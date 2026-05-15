# Ripples — iOS Bottom-Bar Badge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the visual "Bounce" stat on the iOS Path bottom bar with a new dynamic **Ripples** badge (44×44, six bezier strokes + center digit) driven by a new DB view that buckets pebbles-in-the-last-28-days into 7 levels (0–6) and exposes an `active_today` flag.

**Architecture:** Three independent, thin layers — (1) a new `public.v_ripple` Postgres view (no RPC, no breaking change to `v_bounce`); (2) a `RippleSummary` decodable model loaded in parallel with karma/bounce by the existing `PathStatsService`; (3) a pure-SwiftUI `RippleBadge` view composed of six hand-ported `Shape` structs whose stroke colors are computed by a single pure resolver function. Bounce data, admin analytics, and Profile-screen surfaces are deliberately untouched.

**Tech Stack:** Swift 5 + SwiftUI (iOS 17, `@Observable`), Supabase Postgres + PostgREST, Swift Testing.

**Spec:** `docs/superpowers/specs/2026-05-15-ripples-design.md`

**Issue:** #442 (labels: `core`, `feat`, `ios`, `ui`)

---

### Task 0: Create the feature branch

**Files:** none (git only)

- [ ] **Step 1: Create and switch to the branch from `main`**

```bash
git checkout main
git pull --ff-only
git checkout -b feat/442-ios-ripples
```

- [ ] **Step 2: Confirm clean status**

Run: `git status`
Expected: `On branch feat/442-ios-ripples` / `nothing to commit, working tree clean`.

---

### Task 1: Create the `v_ripple` DB view

**Files:**
- Create: `packages/supabase/supabase/migrations/20260515000001_v_ripple.sql`
- Modify (auto-regen): `packages/supabase/types/database.ts`

- [ ] **Step 1: Create the migration file**

Write `packages/supabase/supabase/migrations/20260515000001_v_ripple.sql`:

```sql
-- Migration: v_ripple
-- New read view powering the iOS "Ripples" badge (issue #442).
-- Counts pebbles created (by created_at, NOT happened_at) in the
-- trailing 28 days and buckets into 7 levels (0–6). Also exposes
-- active_today: did the user create any pebble today (server date).
--
-- v_bounce is left untouched — Ripples is a deliberate parallel
-- signal, not a rename or replacement of the bounce data layer.

create view public.v_ripple as
select
  u.id as user_id,
  coalesce(stats.pebbles_28d, 0) as pebbles_28d,
  coalesce(stats.active_today, false) as active_today,
  case
    when coalesce(stats.pebbles_28d, 0) = 0   then 0
    when stats.pebbles_28d between  1 and  4  then 1
    when stats.pebbles_28d between  5 and  8  then 2
    when stats.pebbles_28d between  9 and 12  then 3
    when stats.pebbles_28d between 13 and 16  then 4
    when stats.pebbles_28d between 17 and 20  then 5
    else 6
  end::smallint as ripple_level
from auth.users u
left join lateral (
  select
    count(*) as pebbles_28d,
    bool_or(pb.created_at::date = current_date) as active_today
  from public.pebbles pb
  where pb.user_id = u.id
    and pb.created_at >= now() - interval '28 days'
) stats on true;
```

- [ ] **Step 2: Apply the migration locally and regenerate types**

Run:
```bash
npm run db:reset --workspace=packages/supabase
npm run db:types --workspace=packages/supabase
```

Expected: `db:reset` re-applies all migrations cleanly, ending with `Finished supabase db reset on branch main.` (or similar). `db:types` writes a fresh `packages/supabase/types/database.ts` that now includes a `v_ripple` entry in `Views`.

- [ ] **Step 3: Sanity-check the view shape by grep**

Run: `grep -A 20 "v_ripple" packages/supabase/types/database.ts | head -30`
Expected: a `Row:` block listing `ripple_level: number`, `pebbles_28d: number`, `active_today: boolean`, `user_id: string`.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20260515000001_v_ripple.sql \
        packages/supabase/types/database.ts
git commit -m "feat(db): add v_ripple view for ios ripples badge"
```

---

### Task 2: Add the three new color tokens to `Assets.xcassets`

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/RippleDefault.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/RippleActive.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/RippleInactive.colorset/Contents.json`

