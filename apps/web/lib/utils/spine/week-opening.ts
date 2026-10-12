import type { Locale } from "@/lib/i18n/locales"
import { cardinalWords, dayOfMonthWords, ordinalWords } from "@/lib/i18n/number-words"
import { isoWeekNumber, isoWeekYear } from "@/lib/utils/week-roll-entries"
import { addLocalDays } from "./local-day"

/**
 * The words a week opens and closes with (#1052, spec §Type ranks of the
 * opening, §i18n): its ordinal, its title in words, and the week after it.
 * The ICU messages (`path.spine.*`) set the sentence; these are the arguments.
 * A week is feminine in French ("la quarante et unième semaine").
 */
export type WeekTitle =
  | { kind: "sameMonth"; from: string; to: string; month: string }
  | { kind: "crossMonth"; from: string; fromMonth: string; to: string; toMonth: string }

export type WeekWords = {
  number: number
  year: number
  /** "forty-first", "quarante et unième". */
  ordinal: string
  title: WeekTitle
  previous: { number: number }
  next: { number: number; ordinal: string; monday: string }
}

function monthName(date: Date, locale: Locale): string {
  return new Intl.DateTimeFormat(locale, { month: "long" }).format(date)
}

export function weekWords(weekStart: Date, locale: Locale): WeekWords {
  const sunday = addLocalDays(weekStart, 6)
  const nextMonday = addLocalDays(weekStart, 7)
  const number = isoWeekNumber(weekStart)
  const nextNumber = isoWeekNumber(nextMonday)
  const from = dayOfMonthWords(weekStart.getDate(), locale)
  const to = dayOfMonthWords(sunday.getDate(), locale)
  const title: WeekTitle =
    weekStart.getMonth() === sunday.getMonth()
      ? { kind: "sameMonth", from, to, month: monthName(sunday, locale) }
      : { kind: "crossMonth", from, fromMonth: monthName(weekStart, locale), to, toMonth: monthName(sunday, locale) }
  return {
    number,
    year: isoWeekYear(weekStart),
    ordinal: ordinalWords(number, locale, "feminine"),
    title,
    previous: { number: isoWeekNumber(addLocalDays(weekStart, -7)) },
    next: {
      number: nextNumber,
      ordinal: ordinalWords(nextNumber, locale, "feminine"),
      monday: dayOfMonthWords(nextMonday.getDate(), locale),
    },
  }
}

/**
 * The summary's numbers in words: pebbles are masculine in French ("un
 * galet"), people feminine ("une personne").
 */
export function summaryWords(pebbles: number, people: number, locale: Locale) {
  return {
    pebblesWords: cardinalWords(pebbles, locale, "masculine"),
    peopleWords: cardinalWords(people, locale, "feminine"),
  }
}

/** "eleven pebbles…" → "Eleven pebbles…": the summary opens a sentence. */
export function sentenceCase(text: string, locale: Locale): string {
  return text.charAt(0).toLocaleUpperCase(locale) + text.slice(1)
}

/** "m t w t f s s", "l m m j v s d": the persons' day header, Monday first. */
export function weekdayLetters(weekStart: Date, locale: Locale): string[] {
  const format = new Intl.DateTimeFormat(locale, { weekday: "narrow" })
  return Array.from({ length: 7 }, (_, i) => format.format(addLocalDays(weekStart, i)).toLocaleLowerCase(locale))
}

/**
 * The days a person's dots mark, as a screen reader hears them: "Monday 5,
 * Tuesday 6, and Thursday 8". Composed rather than formatted, because English
 * Intl sets `{ weekday, day }` as "5 Monday".
 */
export function dotDays(days: readonly boolean[], weekStart: Date, locale: Locale): { count: number; days: string } {
  const weekday = new Intl.DateTimeFormat(locale, { weekday: "long" })
  const named: string[] = []
  days.forEach((on, i) => {
    if (!on) return
    const date = addLocalDays(weekStart, i)
    named.push(`${weekday.format(date)} ${date.getDate()}`)
  })
  return { count: named.length, days: new Intl.ListFormat(locale).format(named) }
}
