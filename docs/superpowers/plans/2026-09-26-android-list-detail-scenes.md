# Android list-detail Scenes — Implementation Plan (#940)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On large screens, souls, collections, pebbles and glyphs open beside their list (co-planar panes); on phones souls and collections stay pushes and pebbles and glyphs open as docked sheets.

**Architecture:** `RootScreen`'s `NavDisplay` gets a chain of Navigation 3 Scene strategies: a Path-aware wrapper around `ListDetailSceneStrategy` (adaptive-navigation3) fed a posture-aware `PaneScaffoldDirective`, then a `BottomSheetSceneStrategy` (Nav3 recipe). Entries declare their role through metadata in `PebblesEntryProvider`; screens learn they are in a pane only through a `showBack` parameter the entry provider derives from `LocalListDetailSceneScope`. Four stacked PRs: stateless content split → souls/collections → pebble → glyph.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 (1.5.0-alpha27, Expressive), `androidx.compose.material3.adaptive` 1.3.0 (+ `adaptive-navigation3`), Navigation 3 1.2.0, Hilt, JUnit4 + kotlinx-coroutines-test, Compose Preview Screenshot Testing.

**Spec:** `docs/superpowers/specs/2026-09-26-android-list-detail-scenes-design.md`. Read it first.

---

## Ground rules for every task

- Paths below are relative to the repo root. `APP=apps/android/app/src/main/kotlin/app/pbbls/android`, `TEST=apps/android/app/src/test/kotlin/app/pbbls/android`, `SHOT=apps/android/app/src/screenshotTest/kotlin/app/pbbls/android`.
- Before any Gradle command: `export ANDROID_HOME=$HOME/Library/Android/sdk` (otherwise the npm wrapper silently no-ops). Run Gradle from `apps/android`.
- Commands, from `apps/android`:
  - ktlint: `./gradlew ktlintCheck` (auto-fix: `./gradlew ktlintFormat`)
  - unit tests: `./gradlew testDebugUnitTest` (one class: `--tests 'app.pbbls.android.navigation.NavigatorTest'`)
  - Android Lint gate: `./gradlew lint`
  - build: `./gradlew assembleDebug`
  - screenshots (look, never commit): `./gradlew validateDebugScreenshotTest`, `./gradlew updateDebugScreenshotTest`
- **Never commit reference PNGs rendered locally** (`app/src/screenshotTestDebug/reference/`). CI re-renders them via the `rebaseline-screenshots` PR label. If `updateDebugScreenshotTest` wrote PNGs, `git checkout -- app/src/screenshotTestDebug/reference/` and `git clean -fd app/src/screenshotTestDebug/reference/` before committing.
- Read `apps/android/CLAUDE.md` once. Key rules: `@Composable` functions PascalCase; no `Color(0x…)`, literal `RoundedCornerShape` radii or `.copy(fontSize=…)` in `features/` or `core/ui` (`ThemeLiteralsTest`); `core` never imports `features`, a feature never imports another feature (`ArchitectureBoundaryTest`); every user-facing string in both `res/values/strings.xml` and `res/values-fr/strings.xml` (`LocalizationParityTest`); log errors with `android.util.Log`, never swallow.
- Commits: conventional, lowercase, no period, ending with the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Comments explain intent and reasoning, matching the density of the surrounding file. Do not add comments that restate the code.

---

# Part 1 — stateless content layers

Branch: `quality/940-stateless-detail-content` (already exists, holds the spec commit). A **pure split**: every screen becomes VM wiring around a public `XContent` composable taking its `UiState`. No behavior change, no string change, no reference PNG moves.

The shape to copy is `PathScreen` / `PathContent` in `$APP/features/path/PathScreen.kt`: the stateful screen collects state, runs effects, owns dialogs and covers, and calls the stateless content; the content takes plain values and lambdas and reads **no** service CompositionLocal (palettes come in as a `paletteFor` lambda, the same as `PathContent`).

### Task 1: `SoulsListContent`

**Files:**
- Modify: `$APP/features/profile/SoulsListScreen.kt`

- [ ] **Step 1: Split the screen.** Add this public composable below `SoulsListScreen` and move the whole `PebblesScreen(...) { … }` call into it, replacing `viewModel::retry` with `onRetry` and `viewModel.requestDelete(soul)` with `onDeleteSoul(soul)`. Nothing else in the moved block changes.

```kotlin
/**
 * The souls grid without its ViewModel (#940): the top bar, the three states
 * and the grid. [SoulsListScreen] wires it; screenshots drive it directly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoulsListContent(
    uiState: SoulsListUiState,
    onBack: () -> Unit,
    onOpenSoul: (SoulWithGlyph) -> Unit,
    onCreateSoul: () -> Unit,
    onRetry: () -> Unit,
    onDeleteSoul: (SoulWithGlyph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PebblesScreen(
        modifier = modifier,
        // … the moved block, verbatim apart from the two substitutions above …
    )
}
```

`SoulsListScreen` keeps its signature, its `uiState`/`covers` collection, the `LifecycleResumeEffect`, and the two dialogs, and its body becomes:

```kotlin
    SoulsListContent(
        uiState = uiState,
        onBack = onBack,
        onOpenSoul = onOpenSoul,
        onCreateSoul = onCreateSoul,
        onRetry = viewModel::retry,
        onDeleteSoul = viewModel::requestDelete,
        modifier = modifier,
    )
```

followed by the unchanged `covers.pendingDeletion?.let { … }` and `if (covers.didDeleteFail) …` lines. Delete the now-unused `val colors` from the screen if nothing else in it reads it.

- [ ] **Step 2: Compile and format.** Run `./gradlew ktlintFormat ktlintCheck assembleDebug`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulsListScreen.kt
git commit -m "quality(android): souls list renders through a stateless content layer"
```

### Task 2: `CollectionsListContent`

**Files:**
- Modify: `$APP/features/profile/CollectionsListScreen.kt`

- [ ] **Step 1: Split the screen** exactly as Task 1, with this signature. In the moved block substitute `viewModel::retry` → `onRetry`, `viewModel::refresh` → `onRefresh`, `viewModel.requestDelete(collection)` → `onDeleteCollection(collection)`.

```kotlin
/**
 * The collections list without its ViewModel (#940): top bar, states and the
 * pull-to-refresh list. [CollectionsListScreen] wires it; screenshots drive it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionsListContent(
    uiState: CollectionsListUiState,
    onBack: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onCreateCollection: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteCollection: (Collection) -> Unit,
    modifier: Modifier = Modifier,
)
```

(`Collection` is `app.pbbls.android.core.model.Collection` — the file already imports it; do not confuse it with `kotlin.collections.Collection`.) Check the existing `@OptIn` annotations on `CollectionsListScreen` and carry every one the moved block needs (`PullToRefreshBox` may need `ExperimentalMaterial3Api`).

The screen's body becomes a `CollectionsListContent(…)` call with `onRetry = viewModel::retry`, `onRefresh = viewModel::refresh`, `onDeleteCollection = viewModel::requestDelete`, plus its unchanged dialogs.

- [ ] **Step 2:** `./gradlew ktlintFormat ktlintCheck assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `quality(android): collections list renders through a stateless content layer`.

### Task 3: `SoulDetailContent`

**Files:**
- Modify: `$APP/features/profile/SoulDetailScreen.kt`

- [ ] **Step 1: Split the screen.** Move the `PebblesScreen(...) { … }` call into this composable. Substitutions in the moved block: `viewModel::retry` → `onRetry`; `viewModel.openPebble(pebble.id)` → `onOpenPebble(pebble.id)`; `viewModel.requestDelete(pebble)` → `onDeletePebble(pebble)`; `pebble.emotion?.let { palettes.palette(it.id) }` → `paletteFor(pebble)`.

```kotlin
/**
 * One soul without its ViewModel (#940): top bar, header and tagged pebbles.
 * [SoulDetailScreen] wires it and owns the edit cover and delete dialogs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoulDetailContent(
    uiState: SoulDetailUiState,
    onBack: () -> Unit,
    onEditSoul: () -> Unit,
    onRetry: () -> Unit,
    onOpenPebble: (String) -> Unit,
    onDeletePebble: (Pebble) -> Unit,
    paletteFor: (Pebble) -> EmotionPalette?,
    modifier: Modifier = Modifier,
)
```

`SoulDetailScreen` keeps `LaunchedEffect(soulId)`, `LifecycleResumeEffect`, the `EditPebbleScreen` cover and both dialogs; it reads `LocalEmotionPaletteService.current` and passes `paletteFor = { pebble -> pebble.emotion?.let { palettes.palette(it.id) } }`, `onOpenPebble = viewModel::openPebble`, `onDeletePebble = viewModel::requestDelete`, `onRetry = viewModel::retry`. `SoulHeader` stays private in the file. Import `app.pbbls.android.core.model.EmotionPalette` (check the real package with `grep -rn "class EmotionPalette" $APP/core/model`).

- [ ] **Step 2:** `./gradlew ktlintFormat ktlintCheck assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `quality(android): soul detail renders through a stateless content layer`.

### Task 4: `CollectionDetailContent`

**Files:**
- Modify: `$APP/features/profile/CollectionDetailScreen.kt`

- [ ] **Step 1: Split the screen** as Task 3, same substitutions, with:

```kotlin
/**
 * One collection without its ViewModel (#940): top bar and the month-grouped
 * pebbles. [CollectionDetailScreen] wires it and owns the covers and dialogs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailContent(
    uiState: CollectionDetailUiState,
    onBack: () -> Unit,
    onEditCollection: () -> Unit,
    onRetry: () -> Unit,
    onOpenPebble: (String) -> Unit,
    onDeletePebble: (Pebble) -> Unit,
    paletteFor: (Pebble) -> EmotionPalette?,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2:** `./gradlew ktlintFormat ktlintCheck assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `quality(android): collection detail renders through a stateless content layer`.

### Task 5: `PebbleDetailContent`

**Files:**
- Modify: `$APP/features/path/PebbleDetailScreen.kt`

- [ ] **Step 1: Split the screen.** The screen keeps: `uiState` collection, `LaunchedEffect(pebbleId)`, `LifecycleResumeEffect`, the `palettes` read, the `context` and the `onShare` computation. Everything from `Column(modifier…` to the end of the function moves into:

```kotlin
/**
 * One pebble without its ViewModel (#940): the tinted page, the top bar and
 * the three states. [PebbleDetailScreen] wires it; screenshots drive it.
 *
 * [palette] is the pebble's emotion palette once loaded, or null (loading, or
 * a palette-cache miss), in which case the page stays on `surface`.
 */