- [ ] **Step 1: Create `RippleDefault.colorset`**

Make the directory `apps/ios/Pebbles/Resources/Assets.xcassets/RippleDefault.colorset/` and write `Contents.json`:

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xE4",
          "green" : "0xE4",
          "red" : "0xF0"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x24",
          "green" : "0x20",
          "red" : "0x2E"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

(Light `#F0E4E4`, dark `#2E2024` — RGB hex bytes split into `red` / `green` / `blue` components.)

- [ ] **Step 2: Create `RippleActive.colorset`**

Make the directory and write `Contents.json`:

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x7A",
          "green" : "0x7A",
          "red" : "0xC0"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x7A",
          "green" : "0x7A",
          "red" : "0xC0"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

(Light + dark both `#C07A7A` per spec.)

- [ ] **Step 3: Create `RippleInactive.colorset`**

Make the directory and write `Contents.json`:

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xD2",
          "green" : "0xD0",
          "red" : "0xE0"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x64",
          "green" : "0x5E",
          "red" : "0x7A"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

(Light `#E0D0D2`, dark `#7A5E64`.)

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/RippleDefault.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/RippleActive.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/RippleInactive.colorset
git commit -m "feat(ios): add ripple color tokens (default/active/inactive)"
```

---

### Task 3: Expose the new colors via `Color+Pebbles`

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Color+Pebbles.swift`

- [ ] **Step 1: Add three static accessors**

In `apps/ios/Pebbles/Theme/Color+Pebbles.swift`, insert after the `pebblesAccentSoft` line (line 13) and before the `pebblesAccentHex` block:

```swift
    static let rippleDefault  = Color("RippleDefault")
    static let rippleActive   = Color("RippleActive")
    static let rippleInactive = Color("RippleInactive")
```

- [ ] **Step 2: Commit**

```bash
git add apps/ios/Pebbles/Theme/Color+Pebbles.swift
git commit -m "feat(ios): expose ripple color tokens via Color extension"
```

---

### Task 4: Create `RippleSummary` decodable model (TDD)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/RippleSummary.swift`
- Test: `apps/ios/PebblesTests/RippleSummaryDecodingTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/RippleSummaryDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("RippleSummary decoding")
struct RippleSummaryDecodingTests {

    @Test("decodes the canonical v_ripple row shape")
    func decodesCanonicalShape() throws {
        let json = #"""
        {
          "ripple_level": 3,
          "pebbles_28d": 11,
          "active_today": true
        }
        """#.data(using: .utf8)!

        let summary = try JSONDecoder().decode(RippleSummary.self, from: json)

        #expect(summary.rippleLevel == 3)
        #expect(summary.pebbles28d == 11)
        #expect(summary.activeToday == true)
    }

    @Test("decodes a zero/false row (resting state)")
    func decodesRestingState() throws {
        let json = #"""
        {
          "ripple_level": 0,
          "pebbles_28d": 0,
          "active_today": false
        }
        """#.data(using: .utf8)!

        let summary = try JSONDecoder().decode(RippleSummary.self, from: json)

        #expect(summary.rippleLevel == 0)
        #expect(summary.pebbles28d == 0)
        #expect(summary.activeToday == false)
    }
}
```

- [ ] **Step 2: Verify the test fails**

Run (from `apps/ios/`):
```bash
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/RippleSummaryDecodingTests 2>&1 | tail -20
```
Expected: build error — `cannot find 'RippleSummary' in scope` or similar.

- [ ] **Step 3: Write the model**

Create `apps/ios/Pebbles/Features/Path/Models/RippleSummary.swift`:

```swift
import Foundation

/// Mirrors the `public.v_ripple` view. `ripple_level` is a 0–6 integer
/// bucketed from pebbles-in-last-28-days (counted by `created_at`).
/// `active_today` is true iff the user created at least one pebble
/// today (server-side `current_date`).
struct RippleSummary: Decodable {
    let rippleLevel: Int
    let pebbles28d: Int
    let activeToday: Bool

    enum CodingKeys: String, CodingKey {
        case rippleLevel = "ripple_level"
        case pebbles28d  = "pebbles_28d"
        case activeToday = "active_today"
    }
}
```

