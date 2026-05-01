-- =============================================================================
-- Admin · Analytics · Retention cohort heatmap
-- =============================================================================
-- Builds on 20260430000000_analytics_thin_slice.sql.
--
-- Cohort = users grouped by signup week (Mon–Sun, UTC) from auth.users.
-- Cell [cohort, week_offset] = % of cohort users who created >=1 pebble in
-- the week N weeks after their signup week. week_offset = 0 is the signup week
-- itself (always 100% by definition for any cohort that has activity).
--
-- The view is a plain view (volume is small at this stage). Access goes through
-- get_retention_cohorts() which gates on is_admin(auth.uid()) and returns
-- the last 8 cohorts.
-- =============================================================================

drop view if exists public.v_analytics_retention_cohorts_weekly;

create view public.v_analytics_retention_cohorts_weekly as
with cohorts as (
  select
    date_trunc('week', u.created_at)::date as cohort_week,
    u.id                                   as user_id
  from auth.users u
),
cohort_size as (
  select cohort_week, count(*)::int as size
  from cohorts
  group by cohort_week
),
activity as (
  select
    c.cohort_week,
    floor(
      extract(epoch from (date_trunc('week', p.created_at) - c.cohort_week)) / 604800
    )::int as week_offset,
    c.user_id
  from cohorts c
  join public.pebbles p
    on p.user_id = c.user_id
   and p.created_at >= c.cohort_week
)
select
  a.cohort_week,
  a.week_offset,
  cs.size                                                                       as cohort_size,
  count(distinct a.user_id)::int                                                as active_users,
  round((count(distinct a.user_id)::numeric / cs.size) * 100, 1)                as retention_pct
from activity a
join cohort_size cs using (cohort_week)
group by a.cohort_week, a.week_offset, cs.size;

-- -----------------------------------------------------------------------------
-- get_retention_cohorts()
-- Returns last 8 cohorts (most recent signup weeks) with all their week_offset
-- rows. Ordering left to the caller.
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
      select distinct cohort_week
      from public.v_analytics_retention_cohorts_weekly
      order by cohort_week desc
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
