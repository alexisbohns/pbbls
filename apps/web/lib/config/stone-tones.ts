// The lit stone's tone slots (#1052), as CSS colours over the five palette
// variables each stone declares inline: --p primary, --s secondary, --l light,
// --sh shaded, --d dark.
//
// They are the final mock's, not iOS `StoneTones` (spec D4): each mix below
// reproduces the mock's baked hex for every emotion it drew to within 1/255.
// If the palette is redefined, iOS is re-tuned against these, not the reverse.

import type { Polarity } from "./pebble-geometry"

export type ToneStop = { offset: number; color: string }

/** The body's radial ramp, lit from the upper left. */
export const BODY_RAMP: Record<Polarity, ToneStop[]> = {
  highlight: [
    { offset: 0, color: "color-mix(in srgb, var(--s) 66%, var(--l))" },
    { offset: 0.4, color: "var(--p)" },
    { offset: 1, color: "color-mix(in srgb, var(--sh) 70%, var(--d))" },
  ],
  // Flatter than the mock's: a matte pebble has no hot spot.
  neutral: [
    { offset: 0, color: "color-mix(in srgb, var(--s) 72%, var(--l))" },
    { offset: 0.5, color: "var(--s)" },
    { offset: 1, color: "color-mix(in srgb, var(--p) 72%, var(--s))" },
  ],
  lowlight: [
    { offset: 0, color: "color-mix(in oklab, var(--s) 31%, var(--sh))" },
    { offset: 0.5, color: "color-mix(in oklab, var(--sh) 53%, var(--d))" },
    { offset: 1, color: "var(--d)" },
  ],
}

export type Paint = {
  color: string
  opacity: number
  /** Stroke width in the layer's own units; absent for a fill. */
  width?: number
  /** Offset in the layer's own units, for a lip or a groove's far wall. */
  dx?: number
  dy?: number
  glow?: boolean
}

/** The glyph: a lit lip under the ink, both a constant 1.25 px line. */
export const GLYPH_TONES: Record<Polarity, { lip: Paint; ink: Paint }> = {
  highlight: {
    lip: { color: "var(--l)", opacity: 0.6 },
    ink: { color: "color-mix(in srgb, var(--sh) 68%, #000)", opacity: 0.85 },
  },
  neutral: {
    lip: { color: "var(--l)", opacity: 0.75 },
    ink: { color: "var(--sh)", opacity: 0.9 },
  },
  lowlight: {
    lip: { color: "var(--s)", opacity: 0.5 },
    ink: { color: "#000", opacity: 0.66 },
  },
}

/**
 * The seed's veins, by material: a gem's light seams, a river pebble's groove
 * (a lit bed and its shadowed far wall), lava's glowing crack.
 */
export const VEIN_TONES: Record<Polarity, Paint[]> = {
  highlight: [{ color: "var(--l)", opacity: 0.3, width: 3 }],
  neutral: [
    { color: "var(--l)", opacity: 0.14, width: 9 },
    { color: "var(--sh)", opacity: 0.2, width: 2.4, dx: 1.5, dy: 3.5 },
  ],
  lowlight: [
    { color: "var(--p)", opacity: 0.5, width: 12, glow: true },
    { color: "color-mix(in srgb, var(--p) 50%, var(--s))", opacity: 0.9, width: 4.18 },
  ],
}

/** The seed's fossil: a ghost in the gem, raised in the pebble, sunk in lava. */
export const FOSSIL_TONES: Record<Polarity, Paint[]> = {
  highlight: [{ color: "var(--l)", opacity: 0.18 }],
  neutral: [
    { color: "var(--l)", opacity: 0.35, dx: -1.5, dy: -2 },
    { color: "var(--p)", opacity: 0.3 },
  ],
  lowlight: [{ color: "var(--d)", opacity: 0.5 }],
}

/** The inner shade along the edge (stroke width in px), blurred by `ao`. */
export const EDGE_SHADE: Record<Polarity, Paint> = {
  highlight: { color: "var(--d)", opacity: 0.5, width: 5 },
  neutral: { color: "var(--d)", opacity: 0.26, width: 6 },
  lowlight: { color: "var(--d)", opacity: 0.8, width: 6 },
}

/** How strongly a press glints: gem, pebble, lava. A matte pebble barely does. */
export const GLINT_STRENGTH: Record<Polarity, number> = {
  highlight: 1,
  neutral: 0.3,
  lowlight: 0.34,
}

/**
 * The surface's grain, by material: lava harsh and pitted, the river pebble a
 * faint tooth that keeps it matte, the gem polished clean.
 */
export const GRAIN_STRENGTH: Record<Polarity, number> = {
  highlight: 0,
  neutral: 0.14,
  lowlight: 0.32,
}

/** Names the palette slots a recipe dot or facet paints with. */
export const SLOT_COLOR = {
  light: "var(--l)",
  shaded: "var(--sh)",
  dark: "var(--d)",
  white: "#fff",
} as const

export type Slot = keyof typeof SLOT_COLOR

/**
 * A moment with no emotion yet (the moments split): a blank river pebble in a
 * grey ramp. The values are the shame category's, the palette's own grey.
 */
export const BLANK_PALETTE = {
  primary_color: "#868686",
  secondary_color: "#B9B9B9",
  light_color: "#F3F3F3",
  shaded_color: "#5E5E5E",
  dark_color: "#1B1B1B",
} as const

/** The five tones a stone reads; an `EmotionPalette` satisfies it. */
export type StonePalette = {
  primary_color: string
  secondary_color: string
  light_color: string
  shaded_color: string
  dark_color: string
}

/** The palette as the inline variables the tone slots above read. */
export function stonePaletteVars(palette: StonePalette): Record<"--p" | "--s" | "--l" | "--sh" | "--d", string> {
  return {
    "--p": palette.primary_color,
    "--s": palette.secondary_color,
    "--l": palette.light_color,
    "--sh": palette.shaded_color,
    "--d": palette.dark_color,
  }
}
