// Where each part of a lit stone sits (#1052). Three spaces meet in one stone:
//
// - outline units: the backdrop silhouette's own viewBox (337 × 270 for small).
//   The stone's SVG is drawn in it, so the body, its clip and the rings are
//   the iOS geometry (spec D3).
// - canvas units: the engine canvas (250 × 200 for small) fitted to the box.
//   The final mock drew its stones in this space at the same on-screen size,
//   so every material constant lifted from it (facets, specks, sheen, glint)
//   is used verbatim inside `canvasTransform`.
// - seed units: the engine canvas again, but placed with iOS's carving inset
//   (`pebbleScale × 1.22`, `StoneLook.carvingScale`). The veins, fossil and
//   glyph live here, inside `carveTransform`.
//
// Pixel constants are the mock's, measured on its phone stones; they convert
// to outline units through `unitsPerPx`.

import type { Polarity, Size } from "./pebble-geometry"
import { pebbleScale } from "./pebble-geometry"
import { getOutline } from "./pebble-outlines"
import { parsePath } from "@/lib/wobble/path-parser"
import { flatten } from "@/lib/wobble/flatten"

// ── Glyph zones ─────────────────────────────────────────────
// Copied from packages/supabase/supabase/functions/_shared/engine/layout.ts;
// `stone-geometry.test.ts` fails when they drift. Seed units, top-left origin.

export const GLYPH_SIZE: Record<Size, number> = {
  small: 140,
  medium: 150,
  large: 160,
}

export const GLYPH_POSITION: Record<Size, Record<Polarity, { x: number; y: number }>> = {
  small: {
    highlight: { x: 37.5, y: 30 },
    neutral: { x: 25, y: 30 },
    lowlight: { x: 30, y: 30 },
  },
  medium: {
    highlight: { x: 26, y: 84 },
    neutral: { x: 26, y: 55 },
    lowlight: { x: 39, y: 26 },
  },
  large: {
    highlight: { x: 50, y: 75 },
    neutral: { x: 26, y: 88 },
    lowlight: { x: 26, y: 75 },
  },
}

// ── On-screen size ──────────────────────────────────────────

/** Stone width in px; the height follows the silhouette's aspect ratio. */
export const STONE_WIDTH: Record<Size, { phone: number; wide: number }> = {
  small: { phone: 34, wide: 40 },
  medium: { phone: 44, wide: 52 },
  large: { phone: 54, wide: 58 },
}

/** iOS `StoneLook.carvingScale`: the carving's inset relative to the engine's. */
export const CARVE_SCALE = 1.22

/** The glyph's carved line, whatever the stone's size. */
export const CARVE_STROKE_PX = 1.25

/** Outline units per on-screen px on a phone. */
export function unitsPerPx(size: Size): number {
  return getOutline(size, "neutral").width / STONE_WIDTH[size].phone
}

type Box = { width: number; height: number }

/** The engine canvas for a size (all three polarities share it). */
export const SEED_CANVAS: Record<Size, Box> = {
  small: { width: 250, height: 200 },
  medium: { width: 260, height: 260 },
  large: { width: 260, height: 310 },
}

function fit(size: Size, polarity: Polarity): { outline: Box; seed: Box; scale: number } {
  const outline = getOutline(size, polarity)
  const seed = SEED_CANVAS[size]
  return { outline, seed, scale: Math.min(outline.width / seed.width, outline.height / seed.height) }
}

function centred(outline: Box, seed: Box, scale: number): string {
  const tx = (outline.width - seed.width * scale) / 2
  const ty = (outline.height - seed.height * scale) / 2
  return `matrix(${round(scale)} 0 0 ${round(scale)} ${round(tx)} ${round(ty)})`
}

/** Canvas units → outline units: the mock's stone, fitted to the silhouette box. */
export function canvasScale(size: Size): number {
  return fit(size, "neutral").scale
}

export function canvasTransform(size: Size, polarity: Polarity): string {
  const { outline, seed, scale } = fit(size, polarity)
  return centred(outline, seed, scale)
}

