# Achievements page — web implementation plan (#1308)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the web's You → Achievements grid with the approved page (the days kept, Explorer, Emotions, Domains, Pebbles and one card per year) and its five sheets, rendered from `get_achievements`. Opening a sheet marks its card seen, a "ready" mark pulls a completed set's ceremony, and the unlock moment celebrates only what the server marks `celebrate`.

**Architecture:**
- **Data layer (`lib/data/`):**
  - pure decoders turn the RPC payloads into typed models, tolerantly;
  - a per-user localStorage cache keeps the last raw payload (stale-while-revalidate);
  - a loader runs `check_achievements()` and then `get_achievements(p_tz)`;
  - one hook, `useAchievementsPage`, exposes the page, the seen marks and the ceremony pull.
- **Mapping and geometry (`lib/achievements/`):** pure functions, ported from the approved boards' generators, turn served positions (seat, level, progress, family) into coordinates. They never compare a count with a threshold. Every module has Vitest tests that replay the payload fixtures or the boards' own output.
- **Components (`components/achievements/`):** atoms (the flat seal, the gauge, the stage), molecules (the card shell, the register row) and organisms (the wheel, the rosace, the band, the skyline, the year disc) are React SVG. Cards compose them; the five sheets use the shadcn `Sheet`, and a pushed sheet nests inside the one below it.

**Tech Stack:** Next.js 16 App Router (client components), React 19, TypeScript strict, Tailwind 4 over the Spine tokens in `app/globals.css`, shadcn `Sheet`/`Dialog` on `@base-ui/react`, next-intl (EN/FR), Vitest 5 (node environment, `lib/**/*.test.ts`).

**Spec:** `docs/superpowers/specs/2026-10-29-achievements-page-design.md`, with §4 as the client rule.

**Contract consumed:** `docs/superpowers/plans/2026-10-29-achievements-page-backend.md`:
- Task 3: `check_achievements()` returns `celebrate`.
- Task 9: `get_achievements`, `achievements_year`.
- Task 12: `mark_achievements_seen`, `pull_ceremony`.

**Design (private, maintainer-approved):** the final view and the five sheets.
- Their notes: `ach/final/NOTES.md`.
- Their generators: `ach/final/_build/app.js` and `sheets.js`.
- Location: the design session's scratchpad, published at https://claude.ai/artifact/5MD93PUAXwpRjGGMimoKgF.

**Issue:** #1308 (M65 · Achievements). **Stack:** seven parts with `gh stack`, one PR each, in this order. Each part lints, typechecks and passes the web suite on its own.


> **Contract resolutions (2026-10-29, after the three client plans):** the back-end plan now serves what the client planners found missing. Where this plan hard-codes or tolerates one of these, switch to the served value:
> - **No explicit nulls:** `explorer.first_five` and `six_firsts` are `[]` when empty, and `twenty_five` is omitted when it can't be built.
> - **The year serves `days_target` (14) and per-month `done: {moments, pebbles, weeks}`.** Draw the done style from `done`; never compare a count with 14.
> - **Every dated row carries `on`,** by one rule: the local day its pebble happened, or the recorded day when that pebble is gone. That covers seats, domain entries, the six firsts, the 25th and ladder levels.
> - **`emotions.families`** gives the sheet's order: families in wheel order, each with its met seats by the day met and its `not_yet` seats.
> - **`since`** on `emotions` and on each ladder (pebbles, glyphs carved, glyph sales) is the card's "since" date.
> - **`seen`** is a top-level map of card → last-look time, at whole seconds.
> - **A completion carries its `pebble`.**
> - **Celebration needs no client change.** After the part 1 review, `check_achievements()` keeps its shape and returns only the rows to celebrate; there is no `celebrate` column. Drop any task here that decodes or filters on `celebrate`, and keep celebrating every returned row. Silent rows (the firsts, `pebble-count-1`, heavy crossings) are recorded but never returned.
> - **No year "new" marks** in v1.
> - **The dumps are verbatim response bodies,** and a keyed run adds `get-achievements-pulled.json` (a pulled completion).
> - **The Profile shelf is served** (D18, the maintainer's call on 29 October). `get_achievements` returns `shelf` (up to six `{slug, family, threshold, unlocked_on}`, private families already removed) and `shelf_count`. Point the Profile shelf at it instead of reading `achievement_unlocks`, and keep resolving titles and glyphs from the cached catalog by slug.
> - **The ceremony reuses the existing unlock moment** (maintainer, 29 October).
> - **Lab Note:** the page parts carry one (maintainer, 29 October).

---

## Before you start

- **Read** `apps/web/CLAUDE.md`, `AGENTS.md`, `docs/agents/ui-and-styling.md` and `docs/agents/data-and-async.md`.
- **Next.js 16:** this plan uses only `page.tsx` and `layout.tsx` default exports and `"use client"` components. Before you touch any other App Router API, read the matching guide under `node_modules/next/dist/docs/01-app/` (from the repo root).
- **Back-end order:**
  - Part 1 needs back-end part 1 applied: `check_achievements()` returns `celebrate`.
  - Parts 2–7 need back-end parts 2 and 3 (`get_achievements`, `mark_achievements_seen`, `pull_ceremony`) applied to the linked project before their PRs are merged.
  - The code compiles without the back-end, because the Supabase client is untyped. A read before the RPCs exist fails, and the page keeps its cache.
- **Branch from an updated `main`**, never from a feature branch. Part 1's branch is `feat/1308-achievements-web-celebrate`; each later part starts with `gh stack add <branch>` from the top branch.
- **Arkaik:** at the start of Part 1, move `V-achievements` to `development` with the `arkaik-mcp` `update_node` tool. `update_node` replaces metadata wholesale, so send the node's gherkin and values back unchanged. **If the `arkaik-mcp` tools are not available, say so and stop.** Never edit `docs/arkaik/bundle.json`.

## Ground rules (every task)

- **The client rule (spec §4):** clients decode, draw, localise and stay resilient; they compute nothing.
  - Every number, membership, order, level, family, "new" and "ready" flag is read from the payload.
  - Geometry may scale a served value: seat index → angle, level → radius, progress → length.
  - A ratio of a served count to a served denominator is geometry (`count / of`, `weeks_filled / full_weeks`).
  - A tally of served flags is display: how many seats are met, how many rungs are listed. Two platforms cannot disagree about it.
  - Never hard-code a rule threshold (14 days, 25 pebbles as a target, a ladder rung). Never sort, date-bucket or decide a state.
- **Commands** (from the repo root):
  - one test file: `npm run test --workspace=apps/web -- lib/data/achievement-results.test.ts`
  - the web suite: `npm run test --workspace=apps/web`
  - lint: `npm run lint --workspace=apps/web`
  - typecheck: `npx tsc --noEmit -p apps/web/tsconfig.json`. If types look stale after a JSON change, delete `apps/web/tsconfig.tsbuildinfo` first.
  - build (Parts 5 and 7): `npm run build --workspace=apps/web`
- **Message catalogs** (`apps/web/lib/i18n/messages/en.json`, `fr.json`):
  - Insert as text at the anchor each task gives, with the Edit tool.
  - Never rewrite or reformat the whole file.
  - `en.json` types every `useTranslations` key, so a missing EN key fails the typecheck. Keep FR in step by hand; French is "Tu".
- **No new CSS files.** Use Tailwind utilities over the CSS variables in `app/globals.css` (`--spine-*`, and `spine-spot` for a palette's spot ink). Inline `style` is only for computed values: palette variables and gauge fills.
- **Fixtures:**
  - The payload fixtures in `lib/data/__fixtures__/achievements-captured.ts` are hand-built to the back-end SQL's exact shape, in jsonb's text form.
  - When back-end Task 10 has written `packages/supabase/scripts/oracles/achievements/payloads/*.json`, do Task 34.
- **Commits:** conventional, lowercase, no period, one logical change each. Every message ends with:

  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```
- **Pushing, submitting and merging are the maintainer's.** If the auto-mode classifier blocks `gh stack push`/`submit`, prepare the command and hand it over.
- **PRs:**
  - The body opens with `Part of #1308`; the last part's opens with `Resolves #1308`.
  - Propose the issue's labels (`feat`, `web`, `ui`) and milestone (M65) and confirm them with the maintainer.
  - Never write a Kritik finding id (`F-…`) in a body.
  - Lab Note: `V-achievements` is covered in Arkaik. Per the maintainer's standing preference (Arkaik coverage replaces the Lab Note), label the PRs `no-lab-note`, and confirm on the first PR.

## Contract gaps found in the back-end plan

These were found while writing this plan; the web code tolerates each one. Raise them with the maintainer before back-end part 2 is applied, because each fix is one line in Task 9's SQL.

1. **The year carries no day target.**
   - The approved table shows `n/14`, caps at 14 and sets a reached cell in the done style. That needs the rule's 14, which the client must not hard-code.
   - Proposed: `achievements_year` returns `'days_target', 14` beside `listed`.
   - This plan decodes `days_target` when present (`AchievementYear.daysTarget`). Until then the day cells show the bare count with no gauge.
2. **The emotions sheet's order "by date" is not served.**
   - The board lists each family's met emotions by date. The seats come in wheel order, then slug, and carry no date of their own; the crossing pebble's `happened_at` disappears when that pebble is deleted.
   - This plan keeps the served seat order.
   - Proposed: a served `on` per seat (and per domain entry), as ladder levels have, plus either a per-family `met` order or an agreement that seat order stands.
3. **Explicit nulls inside `explorer`.**
   - `first_five` and `six_firsts` are `jsonb_agg` subqueries, and `twenty_five` is a scalar subquery. With no catalog rows each arrives as JSON `null`, against D17's "never null".
   - The web reads null as absent. The iOS and Android decoders must too, or the SQL should `coalesce` them to `'[]'` or drop the key.
4. **The payload dump is not verbatim.**
   - Task 5's `dump()` writes `JSON.stringify(parsed, null, 2)`. That loses jsonb's text form and numeric scale (`0.250` becomes `0.25`, `0.000` becomes `0`), which the standing cross-surface rule asks the client tests to replay.
   - Proposed: write the raw response text from a direct `fetch` of `/rest/v1/rpc/get_achievements`.
5. **One pebble, two celebrations.** A bright first pebble returns both `pebble-count-1` and `first-pebble` with `celebrate = true`, so the unlock moment shows two cards for one stone. Decide whether the server should keep one.
6. **Spec wording:** D12's tie-break lists "peaceful", but the family slug is `peace` (as the SQL's `wheel` array has it). The web uses `peace`.

Also on the web side, fixed in Part 1: the web never learned the `first_moment`, `first_pebble` and `first_five` families from #1254. Their celebration showed a raw message key.

---

## File map

All paths are under `apps/web/`.

| File | Part | Responsibility |
|---|---|---|
| `lib/data/achievement-results.ts` (+ test) | 1 | `check_achievements` rows with `celebrate`; `celebratedResults` |
| `lib/data/__fixtures__/achievements-captured.ts` | 1, 2 | the RPC payloads, as PostgREST returns them |
| `lib/activity/fire-achievement-check.ts` (+ test) | 1 | the mutation path celebrates only `celebrate` rows |
| `lib/data/data-provider.ts`, `lib/data/supabase-provider.ts` | 1, 2 | `celebrate`; `getAchievementsPayload`, `markAchievementsSeen`, `pullCeremony` |
| `lib/types.ts` | 1, 2 | the new families; the decoded page's types |
| `lib/i18n/useAchievementCopy.ts`, `components/profile/AchievementShelfBadge.tsx`, `components/achievements/AchievementBadge.tsx` | 1 | copy and icons for the new families |
| `lib/i18n/messages/{en,fr}.json` | 1, 4–7 | the copy |
| `lib/data/achievements-payload.ts` (+ test) | 2 | the `get_achievements` and `pull_ceremony` decoders |
| `lib/data/achievements-cache.ts` (+ test) | 2 | the per-user cache; cleared at sign-out |
| `lib/data/achievements-loader.ts` (+ test) | 2 | check → read, mark seen, pull outcome |
| `lib/data/useAchievementsPage.ts` | 2 | the page's hook |
| `lib/data/useSupabaseAuth.ts` | 2 | clears the cache with the drafts |
| `lib/achievements/geometry.ts`, `rosace.ts`, `shards.ts`, `skyline.ts`, `year-disc.ts`, `seals.ts` | 3 | the figures' geometry |
| `lib/achievements/palette.ts`, `explorer-model.ts`, `emotions-model.ts`, `domains-model.ts`, `ladder-model.ts`, `year-model.ts`, `copy.ts` (+ `figures`, `models` and `copy` tests) | 3 | the mapping from payload to figures and words |
| `components/achievements/HueGroup.tsx`, `AchievementSeal.tsx`, `GhostSeat.tsx`, `NewMark.tsx`, `FirstFiveMark.tsx`, `MarginLadder.tsx`, `CountCell.tsx`, `SkylineStage.tsx` | 4 | the atoms |
| `components/achievements/ExplorerWheel.tsx`, `EmotionRosace.tsx`, `DomainBand.tsx`, `PebbleSkyline.tsx`, `YearDisc.tsx` | 4 | the organisms |
| `components/achievements/AchievementCard.tsx`, `DaysKeptHeader.tsx`, `NextStepLine.tsx`, `ExplorerCard.tsx`, `EmotionsCard.tsx`, `DomainsCard.tsx`, `PebblesCard.tsx`, `YearCard.tsx`, `lib/hooks/useYearAria.ts` | 5 | the card shell, the header and the five cards |
| `components/achievements/AchievementsPageView.tsx`, `app/achievements/page.tsx`, `app/achievements/layout.tsx` | 5–7 | the page |
| `components/achievements/AchievementsView.tsx` | 5 | deleted (the old grid), with `AchievementBadge.tsx` |
| `components/sandbox/design/AchievementSpecimens.tsx`, `ComponentsSection.tsx`, `lib/design/specimens.ts` | 5 | the cards on `/sandbox/design`, from the fixtures |
| `lib/achievements/sheets.ts` (+ test), `lib/i18n/useReferenceNames.ts` | 6 | the sheet stack, links, reference names |
| `components/achievements/sheets/*.tsx` | 6 | the sheet shell, its molecules, the five sheets |
| `components/achievements/ReadyMark.tsx`, `CeremonyDialog.tsx` | 7 | the ceremony |

`useAchievements.ts` stays: the profile shelf still reads the catalog and the ledger through it.

---

# Part 1 — celebrate only what the server marks (branch `feat/1308-achievements-web-celebrate`)

Small and independent: it can ship as soon as back-end part 1 is applied.

### Task 1: Decode `celebrate`

**Files:**
- Create: `apps/web/lib/data/__fixtures__/achievements-captured.ts`
- Create: `apps/web/lib/data/achievement-results.ts`
- Test: `apps/web/lib/data/achievement-results.test.ts`
- Modify: `apps/web/lib/data/data-provider.ts` (`AchievementUnlockResult`)
- Modify: `apps/web/lib/data/supabase-provider.ts` (`checkAchievements`)

- [ ] **Step 1: Write the fixtures**

Create `apps/web/lib/data/__fixtures__/achievements-captured.ts`. Part 2 appends the page payloads to this file.

```ts
// Achievements payloads (#1308), as PostgREST returns them: a `returns table`
// RPC is a compact JSON array; a `returns jsonb` RPC is Postgres's jsonb text
// (`"key": value`, keys sorted by length then bytes, numerics at their scale).
// Never reformat them. Hand-built from the back-end plan's Task 3 and Task 9
// SQL until `ACHIEVEMENTS_DUMP=1` writes the real ones to
// packages/supabase/scripts/oracles/achievements/payloads/; swap them in
// then, byte for byte, and keep the VARIANTS fixture (it covers what one
// harness run cannot produce).

/** `check_achievements()` after a bright first pebble (spec D7, D8). */
export const CHECK_ACHIEVEMENTS_MIXED = String.raw`[{"slug":"pebble-count-1","karma_granted":5,"celebrate":true},{"slug":"first-pebble","karma_granted":0,"celebrate":true},{"slug":"emotion-first-joyful","karma_granted":0,"celebrate":false},{"slug":"domain-first-family","karma_granted":0,"celebrate":false},{"slug":"first-stone-medium","karma_granted":0,"celebrate":false},{"slug":"first-stone-highlight","karma_granted":0,"celebrate":false}]`

/** `check_achievements()` after a heavy first pebble: nothing celebrates (D8). */
export const CHECK_ACHIEVEMENTS_SILENT = String.raw`[{"slug":"pebble-count-1","karma_granted":5,"celebrate":false},{"slug":"first-pebble","karma_granted":0,"celebrate":false},{"slug":"emotion-first-sad","karma_granted":0,"celebrate":false},{"slug":"first-stone-small","karma_granted":0,"celebrate":false},{"slug":"first-stone-lowlight","karma_granted":0,"celebrate":false}]`

/** A pre-#1305 server: no `celebrate` column, and one malformed row. */
export const CHECK_ACHIEVEMENTS_LEGACY = String.raw`[{"slug":"pebble-count-10","karma_granted":10},{"karma_granted":3},{"slug":"glyph-count-10","karma_granted":null,"celebrate":"true"}]`
```

- [ ] **Step 2: Write the failing test**

Create `apps/web/lib/data/achievement-results.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { celebratedResults, unlockResultsFrom } from "./achievement-results"
import {
  CHECK_ACHIEVEMENTS_LEGACY,
  CHECK_ACHIEVEMENTS_MIXED,
  CHECK_ACHIEVEMENTS_SILENT,
} from "./__fixtures__/achievements-captured"

const rows = (json: string): unknown => JSON.parse(json)

describe("unlockResultsFrom", () => {
  it("decodes the celebrate column", () => {
    expect(unlockResultsFrom(rows(CHECK_ACHIEVEMENTS_MIXED)).slice(0, 3)).toEqual([
      { slug: "pebble-count-1", karmaGranted: 5, celebrate: true },
      { slug: "first-pebble", karmaGranted: 0, celebrate: true },
      { slug: "emotion-first-joyful", karmaGranted: 0, celebrate: false },
    ])
  })

  it("never celebrates a row that does not say true, and drops rows without a slug", () => {
    expect(unlockResultsFrom(rows(CHECK_ACHIEVEMENTS_LEGACY))).toEqual([
      { slug: "pebble-count-10", karmaGranted: 10, celebrate: false },
      { slug: "glyph-count-10", karmaGranted: 0, celebrate: false },
    ])
  })

  it("reads anything that is not an array as no rows", () => {
    expect(unlockResultsFrom(null)).toEqual([])
    expect(unlockResultsFrom({ slug: "pebble-count-1" })).toEqual([])
  })
})

describe("celebratedResults", () => {
  it("keeps only the rows the server celebrates", () => {
    expect(celebratedResults(unlockResultsFrom(rows(CHECK_ACHIEVEMENTS_MIXED))).map((r) => r.slug)).toEqual([
      "pebble-count-1",
      "first-pebble",
    ])
  })

  it("keeps nothing after a heavy first pebble", () => {
    expect(celebratedResults(unlockResultsFrom(rows(CHECK_ACHIEVEMENTS_SILENT)))).toEqual([])
  })
})
```

- [ ] **Step 3: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/data/achievement-results.test.ts`

Expected: FAIL with `Failed to resolve import "./achievement-results"`.

- [ ] **Step 4: Add `celebrate` to the result type**

In `apps/web/lib/data/data-provider.ts`, replace:

```ts
// One newly granted badge from `check_achievements()`. `karmaGranted` is what
// the ledger actually received at unlock time (0 for reward-less badges),
// never the catalog's current value.
export type AchievementUnlockResult = {
  slug: string
  karmaGranted: number
}
```

with:

```ts
// One newly granted badge from `check_achievements()`. `karmaGranted` is what
// the ledger actually received at unlock time (0 for reward-less badges),
// never the catalog's current value. `celebrate` is the server's verdict
// (#1308, spec D7–D8): false for contributions and for a level crossed by a
// heavy pebble, which are recorded silently.
export type AchievementUnlockResult = {
  slug: string
  karmaGranted: number
  celebrate: boolean
}
```

- [ ] **Step 5: Write the decoder**

Create `apps/web/lib/data/achievement-results.ts`:

```ts
import type { AchievementUnlockResult } from "@/lib/data/data-provider"

/**
 * `check_achievements()` rows, decoded (#1308). `celebrate` is the server's
 * verdict (spec 2026-10-29 D7, D8): contributions and a level crossed by a
 * heavy pebble arrive false. A row without the column, or with anything but
 * `true`, never celebrates: a first sadness must not be celebrated because a
 * server was a version behind. Rows without a slug are dropped.
 */
export function unlockResultsFrom(raw: unknown): AchievementUnlockResult[] {
  if (!Array.isArray(raw)) return []
  return raw.flatMap((row: unknown) => {
    if (typeof row !== "object" || row === null) return []
    const r = row as Record<string, unknown>
    if (typeof r.slug !== "string" || r.slug === "") return []
    const karma = r.karma_granted
    return [
      {
        slug: r.slug,
        karmaGranted: typeof karma === "number" && Number.isFinite(karma) ? karma : 0,
        celebrate: r.celebrate === true,
      },
    ]
  })
}

/** The rows the unlock moment shows (achievements design D13, page spec §4). */
export function celebratedResults(results: AchievementUnlockResult[]): AchievementUnlockResult[] {
  return results.filter((r) => r.celebrate)
}
```

- [ ] **Step 6: Use it in the provider**

In `apps/web/lib/data/supabase-provider.ts`, after the line `import { toConnectionPeer } from "@/lib/data/invite-api"`, add:

```ts
import { unlockResultsFrom } from "@/lib/data/achievement-results"
```

Then, in `checkAchievements()`, replace:

```ts
    const { data, error } = await this.supabase.rpc("check_achievements")
    if (error) throw new Error(`check_achievements failed: ${error.message}`)
    return ((data ?? []) as Record<string, unknown>[]).map((row) => ({
      slug: row.slug as string,
      karmaGranted: (row.karma_granted as number) ?? 0,
    }))
```

with:

```ts
    const { data, error } = await this.supabase.rpc("check_achievements")
    if (error) throw new Error(`check_achievements failed: ${error.message}`)
    return unlockResultsFrom(data)
```

- [ ] **Step 7: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/data/achievement-results.test.ts`

Expected: PASS, 5 tests.

- [ ] **Step 8: Commit**

```bash
git add apps/web/lib/data/__fixtures__/achievements-captured.ts apps/web/lib/data/achievement-results.ts apps/web/lib/data/achievement-results.test.ts apps/web/lib/data/data-provider.ts apps/web/lib/data/supabase-provider.ts
git commit -m "feat(web): decode the celebrate flag from check_achievements"
```

### Task 2: The unlock moment shows only celebrated rows

**Files:**
- Modify: `apps/web/lib/activity/fire-achievement-check.ts`
- Test: `apps/web/lib/activity/fire-achievement-check.test.ts`

- [ ] **Step 1: Write the failing test**

Create `apps/web/lib/activity/fire-achievement-check.test.ts`:

```ts
import { afterEach, describe, expect, it, vi } from "vitest"
import { fireAchievementCheck } from "./fire-achievement-check"
import { subscribeAchievementMoment, type AchievementMoment } from "./achievement-moment"
import { unlockResultsFrom } from "@/lib/data/achievement-results"
import {
  CHECK_ACHIEVEMENTS_MIXED,
  CHECK_ACHIEVEMENTS_SILENT,
} from "@/lib/data/__fixtures__/achievements-captured"
import type { Achievement } from "@/lib/types"

const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

function providerReturning(json: string) {
  return {
    checkAchievements: vi.fn(async () => unlockResultsFrom(JSON.parse(json))),
    getAchievements: vi.fn(async (): Promise<Achievement[]> => []),
  }
}

let unsubscribe: () => void = () => {}
afterEach(() => unsubscribe())

describe("fireAchievementCheck", () => {
  it("presents only the rows the server celebrates", async () => {
    const moments: AchievementMoment[] = []
    unsubscribe = subscribeAchievementMoment((m) => moments.push(m))
    fireAchievementCheck(providerReturning(CHECK_ACHIEVEMENTS_MIXED))
    await flush()
    expect(moments).toHaveLength(1)
    expect(moments[0].results.map((r) => r.slug)).toEqual(["pebble-count-1", "first-pebble"])
  })

  it("presents nothing, and fetches no catalog, when nothing celebrates", async () => {
    const moments: AchievementMoment[] = []
    unsubscribe = subscribeAchievementMoment((m) => moments.push(m))
    const provider = providerReturning(CHECK_ACHIEVEMENTS_SILENT)
    fireAchievementCheck(provider)
    await flush()
    expect(moments).toEqual([])
    expect(provider.getAchievements).not.toHaveBeenCalled()
  })

  it("logs a failed check and presents nothing", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    const moments: AchievementMoment[] = []
    unsubscribe = subscribeAchievementMoment((m) => moments.push(m))
    fireAchievementCheck({
      checkAchievements: vi.fn(async () => {
        throw new Error("check_achievements failed: boom")
      }),
      getAchievements: vi.fn(async (): Promise<Achievement[]> => []),
    })
    await flush()
    expect(moments).toEqual([])
    expect(warn).toHaveBeenCalledWith("[achievements] check_achievements failed", expect.any(Error))
    warn.mockRestore()
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/activity/fire-achievement-check.test.ts`

Expected: FAIL.
- "presents only the rows the server celebrates" receives all six slugs.
- "presents nothing" receives one moment.

- [ ] **Step 3: Filter on `celebrate`**

Replace the whole of `apps/web/lib/activity/fire-achievement-check.ts`. The parameter narrows to the two methods it calls, and every caller still passes the full provider.

```ts
import { presentAchievementMoment } from "@/lib/activity/achievement-moment"
import { celebratedResults } from "@/lib/data/achievement-results"
import type { DataProvider } from "@/lib/data/data-provider"

/**
 * Fire-and-forget achievement evaluation after a count-changing mutation (D5).
 * Call AFTER the mutation succeeded and the store snapshot is pushed — this
 * never blocks and never throws: the mutation already landed, and a failed
 * check self-heals by construction (the next call re-evaluates live counts).
 *
 * Only the rows the server marks `celebrate` reach the moment (#1308, page
 * spec D7–D8): emotion, domain and six-firsts contributions, and a level
 * crossed by a heavy pebble, are recorded silently and surface later as a
 * "new" mark on the achievements page.
 *
 * The catalog fetch runs only when something celebrates (rare), and joins the
 * returned slugs to their rows so the moment can localize titles from family
 * i18n + admin overrides inside React — no translator plumbing needed at the
 * six call sites.
 *
 * This is the MUTATION path, and only it celebrates (D13): the screen-open
 * call on the achievements page is the retroactive grant and can return a
 * veteran's entire history at once, so it never celebrates.
 */
export function fireAchievementCheck(provider: Pick<DataProvider, "checkAchievements" | "getAchievements">): void {
  void provider
    .checkAchievements()
    .then(async (results) => {
      const celebrated = celebratedResults(results)
      if (celebrated.length === 0) return
      const catalog = await provider.getAchievements()
      presentAchievementMoment({ results: celebrated, catalog })
    })
    .catch((err) => {
      console.warn("[achievements] check_achievements failed", err)
    })
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/activity/fire-achievement-check.test.ts`

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/activity/fire-achievement-check.ts apps/web/lib/activity/fire-achievement-check.test.ts
git commit -m "feat(web): celebrate only the unlocks the server marks celebrate"
```

### Task 3: Copy and icons for the five families the web did not know

`check_achievements()` can now return `first_moment`, `first_pebble` (#1254), `first_stone` and, through the ledger, `first_five` and `set_complete` (#1305). Their titles showed a raw message key in the unlock moment and on the profile shelf.

**Files:**
- Modify: `apps/web/lib/types.ts` (`AchievementFamily`)
- Modify: `apps/web/lib/i18n/messages/en.json`, `fr.json`
- Modify: `apps/web/lib/i18n/useAchievementCopy.ts`
- Modify: `apps/web/components/profile/AchievementShelfBadge.tsx`, `apps/web/components/achievements/AchievementBadge.tsx`

- [ ] **Step 1: Extend the family union**

In `apps/web/lib/types.ts`, replace:

```ts
  | "glyph_count"
  | "glyph_sales"
```

with:

```ts
  | "glyph_count"
  | "glyph_sales"
  // #1254 (the first five) and #1305 (the six firsts, set completions):
  | "first_moment"
  | "first_pebble"
  | "first_five"
  | "first_stone"
  | "set_complete"
```

- [ ] **Step 2: Add the families' group names**

In `apps/web/lib/i18n/messages/en.json`, replace:

```json
      "glyph_sales": "Market"
    },
    "family": {
```

with:

```json
      "glyph_sales": "Market",
      "first_moment": "First steps",
      "first_pebble": "First steps",
      "first_five": "First steps",
      "first_stone": "The six firsts",
      "set_complete": "Complete sets"
    },
    "family": {
```

In `apps/web/lib/i18n/messages/fr.json`, replace:

```json
      "glyph_sales": "Marché"
    },
    "family": {
```

with:

```json
      "glyph_sales": "Marché",
      "first_moment": "Premiers pas",
      "first_pebble": "Premiers pas",
      "first_five": "Premiers pas",
      "first_stone": "Les six premières fois",
      "set_complete": "Ensembles complets"
    },
    "family": {
```

- [ ] **Step 3: Add the families, the stone kinds and the completions**

In `en.json`, replace the end of the `achievements` object:

```json
        "description": "{threshold, plural, one {Sell your first glyph in the market.} other {Make # glyph sales in the market.}}"
      }
    }
  },
  "wallet": {
```

with:

```json
        "description": "{threshold, plural, one {Sell your first glyph in the market.} other {Make # glyph sales in the market.}}"
      },
      "first_moment": {
        "title": "First moment",
        "description": "Keep your first moment."
      },
      "first_pebble": {
        "title": "First pebble",
        "description": "Turn a moment into your first pebble."
      },
      "first_five": {
        "title": "Blue pebble",
        "description": "Finish your first five steps and claim the blue pebble."
      },
      "first_stone": {
        "title": "First stone: {name}",
        "description": "Keep your first pebble of this kind: {name}."
      },
      "set_complete": {
        "title": "{name}",
        "description": "A whole set, complete."
      }
    },
    "stoneKind": {
      "small": "Small",
      "medium": "Medium",
      "large": "Large",
      "lowlight": "Lowlight",
      "neutral": "Neutral",
      "highlight": "Highlight"
    },
    "completion": {
      "emotions": {
        "title": "Every emotion met",
        "description": "You have met every emotion the wheel holds."
      },
      "domains": {
        "title": "Every part of your life",
        "description": "Each domain of your life now holds a pebble."
      },
      "six_firsts": {
        "title": "The six firsts",
        "description": "Small, medium and large; lowlight, neutral and highlight: each has its first pebble."
      }
    }
  },
  "wallet": {
```

In `fr.json`, replace:

```json
        "description": "{threshold, plural, one {Un pebbler adopte l'un de tes glyphes.} other {Tes glyphes trouvent preneur # fois.}}"
      }
    }
  },
  "wallet": {
```

with:

```json
        "description": "{threshold, plural, one {Un pebbler adopte l'un de tes glyphes.} other {Tes glyphes trouvent preneur # fois.}}"
      },
      "first_moment": {
        "title": "Premier moment",
        "description": "Garde ton premier moment."
      },
      "first_pebble": {
        "title": "Premier galet",
        "description": "Fais de l'un de tes moments ton premier galet."
      },
      "first_five": {
        "title": "Galet bleu",
        "description": "Termine tes cinq premiers pas et réclame le galet bleu."
      },
      "first_stone": {
        "title": "Première pierre : {name}",
        "description": "Garde ton premier galet de ce genre : {name}."
      },
      "set_complete": {
        "title": "{name}",
        "description": "Un ensemble entier, complet."
      }
    },
    "stoneKind": {
      "small": "Petit",
      "medium": "Moyen",
      "large": "Grand",
      "lowlight": "Creux",
      "neutral": "Neutre",
      "highlight": "Temps fort"
    },
    "completion": {
      "emotions": {
        "title": "Toutes les émotions rencontrées",
        "description": "Tu as rencontré chaque émotion de la roue."
      },
      "domains": {
        "title": "Chaque part de ta vie",
        "description": "Chaque domaine de ta vie tient désormais un galet."
      },
      "six_firsts": {
        "title": "Les six premières fois",
        "description": "Petit, moyen et grand ; creux, neutre et temps fort : chacun a son premier galet."
      }
    }
  },
  "wallet": {
```

Check that both files still parse: `node -e "for (const l of ['en','fr']) JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/'+l+'.json','utf8'))"`. Expected: no output.

- [ ] **Step 4: Name a first stone's kind and a completion's set**

In `apps/web/lib/i18n/useAchievementCopy.ts`, replace:

```ts
    return achievement.slug.replace(/^domain-first-/, "")
  }
  return ""
}
```

with:

```ts
    return achievement.slug.replace(/^domain-first-/, "")
  }
  // #1305: the six firsts name their kind, a completion its set, both by slug.
  if (achievement.family === "first_stone") {
    const kind = achievement.slug.replace(/^first-stone-/, "")
    const key = `achievements.stoneKind.${kind}`
    return t.has(key) ? t(key) : kind
  }
  if (achievement.family === "set_complete") {
    const stack = achievement.slug.replace(/^complete-/, "").replace(/-/g, "_")
    const key = `achievements.completion.${stack}.title`
    return t.has(key) ? t(key) : achievement.slug
  }
  return ""
}
```

Then, in the doc comment above `useAchievementCopy`, replace:

```ts
 * `{threshold}` and — for emotion_first / domain_first — the reference
 * entity's localized display `{name}` interpolated. When a row carries admin
```

with:

```ts
 * `{threshold}` and — for emotion_first / domain_first — the reference
 * entity's localized display `{name}` interpolated (first_stone and
 * set_complete name their kind and their set the same way). When a row carries admin
```

- [ ] **Step 5: Give the families an icon**

The `FAMILY_ICONS` records are typed `Record<AchievementFamily, LucideIcon>`, so the typecheck fails until both list the new families.

In `apps/web/components/profile/AchievementShelfBadge.tsx`, replace the lucide import with:

```tsx
import {
  Award,
  CircleDashed,
  CircleDot,
  Compass,
  Gem,
  Heart,
  Hexagon,
  Layers,
  ListChecks,
  PenTool,
  Shapes,
  Store,
  Trophy,
  Users,
} from "lucide-react"
```

In `apps/web/components/achievements/AchievementBadge.tsx`, replace the lucide import with the same list plus `Lock` after `ListChecks`. In both files, replace:

```tsx
  glyph_count: Shapes,
  glyph_sales: Store,
}
```

with:

```tsx
  glyph_count: Shapes,
  glyph_sales: Store,
  first_moment: CircleDashed,
  first_pebble: CircleDot,
  first_five: ListChecks,
  first_stone: Hexagon,
  set_complete: Award,
}
```

- [ ] **Step 6: Typecheck and lint**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`

Expected: no errors. The lint's one existing warning, in `app/settings/page.tsx`, is on `main` too.

- [ ] **Step 7: Commit**

```bash
git add apps/web/lib/types.ts apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json apps/web/lib/i18n/useAchievementCopy.ts apps/web/components/profile/AchievementShelfBadge.tsx apps/web/components/achievements/AchievementBadge.tsx
git commit -m "fix(web): title and draw the first-steps, six-firsts and completion families"
```

### Task 4: Verify Part 1 and open its PR

- [ ] **Step 1:** Run `npm run test --workspace=apps/web`. Expected: every file passes.
- [ ] **Step 2:** Check by hand, on a local dev server (`npm run dev --workspace=apps/web`, signed in as a throwaway account):
  - a first pebble that is bright shows the unlock moment;
  - a first pebble that is heavy (lowlight) shows none.
- [ ] **Step 3:** Open the PR with `gh stack`, titled `feat(web): celebrate only what the server marks celebrate`. Its body opens with `Part of #1308`; propose `feat`, `web`, `ui` and M65.

---

# Part 2 — the contract (branch `feat/1308-achievements-web-contract`)

Start the branch from Part 1's: `gh stack add feat/1308-achievements-web-contract`. This part adds the decoders, the provider calls, the cache, the loader and the hook. Nothing renders yet.

### Task 5: The decoded page's types

**Files:**
- Modify: `apps/web/lib/types.ts`

- [ ] **Step 1: Add the types after `AchievementUnlock`**

In `apps/web/lib/types.ts`, replace:

```ts
// One row of the caller's `achievement_unlocks` ledger (permanent by design).
export type AchievementUnlock = {
  achievementId: string
  unlockedAt: string
}
```

with:

