# Android Home and You — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Before any Android task, read `apps/android/CLAUDE.md`; route Compose questions to `android-skills:compose`, tests to `android-skills:android-testing`, navigation to `navigation-3`, the screenshot gate to `android-screenshot-gate`.

**Goal:** Give Android the Home tab (first of five) and rebuild You in the approved c3 "Cut Stones, carded" design, mirroring iOS's behaviour, copy and server calls. Spec: `docs/superpowers/specs/2026-10-31-android-home-and-you-design.md` (D1–D23). Issue #1383 (absorbs #1376), milestone M67 · Home & You.

**Architecture:** A new `features/home/` (screen, ViewModel, greeting, month grid host, shortcuts and their three flows, first five, wraps, guides) over `core/*` only. Shared drawing goes to `core/designsystem` (stone geometry and shapes, `PageTypography`, `PageShapes`, `PebblesCard`); shared data to `core/data` (`HomeService`, `TabIntents`, `HomeShortcutsPreference`, two `AchievementsService` methods). The month grid and the month page stay in `features/path` and reach Home as slots typed in `core/ui/HomeSlots.kt`, filled by `PebblesEntryProvider`. Cross-tab actions (focus a week, create a soul, a collection, carve a glyph) go through `TabIntents`, consumed once by the target ViewModel on resume. You (`features/profile`) is rebuilt on the same kit after its outgoing links (Connections, Log out, the glyph tabs) have new homes.

**Tech stack:** Kotlin + Jetpack Compose (Material 3 Expressive, material3 `1.5.0-beta02`, Compose BOM `2026.09.00`), Hilt, Navigation 3, supabase-kt, kotlinx-serialization, JUnit 4 + Robolectric (`testDebugUnitTest`), Compose Preview Screenshot Testing (references recorded on CI only), ktlint, Android Lint (`warningsAsErrors`, committed baseline), Konsist. Part B: Postgres migration + Deno harness.

**Stack (gh stack, fifteen parts = fifteen PRs, bottom first), plus Part B outside it:**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| B | `feat/<issue>-glyph-counts` | `feat(db): count carved and owned glyphs in get_profile_engagement` | B1–B3 |
| 1 | `feat/1383-inclusive-sans-android` | `chore(android): bundle the same inclusive sans files as ios and web` | 1–2 |
| 2 | `feat/1383-stones-android` | `feat(android): port the stone geometry and the squircle pentagon` | 3–7 |
| 3 | `feat/1383-page-kit-android` | `feat(android): add the page type set, the card and the section parts` | 8–12 |
| 4 | `feat/1383-home-data-android` | `feat(android): read home wraps, guides and the blue pebble claim` | 13–17 |
| 5 | `feat/1383-home-tab-android` | `feat(android): open the app on a home tab with the greeting` | 18–22 |
| 6 | `feat/1383-home-month-android` | `feat(android): show the current month's weeks on home` | 23–26 |
| 7 | `feat/1383-quick-actions-android` | `feat(android): draw the home quick actions as one split stone, row or bento` (`Resolves #1376`) | 27–30 |
| 8 | `feat/1383-shortcut-flows-android` | `feat(android): camera moment, quick moment and new pebble from home` | 31–35 |
| 9 | `feat/1383-first-five-android` | `feat(android): your first five on home, with the blue pebble claim` | 36–40 |
| 10 | `feat/1383-home-wraps-android` | `feat(android): monthly wraps on home, with the unfold` | 41–44 |
| 11 | `feat/1383-markdown-move-android` | `chore(android): move the markdown body to core ui` | 45 |
| 12 | `feat/1383-guides-android` | `feat(android): about pebbles and its guide pages on home` | 46–49 |
| 13 | `feat/1383-you-prep-android` | `feat(android): circle on people, log out in settings, glyphs open on a tab` | 50–53 |
| 14 | `feat/1383-you-header-android` | `feat(android): rebuild the you header, counters and glyphs` | 54–58 |
| 15 | `feat/1383-you-sections-android` | `feat(android): achievements cards and the lab on you` (`Resolves #1383`) | 59–64 |

Labels: Parts 2–15 `feat`, `ui`, `android` (Part 4 adds `api`); Part 1 and Part 11 `chore`, `android`, `no-lab-note`; Part B `feat`, `db`, `supabase`. Milestone `M67 · Home & You`. Bodies for Parts 1–14 start `Part N of #1383`, never `Resolves` (Part 7 also says `Resolves #1376`); Part 15 starts `Resolves #1383`. Lab Note on Parts 5–10, 12, 14, 15 (author with the `lab-note` skill) unless the maintainer covers the change in Arkaik (`no-lab-note`). Never write a Kritik finding id in a body. Arkaik: Part 5 adds the Home view, Part 10 the wrap sheet, Part 12 the guide page, Part 14 updates You; move each to `development` when the part starts, through `arkaik-mcp` only (stop and say so if the tools are absent).

**Before Part 1:**

- Work in a worktree off an updated `main` (`git fetch origin && git switch -c feat/1383-inclusive-sans-android origin/main`); never `cd` to the main checkout. Copy `apps/android/secrets.properties` from the main checkout only if you install on a device.
- Once per worktree: `git config rerere.enabled true && git config remote.pushDefault origin`.
- Before any Gradle call: `export ANDROID_HOME=$HOME/Library/Android/sdk` (without it `scripts/gradle-if-sdk.sh` exits 0 having done nothing). Screenshot renders need the 2 GB heap the month-intro stack set.
- Each part's branch name ends in `-android` (Vercel skips it).

**The Android gate, at the end of every part** (from the worktree root):

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation, `BUILD SUCCESSFUL`. Do not run `updateDebugScreenshotTest` or commit a PNG: references are recorded on CI only.

**Re-baselining a part (spec D21):** after the part's last code commit is pushed and the `Android` check is red only on `Validate UI screenshots`: label a part only once the part below has its bot commit in; add the label alone (`gh pr edit <branch> --add-label rebaseline-screenshots`); `git fetch origin`, fast-forward the local branch to its bot commit, `gh stack rebase --no-trunk` from the part above (the upper branch wins a PNG conflict), `gh stack push`; approve the held `action_required` runs through the API; never re-run the failed run; open every new PNG in the PR's Files tab before moving on.

---

## Part B — glyph counts on `get_profile_engagement` (back-end, own issue)

File the issue first: `[Feat] Count carved and owned glyphs in get_profile_engagement`, labels `feat`, `db`, `supabase`, milestone M67, body naming #1382, #1383, #1384 as consumers. Whichever surface reaches its You header first builds it; skip this part if it has merged.

### Task B1: The migration

