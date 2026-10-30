import type {
  AchievementCard,
  AchievementCompletion,
  AchievementYear,
  AchievementsDomains,
  AchievementsEmotions,
  AchievementsExplorer,
  AchievementsPage,
  CeremonyStack,
  CountingLadder,
  CrossingPebble,
  DomainCoverage,
  DomainEntry,
  EmotionFamilyGroup,
  EmotionSeat,
  ExplorerRing,
  FirstFiveItem,
  FirstFiveSubject,
  FirstFiveSubjectKind,
  FirstFiveTask,
  Intensity,
  LadderLevel,
  LadderNext,
  MonthDone,
  MonthState,
  Positiveness,
  ShelfItem,
  SixFirst,
  StoneKind,
  TwentyFive,
  YearMonth,
  YearNeeds,
  YearNextStep,
} from "@/lib/types"

// ---------------------------------------------------------------------------
// `get_achievements(p_tz)` decoding (#1308, spec 2026-10-29 §3.3, §4), the
// web's twin of iOS's AchievementsPayload + AchievementsMapping. Pure. It
// renames, types and clamps (a level to 0…2, a progress to 0…1: the drawing's
// bounds); it never counts, compares, sorts or picks.
//
// The shared tolerance rules (spec §4 "Resilience"):
// - an absent or null section is null, and its card is hidden;
// - a section present but mistyped (a string where a number belongs, an array
//   where an object does) is dropped whole and its key reported in `skipped`,
//   so one bad card never blanks the page (iOS drops it the same way);
// - a null element inside an array or a map is dropped on its own;
// - an unknown enum keeps its item with that field null (a ghost seat, a row
//   left out), never dropping it, so seats never shift; an unknown month state
//   reads as quiet; a ring, seen card or family the build does not know is
//   kept as served where it is a string (families) and left out where it is
//   a flag (rings, seen cards);
// - a missing id, day or instant is null; a missing count is 0, a missing flag
//   false, a missing `reachable` true (iOS's defaults);
// - a year without exactly twelve months, January to December, is dropped,
//   and `listed` values outside 1…12 are left out.
// ---------------------------------------------------------------------------

type Json = Record<string, unknown>

/** A present value of the wrong JSON type: its section is dropped. */
class Mistyped extends Error {
  constructor(key: string, raw: unknown) {
    super(`${key}: unexpected ${Array.isArray(raw) ? "array" : typeof raw}`)
    this.name = "Mistyped"
  }
}

const CARDS: readonly AchievementCard[] = ["explorer", "emotions", "domains", "pebbles", "year"]
const STONE_KINDS: readonly StoneKind[] = ["small", "medium", "large", "lowlight", "neutral", "highlight"]
const RINGS: readonly ExplorerRing[] = ["first_five", "six_firsts", "twenty_five", "domains", "emotions"]
const TASKS: readonly FirstFiveTask[] = ["first-moment", "first-pebble", "first-collection", "first-soul", "first-glyph"]
const SUBJECT_KINDS: readonly FirstFiveSubjectKind[] = ["moment", "pebble", "collection", "soul", "glyph"]
const COVERAGES: readonly DomainCoverage[] = ["none", "few", "third", "half", "most", "all"]
const STATES: readonly MonthState[] = ["upcoming", "quiet", "kept"]
const NEEDS: readonly YearNeeds[] = ["moments", "pebbles"]

function isObject(raw: unknown): raw is Json {
  return typeof raw === "object" && raw !== null && !Array.isArray(raw)
}

/** A required object: anything else is mistyped. */
function object(raw: unknown, key: string): Json {
  if (!isObject(raw)) throw new Mistyped(key, raw)
  return raw
}

/** An optional object: absent or null is null. */
function optObject(o: Json, key: string): Json | null {
  const raw = o[key]
  return raw === undefined || raw === null ? null : object(raw, key)
}

