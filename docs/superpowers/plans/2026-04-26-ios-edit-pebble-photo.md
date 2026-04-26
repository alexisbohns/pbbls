# Edit-mode photo support — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire attach / replace / remove photo support into `EditPebbleSheet` for issue [#323](https://github.com/alexisbohns/pbbls/issues/323).

**Architecture:** Backend gets one new migration that (a) extends `update_pebble` to accept `snaps[].id` and enforce the per-pebble quota, and (b) adds a `delete_pebble_media(snap_id)` RPC returning `storage_path` for eager Storage cleanup. iOS introduces a `FormSnap` enum bridging "already in DB" (`.existing`) and "in-flight upload" (`.pending`) states, replaces `PebbleDraft.attachedSnap` with `formSnap`, dispatches the photo section in `PebbleFormView`, and copies the picker/upload pipeline from `CreatePebbleSheet` into `EditPebbleSheet` plus an eager remove handler.

**Tech Stack:** SwiftUI / iOS 17, Supabase (Postgres RPC + Storage), shared edge function `compose-pebble-update` (already passes `payload` through).

**Spec:** `docs/superpowers/specs/2026-04-26-ios-edit-pebble-photo-design.md`

**Branch:** `feat/323-ios-edit-pebble-photo` (already created; spec already committed).

## File map

| File | Action | Responsibility |
|---|---|---|
| `packages/supabase/supabase/migrations/20260426000002_pebble_media_edit.sql` | Create | Replace `update_pebble` with id-preserving + quota-enforcing snaps block; add `delete_pebble_media` RPC |
| `packages/supabase/types/database.ts` | Modify (regenerated) | Pick up new RPC signature |
| `apps/ios/Pebbles/Features/PebbleMedia/Models/FormSnap.swift` | Create | Enum bridging existing vs pending snap |
| `apps/ios/Pebbles/Features/PebbleMedia/Models/AttachedSnap.swift` | Modify | (no change — referenced by `.pending`) |
| `apps/ios/Pebbles/Features/PebbleMedia/ExistingSnapRow.swift` | Create | Form row for an already-saved snap (thumb + remove button) |
| `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift` | Modify | `attachedSnap` → `formSnap`; prefill from `PebbleDetail` |
| `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift` | Modify | Read from `draft.formSnap` instead of `draft.attachedSnap` |
| `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift` | Modify | Add `snaps: [SnapPayload]?`; build from `draft.formSnap` |
| `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` | Modify | Switch on `draft.formSnap` for photo section |
| `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` | Modify | Update references to renamed `formSnap`; lift `userMessage(for:)` to module-private helper |
| `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` | Modify | Extend select to include `snaps`, host photo picker, handle eager remove, pass `userId` to payload |

---

## Task 1: Migration — extend `update_pebble`, add `delete_pebble_media`

**Files:**
- Create: `packages/supabase/supabase/migrations/20260426000002_pebble_media_edit.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated)

- [ ] **Step 1: Create the migration file**

Create `packages/supabase/supabase/migrations/20260426000002_pebble_media_edit.sql` with this content:

```sql
-- Migration: edit-mode pebble photo support
--
-- (a) Re-creates update_pebble so the snaps replace block accepts an
--     optional `id` per snap (so unchanged snaps round-trip with the same
--     UUID and Storage path) and enforces the per-pebble quota by raising
--     `media_quota_exceeded` (SQLSTATE P0001) when exceeded — same shape
--     as create_pebble (20260426000001_pebbles_pictures.sql).
--
-- (b) Adds delete_pebble_media(p_snap_id), used by EditPebbleSheet to
--     commit a snap removal eagerly. Returns the deleted row's
--     storage_path so the iOS client can clean up Storage. Storage-level
--     RLS already restricts deletes to the owner; the ownership check
--     here is defense in depth.
--
-- Assumption: no FK references `public.snaps.id`. The replace block in
-- update_pebble does delete-then-reinsert with the same id for unchanged
-- snaps, which is safe under that assumption. If a future migration adds
-- a reference, switch the block to upsert semantics.

-- ============================================================
-- update_pebble — accepts snaps[].id, enforces per-pebble quota
-- ============================================================

create or replace function public.update_pebble(p_pebble_id uuid, payload jsonb)
returns void as $$
declare
  v_user_id uuid := auth.uid();
  v_glyph_id uuid;
  v_soul_ids uuid[];
  v_collection_ids uuid[];
  v_new_soul record;
  v_new_soul_id uuid;
  v_new_collection record;
  v_new_collection_id uuid;
  v_card record;
  v_snap record;
  v_old_karma int;
  v_new_karma int;
  v_description text;
  v_cards_count int;
  v_souls_count int;
  v_domains_count int;
  v_has_glyph boolean;
  v_snaps_count int;
  v_unauthorized_collection uuid;
  v_max_media int;
