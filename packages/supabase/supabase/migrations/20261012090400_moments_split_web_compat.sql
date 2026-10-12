-- =============================================================================
-- Moments and pebbles split — web compatibility layer (spec §5)
--
-- DEPRECATED ON ARRIVAL. The web app still reads v_pebbles_full, calls
-- path_pebbles and writes through the compose-pebble / compose-pebble-update
-- edge functions, which call create_pebble(payload) / update_pebble(id, payload).
-- This file recreates those four objects over the moments schema so web keeps
-- working until its composer ships; every one of them is removed with the web
-- composer (#1050). Nothing new may depend on them — new readers use
-- v_moments_full / path_moments, new writers save_moment / complete_pebble.
--
-- NOT safe to push alone: 20261012090000–20261012090400 ship together.
-- 20261012090000 dropped v_pebbles_full and the pebble columns the old
-- create_pebble / update_pebble / path_pebbles bodies still name; those three
-- functions exist with broken bodies until this file replaces them.
--
-- Compat concessions (the old contract wins over the new conventions, because
-- the web payload builder is unchanged until #1050):
--   * glyph_id / new_glyph are ignored (D5: the glyph is derived).
--   * create_pebble: `jsonb_strip_nulls` on the moment part — harmless, since
--     save_moment already treats JSON null as absent; kept so the intent reads.
--   * update_pebble: `description: null` means CLEAR on the web contract
--     (apps/web/lib/data/data-provider.ts), while save_moment reads null as
--     absent; the wrapper rewrites it to '' so save_moment's nullif clears it.
--   * update_pebble: web can send `domain_ids: []` on an edit (PebbleDetail's
--     DomainTile, usePebbleDraft); complete_pebble refuses an empty set (D2,
--     pebble_needs_domain). The wrapper drops an empty domain_ids and keeps the
--     pebble's domains instead of failing the whole edit. On create the
--     empty set still raises pebble_needs_domain: a pebble needs a domain.
--   Behaviour differences web will observe (new RPC semantics, accepted):
--   * `description: ''` stores null (was '').
--   * a blank name with no snaps raises moment_empty (D3).
--   * happened_at is truncated to whole seconds.
--   * enrichment errors are slugs: collection_not_owned, soul_not_owned,
--     invalid_payload (were sentences).
--   * the first edit of an old glyph-bearing pebble may post a negative karma
--     delta: the glyph bonus is gone from compute_karma_delta.
--   * v_pebbles_full.updated_at is greatest(pebble, moment), so a name /
--     description / date / snaps edit still moves it.
--
-- Seven harnesses under packages/supabase/scripts/ call create_pebble or read
-- v_pebbles_full; they must be migrated to save_moment / complete_pebble /
-- v_moments_full or retargeted before #1050 removes these objects.
--
-- Grants: v_pebbles_full repeats the 20260729000000 policy (revoke public +
-- anon, select to authenticated). path_pebbles keeps its 20260519141000
-- signature, so `create or replace` carries its authenticated grant.
-- create_pebble / update_pebble never had an explicit grant (default
-- privileges); they now get the batch's policy — revoke public + anon, execute
-- to authenticated. The edge functions forward the caller's JWT, so nothing
-- else changes.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. v_pebbles_full in its 20260701114205 / 20260729000000 column set and
-- order. glyph = the resolved glyph (D5) with its extra `source` key,
-- glyph_id = its id; the old left join on pebbles.glyph_id is gone.
-- collections keeps the old element shape (id, name, mode).
-- ---------------------------------------------------------------------------
create view public.v_pebbles_full
with (security_invoker = true) as
select
  p.id,
  p.user_id,
  m.name,
  m.description,
  m.happened_at,
  p.intensity,
  p.positiveness,
  p.visibility,
  p.emotion_id,
  (g.glyph->>'id')::uuid as glyph_id,
  p.created_at,
  -- A name/description/date/snaps edit lives on the moment: keep updated_at moving.
  greatest(p.updated_at, m.updated_at) as updated_at,

  -- emotion (1:1, always present)
  jsonb_build_object(
    'id',    e.id,
    'slug',  e.slug,
    'name',  e.name,
    'color', e.color
  ) as emotion,

  -- glyph (derived, optional): id, name, strokes, view_box, source
  g.glyph as glyph,

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

  -- snaps (1:N, on the moment)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',           sn.id,
        'storage_path', sn.storage_path,
        'sort_order',   sn.sort_order
      ) order by sn.sort_order
    )
    from public.snaps sn
    where sn.moment_id = p.moment_id),
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
join public.moments m on m.id = p.moment_id
join public.emotions e on e.id = p.emotion_id
-- resolved once per row, shared by glyph_id and glyph
cross join lateral (select public.resolve_pebble_glyph(p.id) as glyph) g;

-- Defense in depth (20260729000000): anon has no legitimate read on the view.
revoke all on public.v_pebbles_full from public, anon;
grant select on public.v_pebbles_full to authenticated;
comment on view public.v_pebbles_full is
  'DEPRECATED compat projection of moment + pebble in the pre-split column set; glyph is the derived glyph (D5). Web only. Removed with the web composer (#1050); read v_moments_full instead.';

