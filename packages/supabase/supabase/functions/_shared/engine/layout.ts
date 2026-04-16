/**
 * Pebble Engine · Layout Config
 *
 * Resolves the glyph slot position within a pebble canvas
 * based on (size, valence). Config-driven — tuning positions
 * means editing this file, not engine logic.
 *
 * Runs BOTH client-side (live preview) and server-side (Edge Function).
 * Pure function. No DOM. No side effects.
 */

import type {
  PebbleSize,
  PebbleValence,
  PebbleLayoutConfig,
  GlyphSlot,
  CanvasSize,
} from "./types.ts";

// ── Canvas Sizes ────────────────────────────────────────────

const CANVAS: Record<PebbleSize, CanvasSize> = {
  small:  { width: 250, height: 200 },
  medium: { width: 260, height: 260 },
  large:  { width: 260, height: 310 },
};

// ── Glyph Target Sizes ─────────────────────────────────────

const GLYPH_SIZE: Record<PebbleSize, number> = {
  small:  140,
  medium: 150,
  large:  160,
};

// ── Glyph Position Config ───────────────────────────────────
//
// Origin = top-left of the glyph bounding box.
// Values from the spec (Notion: Glyph System & Pebble Engine · Spec).
//
// Format: [x, y] in px, pre-computed from the percentage rules.

const GLYPH_POSITION: Record<PebbleSize, Record<PebbleValence, { x: number; y: number }>> = {
  small: {
    highlight: { x: 37.5, y: 30 },   // L 15%, Y center
    neutral:   { x: 25,   y: 30 },   // L 10%, Y center
    lowlight:  { x: 30,   y: 30 },   // L 12%, Y center
  },
  medium: {
    highlight: { x: 26,   y: 84 },   // L 10%, B 10%
    neutral:   { x: 26,   y: 55 },   // L 10%, Y center
    lowlight:  { x: 39,   y: 26 },   // L 15%, T 10%
  },
  large: {
    highlight: { x: 50,   y: 75 },   // X center, Y center
    neutral:   { x: 26,   y: 88 },   // L 10%, B 20%
    lowlight:  { x: 26,   y: 75 },   // L 10%, Y center
  },
};

// ── Composed Layout Map ─────────────────────────────────────

const LAYOUT: Record<PebbleSize, Record<PebbleValence, PebbleLayoutConfig>> = (() => {
  const map = {} as Record<PebbleSize, Record<PebbleValence, PebbleLayoutConfig>>;
  for (const size of ["small", "medium", "large"] as PebbleSize[]) {
    map[size] = {} as Record<PebbleValence, PebbleLayoutConfig>;
    for (const valence of ["highlight", "neutral", "lowlight"] as PebbleValence[]) {
      map[size][valence] = {
        canvas: CANVAS[size],
        glyphSlot: {
          size: GLYPH_SIZE[size],
          ...GLYPH_POSITION[size][valence],
        },
      };
    }
  }
  return map;
})();

// ── Public API ──────────────────────────────────────────────

/**
 * Resolve the full layout config for a given pebble size and valence.
 *
 * Returns canvas dimensions and the glyph slot (size + position).
 * If a layoutOverride is provided, it takes precedence entirely.
 *
 * @param size     — "small" | "medium" | "large"
 * @param valence  — "highlight" | "neutral" | "lowlight"
 * @param override — Optional full override (from workbench tuning).
 */
export function resolveLayout(
  size: PebbleSize,
  valence: PebbleValence,
  override?: PebbleLayoutConfig
): PebbleLayoutConfig {
  if (override) return override;
  return LAYOUT[size][valence];
}

/**
 * Get only the glyph slot for a given size + valence.
 * Convenience wrapper when you just need position info.
 */
export function resolveGlyphSlot(
  size: PebbleSize,
  valence: PebbleValence,
  override?: GlyphSlot
): GlyphSlot {
  if (override) return override;
  return LAYOUT[size][valence].glyphSlot;
}

/**
 * Get the canvas size for a given pebble size.
 */
export function resolveCanvas(size: PebbleSize): CanvasSize {
  return CANVAS[size];
}

/**
 * Recompute a glyph position from percentage rules.
 * Useful when canvas or glyph sizes change and you need
 * to regenerate the pixel values from the original intent.
 *
 * @param canvas     — Canvas dimensions.
 * @param glyphSize  — Glyph slot side length.
 * @param xRule      — X positioning rule.
 * @param yRule      — Y positioning rule.
 */
export function computeGlyphPosition(
  canvas: CanvasSize,
  glyphSize: number,
  xRule: { type: "left-percent"; percent: number } | { type: "center" },
  yRule: { type: "top-percent"; percent: number } | { type: "bottom-percent"; percent: number } | { type: "center" }
): { x: number; y: number } {
  let x: number;
  if (xRule.type === "center") {
    x = (canvas.width - glyphSize) / 2;
  } else {
    x = canvas.width * (xRule.percent / 100);
  }

  let y: number;
  if (yRule.type === "center") {
    y = (canvas.height - glyphSize) / 2;
  } else if (yRule.type === "top-percent") {
    y = canvas.height * (yRule.percent / 100);
  } else {
    // bottom-percent: offset from bottom
    y = canvas.height - canvas.height * (yRule.percent / 100) - glyphSize;
  }

  return { x: Math.round(x * 100) / 100, y: Math.round(y * 100) / 100 };
}

// ── Export raw config for introspection / serialization ─────

export { LAYOUT, CANVAS, GLYPH_SIZE, GLYPH_POSITION };
