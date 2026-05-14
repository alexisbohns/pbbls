# Web Week Path View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the iOS Path week-paginated experience to `apps/web/app/path`, replacing today's continuous timeline with a horizontal weeks roll of Rive cairns, a date-range header with chevrons, a per-week paged list of pebbles, the relocated `QuickPebbleEditor` as a bottom overlay, and a new `PathBottomBar` (glyph + bounce + karma).

**Architecture:** Pure presentational refactor of `/path` in `apps/web`. No schema, RPC, or `DataProvider` changes — `usePebbles()` already returns `intensity` and pre-signed snap URLs. State (`focusedWeekStart`) lives in a new `PathScreen` component. Week paging uses Framer Motion `x` transform with lazy ±1 page render and touch-only drag. Weeks roll re-centers via native `scrollIntoView`. The existing `QuickPebbleEditor` becomes a controlled component whose collapsed state is the "New pebble" bar in the bottom dock and whose expanded state floats as an overlay above the path.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript strict, Tailwind 4, shadcn/ui, Framer Motion 12, next-intl, `@rive-app/react-canvas`, `date-fns`.

**Spec:** [`docs/superpowers/specs/2026-05-14-web-week-path-view-design.md`](../specs/2026-05-14-web-week-path-view-design.md)

---

## Pre-flight

### Task 0: Create branch

**Files:** N/A (git only).

- [ ] **Step 1: Create and check out the feature branch**

Run:
```bash
git checkout -b feat/421-weeks-roll-web
```

Expected: `Switched to a new branch 'feat/421-weeks-roll-web'`.

- [ ] **Step 2: Verify the branch**

Run: `git status`
Expected: `On branch feat/421-weeks-roll-web` with a clean working tree.

---

## Phase 1 — Pure helpers and hook

The pure helpers and the `useMediaQuery` hook have no UI dependencies. Build them first with inline test assertions so later tasks can rely on them.

### Task 1: `lib/utils/week-roll-entries.ts` — pure helpers + assertions

**Files:**
- Create: `apps/web/lib/utils/week-roll-entries.ts`
- Create: `apps/web/scripts/test-week-roll.ts`
- Modify: `apps/web/package.json` (add `test:week-roll` script)

- [ ] **Step 1: Write the helpers**

Create `apps/web/lib/utils/week-roll-entries.ts`:

```ts
import { addWeeks } from "date-fns"
import type { Pebble } from "@/lib/types"

export type WeekRollEntry = {
  weekStart: Date           // ISO Monday 00:00 local
  weekStartIso: string      // "YYYY-Www" stable key (e.g. "2026-W19")
  isoWeek: number           // 1..53
  pebbles: Pebble[]
}

/** ISO 8601 week start (Monday 00:00 local) for the given date. */
export function isoWeekStart(d: Date): Date {
  const out = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const day = out.getDay()                   // 0 = Sun, 1 = Mon, ..., 6 = Sat
  const delta = day === 0 ? -6 : 1 - day     // shift back to Monday
  out.setDate(out.getDate() + delta)
  out.setHours(0, 0, 0, 0)
  return out
}

/** ISO 8601 week number (1..53). Mirrors `Date.weekOfYear` from Swift. */
export function isoWeekNumber(d: Date): number {
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  // ISO weeks: Thursday in current week decides the year.
  const dayNr = (target.getDay() + 6) % 7    // Mon = 0, Sun = 6
  target.setDate(target.getDate() - dayNr + 3)
  const firstThursday = new Date(target.getFullYear(), 0, 4)
  const firstThursdayDayNr = (firstThursday.getDay() + 6) % 7
  firstThursday.setDate(firstThursday.getDate() - firstThursdayDayNr + 3)
  const diff = target.getTime() - firstThursday.getTime()
  return 1 + Math.round(diff / (7 * 24 * 60 * 60 * 1000))
}

/** ISO 8601 year-for-week (e.g. 2025-12-30 is in ISO year 2026). */
export function isoWeekYear(d: Date): number {
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const dayNr = (target.getDay() + 6) % 7
  target.setDate(target.getDate() - dayNr + 3)
  return target.getFullYear()
}

/** Stable string key for a week, e.g. "2026-W19". */
export function isoWeekKey(d: Date): string {
  const year = isoWeekYear(d)
  const week = isoWeekNumber(d)
  return `${year}-W${String(week).padStart(2, "0")}`
}

/** Index of `weekStart` in `entries`. Returns -1 if not found. */
export function weekIndex(entries: WeekRollEntry[], weekStart: Date): number {
  const key = isoWeekKey(weekStart)
  return entries.findIndex((e) => e.weekStartIso === key)
}

/**
 * Build the weeks roll: union of weeks that contain pebbles with the
 * current and next week, sorted ascending by `weekStart`. Past weeks
 * have their pebbles sorted oldest-first; current and future weeks
 * sort newest-first. Pivot is strict `weekStart < currentWeekStart`.
 */
export function buildWeekRollEntries(
  pebbles: Pebble[],
  today: Date,
): WeekRollEntry[] {
  const currentStart = isoWeekStart(today)
  const nextStart = isoWeekStart(addWeeks(today, 1))

  const bucket = new Map<string, { weekStart: Date; pebbles: Pebble[] }>()
  const seed = (date: Date) => {
    const key = isoWeekKey(date)
    if (!bucket.has(key)) {
      bucket.set(key, { weekStart: isoWeekStart(date), pebbles: [] })
    }
    return bucket.get(key)!
  }

  seed(currentStart)
  seed(nextStart)

  for (const p of pebbles) {
    const happened = new Date(p.happened_at)
    seed(happened).pebbles.push(p)
  }

  const entries: WeekRollEntry[] = []
  for (const { weekStart, pebbles: bucketPebbles } of bucket.values()) {
    const isPast = weekStart.getTime() < currentStart.getTime()
    const sorted = [...bucketPebbles].sort((a, b) => {
      const aT = new Date(a.happened_at).getTime()
      const bT = new Date(b.happened_at).getTime()
      return isPast ? aT - bT : bT - aT
    })
    entries.push({
      weekStart,
      weekStartIso: isoWeekKey(weekStart),
      isoWeek: isoWeekNumber(weekStart),
      pebbles: sorted,
    })
  }

  entries.sort((a, b) => a.weekStart.getTime() - b.weekStart.getTime())
  return entries
}

/**
 * Format a week's date range, locale-aware. Appends ` · YYYY` when the
 * focused-week year differs from today's calendar year.
 */
export function formatWeekRange(
  weekStart: Date,
  today: Date,
  locale: string,
): string {
  const end = new Date(weekStart)
  end.setDate(end.getDate() + 6)
  const fmt = new Intl.DateTimeFormat(locale, { month: "short", day: "numeric" })
  const range = `${fmt.format(weekStart)} – ${fmt.format(end)}`
  return weekStart.getFullYear() === today.getFullYear()
    ? range
    : `${range} · ${weekStart.getFullYear()}`
}
```

- [ ] **Step 2: Write the inline assertion script**

Create `apps/web/scripts/test-week-roll.ts`:

```ts
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
```

- [ ] **Step 3: Add the `test:week-roll` npm script**

