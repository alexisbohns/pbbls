# Android Pebble Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Android pebble detail page becomes **the card**:
- **Recto (the moment):** the Spine card with a leaf.
- **Verso (the pebble):** the valence lockup, the lit stone, round domain postmarks and a collection band.
- **Footer:** stays still, with privacy and the turn button.
- **Bare moment:** a plain page with a "Not a pebble yet" place, into which the card prints on completion.
- **Navigation:** it opens from the Spine on the face you pressed.

**Architecture:** Five stacked PRs on issue #1135.
1. **Part 1** builds the card's components and swaps them in for a pebble. The turn is a plain button swap.
2. **Part 2** adds the animated turn and the face key.
3. **Part 3** adds the bare moment and its completion.
4. **Part 4** adds the container transform from the Spine and predictive back.
5. **Part 5** deletes the orphaned read view and updates the docs.

Pure logic (ring fit, people naming, valence lockup, turn settling, noise) is TDD'd on the JVM. Composables are proven by `@PreviewTest` screenshots, then on the Pixel 8.

**Tech Stack:** Kotlin, Jetpack Compose (material3 1.5.0-alpha27), Navigation 3 (1.2.0), `android.graphics`, JUnit 4 + Robolectric (NATIVE graphics), Compose Preview Screenshot Testing.

**Spec:** `docs/superpowers/specs/2026-10-20-android-pebble-card-design.md`. Issue #1135, milestone M64 · New Path, labels `feat`, `android`, `ui`, plus `no-lab-note`, because the Arkaik map covers the change.

**Sources of numbers (read, never copy wholesale):**
- The final mocks, built during the design rounds, in `/private/tmp/claude-503/-Users-alexis-code-pbbls/92498e59-5f20-48ec-b4d0-e4bbb7159d30/scratchpad/detail/` (also published at https://claude.ai/artifact/DG1GnRv6V2sicesPu2y6t1):
  - Recto, shell, footer and bare page: `synthesis/parts/style.css` and `synthesis/parts/app.js`.
  - Verso, postmarks, band and turn button: `back-token3/token.css`, `back-token3/ring.js` and `back-token3/index.html`.
  - The miniature of the other face: `back-label/index.html`.
- Where a mock is in CSS px, use dp. Where the plan gives a number, the plan wins.

**Environment notes for every task:**
- **Paths.** The repo root is `/Users/alexis/code/pbbls`. `…/android/` means `apps/android/app/src/main/kotlin/app/pbbls/android/`. Unit tests live under `apps/android/app/src/test/kotlin/app/pbbls/android/`, screenshot previews under `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/`, and strings in `apps/android/app/src/main/res/values{,-fr}/strings.xml`.
- **Gradle.** Run `export ANDROID_HOME=$HOME/Library/Android/sdk` first, then run `./gradlew` from `apps/android`.
  - Unit tests: `./gradlew testDebugUnitTest --tests '<fqcn>'`
  - Gates: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`
  - Format: `./gradlew ktlintFormat`
- **Architecture tests.** `ThemeLiteralsTest` fails on a `Color(0x…)`, a literal `RoundedCornerShape(` radius or a `.copy(fontSize = …)` in `features/` and `core/ui`: every type size is a token in `core/designsystem`. `ArchitectureBoundaryTest` fails on `core → features` or `features → features` imports.
- **Screenshot references are never rendered locally.** A new `@PreviewTest` ships without a reference, and CI's `rebaseline-screenshots` label creates it. Changed previews of the old read view turn the gate red, which is expected: re-baseline.
- **The Pixel 8 is connected.** Use `adb devices` to confirm, then `./gradlew installDebug`. It is the maintainer's real account, so ask before any write: completing a pebble, deleting or editing. Reading, turning and opening are fine.
- **Commits** are conventional, lowercase, with no period, and end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01S5qk3FP8ZKzoigiDjCDdo2
  ```
- **PRs.** Ask the maintainer before opening each PR. Before Part 2's branch, read the `gh-stack` skill and the memory note on the rebaseline bot. PR bodies never cite Kritik finding ids.

---

## File map

| Part | Create | Modify | Delete |
|---|---|---|---|
| 1 | `features/path/card/logic/{RingFit,ValenceLockup,PostmarkNoise}.kt`, `features/path/card/{CardLayout,PebbleCard,CardRecto,CardVerso,CardLeaf,DomainPostmark,CollectionBand,CardFooter,FaceMiniature}.kt`, tests for each logic file, `screenshotTest/…/PebbleCardScreenshots.kt`, `screenshotTest/…/PebbleCardFixtures.kt` | `core/designsystem/SpineTypography.kt`, `features/path/spine/SpineCard.kt`, `features/path/spine/logic/PeopleLine.kt`, `features/path/valence/ValenceWord.kt`, `core/ui/render/stone/LitStone.kt`, `features/path/PebbleDetailScreen.kt`, `features/path/PebbleDetailViewModel.kt`, `navigation/PebblesSceneStrategies.kt`, `navigation/PebblesKeys.kt` (KDoc), `navigation/PebblesEntryProvider.kt`, strings, `screenshotTest/…/PebbleDetailScreenshots.kt`, `screenshotTest/…/ListDetailScreenshots.kt`, `test/…/spine/logic/PeopleLineTest.kt` | |
| 2 | `core/model/CardFace.kt`, `features/path/card/logic/TurnSettle.kt`, its test, `test/…/navigation/NavigatorCloseMomentDetailTest.kt` | `navigation/{PebblesKeys,Navigator,PebblesEntryProvider}.kt`, `features/path/spine/{SpinePebble,SpineWeek}.kt`, `features/path/PathScreen.kt`, `features/path/card/PebbleCard.kt`, `PebbleDetailScreen.kt` | |
| 3 | `features/path/card/{BareMomentPage,CardCompletion}.kt`, previews | `PebbleDetailViewModel.kt`, `test/…/features/path/PebbleDetailViewModelTest.kt`, `PebbleDetailScreen.kt`, strings | |
| 4 | | `RootScreen.kt` (or wherever `NavDisplay` lives), `navigation/NavTransitions.kt`, `features/path/spine/SpineCard.kt`, `features/path/card/PebbleCard.kt` | |
| 5 | | `apps/android/CLAUDE.md`, `docs/decisions/log.md`, `screenshotTest/…/PebbleDetailScreenshots.kt` | `features/path/read/{PebbleReadView,PebbleReadBanner,PebbleReadTitle,PebblePrivacyBadge,BannerAspect,PebbleSnapFrame}.kt` and their unused strings (keep `PebbleReadPetroglyph.kt`, `PetroglyphColors.kt`) |

---

# Part 1: The card, static

Branch: `feat/1135-android-pebble-card`. It already exists and holds the spec commit; this plan is committed on it too. PR title: `feat(android): the pebble detail becomes the card`. Body: `Part 1 of #1135` (only Part 5 resolves the issue). Labels: `feat`, `android`, `ui`, `no-lab-note`. Milestone: M64.

### Task 1.1: `RingFit`, the postmark's ring arithmetic

**Files:**
- Create: `…/android/features/path/card/logic/RingFit.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/RingFitTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.path.card.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RingFitTest {
    /** A monospaced fake: every character advances 0.6 em. */
    private val measure: (String, Float) -> Float = { text, sizeSp -> text.length * sizeSp * 0.6f }

    private fun seamError(fit: RingFit, circumference: Float): Float =
        kotlin.math.abs(measure(fit.text, fit.sizeSp) + fit.trackingPx * fit.text.length - circumference)

    @Test
    fun `a short name repeats as often as the circle allows, closing exactly`() {
        val fit = RingFits.fit("Santé", circumferencePx = 180f, locale = Locale.FRENCH, measure = measure)
        assertEquals(4, fit.repetitions)
        assertEquals(8.5f, fit.sizeSp)
        assertEquals("SANTÉ · ".repeat(4), fit.text)
        assertTrue(seamError(fit, 180f) < 0.5f)
    }

    @Test
    fun `a long name steps the size down before it falls under two repetitions`() {
        val fit = RingFits.fit("Current events", circumferencePx = 170f, locale = Locale.ENGLISH, measure = measure)
        assertEquals(2, fit.repetitions)
        assertEquals(8f, fit.sizeSp)
        assertTrue(seamError(fit, 170f) < 0.5f)
    }

    @Test
    fun `a name that cannot ring twice even at the smallest size is set once`() {
        val fit = RingFits.fit("Spirituality", circumferencePx = 60f, locale = Locale.ENGLISH, measure = measure)
        assertEquals(1, fit.repetitions)
        assertEquals(7.5f, fit.sizeSp)
        assertEquals("SPIRITUALITY · ", fit.text)
        assertEquals(0f, fit.trackingPx)
    }

    @Test
    fun `tracking is never negative`() {
        for (c in listOf(90f, 120f, 163.2f, 200f, 260f)) {
            val fit = RingFits.fit("Friends", circumferencePx = c, locale = Locale.ENGLISH, measure = measure)
            assertTrue("tracking at $c", fit.trackingPx >= 0f)
        }
    }

    @Test
    fun `the name is set in the locale's capitals`() {
        val fit = RingFits.fit("Bien-être", circumferencePx = 200f, locale = Locale.FRENCH, measure = measure)
        assertTrue(fit.text.startsWith("BIEN-ÊTRE · "))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'app.pbbls.android.features.path.card.logic.RingFitTest'`. Expected: compilation failure, because `RingFits` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.features.path.card.logic

import java.util.Locale
import kotlin.math.floor

/**
 * How a domain's name rings a postmark (spec §Verso 4): [text] is the name in
 * capitals, repeated with " · " after each, set at [sizeSp] with [trackingPx]
 * added after every character so the ring closes exactly and the seam is an
 * ordinary gap.
 */
data class RingFit(
    val text: String,
    val repetitions: Int,
    val sizeSp: Float,
    val trackingPx: Float,
)

object RingFits {
    /** Tried in order; a name that cannot ring twice at the last is set once. */
    val SIZES_SP = listOf(8.5f, 8f, 7.5f)
    const val SEPARATOR = " · "
    private const val MIN_REPETITIONS = 2

    /**
     * @param circumferencePx the circle the text's baseline-centre runs on.
     * @param measure the advance of a string at a size in sp, in px: a
     *   `TextMeasurer` on device, a fake in tests.
     */
    fun fit(
        name: String,
        circumferencePx: Float,
        locale: Locale,
        measure: (text: String, sizeSp: Float) -> Float,
    ): RingFit {
        val unit = name.uppercase(locale) + SEPARATOR
        for (size in SIZES_SP) {
            val width = measure(unit, size)
            val repetitions = floor(circumferencePx / width).toInt()
            if (repetitions >= MIN_REPETITIONS) {
                val text = unit.repeat(repetitions)
                return RingFit(text, repetitions, size, (circumferencePx - measure(text, size)) / text.length)
            }
        }
        val size = SIZES_SP.last()
        return RingFit(unit, 1, size, ((circumferencePx - measure(unit, size)) / unit.length).coerceAtLeast(0f))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes.** Expected: 5 tests PASS.

- [ ] **Step 5: Commit** with the message `feat(android): ring fit for the card's domain postmarks`.

### Task 1.2: `PeopleLines.of` names up to eight on the card

**Files:**
- Modify: `…/android/features/path/spine/logic/PeopleLine.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/spine/logic/PeopleLineTest.kt`

- [ ] **Step 1: Add the failing tests** at the end of `PeopleLineTest`:

```kotlin
    @Test
    fun `the held card names everyone up to eight`() {
        val line = PeopleLines.of(ids(8), ids(8), en, namedInFull = PeopleLines.NAMED_ON_CARD)
        assertEquals(ids(8), line.named)
        assertEquals(0, line.others)
    }

    @Test
    fun `past eight the card falls back to the counting rule`() {
        val line = PeopleLines.of(ids(9), ids(9), en, namedInFull = PeopleLines.NAMED_ON_CARD)
        assertEquals(ids(2), line.named)
        assertEquals(7, line.others)
        assertEquals(5, line.marks.size)
        assertEquals(2, line.overflow)
    }
```

- [ ] **Step 2: Run it.** `PeopleLineTest` should fail to compile, because the `namedInFull` parameter is unknown.

- [ ] **Step 3: Implement.** In `PeopleLines`:
  - Make `NAMED_IN_FULL` public as `const val NAMED_IN_FULL = 3`.
  - Add `const val NAMED_ON_CARD = 8` with the KDoc `/** The held card has room to name more (pebble card spec §Recto). */`.
  - Give `of` a trailing parameter `namedInFull: Int = NAMED_IN_FULL`.
  - Use it in place of the constant in the `if (ranked.size <= …)` test.

  Nothing else changes, so the Spine's call sites keep three.

- [ ] **Step 4: Run `PeopleLineTest`.** Expected: all tests pass, old and new.

- [ ] **Step 5: Commit** with the message `feat(android): people line can name up to eight`.

### Task 1.3: `ValenceLockup`, the verso's top as data

**Files:**
- Create: `…/android/features/path/card/logic/ValenceLockup.kt`
- Modify: `…/android/features/path/valence/ValenceWord.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/ValenceLockupTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.path.card.logic

import app.pbbls.android.R
import app.pbbls.android.core.model.Valence
import org.junit.Assert.assertEquals
import org.junit.Test

class ValenceLockupTest {
    @Test
    fun `every valence maps to the record flow's lockup`() {
        val expected =
            mapOf(
                Valence.HIGHLIGHT_LARGE to ValenceLockup(R.string.valence_highlight, false, 1f, R.string.valence_span_month, LockupInk.STONE_RAMP),
                Valence.HIGHLIGHT_MEDIUM to ValenceLockup(R.string.valence_highlight, false, 44f / 56f, R.string.valence_span_week, LockupInk.STONE_RAMP),
                Valence.HIGHLIGHT_SMALL to ValenceLockup(R.string.valence_highlight, true, 34f / 56f, R.string.valence_span_day, LockupInk.STONE_RAMP),
                Valence.NEUTRAL_LARGE to ValenceLockup(R.string.valence_headline_neutral, false, 1f, R.string.valence_span_month, LockupInk.PRIMARY),
                Valence.NEUTRAL_MEDIUM to ValenceLockup(R.string.valence_headline_neutral, false, 44f / 56f, R.string.valence_span_week, LockupInk.PRIMARY),
                Valence.NEUTRAL_SMALL to ValenceLockup(R.string.valence_headline_neutral, true, 34f / 56f, R.string.valence_span_day, LockupInk.PRIMARY),
                Valence.LOWLIGHT_LARGE to ValenceLockup(R.string.valence_lowlight, false, 1f, R.string.valence_span_month, LockupInk.INK),
                Valence.LOWLIGHT_MEDIUM to ValenceLockup(R.string.valence_lowlight, false, 44f / 56f, R.string.valence_span_week, LockupInk.INK),
                Valence.LOWLIGHT_SMALL to ValenceLockup(R.string.valence_lowlight, true, 34f / 56f, R.string.valence_span_day, LockupInk.INK),
            )
        Valence.entries.forEach { assertEquals(it.name, expected.getValue(it), ValenceLockups.of(it)) }
    }
}
```

- [ ] **Step 2: Run it.** Expected: compilation failure.

- [ ] **Step 3: Implement.** In `ValenceWord.kt`:
  - Change `private fun valenceWordRes` to `internal fun valenceWordRes`.
  - Give `ValenceWord` a trailing parameter `brush: Brush? = null`, and use `brush ?: ValenceStoneStyles.headlineInk(valence.polarity, scheme)` as the style's brush.
  - Add `lowercase: Boolean = valence.sizeGroup == ValenceSizeGroup.SMALL` in place of the inline test.

  The record flow passes neither, so it is unchanged. Then create:

```kotlin
package app.pbbls.android.features.path.card.logic

