# Android app bootstrap — scaffold, funnel, and a read-only Path

> Milestone-level design for the Android bootstrap — drafted as "M37"; created on GitHub as **M38 · Android App** (docs referring to "M37" mean this milestone). Four sub-projects **A–D**, each its own issue + spec + plan + PR, per the M36 precedent. Foundation: decision-log entry **2026-07-10 — Android app: native Kotlin + Jetpack Compose, mirroring the iOS architecture** (`docs/decisions/log.md`). Issues: #528 (A) · #529 (B) · #530 (C) · #531 (D) — all shipped (PRs #533–#536).

## Goal

Stand up `apps/android` as a native Kotlin + Jetpack Compose app that mirrors `apps/ios` 1:1 — same architecture, same tokens, same funnel — and take a user from cold install through the full entry funnel (Welcome → email/Google auth → session gate → Onboarding) into a **read-only** Path timeline rendering real pebbles from `path_pebbles()`. Along the way the repo gains its **first CI** (GitHub Actions), an `android` scope label, and an `apps/android/CLAUDE.md` that encodes the conventions agents follow for all future Android work.

The finished iOS app is the reference implementation. Where this design says "mirror X", the implementer reads the named iOS file and ports its structure, not just its behavior.

## Non-goals

- **Any write path.** No pebble creation/edit/delete, no enrichment, no glyph carving, no karma UI. The timeline is read-only.
- **Apple sign-in.** Email/password + Google OAuth only in v1. Consequence (accepted): accounts created on iOS with Sign in with Apple cannot log in on Android until Apple web-OAuth is added in a later milestone.
- **Play Store distribution.** No release signing, no Play Console, no versioning scheme. Debug APKs side-loaded by the maintainer are the delivery mechanism. Release engineering is a future milestone.
- **Instrumented/UI tests.** JVM unit tests only, mirroring the iOS "no UI tests for now" rule.
- **Codegen or shared types.** Hand-written `@Serializable` models per surface (settled in the decision log; do not relitigate).
- **Profile, Lab, Glyph, Karma, widgets, notifications** — everything past the Path read surface.
- **Tablet/foldable layouts.** Phone-only, portrait, matching iPhone-only iOS.

## Background: what exists today

