-- path_moments: the pebble gains its souls (#1121), for the Spine's persons
-- of the week and people lines. Additive: every installed client ignores
-- unknown keys. Same subquery as v_moments_full, plus glyph_id so a client
-- can draw the soul's mark from its own glyph cache.
create or replace function public.path_moments()
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
        'glyph', public.resolve_pebble_glyph(p.id),
        'souls', coalesce((
          select jsonb_agg(jsonb_build_object('id', so.id, 'name', so.name, 'glyph_id', so.glyph_id) order by so.name)
          from public.pebble_souls ps join public.souls so on so.id = ps.soul_id
          where ps.pebble_id = p.id), '[]'::jsonb))
       from public.pebbles p left join public.emotions e on e.id = p.emotion_id
      where p.moment_id = m.id and p.user_id = (select auth.uid())) as pebble
  from public.moments m
  where m.user_id = (select auth.uid())
  order by m.happened_at desc;
$$;
comment on function public.path_moments() is
  'The caller''s timeline, newest happened_at first. A null pebble is an uncompleted moment; pebble.souls lists its souls ordered by name (#1121).';
