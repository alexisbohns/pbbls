// apps/admin/lib/emotions/color.ts
// Helpers for the 8-digit hex (#RRGGBBAA) palette colours. Native
// <input type="color"> only speaks 6-digit #RRGGBB, so we split/rejoin the
// alpha byte ourselves to keep it editable without losing transparency.

const HEX8 = /^#[0-9A-Fa-f]{8}$/

/** True when value is a #RRGGBBAA string. */
export function isHex8(value: string): boolean {
  return HEX8.test(value.trim())
}

/** Uppercase + trim, matching the DB's FF-padded seed convention. */
export function normalizeHex8(value: string): string {
  return value.trim().toUpperCase()
}

/** #RRGGBBAA → #RRGGBB (drops the alpha byte, for the native colour picker). */
export function rgbPart(hex8: string): string {
  return hex8.slice(0, 7)
}

/** #RRGGBBAA → AA (the alpha byte). */
export function alphaPart(hex8: string): string {
  return hex8.slice(7, 9)
}

/** Combine a #RRGGBB from the colour picker with the alpha already in hex8. */
export function withRgb(rgb6: string, hex8: string): string {
  return normalizeHex8(rgb6.slice(0, 7) + alphaPart(hex8))
}

/** Replace just the alpha byte (two hex chars). */
export function withAlpha(hex8: string, alpha2: string): string {
  return normalizeHex8(rgbPart(hex8) + alpha2)
}

/** Alpha byte → 0–100 percentage (rounded), for display. */
export function alphaToPercent(hex8: string): number {
  const a = parseInt(alphaPart(hex8) || "FF", 16)
  return Math.round((a / 255) * 100)
}

/**
 * surface_color as the server derives it: primary RGB + `1A` alpha (~10%).
 * Kept in sync with admin_update_emotion_palette so the editor preview matches
 * what will be stored.
 */
export function deriveSurface(primaryHex8: string): string {
  return normalizeHex8(rgbPart(primaryHex8) + "1A")
}
