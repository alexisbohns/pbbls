-- =============================================================================
-- Drop deprecated glyph shape scaffolding (#503)
-- =============================================================================
-- Glyphs are shape-agnostic square drawings: the stroke is always 6px in glyph
-- space and scaled into the pebble slot, so `glyphs.shape_id` (and the orphaned
-- `pebble_shapes` lookup table it referenced) carry no meaning. There is no live
-- data depending on either, so this migration removes both.
--
-- Order matters: every dependent object (RPCs + views) must stop referencing
-- `glyphs.shape_id` BEFORE the column is dropped, so we recreate them first
-- (a–e) and only then drop the column and the table (f).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- a. create_pebble — drop shape_id from the inline glyph INSERT
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
-- b. publish_admin_glyph — drop the p_shape_id parameter (signature change)
-- ---------------------------------------------------------------------------
drop function if exists public.publish_admin_glyph(text, uuid, jsonb, text, integer);

create function public.publish_admin_glyph(
  p_name text,
  p_strokes jsonb,
  p_view_box text,
  p_price integer
)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user          uuid := auth.uid();
  v_glyph_id      uuid;
  v_submission_id uuid;
begin
  if not public.is_admin(v_user) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_price <= 0 then raise exception 'bad_price'; end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  insert into public.glyphs (user_id, name, strokes, view_box)
  values (v_user, nullif(btrim(p_name), ''), p_strokes, p_view_box)
  returning id into v_glyph_id;

  insert into public.glyph_submissions
    (glyph_id, submitter_id, status, price, reviewed_at, reviewed_by)
  values (v_glyph_id, v_user, 'approved', p_price, now(), v_user)
  returning id into v_submission_id;

  return jsonb_build_object('glyph_id', v_glyph_id, 'submission_id', v_submission_id);
end;
$$;

revoke all on function public.publish_admin_glyph(text, jsonb, text, integer) from public, anon;
grant execute on function public.publish_admin_glyph(text, jsonb, text, integer) to authenticated;

-- ---------------------------------------------------------------------------
-- c. admin_list_glyph_submissions — drop g.shape_id from the inner select
-- ---------------------------------------------------------------------------
create or replace function public.admin_list_glyph_submissions(p_status text default null)
returns jsonb
language plpgsql security definer set search_path = public, auth as $$
declare
  v_result jsonb;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at), '[]'::jsonb)
  into v_result
  from (
    select
      s.id           as submission_id,
      s.glyph_id     as glyph_id,
      s.status       as status,
      s.listed       as listed,
      s.price        as price,
      s.review_note  as review_note,
      s.created_at   as created_at,
      s.reviewed_at  as reviewed_at,
      s.submitter_id as submitter_id,
      su.email       as submitter_email,
      g.user_id      as owner_id,
      ou.email       as owner_email,
      g.name         as name,
      g.strokes      as strokes,
      g.view_box     as view_box
    from public.glyph_submissions s
    join public.glyphs g on g.id = s.glyph_id
    left join auth.users su on su.id = s.submitter_id
    left join auth.users ou on ou.id = g.user_id
    where p_status is null or s.status = p_status
  ) t;

  return v_result;
end;
$$;

-- ---------------------------------------------------------------------------
-- d. v_pebbles_full — drop 'shape_id' from the glyph jsonb_build_object
-- ---------------------------------------------------------------------------
create or replace view public.v_pebbles_full as
select
  p.id,
  p.user_id,
  p.name,
  p.description,
  p.happened_at,
  p.intensity,
  p.positiveness,
  p.visibility,
  p.emotion_id,
  p.glyph_id,
  p.created_at,
  p.updated_at,

  -- emotion (1:1, always present)
  jsonb_build_object(
    'id',    e.id,
    'slug',  e.slug,
    'name',  e.name,
    'color', e.color
  ) as emotion,

  -- glyph (1:1, optional)
  case when g.id is not null then
    jsonb_build_object(
      'id',       g.id,
      'name',     g.name,
      'strokes',  g.strokes,
      'view_box', g.view_box
    )
  else null end as glyph,

  -- cards (1:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',         pc.id,
        'species_id', pc.species_id,
        'value',      pc.value,
        'sort_order', pc.sort_order
      ) order by pc.sort_order
    )
    from public.pebble_cards pc
    where pc.pebble_id = p.id),
    '[]'::jsonb
  ) as cards,

  -- souls (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',   s.id,
        'name', s.name
      ) order by s.name
    )
    from public.pebble_souls ps
    join public.souls s on s.id = ps.soul_id
    where ps.pebble_id = p.id),
    '[]'::jsonb
  ) as souls,

  -- domains (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',    d.id,
        'slug',  d.slug,
        'name',  d.name,
        'label', d.label
      ) order by d.slug
    )
    from public.pebble_domains pd
    join public.domains d on d.id = pd.domain_id
    where pd.pebble_id = p.id),
    '[]'::jsonb
  ) as domains,

  -- snaps (1:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',           sn.id,
        'storage_path', sn.storage_path,
        'sort_order',   sn.sort_order
      ) order by sn.sort_order
    )
    from public.snaps sn
    where sn.pebble_id = p.id),
    '[]'::jsonb
  ) as snaps,

  -- collections (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',   c.id,
        'name', c.name,
        'mode', c.mode
      ) order by c.name
    )
    from public.collection_pebbles cp
    join public.collections c on c.id = cp.collection_id
    where cp.pebble_id = p.id),
    '[]'::jsonb
  ) as collections

from public.pebbles p
join public.emotions e on e.id = p.emotion_id
left join public.glyphs g on g.id = p.glyph_id;

-- ---------------------------------------------------------------------------
-- e. v_glyph_market — drop the top-level g.shape_id column (signature change)
-- ---------------------------------------------------------------------------
drop view if exists public.v_glyph_market;

create view public.v_glyph_market with (security_invoker = true) as
select
  g.id, g.user_id, g.name, g.strokes, g.view_box,
  g.created_at, g.updated_at,
  s.price,
  exists (select 1 from public.glyph_entitlements e
          where e.glyph_id = g.id and e.user_id = auth.uid()) as owned,
  exists (select 1 from public.glyph_favourites f
          where f.glyph_id = g.id and f.user_id = auth.uid()) as favourited
from public.glyph_submissions s
join public.glyphs g on g.id = s.glyph_id
where s.status = 'approved' and s.listed;

revoke all on public.v_glyph_market from public, anon;
grant select on public.v_glyph_market to authenticated;

-- ---------------------------------------------------------------------------
-- f. Drop the column and the orphaned lookup table (now unreferenced)
-- ---------------------------------------------------------------------------
alter table public.glyphs drop column shape_id;
drop table public.pebble_shapes;