**Files:** create `packages/supabase/supabase/migrations/<timestamp>_profile_engagement_glyph_counts.sql`.

- [ ] **Step 1:** Read `20260516104231_profile_glyph_and_engagement.sql` for the current body, grants and `p_tz` handling. Grep the migrations for later re-emissions of `get_profile_engagement` (none on `05cf4926`); if one exists, start from the last.
- [ ] **Step 2:** `drop function public.get_profile_engagement(text);` then `create function` with the same body plus `glyphs_carved int` (`count(*)` of `public.glyphs` where `user_id = auth.uid()`) and `glyphs_owned int` (`count(*)` of `public.glyph_entitlements` where `user_id = auth.uid()`) appended to the `returns table`. Keep `security` mode, `search_path`, `stable` and the grants (`revoke … from public, anon; grant execute … to authenticated`) exactly as the original.
- [ ] **Step 3:** Validate on the native local Postgres (Homebrew `postgresql@16`, never Docker) by replaying the chain, then hand the maintainer the `db:push` command (the classifier blocks it).
- [ ] **Step 4:** `npm run db:types:remote --workspace=packages/supabase` (the `:remote` variant; `db:types` targets Docker and truncates on failure) and commit `packages/supabase/types/database.ts`.

### Task B2: The harness

**Files:** create `packages/supabase/scripts/verify-profile-engagement.ts`; modify `packages/supabase/package.json` (`db:verify:engagement`, appended to `db:verify`).

- [ ] **Step 1:** Model on `verify-home.ts`: sign up a throwaway user, assert `glyphs_carved = 0, glyphs_owned = 0`; insert one glyph through the existing client path (`glyphs` insert, as `verify-glyph-usability.ts` does) and assert `glyphs_carved = 1`; assert `days_practiced` and a 28-length `assiduity` are unchanged in shape; assert anon is refused; delete the user through `delete-account` even on failure. Entitlements need a second user's listed glyph and `buy_glyph`: if karma at seed cannot pay, assert `glyphs_owned = 0` and say in the header why the positive case is not provable anon-only.
- [ ] **Step 2:** Run it against the linked project. Expected: `Summary: passed=N failed=0`.

### Task B3: PR

- [ ] Commit (`feat(db): count carved and owned glyphs in get_profile_engagement`), push, PR with labels and milestone; `supabase.yml` replays the chain and runs the harness.

---

## Part 1 — Inclusive Sans files (`feat/1383-inclusive-sans-android`)

### Task 1: Replace the two files (spec D4)

**Files:** replace `apps/android/app/src/main/res/font/inclusive_sans.ttf`, `inclusive_sans_italic.ttf`; create `apps/android/app/src/test/kotlin/app/pbbls/android/core/designsystem/FontFilesTest.kt`.

- [ ] **Step 1: Write the failing test.** `FontFilesTest` reads both files from `src/main/res/font` (path relative to the module dir, as `LocalizationParityTest` reads `strings.xml`) and asserts their SHA-1: `inclusive_sans.ttf` = `7c3e44eb5d0cb356fc8b5389578564cc345a1cfa`, `inclusive_sans_italic.ttf` = `c4fdd08a8ffb41239938920bb19b522cc7cb280e`. KDoc: the files are the maintainer's Inclusive Sans 2.004 release build, shared byte-for-byte with iOS and web.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*FontFilesTest'`. Expected: FAIL (the Google Fonts build hashes `3058acb0…`, `a25a2b50…`).
- [ ] **Step 3:** Copy `InclusiveSans-VariableFont_wght.ttf` → `inclusive_sans.ttf` and `InclusiveSans-Italic-VariableFont_wght.ttf` → `inclusive_sans_italic.ttf` from the maintainer's folder (`~/Downloads/Inclusive Sans (1)/`; if gone, from the iOS bundle once #1382 lands it). Check `ls -l`: 112 064 and 114 308 bytes.
- [ ] **Step 4:** Run the test again. Expected: PASS. Check the open-source notice the app shows (grep `OFL\|Inclusive` in `res/` and `features/profile/Settings*`) lists Inclusive Sans under the OFL; if it does not, add the line in both string files.
- [ ] **Step 5:** Commit `chore(android): bundle the same inclusive sans files as ios and web`.

### Task 2: Part 1 gate, PR, references

- [ ] Gate; push; PR; re-baseline. Review the bot's PNGs for kerning shifts only (identical outlines); any other change is a stop.

---

## Part 2 — stones (`feat/1383-stones-android`)

All files under `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/stone/`; tests under `src/test/.../core/designsystem/stone/`. Read `apps/ios/Pebbles/Features/Home/Shortcuts/ShortcutStones.swift` (393 lines) whole before Task 3; port its arithmetic in order, never re-derive it.

### Task 3: `StoneRandom`

- [ ] **Step 1: Failing test** `StoneRandomTest`: `StoneRandom("camera|fixed|rock|q11")` draws 0.9302541588, 0.5075708053, 0.6537410403; `StoneRandom("quick|fixed|q12split")` draws 0.0461277429, 0.2574864214, 0.5876142546 (tolerance 1e-9; values from `PebblesTests/HomeShortcutsTests.swift:21–28`); two instances of one seed agree for 100 draws.
- [ ] **Step 2:** Implement over `UInt` arithmetic: seed `h = 1779033703u xor length`, per UTF-16 unit `h = (h xor u) * 3432918353u`, `h = h rotl 13`; `next()`: `h = (h xor (h shr 16)) * 2246822507u`, `h = (h xor (h shr 13)) * 3266489909u`, `h = h xor (h shr 16)`, return `h.toDouble() / 4294967296.0`.
- [ ] **Step 3:** Test passes. Commit `feat(android): port the stone random generator`.

### Task 4: `StoneGeometry`

- [ ] **Step 1: Failing tests** `StoneGeometryTest`, the iOS assertions copied: row pieces stay inside their cells (± gutter) and gutters stay open at 359×84, 359×98, 300×120; bento pieces stay inside their cells (± 0.5) with more than 24 points at 359×162 for each lead; shapes are deterministic; a single `rockOutline` fills its w×h (bounds within 0.01). For the options iOS lacks (`n = 5`, `corner`, `gutter`, `fillet`, `lean`), pin the first and middle point of each piece for `splitRow(324×6, n = 5, seed = "first-five", corner 3, gutter 5, fillet 2, lean 0.9)` and `splitRow(358×88, n = 3, seed = "days|pebbles|karma")` from the oracle: the iOS generalisation if #1382 has merged it (copy its test values), else the mock kit's `stones.js` run under node with the same arguments; name the source in a comment.
- [ ] **Step 2:** Implement `rockOutline`, `splitRow(size, n, seed, options)`, `splitBento(size, leadWidth, order)`, `rowCells`, `bentoCells`, `resample` (≥ 24 points), `path` (closed Catmull-Rom, control points at ±1/6), and the memo (key = size × 4 rounded, seed, options; cleared after 24). `StoneOptions(corner = 34f, bow = 1f, gutter = 8f, fillet = 20f, lean: Float? = null)`; `lean = null` keeps iOS's drawn lean (`0.11 ± 0.03`), so the default call reproduces the shipping quick actions exactly.
- [ ] **Step 3:** Tests pass. Commit `feat(android): port the split-stone geometry`.

### Task 5: `StoneShape` and `SplitStone`

- [ ] **Step 1:** `StoneShape(seed: String, corner: Dp = 34.dp, bow: Float = 1f) : Shape` — `createOutline` converts dp to px with the given `Density`, asks `rockOutline` at the size, returns `Outline.Generic`. `equals`/`hashCode` on the three fields so Compose does not re-create it.
- [ ] **Step 2:** `SplitStone(n, seed, fills: List<Color>, modifier, options, minHeight: Dp, content: @Composable (index: Int) -> Unit)`: a `Layout` that gives each cell its `rowCells` slot, draws piece *k* (`drawBehind`, translated) in `fills[k]`, and exposes `Modifier.pieceClip(k)` (a `GenericShape` of the piece in cell coordinates) for clickable cells' ripples. `vertical: Boolean` stacks the pieces as three full-width stones (seeds `<seed>#k`) for the large-font case (D18).
- [ ] **Step 3:** `StoneShapeTest` (Robolectric, `@GraphicsMode(NATIVE)`): a 100×60 `StoneShape` outline's bounds equal the size; two shapes with one seed are `equals`.
- [ ] **Step 4:** Commit `feat(android): stone shape and split stone layout`.

