# iOS Path Week Groups + Emotion-Tinted Rows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group pebbles in the iOS Path screen into per-ISO-week white rounded cards, and replace the row treatment with an emotion-palette-tinted variant (surface bg + secondary glyph + primary/light name + uppercase date+time at 50% opacity). Resolves issue #374.

**Architecture:** Add a free-function helper `groupPebblesByISOWeek(_:calendar:)` that mirrors the existing `groupPebblesByMonth(_:calendar:)` shape. Add two new view files (`PathPebbleRow`, `WeekSectionHeader`) used only by `PathView`. The existing `PebbleRow` stays untouched — `SoulDetailView` and `CollectionDetailView` continue to use it. `PathView` keeps its `List`, `Section`s, sheets, dialogs, toolbar, and Supabase query as today; only the inner row content changes.

**Tech Stack:** Swift 5.9, SwiftUI, iOS 17, Swift Testing (`@Suite`/`@Test`/`#expect`), Supabase Swift, Ysabeau-SemiBold (already bundled), `EmotionPaletteService` (already in environment via `RootView`).

**Spec:** [`docs/superpowers/specs/2026-05-09-ios-week-groups-emotion-rows-design.md`](../specs/2026-05-09-ios-week-groups-emotion-rows-design.md)

**Branch:** `feat/374-ios-week-groups-emotion-rows` (already created off `origin/main`).

**Conventions to follow:**

- Conventional commits, lowercase, no period: `type(scope): description (#374)`.
- Each task ends with one commit. The PR should be a clean, reviewable sequence of small commits.
- After every code change, run **only** the iOS test suite — no node lint/build is needed for this issue (no web or `packages/*` changes).
- Use the existing patterns: free-function grouping, `@Environment(EmotionPaletteService.self)`, `Color.pebblesAccent` defensive fallback, Ysabeau via `.font(.custom("Ysabeau-SemiBold", size: …))`.

---

## Task 1: Add the ISO-week grouping helper (TDD)

