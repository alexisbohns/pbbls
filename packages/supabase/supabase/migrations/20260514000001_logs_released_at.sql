-- Migration: Add released_at to logs
-- The admin UI and the changelog feeds (web + iOS) already reference
-- `released_at` — it distinguishes when a feature actually shipped from
-- when its log row was first published. The column was never added to the
-- table, so any query that selects or orders by it crashes
-- (`column v_logs_with_counts.released_at does not exist`).
--
-- The view `v_logs_with_counts` is `select l.*`, which Postgres expands
-- to the column list at creation time. Adding a column to `logs` is not
-- enough — the view has to be recreated so it picks up the new column.

alter table public.logs
  add column released_at timestamptz;

-- Backfill existing shipped rows from `published_at` so the changelog
-- ordering (`released_at desc nulls last, published_at desc`) is stable
-- on rows authored before this migration.
update public.logs
  set released_at = published_at
  where status = 'shipped' and released_at is null;

create index logs_released_at_idx on public.logs (released_at desc);

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
