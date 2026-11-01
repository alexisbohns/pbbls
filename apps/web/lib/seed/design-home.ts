import type { Guide, HomeMonth, HomeWrap, Intensity, MonthWrapDay, MonthWrapWeek, Positiveness } from "@/lib/types"

// Home's fixtures for /sandbox/design (#1384): the approved mock's October 2026
// (today Saturday 31), three wraps and three guides. Network-free, never a
// source of truth. Emotion ids are real ones the sandbox palettes know.

const JOY = "3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c"
const PEACE = "21d79e4c-9482-40d3-bf73-53adfb452e5a"
const SADNESS = "075ec40d-f27c-033d-a498-850947e4e887"
const FAMILY = { joy: JOY, peace: PEACE, sadness: SADNESS } as const

type Pip = readonly [keyof typeof FAMILY, Intensity, Positiveness]

// The mock's days (1-based date → pebbles: family, intensity, positive or not).
const OCTOBER: Record<number, readonly Pip[]> = {
  1: [["joy", 2, 1]],
  3: [["peace", 3, 1], ["joy", 1, 1]],
  6: [["sadness", 2, -1]],
  9: [["joy", 3, 1]],
  11: [["peace", 1, 1]],
  15: [["joy", 2, 1]],
  17: [["peace", 2, -1], ["joy", 1, 1]],
  18: [["joy", 3, 1]],
  22: [["sadness", 1, -1]],
  24: [["joy", 2, 1], ["peace", 2, 1], ["joy", 1, 1]],
  27: [["peace", 3, 1]],
  30: [["joy", 1, 1]],
  31: [["joy", 2, 1]],
}

function weekOf(weekStart: string, firstIndex: number): MonthWrapWeek {
  const days = Array.from({ length: 7 }, (_, k): [number, number] => {
    const pips = OCTOBER[firstIndex + k + 1] ?? []
    return [pips.length, Math.max(0, ...pips.map(([, i]) => i))]
  })
  const from = Math.max(0, firstIndex)
  const to = Math.min(30, firstIndex + 6)
  return { weekStart, from, to, count: days.reduce((n, [m]) => n + m, 0), soulCount: 0, days }
}

export const DESIGN_HOME_MONTH: HomeMonth = {
  month: "2026-10-01",
  daysInMonth: 31,
  firstWeekday: 3,
  count: Object.values(OCTOBER).reduce((n, pips) => n + pips.length, 0),
  days: Object.entries(OCTOBER).map(
    ([date, pips]): MonthWrapDay => ({
      day: Number(date) - 1,
      pebbles: pips.map(([family, intensity, positiveness], k) => ({
        momentId: `design-${date}-${k}`,
        emotionId: FAMILY[family],
        intensity,
        positiveness,
      })),
    }),
  ),
  weeks: [
    weekOf("2026-09-28", -3),
    weekOf("2026-10-05", 4),
    weekOf("2026-10-12", 11),
    weekOf("2026-10-19", 18),
    weekOf("2026-10-26", 25),
  ],
}

export const DESIGN_HOME_WRAPS: HomeWrap[] = [
  { month: "2026-10-01", count: 23, revealedAt: new Date("2026-11-01T08:00:00Z"), days: [0, 2, 5, 8, 10, 14, 16, 17, 21, 23, 26, 29, 30] },
  { month: "2026-09-01", count: 41, revealedAt: new Date("2026-10-01T08:00:00Z"), days: [0, 1, 3, 4, 6, 7, 9, 12, 13, 15, 18, 19, 20, 22, 25, 27, 28] },
  { month: "2026-08-01", count: 12, revealedAt: null, days: [2, 9, 16, 23] },
]

export const DESIGN_GUIDES: Guide[] = [
  { slug: "stones", illustration: "stones", title: "Stones", summary: "Nine shapes, by weight and light.", bodyMd: "" },
  { slug: "emotions", illustration: "emotions", title: "Emotions", summary: "Seven families, each with a colour.", bodyMd: "" },
  { slug: "souls", illustration: "souls", title: "Souls", summary: "The people in your moments.", bodyMd: "" },
]
