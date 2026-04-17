# iOS — Display pebble render in Path list

**Issue:** [#276](https://github.com/bohns/pbbls/issues/276) · `ios` · `ui` · M23

## Goal

Show each pebble's visual (the server-composed SVG, colored by the pebble's emotion) as a small leading thumbnail inside every row of the Path list. When a pebble is edited and the user returns to Path, the refreshed visual must reflect any changes.

## Acceptance

- As a user with pebbles, when I'm on the Path, I see the pebble visual for each item.
- As a user edits an existing pebble, when I return to the Path, the edited pebble's visual is refreshed.

## Non-goals

- Server-side recomposition when an edit changes emotion or valence. This issue renders whatever `render_svg` the server currently stores; triggering `compose-pebble` on edit is tracked separately.
- Animation of the pebble visual (slice 1 — static render only).
- Any redesign of Path rows beyond adding the leading visual.
- Pagination, prefetching, or caching of SVG strings.

## Current state (reference)

- `apps/ios/Pebbles/Features/Path/PathView.swift` — Path list. Each row is a `Button` wrapping `VStack(name, date)`. Query selects `id, name, happened_at` only. `load()` is invoked from `.task` and after create/edit sheets dismiss.
- `apps/ios/Pebbles/Features/Path/PebbleRenderView.swift` — SwiftUI view that takes `svg: String` + `strokeColor: String?`, replaces `currentColor` tokens, and renders via `SVGView` at a hardcoded `.frame(width: 260, height: 260)`.
- Emotion color is stored on `emotions.color` (hex string). It is not on `pebbles`; the Path query does not currently join emotions.
- `pebbles.render_svg` is populated by the `compose-pebble` edge function on create. It can be `NULL` when compose failed (soft-success path).

## Design

### 1. Data layer

Extend the Path query to pull SVG + emotion color in a single round-trip:

```swift
.from("pebbles")
.select("id, name, happened_at, render_svg, emotion:emotions(color)")
.order("happened_at", ascending: false)
```

The `Pebble` struct used by `PathView` gains two optional fields:

```swift
struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let renderSvg: String?      // nil when compose-pebble failed
    let emotionColor: String?   // nil if the emotion row has no color
    // nested decoding helper for `emotion:emotions(color)` → emotionColor
}
```

Rationale: one round-trip beats lazy per-row loading. The list is short (pre-pagination), and rendering rows piecemeal as SVG strings stream in would feel janky.

### 2. Row layout

Replace the current `VStack(name, date)` with an `HStack`:

- Leading: `PebbleRenderView` sized 40×40.
- Spacing: 12pt.
- Trailing: existing `VStack(alignment: .leading) { name; date }`.
- Outer `Button { selectedPebbleId = pebble.id }` wraps the whole row, `buttonStyle(.plain)` preserved.

```
┌─────────────────────────────────────────┐
│  [▣]   Pebble name                      │
│  40pt  Apr 17, 2026                     │
└─────────────────────────────────────────┘
```

40pt matches the combined height of `.body` + `.caption` text, keeping rows compact (per option A confirmed during brainstorming).

### 3. `PebbleRenderView` size parameter

`PebbleRenderView` currently hardcodes `.frame(width: 260, height: 260)`. Add a `size: CGFloat` parameter with a default of `260` so existing call sites (Create/Edit/Detail sheets) remain unchanged. Path passes `size: 40`.

```swift
struct PebbleRenderView: View {
    let svg: String
    var strokeColor: String?
    var size: CGFloat = 260
    // ...
    .frame(width: size, height: size)
}
```

### 4. Fallbacks

- **`renderSvg == nil`:** render a 40×40 `RoundedRectangle` filled with a subtle surface color (no icon, no text) inline in the row via an `if let` branch. Keeps row height stable without drawing attention to the missing visual and avoids spawning a single-use placeholder view.
- **`emotionColor == nil`:** pass `strokeColor: nil` to `PebbleRenderView`. The existing implementation already handles this by skipping the `currentColor` replacement.

### 5. Refresh on edit

No new plumbing required. `PathView` already calls `load()` when the create and edit sheets dismiss. Since the same query now pulls `render_svg` and emotion color, the refreshed array naturally includes any server-stored updates. Acceptance criterion 2 is satisfied by the existing pattern.

## Files to change

- `apps/ios/Pebbles/Features/Path/PathView.swift` — update query, extend `Pebble` struct, refactor row to `HStack` with leading `PebbleRenderView`, handle fallbacks.
- `apps/ios/Pebbles/Features/Path/PebbleRenderView.swift` — add `size: CGFloat = 260` parameter.

## Risks / open items

- SVG rendering performance at 40pt across a list of N rows: untested at list density. If scroll performance degrades with many pebbles, revisit (cache rendered images, or switch to `Image`-backed raster rendering). Not a blocker for shipping this issue; flag if observed during testing.
- The soft-success case (pebble exists without `render_svg`) will now be visually apparent as a blank placeholder in Path — previously invisible. Expected behavior.
