-- =============================================================================
-- Privacy grades (#708, M51) — activate pebbles.visibility as
-- secret | private | public, backfill to secret, visibility-aware RLS,
-- and anonymous public-by-link via get_shared_pebble.
-- =============================================================================
-- The column has existed since 20260411000001 (default 'private') but nothing
-- ever read it. Now that connections exist (M49), 'private' is reinterpreted
-- as CONNECTIONS-VISIBLE — so every existing pebble is backfilled to 'secret'
-- (owner-only): they were all created under owner-only expectations, and
-- letting them become connection-visible the day the grade activates would be
-- a privacy regression. Spec: roadmap §M51
-- (docs/superpowers/specs/2026-07-28-store-launch-roadmap.md).
--
-- What each grade exposes to a non-owner: the pebbles ROW only — core columns
-- plus render_svg (the glyph is already baked into the SVG, sidestepping
-- cross-user glyph/snap RLS entirely). Enrichment tables (pebble_cards,
-- pebble_souls, snaps, pebble_domains via parent join) keep owner-only RLS
-- deliberately; post-F1 (20260729000000) v_pebbles_full is security_invoker,
-- so it returns empty enrichment arrays for non-owners automatically.
--
-- purge_account needs no extension: no new table, and pebbles are already
-- purged. No emotion/domain inserts, so no sync_achievement_catalog() re-run.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Backfill BEFORE the CHECK lands. The pebbles_updated_at trigger is
-- disabled around the sweep: a privacy re-grade is not a user edit, and
-- bumping updated_at on every row would scramble client sync heuristics.
-- ---------------------------------------------------------------------------
alter table public.pebbles disable trigger pebbles_updated_at;

update public.pebbles
   set visibility = 'secret'
 where visibility is distinct from 'secret';

alter table public.pebbles enable trigger pebbles_updated_at;

-- ---------------------------------------------------------------------------
-- 2. Grade domain + new default. The CHECK makes invalid grades
-- unrepresentable for every write path (create_pebble, update_pebble, any
-- direct client update under pebbles_update RLS).
-- ---------------------------------------------------------------------------
alter table public.pebbles
  add constraint pebbles_visibility_check
    check (visibility in ('secret', 'private', 'public'));

alter table public.pebbles
  alter column visibility set default 'secret';

-- ---------------------------------------------------------------------------
-- 3. Visibility-aware read RLS. Owner sees everything; a mutual connection
-- (M49) sees 'private'; any authenticated user sees 'public'. Writes stay
-- owner-only (insert/update/delete policies untouched). The connections pair
-- is canonical (user_a < user_b), so least/greatest lands the probe on the
-- unique (user_a, user_b) index.
--
-- Scoped `to authenticated` where the original was role-less: the anon road
-- to a public pebble is get_shared_pebble below, never the table — a table
-- read with the publishable key would make public pebbles listable as a
-- directory, while the share link is meant to be reach-by-uuid only.
-- ---------------------------------------------------------------------------
drop policy "pebbles_select" on public.pebbles;

create policy "pebbles_select" on public.pebbles
  for select to authenticated using (
    user_id = auth.uid()
    or visibility = 'public'
    or (
      visibility = 'private'
      and exists (
        select 1
          from public.connections c
         where c.user_a = least(auth.uid(), pebbles.user_id)
           and c.user_b = greatest(auth.uid(), pebbles.user_id)
      )
    )
  );

-- Defense in depth (pattern: v_pebbles_full, 20260729000000): anon holds the
-- default public-schema grant but has no legitimate direct read or write.
revoke all on public.pebbles from anon;

-- ---------------------------------------------------------------------------
-- 4. get_shared_pebble — the anon-facing projection (pattern:
-- get_public_profile, 20260730120000). Null unless the pebble exists AND is
-- 'public' — unknown and known-but-ungraded are indistinguishable to the
-- caller (enumeration resistance). The uuid itself is the capability: 122
-- unguessable bits; revocation = flip the grade back; no token table in v1.
--
-- Projected keys only — deliberately excluded: user_id (no cross-user
-- identifier leaks, standing rule), glyph_id and raw glyph geometry
-- (render_svg already carries the visual), visibility (constant 'public' by
-- construction), created_at/updated_at, and all enrichments (cards, souls,
-- snaps, domains — snaps are excluded from public shares in v1, spec §M51).
-- The emotion palette rides along so the anonymous page can tint the
-- server-composed SVG without a second query. happened_at is emitted at
-- whole-second UTC (standing timestamp rule).
-- ---------------------------------------------------------------------------
create function public.get_shared_pebble(p_pebble_id uuid)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  select jsonb_build_object(
    'id', p.id,
    'name', p.name,
    'description', p.description,
    'happened_at', to_char(p.happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'intensity', p.intensity,
    'positiveness', p.positiveness,
    'render_svg', p.render_svg,
    'emotion', jsonb_build_object(
      'id', e.id,
      'slug', e.slug,
      'name', e.name,
      'color', e.color,
      'primary_color', c.primary_color,
      'secondary_color', c.secondary_color
    )
  )
  from public.pebbles p
  join public.emotions e on e.id = p.emotion_id
  left join public.emotion_categories c on c.id = e.category_id
  where p.id = p_pebble_id
    and p.visibility = 'public';
$$;

revoke all on function public.get_shared_pebble(uuid) from public;
grant execute on function public.get_shared_pebble(uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 5. create_pebble: the coalesce default flips 'private' -> 'secret' so a
-- payload without an explicit grade records owner-only (matching the new
-- column default). update_pebble needs no re-emission — its coalesce falls
-- back to the row's existing value, and the CHECK guards bad inputs.
--
-- STANDING-RULE CHECK (re-emitting a whole function body): base body is the
-- latest emission, 20260729140000_media_quota_profile_lookup.sql lines
-- 24-213, copied verbatim — diffed against every prior emission's tail; no
-- later migration touches create_pebble. Only the coalesce line changes.
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
    coalesce(payload->>'visibility', 'secret'),
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
