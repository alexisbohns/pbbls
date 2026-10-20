/**
 * iOS draws the M3 scheme Android exports (#921). This module turns the parsed
 * `ColorSchemes.kt` into the asset-catalog colorsets and the generated Swift
 * entry point `Color.pebbles`. It is pure string-in / files-out like
 * m3-schemes.ts, so the drift test runs it in Vitest's node env.
 * Spec: docs/superpowers/specs/2026-10-20-ios-m3-colour-design.md (D1–D3, §2.3–2.5).
 */

export interface IosPins {
  googleCapsuleInk: string
}

/** `surface-container-low` → `surfaceContainerLow`: the inverse of m3-schemes' `toKebab`. */
export function fromKebab(role: string): string {
  return role.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())
}

const GOOGLE_CAPSULE_INK = /internal val GoogleCapsuleInk = Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\)/

/** The one hand-pinned value iOS needs (§2.5). Lives here so m3-schemes.ts stays untouched. */
export function parsePinnedColors(kotlin: string): IosPins {
  const match = kotlin.match(GOOGLE_CAPSULE_INK)
  if (!match) throw new Error("ColorSchemes.kt has no GoogleCapsuleInk pin")
  const [, alpha, rgb] = match
  if (alpha.toUpperCase() !== "FF") {
    throw new Error(`GoogleCapsuleInk has alpha 0x${alpha}; the pinned ink must be opaque`)
  }
  return { googleCapsuleInk: `#${rgb.toUpperCase()}` }
}

export type Appearance = "light" | "dark" | "lightHigh" | "darkHigh"

export interface Tone {
  hex: string
  alpha: number
}

/** D2: entry order in every colorset. */
export const APPEARANCES: readonly Appearance[] = ["light", "dark", "lightHigh", "darkHigh"]

interface AppearanceKey {
  appearance: "luminosity" | "contrast"
  value: "dark" | "high"
}

/**
 * D2: the keys actool honours, checked by compiling a test catalog with Xcode
 * 26.3 and reading the .car with `assetutil --info`. A misspelt key compiles
 * silently and falls back to light, which is why PebblesColorsTests resolves
 * real trait collections.
 */
const APPEARANCE_KEYS: Record<Appearance, AppearanceKey[] | null> = {
  light: null,
  dark: [{ appearance: "luminosity", value: "dark" }],
  lightHigh: [{ appearance: "contrast", value: "high" }],
  darkHigh: [
    { appearance: "luminosity", value: "dark" },
    { appearance: "contrast", value: "high" },
  ],
}

const XCODE_INFO = { author: "xcode", version: 1 }

/** JSON the way Xcode writes it: two-space indent, `"key" : value`, trailing newline. */
export function xcodeJson(value: unknown): string {
  return `${JSON.stringify(value, null, 2).replace(/"([^"]+)": /g, '"$1" : ')}\n`
}

function components({ hex, alpha }: Tone) {
  if (!/^#[0-9A-F]{6}$/.test(hex)) throw new Error(`${hex} is not an uppercase #RRGGBB`)
  // Key order is Xcode's (alphabetical), so the file does not churn on open.
  return {
    alpha: alpha.toFixed(3),
    blue: `0x${hex.slice(5, 7)}`,
    green: `0x${hex.slice(3, 5)}`,
    red: `0x${hex.slice(1, 3)}`,
  }
}

/** One `Contents.json`. A tone given only for `light` is a single-appearance colorset (§2.5). */
export function renderColorset(tones: Partial<Record<Appearance, Tone>>): string {
  const colors = APPEARANCES.flatMap((appearance) => {
    const tone = tones[appearance]
    if (!tone) return []
    const color = { "color-space": "srgb", components: components(tone) }
    const keys = APPEARANCE_KEYS[appearance]
    return [keys ? { appearances: keys, color, idiom: "universal" } : { color, idiom: "universal" }]
  })
  return xcodeJson({ colors, info: XCODE_INFO })
}
