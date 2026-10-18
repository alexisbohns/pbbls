-- =============================================================================
-- Drop the moments-split web compatibility layer (#1050, spec §5)
--
-- 20261012090400 recreated v_pebbles_full, path_pebbles, create_pebble and
-- update_pebble over the moments schema so the web app kept working until its
-- Capture / Complete flows shipped. They have (#1050 Parts 1–6). Nothing in
-- apps/, packages/ or a later migration references them any more. The
-- compose-pebble / compose-pebble-update edge functions that called the two
-- wrappers are deleted in the same change and undeployed right after this push.
--
-- Exactly the four objects 20261012090400 created, nothing else:
--   * view     public.v_pebbles_full
--   * function public.path_pebbles()
--   * function public.create_pebble(jsonb)
--   * function public.update_pebble(uuid, jsonb)
--
-- No `cascade`: an unexpected dependent must fail the push, not vanish.
-- Grants and comments go with the objects. The functions go first; no
-- function body binds the view (sql / plpgsql bodies are not tracked
-- dependencies), so the order is for the reader, not for Postgres.
--
-- Not dropped, deliberately: get_shared_pebble's explicit `render_svg: null`
-- key (a definer contract served to anon — a follow-up), delete_moment_media
-- (a split writer, not compat), private.jsonb_has / private.assert_jsonb_array.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run. Not a purge_account
-- or remove_connection re-emission.
-- =============================================================================

drop function public.create_pebble(jsonb);
drop function public.update_pebble(uuid, jsonb);
drop function public.path_pebbles();
drop view public.v_pebbles_full;
