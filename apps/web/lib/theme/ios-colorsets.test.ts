import { readdirSync, readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { ANDROID_SCHEMES_FROM_WEB, type M3Scheme, parseColorSchemes, toKebab } from "./m3-schemes"
import {
  APP_CATALOG,
  IOS_ROOT_FROM_WEB,
  M3_FOLDER,
  SWIFT_ENTRY_POINT,
  WIDGET_CATALOG,
  fromKebab,
  parsePinnedColors,
  renderColorset,
  renderIosColorsets,
  renderPebblesColorsSwift,
} from "./ios-colorsets"

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

// The renderer reads only these four schemes. Each carries the five roles the
// Spine tokens derive from. Hexes are the real ColorSchemes.kt values.
function scheme(
  name: string,
  mode: "light" | "dark",
  contrast: "standard" | "high",
  [primary, onSurface, onSurfaceVariant, low, lowest]: [string, string, string, string, string],
): M3Scheme {
  return {
    name,
    mode,
    contrast,
    roles: [
      ["primary", primary],
      ["on-surface", onSurface],
      ["on-surface-variant", onSurfaceVariant],
      ["surface-container-low", low],
      ["surface-container-lowest", lowest],
    ],
  }
}
const FIXTURE: M3Scheme[] = [
  scheme("LightScheme", "light", "standard", ["#8E4955", "#1D1B16", "#4C4639", "#F9F3EA", "#FFFFFF"]),
  scheme("LightHighContrastScheme", "light", "high", ["#501824", "#000000", "#000000", "#F6F0E7", "#FFFFFF"]),
  scheme("DarkScheme", "dark", "standard", ["#FFB2BC", "#E7E2D9", "#CFC6B4", "#1B1B1E", "#0E0E10"]),
  scheme("DarkHighContrastScheme", "dark", "high", ["#FFEBED", "#FFFFFF", "#FFFFFF", "#1F1F22", "#000000"]),
]
const PINS = { googleCapsuleInk: "#4A3639" }

interface ColorsetEntry {
  appearances?: { appearance: string; value: string }[]
  color: { components: { alpha: string; red: string; green: string; blue: string } }
}
interface ColorsetJson {
  colors: ColorsetEntry[]
}
function colorset(files: Map<string, string>, role: string, catalog = APP_CATALOG): ColorsetJson {
  const text = files.get(`${catalog}/${M3_FOLDER}/${role}.colorset/Contents.json`)
  if (text === undefined) throw new Error(`no colorset for ${role} in ${catalog}`)
  const parsed: ColorsetJson = JSON.parse(text)
  return parsed
}
const hexes = (json: ColorsetJson) =>
  json.colors.map(({ color: { components: c } }) => `#${c.red.slice(2)}${c.green.slice(2)}${c.blue.slice(2)}`)
const alphas = (json: ColorsetJson) => json.colors.map(({ color: { components: c } }) => c.alpha)

describe("renderIosColorsets", () => {
  const files = renderIosColorsets(FIXTURE, PINS)

  it("maps each scheme role's four entries to light, dark, light HC, dark HC", () => {
    expect(hexes(colorset(files, "primary"))).toEqual(["#8E4955", "#FFB2BC", "#501824", "#FFEBED"])
    expect(colorset(files, "surfaceContainerLowest").colors).toHaveLength(4)
  })

  it("regenerates AccentColor as primary, so the global tint is the role", () => {
    expect(files.get(`${APP_CATALOG}/AccentColor.colorset/Contents.json`)).toBe(
      files.get(`${APP_CATALOG}/${M3_FOLDER}/primary.colorset/Contents.json`),
    )
  })

  it("pins googleCapsuleInk to one entry with no appearances", () => {
    const ink = colorset(files, "googleCapsuleInk")
    expect(ink.colors).toHaveLength(1)
    expect(ink.colors[0].appearances).toBeUndefined()
    expect(hexes(ink)).toEqual(["#4A3639"])
  })

  it("blends rule from onSurface at 0.14 Paper and 0.12 Night", () => {
    const rule = colorset(files, "rule")
    expect(hexes(rule)).toEqual(["#1D1B16", "#E7E2D9", "#000000", "#FFFFFF"])
    expect(alphas(rule)).toEqual(["0.140", "0.120", "0.140", "0.120"])
  })

  it("reads stock from surfaceContainerLowest Paper and surfaceContainerLow Night", () => {
    expect(hexes(colorset(files, "stock"))).toEqual(["#FFFFFF", "#1B1B1E", "#FFFFFF", "#1F1F22"])
  })

  it("takes ring from onSurfaceVariant Paper and onSurface Night", () => {
    const ring = colorset(files, "ring")
    expect(hexes(ring)).toEqual(["#4C4639", "#E7E2D9", "#000000", "#FFFFFF"])
    expect(alphas(ring)).toEqual(["0.300", "0.170", "0.300", "0.170"])
  })

  it("writes the rake's lit line from constants, not roles (#1130)", () => {
    const rakeLight = colorset(files, "rakeLight")
    expect(hexes(rakeLight)).toEqual(["#FFFFFF", "#DCE2EA", "#FFFFFF", "#DCE2EA"])
    expect(alphas(rakeLight)).toEqual(["0.550", "0.075", "0.550", "0.075"])
  })

  it("emits all ten Spine tokens", () => {
    for (const token of ["halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple", "rakeLight"]) {
      expect(colorset(files, token).colors, token).toHaveLength(4)
    }
  })

  it("marks the M3 folder as a namespace in both catalogs", () => {
    for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
      expect(files.get(`${catalog}/${M3_FOLDER}/Contents.json`)).toBe(`{
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "provides-namespace" : true
  }
}
`)
    }
  })

  it("gives the widget the app's M3 folder byte for byte", () => {
    const appPrefix = `${APP_CATALOG}/${M3_FOLDER}/`
    const appFiles = [...files.keys()].filter((k) => k.startsWith(appPrefix))
    expect(appFiles.length).toBeGreaterThan(0)
    for (const key of appFiles) {
      const widgetKey = `${WIDGET_CATALOG}/${M3_FOLDER}/${key.slice(appPrefix.length)}`
      expect(files.get(widgetKey), widgetKey).toBe(files.get(key))
    }
  })

  it("fails when a Spine token collides with a scheme role", () => {
    const colliding = FIXTURE.map((s) => ({ ...s, roles: [...s.roles, ["halo", "#000000"] as [string, string]] }))
    expect(() => renderIosColorsets(colliding, PINS)).toThrow(/halo/)
  })

  it("fails when one of the four schemes is missing", () => {
    expect(() => renderIosColorsets(FIXTURE.slice(1), PINS)).toThrow(/LightScheme/)
  })
})

