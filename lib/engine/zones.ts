import type { BBox, Rect, ZoneAllocation } from "./types"

// ── Constants ──────────────────────────────────────────────────────

/** Glyph zone occupies 35 % of bbox (midpoint of 30–40 % spec). */
const GLYPH_RATIO = 0.35

/** Fossil zone width as a fraction of bbox. */
const FOSSIL_WIDTH_RATIO = 0.4

/** Fossil zone height as a fraction of bbox. */
const FOSSIL_HEIGHT_RATIO = 0.35

/** Vertical start of the fossil zone (60 % down → bottom quadrant). */
const FOSSIL_Y_OFFSET = 0.6

/** Minimum gap between glyph and fossil zones. */
const ZONE_PADDING = 2

// ── Public API ─────────────────────────────────────────────────────

/**
 * Divide a pebble bounding box into non-overlapping zones for
 * glyph placement, fossil rendering, and vein exclusion.
 *
 * Pure function — no PRNG, no DOM dependencies.
 */
export function allocateZones(
  bbox: BBox,
  hasGlyph: boolean,
  hasFossil: boolean,
): ZoneAllocation {
  const w = bbox.maxX - bbox.minX
  const h = bbox.maxY - bbox.minY

  const glyphZone = hasGlyph ? buildGlyphZone(bbox, w, h) : null
  const fossilZone = hasFossil ? buildFossilZone(bbox, w, h, glyphZone) : null

  const exclusionZones: Rect[] = []
  if (glyphZone) exclusionZones.push(glyphZone)
  if (fossilZone) exclusionZones.push(fossilZone)

  return { glyphZone, fossilZone, exclusionZones }
}

// ── Zone builders ──────────────────────────────────────────────────

function buildGlyphZone(bbox: BBox, w: number, h: number): Rect {
  const zoneW = w * GLYPH_RATIO
  const zoneH = h * GLYPH_RATIO

  return {
    x: bbox.minX + (w - zoneW) / 2,
    y: bbox.minY + (h - zoneH) / 2,
    width: zoneW,
    height: zoneH,
  }
}

function buildFossilZone(
  bbox: BBox,
  w: number,
  h: number,
  glyphZone: Rect | null,
): Rect {
  const zoneW = w * FOSSIL_WIDTH_RATIO
  const zoneH = h * FOSSIL_HEIGHT_RATIO

  let y = bbox.minY + h * FOSSIL_Y_OFFSET
  const x = bbox.minX + (w - zoneW) / 2

  // Shift below glyph zone if they overlap
  if (glyphZone) {
    const glyphBottom = glyphZone.y + glyphZone.height
    if (y < glyphBottom + ZONE_PADDING) {
      y = glyphBottom + ZONE_PADDING
    }
  }

  // Clamp so the fossil zone stays within the bbox
  const maxY = bbox.maxY - zoneH
  if (y > maxY) {
    y = maxY
  }

  return { x, y, width: zoneW, height: zoneH }
}
