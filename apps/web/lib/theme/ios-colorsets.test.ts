import { describe, expect, it } from "vitest"
import { fromKebab, parsePinnedColors, renderColorset } from "./ios-colorsets"

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

describe("renderColorset", () => {
  // Byte-for-byte copy of today's SystemBackground.colorset/Contents.json, as
  // Xcode wrote it: opening a generated catalog in Xcode must not churn it.
  it("writes a two-appearance colorset exactly the way Xcode does", () => {
    expect(
      renderColorset({ light: { hex: "#FFFFFF", alpha: 1 }, dark: { hex: "#171012", alpha: 1 } }),
    ).toBe(`{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xFF",
          "green" : "0xFF",
          "red" : "0xFF"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x12",
          "green" : "0x10",
          "red" : "0x17"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
`)
  })

  it("orders the four appearances as D2 and keys High Contrast with contrast/high", () => {
    const json: { colors: { appearances?: unknown }[] } = JSON.parse(
      renderColorset({
        light: { hex: "#000001", alpha: 1 },
        dark: { hex: "#000002", alpha: 1 },
        lightHigh: { hex: "#000003", alpha: 1 },
        darkHigh: { hex: "#000004", alpha: 1 },
      }),
    )
    expect(json.colors.map((c) => c.appearances ?? null)).toEqual([
      null,
      [{ appearance: "luminosity", value: "dark" }],
      [{ appearance: "contrast", value: "high" }],
      [
        { appearance: "luminosity", value: "dark" },
        { appearance: "contrast", value: "high" },
      ],
    ])
  })

  it("writes alpha with three decimals", () => {
    expect(renderColorset({ light: { hex: "#1D1B16", alpha: 0.14 } })).toContain(`"alpha" : "0.140"`)
  })

  it("rejects a hex that is not #RRGGBB", () => {
    expect(() => renderColorset({ light: { hex: "#abc", alpha: 1 } })).toThrow(/RRGGBB/)
  })
})
