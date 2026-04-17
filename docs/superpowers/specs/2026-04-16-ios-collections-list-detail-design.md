# iOS Collections — list and detail

**Issue:** #216 · **Milestone:** M21 · Souls & collections · **Platform:** iOS (SwiftUI, iOS 17+)

## Context

Collections group pebbles with intention — Stacks (goal), Packs (time-bound), Tracks (recurring). On iOS today, `CollectionsListView.swift` exists as a stub: it fetches `id, name` and renders a flat list with no navigation. This spec replaces that stub with a list, a detail view, an edit sheet, and a swipe-delete flow — mirroring the souls pattern shipped in #214/#215.

Creating collections is out of scope (tracked separately in #217).

## Scope

**In scope**

- `CollectionsListView` — list with name, mode badge, pebble count; navigation to detail; swipe-to-delete with confirmation.
- `CollectionDetailView` — month-grouped timeline of pebbles in the collection; tap a pebble opens the existing `EditPebbleSheet`; toolbar "Edit" opens the edit sheet.
- `EditCollectionSheet` — edits name and mode (Stack/Pack/Track/None).
- Matching unit tests (Swift Testing) for decoding, encoding, and the grouping helper.
- Arkaik map update for the new product surfaces.

**Out of scope (deferred)**

- Creating a collection and adding pebbles (#217).
- "Rise level preview" — no schema or code concept exists; deferred to a future issue.
- Visibility (secret/private/public) — `collections` table has no visibility column; deferred to a future issue + migration.
- Backporting a shared `PebbleTimelineView` to `PathView` / `SoulDetailView`.
- Pull-to-refresh on `SoulsListView`.

## Data model

**New model — `apps/ios/Pebbles/Features/Profile/Models/Collection.swift`**

```swift
enum CollectionMode: String, Decodable, CaseIterable, Hashable {
    case stack, pack, track
}

struct Collection: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let mode: CollectionMode?
    let pebbleCount: Int

    enum CodingKeys: String, CodingKey {
        case id, name, mode
        case pebbleCount = "pebble_count"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(UUID.self, forKey: .id)
        self.name = try c.decode(String.self, forKey: .name)
        self.mode = try c.decodeIfPresent(CollectionMode.self, forKey: .mode)

        // PostgREST nested count returns `[{ "count": N }]`.
        // Fall back to 0 when absent (e.g., detail-fetch of a single row
        // reuses this decoder without the aggregate).
        if let counts = try? c.decode([CountWrapper].self, forKey: .pebbleCount) {
            self.pebbleCount = counts.first?.count ?? 0
        } else {
            self.pebbleCount = 0
        }
    }

    private struct CountWrapper: Decodable { let count: Int }
}
```

**Retire** `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift` — no other references in the codebase. Grep confirmed before deleting.

## Queries

**List fetch** (`CollectionsListView`):

```swift
.from("collections")
  .select("id, name, mode, pebble_count:collection_pebbles(count)")
  .order("name")
```

PostgREST returns `pebble_count` as `[{ count: N }]`; the custom decoder above unwraps it to `Int`.

**Single-collection reload** (`CollectionDetailView.reloadCollection()` — after edit):

```swift
.from("collections")
  .select("id, name, mode, pebble_count:collection_pebbles(count)")
  .eq("id", value: collection.id)
  .single()
```

**Detail fetch — pebbles in a collection** (mirrors `SoulDetailView`):

```swift
.from("pebbles")
  .select("id, name, happened_at, collection_pebbles!inner(collection_id)")
  .eq("collection_pebbles.collection_id", value: collection.id)
  .order("happened_at", ascending: false)
```

**Update** (`EditCollectionSheet`):

```swift
.from("collections").update(payload).eq("id", value: collection.id)
```

where `payload = CollectionUpdatePayload(name: String, mode: String?)`. `mode == nil` writes `null`, which the DB `check (mode in ('stack','pack','track'))` constraint permits.

**Delete** (`CollectionsListView`):

```swift
.from("collections").delete().eq("id", value: collection.id)
```

`collection_pebbles.collection_id` has `on delete cascade`, so junction rows drop automatically. Pebble rows are untouched.

RLS on `collections` already enforces `user_id = auth.uid()` for select/insert/update/delete, so no explicit ownership check is needed on the client. Single-table writes, so no RPC required (per `AGENTS.md`).

## File plan

All new/changed files live under `apps/ios/Pebbles/Features/Profile/`, mirroring the souls layout:

```
Profile/
  Components/
    CollectionModeBadge.swift        NEW
  Lists/
    CollectionsListView.swift        REPLACED (stub → full)
  Models/
    Collection.swift                 NEW
  Sheets/
    EditCollectionSheet.swift        NEW
  Views/
    CollectionDetailView.swift       NEW
```

Files to delete:

```
Path/Models/PebbleCollection.swift   DELETED (unused)
```

Tests (new, under `apps/ios/PebblesTests/`):

```
CollectionDecodingTests.swift
CollectionUpdatePayloadEncodingTests.swift
GroupPebblesByMonthTests.swift
```

## `CollectionModeBadge`

New small view `Profile/Components/CollectionModeBadge.swift`.

- **Input:** `mode: CollectionMode?`.
- **Rendering:** a capsule with emoji + label. Uses iOS-native styling (not a port of the web's shadcn `Badge`).
- **Mapping:** `.stack` → `🎯 Stack`, `.pack` → `📦 Pack`, `.track` → `🔄 Track`.
- Returns `EmptyView()` when `mode == nil`.
- Accessibility: `.accessibilityLabel("Mode: Stack")` (etc.), emoji marked `decorative`.

## `CollectionsListView`

**States:** `isLoading`, `loadError`, empty, populated. Same four-state pattern as `SoulsListView`.

**Empty state:**
```swift
ContentUnavailableView(
    "No collections yet",
    systemImage: "square.stack.3d.up",
    description: Text("Your collections will appear here.")
)
```

**Error state:** `ContentUnavailableView("Couldn't load collections", systemImage: "exclamationmark.triangle", description: Text(loadError))`.

**Row composition** (`CollectionRow`, private to the file):

```
┌──────────────────────────────────────┐
│  Collection name                     │
│  🎯 Stack · 12 pebbles             > │
└──────────────────────────────────────┘
```

- Name: `Text(collection.name).font(.body)`.
- Sub-line: `HStack(spacing: 6) { CollectionModeBadge(mode:); Text("·"); Text(pebbleCountLabel) }`, `.font(.caption)`, `.foregroundStyle(.secondary)`.
- `pebbleCountLabel`: `"No pebbles"` / `"1 pebble"` / `"N pebbles"`.

**Tap:**
```swift
NavigationLink {
    CollectionDetailView(collection: c, onChanged: { Task { await load() } })
} label: { CollectionRow(collection: c) }
```

**Swipe-to-delete:** trailing edge, `allowsFullSwipe: false`, destructive button sets `pendingDeletion = collection`. Full delete flow specified below.

**Pull-to-refresh:** `.refreshable { await load() }`. This is a new pattern for iOS (souls doesn't have it). Not adding to souls retroactively here; can be done in a follow-up quality PR if the pattern reads well.

**Navigation title:** `"Collections"`, `.inline`.

**Logger category:** `"profile.collections"`.

## `CollectionDetailView`

Mirrors `SoulDetailView` structure and sheet patterns:

```swift
struct CollectionDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var collection: Collection
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingEdit = false

    init(collection: Collection, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._collection = State(initialValue: collection)
    }
}
```

**Header:** `.navigationTitle(collection.name)`, `.inline`. Title reflects local state so rename updates without popping the stack. Toolbar `Button("Edit") { isPresentingEdit = true }` in `.primaryAction`.

**Subheader** (first section of the list): `CollectionModeBadge(mode:)` + pebble count text. Souls has no subheader; we add one here because mode is part of a collection's identity.

**Month grouping helper** (private to the file):

```swift
private func groupByMonth(
    _ pebbles: [Pebble],
    calendar: Calendar = .current
) -> [(key: Date, value: [Pebble])] {
    let buckets = Dictionary(grouping: pebbles) { pebble -> Date in
        let comps = calendar.dateComponents([.year, .month], from: pebble.happenedAt)
        return calendar.date(from: comps) ?? pebble.happenedAt
    }
    return buckets.sorted { $0.key > $1.key }
}
```

Within a bucket, the input order is preserved. Since the query returns pebbles `order("happened_at", ascending: false)`, each bucket ends up descending by date.

**Month formatter** (file-scope, memoized):

```swift
private let monthFormatter: DateFormatter = {
    let f = DateFormatter()
    f.setLocalizedDateFormatFromTemplate("MMMM yyyy")
    return f
}()
```

**Rendering:**

```swift
List {
    Section {
        HStack {
            CollectionModeBadge(mode: collection.mode)
            Spacer()
            Text(pebbleCountLabel).foregroundStyle(.secondary)
        }
    }

    ForEach(groupedPebbles, id: \.key) { group in
        Section(header: Text(monthFormatter.string(from: group.key))) {
            ForEach(group.value) { pebble in
                Button {
                    selectedPebbleId = pebble.id
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(pebble.name).font(.body)
                        Text(pebble.happenedAt, style: .date)
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }
}
.listStyle(.insetGrouped)
```

**Empty state:**
```swift
ContentUnavailableView(
    "No pebbles yet",
    systemImage: "circle.grid.2x1",
    description: Text("Pebbles added to this collection will appear here.")
)
```

**Sheets:**

- `.sheet(item: $selectedPebbleId) { id in EditPebbleSheet(pebbleId: id, onSaved: { Task { await load() } }) }` — reuse the existing sheet; a pebble's name/date may have changed.
- `.sheet(isPresented: $isPresentingEdit) { EditCollectionSheet(collection: collection, onSaved: { Task { await reloadCollection() }; onChanged() }) }` — both refresh paths run independently (matches the souls sequencing from commit `9dfe326`).

**`reloadCollection()` error handling:** on failure, log and leave local state alone — the next navigation will refresh. Same pattern as `SoulDetailView.reloadSoul()`.

**Logger category:** `"profile.collection.detail"`.

## `EditCollectionSheet`

Mirrors `EditSoulSheet` with an added mode picker.

```swift
struct EditCollectionSheet: View {
    let collection: Collection
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var mode: CollectionMode?
    @State private var isSaving = false
    @State private var saveError: String?

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        guard !trimmedName.isEmpty else { return false }
        return trimmedName != collection.name || mode != collection.mode
    }
}
```

**Form layout:**

```
┌─────────────────────────────────────┐
│  Edit collection           [Cancel] │
│                              [Save] │
├─────────────────────────────────────┤
│  Name                               │
│  [ Text field                   ]   │
├─────────────────────────────────────┤
│  Mode                               │
│  ( None ) ( Stack ) ( Pack ) (Track)│
└─────────────────────────────────────┘
```

- **Name field:** `TextField("Name", text: $name).textInputAutocapitalization(.words).autocorrectionDisabled(false)`.
- **Mode picker:** segmented `Picker` with options `None` (→ `nil`), `Stack`, `Pack`, `Track`.
- **Error row:** conditional section with `Text(saveError).font(.footnote).foregroundStyle(.red)` when present.

**Payload:**

```swift
private struct CollectionUpdatePayload: Encodable {
    let name: String
    let mode: String?
}
```

**Save:**

```swift
private func save() async {
    guard canSave else { return }
    isSaving = true
    saveError = nil
    do {
        let payload = CollectionUpdatePayload(
            name: trimmedName,
            mode: mode?.rawValue
        )
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
```

## Delete flow

All UI lives on `CollectionsListView`; `CollectionDetailView` does not surface delete.

**State on `CollectionsListView`:**
```swift
@State private var pendingDeletion: Collection?
@State private var deleteError: String?
```

**Swipe action:**
```swift
.swipeActions(edge: .trailing, allowsFullSwipe: false) {
    Button(role: .destructive) {
        pendingDeletion = collection
    } label: {
        Label("Delete", systemImage: "trash")
    }
}
```

**Confirmation dialog:**
```swift
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
    Button("Cancel", role: .cancel) { pendingDeletion = nil }
} message: { _ in
    Text("Linked pebbles stay; only the collection and its links are removed.")
}
```

The message text intentionally parallels the souls dialog so users learn a single mental model: deleting a grouping never destroys its pebbles.

**Delete:**
```swift
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
```

**Error alert:** plain `.alert("Couldn't delete", isPresented: <binding to deleteError>)`. Same shape as souls.

## Error handling summary

Every async path logs via `os.Logger` and surfaces a user-facing state — no silent failures (matches the `CLAUDE.md` discipline).

| Path | Log | User-facing |
|------|-----|-------------|
| List fetch | `"collections fetch failed: ..."` | `ContentUnavailableView` error state |
| Detail pebbles fetch | `"collection pebbles fetch failed: ..."` | `ContentUnavailableView` error state |
| Reload collection (after edit) | `"collection reload failed: ..."` | None — stale state kept; next nav refreshes |
| Update | `"update collection failed: ..."` | Inline red row in sheet |
| Delete | `"delete collection failed: ..."` | `.alert("Couldn't delete", ...)` |

## Testing

Swift Testing only (`@Suite`, `@Test`, `#expect`). No XCTest, no UI tests.

**`CollectionDecodingTests`** — verify `Collection` decodes:
- PostgREST JSON with `collection_pebbles: [{ "count": 5 }]` → `pebbleCount == 5`.
- Empty `collection_pebbles: []` → `pebbleCount == 0`.
- Missing `collection_pebbles` key (single-row fetch) → `pebbleCount == 0`.
- Missing `mode` → `mode == nil`.
- Each of `"stack"`, `"pack"`, `"track"` decodes to the matching enum case.

**`CollectionUpdatePayloadEncodingTests`** — mirroring `PebbleUpdatePayloadEncodingTests`:
- `{ name: "X", mode: "stack" }` encodes as expected.
- `mode: nil` encodes as JSON `null` (not omitted) so the DB column is cleared.

**`GroupPebblesByMonthTests`** — pure function test on the grouping helper:
- Pebbles in the same calendar month group together.
- Outer array is ordered descending by month key.
- Within a group, input order is preserved.
- Month boundaries respect the injected `Calendar` (test with a fixed calendar to avoid machine-local drift).
- Empty input → empty output.

## Verification before marking complete

1. `xcodebuild -scheme Pebbles -destination 'generic/platform=iOS' build` — clean build, no new warnings.
2. `xcodebuild test -scheme Pebbles` — all Swift Testing suites pass.
3. Manual QA in the iOS simulator (iPhone 15, iOS 17):
   - Profile → Collections row shows count with mode badge.
   - Empty collection list state renders correctly.
   - Tap a collection → detail with month-grouped pebbles; headers read "April 2026", etc.
   - Tap a pebble → `EditPebbleSheet` opens; save dismisses and the list stays consistent.
   - Edit button → sheet with name field + mode segmented picker. Changing mode, save — detail header and list row both reflect the change.
   - Swipe-delete row → confirmation dialog → collection disappears; open Path and confirm the pebbles still exist.
   - Pull-to-refresh on the list works.
   - Error simulation (disconnect network): all four `ContentUnavailableView` / alert paths render human-readable text.
4. `npm run generate --workspace=@pbbls/ios` (xcodegen) if any added files don't appear in Xcode automatically.

## Arkaik map

Per the `arkaik` skill, adding new product surfaces requires updating `docs/arkaik/bundle.json`:

- New screen node for **iOS Collection Detail**.
- New sheet node for **iOS Edit Collection**.
- Edge: iOS Collections List → iOS Collection Detail (navigation).
- Edge: iOS Collection Detail → iOS Edit Collection (sheet).
- Edge: iOS Collection Detail → iOS Edit Pebble (sheet) — reuses existing node.
- Status stays `development` until shipped.

Run the bundle validation script before committing the map change.

## Open questions

None at spec time. The two defaults left implicit during brainstorming (pull-to-refresh on list, stacked name + sub-line row composition) are codified above.
