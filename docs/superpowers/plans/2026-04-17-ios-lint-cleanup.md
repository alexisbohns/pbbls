# iOS SwiftLint Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring `apps/ios` to zero SwiftLint violations so `npm run lint` exits clean and future regressions are visible immediately.

**Architecture:** Six per-rule commits touching 12 files. Each commit fixes one SwiftLint rule category end-to-end (rename, signature change, idiom swap) then verifies build + test + lint stay green. No config changes. No behavior changes. No new tests — we rely on the existing Swift Testing suite to prove we didn't regress the affected code paths.

**Tech Stack:** Swift 5.9, Swift Testing (`@Suite`, `@Test`, `#expect`, `#require`), SwiftUI iOS 17, SwiftLint via `npm run lint --workspace=@pbbls/ios`, xcodebuild via `npm run test --workspace=@pbbls/ios`.

**Branch:** `quality/272-ios-lint-cleanup` (already created; first commit `3845d2a` is the design spec).

---

## File structure

No new files. All 12 touched files already exist:

| File | Responsibility (unchanged) | Changes |
|------|----------------------------|---------|
| `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift` | Create-pebble wire payload + ISO8601 date encoding | Rename local `f` → `formatter` in static formatter builder |
| `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` | Create-pebble form sheet (calls compose edge function) | Hoist nested `Partial` struct to file-scope `PebbleIdPartial` |
| `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift` | Collection detail view with month-grouped timeline | Rename local `f` → `formatter` in static formatter builder |
| `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift` | Create-collection form sheet | Drop redundant `= nil` initializer on `mode` state |
| `apps/ios/Pebbles/Services/AppEnvironment.swift` | Typed Info.plist config accessor | Wrap two long `fatalError` message strings across lines |
| `apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift` | Swift Testing suite for insert payload | Replace `as!` with `try #require(... as?)` (2 sites) |
| `apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift` | Swift Testing suite for update payload | Replace `as!` with `try #require(... as?)` (2 sites) |
| `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift` | Swift Testing suite for pebble create payload | Rename `c` → `container`, replace `as!` with `try #require(... as?)` |
| `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift` | Swift Testing suite for pebble update payload | Rename `c` → `container`, replace `as!` with `try #require(... as?)` |
| `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift` | Swift Testing suite for month-grouping helper | Convert `pebble(_:)` helper to `throws`; rename `c`/`f`/`s`/`a`/`b`/`i` locals |
| `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift` | Swift Testing suite for PebbleDraft factory | Convert `makeDetail(...)` helper to `throws`; rename `d`/`s`/`c` locals (both map-closure params and decoder closure locals) |
| `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift` | Swift Testing suite for compose response decoding | Swap `"…".data(using: .utf8)!` → `Data("…".utf8)` (2 sites) |
| `apps/ios/PebblesTests/PebbleDetailDecodingTests.swift` | Swift Testing suite for PebbleDetail decoding | Swap `"…".data(using: .utf8)!` → `Data("…".utf8)` (2 sites) |

## Conventions (read before starting)

- **Do not regenerate the Xcode project.** No files are added or removed — `project.yml` stays untouched, so `xcodegen generate` is not required between commits.
- **Never run `git add .` or `-A`.** Always stage by explicit path.
- **Every commit must keep `xcodebuild test` green.** Intermediate states that fail the test suite are not acceptable.
- **Swift Testing idiom:** `#require(_:)` is imported by `import Testing` (already present in every test file we touch). It throws on nil, producing a clear per-test failure message. Use `try #require(expr as? Type)` in place of `expr as! Type`.
- **Closure parameter naming:** SwiftLint's `identifier_name` minimum is 3 characters — `dec`, `enc` in decoder/encoder closures already satisfy the rule. Leave those alone; only rename 1–2 character locals.
- **Simulator destination:** `iPhone 17` on iOS 26 (matches prior test runs on this repo). The `npm run test` script pins this implicitly.

## Verification loop (applied at the end of every task)

Each task ends with the same verification loop. Run from the repo root:

```bash
npm run lint --workspace=@pbbls/ios
```

Expected at the end of each task: a specific violation count (documented per task). The count strictly decreases each task.

```bash
npm run test --workspace=@pbbls/ios
```

Expected at the end of every task: `** TEST SUCCEEDED **` with the same test count as the task started with (~50 cases).

---

## Task 1: Replace force_cast with try #require in encoding tests

**Rule removed:** `force_cast` (6 errors).

**Files:**
- Modify: `apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift:10-13, 35-40`
- Modify: `apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift:8-11, 24-28`
- Modify: `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift:8-18`
- Modify: `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift:8-18`

- [ ] **Step 1.1 — Fix `CollectionInsertPayloadEncodingTests.swift` helper**

Replace lines 10-13 of `apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift`:

```swift
    private func encode(_ payload: CollectionInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) as! [String: Any]
    }
```

