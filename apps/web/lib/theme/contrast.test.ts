import { describe, expect, it } from "vitest"
import { contrastRatio, toHex } from "./contrast"

describe("contrastRatio", () => {
  it("is 21 for black on white and 1 for a colour on itself", () => {
    expect(contrastRatio([0, 0, 0], [255, 255, 255])).toBeCloseTo(21, 5)
    expect(contrastRatio([142, 73, 85], [142, 73, 85])).toBeCloseTo(1, 5)
  })

  it("is symmetric and matches the WCAG value for #777 on white", () => {
    expect(contrastRatio([119, 119, 119], [255, 255, 255])).toBeCloseTo(4.48, 2)
    expect(contrastRatio([255, 255, 255], [119, 119, 119])).toBeCloseTo(4.48, 2)
  })
})

describe("toHex", () => {
  it("formats upper-case, zero-padded", () => {
    expect(toHex([142, 73, 85])).toBe("#8E4955")
    expect(toHex([0, 5, 255])).toBe("#0005FF")
  })
})
