-- The caller-summary views stop reading auth.users and run with invoker rights.
--
-- v_karma_summary, v_bounce and v_ripple were definer-rights views over
-- auth.users with a trailing `where u.id = auth.uid()`. They only ever returned
-- the caller's own id and counts, so nothing leaked, but the Supabase advisor
-- flags every API-exposed view that touches auth.users (lint 0002), and the
-- definer + bolt-on filter shape is the one decision log 2026-07-29 says not to
-- copy. auth.users was only there to produce one row per caller; a one-row
-- `select auth.uid()` does the same without touching the auth schema.
--
-- Each view keeps its column list and types, so it is replaced in place and no
-- client changes. With security_invoker the counts go through base-table RLS;
-- karma_events_select, pebbles_select and moments_select all admit
-- `user_id = auth.uid()`, so the caller still sees every one of their own rows.
-- anon gets no row either way (auth.uid() is null); the grants now say so too.

create or replace view public.v_karma_summary
with (security_invoker = true) as
select
  me.id as user_id,
  coalesce((select sum(ke.delta) from public.karma_events ke where ke.user_id = me.id), 0) as total_karma,
  (select count(*) from public.pebbles pb where pb.user_id = me.id) as pebbles_count
from (select auth.uid() as id) me
where me.id is not null;

create or replace view public.v_bounce
with (security_invoker = true) as
select
  me.id as user_id,
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
from (select auth.uid() as id) me
left join lateral (
  select count(distinct date(m.happened_at)) as active_days
  from public.pebbles pb
  join public.moments m on m.id = pb.moment_id
  where pb.user_id = me.id
    and m.happened_at >= now() - interval '28 days'
) stats on true
where me.id is not null;

create or replace view public.v_ripple
with (security_invoker = true) as
select
  me.id as user_id,
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
from (select auth.uid() as id) me
left join lateral (
  select
    count(*) as pebbles_28d,
    bool_or(pb.created_at::date = current_date) as active_today
  from public.pebbles pb
  where pb.user_id = me.id
    and pb.created_at >= now() - interval '28 days'
) stats on true
where me.id is not null;

revoke all on public.v_karma_summary, public.v_bounce, public.v_ripple from public, anon;
grant select on public.v_karma_summary, public.v_bounce, public.v_ripple to authenticated;
