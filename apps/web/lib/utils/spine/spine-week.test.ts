import { describe, expect, it } from "vitest"
import { momentFromViewRow } from "@/lib/data/moment-row"
import {
  ANDROID_DETAIL_BARE_NAMED,
  ANDROID_DETAIL_COMPLETED,
  IOS_DETAIL_COMPLETED,
  IOS_DETAIL_PHOTO,
} from "@/lib/data/__fixtures__/moments-captured"
import type { Moment, MomentPebble } from "@/lib/types"
import { buildWeekRollEntries, isoWeekKey } from "@/lib/utils/week-roll-entries"
import { readingOrder, spineWeek } from "./spine-week"

// TZ is Europe/Paris (vitest.config.ts). Week of Monday 5 Oct 2026, the
// brief's fixture week.
function momentFixture(overrides: Partial<Moment> & Pick<Moment, "id" | "happened_at">): Moment {
  const { id, happened_at } = overrides
  const pebble: MomentPebble = {
    id: `pebble-${id}`,
    intensity: 2,
    positiveness: 0,
    visibility: "private",
    emotion_id: "e",
    soul_ids: [],
    domain_ids: [],
    collection_ids: [],
    cards: [],
    glyph: null,
    created_at: happened_at,
    updated_at: happened_at,
  }
  return {
    name: id,
    description: null,
    snaps: [],
    instants: [],
    cover: null,
    created_at: happened_at,
    updated_at: happened_at,
    pebble,
    ...overrides,
  }
}

/** A completed moment. */
const pebble = (id: string, happened_at: string) => momentFixture({ id, happened_at })
/** A moment that is not a pebble yet. */
const bare = (id: string, happened_at: string) => momentFixture({ id, happened_at, pebble: null })

function entryOf(moments: Moment[], today: Date, key = "2026-W41") {
  const entry = buildWeekRollEntries(moments, today).find((e) => e.weekStartIso === key)
  if (!entry) throw new Error(`no entry ${key}`)
  return entry
}

function weekOf(moments: Moment[], today: Date, key = "2026-W41") {
  return spineWeek(entryOf(moments, today, key), today)
}

const row = (json: string): Record<string, unknown> => JSON.parse(json)

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
    expect(entry?.moments[0].id).toBe("sun")
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
    expect(week.days.filter((d) => d.still).every((d) => d.moments.length === 0)).toBe(true)
  })

  it("omits the current week's future days, and keeps today", () => {
    const today = new Date(2026, 9, 7, 12) // Wednesday
    const week = weekOf(WEEK.filter((p) => ["mon", "tue-early"].includes(p.id)), today)
    expect(week.days.map((d) => d.index)).toEqual([0, 1, 2])
    const wednesday = week.days[2]
    expect(wednesday).toMatchObject({ isToday: true, still: true, moments: [] })
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
    expect(week.days[6].moments.map((p) => p.id)).toEqual(["dst"])
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

  it("gives a bare moment its day: the day is not still", () => {
    // Wednesday 7 Oct is still in the fixture week; a bare moment fills it.
    const week = weekOf([...WEEK, bare("wed-bare", "2026-10-07T12:00:00Z")], new Date(2026, 9, 20))
    const wednesday = week.days[2]
    expect(wednesday).toMatchObject({ still: false })
    expect(wednesday.moments.map((m) => m.id)).toEqual(["wed-bare"])
    expect(week.days.filter((d) => d.still).map((d) => d.index)).toEqual([3, 4])
  })

  it("reads a bare moment in its place among the pebbles", () => {
    const between = bare("tue-noon", "2026-10-06T10:00:00Z")
    const read = readingOrder(weekOf([...WEEK, between], new Date(2026, 9, 20)))
    expect(read.map((m) => m.id)).toEqual(["mon", "tue-early", "tue-noon", "tue-late", "sat", "sun"])
  })

  it("counts only pebbles in a week of bare moments, and draws its days anyway", () => {
    const today = new Date(2026, 9, 20)
    const moments = [bare("mon", "2026-10-05T08:15:00Z"), bare("thu", "2026-10-08T18:00:00Z")]
    const entry = entryOf(moments, today)
    expect(entry.moments).toHaveLength(2)
    expect(entry.pebbleCount).toBe(0)
    const week = spineWeek(entry, today)
    expect(week.days.filter((d) => !d.still).map((d) => d.index)).toEqual([0, 3])
  })

  it("counts the completed moments of a mixed week", () => {
    const entry = entryOf([...WEEK, bare("wed-bare", "2026-10-07T12:00:00Z")], new Date(2026, 9, 20))
    expect(entry.moments).toHaveLength(6)
    expect(entry.pebbleCount).toBe(5)
  })

  it("lays out what iOS and Android wrote, verbatim", () => {
    // Sunday 11 Oct 21:04 Paris (iOS photo-only, bare), then Monday 12 Oct:
    // 08:40 (Android bare, named), 10:30 (iOS completed), 20:05 (Android completed).
    const moments = [
      momentFromViewRow(row(IOS_DETAIL_PHOTO), new Map()),
      momentFromViewRow(row(IOS_DETAIL_COMPLETED), new Map()),
      ...[ANDROID_DETAIL_BARE_NAMED, ANDROID_DETAIL_COMPLETED].map((json) =>
        momentFromViewRow(JSON.parse(json)[0], new Map()),
      ),
    ]
    const today = new Date(2026, 9, 14, 9)
    const w41 = entryOf(moments, today, "2026-W41")
    expect(w41.moments.map((m) => m.name)).toEqual([null])
    expect(w41.pebbleCount).toBe(0)
    expect(spineWeek(w41, today).days[6].moments).toHaveLength(1)

    const w42 = spineWeek(entryOf(moments, today, "2026-W42"), today)
    expect(w42.days[0].moments.map((m) => m.name)).toEqual([
      "Morning coffee on the balcony",
      "Coffee",
      "Evening walk with Maya",
    ])
    expect(entryOf(moments, today, "2026-W42").pebbleCount).toBe(2)
  })
})