-- ---------------------------------------------------------------------------
-- 2. path_pebbles in its 20260519141000 signature; render_svg is null (D6).
-- ---------------------------------------------------------------------------
create or replace function public.path_pebbles()
returns table (
  id uuid,
  name text,
  happened_at timestamptz,
  created_at timestamptz,
  intensity smallint,
  positiveness smallint,
  render_svg text,
  emotion jsonb,
  first_snap_path text
)
language sql
security invoker
stable
set search_path = public
as $$
  select
    p.id,
    m.name,
    m.happened_at,
    p.created_at,
    p.intensity,
    p.positiveness,
    null::text as render_svg,
    case
      when e.id is null then null
      else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name)
    end as emotion,
    (
      select s.storage_path
      from public.snaps s
      where s.moment_id = m.id
      order by s.sort_order asc nulls last, s.created_at asc
      limit 1
    ) as first_snap_path
  from public.pebbles p
  join public.moments m on m.id = p.moment_id
  left join public.emotions e on e.id = p.emotion_id
  where p.user_id = auth.uid()
  order by m.happened_at desc;
$$;
comment on function public.path_pebbles() is
  'DEPRECATED compat timeline in the pre-split shape; render_svg is always null. Web only. Removed with the web composer (#1050); call path_moments instead.';

-- ---------------------------------------------------------------------------
-- 3. create_pebble / update_pebble: one call, one complete pebble, as today.
-- Moment keys (name, description, happened_at, snaps) go to save_moment, the
-- rest plus moment_id to complete_pebble. Glyph keys are dropped on the floor.
-- Same signatures as 20260817130000 / 20260729140000 so `create or replace`
-- carries the grants.
-- ---------------------------------------------------------------------------
create or replace function public.create_pebble(payload jsonb)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_moment uuid;
begin
  v_moment := public.save_moment(jsonb_strip_nulls(jsonb_build_object(
    'name',        payload->'name',
    'description', payload->'description',
    'happened_at', payload->'happened_at',
    'snaps',       payload->'snaps')));
  return public.complete_pebble(
    (payload - 'name' - 'description' - 'happened_at' - 'snaps' - 'glyph_id' - 'new_glyph')
    || jsonb_build_object('moment_id', v_moment));
end;
$$;
revoke all on function public.create_pebble(jsonb) from public, anon;
grant execute on function public.create_pebble(jsonb) to authenticated;
comment on function public.create_pebble(jsonb) is
  'DEPRECATED compat wrapper over save_moment + complete_pebble; glyph keys are ignored. Web only. Removed with the web composer (#1050).';

create or replace function public.update_pebble(p_pebble_id uuid, payload jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_moment      uuid;
  v_moment_part jsonb;
  v_pebble_part jsonb;
begin
  -- Historical sentence kept for compatibility: existing clients match on it.
  select moment_id into v_moment
    from public.pebbles
   where id = p_pebble_id and user_id = auth.uid();
  if v_moment is null then
    raise exception 'Pebble not found or access denied';
  end if;

  -- Moment part: the four moment keys only, when present. An explicit null
  -- description is a clear on the old contract; save_moment reads null as
  -- absent, so hand it '' (its nullif turns that into null).
  v_moment_part := jsonb_strip_nulls(jsonb_build_object(
    'name',        payload->'name',
    'happened_at', payload->'happened_at',
    'snaps',       payload->'snaps'));
  -- `->>` yields SQL NULL for a JSON null (`->` would yield a jsonb null and
  -- never fall through the coalesce).
  if payload ? 'description' then
    v_moment_part := v_moment_part
      || jsonb_build_object('description', to_jsonb(coalesce(payload->>'description', '')));
  end if;
  if v_moment_part <> '{}'::jsonb then
    perform public.save_moment(v_moment_part || jsonb_build_object('id', v_moment));
  end if;

  -- Pebble part: everything else. An empty domain_ids would fail the edit
  -- (D2); drop it and keep the pebble's domains. With no pebble key left this
  -- is a no-op update in complete_pebble (karma re-read included).
  v_pebble_part := payload - 'name' - 'description' - 'happened_at' - 'snaps' - 'glyph_id' - 'new_glyph';
  if jsonb_typeof(v_pebble_part->'domain_ids') = 'array'
     and jsonb_array_length(v_pebble_part->'domain_ids') = 0 then
    v_pebble_part := v_pebble_part - 'domain_ids';
  end if;
  perform public.complete_pebble(v_pebble_part || jsonb_build_object('moment_id', v_moment));
end;
$$;
revoke all on function public.update_pebble(uuid, jsonb) from public, anon;
grant execute on function public.update_pebble(uuid, jsonb) to authenticated;
comment on function public.update_pebble(uuid, jsonb) is
  'DEPRECATED compat wrapper over save_moment + complete_pebble; glyph keys are ignored, an empty domain_ids is dropped. Web only. Removed with the web composer (#1050).';
