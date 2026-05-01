-- =============================================================================
-- Admin · Analytics · Quality signals (Phase A)
-- =============================================================================
-- Issue: #346
-- Reference: docs/poc/admin-analytics/20260430_analytics_mvs.sql
--            § mv_quality_signals_daily
--
-- Phase A scope: render the 8-row Quality signals table with the four metrics
-- that are computable from existing data:
--
--   1. pebbles_per_wau     — pebbles / distinct authors over the last 7 days
--   2. d1_retention        — % of users who signed up yesterday and pebbled today
--   3. d7_retention        — % of users who signed up 7 days ago and pebbled in week 1
--   4. d30_retention       — % of users who signed up 30 days ago and pebbled in days 1-30
--
-- The other four metrics depend on data Pebbles doesn't capture yet
-- (sessions table, pebble_views, normalized analytics_events). They are emitted
-- as rows with `available = false`, value = null, so the UI can render eight
-- rows from a single query and label the missing ones with "data not
-- collected yet" instead of hardcoding the 4-of-8 split client-side.
--
-- Each row carries `value` (current period) and `previous_value` (the same
-- metric computed for the matching prior period) so the table can show a delta
-- without a second RPC call. `unit` lets the client pick its formatter.
-- =============================================================================

drop function if exists public.get_quality_signals_today();
drop view if exists public.v_analytics_quality_signals_today;

create view public.v_analytics_quality_signals_today as
with
  -- Pebbles per WAU: last 7 days vs the prior 7 days.
  pebbles_per_wau_current as (
    select round(
      count(*)::numeric / nullif(count(distinct p.user_id), 0)
    , 2) as v
    from public.pebbles p
    where p.deleted_at is null
      and p.created_at >= now() - interval '7 days'
  ),
  pebbles_per_wau_previous as (
    select round(
      count(*)::numeric / nullif(count(distinct p.user_id), 0)
    , 2) as v
    from public.pebbles p
    where p.deleted_at is null
      and p.created_at >= now() - interval '14 days'
      and p.created_at <  now() - interval '7 days'
  ),
  -- D1 retention: cohort signed up yesterday, active today.
  -- Previous period: cohort signed up two days ago, active yesterday.
  d1_current as (
    select round(100.0 *
      (select count(distinct u.id)
         from auth.users u
         join public.pebbles p
           on p.user_id = u.id
          and p.deleted_at is null
          and p.created_at::date = current_date
        where u.created_at::date = current_date - 1)::numeric
      / nullif((select count(*) from auth.users u
                  where u.created_at::date = current_date - 1), 0)
    , 1) as v
  ),
  d1_previous as (
    select round(100.0 *
      (select count(distinct u.id)
         from auth.users u
         join public.pebbles p
           on p.user_id = u.id
          and p.deleted_at is null
          and p.created_at::date = current_date - 1
        where u.created_at::date = current_date - 2)::numeric
      / nullif((select count(*) from auth.users u
                  where u.created_at::date = current_date - 2), 0)
    , 1) as v
  ),
  -- D7 retention: cohort signed up 7 days ago, active any day in days 1-7
  -- after signup. Previous period: cohort signed up 14 days ago.
  d7_current as (
    select round(100.0 *
      (select count(distinct u.id)
         from auth.users u
         join public.pebbles p
           on p.user_id = u.id
          and p.deleted_at is null
          and p.created_at::date between current_date - 6 and current_date
        where u.created_at::date = current_date - 7)::numeric
      / nullif((select count(*) from auth.users u
                  where u.created_at::date = current_date - 7), 0)
    , 1) as v
  ),
  d7_previous as (
    select round(100.0 *
      (select count(distinct u.id)
         from auth.users u
         join public.pebbles p
           on p.user_id = u.id
          and p.deleted_at is null
          and p.created_at::date between current_date - 13 and current_date - 7
        where u.created_at::date = current_date - 14)::numeric
      / nullif((select count(*) from auth.users u
                  where u.created_at::date = current_date - 14), 0)
    , 1) as v
  ),
  -- D30 retention: cohort signed up 30 days ago, active in days 1-30 after
  -- signup. Previous period: cohort signed up 60 days ago.
  d30_current as (
    select round(100.0 *
      (select count(distinct u.id)
         from auth.users u
         join public.pebbles p
           on p.user_id = u.id
          and p.deleted_at is null
          and p.created_at::date between current_date - 29 and current_date
        where u.created_at::date = current_date - 30)::numeric
      / nullif((select count(*) from auth.users u
                  where u.created_at::date = current_date - 30), 0)
    , 1) as v
  ),
  d30_previous as (
    select round(100.0 *
      (select count(distinct u.id)
         from auth.users u
         join public.pebbles p
           on p.user_id = u.id
          and p.deleted_at is null
          and p.created_at::date between current_date - 59 and current_date - 30
        where u.created_at::date = current_date - 60)::numeric
      / nullif((select count(*) from auth.users u
                  where u.created_at::date = current_date - 60), 0)
    , 1) as v
  )
select
  current_date as bucket_date,
  t.indicator_order,
  t.indicator_key,
  t.indicator_label,
  t.unit,
  t.value,
  t.previous_value,
  t.available
from (values
  -- Order matches the spec / mockup. `available = false` marks indicators
  -- whose source data isn't captured yet (Phase B + C).
  (1, 'median_session_seconds',      'Median session duration',         'seconds',  null::numeric,                              null::numeric,                               false),
  (2, 'sessions_per_wau',            'Sessions per active user / week', 'sessions', null::numeric,                              null::numeric,                               false),
  (3, 'pebbles_per_wau',             'Pebbles per active user / week',  'pebbles',  (select v from pebbles_per_wau_current),    (select v from pebbles_per_wau_previous),    true),
  (4, 'pct_revisits_to_past_pebbles','% revisits to past pebbles',      'percent',  null::numeric,                              null::numeric,                               false),
  (5, 'd1_retention',                'D1 retention',                    'percent',  (select v from d1_current),                 (select v from d1_previous),                 true),
  (6, 'd7_retention',                'D7 retention',                    'percent',  (select v from d7_current),                 (select v from d7_previous),                 true),
  (7, 'd30_retention',               'D30 retention',                   'percent',  (select v from d30_current),                (select v from d30_previous),                true),
  (8, 'friction_events_per_session', 'Friction events / session',       'events',   null::numeric,                              null::numeric,                               false)
) as t(indicator_order, indicator_key, indicator_label, unit, value, previous_value, available);

-- -----------------------------------------------------------------------------
-- get_quality_signals_today() RPC. Enforces is_admin(auth.uid()).
-- -----------------------------------------------------------------------------
create or replace function public.get_quality_signals_today()
returns setof public.v_analytics_quality_signals_today
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
    select * from public.v_analytics_quality_signals_today
    order by indicator_order asc;
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions: lock the view, expose the RPC.
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_quality_signals_today from public, anon, authenticated;

grant execute on function public.get_quality_signals_today() to authenticated;