With:

```swift
    private func encode(_ payload: CollectionInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }
```

- [ ] **Step 1.2 — Fix `CollectionInsertPayloadEncodingTests.swift` inline site**

Replace lines 35-40 of the same file:

```swift
    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(json["mode"] is NSNull)
    }
```

With:

```swift
    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let object = try JSONSerialization.jsonObject(with: data)
        let json = try #require(object as? [String: Any])
        #expect(json["mode"] is NSNull)
    }
```

- [ ] **Step 1.3 — Fix `CollectionUpdatePayloadEncodingTests.swift` helper**

Replace lines 8-11 of `apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift`:

```swift
    private func encode(_ payload: CollectionUpdatePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) as! [String: Any]
    }
```

With:

```swift
    private func encode(_ payload: CollectionUpdatePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }
```

- [ ] **Step 1.4 — Fix `CollectionUpdatePayloadEncodingTests.swift` inline site**

Replace lines 22-29:

```swift
    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionUpdatePayload(name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(json["mode"] is NSNull)
    }
```

With:

```swift
    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionUpdatePayload(name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let object = try JSONSerialization.jsonObject(with: data)
        let json = try #require(object as? [String: Any])
        #expect(json["mode"] is NSNull)
    }
```

- [ ] **Step 1.5 — Fix `PebbleCreatePayloadEncodingTests.swift` helper**

Replace lines 8-18 of `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`:

```swift
    private func encode(_ payload: PebbleCreatePayload) throws -> [String: Any] {
        let encoder = JSONEncoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        encoder.dateEncodingStrategy = .custom { date, enc in
            var c = enc.singleValueContainer()
            try c.encode(formatter.string(from: date))
        }
        let data = try encoder.encode(payload)
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }
```

With (note: only `force_cast` is removed here — the `c` rename is Task 3):

```swift
    private func encode(_ payload: PebbleCreatePayload) throws -> [String: Any] {
        let encoder = JSONEncoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        encoder.dateEncodingStrategy = .custom { date, enc in
            var c = enc.singleValueContainer()
            try c.encode(formatter.string(from: date))
        }
        let data = try encoder.encode(payload)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }
```

- [ ] **Step 1.6 — Fix `PebbleUpdatePayloadEncodingTests.swift` helper**

Replace lines 8-18 of `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`:

```swift
    private func encode(_ payload: PebbleUpdatePayload) throws -> [String: Any] {
        let encoder = JSONEncoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        encoder.dateEncodingStrategy = .custom { date, enc in
            var c = enc.singleValueContainer()
            try c.encode(formatter.string(from: date))
        }
        let data = try encoder.encode(payload)
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }
```

With:

```swift
    private func encode(_ payload: PebbleUpdatePayload) throws -> [String: Any] {
        let encoder = JSONEncoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        encoder.dateEncodingStrategy = .custom { date, enc in
            var c = enc.singleValueContainer()
            try c.encode(formatter.string(from: date))
        }
        let data = try encoder.encode(payload)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }
```

- [ ] **Step 1.7 — Verify lint shows no `force_cast` errors**

Run:

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | grep -c "force_cast" || true
```

Expected: `0` (exit code from `grep -c` may be 1 when no matches — that's fine).

Then run:

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -5
```

Expected: `Found 29 violations, 20 serious in 58 files.` (down from 35/26).

- [ ] **Step 1.8 — Verify tests pass**

Run:

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **` and no test failures.

- [ ] **Step 1.9 — Commit**

```bash
git add apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift \
        apps/ios/PebblesTests/CollectionUpdatePayloadEncodingTests.swift \
        apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift \
        apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift
git commit -m "$(cat <<'EOF'
quality(ios): replace force_cast with try #require in encoding tests (#272)

Use Swift Testing's #require macro to safely unwrap the JSON-serialized
payload dictionary. Fixes six force_cast SwiftLint violations across the
four encoding test suites.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Convert try! helpers to throws in tests

**Rule removed:** `force_try` (3 errors).

**Files:**
- Modify: `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift:22-36, 38-88`
- Modify: `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift:8-57, 59-126`

- [ ] **Step 2.1 — Convert `pebble(_:)` helper in `GroupPebblesByMonthTests.swift` to throws**

Replace lines 22-36 of `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift`:

```swift
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
```

With (only the outer signature and `try!` swap — closure locals `c`/`s` are Task 3):

```swift
    private func pebble(_ happened: String) throws -> Pebble {
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
        return try decoder.decode(Pebble.self, from: json)
    }
```

- [ ] **Step 2.2 — Propagate throws through `GroupPebblesByMonthTests.swift` test methods**

Update the four tests that call `pebble(...)`. Replace lines 44-51:

```swift
    @Test("pebbles in the same month group together")
    func sameMonth() {
        let a = pebble("2026-04-02T10:00:00Z")
        let b = pebble("2026-04-28T22:00:00Z")
        let result = groupPebblesByMonth([a, b], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }
```

With:

```swift
    @Test("pebbles in the same month group together")
    func sameMonth() throws {
        let a = try pebble("2026-04-02T10:00:00Z")
        let b = try pebble("2026-04-28T22:00:00Z")
        let result = groupPebblesByMonth([a, b], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }
```

Replace lines 53-69:

```swift
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
```

With:

```swift
    @Test("different months produce separate groups ordered desc")
    func descendingOrder() throws {
        let april = try pebble("2026-04-02T10:00:00Z")
        let march = try pebble("2026-03-15T10:00:00Z")
        let may   = try pebble("2026-05-01T10:00:00Z")
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
```

Replace lines 71-79:

```swift
    @Test("input order within a group is preserved")
    func preservesInputOrder() {
        let first  = pebble("2026-04-28T10:00:00Z")
        let second = pebble("2026-04-10T10:00:00Z")
        let result = groupPebblesByMonth([first, second], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value[0].happenedAt == first.happenedAt)
        #expect(result[0].value[1].happenedAt == second.happenedAt)
    }
```

With:

```swift
    @Test("input order within a group is preserved")
    func preservesInputOrder() throws {
        let first  = try pebble("2026-04-28T10:00:00Z")
        let second = try pebble("2026-04-10T10:00:00Z")
        let result = groupPebblesByMonth([first, second], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value[0].happenedAt == first.happenedAt)
        #expect(result[0].value[1].happenedAt == second.happenedAt)
    }
```

Replace lines 81-88:

```swift
    @Test("month boundary respects the injected calendar")
    func monthBoundary() {
        // In UTC, 2026-04-01T00:00:00Z is April. In UTC-5 it would be March.
        let utcApril = pebble("2026-04-01T00:00:00Z")
        let lateMarch = pebble("2026-03-31T22:00:00Z")
        let result = groupPebblesByMonth([utcApril, lateMarch], calendar: calendar)
        #expect(result.count == 2)
    }
```

With:

```swift
    @Test("month boundary respects the injected calendar")
    func monthBoundary() throws {
        // In UTC, 2026-04-01T00:00:00Z is April. In UTC-5 it would be March.
        let utcApril = try pebble("2026-04-01T00:00:00Z")
        let lateMarch = try pebble("2026-03-31T22:00:00Z")
        let result = groupPebblesByMonth([utcApril, lateMarch], calendar: calendar)
        #expect(result.count == 2)
    }
```

(The `emptyInput` test at line 38 does not call `pebble(_:)` and stays non-throwing.)

- [ ] **Step 2.3 — Convert `makeDetail(...)` helper in `PebbleDraftFromDetailTests.swift` to throws**

Replace lines 8-57 of `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift` (the whole `makeDetail` function — we only change the signature and swap both `try!` to `try`; `d`/`s`/`c` locals stay for Task 3):

```swift
    private func makeDetail(
        name: String = "Shipped",
        description: String? = "Finally.",
        positiveness: Int = 1,
        intensity: Int = 3,
        visibility: Visibility = .private,
        emotionId: UUID = UUID(),
        domains: [DomainRef] = [DomainRef(id: UUID(), name: "Work")],
        souls: [Soul] = [],
        collections: [PebbleCollection] = []
    ) -> PebbleDetail {
        // Build JSON and decode — mirrors how PebbleDetail is actually constructed.
        // PebbleDetail has a custom init(from: Decoder), so we can't memberwise-construct it.
        let emotionJSON: [String: Any] = [
            "id": emotionId.uuidString,
            "name": "Joy",
            "color": "#FFD166"
        ]
        let domainsJSON = domains.map { d in ["domain": ["id": d.id.uuidString, "name": d.name]] }
        let soulsJSON = souls.map { s in ["soul": ["id": s.id.uuidString, "name": s.name]] }
        let collectionsJSON = collections.map { c in ["collection": ["id": c.id.uuidString, "name": c.name]] }

        var root: [String: Any] = [
            "id": UUID().uuidString,
            "name": name,
            "happened_at": "2026-04-14T15:42:00Z",
            "intensity": intensity,
            "positiveness": positiveness,
            "visibility": visibility.rawValue,
            "emotion": emotionJSON,
            "pebble_domains": domainsJSON,
            "pebble_souls": soulsJSON,
            "collection_pebbles": collectionsJSON
        ]
        if let description { root["description"] = description }

        let data = try! JSONSerialization.data(withJSONObject: root)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            guard let d = formatter.date(from: s) else {
                throw DecodingError.dataCorruptedError(in: c, debugDescription: "bad date")
            }
            return d
        }
        return try! decoder.decode(PebbleDetail.self, from: data)
    }
```

With:

