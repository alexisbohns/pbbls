# Admin Analytics — Thin Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the first cut of the admin analytics page — KPI strip (6 cards) + Active users line chart — for `apps/admin`, backed by two new Postgres views and two SECURITY DEFINER RPCs.

**Architecture:** Server Components by default. Two plain Postgres views (no MV, no cron) read by SECURITY DEFINER RPCs that gate on `is_admin()`. Time-range state lives in `?range=` URL param and re-renders the page server-side; the DAU/WAU/MAU toggle on the chart is local client state. A playground page at `/playground/analytics` renders both components from fixtures for component-level review.

**Tech Stack:** Next.js 16 App Router, React 19 Server Components, TypeScript strict, `@supabase/ssr`, shadcn/ui (`base-nova` on `@base-ui/react`), Recharts via shadcn `chart` primitive, Tailwind CSS 4, Postgres views + plpgsql RPCs.

**Spec:** `docs/superpowers/specs/2026-04-30-admin-analytics-thin-slice-design.md`

**Project conventions to follow:**
- Branch: `feat/<issue-number>-admin-analytics-thin-slice` (create the issue before the branch).
- Commits: conventional, lowercase, no period. Scope is `core`/`db`/`api`/`ui`/`auth` as appropriate.
- No tests in V1 — verification is `npm run build`, `npm run lint`, manual smoke on `/analytics` and `/playground/analytics`.
- Project prefers remote Supabase deploys (no local Docker). The `db:push` script applies migrations to the linked remote project.
- After any DB migration: `npm run db:types --workspace=packages/supabase`, then commit `packages/supabase/types/database.ts`.
- Never `as` cast on RPC return rows — let codegen do its job.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `packages/supabase/supabase/migrations/20260430000000_analytics_thin_slice.sql` | Create | Two views (`v_analytics_kpi_daily`, `v_analytics_active_users_daily`) + two RPCs (`get_kpi_daily`, `get_active_users_series`) with admin gates. |
| `packages/supabase/types/database.ts` | Regenerate | Add the new view rows + RPC signatures. |
| `apps/admin/lib/analytics/types.ts` | Create | `KpiDailyRow`, `ActiveUsersDailyRow`, `TimeRange`, `ActivityMetric`. |
| `apps/admin/lib/analytics/date.ts` | Create | `dateRangeFor(range)`, `periodLengthDays(range)`. |
| `apps/admin/lib/analytics/fetchers.ts` | Create | `getKpiDaily(range)`, `getActiveUsersSeries(range)` — wrap RPCs with try/catch + `console.error`. |
| `apps/admin/components/ui/chart.tsx` | Create (via shadcn CLI) | shadcn chart primitive (recharts wrapper). |
| `apps/admin/components/analytics/Sparkline.tsx` | Create | Inline SVG mini-line. |
| `apps/admin/components/analytics/KpiCard.tsx` | Create | Presentational card: value, delta badge, sub-label, sparkline. |
| `apps/admin/components/analytics/KpiStrip.tsx` | Create | Server Component: fetches KPI rows, computes deltas, renders 6 cards. |
| `apps/admin/components/analytics/KpiStripSkeleton.tsx` | Create | Skeleton fallback. |
| `apps/admin/components/analytics/TimeRangeTabs.tsx` | Create | Client Component, URL-driven via `next/navigation`. |
| `apps/admin/components/analytics/ActiveUsersChart.tsx` | Create | Client Component: recharts line chart with DAU/WAU/MAU toggle. |
| `apps/admin/components/analytics/ActiveUsersChartCard.tsx` | Create | Server Component shell: fetches series, passes to client child. |
| `apps/admin/components/analytics/ChartCardSkeleton.tsx` | Create | Skeleton fallback. |
| `apps/admin/components/analytics/ErrorBlock.tsx` | Create | Inline verbose error renderer. |
| `apps/admin/components/analytics/__fixtures__/kpi.ts` | Create | Mock `KpiDailyRow[]` for playground. |
| `apps/admin/components/analytics/__fixtures__/activeUsers.ts` | Create | Mock `ActiveUsersDailyRow[]` (dense / sparse / empty). |
| `apps/admin/app/(authed)/analytics/page.tsx` | Create | Compose KpiStrip + ActiveUsersChartCard. |
| `apps/admin/app/(authed)/analytics/loading.tsx` | Create | Page-level skeleton shell. |
| `apps/admin/app/(authed)/playground/analytics/page.tsx` | Create | Renders both components with fixtures. |
| `apps/admin/components/layout/Sidebar.tsx` | Modify | Add "Analytics" group + nav item. |
| `docs/arkaik/bundle.json` | Modify | Add screen + endpoints + data nodes (via arkaik skill). |

---

## Task 1: Create issue, branch, and worktree

**Files:** none.

- [ ] **Step 1: Create the GitHub issue**

Title: `[Feat] Admin · Analytics — KPI strip + Active users chart (thin slice)`
Body: link to the spec at `docs/superpowers/specs/2026-04-30-admin-analytics-thin-slice-design.md`. Apply labels `feat` + `core` + `ui` + `db`. Assign to the appropriate milestone.

```bash
gh issue create \
  --title "[Feat] Admin · Analytics — KPI strip + Active users chart (thin slice)" \
  --body "Ships the thin slice of the admin analytics page: KPI strip + Active users chart, backed by two new Postgres views + two SECURITY DEFINER RPCs.

Spec: docs/superpowers/specs/2026-04-30-admin-analytics-thin-slice-design.md
Plan: docs/superpowers/plans/2026-04-30-admin-analytics-thin-slice.md

Resolves the data-pipeline foundation for the broader admin analytics work; the remaining 6 buildable surfaces follow in a second PR." \
  --label feat,core,ui,db
```

Note the issue number returned by `gh issue create` — call it `<N>`. Use it for the branch name in step 2.

- [ ] **Step 2: Create the branch**

```bash
git checkout main && git pull
git checkout -b feat/<N>-admin-analytics-thin-slice
```

---

## Task 2: Database migration — views + RPCs

**Files:**
- Create: `packages/supabase/supabase/migrations/20260430000000_analytics_thin_slice.sql`
- Modify (regenerate): `packages/supabase/types/database.ts`

