# Filter Lab on iOS — design

Issue #1675, iOS only, debug only. Approved in conversation on 2026-11-17. It is a proof of concept for the photo editor's future Filters tab (reserved by the 2026-11-15 crop design): before designing that tab, find out which presets and how few controls it needs.

## Why

The Filters tab should offer a handful of presets with very few tweaks (an intensity, grain), not the thirteen sliders of Photos.app. Nobody knows yet which presets those are. The maintainer already edits photos with Photos-style sliders; the lab records those edits on their own photos, measures each photo, and hands both to an analysis that derives correction rules and presets. The tab is designed from that result, not guessed.

## Decisions

1. **The lab is a plain debug screen** on Settings → Developer (`#if DEBUG`, beside the Spine and icon labs). It is throwaway UI: no Pebbles styling effort, no reference images. Only the filter pipeline under it is meant to last.
2. **Tuning happens on the maintainer's iPhone**, from a Debug build installed from Xcode (the Developer section does not exist in TestFlight builds). The phone's library and screen are the real conditions.
3. **Real Core Image, not a web approximation.** The looks recorded are exactly what the app will render.
4. **Every photo goes through the existing crop editor first**, so resize and rotate come for free and the lab works on the same 1024 px output that uploads.
5. **Thirteen sliders, Photos-style ranges.** Two-sided controls run −1…1, one-sided controls 0…1; zero everywhere is the identity.
6. **Three extras:** auto-enhance with red-eye (Apple's suggested correction), portrait refocus (background blur), and the aesthetics score (read-only, iOS 18+). Subject relighting is out.
7. **Effects are resolution-independent.** Every pixel radius (sharpen, blur, vignette, grain size) scales with the image's long edge, so the preview, the 1024 px render and a Mac render look the same.
8. **The grain is deterministic:** the same values on the same photo always give the same pixels.
9. **Photos never enter the repo.** pbbls is public. Test photos, exports and the analysis live on the phone, the Mac's scratchpad, or a folder the maintainer names.
10. **The analysis report is local HTML by default.** Publishing it as a private claude.ai artifact uploads the maintainer's photos, so it is asked for each time and never done by default.

## The controls

| Control | Range | Core Image | Mapping (starting values, tuned and frozen in Part 1) |
|---|---|---|---|
| Exposure | −1…1 | `CIExposureAdjust` | ±2 EV |
| Brilliance | −1…1 | `CIHighlightShadowAdjust` + `CIToneCurve` | Lifts shadows and pulls highlights while steepening the midtones. Apple does not publish its own; this is an approximation |
| Highlights | −1…1 | `CIHighlightShadowAdjust` (negative), `CIToneCurve` top points (positive) | −1 is `highlightAmount` 0; +1 lifts the 75 % and 100 % curve points |
| Shadows (lowlight) | −1…1 | `CIHighlightShadowAdjust.shadowAmount` | The value as is |
| Contrast | −1…1 | `CIColorControls.contrast` | 1 ± 0.5 |
| Saturation | −1…1 | `CIColorControls.saturation` | 0…2 |
| Vibrance | −1…1 | `CIVibrance` | The value as is |
| Tone (warmth) | −1…1 | `CITemperatureAndTint` | ±2000 K around 6500 K, positive is warmer |
| Tint | −1…1 | `CITemperatureAndTint` | Positive is magenta, negative green |
| Sharpness | 0…1 | `CISharpenLuminance` | Sharpness 0…0.8, radius scaled to the long edge |
| Vignette | −1…1 | `CIVignetteEffect` | Negative lightens the edges |
| Blur vignette | 0…1 | `CIMaskedVariableBlur` with a radial mask | Clear centre, blurred edges |
| Grain | 0…1 | `CIRandomGenerator`, luminance-only, blended | Overlay strength 0…0.25, grain size scaled to the long edge |
| Auto-enhance | on/off | `CIImage.autoAdjustmentFilters([.enhance, .redEye])` | Applied first; the filters Apple picks are recorded too |
| Refocus | 0…1 | `CIDepthBlurEffect` when the photo carries depth, else `CIMaskedVariableBlur` through a Vision mask | Person mask (`VNGeneratePersonSegmentationRequest`) when a face is found, subject mask (`VNGenerateForegroundInstanceMaskRequest`) otherwise; no mask found means the control is disabled for that photo |

**Order is fixed:** auto-enhance → exposure → brilliance → highlights/shadows → contrast/saturation → vibrance → tone/tint → sharpness → refocus → vignette → blur vignette → grain.

## Architecture

New code under `apps/ios/Pebbles/Features/PebbleMedia/Filters/` (lasting) and `.../Filters/Lab/` (debug, `#if DEBUG`).

| Unit | Purpose | Depends on |
|---|---|---|
| `FilterParams` | `Codable` value: the thirteen controls plus auto-enhance and refocus. `.neutral` is the identity | — |
| `FilterPipeline` | Pure: `(CIImage, FilterParams, FilterMasks) → CIImage`, in the fixed order. No UIKit, no logging. Compiles on macOS too | Core Image |
| `FilterMasks` | The subject mask, depth data, and Apple's auto-enhance filters for one photo, computed once | Vision, Core Image |
| `PhotoMeasures` | `Codable` facts about one photo: luminance percentiles (5/25/50/75/95), mean saturation, colour cast (mean a\*/b\*), face count, top five scene tags with confidence (`VNClassifyImageRequest`), has depth, aesthetics score (iOS 18+, else null), auto-enhance's chosen filters and values | Vision, Core Image |
| `FilterLabStore` | One session on disk in the app's `Documents/FilterLab/<session>/`: cropped photos and `looks.json`. Survives relaunches | FileManager |
| `FilterLabView` | Photo grid, import (photo picker → crop editor), share-sheet export | store, crop editor |
| `FilterLookEditor` | Live preview (press and hold for the original), the sliders and extras, the aesthetics score before and after, save look with a name, the photo's looks, duplicate and delete a look | pipeline, store |
| `FilterLabExport` | Builds `filter-lab-<yyyy-MM-dd>.zip` | store, pipeline |

The preview renders through one shared `CIContext` at screen size, coalescing slider changes so it never queues more than one render.

## The export

`filter-lab-<date>.zip`:

- `photos/<photoId>.jpg`: the cropped 1024 px input.
- `renders/<photoId>/<lookId>.jpg`: each look as the phone rendered it, the reference for the Mac render.
- `looks.json`: `{ "version": 1, "photos": [ { "id", "file", "width", "height", "looks": [ { "id", "name", "createdAt", "params": { … } } ] } ] }`. Timestamps are ISO 8601 in whole seconds.
- `measures.json`: `{ "version": 1, "measures": { "<photoId>": { … } } }`.

## The analysis (on the Mac, never committed)

Run from the scratchpad once the maintainer names the folder holding the zip.

1. **Check the Mac render.** `FilterPipeline.swift` is compiled for macOS with a small command-line wrapper. Each look is re-rendered and compared with the phone's render. Any look that differs visibly stops the analysis until the gap is explained.
2. **Correction rules.** Each control is regressed against the photo measures across all looks. A rule is kept only when it is clear (R² ≥ 0.5 across at least six photos) and reads in plain words ("on photos whose median brightness is under 0.35, exposure rises by about …").
3. **Presets.** Subtract the rules' corrections, normalise what is left (grain apart), and cluster it into 3 to 6 groups, the count chosen by silhouette score. Each preset is its cluster's centre: 100 % intensity is the centre, 0 % is the corrected photo. Grain stays a separate control at its median per preset.
4. **Scene defaults.** Where a preset is chosen far more often for a scene tag or when faces are present, it becomes that scene's suggested first preset.
5. **Extras.** Compare the looks with auto-enhance's suggestion and the aesthetics score before and after each look, as hints only.
6. **Report.** One page: the rules in plain words; a grid of every photo at each preset at 50 % and 100 %, rendered by the Mac pipeline; the maintainer's own looks next to the nearest preset. Local HTML; published as a private artifact only on an explicit yes.

**Enough data:** about 15 photos and 30 looks, spread across scenes (people, landscape, food, night, indoor). Below that, the report says the rules are too weak to keep.

## Testing (Swift Testing)

- `FilterPipeline`: neutral values return the input within a small tolerance; each control moves the image in its expected direction (exposure up brightens the mean, tone up raises red over blue, saturation down lowers chroma); the same grain values twice give identical pixels; a 512 px and a 1024 px render of the same values match once downscaled.
- `FilterParams`: round-trips through JSON, including a payload with missing keys (they default to neutral).
- `PhotoMeasures`: luminance percentiles of a known grey ramp.
- The lab screens: no reference images (debug UI).

## Out of scope

The Filters tab itself and its preset UI (designed from the report) · subject relighting · the Android mirror · editing already-saved photos · any upload or database change.

## Data safety

No change: the lab is a debug build feature, everything stays on the device until the maintainer exports it by hand, and nothing is sent to a service.

## Delivery

Medium–large, iOS only, one issue. Two-part stack:

1. `FilterParams`, `FilterPipeline`, `FilterMasks`, `PhotoMeasures`, with their tests.
2. The lab: `FilterLabStore`, `FilterLabView`, `FilterLookEditor`, `FilterLabExport`, the Developer row.

The analysis script and the macOS wrapper stay in the scratchpad. If the approach proves out, they move into the repo with the Filters tab's own spec, without any photos.

The lab is a debug screen, so it gets no Arkaik node.