begin
  -- Verify ownership
  if not exists (
    select 1 from public.pebbles
    where id = p_pebble_id and user_id = v_user_id
  ) then
    raise exception 'Pebble not found or access denied';
  end if;

  -- Inline glyph creation
  if payload ? 'new_glyph' then
    insert into public.glyphs (user_id, name, shape_id, strokes, view_box)
    values (
      v_user_id,
      (payload->'new_glyph'->>'name'),
      (payload->'new_glyph'->>'shape_id')::uuid,
      coalesce(payload->'new_glyph'->'strokes', '[]'::jsonb),
      (payload->'new_glyph'->>'view_box')
    )
    returning id into v_glyph_id;

    payload := payload || jsonb_build_object('glyph_id', v_glyph_id::text);
  end if;

  -- Inline soul creation
  if payload ? 'new_souls' then
    select array_agg(val::uuid)
    into v_soul_ids
    from jsonb_array_elements_text(coalesce(payload->'soul_ids', '[]'::jsonb)) val;

    for v_new_soul in select * from jsonb_array_elements(payload->'new_souls')
    loop
      insert into public.souls (user_id, name)
      values (v_user_id, v_new_soul.value->>'name')
      returning id into v_new_soul_id;

      v_soul_ids := array_append(v_soul_ids, v_new_soul_id);
    end loop;

    payload := payload || jsonb_build_object(
      'soul_ids', to_jsonb(v_soul_ids)
    );
  end if;

  -- Inline collection creation (merges into collection_ids before the replace)
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

    payload := payload || jsonb_build_object(
      'collection_ids', to_jsonb(v_collection_ids)
    );
  end if;

  -- Ownership check: every collection ID in the final payload must belong to the user
  if payload ? 'collection_ids' then
    select c_id into v_unauthorized_collection
    from jsonb_array_elements_text(payload->'collection_ids') val,
         lateral (select (val::text)::uuid as c_id) ids
    where not exists (
      select 1 from public.collections
      where id = ids.c_id and user_id = v_user_id
    )
    limit 1;

    if v_unauthorized_collection is not null then
      raise exception 'Collection not owned by user: %', v_unauthorized_collection;
    end if;
  end if;

  -- Update scalar fields (only those present in payload)
  update public.pebbles set
    name          = coalesce(payload->>'name', name),
    description   = case when payload ? 'description' then payload->>'description' else description end,
    happened_at   = coalesce((payload->>'happened_at')::timestamptz, happened_at),
    intensity     = coalesce((payload->>'intensity')::smallint, intensity),
    positiveness  = coalesce((payload->>'positiveness')::smallint, positiveness),
    visibility    = coalesce(payload->>'visibility', visibility),
    emotion_id    = coalesce((payload->>'emotion_id')::uuid, emotion_id),
    glyph_id      = case when payload ? 'glyph_id' then (payload->>'glyph_id')::uuid else glyph_id end
  where id = p_pebble_id;

  -- Replace cards
  if payload ? 'cards' then
    delete from public.pebble_cards where pebble_id = p_pebble_id;

    for v_card in select * from jsonb_array_elements(payload->'cards')
    loop
      insert into public.pebble_cards (pebble_id, species_id, value, sort_order)
      values (
        p_pebble_id,
        (v_card.value->>'species_id')::uuid,
        v_card.value->>'value',
        coalesce((v_card.value->>'sort_order')::smallint, 0)
      );
    end loop;
  end if;

  -- Replace souls
  if payload ? 'soul_ids' then
    delete from public.pebble_souls where pebble_id = p_pebble_id;

    insert into public.pebble_souls (pebble_id, soul_id)
    select p_pebble_id, (val::text)::uuid
    from jsonb_array_elements_text(payload->'soul_ids') val;
  end if;

  -- Replace domains
  if payload ? 'domain_ids' then
    delete from public.pebble_domains where pebble_id = p_pebble_id;

    insert into public.pebble_domains (pebble_id, domain_id)
    select p_pebble_id, (val::text)::uuid
    from jsonb_array_elements_text(payload->'domain_ids') val;
  end if;

  -- Replace collections
  if payload ? 'collection_ids' then
    delete from public.collection_pebbles where pebble_id = p_pebble_id;

    insert into public.collection_pebbles (collection_id, pebble_id)
    select (val::text)::uuid, p_pebble_id
    from jsonb_array_elements_text(payload->'collection_ids') val;
  end if;

  -- Replace snaps (accepts iOS-generated id; enforces per-pebble quota)
  if payload ? 'snaps' then
    select coalesce(max_media_per_pebble, 1) into v_max_media
      from public.profiles where id = v_user_id;

    if jsonb_array_length(payload->'snaps') > v_max_media then
      raise exception 'media_quota_exceeded' using errcode = 'P0001';
    end if;

    delete from public.snaps where pebble_id = p_pebble_id;

    for v_snap in select * from jsonb_array_elements(payload->'snaps')
    loop
      insert into public.snaps (id, pebble_id, user_id, storage_path, sort_order)
      values (
        coalesce((v_snap.value->>'id')::uuid, gen_random_uuid()),
        p_pebble_id,
        v_user_id,
        v_snap.value->>'storage_path',
        coalesce((v_snap.value->>'sort_order')::smallint, 0)
      );
    end loop;
  end if;

  -- Recompute karma
  select p.description into v_description from public.pebbles p where p.id = p_pebble_id;
  select count(*) into v_cards_count from public.pebble_cards where pebble_id = p_pebble_id;
  select count(*) into v_souls_count from public.pebble_souls where pebble_id = p_pebble_id;
  select count(*) into v_domains_count from public.pebble_domains where pebble_id = p_pebble_id;
  select glyph_id is not null into v_has_glyph from public.pebbles where id = p_pebble_id;
  select count(*) into v_snaps_count from public.snaps where pebble_id = p_pebble_id;

  v_new_karma := public.compute_karma_delta(
    v_description, v_cards_count, v_souls_count,
    v_domains_count, v_has_glyph, v_snaps_count
  );

  select coalesce(sum(ke.delta), 0) into v_old_karma
  from public.karma_events ke
  where ke.ref_id = p_pebble_id and ke.user_id = v_user_id;

  if v_new_karma <> v_old_karma then
    insert into public.karma_events (user_id, delta, reason, ref_id)
    values (v_user_id, v_new_karma - v_old_karma, 'pebble_enriched', p_pebble_id);
  end if;
