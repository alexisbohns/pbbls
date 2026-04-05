import type { PebbleParams, RenderTier, EngineOutput, Rect, VeinParams } from "./types"
import { createPRNG } from "./prng"
import { generateShape } from "./shape"
import { generateSurface } from "./surface"
import { allocateZones } from "./zones"
import { generateVeins } from "./veins"
import { renderFossil } from "./fossil"
import { renderGlyph } from "./glyph"

// ── Constants ──────────────────────────────────────────────────────

/** Padding ratio applied to the shape bounding box for the SVG viewBox. */
const VIEWBOX_PAD = 0.15

// ── Pipeline ──────────────────────────────────────────────────────

/**
 * Render a complete pebble as a self-contained SVG string.
 *
 * Orchestrates all engine modules in a fixed sequence so the PRNG
 * produces identical output for identical inputs:
 *
 *   seed → shape → zones → surface → veins → fossil → glyph → SVG
 *
 * Pure function — no DOM or React dependencies.
 */
export function renderPebble(
  params: PebbleParams,
  seed: number,
  tier: RenderTier,
): EngineOutput {
  // 1. Create deterministic PRNG
  const rng = createPRNG(seed)

  // 2. Generate base shape from intensity
  const shape = generateShape(rng, params.intensity)

  // 3. Compute padded viewBox rect
  const bw = shape.bbox.maxX - shape.bbox.minX
  const bh = shape.bbox.maxY - shape.bbox.minY
  const padX = bw * VIEWBOX_PAD
  const padY = bh * VIEWBOX_PAD

  const viewBoxRect: Rect = {
    x: shape.bbox.minX - padX,
    y: shape.bbox.minY - padY,
    width: bw + padX * 2,
    height: bh + padY * 2,
  }

  const viewBox = `${viewBoxRect.x} ${viewBoxRect.y} ${viewBoxRect.width} ${viewBoxRect.height}`

  // 4. Allocate zones for glyph and fossil (pure, no PRNG)
  const zones = allocateZones(
    shape.bbox,
    params.glyph !== null,
    params.retroactive,
  )

  // 5. Generate surface treatment (gradients, filters)
  const surface = generateSurface(params.positiveness, rng, viewBoxRect, tier)

  // 6. Generate veins
  const veinParams: VeinParams = {
    emotionColor: params.emotionColor,
    intensity: params.intensity,
    bounds: shape.bbox,
    exclusionZones: zones.exclusionZones,
    shapePath: shape.path,
  }
  const veins = generateVeins(rng, veinParams)

  // 7. Render fossil (no-op when not retroactive)
  const fossilSvg = zones.fossilZone
    ? renderFossil(rng, zones.fossilZone, params.retroactive)
    : ""

  // 8. Render glyph (no PRNG dependency)
  const glyphSvg =
    params.glyph && zones.glyphZone
      ? renderGlyph(params.glyph, zones.glyphZone)
      : ""

  // 9. Assemble final SVG
  const filterAttr = surface.filterRef ? ` filter="${surface.filterRef}"` : ""

  const svg = [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewBox}">`,
    `<defs>`,
    surface.defs,
    veins.defs,
    `</defs>`,
    `<path d="${shape.path}" fill="${surface.fill}"${filterAttr}/>`,
    `<g clip-path="url(#${veins.clipId})">`,
    ...veins.paths,
    `</g>`,
    fossilSvg,
    glyphSvg,
    `</svg>`,
  ].join("")

  return { svg, viewBox }
}
