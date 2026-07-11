# Android design system — implementation plan (M37 sub-project B)

> Issue: **#529** ([Feat] Android design system: theme tokens, fonts, core components, Rive assets) · Blocked by #528 (scaffold, done) · Blocks C, D.
> Umbrella design: `docs/superpowers/specs/2026-07-10-android-bootstrap-design.md` (decisions D6, D7, D9, D14).
> Status: **ready to implement.** Font for the rounded slot is **settled: Nunito** (maintainer-approved 2026-07-11).

This plan already contains the exact values pulled from the iOS reference (`apps/ios/Pebbles/Theme/` + `Components/`), so it can be executed without re-exploring. Where a value isn't inlined (French translation strings, the actual font-file bytes), the iOS source path to read is named.

## Context

The Android scaffold (A, PR #533) is delivered and device-verified. B ports the iOS `Theme` + `Components` layer so every later screen (C auth funnel, D timeline) composes from tokens, never literals. Material 3 is the engine only — no dynamic color, no Material color roles in app code (D6).

**Verification synergy:** B ships a debug token-preview screen; because A wired Compose Preview Screenshot Testing, that screen renders into the `ui-screenshots` CI artifact — the maintainer reviews the whole design system as images (and confirms Nunito on a real screenshot) without a device.

All new code under `app/src/main/kotlin/app/pbbls/android/`: `theme/`, `components/`, `rive/`, plus a localization helper — a 1:1 map of `Pebbles/{Theme,Components}`.

## 1. Theme tokens (D6) — `theme/`

- **`Palettes.kt`** — `SystemPalette(foreground, secondary, muted, background)` + `AccentPalette(dark, shaded, primary, secondary, light, surface)`, each with a `light`/`dark` instance. Exact values from the iOS asset catalog (`apps/ios/Pebbles/Resources/Assets.xcassets/`, structs in `apps/ios/Pebbles/Theme/Palettes.swift`):
  - System **light / dark**: foreground `#4A3639`/`#E9E2E4`, secondary `#7A5E64`/`#AF979D`, muted `#E9E2E4`/`#2E2024`, background `#FFFFFF`/`#171012`.
  - Accent (**no dark variant — identical in both themes**): dark `#341B1B`, shaded `#8C4949`, primary `#C07A7A`, secondary `#EAD3D3`, light `#FAF4F4`, surface `#C07A7A` @10% → `Color(0x1AC07A7A)`.
  - `AccentPalette.primaryHex = "#C07A7A"` (a `String`, for SVG `currentColor` injection in D).
  - Note: iOS `AccentColor.colorset` is Xcode's tint slot, *not* part of `AccentPalette` — ignore it.
- **`Spacing.kt`** — object: `xs 3, sm 10, md 13, lg 17, xl 22, xxl 34` (`.dp`). (Mirror `apps/ios/Pebbles/Theme/Spacing.swift`.)
- **`PebblesTheme.kt`** — `staticCompositionLocalOf` for `LocalSystemPalette`, `LocalAccentPalette`, `LocalSpacing`, `LocalPebblesTypography`; resolve light/dark from `isSystemInDarkTheme()`; wrap `MaterialTheme` as engine only. Accessor object `PebblesTheme.colors.system.*` / `.colors.accent.*` / `.spacing.*` / `.type.*`.

## 2. Typography (D7) — `theme/Typography.kt` + `res/font/`

- **Fonts:** copy `apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf` → `res/font/ysabeau_semibold.ttf` and `apps/ios/Pebbles/Resources/ReenieBeanie-Regular.ttf` → `res/font/reenie_beanie.ttf`. Add **Nunito** weighted TTFs → `res/font/` as the rounded face (all SF Pro/Compact Rounded tokens; SF Compact Rounded isn't bundled on iOS either, so everything rounded → Nunito). *Nunito sourcing — see Risks.*
- **`PebblesTypography`** object — 18 tokens as `TextStyle`s (fontFamily, fontSize.sp, fontWeight, letterSpacing.em). Source: `apps/ios/Pebbles/Theme/Font+Pebbles.swift`. Full ramp (Android face · size · weight · tracking · case):
  - **Nunito:** body 17·400·2%, bodyEmphasized 17·600·2%, subhead 15·400·2%, subheadEmphasized 15·600·2%, headline 17·600·2%, headlineEmphasized 17·700·2%, callout 16·500·2%, calloutEmphasized 16·600·2%, meta 12·500·10%·**UPPER**, metaEmphasized 12·700·10%·**UPPER**, cardHeading 15·600·10%·**UPPER**, cardHeadingEmphasized 15·700·10%·**UPPER**, counterLg 17·600·2%, captionEmphasized 12·600·2%.
  - **Ysabeau SemiBold:** title 28·(−2%), buttonLabel 17·2%. Set `fontFeatureSettings = "pnum, lnum"`.
  - **Reenie Beanie:** bodyLeadHand 22·(−4.5%), largeTitleHand 41·(−4.9%). Used for user/soul names.
  - Compose `TextStyle` has no text-case → uppercase tokens uppercase the string at the call site (a small `PebblesText`/helper).

## 3. Core components (D6) — `components/` (source: `apps/ios/Pebbles/Components/`)

- **`PebblesTextInput`** — `BasicTextField` + `decorationBox`: 52dp minHeight, white fill, `RoundedCornerShape(12.dp)`, 1dp `system.muted` border, overlaid placeholder in `system.secondary`, cursor `accent.primary`, `isSecure`→`PasswordVisualTransformation`, keyboard/autofill/capitalization params. Static border (no focus state) to match iOS 1:1.
- **`PebblesCheckbox`** — `Row(spacing 12.dp)`: 44dp box (`RoundedCornerShape(12.dp)`) — unchecked white fill + `system.muted` stroke + "square" glyph (`system.secondary`); checked `accent.primary` fill+stroke + check glyph (`system.background`). Label = `AnnotatedString(prefix + underlined linkText in accent.primary)`; whole-label tap → `onLinkTap`, box tap → toggle; merged a11y (button + selected).
- **`PebblesPrimaryButton(text, onClick, enabled, isLoading)`** — full-width, 52dp, `RoundedCornerShape(50)` pill: enabled = `accent.primary` fill + white label; disabled = clear fill + 1dp `system.muted` outline + muted label; pressed = 0.85 alpha; `isLoading` = spinner replacing the label. (Google/Apple sign-in buttons are C, not B.)
- Note: the iOS components use *system* text styles (`.body`, `.subheadline`, `.title3`), not `PebblesFont` tokens — map to the nearest Pebbles typography token on Android.

## 4. Rive (D14) — `rive/RiveLogo.kt`

- Catalog: `app.rive:rive-android` (~8.7.x, Maven Central) + `androidx.startup:startup-runtime`.
- Copy `packages/rive/pbbls-logo-appear_idle.riv` → `res/raw/pbbls_logo_appear_idle.riv` (only the logo ships in B; cairn is D-optional). Document the rename map in `apps/android/CLAUDE.md`.
- `Rive.init(context)` in `PebblesApp.onCreate()`.
- **`RiveLogo`** — `AndroidView` wrapping `RiveAnimationView` loading the logo with **default artboard + default timeline, autoplay** (the file has no named state machine; iOS `WelcomeView.swift` loads it with no artboard/SM args). Consumed by Welcome in C; B proves it plays via the token-preview + screenshot.

## 5. Debug token-preview (mirror `apps/ios/Pebbles/Theme/ColorTokensPreview.swift`)

Debug composable: swatch grid — **System** (4) + **Accent** (6) sections, adaptive columns, each swatch = 56dp `RoundedCornerShape(8.dp)` + 1px border + monospaced caption. Extend beyond iOS with a **type-ramp** section (each token rendered in its style) and the **RiveLogo**, so one screenshot shows colors + type + logo. For B, `MainActivity` shows this preview (wrapped in `PebblesTheme`) as the temporary home — the real home lands in C. Add `@PreviewTest` light + dark previews in `src/screenshotTest/` so it renders into `ui-screenshots`.

## 6. Localization plumbing (D9)

- `res/values/strings.xml` (en) + `res/values-fr/strings.xml`: brand strings `translatable="false"`, plus reference-data names for every slug — `emotion_<slug>_name` (38), `domain_<slug>_name` (18), `emotionCategory_<slug>_name` (7). Port en+fr values from `apps/ios/Pebbles/Resources/Localizable.xcstrings`; slugs enumerated in `apps/ios/Pebbles/Features/Path/Models/ReferenceSlugs.swift`.
  - Emotions (38): amazed, amused, angry, annoyed, anxious, ashamed, brave, calm, confident, content, disappointed, discouraged, disgusted, drained, embarrassed, excited, frustrated, grateful, guilty, happy, hopeful, hopeless, indifferent, irritated, jealous, joyful, lonely, overwhelmed, passionate, peaceful, proud, relieved, sad, satisfied, scared, stressed, surprised, worried.
  - Categories (7): anger, fear, joy, peace, pride, sadness, shame.
  - Domains (18): community, currentevents, dating, education, family, fitness, friends, health, hobbies, identity, money, partner, selfcare, spirituality, tasks, travel, weather, work.
- `res/xml/locales_config.xml` + `android:localeConfig` on `<application>` (per-app language, API 33).
- **`ReferenceStrings.kt`** — explicit `slug → @StringRes` maps (emotion/domain/category) + resolver `referenceName(type, slug, fallbackDbName)` returning the localized string, falling back to the DB name — mirrors iOS `localizedName` (`apps/ios/Pebbles/Features/Path/Models/Emotion+Localized.swift`, `Domain+Localized.swift`). Never render `.name` directly (rule already in `apps/android/CLAUDE.md`).
- **Unit tests** (mirror `apps/ios/PebblesTests/LocalizationTests.swift`): en/fr key-set parity for the reference keys; every `ReferenceSlugs` entry maps to a real string in both locales; fallback returns the DB name for an unknown slug.

## Build changes
- `apps/android/gradle/libs.versions.toml`: `rive`, `androidx-startup` versions + libraries (Nunito is a bundled resource, no dependency).
- `apps/android/app/build.gradle.kts`: rive + startup deps.
- `PebblesApp.kt`: `Rive.init(this)`.
- `MainActivity.kt`: swap `PlaceholderScreen` → the themed token-preview.
- `apps/android/CLAUDE.md`: fill in the theme/folder-layout sections now that they exist; document the Rive rename map.

## Risks / open items
- **Nunito sourcing (highest).** The SDK-less container may not fetch external font files (the proxy blocked GitHub for the ktlint CLI in A). Attempt to fetch the OFL Nunito TTFs; if blocked, the maintainer drops them into `res/font/` (OFL, free to bundle). Surface this the moment it's confirmed blocked rather than stall.
- **rive-android on AGP 9.2 / Compose** — pulls native libs (bigger APK, first-build download); validate via CI.
- **No local build** — validate via CI + the `ui-screenshots` artifact (token preview shows colors, type ramp, logo). ktlint validates locally via a throwaway `kotlin("jvm")` + `org.jlleitschuh.gradle.ktlint` project (as in A) — the CI plugin bundles ktlint 1.5.0; add the `.editorconfig` Compose exemption (already present).
- **Size** — B is large but is one PR per the design; if it balloons, the localization reference-string port is the natural slice to fast-follow.

## Verification
- CI green: `ktlintCheck testDebugUnitTest assembleDebug` + new localization-parity/typography/palette unit tests + `updateDebugScreenshotTest` render.
- `ui-screenshots` artifact shows the token preview: 10 color swatches in light + dark, the type ramp across the three faces, and a Rive logo frame.
- Maintainer on device (debug APK): open the token preview, confirm colors/type/spacing, watch the logo animate, **confirm Nunito**; toggle system dark mode → palette flips; switch to French via per-app language.

## Delivery
Merge #533 → branch B off `main` → address issue #529. Branch name suggestion: `claude/issue-529-android-design-system-<suffix>` (or `feat/529-android-design-system`). Standard flow from there: implement, ktlint locally, push, open PR (`Resolves #529`, inherit `feat`/`ui`/`android` labels + M38 milestone), iterate CI to green, hand device checks to the maintainer.
