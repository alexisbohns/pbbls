import { describe, expect, it } from "vitest"
import { FAN_REFERENCE, stoneCentre, stoneFrame, stoneHeight, stoneWidth } from "./fan-layout"
import { ALL_CELLS, POLARITY_ORDER, type ValenceCell } from "./valence"

const SIZES = ["small", "medium", "large"] as const

/**
 * The fan's constants are eye-tuned, and no test can say whether it looks
 * right. These are the two things that would be broken by tuning and would not
 * be obvious from a screenshot on the one device it was tuned on — the same two
 * `ValenceFanLayoutTests` asserts on iOS.
 */
describe("fan layout", () => {
  it("keeps every stone inside the canvas with room to spare", () => {
    // If tuning ever breaks this, bring the spreads in before shrinking the
    // radii: the vertical rhythm is what carries the fan.
    const margin = 8
    for (const cell of ALL_CELLS) {
      const frame = stoneFrame(cell)
      expect(frame.x, `${cell.size}-${cell.polarity} left`).toBeGreaterThanOrEqual(margin)
      expect(frame.y, `${cell.size}-${cell.polarity} top`).toBeGreaterThanOrEqual(margin)
      expect(frame.x + frame.width).toBeLessThanOrEqual(FAN_REFERENCE.width - margin)
      expect(frame.y + frame.height).toBeLessThanOrEqual(FAN_REFERENCE.height - margin)
    }
  })

  it("orders lowlight left of neutral left of highlight in every ring", () => {
    for (const size of SIZES) {
      const centres = POLARITY_ORDER.map((polarity) => stoneCentre({ size, polarity }).x)
      expect(centres[0], `${size} lowlight < neutral`).toBeLessThan(centres[1])
      expect(centres[1], `${size} neutral < highlight`).toBeLessThan(centres[2])
    }
  })

  it("grows stones and lifts them as the fan rises", () => {
    expect(stoneHeight("small")).toBeLessThan(stoneHeight("medium"))
    expect(stoneHeight("medium")).toBeLessThan(stoneHeight("large"))
    for (const polarity of POLARITY_ORDER) {
      const y = SIZES.map((size) => stoneCentre({ size, polarity }).y)
      expect(y[0], `${polarity} small sits below medium`).toBeGreaterThan(y[1])
      expect(y[1], `${polarity} medium sits below large`).toBeGreaterThan(y[2])
    }
  })

  it("bows each ring into an arc, with the neutral stone highest", () => {
    for (const size of SIZES) {
      const centre = stoneCentre({ size, polarity: "neutral" }).y
      for (const polarity of ["lowlight", "highlight"] as const) {
        expect(centre, `${size} ${polarity}`).toBeLessThan(stoneCentre({ size, polarity }).y)
      }
    }
  })

  it("keeps each stone's real silhouette proportions", () => {
    // Small is wider than tall, large is taller than wide — the outline assets'
    // own aspect ratios, not a square box.
    expect(stoneWidth("small")).toBeGreaterThan(stoneHeight("small"))
    expect(stoneWidth("large")).toBeLessThan(stoneHeight("large"))
  })

  it("is deterministic", () => {
    const cell: ValenceCell = { size: "medium", polarity: "highlight" }
    expect(stoneCentre(cell)).toEqual(stoneCentre(cell))
  })
})
