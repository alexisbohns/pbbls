import type { AchievementUnlockResult } from "@/lib/data/data-provider"
import { unlockResultsFrom } from "@/lib/data/achievement-results"
import { calendarDayFrom, instantFrom } from "@/lib/data/achievements-payload"
import type {
  Guide,
  GuideIllustration,
  HomeMonth,
  HomeWrap,
  Intensity,
  MonthWrapDay,
  MonthWrapPebble,
  MonthWrapWeek,
  Positiveness,
} from "@/lib/types"

// ---------------------------------------------------------------------------
// Home's decoders (#1384, spec D10–D14, §Data contract). Tolerant by contract:
// unknown keys are ignored, a malformed item is dropped with a warning, a
// malformed payload decodes to null or empty, nothing throws. Tested against
// the captured payloads in packages/supabase/scripts/oracles, verbatim.
// ---------------------------------------------------------------------------

type Json = Record<string, unknown>

const ILLUSTRATIONS: readonly GuideIllustration[] = [
  "pebbles",
  "stones",
  "emotions",
  "valence",
  "domains",
  "collections",
  "souls",
  "glyphs",
  "karma",
]

function isObject(raw: unknown): raw is Json {
  return typeof raw === "object" && raw !== null && !Array.isArray(raw)
}

function int(raw: unknown): number | null {
  return typeof raw === "number" && Number.isInteger(raw) ? raw : null
}

function str(raw: unknown): string | null {
  return typeof raw === "string" ? raw : null
}

function items(raw: unknown): unknown[] {
  return Array.isArray(raw) ? raw : []
}

/** The Path's valence pair, or its neutral medium when either is out of range (as CrossingPebble). */
function valence(intensity: unknown, positiveness: unknown): { intensity: Intensity; positiveness: Positiveness } {
  if ((intensity === 1 || intensity === 2 || intensity === 3) && (positiveness === -1 || positiveness === 0 || positiveness === 1)) {
    return { intensity, positiveness }
  }
  return { intensity: 2, positiveness: 0 }
}

function pebbleFrom(raw: unknown): MonthWrapPebble | null {
  if (!isObject(raw)) return null
  const momentId = str(raw.moment_id)
  if (!momentId) return null
  return { momentId, emotionId: str(raw.emotion_id), ...valence(raw.intensity, raw.polarity) }
}

function dayFrom(raw: unknown): MonthWrapDay | null {
  if (!isObject(raw)) return null
  const day = int(raw.day)
  if (day === null || day < 0) return null
  const pebbles = items(raw.pebbles).flatMap((p) => {
    const pebble = pebbleFrom(p)
    if (!pebble) console.warn("[home] month pebble dropped", p)
    return pebble ? [pebble] : []
  })
  return { day, pebbles }
}

/** A cover's days, malformed ones dropped with a warning. */
function coverDays(cover: unknown): MonthWrapDay[] {
  if (!isObject(cover)) return []
  return items(cover.days).flatMap((d) => {
    const day = dayFrom(d)
    if (!day) console.warn("[home] cover day dropped", d)
    return day ? [day] : []
  })
}

function weekFrom(raw: unknown): MonthWrapWeek | null {
  if (!isObject(raw)) return null
  const weekStart = calendarDayFrom(str(raw.week_start))
  const from = int(raw.from)
  const to = int(raw.to)
  if (!weekStart || from === null || to === null) return null
  const days = items(raw.days).map((pair): [number, number] =>
    Array.isArray(pair) ? [Math.max(0, int(pair[0]) ?? 0), int(pair[1]) ?? 0] : [0, 0],
  )
  return {
    weekStart,
    from,
    to,
    count: Math.max(0, int(raw.count) ?? 0),
    soulCount: items(raw.soul_ids).filter((id) => typeof id === "string").length,
    days,
  }
}

/** `path_month_wrap` as Home reads it (D10), or null when the payload has no month to lay out. */
export function decodeHomeMonth(raw: unknown): HomeMonth | null {
  if (!isObject(raw)) return null
  const month = calendarDayFrom(str(raw.month))
  const daysInMonth = int(raw.days_in_month)
  const firstWeekday = int(raw.first_weekday)
  if (!month || daysInMonth === null || daysInMonth < 28 || daysInMonth > 31) return null
  if (firstWeekday === null || firstWeekday < 0 || firstWeekday > 6) return null
  const weeks = Array.isArray(raw.weeks)
    ? raw.weeks.flatMap((w) => {
        const week = weekFrom(w)
        if (!week) console.warn("[home] month week dropped", w)
        return week ? [week] : []
      })
    : null
  return {
    month,
    daysInMonth,
    firstWeekday,
    count: Math.max(0, int(raw.count) ?? 0),
    days: coverDays(raw.cover),
    weeks: weeks && weeks.length > 0 ? weeks : null,
  }
}

/** A timestamp read tolerantly and kept to the whole second (standing rule). */
function wholeSecond(raw: unknown): Date | null {
  const at = instantFrom(str(raw))
  return at ? new Date(Math.floor(at.getTime() / 1000) * 1000) : null
}

function wrapFrom(raw: unknown): HomeWrap | null {
  if (!isObject(raw)) return null
  const month = calendarDayFrom(str(raw.month))
  if (!month) return null
  return {
    month,
    count: Math.max(0, int(raw.count) ?? 0),
    revealedAt: wholeSecond(raw.revealed_at),
    days: coverDays(raw.cover)
      .filter((d) => d.pebbles.length > 0)
      .map((d) => d.day),
  }
}

/** `home_wraps`' months (D13), newest first as served. */
export function decodeHomeWraps(raw: unknown): HomeWrap[] {
  if (!isObject(raw)) return []
  return items(raw.months).flatMap((m) => {
    const wrap = wrapFrom(m)
    if (!wrap) console.warn("[home] wrap dropped", m)
    return wrap ? [wrap] : []
  })
}

/** The FR field when it has words, else the EN one. */
function localized(row: Json, field: string, locale: "en" | "fr"): string | null {
  const fr = locale === "fr" ? str(row[`${field}_fr`]) : null
  return fr && fr.trim() !== "" ? fr : str(row[`${field}_en`])
}

/** The published guides in the reader's language (D14); a row without its English words is dropped. */
export function decodeGuides(rows: unknown, locale: "en" | "fr"): Guide[] {
  return items(rows).flatMap((row) => {
    if (!isObject(row)) return []
    const slug = str(row.slug)
    const title = localized(row, "title", locale)
    const summary = localized(row, "summary", locale)
    const bodyMd = localized(row, "body_md", locale)
    if (!slug || title === null || summary === null || bodyMd === null) {
      console.warn("[home] guide dropped", row)
      return []
    }
    const illustration = ILLUSTRATIONS.find((i) => i === row.illustration) ?? null
    return [{ slug, illustration, title, summary, bodyMd }]
  })
}

/** `claim_blue_pebble()`: the granted row, or null when nothing was granted (`[]` is never success). */
export function decodeClaim(rows: unknown): AchievementUnlockResult | null {
  return unlockResultsFrom(rows)[0] ?? null
}
