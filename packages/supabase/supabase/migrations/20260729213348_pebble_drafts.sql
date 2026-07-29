-- =============================================================================
-- Drafts (#639, M47) — pebble_drafts + purge_account extension
-- =============================================================================
-- Server-side quick capture. A draft holds a PARTIAL create_pebble payload as
-- jsonb; publishing calls the normal compose-pebble → create_pebble path once
-- and then deletes the row. Design: docs/superpowers/specs/
-- 2026-07-29-drafts-and-autosave-design.md (D1, D2, D8, D9).
--
-- Why a separate table and NOT a status column on pebbles (D1): pebbles has
-- five NOT NULL semantic columns (name, happened_at, intensity, positiveness,
-- emotion_id) that a draft may all lack; every view/analytics migration would
-- need a forever `status <> 'draft'` filter; create_pebble is the schema's only
-- pebble_created emitter and always emits, so drafts must never reach it (zero
-- karma by construction); and update_pebble is coalesce-based and cannot null a
-- scalar, whereas a wholesale jsonb replace can.
--
-- No RPC: owner-scoped single-table CRUD is the sanctioned direct-client case
-- (root AGENTS.md). Nothing here is multi-table and nothing emits karma.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The table. payload keys mirror the compose-pebble wire payload exactly
-- (name, description, happened_at, intensity, positiveness, visibility,
-- emotion_id, domain_ids, soul_ids, collection_ids, glyph_id, snaps) with
-- unset keys omitted. Intentionally unvalidated: a draft is partial by
-- definition, and create_pebble remains the single validation authority at
-- publish time.
-- ---------------------------------------------------------------------------
create table public.pebble_drafts (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  payload    jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- The drafts list is "my drafts, most recently saved first".
create index pebble_drafts_user_updated_idx
  on public.pebble_drafts (user_id, updated_at desc);

-- ---------------------------------------------------------------------------
-- 2. RLS. Pattern: glyph_favourites (20260630003348) — a single `for all`
-- policy with explicit `to authenticated` and BOTH using + with check.
-- Deliberately not the older four-policy core-table style, whose UPDATE policy
-- omits `with check` and would let a row be moved to another user's user_id.
-- ---------------------------------------------------------------------------
alter table public.pebble_drafts enable row level security;

create policy pebble_drafts_all on public.pebble_drafts for all
  to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- updated_at is trigger-maintained, not client-supplied: the list orders on it
-- and renders "saved 2h ago".
create trigger pebble_drafts_updated_at
  before update on public.pebble_drafts
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- 3. purge_account: first customer of the M46 standing rule. Body otherwise
-- verbatim from 20260729201326_account_deletion_purge.sql — the only change is
-- the pebble_drafts delete at the section-(4) append marker.
--
-- Section (4) is the right home: a draft holds its references as jsonb with no
-- FKs, so it can neither block nor be orphaned by sections (1)–(3). Its snaps
-- already live under pebbles-media/{user_id}/, which the delete-account edge
-- function sweeps unchanged.
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

-- Grants are unchanged (service-role only, set in 20260729201326); create or
-- replace preserves them.
