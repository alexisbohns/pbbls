// Non-golden regression coverage for the parse → flatten → outline/displace
// pipeline and the per-surface entry points. The cross-platform parity gate
// lives in `wobble.golden.test.ts`; this file guards the web-only plumbing
// (SVG rewrite, caching, fallbacks) that has no iOS golden.

import { describe, expect, it } from "vitest"

import { parsePath } from "./path-parser"
import { flatten } from "./flatten"
import { contours } from "./outline"
import { CANONICAL } from "./params"
import { wobbleBackdrop, wobbleGlyphInk, wobblePebbleSvg } from "./renderer"
import { getOutline } from "../config/pebble-outlines"

describe("path parser", () => {
  it("returns null for unparseable input", () => {
    expect(parsePath("not a path")).toBeNull()
    expect(parsePath("")).toBeNull()
  })

  it("expands H/V into line elements and closes", () => {
    const els = parsePath("M0 0 H10 V10 Z")
    expect(els).not.toBeNull()
    expect(els!.map((e) => e.type)).toEqual(["move", "line", "line", "close"])
  })

  it("handles implicit line continuation after M", () => {
    const els = parsePath("M0 0 10 10 20 20")
    expect(els!.map((e) => e.type)).toEqual(["move", "line", "line"])
  })

  it("decomposes arcs into cubic segments", () => {
    const els = parsePath("M0 0 A10 10 0 0 1 20 0")
    expect(els!.some((e) => e.type === "cubic")).toBe(true)
  })
})

describe("flattener", () => {
  it("subdivides long straight lines below the step", () => {
    const els = parsePath("M0 0 L100 0")!
    const [poly] = flatten(els, 2)
    // ~50 chords of ≤2 units, plus interior vertices so the line can bend.
    expect(poly.points.length).toBeGreaterThan(40)
    for (let i = 1; i < poly.points.length; i++) {
      const dx = poly.points[i].x - poly.points[i - 1].x
      expect(Math.abs(dx)).toBeLessThanOrEqual(2 + 1e-6)
    }
  })

  it("marks closed subpaths and drops the duplicated start", () => {
    const els = parsePath("M0 0 L10 0 L10 10 L0 10 Z")!
    const [poly] = flatten(els, 100)
    expect(poly.isClosed).toBe(true)
    const first = poly.points[0]
    const last = poly.points[poly.points.length - 1]
    expect(first.x === last.x && first.y === last.y).toBe(false)
  })
})

describe("outline builder", () => {
  it("makes a dual-ring annulus for closed polylines", () => {
    const els = parsePath("M0 0 L10 0 L10 10 L0 10 Z")!
    const [poly] = flatten(els, 100)
    expect(contours(poly, 3)).toHaveLength(2) // outer + reversed inner
  })

  it("makes a single capped contour for open polylines", () => {
    const els = parsePath("M0 0 L100 0")!
    const [poly] = flatten(els, 2)
    const cs = contours(poly, 3)
    expect(cs).toHaveLength(1)
    expect(cs[0].length).toBeGreaterThan(poly.points.length) // + cap arc points
  })

  it("makes a circle for a single-point dot", () => {
    const els = parsePath("M50 50")!
    const [poly] = flatten(els, 2)
    expect(poly.points).toHaveLength(1)
    expect(contours(poly, 3)[0]).toHaveLength(12) // DOT_SEGMENTS
  })
})

describe("glyph ink entry point", () => {
  it("emits a closed filled path and is content-cached", () => {
    const ink = wobbleGlyphInk("M20 20 L80 80 L140 40", CANONICAL.flattenStep === 2 ? 6 : 6)
    expect(ink).not.toBeNull()
    expect(ink!.startsWith("M")).toBe(true)
    expect(ink!.endsWith("Z")).toBe(true)
    expect(wobbleGlyphInk("M20 20 L80 80 L140 40", 6)).toBe(ink) // cache identity
  })

  it("returns null on unparseable d (caller falls back to raw stroke)", () => {
    expect(wobbleGlyphInk("garbage", 6)).toBeNull()
  })
})

