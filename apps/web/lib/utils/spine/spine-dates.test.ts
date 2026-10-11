import { describe, expect, it } from "vitest"
import { dayRange, longDay, shortWeekday } from "./spine-dates"

// TZ is Europe/Paris (vitest.config.ts).
const MONDAY = new Date(2026, 9, 5)
const SUNDAY = new Date(2026, 9, 11)

describe("spine dates", () => {
  it("shortens the weekday, lowercase and without FR's period", () => {
    expect(shortWeekday(MONDAY, "en")).toBe("mon")
    expect(shortWeekday(MONDAY, "fr")).toBe("lun")
  })

  // The app formats with the bare locale, as iOS uses Locale.current: "en"
  // writes the month first. Never hardcode a region here.
  it("writes a day out in full", () => {
    expect(longDay(MONDAY, "en")).toBe("Monday, October 5")
    expect(longDay(MONDAY, "fr")).toBe("lundi 5 octobre")
  })

  it("spans a week", () => {
    // Intl pads the dash with thin spaces.
    expect(dayRange(MONDAY, SUNDAY, "en")).toMatch(/^Monday, October 5\s–\sSunday, October 11$/)
    expect(dayRange(MONDAY, SUNDAY, "fr")).toMatch(/^lundi 5\s–\sdimanche 11 octobre$/)
  })
})
