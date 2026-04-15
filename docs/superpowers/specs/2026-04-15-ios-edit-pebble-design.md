# iOS — Edit a pebble

**Issue:** #255 · **Milestone:** M19 · iOS ShameVP · **Labels:** `core`, `feat`, `ios`

## Intention

A logged-in user can open an existing pebble from the path, change any of its fields, and save the edits. This closes the CRUD loop (create + read + update) on iOS, leaving delete for a later issue.

## Decisions taken in brainstorming

These are the load-bearing choices made before writing this spec. They are recorded here so future readers (and future you) can see *why* the design looks the way it does.

1. **No read-only detail mode.** Opening a pebble lands directly in an editable form. `PebbleDetailSheet` is replaced, not kept alongside. Reason: avoids a mode toggle; matches Apple Notes; collapses read + edit into one surface.
2. **Explicit Save / Cancel toolbar.** Edits are not auto-saved on dismiss or on field change. Save writes, Cancel discards. Reason: symmetry with `CreatePebbleSheet` (same toolbar pattern), makes failure modes visible.
3. **All fields editable.** Every field that can be set at creation can also be edited: `name`, `description`, `happenedAt`, `emotion`, `domain`, `valence` (→ `positiveness`), `soul`, `collection`, `visibility`. Reason: the issue asks for "edit", with no carve-outs, and locking fields now creates follow-up issues later.
4. **No delete button.** Delete stays out of #255 and becomes its own issue. Reason: the acceptance criteria cover edit only; delete has cascade/RLS concerns that deserve their own slice.
5. **The form body is extracted into a shared view.** `CreatePebbleSheet` and `EditPebbleSheet` both render a new `PebbleFormView`, which is pure UI. Each sheet owns "where does the draft start?" and "what happens on Save?". Nothing else. This is an approved refactor of `CreatePebbleSheet` — explicitly justified, not incidental cleanup.
6. **Edit uses the `update_pebble` RPC; create stays on direct inserts.** The asymmetry is intentional: migrating `CreatePebbleSheet` to the RPC is out of scope for #255 and becomes its own issue later.
7. **RPCs get collection support (even though the UI doesn't use it yet).** `update_pebble` and `create_pebble` both gain `collection_ids` and `new_collections` payload keys. The UI still only exposes a single-collection picker. Reason: keeping sibling RPCs symmetric is worth a few unused lines of SQL, and the existing RPCs have a real correctness gap (they don't touch `collection_pebbles` at all).

## Architecture

### File layout

```
apps/ios/Pebbles/Features/Path/
  PebbleFormView.swift          NEW — shared Form body, pure UI
  EditPebbleSheet.swift         NEW — loads pebble + refs, owns draft, calls update_pebble RPC
  CreatePebbleSheet.swift       REFACTORED — renders PebbleFormView instead of inline Form
  PebbleDetailSheet.swift       DELETED — replaced by EditPebbleSheet
  Models/
    PebbleDraft.swift           EXTENDED — add init(from: PebbleDetail)
    PebbleUpdatePayload.swift   NEW — Encodable shape for the update_pebble RPC
    PebbleDetail.swift          unchanged — still the read model used for prefill
```

```
packages/supabase/supabase/migrations/
  202604150000XX_pebble_rpc_collections.sql   NEW — extends create_pebble and update_pebble
packages/supabase/types/database.ts           REGENERATED after migration
```

### Component responsibilities

**`PebbleFormView`** (new, pure view)
- Takes `@Binding var draft: PebbleDraft` and the four reference lists (`emotions`, `domains`, `souls`, `collections`) as plain `let` properties.
- Renders the `Form` body: scalar fields, mood section, optional section, privacy section, and the optional `saveError` row.
- Knows nothing about Supabase, sheets, dismiss, or insert/update semantics. This is the extraction point that makes create and edit share UI.

**`CreatePebbleSheet`** (refactored)
- Owns: empty `PebbleDraft`, reference-list fetch, `save()` that runs the existing direct-insert flow, `onCreated` callback.
- Body becomes: toolbar + `PebbleFormView($draft, ...)`.
- No behavioral change. Only the extraction.

**`EditPebbleSheet`** (new)
- Owns: `pebbleId: UUID` input, `onSaved: () -> Void` callback, `draft: PebbleDraft`, reference lists, loading/error state, `save()` that calls the `update_pebble` RPC.
- On `.task`, loads the pebble detail and the reference lists concurrently (`async let`), then builds the draft from the fetched detail and flips `isLoading = false`.
- Body is the same three-state pattern as `PebbleDetailSheet` (loading / error + retry / loaded form), with the loaded state rendering `PebbleFormView`.

