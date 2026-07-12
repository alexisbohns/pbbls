# Glyph Picker ↔ Store Harmonization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the flat glyph picker into a tabbed sheet (Mine · Owned · Community) that mirrors the store and lets users buy a glyph inline, and enforce glyph ownership server-side for pebbles and souls.

**Architecture:** Web-side, `GlyphPickerDialog` stops receiving a `marks` prop and instead renders a new `GlyphPickerTabs` shell that sources its own data — Mine (`store.marks`) and Owned (`store.entitledMarks`) reuse `GlyphPickerGrid`; Community reuses `useGlyphMarket` + `BuyGlyphDialog` via a new `GlyphMarketPickerList`, where a successful purchase auto-selects the glyph and closes the sheet. DB-side, a new `can_use_glyph(glyph_id, user)` helper is enforced inside `create_pebble`/`update_pebble` and by a `before insert/update` trigger on `souls`.

**Tech Stack:** Next.js 16 / React 19 (`apps/web`), next-intl, Tailwind 4, shadcn/ui; PostgreSQL RPC + triggers (`packages/supabase`).

**Note on testing:** the web app has no automated test harness yet (V1), so web tasks verify via `npm run lint`/`npm run build` at change scope plus manual checks. DB tasks verify via a SQL assertion after applying the migration.

**Context already gathered (do not re-derive):**
- Picker source hook `useUsableGlyphs` is ALSO used for display by `PathBottomBar.tsx:33` and `PebblePeek.tsx:33` — **do not delete it.**
- `store.marks` and `store.entitledMarks` are both `Mark[]` (entitled mapped via `rowToMark`, so no `price`/`owned` fields) — both feed `GlyphPickerGrid` directly.
- Existing keys present: `glyphs.loading`, `glyphs.untitled`, `market.price`, `market.buy`, `glyphs.picker.empty`.
- Latest migration is `20260703000000_*`; new migration filename must sort after it.

---

## File structure

**Create:**
- `packages/supabase/supabase/migrations/20260712000000_glyph_usability_guard.sql` — `can_use_glyph` helper, recreated `create_pebble` + `update_pebble` with the guard, `souls` trigger.
- `apps/web/components/glyphs/GlyphMarketPickerList.tsx` — Community tab: buyable market glyphs, buy → `onBought`.
- `apps/web/components/record/GlyphPickerTabs.tsx` — 3-tab shell owning tab state and per-tab data sourcing.

**Modify:**
- `packages/supabase/types/database.ts` — regenerated (adds `can_use_glyph`).
- `apps/web/lib/i18n/messages/en.json` + `fr.json` — tab labels + per-tab empty strings.
- `apps/web/components/glyphs/GlyphPickerGrid.tsx` — optional `emptyMessage` prop.
- `apps/web/components/record/GlyphPickerDialog.tsx` — drop `marks` prop, render `GlyphPickerTabs`.
- `apps/web/components/path/QuickPebbleEditor.tsx:367` — drop `marks={marks}` from the dialog (keep `marks` var; `SoulsSheet` still uses it).
- `apps/web/components/souls/AddSoulForm.tsx:77` — drop `marks={marks}` from the dialog (keep the `marks` prop; the trigger preview still uses it).
- `apps/web/components/souls/SoulDetailHeader.tsx:123` — drop `marks={marks}` from the dialog (keep the `marks` prop).

---

## Task 1: DB — `can_use_glyph` helper, guarded RPCs, souls trigger

