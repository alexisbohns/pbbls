import { describe, expect, it } from "vitest"
import { calendarDayFrom, ceremonyPullFrom, decodeAchievementsPage, instantFrom } from "./achievements-payload"
import {
  DUMPED_GET_ACHIEVEMENTS,
  DUMPED_GET_ACHIEVEMENTS_PULLED,
  DUMPED_GET_ACHIEVEMENTS_SEEN,
  DUMPED_GET_ACHIEVEMENTS_YEAR,
  GET_ACHIEVEMENTS_VARIANTS,
} from "./__fixtures__/achievements-captured"
import type { AchievementsDecode } from "./achievements-payload"

function decode(json: string): AchievementsDecode {
  const decoded = decodeAchievementsPage(JSON.parse(json))
  if (!decoded) throw new Error("payload did not decode")
  return decoded
}

const at = (iso: string) => new Date(iso)

describe("dumped payloads: get-achievements", () => {
  const { page, skipped } = decode(DUMPED_GET_ACHIEVEMENTS)

  it("decodes every section, skipping none", () => {
    expect(skipped).toEqual([])
    expect([page.since, page.daysKept]).toEqual(["2026-10-30", 1])
  })

  it("reads the explorer: its centre, the first five in Home order, the six firsts all done in kind order", () => {
    const e = page.explorer
    expect(e?.firstPebble).toEqual({
      momentId: "1357b116-055a-444e-b773-54ef3ed7d1b6",
      name: "heavy small badfcbbf",
      happenedAt: at("2026-03-02T09:00:00Z"),
      emotionId: "50a68890-f6bb-e67a-6655-e84ff0b71f49",
      intensity: 1,
      positiveness: -1,
    })
    expect(e?.firstFive.map((f) => [f.task, f.done, f.on, f.subject?.kind ?? null])).toEqual([
      ["first-moment", true, "2026-10-30", "moment"],
      ["first-pebble", true, "2026-10-30", "pebble"],
      ["first-collection", false, null, null],
      ["first-soul", false, null, null],
      ["first-glyph", false, null, null],
    ])
    expect(e?.sixFirsts.map((s) => [s.kind, s.done])).toEqual([
      ["small", true],
      ["medium", true],
      ["large", true],
      ["lowlight", true],
      ["neutral", true],
      ["highlight", true],
    ])
    // An unnamed moment's snapshot omits its name.
    expect(e?.sixFirsts[1].pebble).toMatchObject({ name: null, intensity: 2, positiveness: 0 })
    expect(e?.sixFirsts[1].on).toBe("2026-03-03")
    expect(e?.twentyFive).toEqual({ count: 3, done: false, on: null, pebble: null, isNew: false })
    expect(e?.ringsDone).toEqual(["six_firsts"])
  })

  it("reads the six firsts' completion, not offered yet, with the stone that made it whole", () => {
    expect(page.explorer?.completion).toEqual({
      stack: "six_firsts",
      completedAt: at("2026-10-30T20:23:42Z"),
      offeredFrom: at("2026-10-31T05:00:00Z"),
      ready: false,
      pulledAt: null,
      pebble: {
        momentId: "b3d1671d-7a5c-4944-a22b-dacfb1b4c6ea",
        name: "large bright badfcbbf",
        happenedAt: at("2026-03-04T09:00:00Z"),
        emotionId: "50a68890-f6bb-e67a-6655-e84ff0b71f49",
        intensity: 3,
        positiveness: 1,
      },
    })
  })

  it("keeps the 38 seats in the served order, and the families in the sheet's order", () => {
    const e = page.emotions
    expect([e?.count, e?.of, e?.seats.length, e?.since]).toEqual([1, 38, 38, "2026-03-02"])
    expect(e?.seats.map((s) => s.seat)).toEqual(Array.from({ length: 38 }, (_, k) => k))
    expect(e?.seats[0]).toMatchObject({ family: "joy", met: true, on: "2026-03-02", isNew: false })
    expect(e?.seats[1]).toMatchObject({ met: false, on: null, pebble: null })
    expect(e?.families.map((f) => f.family)).toEqual(["joy", "pride", "anger", "fear", "shame", "sadness", "peace"])
    expect(e?.families[0]).toEqual({ family: "joy", met: [0], notYet: [1, 2, 3, 4, 5] })
    expect(e?.completion).toBeNull()
  })

  it("reads the domains: the tier, the one entered with its day, the seventeen not yet", () => {
    const d = page.domains
    expect([d?.count, d?.of, d?.coverage]).toEqual([1, 18, "few"])
    expect(d?.entered).toHaveLength(1)
    expect(d?.entered[0]).toMatchObject({ domainId: "55a005c8-e2af-4f40-ad19-bd6914bee484", on: "2026-03-02", isNew: false })
    expect(d?.notYet).toHaveLength(17)
    expect(d?.completion).toBeNull()
  })

  it("reads the counting ladders at the server's scale", () => {
    expect(page.pebbles?.next).toEqual({ threshold: 10, left: 7, progress: 0.222 })
    expect(page.pebbles?.levels.map((l) => [l.threshold, l.on])).toEqual([[1, "2026-03-02"]])
    expect(page.pebbles?.ladder).toEqual([1, 10, 25, 50, 100, 250, 500, 1000])
    expect([page.pebbles?.thenEvery, page.pebbles?.since]).toEqual([500, "2026-03-02"])
    expect(page.glyphsCarved).toEqual({
      count: 0,
      levels: [],
      next: { threshold: 10, left: 10, progress: 0 },
      ladder: [10, 25, 50, 75, 100],
      thenEvery: null,
      since: null,
    })
    expect(page.glyphSales?.next).toEqual({ threshold: 1, left: 1, progress: 0 })
  })

  it("reads the year as served: twelve months, the table's months, the out-of-reach step", () => {
    expect(page.years).toHaveLength(1)
    const [y] = page.years
    expect([y.year, y.current, y.daysTarget, y.months.length]).toEqual([2026, true, 14, 12])
    expect(y.months[2]).toEqual({
      month: 3,
      state: "kept",
      family: "joy",
      momentDays: 3,
      pebbleDays: 3,
      fullWeeks: 4,
      weeksFilled: 1,
      level: 0,
      progress: 0.214,
      done: { moments: false, pebbles: false, weeks: false },
    })
    expect(y.months[0]).toMatchObject({ state: "quiet", family: null, progress: 0 })
    expect(y.months[10].state).toBe("upcoming")
    expect(y.listed).toEqual([10, 9, 8, 7, 6, 5, 4, 3])
    expect(y.nextStep).toEqual({ month: 10, needs: "moments", daysLeft: 14, reachable: false })
  })

  it("reads the Profile shelf with its explicit null threshold, and an empty seen map", () => {
    expect(page.shelf).toEqual([
      { slug: "pebble-count-1", family: "pebble_count", threshold: 1, unlockedOn: "2026-10-30" },
      { slug: "first-moment", family: "first_moment", threshold: null, unlockedOn: "2026-10-30" },
      { slug: "first-pebble", family: "first_pebble", threshold: null, unlockedOn: "2026-10-30" },
    ])
    expect(page.shelfCount).toBe(3)
    expect(page.seen).toEqual({})
  })
})

