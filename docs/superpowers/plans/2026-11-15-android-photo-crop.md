# Photo crop and rotate on Android — implementation notes

Issue #1659, the Android mirror of #1654. The design and its decisions D1–D10 are the iOS spec's
(`docs/superpowers/specs/2026-11-15-ios-photo-crop-design.md`); this note records where each piece
landed on Android and what differs by construction, not by choice.

## Where it lives

| Piece | Android | iOS source |
|---|---|---|
| Formats | `core/model/crop/CropRatio.kt` | `CropRatio.swift` |
| Geometry (min zoom, clamp, rubber band, edits) | `core/model/crop/CropGeometry.kt` (pure doubles, plain JVM) | `CropGeometry.swift` |
| Renderer | `core/data/PhotoCropRenderer.kt` (`Canvas` + `Bitmap`, the source's colour space when 8-bit can carry it) | `PhotoCropRenderer.swift` |
| Working image | `core/data/CropSource.kt`, `ImagePipeline.decodeUpright` (2048 px, EXIF baked) | `CropSource.swift`, `decodeOriented` |
| Cropped pipeline | `ImagePipeline.process(Bitmap)`, `SnapProcessor.processCropped` | `process(_ image:)`, `ingest(cropped:)` |
| Seeded wobble | `core/common/SeededGenerator.kt` (same SplitMix64 over FNV-1a: same wobble as iOS) | `SeededGenerator.swift` |
| Pick gate | `features/path/crop/CropPickGate.kt` | `CropPickGate.swift` |
| Editor state | `features/path/crop/PhotoCropState.kt`, held in a `CropSession` by the flow's ViewModel | `PhotoCropModel.swift` |
| Editor UI | `PhotoCropEditor`, `CropCanvas`, `AngleRuler`, `CropRatioChip`, `WobblyRectangle`, `CropToolGroup` | same names |

## Android-specific choices

- **The editor is an overlay the ViewModel opens, not a destination.** A `Bitmap` cannot ride a
  serializable `PebblesKey`, and the session must survive a rotation: the ViewModel holds the
  `CropSession` in its UiState and the screen draws `PhotoCropEditor` over the flow. Back while it is
  open is its Cancel (`onSystemBack`). The pick and the camera are other activities and have returned by
  the time a read lands, so the only thing the editor waits for is Capture's close dialog
  (`CropPickGate.presentation`). Process death drops an open editor, as iOS does (the editor is not part
  of the draft).
- **Done closes the editor at once**; the render and the processing run in the ViewModel while the
  library tile shows its busy state, and the gate token is held until the coordinator has the photo.
- **The tool switch is the native M3 Expressive connected `ToggleButton` group** (`ButtonGroupDefaults`
  connected shapes), not a port of the iOS drawing. A third tool is one more `CropTool` entry.
- **The ruler's ticks fade per tick** instead of through a gradient mask: `DstIn` inside an offscreen
  layer is unreliable on HWUI (`apps/android/CLAUDE.md`, Path rendering). Ticks and drag are physical,
  not mirrored in RTL: the ruler measures a clockwise turn.
- **The camera's capture date is the shutter**, as before; a library pick's is the EXIF date read
  before the decode. Both are applied on Done, so Cancel leaves the date alone.
- **Home's one-shot camera moment is untouched** (`CameraPhotos.process`).

## Tests

Ported as oracles: `CropRatioTest`, `CropGeometryTest` (coverage across formats × turns × angles × pans
× zooms, tight minimum zoom), `CropPickGateTest`, `PhotoCropStateTest`, `WobblyRectangleTest`,
`PhotoCropRendererTest` (quadrants, borders always filled), `ImagePipelineCroppedTest` (caps, no APP1
segment, byte caps on noise, 2048 decode cap). Capture and Edit moment ViewModel tests pick through the
editor, plus new cases for Cancel, back, the close dialog, superseded and late reads. Screenshot
previews: `PhotoCropScreenshots` (Ratio and Rotate, light and dark); references come from CI.

## Lessons learned

(Filled after the device pass.)
