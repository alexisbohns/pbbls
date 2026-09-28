import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import {
  ANDROID_SCHEMES_FROM_WEB,
  M3_CSS_FROM_WEB,
  parseColorSchemes,
  renderM3ThemeCss,
  schemeSelector,
  toKebab,
} from "./m3-schemes"

// Minimal stand-in for ColorSchemes.kt: same shape as the generated Kotlin,
// two roles per scheme, plus a non-scheme literal the parser must ignore.
function kotlin(overrides: Partial<Record<string, string>> = {}): string {
  const block = (name: string, fn: "light" | "dark", primary: string) =>
    overrides[name] ??
    `internal val ${name}: ColorScheme =
    ${fn}ColorScheme(
        primary = Color(0xFF${primary}),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    )
`
  return [
    "package app.pbbls.android.core.designsystem\n",
    block("LightScheme", "light", "8E4955"),
    block("LightMediumContrastScheme", "light", "5D222E"),
    block("LightHighContrastScheme", "light", "4F1824"),
    block("DarkScheme", "dark", "FFB2BC"),
    block("DarkMediumContrastScheme", "dark", "FFD1D6"),
    block("DarkHighContrastScheme", "dark", "FFEBED"),
    "internal val GoogleCapsuleInk = Color(0xFF4A3639)\n",
  ].join("\n")
}

describe("toKebab", () => {
  it("kebab-cases a camelCase role", () => {
    expect(toKebab("surfaceContainerLowest")).toBe("surface-container-lowest")
    expect(toKebab("primary")).toBe("primary")
  })
})

describe("parseColorSchemes", () => {
  it("finds the six schemes with mode, contrast and roles in source order", () => {
    const schemes = parseColorSchemes(kotlin())
    expect(schemes.map((s) => [s.name, s.mode, s.contrast])).toEqual([
      ["LightScheme", "light", "standard"],
      ["LightMediumContrastScheme", "light", "medium"],
      ["LightHighContrastScheme", "light", "high"],
      ["DarkScheme", "dark", "standard"],
      ["DarkMediumContrastScheme", "dark", "medium"],
      ["DarkHighContrastScheme", "dark", "high"],
    ])
    expect(schemes[0].roles).toEqual([
      ["primary", "#8E4955"],
      ["surface-container-lowest", "#FFFFFF"],
    ])
  })

  it("ignores Color literals outside a scheme block", () => {
    const roles = parseColorSchemes(kotlin()).flatMap((s) => s.roles.map(([r]) => r))
    expect(roles).not.toContain("google-capsule-ink")
  })

  it("fails when a scheme is missing", () => {
    expect(() => parseColorSchemes(kotlin({ DarkHighContrastScheme: "" }))).toThrow(
      /DarkHighContrastScheme/,
    )
  })

  it("fails when a scheme's role set differs from LightScheme's", () => {
    const drifted = `internal val DarkScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFFFB2BC),
    )
`
    expect(() => parseColorSchemes(kotlin({ DarkScheme: drifted }))).toThrow(
      /DarkScheme.*surface-container-lowest/,
    )
  })

  it("fails on a translucent colour", () => {
    const translucent = `internal val LightScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0x808E4955),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    )
`
    expect(() => parseColorSchemes(kotlin({ LightScheme: translucent }))).toThrow(/alpha/)
  })

  it("fails when a Dark* name is built with lightColorScheme", () => {
    const wrongFn = `internal val DarkScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFFFFB2BC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    )
`
    expect(() => parseColorSchemes(kotlin({ DarkScheme: wrongFn }))).toThrow(/DarkScheme/)
  })
})

// Custom colours (#991): `internal val <Light|Dark><MediumContrast|HighContrast?><Name>: ColorFamily`.
function family(prefix: string, color: string): string {
  return `internal val ${prefix}Sand: ColorFamily =
    ColorFamily(
        color = Color(0xFF${color}),
        onColor = Color(0xFFFFFFFF),
        colorContainer = Color(0xFFFFDDB2),
        onColorContainer = Color(0xFF624000),
    )
`
}
const SAND_PREFIXES = ["Light", "LightMediumContrast", "LightHighContrast", "Dark", "DarkMediumContrast", "DarkHighContrast"]

describe("parseColorSchemes with custom colours", () => {
  it("appends each family's four roles to its own scheme", () => {
    const withSand = kotlin() + SAND_PREFIXES.map((p, i) => family(p, `7F560${i}`)).join("\n")
    const schemes = parseColorSchemes(withSand)
    expect(schemes[0].roles.slice(-4)).toEqual([
      ["sand", "#7F5600"],
      ["on-sand", "#FFFFFF"],
      ["sand-container", "#FFDDB2"],
      ["on-sand-container", "#624000"],
    ])
    expect(schemes[5].roles.find(([r]) => r === "sand")).toEqual(["sand", "#7F5605"])
  })

  it("fails when one scheme lacks a family the others have", () => {
    const partial = kotlin() + SAND_PREFIXES.slice(1).map((p) => family(p, "7F560F")).join("\n")
    expect(() => parseColorSchemes(partial)).toThrow(/sand/)
  })
})

describe("schemeSelector", () => {
  it("gives each scheme a unique, contrast-and-mode-specific selector", () => {
    expect(schemeSelector("light", "standard")).toBe(".m3")
    expect(schemeSelector("light", "medium")).toBe(".m3.m3-contrast-medium")
    expect(schemeSelector("dark", "standard")).toBe(".m3.dark")
    expect(schemeSelector("dark", "high")).toBe(".m3.dark.m3-contrast-high")
  })
})

describe("renderM3ThemeCss", () => {
  const css = renderM3ThemeCss(parseColorSchemes(kotlin()))

  it("starts with the do-not-edit header", () => {
    expect(css.startsWith("/* GENERATED")).toBe(true)
  })

  it("exposes every role as a Tailwind colour", () => {
    expect(css).toContain("@theme inline {")
    expect(css).toContain("  --color-m3-primary: var(--m3-primary);")
    expect(css).toContain("  --color-m3-surface-container-lowest: var(--m3-surface-container-lowest);")
  })

  it("writes each scheme's values under its selector", () => {
    expect(css).toContain(".m3 {\n  --m3-primary: #8E4955;")
    expect(css).toContain(".m3.dark.m3-contrast-high {\n  --m3-primary: #FFEBED;")
  })

  it("ends with a single newline", () => {
    expect(css.endsWith("}\n")).toBe(true)
  })
})

// The drift gate: the committed CSS must be exactly what the generator writes
// from the committed Kotlin. Fails when Android re-exports without a web regen.
describe("app/m3-theme.css", () => {
  const webRoot = fileURLToPath(new URL("../..", import.meta.url))
  const kotlinSource = readFileSync(path.join(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8")

  it("parses the real ColorSchemes.kt into six schemes of 52 roles (48 M3 + sand)", () => {
    const schemes = parseColorSchemes(kotlinSource)
    expect(schemes).toHaveLength(6)
    for (const scheme of schemes) expect(scheme.roles).toHaveLength(52)
  })

  it("matches the generator output (run `npm run generate:m3 --workspace=apps/web`)", () => {
    const committed = readFileSync(path.join(webRoot, M3_CSS_FROM_WEB), "utf8")
    expect(committed).toBe(renderM3ThemeCss(parseColorSchemes(kotlinSource)))
  })
})
