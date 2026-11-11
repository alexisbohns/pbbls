import { describe, expect, it } from "vitest"
import { flattenPath, isClosed, simplifyRing, type Pt } from "./geometry"

describe("flattenPath", () => {
  it("splits subpaths and marks the closed ones", () => {
    const subs = flattenPath("M2 2 L10 2 L10 10 Z M12 12 L20 12")
    expect(subs).toHaveLength(2)
    expect(subs[0]?.closed).toBe(true)
    expect(subs[0]?.points).toEqual([[2, 2], [10, 2], [10, 10]])
    expect(subs[1]?.closed).toBe(false)
  })

  it("reads the extra pairs of an M as line points", () => {
    const [sub] = flattenPath("M2 2 5 5 L8 2 Z")
    expect(sub?.points).toEqual([[2, 2], [5, 5], [8, 2]])
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

describe("simplifyRing", () => {
  const distanceToSegment = (p: Pt, a: Pt, b: Pt): number => {
    const dx = b[0] - a[0]
    const dy = b[1] - a[1]
    const len2 = dx * dx + dy * dy
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2))
    return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy))
  }
  const deviation = (ring: Pt[], simple: Pt[]): number =>
    Math.max(...ring.map((p) => Math.min(...simple.map((a, i) => distanceToSegment(p, a, simple[(i + 1) % simple.length] ?? a)))))

  it("keeps a square's corners and drops the points along its edges", () => {
    const ring: Pt[] = [[0, 0], [1, 0], [2, 0], [2, 1], [2, 2], [1, 2], [0, 2], [0, 1]]
    expect(simplifyRing(ring, 0.01)).toEqual([[0, 0], [2, 0], [2, 2], [0, 2]])
  })

  it("thins a dense circle while staying within the tolerance", () => {
    const ring: Pt[] = Array.from({ length: 400 }, (_, i): Pt => [5 * Math.cos((i / 400) * 2 * Math.PI), 5 * Math.sin((i / 400) * 2 * Math.PI)])
    const simple = simplifyRing(ring, 0.01)
    expect(simple.length).toBeLessThan(120)
    expect(deviation(ring, simple)).toBeLessThanOrEqual(0.01 + 1e-9)
  })

  it("leaves a ring of four points or fewer alone", () => {
    const tri: Pt[] = [[0, 0], [1, 0], [0, 1]]
    expect(simplifyRing(tri, 1)).toEqual(tri)
  })
})
