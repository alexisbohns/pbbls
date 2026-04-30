# Admin · Analytics — Thin slice (v1)

**Status:** Spec, ready for plan
**Owner:** Alexis
**Last updated:** 2026-04-30
**Companion docs:** `docs/poc/admin-analytics/` (full POC: spec, mockup, SVG layout, MV DDL, TS contracts, fetchers)

## Why a thin slice and not the full POC

The POC at `docs/poc/admin-analytics/` describes a 12-surface analytics page backed by 12 nightly-refreshed materialized views. A schema audit against the actual repo found that:

- Roughly 8 of the 12 surfaces are buildable on today's schema with light SQL adaptations.
- 4 surfaces (bounce karma distribution, cairn participation, quality signals/sessions, custom-glyph metric) require net-new schema or product features that haven't shipped.
- The POC's MV/cron infrastructure is overkill for current data volume and would drag `pg_cron` availability + RLS-on-MVs + refresh-failure handling into v1's blast radius.

This spec scopes the **first PR** to the smallest surface that validates the full pipeline: KPI strip + Active users chart. Once that's reviewed, a follow-up "buildable-8" PR adds the remaining 6 buildable surfaces, and the four blocked surfaces wait on their data foundations.

## Scope of v1 (this spec)

In:

- One page at `apps/admin/app/(authed)/analytics/page.tsx`.
- **KPI strip** — six cards: Total users, DAU, WAU, MAU, Pebbles/day, DAU/MAU. Each card shows current value, delta vs prior equal-length period, sub-label, and a sparkline.
- **Active users chart** — daily DAU/WAU/MAU line chart with a client-side metric toggle (DAU / WAU / MAU / All).
- Global time-range tabs in the page header (7d / 30d / 90d / 1y / All), URL-driven via `?range=`.
- Admin-only access guard reusing the existing `is_admin()` RPC pattern.
- A playground page at `apps/admin/app/(authed)/playground/analytics/page.tsx` that renders both components from fixtures.
- Arkaik map updated with the new screen, view nodes, and endpoint nodes.

Out (future PRs):

- The other 6 buildable surfaces: pebble volume, pebble enrichment donuts, per-user averages, domain share, visibility mix, single-emotion share, retention cohorts. Held for PR #2.
- Materialized views and `pg_cron` refresh. Will be added when query times warrant it; the RPC contract is shaped to make that a one-line swap.
- CSV export.
- "Last refresh" timestamp in the header (no refresh exists yet).
- The four data-blocked surfaces: bounce karma distribution, cairn participation, quality signals, custom-glyph metric.

## Schema audit summary

| POC assumption | Reality | Adaptation |
|---|---|---|
| `users` (id, created_at, deleted_at, role) | `auth.users` + `public.profiles.is_admin` | Source `auth.users` for counts; admin gate via `is_admin()` |
| `pebbles.deleted_at` | No soft-delete in this project | Drop all `deleted_at is null` filters |
| `pebbles.picture_url` | `snaps` table (1-many) | Out of scope for v1 |
| `pebbles.collection_id` | `collection_pebbles` join | Out of scope for v1 |
| `pebble_emotions` join | Pebbles have a single non-null `emotion_id` | Out of scope for v1; PR #2 redefines as single-emotion share |
| `glyphs.is_custom` | Column doesn't exist | Drop the metric entirely |
| `bounces`, `cairns`, `sessions`, `user_active_days` | None exist | Blocked, deferred |
| RLS via JWT `role` claim | `is_admin()` SECURITY DEFINER fn | Use existing pattern |

The thin slice only needs `auth.users` and `public.pebbles`. No schema changes required.

## Architecture

Server Components by default. Time-range state lives in the URL (`?range=30d`). The page is a Server Component re-rendered on tab change — no SWR, no client fetching, no provider.

```
apps/admin/app/(authed)/analytics/
  layout.tsx                     // admin guard via is_admin()
  page.tsx                       // composes KpiStrip + ActiveUsersChartCard
  loading.tsx                    // page-level skeleton

apps/admin/components/analytics/
  KpiStrip.tsx                   // SC, fetches kpi rows, renders 6 cards
  KpiCard.tsx                    // presentational card (value, delta, sparkline)
  Sparkline.tsx                  // inline-SVG mini line, ~40 LOC
  ActiveUsersChartCard.tsx       // SC shell, fetches series, passes to client child
  ActiveUsersChart.tsx           // CC, Recharts line chart with metric toggle
  TimeRangeTabs.tsx              // CC, reads/writes ?range= via next/navigation
  ErrorBlock.tsx                 // shared inline error renderer
  __fixtures__/
    kpi.ts
    activeUsers.ts

apps/admin/app/(authed)/playground/analytics/page.tsx

apps/admin/lib/analytics/
  types.ts                       // row types, TimeRange, ActivityMetric
  fetchers.ts                    // server-side .rpc() calls
  date.ts                        // dateRangeFor + helpers

packages/supabase/supabase/migrations/<ts>_analytics_thin_slice.sql
```

