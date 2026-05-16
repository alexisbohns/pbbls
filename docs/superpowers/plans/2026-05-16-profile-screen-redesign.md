# iOS Profile Screen Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `ProfileView.swift` to match the engaged + new-user mockups from issue #451 — banner, 3 shortcuts, stats card (Ripples + assiduity + counters), collections card, lab card, logout pill — and remove the legacy Bounce/Karma row + sheet surface from iOS.

**Architecture:** Three layers of change. (1) **Relocate** the Ripples primitives from `Features/Path/` to `Features/Shared/Ripples/` so both Path and Profile can consume them without coupling. (2) **Extend** `PathStatsService` to load `daysPracticed` + `assiduity` via the already-deployed `get_profile_engagement` RPC (PR #453). (3) **Replace** `ProfileView`'s `List` body with a `ScrollView` of new composed components, delete the Bounce surface, and stub the gear-button settings sheet for follow-up #452.

**Tech Stack:** SwiftUI (iOS 17+), `@Observable` services, Swift Testing for unit tests, Supabase Swift SDK for the new RPC call, Xcode string catalog (`Localizable.xcstrings`) for en+fr.

**Source spec:** `docs/superpowers/specs/2026-05-16-ios-profile-redesign-and-settings-design.md` § Issue 2
**Data foundation (merged):** PR #453 — `update_profile` + `get_profile_engagement` RPCs, `profiles.glyph_id` FK
**Issue:** #451

---

## File structure

### Created

```
apps/ios/Pebbles/Features/
├── Shared/Ripples/                       (NEW directory)
│   ├── RippleBadge.swift                 (moved from Path/Components/)
│   ├── RippleStrokes.swift               (moved from Path/Components/)
│   ├── RippleStrokeColor.swift           (moved from Path/Components/)
│   └── RippleSummary.swift               (moved from Path/Models/, + extended)
└── Profile/
    ├── Components/                       (NEW components, except deletions below)
    │   ├── ProfileBanner.swift
    │   ├── ProfileShortcutTile.swift
    │   ├── ProfileShortcutsRow.swift
    │   ├── ProfileStatsCard.swift
    │   ├── RipplesRow.swift
    │   ├── AssiduityGrid.swift
    │   ├── ProfileCountersRow.swift
    │   ├── ProfileCollectionsCard.swift
    │   ├── ProfileCollectionCard.swift
    │   ├── ProfileLabCard.swift
    │   └── ProfileLogoutPill.swift
    ├── Models/
    │   └── ProfileEngagement.swift       (decodable for get_profile_engagement)
    └── Sheets/
        └── SettingsStubSheet.swift       (placeholder until #452)
```

### Modified

- `apps/ios/Pebbles/Features/Profile/ProfileView.swift` — full rewrite
- `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift` — drop `bounce`, add `daysPracticed` + `assiduity` + engagement load
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — all new user-facing copy in en + fr

### Deleted

- `apps/ios/Pebbles/Features/Profile/Components/ProfileStatRow.swift`
- `apps/ios/Pebbles/Features/Profile/Components/ProfileNavRow.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Models/BounceSummary.swift`

### Tests added/modified

- `apps/ios/PebblesTests/RippleSummaryDecodingTests.swift` — exists; extend with thresholds tests
- `apps/ios/PebblesTests/ProfileEngagementDecodingTests.swift` — new
- `apps/ios/PebblesTests/AssiduityGridLayoutTests.swift` — new (data → row chunking helper)

---

## Open decisions resolved upfront

These were either deferred in the spec or required by the plan; resolving them here so steps below are unambiguous.

1. **"Days to reach level X" formula.** `RippleSummary` will expose `pebblesToNextLevel: Int?` and `nextLevel: Int?`, computed from the SQL thresholds in `v_ripple` (mirror them as a private static `[Int]` with a comment pointing at `20260516000001_v_ripple_security_filter.sql`). Engagement copy: `"%lld more pebbles to level %lld"` (or `"Max level reached"` when `nextLevel == nil`). No date arithmetic — counting pebbles is honest; pretending to predict calendar days isn't.
2. **Glyph empty state for banner.** `GlyphThumbnail` has no built-in empty state. The banner renders a tinted `RoundedRectangle` with the `scribble` SF Symbol when `profile.glyphId == nil`. Built inline in `ProfileBanner`; not added to `GlyphThumbnail` (out of scope).
3. **"Member since" date.** Use `profile.createdAt.formatted(.dateTime.month(.wide).year())` — yields "May 2026" / "mai 2026" automatically per locale. Copy: `"Member since %@"` localized.
4. **Collections fetch.** Read directly from `CollectionsListView`'s underlying call (a `.from("collections").select(...)` chained query). For this card we need name + pebble count, so use `.select("id, name, pebble_count")` from the existing `collections` table view if it exposes a count column; otherwise fetch counts via `pebble_collections` aggregate. To avoid blocking the rest of this plan on schema spelunking, **scope the card to fetch only `id` + `name` and show name only** — the per-collection pebble count is a small follow-up. If the existing service already returns a count, use it; if not, defer the count copy.
5. **"Replay onboarding" affordance.** Drop entirely from Profile (matches mockup). Devs can still trigger via debug builds. Resolved decision: **(a)** from spec § Open questions item 6.
6. **Profile fetch.** We need `display_name`, `created_at`, `glyph_id`. There is currently no `ProfileService` in iOS (verified by grep). Rather than introduce a new service for one screen, fetch directly inside `ProfileView`'s `.task` via `supabase.client.from("profiles").select("display_name, created_at, glyph_id").single().execute().value`, decoded into a local `ProfileRow` struct. If a `ProfileService` already exists by the time this lands, use it; the dispatch agent should grep once before writing the inline fetch.

---

## Task 1: Create branch

**Files:** none.

- [ ] **Step 1: Branch off main**

```bash
cd /Users/alexis/code/pbbls
git checkout main
git pull --ff-only
git checkout -b feat/451-profile-screen-redesign
```

Expected: `Switched to a new branch 'feat/451-profile-screen-redesign'`.

---

## Task 2: Relocate the Ripples primitives (move files only, no behavior change)

**Files:**
- Move: `apps/ios/Pebbles/Features/Path/Components/RippleBadge.swift` → `apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift`
- Move: `apps/ios/Pebbles/Features/Path/Components/RippleStrokes.swift` → `apps/ios/Pebbles/Features/Shared/Ripples/RippleStrokes.swift`
- Move: `apps/ios/Pebbles/Features/Path/Components/RippleStrokeColor.swift` → `apps/ios/Pebbles/Features/Shared/Ripples/RippleStrokeColor.swift`
- Move: `apps/ios/Pebbles/Features/Path/Models/RippleSummary.swift` → `apps/ios/Pebbles/Features/Shared/Ripples/RippleSummary.swift`

`project.yml` uses a single `path: Pebbles` glob, so no manifest change is required. The new directory will be picked up automatically.

- [ ] **Step 1: Create destination directory and move files**

```bash
cd /Users/alexis/code/pbbls
mkdir -p apps/ios/Pebbles/Features/Shared/Ripples
git mv apps/ios/Pebbles/Features/Path/Components/RippleBadge.swift apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift
git mv apps/ios/Pebbles/Features/Path/Components/RippleStrokes.swift apps/ios/Pebbles/Features/Shared/Ripples/RippleStrokes.swift
git mv apps/ios/Pebbles/Features/Path/Components/RippleStrokeColor.swift apps/ios/Pebbles/Features/Shared/Ripples/RippleStrokeColor.swift
git mv apps/ios/Pebbles/Features/Path/Models/RippleSummary.swift apps/ios/Pebbles/Features/Shared/Ripples/RippleSummary.swift
```

Expected: four `renamed:` entries in `git status`.

- [ ] **Step 2: Regenerate Xcode project**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
```

Expected: `Generated project successfully` (no errors). The pbxproj will be rewritten with new group paths.

- [ ] **Step 3: Build to verify zero regression**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -30
```

Expected: `BUILD SUCCEEDED`. If it fails with "cannot find type 'RippleBadge'", the regeneration didn't catch the move — re-run step 2.

- [ ] **Step 4: Run the existing Ripple tests**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PebblesTests/RippleStrokeColorTests -only-testing:PebblesTests/RippleSummaryDecodingTests -quiet 2>&1 | tail -15
```

Expected: `Test Suite ... passed`. Both suites should pass unchanged since they use `@testable import Pebbles` (module-level), not file-path imports.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore(ios): relocate ripples primitives to features/shared/ripples"
```

---

## Task 3: Extend `RippleSummary` with next-level helpers

**Files:**
- Modify: `apps/ios/Pebbles/Features/Shared/Ripples/RippleSummary.swift`
- Modify: `apps/ios/PebblesTests/RippleSummaryDecodingTests.swift`

Thresholds source (read once before touching code):
`packages/supabase/supabase/migrations/20260516000001_v_ripple_security_filter.sql` lines 15–23:
```
0 pebbles → level 0
1-4       → level 1
5-8       → level 2
9-12      → level 3
13-16     → level 4
17-20     → level 5
21+       → level 6
```

So the lower bound (min pebbles) for each level ≥1 is `[1, 5, 9, 13, 17, 21]`. Level 6 is terminal.

- [ ] **Step 1: Write the failing tests**

Append to `apps/ios/PebblesTests/RippleSummaryDecodingTests.swift`:

```swift
@Suite("RippleSummary level progression")
struct RippleSummaryLevelProgressionTests {

    @Test("pebbles to next level at every level")
    func pebblesToNextLevelTable() {
        let cases: [(level: Int, p28d: Int, expectedRemaining: Int?, expectedNext: Int?)] = [
            (0, 0,  1,   1),  // need 1 pebble to reach level 1
            (1, 1,  4,   2),  // 5 - 1 = 4 to reach level 2
            (1, 4,  1,   2),
            (2, 5,  4,   3),
            (2, 8,  1,   3),
            (3, 12, 1,   4),
            (4, 16, 1,   5),
            (5, 20, 1,   6),
            (6, 21, nil, nil),
            (6, 99, nil, nil)
        ]
        for c in cases {
            let summary = RippleSummary(rippleLevel: c.level, pebbles28d: c.p28d, activeToday: false)
            #expect(summary.pebblesToNextLevel == c.expectedRemaining,
                    "level=\(c.level) p28d=\(c.p28d)")
            #expect(summary.nextLevel == c.expectedNext,
                    "level=\(c.level) p28d=\(c.p28d)")
        }
    }
}
```

Note: the existing `RippleSummary` decodable lacks a memberwise initializer. Add one in Step 3 so tests can construct instances directly.

- [ ] **Step 2: Run test, verify it fails to compile**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PebblesTests/RippleSummaryLevelProgressionTests -quiet 2>&1 | tail -10
```

Expected: build error `value of type 'RippleSummary' has no member 'pebblesToNextLevel'`.

- [ ] **Step 3: Add memberwise init + thresholds + computed properties**

Replace the contents of `apps/ios/Pebbles/Features/Shared/Ripples/RippleSummary.swift` with:

```swift
import Foundation

/// Mirrors the `public.v_ripple` view. `ripple_level` is a 0–6 integer
/// bucketed from pebbles-in-last-28-days (counted by `created_at`).
/// `active_today` is true iff the user created at least one pebble
/// today (server-side `current_date`).
struct RippleSummary: Decodable, Equatable {
    let rippleLevel: Int
    let pebbles28d: Int
    let activeToday: Bool

    enum CodingKeys: String, CodingKey {
        case rippleLevel = "ripple_level"
        case pebbles28d  = "pebbles_28d"
        case activeToday = "active_today"
    }

    init(rippleLevel: Int, pebbles28d: Int, activeToday: Bool) {
        self.rippleLevel = rippleLevel
        self.pebbles28d = pebbles28d
        self.activeToday = activeToday
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.rippleLevel = try c.decode(Int.self, forKey: .rippleLevel)
        self.pebbles28d  = try c.decode(Int.self, forKey: .pebbles28d)
        self.activeToday = try c.decode(Bool.self, forKey: .activeToday)
    }

    /// Minimum `pebbles28d` required to enter levels 1…6.
    /// Source of truth: `packages/supabase/supabase/migrations/20260516000001_v_ripple_security_filter.sql`.
    /// If those thresholds change, update both places.
    private static let levelEntryThresholds: [Int] = [1, 5, 9, 13, 17, 21]

    /// `nil` once the user has reached level 6 (terminal).
    var nextLevel: Int? {
        rippleLevel >= 6 ? nil : rippleLevel + 1
    }

    /// Pebbles still needed in the last-28-days window to reach `nextLevel`.
    /// `nil` once the user has reached level 6.
    var pebblesToNextLevel: Int? {
        guard let next = nextLevel else { return nil }
        let threshold = Self.levelEntryThresholds[next - 1]
        return max(threshold - pebbles28d, 0)
    }
}
```

- [ ] **Step 4: Run tests, verify they pass**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PebblesTests/RippleSummaryLevelProgressionTests -only-testing:PebblesTests/RippleSummaryDecodingTests -quiet 2>&1 | tail -10
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Shared/Ripples/RippleSummary.swift apps/ios/PebblesTests/RippleSummaryDecodingTests.swift
git commit -m "feat(ios): expose next-level progression on rippleSummary"
```

---

## Task 4: Drop Bounce from PathStatsService and delete legacy Profile files

Done together so the project keeps building between steps. The bounce field has no other readers besides ProfileView, but ProfileView still references it — so we touch both in this task.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Components/ProfileStatRow.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Components/ProfileNavRow.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Models/BounceSummary.swift`
- Modify (interim stub): `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

The "interim stub" ProfileView is intentional: it replaces the body with a `Text("Profile WIP")` so the project compiles between tasks 4 and the final rewrite in task 14. The dispatch agent should not be alarmed by it.

- [ ] **Step 1: Strip bounce from PathStatsService**

Apply these edits to `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`:

Replace the doc comment + class header:

```swift
/// Shared @Observable wrapper around `v_karma_summary` and `v_ripple`.
/// PathView (bottom bar) and ProfileView read the same instance so a
/// reload from one screen is visible to the other.
@Observable
@MainActor
final class PathStatsService {
    var karma: Int?
    var ripple: RippleSummary?
```

Remove the `var bounce: Int?` line.

In `performLoad()`, remove the `async let bounceResult: ...` line and the `do { self.bounce = ... } catch { ... }` block. Keep the karma and ripple blocks unchanged. Final body of `performLoad()`:

```swift
private func performLoad() async {
    isLoading = true
    defer { isLoading = false }

    async let karmaResult: KarmaSummary = supabase.client
        .from("v_karma_summary").select("total_karma, pebbles_count")
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
        self.ripple = try await rippleResult
    } catch {
        logger.error("ripple fetch failed: \(error.localizedDescription, privacy: .private)")
    }

    hasLoaded = true
}
```

- [ ] **Step 2: Stub ProfileView so the project compiles**

Replace the entire contents of `apps/ios/Pebbles/Features/Profile/ProfileView.swift` with:

```swift
import SwiftUI

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        // Intentional WIP stub; replaced in task 14 of the #451 plan.
        Text(verbatim: "Profile WIP")
            .navigationTitle("Profile")
            .pebblesScreen()
    }
}

#Preview {
    ProfileView()
        .environment(SupabaseService())
}
```

- [ ] **Step 3: Delete the obsolete files**

```bash
cd /Users/alexis/code/pbbls
git rm apps/ios/Pebbles/Features/Profile/Components/ProfileStatRow.swift
git rm apps/ios/Pebbles/Features/Profile/Components/ProfileNavRow.swift
git rm apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift
git rm apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift
git rm apps/ios/Pebbles/Features/Profile/Models/BounceSummary.swift
```

- [ ] **Step 4: Regenerate and build**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -30
```

Expected: `BUILD SUCCEEDED`. If anything else references `stats.bounce`, `BounceSummary`, `ProfileStatRow`, `ProfileNavRow`, `BounceExplainerSheet`, or `KarmaExplainerSheet`, the build will pinpoint it — fix any stragglers (the spec asserts there are none, but verify).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore(ios): remove bounce surface and legacy profile rows"
```

---

## Task 5: Add `ProfileEngagement` model + load method on `PathStatsService`

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Models/ProfileEngagement.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`
- Create: `apps/ios/PebblesTests/ProfileEngagementDecodingTests.swift`

RPC signature (from `packages/supabase/types/database.ts` and PR #453):
```
get_profile_engagement(p_tz: text) → table(days_practiced int, assiduity boolean[])
```

The Swift SDK decodes a single-row table function call into a one-element array of structs.

- [ ] **Step 1: Write failing decoding test**

Create `apps/ios/PebblesTests/ProfileEngagementDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("ProfileEngagement decoding")
struct ProfileEngagementDecodingTests {

    @Test("decodes the canonical RPC row shape")
    func decodesCanonicalRow() throws {
        let json = #"""
        {
          "days_practiced": 42,
          "assiduity": [false, true, true, false, true, false, false,
                        true,  true, false, false, true, true, false,
                        false, true, true, false, true, false, false,
                        true,  true, false, false, true, true, true]
        }
        """#.data(using: .utf8)!

        let row = try JSONDecoder().decode(ProfileEngagement.self, from: json)

        #expect(row.daysPracticed == 42)
        #expect(row.assiduity.count == 28)
        #expect(row.assiduity.last == true)
    }

    @Test("decodes zero-state correctly")
    func decodesZeroState() throws {
        let json = #"""
        { "days_practiced": 0, "assiduity": [
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false
        ] }
        """#.data(using: .utf8)!

        let row = try JSONDecoder().decode(ProfileEngagement.self, from: json)
        #expect(row.daysPracticed == 0)
        #expect(row.assiduity.allSatisfy { $0 == false })
    }
}
```

- [ ] **Step 2: Verify test fails to compile**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PebblesTests/ProfileEngagementDecodingTests -quiet 2>&1 | tail -10
```

Expected: `cannot find type 'ProfileEngagement' in scope`.

- [ ] **Step 3: Create the model**

Create `apps/ios/Pebbles/Features/Profile/Models/ProfileEngagement.swift`:

```swift
import Foundation

/// One-row result of `public.get_profile_engagement(p_tz text)`.
///
/// `daysPracticed` is the all-time distinct count of calendar days
/// (in the caller's timezone) on which the user created any pebble.
///
/// `assiduity` is a 28-element bool array: index 0 = 27 days ago,
/// index 27 = today, both bucketed in the caller's timezone.
struct ProfileEngagement: Decodable, Equatable {
    let daysPracticed: Int
    let assiduity: [Bool]

    enum CodingKeys: String, CodingKey {
        case daysPracticed = "days_practiced"
        case assiduity
    }
}
```

- [ ] **Step 4: Regenerate, run test**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PebblesTests/ProfileEngagementDecodingTests -quiet 2>&1 | tail -10
```

Expected: tests pass.

- [ ] **Step 5: Extend `PathStatsService` with the engagement loader**

Apply these edits to `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`:

Add two new published properties just after `var ripple: RippleSummary?`:

```swift
    var daysPracticed: Int?
    var assiduity: [Bool]?
```

Inside `performLoad()`, after the `async let rippleResult` declaration, add:

```swift
    async let engagementResult: [ProfileEngagement] = supabase.client
        .rpc("get_profile_engagement", params: ["p_tz": TimeZone.current.identifier])
        .execute().value
```

And after the `do { self.ripple = ... } catch { ... }` block, append:

```swift
    do {
        if let row = try await engagementResult.first {
            self.daysPracticed = row.daysPracticed
            self.assiduity     = row.assiduity
        }
    } catch {
        logger.error("engagement fetch failed: \(error.localizedDescription, privacy: .private)")
    }
```

- [ ] **Step 6: Build**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -20
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(ios): load profile engagement (days practiced + 28-day assiduity)"
```

---

## Task 6: `AssiduityGrid` component

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift`
- Create: `apps/ios/PebblesTests/AssiduityGridLayoutTests.swift`

Pure layout: a `data: [Bool]` of length 28, rendered as a 4-row × 7-column grid of small rounded squares. Active days use `Color.rippleActive`, inactive use `Color.rippleInactive`. The mockup shows the most recent day in the bottom-right corner, so layout is **row-major from oldest to newest**: index 0 (oldest) at top-left, index 27 (today) at bottom-right. The chunking helper is what the test pins down.

- [ ] **Step 1: Write the failing test (chunking helper)**

Create `apps/ios/PebblesTests/AssiduityGridLayoutTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("AssiduityGrid layout helper")
struct AssiduityGridLayoutTests {

    @Test("chunks 28 booleans into 4 rows of 7")
    func chunksIntoRows() {
        let data = (0..<28).map { $0 % 2 == 0 }
        let rows = chunkAssiduity(data, columns: 7)
        #expect(rows.count == 4)
        #expect(rows.allSatisfy { $0.count == 7 })
        #expect(rows[0][0] == data[0])
        #expect(rows[3][6] == data[27])
    }

    @Test("pads the last row with false when count not divisible by columns")
    func padsShortFinalRow() {
        let data = Array(repeating: true, count: 10)
        let rows = chunkAssiduity(data, columns: 7)
        #expect(rows.count == 2)
        #expect(rows[0] == Array(repeating: true, count: 7))
        #expect(rows[1] == [true, true, true, false, false, false, false])
    }

    @Test("handles empty input")
    func handlesEmpty() {
        let rows = chunkAssiduity([], columns: 7)
        #expect(rows.isEmpty)
    }
}
```

- [ ] **Step 2: Verify failure**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PebblesTests/AssiduityGridLayoutTests -quiet 2>&1 | tail -10
```

Expected: `cannot find 'chunkAssiduity' in scope`.

- [ ] **Step 3: Implement the component + helper**

Create `apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift`:

```swift
import SwiftUI

/// Splits a flat array into rows of `columns` width.
/// Pads the final row with `false` if the input length isn't divisible.
/// Returns an empty array for empty input. Used by AssiduityGrid and
/// (later) the stats view's denser variant.
func chunkAssiduity(_ data: [Bool], columns: Int) -> [[Bool]] {
    guard !data.isEmpty, columns > 0 else { return [] }
    var rows: [[Bool]] = []
    var i = 0
    while i < data.count {
        let end = min(i + columns, data.count)
        var row = Array(data[i..<end])
        if row.count < columns {
            row.append(contentsOf: Array(repeating: false, count: columns - row.count))
        }
        rows.append(row)
        i += columns
    }
    return rows
}

struct AssiduityGrid: View {
    let data: [Bool]
    var columns: Int = 7
    var cellSize: CGFloat = 10
    var cellSpacing: CGFloat = 3

    var body: some View {
        let rows = chunkAssiduity(data, columns: columns)
        VStack(spacing: cellSpacing) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: cellSpacing) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, active in
                        RoundedRectangle(cornerRadius: cellSize * 0.25)
                            .fill(active ? Color.rippleActive : Color.rippleInactive)
                            .frame(width: cellSize, height: cellSize)
                    }
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Assiduity grid, last 28 days"))
    }
}

#Preview {
    let sample = (0..<28).map { $0 % 3 != 0 }
    return AssiduityGrid(data: sample)
        .padding()
        .background(Color.pebblesListRow)
}
```

- [ ] **Step 4: Regenerate, test, build**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:PebblesTests/AssiduityGridLayoutTests -quiet 2>&1 | tail -10
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
```

Expected: tests pass; build succeeds.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(ios): add assiduity grid component"
```

---

## Task 7: `RipplesRow` component

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/RipplesRow.swift`

Composition: `RippleBadge` (large variant 44pt — already its fixed size) on the left, level/progress copy in the middle, `AssiduityGrid` on the right.

- [ ] **Step 1: Implement**

```swift
import SwiftUI

struct RipplesRow: View {
    let ripple: RippleSummary?
    let assiduity: [Bool]?

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            RippleBadge(
                level: ripple?.rippleLevel ?? 0,
                activeToday: ripple?.activeToday ?? false
            )

            VStack(alignment: .leading, spacing: 2) {
                Text("Ripples Level \(ripple?.rippleLevel ?? 0)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.pebblesForeground)
                Text(progressCopy)
                    .font(.caption)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }

            Spacer(minLength: 8)

            AssiduityGrid(data: assiduity ?? Array(repeating: false, count: 28))
        }
    }

    private var progressCopy: LocalizedStringResource {
        guard let ripple else { return "Loading…" }
        if let remaining = ripple.pebblesToNextLevel, let next = ripple.nextLevel {
            return "\(remaining) more pebbles to level \(next)"
        } else {
            return "Max level reached"
        }
    }
}

#Preview("Engaged") {
    RipplesRow(
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 11, activeToday: true),
        assiduity: (0..<28).map { $0 % 2 == 0 }
    )
    .padding()
}

#Preview("Empty") {
    RipplesRow(ripple: nil, assiduity: nil)
        .padding()
}
```

- [ ] **Step 2: Regenerate + build**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(ios): add ripples row component"
```

---

## Task 8: `ProfileCountersRow` component

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileCountersRow.swift`

Three equal-width columns: Days practiced · Pebbles · Karma. Each = big number, SF Symbol icon, small label.

- [ ] **Step 1: Implement**

```swift
import SwiftUI

struct ProfileCountersRow: View {
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            counter(value: daysPracticed, icon: "calendar", label: "Days")
            counter(value: pebbles,       icon: "circle.fill", label: "Pebbles")
            counter(value: karma,         icon: "sparkles", label: "Karma")
        }
    }

    @ViewBuilder
    private func counter(value: Int?, icon: String, label: LocalizedStringResource) -> some View {
        VStack(spacing: 4) {
            Text(value.map { "\($0)" } ?? "—")
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.pebblesForeground)
                .monospacedDigit()
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(Color.pebblesMutedForeground)
            Text(label)
                .font(.caption2)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
        .frame(maxWidth: .infinity)
    }
}

#Preview {
    ProfileCountersRow(daysPracticed: 42, pebbles: 137, karma: 1200)
        .padding()
}
```

**Note on `pebbles` source:** the existing `KarmaSummary` decodes both `total_karma` and `pebbles_count` from `v_karma_summary`. PathStatsService currently only stores `total_karma` (via `karmaResult.totalKarma`). In Task 14, when ProfileView consumes counters, the dispatch agent should also expose `pebbles` on PathStatsService — Step in Task 14 will spell this out.

- [ ] **Step 2: Build + commit**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
git add -A
git commit -m "feat(ios): add profile counters row component"
```

Expected: `BUILD SUCCEEDED`.

---

## Task 9: `ProfileStatsCard` composition

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileStatsCard.swift`

Wraps the section header ("STATS"), the `RipplesRow`, a divider, and the `ProfileCountersRow`. No chevron — stats page is deferred (spec § Issue 2 stats card).

- [ ] **Step 1: Implement**

```swift
import SwiftUI

struct ProfileStatsCard: View {
    let ripple: RippleSummary?
    let assiduity: [Bool]?
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("STATS")
                .font(.caption.weight(.semibold))
                .tracking(0.8)
                .foregroundStyle(Color.pebblesMutedForeground)

            RipplesRow(ripple: ripple, assiduity: assiduity)

            Divider().overlay(Color.pebblesMutedForeground.opacity(0.3))

            ProfileCountersRow(daysPracticed: daysPracticed, pebbles: pebbles, karma: karma)
        }
        .padding(16)
        .background(Color.pebblesListRow)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

#Preview {
    ProfileStatsCard(
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 11, activeToday: true),
        assiduity: (0..<28).map { $0 % 2 == 0 },
        daysPracticed: 42,
        pebbles: 137,
        karma: 1200
    )
    .padding()
}
```

- [ ] **Step 2: Build + commit**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
git add -A
git commit -m "feat(ios): add profile stats card"
```

Expected: `BUILD SUCCEEDED`.

---

## Task 10: `ProfileBanner` component

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift`

Glyph thumbnail (or placeholder) + display name + member-since line.

- [ ] **Step 1: Implement**

```swift
import SwiftUI

struct ProfileBanner: View {
    let displayName: String?
    let memberSince: Date?
    let glyphStrokes: [GlyphStroke]?

    var body: some View {
        VStack(spacing: 12) {
            glyph
                .frame(width: 96, height: 96)

            VStack(spacing: 2) {
                Text(displayName ?? "")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Color.pebblesForeground)
                if let memberSince {
                    Text("Member since \(memberSince.formatted(.dateTime.month(.wide).year()))")
                        .font(.caption)
                        .foregroundStyle(Color.pebblesMutedForeground)
                        .textCase(.uppercase)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var glyph: some View {
        if let strokes = glyphStrokes, !strokes.isEmpty {
            GlyphThumbnail(strokes: strokes, side: 96)
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.08))
                .overlay {
                    Image(systemName: "scribble")
                        .font(.title)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
        }
    }
}

#Preview("With glyph (placeholder strokes)") {
    ProfileBanner(displayName: "Alexis", memberSince: Date(), glyphStrokes: nil)
        .padding()
}
```

- [ ] **Step 2: Build + commit**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
git add -A
git commit -m "feat(ios): add profile banner component"
```

---

## Task 11: `ProfileShortcutTile` + `ProfileShortcutsRow`

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift`

A 3-up row of identical tiles (icon · label) navigating to Collections / Souls / Glyphs lists.

- [ ] **Step 1: Implement the tile**

`ProfileShortcutTile.swift`:

```swift
import SwiftUI

struct ProfileShortcutTile<Destination: View>: View {
    let title: LocalizedStringResource
    let systemImage: String
    @ViewBuilder let destination: () -> Destination

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            VStack(spacing: 8) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(Color.pebblesAccent)
                Text(title)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(Color.pebblesForeground)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Color.pebblesListRow)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}
```

- [ ] **Step 2: Implement the row wrapper**

`ProfileShortcutsRow.swift`:

```swift
import SwiftUI

struct ProfileShortcutsRow: View {
    var body: some View {
        HStack(spacing: 12) {
            ProfileShortcutTile(title: "Collections", systemImage: "square.stack.3d.up") {
                CollectionsListView()
            }
            ProfileShortcutTile(title: "Souls", systemImage: "person.2") {
                SoulsListView()
            }
            ProfileShortcutTile(title: "Glyphs", systemImage: "scribble") {
                GlyphsListView()
            }
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
}
```

- [ ] **Step 3: Build + commit**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
git add -A
git commit -m "feat(ios): add profile shortcuts row"
```

---

## Task 12: `ProfileCollectionCard` + `ProfileCollectionsCard`

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionCard.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift`

Per "Open decisions resolved upfront" item 4: the card shows only the collection's name (no pebble count) until a follow-up confirms the count source.

- [ ] **Step 1: Implement the single card**

`ProfileCollectionCard.swift`:

```swift
import SwiftUI

/// Tile in the horizontal Collections scroller on the Profile screen.
/// Two visual variants — a normal filled tile, and a dashed empty-state
/// tile that prompts the user to create their first collection.
struct ProfileCollectionCard: View {
    enum Variant {
        case filled(name: String)
        case empty
    }

    let variant: Variant
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: variant.iconName)
                    .font(.title3)
                    .foregroundStyle(variant.iconColor)
                Spacer(minLength: 0)
                Text(variant.title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(variant.textColor)
                    .lineLimit(2)
            }
            .padding(12)
            .frame(width: 140, height: 120, alignment: .leading)
            .background(variant.backgroundColor)
            .overlay { variant.borderOverlay }
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

private extension ProfileCollectionCard.Variant {
    var iconName: String {
        switch self {
        case .filled: return "square.stack.3d.up"
        case .empty:  return "plus"
        }
    }
    var iconColor: Color {
        switch self {
        case .filled: return .pebblesAccent
        case .empty:  return .pebblesMutedForeground
        }
    }
    var title: LocalizedStringResource {
        switch self {
        case .filled(let name): return LocalizedStringResource(stringLiteral: name)
        case .empty:            return "New collection"
        }
    }
    var textColor: Color {
        switch self {
        case .filled: return .pebblesForeground
        case .empty:  return .pebblesMutedForeground
        }
    }
    var backgroundColor: Color {
        switch self {
        case .filled: return .pebblesListRow
        case .empty:  return .clear
        }
    }
    @ViewBuilder
    var borderOverlay: some View {
        switch self {
        case .filled:
            EmptyView()
        case .empty:
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1.5, dash: [4])
                )
        }
    }
}
```

- [ ] **Step 2: Implement the wrapper card with inline fetch**

`ProfileCollectionsCard.swift`:

```swift
import SwiftUI
import os

private struct ProfileCollectionRow: Decodable, Identifiable {
    let id: UUID
    let name: String
}

struct ProfileCollectionsCard: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var collections: [ProfileCollectionRow] = []
    @State private var hasLoaded = false
    @State private var isPresentingCreateSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile-collections")

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    if collections.isEmpty && hasLoaded {
                        ProfileCollectionCard(variant: .empty) {
                            isPresentingCreateSheet = true
                        }
                    } else {
                        ForEach(collections) { c in
                            ProfileCollectionCard(variant: .filled(name: c.name)) {
                                // The horizontal cards aren't NavigationLinks; the chevron in
                                // the card header navigates to the full list instead. Tapping
                                // a card from the Profile is a future enhancement.
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.horizontal, -16)
        }
        .padding(16)
        .background(Color.pebblesListRow)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .task { await load() }
        .sheet(isPresented: $isPresentingCreateSheet) {
            CreateCollectionSheet()
        }
    }

    private var header: some View {
        HStack {
            Text("COLLECTIONS")
                .font(.caption.weight(.semibold))
                .tracking(0.8)
                .foregroundStyle(Color.pebblesMutedForeground)
            Spacer()
            NavigationLink {
                CollectionsListView()
            } label: {
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
    }

    private func load() async {
        guard !hasLoaded else { return }
        do {
            let rows: [ProfileCollectionRow] = try await supabase.client
                .from("collections")
                .select("id, name")
                .order("created_at", ascending: false)
                .execute().value
            self.collections = rows
            self.hasLoaded = true
        } catch {
            logger.error("collections fetch failed: \(error.localizedDescription, privacy: .private)")
            self.hasLoaded = true
        }
    }
}
```

Note: the dispatch agent should grep `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift` before writing this fetch. If `CollectionsListView` has already extracted its query into a reusable service, use that service instead of re-issuing the call. If not, the inline fetch above is fine — duplication is acceptable here per spec.

- [ ] **Step 3: Build + commit**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
git add -A
git commit -m "feat(ios): add profile collections card with create-empty state"
```

---

## Task 13: `ProfileLabCard`, `ProfileLogoutPill`, `SettingsStubSheet`

Three small components in one task to avoid trivially small commits.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutPill.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsStubSheet.swift`

- [ ] **Step 1: `ProfileLabCard.swift`**

```swift
import SwiftUI

struct ProfileLabCard: View {
    var body: some View {
        NavigationLink {
            LabView()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "lightbulb.max")
                    .font(.title3)
                    .foregroundStyle(Color.pebblesAccent)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Lab")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.pebblesForeground)
                    Text("News & community")
                        .font(.caption)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
            .padding(16)
            .background(Color.pebblesListRow)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}
```

- [ ] **Step 2: `ProfileLogoutPill.swift`**

```swift
import SwiftUI

struct ProfileLogoutPill: View {
    let action: () -> Void

    var body: some View {
        Button(role: .destructive, action: action) {
            Text("Log out")
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.bordered)
        .clipShape(Capsule())
    }
}
```

- [ ] **Step 3: `SettingsStubSheet.swift`**

```swift
import SwiftUI

/// Placeholder presented from ProfileView's gear button until issue #452 lands.
struct SettingsStubSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Image(systemName: "gear")
                    .font(.system(size: 48))
                    .foregroundStyle(Color.pebblesMutedForeground)
                Text("Settings — coming in #452")
                    .font(.subheadline)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .pebblesScreen()
        }
    }
}
```

- [ ] **Step 4: Build + commit**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
git add -A
git commit -m "feat(ios): add lab card, logout pill, and settings stub sheet"
```

---

## Task 14: Expose `pebbles` count on `PathStatsService` + rewrite `ProfileView`

This is the big composition task. Two related changes that ship together.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

### Sub-task 14a: Expose `pebbles` count

`KarmaSummary` already decodes `total_karma, pebbles_count` from `v_karma_summary`, but PathStatsService only stores `total_karma`. Add `var pebbles: Int?` and store it.

- [ ] **Step 1: Find the `KarmaSummary` definition**

```bash
grep -rn "struct KarmaSummary\|totalKarma\|pebblesCount" /Users/alexis/code/pbbls/apps/ios/Pebbles/
```

Expected output includes a definition like `struct KarmaSummary: Decodable { let totalKarma: Int; let pebblesCount: Int; ... }` somewhere under `Features/Path/`. If `pebblesCount` is not already a property on `KarmaSummary`, add it — the SELECT already requests `pebbles_count`.

- [ ] **Step 2: Edit `PathStatsService.swift`**

Add a property after `var ripple: RippleSummary?`:

```swift
    var pebbles: Int?
```

Replace the karma assignment:

```swift
    do {
        let summary = try await karmaResult
        self.karma   = summary.totalKarma
        self.pebbles = summary.pebblesCount
    } catch {
        logger.error("karma fetch failed: \(error.localizedDescription, privacy: .private)")
    }
```

(`karmaResult` is `async let karmaResult: KarmaSummary = ...` — the `.value` already resolves to a full `KarmaSummary`. The original code only used `.totalKarma` because it threw away the rest. Now we keep both.)

If `KarmaSummary` doesn't have `pebblesCount`, add it to that struct first (one line + a CodingKey entry).

### Sub-task 14b: Rewrite `ProfileView`

- [ ] **Step 3: Replace the entire body of `apps/ios/Pebbles/Features/Profile/ProfileView.swift`**

```swift
import SwiftUI
import os

private struct ProfileRow: Decodable {
    let displayName: String?
    let createdAt: Date
    let glyphId: UUID?

    enum CodingKeys: String, CodingKey {
        case displayName = "display_name"
        case createdAt   = "created_at"
        case glyphId     = "glyph_id"
    }
}

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(PathStatsService.self) private var stats
    @Environment(\.dismiss) private var dismiss

    @State private var profile: ProfileRow?
    @State private var glyphStrokes: [GlyphStroke]?
    @State private var presentedLegalDoc: LegalDoc?
    @State private var isPresentingSettings = false
    @State private var hasLoadedProfile = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile-view")

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                ProfileBanner(
                    displayName: profile?.displayName,
                    memberSince: profile?.createdAt,
                    glyphStrokes: glyphStrokes
                )
                .padding(.top, 8)

                ProfileShortcutsRow()

                ProfileStatsCard(
                    ripple: stats.ripple,
                    assiduity: stats.assiduity,
                    daysPracticed: stats.daysPracticed,
                    pebbles: stats.pebbles,
                    karma: stats.karma
                )

                ProfileCollectionsCard()

                ProfileLabCard()

                LegalLinks(presentedLegalDoc: $presentedLegalDoc)

                ProfileLogoutPill {
                    Task { await supabase.signOut() }
                }
                .padding(.top, 8)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    isPresentingSettings = true
                } label: {
                    Image(systemName: "gear")
                }
                .accessibilityLabel(Text("Settings"))
            }
        }
        .pebblesScreen()
        .task {
            await stats.load()
            await loadProfile()
        }
        .sheet(isPresented: $isPresentingSettings) {
            SettingsStubSheet()
        }
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
    }

    private func loadProfile() async {
        guard !hasLoadedProfile else { return }
        do {
            let row: ProfileRow = try await supabase.client
                .from("profiles")
                .select("display_name, created_at, glyph_id")
                .single().execute().value
            self.profile = row
            self.hasLoadedProfile = true

            if let glyphId = row.glyphId {
                await loadGlyphStrokes(glyphId)
            }
        } catch {
            logger.error("profile fetch failed: \(error.localizedDescription, privacy: .private)")
            self.hasLoadedProfile = true
        }
    }

    private func loadGlyphStrokes(_ glyphId: UUID) async {
        // Reuse whatever glyph fetch the existing GlyphPickerSheet / GlyphsListView
        // uses. If a service exists (e.g. GlyphService), call it. Otherwise inline
        // the same select that those views use. The dispatch agent should grep for
        // `from("glyphs")` and copy that shape.
        //
        // Minimal placeholder shape:
        struct GlyphRow: Decodable { let strokes: [GlyphStroke] }
        do {
            let row: GlyphRow = try await supabase.client
                .from("glyphs")
                .select("strokes")
                .eq("id", value: glyphId.uuidString)
                .single().execute().value
            self.glyphStrokes = row.strokes
        } catch {
            logger.error("glyph fetch failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}

private struct LegalLinks: View {
    @Binding var presentedLegalDoc: LegalDoc?

    var body: some View {
        HStack(spacing: 16) {
            Button {
                presentedLegalDoc = .terms
            } label: {
                Text("Terms")
                    .font(.footnote)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
            Text(verbatim: "·")
                .foregroundStyle(Color.pebblesMutedForeground)
            Button {
                presentedLegalDoc = .privacy
            } label: {
                Text("Privacy")
                    .font(.footnote)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
        .padding(.vertical, 8)
    }
}

#Preview {
    NavigationStack {
        ProfileView()
            .environment(SupabaseService())
    }
}
```

**Important caveats for the dispatch agent:**

1. The `GlyphRow` decodable assumes `glyphs.strokes` is a JSONB column decoding into `[GlyphStroke]`. **Verify** the actual column name and shape against an existing glyph fetch site (search for `from("glyphs")` in iOS). If the project stores glyph strokes differently (e.g. as a separate `glyph_strokes` table), adapt the load function accordingly. Do not invent shape.
2. `LegalDoc` is the existing enum used by the old ProfileView (lines 17, 79, 83 of the pre-rewrite file). Keep using it as-is.
3. Onboarding replay is intentionally dropped (spec § Open questions item 6, resolution (a)).
4. `Settings` accessibility label is a string literal; localization happens automatically.

- [ ] **Step 4: Regenerate, build, manual smoke**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -30
```

Expected: `BUILD SUCCEEDED`. If the glyph fetch shape is wrong, the build will compile but the runtime fetch will log a decoding error — caught and logged, not crashing.

- [ ] **Step 5: Run the full PebblesTests suite**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -30
```

Expected: all tests pass. There should be no regressions in Ripple, decoding, or other unrelated suites.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(ios): rewrite profile view with banner, shortcuts, cards"
```

---

## Task 15: Localize all new strings (en + fr)

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

Xcode auto-extracts `LocalizedStringKey` / `LocalizedStringResource` literals into the catalog on the next build with `SWIFT_EMIT_LOC_STRINGS=YES` (already enabled in `project.yml`). New entries will appear in `New` state with empty French values.

The dispatch agent should:
1. Build the project once (already done in Task 14) so extraction runs.
2. Open `Localizable.xcstrings` in Xcode (or edit the JSON directly), find every entry in `New` or with a missing `fr` value introduced by this PR, and provide both `en` (confirm) and `fr` translations.

### String inventory (touch this list before opening Xcode)

The new user-facing literals introduced by this plan are:

| Key                                       | EN                                | FR                                            |
|-------------------------------------------|-----------------------------------|-----------------------------------------------|
| `Profile`                                 | Profile                           | Profil                                        |
| `Settings`                                | Settings                          | Paramètres                                    |
| `Done`                                    | Done                              | OK                                            |
| `Settings — coming in #452`               | Settings — coming in #452         | Paramètres — à venir dans #452                |
| `STATS`                                   | STATS                             | STATS                                         |
| `COLLECTIONS`                             | COLLECTIONS                       | COLLECTIONS                                   |
| `Ripples Level %lld`                      | Ripples Level %lld                | Niveau Ripples %lld                           |
| `%lld more pebbles to level %lld`         | %lld more pebbles to level %lld   | %lld pebbles avant le niveau %lld             |
| `Max level reached`                       | Max level reached                 | Niveau max atteint                            |
| `Loading…`                                | Loading…                          | Chargement…                                   |
| `Days`                                    | Days                              | Jours                                         |
| `Pebbles`                                 | Pebbles                           | Pebbles                                       |
| `Karma`                                   | Karma                             | Karma                                         |
| `Lab`                                     | Lab                               | Lab                                           |
| `News & community`                        | News & community                  | Actus & communauté                            |
| `Collections`                             | Collections                       | Collections                                   |
| `Souls`                                   | Souls                             | Âmes                                          |
| `Glyphs`                                  | Glyphs                            | Glyphes                                       |
| `New collection`                          | New collection                    | Nouvelle collection                           |
| `Member since %@`                         | Member since %@                   | Membre depuis %@                              |
| `Terms`                                   | Terms                             | Conditions                                    |
| `Privacy`                                 | Privacy                           | Confidentialité                               |
| `Log out`                                 | Log out                           | Se déconnecter                                |
| `Assiduity grid, last 28 days`            | Assiduity grid, last 28 days      | Grille d'assiduité, 28 derniers jours         |

Some keys (`Profile`, `Terms`, `Privacy`, `Log out`, `Collections`, `Souls`, `Glyphs`, `Lab`) already exist in the catalog from the old ProfileView — they should already have french values. Confirm they remain present (the catalog won't garbage-collect them).

- [ ] **Step 1: Build to trigger extraction**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -5
```

- [ ] **Step 2: Apply translations**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings`. For each entry in the table above:
- If the `en` localization is missing, add it with `state: "translated"`.
- Add the `fr` localization with `state: "translated"`.
- If the entry is in extraction state `new`, change it to `manual` or leave as auto-extracted (omitting the field).

The schema is JSON and the file is hand-editable. Reference shape (from existing entries):

```json
"Member since %@": {
  "extractionState": "manual",
  "localizations": {
    "en": { "stringUnit": { "state": "translated", "value": "Member since %@" } },
    "fr": { "stringUnit": { "state": "translated", "value": "Membre depuis %@" } }
  }
}
```

- [ ] **Step 3: Open the catalog in Xcode (or grep) to confirm no `New` / `Stale` entries**

```bash
grep -c '"state" : "new"' apps/ios/Pebbles/Resources/Localizable.xcstrings || true
grep -c '"state" : "stale"' apps/ios/Pebbles/Resources/Localizable.xcstrings || true
```

Expected: both return `0` (the `|| true` keeps the command exit code clean if grep finds zero matches). If either is non-zero, open Xcode and resolve.

- [ ] **Step 4: Build once more to confirm no regressions**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -5
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): localize profile redesign strings (en + fr)"
```

---

## Task 16: Manual verification

No automated UI tests exist (per `apps/ios/CLAUDE.md` — UI tests are out of scope). Manual smoke is the verification.

- [ ] **Step 1: Run on simulator**

```bash
cd /Users/alexis/code/pbbls/apps/ios
open Pebbles.xcodeproj
```

In Xcode: select `Pebbles` scheme, `iPhone 16` simulator, ⌘R.

- [ ] **Step 2: Smoke checklist**

Verify in the running app:

1. **Path screen unchanged** — bottom bar still renders the Ripple badge correctly (no visual regression from the file move).
2. **Profile screen — engaged user (sign in to a real test account):**
   - Banner shows glyph + display name + "Member since <Month YYYY>".
   - 3 shortcut tiles navigate to Collections, Souls, Glyphs.
   - Stats card shows current Ripples level + progress copy + 28-day assiduity grid + 3 counters with non-nil values.
   - Collections card shows horizontal scroll of the user's collections.
   - Lab card navigates to LabView.
   - Logout pill signs out.
   - Gear button opens the stub Settings sheet.
3. **Profile screen — new user (sign in to a fresh test account or clear data):**
   - Banner shows placeholder glyph.
   - Stats card shows level 0 + "1 more pebble to level 1" + all-grey assiduity grid + zeros in counters (or em-dash where data is genuinely null).
   - Collections card shows the dashed "New collection" tile; tapping opens `CreateCollectionSheet`.
4. **Timezone correctness:**
   - In simulator: Features → ⌘ Settings → General → Date & Time → set to `Pacific/Auckland`.
   - Force-quit and relaunch the app.
   - Pebbles created today (NZ time) should make the bottom-right cell of the assiduity grid active.
5. **French locale:**
   - Simulator → Settings app → General → Language & Region → French.
   - Relaunch the app. All Profile screen labels should be in French. No raw English literals.

- [ ] **Step 3: Lint workspace (per CLAUDE.md task triage — this is a Large task)**

```bash
cd /Users/alexis/code/pbbls
npm run lint
```

If a separate iOS Swift lint isn't wired into the JS workspace, this is a no-op for the iOS sources but should still pass at root.

- [ ] **Step 4: Final test run**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild test -project Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -10
```

Expected: all suites pass.

---

## Task 17: Push, open PR

- [ ] **Step 1: Push branch**

```bash
cd /Users/alexis/code/pbbls
git push -u origin feat/451-profile-screen-redesign
```

- [ ] **Step 2: Create PR (inherit labels + milestone from #451)**

Per project memory: PR title in conventional commits, body starts with `Resolves #N`, labels = issue labels (with `bug → fix` swap), milestone = issue milestone. Issue #451 labels: `feat`, `ui`, `engagement`, `ios`. Milestone: `M22 · Bounce karma & gamification`.

```bash
gh pr create \
  --title "feat(ios): redesign profile screen with banner, shortcuts, cards" \
  --label "feat,ui,engagement,ios" \
  --milestone "M22 · Bounce karma & gamification" \
  --body "$(cat <<'EOF'
Resolves #451 · Closes #445 (superseded)

## Summary
- Rewrites `ProfileView` to match the engaged + new-user mockups (banner, 3 shortcuts, stats card, collections card, lab card, logout pill).
- Relocates Ripples primitives (`RippleBadge`, `RippleStrokes`, `RippleStrokeColor`, `RippleSummary`) from `Features/Path/` to `Features/Shared/Ripples/` so Profile can consume them without coupling to Path.
- Extends `PathStatsService` to load `daysPracticed` + `assiduity` via the `get_profile_engagement` RPC (PR #453). Drops `bounce`.
- Deletes the legacy Bounce/Karma sheets, `ProfileStatRow`, `ProfileNavRow`, `BounceSummary`.
- Stubs the gear-button Settings sheet (real settings ship in #452).
- New strings localised in en + fr.

Spec: `docs/superpowers/specs/2026-05-16-ios-profile-redesign-and-settings-design.md` § Issue 2

## Notable decisions
- Engagement copy uses *pebbles*-to-next-level rather than *days*-to-next-level; the SQL exposes a 28-day pebble count, not a calendar projection. Thresholds mirrored in `RippleSummary.levelEntryThresholds` with a pointer back to the SQL.
- Collections card shows name only (no per-collection pebble count) until the count source is confirmed in a follow-up.
- "Replay onboarding" affordance dropped from Profile (matches mockup; debug builds can still trigger).

## Test plan
- [x] `xcodebuild test` — all PebblesTests suites pass.
- [ ] Engaged user: stats card populated, collections scroll renders.
- [ ] New user: dashed "New collection" empty state, level 0 progress copy.
- [ ] Timezone manual: device tz `Pacific/Auckland`, today's pebble lights up bottom-right of grid.
- [ ] French locale: all labels translated, no raw English.
- [ ] Path bottom-bar Ripple badge unchanged.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL returned. Confirm labels + milestone applied on the PR page.

---

## Self-review summary

- **Spec coverage:** every bullet from `docs/superpowers/specs/2026-05-16-ios-profile-redesign-and-settings-design.md` § Issue 2 maps to a task: Ripples relocation (Task 2), ProfileView rewrite (Tasks 6–14), engagement RPC (Task 5), deletions (Task 4), settings stub (Task 13), localization (Task 15), manual TZ verification (Task 16). The collections per-card pebble count is acknowledged as a follow-up (Open decision 4).
- **Type consistency:** `RippleSummary` methods/properties used in Tasks 7 (`pebblesToNextLevel`, `nextLevel`) match what's defined in Task 3. `ProfileEngagement` (Task 5) is consumed exactly as defined. `PathStatsService.pebbles` introduced in Task 14a is consumed in Task 14b.
- **No placeholders:** every code block contains real code; every command is runnable. The two intentional uncertainties (collections fetch reuse, glyph stroke fetch shape) are flagged inline as grep-then-decide rather than left as TODO.
- **Bite-size:** each step is 2–5 minutes. Each task ends with a commit. The most ambitious task (14) is split into 14a/14b sub-tasks.
