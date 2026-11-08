import { describe, expect, it } from "vitest"
import { flattenPath, isClosed } from "./geometry"

describe("flattenPath", () => {
  it("splits subpaths and marks the closed ones", () => {
    const subs = flattenPath("M2 2 L10 2 L10 10 Z M12 12 L20 12")
    expect(subs).toHaveLength(2)
    expect(subs[0]?.closed).toBe(true)
    expect(subs[0]?.points).toEqual([[2, 2], [10, 2], [10, 10]])
    expect(subs[1]?.closed).toBe(false)
  })

  it("samples a cubic finely enough for the hero size", () => {
    const [sub] = flattenPath("M2 12 C2 2 22 2 22 12")
    expect(sub?.points.length).toBeGreaterThan(40)
    expect(sub?.points.at(-1)).toEqual([22, 12])
  })

  it("treats a subpath whose ends meet as closed", () => {
    expect(isClosed({ points: [[2, 2], [10, 2], [2.2, 2.1]], closed: false })).toBe(true)
    expect(isClosed({ points: [[2, 2], [10, 2]], closed: false })).toBe(false)
  })
})
