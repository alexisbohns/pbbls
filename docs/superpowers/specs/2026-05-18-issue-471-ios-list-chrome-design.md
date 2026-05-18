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

Every list in scope renders as a stack of bordered "card" groups, one per `Section`:

- Border: 1pt continuous solid `Color.system.muted`
- Corner radius: `Spacing.lg` (17pt) on the outer corners of each section group
- Outer scroll background: transparent
- No system row separators — section borders provide all dividers
- Section headers: `Color.system.secondary` in `.pebblesFont(.cardHeading)` (SF Compact Rounded 15 semibold, uppercase, 10% tracking) — matches Profile card titles
- Outer horizontal margin from screen edges: `Spacing.lg` (17pt) so cards have breathing room

## Approach

Two reusable pieces in `apps/ios/Pebbles/Theme/`, applied at each call site, plus one structural change to the affected screens.

### Structural change: `Form` → `List`

`Form` is hardcoded to `.formStyle(.grouped)` chrome on iOS — it ignores `.listStyle(.plain)` and keeps a `UITableViewCell`-level clip mask that crops the row background to the system's ~10pt section radius, cutting our 17pt corner arcs. The six `Form`-based screens are converted to `List` so `.plain` style takes effect and the clip mask is removed.

`Form`'s implicit conveniences that needed explicit replacement on `List`:
- `Picker` defaults to menu style in `Form` and to navigation-push style in `List`. Two implicit pickers in `PebbleFormView` (Domain, Collection) gain explicit `.pickerStyle(.menu)`.
- Other Pickers in scope already have explicit styles (`.pickerStyle(.segmented)` in `CreateCollectionSheet` / `EditCollectionSheet`), so they need no change.

### Theme primitive: `PebblesList.swift`

A single file that introduces three small, related helpers.

**`View.pebblesList()`** — applied to the `List`:

```swift
extension View {
    func pebblesList() -> some View {
        modifier(PebblesListModifier())
    }
}

private struct PebblesListModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .listRowSeparatorTint(Color.system.muted)
            .listSectionSpacing(Spacing.lg)
            .padding(.horizontal, Spacing.lg)
    }
}
```

- `.listStyle(.plain)` — disables `.insetGrouped` chrome and its row-background clip.
- `.scrollContentBackground(.hidden)` — removes the native scroll background.
- `.listSectionSpacing(Spacing.lg)` — consistent vertical gap between sections matching the corner radius.
- `.padding(.horizontal, Spacing.lg)` — outer breathing room from screen edges, since `.plain` style has no built-in horizontal inset.
- `.listRowSeparatorTint(...)` — vestigial after switching to `.listRowSeparator(.hidden)` per row but harmless.

**`View.pebblesListRow(position:)`** — applied to each row inside a Section:

```swift
enum PebblesListRowPosition {
    case only
    case top
    case middle
    case bottom
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
            .listRowBackground(borderBackground)
            .listRowSeparator(.hidden)
    }

    @ViewBuilder
    private var borderBackground: some View {
        let radius = Spacing.lg
        switch position {
        case .only:
            RoundedRectangle(cornerRadius: radius)
                .stroke(Color.system.muted, lineWidth: 1)
        case .top:
            UnevenRoundedRectangle(
                cornerRadii: RectangleCornerRadii(
                    topLeading: radius, bottomLeading: 0,
                    bottomTrailing: 0, topTrailing: radius
                )
            )
            .stroke(Color.system.muted, lineWidth: 1)
        case .middle:
            Rectangle()
                .stroke(Color.system.muted, lineWidth: 1)
        case .bottom:
            UnevenRoundedRectangle(
                cornerRadii: RectangleCornerRadii(
                    topLeading: 0, bottomLeading: radius,
                    bottomTrailing: radius, topTrailing: 0
                )
            )
            .stroke(Color.system.muted, lineWidth: 1)
        }
    }
}
```

Three points of detail in the row modifier:

1. **`.listRowBackground` (not `.overlay`).** The shape sits at the row's full background frame, edge-to-edge of the list, rather than wrapping the row's content box. This is what makes adjacent rows' edges line up to form a continuous section card.
2. **`.stroke` (not `.strokeBorder`).** With `.stroke`, the stroke is centered on the path. When two adjacent rows draw their shared horizontal edge (top row's bottom + middle row's top), the strokes overlap exactly in the same 1pt of pixels — visually a single line, not a 2pt stack. `.strokeBorder` would inset the stroke entirely inside each shape, causing the two strokes to abut at the boundary and double up.
3. **`.listRowSeparator(.hidden)`.** Without this, the system's native row separator would render on top of the (now identical) interior border line, producing a 2-3pt thick rule at every row boundary. Hiding it leaves only the section border's contribution.

Each row's shape is closed (full perimeter), so `.top` and `.bottom` rows draw their inner horizontal edge as well. That edge sits at the row boundary, overlapping precisely with the adjacent row's matching edge, so it reads as one 1pt line — the section's internal divider.

Border lives on a per-row background (not a section background) because SwiftUI does not expose section bounds to a custom modifier on `Section`.

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

### Borderless rows

Rows that should render edge-to-edge with no border (the `PebbleRenderView` row in `PebbleFormView`, the glyph banner row in `SettingsSheet`) don't use `.pebblesListRow`. They keep `.listRowBackground(Color.clear)` directly and must add `.listRowSeparator(.hidden)` themselves — otherwise iOS draws a default divider above and below them.

## Per-file changes

For every list in scope: change `Form { … }` → `List { … }` where applicable; add `.pebblesList()` on the `List`; replace existing scattered `listRowBackground(Color.clear)` / `listRowSeparatorTint(...)` calls with `.pebblesListRow(position:)` per row; switch labeled `Section("Title")` headers to `Section { … } header: { Text("Title").pebblesSectionHeader() }`.

### `Features/Profile/Lists/CollectionsListView.swift`

Already uses `List`. Drop the existing `.listRowBackground(Color.clear)` and `.listRowSeparatorTint(...)` calls. Add `.pebblesList()` to the `List`. The single `ForEach(items)` renders one section's worth of rows; use `pebblesRowPosition(index:count:)` against `items.count`.

### `Features/Profile/Views/CollectionDetailView.swift`

Already uses `List`. Remove `.listStyle(.insetGrouped)`; add `.pebblesList()`. The header `Section { HStack { mode badge, count } }` is single-row → `.pebblesListRow(position: .only)`. Each per-month `Section(header: Text(month))` becomes `Section { … } header: { Text(month).pebblesSectionHeader() }`, with rows using `pebblesRowPosition(index:count:)` over `group.value`.

### `Features/Profile/Views/SoulDetailView.swift`

Already uses `List`. Add `.pebblesList()` to the `List`. Single `ForEach(pebbles)` over one implicit section → row position via `pebblesRowPosition(index:count:)`.

### `Features/Profile/Sheets/SettingsSheet.swift`

Change `Form` → `List`. Add `.pebblesList()`. `headerSection` (single glyph button row) stays borderless: keep `.listRowBackground(Color.clear)` and add `.listRowSeparator(.hidden)`. `informationsSection`, `passwordSection` / `providersSection`, `legalSection` get position-aware `.pebblesListRow` per row, with labeled headers converted to the `header:` form using `pebblesSectionHeader()`. Error section: `.pebblesListRow(position: .only)`.

### `Features/Profile/Sheets/CreateSoulSheet.swift` and `EditSoulSheet.swift`

Change `Form` → `List`. Add `.pebblesList()`. Name section (single `TextField`): `.pebblesListRow(position: .only)`. Glyph section: header via `pebblesSectionHeader()`, row → `.pebblesListRow(position: .only)`. Error section: `.pebblesListRow(position: .only)`.

