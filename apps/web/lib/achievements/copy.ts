import { cardinalWords, ordinalWords } from "@/lib/i18n/number-words"
import type { Locale } from "@/lib/i18n/locales"
import { sentenceCase } from "@/lib/utils/spine/week-opening"
import { TWENTY_FIVE_SEATS } from "@/lib/achievements/wheel"
import type {
  AchievementYear,
  AchievementsDomains,
  AchievementsEmotions,
  AchievementsExplorer,
  CeremonyStack,
  CountingLadder,
  DomainCoverage,
  ExplorerRing,
  FirstFiveTask,
  StoneKind,
  YearMonth,
  YearNextStep,
} from "@/lib/types"

// The achievements page's words (#1308, spec §4 "Localise", §5): the one place
// the payload's numbers, tiers and steps become sentences, over the catalogs'
// `achievements.page.*` keys, word for word with iOS's `AchievementsCopy` and
// `AchievementsA11y`. Choosing a sentence by how many stages stand is wording,
// not counting: the stages are the ones the server lists. Dates are formatted,
// never computed: a served `YYYY-MM-DD` is read at noon UTC and printed in UTC.

/** A translator rooted at `achievements.page`. */
export type Translate = (key: string, values?: Record<string, string | number>) => string

const RINGS: readonly ExplorerRing[] = ["first_five", "six_firsts", "twenty_five", "domains", "emotions"]

function served(day: string): Date {
  return new Date(`${day}T12:00:00Z`)
}

/** A first-five task's catalog family, whose title names its row (the shelf's names). */
const FIRST_FIVE_FAMILY: Record<FirstFiveTask, string> = {
  "first-moment": "first_moment",
  "first-pebble": "first_pebble",
  "first-collection": "first_collection",
  "first-soul": "first_soul",
  "first-glyph": "first_glyph",
}

/**
 * `t` is rooted at `achievements.page`; `root` at the catalog's top, for the
 * app's own words the sheets reuse as iOS does (untitled, the valences, the
 * first five's titles).
 */
