# iOS Collections List + Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stub `CollectionsListView` with a list + detail + edit + swipe-delete experience for collections on iOS, resolving issue #216.

**Architecture:** SwiftUI, iOS 17+. `@Environment(SupabaseService.self)` for data access. Direct `.from("collections")` / `.from("pebbles")` calls — RLS scopes to the owner, no RPC needed for single-table reads/writes (per `AGENTS.md`). Mirror the souls CRUD pattern from #215: list view holds swipe-delete + confirmation, detail view holds a month-grouped timeline + Edit toolbar, edit sheet renames and re-modes. One new testable helper (`groupPebblesByMonth`) and one new model (`Collection` with a custom decoder that unwraps PostgREST's nested count aggregate).

**Tech Stack:** Swift 5.9, SwiftUI, Supabase Swift SDK, Swift Testing (`@Suite` / `@Test` / `#expect`), xcodegen, `os.Logger`.

**Reference:** spec at `docs/superpowers/specs/2026-04-16-ios-collections-list-detail-design.md`.

---

## Conventions & Shared Context

Read these once before starting; every task depends on them.

**Branch:** `feat/216-ios-collections-list-detail` (already created; the spec commit lives there).

**Generate Xcode project after adding files:**

```bash
cd apps/ios && npm run generate
```

`project.yml` globs the `Pebbles` folder, so new `.swift` files are picked up on re-generate. Run this once per task that adds a file.

**Build verification command (run from repo root):**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`. If this ever fails, stop and fix before continuing.

**Test verification command:**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet | tail -30
```

Expected: `** TEST SUCCEEDED **` and the count of executed tests includes the new suites.

**Logger convention:** every `try/catch` on an async path logs `logger.error("<operation> failed: \(error.localizedDescription, privacy: .private)")` and surfaces a user-facing string. Never an empty catch. Categories: `"profile.collections"` for the list, `"profile.collection.detail"` for the detail view.

**Existing models you'll reuse (do not modify):**
- `apps/ios/Pebbles/Features/Path/Models/Pebble.swift` — `struct Pebble: Identifiable, Decodable, Hashable { id, name, happenedAt }`. Extra keys in the PostgREST response are ignored by the default decoder.
- `apps/ios/Pebbles/Services/UUID+Identifiable.swift` — makes `UUID` `Identifiable`, which is why `.sheet(item: $someUUID)` compiles.
- `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` — the pebble edit sheet, reused from the detail view on pebble tap.

**Retiring the stub model:** `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift` is not referenced outside `CollectionsListView.swift` (grep confirmed). It will be deleted in Task 8 after the new `Collection` model fully replaces it.

**RLS context:** `collections` has RLS policies `collections_select/insert/update/delete` keyed on `user_id = auth.uid()`. Every query and mutation scopes to the current user automatically. No explicit ownership checks needed from the client. `collection_pebbles.collection_id` has `on delete cascade`, so deleting a collection drops its junction rows automatically.

**Commit convention:** one commit per task. Format: `type(scope): description (#216)`. Types: `feat`, `test`, `docs`, `quality`. Scope: `ios` for app code, `arkaik` for the map.

---

## File Structure

| Path | Role |
|------|------|
| `apps/ios/Pebbles/Features/Profile/Models/Collection.swift` | NEW — `Collection` struct + `CollectionMode` enum + custom decoder for the nested count aggregate. |
| `apps/ios/Pebbles/Features/Profile/Components/CollectionModeBadge.swift` | NEW — tiny capsule showing emoji + label per mode; returns `EmptyView` for nil. |
| `apps/ios/Pebbles/Features/Profile/Views/GroupPebblesByMonth.swift` | NEW — internal pure helper grouping `[Pebble]` by calendar month; testable. |
| `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift` | NEW — pushed view; subheader with mode+count; month-grouped pebble sections; tap → `EditPebbleSheet`; Edit toolbar → `EditCollectionSheet`. |
| `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift` | NEW — form with Name field + segmented Mode picker; UPDATE on `collections`. Includes a private `CollectionUpdatePayload: Encodable`. |
| `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift` | REPLACED — row with name + mode badge + count; nav link to detail; swipe-to-delete with confirmation dialog + error alert; pull-to-refresh. |
| `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift` | DELETED — superseded by the new `Collection` model. |
| `apps/ios/PebblesTests/CollectionDecodingTests.swift` | NEW — Swift Testing suite for the custom decoder. |
| `apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift` | NEW — Swift Testing suite for the update payload. |
| `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift` | NEW — Swift Testing suite for the month grouping helper. |
| `docs/arkaik/bundle.json` | MODIFIED — flip `V-collection-detail` to `development`, add `V-collection-edit`, add composition edges. |

