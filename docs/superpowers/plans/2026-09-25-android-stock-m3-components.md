# Android stock M3 components (#854) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-rolled Android chrome with stock Material 3 (Expressive) components and fix the controls that stay custom, closing the three Android A11Y findings.

**Architecture:** One `gh stack`, seven parts, one component family per PR (spec: `docs/superpowers/specs/2026-09-25-android-stock-m3-components-design.md`). Part 1 is fully specified below. Parts 2–7 are specified at file + rule level here and get their step-level tasks appended at the start of each part, once the part below has landed — their code depends on what the lower parts leave behind.

**Tech Stack:** Kotlin, Jetpack Compose, `material3 1.5.0-alpha27` (Expressive API), JUnit4, Compose Preview Screenshot Testing.

---

## Conventions for every part

- Work from `apps/android`. Export the SDK first or Gradle silently no-ops:
  `export ANDROID_HOME=$HOME/Library/Android/sdk`
- Gate, in this order, before every push:
  ```bash
  ./gradlew ktlintCheck lint testDebugUnitTest assembleDebug
  ./gradlew validateDebugScreenshotTest   # expect failures = the previews this part moved
  ```
  `lint` must add no baseline entry. Screenshot failures are expected; list which
  previews moved in the PR body. Never commit locally rendered references — add
  the `rebaseline-screenshots` label on the PR and approve the bot's held runs.
- Before `gh stack push`/`submit`: `git fetch` and fast-forward over any
  re-baseline bot commit on the branch.
- No `Color(0x…)`, literal `RoundedCornerShape` radius or `.copy(fontSize = …)`
  in `features/` or `core/ui` (`ThemeLiteralsTest`). No `core -> features`
  import (`ArchitectureBoundaryTest`).
- Commits: `type(android): …`, lowercase, one logical change each, ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Arkaik: at the start of each part, `update_node` the acceptances/views it
  touches to `development`. PR bodies list node ids in `nodes:` and **never**
  write a finding id.

---

## Part 1 — `feat/854-custom-control-a11y`

Closes the SlideToConfirm (A11Y-03), sub-48 dp targets (A11Y-04) and fixed
heights (A11Y-04) findings. Nothing here replaces a component; it fixes the ones
that stay custom.

### File map

| File | Change |
|---|---|
| `core/designsystem/LayoutDirectionDrag.kt` (new) | Pure helpers turning a physical pointer x into a logical (start→end) one |
| `test/.../core/designsystem/LayoutDirectionDragTest.kt` (new) | JVM test of the helpers |
| `features/glyph/store/SlideToConfirm.kt` | RTL-aware drag; `onClick` semantics (button role, cost as state) that runs the same confirm path |
| `res/values{,-fr}/strings.xml` | `glyph_drawer_slide_confirm_action`, `glyph_drawer_slide_cost_a11y` |
| `features/path/valence/ValenceRoll.kt` | Polarity axis reads logical x |
| `features/path/valence/ValenceFan.kt` | `MinimumHitTarget` 44 → 48 dp |
| `features/path/components/WeekHeader.kt` | Canvas chevrons → `IconButton` + `ic_chevron_left/right` (auto-mirrored); pill `height(40)` → `heightIn(min = 48)` |
| `res/drawable/ic_chevron_left.xml` (new), `ic_chevron_right.xml`, `ic_arrow_back.xml`, `ic_arrow_right.xml` | `android:autoMirrored="true"` |
| `features/path/record/RecordFlowChrome.kt` | 44 dp back/close boxes → `IconButton` (48 dp) |
| `features/lab/components/ReactionButton.kt` | `minimumInteractiveComponentSize()` |
| `features/welcome/WelcomeCarousel.kt` | pager `height(110)` → `heightIn(min = 110)` |
| `features/path/components/WeekRoll.kt` | cell `height(96)` → `heightIn(min = 96)` |

`CheckGlyph` is not touched here: its only caller is `PebblesCheckbox`, which
Part 3 replaces with a stock `Checkbox`, deleting both.

### Task 1.1: Layout-direction drag helpers

