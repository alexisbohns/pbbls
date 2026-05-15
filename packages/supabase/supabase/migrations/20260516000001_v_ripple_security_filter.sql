-- Migration: v_ripple security filter (hotfix for #442)
-- The previous migration created public.v_ripple without `where u.id = auth.uid()`.
-- Postgres views run with the view owner's privileges by default and bypass
-- underlying RLS on `pebbles`, so any authenticated PostgREST client could
-- enumerate one row per user via .from("v_ripple").select(...). Aligns with
-- the established pattern in 20260411000005_security_hardening.sql, where
-- v_bounce and v_karma_summary both end with the same filter.

drop view if exists public.v_ripple;
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
) stats on true
where u.id = auth.uid();
