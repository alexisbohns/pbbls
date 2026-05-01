-- =============================================================================
-- Bounce score snapshot + Admin · Analytics · Bounce distribution
-- =============================================================================
-- Issue: #344
-- Reference: docs/poc/admin-analytics/20260430_analytics_mvs.sql
--            § mv_bounce_distribution_daily
--
-- Adds the missing data foundation for "bounce karma":
--
--   1. public.bounces (user_id, score, updated_at)
--      Snapshot of each user's running karma total. Maintained atomically by
--      a trigger on public.karma_events insert (each insert upserts the
--      delta into bounces in the same transaction). Backfilled once at
--      migration time from sum(karma_events.delta) per user.
--
--   2. v_analytics_bounce_distribution_today
--      Histogram of users by current bounce score, with three summary stats:
--      median bounce, % maintaining (current >= score 7 days ago, computed
--      from karma_events to avoid needing a snapshot history table), and
--      avg active days / week across MAU (active day = day with >=1 pebble).
--
--   3. get_bounce_distribution_today() RPC, gated on is_admin(auth.uid()).
--
-- Terminology: "bounce" is the table noun; "score" is the integer column.
-- The UI says "bounce karma" but the data layer uses score for brevity.
-- Negative cumulative scores (possible if reversals exceed prior gains) are
-- stored as-is; the analytics view buckets anything <= 0 into the "0" bin.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. public.bounces snapshot table
-- -----------------------------------------------------------------------------
create table if not exists public.bounces (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  score      integer not null default 0,
  updated_at timestamptz not null default now()
);

create index if not exists bounces_score_idx on public.bounces (score);

alter table public.bounces enable row level security;

-- Users can read their own bounce; admins can read all (via is_admin in RPCs).
drop policy if exists "bounces_select_self" on public.bounces;
create policy "bounces_select_self" on public.bounces
  for select using (user_id = auth.uid());

-- No INSERT/UPDATE/DELETE policies: writes happen exclusively through the
-- karma_events trigger (security definer). Direct writes from clients are
-- rejected by RLS.

-- -----------------------------------------------------------------------------
-- 2. Trigger: keep bounces in sync with karma_events
-- -----------------------------------------------------------------------------
create or replace function public.apply_karma_event_to_bounce()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.bounces (user_id, score, updated_at)
  values (new.user_id, new.delta, now())
  on conflict (user_id) do update
    set score      = public.bounces.score + excluded.score,
        updated_at = now();
  return new;
end;
$$;

drop trigger if exists karma_events_apply_to_bounce on public.karma_events;
create trigger karma_events_apply_to_bounce
  after insert on public.karma_events
  for each row execute function public.apply_karma_event_to_bounce();

-- -----------------------------------------------------------------------------
-- 3. One-shot backfill from existing karma_events
-- Idempotent: re-running this migration replays sum(delta) per user. The
-- on conflict clause overwrites whatever the trigger may have left behind
-- (e.g. partial inserts mid-migration).
-- -----------------------------------------------------------------------------
insert into public.bounces (user_id, score, updated_at)
select ke.user_id, sum(ke.delta)::int, now()
from public.karma_events ke
group by ke.user_id
on conflict (user_id) do update
  set score      = excluded.score,
      updated_at = excluded.updated_at;

-- -----------------------------------------------------------------------------
-- 4. v_analytics_bounce_distribution_today
-- One row per histogram bucket (always 6 rows, even if some are empty so the
-- chart doesn't have to coalesce). Summary stats are denormalized onto every
-- row to keep the page query a single SELECT.
-- -----------------------------------------------------------------------------
drop function if exists public.get_bounce_distribution_today();
drop view if exists public.v_analytics_bounce_distribution_today;

create view public.v_analytics_bounce_distribution_today as
with all_buckets(bucket_order, bucket_label, lo, hi) as (
  values
    (0, '0',      0,    0),
    (1, '1-10',   1,    10),
    (2, '11-25',  11,   25),
    (3, '26-50',  26,   50),
    (4, '51-100', 51,   100),
    (5, '100+',   101,  2147483647)
),
scored as (
  -- Floor at 0 only for bucketing; the underlying score column may be negative.
  select greatest(b.score, 0) as score
  from public.bounces b
),
binned as (
  select ab.bucket_order, ab.bucket_label,
         count(s.score)::int as users
  from all_buckets ab
  left join scored s on s.score between ab.lo and ab.hi
  group by ab.bucket_order, ab.bucket_label
),
median as (
  select percentile_cont(0.5) within group (order by score)::numeric as median_score
  from scored
),
maintaining as (
  -- A user "maintains" if their current score is >= the score they had 7 days
  -- ago. We reconstruct the 7-day-ago score from karma_events rather than
  -- relying on a snapshot history table (which doesn't exist yet).
  select
    case when count(*) = 0 then null
         else round(100.0 * count(*) filter (where curr >= prev) / count(*), 1)
    end as pct_maintaining
  from (
    select
      b.user_id,
      greatest(b.score, 0) as curr,
      greatest(coalesce((
        select sum(ke.delta)::int
        from public.karma_events ke
        where ke.user_id = b.user_id
          and ke.created_at < now() - interval '7 days'
      ), 0), 0) as prev
    from public.bounces b
  ) x
),
mau as (
  -- MAU = users with >=1 pebble in the last 30 days.
  select distinct p.user_id
  from public.pebbles p
  where p.created_at >= now() - interval '30 days'
),
active_days as (
  -- For each MAU user, count distinct calendar days with >=1 pebble in the
  -- last 7 days. Average across MAU. Users with zero days in the window
  -- still count as 0 in the average so the metric reflects all MAU.
  select
    case when count(*) = 0 then null
         else round(avg(coalesce(d.days, 0))::numeric, 2)
    end as avg_active_days_per_week
  from mau m
  left join lateral (
    select count(distinct p.created_at::date) as days
    from public.pebbles p
    where p.user_id = m.user_id
      and p.created_at >= now() - interval '7 days'
  ) d on true
)
select
  current_date            as bucket_date,
  b.bucket_order,
  b.bucket_label,
  b.users,
  med.median_score,
  mt.pct_maintaining,
  ad.avg_active_days_per_week
from binned b
cross join median       med
cross join maintaining  mt
cross join active_days  ad;

-- -----------------------------------------------------------------------------
-- 5. get_bounce_distribution_today() RPC
-- Enforces is_admin(auth.uid()).
-- -----------------------------------------------------------------------------
create or replace function public.get_bounce_distribution_today()
returns setof public.v_analytics_bounce_distribution_today
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
    select * from public.v_analytics_bounce_distribution_today
    order by bucket_order asc;
end;
$$;

-- -----------------------------------------------------------------------------
-- Permissions
-- -----------------------------------------------------------------------------
revoke all on public.v_analytics_bounce_distribution_today from public, anon, authenticated;

grant execute on function public.get_bounce_distribution_today() to authenticated;