describe("dumped payloads: get-achievements-seen", () => {
  const { page } = decode(DUMPED_GET_ACHIEVEMENTS_SEEN)

  it("reads when the cards were seen, at whole seconds", () => {
    expect(page.seen).toEqual({ explorer: at("2026-10-30T20:23:50Z"), emotions: at("2026-10-30T20:23:50Z") })
  })

  it("marks what was recorded since as new", () => {
    expect(page.emotions?.seats[1]).toMatchObject({ met: true, isNew: true, on: "2026-03-06" })
    expect(page.emotions?.families[0]).toEqual({ family: "joy", met: [0, 1], notYet: [2, 3, 4, 5] })
    expect(page.explorer?.firstFive[3]).toMatchObject({ task: "first-soul", done: true, subject: { kind: "soul", name: "Mara badfcbbf" } })
    expect(page.shelfCount).toBe(4)
  })
})

describe("dumped payloads: get-achievements-pulled", () => {
  const { page } = decode(DUMPED_GET_ACHIEVEMENTS_PULLED)

  it("reads a pulled completion: pulled_at set, no longer ready", () => {
    expect(page.explorer?.completion).toMatchObject({
      stack: "six_firsts",
      completedAt: at("2026-01-01T00:00:00Z"),
      offeredFrom: at("2026-01-02T05:00:00Z"),
      ready: false,
      pulledAt: at("2026-10-30T20:23:51Z"),
    })
  })
})

