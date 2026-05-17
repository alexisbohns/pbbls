# Issue #465 — Profile page token refacto (iOS)

**Scope**: re-tokenize every Profile-screen component against the latest design — typography, color, spacing, corner radii — and add the small set of new design tokens the spec introduces. Wire one frustrating gap (tapping anywhere on a collection tile should open / create the collection).

**Out of scope**: settings sheet, list views (Souls, Glyphs, Collections), detail views, any data-model change. No RPC work — the only data addition (`pebble_count` per collection) is already used by `CollectionsListView` and is a single-table aggregate.

---

## Token additions

### Typography (`Theme/Font+Pebbles.swift`)

Add two cases to the existing `PebblesFont` enum:

| Token | Family | Size | Weight | Tracking | textCase |
|---|---|---|---|---|---|
| `counterLg` | SF Pro Rounded | 17 | semibold | 0.34 (2%) | — |
| `captionEmphasized` | SF Pro Rounded | 12 | semibold | 0.24 (2%) | — |

No new mapping logic — they slot into the existing switch over `font / tracking / isUppercase`.

### Icons (new file: `Theme/Icon+Pebbles.swift`)

Icons live in a sibling enum, not in `PebblesFont`, so call sites read intent-first (`Image(...).pebblesIcon(.md)` vs. `.pebblesFont(.iconMd)` — the second pretends an icon is a text style).

```swift
enum PebblesIcon { case sm, md, large }

extension View {
    func pebblesIcon(_ token: PebblesIcon) -> some View {
        font(.system(size: token.size, weight: token.weight, design: .rounded))
    }
}
```

| Token | Size | Weight |
|---|---|---|
| `.sm` | 13 | semibold |
| `.md` | 15 | medium |
| `.large` | 17 | semibold |

Implementation note: under the hood this still applies a `Font`, because SF Symbols are font glyphs and `font(.system(size:weight:design:))` is the native, pixel-precise API. The `pebblesIcon(_:)` wrapper exists for *semantics*, not mechanics. (`imageScale` / `fontWeight` exist but only scale relative to dynamic-type — wrong tool for fixed sizes.)

---

## Component changes

### `ProfileView`

- Outer `VStack` spacing: `Spacing.xl` (22), replacing the literal `16`.
- Bottom padding: keep.
- Top `padding(.top, 8)` on banner: remove — the `xl` outer gap and screen padding already handle it.

### `ProfileBanner`

- Outer `VStack(spacing: Spacing.xxl)` (34).
- Inner title/subtitle `VStack(spacing: Spacing.xs)` (3).
- Title: `.title` token + `system.foreground`.
- Subtitle: `.meta` token + `system.secondary`. Drop the manual `.textCase(.uppercase)` (the `.meta` token already applies it).

### `ProfileShortcutTile`

- Background: `Color.accent.surface` (was `system.muted`).
- Corner radius: 17 (was 16).
- VStack spacing: `Spacing.sm` (10) (was 8).
- Icon: `.pebblesIcon(.large)` + `Color.accent.primary` (was `.font(.title3)`).
- Label: `.pebblesFont(.callout)` + `Color.system.secondary` (was `.caption.weight(.medium)` + `system.foreground`).
- Vertical padding: `Spacing.lg` (17).
- `ProfileShortcutsRow` HStack spacing: `Spacing.sm` (10) (was 12).

### Card chrome (Stats / Collections / Lab)

Three cards share identical chrome: clear background, `system.muted` 1pt stroke, 17 radius, 17 padding, 17 inner gap.

Expressed as a `View` extension in `Theme/PebblesScreen.swift` (or a new `Theme/ProfileCard.swift` — placement TBD during implementation), so call sites stay modifier-chain style:

```swift
VStack(alignment: .leading, spacing: Spacing.lg) {
    // card content
}
.profileCard()
```

