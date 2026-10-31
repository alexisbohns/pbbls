import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { decodeHomeMonth } from "@/lib/data/home-payloads"
import type { HomeMonth } from "@/lib/types"
import { monthColumns, sealWidth } from "./month-columns"

const webRoot = fileURLToPath(new URL("../..", import.meta.url))
function month(name: string): HomeMonth {
  const raw: unknown = JSON.parse(
    readFileSync(path.join(webRoot, `../../packages/supabase/scripts/oracles/path-wrap/payloads/${name}.json`), "utf8"),
  )
  const decoded = decodeHomeMonth(raw)
  if (!decoded) throw new Error(`${name} did not decode`)
  return decoded
}

describe("monthColumns", () => {
  it("lays out October 2026 from the served weeks", () => {
    const columns = monthColumns(month("month-full"))
    expect(columns.map((c) => c.weekStart)).toEqual(["2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26"])
    // 28 September – 4 October: three days of September, then 1–4 October.
    expect(columns[0].days.slice(0, 3)).toEqual([{ kind: "outside" }, { kind: "outside" }, { kind: "outside" }])
    // Day 0 (1 October, a Thursday) holds one pebble.
    expect(columns[0].days[3]).toMatchObject({ kind: "pebbles" })
    // The last week runs into November: Sunday 1 November is outside.
    expect(columns[4].days[6]).toEqual({ kind: "outside" })
    expect(columns[1]).toMatchObject({ from: 4, to: 10, count: 11, soulCount: 5 })
  })

  it("shows at most three seals a day, in served order", () => {
    const m = month("month-full")
    const columns = monthColumns(m)
    for (const column of columns) {
      for (const day of column.days) if (day.kind === "pebbles") expect(day.pebbles.length).toBeLessThanOrEqual(3)
    }
    const day4 = m.days.find((d) => d.day === 4)
    const placed = columns[1].days[0]
    expect(placed.kind === "pebbles" ? placed.pebbles : []).toEqual(day4?.pebbles.slice(0, 3))
  })

  it("lays out June 2026 (starts on a Monday) and the thin May", () => {
    const june = monthColumns(month("month-quiet"))
    expect(june[0].weekStart).toBe("2026-06-01")
    expect(june[0].days[0]).toEqual({ kind: "empty" })
    expect(monthColumns(month("month-thin")).length).toBeGreaterThanOrEqual(5)
  })

  it("lays out a month without cover or weeks: five columns of sockets, dotted cairns", () => {
    const columns = monthColumns({ month: "2026-10-01", daysInMonth: 31, firstWeekday: 3, count: 0, days: [], weeks: null })
    expect(columns.map((c) => c.weekStart)).toEqual(["2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26"])
    expect(columns[0]).toMatchObject({ from: 0, to: 3, count: 0, cairn: null })
    expect(columns[4]).toMatchObject({ from: 25, to: 30 })
    expect(columns[0].days.map((d) => d.kind)).toEqual(["outside", "outside", "outside", "empty", "empty", "empty", "empty"])
    expect(columns.every((c) => c.cairn === null)).toBe(true)
  })

  it("gives a week with moments but no pebble a dotted cairn", () => {
    const m = month("month-thin")
    const columns = monthColumns(m)
    const quiet = m.weeks?.findIndex((w) => w.count === 0) ?? -1
    if (quiet >= 0) expect(columns[quiet].cairn).toBeNull()
  })
})

describe("sealWidth", () => {
  it("scales the pip by intensity", () => {
    expect(sealWidth(1)).toBeCloseTo(12.75, 9)
    expect(sealWidth(2)).toBe(15)
    expect(sealWidth(3)).toBeCloseTo(17.25, 9)
  })
})
