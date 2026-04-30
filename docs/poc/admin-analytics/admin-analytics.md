# Admin · Analytics

**Status:** Draft v0.1 · POC mockup approved, pending implementation
**Owner:** Alexis
**Last updated:** 2026-04-30

## Why

Pebbles is a daily ritual product — its long-term success depends on whether
people come back, whether they're collecting meaningful pebbles (not just
text-only one-liners), and whether the product gives them back the mirror it
promises (emotional shape, who matters, what domains of their life are full or
starved). The admin Analytics surface answers four questions on one page:

1. **Are people coming back?** (retention, engagement)
2. **Are they collecting?** (usage volume by period, with picture/glyph/collection)
3. **What does an average user look like?** (per-user averages of glyphs, souls, collections — and how those evolve)
4. **What's emerging?** (emotion and domain prevalence, and how those shift)

This is for us, not for users — operating instrument, not consumer dashboard.

## Goals

- One page, no drill-downs in v1.
- All charts driven by Supabase materialized views, refreshed once per day at night.
- All metrics show **both** an absolute current value **and** a delta vs. the previous period (or a time series).
- Built with `shadcn/ui` and shadcn charts (Recharts under the hood).
- Loadable end-to-end in under 1.5s on a warm cache.

## Non-goals (v1)

- Per-user drill-down. Use the Users surface for that.
- Cohort comparison beyond weekly retention table.
- Real-time data. Nightly refresh is enough.
- Predictive metrics (churn risk, propensity). Defer.
- Geo, device, app-version breakdowns. Defer.
- Funnel analysis (signup → first pebble → first cairn). Defer to a separate "Funnels" surface.
- Export beyond CSV.

## Visual reference

- Live interactive mockup: `analytics-mockup.html` (project root). Open in any browser; deps are bundled locally under `docs/specs/admin-analytics/vendor/`.
- Layout map: `docs/specs/admin-analytics/screenshots/layout-overview.svg`.

If anything in this spec is ambiguous, the **live mockup wins** for visual decisions and **this doc wins** for metric definitions.

## Layout

The page is a 12-column grid. Reading top-to-bottom:

| Row | Width split | Contents |
|---|---|---|
| Header | 12 | Title, breadcrumb, time-range tabs (7d / 30d / 90d / 1y / All), last-refresh timestamp, Export CSV. |
| KPI strip | 6 × 2 | Total users · DAU · WAU · MAU · Pebbles/day · DAU/MAU. Each card has value, delta badge, contextual sub-label, sparkline. |
| Engagement | 8 + 4 | Active users line chart (DAU/WAU/MAU toggle) · Retention cohort heatmap (weekly). |
| Volume | 8 + 4 | Pebbles collected (bar volume + enrichment overlay lines, with Day/Week/Month/Year toggle) · Pebble enrichment donuts + secondary ratios. |
| Per-user & habit | 7 + 5 | Per-user averages over time (glyphs, souls, collections) · Bounce karma distribution + habit summary. |
| Meaning | 6 + 6 | Emotion pearls prevalence (snapshot bar + over-time stacked area) · Maslow domains prevalence + biggest movers. |
| Cairns & visibility | 6 + 3 + 3 | Cairn participation · Visibility mix · Quality signals table. |

Time range selector applies globally to every chart. The toggles within charts (DAU/WAU/MAU, Day/Week/Month/Year) only change which series is visible, not the underlying time window.

## Metric definitions

> **Active user** throughout this spec means a user who created **at least one pebble** in the period. Sessions don't count — passive opens don't make you active in Pebbles' terms.

> **Period buckets** are anchored to UTC. We make this explicit because cairns and bounces are user-locale-aware in the product but admin analytics aggregate to UTC for consistency.

### KPI strip