end;
$$ language plpgsql security definer set search_path = public;

-- ============================================================
-- delete_pebble_media — eager removal RPC
-- ============================================================

create or replace function public.delete_pebble_media(p_snap_id uuid)
returns text as $$
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
$$ language plpgsql security definer set search_path = public;
```

- [ ] **Step 2: Commit the migration file only**

Type regeneration and remote `db push` are deferred to the user (this project's local Docker is unreliable; the user runs deploys manually). The iOS tasks don't read from `database.ts`, so they compile without the regen.

```bash
git add packages/supabase/supabase/migrations/20260426000002_pebble_media_edit.sql
git commit -m "feat(db): support snap id round-trip and eager media delete"
```

---

## Task 2: New `FormSnap` enum

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Models/FormSnap.swift`

- [ ] **Step 1: Write the file**

```swift
import Foundation

/// In-form representation of the (at most one) photo attached to a pebble
/// being created or edited.
///
/// - `.existing` — already saved in the DB. The form renders the thumbnail
///   from `storagePath` and exposes a remove affordance that triggers the
///   eager `delete_pebble_media` RPC in `EditPebbleSheet`.
/// - `.pending` — an in-flight or just-uploaded local pick (no DB row yet).
///   Same `AttachedSnap` shape used by `CreatePebbleSheet`.
enum FormSnap: Equatable {
    case existing(id: UUID, storagePath: String)
    case pending(AttachedSnap)
}
```

- [ ] **Step 2: Build to verify the file compiles**

Run from the repo root:

```bash
xcodegen generate --spec apps/ios/project.yml
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build CODE_SIGNING_ALLOWED=NO
```

Expected: build succeeds (the new file is picked up by the path glob in `project.yml`).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/project.yml apps/ios/Pebbles/Features/PebbleMedia/Models/FormSnap.swift
git commit -m "feat(ios): add FormSnap enum bridging existing and pending snaps"
```

---

## Task 3: Rename `PebbleDraft.attachedSnap` → `formSnap`, prefill from detail

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`

- [ ] **Step 1: Replace the file content**

Overwrite `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`:

```swift
import Foundation

/// In-progress form state for the create- and edit-pebble sheets.
/// A value type held in `@State`. Optional fields use `nil` to mean
/// "not yet picked"; non-optionals carry sensible defaults.
struct PebbleDraft {
    var happenedAt: Date = Date()         // mandatory, "now" by default
    var name: String = ""                 // mandatory
    var description: String = ""          // optional
    var emotionId: UUID?                  // mandatory
    var domainId: UUID?                   // mandatory
    var valence: Valence?                 // mandatory
    var soulId: UUID?                     // optional
    var collectionId: UUID?               // optional
    var glyphId: UUID?                    // optional — set via GlyphPickerSheet
    var formSnap: FormSnap?               // optional — `.existing` from DB or `.pending` local upload
    var visibility: Visibility = .private // mandatory

    /// True when every mandatory field is set. Drives the Save button's disabled state.
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && emotionId != nil
        && domainId != nil
        && valence != nil
    }
}

extension PebbleDraft {
    /// Build a prefilled draft from a fetched `PebbleDetail`.
    /// Used by `EditPebbleSheet` to populate the form with the pebble's current values.
    ///
    /// Notes:
    /// - `description` defaults to empty string when the detail has no description.
    /// - `domainId` takes the first (and only expected) domain from `detail.domains`.
    ///   If `detail.domains` is unexpectedly empty, `domainId` stays nil and
    ///   `draft.isValid` will return false.
    /// - `soulId` / `collectionId` take the first element when present, nil otherwise.
    /// - `valence` is derived from `(positiveness, intensity)` by `PebbleDetail.valence`.
    /// - `formSnap` is `.existing(...)` when the detail has at least one snap, else nil
    ///   (the spec caps `max_media_per_pebble` at 1).
    init(from detail: PebbleDetail) {
        self.happenedAt = detail.happenedAt
        self.name = detail.name
        self.description = detail.description ?? ""
        self.emotionId = detail.emotion.id
        self.domainId = detail.domains.first?.id
        self.valence = detail.valence
        self.soulId = detail.souls.first?.id
        self.collectionId = detail.collections.first?.id
        self.visibility = detail.visibility
        self.glyphId = detail.glyphId
        self.formSnap = detail.snaps.first.map {
            .existing(id: $0.id, storagePath: $0.storagePath)
        }
    }
}
```

- [ ] **Step 2: Note that builds will fail until callers are updated**

The next several tasks update callers (`PebbleCreatePayload`, `PebbleUpdatePayload`, `PebbleFormView`, `CreatePebbleSheet`). Don't commit yet — leave the working tree dirty until Task 7's commit.

---

## Task 4: Update `PebbleCreatePayload` to read from `formSnap`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift:80-101`

- [ ] **Step 1: Replace the snaps mapping in `init(from:userId:)`**

In the `extension PebbleCreatePayload { init(from:userId:) ... }` block, replace the final `self.snaps = …` assignment (currently reading `draft.attachedSnap`) with:

```swift
        self.snaps = {
            switch draft.formSnap {
            case .none:
                return nil
            case .pending(let snap):
                return [SnapPayload(
                    id: snap.id,
                    storagePath: snap.storagePrefix(userId: userId),
                    sortOrder: 0
                )]
            case .existing:
                // `.existing` only appears in edit flows. Reaching this branch
                // from create is a programming error — fail loudly in debug.
                assertionFailure("PebbleCreatePayload: unexpected .existing FormSnap during create")
                return nil
            }
        }()
```

