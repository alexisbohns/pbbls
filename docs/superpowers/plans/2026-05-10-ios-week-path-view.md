# iOS Week Path View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the iOS Path screen with a week-paginated experience: horizontal weeks roll, per-week pebble list (TabView paging), large pebble variant, photo attachments with index-parity rotation, custom bottom navigation replacing the system tab bar, Lab moved into Profile. Resolves issue #388.

**Architecture:** Single `path_pebbles` RPC returns enriched pebble rows (intensity, first-snap path, emotion). `PathView` becomes the post-auth root and computes a `[WeekRollEntry]` array via a pure `WeekRollBuilder`. Three sources (`WeekRollView` taps, `TabView(.page)` swipes, `WeekHeaderView` chevrons) all bind to a single `@State focusedWeekStart: Date`. `MainTabView` is deleted and `ProfileView` gains a Lab navigation link plus an onboarding-replay row.

**Tech Stack:** Swift 5.9, SwiftUI, iOS 17, Swift Testing, Supabase Swift, Postgres + RLS, RiveRuntime. No new external dependencies.

**Spec:** [`docs/superpowers/specs/2026-05-10-ios-week-path-view-design.md`](../specs/2026-05-10-ios-week-path-view-design.md)

**Branch:** `feat/388-week-path-view` (already created off `origin/main`; spec already committed there).

**Conventions to follow:**

- Conventional commits, lowercase, no period: `type(scope): description (#388)`.
- One commit per task. The PR should be a clean, reviewable sequence.
- Per-task lint: workspace-scoped where possible. Final task runs full repo `npm run lint` + `npm run build`.
- iOS tests run in Xcode (`Cmd+U`) — no CI script for the iOS suite.
- DB migrations: this project does **not** use Docker local dev. Use `npm run db:push` to apply to the linked remote, then `npm run db:types:remote` to regenerate types.
- After deleting/adding files in `apps/ios/Pebbles/**`, run `npm run generate --workspace=@pbbls/ios` (xcodegen) to refresh `Pebbles.xcodeproj`.
- New user-facing strings must land in `apps/ios/Pebbles/Resources/Localizable.xcstrings` with both EN source and FR translation populated.

---

## Task 1: Add the `path_pebbles` RPC migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260510000001_path_pebbles_rpc.sql`

- [ ] **Step 1.1: Create the migration file**

Create `packages/supabase/supabase/migrations/20260510000001_path_pebbles_rpc.sql` with the following content:

```sql
-- ============================================================
-- path_pebbles()
-- ------------------------------------------------------------
-- Single-round-trip read for the iOS Path screen. Returns every
-- pebble owned by auth.uid() with the few extra columns the new
-- Path UI needs that are not on the pebbles row itself:
--   - intensity (already on pebbles)
--   - render_svg (already on pebbles)
--   - emotion: jsonb {id, slug, name} via left join (left so a
--       deleted-but-referenced emotion still returns the pebble)
--   - first_snap_path: storage_path of the lowest-sort_order
--       snap, used by PathPebbleSnapThumb to sign a thumb URL
--
-- Ordering is global desc by happened_at; the iOS layer
-- re-sorts per-week (past weeks ascend, current/future descend).
-- ============================================================

create or replace function public.path_pebbles()
returns table (
  id uuid,
  name text,
  happened_at timestamptz,
  intensity smallint,
  render_svg text,
  emotion jsonb,
  first_snap_path text
)
language sql
security invoker
stable
as $$
  select
    p.id,
    p.name,
    p.happened_at,
    p.intensity,
    p.render_svg,
    case
      when e.id is null then null
      else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name)
    end as emotion,
    (
      select s.storage_path
      from public.snaps s
      where s.pebble_id = p.id
      order by s.sort_order asc nulls last, s.created_at asc
      limit 1
    ) as first_snap_path
  from public.pebbles p
  left join public.emotions e on e.id = p.emotion_id
  where p.user_id = auth.uid()
  order by p.happened_at desc;
$$;

grant execute on function public.path_pebbles() to authenticated;
```

- [ ] **Step 1.2: Push the migration to the linked remote**

From the repo root:

```bash
npm run db:push --workspace=packages/supabase
```

Expected: a confirmation prompt followed by a success message listing the migration. If the link is missing, follow the prompt to `npm run db:link -- --project-ref <ref>` first.

- [ ] **Step 1.3: Regenerate TypeScript types from the remote**

```bash
npm run db:types:remote --workspace=packages/supabase
```

Expected: `packages/supabase/types/database.ts` is rewritten. `git diff packages/supabase/types/database.ts` should show a new `path_pebbles` entry under `Functions`.

- [ ] **Step 1.4: Commit**

```bash
git add packages/supabase/supabase/migrations/20260510000001_path_pebbles_rpc.sql \
        packages/supabase/types/database.ts
git commit -m "feat(db): add path_pebbles RPC for iOS week path view (#388)"
```

---

## Task 2: Extend the iOS `Pebble` model

The decoded shape now includes `intensity` and `first_snap_path`. Update the struct and add a decoding test that exercises the new fields against the new RPC payload shape.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Pebble.swift`
- Create: `apps/ios/PebblesTests/PebbleDecodingTests.swift` (if missing) or extend it (search first)

- [ ] **Step 2.1: Update `Pebble` struct**

Replace the contents of `apps/ios/Pebbles/Features/Path/Models/Pebble.swift` with:

```swift
import Foundation

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let intensity: Int                  // 1=small, 2=medium, 3=large
    let renderSvg: String?
    let emotion: EmotionRef?
    let firstSnapPath: String?

    private enum CodingKeys: String, CodingKey {
        case id, name, intensity, emotion
        case happenedAt = "happened_at"
        case renderSvg = "render_svg"
        case firstSnapPath = "first_snap_path"
    }
}
```

- [ ] **Step 2.2: Write a failing decoding test**

Search for an existing `Pebble` decoding test first:

```bash
grep -rln "decode(Pebble" apps/ios/PebblesTests
```

If a test file already covers `Pebble` decoding, extend it. Otherwise create `apps/ios/PebblesTests/PebbleDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("Pebble decoding (path_pebbles RPC shape)")
struct PebbleDecodingTests {

    private func decoder() -> JSONDecoder {
        let dec = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        dec.dateDecodingStrategy = .custom { d in
            let raw = try d.singleValueContainer().decode(String.self)
            // Allow both with and without fractional seconds.
            if let date = formatter.date(from: raw) { return date }
            formatter.formatOptions = [.withInternetDateTime]
            return formatter.date(from: raw) ?? Date()
        }
        return dec
    }

    @Test("decodes a row with intensity, emotion, and first_snap_path")
    func decodesFullRow() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Sunday walk",
          "happened_at": "2026-05-10T08:30:00Z",
          "intensity": 3,
          "render_svg": "<svg/>",
          "emotion": { "id": "22222222-2222-2222-2222-222222222222", "slug": "joy", "name": "Joy" },
          "first_snap_path": "user-uuid/snap-uuid"
        }
        """.data(using: .utf8)!

        let pebble = try decoder().decode(Pebble.self, from: json)
        #expect(pebble.intensity == 3)
        #expect(pebble.firstSnapPath == "user-uuid/snap-uuid")
        #expect(pebble.emotion?.slug == "joy")
    }

    @Test("decodes a row with no emotion and no snap")
    func decodesMinimal() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Quiet evening",
          "happened_at": "2026-05-10T20:00:00Z",
          "intensity": 1,
          "render_svg": null,
          "emotion": null,
          "first_snap_path": null
        }
        """.data(using: .utf8)!

        let pebble = try decoder().decode(Pebble.self, from: json)
        #expect(pebble.intensity == 1)
        #expect(pebble.emotion == nil)
        #expect(pebble.firstSnapPath == nil)
        #expect(pebble.renderSvg == nil)
    }
}
```

- [ ] **Step 2.3: Run tests in Xcode (Cmd+U) — verify the new tests pass**

Expected: both tests pass. Existing `Pebble` callers may surface compile errors due to the new `intensity` and `firstSnapPath` fields — that's expected and gets fixed in later tasks. If callers fail to compile, **leave them broken for now** (next tasks update them).

- [ ] **Step 2.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Pebble.swift \
        apps/ios/PebblesTests/PebbleDecodingTests.swift
git commit -m "feat(ios): add intensity + firstSnapPath to Pebble model (#388)"
```

---

## Task 3: Update existing `Pebble` construction sites

`Pebble` is now stricter (extra non-optional `intensity`). Find every `Pebble(` initializer call and supply a value. Optional `firstSnapPath` defaults to `nil` — initializer order matters.

