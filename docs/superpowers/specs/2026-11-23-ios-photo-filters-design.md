# Photo filters on iOS — design

Issue #1680, iOS only. Approved in conversation on 2026-11-23. It follows the Filter Lab (#1675, spec `2026-11-17-ios-filter-lab-design.md`). The lab recorded 26 looks on 14 of the maintainer's photos. Its analysis gave seven presets, and the maintainer settled on the controls. The Android mirror is a separate issue.

## Why

The photo editor that opens after every new photo (spec `2026-11-15-ios-photo-crop-design.md`) can frame and straighten. It cannot change how the photo looks. Photos.app hands people thirteen sliders. The lab showed that one person's looks fall into a few directions, each applied at some strength, plus three habits that sit beside the look: vignette, blur vignette and grain. So the editor offers presets with four knobs, and keeps the sliders one tab further for those who want them.

## Decisions

1. **Four tools, four tabs:** Reframe · Rotate · Filter · Adjust. Reframe is today's Ratio tab under its new name. The tab bar already makes room for more tabs (`CropToolGroup`).
2. **Filter = one preset + four knobs.** The knobs are intensity, vignette, blur and grain.
   - **Intensity** scales the preset: 0 is the photo untouched, 1 is the preset as designed.
   - **Vignette, blur and grain** are separate from the preset and the intensity. Changing preset or intensity leaves them alone.
3. **Seven presets**, four in colour and three in black and white, values frozen below. The row starts with **Original** (no preset).
4. **Adjust = the lab's controls, applied on top of the filter.** Every Adjust slider starts at 0 and adds to the preset × intensity value, clamped to the control's range. Adjust shows only the ten tone and colour controls, plus refocus. Vignette, blur and grain are never repeated there.
5. **Auto-enhance is dropped.** It was never used in the lab. **Refocus stays**, last in Adjust, disabled when no subject is found (as in the lab).
6. **The look is baked into the uploaded JPEG.** Nothing new is stored: no schema, RPC, web or Android display change. As with the crop, a saved photo cannot be re-filtered.
7. **Double-tap a knob or slider to reset it** to its default. This is the lab's learning; the editor is a full-screen cover, so there is no swipe-back to fight.
8. **Reset in the top bar resets the tool of the open tab.** It shows only once that tool has changed. Today it resets the crop, which stays true on Reframe and Rotate.

## The presets

Values are the pipeline's control values at intensity 1. Any control not listed is 0. They come from the cluster centres of the lab's looks. Vivid's warmth was softened from the raw mean (Cold Sunset's −1 dominated it) and its vibrance raised to match its intent.

| Preset (en · fr) | Built from | Exposure | Brilliance | Highlights | Shadows | Contrast | Saturation | Vibrance | Warmth (tone) | Sharpness |
|---|---|---|---|---|---|---|---|---|---|---|
| Everyday · Quotidien | 10 colour looks | 0.08 | −0.05 | 0 | −0.19 | 0.06 | −0.09 | 0.16 | −0.08 | 0.20 |
| Vivid · Vif | Nature Focus, Blue Sky, Cold Sunset | 0.13 | 0.22 | −0.04 | −0.22 | 0.11 | 0.10 | 0.30 | −0.25 | 0.22 |
| Soft · Doux | Indoor Moody, Warm/Cold Sunset | 0.23 | 0.18 | −0.26 | 0.09 | 0 | 0 | −0.08 | −0.29 | 0.30 |
| Lift · Lumineux | Abyssal Blue, Intense Night Portrait | 0.35 | 0.30 | 0.36 | −0.15 | 0.09 | −0.21 | 0.22 | −0.10 | 0.38 |
| Mono · Mono | 10 black and white looks | 0.21 | 0.14 | 0.25 | −0.08 | 0.12 | −1 | 0 | 0 | 0.29 |
| Mono Deep · Mono profond | Nature Focus Black, Intense Night Portrait Black | 0.18 | 0.35 | 0.38 | −0.32 | 0.36 | −1 | 0 | 0 | 0.43 |
| Memory · Souvenir | Old Memory, Flat Monochrome | 0.20 | 0.15 | −0.08 | 0.15 | −0.08 | −1 | 0 | 0.15 | 0.10 |

