/** Midnight local of `d`'s calendar day. */
export function localDayStart(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

/** The calendar day `n` days after `d`'s, at midnight local. DST-safe. */
export function addLocalDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n)
}

/**
 * Whole calendar days from `weekStart` to `d`'s local day (0 = Monday when
 * `weekStart` is an ISO Monday). Rounded, because a day across a DST change is
 * 23 or 25 hours long.
 */
export function dayIndexInWeek(weekStart: Date, d: Date): number {
  const ms = localDayStart(d).getTime() - localDayStart(weekStart).getTime()
  return Math.round(ms / 86_400_000)
}
