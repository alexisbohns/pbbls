# iOS — Edit a pebble · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow a logged-in user to tap a pebble in the path, edit any of its fields in a prefilled sheet, and save the edits atomically via the `update_pebble` RPC.

**Architecture:** Extract the pebble form body into a shared `PebbleFormView` that both `CreatePebbleSheet` and a new `EditPebbleSheet` render. The edit sheet loads the pebble via the same PostgREST query used by the (now-deleted) `PebbleDetailSheet`, builds a prefilled `PebbleDraft`, and on save calls the `update_pebble` RPC — extended in this PR to handle `collection_ids` / `new_collections` and to verify ownership of every linked collection. The `create_pebble` RPC gets the same collection support for symmetry (no UI caller yet).

**Tech Stack:** SwiftUI (iOS 17+), Swift Testing, Supabase Swift SDK, Postgres (PL/pgSQL `security definer` RPCs), PostgREST.

**Spec:** `docs/superpowers/specs/2026-04-15-ios-edit-pebble-design.md`

**Branch:** `feat/255-edit-pebble` (already checked out)

---

## File Structure

### Created

| Path | Responsibility |
|---|---|
| `packages/supabase/supabase/migrations/20260415000000_pebble_rpc_collections.sql` | Extends `create_pebble` and `update_pebble` with `collection_ids` / `new_collections` support and a collection-ownership check. |
| `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` | Pure-UI `Form` body shared by create and edit sheets. Takes `@Binding PebbleDraft` and reference lists. |
| `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` | Thin sheet: loads pebble + refs, owns prefilled draft, calls `update_pebble` RPC on save. |
| `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift` | Encodable payload matching the `update_pebble` RPC's jsonb shape. |
| `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift` | Tests for `PebbleDraft.init(from: PebbleDetail)`. |
| `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift` | Tests for the RPC payload encoding. |

### Modified

| Path | Change |
|---|---|
| `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift` | Add `init(from: PebbleDetail)`. |
| `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` | Render `PebbleFormView` instead of inline `Form`. No behavioral change. |
| `apps/ios/Pebbles/Features/Path/PathView.swift` | Present `EditPebbleSheet` (not `PebbleDetailSheet`) on row tap; refetch list on save. |
| `packages/supabase/types/database.ts` | Regenerated after migration applies. |

### Deleted

