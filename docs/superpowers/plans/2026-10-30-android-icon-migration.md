# Android icon migration — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 72 references to hand-traced drawables that carry meaning in the Android app with `PbblsIcon` drawings (and two with shapes), keep the 7 chrome tracings, the 11 wave tracings and the 4 brand marks, delete the 20 replaced drawables, light the navigation bar's selected stone, add `PbblsIconSize`, retire `PebblesIconToken`, and flip `android` into the manifest's `migrated` marker so `lint:replaces` polices the surface. Spec: `docs/superpowers/specs/2026-10-30-android-icon-migration-design.md` (D1–D11). Issue 1315, milestone M66.

**Architecture:** `PbblsIconSize` (hand-written, `core/designsystem/PbblsIconSize.kt`) names the five sizes in dp, `INLINE` alone following the font scale, and adds a `PbblsIconImage(icon, size, …)` overload over the generated `PbblsIconImage(icon, contentDescription, modifier, size: Dp, tint)`. Every helper that took a drawable `Int` or a `Painter` for a Pebbles meaning takes a `PbblsIcon` instead; the three helpers that still draw a kept tracing (`MediaStateLabel`, `WhenPill`, `CarvePillButton`) keep their `Int` and their Pebbles callers pass `PbblsIcon.X.drawableRes`. Android Lint fails on an unused resource, so each drawable is deleted in the commit that removes its last reference; the manifest flips `migrated` in the commit that deletes the last one, so the lint goes live with the code it polices.

**Tech stack:** Kotlin + Jetpack Compose (Material 3 Expressive), JUnit 4 on the plain JVM (`testDebugUnitTest`), Compose Preview Screenshot Testing (`src/screenshotTest`, references recorded on CI only), ktlint (stock `ktlint_official`), Android Lint (`warningsAsErrors`, committed baseline), Konsist; tsx + Vitest in `packages/icons`.

**Stack (gh stack, seven parts = seven PRs, bottom first):**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 1 | `chore/1315-icon-sizes` | `chore(ui): add the android icon sizes and record which drawables the pebbles icons replace` | 1–5 |
| 2 | `chore/1315-navigation` | `chore(android): draw the navigation bar, the detail placeholders and the top-bar actions with pebbles icons` | 6–11 |
| 3 | `chore/1315-capture-complete` | `chore(android): draw capture, the pebble form and the karma flash with pebbles icons` | 12–16 |
| 4 | `chore/1315-path-card` | `chore(android): draw the path card, the fab and the pebble menus with pebbles icons` | 17–20 |
| 5 | `chore/1315-profile` | `chore(android): draw profile, achievements, souls and collections with pebbles icons` | 21–28 |
| 6 | `chore/1315-glyph-lab` | `chore(android): draw glyphs and the lab with pebbles icons, and arm the lint` | 29–34 |
| 7 | `chore/1315-retire-tokens` | `chore(android): retire the PebblesIcon tokens` (`Resolves #1315`) | 35–37 |

Labels on every part: `chore`, `ui`, `android`, `no-lab-note` (a re-skin with no new behaviour; the maintainer can flip the note on a user-facing part). Milestone: `M66 · Brand icons`. PR bodies for Parts 1–6 start `Part N of #1315`, never `Resolves`; Part 7 starts `Resolves #1315`. Never write a Kritik finding id in a body. Arkaik: no change (no view, route, model or endpoint moves).

**Before Part 1:**

