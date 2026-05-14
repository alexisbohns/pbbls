import {
  buildWeekRollEntries,
  formatWeekRange,
  isoWeekKey,
  isoWeekNumber,
  isoWeekStart,
  weekIndex,
} from "../lib/utils/week-roll-entries"
import type { Pebble } from "../lib/types"

let failures = 0
function eq<T>(name: string, actual: T, expected: T) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected)
  if (!ok) {
    failures += 1
    console.error(`✗ ${name}`)
    console.error(`  expected: ${JSON.stringify(expected)}`)
    console.error(`  actual:   ${JSON.stringify(actual)}`)
  } else {
    console.log(`✓ ${name}`)
  }
}

function pebble(id: string, isoDateLocal: string): Pebble {
  // Caller passes "YYYY-MM-DDTHH:mm:ss" without a tz so Date treats it as local.
  return {
    id, name: id, description: "",
    happened_at: new Date(isoDateLocal).toISOString(),
    intensity: 2, positiveness: 0, visibility: "private",
    emotion_id: "serenity", soul_ids: [], domain_ids: [],
    mark_id: undefined, collection_ids: [], snaps: [], instants: [], cards: [],
  } as unknown as Pebble
}

// --- isoWeekStart / isoWeekKey / isoWeekNumber ----------------------------

eq(
  "isoWeekStart: Wed maps back to Mon",
  isoWeekStart(new Date(2026, 4, 13)).toDateString(),
  new Date(2026, 4, 11).toDateString(),
)

eq(
  "isoWeekStart: Sunday maps back to previous Monday",
  isoWeekStart(new Date(2026, 4, 17)).toDateString(),
  new Date(2026, 4, 11).toDateString(),
)

eq(
  "isoWeekKey: late December rolls to next ISO year",
  isoWeekKey(new Date(2025, 11, 29)),
  "2026-W01",
)

eq(
  "isoWeekNumber: 2026-05-14 is week 20",
  isoWeekNumber(new Date(2026, 4, 14)),
  20,
)

// --- buildWeekRollEntries -------------------------------------------------

const today = new Date(2026, 4, 14)   // 2026-05-14 Thursday → ISO W20

let entries = buildWeekRollEntries([], today)
eq("empty pebbles → [currentWeek, nextWeek]", entries.map((e) => e.weekStartIso), [
  "2026-W20",
  "2026-W21",
])

entries = buildWeekRollEntries([pebble("p1", "2026-05-14T09:00:00")], today)
eq(
  "single current-week pebble → [W20 (1), W21 (0)]",
  entries.map((e) => `${e.weekStartIso}:${e.pebbles.length}`),
  ["2026-W20:1", "2026-W21:0"],
)

entries = buildWeekRollEntries([pebble("p1", "2026-04-23T09:00:00")], today)
eq(
  "single past pebble in W17 → [W17, W20, W21]",
  entries.map((e) => e.weekStartIso),
  ["2026-W17", "2026-W20", "2026-W21"],
)

entries = buildWeekRollEntries(
  [
    pebble("a", "2026-04-22T10:00:00"),   // Wed W17
    pebble("b", "2026-04-20T09:00:00"),   // Mon W17 — earliest
    pebble("c", "2026-04-24T11:00:00"),   // Fri W17 — latest
  ],
  today,
)
eq(
  "past week sorts ascending (oldest-first)",
  entries.find((e) => e.weekStartIso === "2026-W17")!.pebbles.map((p) => p.id),
  ["b", "a", "c"],
)

entries = buildWeekRollEntries(
  [
    pebble("a", "2026-05-11T09:00:00"),   // Mon W20
    pebble("b", "2026-05-14T09:00:00"),   // Thu W20
    pebble("c", "2026-05-12T09:00:00"),   // Tue W20
  ],
  today,
)
eq(
  "current week sorts descending (newest-first)",
  entries.find((e) => e.weekStartIso === "2026-W20")!.pebbles.map((p) => p.id),
  ["b", "c", "a"],
)

// Monday 00:00 local belongs to the new week.
eq(
  "boundary Mon 00:00 belongs to new week",
  isoWeekKey(new Date(2026, 4, 11, 0, 0, 0)),
  "2026-W20",
)

// DST forward (US: 2026-03-08 Sunday). Mon 2026-03-09 starts W11.
eq(
  "DST forward: 2026-03-09 Mon is W11",
  isoWeekKey(new Date(2026, 2, 9)),
  "2026-W11",
)

// --- weekIndex ------------------------------------------------------------

entries = buildWeekRollEntries([pebble("p", "2026-04-23T09:00:00")], today)
eq(
  "weekIndex finds focused entry",
  weekIndex(entries, isoWeekStart(today)),
  1, // [W17, W20, W21] → W20 is index 1
)

eq(
  "weekIndex returns -1 for missing",
  weekIndex(entries, new Date(2030, 0, 1)),
  -1,
)

// --- formatWeekRange ------------------------------------------------------

eq(
  "formatWeekRange same year omits year suffix",
  formatWeekRange(new Date(2026, 4, 11), new Date(2026, 4, 14), "en-US"),
  "May 11 – May 17",
)

eq(
  "formatWeekRange cross-year appends year",
  formatWeekRange(new Date(2025, 0, 6), new Date(2026, 4, 14), "en-US"),
  "Jan 6 – Jan 12 · 2025",
)

eq(
  "formatWeekRange fr-FR uses French short month",
  formatWeekRange(new Date(2026, 4, 11), new Date(2026, 4, 14), "fr-FR"),
  "11 mai – 17 mai",
)

// -------------------------------------------------------------------------

if (failures > 0) {
  console.error(`\n${failures} failure(s)`)
  process.exit(1)
} else {
  console.log(`\nAll assertions passed.`)
}