## Data layer

### Postgres views and RPCs

A single new migration `<ts>_analytics_thin_slice.sql` adds:

- `v_analytics_kpi_daily` — one row per calendar day from the earliest pebble's `created_at` to today. Columns:
  - `bucket_date date`
  - `total_users int` — `count(*) from auth.users where created_at::date <= bucket_date`
  - `dau int` — distinct `pebbles.user_id` with `created_at::date = bucket_date`
  - `pebbles_today int` — count of pebbles with `created_at::date = bucket_date`
  - `wau int` — rolling 7-day distinct user count ending on `bucket_date`
  - `mau int` — rolling 30-day distinct user count
  - `dau_mau_pct numeric(5,2)` — `(dau / mau) * 100`, null when `mau = 0`
- `v_analytics_active_users_daily` — `select bucket_date, dau, wau, mau from v_analytics_kpi_daily`.

Both views: plain (non-materialized). Computed live per request. Acceptable at current data volume; the swap to MVs is mechanical when warranted.

Two SECURITY DEFINER RPCs read from the views:

- `get_kpi_daily(p_range text)` returns rows from `v_analytics_kpi_daily` covering: the latest row (current values + sparkline window of the last 30 days) **plus** the row at `latest_bucket_date - period_length(p_range)` (for delta computation). For `p_range = "all"` the prior-period row is omitted and deltas render as `—`.
- `get_active_users_series(p_start date, p_end date)` returns rows from `v_analytics_active_users_daily` in `[p_start, p_end]`, ordered ascending.

Both RPCs gate on `is_admin(auth.uid())` and raise `insufficient_privilege` otherwise. RLS on the underlying objects is moot because the RPCs are SECURITY DEFINER, but the admin check is still enforced inside the function body.

The fetchers in `apps/admin/lib/analytics/fetchers.ts` call these RPCs via `supabase.rpc("get_kpi_daily", { p_range: range })` and `supabase.rpc("get_active_users_series", { p_start, p_end })`. Row types match the POC's `KpiDailyRow` and `ActiveUsersDailyRow` so the eventual swap to MVs requires no caller-side change.

### Why RPCs (not direct view reads)

Per `AGENTS.md` the project's bias is toward RPCs for non-trivial reads. Views over `auth.users` would otherwise need bespoke RLS plumbing; SECURITY DEFINER RPCs sidestep that and align with the pattern already used for `is_admin()` and the pebble engine.

## Components

```ts
// KpiStrip.tsx (Server Component)
type KpiStripProps = { range: TimeRange };

// KpiCard.tsx
type KpiCardProps = {
  label: string;
  value: number | string;
  unit?: string;
  delta?: { absolute: number; direction: "up" | "down" | "flat" };
  subLabel?: string;
  sparkline?: number[];
};

// ActiveUsersChartCard.tsx (Server Component)
type ActiveUsersChartCardProps = { range: TimeRange };

// ActiveUsersChart.tsx (Client Component)
type ActiveUsersChartProps = {
  data: ActiveUsersDailyRow[];
  initialMetric?: ActivityMetric; // "all" | "dau" | "wau" | "mau"
};

// TimeRangeTabs.tsx (Client Component) — no props; URL-driven.
```

Notes:

- KPI **delta** is computed server-side: latest row vs row at `latest.bucket_date - period_length`, both pulled from the same RPC payload. DAU/MAU delta is in **percentage points**, not %.
- DAU display value = trailing 7-day mean (per POC spec). WAU display value = today's rolling 7-day count. MAU display value = today's rolling 30-day count. Pebbles/day display value = trailing 7-day mean.
- The DAU/WAU/MAU toggle on the chart is local component state, not URL state — it's a view preference, not deep-linkable.
- Sparkline is hand-rolled inline SVG (`<polyline>` + `<svg viewBox>`), ~40 LOC, no axis, no tooltip. Recharts is overkill for a 30-point card decoration.
- Charts library: `recharts` via the shadcn charts pattern. Add via `npx shadcn@latest add chart` from inside `apps/admin/`.

## Page composition

```tsx
// apps/admin/app/(authed)/analytics/page.tsx
export default async function AnalyticsPage({ searchParams }) {
  const { range = "30d" } = await searchParams;
  return (
    <PageLayout title="Analytics" header={<TimeRangeTabs />}>
      <Suspense fallback={<KpiStripSkeleton />}>
        <KpiStrip range={range} />
      </Suspense>
      <Suspense fallback={<ChartCardSkeleton />}>
        <ActiveUsersChartCard range={range} />
      </Suspense>
    </PageLayout>
  );
}
```

Layout:

- KPI strip: `grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4`. 6×1 on desktop, wraps on smaller widths.
- Active users chart: full-width (`col-span-12`). When PR #2 lands the retention heatmap, this shrinks to 8/12 and the heatmap fills 4/12.

