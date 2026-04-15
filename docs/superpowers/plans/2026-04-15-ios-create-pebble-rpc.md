# iOS — CreatePebbleSheet → create_pebble RPC · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Migrate `CreatePebbleSheet` to call the `create_pebble` RPC instead of issuing four separate direct inserts, closing the atomicity gap and aligning with the edit flow.

**Architecture:** Introduce `PebbleCreatePayload` (Encodable, mirrors `PebbleUpdatePayload`). Rewrite `CreatePebbleSheet.save()` to call `supabase.client.rpc("create_pebble", params:)`. Change `onCreated` callback to `() -> Void` and have `PathView` refetch its list. Delete now-unused `PebbleInsert` and the private join-row helper structs.

**Tech Stack:** SwiftUI (iOS 17+), Swift Testing, Supabase Swift SDK.

**Spec:** `docs/superpowers/specs/2026-04-15-ios-create-pebble-rpc-design.md`

**Branch:** `quality/257-create-pebble-rpc` (already checked out)

---

## Task 1: Create `PebbleCreatePayload` with tests (TDD)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`
- Test: `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`

### Step 1: Write the failing tests

Create `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleCreatePayload encoding")
struct PebbleCreatePayloadEncodingTests {

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

    private func makeValidDraft(
        soulId: UUID? = nil,
        collectionId: UUID? = nil
    ) -> PebbleDraft {
        var draft = PebbleDraft()
        draft.name = "Test"
        draft.description = "body"
        draft.emotionId = UUID()
        draft.domainId = UUID()
        draft.valence = .highlightLarge
        draft.soulId = soulId
        draft.collectionId = collectionId
        draft.visibility = .private
        return draft
    }

    @Test("encodes all scalar fields with snake_case keys")
    func scalarKeys() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleCreatePayload(from: draft))

        #expect(json["name"] as? String == "Test")
        #expect(json["description"] as? String == "body")
        #expect(json["happened_at"] is String)
        #expect(json["emotion_id"] is String)
        #expect(json["intensity"] as? Int == 3)
        #expect(json["positiveness"] as? Int == 1)
        #expect(json["visibility"] as? String == "private")
    }

    @Test("domain_ids is always a single-element array")
    func domainIds() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleCreatePayload(from: draft))

        let ids = json["domain_ids"] as? [String] ?? []
        #expect(ids.count == 1)
        #expect(ids.first == draft.domainId?.uuidString)
    }

    @Test("soul_ids is empty array when soulId is nil")
    func emptySoulIds() throws {
        let draft = makeValidDraft(soulId: nil)
        let json = try encode(PebbleCreatePayload(from: draft))

        let ids = json["soul_ids"] as? [String] ?? ["not-empty"]
        #expect(ids.isEmpty)
    }

    @Test("soul_ids is single-element array when soulId is set")
    func singleSoulId() throws {
        let soulId = UUID()
        let draft = makeValidDraft(soulId: soulId)
        let json = try encode(PebbleCreatePayload(from: draft))

        let ids = json["soul_ids"] as? [String] ?? []
        #expect(ids == [soulId.uuidString])
    }

    @Test("collection_ids follows the same pattern as soul_ids")
    func collectionIds() throws {
        let collectionId = UUID()
        let draftWith = makeValidDraft(collectionId: collectionId)
        let jsonWith = try encode(PebbleCreatePayload(from: draftWith))
        #expect((jsonWith["collection_ids"] as? [String]) == [collectionId.uuidString])

        let draftWithout = makeValidDraft(collectionId: nil)
        let jsonWithout = try encode(PebbleCreatePayload(from: draftWithout))
        #expect((jsonWithout["collection_ids"] as? [String])?.isEmpty == true)
    }

    @Test("description encodes as null when empty-string-trimmed")
    func emptyDescriptionBecomesNull() throws {
        var draft = makeValidDraft()
        draft.description = "   "
        let json = try encode(PebbleCreatePayload(from: draft))
        #expect(json["description"] is NSNull)
    }
}
```

### Step 2: Run tests → expect compile failure

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:PebblesTests/PebbleCreatePayloadEncodingTests 2>&1 | tail -30
```

Expected: compile error — `PebbleCreatePayload` does not exist.

### Step 3: Create the payload type

Create `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`:

```swift
import Foundation

