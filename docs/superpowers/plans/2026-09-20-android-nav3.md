# Android Navigation 3 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Navigation 2 string routes and fourteen conditionally-composed full-screen covers with Navigation 3 typed keys, one `NavDisplay`, an M3 four-tab `NavigationBar` with per-tab back stacks, and working predictive back.

**Architecture:** One `NavDisplay` renders a flattened list of entries drawn from four per-tab `NavBackStack`s held in a `NavigationState` and mutated only by a `Navigator`. Every route and every full-screen cover becomes a `@Serializable` key implementing `PebblesKey : NavKey`; two marker sub-interfaces (`TopLevelKey`, `BarKey`) make bar visibility a property of the key rather than a parallel list. Auth and onboarding stop being a composition branch and become keys the session state pushes.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.navigation3` 1.1.7, `lifecycle-viewmodel-navigation3` 2.11.0, `navigationevent-compose` 1.1.2, Hilt 2.60.1, JUnit4 + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-09-20-android-nav3-design.md`. Decision ids below (D1–D11) refer to it.

---

## Conventions for every task

**Working directory** is `apps/android` unless a path says otherwise. The Android SDK must be resolvable — `export ANDROID_HOME=$HOME/Library/Android/sdk` if `scripts/gradle-if-sdk.sh` warns and exits 0, because a silent skip looks exactly like a pass.

| Command | What it is |
|---|---|
| `npm run lint --workspace=@pbbls/android` | ktlint only |
| `./gradlew lint` | Android Lint — **this is the real gate**, ktlint is not |
| `npm run test --workspace=@pbbls/android` | JVM unit tests (`testDebugUnitTest`) |
| `npm run build --workspace=@pbbls/android` | `assembleDebug` |
| `./gradlew validateDebugScreenshotTest` | Screenshot comparison against committed PNGs |
| `./gradlew updateDebugScreenshotTest` | Re-baseline PNGs (only when a change is intended) |

**Commit style:** conventional commits, lowercase, no period — `type(scope): description`. Scope is `android`. End every commit message with:

```
Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

**Branches and the stack.** One part is one branch is one PR, chained with `gh stack` (see the `gh-stack` skill). Branch names follow `type/852-<description>`:

| Part | Branch |
|---|---|
| 1 | `feat/852-navigation3-migration` (already created; holds the spec + this plan) |
| 2 | `feat/852-navigation-bar-tabs` |
| 3 | `feat/852-profile-covers-as-entries` |
| 4 | `feat/852-write-path-as-entries` |
| 5 | `feat/852-auth-and-deep-links` |
| 6 | `feat/852-service-graph-dies` |

Each part must pass `./gradlew lint` and `npm run test --workspace=@pbbls/android` **on its own**, without the parts above it. If you discover mid-stack that a lower part needs a change, navigate down (`gh stack down`), fix it there, and `gh stack rebase --upstack` — do not patch around it at the top.

**Where tests go.** TDD applies to the parts with logic in them: `Navigator`, key serialization, and every ViewModel. Compose wiring (which entry renders which screen) is not unit-tested here — it is covered by `assembleDebug` compiling and by the screenshot suite. Do not invent a Robolectric harness for it; the repo has none and adding one is out of scope.

**Do not** add an entry to `di/ServiceGraph.kt` or a new `Local…Service` at any point in this plan. Part 6 deletes the former.

**Do not convert the sheets or the confirm dialogs (spec D7).** The five
`ModalBottomSheet` call sites — `EmotionPickerSheet`, `SoulPickerSheet`,
`ValencePickerSheet`, `GlyphPickerSheet`, `GlyphDetailDrawer` — return a value to
their parent and stay sheets. `DeleteConfirmDialog` and `DeleteErrorDialog` stay
plain composables; `DialogSceneStrategy` earns its place only when a dialog must
survive process death, and re-asking "delete this pebble?" after a process death
is correct behaviour, not a bug. No task in this plan touches them, and a task
that starts to is off-plan.

---

## File structure

**Created:**

| Path (under `app/src/main/kotlin/app/pbbls/android/`) | Responsibility |
|---|---|
| `navigation/PebblesKeys.kt` | Every nav key; `PebblesKey`, `TopLevelKey`, `BarKey` |
| `navigation/NavigationState.kt` | The four per-tab back stacks + `rememberNavigationState` |
| `navigation/Navigator.kt` | The only writer of navigation state |
| `navigation/PebblesEntryProvider.kt` | Key → `NavEntry`, including transition metadata |
| `navigation/NavTransitions.kt` | The slide-up and shared-axis transition specs |
| `navigation/PebblesNavigationBar.kt` | The M3 bar and its four items |
| `RootViewModel.kt` | Session → stack transitions, pending invite (part 6) |

**Deleted by the end:** `di/ServiceGraph.kt`, and from `RootScreen.kt` the twelve `ROUTE_*` constants, `AuthedNavHost`, `WelcomeAuthNavHost`.

**Heavily modified:** `RootScreen.kt` (parts 1, 2, 5, 6), `MainActivity.kt` (parts 5, 6), `PathScreen.kt` (parts 2, 4), `ProfileScreen.kt` (part 2), `SettingsScreen.kt` (part 3), `SoulFormScreen.kt` / `CollectionFormScreen.kt` (part 3), `LabScreen.kt` (part 3), `GlyphPickerSheet.kt` (part 6).

---

# Part 1 — Navigation 3 in, string routes out

**Branch:** `feat/852-navigation3-migration` (already checked out)

**What ships:** One `NavDisplay` over a single flat `rememberNavBackStack(Path)`, rendering exactly the destinations the two `NavHost`s render today. **The IA does not change and covers stay covers** (D11). A reviewer of this PR checks one thing: that every route became the same destination.

---

### Task 1: Add the Navigation 3 dependencies

**Files:**
- Modify: `apps/android/gradle/libs.versions.toml`
- Modify: `apps/android/app/build.gradle.kts`

- [ ] **Step 1: Add the versions**

In `gradle/libs.versions.toml`, after the `hiltNavigationCompose = "1.4.0"` line in `[versions]`:

```toml
# Navigation 3 (#852, supersedes M38 D5). Stable — none of these are alpha.
# navigation3-runtime/-ui are the back stack and the display; the lifecycle
# artifact supplies rememberViewModelStoreNavEntryDecorator, which is what
# scopes a ViewModel to a NavEntry rather than to the screen hosting it.
#
# lifecycleViewmodelNavigation3 deliberately tracks `lifecycleRuntimeKtx`
# (2.11.0) rather than carrying its own number: androidx ships lifecycle-* as
# one coordinated release and mixing versions across the group is the classic
# NoSuchMethodError.
navigation3 = "1.1.7"
navigationEvent = "1.1.2"
```

In `[libraries]`, after the `androidx-hilt-navigation-compose` line:

```toml
# Navigation 3 (#852). material3-adaptive-navigation3 is deliberately NOT here:
# it exists for adaptive list-detail/supporting-pane Scenes on large screens,
# and this app is phone-only and portrait-locked, so it would have no call site.
androidx-navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
androidx-lifecycle-viewmodel-navigation3 = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-navigation3", version.ref = "lifecycleRuntimeKtx" }
androidx-navigationevent-compose = { group = "androidx.navigationevent", name = "navigationevent-compose", version.ref = "navigationEvent" }
```

- [ ] **Step 2: Wire them into the module**

In `app/build.gradle.kts`, immediately after the `implementation(libs.androidx.navigation.compose)` line:

```kotlin
    // Navigation 3 (#852). navigation-compose above is still here because Part 1
    // is a move, not a rewrite — it is removed in Part 5 once nothing imports it.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigationevent.compose)
