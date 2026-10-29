# Achievements page — Android implementation plan (#1307)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** replace the Android achievements grid with the approved achievements page and its five sheets, served whole by `get_achievements`, mirroring iOS (#1306) 1:1.

**Architecture:**
- **The server decides, Android renders.** `get_achievements(p_tz)` arrives as one JSON body. It is decoded by `AchievementsJson`, which tolerates omitted keys, explicit nulls and unknown enums. A pure `toModel()` maps it into display models. Drawing is geometry only: seat → angle, level → radius, progress → spoke length, family → palette. Nothing compares a count with a threshold.
- **Layering:**
  - `core/model/achievements` holds the contract: DTOs, models and mapping.
  - `core/data/AchievementsService` holds the calls and the cache. The cache reuses the wraps' `WrapDiskCaching`: owner- and zone-scoped, never backed up, wiped at sign-out.
  - `core/ui/render/stone` gains ink seals.
  - `core/designsystem/SpineTypography` gains the page's type ranks.
  - Everything else lives in a new feature package, `features/achievements`, which imports no other feature (Konsist).
- **Interaction:**
  - One `@HiltViewModel` owns the page state, using stale-while-revalidate from the device copy.
  - It also owns the sheet stack. Sheets are screen-local `ModalBottomSheet`s, the house precedent. Explorer's Domains and Emotions rows push a second sheet on top.
  - It fires the seen marks and runs the ceremony pull. The pull reuses the existing unlock moment overlay.
- **The celebration** after a mutation shows only the `check_achievements` rows where `celebrate` is true.

**Tech stack:** Kotlin, Jetpack Compose and Material 3 (Expressive alpha pin), Hilt, Navigation 3, kotlinx.serialization, supabase-kt Postgrest, JUnit 4 with Robolectric (`@GraphicsMode(NATIVE)` for pixels), and Compose Preview Screenshot Testing.

**Spec:** `docs/superpowers/specs/2026-10-29-achievements-page-design.md`. §4 is the client rule, §5 is accessibility and i18n.

**Contract:** `docs/superpowers/plans/2026-10-29-achievements-page-backend.md`:
- Task 3: `check_achievements` returns `celebrate`.
- Task 9: `get_achievements` and `achievements_year`.
- Task 12: `mark_achievements_seen` and `pull_ceremony`.

**Design:** the maintainer-approved boards (`final/index.html` and `final/sheets.html`, with `NOTES.md`). This plan ports their geometry from `_build/app.js` and `_build/sheets.js`, and never their data logic: the boards compute levels from a fixture, and Android reads them from the payload.

**Issue:** #1307, milestone M65 · Achievements. The stack has four parts, one branch and one PR each, chained with `gh stack` (read the `gh-stack` skill before opening the first one):

| Part | Branch | What it ships | Builds and passes alone because |
|---|---|---|---|
| 1 | `feat/1307-android-achievements-data` | the contract: DTOs, models, mapping, replay and tolerance tests, `celebrate` and its filter, the service seam (read, cache, seen, pull) and catalog copy for the new families | nothing in the UI calls the new seam yet, and the old grid still compiles against `loadCatalog`/`loadUnlocks` |
| 2 | `feat/1307-android-achievements-figures` | type ranks, ink seals, the five organisms (wheel, rosace, band, skyline, year disc), the margin ladder, the marks, a debug fixture and figure previews | the figures are public composables previewed by the screenshot set; no screen uses them yet |
| 3 | `feat/1307-android-achievements-sheets` | the page copy (EN/FR), the register molecules and the five sheets with their push host, plus sheet previews | the sheets are stateless and previewed; the host takes its stack as a parameter |
| 4 | `feat/1307-android-achievements-page` | the ViewModel (cache, resume refresh, sheet stack, seen, ceremony), the card shell, the page, the entry swap, the old grid's removal and page previews | it is the first consumer of parts 1–3, and the old grid goes in the same commit as the swap |

**Ground rules for every task:**
- **Run Gradle from `apps/android`** with the SDK exported, or `scripts/gradle-if-sdk.sh` no-ops and looks green: `export ANDROID_HOME=$HOME/Library/Android/sdk`.
- **The gate is three commands:**
  - `./gradlew testDebugUnitTest`, the JVM, Robolectric and Konsist tests;
  - `./gradlew lint`, which is a gate (`warningsAsErrors`; an unused string fails it);
  - `./gradlew ktlintCheck`, where `ktlintFormat` fixes style.
  - `npm run lint --workspace=@pbbls/android` is ktlint only and is not the gate.
- **The screenshot gate is CI-only:**
  - Never commit references rendered on this Mac.
  - New previews get their references from the `rebaseline-screenshots` label on the PR; then approve the held runs (see the `android-screenshot-gate` skill).
  - Locally, `./gradlew validateDebugScreenshotTest` is judged only by diffing against `main`'s local run.
- **Strings land with their first `main` caller**, in `res/values/strings.xml` and `res/values-fr/strings.xml` together:
  - `LocalizationParityTest` checks the two files match key for key.
  - Lint's `UnusedResources` fails a string with no caller.
  - French plurals carry `one`, `many` and `other`, the #1121 lesson.
- **Copy mirrors the iOS catalog.** #1306 lands its strings in its own part 4. When it does, diff the English sentences in this plan against its `Localizable.xcstrings` and align the Android copy in a follow-up commit. The cross-surface rule is the same words on every surface.
- **No `.message` reaches the UI**, no `Color(0x…)`, no literal corner radius and no `.copy(fontSize = …)` in `features/` or `core/ui`. `ThemeLiteralsTest` enforces the last three.
- **Never write a Kritik finding id in a PR body.**

**Before Task 1:** on the Arkaik map, move `V-achievements` to `development` with `update_node`, following the `arkaik` skill's hosted-map rules. `update_node` replaces metadata wholesale, so send the gherkin and values back. If the `arkaik-mcp` tools are not available, say so and stop; never fall back to `docs/arkaik/bundle.json`.


> **Contract resolutions (2026-10-29, after the three client plans):** the back-end plan now serves what the client planners found missing. Where this plan hard-codes or tolerates one of these, switch to the served value:
> - **No explicit nulls:** `explorer.first_five` and `six_firsts` are `[]` when empty, and `twenty_five` is omitted when it can't be built.
> - **The year serves `days_target` (14) and per-month `done: {moments, pebbles, weeks}`.** Draw the done style from `done`; never compare a count with 14.
> - **Every dated row carries `on`,** by one rule: the local day its pebble happened, or the recorded day when that pebble is gone. That covers seats, domain entries, the six firsts, the 25th and ladder levels.
> - **`emotions.families`** gives the sheet's order: families in wheel order, each with its met seats by the day met and its `not_yet` seats.
> - **`since`** on `emotions` and on each ladder (pebbles, glyphs carved, glyph sales) is the card's "since" date.
> - **`seen`** is a top-level map of card → last-look time, at whole seconds.
> - **A completion carries its `pebble`.**
> - **Celebration:** `pebble-count-1` never celebrates, because `first-pebble` celebrates that pebble once. A heavy first pebble celebrates neither.
> - **No year "new" marks** in v1.
> - **The dumps are verbatim response bodies,** and a keyed run adds `get-achievements-pulled.json` (a pulled completion).
> - **Still open with the maintainer:**
>   - the ceremony's look and copy;
>   - whether the owner's own Profile shelf also hides the private families.

---

## Contract notes (read before coding)

These are read off back-end Tasks 3, 9 and 12, and they shape the decoder:

- **Explicit nulls exist where D17 says they should not.**
  - `explorer.six_firsts` is a `jsonb_agg` over the `first_stone` catalog rows, so it is JSON `null` when no row is active.
  - `explorer.twenty_five` is a scalar subquery, so it is `null` when `pebble-count-25` is missing.
  - The decoder therefore defaults every field and treats `null` like absent.
- **Order is the server's, read as sent:**
  - `domains.entered` is newest first. The register reads it as sent, and the band and the wheel read it backwards (arrival order, as the board draws it).
  - `emotions.seats` is in wheel order, then slug.
  - `years` is newest first.
  - `years[].listed` is newest first.
  - The client never sorts by a date.
- **Emotions within a family are listed in seat order, not "by date".** The sheet's board orders the met emotions by first date, but the payload carries no such order and sorting by `pebble.happened_at` would be the client computing an order. This is reported as a gap; Android uses seat order.
- **The year carries no `new` flag**, so `mark_achievements_seen('year')` is sent (it is a valid card) but nothing on the disc reads "new". This is reported as a gap.
- **`pull_ceremony` outcomes:**
  - a row means pulled, with the karma paid;
  - no row means already pulled, so the client refreshes and shows nothing;
  - `not_ready` (P0001) means the client's view is stale, so it refreshes and shows nothing;
  - any other failure is logged and shown as a snackbar.
- **Timestamps** are whole-second UTC `…Z` from the server. The decoder also accepts fractional seconds and `+00:00`, the standing tolerance rule. Dates are `YYYY-MM-DD`.
- **The dumped payloads** are `packages/supabase/scripts/oracles/achievements/payloads/{check-achievements,get-achievements,get-achievements-year,get-achievements-seen}.json`, from back-end Tasks 5, 10 and 14 with `ACHIEVEMENTS_DUMP=1`.
  - Until they land, Task 1 commits hand-built copies of the same names, shaped exactly as Task 9 emits them, under `app/src/test/resources/`.
  - Task 6 swaps them for the dumped files.
  - The replay tests assert only what the harness itself asserts for those users, so the swap changes no test.
  - Everything date-dependent is asserted in the tolerance tests, on inline JSON.

---

## File map

All paths are under `apps/android/app/src/` unless they start with `apps/`, `docs/` or `packages/`. `K` = `main/kotlin/app/pbbls/android`, `T` = `test/kotlin/app/pbbls/android`.

| File | Part | Responsibility |
|---|---|---|
| `test/resources/{get-achievements,get-achievements-year,get-achievements-seen,check-achievements}.json` | 1 | hand-built payloads until the dumps land (Task 6 deletes them) |
| `K/core/model/achievements/AchievementsJson.kt` | 1 | the one tolerant decoder, production and tests |
| `K/core/model/achievements/AchievementsPageDto.kt` | 1 | `get_achievements` as sent; every field defaulted |
| `K/core/model/achievements/AchievementsPage.kt` | 1 | display models and the semantic enums with their fallbacks |
| `K/core/model/achievements/AchievementsMapping.kt` | 1 | `toModel()`, tolerant timestamps, `decodeAchievementsPage` |
| `K/core/model/achievements/CeremonyPull.kt` | 1 | `pull_ceremony`'s row and its outcome |
| `K/core/data/AchievementsService.kt` | 1 | `celebrate`, `celebrated()`, `page`, `cachedPage`, `markSeen`, `pullCeremony` |
| `K/core/ui/AchievementCopy.kt` | 1 | titles for `first_moment`, `first_pebble`, `first_stone`, `set_complete` |
| `T/core/model/achievements/*Test.kt`, `T/core/data/AchievementCelebrationTest.kt`, `T/core/ui/AchievementCopyNewFamiliesTest.kt` | 1 | replay, tolerance, celebration and copy tests |
| `T/testing/FakeAchievementsService.kt` | 1, 4 | the seam's fake: page, cache, seen, pull |
| `app/build.gradle.kts` | 1 | the dumped payloads on the test classpath (Task 6) |
| `K/core/designsystem/SpineTypography.kt` | 2 | the page's type ranks |
| `K/core/ui/render/stone/StoneSeal.kt` | 2 | ink seals: relief, open, engraved, blank, dotted |
| `K/features/achievements/AchievementLookups.kt` | 2 | ids and slugs → families, emotions, domains |
| `K/features/achievements/figures/FigureInk.kt` | 2 | family hue, rosace tones, seal sets, ghosts and new marks |
| `K/features/achievements/figures/FigureData.kt` | 2 | the payload readings figures share: family runs, the band's arrival order |
| `K/features/achievements/figures/CardLadder.kt` | 2 | the margin ladder and its pure fills |
| `K/features/achievements/figures/Marks.kt` | 2 | first-five marks, six-firsts marks, pebble seals as register marks |
| `K/features/achievements/figures/ExplorerWheel.kt` (+`ExplorerWheelGeometry.kt`) | 2 | the five rings |
| `K/features/achievements/figures/Rosace.kt` (+`RosaceGeometry.kt`) | 2 | the 38-seat rosace |
| `K/features/achievements/figures/ShardBand.kt` (+`ShardBandGeometry.kt`) | 2 | the mended band of domains |
| `K/features/achievements/figures/Skyline.kt` (+`SkylineGeometry.kt`) | 2 | the skyline, the mini skyline and one stage |
| `K/features/achievements/figures/YearDisc.kt` (+`YearDiscGeometry.kt`) | 2 | the docked year disc and the month counter |
| `debug/kotlin/app/pbbls/android/AchievementsFixture.kt` | 2 | the board's page as a model, with lookups, for previews and tests |
| `screenshotTest/kotlin/app/pbbls/android/AchievementFiguresScreenshots.kt` | 2 | figures in both themes |
| `K/features/achievements/copy/AchievementsCopy.kt` | 3 | every sentence of the page, from semantic values |
| `K/features/achievements/AchievementSheet.kt` | 3 | the sheet identities |
| `K/features/achievements/sheets/SheetParts.kt` | 3 | band, register row (48 dp), dotted names, ready mark |
| `K/features/achievements/sheets/{Explorer,Emotions,Domains,Pebbles,Year}Sheet.kt` | 3 | the five sheets |
| `K/features/achievements/sheets/AchievementSheetHost.kt` | 3 | the stacked `ModalBottomSheet`s |
| `screenshotTest/kotlin/app/pbbls/android/AchievementSheetsScreenshots.kt` | 3 | sheets in both themes, French and large font |
| `K/features/achievements/AchievementsViewModel.kt` | 4 | state, cache, resume refresh, sheets, seen, ceremony |
| `K/features/achievements/page/AchievementsPageContent.kt` | 4 | the page: header, card shell and the five cards (private to it) |
| `K/features/achievements/AchievementsScreen.kt` | 4 | the stateful screen and the stateless content |
| `K/navigation/PebblesEntryProvider.kt` | 4 | the entry points at the new screen |
| `K/features/profile/Achievements{Screen,ViewModel}.kt`, `T/features/profile/AchievementsViewModelTest.kt`, `screenshotTest/.../AchievementsScreenshots.kt`, its references | 4 | deleted (the grid) |
| `screenshotTest/kotlin/app/pbbls/android/AchievementsPageScreenshots.kt` | 4 | the page: content, loading, error, both themes, French, large font |
| `T/features/achievements/*Test.kt` | 2–4 | geometry, copy, ViewModel and target-size tests |
| `T/core/designsystem/AchievementTypeRanksTest.kt`, `T/core/ui/render/stone/InkSealPixelTest.kt` | 2 | the type ranks and the ink seal's pixels |
| `T/ui/AchievementsPageTest.kt` | 4 | the page through the whole app: open, push, back |

---
# Part 1 — the contract (branch `feat/1307-android-achievements-data`)

Create the branch from an updated `main`, never from another feature branch:

```bash
git fetch origin && git switch -c feat/1307-android-achievements-data origin/main
```

### Task 1: The payloads and the DTOs

**Files:**
- Create: `apps/android/app/src/test/resources/get-achievements.json`
- Create: `apps/android/app/src/test/resources/get-achievements-year.json`
- Create: `apps/android/app/src/test/resources/get-achievements-seen.json`
- Create: `apps/android/app/src/test/resources/check-achievements.json`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsJson.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsPageDto.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementPayloads.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsDtoDecodingTest.kt`

The four payloads are the users the harness dumps, built by hand to Task 9's exact shape, with today being 29 October 2026 in `Europe/Paris`:
- **`get-achievements`:** Alice after back-end section 2. Three pebbles of one emotion and one domain: small heavy on 2 March, medium even on 3 March, large bright on 4 March. The six firsts are whole and offered tomorrow.
- **`get-achievements-year`:** Bob. March to June 2025: March reaches the rim, April the inner disc, May is kept below, June holds moments only. He has 26 pebbles.
- **`get-achievements-seen`:** Alice after back-end section 3. Three emotions met, and `amused` is new.
- **`check-achievements`:** Alice's first check over her heavy first pebble.

Emotion ids are `e0000000-…-0000000000NN`, with NN the emotion's index by slug, so `amazed` is `…00` and `amused` is `…01`. Domain ids are `d0000000-…`, by slug. Seats follow the wheel (joy · pride · anger · fear · shame · sadness · peace), then the slug.

- [ ] **Step 1: Write the four payload files**

`apps/android/app/src/test/resources/get-achievements.json`:

```json
{
  "since": "2026-10-29",
  "days_kept": 1,
  "explorer": {
    "first_pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1},
    "first_five": [
      {"slug": "first-moment", "done": true, "new": false, "on": "2026-10-29", "subject": {"kind": "moment", "id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d"}},
      {"slug": "first-pebble", "done": true, "new": false, "on": "2026-10-29", "subject": {"kind": "pebble", "id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d"}},
      {"slug": "first-collection", "done": false, "new": false},
      {"slug": "first-soul", "done": false, "new": false},
      {"slug": "first-glyph", "done": false, "new": false}
    ],
    "six_firsts": [
      {"kind": "small", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}},
      {"kind": "medium", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000002", "name": "medium even 1a2b3c4d", "happened_at": "2026-03-03T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 2, "positiveness": 0}},
      {"kind": "large", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000003", "name": "large bright 1a2b3c4d", "happened_at": "2026-03-04T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 3, "positiveness": 1}},
      {"kind": "lowlight", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}},
      {"kind": "neutral", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000002", "name": "medium even 1a2b3c4d", "happened_at": "2026-03-03T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 2, "positiveness": 0}},
      {"kind": "highlight", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000003", "name": "large bright 1a2b3c4d", "happened_at": "2026-03-04T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 3, "positiveness": 1}}
    ],
    "twenty_five": {"count": 3, "done": false, "new": false},
    "rings_done": ["six_firsts"],
    "completion": {"completed_at": "2026-10-29T08:12:31Z", "offered_from": "2026-10-30T05:00:00Z", "ready": false}
  },
  "emotions": {
    "count": 1,
    "of": 38,
    "seats": [
      {"seat": 0, "emotion_id": "e0000000-0000-4000-8000-000000000000", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}},
      {"seat": 1, "emotion_id": "e0000000-0000-4000-8000-000000000001", "family": "joy", "met": false, "new": false},
      {"seat": 2, "emotion_id": "e0000000-0000-4000-8000-000000000015", "family": "joy", "met": false, "new": false},
      {"seat": 3, "emotion_id": "e0000000-0000-4000-8000-000000000017", "family": "joy", "met": false, "new": false},
      {"seat": 4, "emotion_id": "e0000000-0000-4000-8000-000000000019", "family": "joy", "met": false, "new": false},
      {"seat": 5, "emotion_id": "e0000000-0000-4000-8000-000000000020", "family": "joy", "met": false, "new": false},
      {"seat": 6, "emotion_id": "e0000000-0000-4000-8000-000000000025", "family": "joy", "met": false, "new": false},
      {"seat": 7, "emotion_id": "e0000000-0000-4000-8000-000000000028", "family": "joy", "met": false, "new": false},
      {"seat": 8, "emotion_id": "e0000000-0000-4000-8000-000000000036", "family": "joy", "met": false, "new": false},
      {"seat": 9, "emotion_id": "e0000000-0000-4000-8000-000000000006", "family": "pride", "met": false, "new": false},
      {"seat": 10, "emotion_id": "e0000000-0000-4000-8000-000000000008", "family": "pride", "met": false, "new": false},
      {"seat": 11, "emotion_id": "e0000000-0000-4000-8000-000000000030", "family": "pride", "met": false, "new": false},
      {"seat": 12, "emotion_id": "e0000000-0000-4000-8000-000000000031", "family": "pride", "met": false, "new": false},
      {"seat": 13, "emotion_id": "e0000000-0000-4000-8000-000000000033", "family": "pride", "met": false, "new": false},
      {"seat": 14, "emotion_id": "e0000000-0000-4000-8000-000000000002", "family": "anger", "met": false, "new": false},
      {"seat": 15, "emotion_id": "e0000000-0000-4000-8000-000000000003", "family": "anger", "met": false, "new": false},
      {"seat": 16, "emotion_id": "e0000000-0000-4000-8000-000000000012", "family": "anger", "met": false, "new": false},
      {"seat": 17, "emotion_id": "e0000000-0000-4000-8000-000000000016", "family": "anger", "met": false, "new": false},
      {"seat": 18, "emotion_id": "e0000000-0000-4000-8000-000000000023", "family": "anger", "met": false, "new": false},
      {"seat": 19, "emotion_id": "e0000000-0000-4000-8000-000000000024", "family": "anger", "met": false, "new": false},
      {"seat": 20, "emotion_id": "e0000000-0000-4000-8000-000000000004", "family": "fear", "met": false, "new": false},
      {"seat": 21, "emotion_id": "e0000000-0000-4000-8000-000000000027", "family": "fear", "met": false, "new": false},
      {"seat": 22, "emotion_id": "e0000000-0000-4000-8000-000000000034", "family": "fear", "met": false, "new": false},
      {"seat": 23, "emotion_id": "e0000000-0000-4000-8000-000000000035", "family": "fear", "met": false, "new": false},
      {"seat": 24, "emotion_id": "e0000000-0000-4000-8000-000000000037", "family": "fear", "met": false, "new": false},
      {"seat": 25, "emotion_id": "e0000000-0000-4000-8000-000000000005", "family": "shame", "met": false, "new": false},
      {"seat": 26, "emotion_id": "e0000000-0000-4000-8000-000000000014", "family": "shame", "met": false, "new": false},
      {"seat": 27, "emotion_id": "e0000000-0000-4000-8000-000000000018", "family": "shame", "met": false, "new": false},
      {"seat": 28, "emotion_id": "e0000000-0000-4000-8000-000000000010", "family": "sadness", "met": false, "new": false},
      {"seat": 29, "emotion_id": "e0000000-0000-4000-8000-000000000011", "family": "sadness", "met": false, "new": false},
      {"seat": 30, "emotion_id": "e0000000-0000-4000-8000-000000000013", "family": "sadness", "met": false, "new": false},
      {"seat": 31, "emotion_id": "e0000000-0000-4000-8000-000000000021", "family": "sadness", "met": false, "new": false},
      {"seat": 32, "emotion_id": "e0000000-0000-4000-8000-000000000026", "family": "sadness", "met": false, "new": false},
      {"seat": 33, "emotion_id": "e0000000-0000-4000-8000-000000000032", "family": "sadness", "met": false, "new": false},
      {"seat": 34, "emotion_id": "e0000000-0000-4000-8000-000000000007", "family": "peace", "met": false, "new": false},
      {"seat": 35, "emotion_id": "e0000000-0000-4000-8000-000000000009", "family": "peace", "met": false, "new": false},
      {"seat": 36, "emotion_id": "e0000000-0000-4000-8000-000000000022", "family": "peace", "met": false, "new": false},
      {"seat": 37, "emotion_id": "e0000000-0000-4000-8000-000000000029", "family": "peace", "met": false, "new": false}
    ]
  },
  "domains": {
    "count": 1,
    "of": 18,
    "coverage": "few",
    "entered": [
      {"domain_id": "d0000000-0000-4000-8000-000000000000", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}}
    ],
    "not_yet": ["d0000000-0000-4000-8000-000000000001", "d0000000-0000-4000-8000-000000000002", "d0000000-0000-4000-8000-000000000003", "d0000000-0000-4000-8000-000000000004", "d0000000-0000-4000-8000-000000000005", "d0000000-0000-4000-8000-000000000006", "d0000000-0000-4000-8000-000000000007", "d0000000-0000-4000-8000-000000000008", "d0000000-0000-4000-8000-000000000009", "d0000000-0000-4000-8000-000000000010", "d0000000-0000-4000-8000-000000000011", "d0000000-0000-4000-8000-000000000012", "d0000000-0000-4000-8000-000000000013", "d0000000-0000-4000-8000-000000000014", "d0000000-0000-4000-8000-000000000015", "d0000000-0000-4000-8000-000000000016", "d0000000-0000-4000-8000-000000000017"]
  },
  "pebbles": {
    "count": 3,
    "levels": [
      {"threshold": 1, "on": "2026-10-29", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}}
    ],
    "ladder": [1, 10, 25, 50, 100, 250, 500, 1000],
    "next": {"threshold": 10, "left": 7, "progress": 0.222},
    "then_every": 500
  },
  "glyphs_carved": {
    "count": 0,
    "levels": [],
    "ladder": [10, 25, 50, 75, 100],
    "next": {"threshold": 10, "left": 10, "progress": 0.0}
  },
  "glyph_sales": {
    "count": 0,
    "levels": [],
    "ladder": [1, 5, 10, 25, 50],
    "next": {"threshold": 1, "left": 1, "progress": 0.0}
  },
  "years": [
    {"year": 2026, "current": true, "months": [{"month": 1, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 2, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 3, "state": "kept", "moment_days": 3, "pebble_days": 3, "full_weeks": 4, "weeks_filled": 1, "level": 0, "progress": 0.214, "family": "joy"}, {"month": 4, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 5, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 6, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 7, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 8, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 9, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 10, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 11, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}, {"month": 12, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}], "listed": [10, 9, 8, 7, 6, 5, 4, 3], "next_step": {"month": 10, "needs": "moments", "days_left": 14, "reachable": false}}
  ]
}
```

`apps/android/app/src/test/resources/get-achievements-year.json`:

```json
{
  "since": "2026-10-29",
  "days_kept": 1,
  "explorer": {
    "first_pebble": {"moment_id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d", "happened_at": "2025-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 3, "positiveness": 1},
    "first_five": [
      {"slug": "first-moment", "done": true, "new": false, "on": "2026-10-29", "subject": {"kind": "moment", "id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d"}},
      {"slug": "first-pebble", "done": true, "new": false, "on": "2026-10-29", "subject": {"kind": "pebble", "id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d"}},
      {"slug": "first-collection", "done": false, "new": false},
      {"slug": "first-soul", "done": false, "new": false},
      {"slug": "first-glyph", "done": false, "new": false}
    ],
    "six_firsts": [
      {"kind": "small", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000102", "name": "y 2025-03-03 1a2b3c4d", "happened_at": "2025-03-03T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000030", "intensity": 1, "positiveness": 1}},
      {"kind": "medium", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000103", "name": "y 2025-03-04 1a2b3c4d", "happened_at": "2025-03-04T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000002", "intensity": 2, "positiveness": 1}},
      {"kind": "large", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d", "happened_at": "2025-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 3, "positiveness": 1}},
      {"kind": "lowlight", "done": false, "new": false},
      {"kind": "neutral", "done": false, "new": false},
      {"kind": "highlight", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d", "happened_at": "2025-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 3, "positiveness": 1}}
    ],
    "twenty_five": {"count": 25, "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000125", "name": "y 2025-05-12 1a2b3c4d", "happened_at": "2025-05-12T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 1, "positiveness": 1}},
    "rings_done": ["twenty_five"]
  },
  "emotions": {
    "count": 5,
    "of": 38,
    "seats": [
      {"seat": 0, "emotion_id": "e0000000-0000-4000-8000-000000000000", "family": "joy", "met": false, "new": false},
      {"seat": 1, "emotion_id": "e0000000-0000-4000-8000-000000000001", "family": "joy", "met": false, "new": false},
      {"seat": 2, "emotion_id": "e0000000-0000-4000-8000-000000000015", "family": "joy", "met": false, "new": false},
      {"seat": 3, "emotion_id": "e0000000-0000-4000-8000-000000000017", "family": "joy", "met": false, "new": false},
      {"seat": 4, "emotion_id": "e0000000-0000-4000-8000-000000000019", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000104", "name": "y 2025-03-06 1a2b3c4d", "happened_at": "2025-03-06T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000019", "intensity": 1, "positiveness": 1}},
      {"seat": 5, "emotion_id": "e0000000-0000-4000-8000-000000000020", "family": "joy", "met": false, "new": false},
      {"seat": 6, "emotion_id": "e0000000-0000-4000-8000-000000000025", "family": "joy", "met": false, "new": false},
      {"seat": 7, "emotion_id": "e0000000-0000-4000-8000-000000000028", "family": "joy", "met": false, "new": false},
      {"seat": 8, "emotion_id": "e0000000-0000-4000-8000-000000000036", "family": "joy", "met": false, "new": false},
      {"seat": 9, "emotion_id": "e0000000-0000-4000-8000-000000000006", "family": "pride", "met": false, "new": false},
      {"seat": 10, "emotion_id": "e0000000-0000-4000-8000-000000000008", "family": "pride", "met": false, "new": false},
      {"seat": 11, "emotion_id": "e0000000-0000-4000-8000-000000000030", "family": "pride", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000102", "name": "y 2025-03-03 1a2b3c4d", "happened_at": "2025-03-03T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000030", "intensity": 1, "positiveness": 1}},
      {"seat": 12, "emotion_id": "e0000000-0000-4000-8000-000000000031", "family": "pride", "met": false, "new": false},
      {"seat": 13, "emotion_id": "e0000000-0000-4000-8000-000000000033", "family": "pride", "met": false, "new": false},
      {"seat": 14, "emotion_id": "e0000000-0000-4000-8000-000000000002", "family": "anger", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000103", "name": "y 2025-03-04 1a2b3c4d", "happened_at": "2025-03-04T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000002", "intensity": 2, "positiveness": 1}},
      {"seat": 15, "emotion_id": "e0000000-0000-4000-8000-000000000003", "family": "anger", "met": false, "new": false},
      {"seat": 16, "emotion_id": "e0000000-0000-4000-8000-000000000012", "family": "anger", "met": false, "new": false},
      {"seat": 17, "emotion_id": "e0000000-0000-4000-8000-000000000016", "family": "anger", "met": false, "new": false},
      {"seat": 18, "emotion_id": "e0000000-0000-4000-8000-000000000023", "family": "anger", "met": false, "new": false},
      {"seat": 19, "emotion_id": "e0000000-0000-4000-8000-000000000024", "family": "anger", "met": false, "new": false},
      {"seat": 20, "emotion_id": "e0000000-0000-4000-8000-000000000004", "family": "fear", "met": false, "new": false},
      {"seat": 21, "emotion_id": "e0000000-0000-4000-8000-000000000027", "family": "fear", "met": false, "new": false},
      {"seat": 22, "emotion_id": "e0000000-0000-4000-8000-000000000034", "family": "fear", "met": false, "new": false},
      {"seat": 23, "emotion_id": "e0000000-0000-4000-8000-000000000035", "family": "fear", "met": false, "new": false},
      {"seat": 24, "emotion_id": "e0000000-0000-4000-8000-000000000037", "family": "fear", "met": false, "new": false},
      {"seat": 25, "emotion_id": "e0000000-0000-4000-8000-000000000005", "family": "shame", "met": false, "new": false},
      {"seat": 26, "emotion_id": "e0000000-0000-4000-8000-000000000014", "family": "shame", "met": false, "new": false},
      {"seat": 27, "emotion_id": "e0000000-0000-4000-8000-000000000018", "family": "shame", "met": false, "new": false},
      {"seat": 28, "emotion_id": "e0000000-0000-4000-8000-000000000010", "family": "sadness", "met": false, "new": false},
      {"seat": 29, "emotion_id": "e0000000-0000-4000-8000-000000000011", "family": "sadness", "met": false, "new": false},
      {"seat": 30, "emotion_id": "e0000000-0000-4000-8000-000000000013", "family": "sadness", "met": false, "new": false},
      {"seat": 31, "emotion_id": "e0000000-0000-4000-8000-000000000021", "family": "sadness", "met": false, "new": false},
      {"seat": 32, "emotion_id": "e0000000-0000-4000-8000-000000000026", "family": "sadness", "met": false, "new": false},
      {"seat": 33, "emotion_id": "e0000000-0000-4000-8000-000000000032", "family": "sadness", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d", "happened_at": "2025-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 3, "positiveness": 1}},
      {"seat": 34, "emotion_id": "e0000000-0000-4000-8000-000000000007", "family": "peace", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000107", "name": "y 2025-03-10 1a2b3c4d", "happened_at": "2025-03-10T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000007", "intensity": 2, "positiveness": 1}},
      {"seat": 35, "emotion_id": "e0000000-0000-4000-8000-000000000009", "family": "peace", "met": false, "new": false},
      {"seat": 36, "emotion_id": "e0000000-0000-4000-8000-000000000022", "family": "peace", "met": false, "new": false},
      {"seat": 37, "emotion_id": "e0000000-0000-4000-8000-000000000029", "family": "peace", "met": false, "new": false}
    ]
  },
  "domains": {
    "count": 1,
    "of": 18,
    "coverage": "few",
    "entered": [
      {"domain_id": "d0000000-0000-4000-8000-000000000000", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d", "happened_at": "2025-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 3, "positiveness": 1}}
    ],
    "not_yet": ["d0000000-0000-4000-8000-000000000001", "d0000000-0000-4000-8000-000000000002", "d0000000-0000-4000-8000-000000000003", "d0000000-0000-4000-8000-000000000004", "d0000000-0000-4000-8000-000000000005", "d0000000-0000-4000-8000-000000000006", "d0000000-0000-4000-8000-000000000007", "d0000000-0000-4000-8000-000000000008", "d0000000-0000-4000-8000-000000000009", "d0000000-0000-4000-8000-000000000010", "d0000000-0000-4000-8000-000000000011", "d0000000-0000-4000-8000-000000000012", "d0000000-0000-4000-8000-000000000013", "d0000000-0000-4000-8000-000000000014", "d0000000-0000-4000-8000-000000000015", "d0000000-0000-4000-8000-000000000016", "d0000000-0000-4000-8000-000000000017"]
  },
  "pebbles": {
    "count": 26,
    "levels": [
      {"threshold": 1, "on": "2026-10-29", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000101", "name": "y 2025-03-02 1a2b3c4d", "happened_at": "2025-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 3, "positiveness": 1}},
      {"threshold": 10, "on": "2026-10-29", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000110", "name": "y 2025-03-16 1a2b3c4d", "happened_at": "2025-03-16T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000019", "intensity": 2, "positiveness": 1}},
      {"threshold": 25, "on": "2026-10-29", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000125", "name": "y 2025-05-12 1a2b3c4d", "happened_at": "2025-05-12T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000032", "intensity": 1, "positiveness": 1}}
    ],
    "ladder": [1, 10, 25, 50, 100, 250, 500, 1000],
    "next": {"threshold": 50, "left": 24, "progress": 0.04},
    "then_every": 500
  },
  "glyphs_carved": {
    "count": 0,
    "levels": [],
    "ladder": [10, 25, 50, 75, 100],
    "next": {"threshold": 10, "left": 10, "progress": 0.0}
  },
  "glyph_sales": {
    "count": 0,
    "levels": [],
    "ladder": [1, 5, 10, 25, 50],
    "next": {"threshold": 1, "left": 1, "progress": 0.0}
  },
  "years": [
    {"year": 2026, "current": true, "months": [{"month": 1, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 2, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 3, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 4, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 5, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 6, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 7, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 8, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 9, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 10, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 11, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}, {"month": 12, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}], "listed": [10, 9, 8, 7, 6, 5, 4, 3, 2, 1], "next_step": {"month": 10, "needs": "moments", "days_left": 14, "reachable": false}},
    {"year": 2025, "current": false, "months": [{"month": 1, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 2, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 3, "state": "kept", "moment_days": 19, "pebble_days": 19, "full_weeks": 4, "weeks_filled": 4, "level": 2, "progress": 0, "family": "pride"}, {"month": 4, "state": "kept", "moment_days": 16, "pebble_days": 4, "full_weeks": 3, "weeks_filled": 3, "level": 1, "progress": 0.286, "family": "peace"}, {"month": 5, "state": "kept", "moment_days": 3, "pebble_days": 3, "full_weeks": 3, "weeks_filled": 3, "level": 0, "progress": 0.214, "family": "peace"}, {"month": 6, "state": "quiet", "moment_days": 3, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 3, "level": 0, "progress": 0.214}, {"month": 7, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 8, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 9, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 10, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 11, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 12, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}], "listed": [12, 11, 10, 9, 8, 7, 6, 5, 4, 3]}
  ]
}
```

`apps/android/app/src/test/resources/get-achievements-seen.json`:

```json
{
  "since": "2026-10-29",
  "days_kept": 1,
  "explorer": {
    "first_pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1},
    "first_five": [
      {"slug": "first-moment", "done": true, "new": false, "on": "2026-10-29", "subject": {"kind": "moment", "id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d"}},
      {"slug": "first-pebble", "done": true, "new": false, "on": "2026-10-29", "subject": {"kind": "pebble", "id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d"}},
      {"slug": "first-collection", "done": false, "new": false},
      {"slug": "first-soul", "done": false, "new": false},
      {"slug": "first-glyph", "done": false, "new": false}
    ],
    "six_firsts": [
      {"kind": "small", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}},
      {"kind": "medium", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000002", "name": "medium even 1a2b3c4d", "happened_at": "2026-03-03T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 2, "positiveness": 0}},
      {"kind": "large", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000003", "name": "large bright 1a2b3c4d", "happened_at": "2026-03-04T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 3, "positiveness": 1}},
      {"kind": "lowlight", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}},
      {"kind": "neutral", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000002", "name": "medium even 1a2b3c4d", "happened_at": "2026-03-03T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 2, "positiveness": 0}},
      {"kind": "highlight", "done": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000003", "name": "large bright 1a2b3c4d", "happened_at": "2026-03-04T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 3, "positiveness": 1}}
    ],
    "twenty_five": {"count": 6, "done": false, "new": false},
    "rings_done": ["six_firsts"],
    "completion": {"completed_at": "2026-10-29T08:12:31Z", "offered_from": "2026-10-30T05:00:00Z", "ready": false}
  },
  "emotions": {
    "count": 3,
    "of": 38,
    "seats": [
      {"seat": 0, "emotion_id": "e0000000-0000-4000-8000-000000000000", "family": "joy", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}},
      {"seat": 1, "emotion_id": "e0000000-0000-4000-8000-000000000001", "family": "joy", "met": true, "new": true, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000006", "name": "a third feeling 1a2b3c4d", "happened_at": "2026-03-07T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000001", "intensity": 2, "positiveness": 1}},
      {"seat": 2, "emotion_id": "e0000000-0000-4000-8000-000000000015", "family": "joy", "met": false, "new": false},
      {"seat": 3, "emotion_id": "e0000000-0000-4000-8000-000000000017", "family": "joy", "met": false, "new": false},
      {"seat": 4, "emotion_id": "e0000000-0000-4000-8000-000000000019", "family": "joy", "met": false, "new": false},
      {"seat": 5, "emotion_id": "e0000000-0000-4000-8000-000000000020", "family": "joy", "met": false, "new": false},
      {"seat": 6, "emotion_id": "e0000000-0000-4000-8000-000000000025", "family": "joy", "met": false, "new": false},
      {"seat": 7, "emotion_id": "e0000000-0000-4000-8000-000000000028", "family": "joy", "met": false, "new": false},
      {"seat": 8, "emotion_id": "e0000000-0000-4000-8000-000000000036", "family": "joy", "met": false, "new": false},
      {"seat": 9, "emotion_id": "e0000000-0000-4000-8000-000000000006", "family": "pride", "met": false, "new": false},
      {"seat": 10, "emotion_id": "e0000000-0000-4000-8000-000000000008", "family": "pride", "met": false, "new": false},
      {"seat": 11, "emotion_id": "e0000000-0000-4000-8000-000000000030", "family": "pride", "met": false, "new": false},
      {"seat": 12, "emotion_id": "e0000000-0000-4000-8000-000000000031", "family": "pride", "met": false, "new": false},
      {"seat": 13, "emotion_id": "e0000000-0000-4000-8000-000000000033", "family": "pride", "met": false, "new": false},
      {"seat": 14, "emotion_id": "e0000000-0000-4000-8000-000000000002", "family": "anger", "met": false, "new": false},
      {"seat": 15, "emotion_id": "e0000000-0000-4000-8000-000000000003", "family": "anger", "met": false, "new": false},
      {"seat": 16, "emotion_id": "e0000000-0000-4000-8000-000000000012", "family": "anger", "met": false, "new": false},
      {"seat": 17, "emotion_id": "e0000000-0000-4000-8000-000000000016", "family": "anger", "met": false, "new": false},
      {"seat": 18, "emotion_id": "e0000000-0000-4000-8000-000000000023", "family": "anger", "met": false, "new": false},
      {"seat": 19, "emotion_id": "e0000000-0000-4000-8000-000000000024", "family": "anger", "met": false, "new": false},
      {"seat": 20, "emotion_id": "e0000000-0000-4000-8000-000000000004", "family": "fear", "met": false, "new": false},
      {"seat": 21, "emotion_id": "e0000000-0000-4000-8000-000000000027", "family": "fear", "met": false, "new": false},
      {"seat": 22, "emotion_id": "e0000000-0000-4000-8000-000000000034", "family": "fear", "met": false, "new": false},
      {"seat": 23, "emotion_id": "e0000000-0000-4000-8000-000000000035", "family": "fear", "met": false, "new": false},
      {"seat": 24, "emotion_id": "e0000000-0000-4000-8000-000000000037", "family": "fear", "met": true, "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000005", "name": "a new feeling 1a2b3c4d", "happened_at": "2026-03-06T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000037", "intensity": 2, "positiveness": 1}},
      {"seat": 25, "emotion_id": "e0000000-0000-4000-8000-000000000005", "family": "shame", "met": false, "new": false},
      {"seat": 26, "emotion_id": "e0000000-0000-4000-8000-000000000014", "family": "shame", "met": false, "new": false},
      {"seat": 27, "emotion_id": "e0000000-0000-4000-8000-000000000018", "family": "shame", "met": false, "new": false},
      {"seat": 28, "emotion_id": "e0000000-0000-4000-8000-000000000010", "family": "sadness", "met": false, "new": false},
      {"seat": 29, "emotion_id": "e0000000-0000-4000-8000-000000000011", "family": "sadness", "met": false, "new": false},
      {"seat": 30, "emotion_id": "e0000000-0000-4000-8000-000000000013", "family": "sadness", "met": false, "new": false},
      {"seat": 31, "emotion_id": "e0000000-0000-4000-8000-000000000021", "family": "sadness", "met": false, "new": false},
      {"seat": 32, "emotion_id": "e0000000-0000-4000-8000-000000000026", "family": "sadness", "met": false, "new": false},
      {"seat": 33, "emotion_id": "e0000000-0000-4000-8000-000000000032", "family": "sadness", "met": false, "new": false},
      {"seat": 34, "emotion_id": "e0000000-0000-4000-8000-000000000007", "family": "peace", "met": false, "new": false},
      {"seat": 35, "emotion_id": "e0000000-0000-4000-8000-000000000009", "family": "peace", "met": false, "new": false},
      {"seat": 36, "emotion_id": "e0000000-0000-4000-8000-000000000022", "family": "peace", "met": false, "new": false},
      {"seat": 37, "emotion_id": "e0000000-0000-4000-8000-000000000029", "family": "peace", "met": false, "new": false}
    ]
  },
  "domains": {
    "count": 1,
    "of": 18,
    "coverage": "few",
    "entered": [
      {"domain_id": "d0000000-0000-4000-8000-000000000000", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}}
    ],
    "not_yet": ["d0000000-0000-4000-8000-000000000001", "d0000000-0000-4000-8000-000000000002", "d0000000-0000-4000-8000-000000000003", "d0000000-0000-4000-8000-000000000004", "d0000000-0000-4000-8000-000000000005", "d0000000-0000-4000-8000-000000000006", "d0000000-0000-4000-8000-000000000007", "d0000000-0000-4000-8000-000000000008", "d0000000-0000-4000-8000-000000000009", "d0000000-0000-4000-8000-000000000010", "d0000000-0000-4000-8000-000000000011", "d0000000-0000-4000-8000-000000000012", "d0000000-0000-4000-8000-000000000013", "d0000000-0000-4000-8000-000000000014", "d0000000-0000-4000-8000-000000000015", "d0000000-0000-4000-8000-000000000016", "d0000000-0000-4000-8000-000000000017"]
  },
  "pebbles": {
    "count": 6,
    "levels": [
      {"threshold": 1, "on": "2026-10-29", "new": false, "pebble": {"moment_id": "a1000000-0000-4000-8000-000000000001", "name": "heavy small 1a2b3c4d", "happened_at": "2026-03-02T09:00:00Z", "emotion_id": "e0000000-0000-4000-8000-000000000000", "intensity": 1, "positiveness": -1}}
    ],
    "ladder": [1, 10, 25, 50, 100, 250, 500, 1000],
    "next": {"threshold": 10, "left": 4, "progress": 0.556},
    "then_every": 500
  },
  "glyphs_carved": {
    "count": 0,
    "levels": [],
    "ladder": [10, 25, 50, 75, 100],
    "next": {"threshold": 10, "left": 10, "progress": 0.0}
  },
  "glyph_sales": {
    "count": 0,
    "levels": [],
    "ladder": [1, 5, 10, 25, 50],
    "next": {"threshold": 1, "left": 1, "progress": 0.0}
  },
  "years": [
    {"year": 2026, "current": true, "months": [{"month": 1, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 2, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 3, "state": "kept", "moment_days": 6, "pebble_days": 6, "full_weeks": 4, "weeks_filled": 1, "level": 0, "progress": 0.25, "family": "joy"}, {"month": 4, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 5, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 6, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 7, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 8, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 4, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 9, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 10, "state": "quiet", "moment_days": 0, "pebble_days": 0, "full_weeks": 3, "weeks_filled": 0, "level": 0, "progress": 0.0}, {"month": 11, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}, {"month": 12, "state": "upcoming", "moment_days": 0, "pebble_days": 0, "full_weeks": 0, "weeks_filled": 0, "level": 0, "progress": 0}], "listed": [10, 9, 8, 7, 6, 5, 4, 3], "next_step": {"month": 10, "needs": "moments", "days_left": 14, "reachable": false}}
  ]
}
```

`apps/android/app/src/test/resources/check-achievements.json`:

```json
[
  {"slug": "pebble-count-1", "karma_granted": 5, "celebrate": false},
  {"slug": "emotion-first-amazed", "karma_granted": 0, "celebrate": false},
  {"slug": "domain-first-community", "karma_granted": 0, "celebrate": false},
  {"slug": "first-moment", "karma_granted": 0, "celebrate": true},
  {"slug": "first-pebble", "karma_granted": 0, "celebrate": false},
  {"slug": "first-stone-small", "karma_granted": 0, "celebrate": false},
  {"slug": "first-stone-lowlight", "karma_granted": 0, "celebrate": false}
]
```

- [ ] **Step 2: Write the failing test**

`apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementPayloads.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

/**
 * The server's payloads (#1307): `packages/supabase/scripts/oracles/achievements/payloads/`,
 * dumped by `verify-achievements.ts` (ACHIEVEMENTS_DUMP=1), on the test classpath. One copy,
 * owned by the harness that produced it.
 */
internal object AchievementPayloads {
    fun read(name: String): String =
        requireNotNull(AchievementPayloads::class.java.getResource("/$name.json")) { "missing payload fixture $name.json" }.readText()

    fun dto(name: String): AchievementsPageDto = AchievementsJson.decodeFromString<AchievementsPageDto>(read(name))
}
```

`apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsDtoDecodingTest.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The payloads decode as sent, before any mapping (#1307): every card, every key Task 9 emits. */
class AchievementsDtoDecodingTest {
    @Test
    fun `the first payload decodes every card`() {
        val dto = AchievementPayloads.dto("get-achievements")
        assertNotNull(dto.explorer)
        assertNotNull(dto.emotions)
        assertNotNull(dto.domains)
        assertNotNull(dto.pebbles)
        assertNotNull(dto.glyphsCarved)
        assertNotNull(dto.glyphSales)
        assertTrue(dto.years.orEmpty().isNotEmpty())
        assertEquals(38, dto.emotions!!.seats.size)
        assertEquals(5, dto.explorer!!.firstFive!!.size)
        assertEquals(6, dto.explorer!!.sixFirsts!!.size)
    }

    @Test
    fun `a not-yet completion and a not-done first decode with their keys omitted`() {
        val explorer = AchievementPayloads.dto("get-achievements").explorer!!
        assertNull(explorer.completion!!.pulledAt)
        val collection = explorer.firstFive!!.first { it.slug == "first-collection" }
        assertNull(collection.on)
        assertNull(collection.subject)
    }

    @Test
    fun `the check rows carry celebrate`() {
        val rows = AchievementsJson.decodeFromString<List<CheckRowDto>>(AchievementPayloads.read("check-achievements"))
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.any { !it.celebrate })
    }
}
```

`CheckRowDto` is a test-local alias that Task 3 replaces with the service's own type; define it in the same test file, below the class, so this task stands alone:

```kotlin
@kotlinx.serialization.Serializable
private data class CheckRowDto(
    val slug: String = "",
    val celebrate: Boolean = false,
)
```

- [ ] **Step 3: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.model.achievements.AchievementsDtoDecodingTest"`
Expected: FAIL to compile, with `Unresolved reference: AchievementsJson` and `AchievementsPageDto`.

- [ ] **Step 4: Write the decoder and the DTOs**

`apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsJson.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import kotlinx.serialization.json.Json

/**
 * The achievements decoder (#1307), production and tests alike, so the tests read the server's
 * payloads exactly as the app does.
 *
 * Unknown keys are ignored because the payload grows additively across clients. An explicit null
 * takes the field's default, because `jsonb_agg` over no rows is `null` even where D17 promises
 * an omitted key.
 */
val AchievementsJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
```

`apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsPageDto.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `get_achievements(p_tz)` as the server sends it (#1305 Task 9, spec §3.3). Every field has a
 * default: an absent section omits its keys (D17) and an explicit null is coerced to the default.
 * [toModel] maps it to [AchievementsPage] and drops what cannot be drawn.
 */
@Serializable
data class AchievementsPageDto(
    val since: String? = null,
    @SerialName("days_kept")
    val daysKept: Int? = null,
    val explorer: ExplorerDto? = null,
    val emotions: EmotionsDto? = null,
    val domains: DomainsDto? = null,
    val pebbles: CountingDto? = null,
    @SerialName("glyphs_carved")
    val glyphsCarved: CountingDto? = null,
    @SerialName("glyph_sales")
    val glyphSales: CountingDto? = null,
    val years: List<YearDto>? = null,
)

/** `{pebble}`: the crossing pebble's display snapshot (D15). */
@Serializable
data class PebbleSnapshotDto(
    @SerialName("moment_id")
    val momentId: String? = null,
    val name: String? = null,
    @SerialName("happened_at")
    val happenedAt: String? = null,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    val intensity: Int? = null,
    val positiveness: Int? = null,
)

@Serializable
data class SubjectDto(
    val kind: String? = null,
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class FirstFiveDto(
    val slug: String? = null,
    val done: Boolean = false,
    val on: String? = null,
    val subject: SubjectDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class SixFirstDto(
    val kind: String? = null,
    val done: Boolean = false,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class TwentyFiveDto(
    val count: Int = 0,
    val done: Boolean = false,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class CompletionDto(
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("offered_from")
    val offeredFrom: String? = null,
    val ready: Boolean = false,
    @SerialName("pulled_at")
    val pulledAt: String? = null,
)

@Serializable
data class ExplorerDto(
    @SerialName("first_pebble")
    val firstPebble: PebbleSnapshotDto? = null,
    @SerialName("first_five")
    val firstFive: List<FirstFiveDto>? = null,
    @SerialName("six_firsts")
    val sixFirsts: List<SixFirstDto>? = null,
    @SerialName("twenty_five")
    val twentyFive: TwentyFiveDto? = null,
    @SerialName("rings_done")
    val ringsDone: List<String> = emptyList(),
    val completion: CompletionDto? = null,
)

@Serializable
data class SeatDto(
    val seat: Int = 0,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    val family: String? = null,
    val met: Boolean = false,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class EmotionsDto(
    val count: Int = 0,
    val of: Int = 0,
    val seats: List<SeatDto> = emptyList(),
    val completion: CompletionDto? = null,
)

@Serializable
data class EnteredDto(
    @SerialName("domain_id")
    val domainId: String? = null,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class DomainsDto(
    val count: Int = 0,
    val of: Int = 0,
    val coverage: String? = null,
    val entered: List<EnteredDto> = emptyList(),
    @SerialName("not_yet")
    val notYet: List<String> = emptyList(),
    val completion: CompletionDto? = null,
)

@Serializable
data class LevelDto(
    val threshold: Int = 0,
    val on: String? = null,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class NextLevelDto(
    val threshold: Int = 0,
    val left: Int = 0,
    val progress: Double = 0.0,
)

/** One counting stack: pebbles, glyphs carved or glyph sales (D3). */
@Serializable
data class CountingDto(
    val count: Int = 0,
    val levels: List<LevelDto> = emptyList(),
    val next: NextLevelDto? = null,
    val ladder: List<Int> = emptyList(),
    @SerialName("then_every")
    val thenEvery: Int? = null,
)

@Serializable
data class MonthDto(
    val month: Int = 0,
    val state: String? = null,
    val family: String? = null,
    @SerialName("moment_days")
    val momentDays: Int = 0,
    @SerialName("pebble_days")
    val pebbleDays: Int = 0,
    @SerialName("full_weeks")
    val fullWeeks: Int = 0,
    @SerialName("weeks_filled")
    val weeksFilled: Int = 0,
    val level: Int = 0,
    val progress: Double = 0.0,
)

@Serializable
data class NextStepDto(
    val month: Int = 0,
    val needs: String? = null,
    @SerialName("days_left")
    val daysLeft: Int = 0,
    val reachable: Boolean = true,
)

@Serializable
data class YearDto(
    val year: Int = 0,
    val current: Boolean = false,
    val months: List<MonthDto> = emptyList(),
    val listed: List<Int> = emptyList(),
    @SerialName("next_step")
    val nextStep: NextStepDto? = null,
)
```

- [ ] **Step 5: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.model.achievements.AchievementsDtoDecodingTest"`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/test/resources/get-achievements.json apps/android/app/src/test/resources/get-achievements-year.json \
  apps/android/app/src/test/resources/get-achievements-seen.json apps/android/app/src/test/resources/check-achievements.json \
  apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/ \
  apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/
git commit -m "test(android): decode the achievements payloads as the server sends them"
```

### Task 2: The display models and the mapping

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsPage.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsMapping.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsReplayTest.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsToleranceTest.kt`

- [ ] **Step 1: Write the failing replay test**

It asserts only what `verify-achievements.ts` asserts for the same users, so it stays true once the dumped payloads replace the hand-built ones.

`apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsReplayTest.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page mapped from the server's own payloads (#1307, spec §4). The expected values are the
 * ones back-end sections 2 and 3 assert on the server; this checks that Android reads the same
 * payload the same way.
 */
class AchievementsReplayTest {
    private fun page(name: String) = decodeAchievementsPage(AchievementPayloads.read(name))

    @Test
    fun `the explorer's centre is the first, heavy, small pebble, at whole seconds`() {
        val first = page("get-achievements").explorer!!.firstPebble!!
        assertEquals(Valence.LOWLIGHT_SMALL, first.valence)
        assertEquals(0, first.happenedAt.nano)
    }

    @Test
    fun `the first five and the six firsts come in the server's order, the six done`() {
        val explorer = page("get-achievements").explorer!!
        assertEquals(FirstStep.entries, explorer.firstFive.map { it.step })
        assertEquals(StoneKind.entries, explorer.sixFirsts.map { it.kind })
        assertTrue(explorer.sixFirsts.all { it.done && it.pebble != null })
    }

    @Test
    fun `the six firsts ring is done and its completion is not ready before tomorrow 06 00`() {
        val explorer = page("get-achievements").explorer!!
        assertTrue(ExplorerRing.SIX_FIRSTS in explorer.ringsDone)
        val completion = explorer.completion!!
        assertFalse(completion.ready)
        assertNull(completion.pulledAt)
        assertTrue(completion.offeredFrom.isAfter(completion.completedAt))
    }

    @Test
    fun `emotions seat 38 with one met, in seat order`() {
        val emotions = page("get-achievements").emotions!!
        assertEquals(38, emotions.seats.size)
        assertEquals((0 until 38).toList(), emotions.seats.map { it.seat })
        assertEquals(1, emotions.count)
        assertEquals(1, emotions.seats.count { it.met })
    }

    @Test
    fun `domains report one entered, the rest not yet, and the few tier`() {
        val domains = page("get-achievements").domains!!
        assertEquals(1, domains.count)
        assertEquals(Coverage.FEW, domains.coverage)
        assertEquals(1, domains.entered.size)
        assertEquals(domains.of - 1, domains.notYet.size)
    }

    @Test
    fun `pebbles - three kept, the next is 10 with 7 left at 0 222, then every 500`() {
        val pebbles = page("get-achievements").pebbles!!
        assertEquals(3, pebbles.count)
        assertEquals(NextLevel(threshold = 10, left = 7, progress = 0.222f), pebbles.next)
        assertEquals(500, pebbles.thenEvery)
        assertNotNull(page("get-achievements").glyphsCarved)
        assertNotNull(page("get-achievements").glyphSales)
    }

    @Test
    fun `nothing is new before the first look`() {
        val p = page("get-achievements")
        assertTrue(p.explorer!!.sixFirsts.none { it.isNew })
        assertTrue(p.pebbles!!.levels.none { it.isNew })
        assertTrue(p.emotions!!.seats.none { it.isNew })
    }

    @Test
    fun `the year payload lists the current year and 2025, newest first, twelve months each`() {
        val years = page("get-achievements-year").years
        assertEquals(2, years.size)
        assertTrue(years[0].current)
        assertEquals(2025, years[1].year)
        assertTrue(years[0].year > years[1].year)
        assertTrue(years.all { y -> y.months.map { it.month } == (1..12).toList() })
    }

    @Test
    fun `a past year is never current, has no next step, and lists March to December newest first`() {
        val y2025 = page("get-achievements-year").years[1]
        assertFalse(y2025.current)
        assertNull(y2025.nextStep)
        assertEquals((12 downTo 3).toList(), y2025.listed)
        assertTrue(y2025.months.none { it.state == MonthState.UPCOMING })
    }

    @Test
    fun `after the emotions card is seen, exactly one emotion first is new`() {
        val seats = page("get-achievements-seen").emotions!!.seats
        assertEquals(1, seats.count { it.isNew })
    }
}
```

- [ ] **Step 2: Write the failing tolerance test**

These are inline payloads in the server's shape, with the precision variants, explicit nulls, omissions and unknown values that the standing rule requires.

`apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsToleranceTest.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The decoder's tolerance (#1307, spec D17): what a client must survive without an error. */
class AchievementsToleranceTest {
    private fun snapshotAt(raw: String) =
        decodeAchievementsPage(
            """{"explorer": {"first_pebble": {"moment_id": "m1", "name": "x", "happened_at": "$raw",
               "emotion_id": "e1", "intensity": 2, "positiveness": 1}}}""",
        ).explorer!!.firstPebble

    @Test
    fun `a snapshot's timestamp decodes at every precision and offset spelling`() {
        val expected = Instant.parse("2026-03-02T09:00:00Z")
        for (raw in listOf(
            "2026-03-02T09:00:00Z",
            "2026-03-02T09:00:00.123Z",
            "2026-03-02T09:00:00.123456+00:00",
            "2026-03-02T10:00:00+01:00",
            "2026-03-02 09:00:00+00",
        )) {
            val at = snapshotAt(raw)!!.happenedAt
            assertEquals(raw, expected.epochSecond, at.epochSecond)
        }
    }

    @Test
    fun `a snapshot without a moment id or a readable time is dropped, the row stays`() {
        val page =
            decodeAchievementsPage(
                """{"pebbles": {"count": 1, "ladder": [1, 10], "levels": [
                    {"threshold": 1, "on": "2026-03-02", "new": false, "pebble": {"name": "gone"}},
                    {"threshold": 10, "on": "2026-03-09", "new": false, "pebble": {"moment_id": "m", "happened_at": "soon"}}]}}""",
            )
        val levels = page.pebbles!!.levels
        assertEquals(listOf(1, 10), levels.map { it.threshold })
        assertTrue(levels.all { it.pebble == null })
    }

    @Test
    fun `explicit nulls read as absent`() {
        val explorer =
            decodeAchievementsPage(
                """{"explorer": {"first_pebble": null, "first_five": null, "six_firsts": null,
                    "twenty_five": null, "rings_done": null, "completion": null}}""",
            ).explorer!!
        assertNull(explorer.firstPebble)
        assertTrue(explorer.firstFive.isEmpty())
        assertTrue(explorer.sixFirsts.isEmpty())
        assertNull(explorer.twentyFive)
        assertTrue(explorer.ringsDone.isEmpty())
        assertNull(explorer.completion)
    }

    @Test
    fun `an omitted section hides its card, an empty body is an empty page`() {
        val page = decodeAchievementsPage("{}")
        assertNull(page.explorer)
        assertNull(page.emotions)
        assertNull(page.domains)
        assertNull(page.pebbles)
        assertNull(page.since)
        assertNull(page.daysKept)
        assertTrue(page.years.isEmpty())
    }

    @Test
    fun `unknown semantic values fall back, never throw`() {
        val page =
            decodeAchievementsPage(
                """{"domains": {"count": 2, "of": 18, "coverage": "plenty", "entered": [], "not_yet": []},
                    "explorer": {"rings_done": ["first_five", "moons"], "six_firsts": [{"kind": "tiny", "done": true}],
                                 "first_five": [{"slug": "first-moon", "done": true}]},
                    "years": [{"year": 2026, "current": true, "listed": [1],
                       "next_step": {"month": 1, "needs": "stars", "days_left": 3, "reachable": true},
                       "months": [${(1..12).joinToString { """{"month": $it, "state": "eclipsed", "family": "awe", "level": 7, "progress": 3.5}""" }}]}]}""",
            )
        assertNull(page.domains!!.coverage)
        assertEquals(setOf(ExplorerRing.FIRST_FIVE), page.explorer!!.ringsDone)
        assertTrue(page.explorer!!.sixFirsts.isEmpty())
        assertTrue(page.explorer!!.firstFive.isEmpty())
        val year = page.years.single()
        assertNull(year.nextStep)
        val january = year.months.first()
        assertEquals(MonthState.QUIET, january.state)
        assertEquals("awe", january.family)
        assertEquals(2, january.level)
        assertEquals(1f, january.progress)
    }

    @Test
    fun `a year without twelve distinct months is dropped, a full one is kept in month order`() {
        val months = (12 downTo 1).joinToString { """{"month": $it, "state": "quiet"}""" }
        val page =
            decodeAchievementsPage(
                """{"years": [{"year": 2026, "months": [{"month": 1}]}, {"year": 2025, "months": [$months]}]}""",
            )
        assertEquals(listOf(2025), page.years.map { it.year })
        assertEquals((1..12).toList(), page.years.single().months.map { it.month })
    }

    @Test
    fun `a seat without an emotion id is dropped and the rest keep the server's seat order`() {
        val page =
            decodeAchievementsPage(
                """{"emotions": {"count": 0, "of": 3, "seats": [
                    {"seat": 2, "emotion_id": "c", "family": "peace"}, {"seat": 1},
                    {"seat": 0, "emotion_id": "a", "family": "joy"}]}}""",
            )
        assertEquals(listOf(0, 2), page.emotions!!.seats.map { it.seat })
    }

    @Test
    fun `a ready completion and a pulled one decode, a broken one is dropped`() {
        val page =
            decodeAchievementsPage(
                """{"emotions": {"count": 38, "of": 38, "seats": [],
                      "completion": {"completed_at": "2026-10-01T08:00:00Z", "offered_from": "2026-10-02T04:00:00Z", "ready": true}},
                    "domains": {"count": 18, "of": 18, "coverage": "all",
                      "completion": {"completed_at": "2026-10-01T08:00:00Z", "offered_from": "2026-10-02T04:00:00Z",
                                     "ready": false, "pulled_at": "2026-10-03T07:30:00.5+00:00"}},
                    "explorer": {"completion": {"ready": true}}}""",
            )
        assertTrue(page.emotions!!.completion!!.ready)
        assertNotNull(page.domains!!.completion!!.pulledAt)
        assertEquals(Coverage.ALL, page.domains!!.coverage)
        assertNull(page.explorer!!.completion)
    }

    @Test
    fun `a top-of-ladder stack has no next, out-of-range values clamp, the days decode`() {
        val page =
            decodeAchievementsPage(
                """{"since": "2026-03-01", "days_kept": 249,
                    "glyph_sales": {"count": 60, "ladder": [1, 5, 10, 25, 50], "levels": []},
                    "pebbles": {"count": 3, "ladder": [1, 10], "next": {"threshold": 10, "left": 7, "progress": 1.7}}}""",
            )
        assertEquals(LocalDate.of(2026, 3, 1), page.since)
        assertEquals(249, page.daysKept)
        assertNull(page.glyphSales!!.next)
        assertEquals(1f, page.pebbles!!.next!!.progress)
    }

    @Test
    fun `an out-of-range valence falls back to medium even, as the Path's does`() {
        val first =
            decodeAchievementsPage(
                """{"explorer": {"first_pebble": {"moment_id": "m", "happened_at": "2026-03-02T09:00:00Z", "intensity": 9}}}""",
            ).explorer!!.firstPebble!!
        assertEquals(Valence.NEUTRAL_MEDIUM, first.valence)
        assertNull(first.emotionId)
    }
}
```

- [ ] **Step 3: Run both to make sure they fail**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.model.achievements.*"`
Expected: FAIL to compile, with `Unresolved reference: decodeAchievementsPage`, `FirstStep`, `StoneKind` and so on.

- [ ] **Step 4: Write the models**

`apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsPage.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import java.time.Instant
import java.time.LocalDate

/**
 * The achievements page as Android draws it (#1307, spec §3.3). Every number, order, level, family
 * and flag is the server's. A null section hides its card (spec §4, resilience). Display models
 * only: nothing here is computed from counts.
 */
data class AchievementsPage(
    val since: LocalDate?,
    val daysKept: Int?,
    val explorer: ExplorerSection?,
    val emotions: EmotionsSection?,
    val domains: DomainsSection?,
    val pebbles: CountingSection?,
    val glyphsCarved: CountingSection?,
    val glyphSales: CountingSection?,
    val years: List<AchievementYear>,
)

/** The crossing pebble's snapshot (D15): enough to draw its seal and open its moment. */
data class PebbleSnapshot(
    val momentId: String,
    val name: String?,
    val happenedAt: Instant,
    val emotionId: String?,
    val valence: Valence,
)

/** The first five, in the Home checklist's order. */
enum class FirstStep(
    val slug: String,
) {
    MOMENT("first-moment"),
    PEBBLE("first-pebble"),
    COLLECTION("first-collection"),
    SOUL("first-soul"),
    GLYPH("first-glyph"),
    ;

    companion object {
        fun of(slug: String?): FirstStep? = entries.firstOrNull { it.slug == slug }
    }
}

enum class SubjectKind(
    val key: String,
) {
    MOMENT("moment"),
    PEBBLE("pebble"),
    COLLECTION("collection"),
    SOUL("soul"),
    GLYPH("glyph"),
    ;

    companion object {
        fun of(key: String?): SubjectKind? = entries.firstOrNull { it.key == key }
    }
}

/** What a first-five task made. For a moment or a pebble, [id] is the moment's id. */
data class FirstSubject(
    val kind: SubjectKind,
    val id: String,
    val name: String?,
)

data class FirstFiveItem(
    val step: FirstStep,
    val done: Boolean,
    val on: LocalDate?,
    val subject: FirstSubject?,
    val isNew: Boolean,
)

/** The six firsts (D5), in the catalog's order: three intensities, then three valences. */
enum class StoneKind(
    val slug: String,
) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    LOWLIGHT("lowlight"),
    NEUTRAL("neutral"),
    HIGHLIGHT("highlight"),
    ;

    companion object {
        fun of(slug: String?): StoneKind? = entries.firstOrNull { it.slug == slug }
    }
}

data class SixFirst(
    val kind: StoneKind,
    val done: Boolean,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

data class TwentyFive(
    val count: Int,
    val done: Boolean,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

/** The wheel's five rings, from the centre out (D6). */
enum class ExplorerRing(
    val slug: String,
) {
    FIRST_FIVE("first_five"),
    SIX_FIRSTS("six_firsts"),
    TWENTY_FIVE("twenty_five"),
    DOMAINS("domains"),
    EMOTIONS("emotions"),
    ;

    companion object {
        fun of(slug: String?): ExplorerRing? = entries.firstOrNull { it.slug == slug }
    }
}

/** A set made whole (D9). [ready] is the server's clock, so no client compares times. */
data class Completion(
    val completedAt: Instant,
    val offeredFrom: Instant,
    val ready: Boolean,
    val pulledAt: Instant?,
)

data class ExplorerSection(
    val firstPebble: PebbleSnapshot?,
    val firstFive: List<FirstFiveItem>,
    val sixFirsts: List<SixFirst>,
    val twentyFive: TwentyFive?,
    val ringsDone: Set<ExplorerRing>,
    val completion: Completion?,
)

/** One of the rosace's seats: [family] is a category slug, which may be one Android does not know. */
data class EmotionSeat(
    val seat: Int,
    val emotionId: String,
    val family: String?,
    val met: Boolean,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

data class EmotionsSection(
    val count: Int,
    val of: Int,
    val seats: List<EmotionSeat>,
    val completion: Completion?,
)

/** D11's tiers; an unknown tier decodes to null and the card shows no sentence. */
enum class Coverage(
    val slug: String,
) {
    NONE("none"),
    FEW("few"),
    THIRD("third"),
    HALF("half"),
    MOST("most"),
    ALL("all"),
    ;

    companion object {
        fun of(slug: String?): Coverage? = entries.firstOrNull { it.slug == slug }
    }
}

data class DomainEntry(
    val domainId: String,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

/** [entered] is newest first, as the server sends it; the band reads it backwards. */
data class DomainsSection(
    val count: Int,
    val of: Int,
    val coverage: Coverage?,
    val entered: List<DomainEntry>,
    val notYet: List<String>,
    val completion: Completion?,
)

data class StackLevel(
    val threshold: Int,
    val on: LocalDate?,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

data class NextLevel(
    val threshold: Int,
    val left: Int,
    val progress: Float,
)

/** Pebbles, glyphs carved or glyph sales. [next] is null at the top of the ladder. */
data class CountingSection(
    val count: Int,
    val levels: List<StackLevel>,
    val next: NextLevel?,
    val ladder: List<Int>,
    val thenEvery: Int?,
)

/** An unknown state draws as [QUIET] (spec §4). */
enum class MonthState(
    val slug: String,
) {
    UPCOMING("upcoming"),
    QUIET("quiet"),
    KEPT("kept"),
    ;

    companion object {
        fun of(slug: String?): MonthState = entries.firstOrNull { it.slug == slug } ?: QUIET
    }
}

/** One month of a year disc (D12). [level] is 0..2 and [progress] 0..1, clamped on decode. */
data class AchievementMonth(
    val month: Int,
    val state: MonthState,
    val family: String?,
    val momentDays: Int,
    val pebbleDays: Int,
    val fullWeeks: Int,
    val weeksFilled: Int,
    val level: Int,
    val progress: Float,
)

enum class Needs(
    val slug: String,
) {
    MOMENTS("moments"),
    PEBBLES("pebbles"),
    ;

    companion object {
        fun of(slug: String?): Needs? = entries.firstOrNull { it.slug == slug }
    }
}

data class NextStep(
    val month: Int,
    val needs: Needs,
    val daysLeft: Int,
    val reachable: Boolean,
)

/** One disc. [months] are always January to December; [listed] is the table, newest first. */
data class AchievementYear(
    val year: Int,
    val current: Boolean,
    val months: List<AchievementMonth>,
    val listed: List<Int>,
    val nextStep: NextStep?,
)

/** The cards a sheet marks seen (`mark_achievements_seen`'s `p_card`). */
enum class AchievementCard(
    val slug: String,
) {
    EXPLORER("explorer"),
    EMOTIONS("emotions"),
    DOMAINS("domains"),
    PEBBLES("pebbles"),
    YEAR("year"),
}

/** A set whose completion is pulled (`pull_ceremony`'s `p_stack`), with its `set_complete` slug. */
enum class CompletionStack(
    val slug: String,
    val catalogSlug: String,
) {
    EMOTIONS("emotions", "complete-emotions"),
    DOMAINS("domains", "complete-domains"),
    SIX_FIRSTS("six_firsts", "complete-six-firsts"),
}
```

- [ ] **Step 5: Write the mapping**

`apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/AchievementsMapping.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** The body as the app reads it: decode, then map (#1307). Throws only on a body that is not JSON. */
fun decodeAchievementsPage(body: String): AchievementsPage = AchievementsJson.decodeFromString<AchievementsPageDto>(body).toModel()

fun AchievementsPageDto.toModel(): AchievementsPage =
    AchievementsPage(
        since = since?.let(::parseDay),
        daysKept = daysKept,
        explorer = explorer?.toModel(),
        emotions = emotions?.toModel(),
        domains = domains?.toModel(),
        pebbles = pebbles?.toModel(),
        glyphsCarved = glyphsCarved?.toModel(),
        glyphSales = glyphSales?.toModel(),
        years = years.orEmpty().mapNotNull { it.toModel() },
    )

private val SHORT_OFFSET = Regex("""([+-]\d{2})$""")

/**
 * A timestamp crossing the boundary, read tolerantly (standing rule): `Z` or `+00:00`, any
 * fraction, and Postgres's own `2026-03-02 09:00:00+00` spelling. Null when unreadable.
 */
internal fun parseInstant(raw: String): Instant? {
    val iso = raw.trim().replace(' ', 'T').replace(SHORT_OFFSET, "$1:00")
    return try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }
}

internal fun parseDay(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (e: DateTimeParseException) {
        null
    }

private fun PebbleSnapshotDto.toModel(): PebbleSnapshot? {
    val id = momentId ?: return null
    val at = happenedAt?.let(::parseInstant) ?: return null
    return PebbleSnapshot(
        momentId = id,
        name = name,
        happenedAt = at,
        emotionId = emotionId,
        valence = Valence.fromOrDefault(positiveness ?: 0, intensity ?: 2),
    )
}

private fun CompletionDto.toModel(): Completion? {
    val completed = completedAt?.let(::parseInstant) ?: return null
    val offered = offeredFrom?.let(::parseInstant) ?: return null
    return Completion(completed, offered, ready, pulledAt?.let(::parseInstant))
}

private fun ExplorerDto.toModel(): ExplorerSection =
    ExplorerSection(
        firstPebble = firstPebble?.toModel(),
        firstFive =
            firstFive.orEmpty().mapNotNull { item ->
                FirstStep.of(item.slug)?.let { step ->
                    FirstFiveItem(
                        step = step,
                        done = item.done,
                        on = item.on?.let(::parseDay),
                        subject =
                            item.subject?.let { s ->
                                val kind = SubjectKind.of(s.kind)
                                val id = s.id
                                if (kind == null || id == null) null else FirstSubject(kind, id, s.name)
                            },
                        isNew = item.isNew,
                    )
                }
            },
        sixFirsts =
            sixFirsts.orEmpty().mapNotNull { item ->
                StoneKind.of(item.kind)?.let { SixFirst(it, item.done, item.pebble?.toModel(), item.isNew) }
            },
        twentyFive = twentyFive?.let { TwentyFive(it.count, it.done, it.pebble?.toModel(), it.isNew) },
        ringsDone = ringsDone.mapNotNull(ExplorerRing::of).toSet(),
        completion = completion?.toModel(),
    )

private fun EmotionsDto.toModel(): EmotionsSection =
    EmotionsSection(
        count = count,
        of = of,
        seats =
            seats
                .mapNotNull { s ->
                    s.emotionId?.let { EmotionSeat(s.seat, it, s.family, s.met, s.pebble?.toModel(), s.isNew) }
                }.sortedBy { it.seat },
        completion = completion?.toModel(),
    )

private fun DomainsDto.toModel(): DomainsSection =
    DomainsSection(
        count = count,
        of = of,
        coverage = Coverage.of(coverage),
        entered = entered.mapNotNull { e -> e.domainId?.let { DomainEntry(it, e.pebble?.toModel(), e.isNew) } },
        notYet = notYet,
        completion = completion?.toModel(),
    )

private fun CountingDto.toModel(): CountingSection =
    CountingSection(
        count = count,
        levels =
            levels.map { l ->
                StackLevel(l.threshold, l.on?.let(::parseDay), l.pebble?.toModel(), l.isNew)
            },
        next = next?.let { NextLevel(it.threshold, it.left, it.progress.toFloat().coerceIn(0f, 1f)) },
        ladder = ladder,
        thenEvery = thenEvery,
    )

/** Twelve distinct months or nothing: the disc's geometry has twelve spokes. */
private fun YearDto.toModel(): AchievementYear? {
    val byMonth =
        months
            .filter { it.month in 1..12 }
            .associateBy { it.month }
    if (byMonth.size != 12) return null
    return AchievementYear(
        year = year,
        current = current,
        months =
            (1..12).map { m ->
                val d = byMonth.getValue(m)
                AchievementMonth(
                    month = m,
                    state = MonthState.of(d.state),
                    family = d.family,
                    momentDays = d.momentDays,
                    pebbleDays = d.pebbleDays,
                    fullWeeks = d.fullWeeks,
                    weeksFilled = d.weeksFilled,
                    level = d.level.coerceIn(0, 2),
                    progress = d.progress.toFloat().coerceIn(0f, 1f),
                )
            },
        listed = listed.filter { it in 1..12 },
        nextStep =
            nextStep?.let { n ->
                Needs.of(n.needs)?.let { NextStep(n.month, it, n.daysLeft, n.reachable) }
            },
    )
}
```

The non-null lists need no `orEmpty()`: `coerceInputValues` already turns an explicit `null` into the declared default. Only the three nullable fields (`first_five`, `six_firsts`, `years`) are read through `orEmpty()`, because their DTO type is nullable.

- [ ] **Step 6: Run the tests to make sure they pass**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.model.achievements.*"`
Expected: PASS: 10 replay tests, 10 tolerance tests and 3 DTO tests.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/ apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/
git commit -m "feat(android): map the achievements payload into display models, tolerantly"
```

### Task 3: `celebrate`, and only the celebrated rows chain

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/AchievementsService.kt`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsDtoDecodingTest.kt` (drop the local `CheckRowDto`)
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/data/AchievementCelebrationTest.kt`

- [ ] **Step 1: Write the failing test**

`apps/android/app/src/test/kotlin/app/pbbls/android/core/data/AchievementCelebrationTest.kt`:

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.model.achievements.AchievementPayloads
import app.pbbls.android.core.model.achievements.AchievementsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unlock moment shows only what the server says to celebrate (#1307, spec §4; D7, D8). The
 * payload is Alice's first check over a heavy first pebble: every contribution and the
 * heavy-crossed first level arrive with `celebrate = false`.
 */
class AchievementCelebrationTest {
    private val rows = AchievementsJson.decodeFromString<List<AchievementCheckResult>>(AchievementPayloads.read("check-achievements"))

    @Test
    fun `no contribution and no heavy-crossed level celebrates`() {
        val celebrated = rows.celebrated().map { it.slug }
        assertTrue(celebrated.none { it.startsWith("emotion-first-") || it.startsWith("domain-first-") || it.startsWith("first-stone-") })
        assertFalse("pebble-count-1" in celebrated)
    }

    @Test
    fun `celebrated keeps exactly the flagged rows, in order`() {
        assertEquals(rows.filter { it.celebrate }, rows.celebrated())
    }

    @Test
    fun `a row without the key is silent, the safe side of D7`() {
        val row = AchievementsJson.decodeFromString<AchievementCheckResult>("""{"slug": "pebble-count-10", "karma_granted": 5}""")
        assertFalse(row.celebrate)
        assertTrue(listOf(row).celebrated().isEmpty())
    }
}
```

`AchievementPayloads` is `internal` in the test source set of the same module, so the `core.data` test can use it.

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.data.AchievementCelebrationTest"`
Expected: FAIL to compile, with `Unresolved reference: celebrate` and `celebrated`.

- [ ] **Step 3: Add the column, decode it through `AchievementsJson`, and filter the chain**

In `AchievementsService.kt`, replace the `AchievementCheckResult` class with:

```kotlin
/**
 * One newly unlocked badge as `check_achievements()` reports it: the karma is what the server
 * actually emitted at unlock time, never the catalog's current value. [celebrate] is the server's
 * verdict (#1305 D7, D8): false for contributions (emotion, domain, six firsts) and for a level
 * crossed by a heavy pebble. Absent reads as false, the safe side.
 */
@Serializable
data class AchievementCheckResult(
    val slug: String,
    @SerialName("karma_granted")
    val karmaGranted: Int = 0,
    val celebrate: Boolean = false,
)

/** The rows the unlock moment shows (#1307, spec §4): only those the server flags. */
internal fun List<AchievementCheckResult>.celebrated(): List<AchievementCheckResult> = filter { it.celebrate }
```

Replace `check()` with a decode through the shared, tolerant decoder, so the test above reads the payload exactly as production does:

```kotlin
        /**
         * Runs the evaluation. SETOF-returning RPC, so PostgREST yields an array (empty = nothing
         * newly unlocked). Decoded by [AchievementsJson], as the replay tests decode it.
         */
        suspend fun check(): List<AchievementCheckResult> =
            AchievementsJson.decodeFromString<List<AchievementCheckResult>>(
                supabase.client.postgrest
                    .rpc("check_achievements")
                    .data,
            )
```

In `fireCheck()`, change the first two lines of the `try` to:

```kotlin
                    // Only what the server says to celebrate (#1307): a first sadness, a heavy
                    // first stone and every contribution are recorded silently.
                    val results = check().celebrated()
                    if (results.isEmpty()) return@launch
```

Add the import `import app.pbbls.android.core.model.achievements.AchievementsJson`.

In `AchievementsDtoDecodingTest.kt`, delete the private `CheckRowDto` and change the third test to decode the real type:

```kotlin
    @Test
    fun `the check rows carry celebrate`() {
        val rows =
            AchievementsJson.decodeFromString<List<app.pbbls.android.core.data.AchievementCheckResult>>(
                AchievementPayloads.read("check-achievements"),
            )
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.any { !it.celebrate })
    }
```

- [ ] **Step 4: Run the tests to make sure they pass**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.data.AchievementCelebrationTest" --tests "app.pbbls.android.core.model.achievements.*" --tests "app.pbbls.android.core.data.AchievementNotificationServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/data/AchievementsService.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/core/data/AchievementCelebrationTest.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/AchievementsDtoDecodingTest.kt
git commit -m "feat(android): celebrate only the unlocks check_achievements flags"
```

### Task 4: The seam — read, cache, seen marks, ceremony pull

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/CeremonyPull.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/AchievementsService.kt`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeAchievementsService.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/CeremonyPullTest.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeAchievementsServiceTest.kt`

- [ ] **Step 1: Write the failing tests**

`apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/CeremonyPullTest.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import org.junit.Assert.assertEquals
import org.junit.Test

/** `pull_ceremony`'s answer (#1305 Task 12): one row is a pull; no row is "already pulled", never success. */
class CeremonyPullTest {
    @Test
    fun `one row is a pull with the karma the server paid`() {
        val rows = AchievementsJson.decodeFromString<List<CeremonyRowDto>>("""[{"stack": "emotions", "karma_granted": 25}]""")
        assertEquals(CeremonyPull.Pulled(CompletionStack.EMOTIONS, 25), ceremonyPullOf(CompletionStack.EMOTIONS, rows))
    }

    @Test
    fun `no row is already pulled`() {
        assertEquals(CeremonyPull.AlreadyPulled, ceremonyPullOf(CompletionStack.DOMAINS, emptyList()))
    }
}
```

`apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeAchievementsServiceTest.kt`:

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.decodeAchievementsPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.ZoneId

/** The fake's page, cache, seen and pull behave as the ViewModel tests rely on (#1307). */
class FakeAchievementsServiceTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val page = decodeAchievementsPage("{}")

    @Test
    fun `it serves the armed page, fails once when armed, and records calls`() =
        runTest {
            val fake = FakeAchievementsService(served = page)
            assertSame(page, fake.page(zone))
            fake.failNextPage = IOException("offline")
            try {
                fake.page(zone)
                fail("expected the armed failure")
            } catch (e: IOException) {
                assertEquals("offline", e.message)
            }
            assertSame(page, fake.page(zone))
            assertEquals(3, fake.pageCalls)
        }

    @Test
    fun `the kept page is null until armed`() =
        runTest {
            val fake = FakeAchievementsService()
            assertNull(fake.cachedPage(zone))
            fake.kept = page
            assertSame(page, fake.cachedPage(zone))
        }

    @Test
    fun `seen marks and pulls are recorded in order`() =
        runTest {
            val fake = FakeAchievementsService(pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 10))
            fake.markSeen(AchievementCard.EXPLORER)
            fake.markSeen(AchievementCard.EMOTIONS)
            assertEquals(listOf(AchievementCard.EXPLORER, AchievementCard.EMOTIONS), fake.seen)
            assertEquals(CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 10), fake.pullCeremony(CompletionStack.SIX_FIRSTS, zone))
            assertEquals(listOf(CompletionStack.SIX_FIRSTS), fake.pulls)
        }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.model.achievements.CeremonyPullTest" --tests "app.pbbls.android.testing.FakeAchievementsServiceTest"`
Expected: FAIL to compile, with `Unresolved reference: CeremonyRowDto`, `ceremonyPullOf` and `served`.

- [ ] **Step 3: Write the outcome type**

`apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/CeremonyPull.kt`:

```kotlin
package app.pbbls.android.core.model.achievements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of `pull_ceremony(p_stack, p_tz)` (#1305 Task 12). */
@Serializable
data class CeremonyRowDto(
    val stack: String = "",
    @SerialName("karma_granted")
    val karmaGranted: Int = 0,
)

/** What a pull did. `not_ready` is not here: it arrives as `DataError.Conflict("not_ready")`. */
sealed interface CeremonyPull {
    data class Pulled(
        val stack: CompletionStack,
        val karmaGranted: Int,
    ) : CeremonyPull

    /** No row: another pull (or another device) got there first. Never read as success. */
    data object AlreadyPulled : CeremonyPull
}

fun ceremonyPullOf(
    stack: CompletionStack,
    rows: List<CeremonyRowDto>,
): CeremonyPull = rows.firstOrNull()?.let { CeremonyPull.Pulled(stack, it.karmaGranted) } ?: CeremonyPull.AlreadyPulled
```

- [ ] **Step 4: Extend the seam and the service**

In `AchievementsService.kt`, replace the `AchievementsServicing` interface with:

```kotlin
interface AchievementsServicing {
    suspend fun loadCatalog(): List<AchievementRecord>

    suspend fun loadUnlocks(): List<AchievementUnlockRecord>

    suspend fun checkIgnoringFailure()

    fun fireCheck()

    /** `get_achievements(p_tz)` (#1307), decoded and kept on the device for the signed-in user. Throws. */
    suspend fun page(zone: ZoneId): AchievementsPage

    /** The device's last copy for the signed-in user, or null: display only, never instead of [page]. */
    suspend fun cachedPage(zone: ZoneId): AchievementsPage?

    /** `mark_achievements_seen(p_card)`, fire-and-forget: it outlives the sheet that sent it. */
    fun markSeen(card: AchievementCard)

    /** `pull_ceremony(p_stack, p_tz)`. Throws, `not_ready` included (a `DataError.Conflict`). */
    suspend fun pullCeremony(
        stack: CompletionStack,
        zone: ZoneId,
    ): CeremonyPull
}
```

Give the class the wraps' disk cache:

```kotlin
@Singleton
class AchievementsService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        private val notify: AchievementNotificationService,
        @ApplicationScope private val scope: CoroutineScope,
        private val disk: WrapDiskCaching,
    ) : AchievementsServicing {
```

The disk cache is the wraps' (`WrapDiskCaching`, #1207 §7.1). It is reused rather than copied because its contract is exactly this one:
- one file per page under the signed-in user and the zone;
- never backed up;
- wiped by `RootViewModel` whenever the device resolves signed out;
- a format bump that drops old files.

The page key `achievements-page` never collides with a wrap's `strip`, `week-…` or `month-…`.

Add these members before the `companion object`:

```kotlin
        override suspend fun page(zone: ZoneId): AchievementsPage {
            val body =
                supabase.client.postgrest
                    .rpc("get_achievements", buildJsonObject { put("p_tz", zone.id) })
                    .data
            val page = decodeAchievementsPage(body)
            // Kept only once it has decoded, so the device never holds a body it cannot read.
            supabase.session
                ?.user
                ?.id
                ?.let { owner -> disk.write(owner, zone, CACHE_PAGE, body) }
            return page
        }

        override suspend fun cachedPage(zone: ZoneId): AchievementsPage? {
            val owner = supabase.session?.user?.id ?: return null
            val body = disk.read(owner, zone, CACHE_PAGE) ?: return null
            return try {
                decodeAchievementsPage(body)
            } catch (e: SerializationException) {
                Log.w(TAG, "kept achievements page no longer decodes, read as a miss", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "kept achievements page no longer decodes, read as a miss", e)
                null
            }
        }

        override fun markSeen(card: AchievementCard) {
            // @ApplicationScope has no handler: catch inside (DispatchersModule's KDoc).
            scope.launch {
                runCatchingCancellable {
                    supabase.client.postgrest.rpc("mark_achievements_seen", buildJsonObject { put("p_card", card.slug) })
                }.onFailure { Log.w(TAG, "mark_achievements_seen(${card.slug}) failed; the marks stay until the next look", it) }
            }
        }

        override suspend fun pullCeremony(
            stack: CompletionStack,
            zone: ZoneId,
        ): CeremonyPull {
            val body =
                supabase.client.postgrest
                    .rpc(
                        "pull_ceremony",
                        buildJsonObject {
                            put("p_stack", stack.slug)
                            put("p_tz", zone.id)
                        },
                    ).data
            return ceremonyPullOf(stack, AchievementsJson.decodeFromString<List<CeremonyRowDto>>(body))
        }
```

In the `companion object`, add `const val CACHE_PAGE = "achievements-page"`.

Add these imports:

```kotlin
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CeremonyRowDto
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.ceremonyPullOf
import app.pbbls.android.core.model.achievements.decodeAchievementsPage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId
```

- [ ] **Step 5: Extend the fake**

Replace `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeAchievementsService.kt` with:

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.data.AchievementUnlockRecord
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.decodeAchievementsPage
import kotlinx.coroutines.CompletableDeferred
import java.time.ZoneId

/**
 * In-memory [AchievementsServicing] (#849, #1307): the grid's catalog and unlocks, and the page's
 * read, device copy, seen marks and pulls, drivable without a live project. See
 * [PebblesTestHarness] for where these live and why.
 */
class FakeAchievementsService(
    /** What [loadCatalog] returns on a successful call. Settable mid-test. */
    var catalog: List<AchievementRecord> = emptyList(),
    /** What [loadUnlocks] returns on a successful call. Settable mid-test. */
    var unlocks: List<AchievementUnlockRecord> = emptyList(),
    /** What [page] returns. An empty page by default: every card hidden. */
    var served: AchievementsPage = decodeAchievementsPage("{}"),
    /** What [cachedPage] returns: the device's copy. */
    var kept: AchievementsPage? = null,
    /** What [pullCeremony] returns when it does not throw. */
    var pullResult: CeremonyPull = CeremonyPull.AlreadyPulled,
) : AchievementsServicing {
    var catalogLoadCount = 0
        private set

    var checkCount = 0
        private set

    var fireCheckCount = 0
        private set

    var pageCalls = 0
        private set

    /** Every call in order: "check" and "page", so a test can assert the retroactive grant runs first. */
    val calls = mutableListOf<String>()

    val seen = mutableListOf<AchievementCard>()

    val pulls = mutableListOf<CompletionStack>()

    /** When set, [page] waits for it: a test can observe what shows before the read answers. */
    var pageGate: CompletableDeferred<Unit>? = null

    private val armed = ArmedFailure()
    private val pageFailure = ArmedFailure()
    private val pullFailure = ArmedFailure()

    /**
     * Thrown by the next [loadCatalog] or [loadUnlocks], then cleared.
     *
     * Deliberately not armed on [checkIgnoringFailure]: its contract is that it
     * swallows everything, so a fake that could throw from it would let a test
     * assert behaviour the real service cannot produce.
     */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    /** Thrown by the next [page], then cleared. */
    var failNextPage: Exception?
        get() = pageFailure.next
        set(value) {
            pageFailure.next = value
        }

    /** Thrown by the next [pullCeremony], then cleared. */
    var failNextPull: Exception?
        get() = pullFailure.next
        set(value) {
            pullFailure.next = value
        }

    override suspend fun loadCatalog(): List<AchievementRecord> {
        catalogLoadCount += 1
        armed.fire()
        return catalog
    }

    override suspend fun loadUnlocks(): List<AchievementUnlockRecord> {
        armed.fire()
        return unlocks
    }

    override suspend fun checkIgnoringFailure() {
        checkCount += 1
        calls += "check"
    }

    override fun fireCheck() {
        fireCheckCount += 1
    }

    override suspend fun page(zone: ZoneId): AchievementsPage {
        pageCalls += 1
        calls += "page"
        pageGate?.await()
        pageFailure.fire()
        return served
    }

    override suspend fun cachedPage(zone: ZoneId): AchievementsPage? = kept

    override fun markSeen(card: AchievementCard) {
        seen += card
    }

    override suspend fun pullCeremony(
        stack: CompletionStack,
        zone: ZoneId,
    ): CeremonyPull {
        pulls += stack
        pullFailure.fire()
        return pullResult
    }
}
```

- [ ] **Step 6: Run the whole unit suite**

The constructor change reaches Hilt and every test that builds the fake.

Run: `cd apps/android && ./gradlew testDebugUnitTest`
Expected: PASS. `ServiceFakesTest` still pins `ArmedFailure`'s clear-once rule, and Hilt resolves `WrapDiskCaching` from `ServiceBindings`.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/model/achievements/CeremonyPull.kt \
  apps/android/app/src/main/kotlin/app/pbbls/android/core/data/AchievementsService.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeAchievementsService.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeAchievementsServiceTest.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/core/model/achievements/CeremonyPullTest.kt
git commit -m "feat(android): read, keep, mark seen and pull the achievements page through one seam"
```

### Task 5: Catalog copy for the new families

The profile shelf and the unlock moment title a badge through `achievementTitle(record)`. Without these arms:
- `first_stone` and `set_complete` show their raw slug;
- so do `first_moment` and `first_pebble`, which `celebrate` now lets through to the moment.

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/AchievementCopy.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `apps/android/app/src/main/res/values-fr/strings.xml`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/AchievementCopyNewFamiliesTest.kt`

- [ ] **Step 1: Write the failing test**

`apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/AchievementCopyNewFamiliesTest.kt`:

```kotlin
package app.pbbls.android.core.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.pbbls.android.core.data.AchievementRecord
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The four families the page adds are titled from strings, never by their slug (#1307). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AchievementCopyNewFamiliesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun record(
        slug: String,
        family: String,
    ) = AchievementRecord(id = slug, slug = slug, family = family, sortOrder = 0, karmaReward = 0, isActive = true)

    @Test
    fun `the new families read as words`() {
        compose.setContent {
            Text(achievementTitle(record("first-stone-lowlight", "first_stone")))
            Text(achievementTitle(record("complete-emotions", "set_complete")))
            Text(achievementTitle(record("first-moment", "first_moment")))
            Text(achievementTitle(record("first-pebble", "first_pebble")))
        }
        compose.onNodeWithText("A first lowlight").assertExists()
        compose.onNodeWithText("Every emotion met").assertExists()
        compose.onNodeWithText("First moment").assertExists()
        compose.onNodeWithText("First pebble").assertExists()
    }

    @Test
    fun `an unknown slug inside a known family still falls back to the slug`() {
        compose.setContent { Text(achievementTitle(record("first-stone-moon", "first_stone"))) }
        compose.onNodeWithText("first-stone-moon").assertExists()
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.ui.AchievementCopyNewFamiliesTest"`
Expected: FAIL: `onNodeWithText("A first lowlight")` finds no node, because the slug shows.

- [ ] **Step 3: Add the strings**

In `apps/android/app/src/main/res/values/strings.xml`, after `achievement_family_first_soul_description`, add:

```xml
    <!-- The achievements page's families (#1307): the first five's two new tasks, the six firsts, the completions. -->
    <string name="achievement_family_first_moment_title">First moment</string>
    <string name="achievement_family_first_pebble_title">First pebble</string>
    <string name="achievement_first_stone_small_title">A first small stone</string>
    <string name="achievement_first_stone_medium_title">A first medium stone</string>
    <string name="achievement_first_stone_large_title">A first large stone</string>
    <string name="achievement_first_stone_lowlight_title">A first lowlight</string>
    <string name="achievement_first_stone_neutral_title">A first neutral stone</string>
    <string name="achievement_first_stone_highlight_title">A first highlight</string>
    <string name="achievement_complete_emotions_title">Every emotion met</string>
    <string name="achievement_complete_domains_title">Every part of your life</string>
    <string name="achievement_complete_six_firsts_title">The six firsts</string>
```

In `apps/android/app/src/main/res/values-fr/strings.xml`, at the same place:

```xml
    <!-- Les familles de la page des succès (#1307). -->
    <string name="achievement_family_first_moment_title">Premier moment</string>
    <string name="achievement_family_first_pebble_title">Premier galet</string>
    <string name="achievement_first_stone_small_title">Un premier petit galet</string>
    <string name="achievement_first_stone_medium_title">Un premier galet moyen</string>
    <string name="achievement_first_stone_large_title">Un premier grand galet</string>
    <string name="achievement_first_stone_lowlight_title">Un premier creux</string>
    <string name="achievement_first_stone_neutral_title">Un premier galet neutre</string>
    <string name="achievement_first_stone_highlight_title">Un premier temps fort</string>
    <string name="achievement_complete_emotions_title">Toutes les émotions rencontrées</string>
    <string name="achievement_complete_domains_title">Chaque part de ta vie</string>
    <string name="achievement_complete_six_firsts_title">Les six premières</string>
```

- [ ] **Step 4: Add the arms**

In `AchievementCopy.kt`, add four arms to `achievementTitle`'s `when`, before `else`:

```kotlin
        "first_moment" -> stringResource(R.string.achievement_family_first_moment_title)
        "first_pebble" -> stringResource(R.string.achievement_family_first_pebble_title)
        "first_stone" -> FIRST_STONE_TITLES[record.slug]?.let { stringResource(it) } ?: record.slug
        "set_complete" -> SET_COMPLETE_TITLES[record.slug]?.let { stringResource(it) } ?: record.slug
```

Add the two maps under the file's first comment block:

```kotlin
/** The six firsts (#1305 D5) by slug; a slug this client does not know keeps the slug. */
private val FIRST_STONE_TITLES: Map<String, Int> =
    mapOf(
        "first-stone-small" to R.string.achievement_first_stone_small_title,
        "first-stone-medium" to R.string.achievement_first_stone_medium_title,
        "first-stone-large" to R.string.achievement_first_stone_large_title,
        "first-stone-lowlight" to R.string.achievement_first_stone_lowlight_title,
        "first-stone-neutral" to R.string.achievement_first_stone_neutral_title,
        "first-stone-highlight" to R.string.achievement_first_stone_highlight_title,
    )

/** The three completions a ceremony pulls (#1305 D9). */
private val SET_COMPLETE_TITLES: Map<String, Int> =
    mapOf(
        "complete-emotions" to R.string.achievement_complete_emotions_title,
        "complete-domains" to R.string.achievement_complete_domains_title,
        "complete-six-firsts" to R.string.achievement_complete_six_firsts_title,
    )
```

- [ ] **Step 5: Run the test and the parity check**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.ui.AchievementCopyNewFamiliesTest" --tests "app.pbbls.android.core.ui.LocalizationParityTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/AchievementCopy.kt apps/android/app/src/main/res/values/strings.xml \
  apps/android/app/src/main/res/values-fr/strings.xml apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/AchievementCopyNewFamiliesTest.kt
git commit -m "feat(android): title the six firsts, the completions and the first five by name"
```

### Task 6: Swap in the dumped payloads, verify, open the PR

- [ ] **Step 1: Swap the fixtures for the harness's dumps**

Do this once back-end part 2 and part 3 are merged and `packages/supabase/scripts/oracles/achievements/payloads/` holds `check-achievements.json`, `get-achievements.json`, `get-achievements-year.json` and `get-achievements-seen.json`. If they have not landed when this part is ready, skip this step, open the PR, and do it as the first commit of part 2. Never let the hand-built copies outlive the dumps.

Delete the hand-built copies:

```bash
git rm apps/android/app/src/test/resources/get-achievements.json apps/android/app/src/test/resources/get-achievements-year.json \
  apps/android/app/src/test/resources/get-achievements-seen.json apps/android/app/src/test/resources/check-achievements.json
```

In `apps/android/app/build.gradle.kts`, extend the test `sourceSets` block and its comment:

```kotlin
    // The wraps' decoding tests (#1207) and the achievements' (#1307) replay the server's own
    // payloads, captured by packages/supabase/scripts/verify-path-wraps.ts (PATH_WRAPS_DUMP=1) and
    // verify-achievements.ts (ACHIEVEMENTS_DUMP=1): one copy each, owned by the harness that
    // produced it, never a hand-edited duplicate here.
    sourceSets {
        getByName("test") {
            resources.srcDir("../../../packages/supabase/scripts/oracles/path-wrap/payloads")
            resources.srcDir("../../../packages/supabase/scripts/oracles/achievements/payloads")
        }
    }
```

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.model.achievements.*" --tests "app.pbbls.android.core.data.AchievementCelebrationTest"`
Expected: PASS, unchanged: the tests assert only what the harness asserts.

If one fails, the client and the server disagree. Fix the mapping, never the dumped file.

```bash
git add apps/android/app/build.gradle.kts
git commit -m "test(android): replay the dumped achievements payloads instead of hand-built copies"
```

- [ ] **Step 2: Run the part's gate**

Run, from `apps/android`:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew testDebugUnitTest lint ktlintCheck assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Lint reports no new finding; every new string has its caller in `AchievementCopy.kt`.

- [ ] **Step 3: Open the PR**

Open it with `gh stack`, following the `gh-stack` skill, titled `feat(android): read the achievements page from get_achievements`.

The body:
- opens with `Part of #1307`;
- lists the key files;
- notes that the ViewModel and the UI land in parts 3–4;
- ends with the attribution line.

Propose inheriting #1307's labels and milestone (M65 · Achievements), and confirm them with the maintainer before opening.

This part changes no UI, so it carries no Lab Note: delete the section, and add `no-lab-note` if the reminder comments.

---
# Part 2 — the figures (branch `feat/1307-android-achievements-figures`, stacked on part 1)

Add `feat/1307-android-achievements-figures` on top of part 1 with `gh stack`, following the `gh-stack` skill. Parts 3 and 4 stack the same way, each on the one before.

The board's units are CSS pixels, and they become dp one for one. Each figure is a pure geometry object, tested on the JVM, plus a stateless `Canvas` composable that draws it. Colour is never decided by a figure: ink comes from `PebblesTheme.spine.colors`, and hues from the emotion palettes through `AchievementLookups`.

### Task 7: The page's type ranks

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/SpineTypography.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/designsystem/AchievementTypeRanksTest.kt`

The board sets numerals in Ysabeau Office, which the app does not bundle. The ranks below use the bundled Ysabeau with its lining figures (`lnum`), as the Spine does.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The achievements board's ranks (#1307), so features/achievements never names a size (ThemeLiteralsTest). */
class AchievementTypeRanksTest {
    @Test
    fun `the ranks carry the board's sizes`() {
        assertEquals(78.sp, SpineTypography.daysKept.fontSize)
        assertEquals(11.5.sp, SpineTypography.cardBand.fontSize)
        assertEquals(FontStyle.Italic, SpineTypography.cardHint.fontStyle)
        assertEquals(21.sp, SpineTypography.milestone.fontSize)
        assertEquals(9.sp, SpineTypography.discNumeral.fontSize)
        assertEquals("smcp, c2sc", SpineTypography.tableHead.fontFeatureSettings)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.designsystem.AchievementTypeRanksTest"`
Expected: FAIL to compile, with `Unresolved reference: daysKept`.

- [ ] **Step 3: Add the ranks**

In `SpineTypography`, after `still`, add:

```kotlin
    // The achievements page (#1307): the approved board's rule set (`final/_build/style.css`, `sheets.css`).
    val daysKept = ysabeau(78.sp, 230, (-0.025f).em, features = LINING, lineHeight = 66.sp)
    val daysUnit = ysabeau(15.sp, 560, 0.16f.em, features = SMALL_CAPS)
    val sinceLine = ysabeau(11.5.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val cardMargin = ysabeau(11.sp, 600, 0.26f.em, features = SMALL_CAPS)
    val cardBand = ysabeau(11.5.sp, 600, 0.22f.em, features = SMALL_CAPS)
    val cardHint = ysabeau(14.sp, 380, italic = true)
    val cardLine = ysabeau(16.sp, 380, italic = true, lineHeight = 21.6.sp)
    val stackLabel = ysabeau(11.sp, 600, 0.14f.em, features = SMALL_CAPS)
    val stackCount = ysabeau(20.sp, 300, features = LINING)
    val registerSub = ysabeau(14.5.sp, 420, lineHeight = 18.sp)
    val registerDate = ysabeau(12.5.sp, 400, 0.03f.em, features = TABULAR)
    val milestone = ysabeau(21.sp, 300, features = LINING)
    val milestoneSmall = ysabeau(17.sp, 300, features = LINING)
    val cellNumber = ysabeau(14.sp, 400, features = TABULAR)
    val cellNumberDone = ysabeau(14.sp, 560, features = TABULAR)
    val tableHead = ysabeau(10.5.sp, 600, 0.14f.em, features = SMALL_CAPS)
    val discNumeral = ysabeau(9.sp, 400, features = LINING)
    val discNumeralRim = ysabeau(9.sp, 600, features = LINING)
    val rosaceCount = ysabeau(28.sp, 300, features = LINING)
    val rosaceOf = ysabeau(11.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val rosaceFamily = ysabeau(11.sp, 600, 0.14f.em, features = SMALL_CAPS)
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.designsystem.AchievementTypeRanksTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/SpineTypography.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/core/designsystem/AchievementTypeRanksTest.kt
git commit -m "feat(android): add the achievements page's type ranks to the Spine"
```

### Task 8: Ink seals

The board prints everything that is not an emotion in ink: the first five, the six firsts, the domains, 25 pebbles, the month counters. It uses the Spine's three prints, plus two of its own:
- a **blank** seal, a rim on the paper, for a moment or a quiet month;
- a **dotted** blank, for a month still to come.

Today `rememberSealPrint` takes an emotion palette and forces a palette-less stone to neutral. The ink seal keeps the polarity and takes the colour as given.

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/render/stone/StoneSeal.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/render/stone/InkSealPixelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.ui.render.stone

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The ink seal prints in the colour it is given, keeping its print (#1307). A guard, not a proof of HWUI. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkSealPixelTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Seal(
        print: InkPrint,
        tag: String,
    ) {
        val seal = rememberInkSealPrint(ValenceSizeGroup.MEDIUM, print, Color.Black, Color.White)
        Canvas(Modifier.size(60.dp).background(Color.White).testTag(tag)) { drawSeal(seal, size.width) }
    }

    private fun pixels(tag: String): PixelMap = compose.onNodeWithTag(tag).captureToImage().toPixelMap()

    private fun PixelMap.dark(): Int {
        var n = 0
        for (y in 0 until height) for (x in 0 until width) if (this[x, y].red < 0.5f) n++
        return n
    }

    @Test
    fun `relief fills in ink, blank keeps the paper inside its rim, dotted inks less than blank`() {
        compose.setContent {
            PebblesTheme {
                Row {
                    Seal(InkPrint.RELIEF, "relief")
                    Seal(InkPrint.BLANK, "blank")
                    Seal(InkPrint.DOTTED, "dotted")
                }
            }
        }
        val relief = pixels("relief")
        val blank = pixels("blank")
        val dotted = pixels("dotted")
        val cx = relief.width / 2
        val cy = relief.height / 3
        assertTrue("relief centre should be ink", relief[cx, cy].red < 0.2f)
        assertTrue("blank centre should be paper", blank[cx, cy].red > 0.9f)
        assertTrue("blank should ink its rim", blank.dark() > 0)
        assertTrue("dotted (${dotted.dark()}) should ink less than blank (${blank.dark()})", dotted.dark() < blank.dark())
    }
}
```

The seal's height follows its silhouette, so a 60 dp square holds it in its top part. `height / 3` is inside the stone for every size.

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.ui.render.stone.InkSealPixelTest"`
Expected: FAIL to compile, with `Unresolved reference: InkPrint` and `rememberInkSealPrint`.

- [ ] **Step 3: Add the ink seal**

In `StoneSeal.kt`, add two trailing constructor parameters to `SealPrint`. They default off, so every existing print is unchanged:

```kotlin
    val aspectRatio: Float,
    /** Only the rim, on the knockout: the blank and dotted ink prints (#1307). */
    internal val rimOnly: Boolean = false,
    internal val dotted: Boolean = false,
)
```

In `drawSeal`, right after the `SealVariant.PIP` early return, add:

```kotlin
        if (print.rimOnly) {
            drawPath(body, print.knockout)
            val dash =
                if (print.dotted) {
                    PathEffect.dashPathEffect(floatArrayOf(DOT_ON.toPx() / pxPerUnit, DOT_OFF.toPx() / pxPerUnit))
                } else {
                    null
                }
            drawPath(body, inks.spot, style = Stroke(weight.rim.toPx() / pxPerUnit, pathEffect = dash))
            return@withTransform
        }
```

Below `rememberSealPrint`, add:

```kotlin
/**
 * How an ink seal prints (#1307): the Spine's three prints, plus the board's blank rim (a moment,
 * a quiet month) and its dotted rim (a month still to come).
 */
enum class InkPrint(
    internal val polarity: ValencePolarity,
) {
    RELIEF(ValencePolarity.HIGHLIGHT),
    OPEN(ValencePolarity.NEUTRAL),
    ENGRAVED(ValencePolarity.LOWLIGHT),
    BLANK(ValencePolarity.NEUTRAL),
    DOTTED(ValencePolarity.NEUTRAL),
}

/** The print for a valence's polarity, in ink. */
val ValencePolarity.inkPrint: InkPrint
    get() =
        when (this) {
            ValencePolarity.HIGHLIGHT -> InkPrint.RELIEF
            ValencePolarity.NEUTRAL -> InkPrint.OPEN
            ValencePolarity.LOWLIGHT -> InkPrint.ENGRAVED
        }

/**
 * A seal in [ink] rather than an emotion's spot ink (#1307): the achievements' marks are ink
 * wherever no emotion colours them. It keeps the polarity, unlike a palette-less [rememberSealPrint]
 * (a blank moment), and carries no glyph. Draw it with [drawSeal].
 */
@Composable
fun rememberInkSealPrint(
    size: ValenceSizeGroup,
    print: InkPrint,
    ink: Color,
    knockout: Color,
    variant: SealVariant = SealVariant.MINI,
): SealPrint {
    val polarity = print.polarity
    val outline = rememberStoneOutline(size, polarity)
    val inks =
        remember(ink, knockout) {
            SealInks(
                spot = ink,
                deep = ink,
                tint = StoneTones.mixOklab(ink, NEUTRAL_TINT, knockout),
                wash = StoneTones.mixOklab(ink, LOWLIGHT_WASH, knockout),
            )
        }
    val hatch = remember(size, polarity) { if (polarity == ValencePolarity.LOWLIGHT) hatchPath(size, HATCH_STEP.toFloat()) else null }
    val canvasAffine = remember(size, polarity) { StoneGeometry.canvasTransform(size, polarity) }
    val box = StoneGeometry.outlineBox(size)
    return remember(outline, polarity, inks, knockout, hatch, canvasAffine, variant, print) {
        SealPrint(
            outline.path,
            polarity,
            inks,
            knockout,
            emptyList(),
            hatch,
            size,
            canvasAffine,
            variant,
            box.width,
            box.width / box.height,
            rimOnly = print == InkPrint.BLANK || print == InkPrint.DOTTED,
            dotted = print == InkPrint.DOTTED,
        )
    }
}
```

Add the constants beside the others: `private val DOT_ON = 1.6.dp` and `private val DOT_OFF = 1.7.dp` (the board's `stroke-dasharray="1.6 1.7"`). Add the imports `androidx.compose.ui.graphics.PathEffect` and `app.pbbls.android.core.model.ValenceSizeGroup`, if the file lacks either.

- [ ] **Step 4: Run it, and the existing seal tests**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.core.ui.render.stone.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/render/stone/StoneSeal.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/render/stone/InkSealPixelTest.kt
git commit -m "feat(android): print a seal in ink, with the blank and dotted rims"
```

### Task 9: Lookups, hues and the shared marks

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/AchievementLookups.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/FigureInk.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/FigureData.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/FigureInkTest.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/FigureDataTest.kt`

- [ ] **Step 1: Write the failing tests**

`FigureInkTest.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.EmotionPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The board's colour rules (#1307): `famHue`, `tx` and the rosace's `tone`. */
class FigureInkTest {
    private val joy =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#A15C08FF",
                secondaryHex = "#CF8C39FF",
                lightHex = "#FAF6EAFF",
                surfaceHex = "#A15C081A",
                darkHex = "#201202FF",
                shadedHex = "#714006FF",
            ),
        )

    @Test
    fun `a family's hue is its primary on paper and its secondary at night`() {
        assertEquals(joy.primary, joy.hue(night = false))
        assertEquals(joy.secondary, joy.hue(night = true))
    }

    @Test
    fun `shame's name reads in its shaded tone on paper`() {
        assertEquals(joy.shaded, familyLabelColor("shame", joy, night = false))
        assertEquals(joy.primary, familyLabelColor("joy", joy, night = false))
        assertEquals(joy.secondary, familyLabelColor("shame", joy, night = true))
    }

    @Test
    fun `a family's first seat at night is its secondary, the run deepens, the soft star is paler`() {
        val stock = joy.light
        val first = seatTone(joy, index = 0, familySize = 5, night = true, stock = stock, soft = false)
        assertEquals(joy.secondary, first)
        val last = seatTone(joy, index = 4, familySize = 5, night = true, stock = stock, soft = false)
        assertNotEquals(first, last)
        assertNotEquals(last, seatTone(joy, index = 4, familySize = 5, night = true, stock = stock, soft = true))
    }
}
```

`FigureDataTest.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.DomainEntry
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat
import org.junit.Assert.assertEquals
import org.junit.Test

/** The payload readings the figures share (#1307): runs of a family, and the band's arrival order. */
class FigureDataTest {
    private fun seat(
        k: Int,
        family: String?,
        met: Boolean = false,
    ) = EmotionSeat(k, "e$k", family, met, null, false)

    @Test
    fun `a family's run is its consecutive seats, met counted, in seat order`() {
        val runs = familyRuns(listOf(seat(0, "joy", true), seat(1, "joy"), seat(2, "pride", true), seat(3, null)))
        assertEquals(listOf(FamilyRun("joy", 0, 2, 1), FamilyRun("pride", 2, 1, 1), FamilyRun(null, 3, 1, 0)), runs)
    }

    @Test
    fun `the band reads the entered domains oldest first, then the not yet`() {
        val domains =
            DomainsSection(
                count = 2,
                of = 4,
                coverage = null,
                entered = listOf(DomainEntry("newest", null, true), DomainEntry("oldest", null, false)),
                notYet = listOf("a", "b"),
                completion = null,
            )
        assertEquals(
            listOf(BandItem("oldest", true, false), BandItem("newest", true, true), BandItem("a", false, false), BandItem("b", false, false)),
            domainBandItems(domains),
        )
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.*"`
Expected: FAIL to compile, with `Unresolved reference: hue`, `familyRuns` and `domainBandItems`.

- [ ] **Step 3: Write the lookups**

`AchievementLookups.kt`:

```kotlin
package app.pbbls.android.features.achievements

import androidx.compose.runtime.Immutable
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionFamily
import app.pbbls.android.core.model.EmotionWithPalette

/**
 * What the payload's ids and slugs point at (#1307), as the wraps' `WrapLookups` does for theirs:
 * a family by category slug, an emotion by id, a domain by id. The screen builds it from the
 * reference services; a preview builds it from fixtures. A null is a row the device does not know
 * yet: it draws in ink, or its name falls back.
 */
@Immutable
class AchievementLookups(
    val family: (String) -> EmotionFamily?,
    val emotion: (String) -> EmotionWithPalette?,
    val domain: (String) -> Domain?,
)
```

- [ ] **Step 4: Write the ink helpers**

`figures/FigureInk.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.rememberInkSealPrint
import app.pbbls.android.core.ui.render.stone.rememberSealPrint
import kotlin.math.roundToInt

/** A family's hue (the board's `famHue`): its primary on Paper, its secondary at Night. */
internal fun EmotionPalette.hue(night: Boolean): Color = if (night) secondary else primary

/** A family's name on the rosace (the board's `tx`): shame's primary is too pale on Paper, so it reads shaded. */
internal fun familyLabelColor(
    slug: String,
    palette: EmotionPalette,
    night: Boolean,
): Color =
    when {
        night -> palette.secondary
        slug == "shame" -> palette.shaded
        else -> palette.primary
    }

/**
 * A lit facet's tone in its family's run (the board's `tone`): at Night it climbs from secondary
 * toward primary (62 % at the run's end); on Paper from secondary toward shaded (8 % to 80 %). A
 * soft one, the star between two lit seats, is 38 % paper.
 */
internal fun seatTone(
    palette: EmotionPalette,
    index: Int,
    familySize: Int,
    night: Boolean,
    stock: Color,
    soft: Boolean,
): Color {
    val t = if (familySize > 1) index.toFloat() / (familySize - 1) else 0.5f
    val base =
        if (night) {
            StoneTones.mixOklab(palette.primary, (t * 62).roundToInt() / 100f, palette.secondary)
        } else {
            StoneTones.mixOklab(palette.shaded, (8 + t * 72).roundToInt() / 100f, palette.secondary)
        }
    return if (soft) StoneTones.mixOklab(stock, 0.38f, base) else base
}

/** "Not yet": a dotted ring in secondary ink on the paper (the board's `ghost`). [unit] is px per board px. */
internal fun DrawScope.ghost(
    center: Offset,
    radius: Float,
    unit: Float,
    colors: SpineColors,
) {
    drawCircle(colors.stock, radius, center)
    drawCircle(
        colors.ink2,
        radius,
        center,
        style = Stroke(width = unit, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * unit, 1.6f * unit))),
    )
}

/** "New since you last looked": a ring around the mark and a dot at its shoulder (the board's `newMark`). */
internal fun DrawScope.newMark(
    center: Offset,
    width: Float,
    unit: Float,
    ink: Color,
) {
    drawCircle(ink, width * 0.86f, center, style = Stroke(unit))
    drawCircle(ink, 1.9f * unit, center + Offset(width * 0.66f, -width * 0.66f))
}

/** A seal centred on [center], [width] px wide. */
internal fun DrawScope.sealAt(
    print: SealPrint,
    center: Offset,
    width: Float,
) {
    translate(center.x - width / 2, center.y - width / print.aspectRatio / 2) { drawSeal(print, width) }
}

/** Every ink print at every size, for one ink: the figures' fixed set (15 prints, a constant count). */
@Stable
internal class InkSeals(
    private val prints: Map<Pair<ValenceSizeGroup, InkPrint>, SealPrint>,
) {
    fun of(
        size: ValenceSizeGroup,
        print: InkPrint,
    ): SealPrint = prints.getValue(size to print)
}

@Composable
internal fun rememberInkSeals(
    ink: Color,
    knockout: Color,
    variant: SealVariant = SealVariant.TINY,
): InkSeals {
    val prints = HashMap<Pair<ValenceSizeGroup, InkPrint>, SealPrint>()
    ValenceSizeGroup.entries.forEach { size ->
        InkPrint.entries.forEach { print -> prints[size to print] = rememberInkSealPrint(size, print, ink, knockout, variant) }
    }
    return InkSeals(prints)
}

/** One emotion seal: a valence printed in a family's palette. */
internal data class SealKey(
    val valence: Valence,
    val palette: EmotionPalette,
)

/** The emotion seals a figure needs, each remembered under its own key, so the set can change between payloads. */
@Composable
internal fun rememberEmotionSeals(
    keys: Collection<SealKey>,
    knockout: Color,
    variant: SealVariant,
): Map<SealKey, SealPrint> {
    val out = HashMap<SealKey, SealPrint>()
    keys.distinct().forEach { k ->
        out[k] = key(k) { rememberSealPrint(k.valence, k.palette, null, knockout, variant) }
    }
    return out
}

/** One ink seal in a given colour: the year's counters, inked in their month's hue. */
internal data class InkKey(
    val size: ValenceSizeGroup,
    val print: InkPrint,
    val ink: Color,
)

@Composable
internal fun rememberInkPrints(
    keys: Collection<InkKey>,
    knockout: Color,
    variant: SealVariant,
): Map<InkKey, SealPrint> {
    val out = HashMap<InkKey, SealPrint>()
    keys.distinct().forEach { k ->
        out[k] = key(k) { rememberInkSealPrint(k.size, k.print, k.ink, knockout, variant) }
    }
    return out
}
```

`StoneTones` is `internal` to the module, so `features/` may read it: one module, and Konsist forbids only `core → features` and `feature → feature`.

- [ ] **Step 5: Write the shared readings**

`figures/FigureData.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat

/** A family's consecutive seats in the server's seat order: where it starts, how many, how many met. */
data class FamilyRun(
    val family: String?,
    val start: Int,
    val size: Int,
    val met: Int,
)

/** Runs of one family, read off the seats as sent (#1307). Reading, not ordering: the order is the server's. */
fun familyRuns(seats: List<EmotionSeat>): List<FamilyRun> {
    val runs = mutableListOf<FamilyRun>()
    seats.forEachIndexed { i, s ->
        val last = runs.lastOrNull()
        if (last != null && last.family == s.family) {
            runs[runs.lastIndex] = last.copy(size = last.size + 1, met = last.met + if (s.met) 1 else 0)
        } else {
            runs += FamilyRun(s.family, i, 1, if (s.met) 1 else 0)
        }
    }
    return runs
}

/** One shard or seat of the domains: a domain id, whether it came, whether it is new. */
data class BandItem(
    val domainId: String,
    val on: Boolean,
    val isNew: Boolean,
)

/**
 * The band and the wheel draw the domains in the order they came, then the not yet (the board,
 * 28 October). The server sends `entered` newest first for the register, so this reads it backwards.
 */
fun domainBandItems(domains: DomainsSection): List<BandItem> =
    domains.entered.asReversed().map { BandItem(it.domainId, on = true, isNew = it.isNew) } +
        domains.notYet.map { BandItem(it, on = false, isNew = false) }
```

- [ ] **Step 6: Run the tests to make sure they pass**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.*"`
Expected: PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/ apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/
git commit -m "feat(android): add the achievements figures' lookups, hues, seal sets and shared readings"
```

### Task 10: The margin ladder

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/CardLadder.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/CardLaddersTest.kt`

Each bar of the ladder is a fill fraction read off the payload. A level the server lists is a full bar, and the server's `next.progress` is the partial one. Nothing is compared with a threshold.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.EmotionSeat
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.StackLevel
import app.pbbls.android.core.model.achievements.TwentyFive
import org.junit.Assert.assertEquals
import org.junit.Test

class CardLaddersTest {
    @Test
    fun `a counting ladder fills the listed levels, then the server's progress, then nothing`() {
        val pebbles =
            CountingSection(
                count = 137,
                levels = listOf(1, 10, 25, 50, 100).map { StackLevel(it, null, null, false) },
                next = NextLevel(250, 113, 0.25f),
                ladder = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
                thenEvery = 500,
            )
        assertEquals(listOf(1f, 1f, 1f, 1f, 1f, 0.25f, 0f, 0f), CardLadders.counting(pebbles))
    }

    @Test
    fun `the explorer ladder is one bar per ring, full when the server says the ring is done`() {
        val explorer =
            ExplorerSection(
                firstPebble = null,
                firstFive = emptyList(),
                sixFirsts = emptyList(),
                twentyFive = TwentyFive(10, false, null, false),
                ringsDone = setOf(ExplorerRing.FIRST_FIVE, ExplorerRing.SIX_FIRSTS),
                completion = null,
            )
        val emotions = EmotionsSection(19, 38, emptyList(), null)
        assertEquals(listOf(1f, 1f, 0.4f, 0f, 0.5f), CardLadders.explorer(explorer, emotions, domains = null))
    }

    @Test
    fun `the emotions ladder is one bar per family run`() {
        val seats = listOf(EmotionSeat(0, "a", "joy", true, null, false), EmotionSeat(1, "b", "joy", false, null, false), EmotionSeat(2, "c", "pride", true, null, false))
        assertEquals(listOf(0.5f, 1f), CardLadders.emotions(EmotionsSection(2, 3, seats, null)))
    }

    @Test
    fun `the year ladder places each month by its level and progress, the rim full`() {
        fun m(
            month: Int,
            level: Int,
            progress: Float,
        ) = AchievementMonth(month, MonthState.KEPT, "joy", 0, 0, 0, 0, level, progress)
        val year = AchievementYear(2026, true, listOf(m(1, 0, 0.5f), m(2, 1, 0.5f), m(3, 2, 0f)) + (4..12).map { m(it, 0, 0f) }, emptyList(), null)
        assertEquals(listOf(0.25f, 0.75f, 1f), CardLadders.year(year).take(3))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.CardLaddersTest"`
Expected: FAIL to compile, with `Unresolved reference: CardLadders`.

- [ ] **Step 3: Write the ladder**

`figures/CardLadder.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection

/** The 25 seats of the Explorer's third ring (D6). */
const val TWENTY_FIVE_SEATS = 25

/** Each card's margin ladder, as fill fractions read off the payload (the board's `ladder`, `fracLadder`). */
object CardLadders {
    fun explorer(
        explorer: ExplorerSection,
        emotions: EmotionsSection?,
        domains: DomainsSection?,
    ): List<Float> {
        fun ring(
            ring: ExplorerRing,
            fraction: Float,
        ) = if (ring in explorer.ringsDone) 1f else fraction
        return listOf(
            ring(ExplorerRing.FIRST_FIVE, fraction(explorer.firstFive.count { it.done }, explorer.firstFive.size)),
            ring(ExplorerRing.SIX_FIRSTS, fraction(explorer.sixFirsts.count { it.done }, explorer.sixFirsts.size)),
            ring(ExplorerRing.TWENTY_FIVE, fraction(explorer.twentyFive?.count ?: 0, TWENTY_FIVE_SEATS)),
            ring(ExplorerRing.DOMAINS, fraction(domains?.count ?: 0, domains?.of ?: 0)),
            ring(ExplorerRing.EMOTIONS, fraction(emotions?.count ?: 0, emotions?.of ?: 0)),
        )
    }

    fun emotions(emotions: EmotionsSection): List<Float> = familyRuns(emotions.seats).map { fraction(it.met, it.size) }

    fun domains(domains: DomainsSection): Float = fraction(domains.count, domains.of)

    fun counting(stack: CountingSection): List<Float> {
        val reached = stack.levels.map { it.threshold }.toSet()
        return stack.ladder.map { t ->
            when {
                t in reached -> 1f
                t == stack.next?.threshold -> stack.next.progress
                else -> 0f
            }
        }
    }

    /** Each month at `(level + progress) / 2`: the inner disc is half way, the rim the top. */
    fun year(year: AchievementYear): List<Float> = year.months.map { (it.level + if (it.level >= 2) 0f else it.progress) / 2f }

    private fun fraction(
        n: Int,
        of: Int,
    ): Float = if (of <= 0) 0f else (n.toFloat() / of).coerceIn(0f, 1f)
}

private val BAR_WIDTH = 4.dp
private val BAR_HEIGHT = 11.dp
private val BAR_GAP = 4.dp
private val BAR_RING = 1.dp

/**
 * The card's margin ladder (the board's `.lad`): one 4 dp bar per step. A full bar is solid ink;
 * any other bar is a secondary-ink ring, filled from the top by its fraction. A [long] bar is
 * the domains' single tall one. Decorative: the card's label says it in words.
 */
@Composable
fun CardLadder(
    fills: List<Float>,
    modifier: Modifier = Modifier,
    long: Dp? = null,
) {
    val colors = PebblesTheme.spine.colors
    Column(
        modifier = modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(BAR_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        fills.forEach { fill ->
            Canvas(Modifier.size(BAR_WIDTH, long ?: BAR_HEIGHT)) {
                val radius = CornerRadius(size.width / 2)
                if (fill >= 1f) {
                    drawRoundRect(colors.ink, cornerRadius = radius)
                } else {
                    if (fill > 0f) clipRect(bottom = size.height * fill) { drawRoundRect(colors.ink, cornerRadius = radius) }
                    val inset = BAR_RING.toPx() / 2
                    drawRoundRect(
                        colors.ink2,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = radius,
                        style = Stroke(BAR_RING.toPx()),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.CardLaddersTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/CardLadder.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/CardLaddersTest.kt
git commit -m "feat(android): draw the achievements cards' margin ladder from the payload"
```

### Task 11: The marks — the first five, the six firsts, a pebble's seal

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/Marks.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/MarksTest.kt`

The board's `FIVE_MARKS` draws each first-five task as the part it makes:
- the first moment is a blank seal;
- the first pebble is the seal lit;
- the first collection is three seals together;
- the first soul is a soul's mark;
- the first glyph is a glyph in a dashed circle.

The six firsts are three intensities (solid seals at three sizes) and three valences (the three prints at medium size). The board draws the intensity seals on the neutral outline with a solid print. Android's outline follows its print, so they use the highlight outline, the solid print's own; the difference is a few dp of silhouette.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.core.ui.render.stone.InkPrint
import org.junit.Assert.assertEquals
import org.junit.Test

/** The board's six-firsts table (`app.js`, the wheel's six ring), one entry per kind. */
class MarksTest {
    @Test
    fun `intensities are solid at three sizes, valences the three prints at medium`() {
        assertEquals(SixMark(ValenceSizeGroup.SMALL, InkPrint.RELIEF, 9f), SIX_MARKS.getValue(StoneKind.SMALL))
        assertEquals(SixMark(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, 12f), SIX_MARKS.getValue(StoneKind.MEDIUM))
        assertEquals(SixMark(ValenceSizeGroup.LARGE, InkPrint.RELIEF, 14f), SIX_MARKS.getValue(StoneKind.LARGE))
        assertEquals(SixMark(ValenceSizeGroup.MEDIUM, InkPrint.ENGRAVED, 12.5f), SIX_MARKS.getValue(StoneKind.LOWLIGHT))
        assertEquals(SixMark(ValenceSizeGroup.MEDIUM, InkPrint.OPEN, 12.5f), SIX_MARKS.getValue(StoneKind.NEUTRAL))
        assertEquals(SixMark(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, 12.5f), SIX_MARKS.getValue(StoneKind.HIGHLIGHT))
        assertEquals(StoneKind.entries.toSet(), SIX_MARKS.keys)
    }

    @Test
    fun `an emotion's seal grows with its intensity`() {
        assertEquals(9f, emotionSealWidth(1), 1e-4f)
        assertEquals(10.6f, emotionSealWidth(2), 1e-4f)
        assertEquals(12.2f, emotionSealWidth(3), 1e-4f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.MarksTest"`
Expected: FAIL to compile, with `Unresolved reference: SIX_MARKS`.

- [ ] **Step 3: Write the marks**

`figures/Marks.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.PebbleSnapshot
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.inkPrint
import app.pbbls.android.features.achievements.AchievementLookups

/** One six-firsts mark: a size, a print and a width in board px. */
data class SixMark(
    val size: ValenceSizeGroup,
    val print: InkPrint,
    val width: Float,
)

val SIX_MARKS: Map<StoneKind, SixMark> =
    mapOf(
        StoneKind.SMALL to SixMark(ValenceSizeGroup.SMALL, InkPrint.RELIEF, 9f),
        StoneKind.MEDIUM to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, 12f),
        StoneKind.LARGE to SixMark(ValenceSizeGroup.LARGE, InkPrint.RELIEF, 14f),
        StoneKind.LOWLIGHT to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.ENGRAVED, 12.5f),
        StoneKind.NEUTRAL to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.OPEN, 12.5f),
        StoneKind.HIGHLIGHT to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, 12.5f),
    )

/** An emotion's seal width on the wheel, in board px (`7.4 + intensity × 1.6`). */
fun emotionSealWidth(intensity: Int): Float = 7.4f + intensity * 1.6f

/** The size group an intensity prints at. */
internal fun sizeOf(intensity: Int): ValenceSizeGroup =
    when (intensity) {
        1 -> ValenceSizeGroup.SMALL
        2 -> ValenceSizeGroup.MEDIUM
        else -> ValenceSizeGroup.LARGE
    }

private const val GLYPH_BOX = 200f

/** Glyph strokes keep the engine's 6 in a 200 box, at the board's 0.19 weight: 1.14 board px at any size. */
private const val GLYPH_STROKE = 6f * 0.19f
private val SOUL_MARK = listOf("M30,110 Q65,55 100,110 Q135,165 170,110")
private val GLYPH_MARK = listOf("M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", "M100,100 L100,101")
private val PARSER = PathParser()

private fun parse(d: String): Path = PARSER.parsePathString(d).toPath()

private val SOUL_PATHS = SOUL_MARK.map(::parse)
private val GLYPH_PATHS = GLYPH_MARK.map(::parse)

private fun DrawScope.glyphAt(
    center: Offset,
    side: Float,
    paths: List<Path>,
    unit: Float,
    colors: SpineColors,
) {
    val scale = side / GLYPH_BOX
    translate(center.x - side / 2, center.y - side / 2) {
        // The paths are in a 200 box: scale them into `side` px and keep the stroke at its on-screen weight.
        val m = androidx.compose.ui.graphics.Matrix().apply { scale(scale, scale) }
        paths.forEach { p ->
            val placed = Path().apply { addPath(p) }.also { it.transform(m) }
            drawPath(placed, colors.ink, style = Stroke(GLYPH_STROKE * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A first-five task's mark at [center], in board px times [unit] (the board's `FIVE_MARKS`). */
internal fun DrawScope.firstFiveMark(
    step: FirstStep,
    center: Offset,
    unit: Float,
    seals: InkSeals,
    colors: SpineColors,
) {
    when (step) {
        FirstStep.MOMENT -> sealAt(seals.of(ValenceSizeGroup.MEDIUM, InkPrint.BLANK), center, 10f * unit)
        FirstStep.PEBBLE -> sealAt(seals.of(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF), center, 10f * unit)
        FirstStep.COLLECTION -> {
            sealAt(seals.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), center + Offset(-4.6f * unit, 2.4f * unit), 6f * unit)
            sealAt(seals.of(ValenceSizeGroup.MEDIUM, InkPrint.OPEN), center + Offset(0f, -3f * unit), 6.6f * unit)
            sealAt(seals.of(ValenceSizeGroup.SMALL, InkPrint.ENGRAVED), center + Offset(4.6f * unit, 2.4f * unit), 6f * unit)
        }
        FirstStep.SOUL -> glyphAt(center, 14f * unit, SOUL_PATHS, unit, colors)
        FirstStep.GLYPH -> {
            drawCircle(
                colors.ink2,
                7.2f * unit,
                center,
                style = Stroke(unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f * unit, 1.6f * unit))),
            )
            glyphAt(center, 9.5f * unit, GLYPH_PATHS, unit, colors)
        }
    }
}

/** A six-firsts mark at [center]. */
internal fun DrawScope.sixFirstMark(
    kind: StoneKind,
    center: Offset,
    unit: Float,
    seals: InkSeals,
) {
    val mark = SIX_MARKS.getValue(kind)
    sealAt(seals.of(mark.size, mark.print), center, mark.width * unit)
}

private val MARK_BOX = 28.dp

/** A register row's mark: a first-five task, or a ghost when not yet done. Decorative. */
@Composable
fun FirstFiveMark(
    step: FirstStep,
    done: Boolean,
    modifier: Modifier = Modifier,
    box: Dp = MARK_BOX,
) {
    val colors = PebblesTheme.spine.colors
    val seals = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f * 1.7f
        if (done) firstFiveMark(step, center, unit, seals, colors) else ghost(center, 3.6f * unit, unit, colors)
    }
}

/** A register row's mark for a six-firsts kind. Decorative. */
@Composable
fun SixFirstMark(
    kind: StoneKind,
    done: Boolean,
    modifier: Modifier = Modifier,
    box: Dp = MARK_BOX,
) {
    val colors = PebblesTheme.spine.colors
    val seals = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f * 1.55f
        if (done) sixFirstMark(kind, center, unit, seals) else ghost(center, 3.6f * unit, unit, colors)
    }
}

/**
 * A pebble's own seal as a register mark: its size and print, in its emotion's palette, or in ink
 * when the device does not know the emotion (spec §4). [width] in board px, in a 28 dp box.
 */
@Composable
fun PebbleSealMark(
    pebble: PebbleSnapshot,
    lookups: AchievementLookups,
    width: Float,
    modifier: Modifier = Modifier,
    box: Dp = MARK_BOX,
) {
    val colors = PebblesTheme.spine.colors
    val palette = pebble.emotionId?.let { lookups.emotion(it)?.palette }
    val emotion = rememberEmotionSeals(listOfNotNull(palette?.let { SealKey(pebble.valence, it) }), colors.stock, SealVariant.MINI)
    val ink = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f
        val print =
            palette?.let { emotion.getValue(SealKey(pebble.valence, it)) }
                ?: ink.of(sizeOf(pebble.valence.intensity), pebble.valence.polarity.inkPrint)
        sealAt(print, center, width * unit)
    }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.MarksTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/Marks.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/MarksTest.kt
git commit -m "feat(android): draw the first five, the six firsts and a pebble's seal as marks"
```

### Task 12: The Explorer wheel

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/ExplorerWheelGeometry.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/ExplorerWheel.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/ExplorerWheelGeometryTest.kt`

The wheel's rings, from the centre out (D6):
1. the user's first pebble;
2. the first five, at radius 44;
3. the six firsts, at 68;
4. 25 pebbles, at 90;
5. the domains, at 112;
6. the emotions, at 134.

Angles are degrees clockwise from twelve o'clock in a 300 box centred on 0 (the board's `polar`).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.figures

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerWheelGeometryTest {
    private val g = ExplorerWheelGeometry

    @Test
    fun `polar runs clockwise from twelve o'clock, y down`() {
        val top = g.polar(10f, 0f)
        assertEquals(0f, top.x, 1e-4f)
        assertEquals(-10f, top.y, 1e-4f)
        val right = g.polar(10f, 90f)
        assertEquals(10f, right.x, 1e-4f)
        assertEquals(0f, right.y, 1e-4f)
    }

    @Test
    fun `the rings sit at the board's radii and spacing`() {
        assertEquals(listOf(44f, 68f, 90f, 112f, 134f), listOf(g.FIRST_FIVE_R, g.SIX_R, g.TWENTY_FIVE_R, g.DOMAINS_R, g.EMOTIONS_R))
        assertEquals(144f, g.firstFiveAngle(2), 1e-4f)
        assertEquals(7.2f, g.twentyFiveAngle(0), 1e-4f)
        assertEquals(10f, g.domainAngle(0, 18), 1e-4f)
        assertEquals(-2.5f * 360f / 38, g.seatAngle(0, 38), 1e-4f)
    }

    @Test
    fun `a family's arc spans its seats less 1 2 degrees at each end, its tick at its start`() {
        val step = 360f / 38
        val arc = g.familyArcs(listOf(FamilyRun("joy", 0, 9, 3)), seats = 38).single()
        val a0 = g.seatAngle(0, 38) - step / 2
        val a1 = g.seatAngle(8, 38) + step / 2
        assertEquals(a0 + 1.2f, arc.startDeg, 1e-3f)
        assertEquals(a1 - a0 - 2.4f, arc.sweepDeg, 1e-3f)
        assertEquals(a0, arc.tickDeg, 1e-3f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.ExplorerWheelGeometryTest"`
Expected: FAIL to compile, with `Unresolved reference: ExplorerWheelGeometry`.

- [ ] **Step 3: Write the geometry**

`figures/ExplorerWheelGeometry.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/** The Explorer wheel's geometry (the board's `wheel`, `WR`), in board px of a 300 box centred on 0. */
object ExplorerWheelGeometry {
    const val VIEW = 300f
    const val FIRST_FIVE_R = 44f
    const val SIX_R = 68f
    const val TWENTY_FIVE_R = 90f
    const val DOMAINS_R = 112f
    const val EMOTIONS_R = 134f
    const val CENTRE_WIDTH = 25f
    const val DOMAIN_WIDTH = 9f
    const val TWENTY_FIVE_WIDTH = 6.2f
    const val GHOST_R = 3.2f
    const val SMALL_GHOST_R = 2.6f
    const val ARC_INSET = 1.2f
    const val TICK_IN = 6f
    const val TICK_OUT = 11f
    val SIX_ANGLES = listOf(30f, 90f, 150f, 210f, 270f, 330f)

    /** Degrees clockwise from twelve o'clock, y down. */
    fun polar(
        r: Float,
        deg: Float,
    ): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset((r * sin(a)).toFloat(), (-r * cos(a)).toFloat())
    }

    fun firstFiveAngle(i: Int): Float = i * 72f

    fun twentyFiveAngle(i: Int): Float = (i + 0.5f) * 360f / TWENTY_FIVE_SEATS

    fun domainAngle(
        i: Int,
        of: Int,
    ): Float = (i + 0.5f) * 360f / of.coerceAtLeast(1)

    /** The rosace's seats, so the wheel's emotions sit where the rosace's facets do. */
    fun seatAngle(
        seat: Int,
        seats: Int,
    ): Float = (seat - 2.5f) * 360f / seats.coerceAtLeast(1)

    data class Arc(
        val family: String?,
        val startDeg: Float,
        val sweepDeg: Float,
        val tickDeg: Float,
    )

    fun familyArcs(
        runs: List<FamilyRun>,
        seats: Int,
    ): List<Arc> {
        val step = 360f / seats.coerceAtLeast(1)
        return runs.map { run ->
            val a0 = seatAngle(run.start, seats) - step / 2
            val a1 = seatAngle(run.start + run.size - 1, seats) + step / 2
            Arc(run.family, a0 + ARC_INSET, (a1 - ARC_INSET) - (a0 + ARC_INSET), a0)
        }
    }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.ExplorerWheelGeometryTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Write the wheel**

`figures/ExplorerWheel.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.inkPrint
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.figures.ExplorerWheelGeometry as G

private const val ARC_ALPHA = 0.5f
private const val ARC_WIDTH = 0.8f
private const val FIRST_FIVE_WIDTH = 10f
private const val CENTRE_GHOST_R = 6f

/**
 * The Explorer wheel (D6; the board's `wheel`, pick 5): five rings around the first pebble. A ring
 * the server lists in `rings_done` draws its track in secondary ink, otherwise in the rule. Every
 * "not yet" is a ghost, and every new item a ring and a dot. One node: [description].
 */
@Composable
fun ExplorerWheel(
    explorer: ExplorerSection,
    emotions: EmotionsSection?,
    domains: DomainsSection?,
    lookups: AchievementLookups,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    val colors = PebblesTheme.spine.colors
    val night = colors.isNight
    val ink = rememberInkSeals(colors.ink, colors.stock, SealVariant.TINY)
    val seats = emotions?.seats.orEmpty()
    val seatKeys =
        seats.mapNotNull { s ->
            val pebble = s.pebble ?: return@mapNotNull null
            val palette = s.family?.let { lookups.family(it)?.palette } ?: return@mapNotNull null
            if (s.met) SealKey(pebble.valence, palette) else null
        }
    val emotionSeals = rememberEmotionSeals(seatKeys, colors.stock, SealVariant.TINY)
    val centre = explorer.firstPebble
    val centrePalette = centre?.emotionId?.let { lookups.emotion(it)?.palette }
    val centreSeals =
        rememberEmotionSeals(listOfNotNull(centrePalette?.let { p -> centre?.let { SealKey(it.valence, p) } }), colors.stock, SealVariant.MINI)
    val centreInk = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    val arcs = remember(emotions) { emotions?.let { G.familyArcs(familyRuns(it.seats), it.seats.size) }.orEmpty() }
    val band = remember(domains) { domains?.let(::domainBandItems).orEmpty() }

    // As wide as the room allows, up to [size], always square: the card and the sheet never overflow a narrow phone.
    Canvas(modifier.fillMaxWidth().widthIn(max = size).aspectRatio(1f).clearAndSetSemantics { contentDescription = description }) {
        val unit = this.size.width / G.VIEW
        val c = center

        fun at(
            r: Float,
            deg: Float,
        ): Offset = c + G.polar(r, deg) * unit

        fun track(
            r: Float,
            done: Boolean,
        ) = drawCircle(if (done) colors.ink2 else colors.rule, r * unit, c, style = Stroke(unit))

        // The emotions, on the rosace's seats: each family's arc in its hue, a tick where it starts.
        arcs.forEach { arc ->
            val hue = arc.family?.let { lookups.family(it)?.palette?.hue(night) } ?: colors.ink
            val r = G.EMOTIONS_R * unit
            drawArc(
                color = hue,
                startAngle = arc.startDeg - 90f,
                sweepAngle = arc.sweepDeg,
                useCenter = false,
                topLeft = c - Offset(r, r),
                size = Size(2 * r, 2 * r),
                alpha = ARC_ALPHA,
                style = Stroke(ARC_WIDTH * unit),
            )
            drawLine(colors.ink2, at(G.EMOTIONS_R + G.TICK_IN, arc.tickDeg), at(G.EMOTIONS_R + G.TICK_OUT, arc.tickDeg), unit, StrokeCap.Round)
        }
        seats.forEach { s ->
            val p = at(G.EMOTIONS_R, G.seatAngle(s.seat, seats.size))
            val pebble = s.pebble
            if (!s.met || pebble == null) {
                ghost(p, G.GHOST_R * unit, unit, colors)
                return@forEach
            }
            val w = emotionSealWidth(pebble.valence.intensity) * unit
            val palette = s.family?.let { lookups.family(it)?.palette }
            val print =
                palette?.let { emotionSeals[SealKey(pebble.valence, it)] }
                    ?: ink.of(sizeOf(pebble.valence.intensity), pebble.valence.polarity.inkPrint)
            sealAt(print, p, w)
            if (s.isNew) newMark(p, w, unit, colors.ink)
        }

        // The domains, in the order they came, then the not yet.
        track(G.DOMAINS_R, ExplorerRing.DOMAINS in explorer.ringsDone)
        val of = domains?.of?.takeIf { it > 0 } ?: band.size
        band.forEachIndexed { i, item ->
            val p = at(G.DOMAINS_R, G.domainAngle(i, of))
            if (!item.on) {
                ghost(p, G.GHOST_R * unit, unit, colors)
            } else {
                sealAt(ink.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), p, G.DOMAIN_WIDTH * unit)
                if (item.isNew) newMark(p, G.DOMAIN_WIDTH * unit, unit, colors.ink)
            }
        }

        // 25 pebbles: the first `count` seats lit.
        track(G.TWENTY_FIVE_R, ExplorerRing.TWENTY_FIVE in explorer.ringsDone)
        val kept = explorer.twentyFive?.count ?: 0
        for (i in 0 until TWENTY_FIVE_SEATS) {
            val p = at(G.TWENTY_FIVE_R, G.twentyFiveAngle(i))
            if (i < kept) {
                sealAt(ink.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), p, G.TWENTY_FIVE_WIDTH * unit)
            } else {
                ghost(p, G.SMALL_GHOST_R * unit, unit, colors)
            }
        }

        // The six firsts: three intensities, three valences.
        track(G.SIX_R, ExplorerRing.SIX_FIRSTS in explorer.ringsDone)
        explorer.sixFirsts.forEach { first ->
            val p = at(G.SIX_R, G.SIX_ANGLES[first.kind.ordinal])
            if (first.done) sixFirstMark(first.kind, p, unit, ink) else ghost(p, G.GHOST_R * unit, unit, colors)
            if (first.isNew) newMark(p, SIX_MARKS.getValue(first.kind).width * unit, unit, colors.ink)
        }

        // The first five, in the Home checklist's order, clockwise from the top.
        track(G.FIRST_FIVE_R, ExplorerRing.FIRST_FIVE in explorer.ringsDone)
        explorer.firstFive.forEach { item ->
            val p = at(G.FIRST_FIVE_R, G.firstFiveAngle(item.step.ordinal))
            if (item.done) firstFiveMark(item.step, p, unit, ink, colors) else ghost(p, G.GHOST_R * unit, unit, colors)
            if (item.isNew) newMark(p, FIRST_FIVE_WIDTH * unit, unit, colors.ink)
        }

        // The centre: the first pebble, its own seal in its emotion's palette.
        if (centre == null) {
            ghost(c, CENTRE_GHOST_R * unit, unit, colors)
        } else {
            val print =
                centrePalette?.let { centreSeals[SealKey(centre.valence, it)] }
                    ?: centreInk.of(sizeOf(centre.valence.intensity), centre.valence.polarity.inkPrint)
            sealAt(print, c, G.CENTRE_WIDTH * unit)
        }
    }
}
```

- [ ] **Step 6: Compile and commit**

Run: `cd apps/android && ./gradlew compileDebugKotlin testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.*"`
Expected: `BUILD SUCCESSFUL`. The wheel's render is proven by the screenshot previews in Task 17.

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/ExplorerWheel*.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/ExplorerWheelGeometryTest.kt
git commit -m "feat(android): draw the Explorer wheel's five rings around the first pebble"
```

### Task 13: The rosace

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/RosaceGeometry.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/Rosace.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/RosaceGeometryTest.kt`

The board's `gem` is a ring of kites, one per seat, with a star between each pair, inside a sawtooth silhouette. A met seat's kite is filled in its family's tone. A star between two met seats of one family is filled too, softer.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.figures

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class RosaceGeometryTest {
    private val g = RosaceGeometry(size = 270f, labels = true, seats = 38)

    private fun r(p: androidx.compose.ui.geometry.Offset) = hypot((p.x - g.centre).toDouble(), (p.y - g.centre).toDouble()).toFloat()

    @Test
    fun `the radii follow the board, with labels shrinking the stone`() {
        assertEquals(270f * 0.345f, g.radius, 1e-3f)
        assertEquals(g.radius * 0.85f, g.inner, 1e-3f)
        assertEquals(g.radius * 0.47f, g.core, 1e-3f)
        assertEquals(200f * 0.44f, RosaceGeometry(200f, labels = false, seats = 38).radius, 1e-3f)
    }

    @Test
    fun `seat zero sits two and a half steps before twelve o'clock`() {
        assertEquals(-PI / 2 - 2.5 * 2 * PI / 38, g.angle(0), 1e-9)
    }

    @Test
    fun `a kite reaches the rim at its seat and the core between, a star joins two seats`() {
        val kite = g.kite(3)
        assertEquals(4, kite.size)
        assertEquals(g.radius, r(kite[1]), 1e-3f)
        assertEquals(g.core, r(kite[3]), 1e-3f)
        val star = g.star(3)
        assertEquals(3, star.size)
        assertEquals(g.core, r(star[0]), 1e-3f)
        assertEquals(g.core, r(star[2]), 1e-3f)
        assertEquals(g.inner, r(star[1]), 1e-3f)
    }

    @Test
    fun `the silhouette has two points per seat, labels sit 22 past the rim`() {
        assertEquals(76, g.silhouette().size)
        assertEquals(g.radius + 22f, r(g.label(start = 0, size = 9)), 1e-3f)
        assertEquals(g.radius + 6.5f, r(g.freshDot(5)), 1e-3f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.RosaceGeometryTest"`
Expected: FAIL to compile, with `Unresolved reference: RosaceGeometry`.

- [ ] **Step 3: Write the geometry**

`figures/RosaceGeometry.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The rosace's cut (the board's `gem`), in dp of a [size] box; angles in radians, 0 at three o'clock. */
class RosaceGeometry(
    val size: Float,
    labels: Boolean,
    val seats: Int,
) {
    val centre = size / 2
    val radius = size * if (labels) 0.345f else 0.44f
    val inner = radius * 0.85f
    val core = radius * 0.47f
    private val step = 2 * PI / seats.coerceAtLeast(1)

    fun angle(i: Int): Double = -PI / 2 + (i - 2.5) * step

    fun point(
        r: Float,
        a: Double,
    ): Offset = Offset(centre + (cos(a) * r).toFloat(), centre + (sin(a) * r).toFloat())

    fun kite(k: Int): List<Offset> =
        listOf(point(inner, angle(k) - step / 2), point(radius, angle(k)), point(inner, angle(k) + step / 2), point(core, angle(k)))

    fun star(k: Int): List<Offset> = listOf(point(core, angle(k)), point(inner, angle(k) + step / 2), point(core, angle(k + 1)))

    fun silhouette(): List<Offset> = (0 until seats).flatMap { k -> listOf(point(inner, angle(k) - step / 2), point(radius, angle(k))) }

    fun freshDot(k: Int): Offset = point(radius + 6.5f, angle(k))

    /** A family boundary's tick, outside the rim. */
    fun tick(start: Int): Pair<Offset, Offset> = point(radius + 3f, angle(start) - step / 2) to point(radius + 9f, angle(start) - step / 2)

    /** Where a family's name sits: 22 past the rim, at its run's middle. */
    fun label(
        start: Int,
        size: Int,
    ): Offset = point(radius + 22f, angle(start) + (size - 1) / 2.0 * step)
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.RosaceGeometryTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Write the rosace**

`figures/Rosace.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.achievements.AchievementLookups

private const val FACET_SEAM = 0.9f
private const val FRESH_DOT_R = 2.6f
private const val CENTRE_COUNT_RISE = 3f
private const val CENTRE_OF_DROP = 15f

private fun Path.polygon(points: List<Offset>): Path =
    apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }

/**
 * The emotions rosace (the board's `gem`, A1 pick 6): one kite per seat in the server's seat order.
 * A met seat is lit in its family's tone, and an unknown family in ink. A new seat carries a dot
 * outside the rim. [labels] names the families around it; [centre] is the count and its "of 38".
 */
@Composable
fun Rosace(
    emotions: EmotionsSection,
    lookups: AchievementLookups,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 270.dp,
    labels: Boolean = true,
    centre: Pair<String, String>? = null,
    ticks: Boolean = true,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val night = colors.isNight
    val seats = emotions.seats
    val runs = remember(seats) { familyRuns(seats) }
    val g = remember(size, labels, seats.size) { RosaceGeometry(size.value, labels, seats.size) }
    val names =
        runs.map { run ->
            run.family?.let { slug -> ReferenceStrings.referenceName(ReferenceType.EMOTION_CATEGORY, slug, lookups.family(slug)?.name ?: slug) }
        }
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().widthIn(max = size).aspectRatio(1f).clearAndSetSemantics { contentDescription = description }) {
        val k = this.size.width / g.size
        withTransform({ scale(k, k, Offset.Zero) }) {
            drawPath(Path().polygon(g.silhouette()), colors.stock)
            drawPath(Path().polygon(g.silhouette()), colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
            seats.indices.forEach { seat ->
                drawPath(Path().polygon(g.kite(seat)), colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
                drawPath(Path().polygon(g.star(seat)), colors.ink2, style = Stroke(1f, join = StrokeJoin.Round))
            }
            runs.forEach { run ->
                val palette = run.family?.let { lookups.family(it)?.palette }
                for (j in 0 until run.size) {
                    val seat = run.start + j
                    if (!seats[seat].met) continue
                    val tone = palette?.let { seatTone(it, j, run.size, night, colors.stock, soft = false) } ?: colors.ink
                    drawPath(Path().polygon(g.kite(seat)), tone)
                    drawPath(Path().polygon(g.kite(seat)), colors.stock, style = Stroke(FACET_SEAM))
                    val next = seat + 1
                    if (j + 1 < run.size && seats[next].met) {
                        val soft = palette?.let { seatTone(it, j + 1, run.size, night, colors.stock, soft = true) } ?: colors.ink2
                        drawPath(Path().polygon(g.star(seat)), soft)
                        drawPath(Path().polygon(g.star(seat)), colors.stock, style = Stroke(FACET_SEAM))
                    }
                }
                if (ticks) {
                    val (a, b) = g.tick(run.start)
                    drawLine(colors.ink2, a, b, 1f, StrokeCap.Round)
                }
            }
            seats.forEachIndexed { seat, s -> if (s.isNew) drawCircle(colors.ink, FRESH_DOT_R, g.freshDot(seat)) }
        }
        if (labels) {
            runs.forEachIndexed { i, run ->
                val name = names[i] ?: return@forEachIndexed
                val palette = run.family?.let { lookups.family(it)?.palette }
                val colour = palette?.let { familyLabelColor(run.family, it, night) } ?: colors.ink2
                val layout = measurer.measure(name, type.rosaceFamily.copy(color = colour))
                val at = g.label(run.start, run.size) * k
                drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
        centre?.let { (count, of) ->
            val countLayout = measurer.measure(count, type.rosaceCount.copy(color = colors.ink))
            val ofLayout = measurer.measure(of, type.rosaceOf.copy(color = colors.ink2))
            val c = Offset(g.centre, g.centre) * k
            drawText(countLayout, topLeft = c - Offset(countLayout.size.width / 2f, countLayout.size.height - CENTRE_COUNT_RISE * k))
            drawText(ofLayout, topLeft = c + Offset(-ofLayout.size.width / 2f, CENTRE_OF_DROP * k - ofLayout.size.height))
        }
    }
}
```

The two `TextStyle.copy(color = …)` calls change only the colour. `ThemeLiteralsTest` forbids a `fontSize` in a `.copy(…)`, not a colour.

- [ ] **Step 6: Compile and commit**

Run: `cd apps/android && ./gradlew compileDebugKotlin testDebugUnitTest --tests "app.pbbls.android.architecture.*"`
Expected: `BUILD SUCCESSFUL`; `ThemeLiteralsTest` and `ArchitectureBoundaryTest` pass.

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/Rosace*.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/RosaceGeometryTest.kt
git commit -m "feat(android): draw the emotions rosace on the server's seats"
```

### Task 14: The mended band

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/ShardBandGeometry.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/ShardBand.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/ShardBandGeometryTest.kt`

The board's `shardStrip`: one shard per domain, its seams jittered by a stable hash of the seam's name, `R2.hash`. That is FNV-1a, the app's `UuidHash.fnv1a`, taken modulo 100 000. The expected values below come from the board's JS.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.figures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShardBandGeometryTest {
    private val items = List(18) { BandItem("d$it", on = it < 14, isNew = it == 13) }
    private val shards = ShardBandGeometry.shards(items, width = 300f, height = 30f)

    @Test
    fun `the seams jitter as the board's hash does`() {
        // R2.hash("R0t") = 0.14289 and R2.hash("R0b") = 0.11431, over a 300 / 18 cell at 70 %.
        assertEquals(-4.1662833f, ShardBandGeometry.jitter("R0t", 300f / 18), 1e-4f)
        assertEquals(-4.4997167f, ShardBandGeometry.jitter("R0b", 300f / 18), 1e-4f)
    }

    @Test
    fun `neighbours share their seam, the band starts at 0 and ends at its width`() {
        assertEquals(0f, shards.first().corners[0].x, 1e-4f)
        assertEquals(300f, shards.last().corners[1].x, 1e-3f)
        for (i in 0 until shards.lastIndex) {
            assertEquals(shards[i].corners[1], shards[i + 1].corners[0])
            assertEquals(shards[i].corners[2], shards[i + 1].corners[3])
        }
    }

    @Test
    fun `each shard keeps its item, its dot under its middle`() {
        assertEquals(14, shards.count { it.on })
        assertTrue(shards[13].isNew)
        assertEquals(13.5f * 300f / 18, shards[13].dot.x, 1e-3f)
        assertEquals(36f, shards[13].dot.y, 1e-4f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.ShardBandGeometryTest"`
Expected: FAIL to compile, with `Unresolved reference: ShardBandGeometry`.

- [ ] **Step 3: Write the geometry**

`figures/ShardBandGeometry.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import app.pbbls.android.core.common.UuidHash

/** The mended band (the board's `shardStrip`, S2 pick 4), in dp of a [width] by [height] strip. */
object ShardBandGeometry {
    private const val HASH_RANGE = 100_000L
    private const val JITTER = 0.7f
    const val DOT_DROP = 6f
    const val DOT_R = 2.4f

    data class Shard(
        val corners: List<Offset>,
        val on: Boolean,
        val isNew: Boolean,
        val dot: Offset,
    )

    /** The board's `(R2.hash(key) − .5) × cell × .7`: a seam's lean, stable for its name. */
    fun jitter(
        key: String,
        cell: Float,
    ): Float = ((UuidHash.fnv1a(key) % HASH_RANGE).toFloat() / HASH_RANGE - 0.5f) * cell * JITTER

    fun shards(
        items: List<BandItem>,
        width: Float,
        height: Float,
    ): List<Shard> {
        if (items.isEmpty()) return emptyList()
        val cell = width / items.size
        var x = 0f
        var leftTop = 0f
        var leftBottom = 0f
        return items.mapIndexed { i, item ->
            val last = i == items.lastIndex
            val rightTop = if (last) 0f else jitter("R${i}t", cell)
            val rightBottom = if (last) 0f else jitter("R${i}b", cell)
            val shard =
                Shard(
                    corners =
                        listOf(
                            Offset(x + leftTop, 0f),
                            Offset(x + cell + rightTop, 0f),
                            Offset(x + cell + rightBottom, height),
                            Offset(x + leftBottom, height),
                        ),
                    on = item.on,
                    isNew = item.isNew,
                    dot = Offset(x + cell / 2, height + DOT_DROP),
                )
            leftTop = rightTop
            leftBottom = rightBottom
            x += cell
            shard
        }
    }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.ShardBandGeometryTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Write the band**

`figures/ShardBand.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.render.stone.StoneTones

private const val ON_TINT = 0.08f
private const val ON_STROKE = 1.3f
private const val OFF_STROKE = 1f
private const val DOT_HALO = 0.8f
private val DOT_ROOM = 10.dp

/**
 * The domains' mended band: an inked shard per domain that came, in the order it came, then a
 * dotted shard per domain not yet there; a new one has a dot under it. One node: [description].
 */
@Composable
fun ShardBand(
    items: List<BandItem>,
    description: String,
    modifier: Modifier = Modifier,
    width: Dp = 300.dp,
    height: Dp = 30.dp,
) {
    val colors = PebblesTheme.spine.colors
    val fill = remember(colors.ink, colors.stock) { StoneTones.mixOklab(colors.ink, ON_TINT, colors.stock) }
    // As wide as the room allows, up to [width]; the shards are cut for the width the band gets.
    Canvas(
        modifier.fillMaxWidth().widthIn(max = width).aspectRatio(width / (height + DOT_ROOM)).clearAndSetSemantics { contentDescription = description },
    ) {
        val shards = ShardBandGeometry.shards(items, size.width / density, height.value)
        withTransform({ scale(density, density, Offset.Zero) }) {
            shards.forEach { shard ->
                val path =
                    Path().apply {
                        shard.corners.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                        close()
                    }
                if (shard.on) {
                    drawPath(path, fill)
                    drawPath(path, colors.ink, style = Stroke(ON_STROKE, join = StrokeJoin.Round))
                } else {
                    drawPath(
                        path,
                        colors.ink2,
                        style = Stroke(OFF_STROKE, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f, 2.2f))),
                    )
                }
            }
            shards.filter { it.isNew }.forEach {
                drawCircle(colors.stock, ShardBandGeometry.DOT_R + DOT_HALO, it.dot)
                drawCircle(colors.ink, ShardBandGeometry.DOT_R, it.dot)
            }
        }
    }
}
```

- [ ] **Step 6: Compile and commit**

Run: `cd apps/android && ./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/ShardBand*.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/ShardBandGeometryTest.kt
git commit -m "feat(android): draw the domains' mended band in the order they came"
```

### Task 15: The skyline

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/SkylineGeometry.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/Skyline.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/SkylineGeometryTest.kt`

The board's `skyline` has eight stages of stone. The pebbles card reads all eight; glyphs carved and glyph sales, with five steps each, read the first five. A stage the server lists as a level is solid. The next one is filled from its foot by the server's `next.progress`. The contour turns solid ink once every stage stands.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.StackLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkylineGeometryTest {
    private fun stack(
        count: Int,
        reached: List<Int>,
        next: NextLevel?,
        ladder: List<Int> = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
    ) = CountingSection(count, reached.map { StackLevel(it, null, null, false) }, next, ladder, null)

    @Test
    fun `three pebbles - one stage stands, the second rises by the server's progress`() {
        assertEquals(SkylineFill(stages = 8, full = 1, partial = 0.222f), stack(3, listOf(1), NextLevel(10, 7, 0.222f)).skylineFill())
    }

    @Test
    fun `glyphs read five stages, a top-of-ladder stack stands whole`() {
        val sales = stack(60, listOf(1, 5, 10, 25, 50), null, ladder = listOf(1, 5, 10, 25, 50)).skylineFill()
        assertEquals(SkylineFill(stages = 5, full = 5, partial = 0f), sales)
        assertTrue(sales.done)
    }

    @Test
    fun `past the ladder the skyline stands whole while the next level is beyond it`() {
        val big = stack(1200, listOf(1, 10, 25, 50, 100, 250, 500, 1000), NextLevel(1500, 300, 0.4f)).skylineFill()
        assertEquals(8, big.full)
        assertEquals(0f, big.partial)
        assertTrue(big.done)
        assertFalse(stack(3, listOf(1), NextLevel(10, 7, 0.222f)).skylineFill().done)
    }

    @Test
    fun `the contour runs from 16 along the stages to the end`() {
        assertEquals(
            "M16 148 L23 148 C23 143.3 29 143.3 29 148 L32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 L126 148",
            SkylineGeometry.contour(stages = 2, end = 126f),
        )
    }

    @Test
    fun `a partial stage is clipped from its foot by its fraction`() {
        val clip = SkylineGeometry.partialClip(stage = 1, fraction = 0.5f)
        assertEquals(30.5f, clip.left, 1e-4f)
        assertEquals(51.5f, clip.right, 1e-4f)
        assertEquals(148f - (148f - 133.2f) * 0.5f, clip.top, 1e-4f)
        assertEquals(149f, clip.bottom, 1e-4f)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.SkylineGeometryTest"`
Expected: FAIL to compile, with `Unresolved reference: skylineFill`.

- [ ] **Step 3: Write the geometry**

`figures/SkylineGeometry.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Rect
import app.pbbls.android.core.model.achievements.CountingSection
import kotlin.math.min

/** How much of a skyline stands: [full] stages solid, the next filled by [partial], of [stages]. */
data class SkylineFill(
    val stages: Int,
    val full: Int,
    val partial: Float,
) {
    val done: Boolean get() = full >= stages
}

/** The skyline a counting stack draws, read off its levels and its next (never its count). */
fun CountingSection.skylineFill(): SkylineFill {
    val stages = min(ladder.size, SkylineGeometry.STAGES.size)
    val steps = ladder.take(stages)
    val reached = levels.map { it.threshold }.toSet()
    val full = steps.count { it in reached }
    val partial = next?.takeIf { steps.indexOf(it.threshold) == full }?.progress ?: 0f
    return SkylineFill(stages, full, partial)
}

/** The board's one skyline (g/92, its second section), in its own units: the ground at y = 148. */
object SkylineGeometry {
    const val GROUND = 148f
    const val START = 16f

    val STAGES: List<String> =
        listOf(
            "M23 148 C23 143.3 29 143.3 29 148 Z",
            "M32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 Z",
            "M53 148 C52.6 133.5 56.8 128.7 63.3 128.7 C68.8 128.7 72.4 136 72 148 Z",
            "M75.5 148 L76.7 135.5 L83.1 125.2 L89.9 127.5 L95.5 138.4 L95.5 148 Z",
            "M99 148 C98.6 128.4 105 121.8 112.8 121.8 C118.8 121.8 122.4 131.8 122 148 Z",
            "M125.5 148 L125.9 123.5 C126.3 116.7 133.4 116.1 134 122.8 L134 148 Z",
            "M141 148 L141 120.7 L137.5 120.7 C137.2 114.5 145.9 113.9 151.5 113.9 C157.1 113.9 165.8 114.5 165.5 120.7 " +
                "L162 120.7 L162 148 L158 148 L158 120.7 L145 120.7 L145 148 Z",
            "M169 148 C180.5 147.2 182 110.5 194.9 110.5 C208.4 110.5 207.4 145.8 217 148 Z",
        )
    val STAGE_X: List<Pair<Float, Float>> =
        listOf(23f to 29f, 32.5f to 49.5f, 53f to 72f, 75.5f to 95.5f, 99f to 122f, 125.5f to 134f, 137.5f to 165.5f, 169f to 217f)
    val STAGE_TOP: List<Float> = listOf(144.5f, 133.2f, 128.7f, 125.2f, 121.8f, 117.4f, 113.9f, 110.5f)

    /** A view onto the skyline: the board's `viewBox` and where its contour ends. */
    data class Viewport(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val end: Float,
    )

    /** The card's and the sheet's skyline (`12 104 216 48`), and the glyph lines' (`18 118 110 32`). */
    val BIG = Viewport(12f, 104f, 216f, 48f, end = 224f)
    val MINI = Viewport(18f, 118f, 110f, 32f, end = 126f)

    fun contour(
        stages: Int,
        end: Float,
    ): String {
        val endText = if (end % 1f == 0f) end.toInt().toString() else end.toString()
        return "M16 148 " + STAGES.take(stages).joinToString(" ") { "L" + it.drop(1).removeSuffix(" Z") } + " L$endText 148"
    }

    fun partialClip(
        stage: Int,
        fraction: Float,
    ): Rect {
        val (x0, x1) = STAGE_X[stage]
        val rise = (GROUND - STAGE_TOP[stage]) * fraction
        return Rect(left = x0 - 2f, top = GROUND - rise, right = x1 + 2f, bottom = GROUND + 1f)
    }
}
```

The test's contour string joins the stages on single spaces, and `STAGES[6]` is concatenated across two lines with a single space. That keeps the strings byte-identical to the board's.

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.SkylineGeometryTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Write the skyline and its stage**

`figures/Skyline.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.figures.SkylineGeometry as G
import kotlin.math.min

/** A skyline path placed: board units → px, `p' = (p − origin) × scale`. */
private fun placed(
    d: String,
    scale: Float,
    origin: Offset,
): Path =
    PathParser().parsePathString(d).toPath().also { p ->
        p.translate(-origin)
        p.transform(Matrix().apply { scale(scale, scale) })
    }

/**
 * The skyline (g/92): [fill]'s stages in ink, the next rising from its foot, the contour over all
 * of them in secondary ink until every stage stands. [viewport] is the board's view box; the
 * height follows it. As wide as the room allows, up to [width]. One node: [description].
 */
@Composable
fun Skyline(
    fill: SkylineFill,
    description: String,
    modifier: Modifier = Modifier,
    viewport: SkylineGeometry.Viewport = G.BIG,
    width: Dp = 300.dp,
) {
    val colors = PebblesTheme.spine.colors
    Canvas(
        modifier.fillMaxWidth().widthIn(max = width).aspectRatio(viewport.width / viewport.height).clearAndSetSemantics { contentDescription = description },
    ) {
        val scale = size.width / viewport.width
        val origin = Offset(viewport.left, viewport.top)
        for (k in 0 until fill.full.coerceAtMost(fill.stages)) drawPath(placed(G.STAGES[k], scale, origin), colors.ink)
        if (fill.full < fill.stages && fill.partial > 0f) {
            val r = G.partialClip(fill.full, fill.partial)
            clipRect(
                left = (r.left - origin.x) * scale,
                top = (r.top - origin.y) * scale,
                right = (r.right - origin.x) * scale,
                bottom = (r.bottom - origin.y) * scale,
            ) { drawPath(placed(G.STAGES[fill.full], scale, origin), colors.ink) }
        }
        drawPath(
            placed(G.contour(fill.stages, viewport.end), scale, origin),
            if (fill.done) colors.ink else colors.ink2,
            style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private const val STAGE_W_PAD = 4f
private const val STAGE_H_PAD = 2f
private const val STAGE_BOX_W = 28f
private const val STAGE_BOX_H = 26f
private const val BASELINE_OVERHANG = 3f

/**
 * One stage alone, as a register row's mark (the board's `stage`): solid when [fill] is 1,
 * otherwise its contour with the fill it has, on its own ground line. Stages past the eighth
 * reuse the last. Decorative.
 */
@Composable
fun SkylineStage(
    stage: Int,
    fill: Float,
    modifier: Modifier = Modifier,
    box: Dp = 28.dp,
    scale: Float = 1f,
) {
    val colors = PebblesTheme.spine.colors
    val k = stage.coerceIn(0, G.STAGES.lastIndex)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val (x0, x1) = G.STAGE_X[k]
        val top = G.STAGE_TOP[k]
        val w = x1 - x0 + STAGE_W_PAD
        val h = G.GROUND - top + STAGE_H_PAD
        val unit = size.width / STAGE_BOX_W
        val s = min(STAGE_BOX_W / w, STAGE_BOX_H / h) * scale * unit
        val origin = Offset((x0 + x1) / 2 - center.x / s, (G.GROUND + top) / 2 - center.y / s)
        if (fill >= 1f) {
            drawPath(placed(G.STAGES[k], s, origin), colors.ink)
        } else {
            if (fill > 0f) {
                val r = G.partialClip(k, fill)
                clipRect(
                    left = (r.left - origin.x) * s,
                    top = (r.top - origin.y) * s,
                    right = (r.right - origin.x) * s,
                    bottom = (r.bottom - origin.y) * s,
                ) { drawPath(placed(G.STAGES[k], s, origin), colors.ink) }
            }
            drawPath(placed(G.STAGES[k], s, origin), colors.ink2, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
        }
        val y = (G.GROUND - origin.y) * s
        drawLine(colors.ink2, Offset((x0 - BASELINE_OVERHANG - origin.x) * s, y), Offset((x1 + BASELINE_OVERHANG - origin.x) * s, y), 1.dp.toPx())
    }
}
```

- [ ] **Step 6: Compile and commit**

Run: `cd apps/android && ./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/Skyline*.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/SkylineGeometryTest.kt
git commit -m "feat(android): draw the counting stacks as the skyline and its stages"
```

### Task 16: The year disc

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/YearDiscGeometry.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/YearDisc.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/YearDiscGeometryTest.kt`

This is the docked disc, card B, decided on 28 October. A month's counter sits at the place its level reached: the centre at radius 30, the inner circle at 50, the rim at 80. Its progress toward the next level is drawn along the spoke.

A month with pebbles is a solid seal in its hue; a rim month adds a hairline ring and a numeral in its hue. A month without pebbles is a blank seal in secondary ink, dotted if it is still to come. The stone, grain, rings, spokes, notches and numerals are the board's `g/112` base, which `build.py` extracts; the geometry below is that base, read off its SVG.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.MonthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YearDiscGeometryTest {
    private val g = YearDiscGeometry

    private fun month(
        level: Int,
        progress: Float,
        state: MonthState = MonthState.KEPT,
        family: String? = "joy",
        m: Int = 1,
    ) = AchievementMonth(m, state, family, 0, 0, 0, 0, level, progress)

    @Test
    fun `the base sits where the board's g 112 svg draws it`() {
        // Numeral II at (168.0, 39.4) is r 96 at 30°, baseline 2.5 under the centre.
        val ii = g.point(g.NUMERAL_R, g.angle(2))
        assertEquals(168f, ii.x, 0.05f)
        assertEquals(36.9f, ii.y, 0.05f)
        // Month I's inner notch is the segment (117, 70)–(123, 70).
        val (a, b) = g.notch(1, g.RADII[1])
        assertEquals(117f, a.x, 1e-3f)
        assertEquals(70f, a.y, 1e-3f)
        assertEquals(123f, b.x, 1e-3f)
        assertEquals(70f, b.y, 1e-3f)
    }

    @Test
    fun `a rim month docks on the rim with its whole climb in its hue`() {
        val mark = g.mark(month(2, 0f))
        assertEquals(80f, mark.counterR)
        assertTrue(mark.rim)
        assertEquals(56f..73f, mark.climb)
        assertTrue(mark.climbInHue)
        assertFalse(mark.goalNotch)
    }

    @Test
    fun `an inner-disc month climbs toward the rim in its hue by its progress`() {
        val mark = g.mark(month(1, 0.5f))
        assertEquals(50f, mark.counterR)
        assertEquals(56f..(56f + 0.5f * 18f), mark.climb)
        assertTrue(mark.climbInHue)
    }

    @Test
    fun `a month below the inner disc climbs in ink and shows its goal, unless muted`() {
        val begun = g.mark(month(0, 0.25f))
        assertEquals(30f, begun.counterR)
        assertEquals(36f..(36f + 0.25f * 8f), begun.climb)
        assertFalse(begun.climbInHue)
        assertTrue(begun.goalNotch)
        val quiet = g.mark(month(0, 0.25f, MonthState.QUIET, family = null))
        assertTrue(quiet.muted)
        assertFalse(quiet.goalNotch)
        val untouched = g.mark(month(0, 0f))
        assertNull(untouched.climb)
        assertTrue(g.mark(month(0, 0f, MonthState.UPCOMING, family = null)).upcoming)
    }

    @Test
    fun `the disc reads as the server's lists`() {
        val months =
            listOf(month(2, 0f, m = 8), month(1, 0f, m = 3), month(0, 0.3f, m = 11), month(0, 0f, MonthState.UPCOMING, null, m = 12))
        assertEquals(listOf(8), g.onRim(months))
        assertEquals(listOf(3), g.onInner(months))
        assertEquals(listOf(11), g.onTheirWay(months))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.YearDiscGeometryTest"`
Expected: FAIL to compile, with `Unresolved reference: YearDiscGeometry`.

- [ ] **Step 3: Write the geometry**

`figures/YearDiscGeometry.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.MonthState
import kotlin.math.cos
import kotlin.math.sin

/**
 * How one month draws on the docked disc. [counterR] is the place its level reached. [climb] is
 * the stretch of spoke inked, from and to a radius. [muted] holds for a month with no pebble.
 */
data class MonthMark(
    val month: Int,
    val angle: Float,
    val counterR: Float,
    val climb: ClosedFloatingPointRange<Float>?,
    val climbInHue: Boolean,
    val goalNotch: Boolean,
    val rim: Boolean,
    val muted: Boolean,
    val upcoming: Boolean,
)

/** The year disc (g/112, docked; the board's `yearDiscDocked`), in its 220 view from 10 to 230, centred at 120. */
object YearDiscGeometry {
    const val VIEW_MIN = 10f
    const val VIEW = 220f
    const val CENTRE = 120f
    val RADII = floatArrayOf(30f, 50f, 80f)
    const val SPOKE_IN = 14f
    const val SPOKE_OUT = 88f
    const val NOTCH_HALF = 3f
    const val NUMERAL_R = 96f
    const val GOAL_NOTCH_DEG = 4.6f
    const val INNER_RING = 52f
    const val INNER_RING_2 = 48.5f
    const val COUNTER_WIDTH = 12.5f
    const val CLIMB_GAP = 6f
    const val RIM_CLIMB_STOP = 7f
    private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")

    /** The stone's edge: the board's 40-point outline, verbatim. */
    const val STONE =
        "M225.1,120.0 L223.0,136.3 L217.4,151.6 L211.9,166.8 L205.0,181.7 L192.4,192.4 L181.0,203.9 L167.6,213.5 " +
            "L151.9,218.2 L136.1,221.4 L120.0,225.2 L103.6,223.5 L87.6,219.6 L72.9,212.5 L58.7,204.4 L46.3,193.7 " +
            "L34.7,182.0 L27.8,167.0 L20.4,152.4 L17.4,136.3 L15.6,120.0 L16.7,103.6 L20.4,87.6 L27.0,72.6 L36.8,59.6 " +
            "L45.4,45.4 L58.2,35.0 L73.4,28.6 L88.0,21.5 L103.9,18.3 L120.0,14.9 L136.0,18.8 L152.0,21.7 L167.3,27.1 " +
            "L180.7,36.5 L192.8,47.2 L203.3,59.5 L213.5,72.4 L220.4,87.4 L222.5,103.8 Z"

    /** The stone's grain: x, y, r, verbatim. */
    val GRAIN: List<Triple<Float, Float, Float>> =
        listOf(
            Triple(217.6f, 135.5f, 0.7f), Triple(25.8f, 134.9f, 0.8f), Triple(105.0f, 25.2f, 0.6f), Triple(37.7f, 179.8f, 0.9f),
            Triple(25.9f, 134.9f, 0.8f), Triple(189.4f, 50.6f, 0.8f), Triple(209.8f, 74.2f, 0.7f), Triple(89.4f, 25.9f, 0.9f),
            Triple(52.0f, 52.0f, 0.8f), Triple(25.7f, 105.1f, 0.8f), Triple(217.5f, 135.4f, 0.8f), Triple(197.8f, 63.5f, 0.8f),
            Triple(75.7f, 207.0f, 0.9f), Triple(150.1f, 27.3f, 0.7f), Triple(25.1f, 135.0f, 0.6f), Triple(135.0f, 214.5f, 0.5f),
            Triple(89.0f, 24.5f, 0.9f), Triple(75.3f, 32.2f, 0.5f), Triple(25.7f, 134.9f, 0.6f), Triple(63.8f, 42.6f, 0.7f),
            Triple(176.8f, 41.8f, 0.5f), Triple(120.0f, 18.6f, 0.8f), Triple(105.1f, 26.1f, 0.6f), Triple(197.9f, 176.6f, 0.5f),
            Triple(120.0f, 23.9f, 0.9f), Triple(164.2f, 206.7f, 0.5f), Triple(42.2f, 176.5f, 0.9f), Triple(178.3f, 200.2f, 0.7f),
            Triple(89.6f, 213.6f, 0.7f), Triple(204.2f, 162.9f, 0.5f), Triple(207.2f, 75.6f, 0.8f), Triple(176.1f, 42.8f, 0.8f),
            Triple(120.0f, 219.1f, 0.5f), Triple(219.4f, 104.3f, 0.7f), Triple(90.6f, 29.7f, 0.6f), Triple(90.5f, 210.8f, 0.9f),
            Triple(216.3f, 88.7f, 0.6f), Triple(120.0f, 220.0f, 0.5f), Triple(135.4f, 22.6f, 0.7f), Triple(20.7f, 135.7f, 0.5f),
        )

    /** January at twelve o'clock, clockwise, 30° a month. */
    fun angle(month: Int): Float = (month - 1) * 30f

    fun point(
        r: Float,
        deg: Float,
    ): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(CENTRE + (r * sin(a)).toFloat(), CENTRE - (r * cos(a)).toFloat())
    }

    /** A notch across a month's spoke at radius [r]: 3 either side, perpendicular. */
    fun notch(
        month: Int,
        r: Float,
    ): Pair<Offset, Offset> {
        val a = Math.toRadians(angle(month).toDouble())
        val p = point(r, angle(month))
        val across = Offset(cos(a).toFloat(), sin(a).toFloat()) * NOTCH_HALF
        return (p - across) to (p + across)
    }

    fun numeral(month: Int): String = NUMERALS[month - 1]

    fun mark(m: AchievementMonth): MonthMark {
        val level = m.level.coerceIn(0, 2)
        val progress = if (level >= 2) 0f else m.progress
        val upcoming = m.state == MonthState.UPCOMING
        val muted = upcoming || m.family == null
        val climb =
            when {
                level == 2 -> (RADII[1] + CLIMB_GAP)..(RADII[2] - RIM_CLIMB_STOP)
                progress > 0f -> {
                    val from = RADII[level] + CLIMB_GAP
                    from..(from + progress * (RADII[level + 1] - RADII[level] - 2 * CLIMB_GAP))
                }
                else -> null
            }
        return MonthMark(
            month = m.month,
            angle = angle(m.month),
            counterR = RADII[level],
            climb = climb,
            climbInHue = level >= 1 && m.family != null,
            goalNotch = level == 0 && progress > 0f && !muted,
            rim = level == 2,
            muted = muted,
            upcoming = upcoming,
        )
    }

    /** The disc read aloud (spec §5): the server's months on the rim, on the inner circle, on their way. */
    fun onRim(months: List<AchievementMonth>): List<Int> = months.filter { it.level >= 2 }.map { it.month }

    fun onInner(months: List<AchievementMonth>): List<Int> = months.filter { it.level == 1 }.map { it.month }

    fun onTheirWay(months: List<AchievementMonth>): List<Int> =
        months.filter { it.level == 0 && it.progress > 0f && it.state != MonthState.UPCOMING }.map { it.month }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.figures.YearDiscGeometryTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Write the disc and the month counter**

`figures/YearDisc.kt`:

```kotlin
package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.figures.YearDiscGeometry as G

private val STONE_PATH: Path by lazy { PathParser().parsePathString(G.STONE).toPath() }
private const val STONE_EDGE = 1.2f
private const val STONE_EDGE_ALPHA = 0.85f
private const val RING_ALPHA = 0.5f
private const val RING_2_ALPHA = 0.45f
private const val SPOKE = 0.7f
private const val SPOKE_MUTED = 0.4f
private const val SPOKE_RIM = 1f
private const val NOTCH = 1f
private const val NOTCH_QUIET = 0.6f
private const val GOAL = 1.3f
private const val CLIMB = 1.8f
private const val RIM_RING = 0.78f
private const val RIM_RING_WIDTH = 0.8f
private const val CROSS = 1.1f
private const val CROSS_HALF = 4f

/** A month's hue: its family's, or ink for a family Android does not know (spec §4); null without pebbles. */
@Composable
internal fun monthHues(
    months: List<AchievementMonth>,
    lookups: AchievementLookups,
): Map<Int, Color?> {
    val colors = PebblesTheme.spine.colors
    return months.associate { m -> m.month to m.family?.let { lookups.family(it)?.palette?.hue(colors.isNight) ?: colors.ink } }
}

/** The counter prints a year needs: a solid seal per hue, and the blank and dotted ones in secondary ink. */
@Composable
internal fun rememberCounterPrints(hues: Collection<Color?>): Map<InkKey, SealPrint> {
    val colors = PebblesTheme.spine.colors
    val keys =
        hues.filterNotNull().map { InkKey(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, it) } +
            listOf(InkKey(ValenceSizeGroup.MEDIUM, InkPrint.BLANK, colors.ink2), InkKey(ValenceSizeGroup.MEDIUM, InkPrint.DOTTED, colors.ink2))
    return rememberInkPrints(keys, colors.stock, SealVariant.MINI)
}

/** A month's counter at [center], [width] px wide (the board's `monthCounter`). */
internal fun DrawScope.monthCounter(
    mark: MonthMark,
    hue: Color?,
    center: Offset,
    width: Float,
    prints: Map<InkKey, SealPrint>,
    colors: SpineColors,
    unit: Float,
) {
    if (hue != null) {
        if (mark.rim) drawCircle(colors.ink, width * RIM_RING, center, style = Stroke(RIM_RING_WIDTH * unit))
        sealAt(prints.getValue(InkKey(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, hue)), center, width)
    } else {
        val print = if (mark.upcoming) InkPrint.DOTTED else InkPrint.BLANK
        sealAt(prints.getValue(InkKey(ValenceSizeGroup.MEDIUM, print, colors.ink2)), center, width)
    }
}

/**
 * The docked year disc (D12; the board's B, decided 28 October): twelve spokes on a stone, each
 * month's counter docked where its level reached, its progress inked along the spoke. One node:
 * [description], the disc read as the server's lists.
 */
@Composable
fun YearDisc(
    year: AchievementYear,
    lookups: AchievementLookups,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val hues = monthHues(year.months, lookups)
    val prints = rememberCounterPrints(hues.values)
    val marks = remember(year) { year.months.map(G::mark) }
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().widthIn(max = size).aspectRatio(1f).clearAndSetSemantics { contentDescription = description }) {
        val unit = this.size.width / G.VIEW

        fun px(p: Offset) = (p - Offset(G.VIEW_MIN, G.VIEW_MIN)) * unit

        withTransform({
            scale(unit, unit, Offset.Zero)
            translate(-G.VIEW_MIN, -G.VIEW_MIN)
        }) {
            drawPath(STONE_PATH, colors.stock)
            drawPath(STONE_PATH, colors.ink2, alpha = STONE_EDGE_ALPHA, style = Stroke(STONE_EDGE, join = StrokeJoin.Round))
            G.GRAIN.forEach { (x, y, r) -> drawCircle(colors.ink3, r, Offset(x, y)) }
            drawCircle(colors.ink2, G.INNER_RING, Offset(G.CENTRE, G.CENTRE), alpha = RING_ALPHA, style = Stroke(1f))
            drawCircle(colors.ink2, G.INNER_RING_2, Offset(G.CENTRE, G.CENTRE), alpha = RING_2_ALPHA, style = Stroke(0.5f))
            marks.forEach { mark ->
                val hue = hues[mark.month]
                val spokeColour = if (mark.rim && hue != null) hue else colors.ink2
                val spokeWidth = if (mark.rim && hue != null) SPOKE_RIM else if (mark.muted) SPOKE_MUTED else SPOKE
                drawLine(spokeColour, G.point(G.SPOKE_IN, mark.angle), G.point(G.SPOKE_OUT, mark.angle), spokeWidth)
                listOf(G.RADII[1] to false, G.RADII[2] to true).forEach { (r, isRim) ->
                    val (a, b) = G.notch(mark.month, r)
                    val quiet = mark.muted || (isRim && !mark.rim)
                    drawLine(colors.ink2, a, b, if (quiet) NOTCH_QUIET else NOTCH)
                }
                if (mark.goalNotch) {
                    drawLine(
                        colors.ink,
                        G.point(G.RADII[1], mark.angle + G.GOAL_NOTCH_DEG),
                        G.point(G.RADII[1], mark.angle - G.GOAL_NOTCH_DEG),
                        GOAL,
                        StrokeCap.Round,
                    )
                }
                mark.climb?.let { climb ->
                    val colour = if (mark.climbInHue && hue != null) hue else colors.ink
                    drawLine(colour, G.point(climb.start, mark.angle), G.point(climb.endInclusive, mark.angle), CLIMB, StrokeCap.Round)
                }
            }
            drawLine(colors.ink2, Offset(G.CENTRE - CROSS_HALF, G.CENTRE), Offset(G.CENTRE + CROSS_HALF, G.CENTRE), CROSS)
            drawLine(colors.ink2, Offset(G.CENTRE, G.CENTRE - CROSS_HALF), Offset(G.CENTRE, G.CENTRE + CROSS_HALF), CROSS)
        }
        marks.forEach { mark ->
            val hue = hues[mark.month]
            val style = if (mark.rim && hue != null) type.discNumeralRim.copy(color = hue) else type.discNumeral.copy(color = colors.ink2)
            val layout = measurer.measure(G.numeral(mark.month), style)
            val at = px(G.point(G.NUMERAL_R, mark.angle))
            drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
            monthCounter(mark, hue, px(G.point(mark.counterR, mark.angle)), G.COUNTER_WIDTH * unit, prints, colors, unit)
        }
    }
}

/** A month's counter alone, the year table's mark (the board's `counterIcon`, 14 px in 28). Decorative. */
@Composable
fun MonthCounter(
    month: AchievementMonth,
    lookups: AchievementLookups,
    modifier: Modifier = Modifier,
    box: Dp = 28.dp,
) {
    val colors = PebblesTheme.spine.colors
    val hue = monthHues(listOf(month), lookups)[month.month]
    val prints = rememberCounterPrints(listOf(hue))
    val mark = remember(month) { G.mark(month) }
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f
        monthCounter(mark, hue, center, 14f * unit, prints, colors, unit)
    }
}
```

`withTransform`'s block applies `scale` then `translate` to the drawing. A point `p` lands at `(p − 10) × unit`, the same as `px(p)` for the text and the seals, which are drawn outside the transform at their own pixel sizes.

- [ ] **Step 6: Compile and commit**

Run: `cd apps/android && ./gradlew compileDebugKotlin testDebugUnitTest --tests "app.pbbls.android.architecture.*"`
Expected: `BUILD SUCCESSFUL`.

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/figures/YearDisc*.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/figures/YearDiscGeometryTest.kt
git commit -m "feat(android): draw the docked year disc from the server's months"
```

### Task 17: The fixture, the figure previews, the PR

**Files:**
- Create: `apps/android/app/src/debug/kotlin/app/pbbls/android/AchievementsFixture.kt`
- Create: `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementFiguresScreenshots.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/AchievementsFixtureTest.kt`

The fixture is the board's page as a model:
- since 1 March 2026, 249 days;
- the first pebble is "Mara moves in" (joy, medium, bright);
- 23 of 38 emotions, two of them new;
- 14 of 18 domains, Current events new;
- 137 pebbles, 12 glyphs carved, 3 sales;
- the year 2026: August on the rim; March to October, August aside, on the inner disc (April at 0.86 toward the rim); November on its way; December to come.

It lives in `src/debug` beside `SpineFixtureWeek`, whose families and palettes it reuses, so previews and tests share it.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements

import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.model.achievements.Coverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The board's page, as the previews draw it: the numbers the boards were judged on (#1307). */
class AchievementsFixtureTest {
    private val page = AchievementsFixture.page

    @Test
    fun `the fixture carries the board's numbers`() {
        assertEquals(249, page.daysKept)
        assertEquals(38, page.emotions!!.seats.size)
        assertEquals(23, page.emotions!!.seats.count { it.met })
        assertEquals(2, page.emotions!!.seats.count { it.isNew })
        assertEquals(14, page.domains!!.entered.size)
        assertEquals(Coverage.MOST, page.domains!!.coverage)
        assertEquals(137, page.pebbles!!.count)
        assertEquals(listOf(8), page.years.single().months.filter { it.level == 2 }.map { it.month })
        assertNotNull(page.explorer!!.firstPebble)
    }

    @Test
    fun `every seat's family and every domain resolve through the lookups`() {
        val lookups = AchievementsFixture.lookups
        page.emotions!!.seats.forEach { assertNotNull(it.family?.let(lookups.family)) }
        page.domains!!.entered.forEach { assertNotNull(lookups.domain(it.domainId)) }
        page.domains!!.notYet.forEach { assertNotNull(lookups.domain(it)) }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.AchievementsFixtureTest"`
Expected: FAIL to compile, with `Unresolved reference: AchievementsFixture`.

- [ ] **Step 3: Write the fixture**

`apps/android/app/src/debug/kotlin/app/pbbls/android/AchievementsFixture.kt`:

```kotlin
package app.pbbls.android

import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionWithPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.Completion
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.DomainEntry
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.FirstFiveItem
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.FirstSubject
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.NextStep
import app.pbbls.android.core.model.achievements.PebbleSnapshot
import app.pbbls.android.core.model.achievements.SixFirst
import app.pbbls.android.core.model.achievements.StackLevel
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.core.model.achievements.SubjectKind
import app.pbbls.android.core.model.achievements.TwentyFive
import app.pbbls.android.features.achievements.AchievementLookups
import java.time.Instant
import java.time.LocalDate

/**
 * The achievements board's page (#1307; `final/index.html`, `NOTES.md`) as the server would send
 * it, already mapped. The families and their palettes are `SpineFixtureWeek`'s. The 38 emotions'
 * family split is a fixture's, not the live table's: the previews need every family run, not
 * the real membership. Data only.
 */
internal object AchievementsFixture {
    private val SPLIT: List<Pair<String, List<String>>> =
        listOf(
            "joy" to listOf("amazed", "amused", "excited", "grateful", "happy", "hopeful", "joyful", "passionate", "surprised"),
            "pride" to listOf("brave", "confident", "proud", "relieved", "satisfied"),
            "anger" to listOf("angry", "annoyed", "disgusted", "frustrated", "irritated", "jealous"),
            "fear" to listOf("anxious", "overwhelmed", "scared", "stressed", "worried"),
            "shame" to listOf("ashamed", "embarrassed", "guilty"),
            "sadness" to listOf("disappointed", "discouraged", "drained", "hopeless", "lonely", "sad"),
            "peace" to listOf("calm", "content", "indifferent", "peaceful"),
        )
    private val DOMAINS =
        listOf(
            "family", "friends", "partner", "work", "health", "hobbies", "travel", "money", "identity", "education", "selfcare",
            "fitness", "tasks", "currentevents", "community", "dating", "spirituality", "weather",
        )
    private const val MET_DOMAINS = 14

    private val emotions: Map<String, EmotionWithPalette> =
        SPLIT
            .flatMap { (family, slugs) ->
                slugs.map { slug ->
                    EmotionWithPalette(
                        id = "emotion-$slug",
                        slug = slug,
                        name = slug.replaceFirstChar { it.uppercase() },
                        emoji = "",
                        categoryId = "category-$family",
                        categorySlug = family,
                        categoryName = family.replaceFirstChar { it.uppercase() },
                        palette = SpineFixtureWeek.families.getValue(family).palette,
                    )
                }
            }.associateBy { it.id }

    private val domains: Map<String, Domain> =
        DOMAINS.associate { slug -> "domain-$slug" to Domain(id = "domain-$slug", slug = slug, name = slug, label = "") }

    val lookups: AchievementLookups = AchievementLookups(SpineFixtureWeek.families::get, emotions::get, domains::get)

    private var nextMoment = 0

    private fun pebble(
        name: String,
        day: String,
        emotion: String,
        valence: Valence,
    ) = PebbleSnapshot("moment-${nextMoment++}", name, Instant.parse("${day}T18:00:00Z"), "emotion-$emotion", valence)

    private val first = pebble("Mara moves in", "2026-03-02", "joyful", Valence.HIGHLIGHT_MEDIUM)

    /** Which emotions were met, by seat: 23 of 38, every family touched. */
    private val metSlugs =
        setOf(
            "amazed", "excited", "grateful", "happy", "hopeful", "joyful", "surprised", "brave", "proud", "relieved", "angry",
            "frustrated", "anxious", "scared", "worried", "ashamed", "disappointed", "lonely", "sad", "calm", "content", "peaceful",
            "satisfied",
        )
    private val newSlugs = setOf("sad", "peaceful")

    private val seats: List<EmotionSeat> =
        SPLIT
            .flatMap { (family, slugs) -> slugs.map { it to family } }
            .mapIndexed { seat, (slug, family) ->
                val met = slug in metSlugs
                val valence = if (family in setOf("sadness", "fear", "shame", "anger")) Valence.LOWLIGHT_MEDIUM else Valence.HIGHLIGHT_MEDIUM
                EmotionSeat(
                    seat = seat,
                    emotionId = "emotion-$slug",
                    family = family,
                    met = met,
                    pebble = if (met) pebble("A $slug evening", "2026-0${3 + seat % 7}-1${seat % 9}", slug, valence) else null,
                    isNew = slug in newSlugs,
                )
            }

    private fun month(
        m: Int,
        state: MonthState,
        family: String?,
        moments: Int,
        pebbles: Int,
        fullWeeks: Int,
        filled: Int,
        level: Int,
        progress: Float,
    ) = AchievementMonth(m, state, family, moments, pebbles, fullWeeks, filled, level, progress)

    private val year2026 =
        AchievementYear(
            year = 2026,
            current = true,
            months =
                listOf(
                    month(1, MonthState.QUIET, null, 0, 0, 3, 0, 0, 0f),
                    month(2, MonthState.QUIET, null, 0, 0, 4, 0, 0, 0f),
                    month(3, MonthState.KEPT, "joy", 16, 12, 4, 4, 1, 0.86f),
                    month(4, MonthState.KEPT, "sadness", 15, 12, 3, 3, 1, 0.86f),
                    month(5, MonthState.KEPT, "peace", 17, 13, 4, 4, 1, 0.93f),
                    month(6, MonthState.KEPT, "joy", 14, 10, 4, 4, 1, 0.71f),
                    month(7, MonthState.KEPT, "pride", 16, 11, 4, 4, 1, 0.79f),
                    month(8, MonthState.KEPT, "joy", 16, 14, 4, 4, 2, 0f),
                    month(9, MonthState.KEPT, "fear", 15, 9, 3, 3, 1, 0.64f),
                    month(10, MonthState.KEPT, "peace", 18, 13, 4, 4, 1, 0.93f),
                    month(11, MonthState.QUIET, null, 1, 0, 0, 0, 0, 0.07f),
                    month(12, MonthState.UPCOMING, null, 0, 0, 0, 0, 0, 0f),
                ),
            listed = (11 downTo 3).toList(),
            nextStep = NextStep(month = 11, needs = Needs.MOMENTS, daysLeft = 13, reachable = true),
        )

    private val pebbleLevels =
        listOf(1, 10, 25, 50, 100).mapIndexed { i, t ->
            StackLevel(t, LocalDate.of(2026, 3 + i, 2), pebble("Pebble number $t", "2026-0${3 + i}-02", "happy", Valence.HIGHLIGHT_SMALL), i == 4)
        }

    val page: AchievementsPage =
        AchievementsPage(
            since = LocalDate.of(2026, 3, 1),
            daysKept = 249,
            explorer =
                ExplorerSection(
                    firstPebble = first,
                    firstFive =
                        FirstStep.entries.map { step ->
                            FirstFiveItem(step, done = true, on = LocalDate.of(2026, 3, 2), subject = FirstSubject(SubjectKind.MOMENT, first.momentId, step.slug), isNew = false)
                        },
                    sixFirsts =
                        StoneKind.entries.map { kind ->
                            SixFirst(kind, done = true, pebble = pebble("The first ${kind.slug} one", "2026-03-0${3 + kind.ordinal}", "calm", Valence.NEUTRAL_MEDIUM), isNew = false)
                        },
                    twentyFive = TwentyFive(25, done = true, pebble = pebble("A walk by the canal", "2026-04-11", "content", Valence.NEUTRAL_SMALL), isNew = false),
                    ringsDone = setOf(ExplorerRing.FIRST_FIVE, ExplorerRing.SIX_FIRSTS, ExplorerRing.TWENTY_FIVE),
                    completion =
                        Completion(
                            completedAt = Instant.parse("2026-03-07T10:00:00Z"),
                            offeredFrom = Instant.parse("2026-03-08T05:00:00Z"),
                            ready = true,
                            pulledAt = null,
                        ),
                ),
            emotions = EmotionsSection(count = 23, of = 38, seats = seats, completion = null),
            domains =
                DomainsSection(
                    count = MET_DOMAINS,
                    of = DOMAINS.size,
                    coverage = Coverage.MOST,
                    entered =
                        DOMAINS.take(MET_DOMAINS).asReversed().mapIndexed { i, slug ->
                            DomainEntry("domain-$slug", pebble("At the $slug table", "2026-0${3 + i % 7}-2${i % 8}", "happy", Valence.HIGHLIGHT_LARGE), isNew = slug == "currentevents")
                        },
                    notYet = DOMAINS.drop(MET_DOMAINS).map { "domain-$it" },
                    completion = null,
                ),
            pebbles = CountingSection(137, pebbleLevels, NextLevel(250, 113, 0.248f), listOf(1, 10, 25, 50, 100, 250, 500, 1000), 500),
            glyphsCarved = CountingSection(12, listOf(StackLevel(10, LocalDate.of(2026, 6, 1), null, false)), NextLevel(25, 13, 0.133f), listOf(10, 25, 50, 75, 100), null),
            glyphSales = CountingSection(3, listOf(StackLevel(1, LocalDate.of(2026, 7, 9), null, false)), NextLevel(5, 2, 0.5f), listOf(1, 5, 10, 25, 50), null),
            years = listOf(year2026),
        )
}
```

The day strings above build valid ISO dates for every index used. Seat months run from `03` to `09`, and seat days from `10` to `18`. Domain months run from `03` to `09`, and domain days from `20` to `27`.

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.AchievementsFixtureTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Write the figure previews**

`apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementFiguresScreenshots.kt`:

```kotlin
package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.figures.CardLadder
import app.pbbls.android.features.achievements.figures.CardLadders
import app.pbbls.android.features.achievements.figures.ExplorerWheel
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.ShardBand
import app.pbbls.android.features.achievements.figures.Skyline
import app.pbbls.android.features.achievements.figures.SkylineGeometry
import app.pbbls.android.features.achievements.figures.YearDisc
import app.pbbls.android.features.achievements.figures.domainBandItems
import app.pbbls.android.features.achievements.figures.skylineFill
import com.android.tools.screenshot.PreviewTest

/** The five organisms and the ladder (#1307) on the board's page, in paper and night. */
@Composable
private fun Figures() {
    val page = AchievementsFixture.page
    val lookups = AchievementsFixture.lookups
    Surface(color = PebblesTheme.spine.colors.stock) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExplorerWheel(page.explorer!!, page.emotions, page.domains, lookups, description = "")
            Rosace(page.emotions!!, lookups, description = "", centre = "23" to "OF 38")
            ShardBand(domainBandItems(page.domains!!), description = "")
            Skyline(page.pebbles!!.skylineFill(), description = "")
            Skyline(page.glyphsCarved!!.skylineFill(), description = "", viewport = SkylineGeometry.MINI, width = 103.dp)
            YearDisc(page.years.single(), lookups, description = "")
            CardLadder(CardLadders.year(page.years.single()))
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@Composable
fun AchievementFiguresLight() {
    PebblesTheme { Figures() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementFiguresDark() {
    PebblesTheme { Figures() }
}
```

- [ ] **Step 6: Look at the renders locally, then gate the part**

```bash
cd apps/android
./gradlew updateDebugScreenshotTest --tests "*AchievementFigures*"   # to LOOK only: never commit these PNGs
open app/src/screenshotTestDebug/reference/app/pbbls/android/AchievementFiguresScreenshotsKt/
git checkout -- app/src/screenshotTestDebug/reference/ && git clean -fd app/src/screenshotTestDebug/reference/
./gradlew testDebugUnitTest lint ktlintCheck assembleDebug
```

Compare the renders with `final/index.html` side by side, in paper and night:
- the wheel's five rings, with two new emotions and Current events new;
- the rosace's lit runs and the two dots;
- the band's 14 inked shards;
- the skyline's five stages and its quarter-filled sixth;
- the disc's August on the rim and November leaving the centre.

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit, then open the PR**

```bash
git add apps/android/app/src/debug/kotlin/app/pbbls/android/AchievementsFixture.kt \
  apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementFiguresScreenshots.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/AchievementsFixtureTest.kt
git commit -m "test(android): preview the achievements figures on the board's page"
```

Open the PR with `gh stack`, titled `feat(android): draw the achievements figures`. The body opens with `Part of #1307`. Use the same labels and milestone as part 1.

Add the `rebaseline-screenshots` label so CI renders the two new references, then approve the held runs. Do not re-run the failed one.

No Lab Note; the part is not user-visible yet.

---
# Part 3 — the copy and the five sheets (branch `feat/1307-android-achievements-sheets`, stacked on part 2)

Every sheet uses the board's register template (`final/sheets.html`, pick 3):
- a title band;
- the figure;
- dated sections, each a small-caps title with an italic count;
- rows of at least 48 dp that open their pebble;
- then "not yet" as dotted names.

Colour appears only in emotion hues. The sheets are stateless; the host takes the stack as a parameter, and part 4's ViewModel owns it.

### Task 18: The page's copy

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/copy/AchievementsCopy.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `apps/android/app/src/main/res/values-fr/strings.xml`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/copy/AchievementsCopyTest.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/copy/AchievementsCopyFrenchTest.kt`

The server sends semantic values; this class is the one place they become words (spec §4, "Localise"):
- a coverage tier becomes its sentence;
- `next_step` becomes the current month's line;
- the skyline becomes its line;
- the disc becomes its spoken lists;
- months, dates and numerals follow the active locale.

The number words come from `core/common/NumberWords`, and the French plurals carry `many`.

**The day target.** The year table reads `n/14`, but the payload sends the counts and not the 14 (D12's target). Android holds it as `DAYS_TARGET` with a pointer to D12. It is reported as a contract gap: the server should send it.

- [ ] **Step 1: Write the failing tests**

`AchievementsCopyTest.kt`:

```kotlin
package app.pbbls.android.features.achievements.copy

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.NextStep
import app.pbbls.android.core.model.achievements.StackLevel
import app.pbbls.android.features.achievements.figures.skylineFill
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/** The page's sentences over the real `values/strings.xml` (#1307, spec §4–§5), the board's words. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AchievementsCopyTest {
    private val copy = AchievementsCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.ENGLISH)

    @Test
    fun `each coverage tier reads as its sentence`() {
        assertEquals("Your pebbles reach most parts of your life. Your insights have plenty to read.", copy.coverage(Coverage.MOST))
        assertEquals(
            "Link your pebbles to the parts of your life they touch, and your insights will start reading them.",
            copy.coverage(Coverage.NONE),
        )
        assertEquals("Every part of your life has a pebble in it. Your insights see the whole picture.", copy.coverage(Coverage.ALL))
    }

    @Test
    fun `the next step reads in days, in each of its forms`() {
        assertEquals(
            "Twelve more days with a moment, one in each full week, bring November to the inner disc.",
            copy.nextStep(NextStep(11, Needs.MOMENTS, daysLeft = 12, reachable = true)),
        )
        assertEquals(
            "One more day with a pebble, one in each full week, brings March to the rim.",
            copy.nextStep(NextStep(3, Needs.PEBBLES, daysLeft = 1, reachable = true)),
        )
        assertEquals(
            "Keep a moment in every full week and October reaches the inner disc.",
            copy.nextStep(NextStep(10, Needs.MOMENTS, daysLeft = 0, reachable = true)),
        )
        assertEquals(
            "A full week went by without a pebble, so June stays off the rim this month.",
            copy.nextStep(NextStep(6, Needs.PEBBLES, daysLeft = 4, reachable = false)),
        )
    }

    @Test
    fun `the skyline's line names the stones that stand and the one rising`() {
        val pebbles =
            CountingSection(
                count = 137,
                levels = listOf(1, 10, 25, 50, 100).map { StackLevel(it, null, null, false) },
                next = NextLevel(250, 113, 0.25f),
                ladder = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
                thenEvery = 500,
            )
        assertEquals("137 pebbles. Five stones stand; the sixth rises toward 250.", copy.pebblesLine(pebbles, pebbles.skylineFill()))
        val one = pebbles.copy(count = 1, levels = listOf(StackLevel(1, null, null, false)), next = NextLevel(10, 9, 0f))
        assertEquals("1 pebble. One stone stands; the second rises toward 10.", copy.pebblesLine(one, one.skylineFill()))
    }

    @Test
    fun `counts, milestones and dates read in words and in the locale`() {
        assertEquals("five of eight", copy.ofWords(5, 8))
        assertEquals("113 to go", copy.toGo(113))
        assertEquals("then every 500", copy.thenEvery(500))
        assertEquals("November", copy.monthName(11))
        assertEquals("Apr 21", copy.shortDate(LocalDate.of(2026, 4, 21)))
        assertEquals("since Sunday, March 1, 2026", copy.since(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `the disc is read as the server's lists`() {
        fun m(
            month: Int,
            level: Int,
            progress: Float,
        ) = AchievementMonth(month, MonthState.KEPT, "joy", 0, 0, 0, 0, level, progress)
        val months = (1..12).map { m(it, if (it == 8) 2 else if (it in 3..5) 1 else 0, if (it == 11) 0.2f else 0f) }
        assertEquals(
            "2026. On the rim: August. On the inner circle: March, April, May. On their way: November.",
            copy.discDescription(AchievementYear(2026, true, months, emptyList(), null)),
        )
    }
}
```

`AchievementsCopyFrenchTest.kt`:

```kotlin
package app.pbbls.android.features.achievements.copy

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextStep
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The same sentences over `values-fr/strings.xml` (#1307): French plurals (one, many, other) and genders. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "fr")
class AchievementsCopyFrenchTest {
    private val copy = AchievementsCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.FRENCH)

    @Test
    fun `the next step agrees in number`() {
        assertEquals(
            "Encore douze jours avec un moment, un par semaine complète, et novembre rejoint le disque intérieur.",
            copy.nextStep(NextStep(11, Needs.MOMENTS, daysLeft = 12, reachable = true)),
        )
        assertEquals(
            "Encore un jour avec un galet, un par semaine complète, et mars rejoint le bord.",
            copy.nextStep(NextStep(3, Needs.PEBBLES, daysLeft = 1, reachable = true)),
        )
    }

    @Test
    fun `counts read as French words, stones are feminine`() {
        assertEquals("cinq sur huit", copy.ofWords(5, 8))
        assertEquals("Tes galets touchent la plupart des parts de ta vie. Tes analyses ont de quoi lire.", copy.coverage(Coverage.MOST))
        assertEquals("novembre", copy.monthName(11))
        assertEquals("Une pierre se dresse", copy.standing(1))
        assertEquals("Cinq pierres se dressent", copy.standing(5))
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.copy.*"`
Expected: FAIL to compile, with `Unresolved reference: AchievementsCopy`.

- [ ] **Step 3: Add the strings**

In `apps/android/app/src/main/res/values/strings.xml`, before `</resources>`:

```xml
    <!-- ==== The achievements page (#1307): the board's words, from the server's semantic values ==== -->
    <string name="achievements_since">since %1$s</string>
    <plurals name="achievements_days_unit">
        <item quantity="one">day</item>
        <item quantity="other">days</item>
    </plurals>
    <string name="achievements_card_explorer">Explorer</string>
    <string name="achievements_card_emotions">Emotions</string>
    <string name="achievements_card_domains">Domains</string>
    <string name="achievements_card_pebbles">Pebbles</string>
    <string name="achievements_card_year">The year</string>
    <string name="achievements_explorer_hint">five rings</string>
    <string name="achievements_domains_hint">in the order they came</string>
    <string name="achievements_domains_register">the register</string>
    <string name="achievements_year_in_progress">in progress</string>
    <string name="achievements_coverage_none">Link your pebbles to the parts of your life they touch, and your insights will start reading them.</string>
    <string name="achievements_coverage_few">You haven\'t linked many pebbles to domains yet, so your insights have little to go on.</string>
    <string name="achievements_coverage_third">Your pebbles touch a third of your life. Link a few more domains and your insights will see further.</string>
    <string name="achievements_coverage_half">You\'ve already linked pebbles with half of the domains. What a rich life!</string>
    <string name="achievements_coverage_most">Your pebbles reach most parts of your life. Your insights have plenty to read.</string>
    <string name="achievements_coverage_all">Every part of your life has a pebble in it. Your insights see the whole picture.</string>
    <plurals name="achievements_pebbles_count">
        <item quantity="one">%1$s pebble.</item>
        <item quantity="other">%1$s pebbles.</item>
    </plurals>
    <plurals name="achievements_pebbles_kept">
        <item quantity="one">%1$s pebble kept.</item>
        <item quantity="other">%1$s pebbles kept.</item>
    </plurals>
    <plurals name="achievements_skyline_standing">
        <item quantity="one">%1$s stone stands</item>
        <item quantity="other">%1$s stones stand</item>
    </plurals>
    <string name="achievements_skyline_none">No stone stands yet</string>
    <string name="achievements_skyline_rising">the %1$s rises toward %2$s</string>
    <string name="achievements_skyline_all">every stone stands</string>
    <string name="achievements_skyline_join">%1$s; %2$s.</string>
    <string name="achievements_of_words">%1$s of %2$s</string>
    <string name="achievements_count_of">%1$d of %2$d</string>
    <string name="achievements_rosace_of">of %1$d</string>
    <plurals name="achievements_to_go">
        <item quantity="one">%1$s to go</item>
        <item quantity="other">%1$s to go</item>
    </plurals>
    <string name="achievements_then_every">then every %1$s</string>
    <plurals name="achievements_next_moments">
        <item quantity="one">%1$s more day with a moment, one in each full week, brings %2$s to the inner disc.</item>
        <item quantity="other">%1$s more days with a moment, one in each full week, bring %2$s to the inner disc.</item>
    </plurals>
    <plurals name="achievements_next_pebbles">
        <item quantity="one">%1$s more day with a pebble, one in each full week, brings %2$s to the rim.</item>
        <item quantity="other">%1$s more days with a pebble, one in each full week, bring %2$s to the rim.</item>
    </plurals>
    <string name="achievements_next_keep_moments">Keep a moment in every full week and %1$s reaches the inner disc.</string>
    <string name="achievements_next_keep_pebbles">Keep a pebble in every full week and %1$s reaches the rim.</string>
    <string name="achievements_next_missed_moments">A full week went by without a moment, so %1$s stays off the inner disc this month.</string>
    <string name="achievements_next_missed_pebbles">A full week went by without a pebble, so %1$s stays off the rim this month.</string>
    <string name="achievements_kind_small">Small</string>
    <string name="achievements_kind_medium">Medium</string>
    <string name="achievements_kind_large">Large</string>
    <string name="achievements_section_first_five">The first five</string>
    <string name="achievements_section_six_firsts">The six firsts</string>
    <string name="achievements_section_twenty_five">25 pebbles</string>
    <string name="achievements_twenty_five_closed">closed</string>
    <string name="achievements_twenty_fifth">The 25th</string>
    <string name="achievements_twenty_five_waiting">The 25th pebble is still to come.</string>
    <string name="achievements_section_domains_emotions">Domains and emotions</string>
    <string name="achievements_section_entered">Entered</string>
    <string name="achievements_newest_first">newest first</string>
    <string name="achievements_register_number">No. %1$02d</string>
    <string name="achievements_section_not_yet">Not yet in these pages</string>
    <string name="achievements_section_milestones">Milestones</string>
    <string name="achievements_section_glyphs_carved">Glyphs carved</string>
    <string name="achievements_section_glyph_sales">Glyph sales</string>
    <string name="achievements_buyer_never_named">The buyer is never named.</string>
    <string name="achievements_section_months">The months</string>
    <string name="achievements_column_moments">moments</string>
    <string name="achievements_column_pebbles">pebbles</string>
    <string name="achievements_column_weeks">weeks</string>
    <string name="achievements_section_earlier_years">Earlier years</string>
    <string name="achievements_first_year">None yet. %1$d is your first year here.</string>
    <string name="achievements_explorer_a11y">Explorer: first five %1$s, six firsts %2$s, 25 pebbles %3$s, domains %4$s, emotions %5$s.</string>
    <string name="achievements_emotions_a11y">Emotions: %1$s met.</string>
    <string name="achievements_domains_a11y">Domains: %1$s. %2$s</string>
    <string name="achievements_pebbles_a11y">Pebbles: %1$d. Glyphs carved: %2$d. Glyph sales: %3$d.</string>
    <string name="achievements_disc_a11y">%1$d. On the rim: %2$s. On the inner circle: %3$s. On their way: %4$s.</string>
    <string name="achievements_none">none</string>
    <string name="achievements_month_a11y">%1$s: %2$d of %3$d days with moments, %4$d of %3$d with pebbles, %5$s.</string>
    <string name="achievements_weeks_filled">%1$d of %2$d weeks filled</string>
    <string name="achievements_no_full_week">no full week yet</string>
    <string name="achievements_new_a11y">new since you last looked</string>
    <string name="achievements_open_pebble">Open the pebble</string>
    <string name="achievements_open_sheet">Open</string>
    <string name="achievements_ready">Ready</string>
    <string name="achievements_ready_a11y">%1$s is complete. Open the ceremony.</string>
```

In `apps/android/app/src/main/res/values-fr/strings.xml`, before `</resources>`:

```xml
    <!-- ==== La page des succès (#1307) ==== -->
    <string name="achievements_since">depuis le %1$s</string>
    <plurals name="achievements_days_unit">
        <item quantity="one">jour</item>
        <item quantity="many">jours</item>
        <item quantity="other">jours</item>
    </plurals>
    <string name="achievements_card_explorer">Explorateur</string>
    <string name="achievements_card_emotions">Émotions</string>
    <string name="achievements_card_domains">Domaines</string>
    <string name="achievements_card_pebbles">Galets</string>
    <string name="achievements_card_year">L\'année</string>
    <string name="achievements_explorer_hint">cinq anneaux</string>
    <string name="achievements_domains_hint">dans l\'ordre d\'arrivée</string>
    <string name="achievements_domains_register">le registre</string>
    <string name="achievements_year_in_progress">en cours</string>
    <string name="achievements_coverage_none">Relie tes galets aux parts de ta vie qu\'ils touchent, et tes analyses commenceront à les lire.</string>
    <string name="achievements_coverage_few">Tu n\'as pas encore relié beaucoup de galets à des domaines : tes analyses ont peu à lire.</string>
    <string name="achievements_coverage_third">Tes galets touchent un tiers de ta vie. Relie quelques domaines de plus et tes analyses verront plus loin.</string>
    <string name="achievements_coverage_half">Tu as déjà relié des galets à la moitié des domaines. Quelle vie riche !</string>
    <string name="achievements_coverage_most">Tes galets touchent la plupart des parts de ta vie. Tes analyses ont de quoi lire.</string>
    <string name="achievements_coverage_all">Chaque part de ta vie a son galet. Tes analyses voient tout le tableau.</string>
    <plurals name="achievements_pebbles_count">
        <item quantity="one">%1$s galet.</item>
        <item quantity="many">%1$s galets.</item>
        <item quantity="other">%1$s galets.</item>
    </plurals>
    <plurals name="achievements_pebbles_kept">
        <item quantity="one">%1$s galet gardé.</item>
        <item quantity="many">%1$s galets gardés.</item>
        <item quantity="other">%1$s galets gardés.</item>
    </plurals>
    <plurals name="achievements_skyline_standing">
        <item quantity="one">%1$s pierre se dresse</item>
        <item quantity="many">%1$s pierres se dressent</item>
        <item quantity="other">%1$s pierres se dressent</item>
    </plurals>
    <string name="achievements_skyline_none">Aucune pierre ne se dresse encore</string>
    <string name="achievements_skyline_rising">la %1$s s\'élève vers %2$s</string>
    <string name="achievements_skyline_all">toutes les pierres se dressent</string>
    <string name="achievements_skyline_join">%1$s ; %2$s.</string>
    <string name="achievements_of_words">%1$s sur %2$s</string>
    <string name="achievements_count_of">%1$d sur %2$d</string>
    <string name="achievements_rosace_of">sur %1$d</string>
    <plurals name="achievements_to_go">
        <item quantity="one">encore %1$s</item>
        <item quantity="many">encore %1$s</item>
        <item quantity="other">encore %1$s</item>
    </plurals>
    <string name="achievements_then_every">puis tous les %1$s</string>
    <plurals name="achievements_next_moments">
        <item quantity="one">Encore %1$s jour avec un moment, un par semaine complète, et %2$s rejoint le disque intérieur.</item>
        <item quantity="many">Encore %1$s jours avec un moment, un par semaine complète, et %2$s rejoint le disque intérieur.</item>
        <item quantity="other">Encore %1$s jours avec un moment, un par semaine complète, et %2$s rejoint le disque intérieur.</item>
    </plurals>
    <plurals name="achievements_next_pebbles">
        <item quantity="one">Encore %1$s jour avec un galet, un par semaine complète, et %2$s rejoint le bord.</item>
        <item quantity="many">Encore %1$s jours avec un galet, un par semaine complète, et %2$s rejoint le bord.</item>
        <item quantity="other">Encore %1$s jours avec un galet, un par semaine complète, et %2$s rejoint le bord.</item>
    </plurals>
    <string name="achievements_next_keep_moments">Garde un moment dans chaque semaine complète et %1$s rejoint le disque intérieur.</string>
    <string name="achievements_next_keep_pebbles">Garde un galet dans chaque semaine complète et %1$s rejoint le bord.</string>
    <string name="achievements_next_missed_moments">Une semaine complète est passée sans moment : %1$s ne rejoindra pas le disque intérieur ce mois-ci.</string>
    <string name="achievements_next_missed_pebbles">Une semaine complète est passée sans galet : %1$s ne rejoindra pas le bord ce mois-ci.</string>
    <string name="achievements_kind_small">Petit</string>
    <string name="achievements_kind_medium">Moyen</string>
    <string name="achievements_kind_large">Grand</string>
    <string name="achievements_section_first_five">Les cinq premiers</string>
    <string name="achievements_section_six_firsts">Les six premières</string>
    <string name="achievements_section_twenty_five">25 galets</string>
    <string name="achievements_twenty_five_closed">bouclé</string>
    <string name="achievements_twenty_fifth">Le 25e</string>
    <string name="achievements_twenty_five_waiting">Le 25e galet reste à venir.</string>
    <string name="achievements_section_domains_emotions">Domaines et émotions</string>
    <string name="achievements_section_entered">Entrés</string>
    <string name="achievements_newest_first">du plus récent au plus ancien</string>
    <string name="achievements_register_number">N° %1$02d</string>
    <string name="achievements_section_not_yet">Pas encore dans ces pages</string>
    <string name="achievements_section_milestones">Jalons</string>
    <string name="achievements_section_glyphs_carved">Glyphes gravés</string>
    <string name="achievements_section_glyph_sales">Ventes de glyphes</string>
    <string name="achievements_buyer_never_named">L\'acheteur n\'est jamais nommé.</string>
    <string name="achievements_section_months">Les mois</string>
    <string name="achievements_column_moments">moments</string>
    <string name="achievements_column_pebbles">galets</string>
    <string name="achievements_column_weeks">semaines</string>
    <string name="achievements_section_earlier_years">Années précédentes</string>
    <string name="achievements_first_year">Aucune pour l\'instant. %1$d est ta première année ici.</string>
    <string name="achievements_explorer_a11y">Explorateur : cinq premiers %1$s, six premières %2$s, 25 galets %3$s, domaines %4$s, émotions %5$s.</string>
    <string name="achievements_emotions_a11y">Émotions : %1$s rencontrées.</string>
    <string name="achievements_domains_a11y">Domaines : %1$s. %2$s</string>
    <string name="achievements_pebbles_a11y">Galets : %1$d. Glyphes gravés : %2$d. Ventes de glyphes : %3$d.</string>
    <string name="achievements_disc_a11y">%1$d. Au bord : %2$s. Sur le cercle intérieur : %3$s. En chemin : %4$s.</string>
    <string name="achievements_none">aucun</string>
    <string name="achievements_month_a11y">%1$s : %2$d jours sur %3$d avec un moment, %4$d sur %3$d avec un galet, %5$s.</string>
    <string name="achievements_weeks_filled">%1$d semaines sur %2$d remplies</string>
    <string name="achievements_no_full_week">pas encore de semaine complète</string>
    <string name="achievements_new_a11y">nouveau depuis ta dernière visite</string>
    <string name="achievements_open_pebble">Ouvrir le galet</string>
    <string name="achievements_open_sheet">Ouvrir</string>
    <string name="achievements_ready">Prêt</string>
    <string name="achievements_ready_a11y">%1$s est complet. Ouvrir la cérémonie.</string>
```

The French `one` items hold `%1$s` in every quantity on purpose, which keeps lint's `ImpliedQuantity` quiet. French `one` also covers 0. The copy class never sends a 0 to these plurals: `days_left == 0` takes the "keep" sentence, and a count of zero stones takes `achievements_skyline_none`.

- [ ] **Step 4: Write the copy**

`copy/AchievementsCopy.kt`:

```kotlin
package app.pbbls.android.features.achievements.copy

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import app.pbbls.android.R
import app.pbbls.android.core.common.NumberWords
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextStep
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.features.achievements.figures.SkylineFill
import app.pbbls.android.features.achievements.figures.TWENTY_FIVE_SEATS
import app.pbbls.android.features.achievements.figures.YearDiscGeometry
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * D12's day target: a month's level needs 14 days. The payload sends the counts, not the target
 * (reported as a gap in #1307's plan), so the table's `n/14` reads it from here.
 */
const val DAYS_TARGET = 14

/**
 * The achievements page's sentences (#1307, spec §4–§5). The server sends counts, slugs and
 * semantic values, and this is the one place they become words. Plain functions over [Resources],
 * so the tests read the real `strings.xml` in English and French.
 */
class AchievementsCopy(
    private val resources: Resources,
    private val locale: Locale,
) {
    private val numbers: NumberFormat = NumberFormat.getIntegerInstance(locale)

    fun since(date: LocalDate): String =
        resources.getString(
            R.string.achievements_since,
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), locale).format(date),
        )

    fun daysUnit(days: Int): String = resources.getQuantityString(R.plurals.achievements_days_unit, days)

    fun number(n: Int): String = numbers.format(n.toLong())

    fun cardTitle(card: AchievementCard): String =
        resources.getString(
            when (card) {
                AchievementCard.EXPLORER -> R.string.achievements_card_explorer
                AchievementCard.EMOTIONS -> R.string.achievements_card_emotions
                AchievementCard.DOMAINS -> R.string.achievements_card_domains
                AchievementCard.PEBBLES -> R.string.achievements_card_pebbles
                AchievementCard.YEAR -> R.string.achievements_card_year
            },
        )

    fun explorerHint(): String = resources.getString(R.string.achievements_explorer_hint)

    fun domainsHint(): String = resources.getString(R.string.achievements_domains_hint)

    fun domainsRegister(): String = resources.getString(R.string.achievements_domains_register)

    fun yearHint(current: Boolean): String? = if (current) resources.getString(R.string.achievements_year_in_progress) else null

    fun coverage(coverage: Coverage): String =
        resources.getString(
            when (coverage) {
                Coverage.NONE -> R.string.achievements_coverage_none
                Coverage.FEW -> R.string.achievements_coverage_few
                Coverage.THIRD -> R.string.achievements_coverage_third
                Coverage.HALF -> R.string.achievements_coverage_half
                Coverage.MOST -> R.string.achievements_coverage_most
                Coverage.ALL -> R.string.achievements_coverage_all
            },
        )

    /** "Five stones stand" / "Cinq pierres se dressent": stones are feminine in French. */
    fun standing(stones: Int): String =
        if (stones <= 0) {
            resources.getString(R.string.achievements_skyline_none)
        } else {
            capitalize(
                resources
                    .getQuantityString(R.plurals.achievements_skyline_standing, stones)
                    .format(locale, NumberWords.cardinal(stones, locale, NumberWords.Gender.FEMININE)),
            )
        }

    /** The card's line: "137 pebbles. Five stones stand; the sixth rises toward 250." */
    fun pebblesLine(
        stack: CountingSection,
        fill: SkylineFill,
    ): String {
        val count = resources.getQuantityString(R.plurals.achievements_pebbles_count, stack.count).format(locale, number(stack.count))
        val next = stack.next
        val rest =
            when {
                fill.done || next == null -> resources.getString(R.string.achievements_skyline_all)
                else ->
                    resources.getString(
                        R.string.achievements_skyline_rising,
                        NumberWords.ordinal(fill.full + 1, locale, NumberWords.Gender.FEMININE),
                        number(next.threshold),
                    )
            }
        return "$count " + resources.getString(R.string.achievements_skyline_join, standing(fill.full), rest)
    }

    fun pebblesKept(count: Int): String = resources.getQuantityString(R.plurals.achievements_pebbles_kept, count).format(locale, number(count))

    fun ofWords(
        n: Int,
        of: Int,
    ): String = resources.getString(R.string.achievements_of_words, NumberWords.cardinal(n, locale), NumberWords.cardinal(of, locale))

    fun countOf(
        n: Int,
        of: Int,
    ): String = resources.getString(R.string.achievements_count_of, n, of)

    fun rosaceOf(of: Int): String = resources.getString(R.string.achievements_rosace_of, of)

    fun toGo(left: Int): String = resources.getQuantityString(R.plurals.achievements_to_go, left).format(locale, number(left))

    fun thenEvery(step: Int): String = resources.getString(R.string.achievements_then_every, number(step))

    /** The current month's next step (spec §4): days, then "keep", then a level out of reach. */
    fun nextStep(step: NextStep): String {
        val month = monthName(step.month)
        val moments = step.needs == Needs.MOMENTS
        return when {
            !step.reachable ->
                resources.getString(if (moments) R.string.achievements_next_missed_moments else R.string.achievements_next_missed_pebbles, month)
            step.daysLeft <= 0 ->
                resources.getString(if (moments) R.string.achievements_next_keep_moments else R.string.achievements_next_keep_pebbles, month)
            else ->
                capitalize(
                    resources
                        .getQuantityString(if (moments) R.plurals.achievements_next_moments else R.plurals.achievements_next_pebbles, step.daysLeft)
                        .format(locale, NumberWords.cardinal(step.daysLeft, locale), month),
                )
        }
    }

    /** "November" / "novembre": the language's own case for a month in a sentence. */
    fun monthName(month: Int): String {
        val name = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
        return if (locale.language == "fr") name.lowercase(locale) else capitalize(name)
    }

    /** A month as a table row's title: capitalised in every language. */
    fun monthTitle(month: Int): String = capitalize(Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale))

    /** The register's date: "Apr 21" / "21 avr.". */
    fun shortDate(date: LocalDate): String = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(date)

    fun shortDate(
        instant: Instant,
        zone: ZoneId,
    ): String = shortDate(instant.atZone(zone).toLocalDate())

    fun firstFiveTitle(step: FirstStep): String =
        resources.getString(
            when (step) {
                FirstStep.MOMENT -> R.string.achievement_family_first_moment_title
                FirstStep.PEBBLE -> R.string.achievement_family_first_pebble_title
                FirstStep.COLLECTION -> R.string.achievement_family_first_collection_title
                FirstStep.SOUL -> R.string.achievement_family_first_soul_title
                FirstStep.GLYPH -> R.string.achievement_family_first_glyph_title
            },
        )

    /** The app's own words for the six firsts: Small · Medium · Large · Lowlight · Neutral · Highlight. */
    fun sixFirstName(kind: StoneKind): String =
        resources.getString(
            when (kind) {
                StoneKind.SMALL -> R.string.achievements_kind_small
                StoneKind.MEDIUM -> R.string.achievements_kind_medium
                StoneKind.LARGE -> R.string.achievements_kind_large
                StoneKind.LOWLIGHT -> R.string.valence_lowlight
                StoneKind.NEUTRAL -> R.string.valence_neutral
                StoneKind.HIGHLIGHT -> R.string.valence_highlight
            },
        )

    fun string(id: Int): String = resources.getString(id)

    fun firstYear(year: Int): String = resources.getString(R.string.achievements_first_year, year)

    fun explorerDescription(
        explorer: ExplorerSection,
        emotions: EmotionsSection?,
        domains: DomainsSection?,
    ): String =
        resources.getString(
            R.string.achievements_explorer_a11y,
            countOf(explorer.firstFive.count { it.done }, explorer.firstFive.size),
            countOf(explorer.sixFirsts.count { it.done }, explorer.sixFirsts.size),
            countOf(explorer.twentyFive?.count ?: 0, TWENTY_FIVE_SEATS),
            countOf(domains?.count ?: 0, domains?.of ?: 0),
            countOf(emotions?.count ?: 0, emotions?.of ?: 0),
        )

    fun emotionsDescription(emotions: EmotionsSection): String =
        resources.getString(R.string.achievements_emotions_a11y, countOf(emotions.count, emotions.of))

    fun domainsDescription(domains: DomainsSection): String =
        resources.getString(R.string.achievements_domains_a11y, countOf(domains.count, domains.of), domains.coverage?.let(::coverage).orEmpty()).trim()

    fun pebblesDescription(
        pebbles: CountingSection?,
        carved: CountingSection?,
        sales: CountingSection?,
    ): String = resources.getString(R.string.achievements_pebbles_a11y, pebbles?.count ?: 0, carved?.count ?: 0, sales?.count ?: 0)

    /** "2026. On the rim: August. On the inner circle: March, April. On their way: November." */
    fun discDescription(year: AchievementYear): String {
        fun list(months: List<Int>) = if (months.isEmpty()) resources.getString(R.string.achievements_none) else months.joinToString(", ") { monthTitle(it) }
        return resources.getString(
            R.string.achievements_disc_a11y,
            year.year,
            list(YearDiscGeometry.onRim(year.months)),
            list(YearDiscGeometry.onInner(year.months)),
            list(YearDiscGeometry.onTheirWay(year.months)),
        )
    }

    fun monthDescription(month: AchievementMonth): String =
        resources.getString(
            R.string.achievements_month_a11y,
            monthTitle(month.month),
            month.momentDays.coerceAtMost(DAYS_TARGET),
            DAYS_TARGET,
            month.pebbleDays.coerceAtMost(DAYS_TARGET),
            if (month.fullWeeks > 0) {
                resources.getString(R.string.achievements_weeks_filled, month.weeksFilled, month.fullWeeks)
            } else {
                resources.getString(R.string.achievements_no_full_week)
            },
        )

    fun readyDescription(card: AchievementCard): String = resources.getString(R.string.achievements_ready_a11y, cardTitle(card))

    /** A row read aloud: its parts, then "new since you last looked" when it is. */
    fun row(
        vararg parts: String?,
        isNew: Boolean = false,
    ): String = (parts.filterNotNull().filter { it.isNotBlank() } + listOfNotNull(if (isNew) resources.getString(R.string.achievements_new_a11y) else null)).joinToString(", ")

    private fun capitalize(s: String): String = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

@Composable
fun rememberAchievementsCopy(): AchievementsCopy {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(resources, locale) { AchievementsCopy(resources, locale) }
}
```

- [ ] **Step 5: Run the tests to make sure they pass**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.copy.*" --tests "app.pbbls.android.core.ui.LocalizationParityTest"`
Expected: PASS.

If the English `since` reads `since Sunday, March 1, 2026` with different punctuation on the Robolectric ICU, keep the test's string in line with what `getBestDateTimePattern` produces there. The pattern is the platform's, not the plan's.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/copy/ apps/android/app/src/main/res/values/strings.xml \
  apps/android/app/src/main/res/values-fr/strings.xml apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/copy/
git commit -m "feat(android): put the achievements page's semantic values into words, in English and French"
```

The strings are unused until Task 19's sheets call them. Run `./gradlew lint` only at the end of this part (Task 25), not after this commit.

### Task 19: The sheet identities and the register parts

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/AchievementSheet.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/SheetParts.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/SheetPartsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A register row and the ready mark are 48 dp targets that say what they are (#1307, spec §5). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class SheetPartsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a register row is one 48 dp button carrying its description`() {
        var opened = 0
        compose.setContent {
            PebblesTheme {
                RegisterRow(
                    mark = { Box(Modifier.size(24.dp)) },
                    title = "Small",
                    subtitle = "Argument about money",
                    date = "Apr 21",
                    isNew = true,
                    description = "Small, Argument about money, Apr 21, new since you last looked",
                    onClick = { opened++ },
                    clickLabel = "Open the pebble",
                )
            }
        }
        compose
            .onNodeWithContentDescription("Small, Argument about money, Apr 21, new since you last looked")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the ready mark is a 48 dp button`() {
        var pulled = 0
        compose.setContent {
            PebblesTheme { ReadyMark(label = "Ready", description = "Emotions is complete. Open the ceremony.", onClick = { pulled++ }) }
        }
        compose.onNodeWithContentDescription("Emotions is complete. Open the ceremony.").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, pulled)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.SheetPartsTest"`
Expected: FAIL to compile, with `Unresolved reference: RegisterRow`.

- [ ] **Step 3: Write the identities**

`AchievementSheet.kt`:

```kotlin
package app.pbbls.android.features.achievements

import app.pbbls.android.core.model.achievements.AchievementCard

/** What a sheet shows (#1307). Explorer pushes Domains or Emotions on top; the year pushes another year. */
sealed interface AchievementSheet {
    /** The card `mark_achievements_seen` records when this sheet opens. */
    val card: AchievementCard

    data object Explorer : AchievementSheet {
        override val card = AchievementCard.EXPLORER
    }

    data object Emotions : AchievementSheet {
        override val card = AchievementCard.EMOTIONS
    }

    data object Domains : AchievementSheet {
        override val card = AchievementCard.DOMAINS
    }

    data object Pebbles : AchievementSheet {
        override val card = AchievementCard.PEBBLES
    }

    data class Year(
        val year: Int,
    ) : AchievementSheet {
        override val card: AchievementCard get() = AchievementCard.YEAR
    }
}
```

- [ ] **Step 4: Write the parts**

`sheets/SheetParts.kt`:

```kotlin
package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.features.achievements.figures.ghost

private val BAND_TOP = 16.dp
private val BAND_BOTTOM = 9.dp
private val SECTION_TOP = 28.dp
private val SECTION_BOTTOM = 7.dp
private val MARK_COLUMN = 34.dp
private val NEW_DOT = 5.dp
private val NEW_DOT_GAP = 6.dp
private val GHOST_BOX = 10.dp

/** A sheet's title band (the board's `.band`): small caps, an italic hint, an optional trailing mark, a rule. */
@Composable
fun SheetBand(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(
        modifier = modifier.fillMaxWidth().rule(colors.cardRule, above = false).padding(top = BAND_TOP, bottom = BAND_BOTTOM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = type.cardBand, color = colors.ink, modifier = Modifier.semantics { heading() })
        if (hint != null) Text(hint, style = type.cardHint, color = colors.ink2, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A dated section's head (the board's `.lh`): the Spine's section header over a card rule. */
@Composable
fun SheetSection(
    label: String,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    SpineSectionHeader(
        label = label,
        hint = hint,
        modifier = modifier.padding(top = SECTION_TOP).rule(PebblesTheme.spine.colors.cardRule, above = false).padding(bottom = SECTION_BOTTOM),
    )
}

/** The register's date column: the date, with the "new" dot before it. */
@Composable
private fun DateMark(
    date: String?,
    isNew: Boolean,
) {
    val colors = PebblesTheme.spine.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isNew) {
            Canvas(Modifier.size(NEW_DOT)) { drawCircle(colors.ink) }
            Spacer(Modifier.width(NEW_DOT_GAP))
        }
        if (date != null) Text(date, style = PebblesTheme.spine.type.registerDate, color = colors.ink2)
    }
}

/**
 * A register row (the board's `.r`): a mark, a small-caps name with the pebble's name under it,
 * the date and "new" dot at the end. Built on the wraps' [MarkRow], at least 48 dp tall, one node
 * saying [description]; with [onClick], a button.
 */
@Composable
fun RegisterRow(
    mark: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    date: String?,
    isNew: Boolean,
    description: String,
    onClick: (() -> Unit)?,
    clickLabel: String?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = PebblesTheme.spine.type.soulName,
    markWidth: Dp = MARK_COLUMN,
    trailing: (@Composable () -> Unit)? = null,
) {
    val end: (@Composable () -> Unit)? =
        trailing ?: if (date != null || isNew) {
            { DateMark(date, isNew) }
        } else {
            null
        }
    MarkRow(
        title = title,
        description = description,
        modifier = modifier,
        leading = mark,
        titleStyle = titleStyle,
        subtitle = subtitle,
        trailing = end,
        onClick = onClick,
        clickLabel = clickLabel,
        minHeight = SpineLayoutTokens.TOUCH_MIN,
        markWidth = markWidth,
    )
}

/** What is not yet there, set quietly (the board's `.rest`): a ghost before each name. */
@Composable
fun NotYetNames(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    FlowRow(
        modifier = modifier.fillMaxWidth().rule(colors.rule, above = false).padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        names.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(GHOST_BOX)) { ghost(center, 3.6f * density, density, colors) }
                Text(name, style = PebblesTheme.spine.type.registerSub, color = colors.ink2)
            }
        }
    }
}

/**
 * A completed set's quiet "ready" mark (D9): a dot and the word, a stock text button (48 dp target
 * through Material's minimum interactive size), [description] read aloud. It starts the ceremony.
 * Its semantics are set whole, so the click action is restored by hand, as `MarkRow` does.
 */
@Composable
fun ReadyMark(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    TextButton(
        onClick = onClick,
        modifier =
            modifier.heightIn(min = SpineLayoutTokens.TOUCH_MIN).clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
    ) {
        Box(Modifier.size(6.dp)) { Canvas(Modifier.size(6.dp)) { drawCircle(colors.ink) } }
        Spacer(Modifier.width(7.dp))
        Text(label, style = PebblesTheme.spine.type.cardHint, color = colors.ink)
    }
}
```

- [ ] **Step 5: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.SheetPartsTest"`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/AchievementSheet.kt \
  apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/SheetParts.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/SheetPartsTest.kt
git commit -m "feat(android): add the achievements sheets' band, register row, dotted names and ready mark"
```

### Task 20: The Explorer sheet

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/ExplorerSheet.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/ExplorerSheetTest.kt`

The board's Explorer sheet holds one short section per ring, from the inside out:
- the first five, in the Home order, with their marks and dates;
- the six firsts, in the app's words, each with the first pebble of its kind;
- the 25th pebble;
- one row each that opens Domains and Emotions on top. The maintainer settled on 29 October that they push their own sheet.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class ExplorerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val pushed = mutableListOf<AchievementSheet>()
    private val opened = mutableListOf<String>()
    private val pulled = mutableListOf<CompletionStack>()

    private fun show() {
        val page = AchievementsFixture.page
        compose.setContent {
            PebblesTheme {
                ExplorerSheet(
                    explorer = page.explorer!!,
                    emotions = page.emotions,
                    domains = page.domains,
                    lookups = AchievementsFixture.lookups,
                    zone = ZoneId.of("Europe/Paris"),
                    onPush = { pushed += it },
                    onOpenMoment = { opened += it },
                    onPull = { pulled += it },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    @Test
    fun `the domains and emotions rows push their own sheet`() {
        show()
        compose.onNodeWithContentDescription("Emotions, 23 of 38", substring = true).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Domains, 14 of 18", substring = true).performScrollTo().performClick()
        assertEquals(listOf(AchievementSheet.Emotions, AchievementSheet.Domains), pushed)
    }

    @Test
    fun `a six-firsts row opens its pebble`() {
        show()
        compose.onNodeWithContentDescription("Lowlight, The first lowlight one", substring = true).performScrollTo().performClick()
        assertEquals(listOf(AchievementsFixture.page.explorer!!.sixFirsts[3].pebble!!.momentId), opened)
    }

    @Test
    fun `the ready mark pulls the six firsts' ceremony`() {
        show()
        compose.onNodeWithContentDescription("Explorer is complete. Open the ceremony.").performClick()
        assertEquals(listOf(CompletionStack.SIX_FIRSTS), pulled)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.ExplorerSheetTest"`
Expected: FAIL to compile, with `Unresolved reference: ExplorerSheet`.

- [ ] **Step 3: Write the sheet**

`sheets/ExplorerSheet.kt`:

```kotlin
package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.SubjectKind
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.ExplorerWheel
import app.pbbls.android.features.achievements.figures.ExplorerWheelGeometry
import app.pbbls.android.features.achievements.figures.FirstFiveMark
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.SixFirstMark
import app.pbbls.android.features.achievements.figures.TWENTY_FIVE_SEATS
import app.pbbls.android.features.achievements.figures.rememberInkSeals
import app.pbbls.android.features.achievements.figures.sealAt
import java.time.ZoneId

private val WHEEL = 340.dp
private val MARK = 28.dp

/** The board's 25-dot ring, the 25th row's mark. */
@Composable
private fun TwentyFiveMark() {
    val colors = PebblesTheme.spine.colors
    Canvas(Modifier.size(MARK).clearAndSetSemantics {}) {
        val unit = size.width / 28f
        drawCircle(colors.ink2, 11f * unit, center, style = androidx.compose.ui.graphics.drawscope.Stroke(unit))
        for (i in 0 until TWENTY_FIVE_SEATS) {
            drawCircle(colors.ink, 1.1f * unit, center + ExplorerWheelGeometry.polar(11f, (i + 0.5f) * 14.4f) * unit)
        }
    }
}

/** A small inked seal, the domains row's mark. */
@Composable
private fun DomainMark() {
    val colors = PebblesTheme.spine.colors
    val seals = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    Canvas(Modifier.size(MARK).clearAndSetSemantics {}) {
        sealAt(seals.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), center, 14.4f * size.width / 28f)
    }
}

@Composable
private fun Chevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = PebblesTheme.spine.colors.ink2,
        modifier = Modifier.size(20.dp),
    )
}

/**
 * The Explorer sheet (the board's `sheetExplorer`): the wheel large, then one section per ring.
 * The Domains and Emotions rows [onPush] their own sheet on top.
 */
@Composable
fun ExplorerSheet(
    explorer: ExplorerSection,
    emotions: EmotionsSection?,
    domains: DomainsSection?,
    lookups: AchievementLookups,
    zone: ZoneId,
    onPush: (AchievementSheet) -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val openPebble = copy.string(R.string.achievements_open_pebble)
    Column(modifier.fillMaxWidth()) {
        val ready: (@Composable () -> Unit)? =
            if (explorer.completion?.ready == true) {
                {
                    ReadyMark(copy.string(R.string.achievements_ready), copy.readyDescription(AchievementCard.EXPLORER), { onPull(CompletionStack.SIX_FIRSTS) })
                }
            } else {
                null
            }
        SheetBand(title = copy.cardTitle(AchievementCard.EXPLORER), hint = copy.explorerHint(), trailing = ready)
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            ExplorerWheel(explorer, emotions, domains, lookups, copy.explorerDescription(explorer, emotions, domains), size = WHEEL)
        }

        SheetSection(copy.string(R.string.achievements_section_first_five), copy.ofWords(explorer.firstFive.count { it.done }, explorer.firstFive.size))
        explorer.firstFive.forEach { item ->
            val title = copy.firstFiveTitle(item.step)
            val date = item.on?.let(copy::shortDate)
            val subject = item.subject
            val opens = subject?.takeIf { it.kind == SubjectKind.MOMENT || it.kind == SubjectKind.PEBBLE }
            RegisterRow(
                mark = { FirstFiveMark(item.step, item.done) },
                title = title,
                subtitle = subject?.name,
                date = date,
                isNew = item.isNew,
                description = copy.row(title, subject?.name, date, isNew = item.isNew),
                onClick = opens?.let { s -> { onOpenMoment(s.id) } },
                clickLabel = opens?.let { openPebble },
            )
        }

        SheetSection(copy.string(R.string.achievements_section_six_firsts), copy.ofWords(explorer.sixFirsts.count { it.done }, explorer.sixFirsts.size))
        explorer.sixFirsts.forEach { first ->
            val title = copy.sixFirstName(first.kind)
            val pebble = first.pebble
            val date = pebble?.let { copy.shortDate(it.happenedAt, zone) }
            RegisterRow(
                mark = { SixFirstMark(first.kind, first.done) },
                title = title,
                subtitle = pebble?.name,
                date = date,
                isNew = first.isNew,
                description = copy.row(title, pebble?.name, date, isNew = first.isNew),
                onClick = pebble?.let { p -> { onOpenMoment(p.momentId) } },
                clickLabel = pebble?.let { openPebble },
            )
        }

        explorer.twentyFive?.let { twentyFive ->
            SheetSection(
                copy.string(R.string.achievements_section_twenty_five),
                if (twentyFive.done) copy.string(R.string.achievements_twenty_five_closed) else copy.countOf(twentyFive.count, TWENTY_FIVE_SEATS),
            )
            val pebble = twentyFive.pebble
            if (pebble == null) {
                Text(copy.string(R.string.achievements_twenty_five_waiting), style = type.still, color = colors.ink2, modifier = Modifier.padding(top = 10.dp))
            } else {
                val title = copy.string(R.string.achievements_twenty_fifth)
                val date = copy.shortDate(pebble.happenedAt, zone)
                RegisterRow(
                    mark = { TwentyFiveMark() },
                    title = title,
                    subtitle = pebble.name,
                    date = date,
                    isNew = twentyFive.isNew,
                    description = copy.row(title, pebble.name, date, isNew = twentyFive.isNew),
                    onClick = { onOpenMoment(pebble.momentId) },
                    clickLabel = openPebble,
                )
            }
        }

        if (domains != null || emotions != null) SheetSection(copy.string(R.string.achievements_section_domains_emotions), hint = null)
        domains?.let { d ->
            val title = copy.cardTitle(AchievementCard.DOMAINS)
            val count = copy.countOf(d.count, d.of)
            RegisterRow(
                mark = { DomainMark() },
                title = title,
                subtitle = count,
                date = null,
                isNew = false,
                description = copy.row(title, count),
                onClick = { onPush(AchievementSheet.Domains) },
                clickLabel = copy.string(R.string.achievements_open_sheet),
                trailing = { Chevron() },
            )
        }
        emotions?.let { e ->
            val title = copy.cardTitle(AchievementCard.EMOTIONS)
            val count = copy.countOf(e.count, e.of)
            RegisterRow(
                mark = { Rosace(e, lookups, description = "", size = MARK, labels = false, ticks = false) },
                title = title,
                subtitle = count,
                date = null,
                isNew = false,
                description = copy.row(title, count),
                onClick = { onPush(AchievementSheet.Emotions) },
                clickLabel = copy.string(R.string.achievements_open_sheet),
                trailing = { Chevron() },
            )
        }
    }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.ExplorerSheetTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/ExplorerSheet.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/ExplorerSheetTest.kt
git commit -m "feat(android): add the Explorer sheet, its rings as dated sections"
```

### Task 21: The Emotions sheet

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/EmotionsSheet.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/EmotionsSheetTest.kt`

The board puts the rosace at 330, then follows each family in seat order:
- what was met, with the seal of the pebble that brought it;
- then what was not, set quietly.

The new marks are a dot by the date. The board lists the met emotions "by date", but the payload carries no such order, so this sheet keeps the server's seat order (see the contract notes).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class EmotionsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a met emotion opens its pebble and says it is new, an unmet one is a quiet name`() {
        val opened = mutableListOf<String>()
        val emotions = AchievementsFixture.page.emotions!!
        compose.setContent {
            PebblesTheme {
                EmotionsSheet(
                    emotions = emotions,
                    lookups = AchievementsFixture.lookups,
                    zone = ZoneId.of("Europe/Paris"),
                    onOpenMoment = { opened += it },
                    onPull = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        compose.onNodeWithContentDescription("Sadness, A sad evening", substring = true).performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("new since you last looked", substring = true).assertCountEquals(2)
        compose.onNodeWithText("Drained").performScrollTo().assertExists()
        assertEquals(listOf(emotions.seats.first { it.emotionId == "emotion-sad" }.pebble!!.momentId), opened)
    }
}
```

`Sadness` and `Drained` are the English catalog names of the `sad` and `drained` slugs (`R.string.emotion_sad_name`, `emotion_drained_name`). The fixture's emotions resolve through `ReferenceStrings` like the real ones.

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.EmotionsSheetTest"`
Expected: FAIL to compile, with `Unresolved reference: EmotionsSheet`.

- [ ] **Step 3: Write the sheet**

`sheets/EmotionsSheet.kt`:

```kotlin
package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.PebbleSealMark
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.familyLabelColor
import app.pbbls.android.features.achievements.figures.familyRuns
import java.time.ZoneId

private val ROSACE = 330.dp

/**
 * The Emotions sheet (the board's `sheetEmotions`): the rosace large, then a register by family, in
 * the server's seat order. A seat whose emotion the device does not know keeps its facet on the
 * rosace and drops its row.
 */
@Composable
fun EmotionsSheet(
    emotions: EmotionsSection,
    lookups: AchievementLookups,
    zone: ZoneId,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val openPebble = copy.string(R.string.achievements_open_pebble)
    Column(modifier.fillMaxWidth()) {
        val ready: (@Composable () -> Unit)? =
            if (emotions.completion?.ready == true) {
                {
                    ReadyMark(copy.string(R.string.achievements_ready), copy.readyDescription(AchievementCard.EMOTIONS), { onPull(CompletionStack.EMOTIONS) })
                }
            } else {
                null
            }
        SheetBand(title = copy.cardTitle(AchievementCard.EMOTIONS), hint = null, trailing = ready)
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Rosace(emotions, lookups, copy.emotionsDescription(emotions), size = ROSACE, centre = copy.number(emotions.count) to copy.rosaceOf(emotions.of))
        }
        familyRuns(emotions.seats).forEach { run ->
            val seats = emotions.seats.subList(run.start, run.start + run.size)
            val family = run.family
            val palette = family?.let { lookups.family(it)?.palette }
            val name = family?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION_CATEGORY, it, lookups.family(it)?.name ?: it) }
            if (name != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 26.dp).rule(colors.cardRule, above = false).padding(bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        style = type.cardBand,
                        color = palette?.let { familyLabelColor(family, it, colors.isNight) } ?: colors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(copy.countOf(run.met, run.size), style = type.sectionHint, color = colors.ink2, modifier = Modifier.padding(start = 8.dp))
                }
            }
            seats.filter { it.met }.forEach { seat ->
                val emotion = lookups.emotion(seat.emotionId) ?: return@forEach
                val pebble = seat.pebble ?: return@forEach
                val title = ReferenceStrings.referenceName(ReferenceType.EMOTION, emotion.slug, emotion.name)
                val date = copy.shortDate(pebble.happenedAt, zone)
                RegisterRow(
                    mark = { PebbleSealMark(pebble, lookups, width = 9f + pebble.valence.intensity * 2.4f) },
                    title = title,
                    subtitle = pebble.name,
                    date = date,
                    isNew = seat.isNew,
                    description = copy.row(title, pebble.name, date, isNew = seat.isNew),
                    onClick = { onOpenMoment(pebble.momentId) },
                    clickLabel = openPebble,
                )
            }
            val notYet =
                seats.filterNot { it.met }.mapNotNull { seat ->
                    lookups.emotion(seat.emotionId)?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION, it.slug, it.name) }
                }
            if (notYet.isNotEmpty()) NotYetNames(notYet)
        }
    }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.EmotionsSheetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/EmotionsSheet.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/EmotionsSheetTest.kt
git commit -m "feat(android): add the Emotions sheet, family by family in seat order"
```

### Task 22: The Domains sheet

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/DomainsSheet.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/DomainsSheetTest.kt`

This is pick 3, as approved:
1. the band;
2. the coverage sentence;
3. the register, numbered by arrival and listed newest first, as the server sends it;
4. "Not yet in these pages".

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class DomainsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the register numbers by arrival, newest first, and opens its pebble`() {
        val opened = mutableListOf<String>()
        val domains = AchievementsFixture.page.domains!!
        compose.setContent {
            PebblesTheme {
                DomainsSheet(domains, AchievementsFixture.lookups, ZoneId.of("Europe/Paris"), { opened += it }, {}, Modifier.verticalScroll(rememberScrollState()))
            }
        }
        compose.onNodeWithText("Your pebbles reach most parts of your life. Your insights have plenty to read.").assertExists()
        compose.onNodeWithContentDescription("No. 14, Events", substring = true).performScrollTo().performClick()
        assertEquals(listOf(domains.entered.first().pebble!!.momentId), opened)
        compose.onNodeWithText("Weather").performScrollTo().assertExists()
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.DomainsSheetTest"`
Expected: FAIL to compile, with `Unresolved reference: DomainsSheet`.

- [ ] **Step 3: Write the sheet**

`sheets/DomainsSheet.kt`:

```kotlin
package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.ShardBand
import app.pbbls.android.features.achievements.figures.domainBandItems
import app.pbbls.android.features.achievements.figures.rememberInkSeals
import app.pbbls.android.features.achievements.figures.sealAt
import java.time.ZoneId

private val NUMBER_COLUMN = 42.dp
private val SEAL_COLUMN = 30.dp

/** The Domains sheet (the board's `sheetDomains`, pick 3): the band, the insight, the register, the not yet. */
@Composable
fun DomainsSheet(
    domains: DomainsSection,
    lookups: AchievementLookups,
    zone: ZoneId,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val seals = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    val openPebble = copy.string(R.string.achievements_open_pebble)

    @Composable
    fun domainName(id: String): String? = lookups.domain(id)?.let { ReferenceStrings.referenceName(ReferenceType.DOMAIN, it.slug, it.name) }

    Column(modifier.fillMaxWidth()) {
        val ready: (@Composable () -> Unit)? =
            if (domains.completion?.ready == true) {
                {
                    ReadyMark(copy.string(R.string.achievements_ready), copy.readyDescription(AchievementCard.DOMAINS), { onPull(CompletionStack.DOMAINS) })
                }
            } else {
                null
            }
        SheetBand(title = copy.cardTitle(AchievementCard.DOMAINS), hint = copy.domainsRegister(), trailing = ready)
        ShardBand(domainBandItems(domains), copy.domainsDescription(domains), modifier = Modifier.padding(top = 16.dp), width = 350.dp)
        domains.coverage?.let { Text(copy.coverage(it), style = type.lead, color = colors.ink, modifier = Modifier.padding(top = 14.dp)) }

        SheetSection(copy.string(R.string.achievements_section_entered), copy.string(R.string.achievements_newest_first))
        domains.entered.forEachIndexed { i, entry ->
            val name = domainName(entry.domainId) ?: return@forEachIndexed
            val number = copy.string(R.string.achievements_register_number).format(domains.entered.size - i)
            val pebble = entry.pebble
            val date = pebble?.let { copy.shortDate(it.happenedAt, zone) }
            RegisterRow(
                mark = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(number, style = type.registerDate, color = colors.ink2, modifier = Modifier.width(NUMBER_COLUMN))
                        Canvas(Modifier.size(SEAL_COLUMN).clearAndSetSemantics {}) {
                            sealAt(seals.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), center, 17f * size.width / 30f)
                        }
                    }
                },
                title = name,
                subtitle = pebble?.name,
                date = date,
                isNew = entry.isNew,
                description = copy.row(number, name, pebble?.name, date, isNew = entry.isNew),
                onClick = pebble?.let { p -> { onOpenMoment(p.momentId) } },
                clickLabel = pebble?.let { openPebble },
                markWidth = NUMBER_COLUMN + SEAL_COLUMN,
            )
        }

        val notYet = domains.notYet.mapNotNull { domainName(it) }
        if (notYet.isNotEmpty()) {
            SheetSection(copy.string(R.string.achievements_section_not_yet), hint = null)
            NotYetNames(notYet)
        }
    }
}
```

`R.string.achievements_register_number` is `No. %1$02d`. `format(Int)` pads it, so 14 reads "No. 14" and 3 reads "No. 03".

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.DomainsSheetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/DomainsSheet.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/DomainsSheetTest.kt
git commit -m "feat(android): add the Domains sheet, the register by arrival"
```

### Task 23: The Pebbles sheet

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/PebblesSheet.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/PebblesSheetTest.kt`

The board's Pebbles sheet:
1. the skyline;
2. the milestones reached, each its own stage with its pebble and date;
3. the next one, with its partial fill and "n to go", never a date;
4. the empty steps, as ghost stages with their number only;
5. "then every 500".

Glyphs carved and glyph sales follow in the same grammar, smaller. Sales say the buyer is never named.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class PebblesSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `reached milestones open their pebble, the next says what is left, then every 500`() {
        val opened = mutableListOf<String>()
        val page = AchievementsFixture.page
        compose.setContent {
            PebblesTheme {
                PebblesSheet(page.pebbles!!, page.glyphsCarved, page.glyphSales, ZoneId.of("Europe/Paris"), { opened += it }, Modifier.verticalScroll(rememberScrollState()))
            }
        }
        compose.onNodeWithText("137 pebbles kept.").assertExists()
        compose.onNodeWithContentDescription("100, Pebble number 100", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("250, 113 to go", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("then every 500").performScrollTo().assertExists()
        compose.onNodeWithText("The buyer is never named.").performScrollTo().assertExists()
        assertEquals(listOf(page.pebbles!!.levels.last().pebble!!.momentId), opened)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.PebblesSheetTest"`
Expected: FAIL to compile, with `Unresolved reference: PebblesSheet`.

- [ ] **Step 3: Write the sheet**

`sheets/PebblesSheet.kt`:

```kotlin
package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.features.achievements.copy.AchievementsCopy
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.Skyline
import app.pbbls.android.features.achievements.figures.SkylineStage
import app.pbbls.android.features.achievements.figures.skylineFill
import java.time.ZoneId

private const val SMALL_STAGE = 0.8f

/**
 * One stack's milestones: the levels the server lists, then its next, then the ladder's steps that
 * are neither, then "then every N" when the ladder continues. [small] is the glyph stacks' grammar.
 */
@Composable
private fun Milestones(
    stack: CountingSection,
    copy: AchievementsCopy,
    zone: ZoneId,
    small: Boolean,
    onOpenMoment: (String) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val numeral = if (small) type.milestoneSmall else type.milestone
    val scale = if (small) SMALL_STAGE else 1f
    val openPebble = copy.string(R.string.achievements_open_pebble)
    stack.levels.forEachIndexed { i, level ->
        val title = copy.number(level.threshold)
        val pebble = level.pebble
        val date = level.on?.let(copy::shortDate) ?: pebble?.let { copy.shortDate(it.happenedAt, zone) }
        RegisterRow(
            mark = { SkylineStage(i, 1f, scale = scale) },
            title = title,
            subtitle = pebble?.name,
            date = date,
            isNew = level.isNew,
            description = copy.row(title, pebble?.name, date, isNew = level.isNew),
            onClick = pebble?.let { p -> { onOpenMoment(p.momentId) } },
            clickLabel = pebble?.let { openPebble },
            titleStyle = numeral,
        )
    }
    val next = stack.next
    if (next != null) {
        val title = copy.number(next.threshold)
        val left = copy.toGo(next.left)
        RegisterRow(
            mark = { SkylineStage(stack.levels.size, next.progress, scale = scale) },
            title = title,
            subtitle = left,
            date = null,
            isNew = false,
            description = copy.row(title, left),
            onClick = null,
            clickLabel = null,
            titleStyle = numeral.copy(color = colors.ink2),
        )
    }
    val reached = stack.levels.map { it.threshold }.toSet()
    stack.ladder.forEachIndexed { i, step ->
        if (step in reached || step == next?.threshold) return@forEachIndexed
        val title = copy.number(step)
        RegisterRow(
            mark = { SkylineStage(i, 0f, scale = scale) },
            title = title,
            subtitle = null,
            date = null,
            isNew = false,
            description = title,
            onClick = null,
            clickLabel = null,
            titleStyle = numeral.copy(color = colors.ink2),
        )
    }
    stack.thenEvery?.let { every ->
        Text(
            copy.thenEvery(every),
            style = type.still,
            color = colors.ink2,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SpineLayoutTokens.ROW_MIN)
                    .rule(colors.rule, above = false)
                    .padding(start = 44.dp, top = 12.dp),
        )
    }
}

/** The Pebbles sheet (the board's `sheetPebbles`): the skyline, the milestones, then glyphs and sales smaller. */
@Composable
fun PebblesSheet(
    pebbles: CountingSection,
    glyphsCarved: CountingSection?,
    glyphSales: CountingSection?,
    zone: ZoneId,
    onOpenMoment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val fill = pebbles.skylineFill()
    Column(modifier.fillMaxWidth()) {
        SheetBand(copy.cardTitle(AchievementCard.PEBBLES), hint = null)
        Skyline(fill, copy.pebblesLine(pebbles, fill), modifier = Modifier.padding(top = 24.dp), width = 350.dp)
        Text(
            copy.pebblesKept(pebbles.count),
            style = type.lead,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        SheetSection(copy.string(R.string.achievements_section_milestones), copy.ofWords(fill.full, fill.stages))
        Milestones(pebbles, copy, zone, small = false, onOpenMoment = onOpenMoment)
        glyphsCarved?.let {
            SheetSection(copy.string(R.string.achievements_section_glyphs_carved), copy.number(it.count))
            Milestones(it, copy, zone, small = true, onOpenMoment = onOpenMoment)
        }
        glyphSales?.let {
            SheetSection(copy.string(R.string.achievements_section_glyph_sales), copy.number(it.count))
            Milestones(it, copy, zone, small = true, onOpenMoment = onOpenMoment)
            Text(copy.string(R.string.achievements_buyer_never_named), style = type.still, color = colors.ink2, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
```

`numeral.copy(color = …)` changes only the colour, which `ThemeLiteralsTest` allows.

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.PebblesSheetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/PebblesSheet.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/PebblesSheetTest.kt
git commit -m "feat(android): add the Pebbles sheet, its milestones as stages"
```

### Task 24: The year sheet

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/YearSheet.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/YearSheetTest.kt`

The board's year sheet:
- the docked disc;
- the months the server lists, newest first, each row with its counter;
- days with moments over 14, days with pebbles over 14, and the full weeks, each cell a number over a hairline gauge;
- the current month's next-step line.

The pebbles gauge takes the month's hue; a month without a pebble is dimmed. Under "Earlier years", each other year is a row that pushes its own sheet.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class YearSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val year = AchievementsFixture.page.years.single()

    @Test
    fun `the listed months read their counts, the next step closes the table`() {
        compose.setContent {
            PebblesTheme { YearSheet(year, otherYears = emptyList(), lookups = AchievementsFixture.lookups, onPush = {}, modifier = Modifier.verticalScroll(rememberScrollState())) }
        }
        compose.onNodeWithContentDescription("August: 14 of 14 days with moments, 14 of 14 with pebbles, 4 of 4 weeks filled.").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("November: 1 of 14 days with moments, 0 of 14 with pebbles, no full week yet.").performScrollTo().assertExists()
        compose.onNodeWithText("Thirteen more days with a moment, one in each full week, bring November to the inner disc.").performScrollTo().assertExists()
        compose.onNodeWithText("None yet. 2026 is your first year here.").performScrollTo().assertExists()
    }

    @Test
    fun `an earlier year pushes its own sheet`() {
        val pushed = mutableListOf<AchievementSheet>()
        compose.setContent {
            PebblesTheme { YearSheet(year, otherYears = listOf(2025), lookups = AchievementsFixture.lookups, onPush = { pushed += it }, modifier = Modifier.verticalScroll(rememberScrollState())) }
        }
        compose.onNodeWithContentDescription("2025", substring = true).performScrollTo().performClick()
        assertEquals(listOf<AchievementSheet>(AchievementSheet.Year(2025)), pushed)
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.YearSheetTest"`
Expected: FAIL to compile, with `Unresolved reference: YearSheet`.

- [ ] **Step 3: Write the sheet**

`sheets/YearSheet.kt`:

```kotlin
package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.copy.DAYS_TARGET
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.MonthCounter
import app.pbbls.android.features.achievements.figures.YearDisc
import app.pbbls.android.features.achievements.figures.monthHues

private val DISC = 330.dp
private val COUNTER_COLUMN = 30.dp
private val CELL = 58.dp
private val GAUGE = 52.dp
private const val MUTED_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.55f
private const val EM_DASH = "—"

/** One table cell (the board's `cell`): a number over a hairline gauge; a full gauge reads as done. */
@Composable
private fun YearCell(
    n: Int,
    of: Int,
    tone: Color,
    doneColor: Color,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val none = of <= 0
    val fraction = if (none) 0f else (n.toFloat() / of).coerceIn(0f, 1f)
    val done = !none && n >= of
    Column(Modifier.width(CELL), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (none) EM_DASH else "${n.coerceAtMost(of)}/$of",
            style = if (done) type.cellNumberDone else type.cellNumber,
            color = if (done) doneColor else colors.ink2,
            textAlign = TextAlign.Center,
        )
        Canvas(Modifier.size(GAUGE, 4.dp)) {
            drawLine(colors.ink2, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(), alpha = TRACK_ALPHA)
            if (fraction > 0f) {
                val h = 2.5.dp.toPx()
                drawRoundRect(tone, topLeft = Offset(0f, (size.height - h) / 2), size = Size(size.width * fraction, h), cornerRadius = CornerRadius(h / 2))
            }
        }
    }
}

/**
 * The year sheet (the board's `sheetYear`): the docked disc large, then one row per month the
 * server lists, newest first, then the next step, then the earlier years as rows that [onPush].
 */
@Composable
fun YearSheet(
    year: AchievementYear,
    otherYears: List<Int>,
    lookups: AchievementLookups,
    onPush: (AchievementSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val hues = monthHues(year.months, lookups)
    val byMonth = year.months.associateBy(AchievementMonth::month)
    Column(modifier.fillMaxWidth()) {
        SheetBand(year.year.toString(), copy.yearHint(year.current))
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            YearDisc(year, lookups, copy.discDescription(year), size = DISC)
        }
        SheetSection(copy.string(R.string.achievements_section_months), copy.string(R.string.achievements_newest_first))
        Row(Modifier.fillMaxWidth().rule(colors.rule, above = false).padding(top = 8.dp, bottom = 4.dp).clearAndSetSemantics {}) {
            Box(Modifier.width(COUNTER_COLUMN))
            Box(Modifier.weight(1f))
            listOf(R.string.achievements_column_moments, R.string.achievements_column_pebbles, R.string.achievements_column_weeks).forEach {
                Text(copy.string(it), style = type.tableHead, color = colors.ink2, textAlign = TextAlign.Center, modifier = Modifier.width(CELL))
            }
        }
        year.listed.mapNotNull(byMonth::get).forEach { month ->
            val hue = hues[month.month]
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SpineLayoutTokens.TOUCH_MIN)
                        .rule(colors.rule, above = false)
                        .clearAndSetSemantics { contentDescription = copy.monthDescription(month) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(COUNTER_COLUMN)) { MonthCounter(month, lookups) }
                val dim = if (hue == null) Modifier.alpha(MUTED_ALPHA) else Modifier
                Text(copy.monthTitle(month.month), style = type.soulName, color = colors.ink, modifier = Modifier.weight(1f).then(dim))
                Row(dim) {
                    YearCell(month.momentDays, DAYS_TARGET, colors.ink, colors.ink)
                    YearCell(month.pebbleDays, DAYS_TARGET, hue ?: colors.ink, hue ?: colors.ink)
                    YearCell(month.weeksFilled, month.fullWeeks, colors.ink, colors.ink)
                }
            }
        }
        year.nextStep?.let { Text(copy.nextStep(it), style = type.lead, color = colors.ink, modifier = Modifier.padding(top = 14.dp)) }

        SheetSection(copy.string(R.string.achievements_section_earlier_years), hint = null)
        if (otherYears.isEmpty()) {
            Text(copy.firstYear(year.year), style = type.still, color = colors.ink2, modifier = Modifier.padding(top = 10.dp))
        } else {
            otherYears.forEach { other ->
                val title = other.toString()
                RegisterRow(
                    mark = {},
                    title = title,
                    subtitle = null,
                    date = null,
                    isNew = false,
                    description = title,
                    onClick = { onPush(AchievementSheet.Year(other)) },
                    clickLabel = copy.string(R.string.achievements_open_sheet),
                    titleStyle = type.milestone,
                    trailing = {
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.ink2, modifier = Modifier.size(20.dp))
                    },
                )
            }
        }
    }
}
```

The band shows the year as `year.toString()`, never through the copy's `number`: `NumberFormat` would group 2026 as "2,026".

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.YearSheetTest"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/YearSheet.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/YearSheetTest.kt
git commit -m "feat(android): add the year sheet, the docked disc over its months"
```

### Task 25: The sheet host, the sheet previews, the PR

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/AchievementSheetHost.kt`
- Create: `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementSheetsScreenshots.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/AchievementSheetContentTest.kt`

Each sheet in the stack is its own `ModalBottomSheet`, opened at the large detent the board shows (`skipPartiallyExpanded`). The stack is the house precedent, as in `GlyphPickerSheet` and the pickers: Explorer's push lays a second sheet over the first, and back dismisses the top one. A sheet whose section has left the payload renders nothing.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class AchievementSheetContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each identity renders its section, a missing section renders nothing`() {
        val page = AchievementsFixture.page.copy(pebbles = null)
        compose.setContent {
            PebblesTheme {
                AchievementSheetContent(AchievementSheet.Domains, page, AchievementsFixture.lookups, ZoneId.of("Europe/Paris"), {}, {}, {})
                AchievementSheetContent(AchievementSheet.Pebbles, page, AchievementsFixture.lookups, ZoneId.of("Europe/Paris"), {}, {}, {})
            }
        }
        compose.onNodeWithText("Domains").assertExists()
        compose.onNodeWithText("Pebbles").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.AchievementSheetContentTest"`
Expected: FAIL to compile, with `Unresolved reference: AchievementSheetContent`.

- [ ] **Step 3: Write the host**

`sheets/AchievementSheetHost.kt`:

```kotlin
package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet
import java.time.ZoneId

/** One sheet's content for its identity; null-safe on the payload (a section that left hides). */
@Composable
fun AchievementSheetContent(
    sheet: AchievementSheet,
    page: AchievementsPage,
    lookups: AchievementLookups,
    zone: ZoneId,
    onPush: (AchievementSheet) -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (sheet) {
        AchievementSheet.Explorer ->
            page.explorer?.let { ExplorerSheet(it, page.emotions, page.domains, lookups, zone, onPush, onOpenMoment, onPull, modifier) }
        AchievementSheet.Emotions -> page.emotions?.let { EmotionsSheet(it, lookups, zone, onOpenMoment, onPull, modifier) }
        AchievementSheet.Domains -> page.domains?.let { DomainsSheet(it, lookups, zone, onOpenMoment, onPull, modifier) }
        AchievementSheet.Pebbles -> page.pebbles?.let { PebblesSheet(it, page.glyphsCarved, page.glyphSales, zone, onOpenMoment, modifier) }
        is AchievementSheet.Year ->
            page.years.firstOrNull { it.year == sheet.year }?.let { year ->
                YearSheet(year, page.years.map { it.year }.filter { it != year.year }, lookups, onPush, modifier)
            }
    }
}

/**
 * The stacked sheets (#1307): one `ModalBottomSheet` per entry of [sheets], the last on top.
 * [onDismiss] pops the top one, which is the only one the user can reach. The stack itself
 * belongs to the screen's ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementSheetHost(
    sheets: List<AchievementSheet>,
    page: AchievementsPage,
    lookups: AchievementLookups,
    zone: ZoneId,
    onPush: (AchievementSheet) -> Unit,
    onDismiss: () -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    sheets.forEachIndexed { depth, sheet ->
        key(depth, sheet) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.stock,
            ) {
                AchievementSheetContent(
                    sheet = sheet,
                    page = page,
                    lookups = lookups,
                    zone = zone,
                    onPush = onPush,
                    onOpenMoment = onOpenMoment,
                    onPull = onPull,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 34.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.sheets.*"`
Expected: PASS.

- [ ] **Step 5: Write the sheet previews**

The previews render the sheets' content, unrolled, on the stock, as the board does. A `ModalBottomSheet` is a separate window that layoutlib does not render.

`apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementSheetsScreenshots.kt`:

```kotlin
package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.sheets.AchievementSheetContent
import com.android.tools.screenshot.PreviewTest
import java.time.ZoneId

/** The five sheets (#1307), unrolled on the stock as `final/sheets.html` shows them, in paper and night. */
@Composable
private fun Sheet(sheet: AchievementSheet) {
    Surface(color = PebblesTheme.spine.colors.stock) {
        AchievementSheetContent(
            sheet = sheet,
            page = AchievementsFixture.page,
            lookups = AchievementsFixture.lookups,
            zone = ZoneId.of("Europe/Paris"),
            onPush = {},
            onOpenMoment = {},
            onPull = {},
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 34.dp),
        )
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1900)
@PreviewFrench
@Composable
fun AchievementSheetExplorerLight() {
    PebblesTheme { Sheet(AchievementSheet.Explorer) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1900, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetExplorerDark() {
    PebblesTheme { Sheet(AchievementSheet.Explorer) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2600)
@Composable
fun AchievementSheetEmotionsLight() {
    PebblesTheme { Sheet(AchievementSheet.Emotions) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2600, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetEmotionsDark() {
    PebblesTheme { Sheet(AchievementSheet.Emotions) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300)
@PreviewLargeFont
@Composable
fun AchievementSheetDomainsLight() {
    PebblesTheme { Sheet(AchievementSheet.Domains) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetDomainsDark() {
    PebblesTheme { Sheet(AchievementSheet.Domains) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@PreviewFrench
@Composable
fun AchievementSheetPebblesLight() {
    PebblesTheme { Sheet(AchievementSheet.Pebbles) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1500, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetPebblesDark() {
    PebblesTheme { Sheet(AchievementSheet.Pebbles) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300)
@PreviewLargeFont
@Composable
fun AchievementSheetYearLight() {
    PebblesTheme { Sheet(AchievementSheet.Year(2026)) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 1300, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementSheetYearDark() {
    PebblesTheme { Sheet(AchievementSheet.Year(2026)) }
}
```

- [ ] **Step 6: Gate the part**

```bash
cd apps/android
./gradlew updateDebugScreenshotTest --tests "*AchievementSheet*"   # to LOOK only
open app/src/screenshotTestDebug/reference/app/pbbls/android/AchievementSheetsScreenshotsKt/
git checkout -- app/src/screenshotTestDebug/reference/ && git clean -fd app/src/screenshotTestDebug/reference/
./gradlew testDebugUnitTest lint ktlintCheck assembleDebug
```

Compare each render with `final/sheets.html` in both themes. At large font, check that every row grows rather than clipping, and that the year table's three cells keep their columns.

Expected: `BUILD SUCCESSFUL`. Lint finds no unused string: every Task 18 string has a caller in `copy/` or `sheets/`, and the card strings in `cardTitle`.

- [ ] **Step 7: Commit and open the PR**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/sheets/AchievementSheetHost.kt \
  apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementSheetsScreenshots.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/sheets/AchievementSheetContentTest.kt
git commit -m "feat(android): stack the achievements sheets, Explorer pushing Domains and Emotions"
```

Open the PR with `gh stack`, titled `feat(android): add the achievements sheets`. The body opens with `Part of #1307`. Use the same labels and milestone. Add the `rebaseline-screenshots` label and approve the held runs. No Lab Note.

---
# Part 4 — the page (branch `feat/1307-android-achievements-page`, stacked on part 3)

### Task 26: The ViewModel

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/AchievementsViewModel.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `apps/android/app/src/main/res/values-fr/strings.xml` (the ceremony's error)
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/AchievementsViewModelTest.kt`

The ViewModel owns:
- **The page state.** It is stale-while-revalidate, like the wraps: the device copy is shown at once, then `check_achievements()` (the retroactive grant, D14) and `get_achievements` replace it. A failed read keeps whatever is shown and logs it; only a cold failure with no copy is an error.
- **The resume refresh.** The entry outlives a trip to a pebble's detail and back, and a pebble deleted there must leave the page.
- **The sheet stack.** Opening a sheet marks its card seen, fire-and-forget. Opening a pebble from a sheet parks the stack, so the sheet windows never cover the detail; the return unparks it.
- **The ceremony pull.**
  - A row presents the unlock moment, with the server's karma, after closing the sheets: the overlay lives in the activity window, under any sheet's.
  - No row, or `not_ready`, refreshes and shows nothing.
  - Anything else is a snackbar.

- [ ] **Step 1: Add the error string**

In `values/strings.xml`, at the end of the achievements block:

```xml
    <string name="achievements_ceremony_error">Couldn\'t open the ceremony. Please try again.</string>
```

In `values-fr/strings.xml`, at the end of its achievements block:

```xml
    <string name="achievements_ceremony_error">Impossible d\'ouvrir la cérémonie. Réessaie.</string>
```

- [ ] **Step 2: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements

import app.pbbls.android.AchievementsFixture
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementMomentCard
import app.pbbls.android.core.data.AchievementNotificationService
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The page's load, cache, resume, sheets, seen marks and ceremony (#1307), on a virtual clock. */
class AchievementsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val page = AchievementsFixture.page
    private val stale = AchievementsFixture.page.copy(daysKept = 1)

    private fun vm(
        service: FakeAchievementsService,
        notify: AchievementNotificationService = AchievementNotificationService(),
    ) = AchievementsViewModel(service, notify)

    @Test
    fun `the retroactive grant runs before the read, and the page lands`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            assertEquals(AchievementsUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(page), viewModel.uiState.value)
            assertEquals(listOf("check", "page"), service.calls)
        }

    @Test
    fun `the device copy shows at once, then the fresh page replaces it`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val service = FakeAchievementsService(served = page, kept = stale).apply { pageGate = gate }
            val viewModel = vm(service)
            runCurrent()
            assertEquals(AchievementsUiState.Content(stale), viewModel.uiState.value)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(page), viewModel.uiState.value)
        }

    @Test
    fun `a failed read keeps the device copy`() =
        runTest {
            val service = FakeAchievementsService(served = page, kept = stale).apply { failNextPage = IOException("offline") }
            val viewModel = vm(service)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(stale), viewModel.uiState.value)
        }

    @Test
    fun `a cold failure is an error, and retry recovers`() =
        runTest {
            val service = FakeAchievementsService(served = page).apply { failNextPage = IOException("offline") }
            val viewModel = vm(service)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Error(R.string.achievements_load_error), viewModel.uiState.value)
            viewModel.retry()
            assertEquals(AchievementsUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(page), viewModel.uiState.value)
        }

    @Test
    fun `opening a sheet marks its card seen, Explorer pushes Emotions, close pops`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            advanceUntilIdle()
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.openSheet(AchievementSheet.Emotions)
            assertEquals(listOf(AchievementSheet.Explorer, AchievementSheet.Emotions), viewModel.sheets.value.visible)
            assertEquals(listOf(AchievementCard.EXPLORER, AchievementCard.EMOTIONS), service.seen)
            viewModel.closeSheet()
            assertEquals(listOf<AchievementSheet>(AchievementSheet.Explorer), viewModel.sheets.value.visible)
        }

    @Test
    fun `opening a pebble parks the sheets and asks to open it, the return unparks and refreshes`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            val effects = recordEffects(viewModel.effects)
            advanceUntilIdle()
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.pageCalls)
            viewModel.openSheet(AchievementSheet.Pebbles)
            viewModel.openMoment("moment-7")
            advanceUntilIdle()
            assertEquals(listOf<AchievementsEffect>(AchievementsEffect.OpenMoment("moment-7")), effects.values)
            assertTrue(viewModel.sheets.value.visible.isEmpty())
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(2, service.pageCalls)
            assertEquals(listOf<AchievementSheet>(AchievementSheet.Pebbles), viewModel.sheets.value.visible)
            effects.stop()
        }

    @Test
    fun `a pull closes the sheets, presents the ceremony with the server's karma, and refreshes`() =
        runTest {
            val record = AchievementRecord(id = "c6", slug = "complete-six-firsts", family = "set_complete", sortOrder = 0, karmaReward = 30, isActive = true)
            val service =
                FakeAchievementsService(served = page, catalog = listOf(record), pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 30))
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            advanceUntilIdle()
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.pullCeremony(CompletionStack.SIX_FIRSTS)
            advanceUntilIdle()
            assertEquals(listOf(AchievementMomentCard("complete-six-firsts", record, 30)), notify.cards)
            assertTrue(viewModel.sheets.value.visible.isEmpty())
            assertEquals(2, service.pageCalls)
        }

    @Test
    fun `an already-pulled ceremony and a not-ready one show nothing and refresh`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            val effects = recordEffects(viewModel.effects)
            advanceUntilIdle()
            viewModel.pullCeremony(CompletionStack.EMOTIONS)
            advanceUntilIdle()
            service.failNextPull = postgrestException("not_ready")
            viewModel.pullCeremony(CompletionStack.EMOTIONS)
            advanceUntilIdle()
            assertNull(notify.currentCard)
            assertTrue(effects.values.isEmpty())
            assertEquals(3, service.pageCalls)
            effects.stop()
        }

    @Test
    fun `any other pull failure says so and changes nothing`() =
        runTest {
            val service = FakeAchievementsService(served = page).apply { failNextPull = IOException("offline") }
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            val effects = recordEffects(viewModel.effects)
            advanceUntilIdle()
            viewModel.openSheet(AchievementSheet.Emotions)
            viewModel.pullCeremony(CompletionStack.EMOTIONS)
            advanceUntilIdle()
            assertEquals(listOf<AchievementsEffect>(AchievementsEffect.Message(R.string.achievements_ceremony_error)), effects.values)
            assertFalse(viewModel.sheets.value.visible.isEmpty())
            assertNull(notify.currentCard)
            effects.stop()
        }
}
```

- [ ] **Step 3: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.AchievementsViewModelTest"`
Expected: FAIL to compile, with `Unresolved reference: AchievementsViewModel`. Two classes of that name now exist, in `features.profile` and `features.achievements`; the test imports neither and names the new package's.

- [ ] **Step 4: Write the ViewModel**

`features/achievements/AchievementsViewModel.kt`:

```kotlin
package app.pbbls.android.features.achievements

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementMomentCard
import app.pbbls.android.core.data.AchievementNotificationService
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CompletionStack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "achievements-page"
private const val NOT_READY = "not_ready"

/** What the page shows (#1307): the house's three cases, nothing beside them. */
sealed interface AchievementsUiState {
    data object Loading : AchievementsUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : AchievementsUiState

    data class Content(
        val page: AchievementsPage,
    ) : AchievementsUiState
}

/** The open sheets, the last on top. [parked] hides them while a pebble opened from one is on screen. */
data class SheetStack(
    val sheets: List<AchievementSheet> = emptyList(),
    val parked: Boolean = false,
) {
    val visible: List<AchievementSheet> get() = if (parked) emptyList() else sheets
}

sealed interface AchievementsEffect {
    data class OpenMoment(
        val momentId: String,
    ) : AchievementsEffect

    data class Message(
        @StringRes val messageRes: Int,
    ) : AchievementsEffect
}

/**
 * State holder for the achievements page (#1307). The page renders the server's payload and
 * computes nothing; this holds what the page is showing, which sheets are open, and the two
 * writes the page makes (seen marks, the ceremony pull).
 */
@HiltViewModel
class AchievementsViewModel
    @Inject
    constructor(
        private val achievements: AchievementsServicing,
        private val notify: AchievementNotificationService,
    ) : ViewModel() {
        /** The reader's zone: the server cuts the days, the year and the 06:00 offer in it. */
        private val zone: ZoneId = ZoneId.systemDefault()

        private val _uiState = MutableStateFlow<AchievementsUiState>(AchievementsUiState.Loading)
        val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

        private val _sheets = MutableStateFlow(SheetStack())
        val sheets: StateFlow<SheetStack> = _sheets.asStateFlow()

        private val _effects = UiEffects<AchievementsEffect>(viewModelScope)
        val effects: Flow<AchievementsEffect> = _effects.flow

        private var loadJob: Job? = null
        private var pullJob: Job? = null
        private var resumeCount = 0

        init {
            load()
        }

        fun retry() = load()

        /**
         * The entry leaves RESUMED on any trip to a pushed screen (a pebble's detail) and outlives it,
         * so the return re-reads the page and brings back the sheets it parked. The first resume is
         * the screen's own start, which `init` already loaded.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) {
                _sheets.update { it.copy(parked = false) }
                refresh()
            }
        }

        /** Pushes [sheet] (a second tap on the top one is a no-op) and marks its card seen (D10). */
        fun openSheet(sheet: AchievementSheet) {
            val stack = _sheets.value
            if (stack.sheets.lastOrNull() == sheet) return
            _sheets.value = stack.copy(sheets = stack.sheets + sheet, parked = false)
            achievements.markSeen(sheet.card)
        }

        fun closeSheet() {
            _sheets.update { it.copy(sheets = it.sheets.dropLast(1)) }
        }

        /** A sheet's row opens its pebble: park the sheets, so their windows never cover the detail. */
        fun openMoment(momentId: String) {
            _sheets.update { it.copy(parked = true) }
            _effects.emit(AchievementsEffect.OpenMoment(momentId))
        }

        /** Pulls a ready set's ceremony (D9). One at a time; a second tap while it runs is ignored. */
        fun pullCeremony(stack: CompletionStack) {
            if (pullJob?.isActive == true) return
            pullJob =
                viewModelScope.launch {
                    runCatchingCancellable { achievements.pullCeremony(stack, zone) }
                        .onSuccess { pull ->
                            when (pull) {
                                is CeremonyPull.Pulled -> {
                                    val record =
                                        runCatchingCancellable { achievements.loadCatalog().firstOrNull { it.slug == stack.catalogSlug } }
                                            .onFailure { Log.w(TAG, "catalog fetch for the ceremony failed; the moment shows its slug", it) }
                                            .getOrNull()
                                    // The moment's overlay lives in the activity window, under any sheet's.
                                    _sheets.value = SheetStack()
                                    notify.present(listOf(AchievementMomentCard(stack.catalogSlug, record, pull.karmaGranted)))
                                }
                                CeremonyPull.AlreadyPulled -> Log.i(TAG, "${stack.slug} was already pulled; refreshing the offer")
                            }
                            refresh()
                        }.onFailure { e ->
                            if (e.toDataError() == DataError.Conflict(NOT_READY)) {
                                Log.w(TAG, "${stack.slug} is not ready yet; refreshing the offer", e)
                                refresh()
                            } else {
                                Log.e(TAG, "pull_ceremony(${stack.slug}) failed", e)
                                _effects.emit(AchievementsEffect.Message(R.string.achievements_ceremony_error))
                            }
                        }
                }
        }

        private fun load() {
            loadJob?.cancel()
            // Synchronously, before the launch: a retry tap must not leave the error on screen.
            if (_uiState.value !is AchievementsUiState.Content) _uiState.value = AchievementsUiState.Loading
            loadJob = viewModelScope.launch { fetch() }
        }

        private fun refresh() {
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch() }
        }

        private suspend fun fetch() {
            if (_uiState.value !is AchievementsUiState.Content) {
                runCatchingCancellable { achievements.cachedPage(zone) }
                    .onFailure { Log.w(TAG, "the device's copy could not be read", it) }
                    .getOrNull()
                    ?.let { _uiState.value = AchievementsUiState.Content(it) }
            }
            // The retroactive grant first (D14), so a veteran's history is unlocked before the read.
            achievements.checkIgnoringFailure()
            runCatchingCancellable { achievements.page(zone) }.fold(
                onSuccess = { _uiState.value = AchievementsUiState.Content(it) },
                onFailure = {
                    Log.e(TAG, "get_achievements failed; keeping what is shown", it)
                    if (_uiState.value !is AchievementsUiState.Content) {
                        _uiState.value = AchievementsUiState.Error(R.string.achievements_load_error)
                    }
                },
            )
        }
    }
```

- [ ] **Step 5: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.AchievementsViewModelTest"`
Expected: PASS, 9 tests.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/AchievementsViewModel.kt \
  apps/android/app/src/main/res/values/strings.xml apps/android/app/src/main/res/values-fr/strings.xml \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/AchievementsViewModelTest.kt
git commit -m "feat(android): hold the achievements page, its sheets, seen marks and ceremony in a ViewModel"
```

### Task 27: The page — header, card shell, five cards

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/page/AchievementsPageContent.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/page/AchievementsPageContentTest.kt`

The board's page (D1):
- the header shows the days kept since the account began;
- below it, Explorer, Emotions, Domains, Pebbles (with its glyph lines), and one card per year.

There are no legends and no "new since" lines; new items are marked only on the figures.

The card shell is pick 6:
- a 34 dp margin with the card's name set vertically over its ladder;
- the title band;
- the figure.

Each card is one button whose label is assembled from the payload (spec §5). The ready mark sits beside the band as its own 48 dp button, outside the card's merged node, so TalkBack can reach both. The shell, the header and the cards are used only by the page, so they live in its file, private.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.achievements.page

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h3200dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class AchievementsPageContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<AchievementSheet>()
    private val pulled = mutableListOf<CompletionStack>()

    private fun show(page: app.pbbls.android.core.model.achievements.AchievementsPage = AchievementsFixture.page) {
        compose.setContent {
            PebblesTheme {
                AchievementsPageContent(page, AchievementsFixture.lookups, onOpen = { opened += it }, onPull = { pulled += it })
            }
        }
    }

    @Test
    fun `each card is one labelled button that opens its sheet`() {
        show()
        compose.onNodeWithContentDescription("Explorer: first five 5 of 5", substring = true).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Emotions: 23 of 38 met.").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Domains: 14 of 18.", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pebbles: 137. Glyphs carved: 12. Glyph sales: 3.").performScrollTo().performClick()
        compose.onNodeWithContentDescription("2026. On the rim: August.", substring = true).performScrollTo().performClick()
        assertEquals(
            listOf(AchievementSheet.Explorer, AchievementSheet.Emotions, AchievementSheet.Domains, AchievementSheet.Pebbles, AchievementSheet.Year(2026)),
            opened,
        )
    }

    @Test
    fun `the header reads the days kept, the ready mark pulls on its own`() {
        show()
        compose.onNodeWithText("249").assertExists()
        compose.onNodeWithContentDescription("Explorer is complete. Open the ceremony.").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(CompletionStack.SIX_FIRSTS), pulled)
        assertEquals(emptyList<AchievementSheet>(), opened)
    }

    @Test
    fun `a missing section hides its card and nothing else`() {
        show(AchievementsFixture.page.copy(emotions = null, pebbles = null))
        compose.onNodeWithContentDescription("Emotions: 23 of 38 met.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pebbles:", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Explorer:", substring = true).assertExists()
    }
}
```

The qualifier is deliberate on both axes:
- at 360 dp wide, the figures have to fit a narrow phone without overflow;
- at 3200 dp tall, the `LazyColumn` composes every card, so `assertDoesNotExist` proves a card is hidden rather than merely off screen.

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.page.AchievementsPageContentTest"`
Expected: FAIL to compile, with `Unresolved reference: AchievementsPageContent`.

- [ ] **Step 3: Write the page**

`page/AchievementsPageContent.kt`:

```kotlin
package app.pbbls.android.features.achievements.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.Completion
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.copy.AchievementsCopy
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.CardLadder
import app.pbbls.android.features.achievements.figures.CardLadders
import app.pbbls.android.features.achievements.figures.ExplorerWheel
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.ShardBand
import app.pbbls.android.features.achievements.figures.Skyline
import app.pbbls.android.features.achievements.figures.SkylineGeometry
import app.pbbls.android.features.achievements.figures.YearDisc
import app.pbbls.android.features.achievements.figures.domainBandItems
import app.pbbls.android.features.achievements.figures.skylineFill
import app.pbbls.android.features.achievements.sheets.ReadyMark

private val CARD_GUTTER = 14.dp
private val MARGIN = 34.dp
private val BODY_PAD = 14.dp
private val DOMAINS_BAR = 120.dp
private val STACK_LABEL = 104.dp
private val STACK_COUNT = 34.dp

/** The board's margin title: set on its side, reading bottom to top. */
private fun Modifier.vertical(): Modifier =
    layout { measurable, _ ->
        val p = measurable.measure(Constraints())
        layout(p.height, p.width) { p.placeWithLayer(x = (p.height - p.width) / 2, y = (p.width - p.height) / 2) { rotationZ = -90f } }
    }

/**
 * The card shell (pick 6): the margin with the card's name and its [ladder], the title band, the
 * figure. One button saying [description]. [ready] sits over the band's end as its own button.
 */
@Composable
private fun CardShell(
    marginTitle: String,
    title: String,
    hint: String?,
    description: String,
    onClick: () -> Unit,
    ladder: @Composable () -> Unit,
    ready: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Box(Modifier.fillMaxWidth().padding(start = CARD_GUTTER, end = CARD_GUTTER, bottom = CARD_GUTTER)) {
        ElevatedCard(
            onClick = onClick,
            modifier =
                Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        onClick()
                        true
                    }
                },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(containerColor = colors.stock),
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = SpineLayoutTokens.TOUCH_MIN)) {
                Column(
                    modifier = Modifier.width(MARGIN).fillMaxHeight().padding(top = 16.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(marginTitle, style = type.cardMargin, color = colors.ink2, maxLines = 1, modifier = Modifier.vertical())
                    ladder()
                }
                VerticalDivider(color = colors.cardRule)
                Column(Modifier.weight(1f).padding(start = BODY_PAD, end = BODY_PAD, bottom = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().rule(colors.cardRule, above = false).padding(top = 14.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = type.cardBand, color = colors.ink)
                        Spacer(Modifier.weight(1f))
                        // The ready mark takes the hint's place.
                        if (hint != null && ready == null) Text(hint, style = type.cardHint, color = colors.ink2)
                    }
                    content()
                }
            }
        }
        ready?.let { Box(Modifier.align(Alignment.TopEnd).padding(end = 4.dp)) { it() } }
    }
}

@Composable
private fun readyFor(
    completion: Completion?,
    card: AchievementCard,
    stack: CompletionStack,
    copy: AchievementsCopy,
    onPull: (CompletionStack) -> Unit,
): (@Composable () -> Unit)? =
    if (completion?.ready == true) {
        { ReadyMark(copy.string(R.string.achievements_ready), copy.readyDescription(card), { onPull(stack) }) }
    } else {
        null
    }

/** The header (S1): "since Sunday 1 March 2026", then the days kept, large (D2). One node. */
@Composable
private fun Header(
    page: AchievementsPage,
    copy: AchievementsCopy,
) {
    val since = page.since ?: return
    val days = page.daysKept ?: return
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 22.dp).semantics(mergeDescendants = true) {}) {
        Text(copy.since(since), style = type.sinceLine, color = colors.ink2, modifier = Modifier.padding(bottom = 4.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(copy.number(days), style = type.daysKept, color = colors.ink)
            Text(copy.daysUnit(days), style = type.daysUnit, color = colors.ink2, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ExplorerCard(
    explorer: ExplorerSection,
    page: AchievementsPage,
    lookups: AchievementLookups,
    copy: AchievementsCopy,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val title = copy.cardTitle(AchievementCard.EXPLORER)
    CardShell(
        marginTitle = title,
        title = title,
        hint = copy.explorerHint(),
        description = copy.explorerDescription(explorer, page.emotions, page.domains),
        onClick = { onOpen(AchievementSheet.Explorer) },
        ladder = { CardLadder(CardLadders.explorer(explorer, page.emotions, page.domains)) },
        ready = readyFor(explorer.completion, AchievementCard.EXPLORER, CompletionStack.SIX_FIRSTS, copy, onPull),
    ) {
        Centred { ExplorerWheel(explorer, page.emotions, page.domains, lookups, description = "") }
    }
}

@Composable
private fun EmotionsCard(
    emotions: EmotionsSection,
    lookups: AchievementLookups,
    copy: AchievementsCopy,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val title = copy.cardTitle(AchievementCard.EMOTIONS)
    CardShell(
        marginTitle = title,
        title = title,
        hint = null,
        description = copy.emotionsDescription(emotions),
        onClick = { onOpen(AchievementSheet.Emotions) },
        ladder = { CardLadder(CardLadders.emotions(emotions)) },
        ready = readyFor(emotions.completion, AchievementCard.EMOTIONS, CompletionStack.EMOTIONS, copy, onPull),
    ) {
        Centred { Rosace(emotions, lookups, description = "", centre = copy.number(emotions.count) to copy.rosaceOf(emotions.of)) }
    }
}

@Composable
private fun DomainsCard(
    domains: DomainsSection,
    copy: AchievementsCopy,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val title = copy.cardTitle(AchievementCard.DOMAINS)
    CardShell(
        marginTitle = title,
        title = title,
        hint = copy.domainsHint(),
        description = copy.domainsDescription(domains),
        onClick = { onOpen(AchievementSheet.Domains) },
        ladder = { CardLadder(listOf(CardLadders.domains(domains)), long = DOMAINS_BAR) },
        ready = readyFor(domains.completion, AchievementCard.DOMAINS, CompletionStack.DOMAINS, copy, onPull),
    ) {
        ShardBand(domainBandItems(domains), description = "", modifier = Modifier.padding(top = 16.dp))
        domains.coverage?.let {
            Text(
                copy.coverage(it),
                style = PebblesTheme.spine.type.cardLine,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun StackLine(
    label: String,
    stack: CountingSection,
    copy: AchievementsCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = SpineLayoutTokens.ROW_MIN).rule(colors.rule, above = false),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.stackLabel, color = colors.ink2, maxLines = 1, modifier = Modifier.width(STACK_LABEL))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Skyline(stack.skylineFill(), description = "", viewport = SkylineGeometry.MINI, width = 103.dp)
        }
        Text(copy.number(stack.count), style = type.stackCount, color = colors.ink, textAlign = TextAlign.End, modifier = Modifier.width(STACK_COUNT))
    }
}

@Composable
private fun PebblesCard(
    page: AchievementsPage,
    pebbles: CountingSection,
    copy: AchievementsCopy,
    onOpen: (AchievementSheet) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val title = copy.cardTitle(AchievementCard.PEBBLES)
    val fill = pebbles.skylineFill()
    CardShell(
        marginTitle = title,
        title = title,
        hint = null,
        description = copy.pebblesDescription(pebbles, page.glyphsCarved, page.glyphSales),
        onClick = { onOpen(AchievementSheet.Pebbles) },
        ladder = { CardLadder(CardLadders.counting(pebbles)) },
        ready = null,
    ) {
        Skyline(fill, description = "", modifier = Modifier.padding(top = 22.dp))
        Text(
            copy.pebblesLine(pebbles, fill),
            style = PebblesTheme.spine.type.cardLine,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
        )
        page.glyphsCarved?.let { StackLine(copy.string(R.string.achievements_section_glyphs_carved), it, copy) }
        page.glyphSales?.let { StackLine(copy.string(R.string.achievements_section_glyph_sales), it, copy) }
    }
}

@Composable
private fun YearCard(
    year: AchievementYear,
    lookups: AchievementLookups,
    copy: AchievementsCopy,
    onOpen: (AchievementSheet) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val next = year.nextStep?.let(copy::nextStep)
    CardShell(
        marginTitle = copy.cardTitle(AchievementCard.YEAR),
        title = year.year.toString(),
        hint = copy.yearHint(year.current),
        description = listOfNotNull(copy.discDescription(year), next).joinToString(" "),
        onClick = { onOpen(AchievementSheet.Year(year.year)) },
        ladder = { CardLadder(CardLadders.year(year)) },
        ready = null,
    ) {
        Centred { YearDisc(year, lookups, description = "") }
        next?.let {
            Text(
                it,
                style = PebblesTheme.spine.type.cardLine,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/**
 * The achievements page (#1307, D1): the header, then one card per section the payload carries,
 * then one card per year. A missing section hides its card (spec §4). [onOpen] opens a card's
 * sheet; [onPull] starts a ready set's ceremony.
 */
@Composable
fun AchievementsPageContent(
    page: AchievementsPage,
    lookups: AchievementLookups,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") { Header(page, copy) }
        page.explorer?.let { e -> item(key = "explorer") { ExplorerCard(e, page, lookups, copy, onOpen, onPull) } }
        page.emotions?.let { e -> item(key = "emotions") { EmotionsCard(e, lookups, copy, onOpen, onPull) } }
        page.domains?.let { d -> item(key = "domains") { DomainsCard(d, copy, onOpen, onPull) } }
        page.pebbles?.let { p -> item(key = "pebbles") { PebblesCard(page, p, copy, onOpen) } }
        items(page.years, key = { "year-${it.year}" }) { year -> YearCard(year, lookups, copy, onOpen) }
    }
}
```

Four details:
- `ElevatedCard(onClick)` gives the ripple and the 48 dp floor. The `clearAndSetSemantics` block replaces the card's merged children with the one label (spec §5) and restores the click action by hand, as `MarkRow` does.
- The figures' own descriptions are `""` inside a card: the card speaks for them.
- The figures fill the width they get, up to their board size: `fillMaxWidth().widthIn(max).aspectRatio(…)` from part 2. A 360 dp phone draws them smaller rather than clipping, and `IntrinsicSize.Min` measures them through `aspectRatio`.
- No `BoxWithConstraints` may sit inside a card: the margin's `IntrinsicSize.Min` cannot ask a `SubcomposeLayout` for its height.

- [ ] **Step 4: Run it to make sure it passes**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.features.achievements.page.AchievementsPageContentTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/page/ \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/achievements/page/
git commit -m "feat(android): lay out the achievements page, a header and one card per section"
```

### Task 28: The screen, the entry, and the grid's removal

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/AchievementsScreen.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt:30,177-179`
- Delete: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/AchievementsScreen.kt`
- Delete: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/AchievementsViewModel.kt`
- Delete: `apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/AchievementsViewModelTest.kt`
- Delete: `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementsScreenshots.kt` and `apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/AchievementsScreenshotsKt/`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/core/data/AchievementsDecodingTest.kt` (drop the grid's filter test)
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `apps/android/app/src/main/res/values-fr/strings.xml` (drop `achievement_locked`, `achievement_unlocked_on`)
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/ui/AchievementsPageTest.kt`

- [ ] **Step 1: Write the failing whole-app test**

```kotlin
package app.pbbls.android.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.R
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeAchievementsService
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/**
 * The page through the real app (#1307): You → Achievements, a card opens its sheet and marks it
 * seen, Explorer's Emotions row pushes a second sheet, and back pops one sheet at a time.
 */
@HiltAndroidTest
class AchievementsPageTest : AppUiTest() {
    @Inject lateinit var achievements: FakeAchievementsService

    private fun openAchievements() {
        achievements.served = AchievementsFixture.page
        skipOnboarding()
        launch()
        signIn()
        compose.onNode(hasText(string(R.string.tab_you)) and isSelectable()).performClick()
        onText(R.string.achievements_title).performScrollTo().performClick()
    }

    @Test
    fun `a card opens its sheet, Explorer pushes Emotions, back pops one at a time`() {
        openAchievements()
        compose.onNodeWithContentDescription("Explorer: first five", substring = true).performClick()
        assertEquals(listOf(AchievementCard.EXPLORER), achievements.seen)

        compose.onNodeWithContentDescription("Emotions, 23 of 38", substring = true).performScrollTo().performClick()
        assertEquals(listOf(AchievementCard.EXPLORER, AchievementCard.EMOTIONS), achievements.seen)

        pressBack()
        compose.onNodeWithContentDescription("Emotions, 23 of 38", substring = true).assertExists()
        pressBack()
        compose.onNodeWithContentDescription("Emotions, 23 of 38", substring = true).assertDoesNotExist()
        onText(R.string.achievements_title).assertExists()
    }
}
```

`AppUiTest.pressBack()` dispatches to the focused window. A `ModalBottomSheet` is a dialog window holding focus, so back reaches the top sheet first. If a Robolectric upgrade ever sends it to the activity instead, the test fails on the second assertion; that is the signal, so do not soften it.

- [ ] **Step 2: Run it to make sure it fails**

Run: `cd apps/android && ./gradlew testDebugUnitTest --tests "app.pbbls.android.ui.AchievementsPageTest"`
Expected: FAIL: the old grid is on screen, so no node carries "Explorer: first five".

- [ ] **Step 3: Write the screen**

`features/achievements/AchievementsScreen.kt`:

```kotlin
package app.pbbls.android.features.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.model.EmotionFamilies
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.page.AchievementsPageContent
import app.pbbls.android.features.achievements.sheets.AchievementSheetHost
import kotlinx.coroutines.launch
import java.time.ZoneId

/** The page's lookups from the app's reference data: the three permanent locals, read where they render. */
@Composable
internal fun rememberAchievementLookups(): AchievementLookups {
    val palettes = LocalEmotionPaletteService.current
    val references = LocalReferenceDataService.current
    val emotions = palettes.byEmotionId
    val families = remember(emotions) { EmotionFamilies.of(emotions.values).associateBy { it.slug } }
    val domains = remember(references.domains) { references.domains.associateBy { it.id } }
    return remember(families, emotions, domains) { AchievementLookups(families::get, emotions::get, domains::get) }
}

/**
 * You → Achievements (#1307): the page and its sheets, served whole by `get_achievements`. It
 * reads one [AchievementsUiState] and one sheet stack, and takes navigation as lambdas.
 */
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    onOpenMoment: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheets by viewModel.sheets.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // The entry outlives a trip to a pebble's detail: the return re-reads the page and unparks the sheets.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is AchievementsEffect.OpenMoment -> onOpenMoment(effect.momentId)
            // A snackbar is UI, not a write: the composition's scope is the right one for it.
            is AchievementsEffect.Message -> scope.launch { snackbar.showSnackbar(resources.getString(effect.messageRes)) }
        }
    }

    AchievementsScreen(
        uiState = uiState,
        sheets = sheets.visible,
        lookups = rememberAchievementLookups(),
        zone = ZoneId.systemDefault(),
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenSheet = viewModel::openSheet,
        onCloseSheet = viewModel::closeSheet,
        onOpenMoment = viewModel::openMoment,
        onPull = viewModel::pullCeremony,
        snackbar = snackbar,
        modifier = modifier,
    )
}

/** Stateless host: the chrome, the state switch, and the sheets over the content. Previewable. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AchievementsScreen(
    uiState: AchievementsUiState,
    sheets: List<AchievementSheet>,
    lookups: AchievementLookups,
    zone: ZoneId,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSheet: (AchievementSheet) -> Unit,
    onCloseSheet: () -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MaterialTheme.colorScheme
    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.achievements_title),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
        overlay = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) },
    ) {
        when (uiState) {
            AchievementsUiState.Loading ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                }

            is AchievementsUiState.Error ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(uiState.messageRes), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.profile_retry), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }

            is AchievementsUiState.Content -> {
                AchievementsPageContent(uiState.page, lookups, onOpen = onOpenSheet, onPull = onPull)
                AchievementSheetHost(
                    sheets = sheets,
                    page = uiState.page,
                    lookups = lookups,
                    zone = zone,
                    onPush = onOpenSheet,
                    onDismiss = onCloseSheet,
                    onOpenMoment = onOpenMoment,
                    onPull = onPull,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Point the entry at it**

In `PebblesEntryProvider.kt`, replace the import `app.pbbls.android.features.profile.AchievementsScreen` with `app.pbbls.android.features.achievements.AchievementsScreen`. Then replace the entry with:

```kotlin
    entry<PebblesKey.Achievements>(metadata = NavTransitions.forKey(PebblesKey.Achievements)) {
        AchievementsScreen(
            onBack = navigator::goBack,
            // A sheet's row opens its pebble: the held card, as the soul and collection details do.
            onOpenMoment = { navigator.navigate(PebblesKey.PebbleDetail(it)) },
        )
    }
```

- [ ] **Step 5: Remove the grid**

```bash
git rm apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/AchievementsScreen.kt \
  apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/AchievementsViewModel.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/AchievementsViewModelTest.kt \
  apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementsScreenshots.kt
git rm -r apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android/AchievementsScreenshotsKt/
```

Deleting the grid's references is not a re-baseline: they belong to previews that no longer exist.

In `AchievementsDecodingTest.kt`, delete the test `inactive badges only show once unlocked, families keep catalog order` and the import `app.pbbls.android.features.profile.visibleFamilyGroups`. It tested the grid's filter, which leaves with the grid. The catalog and unlock decoding tests stay: `ProfileViewModel` still reads both.

The grid was the only caller of two strings, and lint's `UnusedResources` would fail on them. Delete these lines from `values/strings.xml`:

```xml
    <string name="achievement_locked">Locked</string>
    <string name="achievement_unlocked_on">Unlocked %1$s</string>
```

And these from `values-fr/strings.xml`:

```xml
    <string name="achievement_locked">À débloquer</string>
    <string name="achievement_unlocked_on">Débloqué le %1$s</string>
```

`achievementGroupName`, `achievementFamilyIcon` and `achievementDescription` keep their callers: the profile shelf (`ProfileAchievementsCard`) and the unlock moment. Leave `AchievementCopy.kt` alone.

- [ ] **Step 6: Run the whole suite**

Run: `cd apps/android && ./gradlew testDebugUnitTest`
Expected: PASS, including:
- `AchievementsPageTest`;
- `NavigationTest`'s "Profile opens … Achievements …", because the title is still `achievements_title`;
- `ArchitectureBoundaryTest`: `features/achievements` imports only `core/`;
- `ThemeLiteralsTest` and `LocalizationParityTest`.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/achievements/AchievementsScreen.kt \
  apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/core/data/AchievementsDecodingTest.kt \
  apps/android/app/src/test/kotlin/app/pbbls/android/ui/AchievementsPageTest.kt \
  apps/android/app/src/main/res/values/strings.xml apps/android/app/src/main/res/values-fr/strings.xml
git commit -m "feat(android): open the achievements page from You, in place of the badge grid"
```

### Task 29: The page previews, the gate, the PR

**Files:**
- Create: `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementsPageScreenshots.kt`

- [ ] **Step 1: Write the page previews**

```kotlin
package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.AchievementsScreen
import app.pbbls.android.features.achievements.AchievementsUiState
import com.android.tools.screenshot.PreviewTest
import java.time.ZoneId

/**
 * The achievements page (#1307) in its three states. The screen itself is the subject, chrome
 * included, as `AchievementsScreenshots` was for the grid. Tall, so the whole scroll renders; the
 * sheets have their own set (`AchievementSheetsScreenshots`).
 */
@Composable
private fun PageAt(state: AchievementsUiState) {
    PebblesTheme {
        AchievementsScreen(
            uiState = state,
            sheets = emptyList(),
            lookups = AchievementsFixture.lookups,
            zone = ZoneId.of("Europe/Paris"),
            onBack = {},
            onRetry = {},
            onOpenSheet = {},
            onCloseSheet = {},
            onOpenMoment = {},
            onPull = {},
        )
    }
}

private val content = AchievementsUiState.Content(AchievementsFixture.page)

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2900)
@PreviewFrench
@Composable
fun AchievementsPageLight() {
    PageAt(content)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 2900, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementsPageDark() {
    PageAt(content)
}

/**
 * The accessibility ceiling: every card's text grows, the figures keep their place. A tall viewport
 * of its own, not `@PreviewLargeFontTall` (720 dp), which would show only the header and Explorer.
 */
@PreviewTest
@Preview(name = "fs2", showBackground = true, fontScale = 2f, widthDp = 390, heightDp = 4200)
@Composable
fun AchievementsPageLargeFont() {
    PageAt(content)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360, heightDp = 2900)
@Composable
fun AchievementsPageNarrow() {
    PageAt(content)
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AchievementsPageLoading() {
    PageAt(AchievementsUiState.Loading)
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun AchievementsPageErrorLight() {
    PageAt(AchievementsUiState.Error(R.string.achievements_load_error))
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AchievementsPageErrorDark() {
    PageAt(AchievementsUiState.Error(R.string.achievements_load_error))
}
```

- [ ] **Step 2: Look at the renders, then run the full gate**

```bash
cd apps/android
./gradlew updateDebugScreenshotTest --tests "*AchievementsPage*"   # to LOOK only
open app/src/screenshotTestDebug/reference/app/pbbls/android/AchievementsPageScreenshotsKt/
git checkout -- app/src/screenshotTestDebug/reference/ && git clean -fd app/src/screenshotTestDebug/reference/
./gradlew testDebugUnitTest lint ktlintCheck assembleDebug bundleRelease
./gradlew validateDebugScreenshotTest   # judge only against main's local run: compare the failure list, not the count
```

Compare the page with `final/index.html`, side by side in paper and night. Check:
- the header;
- the Explorer wheel with its ladder of five;
- the rosace with its family names;
- the band with its sentence;
- the skyline and the two glyph lines;
- the 2026 disc with November's line;
- at 360 dp, nothing clips;
- at large font, the cards grow and the margin titles stay on their side.

Expected: `BUILD SUCCESSFUL`. `bundleRelease` runs R8 and `lintVitalRelease`, and no new library reflects.

- [ ] **Step 3: Check it on a device**

Install the debug build on the Pixel 7 AVD (`oxymore-eclipse`); the maintainer's Pixel 8 holds a real account, so ask before any write there.

Open You → Achievements, then:
1. Open each card's sheet.
2. Push Emotions from Explorer, then go back twice.
3. Open a pebble from a sheet and come back: the sheet returns.
4. Switch the system to dark mode with the page open.
5. Turn TalkBack on and swipe through: each card reads as one button with its sentence, and the ready mark reads apart from it.

- [ ] **Step 4: Commit the previews and open the PR**

```bash
git add apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/AchievementsPageScreenshots.kt
git commit -m "test(android): preview the achievements page in paper, night, French, large font and narrow"
```

Open the PR with `gh stack`, titled `feat(android): the achievements page and its five sheets`.

The body:
- opens with `Resolves #1307`;
- lists the key files of the whole stack;
- states the three contract gaps from the plan's end, each as a follow-up issue number, never a finding id;
- ends with the attribution line.

Propose #1307's labels and milestone and confirm them with the maintainer. Add `rebaseline-screenshots`, then approve the held runs.

**Lab Note.** The PR carries `feat` and touches the user-visible `V-achievements` view, so the gate in `CLAUDE.md` asks for a note. The maintainer's standing practice is that Arkaik coverage replaces it for views already on the map: confirm with them, then either add the `no-lab-note` label or author the note with the repo-local `lab-note` skill.

**Arkaik.** `V-achievements` was moved to `development` before Task 1. The Arkaik App marks it `releasing` at merge. Never set `live` by hand: nothing marks Android `live` yet.

**Stack merge.** Merge the stack whole and in order, after back-end part 3 is live on the linked project: the page calls `get_achievements`, `mark_achievements_seen` and `pull_ceremony`. Fetch and fast-forward before each stack push, as the `gh stack` and re-baseline-bot notes require.

---

## Self-review notes

- **Spec coverage:**
  - D1, the page: Task 27.
  - D2, days kept: the header in Task 27, from `since` and `days_kept`.
  - D3, counting: Tasks 15, 23 and 27 (skyline, milestones, "then every").
  - D4, the crossing pebble: snapshots in Task 2, opened from every sheet row.
  - D5, the six firsts: Tasks 11 and 20.
  - D6, the wheel: Task 12.
  - D7 and D8, silence: Task 3's `celebrated()`.
  - D9, completions: the ready mark (Task 19) and the pull (Task 26).
  - D10, "new": every figure's new mark and every row's dot. The seen marks are in Task 26.
  - D11, coverage: Task 18's sentences and the band.
  - D12, the disc: Task 16, with the year sheet in Task 24.
  - D14, one read after the grant: Task 26.
  - D15, snapshots: Task 2.
  - D17, tolerance: Task 2.
  - §4 resilience: the cache (Tasks 4 and 26), a missing section hides (Tasks 25 and 27), an unknown enum falls back (Task 2), an unknown family draws in ink (Tasks 12, 13 and 16).
  - §4 interaction: Tasks 25, 26 and 28.
  - §5, accessibility: card labels and spoken lists in Tasks 18 and 27, 48 dp targets in Tasks 19 and 27, both themes, French and large font in the previews, colour never the only signal (position and rings).
- **Mirror of iOS (#1306):** behaviour, copy and calls match the spec and the board. The Android-only shapes are those `apps/android/CLAUDE.md` already records: a ViewModel, stacked M3 sheets, `MarkRow` rows and the wraps' disk cache. Align the copy with #1306's catalog when it lands.
- **Not in this plan:**
  - the public profile's private families: back-end Task 13 removes them server-side, and Android's public shelf renders whatever the projection sends;
  - a first-moment Home checklist: Android has no Home tab yet.
## Contract gaps found in the back-end plan

The client copes with each of these today, as described in the tasks above. Each should be settled on the server, or written into the spec, before #1306 and #1308 copy the same workarounds. File them as issues; reference them by number in PR bodies.

1. **Explicit nulls where D17 promises omitted keys** (Task 9):
   - `explorer.six_firsts` is a bare `jsonb_agg`, which gives `null` when no `first_stone` row is active;
   - `explorer.twenty_five` is a scalar subquery, which gives `null` when `pebble-count-25` is missing;
   - suggested fix: `coalesce(…, '[]')`, and `achievement_opt` for the second.
2. **No order for the met emotions inside a family.** The approved sheet lists them by the date they were met. The payload sends seats in seat order only, so a client would have to sort by `pebble.happened_at` to match the board. Android uses seat order. Either send the order (a `met_rank`, or a per-family list), or amend the board and spec to seat order.
3. **The day target is not in the payload.** The year table reads `n/14`, the next step and the levels hang on D12's 14, and `progress` is sent but the target is not. Android holds `DAYS_TARGET = 14`. Send `days_target` on each year, or on the payload, so a rule change cannot strand a client.
4. **The year has no "new" flag.**
   - `mark_achievements_seen('year')` records a `seen_at` that no field reads, while the board marks "October reaching the rim" as new.
   - Adding `months[].new` needs a stored time at which the month reached its level. That ties into the spec's open question 3 (a month that can lose its level).
   - Until then, the client sends the mark and draws nothing.
5. **No per-card "since" date.** The board's Emotions and Pebbles bands read "since 2 March". Nothing in the payload carries it, and a client should not take a minimum over the snapshots, so Android leaves those hints out.
6. **A completion has no pebble.**
   - `achievement_completions.pebble_id` is stored, but `achievement_completion()` does not serve it.
   - So the ceremony cannot draw the stone that made the set whole.
   - Add `completion.pebble`, a `{pebble}` snapshot.
7. **The dumps miss the branches clients must draw.** No dumped payload has `completion.ready = true`, a `pulled_at`, a stack at the top of its ladder (no `next`), a glyph level, or an unknown family or state. The ready path runs only with the service key and dumps nothing. Suggestion: dump `get-achievements-pulled` in that branch, commit it from a manual run, and have the clients replay it. Android's tolerance tests hand-build these meanwhile.
8. **Spec typo:** D12's tie-break order says `peaceful`; the SQL, the wheel and Android use the category slug `peace`.
9. **Mixed date semantics in `on`:**
   - `first_five[].on` is the subject's `created_at`;
   - `levels[].on` is the crossing pebble's `created_at`;
   - the six firsts carry only the snapshot's `happened_at`;
   - one sheet therefore mixes "recorded" and "happened" dates. Pick one meaning for the register's date column.
