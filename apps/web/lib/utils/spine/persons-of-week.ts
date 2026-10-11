import type { Pebble, Soul } from "@/lib/types"
import { dayIndexInWeek } from "./local-day"

/**
 * The persons of the week (#1052, spec §Persons of the week): every soul that
 * appears in the week, with the days they appear on.
 *
 * Order — days present desc, then pebbles desc, then name by locale collation —
 * is the one rule every surface uses, so the most present people come first
 * here, in each pebble's people line, and in the same order on iOS and Android.
 */
export type PersonOfWeek = {
  soul: Soul
  /** Monday first, by the local day of each pebble's `happened_at`. */
  days: boolean[]
  dayCount: number
  pebbleCount: number
}

/** Rows shown before the rest fold into "and N others". */
export const PERSONS_CAP = 8

export function personsOfWeek(
  pebbles: readonly Pebble[],
  weekStart: Date,
  soulsById: ReadonlyMap<string, Soul>,
  locale: string,
): PersonOfWeek[] {
  const rows = new Map<string, PersonOfWeek>()
  for (const pebble of pebbles) {
    const day = dayIndexInWeek(weekStart, new Date(pebble.happened_at))
    if (day < 0 || day > 6) continue
    // A pebble can list a soul twice only through a data bug; count it once.
    for (const soulId of new Set(pebble.soul_ids)) {
      const soul = soulsById.get(soulId)
      // A soul the user deleted can linger on a pebble; it has no name to set.
      if (!soul) continue
      let row = rows.get(soulId)
      if (!row) {
        row = { soul, days: Array<boolean>(7).fill(false), dayCount: 0, pebbleCount: 0 }
        rows.set(soulId, row)
      }
      if (!row.days[day]) {
        row.days[day] = true
        row.dayCount += 1
      }
      row.pebbleCount += 1
    }
  }

  const collator = new Intl.Collator(locale)
  return [...rows.values()].sort(
    (a, b) =>
      b.dayCount - a.dayCount ||
      b.pebbleCount - a.pebbleCount ||
      collator.compare(a.soul.name, b.soul.name) ||
      // Two souls can share a name; their ids keep the order stable anyway.
      (a.soul.id < b.soul.id ? -1 : a.soul.id > b.soul.id ? 1 : 0),
  )
}

/** The first `cap` rows, and the rest behind "and N others". */
export function splitPersons(
  rows: readonly PersonOfWeek[],
  cap: number = PERSONS_CAP,
): { shown: PersonOfWeek[]; rest: PersonOfWeek[] } {
  return { shown: rows.slice(0, cap), rest: rows.slice(cap) }
}