```

- [ ] **Step 3: Verify they resolve**

Run: `npm run build --workspace=@pbbls/android`
Expected: `BUILD SUCCESSFUL`. If you see `WARN: no Android SDK`, export `ANDROID_HOME` and run again — that warning is an exit-0 skip, not a pass.

- [ ] **Step 4: Commit**

```bash
git add apps/android/gradle/libs.versions.toml apps/android/app/build.gradle.kts
git commit -m "$(cat <<'EOF'
chore(android): add the Navigation 3 dependencies (#852)

All four are stable releases. lifecycle-viewmodel-navigation3 rides the
lifecycle 2.11.0 already in the catalog rather than its own version, because
androidx ships lifecycle-* as one coordinated release.

material3-adaptive-navigation3 is named in the issue and deliberately left
out: it serves adaptive list-detail Scenes on large screens, and this app is
phone-only and portrait-locked.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The key taxonomy

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesKeys.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/navigation/PebblesKeysTest.kt`

A key that fails to serialize is a process-death restoration bug that nothing else in this plan would catch, so this task is test-first.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/pbbls/android/navigation/PebblesKeysTest.kt`:

```kotlin
package app.pbbls.android.navigation

import app.pbbls.android.features.auth.AuthMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every key must survive a serialization round-trip, because that is exactly
 * what `rememberNavBackStack` does to restore a stack after process death. A
 * key that throws here is a screen that does not come back.
 */
class PebblesKeysTest {
    private val json = Json

    private fun roundTrip(key: PebblesKey): PebblesKey = json.decodeFromString(PebblesKey.serializer(), json.encodeToString(PebblesKey.serializer(), key))

    @Test
    fun `every key round-trips through the polymorphic serializer`() {
        val keys: List<PebblesKey> =
            listOf(
                PebblesKey.Path,
                PebblesKey.People,
                PebblesKey.Collections,
                PebblesKey.You,
                PebblesKey.SoulDetail("soul-1"),
                PebblesKey.CollectionDetail("col-1"),
                PebblesKey.Connections,
                PebblesKey.Glyphs,
                PebblesKey.Achievements,
                PebblesKey.Lab,
                PebblesKey.LabAnnouncement("log-1"),
                PebblesKey.LabLogList("announcements"),
                PebblesKey.RecordFlow(resumeDraftId = null),
                PebblesKey.RecordFlow(resumeDraftId = "draft-1"),
                PebblesKey.CreatePebble(resumeDraftId = null),
                PebblesKey.CreatePebble(resumeDraftId = "draft-1"),
                PebblesKey.Drafts,
                PebblesKey.PebbleDetail("pebble-1"),
                PebblesKey.EditPebble("pebble-1"),
                PebblesKey.Settings,
                PebblesKey.SoulForm(soulId = null),
                PebblesKey.SoulForm(soulId = "soul-1"),
                PebblesKey.CollectionForm(collectionId = null),
                PebblesKey.CollectionForm(collectionId = "col-1"),
                PebblesKey.Invite,
                PebblesKey.AcceptInvite("token-1"),
                PebblesKey.GlyphCarve,
                PebblesKey.Onboarding,
                PebblesKey.Welcome,
                PebblesKey.Auth(AuthMode.LOGIN),
                PebblesKey.Auth(AuthMode.SIGNUP),
            )

        keys.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `the four tabs are TopLevelKeys and every TopLevelKey is a BarKey`() {
        val tabs = listOf(PebblesKey.Path, PebblesKey.People, PebblesKey.Collections, PebblesKey.You)
        tabs.forEach {
            assertTrue("$it should be a TopLevelKey", it is TopLevelKey)
            assertTrue("$it should be a BarKey", it is BarKey)
        }
        assertEquals(tabs, PebblesKey.tabs)
    }

    @Test
    fun `modal keys are not BarKeys`() {
        val modals: List<PebblesKey> =
            listOf(
                PebblesKey.RecordFlow(null),
                PebblesKey.CreatePebble(null),
                PebblesKey.Drafts,
                PebblesKey.PebbleDetail("p"),
                PebblesKey.EditPebble("p"),
                PebblesKey.Settings,
                PebblesKey.SoulForm(null),
                PebblesKey.CollectionForm(null),
                PebblesKey.Invite,
                PebblesKey.AcceptInvite("t"),
                PebblesKey.GlyphCarve,
                PebblesKey.Onboarding,
                PebblesKey.Welcome,
                PebblesKey.Auth(AuthMode.LOGIN),
            )
        modals.forEach { assertTrue("$it must not render the bar", it !is BarKey) }
    }

    @Test
    fun `browse pushes render the bar but are not tabs`() {
        val pushes: List<PebblesKey> =
            listOf(
                PebblesKey.SoulDetail("s"),
                PebblesKey.CollectionDetail("c"),
                PebblesKey.Connections,
                PebblesKey.Glyphs,
                PebblesKey.Achievements,
                PebblesKey.Lab,
                PebblesKey.LabAnnouncement("l"),
                PebblesKey.LabLogList("m"),
            )
        pushes.forEach {
            assertTrue("$it should render the bar", it is BarKey)
            assertTrue("$it must not be a tab", it !is TopLevelKey)
        }
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `npm run test --workspace=@pbbls/android -- --tests '*PebblesKeysTest*'`
Expected: compilation failure — `Unresolved reference: PebblesKey`.

- [ ] **Step 3: Write the keys**

Create `app/src/main/kotlin/app/pbbls/android/navigation/PebblesKeys.kt`:

```kotlin
package app.pbbls.android.navigation

import androidx.navigation3.runtime.NavKey
import app.pbbls.android.features.auth.AuthMode
import kotlinx.serialization.Serializable

/**
 * Every navigable destination in the app (#852, supersedes M38 D5).
 *
 * The two marker interfaces below are the whole trick: bar visibility and
 * tab-ness are properties OF THE KEY, read straight off the stack, rather than
 * a second list somewhere that can disagree with the first. A new modal key is
 * safe by default because it simply is not a [BarKey].
 *
 * Keys are `@Serializable` because `rememberNavBackStack` persists them across
 * process death — that persistence is the whole reason the covers are being
 * promoted, so a key that cannot serialize defeats the migration.
 */
@Serializable
sealed interface PebblesKey : NavKey {
    // ---- Top level: the four tabs (D2) ----

    @Serializable
    data object Path : PebblesKey, TopLevelKey

    @Serializable
    data object People : PebblesKey, TopLevelKey

    @Serializable
    data object Collections : PebblesKey, TopLevelKey

    @Serializable
    data object You : PebblesKey, TopLevelKey

    // ---- Browse pushes: the bar stays up ----

    @Serializable
    data class SoulDetail(val soulId: String) : PebblesKey, BarKey

    @Serializable
    data class CollectionDetail(val collectionId: String) : PebblesKey, BarKey

    @Serializable
    data object Connections : PebblesKey, BarKey

    @Serializable
    data object Glyphs : PebblesKey, BarKey

    @Serializable
    data object Achievements : PebblesKey, BarKey

    @Serializable
    data object Lab : PebblesKey, BarKey

    @Serializable
    data class LabAnnouncement(val logId: String) : PebblesKey, BarKey

    @Serializable
    data class LabLogList(val mode: String) : PebblesKey, BarKey

    // ---- Modal: full-screen, the bar is covered ----

    @Serializable
    data class RecordFlow(val resumeDraftId: String? = null) : PebblesKey

    @Serializable
    data class CreatePebble(val resumeDraftId: String? = null) : PebblesKey

    @Serializable
    data object Drafts : PebblesKey

    @Serializable
    data class PebbleDetail(val pebbleId: String) : PebblesKey

    @Serializable
    data class EditPebble(val pebbleId: String) : PebblesKey

    @Serializable
    data object Settings : PebblesKey

    /** [soulId] null means "create". */
    @Serializable
    data class SoulForm(val soulId: String? = null) : PebblesKey

    /** [collectionId] null means "create". */
    @Serializable
    data class CollectionForm(val collectionId: String? = null) : PebblesKey

    @Serializable
    data object Invite : PebblesKey

    @Serializable
    data class AcceptInvite(val token: String) : PebblesKey

    @Serializable
    data object GlyphCarve : PebblesKey

    @Serializable
    data object Onboarding : PebblesKey

    // ---- Unauthenticated funnel (Part 5 makes these reachable) ----

    @Serializable
    data object Welcome : PebblesKey

    /**
     * [mode] is a typed enum, not a route string. This is what deletes
     * `AuthMode.fromRoute`'s silent `LOGIN` default: a malformed mode is now a
     * deserialization failure, not a user silently landing on the wrong tab.
     */
    @Serializable
    data class Auth(val mode: AuthMode) : PebblesKey

    companion object {
        /** Bar order, left to right. Also the tab set [NavigationState] keys on. */
        val tabs: List<PebblesKey> = listOf(Path, People, Collections, You)
    }
}

/**
 * A key that is one of the four bar destinations.
 *
 * It extends [BarKey] rather than each tab object declaring both: a tab that
 * did not render the bar would be unreachable from every other tab, so the
 * relationship is an invariant of the type, not a per-object choice.
 */
sealed interface TopLevelKey : BarKey

/**
 * A key that renders the [NavigationBar]. Everything else is modal and covers
 * it (D5).
 *
 * **Load-bearing invariant (D6):** tab switching is only reachable from a
 * `BarKey`, which is what guarantees a non-current tab's stack never has a
 * modal on top of it. Making the bar visible over a modal breaks the flattened
 * stack and must be rejected on those grounds.
 */
sealed interface BarKey
```

`AuthMode` needs `@Serializable`. In `app/src/main/kotlin/app/pbbls/android/features/auth/AuthMode.kt`, add the import `kotlinx.serialization.Serializable` and annotate the enum:

```kotlin
@Serializable
enum class AuthMode(
```

Leave `route` and `fromRoute` in place for now — Part 5 deletes them, once nothing calls them.

- [ ] **Step 4: Run the test**

Run: `npm run test --workspace=@pbbls/android -- --tests '*PebblesKeysTest*'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesKeys.kt \
        apps/android/app/src/main/kotlin/app/pbbls/android/features/auth/AuthMode.kt \
        apps/android/app/src/test/kotlin/app/pbbls/android/navigation/PebblesKeysTest.kt
git commit -m "$(cat <<'EOF'
feat(android): typed navigation keys for every route and cover (#852)

PebblesKey covers all twelve former ROUTE_* strings plus the fourteen
conditionally-composed covers. Bar visibility and tab-ness are marker
interfaces on the key rather than a parallel list, so a new modal key is
safe by default.

AuthMode gains @Serializable so Auth(mode) carries the enum instead of a
route string; fromRoute's silent LOGIN default dies in Part 5.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Transition specs

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/NavTransitions.kt`

Pure declarations, no logic — no test.

- [ ] **Step 1: Write it**

Create `app/src/main/kotlin/app/pbbls/android/navigation/NavTransitions.kt`:

```kotlin
package app.pbbls.android.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay

/**
 * The two motions this app navigates with (#852).
 *
 * Modal keys slide up from the bottom — the `fullScreenCover` analog the covers
 * used to fake by appearing instantly. Browse pushes use an M3 shared-axis
 * slide, so a push reads as lateral movement within a tab.
 *
 * These are handed to `NavDisplay` as per-entry metadata rather than decided in
 * a `when` at the display, so a key's animation travels with the key.
 *
 * Every lambda parameter below is named rather than left implicit. The three
 * spec builders are extension lambdas on `AnimatedContentTransitionScope`, and
 * `predictivePopTransitionSpec` carries an extra `Int` parameter — so a bare
 * `it` inside one of them is ambiguous between that parameter and the slide
 * offset lambda's own. Naming both is what keeps this compiling.
 */
object NavTransitions {
    private const val DURATION_MS = 350
    private const val FADE_MS = 200

    /** Slide-up / slide-down. Applied to every non-[BarKey]. */
    val modal: Map<String, Any> =
        NavDisplay.transitionSpec {
            slideInVertically(tween(DURATION_MS)) { height -> height } togetherWith fadeOut(tween(FADE_MS))
        } +
            NavDisplay.popTransitionSpec {
                fadeIn(tween(FADE_MS)) togetherWith slideOutVertically(tween(DURATION_MS)) { height -> height }
            } +
            // The Int parameter is the swipe edge; this app animates the same
            // way from either edge, so it is deliberately ignored.
            NavDisplay.predictivePopTransitionSpec { _ ->
                fadeIn(tween(FADE_MS)) togetherWith slideOutVertically(tween(DURATION_MS)) { height -> height }
            }

    /** M3 shared-axis X. Applied to [BarKey] pushes. */
    val push: Map<String, Any> =
        NavDisplay.transitionSpec { sharedAxisForward() } +
            NavDisplay.popTransitionSpec { sharedAxisBackward() } +
            NavDisplay.predictivePopTransitionSpec { _ -> sharedAxisBackward() }

    private fun AnimatedContentTransitionScope<*>.sharedAxisForward() =
        (
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(DURATION_MS)) +
                fadeIn(tween(FADE_MS))
        ) togetherWith (
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(DURATION_MS)) +
                fadeOut(tween(FADE_MS))
        )

    private fun AnimatedContentTransitionScope<*>.sharedAxisBackward() =
        (
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(DURATION_MS)) +
                fadeIn(tween(FADE_MS))
        ) togetherWith (
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(DURATION_MS)) +
                fadeOut(tween(FADE_MS))
        )

    /** The metadata a key should carry, chosen by whether it shows the bar. */
    fun forKey(key: PebblesKey): Map<String, Any> = if (key is BarKey) push else modal
}
```

**Verified against the 1.1.7 artifact before this plan was written:**

```
transitionSpec(Function1<AnimatedContentTransitionScope<Scene<?>>, ContentTransform>)
popTransitionSpec(Function1<AnimatedContentTransitionScope<Scene<?>>, ContentTransform>)
predictivePopTransitionSpec(Function2<AnimatedContentTransitionScope<Scene<?>>, Integer, ContentTransform>)
```

All three exist. The third's `Function2` is the extra `Int`, which is why its
lambda is written `{ _ -> … }` and the other two are not.

- [ ] **Step 2: Verify it compiles**

Run: `npm run build --workspace=@pbbls/android`
Expected: `BUILD SUCCESSFUL`.

All three spec builders are confirmed present in 1.1.7 (signatures above), so a
failure here is a syntax problem in the lambdas, not a missing API. Do not drop
`predictivePopTransitionSpec` to get green — it is what makes the gesture
scrubbable, which is an acceptance criterion.

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/NavTransitions.kt
git commit -m "$(cat <<'EOF'
feat(android): slide-up and shared-axis navigation transitions (#852)

Covers appeared by instant composition with no animation at all. Modal
keys now slide up, browse pushes use shared-axis X, and both carry a
predictivePopTransitionSpec so the back gesture can be scrubbed.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: The entry provider

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt`

This maps each key to the screen it renders. In Part 1 it covers **only the keys the current `NavHost`s reach** — Path, Profile-as-`You`, and the six pushes. The modal keys exist in the taxonomy but are not yet wired, because their covers are still inside their parent screens (D11). Parts 3–5 wire the rest.

- [ ] **Step 1: Write it**

Create `app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt`:

```kotlin
package app.pbbls.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entry
import app.pbbls.android.features.connections.ConnectionsScreen
import app.pbbls.android.features.glyph.store.GlyphsListScreen
import app.pbbls.android.features.lab.LabScreen
import app.pbbls.android.features.path.PathScreen
import app.pbbls.android.features.profile.AchievementsScreen
import app.pbbls.android.features.profile.CollectionDetailScreen
import app.pbbls.android.features.profile.CollectionsListScreen
import app.pbbls.android.features.profile.ProfileScreen
import app.pbbls.android.features.profile.SoulDetailScreen
import app.pbbls.android.features.profile.SoulsListScreen

/**
 * Key → screen (#852).
 *
 * Part 1 wires exactly what the two NavHosts reached, with the same IA, so this
 * PR is a move and not a redesign (D11). The modal keys in [PebblesKey] are
 * declared but not yet wired: their covers still live inside their parent
 * screens until Parts 3–5 promote them.
 *
 * Each `entry` carries its transition metadata via [NavTransitions.forKey], so
 * the animation travels with the key rather than living in a `when` at the
 * display.
 */
@Suppress("LongParameterList")
fun EntryProviderScope<PebblesKey>.pebblesEntries(
    navigator: Navigator,
    onSignOut: () -> Unit,
) {
    entry<PebblesKey.Path>(metadata = NavTransitions.forKey(PebblesKey.Path)) {
        PathScreen(onProfile = { navigator.navigate(PebblesKey.You) })
    }

    entry<PebblesKey.You>(metadata = NavTransitions.forKey(PebblesKey.You)) {
        ProfileScreen(
            onBack = navigator::goBack,
            onSignOut = onSignOut,
            onOpenSouls = { navigator.navigate(PebblesKey.People) },
            onOpenCollections = { navigator.navigate(PebblesKey.Collections) },
            onOpenCollection = { navigator.navigate(PebblesKey.CollectionDetail(it.id)) },
            onOpenGlyphs = { navigator.navigate(PebblesKey.Glyphs) },
            onOpenConnections = { navigator.navigate(PebblesKey.Connections) },
            onOpenLab = { navigator.navigate(PebblesKey.Lab) },
            onOpenAchievements = { navigator.navigate(PebblesKey.Achievements) },
        )
    }

    entry<PebblesKey.People>(metadata = NavTransitions.forKey(PebblesKey.People)) {
        SoulsListScreen(
            onBack = navigator::goBack,
            onOpenSoul = { navigator.navigate(PebblesKey.SoulDetail(it.id)) },
        )
    }

    entry<PebblesKey.Collections>(metadata = NavTransitions.forKey(PebblesKey.Collections)) {
        CollectionsListScreen(
            onBack = navigator::goBack,
            onOpenCollection = { navigator.navigate(PebblesKey.CollectionDetail(it.id)) },
        )
    }

    entry<PebblesKey.SoulDetail>(metadata = NavTransitions.forKey(PebblesKey.SoulDetail(""))) { key ->
        SoulDetailScreen(soulId = key.soulId, onBack = navigator::goBack)
    }

    entry<PebblesKey.CollectionDetail>(metadata = NavTransitions.forKey(PebblesKey.CollectionDetail(""))) { key ->
        CollectionDetailScreen(collectionId = key.collectionId, onBack = navigator::goBack)
    }

    entry<PebblesKey.Connections>(metadata = NavTransitions.forKey(PebblesKey.Connections)) {
        ConnectionsScreen(onDismiss = navigator::goBack)
    }

    entry<PebblesKey.Glyphs>(metadata = NavTransitions.forKey(PebblesKey.Glyphs)) {
        GlyphsListScreen(onBack = navigator::goBack)
    }

    entry<PebblesKey.Lab>(metadata = NavTransitions.forKey(PebblesKey.Lab)) {
        LabScreen(onBack = navigator::goBack)
    }

    entry<PebblesKey.Achievements>(metadata = NavTransitions.forKey(PebblesKey.Achievements)) {
        AchievementsScreen(onBack = navigator::goBack)
    }
}
```

**Note on `onOpenSoul`:** `SoulsListScreen` hands back a `SoulWithGlyph`, which
is flat — `id`, `name`, `glyphId`, `glyph`, `pebblesCount`
(`features/profile/models/SoulWithGlyph.kt:8`). So the id is `it.id`, not
`it.soul.id`. Verified before this plan was finalized.

- [ ] **Step 2: Verify it compiles**

Run: `npm run build --workspace=@pbbls/android`
Expected: `BUILD SUCCESSFUL`. `Navigator` does not exist yet, so this will fail — that is Task 5. If you prefer a green build at every step, do Task 5 first and return here; the order in the commit history does not matter as long as both land in this PR.

---

### Task 5: A flat Navigator, and `RootScreen` on `NavDisplay`

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/Navigator.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/RootScreen.kt`

Part 1's `Navigator` wraps a single flat `NavBackStack`. Part 2 replaces its internals with the four per-tab stacks; the *call sites written in Task 4 do not change*, which is the point of introducing the seam now.

- [ ] **Step 1: Write the Navigator**

Create `app/src/main/kotlin/app/pbbls/android/navigation/Navigator.kt`:

```kotlin
package app.pbbls.android.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack

/**
 * The only writer of navigation state (#852).
 *
 * Part 1 backs this with one flat [NavBackStack], preserving the IA exactly as
 * the two NavHosts had it. Part 2 swaps the internals for four per-tab stacks
 * without touching a single call site — which is why the seam exists now rather
 * than arriving with the tabs.
 */
@Stable
class Navigator(private val backStack: NavBackStack<PebblesKey>) {
    fun navigate(key: PebblesKey) {
        backStack.add(key)
    }

