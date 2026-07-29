-- =============================================================================
-- Account deletion (#631, M46) — purge_account(p_user_id) + submitter detach
-- =============================================================================
-- Server-side half of account deletion. The delete-account edge function
-- orchestrates: purge_account (this file) → storage prefix removal
-- (pebbles-media/{user_id}/) → auth.admin.deleteUser. Every statement below is
-- scoped to p_user_id and the function body runs in one transaction, so the
-- purge is idempotent by construction: a re-run after a failure in a LATER
-- step matches zero rows and returns zero counts (convergence).
--
-- Sold glyphs are ANONYMIZED, not deleted (decision log 2026-07-29):
-- user_id = null is the existing system-glyph state — can_use_glyph() and the
-- glyphs SELECT policy already treat it as usable-by-all, so buyers'
-- entitlements keep rendering. The predicate is deliberately "externally
-- referenced", wider than "sold": admin_attribute_glyph can hand a
-- formerly-system glyph to a user while other users' souls already reference
-- it, and souls.glyph_id is ON DELETE RESTRICT — a sold-only predicate would
-- hard-fail there and the purge would never converge. profiles.glyph_id is
-- only SET NULL, but is included so a counterparty's profile glyph doesn't
-- silently vanish.
--
-- glyph_submissions.submitter_id was NOT NULL; kept (anonymized) glyphs keep
-- their approved submission as the audit trail, so the column must accept the
-- detached state.
--
-- STANDING RULE (roadmap §M46): every later milestone appends its new
-- user-owned tables to the numbered sections below (drafts, unlocks,
-- connections, invites, blocks, seams, pairs, whispers, reports, …).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Kept submissions must outlive their author.
-- ---------------------------------------------------------------------------
alter table public.glyph_submissions
  alter column submitter_id drop not null;

-- ---------------------------------------------------------------------------
-- 2. The purge. Service-role only. Returns per-table counts for observability.
-- ---------------------------------------------------------------------------
create or replace function public.purge_account(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_kept   uuid[];
  v_counts jsonb := '{}'::jsonb;
  v_n      bigint;
begin
  if p_user_id is null then
    raise exception 'missing_user_id' using errcode = '22004';
  end if;

  -- -------------------------------------------------------------------------
  -- (1) Marketplace rows the user OWNS. Own entitlements first:
  -- glyph_entitlements.karma_event_id references the buyer's own purchase
  -- karma_event with no cascade, so these must precede the karma_events
  -- delete in (3). Counterparties' entitlements reference their OWN events
  -- (buy_glyph links the entitlement to the buyer's spend) and are untouched.
  -- -------------------------------------------------------------------------
  delete from public.glyph_entitlements where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_entitlements', v_n);

  delete from public.glyph_favourites where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_favourites', v_n);

  -- -------------------------------------------------------------------------
  -- (2) Glyphs: anonymize the externally-referenced ones, delete the rest.
  -- Externally referenced = another user's entitlement (sold) ∪ another
  -- user's soul (RESTRICT FK) ∪ another user's pebble (NO ACTION FK) ∪
  -- another user's profile glyph (SET NULL, kept for their UX) ∪ a domain
  -- default (NO ACTION FK).
  -- -------------------------------------------------------------------------
  select coalesce(array_agg(g.id), '{}') into v_kept
  from public.glyphs g
  where g.user_id = p_user_id
    and (
         exists (select 1 from public.glyph_entitlements e
                 where e.glyph_id = g.id and e.user_id <> p_user_id)
      or exists (select 1 from public.souls s
                 where s.glyph_id = g.id and s.user_id <> p_user_id)
      or exists (select 1 from public.pebbles pb
                 where pb.glyph_id = g.id and pb.user_id <> p_user_id)
      or exists (select 1 from public.profiles pr
                 where pr.glyph_id = g.id and pr.user_id <> p_user_id)
      or exists (select 1 from public.domains d
                 where d.default_glyph_id = g.id)
    );

  update public.glyphs set user_id = null where id = any(v_kept);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyphs_anonymized', v_n);

  -- Kept glyphs leave the market but keep the approved audit trail. Must run
  -- BEFORE the submitter detach below (this filters on submitter_id).
  update public.glyph_submissions
     set listed = false
   where glyph_id = any(v_kept) and submitter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_delisted', v_n);

  -- Detach the user from every surviving submission: as submitter (kept
  -- glyphs above, plus glyphs admin_attribute_glyph moved to a new owner —
  -- those stay listed for that owner) and as reviewer (admin self-deletion).
  update public.glyph_submissions set submitter_id = null
   where submitter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_detached', v_n);

  update public.glyph_submissions set reviewed_by = null
   where reviewed_by = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_unreviewed', v_n);

  -- -------------------------------------------------------------------------
  -- (3) Content, FK-ordered: pebbles before glyphs (pebbles.glyph_id NO
  -- ACTION), souls before glyphs (souls.glyph_id RESTRICT). Deleting pebbles
  -- cascades pebble_cards / pebble_domains / pebble_souls / snaps /
  -- collection_pebbles; the explicit snaps delete is belt-and-braces.
  -- -------------------------------------------------------------------------
  delete from public.pebbles where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('pebbles', v_n);

  delete from public.snaps where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('snaps', v_n);

  delete from public.souls where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('souls', v_n);

  delete from public.collections where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('collections', v_n);

  delete from public.profiles where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('profiles', v_n);

  -- Anonymized glyphs now have user_id = null, so delete-by-owner removes
  -- exactly the non-kept remainder. Cascades take their submissions and any
  -- other users' favourites on them. No entitlement can reference a deleted
  -- glyph: any glyph with a foreign entitlement is in v_kept by construction.
  delete from public.glyphs where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyphs_deleted', v_n);

  -- Ledger: nothing references these rows any more — own entitlements went in
  -- (1); other users' entitlements point at their own events. The wallet and
  -- bounce triggers are AFTER INSERT only (20260629193636, 20260501000004),
  -- so this fires nothing.
  delete from public.karma_events where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('karma_events', v_n);

  -- -------------------------------------------------------------------------
  -- (4) auth.users-CASCADE rows. deleteUser would reap these, but deleting
  -- them here makes "all personal rows gone" true at RPC success and keeps
  -- re-runs meaningful when deleteUser is the step that failed.
  -- >>> APPEND new per-user tables from later milestones HERE. <<<
  -- -------------------------------------------------------------------------
  delete from public.log_reactions where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('log_reactions', v_n);

  delete from public.wallet_balances where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('wallet_balances', v_n);

  delete from public.bounces where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('bounces', v_n);

  return jsonb_build_object(
    'user_id', p_user_id,
    'kept_glyphs', coalesce(array_length(v_kept, 1), 0),
    'counts', v_counts
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. Service-role only (pattern: 20260629194418_restrict_refund_karma_to_service_role.sql).
-- ---------------------------------------------------------------------------
revoke execute on function public.purge_account(uuid) from public, anon, authenticated;
grant  execute on function public.purge_account(uuid) to service_role;