describe("renderPebblesColorsSwift", () => {
  const swift = renderPebblesColorsSwift(["primary", "onPrimary"])

  it("opens with the do-not-edit header and ends with one newline", () => {
    expect(swift.startsWith("// GENERATED by `npm run generate:ios-colors --workspace=@pbbls/web`")).toBe(true)
    expect(swift.endsWith("}\n")).toBe(true)
    expect(swift.endsWith("}\n\n")).toBe(false)
  })

  it("declares one case and one property per role", () => {
    expect(swift).toContain("enum PebblesColorRole: String, CaseIterable, Sendable {\n    case primary\n    case onPrimary\n")
    expect(swift).toContain("    var onPrimary: Color { Color(PebblesColorRole.onPrimary.assetName) }\n")
    expect(swift).toContain('    var assetName: String { "M3/\\(rawValue)" }')
  })

  it("exposes Color.pebbles and the UIKit bridge", () => {
    expect(swift).toContain("    static let pebbles = PebblesColors()")
    expect(swift).toContain("    static func pebbles(_ role: PebblesColorRole) -> UIColor {")
    expect(swift).toContain('preconditionFailure("missing colorset \\(role.assetName)")')
  })
})

describe("renderIosColorsets' Swift file", () => {
  it("lists scheme roles, then the Spine tokens, then the pin", () => {
    const swift = renderIosColorsets(FIXTURE, PINS).get(SWIFT_ENTRY_POINT) ?? ""
    const cases = [...swift.matchAll(/^ {4}case (\w+)$/gm)].map(([, c]) => c)
    expect(cases).toEqual([
      "primary", "onSurface", "onSurfaceVariant", "surfaceContainerLow", "surfaceContainerLowest",
      "halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple", "rakeLight",
      "googleCapsuleInk",
    ])
  })
})

// The drift gate (§8): the committed iOS files must be exactly what the
// generator writes from the committed Kotlin. Runs in web.yml on every PR.
describe("committed iOS colours", () => {
  const webRoot = fileURLToPath(new URL("../..", import.meta.url))
  const iosRoot = path.join(webRoot, IOS_ROOT_FROM_WEB)
  const kotlin = readFileSync(path.join(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8")
  const files = renderIosColorsets(parseColorSchemes(kotlin), parsePinnedColors(kotlin))

  it("matches the generator output (run `npm run generate:colors --workspace=@pbbls/ios`)", () => {
    for (const [relative, contents] of files) {
      expect(readFileSync(path.join(iosRoot, relative), "utf8"), relative).toBe(contents)
    }
  })

  it("holds no colorset in M3/ that the generator does not write", () => {
    for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
      const onDisk = readdirSync(path.join(iosRoot, catalog, M3_FOLDER)).filter((n) => n.endsWith(".colorset"))
      const prefix = `${catalog}/${M3_FOLDER}/`
      const expected = [...files.keys()]
        .filter((k) => k.startsWith(prefix) && k.endsWith(".colorset/Contents.json"))
        .map((k) => k.slice(prefix.length, -"/Contents.json".length))
      expect(onDisk.sort()).toEqual(expected.sort())
    }
  })

  it("renders 63 roles: 48 M3 + 4 sand + 10 Spine + the pin", () => {
    const prefix = `${APP_CATALOG}/${M3_FOLDER}/`
    expect([...files.keys()].filter((k) => k.startsWith(prefix) && k.endsWith(".colorset/Contents.json"))).toHaveLength(63)
  })

  it("carries the real primary, error and surface hexes in D2 order", () => {
    expect(hexes(colorset(files, "primary"))).toEqual(["#8E4955", "#FFB2BC", "#501824", "#FFEBED"])
    expect(hexes(colorset(files, "error"))).toEqual(["#9B4500", "#FFB68E", "#4D1E00", "#FFECE4"])
    expect(hexes(colorset(files, "surface"))).toEqual(["#FEF9EF", "#131315", "#FEF9EF", "#131315"])
  })

  it("round-trips every Kotlin role name through toKebab and fromKebab", () => {
    const lightBlock = kotlin.slice(
      kotlin.indexOf("internal val LightScheme"),
      kotlin.indexOf("internal val LightMediumContrastScheme"),
    )
    const camel = [...lightBlock.matchAll(/(\w+) = Color\(0x/g)].map(([, role]) => role)
    expect(camel.length).toBe(48)
    for (const role of camel) expect(fromKebab(toKebab(role))).toBe(role)
    for (const role of ["sand", "onSand", "sandContainer", "onSandContainer"]) {
      expect(fromKebab(toKebab(role))).toBe(role)
    }
  })
})
