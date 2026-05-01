-- =============================================================================
-- Glyph custom flag + custom-glyph metric on analytics
-- =============================================================================
-- Issue: #347
--
-- Adds `glyphs.is_custom` so analytics can express the original POC signal
-- "% of pebbles using a custom-drawn glyph" (a creativity proxy).
--
-- Definition decision (locked here):
--   custom = user-drawn glyph (`user_id IS NOT NULL`).
--   non-custom = system-seeded default glyph (`user_id IS NULL`), as inserted
--                by 20260415000001_remote_pebble_engine.sql for each canonical
--                domain.
--
-- The codebase currently has only those two kinds of glyphs: every client
-- create_pebble / update_pebble path inserts with `user_id = auth.uid()`, and
-- the only NULL-user_id rows are the per-domain system seeds. So the flag is
-- fully derivable from `user_id`, and we model it as a STORED generated column
-- to remove any chance of drift and to skip a separate backfill step.
--
-- Knock-on changes:
--   - v_analytics_pebble_volume_daily / get_pebble_volume_series: add
--     `pebbles_with_custom_glyph`.
--   - v_analytics_pebble_enrichment_daily / get_pebble_enrichment: add
--     `pct_with_custom_glyph`.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. glyphs.is_custom
-- -----------------------------------------------------------------------------
alter table public.glyphs
  add column is_custom boolean
    generated always as (user_id is not null) stored;

-- -----------------------------------------------------------------------------
-- 2. Rebuild the analytics views to include the new metric.
--    Both views are owned by this analytics slice (see
--    20260501000001_analytics_pebble_volume_enrichment.sql) so we drop and
--    recreate to keep their column lists explicit.
-- -----------------------------------------------------------------------------
drop view if exists public.v_analytics_pebble_enrichment_daily;
drop view if exists public.v_analytics_pebble_volume_daily;

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
    count(*) filter (
      where exists (
        select 1 from public.glyphs g
        where g.id = p.glyph_id and g.is_custom
      )
    )::int                                              as pebbles_with_custom_glyph,
    count(distinct p.user_id)::int                      as active_users
  from public.pebbles p
  group by p.created_at::date
)
select
  d.bucket_date,
  coalesce(dp.pebbles, 0)                   as pebbles,
  coalesce(dp.pebbles_with_picture, 0)      as pebbles_with_picture,
  coalesce(dp.pebbles_in_collection, 0)     as pebbles_in_collection,
  coalesce(dp.pebbles_with_custom_glyph, 0) as pebbles_with_custom_glyph,
  coalesce(dp.active_users, 0)              as active_users
from days d
left join day_pebbles dp using (bucket_date);

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
              where ps.pebble_id = p.id)                          as has_soul,
    exists (select 1 from public.glyphs g
              where g.id = p.glyph_id and g.is_custom)            as has_custom_glyph
  from public.pebbles p
)
select
  bucket_date,
  count(*)::int                                                                  as total_pebbles,
  round(100.0 * count(*) filter (where has_picture)
                / nullif(count(*), 0), 1)                                        as pct_with_picture,
  round(100.0 * count(*) filter (where in_collection)
                / nullif(count(*), 0), 1)                                        as pct_in_collection,
  round(100.0 * count(*) filter (where has_custom_glyph)
                / nullif(count(*), 0), 1)                                        as pct_with_custom_glyph,
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
-- 3. Rebuild the RPCs to expose the new metric.
--    Drop first because the return type changed (cannot CREATE OR REPLACE
--    across return-type changes).
-- -----------------------------------------------------------------------------
drop function if exists public.get_pebble_volume_series(date, date, text);
drop function if exists public.get_pebble_enrichment(date, date);

create function public.get_pebble_volume_series(
  p_start  date,
  p_end    date,
  p_bucket text default 'day'
)
returns table (
  bucket_date               date,
  pebbles                   bigint,
  pebbles_with_picture      bigint,
  pebbles_in_collection     bigint,
  pebbles_with_custom_glyph bigint,
  active_users              bigint
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
      count(*) filter (
        where exists (
          select 1 from public.glyphs g where g.id = p.glyph_id and g.is_custom
        )
      )                                                                              as pebbles_with_custom_glyph,
      count(distinct p.user_id)                                                      as active_users
    from public.pebbles p
    where p.created_at::date between p_start and p_end
    group by date_trunc(p_bucket, p.created_at)
  )
  select
    b.bucket_date,
    coalesce(d.pebbles, 0)::bigint                    as pebbles,
    coalesce(d.pebbles_with_picture, 0)::bigint       as pebbles_with_picture,
    coalesce(d.pebbles_in_collection, 0)::bigint      as pebbles_in_collection,
    coalesce(d.pebbles_with_custom_glyph, 0)::bigint  as pebbles_with_custom_glyph,
    coalesce(d.active_users, 0)::bigint               as active_users
  from buckets b
  left join data d using (bucket_date)
  order by b.bucket_date asc;
end;
$$;

create function public.get_pebble_enrichment(
  p_start date,
  p_end   date
)
returns table (
  total_pebbles          bigint,
  pct_with_picture       numeric,
  pct_in_collection      numeric,
  pct_with_custom_glyph  numeric,
  pct_with_thought       numeric,
  pct_with_soul          numeric,
  pct_with_intensity     numeric
)
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
  with base as (
    select
      p.id,
      p.description,
      p.intensity,
      exists (select 1 from public.snaps s
                where s.pebble_id = p.id)             as has_picture,
      exists (select 1 from public.collection_pebbles cp
                where cp.pebble_id = p.id)            as in_collection,
      exists (select 1 from public.pebble_souls ps
                where ps.pebble_id = p.id)            as has_soul,
      exists (select 1 from public.glyphs g
                where g.id = p.glyph_id and g.is_custom) as has_custom_glyph
    from public.pebbles p
    where p.created_at::date between p_start and p_end
  )
  select
    count(*)::bigint                                                           as total_pebbles,
    round(100.0 * count(*) filter (where has_picture)
                  / nullif(count(*), 0), 1)                                    as pct_with_picture,
    round(100.0 * count(*) filter (where in_collection)
                  / nullif(count(*), 0), 1)                                    as pct_in_collection,
    round(100.0 * count(*) filter (where has_custom_glyph)
                  / nullif(count(*), 0), 1)                                    as pct_with_custom_glyph,
    round(100.0 * count(*) filter (where description is not null
                                     and length(description) > 0)
                  / nullif(count(*), 0), 1)                                    as pct_with_thought,
    round(100.0 * count(*) filter (where has_soul)
                  / nullif(count(*), 0), 1)                                    as pct_with_soul,
    round(100.0 * count(*) filter (where intensity is not null)
                  / nullif(count(*), 0), 1)                                    as pct_with_intensity
  from base
  having count(*) > 0;
end;
$$;

-- -----------------------------------------------------------------------------
-- 4. Permissions: re-apply the same policy as the analytics slice.
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_pebble_volume_daily     from public, anon, authenticated;
revoke all on public.v_analytics_pebble_enrichment_daily from public, anon, authenticated;

grant execute on function public.get_pebble_volume_series(date, date, text) to authenticated;
grant execute on function public.get_pebble_enrichment(date, date)          to authenticated;
