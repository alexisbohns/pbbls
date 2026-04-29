# iOS Pebble Stroke Animation (Issue #333)

## Context

The iOS pebble read sheet currently renders the composed pebble SVG statically via the third-party `SVGView` (in `PebbleRenderView`). The server-side engine has been emitting an `AnimationManifest` to the `pebbles.render_manifest` jsonb column since the engine landed, with the intention that a later iOS slice would consume it to draw strokes progressively. Issue #333 is that slice.

When reviewing the existing manifest shape, we found it duplicates information already present in the composed SVG (path `d` strings and per-stroke `length` estimates) and adds non-trivial payload for what is, conceptually, a small ordered list of phase timings. We're using this work to lean the system out: drop the manifest entirely, let the SVG carry structure, and put motion on the client where it belongs.

## Goals

- Animate the pebble on the read sheet with a progressive stroke-drawing reveal each time the sheet opens.
- Respect Reduce Motion by rendering the pebble in its final settled state with no animation.
- Remove server-side animation metadata: drop the `render_manifest` column, the manifest JSON, and the engine code that produces it.
- Move animation timing to a versioned client-owned table keyed by `render_version`.
- Keep the existing static renderer (`PebbleRenderView` / `SVGView`) in place for non-read-sheet call sites.

## Non-goals

- No replacement of `SVGView` in the timeline row, edit preview, or form preview.
- No per-path stagger inside a single phase; all paths in a phase share its start/end.
- No "muted-to-colored" coloring-in beat. Strokes render in the emotion color from the start of the animation.
- No web-side renderer changes.
- No UI tests for the animation.

## Trigger and accessibility

- Animation plays on every appearance of the read sheet — there is no "first time only" persistence.
- When `accessibilityReduceMotion` is `true`, render the pebble fully drawn with no animation.
- When `render_version` is unknown to the client, render fully drawn with no animation and log a warning at `info` level — never crash, never block the sheet.

## Server changes

### Engine simplification

`packages/supabase/supabase/functions/_shared/engine/`:

- `compose.ts`:
  - Remove `buildManifest`, `extractPaths`, `estimatePathLength`, and the `TIMING` constant.
  - `composePebble()` returns `{ svg, canvas }` only.
- `types.ts`:
  - Remove `AnimationManifest` and `AnimationManifestLayer`.
  - `PebbleEngineOutput` becomes `{ svg, canvas }`.

### Edge functions

- `_shared/compose-and-write.ts`:
  - Stop writing `render_manifest` to the `pebbles` row.
  - The function's return shape becomes `{ render_svg, render_version }`.
- `compose-pebble/index.ts` and `compose-pebble-update/index.ts`:
  - Response payload becomes `{ pebble_id, render_svg, render_version }`. No `render_manifest` field at any level.

### Database

- New migration: `alter table pebbles drop column render_manifest;`.
- Run `npm run db:reset && npm run db:types --workspace=packages/supabase`. Commit the regenerated `packages/supabase/types/database.ts`.
- No data preservation needed — the column has never been read by any client.

### Render version

- `RENDER_VERSION` is **not** bumped. Removing `render_manifest` does not alter the composed SVG, so the rendered output is unchanged. The iOS timing table maps the current version string to the 3-phase timings. We bump only when we change something that affects what the renderer should do.

## iOS phase model

3-phase model: `glyph → shape → fossil → settle`. No `fill` phase.

| Phase  | Delay (ms) | Duration (ms) | Behavior |
|--------|-----------:|--------------:|----------|
| glyph  | 0          | 800           | Layer's combined path trims 0 → 1, ease-out. |
| shape  | 400        | 800           | Layer's combined path trims 0 → 1, ease-out. |
| fossil | 800        | 600           | Layer's combined path trims 0 → 1, ease-out. Skipped if the layer is absent. |
| settle | 1200       | 400           | Whole-renderer scale pulse 1.0 → 1.04 → 1.0, ease-in-out. |

Total runtime ≈ 1.6 s. Strokes are emotion-colored from the first frame.

## iOS code structure

New folder: `apps/ios/Pebbles/Features/Path/Render/`

