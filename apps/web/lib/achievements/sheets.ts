import type { AchievementCard, AchievementsPage } from "@/lib/types"

// The achievements sheets (#1308, spec §4 "Interaction"), iOS's
// `AchievementSheet`. On the web each is a detail of its own since #1384
// (two-area spec D5, a nested detail under Achievements): a card opens its
// sheet's URL; Explorer's Domains and Emotions rows, and a year's earlier
// years, open theirs; Back returns to where it was opened from.

export type SheetKey = { card: Exclude<AchievementCard, "year"> } | { card: "year"; year: number }

/** The four sheets addressed by name; a year is addressed by its number. */
const NAMED = ["explorer", "emotions", "domains", "pebbles"] as const

export function sameSheet(a: SheetKey, b: SheetKey): boolean {
  return a.card === b.card && (a.card !== "year" || (b.card === "year" && a.year === b.year))
}

/** Whether the page still holds what a sheet shows: one whose section left the payload is not drawn empty. */
export function isShown(key: SheetKey, page: AchievementsPage | null): boolean {
  if (!page) return false
  switch (key.card) {
    case "explorer":
      return page.explorer !== null
    case "emotions":
      return page.emotions !== null
    case "domains":
      return page.domains !== null
    case "pebbles":
      return page.pebbles !== null
    case "year":
      return page.years.some((y) => y.year === key.year)
  }
}

/** A sheet's own identity, for keys and for marking its card seen once per visit. */
export function sheetId(key: SheetKey): string {
  return key.card === "year" ? `year-${key.year}` : key.card
}

/** A sheet's URL: `/achievements/explorer` … `/achievements/pebbles`, `/achievements/year/2026`. */
export function sheetHref(key: SheetKey): string {
  return key.card === "year" ? `/achievements/year/${key.year}` : `/achievements/${key.card}`
}

/** A named sheet from its URL segment; anything else is null (the page then says it is not there). */
export function namedSheet(segment: string): SheetKey | null {
  const card = NAMED.find((name) => name === segment)
  return card ? { card } : null
}

/** A year's sheet from its URL segment: four digits, else null. */
export function yearSheet(segment: string): SheetKey | null {
  return /^\d{4}$/.test(segment) ? { card: "year", year: Number(segment) } : null
}
