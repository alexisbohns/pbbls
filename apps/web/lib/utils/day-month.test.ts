import { describe, expect, it } from "vitest"
import { dayMonth } from "./day-month"

describe("dayMonth", () => {
  it("puts the day first in English and French", () => {
    expect(dayMonth("2026-10-31T09:00:00Z", "en")).toBe("31 October")
    expect(dayMonth("2026-10-31T09:00:00Z", "fr")).toBe("31 octobre")
    expect(dayMonth("2026-10-29T09:00:00Z", "en", "short")).toBe("29 Oct")
    expect(dayMonth("2026-10-29T09:00:00Z", "fr", "short")).toBe("29 oct.")
  })
})
