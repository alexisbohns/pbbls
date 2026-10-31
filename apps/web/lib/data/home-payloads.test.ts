import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { afterEach, describe, expect, it, vi } from "vitest"
import { decodeClaim, decodeGuides, decodeHomeMonth, decodeHomeWraps } from "./home-payloads"

// The payloads the back-end harnesses captured, read in place (never copied):
// the cross-surface rule is to test a shared shape against real payloads.
const webRoot = fileURLToPath(new URL("../..", import.meta.url))
const oracle = (rel: string): unknown =>
  JSON.parse(readFileSync(path.join(webRoot, "../../packages/supabase/scripts/oracles", rel), "utf8"))

afterEach(() => {
  vi.restoreAllMocks()
})

describe("decodeHomeMonth", () => {
  it("reads the full month verbatim", () => {
    const month = decodeHomeMonth(oracle("path-wrap/payloads/month-full.json"))
    expect(month).not.toBeNull()
    expect(month?.month).toBe("2026-10-01")
    expect(month?.daysInMonth).toBe(31)
    expect(month?.firstWeekday).toBe(3)
    expect(month?.count).toBe(24)
    expect(month?.weeks).toHaveLength(5)
    expect(month?.weeks?.[1]).toMatchObject({ weekStart: "2026-10-05", from: 4, to: 10, count: 11, soulCount: 5 })
    expect(month?.weeks?.[1].days).toHaveLength(7)
    expect(month?.days.length).toBe(19)
    expect(month?.days[0]).toEqual({
      day: 0,
      pebbles: [{ momentId: "872ccf91-a434-4286-a0f0-4d54d2ae9308", emotionId: "f76b1274-04ab-4a9c-858d-4da26f8d124d", intensity: 3, positiveness: -1 }],
    })
  })

  it("reads the thin and quiet months", () => {
    expect(decodeHomeMonth(oracle("path-wrap/payloads/month-thin.json"))?.count).toBe(7)
    const quiet = decodeHomeMonth(oracle("path-wrap/payloads/month-quiet.json"))
    expect(quiet).toMatchObject({ month: "2026-06-01", daysInMonth: 30, firstWeekday: 0, count: 2 })
    expect(quiet?.days.map((d) => d.day)).toEqual([12, 14])
  })

  it("lays out a month without cover or weeks (below one pebble)", () => {
    expect(decodeHomeMonth({ month: "2026-10-01", days_in_month: 31, first_weekday: 3, count: 0 })).toEqual({
      month: "2026-10-01",
      daysInMonth: 31,
      firstWeekday: 3,
      count: 0,
      days: [],
      weeks: null,
    })
  })

  it("ignores unknown keys and reads out-of-range valence as the neutral medium", () => {
    vi.spyOn(console, "warn").mockImplementation(() => {})
    const month = decodeHomeMonth({
      month: "2026-10-01",
      days_in_month: 31,
      first_weekday: 3,
      count: 2,
      shiny_new_key: { anything: true },
      cover: {
        days: [
          { day: 2, pebbles: [{ moment_id: "m1", emotion_id: null, intensity: 7, polarity: 1 }, { emotion_id: "x" }] },
          "garbage",
        ],
      },
    })
    expect(month?.days).toEqual([{ day: 2, pebbles: [{ momentId: "m1", emotionId: null, intensity: 2, positiveness: 0 }] }])
  })

  it("is null for something that is not a month", () => {
    expect(decodeHomeMonth(null)).toBeNull()
    expect(decodeHomeMonth({ month: "2026-13-01", days_in_month: 31, first_weekday: 3 })).toBeNull()
  })
})

describe("decodeHomeWraps", () => {
  it("reads home-wraps.json verbatim, its explicit revealed_at null folded", () => {
    expect(decodeHomeWraps(oracle("home/payloads/home-wraps.json"))).toEqual([
      { month: "2026-09-01", count: 4, revealedAt: null, days: [2, 11, 19, 27] },
    ])
  })

  it("reads revealed_at tolerantly, to the whole second", () => {
    const at = (revealed_at: string) =>
      decodeHomeWraps({ months: [{ month: "2026-09-01", count: 1, revealed_at, extra: 1 }] })[0].revealedAt
    const expected = new Date("2026-10-30T09:12:44Z")
    expect(at("2026-10-30T09:12:44.123456+00:00")).toEqual(expected)
    expect(at("2026-10-30T09:12:44Z")).toEqual(expected)
    expect(at("2026-10-30 09:12:44+00")).toEqual(expected)
  })

  it("is empty for anything else", () => {
    expect(decodeHomeWraps(null)).toEqual([])
    expect(decodeHomeWraps({ months: null })).toEqual([])
  })
})

describe("decodeGuides", () => {
  const row = {
    slug: "stones",
    illustration: "stones",
    sort_order: 1,
    title_en: "Stones",
    title_fr: null,
    summary_en: "Nine shapes, by weight and light.",
    summary_fr: "Neuf formes, selon le poids et la lumière.",
    body_md_en: "Body",
    body_md_fr: "",
  }

  it("reads the locale and falls back to English per field", () => {
    expect(decodeGuides([row], "fr")).toEqual([
      { slug: "stones", illustration: "stones", title: "Stones", summary: "Neuf formes, selon le poids et la lumière.", bodyMd: "Body" },
    ])
    expect(decodeGuides([row], "en")[0].summary).toBe("Nine shapes, by weight and light.")
  })

  it("keeps a row with an unknown illustration, without one", () => {
    expect(decodeGuides([{ ...row, illustration: "rainbow" }], "en")[0].illustration).toBeNull()
  })
})

describe("decodeClaim", () => {
  it("reads a granted row, and [] as not granted", () => {
    expect(decodeClaim([{ slug: "blue-pebble", karma_granted: 50 }])).toEqual({ slug: "blue-pebble", karmaGranted: 50 })
    expect(decodeClaim([])).toBeNull()
    expect(decodeClaim(null)).toBeNull()
  })
})