- [ ] **Step 2: Don't commit yet — see Task 7**

---

## Task 5: Update `PebbleUpdatePayload` — add `snaps`, build from `formSnap`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`

- [ ] **Step 1: Replace the file content**

```swift
import Foundation

/// The Encodable payload sent as the `payload` jsonb parameter of the
/// `update_pebble` Postgres RPC.
///
/// Shape matches what the server expects: snake_case keys, arrays for
/// domain/soul/collection links (even when the UI only allows one of each).
///
/// We always send every scalar field — the RPC uses `coalesce(payload->>..., existing)`
/// to fall back to the current value on absent keys, so sending everything is
/// both correct and simpler than tracking dirty fields on the client.
///
/// `snaps` is always sent (possibly empty) so `update_pebble`'s replace block
/// fires every save: an unchanged snap round-trips with the same `id` and
/// `storage_path`; a removed-then-not-replaced photo sends `[]` so any stale
/// row is wiped server-side as defense in depth (the eager `delete_pebble_media`
/// path should already have removed it).
struct PebbleUpdatePayload: Encodable {
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
    let glyphId: UUID?
    let snaps: [SnapPayload]

    struct SnapPayload: Encodable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder   = "sort_order"
        }
    }

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
        case glyphId = "glyph_id"
        case snaps
    }

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        // Explicit nil encoding so absent descriptions clear the field server-side.
        try container.encode(description, forKey: .description)
        // Encode Date as an ISO8601 string so Postgres' timestamptz cast accepts
        // it. The Supabase SDK's .functions.invoke() path uses an encoder whose
        // default date strategy emits Double seconds — which Postgres rejects.
        try container.encode(Self.iso8601.string(from: happenedAt), forKey: .happenedAt)
        try container.encode(intensity, forKey: .intensity)
        try container.encode(positiveness, forKey: .positiveness)
        try container.encode(visibility, forKey: .visibility)
        try container.encode(emotionId, forKey: .emotionId)
        try container.encode(domainIds, forKey: .domainIds)
        try container.encode(soulIds, forKey: .soulIds)
        try container.encode(collectionIds, forKey: .collectionIds)
        try container.encode(glyphId, forKey: .glyphId)
        try container.encode(snaps, forKey: .snaps)
    }
}

extension PebbleUpdatePayload {
    /// Build a payload from a validated draft.
    /// `userId` is needed to derive the storage_path of a `.pending` snap;
    /// `.existing` snaps already carry the path from the DB.
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft, userId: UUID) {
        precondition(draft.isValid, "PebbleUpdatePayload(from:userId:) called with invalid draft")
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
        self.glyphId = draft.glyphId
        self.snaps = {
            switch draft.formSnap {
            case .none:
                return []
            case .existing(let id, let storagePath):
                return [SnapPayload(id: id, storagePath: storagePath, sortOrder: 0)]
            case .pending(let snap):
                return [SnapPayload(
                    id: snap.id,
                    storagePath: snap.storagePrefix(userId: userId),
                    sortOrder: 0
                )]
            }
        }()
    }
}
```

- [ ] **Step 2: Don't commit yet — see Task 7**

---

## Task 6: New `ExistingSnapRow` view

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/ExistingSnapRow.swift`

- [ ] **Step 1: Write the file**

```swift
import SwiftUI
import Supabase
import os

/// Form row rendering an already-saved snap (loaded from the DB) inside the
/// edit-pebble photo section. Layout matches `AttachedPhotoView` (56×56
/// thumbnail + label + trailing button) so the section feels consistent
/// regardless of whether the snap is `.existing` or `.pending`.
///
/// Stateless besides the lazily-loaded thumb URL. The remove button calls
/// back into the parent (`EditPebbleSheet`) which owns the eager
/// `delete_pebble_media` RPC + Storage cleanup.
struct ExistingSnapRow: View {
    let storagePath: String
    let isRemoving: Bool
    let onRemove: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @State private var thumbURL: URL?
    @State private var loadFailed = false

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "existing-snap-row")

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 4) {
                Text("Photo")
                    .font(.subheadline)
                Label("Saved", systemImage: "checkmark.circle.fill")
                    .labelStyle(.titleAndIcon)
                    .font(.caption)
                    .foregroundStyle(.green)
            }
            Spacer()
            trailingButton
        }
        .task { await loadThumbURL() }
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let thumbURL {
            AsyncImage(url: thumbURL) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                case .empty:
                    placeholder
                case .failure:
                    placeholder
                @unknown default:
                    placeholder
                }
            }
        } else {
            placeholder
        }
    }

    private var placeholder: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.secondary.opacity(0.2))
            .frame(width: 56, height: 56)
    }

    @ViewBuilder
    private var trailingButton: some View {
        if isRemoving {
            ProgressView()
        } else {
            Button(role: .destructive, action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove photo")
        }
    }

    private func loadThumbURL() async {
        do {
            let urls = try await PebbleSnapRepository(client: supabase.client)
                .signedURLs(storagePrefix: storagePath)
            self.thumbURL = urls.thumb
        } catch {
            Self.logger.warning(
                "thumb URL fetch failed: \(error.localizedDescription, privacy: .private)"
            )
            self.loadFailed = true
        }
    }
}
```

- [ ] **Step 2: Don't commit yet — see Task 7**

---

## Task 7: Update `PebbleFormView` photo section + `CreatePebbleSheet` references

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift:202-216`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Replace the Photo section in `PebbleFormView`**

Replace the entire `if showsPhotoSection { ... }` block (currently lines 202–216) with:

