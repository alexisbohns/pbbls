# Android month wrap: the animated intro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the iOS month wrap's animated, haptic intro to Android (#1242), as a deck of galley cards, and show the same month as iOS (every week, milestones in their stones), with Pebbles' own haptics switch.

**Architecture:**
- **The score:** a pure Kotlin port of iOS's `IntroScore` turns each card into beats.
- **The clock:** one `IntroClock` per month page plays them on Compose's frame clock. Figures read each key's progress in the draw phase, defaulting to settled.
- **Haptics:** the same beats compile into one `VibrationEffect.Composition` per card, falling back to a waveform, then to `performHapticFeedback`.
- **The deck:** a `VerticalPager` of galley cards nested in the Path's `HorizontalPager`.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2026.09, foundation 1.12), `android.os.Vibrator`/`VibrationEffect` (API 33 floor), Hilt, Preferences DataStore (`SettingsStore`), Coil 3, JUnit 4 + Robolectric, Compose Preview Screenshot Testing.

**Read first:**
- the design addendum `docs/superpowers/specs/2026-10-26-android-month-intro-design.md` (D18–D24)
- the iOS spec `docs/superpowers/specs/2026-10-25-ios-month-intro-design.md`: the choreography, D1–D17's final shape
- `apps/android/CLAUDE.md`

The iOS sources named in each task are the reference for *what it does*.

---

## Ground rules (read once, apply everywhere)

- **Worktree:** `/Users/alexis/code/pbbls/.worktrees/android-month-intro`. Run every command from there; never `cd` to the main checkout.
  - `apps/android/secrets.properties` is already copied in.
  - The local branch `chore/1242-haptic-probe` holds the device probe (`SpineLabHaptics.kt`); read it for the Vibrator calls. It is never pushed.
- **Environment, before any Gradle call:** `export ANDROID_HOME=$HOME/Library/Android/sdk`. Without it, `scripts/gradle-if-sdk.sh` silently no-ops.
- **Commands** (from `apps/android`):
  - unit tests, one class: `./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.features.path.wrap.intro.IntroScoreCoverTest"`
  - unit tests, all: `./gradlew :app:testDebugUnitTest`
  - lint: `./gradlew ktlintCheck` (`ktlintFormat` fixes), then `./gradlew :app:lintDebug`. CI runs `./gradlew lint`, and `warningsAsErrors` is on.
  - screenshot sources compile: `./gradlew :app:compileDebugScreenshotTestKotlin`. **Never record references locally.** CI records them when the PR carries `rebaseline-screenshots` (add that label alone, in its own `gh pr edit`).
  - install on the Pixel 8: `./gradlew :app:installDebug`, then `$ANDROID_HOME/platform-tools/adb shell am start -n app.pbbls.android/.SpineLabActivity` (the Lab) or `-n app.pbbls.android/.MainActivity` (the app).
- **The Pixel 8 holds the maintainer's real account.**
  - Installing a debug build is allowed; it keeps the data.
  - Nothing in this plan writes to the account, and nothing may.
  - Ask before any write.
- **TDD for every pure unit:** score, motion, clock, haptics compile steps, deck logic, shapes. The tests are ports of the Swift oracles named in each task: same inputs, same expected values. Seconds are `Double`, as Swift's `TimeInterval`, so values port unchanged.
- **Settled is the default.** No existing screenshot reference may change, except where a task says so (Part 1's month page, Part 8's Settings).
  - Every reveal parameter defaults to settled.
  - `Modifier.introReveal` returns `this` for `IntroReveal.settled`, so no extra `graphicsLayer` reaches a settled render.
  - Figures only transform a mark whose progress is below 1.
- **Boundaries:** `core/` never imports `features/` (`ArchitectureBoundaryTest`). `core/` components take progress providers (`() -> Float`, `(Int) -> Float`) or `Modifier`s, never an intro type.
- **No literals:** no `Color(0x…)`, no literal `RoundedCornerShape` radius, no `.copy(fontSize = …)` in `features/` or `core/ui` (`ThemeLiteralsTest`). Colours come from `PebblesTheme.spine.colors`, type from `PebblesTheme.spine.type`.
- **Strings:** `res/values/strings.xml` and `res/values-fr/strings.xml`, inserted as text at an anchor line, never by rewriting the file. `LocalizationParityTest` checks en/fr parity.
- **Commits:** conventional, lowercase, no period; one logical change each. Every commit message ends with:

  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```
- **Stack:** `gh stack` (see the `gh-stack` skill).
  - Part 1's branch exists. Each later part starts with `gh stack add <branch>` from the top branch.
  - **Pushing, submitting and merging are the maintainer's.** The auto-mode classifier blocks them, so prepare the command (or a script in the scratchpad) and hand it over.
  - Before any `gh stack push`/`submit`/`rebase` after a re-baseline bot commit, fetch and fast-forward the local branch first (memory: gh stack vs the rebaseline bot).
- **After each part:** install on the Pixel 8 and give the maintainer a short list of what to try. Record what they report in "As built" at the end of this plan.

## File map

`features/path/wrap/intro/` is new; everything under `app/src/main/kotlin/app/pbbls/android/`.

| File | Part | Responsibility |
|---|---|---|
| `core/designsystem/RevealMotion.kt` | 2 | Generic reveal curves: `clamp`, `easeOut`, `easeInOut`, `easeInOutInverse`, `popScale`, `popOpacity` (core can apply them). |
| `core/designsystem/TouchExploration.kt` | 2 | `rememberTouchExploration()`, live on the accessibility manager. |
| `features/path/wrap/intro/IntroBeat.kt` | 2 | `IntroSequence`, `IntroKey`, `IntroTick`, `IntroSwell`, `IntroBeat`, `IntroReveal`. |
| `features/path/wrap/intro/IntroMotion.kt` | 2 | Durations, the stamp's curves, `IntroCurve`. |
| `features/path/wrap/intro/IntroTempo.kt` | 2 | Cover ×1.45, sections ×2.5. |
| `features/path/wrap/intro/IntroScore.kt` | 2 (grows 3–7) | Every constant, `duration`, `cover`. |
| `features/path/wrap/intro/IntroScoreSections.kt` | 3, 4 | `head`, `shift`, `persons`, `dotPlace`. |
| `features/path/wrap/intro/IntroScoreGem.kt` | 5 | `gem`, `countSpan`, `counted`, `gemRuns`, `sweep`. |
| `features/path/wrap/intro/IntroScoreInsights.kt` | 6 | `insightsHead`, `tone`, `hours`, `ranks`. |
| `features/path/wrap/intro/IntroScoreSignatures.kt` | 7 | `weekColumns`, `milestones`, `domains`, `end`, `fillSpan`, `swellTo`. |
| `features/path/wrap/intro/IntroLedger.kt` | 2 | Once per month per launch. |
| `features/path/wrap/intro/IntroClock.kt` | 2 | Phases, reveal, `register`/`show`/`leave`/`landed`/`skip`/`settleAll`/`advance`, the frame driver. |
| `features/path/wrap/intro/IntroPolicy.kt` | 2 | Reduce Motion, TalkBack, large text. |
| `features/path/wrap/intro/IntroStage.kt` | 2 | `LocalIntroStage`, `IntroHost`, `currentIntroReveal()`, `Modifier.introReveal`, `Modifier.onAnyTap`. |
| `features/path/wrap/intro/IntroHaptics.kt` | 2 | Pure: `IntroHaptics.FLOOR`, `events`, `swells`; `IntroHapticPlaying`. |
| `features/path/wrap/intro/IntroHapticTier.kt` | 2 | The tier choice. |
| `features/path/wrap/intro/IntroComposition.kt` | 2 | Pure: beats → primitives with pauses (tier C). |
| `features/path/wrap/intro/IntroWaveform.kt` | 2 | Pure: beats → timings and amplitudes (tier W). |
| `features/path/wrap/intro/IntroVibrator.kt` | 2 | The device backend, three tiers; `rememberIntroVibrator`. |
| `features/path/wrap/month/MilestoneSnap.kt` | 1 | The polaroid and its pure shape and layout. |
| `features/path/wrap/month/MonthHead.kt` | 3 | `MonthHead` and each section's head. |
| `features/path/wrap/month/MonthSectionContent.kt` | 3 | The section switch, moved out of `MonthPage`. |
| `features/path/wrap/month/MonthDeck.kt` | 3 | Cards, insights lead, beats per card, the pager. |
| `features/path/wrap/month/MonthDeckMotion.kt` | 3 | Pure deck motion and the stepper. |
| `features/path/wrap/month/MonthDeckCard.kt` | 3 | The galley card: paper, margin, stepper, title band, room. |
| `features/path/wrap/month/WeekColumn.kt`, `WeeksColumns.kt` | 7 | The weeks' calendar of columns. |
| `features/path/wrap/month/MonthDeckEnd.kt` | 7 | The end card and Replay. |
| `core/data/HapticsPreference.kt` | 8 | The switch on `SettingsStore`. |

Modified along the way:
- `core/ui/MarkRow.kt` (1, 4)
- `core/ui/render/stone/LitStone.kt` (1)
- `core/ui/DaySetMarks.kt` (4)
- `core/ui/marks/CairnMark.kt` (7)
- `core/data/TapHaptics.kt` (8)
- `features/path/wrap/month/*`
- `features/path/PathScreen.kt`, `PathViewModel.kt` (3)
- `features/profile/Settings*` (8)
- the Spine Lab (`src/debug/…/SpineLabWeek.kt`) (2, 3)
- the manifest (2)
- strings (1, 7, 8)

---

# Part 1 — what the month says (`feat/1242-android-month-content`)

D22 (the iOS D12 and D16): every week of the month is listed, with an empty one muted. Milestones show their date first, their lit stone at 45 % rings, and their photo as a polaroid, or their souls. No intro code. The branch already exists and holds the spec commit.

### Task 1: The empty week's words

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (after `path_wrap_line_no_pebble`), `app/src/main/res/values-fr/strings.xml` (same anchor)
- Modify: `app/src/main/kotlin/app/pbbls/android/features/path/wrap/copy/WrapCopy.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/features/path/wrap/WrapCopyTest.kt`, `WrapCopyFrenchTest.kt`

- [ ] **Step 1: Write the failing tests.** In `WrapCopyTest`, add:

```kotlin
    @Test
    fun `a past week without a pebble says so`() {
        assertEquals("No pebble.", copy.emptyWeek())
    }
```

In `WrapCopyFrenchTest` (same shape, its own `copy`):

```kotlin
    @Test
    fun `a past week without a pebble says so`() {
        assertEquals("Aucun galet.", copy.emptyWeek())
    }
```

- [ ] **Step 2: Run them; they fail** (`emptyWeek` unresolved).

Run: `./gradlew :app:testDebugUnitTest --tests "app.pbbls.android.features.path.wrap.WrapCopy*"`

- [ ] **Step 3: Add the strings and the function.**

  - `values/strings.xml`, on the line after `<string name="path_wrap_line_no_pebble">No pebble yet.</string>`:

    ```xml
        <string name="path_wrap_line_no_pebbles">No pebble.</string>
    ```

  - `values-fr/strings.xml`, after `path_wrap_line_no_pebble`:

    ```xml
        <string name="path_wrap_line_no_pebbles">Aucun galet.</string>
    ```

  - `WrapCopy.kt`, after `soFar()`:

    ```kotlin
        /** A past week of a month without a pebble (#1242, iOS D12): a month page is always a completed month. */
        fun emptyWeek(): String = resources.getString(R.string.path_wrap_line_no_pebbles)
    ```

- [ ] **Step 4: Run the tests and `LocalizationParityTest`; they pass.**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml \
  app/src/main/kotlin/app/pbbls/android/features/path/wrap/copy/WrapCopy.kt \
  app/src/test/kotlin/app/pbbls/android/features/path/wrap/WrapCopyTest.kt \
  app/src/test/kotlin/app/pbbls/android/features/path/wrap/WrapCopyFrenchTest.kt
git commit -m "feat(android): say when a past week holds no pebble"
```

### Task 2: Every week of the month

**Files:**
- Modify: `features/path/wrap/month/WeeksSummary.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/WeeksSummaryTest.kt` (create)

Port of iOS `WeeksSummary.weeks`/`isShown`/`row` (`apps/ios/Pebbles/Features/Path/Wrap/Month/MonthSections.swift`).

- [ ] **Step 1: Write the failing test.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.MonthWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthCardsTests.everyWeek` / `allEmpty` (#1241 D12) on the debug fixture's October. */
class WeeksSummaryTest {
    private val october = SpineFixtureWeek.monthWrap

    @Test
    fun `every week of the month is listed, an empty one too`() {
        val weeks = WeeksSummary.weeks(october)
        assertEquals(october.weeks!!.size, weeks.size)
        assertEquals(0, weeks.first().pebbles)
        assertTrue(WeeksSummary.isShown(october))
    }

    @Test
    fun `a month whose weeks are all empty shows no weeks`() {
        val quiet = october.copy(weeks = october.weeks!!.map(::emptied))
        assertFalse(WeeksSummary.isShown(quiet))
    }

    private fun emptied(week: MonthWeek) = week.copy(pebbles = 0, soulIds = emptyList(), days = week.days.map { DayWeight(0, 0) })
}
```