- [ ] **Step 1: Create the migration file**

```bash
cd packages/supabase
touch supabase/migrations/20260430000000_analytics_thin_slice.sql
```

- [ ] **Step 2: Write the migration SQL**

Paste this exact content into `packages/supabase/supabase/migrations/20260430000000_analytics_thin_slice.sql`:

```sql
-- =============================================================================
-- Admin · Analytics · Thin slice (KPI strip + Active users chart)
-- =============================================================================
-- Spec: docs/superpowers/specs/2026-04-30-admin-analytics-thin-slice-design.md
--
-- Two plain views (not materialized — current data volume doesn't warrant MVs)
-- exposed via two SECURITY DEFINER RPCs that gate on is_admin(auth.uid()).
-- Soft-delete does not exist in this project, so no deleted_at filters.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- v_analytics_kpi_daily
-- One row per calendar day from the earliest pebble's created_at to today.
-- -----------------------------------------------------------------------------
drop view if exists public.v_analytics_active_users_daily;
drop view if exists public.v_analytics_kpi_daily;

create view public.v_analytics_kpi_daily as
with days as (
  select generate_series(
    coalesce((select min(created_at)::date from public.pebbles), current_date),
    current_date,
    interval '1 day'
  )::date as bucket_date
),
totals as (
  select
    d.bucket_date,
    (select count(*) from auth.users u where u.created_at::date <= d.bucket_date) as total_users
  from days d
),
day_counts as (
  select
    d.bucket_date,
    count(distinct p.user_id) filter (where p.created_at::date = d.bucket_date) as dau,
    count(*)                  filter (where p.created_at::date = d.bucket_date) as pebbles_today
  from days d
  left join public.pebbles p on p.created_at::date = d.bucket_date
  group by d.bucket_date
),
rolling as (
  select
    d.bucket_date,
    (select count(distinct p.user_id) from public.pebbles p
       where p.created_at::date >  d.bucket_date - 7
         and p.created_at::date <= d.bucket_date) as wau,
    (select count(distinct p.user_id) from public.pebbles p
       where p.created_at::date >  d.bucket_date - 30
         and p.created_at::date <= d.bucket_date) as mau
  from days d
)
select
  t.bucket_date,
  t.total_users::int           as total_users,
  d.dau::int                   as dau,
  d.pebbles_today::int         as pebbles_today,
  r.wau::int                   as wau,
  r.mau::int                   as mau,
  case when r.mau > 0 then round((d.dau::numeric / r.mau) * 100, 2) else null end as dau_mau_pct
from totals t
join day_counts d using (bucket_date)
join rolling r    using (bucket_date);

-- -----------------------------------------------------------------------------
-- v_analytics_active_users_daily
-- Projection of v_analytics_kpi_daily for the line chart.
-- -----------------------------------------------------------------------------
create view public.v_analytics_active_users_daily as
select bucket_date, dau, wau, mau
from public.v_analytics_kpi_daily;

-- -----------------------------------------------------------------------------
-- get_kpi_daily(p_range text)
-- Returns: latest row plus the row at (latest - period_length(p_range)).
-- For p_range = 'all' the prior-period row is omitted (deltas render '—').
-- -----------------------------------------------------------------------------
create or replace function public.get_kpi_daily(p_range text)
returns setof public.v_analytics_kpi_daily
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_period_days int;
  v_latest      date;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  select max(bucket_date) into v_latest from public.v_analytics_kpi_daily;
  if v_latest is null then
    return;
  end if;

  v_period_days := case p_range
    when '7d'  then 7
    when '30d' then 30
    when '90d' then 90
    when '1y'  then 365
    else null
  end;

  if v_period_days is null then
    return query
      select * from public.v_analytics_kpi_daily
      where bucket_date in (v_latest, v_latest - 30);  -- 30 still useful for sparkline
  else
    return query
      select * from public.v_analytics_kpi_daily
      where bucket_date in (v_latest, v_latest - v_period_days)
         or bucket_date >  v_latest - 30;             -- sparkline window
  end if;
end;
$$;

-- -----------------------------------------------------------------------------
-- get_active_users_series(p_start date, p_end date)
-- Returns: rows from v_analytics_active_users_daily in [p_start, p_end].
-- -----------------------------------------------------------------------------
create or replace function public.get_active_users_series(
  p_start date,
  p_end   date
)
returns setof public.v_analytics_active_users_daily
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
    select * from public.v_analytics_active_users_daily
    where bucket_date between p_start and p_end
    order by bucket_date asc;
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions
-- Views: the SECURITY DEFINER RPCs are the access path. We still revoke direct
-- select to keep the surface tight.
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_kpi_daily          from public, anon, authenticated;
revoke all on public.v_analytics_active_users_daily from public, anon, authenticated;

grant execute on function public.get_kpi_daily(text)                to authenticated;
grant execute on function public.get_active_users_series(date, date) to authenticated;
```

- [ ] **Step 3: Push the migration to the remote Supabase project**

```bash
cd packages/supabase
npm run db:push
```

Expected: migration listed and applied. No errors.

- [ ] **Step 4: Regenerate database types from the linked remote project**

The repo's `db:types` script uses `--local` (requires Docker). The user doesn't run Docker, so target the linked remote project directly:

```bash
cd packages/supabase
npx supabase gen types typescript --linked > types/database.ts
```

Expected: `types/database.ts` is updated. Confirm new entries appear:

```bash
grep -n "v_analytics_kpi_daily\|get_kpi_daily\|get_active_users_series" types/database.ts
```

Expected: at least 4 hits.

- [ ] **Step 5: Hand smoke-test SQL to the user**

The user will run these in Supabase Studio. Surface the exact queries to run as an admin:

```sql
select * from public.get_kpi_daily('30d');
select * from public.get_active_users_series(current_date - 30, current_date);
```

Expected: rows return without error.

And as a non-admin (or by setting the JWT role to a non-admin user):

```sql
select * from public.get_kpi_daily('30d');
-- Expected: ERROR:  insufficient_privilege
```

Do not block on this step — proceed; the user will report any failure.

- [ ] **Step 6: Commit**

