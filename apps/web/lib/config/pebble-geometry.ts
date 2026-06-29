// Geometry + size/polarity mapping for the pebble outline frame (web port of
// iOS PR #475). Single source of truth for:
//   - intensity → size      (1 → small,  2 → medium, 3 → large)
//   - positiveness → polarity (-1 → lowlight, 0 → neutral, 1 → highlight)
//   - the outline vs pebble viewBox dims, and the derived scale / aspect ratio.
//
// The outline silhouette viewBox is intentionally ~1.35× the composed pebble
// canvas, so the pebble must be scaled DOWN to sit inside the frame with
// ~12–13% margin per edge. Mirrors `PebbleOutlineGeometry.swift`.

export type Intensity = 1 | 2 | 3
export type Valence = -1 | 0 | 1

export type Size = "small" | "medium" | "large"
export type Polarity = "lowlight" | "neutral" | "highlight"

export const SIZE_BY_INTENSITY: Record<Intensity, Size> = {
  1: "small",
  2: "medium",
  3: "large",
}

export const POLARITY_BY_VALENCE: Record<Valence, Polarity> = {
  [-1]: "lowlight",
  0: "neutral",
  1: "highlight",
}

type Dimensions = { width: number; height: number }

// Outline frame viewBox dims, from the iOS SVG `width`/`height` attributes
// (`apps/ios/Pebbles/Resources/Outlines/*.svg`). Keep in sync with
// `pebble-outlines.ts`.
const OUTLINE_SIZE: Record<Size, Dimensions> = {
  small: { width: 337, height: 270 },
  medium: { width: 350, height: 350 },
  large: { width: 335, height: 400 },
}

// Composed pebble canvas dims, mirroring the engine layout
// (`packages/supabase/supabase/functions/_shared/engine`).
const PEBBLE_SIZE: Record<Size, Dimensions> = {
  small: { width: 250, height: 200 },
  medium: { width: 260, height: 260 },
  large: { width: 260, height: 310 },
}

// Linear scale to apply to the pebble render so it fits inside the larger
// outline viewBox. Per-size width ratio (small ≈ 0.742, medium ≈ 0.743,
// large ≈ 0.776); the matched per-size aspect ratios keep the pebble centred.
export function pebbleScale(size: Size): number {
  return PEBBLE_SIZE[size].width / OUTLINE_SIZE[size].width
}

// Outline aspect ratio (width / height) for the framed container to adopt.
export function outlineAspectRatio(size: Size): number {
  return OUTLINE_SIZE[size].width / OUTLINE_SIZE[size].height
}
