-- =============================================================================
-- Admin · Analytics · Per-user weekly averages
-- =============================================================================
-- Issue: #341
-- Reference: docs/poc/admin-analytics/20260430_analytics_mvs.sql
--            § mv_user_averages_weekly
--
-- For each ISO week (Monday-Sunday, UTC), computes:
--   * active_users     — distinct users with ≥1 pebble in the week
--   * avg_glyphs       — glyphs / active_users
--   * avg_souls        — souls / active_users
--   * avg_collections  — collections / active_users
--
-- Counts of glyphs / souls / collections are point-in-time: items created on
-- or before the end of the week, owned by that week's active users. The POC
-- reference used current-state counts (no created_at filter). Doing it
-- point-in-time keeps the 12-week trend meaningful — otherwise the numerator
-- is roughly constant and the line only moves with the active-user set.
--
-- Soft-delete does not exist in this project, so no deleted_at filters.
-- =============================================================================

drop function if exists public.get_user_averages_series(int);
drop view if exists public.v_analytics_user_averages_weekly;

-- -----------------------------------------------------------------------------
-- v_analytics_user_averages_weekly
-- One row per ISO week, from the week of the earliest pebble to the current
-- week. Weeks with zero active users return zeros (not NULL) so chart consumers
-- don't need to coalesce.
-- -----------------------------------------------------------------------------
create view public.v_analytics_user_averages_weekly as
with weeks as (
  select date_trunc('week', d)::date as bucket_week
  from generate_series(
    coalesce(
      date_trunc('week', (select min(created_at) from public.pebbles))::date,
      date_trunc('week', current_date)::date
    ),
    date_trunc('week', current_date)::date,
    interval '1 week'
  ) as d
),
active as (
  select
    date_trunc('week', p.created_at)::date as bucket_week,
    p.user_id
  from public.pebbles p
  group by 1, 2
),
active_counts as (
  select bucket_week, count(*)::int as active_users
  from active
  group by bucket_week
),
glyph_counts as (
  select a.bucket_week, count(*)::int as glyph_count
  from active a
  join public.glyphs g
    on g.user_id = a.user_id
   and g.created_at < (a.bucket_week + interval '7 days')
  group by a.bucket_week
),
soul_counts as (
  select a.bucket_week, count(*)::int as soul_count
  from active a
  join public.souls s
    on s.user_id = a.user_id
   and s.created_at < (a.bucket_week + interval '7 days')
  group by a.bucket_week
),
collection_counts as (
  select a.bucket_week, count(*)::int as collection_count
  from active a
  join public.collections c
    on c.user_id = a.user_id
   and c.created_at < (a.bucket_week + interval '7 days')
  group by a.bucket_week
)
select
  w.bucket_week,
  coalesce(ac.active_users, 0) as active_users,
  case when coalesce(ac.active_users, 0) > 0
    then round(coalesce(gc.glyph_count, 0)::numeric / ac.active_users, 2)
    else 0::numeric
  end as avg_glyphs,
  case when coalesce(ac.active_users, 0) > 0
    then round(coalesce(sc.soul_count, 0)::numeric / ac.active_users, 2)
    else 0::numeric
  end as avg_souls,
  case when coalesce(ac.active_users, 0) > 0
    then round(coalesce(cc.collection_count, 0)::numeric / ac.active_users, 2)
    else 0::numeric
  end as avg_collections
from weeks w
left join active_counts     ac on ac.bucket_week = w.bucket_week
left join glyph_counts      gc on gc.bucket_week = w.bucket_week
left join soul_counts       sc on sc.bucket_week = w.bucket_week
left join collection_counts cc on cc.bucket_week = w.bucket_week;

-- -----------------------------------------------------------------------------
-- get_user_averages_series(p_weeks int default 12)
-- Returns the most recent p_weeks rows from the view (current week included),
-- ordered ascending by bucket_week so chart consumers can plot left-to-right.
-- Enforces is_admin(auth.uid()).
-- -----------------------------------------------------------------------------
create or replace function public.get_user_averages_series(p_weeks int default 12)
returns setof public.v_analytics_user_averages_weekly
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_cutoff date;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  if p_weeks is null or p_weeks <= 0 then
    raise exception 'invalid p_weeks: %, expected positive integer', p_weeks
      using errcode = '22023';
  end if;

  v_cutoff := date_trunc('week', current_date)::date - ((p_weeks - 1) * 7);

  return query
    select * from public.v_analytics_user_averages_weekly
    where bucket_week >= v_cutoff
    order by bucket_week asc;
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions: lock the view, expose the RPC to authenticated callers (the RPC
-- gates on is_admin(auth.uid())).
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_user_averages_weekly from public, anon, authenticated;

grant execute on function public.get_user_averages_series(int) to authenticated;