**Files:**
- Modify: every call site of `Pebble(...)` (search and patch).

- [ ] **Step 3.1: Find all initializer call sites**

```bash
grep -rn "Pebble(" apps/ios/Pebbles apps/ios/PebblesTests --include="*.swift" | grep -v "Pebble.self\|Pebbles\b\|PebbleRow\|PebbleDetail\|PebbleCollection\|PebblesApp\|PebbleSnap\|PebbleMedia\|PebbleAnimat\|PebbleForm\|PebbleSVG\|PebbleAudio\|PebbleRender\|PebbleMeta\|PebblePill\|PebblePrivacy\|PebbleRead\|GroupPebbles\|PebbleDraft\|PebbleCreate\|PebbleUpdate"
```

- [ ] **Step 3.2: Patch each call site**

For every preview / mock that constructs `Pebble(id:name:happenedAt:renderSvg:emotion:)`, change to:

```swift
Pebble(
    id: UUID(),
    name: "Sample pebble",
    happenedAt: Date(),
    intensity: 1,
    renderSvg: nil,
    emotion: nil,
    firstSnapPath: nil
)
```

The argument order matches the struct declaration (`intensity` is between `happenedAt` and `renderSvg`).

- [ ] **Step 3.3: Build the iOS target in Xcode (Cmd+B)**

Expected: clean build. If callers still fail to compile, repeat Step 3.1 with more permissive grep patterns.

- [ ] **Step 3.4: Commit**

```bash
git add apps/ios/Pebbles apps/ios/PebblesTests
git commit -m "feat(ios): update Pebble call sites for intensity + snap path (#388)"
```

---

## Task 4: Add the `WeekRollEntry` value type

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/WeekRollEntry.swift`

- [ ] **Step 4.1: Create the file**

```swift
import Foundation

/// One slot in the iOS Path's horizontal weeks roll.
///
/// `weekStart` is the ISO Monday 00:00 of the week, in the calendar passed
/// to `WeekRollBuilder.build(...)`. `pebbles` is already sorted per the
/// past-vs-current rule (past = oldest first, current/future = newest first).
///
/// Identity is `weekStart` so SwiftUI's `ForEach` keys correctly across
/// rebuilds (e.g. after a pebble create that does not change which weeks
/// are populated).
struct WeekRollEntry: Identifiable, Hashable {
    let weekStart: Date
    let pebbles: [Pebble]
    var id: Date { weekStart }
}
```

- [ ] **Step 4.2: Build the iOS target — verify clean compile**

- [ ] **Step 4.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/WeekRollEntry.swift
git commit -m "feat(ios): add WeekRollEntry value type (#388)"
```

---

## Task 5: Add `WeekRollBuilder.build` (TDD)

The pure builder that turns `[Pebble]` + today's date into `[WeekRollEntry]`. All weeks-with-pebbles + the current week + the next week, sorted ascending.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Services/WeekRollBuilder.swift`
- Create: `apps/ios/PebblesTests/WeekRollBuilderTests.swift`

- [ ] **Step 5.1: Write the failing test suite**

Create `apps/ios/PebblesTests/WeekRollBuilderTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("WeekRollBuilder.build")
struct WeekRollBuilderTests {

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

    private func pebble(_ happened: String, intensity: Int = 1) -> Pebble {
        Pebble(
            id: UUID(),
            name: "p",
            happenedAt: date(happened),
            intensity: intensity,
            renderSvg: nil,
            emotion: nil,
            firstSnapPath: nil
        )
    }

    /// 2026-05-10 is a Sunday → ISO week 19 of 2026 runs Mon 2026-05-04 → Sun 2026-05-10.
    private let today = ISO8601DateFormatter().date(from: "2026-05-10T12:00:00Z")!

    @Test("empty pebbles → currentWeek and nextWeek only")
    func emptyInput() {
        let entries = WeekRollBuilder.build(pebbles: [], calendar: calendar, today: today)
        #expect(entries.count == 2)
        #expect(entries[0].pebbles.isEmpty)
        #expect(entries[1].pebbles.isEmpty)
        // ascending: currentWeek (May 4) then nextWeek (May 11)
        #expect(entries[0].weekStart < entries[1].weekStart)
    }

    @Test("single pebble in current week → [current(1), next(0)]")
    func singleCurrent() {
        let p = pebble("2026-05-08T10:00:00Z") // Friday in week 19
        let entries = WeekRollBuilder.build(pebbles: [p], calendar: calendar, today: today)
        #expect(entries.count == 2)
        #expect(entries[0].pebbles.count == 1)
        #expect(entries[1].pebbles.isEmpty)
    }

    @Test("retro pebble in 1990 → roll has 1990 + current + next, ascending")
    func retroPebble() {
        let retro = pebble("1990-02-01T10:00:00Z") // ISO week 5 of 1990
        let entries = WeekRollBuilder.build(pebbles: [retro], calendar: calendar, today: today)
        #expect(entries.count == 3)
        #expect(entries[0].weekStart < entries[1].weekStart)
        #expect(entries[1].weekStart < entries[2].weekStart)
        #expect(entries[0].pebbles.count == 1) // 1990 entry has the retro
    }

    @Test("non-adjacent weeks: 17, 19, current, next — no gap-filling for week 18")
    func nonAdjacentWeeks() {
        let w17 = pebble("2026-04-22T10:00:00Z") // week 17
        let w19 = pebble("2026-05-04T10:00:00Z") // week 19 (current)
        let entries = WeekRollBuilder.build(pebbles: [w17, w19], calendar: calendar, today: today)
        // Expected entries: w17 + current(week 19) + next(week 20). Week 18 is NOT filled.
        #expect(entries.count == 3)
        let weekNumbers = entries.map { calendar.component(.weekOfYear, from: $0.weekStart) }
        #expect(weekNumbers == [17, 19, 20])
    }

    @Test("past-week pebbles sort ascending; current sorts descending")
    func sortAsymmetry() {
        // Past: ISO week 17 of 2026 (Apr 20–26)
        let pastEarly = pebble("2026-04-21T08:00:00Z")
        let pastLate  = pebble("2026-04-25T20:00:00Z")
        // Current: week 19 (May 4–10)
        let curEarly = pebble("2026-05-05T08:00:00Z")
        let curLate  = pebble("2026-05-09T20:00:00Z")

        let entries = WeekRollBuilder.build(
            pebbles: [pastLate, pastEarly, curLate, curEarly],
            calendar: calendar, today: today
        )
        // Find past entry (week 17) and current entry (week 19)
        let past    = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 17 }!
        let current = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 19 }!
        #expect(past.pebbles.first?.happenedAt == pastEarly.happenedAt)   // ascending
        #expect(past.pebbles.last?.happenedAt  == pastLate.happenedAt)
        #expect(current.pebbles.first?.happenedAt == curLate.happenedAt)  // descending
        #expect(current.pebbles.last?.happenedAt  == curEarly.happenedAt)
    }

    @Test("year-boundary: 2025-12-29 buckets into ISO week 1 of 2026")
    func yearBoundary() {
        // 2026-01-01 is a Thursday → ISO week 1 of 2026 starts Mon 2025-12-29.
        let dec = pebble("2025-12-29T10:00:00Z")
        let entries = WeekRollBuilder.build(pebbles: [dec], calendar: calendar, today: today)
        let target = entries.first { $0.pebbles.count == 1 }!
        let comps = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: target.weekStart)
        #expect(comps.yearForWeekOfYear == 2026)
        #expect(comps.weekOfYear == 1)
    }

    @Test("pebble at exactly Monday 00:00 of current week buckets to current")
    func mondayBoundary() {
        // 2026-05-04T00:00:00Z is the start of ISO week 19.
        let mon = pebble("2026-05-04T00:00:00Z")
        let entries = WeekRollBuilder.build(pebbles: [mon], calendar: calendar, today: today)
        let target = entries.first { $0.pebbles.count == 1 }!
        let weekNum = calendar.component(.weekOfYear, from: target.weekStart)
        #expect(weekNum == 19)
    }
}
```

- [ ] **Step 5.2: Run the suite — expect it to fail (no `WeekRollBuilder` symbol yet)**

Build in Xcode (Cmd+B). Expected: `cannot find 'WeekRollBuilder' in scope` for every `@Test` body.

- [ ] **Step 5.3: Implement `WeekRollBuilder.build`**

Create `apps/ios/Pebbles/Features/Path/Services/WeekRollBuilder.swift`:

```swift
import Foundation

/// Pure builder for the iOS Path screen's weeks roll.
///
/// Returns `[WeekRollEntry]` containing every ISO week with at least one
/// pebble, plus the current week and the next week (so the user can always
/// see "today" and "tomorrow's week" in the roll). Entries are sorted
/// ascending by `weekStart`.
///
/// Within each entry, pebbles are sorted asymmetrically:
///   - Past weeks (whose Monday is strictly before the current week's
///     Monday) sort **ascending** by `happenedAt` — reading time forward.
///   - Current and future weeks sort **descending** — most recent first,
///     matching the way the old `PathView` rendered.
enum WeekRollBuilder {

    static func build(
        pebbles: [Pebble],
        calendar: Calendar,
        today: Date
    ) -> [WeekRollEntry] {
        let currentStart = weekStart(for: today, calendar: calendar)
        guard let nextStart = calendar.date(byAdding: .weekOfYear, value: 1, to: currentStart) else {
            return []
        }

        // Bucket pebbles by their week's Monday.
        let grouped: [Date: [Pebble]] = Dictionary(grouping: pebbles) { p in
            weekStart(for: p.happenedAt, calendar: calendar)
        }

        // Union: every week with pebbles, plus current and next.
        let weekStarts = Set(grouped.keys).union([currentStart, nextStart])

        return weekStarts
            .sorted()
            .map { ws in
                let raw = grouped[ws] ?? []
                let sorted = ws < currentStart
                    ? raw.sorted { $0.happenedAt < $1.happenedAt }
                    : raw.sorted { $0.happenedAt > $1.happenedAt }
                return WeekRollEntry(weekStart: ws, pebbles: sorted)
            }
    }

    /// ISO Monday 00:00:00 of the week containing `date`.
    private static func weekStart(for date: Date, calendar: Calendar) -> Date {
        let comps = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        var monday = DateComponents()
        monday.yearForWeekOfYear = comps.yearForWeekOfYear
        monday.weekOfYear = comps.weekOfYear
        monday.weekday = 2 // Monday in ISO 8601
        return calendar.date(from: monday) ?? date
    }
}
```

- [ ] **Step 5.4: Run tests in Xcode (Cmd+U) — verify all pass**

Expected: all `WeekRollBuilderTests` cases green.

- [ ] **Step 5.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Services/WeekRollBuilder.swift \
        apps/ios/PebblesTests/WeekRollBuilderTests.swift
git commit -m "feat(ios): add WeekRollBuilder for path weeks roll (#388)"
```

---

## Task 6: Add `WeekRollBuilder.previous` / `.next` helpers (TDD)

Chevron taps need O(1) lookups for the prev/next entry given the focused `weekStart`.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Services/WeekRollBuilder.swift`
- Modify: `apps/ios/PebblesTests/WeekRollBuilderTests.swift`

- [ ] **Step 6.1: Add failing tests**

Append to `WeekRollBuilderTests`:

```swift
@Test("previous(of:) returns the entry one before focus, or nil at the head")
func previousLookup() {
    let p17 = pebble("2026-04-22T10:00:00Z")
    let p19 = pebble("2026-05-04T10:00:00Z")
    let entries = WeekRollBuilder.build(pebbles: [p17, p19], calendar: calendar, today: today)
    let week17Start = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 17 }!.weekStart
    let week19Start = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 19 }!.weekStart
    #expect(WeekRollBuilder.previous(of: week17Start, in: entries) == nil)
    #expect(WeekRollBuilder.previous(of: week19Start, in: entries)?.weekStart == week17Start)
}

@Test("next(of:) returns the entry one after focus, or nil at the tail")
func nextLookup() {
    let p17 = pebble("2026-04-22T10:00:00Z")
    let p19 = pebble("2026-05-04T10:00:00Z")
    let entries = WeekRollBuilder.build(pebbles: [p17, p19], calendar: calendar, today: today)
    let last = entries.last!.weekStart
    let week19Start = entries.first { calendar.component(.weekOfYear, from: $0.weekStart) == 19 }!.weekStart
    #expect(WeekRollBuilder.next(of: last, in: entries) == nil)
    #expect(WeekRollBuilder.next(of: week19Start, in: entries)?.weekStart > week19Start)
}
```

- [ ] **Step 6.2: Run — expect failure (`previous`/`next` missing)**

- [ ] **Step 6.3: Implement the helpers**

Append to `WeekRollBuilder`:

```swift
extension WeekRollBuilder {

    static func previous(of weekStart: Date, in entries: [WeekRollEntry]) -> WeekRollEntry? {
        guard let idx = entries.firstIndex(where: { $0.weekStart == weekStart }), idx > 0 else {
            return nil
        }
        return entries[idx - 1]
    }

    static func next(of weekStart: Date, in entries: [WeekRollEntry]) -> WeekRollEntry? {
        guard let idx = entries.firstIndex(where: { $0.weekStart == weekStart }),
              idx + 1 < entries.count else {
            return nil
        }
        return entries[idx + 1]
    }
}
```

- [ ] **Step 6.4: Run — verify all pass**

- [ ] **Step 6.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Services/WeekRollBuilder.swift \
        apps/ios/PebblesTests/WeekRollBuilderTests.swift
git commit -m "feat(ios): add WeekRollBuilder previous/next chevron helpers (#388)"
```

---

## Task 7: Add `PathPebbleRow` geometry helpers (TDD)

Pure helpers for photo rotation parity and row height. Extracted as `static` so they're testable without rendering.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift` (extension only — main view stays untouched in this task)
- Create: `apps/ios/PebblesTests/PathPebbleRowGeometryTests.swift`

- [ ] **Step 7.1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles
import CoreGraphics

@Suite("PathPebbleRow geometry helpers")
struct PathPebbleRowGeometryTests {

    @Test("rotation alternates: even = -7°, odd = +4°")
    func rotation() {
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 0) == -7)
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 1) == 4)
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 2) == -7)
        #expect(PathPebbleRow.rotationAngle(forPositionIndex: 3) == 4)
    }

    @Test("row height: small/medium without photo = 60pt")
    func smallNoPhoto() {
        #expect(PathPebbleRow.rowHeight(intensity: 1, hasPhoto: false, positionIndex: 0) == 60)
        #expect(PathPebbleRow.rowHeight(intensity: 2, hasPhoto: false, positionIndex: 5) == 60)
    }

    @Test("row height: small/medium with +4° photo (odd) = 68pt")
    func smallPhotoOdd() {
        #expect(PathPebbleRow.rowHeight(intensity: 1, hasPhoto: true, positionIndex: 1) == 68)
        #expect(PathPebbleRow.rowHeight(intensity: 2, hasPhoto: true, positionIndex: 3) == 68)
    }

    @Test("row height: small/medium with -7° photo (even) = 71pt")
    func smallPhotoEven() {
        #expect(PathPebbleRow.rowHeight(intensity: 1, hasPhoto: true, positionIndex: 0) == 71)
        #expect(PathPebbleRow.rowHeight(intensity: 2, hasPhoto: true, positionIndex: 2) == 71)
    }

    @Test("row height: large = 100pt regardless of photo state")
    func largeRow() {
        #expect(PathPebbleRow.rowHeight(intensity: 3, hasPhoto: false, positionIndex: 0) == 100)
        #expect(PathPebbleRow.rowHeight(intensity: 3, hasPhoto: true, positionIndex: 0) == 100)
        #expect(PathPebbleRow.rowHeight(intensity: 3, hasPhoto: true, positionIndex: 1) == 100)
    }
}
```

- [ ] **Step 7.2: Run — expect failure (helpers don't exist)**

- [ ] **Step 7.3: Add the helpers to `PathPebbleRow`**

Open `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift` and append at the bottom (outside the existing `struct PathPebbleRow` body, but in the same file):

```swift
extension PathPebbleRow {

    /// Photo rotation by row position. Even indices (0, 2, 4...) lean
    /// counter-clockwise (-7°); odd lean clockwise (+4°).
    static func rotationAngle(forPositionIndex i: Int) -> Double {
        i.isMultiple(of: 2) ? -7 : 4
    }

    /// Row height by intensity + photo state + parity. Sized to fit the
    /// rotated 64pt photo's bounding box for small/medium rows; large rows
    /// are dominated by the 96pt thumbnail and stay at 100pt.
    static func rowHeight(intensity: Int, hasPhoto: Bool, positionIndex: Int) -> CGFloat {
        if intensity >= 3 { return 100 }
        if !hasPhoto { return 60 }
        return positionIndex.isMultiple(of: 2) ? 71 : 68
    }
}
```

- [ ] **Step 7.4: Run — verify all geometry tests pass**

- [ ] **Step 7.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift \
        apps/ios/PebblesTests/PathPebbleRowGeometryTests.swift
git commit -m "feat(ios): add PathPebbleRow geometry helpers (#388)"
```

---

