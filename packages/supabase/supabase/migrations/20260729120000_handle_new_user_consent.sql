-- Migration: persist consent timestamps at signup
--
-- All three clients send `terms_accepted_at` / `privacy_accepted_at` in the
-- signup metadata and `profiles` has the columns, but handle_new_user() only
-- ever inserted user_id + display_name — the consent proof was collected and
-- thrown away (GDPR accountability gap, issue #617).

-- ============================================================
-- 1. handle_new_user — copy consent timestamps into the profile
-- ============================================================
-- `->>` yields NULL when the key is absent (OAuth signups carry no consent
-- metadata) or when the client sent JSON null, so the casts are NULL-safe.

create or replace function public.handle_new_user()
returns trigger as $$
begin
  insert into public.profiles (
    user_id,
    display_name,
    terms_accepted_at,
    privacy_accepted_at
  )
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', 'Pebbler'),
    (new.raw_user_meta_data->>'terms_accepted_at')::timestamptz,
    (new.raw_user_meta_data->>'privacy_accepted_at')::timestamptz
  );
  return new;
end;
$$ language plpgsql security definer set search_path = public;

-- ============================================================
-- 2. One-shot backfill from auth.users.raw_user_meta_data
-- ============================================================
-- Signup metadata survives in auth.users, so profiles created before the fix
-- can recover their consent proof. Only fills NULLs — never overwrites a
-- timestamp already on the profile, and no-ops for rows without metadata.

update public.profiles p
set
  terms_accepted_at =
    coalesce(p.terms_accepted_at, (u.raw_user_meta_data->>'terms_accepted_at')::timestamptz),
  privacy_accepted_at =
    coalesce(p.privacy_accepted_at, (u.raw_user_meta_data->>'privacy_accepted_at')::timestamptz)
from auth.users u
where u.id = p.user_id
  and (p.terms_accepted_at is null or p.privacy_accepted_at is null)
  and (
    u.raw_user_meta_data ? 'terms_accepted_at'
    or u.raw_user_meta_data ? 'privacy_accepted_at'
  );
