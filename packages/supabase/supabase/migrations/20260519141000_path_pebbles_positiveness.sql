-- Migration: path_pebbles + positiveness
-- Widens the path_pebbles RPC return shape to include positiveness so
-- iOS can pick the matching pebble-outline silhouette per (size × polarity).
--
-- No engine change, no render_version bump, no backfill. The compose
-- pipeline and existing render_svg rows are untouched — this only
-- exposes a column already on public.pebbles.

drop function if exists public.path_pebbles();
create function public.path_pebbles()
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
    p.name,
    p.happened_at,
    p.created_at,
    p.intensity,
    p.positiveness,
    p.render_svg,
    case
      when e.id is null then null
      else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name)
    end as emotion,
    (
      select s.storage_path
      from public.snaps s
      where s.pebble_id = p.id
      order by s.sort_order asc nulls last, s.created_at asc
      limit 1
    ) as first_snap_path
  from public.pebbles p
  left join public.emotions e on e.id = p.emotion_id
  where p.user_id = auth.uid()
  order by p.happened_at desc;
$$;

grant execute on function public.path_pebbles() to authenticated;
