import type { Pebble } from "@/lib/types"
import type { Point, BBox, ShapeOutput } from "./types"
import type { PRNG } from "./prng"
import { polarToCartesian, displacePoint, bezierSmooth } from "./geometry"

// ── Intensity-to-shape configuration ─────────────────────────────

type ShapeConfig = {
  vertexCount: number
  radius: number
  startAngle: number
  displacement: number
}

const SHAPE_CONFIGS: Record<Pebble["intensity"], ShapeConfig> = {
  1: { vertexCount: 8, radius: 30, startAngle: 0, displacement: 4 },
  2: { vertexCount: 3, radius: 40, startAngle: -Math.PI / 2, displacement: 6 },
  3: { vertexCount: 4, radius: 50, startAngle: -Math.PI / 2, displacement: 8 },
}

// ── Shape generator ──────────────────────────────────────────────

/**
 * Generate a deterministic pebble contour from intensity and PRNG state.
 *
 * - Intensity 1: rounded (8-vertex polygon, small radius)
 * - Intensity 2: triangular (3 vertices, medium radius)
 * - Intensity 3: diamond (4 vertices, large radius)
 *
 * Shapes are centered at the origin. The consumer translates/scales as needed.
 */
export function generateShape(
  rng: PRNG,
  intensity: Pebble["intensity"],
): ShapeOutput {
  const config = SHAPE_CONFIGS[intensity]
  const step = (2 * Math.PI) / config.vertexCount

  const baseVertices: Point[] = []
  for (let i = 0; i < config.vertexCount; i++) {
    const angle = config.startAngle + step * i
    baseVertices.push(polarToCartesian(0, 0, config.radius, angle))
  }

  const vertices = baseVertices.map((v) =>
    displacePoint(v, config.displacement, rng),
  )

  const path = bezierSmooth(vertices)
  const bbox = computeBBox(vertices)

  return { path, bbox, vertices }
}

// ── Helpers ──────────────────────────────────────────────────────

function computeBBox(points: Point[]): BBox {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity

  for (const p of points) {
    if (p.x < minX) minX = p.x
    if (p.y < minY) minY = p.y
    if (p.x > maxX) maxX = p.x
    if (p.y > maxY) maxY = p.y
  }

  return { minX, minY, maxX, maxY }
}
