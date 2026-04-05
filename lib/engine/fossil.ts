import type { Point, Rect } from "./types"
import type { PRNG } from "./prng"
import { openBezierPath } from "./geometry"

// ── Constants ──────────────────────────────────────────────────────

/** Number of sample points along the spiral. */
const SPIRAL_STEPS = 40

/** How much of the zone radius the spiral may occupy. */
const FIT_RATIO = 0.45

// ── Spiral math ────────────────────────────────────────────────────

/**
 * Sample a logarithmic spiral: r = a · e^(b · θ).
 * Returns raw points centred at the origin.
 */
function sampleSpiral(
  initialRadius: number,
  growthRate: number,
  totalAngle: number,
  steps: number,
): Point[] {
  const points: Point[] = []
  for (let i = 0; i <= steps; i++) {
    const theta = (i / steps) * totalAngle
    const r = initialRadius * Math.exp(growthRate * theta)
    points.push({ x: r * Math.cos(theta), y: r * Math.sin(theta) })
  }
  return points
}

/**
 * Scale and translate raw spiral points to fit within `zone`.
 */
function fitToZone(points: Point[], zone: Rect): Point[] {
  if (points.length === 0) return []

  let pMinX = Infinity
  let pMinY = Infinity
  let pMaxX = -Infinity
  let pMaxY = -Infinity

  for (const p of points) {
    if (p.x < pMinX) pMinX = p.x
    if (p.y < pMinY) pMinY = p.y
    if (p.x > pMaxX) pMaxX = p.x
    if (p.y > pMaxY) pMaxY = p.y
  }

  const rawW = pMaxX - pMinX || 1
  const rawH = pMaxY - pMinY || 1
  const maxExtent = Math.min(zone.width, zone.height) * FIT_RATIO
  const scale = Math.min(maxExtent / rawW, maxExtent / rawH)

  const cx = zone.x + zone.width / 2
  const cy = zone.y + zone.height / 2
  const rawCx = (pMinX + pMaxX) / 2
  const rawCy = (pMinY + pMaxY) / 2

  return points.map((p) => ({
    x: cx + (p.x - rawCx) * scale,
    y: cy + (p.y - rawCy) * scale,
  }))
}

// ── Public API ─────────────────────────────────────────────────────

/**
 * Render a fossil ammonite spiral as an SVG `<g>` element.
 *
 * Only produces output when `retroactive` is true — otherwise returns
 * an empty string and consumes no PRNG values.
 *
 * The spiral uses a logarithmic curve with seeded variation in rotation,
 * turn count, growth rate, and colouring. Output is deterministic for
 * identical PRNG state.
 *
 * Pure function — no DOM or React dependencies.
 */
export function renderFossil(
  rng: PRNG,
  zone: Rect,
  retroactive: boolean,
): string {
  if (!retroactive) return ""

  // ── Spiral parameters from PRNG ─────────────────────────────────
  const rotation = rng.nextFloat(0, 2 * Math.PI)
  const turnCount = rng.nextFloat(2.5, 4.5)
  const growthRate = rng.nextFloat(0.12, 0.22)
  const baseRadius = Math.min(zone.width, zone.height) * rng.nextFloat(0.03, 0.06)

  const totalAngle = turnCount * 2 * Math.PI
  const rawPoints = sampleSpiral(baseRadius, growthRate, totalAngle, SPIRAL_STEPS)
  const points = fitToZone(rawPoints, zone)

  if (points.length < 2) return ""

  const pathData = openBezierPath(points)

  // ── Stone-like colouring ────────────────────────────────────────
  const hue = rng.nextInt(25, 50)
  const sat = rng.nextInt(10, 25)
  const light = rng.nextInt(40, 55)
  const opacity = rng.nextFloat(0.15, 0.35)
  const strokeWidth = rng.nextFloat(0.5, 1.2)

  const color = `hsl(${hue}, ${sat}%, ${light}%)`
  const rotDeg = (rotation * 180) / Math.PI
  const cx = zone.x + zone.width / 2
  const cy = zone.y + zone.height / 2

  return (
    `<g transform="rotate(${rotDeg}, ${cx}, ${cy})">` +
    `<path d="${pathData}" stroke="${color}" stroke-width="${strokeWidth}" ` +
    `stroke-opacity="${opacity}" fill="none" stroke-linecap="round"/>` +
    `</g>`
  )
}