```swift
            if showsPhotoSection {
                Section("Photo") {
                    switch draft.formSnap {
                    case .none:
                        Button {
                            photoPickerPresented = true
                        } label: {
                            Label("Add a photo", systemImage: "photo.badge.plus")
                        }
                        .listRowBackground(Color.pebblesListRow)
                    case .existing(_, let storagePath):
                        ExistingSnapRow(
                            storagePath: storagePath,
                            isRemoving: isRemovingExistingSnap,
                            onRemove: onRemoveExistingSnap
                        )
                        .listRowBackground(Color.pebblesListRow)
                    case .pending:
                        AttachedPhotoView(snap: pendingSnapBinding)
                            .listRowBackground(Color.pebblesListRow)
                    }
                }
            }
```

- [ ] **Step 2: Add the supporting properties + bindings to `PebbleFormView`**

In `PebbleFormView`, add two new stored properties and a computed binding. The init also needs the new parameters:

```swift
    /// Provided by `EditPebbleSheet` to gate the remove button while the RPC
    /// is in flight. `CreatePebbleSheet` always passes `false`.
    let isRemovingExistingSnap: Bool

    /// Triggered when the user taps remove on an `.existing` snap row.
    /// `EditPebbleSheet` runs the eager `delete_pebble_media` RPC + Storage
    /// cleanup. `CreatePebbleSheet` never sees `.existing`, so it passes a
    /// no-op closure.
    let onRemoveExistingSnap: () -> Void
```

Update the `init`:

```swift
    init(
        draft: Binding<PebbleDraft>,
        emotions: [Emotion],
        domains: [Domain],
        souls: [Soul],
        collections: [PebbleCollection],
        saveError: String?,
        renderSvg: String? = nil,
        strokeColor: String? = nil,
        renderHeight: CGFloat = 260,
        showsPhotoSection: Bool = false,
        photoPickerPresented: Binding<Bool> = .constant(false),
        isRemovingExistingSnap: Bool = false,
        onRemoveExistingSnap: @escaping () -> Void = {}
    ) {
        self._draft = draft
        self.emotions = emotions
        self.domains = domains
        self.souls = souls
        self.collections = collections
        self.saveError = saveError
        self.renderSvg = renderSvg
        self.strokeColor = strokeColor
        self.renderHeight = renderHeight
        self.showsPhotoSection = showsPhotoSection
        self._photoPickerPresented = photoPickerPresented
        self.isRemovingExistingSnap = isRemovingExistingSnap
        self.onRemoveExistingSnap = onRemoveExistingSnap
    }
```

Add the computed `pendingSnapBinding` somewhere in the struct (e.g. just above `body`):

```swift
    /// Two-way bridge between the `.pending` case of `draft.formSnap` and the
    /// `Binding<AttachedSnap?>` that `AttachedPhotoView` already speaks. Setting
    /// the binding to nil clears `formSnap`; setting it to a value re-wraps as
    /// `.pending` so the existing retry/remove `.onChange` observers in
    /// `CreatePebbleSheet` keep working unchanged.
    private var pendingSnapBinding: Binding<AttachedSnap?> {
        Binding<AttachedSnap?>(
            get: {
                if case .pending(let snap) = draft.formSnap { return snap }
                return nil
            },
            set: { newValue in
                if let newValue {
                    draft.formSnap = .pending(newValue)
                } else {
                    draft.formSnap = nil
                }
            }
        )
    }
```

- [ ] **Step 3: Update `CreatePebbleSheet` for the rename**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`, replace every `draft.attachedSnap` with the equivalent `formSnap` access. Concretely:

a) The `handlePicked` body — replace:
```swift
        draft.attachedSnap = AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading)
```
with:
```swift
        draft.formSnap = .pending(AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading))
```

b) `uploadCurrentSnap` body — replace `guard var snap = draft.attachedSnap` and the two `draft.attachedSnap = snap` lines with:
```swift
        guard case .pending(var snap) = draft.formSnap else { return }
```
…and the two assignments become:
```swift
        draft.formSnap = .pending(snap)
```
(in both the success and retry-success branches; the failed branch likewise).

c) `cancelAndCleanup` — replace `draft.attachedSnap = nil` with `draft.formSnap = nil`.

d) The two `.onChange` observers:

```swift
        .onChange(of: pendingSnapState) { oldState, newState in
            if oldState == .failed,
               newState == .uploading,
               let processed = processedForRetry,
               let userId = currentUserId,
               case .pending(let snap) = draft.formSnap {
                Task { await uploadCurrentSnap(processed: processed, userId: userId) }
                _ = snap // silence unused-let in case the body changes
            }
        }
        .onChange(of: pendingSnapId) { oldId, newId in
            guard let oldId, newId == nil, let userId = currentUserId else { return }
            processedForRetry = nil
            Task { await snapRepo.deleteFiles(snapId: oldId, userId: userId) }
        }
```

…with these supporting computed properties on the view:

```swift
    private var pendingSnapState: AttachedSnap.UploadState? {
        if case .pending(let snap) = draft.formSnap { return snap.state }
        return nil
    }

    private var pendingSnapId: UUID? {
        if case .pending(let snap) = draft.formSnap { return snap.id }
        return nil
    }
```

e) `save()` blocks that read `draft.attachedSnap` — replace with the `case .pending(let snap)` extraction:

```swift
        if case .pending(let snap) = draft.formSnap, snap.state == .uploading {
            logger.notice("save blocked: snap still uploading")
            saveError = "Photo is still uploading."
            return
        }
        if case .pending(let snap) = draft.formSnap, snap.state == .failed {
            logger.notice("save blocked: snap upload failed")
            saveError = "Photo upload failed. Retry or remove it."
            return
        }