```ts
// One row of the caller's `achievement_unlocks` ledger (permanent by design).
export type AchievementUnlock = {
  achievementId: string
  unlockedAt: string
}

// ---------------------------------------------------------------------------
// The achievements page (#1308): `get_achievements(p_tz)` decoded. The server
// decides every number, order, level, family and flag (spec 2026-10-29 §4);
// these types only carry them. An absent or malformed section is null, and its
// card is hidden; it is never an error.
// ---------------------------------------------------------------------------

/** The five cards whose sheets `mark_achievements_seen` records. */
export type AchievementCard = "explorer" | "emotions" | "domains" | "pebbles" | "year"

/** The three sets whose completion is offered, then pulled (D9). */
export type CeremonyStack = "emotions" | "domains" | "six_firsts"

/** The six firsts (D5): three intensities, three valences. */
export type StoneKind = "small" | "medium" | "large" | "lowlight" | "neutral" | "highlight"

/** `emotion_categories.slug`, in the wheel's order. */
export type EmotionFamily = "joy" | "pride" | "anger" | "fear" | "shame" | "sadness" | "peace"

/** What the domains' coverage means (D11), never a count. */
export type DomainCoverage = "none" | "few" | "third" | "half" | "most" | "all"

/** The Explorer wheel's five rings, centre out (D6). */
export type ExplorerRing = "first_five" | "six_firsts" | "twenty_five" | "domains" | "emotions"

/** The crossing pebble's snapshot (D15): enough to print its seal and open it. */
export type CrossingPebble = {
  momentId: string
  name: string | null
  /** The server's instant, as sent. */
  happenedAt: string
  emotionId: string | null
  intensity: Intensity
  positiveness: Positiveness
}

/** A set made whole (D9). `ready` is the server's clock, so no client compares one. */
export type AchievementCompletion = {
  completedAt: string
  offeredFrom: string
  ready: boolean
  pulledAt: string | null
}

export type FirstFiveSubject = { kind: string; id: string; name: string | null }

export type FirstFiveItem = {
  slug: string
  done: boolean
  isNew: boolean
  /** `YYYY-MM-DD`, local to the zone the page was read in. */
  on: string | null
  subject: FirstFiveSubject | null
}

export type SixFirst = { kind: StoneKind; done: boolean; isNew: boolean; pebble: CrossingPebble | null }

export type TwentyFive = { count: number; done: boolean; isNew: boolean; pebble: CrossingPebble | null }

export type AchievementsExplorer = {
  firstPebble: CrossingPebble | null
  firstFive: FirstFiveItem[]
  sixFirsts: SixFirst[]
  twentyFive: TwentyFive | null
  ringsDone: ExplorerRing[]
  completion: AchievementCompletion | null
}

export type EmotionSeat = {
  seat: number
  emotionId: string
  /** Null for a family this build does not know: it draws in ink. */
  family: EmotionFamily | null
  met: boolean
  isNew: boolean
  pebble: CrossingPebble | null
}

export type AchievementsEmotions = {
  count: number
  of: number
  /** Wheel order, then slug, as served. */
  seats: EmotionSeat[]
  completion: AchievementCompletion | null
}

export type DomainEntry = { domainId: string; isNew: boolean; pebble: CrossingPebble | null }

export type AchievementsDomains = {
  count: number
  of: number
  /** Null for a tier this build does not know: its sentence is hidden. */
  coverage: DomainCoverage | null
  /** Newest first, as served. */
  entered: DomainEntry[]
  notYet: string[]
  completion: AchievementCompletion | null
}

export type LadderLevel = { threshold: number; on: string | null; isNew: boolean; pebble: CrossingPebble | null }

export type LadderNext = { threshold: number; left: number; progress: number }

/** One counting stack (D3): pebbles, glyphs carved or glyph sales. */
export type CountingLadder = {
  count: number
  levels: LadderLevel[]
  next: LadderNext | null
  ladder: number[]
  thenEvery: number | null
}

export type MonthState = "upcoming" | "quiet" | "kept"
export type MonthLevel = 0 | 1 | 2

export type YearMonth = {
  month: number
  state: MonthState
  /** The month's colour (D12); null without a pebble or for an unknown family. */
  family: EmotionFamily | null
  momentDays: number
  pebbleDays: number
  fullWeeks: number
  weeksFilled: number
  level: MonthLevel
  progress: number
}

export type YearNextStep = { month: number; needs: "moments" | "pebbles"; daysLeft: number; reachable: boolean }

export type AchievementYear = {
  year: number
  current: boolean
  months: YearMonth[]
  /** The table's months, newest first, as served. */
  listed: number[]
  nextStep: YearNextStep | null
  /** Days a month needs per level (`days_target`); null until the server sends it. */
  daysTarget: number | null
}

export type AchievementsPage = {
  /** `YYYY-MM-DD`: the account's first day, local to the reader (D2). */
  since: string | null
  daysKept: number | null
  explorer: AchievementsExplorer | null
  emotions: AchievementsEmotions | null
  domains: AchievementsDomains | null
  pebbles: CountingLadder | null
  glyphsCarved: CountingLadder | null
  glyphSales: CountingLadder | null
  /** Newest first, as served. */
  years: AchievementYear[]
}
```

`Intensity` and `Positiveness` are already defined at the top of the file.

- [ ] **Step 2: Typecheck**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json`. Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/types.ts
git commit -m "feat(web): type the achievements page payload"
```

### Task 6: The page payload fixtures

The fixtures are generated, not typed by hand, so their shape matches back-end Task 9's SQL exactly, in Postgres's jsonb text form:
- `"key": value` spacing, and keys sorted by length, then bytes;
- numerics at their scale (`0.247`, `0.500`, `0.000`), and `0` where the SQL writes a literal 0;
- every `achievement_opt` key omitted when its value is null.

**Files:**
- Modify: `apps/web/lib/data/__fixtures__/achievements-captured.ts` (append)

- [ ] **Step 1: Write the generator** to a scratch file outside the repo, for example `$TMPDIR/gen-achievements-fixtures.py`. It is not committed: the fixtures are its output, and the tests pin their lengths.

```python
import json, re
from decimal import Decimal as D

class Num:
    def __init__(s, t): s.t = t
def ser(v):
    if isinstance(v, Num): return v.t
    if v is True: return "true"
    if v is False: return "false"
    if v is None: return "null"
    if isinstance(v, int): return str(v)
    if isinstance(v, str): return json.dumps(v, ensure_ascii=False)
    if isinstance(v, list): return "[" + ", ".join(ser(x) for x in v) + "]"
    if isinstance(v, dict):
        ks = sorted(v.keys(), key=lambda k: (len(k.encode()), k.encode()))
        return "{" + ", ".join(json.dumps(k) + ": " + ser(v[k]) for k in ks) + "}"
    raise TypeError(v)

src = open('apps/web/lib/config/emotions.ts').read()
EMO = {m[1]: m[0] for m in re.findall(r'id: "([^"]+)", slug: "([^"]+)"', src)}
dsrc = open('apps/web/lib/config/domains.ts').read()
DOM = {m[1]: m[0] for m in re.findall(r'id: "([^"]+)", slug: "([^"]+)"', dsrc)}
FAM = {
 'joy': ['amazed','amused','excited','happy','joyful','passionate'],
 'pride': ['brave','confident','proud'],
 'anger': ['angry','annoyed','disgusted','frustrated','irritated','jealous'],
 'fear': ['anxious','overwhelmed','scared','stressed','worried'],
 'shame': ['ashamed','embarrassed','guilty','indifferent'],
 'sadness': ['disappointed','discouraged','drained','hopeless','lonely','sad'],
 'peace': ['calm','content','grateful','hopeful','peaceful','relieved','satisfied','surprised'],
}
SEATS = [(f, s) for f in FAM for s in sorted(FAM[f])]
assert len(SEATS) == 38
n = [0]
def mid():
    n[0] += 1
    return "6b1f0a52-%04x-4c1e-9a3d-%012x" % (n[0], n[0] * 7919)
def peb(name, at, emo, i, p):
    return {"moment_id": mid(), "name": name, "happened_at": at, "emotion_id": EMO[emo], "intensity": i, "positiveness": p}
LADDER = [1, 10, 25, 50, 100, 250, 500, 1000]

# ---------------------------------------------------------------- veteran
first = peb("Mara moves in", "2026-03-02T18:20:00Z", "joyful", 2, 1)
coffee = peb("Coffee with Elio", "2026-03-03T08:05:00Z", "content", 1, 0)
argue = peb("Argument about money", "2026-03-14T21:40:00Z", "frustrated", 3, -1)
explorer = {
 "first_pebble": first,
 "first_five": [
  {"slug": "first-moment", "done": True, "new": False, "on": "2026-03-01", "subject": {"kind": "moment", "id": mid(), "name": "Boxes everywhere"}},
  {"slug": "first-pebble", "done": True, "new": False, "on": "2026-03-02", "subject": {"kind": "pebble", "id": first["moment_id"], "name": "Mara moves in"}},
  {"slug": "first-collection", "done": True, "new": False, "on": "2026-03-09", "subject": {"kind": "collection", "id": mid(), "name": "Spring"}},
  {"slug": "first-soul", "done": True, "new": False, "on": "2026-03-02", "subject": {"kind": "soul", "id": mid(), "name": "Mara"}},
  {"slug": "first-glyph", "done": True, "new": False, "on": "2026-04-12", "subject": {"kind": "glyph", "id": mid(), "name": "Wave"}},
 ],
 "six_firsts": [
  {"kind": "small", "done": True, "new": False, "pebble": coffee},
  {"kind": "medium", "done": True, "new": False, "pebble": first},
  {"kind": "large", "done": True, "new": False, "pebble": argue},
  {"kind": "lowlight", "done": True, "new": False, "pebble": argue},
  {"kind": "neutral", "done": True, "new": False, "pebble": coffee},
  {"kind": "highlight", "done": True, "new": False, "pebble": first},
 ],
 "twenty_five": {"count": 25, "done": True, "new": False, "pebble": peb("Nadia's birthday", "2026-03-29T19:00:00Z", "happy", 2, 1)},
 "rings_done": ["first_five", "six_firsts", "twenty_five"],
 "completion": {"completed_at": "2026-03-14T21:41:07Z", "offered_from": "2026-03-15T05:00:00Z", "ready": True},
}
MET = {'amazed','amused','excited','happy','joyful','confident','proud','angry','annoyed','frustrated','anxious','stressed','worried','embarrassed','disappointed','lonely','sad','calm','content','grateful','hopeful','peaceful','relieved'}
assert len(MET) == 23
NAMES = iter(["Night swim at the lake","The Elio phone call","Concert in the park","Bruno's new job","Mara moves in","Late train home","Inès draws the house","Kitchen flood","The landlord again","Bike stolen","Exam results","Waiting for the doctor","Jury duty letter","Wrong name at dinner","The flat we lost","Sunday alone","Grandpa's chair","Morning run","Coffee with Elio","Thank-you note","First warm day","Rain on the roof","Keys found"])
seats = []
for k, (f, s) in enumerate(SEATS):
    seat = {"seat": k, "emotion_id": EMO[s], "family": f, "met": s in MET, "new": s in ("sad", "peaceful")}
    if s in MET:
        name = next(NAMES)
        if s == "joyful": seat["pebble"] = first
        elif s == "content": seat["pebble"] = coffee
        elif s == "frustrated": seat["pebble"] = argue
        else:
            sz = 1 + (k % 3); val = -1 if f in ("anger","fear","shame","sadness") else (0 if f == "peace" else 1)
            seat["pebble"] = peb(name, "2026-%02d-%02dT12:00:00Z" % (3 + k % 8, 1 + (k * 3) % 27), s, sz, val)
    seats.append(seat)
emotions = {"count": 23, "of": 38, "seats": seats}
CAME = ["family","partner","friends","work","health","passions","self-care","tasks","identity","travel","community","education","sport","event"]
NOT = sorted(set(DOM) - set(CAME))
assert len(CAME) == 14 and len(NOT) == 4
entered = []
for i, d in enumerate(CAME):
    entered.append({"domain_id": DOM[d], "new": d == "event", "pebble": peb("First in %s" % d, "2026-%02d-%02dT10:30:00Z" % (3 + i // 2, 2 + i), "calm", 2, 0)})
entered.reverse()
domains = {"count": 14, "of": 18, "coverage": "most", "entered": entered, "not_yet": [DOM[d] for d in NOT]}
def lvl(t, on, p=None):
    o = {"threshold": t, "on": on, "new": False}
    if p: o["pebble"] = p
    return o
pebbles = {"count": 137, "levels": [lvl(1, "2026-03-02", first), lvl(10, "2026-03-11", peb("Bread that rose", "2026-03-11T17:00:00Z", "satisfied", 1, 1)),
  lvl(25, "2026-03-29", explorer["twenty_five"]["pebble"]), lvl(50, "2026-05-03", peb("Tide pools", "2026-05-03T15:10:00Z", "amazed", 2, 1)),
  lvl(100, "2026-08-16", peb("Jules's wedding", "2026-08-16T20:00:00Z", "joyful", 3, 1))],
  "next": {"threshold": 250, "left": 113, "progress": Num("0.247")}, "ladder": LADDER, "then_every": 500}
glyphs_carved = {"count": 12, "levels": [lvl(10, "2026-06-02")], "next": {"threshold": 25, "left": 13, "progress": Num("0.133")}, "ladder": [10, 25, 50, 75, 100]}
glyph_sales = {"count": 3, "levels": [lvl(1, "2026-07-19")], "next": {"threshold": 5, "left": 2, "progress": Num("0.500")}, "ladder": [1, 5, 10, 25, 50]}
def month(m, state, md, pd, fw, wf, level, progress, family=None):
    o = {"month": m, "state": state, "moment_days": md, "pebble_days": pd, "full_weeks": fw, "weeks_filled": wf, "level": level, "progress": progress}
    if family: o["family"] = family
    return o
UP = lambda m: month(m, "upcoming", 0, 0, 0, 0, 0, 0)
months = [month(1, "quiet", 0, 0, 3, 0, 0, Num("0.000")), month(2, "quiet", 0, 0, 4, 0, 0, Num("0.000")),
  month(3, "kept", 20, 16, 4, 3, 1, Num("0.750"), "joy"), month(4, "kept", 18, 12, 3, 3, 1, Num("0.857"), "sadness"),
  month(5, "kept", 17, 13, 4, 4, 1, Num("0.929"), "peace"), month(6, "kept", 16, 14, 4, 3, 1, Num("0.750"), "joy"),
  month(7, "kept", 15, 12, 3, 3, 1, Num("0.857"), "pride"), month(8, "kept", 22, 18, 4, 4, 2, 0, "joy"),
  month(9, "kept", 16, 11, 3, 3, 1, Num("0.786"), "fear"), month(10, "kept", 19, 13, 3, 2, 1, Num("0.667"), "sadness"),
  month(11, "kept", 3, 2, 0, 0, 0, Num("0.214"), "joy"), UP(12)]
years = [{"year": 2026, "current": True, "months": months, "listed": [11, 10, 9, 8, 7, 6, 5, 4, 3],
  "next_step": {"month": 11, "needs": "moments", "days_left": 11, "reachable": True}}]
veteran = {"since": "2026-03-01", "days_kept": 249, "explorer": explorer, "emotions": emotions, "domains": domains,
  "pebbles": pebbles, "glyphs_carved": glyphs_carved, "glyph_sales": glyph_sales, "years": years}

# ---------------------------------------------------------------- newcomer
nc_explorer = {
 "first_five": [{"slug": s, "done": False, "new": False} for s in ["first-moment","first-pebble","first-collection","first-soul","first-glyph"]],
 "six_firsts": [{"kind": k, "done": False, "new": False} for k in ["small","medium","large","lowlight","neutral","highlight"]],
 "twenty_five": {"count": 0, "done": False, "new": False}, "rings_done": []}
nc_emotions = {"count": 0, "of": 38, "seats": [{"seat": k, "emotion_id": EMO[s], "family": f, "met": False, "new": False} for k, (f, s) in enumerate(SEATS)]}
nc_domains = {"count": 0, "of": 18, "coverage": "none", "entered": [], "not_yet": [DOM[d] for d in sorted(DOM)]}
nc_months = [month(1, "quiet", 0, 0, 3, 0, 0, Num("0.000")), month(2, "quiet", 0, 0, 4, 0, 0, Num("0.000")), month(3, "quiet", 0, 0, 4, 0, 0, Num("0.000")),
  month(4, "quiet", 0, 0, 3, 0, 0, Num("0.000")), month(5, "quiet", 0, 0, 4, 0, 0, Num("0.000")), month(6, "quiet", 0, 0, 4, 0, 0, Num("0.000")),
  month(7, "quiet", 0, 0, 3, 0, 0, Num("0.000")), month(8, "quiet", 0, 0, 4, 0, 0, Num("0.000")), month(9, "quiet", 0, 0, 3, 0, 0, Num("0.000")),
  month(10, "quiet", 0, 0, 3, 0, 0, Num("0.000")), UP(11), UP(12)]
newcomer = {"since": "2026-10-29", "days_kept": 1, "explorer": nc_explorer, "emotions": nc_emotions, "domains": nc_domains,
  "pebbles": {"count": 0, "levels": [], "next": {"threshold": 1, "left": 1, "progress": Num("0.000")}, "ladder": LADDER, "then_every": 500},
  "glyphs_carved": {"count": 0, "levels": [], "next": {"threshold": 10, "left": 10, "progress": Num("0.000")}, "ladder": [10, 25, 50, 75, 100]},
  "glyph_sales": {"count": 0, "levels": [], "next": {"threshold": 1, "left": 1, "progress": Num("0.000")}, "ladder": [1, 5, 10, 25, 50]},
  "years": [{"year": 2026, "current": True, "months": nc_months, "listed": [], "next_step": {"month": 10, "needs": "moments", "days_left": 14, "reachable": False}}]}

# ---------------------------------------------------------------- variants
variants = {
 "streak": 3,
 "explorer": {
  "first_pebble": {"moment_id": "9d1c7a10-0000-4000-8000-000000000001", "name": None, "happened_at": "2026-03-02T18:20:00.376669+00:00", "emotion_id": EMO["joyful"], "intensity": 4, "positiveness": -2},
  "first_five": [{"slug": "first-moment", "done": True, "new": True, "on": "2026-03-01", "subject": {"kind": "moment", "id": "9d1c7a10-0000-4000-8000-000000000002", "name": None}},
                 {"slug": "first-dream", "done": False, "new": False}, {"done": True}],
  "six_firsts": None, "twenty_five": None, "rings_done": ["first_five", "seven_seas"], "completion": {}},
 "emotions": {"count": 2, "of": 38, "seats": [
  {"seat": 0, "emotion_id": EMO["amazed"], "family": "awe", "met": True, "new": False, "pebble": None},
  {"seat": 1, "emotion_id": EMO["amused"], "family": "joy", "met": True, "new": True},
  {"seat": 2, "family": "joy", "met": False, "new": False}],
  "completion": {"completed_at": "2026-09-30T21:00:00Z", "offered_from": "2026-10-01T04:00:00Z", "ready": False, "pulled_at": "2026-10-02T07:00:00Z"}},
 "domains": {"count": 3, "of": 18, "coverage": "plenty", "entered": [{"domain_id": DOM["work"], "new": False}, {"new": True}], "not_yet": None},
 "pebbles": {"count": 10234, "levels": None, "ladder": LADDER, "then_every": 500},
 "glyphs_carved": {"count": "12", "levels": [], "ladder": [10, 25, 50, 75, 100]},
 "years": [{"year": 2026, "current": True, "days_target": 14,
   "months": [{"month": 5, "state": "resting", "family": "awe", "moment_days": 15, "pebble_days": 15, "full_weeks": 4, "weeks_filled": 4, "level": 3, "progress": Num("1.5")},
              {"month": 13, "state": "kept", "level": 1}],
   "listed": [5, 0, 13], "next_step": {"month": 5, "needs": "sleep", "days_left": 3, "reachable": True}},
  {"current": False}],
}

DOCS = {
 "GET_ACHIEVEMENTS_VETERAN": "`get_achievements('Europe/Paris')` on 4 November 2026 for the approved boards' veteran: 137 pebbles, 23 of 38 emotions, 14 of 18 domains, the six firsts complete and ready.",
 "GET_ACHIEVEMENTS_NEWCOMER": "`get_achievements('Europe/Paris')` on 29 October 2026 for an account a day old: every key the SQL omits before a first pebble is absent.",
 "GET_ACHIEVEMENTS_VARIANTS": "Not a server payload: the tolerance cases one harness run cannot produce (microsecond `+00:00`, explicit nulls, unknown enums, a malformed year, the proposed `days_target`).",
}
for k in ["GET_ACHIEVEMENTS_VETERAN", "GET_ACHIEVEMENTS_NEWCOMER", "GET_ACHIEVEMENTS_VARIANTS"]:
    v = {"GET_ACHIEVEMENTS_VETERAN": veteran, "GET_ACHIEVEMENTS_NEWCOMER": newcomer, "GET_ACHIEVEMENTS_VARIANTS": variants}[k]
    text = ser(v)
    json.loads(text)
    assert "`" not in text and "${" not in text
    print()
    print("/** " + DOCS[k] + " */")
    print("export const " + k + " = String.raw`" + text + "`")
```

- [ ] **Step 2: Append its output**

From the repo root:

```bash
python3 "$TMPDIR/gen-achievements-fixtures.py" >> apps/web/lib/data/__fixtures__/achievements-captured.ts
```

Expected: three new constants, `GET_ACHIEVEMENTS_VETERAN` (20874 characters between the backticks), `GET_ACHIEVEMENTS_NEWCOMER` (8143) and `GET_ACHIEVEMENTS_VARIANTS` (1856). Task 7's first test asserts those lengths.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/data/__fixtures__/achievements-captured.ts
git commit -m "test(web): add achievements payload fixtures in jsonb's text form"
```

### Task 7: Decode `get_achievements` and `pull_ceremony`

**Files:**
- Create: `apps/web/lib/data/achievements-payload.ts`
- Test: `apps/web/lib/data/achievements-payload.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from "vitest"
import { achievementsPageFrom, ceremonyPullFrom, crossingPebbleFrom } from "./achievements-payload"
import {
  GET_ACHIEVEMENTS_NEWCOMER,
  GET_ACHIEVEMENTS_VARIANTS,
  GET_ACHIEVEMENTS_VETERAN,
} from "./__fixtures__/achievements-captured"
import type { AchievementsPage } from "@/lib/types"

function decode(json: string): AchievementsPage {
  const page = achievementsPageFrom(JSON.parse(json))
  if (!page) throw new Error("payload did not decode")
  return page
}

describe("achievements fixtures", () => {
  it("are byte for byte what the generator wrote", () => {
    expect(GET_ACHIEVEMENTS_VETERAN.length).toBe(20874)
    expect(GET_ACHIEVEMENTS_NEWCOMER.length).toBe(8143)
    expect(GET_ACHIEVEMENTS_VARIANTS.length).toBe(1856)
  })
})

describe("achievementsPageFrom — the veteran", () => {
  const page = decode(GET_ACHIEVEMENTS_VETERAN)

  it("reads the header", () => {
    expect([page.since, page.daysKept]).toEqual(["2026-03-01", 249])
  })

  it("reads the explorer, its centre and its completion", () => {
    const e = page.explorer
    expect(e?.firstPebble).toMatchObject({ name: "Mara moves in", happenedAt: "2026-03-02T18:20:00Z", intensity: 2, positiveness: 1 })
    expect(e?.firstFive.map((f) => [f.slug, f.done, f.on, f.subject?.kind])).toEqual([
      ["first-moment", true, "2026-03-01", "moment"],
      ["first-pebble", true, "2026-03-02", "pebble"],
      ["first-collection", true, "2026-03-09", "collection"],
      ["first-soul", true, "2026-03-02", "soul"],
      ["first-glyph", true, "2026-04-12", "glyph"],
    ])
    expect(e?.sixFirsts.map((s) => s.kind)).toEqual(["small", "medium", "large", "lowlight", "neutral", "highlight"])
    expect(e?.sixFirsts[2].pebble?.name).toBe("Argument about money")
    expect(e?.twentyFive).toMatchObject({ count: 25, done: true })
    expect(e?.ringsDone).toEqual(["first_five", "six_firsts", "twenty_five"])
    expect(e?.completion).toEqual({
      completedAt: "2026-03-14T21:41:07Z",
      offeredFrom: "2026-03-15T05:00:00Z",
      ready: true,
      pulledAt: null,
    })
  })

  it("keeps the 38 seats in the served order, with their families and new marks", () => {
    const e = page.emotions
    expect([e?.count, e?.of, e?.seats.length]).toEqual([23, 38, 38])
    expect(e?.seats.map((s) => s.seat)).toEqual(Array.from({ length: 38 }, (_, k) => k))
    expect(e?.seats[0].family).toBe("joy")
    expect(e?.seats[37].family).toBe("peace")
    expect(e?.seats.filter((s) => s.met)).toHaveLength(23)
    expect(e?.seats.filter((s) => s.isNew).map((s) => s.emotionId)).toEqual([
      "075ec40d-f27c-033d-a498-850947e4e887",
      "21d79e4c-9482-40d3-bf73-53adfb452e5a",
    ])
    expect(e?.completion).toBeNull()
  })

  it("reads the domains newest first, with the tier and the four not yet", () => {
    const d = page.domains
    expect([d?.count, d?.of, d?.coverage]).toEqual([14, 18, "most"])
    expect(d?.entered[0]).toMatchObject({ domainId: "4f1fec32-318e-4fc1-b095-7ed793ebcaac", isNew: true })
    expect(d?.entered).toHaveLength(14)
    expect(d?.notYet).toHaveLength(4)
  })

  it("reads the counting ladders, numeric scale and all", () => {
    expect(page.pebbles?.next).toEqual({ threshold: 250, left: 113, progress: 0.247 })
    expect(page.pebbles?.levels.map((l) => l.threshold)).toEqual([1, 10, 25, 50, 100])
    expect(page.pebbles?.thenEvery).toBe(500)
    expect(page.glyphsCarved?.levels[0]).toEqual({ threshold: 10, on: "2026-06-02", isNew: false, pebble: null })
    expect(page.glyphsCarved?.thenEvery).toBeNull()
    expect(page.glyphSales?.next?.progress).toBe(0.5)
  })

  it("reads the year as served: twelve months, the table's months, the next step", () => {
    const [y] = page.years
    expect([y.year, y.current, y.months.length]).toEqual([2026, true, 12])
    expect(y.months[7]).toEqual({
      month: 8, state: "kept", family: "joy", momentDays: 22, pebbleDays: 18, fullWeeks: 4, weeksFilled: 4, level: 2, progress: 0,
    })
    expect(y.months[0]).toMatchObject({ state: "quiet", family: null, progress: 0 })
    expect(y.months[11].state).toBe("upcoming")
    expect(y.listed).toEqual([11, 10, 9, 8, 7, 6, 5, 4, 3])
    expect(y.nextStep).toEqual({ month: 11, needs: "moments", daysLeft: 11, reachable: true })
    expect(y.daysTarget).toBeNull()
  })
})

describe("achievementsPageFrom — the newcomer", () => {
  const page = decode(GET_ACHIEVEMENTS_NEWCOMER)

  it("reads the absent keys as absent, never as an error", () => {
    expect(page.explorer?.firstPebble).toBeNull()
    expect(page.explorer?.completion).toBeNull()
    expect(page.explorer?.firstFive.every((f) => !f.done && f.on === null && f.subject === null)).toBe(true)
    expect(page.explorer?.sixFirsts.every((s) => !s.done && s.pebble === null)).toBe(true)
    expect(page.explorer?.twentyFive).toEqual({ count: 0, done: false, isNew: false, pebble: null })
    expect(page.explorer?.ringsDone).toEqual([])
    expect(page.domains?.coverage).toBe("none")
    expect(page.domains?.notYet).toHaveLength(18)
    expect(page.pebbles?.next).toEqual({ threshold: 1, left: 1, progress: 0 })
  })

  it("reads an unreachable next step", () => {
    expect(page.years[0].nextStep).toEqual({ month: 10, needs: "moments", daysLeft: 14, reachable: false })
    expect(page.years[0].listed).toEqual([])
  })
})

describe("achievementsPageFrom — tolerance", () => {
  const page = decode(GET_ACHIEVEMENTS_VARIANTS)

  it("hides the sections that are absent or malformed", () => {
    expect([page.since, page.daysKept]).toEqual([null, null])
    expect(page.glyphsCarved).toBeNull()
    expect(page.glyphSales).toBeNull()
  })

  it("keeps a microsecond offset instant and clamps an out-of-range valence", () => {
    expect(page.explorer?.firstPebble).toEqual({
      momentId: "9d1c7a10-0000-4000-8000-000000000001",
      name: null,
      happenedAt: "2026-03-02T18:20:00.376669+00:00",
      emotionId: "3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c",
      intensity: 2,
      positiveness: 0,
    })
  })

  it("reads explicit nulls and empty objects as absent, and drops unknown rings", () => {
    expect(page.explorer?.sixFirsts).toEqual([])
    expect(page.explorer?.twentyFive).toBeNull()
    expect(page.explorer?.completion).toBeNull()
    expect(page.explorer?.ringsDone).toEqual(["first_five"])
    expect(page.explorer?.firstFive.map((f) => f.slug)).toEqual(["first-moment", "first-dream"])
    expect(page.pebbles?.levels).toEqual([])
    expect(page.pebbles?.next).toBeNull()
    expect(page.domains?.notYet).toEqual([])
    expect(page.domains?.entered).toHaveLength(1)
  })

  it("falls back on unknown enums: ink, quiet, no sentence, no next step", () => {
    expect(page.emotions?.seats.map((s) => [s.seat, s.family, s.pebble])).toEqual([
      [0, null, null],
      [1, "joy", null],
    ])
    expect(page.emotions?.completion?.pulledAt).toBe("2026-10-02T07:00:00Z")
    expect(page.domains?.coverage).toBeNull()
    const [y] = page.years
    expect(page.years).toHaveLength(1)
    expect(y.months).toEqual([
      { month: 5, state: "quiet", family: null, momentDays: 15, pebbleDays: 15, fullWeeks: 4, weeksFilled: 4, level: 2, progress: 1 },
    ])
    expect(y.listed).toEqual([5])
    expect(y.nextStep).toBeNull()
    expect(y.daysTarget).toBe(14)
  })

  it("reads anything but an object as no page", () => {
    expect(achievementsPageFrom(null)).toBeNull()
    expect(achievementsPageFrom([])).toBeNull()
    expect(achievementsPageFrom("{}")).toBeNull()
  })
})

describe("crossingPebbleFrom", () => {
  it("needs a moment id and a readable instant", () => {
    expect(crossingPebbleFrom({ moment_id: "m", happened_at: "not a date" })).toBeNull()
    expect(crossingPebbleFrom({ happened_at: "2026-03-02T18:20:00Z" })).toBeNull()
  })
})

describe("ceremonyPullFrom", () => {
  it("reads the first pull's row", () => {
    expect(ceremonyPullFrom(JSON.parse(`[{"stack":"six_firsts","karma_granted":20}]`))).toEqual({ stack: "six_firsts", karmaGranted: 20 })
  })

  it("reads a second pull, with no row, as no success", () => {
    expect(ceremonyPullFrom([])).toBeNull()
    expect(ceremonyPullFrom(null)).toBeNull()
    expect(ceremonyPullFrom([{ stack: "rainbows", karma_granted: 5 }])).toBeNull()
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/data/achievements-payload.test.ts`

Expected: FAIL with `Failed to resolve import "./achievements-payload"`.

- [ ] **Step 3: Write the decoder**

```ts
import type {
  AchievementCompletion,
  AchievementYear,
  AchievementsDomains,
  AchievementsEmotions,
  AchievementsExplorer,
  AchievementsPage,
  CeremonyStack,
  CountingLadder,
  CrossingPebble,
  DomainCoverage,
  DomainEntry,
  EmotionFamily,
  EmotionSeat,
  ExplorerRing,
  FirstFiveItem,
  FirstFiveSubject,
  Intensity,
  LadderLevel,
  LadderNext,
  MonthLevel,
  MonthState,
  Positiveness,
  SixFirst,
  StoneKind,
  TwentyFive,
  YearMonth,
  YearNextStep,
} from "@/lib/types"

// ---------------------------------------------------------------------------
// `get_achievements(p_tz)` decoding (#1308, spec 2026-10-29 §3.3, §4). Pure
// and tolerant: an absent or malformed section decodes to null (its card is
// hidden), an unknown enum to its safe fallback (D17), and nothing here
// computes a number, an order or a state; every one is the server's.
// ---------------------------------------------------------------------------

type Json = Record<string, unknown>

export const EMOTION_FAMILIES: readonly EmotionFamily[] = ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"]
export const STONE_KINDS: readonly StoneKind[] = ["small", "medium", "large", "lowlight", "neutral", "highlight"]
const COVERAGES: readonly DomainCoverage[] = ["none", "few", "third", "half", "most", "all"]
const RINGS: readonly ExplorerRing[] = ["first_five", "six_firsts", "twenty_five", "domains", "emotions"]
const STATES: readonly MonthState[] = ["upcoming", "quiet", "kept"]
const NEEDS: readonly YearNextStep["needs"][] = ["moments", "pebbles"]
const STACKS: readonly CeremonyStack[] = ["emotions", "domains", "six_firsts"]

function obj(raw: unknown): Json | null {
  return typeof raw === "object" && raw !== null && !Array.isArray(raw) ? (raw as Json) : null
}

/** An array, or none: jsonb_agg over no rows is an explicit null. */
function list(raw: unknown): unknown[] {
  return Array.isArray(raw) ? raw : []
}

function int(raw: unknown): number | null {
  return typeof raw === "number" && Number.isInteger(raw) ? raw : null
}

function str(raw: unknown): string | null {
  return typeof raw === "string" && raw !== "" ? raw : null
}

function oneOf<T extends string>(raw: unknown, values: readonly T[]): T | null {
  return values.find((v) => v === raw) ?? null
}

/** A share in [0, 1]; the server rounds to three places, at any scale. */
function share(raw: unknown): number {
  return typeof raw === "number" && Number.isFinite(raw) ? Math.min(1, Math.max(0, raw)) : 0
}

/** An instant kept as sent, if `Date` reads it: whole seconds, fractions, `Z` or `+00:00`. */
function instant(raw: unknown): string | null {
  const s = str(raw)
  return s !== null && !Number.isNaN(Date.parse(s)) ? s : null
}

/** A local calendar date, `YYYY-MM-DD`. */
function calendarDay(raw: unknown): string | null {
  const s = str(raw)
  return s !== null && /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : null
}

function intensityOf(raw: unknown): Intensity {
  return raw === 1 || raw === 3 ? raw : 2
}

function positivenessOf(raw: unknown): Positiveness {
  return raw === -1 || raw === 1 ? raw : 0
}

/** `{pebble}` (D15), or null when it is gone: a deleted pebble leaves its unlock. */
export function crossingPebbleFrom(raw: unknown): CrossingPebble | null {
  const p = obj(raw)
  if (!p) return null
  const momentId = str(p.moment_id)
  const happenedAt = instant(p.happened_at)
  if (!momentId || !happenedAt) return null
  return {
    momentId,
    name: str(p.name),
    happenedAt,
    emotionId: str(p.emotion_id),
    intensity: intensityOf(p.intensity),
    positiveness: positivenessOf(p.positiveness),
  }
}

function completionFrom(raw: unknown): AchievementCompletion | null {
  const c = obj(raw)
  if (!c) return null
  const completedAt = instant(c.completed_at)
  const offeredFrom = instant(c.offered_from)
  if (!completedAt || !offeredFrom) return null
  return { completedAt, offeredFrom, ready: c.ready === true, pulledAt: instant(c.pulled_at) }
}

function subjectFrom(raw: unknown): FirstFiveSubject | null {
  const s = obj(raw)
  const kind = s && str(s.kind)
  const id = s && str(s.id)
  return s && kind && id ? { kind, id, name: str(s.name) } : null
}

function firstFiveFrom(raw: unknown): FirstFiveItem[] {
  return list(raw).flatMap((item) => {
    const f = obj(item)
    const slug = f && str(f.slug)
    if (!f || !slug) return []
    return [{ slug, done: f.done === true, isNew: f.new === true, on: calendarDay(f.on), subject: subjectFrom(f.subject) }]
  })
}

function sixFirstsFrom(raw: unknown): SixFirst[] {
  return list(raw).flatMap((item) => {
    const s = obj(item)
    // A kind this build cannot seat is left off the ring rather than guessed.
    const kind = s && oneOf(s.kind, STONE_KINDS)
    if (!s || !kind) return []
    return [{ kind, done: s.done === true, isNew: s.new === true, pebble: crossingPebbleFrom(s.pebble) }]
  })
}

function twentyFiveFrom(raw: unknown): TwentyFive | null {
  const t = obj(raw)
  if (!t) return null
  return { count: int(t.count) ?? 0, done: t.done === true, isNew: t.new === true, pebble: crossingPebbleFrom(t.pebble) }
}

function explorerFrom(raw: unknown): AchievementsExplorer | null {
  const e = obj(raw)
  if (!e) return null
  return {
    firstPebble: crossingPebbleFrom(e.first_pebble),
    firstFive: firstFiveFrom(e.first_five),
    sixFirsts: sixFirstsFrom(e.six_firsts),
    twentyFive: twentyFiveFrom(e.twenty_five),
    ringsDone: list(e.rings_done).flatMap((r) => {
      const ring = oneOf(r, RINGS)
      return ring ? [ring] : []
    }),
    completion: completionFrom(e.completion),
  }
}

function seatFrom(raw: unknown): EmotionSeat[] {
  const s = obj(raw)
  const seat = s && int(s.seat)
  const emotionId = s && str(s.emotion_id)
  if (!s || seat === null || !emotionId) return []
  return [
    {
      seat,
      emotionId,
      family: oneOf(s.family, EMOTION_FAMILIES),
      met: s.met === true,
      isNew: s.new === true,
      pebble: crossingPebbleFrom(s.pebble),
    },
  ]
}

function emotionsFrom(raw: unknown): AchievementsEmotions | null {
  const e = obj(raw)
  const count = e && int(e.count)
  const of = e && int(e.of)
  if (!e || count === null || of === null) return null
  return { count, of, seats: list(e.seats).flatMap(seatFrom), completion: completionFrom(e.completion) }
}

function domainEntryFrom(raw: unknown): DomainEntry[] {
  const d = obj(raw)
  const domainId = d && str(d.domain_id)
  if (!d || !domainId) return []
  return [{ domainId, isNew: d.new === true, pebble: crossingPebbleFrom(d.pebble) }]
}

function domainsFrom(raw: unknown): AchievementsDomains | null {
  const d = obj(raw)
  const count = d && int(d.count)
  const of = d && int(d.of)
  if (!d || count === null || of === null) return null
  return {
    count,
    of,
    coverage: oneOf(d.coverage, COVERAGES),
    entered: list(d.entered).flatMap(domainEntryFrom),
    notYet: list(d.not_yet).flatMap((id) => {
      const s = str(id)
      return s ? [s] : []
    }),
    completion: completionFrom(d.completion),
  }
}

function levelFrom(raw: unknown): LadderLevel[] {
  const l = obj(raw)
  const threshold = l && int(l.threshold)
  if (!l || threshold === null) return []
  return [{ threshold, on: calendarDay(l.on), isNew: l.new === true, pebble: crossingPebbleFrom(l.pebble) }]
}

function nextFrom(raw: unknown): LadderNext | null {
  const n = obj(raw)
  const threshold = n && int(n.threshold)
  const left = n && int(n.left)
  if (!n || threshold === null || left === null) return null
  return { threshold, left, progress: share(n.progress) }
}

function ladderFrom(raw: unknown): CountingLadder | null {
  const l = obj(raw)
  const count = l && int(l.count)
  if (!l || count === null) return null
  return {
    count,
    levels: list(l.levels).flatMap(levelFrom),
    next: nextFrom(l.next),
    ladder: list(l.ladder).flatMap((t) => {
      const n = int(t)
      return n === null ? [] : [n]
    }),
    thenEvery: int(l.then_every),
  }
}

function monthLevelOf(raw: unknown): MonthLevel {
  const n = int(raw) ?? 0
  return n >= 2 ? 2 : n === 1 ? 1 : 0
}

function monthFrom(raw: unknown): YearMonth[] {
  const m = obj(raw)
  const month = m && int(m.month)
  if (!m || month === null || month < 1 || month > 12) return []
  return [
    {
      month,
      // An unknown state draws as a quiet month (spec §4).
      state: oneOf(m.state, STATES) ?? "quiet",
      family: oneOf(m.family, EMOTION_FAMILIES),
      momentDays: int(m.moment_days) ?? 0,
      pebbleDays: int(m.pebble_days) ?? 0,
      fullWeeks: int(m.full_weeks) ?? 0,
      weeksFilled: int(m.weeks_filled) ?? 0,
      level: monthLevelOf(m.level),
      progress: share(m.progress),
    },
  ]
}

function nextStepFrom(raw: unknown): YearNextStep | null {
  const n = obj(raw)
  const month = n && int(n.month)
  const needs = n && oneOf(n.needs, NEEDS)
  const daysLeft = n && int(n.days_left)
  if (!n || month === null || !needs || daysLeft === null) return null
  return { month, needs, daysLeft, reachable: n.reachable !== false }
}

function yearFrom(raw: unknown): AchievementYear[] {
  const y = obj(raw)
  const year = y && int(y.year)
  if (!y || year === null) return []
  return [
    {
      year,
      current: y.current === true,
      months: list(y.months).flatMap(monthFrom),
      listed: list(y.listed).flatMap((m) => {
        const n = int(m)
        return n !== null && n >= 1 && n <= 12 ? [n] : []
      }),
      nextStep: nextStepFrom(y.next_step),
      daysTarget: int(y.days_target),
    },
  ]
}

/** The whole page, or null when the payload is not an object at all. */
export function achievementsPageFrom(raw: unknown): AchievementsPage | null {
  const p = obj(raw)
  if (!p) return null
  return {
    since: calendarDay(p.since),
    daysKept: int(p.days_kept),
    explorer: explorerFrom(p.explorer),
    emotions: emotionsFrom(p.emotions),
    domains: domainsFrom(p.domains),
    pebbles: ladderFrom(p.pebbles),
    glyphsCarved: ladderFrom(p.glyphs_carved),
    glyphSales: ladderFrom(p.glyph_sales),
    years: list(p.years).flatMap(yearFrom),
  }
}

/**
 * `pull_ceremony(p_stack, p_tz)` rows (spec §3.4). The first pull returns one
 * row; a second returns none, which is not a success (null here).
 */
export function ceremonyPullFrom(raw: unknown): { stack: CeremonyStack; karmaGranted: number } | null {
  const row = obj(list(raw)[0])
  const stack = row && oneOf(row.stack, STACKS)
  if (!row || !stack) return null
  return { stack, karmaGranted: int(row.karma_granted) ?? 0 }
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/data/achievements-payload.test.ts`

Expected: PASS, 17 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/data/achievements-payload.ts apps/web/lib/data/achievements-payload.test.ts
git commit -m "feat(web): decode the achievements page payload tolerantly"
```

### Task 8: The provider's three calls

**Files:**
- Modify: `apps/web/lib/data/data-provider.ts`
- Modify: `apps/web/lib/data/supabase-provider.ts`

- [ ] **Step 1: Declare them**

In `apps/web/lib/data/data-provider.ts`, in the `@/lib/types` import, replace:

```ts
  Achievement,
  AchievementUnlock,
  SharedConnectionPebble,
} from "@/lib/types"
```

with:

```ts
  Achievement,
  AchievementCard,
  AchievementUnlock,
  CeremonyStack,
  SharedConnectionPebble,
} from "@/lib/types"
```

Below `AchievementUnlockResult`, replace:

```ts
  karmaGranted: number
  celebrate: boolean
}
```

with:

```ts
  karmaGranted: number
  celebrate: boolean
}

