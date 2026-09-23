# Android M3 Expressive Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Android app's iOS-derived palette, type and corner vocabulary with the M3-evo Material Theme Builder export, rooted in `MaterialExpressiveTheme`, with app code reading `MaterialTheme.*` roles only.

**Architecture:** Part 1 builds the new theme and keeps the old `PebblesTheme.colors` / `.type` API alive as a deprecated bridge fed from the active `ColorScheme`, so the whole app changes look in one step and still compiles. Parts 3–5 move call sites onto M3 roles area by area; Part 6 moves spacing to the 4 dp grid; Part 7 deletes the bridge and locks it with a Konsist test. Part 2 adds the wallpaper-colour switch.

**Tech Stack:** Kotlin, Jetpack Compose (BOM `2026.09.00`), material3 `1.5.0-alpha27` (`ExperimentalMaterial3ExpressiveApi`), Hilt, JUnit4, Konsist, Compose Preview Screenshot Testing.

**Spec:** `docs/superpowers/specs/2026-09-24-android-m3-expressive-theme-design.md`. Read it first; this plan does not restate its reasoning.

---

## Conventions for every task

- Paths below are relative to `apps/android/` unless they start with `docs/` or `packages/`. Kotlin sources live under `app/src/main/kotlin/app/pbbls/android/` — written `$SRC/` below. Unit tests under `app/src/test/kotlin/app/pbbls/android/` — written `$TEST/`.
- Every Gradle command needs `export ANDROID_HOME=$HOME/Library/Android/sdk` first, and runs from `apps/android/`.
- **The per-part gate** (run before every push):
  ```bash
  ./gradlew ktlintCheck lint testDebugUnitTest assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`. `./gradlew ktlintFormat` fixes formatting failures.
- **Screenshots:** run `./gradlew validateDebugScreenshotTest` locally only to see *which* previews moved. Never commit locally rendered references. After opening the PR, add the `rebaseline-screenshots` label; then approve the held runs (`apps/android/CLAUDE.md`, "Screenshot validation gate").
- Commits: conventional, lowercase, `(android)` scope, ending with the `Co-Authored-By` trailer.
- Stack: branches are created with `gh stack` (see the `gh-stack` skill). Part 1's branch `feat/853-m3-theme-foundation` already exists with the spec commits on it.

---

# Part 1 — Foundation and bridge (`feat/853-m3-theme-foundation`)

### Task 1.1: Pin material3 1.5.0-alpha27

**Files:**
- Modify: `gradle/libs.versions.toml` (`[versions]` block and line 97)

- [ ] **Step 1: Add the version and point the library at it**

In `[versions]`, after `composeBom = "2026.09.00"`:

```toml
# Over the BOM's 1.4.0 (#853): 1.4.0 keeps MotionScheme.expressive(), the
# *Emphasized type getters and the increased shape steps internal. alpha27 is
# the newest alpha whose Compose deps (1.12.0-beta01) sit under the BOM's
# stable 1.12.1 — alpha28+ drag ui/foundation/runtime to 1.13.0-alpha01.
# Drop this line when 1.5.0 stable is in the BOM.
material3 = "1.5.0-alpha27"
```

Replace line 97:

```toml
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" }
```

- [ ] **Step 2: Verify resolution**

Run: `./gradlew -q :app:dependencies --configuration debugRuntimeClasspath | grep -E "material3:material3 |compose.ui:ui:|foundation:foundation:" | head`
Expected: `material3 -> 1.5.0-alpha27`; `ui` and `foundation` still `1.12.1`, never `1.13.0-alpha01`.

- [ ] **Step 3: Gate and commit**

Run the per-part gate. Expected: green (no source changes; if a material3 API deprecation now fails lint, fix that call site in this commit).

```bash
git add gradle/libs.versions.toml
git commit -m "chore(android): pin material3 1.5.0-alpha27 for the expressive api"
```

### Task 1.2: Bundle Inclusive Sans and variable Ysabeau

**Files:**
- Create: `app/src/main/res/font/inclusive_sans.ttf`, `inclusive_sans_italic.ttf`, `ysabeau.ttf`, `ysabeau_italic.ttf`
- Create: `$SRC/core/designsystem/PebblesTypeface.kt`

- [ ] **Step 1: Download the four variable TTFs from google/fonts**

```bash
F=app/src/main/res/font
B=https://raw.githubusercontent.com/google/fonts/main/ofl
curl -fsSL -o $F/inclusive_sans.ttf        "$B/inclusivesans/InclusiveSans%5Bwght%5D.ttf"
curl -fsSL -o $F/inclusive_sans_italic.ttf "$B/inclusivesans/InclusiveSans-Italic%5Bwght%5D.ttf"
curl -fsSL -o $F/ysabeau.ttf               "$B/ysabeau/Ysabeau%5Bwght%5D.ttf"
curl -fsSL -o $F/ysabeau_italic.ttf        "$B/ysabeau/Ysabeau-Italic%5Bwght%5D.ttf"
file $F/*.ttf
```

Expected: each reported as `TrueType Font data`. If any is HTML/empty, the path moved upstream — look it up in `https://github.com/google/fonts/tree/main/ofl/<family>` rather than guessing.

- [ ] **Step 2: Write `PebblesTypeface.kt`**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.pbbls.android.R

/**
 * The export's two faces (#853), bundled rather than fetched through the GMS
 * downloadable-fonts provider the Theme Builder template uses: bundled works
 * offline and on devices without Play services, and layoutlib screenshot tests
 * cannot fetch. Both files are variable (wght axis), so each weight is one
 * `FontVariation` over the same file — the pattern Nunito used before.
 */
private val VariableWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(
    upright: Int,
    italic: Int,
): FontFamily =
    FontFamily(
        VariableWeights.flatMap { weight ->
            listOf(
                Font(
                    upright,
                    weight = weight,
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                ),
                Font(
                    italic,
                    weight = weight,
                    style = FontStyle.Italic,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                ),
            )
        },
    )

/** Body and label face. */
internal val InclusiveSansFamily = variableFamily(R.font.inclusive_sans, R.font.inclusive_sans_italic)

/** Display, headline and title face. */
internal val YsabeauFamily = variableFamily(R.font.ysabeau, R.font.ysabeau_italic)

/**
 * Number Spacing → proportional, Number Case → lining: digits align to cap
 * height in Ysabeau, whose default figures are old-style.
 */