## Task 8: Add `WeekHeaderView.formatRange` helper (TDD)

The pure helper that turns `(weekStart, today, calendar, locale)` into the date label, with a year suffix when years differ.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift` (helper-only stub for now; full view in Task 13)
- Create: `apps/ios/PebblesTests/WeekHeaderFormatTests.swift`

- [ ] **Step 8.1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("WeekHeaderView.formatRange")
struct WeekHeaderFormatTests {

    private var calendar: Calendar {
        var iso = Calendar(identifier: .iso8601)
        iso.timeZone = TimeZone(identifier: "UTC")!
        return iso
    }

    private func date(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: iso)!
    }

    @Test("same-year focus → no year suffix (English locale)")
    func sameYearEN() {
        let weekStart = date("2026-05-04T00:00:00Z")          // May 4 2026
        let today     = date("2026-05-10T12:00:00Z")          // Sun May 10 2026
        let label = WeekHeaderView.formatRange(
            weekStart: weekStart, today: today,
            calendar: calendar, locale: Locale(identifier: "en_US")
        )
        // Expected: "May 4 · May 10"
        #expect(label.contains("May 4"))
        #expect(label.contains("May 10"))
        #expect(!label.contains("2026"))
    }

    @Test("cross-year focus → year suffix appears (English locale)")
    func crossYearEN() {
        let weekStart = date("1990-01-29T00:00:00Z")           // ISO week 5 of 1990
        let today     = date("2026-05-10T12:00:00Z")
        let label = WeekHeaderView.formatRange(
            weekStart: weekStart, today: today,
            calendar: calendar, locale: Locale(identifier: "en_US")
        )
        // Expected something like "January 29 · February 4 · 1990"
        #expect(label.contains("1990"))
    }

    @Test("French locale renders month names in French")
    func frenchLocale() {
        let weekStart = date("2026-05-04T00:00:00Z")
        let today     = date("2026-05-10T12:00:00Z")
        let label = WeekHeaderView.formatRange(
            weekStart: weekStart, today: today,
            calendar: calendar, locale: Locale(identifier: "fr_FR")
        )
        // FR formats day before month: "4 mai · 10 mai"
        #expect(label.lowercased().contains("mai"))
    }
}
```

- [ ] **Step 8.2: Run — expect failure (no `WeekHeaderView` yet)**

- [ ] **Step 8.3: Create the helper-only stub**

Create `apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift`:

```swift
import SwiftUI

/// The "MAY 4 · MAY 10" pill above the path body. Shows the focused
/// week's date range, with a year suffix when the year differs from
/// today's. Chevrons step `focusedWeekStart` through the surrounding
/// `entries` array.
///
/// This file currently exposes only `formatRange` (used in tests).
/// The full view body lives in a later task.
struct WeekHeaderView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar
    let today: Date

    var body: some View {
        // Placeholder body; replaced in Task 13.
        Text(Self.formatRange(
            weekStart: focusedWeekStart, today: today,
            calendar: calendar, locale: .current
        ))
    }

    /// Pure helper, exposed for testing. Locale is taken from the
    /// environment in production but injected here so the test suite is
    /// hermetic against the simulator's locale.
    static func formatRange(
        weekStart: Date,
        today: Date,
        calendar: Calendar,
        locale: Locale
    ) -> String {
        guard let weekEnd = calendar.date(byAdding: .day, value: 6, to: weekStart) else {
            return ""
        }
        let monthDay = Date.FormatStyle.dateTime
            .month(.wide).day()
            .locale(locale)
        let startLabel = weekStart.formatted(monthDay)
        let endLabel   = weekEnd.formatted(monthDay)

        let weekYear  = calendar.component(.yearForWeekOfYear, from: weekStart)
        let todayYear = calendar.component(.yearForWeekOfYear, from: today)

        if weekYear != todayYear {
            return "\(startLabel) · \(endLabel) · \(weekYear)"
        } else {
            return "\(startLabel) · \(endLabel)"
        }
    }
}
```

- [ ] **Step 8.4: Run — verify all `WeekHeaderFormatTests` pass**

- [ ] **Step 8.5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift \
        apps/ios/PebblesTests/WeekHeaderFormatTests.swift
git commit -m "feat(ios): add WeekHeaderView.formatRange helper (#388)"
```

---

## Task 9: Add `PathStatsService` and inject it from `PebblesApp`

The shared `@Observable` for karma + bounce, consumed by both `PathBottomBar` and `ProfileView`.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`
- Modify: `apps/ios/Pebbles/PebblesApp.swift`

- [ ] **Step 9.1: Create the service**

```swift
import Foundation
import os
import Supabase

/// Shared @Observable wrapper around `v_karma_summary` and `v_bounce`.
/// PathView (bottom bar) and ProfileView read the same instance so a
/// reload from one screen is visible to the other.
@Observable
@MainActor
final class PathStatsService {
    var karma: Int?
    var bounce: Int?

    private let supabase: SupabaseService
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path-stats")

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    func load() async {
        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary").select("total_karma, pebbles_count")
            .single().execute().value
        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce").select("bounce_level, active_days")
            .single().execute().value

        do {
            self.karma = try await karmaResult.totalKarma
        } catch {
            logger.error("karma fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        do {
            self.bounce = try await bounceResult.bounceLevel
        } catch {
            logger.error("bounce fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}
```

- [ ] **Step 9.2: Wire it into `PebblesApp`**

Open `apps/ios/Pebbles/PebblesApp.swift` and apply:

```swift
@main
struct PebblesApp: App {
    @State private var supabase: SupabaseService
    @State private var palettes: EmotionPaletteService
    @State private var stats: PathStatsService                    // NEW

    init() {
        let supabase = SupabaseService()
        self._supabase = State(initialValue: supabase)
        self._palettes = State(initialValue: EmotionPaletteService(client: supabase.client))
        self._stats    = State(initialValue: PathStatsService(supabase: supabase))   // NEW
        Self.configureSegmentedControlAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
                .environment(palettes)
                .environment(stats)                                // NEW
        }
    }

    // ... unchanged configureSegmentedControlAppearance() below
}
```

- [ ] **Step 9.3: Build the iOS target**

Expected: clean. (No callers consume `stats` yet — that comes in later tasks.)

- [ ] **Step 9.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift \
        apps/ios/Pebbles/PebblesApp.swift
git commit -m "feat(ios): add PathStatsService env injection (#388)"
```

---

## Task 10: Migrate `ProfileView.loadStats` to use `PathStatsService`

ProfileView keeps the same UI; it just reads from the shared service instead of running its own queries.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

- [ ] **Step 10.1: Apply the migration**

In `apps/ios/Pebbles/Features/Profile/ProfileView.swift`:

1. Add the env property near the existing `@Environment(SupabaseService.self)` line:
   ```swift
   @Environment(PathStatsService.self) private var stats
   ```

2. Replace the local `karma` and `bounce` `@State` declarations with reads from `stats`:
   - Delete `@State private var karma: KarmaSummary?` and `@State private var bounce: BounceSummary?`.
   - Replace `karma?.totalKarma` references with `stats.karma`.
   - Replace `bounce?.bounceLevel` references with `stats.bounce`.

3. Replace the body of `loadStats()` with:
   ```swift
   private func loadStats() async {
       await stats.load()
   }
   ```

4. The `.task { await loadStats() }` modifier stays; the existing logger reference is no longer used inside `loadStats` and can be removed if it has no other callers.

- [ ] **Step 10.2: Build the iOS target**

Expected: clean compile. ProfileView's stats display still works (verify by running the app and viewing Profile).

- [ ] **Step 10.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/ProfileView.swift
git commit -m "refactor(ios): consume PathStatsService in ProfileView (#388)"
```

---

## Task 11: Add `PathPebbleSnapThumb`

Tiny lazy-signed thumb image used by `PathPebbleRow`'s photo overlay.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift`

- [ ] **Step 11.1: Create the file**

```swift
import SwiftUI
import os

/// Lazy-loads a signed thumb URL for one snap and renders it.
///
/// Sized and clipped by the caller (`PathPebbleRow` wraps it in a 64×64
/// frame, applies a corner radius, white border, drop shadow, and rotation).
/// Failure to sign leaves `url` `nil`; the AsyncImage placeholder shows.
struct PathPebbleSnapThumb: View {
    let storagePath: String

    @Environment(SupabaseService.self) private var supabase
    @State private var url: URL?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path-row-thumb")

    var body: some View {
        AsyncImage(url: url) { image in
            image
                .resizable()
                .aspectRatio(contentMode: .fill)
        } placeholder: {
            Color.clear
        }
        .task(id: storagePath) {
            do {
                let urls = try await PebbleSnapRepository(client: supabase.client)
                    .signedURLs(storagePrefix: storagePath)
                url = urls.thumb
            } catch {
                logger.error("snap sign failed: \(error.localizedDescription, privacy: .private)")
            }
        }
    }
}
```