/** An array with its null elements dropped one by one; absent or null is empty. */
function list(o: Json, key: string): unknown[] {
  const raw = o[key]
  if (raw === undefined || raw === null) return []
  if (!Array.isArray(raw)) throw new Mistyped(key, raw)
  return raw.filter((item) => item !== null)
}

function optInt(o: Json, key: string): number | null {
  const raw = o[key]
  if (raw === undefined || raw === null) return null
  if (typeof raw !== "number" || !Number.isInteger(raw)) throw new Mistyped(key, raw)
  return raw
}

function int(o: Json, key: string, fallback: number): number {
  return optInt(o, key) ?? fallback
}

function ints(o: Json, key: string): number[] {
  return list(o, key).map((raw) => {
    if (typeof raw !== "number" || !Number.isInteger(raw)) throw new Mistyped(key, raw)
    return raw
  })
}

/** A finite number at any scale (`0.250`, `0`, `1`), clamped to [0, 1]. */
function unit(o: Json, key: string): number {
  const raw = o[key]
  if (raw === undefined || raw === null) return 0
  if (typeof raw !== "number" || !Number.isFinite(raw)) throw new Mistyped(key, raw)
  return Math.min(1, Math.max(0, raw))
}

function bool(o: Json, key: string, fallback: boolean): boolean {
  const raw = o[key]
  if (raw === undefined || raw === null) return fallback
  if (typeof raw !== "boolean") throw new Mistyped(key, raw)
  return raw
}

function optString(o: Json, key: string): string | null {
  const raw = o[key]
  if (raw === undefined || raw === null) return null
  if (typeof raw !== "string") throw new Mistyped(key, raw)
  return raw
}

function strings(o: Json, key: string): string[] {
  return list(o, key).map((raw) => {
    if (typeof raw !== "string") throw new Mistyped(key, raw)
    return raw
  })
}

/** A served string in `values`, or null for one this build does not know. */
function oneOf<T extends string>(o: Json, key: string, values: readonly T[]): T | null {
  const raw = optString(o, key)
  return values.find((v) => v === raw) ?? null
}

const DAY = /^(\d{4})-(\d{2})-(\d{2})$/

/** A `YYYY-MM-DD` local day (D17), or null when absent, malformed or impossible (`2026-02-31`). */
export function calendarDayFrom(raw: string | null): string | null {
  const m = raw === null ? null : DAY.exec(raw.trim())
  if (!m) return null
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])]
  const date = new Date(Date.UTC(year, month - 1, day))
  const real = date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
  return real ? `${m[1]}-${m[2]}-${m[3]}` : null
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** An id that opens something (a moment, a first-five subject), or null when malformed, as iOS's `UUID(uuidString:)`. */
function uuid(o: Json, key: string): string | null {
  const raw = optString(o, key)
  return raw !== null && UUID.test(raw) ? raw : null
}

const INSTANT = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(?::\d{2})?)(?:\.(\d+))?(Z|[+-]\d{2}(?::?\d{2})?)$/i

/**
 * A timestamp crossing the boundary, read tolerantly (standing rule): whole
 * seconds or any fraction, `Z`, `+00:00`, `+00` or `+0000`, and Postgres's
 * space-separated spelling. Null when unreadable or zoneless (a zoneless
 * instant would be read in the browser's zone).
 */
export function instantFrom(raw: string | null): Date | null {
  const m = raw === null ? null : INSTANT.exec(raw.trim())
  if (!m) return null
  const fraction = m[3] ? `.${m[3].slice(0, 3).padEnd(3, "0")}` : ""
  const zone = m[4].toUpperCase() === "Z" ? "Z" : m[4].length === 3 ? `${m[4]}:00` : m[4].length === 5 ? `${m[4].slice(0, 3)}:${m[4].slice(3)}` : m[4]
  const ms = Date.parse(`${m[1]}T${m[2]}${fraction}${zone}`)
  return Number.isNaN(ms) ? null : new Date(ms)
}

