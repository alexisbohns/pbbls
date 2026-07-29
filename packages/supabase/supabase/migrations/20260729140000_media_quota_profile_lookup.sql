-- =============================================================================
-- Media-quota lookup keys off profiles.user_id, not profiles.id (#618)
-- =============================================================================
-- `create_pebble` and `update_pebble` resolved the per-pebble media quota with
--
--   select coalesce(max_media_per_pebble, 1) from public.profiles where id = v_user_id
--
-- but `v_user_id` is `auth.uid()`, which matches `profiles.user_id`.
-- `profiles.id` is an independent surrogate key (gen_random_uuid()), so the
-- lookup matched no row, `v_max_media` stayed NULL, and `n > NULL` evaluated to
-- NULL — the `media_quota_exceeded` guard never fired for anyone.
--
-- Two changes, applied symmetrically to both RPCs:
--   1. `where user_id = v_user_id` — the actual fix.
--   2. `coalesce(v_max_media, 1)` at the comparison — fail closed if the profile
--      row is ever missing, so a NULL can no longer silently disable the guard.
--
-- Bodies are otherwise verbatim from 20260712000000_glyph_usability_guard.sql.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. create_pebble
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
    -- auth.uid() matches profiles.user_id, NOT the profiles.id surrogate key (#618)
    select coalesce(max_media_per_pebble, 1) into v_max_media
      from public.profiles where user_id = v_user_id;

    -- coalesce again: a missing profile row must fail closed, not skip the guard
    if jsonb_array_length(payload->'snaps') > coalesce(v_max_media, 1) then
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
-- 2. update_pebble — same lookup, kept symmetric
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
    -- auth.uid() matches profiles.user_id, NOT the profiles.id surrogate key (#618)
    select coalesce(max_media_per_pebble, 1) into v_max_media
      from public.profiles where user_id = v_user_id;

    -- coalesce again: a missing profile row must fail closed, not skip the guard
    if jsonb_array_length(payload->'snaps') > coalesce(v_max_media, 1) then
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
