# @pbbls/android — agent context

Native Android app for Pebbles. Kotlin + Jetpack Compose, minSdk 33, phone-only,
portrait. It mirrors `apps/ios` 1:1 — same architecture, same tokens, same funnel.
When this file says "mirror X", read the named iOS file under `apps/ios/Pebbles/`
and port its structure, not just its behavior.

> This app is built up across milestone **M37** (design:
> `docs/superpowers/specs/2026-07-10-android-bootstrap-design.md`, decisions
> D1–D18). Sub-project **A** is the scaffold; B adds the design system, C the
> entry funnel, D the read-only Path. Much of what this file mandates (services,
> localization, the deadlock rule) has no code yet — it is the contract B–D work
> under.

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
- **Reference-data names resolve through a slug-keyed map**, never `.name`
  directly on a read path: `emotion.<slug>.name` → `R.string.emotion_<slug>_name`,
  falling back to the DB `name`. Explicit map (compile-checked, greppable) over
  `getIdentifier()`.
- **`android:localeConfig`** enables per-app language (added in B). Unit tests
  assert en/fr key-set parity and that every reference-slug entry has both
  translations.
- Dates/numbers localize via the active `Locale` — never pin a formatter to a
  fixed locale.

## Folder layout

```
app/src/main/kotlin/app/pbbls/android/
  PebblesApp.kt          Application entry — constructs the service graph
  MainActivity.kt        Single activity, hosts the Compose tree + auth gate
  features/<feature>/     welcome, auth, onboarding, path (matches Pebbles/Features)
  services/              SupabaseService, EmotionPaletteService, … (non-view code)
  components/            PebblesTextInput, PebblesPrimaryButton, … (Pebbles/Components)
  theme/                PebblesTheme, palettes, spacing, typography (Pebbles/Theme)
```

A 1:1 map of `apps/ios/Pebbles/{Features,Services,Components,Theme}`.

## Lint & test

- **ktlint, stock ruleset (D11).** `./gradlew ktlintCheck`; `./gradlew
  ktlintFormat` auto-fixes. No detekt yet.
- **JUnit4 + `kotlinx-coroutines-test`, JVM unit tests only.** No Robolectric, no
  instrumented tests, no screenshot tests (mirrors the iOS "no UI tests" rule).
  Test pure logic (auth `canSubmit`, week grouping, valence mapping, palette
  parsing, slug resolution) and localization parity.

## What's scaffolded but not used yet (sub-project A)

- `PebblesApp` is an empty `Application` — the service graph lands in C.
- No app icon slot — the launcher shows the default system icon. Expected.
- `PlaceholderScreen` uses default Material 3 color schemes only because the token
  system doesn't exist yet; the real `PebblesTheme` tokens replace it in B.
