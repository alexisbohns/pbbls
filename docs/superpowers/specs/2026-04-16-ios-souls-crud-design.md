# iOS — Create, edit, view & delete souls

Resolves [#215](https://github.com/alexisbohns/pbbls/issues/215).

## Context

On iOS, souls are currently read-only: `SoulsListView` (pushed from `ProfileView → Lists → Souls`) shows a plain `List<Text>` of the user's souls. There is no way from the iOS client to create, rename, or delete a soul, and there is no drill-down from a soul to the pebbles linked to it.

The web already has full soul CRUD (`apps/web/app/souls/page.tsx` + `apps/web/app/souls/[id]/page.tsx`) using direct `supabase.from("souls")` client calls — no RPC, since souls are a single-table entity and AGENTS.md explicitly permits direct client calls for single-table, single-statement reads and writes.

## Goal

Bring soul CRUD parity to iOS using native SwiftUI primitives, following the patterns already established in the codebase (split Create/Edit sheets, `SupabaseService` via environment, `os.Logger`, `@Observable`, iOS 17 APIs only).

## Acceptance (from issue #215)

- From the souls list, tap `+` → sheet with a single "name" field → save creates the soul and refreshes the list.
- From the souls list, tap a row → pushed soul detail view showing the pebbles linked to that soul.
- Empty pebble state on the soul detail view is handled.
- From the soul detail view, tap a pebble → existing `EditPebbleSheet`.
- From the soul detail view, tap `Edit` → sheet with the name prefilled → save updates the soul and the detail view in place.
- From the souls list, swipe a row → `Delete` with confirmation → deletes the soul and refreshes the list. Linked `pebble_souls` join rows cascade; pebbles themselves are untouched.

"Use native SwiftUI primitives, no extra design, standard iOS UX."

## File layout

```
apps/ios/Pebbles/Features/Profile/
  Lists/
    SoulsListView.swift           ← MODIFIED
  Sheets/
    CreateSoulSheet.swift         ← NEW
    EditSoulSheet.swift           ← NEW
  Views/                          ← NEW folder
    SoulDetailView.swift          ← NEW
```

`SoulDetailView` is a pushed view, not a sheet, so the new `Views/` subfolder is the appropriate home. This keeps `Sheets/` strictly for sheet-presented views.

## Components

### `SoulsListView` (modified)

Keeps its current `load()` / loading / error / empty branches. Changes:

- `@State private var isPresentingCreate = false`
- `@State private var pendingDeletion: Soul?`
- `@State private var deleteError: String?` (separate from `loadError` so a failed delete shows an alert instead of replacing the list with an error view)
- Toolbar gains `ToolbarItem(placement: .primaryAction) { Button { isPresentingCreate = true } label: { Image(systemName: "plus") } }`.
- Rows become `NavigationLink(value: soul) { Text(soul.name) }` so taps push `SoulDetailView`. The enclosing view adds `.navigationDestination(for: Soul.self) { soul in SoulDetailView(soul: soul, onChanged: load) }`.
- Row swipe: `.swipeActions(edge: .trailing) { Button(role: .destructive) { pendingDeletion = soul } label: { Label("Delete", systemImage: "trash") } }`.
- `.confirmationDialog("Delete \(soul.name)?", isPresented: …, titleVisibility: .visible, presenting: pendingDeletion) { soul in Button("Delete", role: .destructive) { Task { await delete(soul) } } }`.
- `.sheet(isPresented: $isPresentingCreate) { CreateSoulSheet(onCreated: { Task { await load() } }) }`.
- `delete(_ soul:)` performs `from("souls").delete().eq("id", value: soul.id).execute()`. On failure: log and set `deleteError` (rendered via `.alert("Couldn't delete", isPresented: …)`); on success: reload.

### `CreateSoulSheet` (new)

Mirrors the shape of `CreatePebbleSheet` but trivially simple.

- Inputs: `let onCreated: () -> Void`.
- Environment: `SupabaseService`, `dismiss`.
- State: `name: String = ""`, `isSaving: Bool = false`, `saveError: String?`.
- Body: `NavigationStack { Form { TextField("Name", text: $name) } }` with `.navigationTitle("New soul")`, `.navigationBarTitleDisplayMode(.inline)`, Cancel/Save toolbar items. Save shows a `ProgressView` while `isSaving`. Save disabled when `name.trimmingCharacters(in: .whitespaces).isEmpty`.
- Save path:
  - Resolve user id from `supabase.client.auth.session.user.id` (already established pattern elsewhere). If the session is missing, surface a generic error and log — this is an invariant violation in practice because the souls list is gated behind auth.
  - `from("souls").insert(["user_id": userId.uuidString, "name": trimmed]).execute()`.
  - On success: call `onCreated()` then `dismiss()`.
  - On failure: `logger.error(...)` via `Logger(subsystem: "app.pbbls.ios", category: "profile.souls")`, set `saveError = "Couldn't save the soul. Please try again."`, clear `isSaving`.
- `saveError` rendered inline below the form (small secondary-colored text), matching how `PebbleFormView` surfaces errors.

### `EditSoulSheet` (new)

Same shape as `CreateSoulSheet`.

- Inputs: `let soul: Soul`, `let onSaved: () -> Void`.
- State: `name: String` prefilled to `soul.name`, plus the same `isSaving` / `saveError`.
- Navigation title: `"Edit soul"`.
- Save disabled when trimmed name is empty **or** equal to `soul.name` (no-op prevention; matches web `SoulDetailHeader`).
- Save path: `from("souls").update(["name": trimmed]).eq("id", value: soul.id).execute()`. On success: `onSaved()` then `dismiss()`. On failure: log + set `saveError`.

### `SoulDetailView` (new)

Pushed from `SoulsListView` via `NavigationLink(value:)`.

- Inputs: `let initialSoul: Soul`, `let onChanged: () -> Void`.
- State:
  - `@State private var soul: Soul` (seeded from `initialSoul` in `init`) — so a rename updates the header immediately without popping the nav stack.
  - `@State private var pebbles: [Pebble] = []`
  - `@State private var isLoading = true`, `@State private var loadError: String?`
  - `@State private var isPresentingEdit = false`
  - `@State private var selectedPebbleId: UUID?`
- Body:
  - `.navigationTitle(soul.name)`, `.navigationBarTitleDisplayMode(.inline)`.
  - `ToolbarItem(placement: .primaryAction) { Button("Edit") { isPresentingEdit = true } }`.
  - Content: loading → `ProgressView`. Error → `ContentUnavailableView("Couldn't load pebbles", …)`. Empty → `ContentUnavailableView("No pebbles yet", systemImage: "circle.grid.2x1", description: Text("Pebbles you tag with this soul will appear here."))`. Non-empty → `List` with one row per pebble (same `Text(pebble.name)` + `Text(pebble.happenedAt, style: .date)` shape as `PathView`), each a `Button` that sets `selectedPebbleId`.
  - `.sheet(isPresented: $isPresentingEdit) { EditSoulSheet(soul: soul, onSaved: { Task { await reloadSoul() }; onChanged() }) }`.
  - `.sheet(item: $selectedPebbleId) { id in EditPebbleSheet(pebbleId: id, onSaved: { Task { await load() } }) }` — matches `PathView` exactly.
  - `.task { await load() }`.
- `load()` fetches pebbles filtered through the join table:
  ```swift
  let rows: [Pebble] = try await supabase.client
      .from("pebbles")
      .select("id, name, happened_at, pebble_souls!inner(soul_id)")
      .eq("pebble_souls.soul_id", value: soul.id)
      .order("happened_at", ascending: false)
      .execute()
      .value
  ```
  The `!inner` hint forces PostgREST to use an inner join so the `.eq` on the join column filters the parent rows. The extra `pebble_souls(soul_id)` column is ignored when decoding into `Pebble` — the existing `Pebble` struct (`id, name, happenedAt`) accepts it because the decoder is tolerant of extra fields.
- `reloadSoul()` re-selects the single soul row so the header stays in sync after rename:
  ```swift
  let refreshed: Soul = try await supabase.client
      .from("souls")
      .select("id, name")
      .eq("id", value: soul.id)
      .single()
      .execute()
      .value
  self.soul = refreshed
  ```
  On failure: log, leave stale state in place (user still sees the old name, next navigation will refresh).

### Data layer

All writes go through direct client calls on `public.souls`:

| Operation | Call |
|-----------|------|
| Create    | `from("souls").insert(["user_id": uid, "name": name])` |
| Rename    | `from("souls").update(["name": name]).eq("id", value: id)` |
| Delete    | `from("souls").delete().eq("id", value: id)` |
| List      | `from("souls").select("id, name").order("name")` (already present) |
| Pebbles per soul | `from("pebbles").select("id, name, happened_at, pebble_souls!inner(soul_id)").eq("pebble_souls.soul_id", value: id).order("happened_at", ascending: false)` |

No RPC is introduced. Per AGENTS.md: direct client calls are the correct shape for single-table, single-statement reads/writes. RLS policies `souls_select/insert/update/delete` (migration `20260411000001_core_tables.sql` lines 175–184) scope each op to `user_id = auth.uid()`. Delete cascades `pebble_souls` rows via the `ON DELETE CASCADE` on the join FK (line 107). Pebbles themselves are untouched.

### Model changes

None. `Soul` already conforms to `Identifiable`, `Decodable`, `Hashable` — the three protocols needed for `NavigationLink(value:)`, `.sheet(item:)`, and the query decoders.

### Error handling

Every `catch` block:

1. Logs via `Logger(subsystem: "app.pbbls.ios", category: "profile.souls")` (or `"profile.soul.detail"` for `SoulDetailView`) at `.error` level.
2. Surfaces a user-facing string in `loadError` / `saveError`.
3. Clears `isSaving` / `isLoading` so the UI recovers.

No empty catches. This mirrors the web-side discipline and the existing iOS pattern in `CreatePebbleSheet` / `EditPebbleSheet`.

## Edge cases

- **Delete a soul with linked pebbles** — `pebble_souls` cascades; pebbles survive. No client pre-check.
- **Network failure on save** — error rendered inline, sheet stays open, Save button re-enabled. User can retry or Cancel.
- **Whitespace-only / empty name** — Save disabled. Matches web.
- **Rename to the existing name** — Save disabled (no-op prevention).
- **Duplicate names across different souls** — allowed. No uniqueness constraint in schema; web permits it too.
- **Soul deleted from another device between list-load and tap** — `SoulDetailView.task` returns empty pebbles; rename/delete will hit RLS and no-op. Acceptable for V1 (no defensive refetch).
- **User pops the detail view mid-rename** — sheet lifecycle is tied to the detail view; it dismisses with the view. No orphan state.

## Out of scope

- Soul avatars, initials, or emoji.
- Search, filter, or reorder on the souls list.
- Bulk actions.
- Web-side changes of any kind.

## Testing

Per `apps/ios/CLAUDE.md`, no tests in V1. The view types take plain inputs + callbacks, so they are test-ready when the testing bar is revisited.

Manual smoke-test checklist:

1. Empty souls list → tap `+` → create "Alice" → row appears.
2. Tap "Alice" → detail view → empty pebbles state.
3. Tap `Edit` → rename to "Alice B." → detail title updates immediately.
4. Pop back → list row shows "Alice B."
5. From `PathView`, create a pebble tagged with "Alice B." → return to the soul detail → pebble appears, ordered newest first.
6. Tap the pebble row → `EditPebbleSheet` opens.
7. Swipe the "Alice B." row → `Delete` → confirm → row gone, `PathView` pebble still present, its soul chip is empty.

## Product architecture map (Arkaik)

The soul CRUD flow adds three user-visible surfaces to the iOS app:

- `ios/souls/create` (sheet)
- `ios/souls/detail` (pushed view)
- `ios/souls/edit` (sheet)

The implementation plan will include an `arkaik` skill step to register these nodes and their edges from `ios/souls/list`, update the souls feature status if appropriate, and run the bundle validator.
