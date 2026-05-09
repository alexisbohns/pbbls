# iOS Path: week groups + emotion-tinted pebble rows

**Issue:** #374
**Depends on:** #375 (shipped — `EmotionPaletteService`, `EmotionPalette`, `Color(hex:)` 6/8-digit support)
**Date:** 2026-05-09

## Goal

Redesign the iOS Path screen so that pebbles are grouped into per-week cards and each pebble row is rendered using the row's emotion-category palette. Out of scope: any other element shown in the design mockup (profile avatar, bounce/karma counters, "What's up Alexis?" record-prompt card).

## Visual specification

Reference mockup: GitHub attachment `2290a890-a895-41a1-a3ca-b4a8562e34e8` on issue #374.

### Week card

- One white rounded card per ISO week (`Calendar(identifier: .iso8601)`).
- Cards sorted by `(yearForWeekOfYear, weekOfYear)` descending — newest week first.
- Card contents: a centered "Week N" title row followed by one pebble row per pebble in that week.
- Container is the standard `List` `.insetGrouped` style (default when sections are present); each `Section` becomes a rounded card. The screen-level `.pebblesScreen()` modifier already paints the page background (`Color.pebblesBackground`) and hides the system list-content background.
- Default row separators are kept for this iteration. Hiding them is explicitly out of scope.

### Week header row (first row of each card)

- Single centered `Text` rendering the localized key `path.week.title` (en: `"Week %lld"`, fr: `"Semaine %lld"`).
- Font: `Ysabeau-SemiBold`, size 18.
- Foreground: `Color.pebblesForeground`.
- Vertical padding: 8pt above and below to read as a card title rather than a list row.
- No tap target.

### Pebble row (`PathPebbleRow`)

Layout: `HStack(spacing: 12)` of thumbnail + 2-line text VStack.

**Thumbnail (56×56, RoundedRectangle radius 12):**

