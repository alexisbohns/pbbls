-- =============================================================================
-- Moments and pebbles split — readers (spec §4)
-- resolve_pebble_glyph / v_moments_full / path_moments / get_shared_pebble
--
-- NOT safe to push alone: 20261012090000–20261012090400 ship together.
-- v_pebbles_full and path_pebbles come back in compat form in 20261012090400.
-- Supersedes 20260817130000's exclusion of raw glyph geometry from the share
-- payload: with render_svg gone (D6), the strokes are the visual.
-- =============================================================================

-- The resolver and the view probe collection_pebbles by pebble per row; the
-- primary key leads on collection_id, so nothing indexes that probe today.
create index collection_pebbles_pebble_id_idx on public.collection_pebbles (pebble_id);

-- D5: oldest collection carrying a glyph, else first domain default, else null.
-- Two layers. private.resolve_pebble_glyph is the definer resolution: every
-- viewer carves the same stone, because collection_pebbles is owner-readable
-- and a connection would otherwise see a different glyph. `private` is never
-- served by PostgREST, so it is not an API; the authenticated grant is for the
-- invoker wrapper below.
create function private.resolve_pebble_glyph(p_pebble_id uuid)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  with candidates as (
    select c.glyph_id, 'collection' as source, 0 as rank, c.created_at as ord, c.name as tie
      from public.collection_pebbles cp
      join public.collections c on c.id = cp.collection_id
     where cp.pebble_id = p_pebble_id and c.glyph_id is not null
    union all
    select d.default_glyph_id, 'domain', 1, null::timestamptz, d.slug
      from public.pebble_domains pd
      join public.domains d on d.id = pd.domain_id
     where pd.pebble_id = p_pebble_id and d.default_glyph_id is not null
  ),
  pick as (
    select * from candidates order by rank, ord nulls last, tie limit 1
  )
  select jsonb_build_object(
    'id', g.id, 'name', g.name, 'strokes', g.strokes, 'view_box', g.view_box, 'source', pick.source
  )
  from pick join public.glyphs g on g.id = pick.glyph_id;
$$;
revoke all on function private.resolve_pebble_glyph(uuid) from public, anon;
grant execute on function private.resolve_pebble_glyph(uuid) to authenticated;

-- public.resolve_pebble_glyph is the API: invoker, gated by pebble readability.
-- The exists runs under the caller's pebbles_select, so a pebble the caller
-- cannot read yields null — the same answer as an unknown id, which closes the
-- by-uuid oracle a bare definer would open on a secret pebble's custom glyph.
create function public.resolve_pebble_glyph(p_pebble_id uuid)
returns jsonb
language sql
security invoker
stable
set search_path = public
as $$
  select private.resolve_pebble_glyph(p_pebble_id)
   where exists (select 1 from public.pebbles p where p.id = p_pebble_id);
$$;
revoke all on function public.resolve_pebble_glyph(uuid) from public, anon;
grant execute on function public.resolve_pebble_glyph(uuid) to authenticated;
comment on function public.resolve_pebble_glyph(uuid) is
  'The pebble''s derived glyph (D5): oldest collection with a glyph, else first domain default, else null. Gated by pebble readability: a pebble the caller cannot read answers null, the same as an unknown id.';

