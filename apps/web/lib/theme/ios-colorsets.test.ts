import { describe, expect, it } from "vitest"
import { fromKebab, parsePinnedColors } from "./ios-colorsets"

describe("fromKebab", () => {
  it("camel-cases a kebab role, inverting m3-schemes' toKebab", () => {
    expect(fromKebab("surface-container-low")).toBe("surfaceContainerLow")
    expect(fromKebab("on-sand-container")).toBe("onSandContainer")
    expect(fromKebab("primary")).toBe("primary")
  })
})

describe("parsePinnedColors", () => {
  it("reads the Google capsule ink", () => {
    expect(parsePinnedColors("internal val GoogleCapsuleInk = Color(0xFF4A3639)\n")).toEqual({
      googleCapsuleInk: "#4A3639",
    })
  })

  it("fails when the pin is missing", () => {
    expect(() => parsePinnedColors("internal val Other = Color(0xFF4A3639)\n")).toThrow(/GoogleCapsuleInk/)
  })

  it("fails when the pin is translucent", () => {
    expect(() => parsePinnedColors("internal val GoogleCapsuleInk = Color(0x804A3639)\n")).toThrow(/opaque/)
  })
})