- [ ] **Step 11.2: Build the iOS target — clean compile**

- [ ] **Step 11.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift
git commit -m "feat(ios): add PathPebbleSnapThumb for row photo (#388)"
```

---

## Task 12: Update `PathPebbleRow` (large variant + photo + new date format)

The full row implementation, replacing today's body. Geometry helpers from Task 7 already exist in this file.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift`

- [ ] **Step 12.1: Replace the row body**

Open `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift`. Replace everything **above** the `extension PathPebbleRow { ... static helpers ... }` block (i.e., the original `struct PathPebbleRow: View { ... }` and its preview) with:

```swift
import SwiftUI

/// Path-specific pebble row, used by `WeekPathView`. Renders three
/// states based on the pebble's `intensity`:
///   - intensity 1–2 (small/medium): 56pt thumbnail with `palette.surface`
///     fill and `palette.secondary` glyph stroke; name color follows the
///     scheme (light=primary, dark=light).
///   - intensity 3 (large): 96pt thumbnail with `palette.primary` fill and
///     `palette.light` glyph stroke; name color is `palette.light` in both
///     schemes (the primary fill carries scheme contrast).
///
/// When `pebble.firstSnapPath` is non-nil, a 64pt photo is rendered to
/// the right with rotation by parity (even = -7°, odd = +4°) and a white
/// border + drop shadow. Row height grows to fit the rotated photo per
/// `rowHeight(intensity:hasPhoto:positionIndex:)`.
///
/// Long-press surfaces a delete option via `.contextMenu` — the parent
/// `PathView` owns the confirmation dialog.
struct PathPebbleRow: View {
    let pebble: Pebble
    let positionIndex: Int
    let onTap: () -> Void
    let onDelete: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme

    private static let smallThumbnailSize: CGFloat = 56
    private static let largeThumbnailSize: CGFloat = 96
    private static let glyphInset: CGFloat = 8
    private static let photoSize: CGFloat = 64

    private var isLarge: Bool { pebble.intensity >= 3 }
    private var thumbnailSize: CGFloat { isLarge ? Self.largeThumbnailSize : Self.smallThumbnailSize }
    private var hasPhoto: Bool { pebble.firstSnapPath != nil }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name)
                        .font(.custom("Ysabeau-SemiBold", size: 17))
                        .foregroundStyle(nameColor)
                    Text(formattedWeekdayTime)
                        .font(.caption)
                        .tracking(1.0)
                        .textCase(.uppercase)
                        .foregroundStyle(nameColor.opacity(0.5))
                }
                if hasPhoto, let path = pebble.firstSnapPath {
                    Spacer(minLength: 0)
                    photoView(path: path)
                }
            }
            .frame(
                height: PathPebbleRow.rowHeight(
                    intensity: pebble.intensity,
                    hasPhoto: hasPhoto,
                    positionIndex: positionIndex
                ),
                alignment: .center
            )
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
        .frame(width: thumbnailSize, height: thumbnailSize)
    }

    @ViewBuilder
    private func photoView(path: String) -> some View {
        PathPebbleSnapThumb(storagePath: path)
            .frame(width: Self.photoSize, height: Self.photoSize)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.white, lineWidth: 4)
            )
            .shadow(color: Color.black.opacity(0.18), radius: 6, x: 0, y: 2)
            .rotationEffect(.degrees(PathPebbleRow.rotationAngle(forPositionIndex: positionIndex)))
    }

    private var palette: EmotionPalette? {
        guard let emotionId = pebble.emotion?.id else { return nil }
        return palettes.palette(for: emotionId)
    }

    private var thumbnailFill: Color {
        if isLarge { return palette?.primary ?? Color.pebblesAccent }
        return palette?.surface ?? Color.pebblesAccent.opacity(0.15)
    }

    private var glyphStrokeHex: String? {
        if isLarge {
            // Large rows stroke in light variant. Trim 8-digit hex to 6-digit
            // for SVGView reliability (matches PebbleRenderView's ingest).
            guard let palette else { return Color.pebblesAccentHex }
            let hex = palette.lightHex
            return hex.count == 9 ? String(hex.prefix(7)) : hex
        }
        guard let palette else { return Color.pebblesAccentHex }
        let hex = palette.secondaryHex
        return hex.count == 9 ? String(hex.prefix(7)) : hex
    }

    private var nameColor: Color {
        guard let palette else { return Color.pebblesForeground }
        if isLarge { return palette.light }
        return colorScheme == .dark ? palette.light : palette.primary
    }

    /// Weekday + time only — the focused week is already known from
    /// `WeekHeaderView`, so day/month would be redundant.
    private var formattedWeekdayTime: String {
        let weekday = pebble.happenedAt.formatted(.dateTime.weekday(.wide))
        let time    = pebble.happenedAt.formatted(.dateTime.hour().minute())
        return "\(weekday) · \(time)"
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
                    intensity: 1,
                    renderSvg: nil,
                    emotion: nil,
                    firstSnapPath: nil
                ),
                positionIndex: 0,
                onTap: {},
                onDelete: {}
            )
            .listRowBackground(Color.pebblesListRow)
        }
    }
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(supabase)
}
```

Keep the existing `extension PathPebbleRow { static func rotationAngle... ; static func rowHeight... }` from Task 7 untouched.

- [ ] **Step 12.2: Verify `EmotionPalette` exposes `lightHex`**

```bash
grep -n "var lightHex\|var light\b" apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift
```

If `lightHex` is missing, add it as a sibling of `secondaryHex` using the same encoding rule. If the palette already exposes `light: Color` but not the hex string, derive the hex from the same source the existing `secondaryHex` uses.

- [ ] **Step 12.3: Build the iOS target**

Expected: clean. Geometry helper tests still pass.

- [ ] **Step 12.4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift \
        apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift
git commit -m "feat(ios): rewrite PathPebbleRow with large variant + photo (#388)"
```

---

## Task 13: Implement `WeekHeaderView` body

Replace the placeholder `body` from Task 8 with the real chevron + pill layout.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift`

- [ ] **Step 13.1: Replace the body**

In `apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift`, replace **only** the `body` of `WeekHeaderView` (keep the `formatRange` static func and the struct stored properties):

```swift
var body: some View {
    HStack(spacing: 12) {
        chevronButton(isPrevious: true)
        Spacer(minLength: 0)
        Text(Self.formatRange(
            weekStart: focusedWeekStart, today: today,
            calendar: calendar, locale: .current
        ))
        .font(.custom("Ysabeau-SemiBold", size: 17))
        .tracking(0.34)              // 2% of 17pt
        .textCase(.uppercase)
        .foregroundStyle(Color.pebblesMutedForeground)
        Spacer(minLength: 0)
        chevronButton(isPrevious: false)
    }
    .padding(.horizontal, 16)
    .frame(height: 40)
    .overlay(
        Capsule().stroke(strokeColor, lineWidth: 1)
    )
    .padding(.horizontal, 16)
}

@Environment(\.colorScheme) private var colorScheme

private var strokeColor: Color {
    colorScheme == .dark ? Color.pebblesForeground : Color.pebblesMutedForeground
}

@ViewBuilder
private func chevronButton(isPrevious: Bool) -> some View {
    let target = isPrevious
        ? WeekRollBuilder.previous(of: focusedWeekStart, in: entries)
        : WeekRollBuilder.next(of: focusedWeekStart, in: entries)

    Button {
        guard let target else { return }
        withAnimation { focusedWeekStart = target.weekStart }
    } label: {
        Image(systemName: isPrevious ? "chevron.compact.left" : "chevron.compact.right")
            .font(.title3)
            .foregroundStyle(Color.pebblesAccent)
            .opacity(target == nil ? 0.3 : 1.0)
    }
    .disabled(target == nil)
    .accessibilityLabel(isPrevious ? "Previous week" : "Next week")
}
```

- [ ] **Step 13.2: Build the iOS target — clean compile**

- [ ] **Step 13.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/WeekHeaderView.swift
git commit -m "feat(ios): implement WeekHeaderView body (#388)"
```

---

## Task 14: Add `WeekRollCairnCell`

Single cairn cell in the horizontal roll. Plays its one-shot animation when it becomes focused.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift`

- [ ] **Step 14.1: Create the file**