**Files:**
- Create: `app/src/main/kotlin/app/pbbls/android/core/designsystem/LayoutDirectionDrag.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/core/designsystem/LayoutDirectionDragTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Physical pointer x → logical (start-to-end) x, so drag math is written once for LTR and RTL. */
class LayoutDirectionDragTest {
    @Test
    fun `a delta keeps its sign in LTR and flips in RTL`() {
        assertEquals(12f, LayoutDirection.Ltr.logicalDelta(12f))
        assertEquals(-12f, LayoutDirection.Rtl.logicalDelta(12f))
    }

    @Test
    fun `distance from the start edge is x in LTR and width minus x in RTL`() {
        assertEquals(20f, LayoutDirection.Ltr.distanceFromStart(x = 20f, width = 300f))
        assertEquals(280f, LayoutDirection.Rtl.distanceFromStart(x = 20f, width = 300f))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew testDebugUnitTest --tests '*LayoutDirectionDragTest'`
Expected: compilation failure, `Unresolved reference: logicalDelta`.

- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.LayoutDirection

/*
 * Pointer positions are physical (x grows to the right in every locale), but
 * RTL-aware layout — `Modifier.offset`, `Alignment.CenterStart` — is logical.
 * Drag code converts at the edge with these and then only ever reasons in
 * logical units, so the same arithmetic moves toward the end edge in both
 * directions.
 */

/** A horizontal pointer delta, in the direction the layout reads. */
fun LayoutDirection.logicalDelta(physicalDx: Float): Float = if (this == LayoutDirection.Rtl) -physicalDx else physicalDx

/** How far [x] (physical, within a box of [width]) sits from the box's start edge. */
fun LayoutDirection.distanceFromStart(
    x: Float,
    width: Float,
): Float = if (this == LayoutDirection.Rtl) width - x else x
```

- [ ] **Step 4: Run it and watch it pass**

Run: `./gradlew testDebugUnitTest --tests '*LayoutDirectionDragTest'`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/core/designsystem/LayoutDirectionDrag.kt app/src/test/kotlin/app/pbbls/android/core/designsystem/LayoutDirectionDragTest.kt
git commit -m "feat(android): layout-direction helpers for drag math"
```

### Task 1.2: SlideToConfirm — TalkBack confirms, RTL slides toward the end

Mirrors iOS `SlideToConfirm.swift:66-70` (button trait, cost as value, an
activate action that confirms).

**Files:**
- Modify: `app/src/main/kotlin/app/pbbls/android/features/glyph/store/SlideToConfirm.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`

- [ ] **Step 1: Strings.** After `glyph_drawer_slide_a11y` in `values/strings.xml`:

```xml
    <string name="glyph_drawer_slide_confirm_action">confirm the swap</string>
    <string name="glyph_drawer_slide_cost_a11y">%1$d karma</string>
```

and in `values-fr/strings.xml`:

```xml
    <string name="glyph_drawer_slide_confirm_action">confirmer l\'échange</string>
    <string name="glyph_drawer_slide_cost_a11y">%1$d karma</string>
```

- [ ] **Step 2: One confirm path for drag and accessibility.** In `SlideToConfirm`,
read the layout direction and hoist the confirm sequence out of the gesture:

```kotlin
    val layoutDirection = LocalLayoutDirection.current
    val a11y = stringResource(R.string.glyph_drawer_slide_a11y)
    val confirmAction = stringResource(R.string.glyph_drawer_slide_confirm_action)
    val costA11y = stringResource(R.string.glyph_drawer_slide_cost_a11y, cost)

    // The drag and TalkBack's double-tap end in the same place: park the
    // thumb, run the purchase, spring back if it failed. The haptic fires
    // before the RPC — verbatim iOS quirk.
    fun confirm(travel: Float) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            dragX.animateTo(travel)
            val success = onConfirm()
            if (!success) dragX.animateTo(0f)
        }
    }
```

- [ ] **Step 3: Replace the semantics and the gesture.** Replace the
`.clearAndSetSemantics { … }` and `.pointerInput(…) { … }` lines with:

```kotlin
                .clearAndSetSemantics {
                    contentDescription = a11y
                    stateDescription = costA11y
                    role = Role.Button
                    if (enabled) {
                        onClick(label = confirmAction) {
                            confirm(SlideMath.travel(trackWidthPx.toFloat(), thumbPx))
                            true
                        }
                    } else {
                        disabled()
                    }
                }.pointerInput(enabled, trackWidthPx, layoutDirection) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // The press must start on the resting thumb, which sits at the start edge.
                        if (layoutDirection.distanceFromStart(down.position.x, trackWidthPx.toFloat()) > thumbPx) {
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val travel = SlideMath.travel(trackWidthPx.toFloat(), thumbPx)
                        drag(down.id) { change ->
                            val delta = layoutDirection.logicalDelta(change.position.x - change.previousPosition.x)
                            val next = (dragX.value + delta).coerceIn(0f, travel)
                            scope.launch { dragX.snapTo(next) }
                            change.consume()
                        }
                        if (SlideMath.isConfirmed(SlideMath.progress(dragX.value, travel))) {
                            confirm(travel)
                        } else {
                            scope.launch { dragX.animateTo(0f) }
                        }
                    }
                },
```

`dragX` is now logical; the thumb's `Modifier.offset { IntOffset(dragX…, 0) }`
and the trail's `CenterStart` alignment are already RTL-aware, so they need no
change. Add imports: `androidx.compose.ui.platform.LocalLayoutDirection`,
`androidx.compose.ui.semantics.{Role, disabled, onClick, role, stateDescription}`,
`app.pbbls.android.core.designsystem.{distanceFromStart, logicalDelta}`.
Update the KDoc's last sentence to say TalkBack's double-tap confirms directly.

- [ ] **Step 4: Build and test**

Run: `./gradlew ktlintCheck testDebugUnitTest --tests '*SlideMathTest' --tests '*LocalizationParityTest' assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/features/glyph/store/SlideToConfirm.kt app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "fix(android): talkback can confirm a glyph swap, and the slide follows rtl"
```

### Task 1.3: ValenceRoll — polarity follows the reading direction

**Files:** Modify `app/src/main/kotlin/app/pbbls/android/features/path/valence/ValenceRoll.kt`

The neighbour words are placed with RTL-aware `Modifier.offset(x = ±PolarityStep)`
and the row with RTL-aware `offset { }`, so the layout mirrors on its own; only
the finger's x is physical.

- [ ] **Step 1:** In `ValenceRoll`, add `val layoutDirection = LocalLayoutDirection.current`
beside `current`/`change`, key the gesture on it (`.pointerInput(layoutDirection) {`),
and change the polarity amount line to:

```kotlin
                            val amount =
                                if (locked == RollAxis.POLARITY) layoutDirection.logicalDelta(travel.x) else travel.y
```

Add a comment above it: `// Logical x: in RTL the values after this one sit to the left.`
Imports: `androidx.compose.ui.platform.LocalLayoutDirection`,
`app.pbbls.android.core.designsystem.logicalDelta`.

- [ ] **Step 2:** `./gradlew ktlintCheck assembleDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit** — `fix(android): the valence roll follows the reading direction`

### Task 1.4: Mirrored icons and the week chevrons

**Files:**
- Create: `app/src/main/res/drawable/ic_chevron_left.xml`
- Modify: `ic_chevron_right.xml`, `ic_arrow_back.xml`, `ic_arrow_right.xml`
- Modify: `app/src/main/kotlin/app/pbbls/android/features/path/components/WeekHeader.kt`

- [ ] **Step 1:** Add `android:autoMirrored="true"` to the `<vector>` of
`ic_chevron_right.xml`, `ic_arrow_back.xml` and `ic_arrow_right.xml` (all
direction-bearing: back, forward, trailing disclosure). Create
`ic_chevron_left.xml`:

```xml
<!--
  Leading chevron (previous week, WeekHeader). Standard Material path data;
  tinted at the call site by Icon, so fillColor is a placeholder. Auto-mirrored:
  "previous" points to the start edge in RTL.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#000000"
        android:pathData="M15.41,16.59L10.83,12l4.58,-4.59L14,6l-6,6 6,6 1.41,-1.41z" />
