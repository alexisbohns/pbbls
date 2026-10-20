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
