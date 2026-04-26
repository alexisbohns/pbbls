-- Migration: Expose render columns on v_pebbles_full
-- Issue: #314
--
-- The `pebbles` table gained `render_svg`, `render_manifest`, and `render_version`
-- in `20260415000001_remote_pebble_engine.sql` but `v_pebbles_full` was not
-- updated, so web reads through the view never saw the composed render. The
-- web app is moving from client-side rendering to the remote engine and needs
-- these columns surfaced on the read view it already queries.
--
-- Idempotent: `create or replace view` keeps the view name stable so existing
-- foreign-key relationships and dependent code continue to resolve.

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
  p.render_svg,
  p.render_manifest,
  p.render_version,
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
