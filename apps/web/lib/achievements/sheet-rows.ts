import type { AchievementsWords } from "@/lib/achievements/copy"
import { NEUTRAL_MEDIUM } from "@/lib/achievements/seals"
import { STAGES } from "@/lib/achievements/skyline"
import type { SheetKey } from "@/lib/achievements/sheets"
import { TWENTY_FIVE_SEATS } from "@/lib/achievements/wheel"
import type {
  AchievementYear,
  AchievementsDomains,
  AchievementsEmotions,
  AchievementsExplorer,
  CountingLadder,
  CrossingPebble,
  EmotionSeat,
  FirstFiveTask,
  Intensity,
  Positiveness,
  StoneKind,
  YearMonth,
} from "@/lib/types"

// The five sheets' rows (#1308), built from the payload in its own order, as
// iOS's `AchievementsSheetRows` and Android's sheets read it: what this device
// cannot name keeps its place under a fallback ("A first", "An emotion", "A
// domain", "Other emotions"), every date is the served `on`, and a row opens
// its pebble only when the server sent a moment id. Nothing is sorted, and no
// count is set against a target.

export type RowMark =
  | { kind: "firstFive"; task: FirstFiveTask }
  | { kind: "sixFirst"; stone: StoneKind }
  | { kind: "twentyFive" }
  | { kind: "pebble"; intensity: Intensity; positiveness: Positiveness; family: string | null }
  | { kind: "domain" }
  | { kind: "stage"; index: number; fill: number; done: boolean }
  | { kind: "rosace" }
  | { kind: "ghost" }
  | { kind: "none" }

export type RowOpens = { kind: "moment"; momentId: string } | { kind: "sheet"; sheet: SheetKey }

export type RegisterRow = {
  id: string
  mark: RowMark
  name: string
  sub?: string
  /** The italic line of the next milestone ("113 to go"). */
  next?: string
  date?: string
  isNew: boolean
  /** A domain's place in the order they came ("No. 14"). */
  number?: string
  /** Small-caps name; a reached milestone's number (or an earlier year); the next's; one still ahead. */
  style: "name" | "number" | "next" | "ghostNumber"
  opens?: RowOpens
  label: string
}

export type RegisterSection = {
  id: string
  title: string
  /** The family whose name ink the title takes (the Emotions sheet). */
  family?: string | null
  note?: string
  rows: RegisterRow[]
  /** What is not yet, as dotted names, read aloud as one. */
  notYet: string[]
  /** A closing line ("then every 500", "The buyer is never named."). */
  trailing?: string
  /** A quiet line in place of rows ("The 25th pebble is still to come."). */
  quiet?: string
}

/** One gauge of the year table: the served count, an "/n" for the weeks, its fill over the served target, and `done`. */
export type YearCell = { value: string; target: string | null; fill: number | null; done: boolean; hued: boolean }

export type YearRow = { month: YearMonth; name: string; moments: YearCell; pebbles: YearCell; weeks: YearCell; label: string }

/** Names by id and slug, from this device's reference data; null for one it does not know. */
export type SheetNames = {
  emotion: (id: string | null) => string | null
  domain: (id: string | null) => string | null
  family: (slug: string | null) => string | null
}

type Words = AchievementsWords

/** A row opens its pebble only when the server sent a moment id to open. */
function opens(pebble: CrossingPebble | null): RowOpens | undefined {
  return pebble?.momentId ? { kind: "moment", momentId: pebble.momentId } : undefined
}

function optional<T>(value: T | null | undefined): T | undefined {
  return value === null ? undefined : value
}