Where `.profileCard()` applies `padding(Spacing.lg)` + an `overlay { RoundedRectangle(cornerRadius: Spacing.lg).strokeBorder(Color.system.muted, lineWidth: 1) }` + `clipShape(RoundedRectangle(cornerRadius: Spacing.lg))`. No `View` wrapper, no children-closure — just a modifier.

### Card header (Stats + Collections)

Spec: chevron is `.iconMd` + `system.muted`, wrapped in an `HStack` with the heading, with the **whole row** as the interaction zone.

Small helper (private inside each card file or extracted as `ProfileCardHeader` in the same folder):

```swift
HStack {
    Text("COLLECTIONS").pebblesFont(.cardHeading).foregroundStyle(Color.system.secondary)
    Spacer()
    Image(systemName: "chevron.right")
        .pebblesIcon(.md)
        .foregroundStyle(Color.system.muted)
}
.contentShape(Rectangle())
```

Wrap the entire `HStack` in a `NavigationLink { destination } label: { … }.buttonStyle(.plain)` so taps anywhere along the row navigate.

**Stats card chevron**: there is no Stats-detail screen yet in the iOS app. Omit the chevron on the Stats card for this issue (header is just the label) — surface this as a follow-up. Don't fabricate a destination.

### `ProfileStatsCard`

- Chrome via `.profileCard()`.
- Inner `VStack(alignment: .leading, spacing: Spacing.lg)` (17).
- Heading: `Text("STATS").pebblesFont(.cardHeading).foregroundStyle(Color.system.secondary)` — drop hand-rolled `.caption.weight(.semibold).tracking(0.8)` + `.textCase(.uppercase)`.
- Separator: `Divider().overlay(Color.system.muted)` (was `system.secondary.opacity(0.3)`).
- Inside: `RipplesRow` then divider then `ProfileCountersRow` — order unchanged.

### `RipplesRow`

- HStack spacing: `Spacing.lg` (17).
- Title: `.headline` + `system.foreground` (already correct, but use the token, drop `.subheadline.weight(.semibold)`).
- Subtitle: `.subhead` + `system.secondary`.

### `RippleBadge`

- Digit typography: `.pebblesFont(.captionEmphasized)` (was `.system(size: 12, weight: .bold, design: .rounded)`).
- Digit color: always `Color.system.foreground` (was conditional on `colorScheme` × `activeToday`). Remove the `digitColor` computed property and `@Environment(\.colorScheme)`. This is a spec change — flag in PR description so design can confirm dark-mode contrast on inactive states.

### `AssiduityGrid`

- Cell size 7 per spec (`.font(.system(size: 7))`), down from 14. Confirm visual density is still readable; if not, escalate to design before changing.
- Cell spacing stays 4 unless visual review says otherwise.

### `ProfileCountersRow` → `DataTile`

Rewrite. Extract a `DataTile` view in the same file (or `Components/DataTile.swift` — decide during implementation based on whether anything else consumes it; if only counters use it, keep private).

```swift
private struct DataTile: View {
    let value: Int?
    let icon: String
    let label: LocalizedStringResource

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(value.map { "\($0)" } ?? "—")
                .pebblesFont(.counterLg)
                .foregroundStyle(Color.system.foreground)
                .monospacedDigit()
            HStack(spacing: Spacing.xs) {
                Image(systemName: icon)
                    .pebblesIcon(.sm)
                    .foregroundStyle(Color.accent.primary)
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
```

Three tiles in an `HStack`: `Days`, `Pebbles`, `Karma`. Same icons (`calendar`, `fossil.shell`, `sparkles`).

### `ProfileCollectionsCard`

- Chrome via `.profileCard()`, inner `VStack(spacing: Spacing.lg)`.
- Header via the navigable card-header pattern → `CollectionsListView`.
- Query swap: `select("id, name")` → `select("id, name, mode, pebble_count:collection_pebbles(count)")`. Decode into the existing `Collection` model (which already handles the PostgREST aggregate shape). Drop the private `ProfileCollectionRow` struct.
- Horizontal scroller: spacing `Spacing.sm` (10) — match the tile spec.
- Empty state: unchanged behaviour (show single dashed `.empty` tile that opens `CreateCollectionSheet`).
- Filled tile tap: wired to push `CollectionDetailView(collection: c, onChanged: { hasLoaded = false; await load() })`. The reload closure refreshes both name and pebble count after edits.

