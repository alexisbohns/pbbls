import { describe, expect, it } from "vitest"
import { cardTitleCut, contentsNameType, entryType, type Valence } from "./entry-type"
import type { Intensity } from "./card-size"

const INTENSITIES: Intensity[] = [1, 2, 3]
const VALENCES: Valence[] = [-1, 0, 1]

describe("entryType", () => {
  const SIZES: Record<Intensity, [number, number]> = { 1: [19, 21], 2: [24, 27], 3: [31, 35] }
  const CUTS: Record<Valence, [number, boolean, number]> = {
    1: [600, false, -0.012],
    0: [460, false, -0.012],
    [-1]: [340, true, -0.004],
  }

  for (const intensity of INTENSITIES) {
    for (const valence of VALENCES) {
      it(`i${intensity} v${valence} on phone and wide`, () => {
        const [weight, italic, tracking] = CUTS[valence]
        for (const [wide, size] of [[false, SIZES[intensity][0]], [true, SIZES[intensity][1]]] as const) {
          expect(entryType(intensity, valence, wide)).toEqual({
            fontSize: size,
            fontWeight: weight,
            italic,
            letterSpacing: tracking,
            lineHeight: 1.12,
            anchor: size * 0.6,
          })
        }
      })
    }
  }

  it("grows strictly with intensity, so size alone tells it", () => {
    const sizes = INTENSITIES.map((i) => entryType(i, 0, false).fontSize)
    expect(sizes).toEqual([...sizes].sort((a, b) => a - b))
    expect(new Set(sizes).size).toBe(3)
  })
})

describe("contentsNameType", () => {
  it("weighs by intensity and sets lowlights in a light italic", () => {
    expect(INTENSITIES.map((i) => contentsNameType(i, 1, false).fontWeight)).toEqual([400, 470, 620])
    expect(INTENSITIES.map((i) => contentsNameType(i, 0, false).fontWeight)).toEqual([400, 470, 620])
    for (const i of INTENSITIES) {
      expect(contentsNameType(i, -1, false)).toMatchObject({ fontWeight: 350, italic: true })
    }
  })

  it("is a touch smaller on the spread", () => {
    expect(contentsNameType(2, 0, false).fontSize).toBe(16.5)
    expect(contentsNameType(2, 0, true).fontSize).toBe(15.5)
  })
})

describe("cardTitleCut", () => {
  it("cuts by valence", () => {
    expect(cardTitleCut(1)).toEqual({ fontWeight: 600, italic: false })
    expect(cardTitleCut(0)).toEqual({ fontWeight: 480, italic: false })
    expect(cardTitleCut(-1)).toEqual({ fontWeight: 360, italic: true })
  })
})