export function explorerSections(
  explorer: AchievementsExplorer,
  emotions: AchievementsEmotions | null,
  domains: AchievementsDomains | null,
  w: Words,
): RegisterSection[] {
  const s = w.sheet
  const five: RegisterRow[] = explorer.firstFive.map((item, index) => {
    const name = s.firstFiveTitle(item.task)
    const sub = item.subject && (item.subject.id !== null || item.subject.name !== null) ? s.momentName(item.subject.name) : undefined
    const date = optional(s.day(item.on))
    const opensMoment = (item.subject?.kind === "moment" || item.subject?.kind === "pebble") && item.subject.id
    return {
      id: `five-${index}`,
      mark: item.done && item.task ? { kind: "firstFive", task: item.task } : { kind: "ghost" },
      name,
      sub,
      date,
      isNew: item.isNew,
      style: "name",
      opens: opensMoment && item.subject?.id ? { kind: "moment", momentId: item.subject.id } : undefined,
      label: s.row([name, sub, date], item.isNew),
    }
  })
  const six: RegisterRow[] = explorer.sixFirsts.map((first, index) => {
    const name = s.kind(first.kind)
    const sub = first.pebble ? s.momentName(first.pebble.name) : undefined
    const date = optional(s.day(first.on))
    return {
      id: `six-${index}`,
      mark: first.done && first.kind ? { kind: "sixFirst", stone: first.kind } : { kind: "ghost" },
      name,
      sub,
      date,
      isNew: first.isNew,
      style: "name",
      opens: opens(first.pebble),
      label: s.row([name, sub, date], first.isNew),
    }
  })
  const sections: RegisterSection[] = [
    { id: "first-five", title: s.text("firstFive"), note: s.ofWords(explorer.firstFive.filter((f) => f.done).length, explorer.firstFive.length), rows: five, notYet: [] },
    { id: "six-firsts", title: s.text("sixFirsts"), note: s.ofWords(explorer.sixFirsts.filter((f) => f.done).length, explorer.sixFirsts.length), rows: six, notYet: [] },
  ]
  const twentyFive = explorer.twentyFive
  if (twentyFive) {
    const section: RegisterSection = { id: "twenty-five", title: s.text("twentyFive"), note: s.ofWords(twentyFive.count, TWENTY_FIVE_SEATS), rows: [], notYet: [] }
    if (twentyFive.done) {
      const name = s.text("twentyFifth")
      const sub = twentyFive.pebble ? s.momentName(twentyFive.pebble.name) : undefined
      const date = optional(s.day(twentyFive.on))
      section.rows = [
        { id: "the-25th", mark: { kind: "twentyFive" }, name, sub, date, isNew: twentyFive.isNew, style: "name", opens: opens(twentyFive.pebble), label: s.row([name, sub, date], twentyFive.isNew) },
      ]
    } else {
      section.quiet = s.text("twentyFiveWaiting")
    }
    sections.push(section)
  }
  const outer: RegisterRow[] = []
  if (domains) {
    const name = w.cardTitle("domains")
    const count = s.countOf(domains.count, domains.of)
    outer.push({ id: "open-domains", mark: { kind: "domain" }, name, sub: count, isNew: false, style: "name", opens: { kind: "sheet", sheet: { card: "domains" } }, label: s.row([name, count]) })
  }
  if (emotions) {
    const name = w.cardTitle("emotions")
    const count = s.countOf(emotions.count, emotions.of)
    outer.push({ id: "open-emotions", mark: { kind: "rosace" }, name, sub: count, isNew: false, style: "name", opens: { kind: "sheet", sheet: { card: "emotions" } }, label: s.row([name, count]) })
  }
  if (outer.length > 0) sections.push({ id: "domains-emotions", title: s.text("domainsEmotions"), rows: outer, notYet: [] })
  return sections
}

/** The server's families in its order: each with its met emotions by the day they were met, then the rest as names. */
export function emotionSections(emotions: AchievementsEmotions, names: SheetNames, w: Words): RegisterSection[] {
  const s = w.sheet
  const seat = (index: number): EmotionSeat | undefined => emotions.seats.find((x) => x.seat === index)
  const name = (x: EmotionSeat) => names.emotion(x.emotionId) ?? s.text("unknownEmotion")
  return emotions.families.map((family, index) => {
    const rows: RegisterRow[] = family.met.flatMap((i) => {
      const x = seat(i)
      if (!x) return []
      const title = name(x)
      const sub = x.pebble ? s.momentName(x.pebble.name) : undefined
      const date = optional(s.day(x.on))
      const valence = x.pebble ?? NEUTRAL_MEDIUM
      return [
        {
          id: `seat-${x.seat}`,
          mark: { kind: "pebble", intensity: valence.intensity, positiveness: valence.positiveness, family: x.family },
          name: title,
          sub,
          date,
          isNew: x.isNew,
          style: "name",
          opens: opens(x.pebble),
          label: s.row([title, sub, date], x.isNew),
        },
      ]
    })
    return {
      id: `family-${index}`,
      title: names.family(family.family) ?? s.text("unknownFamily"),
      family: family.family,
      note: s.countOf(family.met.length, family.met.length + family.notYet.length),
      rows,
      notYet: family.notYet.flatMap((i) => {
        const x = seat(i)
        return x ? [name(x)] : []
      }),
    }
  })
}

/** The register newest first as served, numbered by arrival; then not yet. */
export function domainSections(domains: AchievementsDomains, names: SheetNames, w: Words): RegisterSection[] {
  const s = w.sheet
  const name = (id: string | null) => names.domain(id) ?? s.text("unknownDomain")
  const total = domains.entered.length
  const rows: RegisterRow[] = domains.entered.map((entry, index) => {
    const number = s.registerNumber(total - index)
    const title = name(entry.domainId)
    const sub = entry.pebble ? s.momentName(entry.pebble.name) : undefined
    const date = optional(s.day(entry.on))
    return { id: `entry-${index}`, mark: { kind: "domain" }, name: title, sub, date, isNew: entry.isNew, number, style: "name", opens: opens(entry.pebble), label: s.row([number, title, sub, date], entry.isNew) }
  })
  const sections: RegisterSection[] = [{ id: "entered", title: s.text("entered"), note: s.text("newestFirst"), rows, notYet: [] }]
  if (domains.notYet.length > 0) sections.push({ id: "not-yet", title: s.text("notYet"), rows: [], notYet: domains.notYet.map(name) })
  return sections
}

