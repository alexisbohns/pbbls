# iOS Create Collection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `+` toolbar entry on `CollectionsListView` that opens a new `CreateCollectionSheet`, INSERTs a row into `public.collections`, and reloads the list. Resolves issue #217 (scope A — create-only).

**Architecture:** SwiftUI, iOS 17+. `@Environment(SupabaseService.self)` for data access. Direct `.from("collections").insert(...)` — RLS `collections_insert` policy keys on `user_id = auth.uid()`, so the client only needs the session user id in the payload. No RPC. Mirrors the souls create pattern in `CreateSoulSheet.swift`, extended with a segmented mode picker that reuses `CollectionMode` from the model landed in PR #269.

**Tech Stack:** Swift 5.9, SwiftUI, Supabase Swift SDK, Swift Testing (`@Suite` / `@Test` / `#expect`), xcodegen, `os.Logger`.

**Reference:** spec at `docs/superpowers/specs/2026-04-17-ios-create-collection-design.md`.

---

## Conventions & Shared Context

Read these once before starting; every task depends on them.

**Branch:** `feat/217-ios-create-collection` (already created; the spec commit lives there).

**Generate Xcode project after adding files:**

```bash
cd apps/ios && npm run generate
```

`project.yml` globs the `Pebbles` and `PebblesTests` folders, so new `.swift` files are picked up on regenerate. Run this once per task that adds a file.

**Build verification command (run from repo root):**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`. If this fails, stop and fix before continuing.

**Test verification command:**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -quiet | tail -30
```

Expected: `** TEST SUCCEEDED **`. New `CollectionInsertPayload encoding` suite runs alongside the existing ones — final tally should include three Collection\* suites (`Collection decoding`, `CollectionUpdatePayload encoding`, `CollectionInsertPayload encoding`) plus `groupPebblesByMonth`.

**Logger convention:** every `try/catch` on an async path logs `logger.error("<operation> failed: \(error.localizedDescription, privacy: .private)")` and surfaces a user-facing string. No empty catches. Category for this work: `"profile.collections"` (same as `CollectionsListView` and `EditCollectionSheet`).