@Composable
fun PebbleDetailContent(
    uiState: PebbleDetailUiState,
    palette: EmotionPalette?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Inside, `detail` becomes `(uiState as? PebbleDetailUiState.Content)?.detail`; `pageBackground` becomes `palette?.let { pebblePageColors(it, isSystemInDarkTheme()).background } ?: MaterialTheme.colorScheme.surface`; the `PebbleReadView` call takes `palette = palette`; `viewModel::retry` → `onRetry`; `onDismiss` → `onBack`; `onEditRequested` → `onEdit`. The pointer-swallowing `pointerInput` and `safeDrawingPadding()` **stay** in this part (Part 3 removes them).

The screen then computes `val palette = detail?.let { palettes.palette(it.emotion.id) }` and calls `PebbleDetailContent(uiState, palette, onDismiss, onEditRequested, onShare, viewModel::retry, modifier)`. Note `PebbleReadView` previously looked up the palette with `state.detail.emotion.id`, which is the same id; passing the one computed value is equivalent. If `PebbleReadView`'s `palette` parameter is non-null, keep the screen's previous lookup semantics by passing `palette` only when non-null and otherwise the same fallback the old call used (read `PebbleReadView`'s signature first).

- [ ] **Step 2:** `./gradlew ktlintFormat ktlintCheck assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `quality(android): pebble detail renders through a stateless content layer`.

### Task 6: Verify and open Part 1

- [ ] **Step 1: Full gate.** `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug validateDebugScreenshotTest`. Expected: all green, and the screenshot validation reports **no** changed previews (a pure split must not move a pixel). If a preview moved, the split changed behavior — fix it, don't re-baseline.
- [ ] **Step 2: Start the stack and push.** `gh stack init quality/940-stateless-detail-content` then `gh stack submit`. PR title `quality(android): detail and list screens render through stateless content layers`. Body starts `Part of #940` (not `Resolves` — Part 4 closes it), lists the five files, says "pure split, no reference PNG moves", no Lab Note (label `no-lab-note`). Labels/milestone: propose the issue's (`ui`, `android`, milestone `M61 · Android Refacto`) with species `quality` instead of `feat` — **confirm with the user before creating the PR.**

---

# Part 2 — list-detail for souls and collections

Branch: `feat/940-list-detail-souls-collections`, created on top of Part 1 with `gh stack add feat/940-list-detail-souls-collections`.

Before the first code task, move the affected view nodes on the hosted Arkaik map to `development` (`list_nodes` on the pbbls map, find the People/Soul detail/Collections/Collection detail views, `update_node` their Android status). If the `arkaik-mcp` tools are unavailable, **stop and tell the user** — never edit `docs/arkaik/bundle.json`.

### Task 7: Add `adaptive-navigation3`

**Files:**
- Modify: `apps/android/gradle/libs.versions.toml` (the comment block above `androidx-navigation3-runtime`, around line 178)
- Modify: `apps/android/app/build.gradle.kts` (next to `implementation(libs.androidx.compose.material3.adaptive)`, around line 225)

- [ ] **Step 1: Catalog.** Replace the "material3-adaptive-navigation3 is deliberately NOT here" comment with:

```toml
# Navigation 3 (#852). material3-adaptive-navigation3 supplies the list-detail
# Scene (#940). It ships in lockstep with material3-adaptive, so it rides the
# same ref, and its own navigation3-ui floor (1.0.0) resolves up to ours.
androidx-compose-material3-adaptive-navigation3 = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation3", version.ref = "material3Adaptive" }
```

- [ ] **Step 2: Dependency.** In `app/build.gradle.kts`, after the adaptive line:

```kotlin
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
```

- [ ] **Step 3: Check resolution didn't move material3 or Compose.** Run `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E "material3:material3:|compose.ui:ui:|navigation3-ui"`. Expected: `material3` still `1.5.0-alpha27`, `navigation3-ui` `1.2.0`, Compose `ui` unchanged from `main`. If `adaptive-navigation3` drags any of them, stop and report.
- [ ] **Step 4:** `./gradlew assembleDebug lint` → green (a new dependency with no call site is fine for lint).
- [ ] **Step 5: Commit** — `chore(android): add material3 adaptive-navigation3 for list-detail scenes`.

### Task 8: Posture-aware pane directive

**Files:**
- Create: `$APP/navigation/PebblesPaneDirective.kt`
- Test: `$TEST/navigation/PebblesPaneDirectiveTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the window gets two panes (#940). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PebblesPaneDirectiveTest {
    private val bookHinge =
        HingeInfo(
            bounds = Rect(left = 419f, top = 0f, right = 421f, bottom = 900f),
            isFlat = false,
            isVertical = true,
            isSeparating = true,
            isOccluding = false,
        )

    private fun info(
        minWidthDp: Int,
        hinges: List<HingeInfo> = emptyList(),
        isTabletop: Boolean = false,
    ) = WindowAdaptiveInfo(
        windowSizeClass = WindowSizeClass(minWidthDp = minWidthDp, minHeightDp = 480),
        windowPosture = Posture(isTabletop = isTabletop, hingeList = hinges),
    )

    @Test
    fun `a phone gets one pane`() {
        assertEquals(1, pebblesPaneDirective(info(minWidthDp = 0)).maxHorizontalPartitions)
    }

    @Test
    fun `a flat medium window gets one pane, as M3 recommends`() {
        assertEquals(1, pebblesPaneDirective(info(minWidthDp = 600)).maxHorizontalPartitions)
    }

    @Test
    fun `an expanded window gets two panes`() {
        assertEquals(2, pebblesPaneDirective(info(minWidthDp = 840)).maxHorizontalPartitions)
    }

    @Test
    fun `book posture splits a medium window at the hinge`() {
        val directive = pebblesPaneDirective(info(minWidthDp = 600, hinges = listOf(bookHinge)))

        assertEquals(2, directive.maxHorizontalPartitions)
        assertEquals(listOf(bookHinge.bounds), directive.excludedBounds)
    }

    @Test
    fun `a flat unfolded hinge does not force a split`() {
        val flat =
            HingeInfo(
                bounds = bookHinge.bounds,
                isFlat = true,
                isVertical = true,
                isSeparating = false,
                isOccluding = false,
            )
        assertEquals(1, pebblesPaneDirective(info(minWidthDp = 600, hinges = listOf(flat))).maxHorizontalPartitions)
    }

    @Test
    fun `tabletop posture does not force a side-by-side split`() {
        val tabletopHinge =
            HingeInfo(
                bounds = Rect(left = 0f, top = 449f, right = 700f, bottom = 451f),
                isFlat = false,
                isVertical = false,
                isSeparating = true,
                isOccluding = false,
            )
        val directive =
            pebblesPaneDirective(info(minWidthDp = 600, hinges = listOf(tabletopHinge), isTabletop = true))
        assertEquals(1, directive.maxHorizontalPartitions)
    }
}
```

- [ ] **Step 2: Run it, confirm it fails.** `./gradlew testDebugUnitTest --tests 'app.pbbls.android.navigation.PebblesPaneDirectiveTest'`. Expected: compilation failure, `pebblesPaneDirective` unresolved.

- [ ] **Step 3: Implement.**

```kotlin
package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.unit.dp

/**
 * How many panes the window gets (#940): M3's recommendation, plus one rule
 * for foldables.
 *
 * `calculatePaneScaffoldDirective` gives Compact and Medium windows a single
 * pane whatever the posture. A foldable in book posture is usually Medium, so
 * the default would lay one pane straight across the fold. A separating
 * vertical hinge therefore raises the count to two; the hinge itself is
 * already in `excludedBounds` (the default `HingePolicy.AvoidSeparating`), so
 * the scaffold puts one pane on each side of it.
 *
 * Tabletop is deliberately left alone: its hinge is horizontal and list-detail
 * splits side by side, so a second partition would not move anything off it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun pebblesPaneDirective(info: WindowAdaptiveInfo): PaneScaffoldDirective {
    val recommended = calculatePaneScaffoldDirective(info)
    val isBookPosture = info.windowPosture.hingeList.any { it.isVertical && it.isSeparating }
    return if (isBookPosture && recommended.maxHorizontalPartitions < 2) {
        recommended.copy(maxHorizontalPartitions = 2, horizontalPartitionSpacerSize = 24.dp)
    } else {
        recommended
    }
}
```

If `copy(…)` reports an overload ambiguity, pass `excludedBounds = recommended.excludedBounds` as well, which selects the non-deprecated overload.

- [ ] **Step 4: Run the test, confirm it passes.** Same command. Expected: 6 tests PASS. If `a phone gets one pane` fails because `WindowSizeClass(0, 480)` is rejected, use `minWidthDp = 1`.
- [ ] **Step 5: Commit** — `feat(android): panes split at a foldable's hinge in book posture`.

### Task 9: `Navigator.navigateToDetail`

Beside a list pane, the list stays tappable. Pushing a second `SoulDetail` on top of the first would make back walk through every soul looked at. On a phone the detail covers the list, so the replace branch cannot fire there.

**Files:**
- Modify: `$APP/navigation/Navigator.kt`
- Test: `$TEST/navigation/NavigatorTest.kt`

- [ ] **Step 1: Write the failing tests** (append to `NavigatorTest`):

```kotlin
    @Test
    fun `opening a detail over a detail of the same kind replaces it`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))
        navigator.navigateToDetail(PebblesKey.SoulDetail("s2"))

        assertEquals(
            listOf<NavKey>(PebblesKey.People, PebblesKey.SoulDetail("s2")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
    }

    @Test
    fun `opening a detail over a different key pushes`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))

        assertEquals(
            listOf<NavKey>(PebblesKey.People, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
    }

    @Test
    fun `a detail never replaces a tab root`() {
        navigator.navigateToDetail(PebblesKey.SoulDetail("s1"))

        assertEquals(
            listOf<NavKey>(PebblesKey.Path, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.Path]!!.toList(),
        )
    }
```

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests 'app.pbbls.android.navigation.NavigatorTest'` → FAIL (unresolved `navigateToDetail`).
- [ ] **Step 3: Implement** in `Navigator`, after `navigate`:

```kotlin
    /**
     * Opens a detail, replacing the one on top if it is the same kind (#940).
     *
     * Beside a list pane the list stays tappable, and pushing each pick would
     * make back walk through every soul looked at. On a phone the detail covers
     * its list, so only the push branch is reachable there. A tab root is
     * never replaced, whatever its type.
     */
    fun navigateToDetail(key: PebblesKey) {
        val stack = state.currentStack
        if (stack.size > 1 && stack.last()::class == key::class) {
            stack[stack.lastIndex] = key
        } else {
            navigate(key)
        }
    }
```

- [ ] **Step 4:** Same command → PASS (all NavigatorTest tests).
- [ ] **Step 5: Commit** — `feat(android): picking another item beside a list swaps the detail`.

### Task 10: Scene strategy chain and pane pairs

**Files:**
- Create: `$APP/navigation/PebblesSceneStrategies.kt`
- Test: `$TEST/navigation/PebblesSceneStrategiesTest.kt`

- [ ] **Step 1: Write the failing test.** It drives the real `ListDetailSceneStrategy` on the JVM with a fixed directive, through `SceneStrategyScope()`.

```kotlin
package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Which back stacks become a list-detail scene (#940). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PebblesSceneStrategiesTest {
    private fun directive(partitions: Int) =
        PaneScaffoldDirective.Default.copy(
            maxHorizontalPartitions = partitions,
            horizontalPartitionSpacerSize = if (partitions > 1) 24.dp else 0.dp,
        )

    private fun entry(key: PebblesKey) = NavEntry<NavKey>(key, metadata = PanePairs.metadataFor(key)) {}

    private fun SceneStrategy<NavKey>.sceneFor(vararg keys: PebblesKey) =
        with(SceneStrategyScope<NavKey>()) { calculateScene(keys.map(::entry)) }

    @Test
    fun `two panes put a soul beside the souls list`() {
        val scene = pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.People, PebblesKey.SoulDetail("s1"))

        assertNotNull(scene)
        assertEquals(2, scene!!.entries.size)
    }

    @Test
    fun `two panes show an idle souls list with its placeholder`() {
        assertNotNull(pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.People))
    }

    @Test
    fun `one pane leaves the push to the default scene`() {
        assertNull(pebblesListDetailStrategy(directive(1)).sceneFor(PebblesKey.People, PebblesKey.SoulDetail("s1")))
    }

    @Test
    fun `a modal over the pair is not part of it`() {
        val scene =
            pebblesListDetailStrategy(directive(2))
                .sceneFor(PebblesKey.People, PebblesKey.SoulDetail("s1"), PebblesKey.SoulForm("s1"))
        assertNull(scene)
    }

    @Test
    fun `a collection opened from the You tab has no list beside it`() {
        // CollectionDetail with only You beneath it: the scaffold would hold
        // one entry, so it must not claim the stack.
        val scene = pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.You, PebblesKey.CollectionDetail("c1"))
        assertNull(scene)
    }
}
```

The last test encodes a spec requirement that the library does **not** give for free: a lone `detailPane` entry with no list under it still forms a scene (its scaffold shows the detail plus nothing). That is why `pebblesListDetailStrategy` wraps the library strategy.

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests 'app.pbbls.android.navigation.PebblesSceneStrategiesTest'` → FAIL (unresolved symbols).
- [ ] **Step 3: Implement.**

