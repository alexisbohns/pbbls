import { describe, expect, it } from "vitest"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"
import { closestEntry, focusedEntryOf, steppedKey, weekParamKey } from "./path-focus"

function entry(y: number, m: number, d: number, iso: string): WeekRollEntry {
  return { weekStart: new Date(y, m - 1, d), weekStartIso: iso, isoWeek: Number(iso.slice(-2)), moments: [], pebbleCount: 0 }
}

const ENTRIES = [entry(2026, 9, 28, "2026-W40"), entry(2026, 10, 12, "2026-W42"), entry(2026, 10, 26, "2026-W44")]

describe("the Path's focus", () => {
  it("lands on the closest week, the earlier on a tie", () => {
    expect(closestEntry(ENTRIES, new Date(2026, 9, 13)).weekStartIso).toBe("2026-W42")
    expect(closestEntry(ENTRIES, new Date(2026, 9, 5)).weekStartIso).toBe("2026-W40")
  })

  it("reads a ?week= Monday at UTC midnight in the reader's calendar", () => {
    expect(weekParamKey(new Date(Date.UTC(2026, 9, 12)), ENTRIES)).toBe("2026-W42")
    expect(weekParamKey(new Date(Date.UTC(2026, 10, 30)), ENTRIES)).toBe("2026-W44")
    expect(weekParamKey(new Date(Date.UTC(2026, 9, 12)), [])).toBeNull()
  })

  it("falls back to the week closest to today when the key is gone", () => {
    expect(focusedEntryOf(ENTRIES, "2026-W42", new Date(2026, 10, 1))?.weekStartIso).toBe("2026-W42")
    expect(focusedEntryOf(ENTRIES, "2026-W43", new Date(2026, 10, 1))?.weekStartIso).toBe("2026-W44")
    expect(focusedEntryOf([], "2026-W43", new Date(2026, 10, 1))).toBeUndefined()
  })

  it("steps one week with the arrows, and stops at either end", () => {
    expect(steppedKey(ENTRIES, "2026-W42", -1)).toBe("2026-W40")
    expect(steppedKey(ENTRIES, "2026-W42", 1)).toBe("2026-W44")
    expect(steppedKey(ENTRIES, "2026-W40", -1)).toBeNull()
    expect(steppedKey(ENTRIES, "2026-W44", 1)).toBeNull()
  })
})