```bash
cd /Users/alexis/code/pbbls
git add packages/supabase/supabase/migrations/20260430000000_analytics_thin_slice.sql \
        packages/supabase/types/database.ts
git commit -m "feat(db): admin analytics thin-slice views and rpcs"
```

---

## Task 3: Lib — types

**Files:**
- Create: `apps/admin/lib/analytics/types.ts`

- [ ] **Step 1: Write `types.ts`**

```ts
/**
 * Admin · Analytics · TS contracts (thin slice).
 *
 * Row types mirror the SQL views in
 * packages/supabase/supabase/migrations/20260430000000_analytics_thin_slice.sql
 */

export type IsoDate = string

export interface KpiDailyRow {
  bucket_date: IsoDate
  total_users: number
  dau: number
  pebbles_today: number
  wau: number
  mau: number
  /** DAU / MAU as a 0–100 percent value. Null when MAU = 0. */
  dau_mau_pct: number | null
}

export interface ActiveUsersDailyRow {
  bucket_date: IsoDate
  dau: number
  wau: number
  mau: number
}

export type TimeRange = "7d" | "30d" | "90d" | "1y" | "all"

export type ActivityMetric = "dau" | "wau" | "mau" | "all"

export const TIME_RANGES: readonly TimeRange[] = ["7d", "30d", "90d", "1y", "all"] as const

export const TIME_RANGE_LABELS: Record<TimeRange, string> = {
  "7d": "7 days",
  "30d": "30 days",
  "90d": "90 days",
  "1y": "1 year",
  all: "All time",
}

export function isTimeRange(value: string | undefined): value is TimeRange {
  return value !== undefined && (TIME_RANGES as readonly string[]).includes(value)
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/lib/analytics/types.ts
git commit -m "feat(core): analytics row types and time-range helpers"
```

---

## Task 4: Lib — date helpers

**Files:**
- Create: `apps/admin/lib/analytics/date.ts`

- [ ] **Step 1: Write `date.ts`**

```ts
import type { IsoDate, TimeRange } from "./types"

/** Number of days a TimeRange covers. `all` returns `null`. */
export function periodLengthDays(range: TimeRange): number | null {
  switch (range) {
    case "7d":
      return 7
    case "30d":
      return 30
    case "90d":
      return 90
    case "1y":
      return 365
    case "all":
      return null
  }
}

/** Inclusive UTC date range ending today. For `all`, start = epoch. */
export function dateRangeFor(
  range: TimeRange,
  today: Date = new Date(),
): { start: IsoDate; end: IsoDate } {
  const end = today.toISOString().slice(0, 10)
  const days = periodLengthDays(range)
  if (days === null) return { start: "1970-01-01", end }
  const startDate = new Date(today)
  startDate.setUTCDate(startDate.getUTCDate() - days + 1)
  return { start: startDate.toISOString().slice(0, 10), end }
}

/** Subtract `days` from an ISO date and return a new ISO date. */
export function shiftIsoDate(iso: IsoDate, days: number): IsoDate {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/lib/analytics/date.ts
git commit -m "feat(core): analytics date helpers"
```

---

## Task 5: Lib — fetchers

**Files:**
- Create: `apps/admin/lib/analytics/fetchers.ts`

- [ ] **Step 1: Write `fetchers.ts`**

```ts
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { dateRangeFor } from "./date"
import type { ActiveUsersDailyRow, KpiDailyRow, TimeRange } from "./types"

/**
 * Fetch the rows needed by the KPI strip:
 *   - the latest row (current values + delta source)
 *   - the row from `period_length` days earlier (for delta)
 *   - the last 30 days (for sparklines)
 *
 * Returned by `get_kpi_daily(p_range)` in the migration. The RPC enforces
 * `is_admin(auth.uid())`; callers must be admin or this throws.
 */
export async function getKpiDaily(range: TimeRange): Promise<KpiDailyRow[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_kpi_daily", { p_range: range })
  if (error) {
    console.error("[analytics] getKpiDaily failed:", error.message)
    throw error
  }
  return data ?? []
}

/**
 * Daily DAU/WAU/MAU series for the active-users chart, scoped to the global
 * time range tab.
 */
export async function getActiveUsersSeries(
  range: TimeRange,
): Promise<ActiveUsersDailyRow[]> {
  const { start, end } = dateRangeFor(range)
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_active_users_series", {
    p_start: start,
    p_end: end,
  })
  if (error) {
    console.error("[analytics] getActiveUsersSeries failed:", error.message)
    throw error
  }
  return data ?? []
}
```

- [ ] **Step 2: Type-check the file**

```bash
cd apps/admin
npx tsc --noEmit
```

Expected: no errors. If `supabase.rpc("get_kpi_daily", ...)` returns `unknown`, re-run `npm run db:types --workspace=packages/supabase` and confirm the function is in the generated types.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/lib/analytics/fetchers.ts
git commit -m "feat(api): analytics server fetchers"
```

---

## Task 6: Add shadcn chart primitive

**Files:**
- Create (via shadcn CLI): `apps/admin/components/ui/chart.tsx`
- Modify: `apps/admin/package.json` (recharts dep added)

- [ ] **Step 1: Add the chart primitive**

Run from `apps/admin/`:

```bash
cd apps/admin
npx shadcn@latest add chart
```

Expected: `components/ui/chart.tsx` is created and `recharts` is added to dependencies.

- [ ] **Step 2: Verify Tailwind tokens**

The chart primitive reads `--chart-1`…`--chart-5` CSS variables. Confirm they exist in `apps/admin/app/globals.css`:

```bash
grep -nE "^\s+--chart-[1-5]" apps/admin/app/globals.css
```

Expected: 5 hits in `:root` and 5 in `.dark`. If missing, copy them from `apps/web/app/globals.css` per the project's "shadcn-first" convention (token set must be complete).

- [ ] **Step 3: Commit**

```bash
git add apps/admin/components/ui/chart.tsx apps/admin/package.json apps/admin/package-lock.json
# Only stage globals.css if tokens were added
git diff --staged --name-only
git commit -m "chore(ui): add shadcn chart primitive to admin"
```

---

## Task 7: Component — Sparkline

**Files:**
- Create: `apps/admin/components/analytics/Sparkline.tsx`

- [ ] **Step 1: Write `Sparkline.tsx`**

```tsx
type SparklineProps = {
  values: number[]
  width?: number
  height?: number
  className?: string
  ariaLabel?: string
}