- [ ] **Step 4: Verify the test passes**

Re-run the same xcodebuild test command from Step 2.
Expected: `Test Suite 'RippleSummary decoding' passed` with 2/2 tests passing.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/RippleSummary.swift \
        apps/ios/PebblesTests/RippleSummaryDecodingTests.swift
git commit -m "feat(ios): add RippleSummary decodable model"
```

---

### Task 5: Wire `ripple` into `PathStatsService`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`

- [ ] **Step 1: Add the field and parallel fetch**

Replace the entire contents of `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift` with:

```swift
import Foundation
import os
import Supabase

/// Shared @Observable wrapper around `v_karma_summary`, `v_bounce`, and
/// `v_ripple`. PathView (bottom bar) and ProfileView read the same
/// instance so a reload from one screen is visible to the other.
@Observable
@MainActor
final class PathStatsService {
    var karma: Int?
    var bounce: Int?
    var ripple: RippleSummary?

    private var isLoading = false
    private(set) var hasLoaded = false

    private let supabase: SupabaseService
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path-stats")

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    /// Idempotent. Returns immediately if already loaded or currently loading,
    /// so it is safe to call from every view's `.task` modifier.
    func load() async {
        guard !hasLoaded, !isLoading else { return }
        await performLoad()
    }

    /// Forces a network reload, bypassing the `hasLoaded` cache. Still guards
    /// against concurrent calls so spam-tapping cannot fan out parallel queries.
    func refresh() async {
        guard !isLoading else { return }
        await performLoad()
    }

    private func performLoad() async {
        isLoading = true
        defer { isLoading = false }

        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary").select("total_karma, pebbles_count")
            .single().execute().value
        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce").select("bounce_level, active_days")
            .single().execute().value
        async let rippleResult: RippleSummary = supabase.client
            .from("v_ripple").select("ripple_level, pebbles_28d, active_today")
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

        do {
            self.ripple = try await rippleResult
        } catch {
            logger.error("ripple fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        hasLoaded = true
    }
}
```

- [ ] **Step 2: Verify the project builds**

Run (from `apps/ios/`):
```bash
xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

(Note: `PathBottomBar` and `PathView` still reference `stats.bounce` but nothing references `stats.ripple` yet — that lands in Tasks 9–10. The build should still pass because we only added a new property.)

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift
git commit -m "feat(ios): fetch v_ripple alongside karma and bounce"
```

---

### Task 6: Pure stroke-color resolver (TDD)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/RippleStrokeColor.swift`
- Test: `apps/ios/PebblesTests/RippleStrokeColorTests.swift`

The resolver is the one piece of business logic in the badge — extracting it as a top-level pure function lets us cover all 14 `(level, activeToday)` cases without instantiating any SwiftUI view. We use a small `RippleStrokeTone` enum (not `Color`, which is hard to compare in tests) so the test asserts on tone identity, and the view maps tone → asset color.

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/RippleStrokeColorTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("RippleStrokeTone resolver")
struct RippleStrokeColorTests {

    /// Truth table from issue #442. Row format: (level, active, expected-tones-for-strokes-1-through-6).
    /// `.default` means "outside the user's level".
    /// `.active`  means "within level AND user is active today".
    /// `.inactive` means "within level AND user is not active today".
    private struct Row {
        let level: Int
        let activeToday: Bool
        let expected: [RippleStrokeTone] // length 6
    }

