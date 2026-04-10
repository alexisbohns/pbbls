-- Migration: JSONB Views
-- Consolidated read views for pebbles, karma, and bounce.

-- ============================================================
-- v_pebbles_full
-- ============================================================
-- Returns one row per pebble with all related data as JSONB.
-- RLS applies transparently via the underlying tables.

create view public.v_pebbles_full as
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
      'shape_id', g.shape_id,
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

-- ============================================================
-- v_karma_summary
-- ============================================================

create view public.v_karma_summary as
select
  u.id as user_id,
  coalesce((select sum(ke.delta) from public.karma_events ke where ke.user_id = u.id), 0) as total_karma,
  (select count(*) from public.pebbles pb where pb.user_id = u.id) as pebbles_count
from auth.users u;

-- ============================================================
-- v_bounce
-- ============================================================

create view public.v_bounce as
select
  u.id as user_id,
  coalesce(stats.active_days, 0) as active_days,
  case
    when coalesce(stats.active_days, 0) = 0  then 0
    when stats.active_days between 1  and 5  then 1
    when stats.active_days between 6  and 9  then 2
    when stats.active_days between 10 and 13 then 3
    when stats.active_days between 14 and 17 then 4
    when stats.active_days between 18 and 20 then 5
    when stats.active_days between 21 and 24 then 6
    else 7
  end::smallint as bounce_level
from auth.users u
left join lateral (
  select count(distinct date(pb.happened_at)) as active_days
  from public.pebbles pb
  where pb.user_id = u.id
    and pb.happened_at >= now() - interval '28 days'
) stats on true;