```kotlin
package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * The list-detail pairs (#940). Each pair is its own scaffold: the scene key
 * is what stops a soul detail from joining a collections list below it in the
 * flattened back stack.
 */
enum class PanePair { SOULS, COLLECTIONS }

/**
 * List-detail on large screens (#940): the library strategy, with two rules
 * of ours on top.
 *
 * - **Back pops one entry** (`PopLatest`). The default,
 *   `PopUntilScaffoldValueChange`, keeps popping while the scaffold looks the
 *   same — and an idle list with its placeholder looks the same as a list with
 *   a detail, so back from a soul would unwind past the People root.
 * - **A detail with no list under it is not a pair.** `CollectionDetail`
 *   pushed from the You tab has only You beneath it; the library would still
 *   build a one-entry scaffold for it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun pebblesListDetailStrategy(directive: PaneScaffoldDirective): SceneStrategy<NavKey> {
    val library =
        ListDetailSceneStrategy<NavKey>(
            shouldHandleSinglePaneLayout = false,
            backNavigationBehavior = BackNavigationBehavior.PopLatest,
            directive = directive,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            paneExpansionDragHandle = null,
            paneExpansionState = null,
        )
    return object : SceneStrategy<NavKey> {
        override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
            val scene = with(library) { calculateScene(entries) } ?: return null
            return if (scene.entries.any(PanePairs::isList)) scene else null
        }
    }
}

/** The strategies `RootScreen`'s `NavDisplay` tries, in order (#940). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberPebblesSceneStrategies(): List<SceneStrategy<NavKey>> {
    val directive = pebblesPaneDirective(currentWindowAdaptiveInfoV2())
    return remember(directive) { listOf(pebblesListDetailStrategy(directive)) }
}
```