**Existing types you'll reuse (do not modify):**
- `apps/ios/Pebbles/Features/Profile/Models/Collection.swift` — `enum CollectionMode: String { stack, pack, track }`. Raw values match the DB check constraint.
- `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift:104-118` — `CollectionUpdatePayload` is the sibling of the new insert payload. Keep the encoding pattern symmetric (custom `encode(to:)` that forces JSON null for `mode`).
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` — the structural template for the new sheet (form + save/cancel toolbar + direct INSERT + `onCreated` callback).
- `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift:19-34` — the toolbar + `.sheet(isPresented:)` wiring to mirror on the collections list.

**RLS & schema context:** `collections` columns are `(id, user_id, name, mode, created_at, updated_at)`. `mode` has a check constraint `in ('stack', 'pack', 'track')` and is nullable. `id`, `created_at`, `updated_at` are auto-defaulted — we only send `user_id`, `name`, `mode`. The `collections_insert` RLS policy requires `user_id = auth.uid()`, so the client must include the session user id in the row (mirrors `SoulInsertPayload`).

**Commit convention:** one commit per task. Format: `type(scope): description (#217)`. Types: `feat`, `test`, `docs`, `quality`. Scope: `ios` for app code, `arkaik` for the map.

---

## File Structure

| Path | Role |
|------|------|
| `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift` | NEW — the sheet view (name field + segmented mode picker + save/cancel toolbar) plus `CollectionInsertPayload: Encodable` at the bottom of the file (non-private so tests can reach it). |
| `apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift` | NEW — Swift Testing suite for the payload encoding (keys, JSON null mode, rawValue fidelity). |
| `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift` | MODIFIED — add `isPresentingCreate` state, `+` toolbar item, `.sheet(isPresented:)` presenting `CreateCollectionSheet`. |
| `docs/arkaik/bundle.json` | MODIFIED — flip `V-collection-create` status from `"idea"` to `"development"`, add `e-V-collection-create-DM-collection` display edge. Other composition/calls edges already exist. |

No schema migrations. No RPC changes. No new dependencies.

---

## Task 1: `CollectionInsertPayload` + encoding tests (TDD)

Land the wire shape first with failing encoding tests, then the minimum real implementation. The View shell is included in the same file since `CollectionInsertPayload` conventionally lives alongside the sheet that uses it (matches `CollectionUpdatePayload` inside `EditCollectionSheet.swift`).

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`
- Test: `apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift`

- [ ] **Step 1: Write the failing test file**

Create `apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CollectionInsertPayload encoding")
struct CollectionInsertPayloadEncodingTests {

    private let userId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!

    private func encode(_ payload: CollectionInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) as! [String: Any]
    }

    @Test("encodes user_id as snake_case and name verbatim")
    func userIdAndName() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Summer", mode: "pack")
        let json = try encode(payload)
        // Swift's default JSONEncoder UUID strategy is `.deferredToUUID`, which
        // emits the same form as `UUID.uuidString`, so the round-trip is exact.
        #expect(json["user_id"] as? String == userId.uuidString)
        #expect(json["name"] as? String == "Summer")
    }

    @Test("encodes mode rawValue when set")
    func modeWhenSet() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "x", mode: "stack")
        let json = try encode(payload)
        #expect(json["mode"] as? String == "stack")
    }

    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(json["mode"] is NSNull)
    }

    @Test("each mode rawValue round-trips")
    func allModes() throws {
        for raw in ["stack", "pack", "track"] {
            let payload = CollectionInsertPayload(userId: userId, name: "x", mode: raw)
            let json = try encode(payload)
            #expect(json["mode"] as? String == raw)
        }
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project and see the test fail**

```bash
cd apps/ios && npm run generate
```

Then run the tests:

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -quiet 2>&1 | tail -30
```

Expected: `** TEST FAILED **` with a Swift compile error like `cannot find 'CollectionInsertPayload' in scope` pointing at `CollectionInsertPayloadEncodingTests.swift`. This confirms the test is wired into the target.

- [ ] **Step 3: Create the sheet file with the payload and full view**

Create `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`:

```swift
import SwiftUI
import os

/// Sheet for creating a new collection: name + optional mode.
/// INSERT goes directly to `public.collections` — RLS scopes to the owner.
struct CreateCollectionSheet: View {
    let onCreated: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var mode: CollectionMode? = nil
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
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
            .navigationTitle("New collection")
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
                        .disabled(trimmedName.isEmpty)
                    }
                }
            }
        }
    }

    private func save() async {
        guard !trimmedName.isEmpty else { return }
        guard let userId = supabase.session?.user.id else {
            logger.error("create collection: no session")
            saveError = "You're signed out. Please sign in again."
            return
        }
        isSaving = true
        saveError = nil
        do {
            let payload = CollectionInsertPayload(
                userId: userId,
                name: trimmedName,
                mode: mode?.rawValue
            )
            try await supabase.client
                .from("collections")
                .insert(payload)
                .execute()
            onCreated()
            dismiss()
        } catch {
            logger.error("create collection failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save the collection. Please try again."
            isSaving = false
        }
    }
}

/// Wire shape for `POST /collections`. Snake-case keys match the DB columns.
/// `user_id` is explicit because the RLS `with check` compares it to `auth.uid()`.
/// `mode` is explicitly encoded so that `nil` becomes JSON null, which Postgres
/// stores as SQL NULL (matching the nullable `mode` column).
struct CollectionInsertPayload: Encodable {
    let userId: UUID
    let name: String
    let mode: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
        case mode
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(userId, forKey: .userId)
        try container.encode(name, forKey: .name)
        try container.encode(mode, forKey: .mode) // forces JSON null when nil
    }
}

