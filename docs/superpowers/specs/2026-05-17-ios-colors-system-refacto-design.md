# iOS Colors System Refactor — Design

**Issue:** [#456](https://github.com/Bohns/pbbls/issues/456) — `[Quality] Colors system refacto on iOS`
**PR:** [#458](https://github.com/Bohns/pbbls/pull/458) — `quality(ios): refactor colors system`
**Milestone:** M32 · iOS Quality
**Date:** 2026-05-17
**Status:** Shipped. This document was last updated post-merge to describe the as-shipped solution; divergences from the original plan are called out in the **Execution notes** section at the bottom.

## Context

The legacy iOS color tokens are a mix of brand names and design-token names, accumulated organically across the app. There are 12 colorsets in `Assets.xcassets` (`Background`, `Foreground`, `Surface`, `SurfaceAlt`, `Muted`, `MutedForeground`, `Border`, `AccentColor`, `AccentSoft`, `RippleDefault/Active/Inactive`) and a single Swift façade in `Pebbles/Theme/Color+Pebbles.swift` exposing them as `Color.pebbles*` plus two trait-aware computed helpers (`pebblesListRow`, `pebblesPathBackground`). About 60 files consume these tokens; top users are `pebblesMutedForeground` (63 refs), `pebblesAccent` (43), `pebblesBackground` (28), `pebblesForeground` (25), `pebblesListRow` (23).

## Intention

Collapse the system into two clearly-scoped palettes that match the structure of `EmotionPalette`:

- **System palette** — four primitives for interface chrome (foreground, secondary, muted, background).
- **Accent palette** — six tiers for branded surfaces (dark, shaded, primary, secondary, light, surface), mirroring the per-emotion palettes already loaded from the DB.

All legacy tokens are removed from both the codebase and the asset catalog.

## Tokens

### System palette (`Color.system`)

| Member       | Light    | Dark     |
|--------------|----------|----------|
| `foreground` | `4A3639` | `E9E2E4` |
| `secondary`  | `7A5E64` | `AF979D` |
| `muted`      | `E9E2E4` | `2E2024` |
| `background` | `FFFFFF` | `171012` |

### Accent palette (`Color.accent`)

All accent tiers are scheme-independent (the spec provides a single hex per tier).

| Member      | Value                          |
|-------------|--------------------------------|
| `dark`      | `341B1B`                       |
| `shaded`    | `8C4949`                       |
| `primary`   | `C07A7A`                       |
| `secondary` | `EAD3D3`                       |
| `light`     | `FAF4F4`                       |
| `surface`   | `C07A7A` with alpha `0.10` (baked into the colorset, not computed at call sites — keeps `accent.surface` symmetric with the other tiers) |

The accent struct also exposes `primaryHex: String = "#C07A7A"` so it can replace the existing `pebblesAccentHex` consumer in SVG-text injection (`PebbleRenderView`) without a behavior change.

## Swift façade

A new file `Pebbles/Theme/Palettes.swift` defines two structs and the two static instances:

```swift
struct SystemPalette {
    let foreground: Color
    let secondary: Color
    let muted: Color
    let background: Color
}

struct AccentPalette {
    let dark: Color
    let shaded: Color
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color

    let primaryHex: String
}

extension Color {
    static let system = SystemPalette(
        foreground: Color("SystemForeground"),
        secondary:  Color("SystemSecondary"),
        muted:      Color("SystemMuted"),
        background: Color("SystemBackground")
    )

    static let accent = AccentPalette(
        dark:       Color("AccentDark"),
        shaded:     Color("AccentShaded"),
        primary:    Color("AccentPrimary"),
        secondary:  Color("AccentSecondary"),
        light:      Color("AccentLight"),
        surface:    Color("AccentSurface"),
        primaryHex: "#C07A7A"
    )
}
```

During the migration commits `Palettes.swift` coexists with `Color+Pebbles.swift`. The legacy file is deleted in the final cleanup commit.

### Helpers that are NOT migrated as tokens

`pebblesListRow` and `pebblesPathBackground` are not part of the new palette. They were component-level recipes ("white in light, something else in dark") that happened to reference other tokens.

- **`pebblesPathBackground` — removed entirely**, not inlined. During execution the `.pebblesScreen()` modifier was simplified to always use `Color.system.background` (the `background:` parameter was dropped). PathView and ProfileView, the only two callers that ever overrode the background, now use the standard shape. The recipe is no longer needed anywhere.
- **`pebblesListRow` — swapped per call site.** Two patterns emerged: rows inside a `List` or `Form` use `.listRowBackground(Color.clear)` paired with `.listRowSeparatorTint(Color.system.muted)` on the container (Lab, Profile collections list, PebbleFormView). Free-standing "card" views that used `.background(Color.pebblesListRow)` (the four Profile cards) became `.background(Color.system.background)` plus a `Color.system.muted` outline — an "outline only" card recipe.

### Navigation title color (added during execution)

`UINavigationBarAppearance` is configured in `PebblesApp.init` so navigation titles (inline + large) render in `system.foreground` instead of SwiftUI's default `UIColor.label`. Asset-catalog can't override `Color.primary` directly — only `AccentColor` has that magic — so titles must be re-tinted via the UIKit appearance proxy. The same `PebblesApp.init` already configured the segmented-control appearance via the same pattern.

## Asset catalog

Ten new colorsets are added under `Pebbles/Resources/Assets.xcassets/`:

```
SystemForeground.colorset
SystemSecondary.colorset
SystemMuted.colorset
SystemBackground.colorset
AccentDark.colorset
AccentShaded.colorset
AccentPrimary.colorset
AccentSecondary.colorset
AccentLight.colorset
AccentSurface.colorset
```

The `System*` prefix avoids name collisions with the existing `Background`, `Foreground`, `Muted` colorsets during the interim commits. The prefix is kept permanently (no renaming after legacy cleanup) since asset names are referenced only inside `Palettes.swift`.

### `AccentColor` slot is preserved

Apple's asset catalog reserves the name `AccentColor` for the global SwiftUI tint and for `AppIcon` tinting. The existing `AccentColor.colorset` is kept (re-tuned to the same hex as `AccentPrimary` — `C07A7A`, scheme-independent) so default tint behavior and the app icon stay correct, even though no Swift code references it by name after the migration.

## Migration mapping

### Direct, mechanical (applied uniformly during the per-feature commits)

| Legacy                       | New                  |
|------------------------------|----------------------|
| `pebblesBackground`          | `system.background`  |
| `pebblesForeground`          | `system.foreground`  |
| `pebblesMuted`               | `system.muted`       |
| `pebblesMutedForeground`     | `system.secondary`   |
| `pebblesAccent`              | `accent.primary`     |
| `pebblesAccentSoft`          | `accent.secondary`   |
| `pebblesAccentHex`           | `accent.primaryHex`  |
| `rippleActive`               | `accent.primary`     |

### Per-call-site (decided inline during execution)

| Legacy                  | Final mapping | Notes |
|-------------------------|---------------|-------|
| `pebblesBorder`         | `system.muted` | Uniform across all 6 sites (Google sign-in capsule, primary-button disabled stroke, checkbox stroke, text input border, week-card outline, privacy-chip outline). |
| `pebblesSurface`        | `system.background` | Single site (RippleBadge digit on dark + active). The digit blends into the dark base so the active ring carries the visual emphasis. |
| `pebblesSurfaceAlt`     | `accent.surface` (Onboarding card) · `system.muted` (ValencePickerSheet inactive pill) | Branded warm for the onboarding illustration; neutral for a picker's off-state. |
| `rippleDefault`         | `system.muted` | "Potential" rings beyond the user's level. |
| `rippleInactive`        | `system.secondary` (RippleStrokeColor) · `system.muted` (AssiduityGrid dots) | Intentionally divergent: RippleStrokeColor needed default vs inactive to read distinctly (split via `system.muted` / `system.secondary`); AssiduityGrid uses `system.muted` because its only contrast is against `accent.primary` active dots. |
| `pebblesListRow`        | `Color.clear` + `.listRowSeparatorTint(Color.system.muted)` on the container (Lab, Profile collections, PebbleFormView) · `system.background` for free-standing Profile cards | See **Helpers that are NOT migrated as tokens** above. |
| `pebblesPathBackground` | Removed entirely | See **Helpers** — `.pebblesScreen()` simplification killed the override knob. |

Per-feature mechanical mappings were applied via `sed` across each folder, then per-call-site decisions were proposed inline via interactive multi-choice questions and committed file-by-file.

## Preview support

A new file `Pebbles/Theme/ColorTokensPreview.swift` renders every system + accent tier in both light and dark mode as a grid. It serves as the north star during the per-feature reviews.

For component-level previews the work relies on the 54 existing `#Preview` blocks. A new `#Preview` is added only when (a) a touched component lacks one AND (b) the visual change at that site is non-trivial.

## Commit sequence (single PR)

Branch: `quality/456-ios-colors-refacto`. PR title: `quality(ios): refactor colors system`. PR landed with 24 commits — the original plan called for 11 but the interactive review surfaced several refinements (preview fixes, design tweaks, missed dot-elided refs) that became their own commits. The full sequence:

```
feat(ios):    add system + accent palettes (foundation)
quality(ios): polish ColorTokensPreview headers
quality(ios): migrate shared components to new palette
quality(ios): use clear fill + outline for disabled primary button
quality(ios): migrate Glyph feature to new palette
quality(ios): recolor nav titles + migrate missed UIColor refs
quality(ios): tint Undo/Clear with accent.surface
quality(ios): migrate Lab feature to new palette
quality(ios): simplify pebblesScreen, drop background override
docs(ios):    add #Preview for AnnouncementDetailView
fix(ios):     use valid LogStatus in AnnouncementDetailView preview
quality(ios): migrate Onboarding feature to new palette
quality(ios): soften onboarding page indicator
quality(ios): migrate Path feature to new palette
quality(ios): migrate Profile feature to new palette
fix(ios):     inject PathStatsService in ProfileView preview
quality(ios): add system.muted border around profile glyph slot
quality(ios): round profile glyph slot to 34pt, clear fill
fix(ios):     catch-up missed dot-elided refs in ProfileCollectionCard
quality(ios): migrate Shared/Ripples to new palette
quality(ios): migrate Welcome feature to new palette
quality(ios): remove legacy color tokens
```

(Two docs commits — the spec and plan — precede the code.)

`PebbleMedia` was skipped: zero color refs. `Auth` had no direct color refs of its own (everything composed shared widgets in `Pebbles/Components/`), so its "commit" was verification-only.

### Per-feature commit workflow (what actually happened)

1. `grep -rEn` inventory of legacy tokens in the feature's folder, including dot-elided refs (`\.pebbles[A-Z]`).
2. Open the relevant Xcode preview together — confirm the current look.
3. Apply mechanical mappings via `sed -i ''` (ordered carefully — `AccentHex` and `AccentSoft` before `Accent`; `MutedForeground` before `Muted` — to avoid partial matches).
4. Propose mappings for per-call-site decisions via interactive multi-choice questions; pause for user confirmation.
5. Rebuild (`xcodebuild -scheme Pebbles … build`); commit.

The interactive multi-choice prompts during step 4 are what turned the "11-commit, one-decision-per-feature" plan into a 24-commit collaboration with design feedback baked in commit-by-commit.

## Risks

**Apple's `AccentColor` slot.** Removing it would change SwiftUI's default `.tint` resolution and break `AppIcon` tinting. Mitigation: keep `AccentColor.colorset` re-tuned to `C07A7A` (matching `AccentPrimary`), referenced by Apple's tooling, not by Swift code.

**SVG hex injection.** `pebblesAccentHex` is consumed by `PebbleRenderView` for inline SVG `currentColor` replacement. The new `accent.primaryHex = "#C07A7A"` is identical to today's value, so no rendering change is expected. Swap all callers in the Path-feature commit.

**Per-call-site decisions taken without enough context.** The 7 tokens listed as case-by-case may have call sites where the "right" mapping is non-obvious. Mitigation: the per-feature commits are interactive — if a decision is unclear, stop and discuss before committing.

**No Arkaik impact.** This work does not add, remove, or rename screens, routes, data models, or endpoints. The Arkaik map does not need updating.

## Verification

**Per commit:** build the iOS target (`⌘B`) in Xcode and confirm `#Preview`s for any touched component render correctly in both schemes. `xcodegen generate` was required once — during the foundation commit — to register the two new Swift files in the (git-ignored) `.xcodeproj`. After that, `project.yml`'s directory globs handled additions automatically. The final cleanup commit also needed `xcodegen generate` to drop the stale `Color+Pebbles.swift` reference.

**Final cleanup commit:** all four greps must return zero rows.

```bash
# Swift references to Color.pebbles* or Color.ripple* via the façade
grep -rEn '(Color\.pebbles|Color\.ripple[ADIc])' apps/ios/Pebbles --include="*.swift" \
  | grep -v "pebblesScreen"

# Dot-elided Swift references (e.g. .pebblesAccent when type is inferred — the
# pattern that bit us mid-PR and required several catch-up commits)
grep -rEn '\.(pebbles[A-Z][A-Za-z]*|ripple(Active|Default|Inactive))' apps/ios/Pebbles --include="*.swift" \
  | grep -vE "pebblesScreen|pebblesCount|pebblesToNextLevel|self\.ripple|rippleLevel"

# UIColor(named:) references to legacy asset names
grep -rEn 'UIColor\(named: *"(Background|Foreground|Surface|SurfaceAlt|Muted|MutedForeground|Border|AccentSoft|Ripple[A-Za-z]*)"' apps/ios/Pebbles --include="*.swift"

# Color("string") references to legacy asset names
grep -rEn 'Color\("(Background|Foreground|Surface|SurfaceAlt|Muted|MutedForeground|Border|AccentSoft|Ripple[A-Za-z]*)"' apps/ios/Pebbles --include="*.swift"
```

Asset catalog must contain none of the 11 legacy colorset folders; `AccentColor.colorset` remains. The user smoke-tested the running app in the simulator across light + dark for Welcome → Onboarding → Auth → Path → Profile → Lab before merge.

## PR metadata

- **Labels:** `quality`, `ios`, `ui` (issue carried `feat, ios, ui`; swapped `feat` → `quality` since the work is a refactor and the issue's title uses `[Quality]`).
- **Milestone:** M32 · iOS Quality (inherited).
- **Resolves:** #456.
- **Follow-up:** [#457](https://github.com/Bohns/pbbls/issues/457) — Glyph carve sheet TextField swap to `PebblesTextInput` (the buttons half of #457 was pulled into #456 mid-review).

## Execution notes (what we didn't anticipate)

These came out of the inline review and deserve their own callouts so future migrations don't repeat the same surprises:

1. **Dot-elided color references.** The original plan grep only looked for `Color.pebbles*` and `Color.ripple*`. Swift allows `.pebblesAccent` when the return type is `Color` (as in `enum.color: Color { switch self … }`). Several files used this form, slipped past the initial inventory, and produced a separate catch-up commit. The final-verification grep now matches both forms.
2. **`UIColor(named:)` references.** `PebblesApp.configureSegmentedControlAppearance` and `OnboardingView.init` both held UIKit-side references to legacy asset names. These never appeared in any `Color.*` search and would have broken silently at runtime when the legacy assets were deleted. Caught during a defensive re-grep before Task 15.
3. **Top-level `Pebbles/Components/*` shared widgets.** The original plan grouped tasks by `Pebbles/Features/<X>/`, but the Auth flow's color usage actually lives in shared widgets one directory above (`Components/Auth/`, `Components/Buttons/Sign*`, `Components/Inputs/`, `Components/Checkboxes/`). Discovered while listing files for the Auth commit; led to a dedicated "shared components" commit before Auth.
4. **Preview crashes.** Two views had previews that crashed at canvas time: `AnnouncementDetailView` (no preview at all — added one with a JSON fixture that initially used an invalid `LogStatus` value), and `ProfileView` (missing `PathStatsService` from the environment). Both fixed inline.
5. **In-scope vs follow-up triage.** The user asked for two additional changes during the Glyph review: replace the carve-sheet TextField with `PebblesTextInput`, and restyle the Undo/Clear buttons with `accent.surface`. The TextField swap is genuine UI polish, not a color migration, so it went to #457. The button restyle is a colors concern and was pulled into this PR. The pattern: stay strict about "is this actually about colors?" — but be willing to absorb the parts that are.
6. **`.pebblesScreen()` simplification.** Was not in the original spec. The user observed mid-review that the `background:` parameter was rarely used and asked to remove it; we did, killing the `pebblesPathBackground` recipe entirely (it had been a special case for PathView and ProfileView's pure-white-in-light treatment). Net win: every screen now uses `system.background` uniformly.
7. **Navigation title color.** Also not in the original spec. The user noticed that `.navigationTitle` rendered in SwiftUI's default `UIColor.label` rather than the brand foreground; fixed via `UINavigationBarAppearance` configuration in `PebblesApp.init`. Same pattern as the existing segmented-control configuration that lived right next to it.
