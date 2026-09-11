-- Migration: revoke the consent RPCs from public and anon
--
-- 20260911090000 granted execute to `authenticated` but did not revoke the
-- default first, unlike the house pattern in 20260629193838_wallet_rpcs.sql
-- and 20260630003348_glyph_marketplace.sql. Both functions already hard-fail
-- on a null auth.uid() with `not_authenticated`, so this closes no live hole —
-- it restores symmetry, and keeps the grant surface of a `security definer`
-- function explicit rather than inherited.

revoke all on function public.record_consent(text, text, text) from public, anon;
revoke all on function public.withdraw_consent(text)            from public, anon;

grant execute on function public.record_consent(text, text, text) to authenticated;
grant execute on function public.withdraw_consent(text)            to authenticated;
