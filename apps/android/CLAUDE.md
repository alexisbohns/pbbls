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
- **State: plain service classes, manual injection, CompositionLocals — no DI
  framework (D4).** Each iOS `@Observable` service becomes a plain Kotlin class
  holding Compose state (`mutableStateOf` for UI-read values; `StateFlow` where a
  non-Compose consumer needs it). `PebblesApp` (Application) constructs the graph
  exactly like `PebblesApp.swift` — `SupabaseService` first, dependents take it by
  constructor — and `MainActivity` provides them via `CompositionLocalProvider`
  (one `staticCompositionLocalOf` per service, the `@Environment(Type.self)`
  analog). No Hilt, no Koin.
- **Log, don't swallow.** Use `android.util.Log` (or a thin logger) with a
  consistent tag on every error path — mirror the web/iOS discipline that silent
  failures are bugs. No empty `catch` blocks. No `println`.
- **View-scoped async cancels with the view.** Use `LaunchedEffect` /
  `rememberCoroutineScope` / `viewModelScope`, never `GlobalScope`.

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
  Path row still never uses them; the row is outline + `render_svg`.
- **The week-roll cairn is a static drawable** (`res/drawable/cairn_static.xml`)
  in v1; the iOS Rive state machine (`pbbls-cairn-states.riv`, with an
  `isSelected` input + `strokeColor` data binding) is a known fast-follow.
- **Engine-composed SVG fixtures** for screenshot tests regenerate via
  `npx tsx apps/android/scripts/gen-pebble-svg-fixtures.ts` (uses the real
  compositor sources in `packages/supabase`) — rerun when the engine changes.

## Lint & test

- **ktlint, stock ruleset (D11).** `./gradlew ktlintCheck`; `./gradlew
  ktlintFormat` auto-fixes. No detekt yet.
- **JUnit4 + `kotlinx-coroutines-test`, JVM unit tests only.** No Robolectric, no
  instrumented tests. Test pure logic (auth `canSubmit`, week grouping, valence
  mapping, palette parsing, slug resolution) and localization parity.
- **Compose Preview Screenshot Testing** (`com.android.compose.screenshot`)
  renders `@PreviewTest` composables in `src/screenshotTest/` to PNGs on the JVM
  (no device, no Robolectric). CI runs `updateDebugScreenshotTest` and uploads the
  `ui-screenshots` artifact so the UI is reviewable without a local SDK — this
  deliberately re-enables screenshot tooling that the milestone design (D17)
  deferred, so the SDK-less maintainer can review UI. It is **render-to-view**,
  not a regression gate: references are git-ignored and nothing fails on a visual
  change. To adopt visual-regression later, commit the references and switch CI to
  `validateDebugScreenshotTest`. Add a preview per screen/state as real UI lands.

## Release & distribution (Play internal testing)

- **The app auto-ships to Play internal testing from CI.**
  `.github/workflows/android-release.yml` builds a **signed release AAB**
  (`bundleRelease`) and publishes it on every push to `main`, on a PR labelled
  `deploy-beta`, or via manual dispatch. It is separate from `android.yml`, which
  stays the debug / lint / test / screenshot CI. Full setup + troubleshooting:
  the [`docs/android-play-deploy.md`](../../docs/android-play-deploy.md) runbook.
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
- `RootScreen` warms the palette cache concurrently with the splash hold and
  flushes the signed-URL cache when the session drops to null.
- Leaf path composables take `palette` / data as **parameters**, not service
  reads — same previewability rule as the funnel screens.
- No app icon slot — the launcher shows the default system icon. Expected.
- `MainActivity` hosts `RootScreen` (the auth gate / single NavHost) and
  forwards `pebbles://auth-callback` deep links to supabase-kt's
  `handleDeeplinks` (`onCreate` + `onNewIntent`, `launchMode="singleTask"`).
  `DebugTokenPreviewScreen` is retained only as a screenshot-test preview.
- Onboarding illustrations render a placeholder surface — the iOS asset-catalog
  artwork is not yet exported to Android drawable densities (milestone risk 6,
  needs the maintainer's design sources).
- Funnel screens take plain action lambdas rather than reading the service
  directly, so they stay previewable (screenshot tests) and keep the supabase-kt
  calls at the `RootScreen`/NavHost binding layer.
