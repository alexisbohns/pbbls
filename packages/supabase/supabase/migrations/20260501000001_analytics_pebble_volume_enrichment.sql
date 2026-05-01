-- =============================================================================
-- Admin · Analytics · Pebble volume + enrichment
-- =============================================================================
-- Builds on 20260430000000_analytics_thin_slice.sql.
--
-- Volume bars + enrichment overlay lines (with picture / in collection) and
-- a donut card with three primary shares + secondary ratios.
--
-- Schema notes (vs. the POC reference SQL):
--   - "with picture"      derives from EXISTS in `snaps`
--                         (pebbles.picture_url does not exist).
--   - "in collection"     derives from EXISTS in `collection_pebbles`
--                         (pebbles.collection_id does not exist).
--   - `glyphs.is_custom`  does not exist — `% with custom glyph` is dropped
--                         (tracked in #347).
--   - `pebbles.emotion_id` is NOT NULL — `% with ≥1 emotion pearl` is always
--                         100% and is dropped from the secondary ratios.
--   - `pebbles.intensity` is NOT NULL (1..3) — `% with intensity` is left in
--                         as a sanity-check metric (always 100%).
--
-- Soft-delete does not exist in this project, so no deleted_at filters.
-- =============================================================================

drop view if exists public.v_analytics_pebble_enrichment_daily;
drop view if exists public.v_analytics_pebble_volume_daily;

-- -----------------------------------------------------------------------------
-- v_analytics_pebble_volume_daily
-- One row per calendar day from the earliest pebble's created_at to today.
-- Days without pebbles are gap-filled with zeros so bar charts render without
-- holes.
-- -----------------------------------------------------------------------------
create view public.v_analytics_pebble_volume_daily as
with days as (
  select generate_series(
    coalesce((select min(created_at)::date from public.pebbles), current_date),
    current_date,
    interval '1 day'
  )::date as bucket_date
),
day_pebbles as (
  select
    p.created_at::date                                  as bucket_date,
    count(*)::int                                       as pebbles,
    count(*) filter (
      where exists (
        select 1 from public.snaps s where s.pebble_id = p.id
      )
    )::int                                              as pebbles_with_picture,
    count(*) filter (
      where exists (
        select 1 from public.collection_pebbles cp where cp.pebble_id = p.id
      )
    )::int                                              as pebbles_in_collection,
    count(distinct p.user_id)::int                      as active_users
  from public.pebbles p
  group by p.created_at::date
)
select
  d.bucket_date,
  coalesce(dp.pebbles, 0)               as pebbles,
  coalesce(dp.pebbles_with_picture, 0)  as pebbles_with_picture,
  coalesce(dp.pebbles_in_collection, 0) as pebbles_in_collection,
  coalesce(dp.active_users, 0)          as active_users
from days d
left join day_pebbles dp using (bucket_date);

-- -----------------------------------------------------------------------------
-- v_analytics_pebble_enrichment_daily
-- Pre-computes the share fields for the donuts + secondary ratios. Only days
-- with at least one pebble produce a row; the get_pebble_enrichment_today RPC
-- selects the latest row.
-- -----------------------------------------------------------------------------
create view public.v_analytics_pebble_enrichment_daily as
with base as (
  select
    p.created_at::date                                            as bucket_date,
    p.id,
    p.description,
    p.intensity,
    exists (select 1 from public.snaps s
              where s.pebble_id = p.id)                           as has_picture,
    exists (select 1 from public.collection_pebbles cp
              where cp.pebble_id = p.id)                          as in_collection,
    exists (select 1 from public.pebble_souls ps
              where ps.pebble_id = p.id)                          as has_soul
  from public.pebbles p
)
select
  bucket_date,
  count(*)::int                                                                  as total_pebbles,
  round(100.0 * count(*) filter (where has_picture)
                / nullif(count(*), 0), 1)                                        as pct_with_picture,
  round(100.0 * count(*) filter (where in_collection)
                / nullif(count(*), 0), 1)                                        as pct_in_collection,
  round(100.0 * count(*) filter (where description is not null
                                   and length(description) > 0)
                / nullif(count(*), 0), 1)                                        as pct_with_thought,
  round(100.0 * count(*) filter (where has_soul)
                / nullif(count(*), 0), 1)                                        as pct_with_soul,
  round(100.0 * count(*) filter (where intensity is not null)
                / nullif(count(*), 0), 1)                                        as pct_with_intensity
from base
group by bucket_date;

-- -----------------------------------------------------------------------------
-- get_pebble_volume_series(p_start, p_end, p_bucket)
-- Aggregates pebble counts over [p_start, p_end] at the requested bucket
-- granularity ('day' | 'week' | 'month' | 'year'). The series is gap-filled
-- with zeros so the chart has a bar for every period in the range, including
-- empty ones. Reads from public.pebbles directly so distinct-user counts
-- remain accurate at higher buckets.
-- -----------------------------------------------------------------------------
create or replace function public.get_pebble_volume_series(
  p_start  date,
  p_end    date,
  p_bucket text default 'day'
)
returns table (
  bucket_date           date,
  pebbles               bigint,
  pebbles_with_picture  bigint,
  pebbles_in_collection bigint,
  active_users          bigint
)
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  if p_bucket not in ('day', 'week', 'month', 'year') then
    raise exception 'invalid p_bucket: %, expected day|week|month|year', p_bucket
      using errcode = '22023';
  end if;

  return query
  with buckets as (
    select date_trunc(p_bucket, gs)::date as bucket_date
    from generate_series(
      date_trunc(p_bucket, p_start::timestamptz),
      date_trunc(p_bucket, p_end::timestamptz),
      ('1 ' || p_bucket)::interval
    ) as gs
  ),
  data as (
    select
      date_trunc(p_bucket, p.created_at)::date as bucket_date,
      count(*)                                                                       as pebbles,
      count(*) filter (
        where exists (select 1 from public.snaps s where s.pebble_id = p.id)
      )                                                                              as pebbles_with_picture,
      count(*) filter (
        where exists (
          select 1 from public.collection_pebbles cp where cp.pebble_id = p.id
        )
      )                                                                              as pebbles_in_collection,
      count(distinct p.user_id)                                                      as active_users
    from public.pebbles p
    where p.created_at::date between p_start and p_end
    group by date_trunc(p_bucket, p.created_at)
  )
  select
    b.bucket_date,
    coalesce(d.pebbles, 0)::bigint                as pebbles,
    coalesce(d.pebbles_with_picture, 0)::bigint   as pebbles_with_picture,
    coalesce(d.pebbles_in_collection, 0)::bigint  as pebbles_in_collection,
    coalesce(d.active_users, 0)::bigint           as active_users
  from buckets b
  left join data d using (bucket_date)
  order by b.bucket_date asc;
end;
$$;

-- -----------------------------------------------------------------------------
-- get_pebble_enrichment_today()
-- Returns the latest row from v_analytics_pebble_enrichment_daily (i.e. the
-- most recent day that had at least one pebble). Empty result if no pebbles
-- exist yet.
-- -----------------------------------------------------------------------------
create or replace function public.get_pebble_enrichment_today()
returns setof public.v_analytics_pebble_enrichment_daily
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_latest date;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  select max(bucket_date)
    into v_latest
    from public.v_analytics_pebble_enrichment_daily;

  if v_latest is null then
    return;
  end if;

  return query
    select * from public.v_analytics_pebble_enrichment_daily
    where bucket_date = v_latest;
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions: lock the views, expose the RPCs to authenticated callers
-- (the RPCs gate on is_admin(auth.uid())).
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_pebble_volume_daily     from public, anon, authenticated;
revoke all on public.v_analytics_pebble_enrichment_daily from public, anon, authenticated;

grant execute on function public.get_pebble_volume_series(date, date, text) to authenticated;
grant execute on function public.get_pebble_enrichment_today()              to authenticated;
