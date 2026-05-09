-- Migration: Logs released_at
-- Adds an explicit release date for shipped log entries so that batch-curated
-- features can be sorted/displayed by their actual ship date rather than by
-- when the admin happened to record them.
--
-- The admin form auto-populates this column when a log transitions to
-- 'shipped' and the user has not provided a date. Existing shipped rows are
-- backfilled from published_at (when published) or updated_at as a best-effort
-- approximation of when they shipped.

alter table public.logs
  add column released_at timestamptz;

update public.logs
  set released_at = coalesce(published_at, updated_at)
  where status = 'shipped' and released_at is null;

create index logs_released_at_idx on public.logs (released_at desc);