    private let rows: [Row] = [
        Row(level: 0, activeToday: true,  expected: [.default, .default, .default, .default, .default, .default]),
        Row(level: 0, activeToday: false, expected: [.default, .default, .default, .default, .default, .default]),
        Row(level: 1, activeToday: true,  expected: [.active,  .default, .default, .default, .default, .default]),
        Row(level: 1, activeToday: false, expected: [.inactive, .default, .default, .default, .default, .default]),
        Row(level: 2, activeToday: true,  expected: [.active, .active, .default, .default, .default, .default]),
        Row(level: 2, activeToday: false, expected: [.inactive, .inactive, .default, .default, .default, .default]),
        Row(level: 3, activeToday: true,  expected: [.active, .active, .active, .default, .default, .default]),
        Row(level: 3, activeToday: false, expected: [.inactive, .inactive, .inactive, .default, .default, .default]),
        Row(level: 4, activeToday: true,  expected: [.active, .active, .active, .active, .default, .default]),
        Row(level: 4, activeToday: false, expected: [.inactive, .inactive, .inactive, .inactive, .default, .default]),
        Row(level: 5, activeToday: true,  expected: [.active, .active, .active, .active, .active, .default]),
        Row(level: 5, activeToday: false, expected: [.inactive, .inactive, .inactive, .inactive, .inactive, .default]),
        Row(level: 6, activeToday: true,  expected: [.active, .active, .active, .active, .active, .active]),
        Row(level: 6, activeToday: false, expected: [.inactive, .inactive, .inactive, .inactive, .inactive, .inactive]),
    ]

    @Test("matches the issue #442 truth table for all 14 cases")
    func matchesTruthTable() {
        for row in rows {
            for strokeIndex in 1...6 {
                let got = rippleStrokeTone(strokeId: strokeIndex,
                                           level: row.level,
                                           activeToday: row.activeToday)
                let want = row.expected[strokeIndex - 1]
                #expect(got == want,
                        "level=\(row.level) active=\(row.activeToday) stroke=\(strokeIndex): got \(got), want \(want)")
            }
        }
    }
}
```

- [ ] **Step 2: Verify the test fails**

Run (from `apps/ios/`):
```bash
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/RippleStrokeColorTests 2>&1 | tail -20
```
Expected: build error — `cannot find 'RippleStrokeTone'` and `cannot find 'rippleStrokeTone'` in scope.

- [ ] **Step 3: Implement the resolver**

Create `apps/ios/Pebbles/Features/Path/Components/RippleStrokeColor.swift`:

```swift
import SwiftUI

/// Logical color slot for a Ripple stroke. The view maps each tone
/// to a theme-aware `Color` asset.
enum RippleStrokeTone: Equatable {
    case `default`   // outside the user's current level
    case active      // within level, user created a pebble today
    case inactive    // within level, user has NOT created a pebble today
}

/// Pure mapping from `(strokeId, level, activeToday)` to a `RippleStrokeTone`.
/// Encodes the truth table from issue #442 verbatim:
///   - strokeId > level                      → .default
///   - strokeId <= level &&  activeToday     → .active
///   - strokeId <= level && !activeToday     → .inactive
func rippleStrokeTone(strokeId: Int, level: Int, activeToday: Bool) -> RippleStrokeTone {
    if strokeId > level { return .default }
    return activeToday ? .active : .inactive
}

extension RippleStrokeTone {
    /// Resolved theme-aware color for this tone.
    var color: Color {
        switch self {
        case .default:  return .rippleDefault
        case .active:   return .rippleActive
        case .inactive: return .rippleInactive
        }
    }
}
```

- [ ] **Step 4: Verify the test passes**

Re-run the same xcodebuild test command from Step 2.
Expected: `Test Suite 'RippleStrokeTone resolver' passed` with 1/1 test passing (14 assertions inside).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/RippleStrokeColor.swift \
        apps/ios/PebblesTests/RippleStrokeColorTests.swift
git commit -m "feat(ios): add RippleStrokeTone resolver with truth-table tests"
```

---

### Task 7: The six `RippleStroke` shapes

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/RippleStrokes.swift`

Each `Shape` hand-ports the corresponding SVG `<path d="…">` from issue #442. SVG `M x y` → `path.move(to:)`; SVG `C x1 y1 x2 y2 x y` → `path.addCurve(to:control1:control2:)`. Coordinates are authored against a 44×44 viewBox; if `rect.size != 44×44`, scale uniformly via `CGAffineTransform`. No tests here — visual verification is via the `#Preview` in Task 8 and a manual diff against the issue screenshot.

- [ ] **Step 1: Write the shapes**

Create `apps/ios/Pebbles/Features/Path/Components/RippleStrokes.swift`:

```swift
import SwiftUI

/// Hand-ported from the SVG <path d="…"> definitions in issue #442.
/// Authored against a 44×44 viewBox. Each shape scales uniformly when
/// drawn into a non-44×44 rect so RippleBadge renders correctly at
/// any size.

private extension Path {
    /// Apply uniform scale from a 44×44 source viewBox into `rect`.
    mutating func scaleFromRippleViewBox(into rect: CGRect) {
        let sx = rect.width / 44
        let sy = rect.height / 44
        let t  = CGAffineTransform(scaleX: sx, y: sy)
            .concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY))
        self = self.applying(t)
    }
}

struct RippleStroke1: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 25.4147, y: 30.7822))
        p.addCurve(to: CGPoint(x: 16.4741, y: 29.5504),
                   control1: CGPoint(x: 22.7365, y: 31.9714),
                   control2: CGPoint(x: 19.5086, y: 31.8785))
        p.addCurve(to: CGPoint(x: 27.8664, y: 14.941),
                   control1: CGPoint(x: 6.9764,  y: 22.2636),
                   control2: CGPoint(x: 18.3687, y: 7.65428))
        p.addCurve(to: CGPoint(x: 29.2088, y: 27.818),
                   control1: CGPoint(x: 32.662,  y: 18.6202),
                   control2: CGPoint(x: 32.1318, y: 24.1663))
        p.scaleFromRippleViewBox(into: rect)
        return p
    }
}

struct RippleStroke2: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 34.1755, y: 13.5272))
        p.addCurve(to: CGPoint(x: 7.58572, y: 25.087),
                   control1: CGPoint(x: 25.9708, y: 1.34962),
                   control2: CGPoint(x: 4.58761, y: 9.44313))
        p.scaleFromRippleViewBox(into: rect)
        return p
    }
}

struct RippleStroke3: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 36.6088, y: 19.5146))
        p.addCurve(to: CGPoint(x: 10, y: 31.0941),
                   control1: CGPoint(x: 39.4844, y: 34.5339),
                   control2: CGPoint(x: 18.0179, y: 42.6778))
        p.scaleFromRippleViewBox(into: rect)
        return p
    }
}

struct RippleStroke4: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 41.458, y: 26.9565))
        p.addCurve(to: CGPoint(x: 11.4043, y: 40.1005),
                   control1: CGPoint(x: 39.2185, y: 38.9628),
                   control2: CGPoint(x: 23.9232, y: 45.4638))
        p.scaleFromRippleViewBox(into: rect)
        return p
    }
}

struct RippleStroke5: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 7.37405, y: 37.1175))
        p.addCurve(to: CGPoint(x: 10.831, y: 4.78223),
                   control1: CGPoint(x: -1.10595, y: 29.2114),
                   control2: CGPoint(x: 0.748869, y: 9.24398))
        p.scaleFromRippleViewBox(into: rect)
        return p
    }
}

struct RippleStroke6: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 15.4023, y: 2.71506))
        p.addCurve(to: CGPoint(x: 41.9999, y: 21.8993),
                   control1: CGPoint(x: 26.241, y: -0.507724),
                   control2: CGPoint(x: 41.9999, y: 7.38652))
        p.scaleFromRippleViewBox(into: rect)
        return p
    }
}
```

- [ ] **Step 2: Verify the project builds**

Run (from `apps/ios/`):
```bash
xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/RippleStrokes.swift
git commit -m "feat(ios): add six RippleStroke bezier shapes"
```

---

### Task 8: The `RippleBadge` view + preview grid

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/RippleBadge.swift`

- [ ] **Step 1: Write the view**

Create `apps/ios/Pebbles/Features/Path/Components/RippleBadge.swift`:

```swift
import SwiftUI

/// 44×44 ring-and-digit badge representing the user's Ripples level.
/// See `docs/superpowers/specs/2026-05-15-ripples-design.md` and
/// issue #442 for full color/state semantics.
struct RippleBadge: View {
    let level: Int
    let activeToday: Bool

    @Environment(\.colorScheme) private var colorScheme

    private var clampedLevel: Int { min(max(level, 0), 6) }

    private var digitColor: Color {
        switch (colorScheme, activeToday) {
        case (.dark, true):   return .pebblesSurface
        case (.light, true):  return .pebblesForeground
        case (.dark, false),
             (.light, false): return .pebblesMutedForeground
        @unknown default:     return .pebblesForeground
        }
    }