/** Seed units → outline units, with iOS's carving inset (`pebbleScale × 1.22`). */
export function carveTransform(size: Size, polarity: Polarity): string {
  const { outline, seed, scale } = fit(size, polarity)
  return centred(outline, seed, scale * pebbleScale(size) * CARVE_SCALE)
}

/** The glyph in its engine zone, in seed units. `viewBox` is the glyph's own side. */
export function glyphTransform(size: Size, polarity: Polarity, viewBox: number): string {
  const { x, y } = GLYPH_POSITION[size][polarity]
  return `translate(${x} ${y}) scale(${round(GLYPH_SIZE[size] / viewBox)})`
}

// ── Shadow and lip (px, from the mock) ──────────────────────

/** The two blurred copies under the stone, pushed away from the light. */
export const SHADOW_FAR_PX = { x: 3.86, y: 6.95 }
export const SHADOW_NEAR_PX = { x: 0.93, y: 2.01 }
/** The glyph's lit lip, drawn under the ink and pushed down-right. */
export const LIP_PX = { x: 0.32, y: 0.54 }

/** The sprite's blurs in canvas units, as the mock declared them. */
export const BLUR = { near: 5, far: 15, ao: 7, soft: 12, glow: 4 } as const

// ── Rings and halo ──────────────────────────────────────────
// The rings are the silhouette scaled about its centroid, each a step further
// out; the halo (the raked clearing, filled --spine-halo) sits 0.78 of a step
// past the last ring. The mock scaled its engine outline the same way; the
// steps are its, measured per size, applied to the silhouette we draw.

export const RING_COUNT: Record<Size, number> = { small: 1, medium: 2, large: 3 }
const RING_STEP_PX: Record<Size, number> = { small: 4.8, medium: 4.4, large: 4.2 }
const HALO_STEP = 0.78

export type RingGeometry = {
  /** One `matrix(...)` per ring, innermost first. */
  rings: string[]
  halo: string
}

const ringCache = new Map<string, RingGeometry>()

export function ringGeometry(size: Size, polarity: Polarity): RingGeometry {
  const key = `${size}-${polarity}`
  const cached = ringCache.get(key)
  if (cached) return cached
  const outline = getOutline(size, polarity)
  const { cx, cy, width, height } = silhouetteFrame(outline.path)
  const step = RING_STEP_PX[size] * unitsPerPx(size)
  const at = (k: number) => {
    const sx = 1 + (2 * k * step) / width
    const sy = 1 + (2 * k * step) / height
    return `matrix(${round(sx)} 0 0 ${round(sy)} ${round(cx * (1 - sx))} ${round(cy * (1 - sy))})`
  }
  const count = RING_COUNT[size]
  const built = {
    rings: Array.from({ length: count }, (_, i) => at(i + 1)),
    halo: at(count + HALO_STEP),
  }
  ringCache.set(key, built)
  return built
}

/** Area centroid and bounding box of a path's outer contour. */
export function silhouetteFrame(d: string): { cx: number; cy: number; width: number; height: number } {
  const elements = parsePath(d)
  const contours = elements ? flatten(elements, 2) : []
  // The first contour is the outer one; large-lowlight's second is its hole.
  const points = contours[0]?.points ?? []
  let area = 0
  let cx = 0
  let cy = 0
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  points.forEach((p, i) => {
    const q = points[(i + 1) % points.length]
    const cross = p.x * q.y - q.x * p.y
    area += cross
    cx += (p.x + q.x) * cross
    cy += (p.y + q.y) * cross
    minX = Math.min(minX, p.x)
    minY = Math.min(minY, p.y)
    maxX = Math.max(maxX, p.x)
    maxY = Math.max(maxY, p.y)
  })
  area /= 2
  return { cx: cx / (6 * area), cy: cy / (6 * area), width: maxX - minX, height: maxY - minY }
}

function round(n: number): number {
  return Math.round(n * 1000) / 1000
}

/** A glyph's square side from its `viewBox` ("0 0 200 200"); 200 if unreadable. */
export function glyphSide(viewBox: string): number {
  const side = Number(viewBox.trim().split(/[\s,]+/)[2])
  return Number.isFinite(side) && side > 0 ? side : 200
}