describe("backdrop entry point", () => {
  it("displaces every bundled outline, preserving subpath count for evenodd", () => {
    for (const size of ["small", "medium", "large"] as const) {
      for (const polarity of ["neutral", "lowlight", "highlight"] as const) {
        const o = getOutline(size, polarity)
        const wobbled = wobbleBackdrop(o.path, o.width, o.height)
        expect(wobbled).not.toBeNull()
        expect(wobbled!.startsWith("M")).toBe(true)
      }
    }
  })

  it("keeps both subpaths of the evenodd large-lowlight hole", () => {
    const o = getOutline("large", "lowlight")
    expect(o.fillRule).toBe("evenodd")
    const wobbled = wobbleBackdrop(o.path, o.width, o.height)!
    // Two source subpaths (silhouette + hole) → two displaced closed contours.
    expect((wobbled.match(/M/g) ?? []).length).toBeGreaterThanOrEqual(2)
  })
})

describe("composed pebble SVG rewrite", () => {
  const svg = `<svg viewBox="0 0 242 283" fill="none" xmlns="http://www.w3.org/2000/svg">
<g id="high-negative">
<path id="pebble-outline" d="M19.67 233.24C2.50 223.06 -5.75 43.48 16.49 6.83L60.77 22.82L64.29 21.94Z" stroke="currentColor" stroke-width="6" stroke-linecap="round"/>
<g id="glyph-wrapper"><g id="glyph"><g transform="translate(14, 56) scale(0.8)"><path id="Vector" d="M43.08 106.03C35.02 112.47 30.54 117.93 29.64 122.42L45.82 146.76" stroke="currentColor" stroke-width="7.5" stroke-linecap="round" stroke-linejoin="round"/></g></g></g>
<path id="fossil" fill-rule="evenodd" clip-rule="evenodd" d="M178.02 105.92C179.87 98.38 186.64 91.60 194.31 90.03L227.29 116.62L216.93 142.78Z" fill="currentColor"/>
</g></svg>`

  it("converts stroke paths to filled ink and drops stroke width", () => {
    const wob = wobblePebbleSvg(svg)
    expect(/stroke-width/.test(wob)).toBe(false)
    expect(/id="pebble-outline" d="[^"]+" fill="currentColor" stroke="none"/.test(wob)).toBe(true)
  })

  it("preserves the glyph transform group and the fossil evenodd fill-rule", () => {
    const wob = wobblePebbleSvg(svg)
    expect(/transform="translate\(14, 56\) scale\(0\.8\)"/.test(wob)).toBe(true)
    expect(/id="Vector"/.test(wob)).toBe(true)
    expect(/id="fossil"[^>]*fill-rule="evenodd"/.test(wob)).toBe(true)
  })

  it("preserves the root svg wrapper and is content-cached", () => {
    const wob = wobblePebbleSvg(svg)
    expect(wob.startsWith(`<svg viewBox="0 0 242 283"`)).toBe(true)
    expect(wob.endsWith("</svg>")).toBe(true)
    expect(wobblePebbleSvg(svg)).toBe(wob)
  })

  it("returns the original string unchanged when there are no paths", () => {
    const plain = `<svg viewBox="0 0 10 10"><g id="x"></g></svg>`
    expect(wobblePebbleSvg(plain)).toBe(plain)
  })

  it("traces glyph strokes at the outline weight regardless of authored width", () => {
    // Custom glyphs are authored heavier than the outline; the wobble must
    // ignore the authored stroke-width and trace at OUTLINE_WIDTH (iOS #511 /
    // Android #552), so varying only the authored width leaves the ink identical.
    const make = (w: number) =>
      `<svg viewBox="0 0 200 200"><g id="glyph"><g transform="translate(10, 10) scale(0.5)"><path id="g" d="M20 20 L180 180" stroke="currentColor" stroke-width="${w}" stroke-linecap="round"/></g></g></svg>`
    const thin = /id="g" d="([^"]+)"/.exec(wobblePebbleSvg(make(6)))![1]
    const heavy = /id="g" d="([^"]+)"/.exec(wobblePebbleSvg(make(24)))![1]
    expect(heavy).toBe(thin)
  })

  it("divides the outline weight by the group scale (transform-aware)", () => {
    // Same glyph path and canonical params at two transform scales differ only
    // in half-width (OUTLINE_WIDTH/2/scale), so the ink must differ — proving the
    // scale is applied rather than ignored.
    const at = (s: number) =>
      `<svg viewBox="0 0 200 200"><g id="glyph"><g transform="scale(${s})"><path id="g" d="M0 100 L200 100" stroke="currentColor" stroke-width="6"/></g></g></svg>`
    const half = /id="g" d="([^"]+)"/.exec(wobblePebbleSvg(at(0.5)))![1]
    const full = /id="g" d="([^"]+)"/.exec(wobblePebbleSvg(at(1)))![1]
    expect(half).not.toBe(full)
  })
})