(The fixture's first week, 28 September, is already empty: `MonthWeek(LocalDate.of(2026, 9, 28), 0, 3, 0, …)`.)

- [ ] **Step 2: Run it; it fails** (`WeeksSummary` has no companion).

- [ ] **Step 3: Rewrite `WeeksSummary.kt`.** Keep its imports and add `IntroKey`-free code only. Replace the body from `private val GLYPH` down with:

```kotlin
private val GLYPH = 14.dp
private const val GLYPHS = 5

/**
 * The month by week (#1207, D6; #1242, iOS D12): every ISO week cut to the month's days, its cairn,
 * its range and one line, and its souls; a tap focuses that week in the strip. A week without a
 * pebble is muted: a dotted stone where its cairn would stand, "No pebble.", no souls, no tap.
 */
@Composable
internal fun WeeksSummary(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onWeek: (LocalDate) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    if (!WeeksSummary.isShown(wrap)) return
    val weeks = WeeksSummary.weeks(wrap)
    Column {
        MonthSectionHead(copy.weeksHeader(weeks.size), copy.weeksHint(wrap.month))
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            weeks.forEach { week ->
                val range = copy.range(wrap.month, week.from, week.to)
                if (week.pebbles == 0) {
                    val line = copy.emptyWeek()
                    MarkRow(
                        title = line,
                        titleStyle = type.mouthLine,
                        titleColor = colors.ink3,
                        subtitle = range,
                        description = "$range, $line",
                        // The place a stone would stand, dotted, as the strip draws a week's next stone.
                        leading = { CairnMark(emptyList(), CairnState.LIVE, colors.ink3) },
                    )
                } else {
                    val line = copy.line(week.pebbles, week.soulIds.size, live = false, hasMoments = true)
                    val souls = week.soulIds.mapNotNull(lookups.soul)
                    MarkRow(
                        title = line,
                        titleStyle = type.mouthLine,
                        subtitle = range,
                        description = "$range, $line",
                        leading = { CairnMark(week.days, CairnState.SETTLED, colors.ink2) },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                souls.take(GLYPHS).forEach { SoulMark(it.glyph, colors.ink2, Modifier.size(GLYPH)) }
                            }
                        },
                        onClick = { onWeek(week.weekStart) },
                    )
                }
            }
        }
    }
}

/** What the weeks draw, shared with the deck (#1242, iOS `WeeksSummary.weeks`/`isShown`). */
internal object WeeksSummary {
    /** Every week of the month, the empty ones too. */
    fun weeks(wrap: MonthWrap): List<MonthWeek> = wrap.weeks.orEmpty()

    /** Drawn when a week of the month has pebbles. */
    fun isShown(wrap: MonthWrap): Boolean = weeks(wrap).any { it.pebbles > 0 }
}
```

Add the import `app.pbbls.android.core.model.wrap.MonthWeek`. A composable and an object may share the name `WeeksSummary` in Kotlin (a function and a type); if ktlint or the compiler objects, name the object `WeeksSummaryRules` and update the test.

- [ ] **Step 4: Run the test; it passes.** Then check that `CairnMark` with no days and `LIVE` draws its dotted next stone. Read `core/ui/marks/CairnMark.kt`'s `LIVE` branch: it is the strip's live week. If an empty `days` draws nothing there, add the dotted stone it draws for the live week's next stone in that case, and say so in the commit body.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/features/path/wrap/month/WeeksSummary.kt \
  app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/WeeksSummaryTest.kt
git commit -m "feat(android): list every week of the month, an empty one muted"
```

### Task 3: A row can put its subtitle first, and widen its mark

**Files:**
- Modify: `core/ui/MarkRow.kt`

- [ ] **Step 1: Add the parameters.** Add three parameters after `clickLabel`:

```kotlin
    /** The subtitle over the title (a milestone's date, #1242 / iOS D12). */
    subtitleFirst: Boolean = false,
    /** At least this tall, past [ROW_MIN] (a milestone's 56 dp, a card's share later). */
    minHeight: Dp = ROW_MIN,
    /** The mark column's width; a lit stone with its rings needs more than [MARK_COLUMN]. */
    markWidth: Dp = MARK_COLUMN,
```

  - Use `.heightIn(min = maxOf(minHeight, ROW_MIN))` and `Box(Modifier.width(markWidth)) { leading() }`.
  - In the `Column` branch, draw the subtitle first when `subtitleFirst`:

```kotlin
            Column(Modifier.weight(1f)) {
                if (subtitleFirst) Text(text = subtitle, style = type.contentsTime, color = colors.ink3)
                Text(text = title, style = titleStyle, color = titleColor)
                if (!subtitleFirst) Text(text = subtitle, style = type.contentsTime, color = colors.ink3)
            }
```

  - Import `androidx.compose.ui.unit.Dp`. Update the KDoc's first sentence to mention the subtitle above or under the title.

- [ ] **Step 2: Build and keep the references.** `./gradlew :app:compileDebugKotlin :app:compileDebugScreenshotTestKotlin`. Every call site keeps the defaults, so no render changes.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/core/ui/MarkRow.kt
git commit -m "feat(android): let a mark row put its subtitle first"
```

### Task 4: The lit stone's rings take a strength

**Files:**
- Modify: `core/ui/render/stone/LitStone.kt`

- [ ] **Step 1: Add the parameter.** `LitStone` gains, after `width`:

```kotlin
    /** The halo's and the rings' strength, 0…1: the Spine's 1, a month's milestone 0.45 (#1242 / iOS D16). */
    rings: Float = 1f,
```

  Pass it to `StoneRings` as `strength = rings`. In `StoneRings`, add `strength: Float` and draw `drawPath(paths.halo, halo, alpha = strength)` and `drawPath(it, color, alpha = strength, style = stroke)`. At 1, `alpha = 1f` is `DrawScope`'s default, so the Spine's renders are unchanged. Update `LitStone`'s KDoc with one sentence on `rings`.

- [ ] **Step 2: Compile.** `./gradlew :app:compileDebugKotlin`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/core/ui/render/stone/LitStone.kt
git commit -m "feat(android): let the lit stone draw its rings quieter"
```

### Task 5: The milestone's polaroid

**Files:**
- Create: `features/path/wrap/month/MilestoneSnap.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/MilestoneSnapTest.kt` (create)

Port of iOS `MilestoneSnap` (`apps/ios/Pebbles/Features/Path/Wrap/Month/MilestoneMarks.swift`) and its tests (`MonthCardsTests.polaroidShapes`, `.polaroid`).

- [ ] **Step 1: Write the failing test.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthCardsTests.polaroidShapes` and `.polaroid` (#1241 D16). */
class MilestoneSnapTest {
    private val wide = 4f / 3
    private val tall = 3f / 4

    @Test
    fun `a polaroid is square, 3 by 4 or 4 by 3, the nearest to its photo`() {
        assertEquals(wide, MilestoneSnapShape.shape(16f / 9), 0f)
        assertEquals(wide, MilestoneSnapShape.shape(wide), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(1.1f), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(1f), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(0.9f), 0f)
        assertEquals(tall, MilestoneSnapShape.shape(tall), 0f)
        assertEquals(tall, MilestoneSnapShape.shape(9f / 16), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(Float.NaN), 0f)
    }

    @Test
    fun `a polaroid keeps within its box, narrow for a portrait, wide for a landscape`() {
        val portrait = MilestoneSnapShape.layout(height = 60f, ratio = 9f / 16)
        val landscape = MilestoneSnapShape.layout(height = 60f, ratio = 16f / 9)
        assertEquals(tall, portrait.windowWidth / portrait.windowHeight, 1e-5f)
        assertEquals(wide, landscape.windowWidth / landscape.windowHeight, 1e-5f)
        assertTrue(portrait.cardWidth < landscape.cardWidth)
        listOf(portrait, landscape).forEach {
            assertTrue(it.cardHeight <= 60f + 1e-4f)
            assertTrue(it.cardWidth <= 60f * 1.15f + 1e-4f)
        }
        val pending = MilestoneSnapShape.layout(height = 60f, ratio = Float.NaN)
        assertEquals(pending.windowWidth, pending.windowHeight, 1e-5f)
    }
}
```

- [ ] **Step 2: Run it; it fails.**

- [ ] **Step 3: Write `MilestoneSnap.kt`.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.LocalSnapURLCache
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.designsystem.PebblesTheme
import coil3.compose.AsyncImage
import kotlin.math.sqrt

/** A milestone polaroid's frames, in the box's own unit (dp at the call site). */
internal data class MilestoneSnapLayout(
    val cardWidth: Float,
    val cardHeight: Float,
    val windowWidth: Float,
    val windowHeight: Float,
    val margin: Float,
    val strip: Float,
)

/** The polaroid's pure geometry (iOS `MilestoneSnap.shape`/`.layout`). */
internal object MilestoneSnapShape {
    /** The box's width over its height: room for a landscape photo. */
    const val BOX = 1.15f
    /** The strip under the photo, of the box's height. */
    private const val STRIP = 0.24f
    const val TILT = 4f

    /** Square, 3:4 or 4:3, whichever is nearest by the geometric midpoints; square while unknown. */
    fun shape(ratio: Float): Float {
        if (!ratio.isFinite() || ratio <= 0f) return 1f
        if (ratio >= sqrt(4f / 3)) return 4f / 3
        if (ratio <= sqrt(3f / 4)) return 3f / 4
        return 1f
    }

    fun layout(
        height: Float,
        ratio: Float,
    ): MilestoneSnapLayout {
        val margin = maxOf(2f, height * 0.06f)
        val strip = height * STRIP
        val roomWidth = height * BOX - 2 * margin
        val roomHeight = height - margin - strip
        val safe = shape(ratio)
        val (w, h) = if (safe >= roomWidth / roomHeight) roomWidth to roomWidth / safe else roomHeight * safe to roomHeight
        return MilestoneSnapLayout(w + 2 * margin, margin + h + strip, w, h, margin, strip)
    }
}

/**
 * A milestone's photo as a mini polaroid (#1242, iOS D16): the stock under a rule, the photo in a
 * window of one of three shapes, cropped, a strip with two faint caption lines, tilted clockwise,
 * keeping within a box [height] tall. Until it loads, the window is a bed in [tint]. Decorative:
 * the row says the milestone.
 */
@Composable
internal fun MilestoneSnap(
    prefix: String,
    tint: Color,
    height: Dp,
    aspect: SnapAspectCache?,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val urls = LocalSnapURLCache.current
    var url by remember(prefix) { mutableStateOf<String?>(null) }
    LaunchedEffect(prefix, urls) {
        if (url == null && urls != null) {
            url =
                runCatchingCancellable { urls.signedUrls(prefix).thumb }
                    .onFailure { Log.e(TAG, "snap sign failed for $prefix", it) }
                    .getOrNull()
        }
    }
    var ratio by remember(prefix) { mutableStateOf(aspect?.cached(prefix) ?: Float.NaN) }
    LaunchedEffect(prefix, aspect) {
        if (ratio.isNaN() && aspect != null) aspect.ratio(prefix)?.let { ratio = it }
    }
    val box = MilestoneSnapShape.layout(height.value, ratio)
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier.size(height * MilestoneSnapShape.BOX, height).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .rotate(MilestoneSnapShape.TILT)
                .size(box.cardWidth.dp, box.cardHeight.dp)
                .background(colors.stock, shape)
                .border(1.dp, colors.cardRule, shape)
                .padding(start = box.margin.dp, end = box.margin.dp, top = box.margin.dp),
        ) {
            Box(Modifier.size(box.windowWidth.dp, box.windowHeight.dp).clip(shape).background(tint.copy(alpha = 0.18f))) {
                url?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(box.windowWidth.dp, box.windowHeight.dp))
                }
            }
            Column(
                Modifier.width(box.windowWidth.dp).height(box.strip.dp),
                verticalArrangement = Arrangement.spacedBy((box.strip * 0.18f).dp, Alignment.CenterVertically),
            ) {
                val line = maxOf(1f, height.value * 0.018f).dp
                Box(Modifier.fillMaxWidth(0.72f).height(line).background(colors.ruleVariant, CircleShape))
                Box(Modifier.fillMaxWidth(0.46f).height(line).background(colors.ruleVariant, CircleShape))
            }
        }
    }
}

private const val TAG = "wrap"
```

  **Verify the names before you compile.** Adjust to what exists and record any rename in the commit body:
  - `PebblesTheme.spine.colors.stock`, `.cardRule` and an `outlineVariant`-like rule colour. iOS uses `outlineVariant` for the caption lines; read `core/designsystem/SpineColors.kt` and pick the role mapped to `outlineVariant`.
  - `SnapUrls.thumb`.
  - `LocalSnapURLCache`'s package.
  - `SnapAspectCache.cached`/`ratio` returning `Float?`.

  `MaterialTheme.shapes.extraSmall` keeps `ThemeLiteralsTest` green, since no literal radius is used.

- [ ] **Step 4: Run the test; it passes.** `./gradlew :app:testDebugUnitTest --tests "*MilestoneSnapTest"`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/features/path/wrap/month/MilestoneSnap.kt \
  app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/MilestoneSnapTest.kt
git commit -m "feat(android): frame a milestone's photo as a small polaroid"
```

### Task 6: Milestones date first, in their stones

**Files:**
- Modify: `features/path/wrap/month/MilestonesSection.kt`
- Modify: `features/path/wrap/month/MonthPage.kt` (pass `aspect`)
- Modify: `features/path/PathScreen.kt` (pass `aspect` to `MonthPage`)
- Modify: `app/src/debug/kotlin/app/pbbls/android/SpineLabWeek.kt` and `app/src/screenshotTest/kotlin/app/pbbls/android/MonthPageScreenshots.kt` only if their `MonthPage` calls need the new parameter. It defaults to `null`, so they shouldn't.

Port of iOS `MilestonesSection.row` (list size).

- [ ] **Step 1: Thread `aspect`.**
  - `MonthPage` gains `aspect: SnapAspectCache? = null` and passes it to `MilestonesSection`.
  - In `PathContent`'s `PathPage.Month` branch, pass `aspect = aspect`.

- [ ] **Step 2: Rewrite the row.** In `MilestonesSection.kt`, replace the `StoneSeal` leading and the souls trailing:

```kotlin
private val GLYPH = 14.dp
private const val GLYPHS = 4

/** A list row: tall enough for the stones' rings to clear their neighbours' (iOS `listRow`). */
private val LIST_ROW = 56.dp
/** The list's stones, about the seals' size they replace (iOS `listStone`). */
private const val LIST_STONE = 0.62f
/** The polaroid's box height in the list (iOS `listSnap`). */
private val LIST_SNAP = 40.dp
/** The stone column past the largest stone: room for its rings (iOS `ringRoom`). */
private const val RING_ROOM = 1.3f
/** The milestones' rings, quieter than the Spine's (iOS `MilestoneStone.rings`). */
internal const val MILESTONE_RINGS = 0.45f
```

  and, inside the `forEach`:

```kotlin
                val stoneWidth = StoneGeometry.STONE_WIDTH_DP.getValue(moment.valence.sizeGroup) * LIST_STONE
                val column = StoneGeometry.STONE_WIDTH_DP.getValue(ValenceSizeGroup.LARGE) * LIST_STONE * RING_ROOM
                val seed = remember(moment.pebble?.id, moment.id) { UuidHash.fnv1a(moment.pebble?.id ?: moment.id) }
                val press = rememberStonePressState()
                MarkRow(
                    title = name,
                    titleStyle = type.contentsName(nameType.fontWeight, nameType.italic),
                    subtitle = date,
                    subtitleFirst = true,
                    minHeight = LIST_ROW,
                    markWidth = column.dp,
                    description = "$date, $name",
                    leading = {
                        Box(Modifier.width(column.dp), contentAlignment = Alignment.Center) {
                            LitStone(
                                valence = moment.valence,
                                palette = lookups.paletteFor(moment),
                                glyph = moment.pebble?.glyph,
                                seed = seed,
                                press = press,
                                width = stoneWidth.dp,
                                rings = MILESTONE_RINGS,
                            )
                        }
                    },
                    trailing = {
                        val prefix = moment.firstSnapPath
                        if (prefix != null) {
                            MilestoneSnap(prefix, tint = colors.ink3, height = LIST_SNAP, aspect = aspect)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                souls.take(GLYPHS).forEach { SoulMark(it.glyph, colors.ink2, Modifier.size(GLYPH)) }
                            }
                        }
                    },
                    onClick = { onOpen(moment) },
                )
```

  - The description becomes "date, name", as iOS reads it.
  - Imports: `UuidHash` (`core.common`), `LitStone`, `rememberStonePressState`, `StoneGeometry` (`core.ui.render.stone`), `ValenceSizeGroup` (find its package with `grep -rn "enum class ValenceSizeGroup"`), `remember`, `Box`, `width`, `Alignment`.
  - Remove the `StoneSeal`/`SealSize` imports if unused.
  - Update the section's KDoc: "each its lit stone, its date over its name, and its photo as a polaroid or its souls".

- [ ] **Step 3: Compile and run the unit tests.** `./gradlew :app:compileDebugKotlin :app:compileDebugScreenshotTestKotlin :app:testDebugUnitTest`.

- [ ] **Step 4: Look at it.** Install, open the Spine Lab's Month tab, and screenshot it:

```bash
$ANDROID_HOME/platform-tools/adb exec-out screencap -p > /private/tmp/claude-503/-Users-alexis-code-pbbls/244d86f9-5026-4908-b0a0-92cd6a79f12a/scratchpad/month-content.png
```

  Read the PNG and check:
  - the 28 September week is muted, with a dotted stone and "No pebble.";
  - the milestones show their date first and their lit stones with faint rings;
  - the fixture moments with a `firstSnapPath` show a polaroid bed (the Lab has no URL cache, so a tinted bed).

  If the phone is locked, ask the maintainer to unlock it.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/features/path/wrap/month/MilestonesSection.kt \
  app/src/main/kotlin/app/pbbls/android/features/path/wrap/month/MonthPage.kt \
  app/src/main/kotlin/app/pbbls/android/features/path/PathScreen.kt
git commit -m "feat(android): set a month's milestones in their own stones, dated first"
```

### Task 7: Ship Part 1

- [ ] **Gate:** `./gradlew ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:compileDebugScreenshotTestKotlin`, all green. The `MonthPage*` references will differ on CI; that is this part's intended change.
- [ ] **Device:** `./gradlew :app:installDebug`. Then tell the maintainer what to try:
  - a real past month on the Path: every week listed, an empty week muted with no tap;
  - the milestones dated first, in their stones, with their photos as polaroids;
  - the Lab's Month tab.
- [ ] **PR (the maintainer runs it):** prepare `/private/tmp/claude-503/-Users-alexis-code-pbbls/244d86f9-5026-4908-b0a0-92cd6a79f12a/scratchpad/ship-1242-part1.sh` with:
  - `gh stack submit --auto`;
  - then, through REST (`gh pr edit` failed on GraphQL during #1247):
    - title `feat(android): show every week and each milestone's stone in the month`;
    - body starting "Part 1 of 8 for #1242" (no `Resolves`; only Part 8 resolves);
    - labels `feat`, `ui`, `android`, `no-lab-note`;
    - milestone `M64 · New Path`;
  - then `rebaseline-screenshots` in its own call.

---
# Part 2 — the engine and the cover (`feat/1242-android-intro-engine`)

D18–D20. The score's vocabulary and the cover's beats, the curves, the ledger, the clock, the policy, the stage and the three haptic tiers. The cover plays in the Spine Lab's Month tab, with Replay and a tier override. The Path is untouched.

Start with `gh stack add feat/1242-android-intro-engine` from Part 1's branch.

All test files below go under `app/src/test/kotlin/app/pbbls/android/features/path/wrap/intro/` (package `app.pbbls.android.features.path.wrap.intro`) unless stated.

### Task 8: The reveal curves (core)

**Files:**
- Create: `core/designsystem/RevealMotion.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/core/designsystem/RevealMotionTest.kt`

Port of iOS `IntroMotion` (`apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroMotion.swift`) curves; test port of `IntroMotionTests.eases`/`.pop`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package app.pbbls.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `IntroMotionTests.eases` and `.pop` (#1241). */
class RevealMotionTest {
    @Test
    fun `the eases run from 0 to 1, and the inverse undoes the in-out`() {
        assertEquals(0.0, RevealMotion.easeOut(0.0), 0.0)
        assertEquals(1.0, RevealMotion.easeOut(1.0), 0.0)
        assertEquals(0.0, RevealMotion.easeInOut(0.0), 0.0)
        assertEquals(1.0, RevealMotion.easeInOut(1.0), 1e-15)
        var x = 0.0
        while (x <= 1.0) {
            assertEquals(x, RevealMotion.easeInOutInverse(RevealMotion.easeInOut(x)), 1e-9)
            x += 0.05
        }
    }

    @Test
    fun `a pop grows from 0_4 to 1 past about 1_08, opaque after its first 120 ms`() {
        assertEquals(0.4, RevealMotion.popScale(0.0), 1e-12)
        assertEquals(1.0, RevealMotion.popScale(1.0), 0.0)
        assertEquals(1.079, RevealMotion.popScale(5.0 / 9.0), 0.001)
        assertEquals(0.0, RevealMotion.popOpacity(0.0), 0.0)
        assertEquals(1.0, RevealMotion.popOpacity(0.12 / 0.22), 1e-12)
    }
}
```

- [ ] **Step 2: Run it; it fails.**

- [ ] **Step 3: Write `RevealMotion.kt`.**

```kotlin
package app.pbbls.android.core.designsystem

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos

/**
 * How a mark lands when it is revealed (#1242, the month intro; iOS `IntroMotion`'s curves), from its
 * linear progress 0…1. Generic, so `core/` components apply them to the progress a feature hands in.
 */
object RevealMotion {
    /** A pop is opaque after its first 120 ms of 220. */
    private const val POP_FADE = 0.12 / 0.22

    /** Ease-out-back's constant: the pop's scale peaks near 1.08. */
    private const val BACK = 2.0

    fun clamp(t: Double): Double = t.coerceIn(0.0, 1.0)

    /** Cubic ease-out. */
    fun easeOut(t: Double): Double {
        val u = 1 - clamp(t)
        return 1 - u * u * u
    }

    /** Sine ease-in-out: slow, fast, slow. Gentler than the cubic, whose middle would crowd 31 ticks under 25 ms. */
    fun easeInOut(t: Double): Double = (1 - cos(PI * clamp(t))) / 2

    /** When, 0…1, [easeInOut] reaches [y]. */
    fun easeInOutInverse(y: Double): Double = acos(1 - 2 * clamp(y)) / PI

    fun popOpacity(t: Double): Double = clamp(t / POP_FADE)

    /** 0.4 to 1, overshooting once (ease-out-back). */
    fun popScale(t: Double): Double {
        val u = clamp(t) - 1
        return 0.4 + 0.6 * (1 + (BACK + 1) * u * u * u + BACK * u * u)
    }
}
```

- [ ] **Step 4: Run it; it passes.**

- [ ] **Step 5: Commit** `feat(android): add the reveal curves the month intro lands marks on`.

### Task 9: The vocabulary, the motion, the tempo

**Files:**
- Create: `features/path/wrap/intro/IntroBeat.kt`, `IntroMotion.kt`, `IntroTempo.kt`
- Test: `IntroMotionTest.kt`, `IntroTempoTest.kt`

- [ ] **Step 1: Write the failing tests.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroMotionTests.curve` and `IntroScoreSignaturesTests.stampCurves`. */
class IntroMotionTest {
    @Test
    fun `a run's beats go slow, fast, slow, from 0 to its duration`() {
        assertTrue(IntroCurve.times(0, 1.5).isEmpty())
        assertEquals(listOf(0.0), IntroCurve.times(1, 1.5))
        val times = IntroCurve.times(31, 1.5)
        assertEquals(31, times.size)
        assertEquals(0.0, times.first(), 0.0)
        assertEquals(1.5, times.last(), 1e-12)
        val gaps = times.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it > 0 })
        val middle = gaps[gaps.size / 2]
        assertTrue(middle < gaps.first() && middle < gaps.last())
        // The fastest stretch still clears the haptics' 25 ms floor.
        assertTrue(gaps.min() > 0.025)
    }

    @Test
    fun `a stamp comes in large and clear, presses past its size at impact, and settles`() {
        assertEquals(1.25, IntroMotion.stampScale(0.0), 0.0)
        assertTrue(IntroMotion.stampScale(IntroMotion.STAMP_IMPACT) < 1)
        assertEquals(1.0, IntroMotion.stampScale(1.0), 1e-12)
        assertEquals(0.0, IntroMotion.stampOpacity(0.0), 0.0)
        assertEquals(1.0, IntroMotion.stampOpacity(IntroMotion.STAMP_IMPACT), 0.0)
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `IntroTempoTests.factors`, `.neverFaster`, `.scaled`, and `IntroScoreSignaturesTests.scaledSwell`. */
class IntroTempoTest {
    @Test
    fun `the cover and a section each have their factor`() {
        assertEquals(1.45, IntroTempo.standard.factor(IntroSequence.Cover), 0.0)
        assertEquals(2.5, IntroTempo.standard.factor(IntroSequence.Section(MonthSection.GEM)), 0.0)
    }

    @Test
    fun `a factor below 1 plays at the score's own pace`() {
        val tempo = IntroTempo(cover = 0.5, sections = 0.2)
        assertEquals(1.0, tempo.factor(IntroSequence.Cover), 0.0)
        assertEquals(1.0, tempo.factor(IntroSequence.Section(MonthSection.WEEKS)), 0.0)
    }

    @Test
    fun `a scaled beat starts and lands that many times later, with its key, tick and swell`() {
        val swell = IntroSwell(0.1f, 0.5f, 0.3f)
        val beat = IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light, swell).scaled(2.5)
        assertEquals(0.8, beat.at, 1e-12)
        assertEquals(0.7, beat.duration, 1e-12)
        assertEquals(IntroKey.Row(0), beat.key)
        assertEquals(IntroTick.light, beat.tick)
        assertEquals(swell, beat.swell)
    }
}
```

- [ ] **Step 2: Run them; they fail.**

- [ ] **Step 3: Write `IntroBeat.kt`.** It holds every key the stack uses, so later parts only add builders.

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection

/** What enters in a month's intro (#1242; iOS `IntroSequence`): the cover, a section's card, or the end card (D14). */
sealed interface IntroSequence {
    data object Cover : IntroSequence

    data class Section(
        val section: MonthSection,
    ) : IntroSequence

    data object End : IntroSequence
}

/** One element of a sequence, named by what it is; the figures ask for these (iOS `IntroKey`, case for case). */
sealed interface IntroKey {
    // The cover: the month's name, the garland's course and leaf, the souls' glyphs, the texts; a day's
    // slot (its socket or its first seal), a stacked seal (day, index), a day's numeral.
    data object Kicker : IntroKey

    data object MonthName : IntroKey

    data object Year : IntroKey

    data object Course : IntroKey

    data object Leaf : IntroKey

    data object Summary : IntroKey

    data object Names : IntroKey

    data object Colophon : IntroKey

    data class Slot(val day: Int) : IntroKey

    data class Seal(val day: Int, val index: Int) : IntroKey

    data class Numeral(val day: Int) : IntroKey

    data class Soul(val index: Int) : IntroKey

    // Any card: the insights head, the head, the lead line, more, a row. The two heads are drawn from
    // the start (D8): their beats carry only the arrival's tick.
    data object Insights : IntroKey

    data object Header : IntroKey

    data object Lead : IntroKey

    data object More : IntroKey

    data class Row(val row: Int) : IntroKey

    // A person's row: its glyph, its name, its ring's line, a day on its ring (row, day).
    data class Glyph(val row: Int) : IntroKey

    data class Name(val row: Int) : IntroKey

    data class Ring(val row: Int) : IntroKey

    data class Dot(val row: Int, val day: Int) : IntroKey

    // The gem: the blank stone, the count, a facet, a family's name and share, the count's tally (1…n).
    data object Blank : IntroKey

    data object Count : IntroKey

    data class Facet(val id: String) : IntroKey

    data class Label(val slug: String) : IntroKey

    data class Tally(val pebble: Int) : IntroKey

    // Tone and hours: the sweep, the dial's face, a day of the chart, a stone by its rank in the day.
    data object Sweep : IntroKey

    data object Dial : IntroKey

    data class Day(val day: Int) : IntroKey

    data class Stone(val rank: Int) : IntroKey

    // The signatures (D10, D16, D17): a week's cairn stone by stone (row, stone), a column's date, a soul
    // of a row (row, index); a milestone's stamp, its impact, its date; a domain's gauge filling, full;
    // a week column's empty day (week, weekday) and a pebble of a day (week, weekday, index).
    data class Cairn(val row: Int, val stone: Int) : IntroKey

    data class Words(val row: Int) : IntroKey

    data class Mark(val row: Int, val index: Int) : IntroKey

    data class Stamp(val row: Int) : IntroKey

    data class Impact(val row: Int) : IntroKey

    data class Date(val row: Int) : IntroKey

    data class Gauge(val row: Int) : IntroKey

    data class Full(val row: Int) : IntroKey

    data class Rest(val week: Int, val weekday: Int) : IntroKey

    data class Pip(val week: Int, val weekday: Int, val index: Int) : IntroKey

    // The end card's replay (D14).
    data object Replay : IntroKey
}

/** How firmly a beat ticks, 0…1 (iOS `IntroTick`; on Android, D18 maps sharpness to a primitive and intensity to its scale). */
data class IntroTick(
    val intensity: Float,
    val sharpness: Float,
) {
    companion object {
        /** An empty day on the garland. */
        val faint = IntroTick(0.22f, 0.25f)

        /** Rows, texts, glyphs, ring dots, tone days, hour stones. */
        val light = IntroTick(0.40f, 0.45f)
        val facet = IntroTick(0.60f, 0.55f)

        /** The garland's leaf, tying the ring. */
        val knot = IntroTick(0.75f, 0.35f)

        /** The gem's count. */
        val firm = IntroTick(0.90f, 0.70f)

        /** A card arriving on top (D8): firm, rounded. */
        val arrive = IntroTick(0.6f, 0.15f)

        /** A seal on the garland, firmer with its pebble's intensity, 1…3. */
        fun seal(intensity: Int) = IntroTick(listOf(0.50f, 0.68f, 0.85f)[(intensity - 1).coerceIn(0, 2)], 0.60f)

        /** A week's cairn, stone [index] from the bottom: each a little firmer (D10). */
        fun cairn(index: Int) = IntroTick((0.32f + 0.07f * index).coerceAtMost(0.8f), 0.5f)
    }
}

/** A continuous vibration under a beat (D15): over its duration, from [from] to [to], at one sharpness. */
data class IntroSwell(
    val from: Float,
    val to: Float,
    val sharpness: Float,
)

/** One beat: [key] starts landing [at] seconds into its sequence and takes [duration]; it ticks [tick] as it starts and swells [swell] while it lands. */
data class IntroBeat(
    val key: IntroKey,
    val at: Double,
    val duration: Double,
    val tick: IntroTick? = null,
    val swell: IntroSwell? = null,
) {
    /** The beat [factor] times slower: its time and its duration (D9). */
    fun scaled(factor: Double) = copy(at = at * factor, duration = duration * factor)
}

/** Each key's linear progress, 0…1, for a figure to draw (D6). The default everywhere is [settled]: today's render. */
fun interface IntroReveal {
    operator fun invoke(key: IntroKey): Float

    companion object {
        val settled = IntroReveal { 1f }
        val hidden = IntroReveal { 0f }
    }
}
```

  ktlint wants each one-line data class on its own lines with a blank line between members; run `./gradlew ktlintFormat` after writing and keep its output.

- [ ] **Step 4: Write `IntroMotion.kt`.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.RevealMotion

/** How long a key takes to land, and the stamp's curves (iOS `IntroMotion`); the eases are [RevealMotion]'s. */
object IntroMotion {
    /** A Compose part: fades in and rises [riseOffset]. */
    const val RISE = 0.28

    /** A mark in a figure: grows from 0.4, overshooting once. */
    const val POP = 0.22

    /** A gem facet: its colour over the blank paper. */
    const val LIGHT = 0.18

    val riseOffset = 6.dp

    /** When, 0…1 of its beat, a stamp meets the paper: its tick's moment. */
    const val STAMP_IMPACT = 0.6

    /** A stamp's press past its size at impact. */
    private const val STAMP_SQUASH = 0.04

    /** A stamp's scale: from 1.25 down to its impact, a slight squash there, back to 1. */
    fun stampScale(t: Double): Double {
        val c = RevealMotion.clamp(t)
        if (c < STAMP_IMPACT) {
            val u = c / STAMP_IMPACT
            return 1.25 - (0.25 + STAMP_SQUASH) * u * u
        }
        return 1 - STAMP_SQUASH * (1 - RevealMotion.easeOut((c - STAMP_IMPACT) / (1 - STAMP_IMPACT)))
    }

    /** A stamp's opacity: there by its impact. */
    fun stampOpacity(t: Double): Double = RevealMotion.clamp(t / STAMP_IMPACT)
}

/** The slow-fast-slow spacing of a run of beats: beat i of n lands when the eased run reaches i / (n − 1). */
object IntroCurve {
    fun times(
        count: Int,
        duration: Double,
    ): List<Double> =
        when {
            count <= 0 -> emptyList()
            count == 1 -> listOf(0.0)
            else -> List(count) { duration * RevealMotion.easeInOutInverse(it / (count - 1.0)) }
        }
}
```

- [ ] **Step 5: Write `IntroTempo.kt`.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

/**
 * How fast the month's intro plays (D9): the clock scales a sequence's beats by its factor as it
 * starts it. Factors below 1 read as 1: the score decides which stacked seals tick at ×1, against
 * the haptics' floor, so only a slower tempo keeps them clear of it.
 */
data class IntroTempo(
    /** ×1.45 takes the garland from 1.5 s to about 2.2 s. */
    val cover: Double = 1.45,
    val sections: Double = 2.5,
) {
    fun factor(sequence: IntroSequence): Double = maxOf(1.0, if (sequence == IntroSequence.Cover) cover else sections)

    companion object {
        val standard = IntroTempo()
    }
}
```

- [ ] **Step 6: Run the tests; they pass. `ktlintFormat`, then commit** `feat(android): add the month intro's vocabulary, motion and tempo`.

### Task 10: The haptics' pure rules

**Files:**
- Create: `features/path/wrap/intro/IntroHaptics.kt`
- Test: `IntroHapticsTest.kt`

Port of iOS `IntroHaptics.events`/`.swells`/`IntroHapticSwell.sounding`, and of `IntroHapticsTests.floor`.

- [ ] **Step 1: Write the failing test** (the cover's tests join it in Task 11).

```kotlin
package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroHapticsTests` (#1241 D5, D15). */
class IntroHapticsTest {
    @Test
    fun `each ticking beat plays at its time; one closer than 25 ms to the last kept is dropped`() {
        val beats =
            listOf(
                IntroBeat(IntroKey.Row(1), 0.05, 0.28, IntroTick.facet),
                IntroBeat(IntroKey.Header, 0.0, 0.28, IntroTick.light),
                IntroBeat(IntroKey.Lead, 0.01, 0.28),
                IntroBeat(IntroKey.Row(0), 0.02, 0.28, IntroTick.light),
            )
        assertEquals(
            listOf(IntroHapticEvent(0.0, IntroTick.light), IntroHapticEvent(0.05, IntroTick.facet)),
            IntroHaptics.events(beats),
        )
    }

    @Test
    fun `a swelling beat plays as a swell over its duration, never as a tick, and falls silent a floor early`() {
        val swell = IntroSwell(0.15f, 0.75f, 0.3f)
        val beats =
            listOf(
                IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light),
                IntroBeat(IntroKey.Gauge(0), 0.41, 0.7, swell = swell),
                IntroBeat(IntroKey.Full(0), 1.11, 0.0, IntroTick.light),
            )
        val swells = IntroHaptics.swells(beats)
        assertEquals(listOf(IntroHapticSwell(0.41, 0.7, swell)), swells)
        assertEquals(IntroHaptics.FLOOR, swells[0].duration - swells[0].sounding, 1e-12)
        assertEquals(listOf(0.32, 1.11), IntroHaptics.events(beats).map { it.at })
        // A beat with nothing to swell over plays no swell.
        assertTrue(IntroHaptics.swells(listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.0, swell = swell))).isEmpty())
    }
}
```

- [ ] **Step 2: Run it; it fails.**

- [ ] **Step 3: Write `IntroHaptics.kt`.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import java.time.YearMonth

/** One tick of a sequence's pattern. */
data class IntroHapticEvent(
    val at: Double,
    val tick: IntroTick,
)

/** One swell of a sequence's pattern (D15): a continuous vibration from [at] for [duration]. */
data class IntroHapticSwell(
    val at: Double,
    val duration: Double,
    val swell: IntroSwell,
) {
    /** How long it sounds: it stops a floor before its beat ends, so the tick closing the beat stands alone. */
    val sounding: Double get() = maxOf(duration - IntroHaptics.FLOOR, duration / 2)
}

/** The month intro's haptic rules, pure (#1242 D18; iOS `IntroHaptics`' static half). */
object IntroHaptics {
    /** Two ticks closer than this blur into one buzz: the later is dropped (its visual beat stays). */
    const val FLOOR = 0.025

    /** The ticks to play: each ticking beat at its time, in time order, dropping one closer than [FLOOR] to the last kept. */
    fun events(beats: List<IntroBeat>): List<IntroHapticEvent> {
        val kept = mutableListOf<IntroHapticEvent>()
        beats.sortedBy { it.at }.forEach { beat ->
            val tick = beat.tick ?: return@forEach
            val last = kept.lastOrNull()
            if (last == null || beat.at - last.at >= FLOOR) kept += IntroHapticEvent(beat.at, tick)
        }
        return kept
    }

    /** The swells to play: each swelling beat over its duration, in time order. */
    fun swells(beats: List<IntroBeat>): List<IntroHapticSwell> =
        beats
            .mapNotNull { beat -> beat.swell?.takeIf { beat.duration > 0 }?.let { IntroHapticSwell(beat.at, beat.duration, it) } }
            .sortedBy { it.at }
}

/** What the intro's clock asks of the haptics; the tests stand in a spy. A page stops only its own. */
interface IntroHapticPlaying {
    /** Plays [beats]' ticks and swells as one voice, starting at [startNanos] (`System.nanoTime()`'s base). */
    fun play(
        sequence: IntroSequence,
        month: YearMonth,
        beats: List<IntroBeat>,
        startNanos: Long,
    )

    fun stop(
        sequence: IntroSequence,
        month: YearMonth,
    )

    /** One tick now, outside any sequence (a throw landing on a card that plays nothing, D11). */
    fun tick(tick: IntroTick)

    /** A frame of the playing [sequence]: the frame-clock tier fires from here; the others ignore it. */
    fun frame(
        sequence: IntroSequence,
        month: YearMonth,
        nanos: Long,
    ) {}
}
```

- [ ] **Step 4: Run it; it passes. Commit** `feat(android): add the month intro's haptic floor and swells`.

### Task 11: The cover's score

**Files:**
- Create: `features/path/wrap/intro/IntroScore.kt`
- Test: `IntroScoreCoverTest.kt`; extend `IntroHapticsTest.kt`

Port of iOS `IntroScore.cover` and `stackedSeals` (`IntroScore.swift`); tests port `IntroScoreCoverTests` and the garland cases of `IntroHapticsTests`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.model.wrap.CoverPebble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreCoverTests` (#1241 §Choreography, the cover). */
class IntroScoreCoverTest {
    private fun pebble(intensity: Int) = CoverPebble("m", null, intensity, ValencePolarity.HIGHLIGHT)

    private fun List<IntroBeat>.of(key: IntroKey) = first { it.key == key }

    @Test
    fun `one beat per day from the 1st, an empty day faint, a seal by its intensity, a stacked seal a step later`() {
        val days = listOf(CoverDay(0, listOf(pebble(3))), CoverDay(4, listOf(pebble(1), pebble(2))))
        val beats = IntroScore.cover(days, daysInMonth = 31, numbered = listOf(0, 4, 30), souls = 0, lines = emptyList())
        val slots = (0 until 31).map { beats.of(IntroKey.Slot(it)) }
        assertTrue(slots.zipWithNext { a, b -> b.at > a.at }.all { it })
        assertEquals(IntroScore.GARLAND_AT, slots.first().at, 0.0)
        assertEquals(IntroTick.seal(3), slots[0].tick)
        assertEquals(IntroTick.faint, slots[1].tick)
        assertEquals(IntroScore.STACK_GAP, beats.of(IntroKey.Seal(4, 1)).at - slots[4].at, 1e-12)
        val numerals = beats.mapNotNull { (it.key as? IntroKey.Numeral)?.day }
        assertEquals(listOf(0, 4, 30), numerals)
        assertEquals(slots[4].at, beats.of(IntroKey.Numeral(4)).at, 0.0)
    }

    @Test
    fun `a stacked seal on the 1st ticks on its own, the slot keeping its first seal's`() {
        val beats = IntroScore.cover(listOf(CoverDay(0, listOf(pebble(1), pebble(3)))), 31, emptyList(), 0, emptyList())
        assertEquals(IntroTick.seal(1), beats.of(IntroKey.Slot(0)).tick)
        assertEquals(IntroTick.seal(3), beats.of(IntroKey.Seal(0, 1)).tick)
    }

    @Test
    fun `a stacked seal mid-garland, too close to the next day, is silent; its day ticks its strongest seal`() {
        val beats = IntroScore.cover(listOf(CoverDay(15, listOf(pebble(1), pebble(3)))), 31, emptyList(), 0, emptyList())
        val slot = beats.of(IntroKey.Slot(15))
        val stacked = beats.of(IntroKey.Seal(15, 1))
        assertNull(stacked.tick)
        assertEquals(IntroScore.STACK_GAP, stacked.at - slot.at, 1e-12)
        assertEquals(IntroTick.seal(3), slot.tick)
    }

    @Test
    fun `the name before the garland; the leaf ties it; then the souls and the lines, in order`() {
        val beats = IntroScore.cover(emptyList(), 30, emptyList(), souls = 2, lines = listOf(IntroKey.Summary, IntroKey.Colophon))
        val name = beats.of(IntroKey.MonthName)
        assertEquals(IntroTick.light, name.tick)
        assertTrue(name.at < IntroScore.GARLAND_AT)
        val leaf = beats.of(IntroKey.Leaf)
        assertEquals(IntroTick.knot, leaf.tick)
        assertTrue(leaf.at > beats.of(IntroKey.Slot(29)).at)
        assertEquals(
            listOf(IntroKey.Soul(0), IntroKey.Soul(1), IntroKey.Summary, IntroKey.Colophon),
            beats.filter { it.at > leaf.at }.map { it.key },
        )
        assertFalse(beats.any { it.key == IntroKey.Names })
        assertEquals(beats.last().at + IntroMotion.RISE, IntroScore.duration(beats), 1e-12)
    }
}
```

  Append to `IntroHapticsTest`:

```kotlin
    @Test
    fun `a 31-day garland keeps its name, every day and its leaf`() {
        val beats = IntroScore.cover(emptyList(), 31, emptyList(), 0, emptyList())
        assertEquals(33, IntroHaptics.events(beats).size)
    }

    @Test
    fun `a 31-day garland with two pebbles every day still ticks every day, its name and its leaf`() {
        val pebble = { i: Int -> app.pbbls.android.core.model.wrap.CoverPebble("m", null, i, app.pbbls.android.core.model.ValencePolarity.HIGHLIGHT) }
        val days = (0 until 31).map { app.pbbls.android.core.model.wrap.CoverDay(it, listOf(pebble(1), pebble(3))) }
        val beats = IntroScore.cover(days, 31, emptyList(), 0, emptyList())
        val ticked = IntroHaptics.events(beats).map { it.at }.toSet()
        ((0 until 31).map { IntroKey.Slot(it) } + listOf(IntroKey.MonthName, IntroKey.Leaf)).forEach { key ->
            assertTrue("$key lost its tick", beats.first { it.key == key }.at in ticked)
        }
    }
