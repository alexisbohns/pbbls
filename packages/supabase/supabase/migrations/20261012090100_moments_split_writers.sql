-- =============================================================================
-- Moments and pebbles split — writers (spec §3)
-- save_moment / complete_pebble / delete_moment / delete_pebble /
-- compute_karma_delta / set_collection_glyph. create_pebble and update_pebble
-- are re-emitted as compat wrappers in 20261012090400.
--
-- NOT safe to push alone: 20261012090000–20261012090400 ship together. This
-- file drops the 6-arg compute_karma_delta and delete_pebble_media that the
-- not-yet-wrapped create_pebble / update_pebble and the clients still name.
--
-- Payload conventions (cross-surface): an explicit JSON null means "absent"
-- (Kotlin and Swift encoders emit explicit nulls), read through
-- private.jsonb_has; array keys are type-checked (invalid_payload, 22023).
-- =============================================================================

-- private.jsonb_has: key present AND not JSON null. Lives in `private`, which
-- PostgREST never serves; the definer functions run as owner anyway.
create or replace function private.jsonb_has(p jsonb, k text)
returns boolean
language sql
immutable
as $$ select p ? k and jsonb_typeof(p->k) <> 'null' $$;
revoke all on function private.jsonb_has(jsonb, text) from public, anon;
grant execute on function private.jsonb_has(jsonb, text) to authenticated;

-- private.assert_jsonb_array: a present key that is not an array is a client
-- bug; name it instead of letting jsonb_array_length throw.
create or replace function private.assert_jsonb_array(p jsonb, k text)
returns void
language plpgsql
immutable
as $$
begin
  if private.jsonb_has(p, k) and jsonb_typeof(p->k) <> 'array' then
    raise exception 'invalid_payload' using errcode = '22023', detail = k || ' must be an array';
  end if;
end;
$$;
revoke all on function private.assert_jsonb_array(jsonb, text) from public, anon;
grant execute on function private.assert_jsonb_array(jsonb, text) to authenticated;

-- karma: the glyph bonus is gone, the description bonus reads the moment.
-- search_path pin and role revoke kept from 20260411000005.
drop function public.compute_karma_delta(text, int, int, int, boolean, int);
create function public.compute_karma_delta(
  p_has_description boolean,
  p_cards_count int,
  p_souls_count int,
  p_domains_count int,
  p_snaps_count int
) returns int as $$
declare
  delta int := 1;
begin
  if p_has_description then delta := delta + 1; end if;
  delta := delta + least(p_cards_count, 4);
  if p_souls_count > 0 then delta := delta + 1; end if;
  if p_domains_count > 0 then delta := delta + 1; end if;
  if p_snaps_count > 0 then delta := delta + 1; end if;
  return least(delta, 10);
end;
$$ language plpgsql immutable set search_path = public;
revoke execute on function public.compute_karma_delta(boolean, int, int, int, int) from public, anon, authenticated;

