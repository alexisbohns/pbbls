/**
 * Home's greeting (#1384; iOS `HomeGreeting`, 2026-10-26 spec §1.1): the hour
 * band (morning < 12, afternoon 12–18, evening ≥ 18) with the Monday-morning
 * and Sunday-evening overrides, read in the reader's zone. Pure: the clock,
 * zone and locale are passed in. The words live in `home.greeting.<key>`.
 */
export type GreetingKey = "morning" | "afternoon" | "evening" | "monday-morning" | "sunday-evening"

function partsIn(date: Date, timeZone: string): { hour: number; weekday: string } {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone, hour: "numeric", hourCycle: "h23", weekday: "short" }).formatToParts(date)
  const hour = Number(parts.find((p) => p.type === "hour")?.value ?? "0")
  const weekday = parts.find((p) => p.type === "weekday")?.value ?? ""
  return { hour: hour === 24 ? 0 : hour, weekday }
}

export function greetingKey(date: Date, timeZone: string): GreetingKey {
  const { hour, weekday } = partsIn(date, timeZone)
  const band = hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening"
  if (weekday === "Mon" && band === "morning") return "monday-morning"
  if (weekday === "Sun" && band === "evening") return "sunday-evening"
  return band
}

/** "Good evening, Alexis"; the bare greeting when the name is blank. */
export function greetingTitle(greeting: string, name: string | null | undefined): string {
  const trimmed = name?.trim() ?? ""
  return trimmed === "" ? greeting : `${greeting}, ${trimmed}`
}

/**
 * "Saturday 31 October" / "samedi 31 octobre": weekday, day, month, no year and
 * no comma, day before month in both languages (iOS's `EEEE d MMMM`).
 */
export function dateline(date: Date, locale: string, timeZone: string): string {
  const parts = new Intl.DateTimeFormat(locale, { timeZone, weekday: "long", day: "numeric", month: "long" }).formatToParts(date)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? ""
  return `${part("weekday")} ${part("day")} ${part("month")}`
}
