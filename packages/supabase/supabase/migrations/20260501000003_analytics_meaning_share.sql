-- =============================================================================
-- Admin · Analytics · Meaning row (emotion share + domain share)
-- =============================================================================
-- Issue: #342
-- Reference: docs/poc/admin-analytics/20260430_analytics_mvs.sql
--            §§ mv_emotion_share_weekly + mv_domain_share_weekly
--
-- Schema notes (vs. the POC reference SQL):
--   - Emotion source is `pebbles.emotion_id` (NOT NULL FK), not the
--     `pebble_emotions` join the POC assumed. Each pebble counts toward
--     exactly one emotion, so emotion shares sum to 100% per week.
--   - `domains` has no `level` column — only (id, slug, name, label). We
--     derive `domain_level` from the seeded slug order so the over-time and
--     snapshot views can keep stacking domains in Maslow order.
--   - Soft-delete does not exist in this project, so no deleted_at filters.
-- =============================================================================

drop function if exists public.get_domain_share(date, date);
drop function if exists public.get_emotion_share(date, date);
drop view if exists public.v_analytics_domain_share_weekly;
drop view if exists public.v_analytics_emotion_share_weekly;

-- -----------------------------------------------------------------------------
-- v_analytics_emotion_share_weekly
-- One row per (ISO week, emotion). Each pebble has exactly one emotion via
-- pebbles.emotion_id, so share_pct sums to 100% within a bucket_week.
-- -----------------------------------------------------------------------------
create view public.v_analytics_emotion_share_weekly as
with weekly_pebbles as (
  select
    date_trunc('week', p.created_at)::date as bucket_week,
    p.id,
    p.emotion_id
  from public.pebbles p
),
totals as (
  select bucket_week, count(*)::int as total_pebbles
  from weekly_pebbles
  group by bucket_week
)
select
  wp.bucket_week,
  e.id    as emotion_id,
  e.slug  as emotion_slug,
  e.name  as emotion_name,
  e.color,
  count(*)::int                                                  as pebbles_with_emotion,
  t.total_pebbles,
  round(100.0 * count(*) / nullif(t.total_pebbles, 0), 2)        as share_pct
from weekly_pebbles wp
join public.emotions e on e.id = wp.emotion_id
join totals t          on t.bucket_week = wp.bucket_week
group by wp.bucket_week, e.id, e.slug, e.name, e.color, t.total_pebbles;

-- -----------------------------------------------------------------------------
-- v_analytics_domain_share_weekly
-- One row per (ISO week, domain). A pebble can be linked to multiple domains
-- via pebble_domains, so domain shares do NOT need to sum to 100%.
-- domain_level is derived from the seeded Maslow slug order; new slugs not in
-- the array sort last (NULLs last).
-- -----------------------------------------------------------------------------
create view public.v_analytics_domain_share_weekly as
with weekly_pebbles as (
  select
    date_trunc('week', p.created_at)::date as bucket_week,
    p.id
  from public.pebbles p
),
totals as (
  select bucket_week, count(*)::int as total_pebbles
  from weekly_pebbles
  group by bucket_week
)
select
  wp.bucket_week,
  d.id                                                                       as domain_id,
  d.slug                                                                     as domain_slug,
  d.name                                                                     as domain_name,
  d.label                                                                    as domain_label,
  array_position(
    array['zoe','asphaleia','philia','time','eudaimonia']::text[],
    d.slug
  )                                                                          as domain_level,
  count(distinct wp.id)::int                                                 as pebbles_in_domain,
  t.total_pebbles,
  round(100.0 * count(distinct wp.id) / nullif(t.total_pebbles, 0), 2)       as share_pct
from weekly_pebbles wp
join public.pebble_domains pd on pd.pebble_id = wp.id
join public.domains d         on d.id = pd.domain_id
join totals t                 on t.bucket_week = wp.bucket_week
group by wp.bucket_week, d.id, d.slug, d.name, d.label, t.total_pebbles;

-- -----------------------------------------------------------------------------
-- get_emotion_share(p_start, p_end)
-- Weekly rows from v_analytics_emotion_share_weekly whose bucket_week falls
-- within [p_start, p_end] (week-truncated). The card uses these rows for both
-- the snapshot (aggregating across weeks) and the stacked-area over-time view.
-- Enforces is_admin(auth.uid()).
-- -----------------------------------------------------------------------------
create or replace function public.get_emotion_share(
  p_start date,
  p_end   date
)
returns setof public.v_analytics_emotion_share_weekly
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_start date := date_trunc('week', p_start::timestamptz)::date;
  v_end   date := date_trunc('week', p_end::timestamptz)::date;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
    select * from public.v_analytics_emotion_share_weekly
    where bucket_week between v_start and v_end
    order by bucket_week asc, share_pct desc;
end;
$$;

-- -----------------------------------------------------------------------------
-- get_domain_share(p_start, p_end)
-- Weekly rows from v_analytics_domain_share_weekly whose bucket_week falls
-- within [p_start, p_end]. The domain card aggregates across weeks for the
-- snapshot and calls this RPC twice (current + previous period) to compute
-- "biggest movers" deltas in pp.
-- Enforces is_admin(auth.uid()).
-- -----------------------------------------------------------------------------
create or replace function public.get_domain_share(
  p_start date,
  p_end   date
)
returns setof public.v_analytics_domain_share_weekly
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_start date := date_trunc('week', p_start::timestamptz)::date;
  v_end   date := date_trunc('week', p_end::timestamptz)::date;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
    select * from public.v_analytics_domain_share_weekly
    where bucket_week between v_start and v_end
    order by bucket_week asc, domain_level asc nulls last;
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions: lock the views, expose the RPCs to authenticated callers
-- (the RPCs gate on is_admin(auth.uid())).
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_emotion_share_weekly from public, anon, authenticated;
revoke all on public.v_analytics_domain_share_weekly  from public, anon, authenticated;

grant execute on function public.get_emotion_share(date, date) to authenticated;
grant execute on function public.get_domain_share(date, date)  to authenticated;