| Path | Reason |
|---|---|
| `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` | Superseded by `EditPebbleSheet` (the edit sheet IS the detail view — decision #1 in the spec). |

---

## Task 1: Write the new Supabase migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260415000000_pebble_rpc_collections.sql`

**Why:** Both `create_pebble` and `update_pebble` currently ignore collections entirely — a correctness bug. We also need an explicit ownership check on `collection_ids` because the RPCs are `security definer` and bypass RLS. Extending both RPCs in one migration keeps them symmetric.

- [ ] **Step 1: Create the migration file with full `create or replace function` bodies for both RPCs**

The migration replaces both function bodies in place. We copy each existing function body from `20260411000003_rpc_functions.sql` (`create_pebble`) and `20260411000005_security_hardening.sql` (`update_pebble`), then add the collection handling and ownership check.

```sql
-- Migration: extend create_pebble and update_pebble with collection support
--
-- Adds `collection_ids` (array) and `new_collections` (array of {name}) payload keys
-- to both RPCs, mirroring the existing soul/domain pattern. Also adds an explicit
-- ownership check on every collection ID in the payload, because the RPCs are
-- `security definer` and bypass RLS.

-- ============================================================
-- create_pebble — adds collection handling
-- ============================================================

create or replace function public.create_pebble(payload jsonb)
returns uuid as $$
declare
  v_user_id uuid := auth.uid();
  v_pebble_id uuid;
  v_glyph_id uuid;
  v_soul_ids uuid[];
  v_collection_ids uuid[];
  v_new_soul record;
  v_new_soul_id uuid;
  v_new_collection record;
  v_new_collection_id uuid;
  v_card record;
  v_snap record;
  v_karma int;
  v_cards_count int;
  v_souls_count int;
  v_domains_count int;
  v_snaps_count int;
  v_unauthorized_collection uuid;
begin
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
  else
    v_glyph_id := (payload->>'glyph_id')::uuid;
  end if;

  -- Collect existing soul IDs
  select array_agg(val::uuid)
  into v_soul_ids
  from jsonb_array_elements_text(coalesce(payload->'soul_ids', '[]'::jsonb)) val;

  -- Inline soul creation
  if payload ? 'new_souls' then
    for v_new_soul in select * from jsonb_array_elements(payload->'new_souls')
    loop
      insert into public.souls (user_id, name)
      values (v_user_id, v_new_soul.value->>'name')
      returning id into v_new_soul_id;

      v_soul_ids := array_append(v_soul_ids, v_new_soul_id);
    end loop;
  end if;

  -- Collect existing collection IDs
  select array_agg(val::uuid)
  into v_collection_ids
  from jsonb_array_elements_text(coalesce(payload->'collection_ids', '[]'::jsonb)) val;

  -- Inline collection creation (appended to v_collection_ids after creation)
  if payload ? 'new_collections' then
    for v_new_collection in select * from jsonb_array_elements(payload->'new_collections')
    loop
      insert into public.collections (user_id, name)
      values (v_user_id, v_new_collection.value->>'name')
      returning id into v_new_collection_id;

      v_collection_ids := array_append(v_collection_ids, v_new_collection_id);
    end loop;
  end if;

  -- Ownership check: every collection ID must belong to the current user.
  -- Newly-created ones trivially pass (they were inserted above with user_id = v_user_id).
  if v_collection_ids is not null then
    select c_id into v_unauthorized_collection
    from unnest(v_collection_ids) as c_id
    where not exists (
      select 1 from public.collections
      where id = c_id and user_id = v_user_id
    )
    limit 1;

    if v_unauthorized_collection is not null then
      raise exception 'Collection not owned by user: %', v_unauthorized_collection;
    end if;
  end if;

  -- Create the pebble
  insert into public.pebbles (
    user_id, name, description, happened_at,
    intensity, positiveness, visibility,
    emotion_id, glyph_id
  )
  values (
    v_user_id,
    payload->>'name',
    payload->>'description',
    (payload->>'happened_at')::timestamptz,
    (payload->>'intensity')::smallint,
    (payload->>'positiveness')::smallint,
    coalesce(payload->>'visibility', 'private'),
    (payload->>'emotion_id')::uuid,
    v_glyph_id
  )
  returning id into v_pebble_id;

  -- Insert cards
  v_cards_count := 0;
  if payload ? 'cards' then
    for v_card in select * from jsonb_array_elements(payload->'cards')
    loop
      insert into public.pebble_cards (pebble_id, species_id, value, sort_order)
      values (
        v_pebble_id,
        (v_card.value->>'species_id')::uuid,
        v_card.value->>'value',
        coalesce((v_card.value->>'sort_order')::smallint, 0)
      );
      v_cards_count := v_cards_count + 1;
    end loop;
  end if;

  -- Insert pebble_souls
  v_souls_count := 0;
  if v_soul_ids is not null then
    insert into public.pebble_souls (pebble_id, soul_id)
    select v_pebble_id, unnest(v_soul_ids);
    v_souls_count := array_length(v_soul_ids, 1);
  end if;

  -- Insert pebble_domains
  v_domains_count := 0;
  if payload ? 'domain_ids' then
    insert into public.pebble_domains (pebble_id, domain_id)
    select v_pebble_id, (val::text)::uuid
    from jsonb_array_elements_text(payload->'domain_ids') val;
    v_domains_count := jsonb_array_length(payload->'domain_ids');
  end if;

  -- Insert collection_pebbles
  if v_collection_ids is not null then
    insert into public.collection_pebbles (collection_id, pebble_id)
    select unnest(v_collection_ids), v_pebble_id;
  end if;

  -- Insert snaps
  v_snaps_count := 0;
  if payload ? 'snaps' then
    for v_snap in select * from jsonb_array_elements(payload->'snaps')
    loop
      insert into public.snaps (pebble_id, user_id, storage_path, sort_order)
      values (
        v_pebble_id,
        v_user_id,
        v_snap.value->>'storage_path',
        coalesce((v_snap.value->>'sort_order')::smallint, 0)
      );
      v_snaps_count := v_snaps_count + 1;
    end loop;
  end if;

  -- Compute and insert karma
  v_karma := public.compute_karma_delta(
    payload->>'description',
    v_cards_count,
    v_souls_count,
    v_domains_count,
    v_glyph_id is not null,
    v_snaps_count
  );

  insert into public.karma_events (user_id, delta, reason, ref_id)
  values (v_user_id, v_karma, 'pebble_created', v_pebble_id);

  return v_pebble_id;
end;
$$ language plpgsql security definer set search_path = public;

-- ============================================================
-- update_pebble — adds collection handling
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

  -- Replace snaps
  if payload ? 'snaps' then
    delete from public.snaps where pebble_id = p_pebble_id;

    for v_snap in select * from jsonb_array_elements(payload->'snaps')
    loop
      insert into public.snaps (pebble_id, user_id, storage_path, sort_order)
      values (
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
```

- [ ] **Step 2: Reset local DB to apply the migration**

Run: `npm run db:reset --workspace=packages/supabase`
Expected: all migrations apply cleanly, including the new one. No errors.

If the reset fails, read the error carefully — it's almost certainly a syntax slip in the migration above. Fix it in place before moving on.

- [ ] **Step 3: Regenerate TypeScript types**

Run: `npm run db:types --workspace=packages/supabase`
Expected: `packages/supabase/types/database.ts` is updated. The function signatures for `create_pebble` and `update_pebble` don't change (they still take `payload jsonb`), so the diff may be minimal — but regenerate anyway per `AGENTS.md`.

- [ ] **Step 4: Stage and commit the migration + regenerated types**

```bash
git add packages/supabase/supabase/migrations/20260415000000_pebble_rpc_collections.sql \
        packages/supabase/types/database.ts
git commit -m "$(cat <<'EOF'
feat(db): add collection support to create/update pebble RPCs

Extends create_pebble and update_pebble with collection_ids and new_collections
payload keys, and adds a security-definer-bypassing ownership check on every
collection ID in the final payload.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add `PebbleDraft.init(from: PebbleDetail)`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`
- Test: `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift`

**Why:** The edit sheet fetches a `PebbleDetail` (read model) but the form binds to a `PebbleDraft` (edit model). This convenience initializer converts between them. TDD: write the tests first.

- [ ] **Step 1: Create the test file with failing tests**

Create `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDraft init(from: PebbleDetail)")
struct PebbleDraftFromDetailTests {

    private func makeDetail(
        name: String = "Shipped",
        description: String? = "Finally.",
        positiveness: Int = 1,
        intensity: Int = 3,
        visibility: Visibility = .private,
        emotionId: UUID = UUID(),
        domains: [DomainRef] = [DomainRef(id: UUID(), name: "Work")],
        souls: [Soul] = [],
        collections: [PebbleCollection] = []
    ) -> PebbleDetail {
        // Build JSON and decode — mirrors how PebbleDetail is actually constructed.
        // This is less fragile than trying to hand-construct the struct through a memberwise init
        // that doesn't exist (PebbleDetail uses a custom `init(from: Decoder)`).
        let emotionJSON: [String: Any] = [
            "id": emotionId.uuidString,
            "name": "Joy",
            "color": "#FFD166"
        ]
        let domainsJSON = domains.map { d in ["domain": ["id": d.id.uuidString, "name": d.name]] }
        let soulsJSON = souls.map { s in ["soul": ["id": s.id.uuidString, "name": s.name]] }
        let collectionsJSON = collections.map { c in ["collection": ["id": c.id.uuidString, "name": c.name]] }

        var root: [String: Any] = [
            "id": UUID().uuidString,
            "name": name,
            "happened_at": "2026-04-14T15:42:00Z",
            "intensity": intensity,
            "positiveness": positiveness,
            "visibility": visibility.rawValue,
            "emotion": emotionJSON,
            "pebble_domains": domainsJSON,
            "pebble_souls": soulsJSON,
            "collection_pebbles": collectionsJSON
        ]
        if let description { root["description"] = description }

        let data = try! JSONSerialization.data(withJSONObject: root)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            guard let d = formatter.date(from: s) else {
                throw DecodingError.dataCorruptedError(in: c, debugDescription: "bad date")
            }
            return d
        }
        return try! decoder.decode(PebbleDetail.self, from: data)
    }

    @Test("populates all fields from a fully-populated detail")
    func fullyPopulated() {
        let emotionId = UUID()
        let domainId = UUID()
        let soulId = UUID()
        let collectionId = UUID()

        let detail = makeDetail(
            name: "Shipped",
            description: "Finally.",
            positiveness: 1,
            intensity: 3,
            visibility: .friends,
            emotionId: emotionId,
            domains: [DomainRef(id: domainId, name: "Work")],
            souls: [Soul(id: soulId, name: "Me")],
            collections: [PebbleCollection(id: collectionId, name: "Wins")]
        )

        let draft = PebbleDraft(from: detail)

        #expect(draft.name == "Shipped")
        #expect(draft.description == "Finally.")
        #expect(draft.happenedAt == detail.happenedAt)
        #expect(draft.emotionId == emotionId)
        #expect(draft.domainId == domainId)
        #expect(draft.soulId == soulId)
        #expect(draft.collectionId == collectionId)
        #expect(draft.valence == .highlightLarge)
        #expect(draft.visibility == .friends)
    }

    @Test("maps nil description to empty string")
    func nilDescription() {
        let detail = makeDetail(description: nil)
        let draft = PebbleDraft(from: detail)
        #expect(draft.description == "")
    }

    @Test("leaves soulId nil when no souls")
    func noSouls() {
        let detail = makeDetail(souls: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.soulId == nil)
    }

    @Test("leaves collectionId nil when no collections")
    func noCollections() {
        let detail = makeDetail(collections: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.collectionId == nil)
    }

    @Test("leaves domainId nil and draft invalid when domains is empty")
    func emptyDomains() {
        let detail = makeDetail(domains: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.domainId == nil)
        #expect(draft.isValid == false)
    }

    @Test("derives valence from positiveness and intensity pair")
    func valenceMapping() {
        let detail = makeDetail(positiveness: -1, intensity: 2)
        let draft = PebbleDraft(from: detail)
        #expect(draft.valence == .lowlightMedium)
    }
}
```

- [ ] **Step 2: Run the tests — they should fail to compile**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test -only-testing:PebblesTests/PebbleDraftFromDetailTests 2>&1 | tail -30`
Expected: compile error — `PebbleDraft` has no `init(from: PebbleDetail)`.

If `Pebbles.xcodeproj` does not exist, first run `npm run generate --workspace=@pbbls/ios` (per `apps/ios/CLAUDE.md`: `project.yml` is the source of truth, `.xcodeproj` is a build artifact).

- [ ] **Step 3: Add the initializer**

Append to `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`:

```swift
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
    }
}
```

- [ ] **Step 4: Run the tests — all should pass**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test -only-testing:PebblesTests/PebbleDraftFromDetailTests 2>&1 | tail -30`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift \
        apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add PebbleDraft init from PebbleDetail

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create `PebbleUpdatePayload` with tests

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`
- Test: `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`

**Why:** We need an `Encodable` type whose JSON shape matches the `update_pebble` RPC's `payload jsonb` parameter. Single-valued pickers (domain, soul, collection) wrap into arrays at this boundary.

- [ ] **Step 1: Write the failing encoding tests**

Create `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleUpdatePayload encoding")
struct PebbleUpdatePayloadEncodingTests {