```

f) `handleSaveFailure` — replace `if let userId = currentUserId, let snap = draft.attachedSnap` with:

```swift
        if let userId = currentUserId,
           case .pending(let snap) = draft.formSnap {
            await snapRepo.deleteFiles(snapId: snap.id, userId: userId)
        }
```

g) Lift `userMessage(for:)` to a module-private helper at the bottom of the file (so `EditPebbleSheet` can reuse it in Task 8). Move the existing `userMessage(for:)` method out of the struct:

```swift
/// Maps a thrown error to a user-facing localized string. Module-private so
/// `CreatePebbleSheet` and `EditPebbleSheet` share one mapping.
func userMessageForPebbleSaveError(_ error: Error) -> String {
    if let fnError = error as? FunctionsError, case let .httpError(_, data) = fnError,
       let body = try? JSONDecoder().decode([String: String].self, from: data) {
        let message = body["error"] ?? body["message"] ?? ""
        if message.contains("media_quota_exceeded") || message.contains("P0001") {
            return "Photo limit reached on this pebble."
        }
    }
    if let pipelineError = error as? ImagePipelineError {
        switch pipelineError {
        case .unsupportedFormat:    return "That image format isn't supported."
        case .decodeFailed:         return "Couldn't read the image."
        case .encodeFailed:         return "Couldn't process the image."
        case .tooLargeAfterResize:  return "That image is too large to attach."
        }
    }
    return "Couldn't save your pebble. Please try again."
}
```

Replace every call to `userMessage(for: …)` inside `CreatePebbleSheet` with `userMessageForPebbleSaveError(…)` and delete the original method.

- [ ] **Step 4: Build to verify everything compiles**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build CODE_SIGNING_ALLOWED=NO
```

Expected: build succeeds.

- [ ] **Step 5: Commit Tasks 2–7**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Models/FormSnap.swift \
        apps/ios/Pebbles/Features/PebbleMedia/ExistingSnapRow.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift \
        apps/ios/Pebbles/Features/Path/PebbleFormView.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "feat(ios): introduce FormSnap and existing-snap row, prep for edit photos"
```

---

## Task 8: Wire `EditPebbleSheet` (load snaps, host picker, eager remove, payload userId)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

- [ ] **Step 1: Extend the load select to include snaps**

Replace the `detailQuery` select string in `EditPebbleSheet.load()` to add `snaps`:

```swift
            async let detailQuery: PebbleDetail = supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    emotion:emotions(id, slug, name, color),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id)),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value
```

- [ ] **Step 2: Add picker + remove state and helpers**

Add these properties to `EditPebbleSheet`:

```swift
    @State private var isPhotoPickerPresented = false
    @State private var processedForRetry: ProcessedImage?
    @State private var isRemovingExistingSnap = false

    private var snapRepo: PebbleSnapRepository {
        PebbleSnapRepository(client: supabase.client)
    }

    private var currentUserId: UUID? {
        supabase.session?.user.id
    }

    private var pendingSnapState: AttachedSnap.UploadState? {
        if case .pending(let snap) = draft.formSnap { return snap.state }
        return nil
    }

    private var pendingSnapId: UUID? {
        if case .pending(let snap) = draft.formSnap { return snap.id }
        return nil
    }
```

- [ ] **Step 3: Pass picker host + remove handler to `PebbleFormView`**

Replace the `PebbleFormView(...)` call site in `content`:

```swift
            PebbleFormView(
                draft: $draft,
                emotions: emotions,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                renderSvg: renderSvg,
                strokeColor: strokeColor,
                renderHeight: sizeGroup.renderHeight,
                showsPhotoSection: true,
                photoPickerPresented: $isPhotoPickerPresented,
                isRemovingExistingSnap: isRemovingExistingSnap,
                onRemoveExistingSnap: {
                    Task { await removeExistingSnap() }
                }
            )
```

- [ ] **Step 4: Attach the picker sheet + onChange observers**

In `body`, after the `NavigationStack { ... }.task { await load() }`, add:

```swift
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked {
                    Task { await handlePicked(picked) }
                }
            }
        }
        .onChange(of: pendingSnapState) { oldState, newState in
            if oldState == .failed,
               newState == .uploading,
               let processed = processedForRetry,
               let userId = currentUserId {
                Task { await uploadCurrentSnap(processed: processed, userId: userId) }
            }
        }
        .onChange(of: pendingSnapId) { oldId, newId in
            guard let oldId, newId == nil, let userId = currentUserId else { return }
            processedForRetry = nil
            Task { await snapRepo.deleteFiles(snapId: oldId, userId: userId) }
        }