import androidx.annotation.StringRes
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.features.path.valence.valenceSpanRes
import app.pbbls.android.features.path.valence.valenceWordRes
import app.pbbls.android.features.path.valence.valenceWordScale

/** How the verso's lockup paints its word (spec §Verso 1). */
enum class LockupInk { STONE_RAMP, INK, PRIMARY }

/** The verso's top, as the record flow's lockup states it (`ValenceWord` + span). */
data class ValenceLockup(
    @StringRes val wordRes: Int,
    val lowercase: Boolean,
    val scale: Float,
    @StringRes val spanRes: Int,
    val ink: LockupInk,
)

object ValenceLockups {
    fun of(valence: Valence): ValenceLockup =
        ValenceLockup(
            wordRes = valenceWordRes(valence.polarity),
            lowercase = valence.sizeGroup == ValenceSizeGroup.SMALL,
            scale = valenceWordScale(valence.sizeGroup),
            spanRes = valenceSpanRes(valence.sizeGroup),
            ink =
                when (valence.polarity) {
                    ValencePolarity.HIGHLIGHT -> LockupInk.STONE_RAMP
                    ValencePolarity.NEUTRAL -> LockupInk.PRIMARY
                    ValencePolarity.LOWLIGHT -> LockupInk.INK
                },
        )
}
```

- [ ] **Step 4: Run `ValenceLockupTest` and the valence tests** (`--tests 'app.pbbls.android.features.path.valence.*'`). Expected: PASS.

- [ ] **Step 5: Commit** with the message `feat(android): valence lockup for the card's verso`.

### Task 1.4: `PostmarkNoise`, the ink's restrained texture

The spec asks for pits and a density drift without a `DstIn` layer, because HWUI ignores that on the Pixel 8. The texture is therefore drawn **over** the stamp as paper-coloured speckles: a `drawImage` of an alpha mask tinted with the panel's `stock`, clipped to the stamp's circle. Over ink it reads as pits and thin ink; over bare paper it is invisible. It uses SrcOver only, so software and hardware agree. This is a deliberate deviation from the spec's "pre-baked image": the mask is generated deterministically in code, so no binary asset is needed. Record it in the PR body.

**Files:**
- Create: `…/android/features/path/card/logic/PostmarkNoise.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/PostmarkNoiseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.features.path.card.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostmarkNoiseTest {
    @Test
    fun `the same seed gives the same mask`() {
        assertArrayEquals(PostmarkNoise.alphas(64, seed = 7L), PostmarkNoise.alphas(64, seed = 7L))
    }

    @Test
    fun `different seeds give different masks`() {
        assertTrue(!PostmarkNoise.alphas(64, seed = 7L).contentEquals(PostmarkNoise.alphas(64, seed = 8L)))
    }

    @Test
    fun `the mask is restrained`() {
        val a = PostmarkNoise.alphas(128, seed = 3L)
        assertEquals(128 * 128, a.size)
        // Density never drops ink below 0.83 except at pits; pits are rare.
        val pits = a.count { it >= PostmarkNoise.PIT_ALPHA }
        val drift = a.filter { it < PostmarkNoise.PIT_ALPHA }
        assertTrue("pits ${pits.toFloat() / a.size}", pits.toFloat() / a.size in 0.01f..0.06f)
        assertTrue("drift max ${drift.max()}", drift.max() <= (255 * (1f - PostmarkNoise.MIN_DENSITY)).toInt() + 1)
    }
}
```

- [ ] **Step 2: Run it.** Expected: compilation failure.

- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.features.path.card.logic

import kotlin.math.floor
import kotlin.random.Random

/**
 * The postmark ink's texture as an alpha mask of paper laid over the ink
 * (spec §Verso 4): a smooth drift that thins the ink to at most
 * 1 − [MIN_DENSITY], plus rare pits where the paper shows through
 * ([PIT_ALPHA]). Deterministic per seed, so each stamp keeps its own pattern
 * across frames and devices.
 */
object PostmarkNoise {
    const val MIN_DENSITY = 0.83f
    const val PIT_ALPHA = 200
    private const val PIT_RATE = 0.03f
    private const val CELL = 8

