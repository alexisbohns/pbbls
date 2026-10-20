# iOS adopts the brand Material 3 colour scheme (Stack A)

**Date:** 2026-10-20
**Issue:** [#921](https://github.com/alexisbohns/pbbls/issues/921). The maintainer decided **converge**.
**Surface:** `apps/ios` (app and widget), plus one generator and its test in `apps/web` and one CI workflow. No schema, no RPC, no wire format.
**References:** `2026-09-24-android-m3-expressive-theme-design.md` (#853, migration rules), `2026-10-18-android-spine-brand-scheme-design.md` (#1118, §The rule set), `2026-05-17-ios-colors-system-refacto-design.md` (#456, today's palette). Decision-log entries 2026-09-24 (#853), 2026-09-28 (#990, #986) and 2026-10-18 (#1118).
**Depended on by:** Stack C (`2026-10-20-ios-tab-navigation-design.md`), which depends on A1 only (the roles exist), and stack D, the iOS Spine (#1130), which names `Color.pebbles.*`. Stack B does not depend on A, and the two land in either order.

## Goal

iOS draws every interface colour from the same Material 3 scheme as Android and the web. That scheme is the Spine-paper brand of #1118 in light, dark and their high-contrast variants. The colours are **generated** from Android's `ColorSchemes.kt` into asset-catalog colorsets. Feature code reaches them through one Swift entry point, `Color.pebbles.<role>`. Error is the brand orange (key `#EA6B00`). Red appears in exactly one place: the destructive button of a system `.alert` / `.confirmationDialog`, the recorded platform exception. After the stack, `SystemPalette`, `AccentPalette` and their twelve colorsets are gone. A test fails the build on any colour literal in feature code.

## Non-goals

- **Per-emotion palettes.** `EmotionPalette`, from `v_emotions_with_palette`, is server data and stays exactly as it is, along with `PebblePageColors`, `PebbleFrameColors`, `PetroglyphColors` and the stone tones.
- **The iOS Spine** (#1130) and its alpha hairlines beyond `rule`. This stack provides the roles. The Spine builds on them.
- **Typography, shapes and spacing.** Android moved those to M3 in #853. iOS does not here.
- **Medium contrast.** iOS has no medium level. Android's medium schemes are not emitted.
- **Wallpaper colour.** iOS has no equivalent, so there is nothing to pin. The error roles are simply never system red.
- **The launcher icon and `.riv` artwork.** Store identity and baked animation colours (D6).
- **Swift 6 / strict concurrency.**

## 1. Today

- `Pebbles/Theme/Palettes.swift` exposes `Color.system` (`foreground`, `secondary`, `muted`, `background`, `onLight`) and `Color.accent` (`dark`, `shaded`, `primary`, `secondary`, `light`, `surface`, `primaryHex`). They are backed by 12 colorsets plus `AccentColor` (`#C07A7A`, one appearance). None has a high-contrast variant, and the `Accent*` sets have no dark variant.
- The code has 366 call sites in 94 files: `system.secondary` 114, `system.muted` 52, `system.background` 47, `system.foreground` 42, `system.onLight` 2, `accent.primary` 81, `accent.surface` 13, `accent.secondary` 5, `accent.light` 4, `accent.shaded` 4, `accent.dark` 2. `accent.primaryHex` has no consumer left.
- UIKit reads colours by name in `PebblesApp.swift:61,62,85` (segmented control, nav titles) and `Onboarding/OnboardingView.swift:24-25` (page control).
- The widget has its own `PebblesWidget/Assets.xcassets/AccentPrimary.colorset`, which `KarmaActivityWidget.swift` reads four times.
- The code has 21 `.foregroundStyle(.red)` sites and 22 `role: .destructive` sites (§6).
- `Scripts/verify-color-contrast.mjs`, run by the `ios.yml` `verify` job, pins `SystemOnLight` as single-appearance and AA on white.

## 2. The generator (A1)

### D1 — Input is `ColorSchemes.kt`, parsed by the web's `parseColorSchemes`

`apps/web/scripts/generate-ios-colors.ts` sits beside `generate-m3-theme.ts` and runs as `npm run generate:ios-colors --workspace=@pbbls/web`. For convenience `apps/ios/package.json` gains `"generate:colors": "npm run generate:ios-colors --workspace=@pbbls/web"`. The script is a thin shell like its sibling: it reads `ANDROID_SCHEMES_FROM_WEB`, calls `parseColorSchemes`, then writes. The logic lives in a new pure module, `apps/web/lib/theme/ios-colorsets.ts`, string-in / files-out:

- `renderIosColorsets(schemes, pins): Map<relativePath, string>` returns every file the generator owns, keyed by a path relative to `apps/ios`.
- `parsePinnedColors(kotlin)` reads the single hand-pinned value, `internal val GoogleCapsuleInk = Color(0xFF4A3639)`. It throws if the value is missing or not opaque. It sits in `ios-colorsets.ts`, not `m3-schemes.ts`, so the web module is not touched.
- `parseColorSchemes` is reused unchanged. Its guards still apply: all six schemes present, light/dark built correctly, opaque, identical role sets. The renderer uses four of the six schemes: `LightScheme`, `DarkScheme`, `LightHighContrastScheme`, `DarkHighContrastScheme`. Role names go back to camelCase. `parseColorSchemes` kebab-cases them, and the renderer inverts that with `fromKebab`, so `surface-container-low` becomes `surfaceContainerLow` and `on-sand-container` becomes `onSandContainer`.

The chain becomes: export JSON → `generate-color-schemes.py` → `ColorSchemes.kt` → { `m3-theme.css`, iOS colorsets + Swift }.

Considered and rejected:
- **The export JSON as input.** It would give iOS a second source of truth parallel to the Kotlin, and would re-implement the Sand family mapping the web parser already proves.
- **A Swift or Python generator under `apps/ios/Scripts`.** It would duplicate `parseColorSchemes` and its guards, which the web Vitest already exercises.
- **Hand-written colorsets.** Excluded by "colours are generated, never hand-written".

### D2 — Four appearances: standard and High Contrast, from Android's *high* schemes

Each colorset has exactly four entries. The keys below were checked by compiling a test catalog with `actool` (Xcode 26.3) and reading the `.car` with `assetutil --info`. It reports `UIAppearanceAny`, `UIAppearanceDark`, `UIAppearanceHighContrastAny` and `UIAppearanceHighContrastDark`.

| Entry | `appearances` | Android source |
|---|---|---|
| 1 | *(none: "Any", serves light)* | `LightScheme` |
| 2 | `[{"appearance":"luminosity","value":"dark"}]` | `DarkScheme` |
| 3 | `[{"appearance":"contrast","value":"high"}]` | `LightHighContrastScheme` |
| 4 | `[{"appearance":"luminosity","value":"dark"},{"appearance":"contrast","value":"high"}]` | `DarkHighContrastScheme` |

Each entry is `"idiom" : "universal"`, `"color-space" : "srgb"`, with components as `"0xRR"` hex strings and `"alpha" : "1.000"`. Alpha differs only for the alpha-blended Spine roles (§2.4). The file is serialised the way Xcode writes it, so that opening the catalog in Xcode does not churn it: `JSON.stringify(obj, null, 2)`, then every `"key": ` rewritten to `"key" : `, then a trailing `\n`, with `"info" : { "author" : "xcode", "version" : 1 }`. iOS sets the trait to `.high` under Settings → Accessibility → Display & Text Size → Increase Contrast.

Considered and rejected:
- **Medium schemes as the High Contrast variant.** Medium is the half-step. The user who turns on Increase Contrast asked for the strong one.
- **All three levels switched in code.** iOS has no third level to follow, and switching in code would put colour selection outside the asset catalog.

### 2.3 Output layout

Every path below is relative to `apps/ios` and owned by the generator. Each file opens with a `GENERATED … do not edit` header (Swift) or is recognisable by its folder (JSON).

```
Pebbles/Resources/Assets.xcassets/
  AccentColor.colorset/Contents.json         ← primary, four appearances (the reserved global tint)
  M3/Contents.json                           ← {"properties":{"provides-namespace":true}}
  M3/<role>.colorset/Contents.json           ← 48 scheme roles + sand ×4 + 9 Spine roles + googleCapsuleInk
PebblesWidget/Assets.xcassets/
  M3/…                                       ← the same M3 folder, byte for byte
Shared/Theme/PebblesColors.swift             ← the entry point (§3), compiled into both targets
```

- The namespace makes asset names `M3/surface`. The `M3/` prefix keeps them apart from the reserved `AccentColor` and from the legacy names while A3 is in flight.
- The widget gets the full set. It is generated, so the duplicate costs nothing to maintain. The widget's `AccentPrimary.colorset` is deleted in A1, and `KarmaActivityWidget` moves to `Color.pebbles.primary`.
- `project.yml` gains `ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS: NO` on both targets. Without it, Xcode generates `Color` statics named after the colorsets, `primary` and `secondary` included. Those collide with SwiftUI's own members and would be a second, ungoverned entry point. A1 confirms that nothing uses generated symbols today: there are no `Color(.…)` resource lookups.

Considered and rejected: **one catalog under `Shared/` compiled into both targets.** It would be a third catalog layout to explain. The app catalog must hold `AccentColor` and `AppIcon` anyway, and one catalog per target is what `project.yml` assumes today.

### 2.4 Derived roles: the nine scheme-only Spine tokens

Stack D names nine Spine tokens as Stack A outputs. They come from #1118's "Spine token → M3 role" table. Each one is either a pure alias of a scheme role or a constant blend of one, so the generator derives all nine from the parsed schemes and emits them as colorsets with four appearances. This mirrors Android's `SpineColors.from(scheme, night)`. "Paper" covers the light and light-high-contrast entries, "Night" the dark and dark-high-contrast ones, each read from its own scheme. Ripple's colour is the Spine's ripple ink. Its geometry is stack D's.

| Spine token → role | Paper (light, light HC) | Night (dark, dark HC) | Kind |
|---|---|---|---|
| `halo` (the clearing, day-label ground) | `surfaceContainerLow` | `surfaceContainerLow` | pure alias of `surfaceContainerLow` |
| `stock` (card) | `surfaceContainerLowest` | `surfaceContainerLow` | alias, differing by appearance |
| `rule` | `onSurface` @ 0.14 | `onSurface` @ 0.12 | alpha blend |
| `cardRule` | `onSurface` @ 0.22 | `onSurface` @ 0.18 | alpha blend |
| `line` (the spine) | `onSurface` @ 0.34 | `onSurface` @ 0.30 | alpha blend |
| `seam` | `onSurface` @ 0.30 | `onSurface` @ 0.24 | alpha blend |
| `teeth` | `onSurface` @ 0.20 | `onSurface` @ 0.14 | alpha blend |
| `ring` | `onSurfaceVariant` @ 0.30 | `onSurface` @ 0.17 | alpha blend, role differs by appearance |
| `ripple` | `onSurface` @ 0.55 | `onSurface` @ 0.50 | alpha blend |

The alphas are constants in `ios-colorsets.ts`, and a comment cites the rule-set table. The aliases are emitted as colorsets rather than Swift aliases, so that every Spine token is one asset with its own four appearances. `halo` duplicates `surfaceContainerLow` on purpose: a later change to the rule set then touches one row. A3 uses only `stock` and `rule`; the other seven are there for stack D. The stone shadow (`onSurface` @ 0.30 Paper, black @ 0.62 Night) is not in this list, because stack D owns the stone render.

**Who owns which Spine colour.** The split follows what the colour is made of:
- **A constant blend of scheme roles is generated**, in `ios-colorsets.ts`, as a colorset with all four appearances. That covers the nine tokens above. If the Spine spec needs another scheme-only token later, it is a new row in the same table, and `Color.pebbles` exposes it.
- **A blend with the per-emotion `EmotionPalette` is computed in Swift**, in stack D's `SpineColors.swift`, because the palette is runtime data from `v_emotions_with_palette`. Examples are a card edge from `palette.dark` or a seal ink from `palette.shaded`. It is never a colorset and never part of the generator.

Emotion-palette fields are always written qualified (`palette.dark`, `palette.shaded`, `palette.primary`), never bare, so they cannot be confused with `Color.pebbles.primary`.

### 2.5 The pinned ink

`googleCapsuleInk` is single-appearance, with no `appearances` block, from `GoogleCapsuleInk` (`#4A3639`, 11.2:1 on white). It replaces `SystemOnLight`, and `verify-color-contrast.mjs` repoints to `M3/googleCapsuleInk.colorset` in A3a.

## 3. The Swift entry point

### D3 — `Color.pebbles.<role>`, with one UIKit bridge

`Shared/Theme/PebblesColors.swift` (generated):

```swift
// GENERATED by `npm run generate:ios-colors --workspace=@pbbls/web` from
// apps/android/.../core/designsystem/ColorSchemes.kt. Do not edit.
import SwiftUI
import UIKit

enum PebblesColorRole: String, CaseIterable, Sendable {
    case primary, onPrimary, primaryContainer, onPrimaryContainer
    // … every scheme role in Kotlin source order, then sand, onSand,
    // sandContainer, onSandContainer, halo, stock, rule, cardRule,
    // line, seam, teeth, ring, ripple, googleCapsuleInk
    var assetName: String { "M3/\(rawValue)" }
}

struct PebblesColors: Sendable {
    var primary: Color { Color(PebblesColorRole.primary.assetName) }
    // … one property per case
}

extension Color {
    /// The only way feature code names a colour. Roles, never hexes.
    static let pebbles = PebblesColors()
}

extension UIColor {
    /// UIKit appearance proxies and Rive. Same roles, same assets.
    static func pebbles(_ role: PebblesColorRole) -> UIColor {
        guard let color = UIColor(named: role.assetName) else {
            preconditionFailure("missing colorset \(role.assetName)")
        }
        return color
    }
}
```

`Color(name)` with no bundle resolves against `Bundle.main`, which is the app in the app and the extension in the widget. That is why both catalogs carry `M3/`. The name `pebbles` does not clash with anything: `Color.pebbles*` statics were removed in #456, and `View.pebblesScreen()` / `.pebblesFont` live on `View`.

Considered and rejected:
- **Flat statics (`Color.surface`, `Color.onSurface`).** `Color.primary` and `Color.secondary` already exist in SwiftUI with other meanings.
- **New members on `Color.system` / `Color.accent`.** They would keep the names this stack retires.
- **A `ShapeStyle where Self == Color` shortcut (`.foregroundStyle(.pebbles.surface)`).** That is a second spelling. Implicit member chains through a non-`Color` intermediate are also not something SE-0299 guarantees.

## 4. Role mapping (A3)

### D4 — The Spine neutrals apply app-wide, and the mapping goes by use through the Spine table

The Spine paper is the brand's neutral set (#1118 D1). There is one scheme and no Path-only override. Each old token maps by **what it is used as**, through the rule-set vocabulary (ground, stock, ink, ink-2, ink-3, rule), onto a role. Where the rule set has no word, the Android #853 migration table applies.

| Old token | Used as | Spine token | → `Color.pebbles.` |
|---|---|---|---|
| `system.background` | page ground, `pebblesScreen` background, `toolbarBackground` | ground | `surface` |
| `system.background` | card, tile, list row, sheet block, text-field ground | stock | `stock` |
| `system.background` | ink on a dark fill (e.g. lowlight selected stone, ripple digit) | — | the fill's on-role (`inverseOnSurface` on `inverseSurface`, …) |
| `system.foreground` | text, icon, stroke ink | ink | `onSurface` |
| `system.secondary` | text, icon, the `pebblesScreen` default foreground | ink-2 | `onSurfaceVariant` |
| `system.secondary` | selected fill (segmented control) | — | `secondaryContainer` + `onSecondaryContainer` |
| `system.muted` | de-emphasised text or icon | ink-3 | `outline` |
| `system.muted` | disabled text or icon | — | `onSurface.opacity(0.38)` |
| `system.muted` | divider, separator, card outline, `listRowSeparatorTint` | rule | `rule` |
| `system.muted` | boundary of a control (checkbox, text input, Google capsule) | — | `outline` (≥ 3:1, WCAG 1.4.11) |
| `system.muted` | fill, track, chip ground, placeholder | — | `surfaceContainerHighest` |
| `system.onLight` | ink on the pinned white Google capsule | — | `googleCapsuleInk` |
| `accent.primary` | text, link, icon, tint, stroke | — | `primary` |
| `accent.primary` | fill carrying a label or glyph | — | `primary` + `onPrimary` |
| `accent.surface` | tinted wash (tiles, capsules, onboarding card, slide track) | — | `primaryContainer` + `onPrimaryContainer` |
| `accent.secondary` | tinted fill (meta pill, glyph drawer, soul ring light) | — | `secondaryContainer` + `onSecondaryContainer` |
| `accent.light` | ink on an `accent.primary` fill | — | `onPrimary` |
| `accent.light` | fill (slide-to-confirm progress) | — | `primaryContainer` |
| `accent.shaded` | mid-tone ink (neutral valence headline, soul ring dark) | — | `onPrimaryContainer` / `secondaryContainer` (see below) |
| `accent.dark`, and every tier in `StonePalette.brand` | stone body ramp | — | not a role (D5) |
| `accent.primaryHex` | — | — | deleted (no consumer) |
| `AccentColor` | global tint, alert buttons | — | regenerated as `primary` |
| `Color.secondary.opacity(…)` | photo placeholder ground | — | `surfaceContainerHighest` |
| `.green` | upload-done check | — | `tertiary` (Android: `PebblesSuccess` → `tertiary`) |
| `.black.opacity(x)` | shadow, dimming overlay | — | `scrim.opacity(x)` (`scrim` is `#000000` in every scheme) |
| `.white` | label on a `primary` fill | — | `onPrimary` |
| `.white` | text field, checkbox off, glyph canvas | stock | `stock` |
| `.white` | photo's paper border | stock | `stock` |
| `.white.opacity(0.22)` | glass rim (karma capsule, glyph tab bar) | rule | `rule` |
| `[.indigo, .purple, .orange]` | karma shimmer | — | `[primary, tertiary, sand]` |

Per-site resolutions that the table alone does not fix:

- **A `colorScheme == .dark ? a : b` choice collapses into one role**, and the `@Environment(\.colorScheme)` read goes when nothing else uses it. `SoulItem:94` becomes `secondaryContainer`. `PathBottomBar:17` becomes `primary`. `NewPebbleButton:16` becomes `surface`. Stack C (which depends on A1 only) deletes the last two files. Whichever stack edits those files second rebases over the other.
- **`ValenceStoneStyle.swift`: A3b migrates the live member, B3 retunes.** `headlineInk(for:)` (read by `ValenceHeadlineView`) is the only live member. A3b migrates its three old-token reads by the table: lowlight `onSurface`, neutral `onPrimaryContainer`, highlight `primary`. Everything else in the file is dead since #975: `style(for:)`, `selected(for:)`, `restingWash`, `joySurface`, the wash and selected-wash meshes, and their eight old-token reads. A3b may delete all of it, which is deletion, not retuning. That deletion is also what lets `Palettes.swift` go. Stack B3 retunes `inkHexes`. If B3 lands first and has already deleted the dead members, that half of A3b is a no-op. If A3b deletes `joySurface`, its allowlist entry (§7) goes in the same commit.
- **`PebblesApp`.** The segmented control takes ground `surfaceContainerHighest`, selected `secondaryContainer`, selected title `onSecondaryContainer` (was `UIColor.white`) and normal title `onSurfaceVariant`. Nav titles take `onSurface`. All go through `UIColor.pebbles(_:)`. **`OnboardingView`.** The current page dot takes `primary`, the others `outlineVariant`.
- **`PebblesScreen`.** Tint `primary`, foreground `onSurfaceVariant`, background and toolbar `surface`, label icon `primary`.
- **Fallbacks beside emotion tints** (`pageColors?.background ?? Color.system.background`) take the role from the table: the tint stays data and the fallback becomes a role.

### Stone and emotion palettes against roles

The two colour systems do not mix. Roles paint chrome: grounds, inks, hairlines, controls. Emotion palettes paint what is the pebble's: the stone, its carving, the read page tint, the name colour. A view that sits an emotion colour on a role ground keeps its existing tests (`PebblePageColorsTests`, `PathPebbleRowNameColorTests`). The scheme's own contrast is proven on Android (`ColorSchemeContrastTest`, all six schemes, high included), so iOS does not re-prove it.

### D5 — `StonePalette.brand` keeps its ramp, as stone data

`StonePalette.brand` is the stone a valence picker or a palette-less pebble wears. It is a five-tone body ramp in the emotion-palette model, not chrome. It keeps the old values verbatim, moved into `Stone/StonePalette.swift` as sRGB literals beside `.blank`: `#341B1B`, `#8C4949`, `#C07A7A`, `#EAD3D3`, `#FAF4F4`, surface `#C07A7A` @ 0.10. `Stone/` is exempt from the literal test (§7).

Considered and rejected: **building the ramp from `primary` roles.** Roles are a tonal scheme of on-pairs, not a body ramp. The lit-stone shader (#975, "dark = darkest tone") is tuned to the ramp, and the stone would change with the theme, which emotion stones never do.

### D6 — Rive keeps its baked colours; bound colours follow the roles

Colours painted inside `pbbls-cairn-states.riv`, `pbbls-logo*.riv` are artwork and stay. The one colour the app binds at runtime, `WeekRollCairnCell`'s stroke, maps by the table (`primary` focused, `onSurfaceVariant` otherwise). `Color+Rive.applyToRiveColorProperty` resolves with a trait collection carrying both `userInterfaceStyle` and `accessibilityContrast`, read from `@Environment(\.colorSchemeContrast)`, so the bound stroke follows High Contrast too. The cell also re-applies on a contrast change. This is documented in the function's doc comment.

Considered and rejected: **re-exporting the `.riv` files with role colours bound.** That is artwork work in the Rive editor, and the files ship on Android and web too.

## 5. Deletions (end of A3)

`Theme/Palettes.swift`, then `SystemBackground`, `SystemForeground`, `SystemMuted`, `SystemOnLight`, `SystemSecondary`, `AccentDark`, `AccentLight`, `AccentPrimary`, `AccentSecondary`, `AccentShaded`, `AccentSurface` (`.colorset`). `AccentColor` stays, generator-owned. `ColorTokensPreview.swift` is rewritten in A1 to grid `PebblesColorRole.allCases` in light, dark and both High Contrast variants, and its old-token section goes with the deletion. The doc comment in `EmotionPalette.swift:17` stops naming `Color.accent`. All of it goes in A3b: once A3b has migrated `ValenceStoneStyle.headlineInk` and deleted that file's dead members (§4), nothing reads an old token any more.

## 6. Error and destructive rules

### D7 — Error is the orange role; system red survives only in system dialogs

1. **Error ink.** All 21 `.foregroundStyle(.red)` sites become `.foregroundStyle(Color.pebbles.error)`: `CaptureFlowView:191`, `RecordPhotoStep:103`, `AuthView:56,92`, `AttachedPhotoView:58`, `CompletePrivacyStep:18`, `EditMomentSheet:234`, `PebbleFormView:140`, `WelcomeView:161`, `InviteAcceptSheet:80`, `CreateSoulSheet:44`, `EditSoulSheet:56`, `SettingsSheet:137,391,467`, `EditCollectionSheet:70`, `CreateCollectionSheet:53`, `GlyphCarveSheet:90`, `GlyphDetailDrawer:65,70`, `GlyphsListView:85`. An error banner, if one appears, uses `errorContainer` / `onErrorContainer`.
2. **Destructive controls the app owns drop `role: .destructive` and take the error role.** An in-app button takes `.foregroundStyle(Color.pebbles.error)`. A swipe action takes `.tint(Color.pebbles.error)`. A `Menu` / `.contextMenu` item follows the menu rule (D9). The 14 sites: `RecordPhotoStep:124,140`, `ExistingSnapRow:76`, `AttachedPhotoView:82`, `PebbleDetailSheet:66` (Menu), `PathPebbleRow:77`, `PebbleRow:36`, `SoulsListView:105`, `ConnectionsListView:93` (context menus), `CollectionsListView:102` (swipe), `InviteSheet:84`, `SettingsSheet:462`, `CollectionGlyphSection:36`, `GlyphCarveSheet:107`. Every one that deletes data already leads to a confirmation dialog, which keeps the destructive role. Dropping the role on the swipe action also stops the row from animating away before that confirmation.
3. **Platform exception.** A `Button(role: .destructive)` inside `.alert` or `.confirmationDialog` keeps the role and renders in system red. Those buttons cannot be tinted, and the role carries the destructive semantics VoiceOver announces. The 8 sites: `CaptureFlowView:112`, `MomentDeleteDialog:94`, `SettingsSheet:192`, `SoulsListView:47`, `ConnectionsListView:44,47`, `CollectionsListView:47`, `GlyphCarveSheet:54`. Their non-destructive buttons take the global tint, now `primary`.
4. **No new red.** The literal test (§7) enforces 1 to 3.

### D9 — One rule for destructive menus, in every stack

A2 first tries `.tint(Color.pebbles.error)` on the Delete `Button` of a system `Menu` (with no `role`), on a physical device, on iOS 17 and on iOS 26.
- **If the tint is honoured,** every menu stays a system `Menu` / `.contextMenu`, the card's ⋮ menu included, with the tinted Delete.
- **If it is ignored,** A2 builds **one** shared hand-built menu, `PebblesMenu` in `Components/`. It must be a VoiceOver modal (`.accessibilityAddTraits(.isModal)`, focus moved to its first item on open), dismiss on the escape gesture (`.accessibilityAction(.escape)`) and on an outside tap, and return focus to its trigger on close. Every menu that carries an orange Delete uses it, the card's ⋮ menu included, in Stack A and in every later stack. No call site rolls its own.

Plain menu ink is not an outcome. Neither is restoring `role: .destructive` to get the colour back. A2 records the outcome in the decision-log entry (§11).

Considered and rejected:
- **Custom sheets replacing system dialogs**, to get orange there too. That loses the system destructive trait and the platform dialog for one hue.
- **Keeping `role: .destructive` on owned controls next to a tint.** The role overrides the tint in menus and swipe actions, so red stays.

## 7. The literal test

### D8 — A Swift Testing ratchet, `PebblesTests/ThemeLiteralsTests.swift`

It mirrors Android's `ThemeLiteralsTest`. It locates the sources from `#filePath`, the precedent being `StoneLab/StoneLabPalettesTests.swift`. It scans every `.swift` file under `Pebbles/Features/` and `Pebbles/Components/` line by line, with two exclusions:
- the text after `//` on each line;
- everything from a file's first `#Preview` to its end (preview fixtures fake server data).

**Forbidden patterns** (Swift regex, each with its failure reason):

| Pattern | Reason |
|---|---|
| `(?<![\w.])(Color)?\.(red\|orange\|yellow\|green\|mint\|teal\|cyan\|blue\|indigo\|purple\|pink\|brown\|gray\|white\|black)\b` | a system or absolute colour. Use a `Color.pebbles` role |
| `Color\.(primary\|secondary\|accentColor)\b` | the system label colours. Use `onSurface` / `onSurfaceVariant` / `primary`. Hierarchical `.foregroundStyle(.secondary)` stays allowed: it is relative to the screen's foreground |
| `Color\(\s*(red\|white\|hue\|\.sRGB\|\.displayP3)` | a literal colour |
| `Color\(hex:\s*"` | a hex literal. `Color(hex: variable)` on server data is allowed |
| `Color\("` | a named asset. Only the generated `PebblesColors.swift` names assets |
| `UIColor\((red\|white\|hue\|named\|displayP3Red):` and `UIColor\.(system\w+\|label\|secondaryLabel\|tertiaryLabel\|white\|black\|red\|clear)\b` | a UIKit literal. Use `UIColor.pebbles(_:)` |
| `role:\s*\.destructive` | §6: only inside `.alert` / `.confirmationDialog` |

**Exempt paths** (not scanned): `Features/Path/Stone/` (stone tones and shader resolution, D5), `Features/StoneLab/` (debug lab of raw palettes), `Features/Path/Models/EmotionPalette.swift` (parses server hex), `Components/Buttons/AppleSignInButton.swift` and `Components/Buttons/GoogleSignInButton.swift` (black/white mandated by Apple's and Google's button guidelines; the ink is `googleCapsuleInk`). `Theme/`, `Shared/` and `PebblesApp.swift` are outside the scan. A3 migrates the last two anyway.

**Permanent allowlist** (`[path: (count, reason)]`; the test fails if the hit count in a file differs from its entry, so stale entries fail too):

| File | Count | Reason |
|---|---|---|
| `Features/Path/Valence/ValenceStoneStyle.swift` | 1 | `joySurface`, Joy's emotion surface (data, the Android precedent) |
| `Features/Path/Read/PebbleSnapFrame.swift` | 1 | preview fixture image drawn above `#Preview` |
| `Features/Path/Components/WeekPathView.swift` | 2 | fade mask stops (alpha only, not a colour) |
| `Features/Path/Render/PebbleAnimatedRenderView.swift` | 1 | stroke-reveal mask |
| `Features/Welcome/HandcraftedLogoView.swift` | 1 | stroke-reveal mask |
| `Features/Connections/InviteSheet.swift` | 1 | QR quiet zone must be white for scanners |
| the 8 dialog files of §6.3 | 1 or 2 each | `role: .destructive` inside a system dialog (platform exception) |

**Ratchet.** A1 lands the test with a second table, `debt`, filled from the test's own output on that commit: every remaining forbidden hit (`.red`, owned `role: .destructive`, system and absolute colours, literals), counted per file. A2 deletes the `.red` and owned-destructive debt. A3a and A3b delete the debt of the folders they migrate. A3b leaves `debt` empty and deletes the table. A count that grows fails ("new literal"). A count that shrinks without its entry being lowered also fails ("lower the debt"), so the ratchet only turns one way.

Considered and rejected: **SwiftLint `custom_rules`.** It cannot express per-file counts or a ratchet. `.swiftlint.yml` reserves error severity for defects. Android's equivalent is also a test, so the two gates read alike.

## 8. CI

`.github/workflows/ios.yml`:
- **Path filter** gains `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt`, `apps/web/lib/theme/**` and `apps/web/scripts/generate-ios-colors.ts`. Without it, a palette change that touches no iOS file never runs the iOS drift check. `web.yml` still catches it, since it runs Vitest on every PR.
- **`verify` job** (ubuntu) adds, before the existing steps: `actions/setup-node` with `cache: npm`, `npm ci`, `npm run generate:ios-colors --workspace=@pbbls/web`, then `test -z "$(git status --porcelain -- apps/ios)"`, which prints `git status` on failure. `git diff --exit-code` alone would miss a newly generated, uncommitted colorset. The job name becomes `privacy manifests · colour contrast · colour drift`.
- **`test` job** is unchanged. `ThemeLiteralsTests` and `PebblesColorsTests` run inside the existing `xcodebuild test`, and `actool` compiles the catalogs there.

`apps/web/lib/theme/ios-colorsets.test.ts` (Vitest, so in `web.yml` on every PR) is the second drift gate. It renders from the real `ColorSchemes.kt` and compares against the committed files.

## 9. Testing per part

**A1.**
- Vitest `ios-colorsets.test.ts`:
  - a fixture with two roles renders to the exact expected `Contents.json`, keys in D2's order and Xcode formatting;
  - every role has four entries mapped to the right schemes;
  - `AccentColor` equals `primary`;
  - `googleCapsuleInk` has one entry and no `appearances`;
  - `rule` carries alpha 0.14 / 0.12;
  - `stock` reads `surfaceContainerLowest` / `surfaceContainerLow`;
  - the drift check passes on the committed tree;
  - `fromKebab ∘ toKebab` is the identity on every role.
- Swift Testing `PebblesTests/PebblesColorsTests.swift`:
  - every `PebblesColorRole.allCases` resolves with `UIColor(named:in:compatibleWith:)` in `Bundle.main`;
  - `surface`, `error` and `primary` resolve, under the four trait collections (light, dark, light + `.high`, dark + `.high`), to the hexes read from `ColorSchemes.kt` at that commit (standard: `#FEF9EF`/`#131315`, `#9B4500`/`#FFB68E`, `#8E4955`/`#FFB2BC`).

  This test is the proof that the `appearances` keys are the ones UIKit honours (Risks). `ThemeLiteralsTests` is green with its `debt` baseline. `ColorTokensPreview` is checked by eye in the canvas.
- Lint, the full suite and `npm run test --workspace=@pbbls/web` are green.

**A2.**
- The `debt` entries for `.red` and owned destructive roles are gone and the test is green.
- Device or simulator, light and dark, Increase Contrast on and off: one error text per feature (auth, soul, collection, glyph, settings), every owned destructive control in §6.2, and every dialog in §6.3, which is still red. VoiceOver reads "Delete, destructive" or the equivalent on a dialog button. In-app delete buttons are announced as plain buttons.

**A3a / A3b.**
- Each part's `debt` shrinks to its remaining folders, and A3b's to nothing. `grep -rn "Color\.system\|Color\.accent\|\.system\.\|\.accent\." apps/ios/Pebbles` returns nothing after A3b.
- `verify:contrast` passes on `googleCapsuleInk`.
- Existing colour tests stay green or are updated only where they asserted an old token's value: `RippleStrokeColorTests`, `PathPebbleRowNameColorTests`, `GoogleSignInButtonContrastTests` (moves to `googleCapsuleInk`), `PebblePageColorsTests`.
- A simulator pass of every feature folder touched, in light, dark and Increase Contrast, against the hosted Spine-on-Material mock for the neutrals.

## 10. Parts

Stack A is one `gh stack`. Every part takes the labels `feat`, `ui`, `ios` and `no-lab-note` (Arkaik coverage replaces the Lab Note by standing rule) and the milestone **M64 · New Path**, where the maintainer moves #921. Arkaik: there are no view, route or data model changes. Statuses move at pickup, so in A1: list the acceptances for an appearance/theme node over `arkaik-mcp`, and if one covers the brand scheme, move it to `development` then. If none applies, nothing moves, and the later parts move nothing either.

**A1 — `feat(ios): generate the m3 colour scheme from the android export`**, on `feat/921-ios-m3-colour-generator`. `Part of #921`. Labels `feat`, `ui`, `ios`, `no-lab-note`. Milestone M64 · New Path.
- `ios-colorsets.ts` + test, `generate-ios-colors.ts`, the npm scripts.
- The generated catalogs and `Shared/Theme/PebblesColors.swift`, and `AccentColor` regenerated (the global tint moves from `#C07A7A` to `primary`, the one visible change).
- The asset-symbol setting in `project.yml`, and the widget moved to `Color.pebbles.primary` with its `AccentPrimary` deleted.
- `PebblesColorsTests`, `ThemeLiteralsTests` with the debt baseline, the `ColorTokensPreview` roles grid, and the `ios.yml` changes.
- A line in `apps/ios/CLAUDE.md`, under Swift conventions: "Colours are generated: `npm run generate:colors` writes `Assets.xcassets/M3/` and `Shared/Theme/PebblesColors.swift` from Android's `ColorSchemes.kt`. Feature code uses `Color.pebbles.<role>` only, and `ThemeLiteralsTests` fails on anything else."

*Done when:* the generator reproduces the committed tree, both drift gates are green in CI, `PebblesColorsTests` resolves all four appearances, the widget builds on the new colorset, and nothing else on screen changes except the tint.

**A2 — `feat(ios): errors are orange, destructive controls take the error role`**, on `feat/921-ios-orange-error`. `Closes #921`. Labels `feat`, `ui`, `ios`, `no-lab-note`. Milestone M64 · New Path.
- §6.1 to §6.3, and D9's device trial with whichever outcome it gives (the tint, or `PebblesMenu` adopted by every orange-Delete menu).
- The decision-log entry (§11). It supersedes the "iOS keeps its old palette (#921)" consequence of 2026-09-28 and records the alert exception.

*Done when:* `grep -rn "\.red\b" apps/ios/Pebbles/Features` finds nothing outside exempt paths, only the 8 §6.3 sites keep `role: .destructive`, the A2 device pass is done, every menu with a Delete follows D9's outcome, and the entry (with that outcome) is in the same PR.

**A3 — migrate the call sites and delete the old palette.** The 366 sites are one-line edits, but with the deletions the diff passes ~500 lines, so A3 splits by feature folder at a balanced cut. Each half lints, tests and ships alone, because the old tokens stay alive until A3b.
- **A3a — `feat(ios): shell, shared components and capture flows speak m3 roles`**, on `feat/921-ios-m3-roles-shell`, `Part of #921`, labels `feat`, `ui`, `ios`, `no-lab-note`, milestone M64 · New Path, about 180 sites:
  - `Theme/` (`PebblesScreen`, `PebblesList`, `SurfaceTile`, `PebblesToolbarTitle`, `PebbleToolbarButton`, `ProfileCard`);
  - `PebblesApp.swift`, `Components/`, `Features/Shared/`;
  - `Welcome`, `Auth`, `Onboarding`, `Record`, `Capture`, `Complete`, `Karma`, `Connections`, `Lab`;
  - `verify-color-contrast.mjs` repointed to `googleCapsuleInk`.
- **A3b — `feat(ios): path, profile and glyphs speak m3 roles; the old palette goes`**, on `feat/921-ios-m3-roles-path`, `Part of #921`, labels `feat`, `ui`, `ios`, `no-lab-note`, milestone M64 · New Path, about 185 sites:
  - `Features/Path/` (D5's `StonePalette.brand` move, D6's `Color+Rive` contrast trait), `Profile`, `Glyph`;
  - in `Features/Path/Valence/ValenceStoneStyle.swift`, the `headlineInk` migration and the deletion of the dead members (§4). `inkHexes` is B3's;
  - §5's deletions (`Palettes.swift` and all 11 old colorsets), with `debt` removed from the test.

*Done when:* `Palettes.swift` and the 11 colorsets are gone, the grep in §9 is empty, `ThemeLiteralsTests` has no `debt` table, the A3 simulator pass is done, and the suite is green.

## 11. Decision-log entry (A2 appends it, dated the day A2 merges)

```markdown
## 2026-10-20 — iOS adopts the brand Material 3 scheme; errors are orange, system dialogs keep their red (#921)

- **Status:** taken
- **Scope:** ios, ui
- **Context:** Android (#853, #990, #1118) and the web (#986, #991) draw from the M3-evo scheme generated from `ColorSchemes.kt`, with an orange error and no red anywhere. iOS kept the #456 palette (`Color.system` / `Color.accent`) and system red for errors and destructive buttons, recorded as "iOS keeps its old palette (#921)".
- **Decision:**
  - iOS takes the same scheme, generated from `ColorSchemes.kt` by `npm run generate:ios-colors --workspace=@pbbls/web` into `Assets.xcassets/M3/` (app and widget) and `Shared/Theme/PebblesColors.swift`. Feature code names colours only as `Color.pebbles.<role>` (`UIColor.pebbles(_:)` for UIKit).
  - Contrast is standard plus the asset catalog's High Contrast appearance, filled from Android's *high* schemes. Medium is not shipped.
  - The Spine neutrals apply app-wide, as on Android (#1118 D1). Old tokens map by use through the Spine rule set. The nine scheme-only Spine tokens (`halo`, `stock`, `rule`, `cardRule`, `line`, `seam`, `teeth`, `ring`, `ripple`) are generated. Blends with an emotion palette are computed in Swift (`SpineColors.swift`).
  - Error is the `error` role (key `#EA6B00`). Destructive controls the app owns drop `role: .destructive` and take the error role. **Platform exception:** the destructive button of a system `.alert` / `.confirmationDialog` keeps the role and its system red, because it cannot be tinted and the role is what VoiceOver announces.
  - **Destructive menus, one rule for every stack:** a system `Menu` item takes `.tint(Color.pebbles.error)` if iOS honours it. Otherwise every menu with an orange Delete, the card's ⋮ menu included, uses the one shared `PebblesMenu` (`Components/`, a VoiceOver modal with escape). A2 records here which one the device trial gave.
  - Emotion palettes, stone ramps (`StonePalette.brand` keeps the old accent ramp as stone data) and baked `.riv` colours are not scheme roles.
  - `ThemeLiteralsTests` fails on colour literals, system colours, named assets and stray destructive roles in `Features/` and `Components/`.
- **Why:** One palette on four clients is what the database contract already assumes for everything else. Two brand looks would be drift. Generating the colours keeps the Kotlin as the single source. The alert exception is the one place where the no-red rule would cost accessibility rather than a hue.
- **Consequences:**
  - Re-exporting the Android palette now means running `generate:m3` and `generate:ios-colors` in the same PR. `web.yml` and `ios.yml` both fail otherwise.
  - iOS has no wallpaper colour, so nothing is pinned. The error roles are simply never system red.
  - Red remains visible in iOS system dialogs. Anyone tempted to "fix" that with custom sheets should read this entry first.
  - The old colorsets and `Palettes.swift` are deleted at the end of the stack.
- **Supersedes / Superseded-by:** Supersedes the "iOS keeps its old palette (#921)" consequence of **2026-09-28 — The error colour is orange, never red** (#990), and answers the #921 lines of 2026-09-24 (#853), 2026-09-28 (#986) and 2026-10-18 (#1118).
- **Refs:** #921, #853, #990, #986, #1118, `docs/superpowers/specs/2026-10-20-ios-m3-colour-design.md`, `apps/web/lib/theme/ios-colorsets.ts`, `apps/ios/Shared/Theme/PebblesColors.swift`, `apps/ios/PebblesTests/ThemeLiteralsTests.swift`.
```

## 12. New patterns, raised in the PR

House rules say a new pattern needs discussion first. Each of these is named in the PR body of the part that introduces it, so the maintainer accepts it explicitly:
- **A Swift entry point generated by a web script** (A1). `Shared/Theme/PebblesColors.swift` and the colorsets come from `apps/web/scripts/generate-ios-colors.ts`, a TypeScript tool in another workspace that writes into `apps/ios`. This is the first iOS source file not written by hand or by Xcode.
- **The debt ratchet** (A1). `ThemeLiteralsTests` carries a shrinking per-file `debt` table that fails when a count moves either way. A test that knowingly passes over existing violations is new to the iOS suite.
- **The destructive-menu rule** (A2, D9). It holds for every stack, and it may introduce `PebblesMenu`, the first hand-built replacement for a system control in the app.

## 13. Risks

- **A misspelled appearance is dropped silently.** A test catalog with `"appearance" : "contrst"` compiled without a warning: `actool` left out the High Contrast "Any" rendition, and the colour fell back to light. CI's `actool` pass is therefore not validation. `PebblesColorsTests`, resolving real trait collections, is. It ships in A1, before any call site depends on the roles.
- **Xcode rewrites `Contents.json`.** Opening the catalog in Xcode and touching a colour reformats or reorders the file, and the drift gate fails. That is intended, because the files are generated. The generator writes Xcode's own formatting, so merely opening the catalog does not churn it.
- **`AccentColor` in the asset catalog.** It is the reserved global tint (`ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME` defaults to it). It tints alert buttons, `Toggle`, `ProgressView`, links and default `Button`s. Regenerating it as `primary` (rose `#8E4955` / `#FFB2BC`, darker than today's `#C07A7A`) changes all of these in A1, before any feature migrates. The A1 PR says so and shows before/after shots of one dialog and one form.
- **`.tint` on `Menu` and `.contextMenu` items.** iOS renders menu items through `UIMenu`, which may ignore a SwiftUI tint. D9 settles this on a device in A2. If the tint is ignored, `PebblesMenu` is the cost. It has to match system menu behaviour for VoiceOver (modal, escape, focus return) and dismissal, which the A2 device pass checks.
- **The widget's Live Activity** renders on the lock screen and Dynamic Island, which force their own appearance. `primary` resolves against that environment, as `AccentPrimary` does today. Check it once on device in A1.
- **Asset symbol generation.** If the setting does not reach the widget target, Xcode generates `Color.primary`-shaped statics and warns about conflicts. A1 sets it on both targets and checks the build log for asset-symbol warnings.
- **`UIColor.pebbles(_:)` traps on a missing asset.** That is a setup bug, like `AppEnvironment`'s `fatalError`, and `PebblesColorsTests` catches it before it ships.
- **The visual change is total.** Every ground moves from white/`#171012` to the Spine paper `#FEF9EF` / cool night `#131315`, and `accent.surface` washes become opaque `primaryContainer`. The review is the simulator pass per part plus the hosted Spine-on-Material mock. Reviewing it by eye per folder is why A3 is split by folder rather than by token.
- **Stack ordering.** C depends on A1 only, and B does not depend on A. Where two stacks edit the same file (`PathBottomBar` and `NewPebbleButton` with C, `ValenceStoneStyle` with B3), the later one rebases, and §4 and §10 say which stack owns each file. On `ValenceStoneStyle` either stack may delete the dead members, so whichever lands second finds them gone, and its own deletion becomes a no-op. `inkHexes` is B3's alone.