describe("dumped payloads: get-achievements-year", () => {
  const { page } = decode(DUMPED_GET_ACHIEVEMENTS_YEAR)

  it("serves the current year, then a past one with its levels, newest first", () => {
    expect(page.years.map((y) => [y.year, y.current, y.months.length])).toEqual([
      [2026, true, 12],
      [2025, false, 12],
    ])
    const past = page.years[1]
    expect(past.nextStep).toBeNull()
    expect(past.listed).toEqual([12, 11, 10, 9, 8, 7, 6, 5, 4, 3])
    expect(past.months[5]).toMatchObject({ level: 2, progress: 0, family: "joy", done: { moments: true, pebbles: true, weeks: true } })
    expect(past.months[2]).toMatchObject({ level: 1, progress: 0.75, family: "sadness", done: { moments: true, pebbles: false, weeks: false } })
    expect(page.years[0].nextStep).toEqual({ month: 10, needs: "moments", daysLeft: 10, reachable: false })
  })
})

describe("tolerance: the variants no harness run produces", () => {
  const { page, skipped } = decode(GET_ACHIEVEMENTS_VARIANTS)

  it("hides absent, null and mistyped sections, and reports the mistyped ones", () => {
    expect([page.since, page.daysKept]).toEqual([null, null])
    expect(page.glyphsCarved).toBeNull()
    expect(page.glyphSales).toBeNull()
    expect(skipped).toEqual(["glyphs_carved"])
  })

  it("keeps a snapshot whatever it lacks, reading instants tolerantly and out-of-range valence as neutral medium", () => {
    expect(page.explorer?.firstPebble).toEqual({
      momentId: "9d1c7a10-0000-4000-8000-000000000001",
      name: null,
      happenedAt: at("2026-03-02T18:20:00.376Z"),
      emotionId: "50a68890-f6bb-e67a-6655-e84ff0b71f49",
      intensity: 2,
      positiveness: 0,
    })
    expect(page.explorer?.sixFirsts[0].pebble).toEqual({
      momentId: null,
      name: "No moment id",
      happenedAt: at("2026-03-02T09:00:00Z"),
      emotionId: null,
      intensity: 1,
      positiveness: 1,
    })
  })

  it("drops null elements alone and keeps unknown enums as null, so no seat shifts", () => {
    const e = page.explorer
    expect(e?.firstFive.map((f) => [f.task, f.subject])).toEqual([
      ["first-moment", { kind: "moment", id: "9d1c7a10-0000-4000-8000-000000000002", name: null }],
      [null, null],
      ["first-soul", { kind: null, id: null, name: "Mara" }],
    ])
    expect(e?.sixFirsts.map((s) => s.kind)).toEqual(["small", null])
    expect(e?.ringsDone).toEqual(["first_five"])
    expect(e?.twentyFive).toBeNull()
    expect(e?.completion).toEqual({
      stack: "six_firsts",
      completedAt: at("2026-03-14T21:41:07.500Z"),
      offeredFrom: null,
      ready: true,
      pulledAt: null,
      pebble: null,
    })
  })

  it("keeps seats missing an id or an index, and an unknown family as its string", () => {
    expect(page.emotions?.seats.map((s) => [s.seat, s.emotionId, s.family, s.met])).toEqual([
      [0, "50a68890-f6bb-e67a-6655-e84ff0b71f49", "awe", true],
      [1, null, "joy", true],
      [null, "98bdc543-6b26-e1c8-da1d-50ae8b9973ff", "joy", false],
    ])
    expect(page.emotions?.families).toEqual([
      { family: "awe", met: [0], notYet: [] },
      { family: null, met: [1], notYet: [2] },
    ])
    expect(page.emotions?.since).toBeNull()
  })

  it("reads an unknown tier as null, keeps an entry without its id, and a completion without its times", () => {
    const d = page.domains
    expect(d?.coverage).toBeNull()
    expect(d?.entered.map((x) => [x.domainId, x.isNew])).toEqual([
      ["55a005c8-e2af-4f40-ad19-bd6914bee484", false],
      [null, true],
    ])
    expect(d?.notYet).toEqual(["4f1fec32-318e-4fc1-b095-7ed793ebcaac"])
    expect(d?.completion).toEqual({ stack: "domains", completedAt: null, offeredFrom: null, ready: false, pulledAt: null, pebble: null })
  })

  it("clamps a progress and drops a null rung", () => {
    expect(page.pebbles).toEqual({
      count: 10234,
      levels: [],
      next: { threshold: 250, left: 113, progress: 1 },
      ladder: [1, 10, 25],
      thenEvery: 500,
      since: null,
    })
  })

  it("drops a year without twelve distinct months, and filters listed months", () => {
    expect(page.years.map((y) => y.year)).toEqual([2026, 2023])
    const [y, bare] = page.years
    expect(y.listed).toEqual([11, 5])
    expect(y.months[4]).toEqual({
      month: 5,
      state: "quiet",
      family: "awe",
      momentDays: 15,
      pebbleDays: 15,
      fullWeeks: 4,
      weeksFilled: 4,
      level: 2,
      progress: 0.25,
      done: { moments: false, pebbles: false, weeks: false },
    })
    expect(y.months[5].progress).toBe(0)
    expect(y.nextStep).toEqual({ month: 11, needs: null, daysLeft: 3, reachable: true })
    expect(bare.months[0]).toEqual({
      month: 1,
      state: "quiet",
      family: null,
      momentDays: 0,
      pebbleDays: 0,
      fullWeeks: 0,
      weeksFilled: 0,
      level: 0,
      progress: 0,
      done: { moments: false, pebbles: false, weeks: false },
    })
    expect(bare.nextStep).toEqual({ month: 3, needs: "pebbles", daysLeft: 2, reachable: true })
  })

  it("keeps known cards' readable seen times only, and a shelf row without its day", () => {
    expect(page.seen).toEqual({ explorer: at("2026-10-02T08:14:05Z"), year: at("2026-10-02T08:14:05.123Z") })
    expect(page.shelf).toEqual([
      { slug: "first-moment", family: "first_moment", threshold: null, unlockedOn: "2026-10-30" },
      { slug: "pebble-count-10", family: "pebble_count", threshold: 10, unlockedOn: null },
    ])
    expect(page.shelfCount).toBe(9)
  })

  it("reads anything but an object as no page", () => {
    expect(decodeAchievementsPage(null)).toBeNull()
    expect(decodeAchievementsPage([])).toBeNull()
    expect(decodeAchievementsPage("{}")).toBeNull()
  })

  it("reports a mistyped years list, seen map or shelf without failing the rest", () => {
    const decoded = decodeAchievementsPage({ years: [{ year: 2026, months: "twelve" }], seen: { explorer: 3 }, shelf: {}, days_kept: 2 })
    expect(decoded?.skipped).toEqual(["years", "seen", "shelf"])
    expect(decoded?.page).toMatchObject({ years: [], seen: {}, shelf: [], daysKept: 2 })
  })
})