- `apps/ios` — SwiftUI, iOS 17+, ~16k LOC, the architecture template: `PebblesApp` (`@main`) constructs `@Observable` services (`SupabaseService`, `EmotionPaletteService`, `ReferenceDataService`, `PathStatsService`, `SnapURLCache`, `KarmaNotificationService`) and injects them via `.environment(...)`; `RootView` is the auth gate; features live in `Pebbles/Features/<Feature>/`; theme tokens in `Pebbles/Theme/`.
- iOS config chain: `Config/Secrets.xcconfig` (git-ignored, `Secrets.example.xcconfig` committed) → `Info.plist` → `AppEnvironment` (`fatalError` on missing). Exactly two keys: `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
- `@pbbls/ios` npm wrapper: `generate`/`build`/`lint`/`test` scripts, no `dev`. `turbo.json` defines only `build`/`dev`/`lint` tasks.
- **No CI exists** — no `.github/workflows/`. iOS builds on Xcode Cloud (`ci_scripts/ci_post_clone.sh`). Android brings the repo's first GitHub Actions workflow.
- `packages/rive` holds shared `.riv` assets, consumed **by copy** (web → `public/animations`, iOS → `Pebbles/Resources`).
- Business logic lives in Postgres RPCs (`AGENTS.md` "prefer RPCs"); the entry funnel + Path read surface need **zero** DB changes — everything Android reads (`path_pebbles()`, `v_emotions_with_palette`, auth) already exists and is exercised by two other surfaces.
- Arkaik: ~96 view nodes already carry `"android"` in `platforms`; the entry-funnel nodes are `V-landing → V-login / V-register → F-onboarding (V-onboarding-intro/concept/qualify/carving) → V-home / V-timeline`.

## Milestone-level settled choices

- **Scope:** scaffold + CI + design system + entry funnel + read-only Path timeline (the decision log's "auth + Path skeleton").
- **Auth v1:** email/password with iOS-parity validations (lowercase + strip `+`, must contain `@` and `.`, password ≥ 6, signup gated on two consent checkboxes writing `terms_accepted_at`/`privacy_accepted_at` into user metadata) + Google via Supabase hosted OAuth. No Apple.
- **minSdk 33** (Android 13) — modern-floor policy analogous to iOS-17-only; unlocks per-app language (clean en/fr story), predictive back, themed icons. No `Build.VERSION` guards below 33.

## Sub-project decomposition

Four sub-projects, each one PR, strictly sequenced A → B → C → D. Each gets its own spec + plan per superpowers conventions.

### A — Scaffold, CI, and monorepo wiring

**Goal:** an installable, CI-green, empty Pebbles app: the Gradle project, the secrets chain, lint + test harness, the GitHub Actions workflow, and the npm/Turbo wrapper — everything later sub-projects build *inside* rather than set up.

- `apps/android/` Gradle project: Kotlin DSL, version catalog (`gradle/libs.versions.toml`), committed wrapper, single `:app` module, namespace `app.pbbls.android`, minSdk 33.
- `MainActivity` + `PebblesApp` (Application) + a placeholder composable (app name, system background) — enough to prove the toolchain end to end.
- Secrets chain per D8; ktlint (default rules); JUnit4 + `kotlinx-coroutines-test` with one real test.
- `.github/workflows/android.yml` — the repo's first workflow (D12).
- `@pbbls/android` package.json with SDK-absence-tolerant scripts (D13).
- `apps/android/CLAUDE.md` (D18) + `apps/android/README.md` (maintainer setup: Android Studio, secrets file, device install).
- Create the **`android` scope label** on GitHub (gap flagged since the iOS-era plans).

Issue title: `[Feat] Android scaffold: Gradle project, first GitHub Actions CI, Turbo wiring`

### B — Design system: theme tokens, fonts, core components, Rive

**Goal:** port the iOS Theme layer so every later screen composes from tokens, never literals — and prove Rive playback works.

- `theme/` package: `SystemPalette` + `AccentPalette` (D6) with light/dark values from the iOS asset catalog; accent primary `#C07A7A` + `primaryHex` exposure for SVG injection.
- `Spacing` object (xs 3 / sm 10 / md 13 / lg 17 / xl 22 / xxl 34, in `.dp`).
- `PebblesFont` token catalog mirroring `Font+Pebbles.swift` (all tokens incl. tracking + uppercase); bundled fonts per D7.
- `PebblesTheme` composable providing palette/spacing/typography via CompositionLocals (no Material dynamic color).
- Core components mirrored from `Pebbles/Components/`: `PebblesTextInput`, `PebblesCheckbox`, `PebblesPrimaryButton`.
- Rive: rive-android dependency, assets copied from `packages/rive` into `res/raw` (D14), a `RiveLogo` composable proving `pbbls_logo_appear_idle.riv` plays.
- Debug-only token preview screen mirroring `ColorTokensPreview.swift` — the maintainer's visual verification surface.
- Localization plumbing: `values/strings.xml` + `values-fr/strings.xml`, `locales_config.xml`, slug-keyed reference-name helper (D9), en/fr key-parity unit test.

Issue title: `[Feat] Android design system: theme tokens, fonts, core components, Rive assets`

### C — Entry funnel: Welcome, auth, session gate, onboarding

**Goal:** the full pre-app experience — cold launch to authenticated, onboarded user — with auth logic unit-tested to iOS parity.