internal const val YSABEAU_NUMBER_FEATURES = "pnum, lnum"
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (families unused so far; not a lint concern until `lint` runs, which Task 1.4 satisfies).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/font/inclusive_sans*.ttf app/src/main/res/font/ysabeau.ttf app/src/main/res/font/ysabeau_italic.ttf $SRC/core/designsystem/PebblesTypeface.kt
git commit -m "feat(android): bundle inclusive sans and variable ysabeau"
```

### Task 1.3: Color schemes and their contrast test

**Files:**
- Create: `$TEST/testing/Contrast.kt`
- Create: `$TEST/core/designsystem/ColorSchemeContrastTest.kt`
- Create: `$SRC/core/designsystem/ColorSchemes.kt` (generated)
- Create: `$SRC/core/designsystem/ContrastLevel.kt`
- Modify: `$TEST/core/designsystem/GoogleSignInButtonContrastTest.kt`

- [ ] **Step 1: Extract the WCAG math into a test helper**

`$TEST/testing/Contrast.kt`:

```kotlin
package app.pbbls.android.testing

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x relative luminance. Test-only: the app ships no contrast utility. */
fun luminance(color: Color): Double {
    fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG contrast ratio between [ink] and [ground], 1.0–21.0. Alpha is ignored. */
fun contrastRatio(
    ink: Color,
    ground: Color,
): Double {
    val a = luminance(ink)
    val b = luminance(ground)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** WCAG AA for body text. */
const val AA_TEXT = 4.5

/** WCAG AA for non-text UI (borders, icons that carry meaning). */
const val AA_NON_TEXT = 3.0
```

- [ ] **Step 2: Write the failing scheme test**

`$TEST/core/designsystem/ColorSchemeContrastTest.kt`:

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import app.pbbls.android.testing.AA_NON_TEXT
import app.pbbls.android.testing.AA_TEXT
import app.pbbls.android.testing.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every foreground/ground pair the app draws, across all six schemes (#853).
 * Dynamic (wallpaper) schemes are generated by the OS and are not ours to test.
 */
class ColorSchemeContrastTest {
    private val schemes: List<Pair<String, ColorScheme>> =
        ContrastLevel.entries.flatMap { level ->
            listOf(
                "light/$level" to pebblesColorScheme(dark = false, contrast = level),
                "dark/$level" to pebblesColorScheme(dark = true, contrast = level),
            )
        }

    private fun ColorScheme.onPairs(): List<Triple<String, Color, Color>> =
        listOf(
            Triple("onPrimary/primary", onPrimary, primary),
            Triple("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer),
            Triple("onSecondary/secondary", onSecondary, secondary),
            Triple("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
            Triple("onTertiary/tertiary", onTertiary, tertiary),
            Triple("onTertiaryContainer/tertiaryContainer", onTertiaryContainer, tertiaryContainer),
            Triple("onError/error", onError, error),
            Triple("onErrorContainer/errorContainer", onErrorContainer, errorContainer),
            Triple("inverseOnSurface/inverseSurface", inverseOnSurface, inverseSurface),
        )

    private fun ColorScheme.grounds(): List<Pair<String, Color>> =
        listOf(
            "surface" to surface,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
        )

    private fun ColorScheme.inks(): List<Pair<String, Color>> =
        listOf("onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant, "primary" to primary, "error" to error)

    @Test
    fun `every on-colour is AA on its container`() {
        val failures =
            schemes.flatMap { (name, scheme) ->
                scheme.onPairs().mapNotNull { (pair, ink, ground) ->
                    val ratio = contrastRatio(ink, ground)
                    "$name $pair = $ratio".takeIf { ratio < AA_TEXT }
                }
            }
        assertTrue("below AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `text inks are AA on every surface step`() {
        val failures =
            schemes.flatMap { (name, scheme) ->
                scheme.inks().flatMap { (inkName, ink) ->
                    scheme.grounds().mapNotNull { (groundName, ground) ->
                        val ratio = contrastRatio(ink, ground)
                        "$name $inkName/$groundName = $ratio".takeIf { ratio < AA_TEXT }
                    }
                }
            }
        assertTrue("below AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `outline is AA for non-text on surface`() {
        val failures =
            schemes.mapNotNull { (name, scheme) ->
                val ratio = contrastRatio(scheme.outline, scheme.surface)
                "$name outline/surface = $ratio".takeIf { ratio < AA_NON_TEXT }
            }
        assertTrue("below 3:1:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `contrast levels map from the system float`() {
        assertTrue(ContrastLevel.fromSystemContrast(0f) == ContrastLevel.STANDARD)
        assertTrue(ContrastLevel.fromSystemContrast(-1f) == ContrastLevel.STANDARD)
        assertTrue(ContrastLevel.fromSystemContrast(0.5f) == ContrastLevel.MEDIUM)
        assertTrue(ContrastLevel.fromSystemContrast(1f) == ContrastLevel.HIGH)
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorSchemeContrastTest*'`
Expected: compilation FAIL — `pebblesColorScheme`, `ContrastLevel` unresolved.

- [ ] **Step 4: Generate `ColorSchemes.kt` from the export**

The export JSON is the maintainer's file at `~/Downloads/pbbls-m3_evo/pbbls-m3_evo-theme.json`. Generate, don't hand-type: 270 hex values are where transcription typos live.

```bash
python3 - <<'EOF'
import json, os, re
src = os.path.expanduser('~/Downloads/pbbls-m3_evo/pbbls-m3_evo-theme.json')
d = json.load(open(src))
schemes = [
    ('LightScheme', 'light', 'lightColorScheme'),
    ('LightMediumContrastScheme', 'light-medium-contrast', 'lightColorScheme'),
    ('LightHighContrastScheme', 'light-high-contrast', 'lightColorScheme'),
    ('DarkScheme', 'dark', 'darkColorScheme'),
    ('DarkMediumContrastScheme', 'dark-medium-contrast', 'darkColorScheme'),
    ('DarkHighContrastScheme', 'dark-high-contrast', 'darkColorScheme'),
]
skip = {'shadow'}  # not a ColorScheme role
out = ['package app.pbbls.android.core.designsystem', '',
       'import androidx.compose.material3.ColorScheme',
       'import androidx.compose.material3.darkColorScheme',
       'import androidx.compose.material3.lightColorScheme',
       'import androidx.compose.ui.graphics.Color', '',
       '// GENERATED from the M3-evo Material Theme Builder export (seed #CE7E8A,',
       f"// {d['description'].splitlines()[-1]}) for #853. Regenerate with the script in",
       '// docs/superpowers/plans/2026-09-24-android-m3-expressive-theme.md (Task 1.3);',
       '// do not hand-edit a value. This file is the only place a Color(0x…) literal',
       '// may live outside tests — ThemeLiteralsTest enforces it from Part 7.', '']
for name, key, fn in schemes:
    out.append(f'internal val {name}: ColorScheme =')
    out.append(f'    {fn}(')
    for role, hexv in d['schemes'][key].items():
        if role in skip: continue
        out.append(f'        {role} = Color(0xFF{hexv.lstrip("#").upper()}),')
    out.append('    )')
    out.append('')
out += [
 '/**',
 ' * Ink for the Google sign-in capsule, which is a pinned white surface under',
 " * Google's branding rules and must not follow the theme (the old",
 ' * `SystemPalette.onLight`). 11.2:1 on white.',
 ' */',
 'internal val GoogleCapsuleInk = Color(0xFF4A3639)', '',
 '/** One of the six static schemes. Wallpaper schemes bypass this entirely. */',
 'internal fun pebblesColorScheme(',
 '    dark: Boolean,',
 '    contrast: ContrastLevel,',
 '): ColorScheme =',
 '    when (contrast) {',
 '        ContrastLevel.STANDARD -> if (dark) DarkScheme else LightScheme',
 '        ContrastLevel.MEDIUM -> if (dark) DarkMediumContrastScheme else LightMediumContrastScheme',
 '        ContrastLevel.HIGH -> if (dark) DarkHighContrastScheme else LightHighContrastScheme',
 '    }', '']
dst = 'app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt'
open(dst, 'w').write('\n'.join(out))
print(dst, sum(1 for l in out if 'Color(0xFF' in l), 'literals')
EOF
```

Expected: prints `… 289 literals` (48 roles × 6 — the export's 49 minus `shadow` — plus the capsule ink). If `lightColorScheme` rejects a named argument at compile time, that role is not a `ColorScheme` parameter in alpha27: add it to `skip` and regenerate.

- [ ] **Step 5: Write `ContrastLevel.kt`**

```kotlin
package app.pbbls.android.core.designsystem

import android.app.UiModeManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Which of the export's three contrast variants to draw. */
enum class ContrastLevel {
    STANDARD,
    MEDIUM,
    HIGH,
    ;

    companion object {
        /**
         * `UiModeManager.getContrast()` is a float in [-1, 1]; the system
         * setting's three stops land at 0, 0.5 and 1. Thirds split them.
         */
        fun fromSystemContrast(value: Float): ContrastLevel =
            when {
                value >= 2f / 3f -> HIGH
                value >= 1f / 3f -> MEDIUM
                else -> STANDARD
            }
    }
}

/**
 * The system contrast level, live: raising it in Settings recomposes the theme
 * without an activity restart. The API is 34+; on 33 (our minSdk) there is no
 * user contrast setting, so STANDARD is the truth there, not a fallback.
 */
@Composable
internal fun rememberContrastLevel(): ContrastLevel {
    if (LocalInspectionMode.current) return ContrastLevel.STANDARD
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return ContrastLevel.STANDARD
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var contrast by remember(uiModeManager) { mutableFloatStateOf(uiModeManager.contrast) }
    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { uiModeManager.removeContrastChangeListener(listener) }
    }
    return ContrastLevel.fromSystemContrast(contrast)
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ColorSchemeContrastTest*'`
Expected: 4 tests PASS. (The spec's pre-check computed every pair above threshold; a failure here means a generation error, not a design problem — diff the value against the JSON.)

- [ ] **Step 7: Point the Google capsule test at the new ink**

Replace the body of `$TEST/core/designsystem/GoogleSignInButtonContrastTest.kt` with:

```kotlin
package app.pbbls.android.core.designsystem

import app.pbbls.android.testing.AA_TEXT
import app.pbbls.android.testing.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Google capsule is a pinned light surface, so its label must not follow the
 * theme. Asserted numerically because no screenshot covers every scheme.
 */
class GoogleSignInButtonContrastTest {
    @Test
    fun labelIsLegibleOnTheCapsule() {
        val ratio = contrastRatio(GoogleCapsuleInk, GoogleButtonSurface)
        assertTrue("$ratio:1 fails WCAG AA (4.5:1)", ratio >= AA_TEXT)
    }
}
```

In `$SRC/core/designsystem/GoogleSignInButton.kt`, replace `googleButtonLabelColor(system: SystemPalette)` and its KDoc with nothing, and change the label colour to `color = GoogleCapsuleInk,`. Keep the `system` local only for the border (`system.muted`) — Part 3 migrates it.

- [ ] **Step 8: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*designsystem*'`
Expected: PASS except `PalettesTest` may still pass (it tests the old constants, which still exist until Task 1.5).

```bash
git add $TEST/testing/Contrast.kt $TEST/core/designsystem/ColorSchemeContrastTest.kt $TEST/core/designsystem/GoogleSignInButtonContrastTest.kt $SRC/core/designsystem/ColorSchemes.kt $SRC/core/designsystem/ContrastLevel.kt $SRC/core/designsystem/GoogleSignInButton.kt
git commit -m "feat(android): six m3-evo color schemes with an aa contrast test"
```

### Task 1.4: Material typography, shapes, hand typography

**Files:**
- Create: `$SRC/core/designsystem/Typography.kt`
- Create: `$SRC/core/designsystem/Shapes.kt`
- Create: `$SRC/core/designsystem/PebblesHandTypography.kt`
- Modify: `$SRC/core/designsystem/PebblesTypography.kt`
- Create: `$TEST/core/designsystem/MaterialTypographyTest.kt`

- [ ] **Step 1: Write the failing test**

`$TEST/core/designsystem/MaterialTypographyTest.kt`:

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MaterialTypographyTest {
    private val baseline = Typography()
    private val t = PebblesMaterialTypography

    @Test
    fun `display headline and title are ysabeau with lining figures`() {
        listOf(t.displayLarge, t.headlineMedium, t.titleLarge, t.titleMedium, t.titleSmallEmphasized).forEach {
            assertEquals(YsabeauFamily, it.fontFamily)
            assertEquals(YSABEAU_NUMBER_FEATURES, it.fontFeatureSettings)
        }
    }

    @Test
    fun `body and label are inclusive sans`() {
        listOf(t.bodyLarge, t.bodySmall, t.labelLarge, t.labelSmallEmphasized).forEach {
            assertEquals(InclusiveSansFamily, it.fontFamily)
        }
    }

    @Test
    fun `sizes are the m3 baseline`() {
        assertEquals(baseline.bodyLarge.fontSize, t.bodyLarge.fontSize)
        assertEquals(baseline.headlineMedium.fontSize, t.headlineMedium.fontSize)
        assertEquals(baseline.titleMediumEmphasized.fontWeight, t.titleMediumEmphasized.fontWeight)
    }

    @Test
    fun `hand faces keep their sizes`() {
        assertEquals(22f, PebblesHandTypography.bodyLeadHand.fontSize.value)
        assertEquals(56f, PebblesHandTypography.valenceWord.fontSize.value)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*MaterialTypographyTest*'`
Expected: compilation FAIL — `PebblesMaterialTypography`, `PebblesHandTypography` unresolved.

- [ ] **Step 3: Write `Typography.kt`**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

private fun TextStyle.ysabeau() = copy(fontFamily = YsabeauFamily, fontFeatureSettings = YSABEAU_NUMBER_FEATURES)

private fun TextStyle.inclusiveSans() = copy(fontFamily = InclusiveSansFamily)

private val Baseline = Typography()

/**
 * The export's type (#853): M3's default scale, faces swapped — Ysabeau for
 * display/headline/title, Inclusive Sans for body/label — including every
 * Expressive `*Emphasized` style so stock components and app code agree.
 * Sizes are deliberately the baseline's: the old 17 sp iOS rhythm is gone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PebblesMaterialTypography: Typography =
    Typography(
        displayLarge = Baseline.displayLarge.ysabeau(),
        displayMedium = Baseline.displayMedium.ysabeau(),
        displaySmall = Baseline.displaySmall.ysabeau(),
        headlineLarge = Baseline.headlineLarge.ysabeau(),
        headlineMedium = Baseline.headlineMedium.ysabeau(),
        headlineSmall = Baseline.headlineSmall.ysabeau(),
        titleLarge = Baseline.titleLarge.ysabeau(),
        titleMedium = Baseline.titleMedium.ysabeau(),
        titleSmall = Baseline.titleSmall.ysabeau(),
        bodyLarge = Baseline.bodyLarge.inclusiveSans(),
        bodyMedium = Baseline.bodyMedium.inclusiveSans(),
        bodySmall = Baseline.bodySmall.inclusiveSans(),
        labelLarge = Baseline.labelLarge.inclusiveSans(),
        labelMedium = Baseline.labelMedium.inclusiveSans(),
        labelSmall = Baseline.labelSmall.inclusiveSans(),
        displayLargeEmphasized = Baseline.displayLargeEmphasized.ysabeau(),
        displayMediumEmphasized = Baseline.displayMediumEmphasized.ysabeau(),
        displaySmallEmphasized = Baseline.displaySmallEmphasized.ysabeau(),
        headlineLargeEmphasized = Baseline.headlineLargeEmphasized.ysabeau(),
        headlineMediumEmphasized = Baseline.headlineMediumEmphasized.ysabeau(),
        headlineSmallEmphasized = Baseline.headlineSmallEmphasized.ysabeau(),
        titleLargeEmphasized = Baseline.titleLargeEmphasized.ysabeau(),
        titleMediumEmphasized = Baseline.titleMediumEmphasized.ysabeau(),
        titleSmallEmphasized = Baseline.titleSmallEmphasized.ysabeau(),
        bodyLargeEmphasized = Baseline.bodyLargeEmphasized.inclusiveSans(),
        bodyMediumEmphasized = Baseline.bodyMediumEmphasized.inclusiveSans(),
        bodySmallEmphasized = Baseline.bodySmallEmphasized.inclusiveSans(),
        labelLargeEmphasized = Baseline.labelLargeEmphasized.inclusiveSans(),
        labelMediumEmphasized = Baseline.labelMediumEmphasized.inclusiveSans(),
        labelSmallEmphasized = Baseline.labelSmallEmphasized.inclusiveSans(),
    )
```

- [ ] **Step 4: Write `Shapes.kt`**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.material3.Shapes

/**
 * M3 Expressive's default shape scale, unchanged (#853): extraSmall 4, small 8,
 * medium 12, large 16, largeIncreased 20, extraLarge 28, extraLargeIncreased 32,
 * extraExtraLarge 48. Named so the choice lives in one place.
 */
val PebblesShapes: Shapes = Shapes()
```

- [ ] **Step 5: Move the hand faces into `PebblesHandTypography.kt`**

Create `PebblesHandTypography.kt` holding `ReenieBeanieFamily`, `CaveatFamily`, the `caveat()` / `reenieBeanie()` builders, and:

```kotlin
/**
 * The only type Pebbles still owns (#853): handwritten faces with no M3 role.
 * Reached through `PebblesTheme.hand`.
 */
object PebblesHandTypography {
    val bodyLeadHand = reenieBeanie(size = 22.sp, tracking = (-0.045f).em)
    val largeTitleHand = reenieBeanie(size = 41.sp, tracking = (-0.049f).em)
    val nameInputHand = caveat(size = 36.sp)
    val valenceWord = caveat(size = 56.sp, weight = FontWeight.Bold)

    fun inkOverhang(style: TextStyle): Dp = if (style == valenceWord) 10.dp else 0.dp

    fun needsInkPadding(style: TextStyle): Boolean = inkOverhang(style) > 0.dp
}
```

Move the existing KDoc for `nameInputHand`, `valenceWord`, `inkOverhang` and `needsInkPadding` verbatim from `PebblesTypography.kt` — it carries measured reasoning (the Caveat Bold `t` overhang) that must survive.

- [ ] **Step 6: Re-point `PebblesTypography` (the bridge half for type)**

In `PebblesTypography.kt`:
- Delete `NunitoFamily`, `YsabeauSemiBoldFamily`, `ReenieBeanieFamily`, `CaveatFamily`, `caveat()`, `reenieBeanie()`, and the local `YSABEAU_NUMBER_FEATURES` (now in `PebblesTypeface.kt`).
- Rename `nunito(...)` to `inclusiveSans(...)` and give it `fontFamily = InclusiveSansFamily`.
- `title` and `buttonLabel`: `fontFamily = YsabeauFamily`.
- Hand tokens become aliases: `val bodyLeadHand = PebblesHandTypography.bodyLeadHand` (same for the other three) and `inkOverhang` / `needsInkPadding` delegate to `PebblesHandTypography`.
- Annotate the object: `@Deprecated("Bridge for #853: use MaterialTheme.typography roles or PebblesTheme.hand")`.
- Update the KDoc: faces are the export's; sizes stay the iOS ones only until Parts 3–5 remap each call site.

Then delete `app/src/main/res/font/nunito.ttf` and `app/src/main/res/font/ysabeau_semibold.ttf` — nothing references them now, and `lint` (warnings as errors) would fail on `UnusedResources`.

- [ ] **Step 7: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*designsystem*'`
Expected: `MaterialTypographyTest` PASS; the existing `TypographyTest` PASS (sizes, weights, tracking, uppercase set are unchanged).

- [ ] **Step 8: Commit**

```bash
git add -A $SRC/core/designsystem app/src/main/res/font $TEST/core/designsystem/MaterialTypographyTest.kt
git commit -m "feat(android): m3 typography on inclusive sans and ysabeau, expressive shapes"
```

### Task 1.5: Expressive root, bridge palettes, reduce-motion helper

**Files:**
- Modify: `$SRC/core/designsystem/PebblesTheme.kt` (whole file)
- Modify: `$SRC/core/designsystem/Palettes.kt` (whole file)
- Create: `$SRC/core/designsystem/ReduceMotion.kt`
- Modify: `$SRC/features/welcome/WelcomeScreen.kt:~296-306`, `$SRC/features/path/valence/ValenceFan.kt:~200-216`
- Modify: `$TEST/core/designsystem/PalettesTest.kt` (whole file)

- [ ] **Step 1: Rewrite `PalettesTest` as the bridge's failing test**

```kotlin
package app.pbbls.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/** The #853 bridge: old token names resolve to scheme roles. Deleted in Part 7. */
@Suppress("DEPRECATION")
class PalettesTest {
    @Test
    fun `system palette reads scheme roles`() {
        val s = LightScheme
        val p = systemPaletteFrom(s)
        assertEquals(s.onSurface, p.foreground)
        assertEquals(s.onSurfaceVariant, p.secondary)
        assertEquals(s.outlineVariant, p.muted)
        assertEquals(s.surface, p.background)
        assertEquals(GoogleCapsuleInk, p.onLight)
    }

    @Test
    fun `accent palette reads scheme roles`() {
        val s = DarkScheme
        val a = accentPaletteFrom(s)
        assertEquals(s.primary, a.primary)
        assertEquals(s.onPrimary, a.light)
        assertEquals(s.primaryContainer, a.secondary)
        assertEquals(s.onPrimaryContainer, a.shaded)
        assertEquals(s.primary.copy(alpha = 0.10f), a.surface)
    }

    @Test
    fun `primary hex is six digit rgb`() {
        assertEquals("#8E4955", accentPaletteFrom(LightScheme).primaryHex)
        assertEquals("#FFB2BC", accentPaletteFrom(DarkScheme).primaryHex)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*PalettesTest*'`
Expected: compilation FAIL — `systemPaletteFrom`, `accentPaletteFrom` unresolved.

- [ ] **Step 3: Rewrite `Palettes.kt` as the bridge**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

// The #853 bridge. The old iOS-derived token vocabulary, kept only so Part 1
// can change the whole app's look without touching ~650 call sites. Every
// value is a role of the active ColorScheme. Parts 3–5 move call sites to
// MaterialTheme.colorScheme; Part 7 deletes this file.

private const val BRIDGE = "Bridge for #853: use MaterialTheme.colorScheme roles"

@Deprecated(BRIDGE)
data class SystemPalette(
    val foreground: Color,
    val secondary: Color,
    val muted: Color,
    val background: Color,
    val onLight: Color,
)

@Deprecated(BRIDGE)
data class AccentPalette(
    val dark: Color,
    val shaded: Color,
    val primary: Color,
    val secondary: Color,
    val light: Color,
    val surface: Color,
    /** `primary` as `#RRGGBB`, for `currentColor` injection into SVG markup (6-digit only). */
    val primaryHex: String,
)

@Suppress("DEPRECATION")
internal fun systemPaletteFrom(scheme: ColorScheme): SystemPalette =
    SystemPalette(
        foreground = scheme.onSurface,
        secondary = scheme.onSurfaceVariant,
        muted = scheme.outlineVariant,
        background = scheme.surface,
        onLight = GoogleCapsuleInk,
    )

@Suppress("DEPRECATION")
internal fun accentPaletteFrom(scheme: ColorScheme): AccentPalette =
    AccentPalette(
        dark = scheme.onPrimaryContainer,
        shaded = scheme.onPrimaryContainer,
        primary = scheme.primary,
        secondary = scheme.primaryContainer,
        light = scheme.onPrimary,
        surface = scheme.primary.copy(alpha = 0.10f),
        primaryHex = scheme.primary.toRgbHex(),
    )

/** `#RRGGBB`, alpha dropped — the SVG pipeline misparses 8-digit hex. */
internal fun Color.toRgbHex(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

@Deprecated("Bridge for #853: use MaterialTheme.colorScheme.error")
internal val PebblesDestructive: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.error

@Deprecated("Bridge for #853: use MaterialTheme.colorScheme.tertiary")
internal val PebblesSuccess: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
```

`PebblesDestructive` / `PebblesSuccess` become composable getters. If a call site reads them outside composition (inside a `drawBehind {}` lambda, a `remember {}` block, a non-composable function), the compiler errors there: hoist the read to a `val` in the enclosing composable and use the local.

- [ ] **Step 4: Write `ReduceMotion.kt` and delete the two private copies**

```kotlin
package app.pbbls.android.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True when the user turned animations off (Developer options or the
 * accessibility "Remove animations" switch — both set the animator scale to 0).
 * Previews report true so screenshot references capture end states, not frames.
 * Lifted from the two private copies in WelcomeScreen and ValenceFan (#853).
 */
@Composable
fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
```

Delete `private fun rememberReduceMotion()` and its KDoc from `WelcomeScreen.kt` and `ValenceFan.kt`; add `import app.pbbls.android.core.designsystem.rememberReduceMotion` to both; remove imports that become unused (`Settings`, `LocalInspectionMode`, `LocalContext`, `remember` — only if unused elsewhere in the file).

- [ ] **Step 5: Rewrite `PebblesTheme.kt`**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

@Suppress("DEPRECATION")
val LocalSystemPalette = staticCompositionLocalOf { systemPaletteFrom(LightScheme) }

@Suppress("DEPRECATION")
val LocalAccentPalette = staticCompositionLocalOf { accentPaletteFrom(LightScheme) }

val LocalSpacing = staticCompositionLocalOf { Spacing }

@Suppress("DEPRECATION")
val LocalPebblesTypography = staticCompositionLocalOf { PebblesTypography }

val LocalHandTypography = staticCompositionLocalOf { PebblesHandTypography }

@Suppress("DEPRECATION")
@Deprecated("Bridge for #853: use MaterialTheme.colorScheme roles")
data class PebblesColors(
    val system: SystemPalette,
    val accent: AccentPalette,
)

/**
 * Pebbles' own tokens beside Material's (#853). Colour, type and shape are
 * `MaterialTheme.*`; this object keeps only what M3 has no slot for —
 * [spacing] and the handwritten faces ([hand]). [colors] and [type] are the
 * Part 1 bridge and go in Part 7.
 */
object PebblesTheme {
    @Suppress("DEPRECATION")
    @Deprecated("Bridge for #853: use MaterialTheme.colorScheme roles")
    val colors: PebblesColors
        @Composable get() = PebblesColors(LocalSystemPalette.current, LocalAccentPalette.current)

    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    @Suppress("DEPRECATION")
    @Deprecated("Bridge for #853: use MaterialTheme.typography roles")
    val type: PebblesTypography
        @Composable get() = LocalPebblesTypography.current

    val hand: PebblesHandTypography
        @Composable get() = LocalHandTypography.current
}

/**
 * Root theme (#853, supersedes D6). The M3-evo export in
 * `MaterialExpressiveTheme`, with Expressive motion, the export's type on the
 * M3 scale, and Expressive shapes.
 *
 * Scheme precedence: wallpaper colour when [dynamicColor] (minSdk 33 always
 * supports it; the OS applies its own contrast level to those), otherwise the
 * export's scheme for the system contrast level. [dynamicColor] defaults to
 * false so previews and screenshot references render the brand scheme; only
 * `MainActivity` passes the user's setting.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebblesTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val contrast = rememberContrastLevel()
    val context = LocalContext.current
    val scheme =
        when {
            dynamicColor -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> pebblesColorScheme(dark, contrast)
        }
    @Suppress("DEPRECATION")
    val system = remember(scheme) { systemPaletteFrom(scheme) }

    @Suppress("DEPRECATION")
    val accent = remember(scheme) { accentPaletteFrom(scheme) }
    @Suppress("DEPRECATION")
    CompositionLocalProvider(
        LocalSystemPalette provides system,
        LocalAccentPalette provides accent,
        LocalSpacing provides Spacing,
        LocalPebblesTypography provides PebblesTypography,
        LocalHandTypography provides PebblesHandTypography,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = PebblesMaterialTypography,
            shapes = PebblesShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 6: Run the unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. `ValenceStoneStyle`, `DebugTokenPreviewScreen` and every other reader of `SystemPalette`/`AccentPalette` compile unchanged (with deprecation warnings — expected, they are the migration's to-do list).

- [ ] **Step 7: Commit**

```bash
git add -A $SRC/core/designsystem $SRC/features/welcome/WelcomeScreen.kt $SRC/features/path/valence/ValenceFan.kt $TEST/core/designsystem/PalettesTest.kt
git commit -m "feat(android): material expressive root with the old tokens bridged to scheme roles"
```

### Task 1.6: AppearancePreferences and the activity wiring

**Files:**
- Create: `$SRC/core/data/AppearancePreferences.kt`
- Create: `$TEST/core/data/AppearancePreferencesTest.kt`
- Modify: `$SRC/MainActivity.kt` (injection + `setContent`)

- [ ] **Step 1: Write the failing test**

The class takes a `SharedPreferences` (not a `Context`) so the JVM test can hand it an in-memory fake.

```kotlin
package app.pbbls.android.core.data

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePreferencesTest {
    @Test
    fun `wallpaper colours default to on`() {
        assertTrue(AppearancePreferences(InMemoryPrefs()).useWallpaperColors)
    }

    @Test
    fun `a write persists and survives a new instance`() {
        val prefs = InMemoryPrefs()
        AppearancePreferences(prefs).setUseWallpaperColors(false)
        assertFalse(AppearancePreferences(prefs).useWallpaperColors)
    }
}

/** Minimal SharedPreferences for booleans — the only type AppearancePreferences writes. */
private class InMemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ) = values[key] as? Boolean ?: defValue

    override fun edit(): SharedPreferences.Editor =
        object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ) = apply { pending[key!!] = value }

            override fun apply() {
                values.putAll(pending)
            }

            override fun commit(): Boolean = true.also { apply() }

            override fun putString(
                key: String?,
                value: String?,
            ) = this

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ) = this

            override fun putInt(
                key: String?,
                value: Int,
            ) = this

            override fun putLong(
                key: String?,
                value: Long,
            ) = this

            override fun putFloat(
                key: String?,
                value: Float,
            ) = this

            override fun remove(key: String?) = this

            override fun clear() = this
        }

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(
        key: String?,
        defValue: String?,
    ) = defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ) = defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ) = defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ) = defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ) = defValue

    override fun contains(key: String?) = values.containsKey(key)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppearancePreferencesTest*'`
