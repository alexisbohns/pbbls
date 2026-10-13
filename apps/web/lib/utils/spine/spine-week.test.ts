import { describe, expect, it } from "vitest"
import type { Pebble } from "@/lib/types"
import { buildWeekRollEntries, isoWeekKey } from "@/lib/utils/week-roll-entries"
import { readingOrder, spineWeek } from "./spine-week"

// TZ is Europe/Paris (vitest.config.ts). Week of Monday 5 Oct 2026, the
// brief's fixture week.
function pebble(id: string, happened_at: string): Pebble {
  return {
    id,
    name: id,
    happened_at,
    intensity: 2,
    positiveness: 0,
    visibility: "private",
    emotion_id: "e",
    soul_ids: [],
    domain_ids: [],
    collection_ids: [],
    instants: [],
    snaps: [],
    cards: [],
    glyph: null,
    cover: null,
    created_at: happened_at,
    updated_at: happened_at,
  }
}

function weekOf(pebbles: Pebble[], today: Date, key = "2026-W41") {
  const entry = buildWeekRollEntries(pebbles, today).find((e) => e.weekStartIso === key)
  if (!entry) throw new Error(`no entry ${key}`)
  return spineWeek(entry, today)
}

// Deliberately out of order, two on Tuesday, whole-second and fractional.
const WEEK = [
  pebble("tue-late", "2026-10-06T19:00:00Z"),
  pebble("sat", "2026-10-10T09:30:00.250+00:00"),
  pebble("mon", "2026-10-05T08:15:00Z"),
  pebble("tue-early", "2026-10-06T06:45:00.123456+00:00"),
  pebble("sun", "2026-10-11T21:59:00Z"), // Sun 23:59 Paris
]

describe("spineWeek", () => {
  it("lays a past week out as seven days, Monday first", () => {
    const week = weekOf(WEEK, new Date(2026, 9, 20))
    expect(week.days.map((d) => d.index)).toEqual([0, 1, 2, 3, 4, 5, 6])
    expect(week.days.map((d) => d.date.getDate())).toEqual([5, 6, 7, 8, 9, 10, 11])
    expect(week.days.every((d) => d.date.getHours() === 0)).toBe(true)
  })

  it("reads oldest first in a past week", () => {
    const week = weekOf(WEEK, new Date(2026, 9, 20))
    expect(readingOrder(week).map((p) => p.id)).toEqual(["mon", "tue-early", "tue-late", "sat", "sun"])
  })

  it("reads oldest first in the current week too, unlike the roll", () => {
    const today = new Date(2026, 9, 11, 23, 59, 30)
    const entry = buildWeekRollEntries(WEEK, today).find((e) => e.weekStartIso === "2026-W41")
    // The roll keeps its newest-first current week for its other consumers...
    expect(entry?.pebbles[0].id).toBe("sun")
    // ...and the Spine re-sorts it as a book.
    expect(readingOrder(weekOf(WEEK, today)).map((p) => p.id)).toEqual([
      "mon",
      "tue-early",
      "tue-late",
      "sat",
      "sun",
    ])
  })

  it("marks past days with no pebble still", () => {
    const week = weekOf(WEEK, new Date(2026, 9, 20))
    expect(week.days.filter((d) => d.still).map((d) => d.index)).toEqual([2, 3, 4])
    expect(week.days.filter((d) => d.still).every((d) => d.pebbles.length === 0)).toBe(true)
  })

  it("omits the current week's future days, and keeps today", () => {
    const today = new Date(2026, 9, 7, 12) // Wednesday
    const week = weekOf(WEEK.filter((p) => ["mon", "tue-early"].includes(p.id)), today)
    expect(week.days.map((d) => d.index)).toEqual([0, 1, 2])
    const wednesday = week.days[2]
    expect(wednesday).toMatchObject({ isToday: true, still: true, pebbles: [] })
    expect(week.days.filter((d) => d.isToday)).toHaveLength(1)
  })

  it("never drops a future-dated pebble", () => {
    const today = new Date(2026, 9, 7, 12)
    const week = weekOf([pebble("fri", "2026-10-09T10:00:00Z")], today)
    expect(week.days.map((d) => d.index)).toEqual([0, 1, 2, 4])
    expect(week.days.at(-1)?.still).toBe(false)
  })

  it("keeps a Sunday 23:59 pebble on the Sunday the clocks go back", () => {
    const sunday = pebble("dst", "2026-10-25T22:59:00Z") // Sun 25 Oct 23:59 Paris, UTC+1
    const week = weekOf([sunday], new Date(2026, 10, 2), "2026-W43")
    expect(week.days[6].pebbles.map((p) => p.id)).toEqual(["dst"])
    expect(week.days.map((d) => d.date.getDate())).toEqual([19, 20, 21, 22, 23, 24, 25])
  })

  it("returns the input in chronological order when the days are read in order", () => {
    // The successor of path-layout's round-robin invariant: whatever order the
    // pebbles arrive in, reading the Spine top to bottom is chronological.
    const shuffled = [...WEEK].reverse()
    const read = readingOrder(weekOf(shuffled, new Date(2026, 9, 20)))
    const chronological = [...WEEK].sort(
      (a, b) => new Date(a.happened_at).getTime() - new Date(b.happened_at).getTime(),
    )
    expect(read).toEqual(chronological)
    expect(isoWeekKey(new Date(read[0].happened_at))).toBe("2026-W41")
  })
})