Modify `apps/web/package.json` — under `"scripts"` add:

```json
"test:week-roll": "tsx scripts/test-week-roll.ts"
```

(Insert after `"lint"` to keep neighborly order.)

- [ ] **Step 4: Run the script and verify all assertions pass**

Run: `npm run test:week-roll --workspace=apps/web`

Expected: every line prefixed with `✓` and `All assertions passed.` on the final line; exit 0.

If `tsx` is not installed at the workspace root, install it as a dev dependency: `npm install -D -w apps/web tsx`, then re-run.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/utils/week-roll-entries.ts apps/web/scripts/test-week-roll.ts apps/web/package.json
git commit -m "feat(ui): add week-roll entry builder and formatters"
```

If `package-lock.json` changed because of `tsx`, add it to the same commit.

---

### Task 2: `lib/hooks/useMediaQuery.ts`

**Files:**
- Create: `apps/web/lib/hooks/useMediaQuery.ts`

- [ ] **Step 1: Write the hook**

Create `apps/web/lib/hooks/useMediaQuery.ts`:

```ts
"use client"

import { useEffect, useState } from "react"

/**
 * SSR-safe wrapper around `window.matchMedia`. Returns `false` on the
 * server pass and during the first client render, then hydrates on
 * mount and subscribes to changes.
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(false)

  useEffect(() => {
    const mql = window.matchMedia(query)
    setMatches(mql.matches)
    const onChange = (event: MediaQueryListEvent) => setMatches(event.matches)
    mql.addEventListener("change", onChange)
    return () => mql.removeEventListener("change", onChange)
  }, [query])

  return matches
}
```

- [ ] **Step 2: Lint the new file**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 lib/hooks/useMediaQuery.ts`

Expected: no errors or warnings.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/hooks/useMediaQuery.ts
git commit -m "feat(ui): add SSR-safe useMediaQuery hook"
```

---

## Phase 2 — Visual components (leaves first)

Build the leaf components in isolation so later tasks compose them. No `/path` route changes yet; the existing page still renders.

### Task 3: `WeekRollCairn`

**Files:**
- Create: `apps/web/components/path/WeekRollCairn.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { useEffect } from "react"
import { useRive } from "@rive-app/react-canvas"
import { useReducedMotion } from "framer-motion"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"

type WeekRollCairnProps = {
  entry: WeekRollEntry
  isFocused: boolean
  opacity: number
  onClick: () => void
}

export function WeekRollCairn({ entry, isFocused, opacity, onClick }: WeekRollCairnProps) {
  const t = useTranslations("path")
  const prefersReducedMotion = useReducedMotion()
  const { rive, RiveComponent } = useRive({
    src: "/animations/pbbls-cairn.riv",
    autoplay: false,
  })

  useEffect(() => {
    if (!rive) return
    if (isFocused && !prefersReducedMotion) rive.play()
    else rive.stop()
  }, [rive, isFocused, prefersReducedMotion])

  return (
    <li>
      <button
        type="button"
        data-week={entry.weekStartIso}
        onClick={onClick}
        aria-pressed={isFocused}
        aria-label={t("weekHeader.weekAria", {
          iso: entry.isoWeek,
          count: entry.pebbles.length,
        })}
        className="flex w-[72px] flex-col items-center gap-1 transition-opacity scroll-mx-[50%]"
        style={{ opacity }}
      >
        <div className="size-14"><RiveComponent /></div>
        <span
          className={cn(
            "font-heading text-xs font-semibold",
            isFocused ? "text-primary" : "text-muted-foreground",
          )}
        >
          {entry.isoWeek}
        </span>
      </button>
    </li>
  )
}
```

- [ ] **Step 2: Lint the file**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/WeekRollCairn.tsx`

Expected: no errors. If translation keys flag a warning, ignore — they're added in Task 16.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/WeekRollCairn.tsx
git commit -m "feat(ui): add WeekRollCairn component"
```

---

### Task 4: `WeekRoll`

**Files:**
- Create: `apps/web/components/path/WeekRoll.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { useEffect, useRef } from "react"
import { useTranslations } from "next-intl"
import { WeekRollCairn } from "@/components/path/WeekRollCairn"
import {
  isoWeekKey,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type WeekRollProps = {
  entries: WeekRollEntry[]
  focused: Date
  onFocus: (weekStart: Date) => void
}

function opacityForDistance(d: number): number {
  if (d === 0) return 1
  if (d === 1) return 0.5
  if (d === 2) return 0.25
  return 0
}

export function WeekRoll({ entries, focused, onFocus }: WeekRollProps) {
  const t = useTranslations("path")
  const focusedIso = isoWeekKey(focused)
  const focusedIndex = entries.findIndex((e) => e.weekStartIso === focusedIso)
  const scrollRef = useRef<HTMLDivElement>(null)
  const isFirstRunRef = useRef(true)

  useEffect(() => {
    const container = scrollRef.current
    if (!container) return
    const el = container.querySelector<HTMLElement>(`[data-week="${focusedIso}"]`)
    if (!el) return
    el.scrollIntoView({
      behavior: isFirstRunRef.current ? "instant" : "smooth",
      inline: "center",
      block: "nearest",
    })
    isFirstRunRef.current = false
  }, [focusedIso])

  return (
    <div
      ref={scrollRef}
      aria-label={t("weekRoll.label")}
      className="overflow-x-auto [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]"
    >
      <ul className="flex items-center gap-3 px-[50%]">
        {entries.map((entry, i) => (
          <WeekRollCairn
            key={entry.weekStartIso}
            entry={entry}
            isFocused={i === focusedIndex}
            opacity={opacityForDistance(Math.abs(i - focusedIndex))}
            onClick={() => onFocus(entry.weekStart)}
          />
        ))}
      </ul>
    </div>
  )
}
```

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/WeekRoll.tsx`

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/WeekRoll.tsx
git commit -m "feat(ui): add WeekRoll component"
```

---

### Task 5: `WeekHeader`

**Files:**
- Create: `apps/web/components/path/WeekHeader.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { ChevronLeft, ChevronRight } from "lucide-react"
import { useLocale, useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import {
  formatWeekRange,
  weekIndex,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type WeekHeaderProps = {
  entries: WeekRollEntry[]
  focused: Date
  today: Date
  onPrev: () => void
  onNext: () => void
}

export function WeekHeader({ entries, focused, today, onPrev, onNext }: WeekHeaderProps) {
  const locale = useLocale()
  const t = useTranslations("path")
  const idx = weekIndex(entries, focused)
  const atStart = idx <= 0
  const atEnd = idx >= entries.length - 1

  return (
    <div className="flex h-10 items-center justify-between rounded-full border border-muted px-2 dark:border-foreground">
      <Button
        variant="ghost"
        size="icon"
        disabled={atStart}
        onClick={onPrev}
        aria-label={t("weekHeader.previous")}
      >
        <ChevronLeft className="size-5 text-primary" />
      </Button>
      <span className="font-heading text-[17px] font-semibold uppercase tracking-[0.02em] text-muted-foreground dark:text-muted">
        {formatWeekRange(focused, today, locale)}
      </span>
      <Button
        variant="ghost"
        size="icon"
        disabled={atEnd}
        onClick={onNext}
        aria-label={t("weekHeader.next")}
      >
        <ChevronRight className="size-5 text-primary" />
      </Button>
    </div>
  )
}
```

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/WeekHeader.tsx`

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/WeekHeader.tsx
git commit -m "feat(ui): add WeekHeader component"
```

---

### Task 6: `PathPebbleRow` — add photo support

**Files:**
- Modify: `apps/web/components/path/PathPebbleRow.tsx`

The existing row already handles the glyph and intensity-driven render. Add:
- `positionIndex` prop (number).
- Photo rendering when `pebble.snaps[0]?.instants?.original` is set.
- Index-parity rotation: even = `-7°`, odd = `+4°`.
- Data-driven row height: 60 / 68 / 71 / 100 per the spec.
- Pure helpers `rotation` and `rowHeight` exported from the file.

- [ ] **Step 1: Add the helper exports at the top of the file (after imports, before the `PathPebbleRow` component)**

```ts
export function rotation(positionIndex: number): number {
  return positionIndex % 2 === 0 ? -7 : 4
}

export function rowHeight(
  intensity: 1 | 2 | 3,
  hasPhoto: boolean,
  positionIndex: number,
): number {
  if (intensity === 3) return 100
  if (!hasPhoto) return 60
  return positionIndex % 2 === 0 ? 71 : 68
}
```

- [ ] **Step 2: Extend the props type and the component signature**

Replace:

```ts
type PathPebbleRowProps = {
  pebble: Pebble
  mark?: Mark
  onSelect?: (id: string) => void
}
```

with:

```ts
type PathPebbleRowProps = {
  pebble: Pebble
  mark?: Mark
  positionIndex: number
  onSelect?: (id: string) => void
}
```

Update the function signature to destructure `positionIndex`.

- [ ] **Step 3: Compute the photo URL and apply the row height**

At the top of the function body (after `const palette = ...`):

```ts
const photoUrl = pebble.snaps[0]?.instants?.original
const hasPhoto = Boolean(photoUrl)
const heightPx = rowHeight(pebble.intensity, hasPhoto, positionIndex)
```

Add `style={{ height: heightPx, ...rowStyle }}` (merged) on the outer `<button>` element. If `rowStyle` is already passed via `style`, fold `height: heightPx` into the same object literal — do not duplicate the style attribute.

- [ ] **Step 4: Render the photo when present**

Inside the row's content (after the existing text column, before the closing `</button>`), insert:

```tsx
{photoUrl && (
  /* eslint-disable-next-line @next/next/no-img-element -- signed Storage URL, next/image not applicable */
  <img
    src={photoUrl}
    alt=""
    loading="lazy"
    className="size-16 shrink-0 rounded-lg object-cover ring-4 ring-background shadow-md"
    style={{ transform: `rotate(${rotation(positionIndex)}deg)` }}
  />
)}
```

The photo sits to the right of the text column. The whole row remains one `<button>` — the photo has no separate click handler.

- [ ] **Step 5: Build the workspace to confirm no type errors**

Run: `npm run build --workspace=apps/web`

Expected: build succeeds.

If `PathPebbleRow` is referenced elsewhere with the old prop shape (no `positionIndex`), the build will surface those callers. The only caller today is `PebbleTimeline.tsx`, which is deleted in Task 13 — but since Phase 2 doesn't delete it yet, temporarily pass `positionIndex={0}` from that file to keep the build green:

In `apps/web/components/path/PebbleTimeline.tsx`, change:

```tsx
<PathPebbleRow
  pebble={pebble}
  mark={pebble.mark_id ? markMap.get(pebble.mark_id) : undefined}
  onSelect={onSelectPebble}