    fun alphas(
        size: Int,
        seed: Long,
    ): IntArray {
        val random = Random(seed)
        val grid = size / CELL + 2
        val lattice = FloatArray(grid * grid) { random.nextFloat() }
        val maxDrift = 255f * (1f - MIN_DENSITY)
        return IntArray(size * size) { i ->
            if (random.nextFloat() < PIT_RATE) {
                PIT_ALPHA + random.nextInt(256 - PIT_ALPHA)
            } else {
                val x = (i % size).toFloat() / CELL
                val y = (i / size).toFloat() / CELL
                val x0 = floor(x).toInt()
                val y0 = floor(y).toInt()
                val tx = smooth(x - x0)
                val ty = smooth(y - y0)
                fun at(cx: Int, cy: Int) = lattice[cy * grid + cx]
                val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
                val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
                ((top + (bottom - top) * ty) * maxDrift).toInt()
            }
        }
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)
}
```

- [ ] **Step 4: Run the test.** Expected: PASS. If the pit rate falls outside 1–6 % for seed 3, the random stream is being consumed per pixel as written; adjust only `PIT_RATE`, never the test.

- [ ] **Step 5: Commit** with the message `feat(android): postmark ink noise`.

### Task 1.5: Card tokens, geometry and strings

**Files:**
- Modify: `…/android/core/designsystem/SpineTypography.kt`, both `strings.xml` files
- Create: `…/android/features/path/card/CardLayout.kt`

- [ ] **Step 1: Typography.** In `SpineTypography`, after `cardOthers`, add these with the existing `ysabeau(...)` helper and `SMALL_CAPS` / `LINING` / `TABULAR` constants. Ysabeau weights must go through the same per-weight family the file already uses (Part 2 lesson of the Spine plan).

```kotlin
    // The held card (pebble card spec): fixed sizes, not card units.
    val cardFooterGrade = ysabeau(10.5.sp, 650, 0.16f.em, features = SMALL_CAPS)
    val cardFooterNote = ysabeau(12.5.sp, 380, italic = true)
    val cardTurnTarget = ysabeau(13.sp, 380, italic = true)
    val cardLeafDateline = ysabeau(10.5.sp, 600, 0.18f.em, features = "$SMALL_CAPS, $LINING")
    val cardLeafBody = ysabeau(17.sp, 380, lineHeight = 27.sp)
    val cardLockupSpan = ysabeau(10.sp, 650, 0.22f.em, features = SMALL_CAPS)
    val cardEmotion = ysabeau(20.sp, 400)
    val cardBandName = ysabeau(14.sp, 400, italic = true)
    val bareTitle = ysabeau(34.sp, 300, (-0.02f).em, lineHeight = 38.sp)
    val placeBody = ysabeau(16.sp, 380, lineHeight = 24.sp)

    /** The postmark ring: capitals (not small caps, too small at 4 px), sized by [RingFit]. */
    fun postmarkRing(sizeSp: Float) = ysabeau(sizeSp.sp, 650, features = LINING)
```

  If `SMALL_CAPS + ", " + LINING` is the file's idiom (as `rollNumeral` uses it), use that form instead of the template string.

- [ ] **Step 2: Geometry.**

```kotlin
package app.pbbls.android.features.path.card

import androidx.compose.ui.unit.dp

/** The held card's geometry, in dp from the final mocks (pebble card spec). */
internal object CardLayout {
    val MAX_WIDTH = 440.dp
    val PAGE_MARGIN = 16.dp

    /** Card units on the held card are not capped (the Spine caps them at 4.4 dp). */
    const val U_DIVISOR = 70f

    val FOOTER_PADDING_H = 16.dp
    val FOOTER_PADDING_V = 12.dp
    val FOOTER_GAP = 12.dp
    val TURN_TARGET = 48.dp
    val MINIATURE_WIDTH = 20.dp
    val MINIATURE_HEIGHT = 28.dp
    const val MINIATURE_TILT_DEG = -7f

    val VERSO_STONE_WIDTH = 150.dp
    val POSTMARK_DIAMETER = 72.dp
    val POSTMARK_GAP = 6.dp
    val POSTMARK_GLYPH = 38.dp
    val POSTMARK_OUTER_RING = 1.6.dp
    val POSTMARK_INNER_RING = 0.8.dp

    /** From the outer ring to the ring text's centre line. */
    val POSTMARK_TEXT_INSET = 9.dp
    val POSTMARK_ROW_TO_BAND = 18.dp
    val BAND_MIN_HEIGHT = 40.dp
    const val NOISE_SIZE_PX = 128
}
```

- [ ] **Step 3: Strings.** Add these to `values/strings.xml` and `values-fr/strings.xml`, near the `valence_` block, with a comment `<!-- The held card (#1135) -->`:

| key | en | fr |
|---|---|---|
| `card_turn` | Turn | Retourner |
| `card_turn_to_pebble` | Pebble | Galet |
| `card_turn_to_moment` | Moment | Moment |
| `card_turn_action_to_pebble` | Turn over to your pebble | Retourner vers ton galet |
| `card_turn_action_to_moment` | Turn over to the moment | Retourner vers le moment |
| `card_verso_heading` | Your pebble | Ton galet |
| `card_domains_a11y` | Life domains | Domaines de vie |
| `card_collections_a11y` | Collections | Collections |
| `bare_place_kicker` | Not a pebble yet | Pas encore un galet |
| `bare_place_body` | Its card prints here once you say what it meant: a feeling and its weight, the parts of your life it touched, who was there. | Sa carte s\'imprimera ici quand tu diras ce qu\'il a compté : un sentiment et son poids, les parts de ta vie qu\'il a touchées, qui était là. |

  Mark the Part 3 strings `tools:ignore="UnusedResources"` until Part 3 uses them, as the Spine plan did. `LocalizationParityTest` must stay green.

- [ ] **Step 4: Run** `./gradlew testDebugUnitTest --tests '*LocalizationParityTest' --tests '*ThemeLiteralsTest'`. Expected: PASS.

- [ ] **Step 5: Commit** with the message `feat(android): card tokens, geometry and strings`.

### Task 1.6: Split `SpineCard` into shell and face (a pure move)

The held card needs the shell (shadow, stock, coloured edge, clip) to stay still while the face (printed border and content) turns, with the footer between them. Today `SpineCard` draws both in one `Box`. This task is a **move** with no visual change on the Path: the Spine screenshots must not change. Do not change any number.

**Files:** Modify `…/android/features/path/spine/SpineCard.kt`.

- [ ] **Step 1: Extract `SpineCardShell`.**

```kotlin
/**
 * The card's body without its print: shadow, Night ring, painted edge, stock, clip.
 * [content] lays out inside the clip. The Spine wraps one [SpineCardFace]; the held
 * card wraps a turning face and its still footer (pebble card spec §The card).
 */
@Composable
internal fun SpineCardShell(
    palette: EmotionPalette?,
    polarity: ValencePolarity,
    intensity: Int,
    modifier: Modifier = Modifier,
    button: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

  It holds the `drawBehind` (shadow, night ring, edge), the `background(colors.stock, shape)`, `clip(shape)` and `then(button)`, all moved verbatim from `SpineCard`. It computes `stone`, `edge`, `shape` and `shadowPaints` as `SpineCard` does today.

- [ ] **Step 2: Extract `SpineCardFace`.**

```kotlin
/**
 * The card's print: the inset border, the field, the legend, the index and the rail,
 * in card units [u]. [leaf] lays out under the legend (the held card's words).
 */
@Composable
internal fun SpineCardFace(
    moment: PathMoment,
    palette: EmotionPalette?,
    u: Dp,
    ratio: Float,
    photoUrl: String?,
    thumbUrl: String?,
    weekday: String,
    time: String,
    emotionName: String,
    people: (@Composable (u: Dp) -> Unit)?,
    modifier: Modifier = Modifier,
    leaf: (@Composable (u: Dp) -> Unit)? = null,
)
```

  It holds the border `drawBehind`, the `Column` (field and legend), the index `Column` and the `Rail`, moved verbatim. When `leaf != null`, it renders `leaf(u)` as the last child of the outer `Column`, after the legend `Column`, full width. The outer column's bottom padding stays `u * 6.2f` in both cases.

- [ ] **Step 3: Recompose `SpineCard`** as `BoxWithConstraints { width; u; SpineCardShell(…, Modifier.width(cardWidth), button) { SpineCardFace(…, u = u, ratio = animatedRatio) } }`. Keep its public signature unchanged. Add two trailing parameters for the held card: `width: Dp? = null` (when set, used instead of `CardSizes.size(...)`) and `uMax: Dp = U_MAX`. Keep `u = min(cardWidth / U_DIVISOR, uMax)`.

- [ ] **Step 4: Verify the move.**
  - Run `./gradlew ktlintCheck testDebugUnitTest`.
  - Run `./gradlew validateDebugScreenshotTest` on this branch, then on `main` (`git stash`, check out, run, come back). Dozens of previews fail locally on `main` too, because the macOS renders differ from CI's. The check is the **diff**: the set of failing `Spine*` and `LitStone*` previews must be identical on both runs.
  - If the set differs, the move changed something: fix it before committing. CI's gate is the final word.

- [ ] **Step 5: Commit** with the message `quality(android): split the spine card into shell and face`. It is a move, so it is its own commit, per the stack rules.

### Task 1.7: `LitStone` at any width

**Files:** Modify `…/android/core/ui/render/stone/LitStone.kt` (and `StoneRings` / `stoneBox` if they read `STONE_WIDTH_DP` directly).

- [ ] **Step 1:** Run `grep -n 'STONE_WIDTH_DP' -r apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/render/stone`. List every use.

- [ ] **Step 2: Add the parameter.** Give `LitStone` a trailing parameter `width: Dp = StoneGeometry.STONE_WIDTH_DP.getValue(valence.sizeGroup).dp`.
  - Use it for `widthPx`.
  - Pass it to `Modifier.stoneBox(size, width)`: add a `width` parameter to `stoneBox` that defaults to the same value.
  - Pass it to `StoneRings`, so rings and halo scale with the box. The rings' stroke widths stay in dp; they are hairlines on purpose.
  - The bitmap cache key already includes `widthPx`.

- [ ] **Step 3: Preview.** In `LitStoneScreenshots.kt`, add `@PreviewTest` `LitStoneVersoLight` and `LitStoneVersoNight`: HIGHLIGHT_LARGE joy and LOWLIGHT_SMALL fear at `width = 150.dp`.

- [ ] **Step 4: Run** `./gradlew ktlintCheck testDebugUnitTest`. The existing stone previews must not change, because the default width equals today's. Commit with the message `feat(android): lit stone at any width`.

### Task 1.8: `DomainPostmark`

**Files:**
- Create: `…/android/features/path/card/DomainPostmark.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/DomainPostmarkPixelTest.kt`

- [ ] **Step 1: Write the composable.**

```kotlin
/**
 * One life domain as a round ink stamp (pebble card spec §Verso 4): the glyph in the
 * middle, the name ringing the edge per [RingFits], an outer and an inner ring, in
 * [ink]. Paper-coloured noise ([PostmarkNoise], tinted [paper], offset by [seed])
 * is laid over the ink so it reads as pressed, not printed. Not clickable; the
 * ring is decorative and hidden from TalkBack (the verso's list reads the name).
 */
@Composable
internal fun DomainPostmark(
    name: String,
    strokes: List<GlyphStroke>?,
    viewBox: String?,
    ink: Color,
    paper: Color,
    seed: Long,
    diameter: Dp,
    modifier: Modifier = Modifier,
)
```

  Body:
  - A `Box(modifier.size(diameter).clearAndSetSemantics {})`.
  - **Canvas 1 (rings and text):**
    - Draw the outer circle stroked `POSTMARK_OUTER_RING` and the inner circle stroked `POSTMARK_INNER_RING`. The inner circle's radius is `r − 2 × POSTMARK_TEXT_INSET`.
    - Set the text centre line radius `rt = r − POSTMARK_TEXT_INSET`, and compute `RingFits.fit(name, 2π·rt in px, Locale.getDefault(), measure)`. Here `measure = { t, s -> textMeasurer.measure(t, SpineTypography.postmarkRing(s)).size.width.toFloat() }`, inside `remember(name, rt, locale)`.
    - Lay out each character with `textMeasurer.measure(char, style)`. Start at angle −90° minus half the first repetition's angular width, so the first repetition is centred upright at the top.
    - For each character, take `rotate(degrees = θ + 90, pivot = center)` then `translate` to `(cx − w/2, cy − rt − h/2)`, then `drawText(layout, color = ink)`. Advance θ by `(w + trackingPx) / rt` radians.
  - **The glyph:** `GlyphImage(strokes, viewBox, ink, Modifier.align(Center).size(POSTMARK_GLYPH))` when `strokes != null && viewBox != null`.
  - **Canvas 2 (noise):**
    - Use `remember(seed) { ImageBitmap }` built from `PostmarkNoise.alphas(NOISE_SIZE_PX, seed)` as `ARGB_8888` white pixels with that alpha.
    - Clip to the outer circle with `clipPath`, then `drawImage(noise, dstSize = size, colorFilter = ColorFilter.tint(paper, BlendMode.SrcIn))`. Plain SrcOver.

- [ ] **Step 2: Write the pixel test (Robolectric NATIVE).**

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DomainPostmarkPixelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the noise leaves paper inside the outer ring`() {
        compose.setContent {
            PebblesTestHarness {
                DomainPostmark(
                    name = "Friends", strokes = null, viewBox = null,
                    ink = Color.Black, paper = Color.White, seed = 1L,
                    diameter = 72.dp, modifier = Modifier.testTag("pm").background(Color.Black),
                )
            }
        }
        val pixels = compose.onNodeWithTag("pm").captureToImage().toPixelMap()
        val center = pixels.width / 2
        // The stamp sits on a black ground, so any non-black pixel inside the ring is paper noise.
        val lightInside = (0 until pixels.width).count { x -> pixels[x, center].red > 0.5f && kotlin.math.abs(x - center) < pixels.width / 3 }
        assertTrue("expected pits inside the ring, got $lightInside", lightInside > 0)
    }
}
```

  Use the harness and the imports that the existing Robolectric component tests in `test/…/` use. Grep `GraphicsMode.Mode.NATIVE` for one to copy the setup from. This is a guard, not proof of HWUI: the device check in Task 1.13 is the proof.

- [ ] **Step 3: Run it.** Expected: PASS.

- [ ] **Step 4: Commit** with the message `feat(android): domain postmark`.

### Task 1.9: `CollectionBand`

**Files:** Create `…/android/features/path/card/CollectionBand.kt`.

- [ ] **Step 1: Write the composable.**

```kotlin
/**
 * The pebble's collections as a paper band wrapped across the card's foot (pebble card
 * spec §Verso 5): full bleed to the shell's edges, the names in italic, a small
 * diamond between, each led by the collection mark. Nothing when [names] is empty.
 */
