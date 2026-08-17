import { describe, expect, it } from "vitest"
import { peerIdOf } from "./connection-peer"

describe("peerIdOf", () => {
  const row = { user_a: "aaa", user_b: "bbb" }

  it("returns the other member when self is user_a", () => {
    expect(peerIdOf(row, "aaa")).toBe("bbb")
  })

  it("returns the other member when self is user_b", () => {
    expect(peerIdOf(row, "bbb")).toBe("aaa")
  })

  it("returns null when self is not a member", () => {
    expect(peerIdOf(row, "zzz")).toBeNull()
  })
})
