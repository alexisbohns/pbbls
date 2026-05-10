-- Migration: orphan snap file sweep
--
-- Adds public.sweep_orphan_snap_files(), which identifies files in the
-- pebbles-media bucket whose second path segment (snap UUID) has no
-- matching public.snaps row and queues their deletion via the Supabase
-- Storage HTTP API (pg_net). Direct DELETE on storage.objects is
-- blocked by storage.protect_delete(); the HTTP API is the correct path.
--
-- Two orphan sources closed:
--   (a) upload-then-abandon before create_pebble commits.
--   (b) pebble delete cascades the public.snaps row; Storage is unreachable
--       from Postgres triggers.
--
-- Pre-requisite (run once, not part of the migration):
--   alter database postgres
--     set app.settings.supabase_url        = 'https://<ref>.supabase.co';
--   alter database postgres
--     set app.settings.service_role_key    = '<service_role_jwt>';
-- These are read at call time via current_setting(), so the database
-- must be reconnected (or reloaded) after the ALTER DATABASE statements.

-- ============================================================
-- Extensions (idempotent)
-- ============================================================

create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;

-- ============================================================
-- Sweep function
-- ============================================================
-- Iterates orphan objects and fires one async DELETE request per file
-- via net.http_delete. Returns the count and estimated bytes of the
-- files queued for deletion. Actual removal is asynchronous — pg_net
-- sends the requests after the transaction commits.
--
-- The async nature is safe for the rollback dry-run: pg_net rows in
-- net.http_request_queue are rolled back with the transaction, so
-- no requests fire if the caller issues ROLLBACK.
--
-- Access: callable by postgres (pg_cron) and service_role (admin API only).
-- Revoked from public so regular authenticated users cannot invoke it.

create or replace function public.sweep_orphan_snap_files()
returns table (queued_count bigint, estimated_bytes bigint)
language plpgsql security definer
set search_path = public
as $$
declare
  v_row   record;
  v_count bigint := 0;
  v_bytes bigint := 0;
  v_url   text;
  v_key   text;
begin
  v_url := current_setting('app.settings.supabase_url',     true);
  v_key := current_setting('app.settings.service_role_key', true);

  if v_url is null or v_key is null then
    raise exception
      'sweep_orphan_snap_files requires app.settings.supabase_url and '
      'app.settings.service_role_key to be set on the database. '
      'See migration header for the ALTER DATABASE commands.';
  end if;

  for v_row in
    select
      o.name,
      coalesce((o.metadata->>'size')::bigint, 0) as bytes
    from storage.objects o
    where o.bucket_id = 'pebbles-media'
      and array_length(storage.foldername(o.name), 1) >= 2
      and (storage.foldername(o.name))[2]
            ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      and not exists (
        select 1 from public.snaps s
        where s.id = ((storage.foldername(o.name))[2])::uuid
      )
  loop
    -- DELETE /storage/v1/object/{bucket}/{*path} — single-file endpoint,
    -- usable with net.http_delete (which has no request-body parameter).
    perform net.http_delete(
      url     := v_url || '/storage/v1/object/pebbles-media/' || v_row.name,
      headers := jsonb_build_object(
        'Authorization', 'Bearer ' || v_key
      )
    );

    v_count := v_count + 1;
    v_bytes := v_bytes + v_row.bytes;
  end loop;

  return query select v_count, v_bytes;
end;
$$;

revoke execute on function public.sweep_orphan_snap_files() from public;
grant  execute on function public.sweep_orphan_snap_files() to postgres;
grant  execute on function public.sweep_orphan_snap_files() to service_role;

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