@Composable
internal fun CollectionBand(
    names: List<String>,
    paper: Color,
    ink: Color,
    edge: Color,
    modifier: Modifier = Modifier,
)
```

  Body:
  - If `names.isEmpty()`, return.
  - Otherwise lay out a `Box(modifier.fillMaxWidth().heightIn(min = BAND_MIN_HEIGHT).background(paper))` with:
    - two hairlines, top and bottom, in `edge` at 0.35 alpha;
    - a narrow darker fold at each end, 3 dp in `edge` at 0.5 alpha, which shows the band turning under.
  - Inside it, a `FlowRow(horizontalArrangement = spacedBy(10.dp, CenterHorizontally), verticalArrangement = spacedBy(4.dp))` padded 16 dp horizontally and 8 dp vertically.
  - Each item is `Row { Icon(painterResource(R.drawable.ic_pebble_collection), null, tint = ink, Modifier.size(14.dp)); Text(name, style = SpineTypography.cardBandName, color = ink) }`. Put a `Text("◆", color = edge)` between items.
  - The semantics are `Modifier.semantics(mergeDescendants = true) { contentDescription = "<card_collections_a11y>: <names joined by the locale's ListFormatter>" }`, and nothing is clickable.

  The band's full-bleed width comes from where `CardVerso` places it: inside the shell, outside the face's inset border. See Task 1.11.

- [ ] **Step 2: Commit** with the message `feat(android): collection band`.

### Task 1.10: `CardFooter`, `TurnButton` and `FaceMiniature`

**Files:** Create `…/android/features/path/card/CardFooter.kt` and `…/android/features/path/card/FaceMiniature.kt`.

- [ ] **Step 1: `FaceMiniature`.**

```kotlin
/** A thumbnail of the face the turn leads to (from the Label exploration): a card back with a tinted stone, or a recto with a photo block and lines. */
@Composable
internal fun FaceMiniature(
    to: CardFaceTarget,
    stone: Color,
    ink: Color,
    stock: Color,
    modifier: Modifier = Modifier,
)

internal enum class CardFaceTarget { PEBBLE, MOMENT }
```

  Draw it with a `Canvas` of `MINIATURE_WIDTH` × `MINIATURE_HEIGHT`, rotated `MINIATURE_TILT_DEG` with `graphicsLayer`.
  - Both targets: a rounded rectangle filled `stock` and stroked `ink` at 1 dp, with a 2 dp corner radius computed as px, not a `RoundedCornerShape` literal.
  - **PEBBLE:** a 0.8 dp inner rule inset 2 dp, and a filled oval in `stone`, 9 × 7 dp, centred slightly high.
  - **MOMENT:** a filled rectangle in `ink` at 0.35 alpha for the photo (the top 40 %), then three 0.8 dp lines.

- [ ] **Step 2: `CardFooter`.**

```kotlin
/**
 * The card's still footer (pebble card spec §The card): privacy left, the turn right.
 * The turn target is one 48 dp box covering label and miniature out to the card's
 * edge; the custom layout places it so it never overlaps the privacy sentence.
 */
