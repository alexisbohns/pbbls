-- ============================================================
-- path_pebbles()
-- ------------------------------------------------------------
-- Single-round-trip read for the iOS Path screen. Returns every
-- pebble owned by auth.uid() with the few extra columns the new
-- Path UI needs that are not on the pebbles row itself:
--   - intensity (already on pebbles)
--   - render_svg (already on pebbles)
--   - emotion: jsonb {id, slug, name} via left join (left so a
--       deleted-but-referenced emotion still returns the pebble)
--   - first_snap_path: storage_path of the lowest-sort_order
--       snap, used by PathPebbleSnapThumb to sign a thumb URL
--
-- Ordering is global desc by happened_at; the iOS layer
-- re-sorts per-week (past weeks ascend, current/future descend).
-- ============================================================

create or replace function public.path_pebbles()
returns table (
  id uuid,
  name text,
  happened_at timestamptz,
  intensity smallint,
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
    p.intensity,
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
