-- Migration: replace sweep_orphan_snap_files with a read-only lister (#322)
--
-- WHY THE OLD FUNCTION GOES. public.sweep_orphan_snap_files() (recovered in
-- 20260912130000, granted to postgres in 20260912140000) deleted orphaned
-- pebbles-media objects with a plain `delete from storage.objects`. Supabase's
-- storage schema refuses that statement ("Direct deletion from storage tables is
-- not allowed. Use the Storage API instead."), so the function could not work
-- whoever called it: the production pg_cron job that ran it failed on all 149
-- nights from 2026-05-11 to 2026-10-06 and was unscheduled by hand on
-- 2026-10-06. Deleting the row would also have left the bytes behind, which is
-- the reason the storage schema blocks it.
--
-- It had a second, latent defect. Server drafts (20260729213348) upload their
-- photo bytes at pick time but keep the snaps only in
-- pebble_drafts.payload->'snaps' ({id, storage_path, sort_order}); no
-- public.snaps row exists until create_pebble publishes. A sweep keyed on
-- public.snaps alone would delete every open draft's photos, and with no age
-- guard it would also take an upload that was mid-compose when it ran.
--
-- WHAT REPLACES IT. The database only decides WHICH objects are orphans; the
-- sweep-orphan-snaps edge function deletes them through the Storage API
-- (storage.from('pebbles-media').remove()), which removes the bytes and the row
-- together. This function is read-only (`stable`, a single select).
--
-- An object is an orphan when ALL of these hold:
--   * it is in the pebbles-media bucket, at {user_id}/{snap_id}/{file}, with a
--     UUID second segment (anything else is not ours to judge);
--   * no public.snaps row has that id, and no public.snaps row's storage_path
--     is its folder or the object itself (belt and braces: today every client
--     sends the snap id that is also the folder name, but a snap whose id and
--     folder ever diverged must not lose its photo);
--   * no pebble_drafts.payload->'snaps' element names that id (compared
--     lowercased, as clients differ in UUID case) or that storage_path (the
--     folder, `{user_id}/{snap_id}`, or the object itself);
--   * it is older than p_min_age_seconds (default 24 hours) on
--     storage.objects.created_at, which covers in-flight composes and the
--     clients' own best-effort cleanups.
--
-- p_owner narrows the listing to one {user_id}/ prefix. It exists for the
-- verify-orphan-snap-sweep harness, which has to prove deletion on its own
-- throwaway account without sweeping anybody else's files, and for a
-- maintainer who wants to try a real sweep on one account first. The edge
-- function refuses a grace period under 24 hours unless p_owner is set.
--
-- Age is in whole seconds, not an interval: it travels through PostgREST as a
-- JSON number, and whole seconds are the narrowest precision every reader
-- accepts.
--
-- GRANTS. Same lockdown as the function it replaces (#739, #797): service_role
-- only. No postgres grant this time: nothing schedules this in the database —
-- the schedule is .github/workflows/orphan-snap-sweep.yml, calling the edge
-- function — and the owner keeps EXECUTE regardless.

drop function if exists public.sweep_orphan_snap_files();

create function public.list_orphan_snap_files(
  p_min_age_seconds integer default 86400,
  p_owner uuid default null
)
returns table(name text, size bigint, created_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  -- `materialized` so the UUID filter is applied before anything casts the
  -- segment: an inlined CTE would let the planner evaluate `::uuid` on a row
  -- the regex was meant to exclude.
  with candidates as materialized (
    select
      o.name,
      coalesce((o.metadata->>'size')::bigint, 0) as size,
      o.created_at,
      lower((storage.foldername(o.name))[2]) as snap_id
    from storage.objects o
    where o.bucket_id = 'pebbles-media'
      and array_length(storage.foldername(o.name), 1) >= 2
      and (storage.foldername(o.name))[2]
          ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      and o.created_at < now() - make_interval(secs => greatest(coalesce(p_min_age_seconds, 86400), 0))
      and (p_owner is null or (storage.foldername(o.name))[1] = p_owner::text)
  ),
  draft_snaps as (
    select
      lower(s.value->>'id') as snap_id,
      s.value->>'storage_path' as storage_path
    from public.pebble_drafts d
    cross join lateral jsonb_array_elements(
      case when jsonb_typeof(d.payload->'snaps') = 'array'
           then d.payload->'snaps'
           else '[]'::jsonb end
    ) as s(value)
    where jsonb_typeof(s.value) = 'object'
  )
  select c.name, c.size, c.created_at
  from candidates c
  where not exists (
      select 1 from public.snaps sn where sn.id = c.snap_id::uuid
    )
    and not exists (
      select 1 from public.snaps sn
      where sn.storage_path = c.name
         or starts_with(c.name, sn.storage_path || '/')
    )
    and not exists (
      select 1 from draft_snaps ds
      where ds.snap_id = c.snap_id
         or ds.storage_path = c.name
         or starts_with(c.name, ds.storage_path || '/')
    )
  order by c.created_at, c.name;
$$;

revoke all on function public.list_orphan_snap_files(integer, uuid) from public, anon, authenticated;
grant execute on function public.list_orphan_snap_files(integer, uuid) to service_role;