// The one row `pull_ceremony` returns on the first pull of a set (#1308, spec
// §3.4): the karma the stack's completion paid, from the catalog.
export type CeremonyPull = {
  stack: CeremonyStack
  karmaGranted: number
}
```

In the `DataProvider` interface, replace:

```ts
  getAchievements(): Promise<Achievement[]>
  getAchievementUnlocks(): Promise<AchievementUnlock[]>
  checkAchievements(): Promise<AchievementUnlockResult[]>
```

with:

```ts
  getAchievements(): Promise<Achievement[]>
  getAchievementUnlocks(): Promise<AchievementUnlock[]>
  checkAchievements(): Promise<AchievementUnlockResult[]>

  // The achievements page (#1308): one read serves the page and its five
  // sheets. It returns the raw jsonb, so the page can cache exactly what the
  // server sent and decode it in one place (`achievementsPageFrom`).
  getAchievementsPayload(tz: string): Promise<unknown>
  /** Records that a card's sheet was opened, for its "new" marks (D10). */
  markAchievementsSeen(card: AchievementCard): Promise<void>
  /**
   * Pulls a completed set's ceremony (D9). Null when it was already pulled: a
   * second call returns no row, which is not a success. Throws a message
   * containing `not_ready` before the offer.
   */
  pullCeremony(stack: CeremonyStack, tz: string): Promise<CeremonyPull | null>
```

- [ ] **Step 2: Implement them**

In `apps/web/lib/data/supabase-provider.ts`, make the same `@/lib/types` import replacement as in Step 1. Then, in the `@/lib/data/data-provider` import, replace:

```ts
  type AchievementUnlockResult,
} from "@/lib/data/data-provider"
```

with:

```ts
  type AchievementUnlockResult,
  type CeremonyPull,
} from "@/lib/data/data-provider"
```

Replace the import Part 1 added:

```ts
import { unlockResultsFrom } from "@/lib/data/achievement-results"
```

with:

```ts
import { unlockResultsFrom } from "@/lib/data/achievement-results"
import { ceremonyPullFrom } from "@/lib/data/achievements-payload"
import { withTimeout } from "@/lib/utils/with-timeout"
```

At the end of `checkAchievements()`, replace:

```ts
    return unlockResultsFrom(data)
  }
```

with:

```ts
    return unlockResultsFrom(data)
  }

  // ---------------------------------------------------------------------------
  // The achievements page (#1308) — get_achievements, mark_achievements_seen,
  // pull_ceremony (back-end plan Tasks 9 and 12).
  // ---------------------------------------------------------------------------

  async getAchievementsPayload(tz: string): Promise<unknown> {
    const { data, error } = await withTimeout(
      this.supabase.rpc("get_achievements", { p_tz: tz }),
      10000,
      "get_achievements",
    )
    if (error) throw new Error(`get_achievements failed: ${error.message}`)
    return data
  }

  async markAchievementsSeen(card: AchievementCard): Promise<void> {
    const { error } = await withTimeout(
      this.supabase.rpc("mark_achievements_seen", { p_card: card }),
      10000,
      "mark_achievements_seen",
    )
    if (error) throw new Error(`mark_achievements_seen failed: ${error.message}`)
  }

  async pullCeremony(stack: CeremonyStack, tz: string): Promise<CeremonyPull | null> {
    const { data, error } = await withTimeout(
      this.supabase.rpc("pull_ceremony", { p_stack: stack, p_tz: tz }),
      10000,
      "pull_ceremony",
    )
    if (error) throw new Error(`pull_ceremony failed: ${error.message}`)
    return ceremonyPullFrom(data)
  }
```

- [ ] **Step 3: Typecheck and lint**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`

Expected: no errors. `SupabaseProvider` is the only `DataProvider` implementation, so nothing else needs the new methods.

- [ ] **Step 4: Commit**

```bash
git add apps/web/lib/data/data-provider.ts apps/web/lib/data/supabase-provider.ts
git commit -m "feat(web): read get_achievements, mark cards seen and pull ceremonies"
```

### Task 9: The per-user cache, cleared at sign-out

**Files:**
- Create: `apps/web/lib/data/achievements-cache.ts`
- Test: `apps/web/lib/data/achievements-cache.test.ts`
- Modify: `apps/web/lib/data/useSupabaseAuth.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { afterEach, describe, expect, it, vi } from "vitest"
import {
  achievementsCacheKey,
  clearAchievementsCaches,
  readAchievementsCache,
  writeAchievementsCache,
} from "./achievements-cache"
import { GET_ACHIEVEMENTS_VETERAN } from "./__fixtures__/achievements-captured"

class MemoryStorage {
  private items = new Map<string, string>()
  get length() {
    return this.items.size
  }
  key(i: number) {
    return [...this.items.keys()][i] ?? null
  }
  getItem(k: string) {
    return this.items.get(k) ?? null
  }
  setItem(k: string, v: string) {
    this.items.set(k, v)
  }
  removeItem(k: string) {
    this.items.delete(k)
  }
}

afterEach(() => vi.restoreAllMocks())

describe("the achievements cache", () => {
  it("round-trips the raw payload, per user", () => {
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", JSON.parse(GET_ACHIEVEMENTS_VETERAN))
    expect(readAchievementsCache(storage, "alice")?.daysKept).toBe(249)
    expect(readAchievementsCache(storage, "bob")).toBeNull()
  })

  it("misses on a corrupt entry, logging it, and on another version", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    const storage = new MemoryStorage()
    storage.setItem(achievementsCacheKey("alice"), "{not json")
    expect(readAchievementsCache(storage, "alice")).toBeNull()
    expect(warn).toHaveBeenCalledWith("[achievements] cache read failed", expect.any(SyntaxError))
    storage.setItem(achievementsCacheKey("alice"), JSON.stringify({ v: 0, payload: JSON.parse(GET_ACHIEVEMENTS_VETERAN) }))
    expect(readAchievementsCache(storage, "alice")).toBeNull()
  })

  it("survives a storage that throws, and works without one", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    const throwing = {
      getItem: () => {
        throw new Error("SecurityError")
      },
      setItem: () => {
        throw new Error("QuotaExceededError")
      },
    }
    expect(readAchievementsCache(throwing, "alice")).toBeNull()
    writeAchievementsCache(throwing, "alice", {})
    expect(warn).toHaveBeenCalledWith("[achievements] cache write failed", expect.any(Error))
    expect(readAchievementsCache(null, "alice")).toBeNull()
    writeAchievementsCache(null, "alice", {})
  })

  it("clears every user's entry and nothing else", () => {
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", {})
    writeAchievementsCache(storage, "bob", {})
    storage.setItem("pbbls-consent-gate", "alice|v3")
    clearAchievementsCaches(storage)
    expect(storage.length).toBe(1)
    expect(storage.getItem("pbbls-consent-gate")).toBe("alice|v3")
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/data/achievements-cache.test.ts`

Expected: FAIL with `Failed to resolve import "./achievements-cache"`.

- [ ] **Step 3: Write the cache**

```ts
import { achievementsPageFrom } from "@/lib/data/achievements-payload"
import type { AchievementsPage } from "@/lib/types"

// ---------------------------------------------------------------------------
// The last achievements payload, per user, on this device (#1308, spec §4):
// shown at once while a fresh read loads, and kept when that read fails. It
// stores the raw payload, not the decoded page, so a decoder fix applies to
// what is already cached. Storage can be missing or throw (private mode,
// blocked site data): a failed read is a miss, a failed write is logged.
// It holds private families (D16), so sign-out clears it (useSupabaseAuth).
// ---------------------------------------------------------------------------

export type CacheStorage = Pick<Storage, "getItem" | "setItem">
export type ClearableStorage = Pick<Storage, "length" | "key" | "removeItem">

const KEY_PREFIX = "pbbls-achievements:"
const VERSION = 1

export function achievementsCacheKey(userId: string): string {
  return `${KEY_PREFIX}${userId}`
}

export function readAchievementsCache(storage: CacheStorage | null, userId: string): AchievementsPage | null {
  if (!storage) return null
  try {
    const text = storage.getItem(achievementsCacheKey(userId))
    if (!text) return null
    const entry: unknown = JSON.parse(text)
    if (typeof entry !== "object" || entry === null) return null
    const { v, payload } = entry as { v?: unknown; payload?: unknown }
    return v === VERSION ? achievementsPageFrom(payload) : null
  } catch (err) {
    console.warn("[achievements] cache read failed", err)
    return null
  }
}

export function writeAchievementsCache(storage: CacheStorage | null, userId: string, payload: unknown): void {
  if (!storage) return
  try {
    storage.setItem(achievementsCacheKey(userId), JSON.stringify({ v: VERSION, payload }))
  } catch (err) {
    console.warn("[achievements] cache write failed", err)
  }
}

/** Every user's cached payload on this device (sign-out, account deletion). */
export function clearAchievementsCaches(storage: ClearableStorage): void {
  const keys: string[] = []
  for (let i = 0; i < storage.length; i++) {
    const key = storage.key(i)
    if (key?.startsWith(KEY_PREFIX)) keys.push(key)
  }
  for (const key of keys) storage.removeItem(key)
}

/** `window.localStorage`, or null where there is none or touching it throws. */
export function browserStorage(): CacheStorage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage
  } catch {
    return null
  }
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/data/achievements-cache.test.ts`

Expected: PASS, 4 tests.

- [ ] **Step 5: Clear it with the drafts**

The cached payload holds the private families (D16), so it must not outlive the session, as the complete-flow drafts do not.

In `apps/web/lib/data/useSupabaseAuth.ts`, after `import { clearAllCompleteDrafts } from "@/lib/record/complete-draft-store"`, add:

```ts
import { clearAchievementsCaches } from "@/lib/data/achievements-cache"
```

Then replace:

```ts
/**
 * Complete-flow autosaves are per account (#1050); none outlives the session
 * that wrote it. Best-effort: storage can be unavailable, and that must never
 * fail a sign-out.
 */
function clearDeviceDrafts() {
  try {
    clearAllCompleteDrafts(window.localStorage)
  } catch (err) {
    console.warn("[auth] could not clear complete drafts", err)
  }
}
```

with:

```ts
/**
 * Complete-flow autosaves (#1050) and the achievements page's cached payload
 * (#1308) are per account; none outlives the session that wrote it.
 * Best-effort: storage can be unavailable, and that must never fail a
 * sign-out.
 */
function clearDeviceDrafts() {
  try {
    clearAllCompleteDrafts(window.localStorage)
  } catch (err) {
    console.warn("[auth] could not clear complete drafts", err)
  }
  try {
    clearAchievementsCaches(window.localStorage)
  } catch (err) {
    console.warn("[auth] could not clear the achievements cache", err)
  }
}
```

`logout` and `deleteAccount` both call `clearDeviceDrafts()` already.

- [ ] **Step 6: Commit**

```bash
git add apps/web/lib/data/achievements-cache.ts apps/web/lib/data/achievements-cache.test.ts apps/web/lib/data/useSupabaseAuth.ts
git commit -m "feat(web): cache the last achievements payload per user and clear it at sign-out"
```

### Task 10: The loader: check, read, mark, pull

**Files:**
- Create: `apps/web/lib/data/achievements-loader.ts`
- Test: `apps/web/lib/data/achievements-loader.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { afterEach, describe, expect, it, vi } from "vitest"
import { loadAchievementsPage, markCardSeen, pullCeremonyOutcome } from "./achievements-loader"
import { GET_ACHIEVEMENTS_VETERAN } from "./__fixtures__/achievements-captured"
import type { AchievementUnlockResult } from "./data-provider"

const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

afterEach(() => vi.restoreAllMocks())

describe("loadAchievementsPage", () => {
  it("checks before it reads, and sends the zone", async () => {
    const calls: string[] = []
    const provider = {
      checkAchievements: vi.fn(async (): Promise<AchievementUnlockResult[]> => {
        calls.push("check")
        return []
      }),
      getAchievementsPayload: vi.fn(async (tz: string) => {
        calls.push(`read ${tz}`)
        return JSON.parse(GET_ACHIEVEMENTS_VETERAN)
      }),
    }
    const load = await loadAchievementsPage(provider, "Europe/Paris")
    expect(calls).toEqual(["check", "read Europe/Paris"])
    expect(load.ok && load.page.daysKept).toBe(249)
  })

  it("still reads when the check fails", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => {})
    const load = await loadAchievementsPage(
      {
        checkAchievements: vi.fn(async (): Promise<AchievementUnlockResult[]> => {
          throw new Error("check_achievements failed: timeout")
        }),
        getAchievementsPayload: vi.fn(async () => JSON.parse(GET_ACHIEVEMENTS_VETERAN)),
      },
      "UTC",
    )
    expect(load.ok).toBe(true)
  })

  it("reports a failed read or a payload with no page, and logs both", async () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {})
    const check = vi.fn(async (): Promise<AchievementUnlockResult[]> => [])
    const failed = await loadAchievementsPage(
      {
        checkAchievements: check,
        getAchievementsPayload: vi.fn(async () => {
          throw new Error("get_achievements failed: invalid_tz")
        }),
      },
      "Mars/Olympus",
    )
    const empty = await loadAchievementsPage({ checkAchievements: check, getAchievementsPayload: vi.fn(async () => null) }, "UTC")
    expect([failed.ok, empty.ok]).toEqual([false, false])
    expect(error).toHaveBeenCalledTimes(2)
  })
})

describe("markCardSeen", () => {
  it("never throws, and logs a failure", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    markCardSeen(
      {
        markAchievementsSeen: vi.fn(async () => {
          throw new Error("mark_achievements_seen failed: invalid_card")
        }),
      },
      "explorer",
    )
    await flush()
    expect(warn).toHaveBeenCalledWith("[achievements] mark_achievements_seen failed", expect.any(Error))
  })
})

describe("pullCeremonyOutcome", () => {
  it("reads the first pull as pulled, with its karma", async () => {
    const outcome = await pullCeremonyOutcome(
      { pullCeremony: vi.fn(async () => ({ stack: "six_firsts" as const, karmaGranted: 20 })) },
      "six_firsts",
      "Europe/Paris",
    )
    expect(outcome).toEqual({ kind: "pulled", karmaGranted: 20 })
  })

  it("reads no row and not_ready as gone, never as a success", async () => {
    const noRow = await pullCeremonyOutcome({ pullCeremony: vi.fn(async () => null) }, "emotions", "UTC")
    const early = await pullCeremonyOutcome(
      {
        pullCeremony: vi.fn(async () => {
          throw new Error("pull_ceremony failed: not_ready")
        }),
      },
      "domains",
      "UTC",
    )
    expect([noRow, early]).toEqual([{ kind: "gone" }, { kind: "gone" }])
  })

  it("reads any other error as failed, and logs it", async () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {})
    const outcome = await pullCeremonyOutcome(
      {
        pullCeremony: vi.fn(async () => {
          throw new Error("[timeout] pull_ceremony did not respond after 10000ms")
        }),
      },
      "emotions",
      "UTC",
    )
    expect(outcome).toEqual({ kind: "failed" })
    expect(error).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/data/achievements-loader.test.ts`

Expected: FAIL with `Failed to resolve import "./achievements-loader"`.

- [ ] **Step 3: Write the loader**

```ts
import { achievementsPageFrom } from "@/lib/data/achievements-payload"
import type { DataProvider } from "@/lib/data/data-provider"
import type { AchievementCard, AchievementsPage, CeremonyStack } from "@/lib/types"

export type AchievementsLoad = { ok: true; page: AchievementsPage; payload: unknown } | { ok: false }

/**
 * Opens the page in the spec's order (D14): `check_achievements()` first, the
 * retroactive grant, whose rows are never celebrated here (achievements design
 * D13); then the one read. A failed check is logged and the read still runs
 * (the next check self-heals). A failed or undecodable read is logged and
 * reported, so the caller keeps whatever it already shows.
 */
export async function loadAchievementsPage(
  provider: Pick<DataProvider, "checkAchievements" | "getAchievementsPayload">,
  tz: string,
): Promise<AchievementsLoad> {
  try {
    await provider.checkAchievements()
  } catch (err) {
    console.warn("[achievements] check_achievements failed", err)
  }
  try {
    const payload = await provider.getAchievementsPayload(tz)
    const page = achievementsPageFrom(payload)
    if (!page) {
      console.error("[achievements] get_achievements returned no page", payload)
      return { ok: false }
    }
    return { ok: true, page, payload }
  } catch (err) {
    console.error("[achievements] get_achievements failed", err)
    return { ok: false }
  }
}

/** Fire-and-forget (spec §4): a lost mark only keeps a "new" mark one visit longer. */
export function markCardSeen(provider: Pick<DataProvider, "markAchievementsSeen">, card: AchievementCard): void {
  void provider.markAchievementsSeen(card).catch((err) => {
    console.warn("[achievements] mark_achievements_seen failed", err)
  })
}

export type CeremonyOutcome = { kind: "pulled"; karmaGranted: number } | { kind: "gone" } | { kind: "failed" }

/**
 * Pulls a ceremony (D9). `gone` covers both answers that mean the "ready" mark
 * was stale: no row (already pulled, perhaps on another device) and
 * `not_ready`. Neither is a success; the caller re-reads the page.
 */
export async function pullCeremonyOutcome(
  provider: Pick<DataProvider, "pullCeremony">,
  stack: CeremonyStack,
  tz: string,
): Promise<CeremonyOutcome> {
  try {
    const row = await provider.pullCeremony(stack, tz)
    return row ? { kind: "pulled", karmaGranted: row.karmaGranted } : { kind: "gone" }
  } catch (err) {
    if (err instanceof Error && err.message.includes("not_ready")) return { kind: "gone" }
    console.error("[achievements] pull_ceremony failed", err)
    return { kind: "failed" }
  }
}

/** The reader's zone, sent with every read so local days are the reader's (D12, D17). */
export function readerTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/data/achievements-loader.test.ts`

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/data/achievements-loader.ts apps/web/lib/data/achievements-loader.test.ts
git commit -m "feat(web): open the achievements page with the grant, then the read"
```

### Task 11: The page's hook

The hook is a thin shell over the tested loader and cache. The suite runs in node without a DOM, so the hook itself is checked in Part 5's browser pass.

**Files:**
- Create: `apps/web/lib/data/useAchievementsPage.ts`

- [ ] **Step 1: Write the hook**

```ts
"use client"

import { useCallback, useEffect, useState } from "react"
import { useAuth } from "@/lib/data/auth-context"
import { useDataProvider } from "@/lib/data/provider-context"
import { browserStorage, readAchievementsCache, writeAchievementsCache } from "@/lib/data/achievements-cache"
import {
  loadAchievementsPage,
  markCardSeen,
  pullCeremonyOutcome,
  readerTimeZone,
  type CeremonyOutcome,
} from "@/lib/data/achievements-loader"
import type { AchievementCard, AchievementsPage, CeremonyStack } from "@/lib/types"

type State = { owner: string | null; page: AchievementsPage | null; loading: boolean; failed: boolean }

/**
 * The achievements page's data (#1308). Stale-while-revalidate (spec §4): the
 * user's last payload shows at once, the fresh read replaces it without a
 * blink, and a failed read keeps it (the loader logs it). The sheets read the
 * same page, so opening one costs no call (D14).
 */
export function useAchievementsPage() {
  const { provider } = useDataProvider()
  const { user } = useAuth()
  const userId = user?.id ?? null
  const [state, setState] = useState<State>(() => ({ owner: null, page: null, loading: provider !== null, failed: false }))
  const [generation, setGeneration] = useState(0)

  useEffect(() => {
    if (!provider || !userId) return
    let cancelled = false
    // `await Promise.resolve()` defers the state updates past the render
    // boundary, as useAchievements does (react-hooks/set-state-in-effect).
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      const storage = browserStorage()
      const cached = readAchievementsCache(storage, userId)
      setState((s) => ({
        owner: userId,
        page: s.owner === userId ? (s.page ?? cached) : cached,
        loading: true,
        failed: false,
      }))
      const load = await loadAchievementsPage(provider, readerTimeZone())
      if (cancelled) return
      if (load.ok) {
        writeAchievementsCache(storage, userId, load.payload)
        setState({ owner: userId, page: load.page, loading: false, failed: false })
      } else {
        setState((s) => ({ ...s, loading: false, failed: true }))
      }
    })()
    return () => {
      cancelled = true
    }
  }, [provider, userId, generation])

  const markSeen = useCallback(
    (card: AchievementCard) => {
      if (provider) markCardSeen(provider, card)
    },
    [provider],
  )

  const pull = useCallback(
    async (stack: CeremonyStack): Promise<CeremonyOutcome> => {
      if (!provider) return { kind: "failed" }
      const outcome = await pullCeremonyOutcome(provider, stack, readerTimeZone())
      // A pull changes the page, and a stale "ready" mark must go: re-read.
      if (outcome.kind !== "failed") setGeneration((g) => g + 1)
      return outcome
    },
    [provider],
  )

  const retry = useCallback(() => setGeneration((g) => g + 1), [])

  return { page: state.page, loading: state.loading, failed: state.failed, markSeen, pull, retry }
}
```

- [ ] **Step 2: Typecheck and lint**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`. Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/data/useAchievementsPage.ts
git commit -m "feat(web): serve the achievements page stale-while-revalidate"
```

### Task 12: Verify Part 2 and open its PR

- [ ] **Step 1:** Run `npm run test --workspace=apps/web && npm run lint --workspace=apps/web && npx tsc --noEmit -p apps/web/tsconfig.json`. Expected: all green.
- [ ] **Step 2:** Open the PR with `gh stack`, titled `feat(web): decode and cache the achievements page`. Its body opens with `Part of #1308`.

---

# Part 3 — the figures' geometry and mapping (branch `feat/1308-achievements-web-figures`)

Start the branch: `gh stack add feat/1308-achievements-web-figures`. Everything in this part is a pure module under `lib/achievements/`, with no React.

The geometry is a port of the approved boards' generators (`ach/final/_build/app.js`: `polar`, `arcD`, `gem`, `shardStrip`, `skyline`, `yearDiscDocked`, `monthCounter`). The tests' expected strings are the boards' own output on the same inputs, so the port draws what the maintainer approved. Only the geometry is ported: the boards' `monthStats`, `monthLevels` and `monthFamily` are the server's job now (back-end D13, the oracle), and nothing here replaces them.

### Task 13: The geometry

**Files:**
- Create: `apps/web/lib/achievements/geometry.ts`, `rosace.ts`, `shards.ts`, `skyline.ts`, `year-disc.ts`
- Test: `apps/web/lib/achievements/figures.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from "vitest"
import { arcPath, hash01, polar } from "./geometry"
import { facetTone, rosace } from "./rosace"
import { shards } from "./shards"
import { contour, stageAlone, stageClip, stageIndex } from "./skyline"
import { discMonth } from "./year-disc"
import type { YearMonth } from "@/lib/types"

// Expected strings are the approved boards' own output (ach/final/_build/app.js),
// run on the same inputs: the port must draw what the maintainer approved.

const month = (m: Partial<YearMonth> & Pick<YearMonth, "month">): YearMonth => ({
  state: "kept", family: null, momentDays: 0, pebbleDays: 0, fullWeeks: 4, weeksFilled: 0, level: 0, progress: 0, ...m,
})

describe("geometry", () => {
  it("measures clockwise from twelve o'clock", () => {
    const [x, y] = polar(10, 90)
    expect(x).toBeCloseTo(10)
    expect(y).toBeCloseTo(0)
  })

  it("draws the joy family's arc on the emotions ring", () => {
    const step = 360 / 38
    const a0 = (0 - 2.5) * step - step / 2
    const a1 = (5 - 2.5) * step + step / 2
    expect(arcPath(134, a0 + 1.2, a1 - 1.2)).toBe("M-61.3 -119.2A134 134 0 0 1 61.3 -119.2")
  })

  it("hashes as the boards do", () => {
    expect(hash01("R3t")).toBe(0.64242)
  })
})

describe("rosace", () => {
  const g = rosace(270, 38, true)

  it("cuts the boards' kites", () => {
    expect(g.kite(7)).toBe("M183.63,72.52L198.09,66.47L193.25,81.37L164.65,102.79Z")
    expect(g.silhouette.startsWith("M")).toBe(true)
    expect(g.silhouette.endsWith("Z")).toBe(true)
  })

  it("tones a facet along its family, softer between two lit neighbours", () => {
    expect(facetTone(0, false)).toEqual({
      paper: "color-mix(in oklab, var(--sh) 8%, var(--s))",
      night: "color-mix(in oklab, var(--p) 0%, var(--s))",
    })
    expect(facetTone(1, true).paper).toBe("color-mix(in oklab, var(--spine-stock) 38%, color-mix(in oklab, var(--sh) 80%, var(--s)))")
  })
})

describe("the band", () => {
  it("jitters the seams as the boards do, the last edge straight", () => {
    const band = shards(18, 300, 30)
    expect(band).toHaveLength(18)
    expect(band[4].d).toBe("M68.33 0 L87.30 0 L84.52 30 L65.55 30Z")
    expect(band[17].d).toBe("M284.53 0 L300.00 0 L300.00 30 L286.09 30Z")
  })
})

describe("the skyline", () => {
  it("draws the ground through the first stages", () => {
    expect(contour(2, 126)).toBe(
      "M16 148 L23 148 C23 143.3 29 143.3 29 148 L32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 L126 148",
    )
  })

  it("fills a stage from the ground by the served progress", () => {
    expect(stageClip(5, 0.247)).toEqual({ x: 123.5, y: 140.44, width: 12.5, height: 8.56 })
  })

  it("sets a stage alone in a register mark, reusing the last stone past the eighth", () => {
    expect(stageAlone(3, 0.8).transform).toBe("scale(0.839) translate(-85.50 -136.60)")
    expect([stageIndex(-1), stageIndex(9)]).toEqual([0, 7])
  })
})

describe("the year disc", () => {
  it("docks a month at its level and runs its progress along the spoke", () => {
    const april = discMonth(month({ month: 4, family: "sadness", level: 1, progress: 0.857 }))
    expect(april.counter).toEqual({ x: 170, y: 120, ring: false })
    expect(april.climb).toEqual({ x1: 176, y1: 120, x2: 191.4, y2: 120, tone: "hue" })
    expect(april.spoke.tone).toBe("base")
  })

  it("rings a rim month and inks its spoke and numeral in its hue", () => {
    const may = discMonth(month({ month: 5, family: "peace", level: 2 }))
    expect(may.counter.ring).toBe(true)
    expect(may.spoke.tone).toBe("hue")
    expect(may.numeral).toEqual({ x: 203.1, y: 170.5, text: "V", size: 7, hue: true })
    expect(may.notches.map((n) => n.quiet)).toEqual([false, false])
  })

  it("marks a begun month's goal on the inner circle", () => {
    const november = discMonth(month({ month: 11, family: "joy", level: 0, progress: 0.214, fullWeeks: 0 }))
    expect(november.goal).toEqual({ x1: 78.8, y1: 91.6, x2: 74.8, y2: 98.6 })
    expect(november.counter).toEqual({ x: 94, y: 105, ring: false })
  })

  it("quiets a month without a pebble, and one still to come", () => {
    const february = discMonth(month({ month: 2, state: "quiet" }))
    expect(february.muted).toBe(true)
    expect(february.spoke.tone).toBe("faint")
    expect(february.notches).toEqual([
      { x1: 142.4, y1: 75.2, x2: 147.6, y2: 78.2, quiet: true },
      { x1: 157.4, y1: 49.2, x2: 162.6, y2: 52.2, quiet: true },
    ])
    expect(february.goal).toBeNull()
    expect(discMonth(month({ month: 12, state: "upcoming" })).upcoming).toBe(true)
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/achievements/figures.test.ts`

Expected: FAIL with `Failed to resolve import "./geometry"`.

- [ ] **Step 3: Write `geometry.ts`**

```ts
// The achievements figures' shared geometry (#1308), ported from the approved
// boards' generators (spec 2026-10-29, "the final view"). Angles are degrees
// clockwise from twelve o'clock and y grows downward, as in SVG. Nothing here
// reads the payload: it turns served positions (seat, level, progress) into
// coordinates.

/** One decimal, as the boards print coordinates. */
export function r1(n: number): number {
  return Math.round(n * 10) / 10
}

/** A point at radius `r` and angle `deg` around the origin. */
export function polar(r: number, deg: number): [number, number] {
  const a = (deg * Math.PI) / 180
  return [r * Math.sin(a), -r * Math.cos(a)]
}

/** A circular arc from `a0` to `a1` degrees. */
export function arcPath(r: number, a0: number, a1: number): string {
  const [x0, y0] = polar(r, a0)
  const [x1, y1] = polar(r, a1)
  const sweep = a1 - a0
  return `M${r1(x0)} ${r1(y0)}A${r} ${r} 0 ${Math.abs(sweep) > 180 ? 1 : 0} ${sweep > 0 ? 1 : 0} ${r1(x1)} ${r1(y1)}`
}

/** Stable jitter in [0, 1) for a key (FNV-1a), the boards' `hash`. */
export function hash01(key: string): number {
  let h = 2166136261
  for (const c of key) {
    h ^= c.charCodeAt(0)
    h = Math.imul(h, 16777619)
  }
  return ((h >>> 0) % 100000) / 100000
}
```

- [ ] **Step 4: Write `rosace.ts`**

```ts
// The emotions rosace (#1308): one kite per seat, a star between neighbours,
// the board's `gem()`. Seat 0 sits two and a half steps left of twelve
// o'clock, so the first family centres on top.

export type Rosace = {
  centre: number
  radius: number
  /** Seat `k`'s angle, in radians from the x axis (SVG convention). */
  angle: (k: number) => number
  point: (r: number, a: number) => [number, number]
  silhouette: string
  outline: string
  kite: (k: number) => string
  star: (k: number) => string
}

export function rosace(size: number, seats: number, labelled: boolean): Rosace {
  const centre = size / 2
  const radius = size * (labelled ? 0.345 : 0.44)
  const outer = radius * 0.85
  const inner = radius * 0.47
  const step = (2 * Math.PI) / Math.max(seats, 1)
  const angle = (k: number) => -Math.PI / 2 + (k - 2.5) * step
  const point = (r: number, a: number): [number, number] => [centre + Math.cos(a) * r, centre + Math.sin(a) * r]
  const at = (r: number, a: number) => {
    const [x, y] = point(r, a)
    return `${x.toFixed(2)},${y.toFixed(2)}`
  }
  const kite = (k: number) =>
    `M${at(outer, angle(k) - step / 2)}L${at(radius, angle(k))}L${at(outer, angle(k) + step / 2)}L${at(inner, angle(k))}Z`
  const star = (k: number) => `M${at(inner, angle(k))}L${at(outer, angle(k) + step / 2)}L${at(inner, angle(k + 1))}Z`
  let silhouette = ""
  let outline = ""
  for (let k = 0; k < seats; k++) {
    silhouette += `${k ? "L" : "M"}${at(outer, angle(k) - step / 2)}L${at(radius, angle(k))}`
    outline += kite(k) + star(k)
  }
  return { centre, radius, angle, point, silhouette: `${silhouette}Z`, outline, kite, star }
}

/**
 * A lit facet's tone, from its place `t` (0 to 1) in its family: deeper along
 * the family on paper, brighter at night. CSS over the family's palette
 * variables (`--p`, `--s`, `--sh`); `soft` is the star between two lit
 * neighbours, washed toward the card.
 */
export function facetTone(t: number, soft: boolean): { paper: string; night: string } {
  const paper = `color-mix(in oklab, var(--sh) ${Math.round(8 + t * 72)}%, var(--s))`
  const night = `color-mix(in oklab, var(--p) ${Math.round(t * 62)}%, var(--s))`
  const wash = (c: string) => `color-mix(in oklab, var(--spine-stock) 38%, ${c})`
  return soft ? { paper: wash(paper), night: wash(night) } : { paper, night }
}
```

- [ ] **Step 5: Write `shards.ts`**

```ts
import { hash01 } from "@/lib/achievements/geometry"

// The mended band (#1308): one shard per domain, its seams jittered by a
// stable hash so the band never shifts between renders, the board's
// `shardStrip`.

export type Shard = { d: string; centre: number }

export function shards(count: number, width: number, height: number): Shard[] {
  const cw = width / Math.max(count, 1)
  const jitter = (key: string) => (hash01(key) - 0.5) * cw * 0.7
  const out: Shard[] = []
  let x = 0
  let left: [number, number] = [0, 0]
  for (let i = 0; i < count; i++) {
    const last = i === count - 1
    const rt = last ? 0 : jitter(`R${i}t`)
    const rb = last ? 0 : jitter(`R${i}b`)
    const lt = i === 0 ? 0 : left[0]
    const lb = i === 0 ? 0 : left[1]
    out.push({
      d: `M${(x + lt).toFixed(2)} 0 L${(x + cw + rt).toFixed(2)} 0 L${(x + cw + rb).toFixed(2)} ${height} L${(x + lb).toFixed(2)} ${height}Z`,
      centre: x + cw / 2,
    })
    left = [rt, rb]
    x += cw
  }
  return out
}
```

- [ ] **Step 6: Write `skyline.ts`**

```ts
// The pebbles' skyline (#1308): eight stages, each a standing stone that
// fills as its level is reached, the board's "One skyline". A glyph ladder
// uses the first five.

export const STAGES = [
  "M23 148 C23 143.3 29 143.3 29 148 Z",
  "M32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 Z",
  "M53 148 C52.6 133.5 56.8 128.7 63.3 128.7 C68.8 128.7 72.4 136 72 148 Z",
  "M75.5 148 L76.7 135.5 L83.1 125.2 L89.9 127.5 L95.5 138.4 L95.5 148 Z",
  "M99 148 C98.6 128.4 105 121.8 112.8 121.8 C118.8 121.8 122.4 131.8 122 148 Z",
  "M125.5 148 L125.9 123.5 C126.3 116.7 133.4 116.1 134 122.8 L134 148 Z",
  "M141 148 L141 120.7 L137.5 120.7 C137.2 114.5 145.9 113.9 151.5 113.9 C157.1 113.9 165.8 114.5 165.5 120.7 L162 120.7 L162 148 L158 148 L158 120.7 L145 120.7 L145 148 Z",
  "M169 148 C180.5 147.2 182 110.5 194.9 110.5 C208.4 110.5 207.4 145.8 217 148 Z",
] as const

const SPAN: readonly [number, number][] = [[23, 29], [32.5, 49.5], [53, 72], [75.5, 95.5], [99, 122], [125.5, 134], [137.5, 165.5], [169, 217]]
const TOP = [144.5, 133.2, 128.7, 125.2, 121.8, 117.4, 113.9, 110.5] as const
export const GROUND = 148

/** The stage a rung draws with: rungs past the eighth reuse the last stone. */
export function stageIndex(rung: number): number {
  return Math.min(Math.max(rung, 0), STAGES.length - 1)
}

/** The skyline's ground line through the first `n` stages, out to `end`. */
export function contour(n: number, end: number): string {
  const stones = STAGES.slice(0, n).map((d) => `L${d.slice(1).replace(/ Z$/, "")}`)
  return `M16 148 ${stones.join(" ")} L${end} 148`
}

/** The clip that shows the lower `fill` of a stage. */
export function stageClip(k: number, fill: number): { x: number; y: number; width: number; height: number } {
  const [x0, x1] = SPAN[stageIndex(k)]
  const h = (GROUND - TOP[stageIndex(k)]) * fill
  return { x: x0 - 2, y: Math.round((GROUND - h) * 100) / 100, width: x1 - x0 + 4, height: Math.round((h + 1) * 100) / 100 }
}

/** A stage alone, centred on the origin in a 28 × 26 box (a register row's mark). */
export function stageAlone(k: number, scale: number): { transform: string; baseline: [number, number] } {
  const i = stageIndex(k)
  const [x0, x1] = SPAN[i]
  const top = TOP[i]
  const s = Math.min(28 / (x1 - x0 + 4), 26 / (GROUND - top + 2)) * scale
  return {
    transform: `scale(${s.toFixed(3)}) translate(${(-(x0 + x1) / 2).toFixed(2)} ${(-(GROUND + top) / 2).toFixed(2)})`,
    baseline: [x0 - 3, x1 + 3],
  }
}
```