function day(o: Json, key: string): string | null {
  return calendarDayFrom(optString(o, key))
}

function instant(o: Json, key: string): Date | null {
  return instantFrom(optString(o, key))
}

/** The Path's valence pair, or its neutral medium when either is out of range (iOS, Android). */
function valence(intensity: number, positiveness: number): { intensity: Intensity; positiveness: Positiveness } {
  if ((intensity === 1 || intensity === 2 || intensity === 3) && (positiveness === -1 || positiveness === 0 || positiveness === 1)) {
    return { intensity, positiveness }
  }
  return { intensity: 2, positiveness: 0 }
}

/** `{pebble}` (D15). Kept whatever it lacks: without a moment id it draws but does not open. */
function pebbleFrom(p: Json | null): CrossingPebble | null {
  if (!p) return null
  return {
    momentId: uuid(p, "moment_id"),
    name: optString(p, "name"),
    happenedAt: instant(p, "happened_at"),
    emotionId: optString(p, "emotion_id"),
    ...valence(int(p, "intensity", 2), int(p, "positiveness", 0)),
  }
}

function completionFrom(c: Json | null, stack: CeremonyStack): AchievementCompletion | null {
  if (!c) return null
  return {
    stack,
    completedAt: instant(c, "completed_at"),
    offeredFrom: instant(c, "offered_from"),
    ready: bool(c, "ready", false),
    pulledAt: instant(c, "pulled_at"),
    pebble: pebbleFrom(optObject(c, "pebble")),
  }
}

function subjectFrom(s: Json | null): FirstFiveSubject | null {
  if (!s) return null
  return { kind: oneOf(s, "kind", SUBJECT_KINDS), id: uuid(s, "id"), name: optString(s, "name") }
}

function firstFiveFrom(raw: unknown): FirstFiveItem {
  const f = object(raw, "first_five")
  return {
    task: oneOf(f, "slug", TASKS),
    done: bool(f, "done", false),
    on: day(f, "on"),
    subject: subjectFrom(optObject(f, "subject")),
    isNew: bool(f, "new", false),
  }
}

function sixFirstFrom(raw: unknown): SixFirst {
  const s = object(raw, "six_firsts")
  return {
    kind: oneOf(s, "kind", STONE_KINDS),
    done: bool(s, "done", false),
    on: day(s, "on"),
    pebble: pebbleFrom(optObject(s, "pebble")),
    isNew: bool(s, "new", false),
  }
}

function twentyFiveFrom(t: Json | null): TwentyFive | null {
  if (!t) return null
  return {
    count: int(t, "count", 0),
    done: bool(t, "done", false),
    on: day(t, "on"),
    pebble: pebbleFrom(optObject(t, "pebble")),
    isNew: bool(t, "new", false),
  }
}

function explorerFrom(e: Json): AchievementsExplorer {
  return {
    firstPebble: pebbleFrom(optObject(e, "first_pebble")),
    firstFive: list(e, "first_five").map(firstFiveFrom),
    sixFirsts: list(e, "six_firsts").map(sixFirstFrom),
    twentyFive: twentyFiveFrom(optObject(e, "twenty_five")),
    // A set of flags, not positions: a ring this build does not know has nothing to draw.
    ringsDone: strings(e, "rings_done").flatMap((r) => RINGS.filter((ring) => ring === r)),
    completion: completionFrom(optObject(e, "completion"), "six_firsts"),
  }
}

function seatFrom(raw: unknown): EmotionSeat {
  const s = object(raw, "seats")
  return {
    seat: optInt(s, "seat"),
    emotionId: optString(s, "emotion_id"),
    family: optString(s, "family"),
    met: bool(s, "met", false),
    on: day(s, "on"),
    pebble: pebbleFrom(optObject(s, "pebble")),
    isNew: bool(s, "new", false),
  }
}