### Task 6: `SquirclePentagonShape`

- [ ] **Step 1: Failing test** `SquirclePentagonShapeTest`: at 128×122 the path's bounds sit inside the box with ≤ 2.5 units of bow overshoot; mirrored about x = 64 within 0.1; five local extrema of distance from (64, 66).
- [ ] **Step 2:** Implement the mock's `pentagon()` in units scaled to the size: `R = 64`, centre (64, 66), `t = 0.28`, `k = 0.04`, `bow = 1` (2 units outward at each side's midpoint, a quadratic), first vertex at −π/2.
- [ ] **Step 3:** Commit `feat(android): the squircle pentagon shape`.

### Task 7: Gallery, gate, PR

- [ ] **Step 1:** `src/screenshotTest/.../core/designsystem/StoneScreenshots.kt`: the quick-actions row (iOS seed), the five-piece stepper, the counters rock, a 48×46 chip, the pentagon; Paper and Night.
- [ ] **Step 2:** Konsist: nothing in `stone/` imports outside androidx and `kotlin`. Gate; push; PR; re-baseline (new references only).

---

## Part 3 — page kit (`feat/1383-page-kit-android`)

### Task 8: `PageTypography` (spec D3)

**Files:** create `core/designsystem/PageTypography.kt`; modify `core/designsystem/PebblesTheme.kt` (a `page` accessor beside `spine` and `hand`), `PebblesHandTypography.kt` (`profileName`).

- [ ] **Step 1:** One `object PageTypography` with every rank of the spec's D3 table, Ysabeau through `ysabeauAt(weight)` with its features (`"lnum, pnum"` lining, `"onum, pnum"` old-style), Inclusive through `InclusiveSansFamily` at `FontWeight(400/500/600)`, sizes and line heights in sp, tracking in em. `profileName = reenieBeanie(60.sp, (-0.075f).em)` with `lineHeight = 54.sp`.
- [ ] **Step 2:** `PageTypographyTest` (JVM): every Inclusive rank is ≥ 12 sp and every body/row rank ≥ 15 sp; every Ysabeau figure rank carries `lnum`.
- [ ] **Step 3:** Commit `feat(android): the page type set`.

### Task 9: `PageShapes` and `PebblesCard` (spec D19)

**Files:** create `core/designsystem/PageShapes.kt`, `core/designsystem/PebblesCard.kt`.

- [ ] **Step 1:** `object PageShapes { val card = RoundedCornerShape(18.dp); val box = RoundedCornerShape(26.dp) }`.
- [ ] **Step 2:** `PebblesCard(modifier, onClick: (() -> Unit)? = null, onClickLabel: String? = null, content: @Composable ColumnScope.() -> Unit)`: `PebblesTheme.spine.colors.stock` fill, `PageShapes.card`; Paper: two `Modifier.dropShadow` layers (0/1/2 dp black 5 %, 0/8/22 dp black 7 %); Night (`spine.colors.isNight`): a 1 dp `rule` border and one 0/8/22 dp black 35 % shadow. Clickable cards clip their ripple to the shape and keep a 48 dp minimum.
- [ ] **Step 3:** Commit.

### Task 10: `SectionHead` and `ChipRow`

**Files:** create `core/designsystem/SectionHead.kt`, `core/designsystem/ChipRow.kt`.

- [ ] **Step 1:** `SectionHead(title: String, modifier, figure: AnnotatedString? = null)`: `sectionHead` title with `heading()` semantics, the figure end-aligned on the baseline in `sectionFigure` (`primary`). A helper `figureOf(done, total, word)` builds "2 *of* 5" with the word in `figureWord` / `onSurfaceVariant` (the caller passes the localised word, so French "2 *sur* 5" works).
- [ ] **Step 2:** `ChipRow(icon: PbblsIcon, title, line, seed, onClick, modifier)`: a 48×46 dp `surfaceContainer` `StoneShape(seed, 16.dp)` holding the icon at `TILE` in `primary`; `rowTitle` over `rowLine`; a chevron (`ic_chevron_right`, kept chrome) in `outline`; one merged button node; 10 dp vertical padding, ≥ 48 dp.
- [ ] **Step 3:** Commit.

### Task 11: `RippleBadge(size)` (spec D15)

**Files:** modify `core/ui/RippleBadge.kt`.

- [ ] **Step 1:** Add `size: Dp = 44.dp, showsLevel: Boolean = true`; replace `.size(44.dp)` with `.size(size)`; stroke width scales `2.dp * size / 44.dp`; skip the digit `Text` when `!showsLevel`. Semantics unchanged.
- [ ] **Step 2:** `RippleBadgeScreenshots` gains a 24 dp, no-digit preview; the two existing references must not change.
- [ ] **Step 3:** Commit.

### Task 12: Kit previews, gate, PR

- [ ] `src/screenshotTest/.../core/designsystem/PageKitScreenshots.kt`: every rank as a specimen line, a card, a box, a section head with a figure, two chip rows; Paper, Night, 2 × font. Gate; PR; re-baseline.