- `SupabaseService` (D4): single supabase-kt client from `AppEnvironment`; session exposed by collecting `auth.sessionStatus` into `session: Session?` + `isInitializing: Boolean`; `signIn` / `signUp` (consent timestamps in the `signUpWith(Email)` data block) / `signInWithGoogle` (D16) / `signOut`. **Hardened rule ported:** never call back into supabase-kt from inside the status collector; mutate state synchronously only (mirror the iOS deadlock comment verbatim in `apps/android/CLAUDE.md`).
- `RootScreen` mirroring `RootView`: `canShowAuthedTabs = session != null && !isInitializing && minSplashDone(2.5s)`; unauthenticated → NavHost (Welcome → Auth); authenticated → Path with Onboarding as a full-screen overlay when `hasSeenOnboarding` (SharedPreferences, D5) is false.
- `WelcomeScreen`: Rive logo + timed 7-step reveal + 3-slide carousel (record/enrich/grow, `HorizontalPager`) + Create account / Log in / Google buttons + legal disclaimer with in-app-intercepted `pebbles://legal/*` links → legal sheet to `https://www.pbbls.app/docs/terms|privacy`.
- `AuthScreen`: login/signup segmented switcher; `canSubmit` extracted as a pure function with iOS-parity rules.
- `OnboardingScreen`: 4 paged steps (intro/concept/qualify/carving), skip/close, "Start your path" on the last page; sets the flag on completion.
- Deep link: `pebbles://auth-callback` intent-filter on `MainActivity` (`launchMode="singleTask"`), supabase-kt `handleDeeplinks` in `onCreate`/`onNewIntent` (D15).
- Unit tests: all 16 iOS `AuthViewLogicTests` cases ported; consent-metadata payload shape; onboarding-flag gating.

If the PR balloons, the pre-agreed slice is Welcome+Auth first, Onboarding second — but aim for one PR.

Issue title: `[Feat] Android entry funnel: welcome, email + Google auth, session gate, onboarding`

### D — Read-only Path timeline

**Goal:** the authenticated landing surface: real pebbles, grouped by ISO week, rendered with server-composed SVGs and emotion palettes — proving the whole supabase-kt → `@Serializable` models → Compose pipeline.

- Models (hand-written `@Serializable`, mirroring `Features/Path/Models`): `Pebble` (id, name, happened_at, created_at, intensity, positiveness, render_svg, emotion `{id, slug, name}`, first_snap_path), `EmotionWithPalette`, `Valence`, `WeekRollEntry`.
- `PathService`: `path_pebbles()` RPC (no params); `EmotionPaletteService`: loads `v_emotions_with_palette` once at splash, `palette(for:)` with accent-primary fallback.
- Week grouping and 9-case valence derivation ported as pure functions (`java.time` ISO week fields); valence images as drawables (`valence_<case>`, exported from the iOS asset catalog — see risk 6).
- `render_svg` rendering: `PebbleSvg` composable per D10 (AndroidSVG, `currentColor` substitution mirroring iOS).
- `PebbleRow` + `PathScreen`: week-paginated list, week-roll header (static cairn imagery acceptable in v1), temporary sign-out affordance (needed to re-test the funnel until Profile exists).
- Snap thumbnails: `SnapURLCache` analog (signed URLs via supabase-kt Storage) + Coil 3 — **slice-able**: drops to a fast-follow if the PR grows.
- Localization: emotion names via the slug-keyed helper (never `.name` directly); fr strings.
- Unit tests: week grouping (year boundaries), valence mapping (all 9 cases), palette hex parsing + fallback, `currentColor` substitution.

Issue title: `[Feat] Android read-only Path timeline`

**Dependencies:** A blocks everything; B blocks C and D (tokens/components); C blocks D (a session is required to call `path_pebbles()`; RootScreen hosts Path).

## Core design decisions (settled)

