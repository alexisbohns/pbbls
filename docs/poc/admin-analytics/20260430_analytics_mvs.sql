-- =============================================================================
-- Admin · Analytics · Materialized Views
-- =============================================================================
-- Source of truth: docs/specs/admin-analytics.md
--
-- Conventions:
--   * One MV per surface on the analytics page. No cross-MV joins at query time.
--   * Every MV exposes a `bucket_date` (or `bucket_week`) column. The Next.js
--     fetchers read `where bucket_date = (select max(bucket_date) from <mv>)`
--     for "current" values and a date range for time series.
--   * UTC throughout. Locale-aware bucketing is a v2 concern (see spec).
--   * "Active user" = user who created ≥1 pebble in the period.
--   * Soft-deleted pebbles (`deleted_at is not null`) are excluded everywhere.
--   * Indexes are defined per MV; we use UNIQUE indexes where possible so we
--     can `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
--
-- Schema assumptions (verify against the actual schema before merging):
--   users           (id, created_at, deleted_at, role)
--   pebbles         (id, user_id, created_at, deleted_at, picture_url,
--                    glyph_id, collection_id, intensity, visibility, thought)
--   glyphs          (id, user_id, is_custom)
--   souls           (id, user_id, created_at)
--   collections     (id, user_id, created_at)
--   emotions        (id, name, color, primary_domain_id)
--   pebble_emotions (pebble_id, emotion_id, intensity)
--   domains         (id, name, level)
--   pebble_domains  (pebble_id, domain_id)
--   pebble_souls    (pebble_id, soul_id)
--   bounces         (user_id, score, updated_at)
--   user_active_days(user_id, active_date)            -- daily activity ledger
--   cairns          (id, user_id, period, period_start, status, completed_at)
--   sessions        (id, user_id, started_at, ended_at)  -- TBC, see spec Q1
-- =============================================================================

-- Drop in reverse-dependency order so this migration is idempotent in dev.
drop materialized view if exists mv_quality_signals_daily       cascade;
drop materialized view if exists mv_visibility_mix_daily        cascade;
drop materialized view if exists mv_cairn_participation_weekly  cascade;
drop materialized view if exists mv_domain_share_weekly         cascade;
drop materialized view if exists mv_emotion_share_weekly        cascade;
drop materialized view if exists mv_bounce_distribution_daily   cascade;
drop materialized view if exists mv_user_averages_weekly        cascade;
drop materialized view if exists mv_pebble_enrichment_daily     cascade;
drop materialized view if exists mv_pebble_volume_daily         cascade;
drop materialized view if exists mv_retention_cohorts_weekly    cascade;
drop materialized view if exists mv_active_users_daily          cascade;
drop materialized view if exists mv_kpi_daily                   cascade;

-- =============================================================================
-- mv_kpi_daily
-- One row per day. Powers the KPI strip.
-- =============================================================================
create materialized view mv_kpi_daily as
with days as (
  select generate_series(
    (select min(created_at)::date from pebbles),
    current_date,
    interval '1 day'
  )::date as bucket_date
),
total as (
  select bucket_date,
    (select count(*) from users u where u.created_at::date <= d.bucket_date and u.deleted_at is null) as total_users
  from days d
),
dau as (
  select d.bucket_date,
    count(distinct p.user_id) filter (where p.created_at::date = d.bucket_date) as dau,
    count(*)                  filter (where p.created_at::date = d.bucket_date) as pebbles_today
  from days d
  left join pebbles p on p.created_at::date = d.bucket_date and p.deleted_at is null
  group by d.bucket_date
),
wau as (
  select d.bucket_date,
    (select count(distinct p.user_id) from pebbles p
       where p.deleted_at is null
         and p.created_at::date >  d.bucket_date - 7
         and p.created_at::date <= d.bucket_date) as wau
  from days d
),
mau as (
  select d.bucket_date,
    (select count(distinct p.user_id) from pebbles p
       where p.deleted_at is null
         and p.created_at::date >  d.bucket_date - 30
         and p.created_at::date <= d.bucket_date) as mau
  from days d
)
select
  t.bucket_date,
  t.total_users,
  d.dau,
  d.pebbles_today,
  w.wau,
  m.mau,
  case when m.mau > 0 then round((d.dau::numeric / m.mau) * 100, 2) else null end as dau_mau_pct
from total t
join dau d using (bucket_date)
join wau w using (bucket_date)
join mau m using (bucket_date);

create unique index on mv_kpi_daily (bucket_date);

-- =============================================================================
-- mv_active_users_daily
-- Daily DAU/WAU/MAU series for the line chart. Same numbers as mv_kpi_daily
-- but split into its own MV so the chart query is small and fast.
-- =============================================================================
create materialized view mv_active_users_daily as
select bucket_date, dau, wau, mau
from mv_kpi_daily;

create unique index on mv_active_users_daily (bucket_date);

-- =============================================================================
-- mv_retention_cohorts_weekly
-- One row per (cohort_week, week_offset). cohort_week is the Monday of the
-- user's signup week. week_offset = 0 is the signup week itself.
-- =============================================================================
create materialized view mv_retention_cohorts_weekly as
with cohorts as (
  select
    date_trunc('week', u.created_at)::date as cohort_week,
    u.id as user_id
  from users u
  where u.deleted_at is null
),
cohort_size as (
  select cohort_week, count(*) as size
  from cohorts
  group by cohort_week
),
activity as (
  select
    c.cohort_week,
    floor(extract(epoch from (date_trunc('week', p.created_at) - c.cohort_week)) / 604800)::int as week_offset,
    c.user_id
  from cohorts c
  join pebbles p on p.user_id = c.user_id and p.deleted_at is null
  where p.created_at >= c.cohort_week
)
select
  a.cohort_week,
  a.week_offset,
  cs.size as cohort_size,
  count(distinct a.user_id) as active_users,
  round((count(distinct a.user_id)::numeric / cs.size) * 100, 1) as retention_pct
from activity a
join cohort_size cs using (cohort_week)
group by a.cohort_week, a.week_offset, cs.size;

create unique index on mv_retention_cohorts_weekly (cohort_week, week_offset);

-- =============================================================================
-- mv_pebble_volume_daily
-- Pebble volume + enrichment overlay counts per day.
-- =============================================================================
create materialized view mv_pebble_volume_daily as
select
  p.created_at::date as bucket_date,
  count(*)                                                     as pebbles,
  count(*) filter (where p.picture_url is not null)            as pebbles_with_picture,
  count(*) filter (where g.is_custom)                          as pebbles_with_custom_glyph,
  count(*) filter (where p.collection_id is not null)          as pebbles_in_collection,
  count(distinct p.user_id)                                    as active_users
from pebbles p
left join glyphs g on g.id = p.glyph_id
where p.deleted_at is null
group by p.created_at::date;

create unique index on mv_pebble_volume_daily (bucket_date);

-- =============================================================================
-- mv_pebble_enrichment_daily
-- Same source as mv_pebble_volume_daily but pre-computes the share fields
-- the donuts and secondary ratios need. Kept separate so the donut card
-- doesn't need to do arithmetic in the page.
-- =============================================================================
create materialized view mv_pebble_enrichment_daily as
with base as (
  select
    p.created_at::date as bucket_date,
    p.id,
    p.picture_url,
    p.collection_id,
    p.thought,
    p.intensity,
    g.is_custom as glyph_is_custom,
    exists (select 1 from pebble_emotions pe where pe.pebble_id = p.id) as has_emotion,
    exists (select 1 from pebble_souls    ps where ps.pebble_id = p.id) as has_soul
  from pebbles p
  left join glyphs g on g.id = p.glyph_id
  where p.deleted_at is null
)
select
  bucket_date,
  count(*) as total_pebbles,
  round(100.0 * count(*) filter (where picture_url is not null) / nullif(count(*), 0), 1) as pct_with_picture,
  round(100.0 * count(*) filter (where glyph_is_custom)         / nullif(count(*), 0), 1) as pct_with_custom_glyph,
  round(100.0 * count(*) filter (where collection_id is not null) / nullif(count(*), 0), 1) as pct_in_collection,
  round(100.0 * count(*) filter (where has_emotion)             / nullif(count(*), 0), 1) as pct_with_emotion,
  round(100.0 * count(*) filter (where has_soul)                / nullif(count(*), 0), 1) as pct_with_soul,
  round(100.0 * count(*) filter (where thought is not null and length(thought) > 0) / nullif(count(*), 0), 1) as pct_with_thought,
  round(100.0 * count(*) filter (where intensity is not null)   / nullif(count(*), 0), 1) as pct_with_intensity
from base
group by bucket_date;

create unique index on mv_pebble_enrichment_daily (bucket_date);

-- =============================================================================
-- mv_user_averages_weekly
-- Per-user averages of glyphs/souls/collections, computed weekly.
-- Denominator = active users that week (≥1 pebble in week).
-- =============================================================================
create materialized view mv_user_averages_weekly as
with weeks as (
  select date_trunc('week', d)::date as bucket_week
  from generate_series(
    date_trunc('week', (select min(created_at) from pebbles))::date,
    date_trunc('week', current_date)::date,
    interval '1 week'
  ) d
),
active_users_in_week as (
  select date_trunc('week', p.created_at)::date as bucket_week,
         count(distinct p.user_id) as active_users
  from pebbles p
  where p.deleted_at is null
  group by 1
),
glyph_totals as (
  select w.bucket_week,
         (select count(*) from glyphs gg
            where gg.user_id in (select distinct p.user_id from pebbles p
                                  where p.deleted_at is null
                                    and date_trunc('week', p.created_at)::date = w.bucket_week)
         ) as glyph_count
  from weeks w
),
soul_totals as (
  select w.bucket_week,
         (select count(*) from souls ss
            where ss.user_id in (select distinct p.user_id from pebbles p
                                  where p.deleted_at is null
                                    and date_trunc('week', p.created_at)::date = w.bucket_week)
         ) as soul_count
  from weeks w
),
collection_totals as (
  select w.bucket_week,
         (select count(*) from collections cc
            where cc.user_id in (select distinct p.user_id from pebbles p
                                  where p.deleted_at is null
                                    and date_trunc('week', p.created_at)::date = w.bucket_week)
         ) as collection_count
  from weeks w
)
select
  w.bucket_week,
  coalesce(au.active_users, 0) as active_users,
  case when au.active_users > 0 then round(gt.glyph_count::numeric      / au.active_users, 2) else 0 end as avg_glyphs,
  case when au.active_users > 0 then round(st.soul_count::numeric       / au.active_users, 2) else 0 end as avg_souls,
  case when au.active_users > 0 then round(ct.collection_count::numeric / au.active_users, 2) else 0 end as avg_collections
from weeks w
left join active_users_in_week au on au.bucket_week = w.bucket_week
left join glyph_totals gt        on gt.bucket_week = w.bucket_week
left join soul_totals st         on st.bucket_week = w.bucket_week
left join collection_totals ct   on ct.bucket_week = w.bucket_week;

create unique index on mv_user_averages_weekly (bucket_week);

-- =============================================================================
-- mv_bounce_distribution_daily
-- Histogram of users by current bounce score, plus summary stats.
-- One row per (bucket_date, bucket_label).
-- =============================================================================
create materialized view mv_bounce_distribution_daily as
with today as (
  select current_date as bucket_date
),
binned as (
  select
    t.bucket_date,
    case
      when b.score = 0                     then '0'
      when b.score between 1   and 10      then '1-10'
      when b.score between 11  and 25      then '11-25'
      when b.score between 26  and 50      then '26-50'
      when b.score between 51  and 100     then '51-100'
      else                                      '100+'
    end as bucket_label,
    case
      when b.score = 0                     then 0
      when b.score between 1   and 10      then 1
      when b.score between 11  and 25      then 2
      when b.score between 26  and 50      then 3
      when b.score between 51  and 100     then 4
      else                                      5
    end as bucket_order,
    b.score
  from today t
  cross join bounces b
)
select
  bucket_date,
  bucket_order,
  bucket_label,
  count(*)               as users,
  -- summary stats are repeated on every row to keep the page query simple
  (select percentile_cont(0.5) within group (order by score)::int from binned) as median_score,
  (select round(100.0 * count(*) filter (where score >= coalesce(
        (select score from bounces b2 where b2.user_id = b.user_id and b2.updated_at < current_date - 7
          order by b2.updated_at desc limit 1), 0))
     / nullif(count(*), 0), 1)
   from bounces b)                                                              as pct_maintaining,
  (select round(avg(active_days), 2) from (
      select count(distinct active_date) as active_days
      from user_active_days
      where active_date > current_date - 7
      group by user_id
    ) x)                                                                        as avg_active_days_per_week
from binned
group by bucket_date, bucket_order, bucket_label;

create unique index on mv_bounce_distribution_daily (bucket_date, bucket_order);

-- =============================================================================
-- mv_emotion_share_weekly
-- Share of pebbles tagged with each emotion, per week.
-- A pebble can have multiple pearls; shares do NOT need to sum to 100%.
-- =============================================================================
create materialized view mv_emotion_share_weekly as
with weekly_pebbles as (
  select date_trunc('week', p.created_at)::date as bucket_week, p.id
  from pebbles p
  where p.deleted_at is null
),
totals as (
  select bucket_week, count(*) as total_pebbles
  from weekly_pebbles
  group by bucket_week
)
select
  wp.bucket_week,
  e.id   as emotion_id,
  e.name as emotion_name,
  e.color,
  count(distinct wp.id) as pebbles_with_emotion,
  t.total_pebbles,
  round(100.0 * count(distinct wp.id) / nullif(t.total_pebbles, 0), 2) as share_pct
from weekly_pebbles wp
join pebble_emotions pe on pe.pebble_id = wp.id
join emotions e         on e.id = pe.emotion_id
join totals t           on t.bucket_week = wp.bucket_week
group by wp.bucket_week, e.id, e.name, e.color, t.total_pebbles;

create unique index on mv_emotion_share_weekly (bucket_week, emotion_id);

-- =============================================================================
-- mv_domain_share_weekly
-- Share of pebbles linked to each Maslow domain, per week.
-- =============================================================================
create materialized view mv_domain_share_weekly as
with weekly_pebbles as (
  select date_trunc('week', p.created_at)::date as bucket_week, p.id
  from pebbles p
  where p.deleted_at is null
),
totals as (
  select bucket_week, count(*) as total_pebbles
  from weekly_pebbles
  group by bucket_week
)
select
  wp.bucket_week,
  d.id    as domain_id,
  d.name  as domain_name,
  d.level as domain_level,
  count(distinct wp.id) as pebbles_in_domain,
  t.total_pebbles,
  round(100.0 * count(distinct wp.id) / nullif(t.total_pebbles, 0), 2) as share_pct
from weekly_pebbles wp
join pebble_domains pd on pd.pebble_id = wp.id
join domains d         on d.id = pd.domain_id
join totals t          on t.bucket_week = wp.bucket_week
group by wp.bucket_week, d.id, d.name, d.level, t.total_pebbles;

create unique index on mv_domain_share_weekly (bucket_week, domain_id);

-- =============================================================================
-- mv_cairn_participation_weekly
-- Participation rates for weekly and monthly cairns over the last 12 periods.
-- =============================================================================
create materialized view mv_cairn_participation_weekly as
select
  c.period,                   -- 'weekly' | 'monthly'
  c.period_start,
  count(*) filter (where c.status = 'completed') as completed,
  count(*) filter (where c.status = 'partial')   as partial,
  count(*) filter (where c.status = 'missed')    as missed,
  count(*)                                       as total,
  round(100.0 * count(*) filter (where c.status = 'completed') / nullif(count(*), 0), 1) as completed_pct,
  round(100.0 * count(*) filter (where c.status = 'partial')   / nullif(count(*), 0), 1) as partial_pct,
  round(100.0 * count(*) filter (where c.status = 'missed')    / nullif(count(*), 0), 1) as missed_pct
from cairns c
where c.period_start >= current_date - interval '12 weeks'
group by c.period, c.period_start;

create unique index on mv_cairn_participation_weekly (period, period_start);

-- =============================================================================
-- mv_visibility_mix_daily
-- Pebble counts by visibility per day. The page sums the trailing window.
-- =============================================================================
create materialized view mv_visibility_mix_daily as
select
  p.created_at::date as bucket_date,
  p.visibility,
  count(*) as pebbles
from pebbles p
where p.deleted_at is null
group by p.created_at::date, p.visibility;

create unique index on mv_visibility_mix_daily (bucket_date, visibility);

-- =============================================================================
-- mv_quality_signals_daily
-- One row per day with the eight habit-health metrics.
-- NOTE: depends on `sessions` table — see spec open question Q1.
-- =============================================================================
create materialized view mv_quality_signals_daily as
with today as (select current_date as bucket_date),
session_metrics as (
  select t.bucket_date,
    percentile_cont(0.5) within group (
      order by extract(epoch from (s.ended_at - s.started_at))
    )::int as median_session_seconds,
    round((select count(*) from sessions s2
             where s2.started_at > current_date - 7)::numeric
          / nullif((select count(distinct user_id) from pebbles p
                      where p.deleted_at is null
                        and p.created_at > current_date - 7), 0), 2) as sessions_per_wau
  from today t
  left join sessions s on s.started_at > current_date - 7
  group by t.bucket_date
),
pebbles_per_wau as (
  select t.bucket_date,
    round(
      (select count(*) from pebbles p
         where p.deleted_at is null and p.created_at > current_date - 7)::numeric
      / nullif((select count(distinct user_id) from pebbles p
                  where p.deleted_at is null and p.created_at > current_date - 7), 0), 2)
      as pebbles_per_wau
  from today t
),
retention as (
  select t.bucket_date,
    round(100.0 *
      (select count(distinct u.id) from users u
         join pebbles p on p.user_id = u.id and p.deleted_at is null
         where u.created_at::date = current_date - 1
           and p.created_at::date = current_date)
      / nullif((select count(*) from users u where u.created_at::date = current_date - 1), 0), 1)
      as d1_retention,
    round(100.0 *
      (select count(distinct u.id) from users u
         join pebbles p on p.user_id = u.id and p.deleted_at is null
         where u.created_at::date = current_date - 7
           and p.created_at::date between current_date - 7 and current_date)
      / nullif((select count(*) from users u where u.created_at::date = current_date - 7), 0), 1)
      as d7_retention,
    round(100.0 *
      (select count(distinct u.id) from users u
         join pebbles p on p.user_id = u.id and p.deleted_at is null
         where u.created_at::date = current_date - 30
           and p.created_at::date between current_date - 30 and current_date)
      / nullif((select count(*) from users u where u.created_at::date = current_date - 30), 0), 1)
      as d30_retention
  from today t
)
select
  t.bucket_date,
  sm.median_session_seconds,
  sm.sessions_per_wau,
  pw.pebbles_per_wau,
  -- TODO: % revisits to past pebbles needs a `pebble_views` table; placeholder.
  null::numeric                  as pct_revisits_to_past_pebbles,
  r.d1_retention,
  r.d7_retention,
  r.d30_retention,
  -- TODO: friction events / session needs a normalized analytics events table.
  null::numeric                  as friction_events_per_session
from today t
left join session_metrics sm on sm.bucket_date = t.bucket_date
left join pebbles_per_wau pw on pw.bucket_date = t.bucket_date
left join retention r        on r.bucket_date  = t.bucket_date;

create unique index on mv_quality_signals_daily (bucket_date);

-- =============================================================================
-- Refresh function + nightly cron
-- =============================================================================
create or replace function refresh_analytics_mvs()
returns void
language plpgsql
security definer
as $$
begin
  refresh materialized view concurrently mv_kpi_daily;
  refresh materialized view concurrently mv_active_users_daily;
  refresh materialized view concurrently mv_retention_cohorts_weekly;
  refresh materialized view concurrently mv_pebble_volume_daily;
  refresh materialized view concurrently mv_pebble_enrichment_daily;
  refresh materialized view concurrently mv_user_averages_weekly;
  refresh materialized view concurrently mv_bounce_distribution_daily;
  refresh materialized view concurrently mv_emotion_share_weekly;
  refresh materialized view concurrently mv_domain_share_weekly;
  refresh materialized view concurrently mv_cairn_participation_weekly;
  refresh materialized view concurrently mv_visibility_mix_daily;
  refresh materialized view concurrently mv_quality_signals_daily;
end;
$$;

-- pg_cron job: nightly at 03:00 UTC
-- Requires the pg_cron extension to be enabled in the Supabase project.
select cron.schedule(
  'refresh_analytics_mvs_nightly',
  '0 3 * * *',
  $$select refresh_analytics_mvs();$$
);

-- =============================================================================
-- RLS: admin-only access on every MV
-- =============================================================================
-- Supabase exposes MVs via PostgREST. We secure them with a policy that checks
-- the JWT for the admin role. Adjust the role claim path to match the actual
-- claim shape used in the app's JWT.

do $$
declare mv_name text;
begin
  for mv_name in
    select unnest(array[
      'mv_kpi_daily',
      'mv_active_users_daily',
      'mv_retention_cohorts_weekly',
      'mv_pebble_volume_daily',
      'mv_pebble_enrichment_daily',
      'mv_user_averages_weekly',
      'mv_bounce_distribution_daily',
      'mv_emotion_share_weekly',
      'mv_domain_share_weekly',
      'mv_cairn_participation_weekly',
      'mv_visibility_mix_daily',
      'mv_quality_signals_daily'
    ])
  loop
    execute format('alter materialized view %I owner to postgres;', mv_name);
    execute format('grant select on %I to authenticated;',           mv_name);
    execute format(
      'create policy %I_admin_only on %I for select using (
         (auth.jwt() ->> ''role'') = ''admin''
       );', mv_name, mv_name);
  end loop;
end $$;
