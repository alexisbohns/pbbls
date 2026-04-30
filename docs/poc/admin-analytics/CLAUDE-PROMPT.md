# Implementation kickoff — Admin · Analytics

Paste this into a fresh Claude Code session in the `pbbls` repo.

---

## Briefing

You are implementing the Admin · Analytics page in the pbbls Next.js admin app.

**Read these first, in this order:**

1. `docs/specs/admin-analytics.md` — the spec, including exact metric definitions and acceptance criteria.
2. `analytics-mockup.html` — open it in your browser. This is the canonical visual reference. Open the network panel to confirm vendor JS loaded from `docs/specs/admin-analytics/vendor/`.
3. `docs/specs/admin-analytics/screenshots/layout-overview.svg` — labeled layout map. Use it for spatial reference.
4. `supabase/migrations/20260430_analytics_mvs.sql` — the materialized views you'll be reading from.
5. `apps/admin/src/lib/analytics/types.ts` and `apps/admin/src/lib/analytics/fetchers.ts` — the data contracts and server-side fetchers. **The MV row types are the source of truth — do not invent new fields.**
6. `docs/arkaik/pebbles-arkaik.json` and `.claude/skills/arkaik/SKILL.md` — the product architecture map, which you will surgically update as part of this work.

## What you are building

The analytics page at `apps/admin/src/app/(admin)/analytics/page.tsx` and the chart components under `apps/admin/src/components/analytics/`.

12 surfaces in total. The spec lists them and defines every metric. Don't invent definitions — if a definition isn't in the spec, stop and ask.

## Stack & conventions

- Next.js App Router, Server Components by default. Data fetching happens server-side via the fetchers in `lib/analytics/fetchers.ts`. Don't fetch from the client.
- shadcn/ui for primitives (Card, Tabs, Badge, Table). shadcn charts (Recharts under the hood) for everything visual.
- Tailwind for layout. The mockup uses a 12-column grid; stick to it.
- TypeScript strict mode. No `any`, no `as` casts on MV rows.
- Loading states: `<Skeleton/>` from shadcn. Empty states: short copy, neutral tone.
- Error states: surface the error to the admin (this is an internal tool — verbose is fine).

## Way of working

1. **Set up the playground first.** Add `apps/admin/src/app/(admin)/playground/analytics/page.tsx` that imports each chart component and renders it with mock data fixtures (write the fixtures alongside the components in `__fixtures__/`). This is your dev loop — Alexis will review chart components in isolation here before they're wired to live MVs.
2. **Build one surface end-to-end first.** Pick `mv_active_users_daily` → `getActiveUsersSeries` → `<ActiveUsersChart/>`. Get it perfect: types, loading, empty, error, dark mode if applicable, accessibility (axis labels, focus states). Get review on it before parallelizing.
3. **Then parallelize.** Once the pattern is approved, build the remaining 11 surfaces in the same shape.
4. **Wire the page.** Compose the page top-down following the layout in the spec. Time-range selector lives in the page and threads down via props.
5. **Update the Arkaik map** as you go (see below). Don't batch this for the end.

## Arkaik map updates (mandatory)

Per `CLAUDE.md`, the Arkaik map at `docs/arkaik/pebbles-arkaik.json` is the source of truth for the product architecture, and you must update it surgically alongside your code changes. Specifically:

- Add a screen node for `Admin · Analytics` with the route `/admin/analytics`.
- Add data nodes for each materialized view.
- Add endpoint nodes for each fetcher in `lib/analytics/fetchers.ts`.
- Wire edges: screen → fetcher → MV → underlying tables (the schema assumptions in the SQL header).

Use the Arkaik skill at `.claude/skills/arkaik/SKILL.md` — read it first. Run the validation script before saving.

## Acceptance — what "done" means

The list in `docs/specs/admin-analytics.md` under "Acceptance criteria" is the bar. In particular:

- The page loads with non-zero data on staging within 5s on a warm cache.
- Lighthouse performance score ≥ 90.
- A non-admin user gets 403 on every fetcher (test it).
- The Arkaik map validates and reflects the new screen, data nodes, and endpoints.
- `/playground/analytics` renders every chart with fixtures.

## Open questions to resolve before starting

These are listed at the bottom of `admin-analytics.md`. Don't begin coding `mv_quality_signals_daily` consumption until you have answers:

1. Where does session live — `events` table, derived from pebble gaps, or a separate `sessions` table?
2. Are deleted pebbles soft-deleted? (Affects every count.)
3. Is `users.created_at` the cohort anchor, or is there a `signed_up_at`?
4. Where is the canonical `pebble → domain` link?

Ask Alexis on Slack/Notion before assuming.

## What NOT to do

- Don't refactor unrelated admin code. Keep the diff scoped to analytics.
- Don't change the Pebbles repo on GitHub without explicit allowance (per project guidelines).
- Don't fetch from the client. Server Components only.
- Don't invent metrics or rename MV columns. The contracts are fixed.
- Don't regenerate the full Arkaik map. Surgical updates only.
- Don't add real-time data, drill-downs, or any of the spec's stated non-goals — those are v2.

## First message to send back

After you've read the spec, mockup, SQL, and TS, reply with:

1. The four open-question answers (or "still need answers from Alexis on Q1/Q2/...").
2. A 5-bullet plan for the first surface end-to-end.
3. Anything in the spec or mockup that looks wrong, ambiguous, or technically risky.

Then wait for confirmation before writing code.