Mirror `Features/Profile/Views/GroupPebblesByMonth.swift` and its test suite.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift`
- Create: `apps/ios/PebblesTests/GroupPebblesByISOWeekTests.swift`
- Modify: `apps/ios/project.yml` — none. Source globs already cover `Pebbles/**` and `PebblesTests/**`.

- [ ] **Step 1.1: Write the test suite (failing)**

Create `apps/ios/PebblesTests/GroupPebblesByISOWeekTests.swift` with the following content:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("groupPebblesByISOWeek")
struct GroupPebblesByISOWeekTests {

    /// ISO 8601 calendar pinned to UTC so tests are deterministic regardless
    /// of the machine running them.
    private var calendar: Calendar {
        var iso = Calendar(identifier: .iso8601)
        iso.timeZone = TimeZone(identifier: "UTC")!
        return iso
    }

    private func date(_ iso: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)!
    }

    private func pebble(_ happened: String) throws -> Pebble {
        let json = Data("""
        { "id": "\(UUID().uuidString)", "name": "p", "happened_at": "\(happened)" }
        """.utf8)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let container = try dec.singleValueContainer()
            let iso = try container.decode(String.self)
            return formatter.date(from: iso)!
        }
        return try decoder.decode(Pebble.self, from: json)
    }

    @Test("empty input → empty output")
    func emptyInput() {
        let result = groupPebblesByISOWeek([], calendar: calendar)
        #expect(result.isEmpty)
    }

    @Test("pebbles in the same ISO week group together")
    func sameWeek() throws {
        // Both Monday 2026-04-27 and Sunday 2026-05-03 are ISO week 18 of 2026.
        let monday = try pebble("2026-04-27T10:00:00Z")
        let sunday = try pebble("2026-05-03T22:00:00Z")
        let result = groupPebblesByISOWeek([sunday, monday], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }

    @Test("adjacent ISO weeks split into separate groups")
    func adjacentWeeks() throws {
        // 2026-05-03 (Sun) is week 18; 2026-05-04 (Mon) is week 19.
        let sun = try pebble("2026-05-03T10:00:00Z")
        let mon = try pebble("2026-05-04T10:00:00Z")
        let result = groupPebblesByISOWeek([mon, sun], calendar: calendar)
        #expect(result.count == 2)
    }

    @Test("year boundary: 2025-12-29 and 2026-01-02 share ISO week 1 of 2026")
    func yearBoundary() throws {
        // Jan 1 2026 is a Thursday → ISO week 1 of 2026 runs Mon 2025-12-29
        // through Sun 2026-01-04. Bucketing by `.year` would split these.
        let lateDecember = try pebble("2025-12-29T10:00:00Z")
        let earlyJanuary = try pebble("2026-01-02T10:00:00Z")
        let result = groupPebblesByISOWeek(
            [earlyJanuary, lateDecember],
            calendar: calendar
        )
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }

    @Test("groups ordered descending by key")
    func descendingOrder() throws {
        let week17 = try pebble("2026-04-20T10:00:00Z") // Mon week 17
        let week18 = try pebble("2026-04-27T10:00:00Z") // Mon week 18
        let week19 = try pebble("2026-05-04T10:00:00Z") // Mon week 19
        let result = groupPebblesByISOWeek(
            [week17, week18, week19],
            calendar: calendar
        )
        #expect(result.count == 3)
        #expect(result[0].key > result[1].key)
        #expect(result[1].key > result[2].key)
    }

    @Test("input order within a group is preserved")
    func preservesInputOrder() throws {
        let later  = try pebble("2026-04-30T10:00:00Z")
        let earlier = try pebble("2026-04-27T10:00:00Z")
        let result = groupPebblesByISOWeek([later, earlier], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value[0].happenedAt == later.happenedAt)
        #expect(result[0].value[1].happenedAt == earlier.happenedAt)
    }

    @Test("group key is the Monday 00:00:00 of the ISO week")
    func keyIsWeekStart() throws {
        let wednesday = try pebble("2026-04-29T15:30:00Z")
        let result = groupPebblesByISOWeek([wednesday], calendar: calendar)
        #expect(result.count == 1)
        let comps = calendar.dateComponents(
            [.yearForWeekOfYear, .weekOfYear, .weekday, .hour, .minute, .second],
            from: result[0].key
        )
        #expect(comps.yearForWeekOfYear == 2026)
        #expect(comps.weekOfYear == 18)
        #expect(comps.weekday == 2)   // Monday in ISO 8601 (1 = Sunday → 2 = Monday)
        #expect(comps.hour == 0)
        #expect(comps.minute == 0)
        #expect(comps.second == 0)
    }
}
```

- [ ] **Step 1.2: Run the tests to verify they fail to compile**

Run from the iOS app folder:

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/GroupPebblesByISOWeekTests test 2>&1 | tail -30
```

If the workspace doesn't exist (xcodegen has not been run on this machine):

```bash
cd apps/ios && xcodegen generate && cd -
```

Expected: build fails with `error: cannot find 'groupPebblesByISOWeek' in scope` from the test file.

- [ ] **Step 1.3: Implement the helper (minimal)**

Create `apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift`:

```swift
import Foundation

/// Groups pebbles by their ISO 8601 week, returning `(weekStart, pebbles)`
/// pairs ordered descending by week. The `weekStart` is the first instant
/// of that week's Monday in the provided calendar.
///
/// - Caller passes `Calendar(identifier: .iso8601)` — only that calendar
///   gives Mon-start, week-1-contains-first-Thursday semantics
///   consistently. `Calendar.current` would vary by user locale.
/// - Within a group, input order is preserved — callers typically pass
///   pebbles already sorted descending by `happenedAt`.
/// - The bucket key is reconstructed from `[.yearForWeekOfYear,
///   .weekOfYear, .weekday]` rather than `[.year, .month]`, so dates that
///   span a calendar-year boundary while sharing an ISO week (e.g.
///   2025-12-29 and 2026-01-02 both land in ISO week 1 of 2026) bucket
///   together.
func groupPebblesByISOWeek(
    _ pebbles: [Pebble],
    calendar: Calendar
) -> [(key: Date, value: [Pebble])] {
    let buckets = Dictionary(grouping: pebbles) { pebble -> Date in
        let comps = calendar.dateComponents(
            [.yearForWeekOfYear, .weekOfYear],
            from: pebble.happenedAt
        )
        var weekStart = DateComponents()
        weekStart.yearForWeekOfYear = comps.yearForWeekOfYear
        weekStart.weekOfYear = comps.weekOfYear
        weekStart.weekday = 2 // Monday in ISO 8601
        return calendar.date(from: weekStart) ?? pebble.happenedAt
    }
    return buckets
        .map { (key: $0.key, value: $0.value) }
        .sorted { $0.key > $1.key }
}
```

- [ ] **Step 1.4: Run the tests to verify they pass**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/GroupPebblesByISOWeekTests test 2>&1 | tail -20
```

Expected: `Test Suite 'GroupPebblesByISOWeekTests' passed` with 7 tests passing.

- [ ] **Step 1.5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift \
        apps/ios/PebblesTests/GroupPebblesByISOWeekTests.swift
git commit -m "feat(ios): add groupPebblesByISOWeek helper (#374)"
```

---

## Task 2: Add the `WeekSectionHeader` view

The "Week 19" / "Semaine 19" centered title that becomes the first row of each week card. No tests — pure rendering, exercised at the manual-smoke step.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/WeekSectionHeader.swift`

- [ ] **Step 2.1: Create the file**

```swift
import SwiftUI

/// Centered "Week N" title rendered as the first list row inside each
/// week card on the Path screen. The week number is read from the
/// supplied `weekStart` Date using the supplied calendar — callers pass
/// the same `Calendar(identifier: .iso8601)` they used to bucket.
///
/// The localized source key is `"Week %lld"`, with `"Semaine %lld"` as
/// the French translation. Xcode auto-extracts the source key on every
/// build because `SWIFT_EMIT_LOC_STRINGS = YES`; the FR value is filled
/// in `Localizable.xcstrings`.
struct WeekSectionHeader: View {
    let weekStart: Date
    let calendar: Calendar

    var body: some View {
        let weekOfYear = calendar.component(.weekOfYear, from: weekStart)
        Text("Week \(weekOfYear)")
            .font(.custom("Ysabeau-SemiBold", size: 18))
            .foregroundStyle(Color.pebblesForeground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
    }
}

#Preview {
    var iso = Calendar(identifier: .iso8601)
    iso.timeZone = TimeZone(identifier: "UTC")!
    let mon = iso.date(from: DateComponents(
        timeZone: TimeZone(identifier: "UTC"),
        yearForWeekOfYear: 2026, weekOfYear: 19, weekday: 2
    ))!
    return List {
        Section {
            WeekSectionHeader(weekStart: mon, calendar: iso)
                .listRowBackground(Color.pebblesListRow)
        }
    }
}
```

- [ ] **Step 2.2: Verify it builds**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 2.3: Add the FR translation to `Localizable.xcstrings`**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode after the build (the build will have auto-inserted a `Week %lld` entry with `state: new`). In the Xcode catalog editor:

- Select the row for `Week %lld`.
- In the `fr` column, paste `Semaine %lld`.
- Confirm both `en` and `fr` are marked `translated`, not `new` or `stale`.

If you prefer to edit the JSON directly: locate the `"Week %lld"` key inserted by the build and add a `fr` localization next to the `en` one:

```json
"Week %lld": {
  "comment": "...auto-generated...",
  "isCommentAutoGenerated": true,
  "localizations": {
    "en": {
      "stringUnit": { "state": "translated", "value": "Week %lld" }
    },
    "fr": {
      "stringUnit": { "state": "translated", "value": "Semaine %lld" }
    }
  }
}
```

- [ ] **Step 2.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/WeekSectionHeader.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): add WeekSectionHeader for path week cards (#374)"
```

---

## Task 3: Add the `PathPebbleRow` view

The new emotion-tinted row used only by `PathView`. The existing `Components/PebbleRow.swift` stays as-is for `SoulDetailView` and `CollectionDetailView`.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift`

- [ ] **Step 3.1: Create the file**

```swift
import SwiftUI

/// Path-specific pebble row. Renders the row using the row's
/// emotion-category palette per spec
/// `docs/superpowers/specs/2026-05-09-ios-week-groups-emotion-rows-design.md`:
///
/// - Thumbnail: 56×56, RoundedRectangle radius 12, fill `palette.surface`,
///   glyph stroked in `palette.secondaryHex` (both schemes).
/// - Name: Ysabeau-SemiBold 17, foreground `palette.primary` in light /
///   `palette.light` in dark.
/// - Date+time: uppercased, tracked `.caption`, foreground = name color
///   at 50% opacity. Built from two `Date.FormatStyle` calls joined by a
///   literal middle-dot separator.
///
/// `PebbleRow` (in `Components/PebbleRow.swift`) is the canonical row used
/// by `SoulDetailView` and `CollectionDetailView`; do not generalize this
/// row — keep them separate so a Path tweak cannot regress the other two.
struct PathPebbleRow: View {
    let pebble: Pebble
    let onTap: () -> Void
    let onDelete: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme

    private static let thumbnailSize: CGFloat = 56
    private static let glyphInset: CGFloat = 8

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name)
                        .font(.custom("Ysabeau-SemiBold", size: 17))
                        .foregroundStyle(nameColor)
                    Text(formattedDateTime)
                        .font(.caption)
                        .tracking(1.0)
                        .textCase(.uppercase)
                        .foregroundStyle(nameColor.opacity(0.5))
                }
            }
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(role: .destructive, action: onDelete) {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(thumbnailFill)
            if let svg = pebble.renderSvg {
                PebbleRenderView(svg: svg, strokeColor: glyphStrokeHex)
                    .padding(Self.glyphInset)
            }
        }
        .frame(width: Self.thumbnailSize, height: Self.thumbnailSize)
    }

    private var palette: EmotionPalette? {
        guard let emotionId = pebble.emotion?.id else { return nil }
        return palettes.palette(for: emotionId)
    }

    private var thumbnailFill: Color {
        palette?.surface ?? Color.pebblesAccent.opacity(0.15)
    }

    private var glyphStrokeHex: String? {
        // 6-digit hex — same trim rule as `EmotionPalette.strokeHex(for:)`,
        // because `PebbleRenderView` injects the value as text into the
        // raw SVG markup and SVGView does not parse the 8-digit form
        // reliably.
        guard let palette else { return Color.pebblesAccentHex }
        let hex = palette.secondaryHex
        return hex.count == 9 ? String(hex.prefix(7)) : hex
    }

    private var nameColor: Color {
        guard let palette else { return Color.pebblesForeground }
        return colorScheme == .dark ? palette.light : palette.primary
    }

    private var formattedDateTime: String {
        let date = pebble.happenedAt.formatted(
            .dateTime.weekday(.wide).day().month(.wide)
        )
        let time = pebble.happenedAt.formatted(.dateTime.hour().minute())
        return "\(date) · \(time)"
    }
}

#Preview {
    let supabase = SupabaseService()
    return List {
        Section {
            PathPebbleRow(
                pebble: Pebble(
                    id: UUID(),
                    name: "Sample pebble",
                    happenedAt: Date(),
                    renderSvg: nil,
                    emotion: nil
                ),
                onTap: {},
                onDelete: {}
            )
            .listRowBackground(Color.pebblesListRow)
        }
    }
    .environment(EmotionPaletteService(client: supabase.client))
}
```

- [ ] **Step 3.2: Verify it builds**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift
git commit -m "feat(ios): add PathPebbleRow with palette-tinted treatment (#374)"
```

---

## Task 4: Wire `PathView` to render week cards

Replace the flat `Section("Path") { ForEach(pebbles) { PebbleRow(...) } }` with one `Section` per ISO-week group.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift` (lines 90–113 and `load()`)

- [ ] **Step 4.1: Add the ISO calendar and grouped-pebbles state**

Edit `apps/ios/Pebbles/Features/Path/PathView.swift`. Add a static-ish ISO calendar and a computed grouping. Locate the property block at the top of the struct:

```swift
struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingOnboarding = false
    @State private var pendingDeletion: Pebble?
    @State private var deleteError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")
```

Insert a new computed `isoCalendar` property and a `groupedPebbles` property right after `logger`:

```swift
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    /// ISO 8601 calendar — Mon-start, week-1-contains-first-Thursday.
    /// Locale-independent so all users see the same week boundaries.
    private var isoCalendar: Calendar {
        Calendar(identifier: .iso8601)
    }

    private var groupedPebbles: [(key: Date, value: [Pebble])] {
        groupPebblesByISOWeek(pebbles, calendar: isoCalendar)
    }
```

- [ ] **Step 4.2: Replace the Path section with week-group sections**

In the same file, locate the inner `else { List { ... } }` block (currently lines 90–113):

```swift
        } else {
            List {
                Section {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Label("Record a pebble", systemImage: "plus.circle.fill")
                            .font(.headline)
                    }
                    .listRowBackground(Color.pebblesListRow)
                }

                Section("Path") {
                    ForEach(pebbles) { pebble in
                        PebbleRow(
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

Replace the entire `List { ... }` body with:

```swift
        } else {
            List {
                Section {
                    Button {
                        isPresentingCreate = true
                    } label: {
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
        }
```

Leave everything else in `PathView.swift` unchanged: the `task`, `sheet(isPresented:)`, `sheet(item:)`, `fullScreenCover`, `confirmationDialog`, `alert`, `load()`, and `delete(_:)` are all kept as-is. The Supabase `select(...)` string is unchanged — palette comes from `EmotionPaletteService`, not the row's data.

- [ ] **Step 4.3: Verify the project still builds**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4.4: Run the full iOS test suite**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -30
```

Expected: all suites pass, including the new `GroupPebblesByISOWeekTests`.

- [ ] **Step 4.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ios): render path as ISO-week cards with palette rows (#374)"
```

---

## Task 5: Manual smoke pass and PR

`PathPebbleRow` and `WeekSectionHeader` are pure rendering — the manual checklist is what proves correctness.

**Files:** none modified.

- [ ] **Step 5.1: Boot the app in the simulator and seed at least two weeks of pebbles**

If you have an existing dev account with pebbles spanning multiple weeks, sign in. Otherwise create three or four pebbles dated across two ISO weeks (use the in-app picker to set `happened_at`).

- [ ] **Step 5.2: Walk the manual checklist from the spec**

For each item, confirm it visually before checking the box:

- Path renders one white rounded card per ISO week.
- Each card's first row is centered "Week N" / "Semaine N" in Ysabeau.
- Pebble row thumbnail is filled with the emotion's surface tone; glyph strokes use the emotion's secondary tone.
- Pebble row name reads in `palette.primary` in light mode and `palette.light` in dark mode.
- Pebble row date+time line is uppercased, tracked, and at 50% opacity.
- Switching the simulator to French (`Settings → General → Language & Region → French`) shows `Semaine N` and the date line uppercases to `DIMANCHE 5 AVRIL · 10:00`.
- Long-press on a pebble row shows the Delete affordance; deleting reloads the list.
- Tapping a pebble row opens the existing detail sheet (no regression).
- Year-boundary check: temporarily edit a seed pebble's `happened_at` to `2025-12-31T10:00:00Z` and another to `2026-01-02T10:00:00Z`. Both must appear under a single `Week 1` / `Semaine 1` card.

- [ ] **Step 5.3: Confirm `Localizable.xcstrings` is clean**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode. Filter by `state: new` and `state: stale` — confirm zero rows in either filter. Confirm the new `Week %lld` row has both `en` (`Week %lld`) and `fr` (`Semaine %lld`) values.

- [ ] **Step 5.4: Push and open the PR**

```bash
git push -u origin feat/374-ios-week-groups-emotion-rows
gh pr create --title "feat(ios): path week groups + palette-tinted rows" --body "$(cat <<'EOF'
Resolves #374.

Groups Path pebbles into per-ISO-week white rounded cards and replaces the row treatment with the emotion-category palette per the spec.

## What changed

**New:**
- `apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift` — free-function helper, mirrors `groupPebblesByMonth` shape. Buckets by `(yearForWeekOfYear, weekOfYear)` so dates straddling a calendar-year boundary share a single ISO week-1 card.
- `apps/ios/Pebbles/Features/Path/Components/WeekSectionHeader.swift` — centered Ysabeau title rendered as the first row of each week card.
- `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift` — emotion-tinted row used only by `PathView`. Surface bg, secondary glyph, name in primary/light, uppercase date+time at 50% opacity.

**Modified:**
- `apps/ios/Pebbles/Features/Path/PathView.swift` — flat list replaced by one `Section` per ISO-week group. Sheets, dialogs, toolbar, `load()`, and `delete(_:)` unchanged. Supabase select string unchanged.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — adds `Week %lld` (en) / `Semaine %lld` (fr).

**Untouched:**
- `Components/PebbleRow.swift` is still the canonical row used by `SoulDetailView` and `CollectionDetailView`. Path-specific styling lives in its own component to avoid coupling Path concerns to the other two screens.

## Spec & plan

- Spec: `docs/superpowers/specs/2026-05-09-ios-week-groups-emotion-rows-design.md`
- Plan: `docs/superpowers/plans/2026-05-09-ios-week-groups-emotion-rows.md`

## Test plan

- [x] Unit: `GroupPebblesByISOWeekTests` — empty, same-week, adjacent-weeks, year-boundary, descending order, input-order preservation, key-is-Monday-00:00 (7 tests).
- [x] Manual smoke (light + dark, en + fr): cards render per week, thumbnail/glyph/name/date colors per spec, long-press delete works, tapping opens the detail sheet.
- [x] `Localizable.xcstrings` clean — no `new` or `stale` rows.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

After the PR is created, apply labels and milestone matching issue #374 (`feat`, `ios`, `ui`, milestone `M30 · Emotions palettes`). Confirm with the user before applying.

---

## Self-review checklist

Before handing off:

- Spec coverage:
  - Week cards (Section 1 of spec) → Task 4.
  - Week header row → Task 2.
  - Pebble row thumbnail / name / date+time → Task 3.
  - `groupPebblesByISOWeek` helper + ISO-8601 rationale → Task 1.
  - Year-boundary case → covered in Task 1 unit test and Task 5 manual check.
  - Localization (`Week %lld` / `Semaine %lld`) → Task 2.
  - Empty / loading / error states → no change required; Task 4 explicitly preserves the existing gates.
  - Out-of-scope items (avatar/karma/record-prompt redesign, separator hiding, dark-mode surface tuning, `PebbleRow` reuse) → not present in any task.
- Type consistency:
  - `groupPebblesByISOWeek(_:calendar:)` signature matches between Task 1 (test + impl) and Task 4 (call site).
  - `WeekSectionHeader(weekStart:calendar:)` matches between Task 2 and Task 4.
  - `PathPebbleRow(pebble:onTap:onDelete:)` matches between Task 3 and Task 4.
- Placeholder scan: no `TBD`, `TODO`, "implement later", or "similar to Task N" — every code-bearing step contains the actual code.
