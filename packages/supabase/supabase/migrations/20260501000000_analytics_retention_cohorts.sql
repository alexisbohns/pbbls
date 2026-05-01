-- =============================================================================
-- Admin · Analytics · Retention cohort heatmap
-- =============================================================================
-- Builds on 20260430000000_analytics_thin_slice.sql.
--
-- Cohort = users grouped by signup week (Mon–Sun, UTC) from auth.users.
-- Cell [cohort, week_offset] = % of cohort users who created >=1 pebble in
-- the week N weeks after their signup week. week_offset = 0 is the signup week
-- itself and is fixed at 100% by definition (every cohort member is "active"
-- in their own signup week, regardless of pebble activity).
--
-- The view emits the full (cohort_week × week_offset) grid via generate_series,
-- so cohorts with zero pebble activity still surface (filled with 0%) and
-- weeks with no actives render as 0% (not "no data").
--
-- The view is a plain view (volume is small at this stage). Access goes through
-- get_retention_cohorts() which gates on is_admin(auth.uid()) and returns
-- the last 8 cohorts.
-- =============================================================================

drop view if exists public.v_analytics_retention_cohorts_weekly;

create view public.v_analytics_retention_cohorts_weekly as
with users_with_cohort as (
  select
    u.id                                       as user_id,
    date_trunc('week', u.created_at)::date     as cohort_week
  from auth.users u
),
cohort_size as (
  select cohort_week, count(*)::int as size
  from users_with_cohort
  group by cohort_week
),
-- Full (cohort_week × week_offset) grid: every cohort gets a row for every
-- week from 0 up to the current week, including cohorts with no pebbles.
grid as (
  select
    cs.cohort_week,
    cs.size                                                                 as cohort_size,
    gs::int                                                                 as week_offset
  from cohort_size cs
  cross join lateral generate_series(
    0,
    greatest(0, ((date_trunc('week', current_date)::date - cs.cohort_week) / 7)::int)
  ) as gs
),
activity as (
  select
    uc.cohort_week,
    ((date_trunc('week', p.created_at)::date - uc.cohort_week) / 7)::int    as week_offset,
    count(distinct p.user_id)::int                                          as active_users
  from users_with_cohort uc
  join public.pebbles p on p.user_id = uc.user_id
  where p.created_at >= uc.cohort_week
  group by uc.cohort_week, week_offset
)
select
  g.cohort_week,
  g.week_offset,
  g.cohort_size,
  -- W0 = 100% by definition: every cohort member is "active" in their signup week.
  case when g.week_offset = 0 then g.cohort_size
       else coalesce(a.active_users, 0)
  end                                                                       as active_users,
  case
    when g.cohort_size = 0 then 0::numeric
    when g.week_offset = 0 then 100::numeric
    else round((coalesce(a.active_users, 0)::numeric / g.cohort_size) * 100, 1)
  end                                                                       as retention_pct
from grid g
left join activity a using (cohort_week, week_offset);

-- -----------------------------------------------------------------------------
-- get_retention_cohorts()
-- Returns last 8 cohorts (most recent signup weeks) with all their week_offset
-- rows. Cohorts are derived from auth.users so signup weeks with zero pebble
-- activity still appear. Ordering left to the caller.
-- -----------------------------------------------------------------------------
create or replace function public.get_retention_cohorts()
returns setof public.v_analytics_retention_cohorts_weekly
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
    with last_cohorts as (
      select date_trunc('week', u.created_at)::date as cohort_week
      from auth.users u
      group by 1
      order by 1 desc
      limit 8
    )
    select v.*
    from public.v_analytics_retention_cohorts_weekly v
    join last_cohorts lc using (cohort_week);
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions: lock the view, expose the RPC.
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_retention_cohorts_weekly from public, anon, authenticated;

grant execute on function public.get_retention_cohorts() to authenticated;
