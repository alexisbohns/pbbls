/**
 * "31 October" / "31 oct." (#1384): the day before the month in every locale,
 * as Home's dateline and iOS's `d MMMM` set it, in the reader's zone.
 */
export function dayMonth(date: Date | string, locale: string, month: "long" | "short" = "long"): string {
  const value = date instanceof Date ? date : new Date(date)
  const parts = new Intl.DateTimeFormat(locale, { day: "numeric", month }).formatToParts(value)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? ""
  return `${part("day")} ${part("month")}`
}