**`PathView`** (minor change)
- Replace `.sheet(item: $selectedPebbleId) { PebbleDetailSheet(pebbleId: $0) }` with `EditPebbleSheet(pebbleId: $0, onSaved: { Task { await reloadPebbles() } })`.

### Data models

**`PebbleDraft.init(from: PebbleDetail)`** (new convenience init)
- Maps `detail.name`, `detail.description`, `detail.happenedAt`, `detail.visibility`.
- Maps `detail.emotion.id` → `emotionId`.
- Maps the first element of `detail.domains` → `domainId` (mandatory; must exist).
- Maps `detail.souls.first?.id` → `soulId` (optional).
- Maps `detail.collections.first?.id` → `collectionId` (optional).
- Maps `detail.valence` → `valence`.

If `detail.domains` is unexpectedly empty, log a warning via `os.Logger` and leave `draft.domainId` nil — `draft.isValid` will catch it and disable Save.

**`PebbleUpdatePayload`** (new `Encodable`)
- Maps the draft to the jsonb shape expected by `update_pebble`. All keys snake_case via `CodingKeys`.
- Single-valued pickers wrap into arrays at the boundary: `domainIds = [draft.domainId!]`, `soulIds = draft.soulId.map { [$0] } ?? []`, `collectionIds = draft.collectionId.map { [$0] } ?? []`.
- Sends all scalar fields on every save. Rationale: `update_pebble` uses `coalesce(payload->>..., existing)` — absent keys mean "keep existing". Sending everything is correct and simpler than a dirty-field tracker.

## Data flow

### Opening the sheet

1. User taps a pebble row in `PathView`.
2. `PathView` sets `selectedPebbleId`, `.sheet(item:)` presents `EditPebbleSheet`.
3. `EditPebbleSheet.task` runs:
   - Concurrently fetch the pebble detail (same `pebbles?select=...` query the old `PebbleDetailSheet` used) and the four reference lists.
   - On success: build `draft = PebbleDraft(from: detail)`, flip `isLoading = false`.
   - On failure: set `loadError`, show retry button.
4. Once loaded, the user sees a prefilled editable form.

### Saving edits

1. User taps **Save** in the toolbar.
2. `save()` checks `draft.isValid`; sets `isSaving = true`.
3. Build `PebbleUpdatePayload(from: draft)`.
4. Call `supabase.client.rpc("update_pebble", params: [pebbleId, payload]).execute()`.
5. On success: call `onSaved()` (triggers `PathView` refetch), `dismiss()`.
6. On failure: log via `os.Logger`, set `saveError`, reset `isSaving`, stay in the form so the user can retry or cancel.

### Cancel

Toolbar **Cancel** button calls `dismiss()` directly. No confirmation, no dirty-state check. If the user wants that, it's a follow-up.

## Supabase changes

### New migration

Filename: `202604150000XX_pebble_rpc_collections.sql` (pick the next available ordinal for today).

Uses `create or replace function` to extend both `create_pebble` and `update_pebble`. Copy-pastes the existing soul/domain pattern for collections.

**`create_pebble` additions:**

```sql
-- Collect existing collection IDs
select array_agg(val::uuid)
into v_collection_ids
from jsonb_array_elements_text(coalesce(payload->'collection_ids', '[]'::jsonb)) val;

-- Inline collection creation
if payload ? 'new_collections' then
  for v_new_collection in select * from jsonb_array_elements(payload->'new_collections')
  loop
    insert into public.collections (user_id, name)
    values (v_user_id, v_new_collection.value->>'name')
    returning id into v_new_collection_id;

    v_collection_ids := array_append(v_collection_ids, v_new_collection_id);
  end loop;
end if;

-- After the pebble INSERT, link collections
if v_collection_ids is not null then
  insert into public.collection_pebbles (collection_id, pebble_id)
  select unnest(v_collection_ids), v_pebble_id;
end if;
```

**`update_pebble` additions:**

