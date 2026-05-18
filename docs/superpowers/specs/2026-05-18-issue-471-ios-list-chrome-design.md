# iOS list chrome harmonization (issue #471)

Resolves https://github.com/Bohns/pbbls/issues/471

## Problem

The iOS app's native `List` and `Form` instances still use the system grouped background and divider color: white-on-white in light mode (the lists effectively disappear), system grey in dark mode (looks unstyled), and stock grey separators in both. Profile cards (Stats, Collections, Lab) already use a harmonized chrome — `Color.system.muted` border, `Spacing.lg` (17pt) corner radius, clear fill — and lists should match.

Today the treatment is inconsistent across files:

- `CollectionsListView` — clear rows + tinted separators, but no border
- `PebbleFormView` — clear rows + tinted separators on every section, but no border
- `SettingsSheet` — only the glyph header row has a clear background; everything else is default
- `CollectionDetailView`, `SoulDetailView`, `CreateSoulSheet`, `EditSoulSheet`, `CreateCollectionSheet`, `EditCollectionSheet` — fully default

## Goal

Every `List`/`Form` in scope renders as a stack of bordered "card" groups, one per `Section`:

- Border: 1pt continuous solid `Color.system.muted`
- Corner radius: `Spacing.lg` (17pt) on the outer corners of each section group
- Row separators inside a section: `Color.system.muted`
- Outer scroll background: transparent
- Section headers: `Color.system.secondary` in `.pebblesFont(.cardHeading)` (SF Compact Rounded 15 semibold, uppercase, 10% tracking) — matches Profile card titles

## Approach

Two reusable pieces in `apps/ios/Pebbles/Theme/`, applied at each call site. Native `List`/`Form` semantics (swipe actions, keyboard avoidance, Form row layout) are preserved.

### Theme primitive: `PebblesList.swift`

A single file that introduces three small, related helpers.

**`View.pebblesList()`** — applied to a `List` or `Form`:

```swift
extension View {
    func pebblesList() -> some View {
        modifier(PebblesListModifier())
    }
}

private struct PebblesListModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .listRowSeparatorTint(Color.system.muted)
            .listSectionSpacing(Spacing.lg)
    }
}
```

- `.scrollContentBackground(.hidden)` — removes the native grouped background (iOS 16+ API; deployment target is iOS 17).
- `.listRowSeparatorTint(Color.system.muted)` — recolors row separators so they read as inner dividers of the bordered group.
- `.listSectionSpacing(Spacing.lg)` — predictable vertical gap between sections matching the corner radius.

**`View.pebblesListRow(position:)`** — applied to each row inside a Section:

```swift
enum PebblesListRowPosition {
    case only       // single-row section (or only row in group)
    case top        // first row of multi-row section
    case middle     // middle row
    case bottom     // last row of multi-row section
}

extension View {
    func pebblesListRow(position: PebblesListRowPosition = .only) -> some View {
        modifier(PebblesListRowModifier(position: position))
    }
}

private struct PebblesListRowModifier: ViewModifier {
    let position: PebblesListRowPosition

    func body(content: Content) -> some View {
        content
            .listRowBackground(Color.clear)
            .listRowSeparatorTint(Color.system.muted)
            .overlay(borderOverlay)
    }

    private var borderOverlay: some View {
        let radius = Spacing.lg
        let corners: RectangleCornerRadii = switch position {
        case .only:   .init(topLeading: radius, bottomLeading: radius, bottomTrailing: radius, topTrailing: radius)
        case .top:    .init(topLeading: radius, bottomLeading: 0,      bottomTrailing: 0,      topTrailing: radius)
        case .middle: .init(topLeading: 0,      bottomLeading: 0,      bottomTrailing: 0,      topTrailing: 0)
        case .bottom: .init(topLeading: 0,      bottomLeading: radius, bottomTrailing: radius, topTrailing: 0)
        }
        return UnevenRoundedRectangle(cornerRadii: corners)
            .strokeBorder(Color.system.muted, lineWidth: 1)
            .allowsHitTesting(false)
    }
}
```

Each row draws its own portion of the section's border. Adjacent rows overlap on the shared horizontal edge — visually the same as a single rectangle because both segments are the same color and width. The inner `listRowSeparatorTint` already paints the divider in `Color.system.muted`, so the row separator becomes the inner horizontal line of the card.

Border lives on a per-row overlay (not a section background) because SwiftUI does not expose section bounds to a custom modifier on `Section`.

**`Text.pebblesSectionHeader()`** — applied to a Section's header text:

```swift
extension Text {
    func pebblesSectionHeader() -> some View {
        self
            .pebblesFont(.cardHeading)
            .foregroundStyle(Color.system.secondary)
    }
}
```

`.cardHeading` already includes uppercase + 10% tracking, so no extra `.textCase` is needed.

### Per-row position helper

For Sections that render rows via `ForEach`, a small free function avoids index-juggling at every call site:

```swift
func pebblesRowPosition(index: Int, count: Int) -> PebblesListRowPosition {
    if count <= 1 { return .only }
    if index == 0 { return .top }
    if index == count - 1 { return .bottom }
    return .middle
}
```

Used as:

```swift
ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
    Row(item: item)
        .pebblesListRow(position: pebblesRowPosition(index: index, count: items.count))
}
```

## Per-file changes

For every file below: add `.pebblesList()` on the `List`/`Form`; replace existing scattered `listRowBackground(Color.clear)` / `listRowSeparatorTint(...)` calls with `.pebblesListRow(position:)` per row; switch labeled `Section("Title")` headers to `Section { … } header: { Text("Title").pebblesSectionHeader() }`.

### `Features/Profile/Lists/CollectionsListView.swift`