/>
```

to:

```tsx
<PathPebbleRow
  pebble={pebble}
  mark={pebble.mark_id ? markMap.get(pebble.mark_id) : undefined}
  positionIndex={0}
  onSelect={onSelectPebble}
/>
```

(`PebbleTimeline.tsx` is removed in a later task; this is a transitional shim.)

- [ ] **Step 6: Re-run the build to confirm**

Run: `npm run build --workspace=apps/web`

Expected: build succeeds.

- [ ] **Step 7: Commit**

```bash
git add apps/web/components/path/PathPebbleRow.tsx apps/web/components/path/PebbleTimeline.tsx
git commit -m "feat(ui): photo and intensity-driven row height on PathPebbleRow"
```

---

### Task 7: `WeekPath`

**Files:**
- Create: `apps/web/components/path/WeekPath.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { useEffect, useRef, useState, type MutableRefObject } from "react"
import { motion, useReducedMotion } from "framer-motion"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { useMarks } from "@/lib/data/useMarks"
import type { Soul } from "@/lib/types"
import { PathPebbleRow } from "@/components/path/PathPebbleRow"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"

type WeekPathProps = {
  entry: WeekRollEntry
  souls: Soul[]
  isFocused: boolean
  onSelectPebble: (id: string) => void
  onCarvePebble: () => void
  scrollTargetRef: MutableRefObject<string | null>
}

export function WeekPath({
  entry,
  souls,
  isFocused,
  onSelectPebble,
  onCarvePebble,
  scrollTargetRef,
}: WeekPathProps) {
  const prefersReducedMotion = useReducedMotion()
  const { marks } = useMarks()
  const { markMap } = useLookupMaps(souls, marks)

  const [cascadeKey, setCascadeKey] = useState(0)
  const prevFocusedRef = useRef(isFocused)
  const prevCountRef = useRef(entry.pebbles.length)

  useEffect(() => {
    const focusBecameTrue = isFocused && !prevFocusedRef.current
    const countChanged = entry.pebbles.length !== prevCountRef.current
    if (focusBecameTrue || (isFocused && countChanged)) {
      setCascadeKey((k) => k + 1)
    }
    prevFocusedRef.current = isFocused
    prevCountRef.current = entry.pebbles.length
  }, [isFocused, entry.pebbles.length])

  useEffect(() => {
    const targetId = scrollTargetRef.current
    if (!targetId) return
    const el = document.getElementById(`pebble-${targetId}`)
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" })
      scrollTargetRef.current = null
    }
  }, [entry.pebbles, scrollTargetRef])

  if (entry.pebbles.length === 0) {
    return <PathEmptyState onCarve={onCarvePebble} />
  }

  return (
    <motion.ol
      key={cascadeKey}
      className="flex h-full flex-col gap-1 overflow-y-auto px-2 pb-32"
      style={{
        maskImage: "linear-gradient(to bottom, black 0%, black 85%, transparent 100%)",
        WebkitMaskImage: "linear-gradient(to bottom, black 0%, black 85%, transparent 100%)",
      }}
      initial={isFocused ? "hidden" : "visible"}
      animate="visible"
      variants={{
        visible: { transition: { staggerChildren: prefersReducedMotion ? 0 : 0.08 } },
      }}
    >
      {entry.pebbles.map((pebble, i) => (
        <motion.li
          key={pebble.id}
          id={`pebble-${pebble.id}`}
          variants={{
            hidden: { opacity: 0, y: -4 },
            visible: {
              opacity: 1,
              y: 0,
              transition: { duration: prefersReducedMotion ? 0 : 0.25, ease: "easeOut" },
            },
          }}
        >
          <PathPebbleRow
            pebble={pebble}
            mark={pebble.mark_id ? markMap.get(pebble.mark_id) : undefined}
            positionIndex={i}
            onSelect={onSelectPebble}
          />
        </motion.li>
      ))}
    </motion.ol>
  )
}
```

Notes:
- Non-focused neighbors start in `visible` so they don't animate when they enter the ±1 window.
- The cascade replays whenever `isFocused` flips to true or the pebble count changes for the focused entry.

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/WeekPath.tsx`

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/WeekPath.tsx
git commit -m "feat(ui): add WeekPath with focus-driven cascade"
```

---

### Task 8: `PathEmptyState` — accept `onCarve` callback

**Files:**
- Modify: `apps/web/components/path/PathEmptyState.tsx`

- [ ] **Step 1: Replace the component**

Replace the entire file with:

```tsx
"use client"