```sql
-- Inline collection creation (before the replace)
if payload ? 'new_collections' then
  select array_agg(val::uuid)
  into v_collection_ids
  from jsonb_array_elements_text(coalesce(payload->'collection_ids', '[]'::jsonb)) val;

  for v_new_collection in select * from jsonb_array_elements(payload->'new_collections')
  loop
    insert into public.collections (user_id, name)
    values (v_user_id, v_new_collection.value->>'name')
    returning id into v_new_collection_id;

    v_collection_ids := array_append(v_collection_ids, v_new_collection_id);
  end loop;

  payload := payload || jsonb_build_object('collection_ids', to_jsonb(v_collection_ids));
end if;

-- Replace collections
if payload ? 'collection_ids' then
  delete from public.collection_pebbles where pebble_id = p_pebble_id;

  insert into public.collection_pebbles (collection_id, pebble_id)
  select (val::text)::uuid, p_pebble_id
  from jsonb_array_elements_text(payload->'collection_ids') val;
end if;
```

`update_pebble` must keep its `security definer` and `set search_path = public` attributes. The migration uses `create or replace function` so both function bodies are rewritten in place, matching the convention in `20260411000005_security_hardening.sql`.

**Karma:** collections do not contribute to karma today; the existing karma computation in `update_pebble` stays untouched.

**Ownership check:** because the RPCs are `security definer`, they bypass RLS. Without an explicit check, a user could link a pebble to another user's collection by guessing IDs. Both functions must verify, after merging `new_collections` into the final `collection_ids` array, that every listed collection has `user_id = v_user_id`. On mismatch, raise `'Collection not owned by user'`. The check runs after the merge (so newly-created collections, which are inserted with `user_id = v_user_id`, always pass) and before the replace/insert step. The same check pattern should be added for `domain_ids`, `soul_ids` is already safe because `new_souls` creates them under the current user — existing `soul_ids` already pass through RLS-bypassing code paths and should get the same explicit ownership check for consistency. Adding ownership checks for pre-existing payload keys (`domain_ids`, `soul_ids`) is a **security hardening** concern that technically pre-dates this PR; flagging it here but keeping it out of scope unless review says otherwise.

### Types regen

After the migration lands:

```bash
npm run db:reset --workspace=packages/supabase
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```

Per `apps/ios/CLAUDE.md`, iOS doesn't import `database.ts` directly, but the regen is still required so web + future tooling stay in sync.

## Error handling and logging

- `EditPebbleSheet` logs via `os.Logger(subsystem: "app.pbbls.ios", category: "edit-pebble")` on every error path (initial load, reference load, save).
- All visible errors are user-facing strings, not raw `error.localizedDescription` — the raw error is logged privately.
- The save path resets `isSaving` on failure so the user can retry.
- No empty catch blocks. No silent failures. This matches the rule in `apps/ios/CLAUDE.md` ("Runtime async failures must be surfaced").

## Testing

Two new test files under `apps/ios/PebblesTests/`, both using Swift Testing (`@Suite`, `@Test`, `#expect`):

**`PebbleDraftFromDetailTests.swift`**
- Fully populated detail → draft has all fields including optional soul/collection.
- Detail with no souls → `draft.soulId == nil`.
- Detail with no collections → `draft.collectionId == nil`.
- Detail with empty domains → `draft.domainId == nil` (guard case), warning logged.
- Valence round-trip: `positiveness` → `valence` → draft matches original.

**`PebbleUpdatePayloadEncodingTests.swift`**
- Encodes to JSON with snake_case keys for every field.
- `domain_ids` is always a single-element array.
- `soul_ids` is an empty array when `draft.soulId == nil`, single-element array otherwise.
- `collection_ids` follows the same pattern as `soul_ids`.
- `visibility` encodes as the raw string value, not the enum name.

No RPC integration test — the repo has no Postgres test harness, and adding one is out of scope. Manual testing during implementation covers the server side.

## Out of scope for #255

Explicitly not in this PR. Each becomes its own issue:

- Delete a pebble.
- Migrating `CreatePebbleSheet` to call `create_pebble` RPC instead of direct inserts.
- Multi-select domains / souls / collections in the UI.
- Inline creation of souls / collections from the sheet (the RPC support lands here; the UI does not).
- Dirty-state tracking, "unsaved changes" warnings on dismiss, disabling Save when nothing changed.
- Snap editing (photos), card editing.

## Acceptance criteria

From the issue, re-verified against this design:

- [x] As a logged user with pebbles, when I open a pebble, I can edit it.
  → Tapping a row in `PathView` opens `EditPebbleSheet` with a prefilled, editable form.
- [x] As a logged user on a pebble, when I save the edits, they are saved.
  → `Save` calls `update_pebble` RPC; server-side transaction replaces join rows and updates scalar fields atomically; `onSaved` triggers a `PathView` refetch so the user sees the change immediately.