</vector>
```

- [ ] **Step 2:** In `WeekHeader.kt`, change the pill's `.height(40.dp)` to
`.heightIn(min = 48.dp)` and its `.padding(horizontal = 16.dp)` to
`.padding(horizontal = 4.dp)` (the `IconButton` carries its own inset). Replace
`ChevronButton` and delete `ChevronGlyph`:

```kotlin
@Composable
private fun ChevronButton(
    pointsLeft: Boolean,
    targetWeekStart: LocalDate?,
    a11yLabel: String,
    onFocusChange: (LocalDate) -> Unit,
) {
    IconButton(
        onClick = { targetWeekStart?.let(onFocusChange) },
        enabled = targetWeekStart != null,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            painter = painterResource(if (pointsLeft) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right),
            contentDescription = a11yLabel,
        )
    }
}
```

The disabled chevron now takes M3's disabled content colour (onSurface at 38%)
instead of `alpha(0.3f)`. Remove the now-unused imports (`Canvas`, `clickable`,
`size`, `height`, `alpha`, `Color`, `Path`, `StrokeCap`, `StrokeJoin`, `Stroke`,
`semantics`, `contentDescription`); add `heightIn`, `Icon`, `IconButton`,
`IconButtonDefaults`, `painterResource`. Fix the KDoc: no more hand-drawn chevron.

- [ ] **Step 3:** `./gradlew ktlintCheck lint assembleDebug` → `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit** — `fix(android): week chevrons are 48 dp icon buttons, and direction icons mirror`

### Task 1.5: 48 dp floors on the remaining custom targets

**Files:** `RecordFlowChrome.kt`, `ReactionButton.kt`, `ValenceFan.kt`

- [ ] **Step 1: RecordFlowChrome.** Replace both 44 dp boxes with `IconButton`s
(48 dp, M3 ripple). The back button keeps its layout slot at zero alpha so the
dots do not shift:

```kotlin
        IconButton(
            onClick = onBack,
            enabled = canGoBack,
            modifier = Modifier.alpha(if (canGoBack) 1f else 0f),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = if (canGoBack) stringResource(R.string.record_back_a11y) else null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
```

and

```kotlin
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.ic_x_circle),
                contentDescription = stringResource(R.string.action_close),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
```

Drop the unused `clickable`/`clip`/`Box` imports if nothing else uses them.

- [ ] **Step 2: ReactionButton.** Prepend the floor to the row's modifier chain:
`modifier.minimumInteractiveComponentSize().clip(CircleShape)…` (import
`androidx.compose.material3.minimumInteractiveComponentSize`).

- [ ] **Step 3: ValenceFan.** `private val MinimumHitTarget = 48.dp` and update
its comment to say 48 dp is the Android floor (iOS uses 44 pt).

- [ ] **Step 4:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`
(`ValenceFanLayoutTest` must still pass).

- [ ] **Step 5: Commit** — `fix(android): no custom target under 48 dp on the record flow, lab and valence fan`

### Task 1.6: Heights that grow with the text

**Files:** `WelcomeCarousel.kt`, `WeekRoll.kt`

- [ ] **Step 1:** `WelcomeCarousel`: pager `.height(110.dp)` → `.heightIn(min = 110.dp)`.
`WeekRoll`: cell `.height(96.dp)` → `.heightIn(min = 96.dp)`. Swap the `height`
import for `heightIn` where it becomes unused.

- [ ] **Step 2:** `./gradlew ktlintCheck assembleDebug validateDebugScreenshotTest`
Expected: only previews that render the welcome carousel, the Path header/roll,
the record-flow chrome, the valence fan, the store drawer and Lab rows differ.
Open the `fs2` renders of Welcome and Path under
`app/build/outputs/screenshotTest-results/` and confirm no clipped text.

- [ ] **Step 3: Commit** — `fix(android): welcome and week-roll heights grow at large font scales`

### Task 1.7: Device verification

- [ ] **Step 1:** `./gradlew installDebug` on the `oxymore-eclipse` AVD. With
TalkBack on: open the store, pick an affordable glyph, double-tap the slide
control → it announces "Slide to confirm swap, N karma, button, double tap to
confirm the swap" and the swap completes. Week chevrons announce and step.
- [ ] **Step 2:** Accessibility Scanner (or `uiautomator dump` + bounds check) on
Path, record flow, store: no touch-target finding for the controls above.
Record any remaining sub-48 dp control and which later part owns it.
- [ ] **Step 3:** Developer options → pseudo-locale `ar-XB` (or `adb shell
settings put global debug.force_rtl 1`): the slide thumb starts at the right
and slides left to confirm; the valence roll brings the value on the left to
centre when dragged right; chevrons and back arrows point the mirrored way.
- [ ] **Step 4:** Full gate (Conventions), push, open the PR (Lab Note,
`feat`/`ui`/`android`, M61, `Part of #854`), add `rebaseline-screenshots`.

