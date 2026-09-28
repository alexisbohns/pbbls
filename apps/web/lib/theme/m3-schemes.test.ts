import { describe, expect, it } from "vitest"
import { parseColorSchemes, toKebab } from "./m3-schemes"

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
