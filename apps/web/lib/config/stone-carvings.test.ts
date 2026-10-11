import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import {
  SHAPE_SEEDS_FROM_WEB,
  STONE_CARVINGS_FROM_WEB,
  parseShapeSeed,
  renderStoneCarvings,
  shapeSeedFile,
} from "@/lib/utils/spine/shape-seed"
import { STONE_CARVINGS } from "./stone-carvings"

const webRoot = fileURLToPath(new URL("../..", import.meta.url))
const readSeed = (file: string) => readFileSync(path.join(webRoot, SHAPE_SEEDS_FROM_WEB, file), "utf8")

describe("stone-carvings", () => {
  it("is what the generator writes from docs/seeds/shape-seeds", () => {
    expect(readFileSync(path.join(webRoot, STONE_CARVINGS_FROM_WEB), "utf8")).toBe(renderStoneCarvings(readSeed))
  })

  it("carries 0/0/0, 1/1/2 and 1/2/6 veins, lowlight to highlight", () => {
    const counts = (["small", "medium", "large"] as const).map((size) =>
      (["lowlight", "neutral", "highlight"] as const).map((p) => STONE_CARVINGS[size][p].veins.length),
    )
    expect(counts).toEqual([
      [0, 0, 0],
      [1, 1, 2],
      [1, 2, 6],
    ])
  })

  it("gives every shape a fossil except large-highlight", () => {
    expect(STONE_CARVINGS.large.highlight.fossil).toBeNull()
    const others = Object.values(STONE_CARVINGS)
      .flatMap((row) => Object.values(row))
      .filter((c) => c !== STONE_CARVINGS.large.highlight)
    expect(others.every((c) => c.fossil?.fillRule === "evenodd")).toBe(true)
  })
})

describe("parseShapeSeed", () => {
  it("picks large-neutral's outline by extent, though the seed lists it third", () => {
    const svg = readSeed(shapeSeedFile("large", "neutral"))
    const strokes = [...svg.matchAll(/<path d="([^"]*)" stroke=/g)].map((m) => m[1])
    expect(strokes).toHaveLength(3)
    const seed = parseShapeSeed(svg)
    expect(seed?.outline.d).toBe(strokes[2])
    expect(seed?.veins.map((v) => v.d)).toEqual(strokes.slice(0, 2))
  })

  it("reads the canvas from the viewBox and the stroke width as written", () => {
    const seed = parseShapeSeed(readSeed(shapeSeedFile("small", "neutral")))
    expect(seed).toMatchObject({ width: 250, height: 200, outline: { width: 6 } })
  })

  it("ignores the clip rect medium-neutral wraps itself in", () => {
    const seed = parseShapeSeed(readSeed(shapeSeedFile("medium", "neutral")))
    expect(seed?.veins).toHaveLength(1)
    expect(seed?.fossil?.fillRule).toBe("evenodd")
  })

  it("returns null for a seed with no viewBox or no stroke", () => {
    expect(parseShapeSeed('<svg><path d="M0 0L1 1" stroke="black"/></svg>')).toBeNull()
    expect(parseShapeSeed('<svg viewBox="0 0 10 10"><path d="M0 0L1 1" fill="black"/></svg>')).toBeNull()
  })
})