    private func tone(forStroke id: Int) -> RippleStrokeTone {
        rippleStrokeTone(strokeId: id, level: clampedLevel, activeToday: activeToday)
    }

    var body: some View {
        ZStack {
            // Draw outermost first so inner rings paint on top.
            RippleStroke6()
                .stroke(tone(forStroke: 6).color, style: stroke)
                .opacity(0.33)
            RippleStroke5()
                .stroke(tone(forStroke: 5).color, style: stroke)
                .opacity(0.33)
            RippleStroke4()
                .stroke(tone(forStroke: 4).color, style: stroke)
                .opacity(0.33)
            RippleStroke3()
                .stroke(tone(forStroke: 3).color, style: stroke)
                .opacity(0.66)
            RippleStroke2()
                .stroke(tone(forStroke: 2).color, style: stroke)
                .opacity(0.66)
            RippleStroke1()
                .stroke(tone(forStroke: 1).color, style: stroke)

            Text(verbatim: "\(clampedLevel)")
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(digitColor)
        }
        .frame(width: 44, height: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
    }

    private var stroke: StrokeStyle {
        StrokeStyle(lineWidth: 2, lineCap: .round)
    }

    private var accessibilityLabel: LocalizedStringResource {
        activeToday
            ? LocalizedStringResource("Ripple level \(clampedLevel), active today")
            : LocalizedStringResource("Ripple level \(clampedLevel), inactive today")
    }
}

#Preview("All states — light") {
    RipplePreviewGrid()
        .preferredColorScheme(.light)
}

#Preview("All states — dark") {
    RipplePreviewGrid()
        .preferredColorScheme(.dark)
}

private struct RipplePreviewGrid: View {
    var body: some View {
        VStack(spacing: 12) {
            ForEach([true, false], id: \.self) { active in
                HStack(spacing: 8) {
                    Text(verbatim: active ? "active" : "inactive")
                        .font(.caption)
                        .frame(width: 60, alignment: .leading)
                    ForEach(0...6, id: \.self) { level in
                        RippleBadge(level: level, activeToday: active)
                    }
                }
            }
        }
        .padding()
        .background(Color.pebblesPathBackground)
    }
}
```

- [ ] **Step 2: Verify the project builds**

Run (from `apps/ios/`):
```bash
xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Open the preview in Xcode and visually diff**

Open `apps/ios/Pebbles/Features/Path/Components/RippleBadge.swift` in Xcode and run the canvas preview. Confirm:

- The 14-cell grid renders without warnings.
- Active rings are `#C07A7A`; inactive rings are the dusty pink/burgundy depending on scheme; default rings are very faint.
- The digit reads cleanly at 12pt rounded bold in both schemes.
- Stroke 1 sits roughly inside the inner area; strokes 2–6 form sweeping arcs at increasing radii — matches the issue screenshot.

If any stroke looks off, re-check its `addCurve` coordinates against the corresponding `<path d="…">` in issue #442.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/RippleBadge.swift
git commit -m "feat(ios): add RippleBadge view with 14-state preview grid"
```

---

### Task 9: Swap Bounce for Ripples in `PathBottomBar`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift`

- [ ] **Step 1: Rewrite the file**

Replace the entire contents of `apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift` with:

```swift
import SwiftUI

/// Bottom nav for the iOS Path screen. Replaces the system tab bar.
///
/// Left: profile avatar (taps to ProfileView).
/// Right: karma stat (icon + number + caption) followed by the
/// Ripples badge. Karma and badge are independent tap targets so a
/// future Ripples explainer sheet can wire in without restructuring.
struct PathBottomBar: View {
    let karma: Int?
    let ripple: RippleSummary?
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
                karmaStat
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Karma \(karma.map(String.init) ?? "—")")

            Button(action: onProfile) {
                RippleBadge(level: ripple?.rippleLevel ?? 0,
                            activeToday: ripple?.activeToday ?? false)
            }
            .buttonStyle(.plain)
            .padding(.leading, 16)
        }
        .padding(.horizontal, 16)
    }

    private var karmaStat: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkle")
                .foregroundStyle(Color.pebblesAccent)
            VStack(alignment: .leading, spacing: 0) {
                Text(karma.map { "\($0)" } ?? "—")
                    .font(.ysabeauSemibold(17))
                    .foregroundStyle(numberColor)
                Text("karma")
                    .font(.caption)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
    }
}
```