- Drop the existing `.listRowBackground(Color.clear)` and `.listRowSeparatorTint(...)` calls.
- Add `.pebblesList()` to the `List`.
- The single `ForEach(items)` renders one section's worth of rows; use `pebblesRowPosition(index:count:)` against `items.count`.

### `Features/Profile/Views/CollectionDetailView.swift`

- Remove `.listStyle(.insetGrouped)`; add `.pebblesList()`.
- The header `Section { HStack { mode badge, count } }` is single-row → `.pebblesListRow(position: .only)`.
- Each per-month `Section(header: Text(month))` becomes `Section { … } header: { Text(month).pebblesSectionHeader() }`, with rows using `pebblesRowPosition(index:count:)` over `group.value`.

### `Features/Profile/Views/SoulDetailView.swift`

- Add `.pebblesList()` to the `List`.
- Single `ForEach(pebbles)` over one implicit section → row position via `pebblesRowPosition(index:count:)`.

### `Features/Profile/Sheets/SettingsSheet.swift`

- Add `.pebblesList()` on the `Form`.
- `headerSection` (single glyph button row) → `.pebblesListRow(position: .only)` (already has clear background; replace with the modifier).
- `informationsSection`, `passwordSection` / `providersSection`, `legalSection`: enumerate rows inside each and apply position-aware `.pebblesListRow`. Convert labeled `Section("Title")` headers to the `header:` form with `pebblesSectionHeader()`.
- Error section (single row) → `.pebblesListRow(position: .only)`.

### `Features/Profile/Sheets/CreateSoulSheet.swift` and `EditSoulSheet.swift`

- Add `.pebblesList()` on the `Form`.
- Name section (single `TextField`) → `.pebblesListRow(position: .only)`.
- Glyph section (`GlyphRow`) → header text via `pebblesSectionHeader()`, row → `.pebblesListRow(position: .only)`.
- Error section → `.pebblesListRow(position: .only)`.

### `Features/Profile/Sheets/CreateCollectionSheet.swift` and `EditCollectionSheet.swift`

- Add `.pebblesList()` on the `Form`.
- Name section → header + `.pebblesListRow(position: .only)`.
- Mode section (segmented `Picker`) → header + `.pebblesListRow(position: .only)`.
- Error section → `.pebblesListRow(position: .only)`.

### `Features/Path/PebbleFormView.swift`

- Add `.pebblesList()` on the `Form`; drop the existing top-level `.listRowSeparatorTint(...)`.
- **Render section**: the `PebbleRenderView` row keeps its current `.listRowInsets(EdgeInsets()) + .listRowBackground(Color.clear)` and gets **no** border. Edge-to-edge artwork is intentional.
- **When / Name / Description section** (3 rows): apply `.pebblesListRow(position:)` with `.top`, `.middle`, `.bottom`.
- **Mood section** (3 rows: Emotion / Domain / Valence): same `.top`/`.middle`/`.bottom`, with header via `pebblesSectionHeader()`.
- **Glyph section** (1 row): `.only` + header.
- **Souls section** (1 row, `SelectedSoulsRow`): `.only` + header. Keep the custom `.listRowInsets` already set on that row.
- **Optional section** (1 row, Collection picker): `.only` + header.
- **Photo section** (1 row, variant-dependent content): `.only` + header.
- **Error section** (1 row, conditional): `.only`, no header.

Remove every now-redundant standalone `.listRowBackground(Color.clear)` from the file.

## Out of scope

- **Lab views** (`LabView`, `LogListView`): not user-facing for the harmonization pass. Skipped per scope decision; can adopt later.
- **`PebbleRow.swift`**: the shared row component used inside several lists. Its visual chrome is unchanged; the position-aware decoration is applied at each call site, not inside `PebbleRow`.
- **Sub-list pickers**: SwiftUI's disclosure-style `Picker` pushes its own native list. Those inner lists are not styled by this pass; revisit if they look visually broken alongside the new chrome.
- **`ContentUnavailableView`, `ProgressView`, error-only states**: not lists, not changed.
- **Row insets, section header insets**: kept at SwiftUI defaults for `insetGrouped` Form. We do not override horizontal margins.

## Risks and validation

- **Press affordance on `NavigationLink` rows.** With `.listRowBackground(Color.clear)`, the system's press-tint background is replaced by transparent. The chevron + label color change may still convey press state; if it feels dead in the simulator, switch the background to a near-clear pressable fill (e.g. `Color.system.muted.opacity(0.0001)`) or rely on `.contentShape`. Validate during implementation, do not preempt.
- **First-row top separator.** Some Form styles draw a separator between the section header and the first row. Our top border overlay sits just inside it. If this reads as a doubled line, hide it on the top row with `.listRowSeparator(.hidden, edges: .top)`.
- **Section header spacing.** Switching `Section("Title")` to an explicit `Text` header builder can change the default header insets and bottom padding. If the spacing reads off, apply `.padding(.top, Spacing.sm)` or `.listSectionSpacing(.compact)` to that section. Adjust during implementation, not in advance.
- **Adjacent-row border overlap.** Two adjacent rows both draw their shared horizontal edge in the same `Color.system.muted` at 1pt. The two segments visually merge — but on non-integer points (Retina vs. 3x devices) there is a theoretical risk of a half-pixel-darker hairline. If visible, drop the bottom edge of the top row by using a custom `Shape` instead of `UnevenRoundedRectangle.strokeBorder`. Park as a follow-up only if reproducible.

## Files touched

New:
- `apps/ios/Pebbles/Theme/PebblesList.swift`

Modified:
- `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`

No `project.yml` change needed — `xcodegen` pulls every `.swift` under `apps/ios/Pebbles/`. After adding the new file, run `npm run generate --workspace=@pbbls/ios` so the local `.xcodeproj` picks it up.
