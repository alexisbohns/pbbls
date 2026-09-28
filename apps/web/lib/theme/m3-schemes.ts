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