#Preview {
    CreateCollectionSheet(onCreated: {})
        .environment(SupabaseService())
}
```

- [ ] **Step 4: Regenerate and re-run tests — should pass**

```bash
cd apps/ios && npm run generate
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -quiet 2>&1 | tail -30
```

Expected: `** TEST SUCCEEDED **`. The `CollectionInsertPayload encoding` suite shows four passing tests. All pre-existing suites still pass.

- [ ] **Step 5: Build to catch any view-side regression**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift \
        apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add CreateCollectionSheet with insert payload (#217)

Sheet mirrors CreateSoulSheet structurally and adds a segmented mode
picker identical to EditCollectionSheet's. CollectionInsertPayload
forces JSON null on unset mode so the column stores SQL NULL rather
than being omitted.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Wire the `+` entry point into `CollectionsListView`

Add the toolbar button, presentation state, and sheet modifier. Empty-state view is not changed (matches `SoulsListView`).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`

- [ ] **Step 1: Add the presentation state**

Open `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`. Between the existing `@State private var deleteError: String?` (line 11) and the logger declaration (line 12), insert:

```swift
    @State private var isPresentingCreate = false
```

Resulting block:

```swift
    @State private var pendingDeletion: Collection?
    @State private var deleteError: String?
    @State private var isPresentingCreate = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")
```

- [ ] **Step 2: Add the `+` toolbar item and `.sheet` modifier**

Find `body` in the same file. Between `.navigationBarTitleDisplayMode(.inline)` and `.task { await load() }` (currently lines 17–18), insert the toolbar + sheet modifiers:

```swift
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add collection")
                }
            }
            .sheet(isPresented: $isPresentingCreate) {
                CreateCollectionSheet(onCreated: {
                    Task { await load() }
                })
            }
```

Place them before `.task { await load() }` to mirror the ordering in `SoulsListView.swift:19-34`. The resulting `body` opens like this:

```swift
    var body: some View {
        content
            .navigationTitle("Collections")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add collection")
                }
            }
            .sheet(isPresented: $isPresentingCreate) {
                CreateCollectionSheet(onCreated: {
                    Task { await load() }
                })
            }
            .task { await load() }
            .refreshable { await load() }
            .confirmationDialog(
                pendingDeletion.map { "Delete \($0.name)?" } ?? "",
                ...
```

Leave every other modifier (`.refreshable`, `.confirmationDialog`, `.alert`) untouched.

- [ ] **Step 3: Regenerate and build**

```bash
cd apps/ios && npm run generate
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Run full test suite for regression**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -quiet 2>&1 | tail -30
```

