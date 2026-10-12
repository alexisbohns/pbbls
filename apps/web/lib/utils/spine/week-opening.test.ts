import { describe, expect, it } from "vitest"
import { isoWeekStart } from "@/lib/utils/week-roll-entries"
import { dotDays, sentenceCase, summaryWords, weekWords, weekdayLetters } from "./week-opening"

// TZ is Europe/Paris (vitest.config.ts). The brief's week: Monday 5 October 2026.
const W41 = isoWeekStart(new Date(2026, 9, 5))

describe("weekWords", () => {
  it("sets the brief's week in words", () => {
    expect(weekWords(W41, "en")).toEqual({
      number: 41,
      year: 2026,
      ordinal: "forty-first",
      title: { kind: "sameMonth", from: "fifth", to: "eleventh", month: "October" },
      previous: { number: 40 },
      next: { number: 42, ordinal: "forty-second", monday: "twelfth" },
    })
  })

  it("reads a French week as feminine and its days as cardinals", () => {
    const words = weekWords(W41, "fr")
    expect(words.ordinal).toBe("quarante et unième")
    expect(words.title).toEqual({ kind: "sameMonth", from: "cinq", to: "onze", month: "octobre" })
    expect(words.next).toEqual({ number: 42, ordinal: "quarante-deuxième", monday: "douze" })
  })

  it("names both months when the week crosses one", () => {
    // Monday 28 September to Sunday 4 October 2026.
    const words = weekWords(isoWeekStart(new Date(2026, 8, 28)), "fr")
    expect(words.title).toEqual({ kind: "crossMonth", from: "vingt-huit", fromMonth: "septembre", to: "quatre", toMonth: "octobre" })
    expect(words.next.monday).toBe("cinq")
  })

  it("takes the ISO year and wraps the week numbers at the new year", () => {
    // Monday 28 December 2026 is week 53 of 2026; the next is week 1 of 2027.
    const words = weekWords(isoWeekStart(new Date(2026, 11, 28)), "fr")
    expect(words).toMatchObject({ number: 53, year: 2026, previous: { number: 52 } })
    expect(words.next).toEqual({ number: 1, ordinal: "première", monday: "quatre" })
    // Monday 29 December 2025 already belongs to ISO 2026.
    expect(weekWords(isoWeekStart(new Date(2025, 11, 29)), "en")).toMatchObject({ number: 1, year: 2026, ordinal: "first" })
  })

  it("says premier for the first of a month in French", () => {
    // Monday 1 March 2027.
    expect(weekWords(isoWeekStart(new Date(2027, 2, 1)), "fr").title).toMatchObject({ from: "premier", to: "sept" })
  })
})

describe("summaryWords", () => {
  it("agrees in gender in French", () => {
    expect(summaryWords(1, 1, "fr")).toEqual({ pebblesWords: "un", peopleWords: "une" })
    expect(summaryWords(21, 31, "fr")).toEqual({ pebblesWords: "vingt et un", peopleWords: "trente et une" })
    expect(summaryWords(11, 5, "en")).toEqual({ pebblesWords: "eleven", peopleWords: "five" })
  })
})

describe("sentenceCase", () => {
  it("capitalises the first letter only", () => {
    expect(sentenceCase("eleven pebbles, five people.", "en")).toBe("Eleven pebbles, five people.")
    expect(sentenceCase("onze galets.", "fr")).toBe("Onze galets.")
  })
})

describe("weekdayLetters", () => {
  it("runs Monday first, lowercase", () => {
    expect(weekdayLetters(W41, "en")).toEqual(["m", "t", "w", "t", "f", "s", "s"])
    expect(weekdayLetters(W41, "fr")).toEqual(["l", "m", "m", "j", "v", "s", "d"])
  })
})

describe("dotDays", () => {
  it("names the marked days with their dates", () => {
    expect(dotDays([true, true, false, true, false, true, false], W41, "en")).toEqual({
      count: 4,
      days: "Monday 5, Tuesday 6, Thursday 8, and Saturday 10",
    })
    expect(dotDays([false, false, false, false, false, false, true], W41, "fr")).toEqual({ count: 1, days: "dimanche 11" })
  })
})