```

  (Use imports rather than the fully-qualified names once ktlint runs.)

- [ ] **Step 2: Run them; they fail.**

- [ ] **Step 3: Write `IntroScore.kt`.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.model.wrap.CoverPebble

/**
 * The beats of each sequence of a month's intro (#1242; iOS `IntroScore`, value for value). Pure, over
 * what the views already show after their own lookups filtering, so a beat never names a row the page
 * doesn't draw. Every constant lives here; the sections' builders are extensions in the files beside it.
 */
object IntroScore {
    const val NAME_AT = 0.12
    const val YEAR_AT = 0.24
    const val GARLAND_AT = 0.4
    const val GARLAND_SPAN = 1.5
    const val STACK_GAP = 0.035
    const val LEAF_AFTER = 0.12
    const val SOULS_AFTER = 0.2
    const val SOUL_GAP = 0.06
    const val LINE_GAP = 0.14

    /** When a sequence has landed: its latest beat's end. */
    fun duration(beats: List<IntroBeat>): Double = beats.maxOfOrNull { it.at + it.duration } ?: 0.0

    /**
     * The cover: the month's name; the garland's days from the 1st (an empty day's socket, or its seals,
     * a stacked one a step after the first) with their numerals, under the course drawing round; the
     * leaf; then the souls' glyphs and the [lines] shown (⊆ summary, names, colophon).
     */
    fun cover(
        days: List<CoverDay>,
        daysInMonth: Int,
        numbered: List<Int>,
        souls: Int,
        lines: List<IntroKey>,
    ): List<IntroBeat> {
        val beats =
            mutableListOf(
                IntroBeat(IntroKey.Kicker, 0.0, IntroMotion.RISE),
                IntroBeat(IntroKey.MonthName, NAME_AT, IntroMotion.RISE, IntroTick.light),
                IntroBeat(IntroKey.Year, YEAR_AT, IntroMotion.RISE),
                IntroBeat(IntroKey.Course, GARLAND_AT, GARLAND_SPAN),
            )
        val seals = days.distinctBy { it.day }.associate { it.day to it.pebbles }
        val numerals = numbered.toSet()
        val slots = IntroCurve.times(daysInMonth, GARLAND_SPAN).map { GARLAND_AT + it }
        val leafAt = GARLAND_AT + GARLAND_SPAN + LEAF_AFTER
        slots.forEachIndexed { day, at ->
            val pebbles = seals[day].orEmpty()
            val next = slots.getOrElse(day + 1) { leafAt }
            val stacked = stackedSeals(pebbles, day, at, next)
            // A day whose stacked seals can't all tick taps once, at its strongest seal.
            val silenced = stacked.any { it.tick == null }
            val firmness = if (silenced) pebbles.maxOfOrNull { it.intensity } else pebbles.firstOrNull()?.intensity
            beats += IntroBeat(IntroKey.Slot(day), at, IntroMotion.POP, firmness?.let(IntroTick::seal) ?: IntroTick.faint)
            beats += stacked
            if (day in numerals) beats += IntroBeat(IntroKey.Numeral(day), at, IntroMotion.RISE)
        }
        var at = leafAt
        beats += IntroBeat(IntroKey.Leaf, at, IntroMotion.POP, IntroTick.knot)
        at += SOULS_AFTER
        repeat(souls) { soul ->
            beats += IntroBeat(IntroKey.Soul(soul), at, IntroMotion.POP, IntroTick.light)
            at += SOUL_GAP
        }
        lines.forEach { line ->
            beats += IntroBeat(line, at, IntroMotion.RISE, IntroTick.light)
            at += LINE_GAP
        }
        return beats
    }

    /**
     * A day's stacked seals, a step apart after its slot. Each ticks only clear of the tick before it and
     * of the next tick in the score (the next day's slot, or the leaf after the last day), by the floor:
     * otherwise the floor would drop it or that slot.
     */
    private fun stackedSeals(
        pebbles: List<CoverPebble>,
        day: Int,
        slot: Double,
        next: Double,
    ): List<IntroBeat> {
        var lastTick = slot
        return pebbles.withIndex().drop(1).map { (index, pebble) ->
            val at = slot + STACK_GAP * index
            val fits = at - lastTick >= IntroHaptics.FLOOR && next - at >= IntroHaptics.FLOOR
            if (fits) lastTick = at
            IntroBeat(IntroKey.Seal(day, index), at, IntroMotion.POP, if (fits) IntroTick.seal(pebble.intensity) else null)
        }
    }
}
```

- [ ] **Step 4: Run them; they pass. Commit** `feat(android): score the month cover's intro`.

### Task 12: The ledger and the policy

**Files:**
- Create: `features/path/wrap/intro/IntroLedger.kt`, `IntroPolicy.kt`, `core/designsystem/TouchExploration.kt`
- Test: `IntroRulesTest.kt`

Port of iOS `IntroLedger`, `IntroPolicy`; tests port `IntroRulesTests`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** iOS `IntroRulesTests` (#1241 D3, D11). */
class IntroRulesTest {
    @Test
    fun `no intro under reduce motion, while touch exploration runs, or at a large text size`() {
        assertTrue(IntroPolicy.plays(reduceMotion = false, touchExploration = false, largeText = false))
        assertFalse(IntroPolicy.plays(reduceMotion = true, touchExploration = false, largeText = false))
        assertFalse(IntroPolicy.plays(reduceMotion = false, touchExploration = true, largeText = false))
        assertFalse(IntroPolicy.plays(reduceMotion = false, touchExploration = false, largeText = true))
        assertTrue(IntroPolicy.isLargeText(1.5f))
        assertFalse(IntroPolicy.isLargeText(1.3f))
    }

    @Test
    fun `the ledger holds a month's sequence once; a reset forgets that month only`() {
        val ledger = IntroLedger()
        val october = YearMonth.of(2026, 10)
        val september = YearMonth.of(2026, 9)
        val gem = IntroSequence.Section(MonthSection.GEM)
        ledger.record(october, IntroSequence.Cover)
        ledger.record(september, gem)
        assertTrue(ledger.contains(october, IntroSequence.Cover))
        assertFalse(ledger.contains(october, gem))
        ledger.reset(october)
        assertFalse(ledger.contains(october, IntroSequence.Cover))
        assertTrue(ledger.contains(september, gem))
    }
}
```

- [ ] **Step 2: Run it; it fails.**

- [ ] **Step 3: Write the three files.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import java.time.YearMonth

/** The sequences that have played this launch (D3): a month's sequence plays once. `PathViewModel` holds the app's; the Lab its own. */
class IntroLedger {
    private data class Entry(
        val month: YearMonth,
        val sequence: IntroSequence,
    )

    private val played = mutableSetOf<Entry>()

    fun contains(
        month: YearMonth,
        sequence: IntroSequence,
    ): Boolean = Entry(month, sequence) in played

    fun record(
        month: YearMonth,
        sequence: IntroSequence,
    ) {
        played += Entry(month, sequence)
    }

    /** Forgets one month, so its intro plays again (Replay, the Lab). */
    fun reset(month: YearMonth) {
        played.removeAll { it.month == month }
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.intro

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.designsystem.rememberTouchExploration

/**
 * Whether the intro plays at all (#1242 D20; iOS `IntroPolicy`): not under Reduce Motion, not while
 * TalkBack explores by touch, where hidden and moving parts would fight its focus, and not at a large
 * text size, where a card scaled to fit would read too small. The month is then the plain page, settled.
 */
object IntroPolicy {
    /** The Pixel's 1.5, 1.8 and 2.0 steps: past iOS's xxxLarge (1.35×), its accessibility sizes. */
    const val LARGE_TEXT = 1.5f

    fun isLargeText(fontScale: Float): Boolean = fontScale >= LARGE_TEXT

    fun plays(
        reduceMotion: Boolean,
        touchExploration: Boolean,
        largeText: Boolean,
    ): Boolean = !reduceMotion && !touchExploration && !largeText
}

/** [IntroPolicy] over this composition's settings. False in previews (`rememberReduceMotion` is true there). */
@Composable
fun rememberIntroAllowed(): Boolean =
    IntroPolicy.plays(
        reduceMotion = rememberReduceMotion(),
        touchExploration = rememberTouchExploration(),
        largeText = IntroPolicy.isLargeText(LocalDensity.current.fontScale),
    )
```

```kotlin
package app.pbbls.android.core.designsystem

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Whether a screen reader explores by touch (TalkBack), live: false in previews. */
@Composable
fun rememberTouchExploration(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}
```

- [ ] **Step 4: Run it; it passes. Commit** `feat(android): add the month intro's ledger and policy`.

### Task 13: The clock

**Files:**
- Create: `features/path/wrap/intro/IntroClock.kt`
- Test: `IntroClockTest.kt`, extend `IntroTempoTest.kt`; `HapticsSpy.kt` (test helper, same package)

Port of iOS `IntroClock` (`IntroClock.swift`), cut to the deck's needs: one card shown at a time (`show`), beats registered by the card's host (`register`), no viewport (`enter`, `order` are iOS's dead paths and are not ported). Tests port `IntroClockTests` (all but `queue`, `leaving`'s viewport half, `reorder`, `preparesOnlyWhatPlays`, `scheduledEnd`), `IntroLandingTests` and `IntroTempoTests.clockScales`.

- [ ] **Step 1: Write the spy and the failing tests.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import java.time.YearMonth

/** Records what the clock asked of the haptics (iOS `HapticsSpy`). */
class HapticsSpy : IntroHapticPlaying {
    val played = mutableListOf<IntroSequence>()
    val beats = mutableListOf<List<IntroBeat>>()
    val starts = mutableListOf<Long>()
    val stopped = mutableListOf<IntroSequence>()
    val stoppedMonths = mutableListOf<YearMonth>()
    val ticks = mutableListOf<IntroTick>()

    override fun play(sequence: IntroSequence, month: YearMonth, beats: List<IntroBeat>, startNanos: Long) {
        played += sequence
        this.beats += beats
        starts += startNanos
    }

    override fun stop(sequence: IntroSequence, month: YearMonth) {
        stopped += sequence
        stoppedMonths += month
    }

    override fun tick(tick: IntroTick) {
        ticks += tick
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** iOS `IntroClockTests` and `IntroLandingTests` (#1241 D3, D4, D11, §Lifecycle), on the deck's clock. */
class IntroClockTest {
    private val october = YearMonth.of(2026, 10)
    private val base = 1_000_000_000_000L
    private val weeks = IntroSequence.Section(MonthSection.WEEKS)
    private val persons = IntroSequence.Section(MonthSection.PERSONS)

    /** Lands at 0.6 s (0.32 + 0.28). */
    private val beats =
        listOf(
            IntroBeat(IntroKey.Header, 0.0, 0.28, IntroTick.light),
            IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light),
        )

    private fun at(seconds: Double) = base + (seconds * 1e9).toLong()

    private val start = base + IntroClock.LEAD_NANOS

    private fun after(seconds: Double) = start + (seconds * 1e9).toLong()

    /** At tempo 1, so the beats play at the times they name. */
    private fun clock(
        ledger: IntroLedger = IntroLedger(),
        haptics: HapticsSpy? = null,
        month: YearMonth = october,
    ) = IntroClock(month, ledger, haptics, IntroTempo(cover = 1.0, sections = 1.0), now = { base })

    @Test
    fun `a card waits hidden, plays once shown on an active page, and lands at its end`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        assertEquals(IntroClock.Phase.Waiting, clock.phase(IntroSequence.Cover))
        assertEquals(0f, clock.reveal(IntroSequence.Cover, base)(IntroKey.Header))
        clock.isActive = true
        assertEquals(IntroClock.Phase.Playing(start), clock.phase(IntroSequence.Cover))
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
        // The haptics start with the frames.
        assertEquals(listOf(start), spy.starts)
        assertTrue(ledger.contains(october, IntroSequence.Cover))
        // A key reads 0 until its beat, while its sequence plays.
        assertEquals(0f, clock.reveal(IntroSequence.Cover, after(0.1))(IntroKey.Row(0)))
        val reveal = clock.reveal(IntroSequence.Cover, after(0.46))
        assertEquals(1f, reveal(IntroKey.Header))
        assertEquals(0.5f, reveal(IntroKey.Row(0)), 1e-6f)
        // A key the beats don't name has landed.
        assertEquals(1f, reveal(IntroKey.Row(1)))
        clock.advance(after(0.5))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        clock.advance(after(0.7))
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.stopped)
    }

    @Test
    fun `the deck's top card plays; each card shown in turn plays in its turn`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        listOf(IntroSequence.Cover, weeks, persons).forEach { clock.register(it) { beats } }
        clock.isActive = true
        assertEquals(IntroClock.Phase.Waiting, clock.phase(IntroSequence.Cover))
        clock.show(IntroSequence.Cover)
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        clock.advance(at(1.0))
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        // The card beneath never plays on its own.
        assertEquals(IntroClock.Phase.Waiting, clock.phase(weeks))
        clock.show(weeks)
        assertTrue(clock.isPlaying(weeks))
        clock.advance(at(2.0))
        clock.show(persons)
        assertEquals(listOf(IntroSequence.Cover, weeks, persons), spy.played)
    }

    @Test
    fun `a card thrown off mid-play lands, and the next plays; one brought back stays landed`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.register(weeks) { beats }
        clock.isActive = true
        clock.show(IntroSequence.Cover)
        clock.show(weeks)
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.stopped)
        assertTrue(clock.isPlaying(weeks))
        clock.show(IntroSequence.Cover)
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertEquals(IntroClock.Phase.Settled, clock.phase(weeks))
        assertEquals(listOf(IntroSequence.Cover, weeks), spy.played)
        assertTrue(ledger.contains(october, weeks))
    }

    @Test
    fun `a card shown before its beats are registered plays as they arrive, with the latest`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        clock.isActive = true
        clock.show(IntroSequence.Cover)
        assertEquals(IntroClock.Phase.Waiting, clock.phase(IntroSequence.Cover))
        assertTrue(spy.played.isEmpty())
        val slow = listOf(IntroBeat(IntroKey.Header, 0.0, 2.0, IntroTick.light))
        clock.register(IntroSequence.Cover) { slow }
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        assertEquals(listOf(slow), spy.beats)
        // Registered again while it plays: the score in play stands.
        clock.register(IntroSequence.Cover) { beats }
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
        clock.advance(at(1.0))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
    }

    @Test
    fun `a card with no beats to play lands at once, silent`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        clock.register(weeks) { emptyList() }
        clock.isActive = true
        clock.show(weeks)
        assertEquals(IntroClock.Phase.Settled, clock.phase(weeks))
        assertTrue(spy.played.isEmpty())
        assertEquals(1f, clock.reveal(weeks, base)(IntroKey.Header))
    }

    @Test
    fun `a card that leaves while it plays lands; one that waits stays unrecorded`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(weeks) { beats }
        clock.isActive = true
        clock.show(weeks)
        clock.leave(weeks)
        assertEquals(IntroClock.Phase.Settled, clock.phase(weeks))
        assertEquals(listOf(weeks), spy.stopped)
        clock.leave(persons)
        assertFalse(ledger.contains(october, persons))
    }

    @Test
    fun `a tap lands what plays and what waits on top, and counts them as played`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        clock.skip()
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertTrue(ledger.contains(october, IntroSequence.Cover))
        assertTrue(spy.played.isEmpty())
        val playing = clock(IntroLedger(), spy)
        playing.register(weeks) { beats }
        playing.isActive = true
        playing.show(weeks)
        playing.skip()
        assertEquals(IntroClock.Phase.Settled, playing.phase(weeks))
        assertEquals(listOf(weeks), spy.stopped)
    }

    @Test
    fun `a page stops only its own haptics, never another page's`() {
        val spy = HapticsSpy()
        val september = clock(haptics = spy, month = YearMonth.of(2026, 9))
        val clock = clock(haptics = spy)
        listOf(clock, september).forEach {
            it.register(IntroSequence.Cover) { beats }
            it.show(IntroSequence.Cover)
            it.isActive = true
        }
        clock.skip()
        assertEquals(listOf(october), spy.stoppedMonths)
        assertTrue(september.isPlaying(IntroSequence.Cover))
    }

    @Test
    fun `losing focus lands what plays; it never plays again this launch`() {
        val ledger = IntroLedger()
        val clock = clock(ledger)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        clock.isActive = false
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        clock.isActive = true
        assertFalse(clock.isPlaying(IntroSequence.Cover))
    }

    @Test
    fun `a card shown on an inactive page waits, unrecorded, and plays on the next focus`() {
        val ledger = IntroLedger()
        val clock = clock(ledger)
        clock.register(weeks) { beats }
        clock.show(weeks)
        assertFalse(ledger.contains(october, weeks))
        clock.isActive = true
        assertTrue(clock.isPlaying(weeks))
    }

    @Test
    fun `a sequence played earlier this launch draws settled and never plays again`() {
        val ledger = IntroLedger()
        ledger.record(october, IntroSequence.Cover)
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        assertTrue(spy.played.isEmpty())
        assertEquals(1f, clock.reveal(IntroSequence.Cover, base)(IntroKey.Header))
    }

    @Test
    fun `a sequence plays the beats its closure gives as it starts`() {
        var current = beats
        val clock = clock()
        clock.register(IntroSequence.Cover) { current }
        current = listOf(IntroBeat(IntroKey.Header, 0.0, 2.0, IntroTick.light))
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        clock.advance(at(1.0))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        assertEquals(0.5f, clock.reveal(IntroSequence.Cover, at(1.05))(IntroKey.Header), 1e-6f)
    }

    @Test
    fun `a frozen clock draws its sequence at its instant and ignores the deck`() {
        val clock = IntroClock.frozen(october, IntroSequence.Cover, beats, elapsed = 0.46)
        assertEquals(0.5f, clock.reveal(IntroSequence.Cover)(IntroKey.Row(0)), 1e-6f)
        clock.register(weeks) { beats }
        clock.show(weeks)
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        assertEquals(IntroClock.Phase.Waiting, clock.phase(weeks))
    }

    @Test
    fun `a card playing its intro ticks its own arrival; a settled card gets the same tick; an idle page none`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.register(weeks) { beats }
        clock.isActive = true
        clock.show(IntroSequence.Cover)
        clock.landed(IntroSequence.Cover)
        assertTrue(spy.ticks.isEmpty())
        clock.advance(at(1.0))
        clock.show(weeks)
        clock.advance(at(2.0))
        clock.show(IntroSequence.Cover)
        clock.landed(IntroSequence.Cover)
        assertEquals(listOf(IntroTick.arrive), spy.ticks)
        clock.isActive = false
        clock.landed(weeks)
        assertEquals(listOf(IntroTick.arrive), spy.ticks)
    }
}
```

  Append to `IntroTempoTest` (port of `clockScales`):

```kotlin
    @Test
    fun `the clock plays the cover and a section at their tempo, frames and haptics alike`() {
        val base = 1_000_000_000_000L
        val beats = listOf(IntroBeat(IntroKey.Header, 0.0, 0.28, IntroTick.light), IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light))
        val weeks = IntroSequence.Section(MonthSection.WEEKS)
        val spy = HapticsSpy()
        val clock = IntroClock(java.time.YearMonth.of(2026, 10), IntroLedger(), spy, IntroTempo(cover = 2.0, sections = 3.0), now = { base })
        clock.register(IntroSequence.Cover) { beats }
        clock.register(weeks) { beats }
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        val start = base + IntroClock.LEAD_NANOS
        fun after(s: Double) = start + (s * 1e9).toLong()
        // 0.14 s into row 0's beat, which now starts at 0.64 s and takes 0.56 s.
        assertEquals(0.25f, clock.reveal(IntroSequence.Cover, after(0.32 * 2 + 0.14))(IntroKey.Row(0)), 1e-6f)
        assertEquals(listOf(beats.map { it.scaled(2.0) }), spy.beats)
        // The cover lands at its scaled end, 1.2 s.
        clock.advance(after(1.1))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        clock.advance(after(1.2))
        assertFalse(clock.isPlaying(IntroSequence.Cover))
        clock.show(weeks)
        assertEquals(beats.map { it.scaled(3.0) }, spy.beats.last())
    }
