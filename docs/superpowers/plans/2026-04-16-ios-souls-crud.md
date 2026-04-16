# iOS Souls CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring soul Create / Read / Update / Delete parity to iOS — create sheet, pushed detail view listing pebbles per soul, edit sheet, and swipe-to-delete on the list — resolving issue #215.

**Architecture:** Native SwiftUI primitives, iOS 17+, `@Environment(SupabaseService.self)` for data access, direct `.from("souls")` client calls (RLS-scoped writes, no RPC), split `CreateSoulSheet` / `EditSoulSheet` mirroring the existing `Create/EditPebbleSheet` pattern, `SoulDetailView` pushed from the list via `NavigationLink(value:)`.

**Tech Stack:** Swift 5.9, SwiftUI, Supabase Swift SDK, xcodegen, `os.Logger`. No automated tests (per `apps/ios/CLAUDE.md` V1 policy) — verification is `xcodebuild` + the manual smoke-test checklist defined in the spec.

**Reference:** spec at `docs/superpowers/specs/2026-04-16-ios-souls-crud-design.md`.

---

## Conventions & Shared Context

Read these once before starting; every task depends on them.

**Branch:** `feat/215-ios-souls-crud` (already created).

**xcodegen:** `apps/ios/project.yml` globs the `Pebbles` folder via `sources: - path: Pebbles`, so new `.swift` files are picked up automatically — but you still need to regenerate the `.xcodeproj` after adding files:

```bash
cd apps/ios && npm run generate
```

**Build verification command (run from repo root):**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`. If this ever fails, stop the task and fix before continuing.

**Logger convention:** Every write path has a `try/catch`. On failure: `logger.error("<operation> failed: \(error.localizedDescription, privacy: .private)")` and surface a user-facing string in the view's error state. Never an empty catch.

**Soul model:** already defined at `apps/ios/Pebbles/Features/Path/Models/Soul.swift` as `struct Soul: Identifiable, Decodable, Hashable { let id: UUID; let name: String }`. Do **not** modify it.

**Pebble model:** already defined at `apps/ios/Pebbles/Features/Path/Models/Pebble.swift` with `id, name, happenedAt` and snake_case CodingKeys. Do **not** modify it.

**UUID: Identifiable** is already registered via `apps/ios/Pebbles/Services/UUID+Identifiable.swift`, which is why `.sheet(item: $someUUID)` compiles.

**Current user id:** resolve as `supabase.session?.user.id` — `SupabaseService.session: Session?` is kept up to date by `authStateChanges`. Pattern `guard let userId = supabase.session?.user.id else { ... }`.

**Supabase write shapes:** no existing iOS code uses `.insert(...)` or `.update(...)` directly — all writes go through RPC / edge functions today. The plan introduces these. Use a private `Encodable` payload struct per operation (the pattern already used for RPC payloads). This avoids untyped dict footguns.

---

## File Structure

| Path | Role |
|------|------|
| `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` | MODIFIED — toolbar `+` button, `NavigationLink(value:)` rows + destination, swipe-to-delete with confirmation, delete-error alert. |
| `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` | NEW — single-field form sheet, `INSERT` into `souls`. |
| `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift` | NEW — single-field form sheet, `UPDATE` on `souls`. |
| `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` | NEW — pushed view; loads pebbles filtered through `pebble_souls`; Edit toolbar; tap pebble → existing `EditPebbleSheet`. |
| `docs/arkaik/bundle.json` | MODIFIED — add iOS-specific nodes & edges for Create/Edit/Detail surfaces. |

Each Swift file has exactly one exported type (the view struct) plus at most one private `Encodable` payload struct co-located in the same file when required by that view.

---

## Task 1: `CreateSoulSheet` + "+" toolbar on `SoulsListView`

Adds the create flow end-to-end. The new sheet fully works and you can create a soul that appears in the list.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

- [ ] **Step 1: Create `CreateSoulSheet.swift`**

Write the complete file:

```swift
import SwiftUI
import os

/// Sheet for creating a new soul. One text field, save/cancel toolbar.
/// INSERT goes directly to `public.souls` — RLS scopes to the current user.
struct CreateSoulSheet: View {
    let onCreated: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(false)
                }
                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("New soul")
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
            logger.error("create soul: no session")
            saveError = "You're signed out. Please sign in again."
            return
        }
        isSaving = true
        saveError = nil
        do {
            let payload = SoulInsertPayload(userId: userId, name: trimmedName)
            try await supabase.client
                .from("souls")
                .insert(payload)
                .execute()
            onCreated()
            dismiss()
        } catch {
            logger.error("create soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save the soul. Please try again."
            isSaving = false
        }
    }
}

