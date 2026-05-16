# iOS Colors System Refactor — Design

**Issue:** [#456](https://github.com/Bohns/pbbls/issues/456) — `[Quality] Colors system refacto on iOS`
**Milestone:** M32 · iOS Quality
**Date:** 2026-05-17

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

`pebblesListRow` and `pebblesPathBackground` are not part of the new palette. They are component-level recipes ("white in light, something else in dark") that happen to reference other tokens. They are inlined locally during the per-feature commits:

- `pebblesPathBackground` — inline a scheme switch in `PathView` directly.
- `pebblesListRow` — inline at each of the 23 call sites; if a clear shared shape emerges, factor it back out into a `Pebbles/Theme/SurfaceRecipes.swift` later (out of scope for this PR).

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

### Per-call-site decision (reviewed live during the relevant feature commit)

| Legacy                  | Why it is case-by-case |
|-------------------------|------------------------|
| `pebblesBorder`         | Could be `system.muted`, `system.secondary`, or `accent.secondary` depending on the divider's role. |
| `pebblesSurface`        | Three sites only — likely `system.muted` or `accent.surface`, but each one needs a look. |
| `pebblesSurfaceAlt`     | Three sites — same reasoning. |
| `rippleDefault`         | Currently muted-hex; may want `system.muted` or `accent.surface`. |
| `rippleInactive`        | Currently border-hex; may want `system.secondary` or `accent.secondary`. |
| `pebblesListRow`        | 23 sites — inline per the helpers section above; decide the look at each call site. |
| `pebblesPathBackground` | Inline in `PathView`. |

The mechanical mappings can be applied with a per-folder search-and-replace; each file is still opened and eyeballed (with Xcode preview) for the per-call-site decisions.

## Preview support

A new file `Pebbles/Theme/ColorTokensPreview.swift` renders every system + accent tier in both light and dark mode as a grid. It serves as the north star during the per-feature reviews.

For component-level previews the work relies on the 54 existing `#Preview` blocks. A new `#Preview` is added only when (a) a touched component lacks one AND (b) the visual change at that site is non-trivial.

## Commit sequence (single PR)

Branch: `quality/456-ios-colors-refacto`
PR title: `quality(ios): refactor colors system`

The commit prefix `(ios)` matches the convention used by recent project commits (`feat(ios)`, `fix(ios)`). The species `quality` matches the issue's `[Quality]` title bracket.

```
commit  1  feat(ios): add system + accent palettes
           - 10 new colorsets + retuned AccentColor in Assets.xcassets
           - Pebbles/Theme/Palettes.swift (SystemPalette, AccentPalette,
             Color.system, Color.accent)
           - Pebbles/Theme/ColorTokensPreview.swift (light + dark grid)
           - No consumer changes. Legacy still in place.

commit  2  quality(ios): migrate Auth to new palette
commit  3  quality(ios): migrate Glyph to new palette
commit  4  quality(ios): migrate Lab to new palette
commit  5  quality(ios): migrate Onboarding to new palette
commit  6  quality(ios): migrate Path to new palette
commit  7  quality(ios): migrate PebbleMedia to new palette
commit  8  quality(ios): migrate Profile to new palette
commit  9  quality(ios): migrate Shared to new palette
commit 10  quality(ios): migrate Welcome to new palette

  - Each feature commit applies the mechanical mappings, then walks the
    per-call-site decisions inline together. Verification: existing
    #Previews + ColorTokensPreview as the reference. Inline
    pebblesListRow / pebblesPathBackground locally.

commit 11  quality(ios): remove legacy color tokens
           - Delete Pebbles/Theme/Color+Pebbles.swift
           - Delete 11 legacy colorsets from Assets.xcassets
             (Background, Foreground, Surface, SurfaceAlt, Muted,
              MutedForeground, Border, AccentSoft, RippleDefault,
              RippleActive, RippleInactive). AccentColor.colorset stays.
```

### Per-feature commit workflow

1. List the files in the feature that touch legacy tokens, with the tokens each uses.
2. Open the relevant Xcode preview together — confirm the current look.
3. Apply mechanical mappings; propose a mapping for each per-call-site decision.
4. User confirms or redirects; commit.

## Risks

**Apple's `AccentColor` slot.** Removing it would change SwiftUI's default `.tint` resolution and break `AppIcon` tinting. Mitigation: keep `AccentColor.colorset` re-tuned to `C07A7A` (matching `AccentPrimary`), referenced by Apple's tooling, not by Swift code.

**SVG hex injection.** `pebblesAccentHex` is consumed by `PebbleRenderView` for inline SVG `currentColor` replacement. The new `accent.primaryHex = "#C07A7A"` is identical to today's value, so no rendering change is expected. Swap all callers in the Path-feature commit.

**Per-call-site decisions taken without enough context.** The 7 tokens listed as case-by-case may have call sites where the "right" mapping is non-obvious. Mitigation: the per-feature commits are interactive — if a decision is unclear, stop and discuss before committing.

**No Arkaik impact.** This work does not add, remove, or rename screens, routes, data models, or endpoints. The Arkaik map does not need updating.

## Verification

**Per commit:** build the iOS target (`⌘B`) in Xcode and confirm `#Preview`s for any touched component render correctly in both schemes. `xcodegen generate` is only required if `project.yml` changes (it shouldn't — colorsets and Swift files are auto-discovered).

**Final commit (commit 11):**

- Grep must return zero results:
  ```
  grep -rn "Color\.pebbles\|Color\.ripple" apps/ios/Pebbles --include="*.swift"
  grep -rn 'Color("\(Background\|Foreground\|Surface\|SurfaceAlt\|Muted\|MutedForeground\|Border\|AccentSoft\|Ripple[A-Za-z]*\)")' apps/ios/Pebbles --include="*.swift"
  ```
- Asset catalog must contain none of the legacy colorset folders.
- Full app smoke in the simulator, light + dark: Path, Profile, Lab, Onboarding, Auth, Welcome.

## PR metadata

- **Labels (to propose to user before opening the PR):** `quality`, `ios`, `ui`. The issue carries `feat, ios, ui`; we propose swapping `feat` → `quality` since the actual work is a refactor and the issue's own title uses `[Quality]`. Confirm with the user before creating the PR.
- **Milestone:** M32 · iOS Quality (inherited from issue).
- **Body:** `Resolves #456`, followed by a short summary and the commit-by-commit overview from above.
