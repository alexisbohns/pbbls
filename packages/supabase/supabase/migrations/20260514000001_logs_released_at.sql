-- Migration: Expose released_at on v_logs_with_counts
-- `logs.released_at` already exists on the remote DB (added out-of-band)
-- and the admin UI / web changelog feed already write and read it. But
-- `v_logs_with_counts` was created with `select l.*`, which Postgres
-- expands to a fixed column list at view-creation time — so the view
-- never picked up `released_at` and any query selecting/ordering by it
-- crashes with "column v_logs_with_counts.released_at does not exist".
--
-- The alter/index statements use `if not exists` so the migration is
-- safe whether the column was pre-added in production or is missing on
-- a fresh local DB.

alter table public.logs
  add column if not exists released_at timestamptz;

-- Backfill any shipped rows that pre-date the column so the changelog
-- ordering (`released_at desc nulls last, published_at desc`) is stable.
-- No-op on rows that already have a release date.
update public.logs
  set released_at = published_at
  where status = 'shipped' and released_at is null;

create index if not exists logs_released_at_idx on public.logs (released_at desc);

drop view if exists public.v_logs_with_counts;

create view public.v_logs_with_counts
with (security_invoker = true) as
select
  l.*,
  coalesce(rc.reaction_count, 0)::int as reaction_count
from public.logs l
left join lateral (
  select count(*)::int as reaction_count
  from public.log_reactions lr
  where lr.log_id = l.id
) rc on true;