(Notes on this rewrite: `bounce: Int?` prop is gone; `stat(...)` helper is inlined into `karmaStat` since there's only one caller left.)

- [ ] **Step 2: Verify the project builds**

Run (from `apps/ios/`):
```bash
xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -10
```
Expected: **build will fail** because `PathView.swift` still passes `bounce: stats.bounce`. That's expected — Task 10 fixes the call site. Capture the error and move on.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift
git commit -m "feat(ios): swap bounce stat for ripple badge in PathBottomBar"
```

---

### Task 10: Update `PathView` call site + refresh hooks

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Update the `PathBottomBar` call site**

In `apps/ios/Pebbles/Features/Path/PathView.swift`, locate the `PathBottomBar(...)` call at line ~124 and replace:

```swift
                    PathBottomBar(
                        karma: stats.karma,
                        bounce: stats.bounce,
                        onProfile: { navPath.append(PathRoute.profile) }
                    )
```

with:

```swift
                    PathBottomBar(
                        karma: stats.karma,
                        ripple: stats.ripple,
                        onProfile: { navPath.append(PathRoute.profile) }
                    )
```

- [ ] **Step 2: Add `stats.refresh()` to the create sheet callback**

Find:

```swift
            CreatePebbleSheet(onCreated: { newPebbleId in
                selectedPebbleId = newPebbleId
                Task { await load() }
            })
```

Replace with:

```swift
            CreatePebbleSheet(onCreated: { newPebbleId in
                selectedPebbleId = newPebbleId
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                }
            })
```

- [ ] **Step 3: Add `stats.refresh()` to the detail sheet callback**

Find:

```swift
            PebbleDetailSheet(pebbleId: id, onPebbleUpdated: {
                Task { await load() }
            })
```

Replace with:

```swift
            PebbleDetailSheet(pebbleId: id, onPebbleUpdated: {
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                }
            })
```

- [ ] **Step 4: Add `stats.refresh()` to the delete path**

In the `delete(_:)` function, find:

```swift
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            await load()
```

Replace with:

```swift
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            async let timeline: Void = load()
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
```

- [ ] **Step 5: Verify the project builds and tests pass**

Run (from `apps/ios/`):
```bash
xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -10
```
Expected: `** BUILD SUCCEEDED **`.

Then:
```bash
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/RippleSummaryDecodingTests -only-testing:PebblesTests/RippleStrokeColorTests 2>&1 | tail -10
```
Expected: both suites pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ios): wire ripple into PathView and refresh stats after writes"
```

---

### Task 11: Localization — fill in French accessibility strings

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

The two new accessibility labels (`"Ripple level \(level), active today"` and `"Ripple level \(level), inactive today"`) are auto-extracted on every build via `SWIFT_EMIT_LOC_STRINGS=YES`. They will already exist in the catalog in `en` once a build runs; we just need to populate `fr` and mark both as `translated`.

- [ ] **Step 1: Trigger string extraction by building**

Run (from `apps/ios/`):
```bash
xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' >/dev/null 2>&1
grep -c "Ripple level" apps/ios/Pebbles/Resources/Localizable.xcstrings
```
Expected: a non-zero count (the two new keys are now present).

- [ ] **Step 2: Open the catalog in Xcode and fill the French column**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode. For both new rows:

| Key | English | French |
|---|---|---|
| `Ripple level %lld, active today` | (already populated) | `Ondes niveau %lld, actif aujourd'hui` |
| `Ripple level %lld, inactive today` | (already populated) | `Ondes niveau %lld, inactif aujourd'hui` |