- `SVGPathParser.swift` — converts an SVG path `d` string to `CGPath`. Supports `M m L l H h V v C c S s Q q T t A a Z z` with implicit-command continuation. Pure function, no dependencies. Targeted ~150 LOC.
- `PebbleSVGModel.swift` — parses a composed pebble SVG into a typed model:
  ```swift
  struct PebbleSVGModel {
    let viewBox: CGRect
    let layers: [Layer]
    struct Layer {
      enum Kind { case shape, fossil, glyph }
      let kind: Kind
      let transform: CGAffineTransform
      let opacity: Double
      let combinedPath: CGPath
    }
  }
  ```
  - Uses `Foundation.XMLParser` (no new dependency).
  - Recognizes the engine-emitted layer ids `layer:shape`, `layer:fossil`, `layer:glyph`.
  - Reads each layer's `transform` (parses `translate(x, y)` and `scale(s)` — the only forms the engine emits) and `opacity` (defaults to 1.0).
  - Concatenates all `<path d="…">` descendants of a layer into a single `combinedPath` so the layer animates as one trim.
  - Fails closed: if the SVG is missing `viewBox`, has no recognized layer, or any path `d` fails to parse, `init?` returns `nil` and the caller falls back to the static renderer.
- `PebbleAnimationTimings.swift` — version → timings table:
  ```swift
  struct Timings {
    struct Phase { let delay: Double; let duration: Double }  // seconds
    let glyph: Phase
    let shape: Phase
    let fossil: Phase
    let settle: Phase
  }
  static func forVersion(_ v: String?) -> Timings?
  ```
  Returns `nil` for unknown versions.
- `PebbleAnimatedRenderView.swift` — the new SwiftUI view used by `PebbleReadBanner`:
  ```swift
  struct PebbleAnimatedRenderView: View {
    let svg: String
    let strokeColor: String
    let renderVersion: String?
  }
  ```
  - Parses the SVG once on first appearance into a `PebbleSVGModel`.
  - Resolves timings via `PebbleAnimationTimings.forVersion(renderVersion)`.
  - If parse fails OR timings are nil OR `accessibilityReduceMotion` is true: falls back to `PebbleRenderView(svg:, strokeColor:)` (the existing SVGView wrapper). No animation.
  - Otherwise, renders each layer as a `Shape` whose `path(in:)` applies the layer's transform to its `combinedPath` and clips to the model's `viewBox`. Per-layer `@State var progress: Double` drives `.trim(from: 0, to: progress)` and is animated via `withAnimation(.easeOut(duration: …).delay(…)) { progress = 1 }`. Settle pulse is a separate `@State var settleScale: Double` driven by two chained `.easeInOut` animations.
  - On view re-appear, all progress states reset to 0 and the animation re-runs.

## Renderer composition

`PebbleReadBanner` (`apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`) currently calls `PebbleRenderView(svg:, strokeColor:)` for both the with-photo and without-photo variants. It will switch to `PebbleAnimatedRenderView(svg:, strokeColor:, renderVersion:)` in both branches. The new view requires `renderVersion`, so `PebbleReadBanner`'s init grows a `renderVersion: String?` parameter, plumbed from `PebbleReadView`'s `detail.renderVersion`.

`PebbleRenderView` itself is **unchanged**. All other call sites (`PebbleRow`, `EditPebbleSheet`, `PebbleFormView`'s preview block) continue to use it as-is.

## Tests

Swift Testing in `apps/ios/PebblesTests/`:

- `SVGPathParserTests` — feed representative `d` strings (line, cubic, quadratic, elliptical arc, relative variants, implicit continuation, closepath) and assert the resulting `CGPath` has the expected element count and bounding box.
- `PebbleSVGModelTests` — feed a fixture composed SVG (including a fossil-less variant and a malformed variant); assert layer order, transforms parsed correctly, opacity defaults, and that malformed input returns `nil`.
- `PebbleAnimationTimingsTests` — known versions return non-nil; unknown returns `nil`.

No tests assert animation timing directly. Visual verification is via SwiftUI Previews (`PebbleAnimatedRenderView` previews with both reduce-motion off and on, and with/without fossil).

## Migration / rollout

1. Land server simplification + DB migration + types regen + iOS animation in a single PR. The change is internally consistent — server stops writing the manifest, no client ever read it.
2. No backfill needed. The composed SVG and `render_version` of existing rows are untouched.
3. No app-store coordination concern: existing iOS builds in the wild already ignore `render_manifest` (it's not in any `select`).

## Open questions

None.
