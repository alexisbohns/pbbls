import { describe, expect, it } from "vitest"
import type { Pebble, Soul } from "@/lib/types"
import { isoWeekStart } from "@/lib/utils/week-roll-entries"
import { PERSONS_CAP, personsOfWeek, splitPersons } from "./persons-of-week"

// vitest.config.ts pins TZ to Europe/Paris: summer time ends Sunday 25 Oct
// 2026 at 03:00, so the week of Monday 19 Oct has a 25-hour Sunday.
const DST_WEEK = isoWeekStart(new Date(2026, 9, 19))

function soul(id: string, name = id): Soul {
  return { id, name, glyph_id: "g", created_at: "", updated_at: "" }
}

function pebble(id: string, happened_at: string, soul_ids: string[]): Pebble {
  return {
    id,
    moment_id: id,
    name: id,
    happened_at,
    intensity: 2,
    positiveness: 0,
    visibility: "private",
    emotion_id: "e",
    soul_ids,
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

const souls = (...list: Soul[]) => new Map(list.map((s) => [s.id, s]))

describe("personsOfWeek", () => {
  it("marks the local day of each pebble, Monday first", () => {
    const rows = personsOfWeek(
      [
        // Monday 19 Oct 10:00 Paris (summer time, UTC+2).
        pebble("a", "2026-10-19T08:00:00Z", ["mara"]),
        // Thursday 22 Oct 23:30 Paris is still Thursday, though it is 21:30Z.
        pebble("b", "2026-10-22T21:30:00Z", ["mara"]),
      ],
      DST_WEEK,
      souls(soul("mara")),
      "en",
    )
    expect(rows).toHaveLength(1)
    expect(rows[0].days).toEqual([true, false, false, true, false, false, false])
    expect(rows[0].dayCount).toBe(2)
    expect(rows[0].pebbleCount).toBe(2)
  })

  it("keeps a pebble at 23:59 on the Sunday DST ends on that Sunday", () => {
    // Sunday 25 Oct 23:59 Paris is winter time (UTC+1): 22:59Z. Whole-second as
    // iOS and Android emit it, fractional as Postgres returns it.
    for (const at of ["2026-10-25T22:59:00Z", "2026-10-25T22:59:59.123456+00:00"]) {
      const [row] = personsOfWeek([pebble("a", at, ["mara"])], DST_WEEK, souls(soul("mara")), "en")
      expect(row.days).toEqual([false, false, false, false, false, false, true])
    }
  })

  it("leaves out pebbles of the week before or after", () => {
    const rows = personsOfWeek(
      [
        pebble("before", "2026-10-18T21:59:00Z", ["mara"]), // Sun 18 Oct 23:59 Paris
        pebble("after", "2026-10-25T23:00:00Z", ["elio"]), // Mon 26 Oct 00:00 Paris
      ],
      DST_WEEK,
      souls(soul("mara"), soul("elio")),
      "en",
    )
    expect(rows).toEqual([])
  })

  it("buckets correctly across the spring change too", () => {
    // Summer time starts Sunday 29 Mar 2026 at 02:00: a 23-hour Sunday.
    const week = isoWeekStart(new Date(2026, 2, 23))
    const [row] = personsOfWeek(
      [pebble("a", "2026-03-29T21:59:00Z", ["mara"])], // Sun 29 Mar 23:59 Paris
      week,
      souls(soul("mara")),
      "en",
    )
    expect(row.days[6]).toBe(true)
  })

  it("does not pull the next Monday into a week that lost an hour", () => {
    // Monday 30 Mar 00:30 Paris is 6 days 23.5 hours after Monday 23 Mar.
    const week = isoWeekStart(new Date(2026, 2, 23))
    const rows = personsOfWeek(
      [pebble("a", "2026-03-29T22:30:00Z", ["mara"])],
      week,
      souls(soul("mara")),
      "en",
    )
    expect(rows).toEqual([])
  })

  it("orders by days present, then pebbles, then name", () => {
    const rows = personsOfWeek(
      [
        pebble("1", "2026-10-19T08:00:00Z", ["zoe", "ana", "bea", "cy"]),
        pebble("2", "2026-10-19T09:00:00Z", ["ana"]),
        pebble("3", "2026-10-20T08:00:00Z", ["zoe"]),
      ],
      DST_WEEK,
      souls(soul("zoe", "Zoé"), soul("ana", "Ana"), soul("bea", "Béa"), soul("cy", "Cy")),
      "fr",
    )
    // zoe: 2 days. ana: 1 day, 2 pebbles. bea and cy: 1 day, 1 pebble, by name.
    expect(rows.map((r) => r.soul.id)).toEqual(["zoe", "ana", "bea", "cy"])
  })

  it("collates names by locale, accents included", () => {
    const rows = personsOfWeek(
      [pebble("1", "2026-10-19T08:00:00Z", ["e2", "e1", "z"])],
      DST_WEEK,
      souls(soul("e2", "Élio"), soul("e1", "Eva"), soul("z", "Zack")),
      "fr",
    )
    expect(rows.map((r) => r.soul.name)).toEqual(["Élio", "Eva", "Zack"])
  })

  it("breaks a full tie on id, so the order is deterministic", () => {
    const rows = personsOfWeek(
      [pebble("1", "2026-10-19T08:00:00Z", ["b", "a"])],
      DST_WEEK,
      souls(soul("b", "Sam"), soul("a", "Sam")),
      "en",
    )
    expect(rows.map((r) => r.soul.id)).toEqual(["a", "b"])
  })

  it("skips souls it cannot name and counts a duplicated soul once", () => {
    const [row, ...rest] = personsOfWeek(
      [pebble("1", "2026-10-19T08:00:00Z", ["mara", "mara", "deleted"])],
      DST_WEEK,
      souls(soul("mara")),
      "en",
    )
    expect(rest).toEqual([])
    expect(row.pebbleCount).toBe(1)
  })
})

describe("splitPersons", () => {
  it("shows eight rows and folds the rest", () => {
    const ids = Array.from({ length: 14 }, (_, i) => `s${String(i).padStart(2, "0")}`)
    const rows = personsOfWeek(
      [pebble("1", "2026-10-19T08:00:00Z", ids)],
      DST_WEEK,
      souls(...ids.map((id) => soul(id))),
      "en",
    )
    const { shown, rest } = splitPersons(rows)
    expect(PERSONS_CAP).toBe(8)
    expect(shown).toHaveLength(8)
    expect(rest).toHaveLength(6)
    expect([...shown, ...rest]).toEqual(rows)
  })

  it("folds nothing at or under the cap", () => {
    expect(splitPersons([]).rest).toEqual([])
  })
})
