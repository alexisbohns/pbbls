// Flattens parsed path elements into polylines whose chords are ≤ ~`step`
// units, so the noise displacement reads as a smooth wobble rather than a
// kinked polygon (issue #555 §2.3 step 1). Straight segments are subdivided
// too — a long straight line needs interior vertices or it cannot bend.
// Faithful port of the iOS `WobblePathFlattener`.

import type { Point } from "./types"
import type { PathElement } from "./path-parser"

/** A flattened subpath: dense points ready for displacement. */
export type Polyline = {
  points: Point[]
  isClosed: boolean
}

const DUPLICATE_EPSILON = 1e-6

export function flatten(elements: PathElement[], step: number): Polyline[] {
  const polylines: Polyline[] = []
  let current: Point[] = []
  let subpathStart: Point = { x: 0, y: 0 }
  let cursor: Point = { x: 0, y: 0 }

  const flush = (closed: boolean): void => {
    if (current.length > 0) polylines.push({ points: current, isClosed: closed })
    current = []
  }

  const append = (point: Point): void => {
    const last = current[current.length - 1]
    if (
      last &&
      Math.abs(last.x - point.x) < DUPLICATE_EPSILON &&
      Math.abs(last.y - point.y) < DUPLICATE_EPSILON
    ) {
      return
    }
    current.push(point)
  }

  const appendLine = (end: Point): void => {
    const distance = Math.hypot(end.x - cursor.x, end.y - cursor.y)
    const count = Math.max(1, Math.ceil(distance / step))
    for (let i = 1; i <= count; i++) {
      const t = i / count
      append({ x: cursor.x + (end.x - cursor.x) * t, y: cursor.y + (end.y - cursor.y) * t })
    }
    cursor = end
  }

  const appendQuad = (control: Point, end: Point): void => {
    // Control-polygon length over-estimates arc length, which only makes chords
    // denser than `step` — never sparser.
    const approxLength =
      Math.hypot(control.x - cursor.x, control.y - cursor.y) +
      Math.hypot(end.x - control.x, end.y - control.y)
    const count = Math.max(1, Math.ceil(approxLength / step))
    const start = cursor
    for (let i = 1; i <= count; i++) {
      const t = i / count
      const mt = 1 - t
      append({
        x: mt * mt * start.x + 2 * mt * t * control.x + t * t * end.x,
        y: mt * mt * start.y + 2 * mt * t * control.y + t * t * end.y,
      })
    }
    cursor = end
  }

  const appendCubic = (control1: Point, control2: Point, end: Point): void => {
    const approxLength =
      Math.hypot(control1.x - cursor.x, control1.y - cursor.y) +
      Math.hypot(control2.x - control1.x, control2.y - control1.y) +
      Math.hypot(end.x - control2.x, end.y - control2.y)
    const count = Math.max(1, Math.ceil(approxLength / step))
    const start = cursor
    for (let i = 1; i <= count; i++) {
      const t = i / count
      const mt = 1 - t
      const c0 = mt * mt * mt
      const c1 = 3 * mt * mt * t
      const c2 = 3 * mt * t * t
      const c3 = t * t * t
      append({
        x: c0 * start.x + c1 * control1.x + c2 * control2.x + c3 * end.x,
        y: c0 * start.y + c1 * control1.y + c2 * control2.y + c3 * end.y,
      })
    }
    cursor = end
  }

  for (const element of elements) {
    switch (element.type) {
      case "move":
        flush(false)
        cursor = element.to
        subpathStart = cursor
        current = [cursor]
        break
      case "line":
        appendLine(element.to)
        break
      case "quad":
        appendQuad(element.control, element.to)
        break
      case "cubic":
        appendCubic(element.control1, element.control2, element.to)
        break
      case "close": {
        // Subdivide the implicit closing leg, then drop the duplicated start
        // point: rings are stored without repetition because the outline
        // builder wraps neighbors cyclically.
        appendLine(subpathStart)
        const last = current[current.length - 1]
        if (
          last &&
          current.length > 1 &&
          Math.abs(last.x - subpathStart.x) < DUPLICATE_EPSILON &&
          Math.abs(last.y - subpathStart.y) < DUPLICATE_EPSILON
        ) {
          current.pop()
        }
        flush(true)
        cursor = subpathStart
        break
      }
    }
  }
  flush(false)
  return polylines
}