export function achievementsWords(t: Translate, locale: Locale, root: Translate = t) {
  // Built once per language (the words are memoised on it), not per call.
  const numbers = new Intl.NumberFormat(locale)
  const months = new Intl.DateTimeFormat(locale, { month: "long", timeZone: "UTC" })
  const lists = new Intl.ListFormat(locale, { style: "long", type: "conjunction" })
  const fullDates = new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "UTC" })
  const dayMonths = new Intl.DateTimeFormat(locale, { day: "numeric", month: "long", timeZone: "UTC" })
  const number = (n: number) => numbers.format(n)
  const monthInSentence = (month: number) => (month >= 1 && month <= 12 ? months.format(Date.UTC(2026, month - 1, 15, 12)) : String(month))
  const list = (items: string[]) => lists.format(items)

  /** "Sunday, March 1, 2026", "dimanche 1 mars 2026". */
  const fullDate = (day: string) => fullDates.format(served(day))
  /** "March 2", "2 mars". */
  const dayMonth = (day: string) => dayMonths.format(served(day))

  /** " 3 new." after a label; nothing when nothing is new. */
  const fresh = (count: number) => (count > 0 ? ` ${t("a11y.new", { count })}` : "")

  const words = {
    number,
    monthInSentence,

    /** "since Sunday, March 1, 2026". */
    since: (day: string) => t("header.since", { date: fullDate(day) }),
    /** "days", or "day" on the first one. */
    daysUnit: (days: number) => t("header.days", { count: days }),
    /** "since March 2": a card's served `since`. */
    sinceHint: (day: string) => t("hint.since", { date: dayMonth(day) }),
    cardTitle: (card: "explorer" | "emotions" | "domains" | "pebbles" | "year" | "glyphsCarved" | "glyphSales") => t(`card.${card}`),
    explorerHint: () => t("hint.explorer"),
    domainsHint: () => t("hint.domains"),
    /** "in progress" on the current year; nothing on a past one. */
    yearHint: (year: AchievementYear) => (year.current ? t("hint.inProgress") : null),
    /** What the coverage tier means (D11); nothing for a tier this build does not know. */
    coverage: (tier: DomainCoverage | null) => (tier ? t(`coverage.${tier}`) : null),
    ready: () => t("ready"),

    /** The skyline in words: how many of its stages the server lists as reached, and the one rising toward `next`. */
    stones: (stack: CountingLadder) => {
      if (!stack.next) return t("pebbles.top")
      const target = number(stack.next.threshold)
      const listed = new Set(stack.levels.map((l) => l.threshold))
      const standing = stack.ladder.filter((step) => listed.has(step)).length
      if (standing >= stack.ladder.length) return t("pebbles.all", { target })
      const rising = ordinalWords(standing + 1, locale, "feminine")
      if (standing === 0) return t("pebbles.none", { target })
      if (standing === 1) return t("pebbles.one", { rising, target })
      return sentenceCase(t("pebbles.some", { words: cardinalWords(standing, locale, "feminine"), rising, target }), locale)
    },

    /** "137 pebbles. Five stones stand; the sixth rises toward 250." */
    pebblesLine: (stack: CountingLadder) => `${t("pebbles.kept", { count: stack.count })} ${words.stones(stack)}`,

    /**
     * "Twelve more days with a moment, one in each full week, bring November
     * to the inner disc." Out of reach this month, for whichever reason, the
     * copy names neither. Nothing for a need this build does not know.
     */
    nextStep: (step: YearNextStep) => {
      if (!step.needs) return null
      const month = monthInSentence(step.month)
      const key = `year.${step.needs}`
      if (!step.reachable) return sentenceCase(t(`${key}.unreachable`, { month }), locale)
      if (step.daysLeft <= 0) return t(`${key}.keep`, { month })
      if (step.daysLeft === 1) return t(`${key}.one`, { month })
      return sentenceCase(t(`${key}.some`, { words: cardinalWords(step.daysLeft, locale), month }), locale)
    },

    error: {
      title: () => t("error.title"),
      body: () => t("error.body"),
      retry: () => t("error.retry"),
    },

    /** A pull that failed (Android's `achievements_ceremony_error`). */
    ceremonyError: () => t("ceremonyError"),

    /** "137 pebbles kept.": the Pebbles sheet's lead. */
    pebblesLead: (stack: CountingLadder) => t("pebbles.keptLead", { count: stack.count }),

    ceremonyTitle: (stack: CeremonyStack) => t(`ceremony.${stack}.title`),
    ceremonyBody: (stack: CeremonyStack) => t(`ceremony.${stack}.body`),

    /** The sheets' words (#1308), word for word iOS's `AchievementsCopy+Sheets` and Android's. */
    sheet: {
      text: (key: string, values?: Record<string, string | number>) => t(`sheet.${key}`, values),
      /** A first-five task's title, as the shelf names it; one this build does not know reads "A first". */
      firstFiveTitle: (task: FirstFiveTask | null) =>
        task ? root(`achievements.family.${FIRST_FIVE_FAMILY[task]}.title`) : t("sheet.unknownFirst"),
      /** The app's words for the six firsts; one this build does not know reads "A first". */
      kind: (kind: StoneKind | null) => {
        if (kind === "small" || kind === "medium" || kind === "large") return t(`kind.${kind}`)
        if (kind) return root(`pebble.positiveness.${kind}`)
        return t("sheet.unknownFirst")
      },
      /** "five of five": a ring's section note, in words. */
      ofWords: (count: number, total: number) => t("sheet.of", { count: cardinalWords(count, locale), total: cardinalWords(total, locale) }),
      /** "14 of 18": a count beside its whole, in figures. */
      countOf: (count: number, total: number) => t("sheet.of", { count: number(count), total: number(total) }),
      /** "No. 03": a domain's place in the order they came. */
      registerNumber: (place: number) => t("sheet.number", { number: String(place).padStart(2, "0") }),
      toGo: (left: number) => t("sheet.toGo", { left: number(left) }),
      thenEvery: (step: number) => t("sheet.thenEvery", { step: number(step) }),
      firstYear: (year: number) => t("sheet.firstYear", { year: String(year) }),
      /** A moment's name in a row, or the app's untitled word for one the server sends without a name. */
      momentName: (name: string | null) => name ?? root("moment.untitled"),
      /** "Apr 21", "21 avr.": a served local day in the register. */
      day: (day: string | null) =>
        day ? new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", timeZone: "UTC" }).format(served(day)) : null,
      /** "November", "Novembre": a month heading a row. */
      monthHeading: (month: number) => sentenceCase(monthInSentence(month), locale),
      /** A row read aloud: its parts, then "new since you last looked" when it is. */
      row: (parts: (string | null | undefined)[], isNew = false) =>
        [...parts.filter((p): p is string => typeof p === "string" && p.trim() !== ""), ...(isNew ? [t("a11y.newSince")] : [])].join(", "),
      /** The quiet names read as one: "Not yet: Anxious, Bitter". */
      notYet: (names: string[]) => t("a11y.notYet", { names: names.join(", ") }),
      /** A table row read aloud: the served counts as they are, each marked "reached" where `done` says so. */
      monthRow: (month: YearMonth) => {
        const reached = (text: string, done: boolean) => (done ? t("a11y.reached", { value: text }) : text)
        const weeks =
          month.fullWeeks > 0 ? t("a11y.weeksFilled", { count: number(month.weeksFilled), total: number(month.fullWeeks) }) : t("a11y.noFullWeek")
        return t("a11y.month", {
          month: sentenceCase(monthInSentence(month.month), locale),
          moments: reached(number(month.momentDays), month.done.moments),
          pebbles: reached(number(month.pebbleDays), month.done.pebbles),
          weeks: reached(weeks, month.done.weeks),
        })
      },
    },

    a11y: {
      /** "249 days, since Sunday, March 1, 2026". */
      header: (daysKept: number, since: string | null) => {
        const days = `${number(daysKept)} ${words.daysUnit(daysKept)}`
        return since ? t("a11y.header", { days, since: words.since(since) }) : days
      },

      /**
       * "Explorer: the first five, done; …; emotions, 23 of 38. 3 new." A ring
       * is done when `rings_done` says so; an absent section is left out, never
       * read as 0. "N new" adds the `new` flags of everything the wheel draws.
       */
      explorer: (explorer: AchievementsExplorer, emotions: AchievementsEmotions | null, domains: AchievementsDomains | null) => {
        const ring = (r: ExplorerRing, count: number, of: number) => {
          const name = t(`a11y.ring.${r}`)
          return explorer.ringsDone.includes(r) ? t("a11y.ringDone", { name }) : t("a11y.ringPart", { name, count: number(count), of: number(of) })
        }
        const counts: Partial<Record<ExplorerRing, [number, number]>> = {
          first_five: [explorer.firstFive.filter((f) => f.done).length, explorer.firstFive.length],
          six_firsts: [explorer.sixFirsts.filter((s) => s.done).length, explorer.sixFirsts.length],
          ...(explorer.twentyFive ? { twenty_five: [explorer.twentyFive.count, TWENTY_FIVE_SEATS] } : {}),
          ...(domains ? { domains: [domains.count, domains.of] } : {}),
          ...(emotions ? { emotions: [emotions.count, emotions.of] } : {}),
        }
        const rings = RINGS.flatMap((r) => {
          const c = counts[r]
          return c ? [ring(r, c[0], c[1])] : []
        })
        const isNew =
          explorer.firstFive.filter((f) => f.isNew).length +
          explorer.sixFirsts.filter((s) => s.isNew).length +
          (explorer.twentyFive?.isNew ? 1 : 0) +
          (domains?.entered.filter((e) => e.isNew).length ?? 0) +
          (emotions?.seats.filter((s) => s.isNew).length ?? 0)
        return t("a11y.explorer", { rings: rings.join(t("a11y.join")) }) + fresh(isNew)
      },

      /** "Emotions, 23 of 38. 2 new." */
      emotions: (emotions: AchievementsEmotions) =>
        t("a11y.emotions", { count: number(emotions.count), of: number(emotions.of) }) + fresh(emotions.seats.filter((s) => s.isNew).length),

      /** "Domains, 14 of 18. <what the coverage means> 1 new." */
      domains: (domains: AchievementsDomains) => {
        const meaning = words.coverage(domains.coverage)
        const label = t("a11y.domains", { count: number(domains.count), of: number(domains.of) })
        return (meaning ? `${label} ${meaning}` : label) + fresh(domains.entered.filter((e) => e.isNew).length)
      },

      /** "Pebbles. 137 pebbles. Five stones stand; … Glyphs carved: 12. Glyph sales: 3." */
      pebbles: (pebbles: CountingLadder, carved: CountingLadder | null, sales: CountingLadder | null) => {
        const parts = [t("a11y.pebbles", { line: words.pebblesLine(pebbles) })]
        if (carved) parts.push(t("a11y.glyphsCarved", { count: number(carved.count) }))
        if (sales) parts.push(t("a11y.glyphSales", { count: number(sales.count) }))
        const isNew = [pebbles, carved, sales].reduce((n, s) => n + (s ? s.levels.filter((l) => l.isNew).length : 0), 0)
        return parts.join(" ") + fresh(isNew)
      },

      /** The disc alone, as the server's lists: "The year 2026. On the rim: August. … On their way: November." */
      disc: (year: AchievementYear) => {
        const begun = year.months.filter((m) => m.state !== "upcoming")
        const names = (months: YearMonth[]) => (months.length > 0 ? list(months.map((m) => monthInSentence(m.month))) : t("a11y.none"))
        return t("a11y.year", {
          year: String(year.year),
          rim: names(begun.filter((m) => m.level === 2)),
          inner: names(begun.filter((m) => m.level === 1)),
          way: names(begun.filter((m) => m.level === 0 && m.progress > 0)),
        })
      },

      /** The disc, then the next step when there is one to say. */
      year: (year: AchievementYear) => {
        const step = year.nextStep ? words.nextStep(year.nextStep) : null
        return step ? `${words.a11y.disc(year)} ${step}` : words.a11y.disc(year)
      },

      /** "The six firsts: the ceremony is ready." */
      ready: (stack: CeremonyStack) => t("a11y.ready", { title: words.ceremonyTitle(stack) }),
    },
  }
  return words
}

export type AchievementsWords = ReturnType<typeof achievementsWords>
