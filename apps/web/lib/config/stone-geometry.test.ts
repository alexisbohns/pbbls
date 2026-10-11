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

const ENGINE_LAYOUT = fileURLToPath(
  new URL("../../../../packages/supabase/supabase/functions/_shared/engine/layout.ts", import.meta.url),
)
const layout = readFileSync(ENGINE_LAYOUT, "utf8")

/** The object literal assigned to `const NAME` in the engine file, as JSON. */
function engineConst(name: string): unknown {
  const start = layout.indexOf(`const ${name}`)
  const open = layout.indexOf("= {", start) + 2
  let depth = 0
  let end = open
  for (; end < layout.length; end++) {
    if (layout[end] === "{") depth++
    if (layout[end] === "}" && --depth === 0) break
  }
  const literal = layout
    .slice(open, end + 1)
    .replace(/\/\/.*$/gm, "")
    .replace(/(\w+)\s*:/g, '"$1":')
    .replace(/,(\s*})/g, "$1")
  return JSON.parse(literal)
}

const SIZES = ["small", "medium", "large"] as const
const POLARITIES = ["lowlight", "neutral", "highlight"] as const

describe("glyph zones", () => {
  it("equal the engine's GLYPH_SIZE", () => {
    expect(GLYPH_SIZE).toEqual(engineConst("GLYPH_SIZE"))
  })

  it("equal the engine's GLYPH_POSITION", () => {
    expect(GLYPH_POSITION).toEqual(engineConst("GLYPH_POSITION"))
  })

  it("equal the engine's canvas sizes", () => {
    expect(SEED_CANVAS).toEqual(engineConst("CANVAS"))
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
