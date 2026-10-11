-- =============================================================================
-- Moments and pebbles split — readers (spec §4)
-- resolve_pebble_glyph / v_moments_full / path_moments / get_shared_pebble
--
-- NOT safe to push alone: 20261012090000–20261012090400 ship together.
-- v_pebbles_full and path_pebbles come back in compat form in 20261012090400.
-- =============================================================================

-- D5: oldest collection carrying a glyph, else first domain default, else null.
-- Definer so every viewer carves the same stone: collection_pebbles is owner-
-- readable, and a connection would otherwise see a different glyph.
create function public.resolve_pebble_glyph(p_pebble_id uuid)
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
revoke all on function public.resolve_pebble_glyph(uuid) from public, anon;
grant execute on function public.resolve_pebble_glyph(uuid) to authenticated;

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
    where p.moment_id = m.id and p.user_id = auth.uid()
  ) as pebble
from public.moments m;
revoke all on public.v_moments_full from public, anon;
grant select on public.v_moments_full to authenticated;

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
      where p.moment_id = m.id and p.user_id = auth.uid()) as pebble
  from public.moments m
  where m.user_id = auth.uid()
  order by m.happened_at desc;
$$;
revoke all on function public.path_moments() from public, anon;
grant execute on function public.path_moments() to authenticated;

-- get_shared_pebble: same name and argument; moment fields + the sharer's pebble.
-- Base: 20260916100000 §4. `create or replace` keeps the anon + authenticated
-- grant from 20260817130000. Null for unknown, non-public and hidden alike.
-- render_svg stays as an explicit null until the web share page reads glyph.
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
    'glyph', public.resolve_pebble_glyph(p.id),
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