**Saturation −1 scales like the rest.** Mono at intensity 0.5 is a half-desaturated photo. That is the honest reading of "0 is untouched, 1 is the preset", and it gives a muted colour look on the way to black and white. If on-device use shows people want black and white at any intensity, the mono presets pin saturation at −1 instead: a one-line change, decided in the device pass.

## The knobs

| Knob | Range | Default when a preset is first picked | Pipeline control |
|---|---|---|---|
| Intensity | 0…1 | 1 | Scales the preset's values |
| Vignette | 0…1 | 0.1 | `vignette` (the lab's lightening side, below 0, is dropped) |
| Blur | 0…1 | 0.2 | `blurVignette` |
| Grain | 0…1 | 0.4 | `grain` |

The defaults are the medians of the lab's looks, where the extras were used in 23–25 of 26 looks. **Picking a preset for the first time** sets each extra to its default only if the person has not moved that extra yet. On Original, every extra starts at 0. The lab found grain and blur mild at their maximum. Their mapping to the pipeline may grow stronger: tuned on the device in the last part, keeping 0 as the identity.

## Adjust

Ten sliders in pipeline order: exposure, brilliance, highlights, shadows, contrast, saturation, vibrance, warmth, tint, sharpness, then refocus. Ranges are the lab's (−1…1, sharpness and refocus 0…1). Each slider shows the person's own offset, not the resolved value, so it starts at 0 whatever the preset. The resolved value is `clamp(preset[k] × intensity + adjust[k], range(k))`.

## Layout

The editor's layout stays as it is: top bar, canvas, tool panel, tabs.

- **Canvas on Filter and Adjust:** the cropped result, no scrim or grid, gestures off: framing happens on Reframe. Touch and hold shows the photo without the look, as in the lab.
- **Filter panel:**
  - A horizontal row of presets: the cropped photo at about 64 pt with the preset at intensity 1 and no extras, its name under it in sentence case. The selected tile rounds into a circle and its name takes the primary colour (device pass, 2026-11-24).
  - Below the row, the four knobs in one row as circular dials (device pass, 2026-11-24): a tonal disc with a 270° track open at the bottom, the value as a primary arc ending in a dot, the value ×100 inside as a bare number, the name below. Drag up to turn one up, down to turn it down; a light tick every 5 %.
  - On Original, intensity is disabled.
- **Adjust panel:** the eleven controls as one horizontally scrolling row of the same knobs (device pass, 2026-11-24), so the photo stays large enough to judge the result. A two-sided control (−1…1) fills from its top-centre zero and reads signed (+20, −35). Knobs turn on a vertical drag only, so a sideways drag scrolls the row. The refocus note sits under the row.
- **Tab glyphs:** Reframe `rectangleDashed`, Rotate `setSquare`, Filter `filterBox` (reserved for it by the crop spec), Adjust `slider`.
- **Tab labels (device pass, 2026-11-24):** four labels do not fit side by side, so only the selected tab shows its name; it takes the rest of the row, the others are icon-only segments (their name stays their accessibility label).

## Architecture

New code under `apps/ios/Pebbles/Features/PebbleMedia/Filters/`, next to the lab's pipeline.

| Unit | Purpose | Depends on |
|---|---|---|
| `FilterPreset` | The seven presets: id, localized name, values at intensity 1 | `FilterParams` |
| `PhotoLook` | Pure value: the preset (or none), the four knobs, the Adjust offsets. `resolve() → FilterParams`. `.untouched` resolves to `.neutral` | `FilterPreset`, `FilterParams` |
| `PhotoLookModel` | `@Observable` editor state for the two tabs: the look, the preview and the thumbnails, coalesced rendering, masks loaded lazily | `PhotoLook`, `FilterPipeline`, `FilterMasks` |
| `FilterToolPanel`, `AdjustToolPanel` | The two panels | model |

**Preview.** The working image (2048 px) is cropped through the current geometry by `PhotoCropRenderer`, at the upload size (1024 px), whenever Filter or Adjust opens after the crop changed. The preview is therefore the upload itself. It then runs through `FilterPipeline` on the shared `CIContext`, with slider changes coalesced so at most one render is queued, as `FilterLookModel` does in the lab. The thumbnails render once from the same cropped image. The pipeline scales every radius to the long edge, and the lab checked that a Mac render and a phone render match within 0.34 %, so the preview matches the upload.

**Masks.** `FilterMasks` runs only when refocus first moves above 0, on the cropped image. Until then refocus shows "Finding the subject…".

**Done.**
1. `PhotoCropRenderer` crops at 1024 px.
2. If the look is not untouched, `FilterPipeline.apply` and `render` run in the cropped image's own colour space, as the lab does (a Display P3 photo stays P3).
3. `ImagePipeline.process(_ image: CGImage)` runs as today; the capture-date path is unchanged.

**The lab stays** a debug screen on its own `FilterParams` and keeps working. `FilterPipeline`, `FilterMasks` and `FilterParams` move out of draft status: they are now product code (the lab spec already called them "meant to last").

## Accessibility

- Preset tiles are buttons with the selected trait and the preset's name as their label.
- Every knob and slider is adjustable, with a label and a value ("Grain, 40 percent"; "Exposure, plus 0.2"), and a "Reset" action.
- The hold-for-original gesture has an accessibility action "Show original".
- Hit targets ≥ 44 pt; Dynamic Type on labels and readouts; Reduce Motion skips the preview cross-fade.

## Localization

Tab names (Reframe, Filter, Adjust), the seven preset names, knob and slider labels: all in `Localizable.xcstrings`, en and fr. Ratio's catalog entry is renamed to Reframe (fr « Cadrer »).

## Testing (Swift Testing)

- `PhotoLook.resolve`:
  - `.untouched` gives `.neutral`;
  - intensity 0 with any preset and no Adjust gives `.neutral` plus the extras;
  - intensity 1 gives the preset's values;
  - an Adjust offset adds, and the sum clamps to the range;
  - extras never change with preset or intensity.
- Picking a preset fills only the untouched extras.
- Every preset at intensity 1 is within each control's range. Every preset moves the image's mean in its own direction: mono presets drop chroma to near 0, Vivid raises it.
- Reference images (CI-only gate, per the 2026-10-20 decision): the Filter and Adjust tabs in light and dark, with a preset selected.

## Out of scope

The Android mirror (own issue). Re-filtering saved photos. User-made or saved presets. Scene-based preset suggestions and automatic corrections: the lab's one rule, that exposure falls as highlight brightness rises, explained half the variation over 14 photos, too weak to ship. Auto-enhance. Subject relighting. Changing the lab.

## Data safety

No change. The look is applied on the device to the photo the person already chose to upload. Vision runs on the device. No new data, purpose or service.

## Delivery

Medium–large, iOS only, one issue. It depends on the Filter Lab stack (#1676, #1677), which carries the pipeline. A three-part stack, from `main` once that stack is merged:

1. **`FilterPreset` and `PhotoLook`** with their tests (pure, no UI).
2. **The Filter tab:**
   - the tab rename to Reframe;
   - `PhotoLookModel`, `FilterToolPanel`;
   - the preview on the canvas;
   - Done applying the look;
   - strings and reference images.
3. **The Adjust tab**, the lazy masks for refocus, and the device pass. The device pass tunes the grain and blur mappings and settles whether mono presets pin saturation; any change goes back into this spec's tables.

The photo editor's Arkaik view gains the Filter and Adjust tools when Part 2 is picked up.
