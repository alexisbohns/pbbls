# iOS color modifiers — design

**Issue:** [#270](../../../issues/270) — `[Feat] Apply default pebbles color system`
**Date:** 2026-04-17
**Scope:** `apps/ios`
**Supersedes:** `2026-04-17-ios-color-system-design.md` (strategy rejected — see "Prior attempt" below)

## Context

The iOS app currently uses SwiftUI's default system colours (iOS blue accent, system-grouped-background grey, etc.). The web app uses the Pebbles "Blush Quartz" palette defined in `apps/web/app/globals.css`. Issue #270 asks iOS to adopt the same palette.

An earlier spec (`2026-04-17-ios-color-system-design.md`) tried to do this by overriding Apple's "default" colour tokens at the root — `AccentColor` asset + `ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME` build setting, `UIKit.appearance()` proxies, and `.background()` on `RootView`. That approach failed in practice:

- `NavigationStack` / `List` / `Form` draw their own system-grouped backgrounds that cover any `.background()` applied at `RootView` — the Pebbles background was invisible.
- SwiftUI `Button`'s default tint reads from the environment's `.tint`, not from the asset-catalog global accent on iOS 17+ SwiftUI — so the asset + build-setting approach didn't change button colour despite being correctly wired in the binary (`NSAccentColorName=AccentColor` in Info.plist, `AccentColor` with correct hex in `Assets.car`).
- `UIKit.appearance()` proxies (`UITableView.appearance().backgroundColor`, `UILabel.appearance().textColor`) are global root-level configuration, but only reach UIKit-backed components. SwiftUI's `List` on iOS 17+ is `UICollectionView`-backed, and SwiftUI `Text` does not use `UILabel` — so the proxies are effectively inert for a SwiftUI-only app.

The conclusion: **iOS 17+ SwiftUI does not expose a root-level token-override mechanism for the surfaces we care about.** The only reliable lever is applying SwiftUI modifiers on the views that render. This spec embraces that reality while keeping per-call-site work minimal.

## Goals

- Every Pebbles screen renders with the Blush Quartz palette: Pebbles accent (dusty rose) for buttons / chevrons / selected tab items, Pebbles background behind lists and content, Pebbles foreground for body text, Pebbles-coloured nav and tab bars.
- Both light and dark colour schemes supported from the first release.
- Adding a new screen is one line of code to opt into the Pebbles look — the styling recipe lives in one place.
- No rewrites of SwiftUI primitives (`List`, `NavigationStack`, `TabView`) — we apply modifiers, we do not replace components.

## Non-goals

- Alternate web themes (Stoic Rock, Cave Pigment, Dusk Stone, Moss Pool). Only Blush Quartz ships.
- A destructive / error colour token. Not listed in issue #270; deferred.
- Runtime theme switching. Light/dark follows the system setting only.
- Styling of individual `List` row internals. If iOS's default cell styling wins for row text colour, we accept it; the spec focuses on chrome and first-order text, not cell internals.
- Per-component wrappers (`PebblesList`, `PebblesRow`, etc.). Out of scope for this issue.

## Approach

Three layers, each with one clear responsibility:

1. **Asset catalog** — the eight `.colorset` resources holding light/dark sRGB hex values for Pebbles tokens. Restored from backup in `/tmp/pebbles-colorsets-backup/` (computed once, already correct — no need to redo the oklch→sRGB conversion).
2. **Token layer** — a `Color` extension with eight typed accessors (`Color.pebblesBackground`, `Color.pebblesAccent`, …). One place to see all Pebbles tokens; compile-time safe; no string-typed asset name lookups.
3. **Modifier layer** — a single `.pebblesScreen()` `ViewModifier` that bundles the Pebbles styling recipe (tint, foreground, hidden scroll-content background, background, nav/tab toolbar backgrounds). Applied once per screen, inside the screen's `NavigationStack` where one exists.

## Design

### 1. Asset catalog

Restore the eight `.colorset` directories from `/tmp/pebbles-colorsets-backup/` into `apps/ios/Pebbles/Resources/Assets.xcassets/`:

| Asset             | Light      | Dark       |
|-------------------|------------|------------|
| `AccentColor`     | `#C07A7A`  | `#CE7E8A`  |
| `Background`      | `#F8F0F0`  | `#120809`  |
| `Foreground`      | `#2E2024`  | `#E7DADC`  |
| `Surface`         | `#F0E4E4`  | `#1C1012`  |
| `SurfaceAlt`      | `#E8D8DA`  | `#27181A`  |
| `Muted`           | `#F0E4E4`  | `#1F1617`  |
| `MutedForeground` | `#7A5E64`  | `#887577`  |
| `Border`          | `#E0D0D2`  | `#2F2224`  |

Dark values were derived from the web's `oklch()` variants using Björn Ottosson's OKLab→sRGB conversion during the prior attempt. These values were already committed and verified, so they are reused as-is.

### 2. Token layer

One new file: `apps/ios/Pebbles/Theme/Color+Pebbles.swift`.

```swift
import SwiftUI

extension Color {
    static let pebblesBackground      = Color("Background")
    static let pebblesForeground      = Color("Foreground")
    static let pebblesSurface         = Color("Surface")
    static let pebblesSurfaceAlt      = Color("SurfaceAlt")
    static let pebblesMuted           = Color("Muted")
    static let pebblesMutedForeground = Color("MutedForeground")
    static let pebblesBorder          = Color("Border")
    static let pebblesAccent          = Color("AccentColor")
}
```

These accessors wrap `Color(_ named:)` — a failed lookup silently returns a fallback colour rather than crashing. The asset catalog and the extension are kept in sync manually; if a future colour set is added, it must be added to both.

### 3. Modifier layer

One new file: `apps/ios/Pebbles/Theme/PebblesScreen.swift`.

```swift
import SwiftUI

private struct PebblesScreen: ViewModifier {
    func body(content: Content) -> some View {
        content
            .tint(.pebblesAccent)
            .foregroundStyle(.pebblesForeground)
            .scrollContentBackground(.hidden)
            .background(Color.pebblesBackground)
            .toolbarBackground(Color.pebblesBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarBackground(Color.pebblesBackground, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
    }
}

extension View {
    /// Applies the Pebbles design-system styling: tint, foreground, background,
    /// hidden scroll-content background, and nav/tab toolbar backgrounds.
    ///
    /// Apply inside a `NavigationStack` so the toolbar modifiers attach to the
    /// correct bar. Modifiers that don't apply to the current context (e.g.
    /// `.toolbarBackground` when there is no toolbar) are inert.
    func pebblesScreen() -> some View {
        modifier(PebblesScreen())
    }
}
```

Why each modifier:

- `.tint(.pebblesAccent)` — sets the accent for every SwiftUI control that reads from the environment's `tint`: buttons, chevrons, the selected tab item, checkboxes, swipe actions.
- `.foregroundStyle(.pebblesForeground)` — cascades to `Text` default colour. Views that specify their own `.foregroundStyle(.secondary)` or similar are unaffected (expected).
- `.scrollContentBackground(.hidden)` — tells `List` / `Form` / `ScrollView` to stop drawing iOS's system-grouped-background. Exposes whatever `.background(...)` is behind it.
- `.background(Color.pebblesBackground)` — the Pebbles background that now shows through behind the list / form.
- `.toolbarBackground(Color.pebblesBackground, for: .navigationBar)` + `.toolbarBackground(.visible, for: .navigationBar)` — nav bar adopts Pebbles background. The `.visible` call is necessary because the default visibility on iOS 16+ is `.automatic`, which becomes `.hidden` when the content underneath doesn't scroll behind the bar.
- Same pair for `.tabBar` — tab bar adopts Pebbles background.

Note on placement: the modifier must be applied **inside** a `NavigationStack` for the `.toolbarBackground(_, for: .navigationBar)` modifier to attach to the stack's nav bar. Applied outside, it would silently no-op on the nav bar. For views without a `NavigationStack` (e.g. `AuthView`), the toolbar modifiers are inert, and the tint/foreground/background parts still work.

### 4. Application points

Apply `.pebblesScreen()` once on each of these views, inside the `NavigationStack` when one exists. One line per screen.

Inventory of top-level views and sheets in `apps/ios/Pebbles/Features/`:

| View | Has `NavigationStack`? | Where to apply `.pebblesScreen()` |
|---|---|---|
| `AuthView` | no | on the view body's outermost container |
| `MainTabView` | no (TabView, not nav) | on the `TabView` — the tab-bar modifier needs this |
| `PathView` | yes | on the content inside the `NavigationStack` |
| `ProfileView` | yes | on the content inside the `NavigationStack` |
| `CollectionsListView` | inherits parent's nav | on the `List` body |
| `GlyphsListView` | inherits parent's nav | on the `List` body |
| `SoulsListView` | inherits parent's nav | on the `List` body |
| `CollectionDetailView` | inherits parent's nav | on the content body |
| `SoulDetailView` | inherits parent's nav | on the content body |
| `CreatePebbleSheet` | yes | on the content inside its `NavigationStack` |
| `EditPebbleSheet` | yes | on the content inside its `NavigationStack` |
| `PebbleDetailSheet` | yes | on the content inside its `NavigationStack` |
| `CreateCollectionSheet` | yes | on the content inside its `NavigationStack` |
| `EditCollectionSheet` | yes | on the content inside its `NavigationStack` |
| `CreateSoulSheet` | yes | on the content inside its `NavigationStack` |
| `EditSoulSheet` | yes | on the content inside its `NavigationStack` |
| `BounceExplainerSheet` | yes | on the content inside its `NavigationStack` |
| `KarmaExplainerSheet` | yes | on the content inside its `NavigationStack` |
| `LegalDocumentSheet` | yes | on the content inside its `NavigationStack` |

That's ~20 call sites. Each is a single-line addition. No other logic changes.

Exact placement within each file is determined during implementation by reading each file and finding the outermost content inside the `NavigationStack` (or outermost view for non-nav views).

### 5. File layout

New top-level folder `apps/ios/Pebbles/Theme/` holds cross-cutting design-system files:

```
apps/ios/Pebbles/Theme/
  Color+Pebbles.swift
  PebblesScreen.swift
```

The `Theme/` folder sits alongside `Features/`, `Services/`, `Resources/` — matching the existing organisation where cross-cutting concerns live at the top level. `project.yml` has `sources: - path: Pebbles`, so new files under `Pebbles/` are auto-included; no project.yml edits required, but `xcodegen generate` must be re-run for the `.xcodeproj` to see them.

## Verification

- `xcodegen generate` succeeds.
- `npm run build --workspace=@pbbls/ios` succeeds.
- `npm run lint --workspace=@pbbls/ios` reports zero new violations.
- Install the freshly-built `.app` to the booted simulator via `xcrun simctl install` (do not rely on whatever is already installed), then launch it.
- Light mode:
  - Tab bar: Pebbles Background, Pebbles accent on the selected tab label+icon.
  - `PathView`: "Record a pebble" button tinted Pebbles accent, list rows on Pebbles Background card area.
  - `ProfileView`: "Collections" / "Souls" / "Glyphs" chevrons tinted Pebbles accent, section backgrounds Pebbles.
  - Any nav bar with a title renders Pebbles Background with Pebbles Foreground title.
  - `ConsentCheckbox` on `AuthView`: checkmark Pebbles accent when ticked, underlined link Pebbles accent.
- Dark mode (`⇧⌘A` in simulator): same checks, with the dark hex values of each token.
- No iOS blue remains on any Pebbles surface after the change.

## Known risks

- **`foregroundStyle` cascade**: SwiftUI `Text` inherits `.foregroundStyle` via environment, but `List` rows sometimes apply their own styling that wins. If a row's body text remains iOS default grey-black, we accept it as cell-internal styling and do not chase per-row fixes in this issue (see non-goals).
- **Sheet inventory drift**: the 20-view list is taken from the current state of `apps/ios/Pebbles/Features/`. If new sheets land between plan and implementation, they must also get `.pebblesScreen()`. Mitigation: a grep for `NavigationStack` during implementation confirms coverage.
- **Dark-mode contrast**: the dark hex values were computed, not perceptually tuned. If a reviewer flags a specific token as illegible, we tune that token in a follow-up rather than redoing the palette wholesale.

## Prior attempt

See `2026-04-17-ios-color-system-design.md` for the rejected "override Apple's defaults at the root" approach and `2026-04-17-ios-color-system.md` for its execution plan. Both documents were deleted along with the `feat/270-ios-color-system` branch; the tip commit `b0b0ba1` remains reachable via reflog for ~90 days if ever needed. The rationale for abandoning that approach is summarised in the Context section above.

## Open questions

None. Ready to plan.
