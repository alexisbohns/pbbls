# @pbbls/android — agent context

Native Android app for Pebbles. Kotlin + Jetpack Compose, minSdk 33, portrait on
phones and adaptive on large screens.

## What "mirror iOS" means here

Android mirrors `apps/ios` in **behavior, funnel and data contract**, not in
architecture. The same screens do the same things in the same order, call the
same RPCs with the same payloads, and show the same copy. When this file or a
spec says "mirror X", read the named iOS file under `apps/ios/Pebbles/` for
**what it does**: its states, edge cases, validation, copy and server calls.
Then build it the Android way described below (ViewModel, Hilt, Navigation 3,
stock Material 3). Do not port SwiftUI structure (`@Observable` services read
from the environment, full-screen presentations, conditional-composition gates,
hand-built controls).

These divergences are deliberate. Each is recorded in `docs/decisions/log.md`,
and none crosses a data contract:

| Divergence | Since |
| --- | --- |
| Architecture: Hilt, a ViewModel per screen, Navigation 3 (M38 D4 and D5 superseded) | #848, #849, #852 |
| Four-tab navigation bar; iOS has a Profile hub (#903 asks whether iOS follows) | #852 |
| `core/` vs `features/` package split; iOS groups by feature all the way down | #851 |
| M3-evo Material 3 Expressive theme: palette, amber error, type (#921 asks whether iOS follows) | #853 |
| Stock Material 3 controls instead of ports of the iOS ones | #854 |
| Large screens: readable column, navigation rail, list-detail panes | #855, #940 |
| No Rive runtime; the Welcome logo is a static vector | #856 |

Anything else that differs is drift, not design. Fix it rather than documenting
it. One known drift runs the other way: Android's pebble read page dropped its
emotion tint (#940) and iOS still has it.

History: the app was bootstrapped in **M38 · Android App**
(`docs/superpowers/specs/2026-07-10-android-bootstrap-design.md`, decisions
D1–D18) and re-architected in **M61 · Android Refacto** (#845–#858). Most of
that doc's architecture decisions are superseded or narrowed in
`docs/decisions/log.md`, and the log wins wherever the two disagree.

## Skill routing

Two Android skill sets are installed user-side and their descriptions
overlap: Google's official set (`adaptive`, `navigation-3`, `edge-to-edge`,
`r8-analyzer`, …, installed through the `android` CLI) and the `android-skills`
plugin by rcosteira79 (`android-skills:compose`, `android-skills:android-dev`,
…, always invoked with the `android-skills:` prefix). They may not be present
for every contributor; ignore any row whose skill is missing. Where two rows
could apply, the more specific topic wins. This table overrides both sets'
own descriptions.

`android-skills:android-dev` advertises itself as the baseline for *all*
Android work and routes only within its own set; it never overrides the second
table. Where its defaults disagree with this file, this file wins.

The `android` CLI (`android docs search "…"`, `android docs fetch kb://…`) is
the authoritative reference for any API question neither set answers; prefer
it to web search. Its device and emulator commands are usable: the maintainer's
machine has a JDK, an Android SDK and AVDs (the `oxymore-eclipse` Pixel 7 AVD).
`ANDROID_HOME` is not exported by default, so set it
(`export ANDROID_HOME=$HOME/Library/Android/sdk`) or `scripts/gradle-if-sdk.sh`
silently no-ops and `npm run build --workspace=@pbbls/android` looks green
having done nothing. CI remains the authority; local is the fast first check,
and the only way to smoke-test a minified build by hand.

### This app's stack

| Topic | Use | Not |
| --- | --- | --- |
| Compose UI, state hoisting, recomposition, `LazyColumn`, side effects | `android-skills:compose` | |
| M3 compliance review, touch targets, a11y audit of a screen | `android-skills:android-ux` | |
| Screen architecture: `@HiltViewModel`, sealed `UiState`, `UiEffects`, Hilt modules | `android-skills:android-dev` | `android-skills:koin`; the container is Hilt |
| Service error boundary, sealed `DataError` | `android-skills:android-data-layer` | |
| Coroutines and `Flow`, dispatcher injection, `Channel` vs `SharedFlow` | `android-skills:kotlin-coroutines`, `android-skills:kotlin-flows` | |
| Navigation: `NavDisplay`, `PebblesKey`, per-tab back stacks, Scenes | `navigation-3` | `android-skills:compose`; there is no `navigation-compose` in the app |
| Snap thumbnails, signed-URL images | `android-skills:coil-compose` | |
| Package boundaries, visibility, `core/` vs `features/` | `android-skills:modularization` | |
| Writing or fixing JVM, Robolectric and screenshot tests | `android-skills:android-testing` | `testing-setup`, which stands up new infra |
| Logcat, ADB, ANR traces, Compose recomposition bugs | `android-skills:android-debugging` | |
| `libs.versions.toml`, convention plugins, build speed | `android-skills:android-gradle-logic`, `android-skills:gradle-build-performance` | |
| AndroidX source lookups | `android-skills:android-source-search` | |

Not applicable here and never routed: `android-skills:android-retrofit`,
`android-skills:kmp-ktor`, `android-skills:kmp-boundaries` (supabase-kt owns
the network client), `android-skills:paging` (the Path loads whole),
`android-skills:rxjava-migration`, `android-skills:pdf-annotations`.
`android-skills:datastore` is not routed **yet**: two booleans live in
`SharedPreferences` (`OnboardingPreferences`, `AppearancePreferences`). The
DataStore trigger D5 named ("real settings exist") fired with #853, and the
next preference added is where the migration happens. Route it then.

### Owned outright by the Google set

| Topic | Skill |
| --- | --- |
| Window size classes, foldables, tablets, list-detail panes, content width cap | `adaptive` |
| System bar and IME insets, `enableEdgeToEdge`, sheet insets | `edge-to-edge` |
| R8 keep rules, `isMinifyEnabled`, mapping upload | `r8-analyzer` |
| Predictive back, `NavigationBackHandler`, `androidx.navigationevent` | `navigation-event` |
| Exported components, deep-link and App Link intent handling | `android-intent-security` |
| Play policy, Data Safety declarations, permission hygiene | `play-policy-insights` |
| Restore Credentials after device restore | `restore-credentials` |
| `android` CLI: docs search, SDK, AVDs, screenshots, UI inspection | `android-cli` |
| Adopting the Compose Styles API | `styles` |
| Standing up a new test harness or CI test job | `testing-setup` |

## Source of truth

- **Gradle Kotlin DSL is the build source of truth.** `gradle/libs.versions.toml`
  is the single place dependency versions live (D2). Never hardcode a version in
  a `build.gradle.kts`; add it to the catalog and reference `libs.…`. Version
  bumps are deliberate, isolated commits.
- **Secrets flow `secrets.properties` → `BuildConfig` → `AppEnvironment` (D8).**
  Never hardcode Supabase keys. Never read `BuildConfig` from arbitrary code.
  `AppEnvironment` validates and fails loud, and `di/SupabaseModule` is the only
  place it is read. `secrets.properties` is git-ignored;
  `secrets.example.properties` is committed.

## Architecture

### Dependency graph (Hilt, #848)

- **Every service is `@Singleton class X @Inject constructor(…)`.** Adding one
  is that annotation and nothing else. The `di/` modules are small on purpose:
  - `SupabaseModule`: the one `@Provides` for `SupabaseClient`. This is the only
    place `AppEnvironment` is read, which is what keeps every service
    constructible in a JVM test.
  - `ServiceBindings`: `@Binds` from each `…Servicing` interface to its
    implementation. It grows one line per interface, and an interface exists
    only once a test needs a fake of it.
  - `ServiceModule`: three services assembled by hand, each for a reason written
    in its KDoc. Do not grow it. Needing a qualifier is not a reason; qualifiers
    on constructor parameters are inferred.
  - `DispatchersModule`: `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`
    and `@ApplicationScope`. Inject a dispatcher; never write `Dispatchers.IO`
    outside this file. `@ApplicationScope` runs on the main thread and has no
    exception handler, so catch inside every `launch` on it (read its KDoc).
- **Screens take dependencies through `hiltViewModel()`, never through a
  CompositionLocal.** Exactly three locals exist and they are permanent:
  `LocalEmotionPaletteService`, `LocalReferenceDataService` and
  `LocalSnapURLCache`. They carry ambient reference data that *leaf* components
  read where they render (`PebbleRow`, `ValenceGlyph`, `WeekHeader`, the
  pickers), which is what a `CompositionLocal` is for. `MainActivity` provides
  those three and nothing else. Do not add a `Local…Service` for anything a
  screen *calls*. (Decision log 2026-09-20 and 2026-09-21.)
- **Services own app-lifetime state, ViewModels own screen state.** A service
  may hold Compose state for what outlives every screen: the session
  (`SupabaseService`), caches (`EmotionPaletteService`, `SnapURLCache`), and
  cross-screen notifications (`KarmaNotificationService`,
  `AchievementNotificationService`). Anything a single screen loads, edits or
  submits lives in that screen's ViewModel.

### Screen architecture (#849)

One `@HiltViewModel` per stateful screen. `AchievementsViewModel` +
`AchievementsScreen` is the worked reference.

- **`StateFlow<XUiState>`, collected with `collectAsStateWithLifecycle()`**,
  not `collectAsState()`, which keeps collecting while the app is backgrounded.
- **`hiltViewModel()` comes from `androidx.hilt.lifecycle.viewmodel.compose`.**
  The `androidx.hilt.navigation.compose` one is the same function at its old
  address and is deprecated; the IDE still offers it first.
- **`XUiState` is a sealed interface: `Loading`, `Error(@StringRes …)`,
  `Content(…)`.** Never a bag of booleans beside the data: five flags admit 32
  combinations of which four are real, and the render code pays for the other 28.
  `when` over it with **no `else`**, so a new case is a compile error.
- **Derived values are getters (`by lazy`) on the case, not constructor fields.**
  A field has to be recomputed at every construction site and joins `equals`; a
  getter replaces a per-recomposition recompute with a per-state-value one.
- **One-shot effects (navigate, snackbar, haptic) go through `UiEffects<T>`**
  (`core/common/UiEffects.kt`) and are collected with `ObserveUiEffects`, never
  held in state. A `StateFlow` replays to every new collector, so "navigate
  back" in state navigates back again after a rotation.
- **Writes run in `viewModelScope`**, never `rememberCoroutineScope`. Leaving
  the composition must not cancel a request the server has already accepted.
- **Arguments arrive from the key.** The entry passes the key's fields as
  parameters and the screen calls `viewModel.start(id)` from a
  `LaunchedEffect(id)` (`SoulDetailScreen` is the pattern).
- **`SavedStateHandle` only for what the server has not seen** (a typed draft,
  an unsaved form). Server-derived data is re-fetchable, so process death has
  nothing to restore that a reload would not produce.
- **A screen keeps a stateless content layer taking the `XUiState`** (`PathContent`,
  and one per list and detail screen since #945). That layer is what the
  screenshot tests render, including the screen's own chrome, spinner and error
  branch. Screens take navigation as lambdas, never the `Navigator`.
- **The ViewModel gets a JVM test** (`runTest` + `MainDispatcherRule`) driving
  load, error and save against a fake. That test is the bar for extracting the
  service's `…Servicing` interface; do not extract one ahead of it.

### Navigation 3 (#852)

One `NavDisplay` in `RootScreen`; there is no `navigation-compose` in the app.
The files, in the order you touch them when adding a destination:

- **`navigation/PebblesKeys.kt`**: every destination is a `@Serializable`
  `PebblesKey`, because the stacks survive process death. Two marker interfaces
  carry the rules, so there is no second list to fall out of sync:
  `TopLevelKey` is one of the four tabs, and `BarKey` keeps the navigation bar
  visible. A new key that is neither renders as a modal by default, which is the
  safe default.
- **`navigation/PebblesEntryProvider.kt`**: key → screen. Each entry carries its
  transition (`NavTransitions.forKey`) and, for list and detail screens, its
  pane metadata (`PanePairs`) as entry metadata.
- **`navigation/Navigator.kt`**: the only writer of the stacks. `navigate`
  pushes, or switches tab for a `TopLevelKey`. `navigateToDetail` and
  `closeDetail` keep list-detail panes honest. **`rootAt` is the auth gate's
  only entry point, never `replaceAll`**, because the gate also runs on a cold
  restore. `NavigatorRootAtTest` guards this; do not delete it as redundant.
- **`navigation/NavigationState.kt`**: four per-tab stacks with
  exit-through-home back. `rememberViewModelStoreNavEntryDecorator` scopes each
  ViewModel to its entry, so popping an entry destroys its ViewModel. A fresh
  entry starts at `Loading`, and nothing needs a manual reset.
- **Auth is a condition, not a branch.** `RootViewModel` resolves a
  `RootDestination` and drives the stack. Welcome, Auth, Onboarding and
  AcceptInvite are ordinary entries.
- **Large screens are Scenes** (`navigation/PebblesSceneStrategies.kt`): a
  wrapped `ListDetailSceneStrategy` (two panes from 840 dp, or at a separating
  hinge) then `BottomSheetSceneStrategy` (pebble detail is a sheet on phones).
  A detail learns it is a pane only through a `showBack` parameter. Screens
  never import the adaptive library.
- **Back is `NavigationBackHandler`** (`androidx.navigationevent`), never the
  legacy `BackHandler`.
- The navigation bar/rail is `NavigationSuiteScaffold`
  (`navigation/PebblesNavigationSuite.kt`); it shows when the top key is a
  `BarKey`.

Design: `docs/superpowers/specs/2026-09-20-android-nav3-design.md` and
`docs/superpowers/specs/2026-09-26-android-list-detail-scenes-design.md`.

### Error boundary (#850)

- **Failures become a `DataError`** (`core/data/DataError.kt`: `Network`,
  `Unauthorized`, `NotFound`, `Conflict(code)`, `Quota`, `Unknown`) through
  `Throwable.toDataError()`. It rethrows `CancellationException` first, and it
  matches server conditions on `RestException.error` (the PostgREST `message`
  field, e.g. `handle_taken`) exactly, never by searching `Exception.message`.
- **Each feature maps `DataError` → `@StringRes`** (`connectionsErrorMessage`,
  `handleErrorStringRes`, …) and the `Error` UiState carries that resource.
  **No `.message` of any exception reaches the UI**: raw SDK text is
  unlocalized and sometimes JSON. `grep -rnE '\.message\b' app/src/main`
  should only ever hit comments. Auth maps GoTrue's typed
  `AuthErrorCode` (`core/ui/AuthErrorMessage.kt`).
- `AppEnvironment` throws `IllegalStateException` with setup instructions when a
  key is blank. That is a setup bug caught at launch, not a runtime condition;
  the build itself never fails on missing secrets.
- Runtime async failures must be surfaced: logged, and reflected in UiState.

### Data layer

- **Nothing outside `core/data` touches `SupabaseClient`.** Views never do;
  ViewModels call services.
- **RPC-first (per root `AGENTS.md`).** Anything touching more than one table, or
  more than a simple single-row read/write, goes through a Postgres RPC, not
  stitched client calls (PostgREST has no client transactions). The payloads
  are the cross-surface contract: they mirror iOS and web exactly, and a change
  to one means checking the other three surfaces.
- **Extract a `…Servicing` interface for a fake only when a test needs one**,
  not before.

### supabase-kt sessionStatus-collector deadlock rule (ported verbatim from iOS)

When `SupabaseService` collects `auth.sessionStatus`, **never call back into
supabase-kt from inside the collector**. Mutate `session` / `isInitializing`
state synchronously only. Re-entering the client from within its own status
emission deadlocks the auth actor (the iOS app hit this; the fix was to make the
observer a pure state assignment). Any network call reacting to a status change
happens in a separate coroutine, not inline in the collector.

## Kotlin conventions

- **API 33 APIs only. No `Build.VERSION` / `if (SDK_INT >= …)` guards below 33.**
  This is the analog of the iOS "no `if #available`" rule. minSdk is 33 so the
  modern floor (per-app language, predictive back, themed icons) is always there.
- **`@Composable` functions are PascalCase.** ktlint runs its stock ruleset
  (D11); the only accommodation is `.editorconfig`'s
  `ktlint_function_naming_ignore_when_annotated_with = Composable`. Don't add
  other ktlint config without a motivating incident.
- **Log, don't swallow.** Use `android.util.Log` with a consistent tag on every
  error path; silent failures are bugs. No empty `catch` blocks. No `println`.
  JVM unit tests set `unitTests.isReturnDefaultValues`, so a `Log` call in a
  tested class does not throw "not mocked". Injecting the logger as a lambda
  (`SnapUploadCoordinator.onLog`) is a pattern to stop copying, not to spread.
- **View-scoped async cancels with the view.** Use `LaunchedEffect` /
  `viewModelScope`, never `GlobalScope`.
- **Let cancellation travel: `runCatchingCancellable`, never a bare `catch (e:
  Exception)`.** Both that and `kotlin.runCatching` swallow
  `CancellationException`, and a coroutine that catches its own cancellation
  keeps running inside a scope that believes it stopped.
  `core/common/CoroutineErrors.kt` rethrows it first. The inverse case, a write
  that must finish once the request has left the device, is
  `withContext(NonCancellable)` around that section, not a catch.

## Folder layout (#851)

```
app/src/main/kotlin/app/pbbls/android/
  PebblesApp.kt          Application: Hilt root, Coil image loader, no launch work
  MainActivity.kt        Single activity: splash, deep links, the three ambient locals
  RootScreen.kt          The one NavDisplay, the navigation suite, overlay hosts
  RootViewModel.kt       Session and invite state → RootDestination
  AppEnvironment.kt      BuildConfig secrets, validated (read only by di/SupabaseModule)
  DebugTokenPreviewScreen.kt  Design-system gallery, rendered only by screenshot tests
  core/model/            Pebble, Domain, Glyph, Collection, EmotionPalette, … (@Serializable, no UI)
  core/data/             Services, DataError, preferences, the snap pipeline
  core/designsystem/     PebblesTheme, ColorSchemes, Typography, Shapes, Spacing,
                          PebblesScreen (Scaffold + top app bar), PebblesPrimaryButton,
                          ReadableWidth, PebblesLogo, … (thin defaults over stock M3)
  core/ui/               Shared UI that knows the domain: GlyphView, SoulItem, PebbleRow,
                          RippleBadge, karma/achievement overlays, ReferenceSlugs/Strings,
                          and core/ui/render/ (PebbleSvg, GlyphImage, the wobble stack)
  core/common/           runCatchingCancellable, UiEffects, JourneyTags (no UI of its own)
  di/                    Hilt modules and qualifiers
  navigation/            Keys, entry provider, Navigator, stacks, Scenes, navigation suite
  features/<feature>/    welcome, auth, onboarding, path, profile, glyph, lab, connections
```

**The `core/` ↔ `features/` split is a test, not a convention.**
`ArchitectureBoundaryTest` (Konsist, a plain JVM unit test) fails the build on
either violation:

- **`core` never imports `features`.** Absolute. If shared code needs a feature,
  it is not shared code; it is that feature's code in the wrong folder.
- **A feature never imports another feature.** Seven exceptions are frozen in
  the test and may only shrink; emptying them is #914. Anything else fails, so
  the answer to "profile needs this bit of path" is to move the bit to `core/`,
  not to add an import.

Which `core` package: `model` if it is data, `data` if Hilt constructs it,
`designsystem` if it would look at home in any app, `ui` if it renders a Pebbles
domain type, `common` if it is neither data nor UI. Inside `core`,
`ui -> designsystem -> model` and `data -> model`; there are no upward edges and
the test is what keeps it that way.

**Gradle modules are deliberately not cut.** `:core:model`, `:core:data`,
`:core:designsystem`, `:feature:*` and a `build-logic/convention` plugin are the
obvious next step, and the package split is exactly what makes them cheap. But
one maintainer and one module that builds in CI in minutes do not pay for the
ceremony. The trigger to revisit is a **second regular contributor** or a
**clean `assembleDebug` past ~3 minutes in CI**, whichever lands first
(decision log, 2026-09-22).

## Theme (#853)

- **App code reads `MaterialTheme.colorScheme`, `.typography` and `.shapes`.
  Nothing else decides a colour, a type style or a corner.** The theme is the
  M3-evo Material Theme Builder export: six generated schemes in
  `core/designsystem/ColorSchemes.kt` (regenerate with the script in
  `docs/superpowers/plans/2026-09-24-android-m3-expressive-theme.md`, never
  hand-edit), `PebblesMaterialTypography` (Inclusive Sans + Ysabeau, bundled
  variable TTFs, M3 default scale), `PebblesShapes` (M3 Expressive defaults),
  under `MaterialExpressiveTheme` with `MotionScheme.expressive()`.
  `ThemeLiteralsTest` fails the build on a `Color(0x…)`, a literal
  `RoundedCornerShape` radius or a `.copy(fontSize = …)` in `features/` or
  `core/ui`.
- **Chrome is stock Material 3 (#854).** Use the stock component (Expressive
  where the pinned material3 has it) and delete wrappers rather than re-skinning
  them. Keep a wrapper only when a real Pebbles default repeats across call
  sites (`PebblesPrimaryButton`, `GoogleSignInButton`). Controls with no stock
  equivalent (`SlideToConfirm`, `ValenceRoll`, the valence fan,
  `NewPebbleButton`) stay custom and carry the semantics a stock one would:
  role, state description, 48 dp target, RTL-aware drag
  (`core/designsystem/LayoutDirectionDrag.kt`).
- **Pick the role by use:** an interactive boundary is `outline`, a decorative
  one `outlineVariant`; muted-but-informative text is `onSurfaceVariant`, and
  `onSurface.copy(alpha = 0.38f)` is for disabled only.
- **Emotion palettes are server data, never scheme roles.** This includes the
  valence mesh and Joy's surface hex (`ValenceMesh.JOY_SURFACE_HEX`). Don't map
  them onto `primary`/`tertiary` because they look close.
- **`PebblesTheme` holds only what M3 has no slot for:** `.spacing` (the M3
  4 dp grid) and `.hand` (Caveat and Reenie Beanie: name input, valence word,
  soul names).
- **Error is amber**, not red. This is a product decision (decision log
  2026-09-24).
- **Scheme choice:** wallpaper colour when the user's Settings switch is on
  (the default; `AppearancePreferences`), else the export's scheme for the
  system contrast level, live on Android 14+. `PebblesTheme(dynamicColor =
  false)` is the default so every preview renders the brand scheme; only
  `MainActivity` passes the setting.
- **material3 is pinned to `1.5.0-alpha27`** in `libs.versions.toml` for the
  Expressive API. Drop the pin when 1.5.0 stable is in the BOM, and do not
  move to an alpha that drags Compose ui/foundation off the BOM's stable line.
- **Large screens (#855):** every screen's content is capped at 600 dp by
  `Modifier.readableWidth()` (tab roots get it through `PebblesScreen`). A new
  screen must apply it or it stretches on tablets. Sheets cap themselves.
- Motion: `rememberReduceMotion()` (`core/designsystem`) gates every custom
  animation; stock components follow the system animator scale themselves.

### Launcher icon & splash (#846)

- **The launcher icon is a vector adaptive icon, generated, never hand-edited.**
  `res/mipmap-anydpi/ic_launcher.xml` composes three layers:
  `@color/ic_launcher_background` (the accent primary), plus
  `drawable/ic_launcher_foreground.xml` and `drawable/ic_launcher_monochrome.xml`
  (the themed-icon layer minSdk 33 exists for). Both drawables, and Welcome's
  `drawable/pbbls_logo.xml` (#856), are produced by
  `node scripts/logo-svg-to-launcher-icon.mjs` from the *iOS* brand mark
  (`apps/ios/Pebbles/Resources/pbbls-logo-loader.svg`). Re-run it when the mark
  changes rather than editing the XML, or the two surfaces drift. There are no
  `mipmap-*dpi` PNG buckets: the layers are vectors, so one file serves all
  densities.
- **`values/colors.xml` is only for what the platform reads before Compose
  runs**: the icon ground and the splash background (with its `values-night`
  override). It duplicates three values from `core/designsystem/ColorSchemes.kt`
  (`splash_icon_background` = `LightScheme.primary` in both modes, and the
  light/dark `splash_background` surfaces) because XML cannot read Kotlin; the
  launcher icon ground `#C07A7A` is deliberately not a scheme value. Keep the
  duplicated values in sync and do **not** grow this file into a second palette.
- **The cold-start splash is the system splash** (`androidx.core:core-splashscreen`).
  `Theme.Pebbles.Starting` (parent `Theme.SplashScreen.IconBackground`) is the
  activity's declared theme and names `Theme.Pebbles` as its
  `postSplashScreenTheme`. It is a separate style rather than a re-parented
  `Theme.Pebbles` because a theme cannot be its own `postSplashScreenTheme`.
- **Nothing gates the launch on a duration.** `MainActivity.onCreate` calls
  `installSplashScreen()` before `super.onCreate` and holds the splash on
  `supabase.isInitializing`, with an 8 s ceiling mirroring iOS's
  `loaderCeilingSeconds` (decision log 2026-07-17) so a wedged auth cannot
  strand it. A warm signed-in launch therefore reaches Path immediately. Do not
  reintroduce a `delay(…)` in `RootScreen`: the 2.5 s hold this replaced was the
  bug.

### No Rive on Android (#856)

Android ships **no Rive runtime** (decision log 2026-09-26). The Welcome logo is
`core/designsystem/PebblesLogo`, a static `R.drawable.pbbls_logo` tinted
`primary`, generated by the same `scripts/logo-svg-to-launcher-icon.mjs` run
as the launcher icon from the same iOS SVG, so re-run it rather than
hand-editing. iOS's `HandcraftedLogoView` draw-on reveal is not ported; if it
is, port it in Compose (it is path reveals over that SVG, not a Rive file).
Rive cost a native-library load in `PebblesApp.onCreate` on every cold start
and ~6.6 MB of `.so` per ABI, for one animation signed-in users never see.
Do not bring it back for a single asset.

### Path rendering

- **`render_svg` renders through `PebbleSvg`** (`core/ui/render/`):
  AndroidSVG parses the RPC string after a literal `currentColor` →
  palette-hex substitution (D10, mirrors iOS `PebbleRenderView`). Injected
  hex must be **6-digit**: `EmotionPalette` truncates the DB's 8-digit
  `#RRGGBBAA` before anything reaches SVG markup (8-digit misparses).
- **Outline silhouettes** (the row backdrop) are byte-identical copies of the
  iOS assets, renamed on copy (Android resource names are `[a-z0-9_]`):

| iOS source (`apps/ios/Pebbles/Resources/Outlines/`) | Android (`res/raw/`) |
| --- | --- |
| `{size}-{polarity}.svg` (9 files) | `outline_{size}_{polarity}.svg` |

  Their `#FF00FF` sentinel fill is string-replaced with the palette fill hex
  (`PebbleOutlineBackdrop`); fill alpha rides separately as view alpha.
- **The collapsed form row's valence imagery** is res/raw SVGs normalized from
  the web line art and tinted via `currentColor` injection
  (`ValenceGlyph`/`ValenceAssets`), not the iOS PDF assets. The Path row never
  uses them; the row is outline + `render_svg`.
- **The valence fan's artworks** (`features/path/valence/`) are byte-identical
  copies of the iOS files, generated from the source PDFs by
  `apps/ios/Scripts/valence-art-to-svg.mjs` and renamed on copy like the
  outlines:

| iOS source (`apps/ios/Pebbles/Resources/ValenceArt/`) | Android (`res/raw/`) |
| --- | --- |
| `valence-{polarity}{Size}.svg` (9 files) | `valence_art_{size}_{polarity}.svg` |

  Each carries stroked paths at the widths the PDF drew them with (inked
  through `WobbleRenderer.glyphInk`) plus exactly one filled path, the
  fossil's spiral, which goes through `backdropArt` instead. Tracing a filled
  spiral as a centreline fills it in solid, so the split is load-bearing and
  `ValenceArtTest` asserts it. Do not re-derive these from the web line art.
- **The week-roll cairn is a static drawable** (`res/drawable/cairn_static.xml`).
  The iOS Rive state machine (`pbbls-cairn-states.riv`) is a known fast-follow,
  in Compose, since Android has no Rive runtime.
- **Engine-composed SVG fixtures** for screenshot tests regenerate via
  `npx tsx apps/android/scripts/gen-pebble-svg-fixtures.ts` (uses the real
  compositor sources in `packages/supabase`). Rerun when the engine changes.

## Behavior that mirrors iOS on purpose

- **Two composers coexist (M58, #725).** The eleven-step record flow
  (`features/path/record/`) is the default: tapping "New pebble" opens it,
  **long-pressing it opens `CreatePebbleScreen`**, and resuming a draft enters
  the flow at its first unanswered mandatory step. Neither is dead code, and
  `EditPebbleScreen` needs `PebbleForm` regardless. Every record-flow
  interaction goes through its ViewModel so the haptic-on-every-tap rule is
  structural. The step order carries three dependencies (photo→when for EXIF,
  valence→emotion for category ordering, privacy last against publish), so
  reordering keeps the cost and drops the reason.
- **Onboarding illustrations are a placeholder** (`OnboardingImage.Placeholder`)
  until the iOS artwork is exported to Android drawables.

## Localization (D9)

- **User-facing strings live in `res/values/strings.xml` (en, default) and
  `res/values-fr/strings.xml`.** Brand strings (the word "Pebbles") are
  `translatable="false"`.
- **Reference-data names resolve through `ReferenceStrings.referenceName(type,
  slug, fallbackDbName)`**, never `.name`/DB value directly on a read path:
  slug → `R.string.emotion_<slug>_name` / `domain_<slug>_name` /
  `emotionCategory_<slug>_name`, falling back to `fallbackDbName` when no
  catalog entry exists (new server-side rows before Android catches up).
  `ReferenceStrings` is an explicit, compile-checked `Map<String, Int>` per
  type (over `getIdentifier()`), mirroring iOS `Emotion+Localized.swift` /
  `Domain+Localized.swift`. `ReferenceSlugs.kt` is the compile-time mirror of
  the live Supabase slugs; adding a reference row server-side means updating
  `ReferenceSlugs.kt` AND both `strings.xml` files in the same change, or
  `LocalizationParityTest` fails.
- **`android:localeConfig`** (`res/xml/locales_config.xml`) enables per-app
  language. `LocalizationParityTest` (JVM unit test, parses the `strings.xml`
  files directly) asserts en/fr key-set parity and that every `ReferenceSlugs`
  entry maps to a real resource id.
- Dates/numbers localize via the active `Locale`. Never pin a formatter to a
  fixed locale.

## Lint & test

- **ktlint, stock ruleset (D11).** `./gradlew ktlintCheck`; `./gradlew
  ktlintFormat` auto-fixes. No detekt yet.
- **Android Lint is a gate, not a report (#845).** `abortOnError`,
  `warningsAsErrors`, `checkDependencies`, and a committed baseline
  (`app/lint-baseline.xml`). `./gradlew lint` is green on main and `android.yml`
  runs it on every PR, so a *new* finding fails the PR. Regenerate the baseline
  with `./gradlew updateLintBaseline` only when a finding is deliberately
  accepted, never to silence one you introduced. The baseline keys on file
  path, so a pure file move un-filters accepted findings: regenerate and diff
  by issue id. The one check disabled outright is `NewerVersionAvailable`
  (network-resolved; Dependabot owns bumps). `npm run lint` is ktlint only.
- **Three test layers, all in `testDebugUnitTest`** (D17's "JVM only, no
  Robolectric" is superseded; decision log 2026-09-26):
  1. **Pure logic and ViewModels** on the plain JVM: `runTest` +
     `MainDispatcherRule`, fakes from `src/test/.../testing/` (`Fake…Service`,
     one per `…Servicing` seam). `PebblesTestHarness` provides the three
     ambient locals for anything that composes.
  2. **Whole-app UI tests on Robolectric (#857).** A test under
     `src/test/.../ui/` extends `testing/AppUiTest`, carries
     `@HiltAndroidTest`, and launches the real `MainActivity` over
     `testing/FakeServicesModule`: every `…Servicing` seam bound to its fake,
     and a `SupabaseClient` on a Ktor `MockEngine` for the services with no
     seam, so nothing reaches a network or `BuildConfig`. Inject the fake
     (`FakePathService`), arm it, *then* `launch()`. Find nodes by string
     resource (`onText(R.string.…)`) and fall back to `JourneyTags` only for
     what has no text. A bar the navigation suite hides stays composed
     off-screen: assert `assertIsNotDisplayed()`, not `assertDoesNotExist()`.
     Process death is `restartAfterProcessDeath()`;
     `ActivityScenario.recreate()` is a config change, keeps every ViewModel,
     and proves nothing about `SavedStateHandle`.
  3. **Architecture tests.** `ArchitectureBoundaryTest` (the `core/`/`features/`
     rule above) and `ThemeLiteralsTest` (no colour, corner or font-size
     literals). If one fires, fix the code; the frozen list is a ratchet with a
     size assertion guarding it.
- **No instrumented tests.** `:baselineprofile`'s on-device journey is a
  profiling tool run by hand, not a test suite.
- **Coverage is reported, not gated (#857).** Kover measures
  `testDebugUnitTest` (Robolectric included); `android.yml` posts the totals
  and a per-package table to the run summary and uploads the HTML report as
  `android-coverage`. Locally: `./gradlew koverHtmlReportDebug`. Add a `verify`
  floor only once a few PRs of numbers say where it belongs.

### Screenshot validation gate (#847)

**Compose Preview Screenshot Testing** (`com.android.compose.screenshot`) renders
the `@PreviewTest` composables in `src/screenshotTest/` to PNGs on the JVM (no
device, no Robolectric). It is a **regression gate**: the reference PNGs are
committed under `app/src/screenshotTestDebug/reference/`, and `android.yml` runs
`validateDebugScreenshotTest` on every PR. A render that moves past the threshold
fails the PR and uploads the reference/actual/diff triptych as
`ui-screenshot-diffs`.

- **Previews render a screen's stateless content layer**, fed a UiState and the
  harness's fakes, so they cover the screen's own chrome and error branch. Add
  a preview per screen and state as UI lands.
- **Changing UI turns the check red. That is the gate working, not a bug.** The
  fix is to re-baseline, never to weaken the threshold or re-ignore the
  references.
- **Re-baseline is one step: add the `rebaseline-screenshots` label to the PR.**
  `android-screenshots.yml` re-renders and commits the new PNGs to the branch. The
  same workflow runs from the Actions tab on any ref; dispatched on `main` it opens
  a branch instead of writing to it.
- **After a re-baseline the Android check is still red, and it needs a hand.** The
  push is attributed to `github-actions[bot]`, so the runs queued against the new
  commit are held at `action_required`: approve them from the PR's Checks tab (or
  `gh api --method POST repos/<owner>/<repo>/actions/runs/<id>/approve`), or push
  any further commit yourself. Do **not** re-run the failed run: a re-run replays
  the commit it was created for, which is the tree from before the re-baseline.
  The workflow's own step summary says all of this at the moment you need it.
- **Never commit references rendered on your own machine.** `./gradlew
  updateDebugScreenshotTest` locally is the right way to *look* at a change, but
  CI is the authority. macOS and the CI runner disagree on every render that
  draws vector art (pebble silhouettes come out one colour level apart, which
  byte-exact comparison counts as every filled pixel), and dozens of renders
  fail locally on `main` too. Render locally, review, then let the
  `rebaseline-screenshots` label regenerate the committed set on the runner.
  `./gradlew validateDebugScreenshotTest` locally still tells you *which*
  previews moved; judge a local run by diffing against `main`'s local run.
- **The threshold lives in `app/build.gradle.kts`**, set on the validation task
  because alpha16 exposes no DSL for it. It is calibrated (0.05% of pixels)
  against a measured real change, and the comment there carries the numbers. Do
  not widen it to paper over a host-platform difference.
  `libs.versions.toml` pins the plugin with `strictly` for the same reason: any
  layoutlib movement re-baselines every reference at once, so that has to be a
  deliberate commit that re-runs the re-baseline job, not a Dependabot drive-by.
- **Variants are multipreview annotations in `PreviewVariants.kt`.** Stack
  `@PreviewLargeFont` / `@PreviewLargeFontTall` (font scale 2.0, the
  accessibility ceiling), `@PreviewFrench` (`values-fr`) or `@PreviewWide` /
  `@PreviewWideTall` (840 dp and 1024 dp) on an existing `@PreviewTest` function
  to add a render without touching its body. The original reference's file
  name is unaffected, so adding a variant never re-baselines what was already
  there.

## Baseline profile (#856)

- **The release build ships a baseline profile and a startup profile**,
  committed under `app/src/release/generated/baselineProfiles/`
  (`baseline-prof.txt`, `startup-prof.txt`). `profileinstaller` hands the
  baseline profile to ART on install, so the first launches after an install or
  update run pre-compiled code; R8 uses the startup profile to put what a cold
  start touches in the primary dex. Every `bundleRelease` packs the committed
  files; nothing re-generates them in CI (`automaticGenerationDuringBuild =
  false`), because generation needs a device and a signed-in test account.
- **Regenerate by hand when startup or Path changes shape** (a new launch-time
  dependency, a new screen on the cold path, a big Path rework):
  `ANDROID_SERIAL=<device> ./gradlew :app:generateBaselineProfile`, then commit
  both files. Set `ANDROID_SERIAL` whenever more than one device is attached,
  or the task runs on all of them. The journey (`:baselineprofile`,
  `PebblesJourney.kt`) signs in with `BENCHMARK_EMAIL` / `BENCHMARK_PASSWORD`
  from `secrets.properties`: a throwaway account with pebbles on its Path,
  never a real one. Without them it profiles Welcome and Auth only; check the
  diff's size before committing a regeneration.
- **The journey finds the UI by `testTag`** (`core/common/JourneyTags.kt`,
  exposed as resource ids by `MainActivity`). `:baselineprofile` is a separate
  APK and repeats the strings, so renaming a tag fails nothing; it silently
  shortens the profiled journey.
- **Measure with** `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
  -P android.testInstrumentationRunnerArguments.class=app.pbbls.android.baselineprofile.StartupBenchmark`
  (cold start, no profile vs the committed one; results in the task's
  `benchmarkData.json`). A phone is the authority. An emulator on a loaded host
  swings by seconds; only its paired `None` vs `BaselineProfile` rows compare.
- **Anything added to `PebblesApp.onCreate` runs before the first frame of
  every launch.** It is empty on purpose.

## Release & distribution (Play internal testing)

- **The app auto-ships to Play internal testing from CI.**
  `.github/workflows/android-release.yml` builds a **signed release AAB**
  (`bundleRelease`) and publishes it on every push to `main`, on a PR labelled
  `deploy-beta`, or via manual dispatch. It is separate from `android.yml`, which
  is the pre-merge gate. Full setup + troubleshooting:
  the [`docs/android-play-deploy.md`](../../docs/android-play-deploy.md) runbook.
- **The release build is minified (#845).** `isMinifyEnabled` +
  `isShrinkResources` on `proguard-android-optimize.txt`. `app/proguard-rules.pro`
  is deliberately two rules long. **Check a library's own artifact for a bundled
  `proguard.txt` / `META-INF/proguard/*.pro` before adding a keep rule here**;
  Ktor, kotlinx-serialization, coroutines, Coil and OkHttp all ship their
  own, and supabase-kt, AndroidSVG and zxing use no reflection at all. A
  redundant `-keep` is not free: it widens the keep radius and undoes the
  shrinking. `android.yml` runs an **unsigned `bundleRelease`** on every PR
  because R8 and `lintVitalRelease` run nowhere else. It cannot catch
  reflection R8 strips *silently*, so **adding a library that reflects means
  smoke-testing a minified build by hand** before merge.
- **Obfuscated stack traces need the mapping file.** `-keepattributes
  SourceFile,LineNumberTable` keeps line numbers;
  `app/build/outputs/mapping/release/mapping.txt` goes to Play with the bundle and
  is kept as a workflow artifact. A release whose mapping is lost can never have
  its crashes decoded.
- **`versionCode` derives from `GITHUB_RUN_NUMBER`** (`build.gradle.kts`). Play
  requires every upload to strictly increase, so never pin it to a constant.
  Re-running a release run reuses its number, which only matters once a publish
  has actually reached Play (a failed publish leaves the code unused).
- **Release signing flows like the Supabase secrets (D8).** The `release`
  `signingConfig` reads the upload keystore + passwords from `KEYSTORE_FILE` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`: env vars in CI (decoded
  from the `KEYSTORE_BASE64` secret), or `secrets.properties` locally. With no
  keystore the release build stays **unsigned** rather than failing, the same
  fail-soft contract as the config secrets. Never commit a keystore (`*.jks` is
  git-ignored).

## Where to look next

This file describes the shape, not the inventory. For what exists right now:

- **Screens and their state:** the hosted Arkaik map (`arkaik` skill), and
  `navigation/PebblesKeys.kt` for every destination.
- **Why something is the way it is:** `docs/decisions/log.md`, searching for
  `android`. The last entry on a topic wins.
- **How a feature was built:** `docs/superpowers/specs/` and
  `docs/superpowers/plans/`. The M61 designs are dated 2026-09-20 onward.
- **What is still missing against iOS:** the open issues on the `android`
  label. `docs/superpowers/specs/2026-07-16-android-parity-audit.md` is the
  M39-era snapshot and is historical.
