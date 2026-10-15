import type { Moment } from "@/lib/types"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"
import { addLocalDays, dayIndexInWeek, localDayStart } from "./local-day"

/**
 * One week laid out as the Spine reads it (#1052, spec D9): a book, so days run
 * oldest first in every week — the current one included — and moments run
 * oldest first within each day. `buildWeekRollEntries` keeps its newest-first
 * current week for its other consumers; the Spine re-sorts here.
 *
 * A bare moment (#1050) takes its place like a pebble: its day is not still.
 * A past day with no moment is kept and marked `still` (a seam with *still*
 * under it). A day after today is not drawn unless it holds a moment: a
 * future-dated one is rare but must never vanish from the Path.
 */
export type SpineDay = {
  /** Midnight local. */
  date: Date
  /** 0 = Monday … 6 = Sunday. */
  index: number
  moments: Moment[]
  still: boolean
  isToday: boolean
}

export type SpineWeek = {
  weekStart: Date
  days: SpineDay[]
}

function happenedAt(m: Moment): number {
  return new Date(m.happened_at).getTime()
}

export function spineWeek(entry: WeekRollEntry, today: Date): SpineWeek {
  const byDay: Moment[][] = Array.from({ length: 7 }, () => [])
  for (const moment of entry.moments) {
    const index = dayIndexInWeek(entry.weekStart, new Date(moment.happened_at))
    if (index >= 0 && index <= 6) byDay[index].push(moment)
  }

  const todayStart = localDayStart(today).getTime()
  const days: SpineDay[] = []
  for (let index = 0; index < 7; index++) {
    const date = addLocalDays(entry.weekStart, index)
    const moments = byDay[index].sort((a, b) => happenedAt(a) - happenedAt(b) || (a.id < b.id ? -1 : 1))
    const future = date.getTime() > todayStart
    if (future && moments.length === 0) continue
    days.push({
      date,
      index,
      moments,
      still: moments.length === 0,
      isToday: date.getTime() === todayStart,
    })
  }
  return { weekStart: entry.weekStart, days }
}

/** The week's moments in reading order: every day, top to bottom. */
export function readingOrder(week: SpineWeek): Moment[] {
  return week.days.flatMap((day) => day.moments)
}
