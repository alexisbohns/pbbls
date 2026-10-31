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

// ---------------------------------------------------------------------------
// The stack's state (#1308): what is open (logical), what is drawn (it lags,
// so a closing sheet plays its close before it leaves), which opens have
// marked their card seen, and a pebble waiting for the sheets to finish
// closing. Pure, so the page's reducer is tested in node.
// ---------------------------------------------------------------------------

/** One open sheet: its key and the open it belongs to (a reopened sheet is a new open). */
export type SheetEntry = { key: SheetKey; open: number }

export type SheetsState = {
  /** What is open, bottom first. */
  opened: SheetEntry[]
  /** What is drawn: the open ones, then any still playing their close. */
  rendered: SheetEntry[]
  /** The opens that have marked their card seen. */
  seen: number[]
  /** A pebble to open once the sheets have closed. */
  pendingMoment: string | null
  nextOpen: number
}

export const NO_SHEETS: SheetsState = { opened: [], rendered: [], seen: [], pendingMoment: null, nextOpen: 1 }

export type SheetsAction =
  | { type: "open"; key: SheetKey }
  | { type: "close"; depth: number }
  | { type: "closed"; open: number }
  | { type: "prune"; page: AchievementsPage | null }
  | { type: "seen"; open: number }
  | { type: "openMoment"; momentId: string }
  | { type: "movedOn" }

export function sheetsReducer(state: SheetsState, action: SheetsAction): SheetsState {
  switch (action.type) {
    case "open": {
      const at = state.opened.findIndex((e) => sameSheet(e.key, action.key))
      // One already open below comes back to the top; the ones above it close.
      if (at >= 0) return { ...state, opened: state.opened.slice(0, at + 1), pendingMoment: null }
      const entry = { key: action.key, open: state.nextOpen }
      const opened = [...state.opened, entry]
      return { ...state, opened, rendered: opened, nextOpen: state.nextOpen + 1, pendingMoment: null }
    }
    case "close":
      // It and everything above it close; they stay drawn until their close has played.
      return { ...state, opened: closeEntries(state.opened, action.depth) }
    case "closed": {
      if (state.opened.some((e) => e.open === action.open)) return state
      const at = state.rendered.findIndex((e) => e.open === action.open)
      return at < 0 ? state : { ...state, rendered: state.rendered.slice(0, at) }
    }
    case "prune": {
      // A sheet whose section left the payload closes for good: it never comes back by itself.
      const opened = keepShown(state.opened, action.page)
      const rendered = keepShown(state.rendered, action.page)
      return opened.length === state.opened.length && rendered.length === state.rendered.length ? state : { ...state, opened, rendered }
    }
    case "seen":
      return state.seen.includes(action.open) ? state : { ...state, seen: [...state.seen, action.open] }
    case "openMoment":
      return { ...state, opened: [], pendingMoment: action.momentId }
    case "movedOn":
      return { ...state, pendingMoment: null }
  }
}

function closeEntries(entries: readonly SheetEntry[], depth: number): SheetEntry[] {
  return entries.slice(0, Math.max(depth, 0))
}

function keepShown(entries: readonly SheetEntry[], page: AchievementsPage | null): SheetEntry[] {
  const gone = entries.findIndex((e) => !isShown(e.key, page))
  return gone < 0 ? [...entries] : entries.slice(0, gone)
}

/** Whether a page change leaves a sheet with nothing to show: the page then prunes. */
export function needsPrune(state: SheetsState, page: AchievementsPage | null): boolean {
  return sheetsReducer(state, { type: "prune", page }) !== state
}