### `ProfileCollectionCard`

Full rewrite to the new spec. Both variants:

- Background: clear.
- Border: 1pt `system.muted` — solid for `.filled`, dashed `[10, 10]` with `lineCap: .round` for `.empty`.
- Corner radius: 17.
- Padding: 17.
- Outer VStack spacing: `Spacing.sm` (10).
- Frame: width 140 (unchanged), height — drop fixed 120 if the new layout looks balanced; otherwise keep. Decide visually during implementation.

Icon box (`Group` or `ZStack`):

- 34×34 square (`.frame(width: Spacing.xxl, height: Spacing.xxl)`).
- Background: `Color.accent.surface`.
- Corner radius: `Spacing.sm` (10) — spec doesn't specify; matches the rounded-square treatment used elsewhere. If design wants a circle, swap to `Capsule()` during review.
- Icon: `.pebblesIcon(.sm)` + `Color.accent.primary`.
- Symbol: `square.stack.3d.up` for `.filled`, `plus` for `.empty` (spec says "icon and `+`" — change from `plus.square.dashed` since the dashed border is now provided by the tile itself).

Body:

- VStack(alignment: .leading, spacing: Spacing.xs).
- Name: `.headline` + `system.foreground` for `.filled`; `"New collection"` + `.headline` + `system.foreground` for `.empty` (per the screenshots, the label is the same weight regardless of variant — only the icon and border differ).
- Subtitle for `.filled`: `Text("\(pebbleCount) pebbles")` → `.subhead` + `system.secondary`. Localize the plural (`pebble` vs `pebbles`) via `LocalizedStringResource` with a stringsdict or inline `Text("^[\(pebbleCount) pebble](inflect: true)")` — pick whichever already matches conventions in the iOS app. **TBD during implementation; cheapest correct option wins.**
- Subtitle for `.empty`: omit (no count to show).

Tap target:

- `.empty` variant: keep `Button(action:)` (opens `CreateCollectionSheet`). Add `.contentShape(RoundedRectangle(cornerRadius: 17))` so the empty interior also hit-tests — that fixes the "only the + works" frustration.
- `.filled` variant: wrap in `NavigationLink { CollectionDetailView(collection:, onChanged:) } label: { … }.buttonStyle(.plain)`. Same `.contentShape(...)` for consistency.

API shape change for `ProfileCollectionCard`:

```swift
enum Variant {
    case filled(collection: Collection)   // was: filled(name: String)
    case empty
}
```

This lets the tile read `collection.name` and `collection.pebbleCount` from a single source. The `action` closure stays — `ProfileCollectionsCard` decides whether to wrap the tile in a `Button` (empty) or `NavigationLink` (filled).

### `ProfileLabCard`

- Chrome via `.profileCard()`.
- Body: `HStack(spacing: Spacing.xs)` — heading and subheading. (Spec calls for the icon + chevron too; keep them. The `xs` spacing applies between heading text + subhead text within a `VStack(alignment: .leading)`. Outer HStack between icon / text-stack / chevron stays comfortable (`Spacing.sm` or default).)
- Heading: `.headline` + `system.foreground`.
- Subheading: `.subhead` + `system.secondary`.
- Chevron: `.pebblesIcon(.md)` + `system.muted`.
- Whole row is a `NavigationLink` to `LabView` (already the case).

### `ProfileLogoutPill`

- Background: `Color.accent.surface` (was `system.muted`).
- Label: `.pebblesFont(.buttonLabel)` + `Color.accent.primary`.
- Shape: `RoundedRectangle(cornerRadius: Spacing.lg)` (was `Capsule()`).
- Drop `role: .destructive` — visually no longer destructive (matches accent palette, not red). Keep the action.
- Rename consideration: file is `ProfileLogoutPill.swift` but it's no longer pill-shaped. Leave the filename for this PR; rename in a follow-up if it bothers anyone.

