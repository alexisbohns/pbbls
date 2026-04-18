# iOS Glyph Carving — Design

**Issue:** [#278 [Feat] Glyph carving in iOS](https://github.com/alexisbohns/pbbls/issues/278)
**Milestone:** M23 · TestFlight V1
**Date:** 2026-04-18

## Context

On web, a user can carve glyphs (free-form drawn marks) and associate them with pebbles. The mark is stored in `public.glyphs` as a JSON array of SVG path strings, and the server-side compose-pebble function bakes it into each pebble's `render_svg`.

The iOS app currently has a read-only `GlyphsListView` that lists glyph names with no carving UI, no picker, and no pebble association. This spec introduces full parity on iOS: drawing a glyph, picking an existing one, associating it with a pebble during record or edit, and managing glyphs from the profile.

## Scope

### In scope
- New "Glyph" row in the iOS pebble record form (Create + Edit).
- Tapping the row opens a picker sheet listing the user's glyphs, with a "Carve new glyph" CTA.
- Tapping "Carve new glyph" opens a full-screen cover with a drawing canvas, Undo, Clear, Save, and Cancel.
- On save, the glyph persists to `public.glyphs` and becomes selectable for the pebble being recorded/edited.
- `Profile → Glyphs` gains a toolbar "+" button to carve a new glyph, and switches from a plain list of names to a thumbnail grid.
- After associating a glyph with an edited pebble, the pebble's `render_svg` is refetched so the user sees the new composed render.

### Out of scope (V1)
- Editing an existing glyph's strokes.
- Naming a glyph (all iOS-created glyphs are stored with `name = null`).
- Deleting a glyph from the profile list.
- Stamps (explicitly marked out of scope in the issue).
- Pressure sensitivity or a user-controlled stroke-width slider (issue constraint: stroke width is always 6).
- Any DB migration — `glyphs.shape_id` stays in the schema and iOS writes the hardcoded `square` shape id on insert.

## Acceptance criteria (from issue #278)

| # | Given / When / Then | Covered by |
|---|---|---|
| 1 | When recording a pebble, user can draw a glyph | `GlyphCarveSheet` reached from `PebbleFormView → GlyphPickerSheet` |
| 2 | When recording a pebble, user can choose an existing glyph | `GlyphPickerSheet` grid |
| 3 | Chosen personal glyph appears in the pebble render after save | Extended `PebbleCreatePayload` sends `glyph_id`; server composes `render_svg` |
| 4 | Editing an existing pebble to add a personal glyph refreshes the render | Extended `PebbleUpdatePayload` sends `glyph_id`; post-update refetch of `render_svg` |
| 5 | Saving a glyph stores it | `GlyphService.create(...)` inserts into `public.glyphs` |
| 6 | Profile → Glyphs shows all user glyphs | Refactored `GlyphsListView` with thumbnail grid |
| 7 | Profile → Glyphs allows creating new glyphs | Toolbar "+" button presents `GlyphCarveSheet` |

## Architecture

### Folder layout

New feature folder `apps/ios/Pebbles/Features/Glyph/`:

```
Glyph/
  Models/
    Glyph.swift              (replaces Features/Profile/Models/Glyph.swift)
    GlyphStroke.swift
  Services/
    GlyphService.swift
  Utils/
    PathSimplification.swift
    SVGPath.swift
  Views/
    GlyphCanvasView.swift
    GlyphCarveSheet.swift
    GlyphPickerSheet.swift
    GlyphThumbnail.swift
    GlyphsListView.swift     (moved from Features/Profile/Lists/)
```

The old `Features/Profile/Models/Glyph.swift` and `Features/Profile/Lists/GlyphsListView.swift` are deleted (file moves only — `ProfileView` needs no change since Swift targets resolve types by module membership, not file path).

### Key choices (selected during brainstorming)

- **`shape_id` deprecation scope — iOS-only, no schema change.** iOS hardcodes the deterministic `square` shape id from `pebble_shapes` on every insert. The DB column stays `NOT NULL`-compatible by always being populated. Web is unchanged in this spec.
- **Record-form placement — inline row.** The "Glyph" row lives in `PebbleFormView` between emotion and domain. Matches the form grammar used for emotion, soul, collection.
- **Carve editor presentation — `.fullScreenCover`.** Prevents swipe-to-dismiss during drawing (addresses the issue's "Caution when carving the glyph if it's a sheet" warning), no need for `.interactiveDismissDisabled` gymnastics.
- **Drawing tech — SwiftUI `Canvas` + `DragGesture`.** Produces the exact `MarkStroke[]` shape the web uses. No PencilKit (binary format, awkward export). No UIKit bridge.

## Data model

### `Glyph` (extended from minimal read-only)

```swift
struct Glyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String    // "0 0 200 200" for iOS-carved glyphs

    enum CodingKeys: String, CodingKey {
        case id, name, strokes
        case viewBox = "view_box"
    }
}
```

### `GlyphStroke` (matches web `MarkStroke`)

```swift
struct GlyphStroke: Codable, Hashable {
    let d: String        // "M x,y L x,y …"
    let width: Double    // always 6 for iOS-carved glyphs
}
```

### `PebbleDraft`

Add:
```swift
var glyphId: UUID?
```

### `PebbleCreatePayload` and the update equivalent

Add:
```swift
let glyphId: UUID?
// encoded as `glyph_id`
```

**Key-present semantics matter.** The `create_pebble` / `update_pebble` RPCs use `payload ? 'glyph_id'` to decide whether to touch the column. For V1 we always send the key — `null` clears the selection, a UUID sets it. This matches the acceptance criterion "editing a pebble to associate a glyph refreshes the render".

## Drawing mechanics

### Coordinate system

- Canvas is rendered at a natural iOS size (target ~300pt square, responsive to device width).
- Stored paths use the `0–200` SVG coordinate space.
- Conversion factor: `stored = touch * (200 / canvasSide)`.
- Touch points are clamped to `0…canvasSide` before conversion so slipped fingers off-canvas don't produce out-of-range coordinates.

### Stroke capture

```swift
Canvas { ctx, size in
    for stroke in committedStrokes { ctx.stroke(path(from: stroke.d), …) }
    if !activePoints.isEmpty { ctx.stroke(liveLinearPath(activePoints), …) }
}
.gesture(
    DragGesture(minimumDistance: 0, coordinateSpace: .local)
        .onChanged { value in activePoints.append(clamp(value.location)) }
        .onEnded { _ in commitStroke() }
)
```

`commitStroke()`:
1. Apply RDP simplification with `epsilon = 1.5` (matches web `RDP_EPSILON`).
2. Map points to 200-space, round to 2 decimals.
3. Serialize to `"M x,y L x,y …"`.
4. Append `GlyphStroke(d: …, width: 6)` to `strokes`.
5. Clear `activePoints`.

### Utilities

**`PathSimplification.simplifyRDP(_ points: [CGPoint], epsilon: Double) -> [CGPoint]`**
Direct port of `apps/web/lib/utils/simplify-path.ts`. Pure function, CoreGraphics only.

**`SVGPath.svgPathString(from points: [CGPoint], precision: Int = 2) -> String`**
Emits `M x,y L x,y L x,y …` with fixed-precision rounding.

**`SVGPath.path(from d: String) -> Path`**
Parses `M`, `L`, `Q`, `C` commands into a SwiftUI `Path`. iOS-carved glyphs only emit `M` and `L`; the seed system glyphs (`community`, `family`, etc.) use `Q`, so the parser must handle quadratic Béziers for `GlyphThumbnail` to render them correctly if they ever appear. Malformed input returns an empty `Path` and logs a warning — no crashes.

## UI flow

### `PebbleFormView` — new "Glyph" row

```
Glyph
  ┌──────┐
  │ [·]  │   Carve or pick a glyph           ›
  └──────┘
```

- Leading: 32×32 `GlyphThumbnail` showing the selected glyph, or a placeholder when `draft.glyphId == nil`.
- Center label: `"Carve or pick a glyph"` when empty; glyph name (or `"Untitled glyph"`) when set.
- Trailing chevron.
- Tap → presents `GlyphPickerSheet`.
- Long-press when selected → "Remove glyph" context menu item that clears `draft.glyphId`.

Row placement: between emotion and domain in both `CreatePebbleSheet` and `EditPebbleSheet`.

### `GlyphPickerSheet`

Standard `.sheet` (swipe-to-dismiss is safe here — no drawing).

- "Carve new glyph" row at top → presents `GlyphCarveSheet` as `.fullScreenCover` layered above the picker.
- 3-column `LazyVGrid` of `GlyphThumbnail`s below. Loaded via `GlyphService.list()` in `.task { await load() }`.
- Tapping a thumbnail sets the caller's `draft.glyphId` via a callback and dismisses.
- Empty state: only the "Carve new" row is shown.
- Error state: `ContentUnavailableView("Couldn't load glyphs", systemImage: "exclamationmark.triangle", description: …)` with a Retry button.

### `GlyphCarveSheet` (full-screen cover)

```
┌───────────────────────────────────┐
│ Cancel       New glyph      Save  │
├───────────────────────────────────┤
│                                   │
│   ┌───────────────────────────┐   │
│   │   GlyphCanvasView         │   │
│   │   (200x200 coord space)   │   │
│   └───────────────────────────┘   │
│                                   │
│         Undo     Clear            │
└───────────────────────────────────┘
```

- **Cancel** — dismisses without saving. If `!strokes.isEmpty`, confirm with a destructive alert "Discard your glyph?".
- **Save** — disabled until `!strokes.isEmpty`. Shows a `ProgressView` while saving. On success, dismisses with the new `Glyph` passed back via callback to `GlyphPickerSheet`, which also dismisses and sets the caller's `draft.glyphId`.
- **Undo** — removes the last element of `strokes`. Disabled when empty.
- **Clear** — empties `strokes`. Disabled when empty.
- **Save error** — `saveError: String?` state, rendered inline below the canvas. Strokes are preserved so the user can retry.

### Profile → Glyphs

`GlyphsListView` moves to `Features/Glyph/Views/` and gains:
- Navigation toolbar "+" button presenting `GlyphCarveSheet`.
- Grid layout: 3-column `LazyVGrid` of `GlyphThumbnail`s (name rendered underneath if set).
- After save, the returned glyph is prepended to the in-memory array — no refetch required (we already have the full row from the INSERT's `SELECT` return).

### Render-SVG refresh after edit

Server-side `compose-pebble` rewrites `render_svg` whenever a pebble's render inputs change. After `update_pebble` succeeds with a new `glyph_id`:

1. `EditPebbleSheet` refetches the pebble's `render_svg` from `v_pebbles_full` (`select id, render_svg where id = …`).
2. The in-memory pebble model is updated.
3. `PebbleRenderView` re-renders the composed SVG.

If the refetch returns the stale `render_svg` (compose function hasn't completed yet), retry once after a 500 ms delay. The implementation plan should confirm whether an existing iOS post-save refetch pattern already handles this — if yes, reuse it rather than duplicating.

## Data flow — Supabase integration

### `GlyphService`

```swift
struct GlyphService {
    let supabase: SupabaseService

    static let squareShapeId = UUID(
        uuidString: "<md5('pebble_shapes:square') as UUID>"
    )!

    func list() async throws -> [Glyph]
    func create(strokes: [GlyphStroke], name: String?) async throws -> Glyph
}
```

The deterministic square shape id is resolved from migration `20260411000006_deterministic_reference_ids.sql` which defines `id = md5('pebble_shapes:' || slug)::uuid`. The implementation plan should include a one-shot helper (or simply a Postgres query during spec execution) to compute and hardcode the exact UUID.

### `list()`

```swift
let rows: [Glyph] = try await supabase.client
    .from("glyphs")
    .select("id, name, strokes, view_box")
    .order("created_at", ascending: false)
    .execute()
    .value
```

**Open point — system glyphs in the picker.** The current RLS policy (`migrations/20260415000001_remote_pebble_engine.sql` line 32) allows reads of system glyphs (`user_id is null`) alongside user glyphs. Decide during implementation whether the picker grid filters these out with `.not("user_id", operator: .is, value: "null")` — recommended default is **filter them out**, since system glyphs are domain-default fallbacks, not a personal carving.

### `create(strokes:, name:)`

```swift
struct GlyphInsertPayload: Encodable {
    let userId: UUID
    let shapeId: UUID
    let strokes: [GlyphStroke]
    let viewBox: String
    let name: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case shapeId = "shape_id"
        case viewBox = "view_box"
        case strokes, name
    }
}

let created: Glyph = try await supabase.client
    .from("glyphs")
    .insert(payload)
    .select("id, name, strokes, view_box")
    .single()
    .execute()
    .value
```

Single-table, single-statement — per `AGENTS.md`, direct client insert is fine (no RPC required).

### Pebble payload extensions

- `PebbleCreatePayload.glyphId: UUID?` → encoded as `glyph_id`.
- `PebbleUpdatePayload.glyphId: UUID?` → encoded as `glyph_id`.
- Both RPCs already accept the key (`create_pebble` in `20260411000005`, `update_pebble` in `20260415000000`). No migration needed.

## Error handling

Every async path logs via `os.Logger` and surfaces user-visible state. No silent catches.

| Path | Failure behavior |
|---|---|
| `GlyphService.list()` | `logger.error(...)`; `ContentUnavailableView` with Retry |
| `GlyphService.create(...)` | `logger.error(...)`; inline `saveError` below the canvas; strokes preserved; Save button re-enabled |
| `create_pebble` with `glyph_id` | existing `CreatePebbleSheet` error path — no change |
| `update_pebble` with `glyph_id` | existing `EditPebbleSheet` error path — no change |
| Post-update `render_svg` refetch | log warning; keep stale `render_svg` rather than blanking |
| Missing auth session at save time | `logger.error("glyph save without session")`; surface "Please sign in again" |

## Accessibility

- `GlyphCanvasView.accessibilityLabel("Drawing canvas")` with `.accessibilityAddTraits(.allowsDirectInteraction)` so VoiceOver doesn't trap the drag.
- `accessibilityValue("\(strokes.count) strokes drawn")` on the canvas; updated after each commit (matches web's `aria-live` stroke count).
- `Undo` / `Clear` / `Save` / `Cancel` — plain SwiftUI buttons with accessible labels.
- Each `GlyphThumbnail` in the picker grid is wrapped in a `Button` with `accessibilityLabel("Select glyph")` (or the glyph name when set).
- The `GlyphThumbnail` inside the form row is marked `.accessibilityHidden(true)`; the row's own label carries the description.
- All text uses semantic fonts and scales with Dynamic Type. The canvas size is fixed (gesture-driven).

## Testing

Swift Testing per `AGENTS.md` (`@Suite`, `@Test`, `#expect`). No UI tests.

New file `PebblesTests/Features/Glyph/GlyphUnitTests.swift`:

- `PathSimplificationTests`
  - Empty input → empty
  - Two-point input → unchanged
  - 10 collinear points → reduces to 2 endpoints (ε = 1.5)
  - Known fixture → known simplified output (point-by-point, tolerance < 0.01)
- `SVGPathSerializationTests`
  - `[(0,0), (10,20)]` → `"M0,0 L10,20"`
  - Two-decimal precision on fractional points
  - Round-trip: `svgPathString(from:)` then `path(from:)` produces the expected number of `Path` subpaths
- `SVGPathParsingTests`
  - Parses `M`-only path (typical iOS output)
  - Parses `Q` curves (system seed glyphs) without throwing
  - Malformed input returns empty `Path`, no crash
- `GlyphInsertPayloadTests`
  - Encodes exact snake_case keys: `user_id`, `shape_id`, `view_box`, `strokes`, `name`
  - `strokes` encodes as `[{ "d": …, "width": 6 }, …]`
- `PebbleCreatePayloadTests` (extend existing)
  - `glyphId == nil` → `glyph_id: null` present in JSON
  - `glyphId = UUID(...)` → `glyph_id: "<uuid>"` present in JSON
- `PebbleUpdatePayloadTests` (same pattern)

No tests against live Supabase. `GlyphService` has no protocol shim yet (YAGNI per `AGENTS.md`); extract one the moment a test needs to fake it.

## Verification checklist

Before opening the PR:

1. Acceptance criteria 1–7 manually verified in TestFlight build.
2. `npm run build` and `npm run lint` pass at repo root.
3. Swift tests pass (`swift test` via the iOS scheme in Xcode, or the CI script).
4. `update-config` / `arkaik` skills invoked if any app-graph changes require it.
5. PR title uses conventional commits (`feat(ios): glyph carving`), body starts with `Resolves #278`, labels `feat` + `ios` + `core`, milestone `M23 · TestFlight V1`.

## Open points for the implementation plan

- Confirm whether the iOS edit flow already refetches a pebble's `render_svg` after `update_pebble` — reuse if yes, add if no.
- Compute and hardcode the exact `square` `pebble_shapes` UUID (deterministic from `md5('pebble_shapes:square')`).
- Decide the default for system-glyph visibility in the picker (recommendation: filter them out — they're domain-default fallbacks, not personal carvings).
- Confirm `RDP_EPSILON = 1.5` is the exact constant used in web's `simplify-path.ts` during the port.