    fun goBack() {
        // Never pop the last entry: an empty back stack has nothing to render,
        // and NavDisplay throws rather than closing the app. Letting the system
        // handle back at the root is what exits.
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Clears the stack and seeds [key]. Part 5 drives auth with this. */
    fun replaceAll(key: PebblesKey) {
        backStack.clear()
        backStack.add(key)
    }
}
```

- [ ] **Step 2: Rewrite `RootScreen`'s navigation half**

In `RootScreen.kt`, delete all twelve `private const val ROUTE_*` declarations, the whole `AuthedNavHost` function, and the whole `WelcomeAuthNavHost` function.

Replace the `if (canShowAuthedTabs) { AuthedNavHost(...) ... } else { WelcomeAuthNavHost(...) }` body inside the `Box` with:

```kotlin
        if (canShowAuthedTabs) {
            AuthedNavDisplay(onSignOut = { scope.launch { supabase.signOut() } })
            if (isPresentingOnboarding) {
                OnboardingScreen(
                    steps = OnboardingSteps.all,
                    onFinish = {
                        OnboardingPreferences.setHasSeenOnboarding(context, true)
                        hasSeenOnboarding = true
                        isPresentingOnboarding = false
                    },
                )
            }
            KarmaOverlayHost(service = karma, modifier = Modifier.fillMaxSize())
            AchievementMomentOverlay(
                service = achievementNotify,
                modifier = Modifier.fillMaxSize(),
            )
            connections.pendingInviteToken?.let { token ->
                AcceptInviteScreen(
                    token = token,
                    onDismiss = { connections.pendingInviteToken = null },
                )
            }
        } else {
            WelcomeAuthNavDisplay(contentRevealed = welcomeContentRevealed)
        }
```

Then add the two new hosts at the bottom of the file:

```kotlin
/**
 * Authed navigation (#852). One [NavDisplay] over one saveable back stack,
 * replacing the NavHost. The IA is unchanged from the NavHost it replaces —
 * Part 2 introduces the four-tab bar (D11).
 *
 * `rememberViewModelStoreNavEntryDecorator` is what scopes a `hiltViewModel()`
 * to its entry rather than to the composition that happens to host it, so a
 * popped entry takes its ViewModel with it.
 */
@Composable
private fun AuthedNavDisplay(onSignOut: () -> Unit) {
    val backStack = rememberNavBackStack<PebblesKey>(PebblesKey.Path)
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.goBack() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = entryProvider { pebblesEntries(navigator = navigator, onSignOut = onSignOut) },
    )
}

@Composable
private fun WelcomeAuthNavDisplay(contentRevealed: Boolean) {
    val backStack = rememberNavBackStack<PebblesKey>(PebblesKey.Welcome)
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.goBack() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<PebblesKey.Welcome>(metadata = NavTransitions.forKey(PebblesKey.Welcome)) {
                    WelcomeScreen(
                        contentRevealed = contentRevealed,
                        onCreateAccount = { navigator.navigate(PebblesKey.Auth(AuthMode.SIGNUP)) },
                        onLogin = { navigator.navigate(PebblesKey.Auth(AuthMode.LOGIN)) },
                    )
                }
                entry<PebblesKey.Auth>(metadata = NavTransitions.forKey(PebblesKey.Auth(AuthMode.LOGIN))) { key ->
                    AuthScreen(initialMode = key.mode)
                }
            },
    )
}
```

Fix the imports: remove `androidx.navigation.*` and `app.pbbls.android.features.*Screen` imports that moved to `PebblesEntryProvider.kt`; add

```kotlin
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import app.pbbls.android.navigation.NavTransitions
import app.pbbls.android.navigation.Navigator
import app.pbbls.android.navigation.PebblesKey
import app.pbbls.android.navigation.pebblesEntries
```

Update the KDoc on `RootScreen` — it currently says "otherwise → a NavHost (Welcome → Auth)". Replace "NavHost" with "NavDisplay" in both places.

- [ ] **Step 3: Build**

Run: `npm run build --workspace=@pbbls/android`
Expected: `BUILD SUCCESSFUL`. Iterate on imports until it is.

- [ ] **Step 4: Full check**

Run:
```bash
npm run lint --workspace=@pbbls/android
./gradlew lint
npm run test --workspace=@pbbls/android
```
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/ \
        apps/android/app/src/main/kotlin/app/pbbls/android/RootScreen.kt
git commit -m "$(cat <<'EOF'
feat(android): one NavDisplay replaces both NavHosts (#852)

A move, not a redesign: every ROUTE_* string becomes the key for the same
destination, with the same IA. The four-tab bar arrives in the next part
(D11) so that this diff can be read as the mechanical translation it is.

Two defects fall out along the way. soulId and collectionId are non-null
String fields on their keys, so both .orEmpty() calls are gone; and
AuthMode rides the Auth key as a typed enum rather than a route string
with a silent LOGIN default.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Verify Part 1 on a device, then open the PR

- [ ] **Step 1: Install and walk every route**

```bash
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
cd apps/android && ./gradlew installDebug
```

Walk each of these by hand and confirm the destination is unchanged from `main`:

| From | Action | Expect |
|---|---|---|
| Path | tap profile | Profile |
| Profile | Souls | souls list |
| Souls | tap a soul | that soul's detail |
| Profile | Collections → a collection | that collection's detail |
| Profile | Glyphs / Connections / Lab / Achievements | each list |
| any push | system back | previous screen, now with a shared-axis slide |
| Signed out | Welcome → Create account | Auth in SIGNUP mode |
| Signed out | Welcome → Log in | Auth in LOGIN mode |

The covers (create, detail, settings, forms) still behave exactly as before — they have not been touched.

- [ ] **Step 2: Smoke-test a minified build**

R8 can strip `@Serializable` keys silently, and the failure mode is a crash on restore rather than a build error.

```bash
./gradlew assembleRelease && ./gradlew installRelease
```

Open the app, push to a soul detail, background it, and return. If it crashes with a serializer error, add to `app/proguard-rules.pro`:

```
# Navigation 3 keys are resolved by their generated serializers at restore time.
-keep,includedescriptorclasses class app.pbbls.android.navigation.** { *; }
-keepclassmembers class app.pbbls.android.navigation.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
```

- [ ] **Step 3: Confirm process-death restoration is still absent for covers**

This is a *negative* check that documents the baseline Part 4 fixes. Open a pebble detail cover, then:

```bash
adb shell am kill app.pbbls.android
```

Reopen from the launcher. You should land on the **Path root** with the cover gone. That is expected in Part 1 — write it in the PR body so a reviewer does not read it as a regression.

- [ ] **Step 4: Open the PR**

```bash
gh pr create --base main \
  --title "feat(android): one NavDisplay and typed keys replace both NavHosts (#852)" \
  --label feat --label android --label core --label ui \
  --milestone "M61 · Android Refacto"
```

Body starts with `Resolves #852` **only on the final part (6)**. Parts 1–5 use `Part N of #852` and do **not** use `Resolves`/`Closes`, so the issue stays open until the stack lands.

**Never write a Kritik finding id (`F-…`) in the body.** The Arkaik App resolves every id it sees, and this PR closes none of them.

Include a Lab Note? **No** — this part is invisible to users. Delete the `## Lab Note (EN/FR)` section from the body and add the `no-lab-note` label. Parts 2 and 4 do ship a visible change and need one.

---

# Part 2 — The four-tab NavigationBar

**Branch:** `feat/852-navigation-bar-tabs` (`gh stack` on top of part 1)

**What ships:** `NavigationState` + the per-tab back stacks, the M3 bar, the Path FAB, and the IA change (D1–D4). This is the part users see.

---

### Task 7: `NavigationState`

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/NavigationState.kt`

- [ ] **Step 1: Write it**

```kotlin
package app.pbbls.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Per-tab back stacks (#852, D4), modelled on the AndroidX `multiplestacks`
 * recipe.
 *
 * Each tab owns a [NavBackStack], which is saveable — so process-death
 * restoration is a property of this container rather than something each screen
 * re-implements. Each tab also owns its own `SaveableStateHolder` decorator, so
 * a tab's scroll position survives a trip to another tab.
 *
 * This class never modifies itself. [Navigator] is the only writer.
 */
@Stable
class NavigationState(
    val startRoute: PebblesKey,
    topLevelRoute: MutableState<PebblesKey>,
    val backStacks: Map<PebblesKey, NavBackStack<PebblesKey>>,
) {
    var topLevelRoute: PebblesKey by topLevelRoute

    /** The stack the user is currently in. Modals are pushed here (D6). */
    val currentStack: NavBackStack<PebblesKey>
        get() = backStacks[topLevelRoute] ?: error("No stack for $topLevelRoute")

    /** The key on top of everything — what the bar check reads. */
    val topKey: PebblesKey
        get() = currentStack.last()

    /**
     * "Exit through home" (D4): the start route is always first, and at most one
     * other tab is active. Back therefore unwinds the current tab, falls back to
     * Path, then exits — and the back path cannot grow without bound however
     * much the user taps around the bar.
     *
     * A tab that is not in use still RETAINS its stack; it is simply not in the
     * back path.
     */
    fun topLevelRoutesInUse(): List<PebblesKey> = if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)

    @Composable
    fun toDecoratedEntries(entryProvider: (PebblesKey) -> NavEntry<PebblesKey>): List<NavEntry<PebblesKey>> {
        val decorated =
            backStacks.mapValues { (_, stack) ->
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = entryProvider,
                )
            }
        return topLevelRoutesInUse().flatMap { decorated[it].orEmpty() }
    }
}

@Composable
fun rememberNavigationState(
    startRoute: PebblesKey = PebblesKey.Path,
    tabs: List<PebblesKey> = PebblesKey.tabs,
): NavigationState {
    val topLevelRoute =
        rememberSerializable(
            startRoute,
            tabs,
            serializer = MutableStateSerializer(NavKeySerializer()),
        ) { mutableStateOf<PebblesKey>(startRoute) }

    val backStacks = tabs.associateWith { key -> rememberNavBackStack<PebblesKey>(key) }

    return remember(startRoute, tabs) {
        NavigationState(startRoute = startRoute, topLevelRoute = topLevelRoute, backStacks = backStacks)
    }
}
```

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/android`
Expected: `BUILD SUCCESSFUL`.