### `Features/Profile/Sheets/CreateCollectionSheet.swift` and `EditCollectionSheet.swift`

Change `Form` → `List`. Add `.pebblesList()`. Name section: header + `.pebblesListRow(position: .only)`. Mode section (segmented `Picker`): header + `.pebblesListRow(position: .only)`. Error section: `.pebblesListRow(position: .only)`.

### `Features/Path/PebbleFormView.swift`

Change `Form` → `List`. Add `.pebblesList()`; drop the existing top-level `.listRowSeparatorTint(...)`.

- **Render section**: the `PebbleRenderView` row keeps `.listRowInsets(EdgeInsets()) + .listRowBackground(Color.clear)` and gets `.listRowSeparator(.hidden)`. No `.pebblesListRow` — the edge-to-edge artwork is intentional and stays borderless.
- **When / Name / Description section** (3 rows): `.pebblesListRow(position:)` with `.top`, `.middle`, `.bottom`.
- **Mood section** (3 rows: Emotion / Domain / Valence): same `.top`/`.middle`/`.bottom` with header via `pebblesSectionHeader()`. Domain `Picker` gains `.pickerStyle(.menu)`.
- **Glyph section** (1 row): `.only` + header.
- **Souls section** (1 row, `SelectedSoulsRow`): `.only` + header. Keep the custom `.listRowInsets` already set on that row.
- **Optional section** (1 row, Collection picker): `.only` + header. Collection `Picker` gains `.pickerStyle(.menu)`.
- **Photo section** (1 row, variant-dependent content): `.only` + header.
- **Error section** (1 row, conditional): `.only`, no header.

Remove every now-redundant standalone `.listRowBackground(Color.clear)` from the file.

## Out of scope

- **Lab views** (`LabView`, `LogListView`): not user-facing for the harmonization pass. Skipped per scope decision; can adopt later.
- **`PebbleRow.swift`**: the shared row component used inside several lists. Its visual chrome is unchanged; the position-aware decoration is applied at each call site, not inside `PebbleRow`.
- **Sub-list pickers**: SwiftUI's menu-style `Picker` opens its own popover. Those popovers are not styled by this pass.
- **`ContentUnavailableView`, `ProgressView`, error-only states**: not lists, not changed.

## Implementation notes

The simple-sounding "border per Section" turned out to be the second-hardest part of this change. Three pivots between the first commit and the working state:

1. **`.overlay` per row → `.listRowBackground`.** Overlays wrap content, not the row's full frame, so each row rendered as an isolated pill instead of contributing to a continuous card.
2. **`.strokeBorder` → `.stroke` + `.listRowSeparator(.hidden)`.** `.strokeBorder` insets the stroke inside the shape, so adjacent rows' boundary edges abutted as a 2pt-thick line. Combined with the system separator drawing on top, boundaries were a 3pt rule. `.stroke` overlaps neighboring strokes exactly, and hiding the system separator eliminates the third line.
3. **`Form` → `List` + `.listStyle(.plain)`.** `Form` ignores `.listStyle(.plain)` and applies a `UITableViewCell`-level clip mask to row backgrounds at ~10pt, cropping our 17pt corner arcs. Converting to `List` removes the clip.

The PR commit history documents each step, including the failed attempts. Anyone touching `PebblesList.swift` later should resist the urge to "clean up" toward `.overlay` or `.strokeBorder` — both were tried and produce the visual regressions described above.

## Files touched

New:
- `apps/ios/Pebbles/Theme/PebblesList.swift`

Modified:
- `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` (Form → List)
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` (Form → List)
- `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift` (Form → List)
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift` (Form → List)
- `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift` (Form → List)
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` (Form → List, + explicit pickerStyle on Domain and Collection)

No `project.yml` change needed — `xcodegen` pulls every `.swift` under `apps/ios/Pebbles/`. After adding the new file, run `npm run generate --workspace=@pbbls/ios` so the local `.xcodeproj` picks it up.