---

## Part 4 — Home data (`feat/1383-home-data-android`)

### Task 13: Models

**Files:** create `core/model/home/HomeWrap.kt` (`HomeWrapMonth(month: YearMonth, count: Int, revealedAt: Instant?, cover: MonthCover?)`, reusing `MonthCover` from `core/model/wrap`), `core/model/home/Guide.kt` (`Guide(id, slug, sortOrder, illustration: GuideIllustration?, title, summary, body)` with EN/FR fields and a `localized(locale)` falling back field by field; `enum GuideIllustration { PEBBLES, STONES, EMOTIONS, VALENCE, DOMAINS, COLLECTIONS, SOULS, GLYPHS, KARMA }`, unknown → null), `core/model/GlyphShelf.kt` (`enum GlyphShelf { MINE, OWNED, COMMU }`, `@Serializable`).

- [ ] **Step 1:** DTOs with `@SerialName` snake case; `revealed_at` parsed tolerantly (fractional seconds or none, `Z` or offset; unparseable → folded) through the app's existing tolerant instant parser (grep `parseInstant\|TolerantInstant` in `core/model`).
- [ ] **Step 2:** Commit.

### Task 14: `HomeService`

**Files:** create `core/data/HomeService.kt` (`HomeServicing` + `@Singleton HomeService`); modify `di/ServiceBindings.kt`; create `src/test/.../testing/FakeHomeService.kt`.

- [ ] **Step 1:** Methods: `displayName(): String` (`profiles.select("display_name").single()`, trimmed, "" on failure, logged), `wraps(zone): List<HomeWrapMonth>` (`home_wraps(p_tz)`, lossy per month with a `Log.w` per dropped row, newest first), `reveal(month, zone): Instant?` (`reveal_wrap(p_month = "YYYY-MM-01", p_tz)`, null `revealed_at` → `DataError.Unknown`), `guides(): List<Guide>` (`guides` where `published = true` order `sort_order`, lossy). Failures map through `toDataError()`; `runCatchingCancellable` only.
- [ ] **Step 2:** Bind in `ServiceBindings`; write the fake.
- [ ] **Step 3:** Commit.

### Task 15: Decoding tests on the oracle payloads

**Files:** modify `apps/android/app/build.gradle.kts` (test `resources.srcDir("../../../packages/supabase/scripts/oracles/home/payloads")`); create `src/test/.../core/model/home/HomeWrapsDecodingTest.kt`, `GuidesDecodingTest.kt`.

- [ ] **Step 1:** Decode `home-wraps.json` verbatim; then the iOS cases from `HomeWrapsDecodingTests.swift` / `HomePayloads.swift` copied as raw strings: a `revealed_at` with and without fractional seconds, an explicit `null`, a month with a malformed cover (dropped, the others kept), an empty `months`. Guides: a row with an unknown illustration (kept, illustration null), a row missing `title_en` (dropped), FR null falling back to EN.
- [ ] **Step 2:** Commit `test(android): decode home payloads as the server and ios send them`.

### Task 16: Achievements: unlocks and the claim

**Files:** modify `core/data/AchievementsService.kt`, `testing/FakeAchievementsService.kt`.

- [ ] **Step 1:** `loadUnlocks(): Set<String>` (achievement ids from `achievement_unlocks.select("achievement_id, unlocked_at")`) and `claimBluePebble(): List<AchievementCheckResult>` (RPC `claim_blue_pebble`, no params; returns the rows; the caller treats an empty list as not granted). Decode the claim with the existing `check-achievements.json` oracle shape.
- [ ] **Step 2:** `AchievementsServiceTest` over the `MockEngine`: an empty array decodes to empty, a row to one result.
- [ ] **Step 3:** Commit.

### Task 17: `TabIntents`, `HomeShortcutsPreference`, gate, PR

**Files:** create `core/data/TabIntents.kt`, `core/data/HomeShortcutsPreference.kt`, tests for both.

- [ ] **Step 1:** `sealed interface TabIntent { data class FocusWeek(val weekStart: LocalDate); data object CreateCollection; data object CreateSoul; data object CarveGlyph }`; `@Singleton class TabIntents @Inject constructor()` with `post(intent)`, `fun <T : TabIntent> take(type: KClass<T>): T?` (returns and clears only a matching intent). `TabIntentsTest`: post then take once; a non-matching take leaves it.
- [ ] **Step 2:** `HomeShortcutsPreference` over `SettingsStore`: `layout: Flow<HomeShortcutsLayout>` (`row|bento`, default row), `stones: Flow<Boolean>` (default true), `lastUsed: Flow<HomeShortcut>` (`camera|quick|pebble`, default camera), setters; keys `home.shortcuts.layout`, `home.shortcuts.stones`, `home.shortcuts.lastUsed`. `HomeShortcutsPreferenceTest`: defaults, round trip, an unknown stored string reads as the default.
- [ ] **Step 3:** Gate (no UI, no references). PR.

---

## Part 5 — the Home tab (`feat/1383-home-tab-android`)

### Task 18: The key and the bar (spec D1)

**Files:** modify `navigation/PebblesKeys.kt`, `navigation/PebblesNavigationSuite.kt`, `navigation/NavigationState.kt`, `RootScreen.kt`, `navigation/PebblesSceneStrategies.kt` (if it switches on tabs), `res/values/strings.xml`, `res/values-fr/strings.xml`; tests `PebblesKeysTest`, `PebblesNavigationIconTest`, `NavigatorRootAtTest`, `NavigatorTest`, `RootGateTest` and the UI tests that click `tab_path` after sign-in.

- [ ] **Step 1: Failing tests.** `PebblesKeysTest`: `tabs == listOf(Home, Path, People, Collections, You)`. Icon test: Home → `HOME` / `HOME_ACTIVE`. `NavigatorRootAtTest`: re-point every `PebblesKey.Path` used as the start route to `Home` (`navigatorOver(startRoute = PebblesKey.Home)`, `rootAt(Home)` re-roots from `[Welcome, Auth]`, sign-out leaves `backStacks[Home] == [Welcome]`); keep the restore case on `People › SoulDetail`. `RootGateTest`: after sign-in `tab_home` is selected.
- [ ] **Step 2:** `data object Home : PebblesKey, TopLevelKey`; `tabs` gains it first; `icon()`/`labelRes()` branches; `tab_home` "Home" / "Accueil"; `startRoute = PebblesKey.Home`; `RootScreen`'s signed-in branch: `wasAlreadyAuthed = navigator.rootKey == PebblesKey.Home`, `rootAt(PebblesKey.Home)`. Fix every "four tabs" comment touched.
- [ ] **Step 3:** Tests pass (the whole `navigation.*` and `ui.*` suites). Commit.

