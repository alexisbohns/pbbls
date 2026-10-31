import { describe, expect, it } from "vitest"
import { cachedStone, stoneCacheSize, stoneKey } from "./stone-cache"

describe("cachedStone", () => {
  it("returns the identical value on a hit without remaking it", () => {
    let made = 0
    const make = () => {
      made++
      return ["a", "b"]
    }
    const first = cachedStone("split", stoneKey("hit", 100, 40, [3]), make)
    const second = cachedStone("split", stoneKey("hit", 100.1, 40, [3]), make)
    expect(second).toBe(first)
    expect(made).toBe(1)
  })

  it("clears whole when the 25th entry arrives", () => {
    for (let i = 0; i < 30; i++) cachedStone("stone", stoneKey(`fill-${i}`, 10, 10), () => "M0 0Z")
    expect(stoneCacheSize()).toBeLessThanOrEqual(24)
    cachedStone("stone", stoneKey("fill-29", 10, 10), () => "M0 0Z")
    const before = stoneCacheSize()
    for (let i = before; i < 24; i++) cachedStone("stone", stoneKey(`more-${i}`, 10, 10), () => "M0 0Z")
    expect(stoneCacheSize()).toBe(24)
    cachedStone("stone", stoneKey("overflow", 10, 10), () => "M0 0Z")
    expect(stoneCacheSize()).toBe(1)
  })
})
