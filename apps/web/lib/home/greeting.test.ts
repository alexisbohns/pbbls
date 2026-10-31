import { describe, expect, it } from "vitest"
import { dateline, greetingKey, greetingTitle } from "./greeting"

const PARIS = "Europe/Paris"
// Saturday 31 October 2026 in Paris is UTC+1 (winter time from 25 October).
const paris = (iso: string) => new Date(`${iso}+01:00`)

describe("greetingKey", () => {
  it("reads the bands at their edges, in the reader's zone", () => {
    expect(greetingKey(paris("2026-10-31T11:59:00"), PARIS)).toBe("morning")
    expect(greetingKey(paris("2026-10-31T12:00:00"), PARIS)).toBe("afternoon")
    expect(greetingKey(paris("2026-10-31T17:59:00"), PARIS)).toBe("afternoon")
    expect(greetingKey(paris("2026-10-31T18:00:00"), PARIS)).toBe("evening")
    expect(greetingKey(paris("2026-10-31T00:30:00"), PARIS)).toBe("morning")
  })

  it("turns Monday morning and Sunday evening into their overrides", () => {
    expect(greetingKey(paris("2026-11-02T09:00:00"), PARIS)).toBe("monday-morning")
    expect(greetingKey(paris("2026-11-02T14:00:00"), PARIS)).toBe("afternoon")
    expect(greetingKey(paris("2026-11-01T20:00:00"), PARIS)).toBe("sunday-evening")
    expect(greetingKey(paris("2026-11-01T10:00:00"), PARIS)).toBe("morning")
  })

  it("uses the zone, not the machine's clock", () => {
    const instant = new Date("2026-10-31T17:30:00Z")
    expect(greetingKey(instant, "UTC")).toBe("afternoon")
    expect(greetingKey(instant, PARIS)).toBe("evening")
  })
})

describe("greetingTitle", () => {
  it("joins the name, and drops it when blank", () => {
    expect(greetingTitle("Good evening", "Alexis")).toBe("Good evening, Alexis")
    expect(greetingTitle("Good evening", "  Alexis ")).toBe("Good evening, Alexis")
    expect(greetingTitle("Good evening", "   ")).toBe("Good evening")
    expect(greetingTitle("Good evening", null)).toBe("Good evening")
  })
})

describe("dateline", () => {
  it("writes weekday, day and month, without a comma, in both languages", () => {
    const date = paris("2026-10-31T20:00:00")
    expect(dateline(date, "en", PARIS)).toBe("Saturday 31 October")
    expect(dateline(date, "fr", PARIS)).toBe("samedi 31 octobre")
  })
})
