-- =============================================================================
-- profiles.glyph_id gains the can_use_glyph guard (#875)
-- =============================================================================
-- `can_use_glyph` (20260712000000, re-emitted by 20260919090000) gates glyph
-- attachment on three surfaces. Two were guarded:
--
--   pebbles.glyph_id  -> inside create_pebble / update_pebble
--   souls.glyph_id    -> the souls_glyph_usable trigger
--   profiles.glyph_id -> NOTHING
--
-- `profiles` is written directly by every client (web PATCHes the row; iOS and
-- Android go through the `update_profile` RPC, which is security INVOKER), and
-- `profiles_update` only asks that the row be yours. So the profile mark — the
-- most visible glyph a user has, rendered on their public profile and in the
-- path bottom bar — accepted any glyph the user could READ. Under the
-- browse-friendly `glyphs_select` policy that is every approved community
-- glyph, bought or not: a user could wear artwork they never paid for.
--
-- Same shape as the souls trigger, with one deliberate difference.
--
-- ---------------------------------------------------------------------------
-- Why the guard only fires when the attachment CHANGES
-- ---------------------------------------------------------------------------
-- `before update of glyph_id` fires whenever the column is NAMED in the SET
-- list, not only when its value moves — and `update_profile` always names it:
--
--   glyph_id = case when p_glyph_id is not null then p_glyph_id else glyph_id end
--
-- so on iOS and Android a rename is also a glyph_id write. An unconditional
-- check would therefore make a profile whose glyph stopped being usable
-- unrenameable — permanently, because there is no UX to clear the mark and the
-- RPC cannot express it (20260516104231 says so in as many words: null means
-- "leave unchanged", not "remove").
--
-- A glyph going from usable to unusable under a standing profile is not
-- hypothetical. `admin_attribute_glyph` (20260701102810) moves a glyph to a new
-- owner with a bare `update glyphs set user_id = ...` and detaches nothing, so
-- the previous owner keeps wearing a mark `can_use_glyph` now refuses. That row
-- was legitimate when it was written; the person should not lose their display
-- name over an admin re-attribution.
--
-- Guarding the transition and not the state is the same trade the neighbouring
-- `profiles_privileged_guard` (20260902090000) makes: the trigger narrows on
-- the column, and the body raises only on an actual change, so an idempotent
-- write that re-sends an unchanged value still succeeds. It closes the attack
-- (you cannot newly attach a glyph you cannot use) without freezing rows that
-- were valid when they were written.
--
-- Probed 2026-09-19 against the linked project before writing this: 3 profiles
-- carry a glyph, 0 of them fail `can_use_glyph`, and there are 0 ownerless
-- non-system glyphs. Nothing to remediate, so this migration backfills nothing
-- and clears nobody's mark.
--
-- ---------------------------------------------------------------------------
-- purge_account is unaffected (#875 ordering constraint 1)
-- ---------------------------------------------------------------------------
-- The purge never writes a non-null glyph_id onto a profile:
--
--   * `update glyphs set user_id = null where id = any(v_kept)` touches glyphs,
--     not profiles — the other user's profile row is not written, so the
--     trigger does not fire and their mark survives the seller's departure.
--   * `delete from profiles where user_id = p_user_id` is a DELETE; this
--     trigger is INSERT/UPDATE only.
--   * `delete from glyphs where user_id = p_user_id` can only reach a profile
--     through `profiles.glyph_id`'s `on delete set null`, and a NULL glyph is
--     usable by everyone (`can_use_glyph` returns true for null) — so even the
--     path that cannot be reached today fails open, not closed.
--
-- `get_public_profile` is security definer and builds the glyph jsonb itself,
-- so public profile rendering is unaffected either way (constraint 2).
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run. `purge_account` is not
-- re-emitted, so there is no pairwise body diff to union.
-- =============================================================================

-- security definer to match enforce_soul_glyph_usable: `can_use_glyph` is
-- itself definer and reads glyphs / glyph_entitlements past RLS, so this
-- function needs no privilege of its own — the symmetry is the point, since the
-- two guards must stay readable as one rule applied twice.
--
-- Deliberately NO `current_user not in ('authenticated', 'anon')` exemption,
-- unlike its neighbour enforce_profile_privileged_columns (20260902090000).
-- That one exempts the sanctioned mutation seam because its four columns have
-- no client write path at all, so the only writer left to trust is an
-- out-of-band one. glyph_id is the opposite: the client write path IS the thing
-- being guarded, and an exemption would hand a silent bypass to every future
-- security definer RPC that touches the column. It also would not work as
-- written here — a definer function runs as its owner, so `current_user` is
-- `postgres` inside this body and the exemption would swallow every call. Any
-- future role check must flip this function to security invoker in the same
-- edit.
create or replace function public.enforce_profile_glyph_usable()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Naming the column is not changing it. See the header: `update_profile`
  -- always names glyph_id, so without this an unusable-but-legitimate mark
  -- would block every other profile edit.
  if tg_op = 'UPDATE' and new.glyph_id is not distinct from old.glyph_id then
    return new;
  end if;

  if not public.can_use_glyph(new.glyph_id, new.user_id) then
    raise exception 'Glyph not usable by user: %', new.glyph_id using errcode = '42501';
  end if;

  return new;
end;
$$;

-- `of glyph_id` keeps the trigger off the hot path: a display_name,
-- color_world or handle update never fires it. The web client PATCHes only the
-- keys it changes, so for it this is a glyph-write-only trigger outright.
drop trigger if exists profiles_glyph_usable on public.profiles;
create trigger profiles_glyph_usable
  before insert or update of glyph_id on public.profiles
  for each row execute function public.enforce_profile_glyph_usable();
