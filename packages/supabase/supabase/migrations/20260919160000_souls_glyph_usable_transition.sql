-- =============================================================================
-- souls_glyph_usable guards the TRANSITION, not the state (#879)
-- =============================================================================
-- `enforce_soul_glyph_usable` (20260712000000) raises unconditionally, and its
-- trigger is `before insert or update of glyph_id`. `update of <col>` fires
-- whenever the column is NAMED in the SET list, not only when its value moves,
-- so any client that sends glyph_id on every soul edit re-validates the glyph
-- on every rename. Two of the three do:
--
--   web     apps/web/lib/data/supabase-provider.ts — spreads only the keys
--           present in UpdateSoulInput, so glyph_id is absent on a rename
--   iOS     SoulUpdatePayload has non-optional `name` AND `glyphId`
--   Android SoulsService.update(soulId, name, glyphId) — both always put
--
-- So on iOS and Android, a soul whose glyph stopped being usable could not be
-- renamed at all: every edit failed with `Glyph not usable by user: …`.
--
-- Worse than the profile case #875 fixed. `souls.glyph_id` is NOT NULL with a
-- system default (20260426000000), so there is no "no mark" state to fall back
-- to and no way for the user to clear it — unlike `profiles.glyph_id`, which is
-- nullable.
--
-- ---------------------------------------------------------------------------
-- How a standing soul glyph becomes unusable
-- ---------------------------------------------------------------------------
-- `admin_attribute_glyph` (20260701102810) moves a glyph to a new owner with a
-- bare `update public.glyphs set user_id = p_user_id` and detaches nothing, so
-- the previous owner keeps a soul wearing a glyph `can_use_glyph` now refuses.
-- The row was legitimate when it was written.
--
-- Not reachable through `purge_account`: `souls.glyph_id` is `on delete
-- restrict`, and the purge anonymizes a glyph another user's soul points at
-- (it is one arm of `v_kept`) rather than deleting it. But anonymized is
-- ownerless-and-not-system since #872, so a soul wearing a SOLD glyph relies on
-- the buyer's entitlement surviving — revoke or refund it and the same lockout
-- appears.
--
-- ---------------------------------------------------------------------------
-- The fix, and what it does not open
-- ---------------------------------------------------------------------------
-- Return early when an UPDATE names glyph_id without moving it — the same shape
-- `enforce_profile_glyph_usable` (20260919140000, #875) already carries, so the
-- two guards stay readable as one rule applied twice. This closes nothing that
-- is currently open: you still cannot newly ATTACH a glyph you cannot use, on
-- INSERT or on a real UPDATE. It only stops the guard from freezing rows that
-- were valid when they were written.
--
-- Probed 2026-09-19 against the linked project before writing this: 89 souls,
-- all 89 carry a glyph (the column is NOT NULL), 0 of them fail
-- `can_use_glyph`, and there are 0 ownerless non-system glyphs. Nothing to
-- remediate, so this migration backfills nothing.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run. `purge_account` is not
-- re-emitted, so there is no pairwise body diff to union. The trigger itself is
-- unchanged and deliberately NOT re-created — only the function body moves.
-- =============================================================================

-- Unchanged from 20260712000000 except the early return: still security
-- definer (can_use_glyph is itself definer and reads glyphs /
-- glyph_entitlements past RLS), still no `current_user` exemption. See the
-- header of 20260919140000 for why an exemption would be both wrong and
-- inoperative here: a definer body runs as its owner, so `current_user` is
-- `postgres` inside it and the exemption would swallow every call.
create or replace function public.enforce_soul_glyph_usable()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Naming the column is not changing it. See the header: iOS and Android send
  -- glyph_id on every soul update, so without this an unusable-but-legitimate
  -- mark would block every rename — permanently, since souls.glyph_id is NOT
  -- NULL and no client offers a way to clear it.
  if tg_op = 'UPDATE' and new.glyph_id is not distinct from old.glyph_id then
    return new;
  end if;

  if not public.can_use_glyph(new.glyph_id, new.user_id) then
    raise exception 'Glyph not usable by user: %', new.glyph_id using errcode = '42501';
  end if;

  return new;
end;
$$;
