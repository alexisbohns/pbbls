/**
 * Android is the source of truth for the M3 palette (#853, #986): the web's
 * `app/m3-theme.css` is generated from `ColorSchemes.kt`, never hand-copied.
 * Pure string-in/string-out so the drift test can run it in Vitest's node env.
 */

export type M3Mode = "light" | "dark"
export type M3Contrast = "standard" | "medium" | "high"

export interface M3Scheme {
  name: string
  mode: M3Mode
  contrast: M3Contrast
  /** [kebab-role, "#RRGGBB"] in Kotlin source order. */
  roles: [string, string][]
}

/** Paths relative to the `apps/web` workspace root. */
export const ANDROID_SCHEMES_FROM_WEB =
  "../android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt"
export const M3_CSS_FROM_WEB = "app/m3-theme.css"

const EXPECTED: Record<string, { mode: M3Mode; contrast: M3Contrast }> = {
  LightScheme: { mode: "light", contrast: "standard" },
  LightMediumContrastScheme: { mode: "light", contrast: "medium" },
  LightHighContrastScheme: { mode: "light", contrast: "high" },
  DarkScheme: { mode: "dark", contrast: "standard" },
  DarkMediumContrastScheme: { mode: "dark", contrast: "medium" },
  DarkHighContrastScheme: { mode: "dark", contrast: "high" },
}

const SCHEME_BLOCK = /internal val (\w+): ColorScheme =\s*(light|dark)ColorScheme\(([\s\S]*?)\n\s*\)/g
const ROLE_LINE = /(\w+) = Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\)/g
// Custom colours (#991), one ColorFamily per scheme: `LightMediumContrastSand` → LightMediumContrastScheme, "sand".
const FAMILY_BLOCK =
  /internal val (Light|Dark)((?:Medium|High)Contrast)?([A-Z]\w*): ColorFamily =\s*ColorFamily\(([\s\S]*?)\n\s*\)/g
const FAMILY_ROLES: Record<string, (family: string) => string> = {
  color: (f) => f,
  onColor: (f) => `on-${f}`,
  colorContainer: (f) => `${f}-container`,
  onColorContainer: (f) => `on-${f}-container`,
}

export function toKebab(role: string): string {
  return role.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)
}

export function parseColorSchemes(kotlin: string): M3Scheme[] {
  const found = new Map<string, M3Scheme>()

  for (const [, name, fn, body] of kotlin.matchAll(SCHEME_BLOCK)) {
    const expected = EXPECTED[name]
    if (!expected) continue
    if (expected.mode !== fn) {
      throw new Error(`${name} is built with ${fn}ColorScheme, expected ${expected.mode}ColorScheme`)
    }
    const roles: [string, string][] = []
    for (const [, role, alpha, rgb] of body.matchAll(ROLE_LINE)) {
      if (alpha.toUpperCase() !== "FF") {
        throw new Error(`${name}.${role} has alpha 0x${alpha}; M3 roles must be opaque`)
      }
      roles.push([toKebab(role), `#${rgb.toUpperCase()}`])
    }
    found.set(name, { name, ...expected, roles })
  }

  const missing = Object.keys(EXPECTED).filter((n) => !found.has(n))
  if (missing.length > 0) {
    throw new Error(`ColorSchemes.kt is missing scheme(s): ${missing.join(", ")}`)
  }

  // Each custom colour joins its scheme's roles, so the role-set check below
  // also proves every scheme carries every custom colour.
  for (const [, mode, contrast = "", name, body] of kotlin.matchAll(FAMILY_BLOCK)) {
    const schemeName = `${mode}${contrast}Scheme`
    const scheme = found.get(schemeName)
    if (!scheme) throw new Error(`${mode}${contrast}${name} has no ${schemeName} to join`)
    const family = toKebab(name.charAt(0).toLowerCase() + name.slice(1))
    for (const [, role, alpha, rgb] of body.matchAll(ROLE_LINE)) {
      const toRole = FAMILY_ROLES[role]
      if (!toRole) throw new Error(`${mode}${contrast}${name}.${role} is not a ColorFamily role`)
      if (alpha.toUpperCase() !== "FF") {
        throw new Error(`${mode}${contrast}${name}.${role} has alpha 0x${alpha}; M3 roles must be opaque`)
      }
      scheme.roles.push([toRole(family), `#${rgb.toUpperCase()}`])
    }
  }

  const schemes = Object.keys(EXPECTED).flatMap((n) => {
    const scheme = found.get(n)
    return scheme ? [scheme] : []
  })
  const reference = schemes[0].roles.map(([r]) => r).sort()
  for (const scheme of schemes.slice(1)) {
    const own = scheme.roles.map(([r]) => r).sort()
    const absent = reference.filter((r) => !own.includes(r))
    const extra = own.filter((r) => !reference.includes(r))
    if (absent.length > 0 || extra.length > 0) {
      throw new Error(
        `${scheme.name} role set differs from LightScheme (missing: ${absent.join(", ") || "none"}; extra: ${extra.join(", ") || "none"})`,
      )
    }
  }
  return schemes
}

export function schemeSelector(mode: M3Mode, contrast: M3Contrast): string {
  return `.m3${mode === "dark" ? ".dark" : ""}${contrast === "standard" ? "" : `.m3-contrast-${contrast}`}`
}

const HEADER = `/* GENERATED from apps/android/.../core/designsystem/ColorSchemes.kt by
   \`npm run generate:m3 --workspace=apps/web\`. Do not edit: change the Android
   export and regenerate. lib/theme/m3-schemes.test.ts fails when this file and
   the Kotlin disagree.

   Each scheme's selector is one class more specific than the last axis it adds,
   so exactly one block wins for any combination of .m3 / .dark / .m3-contrast-*.
   The bridge that points shadcn tokens at these roles lives in globals.css. */
`

export function renderM3ThemeCss(schemes: M3Scheme[]): string {
  const roles = schemes[0].roles.map(([r]) => r)
  const theme = [
    "@theme inline {",
    ...roles.map((r) => `  --color-m3-${r}: var(--m3-${r});`),
    "}",
  ].join("\n")
  const blocks = schemes.map((s) =>
    [
      `${schemeSelector(s.mode, s.contrast)} {`,
      ...s.roles.map(([r, hex]) => `  --m3-${r}: ${hex};`),
      "}",
    ].join("\n"),
  )
  return `${[HEADER, theme, ...blocks].join("\n")}\n`
}
