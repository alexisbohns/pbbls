import type { Rect, Glyph } from "./types"

// ── Constants ─────────────────────────────────────────────────────

/** Stroke width matching the pebble outline in SVG coordinate space. */
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
 * aspect ratio. The stroke width is pre-divided by the scale factor so
 * it resolves to 6px in SVG coordinates after the transform — matching
 * the pebble outline at every display size. Strokes use `stroke="black"`
 * so the downstream recolor step can apply the emotion colour uniformly.
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

  // Pre-divide so the transform scale yields STROKE_WIDTH in SVG coords
  const adjustedWidth = STROKE_WIDTH / scale

  const paths = glyph.strokes.map(
    (stroke) =>
      `<path d="${stroke.d}" stroke="black" ` +
      `stroke-width="${adjustedWidth}" fill="none" ` +
      `stroke-linecap="round" stroke-linejoin="round"/>`,
  )

  return (
    `<g transform="translate(${offsetX}, ${offsetY}) scale(${scale})">` +
    paths.join("") +
    `</g>`
  )
}
