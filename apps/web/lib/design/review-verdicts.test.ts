import { describe, expect, it } from "vitest"
import { nextVerdict } from "./useReviewVerdicts"

describe("nextVerdict", () => {
  it("cycles unset → ok → fix → drop-twin → unset", () => {
    expect(nextVerdict(undefined)).toBe("ok")
    expect(nextVerdict("ok")).toBe("fix")
    expect(nextVerdict("fix")).toBe("drop-twin")
    expect(nextVerdict("drop-twin")).toBeUndefined()
  })
})
