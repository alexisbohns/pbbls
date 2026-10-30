import type { AchievementCard, AchievementsPage } from "@/lib/types"

// The achievements sheets' stack (#1308, spec §4 "Interaction"): a card opens
// its sheet; Explorer's Domains and Emotions rows, and a year's earlier years,
// push their own sheet on top. Dismissing pops only the top. A sheet whose
// section leaves the payload closes, with everything above it. iOS
// `AchievementSheet` and `AchievementSheetHost`.

export type SheetKey = { card: Exclude<AchievementCard, "year"> } | { card: "year"; year: number }

export function sameSheet(a: SheetKey, b: SheetKey): boolean {
  return a.card === b.card && (a.card !== "year" || (b.card === "year" && a.year === b.year))
}

/** Pushes a sheet on top; one already open below comes back to the top instead of opening twice. */
export function pushSheet(stack: readonly SheetKey[], key: SheetKey): SheetKey[] {
  const at = stack.findIndex((open) => sameSheet(open, key))
  return at >= 0 ? stack.slice(0, at + 1) : [...stack, key]
}

/** Closes the sheet at `depth` and every sheet above it. */
export function closeSheet(stack: readonly SheetKey[], depth: number): SheetKey[] {
  return stack.slice(0, Math.max(depth, 0))
}

/** Whether the page still holds what a sheet shows: one whose section left the payload is closed, never drawn empty. */
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

/** The sheets still shown: the stack up to the first whose section left the payload. */
export function shownStack(stack: readonly SheetKey[], page: AchievementsPage | null): SheetKey[] {
  const gone = stack.findIndex((key) => !isShown(key, page))
  return gone < 0 ? [...stack] : stack.slice(0, gone)
}

/** A sheet's own identity, for keys and for marking its card seen once per open. */
export function sheetId(key: SheetKey): string {
  return key.card === "year" ? `year-${key.year}` : key.card
}