```swift
import RiveRuntime
import SwiftUI
import UIKit

/// One cell in the horizontal weeks roll. Renders the cairn Rive
/// animation above the ISO week number. Plays its one-shot whenever
/// `isFocused` flips true; resets to frame 1 when it flips false.
struct WeekRollCairnCell: View {
    let entry: WeekRollEntry
    let isFocused: Bool
    let opacity: Double
    let calendar: Calendar
    let onTap: () -> Void

    @State private var cairn: CairnAnimationViewModel

    init(
        entry: WeekRollEntry,
        isFocused: Bool,
        opacity: Double,
        calendar: Calendar,
        onTap: @escaping () -> Void
    ) {
        self.entry = entry
        self.isFocused = isFocused
        self.opacity = opacity
        self.calendar = calendar
        self.onTap = onTap
        self._cairn = State(initialValue: CairnAnimationViewModel(fileName: "pbbls-cairn"))
    }

    private static let titleSize: CGFloat = 13

    /// Ysabeau-SemiBold with proportional + lining figures (matches
    /// `WeekSectionHeader`'s rendering).
    private static var titleFont: SwiftUI.Font {
        let descriptor = UIFontDescriptor(name: "Ysabeau-SemiBold", size: titleSize)
            .addingAttributes([
                .featureSettings: [
                    [UIFontDescriptor.FeatureKey.type: 6,  UIFontDescriptor.FeatureKey.selector: 1],
                    [UIFontDescriptor.FeatureKey.type: 21, UIFontDescriptor.FeatureKey.selector: 1],
                ],
            ])
        return SwiftUI.Font(UIFont(descriptor: descriptor, size: titleSize))
    }

    var body: some View {
        let weekNum = calendar.component(.weekOfYear, from: entry.weekStart)
        Button(action: onTap) {
            VStack(spacing: 4) {
                cairn.view()
                    .frame(width: 56, height: 56)
                    .accessibilityHidden(true)
                Text(verbatim: "\(weekNum)")
                    .font(Self.titleFont)
                    .foregroundStyle(isFocused ? Color.pebblesAccent : Color.pebblesMutedForeground)
            }
        }
        .buttonStyle(.plain)
        .frame(width: 72)
        .opacity(opacity)
        .onChange(of: isFocused) { _, nowFocused in
            if nowFocused {
                cairn.play()
            } else {
                cairn.reset()
            }
        }
        .onAppear {
            // Initial focused cairn plays its intro on first mount.
            if isFocused { cairn.play() }
        }
        .accessibilityLabel("Week \(weekNum), \(entry.pebbles.count) pebbles")
    }
}
```

- [ ] **Step 14.2: Build the iOS target — clean compile**

- [ ] **Step 14.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift
git commit -m "feat(ios): add WeekRollCairnCell with focus play (#388)"
```

---

## Task 15: Add `WeekRollView`

Horizontal scroll of `WeekRollCairnCell`s, snapping to the focused cell.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/WeekRollView.swift`

- [ ] **Step 15.1: Create the file**

```swift
import SwiftUI

/// Horizontal cairn strip. The focused cell rests centered;
/// surrounding cells fade with distance (±1 → 0.50, ±2 → 0.25,
/// further → invisible).
///
/// Uses iOS 17 `.scrollPosition(id:)` so a write to `focusedWeekStart`
/// from anywhere (chevron, body swipe, tap on a cell) animates the
/// strip to center the focused cairn.
struct WeekRollView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar

    private static let cellWidth: CGFloat = 72

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 0) {
                ForEach(entries) { entry in
                    WeekRollCairnCell(
                        entry: entry,
                        isFocused: entry.weekStart == focusedWeekStart,
                        opacity: opacity(for: entry),
                        calendar: calendar,
                        onTap: {
                            withAnimation { focusedWeekStart = entry.weekStart }
                        }
                    )
                    .id(entry.weekStart)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: Binding(
            get: { focusedWeekStart },
            set: { newValue in
                if let v = newValue { focusedWeekStart = v }
            }
        ))
        .contentMargins(.horizontal, scrollMargin, for: .scrollContent)
        .frame(height: 96)
    }

    /// Half the screen width minus half the cell width, so the focused
    /// cell rests centered. Approximation works on all current iPhone widths.
    private var scrollMargin: CGFloat {
        let screenWidth = UIScreen.main.bounds.width
        return max(0, (screenWidth - Self.cellWidth) / 2)
    }

    /// Opacity falloff by index distance from focused entry.
    private func opacity(for entry: WeekRollEntry) -> Double {
        guard let focusedIdx = entries.firstIndex(where: { $0.weekStart == focusedWeekStart }),
              let myIdx = entries.firstIndex(where: { $0.weekStart == entry.weekStart }) else {
            return 1.0
        }
        switch abs(focusedIdx - myIdx) {
        case 0: return 1.0
        case 1: return 0.5
        case 2: return 0.25
        default: return 0.0
        }
    }
}
```

- [ ] **Step 15.2: Build the iOS target — clean compile**

- [ ] **Step 15.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/WeekRollView.swift
git commit -m "feat(ios): add WeekRollView horizontal cairn strip (#388)"
```

---

## Task 16: Add `WeekPathView` (per-week list with cascade + bottom mask)

The body of one TabView page.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift`

- [ ] **Step 16.1: Create the file**

```swift
import SwiftUI

/// One TabView page in the iOS Path body. Renders the focused week's
/// pebble list with a per-pebble reveal cascade that re-runs whenever
/// the entry's identity or pebble count changes.
///
/// The bottom of the list fades behind the New button via a vertical
/// gradient mask (opaque 0% → opaque 85% → transparent 100%).
struct WeekPathView: View {
    let entry: WeekRollEntry
    let onTap: (Pebble) -> Void
    let onDelete: (Pebble) -> Void

    @State private var revealedCount = 0

    /// Stagger between consecutive pebble reveals.
    private static let revealStagger: Duration = .milliseconds(80)

    /// `cascadeKey` keys the reveal `.task`. Combining `weekStart` with
    /// `pebbles.count` guarantees the cascade replays both on week swap
    /// and on a same-week pebble create/delete.
    private var cascadeKey: String {
        "\(entry.weekStart.timeIntervalSince1970)-\(entry.pebbles.count)"
    }

    var body: some View {
        Group {
            if entry.pebbles.isEmpty {
                emptyState
            } else {
                List {
                    Section {
                        ForEach(Array(entry.pebbles.enumerated()), id: \.element.id) { index, pebble in
                            if index < revealedCount {
                                PathPebbleRow(
                                    pebble: pebble,
                                    positionIndex: index,
                                    onTap: { onTap(pebble) },
                                    onDelete: { onDelete(pebble) }
                                )
                                .listRowBackground(Color.pebblesListRow)
                                .listRowSeparator(.hidden)
                                .transition(.opacity.combined(with: .move(edge: .top)))
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .mask(
                    LinearGradient(
                        stops: [
                            .init(color: .black, location: 0.0),
                            .init(color: .black, location: 0.85),
                            .init(color: .clear, location: 1.0),
                        ],
                        startPoint: .top, endPoint: .bottom
                    )
                )
            }
        }
        .task(id: cascadeKey) {
            revealedCount = 0
            for index in 0..<entry.pebbles.count {
                try? await Task.sleep(for: Self.revealStagger)
                withAnimation(.easeOut(duration: 0.25)) {
                    revealedCount = index + 1
                }
            }
        }
    }

    private var emptyState: some View {
        VStack {
            Spacer()
            Text("No pebbles this week")
                .foregroundStyle(Color.pebblesMutedForeground)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}
```

- [ ] **Step 16.2: Build the iOS target — clean compile**

- [ ] **Step 16.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift
git commit -m "feat(ios): add WeekPathView with cascade + bottom mask (#388)"
```

---

## Task 17: Add `NewPebbleButton`

Full-width pill button for the bottom inset.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift`

- [ ] **Step 17.1: Create the file**

```swift
import SwiftUI

/// Full-width "New pebble" button shown above `PathBottomBar` in
/// `PathView.safeAreaInset(.bottom)`.
///
/// Background is opaque (`pebblesBackground` light, `pebblesForeground`
/// dark) so the gradient-masked list above appears to fade behind it.
struct NewPebbleButton: View {
    let onTap: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private var fill: Color {
        colorScheme == .dark ? Color.pebblesForeground : Color.pebblesBackground
    }

    var body: some View {
        Button(action: onTap) {
            Text("New pebble")
                .font(.custom("Ysabeau-SemiBold", size: 17))
                .foregroundStyle(Color.pebblesAccent)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Capsule().fill(fill))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New pebble")
    }
}
```

- [ ] **Step 17.2: Build the iOS target — clean compile**

