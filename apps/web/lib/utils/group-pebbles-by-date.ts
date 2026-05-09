import type { Pebble } from "@/lib/types"

export type DateGroup = {
  dateKey: string
  label: string
  pebbles: Pebble[]
}

export type GroupLabelFormatters = {
  /** Wraps a long-date string into "Today — {date}" for the active locale. */
  formatTodayLabel: (longDate: string) => string
  /** Wraps a long-date string into "Yesterday — {date}" for the active locale. */
  formatYesterdayLabel: (longDate: string) => string
  /** Locale-aware long date formatter (e.g. "Monday, April 5, 2026"). */
  formatLongDate: (date: Date) => string
}

function toLocalDateKey(isoString: string): string {
  const d = new Date(isoString)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, "0")
  const day = String(d.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function buildLabel(dateKey: string, args: GroupLabelFormatters): string {
  // Parse dateKey as local date (noon avoids DST edge cases)
  const date = new Date(`${dateKey}T12:00:00`)

  const today = new Date()
  const todayKey = toLocalDateKey(today.toISOString())
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  const yesterdayKey = toLocalDateKey(yesterday.toISOString())

  const formatted = args.formatLongDate(date)

  if (dateKey === todayKey) return args.formatTodayLabel(formatted)
  if (dateKey === yesterdayKey) return args.formatYesterdayLabel(formatted)
  return formatted
}

export function groupPebblesByDate(
  pebbles: Pebble[],
  formatters: GroupLabelFormatters,
): DateGroup[] {
  const sorted = [...pebbles].sort(
    (a, b) => new Date(b.happened_at).getTime() - new Date(a.happened_at).getTime(),
  )

  const groupMap = new Map<string, Pebble[]>()
  const groupOrder: string[] = []

  for (const pebble of sorted) {
    const key = toLocalDateKey(pebble.happened_at)
    const existing = groupMap.get(key)
    if (existing) {
      existing.push(pebble)
    } else {
      groupMap.set(key, [pebble])
      groupOrder.push(key)
    }
  }

  return groupOrder.map((dateKey) => ({
    dateKey,
    label: buildLabel(dateKey, formatters),
    pebbles: groupMap.get(dateKey)!,
  }))
}