    private func encode(_ payload: PebbleUpdatePayload) throws -> [String: Any] {
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
        let json = try encode(PebbleUpdatePayload(from: draft))

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
        let json = try encode(PebbleUpdatePayload(from: draft))

        let ids = json["domain_ids"] as? [String] ?? []
        #expect(ids.count == 1)
        #expect(ids.first == draft.domainId?.uuidString)
    }

    @Test("soul_ids is empty array when soulId is nil")
    func emptySoulIds() throws {
        let draft = makeValidDraft(soulId: nil)
        let json = try encode(PebbleUpdatePayload(from: draft))

        let ids = json["soul_ids"] as? [String] ?? ["not-empty"]
        #expect(ids.isEmpty)
    }

    @Test("soul_ids is single-element array when soulId is set")
    func singleSoulId() throws {
        let soulId = UUID()
        let draft = makeValidDraft(soulId: soulId)
        let json = try encode(PebbleUpdatePayload(from: draft))

        let ids = json["soul_ids"] as? [String] ?? []
        #expect(ids == [soulId.uuidString])
    }

    @Test("collection_ids follows the same pattern as soul_ids")
    func collectionIds() throws {
        let collectionId = UUID()
        let draftWith = makeValidDraft(collectionId: collectionId)
        let jsonWith = try encode(PebbleUpdatePayload(from: draftWith))
        #expect((jsonWith["collection_ids"] as? [String]) == [collectionId.uuidString])

        let draftWithout = makeValidDraft(collectionId: nil)
        let jsonWithout = try encode(PebbleUpdatePayload(from: draftWithout))
        #expect((jsonWithout["collection_ids"] as? [String])?.isEmpty == true)
    }