/// Matches the `souls` row shape required for insert.
/// `user_id` is explicit because the RLS policy still requires it in the row
/// (the policy's `with check` compares it to `auth.uid()`).
private struct SoulInsertPayload: Encodable {
    let userId: UUID
    let name: String

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
    }
}

#Preview {
    CreateSoulSheet(onCreated: {})
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Modify `SoulsListView.swift` — add the toolbar `+` button and the sheet presentation**

Replace the full contents of `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` with:

```swift
import SwiftUI
import os

struct SoulsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [Soul] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    var body: some View {
        content
            .navigationTitle("Souls")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add soul")
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingCreate) {
                CreateSoulSheet(onCreated: {
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
                "Couldn't load souls",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No souls yet",
                systemImage: "person.2",
                description: Text("People and beings you tag on your pebbles will appear here.")
            )
        } else {
            List(items) { soul in
                Text(soul.name)
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let result: [Soul] = try await supabase.client
                .from("souls")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        SoulsListView()
            .environment(SupabaseService())
    }
}
```

(Rows are still plain `Text(soul.name)` — the `NavigationLink` upgrade happens in Task 2.)

- [ ] **Step 3: Regenerate the Xcode project and build**

Run:

```bash
cd apps/ios && npm run generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`. If the build fails, fix and re-run before moving on.

- [ ] **Step 4: Smoke-test create flow (simulator, manual)**

Launch the app in the simulator, sign in, navigate `Profile → Souls`. Tap `+`, enter "Alice", tap `Save`. The sheet dismisses and "Alice" appears in the list.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift \
        apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): create a soul via sheet on souls list (#215)"
```

---

## Task 2: `SoulDetailView` + `NavigationLink(value:)` rows

Adds the pushed detail view listing pebbles linked to a soul, with pebble rows opening the existing `EditPebbleSheet`. No edit-soul / delete-soul yet — those land in Tasks 3 and 4.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

- [ ] **Step 1: Create the `Views` folder and scaffold `SoulDetailView.swift`**

Write the complete file:

```swift
import SwiftUI
import os

/// Pushed detail view for a single soul.
///
/// - Shows the pebbles linked to this soul (filtered via `pebble_souls` inner join).
/// - Header = `.navigationTitle(soul.name)`; header stays in sync with `soul` local state
///   so renames reflect without popping the stack (local state is updated in Task 3).
/// - Tapping a pebble opens the existing `EditPebbleSheet`, matching `PathView` UX.
struct SoulDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var soul: Soul
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedPebbleId: UUID?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.soul.detail")

    init(soul: Soul, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._soul = State(initialValue: soul)
    }

    var body: some View {
        content
            .navigationTitle(soul.name)
            .navigationBarTitleDisplayMode(.inline)
            .task { await load() }
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
                description: Text("Pebbles you tag with this soul will appear here.")
            )
        } else {
            List(pebbles) { pebble in
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

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            // `pebble_souls!inner(soul_id)` forces an inner join so the `.eq`
            // on the join column filters the parent rows. The extra column is
            // tolerated by `Pebble`'s default `Decodable` (extra keys ignored).
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, pebble_souls!inner(soul_id)")
                .eq("pebble_souls.soul_id", value: soul.id)
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
        } catch {
            logger.error("soul pebbles fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        SoulDetailView(soul: Soul(id: UUID(), name: "Preview Soul"), onChanged: {})
            .environment(SupabaseService())
    }
}
```

- [ ] **Step 2: Upgrade `SoulsListView` rows to `NavigationLink(value:)` + add the destination**

In `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`, replace the `List(items) { soul in Text(soul.name) }` block with the `NavigationLink(value:)` form, and attach `.navigationDestination(for: Soul.self)` at the same level as the other view modifiers. The full updated `body` is:

```swift
var body: some View {
    content
        .navigationTitle("Souls")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    isPresentingCreate = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Add soul")
            }
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingCreate) {
            CreateSoulSheet(onCreated: {
                Task { await load() }
            })
        }
        .navigationDestination(for: Soul.self) { soul in
            SoulDetailView(soul: soul, onChanged: {
                Task { await load() }
            })
        }
}
```

And in the `content` view's non-empty branch, replace:

```swift
List(items) { soul in
    Text(soul.name)
}
```

with:

```swift
List(items) { soul in
    NavigationLink(value: soul) {
        Text(soul.name)
    }
}
```

- [ ] **Step 3: Regenerate the Xcode project and build**

```bash
cd apps/ios && npm run generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Smoke-test the push + pebble list**

