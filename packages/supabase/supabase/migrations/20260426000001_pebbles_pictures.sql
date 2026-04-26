-- Migration: pebbles media setup
--
-- Adds the per-pebble photo quota column on profiles, the private
-- `pebbles-media` Storage bucket and its owner-scoped object policies, and
-- updates `create_pebble` to (a) accept iOS-generated `snaps[].id` values
-- and (b) enforce `profiles.max_media_per_pebble` (raises SQLSTATE P0001
-- when exceeded). The bucket caps uploads at 1.5 MB and `image/jpeg` only;
-- iOS converts HEIC/HEIF to JPEG before upload and stores two files per
-- snap at `{user_id}/{snap_id}/original.jpg` and `…/thumb.jpg`.

-- ============================================================
-- profiles: per-pebble media quota
-- ============================================================

alter table public.profiles
  add column if not exists max_media_per_pebble integer not null default 1;

-- ============================================================
-- Storage bucket
-- ============================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'pebbles-media',
  'pebbles-media',
  false,
  1572864,                          -- 1.5 MB hard cap
  array['image/jpeg']
)
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- ============================================================
-- Owner-scoped object policies
-- ============================================================
-- Path layout is `{user_id}/{snap_id}/{original|thumb}.jpg`, so the first
-- folder segment must equal the caller's `auth.uid()`. No UPDATE policy:
-- replacing a file means delete + re-upload under a fresh `snap_id`.

drop policy if exists "pebbles_media_owner_select" on storage.objects;
create policy "pebbles_media_owner_select" on storage.objects
  for select using (
    bucket_id = 'pebbles-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "pebbles_media_owner_insert" on storage.objects;
create policy "pebbles_media_owner_insert" on storage.objects
  for insert with check (
    bucket_id = 'pebbles-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "pebbles_media_owner_delete" on storage.objects;
create policy "pebbles_media_owner_delete" on storage.objects
  for delete using (
    bucket_id = 'pebbles-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- ============================================================
-- create_pebble — accepts snaps[].id, enforces per-pebble quota
-- ============================================================
-- Identical to the prior version (20260415000000_pebble_rpc_collections.sql)
-- except: a new `v_max_media` declaration, and the `Insert snaps` block now
-- (a) reads optional `id` from each snap, defaulting to gen_random_uuid()
-- when absent, and (b) raises P0001 if the snap count exceeds the user's
-- `profiles.max_media_per_pebble`.

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