---

## Task 1: `Collection` model + `CollectionMode` enum (TDD)

Build the model first with the custom decoder that unwraps PostgREST's nested count aggregate. Tests before code.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Models/Collection.swift`
- Test: `apps/ios/PebblesTests/CollectionDecodingTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/CollectionDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("Collection decoding")
struct CollectionDecodingTests {

    private func decoder() -> JSONDecoder { JSONDecoder() }

    @Test("decodes list-query shape with populated count aggregate")
    func listQueryPopulatedCount() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Summer 2026",
          "mode": "pack",
          "pebble_count": [{ "count": 5 }]
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.id.uuidString.lowercased() == "11111111-1111-1111-1111-111111111111")
        #expect(collection.name == "Summer 2026")
        #expect(collection.mode == .pack)
        #expect(collection.pebbleCount == 5)
    }

    @Test("decodes list-query shape with empty count array → 0")
    func listQueryEmptyCountArray() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Empty one",
          "mode": "stack",
          "pebble_count": []
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.pebbleCount == 0)
    }

    @Test("decodes detail-shape without pebble_count key → 0")
    func missingPebbleCountKey() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "No count",
          "mode": "track"
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.pebbleCount == 0)
        #expect(collection.mode == .track)
    }

    @Test("decodes rows with null mode as nil")
    func nullMode() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Modeless",
          "mode": null,
          "pebble_count": [{ "count": 2 }]
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.mode == nil)
        #expect(collection.pebbleCount == 2)
    }

    @Test("decodes rows with missing mode key as nil")
    func missingModeKey() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Modeless",
          "pebble_count": [{ "count": 0 }]
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.mode == nil)
    }

    @Test("CollectionMode decodes stack / pack / track")
    func allModes() throws {
        for raw in ["stack", "pack", "track"] {
            let json = Data("""
            { "id": "11111111-1111-1111-1111-111111111111",
              "name": "x", "mode": "\(raw)", "pebble_count": [] }
            """.utf8)
            let collection = try decoder().decode(Collection.self, from: json)
            #expect(collection.mode?.rawValue == raw)
        }
    }
}
```

- [ ] **Step 2: Run the tests — confirm they fail to compile**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -20
```

Expected: compilation error `cannot find type 'Collection' in scope` (or similar). This proves the test is wired; it fails *because* the model doesn't exist yet.

- [ ] **Step 3: Write the model**

Create `apps/ios/Pebbles/Features/Profile/Models/Collection.swift`:

```swift
import Foundation

/// Mode variants for a collection. Mirrors the `mode` check constraint on
/// `public.collections`: `('stack', 'pack', 'track')` or `null`.
enum CollectionMode: String, Decodable, CaseIterable, Hashable {
    case stack
    case pack
    case track
}

/// View-model for a collection row. Not the storage shape — `pebbleCount` comes
/// from a PostgREST nested aggregate (`pebble_count:collection_pebbles(count)`)
/// that returns `[{ "count": N }]`. The custom decoder below unwraps that into
/// a plain `Int`, and falls back to `0` when the aggregate is absent (e.g. a
/// single-row fetch for the detail header).
struct Collection: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let mode: CollectionMode?
    let pebbleCount: Int

    enum CodingKeys: String, CodingKey {
        case id, name, mode
        case pebbleCount = "pebble_count"
    }

    private struct CountWrapper: Decodable { let count: Int }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.name = try container.decode(String.self, forKey: .name)
        self.mode = try container.decodeIfPresent(CollectionMode.self, forKey: .mode)

        if let wrappers = try? container.decode([CountWrapper].self, forKey: .pebbleCount) {
            self.pebbleCount = wrappers.first?.count ?? 0
        } else {
            self.pebbleCount = 0
        }
    }
}
```

- [ ] **Step 4: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate
```

Expected: `xcodegen` prints `Generated project successfully`.

- [ ] **Step 5: Run the tests — confirm they pass**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **` and the `Collection decoding` suite reports 6 passing tests.

- [ ] **Step 6: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Profile/Models/Collection.swift \
        apps/ios/PebblesTests/CollectionDecodingTests.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): add Collection model with nested-count decoder (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `CollectionModeBadge` component

Small UI primitive used by both the list row and the detail subheader. No logic — no tests.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/CollectionModeBadge.swift`

