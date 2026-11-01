export const SETTINGS_LINK_SECTIONS = ["legal", "about", "support"] as const
export type SettingsLinkSection = (typeof SETTINGS_LINK_SECTIONS)[number]
export const KEY_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/
export function isSettingsLinkSection(value: unknown): value is SettingsLinkSection {
  return typeof value === "string" && (SETTINGS_LINK_SECTIONS as readonly string[]).includes(value)
}
/** Mirrors the column CHECK: the database is the authority, this only spares a round trip. */
export function isAllowedLinkUrl(value: string): boolean {
  return /^(https:\/\/\S+|mailto:\S+)$/.test(value)
}
