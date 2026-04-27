# iOS pebble deletion — design

Resolves [#327](https://github.com/Bohns/pbbls/issues/327).

## Intent

Users on iOS can create, read, and edit pebbles, but not delete them. This spec adds long-press deletion to every list where a pebble row appears, and harmonizes those rows into a single component so the visual treatment is consistent everywhere.

## Current state

- Pebble rows are open-coded in three places:
  - `apps/ios/Pebbles/Features/Path/PathView.swift` — the only row that renders the pebble's `render_svg` thumbnail.
  - `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` — name + date only.
  - `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift` — name + date only.
- The `delete_pebble(p_pebble_id uuid)` RPC already exists in the database (`packages/supabase/supabase/migrations/20260411000003_rpc_functions.sql`). It verifies ownership via `auth.uid()`, inserts a compensating `pebble_deleted` karma event, and deletes the pebble row (which cascades to `cards`, `snaps`, and the join tables in DB).
- The web app already calls this RPC in `apps/web/lib/data/supabase-provider.ts:303`.
- A working SwiftUI pattern for "long-press → contextMenu → confirmationDialog → RPC" exists in `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` and `CollectionsListView.swift`.

## Approach

### A new shared `PebbleRow` component

Create `apps/ios/Pebbles/Components/PebbleRow.swift` (a new top-level `Components/` folder, sibling to `Features/`, `Services/`, `Resources/`). The folder is auto-included by xcodegen because `project.yml` declares `sources: - path: Pebbles`.

`PebbleRow` is a single-purpose presentational view:

```swift
struct PebbleRow: View {
    let pebble: Pebble
    let onTap: () -> Void
    let onDelete: () -> Void
}
```

It renders the canonical 40 pt thumbnail (using `PebbleRenderView` with the pebble's emotion stroke colour, falling back to a neutral rounded rectangle when `render_svg` is nil) plus name and `happenedAt` date. It owns the `.contextMenu` containing a destructive Delete button. The contextMenu lives at the row level on purpose: a future fourth list cannot ship without the deletion affordance.

Required `Pebble` fields: `id`, `name`, `happenedAt`, `renderSvg`, `emotion`. The model already decodes these (PathView relies on the same shape today).

### Responsibility split

The row is purely visual. The destructive flow lives in the parent — same shape `SoulsListView` uses today:

- `@State private var pendingDeletion: Pebble?`
- `@State private var deleteError: String?`
- `.confirmationDialog(...)` presenting `pendingDeletion`, with destructive **Delete** and **Cancel** buttons. Title `"Delete <pebble name>?"`, message `"This can't be undone."`.
- `.alert("Couldn't delete", ...)` presenting `deleteError`.
- `private func delete(_ pebble: Pebble) async` calls `delete_pebble` via `.rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])` and reloads the parent's list on success. On failure it logs via `os.Logger` (mirroring the web-side discipline that silent failures are bugs) and surfaces a user-facing alert.

`.listRowBackground(Color.pebblesListRow)` stays at the call site — `PathView` uses it, `CollectionDetailView` uses `.insetGrouped`, and `SoulDetailView` uses a plain `List`.

### Extend the queries in detail views

`SoulDetailView.load()` and `CollectionDetailView.load()` currently fetch only `id, name, happened_at`, which is why their rows have no thumbnail. Extending the selects to match `PathView`:

- `SoulDetailView`: `"id, name, happened_at, render_svg, emotion:emotions(id, slug, name, color), pebble_souls!inner(soul_id)"`
- `CollectionDetailView`: `"id, name, happened_at, render_svg, emotion:emotions(id, slug, name, color), collection_pebbles!inner(collection_id)"`

No `Pebble` model changes are required; the existing decoder already handles these fields.

## Deletion mechanics

The `delete_pebble` RPC handles the entire DB-side transaction atomically:

1. Verifies `auth.uid()` matches the pebble's `user_id`. Raises `Pebble not found or access denied` otherwise.
2. Sums karma earned from this pebble (`karma_events.ref_id = p_pebble_id`) and inserts a compensating negative event with reason `pebble_deleted`.
3. Deletes the pebble row, which cascades in DB to `cards`, `snaps`, `pebble_domains`, `pebble_souls`, and `collection_pebbles`.

No client-side multi-call orchestration is needed.

### Storage cleanup is out of scope

`delete_pebble` cascades to the `snaps` row in DB but does **not** delete the underlying files in Supabase Storage. The web app has the same gap today. Leaving orphaned snap files is the existing behaviour; a server-side trigger or a follow-up RPC change is the right place to fix it. This spec ships parity with web; a follow-up issue should track storage hygiene.

## Localization

Most strings the deletion flow needs are already in `apps/ios/Pebbles/Resources/Localizable.xcstrings` (with both `en` and `fr` populated): `Delete`, `Delete %@?`, `Couldn't delete`, `Cancel`, `OK`. They are reused as-is.

One new entry is needed:

| Key | en | fr |
|---|---|---|
| `This can't be undone.` | This can't be undone. | Cette action est irréversible. |

The runtime error string `"Something went wrong. Please try again."` matches the existing pattern in `SoulsListView`/`CollectionsListView`, where it is assigned to a `String?` variable (`deleteError`). Because the literal is not passed directly to `Text(_:)`, it is not auto-extracted by `SWIFT_EMIT_LOC_STRINGS=YES` — those views currently render this message in English regardless of locale. This spec preserves that behaviour to stay consistent with the established pattern; localizing runtime error strings is tracked as a separate cleanup.

Per the iOS CLAUDE.md, open `Localizable.xcstrings` in Xcode after the change and verify no entries are in `New` or `Stale` state.

## File touch list

**New**

- `apps/ios/Pebbles/Components/PebbleRow.swift`

**Modified**

- `apps/ios/Pebbles/Features/Path/PathView.swift` — replace inline row + `pebbleThumbnail` helper with `PebbleRow`; add `pendingDeletion`, `deleteError`, `.confirmationDialog`, `.alert`, and `delete(_:)`.
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` — extend `load()` select; swap inline row for `PebbleRow`; add deletion state, dialogs, and `delete(_:)`.
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift` — extend `load()` select; swap inline row for `PebbleRow`; add deletion state, dialogs, and `delete(_:)`.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — new entries above.

**Build step**

- Run `npm run generate --workspace=@pbbls/ios` (xcodegen) so the new `Components/` directory is registered in the regenerated `.xcodeproj`.

## Out of scope

- Server-side storage cleanup for orphaned snap files after pebble deletion (matches existing web behaviour; warrants a separate issue).
- A "Delete" button inside `EditPebbleSheet`. Issue #327 specifies long-press only; that affordance can be added later if the product calls for it.
- Swipe-to-delete on the `List`. The contextMenu pattern matches the existing `SoulsListView` and `CollectionsListView` UX.

## Risks

- **Visual parity bug.** The two detail views currently show no thumbnail; after this change they will. Worth eyeballing once on device to confirm the thumbnail renders correctly when the soul/collection's pebbles are loaded with the new select.
- **Localization drift.** New strings must land in both `en` and `fr` or the catalog goes `Stale`. The pre-PR check covers this.
- **Karma reversal already covered by the RPC.** No additional client-side karma handling.
