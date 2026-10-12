import { describe, expect, it } from "vitest"
import { cardinalWords, dayOfMonthWords, ordinalWords } from "./number-words"

describe("cardinalWords", () => {
  it.each([
    [0, "zero", "zéro"],
    [1, "one", "un"],
    [12, "twelve", "douze"],
    [17, "seventeen", "dix-sept"],
    [20, "twenty", "vingt"],
    [21, "twenty-one", "vingt et un"],
    [22, "twenty-two", "vingt-deux"],
    [31, "thirty-one", "trente et un"],
    [41, "forty-one", "quarante et un"],
    [53, "fifty-three", "cinquante-trois"],
    [60, "sixty", "soixante"],
  ])("%i reads %s / %s", (n, en, fr) => {
    expect(cardinalWords(n, "en")).toBe(en)
    expect(cardinalWords(n, "fr")).toBe(fr)
  })

  it("agrees with a feminine noun in French on the ones", () => {
    expect(cardinalWords(1, "fr", "feminine")).toBe("une")
    expect(cardinalWords(21, "fr", "feminine")).toBe("vingt et une")
    expect(cardinalWords(51, "fr", "feminine")).toBe("cinquante et une")
    expect(cardinalWords(2, "fr", "feminine")).toBe("deux")
    expect(cardinalWords(1, "en", "feminine")).toBe("one")
  })

  it("falls back to digits outside 0–60", () => {
    expect(cardinalWords(61, "en")).toBe("61")
    expect(cardinalWords(-1, "fr")).toBe("-1")
    expect(cardinalWords(2.5, "en")).toBe("2.5")
  })
})

describe("ordinalWords", () => {
  it.each([
    [1, "first", "premier"],
    [2, "second", "deuxième"],
    [5, "fifth", "cinquième"],
    [9, "ninth", "neuvième"],
    [11, "eleventh", "onzième"],
    [12, "twelfth", "douzième"],
    [20, "twentieth", "vingtième"],
    [21, "twenty-first", "vingt et unième"],
    [25, "twenty-fifth", "vingt-cinquième"],
    [31, "thirty-first", "trente et unième"],
    [39, "thirty-ninth", "trente-neuvième"],
    [40, "fortieth", "quarantième"],
    [41, "forty-first", "quarante et unième"],
    [44, "forty-fourth", "quarante-quatrième"],
    [53, "fifty-third", "cinquante-troisième"],
  ])("%i reads %s / %s", (n, en, fr) => {
    expect(ordinalWords(n, "en")).toBe(en)
    expect(ordinalWords(n, "fr")).toBe(fr)
  })

  it("sets the first week as 'la première semaine'", () => {
    expect(ordinalWords(1, "fr", "feminine")).toBe("première")
    expect(ordinalWords(21, "fr", "feminine")).toBe("vingt et unième")
  })

  it("falls back to digits outside 1–53", () => {
    expect(ordinalWords(0, "en")).toBe("0")
    expect(ordinalWords(54, "fr")).toBe("54")
  })
})

describe("dayOfMonthWords", () => {
  it("reads English days as ordinals", () => {
    expect(dayOfMonthWords(1, "en")).toBe("first")
    expect(dayOfMonthWords(21, "en")).toBe("twenty-first")
    expect(dayOfMonthWords(31, "en")).toBe("thirty-first")
  })

  it("reads French days as cardinals, with 'premier' for the 1st", () => {
    expect(dayOfMonthWords(1, "fr")).toBe("premier")
    expect(dayOfMonthWords(5, "fr")).toBe("cinq")
    expect(dayOfMonthWords(21, "fr")).toBe("vingt et un")
    expect(dayOfMonthWords(31, "fr")).toBe("trente et un")
  })

  it("falls back to digits outside 1–31", () => {
    expect(dayOfMonthWords(32, "en")).toBe("32")
  })
})
