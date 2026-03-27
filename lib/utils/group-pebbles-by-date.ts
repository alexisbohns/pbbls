import type { Pebble } from "@/lib/types"

export type DateGroup = {
  dateKey: string
  label: string
  pebbles: Pebble[]
}

const dayFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "long",
  year: "numeric",
  month: "long",
  day: "numeric",
})

function toLocalDateKey(isoString: string): string {
  const d = new Date(isoString)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, "0")
  const day = String(d.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function formatDayLabel(dateKey: string): string {
  // Parse dateKey as local date (noon avoids DST edge cases)
  const date = new Date(`${dateKey}T12:00:00`)

  const today = new Date()
  const todayKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`

  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  const yesterdayKey = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, "0")}-${String(yesterday.getDate()).padStart(2, "0")}`

  const formatted = dayFormatter.format(date)

  if (dateKey === todayKey) return `Today — ${formatted}`
  if (dateKey === yesterdayKey) return `Yesterday — ${formatted}`
  return formatted
}

export function groupPebblesByDate(pebbles: Pebble[]): DateGroup[] {
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
    label: formatDayLabel(dateKey),
    pebbles: groupMap.get(dateKey)!,
  }))
}