- Work in `/Users/alexis/code/pbbls/.worktrees/icons-android`; never `cd` to the main checkout. Until docs PR #1339 merges, this plan lives on `origin/docs/1315-android-icon-migration-spec`: `git show origin/docs/1315-android-icon-migration-spec:docs/superpowers/plans/2026-10-30-android-icon-migration.md`.
- Branch from an updated `main`: `git fetch origin && git switch -c chore/1315-icon-sizes origin/main`.
- Once per worktree: `git config rerere.enabled true && git config remote.pushDefault origin` (the `gh-stack` skill's non-interactive setup).
- If `node_modules/` is absent at the worktree root, run `npm ci` once (the package gate needs `tsx` and `vitest`).
- `apps/android/secrets.properties` is git-ignored. The build never needs it, but a debug APK without it crashes at launch: copy it from `/Users/alexis/code/pbbls/apps/android/secrets.properties` only if you install on a device (the device pass is the maintainer's, see each part).
- Before any Gradle call: `export ANDROID_HOME=$HOME/Library/Android/sdk`. Without it `scripts/gradle-if-sdk.sh` prints `⚠️  @pbbls/android: no Android SDK found` and exits 0 having done nothing.

**The Android gate, run at the end of every part** (from the worktree root):

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: `npm run lint` (ktlint) prints no violation and no `no Android SDK found` line; Gradle ends `BUILD SUCCESSFUL`. Android Lint runs with `warningsAsErrors`, so any new finding (an orphaned drawable is `UnusedResources`) fails the build. `:app:compileDebugScreenshotTestKotlin` compiles the previews, which `assembleDebug` does not. Do **not** run `updateDebugScreenshotTest` or commit a PNG: dozens of renders fail locally on an untouched `main` on this Mac (vector art renders one colour level apart); references are recorded on CI only, see **Re-baselining a part**.

**The package gate** (Parts 1 and 6, from the worktree root):

```bash
npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons
```

Expected: all green; `check` says the generated outputs are fresh (neither `replaces` nor `migrated` reaches an emitter); the last line is `replaces: 27 rows, 49 names, 0 hits` until Task 33 and `replaces: 27 rows, 69 names, 0 hits` from Task 33 on.

**Re-baselining a part (spec D9):** after the part's last code commit is pushed and its `Android` check is red only on `Validate UI screenshots`:

1. Label a part only once the part **below** has its bot commit in (and has been fast-forwarded and rebased up, step 3). Labelling two parts at once makes the upper one record the lower one's old PNGs.
2. Add the label alone, in its own call: `gh pr edit <branch> --add-label rebaseline-screenshots`. `android-screenshots.yml` re-renders on the runner and pushes `test(android): re-baseline ...` onto the branch.
3. Before any further stack command: `git fetch origin`, then fast-forward the local branch to its bot commit: `git branch -f <branch> origin/<branch>` from another branch, or `git merge --ff-only origin/<branch>` while it is checked out (the remote is a strict descendant; if `--ff-only` refuses, stop and look). Then, from the part above (if one exists), `gh stack rebase --no-trunk`; if a PNG conflicts, the upper branch's version wins (`git checkout --theirs <png> && git add <png> && gh stack rebase --continue`). Confirm `git diff --stat origin/<branch> <branch>` prints nothing for the re-baselined branch, then `gh stack push`.
4. The bot's push leaves the new runs at `action_required`. Approve them: `for id in $(gh run list --branch <branch> --status action_required --json databaseId --jq '.[].databaseId'); do gh api -X POST repos/alexisbohns/pbbls/actions/runs/$id/approve; done`. Never re-run the failed run: it replays the tree from before the re-baseline.
5. Open the bot commit's PNGs in the PR's Files tab and look at every one before moving on; a render that changed where this part touched nothing is a bug in this part.

`ListDetailScreenshotsKt` renders the navigation rail and the list panes, so it re-records in Parts 2, 4, 5 and 6: expected churn. `PbblsIconsScreenshotsKt` (the whole set) never changes.

**Reviewing a part** (one reviewer per part, after its gate; the iOS stack's habits, Android reading):

- Read the Gradle output, not a summary: a single new Android Lint warning is an error here (`warningsAsErrors`), and an unused import is a ktlint error.
- Every `PbblsIconImage` passes its size through `size =` (a `PbblsIconSize` or, at a commented fixed slot or an M3 token, a `Dp`); none through `Modifier.size`. No `Image(painterResource(…drawableRes))` anywhere.
- Every deleted drawable has its proof grep in the task and no reference in any source set; `git show --stat` of the commit lists the `.xml` deletion beside the last reference's removal.
- A KDoc or comment on a touched file that still names the old drawing (`fossil`, `scribble`, `lightbulb`, `trophy`, `chat bubbles`) is fixed in the same commit.
- Tests iterate what the code exports (`PbblsIconSize.entries`, `PebblesKey.tabs`) instead of re-declaring it.
- The implementer cannot see signed-in screens (the emulator has no account; the Pixel 8 is the maintainer's real account and is not written to): the visual check is the bot's PNGs in the PR diff plus the part's device-pass list, which goes in the PR body for the maintainer.

**The lint baseline:** `apps/android/app/lint-baseline.xml` holds 53 issues; none names a drawable (`grep -c 'res/drawable' apps/android/app/lint-baseline.xml` prints `0`; its 8 `UnusedResources` are strings and plurals). Deleting the 20 drawables therefore removes no entry. Three touched files carry an entry, all `NonObservableLocale` on a `val locale = Locale.getDefault()` line no task edits: `core/ui/PebbleRow.kt`, `features/glyph/store/GlyphDetailDrawer.kt`, `features/lab/components/LogTimeline.kt`. No task moves a file. No task should need `./gradlew updateLintBaseline`; if one does, run `grep -o 'id="[^"]*"' apps/android/app/lint-baseline.xml | sort | uniq -c` before and after, compare by issue id (never by line), and justify every new id in the PR body.

**Konsist (`ArchitectureBoundaryTest`, `ThemeLiteralsTest`):** `core` never imports `features`, and no feature imports another. Every import this plan adds is `app.pbbls.android.core.designsystem.*` (from `core/ui`, `features/*` or `navigation/`), which is downward. `PbblsIconSize.kt` imports only androidx. No task writes a `Color(0x…)`, a `RoundedCornerShape(<number>)` or a `.copy(fontSize = …)`.

**Imports:** ktlint runs `no-unused-imports` and orders imports in ASCII order (uppercase before lowercase: `…designsystem.PbblsIcon` < `…designsystem.PebblesTheme` < `…designsystem.Spacing` < `…designsystem.toDp`; `app.pbbls.android.R` < `app.pbbls.android.core…`). Every task lists the imports it removes and adds, checked against the file. `./gradlew ktlintFormat` fixes an order, never a missing import.

**Line numbers** are on `main` at `ffbc1aff`. A task earlier in the plan can shift a later task's lines in the same file; match the quoted text, which is exact.

**Entry names this plan uses** (all present in the generated `core/designsystem/PbblsIcons.kt` today): `PATH`, `PATH_ACTIVE`, `PEOPLE`, `PEOPLE_ACTIVE`, `COLLECTION`, `COLLECTION_ACTIVE`, `PERSON`, `PERSON_ACTIVE`, `PLUS_CIRCLE`, `SLIDER`, `PEBBLE_DOTTED_GLYPH`, `CHECK_CIRCLE`, `CHECK_CIRCLE_DOT`, `CHECKMARK_DOT`, `REMOVE`, `CAMERA`, `CAMERA_FOCUS`, `CALENDAR_DATE`, `SPARKLE`, `KARMA_DOT`, `TRASH`, `STACK`, `STACK_LIT_3`, `PEBBLE_ROUND`, `PEBBLE_ROUND_LIT_1`, `PEBBLE_FLAT`, `PEBBLE_GLOW_MD`, `PEBBLE_GLOW_LG`, `HEART`, `CHART_DOUBLE_GROW`.

**Size rules (spec D6), applied by every task:**

- `INLINE` (16, follows font scale) beside text; `ROW` (20) list-row, pill and menu leads; `TOOLBAR` (22) an icon alone in an `IconButton`; `TILE` (28) tiles, form and card leads, achievement family marks; `HERO` (48) placeholders, the Capture photo step, the celebration.
- An M3 slot with its own token keeps it, through the generated `size:` (`ButtonDefaults.IconSize`, `FilterChipDefaults.IconSize`, `ToggleButtonDefaults.IconSize`); the navigation item and the FAB take the generated default (24dp).
- A fixed slot passes a `Dp` through the generated `size:`, with a one-line comment saying why: the band mark (14dp), the glyph cells, the shelf badge (16dp in a 32dp disc), the Lab timeline nodes (16dp in a 16dp column).
- Size goes through `size =`, never `Modifier.size` (the generated helper applies `modifier.size(size)` after yours, and the first size wins).
- Tint: none inside an M3 component that sets the content colour; existing explicit tints carry over unchanged. Never `Image(painterResource(icon.drawableRes))`: the generated vectors stroke white.

**The deletion proof** (each task that deletes a drawable runs it for that name, from the worktree root, over every source set):

```bash
grep -rnE '(R\.drawable\.|@drawable/)<name>\b' apps/android/app/src
```

Expected: no output. Then `git rm apps/android/app/src/main/res/drawable/<name>.xml`.

---

## Part 1 — manifest, wave brief, sizes, gallery (`chore/1315-icon-sizes`)

### Task 1: `replaces.android` per the mapping (spec D8c)

**Files:**
- Modify: `packages/icons/icons.json` (9 lines: the `replaces` line of `plus-circle`, `camera-focus`, `people`, `collection`, `pebble-round`, `karma-dot`, `pebble-glow-md`, `sparkle`, `heart`)

The file is edited **as text**, one whole line per row. Never round-trip it through `JSON.parse`/`JSON.stringify`: each `replaces` object sits on one line, and `JSON.stringify(m, null, 2)` rewrites 1,100 lines (iOS plan lesson).

- [ ] **Step 1: Edit the nine lines.** Each `old` line occurs once in the file; replace it whole with its `new` line.

`plus-circle` (line 64):

```
      "replaces": { "ios": ["plus", "plus.circle"], "android": [], "web": [] }
```
becomes
```
      "replaces": { "ios": ["plus", "plus.circle"], "android": ["ic_plus"], "web": [] }
```

`camera-focus` (line 127):

```
      "replaces": { "ios": ["photo", "photo.on.rectangle.angled"], "android": [], "web": [] }
```
becomes
```
      "replaces": { "ios": ["photo", "photo.on.rectangle.angled"], "android": ["ic_image"], "web": [] }
```

`people` (line 217; drops `ic_people`, which stays at `VisibilityUi.kt:17` as a wave drawable, spec D2):

```
      "replaces": { "ios": ["person.2", "person.3", "person.2.badge.plus", "person.3.fill", "bubble.left.and.bubble.right.fill"], "android": ["ic_people", "ic_person_pair"], "web": ["Users"] }
```
becomes
```
      "replaces": { "ios": ["person.2", "person.3", "person.2.badge.plus", "person.3.fill", "bubble.left.and.bubble.right.fill"], "android": ["ic_person_pair", "ic_chat_bubbles"], "web": ["Users"] }
```

`collection` (line 289):

```
      "replaces": { "ios": ["circle.hexagongrid", "square.stack.3d.up"], "android": ["ic_pebble_collection"], "web": ["Layers"] }
```
becomes
```
      "replaces": { "ios": ["circle.hexagongrid", "square.stack.3d.up"], "android": ["ic_pebble_collection", "ic_stack"], "web": ["Layers"] }
```

`pebble-round` (line 685):

```
      "replaces": { "ios": ["fossil.shell", "drop.fill", "circle.dashed", "circle.inset.filled", "square"], "android": ["ic_fossil_shell"], "web": ["Stone"] }
```
becomes
```
      "replaces": { "ios": ["fossil.shell", "drop.fill", "circle.dashed", "circle.inset.filled", "square"], "android": ["ic_fossil_shell", "ic_circle_dashed", "ic_circle_inset_filled"], "web": ["Stone"] }
```

`karma-dot` (line 703):

```
      "replaces": { "ios": ["sparkle", "sparkles"], "android": [], "web": [] }
```
becomes
```
      "replaces": { "ios": ["sparkle", "sparkles"], "android": ["ic_sparkle"], "web": [] }
```

`pebble-glow-md` (line 838):

```
      "replaces": { "ios": ["trophy"], "android": [], "web": [] }
```
becomes
```
      "replaces": { "ios": ["trophy"], "android": ["ic_trophy"], "web": [] }
```

`sparkle` (line 919):

```
      "replaces": { "ios": ["lightbulb.max"], "android": [], "web": [] }
```
becomes
```
      "replaces": { "ios": ["lightbulb.max"], "android": ["ic_lightbulb"], "web": [] }
```

`heart` (line 1009):

```
      "replaces": { "ios": ["heart", "waveform.path.ecg"], "android": [], "web": ["Heart"] }
```
becomes
```
      "replaces": { "ios": ["heart", "waveform.path.ecg"], "android": ["ic_pebble_emotion"], "web": ["Heart"] }
```

The seven other rows with an Android name keep theirs: `checkmark-dot` `ic_check`, `check-circle` `ic_check_circle`, `person` `ic_person`, `slider` `ic_gear`, `pebble-dotted-glyph` `ic_scribble`, `calendar-date` `ic_calendar`, `trash` `ic_trash`.

- [ ] **Step 2: Check the shape of the edit.** From the worktree root:

```bash
git diff --stat packages/icons/icons.json
node -e 'const m=require("./packages/icons/icons.json");const r=m.icons.filter(x=>x.replaces.android.length>0);console.log(r.length, r.flatMap(x=>x.replaces.android).length)'
node -e 'const m=require("./packages/icons/icons.json");console.log(m.icons.flatMap(x=>x.replaces.android).sort().join(" "))'
```

Expected, in order: `1 file changed, 9 insertions(+), 9 deletions(-)`; `16 20`; and exactly the stack's delete list (the invariant: `replaces.android` lists exactly the drawables this stack deletes):

```
ic_calendar ic_chat_bubbles ic_check ic_check_circle ic_circle_dashed ic_circle_inset_filled ic_fossil_shell ic_gear ic_image ic_lightbulb ic_pebble_collection ic_pebble_emotion ic_person ic_person_pair ic_plus ic_scribble ic_sparkle ic_stack ic_trash ic_trophy
```

- [ ] **Step 3: The package gate** (block above). Expected: green, `replaces: 27 rows, 49 names, 0 hits` (Android is not in any `migrated` yet, so its names are not read). `manifest.test.ts`'s "lists bare symbol names, each on one row" is what would catch a drawable listed under two rows.

- [ ] **Step 4: Commit**

```bash
git add packages/icons/icons.json
git commit -m "chore(ui): record which hand-traced drawables each pebbles icon replaces on android"
```

### Task 2: The wave brief's Android lines and the hour figure (spec D2)

**Files:**
- Modify: `packages/icons/waves/2026-11-chrome/brief.md` (whole file, 22 → 23 lines)

- [ ] **Step 1: Rewrite the brief.** Replace the whole file with:

```markdown
# Pebbles icon set — the chrome wave

Thirteen figures the iOS and Android apps still draw as SF Symbols and hand-traced drawables because the foundation wave (`../2026-10-foundation/`) has nothing that means them (iOS migration spec D2, `docs/superpowers/specs/2026-10-30-ios-icon-migration-design.md`; Android migration spec D2, `docs/superpowers/specs/2026-10-30-android-icon-migration-design.md`). Same hand, same pen, same canvas as the foundation brief (`../2026-10-foundation/BRIEF.md`, DNA and Canvas sections). Judge at 20 pt in a list row and at 16 pt beside caption text.

Sets travel together: the visibility trio and the error family are drawn as a set or not at all, so no picker ends up with two vocabularies.

## The figures (meaning only; you choose the subject)

1. again — try once more, replay (`arrow.clockwise.circle.fill`, `arrow.counterclockwise`; Android `ic_refresh`, `ic_replay`)
2. swap — trade one thing for another (`arrow.left.arrow.right`; Android `ic_alternating_current`, between "me" and the creator in the glyph drawer)
3. upload — a photo on its way up, a status, not a button (`arrow.up.circle`; Android `ic_arrow_up_circle`)
4. upvote — I want this, in the Lab's backlog (`arrow.up.circle`, `arrow.up.circle.fill`; Android `ic_arrow_up_circle`, `ic_arrow_up_circle_fill`); related to 3, must read apart from it
5. something went wrong — the error family, one figure in three strengths or three figures: a load that failed, a link that broke, a page that is gone (`exclamationmark.triangle`, `exclamationmark.triangle.fill`, `person.2.slash`, `link.badge.plus`, `book.closed`; Android `ic_warning`)
6. document — terms, a policy, a text to read (`doc.text`)
7. privacy — a hand held up, what we keep for ourselves (`hand.raised`)
8. visibility, secret — only me (`lock.fill`; Android `ic_lock`)
9. visibility, private — me and my circle (`person.2.fill`; Android `ic_people`); with 8 and 10 a trio, three drawings made together
10. visibility, public — anyone (`globe`; Android `ic_globe`)
11. locked — an achievement not yet earned (`lock`; Android `ic_lock`); must read apart from 8
12. invite — bring someone into the circle (`person.badge.plus`)
13. life domain — a compass, a territory, an area of one's life (`safari`; Android `ic_pebble_domain`)
14. market — where glyphs are traded (`storefront`)
15. hour — a time of day, beside a date (Android `ic_pebble_when`, the time pill on Capture's When step; iOS draws no icon there)
```

(Fifteen numbered lines for thirteen concepts: the visibility trio is three lines. Every one of the eleven kept wave drawables is named: `ic_arrow_up_circle`, `ic_arrow_up_circle_fill`, `ic_refresh`, `ic_replay`, `ic_warning`, `ic_lock`, `ic_people`, `ic_globe`, `ic_alternating_current`, `ic_pebble_domain`, `ic_pebble_when`.)

- [ ] **Step 2: Lint.** `npm run lint --workspace=@pbbls/icons`. Expected: green (the lint only checks that each row's wave folder exists).

- [ ] **Step 3: Commit**

```bash
git add packages/icons/waves/2026-11-chrome/brief.md
git commit -m "chore(ui): add the android drawables and the hour figure to the chrome wave brief"
```

### Task 3: `PbblsIconSize` and the sized overload (spec D6)

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIconSize.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/core/designsystem/PbblsIconSizeTest.kt`

- [ ] **Step 1: Write the failing test** `PbblsIconSizeTest.kt`:

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The five sizes a pebbles icon draws at (Android icon migration spec D6, the
 * iOS `PbblsIconSize` values), and the one that follows the font scale.
 */
class PbblsIconSizeTest {
    @Test
    fun `the five sizes, in order, from the 16 floor`() {
        assertEquals(listOf(16f, 20f, 22f, 28f, 48f), PbblsIconSize.entries.map { it.value })
        assertEquals(listOf(PbblsIconSize.INLINE), PbblsIconSize.entries.filter { it.followsFontScale })
    }

    @Test
    fun `at the default font scale every size is its value in dp`() {
        val density = Density(density = 2f, fontScale = 1f)
        PbblsIconSize.entries.forEach { size ->
            assertEquals(size.name, size.value.dp, size.toDp(density))
        }
    }

    @Test
    fun `at 200 percent inline follows the platform curve and the others hold`() {
        val doubled = Density(density = 1f, fontScale = 2f)
        // Compose applies Android's non-linear font scale (API 34+ curve) here too:
        // 16sp at 2x sits halfway between the table's 14 -> 26 and 18 -> 30 points.
        assertEquals(28f, PbblsIconSize.INLINE.toDp(doubled).value, 0.01f)
        assertEquals(20.dp, PbblsIconSize.ROW.toDp(doubled))
        assertEquals(22.dp, PbblsIconSize.TOOLBAR.toDp(doubled))
        assertEquals(28.dp, PbblsIconSize.TILE.toDp(doubled))
        assertEquals(48.dp, PbblsIconSize.HERO.toDp(doubled))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run (from the worktree root): `(cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.core.designsystem.PbblsIconSizeTest')`
Expected: compilation fails, `Unresolved reference 'PbblsIconSize'`.

- [ ] **Step 3: Implement** `PbblsIconSize.kt`:

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The five sizes a pebbles icon draws at (Android icon migration spec D6; the
 * iOS `PbblsIconSize` values). A 24-unit drawing with a 1.75 stroke draws a
 * 1.1dp line at 16, the floor. Hand-written: the generator owns `PbblsIcons.kt`
 * only.
 *
 * - [INLINE] beside text: karma amounts, prices, the soul count, photo states.
 *   The one size that follows the font scale, through the platform's own sp
 *   curve, so the stone keeps pace with the line it sits in (this narrows the
 *   package spec's D9 for one size).
 * - [ROW] list-row, pill and menu-item leads.
 * - [TOOLBAR] an icon alone in an `IconButton` (its 48dp target does not grow).
 * - [TILE] `SurfaceTile`, form and card leads, achievement family marks.
 * - [HERO] detail placeholders, the Capture photo step, the achievement celebration.
 *
 * An M3 slot with its own icon token (`ButtonDefaults.IconSize`,
 * `FilterChipDefaults.IconSize`, `ToggleButtonDefaults.IconSize`, the 24dp
 * navigation and FAB icons) keeps that token. A fixed dp slot (the band mark,
 * the glyph cells, the shelf badge) passes its `Dp` through the generated
 * `PbblsIconImage(size =)` instead.
 */
enum class PbblsIconSize(
    val value: Float,
    val followsFontScale: Boolean,
) {
    INLINE(16f, true),
    ROW(20f, false),
    TOOLBAR(22f, false),
    TILE(28f, false),
    HERO(48f, false),
}

/** The side to draw at: [PbblsIconSize.value] read as sp when it follows the font scale, as dp otherwise. */
fun PbblsIconSize.toDp(density: Density): Dp =
    if (followsFontScale) {
        with(density) { value.sp.toDp() }
    } else {
        value.dp
    }

/**
 * [PbblsIconImage] at a named [size]. Pass the size here (or a `Dp` to the
 * generated overload's `size`), never through [modifier]: the generated helper
 * applies `modifier.size(size)` after yours, and the first size wins.
 */
@Composable
fun PbblsIconImage(
    icon: PbblsIcon,
    size: PbblsIconSize,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    PbblsIconImage(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        size = size.toDp(LocalDensity.current),
        tint = tint,
    )
}
```

- [ ] **Step 4: Run the test again**

Run: `(cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.core.designsystem.PbblsIconSizeTest')`
Expected: `BUILD SUCCESSFUL`, 3 tests passed. If the 200% case prints `expected:<28.0> but was:<32.0>`, the Compose version on the classpath has dropped the non-linear curve on the JVM: stop and report, do not change the expected value to make it pass.

- [ ] **Step 5: ktlint** `npm run lint --workspace=@pbbls/android`. Expected: no violation.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIconSize.kt apps/android/app/src/test/kotlin/app/pbbls/android/core/designsystem/PbblsIconSizeTest.kt
git commit -m "chore(android): name the five sizes a pebbles icon draws at"
```

### Task 4: The design-system gallery draws pebbles icons at the five sizes

**Files:**
- Modify: `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/DesignSystemScreenshots.kt` (imports 18–24, KDoc 34–40, `IconGallery` 95–125)

This frees the eleven `R.drawable` names the gallery holds (`ic_gear`, `ic_person`, `ic_person_pair`, `ic_sparkle`, `ic_calendar`, `ic_fossil_shell`, `ic_alternating_current`, `ic_chevron_right`, `ic_stack`, `ic_scribble`, `ic_plus`) so later parts can delete theirs, and its use of `PebblesIconToken` so Part 7 can delete the tokens.

- [ ] **Step 1: The imports.** Add `import androidx.compose.ui.Alignment` directly above `import androidx.compose.ui.Modifier`. Delete `import androidx.compose.ui.res.painterResource`, `import app.pbbls.android.core.designsystem.PebblesIcon` and `import app.pbbls.android.core.designsystem.PebblesIconToken`. Add, directly above `import app.pbbls.android.core.designsystem.PebblesListSection`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`import androidx.compose.foundation.layout.size` stays: `LoadingIndicator` uses it.)

- [ ] **Step 2: The KDoc.** Replace

```kotlin
 * profile-card chrome, and the milestone icon batch at every size token —
 * light and dark. These are the maintainer's visual review surface for the
 * idiom kit before Profile screens compose from it.
```

with

```kotlin
 * profile-card chrome, and six pebbles icons at each of the five
 * [PbblsIconSize]s (Android icon migration spec D6), light and dark. These are
 * the maintainer's visual review surface for the idiom kit and the icon sizes.
```

- [ ] **Step 3: `IconGallery`.** Replace the whole function

```kotlin
@Composable
private fun IconGallery() {
    val icons =
        listOf(
            R.drawable.ic_gear,
            R.drawable.ic_person,
            R.drawable.ic_person_pair,
            R.drawable.ic_sparkle,
            R.drawable.ic_calendar,
            R.drawable.ic_fossil_shell,
            R.drawable.ic_alternating_current,
            R.drawable.ic_chevron_right,
            R.drawable.ic_stack,
            R.drawable.ic_scribble,
            R.drawable.ic_plus,
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PebblesIconToken.entries.forEach { token ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                icons.forEach { resId ->
                    PebblesIcon(
                        painter = painterResource(resId),
                        token = token,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

with

```kotlin
@Composable
private fun IconGallery() {
    // One row per size, the same six drawings in each: a toolbar action, a
    // soul, karma, a date, a pebble and the create mark.
    val icons =
        listOf(
            PbblsIcon.SLIDER,
            PbblsIcon.PEOPLE,
            PbblsIcon.KARMA_DOT,
            PbblsIcon.CALENDAR_DATE,
            PbblsIcon.PEBBLE_ROUND,
            PbblsIcon.PLUS_CIRCLE,
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PbblsIconSize.entries.forEach { size ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons.forEach { icon ->
                    PbblsIconImage(
                        icon = icon,
                        size = size,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Compile the previews and check nothing old is left**

Run: `(cd apps/android && ./gradlew :app:compileDebugScreenshotTestKotlin)` then `grep -rn 'R\.drawable\.\|PebblesIcon\b\|PebblesIconToken' apps/android/app/src/screenshotTest`
Expected: `BUILD SUCCESSFUL`; the grep prints nothing.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/DesignSystemScreenshots.kt
git commit -m "chore(android): draw the design-system icon gallery with pebbles icons at the five sizes"
```

### Task 5: Part 1 gate, PR, references

- [ ] **Step 1: The package gate** (block at the top). Expected: green, `replaces: 27 rows, 49 names, 0 hits`.
- [ ] **Step 2: The Android gate**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation; `BUILD SUCCESSFUL`. No drawable is orphaned yet (every gallery name still has a `src/main` reference).

- [ ] **Step 3: Stack and PR.** The branch exists, so `init` adopts it:

```bash
gh stack init chore/1315-icon-sizes
gh stack submit --auto --open
gh pr edit chore/1315-icon-sizes --title "chore(ui): add the android icon sizes and record which drawables the pebbles icons replace" --add-label chore,ui,android,no-lab-note --milestone "M66 · Brand icons" --body-file - <<'EOF'
Part 1 of #1315. Spec: `docs/superpowers/specs/2026-10-30-android-icon-migration-design.md`; plan: `docs/superpowers/plans/2026-10-30-android-icon-migration.md`.

- `packages/icons/icons.json`: `replaces.android` lists exactly the 20 drawables this stack deletes (16 rows); `ic_people` leaves `people` (it stays at the visibility trio, a wave drawable). Android is not in any `migrated` yet, so `lint:replaces` does not read these names until Part 6.
- `packages/icons/waves/2026-11-chrome/brief.md`: the Android drawable on each figure's line, and figure 15 (hour).
- `core/designsystem/PbblsIconSize.kt`: INLINE 16 (follows the font scale), ROW 20, TOOLBAR 22, TILE 28, HERO 48, and a `PbblsIconImage(icon, size, …)` overload; `PbblsIconSizeTest`.
- `DesignSystemScreenshots.kt`: the icon gallery draws six pebbles icons at the five sizes (frees the eleven tracings and `PebblesIconToken` it named).

References: `DesignSystemScreenshotsKt` (4) re-record.
EOF
```

- [ ] **Step 4: Re-baseline**

```bash
gh pr edit chore/1315-icon-sizes --add-label rebaseline-screenshots
# wait for the bot's "test(android): re-baseline ..." commit on origin/chore/1315-icon-sizes, then:
git fetch origin && git merge --ff-only origin/chore/1315-icon-sizes   # while chore/1315-icon-sizes is checked out
for id in $(gh run list --branch chore/1315-icon-sizes --status action_required --json databaseId --jq '.[].databaseId'); do gh api -X POST repos/alexisbohns/pbbls/actions/runs/$id/approve; done
```

Full procedure at the top (steps 1–5). Expected bot commit: the four `DesignSystemScreenshotsKt` renders (`DesignSystemChromeLight`, `DesignSystemChromeDark`, `ChromeGalleryHighContrastLight`, `ChromeGalleryHighContrastDark`). Look at them: five rows, the last at 48dp; the INLINE row at 16dp (previews render at font scale 1).

### Device pass, Part 1

None: no screen draws anything new. The gallery is the review surface for the sizes.

---

## Part 2 — navigation bar, detail placeholders, top-bar actions (`chore/1315-navigation`)

`gh stack add chore/1315-navigation` from Part 1's branch.

### Task 6: `PebblesKey.icon()`

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesNavigationSuite.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/navigation/PebblesNavigationIconTest.kt`

- [ ] **Step 1: Write the failing test** `PebblesNavigationIconTest.kt`:

```kotlin
package app.pbbls.android.navigation

import app.pbbls.android.core.designsystem.PbblsIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The four tabs draw what iOS's `AppTab.icon` draws for the same meaning, and
 * each has the `active` twin the bar lights when it is selected (Android icon
 * migration spec D5; the mirror of iOS `AppTabTests`).
 */
class PebblesNavigationIconTest {
    @Test
    fun `each tab draws the icon iOS draws for it`() {
        assertEquals(
            listOf(PbblsIcon.PATH, PbblsIcon.PEOPLE, PbblsIcon.COLLECTION, PbblsIcon.PERSON),
            PebblesKey.tabs.map { it.icon() },
        )
    }

    @Test
    fun `every tab has an active twin`() {
        PebblesKey.tabs.forEach { tab -> assertNotNull("$tab has no active twin", tab.icon().activeTwin) }
        assertEquals(
            listOf(PbblsIcon.PATH_ACTIVE, PbblsIcon.PEOPLE_ACTIVE, PbblsIcon.COLLECTION_ACTIVE, PbblsIcon.PERSON_ACTIVE),
            PebblesKey.tabs.map { it.icon().activeTwin },
        )
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `(cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.navigation.PebblesNavigationIconTest')`
Expected: compilation fails, `Unresolved reference 'icon'`.

- [ ] **Step 3: Implement.** In `PebblesNavigationSuite.kt`, add `import app.pbbls.android.core.designsystem.PbblsIcon` directly below `import app.pbbls.android.R`, and add, directly below the closing brace of `private fun PebblesKey.iconRes()` (line 143):

```kotlin

/** The tab's drawing; the bar lights its `active` twin when selected (spec D5, iOS `AppTab.icon`). */
internal fun PebblesKey.icon(): PbblsIcon =
    when (this) {
        PebblesKey.Path -> PbblsIcon.PATH
        PebblesKey.People -> PbblsIcon.PEOPLE
        PebblesKey.Collections -> PbblsIcon.COLLECTION
        PebblesKey.You -> PbblsIcon.PERSON
        else -> error("$this is not a tab")
    }
```

- [ ] **Step 4: Run the test again.** Same command. Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesNavigationSuite.kt apps/android/app/src/test/kotlin/app/pbbls/android/navigation/PebblesNavigationIconTest.kt
git commit -m "chore(android): give each tab its pebbles icon"
```

### Task 7: The navigation bar lights its stone

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesNavigationSuite.kt` (imports, KDoc 58–62, line 76, lines 135–143)

- [ ] **Step 1: The item's icon.** Replace line 76

```kotlin
            icon = { Icon(painter = painterResource(tab.iconRes()), contentDescription = null) },
```

with

```kotlin
            icon = {
                // The navigation icon slot is M3's 24dp (spec D6): the generated default.
                PbblsIconImage(
                    icon = if (selected) tab.icon().activeTwin ?: tab.icon() else tab.icon(),
                    contentDescription = null,
                )
            },
```

No tint: `NavigationSuiteItem` sets `LocalContentColor` per selection state, and the label `Text` carries the name for TalkBack. The rail on large screens takes the same items.

- [ ] **Step 2: Delete `iconRes()`.** Delete these lines (135–143) and the blank line that follows them:

```kotlin
@DrawableRes
private fun PebblesKey.iconRes(): Int =
    when (this) {
        PebblesKey.Path -> R.drawable.ic_stack
        PebblesKey.People -> R.drawable.ic_people
        PebblesKey.Collections -> R.drawable.ic_pebble_collection
        PebblesKey.You -> R.drawable.ic_person
        else -> error("$this is not a tab")
    }
```

- [ ] **Step 3: The KDoc.** Replace

```kotlin
 * `Icon`/`Text` read their color from `LocalContentColor`, which the item
 * sets from these colors per selection state, so neither needs its own tint.
```

with

```kotlin
 * The icon and the `Text` read their color from `LocalContentColor`, which the
 * item sets from these colors per selection state, so neither needs its own
 * tint. The selected tab draws its icon's `active` twin, the lit stone (Android
 * icon migration spec D5); the M3 indicator stays behind it.
```

- [ ] **Step 4: Imports.** Delete `import androidx.annotation.DrawableRes`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`. Add `import app.pbbls.android.core.designsystem.PbblsIconImage` directly below `import app.pbbls.android.core.designsystem.PbblsIcon`. (`R` stays: `labelRes()` reads strings.)

- [ ] **Step 5: Build and test.** `(cd apps/android && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests 'app.pbbls.android.navigation.*')`. Expected: `BUILD SUCCESSFUL`. No drawable is orphaned: `ic_stack` (AchievementCopy, ProfileCollectionCard), `ic_people` (placeholder, `VisibilityUi`), `ic_pebble_collection`, `ic_person` (`GlyphTabBar`) all keep a reference.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesNavigationSuite.kt
git commit -m "chore(android): light the selected tab's stone"
```

### Task 8: `DetailPlaceholder(icon:)` and its three callers

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/DetailPlaceholder.kt` (whole file)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesSceneStrategies.kt` (imports, lines 163–173)

- [ ] **Step 1: `DetailPlaceholder.kt`.** Replace the whole file with:

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

/**
 * What a detail pane shows before anything is picked (#940): a muted icon and
 * one line, centered. Only ever composed beside its list on large screens.
 */
@Composable
fun DetailPlaceholder(
    icon: PbblsIcon,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(PebblesTheme.spacing.xl)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PbblsIconImage(
            icon = icon,
            size = PbblsIconSize.HERO,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 2: The callers.** In `PebblesSceneStrategies.kt`, add `import app.pbbls.android.core.designsystem.PbblsIcon` directly below `import app.pbbls.android.core.designsystem.DetailPlaceholder`, and replace

```kotlin
    DetailPlaceholder(iconRes = R.drawable.ic_people, text = stringResource(R.string.souls_detail_placeholder))
```
```kotlin
    DetailPlaceholder(iconRes = R.drawable.ic_pebble_collection, text = stringResource(R.string.collections_detail_placeholder))
```
```kotlin
    DetailPlaceholder(iconRes = R.drawable.ic_scribble, text = stringResource(R.string.glyphs_detail_placeholder))
```

with, respectively,

```kotlin
    DetailPlaceholder(icon = PbblsIcon.PEOPLE, text = stringResource(R.string.souls_detail_placeholder))
```
```kotlin
    DetailPlaceholder(icon = PbblsIcon.COLLECTION, text = stringResource(R.string.collections_detail_placeholder))
```
```kotlin
    DetailPlaceholder(icon = PbblsIcon.PEBBLE_DOTTED_GLYPH, text = stringResource(R.string.glyphs_detail_placeholder))
```

- [ ] **Step 3: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin :app:compileDebugScreenshotTestKotlin)`. Expected: `BUILD SUCCESSFUL` (`ListDetailScreenshots.kt` calls the three placeholder functions, whose signatures do not change).

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/DetailPlaceholder.kt apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesSceneStrategies.kt
git commit -m "chore(android): draw the detail placeholders with pebbles icons"
```

### Task 9: The three top-bar pluses

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulsListScreen.kt` (imports, lines 141–146)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/CollectionsListScreen.kt` (imports, lines 148–153)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphsListScreen.kt` (imports, lines 149–154)

- [ ] **Step 1: `SoulsListScreen.kt`.** Replace

```kotlin
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.souls_add_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
```

with

```kotlin
                        PbblsIconImage(
                            icon = PbblsIcon.PLUS_CIRCLE,
                            size = PbblsIconSize.TOOLBAR,
                            contentDescription = stringResource(R.string.souls_add_a11y),
                            tint = colors.onSurfaceVariant,
                        )
```

and add, directly above `import app.pbbls.android.core.designsystem.PebblesScreen`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`Icon`, `painterResource`, `size` and `dp` stay: the back arrow and the trash menu use them.)

- [ ] **Step 2: `CollectionsListScreen.kt`.** Replace

```kotlin
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.collections_add_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
```

with

```kotlin
                        PbblsIconImage(
                            icon = PbblsIcon.PLUS_CIRCLE,
                            size = PbblsIconSize.TOOLBAR,
                            contentDescription = stringResource(R.string.collections_add_a11y),
                            tint = colors.onSurfaceVariant,
                        )
```

and add the same three imports directly above `import app.pbbls.android.core.designsystem.PebblesListSection`.

- [ ] **Step 3: `GlyphsListScreen.kt`.** Replace

```kotlin
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.glyphs_carve_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
```

with

```kotlin
                        PbblsIconImage(
                            icon = PbblsIcon.PLUS_CIRCLE,
                            size = PbblsIconSize.TOOLBAR,
                            contentDescription = stringResource(R.string.glyphs_carve_a11y),
                            tint = colors.onSurfaceVariant,
                        )
```

and add the same three imports directly above `import app.pbbls.android.core.designsystem.PebblesScreen`.

- [ ] **Step 4: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin)`. Expected: `BUILD SUCCESSFUL`. `ic_plus` keeps four references (the Complete CTA and the FAB, Part 4; the collection card, Part 5; the glyph create cell, Part 6).

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulsListScreen.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/CollectionsListScreen.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphsListScreen.kt
git commit -m "chore(android): draw the top-bar add actions with pebbles icons"
```

### Task 10: The settings gear, and `ic_gear` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/ProfileScreen.kt` (imports, lines 111–114)
- Delete: `apps/android/app/src/main/res/drawable/ic_gear.xml`

- [ ] **Step 1: The gear.** Replace

```kotlin
                        Icon(
                            painter = painterResource(R.drawable.ic_gear),
                            contentDescription = stringResource(R.string.settings_title),
                        )
```

with

```kotlin
                        PbblsIconImage(
                            icon = PbblsIcon.SLIDER,
                            size = PbblsIconSize.TOOLBAR,
                            contentDescription = stringResource(R.string.settings_title),
                        )
```

No tint: the top bar's `IconButton` sets the content colour. Add, directly above `import app.pbbls.android.core.designsystem.PebblesScreen`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`Icon` and `painterResource` stay: the back arrow.)

- [ ] **Step 2: Delete the drawable.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_gear\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_gear.xml
```

Expected: the grep prints nothing (the gallery stopped naming it in Task 4). `ic_gear.xml` had no lint-baseline entry.

- [ ] **Step 3: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL` (no `UnusedResources`, no missing resource).

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/ProfileScreen.kt
git commit -m "chore(android): draw the settings action with the slider and drop the gear tracing"
```

### Task 11: Part 2 gate, PR, references

- [ ] **Step 1: The Android gate**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation; `BUILD SUCCESSFUL`, `PebblesNavigationIconTest` and `PbblsIconSizeTest` among the passes.

- [ ] **Step 2: Push and PR.** Fetch first (`git fetch origin`) and fast-forward Part 1 if its bot commit landed since you last looked (procedure step 3), then:

```bash
gh stack push
gh stack submit --auto --open
gh pr edit chore/1315-navigation --title "chore(android): draw the navigation bar, the detail placeholders and the top-bar actions with pebbles icons" --add-label chore,ui,android,no-lab-note --milestone "M66 · Brand icons" --body-file - <<'EOF'
Part 2 of #1315.

- `PebblesKey.icon()` (Path `path`, People `people`, Collections `collection`, You `person`); the selected item draws the `active` twin at M3's 24dp, behind the indicator; `PebblesNavigationIconTest`.
- `DetailPlaceholder(icon:)` at HERO; the souls, collections and glyphs placeholders.
- The three top-bar adds (souls, collections, glyphs) and the settings action (`slider`) at TOOLBAR, labels unchanged.
- `ic_gear.xml` deleted (no reference left).

References: `NavigationSuiteScreenshotsKt` and `ListDetailScreenshotsKt` re-record (the bar and rail, the placeholders, the top bars).
EOF
```

- [ ] **Step 3: Re-baseline**

```bash
git fetch origin && git log --oneline -1 origin/chore/1315-icon-sizes   # the part below: its bot commit must be in and fast-forwarded first
gh pr edit chore/1315-navigation --add-label rebaseline-screenshots
# wait for the bot's "test(android): re-baseline ..." commit on origin/chore/1315-navigation, then:
git fetch origin && git merge --ff-only origin/chore/1315-navigation   # while chore/1315-navigation is checked out
for id in $(gh run list --branch chore/1315-navigation --status action_required --json databaseId --jq '.[].databaseId'); do gh api -X POST repos/alexisbohns/pbbls/actions/runs/$id/approve; done
```

Full procedure at the top (steps 1–5); label only once Part 1's bot commit is in. Expected classes: `NavigationSuiteScreenshotsKt` (about 8), `ListDetailScreenshotsKt` (about 20), plus `ProfileScreenshotsKt`, `SoulsScreenshotsKt`, `CollectionsScreenshotsKt` and `StoreScreenshotsKt` renders that show a top bar. The bot's commit is the count.

### Device pass, Part 2 (the maintainer, on the Pixel 8)

- The navigation bar in light, dark and the wallpaper colour scheme: four drawings, the selected one the lit twin inside the indicator pill; tap each tab.
- On a large-screen emulator (or the Pixel 8 in landscape if the rail shows): the rail's four items; the souls, collections and glyphs detail placeholders at 48dp.
- Profile's top bar: the slider in the settings action. Souls, Collections and Glyphs: the add action at 22dp in its 48dp target.
- TalkBack across the bar (the label speaks, not the icon) and on the four top-bar actions ("Add soul", "Add collection", "Carve a glyph", "Settings").

---

## Part 3 — Capture, the pebble form, the karma flash (`chore/1315-capture-complete`)

`gh stack add chore/1315-capture-complete`.

### Task 12: The photo rows: ready mark and remove button

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/AttachedPhotoView.kt` (imports, lines 79, 144–161, 174–179)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/ExistingSnapRow.kt` (imports, line 92)

`MediaStateLabel` keeps `iconRes: Int`: it still draws the kept upload and failed tracings (`ic_arrow_up_circle`, `ic_warning`, wave drawables). Its one size becomes INLINE for all three states.

- [ ] **Step 1: The ready state.** In `AttachedPhotoView.kt`, replace

```kotlin
                        iconRes = R.drawable.ic_check_circle,
```

with

```kotlin
                        iconRes = PbblsIcon.CHECK_CIRCLE.drawableRes,
```

- [ ] **Step 2: `MediaStateLabel`'s size.** Replace

```kotlin
/** Icon + caption state line (the iOS `Label` analog). */
```

with

```kotlin
/**
 * Icon + caption state line (the iOS `Label` analog). [iconRes] is a kept
 * tracing (upload, failed) or a pebbles icon's `drawableRes` (ready, saved);
 * all draw at INLINE, the helper's one size (Android icon migration spec D6).
 */
```

and, in the same function, replace

```kotlin
            modifier = Modifier.size(14.dp),
```

with

```kotlin
            modifier = Modifier.size(PbblsIconSize.INLINE.toDp(LocalDensity.current)),
```

- [ ] **Step 3: `RemovePhotoButton`.** Replace

```kotlin
        Icon(
            painter = painterResource(R.drawable.ic_x_circle),
            contentDescription = stringResource(R.string.photo_remove_a11y),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
```

with

```kotlin
        PbblsIconImage(
            icon = PbblsIcon.REMOVE,
            size = PbblsIconSize.TOOLBAR,
            contentDescription = stringResource(R.string.photo_remove_a11y),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
```

(`ic_x_circle` stays: the Record flow's close at `RecordFlowChrome.kt:70` is chrome, spec D1.)

- [ ] **Step 4: Imports in `AttachedPhotoView.kt`.** Add `import androidx.compose.ui.platform.LocalDensity` directly below `import androidx.compose.ui.layout.ContentScale`. Add, directly below `import app.pbbls.android.R`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.toDp
```

(`Icon`, `painterResource`, `size` and `dp` stay: the retry button, the spinner and `MediaStateLabel`.)

- [ ] **Step 5: `ExistingSnapRow.kt`.** Replace

```kotlin
                iconRes = R.drawable.ic_check_circle,
```

with

```kotlin
                iconRes = PbblsIcon.CHECK_CIRCLE.drawableRes,
```

and add `import app.pbbls.android.core.designsystem.PbblsIcon` directly below `import app.pbbls.android.core.data.LocalSnapURLCache`.

- [ ] **Step 6: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/AttachedPhotoView.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/ExistingSnapRow.kt
git commit -m "chore(android): draw the photo rows' ready mark and remove action with pebbles icons"
```

### Task 13: The Capture steps

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/flow/steps/RecordPhotoStep.kt` (imports, lines 105–110, 219–224)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/flow/steps/RecordWhenStep.kt` (imports, lines 86, 102–107, 201, 224)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/flow/steps/RecordCollectionStep.kt` (imports, lines 78–83)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/flow/steps/RecordSuccessStep.kt` (imports, lines 93–98)

- [ ] **Step 1: `RecordPhotoStep.kt`, the empty tile** (lines 105–110, 8-space indent). Replace

```kotlin
        Icon(
            painter = painterResource(R.drawable.ic_image),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
```

with

```kotlin
        PbblsIconImage(
            icon = PbblsIcon.CAMERA_FOCUS,
            size = PbblsIconSize.HERO,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
```

- [ ] **Step 2: `RecordPhotoStep.kt`, attached without a thumbnail** (lines 219–224, 12-space indent). Replace

```kotlin
            Icon(
                painter = painterResource(R.drawable.ic_image),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
```

with

```kotlin
            PbblsIconImage(
                icon = PbblsIcon.CAMERA_FOCUS,
                size = PbblsIconSize.HERO,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
```

Imports: delete `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.Spacing`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`size` and `dp` stay: the spinner at line 159.)

- [ ] **Step 3: `RecordWhenStep.kt`, the date pill.** Replace

```kotlin
            iconRes = R.drawable.ic_calendar,
```

with

```kotlin
            iconRes = PbblsIcon.CALENDAR_DATE.drawableRes,
```

The time pill (`iconRes = R.drawable.ic_pebble_when`) stays: a wave drawable (figure 15).

- [ ] **Step 4: `RecordWhenStep.kt`, "date from photo".** Replace

```kotlin
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = PbblsIcon.SPARKLE,
                    size = PbblsIconSize.INLINE,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
```

(The photo date is `sparkle`, not `karma-dot`: iOS's "Taken from your photo" pick.)

- [ ] **Step 5: `RecordWhenStep.kt`, `WhenPill`.** Replace

```kotlin
/** One large, centred tap target carrying half the answer. */
```

with

```kotlin
/**
 * One large, centred tap target carrying half the answer. [iconRes] is the
 * date's pebbles icon (`PbblsIcon.CALENDAR_DATE.drawableRes`) or the time's kept
 * tracing; both draw at ROW (Android icon migration spec D6).
 */
```

and replace

```kotlin
            modifier = Modifier.size(20.dp),
```

with

```kotlin
            modifier = Modifier.size(PbblsIconSize.ROW.toDp(LocalDensity.current)),
```

Imports in `RecordWhenStep.kt`: delete `import androidx.compose.ui.unit.dp` (no `dp` is left in the file); add `import androidx.compose.ui.platform.LocalDensity` directly below `import androidx.compose.ui.platform.LocalContext`; add, directly above `import app.pbbls.android.core.designsystem.Spacing`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

and directly below `import app.pbbls.android.core.designsystem.Spacing`: `import app.pbbls.android.core.designsystem.toDp` (lowercase sorts after uppercase). (`Icon`, `painterResource` and `size` stay: `WhenPill`.)

- [ ] **Step 6: `RecordCollectionStep.kt`.** Replace

```kotlin
                Icon(
                    painter = painterResource(R.drawable.ic_pebble_collection),
                    contentDescription = null,
                    tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = PbblsIcon.COLLECTION,
                    size = PbblsIconSize.ROW,
                    contentDescription = null,
                    tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
```

Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon`, `import androidx.compose.ui.res.painterResource` and `import androidx.compose.ui.unit.dp` (each had this one use); add, directly above `import app.pbbls.android.core.designsystem.Spacing`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 7: `RecordSuccessStep.kt`.** Replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_sparkle),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
```

with

```kotlin
                    PbblsIconImage(
                        icon = PbblsIcon.KARMA_DOT,
                        size = PbblsIconSize.INLINE,
                        contentDescription = null,
                        tint = colors.primary,
                    )
```

Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.PebblesPrimaryButton`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`dp` stays: lines 71 and 89.)

- [ ] **Step 8: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin)`. Expected: `BUILD SUCCESSFUL`. `ic_image` still has `PebbleForm.kt:278` (Task 14), so it is not deleted here.

- [ ] **Step 9: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/flow/steps
git commit -m "chore(android): draw the capture steps with pebbles icons"
```

### Task 14: The When row and the pebble form's leads; `ic_image` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/WhenRow.kt` (imports, lines 58–63)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/PebbleForm.kt` (imports, lines 277–282, 403–408)
- Delete: `apps/android/app/src/main/res/drawable/ic_image.xml`

- [ ] **Step 1: `WhenRow.kt`.** Replace

```kotlin
            Icon(
                painter = painterResource(R.drawable.ic_pebble_when),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(28.dp),
            )
```

with

```kotlin
            PbblsIconImage(
                icon = PbblsIcon.CALENDAR_DATE,
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = colors.primary,
            )
```

(The form's When row leads with the date, then the time: `calendar-date`, spec D3. `ic_pebble_when` stays at the Capture time pill.) Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon`, `import androidx.compose.ui.res.painterResource` and `import androidx.compose.ui.unit.dp`; add, directly below `import app.pbbls.android.R`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 2: `PebbleForm.kt`, the "Add photo" row.** Replace

```kotlin
        Icon(
            painter = painterResource(R.drawable.ic_image),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(24.dp),
        )
```

with

```kotlin
        PbblsIconImage(
            icon = PbblsIcon.CAMERA,
            size = PbblsIconSize.ROW,
            contentDescription = null,
            tint = colors.primary,
        )
```

- [ ] **Step 3: `PebbleForm.kt`, the collection lead.** Replace

```kotlin
                Icon(
                    painter = painterResource(R.drawable.ic_pebble_collection),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = PbblsIcon.COLLECTION,
                    size = PbblsIconSize.TILE,
                    contentDescription = null,
                    tint = colors.primary,
                )
```

The domain lead (`R.drawable.ic_pebble_domain`, line 360) stays: a wave drawable. Imports: add, directly above `import app.pbbls.android.core.designsystem.PebblesTheme`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`Icon`, `painterResource`, `size` and `dp` stay: the chevron, the domain lead and others.)

- [ ] **Step 4: Delete `ic_image`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_image\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_image.xml
```

Expected: the grep prints nothing (`RecordPhotoStep` moved in Task 13). No lint-baseline entry.

- [ ] **Step 5: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/WhenRow.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/PebbleForm.kt
git commit -m "chore(android): draw the when row and the pebble form's leads with pebbles icons"
```

### Task 15: The selection checks and the karma flash; `ic_check` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/VisibilityChip.kt` (imports, lines 98–103)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/pickers/EmotionPickerSheet.kt` (imports, lines 173–177)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/KarmaEarnedCapsule.kt` (imports, KDoc 98, lines 148–153)
- Delete: `apps/android/app/src/main/res/drawable/ic_check.xml`

- [ ] **Step 1: `VisibilityChip.kt`, the selected grade's check.** Replace

```kotlin
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
```

with

```kotlin
                                PbblsIconImage(
                                    icon = PbblsIcon.CHECKMARK_DOT,
                                    size = PbblsIconSize.INLINE,
                                    contentDescription = null,
                                    tint = colors.onSurface,
                                )
```

The two grade icons (`value.iconRes`, `grade.iconRes`) stay: `Visibility.iconRes` is the wave trio. Imports: add, directly below `import app.pbbls.android.R`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 2: `EmotionPickerSheet.kt`, the selected chip's check.** Replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
```

with

```kotlin
                    PbblsIconImage(
                        icon = PbblsIcon.CHECKMARK_DOT,
                        contentDescription = null,
                        size = FilterChipDefaults.IconSize,
                    )
```

(The chip's own icon token, and no tint: `FilterChip` sets the content colour.) Imports: delete `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly below `import app.pbbls.android.core.data.LocalEmotionPaletteService`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
```

(`size` stays: the palette dot at line 121.)

- [ ] **Step 3: `KarmaEarnedCapsule.kt`.** Replace

```kotlin
            Icon(
                painter = painterResource(R.drawable.ic_sparkle),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
```

with

```kotlin
            PbblsIconImage(
                icon = PbblsIcon.KARMA_DOT,
                size = PbblsIconSize.INLINE,
                contentDescription = null,
                tint = colors.primary,
            )
```

and, in the KDoc above `fun KarmaEarnedCapsule(`, replace

```kotlin
 * the container ladder, not a `surfaceTint` wash — #853) with a sparkle, the
```

with

```kotlin
 * the container ladder, not a `surfaceTint` wash — #853) with the karma dot, the
```

Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.rememberReduceMotion`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 4: Delete `ic_check`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_check\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_check.xml
```

Expected: the grep prints nothing (`\b` keeps `ic_check_circle` out). No lint-baseline entry.

- [ ] **Step 5: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/VisibilityChip.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/pickers/EmotionPickerSheet.kt apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/KarmaEarnedCapsule.kt
git commit -m "chore(android): draw the selection checks and the karma flash with pebbles icons"
```

### Task 16: Part 3 gate, PR, references

- [ ] **Step 1: The Android gate**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation; `BUILD SUCCESSFUL`.

- [ ] **Step 2: Push and PR.** `git fetch origin`, fast-forward any lower part whose bot commit landed (procedure step 3), then:

```bash
gh stack push
gh stack submit --auto --open
gh pr edit chore/1315-capture-complete --title "chore(android): draw capture, the pebble form and the karma flash with pebbles icons" --add-label chore,ui,android,no-lab-note --milestone "M66 · Brand icons" --body-file - <<'EOF'
Part 3 of #1315.

- Capture: the photo step's hero (`camera-focus`, HERO), the date pill (`calendar-date` through `WhenPill`, ROW), "taken from your photo" (`sparkle`, INLINE), the collection rows (`collection`, ROW), the success karma row (`karma-dot`, INLINE).
- Photo rows: ready and saved (`check-circle` through `MediaStateLabel`, now INLINE for its kept upload and failed tracings too), remove (`remove`, TOOLBAR).
- The pebble form: When lead (`calendar-date`, TILE), Add photo (`camera`, ROW), collection lead (`collection`, TILE); the domain lead stays (wave).
- Checks: the visibility menu's selected grade (`checkmark-dot`, INLINE) and the emotion chip (`checkmark-dot`, `FilterChipDefaults.IconSize`); the karma flash (`karma-dot`, INLINE).
- Kept, by design: the time pill (`ic_pebble_when`), upload and failed states, retry, the visibility trio, the domain lead (wave brief), the flow's close (chrome).
- `ic_image.xml` and `ic_check.xml` deleted.

References: `CaptureFlowScreenshotsKt`, `EditPebbleScreenshotsKt`, `CompleteFlowScreenshotsKt`, `PickerScreenshotsKt`, `MediaScreenshotsKt` re-record (about 60).
EOF
```

- [ ] **Step 3: Re-baseline**

```bash
git fetch origin && git log --oneline -1 origin/chore/1315-navigation   # the part below: its bot commit must be in and fast-forwarded first
gh pr edit chore/1315-capture-complete --add-label rebaseline-screenshots
# wait for the bot's "test(android): re-baseline ..." commit on origin/chore/1315-capture-complete, then:
git fetch origin && git merge --ff-only origin/chore/1315-capture-complete   # while chore/1315-capture-complete is checked out
for id in $(gh run list --branch chore/1315-capture-complete --status action_required --json databaseId --jq '.[].databaseId'); do gh api -X POST repos/alexisbohns/pbbls/actions/runs/$id/approve; done
```

Full procedure at the top (steps 1–5); label only once Part 2's bot commit is in. Expected classes: `CaptureFlowScreenshotsKt` (about 24), `EditPebbleScreenshotsKt` (about 17), `CompleteFlowScreenshotsKt` (about 10), `PickerScreenshotsKt` (the emotion picker and the karma flash, about 7), `MediaScreenshotsKt` (2). `_fs2` (200% font) variants show INLINE growing with its text: that is D6, not a regression.

### Device pass, Part 3 (the maintainer, on the Pixel 8)

- Capture (+ on the Path): the photo step empty (48dp `camera-focus` in the dashed tile), with a photo, and attached without a thumbnail; the When step's date pill (`calendar-date`) beside the kept time pill; "taken from your photo" after picking a photo with a capture date; the collection step's rows, selected and not; the success step's karma row.
- The karma flash capsule after a record that earns karma.
- Edit a pebble: the When row (28dp `calendar-date`), Add photo (20dp `camera`), the collection lead, the kept domain lead; the photo row's ready / saved states and the remove action.
- The visibility chip's menu check; the emotion picker's selected chip.
- Once at 200% font scale: "taken from your photo", the photo states, the karma row and flash (INLINE grows with the text).

---

## Part 4 — the path card, the FAB, the pebble menus (`chore/1315-path-card`)

`gh stack add chore/1315-path-card`.

### Task 17: The Complete CTA and the FAB

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/card/BareMomentPage.kt` (imports, lines 305–309)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/components/NewPebbleFab.kt` (imports, lines 45–50)

- [ ] **Step 1: `BareMomentPage.kt`.** Replace

```kotlin
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
```

with

```kotlin
            PbblsIconImage(
                icon = PbblsIcon.PLUS_CIRCLE,
                contentDescription = null,
                size = ButtonDefaults.IconSize,
            )
```

(The button's own icon token; no tint, `Button` sets the content colour.) Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource` (each had this one use); add, directly above `import app.pbbls.android.core.designsystem.PebblesTheme`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
```

- [ ] **Step 2: `NewPebbleFab.kt`.** Replace

```kotlin
        Icon(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = label,
            tint = colors.onPrimary,
            modifier = Modifier.size(24.dp),
        )
```

with

```kotlin
        // The FAB's 24dp icon slot: the generated default (spec D6).
        PbblsIconImage(
            icon = PbblsIcon.PLUS_CIRCLE,
            contentDescription = label,
            tint = colors.onPrimary,
        )
```

Imports: delete `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly below `import app.pbbls.android.R`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
```

(`size` and `dp` stay: the 56dp disc.)

- [ ] **Step 3: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/card/BareMomentPage.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/path/components/NewPebbleFab.kt
git commit -m "chore(android): draw the complete action and the new-pebble fab with the plus stone"
```

### Task 18: The band mark; `ic_pebble_collection` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/card/CollectionBand.kt` (imports, lines 91–96)
- Delete: `apps/android/app/src/main/res/drawable/ic_pebble_collection.xml`

- [ ] **Step 1: The band mark.** Replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_pebble_collection),
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(BAND_MARK),
                    )
```

with

```kotlin
                    // A fixed slot (spec D6): the band is laid out in dp and does not grow with
                    // the font scale, so the mark passes its Dp; 14dp is the one size under the 16 floor.
                    PbblsIconImage(
                        icon = PbblsIcon.COLLECTION,
                        contentDescription = null,
                        size = BAND_MARK,
                        tint = ink,
                    )
```

Imports: delete `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.SpineTypography`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
```

(`size` stays: the knot at line 108.)

- [ ] **Step 2: Delete `ic_pebble_collection`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_pebble_collection\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_pebble_collection.xml
```

Expected: the grep prints nothing (the tab bar, the placeholder, the form, the collection step moved in Parts 2–3). No lint-baseline entry.

- [ ] **Step 3: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/card/CollectionBand.kt
git commit -m "chore(android): draw the held card's band mark with the collection stone"
```

### Task 19: The pebble menus

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/PebbleRow.kt` (imports, lines 114–118)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/spine/SpinePebble.kt` (imports, lines 359–363)

- [ ] **Step 1: `PebbleRow.kt`.** Replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = colors.error,
                    )
```

with

```kotlin
                    PbblsIconImage(
                        icon = PbblsIcon.TRASH,
                        size = PbblsIconSize.ROW,
                        contentDescription = null,
                        tint = colors.error,
                    )
```

Imports: delete `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly below `import app.pbbls.android.R`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`size` and `dp` stay: the 40dp thumbnail. The file's lint-baseline entry, `NonObservableLocale` on `val locale = Locale.getDefault()`, is untouched.)

- [ ] **Step 2: `SpinePebble.kt`.** Replace

```kotlin
                        Icon(
                            painter = painterResource(R.drawable.ic_trash),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
```

with

```kotlin
                        PbblsIconImage(
                            icon = PbblsIcon.TRASH,
                            size = PbblsIconSize.ROW,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
```

Imports: delete `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.PebblesTheme`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 3: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin)`. Expected: `BUILD SUCCESSFUL`. `ic_trash` keeps three references (the souls and collections menus, Part 5; carve clear, Part 6).

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/PebbleRow.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/path/spine/SpinePebble.kt
git commit -m "chore(android): draw the pebble menus' delete with the trash stone"
```

### Task 20: Part 4 gate, PR, references

- [ ] **Step 1: The Android gate**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation; `BUILD SUCCESSFUL`.

- [ ] **Step 2: Push and PR.** `git fetch origin`, fast-forward any lower part whose bot commit landed, then:

```bash
gh stack push
gh stack submit --auto --open
gh pr edit chore/1315-path-card --title "chore(android): draw the path card, the fab and the pebble menus with pebbles icons" --add-label chore,ui,android,no-lab-note --milestone "M66 · Brand icons" --body-file - <<'EOF'
Part 4 of #1315.

- The bare moment page's Complete CTA and the new-pebble FAB: `plus-circle` (M3's `ButtonDefaults.IconSize`; the FAB's 24dp).
- The held card's collection band: `collection` at a fixed 14dp (`BAND_MARK`), the one sub-16 size, commented.
- The path row and Spine pebble menus: `trash` at ROW in the error colour.
- `ic_pebble_collection.xml` deleted.

References: `PebbleCardScreenshotsKt`, `PathScreenScreenshotsKt`, `PebbleDetailScreenshotsKt`, `ListDetailScreenshotsKt` re-record (about 25).
EOF
```

- [ ] **Step 3: Re-baseline**

```bash
git fetch origin && git log --oneline -1 origin/chore/1315-capture-complete   # the part below: its bot commit must be in and fast-forwarded first
gh pr edit chore/1315-path-card --add-label rebaseline-screenshots
# wait for the bot's "test(android): re-baseline ..." commit on origin/chore/1315-path-card, then:
git fetch origin && git merge --ff-only origin/chore/1315-path-card   # while chore/1315-path-card is checked out
for id in $(gh run list --branch chore/1315-path-card --status action_required --json databaseId --jq '.[].databaseId'); do gh api -X POST repos/alexisbohns/pbbls/actions/runs/$id/approve; done
```

Full procedure at the top (steps 1–5); label only once Part 3's bot commit is in. Expected classes: `PebbleCardScreenshotsKt` (about 10), `PathScreenScreenshotsKt` (about 8, the FAB), `PebbleDetailScreenshotsKt` (about 5), `ListDetailScreenshotsKt` (path panes, about 3). The menus are closed in every preview, so the trash change renders nowhere.

### Device pass, Part 4 (the maintainer, on the Pixel 8)

- The Path: the FAB's plus stone inside its 56dp `primary` disc (the iOS lessons list the ring-in-a-disc as owed).
- A held card with collections: the 14dp band mark beside the band name.
- A bare moment's page: the Complete button's icon.
- Long-press a pebble on the Spine and in a list: the delete item's trash in the error colour.

---

## Part 5 — profile, achievements, souls, collections (`chore/1315-profile`)

`gh stack add chore/1315-profile`.

### Task 21: `SurfaceTile(icon:)` and its six callers

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/SurfaceTile.kt` (whole file)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileShortcutsRow.kt` (whole file)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphDetailDrawer.kt` (imports, lines 227–245)

- [ ] **Step 1: `SurfaceTile.kt`.** Replace the whole file with:

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Icon-above-label metadata tile — a stock M3 `Card` on `primaryContainer`
 * (ports iOS `SurfaceTile.swift`). Pass [onClick] and it is the card's own
 * click, with the ripple and button role (the profile shortcuts); without it
 * the tile is display-only (the read page, the glyph drawer). Width comes from
 * the caller's `Modifier.weight(1f)`; content is centered. Set [muted] to
 * render a placeholder tile (e.g. the "No domain" empty state). The [icon]
 * draws at TILE (Android icon migration spec D6).
 */
@Composable
fun SurfaceTile(
    icon: PbblsIcon,
    label: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = PebblesTheme.spacing
    // Placeholder tiles read as disabled content (#853 rulebook: onSurface at 38%).
    val disabled = colors.onSurface.copy(alpha = 0.38f)
    val resolvedIconTint = if (muted) disabled else colors.onPrimaryContainer
    val resolvedLabelColor = if (muted) disabled else colors.onPrimaryContainer
    val cardColors = CardDefaults.cardColors(containerColor = colors.primaryContainer)
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            PbblsIconImage(
                icon = icon,
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = resolvedIconTint,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = resolvedLabelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = cardColors) { content() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = cardColors) { content() }
    }
}
```

(Removed imports: `androidx.compose.foundation.layout.size`, `androidx.compose.material3.Icon`, `androidx.compose.ui.graphics.painter.Painter`. `SurfaceTile` and `PbblsIcon` share the package.)

- [ ] **Step 2: `ProfileShortcutsRow.kt`.** Replace the whole file with:

```kotlin
package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SurfaceTile

/**
 * The Profile shortcut tiles — ports iOS `ProfileShortcutsRow.swift`
 * (Souls · Glyphs · Connections, in that order). Collections are reached from
 * their own section card below, so they get no shortcut tile. Handlers are
 * optional and a tile renders only when its handler is wired (D11 — no dead
 * chrome).
 */
@Composable
fun ProfileShortcutsRow(
    modifier: Modifier = Modifier,
    onOpenSouls: (() -> Unit)? = null,
    onOpenGlyphs: (() -> Unit)? = null,
    onOpenConnections: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
    ) {
        if (onOpenSouls != null) {
            ShortcutTile(
                icon = PbblsIcon.PEOPLE,
                label = stringResource(R.string.souls_title),
                onClick = onOpenSouls,
            )
        }
        if (onOpenGlyphs != null) {
            ShortcutTile(
                icon = PbblsIcon.PEBBLE_DOTTED_GLYPH,
                label = stringResource(R.string.glyphs_title),
                onClick = onOpenGlyphs,
            )
        }
        if (onOpenConnections != null) {
            ShortcutTile(
                icon = PbblsIcon.PEOPLE,
                label = stringResource(R.string.connections_title),
                onClick = onOpenConnections,
            )
        }
    }
}

/** One tappable [SurfaceTile] — the `ProfileShortcutTile` analog (nav lambda instead of NavigationLink). */
@Composable
private fun RowScope.ShortcutTile(
    icon: PbblsIcon,
    label: String,
    onClick: () -> Unit,
) {
    SurfaceTile(
        icon = icon,
        label = label,
        modifier = Modifier.weight(1f),
        onClick = onClick,
    )
}
```

- [ ] **Step 3: The glyph drawer's three tiles.** In `GlyphDetailDrawer.kt`, replace

```kotlin
                iconPainter = painterResource(R.drawable.ic_calendar),
```
```kotlin
                iconPainter = painterResource(R.drawable.ic_alternating_current),
```
```kotlin
                iconPainter = painterResource(R.drawable.ic_person_pair),
```

with, respectively,

```kotlin
                icon = PbblsIcon.CALENDAR_DATE,
```
```kotlin
                icon = PbblsIcon.CHART_DOUBLE_GROW,
```
```kotlin
                icon = PbblsIcon.PEOPLE,
```

(The muted usage tile is `chart-double-grow`, spec D3; `ic_alternating_current` stays at the me/creator swap, line 279.) Add `import app.pbbls.android.core.designsystem.PbblsIcon` directly above `import app.pbbls.android.core.designsystem.PebblesTheme`. (`painterResource` stays: the balance, swap, owned and price marks, Part 6.)

- [ ] **Step 4: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin)`. Expected: `BUILD SUCCESSFUL`; `grep -rn 'iconPainter' apps/android/app/src` prints nothing.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/SurfaceTile.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileShortcutsRow.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphDetailDrawer.kt
git commit -m "chore(android): surface tiles take a pebbles icon"
```

### Task 22: `DataTile(icon:)` and the counters; `ic_calendar` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/DataTile.kt` (whole file)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileCountersRow.kt` (whole file)
- Delete: `apps/android/app/src/main/res/drawable/ic_calendar.xml`

- [ ] **Step 1: `DataTile.kt`.** Replace the whole file with:

```kotlin
package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme

/**
 * Single value/icon/label tile inside the Profile Stats card — ports iOS
 * `DataTile.swift`: large counter number (tabular figures) over a small
 * `primary` icon + `onSurfaceVariant` label. "—" while the value is loading.
 * The icon sits in the label's line, so it draws at INLINE (Android icon
 * migration spec D6; iOS draws it at `tile`).
 */
@Composable
fun DataTile(
    value: Int?,
    icon: PbblsIcon,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
        modifier = modifier,
    ) {
        Text(
            text = value?.toString() ?: "—",
            // The .monospacedDigit() analog — tabular figures via font features.
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PbblsIconImage(
                icon = icon,
                size = PbblsIconSize.INLINE,
                contentDescription = null,
                tint = colors.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: `ProfileCountersRow.kt`.** Replace the whole file with:

```kotlin
package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon

/**
 * Days / Pebbles / Karma counter tiles — ports iOS `ProfileCountersRow.swift`.
 */
@Composable
fun ProfileCountersRow(
    daysPracticed: Int?,
    pebbles: Int?,
    karma: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        DataTile(
            value = daysPracticed,
            icon = PbblsIcon.CALENDAR_DATE,
            label = stringResource(R.string.profile_counter_days),
            modifier = Modifier.weight(1f),
        )
        DataTile(
            value = pebbles,
            icon = PbblsIcon.PEBBLE_ROUND,
            label = stringResource(R.string.profile_counter_pebbles),
            modifier = Modifier.weight(1f),
        )
        DataTile(
            value = karma,
            icon = PbblsIcon.KARMA_DOT,
            label = stringResource(R.string.profile_counter_karma),
            modifier = Modifier.weight(1f),
        )
    }
}
```

- [ ] **Step 3: Delete `ic_calendar`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_calendar\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_calendar.xml
```

Expected: the grep prints nothing (the date pill moved in Task 13, the drawer tile in Task 21, the gallery in Task 4). No lint-baseline entry.

- [ ] **Step 4: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/DataTile.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileCountersRow.kt
git commit -m "chore(android): draw the profile counters with pebbles icons"
```

### Task 23: `achievementFamilyIcon` returns iOS's table; `ic_pebble_emotion` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/AchievementCopy.kt` (imports, lines 84–96)
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/AchievementFamilyIconTest.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/AchievementsScreen.kt` (imports, lines 209–216)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileAchievementsCard.kt` (imports, lines 121–126)
- Delete: `apps/android/app/src/main/res/drawable/ic_pebble_emotion.xml`

- [ ] **Step 1: Write the failing test** `AchievementFamilyIconTest.kt`:

```kotlin
package app.pbbls.android.core.ui

import app.pbbls.android.core.designsystem.PbblsIcon
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The family marks are iOS's `Achievement.familySymbol` table, family by family
 * (Android icon migration spec D4): the cross-surface mirror written as a test.
 */
class AchievementFamilyIconTest {
    @Test
    fun `each family draws what iOS draws for it`() {
        val ios =
            mapOf(
                "pebble_count" to PbblsIcon.STACK,
                "emotion_first" to PbblsIcon.HEART,
                "first_collection" to PbblsIcon.COLLECTION,
                "first_glyph" to PbblsIcon.PEBBLE_DOTTED_GLYPH,
                "first_soul" to PbblsIcon.PEOPLE,
                "first_moment" to PbblsIcon.PEBBLE_FLAT,
                "first_pebble" to PbblsIcon.PEBBLE_ROUND,
                "first_five" to PbblsIcon.STACK_LIT_3,
                "glyph_count" to PbblsIcon.PEBBLE_DOTTED_GLYPH,
            )
        ios.forEach { (family, icon) -> assertEquals(family, icon, achievementFamilyIcon(family)) }
    }

    @Test
    fun `families with no drawing of their own get the glowing pebble`() {
        listOf("domain_first", "glyph_sales", "a_family_from_the_future").forEach { family ->
            assertEquals(family, PbblsIcon.PEBBLE_GLOW_MD, achievementFamilyIcon(family))
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `(cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.core.ui.AchievementFamilyIconTest')`
Expected: FAIL, both tests: `pebble_count expected:<STACK> but was:<2131…>` (the function still returns a drawable id).

- [ ] **Step 3: Implement.** In `AchievementCopy.kt`, replace

```kotlin
/** Icon per family from the existing asset set; unknown families get the trophy. */
fun achievementFamilyIcon(family: String): Int =
    when (family) {
        "pebble_count" -> R.drawable.ic_fossil_shell
        "emotion_first" -> R.drawable.ic_pebble_emotion
        "domain_first" -> R.drawable.ic_pebble_domain
        "first_collection" -> R.drawable.ic_stack
        "first_glyph" -> R.drawable.ic_scribble
        "first_soul" -> R.drawable.ic_person_pair
        "glyph_count" -> R.drawable.ic_scribble
        "glyph_sales" -> R.drawable.ic_sparkle
        else -> R.drawable.ic_trophy
    }
```

with

```kotlin
/**
 * The mark per family: exactly iOS `Achievement.familySymbol` (Android icon
 * migration spec D4). Families with no drawing of their own (`domain_first`,
 * `glyph_sales`) and unknown ones get the glowing pebble.
 */
fun achievementFamilyIcon(family: String): PbblsIcon =
    when (family) {
        "pebble_count" -> PbblsIcon.STACK
        "emotion_first" -> PbblsIcon.HEART
        "first_collection" -> PbblsIcon.COLLECTION
        "first_glyph" -> PbblsIcon.PEBBLE_DOTTED_GLYPH
        "first_soul" -> PbblsIcon.PEOPLE
        "first_moment" -> PbblsIcon.PEBBLE_FLAT
        "first_pebble" -> PbblsIcon.PEBBLE_ROUND
        "first_five" -> PbblsIcon.STACK_LIT_3
        "glyph_count" -> PbblsIcon.PEBBLE_DOTTED_GLYPH
        else -> PbblsIcon.PEBBLE_GLOW_MD
    }
```

and add `import app.pbbls.android.core.designsystem.PbblsIcon` directly below `import app.pbbls.android.core.data.LocalReferenceDataService` (`core/ui` reading `core/designsystem`: downward).

- [ ] **Step 4: The grid's family mark.** In `AchievementsScreen.kt`, replace

```kotlin
                Icon(
                    painter = painterResource(achievementFamilyIcon(record.family)),
                    contentDescription = null,
                    // Locked: a faint family mark. The lock and the caption below
                    // carry the state legibly, so the mark itself needn't clear 3:1.
                    tint = if (isUnlocked) colors.primary else colors.outlineVariant,
                    modifier = Modifier.size(PebblesIconToken.LARGE.size),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = achievementFamilyIcon(record.family),
                    size = PbblsIconSize.TILE,
                    contentDescription = null,
                    // Locked: a faint family mark. The lock and the caption below
                    // carry the state legibly, so the mark itself needn't clear 3:1.
                    tint = if (isUnlocked) colors.primary else colors.outlineVariant,
                )
```

Add, directly above `import app.pbbls.android.core.designsystem.PebblesIconToken`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`Icon`, `painterResource`, `size` and `PebblesIconToken` stay: the back arrow and the lock, `MEDIUM`, until Part 7.)

- [ ] **Step 5: The shelf badge.** In `ProfileAchievementsCard.kt`, replace

```kotlin
        Icon(
            painter = painterResource(achievementFamilyIcon(record.family)),
            contentDescription = achievementTitle(record),
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
```

with

```kotlin
        // A fixed slot (spec D6): the 32dp disc does not grow with the font
        // scale, so the mark keeps a 16dp side instead of following INLINE.
        PbblsIconImage(
            icon = achievementFamilyIcon(record.family),
            contentDescription = achievementTitle(record),
            size = 16.dp,
            tint = colors.onPrimaryContainer,
        )
```

and add `import app.pbbls.android.core.designsystem.PbblsIconImage` directly above `import app.pbbls.android.core.designsystem.PebblesIconToken`.

- [ ] **Step 6: Run the test again.** Same command as Step 2. Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 7: Delete `ic_pebble_emotion`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_pebble_emotion\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_pebble_emotion.xml
```

Expected: the grep prints nothing (its one reference was the `emotion_first` branch). `ic_pebble_domain` keeps `PebbleForm.kt:360`; `ic_fossil_shell`, `ic_stack`, `ic_scribble`, `ic_person_pair`, `ic_sparkle`, `ic_trophy` keep other references. No lint-baseline entry.

- [ ] **Step 8: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/AchievementCopy.kt apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/AchievementFamilyIconTest.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/AchievementsScreen.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileAchievementsCard.kt
git commit -m "chore(android): draw the achievement family marks from ios's table"
```

### Task 24: The achievements card, the celebration, the Lab card; `ic_trophy` and `ic_lightbulb` go

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileAchievementsCard.kt` (imports, lines 65–70)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/AchievementMomentOverlay.kt` (imports, lines 173–178)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileLabCard.kt` (imports, KDoc 24–25, lines 45–50)
- Delete: `apps/android/app/src/main/res/drawable/ic_trophy.xml`, `apps/android/app/src/main/res/drawable/ic_lightbulb.xml`

- [ ] **Step 1: The achievements card lead.** In `ProfileAchievementsCard.kt`, replace

```kotlin
            Icon(
                painter = painterResource(R.drawable.ic_trophy),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(PebblesIconToken.LARGE.size),
            )
```

with

```kotlin
            PbblsIconImage(
                icon = PbblsIcon.PEBBLE_GLOW_MD,
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = colors.primary,
            )
```

Imports: add `import app.pbbls.android.core.designsystem.PbblsIcon` directly above `import app.pbbls.android.core.designsystem.PbblsIconImage` (added in Task 23), and `import app.pbbls.android.core.designsystem.PbblsIconSize` directly below `PbblsIconImage`. (`Icon`, `painterResource`, `size` and `PebblesIconToken` stay: the chevron, `MEDIUM`, until Part 7.)

- [ ] **Step 2: The celebration.** In `AchievementMomentOverlay.kt`, replace

```kotlin
                Icon(
                    painter = painterResource(R.drawable.ic_trophy),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = PbblsIcon.PEBBLE_GLOW_LG,
                    size = PbblsIconSize.HERO,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
```

(48 inside the 88dp disc; the celebration is `pebble-glow-lg` by site, spec D3.) Imports: delete `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.PebblesPrimaryButton`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`size` and `dp` stay: the 88dp disc and spacing.)

- [ ] **Step 3: The Lab card.** In `ProfileLabCard.kt`, replace

```kotlin
 * The Profile "Lab" card — ports iOS `ProfileLabCard.swift`: lightbulb in
 * `primary`, "Lab" + "News & community", `onSurfaceVariant` chevron, stock `OutlinedCard`.
```

with

```kotlin
 * The Profile "Lab" card — ports iOS `ProfileLabCard.swift`: the sparkle in
 * `primary` (iOS's pick for its lightbulb), "Lab" + "News & community",
 * `onSurfaceVariant` chevron, stock `OutlinedCard`.
```

and replace

```kotlin
            Icon(
                painter = painterResource(R.drawable.ic_lightbulb),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(PebblesIconToken.LARGE.size),
            )
```

with

```kotlin
            PbblsIconImage(
                icon = PbblsIcon.SPARKLE,
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = colors.primary,
            )
```

Imports: add, directly above `import app.pbbls.android.core.designsystem.PebblesIconToken`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`Icon`, `painterResource`, `size` and `PebblesIconToken` stay: the chevron.)

- [ ] **Step 4: Delete the two drawables.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)(ic_trophy|ic_lightbulb)\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_trophy.xml apps/android/app/src/main/res/drawable/ic_lightbulb.xml
```

Expected: the grep prints nothing (the family default moved in Task 23). No lint-baseline entry for either.

- [ ] **Step 5: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileAchievementsCard.kt apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/AchievementMomentOverlay.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileLabCard.kt
git commit -m "chore(android): draw the achievements card, the celebration and the lab card with pebbles icons"
```

### Task 25: The collection cards; `ic_stack` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileCollectionCard.kt` (imports, lines 83–88)
- Delete: `apps/android/app/src/main/res/drawable/ic_stack.xml`

- [ ] **Step 1: The card's mark.** Replace

```kotlin
            PebblesIcon(
                painter = painterResource(if (collection != null) R.drawable.ic_stack else R.drawable.ic_plus),
                token = PebblesIconToken.SMALL,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
            )
```

with

```kotlin
            PbblsIconImage(
                icon = if (collection != null) PbblsIcon.COLLECTION else PbblsIcon.PLUS_CIRCLE,
                size = PbblsIconSize.ROW,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
            )
```

Imports: delete `import androidx.compose.ui.res.painterResource`, `import app.pbbls.android.core.designsystem.PebblesIcon` and `import app.pbbls.android.core.designsystem.PebblesIconToken`; add, directly above `import app.pbbls.android.core.designsystem.PebblesTheme`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 2: Delete `ic_stack`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_stack\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_stack.xml
```

Expected: the grep prints nothing (the Path tab moved in Task 7, `first_collection` in Task 23). No lint-baseline entry.

- [ ] **Step 3: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileCollectionCard.kt
git commit -m "chore(android): draw the profile collection cards with pebbles icons"
```

### Task 26: The assiduity grid and the soul count; `ic_fossil_shell` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/AssiduityGrid.kt` (whole file)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/SoulItem.kt` (imports, KDoc 40, lines 68–71, 106–111)
- Delete: `apps/android/app/src/main/res/drawable/ic_fossil_shell.xml`

- [ ] **Step 1: `AssiduityGrid.kt`.** Replace the whole file with (the shapes are iOS `AssiduityGrid.swift`'s: a filled `primary` dot for an active day, a 1-unit `surfaceContainerHighest` ring for the others):

```kotlin
package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/**
 * 7-column grid of the last 28 days — ports iOS `AssiduityGrid.swift`. A 7dp
 * cell is too small for any icon (Android icon migration spec D4), so an
 * active day is a filled `primary` dot and a day without a pebble a 1dp
 * `surfaceContainerHighest` ring, iOS's two shapes. One combined a11y label;
 * the cells are decorative.
 */
@Composable
fun AssiduityGrid(
    data: List<Boolean>,
    modifier: Modifier = Modifier,
    columns: Int = 7,
    cellSize: Dp = 7.dp,
    cellSpacing: Dp = 4.dp,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.profile_assiduity_a11y)
    Column(
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        chunkAssiduity(data, columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                row.forEach { active ->
                    Box(
                        modifier =
                            if (active) {
                                Modifier.size(cellSize).background(colors.primary, CircleShape)
                            } else {
                                Modifier.size(cellSize).border(1.dp, colors.surfaceContainerHighest, CircleShape)
                            },
                    )
                }
            }
        }
    }
}
```

(`chunkAssiduity` lives in `ChunkAssiduity.kt`, same package, unchanged. The spec's D4 sentence says `outlineVariant` for the inactive cell; iOS's code draws the ring, see Deviations.)

- [ ] **Step 2: `SoulItem.kt`, the count line.** Replace

```kotlin
 * fossil-shell + pebble count line. [onLongPress] backs the list's delete
```

with

```kotlin
 * pebble + count line. [onLongPress] backs the list's delete
```

replace

```kotlin
    val fossilColor = colors.primaryFixed
```

with

```kotlin
    val countIconColor = colors.primaryFixed
```

and replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_fossil_shell),
                        contentDescription = null,
                        tint = fossilColor,
                        modifier = Modifier.size(11.dp),
                    )
```

with

```kotlin
                    PbblsIconImage(
                        icon = PbblsIcon.PEBBLE_ROUND,
                        size = PbblsIconSize.INLINE,
                        contentDescription = null,
                        tint = countIconColor,
                    )
```

(11 → 16, the floor; the count text stays `labelSmall`, M3's smallest style.) Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.PebblesTheme`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`dp` stays: `side: Dp = 96.dp`; `R` stays: `create_soul_add`.)

- [ ] **Step 3: Delete `ic_fossil_shell`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_fossil_shell\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_fossil_shell.xml
```

Expected: the grep prints nothing (the counters moved in Task 22, `pebble_count` in Task 23). `ic_alternating_current` keeps `GlyphDetailDrawer.kt:279`. No lint-baseline entry.

- [ ] **Step 4: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/AssiduityGrid.kt apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/SoulItem.kt
git commit -m "chore(android): draw the assiduity days as ios's dots and the soul count with a pebble"
```

### Task 27: The souls and collections menus

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulsListScreen.kt` (lines 235–239)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/CollectionsListScreen.kt` (lines 284–288)

- [ ] **Step 1: `SoulsListScreen.kt`.** Replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
```

with

```kotlin
                    PbblsIconImage(
                        icon = PbblsIcon.TRASH,
                        size = PbblsIconSize.ROW,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
```

(The three imports are in place since Task 9; `Icon` and `painterResource` stay for the back arrow.)

- [ ] **Step 2: `CollectionsListScreen.kt`.** Replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = colors.error,
                    )
```

with

```kotlin
                    PbblsIconImage(
                        icon = PbblsIcon.TRASH,
                        size = PbblsIconSize.ROW,
                        contentDescription = null,
                        tint = colors.error,
                    )
```

(Imports in place since Task 9.)

- [ ] **Step 3: Build.** `(cd apps/android && ./gradlew :app:compileDebugKotlin)`. Expected: `BUILD SUCCESSFUL`. `ic_trash` keeps the carve clear (Part 6).

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulsListScreen.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/CollectionsListScreen.kt
git commit -m "chore(android): draw the souls and collections menus' delete with the trash stone"
```

### Task 28: Part 5 gate, PR, references

- [ ] **Step 1: The Android gate**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation; `BUILD SUCCESSFUL`, `AchievementFamilyIconTest` among the passes.

- [ ] **Step 2: Push and PR.** `git fetch origin`, fast-forward any lower part whose bot commit landed, then:

```bash
gh stack push
gh stack submit --auto --open
gh pr edit chore/1315-profile --title "chore(android): draw profile, achievements, souls and collections with pebbles icons" --add-label chore,ui,android,no-lab-note --milestone "M66 · Brand icons" --body-file - <<'EOF'
Part 5 of #1315.

- `SurfaceTile(icon:)` (TILE) with its six callers: the three Profile shortcuts (`people`, `pebble-dotted-glyph`, `people`) and the glyph drawer's three tiles (`calendar-date`, `chart-double-grow`, `people`).
- `DataTile(icon:)` at INLINE (its icon sits in the label's line; iOS draws `tile`, called out for the device pass) and the counters (`calendar-date`, `pebble-round`, `karma-dot`).
- `achievementFamilyIcon` returns iOS's `familySymbol` table (adds `first_moment`, `first_pebble`, `first_five`; `domain_first` and `glyph_sales` fall to `pebble-glow-md`); `AchievementFamilyIconTest`. The grid's mark at TILE, the shelf badge at a fixed 16dp in its 32dp disc.
- The achievements card (`pebble-glow-md`), the celebration (`pebble-glow-lg`, HERO), the Lab card (`sparkle`), the collection cards (`collection` / `plus-circle`), the soul count (`pebble-round`, INLINE), the souls and collections menus (`trash`).
- `AssiduityGrid` draws iOS's shapes: a filled `primary` dot, a `surfaceContainerHighest` ring.
- Deleted: `ic_calendar`, `ic_pebble_emotion`, `ic_trophy`, `ic_lightbulb`, `ic_stack`, `ic_fossil_shell`.

References: `ProfileScreenshotsKt`, `AchievementsScreenshotsKt`, `SoulsScreenshotsKt`, `StoreScreenshotsKt`, `PickerScreenshotsKt`, `ListDetailScreenshotsKt` re-record (about 30).
EOF
```

- [ ] **Step 3: Re-baseline**

```bash
git fetch origin && git log --oneline -1 origin/chore/1315-path-card   # the part below: its bot commit must be in and fast-forwarded first
gh pr edit chore/1315-profile --add-label rebaseline-screenshots
# wait for the bot's "test(android): re-baseline ..." commit on origin/chore/1315-profile, then:
git fetch origin && git merge --ff-only origin/chore/1315-profile   # while chore/1315-profile is checked out
for id in $(gh run list --branch chore/1315-profile --status action_required --json databaseId --jq '.[].databaseId'); do gh api -X POST repos/alexisbohns/pbbls/actions/runs/$id/approve; done
```

Full procedure at the top (steps 1–5); label only once Part 4's bot commit is in. Expected classes: `ProfileScreenshotsKt` (about 5), `AchievementsScreenshotsKt` (about 5), `SoulsScreenshotsKt` (3, the soul count), `StoreScreenshotsKt` (the drawer, 2), `PickerScreenshotsKt` (the soul picker, 3), `ListDetailScreenshotsKt` (souls and collections panes, about 13).

### Device pass, Part 5 (the maintainer, on the Pixel 8)

- Profile: the three shortcut tiles (28dp); the counters (16dp beside `bodyMedium`; iOS draws 28 here, say which reads right); the assiduity grid's dots and rings on the stats card (the ring is iOS's `surfaceContainerHighest`: does it read on Android's card?); the Lab card's sparkle; the achievements card lead and its shelf badges; the collection cards, filled and "New collection".
- Achievements: the family marks, unlocked and locked (faint mark + the kept lock).
- The achievement celebration, if one unlocks during ordinary use (nothing in this pass may write to the account to provoke it).
- Souls: the pebble beside each soul's count. Long-press a soul and a collection: the delete item's trash.
- A glyph's drawer: the three tiles (date, usage "Soon", owners "Soon").
- Once at 200% font scale: the counters and the soul counts.

---

## Part 6 — glyphs and the Lab; the lint goes live (`chore/1315-glyph-lab`)

`gh stack add chore/1315-glyph-lab`.

### Task 29: The glyph cells; `ic_scribble` and `ic_plus` go

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/GlyphView.kt` (imports, lines 34–35, 45, 91–95, 106–120)
- Delete: `apps/android/app/src/main/res/drawable/ic_scribble.xml`, `apps/android/app/src/main/res/drawable/ic_plus.xml`

- [ ] **Step 1: The case comments.** Replace

```kotlin
    CARVE, // dashed 2dp outlineVariant; scribble in onSurfaceVariant
    CREATE, // dashed 2dp outlineVariant; plus in outline
```

with

```kotlin
    CARVE, // dashed 2dp outlineVariant; the dotted-glyph mark in onSurfaceVariant
    CREATE, // dashed 2dp outlineVariant; the plus stone in outline
```

and replace

```kotlin
 * (scribble for [GlyphViewCase.CARVE], plus for [GlyphViewCase.CREATE]).
```

with

```kotlin
 * (`pebble-dotted-glyph` for [GlyphViewCase.CARVE], `plus-circle` for [GlyphViewCase.CREATE]).
```

- [ ] **Step 2: The cases.** Replace

```kotlin
            GlyphViewCase.CARVE ->
                PlaceholderIcon(R.drawable.ic_scribble, side, tint = colors.onSurfaceVariant)
            GlyphViewCase.CREATE ->
                PlaceholderIcon(R.drawable.ic_plus, side, tint = colors.outline)
```

with

```kotlin
            GlyphViewCase.CARVE ->
                PlaceholderIcon(PbblsIcon.PEBBLE_DOTTED_GLYPH, side, tint = colors.onSurfaceVariant)
            GlyphViewCase.CREATE ->
                PlaceholderIcon(PbblsIcon.PLUS_CIRCLE, side, tint = colors.outline)
```

- [ ] **Step 3: `PlaceholderIcon`.** Replace

```kotlin
@Composable
private fun PlaceholderIcon(
    resId: Int,
    side: Dp,
    tint: Color,
) {
    // iOS sizes the SF Symbol at max(side * 0.4, 18pt).
    val iconSide = if (side * 0.4f > 18.dp) side * 0.4f else 18.dp
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(iconSide),
    )
}
```

with

```kotlin
@Composable
private fun PlaceholderIcon(
    icon: PbblsIcon,
    side: Dp,
    tint: Color,
) {
    // A fixed slot (spec D6): the cell is a dp square that does not follow the
    // font scale, so the mark takes iOS's max(side * 0.4, 18) as a Dp.
    val iconSide = if (side * 0.4f > 18.dp) side * 0.4f else 18.dp
    PbblsIconImage(icon = icon, contentDescription = null, size = iconSide, tint = tint)
}
```

- [ ] **Step 4: Imports.** Delete `import androidx.compose.material3.Icon`, `import androidx.compose.ui.res.painterResource` and `import app.pbbls.android.R` (its two uses were the drawables). Add, directly above `import app.pbbls.android.core.model.GlyphStroke`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
```

(`size` stays: `.size(side)` on the cell.)

- [ ] **Step 5: Delete the two drawables.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)(ic_scribble|ic_plus)\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_scribble.xml apps/android/app/src/main/res/drawable/ic_plus.xml
```

Expected: the grep prints nothing (`ic_scribble`: placeholder Task 8, shortcut Task 21, families Task 23; `ic_plus`: pluses Task 9, CTA and FAB Task 17, collection card Task 25). No lint-baseline entry.

- [ ] **Step 6: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/GlyphView.kt
git commit -m "chore(android): draw the glyph cells' carve and create marks with pebbles icons"
```

### Task 30: `GlyphTab.icon`; `ic_person` and `ic_person_pair` go

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphTabBar.kt` (imports, lines 29–37, 110–114)
- Delete: `apps/android/app/src/main/res/drawable/ic_person.xml`, `apps/android/app/src/main/res/drawable/ic_person_pair.xml`

- [ ] **Step 1: The enum.** Replace

```kotlin
/** The three store tabs — ports iOS `GlyphTab` (rawValues mine/owned/commu). */
enum class GlyphTab(
    val labelRes: Int,
    val iconRes: Int,
) {
    MINE(R.string.glyph_tab_mine, R.drawable.ic_person),
    OWNED(R.string.glyph_tab_owned, R.drawable.ic_check_circle),
    COMMU(R.string.glyph_tab_commu, R.drawable.ic_person_pair),
}
```

with

```kotlin
/** The three store tabs — ports iOS `GlyphTab` (rawValues mine/owned/commu) and its icons. */
enum class GlyphTab(
    val labelRes: Int,
    val icon: PbblsIcon,
) {
    MINE(R.string.glyph_tab_mine, PbblsIcon.PERSON),
    OWNED(R.string.glyph_tab_owned, PbblsIcon.CHECK_CIRCLE_DOT),
    COMMU(R.string.glyph_tab_commu, PbblsIcon.PEOPLE),
}
```

- [ ] **Step 2: The toggle.** Replace

```kotlin
        Icon(
            painter = painterResource(tab.iconRes),
            contentDescription = if (showLabel) null else label,
            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
        )
```

with

```kotlin
        PbblsIconImage(
            icon = tab.icon,
            contentDescription = if (showLabel) null else label,
            size = ToggleButtonDefaults.IconSize,
        )
```

(The toggle's own icon token; no tint, `ToggleButton` sets the content colour.)

- [ ] **Step 3: Imports.** Delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.Spacing`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
```

- [ ] **Step 4: Delete the two drawables.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)(ic_person|ic_person_pair)\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_person.xml apps/android/app/src/main/res/drawable/ic_person_pair.xml
```

Expected: the grep prints nothing (the You tab moved in Task 7; the shortcuts, the drawer tile and `first_soul` in Tasks 21 and 23). `ic_check_circle` keeps the drawer and the timeline. No lint-baseline entry.

- [ ] **Step 5: Lint and tests.** `(cd apps/android && ./gradlew lint :app:testDebugUnitTest --tests 'app.pbbls.android.features.glyph.*')`. Expected: `BUILD SUCCESSFUL` (`GlyphsListViewModelTest` and `GlyphPickerViewModelTest` use `GlyphTab` and still pass).

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphTabBar.kt
git commit -m "chore(android): draw the glyph store tabs with pebbles icons"
```

### Task 31: Glyph prices, balance and the owned mark; `ic_sparkle` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphDetailDrawer.kt` (imports, lines 265–270, 372–377, 379–384)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/store/GlyphsListScreen.kt` (lines 298–303)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/picker/GlyphPickerContent.kt` (imports, lines 300–305)
- Delete: `apps/android/app/src/main/res/drawable/ic_sparkle.xml`

- [ ] **Step 1: The drawer's balance.** In `GlyphDetailDrawer.kt`, replace

```kotlin
                    Icon(
                        painter = painterResource(R.drawable.ic_sparkle),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(13.dp),
                    )
```

with

```kotlin
                    PbblsIconImage(
                        icon = PbblsIcon.KARMA_DOT,
                        size = PbblsIconSize.INLINE,
                        contentDescription = null,
                        tint = colors.primary,
                    )
```

The swap between "me" and the creator (`R.drawable.ic_alternating_current`, 16dp) stays: a wave drawable (figure 2).

- [ ] **Step 2: The drawer's owned mark and price.** Replace

```kotlin
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = PbblsIcon.CHECK_CIRCLE_DOT,
                    size = PbblsIconSize.ROW,
                    contentDescription = null,
                    tint = colors.primary,
                )
            } else {
                PbblsIconImage(
                    icon = PbblsIcon.KARMA_DOT,
                    size = PbblsIconSize.INLINE,
                    contentDescription = null,
                    tint = colors.primary,
                )
```

Imports in `GlyphDetailDrawer.kt`: add, directly below `import app.pbbls.android.core.designsystem.PbblsIcon` (Task 21):

```kotlin
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

(`Icon`, `painterResource`, `size` and `dp` stay: the swap. The file's lint-baseline entry, on `val locale = Locale.getDefault()`, is untouched.)

- [ ] **Step 3: The store grid's price.** In `GlyphsListScreen.kt`, replace

```kotlin
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = PbblsIcon.KARMA_DOT,
                    size = PbblsIconSize.INLINE,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
```

(Imports in place since Task 9; `Icon` and `painterResource` stay for the back arrow.)

- [ ] **Step 4: The picker's price.** In `GlyphPickerContent.kt`, replace the same block

```kotlin
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                )
```

with

```kotlin
                PbblsIconImage(
                    icon = PbblsIcon.KARMA_DOT,
                    size = PbblsIconSize.INLINE,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
```

Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource` (each had this one use); add, directly below `import app.pbbls.android.core.common.ObserveUiEffects`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 5: Delete `ic_sparkle`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_sparkle\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_sparkle.xml
```

Expected: the grep prints nothing (Capture, success and the flash in Part 3; counters and `glyph_sales` in Part 5). No lint-baseline entry.

- [ ] **Step 6: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph
git commit -m "chore(android): draw glyph prices, the balance and the owned mark with pebbles icons"
```

### Task 32: The carve pills; `ic_trash` goes

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/carve/GlyphCarveScreen.kt` (imports, lines 187, 315–318, 342)
- Delete: `apps/android/app/src/main/res/drawable/ic_trash.xml`

`CarvePillButton` keeps `iconRes: Int`: the undo pill draws the kept `ic_undo` (chrome).

- [ ] **Step 1: The clear pill.** Replace

```kotlin
                    iconRes = R.drawable.ic_trash,
```

with

```kotlin
                    iconRes = PbblsIcon.TRASH.drawableRes,
```

- [ ] **Step 2: The pill's size.** Replace

```kotlin
 * Undo/Clear pill — `onPrimaryContainer` label + icon on a `primaryContainer`
 * capsule (iOS chrome); disabled content drops to the 38% `onSurface` ink.
```

with

```kotlin
 * Undo/Clear pill — `onPrimaryContainer` label + icon on a `primaryContainer`
 * capsule (iOS chrome); disabled content drops to the 38% `onSurface` ink.
 * [iconRes] is the kept undo tracing or `PbblsIcon.TRASH.drawableRes`; both
 * draw at ROW (Android icon migration spec D6).
```

and, inside `CarvePillButton`, replace

```kotlin
            modifier = Modifier.size(16.dp),
```

with

```kotlin
            modifier = Modifier.size(PbblsIconSize.ROW.toDp(LocalDensity.current)),
```

(`Modifier.size(16.dp)` occurs once in the file.) Imports: add, directly above `import app.pbbls.android.core.designsystem.PebblesScreen`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconSize
```

and directly below `import app.pbbls.android.core.designsystem.PebblesTopBarTextButton`: `import app.pbbls.android.core.designsystem.toDp`. (`LocalDensity` is already imported, line 46.)

- [ ] **Step 3: Delete `ic_trash`.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)ic_trash\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_trash.xml
```

Expected: the grep prints nothing (the four menus moved in Parts 4 and 5). No lint-baseline entry.

- [ ] **Step 4: Lint.** `(cd apps/android && ./gradlew lint)`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/carve/GlyphCarveScreen.kt
git commit -m "chore(android): draw the carve clear pill with the trash stone"
```

### Task 33: The Lab, the last four drawables, and `migrated: android` (the lint goes live)

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/lab/components/LogTimeline.kt` (imports, lines 77–82, 100–105)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/lab/components/FeaturedCommunityCard.kt` (imports, KDoc 22, lines 41–46)
- Delete: `apps/android/app/src/main/res/drawable/ic_check_circle.xml`, `ic_circle_dashed.xml`, `ic_circle_inset_filled.xml`, `ic_chat_bubbles.xml` (same folder)
- Modify: `packages/icons/icons.json` (24 lines: the `migrated` line of the 24 rows Android draws)

One commit: the marker flips in the commit that deletes the last replaced drawable, so `lint:replaces` goes live with the code it polices.

- [ ] **Step 1: `LogTimeline.kt`, the node icon.** Replace

```kotlin
    val iconRes =
        when (mode) {
            LogTimelineMode.CHANGELOG -> R.drawable.ic_check_circle
            LogTimelineMode.IN_PROGRESS -> R.drawable.ic_circle_inset_filled
            LogTimelineMode.BACKLOG -> R.drawable.ic_circle_dashed
        }
```

with

```kotlin
    // Shipped is done, in progress a lit pebble, the backlog a plain one (spec D4).
    val icon =
        when (mode) {
            LogTimelineMode.CHANGELOG -> PbblsIcon.CHECK_CIRCLE
            LogTimelineMode.IN_PROGRESS -> PbblsIcon.PEBBLE_ROUND_LIT_1
            LogTimelineMode.BACKLOG -> PbblsIcon.PEBBLE_ROUND
        }
```

and replace

```kotlin
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
```

with

```kotlin
            // A fixed slot (spec D6): the node column is 16dp wide whatever the
            // font scale, so the node fills it at a fixed 16dp rather than INLINE.
            PbblsIconImage(icon = icon, contentDescription = null, size = 16.dp, tint = iconTint)
```

Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon`, `import androidx.compose.ui.res.painterResource` and `import app.pbbls.android.R` (its three uses were these drawables); add, directly above `import app.pbbls.android.core.model.Log`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
```

(The file's lint-baseline entry, on `val locale = Locale.getDefault()`, is untouched.)

- [ ] **Step 2: `FeaturedCommunityCard.kt`.** Replace

```kotlin
 * The WhatsApp community card — ports iOS `FeaturedCommunityCard`: chat
 * bubbles + copy, then a full-width primary button. Always the Lab's
```

with

```kotlin
 * The WhatsApp community card — ports iOS `FeaturedCommunityCard`: the
 * people mark + copy, then a full-width primary button. Always the Lab's
```

and replace

```kotlin
            Icon(
                painter = painterResource(R.drawable.ic_chat_bubbles),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp),
            )
```

with

```kotlin
            PbblsIconImage(
                icon = PbblsIcon.PEOPLE,
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = colors.primary,
            )
```

Imports: delete `import androidx.compose.foundation.layout.size`, `import androidx.compose.material3.Icon` and `import androidx.compose.ui.res.painterResource`; add, directly above `import app.pbbls.android.core.designsystem.PebblesPrimaryButton`:

```kotlin
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
```

- [ ] **Step 3: Delete the last four.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)(ic_check_circle|ic_circle_dashed|ic_circle_inset_filled|ic_chat_bubbles)\b' apps/android/app/src
git rm apps/android/app/src/main/res/drawable/ic_check_circle.xml apps/android/app/src/main/res/drawable/ic_circle_dashed.xml apps/android/app/src/main/res/drawable/ic_circle_inset_filled.xml apps/android/app/src/main/res/drawable/ic_chat_bubbles.xml
```

Expected: the grep prints nothing (`ic_check_circle`: photo rows Task 12, store tab Task 30, drawer Task 31). No lint-baseline entry.

- [ ] **Step 4: Every replaced drawable is gone.** From the worktree root:

```bash
grep -rnE '(R\.drawable\.|@drawable/)(ic_check|ic_check_circle|ic_circle_dashed|ic_circle_inset_filled|ic_fossil_shell|ic_plus|ic_image|ic_calendar|ic_pebble_emotion|ic_pebble_collection|ic_stack|ic_person_pair|ic_person|ic_chat_bubbles|ic_gear|ic_lightbulb|ic_scribble|ic_sparkle|ic_trash|ic_trophy)\b' apps/android/app/src
ls apps/android/app/src/main/res/drawable | grep -v '^ic_pbbls_' | wc -l
```

Expected: the grep prints nothing (every source set, wider than the lint); the count is `22` (7 chrome, 11 wave, 4 brand and launcher marks).

- [ ] **Step 5: Flip `migrated`.** The 24 rows Android draws are `checkmark-dot`, `check-circle`, `check-circle-dot`, `remove`, `plus-circle`, `camera`, `camera-focus`, `path`, `people`, `collection`, `person`, `stack`, `slider`, `pebble-dotted-glyph`, `pebble-round`, `pebble-flat`, `karma-dot`, `calendar-date`, `trash`, `pebble-glow-md`, `pebble-glow-lg`, `sparkle`, `heart`, `chart-double-grow`. Each has `"migrated": ["ios"],` today. Rewrite that line, as text, by row name, from the worktree root:

```bash
node -e '
const fs = require("node:fs");
const path = "packages/icons/icons.json";
const rows = new Set(["checkmark-dot", "check-circle", "check-circle-dot", "remove", "plus-circle", "camera", "camera-focus", "path", "people", "collection", "person", "stack", "slider", "pebble-dotted-glyph", "pebble-round", "pebble-flat", "karma-dot", "calendar-date", "trash", "pebble-glow-md", "pebble-glow-lg", "sparkle", "heart", "chart-double-grow"]);
const lines = fs.readFileSync(path, "utf8").split("\n");
let row = null;
let flipped = 0;
for (let i = 0; i < lines.length; i++) {
  const name = lines[i].match(/^      "name": "([^"]+)",$/);
  if (name) row = name[1];
  if (rows.has(row) && lines[i] === "      \"migrated\": [\"ios\"],") {
    lines[i] = "      \"migrated\": [\"ios\", \"android\"],";
    flipped++;
  }
}
fs.writeFileSync(path, lines.join("\n"));
console.log(flipped);
'
git diff --stat packages/icons/icons.json
```

Expected: `24`, then `1 file changed, 24 insertions(+), 24 deletions(-)`. Anything else: `git checkout packages/icons/icons.json` and look at the row the script missed.

- [ ] **Step 6: The lint, live, and the package gate.** From the worktree root:

```bash
npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons
```

Expected: all green; the last line is `replaces: 27 rows, 69 names, 0 hits` (the 27 iOS rows, 49 iOS names plus the 20 Android ones). A hit names a file and line under `apps/android/app/src/main` that still says `R.drawable.<name>`: migrate it in this commit, never widen the pattern.

- [ ] **Step 7: Android lint and tests.** `(cd apps/android && ./gradlew lint testDebugUnitTest)`. Expected: `BUILD SUCCESSFUL` (`PbblsIconsTest` still reads the manifest; `migrated` does not reach it).

- [ ] **Step 8: Commit, one commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/lab/components/LogTimeline.kt apps/android/app/src/main/kotlin/app/pbbls/android/features/lab/components/FeaturedCommunityCard.kt packages/icons/icons.json
git commit -m "chore(ui): android has migrated off every replaced drawable; the lint is live"
```

### Task 34: Part 6 gate, PR, references

- [ ] **Step 1: The Android gate**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation; `BUILD SUCCESSFUL`. Plus the package gate (block at the top): `replaces: 27 rows, 69 names, 0 hits`.

- [ ] **Step 2: Push and PR.** `git fetch origin`, fast-forward any lower part whose bot commit landed, then:

```bash
gh stack push
gh stack submit --auto --open
gh pr edit chore/1315-glyph-lab --title "chore(android): draw glyphs and the lab with pebbles icons, and arm the lint" --add-label chore,ui,android,no-lab-note --milestone "M66 · Brand icons" --body-file - <<'EOF'
Part 6 of #1315.

- Glyph cells: carve `pebble-dotted-glyph`, create `plus-circle`, at the fixed `max(side * 0.4, 18.dp)`.
- `GlyphTab.icon` (`person`, `check-circle-dot`, `people`) at `ToggleButtonDefaults.IconSize`.
- Prices in the store grid and the picker, the drawer's balance and price (`karma-dot`, INLINE), the owned mark (`check-circle-dot`, ROW); the me/creator swap stays (wave).
- The carve clear pill (`trash` through `CarvePillButton`, now ROW for the kept undo too).
- The Lab: timeline nodes (`check-circle`, `pebble-round.lit-1`, `pebble-round`) at a fixed 16dp in their 16dp column; the community card (`people`, TILE).
- Deleted: `ic_scribble`, `ic_plus`, `ic_person`, `ic_person_pair`, `ic_sparkle`, `ic_trash`, `ic_check_circle`, `ic_circle_dashed`, `ic_circle_inset_filled`, `ic_chat_bubbles`: all 20 replaced drawables are gone; 22 non-generated drawables remain (7 chrome, 11 wave, 4 brand).
- `packages/icons/icons.json`: `migrated` gains `android` on the 24 rows Android draws, in the commit that deletes the last replaced drawable. `npm run lint:replaces --workspace=@pbbls/icons` prints `replaces: 27 rows, 69 names, 0 hits`.

References: `StoreScreenshotsKt`, `GlyphChromeScreenshotsKt`, `PickerScreenshotsKt`, `LabScreenshotsKt`, `SoulsScreenshotsKt`, `CarveScreenshotsKt`, `ListDetailScreenshotsKt` re-record (about 22).
EOF
```

- [ ] **Step 3: Re-baseline**

```bash
git fetch origin && git log --oneline -1 origin/chore/1315-profile   # the part below: its bot commit must be in and fast-forwarded first
gh pr edit chore/1315-glyph-lab --add-label rebaseline-screenshots
# wait for the bot's "test(android): re-baseline ..." commit on origin/chore/1315-glyph-lab, then:
git fetch origin && git merge --ff-only origin/chore/1315-glyph-lab   # while chore/1315-glyph-lab is checked out
for id in $(gh run list --branch chore/1315-glyph-lab --status action_required --json databaseId --jq '.[].databaseId'); do gh api -X POST repos/alexisbohns/pbbls/actions/runs/$id/approve; done
```

Full procedure at the top (steps 1–5); label only once Part 5's bot commit is in. Expected classes: `StoreScreenshotsKt` (about 7, tabs, prices, drawer), `GlyphChromeScreenshotsKt` (2), `PickerScreenshotsKt` (glyph picker, 3), `LabScreenshotsKt` (about 5), `ListDetailScreenshotsKt` (glyph panes, about 5), and any render holding a `GlyphView` create or carve cell (`SoulsScreenshotsKt`'s "New soul", `CarveScreenshotsKt`): iOS Part 6 re-recorded six People references for the same reason.

### Device pass, Part 6 (the maintainer, on the Pixel 8)

- Glyphs: the store tabs (horizontal on the phone; vertical on a large-screen emulator, icon-only with the label as content description); prices in the grid; a glyph's drawer: balance, the kept swap, price, and the owned mark once owned.
- The glyph picker (from a soul's form): prices, the carve and create cells.
- Souls: the "New soul" create cell.
- Carve: the undo (kept) and clear (trash) pills at 20dp.
- The Lab: the community card; the changelog, in-progress and backlog timelines' nodes.
- Once at 200% font scale: prices and the balance; the timeline nodes hold at 16dp.
- TalkBack on the vertical store tabs (icon-only).

---

## Part 7 — retire the tokens (`chore/1315-retire-tokens`)

`gh stack add chore/1315-retire-tokens`.

### Task 35: Delete `PebblesIcon.kt`; its seven callers take 15dp inline

**Files:**
- Delete: `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PebblesIcon.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulFormScreen.kt` (import line 40, line 190)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsScreen.kt` (import line 55, line 529)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileLabCard.kt` (import, line 70)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/CollectionGlyphSection.kt` (import line 23, line 71)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileCollectionsCard.kt` (imports, line 65)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileAchievementsCard.kt` (import, line 103)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/AchievementsScreen.kt` (import, line 223)

The seven are six kept chevrons and the kept lock (all hand-traced, D1/D2), each at `PebblesIconToken.MEDIUM` (15dp). They keep 15dp, written inline.

- [ ] **Step 1: The seven sizes.** In each of the seven files, replace `Modifier.size(PebblesIconToken.MEDIUM.size)` with `Modifier.size(15.dp)` (one occurrence per file; every other `LARGE`/`SMALL` use left in Part 5). Then delete the line `import app.pbbls.android.core.designsystem.PebblesIconToken` from each. In `ProfileCollectionsCard.kt` only, add `import androidx.compose.ui.unit.dp` directly below `import androidx.compose.ui.res.stringResource` (the file had no `dp`); the six others already import it.

- [ ] **Step 2: Delete the tokens.**

```bash
git rm apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PebblesIcon.kt
grep -rn 'PebblesIconToken\|PebblesIcon(' apps/android/app/src
```

Expected: the grep prints nothing (the gallery stopped in Task 4, `DataTile` in Task 22, `ProfileCollectionCard` in Task 25). `PebblesIcon.kt` had no lint-baseline entry.

- [ ] **Step 3: Build and lint.** `npm run lint --workspace=@pbbls/android && (cd apps/android && ./gradlew lint :app:compileDebugScreenshotTestKotlin)`. Expected: no ktlint violation; `BUILD SUCCESSFUL`. No render changes: 15dp is what `MEDIUM` was.

- [ ] **Step 4: Commit**

```bash
git add -A apps/android/app/src/main/kotlin
git commit -m "chore(android): retire the PebblesIcon tokens"
```

### Task 36: Decisions log and lessons learned

**Files:**
- Modify: `docs/decisions/log.md` (append one entry)
- Modify: `docs/superpowers/plans/2026-10-30-android-icon-migration.md` (this file: fill `## Lessons learned` at the end)

- [ ] **Step 1: Append the entry.** From the worktree root (the heading takes today's date):

```bash
cat >> docs/decisions/log.md <<EOF

## $(date +%F) — Android draws the pebbles icons; chrome and the wave stay hand-traced; a drawable goes when its last reference does (#1315)

- **Status:** taken
- **Scope:** android, cross-surface (the marker and the lint)
- **Context:**
  - After #1311 the Android app drew 38 hand-traced \`ic_*\` VectorDrawables at 113 references, most of them tracings of SF Symbols iOS had just retired (#1314). It drew no Material icon. \`PebblesIconToken\` (13/15/17dp) sized them.
  - Android Lint runs \`UnusedResources\` under \`warningsAsErrors\`, so an orphaned drawable fails the build.
- **Decision:**
  - **Chrome stays hand-traced, closed list:** \`ic_arrow_back\`, \`ic_chevron_right\`, \`ic_arrow_right\` (the three keep \`autoMirrored\` and are never swapped for an \`ic_pbbls_*\` drawable, which mirrors nothing), \`ic_more_vert\`, \`ic_share\`, \`ic_undo\`, \`ic_x_circle\` at the Record flow's close; and the four brand marks \`ic_google_g\`, \`ic_launcher_foreground\`, \`ic_launcher_monochrome\`, \`pbbls_logo\`. Eleven tracings wait for \`packages/icons/waves/2026-11-chrome/\` (visibility, errors, upload, upvote, again, swap, locked, life domain, the time of day). A new hand-traced drawable outside these lists needs a drawing or a wave-brief line.
  - **Five sizes in dp,** \`PbblsIconSize\`: \`INLINE\` 16, \`ROW\` 20, \`TOOLBAR\` 22, \`TILE\` 28, \`HERO\` 48, iOS's values. **\`INLINE\` alone follows the font scale** (\`value.sp.toDp()\`, the platform's non-linear curve: 28dp at 200%); this narrows the package spec's D9 ("icons do not follow font scale") for one size. An M3 slot with its own icon token keeps it; a fixed dp slot (the band mark at 14, the glyph cells, the shelf badge, the Lab timeline nodes) passes a \`Dp\` with a comment saying why.
  - **Helpers carry a \`PbblsIcon\`:** \`SurfaceTile\`, \`DetailPlaceholder\`, \`DataTile\`, \`GlyphTab\`, \`achievementFamilyIcon\` (iOS's \`familySymbol\` table), \`PebblesKey.icon()\`. The three that still draw a kept tracing (\`MediaStateLabel\`, \`WhenPill\`, \`CarvePillButton\`) keep \`iconRes: Int\` and their Pebbles callers pass \`PbblsIcon.X.drawableRes\`. The navigation bar lights the selected tab's \`active\` twin over the M3 indicator.
  - **A drawable is deleted in the commit that removes its last reference,** not at the end of the stack (the \`UnusedResources\` gate). \`replaces.android\` lists exactly the 20 deleted drawables; \`migrated\` gained \`android\` on the 24 rows Android draws in the commit that deleted the last of them.
  - \`PebblesIconToken\` and \`PebblesIcon(painter, token, …)\` are gone; the seven kept tracings that used \`MEDIUM\` take \`Modifier.size(15.dp)\` inline.
- **Why:**
  - **Rejected, drawing the back arrow:** it sits beside the system back gesture and predictive back; a wobbly one reads as a bug.
  - **Rejected, all sizes following the font scale:** the M3 slots around an icon (48dp \`IconButton\`, 24dp navigation item) do not grow; an inline stone beside doubled text does need to.
  - **Rejected, deleting every drawable in the last part:** every part in between would fail \`./gradlew lint\`.
- **Consequences:**
  - Any new Android site draws \`PbblsIconImage(icon = PbblsIcon.X, size = PbblsIconSize.Y, …)\`; naming a replaced drawable fails to compile (the file is gone) and fails the \`Icons\` job's \`replaces\` step.
  - Screenshot references were re-recorded per part by the \`rebaseline-screenshots\` bot.
  - Web (#1313) flips its own surface in \`migrated\` the same way.
- **Supersedes / Superseded-by:** narrows the package spec's D9 for \`INLINE\` (above); extends the 2026-10-30 iOS entry's marker and lint to Android, no change to their rules.
- **Refs:** #1315, \`docs/superpowers/specs/2026-10-30-android-icon-migration-design.md\` (D1–D11), \`docs/superpowers/plans/2026-10-30-android-icon-migration.md\`, \`apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIconSize.kt\`, \`packages/icons/scripts/lib/replaces.ts\`.
EOF
tail -5 docs/decisions/log.md
```

Expected: the tail shows the `Refs` line; the heading line carries today's date (check with `grep -n 'Android draws the pebbles icons' docs/decisions/log.md`). If the device pass changed a size (R2: `INLINE` stops following the font scale; `DataTile` moves to `TILE`), edit the matching bullet before committing.

- [ ] **Step 2: Lessons learned.** Replace the paragraph under `## Lessons learned` at the end of this file with bullets answering, from what happened: how many references each part's bot commit re-recorded, against the estimates; whether any part went red on a lower part's PNGs (and how the fast-forward went); what the device pass changed (the FAB's plus in its disc, `DataTile` at INLINE vs iOS's 28, the shelf badge at 16 vs iOS's `row` 20, the assiduity ring's contrast, R2's verdict on INLINE at 200%, R3's verdict on the lit twin over the indicator); whether `PbblsIconSizeTest`'s 28dp held on CI; anything `lint:replaces` or `UnusedResources` caught that a task missed; any reviewer catch worth keeping as a habit.

- [ ] **Step 3: Commit**

```bash
git add docs/decisions/log.md docs/superpowers/plans/2026-10-30-android-icon-migration.md
git commit -m "docs: log the android icon chrome list, the sizes and delete-when-orphaned"
```

### Task 37: Part 7 gate, PR, merge

- [ ] **Step 1: The Android gate**

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
npm run lint --workspace=@pbbls/android
(cd apps/android && ./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin)
```

Expected: no ktlint violation; `BUILD SUCCESSFUL`. Plus `npm run lint:replaces --workspace=@pbbls/icons`: `replaces: 27 rows, 69 names, 0 hits`.

- [ ] **Step 2: Push and PR.** `git fetch origin`, fast-forward any lower part whose bot commit landed, then:

```bash
gh stack push
gh stack submit --auto --open
gh pr edit chore/1315-retire-tokens --title "chore(android): retire the PebblesIcon tokens" --add-label chore,ui,android,no-lab-note --milestone "M66 · Brand icons" --body-file - <<'EOF'
Resolves #1315.

- `core/designsystem/PebblesIcon.kt` deleted; its seven `MEDIUM` callers (six kept chevrons and the kept lock) take `Modifier.size(15.dp)` inline. No render changes.
- `docs/decisions/log.md`: the Android chrome list, the five sizes with `INLINE` following the font scale (narrowing package D9), delete-when-orphaned.
- The plan's lessons learned.

The stack: 72 references moved to pebbles icons and 2 to shapes, 20 drawables deleted, 7 chrome, 11 wave and 4 brand drawables kept, `migrated` carries `android` on 24 rows, `lint:replaces` prints `27 rows, 69 names, 0 hits`.
EOF
```

No re-baseline expected (15dp was `MEDIUM`'s size). If the screenshot check goes red anyway, look at the diff before labelling: a moved chevron is a bug in Task 35.

- [ ] **Step 3: Before merging.** Every bot commit fast-forwarded in; `gh stack rebase` from the top; `git diff --stat origin/<b> <b>` empty on every branch; `gh stack push`; every PR `CLEAN` (re-run a run the double push cancelled with `gh run rerun <id>`; approve held ones as in step 4 of the procedure). Bot commits break the stack's linearity, so rebase and let CI re-run before merging. Then hand the maintainer `gh stack merge --yes --squash` (the classifier blocks merges for the agent).

### Device pass, Part 7

The six chevrons (Settings rows, the soul form's glyph row, a collection's glyph section, the Profile collections, Lab and achievements cards) and the lock on locked achievements look exactly as before.

---

## Self-review against the spec

- **D1** chrome list: kept in place at every site the tasks pass (`ic_arrow_back`, `ic_chevron_right` and the others are named where a file keeps `Icon`); the closed list lands in the log (Task 36). **D2** wave: Task 2's brief names all eleven; `ic_people` leaves `people.replaces.android` in Task 1; the mixed drawables switch by site (Tasks 7–8 `ic_people`, 12 `ic_x_circle`, 21 and 26 `ic_alternating_current`, 23 `ic_pebble_domain`, 14 `ic_pebble_when`). **D3** picks: every site's entry is the spec table's, including the by-site ones (`path` tab, `camera` form row, `check-circle-dot` owned, `sparkle` photo date, `pebble-glow-lg` celebration, `chart-double-grow` usage tile). **D4** moments flat / pebbles round / the family table / the grid shape: Tasks 23, 26, 33. **D5** the lit tab: Tasks 6–7. **D6** sizes, the INLINE exception, M3 tokens, fixed slots, size through `size`: Task 3 and every site. **D7** helpers take `PbblsIcon` (Tasks 7, 8, 21, 22, 23, 29, 30, 33), the three `Int` helpers keep theirs (Tasks 12, 13, 32), `Visibility.iconRes` untouched. **D8** `replaces.android` (Task 1), delete-when-orphaned (Tasks 10, 14, 15, 18, 22–26, 29–33), `migrated` and the live lint (Task 33). **D9** re-baselines per part: Tasks 5, 11, 16, 20, 28, 34. **D10** nothing in `src/debug`: no task touches it. **D11** tints: no tint inside M3 components (Tasks 7, 10, 15, 17, 30), existing tints carried over everywhere else.
- **Accessibility:** every standalone icon keeps its existing label (`souls_add_a11y`, `collections_add_a11y`, `glyphs_carve_a11y`, `settings_title`, `photo_remove_a11y`, the FAB's `label`, the shelf badge's title, the icon-only store tabs); no string is added, so `LocalizationParityTest` is untouched.
- **Placeholders:** none; every site lists its old and new code.
- **Names:** `PbblsIconSize` (`INLINE`, `ROW`, `TOOLBAR`, `TILE`, `HERO`, `value`, `followsFontScale`), `PbblsIconSize.toDp(Density)`, `PbblsIconImage(icon, size: PbblsIconSize, contentDescription, modifier, tint)`, `PebblesKey.icon()`, `DetailPlaceholder(icon:)`, `SurfaceTile(icon:)`, `DataTile(icon:)`, `GlyphTab.icon`, `achievementFamilyIcon(family): PbblsIcon` are used with the same names in every task.

## Deviations from the spec

1. **`INLINE` at 200% is 28dp, not 32dp.** The spec's `PbblsIconSizeTest` says `INLINE.toDp(Density(1f, fontScale = 2f))` is 32dp. Compose's `FontScaling.toDp` applies Android's non-linear font-scale table whenever `fontScale >= 1.03`, on the plain JVM too (read from `androidx.compose.ui:ui-unit-android:1.12.1`, `FontScaleConverterFactory`): at 2.0 the table maps 14sp → 26dp and 18sp → 30dp, so 16sp → 28dp. Task 3 asserts 28 (the spec's own D6 says "non-linear on API 34+"). Task 3 Step 4 stops rather than edits the value if CI disagrees.
2. **The assiduity grid's inactive day is a ring, not an `outlineVariant` dot.** The spec's D4 says "a plain `Circle` in `primary` (active) or `outlineVariant`, as iOS does". iOS's `AssiduityGrid.swift` draws the active day filled `primary` and the inactive one `Color.clear` with a 1pt `surfaceContainerHighest` ring. Task 26 mirrors the iOS code (the 1:1 rule); the ring's contrast on Android's stats card is on the Part 5 device pass. If the maintainer prefers the spec's `outlineVariant` dot, it is a one-line change in Task 26.
3. **The Lab timeline nodes draw at a fixed 16dp, not `INLINE`.** The node sits in a `Modifier.width(16.dp)` column (`LogTimeline.kt:91`), a fixed slot; `INLINE` would ask for 28dp at 200% inside 16dp. Identical at the default font scale.
4. **Observation, no change:** iOS draws the achievements shelf badge at `row` (20) in its 32pt disc (`ProfileAchievementsCard.swift:69`); the spec keeps Android's 16dp. Task 23 follows the spec and the Part 5 device pass names it.

Everything else in the spec matched the code: 121 references (113 UI + 8 marks), 42 non-generated drawables before and 22 after, 11 names in the gallery, no drawable in `src/debug` or `src/test`, 53 lint-baseline issues with none on a drawable and three on touched files (all on untouched `Locale.getDefault()` lines), seven `MEDIUM` callers, and every `PbblsIcon` entry the mapping names present in the generated enum.

## Lessons learned

Executed 2026-10-30 by subagent-driven development (one implementer and one reviewer per part, seven parts, 37 tasks) from `.worktrees/icons-android-stack`; PRs #1342, #1344, #1346, #1347, #1349, #1350, #1351. No task deviated from the plan in content; every quoted "old" snippet matched, with line numbers drifting by a few lines after Part 2.

- **The bot re-recorded 159 references, against the estimate of about 170:** Part 1 = 4, Part 2 = 28, Part 3 = 37, Part 4 = 23, Part 5 = 28, Part 6 = 39. `ListDetailScreenshotsKt` re-recorded in Parts 2, 4, 5 and 6 as predicted. Part 7 changed nothing.
- **No part went red on a lower part's PNGs.** Labelling one part at a time, fast-forwarding its bot commit and `gh stack rebase --no-trunk` before the next label worked every time, with no PNG conflict.
- **`gh stack push` force-pushes over a bot commit.** The fetch that revealed Part 1's bot commit and the push ran in one command chain, and the push overwrote the commit. It was recoverable only because the fetch had already brought it local (`git branch -f <b> origin/<b>`, rebase, push again). The fast-forward is its own step, before any push, never in the same chain.
- **Android Lint went red on main the same day, outside the stack:** `GradleDependency` found `screenshot-validation-api` alpha17 while the plugin stayed at alpha16 on Google's Maven. Until #1343 landed (the library pinned on its own), every local gate ran `lint --continue` and read the text report minus `GradleDependency`; `--offline` does not help, lint caches the version index. Pin a library whose plugin lags on its own line; a plain bump of the shared version does not resolve.
- **`PbblsIconSizeTest`'s 28dp at 200% held on the JVM** (Compose ships its own copy of the font-scale table; the reviewer read it in `ui-unit-android:1.12.1`); CI result pending at the time of writing.
- **`UnusedResources` caught nothing a task missed:** delete-when-orphaned and the Part 1 gallery rewrite left no orphan at any part boundary; `lint:replaces` went live at `27 rows, 69 names, 0 hits` on the first run.
- **ktlint joined one declaration onto a line** (Part 2, `SoulsPlaceholder()`): a replacement that shortens a body expression trips `ktlint_official`; the implementer ran `ktlintFormat` and checked the diff was only that. Expect it when a replacement shortens a one-expression function.
- **Reviewer habits worth keeping:** read the deleted file to confirm a token's value before calling a change render-neutral (`MEDIUM` = 15dp); diff the decisions-log entry against the stack as built, sentence by sentence; check a KDoc claim about "the first size wins" against the modifier order; compare the achievement table case by case with the iOS source.
- **Owed to the device pass (the maintainer, Pixel 8):** the FAB's plus at 24dp in its 56dp disc; `DataTile` at INLINE beside subhead text (reads small in the renders; iOS draws 28); the shelf badge at 16dp vs iOS's 20; the assiduity ring's contrast on the stats card; INLINE at 200% (R2); the lit twin over the M3 indicator (R3). Any change is one line in `PbblsIconSize` or the site's `size =`.
- **Follow-ups noted in PR bodies:** the iOS `CollectionMark.swift` header still names `ic_pebble_collection`; one `achievementFamilies` list for the icon table and `achievementGroupName`; `CarvePillButton` under 48dp; the generated `baseline-prof.txt` lists the old `SurfaceTile(Painter, …)` until the next baseline run.
