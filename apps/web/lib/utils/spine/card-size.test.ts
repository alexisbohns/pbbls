import { describe, expect, it } from "vitest"
import { CARD_MIN_WIDTH, cardCap, cardEdge, cardSize, type Intensity } from "./card-size"

// Columns from the spec: 290 px on a 390 px phone, 576 px on the spread.
const PHONE = 290
const WIDE = 576

// The spec's sizing table, width × height in px, rounded there; ±1 px here.
const TABLE: Array<[string, number, Record<"phone" | "wide", Record<Intensity, [number, number]>>]> = [
  ["16:9", 16 / 9, {
    phone: { 1: [290, 233], 2: [290, 233], 3: [290, 233] },
    wide: { 1: [481, 340], 2: [576, 394], 3: [576, 394] },
  }],
  ["3:2", 3 / 2, {
    phone: { 1: [290, 253], 2: [290, 253], 3: [290, 253] },
    wide: { 1: [420, 340], 2: [510, 400], 3: [576, 444] },
  }],
  ["1:1", 1, {
    phone: { 1: [290, 318], 2: [290, 318], 3: [290, 318] },
    wide: { 1: [312, 340], 2: [372, 400], 3: [432, 460] },
  }],
  ["2:3", 2 / 3, {
    phone: { 1: [233, 330], 2: [260, 370], 3: [280, 400] },
    wide: { 1: [240, 340], 2: [280, 400], 3: [320, 460] },
  }],
  ["9:16", 9 / 16, {
    phone: { 1: [211, 330], 2: [234, 370], 3: [251, 400] },
    wide: { 1: [217, 340], 2: [251, 400], 3: [285, 460] },
  }],
]

describe("cardSize", () => {
  for (const [label, ratio, rows] of TABLE) {
    for (const intensity of [1, 2, 3] as const) {
      it(`${label} i${intensity} matches the table on phone and wide`, () => {
        const phone = cardSize({ intensity, ratio, column: PHONE, wide: false })
        const wide = cardSize({ intensity, ratio, column: WIDE, wide: true })
        const [pw, ph] = rows.phone[intensity]
        const [ww, wh] = rows.wide[intensity]
        expect(Math.abs(phone.width - pw)).toBeLessThanOrEqual(1)
        expect(Math.abs(phone.height - ph)).toBeLessThanOrEqual(1)
        expect(Math.abs(wide.width - ww)).toBeLessThanOrEqual(1)
        expect(Math.abs(wide.height - wh)).toBeLessThanOrEqual(1)
      })
    }
  }

  it("never lets a fitted card exceed its cap", () => {
    for (const [, ratio] of TABLE) {
      for (const intensity of [1, 2, 3] as const) {
        const size = cardSize({ intensity, ratio, column: WIDE, wide: true })
        expect(size.height).toBeLessThanOrEqual(size.cap + 1e-9)
      }
    }
  })

  it("floors a very tall photo at 180 px and lets it exceed its cap", () => {
    const size = cardSize({ intensity: 1, ratio: 0.4, column: PHONE, wide: false })
    expect(size.width).toBe(CARD_MIN_WIDTH)
    expect(size.height).toBeGreaterThan(size.cap)
  })

  it("never floors past the column itself", () => {
    expect(cardSize({ intensity: 1, ratio: 0.3, column: 160, wide: false }).width).toBe(160)
  })

  it("holds a square while the ratio is unknown (D14)", () => {
    const pending = cardSize({ intensity: 2, ratio: Number.NaN, column: WIDE, wide: true })
    const square = cardSize({ intensity: 2, ratio: 1, column: WIDE, wide: true })
    expect(pending).toEqual(square)
    expect(cardSize({ intensity: 2, ratio: 0, column: WIDE, wide: true })).toEqual(square)
  })
})

describe("cardCap and cardEdge", () => {
  it("selects the cap by intensity × breakpoint", () => {
    expect([1, 2, 3].map((i) => cardCap(i as Intensity, false))).toEqual([330, 370, 400])
    expect([1, 2, 3].map((i) => cardCap(i as Intensity, true))).toEqual([340, 400, 460])
  })

  it("thickens the painted edge with intensity", () => {
    expect([1, 2, 3].map((i) => cardEdge(i as Intensity))).toEqual([2, 3.5, 5.5])
  })
})
