import type { Rect, Glyph } from "./types"

// ── Constants ─────────────────────────────────────────────────────

/** Stroke width matching the pebble outline (always 6px, unscaled). */
const STROKE_WIDTH = 6

// ── ViewBox parsing ────────────────────────────────────────────────

function parseViewBox(vb: string): { x: number; y: number; w: number; h: number } {
  const parts = vb.split(/\s+/).map(Number)
  return { x: parts[0], y: parts[1], w: parts[2], h: parts[3] }
}

// ── Public API ─────────────────────────────────────────────────────

/**
 * Scale, centre, and render a user-drawn glyph (mark) within a zone.
 *
 * The glyph is uniformly scaled to fit the zone while preserving its
 * aspect ratio. Strokes use `vector-effect="non-scaling-stroke"` so
 * the stroke width stays constant at 6px (matching the pebble outline)
 * regardless of the transform scale. Strokes use `stroke="black"` so
 * the downstream recolor step can apply the emotion colour uniformly.
 *
 * Pure function — no PRNG, DOM, or React dependencies.
 */
export function renderGlyphPaths(
  glyph: Glyph,
  zone: Rect,
): string {
  if (glyph.strokes.length === 0) return ""

  const vb = parseViewBox(glyph.viewBox)

  // Uniform scale preserving aspect ratio
  const scale = Math.min(zone.width / vb.w, zone.height / vb.h)

  // Translation to centre the scaled glyph in the zone
  const offsetX = zone.x + (zone.width - vb.w * scale) / 2 - vb.x * scale
  const offsetY = zone.y + (zone.height - vb.h * scale) / 2 - vb.y * scale

  const paths = glyph.strokes.map(
    (stroke) =>
      `<path d="${stroke.d}" stroke="black" ` +
      `stroke-width="${STROKE_WIDTH}" fill="none" ` +
      `vector-effect="non-scaling-stroke" ` +
      `stroke-linecap="round" stroke-linejoin="round"/>`,
  )

  return (
    `<g transform="translate(${offsetX}, ${offsetY}) scale(${scale})">` +
    paths.join("") +
    `</g>`
  )
}
