# Admin · Analytics — Roadmap

**Milestone:** [M28 · Admin Analytics](https://github.com/alexisbohns/pbbls/milestone/28)
**Last updated:** 2026-04-30

This file is the single source of truth for the analytics work on the back-office. It indexes the spec, the original POC handoff, the shipped slice, and the open follow-up issues.

## Source material

| Path | What it is |
|---|---|
| [`docs/superpowers/specs/2026-04-30-admin-analytics-thin-slice-design.md`](../superpowers/specs/2026-04-30-admin-analytics-thin-slice-design.md) | Brainstorming-derived spec for the thin slice (KPI strip + Active users chart). Includes the schema audit table that drove the scope cut. |
| [`docs/superpowers/plans/2026-04-30-admin-analytics-thin-slice.md`](../superpowers/plans/2026-04-30-admin-analytics-thin-slice.md) | The 20-task implementation plan that produced PR #338. |
| [`docs/poc/admin-analytics/`](../poc/admin-analytics/) | The original POC handoff from Claude Cowork: HTML mockup, SVG layout, MV DDL, TS contracts, server fetchers, and the implementation kickoff prompt. **Reference material — not all of it is buildable on the current schema.** |

The POC and the thin-slice spec sometimes disagree. **The spec wins for what we actually ship.** The POC is the visual + product reference.

## Status: shipped

| PR | Issue | What |
|---|---|---|
| [#338](https://github.com/alexisbohns/pbbls/pull/338) | [#337](https://github.com/alexisbohns/pbbls/issues/337) | Thin slice — KPI strip (6 cards) + Active users line chart, 2 Postgres views + 2 SECURITY DEFINER RPCs, playground page, Arkaik update. |

Architecture from the thin slice (RPC contract, URL-driven time range, server-component fetch, client toggle for chart-local state, fixture-driven playground, no MVs/cron yet) is the **template** for every follow-up issue below.

## Status: open — buildable on today's schema

These five issues are mechanical extensions of the thin slice. Each adds a new view + RPC + card following the exact same pattern.

| Issue | Surface(s) | Layout slot | Notes |
|---|---|---|---|
| [#339](https://github.com/alexisbohns/pbbls/issues/339) | Retention cohort heatmap | engagement row, 4/12 (Active Users shrinks to 8/12) | Cohorts via `auth.users.created_at`, activity via `pebbles`. |
| [#340](https://github.com/alexisbohns/pbbls/issues/340) | Pebble volume + enrichment ratios | volume row, 8+4 | Two views, one issue (shared source). Picture via `snaps`, collection via `collection_pebbles`. **Custom-glyph metric dropped** — blocked on [#347](https://github.com/alexisbohns/pbbls/issues/347). **% with emotion dropped** — `pebbles.emotion_id` is NOT NULL so it's always 100%. |
| [#341](https://github.com/alexisbohns/pbbls/issues/341) | Per-user weekly averages | per-user row, 7/12 (paired with bounce, blocked) | Glyphs / souls / collections per active user, last 12 weeks. |
| [#342](https://github.com/alexisbohns/pbbls/issues/342) | Meaning: emotion + domain share | meaning row, 6+6 | **Single-emotion model** — pebble has one `emotion_id`, so emotion shares sum to 100% per week (vs the POC's "may not sum to 100%"). |
| [#343](https://github.com/alexisbohns/pbbls/issues/343) | Visibility mix | cairns/visibility row, 3/12 | Trivial — `pebbles.visibility` already exists. |

## Status: open — blocked on data foundations

These four issues are **product features first, analytics second**. Each requires schema work (and in two cases significant product design) before the analytics card can be built. Each should run through `superpowers:brainstorming` before scoping.

| Issue | Foundation | Unlocks | Size |
|---|---|---|---|
| [#344](https://github.com/alexisbohns/pbbls/issues/344) | `bounces` snapshot table + writer (or derive from `karma_events`) | Bounce karma distribution chart | Medium — schema design + backfill, then mechanical chart. |
| [#345](https://github.com/alexisbohns/pbbls/issues/345) | `cairns` table + lifecycle (creation, completion check) + at least one client surface that creates them | Cairn participation chart | **Large — full product feature.** |
| [#346](https://github.com/alexisbohns/pbbls/issues/346) | `sessions` + analytics-event log + (probably) `pebble_views` | Quality signals table (8 metrics) | **Largest** — explicit phasing recommended (D-retention first, sessions second, view-events third). |
| [#347](https://github.com/alexisbohns/pbbls/issues/347) | `glyphs.is_custom` column + definition of "custom" + writer | Custom-glyph overlay on volume + enrichment cards | Small — one column + a definition decision. |

## Status: deferred (non-goals for M28)

- CSV export from any chart.
- "Last refresh" timestamp in the page header (no MV refresh exists).
- Materialized views + `pg_cron` nightly refresh — adopt when query times warrant; the RPC contract is shaped to make the swap a one-line migration.
- Drill-down from any chart into Users / Pebbles surfaces.
- Real-time data, predictive metrics, geo / device / app-version breakdowns, funnel analysis (per the POC's stated non-goals).
- Locale-aware bucketing (admin aggregates to UTC).
- Multi-emotion model — would widen the emotion share view but isn't required for analytics.

## Architecture invariants (apply to every follow-up issue)

If you're picking up any issue above, follow these without re-litigation:

- **One Postgres view + one SECURITY DEFINER RPC per surface.** RPC body checks `is_admin(auth.uid())` and raises `insufficient_privilege` (42501) for non-admins.
- **Plain views, not materialized views.** Until query times demand otherwise.
- **Soft-delete doesn't exist** in this project — no `deleted_at is null` filters anywhere.
- **Server fetchers in `apps/admin/lib/analytics/fetchers.ts`** — one thin async function per RPC, with a `console.error("[analytics] <name> failed", err)` in the catch path.
- **Time range is URL state** (`?range=`); chart-local toggles are component state.
- **Server Component shells fetch + render** + Client Component children only when the DOM is needed (Recharts, interactive toggles).
- **Each new card has a playground fixture** at `apps/admin/components/analytics/__fixtures__/<name>.ts` and a render in `app/(authed)/playground/analytics/page.tsx`.
- **Each new card has loading + empty + error states** matching the existing pattern (skeletons, "No data yet", inline `<ErrorBlock>`).
- **Arkaik map updated surgically** — add a data node per view, an endpoint node per RPC, edges screen→endpoint and endpoint→view (and endpoint→underlying tables where the join is meaningful). Run the validator.
- **`apps/admin` build + lint must pass** before opening a PR.

## Why some POC bits don't apply

The POC was written against an aspirational schema. The real schema is shipped and stable. Concretely:

- POC's `users` table → reality is `auth.users` + `public.profiles` (with `is_admin` boolean).
- POC's `pebbles.deleted_at` → soft-delete doesn't exist; pebbles use hard delete via `delete_pebble` RPC.
- POC's `pebbles.picture_url` → `snaps` (1-many).
- POC's `pebbles.collection_id` → `collection_pebbles` (m2m).
- POC's `pebble_emotions` join → `pebbles.emotion_id` is a single non-null FK (one emotion per pebble).
- POC's `glyphs.is_custom` → doesn't exist (#347).
- POC's `bounces` table → doesn't exist (#344); event log is `karma_events`.
- POC's `cairns`, `sessions`, `user_active_days` → don't exist (#345, #346).

When in doubt, prefer the spec and the live schema over the POC. The POC stays as visual + naming reference.