function familyGroupFrom(raw: unknown): EmotionFamilyGroup {
  const f = object(raw, "families")
  return { family: optString(f, "family"), met: ints(f, "met"), notYet: ints(f, "not_yet") }
}

function emotionsFrom(e: Json): AchievementsEmotions {
  return {
    count: int(e, "count", 0),
    of: int(e, "of", 0),
    seats: list(e, "seats").map(seatFrom),
    families: list(e, "families").map(familyGroupFrom),
    since: day(e, "since"),
    completion: completionFrom(optObject(e, "completion"), "emotions"),
  }
}

function domainEntryFrom(raw: unknown): DomainEntry {
  const d = object(raw, "entered")
  return {
    domainId: optString(d, "domain_id"),
    on: day(d, "on"),
    pebble: pebbleFrom(optObject(d, "pebble")),
    isNew: bool(d, "new", false),
  }
}

function domainsFrom(d: Json): AchievementsDomains {
  return {
    count: int(d, "count", 0),
    of: int(d, "of", 0),
    coverage: oneOf(d, "coverage", COVERAGES),
    entered: list(d, "entered").map(domainEntryFrom),
    notYet: strings(d, "not_yet"),
    completion: completionFrom(optObject(d, "completion"), "domains"),
  }
}

function levelFrom(raw: unknown): LadderLevel {
  const l = object(raw, "levels")
  return {
    threshold: int(l, "threshold", 0),
    on: day(l, "on"),
    pebble: pebbleFrom(optObject(l, "pebble")),
    isNew: bool(l, "new", false),
  }
}

function nextFrom(n: Json | null): LadderNext | null {
  if (!n) return null
  return { threshold: int(n, "threshold", 0), left: int(n, "left", 0), progress: unit(n, "progress") }
}

function ladderFrom(l: Json): CountingLadder {
  return {
    count: int(l, "count", 0),
    levels: list(l, "levels").map(levelFrom),
    next: nextFrom(optObject(l, "next")),
    ladder: ints(l, "ladder"),
    thenEvery: optInt(l, "then_every"),
    since: day(l, "since"),
  }
}

function doneFrom(d: Json | null): MonthDone {
  return d
    ? { moments: bool(d, "moments", false), pebbles: bool(d, "pebbles", false), weeks: bool(d, "weeks", false) }
    : { moments: false, pebbles: false, weeks: false }
}

function monthFrom(raw: unknown): YearMonth {
  const m = object(raw, "months")
  const level = int(m, "level", 0)
  return {
    month: int(m, "month", 0),
    // An unknown state draws as a quiet month (spec §4).
    state: oneOf(m, "state", STATES) ?? "quiet",
    family: optString(m, "family"),
    momentDays: int(m, "moment_days", 0),
    pebbleDays: int(m, "pebble_days", 0),
    fullWeeks: int(m, "full_weeks", 0),
    weeksFilled: int(m, "weeks_filled", 0),
    level: level >= 2 ? 2 : level === 1 ? 1 : 0,
    progress: unit(m, "progress"),
    done: doneFrom(optObject(m, "done")),
  }
}

function nextStepFrom(n: Json | null): YearNextStep | null {
  if (!n) return null
  return {
    month: int(n, "month", 0),
    needs: oneOf(n, "needs", NEEDS),
    daysLeft: int(n, "days_left", 0),
    reachable: bool(n, "reachable", true),
  }
}

/**
 * January to December, indexed by each month's own number, or null when the
 * served list is not exactly one entry per month: the disc cannot be drawn.
 */
function twelveMonths(served: YearMonth[]): YearMonth[] | null {
  if (served.length !== 12) return null
  const byNumber = new Map(served.map((m) => [m.month, m]))
  const months = Array.from({ length: 12 }, (_, i) => byNumber.get(i + 1))
  return months.every((m) => m !== undefined) ? months.filter((m) => m !== undefined) : null
}

