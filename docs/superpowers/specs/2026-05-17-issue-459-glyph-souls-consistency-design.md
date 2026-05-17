# Issue #459 — Glyph and Souls consistency on iOS

**Status:** Design
**Date:** 2026-05-17
**Issue:** [#459](https://github.com/) (labels: `ios`, `quality`, `ui`; milestone: M32 · iOS Quality)
**Scope:** Single PR
**Branch (proposed):** `quality/459-glyph-souls-consistency`

## Problem

After the recent iOS color-system refactor (PR #458), glyph and soul rendering remains visually inconsistent across six surfaces:

- Glyphs list (`GlyphsListView`)
- Glyph picker (`GlyphPickerSheet`)
- Souls list (`SoulsListView`)
- Soul picker (`SoulPickerSheet`)
- Profile glyph (`ProfileBanner`)
- Settings glyph (`SettingsSheet`)

Today each surface composes its own chrome around the bare `GlyphThumbnail` canvas, so corner radii, borders, and state colors drift between screens. Souls additionally have two distinct components (`SoulGridCell` for the list, `SoulSelectableCell` for the picker) that share no rendering code.

## Goals

1. One `Glyph` component renders the glyph in every surface, with all chrome (border, dashed/continuous, plus/scribble overlays, state colors) owned by the component.
2. One `SoulItem` component renders souls in both the list and the picker.
3. The `SoulPickerSheet` selection rule is explicit and predictable (see §3).
4. Two prerequisites land in the same PR because the new components depend on them: a typography token set (§1) and a spacing scale (§4).

## Non-goals

- "Frequently linked" section in the soul picker (deferred per issue).
- Wiring real per-soul ripple/pebble counts into the data layer (UI accepts a nullable `count`, but it stays `nil` until a follow-up PR updates the souls query).
- Sweeping the existing codebase to migrate every `.padding(16)` to the spacing scale. The scale lands and is used in the touched surfaces only.
- `Ysabeau-Bold.ttf` registration. `title.emphasized` and `buttonLabel.emphasized` are not consumed by #459 and are deferred.

## §1 — Typography foundation

**Location:** `Pebbles/Theme/Font+Pebbles.swift` (extended)
**New resources:** `Pebbles/Resources/SF-Compact-Rounded-{Medium,Semibold,Bold}.ttf`, registered in `Info.plist` under `UIAppFonts`.

### Font helpers (private)

```swift
private static func sfProRounded(_ size: CGFloat, _ weight: UIFont.Weight) -> Font
private static func sfCompactRounded(_ size: CGFloat, _ weight: UIFont.Weight) -> Font
```

Both return `Font(UIFont(descriptor:size:))`, mirroring the existing `ysabeauSemibold(_:)` pattern.

### Tokens

| token | family | size | weight | tracking (pt) | uppercase | line-height |
|---|---|---|---|---|---|---|
| `body` | SF Pro Rounded | 17 | regular | 0.34 (2%) | no | auto |
| `bodyEmphasized` | SF Pro Rounded | 17 | semibold | 0.34 | no | auto |
| `subhead` | SF Pro Rounded | 15 | regular | 0.30 | no | auto |
| `subheadEmphasized` | SF Pro Rounded | 15 | semibold | 0.30 | no | auto |
| `headline` | SF Pro Rounded | 17 | semibold | 0.34 | no | auto |
| `headlineEmphasized` | SF Pro Rounded | 17 | bold | 0.34 | no | auto |
| `callout` | SF Pro Rounded | 16 | medium | 0.32 | no | auto |
| `calloutEmphasized` | SF Pro Rounded | 16 | semibold | 0.32 | no | auto |
| `meta` | SF Compact Rounded | 12 | medium | 1.20 (10%) | yes | 100% (×1.0) |
| `metaEmphasized` | SF Compact Rounded | 12 | bold | 1.20 | yes | 100% |
| `cardHeading` | SF Compact Rounded | 15 | semibold | 1.50 | yes | 100% |
| `cardHeadingEmphasized` | SF Compact Rounded | 15 | bold | 1.50 | yes | 100% |
| `title` | Ysabeau SemiBold | 28 | semibold | −0.56 (−2%) | no | auto |
| `buttonLabel` | Ysabeau SemiBold | 17 | semibold | 0.34 | no | 100% |

Two tokens are intentionally **not** emphasized:
- `title.emphasized` requires Ysabeau Bold (not bundled, deferred).
- `buttonLabel.emphasized` requires Ysabeau Bold (not bundled, deferred).

Neither is consumed by #459, so this PR ships without them.

### Public API

Tokens are exposed as a `ViewModifier` so `.font`, `.tracking`, `.textCase` apply together at the call site:

```swift
enum PebblesFont {
    case body, bodyEmphasized
    case subhead, subheadEmphasized
    case headline, headlineEmphasized
    case callout, calloutEmphasized
    case meta, metaEmphasized
    case cardHeading, cardHeadingEmphasized
    case title
    case buttonLabel
}

extension View {
    func pebblesFont(_ token: PebblesFont) -> some View
}
```

Call site: `Text(name).pebblesFont(.subheadEmphasized)`.

### Risk / fallback

If the Apple-distributed `SF-Compact-Rounded` TTFs cannot be redistributed under license, fall back for `meta` and `cardHeading` to `Font.system(size:, weight:, design: .rounded)` and document the deviation inline in `Font+Pebbles.swift`. All other tokens are unaffected.

## §2 — `Glyph` view component

**Location:** `Pebbles/Features/Glyph/Views/Glyph.swift`

### Public API

```swift
struct Glyph: View {
    enum Case {
        case profile      // chrome: clear bg, 2XL r, continuous 1pt system.muted, glyph in accent.primary
        case carve        // chrome: clear bg, 2XL r, dashed (10,10) 2pt system.muted, sf.scribble in system.secondary
        case create       // chrome: clear bg, 2XL r, dashed (10,10) 2pt system.muted, sf.plus in system.muted
        case selected     // chrome: clear bg, 2XL r, continuous 2pt accent.primary, glyph in accent.primary
        case unselected   // chrome: clear bg, 2XL r, continuous 1pt system.muted, glyph in system.muted
        case `default`    // chrome: clear bg, 2XL r, continuous 1pt system.muted, glyph in system.secondary
    }

    let `case`: Case
    let strokes: [GlyphStroke]?   // ignored for .carve and .create
    var side: CGFloat = 96
}
```

### Internal shape

A `ZStack` containing:

1. Border, `RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous)`:
   - `.selected` → `.stroke(.accent.primary, lineWidth: 2)`
   - `.carve` / `.create` → `.strokeBorder(.system.muted, style: StrokeStyle(lineWidth: 2, dash: [10, 10]))`
   - others → `.stroke(.system.muted, lineWidth: 1)`
2. Content, sized to `side`, centered:
   - `.carve` → `Image(systemName: "scribble")` tinted `system.secondary`
   - `.create` → `Image(systemName: "plus")` tinted `system.muted`
   - others → `GlyphThumbnail(strokes: strokes ?? [], side: side, strokeColor: <case color>)`

SF Symbol size scales as `.system(size: max(side * 0.4, 18))` — `side * 0.4` keeps the symbol proportional at large sizes (200pt → 80pt symbol), and the 18pt floor guards legibility at the smallest sizes the component ships at (32pt → 18pt symbol, not the 12.8pt the formula would otherwise give).

### `GlyphThumbnail` refactor

Today `GlyphThumbnail` owns its own chrome (8pt corner radius, secondary 8% background). Drop both so it becomes a pure stroke canvas:

```swift
struct GlyphThumbnail: View {
    let strokes: [GlyphStroke]
    var side: CGFloat = 100
    var strokeColor: Color = .primary
}
```

### Call-site migration map

| File | Today | After |
|---|---|---|
| `Features/Path/SoulPill.swift:17` | `GlyphThumbnail(side: 24, .accent.primary, bg: .clear)` | **kept as `GlyphThumbnail`** — intentionally chrome-less; `Glyph(.default)` would impose an unwanted border |
| `Features/Path/Read/PebbleMetaPill.swift:59` | `GlyphThumbnail(side: 16, foreground, bg: .clear)` | **kept as `GlyphThumbnail`** — same reason; renders inside the existing meta pill chrome |
| `Features/Path/PebbleFormView.swift:223` | `GlyphThumbnail(side: 32)` | `Glyph(case: .default, strokes:, side: 32)` |
| `Features/Profile/Sheets/SettingsSheet.swift:140` | `GlyphThumbnail(side: 120)` | `Glyph(case: .profile, strokes:, side: 120)` |
| `Features/Profile/Sheets/EditSoulSheet.swift:135` | `GlyphThumbnail(side: 32)` | `Glyph(case: .default, strokes:, side: 32)` |
| `Features/Profile/Sheets/CreateSoulSheet.swift:144` | `GlyphThumbnail(side: 32)` | `Glyph(case: .default, strokes:, side: 32)` |
| `Features/Profile/Lists/SoulGridCell.swift` | `GlyphThumbnail(side: 96)` | **file deleted** (replaced by `SoulItem`) |
| `Features/Profile/Lists/SoulSelectableCell.swift` | builds own chrome | **file deleted** (replaced by `SoulItem`) |
| `Features/Profile/Components/ProfileBanner.swift:35` | `GlyphThumbnail(side: 96)` + outer 34-radius overlay | `Glyph(case: .profile, strokes:, side: 96)`; manual overlay removed |
| `Features/Profile/Views/SoulDetailView.swift:91` | `GlyphThumbnail(side: 56)` | `Glyph(case: .default, strokes:, side: 56)` |
| `Features/Glyph/Views/GlyphThumbnail.swift` | (definition + `#Preview`) | refactored per above; `#Preview` updated to call the bare API |
| `Features/Glyph/Views/GlyphsListView.swift:118` | `GlyphThumbnail(side: 96, .accent.primary)` | `Glyph(case: .default, strokes:, side: 96)` |
| `Features/Glyph/Views/GlyphPickerSheet.swift:75` | `GlyphThumbnail(side: 96, tinted bg)` | `Glyph(case: glyph.id == currentGlyphId ? .selected : .default, strokes:, side: 96)`; the "Carve new glyph" row becomes `Glyph(case: .carve, side: 48)` inside the existing row layout |

After migration, two surfaces still call `GlyphThumbnail` directly (`SoulPill`, `PebbleMetaPill`) because both intentionally render strokes against existing chrome from their parent.

## §3 — `SoulItem` view + `SoulPickerSheet` selection rule

**Location:** `Pebbles/Features/Shared/SoulItem.swift` (shared between path and profile)

### Public API

```swift
struct SoulItem: View {
    enum Case { case selected, unselected, `default`, create }

    let `case`: Case
    let soul: SoulWithGlyph?      // nil for .create
    let count: Int?               // ripples / linked-pebbles count; nil hides the row
    var onTap: () -> Void = {}
}
```

### Layout

Vertical stack — the soul item is a grid cell, not a row.

```
VStack(spacing: Spacing.sm) {       // 10pt between glyph and text block
    Glyph(case: glyphCase, strokes: soul?.glyph.strokes, side: 96)

    VStack(spacing: Spacing.xs) {   // 3pt between name and meta row
        Text(name).pebblesFont(nameToken)

        if count != nil && `case` != .create {
            HStack(spacing: Spacing.xs) {   // 3pt between icon and count
                Image(systemName: "fossil.shell")
                Text("\(count!)").pebblesFont(.meta)
            }
        }
    }
}
```

Visual mapping (issue table):

| case | glyph case | name token | name color | icon | icon color | count token | count color |
|---|---|---|---|---|---|---|---|
| `.selected` | `.selected` | `subheadEmphasized` | `accent.primary` | `fossil.shell` | `accent.primary` | `meta` | `system.secondary` |
| `.unselected` | `.unselected` | `subhead` | `system.secondary` | `fossil.shell` | `accent.primary` | `meta` | `system.secondary` |
| `.default` | `.default` | `subhead` | `system.secondary` | `fossil.shell` | `accent.primary` | `meta` | `system.secondary` |
| `.create` | `.create` | `subhead` | `system.secondary` | — | — | — | — |

`.create` renders only the glyph + name (no count, no icon row).

### `SoulPickerSheet` rewrite

Replace the current `LazyVGrid` of `SoulSelectableCell` with a `LazyVGrid` of `SoulItem`, preceded by a `cardHeading` section header.

```swift
ScrollView {
    LazyVStack(alignment: .leading, spacing: Spacing.lg) {
        Text("All my souls").pebblesFont(.cardHeading)

        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)],
                  spacing: Spacing.lg) {
            SoulItem(case: .create, soul: nil, count: nil) { isPresentingCreate = true }
            ForEach(souls) { soul in
                SoulItem(case: itemCase(for: soul.id),
                         soul: soul,
                         count: nil) {
                    toggle(soul.id)
                }
            }
        }
    }
    .padding(Spacing.lg)
}
```

State rule, per issue:

```swift
private func itemCase(for id: UUID) -> SoulItem.Case {
    if selection.isEmpty { return .default }
    return selection.contains(id) ? .selected : .unselected
}
```

This satisfies all three rules in the issue:
- 0 selected → all `.default`
- ≥1 selected, this one selected → `.selected`
- ≥1 selected, this one not → `.unselected`

`.create` ignores state entirely.

`NewSoulTile` (the dashed `+ New soul` private tile inside `SoulPickerSheet.swift`) and `SoulSelectableCell` are both **deleted**.

### `SoulsListView` rewrite

Replace `LazyVGrid` of `SoulGridCell` with `LazyVGrid` of `SoulItem`, all `.default`. Each cell wrapped in a `NavigationLink` to `SoulDetailView` (preserves current nav). Context-menu delete affordance preserved.

```swift
LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)],
          spacing: Spacing.lg) {
    ForEach(items) { soul in
        NavigationLink { SoulDetailView(initial: soul, onChanged: { … }) } label: {
            SoulItem(case: .default, soul: soul, count: nil)
        }
        .buttonStyle(.plain)
        .contextMenu { … }
    }
}
.padding(Spacing.lg)
```

No section header on the list (single-section view).

`SoulGridCell` is **deleted**.

### `count` field

Both `SoulPickerSheet.load()` and `SoulsListView.load()` currently fetch souls with `select("id, name, glyph_id, glyphs(…)")`. They do not fetch per-soul counts.

This PR ships `SoulItem` with `count: Int?`, passes `nil` everywhere, and the icon+count row is hidden when `count == nil`. A follow-up PR wires the actual count (likely via a Supabase RPC update — `update_souls_list` or new `souls_with_counts`). Out of scope per the non-goals.

## §4 — Spacing scale

**Location:** `Pebbles/Theme/Spacing.swift`

```swift
enum Spacing {
    static let xs:  CGFloat = 3
    static let sm:  CGFloat = 10
    static let md:  CGFloat = 13
    static let lg:  CGFloat = 17    // root, == iOS body font size
    static let xl:  CGFloat = 22
    static let xxl: CGFloat = 34    // == root × 2
}
```

Rationale: iOS HIG has no canonical spacing scale; Apple defaults (`.padding()` = 16) come from a Tailwind-adjacent web heritage that doesn't fit the 17pt body baseline. This scale is rooted on 17pt, so `Spacing.lg == body font size` and `Spacing.xxl == 2 × body == 34` (the corner radius required by §2).

### Applied in this PR

- `Glyph` corner radius → `Spacing.xxl`
- `SoulItem`: outer `VStack` spacing `Spacing.sm`; inner text `VStack` spacing `Spacing.xs`; icon/count `HStack` spacing `Spacing.xs`
- `SoulPickerSheet` and `SoulsListView`: `LazyVGrid` spacing `Spacing.lg` (both axes); container padding `Spacing.lg`
- `SoulPickerSheet`: outer `LazyVStack` spacing `Spacing.lg` between section header and grid

### Out of scope

Sweeping existing `.padding(16)` / `spacing: 8` / `spacing: 12` call sites to use the scale. Follow-up.

## Migration & PR plan

Single PR, branch `quality/459-glyph-souls-consistency`. Suggested commit order so the diff reviews cleanly:

1. `feat(ios): add spacing scale` — `Theme/Spacing.swift` only.
2. `feat(ios): introduce typography tokens` — `Theme/Font+Pebbles.swift` extension, `Resources/SF-Compact-Rounded-*.ttf`, `Info.plist` registration, `PebblesFont` enum + `pebblesFont(_:)` modifier.
3. `quality(ios): strip GlyphThumbnail chrome` — refactor only; no call-site changes (all current sites that rely on the default bg will look slightly different at this commit, fixed in commits 4–5).
4. `feat(ios): introduce Glyph component` — `Features/Glyph/Views/Glyph.swift`; migrate the eight `GlyphThumbnail` call sites listed in §2 that wrap with chrome.
5. `quality(ios): unify soul rendering with SoulItem` — `Features/Shared/SoulItem.swift`; rewrite `SoulPickerSheet` and `SoulsListView`; delete `SoulSelectableCell`, `SoulGridCell`, and `NewSoulTile`.

Final PR title: `quality(ios): unify glyph and soul rendering`.
Labels (inherited from issue, `bug` → `fix` mapping not applicable here): `quality`, `ios`, `ui`.
Milestone: `M32 · iOS Quality`.

## Testing & verification

This is a quality/visual refactor; no new business logic. Verification:

- Per-surface visual check in both light and dark mode against the issue screenshots:
  - Glyph list (default state)
  - Glyph picker (selected vs others when a glyph is currently selected; default when none is)
  - Souls list (all default)
  - Soul picker — no soul selected (all `.default`), one selected (one `.selected`, others `.unselected`)
  - Profile glyph (`.profile`)
  - Settings glyph (`.profile`, size 120)
  - "Carve new glyph" row in picker, "Carve" toolbar in `GlyphsListView`
  - `+ Create soul` tile in soul picker
- `npm run lint --workspace=apps/ios` (or `xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build` for a type-check pass).
- `Localizable.xcstrings` audit — confirm "All my souls" and any new strings have `en` + `fr` values, none in `New` / `Stale`.

No unit tests (V1 conventions, no test target wired for views).

## Risks

- **SF Compact Rounded license / availability.** If we can't redistribute the TTF, fall back to `Font.system(design: .rounded)` for `meta` / `cardHeading` (see §1 risk note). All other tokens unaffected.
- **`GlyphThumbnail` chrome strip happens before all sites adopt `Glyph`.** Mitigated by commit ordering — sites that rely on the default grey background are migrated in the same PR. A reviewer scrubbing commit-by-commit will see a brief regression at commit 3 that's resolved by commit 5.
- **Soul item count is `nil` in this PR.** Visually the icon+count row is absent until a follow-up data-layer PR. Acceptable per non-goals; flagged here so reviewers don't expect the count.

## Open items (none blocking)

- The "Carve new glyph" row in `GlyphPickerSheet` uses `Glyph(case: .carve, side: 48)` — the issue's spec table fixes the chrome for `.carve` but not the size at this surface. 48pt matches the current visual; revisit if it reads too small against the row layout.