- [ ] **Step 1: Create the component**

```swift
import SwiftUI

/// Capsule badge showing a collection's mode with emoji + label.
/// Renders nothing when mode is nil.
struct CollectionModeBadge: View {
    let mode: CollectionMode?

    var body: some View {
        if let mode {
            let (emoji, label) = Self.meta(for: mode)
            Label {
                Text(label)
            } icon: {
                Text(emoji)
            }
            .labelStyle(.titleAndIcon)
            .font(.caption)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .overlay(
                Capsule().stroke(.secondary.opacity(0.3), lineWidth: 1)
            )
            .accessibilityLabel("Mode: \(label)")
        } else {
            EmptyView()
        }
    }

    private static func meta(for mode: CollectionMode) -> (emoji: String, label: String) {
        switch mode {
        case .stack: return ("🎯", "Stack")
        case .pack:  return ("📦", "Pack")
        case .track: return ("🔄", "Track")
        }
    }
}

#Preview {
    VStack(spacing: 12) {
        CollectionModeBadge(mode: .stack)
        CollectionModeBadge(mode: .pack)
        CollectionModeBadge(mode: .track)
        CollectionModeBadge(mode: nil) // renders nothing
    }
    .padding()
}
```

- [ ] **Step 2: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate
```

- [ ] **Step 3: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Profile/Components/CollectionModeBadge.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): add CollectionModeBadge component (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `groupPebblesByMonth` helper (TDD)

Pure function grouping pebbles by calendar month. `internal` visibility so `@testable import` can reach it.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Views/GroupPebblesByMonth.swift`
- Test: `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("groupPebblesByMonth")
struct GroupPebblesByMonthTests {

    /// Fixed Gregorian calendar in UTC so tests are deterministic regardless of
    /// the machine running them.
    private var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }

    private func date(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: iso)!
    }

    private func pebble(_ happened: String) -> Pebble {
        // Decode through JSON to construct a Pebble since all properties are `let`.
        let json = Data("""
        { "id": "\(UUID().uuidString)", "name": "p", "happened_at": "\(happened)" }
        """.utf8)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            return formatter.date(from: s)!
        }
        return try! decoder.decode(Pebble.self, from: json)
    }

    @Test("empty input → empty output")
    func emptyInput() {
        let result = groupPebblesByMonth([], calendar: calendar)
        #expect(result.isEmpty)
    }

    @Test("pebbles in the same month group together")
    func sameMonth() {
        let a = pebble("2026-04-02T10:00:00Z")
        let b = pebble("2026-04-28T22:00:00Z")
        let result = groupPebblesByMonth([a, b], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }

    @Test("different months produce separate groups ordered desc")
    func descendingOrder() {
        let april = pebble("2026-04-02T10:00:00Z")
        let march = pebble("2026-03-15T10:00:00Z")
        let may   = pebble("2026-05-01T10:00:00Z")
        let result = groupPebblesByMonth([may, april, march], calendar: calendar)
        #expect(result.count == 3)
        // First group is May, then April, then March
        let expectedOrder: [(year: Int, month: Int)] = [
            (2026, 5), (2026, 4), (2026, 3)
        ]
        for (i, expected) in expectedOrder.enumerated() {
            let comps = calendar.dateComponents([.year, .month], from: result[i].key)
            #expect(comps.year == expected.year)
            #expect(comps.month == expected.month)
        }
    }

    @Test("input order within a group is preserved")
    func preservesInputOrder() {
        let first  = pebble("2026-04-28T10:00:00Z")
        let second = pebble("2026-04-10T10:00:00Z")
        let result = groupPebblesByMonth([first, second], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value[0].happenedAt == first.happenedAt)
        #expect(result[0].value[1].happenedAt == second.happenedAt)
    }

    @Test("month boundary respects the injected calendar")
    func monthBoundary() {
        // In UTC, 2026-04-01T00:00:00Z is April. In UTC-5 it would be March.
        let utcApril = pebble("2026-04-01T00:00:00Z")
        let lateMarch = pebble("2026-03-31T22:00:00Z")
        let result = groupPebblesByMonth([utcApril, lateMarch], calendar: calendar)
        #expect(result.count == 2)
    }
}
```