/// The Encodable payload sent as the `payload` jsonb parameter of the
/// `create_pebble` Postgres RPC.
///
/// Shape mirrors `PebbleUpdatePayload`: snake_case keys, arrays for
/// domain/soul/collection links (even when the UI only allows one of each).
struct PebbleCreatePayload: Encodable {
    let name: String
    let description: String?
    let happenedAt: Date
    let intensity: Int
    let positiveness: Int
    let visibility: String
    let emotionId: UUID
    let domainIds: [UUID]
    let soulIds: [UUID]
    let collectionIds: [UUID]

    enum CodingKeys: String, CodingKey {
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotionId = "emotion_id"
        case domainIds = "domain_ids"
        case soulIds = "soul_ids"
        case collectionIds = "collection_ids"
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(description, forKey: .description)
        try container.encode(happenedAt, forKey: .happenedAt)
        try container.encode(intensity, forKey: .intensity)
        try container.encode(positiveness, forKey: .positiveness)
        try container.encode(visibility, forKey: .visibility)
        try container.encode(emotionId, forKey: .emotionId)
        try container.encode(domainIds, forKey: .domainIds)
        try container.encode(soulIds, forKey: .soulIds)
        try container.encode(collectionIds, forKey: .collectionIds)
    }
}

extension PebbleCreatePayload {
    /// Build a payload from a validated draft.
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft) {
        precondition(draft.isValid, "PebbleCreatePayload(from:) called with invalid draft")
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
        self.domainIds = [draft.domainId!]
        self.soulIds = draft.soulId.map { [$0] } ?? []
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
    }
}
```

### Step 4: Run tests → expect 6 passing

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:PebblesTests/PebbleCreatePayloadEncodingTests 2>&1 | tail -30
```

Expected: 6/6 pass.

### Step 5: Commit

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift \
        apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add PebbleCreatePayload for create_pebble RPC

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Refactor `CreatePebbleSheet` to use the RPC

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Delete: `apps/ios/Pebbles/Features/Path/Models/PebbleInsert.swift`

### Step 1: Rewrite `CreatePebbleSheet.swift`

Read the current file in full before editing. The end state should look like this — replace the whole file contents with:

```swift
import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var emotions: [Emotion] = []
    @State private var domains: [Domain] = []
    @State private var souls: [Soul] = []
    @State private var collections: [PebbleCollection] = []

    @State private var isLoadingReferences = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("New pebble")
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
                            .disabled(!draft.isValid)
                        }
                    }
                }
        }
        .task { await loadReferences() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoadingReferences {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") {
                    Task { await loadReferences() }
                }
            }
        } else {
            PebbleFormView(
                draft: $draft,
                emotions: emotions,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError
            )
        }
    }

    private func loadReferences() async {
        isLoadingReferences = true
        loadError = nil
        do {
            async let emotionsQuery: [Emotion] = supabase.client
                .from("emotions")
                .select()
                .order("name")
                .execute()
                .value
            async let domainsQuery: [Domain] = supabase.client
                .from("domains")
                .select()
                .order("name")
                .execute()
                .value
            async let soulsQuery: [Soul] = supabase.client
                .from("souls")
                .select("id, name")
                .order("name")
                .execute()
                .value
            async let collectionsQuery: [PebbleCollection] = supabase.client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value

            let (loadedEmotions, loadedDomains, loadedSouls, loadedCollections) =
                try await (emotionsQuery, domainsQuery, soulsQuery, collectionsQuery)

            self.emotions = loadedEmotions
            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.isLoadingReferences = false
        } catch {
            logger.error("reference load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load the form data."
            self.isLoadingReferences = false
        }
    }

    private func save() async {
        guard draft.isValid else { return }
        isSaving = true
        saveError = nil

        do {
            let payload = PebbleCreatePayload(from: draft)

            _ = try await supabase.client
                .rpc("create_pebble", params: CreatePebbleParams(payload: payload))
                .execute()

            onCreated()
            dismiss()
        } catch {
            logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your pebble. Please try again."
            self.isSaving = false
        }
    }
}

/// Wrapper matching the `create_pebble(payload jsonb)` RPC signature.
private struct CreatePebbleParams: Encodable {
    let payload: PebbleCreatePayload
}

#Preview {
    CreatePebbleSheet(onCreated: {})
        .environment(SupabaseService())
}
```

Notes on what changed vs. main:
- `onCreated: (Pebble) -> Void` → `onCreated: () -> Void`
- `save()` no longer does four `.insert()` calls — one `.rpc("create_pebble", ...)` instead
- `insertJoinRows`, `insertPebbleDomain`, `insertPebbleSoul`, `insertCollectionPebble` — all deleted
- `PebbleDomainRow`, `PebbleSoulRow`, `CollectionPebbleRow` private structs — all deleted
- `PebbleInsert` usage — gone (file itself deleted in Step 3)
- `#Preview` updated to pass `onCreated: {}` instead of `onCreated: { _ in }`