`rememberSerializable` and `MutableStateSerializer` come from `androidx.savedstate`, which arrives transitively with `lifecycle-viewmodel-savedstate`. If they do not resolve, add `androidx-savedstate-compose` to the catalog rather than hand-rolling a `Saver` — a hand-rolled one will silently drop the tab on process death.

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/NavigationState.kt
git commit -m "$(cat <<'EOF'
feat(android): per-tab back stacks with exit-through-home (#852)

Four saveable NavBackStacks, one per tab, each with its own
SaveableStateHolder so a tab's scroll survives a trip elsewhere. At most
two are in the back path at a time (start + current), so back unwinds the
current tab, falls back to Path, then exits — and the back depth stays
bounded however much the user taps the bar.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: `Navigator` over the tabs — test first

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/Navigator.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/navigation/NavigatorTest.kt`

This is the part with all the judgement in it, so it is the part that gets tested hardest.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/pbbls/android/navigation/NavigatorTest.kt`:

```kotlin
package app.pbbls.android.navigation

import androidx.navigation3.runtime.NavBackStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The navigation rules from the spec (D4), stated as tests.
 *
 * These run on the JVM against a hand-built NavigationState rather than through
 * Compose, because the rules are pure list arithmetic and the Compose layer adds
 * nothing but a harness.
 */
class NavigatorTest {
    private lateinit var state: NavigationState
    private lateinit var navigator: Navigator

    @Before
    fun setUp() {
        state =
            NavigationState(
                startRoute = PebblesKey.Path,
                topLevelRoute = androidx.compose.runtime.mutableStateOf(PebblesKey.Path),
                backStacks = PebblesKey.tabs.associateWith { NavBackStack(it) },
            )
        navigator = Navigator(state)
    }

    @Test
    fun `navigating to a tab switches rather than pushing`() {
        navigator.navigate(PebblesKey.People)

        assertEquals(PebblesKey.People, state.topLevelRoute)
        assertEquals(listOf<PebblesKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
    }

    @Test
    fun `navigating to a non-tab pushes onto the current tab`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))

        assertEquals(
            listOf(PebblesKey.People, PebblesKey.SoulDetail("s1")),
            state.backStacks[PebblesKey.People]!!.toList(),
        )
        // Path's stack is untouched.
        assertEquals(listOf<PebblesKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }

    @Test
    fun `back unwinds the current tab before leaving it`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))
        navigator.navigate(PebblesKey.SoulForm("s1"))

        navigator.goBack()
        assertEquals(PebblesKey.SoulDetail("s1"), state.topKey)

        navigator.goBack()
        assertEquals(PebblesKey.People, state.topKey)
    }

    @Test
    fun `back at a tab root falls back to the start route`() {
        navigator.navigate(PebblesKey.People)

        navigator.goBack()

        assertEquals(PebblesKey.Path, state.topLevelRoute)
        // People's stack is RETAINED, just no longer in the back path.
        assertEquals(listOf<PebblesKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
    }

    @Test
    fun `back at the start root is a no-op so the system can exit`() {
        navigator.goBack()

        assertEquals(PebblesKey.Path, state.topLevelRoute)
        assertEquals(listOf<PebblesKey>(PebblesKey.Path), state.backStacks[PebblesKey.Path]!!.toList())
    }

    @Test
    fun `at most start plus current are in the back path`() {
        navigator.navigate(PebblesKey.You)
        navigator.navigate(PebblesKey.People)

        // You is skipped: the back path is bounded (D4).
        assertEquals(listOf(PebblesKey.Path, PebblesKey.People), state.topLevelRoutesInUse())
    }

    @Test
    fun `a visited tab retains its stack even when out of the back path`() {
        navigator.navigate(PebblesKey.You)
        navigator.navigate(PebblesKey.Glyphs)
        navigator.navigate(PebblesKey.People)

        assertEquals(
            listOf(PebblesKey.You, PebblesKey.Glyphs),
            state.backStacks[PebblesKey.You]!!.toList(),
        )
    }

    @Test
    fun `reselecting the current tab pops it to its root`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))
        navigator.navigate(PebblesKey.SoulForm("s1"))

        navigator.onReselect(PebblesKey.People)

        assertEquals(listOf<PebblesKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
        assertEquals(PebblesKey.People, state.topLevelRoute)
    }

    @Test
    fun `replaceAll clears every stack and seeds the target`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulDetail("s1"))
        navigator.navigate(PebblesKey.You)
        navigator.navigate(PebblesKey.Glyphs)

        navigator.replaceAll(PebblesKey.Welcome)

        assertEquals(PebblesKey.Path, state.topLevelRoute)
        assertEquals(listOf<PebblesKey>(PebblesKey.Welcome), state.backStacks[PebblesKey.Path]!!.toList())
        assertEquals(listOf<PebblesKey>(PebblesKey.People), state.backStacks[PebblesKey.People]!!.toList())
        assertEquals(listOf<PebblesKey>(PebblesKey.You), state.backStacks[PebblesKey.You]!!.toList())
        assertEquals(listOf<PebblesKey>(PebblesKey.Collections), state.backStacks[PebblesKey.Collections]!!.toList())
    }

    @Test
    fun `a modal pushed on a tab keeps the bar invariant intact`() {
        navigator.navigate(PebblesKey.People)
        navigator.navigate(PebblesKey.SoulForm(null))

        // D6: the top key is modal, so the bar is hidden, so the user cannot
        // switch tabs from here — which is what guarantees no OTHER tab's stack
        // can ever have a modal on top of it.
        assertTrue(state.topKey !is BarKey)
        PebblesKey.tabs.filter { it != PebblesKey.People }.forEach {
            assertTrue(state.backStacks[it]!!.last() is BarKey)
        }
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `npm run test --workspace=@pbbls/android -- --tests '*NavigatorTest*'`
Expected: compilation failure — `Navigator` takes a `NavBackStack`, not a `NavigationState`.

- [ ] **Step 3: Rewrite the Navigator**

Replace the whole body of `navigation/Navigator.kt`:

```kotlin
package app.pbbls.android.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The only writer of [NavigationState] (#852).
 *
 * Part 1 backed this with one flat stack; the call sites written then are
 * unchanged, which is what the seam was for.
 */
@Stable
class Navigator(val state: NavigationState) {
    private val _reselectEvents = MutableSharedFlow<PebblesKey>(extraBufferCapacity = 1)

    /** Emitted when a tab is reselected, for screens that also reset scroll. */
    val reselectEvents = _reselectEvents.asSharedFlow()

    /**
     * A [TopLevelKey] switches tab; anything else pushes onto the current tab's
     * own stack (D6).
     */
    fun navigate(key: PebblesKey) {
        if (key is TopLevelKey) {
            state.topLevelRoute = key
        } else {
            state.currentStack.add(key)
        }
    }

    /**
     * Unwind the current tab, then fall back to the start route, then let the
     * system exit (D4). Popping the start route's last entry is deliberately a
     * no-op: an empty stack has nothing to render.
     */
    fun goBack() {
        val stack = state.currentStack
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else if (state.topLevelRoute != state.startRoute) {
            state.topLevelRoute = state.startRoute
        }
    }

    /** Reselecting a tab pops it to its root and signals anyone resetting scroll. */
    fun onReselect(key: PebblesKey) {
        val stack = state.backStacks[key] ?: return
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        state.topLevelRoute = key
        _reselectEvents.tryEmit(key)
    }

    /**
     * Resets every tab to its root, returns to the start tab, and seeds [key]
     * there. Part 5 drives the auth switch with this: sign-out must not leave a
     * signed-in user's stack sitting under the Welcome screen.
     */
    fun replaceAll(key: PebblesKey) {
        state.backStacks.forEach { (tab, stack) ->
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            if (stack.isNotEmpty()) stack[0] = tab
        }
        state.topLevelRoute = state.startRoute
        val start = state.backStacks.getValue(state.startRoute)
        start[0] = key
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `npm run test --workspace=@pbbls/android -- --tests '*NavigatorTest*'`
Expected: PASS, 10 tests. If `NavBackStack(it)` is not a valid constructor on the JVM, substitute whatever `rememberNavBackStack` wraps — check with `./gradlew :app:dependencies` and read the artifact. Do **not** replace `NavBackStack` with a plain `mutableStateListOf` in the test: the test would then stop exercising the type the production code uses.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/Navigator.kt \
        apps/android/app/src/test/kotlin/app/pbbls/android/navigation/NavigatorTest.kt
git commit -m "$(cat <<'EOF'
feat(android): Navigator over per-tab stacks, with tests (#852)

Ten tests state the D4 rules: tabs switch rather than push, modals land on
the current tab, back unwinds before leaving a tab, back at a tab root
falls back to Path, back at Path is a no-op so the system exits, the back
path is bounded at start-plus-current, an out-of-path tab keeps its stack,
reselect pops to root, and replaceAll clears everything.

The last test pins the D6 invariant: a modal is only ever on top of the
CURRENT tab, because the bar is hidden on modals and the bar is the only
way to switch.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: The NavigationBar

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesNavigationBar.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`
- Modify: `apps/android/app/src/main/res/values-fr/strings.xml`

- [ ] **Step 1: Add the four labels**

Per the repo's formatting-sensitive-catalogs rule, **insert these at the right anchor as text; do not rewrite the file.** Find the navigation or profile block in each and add:

`values/strings.xml`:
```xml
    <!-- Bottom navigation bar (#852) -->
    <string name="tab_path">Path</string>
    <string name="tab_people">People</string>
    <string name="tab_collections">Collections</string>
    <string name="tab_you">You</string>
```

`values-fr/strings.xml`:
```xml
    <!-- Barre de navigation (#852) -->
    <string name="tab_path">Chemin</string>
    <string name="tab_people">Proches</string>
    <string name="tab_collections">Collections</string>
    <string name="tab_you">Toi</string>
```

- [ ] **Step 2: Write the bar**

```kotlin
package app.pbbls.android.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R

/**
 * The four top-level destinations (#852, D2).
 *
 * Tapping a tab you are not on switches to it, retaining that tab's stack;
 * tapping the one you ARE on pops it to its root, which is the standard M3
 * escape hatch and the only reliable way out of a deep stack.
 */
@Composable
fun PebblesNavigationBar(
    current: PebblesKey,
    onSelect: (PebblesKey) -> Unit,
    onReselect: (PebblesKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        PebblesKey.tabs.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { if (selected) onReselect(tab) else onSelect(tab) },
                icon = { Icon(imageVector = tab.icon(), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes())) },
            )
        }
    }
}
```

Pick the four icons from whatever icon set the app already uses. Check first:

```bash
grep -rn "Icons\.\|painterResource(R.drawable" apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ | head
```

If the app uses its own drawables (likely, given the design system), add `tab.icon()` as a `@Composable` returning `painterResource(...)` and adjust `Icon` accordingly. **Do not introduce `material-icons-extended`** for this — it is a large dependency and the app has its own visual language.

Add the two extension functions at the bottom of the file:

```kotlin
private fun PebblesKey.labelRes(): Int =
    when (this) {
        PebblesKey.Path -> R.string.tab_path
        PebblesKey.People -> R.string.tab_people
        PebblesKey.Collections -> R.string.tab_collections
        PebblesKey.You -> R.string.tab_you
        else -> error("$this is not a tab")
    }
```

- [ ] **Step 3: Build and commit**

```bash
npm run build --workspace=@pbbls/android
git add apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesNavigationBar.kt \
        apps/android/app/src/main/res/values/strings.xml \
        apps/android/app/src/main/res/values-fr/strings.xml
git commit -m "$(cat <<'EOF'
feat(android): the four-tab M3 NavigationBar (#852)

Path / People / Collections / You, EN and FR. Tapping the active tab pops
it to its root.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: Wire the bar into `RootScreen`

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/RootScreen.kt`

- [ ] **Step 1: Replace `AuthedNavDisplay`**

```kotlin
@Composable
private fun AuthedNavDisplay(onSignOut: () -> Unit) {
    val state = rememberNavigationState()
    val navigator = remember(state) { Navigator(state) }
    val topKey = state.topKey

    Scaffold(
        bottomBar = {
            // D5: bar visibility is read straight off the stack, so it cannot
            // drift out of sync with what is on screen.
            if (topKey is BarKey) {
                PebblesNavigationBar(
                    current = state.topLevelRoute,
                    onSelect = navigator::navigate,
                    onReselect = navigator::onReselect,
                )
            }
        },
        floatingActionButton = {
            // D3: the pinned "New pebble" button used to occupy exactly the
            // space the bar now takes. Path only — no other tab has a create action.
            if (topKey == PebblesKey.Path) {
                NewPebbleFab(
                    onClick = { navigator.navigate(PebblesKey.RecordFlow()) },
                    onLongClick = { navigator.navigate(PebblesKey.CreatePebble()) },
                )
            }
        },
        containerColor = PebblesTheme.colors.system.background,
    ) { padding ->
        NavDisplay(
            entries = state.toDecoratedEntries(entryProvider = entryProvider { pebblesEntries(navigator, onSignOut) }),
            onBack = { navigator.goBack() },
            modifier = Modifier.padding(padding),
        )
    }
}
```

**The `NavDisplay(entries = …)` overload** is what takes pre-decorated entries; the `backStack = …` overload used in Part 1 decorates internally and cannot express per-tab decorators. Confirm the parameter name against the 1.1.7 artifact before assuming it is `entries`.

- [ ] **Step 2: Move the FAB out of `PathScreen`**

In `PathScreen.kt`, find the pinned "New pebble" block around line 316 and cut it into a new file `features/path/components/NewPebbleFab.kt`, keeping the tap/long-press pair:

```kotlin
package app.pbbls.android.features.path.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R

/**
 * "New pebble" (#852, D3). Was a pinned bar at the bottom of the timeline —
 * exactly where the NavigationBar now lives — so it became a FAB on the Path
 * tab.
 *
 * Tap opens the record flow, long-press the all-at-once form (M58 D1). That
 * pair is preserved verbatim; only the affordance moved.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewPebbleFab(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    ExtendedFloatingActionButton(
        onClick = {},
        modifier =
            modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        interactionSource = interaction,
    ) {
        Text(stringResource(R.string.path_new_pebble))
    }
}
```

Check the existing string name before using `R.string.path_new_pebble`:

```bash
grep -rn "new_pebble" apps/android/app/src/main/res/values/strings.xml
```

Remove `onCreatePebble` / `onCreatePebbleLongPress` from `PathScreen`'s internal timeline call, and remove the bottom inset the pinned button needed.

- [ ] **Step 3: Build, lint, test**

```bash
npm run build --workspace=@pbbls/android
npm run lint --workspace=@pbbls/android
./gradlew lint
npm run test --workspace=@pbbls/android
```

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/RootScreen.kt \
        apps/android/app/src/main/kotlin/app/pbbls/android/features/path/
git commit -m "$(cat <<'EOF'
feat(android): the bar goes up, New pebble becomes a FAB (#852)

Bar visibility is `topKey is BarKey`, read off the stack, so it cannot
disagree with what is on screen. The pinned New pebble button occupied
exactly the space the bar now takes, so it moved to a Path-only FAB,
keeping tap-for-flow and long-press-for-form (M58 D1).

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Redistribute Profile's children

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/ProfileScreen.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileSoulsCard.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/components/ProfileCollectionsCard.kt`

- [ ] **Step 1: Retarget the People and Collections entries**

In `PebblesEntryProvider.kt`, the `People` and `Collections` entries already render `SoulsListScreen` and `CollectionsListScreen`. Now that they are tab roots, their `onBack` is meaningless — a tab root has nothing to go back to within its own stack. Change both to hide the back affordance.

`SoulsListScreen` and `CollectionsListScreen` each take a non-optional `onBack: () -> Unit`. Make it nullable so a tab root can say "no back button":

```kotlin
fun SoulsListScreen(
    onBack: (() -> Unit)?,
    ...
```

and render the back button only when non-null. Do the same for `CollectionsListScreen`. Then:

```kotlin
    entry<PebblesKey.People>(metadata = NavTransitions.forKey(PebblesKey.People)) {
        SoulsListScreen(
            onBack = null,
            onOpenSoul = { navigator.navigate(PebblesKey.SoulDetail(it.id)) },
        )
    }
```

- [ ] **Step 2: Strip the moved entry points from Profile**

`ProfileScreen` loses three parameters — `onOpenSouls`, `onOpenCollections`, `onOpenConnections` — because those surfaces are now one tap away in the bar and a second route into the same place is a maintenance trap. Its signature becomes:

```kotlin
fun ProfileScreen(
    onSignOut: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenGlyphs: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenAchievements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
```

`onBack` goes too: `You` is a tab root.

In `ProfileSoulsCard`, remove `onOpenSouls` and `onOpenConnections`. In `ProfileCollectionsCard`, remove `onOpenList` but **keep** `onOpenCollection` — tapping a specific collection from the profile hub still makes sense and lands on `CollectionDetail` within the `You` stack.

Update the `You` entry to match.

- [ ] **Step 3: Update the screenshot previews**

Any `@PreviewTest` that constructs `ProfileScreen`, `ProfileSoulsCard` or `ProfileCollectionsCard` now has wrong parameters.

```bash
grep -rln "ProfileScreen\|ProfileSoulsCard\|ProfileCollectionsCard" apps/android/app/src/screenshotTest/
```

Fix each. Then re-baseline, because the cards genuinely changed:

```bash
./gradlew updateDebugScreenshotTest
./gradlew validateDebugScreenshotTest
```

Inspect the regenerated PNGs before committing them — a re-baseline that hides an unintended layout break is worse than a failing test.

- [ ] **Step 4: Build, lint, test, commit**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
git add apps/android/app/src/main/kotlin/app/pbbls/android/ apps/android/app/src/screenshotTest/
git commit -m "$(cat <<'EOF'
feat(android): Souls and Collections become tabs, not Profile children (#852)

ProfileScreen loses onOpenSouls, onOpenCollections, onOpenConnections and
onBack: those surfaces are one tap away in the bar now, and a second route
into the same place is a trap. Tapping a specific collection from the hub
still works and lands within the You stack.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: Decision-log entries

**Files:**
- Modify: `docs/decisions/log.md` (append only — never edit a prior entry)

- [ ] **Step 1: Append both entries**

Append to the end of `docs/decisions/log.md`. Match the existing entry format exactly: `## YYYY-MM-DD — Title (#issue)` then the Status / Scope / Context / Decision / Why / Consequences / Supersedes / Refs fields.

Entry 1 — the IA choice and the deliberate mirror break. It must state: the four tabs and what moved; that this breaks the standing 1:1 iOS rule on purpose; that the rule's actual target (schema, RPC payloads, cross-surface semantics) is untouched; the cost (two IAs to reason about for every future surface); and the iOS follow-up issue number from Step 2.

Entry 2 — the supersession of M38 D5. It must state: that "modal surfaces stay conditionally-composed covers" no longer holds; that every full-screen cover is now a nav entry; and that D5's sibling decisions (D1's push IA for the authed tree, D9's z-order) are consumed by this too. Mark it **Supersedes: D5** in
`docs/superpowers/specs/2026-07-10-android-bootstrap-design.md`.

- [ ] **Step 2: File the iOS follow-up issue**

```bash
gh issue create \
  --title "[Feat] Decide whether iOS adopts a TabView to match Android's four-tab IA" \
  --label feat --label ios --label ui --label core \
  --body "Android moved to an M3 NavigationBar with four tabs (Path / People / Collections / You) and per-tab back stacks in #852, deliberately breaking the standing 1:1 iOS/Android mirror at the navigation layer. See the decision log entry of 2026-09-20 and docs/superpowers/specs/2026-09-20-android-nav3-design.md D1.

This issue decides iOS's answer: adopt a TabView and converge, or keep the push IA and accept that the two surfaces navigate differently. Not a bug — a deliberate fork that needs a deliberate resolution."
```

Put the issue number into Entry 1 before committing.

- [ ] **Step 3: Commit**

```bash
git add docs/decisions/log.md
git commit -m "$(cat <<'EOF'
docs: the Android four-tab IA, and D5 superseded (#852)

Two appended entries: the bottom-bar IA as a deliberate break from the 1:1
iOS mirror rule (with the iOS follow-up filed), and the supersession of
M38 D5's "modal surfaces stay conditionally-composed covers".

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 13: Update the Arkaik map

The IA changed, which is exactly what the `arkaik` skill exists for.

- [ ] **Step 1: Confirm the MCP tools are available**

The map is **hosted**. `docs/arkaik/bundle.json` is a frozen snapshot and must not be edited — editing it and running `arkaik restore` has already silently deleted this project's federation feed once ([arkaik#423](https://github.com/alexisbohns/arkaik/issues/423)).

If `mcp__arkaik-mcp__*` tools are not available, **say so and stop this task**. Do not fall back to the file. In a container the usual cause is a missing `ARKAIK_TOKEN`.

- [ ] **Step 2: Move the nodes**

Using `mcp__arkaik-mcp__list_nodes` and `mcp__arkaik-mcp__update_node`:

- Add view nodes for the four tabs if they do not exist; set the `android` platform status of the ones this stack is building to `development`.
- Re-parent the souls / collections / connections view nodes from the Profile view to their new tab roots with `add_edge` / `remove_edge`.
- Record the new `LabAnnouncement` and `LabLogList` views.

Note the node ids returned — they are what the Part 2 and Part 4 Lab Notes put in `nodes:`.

- [ ] **Step 3: Nothing to commit**

Hosted mutations emit their own journal events. There is no repo change for this task.

---

### Task 14: Verify Part 2 and open the PR

- [ ] **Step 1: Walk the tab behavior on a device**

```bash
./gradlew installDebug
```

| Check | Expect |
|---|---|
| Launch | Path, bar visible, FAB visible |
| Tap People, then a soul | soul detail, **bar still visible** |
| Tap People again (reselect) | back at the souls list |
| From People root, press back | Path |
| From Path root, press back | app exits |
| Path → You → People → back | **Path** (You is skipped, D4) |
| You → Glyphs, switch to People, switch back to You | still on Glyphs — the stack was retained |
| Scroll Path, go to Collections, come back | scroll position retained |
| Rotate on any tab | stack and scroll survive |
| Kill the process on People › SoulDetail and relaunch | **back on that soul's detail** |

That last row is the acceptance criterion this part delivers — per-tab stacks are saveable, so it should already hold for pushes even though the covers are not promoted until parts 3–4.

- [ ] **Step 2: Open the PR with a Lab Note**

This part is highly visible, so the PR body needs a `## Lab Note (EN/FR)` section with exactly one ```yaml fence. Double-quote every title and summary. No em dashes in either language. French uses "Tu" and is an adaptation, not a translation.

```yaml
species: feature
platform: android
status: in_progress
published: false
en:
  title: "A new way around the app"
  summary: "Your path, your people, your collections and your profile now each have their own tab at the bottom of the screen. Everything keeps its place, so you can jump away and come back to exactly where you were."
fr:
  title: "Une nouvelle façon de circuler"
  summary: "Ton chemin, tes proches, tes collections et ton profil ont maintenant chacun leur onglet en bas de l'écran. Chaque onglet garde sa place, donc tu peux partir et revenir exactement là où tu étais."
nodes: [V-path, V-souls-list, V-collections-list, V-profile]
suggested:
  molecule: pbbls
  type: feature
  tags: [changelog]
```

Replace the `nodes:` ids with the real ones from Task 13. An id that matches nothing is dropped and reported in the App's delivery response.

```bash
gh pr create --base feat/852-navigation3-migration \
  --title "feat(android): a four-tab NavigationBar with per-tab back stacks (#852)" \
  --label feat --label android --label core --label ui \
  --milestone "M61 · Android Refacto"
```

---

# Part 3 — Profile-side covers become entries

**Branch:** `feat/852-profile-covers-as-entries`

**What ships:** `SoulForm`, `CollectionForm`, `Settings`, `GlyphCarve`, `Invite`, `LabAnnouncement`, `LabLogList` become entries. Seven `BackHandler`s die. Four of the six `enabled = !isSaving` fall-through bugs (D9) are fixed.

---

### Task 15: Forms load by id

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulFormScreen.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SoulFormViewModel.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/CollectionFormScreen.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/CollectionFormViewModel.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/SoulFormViewModelTest.kt`

`SoulFormScreen(original: SoulWithGlyph?)` takes a whole object handed down from the parent. A nav key can only carry an id, so the form must load its own subject. **This is the single most important behavioural change in Part 3** — the screen becoming self-sufficient is precisely what lets it survive process death.

- [ ] **Step 1: Write the failing test**

Add to `SoulFormViewModelTest.kt` (create it if absent, following `AchievementsViewModelTest` as the reference shape):

```kotlin
    @Test
    fun `a soulId loads the soul into the form`() =
        runTest(rule.dispatcher) {
            val souls = FakeSoulsService().apply { seed(soul(id = "s1", name = "Ada")) }
            val vm = SoulFormViewModel(souls, SavedStateHandle(mapOf("soulId" to "s1")))

            advanceUntilIdle()

            val content = vm.uiState.value as SoulFormUiState.Content
            assertEquals("Ada", content.name)
        }

    @Test
    fun `a null soulId opens an empty create form without loading`() =
        runTest(rule.dispatcher) {
            val souls = FakeSoulsService()
            val vm = SoulFormViewModel(souls, SavedStateHandle(mapOf("soulId" to null)))

            advanceUntilIdle()

            val content = vm.uiState.value as SoulFormUiState.Content
            assertEquals("", content.name)
            assertEquals(0, souls.loadByIdCallCount)
        }
```

- [ ] **Step 2: Run it, confirm it fails**

Run: `npm run test --workspace=@pbbls/android -- --tests '*SoulFormViewModelTest*'`
Expected: FAIL — the ViewModel has no `SavedStateHandle` constructor parameter.

- [ ] **Step 3: Make the ViewModel load by id**

Give `SoulFormViewModel` a `SavedStateHandle`, read `soulId` from it, and load when non-null. Add a `loadById` to `SoulsServicing` and `FakeSoulsService` if one is not there. Keep the existing save path untouched.

Change `SoulFormScreen`'s signature from `original: SoulWithGlyph?` to no subject parameter at all — the ViewModel has it.

Do the same for `CollectionFormViewModel` / `CollectionFormScreen` with `collectionId`.

- [ ] **Step 4: Run the tests**

Run: `npm run test --workspace=@pbbls/android -- --tests '*FormViewModelTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/ \
        apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/
git commit -m "$(cat <<'EOF'
feat(android): the soul and collection forms load their own subject (#852)

Both took the whole object from their parent, which is why neither could
survive process death: nothing on disk said what was being edited. They
now read an id from SavedStateHandle and load it, which is what makes them
promotable to nav entries.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 16: Settings loads its own profile

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsScreen.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsViewModel.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/SettingsViewModelTest.kt`

`SettingsScreen` takes **five** initial values from `ProfileScreen`'s already-loaded state (`initialDisplayName`, `initialGlyphId`, `initialGlyphStrokes`, `email`, `providers`, plus `initialHandle` and `initialPublicProfile`). As an entry it has no parent to take them from.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `settings loads the profile itself rather than taking it from a parent`() =
        runTest(rule.dispatcher) {
            val profiles = FakeProfileService().apply { seed(profile(displayName = "Ada", handle = "ada")) }
            val vm = SettingsViewModel(profiles, /* other existing deps */)

            advanceUntilIdle()

            val content = vm.uiState.value as SettingsUiState.Content
            assertEquals("Ada", content.form.displayName)
            assertEquals("ada", content.form.handle)
        }
```

- [ ] **Step 2: Run it, confirm it fails**

Run: `npm run test --workspace=@pbbls/android -- --tests '*SettingsViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

`SettingsViewModel` gains a load in `init` that populates the form from `ProfileServicing` (and the email/providers from `SupabaseServicing`, which `ProfileViewModel` already reads since #849). `SettingsScreen` drops all seven initial-value parameters and keeps `onDismiss` / `onSaved`.

`ProfileViewModel` loses `isPresentingSettings`, `openSettings` and `closeSettings`.

- [ ] **Step 4: Run, then commit**

```bash
npm run test --workspace=@pbbls/android -- --tests '*SettingsViewModelTest*'
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/ \
        apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/
git commit -m "$(cat <<'EOF'
feat(android): Settings loads its own profile (#852)

It took seven initial values from ProfileScreen's loaded state, which is
no state at all once it is a nav entry reachable directly. It loads them
now, which is also what makes a deep link to Settings possible later.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 17: Promote the five profile-side covers

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt`
- Modify: `ProfileScreen.kt`, `SoulsListScreen.kt`, `SoulDetailScreen.kt`, `CollectionsListScreen.kt`, `CollectionDetailScreen.kt`, `ConnectionsScreen.kt`, `GlyphsListScreen.kt`
- Modify: their ViewModels

- [ ] **Step 1: Add the entries**

In `PebblesEntryProvider.kt`:

```kotlin
    entry<PebblesKey.SoulForm>(metadata = NavTransitions.forKey(PebblesKey.SoulForm())) {
        SoulFormScreen(onDismiss = navigator::goBack, onSaved = navigator::goBack)
    }

    entry<PebblesKey.CollectionForm>(metadata = NavTransitions.forKey(PebblesKey.CollectionForm())) {
        CollectionFormScreen(onDismiss = navigator::goBack, onSaved = navigator::goBack)
    }

    entry<PebblesKey.Settings>(metadata = NavTransitions.forKey(PebblesKey.Settings)) {
        SettingsScreen(onDismiss = navigator::goBack, onSaved = { navigator.goBack() })
    }

    entry<PebblesKey.GlyphCarve>(metadata = NavTransitions.forKey(PebblesKey.GlyphCarve)) {
        GlyphCarveScreen(onSaved = { navigator.goBack() }, onCancel = navigator::goBack)
    }

    entry<PebblesKey.Invite>(metadata = NavTransitions.forKey(PebblesKey.Invite)) {
        InviteScreen(onDismiss = navigator::goBack)
    }
```

- [ ] **Step 2: Delete the covers and their flags**

For each of these, delete the `if (covers.isPresentingX) { XScreen(...) }` block and replace the call that set the flag with a `navigate`:

| File | Cover removed | `openX()` becomes |
|---|---|---|
| `ProfileScreen.kt` | `CollectionFormScreen`, `SettingsScreen` | `navigator.navigate(CollectionForm())` / `navigate(Settings)` via a new `onOpenSettings` lambda parameter |
| `SoulsListScreen.kt` | `SoulFormScreen` | `onCreateSoul` lambda → `navigate(SoulForm())` |
| `SoulDetailScreen.kt` | `SoulFormScreen` | `onEditSoul` lambda → `navigate(SoulForm(soulId))` |
| `CollectionsListScreen.kt` | `CollectionFormScreen` | `onCreateCollection` → `navigate(CollectionForm())` |
| `CollectionDetailScreen.kt` | `CollectionFormScreen` | `onEditCollection` → `navigate(CollectionForm(collectionId))` |
| `ConnectionsScreen.kt` | `InviteScreen` | `onOpenInvite` → `navigate(Invite)` |
| `GlyphsListScreen.kt` | `GlyphCarveScreen` | `onCarve` → `navigate(GlyphCarve)` |

Delete from each ViewModel the corresponding `isPresentingX` field, `openX()`, `closeX()` and any `onXSaved` that only closed the cover. Where `onXSaved` *also* reloaded a list, keep the reload and move it to the resume refresh in Task 18.

- [ ] **Step 3: Delete seven `BackHandler`s**

Remove the `BackHandler` from: `SoulFormScreen:84`, `CollectionFormScreen:81`, `SettingsScreen:116`, `GlyphCarveScreen:101`, `InviteScreen:78`, `ConnectionsScreen:58`, and `LogListScreen:75` (Task 19 handles Lab's other one).

Each of these had `enabled = !uiState.isSaving`, which did not block back at all — it declined to handle it, so back fell through to the host (D9). Where the intent was genuinely "refuse back while saving", express it as an **enabled** handler that consumes:

```kotlin
NavigationBackHandler(enabled = uiState.isSaving) { /* consume — a save is in flight */ }
```

Only add that where the screen actually needs it; for the forms, letting the user leave mid-save is fine because the write runs in `viewModelScope` and completes regardless.

- [ ] **Step 4: Build, lint, test**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
```

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/
git commit -m "$(cat <<'EOF'
feat(android): five profile-side covers become nav entries (#852)

SoulForm, CollectionForm, Settings, GlyphCarve and Invite are entries, so
each survives process death, animates, and can be navigated to.

Seven BackHandlers go. Four of them read `enabled = !isSaving`, which
never blocked back: a disabled handler declines the event and it falls
through to the host, so "ignore back while saving" actually meant "leave
the screen while saving" (D9).

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 18: Parents refresh on resume

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/ui/ResumeRefresh.kt`
- Modify: the list screens that used to reload in an `onSaved` callback

The covers used to tell their parent "I saved, reload". An entry has no parent to tell, and the parent entry outlives the trip (D10) — so it refreshes when it comes back.

- [ ] **Step 1: Write the helper**

```kotlin
package app.pbbls.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Runs [block] every time this entry becomes RESUMED, skipping the first pass
 * when [skipInitial] is true (#852, D10).
 *
 * A nav entry outlives a trip to a child, so a list that used to reload from a
 * cover's `onSaved` callback reloads here instead. This is the #849 lesson —
 * a back-stack entry that outlives a trip to a child needs a refresh when it
 * comes back — applied to entries rather than to NavHost destinations.
 */
@Composable
fun RefreshOnResume(
    skipInitial: Boolean = true,
    block: suspend () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        var first = true
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (first && skipInitial) {
                first = false
            } else {
                first = false
                block()
            }
        }
    }
}
```

- [ ] **Step 2: Use it where a cover used to report a save**

In `SoulsListScreen`, `CollectionsListScreen`, `SoulDetailScreen`, `CollectionDetailScreen`, `ConnectionsScreen` and `GlyphsListScreen`, add:

```kotlin
    RefreshOnResume { viewModel.reload() }
```

Use whatever each ViewModel's existing reload entry point is called — check each before writing it; several are `load()`, some are `refresh()`.

- [ ] **Step 3: Verify by hand**

```bash
./gradlew installDebug
```

Create a soul from the souls tab, save, and confirm the list shows it on return. Edit it from the detail, save, confirm the detail updates. Same for collections and for carving a glyph.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/
git commit -m "$(cat <<'EOF'
feat(android): lists refresh on resume instead of via cover callbacks (#852)

A cover told its parent "I saved, reload". An entry has no parent to tell,
and the parent's entry outlives the trip, so it refreshes when it comes
back (D10) — the #849 back-stack lesson applied to nav entries.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 19: Lab's content swaps become entries

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/lab/LabScreen.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/lab/LabViewModel.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt`

D9a. `LabScreen:84-101` swaps its own content for `AnnouncementDetailScreen` or `LogListScreen` behind a `BackHandler` — the cover pattern under another name, with the same three defects.

- [ ] **Step 1: Add the entries**

```kotlin
    entry<PebblesKey.LabAnnouncement>(metadata = NavTransitions.forKey(PebblesKey.LabAnnouncement(""))) { key ->
        AnnouncementDetailScreen(logId = key.logId, onBack = navigator::goBack)
    }

    entry<PebblesKey.LabLogList>(metadata = NavTransitions.forKey(PebblesKey.LabLogList(""))) { key ->
        LogListScreen(mode = key.mode, onBack = navigator::goBack)
    }
```

- [ ] **Step 2: Make both self-sufficient**

`AnnouncementDetailScreen` currently takes a whole `log` object plus a `coverUrl` computed by `LabViewModel.coverImageUrl(announcement)`. Like the forms in Task 15, it must now load by id — give it its own ViewModel that fetches the log and computes the cover URL.

`LogListScreen`'s `mode` is a `LogListMode`; serialize it as its name string on the key and map back with `LogListMode.valueOf(key.mode)`. If `LogListMode` is a sealed type rather than an enum, make the key carry the sealed type directly with `@Serializable` instead of a `String` — a `String` that only some values parse back from is the `AuthMode.fromRoute` bug rebuilt.

- [ ] **Step 3: Delete the swaps**

Remove `covers.openAnnouncement`, `covers.seeAllMode`, `closeAnnouncement()`, `closeSeeAll()`, the `BackHandler` at `LabScreen:86` and the `when { announcement != null -> … }` block. `LabScreen` renders only its own content now, with `onOpenAnnouncement` / `onSeeAll` lambdas that navigate.

- [ ] **Step 4: Build, test, commit**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
git add apps/android/app/src/main/kotlin/app/pbbls/android/
git commit -m "$(cat <<'EOF'
feat(android): Lab's content swaps become nav entries (#852)

LabScreen swapped its own content for the announcement detail and the log
list behind a BackHandler — the cover pattern under another name, with the
same three defects. Both are entries now (D9a).

The issue's task list does not name these; its acceptance criterion ("no
BackHandler in features/") cannot be met without them.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 20: Screenshot fallout

- [ ] **Step 1: Find and fix broken previews**

```bash
grep -rln "SoulFormScreen\|CollectionFormScreen\|SettingsScreen\|AnnouncementDetailScreen\|LogListScreen" apps/android/app/src/screenshotTest/
```

Every one of these lost parameters. Update each to the new signature, driving the stateless overload with its `XUiState` per the #849 convention.

- [ ] **Step 2: Validate, re-baseline only what genuinely changed**

```bash
./gradlew validateDebugScreenshotTest
```

If a diff is purely the new signature rendering the same pixels, there is nothing to re-baseline. If a screen genuinely looks different, `./gradlew updateDebugScreenshotTest` and inspect every regenerated PNG before adding it.

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/screenshotTest/
git commit -m "$(cat <<'EOF'
test(android): update screenshot previews for the promoted screens (#852)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 21: Verify Part 3 and open the PR

- [ ] **Step 1: The acceptance checks this part delivers**

```bash
./gradlew installDebug
```

| Check | Expect |
|---|---|
| Open Settings, `adb shell am kill app.pbbls.android`, relaunch | **back on Settings**, form state intact |
| Open the soul create form, kill, relaunch | back on the form |
| Slowly drag from the left edge on Settings | the screen scrubs down with your finger, and releasing at the edge **cancels** |
| Edit a soul, save | back on the detail, updated |
| Carve a glyph, save | back on the glyph list, new glyph present |
| `grep -rn "BackHandler" app/src/main/kotlin/app/pbbls/android/features/` | 9 remaining (parts 4–6 clear the rest) |

- [ ] **Step 2: Open the PR**

Body: `Part 3 of #852`. No `Resolves`. No `F-…` ids. Label `feat`, `android`, `ui`, `core`, milestone `M61 · Android Refacto`.

This part is user-visible (predictive back on five surfaces, state surviving a kill), so it **does** need a Lab Note. Write it with the `lab-note` skill's tone: benefit-first, warm, no jargon, no em dashes, everything double-quoted, French an adaptation using "Tu".

---

# Part 4 — The write path becomes entries

**Branch:** `feat/852-write-path-as-entries`

**What ships:** `PebbleDetail`, `EditPebble`, `RecordFlow`, `CreatePebble`, `Drafts` become entries with per-entry ViewModel scoping. Five more `BackHandler`s go, one becomes `NavigationBackHandler`. This is the part that makes "kill the process mid-record-flow and come back" true.

---

### Task 22: Composers resume a draft by id

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/record/RecordFlowScreen.kt` + `RecordFlowViewModel.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/CreatePebbleScreen.kt` + `CreatePebbleViewModel.kt`
- Test: the two ViewModels' existing test files

Both take `resuming: PebbleDraftRecord?` — a whole object from `PathViewModel`. The key can only carry `resumeDraftId: String?`.

- [ ] **Step 1: Write the failing test**

Add to `RecordFlowViewModelTest.kt`:

```kotlin
    @Test
    fun `a resumeDraftId loads that draft into the flow`() =
        runTest(rule.dispatcher) {
            val drafts = FakePebbleDraftsService().apply { seed(draftRecord(id = "d1")) }
            val vm = RecordFlowViewModel(drafts, /* existing deps */, SavedStateHandle(mapOf("resumeDraftId" to "d1")))

            advanceUntilIdle()

            assertEquals("d1", vm.uiState.value.resumedDraftId)
        }

    @Test
    fun `a null resumeDraftId starts a fresh flow and loads no draft`() =
        runTest(rule.dispatcher) {
            val drafts = FakePebbleDraftsService()
            val vm = RecordFlowViewModel(drafts, /* existing deps */, SavedStateHandle(mapOf("resumeDraftId" to null)))

            advanceUntilIdle()

            assertEquals(null, vm.uiState.value.resumedDraftId)
            assertEquals(0, drafts.loadByIdCallCount)
        }
```

- [ ] **Step 2: Run, confirm it fails**

Run: `npm run test --workspace=@pbbls/android -- --tests '*RecordFlowViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement for both composers**

Each reads `resumeDraftId` from `SavedStateHandle` and loads via `PebbleDraftsServicing`. Drop the `resuming: PebbleDraftRecord?` parameter from both screens.

- [ ] **Step 4: Run and commit**

```bash
npm run test --workspace=@pbbls/android -- --tests '*ViewModelTest*'
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/ \
        apps/android/app/src/test/kotlin/app/pbbls/android/features/path/
git commit -m "$(cat <<'EOF'
feat(android): composers resume a draft by id, not by object (#852)

Both took the whole PebbleDraftRecord from PathViewModel, which is what
made "kill the process mid-flow" lose the draft: nothing durable said
which draft was open. They read an id from SavedStateHandle now.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 23: Promote the five write-path covers

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/PathScreen.kt` + `PathViewModel.kt`

- [ ] **Step 1: Add the entries**

```kotlin
    entry<PebblesKey.PebbleDetail>(metadata = NavTransitions.forKey(PebblesKey.PebbleDetail(""))) { key ->
        PebbleDetailScreen(
            pebbleId = key.pebbleId,
            onDismiss = navigator::goBack,
            onEditRequested = { navigator.navigate(PebblesKey.EditPebble(key.pebbleId)) },
        )
    }

    entry<PebblesKey.EditPebble>(metadata = NavTransitions.forKey(PebblesKey.EditPebble(""))) { key ->
        EditPebbleScreen(
            pebbleId = key.pebbleId,
            onDismiss = navigator::goBack,
            onSaved = navigator::goBack,
        )
    }

    entry<PebblesKey.RecordFlow>(metadata = NavTransitions.forKey(PebblesKey.RecordFlow())) {
        RecordFlowScreen(
            onPublished = { navigator.goBack() },
            onDismiss = navigator::goBack,
            onDraftSaved = navigator::goBack,
        )
    }

    entry<PebblesKey.CreatePebble>(metadata = NavTransitions.forKey(PebblesKey.CreatePebble())) {
        CreatePebbleScreen(
            onCreated = { pebbleId ->
                // The form reveals the new pebble through the detail; the flow
                // deliberately does not (M58 D10), which is why only this one
                // replaces itself with a detail rather than just popping.
                navigator.goBack()
                navigator.navigate(PebblesKey.PebbleDetail(pebbleId))
            },
            onCancel = navigator::goBack,
            onDraftSaved = navigator::goBack,
        )
    }

    entry<PebblesKey.Drafts>(metadata = NavTransitions.forKey(PebblesKey.Drafts)) {
        DraftsScreen(
            onResume = { record ->
                navigator.goBack()
                navigator.navigate(PebblesKey.RecordFlow(resumeDraftId = record.id))
            },
            onDismiss = navigator::goBack,
        )
    }
```

- [ ] **Step 2: Gut `PathViewModel`'s cover state**

Delete `isPresentingFlow`, `isPresentingCreate`, `isPresentingDrafts`, `showsDrafts`, `resumingDraft`, `detailReloadKey`, `draftsReloadKey`, `editingPebbleId`, and every `openX`/`closeX`/`onXSaved` that only drove them. `PathScreen` loses all five cover blocks.

The `showsDrafts` getter (`isPresentingDrafts && !isPresentingCreate && !isPresentingFlow`) was the hand-written exclusion `PathViewModel`'s own KDoc calls out as "exactly the kind of thing a sealed type should make impossible". A back stack makes it impossible: only one entry is on top.

`PathScreen` keeps `onOpenDetail` and `onOpenDrafts` lambdas, wired at the entry to `navigate`.

- [ ] **Step 3: Path refreshes on resume**

Add `RefreshOnResume { viewModel.reloadTimeline() }` to `PathScreen`. This replaces `onFlowPublished` / `onFormCreated` / `detailReloadKey` at once — publishing, editing or deleting all reload the timeline the same way now.

Delete `reloadKey` from `PebbleDetailScreen` and `DraftsScreen`; `RefreshOnResume` covers both.

- [ ] **Step 4: Build, lint, test, commit**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
git add apps/android/app/src/main/kotlin/app/pbbls/android/
git commit -m "$(cat <<'EOF'
feat(android): the write path becomes nav entries (#852)

PebbleDetail, EditPebble, RecordFlow, CreatePebble and Drafts are entries.
PathViewModel loses eight fields of cover state, including the showsDrafts
exclusion its own KDoc called out as a job for a sealed type — a back
stack makes it impossible rather than merely discouraged.

Three reload mechanisms (detailReloadKey, draftsReloadKey and the
onPublished/onCreated callbacks) collapse into one resume refresh.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 24: The record flow's back handler

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/record/RecordFlowScreen.kt`

`RecordFlowScreen:121` is one of the three handlers that survives (§6), because it walks back through the wizard's internal steps before raising the close-confirm. It becomes cancellable.

- [ ] **Step 1: Convert it**

```kotlin
    // The flow's steps are internal state, not keys (§6) — ten entries for one
    // logical task would be a back stack the user has to dig out of. So this
    // entry owns back: it walks the step machine, and only the first step
    // raises the close-confirm.
    //
    // NavigationBackHandler, not BackHandler, so the gesture can be CANCELLED:
    // a half-swipe on step 7 should put step 7 back, not commit a step change.
    NavigationBackHandler(enabled = true) { progress ->
        try {
            progress.collect { /* scrub: the step AnimatedContent follows it */ }
            viewModel.onSystemBack(unwindGlyphPicker = glyphPickerState::unwind)
        } catch (e: CancellationException) {
            // Gesture cancelled — stay on this step.
            throw e
        }
    }
```

Check `NavigationBackHandler`'s exact signature in `navigationevent-compose` 1.1.2 before writing this; it may take a `Flow<NavigationEvent>` rather than a progress collector. Read the artifact, do not guess. The commit-only fallback is acceptable **only** if the cancellable form genuinely does not exist, and if you take it, say so in the PR body rather than letting it pass as done.

- [ ] **Step 2: Verify on a device**

```bash
./gradlew installDebug
```

Start a record flow, advance to step 4, then half-swipe from the edge and release in the middle. Expect: step 4 is still showing. Full-swipe: step 3.

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/record/RecordFlowScreen.kt
git commit -m "$(cat <<'EOF'
feat(android): the record flow's back gesture can be cancelled (#852)

The steps stay internal to the one entry — ten keys for one logical task
would be a stack the user has to dig out of. NavigationBackHandler
replaces BackHandler so a half-swipe puts the step back instead of
committing it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 25: Scope the composer coordinators per entry

**Files:**
- Modify: `RecordFlowScreen.kt`, `CreatePebbleScreen.kt`

`RecordFlowModel`, `SnapUploadCoordinator` and `ComposerDraftCoordinator` are currently remembered in the screen. With `rememberViewModelStoreNavEntryDecorator()` already in the decorator list (Task 7), a `hiltViewModel()` in these screens is scoped to the entry and dies with it.

- [ ] **Step 1: Check what each currently is**

```bash
grep -rn "RecordFlowModel\|SnapUploadCoordinator\|ComposerDraftCoordinator" apps/android/app/src/main/kotlin/app/pbbls/android/features/path/ | grep -v "^.*://"
```

Any that is a `remember { … }` inside the screen should become a `hiltViewModel()` or a field of the screen's existing ViewModel. Any that is already reached through `hiltViewModel()` needs no change — the decorator does the scoping.

- [ ] **Step 2: Verify the scoping actually holds**

Add a `Log.d` in each coordinator's `init` and `onCleared`. Open the record flow, back out, open it again. Expect one `init`/`onCleared` pair per visit — not a single `init` that never clears, which would mean it is still scoped to the activity.

Remove the logging before committing.

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/
git commit -m "$(cat <<'EOF'
feat(android): composer coordinators are scoped to their nav entry (#852)

RecordFlowModel, SnapUploadCoordinator and ComposerDraftCoordinator now
die with the entry rather than with whatever composition hosted them.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 26: Verify Part 4 and open the PR

- [ ] **Step 1: The headline acceptance check**

```bash
./gradlew installDebug
```

| Check | Expect |
|---|---|
| Start a record flow, reach the snap step, `adb shell am kill app.pbbls.android`, relaunch | **back in the record flow, on that step, with what you entered** |
| Same for the create form, drafts, a pebble detail, the edit screen | each returns to itself |
| Half-swipe back on record step 4 | step 4 stays |
| Publish a pebble | back on Path, timeline reloaded, new pebble present |
| Create via the form (long-press the FAB) | the new pebble's detail opens over Path |
| Resume a draft from Drafts | the flow opens with that draft |
| `grep -rn "BackHandler" app/src/main/kotlin/app/pbbls/android/features/` | 3 remaining: Onboarding, RecordFlow (now `NavigationBackHandler`), AchievementMomentOverlay |

- [ ] **Step 2: Open the PR with a Lab Note**

The most user-visible part of the stack. Note ids from the Arkaik map for `V-pebble-record`, `V-pebble-detail` and the drafts view.

---

# Part 5 — Auth as a condition, deep links through the stack

**Branch:** `feat/852-auth-and-deep-links`

**What ships:** `replaceAll`-driven auth, `Welcome`/`Auth`/`Onboarding` as keys, the invite deep link on the back stack, `ConnectionsService.pendingInviteToken` deleted, `navigation-compose` dropped.

---

### Task 27: `RootViewModel`

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/RootViewModel.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/RootViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android

import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RootViewModelTest {
    @get:Rule val rule = MainDispatcherRule()

    @Test
    fun `a resolved null session asks for the Welcome stack`() =
        runTest(rule.dispatcher) {
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, SavedStateHandle())

            supabase.emitResolved(session = null)
            advanceUntilIdle()

            assertEquals(RootDestination.SignedOut, vm.uiState.value.destination)
        }

    @Test
    fun `a resolved session asks for the Path stack`() =
        runTest(rule.dispatcher) {
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, SavedStateHandle())

            supabase.emitResolved(session = session(userId = "u1"))
            advanceUntilIdle()

            assertEquals(RootDestination.SignedIn, vm.uiState.value.destination)
        }

    @Test
    fun `an unresolved session asks for neither`() =
        runTest(rule.dispatcher) {
            val vm = RootViewModel(FakeSupabaseService(), SavedStateHandle())

            advanceUntilIdle()

            assertEquals(RootDestination.Unresolved, vm.uiState.value.destination)
        }

    @Test
    fun `a pending invite survives a SavedStateHandle round trip`() =
        runTest(rule.dispatcher) {
            val handle = SavedStateHandle()
            val vm = RootViewModel(FakeSupabaseService(), handle)

            vm.onInviteTokenReceived("tok-1")
            advanceUntilIdle()

            val restored = RootViewModel(FakeSupabaseService(), handle)
            assertEquals("tok-1", restored.uiState.value.pendingInvite)
        }

    @Test
    fun `consuming the invite clears it so it cannot re-present`() =
        runTest(rule.dispatcher) {
            val vm = RootViewModel(FakeSupabaseService(), SavedStateHandle())
            vm.onInviteTokenReceived("tok-1")
            advanceUntilIdle()

            vm.onInviteConsumed()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingInvite)
        }

    @Test
    fun `signing out drops a pending invite from the old session`() =
        runTest(rule.dispatcher) {
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, SavedStateHandle())
            supabase.emitResolved(session = session(userId = "u1"))
            vm.onInviteTokenReceived("tok-1")
            advanceUntilIdle()

            supabase.emitResolved(session = null)
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingInvite)
        }

    @Test
    fun `a token parked before auth resolves is kept`() =
        runTest(rule.dispatcher) {
            // The cold-start App Link case (D12): the token arrives before there
            // has ever been a session, and must NOT be treated as a sign-out.
            val supabase = FakeSupabaseService()
            val vm = RootViewModel(supabase, SavedStateHandle())

            vm.onInviteTokenReceived("tok-1")
            supabase.emitResolved(session = null)
            advanceUntilIdle()

            assertEquals("tok-1", vm.uiState.value.pendingInvite)
        }
}
```

- [ ] **Step 2: Run, confirm it fails**

Run: `npm run test --workspace=@pbbls/android -- --tests '*RootViewModelTest*'`
Expected: FAIL — `RootViewModel` does not exist.

- [ ] **Step 3: Implement**

`RootViewModel` is `@HiltViewModel`, takes `SupabaseServicing` and `SavedStateHandle`, and exposes a `StateFlow<RootUiState>` with a sealed `RootDestination` (`Unresolved`, `SignedOut`, `SignedIn`) plus `pendingInvite: String?`. It carries `hasHadSession` so a pre-auth token is not mistaken for a sign-out (the last test).

It also absorbs what `RootScreen` does today: `supabase.start()`, the palette warm, the reference-data warm, the snap-URL flush on sign-out, and the onboarding gate.

**Follow the sessionStatus-collector deadlock rule:** never call back into supabase-kt from inside the `sessionStatus` collector. Mutate state synchronously only; any network reaction launches a separate coroutine.

- [ ] **Step 4: Run and commit**

```bash
npm run test --workspace=@pbbls/android -- --tests '*RootViewModelTest*'
git add apps/android/app/src/main/kotlin/app/pbbls/android/RootViewModel.kt \
        apps/android/app/src/test/kotlin/app/pbbls/android/RootViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(android): RootViewModel owns the session-to-stack transitions (#852)

Seven tests, of which two pin behaviour the old parked-token field got
subtly right and is easy to lose: a sign-out drops the previous session's
invite, but a token parked by a cold-start App Link BEFORE auth resolves
is kept (D12).

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 28: One `NavDisplay` for both signed-in and signed-out

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/RootScreen.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/navigation/PebblesEntryProvider.kt`

- [ ] **Step 1: Add the funnel entries to the main provider**

`pebblesEntries` grows two parameters, because the funnel entries need state that
lives in `RootViewModel` rather than in the entry provider. Its signature becomes:

```kotlin
fun EntryProviderScope<PebblesKey>.pebblesEntries(
    navigator: Navigator,
    onSignOut: () -> Unit,
    welcomeContentRevealed: Boolean,
    onOnboardingFinished: () -> Unit,
)
```

Update the call site in `RootScreen` to match. Then move the `Welcome` and `Auth`
entries out of `WelcomeAuthNavDisplay` into `pebblesEntries`, and add
`Onboarding` and `AcceptInvite`:

```kotlin
    entry<PebblesKey.Welcome>(metadata = NavTransitions.forKey(PebblesKey.Welcome)) {
        WelcomeScreen(
            contentRevealed = welcomeContentRevealed,
            onCreateAccount = { navigator.navigate(PebblesKey.Auth(AuthMode.SIGNUP)) },
            onLogin = { navigator.navigate(PebblesKey.Auth(AuthMode.LOGIN)) },
        )
    }

    entry<PebblesKey.Auth>(metadata = NavTransitions.forKey(PebblesKey.Auth(AuthMode.LOGIN))) { key ->
        AuthScreen(initialMode = key.mode)
    }

    entry<PebblesKey.Onboarding>(metadata = NavTransitions.forKey(PebblesKey.Onboarding)) {
        OnboardingScreen(steps = OnboardingSteps.all, onFinish = onOnboardingFinished)
    }

    entry<PebblesKey.AcceptInvite>(metadata = NavTransitions.forKey(PebblesKey.AcceptInvite(""))) { key ->
        AcceptInviteScreen(token = key.token, onDismiss = navigator::goBack)
    }
```

- [ ] **Step 2: Delete the branch**

`RootScreen` loses `if (canShowAuthedTabs) … else WelcomeAuthNavDisplay(…)` and `WelcomeAuthNavDisplay` entirely. One `AuthedNavDisplay` remains, renamed to `PebblesNavDisplay`, and an effect drives it:

```kotlin
    val root by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(root.destination) {
        when (root.destination) {
            RootDestination.Unresolved -> Unit // the splash still owns the screen
            RootDestination.SignedOut -> navigator.replaceAll(PebblesKey.Welcome)
            RootDestination.SignedIn ->
                if (root.shouldPresentOnboarding) {
                    navigator.replaceAll(PebblesKey.Path)
                    navigator.navigate(PebblesKey.Onboarding)
                } else {
                    navigator.replaceAll(PebblesKey.Path)
                }
        }
    }
```

- [ ] **Step 3: Push the pending invite after onboarding**

```kotlin
    // Ordered deliberately: an invite that arrived before sign-in opens AFTER
    // onboarding, not over it. The old parked-token field composed the accept
    // surface above onboarding, so a first-run user met a stranger's invite
    // before the app had introduced itself.
    LaunchedEffect(root.pendingInvite, root.destination, root.shouldPresentOnboarding) {
        val token = root.pendingInvite ?: return@LaunchedEffect
        if (root.destination != RootDestination.SignedIn) return@LaunchedEffect
        if (root.shouldPresentOnboarding) return@LaunchedEffect
        navigator.navigate(PebblesKey.AcceptInvite(token))
        viewModel.onInviteConsumed()
    }
```

- [ ] **Step 4: Onboarding consumes back, cancellably**

In `OnboardingScreen`, replace `BackHandler(enabled = true) { }` with the `NavigationBackHandler` equivalent that consumes and shows **no** exit animation (the acceptance criterion says so explicitly).

- [ ] **Step 5: Build, lint, test, commit**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
git add apps/android/app/src/main/kotlin/app/pbbls/android/
git commit -m "$(cat <<'EOF'
feat(android): auth is a condition, not a composition branch (#852)

One NavDisplay for both signed-in and signed-out. Sign-out replaces a
stack instead of unmounting a tree, so the display is never torn down.

An invite that arrives before sign-in now opens after onboarding rather
than over it: the old parked-token field composed the accept surface above
onboarding, so a first-run user met a stranger's invite before the app had
introduced itself.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 29: Delete the parked-token field, add the App Link

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/MainActivity.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/services/ConnectionsService.kt`
- Modify: `apps/android/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Route the intent to the ViewModel**

`MainActivity.captureInviteToken` stops writing `graph.connectionsService.pendingInviteToken` and instead hands the token to `RootViewModel`. The activity cannot call `hiltViewModel()`, so use `by viewModels<RootViewModel>()` and call `onInviteTokenReceived` from `onCreate`/`onNewIntent`.

- [ ] **Step 2: Delete the field**

Remove `pendingInviteToken` from `ConnectionsService` and from `ConnectionsServicing`, plus `FakeConnectionsService`'s copy. Remove the sign-out cleanup block in `RootScreen` (`RootScreen.kt:120-129` on `main`) — `replaceAll` and `RootViewModel`'s own clearing replace it.

- [ ] **Step 3: Add the OAuth App Link**

In `AndroidManifest.xml`, beside the existing `pebbles://` intent filter, add an `autoVerify` https filter:

```xml
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https"
                      android:host="www.pbbls.app"
                      android:pathPrefix="/auth/callback" />
            </intent-filter>
```

Keep the `pebbles://` filter as the fallback — do not replace it. An App Link only verifies if `https://www.pbbls.app/.well-known/assetlinks.json` lists this app's package and signing fingerprint; **check whether that file is served before claiming this works**, and if it is not, say so in the PR body and file a follow-up rather than shipping a filter that silently falls back to a browser chooser.

- [ ] **Step 4: Build, lint, test, commit**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
git add apps/android/app/src/main/
git commit -m "$(cat <<'EOF'
feat(android): invites travel on the back stack (#852)

ConnectionsService.pendingInviteToken is deleted. It was never saveable,
never on the back stack, and composed above onboarding. The token lives in
RootViewModel's SavedStateHandle now and becomes an AcceptInvite key.

Adds the https://www.pbbls.app/auth/callback App Link for the OAuth
return, keeping pebbles:// as the fallback.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 30: Drop `navigation-compose`

**Files:**
- Modify: `apps/android/app/build.gradle.kts`
- Modify: `apps/android/gradle/libs.versions.toml`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/auth/AuthMode.kt`

- [ ] **Step 1: Confirm nothing imports it**

```bash
grep -rn "androidx.navigation\." apps/android/app/src/ | grep -v "navigation3\|navigationevent"
```
Expected: no output. If anything remains, fix it before removing the dependency.

`androidx.hilt.navigation.compose` is a **different** artifact and stays — but per `apps/android/CLAUDE.md` the `hiltViewModel()` import should be `androidx.hilt.lifecycle.viewmodel.compose`. Check and fix any stragglers:

```bash
grep -rn "androidx.hilt.navigation.compose.hiltViewModel" apps/android/app/src/
```

- [ ] **Step 2: Remove it**

Delete `implementation(libs.androidx.navigation.compose)` from `app/build.gradle.kts`, and `androidx-navigation-compose` + `navigationCompose` from the catalog.

Delete `AuthMode.route` and `AuthMode.fromRoute` — both existed only for the NavHost path argument.

- [ ] **Step 3: Build, test, commit**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
git add apps/android/
git commit -m "$(cat <<'EOF'
chore(android): drop navigation-compose (#852)

Nothing imports it. AuthMode.route and fromRoute go with it — both existed
only to round-trip a mode through a NavHost path argument, and fromRoute's
silent LOGIN default was the bug that made that round-trip lossy.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 31: Verify Part 5 and open the PR

- [ ] **Step 1: Auth and deep-link checks**

| Check | Expect |
|---|---|
| Sign out from Settings | Welcome, with no flicker of an unmounting tree |
| Sign back in | Path |
| Sign out, sign in as a different user | Path, with **no** trace of the first user's stacks |
| Send an invite link while signed in | the accept surface opens over the current tab; back returns there |
| Sign out, open an invite link, then sign in as a **new** user | onboarding first, **then** the accept surface |
| Same, then kill the process before signing in | the token survives; the invite still opens after onboarding |
| `adb shell am start -W -a android.intent.action.VIEW -d "https://www.pbbls.app/auth/callback?code=x" app.pbbls.android` | the app handles it, not a browser chooser |

- [ ] **Step 2: Open the PR**

Body: `Part 5 of #852`. The invite-after-onboarding fix is user-visible, so include a Lab Note.

---

# Part 6 — `di/ServiceGraph` dies

**Branch:** `feat/852-service-graph-dies`

**What ships:** `GlyphPickerViewModel`, the three `fireCheck()` lambdas, the overlay hosts taking state, and the deletion of `ServiceGraph` plus four CompositionLocals. This is the part that closes the issue.

---

### Task 32: `GlyphPickerViewModel`

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/pickers/GlyphPickerViewModel.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/pickers/GlyphPickerSheet.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/path/create/pickers/GlyphPickerViewModelTest.kt`

432 lines holding `itemsByTab`, `isLoading`, `loadFailed`, `reloadToken` and carve/buy content swaps — a duplicate of the glyph store inside a picker.

- [ ] **Step 1: Write the failing test**

Follow `AchievementsViewModelTest` exactly as the shape. Cover: load populates each tab; a service failure yields `Error` with a `@StringRes`; selecting a glyph emits the selection effect; a buy refreshes the owned tab.

```kotlin
    @Test
    fun `load populates every tab`() =
        runTest(rule.dispatcher) {
            val market = FakeGlyphMarketService().apply { seedMine(glyph("g1")); seedCommunity(glyph("g2")) }
            val vm = GlyphPickerViewModel(market, FakePathStatsService())

            advanceUntilIdle()

            val content = vm.uiState.value as GlyphPickerUiState.Content
            assertEquals(listOf("g1"), content.mine.map { it.id })
            assertEquals(listOf("g2"), content.community.map { it.id })
        }

    @Test
    fun `a failed load surfaces an error string rather than an empty list`() =
        runTest(rule.dispatcher) {
            val market = FakeGlyphMarketService().apply { armFailure(DataError.Network) }
            val vm = GlyphPickerViewModel(market, FakePathStatsService())

            advanceUntilIdle()

            assertEquals(R.string.error_offline, (vm.uiState.value as GlyphPickerUiState.Error).messageRes)
        }
```

- [ ] **Step 2: Run, confirm it fails, implement**

Sealed `GlyphPickerUiState` (`Loading`, `Error`, `Content`) per the #849 convention: derived values as `by lazy` getters on the case, one-shot selection through `UiEffects`, no bag of booleans.

`GlyphPickerSheet` keeps **no** remembered data state. It stops reading `LocalGlyphMarketService` and `LocalPathStatsService`.

- [ ] **Step 3: Remove the `unwind()` hook**

PR #898 left `GlyphPickerContent` releasing `GlyphCarveViewModel` on `unwind()`, because the picker closed the carve surface without a terminal effect. Part 3 made `GlyphCarve` an entry with its own `ViewModelStore`, so the hook is dead. Delete it and its call sites.

```bash
grep -rn "unwind" apps/android/app/src/main/kotlin/app/pbbls/android/
```
Expected after the change: no glyph-picker hits. The record flow's `glyphPickerState::unwind` in Task 24 is a **different** unwind (the sheet's own dismissal) — read each hit before deleting it.

- [ ] **Step 4: Run and commit**

```bash
npm run test --workspace=@pbbls/android -- --tests '*GlyphPicker*'
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/path/create/pickers/ \
        apps/android/app/src/test/kotlin/app/pbbls/android/features/path/create/pickers/
git commit -m "$(cat <<'EOF'
feat(android): GlyphPickerSheet gets a ViewModel (#852)

432 lines holding its own itemsByTab, isLoading, loadFailed and reloadToken
— the glyph store duplicated inside a picker. The sheet holds no remembered
data state now and reads no service local.

Takes #898's unwind() hook with it: that existed because the picker closed
the carve surface without a terminal effect, and the carve is an entry with
its own ViewModelStore since Part 3.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 33: The three `fireCheck()` lambdas

**Files:**
- Modify: `RecordSoulsStep.kt`, `SoulPickerSheet.kt`, `ProfileAchievementsCard.kt`

Each reads `LocalAchievementsService` only to fire an achievement check.

- [ ] **Step 1: Pass it in**

Give each an `onAchievementCheck: () -> Unit = {}` parameter, wire it from the nearest ViewModel-backed caller, and delete the local read. The default makes existing screenshot previews compile unchanged.

- [ ] **Step 2: Verify none remain**

```bash
grep -rn "LocalAchievementsService" apps/android/app/src/main/kotlin/
```
Expected: only the declaration, about to be deleted in Task 35.

- [ ] **Step 3: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/
git commit -m "$(cat <<'EOF'
refactor(android): achievement checks are lambdas, not a local read (#852)

Three composables read LocalAchievementsService only to call fireCheck().
They take it as a parameter now, which is the last thing standing between
the achievements service and the ServiceGraph's deletion.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 34: The overlay hosts take state

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/karma/KarmaOverlayHost.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/karma/AchievementMomentOverlay.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/RootViewModel.kt`

Both take the **service object**, which is why `karma` and `achievementNotify` are still in `ServiceGraph`. Every other surface takes state.

- [ ] **Step 1: Invert them**

```kotlin
@Composable
fun KarmaOverlayHost(
    flash: KarmaFlash?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

```kotlin
@Composable
fun AchievementMomentOverlay(
    card: AchievementCard?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`RootViewModel` gains `KarmaNotificationService` and `AchievementNotificationService` by constructor and surfaces both in `RootUiState`. `RootScreen` passes the state down.

- [ ] **Step 2: Convert the last `BackHandler`**

`AchievementMomentOverlay:97`'s `BackHandler(enabled = card != null)` becomes a `NavigationBackHandler`. It is genuinely not on the back stack — it is an overlay above the `NavDisplay` — so it stays a handler rather than becoming an entry.

- [ ] **Step 3: The acceptance grep**

```bash
grep -rn "BackHandler" apps/android/app/src/main/kotlin/app/pbbls/android/features/
```
Expected: **no output.** Only `NavigationBackHandler` occurrences remain, and `grep -rn "NavigationBackHandler"` should show exactly three: Onboarding, RecordFlow, AchievementMomentOverlay.

Note that `grep -rn "BackHandler"` also matches `NavigationBackHandler` as a substring — use `grep -rnw "BackHandler"` or `grep -rn "[^n]BackHandler"` for the precise check. This is the same trap `#850`'s acceptance grep hit with `.message` / `.messageRes`.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/
git commit -m "$(cat <<'EOF'
refactor(android): the overlay hosts take state, not services (#852)

KarmaOverlayHost and AchievementMomentOverlay took the service object,
which is the last reason karma and achievementNotify sat in ServiceGraph.
They take state now, like every other surface since #849.

The last BackHandler in features/ goes with it. The overlay is genuinely
not on the back stack, so it stays a handler — a cancellable one.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 35: Delete `ServiceGraph`

**Files:**
- Delete: `apps/android/app/src/main/kotlin/app/pbbls/android/di/ServiceGraph.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/MainActivity.kt`
- Delete: four `Local…Service` declarations
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/PebblesTestHarness.kt`

- [ ] **Step 1: Reduce `MainActivity` to three locals**

```kotlin
@Inject internal lateinit var palettes: EmotionPaletteService
@Inject internal lateinit var referenceData: ReferenceDataServicing
@Inject internal lateinit var snapUrls: SnapURLCache
@Inject internal lateinit var supabaseClientOwner: SupabaseService
```

```kotlin
                CompositionLocalProvider(
                    LocalEmotionPaletteService provides palettes,
                    LocalReferenceDataService provides referenceData,
                    LocalSnapURLCache provides snapUrls,
                ) {
                    RootScreen()
                }
```

The splash predicate reads `graph.supabase.isInitializing`. Inject `SupabaseServicing` directly for it.

- [ ] **Step 2: Delete**

```bash
rm apps/android/app/src/main/kotlin/app/pbbls/android/di/ServiceGraph.kt
```

Delete `LocalSupabaseService`, `LocalConnectionsService`, `LocalKarmaNotificationService`, `LocalAchievementNotificationService`, `LocalGlyphMarketService`, `LocalPathStatsService` and `LocalAchievementsService` — whichever of these now have no readers. **Check each**:

```bash
for l in LocalSupabaseService LocalConnectionsService LocalKarmaNotificationService \
         LocalAchievementNotificationService LocalGlyphMarketService \
         LocalPathStatsService LocalAchievementsService; do
  echo "$l: $(grep -rn "$l" apps/android/app/src/main/kotlin/ | wc -l)"
done
```

A count of 1 means only the declaration is left — delete it. Anything higher has a real reader; find it before deleting.

- [ ] **Step 3: Narrow the test harness**

`PebblesTestHarness` provides four locals; it should now provide three. Update its compile-time gate accordingly.

- [ ] **Step 4: Update `apps/android/CLAUDE.md`**

The "Three CompositionLocals are permanent" bullet says `ServiceGraph` "is down to 10 entries and dies in **#852**". That has now happened. Rewrite the bullet in the past tense, keep the three-local carve-out, and keep the standing "do not add a new `Local…Service` for anything a screen calls" rule.

The skill-routing table's `(#852)` markers on the Navigation and predictive-back rows can drop the issue number.

- [ ] **Step 5: Build, lint, test, commit**

```bash
npm run build --workspace=@pbbls/android && ./gradlew lint && npm run test --workspace=@pbbls/android
git add -A apps/android/
git commit -m "$(cat <<'EOF'
refactor(android): di/ServiceGraph is deleted (#852)

The service locator #848 introduced as temporary and #849 halved is gone.
MainActivity provides exactly three CompositionLocals — palettes,
reference data and the snap-URL cache — and all three are ambient
reference data read by leaf components, which is what a CompositionLocal
is for.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 36: Full acceptance sweep

- [ ] **Step 1: Run every criterion from the issue**

| # | Criterion | How |
|---|---|---|
| 1 | Kill the process on any cover or record step; restore returns there with state | `adb shell am kill app.pbbls.android` on each of: record flow step 5, create form, drafts, pebble detail, edit, settings, soul form, collection form, glyph carve, accept invite |
| 2 | Predictive back scrubs every cover and the record step; Onboarding shows no exit animation | half-swipe each; then trigger onboarding and confirm back does nothing at all |
| 3 | Sign-out and sign-in never unmount the NavDisplay | sign out, sign in; no flash, no re-mount |
| 4 | An invite before sign-in opens after onboarding, not over it, and survives process death | Task 31's sequence, with a kill inserted |
| 5 | No string route, no `orEmpty()` on a nav argument, no `BackHandler` in `features/` | the greps below |
| 6 | Decision-log entries for the IA choice and the D5 supersession | Task 12 |
| 7 | `di/ServiceGraph.kt` deleted, `MainActivity` provides three locals | Task 35 |
| 8 | No composable reads a service local to *call* it | inspect the three survivors' readers |
| 9 | `GlyphPickerSheet` holds no remembered data state; no `unwind()` hook | Task 32 |

```bash
cd apps/android
grep -rn "ROUTE_" app/src/main/kotlin/ ; echo "--- expect nothing above ---"
grep -rnw "BackHandler" app/src/main/kotlin/app/pbbls/android/features/ ; echo "--- expect nothing above ---"
grep -rn "orEmpty()" app/src/main/kotlin/app/pbbls/android/navigation/ ; echo "--- expect nothing above ---"
grep -rn "androidx.navigation\." app/src/ | grep -v "navigation3\|navigationevent" ; echo "--- expect nothing above ---"
```

- [ ] **Step 2: Record honestly what did not pass**

If a criterion does not hold, **say so in the PR body** rather than checking the box. A criterion recorded as met when it is not is worse than one recorded as outstanding, because nobody looks again. File a follow-up issue for each and reference it by issue number — **never by finding id.**

- [ ] **Step 3: Final Arkaik status**

Move the acceptances and views this stack delivered to `releasing`, not `live`. `live` means shipped to people — a store accepted the build — and nothing here has done that ([arkaik#424](https://github.com/alexisbohns/arkaik/issues/424)).

---

### Task 37: Open the final PR

- [ ] **Step 1: Body**

This is the only PR in the stack that closes the issue:

```
Resolves #852
```

List the six parts and what each shipped. Include the acceptance table from Task 36 with honest marks. Include a Lab Note (the whole stack is user-visible). **No `F-…` ids anywhere in the body** — the Arkaik App resolves every one it finds, and five launch-gating findings were silently closed that way by #832.

- [ ] **Step 2: Submit the stack**

```bash
gh stack submit
```

Confirm each PR's base is the part below it and that only part 6 says `Resolves`.

---

## Lessons learned

*Fill this in after the stack merges. Candidates for promotion into `apps/android/CLAUDE.md` at the next milestone-boundary grooming pass — remember the bar is **durable** and **action-guiding**, and that CLAUDE.md is never edited per-PR for learnings.*

- [ ] Did `rememberViewModelStoreNavEntryDecorator` scope the coordinators as expected, or did something else own them?
- [ ] Did `NavigationBackHandler`'s cancellable form exist in `navigationevent-compose` 1.1.2, or was the commit-only fallback taken?
- [ ] Did R8 need the `@Serializable` keep rule (Task 6 Step 2)?
- [ ] Did the "exit through home" bound (D4) feel right in the hand, or did users want the full visited-tab history after all?
- [ ] Which of the screens that had to learn to load their own subject (Tasks 15, 16, 19, 22) turned out to have been sharing state with the parent in a way the promotion exposed?
