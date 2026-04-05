import type { Rect, Glyph } from "./types"

// ── Constants ──────────────────────────────────────────────────────

/** Inset colour for the shadow layer of the emboss effect. */
const SHADOW_COLOR = "rgba(0,0,0,0.25)"

/** Offset in viewBox units for the emboss shadow. */
const EMBOSS_OFFSET = 0.5

/** Opacity applied to the main glyph strokes. */
const STROKE_OPACITY = 0.6

// ── ViewBox parsing ────────────────────────────────────────────────

function parseViewBox(vb: string): { x: number; y: number; w: number; h: number } {
  const parts = vb.split(/\s+/).map(Number)
  return { x: parts[0], y: parts[1], w: parts[2], h: parts[3] }
}

// ── Public API ─────────────────────────────────────────────────────

/**
 * Scale, centre, and render a user-drawn glyph (mark) within a zone.
 *
 * Returns an SVG `<g>` element containing transformed `<path>` elements
 * for each stroke. A subtle dual-path emboss effect gives the glyph an
 * inset / relief appearance without requiring SVG filters.
 *
 * Pure function — no PRNG, DOM, or React dependencies.
 */
export function renderGlyph(
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

  const paths: string[] = []

  for (const stroke of glyph.strokes) {
    // Shadow layer (slight offset for emboss)
    paths.push(
      `<path d="${stroke.d}" stroke="${SHADOW_COLOR}" ` +
      `stroke-width="${stroke.width}" fill="none" ` +
      `stroke-linecap="round" stroke-linejoin="round" ` +
      `transform="translate(${EMBOSS_OFFSET}, ${EMBOSS_OFFSET})"/>`,
    )

    // Main stroke layer
    paths.push(
      `<path d="${stroke.d}" stroke="rgba(255,255,255,0.5)" ` +
      `stroke-width="${stroke.width}" fill="none" ` +
      `stroke-linecap="round" stroke-linejoin="round"/>`,
    )
  }

  return (
    `<g transform="translate(${offsetX}, ${offsetY}) scale(${scale})" ` +
    `opacity="${STROKE_OPACITY}">` +
    paths.join("") +
    `</g>`
  )
}