- **Total users** — `count(distinct user_id)` from `users` where `deleted_at is null`. Delta vs. prior period of equal length.
- **DAU** — distinct users with ≥1 pebble created in the past 24h. Sparkline shows the last 30d. Display value = trailing 7-day average.
- **WAU** — distinct users with ≥1 pebble created in the past 7d, rolling. Display value = today's rolling 7d count.
- **MAU** — distinct users with ≥1 pebble created in the past 30d, rolling.
- **Pebbles / day** — count of pebbles created in the past 24h. Display value = trailing 7-day average.
- **DAU / MAU** — stickiness ratio. `dau_today / mau_today`. Delta in **percentage points** (pp), not %.

### Active users over time

- Daily series of DAU, WAU, and MAU as defined above.
- Toggle: DAU / WAU / MAU / All.
- Time window respects the global range tab.

### Retention cohort heatmap

- Cohort = users grouped by **signup week** (Mon–Sun, UTC).
- Cell `[cohort, week_n]` = `% of cohort users who created ≥1 pebble in week_n` where `week_n` is weeks elapsed since signup.
- W0 is always 100% by definition (signup week, signup ≥1 pebble in onboarding). If we ever decouple signup from first pebble, change W0 to "% with ≥1 pebble in W0".
- Last 8 cohorts shown, oldest on top.
- Color scale: light → stone, with 8 buckets (10% steps).

### Pebbles collected

- Total bars: count of pebbles created per bucket (Day/Week/Month/Year). Y-axis switches accordingly.
- Overlay lines (counts, not %, on the same axis):
  - **With picture** — pebbles where `picture_url is not null`.
  - **Custom glyph** — pebbles where `glyph_id` references a glyph with `is_custom = true`.
  - **In collection** — pebbles where `collection_id is not null`.
- Display the absolute count, not the share. The donuts handle share.

### Pebble enrichment (donuts + ratios)

Three donuts showing the **current period** share:
- % of pebbles with picture
- % of pebbles with custom glyph
- % of pebbles in a collection

Then four secondary ratios listed below:
- % with ≥1 emotion pearl
- % linked to ≥1 soul
- % with thought attached
- % with intensity set (should approach 100% — useful as a sanity check)

### Per-user averages over time

For each of the last 12 weeks (week ending Sunday):
- **Avg glyphs / user** — total glyphs owned divided by users who were active that week. (Not all users, only active.)
- **Avg souls / user** — total souls created divided by active users.
- **Avg collections / user** — total collections divided by active users.

Display the latest week's value as a number with delta vs. prior week. Chart shows the 12-week trend.

### Bounce karma distribution

- **Buckets:** 0, 1–10, 11–25, 26–50, 51–100, 100+.
- Y-axis = count of users currently in each bucket.
- Three summary stats:
  - **Median bounce** — median of all users' current bounce.
  - **% maintaining** — share of users whose bounce **did not decrease** vs. 7 days ago.
  - **Avg active days / week** — across all MAU, average distinct active days per 7-day window.

### Emotion pearls prevalence

- For each emotion, **% of pebbles in the period that have ≥1 pearl of this emotion**. A single pebble can contribute to multiple emotions, so the shares **do not need to sum to 100%**. Rank descending.
- Snapshot = current period.
- Over-time view = stacked area, last 12 weeks, **normalized to 100% per week** so the eye can see shifts in mix rather than volume.

### Maslow domains prevalence

- For each domain, % of pebbles linked to that domain in the current period.
- Rank descending.
- Below the chart: "Most-evolving domain (vs prev. period)" and "Most-decreasing domain", showing pp change.

### Cairn participation

- For each of weekly and monthly cairns, **% of eligible users who completed / partially-completed / missed** their cairn in the **last completed period**.
- Eligible = users who were active at least one day in the period.
- Three summary stats:
  - Avg pebbles per completed cairn
  - Rewards unlocked / week
  - % of all users with ≥1 cairn ever

### Visibility mix

Pie chart of pebbles in the current period by visibility setting: Public, Private, Secret.

### Quality signals

A table of 8 healthy-habit indicators with current value and delta:

| Metric | Definition |
|---|---|
| Median session duration | Median of session length in seconds, where a session = consecutive activity with gaps ≤ 30 min. |
| Sessions / active user / week | Distinct sessions per WAU, weekly. |
| Pebbles / active user / week | Total pebbles created divided by WAU. |
| % revisits to past pebbles | % of sessions that include opening a pebble created more than 7 days earlier. |
| D1 retention | % of new users active on day 1. |
| D7 retention | % active on day 7. |
| D30 retention | % active on day 30. |
| Friction events / session | Count of `pebble_created_aborted`, `pebble_save_failed`, `error_shown` events per session. |

## Data sources & contracts

Each surface is backed by **one** materialized view. See `supabase/migrations/20260430_analytics_mvs.sql` for the DDL and `apps/admin/src/lib/analytics/types.ts` for the TS row types.

| Surface | View |
|---|---|
| KPI strip | `mv_kpi_daily` |
| Active users over time | `mv_active_users_daily` |
| Retention cohorts | `mv_retention_cohorts_weekly` |
| Pebbles volume + enrichment lines | `mv_pebble_volume_daily` |
| Pebble enrichment donuts + ratios | `mv_pebble_enrichment_daily` |
| Per-user averages over time | `mv_user_averages_weekly` |
| Bounce karma distribution | `mv_bounce_distribution_daily` |
| Emotions prevalence (snapshot + over time) | `mv_emotion_share_weekly` |
| Domains prevalence | `mv_domain_share_weekly` |
| Cairn participation | `mv_cairn_participation_weekly` |
| Visibility mix | `mv_visibility_mix_daily` |
| Quality signals | `mv_quality_signals_daily` |

All MVs refresh nightly at **03:00 UTC** via `pg_cron`. Each MV includes a `bucket_date` column representing the day it was computed for; the page reads `where bucket_date = (select max(bucket_date) from mv_x)` for current values and a date range for time series.

## Authorization

This page is admin-only. Every fetcher must check that the caller's `user_id` has `role = 'admin'` (Supabase RLS policy on the MVs). Non-admins see 403.

## Performance budget

- Page TTFB: ≤ 200ms (admin shell already streams).
- Chart hydration: ≤ 800ms total for all charts on first render.
- MV row count: each MV should stay under 100k rows for v1; if a daily MV grows past that, split by month.

## Open questions for the implementation session

1. Where exactly does session live — is there an `events` table or do we derive sessions from `pebbles.created_at` gaps? This affects half of "Quality signals". **Resolve before writing `mv_quality_signals_daily`.**
2. Are deleted pebbles soft-deleted? If yes, every count should filter `where deleted_at is null`.
3. Is `users.created_at` the right cohort anchor or do we have a `signed_up_at` column?
4. Where is the canonical mapping `pebble → domain`? Is it via `pebble_emotions` joining out to `emotion → primary_domain`, or a direct `pebble_domains` link?

## Acceptance criteria

- [ ] All 12 surfaces render with non-zero data on staging within 5s of page load.
- [ ] Time range selector updates every chart consistently.
- [ ] Each chart has loading, empty, and error states matching shadcn defaults.
- [ ] All MVs refresh nightly via `pg_cron`; manual refresh trigger available via Supabase function `refresh_analytics_mvs()`.
- [ ] Admin RLS enforced — verified by attempting access from a non-admin session and getting 403.
- [ ] Storybook (or `/playground/analytics`) renders each chart with mock data fixtures so we can review components in isolation.
- [ ] Lighthouse performance score ≥ 90 on the page.
- [ ] Update `docs/arkaik/pebbles-arkaik.json` to add the Analytics screen, all MV data nodes, and the fetcher endpoints.

## Out of scope (will be follow-ups)

- Time-zone aware bucketing (per-user locale)
- Drill-down from any chart into Users / Pebbles surfaces
- Comparative cohort views (this cohort vs. that cohort)
- Predictive churn risk
- Geo / device / version breakdowns
- Funnels surface
