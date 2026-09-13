// @vitest-environment jsdom
//
// The only module in the suite that needs a DOM (`DOMParser`); it opts in per
// file rather than making the whole suite pay jsdom's startup.
import { describe, expect, it } from "vitest"
import { parsePath, pathBounds } from "./path"
import { svgToStrokes } from "./svg-to-strokes"
import { DEFAULT_STROKE_WIDTH, GLYPH_CANVAS, GLYPH_CANVAS_VIEWBOX } from "./types"

const svg = (inner: string, attrs = 'viewBox="0 0 24 24"') => `<svg xmlns="http://www.w3.org/2000/svg" ${attrs}>${inner}</svg>`

/** Superset bounds across every stroke, in the returned glyph space. */
function bounds(strokes: { d: string }[]) {
  const all = strokes.flatMap((s) => parsePath(s.d))
  return pathBounds(all)!
}

describe("svgToStrokes", () => {
  it("imports a path and normalizes into the canonical square viewBox", () => {
    const result = svgToStrokes(svg('<path d="M 0 0 L 10 0"/>'))
    expect(result.viewBox).toBe(GLYPH_CANVAS_VIEWBOX)
    expect(result.skipped).toEqual([])
    expect(result.strokes).toEqual([{ d: "M 10 100 L 190 100", width: DEFAULT_STROKE_WIDTH }])
  })

  // The source viewBox is a last-resort fallback only: content is fitted to the
  // square, so a drawing tucked in one corner of a 1000-unit canvas still
  // arrives centred and full-size.
  it("ignores the source viewBox and fits the content itself", () => {
    const a = svgToStrokes(svg('<path d="M 0 0 L 10 10"/>', 'viewBox="0 0 24 24"'))
    const b = svgToStrokes(svg('<path d="M 900 900 L 910 910"/>', 'viewBox="0 0 1000 1000"'))
    expect(b.strokes).toEqual(a.strokes)
  })

  it("preserves aspect ratio and centres the short axis", () => {
    // 100 wide, 50 tall: the width fills 90% of the square, the height half that.
    const { strokes } = svgToStrokes(svg('<path d="M 0 0 L 100 0 L 100 50 L 0 50 Z"/>'))
    const b = bounds(strokes)
    expect(b.maxX - b.minX).toBeCloseTo(GLYPH_CANVAS * 0.9, 1)
    expect(b.maxY - b.minY).toBeCloseTo(GLYPH_CANVAS * 0.45, 1)
    // Centred on both axes.
    expect((b.minX + b.maxX) / 2).toBeCloseTo(GLYPH_CANVAS / 2, 1)
    expect((b.minY + b.maxY) / 2).toBeCloseTo(GLYPH_CANVAS / 2, 1)
  })

  it("leaves a margin so the stroke caps stay inside the square", () => {
    const { strokes } = svgToStrokes(svg('<path d="M 0 0 L 10 0 L 10 10 Z"/>'))
    const b = bounds(strokes)
    expect(b.minX).toBeGreaterThan(0)
    expect(b.maxX).toBeLessThan(GLYPH_CANVAS)
  })

  it("fits every stroke together, not each one on its own", () => {
    const { strokes } = svgToStrokes(
      svg('<path d="M 0 0 L 10 0"/><path d="M 0 100 L 10 100"/>'),
    )
    expect(strokes).toHaveLength(2)
    const [top, bottom] = strokes.map((s) => bounds([s]))
    expect(top.minY).not.toBeCloseTo(bottom.minY, 1)
  })

  // Stroke weight is a property of the glyph model, not of the uploaded file.
  it("always writes the canonical stroke width, ignoring the source", () => {
    const { strokes } = svgToStrokes(svg('<path d="M 0 0 L 10 0" stroke-width="42"/>'))
    expect(strokes[0].width).toBe(DEFAULT_STROKE_WIDTH)
  })

  it("converts <line> to an equivalent path", () => {
    const fromLine = svgToStrokes(svg('<line x1="0" y1="0" x2="10" y2="10"/>'))
    const fromPath = svgToStrokes(svg('<path d="M 0 0 L 10 10"/>'))
    expect(fromLine.strokes).toEqual(fromPath.strokes)
  })

  it("defaults missing <line> coordinates to zero", () => {
    const { strokes, skipped } = svgToStrokes(svg('<line x2="10" y2="10"/>'))
    expect(skipped).toEqual([])
    expect(strokes).toHaveLength(1)
  })

  it("converts <polyline> open and <polygon> closed", () => {
    const open = svgToStrokes(svg('<polyline points="0,0 10,0 10,10"/>'))
    expect(open.strokes[0].d.endsWith("Z")).toBe(false)
    const closed = svgToStrokes(svg('<polygon points="0,0 10,0 10,10"/>'))
    expect(closed.strokes[0].d.endsWith("Z")).toBe(true)
  })

  it("skips a polyline with fewer than two points", () => {
    const { strokes, skipped } = svgToStrokes(svg('<polyline points="5,5"/>'))
    expect(strokes).toEqual([])
    expect(skipped).toEqual(["polyline"])
  })

  it("skips unsupported shape tags and reports them by name", () => {
    const { strokes, skipped } = svgToStrokes(
      svg('<rect width="10" height="10"/><circle r="5"/><path d="M 0 0 L 10 0"/>'),
    )
    expect(strokes).toHaveLength(1)
    expect(skipped).toEqual(["rect", "circle"])
  })

  // An arc is outside the supported subset: the file still imports, minus that
  // path, and the uploader tells the human which command lost it.
  it("skips a path using an unsupported command, naming the command", () => {
    const { strokes, skipped } = svgToStrokes(
      svg('<path d="M 0 0 A 5 5 0 0 1 10 10"/><path d="M 0 0 L 10 0"/>'),
    )
    expect(strokes).toHaveLength(1)
    expect(skipped).toEqual(["path (A)"])
  })

  it("skips a path with no `d` and one whose `d` is empty", () => {
    const { skipped } = svgToStrokes(svg('<path/><path d=""/>'))
    expect(skipped).toEqual(["path", "path"])
  })

  it("ignores geometry inside <defs> — definitions are not rendered", () => {
    const { strokes, skipped } = svgToStrokes(
      svg('<defs><path d="M 0 0 L 100 100"/></defs><path d="M 0 0 L 10 0"/>'),
    )
    expect(skipped).toEqual([])
    expect(strokes).toEqual([{ d: "M 10 100 L 190 100", width: DEFAULT_STROKE_WIDTH }])
  })

  it("walks into <g> without reporting it as skipped", () => {
    const { strokes, skipped } = svgToStrokes(svg('<g><path d="M 0 0 L 10 0"/></g>'))
    expect(skipped).toEqual([])
    expect(strokes).toHaveLength(1)
  })

  it("returns the square viewBox and no strokes for an SVG with no geometry", () => {
    const result = svgToStrokes(svg("<title>empty</title>"))
    expect(result).toEqual({ strokes: [], viewBox: GLYPH_CANVAS_VIEWBOX, skipped: [] })
  })

  it("throws on input that is not an SVG at all", () => {
    expect(() => svgToStrokes("not xml")).toThrowError(/Could not parse/)
    expect(() => svgToStrokes("<html><body/></html>")).toThrowError(/Could not parse/)
  })

  // Re-importing an already-imported glyph must not shrink it a second time:
  // the content already fills 90% of the square, so the fit is a no-op.
  it("is stable when re-imported", () => {
    const once = svgToStrokes(svg('<path d="M 0 0 L 10 0 L 10 6"/>'))
    const twice = svgToStrokes(
      svg(once.strokes.map((s) => `<path d="${s.d}"/>`).join(""), `viewBox="${once.viewBox}"`),
    )
    expect(twice.strokes).toEqual(once.strokes)
  })
})
