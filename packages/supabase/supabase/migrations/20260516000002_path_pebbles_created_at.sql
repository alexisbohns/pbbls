-- Migration: path_pebbles + created_at
-- Adds created_at to the path_pebbles RPC's return shape so iOS can
-- compute "active today" against the device's local timezone instead
-- of the UTC server date used by v_ripple.active_today. Same authorisation
-- model, same query — just a wider projection.
--
-- Background: v_ripple.active_today compares `pb.created_at::date =
-- current_date`. current_date is UTC on Supabase, so for a CEST user
-- in the early hours of UTC-yesterday it reports active_today=true
-- even though they've not touched the app on their local "today".
-- Long-term fix is server-side TZ (see M22 follow-up); this lets the
-- client override active_today now without a second round-trip.

drop function if exists public.path_pebbles();
create function public.path_pebbles()
returns table (
  id uuid,
  name text,
  happened_at timestamptz,
  created_at timestamptz,
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
    p.created_at,
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