- [ ] **Step 17.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift
git commit -m "feat(ios): add NewPebbleButton (#388)"
```

---

## Task 18: Add `PathBottomBar`

Glyph button on the left, bounce + karma stats on the right.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift`

- [ ] **Step 18.1: Create the file**

```swift
import SwiftUI

/// Bottom nav for the iOS Path screen. Replaces the system tab bar.
///
/// Glyph (left) and stat cluster (right) all push to ProfileView via
/// the `onProfile` callback. Karma uses the `sparkle` symbol; bounce
/// uses `circle.hexagongrid` (issue spec; implementer should verify
/// against Figma — `.fill` variant may apply).
struct PathBottomBar: View {
    let karma: Int?
    let bounce: Int?
    let onProfile: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private var numberColor: Color {
        colorScheme == .dark ? Color.pebblesAccent : Color.pebblesForeground
    }

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onProfile) {
                Image(systemName: "person.crop.circle")
                    .font(.title2)
                    .foregroundStyle(Color.pebblesAccent)
                    .frame(width: 40, height: 40)
            }
            .accessibilityLabel("Profile")

            Spacer(minLength: 0)

            Button(action: onProfile) {
                HStack(spacing: 16) {
                    stat(systemImage: "circle.hexagongrid", value: bounce, label: "bounce")
                    stat(systemImage: "sparkle",            value: karma,  label: "karma")
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Bounce \(bounce.map(String.init) ?? "—"), Karma \(karma.map(String.init) ?? "—")")
        }
        .padding(.horizontal, 16)
    }

    @ViewBuilder
    private func stat(systemImage: String, value: Int?, label: LocalizedStringKey) -> some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
                .foregroundStyle(Color.pebblesAccent)
            VStack(alignment: .leading, spacing: 0) {
                Text(value.map { "\($0)" } ?? "—")
                    .font(.custom("Ysabeau-SemiBold", size: 17))
                    .foregroundStyle(numberColor)
                Text(label)
                    .font(.caption)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
    }
}
```

- [ ] **Step 18.2: Build the iOS target — clean compile**

- [ ] **Step 18.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift
git commit -m "feat(ios): add PathBottomBar (#388)"
```

---

## Task 19: Refactor `PathView` to the new layout

Top: weeks roll + header. Body: TabView paged by entry. Bottom inset: New button + bottom bar. Removes the old per-week cascade state and the Replay-onboarding toolbar item.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 19.1: Replace `PathView` contents**

Replace the entire contents of `apps/ios/Pebbles/Features/Path/PathView.swift` with:

```swift
import SwiftUI
import os

private enum PathRoute: Hashable {
    case profile
}

struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(PathStatsService.self) private var stats

    @State private var pebbles: [Pebble] = []
    @State private var entries: [WeekRollEntry] = []
    @State private var focusedWeekStart: Date = Date()
    @State private var navPath = NavigationPath()
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?
    @State private var pendingDeletion: Pebble?
    @State private var deleteError: String?
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    private var isoCalendar: Calendar { Calendar(identifier: .iso8601) }
    private var today: Date { Date() }

    var body: some View {
        NavigationStack(path: $navPath) {
            content
                .navigationDestination(for: PathRoute.self) { route in
                    switch route {
                    case .profile: ProfileView()
                    }
                }
                .toolbar(.hidden, for: .navigationBar)
                .pebblesScreen()
        }
        .task { await load() }
        .task { await stats.load() }
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                selectedPebbleId = newPebbleId
                Task { await load() }
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            PebbleDetailSheet(pebbleId: id, onPebbleUpdated: {
                Task { await load() }
            })
        }
        .confirmationDialog(
            pendingDeletion.map { "Delete \($0.name)?" } ?? "",
            isPresented: Binding(
                get: { pendingDeletion != nil },
                set: { if !$0 { pendingDeletion = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingDeletion
        ) { pebble in
            Button("Delete", role: .destructive) {
                Task { await delete(pebble) }
            }
            Button("Cancel", role: .cancel) {
                pendingDeletion = nil
            }
        } message: { _ in
            Text("This can't be undone.")
        }
        .alert(
            "Couldn't delete",
            isPresented: Binding(
                get: { deleteError != nil },
                set: { if !$0 { deleteError = nil } }
            ),
            presenting: deleteError
        ) { _ in
            Button("OK", role: .cancel) { deleteError = nil }
        } message: { message in
            Text(message)
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            Text(loadError)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 16) {
                WeekRollView(
                    entries: entries,
                    focusedWeekStart: $focusedWeekStart,
                    calendar: isoCalendar
                )
                WeekHeaderView(
                    entries: entries,
                    focusedWeekStart: $focusedWeekStart,
                    calendar: isoCalendar,
                    today: today
                )
                TabView(selection: $focusedWeekStart) {
                    ForEach(entries) { entry in
                        WeekPathView(
                            entry: entry,
                            onTap: { pebble in selectedPebbleId = pebble.id },
                            onDelete: { pebble in pendingDeletion = pebble }
                        )
                        .tag(entry.weekStart)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 12) {
                    NewPebbleButton(onTap: { isPresentingCreate = true })
                    PathBottomBar(
                        karma: stats.karma,
                        bounce: stats.bounce,
                        onProfile: { navPath.append(PathRoute.profile) }
                    )
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
    }

    private func load() async {
        do {
            let result: [Pebble] = try await supabase.client
                .rpc("path_pebbles")
                .execute()
                .value
            self.pebbles = result
            self.entries = WeekRollBuilder.build(
                pebbles: result, calendar: isoCalendar, today: today
            )
            // First load: focus current week (always present in entries).
            if focusedWeekStart == .distantPast || !entries.contains(where: { $0.weekStart == focusedWeekStart }) {
                let currentWeekStart = WeekRollBuilder.build(
                    pebbles: [], calendar: isoCalendar, today: today
                ).first?.weekStart ?? today
                // Prefer the existing current-week entry by Date equality.
                if let cur = entries.first(where: { $0.weekStart == currentWeekStart }) {
                    focusedWeekStart = cur.weekStart
                } else if let closest = entries.min(by: {
                    abs($0.weekStart.timeIntervalSince(focusedWeekStart)) <
                    abs($1.weekStart.timeIntervalSince(focusedWeekStart))
                }) {
                    focusedWeekStart = closest.weekStart
                }
            }
            self.isLoading = false
        } catch {
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load your pebbles."
            self.isLoading = false
        }
    }

    private func delete(_ pebble: Pebble) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            await load()
        } catch {
            logger.error("delete pebble failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

#Preview {
    PathView()
        .environment(SupabaseService())
}
```

- [ ] **Step 19.2: Build the iOS target**

Expected: clean compile. The old `WeekSectionHeader` is no longer referenced from `PathView`, but the file is still on disk — that's deleted in Task 22.

- [ ] **Step 19.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ios): rewrite PathView with weeks-roll + tabbed body (#388)"
```

---

## Task 20: Strip the outer `NavigationStack` from `LabView`

LabView is now a destination of Profile's stack — it must not own its own.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/LabView.swift`

- [ ] **Step 20.1: Replace the `body`**

Open `apps/ios/Pebbles/Features/Lab/LabView.swift`. Replace:

```swift
var body: some View {
    NavigationStack {
        content
            .navigationTitle("Lab")
            .pebblesScreen()
    }
    .task { await load() }
}
```

with:

```swift
var body: some View {
    content
        .navigationTitle("Lab")
        .pebblesScreen()
        .task { await load() }
}
```

- [ ] **Step 20.2: Build the iOS target**

- [ ] **Step 20.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/LabView.swift
git commit -m "refactor(ios): strip outer NavigationStack from LabView (#388)"
```

---

## Task 21: Add the Lab nav row + Replay onboarding row to `ProfileView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

- [ ] **Step 21.1: Add the Discover section + Replay row + onboarding sheet**

Add `@State private var isPresentingOnboarding = false` near the other state lines.

Add a new `Section` at the top of the `List` (above the existing `Section("Stats")`):

```swift
Section {
    NavigationLink {
        LabView()
    } label: {
        Label("Lab", systemImage: "lightbulb.max")
    }
    .listRowBackground(Color.pebblesListRow)
} header: {
    Text("Discover")
}
```

In the existing `Section("Legal")`, add a new row above "Terms":

```swift
ProfileNavRow(title: "Replay onboarding", systemImage: "play.circle") {
    isPresentingOnboarding = true
}
.listRowBackground(Color.pebblesListRow)
```

After the existing `.sheet(item: $presentedLegalDoc)` modifier, add:

```swift
.fullScreenCover(isPresented: $isPresentingOnboarding) {
    OnboardingView(steps: OnboardingSteps.all) {
        isPresentingOnboarding = false
    }
}
```

- [ ] **Step 21.2: Build the iOS target — clean compile**

- [ ] **Step 21.3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/ProfileView.swift
git commit -m "feat(ios): profile gains Lab link + onboarding replay (#388)"
```

---

## Task 22: Delete `MainTabView` and the old `WeekSectionHeader`

**Files:**
- Delete: `apps/ios/Pebbles/Features/Main/MainTabView.swift`
- Delete: `apps/ios/Pebbles/Features/Path/Components/WeekSectionHeader.swift`

- [ ] **Step 22.1: Confirm no remaining references**

```bash
grep -rn "MainTabView\b\|WeekSectionHeader\b" apps/ios/Pebbles --include="*.swift"
```

Expected output: empty. If anything is still referenced, fix that file before deleting.

- [ ] **Step 22.2: Delete the files**

```bash
rm apps/ios/Pebbles/Features/Main/MainTabView.swift
rm apps/ios/Pebbles/Features/Path/Components/WeekSectionHeader.swift
rmdir apps/ios/Pebbles/Features/Main 2>/dev/null || true
```

(`rmdir` only succeeds if the directory is empty, which it should be.)

- [ ] **Step 22.3: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `Pebbles.xcodeproj` is rewritten without the deleted files. Open in Xcode to confirm a clean build.

- [ ] **Step 22.4: Commit**

```bash
git add -A apps/ios/Pebbles apps/ios/Pebbles.xcodeproj
git commit -m "chore(ios): remove MainTabView and old WeekSectionHeader (#388)"
```

---

## Task 23: Swap `MainTabView` for `PathView` in `RootView`

**Files:**
- Modify: `apps/ios/Pebbles/RootView.swift`

- [ ] **Step 23.1: Replace `MainTabView()` with `PathView()`**

In `apps/ios/Pebbles/RootView.swift`, the `if canShowAuthedTabs` branch currently reads:

```swift
if canShowAuthedTabs {
    MainTabView()
        .fullScreenCover(isPresented: $isPresentingOnboarding) { ... }
}
```

Change to:

```swift
if canShowAuthedTabs {
    PathView()
        .fullScreenCover(isPresented: $isPresentingOnboarding) {
            OnboardingView(steps: OnboardingSteps.all) {
                hasSeenOnboarding = true
                isPresentingOnboarding = false
            }
        }
}
```

(Keep the closure body identical — only the view name changes.)

- [ ] **Step 23.2: Build the iOS target — clean compile**

- [ ] **Step 23.3: Commit**

```bash
git add apps/ios/Pebbles/RootView.swift
git commit -m "feat(ios): root view shows PathView post-auth (#388)"
```

---

## Task 24: Add new localized strings

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 24.1: Open the catalog in Xcode**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode. Confirm the build has just passed so any new auto-extracted keys (`New pebble`, `No pebbles this week`, `Discover`, `Replay onboarding`, `Previous week`, `Next week`, `Profile`, `bounce`, `karma`) are present.

- [ ] **Step 24.2: Fill in French translations**

For each new key, supply the FR value:

| Source key | FR value |
|---|---|
| `New pebble` | `Nouveau pebble` |
| `No pebbles this week` | `Aucun pebble cette semaine` |
| `Discover` | `Découvrir` |
| `Replay onboarding` | `Revoir l'introduction` |
| `Previous week` | `Semaine précédente` |
| `Next week` | `Semaine suivante` |
| `Profile` | `Profil` |
| `bounce` | `bounce` |
| `karma` | `karma` |

Mark each row's state as `Translated` in both `en` and `fr` columns. No row should show `New` or `Stale` after this step.

- [ ] **Step 24.3: Build the iOS target**

Expected: clean. The xcstrings file's diff in git should show the new entries.

- [ ] **Step 24.4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "i18n(ios): add path week view strings + FR translations (#388)"
```

---

## Task 25: Update Arkaik bundle map

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 25.1: Apply Arkaik changes via the skill**

Invoke the `arkaik` skill with the following intent:

> Apply these changes to `docs/arkaik/bundle.json`:
> - Delete the node for `MainTabView`.
> - Update the `LabView` node: parent changes from `MainTabView (tab)` to `ProfileView (push)`.
> - Add child nodes under `PathView`: `WeekRollView`, `WeekHeaderView`, `WeekPathView`, `PathBottomBar`, `NewPebbleButton`, `PathPebbleSnapThumb`, `WeekRollCairnCell`.
> - Add an edge from `PathBottomBar` (inside `PathView`) to `ProfileView` (push).

- [ ] **Step 25.2: Inspect the diff**

```bash
git diff docs/arkaik/bundle.json
```

Confirm the deletions, parent change, and additions are present. No other nodes should have moved.

- [ ] **Step 25.3: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(arkaik): update map for week path view (#388)"
```

---

## Task 26: Full-repo lint, build, and manual smoke

**Files:** none (verification only)

- [ ] **Step 26.1: Repo-root lint**

```bash
npm run lint
```

Expected: clean.

- [ ] **Step 26.2: Repo-root build**

```bash
npm run build
```

Expected: clean. This compiles all workspaces and exercises the regenerated Supabase types.

- [ ] **Step 26.3: iOS test suite (Cmd+U in Xcode)**

Expected: every `@Suite` green, including:
- `WeekRollBuilderTests`
- `PathPebbleRowGeometryTests`
- `WeekHeaderFormatTests`
- `PebbleDecodingTests`
- All previously-passing suites still pass.

- [ ] **Step 26.4: Manual smoke checklist (run in iOS Simulator)**

Run the app and verify each line below. Tick them off in your PR description.

- Cold launch → splash → Path with current week focused, cairn animates, pebbles cascade in.
- Swipe path body left/right several weeks; chevrons reflect bounds; date header updates with year suffix when applicable.
- Tap a cairn 3 weeks back; roll snaps and body slides.
- Tap chevron near boundary — disabled chevron does not respond; opposite chevron still works.
- Create a pebble in the current week; appears at top of list; cascade re-runs.
- Create a retro pebble dated 2 years ago; new entry appears in roll left of current; navigate to it.
- Long-press a pebble → confirm delete → row removes.
- Tap glyph in bottom bar → Profile pushes; back chevron returns.
- Tap karma stat in bottom bar → Profile pushes (same target).
- Tap Lab in Profile → Lab pushes; back returns to Profile.
- Tap "Replay onboarding" in Profile Legal section → onboarding fullscreen plays; close returns to Profile.
- Light + dark mode parity, including the New button and stat number colors.
- Empty current week (sign in as a fresh user or wipe data): empty state visible, New button reachable.
- Pebble row with photo: rotation correct (even = -7°, odd = +4°); row height matches the spec.
- Pebble row with `intensity = 3` (large): big thumbnail, primary fill, light glyph stroke.

- [ ] **Step 26.5: No commit needed** — verification step. If smoke flags anything, fix in a follow-up commit before opening the PR.

---

## Task 27: Open the PR

**Files:** none

- [ ] **Step 27.1: Push the branch**

```bash
git push -u origin feat/388-week-path-view
```

- [ ] **Step 27.2: Verify issue 388 labels and milestone**

```bash
gh issue view 388 --json labels,milestone
```

Note the labels (expected: `core`, `feat`, `ios`, `ui`) and milestone. The PR should inherit them (with `bug` → `fix` if applicable, but this issue is `feat`).

- [ ] **Step 27.3: Open the PR**

```bash
gh pr create --title "feat(ios): split path into week views (#388)" --body "$(cat <<'EOF'
Resolves #388

## Summary
- New iOS Path screen: horizontal weeks roll + paginated per-week pebble list
- Large pebble variant (intensity = 3) renders with primary fill + light glyph stroke
- Photos attach to rows with index-parity rotation (-7° / +4°)
- `MainTabView` removed; PathView is the post-auth root
- Lab moves into Profile; onboarding replay relocates to Profile
- New `path_pebbles` RPC backs the redesign with a single round trip

## Test plan
- [x] `WeekRollBuilderTests` green
- [x] `PathPebbleRowGeometryTests` green
- [x] `WeekHeaderFormatTests` green
- [x] `PebbleDecodingTests` green
- [x] Manual smoke checklist (see plan task 26.4)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 27.4: Apply labels and milestone**

After creation, attach `core,feat,ios,ui` and the milestone:

```bash
gh pr edit --add-label "core,feat,ios,ui"
# Then add the milestone via the gh CLI or web UI — confirm the target with the maintainer first.
```

- [ ] **Step 27.5: Done**

The PR URL prints to stdout from `gh pr create`. Share it with the maintainer and request review.
