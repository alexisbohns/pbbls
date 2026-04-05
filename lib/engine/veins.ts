import type { Pebble } from "@/lib/types"
import type { Point, BBox, Rect, VeinParams, VeinOutput } from "./types"
import type { PRNG } from "./prng"
import { openBezierPath } from "./geometry"

// ── Intensity-to-vein configuration ─────────────────────────────

type VeinConfig = {
  minCount: number
  maxCount: number
  waypointRange: [number, number]
  widthRange: [number, number]
  opacityRange: [number, number]
  wanderFactor: number
}

const VEIN_CONFIGS: Record<Pebble["intensity"], VeinConfig> = {
  1: {
    minCount: 1,
    maxCount: 1,
    waypointRange: [2, 3],
    widthRange: [0.8, 1.5],
    opacityRange: [0.4, 0.6],
    wanderFactor: 0.15,
  },
  2: {
    minCount: 1,
    maxCount: 2,
    waypointRange: [2, 4],
    widthRange: [0.5, 1.8],
    opacityRange: [0.3, 0.65],
    wanderFactor: 0.2,
  },
  3: {
    minCount: 1,
    maxCount: 3,
    waypointRange: [3, 5],
    widthRange: [0.4, 2.0],
    opacityRange: [0.25, 0.7],
    wanderFactor: 0.25,
  },
}

// ── Edge helpers ────────────────────────────────────────────────

type Edge = 0 | 1 | 2 | 3 // top, right, bottom, left

const EDGES: readonly Edge[] = [0, 1, 2, 3]

/**
 * Pick a point along a bounding-box edge, inset by ~15 % so the
 * vein starts/ends within the pebble body rather than at the extreme corner.
 */
function pickEdgePoint(edge: Edge, bounds: BBox, rng: PRNG): Point {
  const w = bounds.maxX - bounds.minX
  const h = bounds.maxY - bounds.minY
  const insetX = w * 0.15
  const insetY = h * 0.15

  switch (edge) {
    case 0: // top
      return { x: rng.nextFloat(bounds.minX + insetX, bounds.maxX - insetX), y: bounds.minY }
    case 1: // right
      return { x: bounds.maxX, y: rng.nextFloat(bounds.minY + insetY, bounds.maxY - insetY) }
    case 2: // bottom
      return { x: rng.nextFloat(bounds.minX + insetX, bounds.maxX - insetX), y: bounds.maxY }
    case 3: // left
      return { x: bounds.minX, y: rng.nextFloat(bounds.minY + insetY, bounds.maxY - insetY) }
  }
}

// ── Exclusion zone avoidance ────────────────────────────────────

const EXCLUSION_PADDING = 3

function isInsideRect(point: Point, rect: Rect, padding: number): boolean {
  return (
    point.x >= rect.x - padding &&
    point.x <= rect.x + rect.width + padding &&
    point.y >= rect.y - padding &&
    point.y <= rect.y + rect.height + padding
  )
}

/**
 * Push a waypoint away from any overlapping exclusion zone.
 * Applies a small random jitter so avoidance paths don't look mechanical.
 */
function avoidExclusions(point: Point, zones: Rect[], rng: PRNG): Point {
  let { x, y } = point

  for (const zone of zones) {
    if (!isInsideRect({ x, y }, zone, EXCLUSION_PADDING)) continue

    const cx = zone.x + zone.width / 2
    const cy = zone.y + zone.height / 2
    let dx = x - cx
    let dy = y - cy

    // If the point is at the exact centre, pick a random direction
    if (dx === 0 && dy === 0) {
      dx = rng.nextFloat(-1, 1)
      dy = rng.nextFloat(-1, 1)
    }

    const len = Math.sqrt(dx * dx + dy * dy)
    const nx = dx / len
    const ny = dy / len

    // Push outward past the zone boundary + padding + jitter
    const escapeDistance =
      Math.max(zone.width, zone.height) / 2 + EXCLUSION_PADDING + rng.nextFloat(1, 3)
    x = cx + nx * escapeDistance
    y = cy + ny * escapeDistance
  }

  return { x, y }
}

// ── Single vein generation ──────────────────────────────────────

function generateSingleVein(
  rng: PRNG,
  params: VeinParams,
  config: VeinConfig,
): string {
  const { emotionColor, bounds, exclusionZones } = params

  // Pick two different edges for entry and exit
  const entryEdge = rng.pick(EDGES)
  let exitEdge = rng.pick(EDGES)
  while (exitEdge === entryEdge) {
    exitEdge = rng.pick(EDGES)
  }

  const start = pickEdgePoint(entryEdge, bounds, rng)
  const end = pickEdgePoint(exitEdge, bounds, rng)

  // Generate intermediate waypoints
  const waypointCount = rng.nextInt(config.waypointRange[0], config.waypointRange[1])
  const pebbleSize = Math.max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY)
  const wanderAmount = config.wanderFactor * pebbleSize

  // Perpendicular direction to the start→end vector
  const vx = end.x - start.x
  const vy = end.y - start.y
  const vLen = Math.sqrt(vx * vx + vy * vy) || 1
  const perpX = -vy / vLen
  const perpY = vx / vLen

  const waypoints: Point[] = []
  for (let i = 0; i < waypointCount; i++) {
    const t = (i + 1) / (waypointCount + 1)
    const baseX = start.x + vx * t
    const baseY = start.y + vy * t
    const offset = rng.nextFloat(-wanderAmount, wanderAmount)

    const raw: Point = {
      x: baseX + perpX * offset,
      y: baseY + perpY * offset,
    }

    waypoints.push(avoidExclusions(raw, exclusionZones, rng))
  }

  const allPoints = [start, ...waypoints, end]
  const pathData = openBezierPath(allPoints)

  const strokeWidth = rng.nextFloat(config.widthRange[0], config.widthRange[1])
  const strokeOpacity = rng.nextFloat(config.opacityRange[0], config.opacityRange[1])

  return `<path d="${pathData}" stroke="${emotionColor}" stroke-width="${strokeWidth}" stroke-opacity="${strokeOpacity}" fill="none" stroke-linecap="round"/>`
}

// ── Public API ──────────────────────────────────────────────────

/**
 * Generate deterministic emotion veins for a pebble.
 *
 * Veins are coloured Bézier curves that flow across the pebble surface.
 * Quantity scales with intensity (1 → 1, 2 → 1-2, 3 → 1-3).
 * All veins share the single emotion colour (V1).
 *
 * Output paths are clipped to the pebble boundary via a `<clipPath>` in
 * `defs`. The consumer wraps the paths in a `<g clip-path="url(#clipId)">`.
 *
 * Pure function — no DOM or React dependencies. Deterministic for
 * identical PRNG state.
 */
export function generateVeins(rng: PRNG, params: VeinParams): VeinOutput {
  const uid = rng.nextInt(1000, 9999)
  const config = VEIN_CONFIGS[params.intensity]

  const veinCount = rng.nextInt(config.minCount, config.maxCount)
  const clipId = `vein-clip-${uid}`

  const defs = `<clipPath id="${clipId}"><path d="${params.shapePath}"/></clipPath>`

  const paths: string[] = []
  for (let i = 0; i < veinCount; i++) {
    paths.push(generateSingleVein(rng, params, config))
  }

  return { defs, paths, clipId }
}
