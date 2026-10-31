/** The reader's IANA zone, as `get_achievements` is called (#1384, spec D10). */
export function readerTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
}

/** The first day of the current month in `timeZone`, as `YYYY-MM-01` (the only date the web emits to Home's RPCs). */
export function currentMonthStart(timeZone: string, now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone, year: "numeric", month: "2-digit" }).formatToParts(now)
  const year = parts.find((p) => p.type === "year")?.value ?? "1970"
  const month = parts.find((p) => p.type === "month")?.value ?? "01"
  return `${year}-${month}-01`
}
