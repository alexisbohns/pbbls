-- v_wallet_summary runs with invoker rights.
--
-- The Supabase advisor flagged it under lint 0010 (Security Definer View), and
-- this exact statement was first run on the live project from the dashboard
-- (#1711). This migration records it so a fresh database matches production;
-- on the project it is a no-op.
--
-- Safe for the caller: the view reads wallet_balances and karma_events, whose
-- select policies (wallet_balances_select_self, karma_events_select) both admit
-- `user_id = auth.uid()`, so a signed-in user still gets their own summary.
-- anon was already revoked when the view was created (20260629194621).

alter view public.v_wallet_summary set (security_invoker = true);
