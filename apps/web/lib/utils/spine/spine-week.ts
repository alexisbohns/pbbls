import type { Pebble } from "@/lib/types"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"
import { addLocalDays, dayIndexInWeek, localDayStart } from "./local-day"

/**
 * One week laid out as the Spine reads it (#1052, spec D9): a book, so days run
 * oldest first in every week — the current one included — and pebbles run
 * oldest first within each day. `buildWeekRollEntries` keeps its newest-first
 * current week for its other consumers; the Spine re-sorts here.
 *
 * A past day with no pebble is kept and marked `still` (a seam with *still*
 * under it). A day after today is not drawn unless it holds a pebble: a
 * future-dated pebble is rare but must never vanish from the Path.
 */
export type SpineDay = {
  /** Midnight local. */
  date: Date
  /** 0 = Monday … 6 = Sunday. */
  index: number
  pebbles: Pebble[]
  still: boolean
  isToday: boolean
}

export type SpineWeek = {
  weekStart: Date
  days: SpineDay[]
}

function happenedAt(p: Pebble): number {
  return new Date(p.happened_at).getTime()
}

export function spineWeek(entry: WeekRollEntry, today: Date): SpineWeek {
  const byDay: Pebble[][] = Array.from({ length: 7 }, () => [])
  for (const pebble of entry.pebbles) {
    const index = dayIndexInWeek(entry.weekStart, new Date(pebble.happened_at))
    if (index >= 0 && index <= 6) byDay[index].push(pebble)
  }

  const todayStart = localDayStart(today).getTime()
  const days: SpineDay[] = []
  for (let index = 0; index < 7; index++) {
    const date = addLocalDays(entry.weekStart, index)
    const pebbles = byDay[index].sort((a, b) => happenedAt(a) - happenedAt(b) || (a.id < b.id ? -1 : 1))
    const future = date.getTime() > todayStart
    if (future && pebbles.length === 0) continue
    days.push({
      date,
      index,
      pebbles,
      still: pebbles.length === 0,
      isToday: date.getTime() === todayStart,
    })
  }
  return { weekStart: entry.weekStart, days }
}

/** The week's pebbles in reading order: every day, top to bottom. */
export function readingOrder(week: SpineWeek): Pebble[] {
  return week.days.flatMap((day) => day.pebbles)
}