import { CirclePlus } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/layout/EmptyState"

type PathEmptyStateProps = {
  onCarve?: () => void
}

export function PathEmptyState({ onCarve }: PathEmptyStateProps) {
  const t = useTranslations("path.empty.currentWeek")
  return (
    <EmptyState
      title={t("title")}
      description={t("description")}
      action={
        <Button onClick={onCarve}>
          <CirclePlus className="size-4" data-icon="inline-start" />
          {t("cta")}
        </Button>
      }
    />
  )
}
```

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/PathEmptyState.tsx`

Expected: no errors. (Translation keys are added in Task 16; lint won't catch missing message keys.)

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/PathEmptyState.tsx
git commit -m "feat(ui): swap PathEmptyState to per-week variant with onCarve callback"
```

---

### Task 9: `WeekPager`

**Files:**
- Create: `apps/web/components/path/WeekPager.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { motion, useReducedMotion, type PanInfo } from "framer-motion"
import { type MutableRefObject } from "react"
import { useMediaQuery } from "@/lib/hooks/useMediaQuery"
import type { Soul } from "@/lib/types"
import { WeekPath } from "@/components/path/WeekPath"
import {
  weekIndex,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type WeekPagerProps = {
  entries: WeekRollEntry[]
  focused: Date
  souls: Soul[]
  onFocusChange: (weekStart: Date) => void
  onSelectPebble: (id: string) => void
  onCarvePebble: () => void
  scrollTargetRef: MutableRefObject<string | null>
}

export function WeekPager({
  entries,
  focused,
  souls,
  onFocusChange,
  onSelectPebble,
  onCarvePebble,
  scrollTargetRef,
}: WeekPagerProps) {
  const focusedIndex = weekIndex(entries, focused)
  const prefersReducedMotion = useReducedMotion()
  const isTouch = useMediaQuery("(pointer: coarse)")

  const handleDragEnd = (_: unknown, info: PanInfo) => {
    const width = typeof window !== "undefined" ? window.innerWidth : 0
    const threshold = width * 0.3
    if (info.offset.x < -threshold || info.velocity.x < -200) {
      const next = entries[focusedIndex + 1]
      if (next) onFocusChange(next.weekStart)
    } else if (info.offset.x > threshold || info.velocity.x > 200) {
      const prev = entries[focusedIndex - 1]
      if (prev) onFocusChange(prev.weekStart)
    }
  }

  return (
    <div className="overflow-hidden">
      <motion.div
        className="flex w-full"
        animate={{ x: `-${focusedIndex * 100}%` }}
        transition={{
          duration: prefersReducedMotion ? 0 : 0.3,
          ease: [0.32, 0.72, 0, 1],
        }}
        drag={isTouch ? "x" : false}
        dragDirectionLock
        dragConstraints={{ left: 0, right: 0 }}
        dragElastic={0.2}
        onDragEnd={handleDragEnd}
      >
        {entries.map((entry, i) => (
          <div key={entry.weekStartIso} className="w-full shrink-0">
            {Math.abs(i - focusedIndex) <= 1 ? (
              <WeekPath
                entry={entry}
                souls={souls}
                isFocused={i === focusedIndex}
                onSelectPebble={onSelectPebble}
                onCarvePebble={onCarvePebble}
                scrollTargetRef={scrollTargetRef}
              />
            ) : null}
          </div>
        ))}
      </motion.div>
    </div>
  )
}
```

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/WeekPager.tsx`

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/WeekPager.tsx
git commit -m "feat(ui): add WeekPager (Framer Motion x slide with touch drag)"
```

---

## Phase 3 — QuickPebbleEditor refactor

The editor needs three changes: (a) remove auto-expand, (b) make `expanded` a controlled prop, (c) render the expanded state as a portal overlay. Split into two tasks so each commit stays small.

### Task 10: `QuickPebbleEditor` — controlled expansion + no auto-expand

**Files:**
- Modify: `apps/web/components/path/QuickPebbleEditor.tsx`

- [ ] **Step 1: Replace the props type and state declaration**

Find:

```ts
type QuickPebbleEditorProps = {
  onPebbleCreated?: (pebbleId: string) => void
}
```

Replace with:

```ts
type QuickPebbleEditorProps = {
  expanded?: boolean
  onExpandedChange?: (next: boolean) => void
  onPebbleCreated?: (pebbleId: string) => void
}
```

Find the function signature:

```tsx
export function QuickPebbleEditor({ onPebbleCreated }: QuickPebbleEditorProps) {
```

Replace with:

```tsx
export function QuickPebbleEditor({
  expanded: expandedProp,
  onExpandedChange,
  onPebbleCreated,
}: QuickPebbleEditorProps) {
```

Find the existing `expanded` state:

```ts
const [expanded, setExpanded] = useState(false)
```

Replace with a controlled-or-uncontrolled fallback:

```ts
const [expandedInternal, setExpandedInternal] = useState(false)
const isControlled = expandedProp !== undefined
const expanded = isControlled ? expandedProp : expandedInternal
const setExpanded = (next: boolean) => {
  if (!isControlled) setExpandedInternal(next)
  onExpandedChange?.(next)
}
```

- [ ] **Step 2: Remove the auto-expand effect**

Delete the entire block:

```ts
// Auto-expand: new users (<5 pebbles) or no pebble created today
const shouldAutoExpand = useMemo(() => {
  if (countLoading || bounceLoading) return false
  if (pebblesCount < 5) return true
  return !bounceWindow.includes(todayLocal())
}, [pebblesCount, countLoading, bounceWindow, bounceLoading])

useEffect(() => {
  if (hasAutoExpanded.current) return
  if (countLoading || bounceLoading) return
  hasAutoExpanded.current = true
  if (shouldAutoExpand) {
    setExpanded(true)
    requestAnimationFrame(() => titleInputRef.current?.focus())
  }
}, [shouldAutoExpand, countLoading, bounceLoading])
```

Also delete:

```ts
const hasAutoExpanded = useRef(false)
```

…and the `useMemo` import if it's no longer used after this removal (verify the rest of the file).

Remove the now-unused imports for `usePebblesCount`, `useBounce`, `todayLocal`:

```ts
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useBounce } from "@/lib/data/useBounce"
import { todayLocal } from "@/lib/data/bounce-levels"
```

Remove their hook calls inside the component:

```ts
const { pebblesCount, loading: countLoading } = usePebblesCount()
const { bounceWindow, loading: bounceLoading } = useBounce()
```

- [ ] **Step 3: Build to verify type-correctness**

Run: `npm run build --workspace=apps/web`

Expected: build succeeds. If a stale reference to `pebblesCount` / `bounceWindow` / `shouldAutoExpand` remains, the build will surface it — remove it.

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/path/QuickPebbleEditor.tsx
git commit -m "refactor(ui): make QuickPebbleEditor expansion controllable; drop auto-expand"
```

---

### Task 11: `QuickPebbleEditor` — render expanded state as overlay

**Files:**
- Modify: `apps/web/components/path/QuickPebbleEditor.tsx`

The current implementation animates a single `<section>` between collapsed and expanded heights inline. Restructure so the expanded form renders in an `AnimatePresence` overlay above the path. The collapsed trigger stays in flow.

- [ ] **Step 1: Restructure the return**

At the top of the file, add to the framer-motion import:

```ts
import { AnimatePresence, motion, useReducedMotion } from "framer-motion"
```

(Replace the current `import { motion, useReducedMotion } from "framer-motion"`.)

Replace the entire `return (...)` block of the component with the following. Keep all the existing dialog/sheet JSX (`DatePickerDialog`, `GlyphPickerDialog`, `SoulsSheet`, `EmotionPickerSheet`) — they sit outside the overlay and remain reachable from inside it via portal.

```tsx
return (
  <>
    {/* Collapsed trigger — always in flow */}
    <button
      type="button"
      onClick={() => setExpanded(true)}
      aria-label={t("triggerAria")}
      className="block w-full rounded-xl border bg-card px-4 py-3 text-left font-heading text-base text-muted-foreground transition-colors hover:bg-muted"
    >
      {t("namePlaceholder")}
    </button>

    {/* Expanded overlay */}
    <AnimatePresence>
      {expanded && (
        <>
          <motion.div
            key="backdrop"
            className="fixed inset-0 z-30 bg-background/60 backdrop-blur-sm"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: prefersReducedMotion ? 0 : 0.15 }}
            onClick={() => {
              if (!name.trim()) setExpanded(false)
            }}
          />
          <motion.section
            key="overlay"
            ref={sectionRef}
            aria-label={t("editorAria")}
            className="fixed inset-x-0 bottom-0 z-40 max-h-[min(72vh,640px)] overflow-y-auto rounded-t-2xl border-t bg-card p-4 pb-[calc(1rem+var(--safe-area-bottom))]"
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ duration: prefersReducedMotion ? 0 : 0.25, ease: "easeOut" }}
            onFocusCapture={handleFocusCapture}
            onBlurCapture={handleBlurCapture}
          >
            {/* Header: date + intensity/valence grid */}
            <div className="mb-3 flex items-center justify-between">
              <button
                type="button"
                onClick={() => setDateOpen(true)}
                className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              >
                <CalendarDays className="size-3.5" aria-hidden />
                {dateLabel}
              </button>
              <ValenceIntensityGrid
                intensity={intensity}
                valence={valence}
                onIntensityChange={setIntensity}
                onValenceChange={setValence}
              />
            </div>

            {/* Title input */}
            <textarea
              ref={titleInputRef}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t("namePlaceholder")}
              className="mb-2 w-full resize-none border-none bg-transparent font-heading text-xl font-semibold text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/50"
              rows={1}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault()
                  void handleSubmit()
                }
              }}
            />

            {/* Qualification pills */}
            <div className="mb-3 flex items-center gap-2">
              <DomainPopover value={domainIds} onChange={setDomainIds} />
              <button
                type="button"
                onClick={() => setEmotionPickerOpen(true)}
                aria-label={
                  selectedEmotion
                    ? tEmotion("selectedAria", { name: selectedEmotion.name })
                    : tEmotion("pickAria")
                }
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                  selectedEmotion
                    ? "border border-border bg-background text-foreground"
                    : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
                )}
              >
                {selectedEmotion ? (
                  <>
                    <span aria-hidden>{selectedEmotion.emoji}</span>
                    {selectedEmotion.name}
                  </>
                ) : (
                  tEmotion("label")
                )}
              </button>
            </div>

            {/* Description */}
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t("descriptionPlaceholder")}
              className="mb-4 w-full resize-none border-none bg-transparent text-sm text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/50"
              rows={1}
            />

            {/* Customization tiles */}
            <div className="mb-4 grid grid-cols-4 gap-2">
              <CustomizationTile
                icon={Fingerprint}
                filled={!!selectedMark}
                onClick={() => setGlyphOpen(true)}
                ariaLabel={selectedMark ? tGlyph("changeAria") : tGlyph("addAria")}
              >
                {selectedMark && <GlyphPreview mark={selectedMark} className="size-full p-2" />}
              </CustomizationTile>
              <CollectionPopover
                value={collectionIds}
                onChange={setCollectionIds}
                collections={collections}
              />
              <CustomizationTile
                icon={Users}
                filled={soulIds.length > 0}
                onClick={() => setSoulsOpen(true)}
                ariaLabel={soulIds.length > 0 ? tSouls("selectedAria", { count: soulIds.length }) : tSouls("addAria")}
              >
                {soulIds.length > 0 && (
                  <span className="text-xs font-medium text-muted-foreground">{soulIds.length}</span>
                )}
              </CustomizationTile>
              <CustomizationTile
                icon={Image}
                filled={!!snapPreview}
                onClick={() => {
                  if (snapPreview) {
                    clearSnap()
                  } else {
                    fileInputRef.current?.click()
                  }
                }}
                ariaLabel={snapPreview ? tPhoto("removeAria") : tPhoto("addAria")}
              >
                {snapPreview && (
                  /* eslint-disable-next-line @next/next/no-img-element -- object URL */
                  <img src={snapPreview} alt={tPhoto("alt")} className="size-full object-cover" />
                )}
              </CustomizationTile>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              aria-hidden="true"
              tabIndex={-1}
              onChange={(e) => {
                void handleFileChange(e.target.files)
                e.target.value = ""
              }}
            />

            {/* Footer */}
            <div className="flex items-center justify-between">
              <VisibilityPicker value={visibility} onChange={setVisibility} />
              <Button
                variant="default"
                size="icon"
                disabled={!name.trim() || saving || snapUploading}
                onClick={() => void handleSubmit()}
                aria-label={t("save")}
                className="size-9 rounded-full"
              >
                <Check className="size-5" aria-hidden />
              </Button>
            </div>
          </motion.section>
        </>
      )}
    </AnimatePresence>

    {/* Dialogs / sheets (unchanged) */}
    <DatePickerDialog
      open={dateOpen}
      onOpenChange={setDateOpen}
      initialDate={new Date(happenedAt)}
      onSave={(date) => setHappenedAt(date.toISOString())}
    />
    <GlyphPickerDialog
      open={glyphOpen}
      onOpenChange={setGlyphOpen}
      marks={marks}
      selectedMarkId={markId}
      onSave={setMarkId}
    />
    <SoulsSheet
      open={soulsOpen}
      onOpenChange={setSoulsOpen}
      selectedIds={soulIds}
      onToggle={toggleSoul}
      souls={souls}
      onAddSoul={handleAddSoul}
    />
    <EmotionPickerSheet
      open={emotionPickerOpen}
      onOpenChange={setEmotionPickerOpen}
      value={emotionId || undefined}
      intensity={intensity}
      valence={valence}
      onChange={(id) => setEmotionId(id ?? "")}
    />
  </>
)
```

- [ ] **Step 2: Update the submit handler to call `setExpanded(false)`**

Find:

```ts
const handleSubmit = useCallback(async () => {
  ...
  resetForm()
  setExpanded(false)
  titleInputRef.current?.blur()
  onPebbleCreated?.(pebble.id)
```

This already calls `setExpanded(false)` — since `setExpanded` now calls `onExpandedChange`, the parent (`PathScreen`) will see the collapse. No code change needed unless the existing setExpanded references differ.

- [ ] **Step 3: Verify the build**

Run: `npm run build --workspace=apps/web`

Expected: build succeeds.

- [ ] **Step 4: Add the missing translation key reference**

The new collapsed trigger uses `t("triggerAria")` — added in Task 16.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/path/QuickPebbleEditor.tsx
git commit -m "feat(ui): render QuickPebbleEditor expanded state as bottom overlay"
```

---

## Phase 4 — Bottom dock and bar

### Task 12: `PathBottomBar`

**Files:**
- Create: `apps/web/components/path/PathBottomBar.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import Link from "next/link"
import { CircleUser, CirclePile, Sparkle } from "lucide-react"
import { useTranslations } from "next-intl"
import { useBounce } from "@/lib/data/useBounce"
import { useKarma } from "@/lib/data/useKarma"

function Stat({
  icon: Icon,
  value,
  label,
}: {
  icon: React.ComponentType<{ className?: string }>
  value: number | string
  label: string
}) {
  return (
    <span className="flex items-center gap-1.5">
      <Icon className="size-4 text-primary" aria-hidden />
      <span className="text-sm font-semibold text-foreground dark:text-primary">{value}</span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </span>
  )
}

export function PathBottomBar() {
  const t = useTranslations("path")
  const { bounce, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()

  return (
    <nav
      aria-label={t("bottomBar.label")}
      className="flex items-center justify-between gap-3 px-4 py-3 pb-[calc(0.75rem+var(--safe-area-bottom))]"
    >
      <Link
        href="/profile"
        aria-label={t("bottomBar.profileAria")}
        className="inline-flex size-10 items-center justify-center text-primary"
      >
        <CircleUser className="size-7" />
      </Link>
      <Link
        href="/profile"
        aria-label={t("bottomBar.statsAria")}
        className="flex items-center gap-4"
      >
        <Stat icon={CirclePile} value={bounceLoading ? "—" : bounce} label={t("stats.bounce")} />
        <Stat icon={Sparkle} value={karmaLoading ? "—" : karma} label={t("stats.karma")} />
      </Link>
    </nav>
  )
}
```

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/PathBottomBar.tsx`

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/PathBottomBar.tsx
git commit -m "feat(ui): add PathBottomBar (profile glyph + bounce + karma)"
```

---

### Task 13: `PathBottomDock`

**Files:**
- Create: `apps/web/components/path/PathBottomDock.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { PathBottomBar } from "@/components/path/PathBottomBar"

type PathBottomDockProps = {
  editorExpanded: boolean
  onEditorExpandedChange: (next: boolean) => void
  onPebbleCreated: (id: string) => void
}

export function PathBottomDock({
  editorExpanded,
  onEditorExpandedChange,
  onPebbleCreated,
}: PathBottomDockProps) {
  return (
    <div className="sticky inset-x-0 bottom-0 bg-gradient-to-t from-background to-transparent pt-4">
      <div className="px-4">
        <QuickPebbleEditor
          expanded={editorExpanded}
          onExpandedChange={onEditorExpandedChange}
          onPebbleCreated={onPebbleCreated}
        />
      </div>
      <PathBottomBar />
    </div>
  )
}
```

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/PathBottomDock.tsx`

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/path/PathBottomDock.tsx
git commit -m "feat(ui): add PathBottomDock wrapper"
```

---

## Phase 5 — Screen integration and route wiring

### Task 14: `PathScreen` — top-level layout owner

**Files:**
- Create: `apps/web/components/path/PathScreen.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Pebble, Soul } from "@/lib/types"
import { WeekRoll } from "@/components/path/WeekRoll"
import { WeekHeader } from "@/components/path/WeekHeader"
import { WeekPager } from "@/components/path/WeekPager"
import { PathBottomDock } from "@/components/path/PathBottomDock"
import { PebblePeek } from "@/components/path/PebblePeek"
import {
  buildWeekRollEntries,
  isoWeekKey,
  isoWeekStart,
  weekIndex,
} from "@/lib/utils/week-roll-entries"

type PathScreenProps = {
  pebbles: Pebble[]
  souls: Soul[]
  loading: boolean
}

export function PathScreen({ pebbles, souls, loading }: PathScreenProps) {
  const t = useTranslations("path")
  const today = useMemo(() => new Date(), [])
  const entries = useMemo(() => buildWeekRollEntries(pebbles, today), [pebbles, today])

  const [focusedWeekStart, setFocusedWeekStart] = useState<Date>(() => isoWeekStart(today))
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)
  const [editorExpanded, setEditorExpanded] = useState(false)
  const scrollTargetRef = useRef<string | null>(null)

  // Keep focusedWeekStart in sync if its entry vanishes after a mutation.
  useEffect(() => {
    if (entries.length === 0) return
    const idx = weekIndex(entries, focusedWeekStart)
    if (idx >= 0) return
    const target = entries.reduce((best, e) => {
      if (!best) return e
      const dBest = Math.abs(best.weekStart.getTime() - focusedWeekStart.getTime())
      const dE = Math.abs(e.weekStart.getTime() - focusedWeekStart.getTime())
      if (dE < dBest) return e
      if (dE === dBest && e.weekStart.getTime() < best.weekStart.getTime()) return e
      return best
    }, entries[0])
    setFocusedWeekStart(target.weekStart)
  }, [entries, focusedWeekStart])

  // Keyboard nav: ←/→ when no input is focused.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return
      const active = document.activeElement
      if (active instanceof HTMLElement) {
        const tag = active.tagName.toLowerCase()
        if (tag === "input" || tag === "textarea" || active.isContentEditable) return
      }
      const idx = weekIndex(entries, focusedWeekStart)
      const nextIdx = e.key === "ArrowLeft" ? idx - 1 : idx + 1
      const target = entries[nextIdx]
      if (target) {
        e.preventDefault()
        setFocusedWeekStart(target.weekStart)
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [entries, focusedWeekStart])

  const handlePebbleCreated = useCallback((id: string) => {
    scrollTargetRef.current = id
    setSelectedPebbleId(id)
  }, [])

  const handleCarvePebble = useCallback(() => setEditorExpanded(true), [])

  const handlePrev = useCallback(() => {
    const idx = weekIndex(entries, focusedWeekStart)
    const target = entries[idx - 1]
    if (target) setFocusedWeekStart(target.weekStart)
  }, [entries, focusedWeekStart])

  const handleNext = useCallback(() => {
    const idx = weekIndex(entries, focusedWeekStart)
    const target = entries[idx + 1]
    if (target) setFocusedWeekStart(target.weekStart)
  }, [entries, focusedWeekStart])

  if (loading && pebbles.length === 0) {
    return (
      <div className="flex min-h-[60dvh] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" aria-label={t("loading")} />
      </div>
    )
  }

  return (
    <div className="mx-auto flex h-[100dvh] max-w-md flex-col">
      <div className="px-4 pt-4">
        <WeekRoll
          entries={entries}
          focused={focusedWeekStart}
          onFocus={setFocusedWeekStart}
        />
      </div>
      <div className="px-4 pt-3">
        <WeekHeader
          entries={entries}
          focused={focusedWeekStart}
          today={today}
          onPrev={handlePrev}
          onNext={handleNext}
        />
      </div>
      <div className="min-h-0 flex-1 pt-3">
        <WeekPager
          entries={entries}
          focused={focusedWeekStart}
          souls={souls}
          onFocusChange={setFocusedWeekStart}
          onSelectPebble={setSelectedPebbleId}
          onCarvePebble={handleCarvePebble}
          scrollTargetRef={scrollTargetRef}
        />
      </div>
      <PathBottomDock
        editorExpanded={editorExpanded}
        onEditorExpandedChange={setEditorExpanded}
        onPebbleCreated={handlePebbleCreated}
      />
      <PebblePeek
        pebbleId={selectedPebbleId}
        onClose={() => setSelectedPebbleId(null)}
      />
    </div>
  )
}
```

Hidden references:
- `isoWeekKey` is imported but currently unused by this file. Remove it from the import — keep only `buildWeekRollEntries`, `isoWeekStart`, `weekIndex`.

- [ ] **Step 2: Clean up the unused import**

Replace the import line with:

```ts
import {
  buildWeekRollEntries,
  isoWeekStart,
  weekIndex,
} from "@/lib/utils/week-roll-entries"
```

- [ ] **Step 3: Lint**

Run: `npm run lint --workspace=apps/web -- --max-warnings=0 components/path/PathScreen.tsx`

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/path/PathScreen.tsx
git commit -m "feat(ui): add PathScreen owning focusedWeekStart and keyboard nav"
```

---

### Task 15: Rewrite `app/path/page.tsx` + delete obsolete components

**Files:**
- Modify: `apps/web/app/path/page.tsx`
- Delete: `apps/web/components/path/PebbleTimeline.tsx`
- Delete: `apps/web/components/path/WeekSectionHeader.tsx`
- Delete: `apps/web/components/path/PathProfileCard.tsx`

- [ ] **Step 1: Rewrite the route**

Replace the file content with:

```tsx
"use client"

import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { PathScreen } from "@/components/path/PathScreen"

export default function PathPage() {
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()
  const loading = pebblesLoading || soulsLoading

  return <PathScreen pebbles={pebbles} souls={souls} loading={loading} />
}
```

- [ ] **Step 2: Delete obsolete components**

Run:

```bash
git rm apps/web/components/path/PebbleTimeline.tsx
git rm apps/web/components/path/WeekSectionHeader.tsx
git rm apps/web/components/path/PathProfileCard.tsx
```

- [ ] **Step 3: Verify no stale references remain**

Run:

```bash
grep -rn "PebbleTimeline\|WeekSectionHeader\|PathProfileCard" apps/web --include="*.tsx" --include="*.ts" 2>/dev/null
```

Expected: empty output. If `PathProfileCard` is still referenced in `apps/web/app/profile/page.tsx` (it was, at spec-write time, as a no-op import), remove the import and any usage. The profile page does not need it — `BackPath` handles the back affordance.

- [ ] **Step 4: Build**

Run: `npm run build --workspace=apps/web`

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add apps/web/app/path/page.tsx apps/web/components/path apps/web/app/profile
git commit -m "feat(ui): wire /path route to PathScreen; drop legacy timeline components"
```

---

## Phase 6 — Translations and Arkaik

### Task 16: Translation strings

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`
- Modify: `apps/web/lib/i18n/messages/fr.json`

- [ ] **Step 1: Update EN messages**

In `apps/web/lib/i18n/messages/en.json`, locate the `"path"` object. Replace the existing `"empty"` block and append the new keys so the final `"path"` block looks like:

```json
"path": {
  "loading": "Loading…",
  "empty": {
    "currentWeek": {
      "title": "Fresh week",
      "description": "Carve your first pebble of the week.",
      "cta": "Carve a pebble"
    }
  },
  "today": "Today",
  "yesterday": "Yesterday",
  "todayLabel": "Today — {date}",
  "yesterdayLabel": "Yesterday — {date}",
  "weekLabel": "Week {week}",
  "profileAria": "{name}'s profile",
  "weekRoll": {
    "label": "Weeks"
  },
  "weekHeader": {
    "previous": "Previous week",
    "next": "Next week",
    "weekAria": "Week {iso}, {count} pebbles"
  },
  "bottomBar": {
    "label": "Profile and stats",
    "profileAria": "Profile",
    "statsAria": "Profile stats"
  },
  "stats": {
    "bounce": "bounce",
    "karma": "karma"
  }
}
```

- [ ] **Step 2: Add the `record.triggerAria` key**

In the same `en.json`, locate the `"record"` namespace. Add `"triggerAria": "What happened?"` alongside the existing keys (sibling of `"save"`, `"namePlaceholder"`, etc).

- [ ] **Step 3: Mirror the changes in FR**

In `apps/web/lib/i18n/messages/fr.json`, locate the `"path"` object and replace it with:

```json
"path": {
  "loading": "Chargement…",
  "empty": {
    "currentWeek": {
      "title": "Nouvelle semaine",
      "description": "Tailler ton premier pebble de la semaine.",
      "cta": "Tailler un pebble"
    }
  },
  "today": "Aujourd'hui",
  "yesterday": "Hier",
  "todayLabel": "Aujourd'hui — {date}",
  "yesterdayLabel": "Hier — {date}",
  "weekLabel": "Semaine {week}",
  "profileAria": "Profil de {name}",
  "weekRoll": {
    "label": "Semaines"
  },
  "weekHeader": {
    "previous": "Semaine précédente",
    "next": "Semaine suivante",
    "weekAria": "Semaine {iso}, {count} pebbles"
  },
  "bottomBar": {
    "label": "Profil et statistiques",
    "profileAria": "Profil",
    "statsAria": "Statistiques du profil"
  },
  "stats": {
    "bounce": "bounce",
    "karma": "karma"
  }
}
```

…and add `"triggerAria": "Que s'est-il passé ?"` in the `"record"` namespace.

(If the EN/FR file structures diverge — e.g. one is missing `today`/`yesterday` keys — match what exists; do not introduce keys to FR that don't exist in EN or vice versa, except for the new ones above.)

- [ ] **Step 4: Build to confirm the JSON parses**

Run: `npm run build --workspace=apps/web`

Expected: build succeeds. If next-intl throws "missing message" for any new key, double-check both files have it.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(ui): add path week-roll translations"
```

---

### Task 17: Arkaik bundle update

**Files:**
- Modify: `docs/arkaik/bundle.json`

Per the `arkaik` skill, sync the product map with the new component graph.

- [ ] **Step 1: Invoke the arkaik skill**

```bash
# In the conversation: invoke the arkaik skill via the Skill tool to apply
# the following changes to docs/arkaik/bundle.json:
#
#   - Delete nodes: PebbleTimeline, WeekSectionHeader, PathProfileCard.
#   - Add nodes (under PathPage): PathScreen, WeekRoll, WeekRollCairn,
#     WeekHeader, WeekPager, WeekPath, PathBottomDock, PathBottomBar.
#   - Move existing node QuickPebbleEditor: parent PathPage → PathBottomDock.
#   - Add edge: PathBottomBar → /profile (push).
#   - Add edge: WeekPath → PebblePeek (open).
#   - No data-model / RPC / endpoint additions.
```

Use the Skill tool with `skill: "arkaik"` and the changes listed above as the prompt. The skill handles the JSON edits.

- [ ] **Step 2: Review the diff**

Run: `git diff docs/arkaik/bundle.json`

Expected: only the changes listed above; no unrelated structural reshuffling.

- [ ] **Step 3: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(arkaik): update product map for /path week-roll redesign"
```

---

## Phase 7 — Verification

### Task 18: Lint, build, manual smoke

**Files:** N/A (verification only).

- [ ] **Step 1: Re-run the pure-helper assertions**

Run: `npm run test:week-roll --workspace=apps/web`

Expected: all `✓`, exit 0.

- [ ] **Step 2: Lint the whole workspace**

Run: `npm run lint --workspace=apps/web`

Expected: zero errors. Fix anything that surfaces; commit fixes as `quality(ui): ...`.

- [ ] **Step 3: Build the workspace**

Run: `npm run build --workspace=apps/web`

Expected: build succeeds with no type errors.

- [ ] **Step 4: Run the dev server and run the manual smoke checklist**

Run: `npm run dev --workspace=apps/web`

Open the local URL printed by the dev server. With a populated test account:

- Cold load `/path` → current week focused, cascade visible, bottom dock visible, stats render.
- Click a cairn 3 weeks back → roll re-centers, header range updates, page slides, new cascade runs.
- Click chevrons; left and right disabled correctly at bounds.
- Press `←` / `→` while no input is focused → weeks swap.
- Tap the bottom "What happened?" trigger → editor opens as overlay with backdrop. Click backdrop with empty name → collapses. Submit a pebble in the current week → collapses, new row appears, cascade re-runs, scrolls to the row.
- Submit a retro pebble dated 2 years ago → new entry appears in roll left of current; focus stays on current; navigating to it shows the new row.
- Tap a pebble row → `PebblePeek` opens; close → focus preserved.
- Light + dark parity for roll, header, rows, dock, bar.
- macOS reduce-motion toggle on → cascade and pager animations collapse to instant.
- iOS Safari (or a touch-emulated browser): horizontal swipe inside the path body navigates weeks; vertical scroll inside `WeekPath` still works.

If any check fails, file a focused fix commit. Do not bundle behavioral bug-fixes with the verification step's documentation.

- [ ] **Step 5: Document smoke results in the PR body**

The smoke checklist becomes the "Test plan" section of the PR.

---

## Phase 8 — Open the PR

### Task 19: Push branch and open PR

**Files:** N/A (gh only).

- [ ] **Step 1: Push the branch with upstream tracking**

Run: `git push -u origin feat/421-weeks-roll-web`

Expected: branch pushed.

- [ ] **Step 2: Open the PR**

Run:

```bash
gh pr create --title "feat(ui): align /path with iOS weeks roll (#421)" \
  --body "$(cat <<'EOF'
Resolves #421

## Summary

Port the iOS Path week-paginated experience to `apps/web/app/path`:
- Horizontal weeks roll of Rive cairns (focused cairn centered, plays on focus).
- Date-range header pill with chevrons.
- Per-week paged list with focus-driven cascade and bottom mask gradient.
- `QuickPebbleEditor` relocated to the bottom dock as a "What happened?" trigger; expands as an overlay above the path.
- New `PathBottomBar` (CircleUser → /profile · bounce · karma).
- Pure helpers + inline-assertion script for the week-roll entry builder.

## Architecture highlights

- `PathScreen` owns `focusedWeekStart`. All triggers (cairn tap, chevrons, touch drag, keyboard `←/→`) write the same state.
- `WeekPager` is a single Framer Motion `<motion.div>` animating `x`, lazy-rendering ±1 neighbors. Touch drag enabled via `useMediaQuery("(pointer: coarse)")`; desktop never gets mouse drag.
- `WeekPath` cascade re-runs whenever `isFocused` flips to true (or pebble count changes for the focused entry) — not on mount, because neighbors stay mounted.
- `QuickPebbleEditor` is now controlled (`expanded` / `onExpandedChange`). Auto-expand removed; empty-state CTA in `WeekPath` is the first-time-create entry point.
- No schema / RPC changes — `usePebbles()` already returns `intensity` and pre-signed snap URLs.

## Test plan

- [x] `npm run test:week-roll --workspace=apps/web` passes (pure-helper assertions)
- [x] `npm run lint --workspace=apps/web` clean
- [x] `npm run build --workspace=apps/web` succeeds
- [x] Manual smoke checklist run (see plan §Task 18)

Spec: [`docs/superpowers/specs/2026-05-14-web-week-path-view-design.md`](docs/superpowers/specs/2026-05-14-web-week-path-view-design.md)
Plan: [`docs/superpowers/plans/2026-05-14-web-week-path-view.md`](docs/superpowers/plans/2026-05-14-web-week-path-view.md)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Apply labels and milestone**

Run:

```bash
gh pr edit --add-label "core,feat,ui,web" --milestone "M27 · Align Webapp with iOS"
```

Confirm labels (`core`, `feat`, `ui`, `web` — inherited from issue 421) and milestone (`M27 · Align Webapp with iOS`) match the issue. If anything diverges, edit accordingly.

- [ ] **Step 4: Return the PR URL**

Run: `gh pr view --json url -q .url`

Paste the URL in chat for the user.

---

## Notes for the executing agent

- This is a Large task (per CLAUDE.md triage). Use the workspace-scoped lint/build commands shown in each task.
- Do not run `npm run build` from the repo root — it is unnecessary for a web-only change and slower.
- Do not introduce snapshot or component tests. The repo has no runner.
- Do not change `DataProvider`, `SupabaseProvider`, or any RPC/migration. This PR is presentational.
- If a translation key is missing in either `en.json` or `fr.json` at runtime, next-intl will log a console error. Treat that as a blocker for the smoke step.
- Keep commits focused. If you discover a bug in unrelated code while implementing, surface it in chat — do not bundle the fix.