export function Sparkline({
  values,
  width = 80,
  height = 24,
  className,
  ariaLabel,
}: SparklineProps) {
  if (values.length < 2) return null

  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const stepX = width / (values.length - 1)

  const points = values
    .map((v, i) => {
      const x = i * stepX
      const y = height - ((v - min) / range) * height
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(" ")

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      className={className}
      role="img"
      aria-label={ariaLabel ?? "trend"}
    >
      <polyline
        points={points}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/analytics/Sparkline.tsx
git commit -m "feat(ui): analytics sparkline primitive"
```

---

## Task 8: Component — KpiCard

**Files:**
- Create: `apps/admin/components/analytics/KpiCard.tsx`

- [ ] **Step 1: Write `KpiCard.tsx`**

> Reminder: this monorepo's shadcn style is `base-nova` on `@base-ui/react`, **not** Radix. `Card` does not accept `asChild`. Wrap `<Card>` inside an `<article>` for semantics + `aria-label`.

```tsx
import { ArrowDown, ArrowUp, Minus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import { Sparkline } from "./Sparkline"

export type KpiCardProps = {
  label: string
  value: number | string
  unit?: string
  delta?: { absolute: number; direction: "up" | "down" | "flat"; unit?: string }
  subLabel?: string
  sparkline?: number[]
}

export function KpiCard({
  label,
  value,
  unit,
  delta,
  subLabel,
  sparkline,
}: KpiCardProps) {
  const ariaParts = [`${label}: ${value}${unit ? ` ${unit}` : ""}`]
  if (delta) {
    const dirWord = delta.direction === "flat" ? "unchanged" : delta.direction
    ariaParts.push(
      `${dirWord} by ${Math.abs(delta.absolute)}${delta.unit ?? ""} from prior period`,
    )
  }

  return (
    <article aria-label={ariaParts.join(", ")}>
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {label}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex items-end justify-between gap-2">
          <div className="space-y-1">
            <div className="text-2xl font-semibold tabular-nums">
              {value}
              {unit ? (
                <span className="ml-1 text-base font-normal text-muted-foreground">
                  {unit}
                </span>
              ) : null}
            </div>
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              {delta ? <DeltaBadge delta={delta} /> : null}
              {subLabel ? <span>{subLabel}</span> : null}
            </div>
          </div>
          {sparkline && sparkline.length >= 2 ? (
            <Sparkline
              values={sparkline}
              className="text-foreground/60"
              ariaLabel={`${label} trend`}
            />
          ) : null}
        </CardContent>
      </Card>
    </article>
  )
}

function DeltaBadge({ delta }: { delta: NonNullable<KpiCardProps["delta"]> }) {
  const Icon =
    delta.direction === "up" ? ArrowUp : delta.direction === "down" ? ArrowDown : Minus
  const sign = delta.absolute > 0 ? "+" : ""
  return (
    <Badge
      variant="secondary"
      className={cn(
        "gap-1",
        delta.direction === "up" && "text-emerald-700 dark:text-emerald-400",
        delta.direction === "down" && "text-rose-700 dark:text-rose-400",
      )}
    >
      <Icon className="size-3" aria-hidden />
      <span>
        {sign}
        {delta.absolute}
        {delta.unit ?? ""}
      </span>
    </Badge>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/analytics/KpiCard.tsx
git commit -m "feat(ui): analytics KpiCard component"
```

---

## Task 9: Component — KpiStripSkeleton

**Files:**
- Create: `apps/admin/components/analytics/KpiStripSkeleton.tsx`

- [ ] **Step 1: Write `KpiStripSkeleton.tsx`**

```tsx
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

export function KpiStripSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      {Array.from({ length: 6 }).map((_, i) => (
        <Card key={i} aria-hidden>
          <CardHeader className="pb-2">
            <Skeleton className="h-3 w-16" />
          </CardHeader>
          <CardContent className="flex items-end justify-between gap-2">
            <div className="space-y-2">
              <Skeleton className="h-7 w-20" />
              <Skeleton className="h-3 w-24" />
            </div>
            <Skeleton className="h-6 w-20" />
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/analytics/KpiStripSkeleton.tsx
git commit -m "feat(ui): KpiStrip skeleton fallback"
```

---

## Task 10: Component — ErrorBlock

**Files:**
- Create: `apps/admin/components/analytics/ErrorBlock.tsx`

- [ ] **Step 1: Write `ErrorBlock.tsx`**

```tsx
import Link from "next/link"
import { AlertTriangle } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"

type ErrorBlockProps = {
  label: string
  /** Verbose admin-facing message. */
  message: string
  /** Same-URL link target for the retry button. Defaults to current. */
  retryHref?: string
}

export function ErrorBlock({ label, message, retryHref }: ErrorBlockProps) {
  return (
    <Card className="border-destructive/40">
      <CardContent className="flex items-start gap-3 py-4">
        <AlertTriangle className="mt-0.5 size-5 text-destructive" aria-hidden />
        <div className="space-y-1">
          <p className="text-sm font-medium">{label}</p>
          <pre className="whitespace-pre-wrap break-words text-xs text-muted-foreground">
            {message}
          </pre>
          {retryHref ? (
            <Link href={retryHref} className="text-xs underline">
              Retry
            </Link>
          ) : null}
        </div>
      </CardContent>
    </Card>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/analytics/ErrorBlock.tsx
git commit -m "feat(ui): analytics inline ErrorBlock"
```

---

## Task 11: Component — KpiStrip (Server Component)

**Files:**
- Create: `apps/admin/components/analytics/KpiStrip.tsx`

- [ ] **Step 1: Write `KpiStrip.tsx`**

```tsx
import { getKpiDaily } from "@/lib/analytics/fetchers"
import { periodLengthDays } from "@/lib/analytics/date"
import { shiftIsoDate } from "@/lib/analytics/date"
import type { KpiDailyRow, TimeRange } from "@/lib/analytics/types"
import { KpiCard, type KpiCardProps } from "./KpiCard"
import { ErrorBlock } from "./ErrorBlock"

type KpiStripProps = { range: TimeRange }

export async function KpiStrip({ range }: KpiStripProps) {
  let rows: KpiDailyRow[]
  try {
    rows = await getKpiDaily(range)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load KPI strip"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  if (rows.length === 0) {
    return <EmptyKpiStrip />
  }

  const sorted = [...rows].sort((a, b) => a.bucket_date.localeCompare(b.bucket_date))
  const latest = sorted[sorted.length - 1]
  const period = periodLengthDays(range)
  const priorBucket = period === null ? null : shiftIsoDate(latest.bucket_date, -period)
  const prior = priorBucket ? sorted.find((r) => r.bucket_date === priorBucket) ?? null : null

  // Sparkline window: last 30 days (or fewer if not enough rows)
  const sparkRows = sorted.slice(-30)

  const trailing7 = (key: keyof KpiDailyRow) => {
    const last7 = sparkRows.slice(-7)
    if (last7.length === 0) return 0
    const sum = last7.reduce((acc, r) => acc + Number(r[key] ?? 0), 0)
    return Math.round(sum / last7.length)
  }

  const cards: KpiCardProps[] = [
    {
      label: "Total users",
      value: latest.total_users,
      delta: deltaFor(latest.total_users, prior?.total_users),
      subLabel: "all signups",
    },
    {
      label: "DAU",
      value: trailing7("dau"),
      sparkline: sparkRows.map((r) => r.dau),
      delta: deltaFor(latest.dau, prior?.dau),
      subLabel: "trailing 7-day avg",
    },
    {
      label: "WAU",
      value: latest.wau,
      sparkline: sparkRows.map((r) => r.wau),
      delta: deltaFor(latest.wau, prior?.wau),
      subLabel: "rolling 7 days",
    },
    {
      label: "MAU",
      value: latest.mau,
      sparkline: sparkRows.map((r) => r.mau),
      delta: deltaFor(latest.mau, prior?.mau),
      subLabel: "rolling 30 days",
    },
    {
      label: "Pebbles / day",
      value: trailing7("pebbles_today"),
      sparkline: sparkRows.map((r) => r.pebbles_today),
      delta: deltaFor(latest.pebbles_today, prior?.pebbles_today),
      subLabel: "trailing 7-day avg",
    },
    {
      label: "DAU / MAU",
      value: latest.dau_mau_pct ?? "—",
      unit: latest.dau_mau_pct === null ? undefined : "%",
      delta: deltaForPct(latest.dau_mau_pct, prior?.dau_mau_pct ?? null),
      subLabel: "stickiness",
    },
  ]

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      {cards.map((c) => (
        <KpiCard key={c.label} {...c} />
      ))}
    </div>
  )
}

function deltaFor(current: number, prior: number | undefined): KpiCardProps["delta"] {
  if (prior === undefined) return undefined
  const absolute = current - prior
  const direction = absolute > 0 ? "up" : absolute < 0 ? "down" : "flat"
  return { absolute, direction }
}

function deltaForPct(
  current: number | null,
  prior: number | null,
): KpiCardProps["delta"] {
  if (current === null || prior === null) return undefined
  const absolute = Number((current - prior).toFixed(1))
  const direction = absolute > 0 ? "up" : absolute < 0 ? "down" : "flat"
  return { absolute, direction, unit: "pp" }
}

function EmptyKpiStrip() {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      {["Total users", "DAU", "WAU", "MAU", "Pebbles / day", "DAU / MAU"].map((label) => (
        <KpiCard key={label} label={label} value="—" subLabel="No data yet" />
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/analytics/KpiStrip.tsx
git commit -m "feat(ui): KpiStrip server component"
```

---

## Task 12: Component — TimeRangeTabs (Client Component)

**Files:**
- Create: `apps/admin/components/analytics/TimeRangeTabs.tsx`

- [ ] **Step 1: Write `TimeRangeTabs.tsx`**

```tsx
"use client"

import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { cn } from "@/lib/utils"
import {
  TIME_RANGES,
  TIME_RANGE_LABELS,
  isTimeRange,
  type TimeRange,
} from "@/lib/analytics/types"

const DEFAULT: TimeRange = "30d"

export function TimeRangeTabs() {
  const params = useSearchParams()
  const raw = params?.get("range")
  const active: TimeRange = isTimeRange(raw ?? undefined) ? (raw as TimeRange) : DEFAULT

  return (
    <nav aria-label="Time range" className="flex items-center gap-1 rounded-md border p-1">
      {TIME_RANGES.map((range) => {
        const isActive = range === active
        return (
          <Link
            key={range}
            href={`?range=${range}`}
            scroll={false}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "rounded px-3 py-1 text-sm transition-colors",
              isActive
                ? "bg-foreground text-background"
                : "text-muted-foreground hover:bg-muted",
            )}
          >
            {TIME_RANGE_LABELS[range]}
          </Link>
        )
      })}
    </nav>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/analytics/TimeRangeTabs.tsx
git commit -m "feat(ui): URL-driven TimeRangeTabs"
```

---

## Task 13: Component — ActiveUsersChart (Client Component)

**Files:**
- Create: `apps/admin/components/analytics/ActiveUsersChart.tsx`

- [ ] **Step 1: Write `ActiveUsersChart.tsx`**

```tsx
"use client"

import { useState } from "react"
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { Button } from "@/components/ui/button"
import type { ActiveUsersDailyRow, ActivityMetric } from "@/lib/analytics/types"

type ActiveUsersChartProps = {
  data: ActiveUsersDailyRow[]
  initialMetric?: ActivityMetric
}

const METRICS: ActivityMetric[] = ["all", "dau", "wau", "mau"]
const METRIC_LABELS: Record<ActivityMetric, string> = {
  all: "All",
  dau: "DAU",
  wau: "WAU",
  mau: "MAU",
}

const config: ChartConfig = {
  dau: { label: "DAU", color: "var(--chart-1)" },
  wau: { label: "WAU", color: "var(--chart-2)" },
  mau: { label: "MAU", color: "var(--chart-3)" },
}

export function ActiveUsersChart({
  data,
  initialMetric = "all",
}: ActiveUsersChartProps) {
  const [metric, setMetric] = useState<ActivityMetric>(initialMetric)

  if (data.length === 0) {
    return (
      <div
        className="flex h-72 items-center justify-center text-sm text-muted-foreground"
        aria-live="polite"
      >
        No activity in this range
      </div>
    )
  }

  const showDau = metric === "all" || metric === "dau"
  const showWau = metric === "all" || metric === "wau"
  const showMau = metric === "all" || metric === "mau"

  const last = data[data.length - 1]
  const ariaSummary = `Active users on ${last.bucket_date}: DAU ${last.dau}, WAU ${last.wau}, MAU ${last.mau}.`

  return (
    <div aria-label={ariaSummary}>
      <div className="mb-3 flex items-center gap-1" role="tablist" aria-label="Metric">
        {METRICS.map((m) => (
          <Button
            key={m}
            variant={m === metric ? "default" : "ghost"}
            size="sm"
            role="tab"
            aria-selected={m === metric}
            onClick={() => setMetric(m)}
          >
            {METRIC_LABELS[m]}
          </Button>
        ))}
      </div>

      <ChartContainer config={config} className="h-72 w-full">
        <LineChart data={data} margin={{ left: 4, right: 12, top: 8, bottom: 4 }}>
          <CartesianGrid vertical={false} strokeDasharray="3 3" />
          <XAxis
            dataKey="bucket_date"
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            minTickGap={32}
            tickFormatter={(v: string) => v.slice(5)}
          />
          <YAxis tickLine={false} axisLine={false} width={36} />
          <ChartTooltip content={<ChartTooltipContent />} />
          <ChartLegend content={<ChartLegendContent />} />
          {showDau ? (
            <Line dataKey="dau" type="monotone" stroke="var(--color-dau)" strokeWidth={2} dot={false} />
          ) : null}
          {showWau ? (
            <Line dataKey="wau" type="monotone" stroke="var(--color-wau)" strokeWidth={2} dot={false} />
          ) : null}
          {showMau ? (
            <Line dataKey="mau" type="monotone" stroke="var(--color-mau)" strokeWidth={2} dot={false} />
          ) : null}
        </LineChart>
      </ChartContainer>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/analytics/ActiveUsersChart.tsx
git commit -m "feat(ui): ActiveUsersChart with DAU/WAU/MAU toggle"
```

---

## Task 14: Component — ActiveUsersChartCard + ChartCardSkeleton

**Files:**
- Create: `apps/admin/components/analytics/ActiveUsersChartCard.tsx`
- Create: `apps/admin/components/analytics/ChartCardSkeleton.tsx`

- [ ] **Step 1: Write `ChartCardSkeleton.tsx`**

```tsx
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

export function ChartCardSkeleton() {
  return (
    <Card aria-hidden>
      <CardHeader>
        <Skeleton className="h-5 w-40" />
      </CardHeader>
      <CardContent>
        <Skeleton className="h-72 w-full" />
      </CardContent>
    </Card>
  )
}
```

- [ ] **Step 2: Write `ActiveUsersChartCard.tsx`**

```tsx
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getActiveUsersSeries } from "@/lib/analytics/fetchers"
import type { ActiveUsersDailyRow, TimeRange } from "@/lib/analytics/types"
import { ActiveUsersChart } from "./ActiveUsersChart"
import { ErrorBlock } from "./ErrorBlock"

type ActiveUsersChartCardProps = { range: TimeRange }

export async function ActiveUsersChartCard({ range }: ActiveUsersChartCardProps) {
  let data: ActiveUsersDailyRow[]
  try {
    data = await getActiveUsersSeries(range)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load active users chart"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Active users over time</CardTitle>
      </CardHeader>
      <CardContent>
        <ActiveUsersChart data={data} />
      </CardContent>
    </Card>
  )
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/admin/components/analytics/ChartCardSkeleton.tsx \
        apps/admin/components/analytics/ActiveUsersChartCard.tsx
git commit -m "feat(ui): ActiveUsersChartCard server shell + skeleton"
```

---

## Task 15: Fixtures + playground page

**Files:**
- Create: `apps/admin/components/analytics/__fixtures__/kpi.ts`
- Create: `apps/admin/components/analytics/__fixtures__/activeUsers.ts`
- Create: `apps/admin/app/(authed)/playground/analytics/page.tsx`

- [ ] **Step 1: Write `__fixtures__/kpi.ts`**

```ts
import type { KpiDailyRow } from "@/lib/analytics/types"

const TODAY = new Date()
function iso(daysAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - daysAgo)
  return d.toISOString().slice(0, 10)
}

/** 30 days of plausible KPI data, ascending. */
export const kpiFixture: KpiDailyRow[] = Array.from({ length: 30 }, (_, i) => {
  const day = 29 - i
  const dau = 80 + Math.round(20 * Math.sin(i / 4)) + i
  const wau = dau * 4
  const mau = dau * 9
  return {
    bucket_date: iso(day),
    total_users: 1200 + i * 3,
    dau,
    pebbles_today: dau * 2 + (i % 5),
    wau,
    mau,
    dau_mau_pct: Math.round((dau / mau) * 10000) / 100,
  }
})

export const kpiEmptyFixture: KpiDailyRow[] = []
```

- [ ] **Step 2: Write `__fixtures__/activeUsers.ts`**

```ts
import type { ActiveUsersDailyRow } from "@/lib/analytics/types"

const TODAY = new Date()
function iso(daysAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - daysAgo)
  return d.toISOString().slice(0, 10)
}

export const denseFixture: ActiveUsersDailyRow[] = Array.from({ length: 90 }, (_, i) => {
  const day = 89 - i
  const dau = 100 + Math.round(40 * Math.sin(i / 7)) + Math.round(i / 3)
  return { bucket_date: iso(day), dau, wau: dau * 4, mau: dau * 9 }
})

export const sparseFixture: ActiveUsersDailyRow[] = Array.from({ length: 12 }, (_, i) => {
  const day = 11 - i
  const dau = 30 + i * 2
  return { bucket_date: iso(day), dau, wau: dau * 3, mau: dau * 6 }
})

export const emptyFixture: ActiveUsersDailyRow[] = []
```

- [ ] **Step 3: Write `playground/analytics/page.tsx`**

```tsx
import { ActiveUsersChart } from "@/components/analytics/ActiveUsersChart"
import { KpiCard } from "@/components/analytics/KpiCard"
import { Sparkline } from "@/components/analytics/Sparkline"
import {
  denseFixture,
  emptyFixture,
  sparseFixture,
} from "@/components/analytics/__fixtures__/activeUsers"
import { kpiFixture } from "@/components/analytics/__fixtures__/kpi"

export default function AnalyticsPlaygroundPage() {
  const sparkValues = kpiFixture.map((r) => r.dau)

  return (
    <div className="space-y-10">
      <header>
        <h1 className="text-2xl font-semibold">Playground · Analytics</h1>
        <p className="text-sm text-muted-foreground">
          Renders analytics components from fixtures. No live data calls.
        </p>
      </header>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">KpiCard variants</h2>
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
          <KpiCard
            label="DAU"
            value={142}
            subLabel="trailing 7-day avg"
            sparkline={sparkValues}
            delta={{ absolute: 8, direction: "up" }}
          />
          <KpiCard
            label="DAU"
            value={142}
            subLabel="trailing 7-day avg"
            sparkline={sparkValues}
            delta={{ absolute: -3, direction: "down" }}
          />
          <KpiCard
            label="MAU"
            value={1212}
            subLabel="rolling 30 days"
            sparkline={sparkValues}
            delta={{ absolute: 0, direction: "flat" }}
          />
          <KpiCard label="Total users" value={1287} subLabel="all signups" />
          <KpiCard label="DAU / MAU" value="—" subLabel="No data yet" />
          <KpiCard
            label="DAU / MAU"
            value={11.7}
            unit="%"
            subLabel="stickiness"
            delta={{ absolute: 0.3, direction: "up", unit: "pp" }}
          />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">Sparkline</h2>
        <div className="text-foreground/60">
          <Sparkline values={sparkValues} width={200} height={40} ariaLabel="DAU sparkline" />
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          ActiveUsersChart — dense (90 days)
        </h2>
        <ActiveUsersChart data={denseFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          ActiveUsersChart — sparse (12 days)
        </h2>
        <ActiveUsersChart data={sparseFixture} />
      </section>

      <section className="space-y-4">
        <h2 className="text-sm font-medium uppercase text-muted-foreground">
          ActiveUsersChart — empty
        </h2>
        <ActiveUsersChart data={emptyFixture} />
      </section>
    </div>
  )
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/admin/components/analytics/__fixtures__ \
        apps/admin/app/\(authed\)/playground
git commit -m "feat(ui): analytics playground page and fixtures"
```

---

## Task 16: Page — analytics

**Files:**
- Create: `apps/admin/app/(authed)/analytics/page.tsx`
- Create: `apps/admin/app/(authed)/analytics/loading.tsx`

- [ ] **Step 1: Write `loading.tsx`**

```tsx
import { ChartCardSkeleton } from "@/components/analytics/ChartCardSkeleton"
import { KpiStripSkeleton } from "@/components/analytics/KpiStripSkeleton"

export default function AnalyticsLoading() {
  return (
    <div className="space-y-6">
      <KpiStripSkeleton />
      <ChartCardSkeleton />
    </div>
  )
}
```

- [ ] **Step 2: Write `page.tsx`**

```tsx
import { Suspense } from "react"
import { ActiveUsersChartCard } from "@/components/analytics/ActiveUsersChartCard"
import { ChartCardSkeleton } from "@/components/analytics/ChartCardSkeleton"
import { KpiStrip } from "@/components/analytics/KpiStrip"
import { KpiStripSkeleton } from "@/components/analytics/KpiStripSkeleton"
import { TimeRangeTabs } from "@/components/analytics/TimeRangeTabs"
import { isTimeRange, type TimeRange } from "@/lib/analytics/types"

type SearchParams = Promise<{ range?: string }>

export default async function AnalyticsPage({
  searchParams,
}: {
  searchParams: SearchParams
}) {
  const params = await searchParams
  const range: TimeRange = isTimeRange(params.range) ? params.range : "30d"

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Analytics</h1>
        <TimeRangeTabs />
      </header>

      <Suspense fallback={<KpiStripSkeleton />}>
        <KpiStrip range={range} />
      </Suspense>

      <Suspense fallback={<ChartCardSkeleton />}>
        <ActiveUsersChartCard range={range} />
      </Suspense>
    </section>
  )
}
```

> The `(authed)` route group already runs `requireAdmin()` in its layout. No additional guard needed at this level.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/app/\(authed\)/analytics
git commit -m "feat(ui): analytics page with KPI strip and active users chart"
```

---

## Task 17: Sidebar nav entry

**Files:**
- Modify: `apps/admin/components/layout/Sidebar.tsx`

- [ ] **Step 1: Add an "Analytics" group above "Logs"**

Open `apps/admin/components/layout/Sidebar.tsx`. Just below the existing `LOG_ITEMS` constant, add:

```ts
import { BarChart3 } from "lucide-react"

const ANALYTICS_ITEMS = [
  { href: "/analytics", label: "Analytics", icon: BarChart3 },
] as const
```

Then inside `<SidebarContent>`, **before** the `Logs` `<SidebarGroup>`, add:

```tsx
<SidebarGroup>
  <SidebarGroupLabel>Insights</SidebarGroupLabel>
  <SidebarGroupContent>
    <SidebarMenu>
      {ANALYTICS_ITEMS.map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`)
        return (
          <SidebarMenuItem key={href}>
            <SidebarMenuButton render={<Link href={href} />} isActive={active}>
              <Icon aria-hidden />
              <span>{label}</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        )
      })}
    </SidebarMenu>
  </SidebarGroupContent>
</SidebarGroup>
```

- [ ] **Step 2: Commit**

```bash
git add apps/admin/components/layout/Sidebar.tsx
git commit -m "feat(ui): admin sidebar Analytics entry"
```

---

## Task 18: Verification

**Files:** none (verification only).

- [ ] **Step 1: Lint and build**

```bash
cd apps/admin
npm run lint
npm run build
```

Expected: both succeed with no errors.

- [ ] **Step 2: Run the dev server and smoke-test the playground**

```bash
cd apps/admin
npm run dev
```

Open `http://localhost:3001/playground/analytics` in the browser. Confirm:
- All 6 KpiCard variants render correctly (positive, negative, flat delta; missing sparkline; empty `—`; `pp` unit).
- Sparkline renders without overflow.
- ActiveUsersChart renders dense / sparse / empty cases. Toggling DAU/WAU/MAU/All hides and shows the right lines.
- No console errors.

- [ ] **Step 3: Smoke-test the live page as admin**

Sign in as an admin user. Navigate to `/analytics`. Confirm:
- KPI strip renders with non-zero values for the seeded DB.
- Sparklines visible on DAU/WAU/MAU/Pebbles per day.
- Active users chart renders with the 30-day default range.
- Clicking time-range tabs (`7d` / `30d` / `90d` / `1y` / All) updates `?range=` and re-renders.
- DAU/WAU/MAU toggle filters lines without a network request (verify in the Network panel).
- No console errors.

- [ ] **Step 4: Smoke-test as a non-admin**

Sign out, then sign in as a non-admin profile (or temporarily flip `profiles.is_admin` to false). Navigate to `/analytics`. Confirm: redirect to `/403` (the `(authed)` layout's `requireAdmin()` enforces this).

Restore the admin flag if you flipped it.

- [ ] **Step 5: Smoke-test the empty state**

In the Supabase SQL editor, temporarily simulate the empty state — e.g., ask the RPC for a future date range:

```sql
select * from public.get_active_users_series(current_date + 30, current_date + 60);
```

Expected: zero rows, no error. (The page doesn't expose this directly, but it confirms the empty path of the RPC.)

---

## Task 19: Arkaik map update

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Read the arkaik skill**

```bash
cat .claude/skills/arkaik/SKILL.md
```

- [ ] **Step 2: Apply surgical updates**

Per the skill, add (do not regenerate the full bundle):

- One **screen** node — id `admin-analytics`, label `Admin · Analytics`, route `/admin/analytics`, status `development`, lives in the admin app.
- Two **data** nodes — `v_analytics_kpi_daily`, `v_analytics_active_users_daily` (Postgres views).
- Two **endpoint** nodes — `get_kpi_daily`, `get_active_users_series` (RPCs, security-definer, admin-gated).
- **Edges:**
  - `screen:admin-analytics → endpoint:get_kpi_daily`
  - `screen:admin-analytics → endpoint:get_active_users_series`
  - `endpoint:get_kpi_daily → data:v_analytics_kpi_daily`
  - `endpoint:get_active_users_series → data:v_analytics_active_users_daily`
  - `data:v_analytics_kpi_daily → data:auth.users` (existing or new)
  - `data:v_analytics_kpi_daily → data:public.pebbles` (existing)
  - `data:v_analytics_active_users_daily → data:v_analytics_kpi_daily` (projection)

- [ ] **Step 3: Run the validation script**

The arkaik skill provides a validator. Run it before saving:

```bash
.claude/skills/arkaik/validate.sh docs/arkaik/bundle.json
```

Expected: validation passes.

- [ ] **Step 4: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(core): arkaik map update for admin analytics"
```

---

## Task 20: Push branch and open PR

**Files:** none (PR only).

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/<N>-admin-analytics-thin-slice
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create \
  --title "feat(core): admin analytics thin slice (kpi strip + active users chart)" \
  --body "$(cat <<'EOF'
Resolves #<N>

Ships the thin slice of the admin analytics page: KPI strip (6 cards) + Active users chart (DAU/WAU/MAU toggle). Backed by two new Postgres views and two SECURITY DEFINER RPCs gated on `is_admin()`. Time range is URL-driven (`?range=`).

## Key files
- `packages/supabase/supabase/migrations/20260430000000_analytics_thin_slice.sql` — views + RPCs
- `apps/admin/lib/analytics/{types,date,fetchers}.ts` — TS contracts and server fetchers
- `apps/admin/components/analytics/*` — KpiStrip, KpiCard, Sparkline, ActiveUsersChart(Card), TimeRangeTabs, ErrorBlock, skeletons, fixtures
- `apps/admin/app/(authed)/analytics/{page,loading}.tsx` — the page
- `apps/admin/app/(authed)/playground/analytics/page.tsx` — fixture-driven component review
- `apps/admin/components/layout/Sidebar.tsx` — Analytics nav entry
- `docs/arkaik/bundle.json` — surgical map update

## Implementation notes
- No materialized views or `pg_cron`. RPC contract is shaped so swapping to MVs later is a one-line migration.
- Soft-delete doesn't exist in this project; no `deleted_at` filters in the SQL.
- DAU/WAU/MAU toggle is local component state; time range is URL state.
- `(authed)` layout already enforces admin via `requireAdmin()`; no extra guard at the page level.

## Plan
docs/superpowers/plans/2026-04-30-admin-analytics-thin-slice.md
EOF
)" \
  --label feat,core,ui,db
```

If the issue had a milestone, propose inheriting it and ask the user before adding `--milestone <name>` to the command.

- [ ] **Step 3: Confirm PR labels and milestone**

Per project guidelines: never open a PR without species + scope labels and a milestone (unless the user has confirmed there is none). Verify after creation:

```bash
gh pr view --json title,labels,milestone
```

If anything is missing, ask the user before patching.

---

## Self-review notes (already applied)

- Spec coverage: every requirement from the spec maps to a task — KPI strip (Task 11), active users chart (Tasks 13–14), time-range tabs (Task 12), playground (Task 15), admin guard (reused via existing `(authed)` layout), Arkaik update (Task 19), acceptance verification (Task 18). Empty/error states handled per-component.
- Placeholders: none. The only `<…>` token is `<N>` for the issue number, deliberately deferred to runtime.
- Type consistency: `KpiCardProps.delta` shape (`{ absolute, direction, unit? }`) is consistent across `KpiCard`, `KpiStrip`, and the playground. `ActivityMetric` typed identically across types/chart/playground. `TimeRange` is canonical in `lib/analytics/types.ts`.
- shadcn-base-nova constraint flagged at the top of Task 8 (no `asChild` on `Card`) — code uses `<article><Card>…</Card></article>` accordingly.