- [ ] **Step 7: Write `year-disc.ts`**

The stone's edge and grain are the board's disc base (`g/112`, `window.YEARBASE`), copied as data. The spokes, notches and numerals are generated: a spoke runs from radius 14 to 88, the notches sit at the inner circle (50) and the rim (80), and a numeral sits at 96.

```ts
import { polar, r1 } from "@/lib/achievements/geometry"
import type { EmotionFamily, YearMonth } from "@/lib/types"

// The year disc (#1308, spec D12): one spoke per month, three places on it
// (the centre, the inner circle, the rim). A month's counter docks at the
// place its served level names; its served progress runs along the spoke
// toward the next. The board's `yearDiscDocked`, decided 28 October (B).

export const DISC_CENTRE = 120
/** The centre, the inner circle (level 1) and the rim (level 2). */
export const DISC_PLACES = [30, 50, 80] as const
export const ROMAN = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"] as const

/** The stone the disc is cut on. */
export const STONE_EDGE =
  "M225.1,120.0 L223.0,136.3 L217.4,151.6 L211.9,166.8 L205.0,181.7 L192.4,192.4 L181.0,203.9 L167.6,213.5 L151.9,218.2 L136.1,221.4 L120.0,225.2 L103.6,223.5 L87.6,219.6 L72.9,212.5 L58.7,204.4 L46.3,193.7 L34.7,182.0 L27.8,167.0 L20.4,152.4 L17.4,136.3 L15.6,120.0 L16.7,103.6 L20.4,87.6 L27.0,72.6 L36.8,59.6 L45.4,45.4 L58.2,35.0 L73.4,28.6 L88.0,21.5 L103.9,18.3 L120.0,14.9 L136.0,18.8 L152.0,21.7 L167.3,27.1 L180.7,36.5 L192.8,47.2 L203.3,59.5 L213.5,72.4 L220.4,87.4 L222.5,103.8 Z"

/** The stone's grain: x, y, r. */
export const STONE_GRAIN: readonly [number, number, number][] = [
  [217.6, 135.5, 0.7], [25.8, 134.9, 0.8], [105.0, 25.2, 0.6], [37.7, 179.8, 0.9], [25.9, 134.9, 0.8],
  [189.4, 50.6, 0.8], [209.8, 74.2, 0.7], [89.4, 25.9, 0.9], [52.0, 52.0, 0.8], [25.7, 105.1, 0.8],
  [217.5, 135.4, 0.8], [197.8, 63.5, 0.8], [75.7, 207.0, 0.9], [150.1, 27.3, 0.7], [25.1, 135.0, 0.6],
  [135.0, 214.5, 0.5], [89.0, 24.5, 0.9], [75.3, 32.2, 0.5], [25.7, 134.9, 0.6], [63.8, 42.6, 0.7],
  [176.8, 41.8, 0.5], [120.0, 18.6, 0.8], [105.1, 26.1, 0.6], [197.9, 176.6, 0.5], [120.0, 23.9, 0.9],
  [164.2, 206.7, 0.5], [42.2, 176.5, 0.9], [178.3, 200.2, 0.7], [89.6, 213.6, 0.7], [204.2, 162.9, 0.5],
  [207.2, 75.6, 0.8], [176.1, 42.8, 0.8], [120.0, 219.1, 0.5], [219.4, 104.3, 0.7], [90.6, 29.7, 0.6],
  [90.5, 210.8, 0.9], [216.3, 88.7, 0.6], [120.0, 220.0, 0.5], [135.4, 22.6, 0.7], [20.7, 135.7, 0.5],
]

export type Segment = { x1: number; y1: number; x2: number; y2: number }

export type DiscMonth = {
  month: number
  upcoming: boolean
  /** Faint: a month still to come, or one that holds no pebble. */
  muted: boolean
  family: EmotionFamily | null
  spoke: Segment & { tone: "hue" | "faint" | "base" }
  /** The inner circle's notch, then the rim's; quiet ones are thinner. */
  notches: [Segment & { quiet: boolean }, Segment & { quiet: boolean }]
  /** The inner circle's goal notch, in ink, for a begun month below it. */
  goal: Segment | null
  /** The way travelled: the whole climb for a rim month, else the progress. */
  climb: (Segment & { tone: "hue" | "ink" }) | null
  counter: { x: number; y: number; ring: boolean }
  numeral: { x: number; y: number; text: string; size: number; hue: boolean }
}

function segment(p: [number, number], q: [number, number]): Segment {
  return { x1: p[0], y1: p[1], x2: q[0], y2: q[1] }
}

export function discMonth(m: YearMonth): DiscMonth {
  const deg = (m.month - 1) * 30
  const at = (r: number, a = deg): [number, number] => {
    const [x, y] = polar(r, a)
    return [r1(x + DISC_CENTRE), r1(y + DISC_CENTRE)]
  }
  const rad = (deg * Math.PI) / 180
  const notch = (r: number): Segment => {
    const [x, y] = polar(r, deg)
    const [tx, ty] = [3 * Math.cos(rad), 3 * Math.sin(rad)]
    return segment(
      [r1(x + DISC_CENTRE - tx), r1(y + DISC_CENTRE - ty)],
      [r1(x + DISC_CENTRE + tx), r1(y + DISC_CENTRE + ty)],
    )
  }
  const upcoming = m.state === "upcoming"
  const muted = upcoming || m.family === null
  const rim = m.level === 2
  const hued = rim && m.family !== null
  const [inner, edge] = [DISC_PLACES[1], DISC_PLACES[2]]
  let climb: DiscMonth["climb"] = null
  if (rim) climb = { ...segment(at(inner + 6), at(edge - 7)), tone: m.family ? "hue" : "ink" }
  else if (m.progress > 0) {
    const from = DISC_PLACES[m.level]
    const span = DISC_PLACES[m.level + 1] - from - 12
    climb = { ...segment(at(from + 6), at(from + 6 + m.progress * span)), tone: m.level === 1 && m.family ? "hue" : "ink" }
  }
  const [cx, cy] = at(DISC_PLACES[m.level])
  const [nx, ny] = polar(96, deg)
  const text = ROMAN[m.month - 1]
  return {
    month: m.month,
    upcoming,
    muted,
    family: m.family,
    spoke: { ...segment(at(14), at(88)), tone: hued ? "hue" : muted ? "faint" : "base" },
    notches: [
      { ...notch(inner), quiet: muted },
      { ...notch(edge), quiet: muted || !rim },
    ],
    goal: m.level === 0 && m.progress > 0 && !muted ? segment(at(inner, deg + 4.6), at(inner, deg - 4.6)) : null,
    climb,
    counter: { x: cx, y: cy, ring: hued },
    numeral: { x: r1(nx + DISC_CENTRE), y: r1(ny + DISC_CENTRE + 2.5), text, size: text.length >= 3 ? 6 : 7, hue: hued },
  }
}
```

- [ ] **Step 8: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/achievements/figures.test.ts`

Expected: PASS, 13 tests.

- [ ] **Step 9: Commit**

```bash
git add apps/web/lib/achievements/geometry.ts apps/web/lib/achievements/rosace.ts apps/web/lib/achievements/shards.ts apps/web/lib/achievements/skyline.ts apps/web/lib/achievements/year-disc.ts apps/web/lib/achievements/figures.test.ts
git commit -m "feat(web): port the achievements figures' geometry from the approved boards"
```

### Task 14: The mapping from payload to figures

**Files:**
- Create: `apps/web/lib/achievements/seals.ts`, `palette.ts`, `emotions-model.ts`, `domains-model.ts`, `ladder-model.ts`, `year-model.ts`, `explorer-model.ts`
- Test: `apps/web/lib/achievements/models.test.ts`

- [ ] **Step 1: Write the failing test**

It replays the three payload fixtures through the decoder, then through each model.

```ts
import { describe, expect, it } from "vitest"
import { achievementsPageFrom } from "@/lib/data/achievements-payload"
import {
  GET_ACHIEVEMENTS_NEWCOMER,
  GET_ACHIEVEMENTS_VARIANTS,
  GET_ACHIEVEMENTS_VETERAN,
} from "@/lib/data/__fixtures__/achievements-captured"
import { bandItems, domainRows } from "./domains-model"
import { emotionRegister, facets, familyBars, familyRuns } from "./emotions-model"
import { explorerBars, explorerSummary, wheelModel } from "./explorer-model"
import { ladderStages, milestoneRows } from "./ladder-model"
import { achievementPalettes } from "./palette"
import { countCell, yearBars, yearPlaces, yearRows } from "./year-model"
import type { AchievementsPage } from "@/lib/types"

function decode(json: string): AchievementsPage {
  const page = achievementsPageFrom(JSON.parse(json))
  if (!page) throw new Error("payload did not decode")
  return page
}

const veteran = decode(GET_ACHIEVEMENTS_VETERAN)
const newcomer = decode(GET_ACHIEVEMENTS_NEWCOMER)
const variants = decode(GET_ACHIEVEMENTS_VARIANTS)

function sections(page: AchievementsPage) {
  const { explorer, emotions, domains, pebbles, glyphsCarved, years } = page
  if (!explorer || !emotions || !domains || !pebbles || !glyphsCarved) throw new Error("fixture lost a section")
  return { explorer, emotions, domains, pebbles, glyphsCarved, year: years[0] }
}

describe("the explorer wheel", () => {
  const v = sections(veteran)

  it("seats every ring from the served flags", () => {
    const w = wheelModel(v.explorer, v.emotions, v.domains)
    expect(w.centre?.name).toBe("Mara moves in")
    expect(w.tracks).toEqual({ first_five: true, six_firsts: true, twenty_five: true, domains: false, emotions: false })
    expect(w.firstFive.map((f) => f.slug)).toEqual(["first-moment", "first-pebble", "first-collection", "first-soul", "first-glyph"])
    expect(w.sixFirsts.map((s) => [s.kind, s.seal.print, s.width])).toEqual([
      ["small", "solid", 9], ["medium", "solid", 12], ["large", "solid", 14],
      ["lowlight", "wash", 12.5], ["neutral", "tint", 12.5], ["highlight", "solid", 12.5],
    ])
    expect(w.twentyFive.filter((s) => s.lit)).toHaveLength(25)
    expect(w.domains.filter((d) => d.on)).toHaveLength(14)
    expect(w.domains.slice(13, 15).map((d) => [d.on, d.isNew])).toEqual([[true, true], [false, false]])
    expect(w.emotions.filter((e) => e.seal)).toHaveLength(23)
    expect(w.families.map((f) => f.family)).toEqual(["joy", "pride", "anger", "fear", "shame", "sadness", "peace"])
    expect(w.families[0].arc).toBe("M-61.3 -119.2A134 134 0 0 1 61.3 -119.2")
  })

  it("prints a met emotion with its crossing pebble's seal", () => {
    const w = wheelModel(v.explorer, v.emotions, v.domains)
    const joyful = w.emotions.find((e) => e.seat.emotionId === "3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c")
    expect(joyful?.seal).toEqual({ size: "medium", polarity: "highlight", print: "solid" })
    expect(joyful?.width).toBeCloseTo(10.6)
  })

  it("lights no seat for a newcomer", () => {
    const n = sections(newcomer)
    const w = wheelModel(n.explorer, n.emotions, n.domains)
    expect(w.centre).toBeNull()
    expect(w.twentyFive.some((s) => s.lit)).toBe(false)
    expect(w.emotions.every((e) => e.seal === null)).toBe(true)
    expect(Object.values(w.tracks).some(Boolean)).toBe(false)
  })

  it("sums the rings for the margin and the label", () => {
    const s = explorerSummary(v.explorer, v.emotions, v.domains)
    expect(s.domains).toEqual({ done: false, count: 14, of: 18 })
    expect(explorerBars(s)).toEqual([1, 1, 1, 14 / 18, 23 / 38])
  })
})

describe("the emotions", () => {
  const v = sections(veteran)

  it("groups the served seats into the wheel's families", () => {
    expect(familyRuns(v.emotions.seats).map((r) => [r.family, r.start, r.seats.length])).toEqual([
      ["joy", 0, 6], ["pride", 6, 3], ["anger", 9, 6], ["fear", 15, 5], ["shame", 20, 4], ["sadness", 24, 6], ["peace", 30, 8],
    ])
    expect(familyBars(v.emotions.seats)).toEqual([5 / 6, 2 / 3, 3 / 6, 3 / 5, 1 / 4, 3 / 6, 6 / 8])
  })

  it("tones each facet by its place in its family, and bridges lit neighbours", () => {
    const f = facets(v.emotions.seats)
    expect(f.slice(0, 6).map((x) => x.t)).toEqual([0, 0.2, 0.4, 0.6, 0.8, 1])
    expect(f[0].bridged).toBe(true)
    expect(f[4].bridged).toBe(false)
    expect(f[5].bridged).toBe(false)
  })

  it("lists each family's met emotions, then the rest, in seat order", () => {
    const joy = emotionRegister(v.emotions.seats)[0]
    expect([joy.family, joy.met.length, joy.notYet.length]).toEqual(["joy", 5, 1])
  })

  it("keeps an unknown family as its own ink run", () => {
    expect(familyRuns(variants.emotions?.seats ?? []).map((r) => r.family)).toEqual([null, "joy"])
  })
})

describe("the domains", () => {
  const v = sections(veteran)

  it("reads the band in the order they came, then the four not yet", () => {
    const band = bandItems(v.domains)
    expect(band).toHaveLength(18)
    expect(band[0].domainId).toBe(v.domains.entered[13].domainId)
    expect(band[13]).toEqual({ domainId: "4f1fec32-318e-4fc1-b095-7ed793ebcaac", on: true, isNew: true })
    expect(band.slice(14).every((b) => !b.on)).toBe(true)
  })

  it("numbers the register by arrival, newest first", () => {
    expect(domainRows(v.domains).map((r) => r.no).slice(0, 3)).toEqual([14, 13, 12])
  })
})

describe("the counting ladders", () => {
  const v = sections(veteran)

  it("fills the rungs the server lists, and the next by its progress", () => {
    expect(ladderStages(v.pebbles).map((s) => s.fill)).toEqual([1, 1, 1, 1, 1, 0.247, 0, 0])
    expect(ladderStages(v.glyphsCarved).map((s) => s.reached)).toEqual([true, false, false, false, false])
  })

  it("lists the milestones reached, the next, then the empty rungs", () => {
    expect(milestoneRows(v.pebbles).map((r) => [r.kind, r.stage])).toEqual([
      ["reached", 0], ["reached", 1], ["reached", 2], ["reached", 3], ["reached", 4], ["next", 5], ["ahead", 6], ["ahead", 7],
    ])
  })

  it("stops at the reached levels when the ladder is done", () => {
    const top = variants.pebbles
    if (!top) throw new Error("variants lost pebbles")
    expect(milestoneRows(top)).toEqual([])
    expect(ladderStages(top).every((s) => s.fill === 0)).toBe(true)
  })
})

describe("the year", () => {
  const v = sections(veteran)

  it("fills each month's bar by its level and progress", () => {
    expect(yearBars(v.year).map((b) => Math.round(b * 1000) / 1000)).toEqual([0, 0, 0.875, 0.929, 0.965, 0.875, 0.929, 1, 0.893, 0.834, 0.107, 0])
  })

  it("names the months at each place", () => {
    expect(yearPlaces(v.year)).toEqual({ rim: [8], inner: [3, 4, 5, 6, 7, 9, 10], way: [11] })
  })

  it("rows the served months, the current one carrying the next step", () => {
    const rows = yearRows(v.year)
    expect(rows.map((r) => r.month.month)).toEqual([11, 10, 9, 8, 7, 6, 5, 4, 3])
    expect(rows[0].nextStep?.daysLeft).toBe(11)
    expect(rows[1].nextStep).toBeNull()
    expect(rows[0].weeks).toEqual({ value: 0, of: 0, fill: 0, done: false, none: true })
    expect(rows[1].weeks).toEqual({ value: 2, of: 3, fill: 2 / 3, done: false, none: false })
    expect(rows[1].moments).toEqual({ value: 19, of: null, fill: 0, done: false, none: false })
  })

  it("caps a day count at the served target, once there is one", () => {
    expect(yearRows(variants.years[0])[0].pebbles).toEqual({ value: 14, of: 14, fill: 1, done: true, none: false })
    expect(countCell(9, 14)).toEqual({ value: 9, of: 14, fill: 9 / 14, done: false, none: false })
  })
})