@Composable
internal fun CardFooter(
    visibility: Visibility,
    turnTo: CardFaceTarget,
    stone: Color,
    onTurn: () -> Unit,
    modifier: Modifier = Modifier,
)
```

  - **Left column:**
    - A `Row`: the privacy glyph, then `Text(stringResource(visibility label), style = cardFooterGrade, color = ink)`.
    - Below it, `Text(stringResource(explanation), style = cardFooterNote, color = ink2)`.
    - Use the visibility glyph the old `PebblePrivacyBadge` uses. Copy its icon selection, a `when (visibility)` over the drawables, into a private function here, because Part 5 deletes the badge.
  - **Right column:** a `Row(verticalAlignment = CenterVertically)`.
    - The text column, end-aligned: `Text(stringResource(R.string.card_turn), cardFooterGrade, ink)`, then `Text(stringResource(if (turnTo == PEBBLE) card_turn_to_pebble else card_turn_to_moment), cardTurnTarget, ink2)`.
    - Then `FaceMiniature(turnTo, …)`, 8 dp after the text.
  - **Layout:** a `Layout` with two measurables.
    - Measure the right one first, at its intrinsic width.
    - Measure the left one with `maxWidth = constraints.maxWidth − right.width − FOOTER_GAP`.
    - Height is `max(left, right, TURN_TARGET)`. The right column sits end-aligned and vertically centred.
    - The right column carries `Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClickLabel = action label) { onTurn() }`, with `semantics { contentDescription = action label }`, where the action label is `card_turn_action_to_pebble` or `card_turn_action_to_moment`.
  - Padding is `FOOTER_PADDING_H` and `FOOTER_PADDING_V`.
  - **Visibility strings:** `visibility_secret`, `visibility_private` and `visibility_public` for the grade; `record_privacy_secret_explanation`, `record_privacy_private_explanation` and `record_privacy_public_explanation` for the sentence.

- [ ] **Step 3: Commit** with the message `feat(android): card footer with the turn button`.

### Task 1.11: `CardLeaf`, `CardRecto` and `CardVerso`

**Files:** Create `CardLeaf.kt`, `CardRecto.kt` and `CardVerso.kt` under `…/android/features/path/card/`.

- [ ] **Step 1: `CardLeaf`.** It takes `dateline: String`, `description: String?` and `u: Dp`.
  - A `Column(fillMaxWidth, padding(top = u * 3f))`.
  - A dashed score: a `Canvas` 1 dp tall, drawing a horizontal line in `cardRule` with `PathEffect.dashPathEffect(floatArrayOf(3dp px, 3dp px))`.
  - 14 dp below it, `Text(dateline, style = cardLeafDateline, color = ink2, textAlign = Center, Modifier.fillMaxWidth())`.
  - Then, when `description` is not blank, 14 dp below, `Text(description, style = cardLeafBody, color = ink)`.
  - The dateline comes from `DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)` + " · " + `ofLocalizedTime(FormatStyle.SHORT)`, with the locale, in the system zone.

- [ ] **Step 2: `CardRecto`.**

```kotlin
@Composable
internal fun CardRecto(
    detail: MomentDetail,
    palette: EmotionPalette?,
    u: Dp,
    ratio: Float?,
    photoUrl: String?,
    thumbUrl: String?,
    soulsById: Map<String, SoulWithGlyph>,
    modifier: Modifier = Modifier,
)
```

  - Build `moment = detail.toPathMoment()`, and compute `weekday`, `time` and `emotionName` exactly as `SpinePebble` does (lines 148–153).
  - Build the people line with `PeopleLines.of(souls.map { it.id }, souls.map { it.id }, locale, namedInFull = PeopleLines.NAMED_ON_CARD)`, where `souls = detail.pebble?.souls`. The order is the server's, since there is no week here.
  - **With a photo** (`detail.sortedSnaps.firstOrNull() != null`): render `SpineCardFace(moment, palette, u, ratio ?: 1f, photoUrl, thumbUrl, weekday, time, emotionName, people = { u -> PeopleLine(...) as SpinePebble's card branch passes it }, leaf = { u -> CardLeaf(dateline, detail.description, u) })`.
  - **Without a photo (F3):** add a parameter `slip: (@Composable (u: Dp) -> Unit)? = null` to `SpineCardFace`. When set, it replaces `Field`, and the legend's title is not repeated. The slip is a `Box(fillMaxWidth, aspectRatio(4f / 3f), background(tint))` built from the same `tint` as `Field`, with the name centred in `SpineTypography.entryName(...)` at the entry's type for this valence (`EntryTypes.entry(intensity, polarity)`), in `ink`.
  - The recto's semantics order is title (heading), date, emotion, souls, the photo's description, then the words. `SpineCardFace` already merges its index and rail with `clearAndSetSemantics`, so give the title `Modifier.semantics { heading() }`.

- [ ] **Step 3: `CardVerso`.**

```kotlin
@Composable
internal fun CardVerso(
    pebble: MomentPebble,
    palette: EmotionPalette?,
    u: Dp,
    domains: List<Domain>,
    modifier: Modifier = Modifier,
)
```

  - **The frame:** the outer `Box(fillMaxWidth)` is filled with the field's emotion mat. Reuse the `Field` pattern drawing: extract `drawFieldPattern` from `SpineCard.kt` as `internal` if it is `private`, so the verso can call it. Within it sits an inner panel at inset `u * 3.2f`, filled `stock` and bordered `cardRule`, whose corner radius is `u * 1.3f` as px. This is Held's frame.
  - **The inner `Column(CenterHorizontally)`:**
    1. **The lockup.** `val l = ValenceLockups.of(pebble.valence)`, then `ValenceWord(valence, scale = l.scale, brush = when (l.ink) { STONE_RAMP -> Brush.verticalGradient(StoneTones.bodyRamp(polarity, stonePalette).map { it.color }); INK -> SolidColor(colors.ink); PRIMARY -> SolidColor(MaterialTheme.colorScheme.primary) })`. The stone ramp is the night ramp when `colors.isNight`: use the palette's night variant if `StoneTones` exposes one, else the same ramp. Then `Text(stringResource(l.spanRes), cardLockupSpan, ink)`. Give it `semantics { heading() }`, with the content description "`card_verso_heading`. <word> <span>".
    2. **The emotion.** A hairline 24 dp wide, then `Text(emotionName, cardEmotion, ink)`.
    3. **The stone.** `LitStone(pebble.valence, palette, pebble.glyph, seed = UuidHash.fnv1a(pebble.id), press = rememberStonePressState(), width = VERSO_STONE_WIDTH)` with `weight(1f)`-style flexible space above and below. The verso fills the face's height, so it centres. Its semantics come from `SpineStoneDescription`.
    4. **The postmarks.** A `Row(horizontalArrangement = spacedBy(POSTMARK_GAP, CenterHorizontally))`, where the diameter is `min(POSTMARK_DIAMETER, (innerWidth − gaps) / n)`. One `DomainPostmark` per `pebble.domains`, with strokes and viewBox from `domains.firstOrNull { it.id == ref.id }`, ink `StoneTones.spot(stonePalette, !night)` (dark tone on paper, light at night), paper `colors.stock`, and seed `UuidHash.fnv1a(ref.id)`. Each name resolves through `ReferenceStrings.referenceName(ReferenceType.DOMAIN, ref.slug, ref.name)`. The row has `semantics(mergeDescendants = true) { contentDescription = "<card_domains_a11y>: <names>" }`. It sits `POSTMARK_ROW_TO_BAND` above the band.
  - **The band is not part of `CardVerso`.** It must run full-bleed to the shell's edges, past the face's inset border, so `PebbleCard` builds and places it (Task 1.12). `CardVerso` leaves `BAND_MIN_HEIGHT` of empty frame at its foot for it to overlap.

- [ ] **Step 4: Commit** with the message `feat(android): card recto, verso and leaf`.

### Task 1.12: `PebbleCard`

**Files:** Create `…/android/features/path/card/PebbleCard.kt`.

- [ ] **Step 1: Write the composable.**

```kotlin
/**
 * The held card (pebble card spec): a still shell, a face that shows the recto or the
 * verso, the collection band wrapped across the face's foot on the verso, and the still
 * footer. Part 1 swaps faces at once; Part 2 animates the turn.
 */
@Composable
fun PebbleCard(
    detail: MomentDetail,
    palette: EmotionPalette?,
    ratio: Float?,
    photoUrl: String?,
    thumbUrl: String?,
    soulsById: Map<String, SoulWithGlyph>,
    domains: List<Domain>,
    modifier: Modifier = Modifier,
    initialFace: CardFaceTarget = CardFaceTarget.MOMENT,
)
```

  Body:
  - `val pebble = requireNotNull(detail.pebble)`. Only a pebble has a card.
  - `var face by rememberSaveable { mutableStateOf(initialFace) }`.
  - `BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = TopCenter)`:
    - `width = min(maxWidth − 2 × PAGE_MARGIN, MAX_WIDTH)` and `u = width / U_DIVISOR`.
    - `SpineCardShell(palette, polarity, intensity, Modifier.width(width))`, holding `Column { faceArea; CardFooter(...) }`.
  - **Face area:** a `SubcomposeLayout` that measures both faces at the same width, then places only the visible one at height `max(rectoHeight, versoHeight)`, so both faces share one height. The verso fills that height with `Modifier.fillMaxHeight()` on its column, so the stone centres.
  - **On the verso,** `CollectionBand(pebble.collections.map { it.name }, paper = StoneTones.mixOklab(spot, 0.18f, colors.stock), ink = colors.ink, edge = StoneTones.edge(stonePalette, polarity, night))` is placed at the bottom of the face area, full width of the shell, overlapping the frame's foot.
  - **Footer:** `CardFooter(pebble.visibility, turnTo = if (face == MOMENT) PEBBLE else MOMENT, stone = spot, onTurn = { face = other })`.

  `CardFaceTarget.MOMENT` means the recto is showing. Its name is the target of a turn *to* it; keep one enum for both uses in Part 1. Part 2 introduces `CardFace` in `core/model` and maps it.

- [ ] **Step 2: Commit** with the message `feat(android): the held pebble card`.

### Task 1.13: Swap the card into the detail page; the phone leaves the sheet

**Files:**
- Modify: `…/android/features/path/PebbleDetailScreen.kt`, `…/android/features/path/PebbleDetailViewModel.kt`, `…/android/navigation/PebblesSceneStrategies.kt`, `…/android/navigation/PebblesKeys.kt` (KDoc of `PebbleDetail`), `…/android/navigation/PebblesEntryProvider.kt`
- Modify: `screenshotTest/…/PebbleDetailScreenshots.kt`, `screenshotTest/…/ListDetailScreenshots.kt`
- Create: `screenshotTest/…/PebbleCardFixtures.kt`, `screenshotTest/…/PebbleCardScreenshots.kt`

- [ ] **Step 1: The ViewModel exposes the aspect cache.**
  - Inject `val snapAspect: SnapAspectCache` as a constructor `val`, as `PathViewModel` does at line 138.
  - Update `PebbleDetailViewModelTest`'s construction to pass `SnapAspectCache` the way `PathViewModelTest` builds it. Grep `SnapAspectCache(` in `test/` to copy the construction.

- [ ] **Step 2: The screen resolves the photo, ratio and reference data.**
  - In `PebbleDetailScreen`, for `detail`, compute `prefix = detail?.sortedSnaps?.firstOrNull()?.storagePath`.
  - Sign the photo with `LocalSnapURLCache.current` and get the ratio with `viewModel.snapAspect.cached(prefix) ?: viewModel.snapAspect.ratio(prefix)`. Copy `SpinePebble.kt` lines 182–199, including `runCatchingCancellable` and the `Log.e`.
  - Read `soulsById = LocalReferenceDataService.current.souls.associateBy { it.id }` and `domains = LocalReferenceDataService.current.domains`.
  - Pass all of these into `PebbleDetailContent` as new parameters: `photo: Pair<String, String>?`, `ratio: Float?`, `soulsById`, `domains`, `showBack: Boolean` and `onBack: () -> Unit`.

- [ ] **Step 3: `PebbleDetailContent` renders the card for a pebble.**
  - In the `Content` branch: `if (state.detail.pebble != null) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(navigationBars bottom).padding(vertical = 8.dp)) { PebbleCard(...) } else PebbleReadView(...)`. Part 3 replaces the bare branch.
  - Remove `.readableWidth()` from the page column, because the card caps itself at 440 dp.

- [ ] **Step 4: Change the top bar.**
  - The `title` slot is empty. Delete the `PebblePrivacyBadge` call; the file stays until Part 5.
  - Add `navigationIcon = { if (showBack) IconButton(onBack) { Icon(R.drawable.ic_arrow_back or the drawable other detail screens use, contentDescription = stringResource(R.string.back_a11y or the existing back string)) } }`. Grep `SoulDetailScreen.kt` for its back button and copy its icon and string.
  - Add `HorizontalDivider()` before the Delete item.
  - Update the KDoc: there is now a back arrow when not in a pane, and the privacy has moved to the card.

- [ ] **Step 5: The phone leaves the sheet.**
  - In `PebblesSceneStrategies.kt`, find `PebblesKey.PebbleDetail::class -> detail(…, sheetWhenCompact = true)` and drop `sheetWhenCompact`.
  - In `PebblesKeys.kt`, rewrite the `PebbleDetail` KDoc: "A page on phones (#1135, the held card needs the height and the container transform), a pane beside Path on large screens (#940); never a modal, so the bar stays."
  - In `PebblesEntryProvider.kt`, pass `showBack = !isInListDetailPane()` and `onBack = navigator::goBack` to `PebbleDetailScreen`, as the soul detail entry does at line 119.
  - If `BottomSheetSceneStrategy` now has no user, leave it. It is generic, and its deletion is a separate decision; mention it in the PR body.

- [ ] **Step 6: Fixtures and previews.**
  - `PebbleCardFixtures.kt` holds F1–F3 and a stress pebble as `MomentDetail`s:
    - **F1:** Rooftop birthday, Thu 8 Oct 2026 21:00, joy, HIGHLIGHT_LARGE, Friends and Community, collection Birthdays, five souls (Mara, Elio, Nadia, Jules, Bruno), Connections, the long description.
    - **F2:** Mara on the seawall, Sat 10 Oct 17:45, joy, HIGHLIGHT_MEDIUM, Partner, Mara, Public.
    - **F3:** Couldn't sleep, Wed 7 Oct 03:10, fear, LOWLIGHT_SMALL, Health, Secret, no photo.
    - **Stress:** four domains (spirituality, events, community, selfcare) and collections "Les étés chez Mamie" and "Ce qu'Elio a dit".
  - The fixtures also carry a palette map for joy, fear and gratitude, copied from `SpineFixtureWeek`, and `Domain`s with strokes. Copy the strokes for friends, community, partner, health, spirituality, events and selfcare from `packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql`, or reuse the `glyphs.json` the round-3 mock extracted (`scratchpad/detail/back-token3/glyphs.json`).
  - Photos use `photoUrl = null` with a fixed `ratio`. The field shows its tint, as in the Spine previews.
  - `PebbleCardScreenshots.kt` has `@PreviewTest` light and Night for each of these, each `PebbleCard` alone at 412 dp wide:
    - F1 recto, F1 verso, F2 verso, F3 recto, F3 verso and stress verso;
    - F1 verso again under `@Preview(locale = "fr")`.
  - In `PebbleDetailScreenshots.kt` and `ListDetailScreenshots.kt`, update the call sites to the new parameters: `showBack = true`, `onBack = {}`, and the souls, domains, ratio and photo from the fixtures.

- [ ] **Step 7: Gates.** Run `./gradlew ktlintFormat ktlintCheck lint testDebugUnitTest assembleDebug`, and fix what fails. `NavigationTest` "Path opens a moment…" taps ⋮ and Edit moment, which still exist. If a Robolectric test looked for the old privacy badge or the souls grid, update it to the footer's grade text.

- [ ] **Step 8: Device check on the Pixel 8.** Run `adb devices`, then `./gradlew installDebug`.
  - Open a pebble with a photo, one without, and a public one.
  - Check the recto and the verso, which the footer's turn button swaps.
  - Check the postmark ink at 1×. Is the ring legible, and do the pits show with no red tint?
  - Check paper and Night, and wallpaper colours off and on.
  - Check TalkBack over the footer and the verso.
  - Take screenshots with `adb exec-out screencap -p > /tmp/…png` and look at them. Do not complete, edit or delete anything.

- [ ] **Step 9: Commit and PR.**
  - Commit with the message `feat(android): the detail page shows the held card`.
  - Push, then **ask the maintainer**.
  - Open the PR: title `feat(android): the pebble detail becomes the card`, body "Part 1 of #1135" plus key files and notes, including the noise-over-ink deviation and the sheet leaving phones.
  - Labels: `feat`, `android`, `ui`, `no-lab-note`. Milestone: M64.
  - Add the `rebaseline-screenshots` label, and approve the held runs.

---

# Part 2: The turn and the face you pressed

Branch: `feat/1135-pebble-card-turn`, stacked on Part 1 with `gh stack`. PR title: `feat(android): turn the card, open on the face you pressed`. Body: "Part 2 of #1135".

### Task 2.1: `CardFace` in the key; closing a detail by moment id

**Files:**
- Create: `…/android/core/model/CardFace.kt`
- Modify: `…/android/navigation/PebblesKeys.kt`, `…/android/navigation/Navigator.kt`, `…/android/navigation/PebblesEntryProvider.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/navigation/NavigatorCloseMomentDetailTest.kt`, `apps/android/app/src/test/kotlin/app/pbbls/android/navigation/PebblesKeysTest.kt`

- [ ] **Step 1: Write the failing test.** It uses the same construction as `NavigatorRootAtTest`, with the same imports (`NavBackStack`, `NavKey`, `mutableStateOf`).

```kotlin
package app.pbbls.android.navigation

class NavigatorCloseMomentDetailTest {
    private fun navigatorOn(vararg path: PebblesKey): Pair<Navigator, NavigationState> {
        val backStacks =
            PebblesKey.tabs.associateWith { tab ->
                NavBackStack<NavKey>(*(if (tab == PebblesKey.Path) path.toList() else listOf(tab)).toTypedArray())
            }
        val state = NavigationState(startRoute = PebblesKey.Path, topLevelRoute = mutableStateOf(PebblesKey.Path), backStacks = backStacks)
        return Navigator(state) to state
    }

    @Test
    fun `closes the open detail of that moment whatever its face`() {
        val (navigator, state) = navigatorOn(PebblesKey.Path, PebblesKey.PebbleDetail("m1", CardFace.VERSO))
        navigator.closeMomentDetail("m1")
        assertEquals(listOf<NavKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }

    @Test
    fun `leaves another moment's detail open`() {
        val (navigator, state) = navigatorOn(PebblesKey.Path, PebblesKey.PebbleDetail("m2"))
        navigator.closeMomentDetail("m1")
        assertEquals(PebblesKey.PebbleDetail("m2"), state.backStacks[PebblesKey.Path]!!.last())
    }
}
```

  Add the restore case to the existing `PebblesKeysTest.kt`, following its serializer setup:

```kotlin
    @Test
    fun `a detail key saved before faces decodes as the recto`() {
        assertEquals(
            PebblesKey.PebbleDetail("m1", CardFace.RECTO),
            Json.decodeFromString<PebblesKey.PebbleDetail>("""{"pebbleId":"m1"}"""),
        )
    }
