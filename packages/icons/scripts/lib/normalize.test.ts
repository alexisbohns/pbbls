import { describe, expect, it } from "vitest"
import { normalizePath } from "./normalize"

describe("normalizePath", () => {
  it("passes M L C Q Z through with two decimals at most", () => {
    expect(normalizePath("M3 3 L4.123 4 C5 5 6 6 7 7 Q8 8 9 9 Z")).toBe("M3 3 L4.12 4 C5 5 6 6 7 7 Q8 8 9 9 Z")
  })

  it("turns a half-circle arc into two cubics ending at the arc's end", () => {
    const out = normalizePath("M4 12 A8 8 0 0 1 20 12")
    expect(out.startsWith("M4 12 C")).toBe(true)
    expect(out.match(/C/g)?.length).toBe(2)
    expect(out.endsWith(" 20 12")).toBe(true)
    expect(out).not.toMatch(/A/)
  })

  it("draws a zero-radius arc as a line", () => {
    expect(normalizePath("M4 4 A0 0 0 0 1 8 8")).toBe("M4 4 L8 8")
  })
})