describe("palettes", () => {
  const v = sections(veteran)
  const joy = { primary_color: "#E0A100", secondary_color: "#F2C94C", light_color: "#FFF4D6", shaded_color: "#8A5F00", dark_color: "#3D2A00" }

  it("gives a family its first served seat's palette, and unknowns none", () => {
    const p = achievementPalettes(v.emotions.seats, new Map([["50a68890-f6bb-e67a-6655-e84ff0b71f49", joy]]))
    expect(p.family("joy")).toBe(joy)
    expect(p.family("peace")).toBeNull()
    expect(p.family(null)).toBeNull()
    expect(p.emotion("50a68890-f6bb-e67a-6655-e84ff0b71f49")).toBe(joy)
    expect(p.emotion(null)).toBeNull()
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/achievements/models.test.ts`

Expected: FAIL with `Failed to resolve import "./domains-model"`.

- [ ] **Step 3: Write `seals.ts`**

```ts
import { POLARITY_BY_VALENCE, SIZE_BY_INTENSITY, type Polarity, type Size } from "@/lib/config/pebble-geometry"
import type { CrossingPebble, StoneKind } from "@/lib/types"

// The flat seal's prints (#1308), the Spine's print of the stone: a highlight
// in relief (solid), a neutral as a tint with a rim, a lowlight as a wash with
// hatching and a rim; `blank` is a moment with no pebble yet.

export type SealPrint = "solid" | "blank" | "tint" | "wash"
export type SealSpec = { size: Size; polarity: Polarity; print: SealPrint }

const PRINT_BY_POLARITY: Record<Polarity, SealPrint> = { highlight: "solid", neutral: "tint", lowlight: "wash" }

/** A crossing pebble's own seal: its size, its valence's outline and print. */
export function pebbleSeal(p: Pick<CrossingPebble, "intensity" | "positiveness">): SealSpec {
  const polarity = POLARITY_BY_VALENCE[p.positiveness]
  return { size: SIZE_BY_INTENSITY[p.intensity], polarity, print: PRINT_BY_POLARITY[polarity] }
}

/**
 * The six firsts as the wheel seats them (D5): the intensities as three sizes
 * of the solid seal, the valences as the three prints at medium size.
 */
export const SIX_FIRST_SEALS: Record<StoneKind, SealSpec & { width: number; angle: number }> = {
  small: { size: "small", polarity: "neutral", print: "solid", width: 9, angle: 30 },
  medium: { size: "medium", polarity: "neutral", print: "solid", width: 12, angle: 90 },
  large: { size: "large", polarity: "neutral", print: "solid", width: 14, angle: 150 },
  lowlight: { size: "medium", polarity: "lowlight", print: "wash", width: 12.5, angle: 210 },
  neutral: { size: "medium", polarity: "neutral", print: "tint", width: 12.5, angle: 270 },
  highlight: { size: "medium", polarity: "highlight", print: "solid", width: 12.5, angle: 330 },
}
```

- [ ] **Step 4: Write `palette.ts`**

```ts
import type { StonePalette } from "@/lib/config/stone-tones"
import type { EmotionFamily, EmotionSeat } from "@/lib/types"

// Colour is a lookup, never a decision (#1308, spec §4): a family slug or an
// emotion id → its palette. Palettes are per family in the reference data, so
// a family takes the palette of its first served seat. Anything unknown is
// null, and draws in ink.

export type AchievementPalettes = {
  emotion: (id: string | null) => StonePalette | null
  family: (family: EmotionFamily | null) => StonePalette | null
}

export function achievementPalettes(
  seats: readonly EmotionSeat[],
  byEmotion: ReadonlyMap<string, StonePalette>,
): AchievementPalettes {
  const byFamily = new Map<EmotionFamily, StonePalette>()
  for (const seat of seats) {
    const palette = byEmotion.get(seat.emotionId)
    if (seat.family && palette && !byFamily.has(seat.family)) byFamily.set(seat.family, palette)
  }
  return {
    emotion: (id) => (id ? (byEmotion.get(id) ?? null) : null),
    family: (family) => (family ? (byFamily.get(family) ?? null) : null),
  }
}
```

- [ ] **Step 5: Write `emotions-model.ts`**

```ts
import type { EmotionFamily, EmotionSeat } from "@/lib/types"

// The emotions card and sheet (#1308): the served seats, grouped as served.
// Families are the contiguous runs of the seat order (wheel order, then slug);
// nothing is re-sorted here.

export type FamilyRun = { family: EmotionFamily | null; start: number; seats: EmotionSeat[] }

export function familyRuns(seats: readonly EmotionSeat[]): FamilyRun[] {
  const runs: FamilyRun[] = []
  seats.forEach((seat, k) => {
    const last = runs[runs.length - 1]
    if (last && last.family === seat.family) last.seats.push(seat)
    else runs.push({ family: seat.family, start: k, seats: [seat] })
  })
  return runs
}

/** The card's margin: one bar per family, filled by its met seats. */
export function familyBars(seats: readonly EmotionSeat[]): number[] {
  return familyRuns(seats).map((run) => run.seats.filter((s) => s.met).length / run.seats.length)
}

export type Facet = {
  k: number
  seat: EmotionSeat
  /** Its place in its family, 0 to 1: the facet's tone. */
  t: number
  /** The star after it is lit too: it and its neighbour are met, in one family. */
  bridged: boolean
}

export function facets(seats: readonly EmotionSeat[]): Facet[] {
  const out: Facet[] = []
  for (const run of familyRuns(seats)) {
    run.seats.forEach((seat, j) => {
      const k = run.start + j
      const next = seats[(k + 1) % seats.length]
      out.push({
        k,
        seat,
        t: run.seats.length > 1 ? j / (run.seats.length - 1) : 0.5,
        bridged: seat.met && next !== seat && next.met && next.family === seat.family,
      })
    })
  }
  return out
}

/** The sheet's register: per family, what was met, then what was not, in seat order. */
export function emotionRegister(seats: readonly EmotionSeat[]): { family: EmotionFamily | null; met: EmotionSeat[]; notYet: EmotionSeat[] }[] {
  return familyRuns(seats).map((run) => ({
    family: run.family,
    met: run.seats.filter((s) => s.met),
    notYet: run.seats.filter((s) => !s.met),
  }))
}
```

- [ ] **Step 6: Write `domains-model.ts`**

```ts
import type { AchievementsDomains, DomainEntry } from "@/lib/types"

// The domains card and sheet (#1308, spec D11). The server sends the entered
// domains newest first; the band reads them in the order they came, so it
// walks that list backwards, then the ones not yet.

export type BandItem = { domainId: string; on: boolean; isNew: boolean }

export function bandItems(d: AchievementsDomains): BandItem[] {
  return [
    ...[...d.entered].reverse().map((e) => ({ domainId: e.domainId, on: true, isNew: e.isNew })),
    ...d.notYet.map((domainId) => ({ domainId, on: false, isNew: false })),
  ]
}

/** The register, newest first as served, each numbered by arrival ("No. 14"). */
export function domainRows(d: AchievementsDomains): { no: number; entry: DomainEntry }[] {
  return d.entered.map((entry, i) => ({ no: d.entered.length - i, entry }))
}
```

- [ ] **Step 7: Write `ladder-model.ts`**

```ts
import { stageIndex } from "@/lib/achievements/skyline"
import type { CountingLadder, LadderLevel, LadderNext } from "@/lib/types"

// A counting stack's figures (#1308, spec D3). A rung is reached when the
// server lists it among the levels, and the next rung fills by the server's
// progress; no count is compared with a threshold here.

export type Stage = { threshold: number; reached: boolean; fill: number }

export function ladderStages(l: CountingLadder): Stage[] {
  return l.ladder.map((threshold) => {
    const reached = l.levels.some((level) => level.threshold === threshold)
    const fill = reached ? 1 : l.next?.threshold === threshold ? l.next.progress : 0
    return { threshold, reached, fill }
  })
}

export type MilestoneRow =
  | { kind: "reached"; level: LadderLevel; stage: number }
  | { kind: "next"; next: LadderNext; stage: number }
  | { kind: "ahead"; threshold: number; stage: number }

/** The sheet's milestones: the levels reached, the next with its fill, then the empty rungs. */
export function milestoneRows(l: CountingLadder): MilestoneRow[] {
  const rows: MilestoneRow[] = l.levels.map((level, i) => ({ kind: "reached", level, stage: stageIndex(i) }))
  if (!l.next) return rows
  rows.push({ kind: "next", next: l.next, stage: stageIndex(l.levels.length) })
  const at = l.ladder.indexOf(l.next.threshold)
  if (at < 0) return rows
  return [...rows, ...l.ladder.slice(at + 1).map((threshold, i) => ({ kind: "ahead" as const, threshold, stage: stageIndex(at + 1 + i) }))]
}
```

- [ ] **Step 8: Write `year-model.ts`**

`countCell` compares a served count with its served denominator to set the done style. It never reads a hard-coded target: without `days_target` (contract gap 1), the day cells carry no denominator.

```ts
import type { AchievementYear, YearMonth, YearNextStep } from "@/lib/types"

// The year card and sheet (#1308, spec D12): the served months mapped onto
// places and gauges. A cell's gauge is a served count over its served
// denominator; without a denominator it shows the count alone.

/** The card's margin: one bar per month, filled by its level and progress. */
export function yearBars(year: AchievementYear): number[] {
  return year.months.map((m) => (m.state === "upcoming" ? 0 : (m.level + m.progress) / 2))
}

/** The months at each place, for the disc's spoken label (spec §5). */
export function yearPlaces(year: AchievementYear): { rim: number[]; inner: number[]; way: number[] } {
  const begun = year.months.filter((m) => m.state !== "upcoming")
  return {
    rim: begun.filter((m) => m.level === 2).map((m) => m.month),
    inner: begun.filter((m) => m.level === 1).map((m) => m.month),
    way: begun.filter((m) => m.level === 0 && m.progress > 0).map((m) => m.month),
  }
}

export type CountCell = { value: number; of: number | null; fill: number; done: boolean; none: boolean }

export function countCell(n: number, of: number | null): CountCell {
  if (of === null) return { value: n, of: null, fill: 0, done: false, none: false }
  if (of === 0) return { value: 0, of: 0, fill: 0, done: false, none: true }
  return { value: Math.min(n, of), of, fill: Math.min(1, n / of), done: n >= of, none: false }
}

export type YearRow = {
  month: YearMonth
  moments: CountCell
  pebbles: CountCell
  weeks: CountCell
  /** The current month's row carries the next step (D12). */
  nextStep: YearNextStep | null
}

/** The table: the served `listed` months, newest first. */
export function yearRows(year: AchievementYear): YearRow[] {
  return year.listed.flatMap((n) => {
    const month = year.months.find((m) => m.month === n)
    if (!month) return []
    return [
      {
        month,
        moments: countCell(month.momentDays, year.daysTarget),
        pebbles: countCell(month.pebbleDays, year.daysTarget),
        weeks: countCell(month.weeksFilled, month.fullWeeks),
        nextStep: year.nextStep?.month === n ? year.nextStep : null,
      },
    ]
  })
}
```

- [ ] **Step 9: Write `explorer-model.ts`**

```ts
import { arcPath, polar } from "@/lib/achievements/geometry"
import { bandItems } from "@/lib/achievements/domains-model"
import { familyRuns } from "@/lib/achievements/emotions-model"
import { pebbleSeal, SIX_FIRST_SEALS, type SealSpec } from "@/lib/achievements/seals"
import type { Segment } from "@/lib/achievements/year-disc"
import type {
  AchievementsDomains,
  AchievementsEmotions,
  AchievementsExplorer,
  CrossingPebble,
  EmotionFamily,
  EmotionSeat,
  ExplorerRing,
  StoneKind,
} from "@/lib/types"

// The Explorer wheel (#1308, spec D6), centre out: the first pebble, the
// first five, the six firsts, 25 pebbles, the domains, the emotions. Every
// seat's state is served; this places the seats.

export const WHEEL_RADII: Record<ExplorerRing, number> = {
  first_five: 44,
  six_firsts: 68,
  twenty_five: 90,
  domains: 112,
  emotions: 134,
}

type At = { x: number; y: number }
const at = (r: number, deg: number): At => {
  const [x, y] = polar(r, deg)
  return { x, y }
}

export type WheelModel = {
  centre: CrossingPebble | null
  tracks: Record<ExplorerRing, boolean>
  firstFive: (At & { slug: string; done: boolean; isNew: boolean })[]
  sixFirsts: (At & { kind: StoneKind; done: boolean; isNew: boolean; seal: SealSpec; width: number })[]
  twentyFive: (At & { lit: boolean })[]
  domains: (At & { on: boolean; isNew: boolean })[]
  emotions: (At & { seat: EmotionSeat; seal: SealSpec | null; width: number })[]
  families: { family: EmotionFamily | null; arc: string; tick: Segment }[]
}

export function wheelModel(
  explorer: AchievementsExplorer,
  emotions: AchievementsEmotions | null,
  domains: AchievementsDomains | null,
): WheelModel {
  const seats = emotions?.seats ?? []
  const step = 360 / Math.max(seats.length, 1)
  const seatDeg = (k: number) => (k - 2.5) * step
  const band = domains ? bandItems(domains) : []
  const lit = explorer.twentyFive?.count ?? 0
  return {
    centre: explorer.firstPebble,
    tracks: {
      first_five: explorer.ringsDone.includes("first_five"),
      six_firsts: explorer.ringsDone.includes("six_firsts"),
      twenty_five: explorer.ringsDone.includes("twenty_five"),
      domains: explorer.ringsDone.includes("domains"),
      emotions: explorer.ringsDone.includes("emotions"),
    },
    firstFive: explorer.firstFive.map((f, i) => ({ ...at(WHEEL_RADII.first_five, (i * 360) / Math.max(explorer.firstFive.length, 1)), slug: f.slug, done: f.done, isNew: f.isNew })),
    sixFirsts: explorer.sixFirsts.map((s) => {
      const { width, angle, ...seal } = SIX_FIRST_SEALS[s.kind]
      return { ...at(WHEEL_RADII.six_firsts, angle), kind: s.kind, done: s.done, isNew: s.isNew, seal, width }
    }),
    twentyFive: Array.from({ length: 25 }, (_, i) => ({ ...at(WHEEL_RADII.twenty_five, ((i + 0.5) * 360) / 25), lit: i < lit })),
    domains: band.map((d, i) => ({ ...at(WHEEL_RADII.domains, ((i + 0.5) * 360) / band.length), on: d.on, isNew: d.isNew })),
    emotions: seats.map((seat, k) => {
      const seal = seat.met ? (seat.pebble ? pebbleSeal(seat.pebble) : { size: "medium" as const, polarity: "neutral" as const, print: "tint" as const }) : null
      return { ...at(WHEEL_RADII.emotions, seatDeg(k)), seat, seal, width: 7.4 + (seat.pebble?.intensity ?? 2) * 1.6 }
    }),
    families: familyRuns(seats).map((run) => {
      const a0 = seatDeg(run.start) - step / 2
      const a1 = seatDeg(run.start + run.seats.length - 1) + step / 2
      const [x1, y1] = polar(WHEEL_RADII.emotions + 6, a0)
      const [x2, y2] = polar(WHEEL_RADII.emotions + 11, a0)
      return { family: run.family, arc: arcPath(WHEEL_RADII.emotions, a0 + 1.2, a1 - 1.2), tick: { x1, y1, x2, y2 } }
    }),
  }
}

/** What a ring holds, for the card's margin and its spoken label. */
export type RingSummary = { done: boolean; count: number; of: number }

export function explorerSummary(
  explorer: AchievementsExplorer,
  emotions: AchievementsEmotions | null,
  domains: AchievementsDomains | null,
): Record<ExplorerRing, RingSummary> {
  const done = (ring: ExplorerRing) => explorer.ringsDone.includes(ring)
  return {
    first_five: { done: done("first_five"), count: explorer.firstFive.filter((f) => f.done).length, of: explorer.firstFive.length },
    six_firsts: { done: done("six_firsts"), count: explorer.sixFirsts.filter((s) => s.done).length, of: explorer.sixFirsts.length },
    twenty_five: { done: done("twenty_five"), count: explorer.twentyFive?.count ?? 0, of: 25 },
    domains: { done: done("domains"), count: domains?.count ?? 0, of: domains?.of ?? 0 },
    emotions: { done: done("emotions"), count: emotions?.count ?? 0, of: emotions?.of ?? 0 },
  }
}

/** The card's margin, one bar per ring, centre out. */
export function explorerBars(summary: Record<ExplorerRing, RingSummary>): number[] {
  return (["first_five", "six_firsts", "twenty_five", "domains", "emotions"] as const).map((ring) => {
    const s = summary[ring]
    return s.done ? 1 : s.of > 0 ? s.count / s.of : 0
  })
}
```

- [ ] **Step 10: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/achievements/models.test.ts`

Expected: PASS, 18 tests.

- [ ] **Step 11: Commit**

```bash
git add apps/web/lib/achievements/seals.ts apps/web/lib/achievements/palette.ts apps/web/lib/achievements/emotions-model.ts apps/web/lib/achievements/domains-model.ts apps/web/lib/achievements/ladder-model.ts apps/web/lib/achievements/year-model.ts apps/web/lib/achievements/explorer-model.ts apps/web/lib/achievements/models.test.ts
git commit -m "feat(web): map the achievements payload onto its figures"
```

### Task 15: The words' values

**Files:**
- Create: `apps/web/lib/achievements/copy.ts`
- Test: `apps/web/lib/achievements/copy.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from "vitest"
import { capitalize, longDay, monthName, nextStepValues, shortDay, spokenList } from "./copy"

describe("copy values", () => {
  it("names months in each locale", () => {
    expect([monthName(11, "en"), monthName(11, "fr")]).toEqual(["November", "novembre"])
  })

  it("prints a served local day without shifting it", () => {
    expect(shortDay("2026-03-01", "en")).toBe("Mar 1")
    expect(longDay("2026-03-01", "fr")).toBe("dimanche 1 mars 2026")
  })

  it("spells the next step's days", () => {
    expect(nextStepValues({ month: 11, needs: "moments", daysLeft: 11, reachable: true }, "en")).toEqual({
      left: 11, leftWords: "eleven", needs: "moments", month: "November",
    })
    expect(nextStepValues({ month: 3, needs: "pebbles", daysLeft: 13, reachable: true }, "fr").leftWords).toBe("treize")
  })

  it("capitalizes and lists", () => {
    expect(capitalize("novembre", "fr")).toBe("Novembre")
    expect(spokenList(["March", "May", "June"], "en")).toBe("March, May, and June")
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/achievements/copy.test.ts`

Expected: FAIL with `Failed to resolve import "./copy"`.

- [ ] **Step 3: Write `copy.ts`**

```ts
import { cardinalWords } from "@/lib/i18n/number-words"
import type { Locale } from "@/lib/i18n/locales"
import type { YearNextStep } from "@/lib/types"

// Semantic values → the words' inputs (#1308, spec §4 "Localise"). The
// sentences live in the message catalogs; these pick the values they take.
// Dates are formatted, never computed: a `YYYY-MM-DD` is the server's local
// day, so it is read at noon UTC and printed in UTC; an instant prints in
// the reader's own zone.

export function capitalize(s: string, locale: Locale): string {
  return s.charAt(0).toLocaleUpperCase(locale) + s.slice(1)
}

function noonUtc(day: string): Date {
  return new Date(`${day}T12:00:00Z`)
}

/** "March", "mars". */
export function monthName(month: number, locale: Locale): string {
  return new Intl.DateTimeFormat(locale, { month: "long", timeZone: "UTC" }).format(Date.UTC(2026, month - 1, 15, 12))
}

/** A served local day, short: "Apr 21", "21 avr.". */
export function shortDay(day: string, locale: Locale): string {
  return new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", timeZone: "UTC" }).format(noonUtc(day))
}

/** A served instant's day, short, in the reader's zone. */
export function shortInstantDay(instant: string, locale: Locale): string {
  return new Intl.DateTimeFormat(locale, { day: "numeric", month: "short" }).format(new Date(instant))
}

/** The header's first day: "Sunday, March 1, 2026", "dimanche 1 mars 2026". */
export function longDay(day: string, locale: Locale): string {
  return new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(
    noonUtc(day),
  )
}

/** The values the next-step sentences take (`page.year.nextStep*`). */
export function nextStepValues(step: YearNextStep, locale: Locale): { left: number; leftWords: string; needs: string; month: string } {
  return {
    left: step.daysLeft,
    leftWords: cardinalWords(step.daysLeft, locale),
    needs: step.needs,
    month: monthName(step.month, locale),
  }
}

/** A spoken list: "March, May and June". */
export function spokenList(items: string[], locale: Locale): string {
  return new Intl.ListFormat(locale, { style: "long", type: "conjunction" }).format(items)
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/achievements/copy.test.ts`

Expected: PASS, 4 tests. The suite pins `TZ=Europe/Paris`, and the served local days print in UTC, so `Mar 1` holds in any zone.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/achievements/copy.ts apps/web/lib/achievements/copy.test.ts
git commit -m "feat(web): localise the achievements page's semantic values"
```

### Task 16: Verify Part 3 and open its PR

- [ ] **Step 1:** Run `npm run test --workspace=apps/web && npm run lint --workspace=apps/web && npx tsc --noEmit -p apps/web/tsconfig.json`. Expected: all green.
- [ ] **Step 2:** Open the PR with `gh stack`, titled `feat(web): the achievements figures' geometry and mapping`. Its body opens with `Part of #1308`.

---

# Part 4 — the atoms and the organisms (branch `feat/1308-achievements-web-components`)

Start the branch: `gh stack add feat/1308-achievements-web-components`.

These are React SVG components over Part 3's models. The web suite runs in node without a DOM, so components have no unit tests here; their logic lives in the tested models. Part 5 renders every one of them on `/sandbox/design` from the fixtures, and checks them in both themes.

The rules for every file in this part:
- **Colour:**
  - Ink is `var(--spine-ink)` and secondary ink is `var(--spine-ink-2)`.
  - An emotion's hue is `var(--spine-spot)` inside a `HueGroup`. That is its palette's primary on Paper and its secondary at Night, the Spine's rule.
  - Every "not yet" is secondary ink at 1 px.
- **Figures are decorative (`aria-hidden`).** Each card's and sheet's spoken label carries what the figure shows (Part 5).
- **The seals draw from `StoneSprite`**, the Spine's shared outlines, clips and hatching, mounted once per page (Part 5).

### Task 17: The atoms

**Files:**
- Create: `apps/web/components/achievements/HueGroup.tsx`, `AchievementSeal.tsx`, `GhostSeat.tsx`, `NewMark.tsx`, `FirstFiveMark.tsx`, `MarginLadder.tsx`, `CountCell.tsx`, `SkylineStage.tsx`

- [ ] **Step 1: `HueGroup.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react"
import { stonePaletteVars, type StonePalette } from "@/lib/config/stone-tones"

/**
 * Sets an emotion's palette on an SVG group (#1308): inside it,
 * `var(--spine-spot)` is the family's spot ink (primary on Paper, secondary
 * at Night) and `var(--spine-deep)` its deep tone. Without a palette the
 * children draw as they are, in ink.
 */
export function HueGroup({ palette, children }: { palette: StonePalette | null; children: ReactNode }) {
  if (!palette) return <g>{children}</g>
  return (
    <g className="spine-spot" style={stonePaletteVars(palette) as CSSProperties}>
      {children}
    </g>
  )
}
```

- [ ] **Step 2: `AchievementSeal.tsx`**: the board's `sealAt`, drawn on the Spine sprite's bodies (`StoneSeal`'s prints, placed inside a figure).

```tsx
import { getOutline } from "@/lib/config/pebble-outlines"
import { canvasTransform } from "@/lib/config/stone-geometry"
import type { StonePalette } from "@/lib/config/stone-tones"
import type { SealSpec } from "@/lib/achievements/seals"
import { stoneBodyId, stoneClipId, stoneHatchId } from "@/lib/utils/spine/stone-ids"
import { cn } from "@/lib/utils"
import { HueGroup } from "./HueGroup"

type AchievementSealProps = SealSpec & {
  x: number
  y: number
  /** The seal's width, in the figure's units. */
  width: number
  /** The emotion's palette; without one the seal prints in ink. */
  palette?: StonePalette | null
  /** Secondary ink: a month that holds no pebble. */
  muted?: boolean
  /** Dotted: a month still to come. */
  dotted?: boolean
}

/**
 * The flat seal (#1308): the Spine's print of a stone, placed in a figure's
 * SVG at (x, y). Strokes are screen pixels, since the sprite's bodies do not
 * scale theirs. Needs `StoneSprite` once on the page.
 */
export function AchievementSeal({ x, y, width, size, polarity, print, palette = null, muted = false, dotted = false }: AchievementSealProps) {
  const outline = getOutline(size, polarity)
  const scale = width / outline.width
  const body = `#${stoneBodyId(size, polarity)}`
  const tone = palette
    ? polarity === "lowlight"
      ? "text-(--spine-deep)"
      : "text-(--spine-spot)"
    : muted
      ? "text-spine-ink-2"
      : "text-spine-ink"
  return (
    <HueGroup palette={palette}>
      <g
        transform={`translate(${(x - width / 2).toFixed(2)} ${(y - (outline.height * scale) / 2).toFixed(2)}) scale(${scale.toFixed(4)})`}
        className={cn(tone)}
        strokeDasharray={dotted ? "1.6 1.7" : undefined}
      >
        {print === "solid" && <use href={body} fill="currentColor" stroke="currentColor" strokeWidth="1px" />}
        {print === "blank" && <use href={body} fill="var(--spine-stock)" stroke="currentColor" strokeWidth="1.1px" />}
        {print === "tint" && (
          <>
            <use href={body} fill="color-mix(in oklab, currentColor 15%, var(--spine-stock))" />
            <use href={body} fill="none" stroke="currentColor" strokeWidth="1.1px" />
          </>
        )}
        {print === "wash" && (
          <>
            <use href={body} fill="color-mix(in oklab, currentColor 9%, var(--spine-stock))" />
            <g clipPath={`url(#${stoneClipId(size, polarity)})`}>
              <g transform={canvasTransform(size, polarity)}>
                <use href={`#${stoneHatchId(size)}`} fill="none" stroke="currentColor" strokeWidth="0.8px" opacity={0.85} />
              </g>
            </g>
            <use href={body} fill="none" stroke="currentColor" strokeWidth="1.1px" />
          </>
        )}
      </g>
    </HueGroup>
  )
}
```

- [ ] **Step 3: `GhostSeat.tsx`**

```tsx
/** A seat not yet filled (#1308): a dotted ring in secondary ink, 1 px. */
export function GhostSeat({ x, y, r = 3.2 }: { x: number; y: number; r?: number }) {
  return (
    <circle
      cx={x}
      cy={y}
      r={r}
      fill="var(--spine-stock)"
      stroke="var(--spine-ink-2)"
      strokeWidth={1}
      strokeDasharray="1.2 1.6"
      strokeLinecap="round"
    />
  )
}
```

- [ ] **Step 4: `NewMark.tsx`**

```tsx
/** "New since you last looked" on a figure (#1308, D10): a hairline ring and a dot. */
export function NewMark({ x, y, width }: { x: number; y: number; width: number }) {
  return (
    <>
      <circle cx={x} cy={y} r={width * 0.86} fill="none" stroke="var(--spine-ink)" strokeWidth={1} />
      <circle cx={x + width * 0.66} cy={y - width * 0.66} r={1.9} fill="var(--spine-ink)" />
    </>
  )
}
```

- [ ] **Step 5: `FirstFiveMark.tsx`**: the board's `FIVE_MARKS`.

```tsx
import { AchievementSeal } from "./AchievementSeal"

const SOUL = ["M30,110 Q65,55 100,110 Q135,165 170,110"]
const GLYPH = ["M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", "M100,100 L100,101"]

/** A small drawing in a 200-unit glyph box, centred at (x, y), `side` wide. */
function Glyph({ x, y, side, paths }: { x: number; y: number; side: number; paths: string[] }) {
  return (
    <g transform={`translate(${(x - side / 2).toFixed(2)} ${(y - side / 2).toFixed(2)}) scale(${(side / 200).toFixed(4)})`}>
      {paths.map((d) => (
        <path key={d} d={d} fill="none" stroke="var(--spine-ink)" strokeWidth={(6 * 200 * 0.19) / side} strokeLinecap="round" strokeLinejoin="round" />
      ))}
    </g>
  )
}

/**
 * One of the first five (#1308), drawn as the part its task makes, as the
 * Home checklist's pictures are: a blank seal, the seal lit, three seals
 * together, a soul's mark, a glyph in a dashed circle. A slug this build does
 * not know draws as the blank seal.
 */
export function FirstFiveMark({ slug, x, y }: { slug: string; x: number; y: number }) {
  switch (slug) {
    case "first-pebble":
      return <AchievementSeal x={x} y={y} width={10} size="medium" polarity="highlight" print="solid" />
    case "first-collection":
      return (
        <>
          <AchievementSeal x={x - 4.6} y={y + 2.4} width={6} size="small" polarity="highlight" print="solid" />
          <AchievementSeal x={x} y={y - 3} width={6.6} size="medium" polarity="neutral" print="tint" />
          <AchievementSeal x={x + 4.6} y={y + 2.4} width={6} size="small" polarity="lowlight" print="wash" />
        </>
      )
    case "first-soul":
      return <Glyph x={x} y={y} side={14} paths={SOUL} />
    case "first-glyph":
      return (
        <>
          <circle cx={x} cy={y} r={7.2} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} strokeDasharray="1.6 1.6" />
          <Glyph x={x} y={y} side={9.5} paths={GLYPH} />
        </>
      )
    default:
      return <AchievementSeal x={x} y={y} width={10} size="medium" polarity="neutral" print="blank" />
  }
}
```

- [ ] **Step 6: `MarginLadder.tsx`**

```tsx
import { cn } from "@/lib/utils"

/**
 * A card's margin ladder (#1308): one bar per step, filled to its share.
 * `tall` draws one long bar (the domains). Decorative: the card's label says
 * the same in words.
 */
export function MarginLadder({ fills, tall = false }: { fills: number[]; tall?: boolean }) {
  return (
    <div aria-hidden className="flex flex-col items-center gap-1">
      {fills.map((fill, i) => (
        <span
          key={i}
          className={cn(
            "block w-1 rounded-sm",
            tall ? "h-[120px]" : "h-[11px]",
            fill >= 1 ? "bg-spine-ink" : "shadow-[inset_0_0_0_1px_var(--spine-ink-2)]",
          )}
          style={
            fill > 0 && fill < 1
              ? { background: `linear-gradient(var(--spine-ink) ${(fill * 100).toFixed(1)}%, transparent 0)` }
              : undefined
          }
        />
      ))}
    </div>
  )
}
```

- [ ] **Step 7: `CountCell.tsx`**: the year table's `cell()`.

```tsx
import type { CSSProperties } from "react"
import type { CountCell as Cell } from "@/lib/achievements/year-model"
import { cn } from "@/lib/utils"

/**
 * One count over a hairline gauge (#1308): the year table's cell. A full gauge
 * is set in the done style; without a denominator it shows the count alone;
 * a month with no full week yet shows a dash. `hue` inks the pebbles column
 * in the month's family (CSS colour over a palette's variables).
 */
export function CountCell({ cell, showOf = false, hue }: { cell: Cell; showOf?: boolean; hue?: string }) {
  const width = 52
  const tone = hue ?? "var(--spine-ink)"
  return (
    <span className={cn("flex flex-col items-center gap-1", cell.done && "font-medium")}>
      <span className="font-spine-office text-sm leading-none text-spine-ink-2 tabular-nums">
        {cell.none ? (
          "—"
        ) : (
          <>
            <b className={cn("font-normal", cell.done && "font-semibold")} style={cell.done ? ({ color: tone } as CSSProperties) : undefined}>
              {cell.value}
            </b>
            {showOf && cell.of !== null && <span className="font-light">/{cell.of}</span>}
          </>
        )}
      </span>
      {cell.of !== null && (
        <svg aria-hidden focusable="false" width={width} height={4} viewBox={`0 0 ${width} 4`} className="block">
          <line x1={0} y1={2} x2={width} y2={2} stroke="var(--spine-ink-2)" strokeWidth={1} strokeOpacity={0.55} />
          {cell.fill > 0 && <rect x={0} y={0.75} width={Math.round(width * cell.fill * 10) / 10} height={2.5} rx={1.25} fill={tone} />}
        </svg>
      )}
    </span>
  )
}
```

- [ ] **Step 8: `SkylineStage.tsx`**: the Pebbles sheet's `stage()`.

```tsx
import { useId } from "react"
import { GROUND, STAGES, stageAlone, stageClip } from "@/lib/achievements/skyline"

/**
 * One stage of the skyline, alone (#1308): a register row's mark on the
 * pebbles sheet. Inked when reached, its served fill when next, a contour
 * when ahead.
 */
export function SkylineStage({ stage, fill, scale = 1 }: { stage: number; fill: number; scale?: number }) {
  const clipId = useId()
  const { transform, baseline } = stageAlone(stage, scale)
  return (
    <svg aria-hidden focusable="false" viewBox="-14 -14 28 28" width={28} height={28} className="block overflow-visible">
      <g transform={transform}>
        {fill >= 1 ? (
          <path d={STAGES[stage]} fill="var(--spine-ink)" />
        ) : (
          <>
            {fill > 0 && (
              <>
                <clipPath id={clipId}>
                  <rect {...stageClip(stage, fill)} />
                </clipPath>
                <path d={STAGES[stage]} fill="var(--spine-ink)" clipPath={`url(#${clipId})`} />
              </>
            )}
            <path d={STAGES[stage]} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
          </>
        )}
        <line x1={baseline[0]} x2={baseline[1]} y1={GROUND} y2={GROUND} stroke="var(--spine-ink-2)" strokeWidth={1} vectorEffect="non-scaling-stroke" />
      </g>
    </svg>
  )
}
```

- [ ] **Step 9: Typecheck and lint**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`. Expected: no errors.

- [ ] **Step 10: Commit**

```bash
git add apps/web/components/achievements/HueGroup.tsx apps/web/components/achievements/AchievementSeal.tsx apps/web/components/achievements/GhostSeat.tsx apps/web/components/achievements/NewMark.tsx apps/web/components/achievements/FirstFiveMark.tsx apps/web/components/achievements/MarginLadder.tsx apps/web/components/achievements/CountCell.tsx apps/web/components/achievements/SkylineStage.tsx
git commit -m "feat(web): add the achievements atoms: the flat seal, the gauge, the stage"
```

### Task 18: The organisms

**Files:**
- Create: `apps/web/components/achievements/ExplorerWheel.tsx`, `EmotionRosace.tsx`, `DomainBand.tsx`, `PebbleSkyline.tsx`, `YearDisc.tsx`
- Modify: `apps/web/lib/i18n/messages/en.json`, `fr.json`

- [ ] **Step 1: Add the rosace's one string**

In `en.json`, replace:

```json
        "description": "Small, medium and large; lowlight, neutral and highlight: each has its first pebble."
      }
    }
  },
  "wallet": {
```

with:

```json
        "description": "Small, medium and large; lowlight, neutral and highlight: each has its first pebble."
      }
    },
    "page": {
      "rosaceOf": "of {of}"
    }
  },
  "wallet": {
```

In `fr.json`, replace:

```json
        "description": "Petit, moyen et grand ; creux, neutre et temps fort : chacun a son premier galet."
      }
    }
  },
  "wallet": {
```

with:

```json
        "description": "Petit, moyen et grand ; creux, neutre et temps fort : chacun a son premier galet."
      }
    },
    "page": {
      "rosaceOf": "sur {of}"
    }
  },
  "wallet": {
```

- [ ] **Step 2: `ExplorerWheel.tsx`**: the board's `wheel()`.

```tsx
import { WHEEL_RADII, wheelModel } from "@/lib/achievements/explorer-model"
import { pebbleSeal } from "@/lib/achievements/seals"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import type { AchievementsDomains, AchievementsEmotions, AchievementsExplorer, ExplorerRing } from "@/lib/types"
import { AchievementSeal } from "./AchievementSeal"
import { FirstFiveMark } from "./FirstFiveMark"
import { GhostSeat } from "./GhostSeat"
import { HueGroup } from "./HueGroup"
import { NewMark } from "./NewMark"

type ExplorerWheelProps = {
  explorer: AchievementsExplorer
  emotions: AchievementsEmotions | null
  domains: AchievementsDomains | null
  palettes: AchievementPalettes
  /** The largest the wheel draws, in CSS px; it shrinks with its column. */
  size: number
}

/**
 * The Explorer wheel (#1308, D6), centre out: the first pebble as its own
 * seal, the first five, the six firsts, 25 pebbles, the domains in the order
 * they came, the emotions on the rosace's seats. Decorative: its card's label
 * says what it shows.
 */
export function ExplorerWheel({ explorer, emotions, domains, palettes, size }: ExplorerWheelProps) {
  const w = wheelModel(explorer, emotions, domains)
  const track = (ring: ExplorerRing) => (
    <circle r={WHEEL_RADII[ring]} fill="none" stroke={w.tracks[ring] ? "var(--spine-ink-2)" : "var(--spine-rule)"} strokeWidth={1} />
  )
  return (
    <svg aria-hidden focusable="false" viewBox="-150 -150 300 300" className="block h-auto w-full overflow-visible" style={{ maxWidth: size }}>
      {w.families.map((f, i) => (
        <HueGroup key={i} palette={palettes.family(f.family)}>
          <path d={f.arc} fill="none" stroke={f.family ? "var(--spine-spot)" : "var(--spine-ink-2)"} strokeOpacity={0.5} strokeWidth={0.8} />
          <line {...f.tick} stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinecap="round" />
        </HueGroup>
      ))}
      {w.emotions.map(({ x, y, seat, seal, width }) =>
        seal ? (
          <g key={seat.emotionId}>
            <AchievementSeal x={x} y={y} width={width} {...seal} palette={palettes.family(seat.family)} />
            {seat.isNew && <NewMark x={x} y={y} width={width} />}
          </g>
        ) : (
          <GhostSeat key={seat.emotionId} x={x} y={y} />
        ),
      )}
      {track("domains")}
      {w.domains.map((d, i) =>
        d.on ? (
          <g key={i}>
            <AchievementSeal x={d.x} y={d.y} width={9} size="small" polarity="neutral" print="solid" />
            {d.isNew && <NewMark x={d.x} y={d.y} width={9} />}
          </g>
        ) : (
          <GhostSeat key={i} x={d.x} y={d.y} />
        ),
      )}
      {track("twenty_five")}
      {w.twentyFive.map((s, i) =>
        s.lit ? (
          <AchievementSeal key={i} x={s.x} y={s.y} width={6.2} size="small" polarity="highlight" print="solid" />
        ) : (
          <GhostSeat key={i} x={s.x} y={s.y} r={2.6} />
        ),
      )}
      {track("six_firsts")}
      {w.sixFirsts.map((s) =>
        s.done ? (
          <g key={s.kind}>
            <AchievementSeal x={s.x} y={s.y} width={s.width} {...s.seal} />
            {s.isNew && <NewMark x={s.x} y={s.y} width={s.width} />}
          </g>
        ) : (
          <GhostSeat key={s.kind} x={s.x} y={s.y} />
        ),
      )}
      {track("first_five")}
      {w.firstFive.map((f) =>
        f.done ? (
          <g key={f.slug}>
            <FirstFiveMark slug={f.slug} x={f.x} y={f.y} />
            {f.isNew && <NewMark x={f.x} y={f.y} width={10} />}
          </g>
        ) : (
          <GhostSeat key={f.slug} x={f.x} y={f.y} />
        ),
      )}
      {w.centre && <AchievementSeal x={0} y={0} width={25} {...pebbleSeal(w.centre)} palette={palettes.emotion(w.centre.emotionId)} />}
    </svg>
  )
}
```

- [ ] **Step 3: `EmotionRosace.tsx`**: the board's `gem()`. A lit facet sets its two tones as custom properties, and `dark:` picks the Night one, so one render serves both themes.

The family names take the deep tone on Paper and the light one at Night, not the spot ink as the board did. At 11 px the spot inks of joy and peace fall below 4.5:1 on the card; check this in Task 24.

```tsx
"use client"

import type { CSSProperties } from "react"
import { useTranslations } from "next-intl"
import { facetTone, rosace } from "@/lib/achievements/rosace"
import { facets, familyRuns } from "@/lib/achievements/emotions-model"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { stonePaletteVars } from "@/lib/config/stone-tones"
import type { AchievementsEmotions } from "@/lib/types"
import { cn } from "@/lib/utils"
import { HueGroup } from "./HueGroup"

type EmotionRosaceProps = {
  emotions: AchievementsEmotions
  palettes: AchievementPalettes
  size: number
  /** Family names around it and the count at its heart (the card and the sheet). */
  labelled?: boolean
  /** The family boundaries' ticks; off for a row's small mark. */
  ticks?: boolean
}

/**
 * The emotions rosace (#1308): a kite per served seat, lit in its family's
 * tone when met, a star between two met neighbours of one family, a dot by
 * each new one. Every "not yet" is outlined in secondary ink. Decorative: its
 * card's label carries the count.
 */
export function EmotionRosace({ emotions, palettes, size, labelled = false, ticks = true }: EmotionRosaceProps) {
  const t = useTranslations("achievements.page")
  const tc = useTranslations("emotionCategory")
  const n = emotions.seats.length
  const g = rosace(size, n, labelled)
  const step = (2 * Math.PI) / Math.max(n, 1)
  return (
    <svg aria-hidden focusable="false" viewBox={`0 0 ${size} ${size}`} className="block h-auto w-full overflow-visible" style={{ maxWidth: size }}>
      <path d={g.silhouette} fill="var(--spine-stock)" stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinejoin="round" />
      <path d={g.outline} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinejoin="round" />
      {facets(emotions.seats).map(({ k, seat, t: place, bridged }) => {
        if (!seat.met) return null
        const palette = palettes.family(seat.family)
        const paint = (soft: boolean): CSSProperties => {
          const tone = facetTone(place, soft)
          return { ...(palette ? stonePaletteVars(palette) : {}), "--tone-paper": tone.paper, "--tone-night": tone.night } as CSSProperties
        }
        const fill = palette ? "fill-(--tone-paper) dark:fill-(--tone-night)" : "fill-spine-ink-2"
        return (
          <g key={k}>
            <path d={g.kite(k)} className={fill} style={paint(false)} stroke="var(--spine-stock)" strokeWidth={0.9} />
            {bridged && <path d={g.star(k)} className={fill} style={paint(true)} stroke="var(--spine-stock)" strokeWidth={0.9} />}
          </g>
        )
      })}
      {familyRuns(emotions.seats).map((run) => {
        const edge = g.angle(run.start) - step / 2
        const [x1, y1] = g.point(g.radius + 3, edge)
        const [x2, y2] = g.point(g.radius + 9, edge)
        const [lx, ly] = g.point(g.radius + 22, g.angle(run.start + (run.seats.length - 1) / 2))
        return (
          <g key={run.start}>
            {ticks && <line x1={x1} y1={y1} x2={x2} y2={y2} stroke="var(--spine-ink-2)" strokeWidth={1} strokeLinecap="round" />}
            {labelled && run.family && (
              <HueGroup palette={palettes.family(run.family)}>
                {/* The family's deep tone on Paper, its light one at Night: the
                    spot inks are too pale for 11 px text on the card. */}
                <text
                  x={lx}
                  y={ly}
                  textAnchor="middle"
                  dominantBaseline="central"
                  className={cn(
                    "font-spine-sc text-[11px] font-semibold tracking-[.14em] uppercase",
                    palettes.family(run.family) ? "fill-(--sh) dark:fill-(--s)" : "fill-spine-ink",
                  )}
                >
                  {tc(`${run.family}.name`)}
                </text>
              </HueGroup>
            )}
          </g>
        )
      })}
      {emotions.seats.map((seat, k) => {
        if (!seat.isNew) return null
        const [x, y] = g.point(g.radius + 6.5, g.angle(k))
        return <circle key={seat.emotionId} cx={x} cy={y} r={2.6} fill="var(--spine-ink)" />
      })}
      {labelled && (
        <>
          <text x={g.centre} y={g.centre - 3} textAnchor="middle" className="fill-spine-ink font-spine-office text-[28px] font-light">
            {emotions.count}
          </text>
          <text x={g.centre} y={g.centre + 15} textAnchor="middle" className="fill-spine-ink-2 font-spine-sc text-[11px] font-semibold tracking-[.2em] uppercase">
            {t("rosaceOf", { of: emotions.of })}
          </text>
        </>
      )}
    </svg>
  )
}
```

- [ ] **Step 4: `DomainBand.tsx`**: the board's `shardStrip()`.

```tsx
import { bandItems } from "@/lib/achievements/domains-model"
import { shards } from "@/lib/achievements/shards"
import type { AchievementsDomains } from "@/lib/types"

/**
 * The mended band (#1308, D11): one shard per domain in the order they came,
 * inked when entered, dotted when not yet, a dot under a new one. Decorative:
 * its card's label carries the count and the coverage.
 */
export function DomainBand({ domains, width }: { domains: AchievementsDomains; width: number }) {
  const items = bandItems(domains)
  const height = 30
  const pieces = shards(items.length, width, height)
  return (
    <svg aria-hidden focusable="false" viewBox={`0 0 ${width} ${height + 10}`} className="block h-auto w-full overflow-visible" style={{ maxWidth: width }}>
      {items.map((item, i) =>
        item.on ? (
          <path
            key={item.domainId}
            d={pieces[i].d}
            fill="color-mix(in oklab, var(--spine-ink) 8%, var(--spine-stock))"
            stroke="var(--spine-ink)"
            strokeWidth={1.3}
            strokeLinejoin="round"
          />
        ) : (
          <path
            key={item.domainId}
            d={pieces[i].d}
            fill="none"
            stroke="var(--spine-ink-2)"
            strokeWidth={1}
            strokeDasharray="1.2 2.2"
            strokeLinecap="round"
          />
        ),
      )}
      {items.map((item, i) =>
        item.isNew ? (
          <circle
            key={`new-${item.domainId}`}
            cx={pieces[i].centre}
            cy={height + 6}
            r={2.4}
            fill="var(--spine-ink)"
            stroke="var(--spine-stock)"
            strokeWidth={1.6}
            paintOrder="stroke"
          />
        ) : null,
      )}
    </svg>
  )
}
```

- [ ] **Step 5: `PebbleSkyline.tsx`**: the board's `skyline()`.

```tsx
import { useId } from "react"
import { ladderStages } from "@/lib/achievements/ladder-model"
import { contour, STAGES, stageClip } from "@/lib/achievements/skyline"
import type { CountingLadder } from "@/lib/types"

const VARIANTS = {
  /** The pebbles card: eight stages. */
  card: { viewBox: "12 104 216 48", end: 224, width: 300 },
  /** The pebbles sheet, larger. */
  sheet: { viewBox: "12 104 216 48", end: 224, width: 350 },
  /** A glyph stack's line: its five stages, small. */
  line: { viewBox: "18 118 110 32", end: 126, width: 103 },
} as const

/**
 * One skyline (#1308, D3): a stage per served rung, inked when reached, the
 * next filled from the ground by the served progress, the rest a contour.
 * Decorative: its card's label carries the count and the milestones.
 */
export function PebbleSkyline({ ladder, variant }: { ladder: CountingLadder; variant: keyof typeof VARIANTS }) {
  const v = VARIANTS[variant]
  const clipId = useId()
  const stages = ladderStages(ladder).slice(0, STAGES.length)
  const partial = stages.findIndex((s) => !s.reached && s.fill > 0)
  const allReached = stages.length > 0 && stages.every((s) => s.reached)
  return (
    <svg aria-hidden focusable="false" viewBox={v.viewBox} className="block h-auto w-full overflow-visible" style={{ maxWidth: v.width }}>
      {stages.map((s, k) => (s.reached ? <path key={k} d={STAGES[k]} fill="var(--spine-ink)" /> : null))}
      {partial >= 0 && (
        <>
          <clipPath id={clipId}>
            <rect {...stageClip(partial, stages[partial].fill)} />
          </clipPath>
          <path d={STAGES[partial]} fill="var(--spine-ink)" clipPath={`url(#${clipId})`} />
        </>
      )}
      <path
        d={contour(stages.length, v.end)}
        fill="none"
        stroke={allReached ? "var(--spine-ink)" : "var(--spine-ink-2)"}
        strokeWidth={1}
        vectorEffect="non-scaling-stroke"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  )
}
```

- [ ] **Step 6: `YearDisc.tsx`**: the board's `yearDiscDocked()` and `monthCounter()`, with the decided treatment B.

```tsx
import { DISC_CENTRE, STONE_EDGE, STONE_GRAIN, discMonth } from "@/lib/achievements/year-disc"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import type { AchievementYear } from "@/lib/types"
import { AchievementSeal } from "./AchievementSeal"
import { HueGroup } from "./HueGroup"

/**
 * The year disc (#1308, D12), docked: each month's counter sits at the place
 * its served level names, and its served progress runs along the spoke. A
 * month with pebbles prints solid in its family's hue; a rim month adds the
 * hairline ring, the one sign of success, and inks its spoke and numeral.
 * Decorative: its card's label reads the months at each place.
 */
export function YearDisc({ year, palettes, size }: { year: AchievementYear; palettes: AchievementPalettes; size: number }) {
  const months = year.months.map(discMonth)
  return (
    <svg aria-hidden focusable="false" viewBox="10 10 220 220" className="block h-auto w-full overflow-visible" style={{ maxWidth: size }}>
      <path d={STONE_EDGE} fill="var(--spine-stock)" stroke="var(--spine-ink-2)" strokeWidth={1.2} strokeOpacity={0.85} strokeLinejoin="round" />
      {STONE_GRAIN.map(([cx, cy, r], i) => (
        <circle key={i} cx={cx} cy={cy} r={r} fill="var(--spine-ink-3)" />
      ))}
      <circle cx={DISC_CENTRE} cy={DISC_CENTRE} r={52} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} strokeOpacity={0.5} />
      <circle cx={DISC_CENTRE} cy={DISC_CENTRE} r={48.5} fill="none" stroke="var(--spine-ink-2)" strokeWidth={0.5} strokeOpacity={0.45} />
      {months.map((m) => {
        const palette = palettes.family(m.family)
        const hue = palette ? "var(--spine-spot)" : "var(--spine-ink)"
        return (
          <HueGroup key={m.month} palette={palette}>
            <line
              {...m.spoke}
              stroke={m.spoke.tone === "hue" ? hue : "var(--spine-ink-2)"}
              strokeWidth={m.spoke.tone === "hue" ? 1 : m.spoke.tone === "faint" ? 0.4 : 0.7}
            />
            {m.notches.map((n, i) => (
              <line key={i} x1={n.x1} y1={n.y1} x2={n.x2} y2={n.y2} stroke="var(--spine-ink-2)" strokeWidth={n.quiet ? 0.6 : 1} />
            ))}
            <text
              x={m.numeral.x}
              y={m.numeral.y}
              textAnchor="middle"
              fontSize={m.numeral.size}
              fontWeight={m.numeral.hue ? 600 : undefined}
              fill={m.numeral.hue ? hue : "var(--spine-ink-2)"}
              className="font-spine"
            >
              {m.numeral.text}
            </text>
            {m.goal && <line {...m.goal} stroke="var(--spine-ink)" strokeWidth={1.3} strokeLinecap="round" />}
            {m.climb && (
              <line
                x1={m.climb.x1}
                y1={m.climb.y1}
                x2={m.climb.x2}
                y2={m.climb.y2}
                stroke={m.climb.tone === "hue" ? hue : "var(--spine-ink)"}
                strokeWidth={1.8}
                strokeLinecap="round"
              />
            )}
            {m.counter.ring && (
              <circle cx={m.counter.x} cy={m.counter.y} r={12.5 * 0.78} fill="none" stroke="var(--spine-ink)" strokeWidth={0.8} />
            )}
            {m.family && palette ? (
              <AchievementSeal x={m.counter.x} y={m.counter.y} width={12.5} size="medium" polarity="neutral" print="solid" palette={palette} />
            ) : (
              <AchievementSeal x={m.counter.x} y={m.counter.y} width={12.5} size="medium" polarity="neutral" print="blank" muted dotted={m.upcoming} />
            )}
          </HueGroup>
        )
      })}
      <path d="M116,120 L124,120 M120,116 L120,124" stroke="var(--spine-ink-2)" strokeWidth={1.1} />
    </svg>
  )
}
```

- [ ] **Step 7: Typecheck and lint**

Run: `rm -f apps/web/tsconfig.tsbuildinfo && npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`

Expected: no errors. A stale `tsbuildinfo` reports `"achievements.page"` as an unknown namespace.

- [ ] **Step 8: Commit**

```bash
git add apps/web/components/achievements/ExplorerWheel.tsx apps/web/components/achievements/EmotionRosace.tsx apps/web/components/achievements/DomainBand.tsx apps/web/components/achievements/PebbleSkyline.tsx apps/web/components/achievements/YearDisc.tsx apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(web): draw the wheel, the rosace, the band, the skyline and the year disc"
```

### Task 19: Verify Part 4 and open its PR

- [ ] **Step 1:** Run `npm run test --workspace=apps/web && npm run lint --workspace=apps/web && npx tsc --noEmit -p apps/web/tsconfig.json`. Expected: all green.
- [ ] **Step 2:** Open the PR with `gh stack`, titled `feat(web): the achievements figures as React SVG`. Its body opens with `Part of #1308`, and notes that Part 5 renders the figures on `/sandbox/design`.

---

# Part 5 — the page (branch `feat/1308-achievements-web-page`)

Start the branch: `gh stack add feat/1308-achievements-web-page`.

This part replaces the old grid. The cards are not buttons yet; Part 6 gives them their sheets. Until then each card carries its spoken label as screen-reader text.

### Task 20: The page's copy

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`, `fr.json`

- [ ] **Step 1: Add the strings after `rosaceOf`**

In `en.json`, replace:

```json
      "rosaceOf": "of {of}"
    }
```

with:

```json
      "rosaceOf": "of {of}",
      "since": "since {date}",
      "days": "{count, plural, one {day} other {days}}",
      "daysAria": "{count, plural, one {# day} other {# days}} kept, since {date}.",
      "refreshing": "Updating your achievements…",
      "cards": {
        "explorer": "Explorer",
        "emotions": "Emotions",
        "domains": "Domains",
        "pebbles": "Pebbles",
        "year": "The year"
      },
      "explorerHint": "five rings",
      "sinceFirst": "since {date}",
      "domainsHint": "in the order they came",
      "yearHint": "in progress",
      "open": "{label} Open its sheet.",
      "ring": {
        "first_five": "{done, select, true {The first five, done.} other {The first five, {count} of {of}.}}",
        "six_firsts": "{done, select, true {The six firsts, done.} other {The six firsts, {count} of {of}.}}",
        "twenty_five": "{done, select, true {25 pebbles, done.} other {25 pebbles, {count} so far.}}",
        "domains": "Domains, {count} of {of}.",
        "emotions": "Emotions, {count} of {of}."
      },
      "fresh": "{count, plural, =0 {} one {One new since you last looked.} other {# new since you last looked.}}",
      "emotionsAria": "Emotions, {count} of {of}, seated by family.",
      "domainsAria": "Domains, {count} of {of}.",
      "coverage": {
        "none": "Link your pebbles to the parts of your life they touch, and your insights will start reading them.",
        "few": "You haven’t linked many pebbles to domains yet, so your insights have little to go on.",
        "third": "Your pebbles touch a third of your life. Link a few more domains and your insights will see further.",
        "half": "You’ve already linked pebbles with half of the domains. What a rich life!",
        "most": "Your pebbles reach most parts of your life. Your insights have plenty to read.",
        "all": "Every part of your life has a pebble in it. Your insights see the whole picture."
      },
      "pebblesLine": "{count, plural, one {# pebble} other {# pebbles}}. {stood, plural, =0 {No stone stands yet} one {One stone stands} other {{stoodWords} stones stand}}; {hasNext, select, true {the {ordinal} rises toward {next, number}.} other {every stone stands.}}",
      "glyphsCarved": "Glyphs carved",
      "glyphSales": "Glyph sales",
      "ladderAria": "{name}, {count, number}: {stood} of {rungs} milestones reached.",
      "yearAria": "The year {year}. On the rim: {rim}. On the inner disc: {inner}. On their way: {way}.",
      "none": "none",
      "nextStep": "{left, plural, =0 {Keep {needs, select, pebbles {a pebble} other {a moment}} in every full week and {month} reaches {needs, select, pebbles {the rim} other {the inner disc}}.} one {One more day with {needs, select, pebbles {a pebble} other {a moment}}, one in each full week, brings {month} to {needs, select, pebbles {the rim} other {the inner disc}}.} other {{wordsCap} more days with {needs, select, pebbles {a pebble} other {a moment}}, one in each full week, bring {month} to {needs, select, pebbles {the rim} other {the inner disc}}.}}",
      "nextStepUnreachable": "{monthCap} can no longer reach {needs, select, pebbles {the rim} other {the inner disc}} this month: a full week went by without {needs, select, pebbles {a pebble} other {a moment}}."
    }
```

In `fr.json`, replace:

```json
      "rosaceOf": "sur {of}"
    }
```

with:

```json
      "rosaceOf": "sur {of}",
      "since": "depuis le {date}",
      "days": "{count, plural, one {jour} other {jours}}",
      "daysAria": "{count, plural, one {# jour} other {# jours}} gardés, depuis le {date}.",
      "refreshing": "Mise à jour de tes succès…",
      "cards": {
        "explorer": "Exploration",
        "emotions": "Émotions",
        "domains": "Domaines",
        "pebbles": "Galets",
        "year": "L’année"
      },
      "explorerHint": "cinq anneaux",
      "sinceFirst": "depuis le {date}",
      "domainsHint": "dans l’ordre d’arrivée",
      "yearHint": "en cours",
      "open": "{label} Ouvrir sa feuille.",
      "ring": {
        "first_five": "{done, select, true {Les cinq premiers pas, faits.} other {Les cinq premiers pas, {count} sur {of}.}}",
        "six_firsts": "{done, select, true {Les six premières fois, faites.} other {Les six premières fois, {count} sur {of}.}}",
        "twenty_five": "{done, select, true {25 galets, faits.} other {25 galets, {count} pour l’instant.}}",
        "domains": "Domaines, {count} sur {of}.",
        "emotions": "Émotions, {count} sur {of}."
      },
      "fresh": "{count, plural, =0 {} one {Un nouveau depuis ta dernière visite.} other {# nouveaux depuis ta dernière visite.}}",
      "emotionsAria": "Émotions, {count} sur {of}, rangées par famille.",
      "domainsAria": "Domaines, {count} sur {of}.",
      "coverage": {
        "none": "Relie tes galets aux parts de ta vie qu’ils touchent, et tes analyses commenceront à les lire.",
        "few": "Tu n’as pas encore relié beaucoup de galets à des domaines : tes analyses ont peu à lire.",
        "third": "Tes galets touchent un tiers de ta vie. Relie quelques domaines de plus et tes analyses verront plus loin.",
        "half": "Tu as déjà relié des galets à la moitié des domaines. Quelle vie riche !",
        "most": "Tes galets touchent la plupart des parts de ta vie. Tes analyses ont de quoi lire.",
        "all": "Chaque part de ta vie tient un galet. Tes analyses voient le tableau entier."
      },
      "pebblesLine": "{count, plural, one {# galet} other {# galets}}. {stood, plural, =0 {Aucune pierre ne se dresse encore} one {Une pierre se dresse} other {{stoodWords} pierres se dressent}} ; {hasNext, select, true {la {ordinal} s’élève vers {next, number}.} other {toutes les pierres se dressent.}}",
      "glyphsCarved": "Glyphes gravés",
      "glyphSales": "Ventes de glyphes",
      "ladderAria": "{name}, {count, number} : {stood} paliers atteints sur {rungs}.",
      "yearAria": "L’année {year}. Au bord : {rim}. Sur le disque intérieur : {inner}. En chemin : {way}.",
      "none": "aucun",
      "nextStep": "{left, plural, =0 {Garde {needs, select, pebbles {un galet} other {un moment}} dans chaque semaine pleine, et {month} atteint {needs, select, pebbles {le bord} other {le disque intérieur}}.} one {Encore un jour avec {needs, select, pebbles {un galet} other {un moment}}, un par semaine pleine, et {month} atteint {needs, select, pebbles {le bord} other {le disque intérieur}}.} other {Encore {words} jours avec {needs, select, pebbles {un galet} other {un moment}}, un par semaine pleine, et {month} atteint {needs, select, pebbles {le bord} other {le disque intérieur}}.}}",
      "nextStepUnreachable": "{monthCap} n’atteindra plus {needs, select, pebbles {le bord} other {le disque intérieur}} ce mois-ci : une semaine pleine est passée sans {needs, select, pebbles {galet} other {moment}}."
    }
