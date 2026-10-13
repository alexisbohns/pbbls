import type { Rect } from "./types"

// ── Constants ─────────────────────────────────────────────────────

/** Stroke width matching the pebble outline in SVG coordinate space. */
export const GLYPH_OUTLINE_WIDTH = 6

// ── ViewBox parsing ────────────────────────────────────────────────

function parseViewBox(vb: string): { x: number; y: number; w: number; h: number } | null {
  const parts = vb.trim().split(/[\s,]+/).map(Number)
  if (parts.length !== 4 || parts.some((n) => !Number.isFinite(n))) return null
  const [x, y, w, h] = parts
  if (w <= 0 || h <= 0) return null
  return { x, y, w, h }
}

// ── Public API ─────────────────────────────────────────────────────

export type GlyphPlacement = {
  /** `translate(…) scale(…)` taking glyph space into the template's space. */
  transform: string
  /** Uniform scale of `transform`. */
  scale: number
  /** The glyph-space stroke width that lands at the outline's 6 after the transform. */
  strokeWidth: number
}

/**
 * Where a glyph sits in a template's glyph zone: uniformly scaled to fit the
 * zone, aspect ratio preserved, centred. Null for an unreadable viewBox, which
 * the caller draws as no glyph.
 *
 * Geometry only, never markup. The glyph's stroke data is somebody's jsonb, and
 * since the moments split it reaches other people (a connection's tile, the
 * anonymous share page), so it is only ever handed to React as a `<path d>`
 * attribute (`PebbleGlyphStrokes`), never spliced into an SVG string (#829).
 *
 * Pure function — no PRNG, DOM, or React dependencies.
 */
export function placeGlyph(viewBox: string, zone: Rect): GlyphPlacement | null {
  const vb = parseViewBox(viewBox)
  if (!vb) return null

  const scale = Math.min(zone.width / vb.w, zone.height / vb.h)
  const offsetX = zone.x + (zone.width - vb.w * scale) / 2 - vb.x * scale
  const offsetY = zone.y + (zone.height - vb.h * scale) / 2 - vb.y * scale

  return {
    transform: `translate(${offsetX} ${offsetY}) scale(${scale})`,
    scale,
    // Pre-divided so the transform scale yields the outline width in SVG coords.
    strokeWidth: GLYPH_OUTLINE_WIDTH / scale,
  }
}
