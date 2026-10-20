/**
 * iOS draws the M3 scheme Android exports (#921). This module turns the parsed
 * `ColorSchemes.kt` into the asset-catalog colorsets and the generated Swift
 * entry point `Color.pebbles`. It is pure string-in / files-out like
 * m3-schemes.ts, so the drift test runs it in Vitest's node env.
 * Spec: docs/superpowers/specs/2026-10-20-ios-m3-colour-design.md (D1–D3, §2.3–2.5).
 */

import type { M3Scheme } from "./m3-schemes"

/** `apps/ios`, relative to the `apps/web` workspace root. */
export const IOS_ROOT_FROM_WEB = "../ios"
/** Paths below are relative to `apps/ios` (§2.3). */
export const APP_CATALOG = "Pebbles/Resources/Assets.xcassets"
export const WIDGET_CATALOG = "PebblesWidget/Assets.xcassets"
export const M3_FOLDER = "M3"
export const SWIFT_ENTRY_POINT = "Shared/Theme/PebblesColors.swift"

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

const SCHEME_FOR: Record<Appearance, string> = {
  light: "LightScheme",
  dark: "DarkScheme",
  lightHigh: "LightHighContrastScheme",
  darkHigh: "DarkHighContrastScheme",
}

type Source = readonly [role: string, alpha: number]

interface SpineToken {
  name: string
  /** Light and light high contrast. */
  paper: Source
  /** Dark and dark high contrast. */
  night: Source
}

/**
 * §2.4: the nine scheme-only Spine tokens, from #1118's "Spine token → M3 role"
 * table (docs/superpowers/specs/2026-10-18-android-spine-brand-scheme-design.md,
 * §The rule set), mirroring Android's `SpineColors.from(scheme, night)`. A blend
 * with an emotion palette is never here: stack D computes it in SpineColors.swift.
 */
export const SPINE_TOKENS: readonly SpineToken[] = [
  { name: "halo", paper: ["surfaceContainerLow", 1], night: ["surfaceContainerLow", 1] },
  { name: "stock", paper: ["surfaceContainerLowest", 1], night: ["surfaceContainerLow", 1] },
  { name: "rule", paper: ["onSurface", 0.14], night: ["onSurface", 0.12] },
  { name: "cardRule", paper: ["onSurface", 0.22], night: ["onSurface", 0.18] },
  { name: "line", paper: ["onSurface", 0.34], night: ["onSurface", 0.3] },
  { name: "seam", paper: ["onSurface", 0.3], night: ["onSurface", 0.24] },
  { name: "teeth", paper: ["onSurface", 0.2], night: ["onSurface", 0.14] },
  { name: "ring", paper: ["onSurfaceVariant", 0.3], night: ["onSurface", 0.17] },
  { name: "ripple", paper: ["onSurface", 0.55], night: ["onSurface", 0.5] },
]

function perAppearance<T>(make: (appearance: Appearance) => T): Record<Appearance, T> {
  return { light: make("light"), dark: make("dark"), lightHigh: make("lightHigh"), darkHigh: make("darkHigh") }
}

/** Every file the generator owns, keyed by its path relative to `apps/ios`. */
export function renderIosColorsets(schemes: readonly M3Scheme[], pins: IosPins): Map<string, string> {
  const rolesByAppearance = perAppearance((appearance) => {
    const name = SCHEME_FOR[appearance]
    const found = schemes.find((s) => s.name === name)
    if (!found) throw new Error(`no ${name} to render the iOS ${appearance} appearance from`)
    return new Map(found.roles.map(([kebab, hex]) => [fromKebab(kebab), hex]))
  })
  const hexFor = (appearance: Appearance, role: string): string => {
    const hex = rolesByAppearance[appearance].get(role)
    if (hex === undefined) throw new Error(`${SCHEME_FOR[appearance]} has no ${role}`)
    return hex
  }

  // Insertion order is the Swift enum order: scheme roles in Kotlin source
  // order (sand appended by parseColorSchemes), then Spine, then the pin.
  const tones = new Map<string, Partial<Record<Appearance, Tone>>>()
  for (const role of rolesByAppearance.light.keys()) {
    tones.set(role, perAppearance((appearance) => ({ hex: hexFor(appearance, role), alpha: 1 })))
  }
  for (const token of SPINE_TOKENS) {
    if (tones.has(token.name)) throw new Error(`Spine token ${token.name} collides with a scheme role`)
    tones.set(
      token.name,
      perAppearance((appearance) => {
        const [role, alpha] = appearance === "light" || appearance === "lightHigh" ? token.paper : token.night
        return { hex: hexFor(appearance, role), alpha }
      }),
    )
  }
  if (tones.has("googleCapsuleInk")) throw new Error("googleCapsuleInk collides with a scheme role")
  tones.set("googleCapsuleInk", { light: { hex: pins.googleCapsuleInk, alpha: 1 } })

  const primary = tones.get("primary")
  if (!primary) throw new Error("the schemes have no primary role to regenerate AccentColor from")

  const files = new Map<string, string>()
  files.set(`${APP_CATALOG}/AccentColor.colorset/Contents.json`, renderColorset(primary))
  const namespace = xcodeJson({ info: XCODE_INFO, properties: { "provides-namespace": true } })
  for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
    files.set(`${catalog}/${M3_FOLDER}/Contents.json`, namespace)
    for (const [role, tone] of tones) {
      files.set(`${catalog}/${M3_FOLDER}/${role}.colorset/Contents.json`, renderColorset(tone))
    }
  }
  files.set(SWIFT_ENTRY_POINT, renderPebblesColorsSwift([...tones.keys()]))
  return files
}

const SWIFT_HEADER = `// GENERATED by \`npm run generate:ios-colors --workspace=@pbbls/web\` from
// apps/android/.../core/designsystem/ColorSchemes.kt. Do not edit: change the
// Android export and regenerate. apps/web/lib/theme/ios-colorsets.test.ts fails
// when this file and the Kotlin disagree.
`

/** D3: `Shared/Theme/PebblesColors.swift`, compiled into the app and the widget. */
export function renderPebblesColorsSwift(roles: readonly string[]): string {
  const cases = roles.map((role) => `    case ${role}`).join("\n")
  const properties = roles
    .map((role) => `    var ${role}: Color { Color(PebblesColorRole.${role}.assetName) }`)
    .join("\n")
  return `${SWIFT_HEADER}import SwiftUI
import UIKit

/// Every colour iOS paints chrome with: the M3 scheme roles in Kotlin source
/// order, the sand custom colour, the Spine tokens and the pinned Google ink.
enum PebblesColorRole: String, CaseIterable, Sendable {
${cases}

    /// The colorset's name in the \`M3\` namespace of both asset catalogs.
    var assetName: String { "M3/\\(rawValue)" }
}

/// \`Color.pebbles.<role>\`: one property per \`PebblesColorRole\`.
struct PebblesColors: Sendable {
${properties}
}

extension Color {
    /// The only way feature code names a colour. Roles, never hexes.
    static let pebbles = PebblesColors()
}

extension UIColor {
    /// UIKit appearance proxies and Rive. Same roles, same assets.
    static func pebbles(_ role: PebblesColorRole) -> UIColor {
        guard let color = UIColor(named: role.assetName) else {
            preconditionFailure("missing colorset \\(role.assetName)")
        }
        return color
    }
}
`
}