---

## File-by-file summary

| File | Change |
|---|---|
| `Theme/Font+Pebbles.swift` | +2 cases (`counterLg`, `captionEmphasized`) + mappings |
| `Theme/Icon+Pebbles.swift` | **new** — `PebblesIcon` enum + `pebblesIcon(_:)` view extension |
| `Theme/PebblesScreen.swift` *(or new file)* | +`.profileCard()` view extension |
| `Features/Profile/ProfileView.swift` | Spacing token swap; drop top padding on banner |
| `Features/Profile/Components/ProfileBanner.swift` | Tokenize typography + spacing |
| `Features/Profile/Components/ProfileShortcutTile.swift` | Tokenize bg, radius, icon, label |
| `Features/Profile/Components/ProfileShortcutsRow.swift` | HStack spacing token |
| `Features/Profile/Components/ProfileStatsCard.swift` | `.profileCard()`, header tokens, divider color |
| `Features/Profile/Components/RipplesRow.swift` | Spacing + typography tokens |
| `Features/Shared/Ripples/RippleBadge.swift` | Digit typography + color simplification |
| `Features/Profile/Components/AssiduityGrid.swift` | Cell size 7 |
| `Features/Profile/Components/ProfileCountersRow.swift` | Rewrite as `DataTile`s |
| `Features/Profile/Components/ProfileCollectionsCard.swift` | `.profileCard()`, header, query +pebble_count, filled-tile NavigationLink |
| `Features/Profile/Components/ProfileCollectionCard.swift` | Full rewrite per spec |
| `Features/Profile/Components/ProfileLabCard.swift` | `.profileCard()`, tokens |
| `Features/Profile/Components/ProfileLogoutPill.swift` | bg + text + shape per spec |

Estimated diff: ~400 LOC (mostly substitutions and the two component rewrites).

---

## Risks & non-obvious decisions

- **`RippleBadge` digit color**: spec change ("ensure level color is `system.foreground`"). The existing logic inverted the digit to background on the active-today filled disc for contrast. Hard-coding `system.foreground` may reduce contrast on the active-day state. Flag for design review at PR time.
- **`AssiduityGrid` cell size 7**: spec says size 7. The current 14 is already small. Verify in simulator that 7 isn't visually broken (`fossil.shell.fill` at 7pt is tiny).
- **`pebbleCount` aggregate**: PostgREST returns `[{ "count": N }]`. The `Collection` model already handles this shape — no new decoding needed.
- **Per-collection plural**: localization-correct plural ("1 pebble" / "N pebbles") needs either a stringsdict entry in `Localizable.xcstrings` or inline morphology. Pick the convention already used in the iOS app (e.g. `SoulsListView` displays similar counts — match that pattern).
- **No tests added**: project policy is "no UI tests for now"; no test layer for these view-only changes is expected. Snapshot-style preview coverage is via SwiftUI `#Preview` blocks already in each component file — update them as APIs change (e.g. `ProfileCollectionCard` variant signature).
- **Arkaik update**: not required — no screen, route, or data model added or removed. Pure visual refacto.

## Acceptance

- Profile view matches the screenshots in issue #465 in both light and dark mode.
- Tapping anywhere on a filled collection tile pushes its `CollectionDetailView`.
- Tapping anywhere on the dashed "new collection" tile opens `CreateCollectionSheet`.
- Tapping anywhere along the Collections card header row also pushes `CollectionsListView`.
- All new typography / icon tokens are reachable as enum cases — no `.font(.system(size: N, …))` literals remain in Profile components.
- `npm run lint --workspace=@pbbls/ios` passes (or the equivalent Swift lint configured for the workspace, if any). iOS build succeeds in Xcode.