- **D1 — Single `:app` module.** No multi-module Gradle. Solo maintainer + agents; the iOS mirror is a single target with feature *folders*; multi-module buys parallelism and enforced boundaries this codebase doesn't need, at the cost of ceremony on every feature. Package structure supplies the boundaries: `app.pbbls.android.{features.{welcome,auth,onboarding,path},services,components,theme}` — a 1:1 map of `Pebbles/{Features,Services,Components,Theme}`. Revisit only if build times hurt.
- **D2 — Gradle: Kotlin DSL + version catalog, wrapper committed, exact pins.** `gradle/libs.versions.toml` is the single place versions live (AGP, Kotlin, Compose BOM, supabase-kt BOM, rive-android, AndroidSVG, Coil, ktlint plugin). This spec names **libraries, not versions** — the implementer pins the latest stable of each at A-time; later sub-projects don't bump unless required (deliberate bumps, own commit). JDK 21 toolchain. Wrapper validated in CI.
- **D3 — Identity.** Package/namespace/applicationId `app.pbbls.android` (mirrors `app.pbbls.ios`). App label "Pebbles" (brand, non-translatable). minSdk 33; **no `Build.VERSION` guards below 33** (the analog of the iOS "no `if #available`" rule). compileSdk/targetSdk = latest stable at implementation time.
- **D4 — State: plain service classes, manual injection, CompositionLocals. No DI framework.** Each iOS `@Observable` service becomes a plain Kotlin class holding Compose state (`mutableStateOf` for UI-read values; `StateFlow` where a non-Compose consumer needs it). `PebblesApp` (Application) constructs the graph exactly like `PebblesApp.swift` — `SupabaseService` first, dependents take it by constructor — and `MainActivity` provides them via `CompositionLocalProvider` (one `staticCompositionLocalOf` per service, the `@Environment(Type.self)` analog). No Hilt, no Koin: six hand-wired singletons don't need annotation processing and opaque errors. Test seams appear the iOS way — extract an interface at the moment a test needs a fake, not before.
- **D5 — Navigation: single activity, one NavHost, gate by composition.** The auth gate is **conditional composition** (mirroring `RootView`'s `if canShowAuthedTabs`), not navigation: unauthenticated → `NavHost` (`welcome` → `auth?mode=login|signup`); authenticated → `PathScreen` with Onboarding as a conditionally-composed full-screen surface (the `fullScreenCover` analog), back-press-blocked except via skip/close. `hasSeenOnboarding` in `SharedPreferences` — the `@AppStorage` analog; DataStore deferred until real settings exist.
- **D6 — Theming: custom token system over CompositionLocals; Material 3 as engine, never as skin.** Keep `material3` for foundation behavior, but **no dynamic color, no Material color roles in app code**. `PebblesTheme` provides `LocalSystemPalette`, `LocalAccentPalette`, spacing, and typography; screens read `PebblesTheme.colors.system.foreground` etc. Light/dark resolved at the theme root from `isSystemInDarkTheme()`, values ported from the iOS asset-catalog pairs. `AccentPalette.primaryHex = "#C07A7A"` exposed for SVG injection, mirroring `Palettes.swift`.
- **D7 — Fonts: port the token catalog; bundle a rounded OFL face for the SF Pro Rounded slot.** Ysabeau SemiBold and Reenie Beanie TTFs copy over as-is (`res/font`). SF Pro Rounded cannot ship on Android (Apple license); the slot is filled by a bundled OFL rounded face — **proposal: Nunito**. Every call site goes through the token enum, so swapping the face after a device taste-check is a one-file change. Maintainer sign-off on the face is an explicit B checkpoint.
- **D8 — Secrets: `secrets.properties` → `BuildConfig` → `AppEnvironment`, fatal at runtime, absent-tolerant at build.** Git-ignored `apps/android/secrets.properties` (`secrets.example.properties` committed) holds `SUPABASE_URL` + `SUPABASE_ANON_KEY`; `build.gradle.kts` emits `BuildConfig` fields, **defaulting to empty strings when the file is missing so the build always succeeds** (CI builds with no secrets). `AppEnvironment` validates on first access and throws `IllegalStateException` with the copy-the-example instruction — the iOS contract: setup bugs fail loud at launch, never at build.
- **D9 — Localization: `strings.xml` en/fr, slug-keyed reference names via an explicit map, API 33 per-app language.** `values/strings.xml` (en, default) + `values-fr/strings.xml`; brand strings `translatable="false"`. Reference-data names resolve through an explicit `ReferenceSlugs` map slug → `R.string` id (`emotion.<slug>.name` → `R.string.emotion_<slug>_name`), falling back to the DB `name` — explicit map over `getIdentifier()` because it's compile-checked and greppable; **never render `.name` directly on a read path**. `android:localeConfig` enables per-app language. Unit tests assert en/fr key-set parity and that every `ReferenceSlugs` entry has both translations.
- **D10 — `render_svg`: AndroidSVG, direct string parsing, iOS-style `currentColor` substitution.** `render_svg` arrives as a *string in an RPC row*; AndroidSVG's `SVG.getFromString()` → render-to-`Picture` fits that with zero image-loader ceremony (Coil-svg wraps the same engine in cache machinery built for URIs; a custom parser is a last resort). Color injection mirrors iOS `PebbleRenderView`: literal string-replace of `currentColor` with the palette hex **before** parsing. A `PebbleSvg` composable wraps parse+render with `remember` keyed on the SVG string. Fidelity is risk 2 with an early smoke-test task in D's plan.
- **D11 — Lint: ktlint, default rules, zero config.** Stock ruleset — the SwiftLint-defaults philosophy: the linter is a formatter-of-record, not a style debate. detekt deferred until a motivating incident.
- **D12 — CI: `.github/workflows/android.yml`, the repo's first workflow.** Triggers: `pull_request` + `push` to `main`, path-filtered to `apps/android/**` + the workflow file. One Linux job: checkout, Temurin 21, `gradle/actions/setup-gradle` (caching + wrapper validation), `./gradlew ktlintCheck testDebugUnitTest assembleDebug`. No secrets needed (D8). `concurrency` with cancel-in-progress. CI invokes **Gradle directly, not Turbo**. The debug APK uploads as a workflow artifact — the maintainer's install channel given the SDK-less dev container.
- **D13 — npm/Turbo wrapping: `@pbbls/android`, scripts skip gracefully without an SDK.** `package.json`: private, scripts `build`/`lint`/`test` (no `dev`, matching iOS; no `generate` — Gradle *is* the source of truth). Each script runs through a guard (`scripts/gradle-if-sdk.sh`): no resolvable Android SDK → loud one-line warning, **exit 0**; otherwise exec `./gradlew <task>`. Skip-not-fail keeps root `turbo build` green in SDK-less environments; the masking risk is accepted because D12 is the authoritative, path-filtered gate. `turbo.json` unchanged.
- **D14 — Rive: rive-android, assets copied into `res/raw`.** Consume `packages/rive` by copy (the established pattern): `pbbls-logo-appear_idle.riv` → `res/raw/pbbls_logo_appear_idle.riv` (res naming forces lowercase_underscore; document the rename map in `apps/android/CLAUDE.md`). `RiveAnimationView` in `AndroidView` inside a `RiveLogo` composable; `Rive.init()` once in `PebblesApp.onCreate()`. Only the logo animation ships in this milestone; cairn states are D-optional.
- **D15 — Deep links: one custom scheme, two uses, one intent-filter.** `MainActivity` gets `launchMode="singleTask"` + an intent-filter for `pebbles://auth-callback`; supabase-kt configured with `scheme = "pebbles"`, `host = "auth-callback"`, `handleDeeplinks(intent)` in `onCreate` + `onNewIntent`. The redirect URL is **identical to iOS**, so the Supabase dashboard allowlist should need no change (verify, don't assume — risk 1). `pebbles://legal/*` never reaches the OS — intercepted in-compose like `LegalDisclaimerText.swift`.
- **D16 — Auth flow shape: hosted OAuth for Google; PKCE.** Mirror the iOS reasoning (`SupabaseService.swift` chose hosted web OAuth over the native SDK): supabase-kt `signInWith(Google)` external-flow with the deep-link return, `FlowType.PKCE`. No Google Sign-In SDK, no Credential Manager in v1 — parity and simplicity first. The display-name patch (`patchDisplayNameIfDefault` — overwrite trigger-seeded `'Pebbler'` from the OAuth name claim) ports as-is.
- **D17 — Tests: JUnit4 + kotlinx-coroutines-test, JVM only.** AGP's default unit-test setup. Targets mirror the iOS pyramid: pure-logic functions (auth `canSubmit`, week grouping, valence mapping, palette parsing, slug resolution) + localization parity. No Robolectric, no instrumented tests, no screenshot tests in v1.
- **D18 — `apps/android/CLAUDE.md` ships in A, not at the end.** It's a deliverable, not documentation debt: Kotlin conventions (no version guards below 33, services pattern, the ported sessionStatus-collector deadlock rule), data-layer rules (views never touch the client; RPC-first per `AGENTS.md`), localization rules (D9), folder layout, secrets rule. Agents implementing B–D work under it.

## Risks and open questions

1. **Google OAuth round-trip on Android** — highest-uncertainty item. Custom-tab flow visibly leaves the app and returns via deep link; `singleTask` + `onNewIntent` must be exactly right or the session silently never lands. Verify the Supabase dashboard already allowlists `pebbles://auth-callback` (shared with iOS). Mitigation: C's plan puts an end-to-end OAuth smoke test on a real device as the first checkpoint after wiring.
2. **`render_svg` fidelity.** AndroidSVG implements an SVG 1.1 subset; the server-composed markup was only ever validated against iOS SVGView and browsers. Mitigation: D front-loads a spike rendering 10–20 real production `render_svg` strings side-by-side with iOS screenshots. Fallback ladder: (a) server-side tweak to the composed SVG (needs a decision-log entry — affects three surfaces), (b) a scoped custom renderer for the known server-emitted subset.
3. **supabase-kt behavioral parity.** `SessionStatus` maps cleanly onto `isInitializing`/`session`, but refresh-failure semantics and metadata payload shapes differ in detail from supabase-swift. The ported auth tests cover client logic; server-visible behavior (consent metadata actually written) needs one manual verification against the real project.
4. **Maintainer device access.** The dev container has no Android SDK and can't run an emulator; every manual checkpoint needs the maintainer's Android Studio + device. CI's APK artifact is the hand-off. Agents get to "CI green"; only the maintainer gets to "verified on device."
5. **Font taste.** Nunito-as-SF-Rounded is a proposal; rounded faces carry a lot of the Pebbles feel. Explicit sign-off checkpoint in B; token indirection makes swaps cheap.
6. **Raster asset sourcing.** Onboarding images and the 9 valence images live in the iOS asset catalog at iOS densities; they need export to Android drawable densities (or vectors where sources exist) — needs the original design sources, i.e., the maintainer.
7. **Skip-not-fail npm scripts can mask local breakage** (accepted in D13; CI path filters are the real gate).
8. **Version drift between spec and implementation.** Pins happen at A-time (D2); if a major shifts under B–D, bumps are deliberate, isolated commits.

## Verification strategy

Per repo reality (no instrumented tests, SDK-less container), each sub-project proves itself in three rungs: **CI green → unit tests → maintainer on device.**

- **A:** the PR itself exercises the new workflow; `ktlintCheck testDebugUnitTest assembleDebug` green; APK artifact installs and shows the placeholder screen; launching **without** secrets crashes with the exact setup message (deliberate check); root `npm run build` in an SDK-less environment prints the skip warning and exits 0.
- **B:** CI green; localization-parity + token unit tests pass; maintainer opens the debug token-preview screen — colors light/dark, type ramp (three faces, tracking visible), spacing, Rive logo plays. Font sign-off recorded.
- **C:** CI green; ported `canSubmit` cases + consent-payload + gating tests pass; maintainer on device: signup with both checkboxes (confirm `terms_accepted_at`/`privacy_accepted_at` in user metadata via the dashboard), login, **Google OAuth full round-trip**, deep-link return also via `adb shell am start -a android.intent.action.VIEW -d "pebbles://auth-callback"`, onboarding shows once and never again across restarts, splash holds ~2.5 s, legal links open in-app, fr locale via per-app language.
- **D:** CI green; grouping/valence/palette/substitution tests pass; maintainer on device with a real account: timeline shows real pebbles grouped by ISO week, SVGs compared against iOS side-by-side (the fidelity spike's exit criterion), emotion names localized in fr, palette fallback exercised, sign-out returns to Welcome.

## Arkaik

No new nodes — Android is already a declared platform on ~96 view nodes. As each sub-project lands, update `docs/arkaik/bundle.json` status/descriptions for the views Android now implements (`V-landing`, `V-login`, `V-register`, the four `V-onboarding-*`, `V-home`/`V-timeline` read-only) and run `node .claude/skills/arkaik/scripts/validate-bundle.js`. These are user-facing views, but the Android app is unreleased during this milestone — **Lab Notes are deferred**: one milestone-level bilingual note when D lands (or when a distributable build exists), not per-PR.

## Open questions deferred (not blocking)

- Credential Manager / one-tap Google sign-in (post-v1 polish).
- Animated cairn week-roll (`pbbls-cairn-states.riv`) vs static imagery in D.
- Release signing, Play Console, versioning, crash reporting → a future "Android distribution" milestone.
- detekt adoption; instrumented/screenshot tests.
- Whether `turbo.json` should grow a `test` task now that two apps have `test` scripts (repo-wide question, not Android's).