```

On these strings:
- `select` arguments take the strings `"true"`/`"false"`.
- `nextStep` passes both `words` and `wordsCap`: English starts the sentence with the number and French does not.
- `#` and `{x, number}` format with the locale's grouping (`1,000`, `1 000`).
- French plural `many` falls back to `other`, as ICU does. No string here has a separate `many` form.

- [ ] **Step 2: Check that they parse**

Run: `node -e "for (const l of ['en','fr']) JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/'+l+'.json','utf8'))"`. Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(web): write the achievements page's copy in english and french"
```

### Task 21: The card shell, the header and the five cards

**Files:**
- Create: `apps/web/components/achievements/AchievementCard.tsx`, `DaysKeptHeader.tsx`, `NextStepLine.tsx`, `ExplorerCard.tsx`, `EmotionsCard.tsx`, `DomainsCard.tsx`, `PebblesCard.tsx`, `YearCard.tsx`
- Create: `apps/web/lib/hooks/useYearAria.ts`

- [ ] **Step 1: `AchievementCard.tsx`**

A stretched button lies over the card, so the figure stays plain markup. The "ready" mark (Part 7) sits above that button rather than inside it: a button can't contain a button.

```tsx
import type { ReactNode } from "react"

type AchievementCardProps = {
  /** The margin's vertical title. */
  title: string
  /** The band's heading; the title when absent (a year's card sets its year). */
  heading?: string
  /** The band's italic note ("five rings"). */
  hint?: string | null
  /** The margin's ladder, under the vertical title. */
  ladder: ReactNode
  /** The card's spoken label, assembled from the payload (spec §5). */
  label: string
  /** Opens the card's sheet; without it the card is not a button. */
  onOpen?: () => void
  /** A control above the card's button, top right: the "ready" mark. */
  corner?: ReactNode
  children: ReactNode
}

/**
 * The card shell (#1308): a margin with the title set vertically over its
 * ladder, a title band, the figure. The whole card is one button laid over
 * it, so the figure inside stays plain markup and a corner control can sit
 * above without nesting buttons.
 */
export function AchievementCard({ title, heading, hint, ladder, label, onOpen, corner, children }: AchievementCardProps) {
  return (
    <article className="relative grid grid-cols-[34px_minmax(0,1fr)] rounded-[11px] bg-spine-stock text-spine-ink shadow-(--spine-card-shadow)">
      <div className="flex flex-col items-center justify-between gap-3.5 border-r border-spine-card-rule pt-4 pb-3.5">
        <span
          aria-hidden
          className="rotate-180 font-spine-sc text-[11px] font-semibold tracking-[.26em] whitespace-nowrap text-spine-ink-2 [writing-mode:vertical-rl]"
        >
          {title}
        </span>
        {ladder}
      </div>
      <div className="min-w-0 px-3.5 pb-4">
        <header className="flex items-baseline justify-between gap-2 border-b border-spine-card-rule pt-3.5 pb-2">
          <h2 className="font-spine-sc text-[11.5px] font-semibold tracking-[.22em] whitespace-nowrap">{heading ?? title}</h2>
          {hint ? <em className="text-sm font-[380] whitespace-nowrap text-spine-ink-2">{hint}</em> : null}
        </header>
        {children}
      </div>
      {onOpen ? (
        <button
          type="button"
          onClick={onOpen}
          aria-label={label}
          className="absolute inset-0 cursor-pointer rounded-[11px] outline-offset-2 focus-visible:outline-2 focus-visible:outline-spine-ink"
        />
      ) : (
        <p className="sr-only">{label}</p>
      )}
      {corner ? <div className="absolute top-2 right-2 z-10">{corner}</div> : null}
    </article>
  )
}
```

- [ ] **Step 2: `DaysKeptHeader.tsx`**

```tsx
"use client"

import { useTranslations } from "next-intl"
import { longDay } from "@/lib/achievements/copy"
import { useFormatNumber, useLocale } from "@/lib/i18n"

/**
 * The page's header (#1308, D2): the days kept since the account began, a
 * calendar age that never resets. Both values are the server's.
 */
export function DaysKeptHeader({ since, daysKept }: { since: string; daysKept: number }) {
  const t = useTranslations("achievements.page")
  const { locale } = useLocale()
  const formatNumber = useFormatNumber()
  const date = longDay(since, locale)
  return (
    <header className="grid grid-cols-[auto_1fr] items-end gap-x-3.5 px-1.5 pt-1.5 pb-5">
      <p className="sr-only">{t("daysAria", { count: daysKept, date })}</p>
      <span aria-hidden className="col-span-full mb-1 font-spine-sc text-[11.5px] font-semibold tracking-[.2em] text-spine-ink-2 lowercase">
        {t("since", { date })}
      </span>
      <span aria-hidden className="font-spine-office text-[78px] leading-[.84] font-[230] tracking-[-.025em] tabular-nums">
        {formatNumber(daysKept)}
      </span>
      <span aria-hidden className="mb-1.5 font-spine-sc text-[15px] font-[560] tracking-[.16em] text-spine-ink-2">
        {t("days", { count: daysKept })}
      </span>
    </header>
  )
}
```

- [ ] **Step 3: `NextStepLine.tsx`**

```tsx
"use client"

import { useTranslations } from "next-intl"
import { capitalize, nextStepValues } from "@/lib/achievements/copy"
import { useLocale } from "@/lib/i18n"
import type { YearNextStep } from "@/lib/types"

/** What the current month needs next (#1308, D12), in words; every value is the server's. */
export function NextStepLine({ step, className }: { step: YearNextStep; className?: string }) {
  const t = useTranslations("achievements.page")
  const { locale } = useLocale()
  const v = nextStepValues(step, locale)
  const text = step.reachable
    ? t("nextStep", { left: v.left, words: v.leftWords, wordsCap: capitalize(v.leftWords, locale), needs: v.needs, month: v.month })
    : t("nextStepUnreachable", { monthCap: capitalize(v.month, locale), needs: v.needs })
  return <p className={className}>{text}</p>
}
```

- [ ] **Step 4: `lib/hooks/useYearAria.ts`**

```ts
"use client"

import { useTranslations } from "next-intl"
import { monthName, spokenList } from "@/lib/achievements/copy"
import { yearPlaces } from "@/lib/achievements/year-model"
import { useLocale } from "@/lib/i18n"
import type { AchievementYear } from "@/lib/types"

/** The year disc, spoken (#1308, spec §5): the server's months at each place. */
export function useYearAria(year: AchievementYear): string {
  const t = useTranslations("achievements.page")
  const { locale } = useLocale()
  const places = yearPlaces(year)
  const say = (months: number[]) => (months.length ? spokenList(months.map((m) => monthName(m, locale)), locale) : t("none"))
  return t("yearAria", { year: String(year.year), rim: say(places.rim), inner: say(places.inner), way: say(places.way) })
}
```

- [ ] **Step 5: `ExplorerCard.tsx`**

```tsx
"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { explorerBars, explorerSummary } from "@/lib/achievements/explorer-model"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import type { AchievementsDomains, AchievementsEmotions, AchievementsExplorer, ExplorerRing } from "@/lib/types"
import { AchievementCard } from "./AchievementCard"
import { ExplorerWheel } from "./ExplorerWheel"
import { MarginLadder } from "./MarginLadder"

const RINGS: ExplorerRing[] = ["first_five", "six_firsts", "twenty_five", "domains", "emotions"]

type ExplorerCardProps = {
  explorer: AchievementsExplorer
  emotions: AchievementsEmotions | null
  domains: AchievementsDomains | null
  palettes: AchievementPalettes
  onOpen?: () => void
  corner?: ReactNode
}

/** The Explorer card (#1308, D1, D6): five rings around the first pebble. */
export function ExplorerCard({ explorer, emotions, domains, palettes, onOpen, corner }: ExplorerCardProps) {
  const t = useTranslations("achievements.page")
  const summary = explorerSummary(explorer, emotions, domains)
  const fresh = [...explorer.firstFive, ...explorer.sixFirsts, ...(explorer.twentyFive ? [explorer.twentyFive] : [])].filter((x) => x.isNew).length
  const said = [
    t("cards.explorer") + ".",
    ...RINGS.map((ring) => {
      const s = summary[ring]
      return t(`ring.${ring}`, { done: s.done ? "true" : "false", count: s.count, of: s.of })
    }),
    t("fresh", { count: fresh }),
  ].join(" ").trim()
  return (
    <AchievementCard
      title={t("cards.explorer")}
      hint={t("explorerHint")}
      ladder={<MarginLadder fills={explorerBars(summary)} />}
      label={onOpen ? t("open", { label: said }) : said}
      onOpen={onOpen}
      corner={corner}
    >
      <div className="mt-3 flex justify-center">
        <ExplorerWheel explorer={explorer} emotions={emotions} domains={domains} palettes={palettes} size={300} />
      </div>
    </AchievementCard>
  )
}
```

- [ ] **Step 6: `EmotionsCard.tsx`**

```tsx
"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { shortInstantDay } from "@/lib/achievements/copy"
import { familyBars } from "@/lib/achievements/emotions-model"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { useLocale } from "@/lib/i18n"
import type { AchievementsEmotions, CrossingPebble } from "@/lib/types"
import { AchievementCard } from "./AchievementCard"
import { EmotionRosace } from "./EmotionRosace"
import { MarginLadder } from "./MarginLadder"

type EmotionsCardProps = {
  emotions: AchievementsEmotions
  /** The first pebble, for the band's "since" note. */
  firstPebble: CrossingPebble | null
  palettes: AchievementPalettes
  onOpen?: () => void
  corner?: ReactNode
}

/** The Emotions card (#1308, D1): the rosace and nothing else. */
export function EmotionsCard({ emotions, firstPebble, palettes, onOpen, corner }: EmotionsCardProps) {
  const t = useTranslations("achievements.page")
  const { locale } = useLocale()
  const said = [
    t("emotionsAria", { count: emotions.count, of: emotions.of }),
    t("fresh", { count: emotions.seats.filter((s) => s.isNew).length }),
  ].join(" ").trim()
  return (
    <AchievementCard
      title={t("cards.emotions")}
      hint={firstPebble ? t("sinceFirst", { date: shortInstantDay(firstPebble.happenedAt, locale) }) : null}
      ladder={<MarginLadder fills={familyBars(emotions.seats)} />}
      label={onOpen ? t("open", { label: said }) : said}
      onOpen={onOpen}
      corner={corner}
    >
      <div className="mt-3 flex justify-center">
        <EmotionRosace emotions={emotions} palettes={palettes} size={270} labelled />
      </div>
    </AchievementCard>
  )
}
```

- [ ] **Step 7: `DomainsCard.tsx`**

```tsx
"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import type { AchievementsDomains } from "@/lib/types"
import { AchievementCard } from "./AchievementCard"
import { DomainBand } from "./DomainBand"
import { MarginLadder } from "./MarginLadder"

/** The Domains card (#1308, D11): the band and what its coverage means, never a count. */
export function DomainsCard({ domains, onOpen, corner }: { domains: AchievementsDomains; onOpen?: () => void; corner?: ReactNode }) {
  const t = useTranslations("achievements.page")
  const coverage = domains.coverage ? t(`coverage.${domains.coverage}`) : null
  const said = [
    t("domainsAria", { count: domains.count, of: domains.of }),
    coverage ?? "",
    t("fresh", { count: domains.entered.filter((e) => e.isNew).length }),
  ].join(" ").trim()
  return (
    <AchievementCard
      title={t("cards.domains")}
      hint={t("domainsHint")}
      ladder={<MarginLadder fills={[domains.of > 0 ? domains.count / domains.of : 0]} tall />}
      label={onOpen ? t("open", { label: said }) : said}
      onOpen={onOpen}
      corner={corner}
    >
      <div className="mt-4 flex justify-center">
        <DomainBand domains={domains} width={300} />
      </div>
      {coverage ? <p className="mt-2.5 text-center text-base leading-snug font-[380] italic">{coverage}</p> : null}
    </AchievementCard>
  )
}
```

- [ ] **Step 8: `PebblesCard.tsx`**

```tsx
"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { capitalize, shortInstantDay } from "@/lib/achievements/copy"
import { ladderStages } from "@/lib/achievements/ladder-model"
import { cardinalWords, ordinalWords } from "@/lib/i18n/number-words"
import { useFormatNumber, useLocale } from "@/lib/i18n"
import type { CountingLadder, CrossingPebble } from "@/lib/types"
import { AchievementCard } from "./AchievementCard"
import { MarginLadder } from "./MarginLadder"
import { PebbleSkyline } from "./PebbleSkyline"

type PebblesCardProps = {
  pebbles: CountingLadder
  glyphsCarved: CountingLadder | null
  glyphSales: CountingLadder | null
  firstPebble: CrossingPebble | null
  onOpen?: () => void
  corner?: ReactNode
}