    @Test("description encodes as null when empty-string-trimmed")
    func emptyDescriptionBecomesNull() throws {
        var draft = makeValidDraft()
        draft.description = "   "
        let json = try encode(PebbleUpdatePayload(from: draft))
        #expect(json["description"] is NSNull)
    }
}
```

- [ ] **Step 2: Run the tests — they should fail to compile**

Run: `xcodebuild ... test -only-testing:PebblesTests/PebbleUpdatePayloadEncodingTests 2>&1 | tail -30`
Expected: compile error — `PebbleUpdatePayload` does not exist.

- [ ] **Step 3: Create the payload type**

Create `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`:

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
        // Explicit nil encoding so absent descriptions clear the field server-side.
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

extension PebbleUpdatePayload {
    /// Build a payload from a validated draft.
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft) {
        precondition(draft.isValid, "PebbleUpdatePayload(from:) called with invalid draft")
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

**Note on the test for `scalarKeys`:** `valence == .highlightLarge` in `makeValidDraft` maps to `intensity = 3, positiveness = 1`. If `Valence.highlightLarge` has different values in `Valence.swift`, adjust the test's expected `intensity` and `positiveness`. Verify by reading `apps/ios/Pebbles/Features/Path/Models/Valence.swift` before running the tests.

- [ ] **Step 4: Run the tests — all should pass**

Run: `xcodebuild ... test -only-testing:PebblesTests/PebbleUpdatePayloadEncodingTests 2>&1 | tail -30`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift \
        apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add PebbleUpdatePayload for update_pebble RPC

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Extract `PebbleFormView` from `CreatePebbleSheet`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

**Why:** `PebbleFormView` becomes the single source of truth for the pebble form UI. Both create and edit sheets render it. This refactor was explicitly approved during brainstorming (spec decision #5).

- [ ] **Step 1: Create `PebbleFormView.swift`**

Create `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`:

```swift
import SwiftUI

