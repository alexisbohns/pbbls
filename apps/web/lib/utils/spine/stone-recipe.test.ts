import { describe, expect, it } from "vitest"
import { hashUUID } from "@/lib/engine/seed"
import { glintGeometry, starPath, stoneRecipe } from "./stone-recipe"

const SEED = hashUUID("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b")

describe("stoneRecipe", () => {
  it("draws the same stone for the same seed", () => {
    expect(stoneRecipe("medium", "highlight", SEED)).toEqual(stoneRecipe("medium", "highlight", SEED))
  })

  it("draws a different stone for another pebble", () => {
    const other = hashUUID("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01")
    expect(stoneRecipe("medium", "lowlight", other)).not.toEqual(stoneRecipe("medium", "lowlight", SEED))
  })

  it("cuts a gem into five facets and a face, with ten glitter dots and two sparkles", () => {
    const gem = stoneRecipe("large", "highlight", SEED)
    if (gem.material !== "gem") throw new Error("expected a gem")
    expect(gem.facets).toHaveLength(6)
    expect(gem.facets.slice(0, 5).every((f) => f.points.length === 4 && f.edge)).toBe(true)
    expect(gem.facets[5]).toMatchObject({ edge: null, opacity: 0.14 })
    expect(gem.facets[5].points).toHaveLength(5)
    expect(gem.dots).toHaveLength(10)
    expect(gem.dots.every((d) => d.slot === "light")).toBe(true)
    expect(gem.sparkles).toHaveLength(2)
  })

  it("shades the facets from the upper-left light: lit, lit, dark, dark, lit", () => {
    const gem = stoneRecipe("medium", "highlight", SEED)
    if (gem.material !== "gem") throw new Error("expected a gem")
    expect(gem.facets.slice(0, 5).map((f) => f.slot)).toEqual(["white", "white", "dark", "dark", "white"])
  })

  it("grains a river pebble with fourteen specks, lit and shaded", () => {
    const pebble = stoneRecipe("small", "neutral", SEED)
    expect(pebble.material).toBe("pebble")
    expect(pebble.dots).toHaveLength(14)
    expect(pebble.dots.every((d) => d.slot === "light" || d.slot === "shaded")).toBe(true)
  })

  it("pits lava nine times in its dark", () => {
    const lava = stoneRecipe("medium", "lowlight", SEED)
    expect(lava.material).toBe("lava")
    expect(lava.dots).toHaveLength(9)
    expect(lava.dots.every((d) => d.slot === "dark")).toBe(true)
  })

  it("keeps every dot inside the canvas and its radius the same size on screen", () => {
    for (const [size, width, px] of [
      ["small", 250, 34],
      ["large", 260, 54],
    ] as const) {
      for (const d of stoneRecipe(size, "lowlight", SEED).dots) {
        expect(d.cx).toBeGreaterThan(0)
        expect(d.cx).toBeLessThan(width)
        expect((d.r * px) / width).toBeGreaterThanOrEqual(0.4 - 0.01)
        expect((d.r * px) / width).toBeLessThanOrEqual(0.99 + 0.01)
      }
    }
  })
})

describe("glintGeometry", () => {
  it("matches the mock's medium glint", () => {
    const glint = glintGeometry("medium")
    expect(glint.sheen).toMatchObject({ cx: 98.8, cy: 78, rx: 78, rotate: -24 })
    expect(glint.travel).toBe(416)
    expect(glint.sweep).toMatchObject({ x: -143, y: -104, height: 468 })
  })
})

describe("starPath", () => {
  it("draws the mock's four-point spark", () => {
    expect(starPath(82.5, 50, 21.25)).toBe(
      "M82.5 28.75Q85.9 46.6 103.75 50Q85.9 53.4 82.5 71.25Q79.1 53.4 61.25 50Q79.1 46.6 82.5 28.75Z",
    )
  })
})