```

  Add the imports `org.junit.Assert.assertTrue`/`assertFalse` to the file.

- [ ] **Step 2: Run them; they fail.**

- [ ] **Step 3: Write `IntroClock.kt`.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import app.pbbls.android.core.designsystem.RevealMotion
import kotlinx.coroutines.flow.first
import java.time.YearMonth

/**
 * A month page's intro (#1242 D19; iOS `IntroClock`, cut to the deck): which card plays, which waits,
 * which has landed. One card is shown at a time (`show`, the deck's top card); its host registers a
 * closure for its beats, asked for only as it starts. It plays once the page is active (`isActive`),
 * once per launch (the ledger records it as it starts), at its tempo. Figures read [reveal] in their
 * draw phase; [drive] runs the frame loop while a card plays and lands it at its end.
 */
@Stable
class IntroClock private constructor(
    val month: YearMonth,
    private val ledger: IntroLedger,
    private val haptics: IntroHapticPlaying?,
    private val tempo: IntroTempo,
    private val now: () -> Long,
    /** A reference's instant, in nanos into its sequence: every key is drawn there, and nothing moves. */
    private val frozenAt: Long?,
) {
    constructor(
        month: YearMonth,
        ledger: IntroLedger,
        haptics: IntroHapticPlaying?,
        tempo: IntroTempo = IntroTempo.standard,
        now: () -> Long = System::nanoTime,
    ) : this(month, ledger, haptics, tempo, now, frozenAt = null)

    sealed interface Phase {
        data object Waiting : Phase

        data class Playing(
            val start: Long,
        ) : Phase

        data object Settled : Phase
    }

    private val phases = mutableStateMapOf<IntroSequence, Phase>()
    private val ends = mutableStateMapOf<IntroSequence, Long>()
    private var frameNanos by mutableLongStateOf(0L)
    private val keyed = mutableMapOf<IntroSequence, Map<IntroKey, IntroBeat>>()
    private val latest = mutableMapOf<IntroSequence, () -> List<IntroBeat>>()
    private var shown: IntroSequence? = null

    /** The page is the pager's settled page, the activity resumed, and the intro allowed. Off lands what plays. */
    var isActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) startNext() else settleAll()
        }

    fun phase(sequence: IntroSequence): Phase = phases[sequence] ?: if (ledger.contains(month, sequence)) Phase.Settled else Phase.Waiting

    fun isPlaying(sequence: IntroSequence): Boolean = phase(sequence) is Phase.Playing

    /** Each key's progress in [sequence], read at the current frame when invoked (in a draw phase). */
    fun reveal(sequence: IntroSequence): IntroReveal = IntroReveal { key -> progress(sequence, key, frozenAt ?: frameNanos) }

    /** Each key's progress in [sequence] at [nanos] (the tests). */
    fun reveal(
        sequence: IntroSequence,
        nanos: Long,
    ): IntroReveal = IntroReveal { key -> progress(sequence, key, nanos) }

    private fun progress(
        sequence: IntroSequence,
        key: IntroKey,
        at: Long,
    ): Float =
        when (val phase = phase(sequence)) {
            Phase.Settled -> 1f
            Phase.Waiting -> 0f
            is Phase.Playing -> {
                val beat = keyed[sequence]?.get(key)
                when {
                    beat == null -> 1f
                    else -> {
                        val elapsed = (at - phase.start) / 1e9
                        if (beat.duration <= 0) {
                            if (elapsed >= beat.at) 1f else 0f
                        } else {
                            RevealMotion.clamp((elapsed - beat.at) / beat.duration).toFloat()
                        }
                    }
                }
            }
        }

    /** A card's beats, from the latest wrap its host drew, asked for only as it starts. A card shown and waiting for them starts now. */
    fun register(
        sequence: IntroSequence,
        beats: () -> List<IntroBeat>,
    ) {
        if (frozenAt != null) return
        latest[sequence] = beats
        startNext()
    }

    /** The deck's top card (D11): the one it covered lands, and this one plays once registered. */
    fun show(sequence: IntroSequence) {
        if (frozenAt != null) return
        val previous = shown
        shown = sequence
        if (previous != null && previous != sequence) land(previous)
        startNext()
    }

    /** A card's host left the composition: if it was on top it no longer is, and if it played it lands. */
    fun leave(sequence: IntroSequence) {
        if (shown == sequence) shown = null
        if (isPlaying(sequence)) land(sequence)
    }

    /** A throw came to rest on [sequence] (D11): every throw lands on a tick; one that plays its intro ticks its own. */
    fun landed(sequence: IntroSequence) {
        if (frozenAt != null || !isActive || isPlaying(sequence)) return
        haptics?.tick(IntroTick.arrive)
    }

    /** A tap: what plays and what waits on top land now, and count as played. */
    fun skip() {
        shown?.let { top ->
            if (phase(top) == Phase.Waiting) {
                ledger.record(month, top)
                phases[top] = Phase.Settled
            }
        }
        settleAll()
    }

    /** Focus lost, the activity paused, the page gone: what plays lands and its haptics stop; what waits keeps waiting, unrecorded. */
    fun settleAll() {
        ends.keys.toList().forEach { sequence ->
            haptics?.stop(sequence, month)
            phases[sequence] = Phase.Settled
        }
        ends.clear()
    }

    /** Lands the playing card once [nanos] is past its end. */
    fun advance(nanos: Long) {
        ends.entries.filter { nanos >= it.value }.map { it.key }.forEach(::land)
    }

    /** One frame while a card plays: the reveals redraw, the frame-clock haptics fire, and the card lands at its end. */
    fun frame(nanos: Long) {
        frameNanos = nanos
        ends.keys.toList().forEach { haptics?.frame(it, month, nanos) }
        advance(nanos)
    }

    /** The frame loop: idle until a card plays, then one [frame] per frame until none does. Run it in the page's `LaunchedEffect`. */
    suspend fun drive() {
        if (frozenAt != null) return
        while (true) {
            snapshotFlow { ends.isNotEmpty() }.first { it }
            while (ends.isNotEmpty()) withFrameNanos(::frame)
        }
    }

    private fun land(sequence: IntroSequence) {
        if (ends.remove(sequence) == null) return
        haptics?.stop(sequence, month)
        phases[sequence] = Phase.Settled
        startNext()
    }

    /** Starts the card on top when the page may play and nothing does. One with nothing to play lands at once. */
    private fun startNext() {
        if (!isActive || ends.isNotEmpty()) return
        val next = shown?.takeIf { phase(it) == Phase.Waiting } ?: return
        val beats = latest[next]?.invoke() ?: return
        if (beats.isEmpty()) {
            ledger.record(month, next)
            phases[next] = Phase.Settled
            return
        }
        val factor = tempo.factor(next)
        start(next, beats.map { it.scaled(factor) })
    }

    private fun start(
        sequence: IntroSequence,
        beats: List<IntroBeat>,
    ) {
        val begin = now() + LEAD_NANOS
        // The first beat of a key wins, as iOS.
        keyed[sequence] = beats.distinctBy { it.key }.associateBy { it.key }
        ends[sequence] = begin + (IntroScore.duration(beats) * 1e9).toLong()
        phases[sequence] = Phase.Playing(begin)
        ledger.record(month, sequence)
        haptics?.play(sequence, month, beats, begin)
    }

    companion object {
        /** A card starts this far ahead, so its haptics and its frames begin together. */
        const val LEAD_NANOS = 50_000_000L

        /** A reference's clock: [sequence] playing, drawn [elapsed] seconds in, its beats unscaled. */
        fun frozen(
            month: YearMonth,
            sequence: IntroSequence,
            beats: List<IntroBeat>,
            elapsed: Double,
        ): IntroClock =
            IntroClock(month, IntroLedger(), null, IntroTempo.standard, { 0L }, frozenAt = (elapsed * 1e9).toLong()).apply {
                keyed[sequence] = beats.distinctBy { it.key }.associateBy { it.key }
                phases[sequence] = Phase.Playing(0L)
            }
    }
}
```

- [ ] **Step 4: Run the tests; they pass.** Snapshot state written outside a composition works in plain JVM tests: `mutableStateMapOf` and `mutableLongStateOf` need no Android.

- [ ] **Step 5: Commit** `feat(android): add the month intro's clock`.

### Task 14: The stage, the reveal modifier, the tap

**Files:**
- Create: `features/path/wrap/intro/IntroStage.kt`

- [ ] **Step 1: Write it.** It is UI plumbing with nothing pure to test; Task 16's reference exercises it.

```kotlin
package app.pbbls.android.features.path.wrap.intro

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.pbbls.android.core.designsystem.RevealMotion

/** The clock and the card a composable belongs to (#1242 D19). UI plumbing, like `LocalSharedTransitionScope`. */
@Immutable
class IntroStage(
    val clock: IntroClock,
    val sequence: IntroSequence,
) {
    val reveal: IntroReveal = clock.reveal(sequence)
}

/** Null, the default, draws every figure settled (D2). */
val LocalIntroStage = compositionLocalOf<IntroStage?> { null }

/** The reveal of the card around this composable; [IntroReveal.settled] outside one. */
@Composable
@ReadOnlyComposable
fun currentIntroReveal(): IntroReveal = LocalIntroStage.current?.reveal ?: IntroReveal.settled

/**
 * Makes [content] a card of the intro: it registers [beats] with [clock] while composed (asked for only
 * as the card starts) and leaves as it goes. No clock: [content] as it is.
 */
@Composable
fun IntroHost(
    clock: IntroClock?,
    sequence: IntroSequence,
    beats: () -> List<IntroBeat>,
    content: @Composable () -> Unit,
) {
    if (clock == null) {
        content()
        return
    }
    val current by rememberUpdatedState(beats)
    DisposableEffect(clock, sequence) {
        clock.register(sequence) { current() }
        onDispose { clock.leave(sequence) }
    }
    val stage = remember(clock, sequence) { IntroStage(clock, sequence) }
    CompositionLocalProvider(LocalIntroStage provides stage, content = content)
}

/** How a Compose part lands (iOS `IntroLanding`). */
enum class IntroLanding { RISE, FADE, POP }

/**
 * Lands this part on [key]'s beat, read in the draw phase. Settled, or with no key, it returns `this`:
 * no layer, today's render. Hidden parts keep their frames, so nothing jumps when they land.
 */
fun Modifier.introReveal(
    reveal: IntroReveal,
    key: IntroKey?,
    landing: IntroLanding = IntroLanding.RISE,
): Modifier =
    if (key == null || reveal === IntroReveal.settled) {
        this
    } else {
        graphicsLayer {
            val p = reveal(key).toDouble()
            when (landing) {
                IntroLanding.RISE -> {
                    val eased = RevealMotion.easeOut(p)
                    alpha = eased.toFloat()
                    translationY = IntroMotion.riseOffset.toPx() * (1 - eased).toFloat()
                }
                IntroLanding.FADE -> alpha = RevealMotion.easeOut(p).toFloat()
                IntroLanding.POP -> {
                    alpha = RevealMotion.popOpacity(p).toFloat()
                    scaleX = RevealMotion.popScale(p).toFloat()
                    scaleY = scaleX
                }
            }
        }
    }

/**
 * Calls [onTap] for a tap anywhere inside, observed and never consumed, so the tap still does its job
 * (a milestone opens its held card). A drag that a scroller takes, or past the touch slop, is no tap.
 */
fun Modifier.onAnyTap(onTap: () -> Unit): Modifier =
    pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var tap = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) tap = false
                if (!change.pressed) break
            }
            if (tap) onTap()
        }
    }
```

- [ ] **Step 2: Compile; commit** `feat(android): give the month intro its stage and tap`.

### Task 15: The three haptic tiers

**Files:**
- Create: `features/path/wrap/intro/IntroHapticTier.kt`, `IntroComposition.kt`, `IntroWaveform.kt`, `IntroVibrator.kt`
- Modify: `app/src/main/AndroidManifest.xml` (VIBRATE)
- Test: `IntroCompositionTest.kt`, `IntroWaveformTest.kt`, `IntroHapticTierTest.kt`

D18. The probe on `chore/1242-haptic-probe` (`app/src/debug/kotlin/app/pbbls/android/SpineLabHaptics.kt`) is the working reference for the Vibrator calls.

- [ ] **Step 1: Write the failing tests.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Test

class IntroHapticTierTest {
    @Test
    fun `the tier follows what the vibrator can do`() {
        assertEquals(IntroHapticTier.COMPOSITION, IntroHapticTier.of(hasVibrator = true, tick = true, amplitude = true))
        assertEquals(IntroHapticTier.COMPOSITION, IntroHapticTier.of(hasVibrator = true, tick = true, amplitude = false))
        assertEquals(IntroHapticTier.WAVEFORM, IntroHapticTier.of(hasVibrator = true, tick = false, amplitude = true))
        assertEquals(IntroHapticTier.VIEW, IntroHapticTier.of(hasVibrator = true, tick = false, amplitude = false))
        assertEquals(IntroHapticTier.NONE, IntroHapticTier.of(hasVibrator = false, tick = true, amplitude = true))
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.CLICK
import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.LOW_TICK
import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.TICK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D18's composition, against the Pixel 8's durations (TICK 5, LOW_TICK 12, CLICK 26 ms). */
class IntroCompositionTest {
    private val pixel = mapOf(TICK to 5, LOW_TICK to 12, CLICK to 26)
    private val all = setOf(TICK, LOW_TICK, CLICK)

    @Test
    fun `a tick's primitive follows its sharpness and its scale its intensity`() {
        assertEquals(LOW_TICK, IntroComposition.kindOf(IntroTick.faint))
        assertEquals(TICK, IntroComposition.kindOf(IntroTick.light))
        assertEquals(CLICK, IntroComposition.kindOf(IntroTick.firm))
        assertEquals(LOW_TICK, IntroComposition.kindOf(IntroTick.arrive))
    }

    @Test
    fun `each pause is the beat's time less the end of the primitive before it`() {
        val beats =
            listOf(
                IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.light),
                IntroBeat(IntroKey.Row(0), 0.032, 0.0, IntroTick.light),
                IntroBeat(IntroKey.Row(1), 0.1, 0.0, IntroTick.firm),
            )
        val prims = IntroComposition.of(beats, all, pixel, leadMs = 50)
        assertEquals(listOf(50, 27, 63), prims.map { it.pauseMs })
        assertEquals(listOf(TICK, TICK, CLICK), prims.map { it.kind })
        assertEquals(0.40f, prims[0].scale, 0f)
    }

    @Test
    fun `a missing low tick or click becomes a tick, softer or firmer`() {
        val beats = listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.faint), IntroBeat(IntroKey.Row(0), 0.1, 0.0, IntroTick.firm))
        val prims = IntroComposition.of(beats, setOf(TICK), pixel, leadMs = 0)
        assertEquals(listOf(TICK, TICK), prims.map { it.kind })
        assertEquals(0.22f * 0.7f, prims[0].scale, 1e-6f)
        assertEquals(1f, prims[1].scale, 0f) // 0.9 × 1.2, clamped
    }

    @Test
    fun `a swell is a train of low ticks back to back, ramping, silent a floor before its closing tick`() {
        val swell = IntroSwell(0.15f, 0.75f, 0.3f)
        val beats =
            listOf(
                IntroBeat(IntroKey.Gauge(0), 0.0, 0.3, swell = swell),
                IntroBeat(IntroKey.Full(0), 0.3, 0.0, IntroTick.light),
            )
        val prims = IntroComposition.of(beats, all, pixel, leadMs = 0)
        val train = prims.dropLast(1)
        assertTrue(train.all { it.kind == LOW_TICK })
        assertEquals((275 / 12), train.size)
        assertTrue(train.drop(1).all { it.pauseMs == 0 })
        assertEquals(0.15f, train.first().scale, 1e-6f)
        assertEquals(0.75f, train.last().scale, 1e-6f)
        assertEquals(TICK, prims.last().kind)
        // The closing tick at 300 ms, after a train ending at 22 × 12 = 264 ms.
        assertEquals(36, prims.last().pauseMs)
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Test

/** D18's waveform tier: 8 ms pulses at each tick's strength, swells as 10 ms amplitude steps. */
class IntroWaveformTest {
    @Test
    fun `ticks are pulses at their strength, with silence between`() {
        val beats = listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.light), IntroBeat(IntroKey.Row(0), 0.05, 0.0, IntroTick.firm))
        val wave = IntroWaveform.of(beats, leadMs = 10)
        assertEquals(listOf(10L, 8L, 42L, 8L), wave.timings)
        assertEquals(listOf(0, 102, 0, 230), wave.amplitudes)
    }

    @Test
    fun `a swell ramps in 10 ms steps over its sounding time`() {
        val beats = listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.07, swell = IntroSwell(0.2f, 0.6f, 0.3f)))
        val wave = IntroWaveform.of(beats, leadMs = 0)
        assertEquals(List(4) { 10L }, wave.timings)
        assertEquals(listOf(51, 85, 119, 153), wave.amplitudes)
    }
}
```

  The expected values follow from the algorithms in Step 3:
  - **Composition:** the train sounds for 300 − 25 = 275 ms, which is 22 LOW_TICKs of 12 ms.
  - **Waveform amplitudes:** `(intensity × 255).roundToInt()`, so 0.4 → 102 and 0.9 → 230.
  - **Swell steps:** 70 − 25 = 45 ms of sounding gives 4 steps. The step amplitudes ramp from 0.2 to 0.6 over steps 0…3 (0.2, 0.333, 0.467, 0.6), so ×255 rounds to 51, 85, 119, 153.

  If a value differs by one from rounding, check the algorithm rather than the test.

- [ ] **Step 2: Run them; they fail.**

- [ ] **Step 3: Write the pure files.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

/** Which backend the intro's haptics play through (D18), from what the vibrator can do. */
enum class IntroHapticTier {
    COMPOSITION,
    WAVEFORM,
    VIEW,
    NONE,
    ;

    companion object {
        fun of(
            hasVibrator: Boolean,
            tick: Boolean,
            amplitude: Boolean,
        ): IntroHapticTier =
            when {
                !hasVibrator -> NONE
                tick -> COMPOSITION
                amplitude -> WAVEFORM
                else -> VIEW
            }
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.intro

import kotlin.math.roundToInt

/** The composition's primitives the intro uses (D18). */
enum class IntroPrimitiveKind { TICK, LOW_TICK, CLICK }

/** One primitive: its [kind], its [scale] 0…1, and the pause before it, from the end of the one before. */
data class IntroPrimitive(
    val kind: IntroPrimitiveKind,
    val scale: Float,
    val pauseMs: Int,
)

/** Tier C (D18): a sequence's beats as one composition's primitives. Pure, so the timing is tested. */
object IntroComposition {
    /** A missing LOW_TICK plays as a softer TICK; a missing CLICK as a firmer one. */
    const val LOW_TICK_AS_TICK = 0.7f
    const val CLICK_AS_TICK = 1.2f

    /** A swell's train never steps faster than this, whatever its primitive's length. */
    private const val TRAIN_MIN_STEP_MS = 12

    fun kindOf(tick: IntroTick): IntroPrimitiveKind =
        when {
            tick.sharpness < 0.4f -> IntroPrimitiveKind.LOW_TICK
            tick.sharpness < 0.65f -> IntroPrimitiveKind.TICK
            else -> IntroPrimitiveKind.CLICK
        }

    /**
     * [beats]' ticks (floored, [IntroHaptics.events]) and swells (trains of back-to-back low ticks, ramping
     * from the swell's start to its end over its sounding time), in time order, each paused from the end of
     * the one before by [durations]. [leadMs] delays the whole, so it starts with the frames. [supported]
     * holds at least [IntroPrimitiveKind.TICK]: the tier is chosen on it.
     */
    fun of(
        beats: List<IntroBeat>,
        supported: Set<IntroPrimitiveKind>,
        durations: Map<IntroPrimitiveKind, Int>,
        leadMs: Int,
    ): List<IntroPrimitive> {
        data class Placed(
            val atMs: Double,
            val kind: IntroPrimitiveKind,
            val scale: Float,
        )
        val ticks =
            IntroHaptics.events(beats).map { event ->
                val wanted = kindOf(event.tick)
                val (kind, factor) =
                    when {
                        wanted in supported -> wanted to 1f
                        wanted == IntroPrimitiveKind.LOW_TICK -> IntroPrimitiveKind.TICK to LOW_TICK_AS_TICK
                        else -> IntroPrimitiveKind.TICK to CLICK_AS_TICK
                    }
                Placed(event.at * 1000, kind, (event.tick.intensity * factor).coerceIn(0f, 1f))
            }
        val trains =
            IntroHaptics.swells(beats).flatMap { swell ->
                val kind = if (IntroPrimitiveKind.LOW_TICK in supported) IntroPrimitiveKind.LOW_TICK else IntroPrimitiveKind.TICK
                val step = maxOf(durations[kind] ?: 0, TRAIN_MIN_STEP_MS)
                val count = maxOf(1, (swell.sounding * 1000 / step).toInt())
                List(count) { k ->
                    val f = if (count > 1) k / (count - 1f) else 1f
                    Placed(swell.at * 1000 + k * step, kind, swell.swell.from + (swell.swell.to - swell.swell.from) * f)
                }
            }
        var end = 0.0
        return (ticks + trains).sortedBy { it.atMs }.map { p ->
            val pause = (p.atMs + leadMs - end).roundToInt().coerceAtLeast(0)
            end += pause + (durations[p.kind] ?: 0)
            IntroPrimitive(p.kind, p.scale, pause)
        }
    }
}
```

  Check the swell test against this code: `step = max(12, 12) = 12`; `count = (275 / 12).toInt() = 22`; the train covers 0…252 ms, and its last primitive ends at 264 ms. The closing tick sits at 300 ms, so its pause is 300 − 264 = 36. That matches the test.

```kotlin
package app.pbbls.android.features.path.wrap.intro

import kotlin.math.roundToInt

/** One vibration's on-off timings (ms) and amplitudes (0 off, 1…255 on), for `VibrationEffect.createWaveform`. */
data class IntroWave(
    val timings: List<Long>,
    val amplitudes: List<Int>,
)

/** Tier W (D18): a sequence's beats as one waveform, for a vibrator with amplitude control and no primitives. */
object IntroWaveform {
    const val PULSE_MS = 8
    const val STEP_MS = 10

    fun of(
        beats: List<IntroBeat>,
        leadMs: Int,
    ): IntroWave {
        data class Segment(
            val start: Double,
            val length: Double,
            val amplitude: Int,
        )
        fun amp(intensity: Float) = (intensity * 255).roundToInt().coerceIn(1, 255)
        val pulses = IntroHaptics.events(beats).map { Segment(it.at * 1000 + leadMs, PULSE_MS.toDouble(), amp(it.tick.intensity)) }
        val steps =
            IntroHaptics.swells(beats).flatMap { swell ->
                val count = maxOf(1, (swell.sounding * 1000 / STEP_MS).toInt())
                List(count) { k ->
                    val f = if (count > 1) k / (count - 1f) else 1f
                    Segment(swell.at * 1000 + leadMs + k * STEP_MS, STEP_MS.toDouble(), amp(swell.swell.from + (swell.swell.to - swell.swell.from) * f))
                }
            }
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        var cursor = 0.0
        (pulses + steps).sortedBy { it.start }.forEach { s ->
            if (s.start > cursor) {
                timings += (s.start - cursor).roundToInt().toLong()
                amplitudes += 0
                cursor = s.start
            }
            val length = s.length - (cursor - s.start)
            if (length <= 0) return@forEach
            timings += length.roundToInt().toLong()
            amplitudes += s.amplitude
            cursor += length
        }
        return IntroWave(timings, amplitudes)
    }
}
```

  Check against the waveform tests:
  - **Ticks:** with a 10 ms lead, the pulses sit at 10 and 60 ms. That gives silence 10, then pulse 8 (102), then silence 42 (60 − 18), then pulse 8 (230). This matches the expected timings and amplitudes.
  - **Swell:** `sounding = max(0.07 − 0.025, 0.035) = 0.045`, so the count is `(45 / 10).toInt()`, which is 4. The first step starts at 0 (no lead), so there's no leading silence: four steps of 10 ms. This matches.

- [ ] **Step 4: Run the tests; they pass.**

- [ ] **Step 5: Write `IntroVibrator.kt`,** the device side: three tiers plus a forced tier for the Lab.

```kotlin
package app.pbbls.android.features.path.wrap.intro

import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.time.YearMonth

/**
 * The month intro's haptics on this phone (#1242 D18): one composition per card where the vibrator plays
 * primitives, else one waveform where it controls amplitude, else `performHapticFeedback` fired from the
 * clock's frames. Every vibration is `USAGE_TOUCH`, so Android's touch feedback setting governs it.
 * [enabled] is Pebbles' own switch (Part 8; on until then). [forced] is the Lab's override.
 */
class IntroVibrator(
    private val vibrator: Vibrator?,
    private val view: View,
    private val enabled: () -> Boolean,
    forced: IntroHapticTier? = null,
) : IntroHapticPlaying {
    private val supported: Set<IntroPrimitiveKind> =
        vibrator
            ?.let { v ->
                IntroPrimitiveKind.entries.filter { v.areAllPrimitivesSupported(primitiveId(it)) }.toSet()
            }.orEmpty()

    private val durations: Map<IntroPrimitiveKind, Int> =
        vibrator
            ?.let { v ->
                val kinds = IntroPrimitiveKind.entries
                kinds.zip(v.getPrimitiveDurations(*kinds.map(::primitiveId).toIntArray()).toList()).toMap()
            }.orEmpty()

    val tier: IntroHapticTier =
        forced ?: IntroHapticTier.of(
            hasVibrator = vibrator?.hasVibrator() == true,
            tick = IntroPrimitiveKind.TICK in supported,
            amplitude = vibrator?.hasAmplitudeControl() == true,
        )

    /** Tier V's voices: each playing card's floored ticks, from its start. */
    private class Voice(
        val events: List<IntroHapticEvent>,
        val start: Long,
        var next: Int = 0,
    )

    private val voices = mutableMapOf<Pair<YearMonth, IntroSequence>, Voice>()

    /** Tiers C and W: the card whose vibration is sounding. */
    private var sounding: Pair<YearMonth, IntroSequence>? = null

    init {
        Log.i(TAG, "intro haptics tier $tier, primitives $supported")
    }

    override fun play(
        sequence: IntroSequence,
        month: YearMonth,
        beats: List<IntroBeat>,
        startNanos: Long,
    ) {
        if (!enabled()) return
        val leadMs = ((startNanos - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(0)
        when (tier) {
            IntroHapticTier.COMPOSITION -> {
                val prims = IntroComposition.of(beats, supported + IntroPrimitiveKind.TICK, durations, leadMs)
                if (prims.isEmpty()) return
                val composition = VibrationEffect.startComposition()
                prims.forEach { composition.addPrimitive(primitiveId(it.kind), it.scale, it.pauseMs) }
                vibrate(composition.compose())
                sounding = month to sequence
            }
            IntroHapticTier.WAVEFORM -> {
                val wave = IntroWaveform.of(beats, leadMs)
                if (wave.timings.isEmpty()) return
                vibrate(VibrationEffect.createWaveform(wave.timings.toLongArray(), wave.amplitudes.toIntArray(), -1))
                sounding = month to sequence
            }
            IntroHapticTier.VIEW -> voices[month to sequence] = Voice(IntroHaptics.events(beats), startNanos)
            IntroHapticTier.NONE -> Unit
        }
    }

    /** Stops this page's own voice only: settling September never cuts October's buzz (iOS `ownHaptics`). */
    override fun stop(
        sequence: IntroSequence,
        month: YearMonth,
    ) {
        voices.remove(month to sequence)
        if (sounding == month to sequence) {
            vibrator?.cancel()
            sounding = null
        }
    }

    override fun tick(tick: IntroTick) {
        if (!enabled()) return
        when (tier) {
            IntroHapticTier.VIEW -> view.performHapticFeedback(constantFor(tick))
            IntroHapticTier.NONE -> Unit
            else -> play(IntroSequence.End, NO_MONTH, listOf(IntroBeat(IntroKey.Header, 0.0, 0.0, tick)), System.nanoTime())
        }
    }

    override fun frame(
        sequence: IntroSequence,
        month: YearMonth,
        nanos: Long,
    ) {
        val voice = voices[month to sequence] ?: return
        val elapsed = (nanos - voice.start) / 1e9
        var fire: IntroHapticEvent? = null
        while (voice.next < voice.events.size && voice.events[voice.next].at <= elapsed) {
            fire = voice.events[voice.next]
            voice.next += 1
        }
        fire?.let { view.performHapticFeedback(constantFor(it.tick)) }
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        try {
            v.vibrate(effect, TOUCH)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "intro haptics refused by the vibrator", e)
        }
    }

    private companion object {
        const val TAG = "intro"

        /** A landing tick's voice: no page's month. */
        val NO_MONTH: YearMonth = YearMonth.of(1970, 1)
        val TOUCH: VibrationAttributes = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()

        fun primitiveId(kind: IntroPrimitiveKind): Int =
            when (kind) {
                IntroPrimitiveKind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
                IntroPrimitiveKind.LOW_TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                IntroPrimitiveKind.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }

        /** Tier V: the constant by intensity (D18). */
        fun constantFor(tick: IntroTick): Int =
            when {
                tick.intensity < 0.35f -> HapticFeedbackConstants.CLOCK_TICK
                tick.intensity < 0.7f -> HapticFeedbackConstants.CONTEXT_CLICK
                else -> HapticFeedbackConstants.VIRTUAL_KEY
            }
    }
}

/** The intro's haptics for this view. [enabled] is Pebbles' switch; [forced] the Lab's tier override. */
@Composable
fun rememberIntroVibrator(
    enabled: () -> Boolean = { true },
    forced: IntroHapticTier? = null,
): IntroVibrator {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view, forced) {
        IntroVibrator(context.getSystemService(VibratorManager::class.java)?.defaultVibrator, view, enabled, forced)
    }
}
```

  `tick()` plays tiers C and W through `play` with a one-beat score under `NO_MONTH`, so a landing tick never cancels a page's playing card.

