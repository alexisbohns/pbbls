# Web Week Path View — Design Spec

**Issue:** [#421 — Weeks roll on webapp](https://github.com/alexisbohns/pbbls/issues/421)
**Date:** 2026-05-14
**Scope:** Web only (`apps/web`). No schema changes, no RPC, no shared-package changes.
**Size:** Large (cross-cutting `/path` rewrite — layout overhaul + multiple new components).
**iOS reference:** [`docs/superpowers/specs/2026-05-10-ios-week-path-view-design.md`](2026-05-10-ios-week-path-view-design.md) (PR [#398](https://github.com/alexisbohns/pbbls/pull/398))

## Summary

Port the iOS Path week-paginated experience to web. Replace the continuous "all weeks at once" timeline with: a horizontal weeks roll of Rive cairns at the top, a date-range header with chevrons, a per-week pebble list in the body that pages horizontally, the `QuickPebbleEditor` relocated to the bottom (collapsed as a "New pebble" bar, expanded as an overlay), and a `PathBottomBar` (profile glyph + bounce + karma) at the bottom edge.

## Background and motivation

iOS shipped this redesign in #398 (resolves #388). Issue #421 asks for web parity. The same rationales apply: navigate by week as a first-class unit, give each cairn presence, free up the bottom for identity + stats. On web the redesign is significantly smaller in scope than iOS because:

- The `DataProvider` already returns pebbles with `intensity` and pre-signed `snaps[].instants.original` URLs — no new RPC needed.
- The web's global navigation tier (`Sidebar`, `BottomNav`, `MobileHeader`) is currently unmounted dead code, so introducing an in-view bottom bar collides with nothing.
- `useBounce()` and `useKarma()` already exist as data hooks.

## Locked decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Web replaces the inline `PathProfileCard` sidebar with an in-view `PathBottomBar`. | iOS spec parity; web's global nav is unmounted today, so this is non-disruptive. |
| 2 | `/path` runs on both mobile and desktop with the same layout (narrow centered column, `~max-w-md`). | User decision. Path remains a mobile-class artifact on big screens; desktop differentiation deferred. |
| 3 | Week swap triggers: cairn tap, chevron click, keyboard `←/→`, touch drag. **No mouse drag.** | User decision. Mouse drag is unusual on desktop; touch drag preserves mobile parity with iOS `TabView(.page)`. |
| 4 | Horizontal paging mechanic: a single Framer Motion `<motion.div>` animating `x` transform, lazy-rendering ±1 neighbors. | Framer is already a dep (used by `QuickPebbleEditor`); easier to control the binding than `Embla`/`shadcn Carousel`; lazy ±1 keeps DOM light. |
| 5 | Weeks-roll centering: native `scrollIntoView({ inline: 'center', behavior: 'smooth' })` on the focused button. | Zero extra deps; works in modern Safari/Chrome. |
| 6 | "New pebble" CTA = the collapsed `QuickPebbleEditor` trigger, relocated to the bottom dock. Expand renders as an overlay above the path content. | User decision. Keeps the existing editor and its flows; mirrors iOS's "sheet" feel via a Framer-animated overlay. |
| 7 | Auto-expand of `QuickPebbleEditor` (today triggered for users with `<5` pebbles or no pebble today) is **removed**. | The editor now lives at the bottom; auto-expanding on mount would steal the path. Empty-state CTA in `WeekPath` is the new entry point for first-time creates. |
| 8 | Roll model: union of weeks-with-pebbles ∪ `{currentWeek, nextWeek}`, sorted ascending by `weekStart`. | iOS parity. |
| 9 | Per-week sort: past = oldest-first; current and future = newest-first. Pivot is strict `weekStart < currentWeekStart`. | iOS parity. |
| 10 | Year suffix in `WeekHeader` date range appears only when `focusedWeekStart` year ≠ `today` year. | iOS parity. |
| 11 | Photo rotation parity: `positionIndex % 2 === 0 ? -7° : +4°`. | iOS parity. |
| 12 | Large pebble (`intensity === 3`): primary-fill thumbnail with light glyph stroke. Already supported by `usePebbleVisual`. | iOS parity. |
| 13 | Path body fades behind the bottom dock via `mask-image` linear gradient (0% black → 85% black → 100% transparent). | iOS parity. |
| 14 | The `path_pebbles` RPC introduced in iOS #398 is **not** used on web. | `usePebbles()` already returns the same data shape via `DataProvider`. Reusing it preserves the cache contract. |
| 15 | Both the glyph button and the bounce/karma stat cluster in `PathBottomBar` link to `/profile`. | iOS spec parity. |
| 16 | `/profile` keeps its existing `BackPath` component (no change). | iOS-spec "back to path" affordance already exists on web. |

## Out-of-scope (deferred, matching iOS)

- Pebble shape-conforming background ("thick outline" Figma trick).
- Real user glyph in the bottom bar — placeholder is `CircleUser` (Lucide), tinted accent.
- Auto-jump focus to a freshly-created retro pebble's week.
- Page-indicator dots under the cairn strip.
- Photo failure placeholder. If `snaps[0].instants.original` is missing or fails, the row renders without the photo (no fallback graphic). Row height adjusts accordingly because the height is data-driven (`hasPhoto = !!snaps[0]?.instants?.original`).
- Snapshot or component tests. The repo has no Jest/Vitest runner today. Pure helpers (`buildWeekRollEntries`, `rotation`, `formatWeekRange`) get inline assertions in a `scripts/test-week-roll.ts` script that can be removed once a real runner lands.

## Architecture

### Runtime tree (`/path`)

```
PathPage                            (app/path/page.tsx — thin shell, hooks data)
└─ PathScreen                       (owns focusedWeekStart state, keyboard nav)
   ├─ WeekRoll                      (entries, focused, onFocus)
   │   └─ WeekRollCairn per entry   (Rive cairn, plays on focus flip)
   ├─ WeekHeader                    (entries, focused, onPrev, onNext, today)
   ├─ WeekPager                     (entries, focused — Framer Motion x slide)
   │   └─ WeekPath per ±1 entry     (cascade list)
   │       └─ PathPebbleRow per pebble
   └─ PathBottomDock                (sticky bottom-0)
       ├─ QuickPebbleEditor         (collapsed = "New pebble" bar; expanded = overlay)
       └─ PathBottomBar             (CircleUser link /profile · bounce · karma)
```

### File layout (under `apps/web`)

```
app/path/page.tsx                   # CHANGED — thin shell, renders <PathScreen />
components/path/
  PathScreen.tsx                    # NEW — owns focusedWeekStart, keyboard, top-level layout
  WeekRoll.tsx                      # NEW — horizontal cairn strip
  WeekRollCairn.tsx                 # NEW — single cairn cell with Rive
  WeekHeader.tsx                    # NEW — date-range pill + chevrons
  WeekPager.tsx                     # NEW — Framer Motion x-slide (lazy ±1)
  WeekPath.tsx                      # NEW — per-week scrollable list + cascade + bottom mask
  PathBottomDock.tsx                # NEW — sticky bottom wrapper
  PathBottomBar.tsx                 # NEW — glyph + bounce + karma
  PathPebbleRow.tsx                 # CHANGED — add photo + rotation parity
  QuickPebbleEditor.tsx             # CHANGED — remove auto-expand; render as overlay when expanded
  PathProfileCard.tsx               # DELETED
  PebbleTimeline.tsx                # DELETED
  WeekSectionHeader.tsx             # DELETED
  PathEmptyState.tsx                # CHANGED — current-week-empty variant with CTA
lib/utils/
  week-roll-entries.ts              # NEW — pure builder + formatters
lib/hooks/
  useMediaQuery.ts                  # NEW — tiny window.matchMedia wrapper
scripts/
  test-week-roll.ts                 # NEW — inline assertions over the pure helpers
docs/arkaik/bundle.json             # UPDATED — see Arkaik section
```

### Why split `PathScreen` from `PathPage`

`apps/web/CLAUDE.md` directs route pages to stay thin shells. `PathPage` owns `usePebbles()` and renders `<PathScreen pebbles={...} loading={...} onSelectPebble={...} />`. `PathScreen` is the testable layout unit and is unit-testable without provider context.

## Data layer (no provider changes)

- `usePebbles()` already returns `Pebble[]` carrying `intensity`, `happened_at`, and `snaps: PebbleSnap[]` with pre-signed URLs cached by `SupabaseProvider`.
- `useBounce()` and `useKarma()` back `PathBottomBar`.
- No new `DataProvider` method, no new RPC, no migration.

## Components

### `PathScreen`

```tsx
type PathScreenProps = {
  pebbles: Pebble[]
  souls: Soul[]
  loading: boolean
}

export function PathScreen({ pebbles, souls, loading }: PathScreenProps) {
  const today = useMemo(() => new Date(), [])
  const entries = useMemo(() => buildWeekRollEntries(pebbles, today), [pebbles, today])
  const [focusedWeekStart, setFocusedWeekStart] = useState<Date>(() => isoWeekStart(today))
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)
  const scrollTargetRef = useRef<string | null>(null)
  // ...
}
```

Responsibilities:
- Owns `focusedWeekStart`. Computes `focusedIndex` from `weekIndex(entries, focusedWeekStart)`.
- Keyboard nav: `useEffect` attaches a `keydown` listener on the window. On `ArrowLeft` / `ArrowRight`, when `document.activeElement` is not inside an `<input>`, `<textarea>`, or `[contenteditable]`, move focus to prev/next entry.
- After-create scroll: when `QuickPebbleEditor` reports `onPebbleCreated(id)`:
  1. Look up the new pebble's week.
  2. If it matches `focusedWeekStart` → set `scrollTargetRef.current = id`, the row `useEffect` scrolls it into view. (Mirrors today's behavior in `PathPage`.)
  3. If it falls in a different week → do nothing further. Roll surfaces the new entry; user navigates manually.
- Owns `selectedPebbleId` and renders `<PebblePeek pebbleId={selectedPebbleId} onClose={...} />` at the screen root.

Loading: while `loading` is `true` and `pebbles.length === 0`, render a centered `Loader2` spinner instead of the full layout. Subsequent loads (after mutations) keep the existing UI on screen.

### `WeekRoll`

```tsx
type WeekRollProps = {
  entries: WeekRollEntry[]
  focused: Date
  onFocus: (weekStart: Date) => void
}

export function WeekRoll({ entries, focused, onFocus }: WeekRollProps) {
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
    <div ref={scrollRef} className="overflow-x-auto scrollbar-none" aria-label={t("weekRoll.label")}>
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

function opacityForDistance(d: number): number {
  if (d === 0) return 1
  if (d === 1) return 0.5
  if (d === 2) return 0.25
  return 0
}
```

- `px-[50%]` ensures the first/last cairn rests centered.
- `behavior: 'instant'` on the first run avoids initial-paint jitter. `isFirstRunRef` flips after the first effect; subsequent focus changes use `smooth`.
- The scroll container has `scrollbar-none` (Tailwind utility — add via plugin if not present; otherwise inline `[&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]`).

### `WeekRollCairn`

```tsx
type WeekRollCairnProps = {
  entry: WeekRollEntry
  isFocused: boolean
  opacity: number
  onClick: () => void
}

export function WeekRollCairn({ entry, isFocused, opacity, onClick }: WeekRollCairnProps) {
  const { rive, RiveComponent } = useRive({
    src: "/animations/pbbls-cairn.riv",
    autoplay: false,
  })
  const prefersReducedMotion = useReducedMotion()

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
        aria-label={t("weekHeader.weekAria", { iso: entry.isoWeek, count: entry.pebbles.length })}
        className="flex w-[72px] flex-col items-center gap-1 transition-opacity"
        style={{ opacity }}
      >
        <div className="size-14"><RiveComponent /></div>
        <span className={cn(
          "font-heading text-xs font-semibold",
          isFocused ? "text-primary" : "text-muted-foreground",
        )}>
          {entry.isoWeek}
        </span>
      </button>
    </li>
  )
}
```

Notes:
- The Rive view-model state-machine version (`pbbls-cairn-states.riv`) iOS uses isn't on web yet — the existing `pbbls-cairn.riv` is the non-state-machine version. We use `play()`/`stop()` against the legacy file. If a future PR ports the state-machine file to web, swap the bindings then.
- Reduced motion: skip `play()`; the cairn stays on its idle frame.

### `WeekHeader`

```tsx
type WeekHeaderProps = {
  entries: WeekRollEntry[]
  focused: Date
  today: Date
  onPrev: () => void
  onNext: () => void
}
```

Layout: a `rounded-full border h-10` pill with `<ChevronLeft />` button, centered date range, `<ChevronRight />` button.

```tsx
<div className="flex items-center justify-between rounded-full border h-10 px-2
                border-muted dark:border-foreground">
  <Button variant="ghost" size="icon" disabled={focusedIndex <= 0} onClick={onPrev}
          aria-label={t("weekHeader.previous")}>
    <ChevronLeft className="size-5 text-primary" />
  </Button>
  <span className="font-heading text-[17px] font-semibold uppercase tracking-[0.02em]
                   text-muted-foreground dark:text-muted">
    {formatWeekRange(focused, today, locale)}
  </span>
  <Button variant="ghost" size="icon" disabled={focusedIndex >= entries.length - 1} onClick={onNext}
          aria-label={t("weekHeader.next")}>
    <ChevronRight className="size-5 text-primary" />
  </Button>
</div>
```

`formatWeekRange` lives in `lib/utils/week-roll-entries.ts`; uses `Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' })` for both endpoints. Appends ` · YYYY` when `focused.getFullYear() !== today.getFullYear()`. Locale comes from `useLocale()` (next-intl).

### `WeekPager`

```tsx
type WeekPagerProps = {
  entries: WeekRollEntry[]
  focused: Date
  onFocusChange: (weekStart: Date) => void
  onSelectPebble: (id: string) => void
  scrollTargetRef: MutableRefObject<string | null>
}

export function WeekPager(props: WeekPagerProps) {
  const focusedIndex = weekIndex(props.entries, props.focused)
  const prefersReducedMotion = useReducedMotion()
  const isTouch = useMediaQuery("(pointer: coarse)")

  const handleDragEnd = (_: never, info: PanInfo) => {
    const { offset, velocity } = info
    const threshold = window.innerWidth * 0.3
    if (offset.x < -threshold || velocity.x < -200) {
      const next = props.entries[focusedIndex + 1]
      if (next) props.onFocusChange(next.weekStart)
    } else if (offset.x > threshold || velocity.x > 200) {
      const prev = props.entries[focusedIndex - 1]
      if (prev) props.onFocusChange(prev.weekStart)
    }
  }

  return (
    <div className="overflow-hidden">
      <motion.div
        className="flex w-full"
        animate={{ x: `-${focusedIndex * 100}%` }}
        transition={{ duration: prefersReducedMotion ? 0 : 0.3, ease: [0.32, 0.72, 0, 1] }}
        drag={isTouch ? "x" : false}
        dragDirectionLock
        dragConstraints={{ left: 0, right: 0 }}
        dragElastic={0.2}
        onDragEnd={handleDragEnd}
      >
        {props.entries.map((entry, i) => (
          <div key={entry.weekStartIso} className="w-full shrink-0">
            {Math.abs(i - focusedIndex) <= 1 ? (
              <WeekPath
                entry={entry}
                isFocused={i === focusedIndex}
                onSelectPebble={props.onSelectPebble}
                scrollTargetRef={props.scrollTargetRef}
              />
            ) : null}
          </div>
        ))}
      </motion.div>
    </div>
  )
}
```

- `useMediaQuery('(pointer: coarse)')` gates drag to touch devices. Trackpads return `(pointer: fine)` and are excluded. The hook is new — a 15-line `lib/hooks/useMediaQuery.ts` wrapping `window.matchMedia` with SSR-safe defaults (returns `false` on the server pass, hydrates on mount).
- `dragDirectionLock` keeps vertical scroll inside `WeekPath` working.

### `WeekPath`

```tsx
type WeekPathProps = {
  entry: WeekRollEntry
  isFocused: boolean
  onSelectPebble: (id: string) => void
  scrollTargetRef: MutableRefObject<string | null>
}

export function WeekPath({ entry, isFocused, onSelectPebble, scrollTargetRef }: WeekPathProps) {
  const prefersReducedMotion = useReducedMotion()
  // cascadeKey: increments whenever isFocused flips to true OR the pebble count
  // for this week changes. The motion.ol below is keyed on it, so a new value
  // re-mounts the staggered children and replays the cascade from scratch.
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
    return <PathEmptyState />   // current-week-empty variant (CTA wires to the bottom editor)
  }

  return (
    <motion.ol
      key={cascadeKey}
      className="flex flex-col gap-1 overflow-y-auto px-2 pb-24"
      style={{
        maskImage: "linear-gradient(to bottom, black 0%, black 85%, transparent 100%)",
        WebkitMaskImage: "linear-gradient(to bottom, black 0%, black 85%, transparent 100%)",
      }}
      initial="hidden"
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
            visible: { opacity: 1, y: 0, transition: { duration: 0.25, ease: "easeOut" } },
          }}
        >
          <PathPebbleRow
            pebble={pebble}
            positionIndex={i}
            onSelect={onSelectPebble}
          />
        </motion.li>
      ))}
    </motion.ol>
  )
}
```

The cascade is driven off `isFocused` flips, not mount, because `WeekPager` keeps ±1 neighbors mounted: when an adjacent week becomes focused the previously focused page does **not** remount. `cascadeKey` (incremented when `isFocused` flips true, or when the focused entry's pebble count changes) keys the inner `<motion.ol>`, which re-mounts the staggered children and replays the cascade from scratch. Non-focused neighbors render their pebbles fully revealed (no animation).

`WeekPager` passes `isFocused={i === focusedIndex}` to each `WeekPath` it mounts.

### `PathPebbleRow` (changed)

Today's row already drives intensity-based render via `usePebbleVisual`. Two additions:

1. **Photo attachment.** When `pebble.snaps[0]?.instants?.original` is truthy:
   ```tsx
   <img
     src={pebble.snaps[0].instants.original}
     alt=""
     className="size-16 rounded-lg object-cover ring-4 ring-background shadow-md"
     style={{ transform: `rotate(${rotation(positionIndex)}deg)` }}
     loading="lazy"
   />
   ```
   The photo sits in a 64×64 frame inside the row, offset to the right of the glyph; row height grows to fit the rotated bounding box.

2. **Row height table (data-driven, not position-driven):**

   | State | Glyph thumb | Row height |
   |---|---|---|
   | small/medium, no photo | 56px | 60px |
   | small/medium, photo, even index (-7°) | 56px | 71px |
   | small/medium, photo, odd index (+4°) | 56px | 68px |
   | large (`intensity === 3`), any | 96px | 100px |

Pure helpers exported for testing:

```ts
export function rotation(positionIndex: number): number {
  return positionIndex % 2 === 0 ? -7 : 4
}
export function rowHeight(intensity: 1 | 2 | 3, hasPhoto: boolean, positionIndex: number): number {
  if (intensity === 3) return 100
  if (!hasPhoto) return 60
  return positionIndex % 2 === 0 ? 71 : 68
}
```

The whole row is one clickable `<button>` — tapping anywhere (glyph or photo) calls `onSelect(pebble.id)`. No nested interactive element. Long-press / context-menu delete is **not** introduced on web (today's row uses `PebblePeek` for actions; we preserve that).

### `QuickPebbleEditor` (changed)

Two changes to the existing component:

**(a) Remove the auto-expand effect.** Today the editor opens for users with `<5` pebbles or no pebble today. With the editor now anchored at the bottom, auto-expanding would steal the focus and cover the path. Delete the `useEffect` keyed on `shouldAutoExpand` and the `hasAutoExpanded` ref. Empty-state CTA in `WeekPath` is now the documented first-time-create entry point.

**(a.1) Make `expanded` a controlled prop.** Change the signature to accept `expanded?: boolean` and `onExpandedChange?: (next: boolean) => void`. Internal state still exists as a fallback for callers that don't control it, but `PathScreen` controls it explicitly so that `PathEmptyState`'s "Carve a pebble" CTA can call `onExpandedChange(true)` to expand the editor. `PathBottomDock` is a presentational pass-through.

**(b) Render as an overlay when expanded.** Today the component is a regular `<section>` that grows inline. We wrap it so that expanded state floats over the page:

```tsx
<>
  {/* Trigger row — always visible at the bottom dock */}
  <section ... className="...collapsed-trigger-styles..." />

  {/* Overlay — portaled or absolutely positioned, mounted only when expanded */}
  <AnimatePresence>
    {expanded && (
      <>
        <motion.div
          key="backdrop"
          className="fixed inset-0 bg-background/60 backdrop-blur-sm z-30"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          onClick={() => { if (!name.trim()) setExpanded(false) }}
        />
        <motion.section
          key="overlay"
          className="fixed inset-x-0 bottom-0 z-40 bg-card border-t rounded-t-2xl p-4
                     max-h-[min(72vh,640px)] overflow-y-auto pb-[var(--safe-area-bottom)]"
          initial={{ y: "100%" }} animate={{ y: 0 }} exit={{ y: "100%" }}
          transition={{ duration: prefersReducedMotion ? 0 : 0.25, ease: "easeOut" }}
        >
          {/* full editor form — title, intensity grid, customization tiles, save */}
        </motion.section>
      </>
    )}
  </AnimatePresence>
</>
```

The collapsed trigger (the title `<textarea>` only, styled as a full-width "what happened" bar) stays in flow inside `PathBottomDock`. Tapping it sets `expanded = true`, which mounts the overlay.

Form state, snap upload, validation, submit handler, dialogs/sheets (date, glyph, souls, emotion, collection): **unchanged**. Only the surrounding chrome differs.

Submit collapses the overlay (`setExpanded(false)`), resets the form, and calls `onPebbleCreated`.

### `PathBottomDock`

```tsx
type PathBottomDockProps = {
  editorExpanded: boolean
  onEditorExpandedChange: (next: boolean) => void
  onPebbleCreated: (id: string) => void
}

export function PathBottomDock({ editorExpanded, onEditorExpandedChange, onPebbleCreated }: PathBottomDockProps) {
  return (
    <div className="sticky bottom-0 inset-x-0 bg-gradient-to-t from-background to-transparent pt-4">
      <QuickPebbleEditor
        expanded={editorExpanded}
        onExpandedChange={onEditorExpandedChange}
        onPebbleCreated={onPebbleCreated}
      />
      <PathBottomBar />
    </div>
  )
}
```

No state of its own. The expanded editor's `<motion.section>` is `fixed` to the viewport so it escapes this dock's stacking context and covers the full screen.

### `PathBottomBar`

```tsx
export function PathBottomBar() {
  const { bounce, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()
  const t = useTranslations("path")

  return (
    <nav aria-label={t("bottomBar.label")}
         className="flex items-center justify-between gap-3 px-4 py-3
                    pb-[calc(0.75rem+var(--safe-area-bottom))]">
      <Link href="/profile" aria-label={t("bottomBar.profileAria")}
            className="inline-flex size-10 items-center justify-center text-primary">
        <CircleUser className="size-7" />
      </Link>
      <Link href="/profile" className="flex items-center gap-4 text-sm font-semibold
                                       text-foreground dark:text-primary">
        <Stat icon={CirclePile} value={bounceLoading ? "—" : bounce} label={t("stats.bounce")} />
        <Stat icon={Sparkle} value={karmaLoading ? "—" : karma} label={t("stats.karma")} />
      </Link>
    </nav>
  )
}
```

- Glyph and stat cluster are two separate `<Link href="/profile">` for accessibility (the stat cluster gets its own labeled link).
- `Stat` is a tiny presentational helper (icon + value + label), reused from today's `PathProfileCard` shape.

### Pure helpers (`lib/utils/week-roll-entries.ts`)

```ts
export type WeekRollEntry = {
  weekStart: Date
  weekStartIso: string  // "YYYY-Www" stable key
  isoWeek: number
  pebbles: Pebble[]
}

export function isoWeekStart(d: Date): Date
export function isoWeekKey(d: Date): string         // "2026-W19"
export function isoWeekNumber(d: Date): number
export function weekIndex(entries: WeekRollEntry[], weekStart: Date): number
export function buildWeekRollEntries(pebbles: Pebble[], today: Date): WeekRollEntry[]
export function formatWeekRange(weekStart: Date, today: Date, locale: string): string
```

`buildWeekRollEntries` algorithm:
1. Compute `currentWeekStart = isoWeekStart(today)` and `nextWeekStart = isoWeekStart(addDays(today, 7))`.
2. Bucket `pebbles` by `isoWeekKey(pebble.happened_at)`.
3. Union the bucket keys with `{currentWeekStart, nextWeekStart}`.
4. For each entry: if `weekStart < currentWeekStart`, sort `pebbles` ascending by `happened_at`; else descending.
5. Return entries ascending by `weekStart`.

ISO-week computation: Monday-based (`getDay()` adjusted), local-time, no time-zone tricks. Pebble `happened_at` is an ISO string from Supabase; parse with `new Date(pebble.happened_at)`.

## Data flow

### Mount

1. `PathPage` mounts; `usePebbles()` resolves (cached or fetch).
2. `PathScreen` derives `entries`, initializes `focusedWeekStart = isoWeekStart(today)`.
3. `WeekRoll` mounts; first-run effect scrolls focused cairn to center with `behavior: 'instant'`.
4. `WeekRollCairn` for the focused entry plays its Rive cairn once.
5. `WeekPager` renders focused ± 1 pages. The focused `WeekPath` cascade runs.
6. `PathBottomBar` reads `useBounce()` / `useKarma()` independently; renders `—` until they resolve.
7. `PathBottomDock` is in the layout; `QuickPebbleEditor` is collapsed.

### Week swap

All four triggers write `focusedWeekStart` on `PathScreen`. Side effects:
- `WeekRoll` re-centers via smooth `scrollIntoView`.
- New `WeekRollCairn` plays; previous one stops.
- `WeekPager` animates `x` transform; the new neighbor is already mounted, so the slide is instant after paint.
- New `WeekPath` cascade runs on mount.

### Create pebble

1. User taps collapsed `QuickPebbleEditor` → expand.
2. User submits. `addPebble` resolves; `usePebbles` re-renders.
3. `QuickPebbleEditor` collapses; calls `onPebbleCreated(newId)`.
4. `PathScreen` sets `scrollTargetRef.current = newId`. The currently mounted `WeekPath` for the focused week sees the new row in its `entry.pebbles` and scrolls to it.
5. If the new pebble's week ≠ `focusedWeekStart` (retro create), the row will not be in any currently mounted `WeekPath`. `scrollTargetRef` remains set; the next time the user navigates to that week, the row scrolls into view on mount.

### Edit / delete

`PebblePeek` owns these flows today; we preserve them. After a mutation, `usePebbles` re-renders → `entries` recompute. If `focusedWeekStart` is no longer in `entries` (deleted-last-pebble-in-past-week, and that week is neither current nor next), fall back: pick the entry whose `weekStart` is closest to the prior focus; on tie, prefer the earlier one. This rule lives in a `useEffect` on `PathScreen` watching `entries` and `focusedWeekStart`.

## Edge cases

1. **Pebble at week boundary (Monday 00:00 local).** Belongs to the new week. Tested.
2. **DST forward/backward.** ISO week boundaries operate in local time; `isoWeekStart` doesn't normalize across DST. Tested with March and November Sunday→Monday rollovers.
3. **Year crossover** (late December in ISO week 1 of next year). The ISO key uses ISO year, not calendar year. Tested.
4. **Long roll initial mount jitter.** Resolved by `isFirstRunRef` skipping the smooth animation on the first effect run.
5. **Touch swipe on iOS Safari.** `dragDirectionLock` keeps vertical scroll inside `WeekPath` responsive.
6. **Reduced motion.** Cascade `staggerChildren` becomes 0; pager duration becomes 0; cairn `rive.play()` is skipped.
7. **Editor expanded while user taps a cairn.** Cairn tap is below the backdrop's `z-30`. The backdrop intercepts the click — no week swap during edit. If name is empty, click collapses; else click is ignored. (Today's blur-capture rule preserved.)
8. **Photo missing or fails to resolve.** `snaps[0]?.instants?.original` is `undefined` → row renders without photo, `rowHeight` returns the no-photo value.
9. **Empty corpus.** `entries = [currentWeek, nextWeek]`, both with empty `pebbles`. Roll shows 2 cairns. Left chevron disabled. Focused `WeekPath` renders `PathEmptyState` with CTA. `PathBottomBar` still shows `—` until karma/bounce resolve.

## Translations

New string keys in `apps/web/messages/en.json` and `fr.json`, in the `path` namespace:

| Key | EN | FR |
|---|---|---|
| `path.weekRoll.label` | `Weeks` | `Semaines` |
| `path.weekHeader.previous` | `Previous week` | `Semaine précédente` |
| `path.weekHeader.next` | `Next week` | `Semaine suivante` |
| `path.weekHeader.weekAria` | `Week {iso}, {count} pebbles` | `Semaine {iso}, {count} pebbles` |
| `path.empty.currentWeek.title` | `Fresh week` | `Nouvelle semaine` |
| `path.empty.currentWeek.cta` | `Carve a pebble` | `Tailler un pebble` |
| `path.bottomBar.label` | `Profile and stats` | `Profil et statistiques` |
| `path.bottomBar.profileAria` | `Profile` | `Profil` |

Existing `path.stats.bounce` / `path.stats.karma` / `path.profileAria` stay. Brand terms (`Pebbles`, `bounce`, `karma`) untranslated.

## Test plan

### Pure-helper assertions (`apps/web/scripts/test-week-roll.ts`)

Run with `tsx`. Asserts:

- `buildWeekRollEntries`:
  - Empty pebbles → `[currentWeek, nextWeek]`, both empty.
  - Single pebble in current week → `[currentWeek (1), nextWeek (0)]`.
  - Single pebble 3 weeks ago → `[oldWeek (1), currentWeek (0), nextWeek (0)]`, ascending.
  - Past week with 3 pebbles → ascending by `happened_at`.
  - Current week with 3 pebbles → descending by `happened_at`.
  - Monday 00:00 local boundary → pebble bucketed in the new week.
  - DST forward (last Sunday of March) and back (first Sunday of November) — neighbouring weeks compute correctly.
  - Late-December pebble in ISO week 1 of next year → bucketed to next ISO year.
- `rotation(positionIndex)`: 0 → -7, 1 → 4, 2 → -7, 3 → 4.
- `rowHeight(intensity, hasPhoto, positionIndex)`: full table from the row-heights section.
- `formatWeekRange(weekStart, today, locale)`:
  - Same year → no year suffix.
  - Different year → ` · YYYY` suffix appended.
  - `fr-FR` locale → French month names; structure unchanged.

The script's package.json entry: `"test:week-roll": "tsx scripts/test-week-roll.ts"`. CI is not added in this PR.

### Manual smoke (before opening PR)

- Cold load `/path` → current week focused, cascade visible, bottom dock visible, stats render.
- Click a cairn 3 weeks back → roll re-centers, header range updates, page slides, new cascade runs.
- Click chevrons; left and right disabled correctly at bounds.
- Press `←` / `→` while no input focused → weeks swap.
- Open the bottom "New pebble" bar → editor expands as overlay with backdrop. Click backdrop with empty name → collapses. Submit a pebble in the current week → collapses, new row appears, cascade re-runs, scrolls to the row.
- Submit a retro pebble dated 2 years ago → new entry appears in the roll left of current; focus stays on current; navigating to it shows the new row.
- Tap a pebble → `PebblePeek` opens; close → focus preserved.
- Light + dark parity for roll, header, rows, dock, bar.
- Reduce motion (system setting) → cascade, pager, cairn animations collapse to instant/static.
- Touch device: horizontal swipe inside the path body navigates weeks; vertical scrolling inside `WeekPath` still works.
- Mobile Safari: bottom dock respects safe-area inset; no rubber-band on horizontal swipe.

## Lint and build scope

Per CLAUDE.md task-size triage (Large):

- `npm run lint --workspace=apps/web`
- `npm run build --workspace=apps/web`
- Repo-root `npm run build` only if shared types/config change — for this work, none do.

## Arkaik map updates

Per the `arkaik` skill — apply to `apps/web/docs/arkaik/bundle.json` (or repo-root `docs/arkaik/bundle.json` per current layout):

- **Delete nodes:** `PebbleTimeline`, `WeekSectionHeader`, `PathProfileCard`.
- **Add nodes** under `PathPage`: `PathScreen`, `WeekRoll`, `WeekRollCairn`, `WeekHeader`, `WeekPager`, `WeekPath`, `PathBottomDock`, `PathBottomBar`.
- **Move node** `QuickPebbleEditor`: parent `PathPage` → `PathBottomDock`.
- **Add edges:** `PathBottomBar → /profile (push)`, `WeekPath → PebblePeek (open)`.
- No data-model/RPC/endpoint additions.

Bundle update ships in the same PR.

## Risks

- **`scrollIntoView({ inline: 'center', behavior: 'smooth' })` on iOS PWA.** Supported since iOS 14; verify in smoke on the PWA target.
- **Framer Motion drag eating vertical scroll on touch.** Mitigated by `dragDirectionLock`. Verify in smoke on mobile.
- **`QuickPebbleEditor` overlay vs `PebblePeek` sheet z-index.** Peek opens after editor closes in the create flow; smoke should catch any overlap. If it occurs, peek's portal should be promoted above the editor's overlay (`z-50`).
- **Reduced-motion + Rive.** Rive's runtime ignores `prefers-reduced-motion`; we opt out manually by gating `rive.play()` on the media query.
- **The legacy `pbbls-cairn.riv` is not the state-machine version iOS uses.** We bind `play()`/`stop()` only. Visual parity with iOS's selected-state stroke color is not in scope; the cairn renders with its default colors. A follow-up can port `pbbls-cairn-states.riv` to web once it's exported.

## Branch / PR

- **Branch:** `feat/421-weeks-roll-web`
- **PR title:** `feat(ui): align /path with iOS weeks roll (#421)`
- **PR body opens:** `Resolves #421`
- **Labels (inherit from issue 421):** `core`, `feat`, `ui`, `web`
- **Milestone (inherit):** `M27 · Align Webapp with iOS`

Confirm labels and milestone with the user when opening the PR.
