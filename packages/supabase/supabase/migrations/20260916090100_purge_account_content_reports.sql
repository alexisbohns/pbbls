-- =============================================================================
-- purge_account: detach the reporter, delete the reports against them (#831)
-- =============================================================================
-- content_reports (20260916090000) holds two references to a user, and they
-- are erased DIFFERENTLY on purpose:
--
--   reporter_id    -> DETACHED (set null). The moderation trail must survive a
--                     reporter who leaves; a serial abuser's report history
--                     must not evaporate because one reporter deleted their
--                     account mid-review. Mirrors the glyph_submissions
--                     submitter detach in section (2).
--   target_user_id -> DELETED. The reported content goes with the account, so
--                     an open report about it is unresolvable noise. This also
--                     takes target_snapshot, which is the only place the
--                     departing user's text survives.
--
-- Ordering matters: the detach must precede the delete, or a self-report would
-- be deleted before it could be detached and the counts would mislead.
-- (report_content refuses self-reports today; the ordering is free.)
--
-- The body below is copied VERBATIM from
-- 20260911090100_purge_account_consents.sql with exactly one addition, at the
-- section-(4) `>>> APPEND ... HERE. <<<` marker. Re-emitting this function is
-- how appends get lost: `create or replace` has no merge semantics and git
-- reports no conflict, so anything not carried forward is silently dropped
-- (that is the accident 20260731090000 exists to repair). The check is the
-- pairwise diff of the two function bodies — it must show additions only.
--
-- Design: docs/superpowers/specs/2026-09-16-content-reports-design.md §7.
-- Grants are unchanged (service-role only, set in 20260729201326);
-- create or replace preserves them.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run.
-- =============================================================================

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

  -- Detach, don't delete (#831): the moderation trail outlives the reporter.
  -- reporter_id carries `on delete set null`, so deleteUser would eventually
  -- do this too — the explicit statement exists for this section's stated
  -- reason: it makes "all personal rows gone" true at RPC success, and keeps a
  -- re-run meaningful when deleteUser was the step that failed.
  update public.content_reports set reporter_id = null where reporter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('content_reports_detached', v_n);

  -- Reports AGAINST the departing user go: their content is gone with the
  -- account, so an open report about it is unresolvable. Must follow the
  -- detach above.
  delete from public.content_reports where target_user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('content_reports', v_n);

  -- The Art. 9 consent ledger (#775). Erasure wins over the Art. 7(1) duty to
  -- demonstrate consent: once there is no data subject, there is nothing left
  -- to demonstrate about. Controller decision, 2026-09-11, recorded as Q5 in
  -- docs/compliance/dpia.md.
  --
  -- Unlike its neighbours in this section, user_consents has NO on-delete
  -- cascade (it matches profiles, so the purge stays explicit). That makes this
  -- delete load-bearing rather than belt-and-braces: without it,
  -- auth.admin.deleteUser fails with 23503 and the account is left half-purged.
  delete from public.user_consents where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('user_consents', v_n);
  -- connections and connection_blocks are TWO-SIDED — deliberately NOT the
  -- usual user_id = p_user_id shape. Do not "simplify" them to a single
  -- column: that would silently halve the purge (D10). Deleting A removes the
  -- shared connection row (B just stops seeing it) and blocks in BOTH
  -- directions involving A (a block against a nonexistent user is
  -- meaningless).
  delete from public.connections where p_user_id in (user_a, user_b);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connections', v_n);

  delete from public.connection_invites where inviter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connection_invites', v_n);

  delete from public.connection_blocks where p_user_id in (blocker_id, blocked_id);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connection_blocks', v_n);

  delete from public.achievement_unlocks where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('achievement_unlocks', v_n);

  delete from public.pebble_drafts where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('pebble_drafts', v_n);

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