Expected: `** TEST SUCCEEDED **`. Same four Collection\*\* / grouping suites as Task 1 — no test count regression.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift
git commit -m "$(cat <<'EOF'
feat(ios): wire create collection entry into list (#217)

+ toolbar button on CollectionsListView presents the new sheet;
onCreated reloads the list so the new row appears immediately.
Empty state unchanged, matching SoulsListView.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Manual QA in the simulator

No commit. Boot the app and walk the spec's acceptance criteria. Build correctness does not imply feature correctness — this step catches visual / flow regressions the tests can't.

- [ ] **Step 1: Run the app on a simulator**

Open `apps/ios/Pebbles.xcodeproj` in Xcode, select an iPhone 17 / iOS 26 simulator, and press Cmd+R to build and run.

- [ ] **Step 2: Walk the acceptance list from the spec**

- [ ] Log in with a real test account (or existing session). Navigate: Profile → Collections.
- [ ] `+` icon renders in the top-right of the Collections nav bar (primary action slot). VoiceOver label reads "Add collection" (optional check with accessibility inspector).
- [ ] Tap `+` → sheet appears with title "New collection", an empty name field, a segmented mode picker defaulting to `None`, and a Save button that is disabled.
- [ ] Type a name with leading/trailing whitespace only (e.g. "   ") → Save stays disabled.
- [ ] Type a real name ("QA test 1"), leave mode on None → Save enables → tap Save. Sheet dismisses. List reloads and "QA test 1" appears with no mode badge and "No pebbles" in the subtitle. Order: alphabetical (existing `order("name")` contract).
- [ ] Re-open `+`. Name "QA test 2", mode Stack → Save → list shows the row with the stack badge.
- [ ] Repeat for Pack and Track to cover the three rawValues.
- [ ] Airplane-mode the simulator (Device → Airplane Mode) → try to Save. The red error footnote appears with "Couldn't save the collection. Please try again." Sheet stays open. Turn airplane mode off → retry succeeds.
- [ ] Verify created rows survive a full app restart (Cmd+Shift+H twice, swipe up to kill, relaunch) → they fetch from remote and still render. Confirms INSERT actually persisted.
- [ ] Cmd+. while the sheet is presented → sheet dismisses with no state leaked (re-opening the sheet shows an empty name field).
- [ ] Clean up the QA rows via swipe-to-delete so the list doesn't get polluted.

- [ ] **Step 3: Console log sanity**

In the Xcode console (or `Console.app` filtered to subsystem `app.pbbls.ios`), confirm no `profile.collections` errors were logged during successful creates. Airplane-mode failures should show `create collection failed: ...` entries — expected.

No commit at the end of this task.

---

## Task 4: Arkaik bundle update

The `V-collection-create` node and most edges already exist (`e-F-manage-collections-V-collection-create` compose edge, `e-V-collection-create-API-create-collection` calls edge, and the `F-manage-collections` flow already references the view in its playlist). Two surgical changes: flip the status and add the missing display edge.

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Flip `V-collection-create` status**

In `docs/arkaik/bundle.json`, find the `V-collection-create` node (currently around lines 184–191):

```json
    {
      "id": "V-collection-create",
      "project_id": "pebbles",
      "species": "view",
      "title": "Create Collection",
      "description": "Create a new collection to group pebbles.",
      "status": "idea",
      "platforms": ["web", "ios", "android"]
    },
```

Change `"status": "idea"` to `"status": "development"`. Leave every other field untouched.

Also refresh the top-level `project.updated_at` timestamp to the current ISO-8601 date (matches how PR #269 refreshed it):

```json
    "updated_at": "2026-04-17T00:00:00Z"
```

- [ ] **Step 2: Add the display edge**

In the same file, find the block of `e-V-collection-*-DM-collection` display edges (around lines 1189–1192, right after the composition edges):

```json
    { "id": "e-V-collections-list-DM-collection", "project_id": "pebbles", "source_id": "V-collections-list", "target_id": "DM-collection", "edge_type": "displays" },
    { "id": "e-V-collection-detail-DM-collection", "project_id": "pebbles", "source_id": "V-collection-detail", "target_id": "DM-collection", "edge_type": "displays" },
    { "id": "e-V-collection-detail-DM-pebble", "project_id": "pebbles", "source_id": "V-collection-detail", "target_id": "DM-pebble", "edge_type": "displays" },
    { "id": "e-V-collection-edit-DM-collection", "project_id": "pebbles", "source_id": "V-collection-edit", "target_id": "DM-collection", "edge_type": "displays" },
```

Insert the new edge immediately after the `V-collection-edit-DM-collection` line:

```json
    { "id": "e-V-collection-create-DM-collection", "project_id": "pebbles", "source_id": "V-collection-create", "target_id": "DM-collection", "edge_type": "displays" },
```

Keep the trailing comma — the surrounding array continues. No other edges need changes.

- [ ] **Step 3: Validate the bundle**

```bash
node .claude/skills/arkaik/scripts/validate-bundle.js docs/arkaik/bundle.json
```

Expected: exit code 0, no validation errors. If the script reports dangling references, re-check the edge's `source_id` / `target_id` spellings against the node list.

- [ ] **Step 4: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "$(cat <<'EOF'
docs(arkaik): register collection create as development (#217)

Flip V-collection-create to development now that iOS ships the sheet,
and add the missing display edge to DM-collection. Other edges were
already in place from earlier scaffolding.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Open the PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/217-ios-create-collection
```

- [ ] **Step 2: Run the workspace lint/build once more from the repo root (sanity, not iOS-specific)**

```bash
cd /Users/alexis/code/pbbls && npm run lint && npm run build
```

Expected: both pass. (iOS changes don't touch the web workspace, but the repo-level PR checklist calls for it.)

- [ ] **Step 3: Create the PR**

```bash
gh pr create \
  --title "feat(ios): create collection sheet (#217)" \
  --body "$(cat <<'EOF'
Resolves #217.

## Summary
- New `+` toolbar entry on `CollectionsListView` opens a new `CreateCollectionSheet`.
- `CreateCollectionSheet` — name field + segmented mode picker (None/Stack/Pack/Track), direct INSERT to `public.collections` under RLS, `onCreated` reloads the list.
- New `CollectionInsertPayload: Encodable` — snake-case keys, explicit `user_id`, custom encode forces JSON null for unset `mode`.
- New Swift Testing suite `CollectionInsertPayload encoding` covering keys, rawValue fidelity, and JSON-null-vs-absent for nil mode.
- Arkaik: `V-collection-create` flipped to `development`; added `e-V-collection-create-DM-collection` display edge.

## Out of scope (deferred)
- `description` field — not in the `collections` schema. Dropped, not deferred.
- `visibility` field — not in the schema; already deferred in #269.
- Multi-select pebble management — belongs on `CollectionDetailView`; tracked in a follow-up issue (re-scopes the "add pebbles" half of #217).

## Test plan
- [x] `xcodebuild build` + `xcodebuild test` pass on iPhone 17 / iOS 26.
- [x] Profile → Collections → `+` opens the sheet.
- [x] Save disabled on empty/whitespace name; enabled otherwise.
- [x] Creating with None mode persists `mode IS NULL`; creating with Stack/Pack/Track persists the rawValue and shows the badge on the row.
- [x] Airplane-mode save surfaces the red footnote; sheet stays open; retry after reconnect succeeds.
- [x] Rows survive an app relaunch (INSERT actually persisted).
- [x] No regressions to list / detail / edit / delete flows.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" \
  --label feat --label ios --label ui \
  --milestone "M21 · Souls & collections"
```

Labels and milestone inherit from #217 (`feat`, `ios`) plus `ui` for the new sheet — matches the pattern used by PR #269. If the user prefers a different scope label, adjust before running.

Return the PR URL.

---

## Self-Review

Run this pass against the spec before declaring the plan done.

**1. Spec coverage:**

| Spec requirement | Covered by |
|---|---|
| `+` toolbar button on `CollectionsListView` mirroring `SoulsListView` | Task 2, Steps 1–2 |
| `CreateCollectionSheet` — name + mode picker, direct INSERT, dismiss+reload | Task 1, Step 3 |
| `CollectionInsertPayload` with JSON null on nil mode | Task 1, Step 3 (struct) and Step 1 (test for null) |
| Unit tests for payload encoding | Task 1, Step 1 |
| No description / visibility fields | N/A — explicitly absent from the implementation |
| Empty-state unchanged | Task 2 intentionally leaves `content` alone |
| Error handling + `os.Logger` discipline | Task 1, Step 3 (save() error path) |
| Arkaik update: `V-collection-create` status + display edge | Task 4 |
| `xcodebuild build` + `xcodebuild test` green | Task 1 Step 5, Task 2 Step 3–4 |
| Simulator verification of acceptance criteria | Task 3 |

No gaps.

**2. Placeholder scan:** No "TBD" / "TODO" / "implement later". Every step has exact file paths and either full code blocks or exact modifier diffs. No "similar to Task N" shortcuts — Task 2 spells out the insertion point in full.

**3. Type consistency:**
- `CollectionInsertPayload(userId:name:mode:)` — used identically in the sheet's `save()` (Task 1 Step 3) and in the tests (Task 1 Step 1). ✓
- `CollectionMode` and its cases `.stack / .pack / .track` — matches the enum already shipped in `Collection.swift`. ✓
- `onCreated: () -> Void` callback shape — referenced identically in the sheet declaration (Task 1) and in the `.sheet { ... }` call site (Task 2). ✓
- `supabase.session?.user.id` — matches `CreateSoulSheet`'s usage. ✓
