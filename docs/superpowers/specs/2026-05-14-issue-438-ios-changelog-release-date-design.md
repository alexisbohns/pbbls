# iOS Lab — release-date sort and timeline visual (issue #438)

## Context

GitHub issue [#438](https://github.com/Bohns/pbbls/issues/438) (label: `feat`, `ios`; milestone M31 · Lab Improvements).

Web PR [#384](https://github.com/Bohns/pbbls/pull/384) introduced `logs.released_at` and the web changelog already sorts by it (`released_at desc nulls last, published_at desc`) and displays it on each shipped row via `apps/web/components/lab/LogTimeline.tsx:38`. The iOS Lab tab still sorts the changelog by `published_at` (the "updated" date), does not surface the release date anywhere, and renders shipped features as compact `LogRow`s with no visual treatment.

The DB view `v_logs_with_counts` already exposes `released_at` (migration `20260514000001_logs_released_at.sql`). No schema work is required.

## Acceptance (from the issue)

- On iOS, opening the Lab page shows the changelog displaying the release date (instead of the updated date), with logs sorted by release date, most recent first.
- On the webapp, the Changelog full page shows the release date and is sorted by it. *(Already shipped — verified in `logs-api.ts:93` and `LogTimeline.tsx:38`.)*

## Scope

iOS only. Beyond the strict letter of the issue (changelog), the visual treatment is extended to the **in-progress** and **backlog** sections of the Lab tab so the timeline pattern is consistent across statuses — matching the web's `LogTimeline` which already handles all three modes with different icons (confirmed with the user during brainstorming).

## Design

### 1. Model — `apps/ios/Pebbles/Features/Lab/Models/Log.swift`

Add a release-date field:

- New stored property: `let releasedAt: Date?`
- New coding key: `case releasedAt = "released_at"`
- Pass through in `withAdjustedCount(by:)` so optimistic reaction updates preserve the field.

### 2. Service — `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift`

Change `changelog(limit:)` ordering to mirror the web:

```swift
.order("released_at", ascending: false, nullsFirst: false)
.order("published_at", ascending: false)
```

`announcements`, `initiatives`, `backlog`, and reaction methods are unchanged.

### 3. New component — `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift`

A generic timeline view for feature-status sections. Replaces `LogRow` everywhere it's used today (changelog, in-progress, backlog).

```swift
enum Mode {
    case changelog
    case inProgress
    case backlog
}
```

For each log:

- **Icon column** (leading): SF Symbol per mode.
  - `.changelog`: `checkmark.circle`, tint `Color.pebblesAccent` (the iOS brand token, equivalent to the web's `text-primary`).
  - `.inProgress`: `circle.dotted`, tint `Color.pebblesMutedForeground`.
  - `.backlog`: `circle.dashed`, tint `Color.pebblesMutedForeground`.
  - Below the icon, a 1pt-wide line in `Color.pebblesBorder` filling the remaining height — hidden for the last entry. This matches the web's `!isLast && <div className="my-1 w-px flex-1 bg-border" />`.
- **Content column**: VStack(leading)
  - `.changelog` only: muted date label using `Date.FormatStyle(date: .long, time: .omitted)` — locale-aware (renders "14 mai 2026" / "May 14, 2026") and does **not** require a `Localizable.xcstrings` entry. Source: `log.releasedAt ?? log.publishedAt`.
  - Title: `log.title(for: locale)`, `.font(.body)`, foreground `Color.pebblesForeground`.
  - Summary: `log.summary(for: locale)`, `.font(.footnote)`, foreground `Color.pebblesMutedForeground`, line limit 3.
- **Trailing slot**: a `@ViewBuilder` closure (`Trailing: View`), same pattern as the existing `LogRow`, so the backlog can attach its `ReactionButton`. Default-init overload for the empty case (`Trailing == EmptyView`).

Rendering inside `List`:

- Each log entry is its own `List` row to keep List-managed padding/inset behavior.
- The connector line is drawn inside each row so it visually links across rows.
- `.listRowSeparator(.hidden)` on each entry to keep the line continuous.
- `.listRowBackground(Color.pebblesListRow)` to match the existing rows.

### 4. Consumer — `apps/ios/Pebbles/Features/Lab/LabView.swift`

Replace the three `ForEach { LogRow }` blocks:

- **Changelog section**: `LogTimeline(mode: .changelog, logs: changelog)` — no trailing.
- **In-progress section**: `LogTimeline(mode: .inProgress, logs: initiatives)` — no trailing.
- **Backlog section**: `LogTimeline(mode: .backlog, logs: backlog) { log in ReactionButton(...) }`.

The "See all" `NavigationLink` rows under Changelog and Backlog remain their own list rows — unchanged.

### 5. Consumer — `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift`

Replace its `ForEach { LogRow }` with `LogTimeline(mode: mode == .changelog ? .changelog : .backlog, logs: logs)`. Backlog mode passes the reaction trailing closure (same logic as today's row).

### 6. Cleanup — `apps/ios/Pebbles/Features/Lab/Components/LogRow.swift`

After steps 4–5 land, `LogRow` has no remaining call sites. Per project convention (no orphan files, no backwards-compat shims), delete the file. Announcements use `AnnouncementRow`, which is untouched.

### Project YAML

`LogTimeline.swift` will be picked up automatically by the existing `Lab/Components/**` glob in `project.yml`. After adding the new file, run `npm run generate --workspace=@pbbls/ios` to regenerate the `.xcodeproj`. After deleting `LogRow.swift`, regenerate again.

## What stays the same

- Reaction logic (optimistic insert/remove, revert on error) in both `LabView` and `LogListView`.
- Announcement rendering (`AnnouncementRow`, detail view).
- Data loading orchestration, fullscreen error gating.
- `Log` model accessors (`title(for:)`, `summary(for:)`, `body(for:)`).
- Locale handling — the timeline relies on the existing `@Environment(\.locale)`.

## Out of scope

- Schema or RPC changes (`released_at` already exists and is exposed).
- Web changelog (already shipped per `logs-api.ts` and `LogTimeline.tsx`).
- Admin-side edits to the release-date input (already shipped in PR #384).
- Localization catalog additions (the only new user-facing surface is a SwiftUI-localized `Date`, which auto-localizes via `Locale.current` and bypasses `Localizable.xcstrings`).
- Reordering / restyling `AnnouncementRow`.
- Arkaik bundle: no screens, routes, models, or endpoints change — skip the Arkaik update.

## Verification (manual)

No tests in this codebase yet (per `AGENTS.md`). Manual passes on a connected device or simulator:

1. Open the Lab tab while signed in. Changelog, In progress, and Backlog sections render with the timeline icons and a continuous connecting line.
2. Each changelog row shows a muted date above the title. The date is `released_at` when present, falling back to `published_at`.
3. Tap "See all" under Changelog — the full list shows the same timeline treatment and ordering.
4. Switch the device language between EN and FR — date format updates ("May 14, 2026" ↔ "14 mai 2026"). Existing log strings stay localized.
5. Confirm sort: in the admin, set a backlog feature's release date to a past timestamp and ship it, then refresh iOS — it appears in the correct position by release date, not by `published_at`.
6. Tap an upvote in the Backlog section — count updates optimistically; if the network call fails (offline test), the update reverts.
7. Open `Localizable.xcstrings` in Xcode — no new entries in `New` or `Stale` state.
8. Lint the affected workspace: `npm run lint --workspace=@pbbls/ios` (or the iOS equivalent — currently iOS has no lint step beyond `xcodegen generate` succeeding).

## File touch list

- `apps/ios/Pebbles/Features/Lab/Models/Log.swift` — add field + coding key.
- `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift` — change `changelog` ordering.
- `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift` — new file.
- `apps/ios/Pebbles/Features/Lab/LabView.swift` — swap rows for timelines.
- `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift` — swap rows for timelines.
- `apps/ios/Pebbles/Features/Lab/Components/LogRow.swift` — delete.
- `apps/ios/Pebbles.xcodeproj/**` — regenerated by `xcodegen` (git-ignored, no manual edit).
