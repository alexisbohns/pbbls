// ---------------------------------------------------------------------------
// Bounce — pure utility for the rolling-window streak mechanic.
// No side effects, no dependencies on the provider or localStorage.
// ---------------------------------------------------------------------------

/** Number of days the rolling window covers. */
export const BOUNCE_WINDOW_DAYS = 28

/**
 * Threshold table — sorted descending by `minDays` so the first match wins.
 * Each entry means "if active days >= minDays, the bounce level is `level`".
 */
export const BOUNCE_THRESHOLDS: ReadonlyArray<{ minDays: number; level: number }> = [
  { minDays: 25, level: 7 },
  { minDays: 21, level: 6 },
  { minDays: 18, level: 5 },
  { minDays: 14, level: 4 },
  { minDays: 10, level: 3 },
  { minDays: 6, level: 2 },
  { minDays: 1, level: 1 },
]

/** Map an active-day count to a bounce level (0–7). */
export function computeBounceLevel(activeDays: number): number {
  for (const { minDays, level } of BOUNCE_THRESHOLDS) {
    if (activeDays >= minDays) return level
  }
  return 0
}

/** Return today's date as `YYYY-MM-DD` in the user's local timezone. */
export function todayLocal(): string {
  return new Date().toLocaleDateString("en-CA")
}

/**
 * Remove dates older than 28 days from the window.
 * Compares `YYYY-MM-DD` strings against a cutoff derived from today (local tz).
 */
export function pruneWindow(dates: string[]): string[] {
  const cutoff = new Date()
  cutoff.setDate(cutoff.getDate() - BOUNCE_WINDOW_DAYS)
  const cutoffStr = cutoff.toLocaleDateString("en-CA")
  return dates.filter((d) => d >= cutoffStr)
}

/** Append today to the window if not already present. */
export function addTodayToWindow(window: string[]): string[] {
  const today = todayLocal()
  if (window.includes(today)) return window
  return [...window, today]
}

/**
 * Full refresh: prune → add today → compute level.
 * Used when a pebble is created (active day earned).
 */
export function refreshBounceWindow(currentWindow: string[]): {
  bounce_window: string[]
  bounce: number
} {
  const pruned = pruneWindow(currentWindow)
  const updated = addTodayToWindow(pruned)
  return { bounce_window: updated, bounce: computeBounceLevel(updated.length) }
}

/**
 * Decay only: prune → recompute level (no "add today").
 * Used on app load so inactive days fall off without awarding a new active day.
 */
export function decayBounceWindow(currentWindow: string[]): {
  bounce_window: string[]
  bounce: number
} {
  const pruned = pruneWindow(currentWindow)
  return { bounce_window: pruned, bounce: computeBounceLevel(pruned.length) }
}