```swift
    private func makeDetail(
        name: String = "Shipped",
        description: String? = "Finally.",
        positiveness: Int = 1,
        intensity: Int = 3,
        visibility: Visibility = .private,
        emotionId: UUID = UUID(),
        domains: [DomainRef] = [DomainRef(id: UUID(), name: "Work")],
        souls: [Soul] = [],
        collections: [PebbleCollection] = []
    ) throws -> PebbleDetail {
        // Build JSON and decode — mirrors how PebbleDetail is actually constructed.
        // PebbleDetail has a custom init(from: Decoder), so we can't memberwise-construct it.
        let emotionJSON: [String: Any] = [
            "id": emotionId.uuidString,
            "name": "Joy",
            "color": "#FFD166"
        ]
        let domainsJSON = domains.map { d in ["domain": ["id": d.id.uuidString, "name": d.name]] }
        let soulsJSON = souls.map { s in ["soul": ["id": s.id.uuidString, "name": s.name]] }
        let collectionsJSON = collections.map { c in ["collection": ["id": c.id.uuidString, "name": c.name]] }

        var root: [String: Any] = [
            "id": UUID().uuidString,
            "name": name,
            "happened_at": "2026-04-14T15:42:00Z",
            "intensity": intensity,
            "positiveness": positiveness,
            "visibility": visibility.rawValue,
            "emotion": emotionJSON,
            "pebble_domains": domainsJSON,
            "pebble_souls": soulsJSON,
            "collection_pebbles": collectionsJSON
        ]
        if let description { root["description"] = description }

        let data = try JSONSerialization.data(withJSONObject: root)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            guard let d = formatter.date(from: s) else {
                throw DecodingError.dataCorruptedError(in: c, debugDescription: "bad date")
            }
            return d
        }
        return try decoder.decode(PebbleDetail.self, from: data)
    }
```

- [ ] **Step 2.4 — Propagate throws through `PebbleDraftFromDetailTests.swift` test methods**

All six test methods call `makeDetail(...)`. Replace lines 59-89:

```swift
    @Test("populates all fields from a fully-populated detail")
    func fullyPopulated() {
        let emotionId = UUID()
        let domainId = UUID()
        let soulId = UUID()
        let collectionId = UUID()

        let detail = makeDetail(
            name: "Shipped",
            description: "Finally.",
            positiveness: 1,
            intensity: 3,
            visibility: .public,
            emotionId: emotionId,
            domains: [DomainRef(id: domainId, name: "Work")],
            souls: [Soul(id: soulId, name: "Me")],
            collections: [PebbleCollection(id: collectionId, name: "Wins")]
        )

        let draft = PebbleDraft(from: detail)

        #expect(draft.name == "Shipped")
        #expect(draft.description == "Finally.")
        #expect(draft.happenedAt == detail.happenedAt)
        #expect(draft.emotionId == emotionId)
        #expect(draft.domainId == domainId)
        #expect(draft.soulId == soulId)
        #expect(draft.collectionId == collectionId)
        #expect(draft.valence == .highlightLarge)
        #expect(draft.visibility == .public)
    }
```

With:

```swift
    @Test("populates all fields from a fully-populated detail")
    func fullyPopulated() throws {
        let emotionId = UUID()
        let domainId = UUID()
        let soulId = UUID()
        let collectionId = UUID()

        let detail = try makeDetail(
            name: "Shipped",
            description: "Finally.",
            positiveness: 1,
            intensity: 3,
            visibility: .public,
            emotionId: emotionId,
            domains: [DomainRef(id: domainId, name: "Work")],
            souls: [Soul(id: soulId, name: "Me")],
            collections: [PebbleCollection(id: collectionId, name: "Wins")]
        )

        let draft = PebbleDraft(from: detail)

        #expect(draft.name == "Shipped")
        #expect(draft.description == "Finally.")
        #expect(draft.happenedAt == detail.happenedAt)
        #expect(draft.emotionId == emotionId)
        #expect(draft.domainId == domainId)
        #expect(draft.soulId == soulId)
        #expect(draft.collectionId == collectionId)
        #expect(draft.valence == .highlightLarge)
        #expect(draft.visibility == .public)
    }
```

Replace lines 91-96:

```swift
    @Test("maps nil description to empty string")
    func nilDescription() {
        let detail = makeDetail(description: nil)
        let draft = PebbleDraft(from: detail)
        #expect(draft.description == "")
    }
```

With:

```swift
    @Test("maps nil description to empty string")
    func nilDescription() throws {
        let detail = try makeDetail(description: nil)
        let draft = PebbleDraft(from: detail)
        #expect(draft.description == "")
    }
```

Replace lines 98-103:

```swift
    @Test("leaves soulId nil when no souls")
    func noSouls() {
        let detail = makeDetail(souls: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.soulId == nil)
    }
```

With:

```swift
    @Test("leaves soulId nil when no souls")
    func noSouls() throws {
        let detail = try makeDetail(souls: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.soulId == nil)
    }
```

