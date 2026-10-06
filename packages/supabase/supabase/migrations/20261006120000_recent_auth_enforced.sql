-- Migration: turn the recent sign-in check on (#977)
--
-- 20260927120000 shipped the check with enforcement OFF because web and iOS
-- did not re-authenticate yet. Every client now asks "Confirm it's you" before
-- account deletion, a password change and turning the public profile on, and
-- maps `reauth_required` back to that prompt:
--
--   Android  RecentAuth.kt + SettingsViewModel   (#976)
--   web      lib/auth/recent-auth.ts + settings  (#977)
--   iOS      RecentAuth.swift + SettingsSheet    (#977)
--
-- So the switch flips. From here, assert_recent_auth() refuses a session whose
-- newest `amr` stamp is older than 10 minutes: the delete-account edge function
-- answers 428, and the profiles trigger refuses public_profile false→true.
--
-- Do not apply this before the iOS release carrying the re-auth prompt is
-- live: older iOS builds show a generic error for both actions until updated.
--
-- Password change is gated by GoTrue, not here: `secure_password_change` in
-- config.toml and on the hosted project's Auth settings.
--
-- Only the switch is re-emitted. Its signature, volatility and grants are
-- unchanged, so `create or replace` keeps the existing EXECUTE grants.

create or replace function public.recent_auth_enforced()
returns boolean
language sql
stable
set search_path = public
as $$
  select true;
$$;
