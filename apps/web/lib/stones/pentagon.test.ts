import { describe, expect, it } from "vitest"
import { PENTAGON_BOX, PENTAGON_PATH } from "./pentagon"

describe("PENTAGON_PATH", () => {
  it("is the mock's squircle pentagon", () => {
    expect(PENTAGON_PATH.startsWith("M47.0 14.4C61.6 3.8 66.4 3.8 81.0 14.4Q95.6 22.5 107.8 33.8")).toBe(true)
    expect(PENTAGON_PATH.endsWith("Z")).toBe(true)
    expect(PENTAGON_BOX).toEqual({ width: 128, height: 122 })
  })
})
