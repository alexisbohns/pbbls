# Photo crop and rotate on iOS — design

Issue #1654, iOS only. Approved in conversation on 2026-11-15; Android mirror is a separate issue. Filters are a later iteration that lands in the same editor (a third tool tab).

## Why

A picked or captured photo goes to the Path as whatever shape it was taken in. People want to frame it: choose a format, move and zoom within it, straighten it, turn it a quarter. Nothing about the photo's shape is stored in the database — every surface reads the ratio from the file header (`SnapAspectCache` on iOS and Android) — so a crop applied on the device before upload needs no schema, RPC, web or Android display change.

## Decisions

1. **The editor opens after every new photo** — an inline pick in Capture, a camera shot in Capture, a pick in `EditMomentSheet`. Home's camera shortcut is left alone: it records a moment in one shot with no form, so its photo uploads as taken. "Done" crops, then runs today's `ingest` (process + upload) and moves on; nothing uploads before Done. "Cancel" returns to the picker with the selection cleared. "Change photo" goes back to the picker, so a new pick opens the editor again.
2. **Already-saved photos are not editable.** Only their 1024 px copy exists; re-cropping it would degrade the image.
3. **Five fixed formats, no free crop:** 4:5, 3:4, 1:1, 4:3, 5:4. The editor opens on the one closest to the photo's own ratio (compared in log space, so 3:4 and 4:3 are equidistant from 1:1; ties go to the portrait one).
4. **No empty space, ever.** The photo always covers the crop window, at any angle, zoom and pan.
5. **Rotate:** a fine angle of −45°…+45° and a 90° clockwise button. The 90° button also flips the format to its pair (4:5 ↔ 5:4, 3:4 ↔ 4:3, 1:1 stays), so turning a sideways photo upright gives the frame you expect.
6. **Changing format or angle keeps the photo's centre** where it was and raises the zoom only as far as coverage needs.
7. **The editor follows the app theme** (light and dark), Pebbles tokens throughout. The reference screenshots are for layout only.
8. **The capture date is read from the original bytes** before cropping, since the crop drops all metadata.
9. **Icons come from the existing glyph set** for now; they will be replaced later from a separate glyph effort: Ratio tab `rectangleDashed`, Rotate tab `setSquare`, 90° button `againBackward`. Reset is a text button. `filterBox` is reserved for the future Filters tab.
10. **The photo step's "Just write" and camera controls move to the bottom** of the step, below the picker, in thumb reach. They leave the scaffold's `fixedSlot` between the title and the grid.

## Layout

Full-screen cover over the flow that opened it.

- **Top bar:** Cancel (leading) · Reset (centre, shown only once something changed) · Done (trailing, primary).
- **Canvas:** the crop window centred and fixed; the photo moves beneath it (drag to pan, pinch to zoom). Outside the window the photo shows dimmed by a scrim of the surface colour. A rule-of-thirds grid shows only while dragging or pinching; a finer grid while rotating.
- **Tool panel** (above the tabs):
  - **Ratio** — five chips. Each is the format's own shape as an outline, no name, drawn slightly wobbly: every edge pushed in or out by up to ~3 % of its length, seeded by the ratio's name (`StableHash.seed`, as `WobbleRenderer` does), so it never changes between renders. The selected chip is filled.
  - **Rotate** — the 90° button at the row's leading edge, then a ruler −45°…+45° with a degree readout. The ruler's ticks are hand-drawn: each tick's length, lean and position jittered slightly, seeded by its degree; taller every 5°, taller still every 15°, the 0° tick strongest. Ticks slide under a fixed needle in the primary colour. A haptic tick per degree, a snap at 0°, double-tap the ruler to reset the angle.
- **Tabs:** Ratio · Rotate, laid out so a third tab (Filters) fits without rework.

## Architecture

All new code under `apps/ios/Pebbles/Features/PebbleMedia/Crop/`.

