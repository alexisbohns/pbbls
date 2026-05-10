-- Migration: orphan snap file sweep
--
-- Adds public.sweep_orphan_snap_files(), a security-definer RPC that
-- deletes storage.objects rows from the pebbles-media bucket whose
-- second path segment (the snap UUID) has no matching public.snaps row.
--
-- Two orphan sources closed:
--   (a) upload-then-abandon before create_pebble commits the transaction.
--   (b) pebble delete cascades the public.snaps row but cannot reach Storage.
--
-- Also schedules a pg_cron job to run the sweep daily at 03:00 UTC.
-- The extension, the function, and the job are all created idempotently.

-- ============================================================
-- pg_cron extension (idempotent)
-- ============================================================

create extension if not exists pg_cron;

-- ============================================================
-- Sweep function
-- ============================================================
-- Deletes every storage.objects row in pebbles-media whose second
-- path segment (snap_id) is a valid UUID with no matching snaps row.
-- Returns the count of deleted objects and total bytes freed.
-- Security definer so the function can access storage.objects regardless
-- of the caller's RLS context. Not granted to authenticated — admin only
-- (invoke via the Supabase SQL editor or with the service_role key).

create or replace function public.sweep_orphan_snap_files()
returns table (deleted_count bigint, bytes_freed bigint)
language plpgsql security definer
set search_path = public
as $$
declare
  v_count bigint;
  v_bytes bigint;
begin
  with deleted as (
    delete from storage.objects
    where bucket_id = 'pebbles-media'
      and array_length(storage.foldername(name), 1) >= 2
      and (storage.foldername(name))[2] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      and not exists (
        select 1 from public.snaps s
        where s.id = ((storage.foldername(name))[2])::uuid
      )
    returning coalesce((metadata->>'size')::bigint, 0) as file_size
  )
  select count(*), coalesce(sum(file_size), 0)
  into v_count, v_bytes
  from deleted;

  return query select v_count, v_bytes;
end;
$$;

-- ============================================================
-- pg_cron: daily at 03:00 UTC (idempotent)
-- ============================================================

do $$
begin
  if exists (select 1 from cron.job where jobname = 'sweep_orphan_snap_files') then
    perform cron.unschedule('sweep_orphan_snap_files');
  end if;
end;
$$;

select cron.schedule(
  'sweep_orphan_snap_files',
  '0 3 * * *',
  $$select public.sweep_orphan_snap_files()$$
);