And the metadata registry, in the same file (it is what both the entry provider and the tests read, so a key's role is stated once):

```kotlin
/**
 * Which entries are list panes and which are detail panes (#940). The entry
 * provider adds [list] or [detail] to an entry's own transition metadata;
 * [metadataFor] states the same per key, for tests.
 *
 * The library's pane metadata is internal, so a list pane also carries our
 * own marker: that is what the strategy wrapper reads, rather than guessing
 * from content keys.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
object PanePairs {
    private const val IS_LIST = "pebbles.pane.list"

    fun list(
        pair: PanePair,
        placeholder: @Composable () -> Unit,
    ): Map<String, Any> = ListDetailSceneStrategy.listPane(pair) { placeholder() } + (IS_LIST to true)

    fun detail(pair: PanePair): Map<String, Any> = ListDetailSceneStrategy.detailPane(pair)

    fun metadataFor(key: PebblesKey): Map<String, Any> =
        when (key) {
            PebblesKey.People -> list(PanePair.SOULS) { SoulsPlaceholder() }
            is PebblesKey.SoulDetail -> detail(PanePair.SOULS)
            PebblesKey.Collections -> list(PanePair.COLLECTIONS) { CollectionsPlaceholder() }
            is PebblesKey.CollectionDetail -> detail(PanePair.COLLECTIONS)
            else -> emptyMap()
        }

    fun isList(entry: NavEntry<*>): Boolean = entry.metadata[IS_LIST] == true
}
```

`SoulsPlaceholder` / `CollectionsPlaceholder` come from Task 11; until then, declare them as empty private composables in this file so the task compiles, and Task 11 fills them in.

- [ ] **Step 4:** Same test command → 5 PASS. If `PaneScaffoldDirective.Default` does not exist, build the directive with the public constructor `PaneScaffoldDirective(maxHorizontalPartitions, horizontalPartitionSpacerSize, maxVerticalPartitions = 1, verticalPartitionSpacerSize = 0.dp, defaultPanePreferredWidth = 360.dp, excludedBounds = emptyList())`.
- [ ] **Step 5: Commit** — `feat(android): list-detail scene strategy for souls and collections`.

### Task 11: Detail placeholders

**Files:**
- Create: `$APP/core/designsystem/DetailPlaceholder.kt`
- Modify: `$APP/navigation/PebblesSceneStrategies.kt` (placeholder lambdas)
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `apps/android/app/src/main/res/values-fr/strings.xml`

- [ ] **Step 1: Strings.** Add to `values/strings.xml` (near the `souls_` / `collections_` strings):

```xml
    <string name="souls_detail_placeholder">Pick a soul to see the pebbles you share.</string>
    <string name="collections_detail_placeholder">Pick a collection to open it.</string>
```

and to `values-fr/strings.xml`:

```xml
    <string name="souls_detail_placeholder">Choisis une âme pour voir les galets que vous partagez.</string>
    <string name="collections_detail_placeholder">Choisis une collection pour l’ouvrir.</string>
```

Check the existing French strings for how "soul" and "pebble" are translated (`grep -n "souls_title\|pebbles_count" apps/android/app/src/main/res/values-fr/strings.xml`) and use the same words.

- [ ] **Step 2: The component.**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What a detail pane shows before anything is picked (#940): a muted icon and
 * one line, centered. Only ever composed beside its list on large screens.
 */
@Composable
fun DetailPlaceholder(
    @DrawableRes iconRes: Int,
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
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
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

Check `PebblesTheme.spacing` has `xl` and `md` (`grep -n "val " $APP/core/designsystem/Spacing.kt`); use the nearest existing names.

- [ ] **Step 3: Wire the placeholders** in `PebblesSceneStrategies.kt`:

```kotlin
@Composable
private fun SoulsPlaceholder() =
    DetailPlaceholder(iconRes = R.drawable.ic_people, text = stringResource(R.string.souls_detail_placeholder))

@Composable
private fun CollectionsPlaceholder() =
    DetailPlaceholder(iconRes = R.drawable.ic_pebble_collection, text = stringResource(R.string.collections_detail_placeholder))
```

(the icons are the tabs' own, from `PebblesNavigationSuite.kt`).

- [ ] **Step 4:** `./gradlew ktlintFormat ktlintCheck testDebugUnitTest assembleDebug` → green (`LocalizationParityTest` included).
- [ ] **Step 5: Commit** — `feat(android): detail panes show a placeholder until something is picked`.

### Task 12: Wire the pairs into the display

**Files:**
- Modify: `$APP/navigation/PebblesEntryProvider.kt`
- Modify: `$APP/RootScreen.kt` (the `NavDisplay(…)` call in `PebblesNavDisplay`, ~line 244)
- Modify: `$APP/features/profile/SoulDetailScreen.kt`, `$APP/features/profile/CollectionDetailScreen.kt`

- [ ] **Step 1: `showBack` on the two detail contents.** Add `showBack: Boolean = true` to `SoulDetailContent` and `CollectionDetailContent` (after `onBack`), and to `SoulDetailScreen` / `CollectionDetailScreen` (passed through). In the content, wrap the top bar's `leading` `IconButton` in `if (showBack) { … }`. KDoc on the parameter: `False beside its list on a large screen (#940): the list is the way back, and system back still pops.`

- [ ] **Step 2: Entry metadata and callbacks.** In `pebblesEntries`, for the four entries:

Use `PanePairs.metadataFor(<the key>)` for the two list tabs, and `PanePairs.detail(PanePair.…)` for detail entries (so no throwaway key instance is built just to read metadata):

```kotlin
    entry<PebblesKey.People>(
        metadata = NavTransitions.forKey(PebblesKey.People) + PanePairs.metadataFor(PebblesKey.People),
    ) {
        SoulsListScreen(
            onBack = navigator::goBack,
            onOpenSoul = { navigator.navigateToDetail(PebblesKey.SoulDetail(it.id)) },
            onCreateSoul = { navigator.navigate(PebblesKey.SoulForm()) },
        )
    }
```

Same for `Collections` (`navigateToDetail(PebblesKey.CollectionDetail(it.id))`). For the details:

```kotlin
    entry<PebblesKey.SoulDetail>(
        metadata = NavTransitions.forKey(PebblesKey.SoulDetail("")) + PanePairs.detail(PanePair.SOULS),
    ) { key ->
        SoulDetailScreen(
            soulId = key.soulId,
            onBack = navigator::goBack,
            onEditSoul = { navigator.navigate(PebblesKey.SoulForm(key.soulId)) },
            showBack = !isBesideList(),
        )
    }
```

and likewise `CollectionDetail`. Add the helper at the bottom of the file:

```kotlin
/**
 * Whether this entry is being shown as a pane of a list-detail scene (#940).
 * The strategy only builds that scene with two panes visible, so inside it
 * the list is always on screen.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isBesideList(): Boolean = LocalListDetailSceneScope.current != null
```

Leave `ProfileScreen`'s `onOpenCollection` as a plain `navigate` — it opens from You, where there is no list.

- [ ] **Step 3: The display.** In `RootScreen.PebblesNavDisplay`, `val sceneStrategies = rememberPebblesSceneStrategies()` before `NavigationSuiteScaffold`, and pass `sceneStrategies = sceneStrategies + SinglePaneSceneStrategy()` to `NavDisplay` — the list form of the parameter **replaces** the default, so the single-pane fallback must be appended explicitly. Check the overload taking `entries` accepts `sceneStrategies` (it does in 1.2.0: `NavDisplay(entries, modifier, contentAlignment, sceneStrategies, …, onBack)`). Import `androidx.navigation3.scene.SinglePaneSceneStrategy`. Add a line to the `PebblesNavDisplay` KDoc: `#940 hands it the list-detail Scene; anything the chain declines falls through to a single pane, as before.`

- [ ] **Step 4:** `./gradlew ktlintFormat ktlintCheck lint testDebugUnitTest assembleDebug` → green.
- [ ] **Step 5: Smoke on devices.** `adb`/emulator: install the debug build (`./gradlew installDebug`) on the Pixel 7 AVD — People → soul and Collections → collection are full-screen pushes with a back arrow, exactly as on `main`. Then a large AVD (create a `Pixel Tablet` or `Resizable (Experimental)` AVD if none exists; `$ANDROID_HOME/emulator/emulator -list-avds`): People shows list + placeholder; tapping a soul shows it beside the list without a back arrow; tapping another soul swaps it; system back pops to the placeholder, then leaves People; opening Edit slides the form up full screen and back returns to the pair; You → a collection is a single pane with a back arrow. On a foldable AVD (`7.6" Fold-in with outer display`), half-open to book posture via the extended controls' virtual sensors and confirm panes sit either side of the fold. Record what you saw in the task report; if an AVD cannot be created, say so rather than skipping silently.
- [ ] **Step 6: Commit** — `feat(android): souls and collections open beside their list on large screens`.

### Task 13: 840 and 1024 dp screenshots for both pairs

**Files:**
- Create: `$SHOT/ListDetailPreviewFrame.kt`
- Create: `$SHOT/ListDetailScreenshots.kt`

- [ ] **Step 1: The frame.** Renders the same scaffold the scene uses, with the directive pinned (layoutlib's window size is not trustworthy — see `NavigationSuiteScreenshots.kt`):

```kotlin
package app.pbbls.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.pbbls.android.navigation.PebblesKey
import app.pbbls.android.navigation.PebblesNavigationItems
import app.pbbls.android.navigation.pebblesNavigationSuiteColors

/**
 * A list-detail pair as the Scene lays it out on a large screen (#940): the
 * collapsed rail, then the list and detail panes of a two-partition
 * `ListDetailPaneScaffold`. The directive and suite type are pinned rather
 * than read from the window, so each render is the layout it is named for.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ListDetailPreviewFrame(
    tab: PebblesKey,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationItems = {
            PebblesNavigationItems(
                current = tab,
                navigationSuiteType = NavigationSuiteType.WideNavigationRailCollapsed,
                onSelect = {},
                onReselect = {},
            )
        },
        navigationSuiteType = NavigationSuiteType.WideNavigationRailCollapsed,
        navigationSuiteColors = pebblesNavigationSuiteColors(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ListDetailPaneScaffold(
            directive =
                PaneScaffoldDirective.Default.copy(
                    maxHorizontalPartitions = 2,
                    horizontalPartitionSpacerSize = 24.dp,
                ),
            value =
                ThreePaneScaffoldValue(
                    primary = PaneAdaptedValue.Expanded,
                    secondary = PaneAdaptedValue.Expanded,
                    tertiary = PaneAdaptedValue.Hidden,
                ),
            listPane = { AnimatedPane { list() } },
            detailPane = { AnimatedPane { detail() } },
        )
    }
}
```

- [ ] **Step 2: The renders.** In `ListDetailScreenshots.kt`, for each pair an idle render (list + `DetailPlaceholder`) and an open render (list + detail content with `showBack = false`), each annotated `@PreviewTest @PreviewWideTall` plus one dark `@Preview(showBackground = true, widthDp = 1024, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)`, all wrapped in `PebblesTheme { … }`. Content comes from Part 1's layers with `SoulsListUiState.Content(…)`, `SoulDetailUiState.Content(…)`, `CollectionsListUiState.Content(…)`, `CollectionDetailUiState.Content(…)` built from fixtures. Build the fixtures the way `SoulsScreenshots.kt` (`previewSoul`), `CollectionsScreenshots.kt` and `PebbleDetailScreenshots.kt` (`soul(…)`, `previewPalette`) do — read those files and copy the construction; use three or four souls/collections and two or three pebbles. `paletteFor = { previewPalette }`. Name the functions `SoulsListDetailIdle`, `SoulsListDetailOpen`, `SoulsListDetailOpenDark`, `CollectionsListDetailIdle`, `CollectionsListDetailOpen`, `CollectionsListDetailOpenDark`.
- [ ] **Step 3: Look at them.** `./gradlew updateDebugScreenshotTest`, open the new PNGs under `app/src/screenshotTestDebug/reference/` with the Read tool, and check: rail on the start edge, list pane ~360 dp, detail pane fills the rest, no back arrow in the detail, placeholder centered. Then discard the local PNGs (see Ground rules).
- [ ] **Step 4:** `./gradlew ktlintFormat ktlintCheck` → green.
- [ ] **Step 5: Commit** — `test(android): 840 and 1024 dp list-detail screenshots for souls and collections`.

### Task 14: Verify and open Part 2

- [ ] `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` → green. `validateDebugScreenshotTest` fails only on the new references (expected: they don't exist yet) — note it.
- [ ] `gh stack add` was done at the start; `gh stack submit`. PR title `feat(android): souls and collections open beside their list on large screens`. Body `Part of #940`, key files, the `PopLatest` and "detail with no list is not a pair" notes, the device smoke results. Lab Note (EN/FR) per the root CLAUDE.md section, `platform: android`, `nodes:` the ids read off the hosted map in the Arkaik step. Labels `feat`, `ui`, `android`; milestone `M61 · Android Refacto` — **confirm with the user first.** After opening, add the `rebaseline-screenshots` label and follow the CLAUDE.md steps for approving the bot's held runs.

---

# Part 3 — pebble detail as a sheet and a pane

Branch: `feat/940-pebble-detail-sheet-pane` (`gh stack add feat/940-pebble-detail-sheet-pane`).

### Task 15: `BottomSheetSceneStrategy`

**Files:**
- Create: `$APP/navigation/BottomSheetSceneStrategy.kt`
- Test: `$TEST/navigation/BottomSheetSceneStrategyTest.kt`

- [ ] **Step 1: Failing test.**

```kotlin
package app.pbbls.android.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The docked sheet on compact windows (#940). */
class BottomSheetSceneStrategyTest {
    private val strategy = BottomSheetSceneStrategy<NavKey>()

    private fun entry(
        key: PebblesKey,
        sheet: Boolean,
    ) = NavEntry<NavKey>(key, metadata = if (sheet) BottomSheetSceneStrategy.bottomSheet() else emptyMap()) {}

    private fun sceneFor(entries: List<NavEntry<NavKey>>) =
        with(strategy) { with(SceneStrategyScope<NavKey>()) { calculateScene(entries) } }

    @Test
    fun `a sheet entry on top becomes an overlay over what is below`() {
        val below = entry(PebblesKey.Path, sheet = false)
        val sheet = entry(PebblesKey.PebbleDetail("p1"), sheet = true)

        val scene = sceneFor(listOf(below, sheet))

        assertTrue(scene is OverlayScene<*>)
        assertEquals(listOf(sheet), scene!!.entries)
        assertEquals(listOf(below), (scene as OverlayScene<NavKey>).overlaidEntries)
    }

    @Test
    fun `anything else is left to the next strategy`() {
        assertNull(sceneFor(listOf(entry(PebblesKey.Path, sheet = false))))
    }

    @Test
    fun `a sheet entry covered by a modal is not a sheet`() {
        val scene =
            sceneFor(
                listOf(
                    entry(PebblesKey.Path, sheet = false),
                    entry(PebblesKey.PebbleDetail("p1"), sheet = true),
                    entry(PebblesKey.EditPebble("p1"), sheet = false),
                ),
            )
        assertNull(scene)
    }
}
```

- [ ] **Step 2:** run the class → FAIL (unresolved).
- [ ] **Step 3: Implement** (the Navigation 3 recipe, adapted):

```kotlin
package app.pbbls.android.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * A docked pane on compact windows (#940): the top entry, when it asks for it,
 * as a full-height `ModalBottomSheet` over whatever is below — the M3 "docked
 * pane" that becomes co-planar on large screens, where the list-detail
 * strategy claims the same entry first.
 *
 * Adapted from the Navigation 3 `BottomSheetSceneStrategy` recipe. The sheet's
 * own dismissal (scrim tap, drag, predictive back) calls `onBack`, so leaving
 * it is always a pop.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[SHEET_KEY] != true) return null
        return BottomSheetScene(
            key = last.contentKey,
            entry = last,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            onBack = onBack,
        )
    }

    companion object {
        private const val SHEET_KEY = "pebbles.bottomSheet"

        /** Metadata marking an entry as a docked sheet on compact windows. */
        fun bottomSheet(): Map<String, Any> = mapOf(SHEET_KEY to true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class BottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        // Full height only: the details it hosts are pages, not peeks.
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            entry.Content()
        }
    }

    override fun equals(other: Any?): Boolean =
        other is BottomSheetScene<*> &&
            key == other.key &&
            entry == other.entry &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries

    override fun hashCode(): Int = listOf(key, entry, previousEntries, overlaidEntries).hashCode()
}
```

- [ ] **Step 4:** run the class → 3 PASS.
- [ ] **Step 5: Commit** — `feat(android): a scene strategy for docked sheets on compact windows`.

### Task 16: Path joins the list-detail pairs, full width when idle

**Files:**
- Modify: `$APP/navigation/PebblesSceneStrategies.kt`
- Test: `$TEST/navigation/PebblesSceneStrategiesTest.kt`

- [ ] **Step 1: Failing tests** (append):

```kotlin
    @Test
    fun `idle Path stays full width on two panes`() {
        assertNull(pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.Path))
    }

    @Test
    fun `an open pebble sits beside Path on two panes`() {
        val scene = pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.Path, PebblesKey.PebbleDetail("p1"))
        assertEquals(2, scene!!.entries.size)
    }

    @Test
    fun `an open pebble on one pane is left to the sheet`() {
        assertNull(pebblesListDetailStrategy(directive(1)).sceneFor(PebblesKey.Path, PebblesKey.PebbleDetail("p1")))
    }
```

- [ ] **Step 2:** run → the first two FAIL.
- [ ] **Step 3: Implement.** Add `PEBBLES` to `PanePair`. In `PanePairs`:
  - `list(pair, placeholder)` makes `placeholder` nullable (`(@Composable () -> Unit)? = null`) and records `FULL_WIDTH_WHEN_IDLE to true` when it is null: **a list with nothing to show beside it stays full width until a detail opens.** Add `fun isFullWidthWhenIdle(entry: NavEntry<*>) = entry.metadata[FULL_WIDTH_WHEN_IDLE] == true`.
  - `detail(pair, sheetWhenCompact: Boolean = false)` adds `BottomSheetSceneStrategy.bottomSheet()` when true.
  - `metadataFor`: `PebblesKey.Path -> list(PanePair.PEBBLES)`, `is PebblesKey.PebbleDetail -> detail(PanePair.PEBBLES, sheetWhenCompact = true)`.

  In `pebblesListDetailStrategy`'s wrapper, before delegating:

```kotlin
            // Idle Path keeps its full-width readable column: it only becomes a
            // list pane once a pebble is open beside it.
            if (PanePairs.isFullWidthWhenIdle(entries.last())) return null
```

and extend its KDoc with a third bullet: `**A list without a placeholder (Path) is a list only while a detail is open.** Idle, it keeps its full readable column; the split animates as a scene change.` `rememberPebblesSceneStrategies` returns `listOf(pebblesListDetailStrategy(directive), BottomSheetSceneStrategy())`.
- [ ] **Step 4:** run → all PASS.
- [ ] **Step 5: Commit** — `feat(android): an open pebble sits beside path on large screens`.

### Task 17: `PebbleDetail` is a `BarKey`

**Files:**
- Modify: `$APP/navigation/PebblesKeys.kt`
- Modify: `$TEST/navigation/PebblesKeysTest.kt`
- Modify: `$APP/navigation/PebblesEntryProvider.kt`

- [ ] **Step 1: Tests first.** In `PebblesKeysTest`, remove `PebblesKey.PebbleDetail("p")` from the `modal keys are not BarKeys` list and add it to the `browse pushes render the bar but are not tabs` list.
- [ ] **Step 2:** run `PebblesKeysTest` → FAIL.
- [ ] **Step 3:** Move `PebbleDetail` in `PebblesKeys.kt` from the Modal section to the Browse pushes section, implementing `PebblesKey, BarKey`, with:

```kotlin
    /**
     * A docked sheet on phones and a pane beside Path on large screens (#940),
     * never a full-screen modal — so the bar stays: under the sheet's scrim on
     * a phone, beside the panes on a tablet. D6 still holds because this is no
     * longer a modal; [EditPebble], opened from it, still is.
     */
```

- [ ] **Step 4: Entry.** In `PebblesEntryProvider`:

```kotlin
    entry<PebblesKey.PebbleDetail>(
        metadata =
            NavTransitions.forKey(PebblesKey.PebbleDetail("")) +
                PanePairs.detail(PanePair.PEBBLES, sheetWhenCompact = true),
    ) { key ->
        PebbleDetailScreen(
            pebbleId = key.pebbleId,
            onDismiss = navigator::goBack,
            onEditRequested = { navigator.navigate(PebblesKey.EditPebble(key.pebbleId)) },
        )
    }
```

Path's `entry<PebblesKey.Path>` gets `+ PanePairs.metadataFor(PebblesKey.Path)` and `onOpenDetail = { navigator.navigateToDetail(PebblesKey.PebbleDetail(it)) }`. The `CreatePebble` `onCreated` reveal keeps `navigate` (it pushes onto Path after popping the composer).
- [ ] **Step 5:** `./gradlew testDebugUnitTest assembleDebug` → green.
- [ ] **Step 6: Commit** — `feat(android): pebble detail keeps the navigation bar`.

### Task 18: Pebble detail chrome for sheet and pane

**Files:**
- Modify: `$APP/features/path/PebbleDetailScreen.kt`

- [ ] **Step 1:** In `PebbleDetailContent`, delete the `.pointerInput(Unit) { … }` block and its comment (the scene, sheet or pane, now decides what is under the detail) and the `safeDrawingPadding()` call; keep `.readableWidth()`. Remove the `navigationIcon` from `DetailTopBar` and the `onBack` parameter from both `DetailTopBar` and `PebbleDetailContent` (the sheet's drag handle, scrim and system back dismiss it; in a pane, Path is beside it). Update `DetailTopBar`'s KDoc: replace the back-arrow sentence with `No back arrow (#940): on a phone it is a sheet, dismissed by drag, scrim or system back; on a large screen Path is beside it.` Keep `PebbleDetailScreen`'s `onDismiss` parameter — still used by nothing inside now, so remove it too and update the one call site in `PebblesEntryProvider` (dismissal is the scene's job).
- [ ] **Step 2: Insets.** Install on the Pixel 7 AVD and open a pebble: the sheet must not draw under the status bar at full height, and the last section must scroll clear of the gesture bar. The `TopAppBar` pads the status bar itself (`TopAppBarDefaults.windowInsets`); if the bottom is clipped, add `.navigationBarsPadding()` to the scrolling body in `PebbleReadView`'s call site modifier, not globally. On the large AVD, open a pebble from Path: Path on the start side, detail on the end side tinted to the emotion, rail visible, no back arrow; system back returns Path to full width; Edit slides up full screen and back returns to the pair. On a phone: the rail/bar is under the scrim and not tappable.
- [ ] **Step 3:** `./gradlew ktlintFormat ktlintCheck lint assembleDebug` → green.
- [ ] **Step 4: Commit** — `feat(android): pebble detail opens as a sheet on phones and a pane on large screens`.

### Task 19: Screenshots

**Files:**
- Modify: `$SHOT/ListDetailScreenshots.kt`
- Modify: `$SHOT/PebbleDetailScreenshots.kt`

- [ ] **Step 1:** In `ListDetailScreenshots.kt` add `PathPebbleListDetailOpen` (light, `@PreviewWideTall`) and `PathPebbleListDetailOpenDark`: `ListDetailPreviewFrame(tab = PebblesKey.Path, list = { PathContent(…) }, detail = { PebbleDetailContent(…) })`. Build `PathContent` inputs as `PathScreenScreenshots.kt` does and the detail from `PebbleDetailScreenshots.kt`'s `fullDetail` — make those two fixtures `internal` in their files to share them rather than copying.
- [ ] **Step 2:** In `PebbleDetailScreenshots.kt`, add one phone render of `PebbleDetailContent` on a `Surface` shaped `MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))` with `BottomSheetDefaults.DragHandle()` above it — the sheet's look without a popup window (layoutlib does not render `ModalBottomSheet`'s window). Name it `PebbleDetailSheetLight`. The `ThemeLiteralsTest` rule forbids literal radii in `features/`/`core/ui` only; screenshot sources are exempt, but prefer the theme shape anyway.
- [ ] **Step 3:** render locally, look, discard PNGs (Ground rules). `./gradlew ktlintCheck`.
- [ ] **Step 4: Commit** — `test(android): pebble sheet and path list-detail screenshots`.

### Task 20: Decision log, verify, open Part 3

- [ ] **Step 1: Decision log.** Append to `docs/decisions/log.md`, matching the format of the last entries (read the tail first):
  - Title: `2026-09-26 — Android pebble detail: docked sheet on phones, pane on large screens (#940)`
  - Body: supersedes the M38 D5 full-screen cover for pebble detail; iOS presents `PebbleDetailSheet` as a sheet, so this is parity; `PebbleDetail` (and, with Part 4, `GlyphDetail`) are `BarKey`s and D6 of #852 still holds because neither is a modal; `EditPebble` stays modal. Also record: list-detail uses `BackNavigationBehavior.PopLatest` because the default unwinds past the tab root.
- [ ] **Step 2:** full gate as Task 14; `gh stack submit`. PR `feat(android): pebble detail opens as a sheet on phones and beside path on large screens`, `Part of #940`, Lab Note, `nodes:` including the pebble detail view. Confirm labels/milestone with the user. Add `rebaseline-screenshots`.
- [ ] **Step 3: Commit** the log entry — `docs(android): log the pebble detail presentation change`.

---

# Part 4 — glyph detail as an entry

Branch: `feat/940-glyph-detail-entry` (`gh stack add feat/940-glyph-detail-entry`).

**Two deliberate choices, recorded in the spec's amendment (Task 21):**
- `GlyphDetail` carries the **whole `GlyphGridItem`**, not an id. There is no "one glyph by id" read in `GlyphMarketServicing`, and opening from the grid must stay instant. The item is serializable already in all but name (`Glyph` and its strokes are `@Serializable`); a stale item after process death is harmless because `buy_glyph` is server-authoritative and the panel morphs from the server's answer.
- **A purchase reaches the list through `GlyphMarketServicing.purchases`.** In a pane, the list stays resumed beside the detail, so the resume refresh the other pairs rely on never fires. The market service emits every successful buy; `GlyphsListViewModel` applies its existing bookkeeping from it.

### Task 21: Spec amendment and serializable grid item

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-android-list-detail-scenes-design.md` ("Glyph detail as an entry" section)
- Modify: `$APP/core/model/GlyphMarket.kt`
- Test: `$TEST/navigation/PebblesKeysTest.kt`

- [ ] **Step 1: Amend the spec** section to the two choices above (replace "loads the grid item and the karma balance by id"). Also add a sentence to "Pane-aware chrome": `Picking another item beside a list replaces the detail (Navigator.navigateToDetail) rather than stacking it.`
- [ ] **Step 2: Failing test.** Add `PebblesKey.GlyphDetail(sampleItem)` to the round-trip list in `every key round-trips through the polymorphic serializer`, and to the BarKey list, with:

```kotlin
    private val sampleItem =
        GlyphGridItem(
            glyph = Glyph(id = "g1", name = "Wave", strokes = emptyList(), viewBox = "0 0 200 200", userId = "u1"),
            price = 12,
            owned = false,
            createdAt = OffsetDateTime.parse("2026-09-01T10:00:00Z"),
            acquiredAt = null,
        )
```

- [ ] **Step 3:** run `PebblesKeysTest` → FAIL (no `GlyphDetail`).
- [ ] **Step 4: Implement.** `GlyphGridItem` gets `@Serializable`, and its two `OffsetDateTime?` fields `@Serializable(with = OffsetDateTimeSerializer::class)` (the serializer used elsewhere in `GlyphMarket.kt`). The computed `id` getter has no backing field and is not serialized. In `PebblesKeys.kt`, in the browse pushes:

```kotlin
    /**
     * One glyph's swap/owned panel (#940): a docked sheet on phones, a pane
     * beside the store on large screens. Carries the grid item itself — there
     * is no by-id read, and opening from the grid must be instant.
     */
    @Serializable
    data class GlyphDetail(
        val item: GlyphGridItem,
    ) : PebblesKey,
        BarKey
```

- [ ] **Step 5:** run → PASS. `./gradlew assembleDebug` (the `when` in `PebblesNavigationSuite` has an `else`, so no exhaustiveness break).
- [ ] **Step 6: Commit** — `feat(android): a navigation key for the glyph detail`.

### Task 22: Purchases flow from the market service

**Files:**
- Modify: `$APP/core/data/GlyphMarketService.kt`
- Modify: `$TEST/testing/FakeGlyphMarketService.kt`
- Modify: `$APP/features/glyph/store/GlyphsListViewModel.kt`
- Test: `$TEST/features/glyph/store/GlyphsListViewModelTest.kt`

- [ ] **Step 1: Failing test** (append to `GlyphsListViewModelTest`):

```kotlin
    @Test
    fun `a purchase made elsewhere drops the glyph from Community`() =
        runTest {
            val market = FakeGlyphMarketService(community = listOf(item("c1"), item("c2")))
            val viewModel = viewModel(market)
            advanceUntilIdle()
            viewModel.onSelectTab(GlyphTab.COMMU)
            advanceUntilIdle()

            market.emitPurchase(glyphId = "c1", result = BuyGlyphResult(entitlementId = "e1", balance = 5))
            advanceUntilIdle()

            val state = viewModel.uiState.value as GlyphsUiState.Content
            assertEquals(listOf("c2"), state.items.map { it.id })
        }
```

Check `BuyGlyphResult`'s constructor fields in `GlyphMarket.kt` and match them.

- [ ] **Step 2:** run the class → FAIL (`emitPurchase` unresolved).
- [ ] **Step 3: Implement.**
  - `GlyphMarketServicing`: add
    ```kotlin
    /**
     * Every purchase that landed, as it lands (#940). The store's list sits
     * beside the detail on large screens and never pauses, so it cannot rely
     * on a resume refresh to learn about one.
     */
    val purchases: SharedFlow<GlyphPurchased>
    ```
    and a model in the same file: `data class GlyphPurchased(val glyphId: String, val result: BuyGlyphResult)`.
  - `GlyphMarketService`: `private val _purchases = MutableSharedFlow<GlyphPurchased>(extraBufferCapacity = 8)`; `override val purchases = _purchases.asSharedFlow()`; in `buy`, after the RPC returns successfully, `_purchases.tryEmit(GlyphPurchased(glyphId, result))` and return `result`. It stays inside the caller's `NonCancellable` section, since `GlyphPurchase.buyAndRecord` wraps the `buy` call.
  - `FakeGlyphMarketService`: implement `purchases` the same way, emit from its `buy`, and add `fun emitPurchase(glyphId: String, result: BuyGlyphResult) { _purchases.tryEmit(GlyphPurchased(glyphId, result)) }`.
  - `GlyphsListViewModel`: in `init`, `viewModelScope.launch { market.purchases.collect { recordPurchase(it.glyphId, it.result) } }`. Split `onPurchased`'s cache and karma bookkeeping into `private fun recordPurchase(glyphId: String, result: BuyGlyphResult)`: apply the karma balance, filter `glyphId` out of `COMMU`, remove `OWNED`, `publish()`. Remove the `covers.selected` handling entirely. Delete `onPurchased`, `openDetail`, `closeDetail` and `GlyphsCovers.selected` (Task 24 removes the drawer call site; do Task 22 and Task 24's screen edit in the same commit if the build needs it). Update the class KDoc paragraph about `onPurchased` to describe the flow instead.
  - Remove or rewrite the existing `GlyphsListViewModelTest` cases that call `onPurchased`/`openDetail` so they drive `market.emitPurchase` instead. Keep every assertion they made about karma, Community and Owned.
- [ ] **Step 4:** run the class → PASS; `./gradlew testDebugUnitTest` → green.
- [ ] **Step 5: Commit** — `feat(android): glyph purchases reach the store list as they land`.

### Task 23: `GlyphDetailViewModel`

**Files:**
- Create: `$APP/features/glyph/store/GlyphDetailViewModel.kt`
- Test: `$TEST/features/glyph/store/GlyphDetailViewModelTest.kt`

- [ ] **Step 1: Failing test.**

```kotlin
package app.pbbls.android.features.glyph.store

import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The glyph detail entry's balance and purchase record (#940). */
class GlyphDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val item =
        GlyphGridItem(
            glyph = Glyph(id = "g1", strokes = emptyList(), viewBox = "0 0 200 200"),
            price = 10,
            owned = false,
            createdAt = null,
            acquiredAt = null,
        )

    @Test
    fun `the balance is the shared karma`() =
        runTest {
            val stats = FakePathStatsService(karma = 42)
            val viewModel = GlyphDetailViewModel(FakeGlyphMarketService(), stats)
            advanceUntilIdle()

            assertEquals(42, viewModel.balance.value)
        }

    @Test
    fun `a recorded purchase applies the new balance`() =
        runTest {
            val stats = FakePathStatsService(karma = 42)
            val viewModel = GlyphDetailViewModel(FakeGlyphMarketService(), stats)

            viewModel.onRecorded(BuyGlyphResult(entitlementId = "e1", balance = 32))
            advanceUntilIdle()

            assertEquals(32, viewModel.balance.value)
        }
}
```

Read `FakePathStatsService` first and use its real constructor/setter for the starting karma.

- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: Implement.**

```kotlin
package app.pbbls.android.features.glyph.store

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.model.BuyGlyphResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The glyph detail entry (#940). The item arrives in the key, so there is
 * nothing to load: this holds the buyer's balance and records a purchase.
 *
 * The buy itself stays in [GlyphSwapPanel], which the composer's glyph picker
 * also hosts; [onRecorded] runs inside its uncancellable section and only
 * records. The store list learns of the purchase from
 * `GlyphMarketServicing.purchases`, not from here.
 */
@HiltViewModel
class GlyphDetailViewModel
    @Inject
    constructor(
        val market: GlyphMarketServicing,
        private val stats: PathStatsServicing,
    ) : ViewModel() {
        val balance: StateFlow<Int> =
            snapshotFlow { stats.karma ?: 0 }
                .stateIn(viewModelScope, SharingStarted.Eagerly, stats.karma ?: 0)

        fun onRecorded(result: BuyGlyphResult) = stats.applyKarmaBalance(result.balance)
    }
```

(`snapshotFlow` mirrors how `GlyphsListViewModel` observes the same singleton. If `balance` doesn't update in the test under `StandardTestDispatcher`, add `Snapshot.sendApplyNotifications()` after `onRecorded` in the test, the way the existing tests handle snapshot state — grep the tests for it.)
- [ ] **Step 4:** run → PASS.
- [ ] **Step 5: Commit** — `feat(android): a view model for the glyph detail entry`.

### Task 24: Glyph detail entry, sheet and pane

**Files:**
- Modify: `$APP/features/glyph/store/GlyphDetailDrawer.kt`
- Modify: `$APP/features/glyph/store/GlyphsListScreen.kt`
- Modify: `$APP/navigation/PebblesSceneStrategies.kt`, `$APP/navigation/PebblesEntryProvider.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `values-fr/strings.xml`

- [ ] **Step 1: The entry screen.** In `GlyphDetailDrawer.kt`, replace `GlyphDetailDrawer` (the `ModalBottomSheet` wrapper — the scene is the sheet now) with:

```kotlin
/**
 * The glyph detail entry (#940): [GlyphSwapPanel] for the item in the key. On
 * a phone the bottom-sheet scene hosts it, on a large screen the list-detail
 * scene puts it beside the store, so it draws no sheet of its own.
 */
@Composable
fun GlyphDetailScreen(
    item: GlyphGridItem,
    modifier: Modifier = Modifier,
    viewModel: GlyphDetailViewModel = hiltViewModel(),
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    Box(modifier) {
        GlyphSwapPanel(item = item, balance = balance, market = viewModel.market, onRecorded = viewModel::onRecorded)
    }
}
```

Keep `GlyphSwapPanel` and `GlyphDetailDrawerContent` unchanged (the picker still embeds the panel). Remove the `ModalBottomSheet`/`rememberModalBottomSheetState` imports if unused. Check the picker (`grep -rn "GlyphDetailDrawer(" $APP`) — if anything but `GlyphsListScreen` calls `GlyphDetailDrawer`, keep that function and add `GlyphDetailScreen` beside it instead.
- [ ] **Step 2: The grid.** `GlyphsListScreen` takes a new `onOpenGlyph: (GlyphGridItem) -> Unit` parameter; the cell's `state.tab != GlyphTab.MINE -> ({ viewModel.openDetail(item) })` becomes `({ onOpenGlyph(item) })`; delete the `covers.selected?.let { GlyphDetailDrawer(…) }` block.
- [ ] **Step 3: Pair metadata.** Add `GLYPHS` to `PanePair`; `PanePairs.metadataFor`: `PebblesKey.Glyphs -> list(PanePair.GLYPHS) { GlyphsPlaceholder() }`, `is PebblesKey.GlyphDetail -> detail(PanePair.GLYPHS, sheetWhenCompact = true)`. Placeholder string `glyphs_detail_placeholder`: EN `Pick a glyph to see it up close.`, FR `Choisis un glyphe pour le voir de près.` (check the FR word for glyph in `values-fr`). Icon: the store tab's own icon (`grep -n "ic_" $APP/features/glyph/store/GlyphTabBar.kt`).
- [ ] **Step 4: Entries.**

```kotlin
    entry<PebblesKey.Glyphs>(metadata = NavTransitions.forKey(PebblesKey.Glyphs) + PanePairs.metadataFor(PebblesKey.Glyphs)) {
        GlyphsListScreen(
            onBack = navigator::goBack,
            onCarve = { navigator.navigate(PebblesKey.GlyphCarve) },
            onOpenGlyph = { navigator.navigateToDetail(PebblesKey.GlyphDetail(it)) },
        )
    }

    entry<PebblesKey.GlyphDetail>(
        metadata = NavTransitions.push + PanePairs.detail(PanePair.GLYPHS, sheetWhenCompact = true),
    ) { key ->
        GlyphDetailScreen(item = key.item)
    }
```
- [ ] **Step 5: Toolbar inside a pane.** In `GlyphsListScreen`, `val isWide = isWideWindow()` becomes `val isWide = isWideWindow() && !isInListPane`, where `isInListPane` is a new `Boolean = false` parameter documented `True as the list pane of a list-detail scene (#940): the grid is pane-wide there, so the toolbar goes back to the bottom.`; the entry passes `isInListPane = LocalListDetailSceneScope.current != null` (make the provider's `isBesideList()` helper serve both).
- [ ] **Step 6:** `./gradlew ktlintFormat ktlintCheck lint testDebugUnitTest assembleDebug` → green (`ArchitectureBoundaryTest` included: `navigation` may import `features`; nothing in `core` may).
- [ ] **Step 7: Smoke.** Phone: Commu/Owned glyph opens as a sheet; swiping it away mid-buy still records the purchase (the Commu grid drops it). Large: grid on the start side with the toolbar at its bottom, detail beside it, another glyph swaps the detail, a purchase drops the glyph from Commu without leaving the pane, back returns to the placeholder. Mine-tab cells still open rename, not the detail.
- [ ] **Step 8: Commit** — `feat(android): glyph detail opens as a sheet on phones and beside the store on large screens`.

### Task 25: Screenshots, verify, open Part 4

- [ ] **Step 1:** `ListDetailScreenshots.kt`: `GlyphsListDetailIdle`, `GlyphsListDetailOpen`, `GlyphsListDetailOpenDark` — list side from `StoreScreenshots.kt`'s gallery pieces (make `previewItem` internal), detail side `GlyphDetailDrawerContent(…)` as `DrawerGallery` does.
- [ ] **Step 2:** render, look, discard; `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`.
- [ ] **Step 3: Commit** — `test(android): glyph list-detail screenshots`.
- [ ] **Step 4:** `gh stack submit`. PR `feat(android): glyph detail opens beside the store on large screens`, body `Resolves #940`, amended acceptance checklist from the spec, Lab Note, labels/milestone confirmed with the user, `rebaseline-screenshots`. Update #940's body with the amended acceptance criteria (confirm with the user before editing the issue).

---

## Self-review

- Spec coverage: matrix rows → Tasks 12 (souls, collections), 16–18 (pebble), 21–24 (glyph); strategy chain → 10, 15, 16; directive → 8; back arrow → 12, 18, 24; placeholders → 11, 24; screenshots → 13, 19, 25; decision log → 20; Arkaik → Part 2 preamble; limits → spec only (no code); acceptance edit → 25.
- Added beyond the spec, and amended into it in Task 21: `navigateToDetail`, `PopLatest`, "a detail with no list is not a pair", the item-carrying `GlyphDetail`, `GlyphMarketServicing.purchases`.