Replace lines 105-110:

```swift
    @Test("leaves collectionId nil when no collections")
    func noCollections() {
        let detail = makeDetail(collections: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.collectionId == nil)
    }
```

With:

```swift
    @Test("leaves collectionId nil when no collections")
    func noCollections() throws {
        let detail = try makeDetail(collections: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.collectionId == nil)
    }
```

Replace lines 112-118:

```swift
    @Test("leaves domainId nil and draft invalid when domains is empty")
    func emptyDomains() {
        let detail = makeDetail(domains: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.domainId == nil)
        #expect(draft.isValid == false)
    }
```

With:

```swift
    @Test("leaves domainId nil and draft invalid when domains is empty")
    func emptyDomains() throws {
        let detail = try makeDetail(domains: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.domainId == nil)
        #expect(draft.isValid == false)
    }
```

Replace lines 120-125:

```swift
    @Test("derives valence from positiveness and intensity pair")
    func valenceMapping() {
        let detail = makeDetail(positiveness: -1, intensity: 2)
        let draft = PebbleDraft(from: detail)
        #expect(draft.valence == .lowlightMedium)
    }
```

With:

```swift
    @Test("derives valence from positiveness and intensity pair")
    func valenceMapping() throws {
        let detail = try makeDetail(positiveness: -1, intensity: 2)
        let draft = PebbleDraft(from: detail)
        #expect(draft.valence == .lowlightMedium)
    }
```

- [ ] **Step 2.5 — Verify lint shows no `force_try` errors**

Run:

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | grep -c "force_try" || true
```

Expected: `0`.

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -5
```

Expected: `Found 26 violations, 17 serious in 58 files.` (down from 29/20).

- [ ] **Step 2.6 — Verify tests pass**

Run:

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **` with the same test count as before.

- [ ] **Step 2.7 — Commit**

```bash
git add apps/ios/PebblesTests/GroupPebblesByMonthTests.swift \
        apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift
git commit -m "$(cat <<'EOF'
quality(ios): convert try! helpers to throws in tests (#272)

GroupPebblesByMonthTests.pebble(_:) and PebbleDraftFromDetailTests.makeDetail(...)
are now throws helpers; callers use try. Fixes three force_try SwiftLint
violations.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Rename short locals flagged by identifier_name

**Rule removed:** `identifier_name` (17 errors).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift:33-37`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift:27-31`
- Modify: `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift:12-15`
- Modify: `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift:12-15`
- Modify: `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift:10-14, 16-20, 30-34, 46-47, 64`
- Modify: `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift:26-28, 48-55`

- [ ] **Step 3.1 — Rename `f` → `formatter` in `PebbleCreatePayload.swift`**

Replace lines 33-37 of `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`:

```swift
    private static let iso8601: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
```

With:

```swift
    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
```

- [ ] **Step 3.2 — Rename `f` → `formatter` in `CollectionDetailView.swift`**

Replace lines 27-31 of `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`:

```swift
    private static let monthFormatter: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("MMMM yyyy")
        return f
    }()
```

With:

```swift
    private static let monthFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMMM yyyy")
        return formatter
    }()
```

- [ ] **Step 3.3 — Rename `c` → `container` in `PebbleCreatePayloadEncodingTests.swift`**

Replace lines 12-15 of `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`:

```swift
        encoder.dateEncodingStrategy = .custom { date, enc in
            var c = enc.singleValueContainer()
            try c.encode(formatter.string(from: date))
        }
```

With:

```swift
        encoder.dateEncodingStrategy = .custom { date, enc in
            var container = enc.singleValueContainer()
            try container.encode(formatter.string(from: date))
        }
```

- [ ] **Step 3.4 — Rename `c` → `container` in `PebbleUpdatePayloadEncodingTests.swift`**

Replace lines 12-15 of `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`:

```swift
        encoder.dateEncodingStrategy = .custom { date, enc in
            var c = enc.singleValueContainer()
            try c.encode(formatter.string(from: date))
        }
```

With:

```swift
        encoder.dateEncodingStrategy = .custom { date, enc in
            var container = enc.singleValueContainer()
            try container.encode(formatter.string(from: date))
        }
```

- [ ] **Step 3.5 — Rename locals in `GroupPebblesByMonthTests.swift` calendar computed property**

Replace lines 10-14 of `apps/ios/PebblesTests/GroupPebblesByMonthTests.swift`:

```swift
    private var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }
```

With:

```swift
    private var calendar: Calendar {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = TimeZone(identifier: "UTC")!
        return gregorian
    }
```

- [ ] **Step 3.6 — Rename `f` → `formatter` in `GroupPebblesByMonthTests.swift` date helper**

Replace lines 16-20 of the same file:

```swift
    private func date(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: iso)!
    }
```

With:

```swift
    private func date(_ iso: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)!
    }
```

- [ ] **Step 3.7 — Rename `c`/`s` in `GroupPebblesByMonthTests.swift` decoder closure**

Replace lines 30-34 of the same file:

```swift
        decoder.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            return formatter.date(from: s)!
        }
```

With:

```swift
        decoder.dateDecodingStrategy = .custom { dec in
            let container = try dec.singleValueContainer()
            let iso = try container.decode(String.self)
            return formatter.date(from: iso)!
        }
```

- [ ] **Step 3.8 — Rename `a`/`b` → `early`/`late` in `sameMonth` test**

Replace lines 45-51 of `GroupPebblesByMonthTests.swift` (the body of `sameMonth` — unchanged signature from Task 2):

```swift
    func sameMonth() throws {
        let a = try pebble("2026-04-02T10:00:00Z")
        let b = try pebble("2026-04-28T22:00:00Z")
        let result = groupPebblesByMonth([a, b], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }
```

With:

```swift
    func sameMonth() throws {
        let early = try pebble("2026-04-02T10:00:00Z")
        let late = try pebble("2026-04-28T22:00:00Z")
        let result = groupPebblesByMonth([early, late], calendar: calendar)
        #expect(result.count == 1)
        #expect(result[0].value.count == 2)
    }
```

- [ ] **Step 3.9 — Rename `i` → `index` in `descendingOrder` test**

Replace line 64 of `GroupPebblesByMonthTests.swift`:

```swift
        for (i, expected) in expectedOrder.enumerated() {
            let comps = calendar.dateComponents([.year, .month], from: result[i].key)
            #expect(comps.year == expected.year)
            #expect(comps.month == expected.month)
        }
```

With:

```swift
        for (index, expected) in expectedOrder.enumerated() {
            let comps = calendar.dateComponents([.year, .month], from: result[index].key)
            #expect(comps.year == expected.year)
            #expect(comps.month == expected.month)
        }
```

- [ ] **Step 3.10 — Rename closure params in `PebbleDraftFromDetailTests.swift` map calls**

Replace lines 26-28 of `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift`:

```swift
        let domainsJSON = domains.map { d in ["domain": ["id": d.id.uuidString, "name": d.name]] }
        let soulsJSON = souls.map { s in ["soul": ["id": s.id.uuidString, "name": s.name]] }
        let collectionsJSON = collections.map { c in ["collection": ["id": c.id.uuidString, "name": c.name]] }
```

With:

```swift
        let domainsJSON = domains.map { domain in ["domain": ["id": domain.id.uuidString, "name": domain.name]] }
        let soulsJSON = souls.map { soul in ["soul": ["id": soul.id.uuidString, "name": soul.name]] }
        let collectionsJSON = collections.map { coll in ["collection": ["id": coll.id.uuidString, "name": coll.name]] }
```

- [ ] **Step 3.11 — Rename `c`/`s`/`d` in `PebbleDraftFromDetailTests.swift` decoder closure**

Replace lines 48-55 (the decoder custom strategy closure inside `makeDetail`):

```swift
        decoder.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            guard let d = formatter.date(from: s) else {
                throw DecodingError.dataCorruptedError(in: c, debugDescription: "bad date")
            }
            return d
        }
```

With:

```swift
        decoder.dateDecodingStrategy = .custom { dec in
            let container = try dec.singleValueContainer()
            let iso = try container.decode(String.self)
            guard let date = formatter.date(from: iso) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "bad date")
            }
            return date
        }
```

- [ ] **Step 3.12 — Verify lint shows no `identifier_name` errors**

Run:

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | grep -c "identifier_name" || true
```

Expected: `0`.

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -5
```

Expected: `Found 9 violations, 0 serious in 58 files.` (down from 26/17 — all errors gone, only warnings remain).

- [ ] **Step 3.13 — Verify tests pass**

Run:

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **` with the same test count.

- [ ] **Step 3.14 — Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift \
        apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift \
        apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift \
        apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift \
        apps/ios/PebblesTests/GroupPebblesByMonthTests.swift \
        apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift
git commit -m "$(cat <<'EOF'
quality(ios): rename short locals flagged by identifier_name (#272)

Rename one- and two-character locals to semantic names (formatter,
container, iso, date, early, late, index, domain, soul, coll, gregorian).
Fixes 17 identifier_name violations across two production files and four
test suites.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Prefer Data(_:) over String.data(using:)

**Rule removed:** `non_optional_string_data_conversion` (4 warnings).

**Files:**
- Modify: `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift:10-17, 28-33`
- Modify: `apps/ios/PebblesTests/PebbleDetailDecodingTests.swift:139-154, 163-177`

- [ ] **Step 4.1 — Fix `ComposePebbleResponseDecodingTests.swift` first site**

Replace lines 10-17 of `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift`:

```swift
        let json = """
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "render_svg": "<svg xmlns=\\"http://www.w3.org/2000/svg\\"></svg>",
          "render_manifest": [{"type":"glyph","delay":0,"duration":800}],
          "render_version": "0.1.0"
        }
        """.data(using: .utf8)!
```

With:

```swift
        let json = Data("""
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "render_svg": "<svg xmlns=\\"http://www.w3.org/2000/svg\\"></svg>",
          "render_manifest": [{"type":"glyph","delay":0,"duration":800}],
          "render_version": "0.1.0"
        }
        """.utf8)
```

- [ ] **Step 4.2 — Fix `ComposePebbleResponseDecodingTests.swift` second site**

Replace lines 28-33 of the same file:

```swift
        let json = """
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "error": "compose failed: engine exploded"
        }
        """.data(using: .utf8)!
```

With:

```swift
        let json = Data("""
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "error": "compose failed: engine exploded"
        }
        """.utf8)
```

- [ ] **Step 4.3 — Read `PebbleDetailDecodingTests.swift` to confirm surrounding context**

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | grep PebbleDetailDecodingTests
```

Expected: two warnings on lines 139 and 164 referencing `non_optional_string_data_conversion`.

- [ ] **Step 4.4 — Fix `PebbleDetailDecodingTests.swift` first site (line 139)**

Open the file and locate the `decodesRenderColumns` test body. Replace the JSON literal that currently reads `""" ... """.data(using: .utf8)!`:

```swift
        let json = """
        {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "test",
          "happened_at": "2026-04-15T12:00:00Z",
          "intensity": 2,
          "positiveness": 0,
          "visibility": "private",
          "emotion": {"id": "550e8400-e29b-41d4-a716-446655440001", "name": "joy", "color": "#fff"},
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": [],
          "render_svg": "<svg/>",
          "render_version": "0.1.0"
        }
        """.data(using: .utf8)!
```

With:

```swift
        let json = Data("""
        {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "test",
          "happened_at": "2026-04-15T12:00:00Z",
          "intensity": 2,
          "positiveness": 0,
          "visibility": "private",
          "emotion": {"id": "550e8400-e29b-41d4-a716-446655440001", "name": "joy", "color": "#fff"},
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": [],
          "render_svg": "<svg/>",
          "render_version": "0.1.0"
        }
        """.utf8)
```

- [ ] **Step 4.5 — Fix `PebbleDetailDecodingTests.swift` second site (line 164)**

Locate the `decodesLegacy` test body. It has a JSON literal with the same shape minus the `render_svg` / `render_version` keys. Wrap the same way:

Before (the ending fragment that differs, at ~line 176-179):

```swift
        }
        """.data(using: .utf8)!
```

After: prefix the opening `"""` with `Data(` and change `.data(using: .utf8)!` to `.utf8)`:

```swift
        let json = Data("""
        {
          ...
        }
        """.utf8)
```

Use the Edit tool with a unique surrounding snippet (e.g. `"legacy"` inside the JSON) to avoid ambiguity with the first site.

- [ ] **Step 4.6 — Verify lint shows no `non_optional_string_data_conversion` warnings**

Run:

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | grep -c "non_optional_string_data_conversion" || true
```

Expected: `0`.

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -5
```

Expected: `Found 5 violations, 0 serious in 58 files.` (down from 9 warnings).

- [ ] **Step 4.7 — Verify tests pass**

Run:

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 4.8 — Commit**

```bash
git add apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift \
        apps/ios/PebblesTests/PebbleDetailDecodingTests.swift
git commit -m "$(cat <<'EOF'
quality(ios): prefer Data(_:) over String.data(using:) in tests (#272)

Data(String.UTF8View) returns non-optional Data, avoiding the force-unwrap
of the deprecated pattern. Fixes four non_optional_string_data_conversion
SwiftLint warnings.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Hoist PebbleIdPartial and wrap long AppEnvironment messages

**Rules removed:** `line_length` (3 warnings), `nesting` (1 warning).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift:157-170`
- Modify: `apps/ios/Pebbles/Services/AppEnvironment.swift:7-22`

- [ ] **Step 5.1 — Hoist `Partial` struct out of `CreatePebbleSheet.softSuccessPebbleId(from:)`**

Replace lines 157-161 of `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`:

```swift
    private func softSuccessPebbleId(from error: FunctionsError) -> UUID? {
        guard case let .httpError(_, data) = error, !data.isEmpty else { return nil }
        struct Partial: Decodable { let pebbleId: UUID; enum CodingKeys: String, CodingKey { case pebbleId = "pebble_id" } }
        return try? JSONDecoder().decode(Partial.self, from: data).pebbleId
    }
```

With:

```swift
    private func softSuccessPebbleId(from error: FunctionsError) -> UUID? {
        guard case let .httpError(_, data) = error, !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(PebbleIdPartial.self, from: data).pebbleId
    }
```

Then add the hoisted struct at file scope. Replace lines 164-169 (the existing `ComposePebbleRequest` wrapper block):

```swift
/// Wrapper matching the compose-pebble edge function body shape.
/// The function expects `{ "payload": {...} }` where `payload` mirrors
/// the create_pebble RPC payload.
private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}
```

With:

```swift
/// Wrapper matching the compose-pebble edge function body shape.
/// The function expects `{ "payload": {...} }` where `payload` mirrors
/// the create_pebble RPC payload.
private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}

/// Partial decoder for the compose-pebble soft-success response body.
/// Used by `softSuccessPebbleId(from:)` to extract `pebble_id` out of an
/// `httpError` payload when the render itself failed.
private struct PebbleIdPartial: Decodable {
    let pebbleId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}
```

- [ ] **Step 5.2 — Wrap first fatalError message in `AppEnvironment.swift`**

Replace lines 7-14 of `apps/ios/Pebbles/Services/AppEnvironment.swift`:

```swift
    static let supabaseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
              !raw.isEmpty,
              let url = URL(string: raw) else {
            fatalError("SupabaseURL missing or invalid in Info.plist. Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?")
        }
        return url
    }()
```

With:

```swift
    static let supabaseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
              !raw.isEmpty,
              let url = URL(string: raw) else {
            fatalError(
                "SupabaseURL missing or invalid in Info.plist. " +
                "Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?"
            )
        }
        return url
    }()
```

- [ ] **Step 5.3 — Wrap second fatalError message in `AppEnvironment.swift`**

Replace lines 16-22 of the same file:

```swift
    static let supabaseAnonKey: String = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String,
              !key.isEmpty else {
            fatalError("SupabaseAnonKey missing in Info.plist. Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?")
        }
        return key
    }()
```

With:

```swift
    static let supabaseAnonKey: String = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String,
              !key.isEmpty else {
            fatalError(
                "SupabaseAnonKey missing in Info.plist. " +
                "Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?"
            )
        }
        return key
    }()
```

- [ ] **Step 5.4 — Verify lint shows no `line_length` or `nesting` warnings**

Run:

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | grep -cE "line_length|nesting" || true
```

Expected: `0`.

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -5
```

Expected: `Found 1 violation, 0 serious in 58 files.` (only `implicit_optional_initialization` remains).

- [ ] **Step 5.5 — Verify build and tests pass**

Run:

```bash
npm run build --workspace=@pbbls/ios 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 5.6 — Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Services/AppEnvironment.swift
git commit -m "$(cat <<'EOF'
quality(ios): hoist PebbleIdPartial and wrap AppEnvironment messages (#272)

Promote the nested Partial decoder out of softSuccessPebbleId(from:) to a
file-scope PebbleIdPartial struct alongside ComposePebbleRequest. Wrap the
two long fatalError strings in AppEnvironment across multiple lines. Fixes
one nesting and three line_length SwiftLint warnings.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Drop redundant = nil on CreateCollectionSheet state

**Rule removed:** `implicit_optional_initialization` (1 warning).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift:13`

- [ ] **Step 6.1 — Drop `= nil` initializer**

Replace line 13 of `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`:

```swift
    @State private var mode: CollectionMode? = nil
```

With:

```swift
    @State private var mode: CollectionMode?
```

- [ ] **Step 6.2 — Verify lint is clean**

Run:

```bash
npm run lint --workspace=@pbbls/ios
```

Expected output ends with: `Done linting! Found 0 violations, 0 serious in 58 files.` and exits 0.

- [ ] **Step 6.3 — Verify the whole workspace lint exits 0**

Run:

```bash
npm run lint
```

Expected: exits 0. This is the acceptance criterion on #272.

- [ ] **Step 6.4 — Verify tests pass**

Run:

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 6.5 — Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift
git commit -m "$(cat <<'EOF'
quality(ios): drop redundant = nil on CreateCollectionSheet mode (#272)

@State private var mode: CollectionMode? initializes to nil by default.
Fixes the final implicit_optional_initialization SwiftLint warning.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Wrap-up (after Task 6)

- [ ] **Push branch and open PR**

```bash
git push -u origin quality/272-ios-lint-cleanup
```

Open PR with:

- **Title:** `quality(ios): fix SwiftLint violations across iOS package (#272)`
- **Body:** `Resolves #272.` Short summary of the six rule categories addressed, plus the lint count delta (35 → 0).
- **Labels:** `quality`, `ios` (inherit from #272).
- **Milestone:** `M21 · Souls & collections` (inherit from #272).
- **Confirm with user** before applying labels/milestone per project convention.

Acceptance verification on the PR:
- `npm run lint` from repo root exits 0.
- `npm run test --workspace=@pbbls/ios` reports `** TEST SUCCEEDED **` with the same test count as main.
- `git diff main..HEAD --stat` shows ~12 files touched, no new files (the spec commit and this plan's six commits = 7 commits on the branch after the initial spec commit at `3845d2a`).
