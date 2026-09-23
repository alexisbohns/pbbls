# Android: Material 3 Expressive theme from the M3-evo export — design

- **Issue:** #853 (M61 · Android Refacto). Lands before #854.
- **Supersedes:** M38 decision D6 ("Material 3 as engine only, no Material color roles in app code").
- **Source material:** a Material Theme Builder export, seed `#CE7E8A` (core: primary `#CE7E8A`, tertiary `#98A39A`, error `#D3911A`), six schemes (light/dark × standard/medium/high contrast). Held outside the repo; the schemes are transcribed into `ColorSchemes.kt` in Part 1 and that file becomes the record.

## Decisions (maintainer, 2026-09-24)

| # | Decision |
|---|---|
| D1 | **The export is the Android brand.** Android forgets the iOS-derived palette (`SystemPalette`/`AccentPalette`) and the iOS type rhythm. This is the third deliberate iOS divergence, after the four-tab bar (#852) and the `core/` split (#851). Whether iOS follows is a separate issue. |
| D2 | **Amber is the error colour** (`#7F560F` light, `#F3BD6E` dark). Red read as aggressive; amber catches the eye without alarming. `PebblesDestructive` and both `ErrorRed` literals go. |
| D3 | **App code speaks Material.** `MaterialTheme.colorScheme.*`, `.typography.*`, `.shapes.*` everywhere. `PebblesTheme.colors` / `.type`, `PebblesTypography` and `PebblesText` are deleted. `PebblesTheme` keeps only what M3 has no slot for: `spacing` and the handwritten faces (`hand`). |
| D4 | **Typography is the export's:** Inclusive Sans (body, label) + Ysabeau (display, headline, title) on M3's default type scale, with the Expressive emphasized variants. Fonts are **bundled** variable TTFs, not the export's GMS downloadable fonts (offline, de-Googled devices, and layoutlib screenshot tests cannot fetch). Caveat and Reenie Beanie stay as Pebbles-only hand faces. Nunito goes. Uppercase `meta`/`cardHeading` is dropped (M3 labels are sentence case). |
| D5 | **All six schemes ship.** Android 14+ follows the system contrast level live; Android 13 uses standard. |
| D6 | **Wallpaper (dynamic) colour is on by default**, with a "Use wallpaper colours" switch in Settings. |
| D7 | **Shapes are M3 Expressive's defaults**; **Spacing moves to M3's 4 dp grid** (4/8/12/16/24/32). |
| D8 | **Pin material3 `1.5.0-alpha27`** over the BOM's 1.4.0 and opt in to `ExperimentalMaterial3ExpressiveApi`. In 1.4.0 `MotionScheme.expressive()`, the `*Emphasized` type getters and `shapes.largeIncreased`/`extraLargeIncreased` are `internal`. alpha27 is the newest alpha whose Compose deps (`1.12.0-beta01`) sit under the BOM's stable 1.12.1, so material3 is the only alpha on the classpath; alpha28+ drag ui/foundation/runtime to `1.13.0-alpha01`. The bump is its own commit. Move to 1.5.0 stable when it ships. |
| D9 | **Launcher icon unchanged** (store identity, generated from the iOS mark). Splash background and icon circle in `colors.xml` move to the new `surface` / `primary`. |

Out of scope, owned by #854: replacing hand-rolled components with stock ones (`Button`, `TopAppBar`, `OutlinedTextField`, `LoadingIndicator`, …). This work changes tokens and the theme, not which components are drawn. #854's "align Spacing to 4/8/12/16/24/32" line is delivered here (Part 6).

## Architecture

### Files (`core/designsystem/`)

| File | Role |
|---|---|
| `ColorSchemes.kt` | The six `ColorScheme`s transcribed from the export, plus the one pinned value (`GoogleCapsuleInk`, see below). The only file allowed `Color(0x…)`. |
| `PebblesTypeface.kt` | `FontFamily`s: `InclusiveSans`, `Ysabeau` (variable, wght axis, with italics), `Caveat`, `ReenieBeanie`. Resources `res/font/inclusive_sans.ttf`, `inclusive_sans_italic.ttf`, `ysabeau.ttf`, `ysabeau_italic.ttf` from `google/fonts` `ofl/`. |
| `Typography.kt` | `PebblesMaterialTypography`: `Typography()` baseline with font families swapped per D4, including every `*Emphasized` style. Ysabeau styles keep `fontFeatureSettings = "pnum, lnum"`. |
| `Shapes.kt` | `PebblesShapes = Shapes()` (Expressive defaults: `extraSmall 4`, `small 8`, `medium 12`, `large 16`, `largeIncreased 20`, `extraLarge 28`, `extraLargeIncreased 32`, `extraExtraLarge 48`). Exists so the choice is named in one place. |
| `HandTypography.kt` | `PebblesHandTypography`: `bodyLeadHand`, `largeTitleHand`, `nameInputHand`, `valenceWord`, plus `inkOverhang`/`needsInkPadding`, moved verbatim from `PebblesTypography`. |
| `ContrastLevel.kt` | `rememberContrastLevel(): ContrastLevel` (`STANDARD`/`MEDIUM`/`HIGH`). API 34+: reads `UiModeManager.contrast` and re-reads through `addContrastChangeListener` (removed in `onDispose`). ≥ 2/3 → HIGH, ≥ 1/3 → MEDIUM, else STANDARD. API 33 → STANDARD. The `SDK_INT >= 34` check is above minSdk, which the "no guards below 33" rule allows. |
| `ReduceMotion.kt` | `rememberReduceMotion()`: `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`. Replaces the private copies in `WelcomeScreen.kt` and `ValenceFan.kt`. |
| `PebblesTheme.kt` | The root (below). |

`core/data/AppearancePreferences.kt`: `@Singleton class AppearancePreferences @Inject constructor(@ApplicationContext context)`. `useWallpaperColors: Boolean` backed by `mutableStateOf`, persisted in the existing `pebbles_prefs` SharedPreferences file, key `useWallpaperColors`, default `true`. Setter writes through with `apply()`.

### Root

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebblesTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val contrast = rememberContrastLevel()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> pebblesColorScheme(dark, contrast)
    }
    CompositionLocalProvider(LocalSpacing provides Spacing, LocalHandTypography provides PebblesHandTypography) {
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

`dynamicColor` defaults to `false` so every preview and screenshot test renders the brand scheme without having to say so. `MainActivity` is the one caller that passes `appearancePreferences.useWallpaperColors`. Dynamic schemes already incorporate the system contrast level, so `contrast` only picks between the six static schemes. minSdk 33 means dynamic colour is always available; there is no fallback branch.

### The bridge (Parts 1–6 only)

Part 1 flips the whole app's look in one step without touching call sites: `PebblesTheme.colors` keeps its shape (`system.*`, `accent.*`), marked `@Deprecated`, but every value reads the active scheme:

| Old | Scheme role |
|---|---|
| `system.foreground` | `onSurface` |
| `system.secondary` | `onSurfaceVariant` |
| `system.muted` | `outlineVariant` |
| `system.background` | `surface` |
| `system.onLight` | `GoogleCapsuleInk` (pinned) |
| `accent.primary` | `primary` |
| `accent.light` | `onPrimary` |
| `accent.secondary` | `primaryContainer` |
| `accent.surface` | `primary.copy(alpha = 0.10f)` |
| `accent.shaded`, `accent.dark` | `onPrimaryContainer` |
| `accent.primaryHex` | `primary` formatted as 6-digit `#RRGGBB` |
| `PebblesDestructive` | `error` (becomes a `@Composable` getter) |
| `PebblesSuccess` | `tertiary` (same) |

`PebblesTheme.type` keeps its 18 tokens, rebuilt on Inclusive Sans / Ysabeau at their current sizes, so Part 1 changes faces but not sizes. `primaryHex` following `primary` means pebble SVG accents follow wallpaper colour too, which is intended.

`GoogleCapsuleInk` (`#4A3639`, today's `onLight`) stays pinned: the Google sign-in capsule is a fixed light surface under Google's branding rules, and its ink must not follow the theme. It lives in `ColorSchemes.kt` so the literal rule holds.

## Migration rules (Parts 3–5)

### Colours: role by use, not by name

| Old | Used as | → |
|---|---|---|
| `system.foreground` | text, icon | `onSurface` |
| `system.secondary` | text, icon | `onSurfaceVariant` |
| `system.secondary` | selected fill (auth switcher) | `secondaryContainer` + `onSecondaryContainer` |
| `system.muted` | border, divider | `outlineVariant` |
| `system.muted` | fill, track | `surfaceContainerHighest` |
| `system.muted` | disabled text/icon | `onSurface.copy(alpha = 0.38f)` |
| `system.background` | page ground | `surface` |
| `system.background` | sheet, menu, card | `surfaceContainerLow` / `surfaceContainer` / `surfaceContainerHigh` by elevation |
| `accent.primary` | fill carrying a label | `primary` + `onPrimary` |
| `accent.primary` | text, link, icon | `primary` |
| `accent.surface`, `accent.secondary` | tinted fill | `primaryContainer` + `onPrimaryContainer` |
| `ErrorRed`, `PebblesDestructive` | | `error` / `onError`; banners `errorContainer` / `onErrorContainer` |
| `PebblesSuccess` | | `tertiary` |
| `onLight` | | `GoogleCapsuleInk` |

The two `tonalElevation = 3.dp` surfaces (`KarmaEarnedCapsule`, `AchievementMomentOverlay`) take an explicit `color = surfaceContainerHigh` and `tonalElevation = 0.dp`: lift comes from the ladder, not a `surfaceTint` wash.

Literal colours outside `ColorSchemes.kt`: `ValenceStoneStyle.joySurface` → `tertiaryContainer`; `RiveLogo` placeholder → `surfaceContainerHighest` fill + `outlineVariant` border. `EmotionPalette.kt:106` is hex-parsing bit arithmetic on data, not a design literal, and stays.

### Type

| Old token | → M3 role |
|---|---|
| `body` | `bodyLarge` |
| `bodyEmphasized` | `bodyLargeEmphasized` |
| `subhead` | `bodyMedium` |
| `subheadEmphasized` | `bodyMediumEmphasized` |
| `callout` / `calloutEmphasized` | `bodyLarge` / `bodyLargeEmphasized` |
| `headline` | `titleMedium` |
| `headlineEmphasized` | `titleMediumEmphasized` |
| `counterLg` | `titleMedium` |
| `title` | `headlineMedium` |
| `buttonLabel` | `labelLarge` |
| `captionEmphasized` | `labelMediumEmphasized` |
| `meta` / `metaEmphasized` | `labelSmall` / `labelSmallEmphasized` |
| `cardHeading` / `cardHeadingEmphasized` | `titleSmall` / `titleSmallEmphasized` |
| `bodyLeadHand`, `largeTitleHand`, `nameInputHand`, `valenceWord` | `PebblesTheme.hand.*` |

The 13 `.copy(fontSize = …)` overrides become roles: 12 sp → `labelMedium`, 13 sp → `bodySmall`, 20/22 sp → `titleLarge`, 24 sp → `headlineSmall`. No size override survives. Every `PebblesText(…)` becomes `Text(…)`.

### Corners

| Literal | → |
|---|---|
| 3 dp, 6 dp | `shapes.extraSmall` |
| 8 dp, 10 dp, `spacing.sm` | `shapes.small` |
| 12 dp | `shapes.medium` |
| 16 dp, 17 dp, `spacing.lg` | `shapes.large` |
| 20 dp, 22 dp | `shapes.largeIncreased` |
| 24 dp, `spacing.xxl` | `shapes.extraLarge` |
| `RoundedCornerShape(50)` | `CircleShape` |
| 0 dp | `RectangleShape` |

Top-only / bottom-only corners take the role shape and `.copy()` the opposite corners to `ZeroCornerSize`. `PHOTO_CORNER` and computed `radius` sites are mapped individually and noted in the PR.

## Wallpaper toggle (Part 2)

Settings gains an **Appearance** section with one stock `Switch` row, "Use wallpaper colours", and a supporting line ("Match Pebbles to your wallpaper"). Strings in `values/` and `values-fr/`. `SettingsViewModel` receives `AppearancePreferences` and exposes the value in its `Content` state plus an `onUseWallpaperColorsChange` action. The theme recomposes from the same state object, so the switch takes effect immediately.

This is the first real setting, which is the trigger D5 named for DataStore. One boolean does not pay for the migration; the decision log records that the trigger has fired and leaves the call to #858 or the next setting.

## Motion

`MotionScheme.expressive()` drives stock component springs. `rememberReduceMotion()` gates the custom animations: the record flow's step `AnimatedContent` uses `snap()` in place of its slide, and the karma capsule and achievement overlay enter/exit without animation. Welcome and ValenceFan keep their current reduce-motion behaviour, now through the shared helper.

## Spacing (Part 6)

`Spacing` becomes `xs 4 / sm 8 / md 12 / lg 16 / xl 24 / xxl 32`. Names are unchanged, so this is a values-only diff in one file plus a full re-baseline, kept separate so its pixel movement is not mixed with role changes. Its KDoc stops claiming `lg == body font size`.

## Lockdown (Part 7)

- Delete the bridge: `Palettes.kt`, `PebblesTheme.colors`, `PebblesTheme.type`, `PebblesTypography`, `PebblesText`. (`nunito.ttf` and `ysabeau_semibold.ttf` go in Part 1, the moment nothing references them: `lint` fails on `UnusedResources`.) `DebugTokenPreviewScreen` is rewritten to show scheme roles.
- `ThemeLiteralsTest` (Konsist, JVM) fails on any of these in `features/**` and `core/ui/**`: `Color(0x`, `RoundedCornerShape(` with a numeric literal argument, `.copy(fontSize`.
- Android `CLAUDE.md`: rewrite the "Theme" section, and the opening "two deliberate divergences" becomes three. The full guide rewrite stays with #858.

## Testing

- `ColorSchemeContrastTest` (JVM, no Android): for all six schemes, every on/container pair (`onPrimary`/`primary` … `onErrorContainer`/`errorContainer`, `inverseOnSurface`/`inverseSurface`) ≥ 4.5:1; `onSurface`, `onSurfaceVariant`, `primary`, `error` against `surface` and each `surfaceContainer*` step ≥ 4.5:1; `outline`/`surface` ≥ 3:1. Checked against the export before writing this spec: every pair passes. The WCAG luminance math moves out of `GoogleSignInButtonContrastTest` into a shared test helper both use. Dynamic schemes are generated by the OS and are not tested.
- `AppearancePreferencesTest`: default `true`; round-trips a write.
- `@PreviewHighContrast` is not possible (contrast is not a preview parameter), so high contrast is covered by a gallery preview that calls `pebblesColorScheme(dark, HIGH)` directly under `MaterialExpressiveTheme`.
- Screenshot references re-baseline per part through the `rebaseline-screenshots` label, never locally.
- Every part: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` green before push. Part 1 also gets an emulator smoke pass (light, dark, contrast raised, wallpaper colour on and off) since screenshots cannot see dynamic colour.

## Delivery

A `gh stack` of seven parts, each passing lint and tests on its own:

| Part | Branch | Content | Lab Note |
|---|---|---|---|
| 1 | `feat/853-m3-theme-foundation` | Spec, schemes, fonts, typography, shapes, Expressive root, contrast, `AppearancePreferences` (no UI), reduce-motion helper, bridge, splash colours, contrast test, decision-log entry | yes |
| 2 | `feat/853-wallpaper-colours-setting` | Settings toggle | yes |
| 3 | `feat/853-core-material-roles` | `core/designsystem` + `core/ui` onto M3 roles/type/shapes; `tonalElevation` fix | `no-lab-note` |
| 4 | `feat/853-features-material-roles` | welcome, auth, onboarding, profile, connections, lab, glyph | `no-lab-note` |
| 5 | `feat/853-path-material-roles` | `features/path` | `no-lab-note` |
| 6 | `feat/853-spacing-grid` | 4 dp grid | `no-lab-note` |
| 7 | `feat/853-theme-lockdown` | Bridge deletion, Konsist test, CLAUDE.md | `no-lab-note` |

Labels `feat`, `ui`, `android`, milestone M61. Only Part 7 says `Closes #853`; the others say `Part of #853`.

Arkaik: at the start of Part 1, move the affected acceptances/views to `development` over MCP and list them in each Lab Note's `nodes:`.

## Follow-ups

- iOS issue: decide whether iOS adopts the M3-evo palette and amber error, or keeps the current palette and records the divergence (#921).
- #858: supersede D5 (DataStore) now that the trigger has fired.
