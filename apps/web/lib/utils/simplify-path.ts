export type Point = {
  x: number
  y: number
  pressure?: number
}

/**
 * Ramer-Douglas-Peucker path simplification.
 * Reduces point density while preserving shape fidelity.
 */
export function simplifyPath(points: Point[], epsilon: number): Point[] {
  if (points.length <= 2) return points

  let maxDist = 0
  let maxIndex = 0
  const first = points[0]
  const last = points[points.length - 1]

  for (let i = 1; i < points.length - 1; i++) {
    const dist = perpendicularDistance(points[i], first, last)
    if (dist > maxDist) {
      maxDist = dist
      maxIndex = i
    }
  }

  if (maxDist > epsilon) {
    const left = simplifyPath(points.slice(0, maxIndex + 1), epsilon)
    const right = simplifyPath(points.slice(maxIndex), epsilon)
    return [...left.slice(0, -1), ...right]
  }

  return [first, last]
}

function perpendicularDistance(point: Point, lineStart: Point, lineEnd: Point): number {
  const dx = lineEnd.x - lineStart.x
  const dy = lineEnd.y - lineStart.y
  const lengthSq = dx * dx + dy * dy

  if (lengthSq === 0) {
    const ex = point.x - lineStart.x
    const ey = point.y - lineStart.y
    return Math.sqrt(ex * ex + ey * ey)
  }

  const t = Math.max(0, Math.min(1, ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lengthSq))
  const projX = lineStart.x + t * dx
  const projY = lineStart.y + t * dy
  const ex = point.x - projX
  const ey = point.y - projY
  return Math.sqrt(ex * ex + ey * ey)
}

/**
 * Converts a point array into an SVG path "d" attribute string.
 * Uses quadratic bezier curves for smooth rendering.
 */
export function pointsToSvgPath(points: Point[]): string {
  if (points.length === 0) return ""
  if (points.length === 1) return `M${points[0].x},${points[0].y} L${points[0].x},${points[0].y}`

  let d = `M${points[0].x},${points[0].y}`

  if (points.length === 2) {
    d += ` L${points[1].x},${points[1].y}`
    return d
  }

  // Use quadratic bezier curves through midpoints for smooth strokes
  for (let i = 1; i < points.length - 1; i++) {
    const midX = (points[i].x + points[i + 1].x) / 2
    const midY = (points[i].y + points[i + 1].y) / 2
    d += ` Q${points[i].x},${points[i].y} ${midX},${midY}`
  }

  // End at the last point
  const last = points[points.length - 1]
  d += ` L${last.x},${last.y}`

  return d
}