/// The pebble `Form` body, shared by `CreatePebbleSheet` and `EditPebbleSheet`.
///
/// Pure UI: takes a binding to a `PebbleDraft` and the four reference lists.
/// Knows nothing about Supabase, save/insert semantics, or which sheet is
/// presenting it. The optional `saveError` row is rendered inline so both
/// sheets can surface save failures the same way.
struct PebbleFormView: View {
    @Binding var draft: PebbleDraft
    let emotions: [Emotion]
    let domains: [Domain]
    let souls: [Soul]
    let collections: [PebbleCollection]
    let saveError: String?

    var body: some View {
        Form {
            Section {
                DatePicker(
                    "When",
                    selection: $draft.happenedAt,
                    displayedComponents: [.date, .hourAndMinute]
                )

                TextField("Name", text: $draft.name)

                TextField("Description (optional)", text: $draft.description, axis: .vertical)
                    .lineLimit(1...5)
            }

            Section("Mood") {
                Picker("Emotion", selection: $draft.emotionId) {
                    Text("Choose…").tag(UUID?.none)
                    ForEach(emotions) { emotion in
                        Text(emotion.name).tag(UUID?.some(emotion.id))
                    }
                }

                Picker("Domain", selection: $draft.domainId) {
                    Text("Choose…").tag(UUID?.none)
                    ForEach(domains) { domain in
                        Text(domain.name).tag(UUID?.some(domain.id))
                    }
                }

                Picker("Valence", selection: $draft.valence) {
                    Text("Choose…").tag(Valence?.none)
                    ForEach(Valence.allCases) { valence in
                        Text(valence.label).tag(Valence?.some(valence))
                    }
                }
            }

            Section("Optional") {
                Picker("Soul", selection: $draft.soulId) {
                    Text("None").tag(UUID?.none)
                    ForEach(souls) { soul in
                        Text(soul.name).tag(UUID?.some(soul.id))
                    }
                }

                Picker("Collection", selection: $draft.collectionId) {
                    Text("None").tag(UUID?.none)
                    ForEach(collections) { collection in
                        Text(collection.name).tag(UUID?.some(collection.id))
                    }
                }
            }

            Section("Privacy") {
                Picker("Privacy", selection: $draft.visibility) {
                    ForEach(Visibility.allCases) { visibility in
                        Text(visibility.label).tag(visibility)
                    }
                }
                .pickerStyle(.segmented)
            }

            if let saveError {
                Section {
                    Text(saveError)
                        .foregroundStyle(.red)
                        .font(.callout)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Replace `CreatePebbleSheet`'s inline `Form` with `PebbleFormView`**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`, find the `content` computed property's loaded branch (the large `Form { ... }` starting at line 58) and replace it with:

```swift
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
```

The lines being replaced are roughly `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift:58-130` — the whole `Form { ... }` literal and its sections. Leave everything else in the file untouched (toolbar, save/load logic, helper structs, preview).

- [ ] **Step 3: Regenerate the Xcode project and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -30`
Expected: build succeeds. `PebbleFormView.swift` is picked up because `project.yml` globs `Features/**`.

- [ ] **Step 4: Run all tests to verify nothing regressed**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -30`
Expected: all tests pass.

- [ ] **Step 5: Manually smoke test `CreatePebbleSheet`**

Run the app in the simulator (Product → Run in Xcode, or `xcodebuild ... install`). Tap "Record a pebble", fill in the form, tap Save. A new pebble should appear in the list — the refactor is cosmetic, so behavior must be identical to main.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "$(cat <<'EOF'
refactor(ios): extract PebbleFormView from CreatePebbleSheet

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Create `EditPebbleSheet`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

**Why:** This is the new sheet that loads a pebble, prefills the form, and calls the `update_pebble` RPC on save.

- [ ] **Step 1: Create the file with the full implementation**

Create `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`:

```swift
import SwiftUI
import Supabase
import os

/// Sheet for editing an existing pebble.
///
/// Flow:
/// 1. `.task` loads the pebble detail + the four reference lists concurrently.
/// 2. On load success, `draft` is prefilled via `PebbleDraft(from: detail)` and
///    `PebbleFormView` renders the form.
/// 3. Save calls the `update_pebble` RPC with a `PebbleUpdatePayload` — all join
///    rows are replaced atomically server-side in a single Postgres transaction.
/// 4. `onSaved()` notifies the parent (`PathView`) so it can refetch the list.
struct EditPebbleSheet: View {
    let pebbleId: UUID
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var emotions: [Emotion] = []
    @State private var domains: [Domain] = []
    @State private var souls: [Soul] = []
    @State private var collections: [PebbleCollection] = []

    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-pebble")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Edit pebble")
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
                            .disabled(!draft.isValid || isLoading)
                        }
                    }
                }
        }
        .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") {
                    Task { await load() }
                }
            }
            .padding()
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

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            async let detailQuery: PebbleDetail = supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    emotion:emotions(id, name, color),
                    pebble_domains(domain:domains(id, name)),
                    pebble_souls(soul:souls(id, name)),
                    collection_pebbles(collection:collections(id, name))
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value

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

            let (detail, loadedEmotions, loadedDomains, loadedSouls, loadedCollections) =
                try await (detailQuery, emotionsQuery, domainsQuery, soulsQuery, collectionsQuery)

            self.emotions = loadedEmotions
            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.draft = PebbleDraft(from: detail)
            self.isLoading = false
        } catch {
            logger.error("edit pebble load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }

    private func save() async {
        guard draft.isValid else { return }
        isSaving = true
        saveError = nil

        do {
            let payload = PebbleUpdatePayload(from: draft)

            try await supabase.client
                .rpc("update_pebble", params: UpdatePebbleParams(pPebbleId: pebbleId, payload: payload))
                .execute()

            onSaved()
            dismiss()
        } catch {
            logger.error("update pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your changes. Please try again."
            self.isSaving = false
        }
    }
}

/// Wrapper matching the `update_pebble(p_pebble_id uuid, payload jsonb)` RPC signature.
/// The Supabase Swift SDK's `.rpc(_:params:)` encodes this struct to the JSON body
/// `{ "p_pebble_id": "...", "payload": {...} }`.
private struct UpdatePebbleParams: Encodable {
    let pPebbleId: UUID
    let payload: PebbleUpdatePayload

    enum CodingKeys: String, CodingKey {
        case pPebbleId = "p_pebble_id"
        case payload
    }
}

#Preview {
    EditPebbleSheet(pebbleId: UUID(), onSaved: {})
        .environment(SupabaseService())
}
```

**Note on the `.rpc()` call signature:** The Supabase Swift SDK's RPC API shape has shifted between versions. If the above `rpc("update_pebble", params: UpdatePebbleParams(...))` call does not compile, check the installed version via `cat apps/ios/project.yml | grep -A2 supabase` and `find ~/Library/Developer/Xcode/DerivedData -name "supabase-swift" -type d 2>/dev/null | head -1`, then adjust to match. Common variants:
- `.rpc("update_pebble", params: params)`
- `.rpc("update_pebble").execute()` with params passed differently
- Raw `AnyJSON` parameters

If you need to fall back, the shape the server expects is a POST to `/rest/v1/rpc/update_pebble` with body `{"p_pebble_id": "<uuid>", "payload": {...}}`.

- [ ] **Step 2: Regenerate project and build**

Run: `npm run generate --workspace=@pbbls/ios && xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -30`
Expected: build succeeds.

If the `.rpc()` call fails to compile, follow the note above to find the right signature, fix it, and build again.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): add EditPebbleSheet that calls update_pebble RPC

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Wire `EditPebbleSheet` into `PathView` and delete `PebbleDetailSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Delete: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`

**Why:** The edit sheet replaces the detail sheet entirely. `PathView`'s row tap now opens the editable form. On save, the list refetches so the updated row is visible immediately.

- [ ] **Step 1: Replace the `PebbleDetailSheet` presentation in `PathView`**

In `apps/ios/Pebbles/Features/Path/PathView.swift`, replace lines 25-27:

```swift
        .sheet(item: $selectedPebbleId) { id in
            PebbleDetailSheet(pebbleId: id)
        }
```

With:

```swift
        .sheet(item: $selectedPebbleId) { id in
            EditPebbleSheet(pebbleId: id, onSaved: {
                Task { await load() }
            })
        }
```

The `load()` method already exists on `PathView` and refetches the full list — calling it from `onSaved` gives the user the updated row without additional plumbing.

- [ ] **Step 2: Delete `PebbleDetailSheet.swift`**

```bash
git rm apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift
```

Then regenerate the Xcode project:

```bash
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 3: Build and verify no references remain**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -30`
Expected: build succeeds. No references to `PebbleDetailSheet` anywhere.

If the build fails with "cannot find `PebbleDetailSheet`", grep the codebase with the Grep tool for `PebbleDetailSheet` and fix any stragglers.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift apps/ios/project.yml 2>/dev/null || true
git add -u  # picks up the deletion
git commit -m "$(cat <<'EOF'
feat(ios): open EditPebbleSheet on pebble row tap

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Full test run, lint, and manual verification

**Files:** (none — verification only)

**Why:** Before opening the PR, make sure everything still works end to end — build, tests, lint, and a manual walkthrough of edit + create flows.

- [ ] **Step 1: Run all tests**

Run: `xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40`
Expected: all tests pass, including the new `PebbleDraftFromDetailTests` and `PebbleUpdatePayloadEncodingTests` suites, plus all pre-existing suites.

- [ ] **Step 2: Run SwiftLint**

Run: `cd apps/ios && swiftlint 2>&1 | tail -20`
Expected: no warnings or errors on the new/modified files.

If SwiftLint isn't installed locally, check the project's CI config to see whether it's part of the build pipeline — if so, flag that manual lint was skipped in the PR description and rely on CI.

- [ ] **Step 3: Manual test — edit flow**

1. Run the app in the simulator.
2. Sign in (use a test account with existing pebbles).
3. Tap an existing pebble in the path list.
4. Verify the form prefills: name, description, date, emotion, domain, valence, soul (if any), collection (if any), visibility.
5. Change the name and the emotion. Tap Save.
6. Verify the sheet dismisses and the path list shows the updated name.
7. Tap the same pebble again. Verify the new values persisted.

- [ ] **Step 4: Manual test — edit flow with collection change**

1. Tap a pebble that already has a collection.
2. Change its collection to a different one. Save.
3. Reopen the pebble. Verify the new collection is selected.
4. Repeat: set collection to "None". Save. Reopen. Verify collection is nil.

This exercises the `update_pebble` RPC's new collection replacement code.

- [ ] **Step 5: Manual test — create flow regression**

1. Tap "Record a pebble".
2. Fill in all fields, pick a collection, save.
3. Verify the new pebble appears and has the right collection when reopened.

This confirms the `PebbleFormView` extraction didn't break create.

- [ ] **Step 6: Manual test — error recovery**

1. Put the simulator in airplane mode (Settings → Airplane Mode).
2. Open a pebble. Verify "Couldn't load this pebble." with Retry button.
3. Turn airplane mode off. Tap Retry. Verify the form loads.
4. Edit a field, turn airplane mode on, tap Save. Verify the red error text appears and the sheet stays open.
5. Turn airplane mode off, tap Save again. Verify success.

---

## Task 8: Open the pull request

**Files:** (none — PR creation)

**Why:** Follow the project's PR workflow (`CLAUDE.md` §PR Workflow Checklist). Push the branch, open the PR with the right labels and milestone, ask for user confirmation on milestone.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/255-edit-pebble
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create \
  --title "feat(ios): edit a pebble via update_pebble RPC" \
  --label "feat,core,ios,supabase" \
  --milestone "M19 · iOS ShameVP" \
  --body "$(cat <<'EOF'
Resolves #255

## Summary

- Adds `EditPebbleSheet` that loads an existing pebble, prefills a shared `PebbleFormView`, and on Save calls the `update_pebble` RPC to persist edits atomically.
- Extracts `PebbleFormView` as a pure-UI Form body shared by create and edit sheets.
- Extends `create_pebble` and `update_pebble` RPCs with `collection_ids` and `new_collections` payload keys, plus an explicit ownership check on every linked collection (security definer bypasses RLS, so the check is necessary).
- Deletes the read-only `PebbleDetailSheet` — the edit sheet is now the detail view (spec decision #1).

## Key files

- `packages/supabase/supabase/migrations/20260415000000_pebble_rpc_collections.sql` — extends both pebble RPCs
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` — shared form body
- `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` — new edit sheet
- `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift` — RPC payload encoder
- `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift` — adds `init(from: PebbleDetail)`
- `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` — refactored to render `PebbleFormView`
- `apps/ios/Pebbles/Features/Path/PathView.swift` — presents `EditPebbleSheet` on row tap
- `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` — DELETED

## Implementation notes

- **Spec:** `docs/superpowers/specs/2026-04-15-ios-edit-pebble-design.md`
- **Asymmetry:** edit uses the RPC, create still does direct inserts. Migration of create to the RPC tracked in #257.
- **Security:** collection ownership check landed here; same gap for `domain_ids`/`soul_ids` tracked in #256.
- **No delete button.** Delete stays out of this PR, tracked as a future issue.

## Test plan

- [ ] `xcodebuild test` passes (all tests, including new `PebbleDraftFromDetailTests` and `PebbleUpdatePayloadEncodingTests`)
- [ ] `swiftlint` clean on new/modified files
- [ ] Manually: edit an existing pebble, change every field, verify persistence across reopen
- [ ] Manually: change a pebble's collection (set / change / clear), verify persistence
- [ ] Manually: create a new pebble via `CreatePebbleSheet`, verify nothing regressed
- [ ] Manually: error recovery under airplane mode for both load and save

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Confirm labels and milestone with the user**

If #255 has different labels or milestone than what we applied, ask the user whether to align. Per `CLAUDE.md`: "If the PR resolves an issue, propose inheriting the same labels and milestone from that issue and ask the user to confirm."

---

## Self-Review

### Spec coverage

| Spec section | Task(s) |
|---|---|
| Decisions taken in brainstorming | Recorded in spec, referenced throughout plan |
| Architecture · File layout | Tasks 4, 5, 6 (create, modify, delete as listed) |
| Component responsibilities — PebbleFormView | Task 4 |
| Component responsibilities — CreatePebbleSheet | Task 4 |
| Component responsibilities — EditPebbleSheet | Task 5 |
| Component responsibilities — PathView | Task 6 |
| Data models — PebbleDraft.init(from:) | Task 2 |
| Data models — PebbleUpdatePayload | Task 3 |
| Data flow — Opening the sheet | Task 5, Step 1 (`load()` function) |
| Data flow — Saving edits | Task 5, Step 1 (`save()` function) |
| Data flow — Cancel | Task 5, Step 1 (toolbar Cancel) |
| Supabase changes — New migration | Task 1, Step 1 |
| Supabase changes — create_pebble additions | Task 1, Step 1 |
| Supabase changes — update_pebble additions | Task 1, Step 1 |
| Supabase changes — Ownership check | Task 1, Step 1 (both RPCs) |
| Supabase changes — Types regen | Task 1, Steps 2–3 |
| Error handling and logging | Task 5 (logger + user-facing errors) |
| Testing — PebbleDraftFromDetailTests | Task 2 |
| Testing — PebbleUpdatePayloadEncodingTests | Task 3 |
| Out of scope | Respected — no delete, no create migration, no multi-select |
| Acceptance criteria | Task 7 manual tests |

No gaps.

### Placeholder scan

No `TODO`, `TBD`, or "similar to above" placeholders. Every code step includes the actual code. The only soft spots are (a) the note about the Supabase Swift SDK `.rpc()` signature in Task 5 — this is a real uncertainty because the SDK version isn't pinned in what I've read, and the fallback guidance is explicit; (b) the note in Task 3 about `Valence.highlightLarge` mapping — also explicit with a verification step.

### Type consistency

- `PebbleUpdatePayload` property names (`name`, `description`, `happenedAt`, `intensity`, `positiveness`, `visibility`, `emotionId`, `domainIds`, `soulIds`, `collectionIds`) match `CodingKeys` and match the fields used in `EditPebbleSheet.save()` and the test file.
- `UpdatePebbleParams.pPebbleId` encodes to `p_pebble_id` — matches the RPC signature `update_pebble(p_pebble_id uuid, payload jsonb)`.
- `PebbleDraft(from: PebbleDetail)` uses `detail.emotion.id`, `detail.domains.first?.id`, etc. — matches the existing `PebbleDetail` shape verified in `Models/PebbleDetail.swift`.
- Test file `PebbleDraftFromDetailTests` uses `DomainRef`, `Soul`, `PebbleCollection` — confirmed as the actual types in the codebase.
- `PebbleFormView` properties (`draft`, `emotions`, `domains`, `souls`, `collections`, `saveError`) are referenced identically in both `CreatePebbleSheet` (Task 4 Step 2) and `EditPebbleSheet` (Task 5 Step 1).

Consistent throughout.
