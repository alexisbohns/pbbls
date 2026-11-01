/**
 * The month grid's words (#1384, spec §Accessibility, §i18n), from served
 * values and calendar arithmetic only. Dates are read in UTC from the served
 * `YYYY-MM-DD` strings, never from the local clock.
 */
function utc(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}

function format(date: Date, locale: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(locale, { ...options, timeZone: "UTC" }).format(date)
}

/** "October 2026" / "octobre 2026", the grid's hidden name. */
export function monthTitle(month: string, locale: string): string {
  return format(utc(month), locale, { month: "long", year: "numeric" })
}

/** "October" / "octobre". */
export function monthName(month: string, locale: string): string {
  return format(utc(month), locale, { month: "long" })
}

/** The year of a served month, "2026". */
export function monthYear(month: string): string {
  return month.slice(0, 4)
}

/** "5 – 11 October" from a week's day indices in the served month (iOS `WrapCopy.range`). */
export function weekRange(month: string, from: number, to: number, locale: string): string {
  const name = monthName(month, locale)
  return from === to ? `${from + 1} ${name}` : `${from + 1} – ${to + 1} ${name}`
}

/** A column's month annotation: "oct", "sept" (the trailing abbreviation dot dropped; set in caps by the type role). */
export function weekMonthAbbr(weekStart: string, locale: string): string {
  return format(utc(weekStart), locale, { month: "short" }).replace(/\.$/, "")
}

/** Monday's day of the month, the column's figure. */
export function weekDayNumber(weekStart: string): number {
  return utc(weekStart).getUTCDate()
}

/** Seven narrow weekday letters, Monday first, in the locale. */
export function weekdayLetters(locale: string): string[] {
  // 1 January 2024 was a Monday.
  return Array.from({ length: 7 }, (_, k) => format(new Date(Date.UTC(2024, 0, 1 + k)), locale, { weekday: "narrow" }))
}

/** The number of days of a served month. */
export function daysInMonth(month: string): number {
  const d = utc(month)
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate()
}

/** The Monday (`YYYY-MM-DD`) of the week holding the month's first day: where a wrap opens the Path. */
export function firstWeekOf(month: string): string {
  const d = utc(month)
  const weekday = (d.getUTCDay() + 6) % 7
  return new Date(d.getTime() - weekday * 86_400_000).toISOString().slice(0, 10)
}

/** "31 October" / "31 octobre": the month's last day, day before month. */
export function lastDayLabel(month: string, locale: string): string {
  return `${daysInMonth(month)} ${monthName(month, locale)}`
}

/** A bare month laid out before (or without) its read: every day a socket. */
export function bareMonth(month: string): { month: string; daysInMonth: number; firstWeekday: number; count: number; days: []; weeks: null } {
  const d = utc(month)
  return { month, daysInMonth: daysInMonth(month), firstWeekday: (d.getUTCDay() + 6) % 7, count: 0, days: [], weeks: null }
}