```

- [ ] **Step 2: Run it.** Expected: compilation failure.

- [ ] **Step 3: Implement.**

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.Serializable

/** Which face of the held card is up (pebble card spec §Behaviour). */
@Serializable
enum class CardFace { RECTO, VERSO }
```

  - `PebbleDetail` gains `val face: CardFace = CardFace.RECTO`, with a default so restored stacks decode.
  - `Navigator` gains this function, with the KDoc: closes the open detail of [momentId] whatever face it opened on; `closeDetail(key)` compares the whole key, face included.

    ```kotlin
    fun closeMomentDetail(momentId: String) {
        val stack = state.currentStack
        val last = stack.last()
        if (stack.size > 1 && last is PebblesKey.PebbleDetail && last.momentId == momentId) stack.removeAt(stack.lastIndex)
    }
    ```

  - `PebblesEntryProvider`: line 68 `onDeleteConfirmed` and line 243 `onMomentDeleted` call `navigator.closeMomentDetail(...)`. The entry passes `initialFace = key.face` to `PebbleDetailScreen`.

- [ ] **Step 4: Run the test, then `NavigationTest`.** Expected: PASS.

- [ ] **Step 5: Commit** with the message `feat(android): the detail key carries the card face`.

### Task 2.2: The Spine opens on the face you pressed

**Files:** Modify `…/android/features/path/spine/SpinePebble.kt`, `…/android/features/path/spine/SpineWeek.kt`, `…/android/features/path/PathScreen.kt`, `…/android/navigation/PebblesEntryProvider.kt`.

- [ ] **Step 1: Thread the face through.**
  - Change `onOpen: (PathMoment) -> Unit` to `onOpen: (PathMoment, CardFace) -> Unit` in `SpinePebble`, `SpineWeek` and `PathContent` (`onPebbleTap`).
  - In `SpinePebble`: `pressStone` calls `onOpen(moment, if (moment.pebble != null) CardFace.VERSO else CardFace.RECTO)`, because a bare moment has no verso. `openNow` calls `onOpen(moment, CardFace.RECTO)`.
  - In `PathScreen`, change `onOpenDetail: (String) -> Unit` to `(String, CardFace) -> Unit`. The entry provider's line 66 becomes `onOpenDetail = { id, face -> navigator.navigateToDetail(PebblesKey.PebbleDetail(id, face)) }`.
  - Update every preview and test call site. The compiler lists them, and `{ _, _ -> }` fits most.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest`. The Robolectric journeys tap the row's centre, which is the card or entry, so they stay on the recto. Commit with the message `feat(android): the spine opens the card on the face you pressed`.

### Task 2.3: `TurnSettle`

**Files:**
- Create: `…/android/features/path/card/logic/TurnSettle.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/TurnSettleTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
class TurnSettleTest {
    @Test
    fun `a release past the half completes the turn`() {
        assertEquals(1f, TurnSettle.target(progress = 0.6f, velocityPerSecond = 0f))
    }

    @Test
    fun `a release short of the half reverts`() {
        assertEquals(0f, TurnSettle.target(progress = 0.4f, velocityPerSecond = 0f))
    }

    @Test
    fun `a flick carries the turn whatever the progress`() {
        assertEquals(1f, TurnSettle.target(progress = 0.1f, velocityPerSecond = 2.5f))
        assertEquals(0f, TurnSettle.target(progress = 0.9f, velocityPerSecond = -2.5f))
    }

    @Test
    fun `progress maps drag distance against the card width, clamped`() {
        assertEquals(0.5f, TurnSettle.progress(dragPx = -150f, widthPx = 300f), 0.001f)
        assertEquals(1f, TurnSettle.progress(dragPx = -900f, widthPx = 300f), 0.001f)
        assertEquals(0f, TurnSettle.progress(dragPx = 120f, widthPx = 300f), 0.001f)
    }
}
```

- [ ] **Step 2: Run it.** Expected: compilation failure.

- [ ] **Step 3: Implement.**

```kotlin
package app.pbbls.android.features.path.card.logic

import kotlin.math.abs

/**
 * The card's drag-to-turn (pebble card spec §Behaviour). Progress 0 is the face that was
 * up when the drag began, 1 the other. Dragging toward the start edge (negative x in LTR;
 * callers flip for RTL) turns it.
 */
object TurnSettle {
    /** Turns of the card per second past which a release carries in its direction. */
    const val FLICK = 1.5f

    fun progress(
        dragPx: Float,
        widthPx: Float,
    ): Float = (-dragPx / widthPx).coerceIn(0f, 1f)

