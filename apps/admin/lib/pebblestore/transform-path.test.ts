import { describe, expect, it } from "vitest"
import { parsePath, serializePath, transformPath, type Matrix } from "./path"
import { bakeAdjust, buildAdjustMatrix, matrixToTransform } from "./transform-path"
import { GLYPH_CANVAS_VIEWBOX, IDENTITY_ADJUST, type Adjust, type GlyphStroke } from "./types"

// The adjust matrix is used twice for the same glyph: once as a CSS transform in
// the live preview, once baked into the stored `d`. The two must agree exactly,
// or the moderator approves one drawing and the store ships another.

const adjust = (over: Partial<Adjust> = {}): Adjust => ({ ...IDENTITY_ADJUST, ...over })

describe("buildAdjustMatrix", () => {
  it("is the identity matrix for the identity adjust", () => {
    expect(buildAdjustMatrix(GLYPH_CANVAS_VIEWBOX, IDENTITY_ADJUST)).toEqual([1, 0, 0, 1, 0, 0])
  })

  it("scales about the viewBox centre, not the origin", () => {
    const m = buildAdjustMatrix("0 0 200 200", adjust({ scale: 2 }))
    expect(m).toEqual([2, 0, 0, 2, -100, -100])
    // The centre is the fixed point.
    expect(transform(m, { x: 100, y: 100 })).toEqual({ x: 100, y: 100 })
  })

  it("mirrors about the centre for flipH and flipV", () => {
    const h = buildAdjustMatrix("0 0 200 200", adjust({ flipH: true }))
    expect(transform(h, { x: 40, y: 60 })).toEqual({ x: 160, y: 60 })
    const v = buildAdjustMatrix("0 0 200 200", adjust({ flipV: true }))
    expect(transform(v, { x: 40, y: 60 })).toEqual({ x: 40, y: 140 })
  })

  it("combines flip and scale without moving the centre", () => {
    const m = buildAdjustMatrix("0 0 200 200", adjust({ scale: 0.5, flipH: true, flipV: true }))
    expect(transform(m, { x: 100, y: 100 })).toEqual({ x: 100, y: 100 })
    expect(transform(m, { x: 140, y: 100 })).toEqual({ x: 80, y: 100 })
  })

  it("adds the offset after the scale, in viewBox units", () => {
    const m = buildAdjustMatrix("0 0 200 200", adjust({ scale: 2, offsetX: 10, offsetY: -5 }))
    expect(transform(m, { x: 100, y: 100 })).toEqual({ x: 110, y: 95 })
  })

  // A viewBox with a non-zero origin is legal SVG; the centre has to follow it,
  // otherwise every adjust on such a glyph drifts off-canvas.
  it("respects a viewBox with a non-zero origin", () => {
    const m = buildAdjustMatrix("-50 -50 100 100", adjust({ scale: 2 }))
    expect(transform(m, { x: 0, y: 0 })).toEqual({ x: 0, y: 0 })
    expect(transform(m, { x: 10, y: 10 })).toEqual({ x: 20, y: 20 })
  })

  it("parses a comma-separated viewBox the same as a space-separated one", () => {
    expect(buildAdjustMatrix("0,0,200,200", adjust({ scale: 2 }))).toEqual(
      buildAdjustMatrix("0 0 200 200", adjust({ scale: 2 })),
    )
  })
})

describe("matrixToTransform", () => {
  it("writes the SVG matrix() the preview applies", () => {
    expect(matrixToTransform([1, 0, 0, 1, 0, 0])).toBe("matrix(1,0,0,1,0,0)")
  })

  it("rounds to four decimals, keeping the preview honest at small scales", () => {
    expect(matrixToTransform([1 / 3, 0, 0, 1 / 3, 0, 0])).toBe("matrix(0.3333,0,0,0.3333,0,0)")
  })
})

describe("bakeAdjust", () => {
  const strokes: GlyphStroke[] = [
    { d: "M 50 50 L 150 150", width: 6 },
    { d: "M 50 150 Q 100 100 150 50", width: 6 },
  ]

  it("leaves the geometry untouched for the identity adjust", () => {
    expect(bakeAdjust(strokes, GLYPH_CANVAS_VIEWBOX, IDENTITY_ADJUST)).toEqual(strokes)
  })

  it("keeps the stroke width — adjust changes the footprint, never the weight", () => {
    const baked = bakeAdjust(strokes, GLYPH_CANVAS_VIEWBOX, adjust({ scale: 0.25 }))
    expect(baked.map((s) => s.width)).toEqual([6, 6])
  })

  it("bakes exactly what the preview matrix would have shown", () => {
    const a = adjust({ scale: 1.5, offsetX: 12, offsetY: -8, flipH: true })
    const m = buildAdjustMatrix(GLYPH_CANVAS_VIEWBOX, a)
    const baked = bakeAdjust(strokes, GLYPH_CANVAS_VIEWBOX, a)
    for (const [i, stroke] of strokes.entries()) {
      const byHand = serializePath(transformPath(parsePath(stroke.d), m))
      expect(baked[i].d).toBe(byHand)
    }
  })

  it("does not mutate the input strokes", () => {
    const before = structuredClone(strokes)
    bakeAdjust(strokes, GLYPH_CANVAS_VIEWBOX, adjust({ scale: 3 }))
    expect(strokes).toEqual(before)
  })

  it("handles an empty stroke list", () => {
    expect(bakeAdjust([], GLYPH_CANVAS_VIEWBOX, adjust({ scale: 2 }))).toEqual([])
  })
})

function transform(m: Matrix, p: { x: number; y: number }) {
  return { x: m[0] * p.x + m[2] * p.y + m[4], y: m[1] * p.x + m[3] * p.y + m[5] }
}