## Admin guard

A sub-layout `apps/admin/app/(authed)/analytics/layout.tsx` calls `supabase.rpc("is_admin", { p_user_id: user.id })` server-side and renders `notFound()` (or redirects to `/403`) for non-admins. Reuses the same pattern as the existing logs guard.

## States

**Loading.** `loading.tsx` at the page level renders the grid shell with shadcn `<Skeleton/>` for each card. Each Server Component child is wrapped in `<Suspense>` with its own skeleton, so individual surfaces can stream in.

**Empty.** KpiCard shows `—` for value, omits delta and sparkline, sub-label reads "No data yet". Chart card shows a faint axis stub with neutral copy "No activity in this range".

**Error.** Each Server Component wraps its fetch in `try/catch` and renders an inline `<ErrorBlock>` with the verbose error message and a "Retry" link (a plain `<a>` to the same URL). No `error.tsx` boundary — errors are surfaced per-card, not per-page, so one broken RPC doesn't blank the page.

**Logging.** Every fetcher catch path includes `console.error("[analytics] <fetcherName> failed", err)` with a clear label (per `CLAUDE.md`'s error-visibility rule).

## Accessibility

- Each KPI card is an `<article>` with an `aria-label` summarizing the metric (`"DAU: 142 users, up 8 from last period"`).
- Delta direction communicated by both icon (▲/▼/–) and text — never color alone.
- Time-range tabs: real `<nav>` with `<a>` links so they work without JS, deep-link, and respect the back button.
- Active-users chart: axis labels via Recharts' `<XAxis>`/`<YAxis>`; container has an `aria-label` summarizing the latest values; metric toggle is keyboard-navigable.
- Focus rings match the admin app convention (Tailwind `focus-visible:ring`).

## Playground

`apps/admin/app/(authed)/playground/analytics/page.tsx` imports each component and renders it with fixtures from `__fixtures__/`:

- `<KpiCard>` — one render per state: positive delta, negative delta, no delta, missing sparkline, empty (`—`).
- `<KpiStrip>` — full mock with all 6 cards.
- `<ActiveUsersChart>` — three fixtures: dense (90 days), sparse (12 days), empty (0 days).

No data layer, no live RPC calls. This is the dev loop where chart components are reviewed in isolation before being wired to RPCs.

## Arkaik update

Per `CLAUDE.md`, the product map at `docs/arkaik/bundle.json` is updated as part of this PR using the `arkaik` skill:

- Add screen node: `Admin · Analytics`, route `/admin/analytics`, status `development`.
- Add data nodes: `v_analytics_kpi_daily`, `v_analytics_active_users_daily`.
- Add endpoint nodes: `get_kpi_daily`, `get_active_users_series`.
- Edges: screen → endpoints → views → underlying tables (`auth.users`, `public.pebbles`).
- Run the validation script before saving.

## Acceptance criteria

- [ ] `/analytics` renders KPI strip + active users chart with non-zero data on staging within 3s on cold cache.
- [ ] Time-range tabs change the URL `?range=` and re-render server-side.
- [ ] DAU/WAU/MAU toggle on the chart filters lines client-side without refetch.
- [ ] Non-admin profile gets a 403/notFound on `/analytics`.
- [ ] `/playground/analytics` renders both components with fixtures, no live data calls.
- [ ] `npm run build --workspace=apps/admin` and `npm run lint --workspace=apps/admin` pass.
- [ ] DB types regenerated (`npm run db:types --workspace=packages/supabase`) and committed.
- [ ] Arkaik map validates and includes the new screen, endpoints, and data nodes.

## Open questions resolved

| POC question | Resolution for v1 |
|---|---|
| Where does session live? | Out of scope — quality signals deferred. |
| Are deleted pebbles soft-deleted? | No. Drop `deleted_at` filters everywhere in the SQL. |
| Is `users.created_at` the cohort anchor? | Yes — but cohorts are out of scope for v1 anyway. |
| Where is `pebble → domain`? | `public.pebble_domains` join — out of scope for v1 (domain share is in PR #2). |

## Follow-ups (PR #2 and beyond)

- PR #2 — buildable-8 expansion: add retention cohorts, pebble volume, pebble enrichment donuts, per-user averages, domain share, visibility mix, single-emotion share. Same architecture; reuse the page layout slot, RPC contracts, and playground pattern. May introduce materialized views at this point if query times warrant it.
- PR #3+ — data foundations for the blocked surfaces: a `sessions` table (or session derivation), a bounce-score snapshot, a `cairns` table, an `is_custom` column on `glyphs` (or a different "custom glyph" definition). Each is a separate spec.
- Eventually: CSV export, last-refresh timestamp (gated on having a refresh), drill-downs into Users/Pebbles surfaces, locale-aware bucketing.