- [ ] **Step 2: Run the tests — confirm they fail to compile**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -10
```

Expected: compilation error `cannot find 'groupPebblesByMonth' in scope`.

- [ ] **Step 3: Write the helper**

Create `apps/ios/Pebbles/Features/Profile/Views/GroupPebblesByMonth.swift`:

```swift
import Foundation

/// Groups pebbles by the first day of their calendar month, returning
/// `(monthStart, pebbles)` pairs ordered descending by month.
///
/// - Keys are the first instant of each month in the provided calendar.
/// - Within a group, input order is preserved — callers typically pass
///   pebbles already sorted descending by `happenedAt`.
/// - `calendar` is injectable to keep tests deterministic; production
///   callers should pass `Calendar.current`.
func groupPebblesByMonth(
    _ pebbles: [Pebble],
    calendar: Calendar
) -> [(key: Date, value: [Pebble])] {
    let buckets = Dictionary(grouping: pebbles) { pebble -> Date in
        let comps = calendar.dateComponents([.year, .month], from: pebble.happenedAt)
        return calendar.date(from: comps) ?? pebble.happenedAt
    }
    return buckets
        .map { (key: $0.key, value: $0.value) }
        .sorted { $0.key > $1.key }
}
```

- [ ] **Step 4: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate
```

- [ ] **Step 5: Run the tests — confirm they pass**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -10
```

Expected: `** TEST SUCCEEDED **`, 5 passing tests in the `groupPebblesByMonth` suite.

- [ ] **Step 6: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Profile/Views/GroupPebblesByMonth.swift \
        apps/ios/PebblesTests/GroupPebblesByMonthTests.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): add groupPebblesByMonth helper for timelines (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `EditCollectionSheet` + `CollectionUpdatePayload` encoding tests (TDD for payload)

Build the edit sheet now so the detail view can reference it in Task 5. Payload encoding is tested; the view itself is manually verified.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`
- Test: `apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift`

- [ ] **Step 1: Write the failing encoding tests**

Create `apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CollectionUpdatePayload encoding")
struct CollectionUpdatePayloadEncodingTests {

    private func encode(_ payload: CollectionUpdatePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) as! [String: Any]
    }

    @Test("encodes name and mode rawValue")
    func basicEncoding() throws {
        let payload = CollectionUpdatePayload(name: "Summer", mode: "pack")
        let json = try encode(payload)
        #expect(json["name"] as? String == "Summer")
        #expect(json["mode"] as? String == "pack")
    }

    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionUpdatePayload(name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(json["mode"] is NSNull)
    }

    @Test("each mode rawValue round-trips")
    func allModes() throws {
        for raw in ["stack", "pack", "track"] {
            let payload = CollectionUpdatePayload(name: "x", mode: raw)
            let json = try encode(payload)
            #expect(json["mode"] as? String == raw)
        }
    }
}
```

- [ ] **Step 2: Run the tests — confirm they fail to compile**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -10
```

Expected: `cannot find 'CollectionUpdatePayload' in scope`.

- [ ] **Step 3: Write the sheet (payload is embedded)**

Create `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`:

```swift
import SwiftUI
import os

