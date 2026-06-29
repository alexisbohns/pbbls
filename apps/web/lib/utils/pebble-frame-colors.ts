import type { EmotionPalette } from "@/lib/data/useEmotionPalettes"
import type { Intensity } from "@/lib/config/pebble-geometry"

// Render-ready colors for the pebble outline frame. Web port of iOS
// `EmotionPalette.pebbleFrameColors(forIntensity:)`.
//
// Palette hex columns are stored `#RRGGBBAA`. SVG `fill`/`stroke` only render
// 6-digit hex reliably, so we split each value into a 6-digit color plus a
// separate 0–1 opacity (carried on the backdrop view) — same split iOS makes.
export type PebbleFrameColors = {
  // 6-digit `#RRGGBB` for the outline backdrop fill.
  fillColor: string
  // The fill color's alpha (0–1), applied as backdrop opacity. Large pebbles
  // fill with opaque `primary`; small/medium fill with `surface`, which the
  // palette seeds at a low alpha for a faint silhouette wash.
  fillOpacity: number
  // 6-digit `#RRGGBB` for the pebble strokes (via PebbleVisual `strokeOverride`).
  strokeColor: string
}

// `#RRGGBBAA` → `#RRGGBB`. 6-digit and unrecognized input pass through.
function rgbHex(hex: string): string {
  return hex.length === 9 ? hex.slice(0, 7) : hex
}

// Alpha byte of an `#RRGGBBAA` string as a 0–1 number. Returns 1 for 6-digit
// hex or any input that fails to parse.
function alphaComponent(hex: string): number {
  if (hex.length !== 9) return 1
  const byte = Number.parseInt(hex.slice(7, 9), 16)
  return Number.isNaN(byte) ? 1 : byte / 255
}

// Single source of truth for the intensity → role mapping (theme-neutral):
//   - intensity 3: `light` stroke + opaque `primary` fill.
//   - intensity 1 / 2: `secondary` stroke + low-alpha `surface` fill.
export function pebbleFrameColors(
  palette: EmotionPalette,
  intensity: Intensity,
): PebbleFrameColors {
  if (intensity === 3) {
    return {
      fillColor: rgbHex(palette.primary_color),
      fillOpacity: alphaComponent(palette.primary_color),
      strokeColor: rgbHex(palette.light_color),
    }
  }
  return {
    fillColor: rgbHex(palette.surface_color),
    fillOpacity: alphaComponent(palette.surface_color),
    strokeColor: rgbHex(palette.secondary_color),
  }
}