```

- [ ] **Step 5: Add the picker / upload pipeline copied from `CreatePebbleSheet`**

Add these methods to `EditPebbleSheet`:

```swift
    private func handlePicked(_ picked: PhotoPickerView.PickedItem) async {
        logger.notice("handlePicked: started uti=\(picked.uti, privacy: .public)")

        guard let userId = currentUserId else {
            logger.error("handlePicked: no current user id")
            return
        }

        let data: Data
        do {
            data = try await loadData(from: picked.itemProvider, uti: picked.uti)
            logger.notice("handlePicked: loaded \(data.count, privacy: .public) bytes")
        } catch {
            logger.error("picker data load failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't read the image."
            return
        }

        let processed: ProcessedImage
        let uti = picked.uti
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(data, uti: uti)
            }.value
        } catch {
            logger.error("image pipeline failed: \(String(describing: error), privacy: .public)")
            saveError = userMessageForPebbleSaveError(error)
            return
        }

        let snapId = UUID()
        draft.formSnap = .pending(
            AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading)
        )
        processedForRetry = processed

        await uploadCurrentSnap(processed: processed, userId: userId)
    }

    private func loadData(from provider: NSItemProvider, uti: String) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: uti) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: URLError(.cannotDecodeContentData))
                }
            }
        }
    }

    private func uploadCurrentSnap(processed: ProcessedImage, userId: UUID) async {
        guard case .pending(var snap) = draft.formSnap else { return }
        logger.notice("uploadCurrentSnap: started snap=\(snap.id, privacy: .public)")

        do {
            try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
            logger.notice("uploadCurrentSnap: success snap=\(snap.id, privacy: .public)")
            snap.state = .uploaded
            draft.formSnap = .pending(snap)
        } catch {
            logger.warning("snap upload failed (first attempt): \(error.localizedDescription, privacy: .private)")
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            do {
                try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
                snap.state = .uploaded
                draft.formSnap = .pending(snap)
            } catch {
                logger.error("snap upload failed (retry): \(error.localizedDescription, privacy: .private)")
                snap.state = .failed
                draft.formSnap = .pending(snap)
            }
        }
    }