/// Sheet for editing an existing collection: name + mode.
/// UPDATE goes directly to `public.collections` — RLS scopes to the owner.
struct EditCollectionSheet: View {
    let collection: Collection
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var mode: CollectionMode?
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    init(collection: Collection, onSaved: @escaping () -> Void) {
        self.collection = collection
        self.onSaved = onSaved
        self._name = State(initialValue: collection.name)
        self._mode = State(initialValue: collection.mode)
    }

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        guard !trimmedName.isEmpty else { return false }
        return trimmedName != collection.name || mode != collection.mode
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("Name", text: $name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(false)
                }
                Section("Mode") {
                    Picker("Mode", selection: $mode) {
                        Text("None").tag(CollectionMode?.none)
                        Text("Stack").tag(CollectionMode?.some(.stack))
                        Text("Pack").tag(CollectionMode?.some(.pack))
                        Text("Track").tag(CollectionMode?.some(.track))
                    }
                    .pickerStyle(.segmented)
                }
                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Edit collection")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Save") {
                            Task { await save() }
                        }
                        .disabled(!canSave)
                    }
                }
            }
        }
    }

    private func save() async {
        guard canSave else { return }
        isSaving = true
        saveError = nil
        do {
            let payload = CollectionUpdatePayload(name: trimmedName, mode: mode?.rawValue)
            try await supabase.client
                .from("collections")
                .update(payload)
                .eq("id", value: collection.id)
                .execute()
            onSaved()
            dismiss()
        } catch {
            logger.error("update collection failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
}

/// Wire shape for `PATCH /collections/:id`. Snake-case keys match the DB columns.
/// `mode` is explicitly encoded so that `nil` clears the column server-side.
struct CollectionUpdatePayload: Encodable {
    let name: String
    let mode: String?

    enum CodingKeys: String, CodingKey {
        case name
        case mode
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(mode, forKey: .mode) // encodes nil as JSON null
    }
}

#Preview {
    EditCollectionSheet(
        collection: Collection(
            from: try! JSONDecoder().decode(
                Collection.self,
                from: Data("""
                { "id": "11111111-1111-1111-1111-111111111111",
                  "name": "Preview", "mode": "pack",
                  "pebble_count": [{ "count": 3 }] }
                """.utf8)
            ) as! Decoder
        ),
        onSaved: {}
    )
    .environment(SupabaseService())
}
```

> Note: the `#Preview` block's construction of `Collection` is awkward because the struct has no memberwise initializer (only the custom decoder). If the preview fails to compile, simplify to a file-scope `static let preview` on `Collection` — but only if needed. Manual QA, not preview correctness, is the acceptance bar here.

- [ ] **Step 4: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate
```

- [ ] **Step 5: Run the encoding tests — confirm they pass**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -10
```

Expected: `** TEST SUCCEEDED **`, 3 passing tests in `CollectionUpdatePayload encoding`.

- [ ] **Step 6: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift \
        apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): add EditCollectionSheet and update payload (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `CollectionDetailView`

Pushed view with a subheader, a month-grouped pebble timeline, and an Edit toolbar button. Mirrors `SoulDetailView` conceptually.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`

- [ ] **Step 1: Write the view**

```swift
import SwiftUI
import os

/// Pushed detail view for a single collection.
///
/// - Subheader shows the mode badge + pebble count.
/// - Pebbles are grouped by calendar month (see `groupPebblesByMonth`) with
///   section headers like "April 2026".
/// - Tapping a pebble opens the existing `EditPebbleSheet`.
/// - The Edit toolbar opens `EditCollectionSheet`. After save, the local
///   `collection` is reloaded so the header (title + mode badge + count)
///   stays in sync without popping the stack.
struct CollectionDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var collection: Collection
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingEdit = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collection.detail")

    private static let monthFormatter: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("MMMM yyyy")
        return f
    }()

    init(collection: Collection, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._collection = State(initialValue: collection)
    }

    var body: some View {
        content
            .navigationTitle(collection.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Edit") {
                        isPresentingEdit = true
                    }
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingEdit) {
                EditCollectionSheet(collection: collection, onSaved: {
                    // Refresh this view's header/subheader and the parent list
                    // independently — neither blocks the other.
                    Task { await reloadCollection() }
                    onChanged()
                })
            }
            .sheet(item: $selectedPebbleId) { id in
                EditPebbleSheet(pebbleId: id, onSaved: {
                    Task { await load() }
                })
            }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load pebbles",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if pebbles.isEmpty {
            ContentUnavailableView(
                "No pebbles yet",
                systemImage: "circle.grid.2x1",
                description: Text("Pebbles added to this collection will appear here.")
            )
        } else {
            List {
                Section {
                    HStack {
                        CollectionModeBadge(mode: collection.mode)
                        Spacer()
                        Text(pebbleCountLabel)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                ForEach(groupedPebbles, id: \.key) { group in
                    Section(header: Text(Self.monthFormatter.string(from: group.key))) {
                        ForEach(group.value) { pebble in
                            Button {
                                selectedPebbleId = pebble.id
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(pebble.name).font(.body)
                                    Text(pebble.happenedAt, style: .date)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private var groupedPebbles: [(key: Date, value: [Pebble])] {
        groupPebblesByMonth(pebbles, calendar: .current)
    }

    private var pebbleCountLabel: String {
        switch pebbles.count {
        case 0: return "No pebbles"
        case 1: return "1 pebble"
        default: return "\(pebbles.count) pebbles"
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            // Inner join on `collection_pebbles` filters parent rows; the extra
            // key is ignored by Pebble's default decoder.
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, collection_pebbles!inner(collection_id)")
                .eq("collection_pebbles.collection_id", value: collection.id)
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
        } catch {
            logger.error("collection pebbles fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func reloadCollection() async {
        do {
            let refreshed: Collection = try await supabase.client
                .from("collections")
                .select("id, name, mode, pebble_count:collection_pebbles(count)")
                .eq("id", value: collection.id)
                .single()
                .execute()
                .value
            self.collection = refreshed
        } catch {
            logger.error("collection reload failed: \(error.localizedDescription, privacy: .private)")
            // Leave stale state; next navigation refreshes.
        }
    }
}

#Preview {
    NavigationStack {
        ProgressView() // preview uses a minimal wrapper — see EditCollectionSheet for a fuller example
    }
}
```

- [ ] **Step 2: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate
```

- [ ] **Step 3: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`. If the build fails on `EditPebbleSheet(pebbleId:onSaved:)`, verify the existing signature in `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` and match it exactly — the detail view relies on the current signature.

- [ ] **Step 4: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): add CollectionDetailView with month-grouped timeline (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Rebuild `CollectionsListView` (list row, navigation, swipe-delete, pull-to-refresh)

Replaces the entire stub with the full experience wired to the new detail view.

**Files:**
- Modify (full rewrite): `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`

- [ ] **Step 1: Replace the file contents**

```swift
import SwiftUI
import os

struct CollectionsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [Collection] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pendingDeletion: Collection?
    @State private var deleteError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    var body: some View {
        content
            .navigationTitle("Collections")
            .navigationBarTitleDisplayMode(.inline)
            .task { await load() }
            .refreshable { await load() }
            .confirmationDialog(
                pendingDeletion.map { "Delete \($0.name)?" } ?? "",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { collection in
                Button("Delete", role: .destructive) {
                    Task { await delete(collection) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("Linked pebbles stay; only the collection and its links are removed.")
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
            ContentUnavailableView(
                "Couldn't load collections",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No collections yet",
                systemImage: "square.stack.3d.up",
                description: Text("Your collections will appear here.")
            )
        } else {
            List {
                ForEach(items) { collection in
                    NavigationLink {
                        CollectionDetailView(collection: collection, onChanged: {
                            Task { await load() }
                        })
                    } label: {
                        CollectionRow(collection: collection)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            pendingDeletion = collection
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let result: [Collection] = try await supabase.client
                .from("collections")
                .select("id, name, mode, pebble_count:collection_pebbles(count)")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("collections fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func delete(_ collection: Collection) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .from("collections")
                .delete()
                .eq("id", value: collection.id)
                .execute()
            await load()
        } catch {
            logger.error("delete collection failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

/// Row for the collections list. Two lines: name on top, mode badge + count below.
private struct CollectionRow: View {
    let collection: Collection

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(collection.name)
                .font(.body)
            HStack(spacing: 6) {
                CollectionModeBadge(mode: collection.mode)
                if collection.mode != nil {
                    Text("·")
                        .foregroundStyle(.secondary)
                }
                Text(pebbleCountLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var pebbleCountLabel: String {
        switch collection.pebbleCount {
        case 0: return "No pebbles"
        case 1: return "1 pebble"
        default: return "\(collection.pebbleCount) pebbles"
        }
    }
}

#Preview {
    NavigationStack {
        CollectionsListView()
            .environment(SupabaseService())
    }
}
```

- [ ] **Step 2: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate
```

- [ ] **Step 3: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): full CollectionsListView with detail nav and swipe delete (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Retire the old `PebbleCollection` stub model

Now that nothing references it, delete it.

**Files:**
- Delete: `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift`

- [ ] **Step 1: Confirm no references remain**

```bash
cd /Users/alexis/code/pbbls
grep -rn "PebbleCollection" apps/ios/Pebbles apps/ios/PebblesTests
```

Expected: no output. If there are references, stop and investigate.

- [ ] **Step 2: Delete the file**

```bash
cd /Users/alexis/code/pbbls
git rm apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift
```

- [ ] **Step 3: Regenerate Xcode project**

```bash
cd apps/ios && npm run generate
```

- [ ] **Step 4: Build and run the full test suite**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **` and `** TEST SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
quality(ios): retire unused PebbleCollection stub model (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Arkaik map update

Update `docs/arkaik/bundle.json` to reflect the new iOS surfaces.

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Read the arkaik skill reference**

```bash
cat .claude/skills/arkaik/SKILL.md 2>/dev/null | head -60
```

If the skill has a validation script, locate it (typically `.claude/skills/arkaik/validate.{ts,js,py}`). You'll run it in Step 5.

- [ ] **Step 2: Flip `V-collection-detail` from `idea` to `development`**

Find the node around line 175–182:

```json
{
  "id": "V-collection-detail",
  "project_id": "pebbles",
  "species": "view",
  "title": "Collection Detail",
  "description": "View pebbles within a specific collection.",
  "status": "idea",
  "platforms": ["web", "ios", "android"]
}
```

Change `"status": "idea"` to `"status": "development"` and tighten the description:

```json
{
  "id": "V-collection-detail",
  "project_id": "pebbles",
  "species": "view",
  "title": "Collection Detail",
  "description": "View a collection and its pebbles grouped by month; rename / re-mode / delete entry points.",
  "status": "development",
  "platforms": ["web", "ios", "android"]
}
```

- [ ] **Step 3: Add `V-collection-edit` node**

Insert a new view node alongside the other collection nodes (e.g. immediately after `V-collection-create`). Use the existing `V-soul-edit` node as a template:

```json
{
  "id": "V-collection-edit",
  "project_id": "pebbles",
  "species": "view",
  "title": "Edit Collection",
  "description": "Rename or re-mode an existing collection. Web renders inline on the detail page; iOS presents as a sheet.",
  "status": "development",
  "platforms": ["web", "ios", "android"]
}
```

- [ ] **Step 4: Wire composition edges for the new surface**

Inside the `F-manage-collections` feature node (around line 428), add an "Edit collection" branch to the decision so the map reflects the new action:

Locate the existing block:

```json
{
  "label": "Rise check-in",
  "entries": [{ "type": "view", "view_id": "V-collection-rise" }]
}
```

Insert a new branch entry above it:

```json
{
  "label": "Edit collection",
  "entries": [{ "type": "view", "view_id": "V-collection-edit" }]
},
```

Then at the edges section (around line 1119), add a composition edge:

```json
{ "id": "e-F-manage-collections-V-collection-edit", "project_id": "pebbles", "source_id": "F-manage-collections", "target_id": "V-collection-edit", "edge_type": "composes" },
```

And a data-model displays edge (around line 1177) so `V-collection-edit` is linked to `DM-collection`:

```json
{ "id": "e-V-collection-edit-DM-collection", "project_id": "pebbles", "source_id": "V-collection-edit", "target_id": "DM-collection", "edge_type": "displays" },
```

- [ ] **Step 5: Validate the bundle**

Run the validation script referenced by the `arkaik` skill (path from Step 1). Typical invocation:

```bash
node .claude/skills/arkaik/validate.js docs/arkaik/bundle.json
```

Expected: `OK` / `Bundle is valid`. If the script lives elsewhere, adapt the path; do **not** skip validation.

- [ ] **Step 6: Commit**

```bash
cd /Users/alexis/code/pbbls
git add docs/arkaik/bundle.json
git commit -m "$(cat <<'EOF'
docs(arkaik): register collection detail and edit surfaces (#216)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Manual QA and final verification

Runs the smoke-test checklist from the spec. This is the acceptance gate.

**Files:** none (verification only).

- [ ] **Step 1: Full build + test**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -quiet 2>&1 | tail -30
```

Expected: both succeed. Test output should show the three new suites (`Collection decoding`, `groupPebblesByMonth`, `CollectionUpdatePayload encoding`) with all tests passing.

- [ ] **Step 2: Run in the simulator and walk the checklist**

Launch the app against a Supabase environment with at least two collections (one with pebbles, one empty) and at least a handful of pebbles spread across two or more calendar months.

- [ ] Profile → **Collections** row displays each collection with its mode badge and correct pebble count ("No pebbles" / "1 pebble" / "N pebbles").
- [ ] Tap a collection → **Collection Detail** pushes. Title = collection name. Subheader shows the mode badge + count.
- [ ] Pebbles are grouped under "MMMM yyyy" section headers; months ordered most-recent first.
- [ ] Tap a pebble → `EditPebbleSheet` opens. Save → detail refreshes, grouping is still correct.
- [ ] Toolbar **Edit** → `EditCollectionSheet` opens. Name field prefilled, mode segmented picker selects current mode.
- [ ] Change name only → Save → title, list row, subheader all reflect the new name.
- [ ] Change mode only → Save → badge updates on detail subheader and list row.
- [ ] Change mode to **None** → Save → badge disappears from subheader and list row.
- [ ] Edit sheet Save is disabled when no change is made; disabled when name is blank.
- [ ] Swipe a collection row left → **Delete** reveals; tap → confirmation dialog titled "Delete <name>?" with the "Linked pebbles stay" message.
- [ ] Confirm delete → collection disappears. Open **Path** → the pebbles that were linked still exist.
- [ ] Cancel delete → dialog closes, nothing changes.
- [ ] Pull-to-refresh on the list reloads and preserves order.
- [ ] **Error paths** (toggle airplane mode before each):
  - [ ] List fetch fails → "Couldn't load collections" view renders.
  - [ ] Detail fetch fails → "Couldn't load pebbles" view renders.
  - [ ] Edit save fails → red footnote "Couldn't save your changes. Please try again." appears in the sheet.
  - [ ] Delete fails → `.alert("Couldn't delete")` appears.
- [ ] **Empty states**:
  - [ ] A user with zero collections sees the "No collections yet" state.
  - [ ] A collection with zero pebbles shows the "No pebbles yet" state in detail.

- [ ] **Step 3: Lint pass**

```bash
cd /Users/alexis/code/pbbls
npm run lint --workspace=apps/ios 2>/dev/null || echo "(no ios lint script — skip)"
```

- [ ] **Step 4: Push the branch and open the PR**

Follow the PR workflow checklist in `CLAUDE.md` (conventional commits title, `Resolves #216`, propose inheriting labels/milestone from the issue — `feat`, `ui`, `ios`, milestone `M21 · Souls & collections`).

```bash
cd /Users/alexis/code/pbbls
git push -u origin feat/216-ios-collections-list-detail
```

Then:

```bash
gh pr create --title "feat(ios): collections list and detail (#216)" --body "$(cat <<'EOF'
Resolves #216.

## Summary
- New `CollectionsListView` row: name, mode badge, pebble count, swipe-to-delete with confirmation, pull-to-refresh.
- New `CollectionDetailView` with a subheader and month-grouped pebble timeline. Tap a pebble → `EditPebbleSheet`.
- New `EditCollectionSheet` — rename + re-mode (None/Stack/Pack/Track) via a single UPDATE on `collections`.
- New testable helper `groupPebblesByMonth` and a `Collection` model with a custom decoder that unwraps PostgREST's nested count aggregate.
- Retired the stub `PebbleCollection` model.
- Arkaik bundle: `V-collection-detail` → development, new `V-collection-edit` node + edges.

## Out of scope (deferred)
- Creating a collection and adding pebbles (#217).
- Per-collection rise level preview (no schema support).
- Collection visibility (no schema support).

## Test plan
- [x] New Swift Testing suites pass: `Collection decoding`, `groupPebblesByMonth`, `CollectionUpdatePayload encoding`.
- [x] Manual smoke test walked per the plan (list, detail, edit, delete, empty states, error states).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Verify PR labels and milestone**

```bash
gh pr view --json labels,milestone
```

If labels or milestone are missing, add them per `CLAUDE.md`:

```bash
gh pr edit --add-label feat --add-label ui --add-label ios --milestone "M21 · Souls & collections"
```

---

## Self-review

Final check before handoff:

- **Spec coverage**
  - ✅ Data model & queries → Task 1.
  - ✅ `CollectionModeBadge` → Task 2.
  - ✅ Grouping helper → Task 3.
  - ✅ `EditCollectionSheet` + payload → Task 4.
  - ✅ `CollectionDetailView` → Task 5.
  - ✅ `CollectionsListView` rebuild + swipe-delete + pull-to-refresh → Task 6.
  - ✅ Retire `PebbleCollection.swift` → Task 7.
  - ✅ Arkaik map update → Task 8.
  - ✅ Error handling every async path → Tasks 5, 6, 4 (logger + user-facing state).
  - ✅ All three test suites from the spec → Tasks 1, 3, 4.
  - ✅ Manual QA checklist → Task 9.

- **Type consistency**
  - `Collection` / `CollectionMode` / `CollectionUpdatePayload` names match between tasks.
  - `groupPebblesByMonth(_:calendar:)` signature matches between the helper (Task 3) and its caller (Task 5).
  - `CollectionDetailView(collection:onChanged:)` init matches the call site in `CollectionsListView` (Task 6).
  - `EditCollectionSheet(collection:onSaved:)` init matches the call site in `CollectionDetailView` (Task 5).
  - Logger categories: `"profile.collections"` used in both list and edit sheet (intentional — they're profile-scoped), `"profile.collection.detail"` only in detail view.

- **Placeholders:** none. Every step has concrete code or commands.
