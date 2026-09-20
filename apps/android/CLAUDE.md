# @pbbls/android — agent context

Native Android app for Pebbles. Kotlin + Jetpack Compose, minSdk 33, phone-only,
portrait. It mirrors `apps/ios` 1:1 — same architecture, same tokens, same funnel.
When this file says "mirror X", read the named iOS file under `apps/ios/Pebbles/`
and port its structure, not just its behavior.

> This app was bootstrapped in milestone **M38 · Android App** (design doc:
> `docs/superpowers/specs/2026-07-10-android-bootstrap-design.md`, decisions
> D1–D18 — the doc's draft numbering "M37" means this milestone). Sub-project
> **A** is the scaffold; B adds the design system, C the entry funnel, D the
> read-only Path. All four have shipped (PRs #533–#536).

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
table. When it defaults to a pattern the M61 issues have not landed yet
(Hilt, ViewModel per screen, Navigation 3), write new code to the target and
leave existing code to the issue that owns it.

The `android` CLI (`android docs search "…"`, `android docs fetch kb://…`) is
the authoritative reference for any API question neither set answers; prefer
it to web search. Its device and emulator commands are usable: the maintainer's
machine **does** have a JDK, an Android SDK and AVDs (this file previously said
it did not — corrected 2026-09-17 after building, installing and running a
release APK on the `oxymore-eclipse` Pixel 7 AVD). `ANDROID_HOME` is not exported
by default, so set it (`export ANDROID_HOME=$HOME/Library/Android/sdk`) or
`scripts/gradle-if-sdk.sh` silently no-ops and `npm run build --workspace=@pbbls/android`
looks green having done nothing. CI remains the authority; local is now the fast
first check, and the only way to smoke-test a minified build by hand.

### This app's stack

| Topic | Use | Not |
| --- | --- | --- |
| Compose UI, state hoisting, recomposition, `LazyColumn`, side effects | `android-skills:compose` | |
| M3 compliance review, touch targets, a11y audit of a screen | `android-skills:android-ux` | |
| Screen architecture: `ViewModel`, `UiState`, effects channel, Hilt (#848, #849) | `android-skills:android-dev` | `android-skills:koin`; the DI target is Hilt |
| Repository error boundary, sealed `DataError` (#850) | `android-skills:android-data-layer` | |
| Coroutines and `Flow`, dispatcher injection, `Channel` vs `SharedFlow` | `android-skills:kotlin-coroutines`, `android-skills:kotlin-flows` | |
| Navigation: `NavDisplay`, `NavKey`, back stacks, Scenes (#852) | `navigation-3` | `android-skills:compose`; the target is Navigation 3, not `navigation-compose` |
| Snap thumbnails, signed-URL images | `android-skills:coil-compose` | |
| Package boundaries, visibility, `core/` vs `features/` (#851) | `android-skills:modularization` | |
| Writing or fixing JVM, Robolectric and screenshot tests (#847, #857) | `android-skills:android-testing` | `testing-setup`, which stands up new infra |
| Logcat, ADB, ANR traces, Compose recomposition bugs | `android-skills:android-debugging` | |
| `libs.versions.toml`, convention plugins, build speed | `android-skills:android-gradle-logic`, `android-skills:gradle-build-performance` | |
| AndroidX source lookups | `android-skills:android-source-search` | |

Not applicable here and never routed: `android-skills:android-retrofit`,
`android-skills:kmp-ktor`, `android-skills:kmp-boundaries` (supabase-kt owns
the network client), `android-skills:paging` (the Path loads whole),
`android-skills:datastore` (SharedPreferences is a deliberate D5 choice until
real settings exist), `android-skills:rxjava-migration`,
`android-skills:pdf-annotations`.

### Owned outright by the Google set

| Topic | Skill |
| --- | --- |
| Window size classes, foldables, tablets, content width cap (#855) | `adaptive` |
| System bar and IME insets, `enableEdgeToEdge`, sheet insets | `edge-to-edge` |
| R8 keep rules, `isMinifyEnabled`, mapping upload (#845) | `r8-analyzer` |
| Predictive back, `NavigationBackHandler`, `androidx.navigationevent` (#852) | `navigation-event` |
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
  Never hardcode Supabase keys. Never read `BuildConfig` from arbitrary code — go
  through `AppEnvironment`, which validates and fails loud. `secrets.properties`
  is git-ignored; `secrets.example.properties` is committed.

## Kotlin conventions

- **API 33 APIs only. No `Build.VERSION` / `if (SDK_INT >= …)` guards below 33.**
  The analog of the iOS "no `if #available`" rule. minSdk is 33 precisely so the
  modern floor (per-app language, predictive back, themed icons) is always there.
- **`@Composable` functions are PascalCase.** ktlint runs its stock ruleset
  (D11); the only accommodation is `.editorconfig`'s
  `ktlint_function_naming_ignore_when_annotated_with = Composable`. Don't add
  other ktlint config without a motivating incident.
- **State: plain service classes, constructor-injected by Hilt (#848, supersedes
  D4).** Each iOS `@Observable` service becomes a plain Kotlin class holding
  Compose state (`mutableStateOf` for UI-read values; `StateFlow` where a
  non-Compose consumer needs it), annotated `@Singleton class X @Inject
  constructor(…)`. Adding a service is that one annotation — do **not** add a
  `@Provides` unless Dagger genuinely cannot infer the constructor, and do not
  add a new `Local…Service` **for anything a screen calls** — see the carve-out
  below. `di/SupabaseModule` is the only place `AppEnvironment` is read.
- **Three CompositionLocals are permanent, the rest of the bridge is not.**
  `LocalEmotionPaletteService`, `LocalReferenceDataService` and
  `LocalSnapURLCache` carry ambient reference data read by *leaf* components
  (`PathPebbleRow`, `ValenceGlyph`, `WeekHeader`, the pickers), which is what a
  `CompositionLocal` is for; they stay (decision log, 2026-09-20). `ServiceGraph`
  is down to 10 entries and dies once `GlyphPickerSheet` and `RootScreen` have
  ViewModels. Write new screens against `hiltViewModel()`, never against a local,
  and do not add an entry to `ServiceGraph`.
- **Log, don't swallow.** Use `android.util.Log` (or a thin logger) with a
  consistent tag on every error path — mirror the web/iOS discipline that silent
  failures are bugs. No empty `catch` blocks. No `println`. JVM unit tests set
  `unitTests.isReturnDefaultValues`, so a `Log` call in a tested class no longer
  throws "not mocked" — injecting the logger as a lambda
  (`SnapUploadCoordinator.onLog`) is a pattern to stop copying, not to spread.
- **View-scoped async cancels with the view.** Use `LaunchedEffect` /
  `rememberCoroutineScope` / `viewModelScope`, never `GlobalScope`.
- **Let cancellation travel: `runCatchingCancellable`, never a bare `catch (e:
  Exception)`.** Both that and `kotlin.runCatching` swallow
  `CancellationException`, and a coroutine that catches its own cancellation
  keeps running inside a scope that believes it stopped. `ui/CoroutineErrors.kt`
  rethrows it first. The inverse case — a write that must finish once the request
  has left the device — is `withContext(NonCancellable)` around that section, not
  a catch.

### Screen architecture (#849)

One `@HiltViewModel` per stateful screen. The shape, of which
`AchievementsViewModel` + `AchievementsScreen` is the worked reference:

- **`StateFlow<XUiState>`, collected with `collectAsStateWithLifecycle()`** —
  not `collectAsState()`, which keeps collecting while the app is backgrounded.
- **`hiltViewModel()` comes from `androidx.hilt.lifecycle.viewmodel.compose`.**
  The `androidx.hilt.navigation.compose` one is the same function at its old
  address and is deprecated; the IDE still offers it first.
- **`XUiState` is a sealed interface: `Loading`, `Error(@StringRes …)`,
  `Content(…)`.** Never a bag of booleans beside the data — five flags admit 32
  combinations of which four are real, and the render code pays for the other 28.
  `when` over it with **no `else`**, so a new case is a compile error.
- **Derived values are getters (`by lazy`) on the case, not constructor fields.**
  A field has to be recomputed at every construction site and joins `equals`; a
  getter replaces a per-recomposition recompute with a per-state-value one.
- **One-shot effects (navigate, snackbar, haptic) go through `UiEffects<T>`**
  (`ui/UiEffects.kt`) and are collected with `ObserveUiEffects`, never held in
  state — a `StateFlow` replays to every new collector, so "navigate back" in
  state navigates back again after a rotation.
- **Writes run in `viewModelScope`**, never `rememberCoroutineScope` — leaving
  the composition must not cancel a request the server has already accepted.
- **`SavedStateHandle` only for what the server has not seen** (a typed draft, an
  unsaved form). Server-derived data is re-fetchable, so process death has
  nothing to restore that a reload would not produce.
- **A screen keeps a stateless overload taking the `XUiState`.** That is what
  makes the screen's own chrome, spinner and error branch screenshot-testable —
  a component gallery never covered them.
- **The ViewModel gets a JVM test** (`runTest` + `MainDispatcherRule`) driving
  load, error and save against a fake. That test is the bar for extracting the
  service's `…Servicing` interface; do not extract one ahead of it.

### supabase-kt sessionStatus-collector deadlock rule (ported verbatim from iOS)

When `SupabaseService` collects `auth.sessionStatus`, **never call back into
supabase-kt from inside the collector** — mutate `session` / `isInitializing`
state synchronously only. Re-entering the client from within its own status
emission deadlocks the auth actor (the iOS app hit this; the fix was to make the
observer a pure state assignment). Any network call reacting to a status change
happens in a separate coroutine, not inline in the collector.

## Data layer

- **Views never construct `SupabaseClient`.** They read `SupabaseService` from a
  CompositionLocal, the way iOS views take `@Environment(SupabaseService.self)`.
- **RPC-first (per root `AGENTS.md`).** Anything touching more than one table, or
  more than a simple single-row read/write, goes through a Postgres RPC — not
  stitched client calls (PostgREST has no client transactions). The entry funnel
  and read-only Path need zero DB changes; everything is already exposed
  (`path_pebbles()`, `v_emotions_with_palette`, auth).
- **Extract a `…Servicing` interface for a fake only when a test needs one** — not
  before. YAGNI, same as iOS.

## Error handling

- `AppEnvironment` throws `IllegalStateException` with setup instructions when a
  key is blank. That's a setup bug caught at launch, not a runtime condition — the
  build itself never fails on missing secrets.
- Runtime async failures must be surfaced: logged or reflected in view state.

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
  type (over `getIdentifier()`) — mirrors iOS `Emotion+Localized.swift` /
  `Domain+Localized.swift`. `ReferenceSlugs.kt` is the compile-time mirror of
  the live Supabase slugs (ported from the iOS file of the same name); adding
  a reference row server-side means updating `ReferenceSlugs.kt` AND both
  `strings.xml` files in the same change, or `LocalizationParityTest` fails.
- **`android:localeConfig`** (`res/xml/locales_config.xml`) enables per-app
  language (added in B). `LocalizationParityTest` (JVM unit test, no
  Robolectric — parses the `strings.xml` files directly since there's no
  Android resource system on the plain JVM) asserts en/fr key-set parity and
  that every `ReferenceSlugs` entry maps to a real resource id.
- Dates/numbers localize via the active `Locale` — never pin a formatter to a
  fixed locale.

## Folder layout

```
app/src/main/kotlin/app/pbbls/android/
  PebblesApp.kt          Application entry — constructs the service graph, Rive.init()
  MainActivity.kt        Single activity, hosts the Compose tree + auth gate
  DebugTokenPreviewScreen.kt  Design-system screenshot preview (B's temporary MainActivity home)
  features/<feature>/     welcome, auth, onboarding, path (matches Pebbles/Features)
  services/              SupabaseService, EmotionPaletteService, … (non-view code)
  components/            PebblesTextInput, PebblesCheckbox, PebblesPrimaryButton, CheckGlyph (Pebbles/Components)
  theme/                 PebblesTheme, Palettes, Spacing, Typography, PebblesText,
                          ReferenceSlugs, ReferenceStrings (Pebbles/Theme +
                          Pebbles/Features/Path/Models localization helpers)
  rive/                  RiveLogo (Pebbles Rive usage, e.g. WelcomeView.swift)
```

A 1:1 map of `apps/ios/Pebbles/{Features,Services,Components,Theme}`.

### Theme (sub-project B)

- `PebblesTheme` is both an object (`PebblesTheme.colors.system.*`,
  `.colors.accent.*`, `.spacing.*`, `.type.*`) and a `@Composable` wrapper
  (`PebblesTheme { content }`) that resolves light/dark from
  `isSystemInDarkTheme()` and provides all four CompositionLocals — same
  dual object+function pattern Compose's own `MaterialTheme` uses. Material 3
  is the rendering engine only; no dynamic color, no Material color roles in
  app code (D6).
- `PebblesTypography` exposes 18 `TextStyle` tokens directly (`.body`,
  `.headlineEmphasized`, …) rather than an enum + lookup — call `PebblesText`
  (not raw `Text`) so uppercase tokens (`meta`, `metaEmphasized`,
  `cardHeading`, `cardHeadingEmphasized`) get their case transform; Compose
  `TextStyle` has no text-case property.
- All "rounded" iOS tokens (SF Pro/Compact Rounded) map to **Nunito**
  (maintainer-approved 2026-07-11) — a single variable-font TTF
  (`res/font/nunito.ttf`, OFL, from the Google Fonts repo) declared at four
  weights via `FontVariation` in `Typography.kt`, not four separate files.
- Reference-data names (`emotion.<slug>`, `domain.<slug>`,
  `emotionCategory.<slug>`) resolve through `ReferenceStrings.referenceName`,
  never the DB `name` column directly — see Localization below.

### Launcher icon & splash (#846)

- **The launcher icon is a vector adaptive icon, generated — never hand-edited.**
  `res/mipmap-anydpi/ic_launcher.xml` composes three layers:
  `@color/ic_launcher_background` (the accent primary), plus
  `drawable/ic_launcher_foreground.xml` and `drawable/ic_launcher_monochrome.xml`
  (the themed-icon layer minSdk 33 exists for). Both drawables are produced by
  `node scripts/logo-svg-to-launcher-icon.mjs` from the *iOS* brand mark
  (`apps/ios/Pebbles/Resources/pbbls-logo-loader.svg`) — re-run it when the mark
  changes rather than editing the XML, or the two surfaces drift. There are no
  `mipmap-*dpi` PNG buckets: the layers are vectors and every supported device
  is ≥ API 26, so one file serves all densities.
- **`values/colors.xml` is only for what the platform reads before Compose runs**
  — the icon ground and the splash background (with its `values-night` override).
  It duplicates two values from `theme/Palettes.kt` because XML cannot read
  Kotlin; keep them in sync and do **not** grow it into a second palette.
- **The cold-start splash is the system splash** (`androidx.core:core-splashscreen`).
  `Theme.Pebbles.Starting` (parent `Theme.SplashScreen.IconBackground`) is the
  activity's declared theme and names `Theme.Pebbles` as its
  `postSplashScreenTheme`; it is a separate style rather than a re-parented
  `Theme.Pebbles` because a theme cannot be its own `postSplashScreenTheme`.
- **Nothing gates the launch on a duration.** `MainActivity.onCreate` calls
  `installSplashScreen()` before `super.onCreate` and holds the splash on
  `supabase.isInitializing`, with an 8 s ceiling mirroring iOS's
  `loaderCeilingSeconds` (decision log 2026-07-17) so a wedged auth cannot
  strand it. A warm signed-in launch therefore reaches Path immediately. Do not
  reintroduce a `delay(…)` in `RootScreen`: the 2.5 s hold this replaced was the
  bug.

### Rive rename map (D14)

Only the logo ships in B; the cairn (`pbbls-cairn.riv`) is D-optional and not
bundled. Android resource filenames must be lowercase
`[a-z0-9_]`, so the source is renamed on copy:

| iOS source (`apps/ios/Pebbles/Resources/`) | Android (`res/raw/`)          |
| ------------------------------------------- | ------------------------------ |
| `pbbls-logo-appear_idle.riv`                | `pbbls_logo_appear_idle.riv`   |

`RiveLogo` loads it with the default artboard + default timeline, autoplay
(no named state machine in the file) — mirrors `WelcomeView.swift`'s
`RiveViewModel(fileName:)` call, which also passes no artboard/SM args.

### Path rendering (sub-project D)

- **`render_svg` renders through `PebbleSvg`** (`features/path/render/`):
  AndroidSVG parses the RPC string after a literal `currentColor` →
  palette-hex substitution (D10, mirrors iOS `PebbleRenderView`). Injected
  hex must be **6-digit** — `EmotionPalette` truncates the DB's 8-digit
  `#RRGGBBAA` before anything reaches SVG markup (8-digit misparses).
- **Outline silhouettes** (the row backdrop) are byte-identical copies of the
  iOS assets, renamed on copy like the Rive files:

| iOS source (`apps/ios/Pebbles/Resources/Outlines/`) | Android (`res/raw/`) |
| --- | --- |
| `{size}-{polarity}.svg` (9 files) | `outline_{size}_{polarity}.svg` |

  Their `#FF00FF` sentinel fill is string-replaced with the palette fill hex
  (`PebbleOutlineBackdrop`); fill alpha rides separately as view alpha.
- **The valence-picker imagery shipped with the create flow (M39)** as
  res/raw SVGs normalized from the web line art and tinted via `currentColor`
  injection (`ValenceGlyph`/`ValenceAssets`) — not the iOS PDF assets. The iOS
  Path row still never uses them; the row is outline + `render_svg`. They are
  now the *collapsed form row's* imagery only: the valence fan draws the
  artworks below instead.
- **The valence fan's artworks** (`features/path/valence/`) are byte-identical
  copies of the iOS files, generated from the source PDFs by
  `apps/ios/Scripts/valence-art-to-svg.mjs` and renamed on copy like the
  outlines and the Rive files:

| iOS source (`apps/ios/Pebbles/Resources/ValenceArt/`) | Android (`res/raw/`) |
| --- | --- |
| `valence-{polarity}{Size}.svg` (9 files) | `valence_art_{size}_{polarity}.svg` |

  Each carries stroked paths at the widths the PDF drew them with (inked
  through `WobbleRenderer.glyphInk`) plus exactly one filled path — the
  fossil's spiral, which goes through `backdropArt` instead. Tracing a filled
  spiral as a centreline fills it in solid, so the split is load-bearing and
  `ValenceArtTest` asserts it. Do not re-derive these from the web line art.
- **The week-roll cairn is a static drawable** (`res/drawable/cairn_static.xml`)
  in v1; the iOS Rive state machine (`pbbls-cairn-states.riv`, with an
  `isSelected` input + `strokeColor` data binding) is a known fast-follow.
- **Engine-composed SVG fixtures** for screenshot tests regenerate via
  `npx tsx apps/android/scripts/gen-pebble-svg-fixtures.ts` (uses the real
  compositor sources in `packages/supabase`) — rerun when the engine changes.

## Lint & test

- **ktlint, stock ruleset (D11).** `./gradlew ktlintCheck`; `./gradlew
  ktlintFormat` auto-fixes. No detekt yet.
- **Android Lint is a gate, not a report (#845).** `abortOnError`,
  `warningsAsErrors`, `checkDependencies`, and a committed baseline
  (`app/lint-baseline.xml`, 60 accepted findings) — `./gradlew lint` is green on
  main and `android.yml` runs it on every PR. A *new* finding fails the PR.
  Regenerate the baseline with `./gradlew updateLintBaseline` only when a finding
  is deliberately accepted; never to silence one you introduced. The one check
  disabled outright is `NewerVersionAvailable` (network-resolved, so every
  upstream release would be a finding no baseline can hold — Dependabot owns
  bumps).
- **JUnit4 + `kotlinx-coroutines-test`, JVM unit tests only.** No Robolectric, no
  instrumented tests. Test pure logic (auth `canSubmit`, week grouping, valence
  mapping, palette parsing, slug resolution) and localization parity.
### Screenshot validation gate (#847)

**Compose Preview Screenshot Testing** (`com.android.compose.screenshot`) renders
the `@PreviewTest` composables in `src/screenshotTest/` to PNGs on the JVM (no
device, no Robolectric). Since #847 it is a **regression gate**, not a
render-to-view: the reference PNGs are committed under
`app/src/screenshotTestDebug/reference/`, and `android.yml` runs
`validateDebugScreenshotTest` on every PR. A render that moves past the threshold
fails the PR and uploads the reference/actual/diff triptych as
`ui-screenshot-diffs`.

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
  any further commit yourself. Do **not** re-run the failed run — a re-run replays
  the commit it was created for, which is the tree from before the re-baseline.
  The workflow's own step summary says all of this at the moment you need it.
- **Never commit references rendered on your own machine.** `./gradlew
  updateDebugScreenshotTest` locally is the right way to *look* at a change (it is
  ~35 s for the whole suite), but CI is the authority. Measured on this suite:
  macOS and the CI runner agree byte-for-byte on 108 of 162 renders and disagree
  on 54, by up to 1.77%. Text is not the problem — every divergent render is one
  that draws vector art, and the worst is **visually indistinguishable**: the
  pebble silhouettes come out one colour level apart, which byte-exact comparison
  counts as every pixel of the filled area. So render locally, review, then let
  the `rebaseline-screenshots` label regenerate the committed set on the runner.
  `./gradlew validateDebugScreenshotTest` locally still tells you *which* previews
  moved, which is the useful part even when the absolute pixels are yours.
- **The threshold lives in `app/build.gradle.kts`**, set on the validation task
  because alpha16 exposes no DSL for it. It is calibrated (0.05% of pixels)
  against a measured real change, not guessed — the comment there carries the
  numbers. Do not widen it to paper over a host-platform difference: loose enough
  to swallow that 1.77% is ~35x looser than the smallest real regression measured,
  i.e. no gate at all. `libs.versions.toml` pins the plugin with `strictly` for the same
  reason: any layoutlib movement re-baselines all 162 references at once, so that
  has to be a deliberate commit that re-runs the re-baseline job, not a Dependabot
  drive-by.
- **Variants are multipreview annotations in `PreviewVariants.kt`.** Stack
  `@PreviewLargeFont` / `@PreviewLargeFontTall` (font scale 2.0, the accessibility
  ceiling) or `@PreviewFrench` (`values-fr`) on an existing `@PreviewTest` function
  to add a render without touching its body — the original reference's file name is
  unaffected, so adding a variant never re-baselines what was already there. The
  baseline covers font scale 2.0 across the record flow, the create/edit forms and
  pickers, Settings and the `PebblesScreen` galleries, and `fr` across the funnel
  and Path.
- Add a preview per screen/state as real UI lands.

**Screens that read services cannot be previewed.** `SettingsScreen`,
`ProfileScreen`, `SoulsListScreen` and the rest read their `Local…Service`
CompositionLocals at the top, and those cannot be provided from a preview:
`ProfileService` needs a `SupabaseService`, whose constructor goes through
`AppEnvironment` and throws on a blank `BuildConfig.SUPABASE_URL` — the fork-PR
case. Those surfaces are covered by pure-component *galleries*
(`ProfileScreenshots.kt`, `SettingsScreenshots.kt`, …) that compose the real
components with the real string resources. A gallery catches a control that clips
its own text; it does not catch a regression in the screen's own scroll column or
top bar. The real fix is a stateless content layer per screen, the way
`PathScreen` has `PathContent` — #848/#849 own that architecture, so do not pull
it forward from a screenshot PR.

## Release & distribution (Play internal testing)

- **The app auto-ships to Play internal testing from CI.**
  `.github/workflows/android-release.yml` builds a **signed release AAB**
  (`bundleRelease`) and publishes it on every push to `main`, on a PR labelled
  `deploy-beta`, or via manual dispatch. It is separate from `android.yml`, which
  is the pre-merge gate. Full setup + troubleshooting:
  the [`docs/android-play-deploy.md`](../../docs/android-play-deploy.md) runbook.
- **The release build is minified (#845).** `isMinifyEnabled` +
  `isShrinkResources` on `proguard-android-optimize.txt`. `app/proguard-rules.pro`
  is deliberately two rules long — **check a library's own artifact for a bundled
  `proguard.txt` / `META-INF/proguard/*.pro` before adding a keep rule here**;
  Rive, Ktor, kotlinx-serialization, coroutines, Coil and OkHttp all ship their
  own, and supabase-kt, AndroidSVG and zxing use no reflection at all. A
  redundant `-keep` is not free: it widens the keep radius and undoes the
  shrinking. `android.yml` runs an **unsigned `bundleRelease`** on every PR
  precisely because R8 and `lintVitalRelease` run nowhere else — but it cannot
  catch reflection R8 strips *silently*, so **adding a library that reflects means
  smoke-testing a minified build by hand** before merge.
- **Obfuscated stack traces need the mapping file.** `-keepattributes
  SourceFile,LineNumberTable` keeps line numbers;
  `app/build/outputs/mapping/release/mapping.txt` goes to Play with the bundle and
  is kept as a workflow artifact. A release whose mapping is lost can never have
  its crashes decoded.
- **`versionCode` derives from `GITHUB_RUN_NUMBER`** (`build.gradle.kts`) — Play
  requires every upload to strictly increase, so never pin it to a constant.
  Re-running a release run reuses its number, which only matters once a publish
  has actually reached Play (a failed publish leaves the code unused).
- **Release signing flows like the Supabase secrets (D8).** The `release`
  `signingConfig` reads the upload keystore + passwords from `KEYSTORE_FILE` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` — env vars in CI (decoded
  from the `KEYSTORE_BASE64` secret), or `secrets.properties` locally. With no
  keystore the release build stays **unsigned** rather than failing — the same
  fail-soft contract as the config secrets. Never commit a keystore (`*.jks` is
  git-ignored).

## Current state (post M39 · Android Record Flow)

- `PebblesApp` constructs the full service graph — `SupabaseService`, then
  `EmotionPaletteService`, `PathService`, `PebbleDetailService`, `SnapURLCache`,
  `ReferenceDataService`, `PebbleWriteService`, `GlyphService`, and
  `KarmaNotificationService` (by constructor) — and implements Coil's
  `SingletonImageLoader.Factory` (OkHttp fetcher registered explicitly).
  `MainActivity` provides one CompositionLocal per service; `LocalSnapURLCache`
  is **nullable-default** so previews render without it.
- `features/path/PathScreen.kt` is the timeline plus the M39 record flow:
  `path_pebbles()` → `WeekRollBuilder` → week roll + header + pager, with
  full-screen conditionally-composed covers for create (`CreatePebbleScreen`
  hosting the shared `PebbleForm` + modal picker sheets), detail
  (`PebbleDetailScreen`), and edit (`EditPebbleScreen`), long-press delete, and
  the karma flash pastille overlaid in `RootScreen`. The stateless
  `PathContent` layer is what screenshot previews drive. The temporary
  sign-out button stays until Profile exists. Still missing: stats bar, photo
  attach, glyph carving/store — see the parity audit
  (`docs/superpowers/specs/2026-07-16-android-parity-audit.md`).
- **Two composers coexist on purpose (M58, #725).**
  `features/path/record/RecordFlowScreen.kt` is the eleven-step flow and the
  default: tapping "New pebble" opens it, **long-pressing the same entry opens
  `CreatePebbleScreen`**, and resuming a draft enters the flow at its first
  unanswered mandatory step. Neither is dead code — deleting the form also
  removes the fallback for what is still an experiment, and `EditPebbleScreen`
  needs `PebbleForm` regardless. `RecordFlowModel` owns every interaction
  precisely so the haptic-on-every-tap rule is structural rather than a
  discipline; a step that mutates state without going through it is a bug. Its
  step order carries three dependencies (photo→when for EXIF, valence→emotion
  for category ordering, privacy last against publish) — reordering keeps the
  cost and drops the reason. Known divergences and their follow-ups are in
  `docs/decisions/log.md` (2026-08-24).
- `RootScreen` warms the palette cache at launch and flushes the signed-URL
  cache when the session drops to null. It holds **no** fixed splash duration
  (#846) — see **Launcher icon & splash**.
- Leaf path composables take `palette` / data as **parameters**, not service
  reads — same previewability rule as the funnel screens.
- `MainActivity` hosts `RootScreen` (the auth gate / single NavHost), owns the
  system splash, and forwards `pebbles://auth-callback` deep links to
  supabase-kt's `handleDeeplinks` (`onCreate` + `onNewIntent`,
  `launchMode="singleTask"`). `DebugTokenPreviewScreen` is retained only as a
  screenshot-test preview.
- Onboarding illustrations render a placeholder surface — the iOS asset-catalog
  artwork is not yet exported to Android drawable densities (milestone risk 6,
  needs the maintainer's design sources).
- Funnel screens take plain action lambdas rather than reading the service
  directly, so they stay previewable (screenshot tests) and keep the supabase-kt
  calls at the `RootScreen`/NavHost binding layer.