| Unit | Purpose | Depends on |
|---|---|---|
| `CropRatio` | The five formats, their pair, closest-to-ratio | — |
| `CropGeometry` | Pure value: image size, ratio, quarter turns, angle, zoom, pan → min zoom, clamped pan, output transform | CoreGraphics only |
| `PhotoCropRenderer` | Draws the working image through the geometry's transform into a context the output's size | `CropGeometry` |
| `PhotoCropModel` | `@Observable` editor state; gesture deltas in, clamped geometry out; reset; "changed" flag | `CropGeometry` |
| `PhotoCropEditor` | The screen: top bar, canvas, tool panel, tabs | model, chips, ruler |
| `CropRatioChip`, `AngleRuler` | The hand-drawn controls | `StableHash` |

**Geometry.** For angle θ, window w×h and (quarter-turned) image W×H, minimum zoom is `max((w·cosθ + h·sinθ)/W, (w·sinθ + h·cosθ)/H)`; maximum is 8× that. The pan is clamped by mapping the window's four corners into the image's own axes and pushing them inside its bounds. During a drag the pan rubber-bands past the bound; on release it springs back (with Reduce Motion, it jumps).

**Working image.** On pick, the source is decoded once at 2048 px on the long edge with EXIF orientation baked (`CGImageSourceCreateThumbnailAtIndex`, as `ImagePipeline` does). The editor displays it and the renderer crops from it. The output is capped at 1024 px, so this leaves 2× zoom without loss.

**Pipeline.** `ImagePipeline` gains `process(_ image: CGImage) throws -> ProcessedImage`, sharing the opaque step, the quality loop and the metadata-free JPEG encode with today's `process(_:uti:)`. The thumb (420 px) is downscaled from the cropped image. `SnapUploadCoordinator` gains an ingest entry that takes the cropped `CGImage` plus the capture date read beforehand; generation, replacement and late-upload cleanup stay as they are.

**Flow wiring.** `CaptureFlowView` holds the pending pick (working image + capture date) and presents the editor before calling the coordinator; the step auto-advance keys off the snap as today. The camera path and `EditMomentSheet` take the same editor. A draft restore is unaffected (the editor is not persisted; a restored photo is already processed).

## Accessibility

- The ruler is an adjustable control: 1° per swipe, label "Angle, N degrees".
- Chips are buttons with the selected trait and a label ("4 by 5").
- The 90° button and Reset are labelled; the canvas exposes zoom in / zoom out as accessibility actions.
- Hit targets ≥ 44 pt; Dynamic Type on the readout and buttons.

## Testing (Swift Testing)

- `CropGeometry`: across sampled angles, ratios, quarter turns and pans, every window corner lies inside the image after clamping; min zoom is exactly coverage (a hair less fails).
- `CropRatio`: closest format for common camera ratios (3:4, 4:3, 9:16, 16:9, 1:1, 2:3); pair flip on 90°.
- `PhotoCropRenderer`: a four-colour quadrant image lands in the expected corners after a quarter turn and after a crop.
- `ImagePipeline.process(_ image:)`: output carries no EXIF/GPS/TIFF dictionaries and respects the byte caps.
- Reference images (CI-only gate, per the 2026-10-20 decision) for the editor in light and dark, each tab.

## Out of scope

Filters (next iteration, third tab) · editing already-saved photos · free crop · flip/mirror · the Android mirror (own issue) · new glyphs.

## Data safety

No change: all processing is on the device, nothing new is collected or sent.

## Delivery

Medium–large, iOS only. Three-part stack:

1. `CropRatio`, `CropGeometry`, `PhotoCropRenderer`, `ImagePipeline.process(_ image:)`, with their tests.
2. The editor screen: model, canvas, hand-drawn chips and ruler, tabs, previews and reference images.
3. Wiring into Capture, the camera and `EditMomentSheet`; the photo step's controls moved to the bottom.