### Task 19: `HomeGreeting` (pure)

**Files:** create `features/home/HomeGreeting.kt`, `src/test/.../features/home/HomeGreetingTest.kt`; strings.

- [ ] **Step 1: Failing test**, the iOS cases: hour < 12 morning, 12–17 afternoon, ≥ 18 evening; Monday + morning → monday-morning; Sunday + evening → sunday-evening; Monday evening and Sunday morning ordinary; blank or whitespace name → title without the comma; dateline `EEEE d MMMM` in EN ("Saturday 31 October") and FR ("samedi 31 octobre").
- [ ] **Step 2:** Implement over `LocalDateTime` and a `Locale`; strings `home_greeting_<key>_title` / `_line` with the iOS EN and FR values (spec D20; the iOS inventory lists them) and `home_greeting_named` "%1$s, %2$s".
- [ ] **Step 3:** Commit.

### Task 20: `HomeViewModel` and `HomeUiState`

**Files:** create `features/home/HomeViewModel.kt`, `HomeUiState.kt`, test `HomeViewModelTest.kt`.

- [ ] **Step 1:** `HomeUiState` is not a single sealed `Loading/Error/Content`: Home has no global spinner or error (iOS), so it is one `data class HomeUiState(name, sections…)` whose sections are each a small sealed type (`Loading`, `Failed`, `Ready(value)`); record this deviation from the `XUiState` convention in the ViewModel's KDoc, with the reason (four independent reads, each keeps its last value). This part fills only `name`.
- [ ] **Step 2:** `onResumed()` reloads; the first load hands off the splash within 1.5 s (iOS race) only if `RootScreen` asks for it, else not at all (Android's splash already releases on auth).
- [ ] **Step 3:** Test: the name lands; a failed name read keeps "".
- [ ] **Step 4:** Commit.

### Task 21: `HomeScreen` shell

**Files:** create `features/home/HomeScreen.kt`, `features/home/HomeContent.kt`, `features/home/components/HomeGreetingBlock.kt`; modify `navigation/PebblesEntryProvider.kt`.

- [ ] **Step 1:** `HomeScreen` collects the state (`collectAsStateWithLifecycle`), `LifecycleResumeEffect` → `onResumed()`, scroll-to-top on tab reselect (the `onReselect` path Path uses). `HomeContent(state, …)` is stateless: a `LazyColumn` on `surface` inside `PebblesScreen` (readable width), 16 dp gutters, 44 dp between sections, no top bar.
- [ ] **Step 2:** `HomeGreetingBlock`: dateline (`dateline`, `primary`), greeting (`greeting`, `BasicText` with `TextAutoSize.StepBased(28.sp, style.fontSize)`, `maxLines = 1` up to font scale 1.3, else two lines unsized), line (`lead`, `onSurfaceVariant`); one merged node "title. line. dateline"; the greeting re-reads the clock each minute (`produceState` with a delay to the next minute).
- [ ] **Step 3:** `HomeGreetingFitTest` (Robolectric): "Bonsoir, Alexandrine" at 360 dp wide, font scale 1 → one line (`TextLayoutResult.lineCount == 1` via a test tag).
- [ ] **Step 4:** Commit.

### Task 22: Docs, gate, PR

- [ ] **Step 1:** Append to `docs/decisions/log.md`: "Android gets a fifth tab, Home, first (#1383)", superseding the four-tab half of 2026-09-21; why (iOS has had Home since #1255; Android had no landing surface; the IA divergence narrows to bar vs floating bar). In `apps/android/CLAUDE.md`, edit the one divergence row ("Four-tab navigation bar…") to name five tabs; nothing else.
- [ ] **Step 2:** `HomeScreenshots` (shell: greeting only; Paper, Night, French, 2 × font). Gate; PR; re-baseline (`NavigationSuiteScreenshots` 8, `ListDetailScreenshots` rail, new Home).

---

## Part 6 — the month grid (`feat/1383-home-month-android`)

### Task 23: Slots

**Files:** create `core/ui/HomeSlots.kt`.

- [ ] `typealias HomeMonthSlot = @Composable (wrap: MonthWrap, onWeek: (LocalDate) -> Unit, modifier: Modifier) -> Unit` and `typealias HomeWrapSlot = @Composable (wrap: MonthWrap, onWeek: (LocalDate) -> Unit, modifier: Modifier) -> Unit`, each with a KDoc naming the filler (`GlyphPickerSlot` is the precedent). Commit.

### Task 24: `HomeMonthGrid` in `features/path`

**Files:** create `features/path/wrap/month/HomeMonthGrid.kt`.

- [ ] **Step 1:** Public `HomeMonthGrid(wrap, onWeek, modifier)`: builds `WrapLookups` from the ambient locals as `PathScreen` does (`PathScreen.kt:153`), calls `WeeksColumns(wrap, lookups, room = 310.dp, onWeek)` with no intro host (verify every `currentIntroReveal()` reads settled without one; if not, provide a settled `IntroReveal` explicitly). 310 dp = 34 + 8 + 7 × 28 + 8 + 64, so a day cell is 28 dp.
- [ ] **Step 2:** Screenshot preview from `month-full.json` and `month-quiet.json` (already test resources): Paper, Night, French, 2 × font. Commit.

### Task 25: Home reads the month and hosts the slot

**Files:** modify `features/home/HomeViewModel.kt`, `HomeContent.kt`, `HomeScreen.kt`, `navigation/PebblesEntryProvider.kt`; `features/path/PathViewModel.kt` (or wherever `PathPage` focus lives), its test.

- [ ] **Step 1:** ViewModel: `cachedMonthWrap(YearMonth.now(zone))` first, then `monthWrap(…)`; failure with no cache → section `Failed` (rendered as nothing, spec D9). Test: cache then network; failure keeps the cache.
- [ ] **Step 2:** `HomeContent` takes `monthSlot: HomeMonthSlot` and draws it first with 22 dp below; the entry provider passes `{ w, onWeek, m -> HomeMonthGrid(w, onWeek, m) }`.
- [ ] **Step 3:** Week tap: `tabIntents.post(FocusWeek(d))`, `navigator.navigate(PebblesKey.Path)`. Path consumes it on resume: `take(FocusWeek::class)?.let { focus(PathPage.Week(it.weekStart)) }`. `PathViewModelTest`: a posted intent focuses the week once.
- [ ] **Step 4:** Commit.

### Task 26: Journey, gate, PR

- [ ] `HomeJourneyTest` (Robolectric, `AppUiTest`): launch lands on Home; clicking a column (by its content description) selects `tab_path` and shows that week. Gate; PR; re-baseline.

---

## Part 7 — quick actions (`feat/1383-quick-actions-android`, `Resolves #1376`)

### Task 27: `HomeShortcut` and the bento order

**Files:** create `features/home/shortcuts/HomeShortcut.kt` (the enum lives in `core/data` with the preference; this file holds the per-shortcut icon and label), test `HomeShortcutsTest.kt`.

- [ ] Test and implement: raw values `camera`, `quick`, `pebble` never change; `bento(lead)` = `[lead] + others in fixed order`; icons `CAMERA`, `PEBBLE_FLAT`, `PLUS_CIRCLE`; labels `home_shortcut_camera` "Camera" / "Appareil photo", `home_shortcut_quick` "Quick moment" / "Moment rapide", `home_shortcut_pebble` "New pebble" / "Nouveau galet". Commit.

### Task 28: `HomeShortcuts` composable

**Files:** create `features/home/shortcuts/HomeShortcuts.kt`.

- [ ] **Step 1:** Row + stones: `SplitStone(3, seed = "camera|fixed|rock|q11", fills = primaryContainer ×3, minHeight = 100.dp)`, each cell icon (`TILE`) over label (`actionLabel`, max 2 lines), ink `onPrimaryContainer`, padding 15/12/14/16 (first) and 20 left for the others. Row + plain: three 18 dp `PageShapes.card`-radius tiles, 8 dp gutters. Bento + stones: `splitBento` with the lead square at 45 % width (grown past square when the lead's text needs it, iOS `ShortcutsBentoLayout`), lead icon 40 dp. Bento + plain: same cells, rounded tiles.
- [ ] **Step 2:** Each cell is a `Role.Button` with its label; a tap writes `lastUsed`, plays the selection haptic (`TapHaptics`), and calls its lambda. `enabledCamera = false` dims the camera cell to 0.6 and disables it.
- [ ] **Step 3:** Above font scale 1.5 the row stacks vertically (spec D18).
- [ ] **Step 4:** Commit.

### Task 29: Settings → Home

**Files:** modify `features/profile/SettingsScreen.kt`, `SettingsViewModel.kt`, strings; `SettingsScreenshots`.

- [ ] A "Home" section: a single-choice segmented row "Quick actions" with Row / Bento ("Rangée" / "Bento"), a `Switch` "Pebble shapes" ("Formes de galet"), footer "The quick actions at the top of Home, on this phone." (FR from iOS, "iPhone" → "téléphone"). Writes through `HomeShortcutsPreference`. `SettingsViewModelTest`: the toggles persist. Commit.

### Task 30: Wire, gate, PR

- [ ] `HomeContent` draws `HomeShortcuts` 26 dp under the greeting line, reading the three preferences in the ViewModel; the lambdas are stubs that navigate to `Capture` for now (Part 8 replaces them). `HomeScreenshots` gains the four styles. Gate; PR (`Resolves #1376`); re-baseline.

---

## Part 8 — the three flows (`feat/1383-shortcut-flows-android`)

### Task 31: `Capture(chainsComplete)`

**Files:** modify `navigation/PebblesKeys.kt` (`data class Capture(val chainsComplete: Boolean = false)`), every `PebblesKey.Capture` call site, `navigation/PebblesEntryProvider.kt`, `features/path/capture/CaptureFlowViewModel.kt`, its test.

- [ ] With `chainsComplete`, a successful save emits `SavedThenComplete(id)` without the "make it a pebble now" choice. Test both modes. Commit.

### Task 32: Quick moment sheet

**Files:** create `features/home/quick/QuickMomentSheet.kt`, `QuickMomentViewModel.kt` (or a state holder inside `HomeViewModel`; prefer a separate `@HiltViewModel` scoped to the sheet's entry if the sheet becomes a key, else a plain state class in `HomeViewModel`), test.

- [ ] `ModalBottomSheet`: title "Quick moment", name `OutlinedTextField` (autofocus, `imeAction = Done`, limit = Capture's), a `WhenPill` opening the stock date and time pickers (default now), "Save & open" (`PebblesPrimaryButton`), "Save & close" (`OutlinedButton`), both disabled while blank or saving. Save: `saveMoment(name, happenedAt.truncatedTo(SECONDS))`, `fireCheck()`, success haptic, dismiss; open → `navigate(CompletePebble(id))`. Error line "Couldn't save the moment. Try again." Test: blank disables; save emits the right effect for each button; error keeps the sheet. Commit.

### Task 33: Camera moment

**Files:** modify `AndroidManifest.xml` (a `FileProvider`, authority `${applicationId}.camera`), create `res/xml/camera_paths.xml` (`<cache-path name="camera" path="camera/"/>`), `features/home/camera/CameraMoment.kt` (state machine), test `CameraMomentTest.kt`.

- [ ] **Step 1:** `rememberLauncherForActivityResult(TakePicture())` over a fresh `cache/camera/<uuid>.jpg` URI; before launching, `packageManager.hasSystemFeature(FEATURE_CAMERA_ANY)` and a resolvable `ACTION_IMAGE_CAPTURE`, else the "No camera yet" `AlertDialog`.
- [ ] **Step 2:** On success: shutter = now floored to whole seconds; `SnapUploadCoordinator.attach(…)` with the file; upload failure → cleanup, "The photo didn't upload. Nothing was saved."; else `saveMoment(happenedAt, snaps = [snap])`, failure → cleanup + "Couldn't save the moment. Try again."; success → `fireCheck()`, revision bump, checklist reload, snackbar effect. The temp file is deleted in every branch. Double capture guarded.
- [ ] **Step 3:** Test each branch against fakes; the temp-file deletion with a fake file. Commit.

### Task 34: The snackbar

**Files:** modify `features/home/HomeScreen.kt`.

- [ ] A `SnackbarHost` in Home's scaffold slot: "Moment saved · %s" (time in the locale's short format) with action "Name it" → `navigate(PebblesKey.EditMoment(id))`; `SnackbarDuration.Long`. Strings `home_toast_saved`, `home_toast_name` ("Moment enregistré", "Nommer"). Commit.

### Task 35: Wire, gate, PR

- [ ] Camera → camera moment; Quick → the sheet; New pebble → `Capture(chainsComplete = true)`; each success bumps the path revision. `HomeJourneyTest`: quick moment "Save & open" lands on Complete. Lint check: the provider and XML pass `./gradlew lint`. Gate; PR; re-baseline (sheet previews).

---

## Part 9 — Your first five (`feat/1383-first-five-android`)

### Task 36: Checklist state (pure)

**Files:** create `features/home/checklist/HomeChecklist.kt`, test `HomeChecklistTest.kt`.

- [ ] Port `HomeChecklist.swift` and the `HomeChecklistTests` matrix: tasks in order moment, pebble, collection, soul, glyph (slugs `first-moment` … `first-glyph`), plus `blue-pebble`; `Unavailable` if any of the six is missing or inactive-and-not-unlocked, `Done` if blue-pebble unlocked, `Claimable` if all five, else `InProgress(done: Set<Task>)`. Commit.

### Task 37: ViewModel

- [ ] `HomeViewModel.loadChecklist()`: `checkIgnoringFailure()`, then catalog and unlocks in parallel, generation guard drops a stale read; `claim()`: `claimBluePebble()`, empty → `claimFailed`, rows → the unlock overlay (`AchievementNotificationService.present`) and reload. Reload after any Part 8 save. Tests: the stale read, the three claim outcomes. Commit.

### Task 38: The box

**Files:** create `features/home/checklist/FirstFiveBox.kt`, `TaskMark.kt`.

- [ ] Box per spec D11 (`surfaceContainer`, `PageShapes.box`, padding 24/16/10/18), `SectionHead` with `figureOf(done, 5, "of")` (FR "sur"), the stepper (`SplitStone(5, "first-five", options)`, 6 dp, decorative), five rows (`TaskMark` 26 dp blob, done/open styles, chevron only when open, done rows not clickable with state "Done"), the claim row ("Claim the blue pebble", `pebble-round.lit-1`, filled, disabled while claiming), the error line. Strings from iOS (`home_first_five_*`, `home_task_*_title/line`). Commit.

### Task 39: Task sheet, illustrations, CTAs

**Files:** create `features/home/checklist/ChecklistTaskSheet.kt`, `ChecklistIllustrations.kt`; modify `features/profile/SoulsListViewModel.kt`, `CollectionsListViewModel.kt`, `features/glyph/store/GlyphsListViewModel.kt` (consume their `TabIntent` on resume and open their create/carve flow).

- [ ] Sheet: illustration 120 dp, body (iOS `home.task.<t>.body`, EN and FR), one filled CTA. Illustrations port `ChecklistIllustrations.swift` with `core/ui` parts (contents row with a blank seal, the seal turning lit, three seals over a collection row, three `SoulMark`s from the three fixed strokes, a glyph in a dashed circle). CTAs per spec D11; the pebble task reads `MomentReadService.pathMoments()` newest uncompleted. Tests: each ViewModel consumes its intent once. Commit.

### Task 40: Gate, PR

- [ ] `HomeScreenshots`: in progress (2 of 5), claimable, French; the task sheet per task. `HomeJourneyTest`: "Add your first soul" opens People's create sheet. Gate; PR; re-baseline.

---

## Part 10 — Monthly wraps (`feat/1383-home-wraps-android`)

### Task 41: Row and cards

**Files:** create `features/home/wraps/HomeWrapsSection.kt`, `HomeWrapCard.kt`, `WrapRing.kt`, `WrapPromiseCard.kt`.

- [ ] `SectionHead("Monthly wraps", figure = count)`; `LazyRow` of 152 dp `PebblesCard`s, 12 dp apart, snap fling; `WrapRing` (116 dp, a dot per day, filled `primary` r 3.6 for a day in `cover.days` with a pebble, `outlineVariant` ring r 1.9 otherwise, first day at the top, clockwise) with the count in `wrapFigure`; month and year (year hidden above 1.5 × font scale); folded card: "folded" over a hairline and an 18 dp turned corner; promise card when empty ("Your first wrap folds on %s.", the live month's last day `d MMMM`). Content descriptions: the iOS plurals `home_wrap_a11y_open` / `_folded`. Error: "Couldn't load your months." + "Try again", keeping the last months. Commit.

### Task 42: The wrap key and sheet

**Files:** modify `navigation/PebblesKeys.kt` (`data class HomeWrap(val month: String) : PebblesKey`), `PebblesEntryProvider.kt` (bottom-sheet metadata on compact, as the glyph detail), create `features/home/wraps/HomeWrapScreen.kt`, `HomeWrapViewModel.kt`, test.

- [ ] Folded: summary (`WrapCopy` line through the slot's host, or the count plural), "Unfold" (`HomeService.reveal`, loading state; failure → "Couldn't load your months."), three dashed 72 dp placeholders. Revealed: `PathWrapService` month (cache first), drawn through `HomeWrapSlot` (filled with `MonthPage` in the entry provider, intro once per launch); week tap → close + `FocusWeek` + Path. The sheet closes itself if its month leaves the list. Test: unfold success flips both the sheet and Home's card (shared `HomeService` result through a reload). Commit.

### Task 43: Order on the page

- [ ] `HomeContent` order: grid, greeting, shortcuts, first five (if shown), wraps, about. Commit.

### Task 44: Gate, PR

- [ ] `HomeWrapScreenshots` (folded, revealed from `month-full.json`) and Home with three wraps, one folded, and the promise card. Gate; PR; re-baseline.

---

## Part 11 — move the markdown body (`feat/1383-markdown-move-android`)

### Task 45: Pure move

- [ ] `git mv apps/android/app/src/main/kotlin/app/pbbls/android/features/lab/components/LabMarkdownBody.kt …/core/ui/MarkdownBody.kt`; rename the function to `MarkdownBody` only if nothing else changes; fix imports in Lab. Grep `lint-baseline.xml` for the old path first (none on `05cf4926`). Konsist: `core/ui` must not import `features` (check the file's imports). Gate (no references move); PR; the diff must read as a rename.

---

## Part 12 — About Pebbles (`feat/1383-guides-android`)

### Task 46: Guide icon map

- [ ] `features/home/guides/GuideIcon.kt`: `GuideIllustration?.icon()` per spec D13; test the nine values and null. Commit.

### Task 47: The list

- [ ] `features/home/guides/GuidesSection.kt`: `SectionHead("About Pebbles")`, then `ChipRow`s (seed `chip-<index>`); hidden rows until the read lands; a failed read keeps the last list. `HomeViewModel` reads `guides()` in parallel with the others. Commit.

### Task 48: The page

**Files:** `PebblesKeys.kt` (`data class Guide(val slug: String) : PebblesKey, BarKey`), entry, `features/home/guides/GuidePage.kt`, `GuideIllustrationView.kt`.

- [ ] Large title, italic summary (`lead`), the 120 dp illustration (port of `GuideIllustrationView.swift` page size from `core/ui` parts; emotions use one family palette each), `MarkdownBody(body)`; spinner while guides are null; "This reading isn't available" for an unknown slug or a failed read. Commit.

### Task 49: Gate, PR

- [ ] `GuideScreenshots` (stones page, missing page), Home with the list. Gate; PR; re-baseline.

---

## Part 13 — You preparation (`feat/1383-you-prep-android`)

### Task 50: Circle on People

**Files:** `features/profile/SoulsListScreen.kt`, entry provider, strings (`people_circle_a11y` "Circle" / "Cercle").

- [ ] A leading top-bar `IconButton` (`PEOPLE`, `TOOLBAR`) opening `PebblesKey.Connections`, mirroring `SoulsListView.swift:40–46`. `YouJourneyTest`: Connections reachable from People. Commit.

### Task 51: Log out in Settings

- [ ] `SettingsScreen` gains "Log out" (`profile_log_out`, moved key or a new `settings_log_out`) as the last row above "Sign out everywhere", calling the sign-out ProfileScreen uses today; the entry provider passes `onSignOut`. Test: tapping it signs out (fake `SupabaseService`). Commit.

### Task 52: Glyphs open on a tab

- [ ] `PebblesKey.Glyphs` → `data class Glyphs(val tab: GlyphShelf = GlyphShelf.MINE)`; `GlyphsListScreen(initialTab)` → `GlyphsListViewModel.start(tab)` maps `GlyphShelf` to `GlyphTab`; every `navigate(PebblesKey.Glyphs)` becomes `Glyphs()`. Test: start on OWNED selects Owned. `PebblesKeysTest` serialisation round trip. Commit.

### Task 53: Gate, PR

- [ ] Gate; PR; re-baseline (`SettingsScreenshots`, `ListDetailScreenshots` souls).

---

## Part 14 — You header, counters, glyphs (`feat/1383-you-header-android`)

Needs Part B merged (or render the count-free lines, spec R5).

### Task 54: Engagement counts

- [ ] `ProfileEngagement` gains `glyphsCarved: Int?`, `glyphsOwned: Int?` (optional); `PathStatsService` exposes them. Decoding test with and without the two keys. Commit.

### Task 55: State and ViewModel

- [ ] `ProfileUiState.Content` drops `collections`, `collectionsLoaded`, `assiduity`; adds `glyphsCarved`, `glyphsOwned`. `ProfileViewModel` stops loading collections. Update `ProfileViewModelTest`. Commit.

### Task 56: The header

**Files:** create `features/profile/components/YouHeader.kt`, `PentagonAvatar.kt`, `RipplesLine.kt`; delete `ProfileBanner.kt` (drops its baseline entry, spec D22).

- [ ] Settings stone button (seed `settings`, `surfaceContainerHigh`, 46×44, `minimumInteractiveComponentSize`), pentagon avatar (`SquirclePentagonShape`, `primaryContainer`, `GlyphView` 58 dp in `onPrimaryContainer` or `PEBBLE_DOTTED_GLYPH`), name (`hand.profileName`, one line with auto-size to 40 sp, heading), since (`since`, month and year in the locale), `RipplesLine` (`RippleBadge(size = 24.dp, showsLevel = false)` + "Ripples Level %d", one node). Commit.

### Task 57: Counters and Glyphs

**Files:** create `features/profile/components/YouCounters.kt`, `YouGlyphsSection.kt`; delete `ProfileStatsCard.kt`, `RipplesRow.kt`, `AssiduityGrid.kt`, `ChunkAssiduity.kt`, `ProfileCountersRow.kt`, `ProfileShortcutsRow.kt`, `ProfileCollectionsCard.kt`, `ProfileLogoutButton.kt`, and `DataTile.kt` / `ProfileCollectionCard.kt` / `SurfaceTile` callers only if nothing else uses them (grep first; the glyph drawer uses `SurfaceTile`).

- [ ] Counters per spec D14.4 (vertical above 1.5 ×). Glyphs: three `ChipRow`s (seeds `chip-g0…2`) with plurals `you_glyphs_mine_line` "The %d marks you carved." etc., count-free variants while null, each → `Glyphs(tab)`. Delete the strings the deleted files orphan (both locales). Commit.

### Task 58: Screen, gate, PR

- [ ] `ProfileScreen` loses its top bar and back lambda; `ProfileContent` (stateless) lays out header, counters, glyphs on `surface`, 44 dp between sections; the entry provider drops the removed lambdas. `ProfileScreenshots` rebuilt (Paper, Night, 2 × font, wide, French). `YouJourneyTest`: Owned glyphs opens the Owned tab. `./gradlew lint` (no `UnusedResources`); if the baseline needs regenerating for the deleted file, diff by issue id. Gate; PR; re-baseline.

---

## Part 15 — Achievements and Lab on You (`feat/1383-you-sections-android`, `Resolves #1383`)

Needs the `whatsapp` icon in `PbblsIcons.kt` (spec R4).

### Task 59: Achievements cards

- [ ] `features/profile/components/YouAchievementsSection.kt`: `SectionHead("Achievements", figure "12 earned")`, a 4-column grid of `PebblesCard`s (icon `TILE` `primary` over `badgeLabel`), each opening Achievements with the title as its label. Delete `ProfileAchievementsCard.kt`. Commit.

### Task 60: Lab data

- [ ] `ProfileViewModel` reads `announcements(1)` and `changelog(3)` from `LogsServicing` in parallel, each failing alone (a failed feed hides its block). Test. Commit.

### Task 61: Announcement card and timeline

- [ ] `YouLabSection.kt`: the card per spec D16 (Coil cover or the split-stone band; kicker, title, line; → `LabAnnouncement(id)`), the timeline (46 dp date column, 9 dp dots, 1 dp rule), the "All updates" pill → `PebblesKey.Lab`. Dates through the composition locale (no `NonObservableLocale`). Commit.

### Task 62: Community callout

- [ ] `YouCommunityCallout.kt`: `tertiaryContainer` `StoneShape("whatsapp", 30.dp)`, `WHATSAPP` icon 32 dp, title, line, chevron; `ACTION_VIEW` on `LabConfig.WHATSAPP_INVITE_URL`, logged on `ActivityNotFoundException`. Delete `ProfileLabCard.kt` and its strings. Commit.

### Task 63: Docs

- [ ] Append to `docs/decisions/log.md`: "You keeps no hub tiles; Connections lives on People and Log out in Settings (#1383)" and "Home and You use a page type set beside M3's scale (#1383)"; file the follow-up issue "[Quality] Should the Android M3 type scale follow the page sizes?" (labels `quality`, `ui`, `android`).

### Task 64: Gate, PR

- [ ] `ProfileScreenshots` complete; `YouJourneyTest`: All updates opens the Lab; the announcement opens its detail. Gate; PR; re-baseline. After merge: the device pass (spec), Arkaik nodes to `releasing` by the App, and the build-journal pebble.

---

## Lessons learned