---

## Parts 2–7 (task lists appended when each part starts)

### Part 2 — `feat/854-buttons`
- `core/designsystem/PebblesPrimaryButton.kt`: keep the name as the one thin
  wrapper (full width + `isLoading`), body becomes `Button(shapes = ButtonDefaults.shapes())`;
  `heightIn(min = ButtonDefaults.MediumContainerHeight)` if the 52 dp look is
  wanted. Callers: `AuthScreen`, `WelcomeScreen`, `OnboardingScreen`,
  `RecordStepScaffold`, `RecordSuccessStep`, `AchievementMomentOverlay`,
  `FeaturedCommunityCard`, `NewPebbleButton`.
- `WelcomeOutlineButton` (in `WelcomeScreen.kt`) → `OutlinedButton` inline.
- `core/designsystem/GoogleSignInButton.kt` → `OutlinedButton` with brand
  content colours; keep the file only if the logo + label layout repeats.
- `features/path/components/NewPebbleButton.kt` → `FilledTonalButton`.
- Append the decision-log entry: Android chrome is stock M3, extending the #853
  divergence.

### Part 3 — `feat/854-selection`
- `PebblesCheckbox` + `CheckGlyph` deleted; consent rows become
  `Row(Modifier.toggleable(value, role = Role.Checkbox, onValueChange))` with a
  `Checkbox(checked, onCheckedChange = null)`.
- `PebblesAuthSwitcher` → connected `ButtonGroup` of `ToggleButton`s,
  `selectableGroup()`, each `Role.RadioButton`.
- `GlyphTabBar` → `HorizontalFloatingToolbar` of three `ToggleButton`s (icon +
  label), `selectableGroup()`. Callers: `GlyphsListScreen`, `GlyphPickerSheet`.
- `EmotionChip` (`EmotionPickerSheet.kt`) → `FilterChip(selected)`.
- Privacy / collection / domain rows (`RecordPrivacyStep`, `RecordCollectionStep`,
  `DomainPickerContent`, their `PebbleForm` twins) → `RadioButton` or `Checkbox`
  rows under `selectableGroup()` per single/multi select.

### Part 4 — `feat/854-text-fields`
- `core/designsystem/PebblesTextInput.kt` → `OutlinedTextField` at call sites
  (5 files); `isError` + `supportingText` where a message exists.
- Raw `BasicTextField` rows (Settings and the other 5 files) → `OutlinedTextField`;
  hand-lettered inputs pass `PebblesTheme.hand.*` as `textStyle`.

### Part 5 — `feat/854-lists-cards`
- `PebblesList.kt`, `ProfileCard.kt`, `SurfaceTile.kt` → `OutlinedCard` +
  `ListItem` + `HorizontalDivider`; callers in `features/profile`,
  `features/glyph/store/GlyphDetailDrawer.kt`, `features/path/read/PebbleReadView.kt`.

### Part 6 — `feat/854-top-bars`
- `PebblesScreen` → `Scaffold(topBar, contentWindowInsets = …)`; 20 screens
  move `PebblesTopBar` → `CenterAlignedTopAppBar`; Profile →
  `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior`.
- `SheetToolbar` (`EmotionPickerSheet`, `SoulPickerSheet`, `ValencePickerSheet`) → `CenterAlignedTopAppBar`.
- `RecordFlowChrome` → top app bar; `ProgressDots` → `LinearWavyProgressIndicator`
  with the same "Step n of m" semantics.
- Inset rule: the outer four-tab `NavigationBar` scaffold consumes the bottom
  inset; inner scaffolds must not add it again.

### Part 7 — `feat/854-loading`
- Every `CircularProgressIndicator` (27 files) → `LoadingIndicator`; the
  in-button spinner stays sized to the label line.

---

## Lessons learned

(Filled in after the stack opens.)
