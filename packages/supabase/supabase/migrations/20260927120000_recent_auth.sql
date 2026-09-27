-- Migration: recent sign-in (step-up auth) for high-harm account actions (#976)
--
-- Account deletion and turning the public profile on used to need nothing but a
-- live session. A copied token or an unlocked phone could therefore destroy or
-- expose an account. This migration adds the server half of a "recent sign-in"
-- requirement:
--
--   recent_auth_ok(amr, max_age)  pure evaluator over the JWT `amr` claim
--   recent_auth_enforced()        the rollout switch — FALSE in this migration
--   assert_recent_auth()          raises `reauth_required` when enforced + stale
--   profiles_public_profile_recent_auth   trigger: public_profile false→true
--
-- Why `amr`: GoTrue stamps each real authentication (password, oauth, otp, …)
-- into the session's `amr` claim with an epoch-seconds timestamp, and a token
-- REFRESH keeps the original stamps. So "the newest amr timestamp is recent"
-- means "this person proved the credential recently", which a stolen refresh
-- token cannot fake. No table, no token plumbing, and every surface reads it the
-- same way.
--
-- Rollout: enforcement ships OFF because web and shipped iOS builds do not
-- re-authenticate yet; turning it on here would break account deletion and the
-- public flip for them. #977 re-emits recent_auth_enforced() as TRUE once every
-- client prompts first. A function rather than a settings row so the flip is a
-- reviewed migration and no client role can toggle it.
--
-- The delete-account edge function calls assert_recent_auth() through the
-- caller's forwarded JWT before it purges (index.ts). Password change stays
-- with GoTrue (`secure_password_change`, also #977).

-- ---------------------------------------------------------------------------
-- 1. The evaluator. Pure over its arguments (it reads now(), hence STABLE).
--
-- True when any element carries a numeric `timestamp` no older than p_max_age.
-- CASE, not AND, guards jsonb_array_elements and the numeric cast: Postgres
-- orders AND quals by cost, not as written, and both raise on the wrong type.
-- The comparison stays in epoch space (numeric), so an absurd stamp compares
-- false instead of raising `timestamp out of range`. `->` on a non-object
-- element yields NULL, so no object check is needed.
-- ---------------------------------------------------------------------------
create function public.recent_auth_ok(p_amr jsonb, p_max_age interval)
returns boolean
language sql
stable
set search_path = public
as $$
  select case
    when jsonb_typeof(p_amr) = 'array' then exists (
      select 1
      from jsonb_array_elements(p_amr) as e
      where case
        when jsonb_typeof(e -> 'timestamp') = 'number'
          then (e ->> 'timestamp')::numeric >= extract(epoch from now() - p_max_age)
        else false
      end
    )
    else false
  end;
$$;

-- ---------------------------------------------------------------------------
-- 2. The rollout switch. #977 re-emits this returning true. STABLE, not
-- IMMUTABLE: its whole point is to change, and nothing may index on it.
-- ---------------------------------------------------------------------------
create function public.recent_auth_enforced()
returns boolean
language sql
stable
set search_path = public
as $$
  select false;
$$;

-- ---------------------------------------------------------------------------
-- 3. The assertion. security INVOKER: auth.jwt() must be the caller's claims.
-- The message is exactly `reauth_required` — every client maps that one token.
-- The 10-minute window is duplicated on Android (RecentAuth.WINDOW); change
-- both together.
-- ---------------------------------------------------------------------------
create function public.assert_recent_auth()
returns void
language plpgsql
stable
set search_path = public
as $$
begin
  if public.recent_auth_enforced()
     and not public.recent_auth_ok(auth.jwt() -> 'amr', interval '10 minutes') then
    raise exception 'reauth_required'
      using hint = 'Sign in again (password or provider) and retry within 10 minutes.';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. The trigger. Same exemption as profiles_privileged_guard (20260902090000):
-- only PostgREST client roles are gated. Definer paths (consent withdrawal,
-- moderation) and the service role only ever turn the flag OFF anyway, and
-- the WHEN clause already skips everything but false→true.
-- ---------------------------------------------------------------------------
create function public.enforce_public_profile_recent_auth()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user in ('authenticated', 'anon') then
    perform public.assert_recent_auth();
  end if;
  return new;
end;
$$;

create trigger profiles_public_profile_recent_auth
  before update of public_profile on public.profiles
  for each row
  when (old.public_profile = false and new.public_profile = true)
  execute function public.enforce_public_profile_recent_auth();

-- ---------------------------------------------------------------------------
-- 5. Grants. recent_auth_ok is exposed so the harness can prove the evaluator
-- directly; it reads nothing but its arguments.
-- ---------------------------------------------------------------------------
revoke all on function public.recent_auth_ok(jsonb, interval) from public, anon;
revoke all on function public.recent_auth_enforced()          from public, anon;
revoke all on function public.assert_recent_auth()            from public, anon;
grant execute on function public.recent_auth_ok(jsonb, interval) to authenticated;
grant execute on function public.recent_auth_enforced()          to authenticated;
grant execute on function public.assert_recent_auth()            to authenticated;
