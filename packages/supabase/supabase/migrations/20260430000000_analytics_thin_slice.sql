-- =============================================================================
-- Admin · Analytics · Thin slice (KPI strip + Active users chart)
-- =============================================================================
-- Spec: docs/superpowers/specs/2026-04-30-admin-analytics-thin-slice-design.md
--
-- Two plain views (not materialized — current data volume doesn't warrant MVs)
-- exposed via two SECURITY DEFINER RPCs that gate on is_admin(auth.uid()).
-- Soft-delete does not exist in this project, so no deleted_at filters.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- v_analytics_kpi_daily
-- One row per calendar day from the earliest pebble's created_at to today.
-- -----------------------------------------------------------------------------
drop view if exists public.v_analytics_active_users_daily;
drop view if exists public.v_analytics_kpi_daily;

create view public.v_analytics_kpi_daily as
with days as (
  select generate_series(
    coalesce((select min(created_at)::date from public.pebbles), current_date),
    current_date,
    interval '1 day'
  )::date as bucket_date
),
totals as (
  select
    d.bucket_date,
    (select count(*) from auth.users u where u.created_at::date <= d.bucket_date) as total_users
  from days d
),
day_counts as (
  select
    d.bucket_date,
    count(distinct p.user_id) filter (where p.created_at::date = d.bucket_date) as dau,
    count(*)                  filter (where p.created_at::date = d.bucket_date) as pebbles_today
  from days d
  left join public.pebbles p on p.created_at::date = d.bucket_date
  group by d.bucket_date
),
rolling as (
  select
    d.bucket_date,
    (select count(distinct p.user_id) from public.pebbles p
       where p.created_at::date >  d.bucket_date - 7
         and p.created_at::date <= d.bucket_date) as wau,
    (select count(distinct p.user_id) from public.pebbles p
       where p.created_at::date >  d.bucket_date - 30
         and p.created_at::date <= d.bucket_date) as mau
  from days d
)
select
  t.bucket_date,
  t.total_users::int           as total_users,
  d.dau::int                   as dau,
  d.pebbles_today::int         as pebbles_today,
  r.wau::int                   as wau,
  r.mau::int                   as mau,
  case when r.mau > 0 then round((d.dau::numeric / r.mau) * 100, 2) else null end as dau_mau_pct
from totals t
join day_counts d using (bucket_date)
join rolling r    using (bucket_date);

-- -----------------------------------------------------------------------------
-- v_analytics_active_users_daily
-- Projection of v_analytics_kpi_daily for the line chart.
-- -----------------------------------------------------------------------------
create view public.v_analytics_active_users_daily as
select bucket_date, dau, wau, mau
from public.v_analytics_kpi_daily;

-- -----------------------------------------------------------------------------
-- get_kpi_daily(p_range text)
-- Returns: latest row plus the row at (latest - period_length(p_range)).
-- For p_range = 'all' the prior-period row is omitted (deltas render '—').
-- -----------------------------------------------------------------------------
create or replace function public.get_kpi_daily(p_range text)
returns setof public.v_analytics_kpi_daily
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_period_days int;
  v_latest      date;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  select max(bucket_date) into v_latest from public.v_analytics_kpi_daily;
  if v_latest is null then
    return;
  end if;

  v_period_days := case p_range
    when '7d'  then 7
    when '30d' then 30
    when '90d' then 90
    when '1y'  then 365
    else null
  end;

  if v_period_days is null then
    return query
      select * from public.v_analytics_kpi_daily
      where bucket_date in (v_latest, v_latest - 30);  -- 30 still useful for sparkline
  else
    return query
      select * from public.v_analytics_kpi_daily
      where bucket_date in (v_latest, v_latest - v_period_days)
         or bucket_date >  v_latest - 30;             -- sparkline window
  end if;
end;
$$;

-- -----------------------------------------------------------------------------
-- get_active_users_series(p_start date, p_end date)
-- Returns: rows from v_analytics_active_users_daily in [p_start, p_end].
-- -----------------------------------------------------------------------------
create or replace function public.get_active_users_series(
  p_start date,
  p_end   date
)
returns setof public.v_analytics_active_users_daily
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
    select * from public.v_analytics_active_users_daily
    where bucket_date between p_start and p_end
    order by bucket_date asc;
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions
-- Views: the SECURITY DEFINER RPCs are the access path. We still revoke direct
-- select to keep the surface tight.
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_kpi_daily          from public, anon, authenticated;
revoke all on public.v_analytics_active_users_daily from public, anon, authenticated;

grant execute on function public.get_kpi_daily(text)                to authenticated;
grant execute on function public.get_active_users_series(date, date) to authenticated;