Set the **state** of both rows to **Translated** in both `en` and `fr` columns (no `New` / `Stale` remaining). Confirm no other row regressed to `New` / `Stale`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): localize ripple accessibility labels (en/fr)"
```

---

### Task 12: Final verification + PR

**Files:** none (verification + git)

- [ ] **Step 1: Full iOS build + test**

Run (from repo root):
```bash
xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -workspace apps/ios/Pebbles.xcodeproj/project.xcworkspace 2>&1 | tail -20
```

(If the xcodeproj is not a workspace, swap to `-project apps/ios/Pebbles.xcodeproj`.)

Expected: `** TEST SUCCEEDED **`, with `RippleSummaryDecodingTests` and `RippleStrokeColorTests` both green, no regressions in the other existing suites.

- [ ] **Step 2: Workspace-scoped lint (per CLAUDE.md task-size triage)**

Run:
```bash
npm run lint --workspace=apps/ios
```
Expected: green.

- [ ] **Step 3: Smoke-test on simulator**

1. Boot the iOS Simulator and run the app.
2. Sign in as a test user with zero pebbles. Verify the bottom-bar badge shows six pale rings with a "0" digit.
3. Create one pebble. Verify the badge updates: ring 1 fills with `#C07A7A` (active), digit reads "1".
4. Delete that pebble. Verify the badge returns to all-default with "0".
5. Toggle dark mode (Simulator → Features → Toggle Appearance). Verify the badge re-renders with the dark-mode color set and the digit reads in `pebblesSurface` (active) / muted (inactive).
6. Tap the badge. Verify it routes to `ProfileView` (same destination as the karma cluster — separate but identical V1 behaviour).
7. Tap the karma cluster. Verify it still routes to `ProfileView`.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feat/442-ios-ripples
```

- [ ] **Step 5: Open the PR**

Inherit labels and milestone from issue #442 once confirmed with the user. Per memory: PR title in conventional commits, body starts with `Resolves #442`.

```bash
gh pr create --title "feat(ios): introduce ripples badge in path bottom bar" --body "$(cat <<'EOF'
Resolves #442.

## Summary
- New `public.v_ripple` Postgres view: pebbles created in the last 28 days bucketed into 7 levels (0–6), plus an `active_today` flag.
- New `RippleBadge` SwiftUI view (44×44, six bezier strokes + bold 12pt rounded digit) wired into `PathBottomBar`, replacing the visual display of Bounce on Path.
- Three new theme-aware color tokens (`RippleDefault`, `RippleActive`, `RippleInactive`).
- `PathStatsService` now fetches `v_ripple` in parallel with `v_karma_summary` and `v_bounce`.
- `PathView` calls `stats.refresh()` after every pebble create / update / delete so the badge (and karma) update immediately. Incidentally fixes a dormant karma-staleness bug.

Bounce data, admin analytics, and the Profile screen are deliberately untouched. The Perlin/turbulence "dissolve" effect mentioned in the issue is deferred to a follow-up.

## Test plan
- [ ] `xcodebuild test -scheme Pebbles` — RippleSummary decoding + RippleStrokeTone truth table green
- [ ] `npm run lint --workspace=apps/ios` green
- [ ] Manual: zero-pebble user shows all-default badge with "0"
- [ ] Manual: creating today's first pebble flips ring 1 to active without a navigation
- [ ] Manual: dark-mode rendering passes the 14-cell preview grid
- [ ] Manual: badge and karma cluster both tap to ProfileView in V1
- [ ] Manual: `Localizable.xcstrings` has no `New` / `Stale` rows
EOF
)" --label core --label feat --label ios --label ui
```

If milestone needs setting: `gh pr edit <num> --milestone <milestone-name>` after creation (per memory rule, confirm with user first).

- [ ] **Step 6: Report the PR URL back to the user**

---

## Self-review notes (for plan author)

- **Spec coverage:** every numbered Goal in the spec maps to a task (Goal 1 → Task 1; Goal 2 → Task 8; Goal 3 → Tasks 2/3; Goal 4 → Task 5; Goal 5 → Task 9; Goal 6 → Task 10). Non-goals are explicitly absent.
- **Placeholder scan:** no TBD / "implement appropriate" remain; the only deferred items (Perlin, Profile, calendar grid) are listed as non-goals and not referenced in any task.
- **Type consistency:** `RippleSummary.rippleLevel`, `pebbles28d`, `activeToday` appear consistently across Tasks 4, 5, 8, 9, 10; coding keys match the SQL column names from Task 1; `RippleStrokeTone` cases (`default`, `active`, `inactive`) appear consistently in Tasks 6 and 8.
