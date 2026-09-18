-- =============================================================================
-- glyphs.is_custom is the negation of is_system, not of ownership (#874)
-- =============================================================================
-- Split from #872, which fixed the user-facing half of the same root cause.
--
-- `is_custom` was `generated always as (user_id is not null) stored`
-- (20260501000006), locked in when `user_id is null` still meant exactly one
-- thing: a first-party seed. purge_account deliberately keeps a SOLD glyph and
-- anonymizes it to `user_id = null` (20260729201326:93), so the moment a
-- creator deleted their account, their artwork flipped is_custom true -> false
-- and the admin analytics metric "% of pebbles with a custom glyph"
-- reclassified bought artwork as system-seeded. The creativity proxy under-
-- counted exactly the pebbles that prove the point.
--
-- #872 gave the schema the marker that carries the real meaning
-- (`glyphs.is_system`, 20260919090000). The honest definition of custom is now
-- `not is_system`: it is the system set that is finite and curated, and
-- everything else -- owned, anonymized-but-sold, admin-published -- is a
-- person's drawing.
--
-- Scope: admin analytics only. No client reads is_custom (grep over
-- apps/{web,ios,android,admin}: zero hits outside generated types), and the
-- two RPC output columns keep their names, so the admin dashboard is untouched.
--
-- Note for greppers: 20260730090000:196 says "Ownership IS the custom flag:
-- is_custom is generated as user_id is not null". That comment sits next to
-- check_achievements, which counts `g.user_id = v_uid` directly and never
-- reads is_custom. Its behaviour is correct and unchanged -- "glyphs I
-- carved" really is an ownership question. Only the comment's aside is stale;
-- the function is not re-emitted here to avoid a create-or-replace over a body
-- this change has no reason to touch.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Drop the dependents, swap the column, put the dependents back.
-- -----------------------------------------------------------------------------
-- A generated column cannot be redefined in place (no ALTER ... SET
-- EXPRESSION for the generation expression in PG15), so this is DROP + ADD.
-- Both views bind `g.is_custom` at creation time and would block the drop, so
-- they come down first rather than riding a CASCADE we cannot see the extent
-- of. Their bodies are then restored VERBATIM from 20260501000006: the column
-- keeps its name and type, so the only thing that changed is what it means.
--
-- get_pebble_volume_series and get_pebble_enrichment are deliberately NOT
-- re-emitted. They are plpgsql, so their `g.is_custom` references resolve at
-- execution against whatever column exists then -- and it exists, unchanged in
-- name and type. Re-emitting them would be a no-op diff over two long bodies
-- and an extra create-or-replace to reason about.
drop view if exists public.v_analytics_pebble_enrichment_daily;
drop view if exists public.v_analytics_pebble_volume_daily;

alter table public.glyphs drop column is_custom;

alter table public.glyphs
  add column is_custom boolean
    generated always as (not is_system) stored;

comment on column public.glyphs.is_custom is
  'A person drew this: the negation of is_system, NOT of ownership (#874). '
  'purge_account anonymizes a sold glyph to user_id = null and it stays '
  'custom, because someone still drew it and someone still paid for it. '
  'Read by the analytics views and RPCs below; no client reads it.';

-- -----------------------------------------------------------------------------
-- 2. The two views, restored verbatim from 20260501000006.
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
-- 3. Permissions: a recreated view is a new object and carries no grants from
--    the old one. Re-apply the analytics slice's policy, as 20260501000006 did.
--    The two RPCs keep their grants -- they were never dropped.
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_pebble_volume_daily     from public, anon, authenticated;
revoke all on public.v_analytics_pebble_enrichment_daily from public, anon, authenticated;
