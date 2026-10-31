import { describe, expect, it } from "vitest"
import { arcPath, polar, unitHash } from "./geometry"
import { facetTone, rosaceGeometry } from "./rosace"
import { shards } from "./shards"
import { contour, stageAlone, stageClip, stageIndex, stageOf } from "./skyline"
import { STONE_GRAIN, discMonth } from "./year-disc"
import { emotionAngle, firstFiveAngle, seatAngle } from "./wheel"
import type { YearMonth } from "@/lib/types"

// The geometry against the approved boards' own output (ach/final/_build/app.js)
// on the same inputs: the port draws what the maintainer approved.

const month = (m: Partial<YearMonth> & Pick<YearMonth, "month">): YearMonth => ({
  state: "kept",
  family: null,
  momentDays: 0,
  pebbleDays: 0,
  fullWeeks: 4,
  weeksFilled: 0,
  level: 0,
  progress: 0,
  done: { moments: false, pebbles: false, weeks: false },
  ...m,
})

describe("geometry", () => {
  it("measures clockwise from twelve o'clock", () => {
    const [x, y] = polar(10, 90)
    expect(x).toBeCloseTo(10)
    expect(y).toBeCloseTo(0)
  })

  it("draws the joy family's arc on the emotions ring", () => {
    const step = 360 / 38
    const a0 = emotionAngle(0, 38) - step / 2
    const a1 = emotionAngle(5, 38) + step / 2
    expect(arcPath(134, a0 + 1.2, a1 - 1.2)).toBe("M-61.3 -119.2A134 134 0 0 1 61.3 -119.2")
  })

  it("hashes as the boards do (FNV-1a modulo 100 000)", () => {
    expect(unitHash("R3t")).toBe(0.64242)
  })

  it("spreads a ring by the served count", () => {
    expect([0, 1, 4].map((i) => firstFiveAngle(i, 5))).toEqual([0, 72, 288])
    expect([0, 5].map((i) => seatAngle(i, 6))).toEqual([30, 330])
    expect(seatAngle(16, 17)).toBeCloseTo((16.5 * 360) / 17)
    expect(emotionAngle(1, 37) - emotionAngle(0, 37)).toBeCloseTo(360 / 37)
  })
})

describe("the rosace", () => {
  const g = rosaceGeometry(270, 38, true)

  it("cuts the boards' kites and closes its silhouette", () => {
    expect(g.kite(7)).toBe("M183.63,72.52L198.09,66.47L193.25,81.37L164.65,102.79Z")
    expect(g.silhouette.startsWith("M") && g.silhouette.endsWith("Z")).toBe(true)
    expect(g.silhouette.split("L")).toHaveLength(38 * 2)
  })

  it("seats as many kites as the server sends", () => {
    expect(rosaceGeometry(270, 37, true).silhouette.split("L")).toHaveLength(37 * 2)
    expect(rosaceGeometry(270, 0, true).silhouette).toBe("")
  })

  it("tones a facet along its family, softer between two lit neighbours", () => {
    expect(facetTone(0, false)).toEqual({
      paper: "color-mix(in oklab, var(--sh) 8%, var(--s))",
      night: "color-mix(in oklab, var(--p) 0%, var(--s))",
    })
    expect(facetTone(1, true).paper).toBe(
      "color-mix(in oklab, var(--spine-stock) 38%, color-mix(in oklab, var(--sh) 80%, var(--s)))",
    )
  })
})

describe("the band", () => {
  it("leans the seams as the boards do, the band square at both ends", () => {
    const band = shards(18, 300, 30)
    expect(band).toHaveLength(18)
    expect(band[4].d).toBe("M68.33 0 L87.30 0 L84.52 30 L65.55 30Z")
    expect(band[17].d).toBe("M284.53 0 L300.00 0 L300.00 30 L286.09 30Z")
    expect(band[0].d.startsWith("M0.00 0 ")).toBe(true)
    expect(band[13].centre).toBeCloseTo((13.5 * 300) / 18)
  })

  it("spaces what the server sent", () => {
    expect(shards(17, 300, 30)[16].d).toMatch(/L300\.00 0 L300\.00 30 /)
  })
})