-- v_moments_full: one row per readable moment, the caller's own pebble nested.
create view public.v_moments_full
with (security_invoker = true) as
select
  m.id, m.user_id, m.name, m.description, m.happened_at, m.hidden_at, m.created_at, m.updated_at,
  coalesce((
    select jsonb_agg(jsonb_build_object('id', sn.id, 'storage_path', sn.storage_path, 'sort_order', sn.sort_order)
                     order by sn.sort_order)
      from public.snaps sn where sn.moment_id = m.id), '[]'::jsonb) as snaps,
  (
    select jsonb_build_object(
      'id', p.id,
      'emotion_id', p.emotion_id,
      'intensity', p.intensity,
      'positiveness', p.positiveness,
      'visibility', p.visibility,
      'created_at', p.created_at,
      'updated_at', p.updated_at,
      'emotion', jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name, 'color', e.color),
      'glyph', public.resolve_pebble_glyph(p.id),
      'cards', coalesce((select jsonb_agg(jsonb_build_object('id', pc.id, 'species_id', pc.species_id, 'value', pc.value, 'sort_order', pc.sort_order) order by pc.sort_order)
                          from public.pebble_cards pc where pc.pebble_id = p.id), '[]'::jsonb),
      'souls', coalesce((select jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name) order by s.name)
                          from public.pebble_souls ps join public.souls s on s.id = ps.soul_id where ps.pebble_id = p.id), '[]'::jsonb),
      'domains', coalesce((select jsonb_agg(jsonb_build_object('id', d.id, 'slug', d.slug, 'name', d.name, 'label', d.label) order by d.slug)
                          from public.pebble_domains pd join public.domains d on d.id = pd.domain_id where pd.pebble_id = p.id), '[]'::jsonb),
      'collections', coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'name', c.name, 'glyph_id', c.glyph_id) order by c.name)
                          from public.collection_pebbles cp join public.collections c on c.id = cp.collection_id where cp.pebble_id = p.id), '[]'::jsonb)
    )
    from public.pebbles p
    join public.emotions e on e.id = p.emotion_id
    where p.moment_id = m.id and p.user_id = (select auth.uid())
  ) as pebble
from public.moments m;
revoke all on public.v_moments_full from public, anon;
grant select on public.v_moments_full to authenticated;
comment on view public.v_moments_full is
  'One row per readable moment. pebble is the CALLER''s pebble on that moment: null means uncompleted on the caller''s own moments and not yours on a connection''s moment. snaps is empty on cross-user rows because snaps_select is owner-only (v1 policy, 20260411000001).';

-- path_moments: the timeline. A null pebble is an uncompleted moment.
create function public.path_moments()
returns table (
  id uuid, name text, happened_at timestamptz, created_at timestamptz,
  first_snap_path text, pebble jsonb
)
language sql
security invoker
stable
set search_path = public
as $$
  select
    m.id, m.name, m.happened_at, m.created_at,
    (select s.storage_path from public.snaps s where s.moment_id = m.id
      order by s.sort_order asc nulls last, s.created_at asc limit 1) as first_snap_path,
    (select jsonb_build_object(
        'id', p.id, 'intensity', p.intensity, 'positiveness', p.positiveness, 'visibility', p.visibility,
        'emotion', case when e.id is null then null else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name) end,
        'glyph', public.resolve_pebble_glyph(p.id))
       from public.pebbles p left join public.emotions e on e.id = p.emotion_id
      where p.moment_id = m.id and p.user_id = (select auth.uid())) as pebble
  from public.moments m
  where m.user_id = (select auth.uid())
  order by m.happened_at desc;
$$;
revoke all on function public.path_moments() from public, anon;
grant execute on function public.path_moments() to authenticated;
comment on function public.path_moments() is
  'The caller''s timeline, newest happened_at first. A null pebble is an uncompleted moment.';

-- get_shared_pebble: same name and argument; moment fields + the sharer's pebble.
-- Base: 20260916100000 §4. `create or replace` keeps the anon + authenticated
-- grant from 20260817130000. Null for unknown, non-public and hidden alike.
-- render_svg stays as an explicit null until the web share page reads glyph.
-- Definer serving anon, so it calls the private layer directly: its own where
-- clause (public + not hidden) is the readability gate.
create or replace function public.get_shared_pebble(p_pebble_id uuid)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  select jsonb_build_object(
    'id', p.id,
    'name', m.name,
    'description', m.description,
    'happened_at', to_char(m.happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'intensity', p.intensity,
    'positiveness', p.positiveness,
    'render_svg', null::text,
    'glyph', private.resolve_pebble_glyph(p.id),
    'emotion', jsonb_build_object(
      'id', e.id, 'slug', e.slug, 'name', e.name, 'color', e.color,
      'primary_color', c.primary_color, 'secondary_color', c.secondary_color)
  )
  from public.pebbles p
  join public.moments m on m.id = p.moment_id
  join public.emotions e on e.id = p.emotion_id
  left join public.emotion_categories c on c.id = e.category_id
  where p.id = p_pebble_id
    and p.visibility = 'public'
    and m.hidden_at is null;
$$;