### Step 2: Update `PathView.swift`

In `apps/ios/Pebbles/Features/Path/PathView.swift`, find the existing `.sheet(isPresented: $isPresentingCreate)` block:

```swift
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet { newPebble in
                handleCreated(newPebble)
            }
        }
```

Replace it with:

```swift
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: {
                Task { await load() }
            })
        }
```

Also delete the `handleCreated(_:)` method from `PathView` — it has no more callers:

```swift
    private func handleCreated(_ pebble: Pebble) {
        pebbles.append(pebble)
        pebbles.sort { $0.happenedAt > $1.happenedAt }
    }
```

Leave everything else (`load()`, `body`, the edit sheet presentation, state declarations) untouched.

### Step 3: Delete `PebbleInsert.swift`

```bash
git rm apps/ios/Pebbles/Features/Path/Models/PebbleInsert.swift
```

Verify no remaining references:

```
grep -rn "PebbleInsert" apps/ios/
```

Expected: zero matches.

### Step 4: Regenerate project and build

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' build 2>&1 | tail -30
```

Expected: build succeeds.

### Step 5: Run full test suite

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' test 2>&1 | tail -40
```

Expected: 24/24 tests pass (18 pre-existing + 6 new `PebbleCreatePayloadEncodingTests`).

### Step 6: SwiftLint

```bash
cd apps/ios && swiftlint lint Pebbles/Features/Path/CreatePebbleSheet.swift Pebbles/Features/Path/PathView.swift Pebbles/Features/Path/Models/PebbleCreatePayload.swift 2>&1 | tail -10
```

Expected: 0 violations.

### Step 7: Commit

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/PathView.swift
git add -u  # picks up the PebbleInsert.swift deletion

git status  # sanity check
git commit -m "$(cat <<'EOF'
refactor(ios): migrate CreatePebbleSheet to create_pebble RPC

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

Do NOT stage `apps/ios/Pebbles.xcodeproj`.

---

## Task 3: Manual verification + PR

### Step 1: Manual smoke test (user)

1. Run the app in the simulator.
2. Tap "Record a pebble".
3. Fill every field including a collection. Save.
4. Confirm the new pebble appears in the path list.
5. Tap it → confirm the collection is set correctly.
6. Regression check: edit an existing pebble, save, confirm persistence (make sure Task 2's changes didn't break edit).

### Step 2: Push and open the PR

```bash
git push -u origin quality/257-create-pebble-rpc

gh pr create \
  --title "quality(ios): migrate CreatePebbleSheet to create_pebble RPC" \
  --label "quality,ios,core" \
  --milestone "M19 · iOS ShameVP" \
  --body "$(cat <<'EOF'
Resolves #257

## Summary

- `CreatePebbleSheet.save()` now calls the `create_pebble` RPC in a single atomic Postgres transaction instead of issuing four separate direct inserts.
- Introduces `PebbleCreatePayload` mirroring `PebbleUpdatePayload`'s shape.
- `onCreated` callback becomes `() -> Void`; `PathView` refetches its list on create (symmetric with `EditPebbleSheet.onSaved`).
- Deletes now-unused `PebbleInsert.swift` and the private join-row helper structs in `CreatePebbleSheet`.

## Key files

- `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift` — new Encodable payload
- `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` — save path rewritten, helper structs deleted
- `apps/ios/Pebbles/Features/Path/PathView.swift` — new onCreated closure, `handleCreated` deleted
- `apps/ios/Pebbles/Features/Path/Models/PebbleInsert.swift` — DELETED
- `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift` — 6 new tests

## Implementation notes

- **Spec:** \`docs/superpowers/specs/2026-04-15-ios-create-pebble-rpc-design.md\`
- **Plan:** \`docs/superpowers/plans/2026-04-15-ios-create-pebble-rpc.md\`
- Closes the asymmetry introduced by #258 where edit used an RPC but create did not.
- Karma events for creation are now computed server-side (they already were, the client just bypassed the RPC path before).
- Follow-up #256 (harden domain/soul ownership checks in both RPCs) still outstanding.

## Test plan

- [x] 24/24 tests pass (18 pre-existing + 6 new)
- [x] SwiftLint clean on modified files
- [ ] Manually: create a new pebble with a collection, verify it appears and the collection is correct
- [ ] Manually: edit an existing pebble afterwards, verify edit flow still works (regression check)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