/** The Pebbles card (#1308, D3): the skyline, then glyphs carved and glyph sales as two lines. */
export function PebblesCard({ pebbles, glyphsCarved, glyphSales, firstPebble, onOpen, corner }: PebblesCardProps) {
  const t = useTranslations("achievements.page")
  const { locale } = useLocale()
  const formatNumber = useFormatNumber()
  const stages = ladderStages(pebbles)
  const stood = stages.filter((s) => s.reached).length
  const line = t("pebblesLine", {
    count: pebbles.count,
    stood,
    stoodWords: capitalize(cardinalWords(stood, locale, "feminine"), locale),
    hasNext: pebbles.next ? "true" : "false",
    ordinal: ordinalWords(stood + 1, locale, "feminine"),
    next: pebbles.next?.threshold ?? 0,
  })
  const ladders = [
    { name: t("cards.pebbles"), ladder: pebbles },
    ...(glyphsCarved ? [{ name: t("glyphsCarved"), ladder: glyphsCarved }] : []),
    ...(glyphSales ? [{ name: t("glyphSales"), ladder: glyphSales }] : []),
  ]
  const said = ladders
    .map(({ name, ladder }) =>
      t("ladderAria", { name, count: ladder.count, stood: ladderStages(ladder).filter((s) => s.reached).length, rungs: ladder.ladder.length }),
    )
    .join(" ")
  return (
    <AchievementCard
      title={t("cards.pebbles")}
      hint={firstPebble ? t("sinceFirst", { date: shortInstantDay(firstPebble.happenedAt, locale) }) : null}
      ladder={<MarginLadder fills={stages.map((s) => s.fill)} />}
      label={onOpen ? t("open", { label: said }) : said}
      onOpen={onOpen}
      corner={corner}
    >
      <div className="mt-5 flex justify-center">
        <PebbleSkyline ladder={pebbles} variant="card" />
      </div>
      <p className="mt-2.5 text-center text-base leading-snug font-[380] italic">{line}</p>
      {ladders.length > 1 ? (
        <ul className="mt-3.5 border-t border-spine-rule">
          {ladders.slice(1).map(({ name, ladder }) => (
            <li key={name} className="grid min-h-11 grid-cols-[104px_1fr_34px] items-center gap-x-2 border-b border-spine-rule last:border-b-0">
              <span className="font-spine-sc text-[11px] font-semibold tracking-[.14em] whitespace-nowrap text-spine-ink-2">{name}</span>
              <span className="flex justify-center">
                <PebbleSkyline ladder={ladder} variant="line" />
              </span>
              <span className="text-right font-spine-office text-xl leading-none font-light tabular-nums">{formatNumber(ladder.count)}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </AchievementCard>
  )
}
```

- [ ] **Step 9: `YearCard.tsx`**

```tsx
"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { yearBars } from "@/lib/achievements/year-model"
import { useYearAria } from "@/lib/hooks/useYearAria"
import type { AchievementYear } from "@/lib/types"
import { AchievementCard } from "./AchievementCard"
import { MarginLadder } from "./MarginLadder"
import { NextStepLine } from "./NextStepLine"
import { YearDisc } from "./YearDisc"

type YearCardProps = {
  year: AchievementYear
  palettes: AchievementPalettes
  onOpen?: () => void
  corner?: ReactNode
}

/** One year's card (#1308, D12): the docked disc, and the current month's next step. */
export function YearCard({ year, palettes, onOpen, corner }: YearCardProps) {
  const t = useTranslations("achievements.page")
  const said = useYearAria(year)
  return (
    <AchievementCard
      title={t("cards.year")}
      heading={String(year.year)}
      hint={year.current ? t("yearHint") : null}
      ladder={<MarginLadder fills={yearBars(year)} />}
      label={onOpen ? t("open", { label: said }) : said}
      onOpen={onOpen}
      corner={corner}
    >
      <div className="mt-3 flex justify-center">
        <YearDisc year={year} palettes={palettes} size={280} />
      </div>
      {year.current && year.nextStep ? (
        <NextStepLine step={year.nextStep} className="mt-2.5 text-center text-base leading-snug font-[380] italic" />
      ) : null}
    </AchievementCard>
  )
}
```

- [ ] **Step 10: Typecheck and lint**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`. Expected: no errors.

- [ ] **Step 11: Commit**

```bash
git add apps/web/components/achievements/AchievementCard.tsx apps/web/components/achievements/DaysKeptHeader.tsx apps/web/components/achievements/NextStepLine.tsx apps/web/components/achievements/ExplorerCard.tsx apps/web/components/achievements/EmotionsCard.tsx apps/web/components/achievements/DomainsCard.tsx apps/web/components/achievements/PebblesCard.tsx apps/web/components/achievements/YearCard.tsx apps/web/lib/hooks/useYearAria.ts
git commit -m "feat(web): add the achievements card shell, the header and the five cards"
```

### Task 22: The page replaces the grid

**Files:**
- Create: `apps/web/components/achievements/AchievementsPageView.tsx`
- Create: `apps/web/app/achievements/layout.tsx`
- Modify: `apps/web/app/achievements/page.tsx`
- Delete: `apps/web/components/achievements/AchievementsView.tsx`, `apps/web/components/achievements/AchievementBadge.tsx`

- [ ] **Step 1: The view**

```tsx
"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import { PageHeader } from "@/components/layout/PageHeader"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { Button } from "@/components/ui/button"
import { achievementPalettes } from "@/lib/achievements/palette"
import { useAchievementsPage } from "@/lib/data/useAchievementsPage"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { DaysKeptHeader } from "./DaysKeptHeader"
import { DomainsCard } from "./DomainsCard"
import { EmotionsCard } from "./EmotionsCard"
import { ExplorerCard } from "./ExplorerCard"
import { PebblesCard } from "./PebblesCard"
import { YearCard } from "./YearCard"

/**
 * You → Achievements (#1308, spec 2026-10-29 D1): the days kept, then
 * Explorer, Emotions, Domains, Pebbles and one card per year. Every number,
 * order and flag is the server's; a section the payload lacks hides its card.
 * One column on a phone, two from `md`.
 */
export function AchievementsPageView() {
  const t = useTranslations("achievements")
  const tp = useTranslations("achievements.page")
  const tc = useTranslations("common")
  const { page, loading, failed, retry } = useAchievementsPage()
  const { paletteByEmotionId } = useEmotionPalettes()
  const palettes = useMemo(() => achievementPalettes(page?.emotions?.seats ?? [], paletteByEmotionId), [page, paletteByEmotionId])
  const firstPebble = page?.explorer?.firstPebble ?? null

  return (
    <div className="min-h-dvh bg-spine-ground font-spine text-spine-ink">
      <div className="mx-auto w-full max-w-md px-4 pt-4 pb-24 md:max-w-3xl md:px-6 md:pt-8">
        <PageHeader title={t("title")} backHref="/profile" />
        <p aria-live="polite" className="sr-only">
          {page && loading ? tp("refreshing") : ""}
        </p>
        {page ? (
          <>
            {page.since && page.daysKept !== null ? <DaysKeptHeader since={page.since} daysKept={page.daysKept} /> : null}
            <div className="grid gap-3.5 md:grid-cols-2 md:items-start">
              {page.explorer ? (
                <ExplorerCard explorer={page.explorer} emotions={page.emotions} domains={page.domains} palettes={palettes} />
              ) : null}
              {page.emotions ? <EmotionsCard emotions={page.emotions} firstPebble={firstPebble} palettes={palettes} /> : null}
              {page.domains ? <DomainsCard domains={page.domains} /> : null}
              {page.pebbles ? (
                <PebblesCard pebbles={page.pebbles} glyphsCarved={page.glyphsCarved} glyphSales={page.glyphSales} firstPebble={firstPebble} />
              ) : null}
              {page.years.map((year) => (
                <YearCard key={year.year} year={year} palettes={palettes} />
              ))}
            </div>
          </>
        ) : loading ? (
          <p aria-live="polite" className="text-sm text-spine-ink-2">
            {t("loading")}
          </p>
        ) : failed ? (
          <div role="alert" className="flex flex-col items-start gap-3">
            <p className="text-sm text-destructive">{t("error")}</p>
            <Button variant="outline" onClick={retry}>
              {tc("retry")}
            </Button>
          </div>
        ) : null}
      </div>
      {/* The seals' shared outlines and hatching, once for the page and its sheets. */}
      <StoneSprite />
    </div>
  )
}
```

- [ ] **Step 2: The route's fonts**

Create `apps/web/app/achievements/layout.tsx`, as `app/path/layout.tsx` does:

```tsx
// The achievements page (#1308) sets the Spine's small caps and lining
// figures with the real Ysabeau cuts, as the Path does; they load only here.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
// The root layout loads Ysabeau upright only; without the italic face the
// browser slants the upright glyphs instead of setting the real italic.
import "@fontsource-variable/ysabeau/wght-italic.css"

export default function AchievementsLayout({ children }: { children: React.ReactNode }) {
  return children
}
```

- [ ] **Step 3: The route**

Replace `apps/web/app/achievements/page.tsx` with:

```tsx
import { AchievementsPageView } from "@/components/achievements/AchievementsPageView"

export default function AchievementsPage() {
  return <AchievementsPageView />
}
```

- [ ] **Step 4: Delete the old grid**

```bash
git rm apps/web/components/achievements/AchievementsView.tsx apps/web/components/achievements/AchievementBadge.tsx
```

Nothing else imports either file. Check with: `grep -rn "AchievementsView\|AchievementBadge\b" apps/web/app apps/web/components apps/web/lib`. Expected: no output.

The `achievements.locked`, `achievements.unlockedOn` and `achievements.group.*` strings are now unused. Leave them, and note it in the PR as a follow-up for the maintainer to approve.

- [ ] **Step 5: Typecheck, lint, build**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web && npm run build --workspace=apps/web`

Expected: no errors, and the build lists `/achievements`.

- [ ] **Step 6: Commit**

```bash
git add apps/web/components/achievements/AchievementsPageView.tsx apps/web/app/achievements/layout.tsx apps/web/app/achievements/page.tsx
git commit -m "feat(web): replace the achievements grid with the approved page"
```

### Task 23: The cards on `/sandbox/design`

The design page renders every curated component from fixtures, under its theme switcher. These cards are the only way to see the newcomer's page without a fresh account.

**Files:**
- Create: `apps/web/components/sandbox/design/AchievementSpecimens.tsx`
- Modify: `apps/web/components/sandbox/design/ComponentsSection.tsx`
- Modify: `apps/web/lib/design/specimens.ts`

- [ ] **Step 1: Register the specimen**

In `apps/web/lib/design/specimens.ts`, replace:

```ts
  { id: "spine-week", name: "SpineWeek", source: "components/path/spine/SpineWeek.tsx", section: "components", m3: "bridged" },
```

with:

```ts
  { id: "spine-week", name: "SpineWeek", source: "components/path/spine/SpineWeek.tsx", section: "components", m3: "bridged" },
  { id: "achievement-cards", name: "Achievement cards", source: "components/achievements/AchievementCard.tsx", section: "components", m3: "bridged" },
```

- [ ] **Step 2: Write it**

```tsx
"use client"

import { DomainsCard } from "@/components/achievements/DomainsCard"
import { EmotionsCard } from "@/components/achievements/EmotionsCard"
import { ExplorerCard } from "@/components/achievements/ExplorerCard"
import { PebblesCard } from "@/components/achievements/PebblesCard"
import { YearCard } from "@/components/achievements/YearCard"
import { achievementPalettes } from "@/lib/achievements/palette"
import { achievementsPageFrom } from "@/lib/data/achievements-payload"
import { GET_ACHIEVEMENTS_NEWCOMER, GET_ACHIEVEMENTS_VETERAN } from "@/lib/data/__fixtures__/achievements-captured"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import type { AchievementsPage } from "@/lib/types"
import { Specimen } from "./Specimen"

const PAGES = [GET_ACHIEVEMENTS_VETERAN, GET_ACHIEVEMENTS_NEWCOMER].flatMap((json) => {
  const page = achievementsPageFrom(JSON.parse(json))
  return page ? [page] : []
})

/** One payload's cards, as the page stacks them. */
function Cards({ page }: { page: AchievementsPage }) {
  const { paletteByEmotionId } = useEmotionPalettes()
  const palettes = achievementPalettes(page.emotions?.seats ?? [], paletteByEmotionId)
  const firstPebble = page.explorer?.firstPebble ?? null
  return (
    <div className="flex w-[360px] max-w-full flex-col gap-3.5 rounded-xl bg-spine-ground p-3 font-spine text-spine-ink">
      {page.explorer ? <ExplorerCard explorer={page.explorer} emotions={page.emotions} domains={page.domains} palettes={palettes} /> : null}
      {page.emotions ? <EmotionsCard emotions={page.emotions} firstPebble={firstPebble} palettes={palettes} /> : null}
      {page.domains ? <DomainsCard domains={page.domains} /> : null}
      {page.pebbles ? (
        <PebblesCard pebbles={page.pebbles} glyphsCarved={page.glyphsCarved} glyphSales={page.glyphSales} firstPebble={firstPebble} />
      ) : null}
      {page.years.map((year) => (
        <YearCard key={year.year} year={year} palettes={palettes} />
      ))}
    </div>
  )
}

/**
 * The achievements cards (#1308) from the captured payloads: a veteran and a
 * newcomer. The seals draw from the `StoneSprite` that StoneSpecimens renders
 * on this page.
 */
export function AchievementSpecimens() {
  return (
    <Specimen id="achievement-cards" note="Rendered from lib/data/__fixtures__/achievements-captured.ts; the cards open no sheet here.">
      {PAGES.map((page, i) => (
        <Cards key={i} page={page} />
      ))}
    </Specimen>
  )
}
```

- [ ] **Step 3: Show it after the Spine week**

In `apps/web/components/sandbox/design/ComponentsSection.tsx`, add `import { AchievementSpecimens } from "./AchievementSpecimens"` above `import { DesignSection } from "./DesignSection"`. Then replace:

```tsx
      <SpineWeekSpecimen />
```

with:

```tsx
      <SpineWeekSpecimen />

      <AchievementSpecimens />
```

- [ ] **Step 4: Test, typecheck, lint**

Run: `npm run test --workspace=apps/web -- lib/design && npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`

Expected: PASS. `specimens.test.ts` checks that the new entry's source file exists.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/sandbox/design/AchievementSpecimens.tsx apps/web/components/sandbox/design/ComponentsSection.tsx apps/web/lib/design/specimens.ts
git commit -m "feat(web): show the achievement cards on the design page"
```

### Task 24: Verify Part 5 in a browser, then open its PR

- [ ] **Step 1: The fixtures, both themes**

Run `npm run dev --workspace=apps/web`. Open `http://localhost:3000/sandbox/design?mode=light#specimen-achievement-cards`, then `?mode=dark`. Compare with the approved final view:
- The wheel:
  - the first pebble prints in its hue at the centre;
  - the first five's marks sit on the inner ring;
  - the six firsts show three solid sizes and three prints;
  - 25 dots, then 14 domain seals and 4 dotted ghosts, the last entered (Event) ringed with its dot;
  - 23 emotion seals in their families' hues, 15 ghosts, and the sad and peaceful seats ringed.
- The rosace: 23 lit facets, each family deeper along its run on Paper. The labels read in their family's tone and the centre reads "23 / of 38".
- The band: 14 inked shards, then 4 dotted, with a dot under the 14th. The tier's sentence sits below.
- The skyline: five stages inked and the sixth a quarter filled; glyphs and sales as two lines with their counts.
- The year:
  - August is on the rim, ringed, and its spoke and numeral are in its hue;
  - March to July, September and October sit on the inner circle, climbing in their hue;
  - November is near the centre with its ink goal notch;
  - December is dotted.
- The newcomer: every seat is a ghost, and the sentences are the "none" tier's and "No stone stands yet".
- Every "not yet" stroke stays visible at Night.

- [ ] **Step 2: Contrast**

In devtools, check that every text colour on `--spine-stock` reaches 4.5:1 in both themes, the seven rosace family labels included. If one fails, set that label in `fill-spine-ink`, and record it in "As built" below.

- [ ] **Step 3: The real page, signed in**

Use a throwaway account, never the maintainer's, because the page's check and read write to the ledger. Open `/achievements` and check:
- the first visit shows "Loading…", then the cards;
- a reload shows the cached cards at once, without a blank frame, then updates them;
- with the network offline in devtools, a reload still shows the cached cards, and the console logs `[achievements] get_achievements failed`;
- after you sign out and back in, there is no cache: the page shows "Loading…" first.

- [ ] **Step 4: Widths**

At 320, 390, 768 and 1280 px:
- there is no horizontal scroll and the gutter is 16 px on a phone;
- the figures shrink with their column;
- from `md` on, the cards sit in two columns.

- [ ] **Step 5: Screen reader** (VoiceOver, Safari): each card reads its heading, then its assembled label (for example "Explorer. The first five, done. … Domains, 14 of 18. …"), and the figures are not read.

- [ ] **Step 6: Open the PR** with `gh stack`, titled `feat(web): the achievements page`. Its body:
  - opens with `Part of #1308`;
  - lists the four checks above;
  - names the unused grid strings as a follow-up;
  - carries the light and dark screenshots of the specimen.

---

# Part 6 — the five sheets (branch `feat/1308-achievements-web-sheets`)

Start the branch: `gh stack add feat/1308-achievements-web-sheets`.

Each card opens its sheet:
- the shadcn `Sheet`: a bottom sheet on a phone, a side panel from `md`;
- opening one marks its card seen (`mark_achievements_seen`), fire-and-forget;
- Explorer's Domains and Emotions rows, and a year's earlier years, push their own sheet on top.

A pushed sheet renders inside the popup of the sheet below it. Base UI then treats it as nested: Escape and an outside press close the top sheet only, and focus returns to the row that pushed it.

### Task 25: The sheet stack, the links and the reference names

**Files:**
- Create: `apps/web/lib/achievements/sheets.ts`
- Test: `apps/web/lib/achievements/sheets.test.ts`
- Create: `apps/web/lib/i18n/useReferenceNames.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from "vitest"
import { closeSheet, pebbleHref, pushSheet, sameSheet, subjectHref, type SheetKey } from "./sheets"

const explorer: SheetKey = { card: "explorer" }
const domains: SheetKey = { card: "domains" }

describe("the sheet stack", () => {
  it("pushes a sheet on top", () => {
    expect(pushSheet([explorer], domains)).toEqual([explorer, domains])
  })

  it("brings back a sheet already open instead of opening it twice", () => {
    expect(pushSheet([explorer, domains], explorer)).toEqual([explorer])
    expect(pushSheet([{ card: "year", year: 2026 }], { card: "year", year: 2025 })).toHaveLength(2)
  })

  it("closes a sheet and everything above it", () => {
    expect(closeSheet([explorer, domains], 1)).toEqual([explorer])
    expect(closeSheet([explorer, domains], 0)).toEqual([])
  })

  it("tells years apart", () => {
    expect(sameSheet({ card: "year", year: 2026 }, { card: "year", year: 2026 })).toBe(true)
    expect(sameSheet({ card: "year", year: 2026 }, { card: "year", year: 2025 })).toBe(false)
    expect(sameSheet(explorer, domains)).toBe(false)
  })
})

describe("links", () => {
  it("opens a pebble by its moment", () => {
    expect(pebbleHref({ momentId: "m1" })).toBe("/pebble/m1")
  })

  it("opens what each first-five task made, and nothing it cannot", () => {
    expect(subjectHref({ kind: "moment", id: "m", name: null })).toBe("/pebble/m")
    expect(subjectHref({ kind: "collection", id: "c", name: "Spring" })).toBe("/collections/c")
    expect(subjectHref({ kind: "soul", id: "s", name: "Mara" })).toBe("/souls/s")
    expect(subjectHref({ kind: "glyph", id: "g", name: "Wave" })).toBe("/glyphs/g")
    expect(subjectHref({ kind: "dream", id: "d", name: null })).toBeNull()
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/achievements/sheets.test.ts`

Expected: FAIL with `Failed to resolve import "./sheets"`.

- [ ] **Step 3: Write `sheets.ts`**

The routes are the existing ones: `/pebble/[id]` takes a moment id, as `SoulPebbleList` uses it; `/collections/[id]`, `/souls/[id]` and `/glyphs/[id]` take their own ids.

```ts
import type { AchievementCard, CrossingPebble, FirstFiveSubject } from "@/lib/types"

// The sheets' stack and links (#1308, spec §4 "Interaction"). A sheet opens
// on top of the page; Explorer's Domains and Emotions rows, and a year's
// earlier years, push their own sheet on top of it.

export type SheetKey = { card: Exclude<AchievementCard, "year"> } | { card: "year"; year: number }

export function sameSheet(a: SheetKey, b: SheetKey): boolean {
  return a.card === b.card && (a.card !== "year" || (b.card === "year" && a.year === b.year))
}

/** Pushes a sheet; one already open below comes back to the top instead of opening twice. */
export function pushSheet(stack: readonly SheetKey[], key: SheetKey): SheetKey[] {
  const at = stack.findIndex((open) => sameSheet(open, key))
  return at >= 0 ? stack.slice(0, at + 1) : [...stack, key]
}

/** Closes the sheet at `depth` and every sheet above it. */
export function closeSheet(stack: readonly SheetKey[], depth: number): SheetKey[] {
  return stack.slice(0, depth)
}

/** A crossing pebble's page: its moment, as every pebble route takes. */
export function pebbleHref(p: Pick<CrossingPebble, "momentId">): string {
  return `/pebble/${p.momentId}`
}

/** What a first-five task made (D6); null for a kind this build cannot open. */
export function subjectHref(s: FirstFiveSubject): string | null {
  switch (s.kind) {
    case "moment":
    case "pebble":
      return `/pebble/${s.id}`
    case "collection":
      return `/collections/${s.id}`
    case "soul":
      return `/souls/${s.id}`
    case "glyph":
      return `/glyphs/${s.id}`
    default:
      return null
  }
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/achievements/sheets.test.ts`

Expected: PASS, 6 tests.

- [ ] **Step 5: Write `useReferenceNames.ts`**

The payload names emotions and domains by id. This resolves them through the static configs and the catalog, with the same fallback contract as `useReferenceCatalog`.

```ts
"use client"

import { useCallback } from "react"
import { useTranslations } from "next-intl"
import { DOMAINS } from "@/lib/config/domains"
import { EMOTIONS } from "@/lib/config/emotions"

/**
 * Emotion and domain display names by id, for payloads that carry ids only
 * (#1308): the static config gives the slug, the catalog the localized name,
 * the config's name the fallback (useReferenceCatalog's contract). An id the
 * config does not know yet reads as null.
 */
export function useReferenceNames(): { emotion: (id: string) => string | null; domain: (id: string) => string | null } {
  // Untyped: slugs are runtime values, not part of the typed message tree.
  const t = useTranslations() as unknown as { (key: string): string; has(key: string): boolean }
  const emotion = useCallback(
    (id: string) => {
      const e = EMOTIONS.find((x) => x.id === id)
      if (!e) return null
      const key = `emotion.${e.slug}.name`
      return t.has(key) ? t(key) : e.name
    },
    [t],
  )
  const domain = useCallback(
    (id: string) => {
      const d = DOMAINS.find((x) => x.id === id)
      if (!d) return null
      const key = `domain.${d.slug}.name`
      return t.has(key) ? t(key) : d.name
    },
    [t],
  )
  return { emotion, domain }
}
```

- [ ] **Step 6: Commit**

```bash
git add apps/web/lib/achievements/sheets.ts apps/web/lib/achievements/sheets.test.ts apps/web/lib/i18n/useReferenceNames.ts
git commit -m "feat(web): stack the achievements sheets and link their rows"
```

### Task 26: The sheets' copy, shell and register

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`, `fr.json`
- Create: `apps/web/components/achievements/sheets/AchievementSheet.tsx`, `SectionHead.tsx`, `RowMark.tsx`, `RowDate.tsx`, `RegisterRow.tsx`, `NotYetNames.tsx`

- [ ] **Step 1: The copy**

In `en.json`, replace:

```json
      "nextStepUnreachable": "{monthCap} can no longer reach {needs, select, pebbles {the rim} other {the inner disc}} this month: a full week went by without {needs, select, pebbles {a pebble} other {a moment}}."
    }
```

with:

```json
      "nextStepUnreachable": "{monthCap} can no longer reach {needs, select, pebbles {the rim} other {the inner disc}} this month: a full week went by without {needs, select, pebbles {a pebble} other {a moment}}."
    },
    "sheet": {
      "new": "new since you last looked",
      "untitled": "Untitled moment",
      "removed": "Its pebble was removed",
      "notYet": "not yet",
      "ofWords": "{count} of {of}",
      "explorer": {
        "firstFive": "The first five",
        "sixFirsts": "The six firsts",
        "twentyFive": "25 pebbles",
        "closed": "closed",
        "theTwentyFifth": "The 25th",
        "outer": "Domains and emotions",
        "openDomains": "Domains, {count} of {of}. Open its sheet.",
        "openEmotions": "Emotions, {count} of {of}. Open its sheet.",
        "task": {
          "first-moment": "First moment",
          "first-pebble": "First pebble",
          "first-collection": "First collection",
          "first-soul": "First soul",
          "first-glyph": "First glyph"
        }
      },
      "emotions": {
        "notYet": "Not yet met"
      },
      "domains": {
        "hint": "the register",
        "entered": "Entered",
        "newestFirst": "newest first",
        "notYet": "Not yet in these pages",
        "no": "No. {no}"
      },
      "pebbles": {
        "kept": "{count, plural, one {# pebble kept.} other {# pebbles kept.}}",
        "milestones": "Milestones",
        "toGo": "{left, number} to go",
        "thenEvery": "then every {every, number}"
      },
      "year": {
        "months": "The months",
        "newestFirst": "newest first",
        "moments": "moments",
        "pebbles": "pebbles",
        "weeks": "weeks",
        "row": "{month}, {place}: {moments}, {pebbles}, {weeks}.",
        "place": {
          "centre": "at the centre",
          "inner": "on the inner disc",
          "rim": "on the rim"
        },
        "daysWithMoments": "{hasOf, select, true {{value} of {of} days with a moment} other {{value} days with a moment}}",
        "daysWithPebbles": "{hasOf, select, true {{value} of {of} with a pebble} other {{value} with a pebble}}",
        "weeksCell": "{none, select, true {no full week yet} other {{value} of {of} full weeks filled}}",
        "earlier": "Earlier years",
        "firstYear": "None yet. {year} is your first year here.",
        "openYear": "{year}. Open its sheet."
      }
    }
```

In `fr.json`, replace:

```json
      "nextStepUnreachable": "{monthCap} n’atteindra plus {needs, select, pebbles {le bord} other {le disque intérieur}} ce mois-ci : une semaine pleine est passée sans {needs, select, pebbles {galet} other {moment}}."
    }
```

with:

```json
      "nextStepUnreachable": "{monthCap} n’atteindra plus {needs, select, pebbles {le bord} other {le disque intérieur}} ce mois-ci : une semaine pleine est passée sans {needs, select, pebbles {galet} other {moment}}."
    },
    "sheet": {
      "new": "nouveau depuis ta dernière visite",
      "untitled": "Moment sans titre",
      "removed": "Son galet a été supprimé",
      "notYet": "pas encore",
      "ofWords": "{count} sur {of}",
      "explorer": {
        "firstFive": "Les cinq premiers pas",
        "sixFirsts": "Les six premières fois",
        "twentyFive": "25 galets",
        "closed": "bouclé",
        "theTwentyFifth": "Le 25ᵉ",
        "outer": "Domaines et émotions",
        "openDomains": "Domaines, {count} sur {of}. Ouvrir sa feuille.",
        "openEmotions": "Émotions, {count} sur {of}. Ouvrir sa feuille.",
        "task": {
          "first-moment": "Premier moment",
          "first-pebble": "Premier galet",
          "first-collection": "Première collection",
          "first-soul": "Première âme",
          "first-glyph": "Premier glyphe"
        }
      },
      "emotions": {
        "notYet": "Pas encore rencontrées"
      },
      "domains": {
        "hint": "le registre",
        "entered": "Entrés",
        "newestFirst": "du plus récent",
        "notYet": "Pas encore dans ces pages",
        "no": "N° {no}"
      },
      "pebbles": {
        "kept": "{count, plural, one {# galet gardé.} other {# galets gardés.}}",
        "milestones": "Paliers",
        "toGo": "encore {left, number}",
        "thenEvery": "puis tous les {every, number}"
      },
      "year": {
        "months": "Les mois",
        "newestFirst": "du plus récent",
        "moments": "moments",
        "pebbles": "galets",
        "weeks": "semaines",
        "row": "{month}, {place} : {moments}, {pebbles}, {weeks}.",
        "place": {
          "centre": "au centre",
          "inner": "sur le disque intérieur",
          "rim": "au bord"
        },
        "daysWithMoments": "{hasOf, select, true {{value} jours sur {of} avec un moment} other {{value} jours avec un moment}}",
        "daysWithPebbles": "{hasOf, select, true {{value} sur {of} avec un galet} other {{value} avec un galet}}",
        "weeksCell": "{none, select, true {pas encore de semaine pleine} other {{value} semaines pleines remplies sur {of}}}",
        "earlier": "Années précédentes",
        "firstYear": "Aucune pour l’instant. {year} est ta première année ici.",
        "openYear": "{year}. Ouvrir sa feuille."
      }
    }
```

The first-five task titles are keyed by slug. The sheet reads them untyped, so a slug this build does not know shows itself.

- [ ] **Step 2: `AchievementSheet.tsx`**

The close button is 44 px; the drag handle is `SheetContent`'s own.

```tsx
"use client"

import type { ReactNode } from "react"
import { X } from "lucide-react"
import { useTranslations } from "next-intl"
import { Sheet, SheetClose, SheetContent, SheetTitle } from "@/components/ui/sheet"

type AchievementSheetProps = {
  /** The band's heading: the sheet's accessible name. */
  title: string
  hint?: string | null
  onClose: () => void
  /** The sheet pushed on top of this one, nested so it closes first. */
  pushed?: ReactNode
  children: ReactNode
}

/**
 * The register every achievements sheet shares (#1308): a bottom sheet on a
 * phone, a side panel from `md` (shadcn `Sheet`), set on the card's stock with
 * a title band. A pushed sheet renders inside this one's popup, so Base UI
 * treats it as nested: Escape and an outside press close the top one first.
 */
export function AchievementSheet({ title, hint, onClose, pushed, children }: AchievementSheetProps) {
  const tc = useTranslations("common")
  return (
    <Sheet open onOpenChange={(open) => (open ? undefined : onClose())}>
      <SheetContent className="bg-spine-stock px-5 pb-9 font-spine text-spine-ink md:w-[440px] md:px-6">
        <header className="flex items-center justify-between gap-2 border-b border-spine-card-rule pb-2">
          <div className="flex min-w-0 items-baseline gap-3">
            <SheetTitle className="font-spine-sc text-xs font-semibold tracking-[.22em]">{title}</SheetTitle>
            {hint ? <em className="truncate text-sm text-spine-ink-2">{hint}</em> : null}
          </div>
          <SheetClose variant="ghost" size="icon" className="-mr-2 size-11" aria-label={tc("close")}>
            <X aria-hidden />
          </SheetClose>
        </header>
        {children}
        {pushed}
      </SheetContent>
    </Sheet>
  )
}
```

- [ ] **Step 3: `SectionHead.tsx`**

```tsx
import type { CSSProperties, ReactNode } from "react"
import { cn } from "@/lib/utils"

/** A dated section's head (#1308): small caps, an italic count on the right. */
export function SectionHead({ title, note, className, style }: { title: ReactNode; note?: ReactNode; className?: string; style?: CSSProperties }) {
  return (
    <div className={cn("mt-7 flex items-baseline justify-between border-b border-spine-card-rule pb-1.5", className)} style={style}>
      <h3 className="font-spine-sc text-[11.5px] font-semibold tracking-[.22em]">{title}</h3>
      {note ? <em className="text-[13.5px] text-spine-ink-2">{note}</em> : null}
    </div>
  )
}
```

- [ ] **Step 4: `RowMark.tsx`**

```tsx
import type { ReactNode } from "react"

/** A register row's mark (#1308): a 28-unit box centred on the origin, scaled. */
export function RowMark({ scale = 1, children }: { scale?: number; children: ReactNode }) {
  return (
    <svg aria-hidden focusable="false" viewBox="-14 -14 28 28" width={28} height={28} className="block overflow-visible">
      <g transform={`scale(${scale})`}>{children}</g>
    </svg>
  )
}
```

- [ ] **Step 5: `RowDate.tsx`**

The "new" dot is never colour alone: a screen reader hears "new since you last looked".

```tsx
"use client"

import { useTranslations } from "next-intl"

/** A row's date in the register's style, with the dot of "new since you last looked" (D10). */
export function RowDate({ text, isNew = false }: { text: string | null; isNew?: boolean }) {
  const t = useTranslations("achievements.sheet")
  return (
    <span className="flex items-center justify-end font-spine-office text-[12.5px] whitespace-nowrap text-spine-ink-2">
      {isNew ? (
        <>
          <span aria-hidden className="mr-1.5 size-[5px] rounded-full bg-spine-ink" />
          <span className="sr-only">{t("new")}, </span>
        </>
      ) : null}
      {text}
    </span>
  )
}
```

- [ ] **Step 6: `RegisterRow.tsx`**

```tsx
import type { ReactNode } from "react"
import Link from "next/link"
import { cn } from "@/lib/utils"

type RegisterRowProps = {
  mark: ReactNode
  name: ReactNode
  sub?: ReactNode
  /** An italic line under the name: the year's next step. */
  note?: ReactNode
  /** The row's right edge: a date, a chevron. */
  aside?: ReactNode
  /** The register's number ("No. 14"), in its own column. */
  number?: string
  /** Opens the row's pebble or subject. */
  href?: string | null
  /** Pushes a sheet. */
  onSelect?: () => void
  /** The action's spoken name, when the row's text is not enough. */
  label?: string
  /** The name set as a figure (the milestones), large or small. */
  numeric?: "large" | "small"
  muted?: boolean
  className?: string
}

/**
 * One register row (#1308): at least 44 px high, a link when it opens a
 * pebble, a button when it pushes a sheet, plain text otherwise.
 */
export function RegisterRow({ mark, name, sub, note, aside, number, href, onSelect, label, numeric, muted = false, className }: RegisterRowProps) {
  const body = (
    <>
      {number ? <span className="font-spine-office text-[12.5px] tracking-[.03em] whitespace-nowrap text-spine-ink-2">{number}</span> : null}
      <span className="flex justify-center">{mark}</span>
      <span className="min-w-0">
        <span
          className={cn(
            "block",
            numeric
              ? cn("font-spine-office leading-none font-light tabular-nums", numeric === "large" ? "text-[21px]" : "text-[17px]")
              : "font-spine-sc text-[13px] leading-tight font-semibold tracking-[.1em]",
            muted && "text-spine-ink-2",
          )}
        >
          {name}
        </span>
        {sub ? <span className="mt-0.5 block text-[14.5px] leading-snug font-[420] text-spine-ink-2">{sub}</span> : null}
        {note ? <span className="mt-1 block text-[14.5px] leading-snug italic">{note}</span> : null}
      </span>
      {aside ?? <span />}
    </>
  )
  const row = cn(
    "grid min-h-[50px] w-full items-center border-b border-spine-rule py-1.5 text-left text-spine-ink",
    number ? "grid-cols-[42px_30px_minmax(0,1fr)_auto] gap-x-2" : "grid-cols-[34px_minmax(0,1fr)_auto] gap-x-2.5",
    className,
  )
  const action = "cursor-pointer outline-offset-2 focus-visible:outline-2 focus-visible:outline-spine-ink"
  return (
    <li>
      {href ? (
        <Link href={href} aria-label={label} className={cn(row, action)}>
          {body}
        </Link>
      ) : onSelect ? (
        <button type="button" onClick={onSelect} aria-label={label} className={cn(row, action)}>
          {body}
        </button>
      ) : (
        <div className={row}>{body}</div>
      )}
    </li>
  )
}
```

- [ ] **Step 7: `NotYetNames.tsx`**

```tsx
import { GhostSeat } from "../GhostSeat"

/** What is not yet (#1308): dotted names, set quietly; the label names the list. */
export function NotYetNames({ label, names }: { label: string; names: string[] }) {
  if (names.length === 0) return null
  return (
    <ul aria-label={label} className="flex flex-wrap gap-x-3.5 gap-y-1.5 border-b border-spine-rule pt-2.5 pb-3">
      {names.map((name, i) => (
        <li key={`${name}-${i}`} className="inline-flex items-center gap-1.5 text-[15px] text-spine-ink-2">
          <svg aria-hidden focusable="false" viewBox="-5 -5 10 10" width={10} height={10} className="block overflow-visible">
            <GhostSeat x={0} y={0} r={3.6} />
          </svg>
          {name}
        </li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 8: Typecheck and lint**

Run: `rm -f apps/web/tsconfig.tsbuildinfo && npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`. Expected: no errors.

- [ ] **Step 9: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json apps/web/components/achievements/sheets/AchievementSheet.tsx apps/web/components/achievements/sheets/SectionHead.tsx apps/web/components/achievements/sheets/RowMark.tsx apps/web/components/achievements/sheets/RowDate.tsx apps/web/components/achievements/sheets/RegisterRow.tsx apps/web/components/achievements/sheets/NotYetNames.tsx
git commit -m "feat(web): add the achievements sheet shell and its register rows"
```

### Task 27: The five sheets

**Files:**
- Create: `apps/web/components/achievements/sheets/ExplorerSheet.tsx`, `EmotionsSheet.tsx`, `DomainsSheet.tsx`, `PebblesSheet.tsx`, `YearSheet.tsx`

- [ ] **Step 1: `ExplorerSheet.tsx`**: the board's `sheetExplorer`.

```tsx
"use client"

import type { ReactNode } from "react"
import { ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"
import { shortDay, shortInstantDay } from "@/lib/achievements/copy"
import { explorerSummary } from "@/lib/achievements/explorer-model"
import { polar } from "@/lib/achievements/geometry"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { SIX_FIRST_SEALS } from "@/lib/achievements/seals"
import { pebbleHref, subjectHref } from "@/lib/achievements/sheets"
import { cardinalWords } from "@/lib/i18n/number-words"
import { useLocale } from "@/lib/i18n"
import type { AchievementsDomains, AchievementsEmotions, AchievementsExplorer, CrossingPebble } from "@/lib/types"
import { AchievementSeal } from "../AchievementSeal"
import { EmotionRosace } from "../EmotionRosace"
import { ExplorerWheel } from "../ExplorerWheel"
import { FirstFiveMark } from "../FirstFiveMark"
import { GhostSeat } from "../GhostSeat"
import { AchievementSheet } from "./AchievementSheet"
import { RegisterRow } from "./RegisterRow"
import { RowDate } from "./RowDate"
import { RowMark } from "./RowMark"
import { SectionHead } from "./SectionHead"

type ExplorerSheetProps = {
  explorer: AchievementsExplorer
  emotions: AchievementsEmotions | null
  domains: AchievementsDomains | null
  palettes: AchievementPalettes
  onClose: () => void
  /** Pushes the Domains or the Emotions sheet on top of this one. */
  onPush: (card: "domains" | "emotions") => void
  pushed?: ReactNode
}

/**
 * Explorer's sheet (#1308): the wheel, then one short section per ring, inside
 * out: the first five, the six firsts, the 25th pebble, and a row each that
 * pushes the Domains and the Emotions sheets.
 */
export function ExplorerSheet({ explorer, emotions, domains, palettes, onClose, onPush, pushed }: ExplorerSheetProps) {
  const t = useTranslations("achievements.sheet")
  const tp = useTranslations("achievements.page")
  const tk = useTranslations("achievements.stoneKind")
  // Untyped: first-five slugs are runtime values; an unknown one shows itself.
  const tt = useTranslations("achievements.sheet.explorer") as unknown as { (key: string): string; has(key: string): boolean }
  const { locale } = useLocale()
  const summary = explorerSummary(explorer, emotions, domains)
  const words = (count: number, of: number) => t("ofWords", { count: cardinalWords(count, locale), of: cardinalWords(of, locale) })
  const pebbleName = (p: CrossingPebble | null) => (p ? (p.name ?? t("untitled")) : t("removed"))
  const ghost = (
    <RowMark>
      <GhostSeat x={0} y={0} r={4} />
    </RowMark>
  )
  const twentyFive = explorer.twentyFive
  return (
    <AchievementSheet title={tp("cards.explorer")} hint={tp("explorerHint")} onClose={onClose} pushed={pushed}>
      <div className="mt-4 flex justify-center">
        <ExplorerWheel explorer={explorer} emotions={emotions} domains={domains} palettes={palettes} size={340} />
      </div>

      <SectionHead title={t("explorer.firstFive")} note={words(summary.first_five.count, summary.first_five.of)} />
      <ul>
        {explorer.firstFive.map((f) => {
          const key = `task.${f.slug}`
          const name = tt.has(key) ? tt(key) : f.slug
          return f.done ? (
            <RegisterRow
              key={f.slug}
              mark={
                <RowMark scale={1.7}>
                  <FirstFiveMark slug={f.slug} x={0} y={0} />
                </RowMark>
              }
              name={name}
              sub={f.subject?.name ?? null}
              aside={<RowDate text={f.on ? shortDay(f.on, locale) : null} isNew={f.isNew} />}
              href={f.subject ? subjectHref(f.subject) : null}
            />
          ) : (
            <RegisterRow key={f.slug} mark={ghost} name={name} muted aside={<RowDate text={t("notYet")} />} />
          )
        })}
      </ul>

      <SectionHead title={t("explorer.sixFirsts")} note={words(summary.six_firsts.count, summary.six_firsts.of)} />
      <ul>
        {explorer.sixFirsts.map((s) => {
          const seal = SIX_FIRST_SEALS[s.kind]
          return s.done ? (
            <RegisterRow
              key={s.kind}
              mark={
                <RowMark scale={1.55}>
                  <AchievementSeal x={0} y={0} width={seal.width} size={seal.size} polarity={seal.polarity} print={seal.print} />
                </RowMark>
              }
              name={tk(s.kind)}
              sub={pebbleName(s.pebble)}
              aside={<RowDate text={s.pebble ? shortInstantDay(s.pebble.happenedAt, locale) : null} isNew={s.isNew} />}
              href={s.pebble ? pebbleHref(s.pebble) : null}
            />
          ) : (
            <RegisterRow key={s.kind} mark={ghost} name={tk(s.kind)} muted aside={<RowDate text={t("notYet")} />} />
          )
        })}
      </ul>

      {twentyFive ? (
        <>
          <SectionHead
            title={t("explorer.twentyFive")}
            note={twentyFive.done ? t("explorer.closed") : t("ofWords", { count: String(twentyFive.count), of: "25" })}
          />
          <ul>
            <RegisterRow
              mark={
                <RowMark>
                  <circle r={11} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} />
                  {Array.from({ length: 25 }, (_, i) => {
                    const [x, y] = polar(11, (i + 0.5) * 14.4)
                    return <circle key={i} cx={x} cy={y} r={1.1} fill={i < twentyFive.count ? "var(--spine-ink)" : "var(--spine-ink-3)"} />
                  })}
                </RowMark>
              }
              name={t("explorer.theTwentyFifth")}
              muted={!twentyFive.done}
              sub={twentyFive.done ? pebbleName(twentyFive.pebble) : null}
              aside={
                <RowDate
                  text={twentyFive.done ? (twentyFive.pebble ? shortInstantDay(twentyFive.pebble.happenedAt, locale) : null) : t("notYet")}
                  isNew={twentyFive.isNew}
                />
              }
              href={twentyFive.done && twentyFive.pebble ? pebbleHref(twentyFive.pebble) : null}
            />
          </ul>
        </>
      ) : null}

      {domains || emotions ? (
        <>
          <SectionHead title={t("explorer.outer")} />
          <ul>
            {domains ? (
              <RegisterRow
                mark={
                  <RowMark scale={1.6}>
                    <AchievementSeal x={0} y={0} width={9} size="small" polarity="neutral" print="solid" />
                  </RowMark>
                }
                name={tp("cards.domains")}
                sub={t("ofWords", { count: String(domains.count), of: String(domains.of) })}
                aside={<ChevronRight aria-hidden className="size-5 text-spine-ink-2" />}
                onSelect={() => onPush("domains")}
                label={t("explorer.openDomains", { count: domains.count, of: domains.of })}
              />
            ) : null}
            {emotions ? (
              <RegisterRow
                mark={
                  <span className="block w-7">
                    <EmotionRosace emotions={emotions} palettes={palettes} size={28} ticks={false} />
                  </span>
                }
                name={tp("cards.emotions")}
                sub={t("ofWords", { count: String(emotions.count), of: String(emotions.of) })}
                aside={<ChevronRight aria-hidden className="size-5 text-spine-ink-2" />}
                onSelect={() => onPush("emotions")}
                label={t("explorer.openEmotions", { count: emotions.count, of: emotions.of })}
              />
            ) : null}
          </ul>
        </>
      ) : null}
    </AchievementSheet>
  )
}
```

- [ ] **Step 2: `EmotionsSheet.tsx`**: the board's `sheetEmotions`. Within a family the rows keep the served seat order, not the board's order by date (contract gap 2).

```tsx
"use client"

import type { CSSProperties, ReactNode } from "react"
import { useTranslations } from "next-intl"
import { shortInstantDay } from "@/lib/achievements/copy"
import { emotionRegister } from "@/lib/achievements/emotions-model"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { pebbleSeal } from "@/lib/achievements/seals"
import { pebbleHref } from "@/lib/achievements/sheets"
import { stonePaletteVars } from "@/lib/config/stone-tones"
import { useLocale } from "@/lib/i18n"
import { useReferenceNames } from "@/lib/i18n/useReferenceNames"
import type { AchievementsEmotions, CrossingPebble } from "@/lib/types"
import { cn } from "@/lib/utils"
import { AchievementSeal } from "../AchievementSeal"
import { EmotionRosace } from "../EmotionRosace"
import { AchievementSheet } from "./AchievementSheet"
import { NotYetNames } from "./NotYetNames"
import { RegisterRow } from "./RegisterRow"
import { RowDate } from "./RowDate"
import { RowMark } from "./RowMark"
import { SectionHead } from "./SectionHead"

type EmotionsSheetProps = {
  emotions: AchievementsEmotions
  firstPebble: CrossingPebble | null
  palettes: AchievementPalettes
  onClose: () => void
  pushed?: ReactNode
}

/**
 * The Emotions sheet (#1308): the rosace large, then each family in the
 * wheel's order: what was met, with the seal of the pebble that brought it,
 * then what was not, set quietly. Rows keep the served seat order.
 */
export function EmotionsSheet({ emotions, firstPebble, palettes, onClose, pushed }: EmotionsSheetProps) {
  const t = useTranslations("achievements.sheet")
  const tp = useTranslations("achievements.page")
  const tc = useTranslations("emotionCategory")
  const tcommon = useTranslations("common")
  const { locale } = useLocale()
  const names = useReferenceNames()
  return (
    <AchievementSheet
      title={tp("cards.emotions")}
      hint={firstPebble ? tp("sinceFirst", { date: shortInstantDay(firstPebble.happenedAt, locale) }) : null}
      onClose={onClose}
      pushed={pushed}
    >
      <div className="mt-4 flex justify-center">
        <EmotionRosace emotions={emotions} palettes={palettes} size={330} labelled />
      </div>
      {emotionRegister(emotions.seats).map(({ family, met, notYet }, i) => {
        const palette = palettes.family(family)
        const title = family ? tc(`${family}.name`) : tcommon("unknown")
        return (
          <section key={`${family ?? "ink"}-${i}`} className="mt-6">
            <SectionHead
              className={cn("mt-0", palette && "spine-spot")}
              style={palette ? (stonePaletteVars(palette) as CSSProperties) : undefined}
              title={<span className={palette ? "text-(--sh) dark:text-(--s)" : undefined}>{title}</span>}
              note={t("ofWords", { count: String(met.length), of: String(met.length + notYet.length) })}
            />
            <ul>
              {met.map((seat) => {
                const p = seat.pebble
                const seal = p ? pebbleSeal(p) : ({ size: "medium", polarity: "neutral", print: "tint" } as const)
                return (
                  <RegisterRow
                    key={seat.emotionId}
                    mark={
                      <RowMark>
                        <AchievementSeal x={0} y={0} width={9 + (p?.intensity ?? 2) * 2.4} {...seal} palette={palette} />
                      </RowMark>
                    }
                    name={names.emotion(seat.emotionId) ?? tcommon("unknown")}
                    sub={p ? (p.name ?? t("untitled")) : t("removed")}
                    aside={<RowDate text={p ? shortInstantDay(p.happenedAt, locale) : null} isNew={seat.isNew} />}
                    href={p ? pebbleHref(p) : null}
                  />
                )
              })}
            </ul>
            <NotYetNames label={t("emotions.notYet")} names={notYet.map((s) => names.emotion(s.emotionId) ?? tcommon("unknown"))} />
          </section>
        )
      })}
    </AchievementSheet>
  )
}
```

- [ ] **Step 3: `DomainsSheet.tsx`**: the board's `sheetDomains`.

```tsx
"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { shortInstantDay } from "@/lib/achievements/copy"
import { domainRows } from "@/lib/achievements/domains-model"
import { pebbleHref } from "@/lib/achievements/sheets"
import { useLocale } from "@/lib/i18n"
import { useReferenceNames } from "@/lib/i18n/useReferenceNames"
import type { AchievementsDomains } from "@/lib/types"
import { AchievementSeal } from "../AchievementSeal"
import { DomainBand } from "../DomainBand"
import { AchievementSheet } from "./AchievementSheet"
import { NotYetNames } from "./NotYetNames"
import { RegisterRow } from "./RegisterRow"
import { RowDate } from "./RowDate"
import { RowMark } from "./RowMark"
import { SectionHead } from "./SectionHead"

/**
 * The Domains sheet (#1308): the band, what its coverage means, the register
 * numbered by arrival and newest first, then the domains not yet entered.
 */
export function DomainsSheet({ domains, onClose, pushed }: { domains: AchievementsDomains; onClose: () => void; pushed?: ReactNode }) {
  const t = useTranslations("achievements.sheet")
  const tp = useTranslations("achievements.page")
  const tcommon = useTranslations("common")
  const { locale } = useLocale()
  const names = useReferenceNames()
  return (
    <AchievementSheet title={tp("cards.domains")} hint={t("domains.hint")} onClose={onClose} pushed={pushed}>
      <div className="mt-4 flex justify-center">
        <DomainBand domains={domains} width={350} />
      </div>
      {domains.coverage ? <p className="mt-3.5 text-[16.5px] leading-snug font-[380] italic">{tp(`coverage.${domains.coverage}`)}</p> : null}
      {domains.entered.length > 0 ? (
        <>
          <SectionHead title={t("domains.entered")} note={t("domains.newestFirst")} />
          <ul>
            {domainRows(domains).map(({ no, entry }) => (
              <RegisterRow
                key={entry.domainId}
                number={t("domains.no", { no: String(no).padStart(2, "0") })}
                mark={
                  <RowMark scale={1.9}>
                    <AchievementSeal x={0} y={0} width={9} size="small" polarity="neutral" print="solid" />
                  </RowMark>
                }
                name={names.domain(entry.domainId) ?? tcommon("unknown")}
                sub={entry.pebble ? (entry.pebble.name ?? t("untitled")) : t("removed")}
                aside={<RowDate text={entry.pebble ? shortInstantDay(entry.pebble.happenedAt, locale) : null} isNew={entry.isNew} />}
                href={entry.pebble ? pebbleHref(entry.pebble) : null}
              />
            ))}
          </ul>
        </>
      ) : null}
      {domains.notYet.length > 0 ? (
        <>
          <SectionHead title={t("domains.notYet")} />
          <NotYetNames label={t("domains.notYet")} names={domains.notYet.map((id) => names.domain(id) ?? tcommon("unknown"))} />
        </>
      ) : null}
    </AchievementSheet>
  )
}
```

- [ ] **Step 4: `PebblesSheet.tsx`**: the board's `sheetPebbles`. The next rung shows what is left, never a date.

```tsx
"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { shortDay, shortInstantDay } from "@/lib/achievements/copy"
import { ladderStages, milestoneRows } from "@/lib/achievements/ladder-model"
import { pebbleHref } from "@/lib/achievements/sheets"
import { cardinalWords } from "@/lib/i18n/number-words"
import { useFormatNumber, useLocale } from "@/lib/i18n"
import type { CountingLadder, CrossingPebble } from "@/lib/types"
import { PebbleSkyline } from "../PebbleSkyline"
import { SkylineStage } from "../SkylineStage"
import { AchievementSheet } from "./AchievementSheet"
import { RegisterRow } from "./RegisterRow"
import { RowDate } from "./RowDate"
import { SectionHead } from "./SectionHead"

type PebblesSheetProps = {
  pebbles: CountingLadder
  glyphsCarved: CountingLadder | null
  glyphSales: CountingLadder | null
  firstPebble: CrossingPebble | null
  onClose: () => void
  pushed?: ReactNode
}

/** One stack's milestones: the levels reached, the next with what is left, the empty rungs. */
function Milestones({ ladder, small }: { ladder: CountingLadder; small: boolean }) {
  const t = useTranslations("achievements.sheet")
  const { locale } = useLocale()
  const formatNumber = useFormatNumber()
  const row = small ? "min-h-11" : undefined
  const numeric = small ? "small" : "large"
  const scale = small ? 0.8 : 1
  return (
    <ul>
      {milestoneRows(ladder).map((r) => {
        if (r.kind === "reached") {
          return (
            <RegisterRow
              key={`r${r.level.threshold}`}
              className={row}
              numeric={numeric}
              mark={<SkylineStage stage={r.stage} fill={1} scale={scale} />}
              name={formatNumber(r.level.threshold)}
              sub={r.level.pebble ? (r.level.pebble.name ?? t("untitled")) : null}
              aside={<RowDate text={r.level.on ? shortDay(r.level.on, locale) : null} isNew={r.level.isNew} />}
              href={r.level.pebble ? pebbleHref(r.level.pebble) : null}
            />
          )
        }
        if (r.kind === "next") {
          return (
            <RegisterRow
              key={`n${r.next.threshold}`}
              className={row}
              numeric={numeric}
              muted
              mark={<SkylineStage stage={r.stage} fill={r.next.progress} scale={scale} />}
              name={formatNumber(r.next.threshold)}
              sub={<em>{t("pebbles.toGo", { left: r.next.left })}</em>}
            />
          )
        }
        return (
          <RegisterRow
            key={`a${r.threshold}`}
            className={row}
            numeric={numeric}
            muted
            mark={<SkylineStage stage={r.stage} fill={0} scale={scale} />}
            name={formatNumber(r.threshold)}
          />
        )
      })}
      {ladder.thenEvery ? (
        <li className="flex min-h-11 items-center border-b border-spine-rule pl-11 text-sm text-spine-ink-2 italic">
          {t("pebbles.thenEvery", { every: ladder.thenEvery })}
        </li>
      ) : null}
    </ul>
  )
}

/**
 * The Pebbles sheet (#1308): the skyline, the milestones reached each with
 * its pebble and date, the next with its fill and what is left (never a
 * date); glyphs carved and glyph sales follow in the same grammar, smaller.
 */
export function PebblesSheet({ pebbles, glyphsCarved, glyphSales, firstPebble, onClose, pushed }: PebblesSheetProps) {
  const t = useTranslations("achievements.sheet")
  const tp = useTranslations("achievements.page")
  const { locale } = useLocale()
  const formatNumber = useFormatNumber()
  const reached = (l: CountingLadder) => ladderStages(l).filter((s) => s.reached).length
  const words = (l: CountingLadder) => t("ofWords", { count: cardinalWords(reached(l), locale), of: cardinalWords(l.ladder.length, locale) })
  return (
    <AchievementSheet
      title={tp("cards.pebbles")}
      hint={firstPebble ? tp("sinceFirst", { date: shortInstantDay(firstPebble.happenedAt, locale) }) : null}
      onClose={onClose}
      pushed={pushed}
    >
      <div className="mt-6 flex justify-center">
        <PebbleSkyline ladder={pebbles} variant="sheet" />
      </div>
      <p className="mt-3.5 text-center text-[16.5px] leading-snug font-[380] italic">{t("pebbles.kept", { count: pebbles.count })}</p>
      <SectionHead title={t("pebbles.milestones")} note={words(pebbles)} />
      <Milestones ladder={pebbles} small={false} />
      {glyphsCarved ? (
        <>
          <SectionHead title={tp("glyphsCarved")} note={formatNumber(glyphsCarved.count)} />
          <Milestones ladder={glyphsCarved} small />
        </>
      ) : null}
      {glyphSales ? (
        <>
          <SectionHead title={tp("glyphSales")} note={formatNumber(glyphSales.count)} />
          <Milestones ladder={glyphSales} small />
        </>
      ) : null}
    </AchievementSheet>
  )
}
```

- [ ] **Step 5: `YearSheet.tsx`**: the board's `sheetYear`.

The table is drawn for sight and read as one sentence per month, because a grid of gauges reads badly in a screen reader. Its rows are not interactive, as on the board.

```tsx
"use client"

import type { CSSProperties, ReactNode } from "react"
import { useTranslations } from "next-intl"
import { monthName } from "@/lib/achievements/copy"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { yearRows, type CountCell as Cell } from "@/lib/achievements/year-model"
import { stonePaletteVars } from "@/lib/config/stone-tones"
import { useLocale } from "@/lib/i18n"
import { useYearAria } from "@/lib/hooks/useYearAria"
import type { AchievementYear } from "@/lib/types"
import { cn } from "@/lib/utils"
import { AchievementSeal } from "../AchievementSeal"
import { CountCell } from "../CountCell"
import { NextStepLine } from "../NextStepLine"
import { YearDisc } from "../YearDisc"
import { AchievementSheet } from "./AchievementSheet"
import { RegisterRow } from "./RegisterRow"
import { RowMark } from "./RowMark"
import { SectionHead } from "./SectionHead"

const PLACES = ["centre", "inner", "rim"] as const

type YearSheetProps = {
  year: AchievementYear
  /** The served years after this one, newest first: its "earlier years". */
  earlier: number[]
  palettes: AchievementPalettes
  onClose: () => void
  onPushYear: (year: number) => void
  pushed?: ReactNode
}

/**
 * A year's sheet (#1308, D12): the docked disc, then the served months newest
 * first, each with its counter at its place and three gauges (days with a
 * moment, days with a pebble, full weeks filled); the current month adds its
 * next step. Earlier years push their own sheet.
 */
export function YearSheet({ year, earlier, palettes, onClose, onPushYear, pushed }: YearSheetProps) {
  const t = useTranslations("achievements.sheet")
  const tp = useTranslations("achievements.page")
  const { locale } = useLocale()
  const aria = useYearAria(year)
  const days = (key: "daysWithMoments" | "daysWithPebbles", cell: Cell) =>
    t(`year.${key}`, { hasOf: cell.of === null ? "false" : "true", value: cell.value, of: cell.of ?? 0 })
  return (
    <AchievementSheet title={String(year.year)} hint={year.current ? tp("yearHint") : null} onClose={onClose} pushed={pushed}>
      <p className="sr-only">{aria}</p>
      <div className="mt-4 flex justify-center">
        <YearDisc year={year} palettes={palettes} size={330} />
      </div>
      {year.listed.length > 0 ? (
        <>
          <SectionHead title={t("year.months")} note={t("year.newestFirst")} />
          <div aria-hidden className="grid grid-cols-[30px_1fr_58px_58px_58px] items-center gap-x-1.5 border-b border-spine-rule pt-2 pb-1">
            <span />
            <span />
            {(["moments", "pebbles", "weeks"] as const).map((k) => (
              <span key={k} className="text-center font-spine-sc text-[10.5px] font-semibold tracking-[.14em] text-spine-ink-2">
                {t(`year.${k}`)}
              </span>
            ))}
          </div>
          <ul>
            {yearRows(year).map(({ month, moments, pebbles, weeks, nextStep }) => {
              const palette = palettes.family(month.family)
              const name = monthName(month.month, locale)
              const said = t("year.row", {
                month: name,
                place: t(`year.place.${PLACES[month.level]}`),
                moments: days("daysWithMoments", moments),
                pebbles: days("daysWithPebbles", pebbles),
                weeks: t("year.weeksCell", { none: weeks.none ? "true" : "false", value: weeks.value, of: weeks.of ?? 0 }),
              })
              // A month with no pebble is dimmed, as on the disc.
              const dim = month.family ? undefined : "opacity-55"
              return (
                <li key={month.month} className="border-b border-spine-rule">
                  <p className="sr-only">{said}</p>
                  <div aria-hidden className="grid min-h-12 grid-cols-[30px_1fr_58px_58px_58px] items-center gap-x-1.5 py-1.5">
                    <RowMark>
                      {palette ? (
                        <AchievementSeal x={0} y={0} width={14} size="medium" polarity="neutral" print="solid" palette={palette} />
                      ) : (
                        <AchievementSeal x={0} y={0} width={14} size="medium" polarity="neutral" print="blank" muted />
                      )}
                      {month.level === 2 && palette ? <circle r={14 * 0.78} fill="none" stroke="var(--spine-ink)" strokeWidth={0.8} /> : null}
                    </RowMark>
                    <span className={cn("font-spine-sc text-[13px] font-semibold tracking-[.1em] whitespace-nowrap", dim)}>
                      {name}
                    </span>
                    <span className={dim}>
                      <CountCell cell={moments} />
                    </span>
                    <span className={cn(palette && "spine-spot", dim)} style={palette ? (stonePaletteVars(palette) as CSSProperties) : undefined}>
                      <CountCell cell={pebbles} hue={palette ? "var(--spine-spot)" : undefined} />
                    </span>
                    <span className={dim}>
                      <CountCell cell={weeks} showOf />
                    </span>
                  </div>
                  {nextStep ? <NextStepLine step={nextStep} className="pb-2 pl-9 text-[14.5px] leading-snug italic" /> : null}
                </li>
              )
            })}
          </ul>
        </>
      ) : null}
      <SectionHead title={t("year.earlier")} />
      {earlier.length > 0 ? (
        <ul>
          {earlier.map((y) => (
            <RegisterRow key={y} mark={<span />} name={String(y)} numeric="large" onSelect={() => onPushYear(y)} label={t("year.openYear", { year: String(y) })} />
          ))}
        </ul>
      ) : (
        <p className="mt-2.5 text-[14.5px] text-spine-ink-2 italic">{t("year.firstYear", { year: String(year.year) })}</p>
      )}
    </AchievementSheet>
  )
}
```

- [ ] **Step 6: Typecheck and lint**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`. Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add apps/web/components/achievements/sheets/ExplorerSheet.tsx apps/web/components/achievements/sheets/EmotionsSheet.tsx apps/web/components/achievements/sheets/DomainsSheet.tsx apps/web/components/achievements/sheets/PebblesSheet.tsx apps/web/components/achievements/sheets/YearSheet.tsx
git commit -m "feat(web): add the explorer, emotions, domains, pebbles and year sheets"
```

### Task 28: The cards open their sheets

**Files:**
- Modify: `apps/web/components/achievements/AchievementsPageView.tsx`

- [ ] **Step 1: Replace the view**

The cards gain `onOpen`, the view keeps the stack, and opening a sheet marks its card seen.

```tsx
"use client"

import { useCallback, useMemo, useState, type ReactNode } from "react"
import { useTranslations } from "next-intl"
import { PageHeader } from "@/components/layout/PageHeader"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { Button } from "@/components/ui/button"
import { achievementPalettes } from "@/lib/achievements/palette"
import { closeSheet, pushSheet, type SheetKey } from "@/lib/achievements/sheets"
import { useAchievementsPage } from "@/lib/data/useAchievementsPage"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { DaysKeptHeader } from "./DaysKeptHeader"
import { DomainsCard } from "./DomainsCard"
import { EmotionsCard } from "./EmotionsCard"
import { ExplorerCard } from "./ExplorerCard"
import { PebblesCard } from "./PebblesCard"
import { YearCard } from "./YearCard"
import { DomainsSheet } from "./sheets/DomainsSheet"
import { EmotionsSheet } from "./sheets/EmotionsSheet"
import { ExplorerSheet } from "./sheets/ExplorerSheet"
import { PebblesSheet } from "./sheets/PebblesSheet"
import { YearSheet } from "./sheets/YearSheet"

/**
 * You → Achievements (#1308, spec 2026-10-29 D1): the days kept, then
 * Explorer, Emotions, Domains, Pebbles and one card per year. Every number,
 * order and flag is the server's; a section the payload lacks hides its card.
 * Each card opens its sheet, which marks the card seen (D10); sheets read the
 * same payload, so opening one costs no read (D14). One column on a phone,
 * two from `md`.
 */
export function AchievementsPageView() {
  const t = useTranslations("achievements")
  const tp = useTranslations("achievements.page")
  const tc = useTranslations("common")
  const { page, loading, failed, retry, markSeen } = useAchievementsPage()
  const { paletteByEmotionId } = useEmotionPalettes()
  const palettes = useMemo(() => achievementPalettes(page?.emotions?.seats ?? [], paletteByEmotionId), [page, paletteByEmotionId])
  const firstPebble = page?.explorer?.firstPebble ?? null
  const [stack, setStack] = useState<SheetKey[]>([])

  const open = useCallback(
    (key: SheetKey) => {
      setStack((s) => pushSheet(s, key))
      markSeen(key.card)
    },
    [markSeen],
  )

  // A pushed sheet renders inside the one below it, so it closes first.
  function sheetAt(depth: number): ReactNode {
    const key = stack[depth]
    if (!page || !key) return null
    const onClose = () => setStack((s) => closeSheet(s, depth))
    const pushed = sheetAt(depth + 1)
    switch (key.card) {
      case "explorer":
        return page.explorer ? (
          <ExplorerSheet
            explorer={page.explorer}
            emotions={page.emotions}
            domains={page.domains}
            palettes={palettes}
            onClose={onClose}
            onPush={(card) => open({ card })}
            pushed={pushed}
          />
        ) : null
      case "emotions":
        return page.emotions ? (
          <EmotionsSheet emotions={page.emotions} firstPebble={firstPebble} palettes={palettes} onClose={onClose} pushed={pushed} />
        ) : null
      case "domains":
        return page.domains ? <DomainsSheet domains={page.domains} onClose={onClose} pushed={pushed} /> : null
      case "pebbles":
        return page.pebbles ? (
          <PebblesSheet
            pebbles={page.pebbles}
            glyphsCarved={page.glyphsCarved}
            glyphSales={page.glyphSales}
            firstPebble={firstPebble}
            onClose={onClose}
            pushed={pushed}
          />
        ) : null
      case "year": {
        const i = page.years.findIndex((y) => y.year === key.year)
        return i >= 0 ? (
          <YearSheet
            year={page.years[i]}
            earlier={page.years.slice(i + 1).map((y) => y.year)}
            palettes={palettes}
            onClose={onClose}
            onPushYear={(year) => open({ card: "year", year })}
            pushed={pushed}
          />
        ) : null
      }
    }
  }

  return (
    <div className="min-h-dvh bg-spine-ground font-spine text-spine-ink">
      <div className="mx-auto w-full max-w-md px-4 pt-4 pb-24 md:max-w-3xl md:px-6 md:pt-8">
        <PageHeader title={t("title")} backHref="/profile" />
        <p aria-live="polite" className="sr-only">
          {page && loading ? tp("refreshing") : ""}
        </p>
        {page ? (
          <>
            {page.since && page.daysKept !== null ? <DaysKeptHeader since={page.since} daysKept={page.daysKept} /> : null}
            <div className="grid gap-3.5 md:grid-cols-2 md:items-start">
              {page.explorer ? (
                <ExplorerCard
                  explorer={page.explorer}
                  emotions={page.emotions}
                  domains={page.domains}
                  palettes={palettes}
                  onOpen={() => open({ card: "explorer" })}
                />
              ) : null}
              {page.emotions ? (
                <EmotionsCard emotions={page.emotions} firstPebble={firstPebble} palettes={palettes} onOpen={() => open({ card: "emotions" })} />
              ) : null}
              {page.domains ? <DomainsCard domains={page.domains} onOpen={() => open({ card: "domains" })} /> : null}
              {page.pebbles ? (
                <PebblesCard
                  pebbles={page.pebbles}
                  glyphsCarved={page.glyphsCarved}
                  glyphSales={page.glyphSales}
                  firstPebble={firstPebble}
                  onOpen={() => open({ card: "pebbles" })}
                />
              ) : null}
              {page.years.map((year) => (
                <YearCard key={year.year} year={year} palettes={palettes} onOpen={() => open({ card: "year", year: year.year })} />
              ))}
            </div>
          </>
        ) : loading ? (
          <p aria-live="polite" className="text-sm text-spine-ink-2">
            {t("loading")}
          </p>
        ) : failed ? (
          <div role="alert" className="flex flex-col items-start gap-3">
            <p className="text-sm text-destructive">{t("error")}</p>
            <Button variant="outline" onClick={retry}>
              {tc("retry")}
            </Button>
          </div>
        ) : null}
      </div>
      {sheetAt(0)}
      {/* The seals' shared outlines and hatching, once for the page and its sheets. */}
      <StoneSprite />
    </div>
  )
}
```

- [ ] **Step 2: Typecheck, lint, build**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web && npm run build --workspace=apps/web`. Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/achievements/AchievementsPageView.tsx
git commit -m "feat(web): open each achievements card's sheet and mark it seen"
```

### Task 29: Verify Part 6 in a browser, then open its PR

Use the throwaway account from Task 24.

- [ ] **Step 1: Each card opens its sheet**
  - In the network panel, opening a sheet sends one `mark_achievements_seen` with its card, and no new `get_achievements`.
  - A row with a pebble opens `/pebble/<moment id>`.
- [ ] **Step 2: Explorer's pushes**
  - The Domains row pushes the Domains sheet over Explorer.
  - Escape closes only the top sheet, and focus returns to the Domains row.
  - A second Escape closes Explorer, and focus returns to the Explorer card.
  - The same holds for the Emotions row, and for a year's earlier years when an account has two.
- [ ] **Step 3: "New" marks**
  - Record a pebble with a new emotion, then reload: the Emotions card and its sheet show its new mark.
  - Close the sheet and reload: the mark is gone.
- [ ] **Step 4: Layout and themes**
  - On a phone the sheet is a bottom sheet, at most 92 dvh, and scrolls; from `md` it is a 440 px side panel.
  - Every row is at least 44 px high.
  - Paper and Night both read.
- [ ] **Step 5: Keyboard and screen reader**
  - Tab order: the close button, then the rows in order.
  - Each row reads its name, its pebble and its date, with "new since you last looked" where it is new.
  - The year's months read as one sentence each.
- [ ] **Step 6: Open the PR** with `gh stack`, titled `feat(web): the achievements sheets`. Its body opens with `Part of #1308`.

---

# Part 7 — the ceremony (branch `feat/1308-achievements-web-ceremony`)

Start the branch: `gh stack add feat/1308-achievements-web-ceremony`.

When the server says a completed set is `ready` (06:00 local the day after it was made whole, D9), its card shows a quiet "Ready" mark. The user opens it, and the ceremony calls `pull_ceremony` once:
- **the first pull** returns the karma, which the ceremony shows;
- **no row** (already pulled) or `not_ready` is not a success, so the dialog closes and the page re-reads.

### Task 30: The mark and the ceremony

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`, `fr.json`
- Create: `apps/web/components/achievements/ReadyMark.tsx`, `CeremonyDialog.tsx`

- [ ] **Step 1: The copy**

In `en.json`, replace:

```json
        "openYear": "{year}. Open its sheet."
      }
    }
```

with:

```json
        "openYear": "{year}. Open its sheet."
      }
    },
    "ceremony": {
      "ready": "Ready",
      "readyAria": "{title}: ready. Open the ceremony.",
      "opening": "Opening…",
      "failed": "It didn’t open. Try again in a moment.",
      "done": "Done"
    }
```

In `fr.json`, replace:

```json
        "openYear": "{year}. Ouvrir sa feuille."
      }
    }