describe("instantFrom", () => {
  it("reads every spelling the surfaces emit, and refuses a zoneless or unreadable one", () => {
    expect(instantFrom("2026-03-02T09:00:00Z")).toEqual(at("2026-03-02T09:00:00Z"))
    expect(instantFrom("2026-03-02T09:00:00+00:00")).toEqual(at("2026-03-02T09:00:00Z"))
    expect(instantFrom("2026-03-02 10:00:00+01")).toEqual(at("2026-03-02T09:00:00Z"))
    expect(instantFrom("2026-03-02T10:00:00.376669+0100")).toEqual(at("2026-03-02T09:00:00.376Z"))
    expect(instantFrom("2026-03-02T09:00:00")).toBeNull()
    expect(instantFrom("not a time")).toBeNull()
    expect(instantFrom(null)).toBeNull()
  })
})

describe("calendarDayFrom", () => {
  it("keeps a YYYY-MM-DD day and refuses anything else", () => {
    expect(calendarDayFrom("2026-03-02")).toBe("2026-03-02")
    expect(calendarDayFrom("2026-3-2")).toBeNull()
    expect(calendarDayFrom("2026-13-02")).toBeNull()
    expect(calendarDayFrom("2026-03-02T00:00:00Z")).toBeNull()
  })
})

describe("ceremonyPullFrom", () => {
  it("reads the first pull's row, karma 0 included", () => {
    expect(ceremonyPullFrom(JSON.parse(`[{"stack": "six_firsts", "karma_granted": 20}]`))).toEqual({ karmaGranted: 20 })
    expect(ceremonyPullFrom([{ stack: "six_firsts", karma_granted: 0 }])).toEqual({ karmaGranted: 0 })
  })

  it("reads no row as no success", () => {
    expect(ceremonyPullFrom([])).toBeNull()
    expect(ceremonyPullFrom([null])).toBeNull()
  })

  it("throws on a body that is not rows", () => {
    expect(() => ceremonyPullFrom(null)).toThrow()
    expect(() => ceremonyPullFrom({ stack: "six_firsts" })).toThrow()
  })
})
