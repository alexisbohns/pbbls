-- Migration: public.sweep_orphan_snap_files (#796, for #322)
--
-- Recovered from the linked project, where it was created directly in Supabase
-- Studio and never written back. Found by the `schema` CI job (#794/#795): with
-- the migration chain finally replaying to the end, the generated types no longer
-- matched the committed types/database.ts, and this function was the difference —
-- present in the committed types (generated from the project), absent from every
-- migration.
--
-- It is the third thing this stack has found living only in the production
-- database, after emotion_categories and the 38-row emotions roster. The body
-- below is `pg_get_functiondef` output from the project, reproduced verbatim so
-- `create or replace` is a no-op there and a fresh database gets the same
-- function.
--
-- WHAT IT DOES. Deletes objects under the `pebbles-media` bucket whose second path
-- segment is a UUID with no matching public.snaps row, and reports how many and
-- how many bytes. It is the server half of the orphan problem tracked by #322:
-- `delete_pebble` cascades `snaps` rows without touching `storage.objects`, and
-- client cleanup is best-effort only. NOTHING CALLS IT — no web, iOS, Android or
-- admin caller exists, and it is not exposed through PostgREST — so committing it
-- records work in progress rather than activating anything.
--
-- GRANTS ARE THE ONE THING HERE THAT MAY NOT BE A NO-OP. `pg_get_functiondef`
-- does not report ACLs and they were not captured, so the project's current grants
-- on this function are unknown. Postgres grants EXECUTE to PUBLIC by default on
-- CREATE FUNCTION, which for a `security definer` function that deletes storage
-- objects would mean any authenticated user could invoke it. The lockdown below is
-- therefore applied deliberately: it matches the house pattern for privileged
-- functions (20260730090000 for sync_achievement_catalog) and the standing rule
-- from #739 that a privileged operation needs the service role, not ownership.
-- If the project already looks like this, the statements are a no-op; if it does
-- not, they tighten it, which is the direction this repo has twice chosen before
-- (#442, #616).

create or replace function public.sweep_orphan_snap_files()
 returns table(deleted_count bigint, bytes_freed bigint)
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
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
$function$;

revoke all on function public.sweep_orphan_snap_files() from public, anon, authenticated;
grant execute on function public.sweep_orphan_snap_files() to service_role;