function yearFrom(raw: unknown): AchievementYear[] {
  const y = object(raw, "years")
  const months = twelveMonths(list(y, "months").map(monthFrom))
  if (!months) return []
  return [
    {
      year: int(y, "year", 0),
      current: bool(y, "current", false),
      daysTarget: optInt(y, "days_target"),
      months,
      listed: ints(y, "listed").filter((m) => m >= 1 && m <= 12),
      nextStep: nextStepFrom(optObject(y, "next_step")),
    },
  ]
}

function seenFrom(s: Json): Partial<Record<AchievementCard, Date>> {
  const seen: Partial<Record<AchievementCard, Date>> = {}
  for (const card of CARDS) {
    const at = instant(s, card)
    if (at) seen[card] = at
  }
  // A non-string time for a card this build does not know still mistypes the map, as on iOS.
  for (const [key, raw] of Object.entries(s)) {
    if (raw !== null && typeof raw !== "string") throw new Mistyped(key, raw)
  }
  return seen
}

function shelfItemFrom(raw: unknown): ShelfItem {
  const s = object(raw, "shelf")
  return {
    slug: optString(s, "slug") ?? "",
    family: optString(s, "family") ?? "",
    threshold: optInt(s, "threshold"),
    unlockedOn: day(s, "unlocked_on"),
  }
}

export type AchievementsDecode = {
  page: AchievementsPage
  /** The keys present but mistyped, dropped whole: the caller logs them. */
  skipped: string[]
}

/** The whole page, or null when the payload is not an object at all. */
export function decodeAchievementsPage(raw: unknown): AchievementsDecode | null {
  if (!isObject(raw)) return null
  const p = raw
  const skipped: string[] = []
  function section<T>(key: string, decode: (value: unknown) => T): T | null {
    const value = p[key]
    if (value === undefined || value === null) return null
    try {
      return decode(value)
    } catch (err) {
      if (!(err instanceof Mistyped)) throw err
      skipped.push(key)
      return null
    }
  }
  const page: AchievementsPage = {
    since: section("since", (v) => calendarDayFrom(optString({ since: v }, "since"))),
    daysKept: section("days_kept", (v) => optInt({ days_kept: v }, "days_kept")),
    explorer: section("explorer", (v) => explorerFrom(object(v, "explorer"))),
    emotions: section("emotions", (v) => emotionsFrom(object(v, "emotions"))),
    domains: section("domains", (v) => domainsFrom(object(v, "domains"))),
    pebbles: section("pebbles", (v) => ladderFrom(object(v, "pebbles"))),
    glyphsCarved: section("glyphs_carved", (v) => ladderFrom(object(v, "glyphs_carved"))),
    glyphSales: section("glyph_sales", (v) => ladderFrom(object(v, "glyph_sales"))),
    years: section("years", (v) => list({ years: v }, "years").flatMap(yearFrom)) ?? [],
    seen: section("seen", (v) => seenFrom(object(v, "seen"))) ?? {},
    shelf: section("shelf", (v) => list({ shelf: v }, "shelf").map(shelfItemFrom)) ?? [],
    shelfCount: section("shelf_count", (v) => optInt({ shelf_count: v }, "shelf_count")),
  }
  return { page, skipped }
}

/**
 * `pull_ceremony(p_stack, p_tz)` rows (spec §3.4): the first pull returns one
 * row with the karma it paid (0 while the catalog row is unpriced or
 * inactive); a repeated pull returns none, which is never a success (null
 * here). A body that is not an array of rows throws: the caller cannot know
 * what happened and re-reads the page.
 */
export function ceremonyPullFrom(raw: unknown): { karmaGranted: number } | null {
  if (!Array.isArray(raw)) throw new Error(`pull_ceremony returned ${raw === null ? "null" : typeof raw}, not rows`)
  const row = raw.find((item) => item !== null)
  if (row === undefined) return null
  return { karmaGranted: int(object(row, "pull_ceremony"), "karma_granted", 0) }
}
