-- Migration: Profiles is_admin flag
-- Adds a minimal admin capability on top of existing profiles.
-- Used by the Lab tab's logs table and lab-assets storage bucket
-- to gate content moderation (create/update/delete/unpublished reads).

-- ============================================================
-- COLUMN
-- ============================================================

alter table public.profiles
  add column is_admin boolean not null default false;

-- ============================================================
-- HELPER FUNCTION
-- ============================================================
-- Usable from RLS policies (anon + authenticated). Returns false for
-- unauthenticated callers and for users without a profile row.
-- security definer lets the function read profiles.is_admin without
-- bumping into profiles' user_id-scoped RLS.

create or replace function public.is_admin(p_user_id uuid)
returns boolean as $$
  select coalesce(
    (select p.is_admin from public.profiles p where p.user_id = p_user_id),
    false
  );
$$ language sql stable security definer set search_path = public;

grant execute on function public.is_admin(uuid) to anon, authenticated;