```

- [ ] **Step 6: Add the eager remove handler**

```swift
    /// Tap-X handler for an `.existing` snap row. Calls `delete_pebble_media`
    /// to commit the removal in the DB, then fires fire-and-forget Storage
    /// cleanup using the returned `storage_path`. Cancel does not undo this —
    /// see the design spec.
    private func removeExistingSnap() async {
        guard case .existing(let id, _) = draft.formSnap else { return }
        isRemovingExistingSnap = true
        defer { isRemovingExistingSnap = false }

        do {
            let storagePath: String = try await supabase.client
                .rpc("delete_pebble_media", params: ["p_snap_id": id])
                .execute()
                .value
            // Fire-and-forget Storage cleanup. Logged on failure inside the helper.
            await snapRepo.deleteFiles(storagePrefix: storagePath)
            draft.formSnap = nil
        } catch {
            logger.error("delete_pebble_media failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't remove the photo. Please try again."
        }
    }
```

- [ ] **Step 7: Add a `deleteFiles(storagePrefix:)` overload to `PebbleSnapRepository`**

In `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`, add right under the existing `deleteFiles(snapId:userId:)`:

```swift
    /// Best-effort cleanup variant for callers that already have the
    /// `storage_path` string (e.g. from `delete_pebble_media`).
    func deleteFiles(storagePrefix prefix: String) async {
        let originalPath = "\(prefix)/original.jpg"
        let thumbPath    = "\(prefix)/thumb.jpg"
        do {
            _ = try await client.storage.from(Self.bucketId)
                .remove(paths: [originalPath, thumbPath])
        } catch {
            Self.logger.error(
                "snap delete failed for prefix \(prefix, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
        }
    }
```

- [ ] **Step 8: Update `save()` to pass `userId` and gate on pending state**

Replace the `save()` body (preserving the existing soft-success / FunctionsError handling) with this version:

```swift
    private func save() async {
        guard draft.isValid else { return }

        if case .pending(let snap) = draft.formSnap, snap.state == .uploading {
            logger.notice("save blocked: snap still uploading")
            saveError = "Photo is still uploading."
            return
        }
        if case .pending(let snap) = draft.formSnap, snap.state == .failed {
            logger.notice("save blocked: snap upload failed")
            saveError = "Photo upload failed. Retry or remove it."
            return
        }

        guard let userId = currentUserId else {
            logger.error("save: no current user id")
            saveError = "You must be signed in to save."
            return
        }

        isSaving = true
        saveError = nil

        let payload = PebbleUpdatePayload(from: draft, userId: userId)
        let requestBody = ComposePebbleUpdateRequest(pebbleId: pebbleId, payload: payload)

        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let response: ComposePebbleResponse = try await supabase.client.functions
                .invoke(
                    "compose-pebble-update",
                    options: FunctionInvokeOptions(body: requestBody),
                    decoder: decoder
                )
            self.renderSvg = response.renderSvg ?? self.renderSvg
            onSaved()
            dismiss()
        } catch let functionsError as FunctionsError {
            if case .httpError(let status, let data) = functionsError, status >= 500 {
                let bodyString = String(data: data, encoding: .utf8) ?? "<non-utf8 body>"
                logger.warning("compose-pebble-update returned \(status, privacy: .public) — advancing on soft-success. body=\(bodyString, privacy: .private)")
                onSaved()
                dismiss()
            } else {
                let bodyString: String
                if case .httpError(_, let data) = functionsError {
                    bodyString = String(data: data, encoding: .utf8) ?? "<non-utf8 body>"
                } else {
                    bodyString = "<non-http error>"
                }
                logger.error("compose-pebble-update failed: \(functionsError.localizedDescription, privacy: .private) body=\(bodyString, privacy: .private)")
                self.saveError = userMessageForPebbleSaveError(functionsError)
                self.isSaving = false
            }
        } catch {
            logger.error("update pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = userMessageForPebbleSaveError(error)
            self.isSaving = false
        }
    }
```

- [ ] **Step 9: Build**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build CODE_SIGNING_ALLOWED=NO
```

Expected: build succeeds.

- [ ] **Step 10: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift \
        apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift
git commit -m "feat(ios): wire attach, replace, and eager remove in edit pebble sheet"
```

---

## Task 9: Lint, build, manual QA, and Arkaik map check

- [ ] **Step 1: Lint and full build at workspace level**

```bash
npm run lint --workspace=@pbbls/ios
npm run build --workspace=@pbbls/ios
```

Expected: both pass.

- [ ] **Step 2: Localization strings audit**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode (or grep) and confirm the new user-facing strings are present in both `en` and `fr`:
- `"Add a photo"` (already present from V1)
- `"Photo"` (already present)
- `"Saved"` (new — add for `ExistingSnapRow`)
- `"Remove photo"` (already present)
- `"Couldn't remove the photo. Please try again."` (new)

For each missing string, add the key with `en` + `fr` translations. No `New` or `Stale` rows allowed before merge.

- [ ] **Step 3: Run on a simulator and walk the QA matrix**

```bash
open apps/ios/Pebbles.xcodeproj
```

Then run the app and exercise:
1. Pebble with photo → open edit → confirm thumbnail loads → Save unchanged → confirm photo still present after re-open.
2. Pebble with photo → tap remove → confirm thumb disappears + Storage row gone (verify in Supabase dashboard or `select * from snaps where pebble_id = ...`).
3. Pebble with photo → remove → add new pick → Save → confirm new snap row, old Storage files cleaned.
4. Pebble without photo → add → Save → confirm new snap row.
5. Pebble without photo → add → Cancel sheet → confirm Storage cleaned.
6. Quota error path: temporarily set `update profiles set max_media_per_pebble = 0 where id = '<your-uid>'`, then try to save with a photo attached → confirm "Photo limit reached on this pebble." renders. Restore `max_media_per_pebble = 1` afterward.

- [ ] **Step 4: Arkaik map**

Read `.claude/skills/arkaik/` and update `docs/arkaik/bundle.json` only if this change affects screens, flows, or models. (Likely no-op: edit pebble already exists, the data model is unchanged. Verify per the skill's guidance.)

- [ ] **Step 5: Final commit if anything was touched in steps 2 or 4**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings docs/arkaik/bundle.json
git commit -m "chore(ios): localize new edit-photo strings"
```

(Skip the commit if neither file changed.)

---

## Task 10: Open the PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/323-ios-edit-pebble-photo
```

- [ ] **Step 2: Create the PR**

```bash
gh pr create --title "feat(core): support attaching, replacing, and removing photos in edit pebble" --body "$(cat <<'EOF'
Resolves #323

## Summary
- Extends `EditPebbleSheet` with the photo section, picker host, and eager-remove flow (parity with `CreatePebbleSheet`).
- Introduces `FormSnap` to bridge `.existing` (already in DB) and `.pending` (in-flight upload) states; `PebbleDraft.attachedSnap` becomes `formSnap`.
- New `delete_pebble_media(snap_id)` RPC commits removals immediately and returns `storage_path` for client-side Storage cleanup.
- `update_pebble` now accepts `snaps[].id` (so unchanged snaps round-trip) and enforces `profiles.max_media_per_pebble`, mirroring `create_pebble`.

## Key files
- `packages/supabase/supabase/migrations/20260426000002_pebble_media_edit.sql`
- `packages/supabase/types/database.ts`
- `apps/ios/Pebbles/Features/PebbleMedia/Models/FormSnap.swift`
- `apps/ios/Pebbles/Features/PebbleMedia/ExistingSnapRow.swift`
- `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`
- `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`
- `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`
- `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`
- `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`

## Test plan
- [ ] Pebble with photo: open edit → thumb loads → Save unchanged → photo still attached
- [ ] Pebble with photo: remove → Save → snap row gone, Storage cleaned
- [ ] Pebble with photo: remove → add new pick → Save → new snap row, old Storage gone
- [ ] Pebble without photo: add → Save → new snap row
- [ ] Pebble without photo: add → Cancel → Storage cleaned
- [ ] Quota error path: with `max_media_per_pebble = 0`, attempt save → "Photo limit reached on this pebble." renders
EOF
)"
```

- [ ] **Step 3: Apply labels and milestone**

The issue is labelled `feat`, `core`, `ios` and milestoned `M25 · Improved core UX`. Apply the same to the PR (per project guidelines, `feat` species label carries over):

```bash
gh pr edit --add-label "feat,core,ios" --milestone "M25 · Improved core UX"
```

Confirm with the user before running this command.

---

## Self-review

**1. Spec coverage:**
- ✅ Backend `update_pebble` accepts `snaps[].id` + quota — Task 1.
- ✅ `delete_pebble_media` RPC — Task 1.
- ✅ Type regen — Task 1 step 2.
- ✅ `FormSnap` enum — Task 2.
- ✅ `PebbleDraft.formSnap` rename + prefill — Task 3.
- ✅ `PebbleCreatePayload` updated — Task 4.
- ✅ `PebbleUpdatePayload` adds snaps — Task 5.
- ✅ `ExistingSnapRow` — Task 6.
- ✅ `PebbleFormView` switch dispatch — Task 7.
- ✅ `CreatePebbleSheet` reference updates + lifted `userMessage` helper — Task 7.
- ✅ `EditPebbleSheet` extended select, picker host, eager remove, `userId` in payload — Task 8.
- ✅ `deleteFiles(storagePrefix:)` Storage helper — Task 8.
- ✅ Localization audit — Task 9.
- ✅ Manual QA matrix from spec — Task 9.

**2. Placeholder scan:** No "TBD"/"TODO"/"similar to". Each step shows full code for changes it makes.

**3. Type consistency:** `FormSnap` cases used identically across Tasks 3–8. `pendingSnapBinding` defined in Task 7 step 2, referenced in same task. `userMessageForPebbleSaveError` defined Task 7 step 3, used in Task 8 step 8. `deleteFiles(storagePrefix:)` defined Task 8 step 7, used in same task step 6.
