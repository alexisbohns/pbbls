import type { Locale } from "@/lib/i18n/locales"

/**
 * Numbers spelled out, for the Spine's opening ("the forty-first week", "The
 * fifth to the eleventh of October", "& twelve others"). ICU plurals cover the
 * nouns but not spelled-out numbers, so these are tables.
 *
 * Range: cardinals 0–60, ordinals 1–53 (week numbers and days of the month).
 * Outside it, every function falls back to digits rather than inventing words.
 *
 * French gender only shows on 1 in this range ("un/une", "premier/première",
 * and the "et un/et une" of 21, 31, 41, 51), so a `gender` argument covers it.
 * English ignores it.
 */
export type Gender = "masculine" | "feminine"

const CARDINAL_MAX = 60
const ORDINAL_MAX = 53

const EN_UNITS = [
  "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
  "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
  "seventeen", "eighteen", "nineteen",
]
const EN_TENS = ["", "", "twenty", "thirty", "forty", "fifty", "sixty"]

const EN_ORDINAL_UNITS = [
  "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh",
  "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth",
  "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth",
]
const EN_ORDINAL_TENS = ["", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth"]

const FR_UNITS = [
  "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
  "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept",
  "dix-huit", "dix-neuf",
]
const FR_TENS = ["", "", "vingt", "trente", "quarante", "cinquante", "soixante"]

function inRange(n: number, max: number, min = 0): boolean {
  return Number.isInteger(n) && n >= min && n <= max
}

function enCardinal(n: number): string {
  if (n < 20) return EN_UNITS[n]
  const tens = Math.floor(n / 10)
  const unit = n % 10
  return unit === 0 ? EN_TENS[tens] : `${EN_TENS[tens]}-${EN_UNITS[unit]}`
}

function enOrdinal(n: number): string {
  if (n < 20) return EN_ORDINAL_UNITS[n]
  const tens = Math.floor(n / 10)
  const unit = n % 10
  return unit === 0 ? EN_ORDINAL_TENS[tens] : `${EN_TENS[tens]}-${EN_ORDINAL_UNITS[unit]}`
}

function frCardinal(n: number, gender: Gender): string {
  const un = gender === "feminine" ? "une" : "un"
  if (n === 1) return un
  if (n < 20) return FR_UNITS[n]
  const tens = Math.floor(n / 10)
  const unit = n % 10
  if (unit === 0) return FR_TENS[tens]
  if (unit === 1) return `${FR_TENS[tens]} et ${un}`
  return `${FR_TENS[tens]}-${FR_UNITS[unit]}`
}

/** The ordinal is the cardinal plus "-ième", with the few spelling changes. */
function frOrdinal(n: number, gender: Gender): string {
  if (n === 1) return gender === "feminine" ? "première" : "premier"
  const cardinal = frCardinal(n, "masculine")
  if (cardinal.endsWith("cinq")) return `${cardinal}uième`
  if (cardinal.endsWith("neuf")) return `${cardinal.slice(0, -1)}vième`
  if (cardinal.endsWith("e")) return `${cardinal.slice(0, -1)}ième`
  return `${cardinal}ième`
}

/** "twelve" / "douze". Feminine matters in French for 1, 21, 31, 41, 51. */
export function cardinalWords(n: number, locale: Locale, gender: Gender = "masculine"): string {
  if (!inRange(n, CARDINAL_MAX)) return String(n)
  return locale === "fr" ? frCardinal(n, gender) : enCardinal(n)
}

/** "forty-first" / "quarante et unième". Weeks are feminine in French. */
export function ordinalWords(n: number, locale: Locale, gender: Gender = "masculine"): string {
  if (!inRange(n, ORDINAL_MAX, 1)) return String(n)
  return locale === "fr" ? frOrdinal(n, gender) : enOrdinal(n)
}

/**
 * A day of the month as a title sets it: English reads ordinals ("the fifth"),
 * French reads cardinals with "premier" for the 1st ("du premier au sept").
 */
export function dayOfMonthWords(day: number, locale: Locale): string {
  if (!inRange(day, 31, 1)) return String(day)
  if (locale === "fr") return day === 1 ? "premier" : frCardinal(day, "masculine")
  return enOrdinal(day)
}
