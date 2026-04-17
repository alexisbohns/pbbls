# iOS Create collection

**Issue:** #217 · **Milestone:** M21 · Souls & collections · **Platform:** iOS (SwiftUI, iOS 17+)

## Context

PR #269 landed list, detail, edit, and swipe-delete for collections on iOS. What's still missing is the entry point: users can't create a collection from the app. This spec adds the `+` action on `CollectionsListView` and the `CreateCollectionSheet` behind it.

The issue description additionally mentions an "optional description," a "visibility setting," and multi-select pebble picking. The `collections` table has no `description` or `visibility` column, and PR #269 already deferred visibility on that basis. Multi-select pebble management is a distinct surface that belongs on `CollectionDetailView`, not on the create path. All three are out of scope here — see below.

## Scope

**In scope**

- `+` toolbar button on `CollectionsListView`, mirroring `SoulsListView`.
- New `CreateCollectionSheet` — name + mode picker, INSERT to `public.collections`, dismiss + reload on success.
- `CollectionInsertPayload` with `mode` encoded as JSON `null` when unset (symmetric with `CollectionUpdatePayload`).
- Unit tests for payload encoding (Swift Testing).
- Arkaik bundle update: new `V-collection-create` screen node + edges.

**Out of scope (deferred)**

- `description` field — `collections` table has no such column. Dropped (not deferred) — no schema work planned.
- `visibility` field — no schema support; already deferred in #269. Dropped here too.
- Multi-select pebble management — out of scope for this PR. Tracked in a follow-up issue against `CollectionDetailView` (add/remove pebbles via direct INSERT/DELETE on `collection_pebbles` under RLS). This re-scopes the "add pebbles" half of #217.
- Navigating into the freshly-created collection after save (matches `CreateSoulSheet` — dismiss to list).

## Data model

No schema changes. `collections` has everything we need: `(id, user_id, name, mode, created_at, updated_at)` with `mode` constrained to `('stack', 'pack', 'track')` or null. RLS: `collections_insert` policy already requires `user_id = auth.uid()`, so direct INSERT works — no RPC needed.

## New file — `CreateCollectionSheet.swift`

Location: `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`.

Structure mirrors `CreateSoulSheet` (form + save/cancel toolbar + direct INSERT) and reuses the mode picker from `EditCollectionSheet`:

```swift
struct CreateCollectionSheet: View {
    let onCreated: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var mode: CollectionMode? = nil
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

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
                        Text(saveError).font(.footnote).foregroundStyle(.red)
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
                        Button("Save") { Task { await save() } }
                            .disabled(trimmedName.isEmpty)
                    }
                }
            }
        }
    }

    private func save() async { /* INSERT; onCreated(); dismiss() */ }
}
```

### Wire shape

```swift
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
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(userId, forKey: .userId)
        try c.encode(name, forKey: .name)
        try c.encode(mode, forKey: .mode) // forces JSON null when nil
    }
}
```

`user_id` is explicit (matches `SoulInsertPayload` — the RLS `with check` compares the column to `auth.uid()`, so PostgREST needs the value in the row).

### Save logic

1. Guard `!trimmedName.isEmpty`.
2. Guard `supabase.session?.user.id` — if missing, log and surface `"You're signed out. Please sign in again."`.
3. `isSaving = true; saveError = nil`.
4. `try await supabase.client.from("collections").insert(payload).execute()`.
5. On success: `onCreated()` → dismiss.
6. On failure: log with `os.Logger` (`category: "profile.collections"`), set `saveError = "Couldn't save the collection. Please try again."`, reset `isSaving`.

## `CollectionsListView` changes

Additive only:

- `@State private var isPresentingCreate = false`.
- `.toolbar { ToolbarItem(placement: .primaryAction) { Button { isPresentingCreate = true } label: { Image(systemName: "plus") } .accessibilityLabel("Add collection") } }`.
- `.sheet(isPresented: $isPresentingCreate) { CreateCollectionSheet(onCreated: { Task { await load() } }) }`.

Empty-state `ContentUnavailableView` stays as-is (no inline CTA — matches `SoulsListView`).

## Tests

New file: `apps/ios/PebblesTests/CollectionInsertPayloadEncodingTests.swift` (Swift Testing).

- Encoding a payload with `mode: .some(.stack)` produces `{"user_id":"…","name":"…","mode":"stack"}`.
- Encoding with `mode: nil` produces `{"user_id":"…","name":"…","mode":null}` (not missing — confirm the key is present with JSON null).
- Snake-case `user_id` key is emitted.

No UI tests. No changes to existing test files.

## Error handling & logging

- Every async failure logs via `os.Logger(subsystem: "app.pbbls.ios", category: "profile.collections")` — matches `EditCollectionSheet`.
- Errors surface in the sheet's red footnote section; the user can retry or cancel. No silent catches.
- No signed-in session → user-visible "signed out" message (same pattern as `CreateSoulSheet`).

## Arkaik map update

After implementation, use the `arkaik` skill to update `docs/arkaik/bundle.json`:

- Add `V-collection-create` screen node (status: `live`).
- Add composition edge from `V-collections-list` → `V-collection-create` (the list opens the sheet).
- Add display edge `V-collection-create` → `M-collection`.

Keep changes surgical — don't touch unrelated nodes.

## Acceptance

- Tapping `+` on `CollectionsListView` opens the create sheet.
- Save disabled while the trimmed name is empty; enabled otherwise.
- Saving with `mode = None` inserts a row with `mode IS NULL` (verified by decoding the new row back through the existing list fetch).
- Saving with any mode persists the chosen value and shows the mode badge on the list row.
- Cancel dismisses without writing.
- Network failure surfaces the red footnote; the sheet stays open and retry works.
- No-session case surfaces the signed-out message.
- `xcodebuild build` and `xcodebuild test` pass on iPhone 17 / iOS 26 (matches prior runs).