Expected: compilation FAIL — `AppearancePreferences` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local appearance settings (#853). Compose state, so the theme
 * recomposes the moment the Settings switch flips — no restart.
 *
 * SharedPreferences, per D5. This is the first real user setting, which is the
 * trigger D5 named for DataStore; one boolean does not pay for that migration,
 * and the decision log leaves the call to #858 or the next setting.
 */
@Singleton
class AppearancePreferences internal constructor(
    private val prefs: SharedPreferences,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    /** Wallpaper (dynamic) colour. On by default (#853, maintainer decision). */
    var useWallpaperColors: Boolean by mutableStateOf(prefs.getBoolean(KEY_USE_WALLPAPER_COLORS, true))
        private set

    fun setUseWallpaperColors(value: Boolean) {
        useWallpaperColors = value
        prefs.edit().putBoolean(KEY_USE_WALLPAPER_COLORS, value).apply()
    }

    private companion object {
        /** Shared with OnboardingPreferences: one prefs file for the app. */
        const val PREFS_NAME = "pebbles_prefs"
        const val KEY_USE_WALLPAPER_COLORS = "useWallpaperColors"
    }
}
```

If Hilt rejects two constructors on a `@Singleton` class (it requires exactly one `@Inject` constructor — which this has), keep as is; if ktlint objects to the secondary-constructor layout, run `ktlintFormat`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppearancePreferencesTest*'`
Expected: 2 tests PASS.

- [ ] **Step 5: Wire `MainActivity`**

Add beside the other `@Inject` fields:

```kotlin
    /** Read by the theme root; Settings writes it (#853). */
    @Inject
    internal lateinit var appearance: AppearancePreferences
```

and in `setContent`, replace `PebblesTheme {` with:

```kotlin
            PebblesTheme(dynamicColor = appearance.useWallpaperColors) {
```

Import `app.pbbls.android.core.data.AppearancePreferences`.

- [ ] **Step 6: Commit**

```bash
git add $SRC/core/data/AppearancePreferences.kt $TEST/core/data/AppearancePreferencesTest.kt $SRC/MainActivity.kt
git commit -m "feat(android): wallpaper colours on by default through appearance preferences"
```

### Task 1.7: Splash colours, high-contrast gallery, decision log

**Files:**
- Modify: `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml`, `app/src/main/res/values/themes.xml`
- Modify: `app/src/screenshotTest/kotlin/app/pbbls/android/DesignSystemScreenshots.kt`
- Modify: `docs/decisions/log.md` (append)

- [ ] **Step 1: Splash colours**

`values/colors.xml` — keep `ic_launcher_background` (#C07A7A, the launcher icon is unchanged per D9), add a splash-only icon ground, and move the splash background to the new light surface. Update the header comment: the source is now `ColorSchemes.kt`, not `Palettes.kt`.

```xml
<resources>
    <!-- Launcher icon ground. Deliberately NOT the new scheme (#853 D9): the icon
         is generated from the iOS mark and is the store identity. -->
    <color name="ic_launcher_background">#C07A7A</color>
    <!-- LightScheme.primary — the splash icon circle. Same in both modes: the
         cream mark needs a dark ground, and the dark scheme's primary is pale. -->
    <color name="splash_icon_background">#8E4955</color>
    <!-- LightScheme.surface — matches the first Compose frame under the brand
         scheme. With wallpaper colours on the first frame differs; the splash
         cannot know the wallpaper scheme before Compose runs. -->
    <color name="splash_background">#FFF8F7</color>
</resources>
```

`values-night/colors.xml`: `splash_background` → `#1A1112` (`DarkScheme.surface`).
`themes.xml`: `windowSplashScreenIconBackgroundColor` → `@color/splash_icon_background`.

- [ ] **Step 2: High-contrast gallery previews**

Contrast is not a preview parameter, so the gallery renders the high-contrast schemes directly. In `DesignSystemScreenshots.kt`, add beside the existing `ChromeGallery` previews:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HighContrast(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    // PebblesTheme supplies the Pebbles locals; the inner theme swaps only the scheme.
    PebblesTheme {
        MaterialExpressiveTheme(
            colorScheme = pebblesColorScheme(dark, ContrastLevel.HIGH),
            typography = PebblesMaterialTypography,
            shapes = PebblesShapes,
            content = content,
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChromeGalleryHighContrastLight() {
    HighContrast(dark = false) { ChromeGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ChromeGalleryHighContrastDark() {
    HighContrast(dark = true) { ChromeGallery() }
}
```

Note: while the bridge exists, `ChromeGallery`'s bridged tokens still read the *outer* `PebblesTheme`'s standard scheme; only stock components show high contrast. That is correct after Part 3, when the gallery's components read `MaterialTheme` directly. Mention this in the PR body.

- [ ] **Step 3: Decision-log entry**

Append to `docs/decisions/log.md`:

```markdown
## 2026-09-24 — Android adopts the M3-evo Material 3 Expressive theme; D6 is superseded (#853)

- **Status:** taken
- **Scope:** android, ui
- **Context:** D6 made Material 3 a rendering engine only: no colour roles, typography or shapes in app code, with a partial bridge so stock components stopped rendering purple. The bridge left secondary/tertiary/error/containers at the M3 baseline, all five surface-container tiers equal to the ground (a dark sheet was `#171012` on `#171012`), no `Typography` (stock components rendered Roboto) and no `Shapes`, and the shared accent `#C07A7A` carried white labels at 3.33:1. The maintainer produced a Material Theme Builder export (seed `#CE7E8A`) as the new Android brand.
- **Decision:** Android forgets the iOS-derived palette and type. The export's six schemes (light/dark × standard/medium/high contrast) are the theme, rooted in `MaterialExpressiveTheme` with `MotionScheme.expressive()`, the export's type (Inclusive Sans + Ysabeau, bundled variable fonts, M3 default scale, Expressive emphasized styles) and M3 Expressive shapes. App code reads `MaterialTheme.colorScheme/typography/shapes`; `PebblesTheme` keeps only `spacing` and the handwritten faces. **The error colour is amber** (`#7F560F` / `#F3BD6E`). **Wallpaper colour is on by default**, with a Settings switch. The system contrast level picks the scheme live on Android 14+. material3 is pinned to `1.5.0-alpha27`. The launcher icon is unchanged.
- **Why:** Stock components can only look like the brand if the brand is expressed in the theme they read, and #854 is about to replace every hand-rolled control with a stock one. A partial bridge guarantees leaks at every role it forgets. Amber over red: red read as aggressive; amber catches the eye without alarming. Wallpaper colour on by default is the platform's current expectation; the brand scheme remains the default for anyone who turns it off, and for every screenshot. alpha27 rather than 1.4.0 because 1.4.0 keeps the emphasized type getters, `MotionScheme.expressive()` and the increased shape steps internal; rather than alpha28+ because those pull Compose ui/foundation/runtime to `1.13.0-alpha01`.
- **Consequences:** **A third deliberate iOS divergence** (after the four-tab bar and the `core/` split): Android's palette, error colour and type no longer match iOS; #921 decides whether iOS follows. Emotion palettes and pebble renders are server data and unaffected; the pebble SVG accent (`primaryHex`) now follows `primary`, including wallpaper colour. **D5's DataStore trigger has fired** — `AppearancePreferences` is the first real user setting — but one boolean stays in SharedPreferences; #858 or the next setting decides. Spacing moves to M3's 4 dp grid in the same stack, delivering #854's "align Spacing" line. The migration ships as a seven-part stack with a temporary deprecated bridge; a Konsist test forbids colour, corner and font-size literals once it is gone.
- **Supersedes / Superseded-by:** Supersedes M38 D6. Narrows `apps/android/CLAUDE.md`'s "mirrors `apps/ios` 1:1 in behavior, tokens and funnel" — tokens no longer mirror.
- **Refs:** #853, #854, #858, #921, `docs/superpowers/specs/2026-09-24-android-m3-expressive-theme-design.md`, `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt`, `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PebblesTheme.kt`.
```

- [ ] **Step 4: Full gate**

Run the per-part gate. Expected: green. If `lint` reports a new finding, fix it; never `updateLintBaseline` for something this change introduced.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml app/src/main/res/values/themes.xml app/src/screenshotTest/kotlin/app/pbbls/android/DesignSystemScreenshots.kt ../../docs/decisions/log.md
git commit -m "docs(android): supersede d6 with the m3-evo expressive theme"
```

### Task 1.8: Emulator smoke, Arkaik, PR

- [ ] **Step 1: Smoke on the AVD**

```bash
./gradlew installDebug
```

On the `oxymore-eclipse` Pixel 7 AVD check, with screenshots via the `android-cli` skill: (a) light, wallpaper colours on — scheme follows the wallpaper; (b) turn the switch off by clearing app data then `adb shell am start` with the pref set false:
`adb shell run-as app.pbbls.android sh -c 'echo "<?xml version=\"1.0\" encoding=\"utf-8\"?><map><boolean name=\"useWallpaperColors\" value=\"false\" /></map>" > shared_prefs/pebbles_prefs.xml'` — brand scheme; (c) dark mode; (d) Settings → Accessibility → Contrast → High — scheme switches live without a restart. Note: (b) overwrites `hasSeenOnboarding`; expect onboarding to show again.

- [ ] **Step 2: Arkaik**

Load the arkaik-mcp tools (`ToolSearch select:mcp__arkaik-mcp__list_nodes,mcp__arkaik-mcp__get_node,mcp__arkaik-mcp__update_node`). If they are unavailable, say so and stop — never fall back to `docs/arkaik/bundle.json`. `list_nodes` for acceptances/views mentioning theme, design system, dark mode or settings on android; move the ones this stack touches to `development` with `update_node`; record their ids for the Lab Note `nodes:`.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin feat/853-m3-theme-foundation
```

PR title: `feat(android): material 3 expressive theme from the m3-evo export`. Body starts `Part of #853`, lists key files, the bridge explanation, the high-contrast-gallery note from Task 1.7, and a Lab Note (use the `lab-note` skill; `platform: android`; e.g. EN "Pebbles gets a fresh coat of paint" — a deeper rose, softer amber warnings, a new typeface, and it can match your wallpaper). Labels `feat`, `ui`, `android`; milestone `M61 · Android Refacto`. Then add `rebaseline-screenshots` and approve the held runs.

---

# Part 2 — Wallpaper colours switch (`feat/853-wallpaper-colours-setting`)

Create the branch on top of Part 1 with `gh stack` (see the `gh-stack` skill).

### Task 2.1: Settings row

**Files:**
- Modify: `$SRC/features/profile/SettingsViewModel.kt` (constructor, two members)
- Modify: `$SRC/features/profile/SettingsScreen.kt` (new section before the account sections)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`
- Modify: `$TEST/features/profile/SettingsViewModelTest.kt` (if it constructs the VM — add the new argument)

- [ ] **Step 1: Strings**

`values/strings.xml`:

```xml
    <string name="settings_appearance_section">Appearance</string>
    <string name="settings_wallpaper_colors_title">Use wallpaper colours</string>
    <string name="settings_wallpaper_colors_body">Match Pebbles to your wallpaper</string>
```

`values-fr/strings.xml`:

```xml
    <string name="settings_appearance_section">Apparence</string>
    <string name="settings_wallpaper_colors_title">Couleurs du fond d’écran</string>
    <string name="settings_wallpaper_colors_body">Accorde Pebbles à ton fond d’écran</string>
```

- [ ] **Step 2: Failing ViewModel test**

In `SettingsViewModelTest.kt` (create beside the existing profile VM tests if absent, following `AchievementsViewModelTest`'s `MainDispatcherRule` shape), add:

```kotlin
    @Test
    fun `wallpaper switch writes through to appearance preferences`() {
        val appearance = AppearancePreferences(InMemoryPrefs())
        val vm = newViewModel(appearance = appearance)
        vm.onUseWallpaperColorsChange(false)
        assertFalse(appearance.useWallpaperColors)
        assertFalse(vm.useWallpaperColors)
    }
```

Move `InMemoryPrefs` from `AppearancePreferencesTest.kt` to `$TEST/testing/InMemoryPrefs.kt` (make it `internal class`) so both tests use it. `newViewModel` is the test file's existing factory; add an `appearance` parameter defaulting to `AppearancePreferences(InMemoryPrefs())`.

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsViewModelTest*'`
Expected: compilation FAIL — `onUseWallpaperColorsChange` unresolved.

- [ ] **Step 3: ViewModel**

Add `private val appearance: AppearancePreferences,` to the `@Inject constructor`, then:

```kotlin
        /**
         * Device-local and applied instantly, so it bypasses the form and the
         * dirty/save cycle — there is nothing to send to the server (#853).
         */
        val useWallpaperColors: Boolean
            get() = appearance.useWallpaperColors

        fun onUseWallpaperColorsChange(value: Boolean) {
            appearance.setUseWallpaperColors(value)
        }
```

Run the test. Expected: PASS.

- [ ] **Step 4: Screen**

`SettingsScreen` has a stateless layer taking `uiState` plus lambdas (follow its existing parameter style). Add parameters `useWallpaperColors: Boolean` and `onUseWallpaperColorsChange: (Boolean) -> Unit`, pass `viewModel.useWallpaperColors` / `viewModel::onUseWallpaperColorsChange` from the stateful overload, and add a section as the first `PebblesListSection` in the column, modelled on the existing public-profile `Switch` row at `SettingsScreen.kt:~291`:

```kotlin
            PebblesListSection(title = stringResource(R.string.settings_appearance_section)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = useWallpaperColors,
                                role = Role.Switch,
                                onValueChange = onUseWallpaperColorsChange,
                            ).padding(PebblesTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_wallpaper_colors_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.settings_wallpaper_colors_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // null onCheckedChange: the Row owns the toggle, so TalkBack
                    // announces one "switch, on" node rather than two.
                    Switch(checked = useWallpaperColors, onCheckedChange = null)
                }
            }
```

Match `PebblesListSection`'s real signature (read it in `core/designsystem/PebblesList.kt`) — the `title =` name above is illustrative of the call, not a guess to keep if it differs.

- [ ] **Step 5: Screenshot**

In `SettingsScreenshots.kt`, add the new row to the gallery with both states, as its other rows are composed. Stack `@PreviewFrench` on it (the French string is the longer one).

- [ ] **Step 6: Gate, commit, PR**

Run the per-part gate.

```bash
git add -A $SRC/features/profile app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml $TEST app/src/screenshotTest
git commit -m "feat(android): settings switch for wallpaper colours"
```

PR `feat(android): a switch for wallpaper colours`, `Part of #853`, Lab Note (EN "Your wallpaper, your colours" / FR with "Tu"), labels + milestone as Part 1, then `rebaseline-screenshots`.

---

# Parts 3–5 — Call-site migration

These three parts share one procedure and one rulebook; they differ only in their file lists.

## The rulebook

Read the spec's "Migration rules" tables. Operational form:

**Colour — decide by what the colour is *doing* at that line:**

| Old read | If it is… | Write |
|---|---|---|
| `system.foreground` | text/icon | `MaterialTheme.colorScheme.onSurface` |
| `system.secondary` | text/icon | `…onSurfaceVariant` |
| `system.secondary` | a selected fill | `…secondaryContainer`, content `…onSecondaryContainer` |
| `system.muted` | `border(…)`, divider, stroke | `…outlineVariant` |
| `system.muted` | `background(…)`, track, placeholder fill | `…surfaceContainerHighest` |
| `system.muted` | disabled text/icon | `…onSurface.copy(alpha = 0.38f)` |
| `system.background` | screen ground | `…surface` |
| `system.background` | sheet/menu/card/dialog body | `…surfaceContainerLow` (cards), `…surfaceContainer` (menus, sheets), `…surfaceContainerHigh` (dialogs, overlays) |
| `accent.primary` | fill under a label/icon | fill `…primary`, content `…onPrimary` |
| `accent.primary` | text/link/icon/stroke | `…primary` |
| `accent.light` | content on primary | `…onPrimary` |
| `accent.surface`, `accent.secondary` | tinted fill | `…primaryContainer`, content `…onPrimaryContainer` |
| `accent.shaded`, `accent.dark` | text on a tint | `…onPrimaryContainer` |
| `accent.primaryHex` | SVG injection | `MaterialTheme.colorScheme.primary.toRgbHex()` |
| `PebblesDestructive` | text/icon | `…error`; filled button `…error`/`…onError`; banner `…errorContainer`/`…onErrorContainer` |
| `PebblesSuccess` | | `…tertiary` |
| `Color.White` on an accent fill | | `…onPrimary` |

`EmotionPalette` fields (`palette.primary`, `palette.light`, … on an emotion palette) are **server data, not theme tokens — leave them alone.** The regexes below over-match them; read the type.

Where a composable takes `system: SystemPalette` / `accent: AccentPalette` parameters (e.g. `ValenceStoneStyle`), change the parameter to `scheme: ColorScheme` and map inside.

**Type:** spec table "Type". Each `PebblesText(text, style = PebblesTheme.type.X, …)` becomes `Text(text, style = MaterialTheme.typography.<role>, …)` — same remaining arguments. A `.copy(fontSize = …)` becomes the role in the spec's override list with no copy. Hand faces: `PebblesTheme.type.bodyLeadHand` → `PebblesTheme.hand.bodyLeadHand` (same for the four).

**Corners:** spec table "Corners". `RoundedCornerShape(12.dp)` → `MaterialTheme.shapes.medium`; `RoundedCornerShape(50)` → `CircleShape`. Largest-increased steps are `@ExperimentalMaterial3ExpressiveApi`: opt in at the function. Top-only: `MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)`.

**Opt-ins:** reading any `*Emphasized` style or `largeIncreased`/`extraLargeIncreased` needs `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on the enclosing function.

## The procedure (per file)

- [ ] Read the whole file.
- [ ] Rewrite every bridged read per the rulebook. Hoist `val colors = MaterialTheme.colorScheme` / `val type = MaterialTheme.typography` at the top of a composable when it reads more than three roles, mirroring the old `val system = PebblesTheme.colors.system` idiom.
- [ ] Delete the now-unused bridge imports; add `androidx.compose.material3.MaterialTheme` and `androidx.compose.material3.Text`.
- [ ] Verify the file is clean:
  ```bash
  grep -nE 'PebblesTheme\.(colors|type)|PebblesTypography|PebblesText\(|PebblesDestructive|PebblesSuccess|RoundedCornerShape\([0-9]|fontSize *=|Color\(0x|SystemPalette|AccentPalette|tonalElevation *= *[1-9]' <file>
  ```
  Expected: no output (the `tonalElevation` rule applies only to the two overlay files).
- [ ] After each batch of ~5 files: `./gradlew :app:compileDebugKotlin` — expected success, and the deprecation-warning count shrinks.

At the end of each part: per-part gate, `validateDebugScreenshotTest` locally to review which previews moved (expect many; skim the diffs for a wrong role — e.g. a border that became a filled block), commit per sub-area with `quality(android): <area> reads material roles (#853)`, push, PR with `Part of #853`, the `no-lab-note` label, then `rebaseline-screenshots`.

### Part 3 — `feat/853-core-material-roles`: core and root

Files (bridged reads per file in parentheses):

`core/designsystem/`: `DashedPlaceholder` (2), `DeleteDialogs` (24), `GoogleSignInButton` (7), `LegalDisclaimer` (5 — includes a 12 sp override → `labelMedium`), `PebblesAuthSwitcher` (8 — selected segment is `secondaryContainer`/`onSecondaryContainer`, track `surfaceContainerHighest`), `PebblesCheckbox` (10), `PebblesList` (11), `PebblesPrimaryButton` (9 — label `onPrimary`, disabled per the rulebook), `PebblesScreen` (14), `PebblesTextInput` (10), `ProfileCard` (3), `ProfileEmptyState` (7), `SurfaceTile` (9).
`core/ui/`: `AchievementMomentOverlay` (23), `GlyphBanner` (13), `GlyphView` (14), `KarmaEarnedCapsule` (10), `PebbleRow` (14), `RippleBadge` (5), `RippleStrokeTone` (3), `SoulItem` (11), `render/PebbleThumbnail` (1 — `primaryHex`).
Root: `RootScreen.kt` (3), `navigation/PebblesNavigationBar.kt` (12), `rive/RiveLogo.kt` (1 — placeholder `surfaceContainerHighest` fill, `outlineVariant` border).

Extra tasks in Part 3:

- [ ] **Overlays** — in `KarmaEarnedCapsule.kt` and `AchievementMomentOverlay.kt`: the `Surface(tonalElevation = 3.dp, …)` becomes `Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp, …)`. Then gate their entrances on reduce motion:
  ```kotlin
  val reduceMotion = rememberReduceMotion()
  AnimatedVisibility(
      visible = …,
      enter = if (reduceMotion) EnterTransition.None else fadeIn() + slideInVertically { it / 2 },
      exit = if (reduceMotion) ExitTransition.None else <existing exit>,
  )
  ```
  (Achievement: `fadeIn()` / its existing exit.) The karma ring's `tween` is a countdown, not decoration — leave it.
- [ ] **`PebblesText`** — once no `core` file calls it, leave the function in place (features still do); it goes in Part 7.
- [ ] **`DebugTokenPreviewScreen.kt`** (44) — rewrite its swatch list to show scheme roles (`primary`, `onPrimary`, … the full ladder) instead of `system.*`/`accent.*`. Its screenshot re-baselines.

### Part 4 — `feat/853-features-material-roles`: everything but path

`features/welcome/`: `WelcomeCarousel` (3), `WelcomeScreen` (9 — delete `ErrorRed`, use `error`; 12 sp override → `labelMedium`), `WelcomeSlideView` (7 — 22 sp → `titleLarge`).
`features/auth/AuthScreen` (6 — delete `ErrorRed`; the two 12 sp error texts → `bodyMedium` in `error`, which is also the "error text at 14 sp" acceptance item).
`features/onboarding/`: `OnboardingPageView` (9 — 24 sp → `headlineSmall`), `OnboardingScreen` (8).
`features/connections/`: `AcceptInviteScreen` (49), `ConnectionsScreen` (26), `InviteScreen` (37), `RemoveConnectionDialog` (20).
`features/lab/`: `AnnouncementDetailScreen` (19 — 20 sp → `titleLarge`), `LabScreen` (19), `LogListScreen` (13), `components/AnnouncementRow` (12), `components/FeaturedCommunityCard` (10), `components/LabMarkdownBody` (11 — 22/20 sp headings → `titleLarge`), `components/LogTimeline` (15), `components/ReactionButton` (7).
`features/glyph/`: `carve/GlyphCarveScreen` (38), `store/GlyphDetailDrawer` (40), `store/GlyphTabBar` (12), `store/GlyphsListScreen` (30), `store/SlideToConfirm` (12).
`features/profile/`: `AchievementsScreen` (29), `CollectionDetailScreen` (13), `CollectionFormScreen` (25), `CollectionsListScreen` (27), `ProfileScreen` (11), `SettingsScreen` (74), `SoulDetailScreen` (17), `SoulFormScreen` (22), `SoulsListScreen` (16), `components/AssiduityGrid` (3), `components/CollectionModeBadge` (9), `components/DataTile` (9), `components/ProfileAchievementsCard` (15), `components/ProfileBanner` (2), `components/ProfileCollectionCard` (13), `components/ProfileCollectionsCard` (2), `components/ProfileLabCard` (12), `components/ProfileLogoutButton` (7), `components/ProfileShortcutsRow` (1), `components/ProfileStatsCard` (1), `components/RipplesRow` (8).

Commit per feature folder (six commits). Part 4 is the largest diff by file count; it is mechanical, and the PR body should say so and point reviewers at the rulebook.

### Part 5 — `feat/853-path-material-roles`: path

`features/path/`: `DraftsScreen` (28), `EditPebbleScreen` (26 — `accent.primaryHex` fallback → `colorScheme.primary.toRgbHex()`), `PathScreen` (36), `PebbleDetailScreen` (20).
`components/`: `NewPebbleButton` (9 — 20 sp → `titleLarge`), `NewPebbleFab` (4), `PathPebbleRow` (16), `WeekHeader` (8), `WeekPebbleList` (8 — 20 sp → `titleLarge`), `WeekRoll` (6 — 13 sp → `bodySmall`).
`create/`: `AttachedPhotoView` (19), `CreatePebbleScreen` (26), `ExistingSnapRow` (9), `PebbleForm` (57), `VisibilityChip` (12), `WhenRow` (14), `pickers/DomainPickerContent` (8), `pickers/EmotionPickerSheet` (21), `pickers/GlyphPickerSheet` (29), `pickers/SoulPickerSheet` (17).
`read/`: `PebblePageColors` (18), `PebblePrivacyBadge` (3), `PebbleReadPetroglyph` (1), `PebbleReadTitle` (8 — 24 sp → `headlineSmall`), `PebbleReadView` (12), `PebbleSnapFrame` (1), `PetroglyphColors` (14).
`record/`: `RecordFlowChrome` (6), `RecordFlowScreen` (33), `RecordStepScaffold` (11), `steps/RecordCollectionStep` (12), `steps/RecordNameStep` (12 — `nameInputHand` → `PebblesTheme.hand`), `steps/RecordPhotoStep` (35), `steps/RecordPrivacyStep` (20), `steps/RecordSuccessStep` (10), `steps/RecordWhenStep` (28).
`valence/`: `ValenceRoll` (7), `ValenceStone` (1), `ValenceStoneStyle` (11 — parameters to `scheme: ColorScheme`; `joySurface` literal → `scheme.tertiaryContainer`), `ValenceWord` (6 — `valenceWord`/`inkOverhang` → `PebblesTheme.hand` / `PebblesHandTypography`).

Extra task in Part 5:

- [ ] **Record-flow step transition honours reduce motion.** In `RecordFlowScreen.kt`, read `val reduceMotion = rememberReduceMotion()` above the `AnimatedContent`, and at the top of `transitionSpec`:
  ```kotlin
  if (reduceMotion) return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
  ```
  Keep the existing slide spec below it unchanged.

Commit per sub-folder (`path` root, `components`, `create`, `read`, `record`, `valence`).

---

# Part 6 — Spacing grid (`feat/853-spacing-grid`)

### Task 6.1: Spacing values

**Files:**
- Modify: `$SRC/core/designsystem/Spacing.kt`
- Create: `$TEST/core/designsystem/SpacingTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingTest {
    @Test
    fun `spacing is the m3 4 dp grid`() {
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp), listOf(Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl))
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*SpacingTest*'` — Expected: FAIL (`3.0.dp` ≠ `4.0.dp`).

- [ ] **Step 2: Implement**

```kotlin
/**
 * M3's 4 dp spacing grid (#853): 4 / 8 / 12 / 16 / 24 / 32. The old scale was
 * rooted on the iOS 17 pt body size, which the M3 type scale replaced. Use
 * these for paddings and gaps; corner radii come from `MaterialTheme.shapes`.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
```

Run the test — Expected: PASS.

- [ ] **Step 3: Gate, review, PR**

Per-part gate; `validateDebugScreenshotTest` locally and check the font-scale-2.0 previews for clipping introduced by the tighter `lg`/`xxl`. Commit `quality(android): spacing moves to the m3 4 dp grid (#853)`, PR `Part of #853`, `no-lab-note`, `rebaseline-screenshots`. Comment on #854 that its "align Spacing" line is delivered here.

---

# Part 7 — Lockdown (`feat/853-theme-lockdown`)

### Task 7.1: Migrate the test source sets

- [ ] Apply the rulebook to `app/src/screenshotTest/**` (files listed by `grep -rlE 'PebblesTheme\.(colors|type)|PebblesTypography|PebblesText\(|PebblesDestructive|PebblesSuccess|SystemPalette|AccentPalette' app/src/screenshotTest`) — about 19 files, mostly one line each; `SettingsScreenshots` (22) and `DesignSystemScreenshots` (11) are the real ones. In `DesignSystemScreenshots`, the `HighContrast` wrapper from Task 1.7 no longer needs the outer `PebblesTheme` once `ChromeGallery` reads `MaterialTheme`; keep it anyway for `LocalSpacing`.
- [ ] Delete `$TEST/core/designsystem/TypographyTest.kt` and `PalettesTest.kt` (they test the bridge).
- [ ] Commit `test(android): screenshot and unit tests read material roles (#853)`.

### Task 7.2: Delete the bridge

- [ ] Delete `$SRC/core/designsystem/Palettes.kt`, `PebblesTypography.kt`, `PebblesText.kt`. Move `toRgbHex()` into `ColorSchemes.kt` first (it is used by `PebbleThumbnail`/`EditPebbleScreen`).
- [ ] In `PebblesTheme.kt`: delete `LocalSystemPalette`, `LocalAccentPalette`, `LocalPebblesTypography`, `PebblesColors`, `PebblesTheme.colors`, `PebblesTheme.type`, the two `remember(scheme)` bridge values and their providers, and every `@Suppress("DEPRECATION")`.
- [ ] Run: `grep -rnE 'PebblesTheme\.(colors|type)|PebblesTypography|PebblesText|PebblesDestructive|PebblesSuccess|SystemPalette|AccentPalette' app/src` — Expected: no output.
- [ ] `./gradlew :app:compileDebugKotlin` — Expected: success with zero `DEPRECATION` warnings from `core.designsystem`.
- [ ] Commit `quality(android): delete the #853 token bridge`.

### Task 7.3: ThemeLiteralsTest

**Files:**
- Create: `$TEST/architecture/ThemeLiteralsTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package app.pbbls.android.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme is the only place a colour, a corner radius or a font size is
 * decided (#853). Scoped to `features` and `core.ui`: `core.designsystem` is
 * where the theme itself lives, and `ColorSchemes.kt` is generated.
 */
class ThemeLiteralsTest {
    private val forbidden =
        listOf(
            Regex("""Color\(0x""") to "a Color(0x…) literal — use a MaterialTheme.colorScheme role",
            Regex("""RoundedCornerShape\(\s*[0-9]""") to "a literal corner radius — use MaterialTheme.shapes or CircleShape",
            Regex("""\.copy\([^)]*fontSize""") to "a font-size override — use a MaterialTheme.typography role",
        )

    @Test
    fun `features and core ui decide no colours corners or font sizes`() {
        val offenders =
            Konsist
                .scopeFromProject(sourceSetName = "main")
                .files
                .filter { f ->
                    val pkg = f.packagee?.name.orEmpty()
                    pkg.startsWith("$ROOT.features") || pkg.startsWith("$ROOT.core.ui")
                }.flatMap { file ->
                    file.text.lines().withIndex().flatMap { (i, line) ->
                        forbidden.filter { (re, _) -> re.containsMatchIn(line) }.map { (_, why) ->
                            "${file.path.substringAfter("kotlin/")}:${i + 1}: $why"
                        }
                    }
                }

        assertTrue("theme literals outside the theme:\n" + offenders.joinToString("\n") { "  $it" }, offenders.isEmpty())
    }

    private companion object {
        const val ROOT = "app.pbbls.android"
    }
}
```

- [ ] **Step 2: Prove it bites**

Temporarily add `val x = Color(0xFF000000)` to any `features/` file, run `./gradlew :app:testDebugUnitTest --tests '*ThemeLiteralsTest*'` — Expected: FAIL naming that line. Remove it, re-run — Expected: PASS.

- [ ] **Step 3: Commit** `test(android): konsist forbids theme literals outside the theme (#853)`.

### Task 7.4: Android CLAUDE.md

- [ ] In `apps/android/CLAUDE.md`:
  - Opening paragraph: "Two deliberate divergences" → "Three", adding **the M3-evo theme** (#853, 2026-09-24) — palette, error colour and type no longer mirror iOS — and narrow "mirrors `apps/ios` 1:1 in **behavior, tokens and funnel**" to "behavior and funnel".
  - Replace the "### Theme (sub-project B)" section with:

```markdown
### Theme (#853)

- **App code reads `MaterialTheme.colorScheme`, `.typography` and `.shapes` —
  nothing else decides a colour, a type style or a corner.** The theme is the
  M3-evo Material Theme Builder export: six generated schemes in
  `core/designsystem/ColorSchemes.kt` (regenerate with the script in
  `docs/superpowers/plans/2026-09-24-android-m3-expressive-theme.md`, never
  hand-edit), `PebblesMaterialTypography` (Inclusive Sans + Ysabeau, bundled
  variable TTFs, M3 default scale), `PebblesShapes` (M3 Expressive defaults),
  under `MaterialExpressiveTheme` with `MotionScheme.expressive()`.
  `ThemeLiteralsTest` fails the build on a `Color(0x…)`, a literal
  `RoundedCornerShape` radius or a `.copy(fontSize = …)` in `features/` or
  `core/ui`.
- **`PebblesTheme` holds only what M3 has no slot for:** `.spacing` (the M3
  4 dp grid) and `.hand` (Caveat and Reenie Beanie: name input, valence word,
  soul names).
- **Error is amber**, not red — a product decision (decision log 2026-09-24).
- **Scheme choice:** wallpaper colour when the user's Settings switch is on
  (the default; `AppearancePreferences`), else the export's scheme for the
  system contrast level, live on Android 14+. `PebblesTheme(dynamicColor =
  false)` is the default so every preview renders the brand scheme — only
  `MainActivity` passes the setting.
- **material3 is pinned to `1.5.0-alpha27`** in `libs.versions.toml` for the
  Expressive API; drop the pin when 1.5.0 stable is in the BOM, and do not
  move to an alpha that drags Compose ui/foundation off the BOM's stable line.
- Motion: `rememberReduceMotion()` (`core/designsystem`) gates every custom
  animation; stock components follow the system animator scale themselves.
```

  - `values/colors.xml` bullet under "Launcher icon & splash": point at `ColorSchemes.kt` instead of `core/designsystem/Palettes.kt`, and mention `splash_icon_background`.
  - Folder layout `core/designsystem/` line: replace "PebblesTheme, Palettes, Spacing, Typography, PebblesText" with "PebblesTheme, ColorSchemes, Typography, Shapes, Spacing, PebblesHandTypography".
- [ ] Commit `docs(android): the agent guide describes the m3 theme (#853)`.

### Task 7.5: Close out

- [ ] Per-part gate, locally review screenshots (expect only the preview files Task 7.1 touched to move).
- [ ] PR `quality(android): lock the theme down and delete the token bridge`, body starts `Closes #853`, `no-lab-note`, then `rebaseline-screenshots`.
- [ ] Tick #853's acceptance criteria in the PR body with where each is proven: screenshot matrix (Parts 1–6 re-baselines), `ThemeLiteralsTest`, `ColorSchemeContrastTest`, `MaterialExpressiveTheme` root + `rememberReduceMotion` call sites, decision-log entry.
- [ ] Arkaik: the App moves statuses from the PRs; do not claim `live`.
- [ ] Append "Lessons learned" to this plan.