    fun target(
        progress: Float,
        velocityPerSecond: Float,
    ): Float =
        when {
            abs(velocityPerSecond) >= FLICK -> if (velocityPerSecond > 0f) 1f else 0f
            else -> if (progress >= 0.5f) 1f else 0f
        }
}
```

- [ ] **Step 4: Run the test.** Expected: PASS. Commit with the message `feat(android): turn settle logic`.

### Task 2.4: The animated turn

**Files:** Modify `…/android/features/path/card/PebbleCard.kt` and `…/android/features/path/PebbleDetailScreen.kt`.

- [ ] **Step 1: Replace `CardFaceTarget` state with `CardFace`.**
  - `PebbleCard(initialFace: CardFace = CardFace.RECTO)`, and `var face by rememberSaveable { mutableStateOf(initialFace) }`.
  - Map to `CardFaceTarget` for the footer and miniature.
  - `PebbleDetailScreen` and `PebbleDetailContent` take `initialFace: CardFace` and pass it down.

- [ ] **Step 2: Animate.**
  - `val turn = remember { Animatable(0f) }` is progress from the current face.
  - The face area gets `Modifier.graphicsLayer { rotationY = turn.value * 180f * (if RTL −1 else 1); cameraDistance = 12f * density }`.
  - While `turn.value > 0.5f`, it renders the other face with `graphicsLayer { rotationY += 180f }`, so its text is not mirrored.
  - When an animation reaches 1, set `face = other`, then `turn.snapTo(0f)` in the same frame.
  - The footer and the band are **outside** the rotating layer. The band belongs to the verso: give it the same `graphicsLayer`, and render it only when the verso is visible.

- [ ] **Step 3: Wire the triggers.**
  - **Button:** `scope.launch { turn.animateTo(1f, tween(TURN_MS = 420, easing = FastOutSlowInEasing)) }`.
  - **Reduced motion** (`rememberReduceMotion()`): no rotation. Crossfade the faces over 150 ms with `Crossfade(face, animationSpec = tween(150))`.
  - **Drag:** `Modifier.pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = …) { change, dx -> … } }` on the face area. Accumulate `dragPx`, then `turn.snapTo(TurnSettle.progress(dragPx, widthPx))`. On end, read the velocity from a `VelocityTracker` in turns per second (`vx / widthPx`), then `turn.animateTo(TurnSettle.target(turn.value, v))`. A horizontal detector does not steal the page's vertical scroll.
  - **TalkBack:** the footer's turn button, labelled `card_turn_action_to_pebble` or `card_turn_action_to_moment`, is the turn. A custom action on the card's root was built and removed in review: Compose never makes that node screen-reader focusable, so TalkBack would never offer it.

- [ ] **Step 4: Preview.** Add `@PreviewTest` `PebbleCardMidTurn` at a frozen progress of 0.3, by exposing `turnProgressOverride: Float? = null` for previews only, as `SpinePebble` exposes `ratioOverride`.

- [ ] **Step 5: Gates and device.**
  - Run the gates.
  - On the Pixel: drag a slow half and release; drag a short way and release; flick; tap the button; with TalkBack, reach the footer's turn button and double-tap it.
  - With animator duration scale set to 0 in developer options, check the crossfade.
  - Open from a stone: the card opens verso up. Open from a card: recto up.
  - Commit with the message `feat(android): the card turns`. Push, ask, then open the PR.

---

# Part 3: The bare moment and the completion

Branch: `feat/1135-pebble-card-bare-moment`, stacked. PR title: `feat(android): a bare moment opens plainly and completes into its card`. Body: "Part 3 of #1135".

### Task 3.1: The ViewModel notices a completion

**Files:**
- Modify: `…/android/features/path/PebbleDetailViewModel.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/PebbleDetailViewModelTest.kt`

- [ ] **Step 1: Write the failing tests.** Follow the file's existing pattern: its fake read service, `MainDispatcherRule` and `runTest`.

```kotlin
    @Test
    fun `a resume that finds a pebble on a bare moment emits Completed`() = runTest {
        moments.moments = mapOf("m1" to bareDetail("m1"))
        val vm = viewModel()
        val effects = mutableListOf<PebbleDetailEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.toList(effects) }
        vm.start("m1"); advanceUntilIdle()
        vm.onResumed(); advanceUntilIdle()           // the first resume is skipped
        moments.moments = mapOf("m1" to completedDetail("m1"))
        vm.onResumed(); advanceUntilIdle()
        assertEquals(listOf(PebbleDetailEffect.Completed), effects)
    }

    @Test
    fun `a pebble that was already there emits nothing`() = runTest {
        moments.moments = mapOf("m1" to completedDetail("m1"))
        val vm = viewModel()
        val effects = mutableListOf<PebbleDetailEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.toList(effects) }
        vm.start("m1"); advanceUntilIdle()
        vm.onResumed(); advanceUntilIdle()
        vm.onResumed(); advanceUntilIdle()
        assertEquals(emptyList<PebbleDetailEffect>(), effects)
    }
```

  Use the test file's own names for the fake's field, its fixture builders and its `viewModel()` factory. Grep the file first, and add the `bareDetail` and `completedDetail` builders if they are absent.

- [ ] **Step 2: Run them.** Expected: they fail, because `Completed` is unresolved.

- [ ] **Step 3: Implement.**
  - Add `data object Completed : PebbleDetailEffect`, with the KDoc "The moment came back from Complete with a pebble: the card prints into its place."
  - In `fetch`'s `onSuccess`, before `detail = it`:

    ```kotlin
    val wasBare = detail != null && detail?.pebble == null
    ```

  - After `publish()`:

    ```kotlin
    if (wasBare && it.pebble != null) _effects.emit(PebbleDetailEffect.Completed)
    ```

  - Keep `publish()` first, so the card exists when the effect lands.

- [ ] **Step 4: Run the tests.** Expected: PASS. Commit with the message `feat(android): the detail notices a completion`.

### Task 3.2: `BareMomentPage`

**Files:**
- Create: `…/android/features/path/card/BareMomentPage.kt`
- Modify: `PebbleDetailScreen.kt`

- [ ] **Step 1: Write the composable.**

```kotlin
/**
 * A moment with no pebble has no card (pebble card spec §Bare moment): the Gathering's
 * plain opening, then the place its card will print into, with the page's one call to
 * action. [placeModifier] lets the completion find the place's bounds.
 */
@Composable
internal fun BareMomentPage(
    detail: MomentDetail,
    photoUrl: String?,
    ratio: Float?,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    placeModifier: Modifier = Modifier,
)
```

  A `Column(CenterHorizontally, padding(horizontal = PAGE_MARGIN))` holding:
  - the kicker: the full weekday, day and month uppercased, " · ", and the time, in `SpineTypography.kicker`, `ink2`;
  - 10 dp below, the title in `bareTitle`, `ink`, centred and balanced (`LineBreak.Heading`), marked as a heading;
  - when there is a photo, 20 dp below, the photo on a white mat: `Box(background(stock), padding(10.dp))` around an `AsyncImage` at its ratio, `ContentScale.Fit`;
  - when there is a description, 16 dp below, the description in `cardLeafBody`, centred;
  - 28 dp below, **the place**, with `placeModifier` applied.

  **The place** is a `Box(fillMaxWidth, aspectRatio(1.12f))` with:
  - a dashed rounded border at 1 dp in `ink3`, using `PathEffect.dashPathEffect`;
  - four album corners: small filled triangles in `rule` at each corner, 16 dp;
  - centred inside it, a `Column`:
    - a dashed stone outline: `StoneOutlines` for MEDIUM neutral, stroked dashed in `ink3`, 64 dp;
    - `Text(stringResource(R.string.bare_place_kicker).uppercase(), sectionLabel, ink2)`;
    - `Text(stringResource(R.string.bare_place_body), placeBody, ink, textAlign = Center)`, padded 24 dp horizontally;
    - `Button(onClick = onComplete) { Icon(add-circle drawable); Text(stringResource(R.string.complete_cta)) }`, using the stock filled `Button`. Grep the existing `complete_cta` call site in `PebbleReadView` for the icon it uses.

- [ ] **Step 2: Swap it in.** In `PebbleDetailContent`, the bare branch renders `BareMomentPage(...)` instead of `PebbleReadView`. Remove the `tools:ignore` from the two `bare_place_*` strings.

- [ ] **Step 3: Preview.** Add `@PreviewTest` `BareMomentLight`, `BareMomentNight` and `BareMomentFrench`, using F4 Sunday market (Sun 11 Oct 09:40, no photo, "Elio wanted the yellow tomatoes.") and a photo variant. Commit with the message `feat(android): the bare moment page`.

### Task 3.3: `CardCompletion`, the card prints into its place

**Files:**
- Create: `…/android/features/path/card/CardCompletion.kt`
- Modify: `PebbleDetailScreen.kt`

- [ ] **Step 1: Write the choreography.**

```kotlin
/**
 * The completion (pebble card spec §Completion): over the bare page, the card appears
 * verso up at the place's bounds, the dashed stone flies to the clearing and the lit
 * stone presses in, the lockup, postmarks, band and footer print in turn, the page
 * steps aside and the card rises to its rest. Reduced motion: a crossfade.
 */
@Composable
internal fun CardCompletion(
    placeBounds: Rect,
    content: @Composable (progress: CompletionProgress) -> Unit,
    onFinished: () -> Unit,
)

