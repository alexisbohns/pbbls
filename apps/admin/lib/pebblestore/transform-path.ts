// apps/admin/lib/pebblestore/transform-path.ts
import { parsePath, serializePath, transformPath, type Matrix } from "./path"
import type { Adjust, GlyphStroke } from "./types"

function parseViewBox(vb: string): { x: number; y: number; w: number; h: number } {
  const [x, y, w, h] = vb.trim().split(/[\s,]+/).map(Number)
  return { x, y, w, h }
}

/**
 * Affine matrix for the adjust controls, about the viewBox centre:
 *   p' = c + offset + S·(p - c),  S = diag(sx, sy)
 * where sx = scale·(flipH ? -1 : 1), sy = scale·(flipV ? -1 : 1).
 */
export function buildAdjustMatrix(viewBox: string, a: Adjust): Matrix {
  const { x, y, w, h } = parseViewBox(viewBox)
  const cx = x + w / 2
  const cy = y + h / 2
  const sx = a.scale * (a.flipH ? -1 : 1)
  const sy = a.scale * (a.flipV ? -1 : 1)
  return [sx, 0, 0, sy, cx + a.offsetX - sx * cx, cy + a.offsetY - sy * cy]
}

/** CSS/SVG transform string for live preview. */
export function matrixToTransform(m: Matrix): string {
  return `matrix(${m.map((n) => Number(n.toFixed(4))).join(",")})`
}

/** Bake the adjust into the stroke geometry for publish. viewBox is unchanged. */
export function bakeAdjust(strokes: GlyphStroke[], viewBox: string, a: Adjust): GlyphStroke[] {
  const m = buildAdjustMatrix(viewBox, a)
  return strokes.map((s) => ({ ...s, d: serializePath(transformPath(parsePath(s.d), m)) }))
}
