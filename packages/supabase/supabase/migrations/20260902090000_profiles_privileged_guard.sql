-- Migration: pin privileged profile columns against direct client writes
--
-- `profiles_update` is scoped to the row's owner (`user_id = auth.uid()`), which
-- is the right scope for the columns a user owns — display_name, color_world,
-- onboarding_completed, handle, public_profile. It is the wrong scope for the
-- columns that grant *capability* rather than describe the person:
--
--   is_admin              the sole input to is_admin(uuid), which gates every
--                         admin RPC, all analytics RPCs, lab-assets storage
--                         writes and unpublished-logs reads
--   max_media_per_pebble  the server-side media quota create_pebble /
--                         update_pebble enforce
--   terms_accepted_at     consent proof, kept for GDPR accountability
--   privacy_accepted_at   ditto
--
-- Owning a row is not authority to raise your own capability in it. None of the
-- four has a client write path today: is_admin and max_media_per_pebble are set
-- out of band, and the consent timestamps are written by handle_new_user() from
-- raw_user_meta_data at signup. So they are pinned to their OLD values whenever
-- the writer is a PostgREST client role.
--
-- Why a trigger and not column privileges: Supabase grants UPDATE on the whole
-- table to `authenticated`, and a table-level grant covers every column. Moving
-- to per-column grants would mean re-granting on every future ALTER TABLE — a
-- silent hole the first time someone forgets. A CHECK constraint cannot see OLD.
-- Precedent for the shape: profiles_handle_guard (20260730120000).

-- ---------------------------------------------------------------------------
-- 1. The guard.
--
-- security INVOKER on purpose (unlike enforce_reserved_handle, which must read
-- reserved_handles past RLS): this function reads nothing, so it needs no
-- elevation, and `current_user` must stay the role that actually issued the
-- statement for the exemption below to mean anything.
--
-- The exemption is the sanctioned mutation seam: `postgres` (migrations, the
-- dashboard), `service_role`, and any security definer function — which runs as
-- its owner — pass through. Granting an admin, raising a quota, or backfilling
-- consent therefore stays possible; it just cannot be done by the account that
-- benefits from it, holding nothing but the publishable anon key.
--
-- Adding a privileged column later means adding it here. A column that gates
-- access and is not in this list is writable by every authenticated user.
-- ---------------------------------------------------------------------------
create function public.enforce_profile_privileged_columns()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user not in ('authenticated', 'anon') then
    return new;
  end if;

  if new.is_admin is distinct from old.is_admin
     or new.max_media_per_pebble is distinct from old.max_media_per_pebble
     or new.terms_accepted_at is distinct from old.terms_accepted_at
     or new.privacy_accepted_at is distinct from old.privacy_accepted_at then
    raise exception 'profiles_privileged_column'
      using hint = 'is_admin, max_media_per_pebble and the consent timestamps are not client-writable.';
  end if;

  return new;
end;
$$;

-- `of <columns>` keeps the trigger off the hot path: a display_name or
-- color_world update never fires it. Naming a pinned column in the SET list is
-- what fires it, and the body then raises only on an actual change — so an
-- idempotent write that re-sends an unchanged value still succeeds.
create trigger profiles_privileged_guard
  before update of is_admin, max_media_per_pebble, terms_accepted_at, privacy_accepted_at
  on public.profiles
  for each row execute function public.enforce_profile_privileged_columns();

-- ---------------------------------------------------------------------------
-- 2. Make the update policy's check explicit.
--
-- Postgres implies WITH CHECK = USING when WITH CHECK is omitted, so the new
-- row was already required to satisfy user_id = auth.uid() and this changes no
-- behavior. It is written out so that a future edit to USING cannot silently
-- change what the *written* row must satisfy, and so the policy states its own
-- contract instead of leaving a reader to recall the default.
-- ---------------------------------------------------------------------------
drop policy "profiles_update" on public.profiles;

create policy "profiles_update" on public.profiles
  for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());