- Background fill: `palette.surface` in both schemes.
- Glyph: `PebbleRenderView(svg:strokeColor:)` rendered at ~40×40 inside, with stroke `palette.secondaryHex` in both schemes.
- Fallback when palette is unavailable for the row's emotion: `Color.pebblesAccent` for the background, `Color.pebblesAccentHex` for the glyph stroke. Same defensive pattern as today's `PebbleRow`.
- When `pebble.renderSvg` is nil: a plain `RoundedRectangle` filled with `Color.secondary.opacity(0.15)` (same fallback as today's `PebbleRow`).

**Text VStack (alignment: .leading, spacing: 4):**

- **Line 1 — pebble name.** `Text(pebble.name)`, font `Ysabeau-SemiBold` size 17, foreground = `palette.primary` in light / `palette.light` in dark. Fallback to `Color.pebblesForeground` when palette is unavailable.
- **Line 2 — date and time, uppercased.** Foreground is the same color used by line 1, with `.opacity(0.5)`. Font `.caption`, `.tracking(1.0)`, `.textCase(.uppercase)`.

  Format string built from two `Date.FormatStyle` calls:

  ```swift
  let date = pebble.happenedAt.formatted(.dateTime.weekday(.wide).day().month(.wide))
  let time = pebble.happenedAt.formatted(.dateTime.hour().minute())
  return "\(date) · \(time)"
  ```

  Locale-driven: fr-FR users see `dimanche 5 avril · 10:00` (visually uppercased to `DIMANCHE 5 AVRIL · 10:00`); en-US users see 12-hour time and a localized weekday/month order. The middle-dot separator is a literal `·` because `Date.FormatStyle` does not produce one between independent components.

**Behavior:**

- Wrap in `Button(action: onTap)` with `.buttonStyle(.plain)`.
- Same `contextMenu { Button(role: .destructive, action: onDelete) { Label("Delete", ...) } }` as today's `PebbleRow`.
- Same `(onTap, onDelete)` callback contract.

## Architecture

### File layout

**New files:**

- `apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift` — free function helper, mirroring the existing `Features/Profile/Views/GroupPebblesByMonth.swift` pattern.
- `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift` — emotion-tinted row used only in Path.
- `apps/ios/Pebbles/Features/Path/Components/WeekSectionHeader.swift` — centered Ysabeau "Week N" row, used as the first row of each card.
- `apps/ios/PebblesTests/GroupPebblesByISOWeekTests.swift` — Swift Testing suite for the grouping helper.

**Modified files:**

- `apps/ios/Pebbles/Features/Path/PathView.swift` — switch from a flat `Section { ForEach(pebbles) }` to one `Section` per `WeekGroup`. The "Record a pebble" button section, toolbar, sheets, dialogs, and `load()` / `delete()` logic are unchanged.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — one new key `path.week.title` with en + fr values.

**Untouched:**

- `Components/PebbleRow.swift` — still consumed by `SoulDetailView` and `CollectionDetailView`. Issue #374 only changes Path; modifying `PebbleRow` would silently regress the other two screens.
- `EmotionPaletteService`, `EmotionPalette`, `EmotionWithPalette`.
- The Supabase query in `PathView.load()` — same `select` string, same RPC, same ordering.
- `project.yml` — no new build settings; the new files are already covered by the existing source globs.

### `groupPebblesByISOWeek` helper

Mirror the shape of the existing `groupPebblesByMonth(_:calendar:)`:

```swift
/// Groups pebbles by their ISO 8601 week, returning `(weekStart, pebbles)`
/// pairs ordered descending by week. The `weekStart` is the first instant
/// of the week's Monday in the provided calendar.
///
/// - Caller passes `Calendar(identifier: .iso8601)` — only that calendar
///   gives Mon-start, week-1-contains-first-Thursday semantics
///   consistently. `Calendar.current` would vary by user locale.
/// - Within a group, input order is preserved — callers typically pass
///   pebbles already sorted descending by `happenedAt`.
func groupPebblesByISOWeek(
    _ pebbles: [Pebble],
    calendar: Calendar
) -> [(key: Date, value: [Pebble])]
```

**Why `Calendar(identifier: .iso8601)` specifically:** it is the only standard calendar where `.weekOfYear` and `.yearForWeekOfYear` give Mon-start, week-1-contains-first-Thursday behavior consistently. `Calendar.current` would vary by user locale (Sun-start in en-US). The caller (`PathView`) passes the ISO 8601 calendar; the helper is generic over the calendar so tests can inject it.

**Why bucket key uses `.yearForWeekOfYear` and `.weekOfYear`:** at year boundaries, a date like `2025-12-29` (Mon) belongs to ISO week 1 of `yearForWeekOfYear = 2026`. Bucketing by `.year` + `.month` (as `groupPebblesByMonth` does) would split such a week incorrectly across two cards. Internally, we compute the bucket key by reconstructing the date from `[.yearForWeekOfYear, .weekOfYear, .weekday]` components, fixed at weekday 2 (Monday in ISO 8601).

### Data flow in `PathView`

- Replace `@State private var pebbles: [Pebble]` with `@State private var pebbles: [Pebble] = []` (kept) plus a computed `groupedPebbles: [(key: Date, value: [Pebble])]` property.
- The Supabase query is unchanged. `pebbles` already arrives sorted descending by `happened_at` from the `.order(...)` clause.
- The view passes `Calendar(identifier: .iso8601)` to the grouping helper. That choice lives in the view, not the helper.
- Render structure:

  ```swift
  List {
      Section {
          Button { isPresentingCreate = true } label: {
              Label("Record a pebble", systemImage: "plus.circle.fill")
                  .font(.headline)
          }
          .listRowBackground(Color.pebblesListRow)
      }

      ForEach(groupedPebbles, id: \.key) { group in
          Section {
              WeekSectionHeader(weekStart: group.key, calendar: isoCalendar)
                  .listRowBackground(Color.pebblesListRow)

              ForEach(group.value) { pebble in
                  PathPebbleRow(
                      pebble: pebble,
                      onTap: { selectedPebbleId = pebble.id },
                      onDelete: { pendingDeletion = pebble }
                  )
                  .listRowBackground(Color.pebblesListRow)
              }
          }
      }
  }
  ```

- `WeekSectionHeader` reads `weekOfYear` from the calendar passed in: `calendar.component(.weekOfYear, from: weekStart)`.
- Empty state (`pebbles.isEmpty`): only the "Record a pebble" card renders. No empty-state copy is added.
- Loading / error: still gated by `isLoading` / `loadError` outside the list. Unchanged.

## Localization

- New key `path.week.title` in `Localizable.xcstrings` with format `"Week %lld"` (en) / `"Semaine %lld"` (fr).
- Rendered via `Text("path.week.title \(weekOfYear)")` (or `LocalizedStringResource`) so the week number formats locale-correctly.
- Per AGENTS.md: open the catalog before opening the PR; confirm both en and fr columns are filled and no rows are in `New` or `Stale` state.
- No other user-facing strings change.

## Testing

`apps/ios/PebblesTests/GroupPebblesByISOWeekTests.swift`, Swift Testing (`@Suite`, `@Test`, `#expect`). Mirrors the shape of the existing `GroupPebblesByMonthTests`. All tests inject `Calendar(identifier: .iso8601)` with `TimeZone(identifier: "UTC")` so they are deterministic on any machine.

1. Empty input returns an empty array.
2. Two pebbles inside the same ISO week land in one group; pebbles from adjacent calendar weeks land in two separate groups.
3. **Year-boundary case:** pebbles dated `2025-12-29T10:00:00Z` (Mon) and `2026-01-02T10:00:00Z` (Fri) both fall in ISO week 1 of `yearForWeekOfYear = 2026` — they land in a single group. Pins down the `.yearForWeekOfYear` usage.
4. Groups are ordered descending by `key` (newest week first).
5. Within a group, input order is preserved (mirrors the month helper's contract — caller is responsible for sort).
6. The `key` of a group is the Monday `00:00:00` of that ISO week.

No UI tests, no screenshot tests (none in the repo today).

## Manual smoke checklist (for the PR)

- Path renders week cards in light and dark mode.
- Pebble row name color reads correctly in both schemes (primary in light, light in dark).
- Time line is uppercased and at 50% opacity in both schemes.
- Long-press → Delete still works; tapping a row still opens the detail sheet.
- French locale shows `Semaine 19` and the date line uppercases to `DIMANCHE 5 AVRIL · 10:00`.
- A pebble dated `2025-12-31` and one dated `2026-01-02` both appear under a single "Week 1" card (year-boundary check).
- Falling back to the accent colour when the palette cache is missing the row's emotion is visually acceptable.

## Out of scope

- Avatar, bounce, and karma header shown in the mockup — separate work.
- "What's up Alexis?" record-prompt card — current `Label("Record a pebble")` button stays.
- Hiding default `List` row separators inside week cards.
- Dark-mode tuning of `surface` if it reads poorly — deferred to a follow-up.
- `PebbleRow` (used by Soul and Collection details) — untouched.
- Arkaik bundle update — no new screen, route, data model, or endpoint is introduced; the architecture map does not change.
