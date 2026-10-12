import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { getOutline } from "./pebble-outlines"
import {
  GLYPH_POSITION,
  GLYPH_SIZE,
  RING_COUNT,
  SEED_CANVAS,
  carveTransform,
  ringGeometry,
  silhouetteFrame,
  unitsPerPx,
} from "./stone-geometry"

// The render engine left the server with the moments split (#1046), so the
// zones are checked against the surfaces that still draw them: iOS's carving
// tables (spec D3, web follows iOS geometry) and the shape seeds' canvases.
const IOS_CARVING = fileURLToPath(
  new URL("../../../ios/Pebbles/Features/Path/Stone/StoneCarvingArt.swift", import.meta.url),
)
const ios = readFileSync(IOS_CARVING, "utf8")

/** The body of `static let NAME … = [ … ]` in the iOS file. */
function iosTable(name: string): string {
  const start = ios.indexOf(`static let ${name}`)
  const open = ios.indexOf("= [", start) + 2
  let depth = 0
  let end = open
  for (; end < ios.length; end++) {
    if (ios[end] === "[") depth++
    if (ios[end] === "]" && --depth === 0) break
  }
  return ios.slice(open, end + 1)
}

function iosGlyphSize(): Record<string, number> {
  return Object.fromEntries([...iosTable("glyphSize").matchAll(/\.(\w+):\s*([\d.]+)/g)].map(([, k, v]) => [k, Number(v)]))
}

function iosGlyphPosition(): Record<string, Record<string, { x: number; y: number }>> {
  const table: Record<string, Record<string, { x: number; y: number }>> = {}
  for (const [, size, row] of iosTable("glyphPosition").matchAll(/\.(\w+):\s*\[([^\]]*)\]/g)) {
    table[size] = Object.fromEntries(
      [...row.matchAll(/\.(\w+):\s*CGPoint\(x:\s*([\d.]+),\s*y:\s*([\d.]+)\)/g)].map(([, polarity, x, y]) => [
        polarity,
        { x: Number(x), y: Number(y) },
      ]),
    )
  }
  return table
}

function seedCanvas(size: string, polarity: string): { width: number; height: number } {
  const svg = readFileSync(fileURLToPath(new URL(`../../../../docs/seeds/shape-seeds/${size}-${polarity}.svg`, import.meta.url)), "utf8")
  const [, , , width, height] = /viewBox="([\d.]+) ([\d.]+) ([\d.]+) ([\d.]+)"/.exec(svg) ?? []
  return { width: Number(width), height: Number(height) }
}

const SIZES = ["small", "medium", "large"] as const
const POLARITIES = ["lowlight", "neutral", "highlight"] as const

describe("glyph zones", () => {
  it("equal iOS's glyph slot sizes", () => {
    expect(GLYPH_SIZE).toEqual(iosGlyphSize())
  })

  it("equal iOS's glyph positions", () => {
    expect(GLYPH_POSITION).toEqual(iosGlyphPosition())
  })

  it("use the shape seeds' canvases", () => {
    for (const size of SIZES) {
      for (const polarity of POLARITIES) expect(SEED_CANVAS[size]).toEqual(seedCanvas(size, polarity))
    }
  })
})

describe("carveTransform", () => {
  it("places the seed at 1.22 × the engine inset, centred in the silhouette box", () => {
    // pebbleScale is seed width over outline width, so the product is 1.22
    // seed → outline for every size, as iOS draws it.
    expect(carveTransform("medium", "neutral")).toBe("matrix(1.22 0 0 1.22 16.4 16.4)")
    const [, s] = /matrix\(([\d.]+)/.exec(carveTransform("large", "highlight")) ?? []
    expect(Number(s)).toBeCloseTo(1.22, 2)
  })
})

describe("ringGeometry", () => {
  it.each(SIZES)("draws %s with its ring count", (size) => {
    for (const polarity of POLARITIES) expect(ringGeometry(size, polarity).rings).toHaveLength(RING_COUNT[size])
  })

  it("steps each ring the same distance out, and the halo past the last", () => {
    const outline = getOutline("large", "highlight")
    const { width } = silhouetteFrame(outline.path)
    const sx = (m: string) => Number(/matrix\(([\d.]+)/.exec(m)?.[1])
    const { rings, halo } = ringGeometry("large", "highlight")
    const stepPx = rings.map((m, k) => ((sx(m) - 1) * width) / 2 / unitsPerPx("large") / (k + 1))
    for (const step of stepPx) expect(step).toBeCloseTo(4.2, 1)
    expect(((sx(halo) - 1) * width) / 2 / unitsPerPx("large")).toBeCloseTo(4.2 * 3.78, 0)
  })

  it("scales about a centroid inside the silhouette's box", () => {
    for (const size of SIZES) {
      for (const polarity of POLARITIES) {
        const outline = getOutline(size, polarity)
        const { cx, cy } = silhouetteFrame(outline.path)
        expect(cx).toBeGreaterThan(outline.width * 0.3)
        expect(cx).toBeLessThan(outline.width * 0.7)
        expect(cy).toBeGreaterThan(outline.height * 0.3)
        expect(cy).toBeLessThan(outline.height * 0.7)
      }
    }
  })
})
