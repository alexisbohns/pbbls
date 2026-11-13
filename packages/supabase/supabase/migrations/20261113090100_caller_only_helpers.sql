-- is_admin and can_use_glyph stop answering for other people (#1531).
--
-- Both are security definer and take a user id, and both were executable by
-- anon: anyone could test whether a uuid is an admin, and whether a user owns
-- or bought a given glyph.

-- ---------------------------------------------------------------------------
-- 1. is_admin answers for the caller only (base: 20260421000000).
-- ---------------------------------------------------------------------------
-- It stays executable by anon: logs_select and the other public-role policies
-- call it as the viewer, and a policy needs the viewer to hold EXECUTE. Every
-- caller in the catalog passes auth.uid() (policies, the admin RPCs, the
-- admin app's guard), so for them nothing changes. Any other id now reads
-- false, as does every id when nobody is signed in. Same signature, so every
-- policy and RPC that calls it keeps working untouched.
create or replace function public.is_admin(p_user_id uuid)
returns boolean as $$
  select p_user_id is not distinct from auth.uid()
     and coalesce(
       (select p.is_admin from public.profiles p where p.user_id = p_user_id),
       false
     );
$$ language sql stable security definer set search_path = public;

-- ---------------------------------------------------------------------------
-- 2. can_use_glyph leaves the API.
-- ---------------------------------------------------------------------------
-- Its only callers are security definer: complete_onboarding and the
-- enforce_{soul,profile,collection}_glyph_usable triggers. Those run as the
-- owner, who keeps EXECUTE, so the guard still runs for every write. No client
-- calls it and no policy uses it, so nobody needs it through PostgREST.
revoke execute on function public.can_use_glyph(uuid, uuid) from public, anon, authenticated;