In the simulator: `Profile → Souls` → tap a soul → detail view opens with `<` back.
- If the soul has no pebbles: empty state `"No pebbles yet"` appears.
- If the soul has pebbles (create one tagged to the soul from `PathView` first, if needed): rows show pebble name + date, newest first. Tap a row → `EditPebbleSheet` opens as it does from `PathView`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift \
        apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): view pebbles linked to a soul via pushed detail (#215)"
```

---

## Task 3: `EditSoulSheet` + Edit toolbar on `SoulDetailView`

Adds rename. The detail view gains an `Edit` toolbar button that opens a sheet prefilled with the soul's name; saving updates the soul, refreshes the detail header in place, and notifies the list to refetch on pop.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`

- [ ] **Step 1: Create `EditSoulSheet.swift`**

Write the complete file:

```swift
import SwiftUI
import os

/// Sheet for renaming an existing soul. One text field, save/cancel toolbar.
/// UPDATE goes directly to `public.souls` — RLS scopes to the owner.
struct EditSoulSheet: View {
    let soul: Soul
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    init(soul: Soul, onSaved: @escaping () -> Void) {
        self.soul = soul
        self.onSaved = onSaved
        self._name = State(initialValue: soul.name)
    }

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        !trimmedName.isEmpty && trimmedName != soul.name
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(false)
                }
                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Edit soul")
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
            let payload = SoulUpdatePayload(name: trimmedName)
            try await supabase.client
                .from("souls")
                .update(payload)
                .eq("id", value: soul.id)
                .execute()
            onSaved()
            dismiss()
        } catch {
            logger.error("update soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
}

private struct SoulUpdatePayload: Encodable {
    let name: String
}

#Preview {
    EditSoulSheet(soul: Soul(id: UUID(), name: "Preview"), onSaved: {})
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Wire Edit into `SoulDetailView`**

In `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`, add:

1. `@State private var isPresentingEdit = false` alongside the other state declarations.
2. A toolbar modifier next to `.task`. Place the toolbar above `.task`:

```swift
.toolbar {
    ToolbarItem(placement: .primaryAction) {
        Button("Edit") {
            isPresentingEdit = true
        }
    }
}
.task { await load() }
.sheet(isPresented: $isPresentingEdit) {
    EditSoulSheet(soul: soul, onSaved: {
        Task { await reloadSoul() }
        onChanged()
    })
}
.sheet(item: $selectedPebbleId) { id in
    EditPebbleSheet(pebbleId: id, onSaved: {
        Task { await load() }
    })
}
```

3. Add the `reloadSoul()` method inside `SoulDetailView`:

```swift
private func reloadSoul() async {
    do {
        let refreshed: Soul = try await supabase.client
            .from("souls")
            .select("id, name")
            .eq("id", value: soul.id)
            .single()
            .execute()
            .value
        self.soul = refreshed
    } catch {
        logger.error("soul reload failed: \(error.localizedDescription, privacy: .private)")
        // Leave stale state; next navigation will refresh.
    }
}
```

For clarity, the complete updated `body` of `SoulDetailView` should now be:

```swift
var body: some View {
    content
        .navigationTitle(soul.name)
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
            EditSoulSheet(soul: soul, onSaved: {
                Task { await reloadSoul() }
                onChanged()
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            EditPebbleSheet(pebbleId: id, onSaved: {
                Task { await load() }
            })
        }
}
```

- [ ] **Step 3: Regenerate and build**

```bash
cd apps/ios && npm run generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Smoke-test rename**

In the simulator: `Souls` → tap "Alice" → tap `Edit` → change to "Alice B." → `Save`. Sheet dismisses. Detail title is now "Alice B." Pop back to the list. Row shows "Alice B.".

Also test no-op prevention: open Edit again, leave name unchanged → `Save` is disabled. Clear the field entirely → `Save` is disabled.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift \
        apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): rename a soul via sheet on soul detail (#215)"
```

---

## Task 4: Swipe-to-delete on the souls list

Adds row swipe → Delete with a `confirmationDialog` guard; a failed delete surfaces an `.alert` (not an inline error view so the list stays usable).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

- [ ] **Step 1: Add delete state and the swipe action**

In `SoulsListView.swift`, add these `@State` properties alongside the existing ones:

```swift
@State private var pendingDeletion: Soul?
@State private var deleteError: String?
```

Replace the non-empty `List` block in `content` with:

```swift
List {
    ForEach(items) { soul in
        NavigationLink(value: soul) {
            Text(soul.name)
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                pendingDeletion = soul
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }
}
```

Note the switch from `List(items) { ... }` to `List { ForEach { ... } }` — `.swipeActions` attaches to the row, and using `ForEach` inside `List` is the idiomatic shape for per-row modifiers.

- [ ] **Step 2: Add the confirmation dialog and error alert**

In `body`, add these modifiers after the existing `.sheet(isPresented: $isPresentingCreate) { ... }`:

```swift
.confirmationDialog(
    pendingDeletion.map { "Delete \($0.name)?" } ?? "",
    isPresented: Binding(
        get: { pendingDeletion != nil },
        set: { if !$0 { pendingDeletion = nil } }
    ),
    titleVisibility: .visible,
    presenting: pendingDeletion
) { soul in
    Button("Delete", role: .destructive) {
        Task { await delete(soul) }
    }
    Button("Cancel", role: .cancel) {
        pendingDeletion = nil
    }
} message: { _ in
    Text("Linked pebbles stay; only the soul and its links are removed.")
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
```

- [ ] **Step 3: Add the `delete(_:)` method**

Inside `SoulsListView`, alongside `load()`:

```swift
private func delete(_ soul: Soul) async {
    pendingDeletion = nil
    do {
        try await supabase.client
            .from("souls")
            .delete()
            .eq("id", value: soul.id)
            .execute()
        await load()
    } catch {
        logger.error("delete soul failed: \(error.localizedDescription, privacy: .private)")
        deleteError = "Something went wrong. Please try again."
    }
}
```

For reference, the complete updated `SoulsListView.swift` should look like:

```swift
import SwiftUI
import os

struct SoulsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [Soul] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var pendingDeletion: Soul?
    @State private var deleteError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    var body: some View {
        content
            .navigationTitle("Souls")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add soul")
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingCreate) {
                CreateSoulSheet(onCreated: {
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
            ) { soul in
                Button("Delete", role: .destructive) {
                    Task { await delete(soul) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("Linked pebbles stay; only the soul and its links are removed.")
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
            .navigationDestination(for: Soul.self) { soul in
                SoulDetailView(soul: soul, onChanged: {
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
                "Couldn't load souls",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No souls yet",
                systemImage: "person.2",
                description: Text("People and beings you tag on your pebbles will appear here.")
            )
        } else {
            List {
                ForEach(items) { soul in
                    NavigationLink(value: soul) {
                        Text(soul.name)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            pendingDeletion = soul
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
            let result: [Soul] = try await supabase.client
                .from("souls")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func delete(_ soul: Soul) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .from("souls")
                .delete()
                .eq("id", value: soul.id)
                .execute()
            await load()
        } catch {
            logger.error("delete soul failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

#Preview {
    NavigationStack {
        SoulsListView()
            .environment(SupabaseService())
    }
}
```

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`. (No new files, so `xcodegen` isn't needed.)

- [ ] **Step 5: Smoke-test delete**

In the simulator: `Souls` → swipe left on a row with linked pebbles → tap `Delete` → confirmation dialog shows → tap `Delete`. Row disappears. Open `PathView` — previously-linked pebbles still exist; their soul tag is gone.

Also test Cancel: swipe → Delete → Cancel. Row remains. No alert.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift
git commit -m "feat(ios): delete a soul via swipe with confirmation (#215)"
```

---

## Task 5: Update the Arkaik product architecture map

The spec calls for the bundle to register the new iOS surfaces. Use the `arkaik` skill — it knows the schema and runs a validator.

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Invoke the `arkaik` skill**

Ask Claude (or run the skill directly) to apply this change:

> Add iOS-specific views for soul CRUD to the Arkaik bundle:
>
> - `V-ios-souls-create` — species `view`, title `"Create Soul (iOS sheet)"`, description `"iOS sheet with a single name field to create a soul."`, platforms `["ios"]`, status `"live"`.
> - `V-ios-souls-detail` — species `view`, title `"Soul Detail (iOS)"`, description `"iOS pushed detail view showing pebbles linked to a soul, with Edit in the toolbar."`, platforms `["ios"]`, status `"live"`.
> - `V-ios-souls-edit` — species `view`, title `"Edit Soul (iOS sheet)"`, description `"iOS sheet prefilled with the soul's name to rename it."`, platforms `["ios"]`, status `"live"`.
>
> Edges (flows from existing `V-souls-list`):
>
> - `V-souls-list` → `V-ios-souls-create` (trigger: tap `+` toolbar, iOS only)
> - `V-souls-list` → `V-ios-souls-detail` (trigger: tap row, iOS only)
> - `V-ios-souls-detail` → `V-ios-souls-edit` (trigger: tap Edit, iOS only)
>
> Update `F-manage-souls` playlist to reference the new iOS-specific views where appropriate (after `V-souls-list`). Keep existing web/android entries as they are.
>
> Run the validator before writing.

The arkaik skill handles schema details and validation. If it reports a conflict (e.g., an existing `V-souls-create` node that already covers this), update in place instead of adding a duplicate — follow the skill's guidance.

- [ ] **Step 2: Verify the bundle validates**

The `arkaik` skill runs its own validator. If it reports success, the bundle is good. If it reports errors, fix them and re-run.

- [ ] **Step 3: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(arkaik): register iOS soul CRUD surfaces (#215)"
```

---

## Task 6: Final verification & PR

- [ ] **Step 1: Full build from a clean state**

```bash
cd apps/ios && npm run generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 2: Run the full smoke-test checklist from the spec**

Walk through each item in the simulator and confirm behaviour:

1. Empty souls list → tap `+` → create "Alice" → row appears.
2. Tap "Alice" → detail view → empty pebbles state.
3. Tap `Edit` → rename to "Alice B." → detail title updates immediately.
4. Pop back → list row shows "Alice B."
5. From `PathView`, create a pebble tagged with "Alice B." → return to the soul detail → pebble appears, ordered newest first.
6. Tap the pebble row → `EditPebbleSheet` opens.
7. Swipe the "Alice B." row → `Delete` → confirm → row gone, `PathView` pebble still present, its soul tag is empty.

If any step fails, fix and re-run before opening the PR.

- [ ] **Step 3: Verify issue labels for the PR**

Issue #215 has labels `feat` + `ios`. The PR should inherit both, with milestone `M21 · Souls & collections`. Confirm with the user before creating the PR (per `CLAUDE.md` PR workflow).

- [ ] **Step 4: Push the branch and open the PR**

```bash
git push -u origin feat/215-ios-souls-crud
```

Then create the PR with `gh pr create` using title `feat(ios): create, edit, view & delete souls (#215)` and a body that:
- Starts with `Resolves #215`.
- Lists the key files changed (see File Structure table above).
- Includes the manual smoke-test checklist as the "Test plan" section.
- Applies labels `feat`, `ios` and milestone `M21 · Souls & collections`.

Do **not** push or open the PR without explicit user confirmation on labels/milestone.

---

## Self-review (completed by plan author)

**Spec coverage:**
- Create (sheet) → Task 1. ✓
- Pushed soul detail view listing pebbles → Task 2. ✓
- Empty pebble state → Task 2 Step 1 (`ContentUnavailableView`). ✓
- Tap pebble → `EditPebbleSheet` → Task 2 Step 1. ✓
- Edit (sheet) with rename → Task 3. ✓
- In-place detail update after rename → Task 3 (`reloadSoul()`). ✓
- Swipe-to-delete with confirmation → Task 4. ✓
- `ON DELETE CASCADE` behaviour relied on, not re-implemented → schema already guarantees it; Task 4 Step 5 smoke-tests it. ✓
- No model changes → enforced (Soul / Pebble untouched). ✓
- Arkaik update → Task 5. ✓

**Placeholder scan:** none. Every step has either runnable code or a specific command + expected output.

**Type consistency:** `SoulInsertPayload` and `SoulUpdatePayload` are separate private structs with snake_case CodingKeys where needed. `Soul` / `Pebble` signatures referenced throughout match their source files. The `SoulDetailView` init parameter is `soul:` externally and stored to `initialSoul` + `_soul` internally — called `SoulDetailView(soul: ..., onChanged: ...)` at both call sites (Task 2 Step 2 and the `#Preview`).

No issues found.