- [ ] **Step 6: The permission.** In `app/src/main/AndroidManifest.xml`, under the `INTERNET` permission:

```xml
    <!-- The month intro's haptics (#1242 D18) play VibrationEffect compositions; a normal permission, no prompt. -->
    <uses-permission android:name="android.permission.VIBRATE" />
```

- [ ] **Step 7: Compile, run the intro tests, `lintDebug`. Commit** `feat(android): play the month intro's haptics as a composition, with two fallbacks`.

### Task 16: The cover plays

**Files:**
- Modify: `features/path/wrap/month/Garland.kt` (reveal; `MonthWrap.numberedDays()`)
- Modify: `features/path/wrap/month/MonthCover.kt` (reveals; `coverBeats`)
- Test: `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/GarlandCourseTest.kt`, `CoverBeatsTest.kt`
- Create: `app/src/screenshotTest/kotlin/app/pbbls/android/IntroScreenshots.kt` (`IntroGarlandHalf`)

Port of iOS `Garland` (`Wrap/Month/Garland.swift`: per-slot pop, `courseSweep`, leaf pop, numerals fade) and `MonthCover` (its `introReveal`s).

- [ ] **Step 1: Write the failing tests.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroMotionTests.course` (#1241): the garland's course runs on to its end as the leaf ties it. */
class GarlandCourseTest {
    @Test
    fun `the course runs on to its end as the leaf ties it, without a jump, whatever the month's length`() {
        val whole = Garland.courseSweep(course = 1f, leaf = 1f, daysInMonth = 31)
        listOf(28, 29, 30, 31).forEach { days ->
            val drawn = Garland.courseSweep(1f, 0f, days)
            assertEquals(Garland.courseSweep(0.999_999f, 0f, days), drawn, 1e-2f)
            val tying = Garland.courseSweep(1f, 0.5f, days)
            assertTrue(drawn <= tying && tying <= whole)
            assertEquals(whole, Garland.courseSweep(1f, 1f, days), 0f)
        }
        assertTrue(Garland.courseSweep(1f, 0f, 30) < whole)
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.features.path.wrap.intro.IntroKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cover's beats come from what the cover draws (iOS `MonthCoverView.introBeats`). */
class CoverBeatsTest {
    @Test
    fun `the fixture's cover beats every day, its leaf, its souls in the ring and the lines it draws`() {
        val wrap = SpineFixtureWeek.monthWrap
        val beats = coverBeats(wrap, SpineFixtureWeek.lookups)
        assertEquals(31, beats.count { it.key is IntroKey.Slot })
        assertTrue(beats.any { it.key == IntroKey.Leaf })
        assertEquals(8, beats.count { it.key is IntroKey.Soul })
        assertEquals(listOf(IntroKey.Summary, IntroKey.Names, IntroKey.Colophon), beats.map { it.key }.filter { it == IntroKey.Summary || it == IntroKey.Names || it == IntroKey.Colophon })
        assertEquals(wrap.numberedDays(), beats.mapNotNull { (it.key as? IntroKey.Numeral)?.day })
    }
}
```

  The fixture has 5 named souls plus the neighbours. Check that the count of souls the lookups resolve is at least 8, and otherwise assert `min(resolved, 8)`. Write the test against what the fixture really holds.

- [ ] **Step 2: Run them; they fail.**

- [ ] **Step 3: The garland takes a reveal.**
  - Add `reveal: IntroReveal = IntroReveal.settled` to `Garland` (after `modifier`).
  - Add an `internal object Garland` holding `courseSweep`.
  - Add `internal fun MonthWrap.numberedDays()`, taken from the `numbered` remember, which now calls it.

  In the `Canvas`:
  - **Course:** `val drawn = reveal(IntroKey.Course)`; skip when 0. Sweep `Garland.courseSweep(drawn, reveal(IntroKey.Leaf), wrap.daysInMonth)` from `start`. Settled, it equals today's sweep.
  - **Sockets:** for an empty day, `val shown = reveal(IntroKey.Slot(day))`; skip at 0. Below 1, draw at `RevealMotion.popOpacity` alpha (pass the colour's alpha × it) and the diameter × `popScale`.
  - **Seals:** `val shown = reveal(if (place.index == 0) IntroKey.Slot(place.day) else IntroKey.Seal(place.day, place.index))`; skip at 0. Below 1, add `scale(popScale, popScale, Offset.Zero)` inside the existing `withTransform` after the rotate, and draw with alpha. `drawSeal` needs an alpha parameter: read `core/ui/render/stone/` `drawSeal`. If it has none, wrap the call in a layer with `drawContext.canvas.saveLayer(…, Paint().apply { alpha = … })`, or use a `graphicsLayer`-free alternative it already offers. Only below 1.
  - **Numerals:** `val shown = reveal(IntroKey.Numeral(day))`; at 1 the current `drawText`, below 1 `drawText(text, topLeft = …, alpha = RevealMotion.easeOut(shown.toDouble()).toFloat())`, and skip at 0.
  - **Leaf:** `val tied = reveal(IntroKey.Leaf)`; skip at 0; below 1, `withTransform({ scale(popScale, popScale, foot) })` and draw the leaf's path with `alpha = popOpacity`.

```kotlin
/** The garland's pure geometry (iOS `Garland.courseSweep`). */
internal object Garland {
    /** The course's sweep from its start, in degrees: half a slot past the newest day while they land, then on to its end as the leaf ties the ring. Settled, the whole course. */
    fun courseSweep(
        course: Float,
        leaf: Float,
        daysInMonth: Int,
    ): Float {
        val ring = RadialLayout(SLOTS, startDeg = 90f)
        val start = ring.angleDeg(COURSE_INSET)
        val full = ring.angleDeg(SLOTS - COURSE_INSET) - start
        val span = maxOf(daysInMonth - 1, 0)
        fun newest(drawn: Float) = minOf(full, ring.angleDeg(1.5f + RevealMotion.easeInOut(drawn.toDouble()).toFloat() * span) - start)
        if (course < 1f) return newest(course)
        if (leaf >= 1f) return full
        val last = newest(1f)
        return last + (full - last) * RevealMotion.easeOut(leaf.toDouble()).toFloat()
    }
}

/** The 1st, each Monday and the last day: the days the garland numbers. */
internal fun MonthWrap.numberedDays(): List<Int> =
    (0 until daysInMonth).filter { d -> d == 0 || d == daysInMonth - 1 || (firstWeekday.value - 1 + d) % 7 == 0 }
```

  Check `RadialLayout.angleDeg` accepts a `Float` slot; the current code passes `COURSE_INSET` (Float) and `SLOTS - COURSE_INSET`, so it does.

- [ ] **Step 4: The cover's parts take theirs.** In `MonthCover`, read `val reveal = currentIntroReveal()` and apply `Modifier.introReveal(reveal, key, landing)` to:
  - **In the garland's middle:** the kicker `Text` (`Kicker`), the month name (`MonthName`), the year (`Year`), each ring soul (`Soul(i)`, `POP`).
  - **Below:** the summary `Text` (`Summary`), the persons `Column` (`Names`), the colophon `Text` (`Colophon`; pass a `modifier` into `Colophon`).

  Pass `reveal = reveal` to `Garland`. Then add the beats function under the composable:

```kotlin
/** The cover's beats, over what it draws (iOS `MonthCoverView.introBeats`): the ring's souls, the lines shown. */
internal fun coverBeats(
    wrap: MonthWrap,
    lookups: WrapLookups,
): List<IntroBeat> {
    val cover = wrap.cover ?: return emptyList()
    val souls = cover.soulsInOrder.mapNotNull(lookups.soul)
    val colophon = cover.inks.any { lookups.family(it) != null } || cover.papers.any { lookups.domain(it) != null }
    val lines =
        buildList {
            add(IntroKey.Summary)
            if (souls.isNotEmpty()) add(IntroKey.Names)
            if (colophon) add(IntroKey.Colophon)
        }
    return IntroScore.cover(cover.days, wrap.daysInMonth, wrap.numberedDays(), minOf(souls.size, SOULS_IN_RING), lines)
}
```

  `colophon` must mirror `Colophon()`'s early return exactly. Read `familyNames(…)`: if it filters on anything besides `lookups.family`, use the same filter.

- [ ] **Step 5: Run the tests; they pass. Check that the references hold:** `./gradlew :app:compileDebugScreenshotTestKotlin`. With no stage, every reveal is `IntroReveal.settled`: no layer, no transform.

- [ ] **Step 6: The mid-intro reference.** Create `IntroScreenshots.kt`:

```kotlin
package app.pbbls.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.wrap.intro.IntroClock
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroStage
import app.pbbls.android.features.path.wrap.intro.LocalIntroStage
import app.pbbls.android.features.path.wrap.month.MonthCover
import app.pbbls.android.features.path.wrap.month.coverBeats
import com.android.tools.screenshot.PreviewTest

/** The month's intro, frozen mid-play (#1242; iOS `intro-garland-half`): the cover at 1150 ms, unscaled. */
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
fun IntroGarlandHalf() {
    PebblesTheme {
        val wrap = SpineFixtureWeek.monthWrap
        val clock = IntroClock.frozen(wrap.month, IntroSequence.Cover, coverBeats(wrap, SpineFixtureWeek.lookups), elapsed = 1.15)
        Surface(color = PebblesTheme.spine.colors.ground) {
            Box(Modifier.width(390.dp)) {
                CompositionLocalProvider(LocalIntroStage provides IntroStage(clock, IntroSequence.Cover)) {
                    MonthCover(wrap, SpineFixtureWeek.lookups)
                }
            }
        }
    }
}
```

  `MonthCover`, `coverBeats` and `SpineFixtureWeek` are `internal` in the same module, so the screenshot source set sees them. If it doesn't, make `coverBeats` and `MonthCover` public with a one-line KDoc note.

- [ ] **Step 7: Commit** `feat(android): play the month cover's garland day by day`.

### Task 17: The Lab plays the cover

**Files:**
- Modify: `features/path/wrap/month/MonthPage.kt` (optional `intro` clock)
- Modify: `app/src/debug/kotlin/app/pbbls/android/SpineLabWeek.kt` (`SpineLabMonth`)

- [ ] **Step 1: `MonthPage` takes an optional clock.** Add `intro: IntroClock? = null` (last parameter before `zone`). The Path passes nothing in this part.

```kotlin
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsState()
    if (intro != null) {
        LaunchedEffect(intro) { intro.drive() }
        LaunchedEffect(intro) { intro.show(IntroSequence.Cover) }
        LaunchedEffect(intro, resumed) { intro.isActive = resumed.isAtLeast(Lifecycle.State.RESUMED) }
        DisposableEffect(intro) { onDispose { intro.isActive = false } }
    }
    LazyColumn(modifier = if (intro != null) modifier.onAnyTap(intro::skip) else modifier, …) {
        item(key = "cover") {
            IntroHost(intro, IntroSequence.Cover, { coverBeats(wrap, lookups) }) { MonthCover(wrap, lookups) }
        }
        …
```

  `collectAsState` on a lifecycle's `StateFlow` is the lifecycle library's own idiom; `lifecycle.currentStateAsState()` from `lifecycle-runtime-compose` is the same in one call, so use it if it's on the classpath. `LocalLifecycleOwner` comes from `androidx.lifecycle.compose`.

- [ ] **Step 2: The Lab's Month tab gets Replay and the tier override.** Replace `SpineLabMonth`'s body:

```kotlin
@Composable
internal fun SpineLabMonth(modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(DayView.CIRCLE) }
    val ledger = remember { IntroLedger() }
    var run by remember { mutableIntStateOf(0) }
    var forced by remember { mutableStateOf<IntroHapticTier?>(null) }
    val haptics = rememberIntroVibrator(forced = forced)
    val allowed = rememberIntroAllowed()
    val month = SpineFixtureWeek.monthWrap.month
    val clock = remember(run, haptics, allowed) { if (allowed) IntroClock(month, ledger, haptics) else null }
    Column(modifier) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                ledger.reset(month)
                run += 1
            }) { Text("Replay") }
            listOf(null, IntroHapticTier.COMPOSITION, IntroHapticTier.WAVEFORM, IntroHapticTier.VIEW).forEach { tier ->
                FilterChip(selected = forced == tier, onClick = { forced = tier }, label = { Text(tier?.name?.take(1) ?: "Auto") })
            }
        }
        Text("tier ${haptics.tier}", Modifier.padding(horizontal = 12.dp))
        Surface(color = PebblesTheme.spine.colors.ground, modifier = Modifier.weight(1f)) {
            MonthPage(
                wrap = SpineFixtureWeek.monthWrap,
                lookups = SpineFixtureWeek.lookups,
                dayView = view,
                onDayView = { view = it },
                onWeek = {},
                onOpen = {},
                intro = clock,
                zone = SpineFixtureWeek.zone,
            )
        }
    }
}
```

  The debug manifest needs no permission: the main manifest declares VIBRATE now.

- [ ] **Step 3: Gate, install, try it on the Pixel 8.**
  - Run `./gradlew ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:compileDebugScreenshotTestKotlin`, then `installDebug`, then launch the Lab.
  - **Drift check:** play the cover, then run `adb shell dumpsys vibrator_manager | grep app.pbbls.android | tail -3`. Record the cover composition's `duration` against the scored 2.2 s plus lead, as the D18 risk asks.
  - **What the maintainer tries:**
    - Month tab: the cover plays (name, then the garland day by day, the leaf, souls, texts);
    - Replay;
    - the tier chips: Auto, C, W, V;
    - a tap mid-garland skips;
    - backgrounding mid-garland stops the buzz;
    - Settings → Sound & vibration → Vibration & haptics → Touch feedback off silences each tier (record what each does).

- [ ] **Step 4: Commit** `feat(android): play the month cover's intro in the spine lab`.

### Task 18: The decision log

**Files:** `docs/decisions/log.md` (append).

- [ ] **Step 1: Append the entry,** after the last one:

```markdown
## 2026-10-26 — On Android the month's intro plays its haptics as a composition, with two fallbacks (#1242)

- **Status:** taken
- **Scope:** android
- **Context:**
  - #1242 ports #1241's animated, haptic month intro. iOS plays its ticks and swells through Core Haptics; Android has no single equivalent.
  - On the maintainer's Pixel 8 (Android 17), a debug probe played the iOS presets and sequences two ways: one `VibrationEffect.Composition` per sequence, and `View.performHapticFeedback` fired from the frame clock. The composition felt more natural and closer to the iPhone, above all on the domains' swell. `dumpsys vibrator_manager` showed its pauses played as compiled.
  - A composition holding a primitive the device lacks plays nothing, and many phones lack primitives or amplitude control.
- **Decision:**
  - **Tier C:** one composition per card where the vibrator plays TICK. A tick's primitive follows its sharpness (LOW_TICK, TICK, CLICK), its scale its intensity. A missing LOW_TICK or CLICK becomes a TICK. A swell is a train of back-to-back LOW_TICKs ramping in scale.
  - **Tier W:** one waveform per card where it controls amplitude: short pulses, and the swell as an amplitude ramp.
  - **Tier V:** otherwise, `performHapticFeedback` from the clock's frames, with no swell.
  - Every vibration is `USAGE_TOUCH`. The app gains the normal `VIBRATE` permission.
- **Why:**
  - **Rejected, V alone:** three fixed textures, frame-quantised times, no swell.
  - **Rejected, the composition alone:** silent on phones without primitives.
  - **Rejected, a waveform swell inside tier C:** the LOW_TICK train felt closest and keeps one voice.
  - **Rejected, Android 16's envelopes and start-offset delays:** API 36, past minSdk 33, and the Pixel 8 reports no envelope support.
- **Consequences:**
  - The tier is chosen from the vibrator's capabilities, never the model. Only the Pixel 8 is felt; tiers W and V are felt there through the Spine Lab's override.
  - Primitives run 1–2 ms past their reported durations, so a long run drifts behind its frames. Measured in the device pass, corrected in `IntroComposition` if past 30 ms.
  - Pebbles' own haptics switch gates every tier (#1242's second comment).
- **Supersedes / Superseded-by:** none. It answers the 2026-10-25 iOS entry's "Android ports the score and the lifecycle, with its own haptic backend".
- **Refs:** #1242, `docs/superpowers/specs/2026-10-26-android-month-intro-design.md` (D18), `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/wrap/intro/`.
```

- [ ] **Step 2: Commit** `docs(android): log the month intro's haptic backend`.

### Task 19: Ship Part 2

- [ ] **Gate:** ktlint, `lintDebug`, every unit test, the screenshot sources compile. No existing reference may change, and `IntroGarlandHalf` is new.
- [ ] **PR script** (`ship-1242-part2.sh`, the maintainer runs it):
  - `gh stack submit --auto`;
  - REST title `feat(android): add the month intro's engine and play the cover`;
  - body "Part 2 of 8 for #1242" with D18–D20 in short;
  - labels `feat ui android no-lab-note`, milestone M64;
  - then `rebaseline-screenshots` alone, to record `IntroGarlandHalf`.

---
# Part 3 — the deck (`feat/1242-android-intro-deck`)

D21, D24. When the intro may play, the month is a deck of galley cards:
- **The cover** plays its intro.
- **Each section's card** arrives with its tick and shows its content settled (Parts 4–7 give each its entrance).
- **Every throw** lands on a tick.
- **The Path's pager** swipe and the strip tap play the selection tick.
- **Under the policy,** the month is the plain page.

Start with `gh stack add feat/1242-android-intro-deck`.

### Task 20: Move the section switch out of `MonthPage` (pure move)

**Files:**
- Create: `features/path/wrap/month/MonthSectionContent.kt`
- Modify: `features/path/wrap/month/MonthPage.kt`

- [ ] **Step 1: Move the `when (section)` block** from `MonthPage`'s `items(…)` into a composable, unchanged:

```kotlin
/** One section of the month, as the page and the deck both draw it (#1242; iOS `MonthSectionContent`). */
@Composable
internal fun MonthSectionContent(
    section: MonthSection,
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    zone: ZoneId,
    aspect: SnapAspectCache?,
    onWeek: (LocalDate) -> Unit,
    onOpen: (PathMoment) -> Unit,
    onMore: (MonthSection) -> Unit,
) {
    val copy = rememberWrapCopy()
    when (section) {
        // … the exact branches MonthPage had, with `aspect` passed to MilestonesSection …
    }
}
```

  `MonthPage`'s `items` lambda becomes `MonthSectionContent(section, wrap, lookups, dayView, zone, aspect, onWeek, onOpen, onMore)`.

- [ ] **Step 2: Compile; the references are unchanged** (`compileDebugScreenshotTestKotlin`; the tree renders the same).
- [ ] **Step 3: Commit** `quality(android): move the month's section switch into its own composable` (a pure move).

### Task 21: Heads, and what each section shows

**Files:**
- Create: `features/path/wrap/month/MonthHead.kt`
- Modify: `WeeksSummary.kt`, `MilestonesSection.kt`, `PersonsSection.kt`, `DomainsSection.kt`, `EmotionGem.kt`, `ToneFigure.kt`, `HoursFigure.kt`, `MonthSectionContent.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/MonthHeadTest.kt` (Robolectric, for `WrapCopy`)

Port of iOS `MonthHead`, each section's `head`/`isShown`, and `monthHeadOnCard` (`MonthDeck.swift`, `MonthSections.swift`).

- [ ] **Step 1: Write `MonthHead.kt`.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy

/** A section's head: its label and its hint (#1242; iOS `MonthHead`). On a card, the title band draws it (D11). */
internal data class MonthHead(
    val label: String,
    val hint: String?,
)

/** Each section's head and whether it draws, over the same conditions its composable draws under. */
internal object MonthSections {
    fun isShown(
        section: MonthSection,
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): Boolean =
        when (section) {
            MonthSection.WEEKS -> WeeksSummary.isShown(wrap)
            MonthSection.MILESTONES -> milestones(wrap, lookups).isNotEmpty()
            MonthSection.PERSONS -> wrap.persons != null
            MonthSection.DOMAINS -> wrap.domains.orEmpty().any { lookups.domain(it.domainId) != null }
            MonthSection.GEM -> wrap.gem != null
            MonthSection.TONE -> wrap.tone?.days?.isNotEmpty() == true
            MonthSection.HOURS -> wrap.hours?.items.orEmpty().any { lookups.moment(it.momentId) != null }
        }

    fun milestones(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ) = wrap.milestones.orEmpty().mapNotNull(lookups.moment)

    fun head(
        section: MonthSection,
        wrap: MonthWrap,
        lookups: WrapLookups,
        dayView: DayView,
        copy: WrapCopy,
    ): MonthHead =
        when (section) {
            MonthSection.WEEKS -> MonthHead(copy.weeksHeader(WeeksSummary.weeks(wrap).size), copy.weeksHint(wrap.month))
            MonthSection.MILESTONES -> MonthHead(copy.milestonesHeader(milestones(wrap, lookups).size), copy.milestonesHint())
            MonthSection.PERSONS -> {
                val named = wrap.persons.orEmpty().any { lookups.soul(it.soulId) != null }
                MonthHead(copy.tile(WeekTile.PERSONS), if (dayView == DayView.CIRCLE && named) copy.clockwiseHint() else null)
            }
            MonthSection.DOMAINS -> MonthHead(copy.tile(WeekTile.DOMAINS), null)
            MonthSection.GEM -> MonthHead(copy.tile(WeekTile.EMOTIONS), copy.gemHint())
            MonthSection.TONE -> MonthHead(copy.toneTitle(), copy.toneHint())
            MonthSection.HOURS -> MonthHead(copy.hoursTitle(), copy.hoursHint())
        }

