/**
 * The Path's `?week=` (#1384, spec D9): a Monday as `YYYY-MM-DD`. Only a valid
 * calendar date is accepted (it must round-trip through `Date.UTC`); anything
 * else is ignored. Returned as UTC midnight of that day.
 */
const ISO_DAY = /^(\d{4})-(\d{2})-(\d{2})$/

export function parseWeekParam(value: string | null): Date | null {
  const m = value === null ? null : ISO_DAY.exec(value)
  if (!m) return null
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])]
  const date = new Date(Date.UTC(year, month - 1, day))
  const real = date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  return real ? date : null
}
