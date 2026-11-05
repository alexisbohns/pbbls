-- Trigger functions are not RPCs (#1523).
--
-- Supabase's default privileges grant EXECUTE on every new function in
-- `public` to anon and authenticated, and nothing ever revoked it from the
-- SECURITY DEFINER trigger functions. Postgres refuses to run a trigger
-- function outside a trigger ("trigger functions can only be called as
-- triggers"), so this closes no live hole; it makes the catalog say what the
-- code means, which is what the RLS denial harness asserts
-- (scripts/verify-rls-denial.ts, registry kind `trigger`).
--
-- Firing a trigger does not check the session user's EXECUTE privilege (only
-- CREATE TRIGGER does), so every trigger keeps working.

revoke execute on function public.apply_karma_event_to_bounce()     from public, anon, authenticated;
revoke execute on function public.apply_karma_event_to_wallet()     from public, anon, authenticated;
revoke execute on function public.enforce_collection_glyph_usable() from public, anon, authenticated;
revoke execute on function public.enforce_glyph_system_flag()       from public, anon, authenticated;
revoke execute on function public.enforce_profile_glyph_usable()    from public, anon, authenticated;
revoke execute on function public.enforce_reserved_handle()         from public, anon, authenticated;
revoke execute on function public.enforce_soul_glyph_usable()       from public, anon, authenticated;
revoke execute on function public.handle_new_user()                 from public, anon, authenticated;