-- save_moment ------------------------------------------------------------------
create function public.save_moment(payload jsonb)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user      uuid := auth.uid();
  v_id        uuid := nullif(payload->>'id', '')::uuid;
  v_name      text := nullif(btrim(coalesce(payload->>'name', '')), '');
  v_when      timestamptz := date_trunc('second', (payload->>'happened_at')::timestamptz);
  v_snap      record;
  v_max_media int;
  v_snaps     int;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  perform private.assert_jsonb_array(payload, 'snaps');

  if v_id is null then
    insert into public.moments (user_id, name, description, happened_at)
    values (v_user, v_name, nullif(payload->>'description', ''), coalesce(v_when, date_trunc('second', now())))
    returning id into v_id;
  else
    update public.moments set
      name        = case when private.jsonb_has(payload, 'name') then v_name else name end,
      description = case when private.jsonb_has(payload, 'description') then nullif(payload->>'description', '') else description end,
      happened_at = coalesce(v_when, happened_at)
    where id = v_id and user_id = v_user;
    if not found then
      raise exception 'moment_not_found' using errcode = 'P0002';
    end if;
  end if;

  -- snaps: whole-set replace, per-moment quota (the profile column keeps its
  -- historical name). auth.uid() matches profiles.user_id, not profiles.id (#618).
  if private.jsonb_has(payload, 'snaps') then
    select coalesce(max_media_per_pebble, 1) into v_max_media
      from public.profiles where user_id = v_user;
    if jsonb_array_length(payload->'snaps') > coalesce(v_max_media, 1) then
      raise exception 'media_quota_exceeded' using errcode = 'P0001';
    end if;
    delete from public.snaps where moment_id = v_id;
    for v_snap in select * from jsonb_array_elements(payload->'snaps') loop
      if nullif(v_snap.value->>'storage_path', '') is null then
        raise exception 'invalid_snap' using errcode = '22023', detail = 'storage_path is required';
      end if;
      insert into public.snaps (id, moment_id, user_id, storage_path, sort_order)
      values (
        coalesce(nullif(v_snap.value->>'id', '')::uuid, gen_random_uuid()),
        v_id, v_user, v_snap.value->>'storage_path',
        coalesce((v_snap.value->>'sort_order')::smallint, 0)
      );
    end loop;
  end if;

  -- D3: a date plus a name or a photo. Raising here rolls the whole call back.
  select count(*) into v_snaps from public.snaps where moment_id = v_id;
  if (select m.name from public.moments m where m.id = v_id) is null and v_snaps = 0 then
    raise exception 'moment_empty' using errcode = 'P0001';
  end if;

  return v_id;
end;
$$;

-- complete_pebble ----------------------------------------------------------------
create function public.complete_pebble(payload jsonb)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user          uuid := auth.uid();
  v_moment_id     uuid := (payload->>'moment_id')::uuid;
  v_pebble_id     uuid;
  v_is_new        boolean;
  v_soul_ids      uuid[];
  v_collection_ids uuid[];
  v_new           record;
  v_new_id        uuid;
  v_card          record;
  v_bad_collection uuid;
  v_bad_soul      uuid;
  v_has_desc      boolean;
  v_cards         int;
  v_souls         int;
  v_domains       int;
  v_snaps         int;
  v_new_karma     int;
  v_old_karma     int;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  -- Owner today; "is a participant" when pairs land.
  if not exists (select 1 from public.moments m where m.id = v_moment_id and m.user_id = v_user) then
    raise exception 'moment_not_found' using errcode = 'P0002';
  end if;
  perform private.assert_jsonb_array(payload, 'domain_ids');
  perform private.assert_jsonb_array(payload, 'soul_ids');
  perform private.assert_jsonb_array(payload, 'new_souls');
  perform private.assert_jsonb_array(payload, 'collection_ids');
  perform private.assert_jsonb_array(payload, 'new_collections');
  perform private.assert_jsonb_array(payload, 'cards');

  -- Serialise completions of one (moment, user): closes the double-insert and
  -- the stale karma-sum races. Taken after validation so a rejected call never
  -- waits; the unique index stays as the loud backstop (house pattern:
  -- 20261007120000 record_consent).
  perform pg_advisory_xact_lock(hashtextextended('complete_pebble:' || v_moment_id::text || ':' || v_user::text, 0));

  select id into v_pebble_id from public.pebbles where moment_id = v_moment_id and user_id = v_user;
  v_is_new := v_pebble_id is null;

  -- D2: a pebble needs at least one domain. Required on insert; on update the
  -- key may be absent or null (keep) but never empty.
  if (v_is_new or private.jsonb_has(payload, 'domain_ids'))
     and (case when private.jsonb_has(payload, 'domain_ids') then jsonb_array_length(payload->'domain_ids') else 0 end) = 0 then
    raise exception 'pebble_needs_domain' using errcode = 'P0001';
  end if;
  if v_is_new and (payload->>'emotion_id') is null then
    raise exception 'pebble_needs_emotion' using errcode = 'P0001';
  end if;
  if v_is_new and ((payload->>'intensity') is null or (payload->>'positiveness') is null) then
    raise exception 'pebble_needs_valence' using errcode = 'P0001';
  end if;

  -- souls: existing ids ∪ inline creations
  if private.jsonb_has(payload, 'soul_ids') or private.jsonb_has(payload, 'new_souls') then
    select array_agg(val::uuid) into v_soul_ids
      from jsonb_array_elements_text(case when private.jsonb_has(payload, 'soul_ids') then payload->'soul_ids' else '[]'::jsonb end) val;
    v_soul_ids := coalesce(v_soul_ids, '{}');
    -- Ownership of the existing ids first, before any write.
    select s_id into v_bad_soul from unnest(v_soul_ids) s_id
     where not exists (select 1 from public.souls where id = s_id and user_id = v_user) limit 1;
    if v_bad_soul is not null then
      raise exception 'soul_not_owned' using errcode = '42501', detail = v_bad_soul::text;
    end if;
    for v_new in select * from jsonb_array_elements(case when private.jsonb_has(payload, 'new_souls') then payload->'new_souls' else '[]'::jsonb end) loop
      insert into public.souls (user_id, name) values (v_user, v_new.value->>'name') returning id into v_new_id;
      v_soul_ids := array_append(v_soul_ids, v_new_id);
    end loop;
  end if;

  -- collections: existing ids ∪ inline creations, then ownership
  if private.jsonb_has(payload, 'collection_ids') or private.jsonb_has(payload, 'new_collections') then
    select array_agg(val::uuid) into v_collection_ids
      from jsonb_array_elements_text(case when private.jsonb_has(payload, 'collection_ids') then payload->'collection_ids' else '[]'::jsonb end) val;
    v_collection_ids := coalesce(v_collection_ids, '{}');
    select c_id into v_bad_collection from unnest(v_collection_ids) c_id
     where not exists (select 1 from public.collections where id = c_id and user_id = v_user) limit 1;
    if v_bad_collection is not null then
      raise exception 'collection_not_owned' using errcode = '42501', detail = v_bad_collection::text;
    end if;
    for v_new in select * from jsonb_array_elements(case when private.jsonb_has(payload, 'new_collections') then payload->'new_collections' else '[]'::jsonb end) loop
      insert into public.collections (user_id, name) values (v_user, v_new.value->>'name') returning id into v_new_id;
      v_collection_ids := array_append(v_collection_ids, v_new_id);
    end loop;
  end if;

  if v_is_new then
    insert into public.pebbles (user_id, moment_id, emotion_id, intensity, positiveness, visibility)
    values (
      v_user, v_moment_id,
      (payload->>'emotion_id')::uuid,
      (payload->>'intensity')::smallint,
      (payload->>'positiveness')::smallint,
      coalesce(payload->>'visibility', 'secret')
    )
    returning id into v_pebble_id;
  else
    update public.pebbles set
      emotion_id   = coalesce((payload->>'emotion_id')::uuid, emotion_id),
      intensity    = coalesce((payload->>'intensity')::smallint, intensity),
      positiveness = coalesce((payload->>'positiveness')::smallint, positiveness),
      visibility   = coalesce(payload->>'visibility', visibility)
    where id = v_pebble_id;
  end if;

  if private.jsonb_has(payload, 'cards') then
    delete from public.pebble_cards where pebble_id = v_pebble_id;
    for v_card in select * from jsonb_array_elements(payload->'cards') loop
      insert into public.pebble_cards (pebble_id, species_id, value, sort_order)
      values (v_pebble_id, (v_card.value->>'species_id')::uuid, v_card.value->>'value',
              coalesce((v_card.value->>'sort_order')::smallint, 0));
    end loop;
  end if;
  if v_soul_ids is not null then
    delete from public.pebble_souls where pebble_id = v_pebble_id;
    insert into public.pebble_souls (pebble_id, soul_id) select v_pebble_id, unnest(v_soul_ids);
  end if;
  if private.jsonb_has(payload, 'domain_ids') then
    delete from public.pebble_domains where pebble_id = v_pebble_id;
    insert into public.pebble_domains (pebble_id, domain_id)
    select v_pebble_id, (val::text)::uuid from jsonb_array_elements_text(payload->'domain_ids') val;
  end if;
  if v_collection_ids is not null then
    delete from public.collection_pebbles where pebble_id = v_pebble_id;
    insert into public.collection_pebbles (collection_id, pebble_id) select unnest(v_collection_ids), v_pebble_id;
  end if;

  -- karma: D4, rewarded on the first completion; enrichment deltas after.
  select m.description is not null and m.description <> '' into v_has_desc from public.moments m where m.id = v_moment_id;
  select count(*) into v_cards   from public.pebble_cards   where pebble_id = v_pebble_id;
  select count(*) into v_souls   from public.pebble_souls   where pebble_id = v_pebble_id;
  select count(*) into v_domains from public.pebble_domains where pebble_id = v_pebble_id;
  select count(*) into v_snaps   from public.snaps          where moment_id = v_moment_id;
  v_new_karma := public.compute_karma_delta(v_has_desc, v_cards, v_souls, v_domains, v_snaps);

  if v_is_new then
    insert into public.karma_events (user_id, delta, reason, ref_id)
    values (v_user, v_new_karma, 'pebble_created', v_pebble_id);
  else
    select coalesce(sum(delta), 0) into v_old_karma
      from public.karma_events where ref_id = v_pebble_id and user_id = v_user;
    if v_new_karma <> v_old_karma then
      insert into public.karma_events (user_id, delta, reason, ref_id)
      values (v_user, v_new_karma - v_old_karma, 'pebble_enriched', v_pebble_id);
    end if;
  end if;

  return v_pebble_id;
end;
$$;

-- delete_pebble: the pebble only, the moment stays ----------------------------------
create or replace function public.delete_pebble(p_pebble_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user  uuid := auth.uid();
  v_total int;
begin
  if not exists (select 1 from public.pebbles where id = p_pebble_id and user_id = v_user) then
    -- Historical sentence kept for compatibility: existing clients match on it.
    raise exception 'Pebble not found or access denied';
  end if;
  select coalesce(sum(delta), 0) into v_total from public.karma_events where ref_id = p_pebble_id and user_id = v_user;
  if v_total > 0 then
    insert into public.karma_events (user_id, delta, reason, ref_id) values (v_user, -v_total, 'pebble_deleted', p_pebble_id);
  end if;
  delete from public.pebbles where id = p_pebble_id;
end;
$$;

-- delete_moment: the moment, its pebbles and snaps; returns the files to remove ------
create function public.delete_moment(p_moment_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user  uuid := auth.uid();
  v_p     record;
  v_total int;
  v_paths text[];
begin
  if not exists (select 1 from public.moments where id = p_moment_id and user_id = v_user) then
    raise exception 'moment_not_found' using errcode = 'P0002';
  end if;
  -- Every pebble on the moment, not only the caller's: forward-compatible with
  -- participants, whose pebbles go with the moment and whose karma must follow.
  for v_p in select id, user_id from public.pebbles where moment_id = p_moment_id loop
    select coalesce(sum(delta), 0) into v_total from public.karma_events where ref_id = v_p.id and user_id = v_p.user_id;
    if v_total > 0 then
      insert into public.karma_events (user_id, delta, reason, ref_id) values (v_p.user_id, -v_total, 'pebble_deleted', v_p.id);
    end if;
  end loop;
  select coalesce(array_agg(storage_path), '{}') into v_paths from public.snaps where moment_id = p_moment_id;
  delete from public.moments where id = p_moment_id;
  return jsonb_build_object('storage_paths', to_jsonb(v_paths));
end;
$$;

-- delete_moment_media replaces delete_pebble_media (same body, new name) --------------
drop function public.delete_pebble_media(uuid);
create function public.delete_moment_media(p_snap_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_path text;
begin
  delete from public.snaps where id = p_snap_id and user_id = v_user returning storage_path into v_path;
  if v_path is null then raise exception 'snap_not_found' using errcode = 'P0002'; end if;
  return v_path;
end;
$$;

-- set_collection_glyph: named for symmetry with souls; the trigger guards it --------
create function public.set_collection_glyph(p_collection_id uuid, p_glyph_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.collections set glyph_id = p_glyph_id
   where id = p_collection_id and user_id = auth.uid();
  if not found then raise exception 'collection_not_found' using errcode = 'P0002'; end if;
end;
$$;

revoke all on function public.save_moment(jsonb)                      from public, anon;
revoke all on function public.complete_pebble(jsonb)                  from public, anon;
revoke all on function public.delete_pebble(uuid)                     from public, anon;
revoke all on function public.delete_moment(uuid)                     from public, anon;
revoke all on function public.delete_moment_media(uuid)               from public, anon;
revoke all on function public.set_collection_glyph(uuid, uuid)        from public, anon;
grant execute on function public.save_moment(jsonb)               to authenticated;
grant execute on function public.complete_pebble(jsonb)           to authenticated;
grant execute on function public.delete_pebble(uuid)              to authenticated;
grant execute on function public.delete_moment(uuid)              to authenticated;
grant execute on function public.delete_moment_media(uuid)        to authenticated;
grant execute on function public.set_collection_glyph(uuid, uuid) to authenticated;
