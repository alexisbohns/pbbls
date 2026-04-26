# Edit-mode photo support — design

Resolves: [#323](https://github.com/alexisbohns/pbbls/issues/323) — *[Feat] Support attaching/replacing/removing photos when editing a pebble*

Status: design approved, ready for implementation plan.

## Goal

Extend `EditPebbleSheet` so users can attach, replace, or remove the photo on an existing pebble. V1 of pebble photos (#321 / PR #325) wired this for create only; the form section is already shared and the iOS upload pipeline is reusable as-is. The remaining work is enabling the form section in edit mode, prefilling the existing snap, wiring an immediate-remove RPC, and teaching `update_pebble` to round-trip snap ids and enforce the per-pebble quota.

## Non-goals

- Multi-photo per pebble (still capped by `profiles.max_media_per_pebble = 1`).
- Reordering, captions, alt text.
- Optimistic locking on concurrent edits.
- Orphan Storage sweep (separate follow-up).
- Web app parity — iOS only.

## User-visible behaviour

- Opening edit on a pebble with a photo: the Photo section shows the existing thumbnail and a remove (X) button.
- Tap remove on an existing photo → the photo is removed immediately (DB row + Storage). "Cancel" does not undo this.
- Tap remove on a just-picked (in-flight) photo → behaves exactly like create flow: clears local state, fires compensating Storage delete.
- With no photo attached, the section shows "Add a photo"; picking a photo runs the same pipeline as create (process → upload → `.pending` row).
- Replace = remove then add (two-step, mirroring the affordances we already have).
- Quota error from the server surfaces the existing localized "Photo limit reached on this pebble." string.

## Architecture overview

The edit photo section is a thin dispatcher over a `FormSnap` enum that distinguishes a snap that is already saved in the DB from one that is mid-upload:

```swift
enum FormSnap: Equatable {
  case existing(id: UUID, storagePath: String)   // hydrated from PebbleDetail
  case pending(AttachedSnap)                     // in-flight or just-uploaded
}
```

`PebbleDraft.attachedSnap` is renamed to `formSnap: FormSnap?`. The form section renders:

- `nil` → "Add a photo" button (existing).
- `.existing(...)` → new `ExistingSnapRow` view, which uses `SnapImageView` for the thumbnail and exposes a remove callback.
- `.pending(snap)` → existing `AttachedPhotoView`, fed via a derived `Binding<AttachedSnap?>`.

Removal of an `.existing` snap is committed eagerly via a new `delete_pebble_media(snap_id)` RPC; removal of a `.pending` snap stays local with a compensating Storage delete (current behaviour).

Save reuses `update_pebble`. The RPC is updated to accept `snaps[].id` (so unchanged snaps round-trip with the same UUID and storage path) and to enforce the per-pebble quota — same shape as `create_pebble`.

## Backend changes

New migration: `packages/supabase/supabase/migrations/<timestamp>_pebble_media_edit.sql`.

### `update_pebble` — accept `snaps[].id`, enforce quota

Re-create `public.update_pebble(p_pebble_id uuid, payload jsonb)` with the same body as `20260415000000_pebble_rpc_collections.sql`, with two changes inside the snaps replace block:

- Read `coalesce(max_media_per_pebble, 1)` from `profiles` for the caller and raise `media_quota_exceeded` (`errcode = 'P0001'`) if `jsonb_array_length(payload->'snaps') > v_max_media`.
- When inserting, default `id` to the supplied value when present: `coalesce((v_snap.value->>'id')::uuid, gen_random_uuid())`.

The existing delete-then-insert behaviour stays. For an unchanged snap the row is deleted and reinserted with the same id; this is safe because no FK references `snaps.id` today. The migration header documents this assumption so future schema work catches it.

### New `delete_pebble_media(p_snap_id uuid)` RPC

```sql
create or replace function public.delete_pebble_media(p_snap_id uuid)
returns text
language plpgsql security definer set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_storage_path text;
begin
  delete from public.snaps
  where id = p_snap_id and user_id = v_user_id
  returning storage_path into v_storage_path;

  if v_storage_path is null then
    raise exception 'Snap not found or access denied';
  end if;

  return v_storage_path;
end;
$$;
```

Returns the deleted row's `storage_path` so the iOS client can issue Storage `remove(paths:)` for `original.jpg` and `thumb.jpg`. Storage-level RLS already restricts deletes to the owner; the RPC ownership check is defence-in-depth and keeps the contract tight.

### Type regeneration

Per `AGENTS.md`: after the migration, run `npm run db:types --workspace=packages/supabase` and commit `packages/supabase/types/database.ts` in the same change.

## iOS changes

### Models

- **New file** `apps/ios/Pebbles/Features/PebbleMedia/Models/FormSnap.swift` — the enum above.
- `PebbleDraft.attachedSnap` → `formSnap: FormSnap?`. All references in `CreatePebbleSheet`, `PebbleFormView`, and any `.onChange` observers are updated.
- `PebbleDraft.init(from: PebbleDetail)` prefills `formSnap` from `detail.snaps.first` (one-snap world): `.existing(id: $0.id, storagePath: $0.storagePath)`.

### `PebbleDetail` load

`EditPebbleSheet.load()` extends the PostgREST select to include `snaps(id, storage_path, sort_order)`. `PebbleDetail.snaps` is already decoded; this just populates it.

### `PebbleUpdatePayload`

Adds `let snaps: [SnapPayload]?` (same shape as `PebbleCreatePayload.SnapPayload`: `id`, `storage_path`, `sort_order`). `init(from: draft, userId:)` builds the array from `draft.formSnap`:

- `.existing(id, path)` → `[SnapPayload(id: id, storagePath: path, sortOrder: 0)]`
- `.pending(snap)` (must be `.uploaded`) → `[SnapPayload(id: snap.id, storagePath: snap.storagePrefix(userId:), sortOrder: 0)]`
- `nil` → `[]` (explicit empty array so `update_pebble`'s `payload ? 'snaps'` branch fires and any stale row is wiped server-side as defence).

`save()` in `EditPebbleSheet` now needs `userId` (already available via `supabase.session?.user.id`).

### `PebbleFormView` photo section

The `if showsPhotoSection { ... }` block becomes a switch on `draft.formSnap`:

- `.none` → existing "Add a photo" button.
- `.existing(id, path)` → new `ExistingSnapRow` with `onRemove` callback.
- `.pending` → existing `AttachedPhotoView`, bound through a derived `Binding<AttachedSnap?>` that maps `.pending(snap) ↔ AttachedSnap?` (setting nil clears `formSnap` and triggers the existing compensating-delete `.onChange`).

The `showsPhotoSection` parameter stays; `EditPebbleSheet` passes `true`.

### New `ExistingSnapRow`

`apps/ios/Pebbles/Features/PebbleMedia/ExistingSnapRow.swift`. Inputs: `id: UUID`, `storagePath: String`, `onRemove: () -> Void`. Renders:

- `SnapImageView(storagePath:)` thumbnail (existing component, signed-URL fetch).
- "Photo" label.
- Destructive remove button calling `onRemove`.

Stateless. The parent (`EditPebbleSheet`) handles the actual eager-delete RPC and any in-flight UI gating.

### `EditPebbleSheet` wiring

Three additions, parallel to `CreatePebbleSheet`:

1. **Photo picker host** — add `@State isPhotoPickerPresented`, the `.sheet` block hosting `PhotoPickerView`, and a `handlePicked(_:)` method copy-pasted from `CreatePebbleSheet`. (Sets `draft.formSnap = .pending(...)` and uploads. Parallel copies are fine for V1; share later if churn grows.)
2. **Eager remove handler** — `ExistingSnapRow.onRemove` triggers:
   1. Disable the row to block double-tap.
   2. `supabase.client.rpc("delete_pebble_media", params: ["p_snap_id": id])`, decoding the returned `String` storage_path.
   3. On success, fire-and-forget Storage `remove(paths: [.../original.jpg, .../thumb.jpg])` (mirroring `PebbleSnapRepository.deleteFiles` discipline — log failures, don't throw), then `draft.formSnap = nil`.
   4. On RPC failure, log via `os.Logger` and surface `"Couldn't remove the photo. Please try again."`. Leave `formSnap` unchanged so retry works.
3. **`.onChange` observers** — copy the two from `CreatePebbleSheet` (`failed→uploading` retry; `.pending` cleared → compensating Storage delete). They only act on `.pending` cases by construction.

Replace flow falls out for free: existing remove → `formSnap = nil` → "Add a photo" → pick → upload → `.pending` → save replaces in `update_pebble`. No special handling.

## Error handling

- **Quota exceeded (`P0001` / `media_quota_exceeded`):** the existing `userMessage(for:)` helper in `CreatePebbleSheet` already maps this to "Photo limit reached on this pebble." Lift it to a private module-level helper (or `PebbleSaveError.message`) so `EditPebbleSheet` reuses the same mapping.
- **`delete_pebble_media` failure:** log via `os.Logger`; surface `"Couldn't remove the photo. Please try again."` via the `saveError` slot. Revert any optimistic UI.
- **Save blocks (`.pending` still `.uploading` / `.failed`):** carry over verbatim from `CreatePebbleSheet`.
- **Storage cleanup after RPC delete:** fire-and-forget; log on failure. Orphan sweep (out of scope) catches residue.

## Edge cases

- **Pick photo, then Cancel sheet.** Compensating Storage delete fires on the `.pending` upload (existing observer).
- **Remove existing, then Cancel sheet.** Photo stays gone (per eager-remove decision). Cancel only abandons text/metadata edits.
- **Remove existing, pick new, then Cancel sheet.** Original is gone (eager). New `.pending` upload gets compensating delete. Final state: pebble has no photo.
- **Concurrent edits:** out of scope. Last write wins.

## Testing

- No test scaffolding in V1 (per project CLAUDE.md). Keep `PebbleUpdatePayload`'s builder and the `FormSnap` mapping pure so they remain unit-testable later.
- Manual QA matrix on device:
  1. Pebble with photo: open edit → thumb visible → Save unchanged → photo still there.
  2. Pebble with photo: remove → Save → photo gone, Storage cleaned.
  3. Pebble with photo: remove → add new → Save → new photo, old Storage gone.
  4. Pebble without photo: add → Save → new photo present.
  5. Pebble without photo: add → Cancel → Storage cleaned.
  6. Quota boundary: with `max_media_per_pebble = 0` (manual DB tweak), confirm error text renders.
- Run `xcodegen generate` after adding `FormSnap.swift` and `ExistingSnapRow.swift`; verify they are picked up by the path glob.
- `npm run lint` and `npm run build` from the iOS workspace before opening the PR (per project guidelines).
