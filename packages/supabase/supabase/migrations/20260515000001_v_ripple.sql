-- Migration: v_ripple
-- New read view powering the iOS "Ripples" badge (issue #442).
-- Counts pebbles created (by created_at, NOT happened_at) in the
-- trailing 28 days and buckets into 7 levels (0–6). Also exposes
-- active_today: did the user create any pebble today (server date).
--
-- v_bounce is left untouched — Ripples is a deliberate parallel
-- signal, not a rename or replacement of the bounce data layer.

create view public.v_ripple as
select
  u.id as user_id,
  coalesce(stats.pebbles_28d, 0) as pebbles_28d,
  coalesce(stats.active_today, false) as active_today,
  case
    when coalesce(stats.pebbles_28d, 0) = 0   then 0
    when stats.pebbles_28d between  1 and  4  then 1
    when stats.pebbles_28d between  5 and  8  then 2
    when stats.pebbles_28d between  9 and 12  then 3
    when stats.pebbles_28d between 13 and 16  then 4
    when stats.pebbles_28d between 17 and 20  then 5
    else 6
  end::smallint as ripple_level
from auth.users u
left join lateral (
  select
    count(*) as pebbles_28d,
    bool_or(pb.created_at::date = current_date) as active_today
  from public.pebbles pb
  where pb.user_id = u.id
    and pb.created_at >= now() - interval '28 days'
) stats on true;