/** The Milestones note: how many of the drawn stages the server lists as reached, in words. */
export function milestonesNote(stack: CountingLadder, w: Words): string {
  const steps = stack.ladder.slice(0, STAGES.length)
  const listed = new Set(stack.levels.map((l) => l.threshold))
  return w.sheet.ofWords(steps.filter((t) => listed.has(t)).length, steps.length)
}

/**
 * A stack's milestones: the levels the server lists (dated, opening their
 * pebble), its next (what is left, never a date), the ladder's other steps,
 * then the ladder's tail.
 */
export function ladderSection(stack: CountingLadder, id: string, title: string, note: string | undefined, w: Words, trailing?: string): RegisterSection {
  const s = w.sheet
  const listed = new Set(stack.levels.map((l) => l.threshold))
  const stage = (threshold: number) => {
    const at = stack.ladder.indexOf(threshold)
    return at >= 0 ? at : Math.max(stack.ladder.length - 1, 0)
  }
  const rows: RegisterRow[] = stack.levels.map((level) => {
    const name = w.number(level.threshold)
    const sub = level.pebble ? s.momentName(level.pebble.name) : undefined
    const date = optional(s.day(level.on))
    return { id: `level-${level.threshold}`, mark: { kind: "stage", index: stage(level.threshold), fill: 1, done: true }, name, sub, date, isNew: level.isNew, style: "number", opens: opens(level.pebble), label: s.row([name, sub, date], level.isNew) }
  })
  const next = stack.next
  if (next) {
    const name = w.number(next.threshold)
    const left = s.toGo(next.left)
    rows.push({ id: `next-${next.threshold}`, mark: { kind: "stage", index: stage(next.threshold), fill: next.progress, done: false }, name, next: left, isNew: false, style: "next", label: s.row([name, left]) })
  }
  stack.ladder.forEach((step, index) => {
    if (listed.has(step) || step === next?.threshold) return
    const name = w.number(step)
    rows.push({ id: `ahead-${step}`, mark: { kind: "stage", index, fill: 0, done: false }, name, isNew: false, style: "ghostNumber", label: name })
  })
  return { id, title, note, rows, notYet: [], trailing: trailing ?? (stack.thenEvery !== null ? s.thenEvery(stack.thenEvery) : undefined) }
}

/**
 * The table: the months the server lists, in its order (newest first). Each
 * count is the served one, uncapped; a gauge fills by it over the served
 * target (capped only in the drawing), and its done style is `done`.
 */
export function yearRows(year: AchievementYear, w: Words): YearRow[] {
  const s = w.sheet
  const fill = (count: number, target: number | null) => (target !== null && target > 0 ? Math.min(Math.max(count / target, 0), 1) : null)
  return year.listed.flatMap((n) => {
    const month = year.months.find((m) => m.month === n)
    if (!month) return []
    const weeks: YearCell =
      month.fullWeeks > 0
        ? { value: w.number(month.weeksFilled), target: `/${w.number(month.fullWeeks)}`, fill: fill(month.weeksFilled, month.fullWeeks), done: month.done.weeks, hued: false }
        : { value: "—", target: null, fill: null, done: month.done.weeks, hued: false }
    return [
      {
        month,
        name: s.monthHeading(month.month),
        moments: { value: w.number(month.momentDays), target: null, fill: fill(month.momentDays, year.daysTarget), done: month.done.moments, hued: false },
        pebbles: { value: w.number(month.pebbleDays), target: null, fill: fill(month.pebbleDays, year.daysTarget), done: month.done.pebbles, hued: true },
        weeks,
        label: s.monthRow(month),
      },
    ]
  })
}

/** The years before this one the payload carries, newest first as served, each opening its own sheet; from the earliest, the first-year line. */
export function earlierYears(year: AchievementYear, years: readonly AchievementYear[], w: Words): RegisterSection {
  const others = years.filter((y) => y.year < year.year)
  return {
    id: "earlier",
    title: w.sheet.text("earlierYears"),
    rows: others.map((other) => ({
      id: `year-${other.year}`,
      mark: { kind: "none" },
      name: String(other.year),
      isNew: false,
      style: "number",
      opens: { kind: "sheet", sheet: { card: "year", year: other.year } },
      label: String(other.year),
    })),
    notYet: [],
    quiet: others.length === 0 ? w.sheet.firstYear(year.year) : undefined,
  }
}