    fun insightsHead(copy: WrapCopy) = MonthHead(copy.insights(), copy.insightsHint())
}
```

  Read each section's early return and copy its condition exactly:
  - `PersonsSection` returns on `wrap.persons == null`.
  - `ToneFigure` returns on a null tone or empty days.
  - `HoursFigure` returns when no item's moment is known.

  If one differs from the above, the section's code wins: fix `isShown`.

- [ ] **Step 2: Each section uses them and takes `onCard`.**
  - Each of the seven composables replaces its own condition with `if (!MonthSections.isShown(…)) return`.
  - Each gains `onCard: Boolean = false`. When true, it leaves out its head (`MonthSectionHead`, or `SpineSectionHeader` in tone and hours), because the card's title band draws it.
  - `MonthSectionContent` gains `leadsInsights: Boolean` and `onCard: Boolean = false`:
    - On the page (`onCard = false`), it draws the insights head over whichever of tone/hours comes first, as today.
    - On a card, the first tone/hours card's title band is the insights head, and the figure's own head stays (iOS: "under the insights band, the tone and hours figures keep their own heads"). So pass `onCard = onCard && !leadsInsights` to `ToneFigure`/`HoursFigure`.

- [ ] **Step 3: Test the heads** (Robolectric, en):

```kotlin
package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** iOS `MonthDeckTests.titles` and `MonthCardsTests.everyWeek`'s head. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en")
class MonthHeadTest {
    private val copy = WrapCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.ENGLISH)
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `every section of the fixture shows, and the weeks' head counts every week`() {
        MonthSection.entries.forEach { assertTrue("$it", MonthSections.isShown(it, wrap, lookups)) }
        assertEquals(copy.weeksHeader(wrap.weeks!!.size), MonthSections.head(MonthSection.WEEKS, wrap, lookups, DayView.CIRCLE, copy).label)
    }

    @Test
    fun `the persons' hint reads clockwise only around a ring`() {
        assertEquals(copy.clockwiseHint(), MonthSections.head(MonthSection.PERSONS, wrap, lookups, DayView.CIRCLE, copy).hint)
        assertEquals(null, MonthSections.head(MonthSection.PERSONS, wrap, lookups, DayView.CALENDAR, copy).hint)
    }
}
```

- [ ] **Step 4: Compile, run, check the references are unchanged** (`onCard` defaults to false).
- [ ] **Step 5: Commit** `feat(android): give each month section its head and its condition`.

### Task 22: The deck's cards and the sections' arrival

**Files:**
- Create: `features/path/wrap/intro/IntroScoreSections.kt`
- Modify: `features/path/wrap/intro/IntroScore.kt` (constants)
- Create: `features/path/wrap/month/MonthDeck.kt` (the pure part first)
- Test: `IntroScoreSectionsTest.kt`, `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/MonthDeckTest.kt`

- [ ] **Step 1: Write the failing tests.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `IntroScoreSectionsTests.arrival` and `IntroScoreInsightsTests.insights`' head. */
class IntroScoreSectionsTest {
    @Test
    fun `a section's head is drawn from the start, its beat the arrival's tick alone`() {
        val header = IntroScore.head(hasLead = false, insights = false).first { it.key == IntroKey.Header }
        assertEquals(0.0, header.at, 0.0)
        assertEquals(0.0, header.duration, 0.0)
        assertEquals(IntroTick.arrive, header.tick)
    }

    @Test
    fun `leading the insights, their head ticks the arrival and the section's own head is silent, later`() {
        val beats = IntroScore.head(hasLead = true, insights = true)
        assertEquals(IntroKey.Insights, beats.first().key)
        assertEquals(IntroTick.arrive, beats.first().tick)
        val header = beats.first { it.key == IntroKey.Header }
        assertEquals(IntroScore.INSIGHTS_SHIFT, header.at, 0.0)
        assertNull(header.tick)
        assertEquals(IntroScore.INSIGHTS_SHIFT + IntroScore.LEAD_AT, beats.first { it.key == IntroKey.Lead }.at, 1e-12)
    }
}
```

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthHours
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthDeckTests` (#1241 D11): the deck deals a card for the cover and each section that shows. */
class MonthDeckTest {
    private val october = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    private fun section(s: MonthSection) = IntroSequence.Section(s)

    @Test
    fun `the fixture deals the cover, then each section in the server's order, then the end card`() {
        assertEquals(
            listOf(IntroSequence.Cover) + october.sections.map(::section) + IntroSequence.End,
            MonthDeck.cards(october, lookups),
        )
    }

    @Test
    fun `a month with nothing to deal deals no end card either`() {
        val empty = october.copy(sections = emptyList(), cover = null)
        assertTrue(MonthDeck.cards(empty, lookups).isEmpty())
    }

    @Test
    fun `an emptied section deals no card`() {
        val noGem = october.copy(gem = null)
        assertFalse(section(MonthSection.GEM) in MonthDeck.cards(noGem, lookups))
        assertEquals(october.sections.size + 1, MonthDeck.cards(noGem, lookups).size)
    }

    @Test
    fun `a wrap without a cover deals none`() {
        assertEquals(section(october.sections.first()), MonthDeck.cards(october.copy(cover = null), lookups).first())
    }

    @Test
    fun `the insights head sits on the first tone or hours card dealt`() {
        val full = MonthDeck.cards(october, lookups)
        val firstInsight = october.sections.first { it == MonthSection.TONE || it == MonthSection.HOURS }
        val other = if (firstInsight == MonthSection.TONE) MonthSection.HOURS else MonthSection.TONE
        assertTrue(MonthDeck.leadsInsights(section(firstInsight), full))
        assertFalse(MonthDeck.leadsInsights(section(other), full))
        assertFalse(MonthDeck.leadsInsights(section(MonthSection.GEM), full))
        // Its first emptied, the head moves to the other.
        val quiet =
            if (firstInsight == MonthSection.TONE) {
                october.copy(tone = october.tone!!.copy(days = emptyList()))
            } else {
                october.copy(hours = MonthHours(emptyList(), october.hours!!.busiest))
            }
        assertTrue(MonthDeck.leadsInsights(section(other), MonthDeck.cards(quiet, lookups)))
    }
}
```

  If `MonthHours`' constructor differs (field names), read `core/model/wrap/MonthWrap.kt` and adjust. The fixture builds it as `MonthHours(fixtureHours, BusiestWindow(17, 4))`.

- [ ] **Step 2: Run them; they fail.**

- [ ] **Step 3: Write the score's head.** Append to `IntroScore`'s constants:

```kotlin
    // The sections (D8, iOS IntroScore+Sections).
    const val LEAD_AT = 0.16
    const val ROWS_AT = 0.32

    /** When the insights head leads tone or hours, the rest comes this much later. */
    const val INSIGHTS_SHIFT = 0.16
    const val PART_GAP = 0.09
    const val RING_SPAN = 0.45
    const val NEXT_ROW = 0.06
```

  and create `IntroScoreSections.kt`:

```kotlin
package app.pbbls.android.features.path.wrap.intro

/**
 * A section's head (iOS `IntroScore.head`): the insights head first when it leads tone or hours, then the
 * head, then the lead line if any. One arrival per card: the insights head ticks it when it leads, the head
 * otherwise. Both heads are drawn from the start (D8): their beats carry only the tick.
 */
fun IntroScore.head(
    hasLead: Boolean,
    insights: Boolean,
): List<IntroBeat> {
    val beats = mutableListOf<IntroBeat>()
    if (insights) beats += IntroBeat(IntroKey.Insights, 0.0, 0.0, IntroTick.arrive)
    val shift = shift(insights)
    beats += IntroBeat(IntroKey.Header, shift, 0.0, if (insights) null else IntroTick.arrive)
    if (hasLead) beats += IntroBeat(IntroKey.Lead, shift + IntroScore.LEAD_AT, IntroMotion.RISE)
    return beats
}

fun IntroScore.shift(insights: Boolean): Double = if (insights) IntroScore.INSIGHTS_SHIFT else 0.0
```

- [ ] **Step 4: Write `MonthDeck.kt`'s pure part.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.head

/** The month's deck (#1242 D21; iOS `MonthDeck`'s statics): which cards it deals, and what each plays. */
internal object MonthDeck {
    /** The cover if shown, each section that shows in the server's order, then the end card; nothing for an empty month. */
    fun cards(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<IntroSequence> {
        val cover = if (wrap.cover != null) listOf(IntroSequence.Cover) else emptyList()
        val dealt = cover + wrap.sections.filter { MonthSections.isShown(it, wrap, lookups) }.map { IntroSequence.Section(it) }
        return if (dealt.isEmpty()) emptyList() else dealt + IntroSequence.End
    }

    /** The first tone or hours card carries the insights head. */
    fun leadsInsights(
        card: IntroSequence,
        cards: List<IntroSequence>,
    ): Boolean =
        card ==
            cards.firstOrNull {
                it == IntroSequence.Section(MonthSection.TONE) || it == IntroSequence.Section(MonthSection.HOURS)
            }

    /**
     * What a card plays. The cover its garland; a section, for now, its arrival alone (its entrance lands in
     * the parts after this one); the end card nothing yet.
     */
    fun beats(
        card: IntroSequence,
        wrap: MonthWrap,
        lookups: WrapLookups,
        leadsInsights: Boolean,
    ): List<IntroBeat> =
        when (card) {
            IntroSequence.Cover -> coverBeats(wrap, lookups)
            is IntroSequence.Section -> IntroScore.head(hasLead = false, insights = leadsInsights)
            IntroSequence.End -> emptyList()
        }
}
```

  Part 7 gives the end card its beats. Until then the end card never deals: add `.filter { it != IntroSequence.End }` at the call site in Task 25, and remove that filter in Part 7. Leave `cards` as tested.

- [ ] **Step 5: Run them; they pass. Commit** `feat(android): deal the month's cards`.

### Task 23: The deck's motion and stepper

**Files:**
- Create: `features/path/wrap/month/MonthDeckMotion.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/MonthDeckMotionTest.kt`

Port of iOS `MonthDeckMotion` and `MonthDeckStep`; tests port `MonthDeckTests.topIsIdentity`, `.comingIsHeld`, `.leavingLifts`, `.edges`, `.step`, `.noStep`. `settled` is not ported, since Compose's `settledPage` replaces it.

- [ ] **Step 1: Write the failing test.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthDeckMotionTest {
    @Test
    fun `the top card is drawn as it is`() {
        assertEquals(1f, MonthDeckMotion.scale(0f), 0f)
        assertEquals(0f, MonthDeckMotion.turn(0f), 0f)
        assertEquals(0f, MonthDeckMotion.offset(0f, page = 800f, peek = 10f), 0f)
    }

    @Test
    fun `a card to come is held in place beneath the top one, a little smaller, its foot peeking`() {
        assertEquals(10f, 800f + MonthDeckMotion.offset(1f, page = 800f, peek = 10f), 1e-4f)
        assertEquals(5f, 400f + MonthDeckMotion.offset(0.5f, page = 800f, peek = 10f), 1e-4f)
        assertEquals(1f - MonthDeckMotion.SHRINK, MonthDeckMotion.scale(1f), 1e-6f)
    }

    @Test
    fun `a leaving card lifts and turns as it goes, and is not held`() {
        assertEquals(1f + MonthDeckMotion.LIFT, MonthDeckMotion.scale(-1f), 1e-6f)
        assertEquals(-MonthDeckMotion.MAX_TURN, MonthDeckMotion.turn(-1f), 0f)
        assertEquals(0f, MonthDeckMotion.offset(-0.5f, page = 800f, peek = 10f), 0f)
    }

    @Test
    fun `the static edges stand for the cards past the next, two at most`() {
        assertEquals(listOf(0, 0, 1, 2, 2), listOf(0, 1, 2, 3, 7).map(MonthDeckMotion::edges))
    }

    @Test
    fun `each card knows its place in the deck, the segments filled up to it`() {
        val cards = MonthDeck.cards(SpineFixtureWeek.monthWrap, SpineFixtureWeek.lookups)
        assertEquals(MonthDeckStep(0, cards.size), MonthDeckStep.of(IntroSequence.Cover, cards))
        val gem = MonthDeckStep.of(IntroSequence.Section(MonthSection.GEM), cards)!!
        assertEquals((0 until gem.count).map { it <= gem.index }, (0 until gem.count).map(gem::isFilled))
        assertEquals(cards.size - 1, MonthDeckStep.of(IntroSequence.End, cards)!!.index)
    }

    @Test
    fun `a deck of one has no stepper, and a card it does not deal has no place`() {
        assertNull(MonthDeckStep.of(IntroSequence.Cover, listOf(IntroSequence.Cover)))
        assertNull(MonthDeckStep.of(IntroSequence.Section(MonthSection.GEM), listOf(IntroSequence.Cover, IntroSequence.End)))
    }
}
```

- [ ] **Step 2: Run it; it fails. Step 3: Write it.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.features.path.wrap.intro.IntroSequence

/**
 * How a card moves with the paging (#1242 D21; iOS `MonthDeckMotion`), by its distance in pages from the
 * top: above 0 still to come, below 0 leaving, 0 on top, where it is the identity. In dp. Pure.
 */
internal object MonthDeckMotion {
    /** The next card's foot below the top card's. */
    const val PEEK = 10f
    const val SHRINK = 0.05f
    const val LIFT = 0.02f
    const val MAX_TURN = 3f
    const val MAX_EDGES = 2

    fun scale(d: Float): Float = if (d > 0) 1 - SHRINK * d else 1 + LIFT * -d

    fun turn(d: Float): Float = if (d < 0) MAX_TURN * d else 0f

    /** A card to come is held in place beneath the top one, its foot peeking; a leaving one scrolls off. */
    fun offset(
        d: Float,
        page: Float,
        peek: Float,
    ): Float = if (d > 0) d * (peek - page) else 0f

    /** The static edges behind the deck: the cards past the next, two at most. */
    fun edges(below: Int): Int = minOf(MAX_EDGES, maxOf(0, below - 1))
}

/** Where a card lies in the deck, for the stepper at its margin's foot (iOS `MonthDeckStep`). */
internal data class MonthDeckStep(
    val index: Int,
    val count: Int,
) {
    fun isFilled(segment: Int): Boolean = segment <= index

    companion object {
        /** The card's place; null for a deck of one, which has nowhere to go. */
        fun of(
            card: IntroSequence,
            cards: List<IntroSequence>,
        ): MonthDeckStep? {
            val index = cards.indexOf(card)
            return if (cards.size > 1 && index >= 0) MonthDeckStep(index, cards.size) else null
        }
    }
}
```

- [ ] **Step 4: Run it; it passes. Commit** `feat(android): add the month deck's motion and stepper`.

### Task 24: The galley card

**Files:**
- Create: `features/path/wrap/month/MonthDeckCard.kt`

Port of iOS `MonthDeckCard.swift`: `MonthDeckPaper`, `MonthDeckCard`, `MonthCardFill`, `MonthDeckLayout`, `MonthDeckMargin`, `MonthDeckStepper`, `MonthDeckTitle`.

- [ ] **Step 1: Write it.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.features.path.spine.SpineCardShell

/** A card's chrome, in dp: the exploration's galley (iOS `MonthDeckLayout`). */
internal object MonthDeckLayout {
    val inset = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 22.dp)
    val margin = 28.dp
    val titleTop = 22.dp
    val titleRuleGap = 10.dp
    val titleBottom = 12.dp
    val marginFoot = 18.dp
    val segment = 10.dp
    val segmentThickness = 3.dp
    val segmentGap = 3.dp
    val endGap = 20.dp
}

/** A card's paper: the pebble cards' own shell with no emotion, so no painted edge (D21). */
@Composable
internal fun MonthDeckPaper(modifier: Modifier = Modifier) {
    SpineCardShell(palette = null, polarity = ValencePolarity.NEUTRAL, intensity = 1, modifier = modifier.fillMaxSize()) {}
}

/**
 * One card (#1242 D21): its paper, the margin ruled off on the left with [month] upright and the stepper,
 * the title band with [head] (none on the cover and the end card), and [content] top-aligned in the room
 * under it: laid out in the whole room when it fits, so a section's *more* stands at the foot; scaled down,
 * anchored at the top, when it doesn't. [content] receives the room's height.
 */
@Composable
internal fun MonthDeckCard(
    month: String,
    step: MonthDeckStep?,
    head: MonthHead?,
    modifier: Modifier = Modifier,
    content: @Composable (room: Dp) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    Box(modifier.fillMaxSize().padding(MonthDeckLayout.inset)) {
        SpineCardShell(palette = null, polarity = ValencePolarity.NEUTRAL, intensity = 1, modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                MonthDeckMargin(month, step)
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.cardRule))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (head != null) MonthDeckTitle(head)
                    MonthCardRoom(Modifier.weight(1f).fillMaxWidth(), content)
                }
            }
        }
    }
}

/**
 * Lays [content] out in the room when its intrinsic height fits, so its spacers open; past the room, at its
 * own height, scaled down from the top (iOS `MonthCardFill` + the card's fit). [content] receives the room.
 * Intrinsics are safe here: nothing a card draws is a SubcomposeLayout (no BoxWithConstraints, no lazy
 * list); a figure that becomes one must move this to a SubcomposeLayout.
 */
@Composable
private fun MonthCardRoom(
    modifier: Modifier,
    content: @Composable (room: Dp) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val room = maxHeight
        Layout(content = { content(room) }) { measurables, constraints ->
            val roomPx = constraints.maxHeight
            val child = measurables.firstOrNull() ?: return@Layout layout(constraints.maxWidth, roomPx) {}
            val fits = child.maxIntrinsicHeight(constraints.maxWidth) <= roomPx
            val placeable =
                child.measure(
                    if (fits) Constraints.fixed(constraints.maxWidth, roomPx) else Constraints.fixedWidth(constraints.maxWidth),
                )
            layout(constraints.maxWidth, roomPx) {
                if (fits) {
                    placeable.place(0, 0)
                } else {
                    val fit = roomPx.toFloat() / placeable.height
                    placeable.placeWithLayer(0, 0) {
                        scaleX = fit
                        scaleY = fit
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                }
            }
        }
    }
}
```

```kotlin
/** The margin: the month set upright at the top, reading upward, and the stepper at the foot. Decorative. */
@Composable
private fun MonthDeckMargin(
    month: String,
    step: MonthDeckStep?,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(
        Modifier
            .width(MonthDeckLayout.margin)
            .fillMaxHeight()
            .padding(top = MonthDeckLayout.titleTop, bottom = MonthDeckLayout.marginFoot)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            month,
            style = type.sectionLabel,
            color = colors.ink3,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.uprightQuarter(),
        )
        Spacer(Modifier.weight(1f))
        if (step != null) {
            Column(verticalArrangement = Arrangement.spacedBy(MonthDeckLayout.segmentGap)) {
                repeat(step.count) { segment ->
                    Box(
                        Modifier
                            .size(MonthDeckLayout.segmentThickness, MonthDeckLayout.segment)
                            .background(if (step.isFilled(segment)) colors.ink2 else colors.ruleVariant, CircleShape),
                    )
                }
            }
        }
    }
}

/** A card's title band: the head's label, its hint in italics at the far end, a rule under them. */
@Composable
private fun MonthDeckTitle(head: MonthHead) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(
        Modifier.padding(horizontal = SECTION_PAD_X).padding(top = MonthDeckLayout.titleTop, bottom = MonthDeckLayout.titleBottom),
        verticalArrangement = Arrangement.spacedBy(MonthDeckLayout.titleRuleGap),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(head.label, style = type.sectionLabel, color = colors.ink2, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f).width(12.dp))
            head.hint?.let { Text(it, style = type.sectionHint, color = colors.ink3, textAlign = TextAlign.End) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.cardRule))
    }
}
```

  `Modifier.uprightQuarter()` turns the text a quarter counter-clockwise and lays it out as turned: width becomes its height (iOS `MonthDeckUpright`). Write it in this file:

```kotlin
/** Its content turned −90° and laid out as turned: its width the content's height, its height its width. */
private fun Modifier.uprightQuarter(): Modifier =
    layout { measurable, _ ->
        val p = measurable.measure(Constraints())
        layout(p.height, p.width) {
            p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = -90f }
        }
    }
```

  **Verify the colour names** in `SpineColors`: `cardRule`, `ruleVariant` (or whichever maps to `outlineVariant`), `ink2`/`ink3`. The iOS stepper fills with `onSurfaceVariant` and the unfilled ones are `outlineVariant`; use the Spine roles that map to those. The rules are 1 dp (`SpineLayout.rule` on iOS).

- [ ] **Step 2: Compile. Commit** `feat(android): add the month deck's galley card`.

### Task 25: The deck plays

**Files:**
- Modify: `features/path/wrap/month/MonthDeck.kt` (the composable)
- Modify: `features/path/wrap/month/MonthPage.kt` (deck or page; the clock; active)
- Modify: `features/path/PathScreen.kt` (ledger, haptics, focused page, swipe and strip ticks)
- Modify: `features/path/PathViewModel.kt` (`introLedger`)
- Modify: `app/src/debug/kotlin/app/pbbls/android/SpineLabWeek.kt` (the Lab passes its ledger and haptics to `MonthPage`)

- [ ] **Step 1: The deck composable.** Append to `MonthDeck.kt`:

```kotlin
/**
 * The month as a deck of cards (#1242 D21; iOS `MonthDeck`): a vertical pager, one card a page, one per
 * fling. The top card lies over the next, which waits beneath it held in place, a little smaller, its foot
 * peeking; the top leaves upward under the finger, lifted and turned a little; up to two static edges
 * below say how many remain. A card arrives once a throw settles on it: the clock shows it, and the throw
 * ticks. Only vertical, so the Path's pager still takes horizontal swipes.
 */
@Composable
internal fun MonthDeckPager(
    wrap: MonthWrap,
    lookups: WrapLookups,
    clock: IntroClock,
    dayView: DayView,
    zone: ZoneId,
    aspect: SnapAspectCache?,
    onWeek: (LocalDate) -> Unit,
    onOpen: (PathMoment) -> Unit,
    onMore: (MonthSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberWrapCopy()
    val cards = remember(wrap, lookups) { MonthDeck.cards(wrap, lookups).filter { it != IntroSequence.End } }
    if (cards.isEmpty()) return
    val pager = rememberPagerState { cards.size }
    val month = remember(wrap.month, copy) { "${copy.monthName(wrap.month)} ${wrap.month.year}" }

    // Arrival: the card a throw settles on plays; the one it covered, or uncovered, lands. A throw lands on a tick.
    LaunchedEffect(pager, clock, cards) {
        var previous: IntroSequence? = null
        snapshotFlow { pager.settledPage }.collect { page ->
            val card = cards.getOrElse(page) { cards.last() }
            clock.show(card)
            if (previous != null && previous != card) clock.landed(card)
            previous = card
        }
    }

    BoxWithConstraints(modifier.onAnyTap(clock::skip)) {
        val page = maxHeight
        val below = cards.size - 1 - pager.settledPage
        MonthDeckEdges(MonthDeckMotion.edges(below), Modifier.fillMaxSize())
        VerticalPager(
            state = pager,
            key = { "${wrap.month}-${cards[it]}" },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val card = cards[index]
            val leads = MonthDeck.leadsInsights(card, cards)
            val head = (card as? IntroSequence.Section)?.let { s ->
                if (leads) MonthSections.insightsHead(copy) else MonthSections.head(s.section, wrap, lookups, dayView, copy)
            }
            MonthDeckCard(
                month = month,
                step = MonthDeckStep.of(card, cards),
                head = head,
                modifier =
                    Modifier
                        .zIndex((cards.size - index).toFloat())
                        .graphicsLayer {
                            val d = pager.getOffsetDistanceInPages(index)
                            val s = MonthDeckMotion.scale(d)
                            scaleX = s
                            scaleY = s
                            rotationZ = MonthDeckMotion.turn(d)
                            translationY = MonthDeckMotion.offset(d, page.toPx(), MonthDeckMotion.PEEK.dp.toPx())
                            transformOrigin = if (d > 0) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
                        },
            ) { room ->
                IntroHost(clock, card, { MonthDeck.beats(card, wrap, lookups, leads) }) {
                    when (card) {
                        IntroSequence.Cover -> MonthCover(wrap, lookups)
                        is IntroSequence.Section ->
                            MonthSectionContent(card.section, wrap, lookups, dayView, zone, aspect, onWeek, onOpen, onMore, leadsInsights = leads, onCard = true)
                        IntroSequence.End -> Unit
                    }
                }
            }
        }
    }
}

/** The cards past the next, as static edges below its foot, drawn behind the deck. */
@Composable
private fun MonthDeckEdges(
    count: Int,
    modifier: Modifier,
) {
    Box(modifier) {
        (count downTo 1).forEach { depth ->
            MonthDeckPaper(
                Modifier
                    .padding(MonthDeckLayout.inset)
                    .graphicsLayer {
                        val s = 1 - MonthDeckMotion.SHRINK * depth
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (MonthDeckMotion.PEEK * depth).dp.toPx()
                    },
            )
        }
    }
}
```

  - Fade an edge as the count changes: wrap each paper in `AnimatedVisibility(visible = depth <= count, enter = fadeIn(tween(200)), exit = fadeOut(tween(200)))` over `0 until MAX_EDGES`, gated by `rememberReduceMotion()`.
  - `cards[it].toString()` reads well for a data object or data class (`Cover`, `Section(section=GEM)`). The month in the key keeps it unique per month page (the #1242 trap).
  - Imports: `VerticalPager`, `rememberPagerState`, `snapshotFlow`, `zIndex`, `graphicsLayer`, `TransformOrigin`, `BoxWithConstraints`.

- [ ] **Step 2: `MonthPage` chooses.** Replace the Part 2 `intro: IntroClock?` parameter with what the page needs to build its own:

```kotlin
/** What a month page needs to play its intro: the launch's ledger and the haptics. Null: no intro. */
@Immutable
class MonthIntro(
    val ledger: IntroLedger,
    val haptics: IntroHapticPlaying?,
)
```

  and in `MonthPage(…, intro: MonthIntro? = null, focused: Boolean = true, …)`:

```kotlin
    val allowed = rememberIntroAllowed()
    val clock = remember(wrap.month, intro, allowed) { if (intro != null && allowed) IntroClock(wrap.month, intro.ledger, intro.haptics) else null }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    if (clock != null) {
        LaunchedEffect(clock) { clock.drive() }
        LaunchedEffect(clock, focused, state) { clock.isActive = focused && state.isAtLeast(Lifecycle.State.RESUMED) }
        DisposableEffect(clock) { onDispose { clock.isActive = false } }
        MonthDeckPager(wrap, lookups, clock, dayView, zone, aspect, onWeek, onOpen, onMore, modifier)
    } else {
        // The plain page: no clock, settled (as today).
        LazyColumn(…) { … }
    }
```

  The sheet stays below both. The Lab passes `MonthIntro(ledger, haptics)`, and its Replay now keys the page on `run` (`key(run) { MonthPage(…) }`).

- [ ] **Step 3: The Path wires it.**
  - **`PathViewModel`:**

    ```kotlin
    /** The month intros played this launch (#1242 D20): the Path tab's root entry lives as long as the launch. */
    val introLedger = IntroLedger()
    ```

  - **`PathScreen`:** pass `introLedger = viewModel.introLedger` to `PathContent`.
  - **`PathContent`** gains `introLedger: IntroLedger? = null` (previews pass nothing).

    ```kotlin
        val introHaptics = if (introLedger != null) rememberIntroVibrator() else null
        val intro = remember(introLedger, introHaptics) { introLedger?.let { MonthIntro(it, introHaptics) } }
    ```

    and in the month branch, `MonthPage(…, intro = intro, focused = pagerState.settledPage == index, aspect = aspect)`.

- [ ] **Step 4: The swipe and the strip tick (D24).** In `PathContent`:

```kotlin
    val tapHaptics = rememberTapHaptics()
    // A page steered to (strip tap, week link, focus request) ticks at the tap, not again as it settles.
    val steeredTo = remember { intArrayOf(-1) }
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            if (page == steeredTo[0]) steeredTo[0] = -1 else tapHaptics(TapHaptic.SELECTION)
        }
    }
```

  In the focus → pager effect, set `steeredTo[0] = index` before `animateScrollToPage`/`scrollToPage`. The strip's `onFocus` becomes `{ tapHaptics(TapHaptic.SELECTION); onFocusChange(it) }`. Read `PathStrip` first: if a tap on the already-focused item calls `onFocus`, skip the tick when `it == focus`.

- [ ] **Step 5: Gate and device.**
  - Run ktlint, `lintDebug`, the unit tests, and the screenshot compile. `PathScreenScreenshots` must be unchanged: previews pass no ledger and `rememberReduceMotion()` is true there, so it draws the plain page.
  - Install. The maintainer tries:
    - a real past month on the Path;
    - the cover plays, then throwing up deals each card with a tick;
    - hard flings move one card at a time;
    - swiping down brings the previous card back;
    - horizontal swipes from any card change the page with a tick, and a strip tap ticks once;
    - revisiting the month shows it settled;
    - backgrounding mid-garland stops the buzz;
    - with TalkBack, Remove animations or font size 1.5+, the plain page.

- [ ] **Step 6: Commit** `feat(android): deal the month's wrap as a deck of cards`, with the haptics changes as their own commit: `feat(android): tick the path's page swipes and strip taps`.

### Task 26: References, Arkaik, ship

- [ ] **References.** Add to `IntroScreenshots.kt` two settled card renders, 390 × 760 dp:
  - **`MonthDeckCover`:** `MonthDeckCard(month = "October 2026", step = MonthDeckStep(0, 9), head = null) { MonthCover(…) }`.
  - **`MonthDeckSection`:** the weeks card, with its head from `MonthSections.head`, `onCard = true`.

  No clock, so everything is settled.

- [ ] **Arkaik.** `get_node` the acceptance, then `update_node` with the full metadata (values, platformStatuses, the new gherkin):

  > Given a past month I haven't opened since launching the app, When its page comes into focus, Then it is dealt as a deck of cards: the cover first, its name rising and its garland drawing day by day from the 1st with a haptic tick on each slot; And each card I throw up lands with a tick and plays its own entrance once; And when I come back to that month before relaunching, its cards are settled; And the last card replays the month; And a tap skips, and Reduce Motion, a screen reader or a large text size show the plain page, settled.

  Also update the description to the deck's shape.

- [ ] **PR script** (`ship-1242-part3.sh`):
  - title `feat(android): deal the month's wrap as a deck of cards`;
  - "Part 3 of 8 for #1242";
  - labels, milestone, then `rebaseline-screenshots` alone.

---
# Part 4 — the persons (`feat/1242-android-intro-persons`)

The persons' entrance, row by row: the glyph pops, the name rises, then the ring draws from the 1st with a dot popping as the line reaches each day. On a card, their rows fill it (D13).

Start with `gh stack add feat/1242-android-intro-persons`.

### Task 27: The persons' score

**Files:**
- Modify: `features/path/wrap/intro/IntroScoreSections.kt`
- Test: `IntroScoreSectionsTest.kt`

Port of iOS `IntroScore.persons`/`dotPlace` (`IntroScore+Sections.swift`); tests port `IntroScoreSectionsTests.persons`/`.nobody`.

- [ ] **Step 1: Write the failing tests** (append):

```kotlin
    @Test
    fun `a person - glyph, name, then the ring, a dot as the line reaches each day; the next row after`() {
        val rings = listOf(DaySet(31, setOf(0, 15, 30)), DaySet(31, setOf(3)))
        val beats = IntroScore.persons(rings, hasLead = false)
        fun of(key: IntroKey) = beats.first { it.key == key }
        val glyph = of(IntroKey.Glyph(0))
        val name = of(IntroKey.Name(0))
        val ring = of(IntroKey.Ring(0))
        assertEquals(IntroScore.ROWS_AT, glyph.at, 0.0)
        assertEquals(IntroScore.PART_GAP, name.at - glyph.at, 1e-12)
        assertEquals(IntroScore.PART_GAP, ring.at - name.at, 1e-12)
        assertEquals(IntroScore.RING_SPAN, ring.duration, 0.0)
        assertNull(ring.tick)
        val dots = listOf(0, 15, 30).map { of(IntroKey.Dot(0, it)) }
        assertTrue(dots.zipWithNext { a, b -> b.at > a.at }.all { it })
        // The middle day sits at the ring's middle: the eased line reaches it half way through.
        assertEquals(ring.at + IntroScore.RING_SPAN / 2, dots[1].at, 1e-9)
        assertTrue(dots.all { it.tick == IntroTick.light })
        assertEquals(IntroScore.NEXT_ROW, of(IntroKey.Glyph(1)).at - (ring.at + IntroScore.RING_SPAN), 1e-12)
        assertTrue(beats.any { it.key == IntroKey.Dot(1, 3) })
        assertEquals(1 + rings.sumOf { 3 + it.days.size } + 1, beats.size)
        assertEquals(of(IntroKey.Ring(1)).let { it.at + it.duration }, of(IntroKey.More).at, 1e-12)
    }

    @Test
    fun `nobody named - the head and the invitation, nothing else`() {
        assertEquals(listOf(IntroKey.Header, IntroKey.Lead), IntroScore.persons(emptyList(), hasLead = true).map { it.key })
    }
```

  `DaySet` is `app.pbbls.android.core.model.wrap.DaySet(length, days)`. Read its constructor for the parameter order.

- [ ] **Step 2: Run; fail. Step 3: Implement** (append to `IntroScoreSections.kt`):

```kotlin
/**
 * The persons: per row its glyph, its name, then its ring's line drawn from the 1st, a beat on each day
 * present as the line reaches it, in day order; the next row once the ring has closed; *more* as the last
 * ring closes. With nobody named, the head and the invitation ([hasLead]).
 */
fun IntroScore.persons(
    rings: List<DaySet>,
    hasLead: Boolean,
): List<IntroBeat> {
    val beats = head(hasLead, insights = false).toMutableList()
    var at = IntroScore.ROWS_AT
    rings.forEachIndexed { row, days ->
        beats += IntroBeat(IntroKey.Glyph(row), at, IntroMotion.POP, IntroTick.light)
        beats += IntroBeat(IntroKey.Name(row), at + IntroScore.PART_GAP, IntroMotion.RISE, IntroTick.light)
        val ring = at + 2 * IntroScore.PART_GAP
        beats += IntroBeat(IntroKey.Ring(row), ring, IntroScore.RING_SPAN)
        days.days.sorted().forEach { day ->
            val reached = IntroScore.RING_SPAN * RevealMotion.easeInOutInverse(dotPlace(day, days.length))
            beats += IntroBeat(IntroKey.Dot(row, day), ring + reached, IntroMotion.POP, IntroTick.light)
        }
        if (row == rings.lastIndex) beats += IntroBeat(IntroKey.More, ring + IntroScore.RING_SPAN, IntroMotion.RISE)
        at = ring + IntroScore.RING_SPAN + IntroScore.NEXT_ROW
    }
    return beats
}

/** How far round its ring a day sits, 0…1: the middle of its slot, the ring opening before the 1st. */
fun IntroScore.dotPlace(
    day: Int,
    length: Int,
): Double = if (length > 0) (day + 0.5) / length else 0.0
```

- [ ] **Step 4: Run; pass. Commit** `feat(android): score the month persons' entrance`.

### Task 28: Rows and rings take a reveal (core)

**Files:**
- Modify: `core/ui/MarkRow.kt`, `core/ui/DaySetMarks.kt`, and `core/designsystem/` `Modifier.rule` if it needs an alpha.

- [ ] **Step 1: `MarkRow` takes modifiers for its columns.** Add after `markWidth`:

```kotlin
    /** The month intro's landing for each column (#1242): the mark, the words (title and subtitle), the count, the trailing view, the rule's alpha. Defaults draw as ever. */
    leadingModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    subtitleModifier: Modifier = Modifier,
    countModifier: Modifier = Modifier,
    trailingModifier: Modifier = Modifier,
    ruleAlpha: () -> Float = { 1f },
```

  - Apply them to the matching nodes: the mark `Box`, the title `Text`, the subtitle `Text`, the count `Text`, and a `Box(trailingModifier) { trailing() }`.
  - Give the trailing box `propagateMinConstraints = false` and no size, so the layout is unchanged.
  - The rule fades with the words: read `core/designsystem/Rule.kt` (`Modifier.rule`). If it has no alpha, add `alpha: () -> Float = { 1f }`, read in its draw lambda, and pass `ruleAlpha`.
  - A default `Modifier` adds no node, so settled renders are unchanged.

- [ ] **Step 2: `DaySetMarks` takes the ring's and the dots' progress.** Add:

```kotlin
    /** The ring's line drawn so far, 0…1, already eased (the month intro, #1242). */
    ringProgress: () -> Float = { 1f },
    /** A present day's dot's progress, 0…1 (a pop through [RevealMotion]). */
    dotProgress: (day: Int) -> Float = { 1f },
```

  - **Circle:**
    - The arc sweeps `(end − start) × ringProgress()`, nothing at 0.
    - The tick at the 1st is drawn once the progress is above 0.
    - Present days are drawn in **sorted** order (`days.days.sorted()`, the #1242 trap: a hash set's order made the half-drawn ring non-deterministic on iOS).
    - Each dot reads `p = dotProgress(day)`: skipped at 0; below 1, its alpha is `RevealMotion.popOpacity(p)` and its diameter × `RevealMotion.popScale(p)`.
  - **Row and calendar:** a present day's filled dot pops the same way. The ring and quiet marks stay.
  - Settled (all 1), the draws are exactly today's: dots don't overlap, so the order doesn't change pixels.

- [ ] **Step 3: Compile; the references are unchanged. Commit** `feat(android): let mark rows and day rings land in parts`.

### Task 29: The persons play, and fill their card

**Files:**
- Modify: `features/path/wrap/month/PersonsSection.kt`, `MonthDeck.kt` (beats), `MonthSectionContent.kt` (room)
- Test: `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/PersonsSectionTest.kt`

Port of iOS `PersonsSection.introBeats`, `rowSize` (`MonthPersons.swift`); tests port `IntroScoreSectionsTests.personsCap` and `MonthCardsTests.personsFill`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.features.path.wrap.intro.IntroKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonsSectionTest {
    @Test
    fun `the persons section names five at most`() {
        val beats = PersonsSection.introBeats(SpineFixtureWeek.monthWrap, SpineFixtureWeek.lookups)
        assertTrue(beats.any { it.key == IntroKey.Glyph(0) })
        assertTrue(beats.count { it.key is IntroKey.Glyph } <= 5)
    }

    @Test
    fun `on a card the persons share the room left by more, their glyphs and rings growing, within bounds`() {
        val roomy = PersonsSection.rowSize(room = 500f, rows = 5)
        assertEquals((500f - PersonsSection.MORE_RESERVE) / 5, roomy.height, 1e-4f)
        assertTrue(roomy.glyph > SpineLayoutTokens.MARK.value && roomy.glyph <= PersonsSection.GLYPH_CAP)
        assertTrue(roomy.ring > ROW_RING.value && roomy.ring <= PersonsSection.RING_CAP)
        val tight = PersonsSection.rowSize(room = 200f, rows = 5)
        assertEquals(SpineLayoutTokens.ROW_MIN.value, tight.height, 0f)
        assertEquals(SpineLayoutTokens.MARK.value, tight.glyph, 0f)
        assertEquals(ROW_RING.value, tight.ring, 0f)
        assertEquals(PersonsSection.ROW_CAP, PersonsSection.rowSize(room = 2000f, rows = 2).height, 0f)
    }
}
```

- [ ] **Step 2: Run; fail. Step 3: Implement.** In `PersonsSection.kt`:

```kotlin
/** The persons' pure parts (#1242; iOS `PersonsSection`'s statics), in dp. */
internal object PersonsSection {
    const val ROW_CAP = 96f
    const val GLYPH_CAP = 48f
    const val RING_CAP = 60f

    /** *more*'s room at the card's foot (iOS `MonthLayout.moreReserve`). */
    const val MORE_RESERVE = 56f

    data class RowSize(
        val height: Float,
        val glyph: Float,
        val ring: Float,
    )

    fun rows(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ) = wrap.persons.orEmpty().filter { lookups.soul(it.soulId) != null }

    fun introBeats(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<IntroBeat> {
        val rows = rows(wrap, lookups)
        return IntroScore.persons(rows.take(SHOWN).map { it.days }, hasLead = rows.isEmpty())
    }

    /** A person's row on a card (D13): its share of the room left by *more*, the glyph and the ring growing with it. */
    fun rowSize(
        room: Float,
        rows: Int,
    ): RowSize {
        val height = ((room - MORE_RESERVE) / maxOf(rows, 1)).coerceIn(SpineLayoutTokens.ROW_MIN.value, ROW_CAP)
        return RowSize(
            height,
            (height * 0.5f).coerceIn(SpineLayoutTokens.MARK.value, GLYPH_CAP),
            (height * 0.7f).coerceIn(ROW_RING.value, RING_CAP),
        )
    }
}
```

  - **The composable `PersonsSection` takes `room: Dp? = null`:**
    - With a room, each row is `MarkRow(minHeight = size.height.dp, …)`, its `SoulMark` `size.glyph.dp`, and its ring `ringSize = size.ring.dp`.
    - *more* stands at the foot: put a `Spacer(Modifier.weight(1f))` before `MonthMore` when on a card. `MonthCardRoom` lays the content out in the whole room when it fits.
  - **The reveal:** with `val reveal = currentIntroReveal()` and the row index `i`:
    - `leadingModifier = Modifier.introReveal(reveal, IntroKey.Glyph(i), POP)`
    - `titleModifier`, `countModifier` and `subtitleModifier` = `Modifier.introReveal(reveal, IntroKey.Name(i))`
    - `ruleAlpha = { reveal(IntroKey.Name(i)) }`
    - `DaySetMarks(…, ringProgress = { RevealMotion.easeInOut(reveal(IntroKey.Ring(i)).toDouble()).toFloat() }, dotProgress = { reveal(IntroKey.Dot(i, it)) })`
    - the invitation `Text` on `IntroKey.Lead` and `MonthMore` on `IntroKey.More`.
  - **The plumbing:**
    - `MonthDeck.beats` returns `PersonsSection.introBeats(wrap, lookups)` for the persons card.
    - `MonthSectionContent` passes its `room` (a new `room: Dp? = null` parameter) to `PersonsSection`.
    - `MonthDeckPager` passes the card's `room`.

- [ ] **Step 4: Run tests; compile. Step 5: The reference.** Add `IntroPersonsRing` to `IntroScreenshots.kt`: the persons section (not on a card) under `IntroClock.frozen(month, Section(PERSONS), PersonsSection.introBeats(…), elapsed = <row 0's ring half drawn>)`. Compute the elapsed time from the score: `ROWS_AT + 2 × PART_GAP + RING_SPAN / 2`, which is 0.32 + 0.18 + 0.225 = 0.725 s.
- [ ] **Step 6: Device, then ship.**
  - The maintainer tries the persons card: glyph, name, ring dot by dot, one row at a time; the rows filling the card.
  - PR "Part 4 of 8", with `rebaseline-screenshots` for `IntroPersonsRing`.
  - Commit `feat(android): play the month persons' entrance`.

---

# Part 5 — the gem (`feat/1242-android-intro-gem`)

The count runs up from 0, a tick per pebble, then the families' facets light around it, each family's name and share with its first facet.

### Task 30: The gem's score

**Files:** create `features/path/wrap/intro/IntroScoreGem.kt`; constants in `IntroScore.kt`; test `IntroScoreGemTest.kt`.

Port of iOS `IntroScore+Gem.swift`; tests port `IntroScoreGemTests` with the same facet ids and values.

- [ ] **Step 1: Write the failing tests.**

```kotlin
package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreGemTests`: ta-ta-ta-ta-ta · ta-ta-ta-ta · ta-ta-ta. */
class IntroScoreGemTest {
    @Test
    fun `a run that crosses the top sweeps clockwise through it, from after its widest gap`() {
        val facets = listOf("kite_0" to 0.0, "girdle_1" to 0.25, "star_6" to 6.5, "girdle_0" to -0.25)
        assertEquals(listOf("star_6", "girdle_0", "kite_0", "girdle_1"), IntroScore.sweep(facets))
        assertEquals(listOf("kite_3"), IntroScore.sweep(listOf("kite_3" to 3.0)))
    }

    @Test
    fun `the families that own facets, in the lead order, each with exactly its facets`() {
        val facets = mapOf("kite_0" to "joy", "star_0" to "joy", "kite_3" to "calm", "star_3" to null)
        val runs = IntroScore.gemRuns(listOf("calm", "anger", "joy"), facets)
        assertEquals(listOf("calm", "joy"), runs.map { it.slug })
        assertEquals(listOf("kite_3"), runs[0].facets)
        assertEquals(listOf("kite_0", "star_0"), runs[1].facets)
    }

    @Test
    fun `the count first, then facets 45 ms apart, a pause between families, each name with its first facet`() {
        val runs = listOf(IntroGemRun("joy", listOf("a", "b", "c")), IntroGemRun("calm", listOf("d", "e")))
        val beats = IntroScore.gem(runs, pebbles = 12)
        fun at(key: IntroKey) = beats.first { it.key == key }.at
        assertTrue(at(IntroKey.Lead) > at(IntroKey.Header))
        assertTrue(at(IntroKey.Blank) < at(IntroKey.Count))
        val count = beats.first { it.key == IntroKey.Count }
        assertEquals(count.at + count.duration + IntroScore.COUNT_AFTER, at(IntroKey.Facet("a")), 1e-12)
        assertEquals(IntroScore.FACET_GAP, at(IntroKey.Facet("b")) - at(IntroKey.Facet("a")), 1e-12)
        assertEquals(IntroScore.FACET_GAP + IntroScore.FAMILY_PAUSE, at(IntroKey.Facet("d")) - at(IntroKey.Facet("c")), 1e-12)
        assertEquals(at(IntroKey.Facet("a")), at(IntroKey.Label("joy")), 0.0)
        assertEquals(at(IntroKey.Facet("d")), at(IntroKey.Label("calm")), 0.0)
        assertEquals(at(IntroKey.Facet("e")), at(IntroKey.More), 0.0)
        assertTrue(beats.filter { it.key is IntroKey.Facet }.all { it.tick == IntroTick.facet })
    }

    @Test
    fun `the count rises to the month's pebbles slow-fast-slow, a tick per pebble, landing firm`() {
        val beats = IntroScore.gem(emptyList(), pebbles = 24)
        val count = beats.first { it.key == IntroKey.Count }
        assertEquals(IntroScore.COUNT_AT, count.at, 0.0)
        assertEquals(IntroScore.countSpan(24), count.duration, 0.0)
        val tallies = (1..24).map { t -> beats.first { it.key == IntroKey.Tally(t) } }
        assertTrue(tallies.dropLast(1).all { it.tick == IntroTick.light })
        assertEquals(IntroTick.firm, tallies.last().tick)
        assertEquals(count.at + count.duration, tallies.last().at, 1e-12)
        val gaps = tallies.zipWithNext { a, b -> b.at - a.at }
        assertTrue(gaps[11] < gaps[0] && gaps[11] < gaps.last())
        tallies.forEach { tally ->
            val pebble = (tally.key as IntroKey.Tally).pebble
            assertEquals(pebble, IntroScore.counted(24, (tally.at - count.at) / count.duration))
        }
        assertEquals(0, IntroScore.counted(24, 0.0))
    }

    @Test
    fun `a fuller month counts for longer, within bounds; an empty one has no tally`() {
        assertEquals(0.6, IntroScore.countSpan(0), 0.0)
        assertTrue(IntroScore.countSpan(50) > IntroScore.countSpan(10))
        assertEquals(1.2, IntroScore.countSpan(1000), 0.0)
        assertFalse(IntroScore.gem(emptyList(), 0).any { it.key is IntroKey.Tally })
    }
}
```

- [ ] **Step 2: Run; fail. Step 3: Implement.** Constants in `IntroScore`: `BLANK_AT = 0.32`, `COUNT_AT = 0.4`, `FACET_GAP = 0.045`, `FAMILY_PAUSE = 0.22`, `COUNT_AFTER = 0.16`. Then:

```kotlin
package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.ui.gem.GemGeometry
import kotlin.math.floor

/** A family of the gem in the intro: its slug and its facets in sweep order. */
data class IntroGemRun(
    val slug: String,
    val facets: List<String>,
)

/**
 * The gem (D10): its head and lead, the blank stone, the count counting up to the month's pebbles on the
 * garland's ease-in-out, a tick per pebble and a firm one as it lands; then each family's facets in turn,
 * its name and share with its first, a pause between families, and *more* with the last.
 */
fun IntroScore.gem(
    runs: List<IntroGemRun>,
    pebbles: Int,
): List<IntroBeat> {
    val beats = head(hasLead = true, insights = false).toMutableList()
    beats += IntroBeat(IntroKey.Blank, IntroScore.BLANK_AT, IntroMotion.RISE)
    val span = countSpan(pebbles)
    beats += IntroBeat(IntroKey.Count, IntroScore.COUNT_AT, span)
    for (tally in 1..pebbles) {
        val reached = span * RevealMotion.easeInOutInverse(tally.toDouble() / pebbles)
        beats += IntroBeat(IntroKey.Tally(tally), IntroScore.COUNT_AT + reached, 0.0, if (tally == pebbles) IntroTick.firm else IntroTick.light)
    }
    var at = IntroScore.COUNT_AT + span + IntroScore.COUNT_AFTER
    var last = at
    runs.forEachIndexed { index, run ->
        if (index > 0) at += IntroScore.FAMILY_PAUSE
        run.facets.forEachIndexed { place, facet ->
            if (place == 0) beats += IntroBeat(IntroKey.Label(run.slug), at, IntroMotion.RISE)
            beats += IntroBeat(IntroKey.Facet(facet), at, IntroMotion.LIGHT, IntroTick.facet)
            last = at
            at += IntroScore.FACET_GAP
        }
    }
    beats += IntroBeat(IntroKey.More, last, IntroMotion.RISE)
    return beats
}

/** How long the count takes: longer for a fuller month, within bounds. */
fun IntroScore.countSpan(pebbles: Int): Double = (0.5 + 0.012 * pebbles).coerceIn(0.6, 1.2)

/** The count shown at [progress], 0…1 of its beat: the pebbles the eased count has passed. */
fun IntroScore.counted(
    pebbles: Int,
    progress: Double,
): Int = floor(pebbles * RevealMotion.easeInOut(progress) + 1e-9).toInt()

/** The families in [order] that own facets in the server's allocation, each with its facets in sweep order. */
fun IntroScore.gemRuns(
    order: List<String>,
    facets: Map<String, String?>,
): List<IntroGemRun> =
    order.mapNotNull { slug ->
        val owned =
            GemGeometry.allocated.mapNotNull { id ->
                val theta = GemGeometry.byId[id]?.theta
                if (facets[id] == slug && theta != null) id to theta else null
            }
        if (owned.isEmpty()) null else IntroGemRun(slug, sweep(owned))
    }

/** A run's facets clockwise from the top, starting after the widest gap, so a run across the top lights as one sweep. */
fun IntroScore.sweep(facets: List<Pair<String, Double>>): List<String> {
    val turn = GemGeometry.N.toDouble()
    val around = facets.map { (id, theta) -> id to ((theta % turn) + turn) % turn }.sortedBy { it.second }
    if (around.size <= 1) return around.map { it.first }
    val gaps = around.indices.map { i -> if (i == 0) around[0].second + turn - around.last().second else around[i].second - around[i - 1].second }
    val start = gaps.indices.maxBy { gaps[it] }
    return around.indices.map { around[(start + it) % around.size].first }
}
```

  `GemGeometry.N` is 7 (iOS `sides`); `theta` is in sevenths of a turn. Use `maxBy`, not `maxOf`: it returns the first maximum, as Swift's `max(by:)` does for strict `<`. If a tie test fails, compare with Swift: `max(by:)` returns the **last** of equal maxima. Make the Kotlin pick the same element and pin it with the test.

- [ ] **Step 4: Run; pass. Commit** `feat(android): score the month gem's count and facets`.

### Task 31: The gem lights

**Files:** `GemCanvas.kt`, `EmotionGem.kt`, `MonthDeck.kt` (beats); reference `IntroGemSecondFamily`.

- [ ] **Step 1: `GemCanvas` takes `reveal: IntroReveal = IntroReveal.settled`.**
  - **Facets:** each facet's fill reads `p = reveal(IntroKey.Facet(f.id))`. Below 1, it's `lerp(blankFill, fill, RevealMotion.easeOut(p))`, where `blankFill` is the table's paper (the colour the table draws in), so the ground-coloured seams stay visible (iOS: "blank facets fill with the table's paper"). Settled, the fill is unchanged.
  - **The blank stone:** the seams, the table and the facets' edges are drawn under `reveal(IntroKey.Blank)`, a rise. Apply it as alpha on the whole canvas (`Modifier.graphicsLayer`) when below 1, via `Modifier.introReveal(reveal, IntroKey.Blank)` on the `Canvas`.
  - **The count:** `count: String` becomes `count: Int`, and the canvas draws `IntroScore.counted(count, reveal(IntroKey.Count).toDouble())` while below 1, else `count`.
  - **The labels:** each family's label pair draws at alpha `RevealMotion.easeOut(reveal(IntroKey.Label(slug)))` and offset `riseOffset × (1 − eased)`, only below 1.
- [ ] **Step 2: `EmotionGem` passes `currentIntroReveal()`.**
  - The lead `Text` lands on `IntroKey.Lead`, *more* on `IntroKey.More`.
  - Add `EmotionGemBeats.introBeats(wrap) = IntroScore.gem(IntroScore.gemRuns(gem.lead?.order.orEmpty(), gem.facets), wrap.pebbles)`, and wire it into `MonthDeck.beats`.
- [ ] **Step 3: The reference** `IntroGemSecondFamily`: the gem section under a frozen clock, midway through the second family. Elapsed = the time of the second run's middle facet, from the score.
- [ ] **Step 4: Gate, device:**
  - the count running up then landing firm;
  - the families lighting ta-ta-ta-ta-ta · ta-ta-ta-ta · ta-ta-ta;
  - each name with its first facet.

  PR "Part 5 of 8", `rebaseline-screenshots`. Commit `feat(android): light the month's gem in its intro`.

---

# Part 6 — tone and hours (`feat/1242-android-intro-insights`)

### Task 32: The insights' score

**Files:** create `IntroScoreInsights.kt`; constants; test `IntroScoreInsightsTest.kt`, and `app/src/test/kotlin/app/pbbls/android/features/path/wrap/month/InsightsBeatsTest.kt`.

Port of iOS `IntroScore+Insights.swift`; tests port `IntroScoreInsightsTests` (all six) and `IntroInsightsBeatsTests` (on `SpineFixtureWeek.monthWrap`, emptying `tone`/`hours` with `copy`).

- [ ] **Step 1: Tests**, with values as the Swift ones:
  - **tone:** `ToneDay(1,0,0), ToneDay(0,0,0), ToneDay(0,2,0)`: the sweep at `SWEEP_AT` for `SWEEP_SPAN`; day 0 at the sweep's start; no `Day(1)`; day 2 at the sweep's end, ticking light.
  - **insights shift:** the first beat is `Insights` with `arrive`; `Header` at `INSIGHTS_SHIFT` with no tick; the sweep at shift + `SWEEP_AT`.
  - **insightsHead:** one beat, `Insights`, `arrive`, duration 0.
  - **hours(4):** four stones from `STONES_AT`, increasing; `Dial` present.
  - **ranks:** `ranks(listOf(23.5, 0.25, 12.0, 0.25)) == listOf(3, 0, 2, 1)`.
  - **31 bright days:** `IntroHaptics.events(tone(...)).size == 32`.
  - **On the fixture:** `ToneFigure.introBeats(wrap, insights)` and `HoursFigure.introBeats(wrap, lookups, insights)` give nothing when empty and not leading, `[Insights]` when leading, and contain `Sweep`/`Dial` on the fixture.
- [ ] **Step 2: Implement:**
  - Constants: `SWEEP_AT = 0.32`, `SWEEP_SPAN = 1.2`, `DIAL_AT = 0.32`, `STONES_AT = 0.52`, `STONES_SPAN = 1.2`.
  - `insightsHead()`.
  - `tone(days, insights)`: `head(true, insights)`, then the sweep at `shift + SWEEP_AT`. Each day with `up + even + down > 0` at `start + SWEEP_SPAN × easeInOutInverse(i / (n − 1))`, ticking light, for `POP`.
  - `hours(stones, insights)`: `head(true, insights)`, the dial at `shift + DIAL_AT`, then `IntroCurve.times(stones, STONES_SPAN)` from `shift + STONES_AT`, ticking light.
  - `ranks(hours)`: a stable sort by value, ties by index.
  - `ToneFigure.introBeats` and `HoursFigure.introBeats` (in an `internal object` beside each composable): `emptyList()`, or `insightsHead()` when the figure draws nothing, as iOS.
- [ ] **Step 3: Commit** `feat(android): score the month tone's sweep and hours' stones`.

### Task 33: Tone sweeps, hours land

**Files:** `ToneFigure.kt`, `HoursFigure.kt`, `MonthDeck.kt`, `MonthSectionContent.kt`; reference `IntroToneSweep`.

- [ ] **Step 1: `ToneFigure`** reads `currentIntroReveal()`.
  - **The sweep:** the day loop and the numerals draw inside `clipRect(right = size.width × RevealMotion.easeInOut(reveal(Sweep)))` while below 1. Always left to right: the canvas ignores RTL, as iOS's mask does.
  - **The pulse:** an even day's ring gets a pulse at `IntroKey.Day(i)`: while `0 < p < 1`, an extra circle of radius `r × (1 + 0.6p)` at alpha `1 − p`.
  - **The words:** the sentence `Text` lands on `Lead`.
  - **The head:** stays drawn (D8).
- [ ] **Step 2: `HoursFigure`.**
  - **The dial:** its rings, night wash, hour numbers and busiest window draw at alpha `easeOut(reveal(Dial))` when below 1.
  - **The stones:** each stone, with its collection dot and wait spiral, pops at `IntroKey.Stone(rank)`, where `rank = IntroScore.ranks(items' hours)[index]`.
  - **The words:** the sentence lands on `Lead`.
- [ ] **Step 3: Beats.** `MonthDeck.beats` for `TONE`/`HOURS` uses `ToneFigure.introBeats(wrap, leads)` / `HoursFigure.introBeats(wrap, lookups, leads)`.
- [ ] **Step 4: The reference** `IntroToneSweep`: the tone at half its sweep, elapsed `SWEEP_AT + SWEEP_SPAN / 2`.
- [ ] **Step 5: Gate, device** (the sweep ticking each day with pebbles; the even days' pulse; the hours stone by stone through the day). PR "Part 6 of 8". Commit `feat(android): sweep the month's tone and land its hours in the intro`.

---

# Part 7 — the signatures and the end card (`feat/1242-android-intro-signatures`)

D10, D13, D14, D16, D17 on the deck: the weeks as a calendar of columns, the milestones stamped with a ripple, the domains' gauges swelling, rows sharing the room, and the end card with Replay.

### Task 34: The signatures' score

**Files:** create `IntroScoreSignatures.kt`; constants; test `IntroScoreSignaturesTest.kt`.

Port of iOS `IntroScore+Signatures.swift`; tests port `IntroScoreSignaturesTests.weekColumn`, `.emptyWeekColumn`, `.milestone`, `.domains`, plus `MonthDeckTests.end` and `IntroHapticsTests.swells`, with the same inputs and expected relations.

- [ ] **Step 1: Write the tests.** Translate each Swift `#expect` to an `assertEquals`/`assertTrue` with the same inputs:
  - the columns `[nil, 0, 2, nil, nil, nil, nil]` (2 stones) and `[0, 0, 0, 0, 0, 0, 1]` (1 stone);
  - the milestones `(3, 1)` and `(1, 0)`;
  - the domains `[1.0, 0.5]` with more;
  - `end()`: `Header` ticking arrive; `Replay` at `ROWS_AT`, light.
- [ ] **Step 2: Implement:**
  - Constants: `STONE_GAP = 0.05`, `DAY_GAP = 0.035`, `PIP_GAP = 0.045`, `CAIRN_AFTER = 0.08`, `MARK_GAP = 0.05`, `NEXT_WEEK = 0.12`, `STAMP_SPAN = 0.24`, `NEXT_MILESTONE = 0.14`, `SWELL_FROM = 0.15f`, `SWELL_SHARPNESS = 0.3f`.
  - `data class IntroWeekColumn(val days: List<Int?>, val stones: Int)` and `data class IntroMilestoneShape(val intensity: Int, val souls: Int)`.
  - `weekColumns`, `milestones`, `domains`, `end`, `fillSpan(share) = 0.25 + 0.45 × clamp(share)`, `swellTo(share) = 0.25 + 0.5 × clamp(share)`, and the private `souls(row, count, after, beats)`, line for line from the Swift.
- [ ] **Step 3: Run; pass. Commit** `feat(android): score the month cards' signatures`.

### Task 35: The weeks' calendar of columns

**Files:**
- Create: `features/path/wrap/month/WeekColumn.kt` (pure) and `WeeksColumns.kt` (composable)
- Modify: `core/ui/marks/CairnMark.kt` (`built: (stone: Int) -> Float`)
- Modify: `WeeksSummary.kt` (on a card, the columns)
- Modify: `WrapCopy.kt` (`weekColumnMonth`)
- Test: `WeekColumnTest.kt`, on the server's own October (`month-full.json`), as iOS does.

- [ ] **Step 1: Test** (port `MonthCardsTests.weekColumns` and `.weekColumnShape`):

```kotlin
package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.CoverPebble
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.MonthWeek
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekColumnTest {
    private val october =
        WrapJson.decodeFromString<MonthWrapDto>(requireNotNull(javaClass.getResource("/month-full.json")).readText()).toModel()

    @Test
    fun `a week's column places each day by its date - outside the month blank, then empty or its pebbles`() {
        val columns = WeekColumn.columns(october)
        assertEquals(5, columns.size)
        val first = columns.first()
        assertTrue(first.days.take(3).all { it == WeekColumnDay.Outside })
        assertNotEquals(WeekColumnDay.Outside, first.days[3])
        val last = columns.last()
        assertEquals(WeekColumnDay.Outside, last.days[6])
        assertNotEquals(WeekColumnDay.Outside, last.days[5])
        val october1 = october.cover?.days?.firstOrNull { it.day == 0 }?.pebbles.orEmpty()
        assertEquals(if (october1.isEmpty()) WeekColumnDay.Empty else WeekColumnDay.Pebbles(october1), first.days[3])
    }

    @Test
    fun `a column's intro shows three pebbles at most a day, and its cairn's stones`() {
        val pebble = CoverPebble("m", null, 2, ValencePolarity.NEUTRAL)
        val quiet = DayWeight(0, 0)
        val days = listOf(DayWeight(5, 2)) + List(6) { quiet }
        val week = MonthWeek(LocalDate.of(2026, 10, 5), 4, 10, 5, emptyList(), days)
        val column = WeekColumn(week, listOf(WeekColumnDay.Pebbles(List(5) { pebble })) + List(6) { WeekColumnDay.Empty })
        assertEquals(3, column.shape.days.first())
        assertTrue(column.shape.days.drop(1).all { it == 0 })
        assertEquals(1, column.shape.stones)
        assertEquals(0, WeekColumn(week.copy(pebbles = 0), column.days).shape.stones)
    }
}
```

- [ ] **Step 2: Implement `WeekColumn.kt`,** a port of iOS `WeekColumnDay`/`WeekColumn`:
  - `sealed interface WeekColumnDay { data object Outside; data object Empty; data class Pebbles(val pebbles: List<CoverPebble>) }`
  - `WeekColumn(week, days)` with `PIPS = 3` and `columns(wrap)`: each of the seven days from the week's Monday, `YearMonth.from(date) == wrap.month`, the cover's pebbles for `date.dayOfMonth − 1`.
  - `shape = IntroWeekColumn(days.map { Outside -> null; Empty -> 0; Pebbles -> min(size, 3) }, if (week.pebbles == 0) 0 else CairnGeometry.widths(week.days).size)`.
- [ ] **Step 3: `CairnMark` takes `built: (stone: Int) -> Float = { 1f }`.** A stone with built 0 is skipped; below 1 it pops (`RevealMotion`), drawn bottom-up as today.
- [ ] **Step 4: `WeeksColumns.kt`,** a port of iOS `WeeksColumns.swift`:
  - **Measures:** the cell is `clamp((room − 34 − 64 − 16) / 7, 18, 40)` dp.
  - **The weekday margin:** 16 dp of weekday letters, Monday first. Use the app's weekday-letter helper (`grep -rn "weekdayLetter\|dayLetters" features/path`).
  - **The columns:** one per week, sharing the width. Each holds:
    - its Monday's number (`type.dayNumeral`) over `copy.weekColumnMonth(weekStart)` (`type.dayLetter`, 34 dp), landing on `Words(i)`;
    - a `Canvas` 7 cells tall. An empty day draws a socket (`dotMark(…, DotStyle.SOCKET, ink3)`) popping on `Rest(i, d)`. A day with pebbles draws up to 3 mini seals, inked as on the garland (`rememberSealPrint(…, SealVariant.MINI)`), popping on `Pip(i, d, k)`;
    - the cairn (64 dp) built by `Cairn(i, k)`, or an empty week's dotted stone popping on `Cairn(i, 0)`.
  - **A tap** on a column focuses its week. Its one semantics node reads `"$range, $line"` as a button.
  - **`WrapCopy.weekColumnMonth(day)`:** the month's abbreviation as the range prints it. Reuse the private abbreviation helper `range()` uses.
  - **On a card,** `WeeksSummary` draws `WeeksColumns(room)` in place of its rows, and `introBeats(wrap) = IntroScore.weekColumns(WeekColumn.columns(wrap).map { it.shape })`.
- [ ] **Step 5: Commit** `feat(android): set the month's weeks as a calendar of columns on their card`.

### Task 36: Milestones stamp, domains swell, rows share the room

**Files:** `MilestonesSection.kt`, `DomainsSection.kt`, `MonthDeck.kt`; tests `MilestonesSectionTest.kt`, `DomainsSectionTest.kt`.

- [ ] **Step 1: Tests:**
  - `MonthCardsTests.milestonesIntro`: each milestone's `Impact(row)` ticks `seal(its intensity)`, and its date comes before its name.
  - `.milestonesFill` with `rowSize(room: Float?, rows)`:
    - `LIST_ROW = 56`, `ROW_CAP = 104`; `listStone 0.62`, `listSnap 40`, the soul side 14.
    - On a card: `stone = clamp(height / 100, 0.62, 1)`, `snap = clamp(height × 0.7, 40, 76)`, `soul = min(14 × stone / 0.62, 20)`.
    - `rowSize(400, 5).height == 80`.
  - `.domainsIntro`: the first gauge's duration is `fillSpan(1)` and longer than the second's; its swell's `to` is larger; *more* is present.
- [ ] **Step 2: Milestones.**
  - **The stamp:** the leading `LitStone` box takes a `graphicsLayer { scale = stampScale(p); alpha = stampOpacity(p) }` from `reveal(Stamp(row))`, only below 1.
  - **The ripple:** as the impact lands *while the card plays* (`LocalIntroStage.current?.let { it.clock.isPlaying(it.sequence) }`), mount a `StoneRipple(valence, blank = palette == null, token = …, width = stoneWidth)` at alpha `MILESTONE_RINGS`, at the stone's own pace, by a `LaunchedEffect` keyed on `reveal(Impact(row)) >= 1f`. Read `SpinePebble`'s ripple token pattern and copy it. A card dealt settled never ripples.
  - **The words:** the date lands on `Date(row)`, the name on `Name(row)`. The polaroid pops on `Mark(row, 0)`; the souls pop on `Mark(row, k)`, each in a `Box` with `introReveal(… POP)`.
  - **Beats:** `introBeats(wrap, lookups) = IntroScore.milestones(moments.map { IntroMilestoneShape(it.valence.intensity, if (it.firstSnapPath == null) min(souls, 4) else 1) })`.
  - **On a card,** rows use `rowSize(room, n)`.
- [ ] **Step 3: Domains.**
  - **The gauge:** the bar fills on `Gauge(row)` with `easeOut`. `ProportionBar`'s first segment is `share × p` and the track the rest; read `ProportionBar` and pass the scaled fractions.
  - **The rest of the row:** the name, count and rule land on `Row(row)`.
  - **Beats:** `introBeats = IntroScore.domains(shares = shown.map { it.count / top }, more = true)`, over the five rows the section draws, with `top` the first's count. *more* is always drawn, as on iOS; with no positive top, `IntroScore.domains(emptyList(), more = true)`.
  - **On a card,** *more* stands at the foot.
- [ ] **Step 4: Commit** `feat(android): stamp the month's milestones and swell its domains`.

### Task 37: The end card and Replay

**Files:**
- Create `MonthDeckEnd.kt`
- Modify `MonthCover.kt`: lift `MonthCoverGarland`, as its own pure-move commit first
- Modify `MonthDeck.kt` (end beats; deal `END`), `MonthPage.kt` (replay)
- Modify strings: `path_wrap_replay_month` "Replay the month" / "Rejouer le mois"

- [ ] **Step 1 (pure move): lift the garland with its middle out of `MonthCover`** into `MonthCoverGarland(wrap, cover, lookups, reveal)`. `MonthCover` calls it. The references are unchanged. Commit `quality(android): lift the month cover's garland into its own composable`.
- [ ] **Step 2: Strings and copy.** `WrapCopy.replayMonth()`, EN and FR at the `path_wrap_line_no_pebbles` anchor, with a `WrapCopyTest` assertion.
- [ ] **Step 3: `MonthDeckEnd`.**
  - It shows the whole garland, settled: wrap it in `CompositionLocalProvider(LocalIntroStage provides null)`, so no clock reaches it. With no cover, the month's name stands in its place.
  - Under it, a stock `OutlinedButton` with a `↺` icon (`Icons.AutoMirrored.Filled.Replay` or `Icons.Default.Replay`) and "Replay the month", landing on `IntroKey.Replay`.
  - Everything is centred in the room, `MonthDeckLayout.endGap` apart.
- [ ] **Step 4: The deck deals `END`.**
  - Remove Part 3's `filter { it != IntroSequence.End }`.
  - `MonthDeck.beats(END) = IntroScore.end()`.
  - The end card has the margin and stepper but no title band.
- [ ] **Step 5: Replay.** `MonthPage` keeps `var run by remember(wrap.month) { mutableIntStateOf(0) }`, keys the clock on `run`, and wraps the deck in `key(wrap.month, run)`: the month is in the key (the #1242 trap). `onReplay` is:

```kotlin
{
    scope.launch {
        // The skip from this same tap lands first; replay on the next frame.
        withFrameNanos { }
        clock?.isActive = false
        intro?.ledger?.reset(wrap.month)
        run += 1
    }
}
```

  The page's `focused`/`resumed` effects stay outside the key, so the new deck inherits them (the visibility trap).

- [ ] **Step 6: References:**
  - **`MonthDeckWeeks`:** the weeks card, settled, room 600.
  - **`MonthDeckEnd`:** the end card.
- [ ] **Step 7: Gate, device:**
  - **Weeks:** the column by column build.
  - **Milestones:** the stamps with a tick at impact and one ripple; the polaroids.
  - **Domains:** the gauges swelling and ticking as they stop.
  - **The end card:** Replay deals again from the cover; swiping to the next month after a replay stays there.

  PR "Part 7 of 8", `rebaseline-screenshots`. Commit `feat(android): end the month's deck with a replay`.

---

# Part 8 — Pebbles' haptics switch (`feat/1242-android-haptics-switch`)

The second comment on #1242 and D23. Every haptic in the app asks the switch first. **This part resolves #1242.**

### Task 38: The preference

**Files:** create `core/data/HapticsPreference.kt`; test `app/src/test/kotlin/app/pbbls/android/core/data/HapticsPreferenceTest.kt` (with `testing/InMemorySettings` or the way `DisplayPreferences` is tested; `grep -rn "DisplayPreferences(" app/src/test`).

- [ ] **Step 1: Test:**
  - on by default;
  - `setOn(false)` reads false at once;
  - the key is `haptics.enabled` (iOS's).
- [ ] **Step 2: Implement** like `DisplayPreferences`:

```kotlin
/**
 * Pebbles' own haptics switch (#1242, the #1241 decision of 2026-10-26), kept on this device: every
 * vibration the app plays asks it first. On until turned off; saved as it is switched.
 */
@Singleton
class HapticsPreference
    @Inject
    constructor(
        private val settings: SettingsStore,
    ) {
        val isOn: Boolean get() = settings[ENABLED] ?: true

        fun setOn(value: Boolean) = settings.set(ENABLED, value)

        private companion object {
            val ENABLED = booleanPreferencesKey("haptics.enabled")
        }
    }
```

- [ ] **Step 3: Commit** `feat(android): add pebbles' own haptics switch`.

### Task 39: Settings → Haptics → Vibrations

**Files:** `features/profile/SettingsScreen.kt`, `SettingsViewModel.kt`, strings (EN/FR), the Settings screenshot (re-baselined by CI).

- [ ] **Step 1:**
  - **Strings:** `settings_haptics_section` ("Haptics" / "Retours haptiques"), `settings_haptics_vibrations` ("Vibrations" / "Vibrations"), `settings_haptics_footer` ("Every vibration in Pebbles, on this phone." / "Toutes les vibrations de Pebbles, sur ce téléphone."), at the `settings_wallpaper_colors_body` anchor.
  - **`SettingsViewModel`:** inject `HapticsPreference`; add `hapticsOn` and `onHapticsChange(value)`, mirroring `useWallpaperColors` (device-local, outside the form's dirty/save cycle).
  - **`SettingsScreen`:** a section under Appearance, with the same stock `ListItem` + `Switch` as the wallpaper row, and the footer as supporting text.
  - **The ViewModel test:** extend the wallpaper case's pattern to the switch.
- [ ] **Step 2: Commit** `feat(android): switch pebbles' vibrations off in settings`.

### Task 40: Every haptic asks it

**Files:**
- `core/data/TapHaptics.kt`: `rememberTapHaptics(enabled: () -> Boolean = { true })`, returning a no-op when off
- `features/path/capture/CaptureFlowScreen.kt`, `features/path/complete/CompleteFlowScreen.kt`, `features/path/spine/SpinePebble.kt`, `features/path/create/pickers/ValencePickerSheet.kt`, and their ViewModels, exposing `hapticsOn`
- `RootScreen.kt` / `RootViewModel.kt` → `KarmaOverlayHost`/`KarmaEarnedCapsule` and `AchievementMomentOverlay` take `haptics: Boolean`
- `features/glyph/store/GlyphDetailDrawer.kt` (and its ViewModel) → `SlideToConfirm(haptics = …)`
- `features/path/PathScreen.kt` / `PathViewModel.kt`: `rememberIntroVibrator(enabled = { viewModel.hapticsOn })` and `rememberTapHaptics { … }` in `PathContent` (as a parameter, `hapticsOn: () -> Boolean = { true }`)

- [ ] **Step 1: Thread the flag** through each ViewModel (inject `HapticsPreference`, expose `val hapticsOn: Boolean get() = haptics.isOn`).
  - Read it at call time (a lambda), so a change in Settings applies without a restart.
  - `SpinePebble` gets it from `PathContent`; `ValencePickerSheet` from its host's ViewModel.
  - Run `grep -rn "rememberTapHaptics()\|performHapticFeedback\|LocalHapticFeedback" app/src/main` to make sure no site is left.
- [ ] **Step 2: Tests:** a `TapHaptics` unit test (the off lambda never calls `performHapticFeedback`: inject a `View` fake or test the wrapper's branching), and each touched ViewModel's test still passes.
- [ ] **Step 3: Gate, device:**
  - turn Vibrations off: the month's intro, the strip, Capture's taps, karma and the glyph slider are all silent;
  - turn it on: they're back.

  Re-baseline Settings on CI.

- [ ] **Step 4: Commit** `feat(android): ask pebbles' haptics switch before every vibration`.

### Task 41: Ship Part 8 and the stack

- [ ] **PR:**
  - title `feat(android): add pebbles' own haptics switch`;
  - body **`Resolves #1242`**, then "Part 8 of 8";
  - labels `feat ui android no-lab-note`, M64, `rebaseline-screenshots` alone.
- [ ] **Hand the maintainer the merge** once CI is green on every head and each bot commit is folded in (fast-forward each branch to its remote, then `gh stack rebase --no-trunk`, then `gh stack push`, then approve held runs): `gh stack merge <bottom PR> --yes --squash`.

---

## The device pass (Pixel 8), before the stack merges

The maintainer's phone, with the real account; the intro writes nothing.

- [ ] **Feel and tiers:**
  - the cover, each card, the end card;
  - the Lab's C/W/V override on the cover and the domains;
  - tune `IntroComposition`'s mapping or `IntroTick` only on the maintainer's word, and record the values below.
- [ ] **Drift:** `dumpsys vibrator_manager` durations against the scored spans, for the cover and the domains card. If the cover runs past 30 ms late, add a measured per-primitive overhead to `IntroComposition`'s pauses.
- [ ] **Size:** the domains card with five full gauges compiles to about 600 primitives, past the HAL's 254. Check that it plays whole (the framework should split it). If it stops short, play each gauge's train as its own composition from the clock.
- [ ] **System switch:** Android's Touch feedback off, for tiers C, W and V.
- [ ] **Interruptions:**
  - a swipe away mid-garland;
  - backgrounding;
  - a tap on a milestone mid-card (its held card opens, the buzz stops);
  - Replay, then a swipe to another month.
- [ ] **Once per launch:**
  - back to the month: settled;
  - a card never reached plays when first reached;
  - after a relaunch, it plays again.
- [ ] **Accessibility:** TalkBack on, Remove animations on, font size 1.5 and up: the plain page, settled, silent.
- [ ] **Flings:** hard throws move one card each; horizontal swipes from every card move the Path.
- [ ] **Smoothness:** the profiler's frame timeline during the garland and the gem. If frames drop, cache the landed marks (spec Risks).

## As built

Measured on the maintainer's Pixel 8 (Android 17) through `dumpsys vibrator_manager` and gfxinfo, Parts 1–8, 2026-10-26/27.

**Haptics**
- **Drift, the cover:** 44 primitives scored at 4375 ms played in 4396 and 4403 ms, 21–28 ms late. Under the 30 ms bar, so no per-primitive overhead is subtracted.
- **Drift, the swells:** the domains card with five full gauges is 726 primitives (716 LOW_TICKs), not the ~600 this plan estimated.
  - Played whole, so the framework does split compositions past 254.
  - It ran 11 015 ms against 11 329 declared: back-to-back LOW_TICKs play about 0.44 ms short each, and the last tick came 314 ms early.
  - Fixed in Part 7: tier C cuts a card's primitives after each swell's train. Each later segment is fired from the frame clock a floor (25 ms) before its scored time (`IntroComposition.segments`, `IntroSegmentQueue`).
  - Measured after: every segment starts 1–4 ms after its score, 145 primitives at most.
  - A first try fired at the card's 50 ms lead. The review showed that would cut a train's loudest ticks on a vibrator that keeps time.
  - Decision log, 2026-10-27.
- **The system switch:** with Touch feedback off, C, W and V are all silent (`USAGE_TOUCH` follows it, unlike iOS Core Haptics).
- **Feel:**
  - The maintainer chose C as the most natural, above all on the domains' swell.
  - W and V are accepted as fallbacks.
  - The persons and gem cards were confirmed on the device.
  - Weeks through the end card, and the switch, await the device pass.
- **Tuned constants:** none yet.

**Frames:** gfxinfo during the cover p90 17–21 ms; the hours card p90 13 ms (0.61 % jank). No cached layer was needed.

**Plan drift (the code is right; these snippets are stale)**
- Tasks 17 and 25 hand-write the drive effects. Every clock is driven through `IntroDrive(clock, active)`.
- `MonthDeckLayout`'s constants are CAPS (`INSET`, `END_GAP`). `MonthDeckPaper` carries its own inset.
- Task 31's gem: the blank stone fades as one layer, and only the count and its label rise (a slide of the stone isn't iOS's). The label spread breaks out of its loop, as iOS does.
- Line ~4453: Swift's `max(by:)` keeps the *first* of equal maxima, not the last.
- Part 6:
  - the tone's sweep masks the whole chart with iOS's `sweepWidth`;
  - an even day's ring pulses as `scaleEffect(1 + t)`;
  - both figures draw through `drawWithCache`.
- Part 7:
  - **Dropped:** no `MonthDeckWeeks` reference. `MonthDeckSection` renders the weeks card on its room.
  - **Ports matching iOS:** the domains' top is the largest of the rows shown; a milestone's intensity is `pebble?.intensity ?: 1`; the ripple's gate is a `snapshotFlow(...).drop(1)` (iOS's `onChange`), tested as `IntroStage.strikes`.
  - **Replay:** cross-fades over 0.3 s, as iOS does.
    - Each dealt deck owns its clock (`MonthDeal`), because `AnimatedContent` keeps the outgoing deck live where SwiftUI freezes it.
    - The old clock is retired (`IntroClock.retire()`) with its cards' phases pinned.
    - `run` is `rememberSaveable`.
    - The hidden Replay takes no press.
- **Fixes placed in lower layers:**
  - a tap on the top card fell through to the card held beneath (Part 3: each card takes the touch on its page);
  - a person row's share is floored to whole pixels (Part 4), as the milestones' is;
  - `PathViewModel.onFocus` ignores a page the Path doesn't hold. An empty past week's column is tappable, as on iOS, but that week is a strip spacer.
- **Part 8:**
  - the rows' long-press buzz (pebble, soul, collection) asks the switch too, as iOS's context menus do;
  - with the switch read off mid-card, the intro stops the card;
  - the Spine Lab's probes are left ungated (debug only).

**Lessons learned**
- **Offscreen layers clip.** A `graphicsLayer` with alpha below 1 draws offscreen, clipped to its bounds, so anything drawn past them (LitStone's rings and halo, a week column's two-line date) is cut.
  - Use `CompositingStrategy.ModulateAlpha`, or put the layer on the overflowing content itself.
- **Siblings hit-test top-down.** Compose hit-tests siblings from the top until one has pointer input at the point. A stacked card needs its own pointer input, consuming nothing, or taps reach the card beneath.
- **A composition's timing is relative.** Each pause runs from the declared end of the primitive before, and real durations differ by device and by texture. Re-anchor long runs from the frame clock rather than correcting by a constant.
- **`AnimatedContent` keeps the outgoing content live and recomposing.** Whatever fades out must read only its own state, never the page's.
- **Test clocks:** under a Compose test rule the frame clock is virtual while `IntroClock` reads `System.nanoTime`. Whole-app tests opt into the plain month (`AppUiTest.drawPlainMonthPages()`), and the replay tests drive their taps knowing cards never land on their own.
- **`{ true }` defaults hide missed callers.** A `hapticsOn: () -> Boolean = { true }` default let Edit Moment's valence picker skip the switch. Production composables take the switch without a default; previews pass `{ true }`.

**Follow-ups (not in this stack)**
- **iOS:** `PathModel.focus` should ignore a week that isn't a page (the Android guard above).
- **Android parity:** `HoursLayout.lanes` has no lane cap (iOS: 4 inner, 3 outer), and `ToneFigure` numbers days over the month rather than over the tone's days.
- **Lower layers:**
  - `drawSeal` allocates a rim stroke (and a hatch path for tiny lowlight seals) per call;
  - `ProportionBar` could take draw-phase weights;
  - `SOCKET` and `SEAL_LAYER_PAD` are duplicated across the garland, the week columns and the hours.
- **Baseline profiles:** they name pre-stack signatures. Regenerate them (`android-baseline-profile`).
