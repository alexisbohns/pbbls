import { describe, expect, it } from "vitest"
import { bareMonth, daysInMonth, firstWeekOf, lastDayLabel, monthTitle, weekMonthAbbr, weekRange, weekdayLetters } from "./month-labels"

describe("month labels", () => {
  it("names the month and its weeks in both languages", () => {
    expect(monthTitle("2026-10-01", "en")).toBe("October 2026")
    expect(monthTitle("2026-10-01", "fr")).toBe("octobre 2026")
    expect(weekRange("2026-10-01", 4, 10, "en")).toBe("5 – 11 October")
    expect(weekRange("2026-10-01", 4, 10, "fr")).toBe("5 – 11 octobre")
    expect(weekMonthAbbr("2026-09-28", "en")).toBe("Sep")
    expect(weekMonthAbbr("2026-09-28", "fr")).toBe("sept")
    expect(weekdayLetters("en")).toEqual(["M", "T", "W", "T", "F", "S", "S"])
    expect(lastDayLabel("2026-10-01", "en")).toBe("31 October")
  })

  it("does the calendar arithmetic in UTC", () => {
    expect(daysInMonth("2026-02-01")).toBe(28)
    expect(firstWeekOf("2026-10-01")).toBe("2026-09-28")
    expect(firstWeekOf("2026-06-01")).toBe("2026-06-01")
    expect(bareMonth("2026-10-01")).toMatchObject({ daysInMonth: 31, firstWeekday: 3, weeks: null })
  })
})
