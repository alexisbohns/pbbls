import type { IsoDate, TimeRange } from "./types"

/** Number of days a TimeRange covers. `all` returns `null`. */
export function periodLengthDays(range: TimeRange): number | null {
  switch (range) {
    case "7d":
      return 7
    case "30d":
      return 30
    case "90d":
      return 90
    case "1y":
      return 365
    case "all":
      return null
  }
}

/** Inclusive UTC date range ending today. For `all`, start = epoch. */
export function dateRangeFor(
  range: TimeRange,
  today: Date = new Date(),
): { start: IsoDate; end: IsoDate } {
  const end = today.toISOString().slice(0, 10)
  const days = periodLengthDays(range)
  if (days === null) return { start: "1970-01-01", end }
  const startDate = new Date(today)
  startDate.setUTCDate(startDate.getUTCDate() - days + 1)
  return { start: startDate.toISOString().slice(0, 10), end }
}

/** Subtract `days` from an ISO date and return a new ISO date. Pass a positive `days` to add. */
export function shiftIsoDate(iso: IsoDate, days: number): IsoDate {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}