internal data class CompletionProgress(val print: Float, val stone: Float, val rise: Float)
```

  - **One timeline**, a single `Animatable(0f)` to 1 over 1600 ms with `LinearEasing`, mapped through `piecewise` tables like `StoneMotion`'s:
    - `print`: 0→1 over 0–0.35;
    - `stone`: 0→1 over 0.25–0.6, which triggers `press.press()` on the verso's `StonePressState` at 0.45;
    - `rise`: 0→1 over 0.6–1.
  - **Phases:**
    - During `print`, the card is clipped to `placeBounds` and fades in.
    - The postmarks, band and footer get alphas staggered across `print`.
    - During `rise`, the card's bounds interpolate from `placeBounds` to its resting bounds, and the bare page's alpha goes to 0.
  - `PebbleCard` gains an `initialFace = VERSO` call site and a `completion: CompletionProgress? = null` parameter, which it uses to stagger alphas and drive its stone's press.
  - **Wiring.** `PebbleDetailScreen` observes `PebbleDetailEffect.Completed`. It holds `var completing by remember { mutableStateOf(false) }`, and records `placeBounds` from `BareMomentPage`'s `placeModifier.onGloballyPositioned`. While `completing`, it renders `CardCompletion` over the page; `onFinished` sets `completing = false`. The content is now a pebble, so the plain card shows, verso up.

- [ ] **Step 2: Preview.** Add `@PreviewTest` frames at `progress` 0.2, 0.45 and 0.8, using a `progressOverride` parameter, as in Task 2.4.

- [ ] **Step 3: Device.** Completing a pebble writes to the real account, so **ask the maintainer first**. With their go, complete one bare moment on the Pixel, then watch the print, the press and the rise. Also check under animator scale 0.

- [ ] **Step 4: Gates and commit.** Commit with the message `feat(android): the card prints into a completed moment`. Push, ask, then open the PR.

---

# Part 4: Open from the Spine, back to it

Branch: `feat/1135-pebble-card-open-close`, stacked. PR title: `feat(android): the card opens from its slot on the spine`. Body: "Part 4 of #1135".

This part has a go/no-go spike first. If the shared bounds across `NavDisplay` cannot be made reliable on the Pixel within the spike, ship the fallback in Step 4 and record why in the decision log.

### Task 4.1: Spike, shared bounds across `NavDisplay`

- [ ] **Step 1: Read the API.** Read `android docs search "navigation3 shared element"` and the `navigation-3` skill. Confirm that `NavDisplay` 1.2.0 takes a `sharedTransitionScope` and exposes `LocalNavAnimatedContentScope` to entries.

- [ ] **Step 2: Wrap and key the bounds.**
  - In the file that hosts `NavDisplay` (grep `NavDisplay(` under `…/android/`), wrap it in `SharedTransitionLayout { NavDisplay(…, sharedTransitionScope = this) }`.
  - Provide the scope to the Spine and the card through `LocalSharedTransitionScope` in `core/ui/SharedCard.kt`. A `CompositionLocal` here is UI plumbing, not a service, so the 2026-09-21 rule on service locals does not forbid it. Still record the reason in the decision-log entry in Part 5.
  - Key the bounds as `"card-${moment.id}"`. Apply `Modifier.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope = LocalNavAnimatedContentScope.current, resizeMode = RemeasureToBounds)` to the `SpineCardShell` in `SpinePebble`'s card branch and to the `PebbleCard` shell.
  - Entries and stones have no card on the Spine, so they keep the fade-through.

- [ ] **Step 3: Test on device** (Pixel 8). Open and close five times from cards at the top, middle and bottom of a week. Then do it with predictive back, with a slow partial swipe and cancel, and a full swipe.
  - **Go:** the card grows from its slot and returns to it, with no flash and no jump.
  - **No-go:** a flicker, a wrong slot, or a crash.

- [ ] **Step 4: The fallback, if no-go.** Revert Step 2. Keep `NavTransitions.split`'s fade-through, and add a 1.04 → 1 scale on the card's first frame as a light gesture of lift.

### Task 4.2: Predictive back and the wide pane

- [ ] **Step 1: Predictive back.** On a go, it is seekable through `NavDisplay`'s own predictive-back support. Verify that a partial swipe scrubs the card toward its slot. On a no-go, there is nothing to add.
- [ ] **Step 2: The wide pane.** At 1280 × 800, on the Pixel Tablet AVD or the emulator in a resizable window, the card is centred in the detail pane, capped at 440 dp. The cross-pane flight may stay a fade.
- [ ] **Step 3: Gates and commit.** Commit with the message `feat(android): the card opens from its slot`. Push, ask, then open the PR.

---

# Part 5: Cleanup and docs

Branch: `chore/1135-pebble-card-cleanup`, stacked. PR title: `chore(android): remove the old read view`. Body: "Resolves #1135". This last part closes the issue.

### Task 5.1: Delete the orphans

- [ ] **Step 1: Confirm each file is unreferenced.** For `PebbleReadView`, `PebbleReadBanner`, `PebbleReadTitle`, `PebblePrivacyBadge`, `BannerAspect` and `PebbleSnapFrame`, run `grep -rn '<Name>' apps/android/app/src`. Each must show only its own file. Delete the ones that do. `PebbleReadPetroglyph` and `PetroglyphColors` stay, because the record flow uses them.
- [ ] **Step 2: Delete their strings** after `grep`, keeping any still used. Remove the old read-view previews from `PebbleDetailScreenshots.kt`.
- [ ] **Step 3: Run the gates,** then commit with the message `chore(android): remove the old read view`. Regenerate the lint baseline only if a pure file move un-filtered an accepted finding; diff by issue id.

### Task 5.2: Docs, the decision log and Arkaik

- [ ] **Step 1: `apps/android/CLAUDE.md`.**
  - In "Behavior that mirrors iOS on purpose", replace "the read page's ⋮ menu" with "the held card's ⋮ menu".
  - Add a "Pebble card" bullet under Path rendering: `features/path/card/`; the shell and face split of `SpineCard`; postmarks via `RingFits` and the noise laid over the ink, never `DstIn`; and the phone leaving the sheet.
  - Note the iOS drift: iOS still has the old read page, tracked in its own issue.
- [ ] **Step 2: Decision log.** Append an entry to `docs/decisions/log.md`:
  - Status: taken. Scope: android, ui.
  - The held card replaces the read view.
  - The phone detail is a page, no longer a sheet.
  - Postmark ink is laid over the ink, not masked.
  - The outcome of the Part 4 spike: shared bounds, or the fade fallback, and why.
  - `LocalSharedTransitionScope`, if added.
  - Refs: #1135, the spec, the hub.
- [ ] **Step 3: Arkaik.** Use `update_node` over MCP to move the pebble-detail view node's android status to `development`, sending gherkin, values and platformStatuses together. Find the node with `list_nodes` on "detail". If the tools are unavailable, say so and stop that step. Never touch `docs/arkaik/bundle.json`.
- [ ] **Step 4: Follow-up issues.** Ask the maintainer first, then file one issue each:
  1. `ValenceMesh.inkHexes` starts at red #F32716 (the record-flow Highlight).
  2. The Travel domain glyph duplicates Identity's in the seed (db).
  3. Show `collections.glyph_id` on the band.
  4. FR "Modifier le caillou" → "galet".
  5. iOS mirror of the held card, after #1130.
- [ ] **Step 5: Commit, push, ask, then open the PR.** Commit with the message `docs(android): the held card in CLAUDE.md and the decision log`. After merge, run a device check of the whole stack on `main`.

---

## Self-review

- **Spec coverage:**

| Spec section | Tasks |
|---|---|
| Top bar | 1.13 |
| Shell, panel and footer | 1.6, 1.10, 1.12 |
| Recto with leaf and eight souls | 1.2, 1.11 |
| F3 slip | 1.11 |
| Verso: lockup, emotion and stone | 1.3, 1.7, 1.11 |
| Postmarks | 1.1, 1.4, 1.8 |
| Band | 1.9, 1.12 |
| Bare moment | 3.2 |
| Completion | 3.1, 3.3 |
| Face you pressed | 2.1, 2.2 |
| Turn (button, drag, TalkBack, reduced motion, saveable) | 1.12, 2.3, 2.4 |
| Open and close, predictive back | 4.1, 4.2 |
| Wide pane | 4.2 |
| Phone leaves the sheet | 1.13 |
| Data, no schema change | 1.13 (souls, domains, aspect, photo) |
| Strings | 1.5 |
| Testing (unit, previews, NATIVE pixel, device) | 1.1–1.4, 1.8, 1.13, 2.1, 2.3, 3.1, and device steps in every part |
| Follow-ups | 5.2 |
| Cleanup | 5.1 |

- **Placeholders:** none deferred. Where a step says "as `X` does" (`NavigatorRootAtTest`, `SpinePebble` lines 182–199, `SoulDetailScreen`'s back button), it names the exact file to copy from in this repository.
- **Consistency:**
  - `RingFits.fit(name, circumferencePx, locale, measure)` is used in 1.1 and 1.8.
  - `PeopleLines.NAMED_ON_CARD` is used in 1.2 and 1.11.
  - `ValenceLockups.of` and `LockupInk` are used in 1.3 and 1.11.
  - `PostmarkNoise.alphas(size, seed)` is used in 1.4 and 1.8.
  - `CardFaceTarget` covers Part 1 (1.10, 1.12) and maps from `CardFace` in 2.4.
  - `SpineCardShell` and `SpineCardFace` are defined in 1.6 and used in 1.11, 1.12 and 4.1.
  - `LitStone(width =)` is defined in 1.7 and used in 1.11.
  - `PebbleDetailEffect.Completed` is defined in 3.1 and used in 3.3.
  - `Navigator.closeMomentDetail` is defined in 2.1 and used in 2.1.

## Lessons learned (execution, Parts 1–5, 2026-10-20)

- **A completion is state, not an effect.** Emitting `Completed` after publishing the refreshed moment let the page swap to the card before the effect arrived, so the print had nothing to play into. The refresh now publishes the pebble already `completing`: the frame that brings the pebble is the frame that starts the print, and the screen reports it shown.
- **A `rememberSaveable` `MutableState` written from a cancelled coroutine reaches the saved copy.** Nav 3's saveable holder keeps the saved state object in memory, so a `finally` clearing the print-under-way marker after the screen was disposed wrote into what the next composition restored, and the print replayed from 0 after Edit pebble and back. Clear such state where the work ends inside the same composition, never in a `finally`.
- **`ScrollState` clamps, and the clamped value is what gets saved.** Pushing an entry with no bar snaps the bar away while the page is still laid out under the outgoing transition; the viewport grows, the value clamps to the shorter reach, and the page comes back a bar's height higher. Hold the reader's position apart from the value (`rememberHeldScrollState`): a clamp leaves it, any other change takes it, it is what is saved.
- **An unplaced face loses its display list.** A verso that is composed but not placed has to record and upload on the turn's first frame. Place both faces, each in an offscreen layer, the hidden one at alpha 0: the half swaps two alphas, and the turn rotates a texture instead of redrawing in perspective.
- **`OverlayClip` on `sharedBounds` blacked out the page on close** on a Pixel 8, from the first frame of the close until the Path faded in. The bounds are unclipped and scale (`ScaleToBounds`) rather than remeasure.
- **HWUI ignores `DstIn` inside an offscreen layer** (again: the Spine's ground hit it first). The postmark noise is paper-coloured speckles laid over the ink, not a mask.
- **Robolectric NATIVE tests pollute each other in one JVM** (#1141): screen tests drawn natively blanked a later pixel test. Keep semantics-only screen tests on legacy graphics, and treat a pixel test failing only beside others as pollution before a regression.
- **A custom accessibility action on an unfocusable container is unreachable.** TalkBack never focuses the card's shell, so its turn action never surfaced; the footer's visible button is the turn for TalkBack.