```

with:

```json
        "openYear": "{year}. Ouvrir sa feuille."
      }
    },
    "ceremony": {
      "ready": "Prêt",
      "readyAria": "{title} : prêt. Ouvrir la cérémonie.",
      "opening": "Ouverture…",
      "failed": "Ça n’a pas pu s’ouvrir. Réessaie dans un instant.",
      "done": "Terminé"
    }
```

The ceremony's titles and descriptions are Part 1's `achievements.completion.*`, and its karma line is the existing `activity.amount`.

- [ ] **Step 2: `ReadyMark.tsx`**

The dot pulses only under `motion-safe`.

```tsx
"use client"

import { useTranslations } from "next-intl"
import type { CeremonyStack } from "@/lib/types"

/**
 * The quiet "ready" mark (#1308, D9): a completed set's ceremony is offered
 * from 06:00 the next day, and the server's `ready` flag says when. The user
 * starts it here; it never starts itself.
 */
export function ReadyMark({ stack, onOpen }: { stack: CeremonyStack; onOpen: (stack: CeremonyStack) => void }) {
  const t = useTranslations("achievements.ceremony")
  const tt = useTranslations("achievements.completion")
  return (
    <button
      type="button"
      onClick={() => onOpen(stack)}
      aria-label={t("readyAria", { title: tt(`${stack}.title`) })}
      className="flex min-h-11 min-w-11 cursor-pointer items-center gap-1.5 rounded-full px-3 font-spine-sc text-[11px] font-semibold tracking-[.18em] text-spine-ink outline-offset-2 focus-visible:outline-2 focus-visible:outline-spine-ink"
    >
      <span aria-hidden className="size-1.5 rounded-full bg-spine-ink motion-safe:animate-pulse" />
      {t("ready")}
    </button>
  )
}
```

- [ ] **Step 3: `CeremonyDialog.tsx`**

The pull runs from an effect behind a ref guard, so development mode's double-invoked effects cannot pull twice. The server is exactly-once anyway: a second pull would only return no row.

```tsx
"use client"

import { useEffect, useRef, useState } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import type { CeremonyOutcome } from "@/lib/data/achievements-loader"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { useHaptics } from "@/lib/hooks/useHaptics"
import type { AchievementsPage, CeremonyStack } from "@/lib/types"
import { DomainBand } from "./DomainBand"
import { EmotionRosace } from "./EmotionRosace"
import { ExplorerWheel } from "./ExplorerWheel"

type CeremonyDialogProps = {
  stack: CeremonyStack
  page: AchievementsPage
  palettes: AchievementPalettes
  pull: (stack: CeremonyStack) => Promise<CeremonyOutcome>
  onClose: () => void
}

type Phase = { kind: "opening" } | { kind: "pulled"; karma: number } | { kind: "failed" }

/**
 * A completed set's ceremony (#1308, D9). Opening it pulls once: the first
 * pull pays the set's karma and shows it. "Gone" (already pulled, or not
 * ready after all) is not a success, so the dialog closes and the page
 * re-reads; a failure says so and keeps the mark.
 */
export function CeremonyDialog({ stack, page, palettes, pull, onClose }: CeremonyDialogProps) {
  const t = useTranslations("achievements.ceremony")
  const tt = useTranslations("achievements.completion")
  const ta = useTranslations("activity")
  const { vibrate } = useHaptics()
  const [phase, setPhase] = useState<Phase>({ kind: "opening" })
  // One pull per opening, even when development mode runs effects twice.
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    void pull(stack).then((outcome) => {
      if (outcome.kind === "pulled") {
        setPhase({ kind: "pulled", karma: outcome.karmaGranted })
        vibrate([12, 40, 18])
      } else if (outcome.kind === "gone") {
        onClose()
      } else {
        setPhase({ kind: "failed" })
      }
    })
  }, [pull, stack, onClose, vibrate])

  const figure =
    stack === "emotions" && page.emotions ? (
      <EmotionRosace emotions={page.emotions} palettes={palettes} size={220} labelled />
    ) : stack === "domains" && page.domains ? (
      <DomainBand domains={page.domains} width={280} />
    ) : stack === "six_firsts" && page.explorer ? (
      <ExplorerWheel explorer={page.explorer} emotions={page.emotions} domains={page.domains} palettes={palettes} size={220} />
    ) : null

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="bg-spine-stock font-spine text-center text-spine-ink">
        <DialogHeader className="place-items-center">
          {figure ? (
            <div className="flex w-full justify-center py-2 motion-safe:animate-in motion-safe:fade-in motion-safe:zoom-in-95 motion-safe:duration-700">
              {figure}
            </div>
          ) : null}
          <DialogTitle className="font-spine-sc text-sm font-semibold tracking-[.18em]">{tt(`${stack}.title`)}</DialogTitle>
          <DialogDescription className="text-base text-spine-ink-2 italic">{tt(`${stack}.description`)}</DialogDescription>
        </DialogHeader>
        <p aria-live="polite" className="min-h-6 text-base font-semibold">
          {phase.kind === "opening" ? <span className="font-normal text-spine-ink-2">{t("opening")}</span> : null}
          {phase.kind === "pulled" && phase.karma > 0 ? ta("amount", { amount: phase.karma }) : null}
          {phase.kind === "failed" ? <span className="font-normal text-destructive">{t("failed")}</span> : null}
        </p>
        <DialogFooter className="sm:justify-center">
          <Button onClick={onClose} disabled={phase.kind === "opening"}>
            {t("done")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 4: Typecheck and lint**

Run: `rm -f apps/web/tsconfig.tsbuildinfo && npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web`. Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json apps/web/components/achievements/ReadyMark.tsx apps/web/components/achievements/CeremonyDialog.tsx
git commit -m "feat(web): add the ready mark and the completed set's ceremony"
```

### Task 31: The cards carry the mark

**Files:**
- Modify: `apps/web/components/achievements/AchievementsPageView.tsx`

- [ ] **Step 1: Replace the view**

The six firsts' completion rides on Explorer, the emotions' on Emotions and the domains' on Domains. The mark appears only when the server's `completion.ready` is true.

```tsx
"use client"

import { useCallback, useMemo, useState, type ReactNode } from "react"
import { useTranslations } from "next-intl"
import { PageHeader } from "@/components/layout/PageHeader"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { Button } from "@/components/ui/button"
import { achievementPalettes } from "@/lib/achievements/palette"
import { closeSheet, pushSheet, type SheetKey } from "@/lib/achievements/sheets"
import { useAchievementsPage } from "@/lib/data/useAchievementsPage"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import type { CeremonyStack } from "@/lib/types"
import { CeremonyDialog } from "./CeremonyDialog"
import { DaysKeptHeader } from "./DaysKeptHeader"
import { DomainsCard } from "./DomainsCard"
import { EmotionsCard } from "./EmotionsCard"
import { ExplorerCard } from "./ExplorerCard"
import { PebblesCard } from "./PebblesCard"
import { ReadyMark } from "./ReadyMark"
import { YearCard } from "./YearCard"
import { DomainsSheet } from "./sheets/DomainsSheet"
import { EmotionsSheet } from "./sheets/EmotionsSheet"
import { ExplorerSheet } from "./sheets/ExplorerSheet"
import { PebblesSheet } from "./sheets/PebblesSheet"
import { YearSheet } from "./sheets/YearSheet"

/**
 * You → Achievements (#1308, spec 2026-10-29 D1): the days kept, then
 * Explorer, Emotions, Domains, Pebbles and one card per year. Every number,
 * order and flag is the server's; a section the payload lacks hides its card.
 * Each card opens its sheet, which marks the card seen (D10); sheets read the
 * same payload, so opening one costs no read (D14). A set the server says is
 * ready carries the mark that opens its ceremony (D9). One column on a phone,
 * two from `md`.
 */
export function AchievementsPageView() {
  const t = useTranslations("achievements")
  const tp = useTranslations("achievements.page")
  const tc = useTranslations("common")
  const { page, loading, failed, retry, markSeen, pull } = useAchievementsPage()
  const { paletteByEmotionId } = useEmotionPalettes()
  const palettes = useMemo(() => achievementPalettes(page?.emotions?.seats ?? [], paletteByEmotionId), [page, paletteByEmotionId])
  const firstPebble = page?.explorer?.firstPebble ?? null
  const [stack, setStack] = useState<SheetKey[]>([])
  const [ceremony, setCeremony] = useState<CeremonyStack | null>(null)
  const closeCeremony = useCallback(() => setCeremony(null), [])
  const ready = (set: CeremonyStack, isReady: boolean | undefined) =>
    isReady ? <ReadyMark stack={set} onOpen={setCeremony} /> : undefined

  const open = useCallback(
    (key: SheetKey) => {
      setStack((s) => pushSheet(s, key))
      markSeen(key.card)
    },
    [markSeen],
  )

  // A pushed sheet renders inside the one below it, so it closes first.
  function sheetAt(depth: number): ReactNode {
    const key = stack[depth]
    if (!page || !key) return null
    const onClose = () => setStack((s) => closeSheet(s, depth))
    const pushed = sheetAt(depth + 1)
    switch (key.card) {
      case "explorer":
        return page.explorer ? (
          <ExplorerSheet
            explorer={page.explorer}
            emotions={page.emotions}
            domains={page.domains}
            palettes={palettes}
            onClose={onClose}
            onPush={(card) => open({ card })}
            pushed={pushed}
          />
        ) : null
      case "emotions":
        return page.emotions ? (
          <EmotionsSheet emotions={page.emotions} firstPebble={firstPebble} palettes={palettes} onClose={onClose} pushed={pushed} />
        ) : null
      case "domains":
        return page.domains ? <DomainsSheet domains={page.domains} onClose={onClose} pushed={pushed} /> : null
      case "pebbles":
        return page.pebbles ? (
          <PebblesSheet
            pebbles={page.pebbles}
            glyphsCarved={page.glyphsCarved}
            glyphSales={page.glyphSales}
            firstPebble={firstPebble}
            onClose={onClose}
            pushed={pushed}
          />
        ) : null
      case "year": {
        const i = page.years.findIndex((y) => y.year === key.year)
        return i >= 0 ? (
          <YearSheet
            year={page.years[i]}
            earlier={page.years.slice(i + 1).map((y) => y.year)}
            palettes={palettes}
            onClose={onClose}
            onPushYear={(year) => open({ card: "year", year })}
            pushed={pushed}
          />
        ) : null
      }
    }
  }

  return (
    <div className="min-h-dvh bg-spine-ground font-spine text-spine-ink">
      <div className="mx-auto w-full max-w-md px-4 pt-4 pb-24 md:max-w-3xl md:px-6 md:pt-8">
        <PageHeader title={t("title")} backHref="/profile" />
        <p aria-live="polite" className="sr-only">
          {page && loading ? tp("refreshing") : ""}
        </p>
        {page ? (
          <>
            {page.since && page.daysKept !== null ? <DaysKeptHeader since={page.since} daysKept={page.daysKept} /> : null}
            <div className="grid gap-3.5 md:grid-cols-2 md:items-start">
              {page.explorer ? (
                <ExplorerCard
                  explorer={page.explorer}
                  emotions={page.emotions}
                  domains={page.domains}
                  palettes={palettes}
                  onOpen={() => open({ card: "explorer" })}
                  corner={ready("six_firsts", page.explorer.completion?.ready)}
                />
              ) : null}
              {page.emotions ? (
                <EmotionsCard
                  emotions={page.emotions}
                  firstPebble={firstPebble}
                  palettes={palettes}
                  onOpen={() => open({ card: "emotions" })}
                  corner={ready("emotions", page.emotions.completion?.ready)}
                />
              ) : null}
              {page.domains ? (
                <DomainsCard domains={page.domains} onOpen={() => open({ card: "domains" })} corner={ready("domains", page.domains.completion?.ready)} />
              ) : null}
              {page.pebbles ? (
                <PebblesCard
                  pebbles={page.pebbles}
                  glyphsCarved={page.glyphsCarved}
                  glyphSales={page.glyphSales}
                  firstPebble={firstPebble}
                  onOpen={() => open({ card: "pebbles" })}
                />
              ) : null}
              {page.years.map((year) => (
                <YearCard key={year.year} year={year} palettes={palettes} onOpen={() => open({ card: "year", year: year.year })} />
              ))}
            </div>
          </>
        ) : loading ? (
          <p aria-live="polite" className="text-sm text-spine-ink-2">
            {t("loading")}
          </p>
        ) : failed ? (
          <div role="alert" className="flex flex-col items-start gap-3">
            <p className="text-sm text-destructive">{t("error")}</p>
            <Button variant="outline" onClick={retry}>
              {tc("retry")}
            </Button>
          </div>
        ) : null}
      </div>
      {sheetAt(0)}
      {ceremony && page ? <CeremonyDialog stack={ceremony} page={page} palettes={palettes} pull={pull} onClose={closeCeremony} /> : null}
      {/* The seals' shared outlines and hatching, once for the page and its sheets. */}
      <StoneSprite />
    </div>
  )
}
```

- [ ] **Step 2: Typecheck, lint, test, build**

Run: `npx tsc --noEmit -p apps/web/tsconfig.json && npm run lint --workspace=apps/web && npm run test --workspace=apps/web && npm run build --workspace=apps/web`. Expected: all green.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/achievements/AchievementsPageView.tsx
git commit -m "feat(web): offer a completed set's ceremony from its card"
```

### Task 32: The whole page, end to end

Use the throwaway account. The ready path needs a completion older than its offer:
- complete the six firsts with three pebbles (small lowlight, medium neutral, large highlight);
- ask the maintainer to backdate that row's `completed_at` in the SQL editor, as back-end Task 14 does with the service role. Never run that write yourself.

- [ ] **Step 1: Celebration** (Part 1, again, on the full stack):
  - a first bright pebble shows the unlock moment;
  - a first emotion, domain or six-firsts contribution shows nothing;
  - a heavy pebble that crosses a level shows nothing, and the Pebbles card marks it new.
- [ ] **Step 2: The ceremony**
  - Before the offer, Explorer has no mark.
  - After the backdate and a reload, Explorer shows "Ready". The mark reads "The six firsts: ready. Open the ceremony.", and it is a separate tab stop from the card.
  - Opening it shows the wheel, the title, "Opening…", then the karma. The phone vibrates if haptics are on, and the "Done" button closes the dialog.
  - The page re-reads, and the mark is gone.
- [ ] **Step 3: A stale mark**
  - Pull on a second tab first, then open the mark on the first.
  - The dialog closes by itself, there is no karma line, and the page re-reads.
- [ ] **Step 4: Reduced motion:** with "reduce motion" set, the figure does not zoom in and the mark's dot does not pulse.
- [ ] **Step 5: Both themes, all widths:** repeat Task 24's Steps 4 and 5 and Task 29's Steps 4 and 5 on the final build. Record anything that differs from the boards in "As built" below.

### Task 33: Open the last PR and update the map

- [ ] **Step 1:** Open the PR with `gh stack`, titled `feat(web): pull a completed set's ceremony`. Its body:
  - opens with `Resolves #1308`;
  - lists the contract gaps from the top of this plan that are still open, by issue number once filed.
- [ ] **Step 2:** On the Arkaik map, leave `V-achievements` at `development`. The Arkaik GitHub App moves it to `releasing` at merge, and `mark-live.yml` moves it to `live` after a production deploy. Never set `live` yourself.
- [ ] **Step 3:** `docs/decisions/log.md` gets no entry: the back-end part already logs "served whole", and the web adds no decision a future agent could reverse.

### Task 34: Swap in the dumped payloads (when back-end Task 10 has run)

Do this as soon as `packages/supabase/scripts/oracles/achievements/payloads/` exists, in whichever part is open then.

- [ ] **Step 1:** For `get-achievements.json`, `get-achievements-year.json`, `get-achievements-seen.json` and `check-achievements.json`, add a constant per file to `achievements-captured.ts` (`DUMPED_GET_ACHIEVEMENTS`, and so on), holding the file's contents byte for byte in `String.raw` backticks.
  - If contract gap 4 is fixed, these are the raw response bodies.
  - If not, note in the comment that they are re-serialized (scale and spacing lost).
- [ ] **Step 2:** In `achievements-payload.test.ts`, add one `describe("dumped payloads")` block. For each dumped page, assert what the harness seeded (back-end Task 10's checks):
  - `explorer.sixFirsts` are all done, in kind order;
  - `emotions.seats` has 38 seats;
  - `domains.coverage` is `"few"`;
  - `pebbles.next` is `{ threshold: 10, left: 7, progress: 0.222 }`;
  - `years[0].months` has 12 months.
- [ ] **Step 3:** In `achievement-results.test.ts`, decode `check-achievements.json`, and assert that `pebble-count-1` is `celebrate: false` (the harness's heavy first pebble).
- [ ] **Step 4:** Keep the hand-built VARIANTS fixture: no harness run produces its tolerance cases. Replace VETERAN and NEWCOMER only if the dumps cover the same states; otherwise keep both.
- [ ] **Step 5:** Run `npm run test --workspace=apps/web`, then commit `test(web): replay the harness's achievements payloads`.

---

## As built

Record here, during execution, anything that differs from this plan: a contrast fallback, a focus-return fix, a copy change the maintainer asked for.

### Part 1 + Part 2, as one PR (`feat/1308-achievements-web-contract`, 30 October)

The back-end shipped first, so the stack was re-cut: its first PR is Task 3, Part 2 (Tasks 5–11) and Task 34.

- **Tasks 1, 2 and 4 dropped.** `check_achievements()` has no `celebrate` column; it returns only the rows to celebrate, and the unlock moment keeps celebrating every returned row. Its row mapping did move out of the provider (`lib/data/achievement-results.ts`, `unlockResultsFrom`) so the dumped rows replay; a null row or one without a slug is now dropped instead of cast. The call runs under a 10 s `withTimeout`, so a hung check cannot hold the page's read.
- **Task 3: copy only, in iOS's words.**
  - Group names for the five families. Titles and descriptions for `first_moment`, `first_pebble` and `first_five`.
  - No family keys for `first_stone` or `set_complete`: their titles are the catalog's seeded `title_en/fr`, and they have no description, as on iOS. The plan's `stoneKind`/`completion` blocks and its slug parsing were not built.
  - `useAchievementCopy` falls back to the slug and an empty description for a family without copy, never to a message-key path. The unlock moment already hides an empty description.
  - **The `AchievementFamily` union and the icons are deferred to the web icon migration (#1313).** Widening the union breaks the typecheck on the two `FAMILY_ICONS` records that migration rewrites, so it widens both together. Until then the union lags the catalog (its comment says so) and every family-keyed consumer must fall back.
- **Task 6 replaced by Task 34.** The harness's five dumps are embedded byte for byte (`DUMPED_*`), and a test pins each to its file. The `get-achievements*.json` bodies are verbatim (gap 4 is fixed); `check-achievements.json` is still re-serialised by the harness. VETERAN and NEWCOMER were not generated. One hand-written `GET_ACHIEVEMENTS_VARIANTS` holds the tolerance cases.
- **Tasks 5 and 7 follow the served payload and the shared tolerance rules (spec §4), not this plan's TypeScript.**
  - Added: `on` on every dated row, `emotions.families` and `since`, each ladder's `since`, `done`, `seen`, `shelf`/`shelf_count`, the completion's `pebble` and `stack`.
  - A family stays the served string (palette lookup, else ink). An unknown stone kind, first-five slug, subject kind, coverage or `needs` is null and its item kept. Unknown rings and seen cards are left out.
  - Instants are `Date`, read in every spelling the surfaces emit; days are `YYYY-MM-DD`, impossible ones null. A `moment_id` or subject `id` that is not a UUID is null and its item kept. `seat` is null when absent.
  - Missing counts are 0, flags false, `reachable` true (iOS's defaults). A section present but mistyped is dropped whole and its key logged (`skipped`), on the fresh read and on the kept copy.
  - A year without one entry per month is dropped; its months are placed January to December by their own number (as Android does). `listed` outside 1…12 is filtered.
- **Parity departures from iOS:** a `next_step` with an unknown `needs` is kept with `needs: null` (iOS drops it, Android keeps it). **Parts 5–6: the views must hide the next-step sentence and gauge when `needs` is null.** The cache is written only by the newest read (iOS writes every decoded read).
- **Tasks 10–11: a store replaces the loader.** `lib/data/achievements-page-store.ts` (`AchievementsPageStore`, framework-free and tested in node) is the twin of iOS's `AchievementsModel`; `useAchievementsPage` is a `useSyncExternalStore` shell that makes one store per user and closes it on unmount or a change of user.
  - Stale-while-revalidate from the per-user cache; a read sequence so an older answer never replaces a newer page; `close()` aborts the session's reads (their cancellation is no error, their body never kept).
  - The zone is read as each read or pull starts, as on iOS, and the body is kept under that zone. The cache is cleared at logout, account deletion and any `SIGNED_OUT`/sessionless auth event, and each clear bumps an epoch so a read in flight across it never writes back.
  - Pull outcomes: `pulled{karmaGranted}`, `pulledWithoutKarma`, `alreadyPulled`, `notReady`, `busy`, `failed`, gated exactly as iOS on a read made in this session. `not_ready` is matched on `code === "P0001"` and `message === "not_ready"`.
  - Known window, as on iOS: a pull that times out client-side but lands on the server after the re-read reads as `failed`; the next read shows it pulled.
  - No hook test: the web suite runs in node with no React renderer (jsdom is only the admin's, and `vitest.config.ts` keeps the web suite DOM-free); the store's tests cover what a closed store must not do.

### Parts 3 + 4, as one PR (`feat/1308-achievements-web-figures`, 30 October)

The figures follow the parity settled with iOS (`Figures/*`) and Android (`features/achievements/figures/*`, `StoneSeal.kt`), not this plan's code where they differ.

- **Fixture:** `packages/supabase/scripts/oracles/achievements/design/` (the canonical design fixture and its domains), byte for byte from the iOS branch, replaces VETERAN/NEWCOMER in the figures' tests; palettes come from `packages/supabase/reference/`.
- **Modules** (`lib/achievements/`): `geometry`, `seals`, `palette`, `runs` (family runs, the band's arrival order), `wheel` (geometry and model in one), `rosace`, `shards`, `skyline`, `year-disc`, `ladders` (the margin ladders as `LadderBar { fill, done }`). Not built here: `copy.ts` (Task 15), `CountCell`, `milestoneRows`, `domainRows`, `emotionRegister`, `yearRows`: they are the page's words and the sheets' rows, and move to those parts.
- **Seals** keep the outline apart from the print; the six firsts' intensities, the domains' ring and the year's counters are a solid print on the neutral outline. Tint and wash are 15 %/9 % on Paper, 18 %/12 % at Night. Strokes stay at the boards' screen widths (the sprite's bodies are non-scaling).
- **The wheel** places seats by list index, spreads the first five by `i·360/n` and the six firsts, the 25 and the domains by `(i+0.5)·360/n` of the served count; the domains by `entered + not_yet`; the 25 ring always (25 ghosts without `twenty_five`); a ghost at the centre before the first pebble; a met seat without its pebble as the even medium stone; new marks from the served flag alone; an unknown task or kind a ghost.
- **The rosace** lights an unresolved family's kites and name in ink, and its soft stars ink 38 % toward the stock (the board's soft star is the family's tone 38 % toward the stock; iOS); names fall back to the slug; labels use the boards' `tx` (shame's shaded on Paper). A run wraps across seat 0 when the last and first seats share a family: the star after the last seat lights (iOS too; Android will align). Geometry, runs and facets are memoised.
- **Stages past the eighth** reuse the last stage (`stageIndex`, `stageOf`), as all three surfaces will.
- **The year disc** carries the 40 grains, mutes a month on its state alone, hues a kept month by its family or ink, rings the rim. A rim month whose family does not resolve keeps its ring, in ink.
- **Done styles** come only from served facts: `rings_done`, a family with an empty `not_yet`, `coverage == all`, listed levels, level 2. The skyline stands per listed level (iOS), and an empty ladder stands (iOS, Android).
- **The band** is laid out on the board's 300 × 40 and scales as one figure, `width` only capping its rendered size (iOS keeps the same 300 × 40 aspect).
- **The wheel** draws four tracks; the emotions ring has its family arcs instead.
- **Owed by the page part (Part 5):** an achievements layout that mounts `StoneSprite` once and imports both Ysabeau fonts (Ysabeau SC, Ysabeau Office) the figures' text uses, with a render test asserting the sprite's ids are on the page; and an optional `label` on `YearDisc` (then `role="img"` with that name instead of `aria-hidden`), for a disc drawn outside a labelled card.
- **Tests:** the boards' own strings (`figures.test.ts`), the models on the design fixture and on counts and families the board never drew (`models.test.ts`), and the organisms rendered to markup with `react-dom/server` (`figures-render.test.ts`, no DOM). The figures are not yet seen in a browser: Part 5's `/sandbox/design` does that.

### Part 5 + Task 15, the page (`feat/1308-achievements-web-page`, 31 October)

iOS's page (`feat/1306-ios-achievements-page`) is the reference; where this plan's Tasks 20–24 differ, iOS wins.

- **Copy** (`achievements.page.*`, Task 20 + Task 15): word for word with iOS's `Localizable.xcstrings` `achievements.*`, not this plan's wording. `lib/achievements/copy.ts` (`achievementsWords`) holds iOS's `AchievementsCopy` and `AchievementsA11y` logic, and is tested against iOS's own expectations. `lib/i18n/useAchievementsWords.ts` binds it to the reader's language.
- **Read-aloud set:** Explorer "Explorer: the first five, done; …; emotions, 23 of 38. 3 new." ("done" only from `rings_done`, "; " join, absent sections left out). Emotions and Domains as "Emotions, 23 of 38." and "Domains, 14 of 18. <coverage>". Pebbles as "Pebbles. <line> Glyphs carved: 12. Glyph sales: 3.". The disc as "The year 2026. On the rim: … On their way: …" through `Intl.ListFormat`. A card-level "N new" plural. A ready mark as "<ceremony title>: the ceremony is ready.". Cards are not buttons yet (Part 6): each carries its label as screen-reader text, its drawing hidden.
- **The next step** is hidden when `needs` is null.
- **The ceremony:** a ready mark replaces the band's hint and is stilled while any pull runs. The store gained `pulledThisSession` and `withPulled` (iOS `markPulled`): a landed pull marks its completion pulled at once, so the mark goes even if the re-read fails, and every later read keeps it marked; a stack is never asked twice in a session. A landed pull plays in the existing unlock moment through `presentCeremony`: the catalog's title, iOS's ceremony body as the description (the row has none), and no karma line at 0 or for pulled-without-karma. The moment takes `fallback` words for that. The screen-open check never celebrates.
- **D18 Profile shelf:** drawn from the served `shelf`/`shelf_count` through the store's shelf mode (kept copy at once, no grant, no read while the kept copy is under 15 minutes old; the cache now records when it kept a payload). Titles and icons come from the catalog by slug (`useAchievementCatalog`). A badge whose row is missing still draws from its served row when its family is one the union knows; otherwise it is left out until the catalog loads. The count keeps the web's existing plural strings ("# badges earned" / "# badges décrochés").
- **Layout:** `app/achievements/layout.tsx` loads Ysabeau SC, Office and the italic, and mounts `StoneSprite` once; a render test asserts the sprite ids. `YearDisc` takes an optional `label` (`role="img"`).
- **Hydration:** the figures' trigonometry is rounded to a millionth (`steady`), because `Math.sin`/`Math.cos` differed in their last bit between Node and Chrome and the server-rendered wheel and rosace failed to hydrate.
- **Old grid:** `AchievementsView.tsx` is deleted. `AchievementBadge.tsx` and `useAchievements.ts` are now unused but kept: the web icon migration (#1313) rewrites the badge's icon map, and deleting it here would conflict. Remove both once #1313 lands.
- **For #1313, when it widens `AchievementFamily`:** drop the shelf's `KNOWN` filter (`lib/achievements/shelf.ts`) and remove `AchievementBadge.tsx` and `useAchievements.ts`, with the `achievements.locked`/`unlockedOn` keys only the badge reads.
- **Review fixes:** the ceremony plays at once from its words (no catalog read); an unlock (a mutation's check returning rows, or a landed pull) marks every kept payload stale so the shelf's next open reads, and a shelf read grants first, as the page does (decided for all three clients); the header and each still card carry a visually hidden heading; the year disc's notches are `steady`; the words' `Intl` formatters are built once per language; `/sandbox` loads the Ysabeau italic; the unlock moment hides "View achievements" on `/achievements`; the old grid's `group` and `error` keys are gone. The cross-workspace JSON import in `AchievementSpecimens.tsx` builds locally with Turbopack; a Vercel preview is the remaining check.
- **`/sandbox/design`:** an "Achievements page" specimen draws the content from the canonical design fixture with the reference palettes, as served or with ceremonies ready.
- **Verified in a browser:** headless Chrome over CDP on port 3100, light and dark, at 390 and 1280. The real route was checked signed out only (it renders its title and mounts the sprite, with no console error). It was not checked signed in: no throwaway account was available. The real-data pass is still owed.

## Self-review notes

- **Spec coverage:**
  - D1 (a header and five cards, no legends, new marks on figures only) → Tasks 21 and 22.
  - D2 (days kept as served) → `DaysKeptHeader`.
  - D3 (stacks) → `ladder-model`, `PebbleSkyline`, `PebblesSheet`.
  - D4 and D15 (the crossing pebble) → `crossingPebbleFrom`; rows link to it, and a removed pebble reads "Its pebble was removed".
  - D5 (six firsts) → `SIX_FIRST_SEALS`.
  - D6 (wheel order) → `wheelModel`.
  - D7 and D8 (`celebrate`) → Part 1.
  - D9 (offered, then pulled) → Part 7.
  - D10 (new per card) → `isNew`, `NewMark`, `RowDate`, `markCardSeen`.
  - D11 (tier, never a count; arrival order) → `DomainsCard`, `bandItems`.
  - D12 (the year) → `year-disc`, `year-model`, `YearSheet`.
  - D14 (one read, sheets cost no call) → `useAchievementsPage`.
  - D16 (private families) → the cache is cleared at sign-out.
  - D17 (whole seconds, omitted keys, enum fallbacks) → the decoder and the VARIANTS fixture.
- **§4:**
  - decode and map → Parts 2 and 3;
  - draw → Parts 3 and 4;
  - localise → `copy.ts` and the catalogs;
  - resilience → the cache, hidden sections and enum fallbacks;
  - interaction → Parts 6 and 7;
  - celebration → Part 1.
- **§5:** card and disc labels are assembled from the payload; EN and FR throughout; rows are at least 44 px; colour is never the only signal (the level is a position, success a ring, "new" a ring and a dot plus spoken text).
- **Not built:** motion beyond the ceremony's entrance. Haptics fire only on a pull. The boards specify no other motion.
- **Verified while writing:**
  - Every pure module and test above was run on a copy of `apps/web`: tsc, ESLint and Vitest are green (the 77 new tests included).
  - The components were rendered from the fixtures in Chrome, Paper and Night, cards and sheets. They match the boards.
  - The geometry tests' expected strings were printed by the boards' own generators.