describe("the skyline", () => {
  it("draws the ground through the first stages", () => {
    expect(contour(2, 126)).toBe(
      "M16 148 L23 148 C23 143.3 29 143.3 29 148 L32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 L126 148",
    )
  })

  it("fills a stage from the ground by the served progress", () => {
    const clip = stageClip(5, 0.247)
    expect(clip.x).toBe(123.5)
    expect(clip.width).toBe(12.5)
    expect(clip.y).toBeCloseTo(140.44, 2)
    expect(clip.height).toBeCloseTo(8.56, 2)
  })

  it("sets a stage alone in a register mark, the last stone past the eighth", () => {
    expect(stageAlone(3, 0.8).transform).toBe("scale(0.839) translate(-85.50 -136.60)")
    expect([stageIndex(-1), stageIndex(9)]).toEqual([0, 7])
    expect(stageOf(250, [1, 10, 25, 50, 100, 250, 500, 1000])).toBe(5)
    expect(stageOf(1500, [1, 10, 25, 50, 100, 250, 500, 1000])).toBe(7)
    expect(stageOf(75, [10, 25, 50, 75, 100])).toBe(3)
  })
})

describe("the year disc", () => {
  it("carries the boards' forty grains", () => {
    expect(STONE_GRAIN).toHaveLength(40)
  })

  it("docks a month at its level and runs its progress along the spoke", () => {
    const april = discMonth(month({ month: 4, family: "sadness", level: 1, progress: 0.857 }))
    expect(april.counter).toEqual({ x: 170, y: 120, ring: false, print: "solid", dashed: false })
    expect(april.climb).toEqual({ x1: 176, y1: 120, x2: 191.4, y2: 120, hued: true })
    expect(april.spoke).toMatchObject({ width: 0.7, hued: false })
    expect(april.tone).toEqual({ muted: false, family: "sadness" })
  })

  it("rings a rim month and hues its spoke and numeral", () => {
    const may = discMonth(month({ month: 5, family: "peace", level: 2 }))
    expect(may.counter.ring).toBe(true)
    expect(may.spoke).toMatchObject({ width: 1, hued: true })
    expect(may.numeral).toEqual({ x: 203.1, y: 170.5, text: "V", size: 7, emphasized: true })
    expect(may.notches.map((n) => n.width)).toEqual([1, 1])
    expect(may.climb).toMatchObject({ hued: true })
  })

  it("keeps a rim month with no resolvable family on the rim, its tone ink", () => {
    const august = discMonth(month({ month: 8, family: null, level: 2 }))
    expect(august.tone).toEqual({ muted: false, family: null })
    expect(august.counter).toMatchObject({ ring: true, print: "solid" })
    expect(august.spoke.hued).toBe(true)
  })

  it("marks a begun month's goal on the inner circle, its climb in ink", () => {
    const november = discMonth(month({ month: 11, family: "joy", level: 0, progress: 0.214, fullWeeks: 0 }))
    expect(november.goal).toEqual({ x1: 78.8, y1: 91.6, x2: 74.8, y2: 98.6 })
    expect(november.counter).toMatchObject({ x: 94, y: 105 })
    expect(november.climb?.hued).toBe(false)
  })

  it("mutes a month on its state alone: quiet and upcoming, never a kept month without a family", () => {
    const february = discMonth(month({ month: 2, state: "quiet" }))
    expect(february.tone).toEqual({ muted: true })
    expect(february.spoke.width).toBe(0.4)
    expect(february.notches).toEqual([
      { x1: 142.4, y1: 75.2, x2: 147.6, y2: 78.2, width: 0.6 },
      { x1: 157.4, y1: 49.2, x2: 162.6, y2: 52.2, width: 0.6 },
    ])
    expect(february.goal).toBeNull()
    expect(february.counter).toMatchObject({ print: "blank", dashed: false, ring: false })
    expect(discMonth(month({ month: 12, state: "upcoming" })).counter).toMatchObject({ print: "blank", dashed: true })
    expect(discMonth(month({ month: 3, family: null })).tone).toEqual({ muted: false, family: null })
  })
})