**Files:**
- Create: `packages/supabase/supabase/migrations/20260712000000_glyph_usability_guard.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated)

- [ ] **Step 1: Write the migration file**

Create `packages/supabase/supabase/migrations/20260712000000_glyph_usability_guard.sql` with exactly this content:

```sql
-- =============================================================================
-- Glyph usability guard (#545)
-- =============================================================================
-- Enforce, server-side, that a glyph attached to a pebble or soul is usable by
-- the actor: authored by them (glyphs.user_id = user), a system default
-- (glyphs.user_id IS NULL), or entitled (an approved purchase). Closes the gap
-- where create_pebble / update_pebble and direct soul writes accepted any
-- glyph_id. A NULL glyph is allowed (pebble glyph is optional).
--
-- Also recreates update_pebble WITHOUT the stale glyphs.shape_id reference in
-- its inline-glyph INSERT (the column was dropped in 20260701114205; the prior
-- update_pebble body still referenced it and would fail on an inline-glyph
-- update).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Helper: is this glyph usable by this user?
-- ---------------------------------------------------------------------------
create or replace function public.can_use_glyph(p_glyph_id uuid, p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_glyph_id is null
    or exists (
      select 1 from public.glyphs g
      where g.id = p_glyph_id
        and (g.user_id = p_user or g.user_id is null)
    )
    or exists (
      select 1 from public.glyph_entitlements e
      where e.glyph_id = p_glyph_id and e.user_id = p_user
    );
$$;

-- ---------------------------------------------------------------------------
-- 2. create_pebble — add the ownership guard (base: 20260701114205, a)
-- ---------------------------------------------------------------------------
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
  v_max_media int;
begin
  -- Inline glyph creation
  if payload ? 'new_glyph' then
    insert into public.glyphs (user_id, name, strokes, view_box)
    values (
      v_user_id,
      (payload->'new_glyph'->>'name'),
      coalesce(payload->'new_glyph'->'strokes', '[]'::jsonb),
      (payload->'new_glyph'->>'view_box')
    )
    returning id into v_glyph_id;
  else
    v_glyph_id := (payload->>'glyph_id')::uuid;
  end if;

  -- Glyph ownership guard (authored ∪ system-default ∪ entitled ∪ null)
  if not public.can_use_glyph(v_glyph_id, v_user_id) then
    raise exception 'Glyph not usable by user: %', v_glyph_id using errcode = '42501';
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

  -- Collection ownership check
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

  -- Insert snaps (accepts iOS-generated id; enforces per-pebble quota)
  v_snaps_count := 0;
  if payload ? 'snaps' then
    select coalesce(max_media_per_pebble, 1) into v_max_media
      from public.profiles where id = v_user_id;

    if jsonb_array_length(payload->'snaps') > v_max_media then
      raise exception 'media_quota_exceeded' using errcode = 'P0001';
    end if;

    for v_snap in select * from jsonb_array_elements(payload->'snaps')
    loop
      insert into public.snaps (id, pebble_id, user_id, storage_path, sort_order)
      values (
        coalesce((v_snap.value->>'id')::uuid, gen_random_uuid()),
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

-- ---------------------------------------------------------------------------
-- 3. update_pebble — add the guard + drop the stale shape_id reference
--    (base: 20260426000002, minus glyphs.shape_id)
-- ---------------------------------------------------------------------------
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

  -- Inline glyph creation (shape_id dropped — column removed in 20260701114205)
  if payload ? 'new_glyph' then
    insert into public.glyphs (user_id, name, strokes, view_box)
    values (
      v_user_id,
      (payload->'new_glyph'->>'name'),
      coalesce(payload->'new_glyph'->'strokes', '[]'::jsonb),
      (payload->'new_glyph'->>'view_box')
    )
    returning id into v_glyph_id;

    payload := payload || jsonb_build_object('glyph_id', v_glyph_id::text);
  end if;

  -- Glyph ownership guard: only when the glyph is being (re)assigned.
  if payload ? 'glyph_id' then
    v_glyph_id := (payload->>'glyph_id')::uuid;
    if not public.can_use_glyph(v_glyph_id, v_user_id) then
      raise exception 'Glyph not usable by user: %', v_glyph_id using errcode = '42501';
    end if;
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

-- ---------------------------------------------------------------------------
-- 4. Souls trigger — souls are written directly (no RPC), so guard at the table
-- ---------------------------------------------------------------------------
create or replace function public.enforce_soul_glyph_usable()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.can_use_glyph(new.glyph_id, new.user_id) then
    raise exception 'Glyph not usable by user: %', new.glyph_id using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists souls_glyph_usable on public.souls;
create trigger souls_glyph_usable
  before insert or update of glyph_id on public.souls
  for each row execute function public.enforce_soul_glyph_usable();
```

- [ ] **Step 2: Apply the migration to the remote DB**

> **CHECKPOINT — production DB write.** This project deploys to remote Supabase (no local Docker). Confirm with the user before pushing.

Run from repo root:
```bash
npm run db:push --workspace=packages/supabase
```
Expected: the CLI lists `20260712000000_glyph_usability_guard.sql` as applied with no errors.

- [ ] **Step 3: Verify the guard behaves (SQL assertion)**

Confirm the helper resolves as designed. Run via the Supabase SQL editor or `psql`:
```sql
-- system-default glyph (user_id IS NULL) is usable by anyone
select public.can_use_glyph(
  (select id from public.glyphs where user_id is null limit 1),
  gen_random_uuid()
) as should_be_true;

-- NULL glyph is usable (optional)
select public.can_use_glyph(null, gen_random_uuid()) as should_be_true;

-- a foreign, non-entitled glyph is NOT usable by a random user
select public.can_use_glyph(
  (select id from public.glyphs where user_id is not null limit 1),
  gen_random_uuid()
) as should_be_false;
```
Expected: `true`, `true`, `false`.

- [ ] **Step 4: Regenerate and commit the types**

Run from repo root (uses the linked remote schema — the `db:types` local variant needs Docker and truncates the file on failure):
```bash
npm run db:types:remote --workspace=packages/supabase
```
Expected: `packages/supabase/types/database.ts` now contains a `can_use_glyph` entry under `Functions`. Sanity-check it is not truncated:
```bash
grep -c "can_use_glyph" packages/supabase/types/database.ts   # expect >= 1
```

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/20260712000000_glyph_usability_guard.sql packages/supabase/types/database.ts
git commit -m "feat(db): enforce glyph ownership in create/update_pebble and souls (#545)"
```

---

## Task 2: i18n — tab labels + per-tab empty strings

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`
- Modify: `apps/web/lib/i18n/messages/fr.json`

- [ ] **Step 1: Add keys under `record.glyph` in `en.json`**

Find the `record.glyph` object (currently `{"title":"Glyph","close":"Close","addAria":"Add glyph","changeAria":"Change glyph"}`) and add three keys so it reads:
```json
"glyph": {
  "title": "Glyph",
  "close": "Close",
  "addAria": "Add glyph",
  "changeAria": "Change glyph",
  "tabs": { "mine": "Mine", "owned": "Owned", "community": "Community" },
  "emptyMine": "No glyphs yet. Carve one from the Glyphs page.",
  "emptyOwned": "You haven't bought any glyphs yet.",
  "emptyCommunity": "No community glyphs available to buy right now."
}
```

- [ ] **Step 2: Add the matching keys under `record.glyph` in `fr.json`**

Find the `record.glyph` object (currently `{"title":"Glyphe","close":"Fermer","addAria":"Ajouter un glyphe","changeAria":"Changer de glyphe"}`) and extend it to:
```json
"glyph": {
  "title": "Glyphe",
  "close": "Fermer",
  "addAria": "Ajouter un glyphe",
  "changeAria": "Changer de glyphe",
  "tabs": { "mine": "Les miens", "owned": "Achetés", "community": "Communauté" },
  "emptyMine": "Aucun glyphe pour l'instant. Gravez-en un depuis la page Glyphes.",
  "emptyOwned": "Vous n'avez encore acheté aucun glyphe.",
  "emptyCommunity": "Aucun glyphe communautaire à acheter pour le moment."
}
```

- [ ] **Step 3: Verify both files are valid JSON**

Run from repo root:
```bash
node -e "require('./apps/web/lib/i18n/messages/en.json'); require('./apps/web/lib/i18n/messages/fr.json'); console.log('ok')"
```
Expected: `ok`.

- [ ] **Step 4: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(ui): add glyph picker tab + empty-state strings (#545)"
```

---

## Task 3: `GlyphPickerGrid` — optional `emptyMessage` prop

**Files:**
- Modify: `apps/web/components/glyphs/GlyphPickerGrid.tsx`

- [ ] **Step 1: Add the prop and use it for the empty state**

Replace the props type and the empty-state branch. The type becomes:
```tsx
type GlyphPickerGridProps = {
  marks: Mark[]
  selectedMarkId: string | undefined
  onSelect: (id: string | undefined) => void
  /** Empty-state copy; defaults to the generic "no glyphs" message. */
  emptyMessage?: string
}
```
Update the signature and empty branch:
```tsx
export function GlyphPickerGrid({ marks, selectedMarkId, onSelect, emptyMessage }: GlyphPickerGridProps) {
  const t = useTranslations("glyphs.picker")

  if (marks.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        {emptyMessage ?? t("empty")}
      </p>
    )
  }
```
Leave the rest of the component unchanged.

- [ ] **Step 2: Lint the workspace**

Run from repo root:
```bash
npm run lint --workspace=apps/web
```
Expected: no errors (existing callers still valid — `emptyMessage` is optional).

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/glyphs/GlyphPickerGrid.tsx
git commit -m "feat(ui): let GlyphPickerGrid take a custom empty message (#545)"
```

---

## Task 4: `GlyphMarketPickerList` — Community tab

**Files:**
- Create: `apps/web/components/glyphs/GlyphMarketPickerList.tsx`

- [ ] **Step 1: Create the component**

Create `apps/web/components/glyphs/GlyphMarketPickerList.tsx`:
```tsx
"use client"

import { useTranslations } from "next-intl"
import { useGlyphMarket } from "@/lib/data/useGlyphMarket"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { BuyGlyphDialog } from "@/components/glyphs/BuyGlyphDialog"
import { Button } from "@/components/ui/button"

type GlyphMarketPickerListProps = {
  /** Called with the glyph id right after a successful purchase. */
  onBought: (glyphId: string) => void
}

/**
 * Community tab of the glyph picker: buyable market glyphs (owned ones live
 * under the Owned tab; the caller's own creations are already excluded by
 * listMarketGlyphs). Buying reuses the store's BuyGlyphDialog + karma spend;
 * on success the glyph is handed back so the picker can select it and close.
 */
export function GlyphMarketPickerList({ onBought }: GlyphMarketPickerListProps) {
  const t = useTranslations("glyphs")
  const tMarket = useTranslations("market")
  const tGlyph = useTranslations("record.glyph")
  const { glyphs, loading, buy } = useGlyphMarket()

  const buyable = glyphs.filter((g) => !g.owned)

  if (loading) return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  if (buyable.length === 0) {
    return <p className="text-sm text-muted-foreground">{tGlyph("emptyCommunity")}</p>
  }

  return (
    <ul className="flex flex-col gap-2">
      {buyable.map((glyph) => (
        <li key={glyph.id}>
          <article className="flex items-center gap-4 rounded-lg border border-border px-4 py-3">
            <GlyphPreview mark={glyph} className="w-14 shrink-0 aspect-square" />
            <div className="min-w-0 flex-1">
              <h3 className="truncate text-sm font-medium">{glyph.name || t("untitled")}</h3>
              <p className="mt-1 text-xs text-muted-foreground">
                {tMarket("price", { amount: glyph.price })}
              </p>
            </div>
            <BuyGlyphDialog
              amount={glyph.price}
              onBuy={async () => {
                await buy(glyph)
                onBought(glyph.id)
              }}
              trigger={<Button size="sm">{tMarket("buy")}</Button>}
            />
          </article>
        </li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 2: Lint the workspace**

Run from repo root:
```bash
npm run lint --workspace=apps/web
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/glyphs/GlyphMarketPickerList.tsx
git commit -m "feat(ui): add community (buy-inline) glyph list for the picker (#545)"
```

---

## Task 5: `GlyphPickerTabs` — 3-tab shell

**Files:**
- Create: `apps/web/components/record/GlyphPickerTabs.tsx`

- [ ] **Step 1: Create the tab shell**

Create `apps/web/components/record/GlyphPickerTabs.tsx`:
```tsx
"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { useDataProvider } from "@/lib/data/provider-context"
import { GlyphPickerGrid } from "@/components/glyphs/GlyphPickerGrid"
import { GlyphMarketPickerList } from "@/components/glyphs/GlyphMarketPickerList"

type GlyphPickerTab = "mine" | "owned" | "community"
const TABS: GlyphPickerTab[] = ["mine", "owned", "community"]

type GlyphPickerTabsProps = {
  selectedMarkId: string | undefined
  /** Select a glyph (Mine/Owned tap, or a just-bought Community glyph). */
  onSelect: (id: string | undefined) => void
}

/**
 * Tabbed body of the glyph picker, harmonized with the /glyphs store:
 * Mine (authored) · Owned (entitled) are directly pickable; Community glyphs
 * are bought inline and auto-selected. Local tab state only — unlike the
 * store's GlyphTabs this is not URL-driven.
 */
export function GlyphPickerTabs({ selectedMarkId, onSelect }: GlyphPickerTabsProps) {
  const t = useTranslations("record.glyph")
  const { store } = useDataProvider()
  const [tab, setTab] = useState<GlyphPickerTab>("mine")

  return (
    <div>
      <nav className="mb-4 flex gap-1 rounded-lg bg-muted p-1" role="tablist" aria-label={t("title")}>
        {TABS.map((tb) => (
          <button
            key={tb}
            type="button"
            role="tab"
            aria-selected={tab === tb}
            onClick={() => setTab(tb)}
            className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
              tab === tb
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {t(`tabs.${tb}`)}
          </button>
        ))}
      </nav>

      {tab === "mine" && (
        <GlyphPickerGrid
          marks={store.marks}
          selectedMarkId={selectedMarkId}
          onSelect={onSelect}
          emptyMessage={t("emptyMine")}
        />
      )}
      {tab === "owned" && (
        <GlyphPickerGrid
          marks={store.entitledMarks}
          selectedMarkId={selectedMarkId}
          onSelect={onSelect}
          emptyMessage={t("emptyOwned")}
        />
      )}
      {tab === "community" && <GlyphMarketPickerList onBought={onSelect} />}
    </div>
  )
}
```

- [ ] **Step 2: Lint the workspace**

Run from repo root:
```bash
npm run lint --workspace=apps/web
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/record/GlyphPickerTabs.tsx
git commit -m "feat(ui): add tabbed glyph picker shell (mine/owned/community) (#545)"
```

---

## Task 6: `GlyphPickerDialog` — render tabs, drop `marks` prop

**Files:**
- Modify: `apps/web/components/record/GlyphPickerDialog.tsx`

- [ ] **Step 1: Replace the dialog body**

Rewrite `apps/web/components/record/GlyphPickerDialog.tsx` to:
```tsx
"use client"

import { useTranslations } from "next-intl"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { GlyphPickerTabs } from "@/components/record/GlyphPickerTabs"

type GlyphPickerDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  selectedMarkId: string | undefined
  onSave: (markId: string | undefined) => void
}

export function GlyphPickerDialog({
  open,
  onOpenChange,
  selectedMarkId,
  onSave,
}: GlyphPickerDialogProps) {
  const t = useTranslations("record.glyph")

  const handleSelect = (id: string | undefined) => {
    onSave(id)
    onOpenChange(false)
  }

  return (
    <PickerSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      closeLabel={t("close")}
    >
      <GlyphPickerTabs selectedMarkId={selectedMarkId} onSelect={handleSelect} />
    </PickerSheet>
  )
}
```
(The `Mark` import and the `marks` prop are gone.)

- [ ] **Step 2: Lint (expect call-site type errors to surface at build, not lint)**

Run from repo root:
```bash
npm run lint --workspace=apps/web
```
The removed `marks` prop makes the three call sites pass an unknown prop — TypeScript flags this at build. Task 7 fixes them; do the build check there.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/record/GlyphPickerDialog.tsx
git commit -m "feat(ui): source glyph picker tabs internally, drop marks prop (#545)"
```

---

## Task 7: Update the three call sites

**Files:**
- Modify: `apps/web/components/path/QuickPebbleEditor.tsx`
- Modify: `apps/web/components/souls/AddSoulForm.tsx`
- Modify: `apps/web/components/souls/SoulDetailHeader.tsx`

- [ ] **Step 1: `QuickPebbleEditor` — drop `marks={marks}` from the dialog**

At the `<GlyphPickerDialog …>` block (around line 367), remove the `marks={marks}` line so it reads:
```tsx
      <GlyphPickerDialog
        open={glyphOpen}
        onOpenChange={setGlyphOpen}
        selectedMarkId={markId}
        onSave={setMarkId}
      />
```
Do **not** remove `const { glyphs: marks } = useUsableGlyphs()` (line 52) or its import — `marks` is still passed to `<SoulsSheet marks={marks} … />` just below.

- [ ] **Step 2: `AddSoulForm` — drop `marks={marks}` from the dialog**

At the `<GlyphPickerDialog …>` block (around line 74), remove the `marks={marks}` line:
```tsx
      <GlyphPickerDialog
        open={glyphOpen}
        onOpenChange={setGlyphOpen}
        selectedMarkId={glyphId === DEFAULT_GLYPH_ID ? undefined : glyphId}
        onSave={(markId) => setGlyphId(markId ?? DEFAULT_GLYPH_ID)}
      />
```
Keep the `marks` prop on `AddSoulForm` and the `selectedMark = marks.find(...)` lookup — the trigger button still previews the selected glyph from it.

- [ ] **Step 3: `SoulDetailHeader` — drop `marks={marks}` from the dialog**

At the `<GlyphPickerDialog …>` block (around line 120), remove the `marks={marks}` line:
```tsx
      <GlyphPickerDialog
        open={glyphOpen}
        onOpenChange={setGlyphOpen}
        selectedMarkId={
          soul.glyph_id === DEFAULT_GLYPH_ID ? undefined : soul.glyph_id
        }
        onSave={handleGlyphSave}
      />
```
Keep the `marks` prop and `selectedMark` lookup for the same reason.

- [ ] **Step 4: Full type-check + build**

The shared `packages/supabase` types changed (Task 1) and multiple components changed, so build from root:
```bash
npm run lint --workspace=apps/web
npm run build
```
Expected: both green. If the build flags a leftover `marks={marks}` on any `GlyphPickerDialog`, remove it.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/path/QuickPebbleEditor.tsx apps/web/components/souls/AddSoulForm.tsx apps/web/components/souls/SoulDetailHeader.tsx
git commit -m "feat(ui): drop marks prop from glyph picker call sites (#545)"
```

---

## Task 8: Manual verification, Arkaik check, PR

**Files:** none (verification + docs)

- [ ] **Step 1: Manual verification (dev server)**

Run `npm run dev` (from repo root) and, as a **non-publisher** account:
1. Start creating a pebble → open the glyph sheet. Confirm three tabs: **Mine · Owned · Community**.
2. **Mine** lists only your authored glyphs; **Owned** lists only bought glyphs; neither lists foreign glyphs. Tapping one selects it and closes the sheet.
3. **Community** lists buyable glyphs with prices. Buy one → confirm karma spend, the sheet closes, and that glyph is now the pebble's glyph. Reopen → it now appears under **Owned**.
4. Repeat the picker checks via a soul: **Souls → add soul** (`AddSoulForm`) and **soul detail → change glyph** (`SoulDetailHeader`).
5. Insufficient karma: attempt to buy with too little karma → the buy dialog shows the error and nothing is selected.

If any foreign glyph appears under Mine/Owned for this account, stop and debug the data/RLS path (systematic-debugging) — the redesign assumes `store.marks`/`store.entitledMarks` are correctly scoped; the server guard still blocks attaching it.

- [ ] **Step 2: Server-guard spot check**

In the Supabase SQL editor, as an authenticated session for a normal user, attempt to attach a foreign glyph and confirm rejection:
```sql
select public.create_pebble(jsonb_build_object(
  'name', 'guard test',
  'happened_at', now()::text,
  'intensity', 1,
  'positiveness', 0,
  'emotion_id', (select id from public.emotions limit 1),
  'glyph_id', (select id from public.glyphs where user_id is not null
                 and user_id <> auth.uid() limit 1)
));
```
Expected: error `Glyph not usable by user: …` (SQLSTATE 42501). (Roll back / delete the row if one is created in a different test.)

- [ ] **Step 3: Arkaik map check**

This is a picker enhancement, not a new screen/route/model/endpoint, and reuses existing views — the product graph is unchanged. Invoke the `arkaik` skill only to confirm no node/edge needs updating; expect a no-op. If the skill finds a gap, apply it and commit `docs/arkaik/bundle.json` separately.

- [ ] **Step 4: Open the PR**

Push the branch and open the PR:
```bash
git push -u origin feat/545-glyph-picker-store-harmonization
```
Then open a PR with:
- **Title:** `feat(ui): harmonize glyph picker with the store + server ownership guard`
- **Body:** starts with `Resolves #545`; lists the key files (migration, `GlyphPickerTabs`, `GlyphMarketPickerList`, `GlyphPickerDialog`, the three call sites, i18n); notes the `update_pebble` `shape_id` cleanup.
- **Labels / milestone:** inherit from #545 — `feat`, `ui`, `db`, milestone **M36**. Confirm with the user before setting.
- **Lab Note (EN/FR):** this is a user-facing `feat`, so include a short bilingual blurb in a **Lab Note** section, e.g.
  - EN — *Buy glyphs while you carve.* The glyph picker now mirrors your Glyph store: pick from **Mine** and **Owned**, or grab a new community glyph from **Community** without leaving the moment — it's selected the instant you buy it.
  - FR — *Achetez des glyphes en gravant.* Le sélecteur de glyphes reflète désormais votre boutique : choisissez parmi **Les miens** et **Achetés**, ou prenez un nouveau glyphe dans **Communauté** sans quitter l'instant — il est sélectionné dès l'achat.
  Mark it a proposal (a human publishes via the Lab admin at release).

---

## Notes for the executor

- **Commit discipline:** one commit per task as written; conventional-commit messages, `#545` in each.
- **No local Docker:** all DB work targets the linked remote (`db:push`, `db:types:remote`). Get user confirmation before `db:push` (production write).
- **Do not delete `useUsableGlyphs`** — it still backs glyph *display* in `PathBottomBar` and `PebblePeek`.
- **Decision log / CLAUDE.md:** no significant decision reversal here; no decision-log entry expected. Do not edit CLAUDE.md for learnings (milestone-boundary grooming only).
