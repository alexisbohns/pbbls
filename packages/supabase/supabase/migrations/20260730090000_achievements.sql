-- =============================================================================
-- Achievements (#664, M48) — catalog + unlocks + check_achievements() + purge
-- =============================================================================
-- One seeded reference catalog, one write-locked unlock ledger, one
-- client-callable idempotent evaluation RPC that also pays each badge's karma.
-- Design: docs/superpowers/specs/2026-07-29-achievements-design.md
-- (D1–D6, D9, D10).
--
-- The satellite columns (glyph_id, karma_reward, is_active, bilingual copy
-- overrides) land here on day one because the admin library manager (D12) and
-- the rewarding screen (D13) were designed before this issue was cut — only
-- their RPCs and client surfaces ship later.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The catalog. Reference-table template (20260411000000): RLS on, one
-- public-read policy, no client write path. Copy columns are nullable on
-- purpose (D1/D7): seeded rows carry null copy and clients compose titles from
-- family-keyed i18n, so a new emotion never needs new database copy; the
-- title_*/description_* pairs exist only for admin-created or admin-renamed
-- rows (D12), which no client can have shipped strings for.
-- ---------------------------------------------------------------------------
create table public.achievements (
  id             uuid primary key default gen_random_uuid(),
  slug           text unique not null,
  family         text not null check (family in (
    'pebble_count','emotion_first','domain_first','first_collection',
    'first_glyph','first_soul','glyph_count','glyph_sales')),
  threshold      integer,                                  -- null for the one-shot families
  emotion_id     uuid references public.emotions(id),      -- emotion_first only
  domain_id      uuid references public.domains(id),       -- domain_first only
  sort_order     integer not null,
  glyph_id       uuid references public.glyphs(id),        -- system-owned visual (D12)
  karma_reward   integer not null default 0 check (karma_reward >= 0),  -- D9
  is_active      boolean not null default true,            -- retirement, never delete (D12)
  title_en       text,
  title_fr       text,
  description_en text,
  description_fr text
);

alter table public.achievements enable row level security;

create policy "achievements_select" on public.achievements
  for select using (true);

-- ---------------------------------------------------------------------------
-- 2. Seed: the static families. Ids are deterministic per the
-- md5('<table>:<slug>')::uuid convention (20260411000006) so three
-- hand-written clients can hardcode nothing and cache everything. Runtime
-- admin-created rows (D12) will take gen_random_uuid() instead — nothing
-- client-side keys on achievement ids, so the two regimes coexist.
--
-- sort_order sits in per-family blocks with gaps (100s) so admin-inserted
-- tiers and the generated families slot in without renumbering.
-- ---------------------------------------------------------------------------
insert into public.achievements (id, slug, family, threshold, sort_order) values
  (md5('achievements:pebble-count-1')::uuid,    'pebble-count-1',    'pebble_count',    1, 100),
  (md5('achievements:pebble-count-10')::uuid,   'pebble-count-10',   'pebble_count',   10, 110),
  (md5('achievements:pebble-count-25')::uuid,   'pebble-count-25',   'pebble_count',   25, 120),
  (md5('achievements:pebble-count-50')::uuid,   'pebble-count-50',   'pebble_count',   50, 130),
  (md5('achievements:pebble-count-100')::uuid,  'pebble-count-100',  'pebble_count',  100, 140),
  (md5('achievements:pebble-count-250')::uuid,  'pebble-count-250',  'pebble_count',  250, 150),
  (md5('achievements:pebble-count-500')::uuid,  'pebble-count-500',  'pebble_count',  500, 160),
  (md5('achievements:pebble-count-1000')::uuid, 'pebble-count-1000', 'pebble_count', 1000, 170);

insert into public.achievements (id, slug, family, threshold, sort_order) values
  (md5('achievements:first-collection')::uuid, 'first-collection', 'first_collection', null, 400),
  (md5('achievements:first-glyph')::uuid,      'first-glyph',      'first_glyph',      null, 410),
  (md5('achievements:first-soul')::uuid,       'first-soul',       'first_soul',       null, 420);

insert into public.achievements (id, slug, family, threshold, sort_order) values
  (md5('achievements:glyph-count-10')::uuid,  'glyph-count-10',  'glyph_count',  10, 500),
  (md5('achievements:glyph-count-25')::uuid,  'glyph-count-25',  'glyph_count',  25, 510),
  (md5('achievements:glyph-count-50')::uuid,  'glyph-count-50',  'glyph_count',  50, 520),
  (md5('achievements:glyph-count-75')::uuid,  'glyph-count-75',  'glyph_count',  75, 530),
  (md5('achievements:glyph-count-100')::uuid, 'glyph-count-100', 'glyph_count', 100, 540);

insert into public.achievements (id, slug, family, threshold, sort_order) values
  (md5('achievements:glyph-sales-1')::uuid,  'glyph-sales-1',  'glyph_sales',  1, 600),
  (md5('achievements:glyph-sales-5')::uuid,  'glyph-sales-5',  'glyph_sales',  5, 610),
  (md5('achievements:glyph-sales-10')::uuid, 'glyph-sales-10', 'glyph_sales', 10, 620),
  (md5('achievements:glyph-sales-25')::uuid, 'glyph-sales-25', 'glyph_sales', 25, 630),
  (md5('achievements:glyph-sales-50')::uuid, 'glyph-sales-50', 'glyph_sales', 50, 640);

-- ---------------------------------------------------------------------------
-- 3. The drift guard (D2). Emotions and domains are only ever added by
-- migration (the admin surface is edit-only), so the per-emotion and
-- per-domain rows are a *projection* of the reference tables, regenerated by
-- one idempotent insert…select over the LIVE rows.
--
-- STANDING RULE: any future migration — or future admin add-RPC — that
-- inserts an emotion or a domain re-runs sync_achievement_catalog() in the
-- same transaction. Service-role only; there is no client reason to call it.
-- ---------------------------------------------------------------------------
create or replace function public.sync_achievement_catalog()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Generated rows share a per-family sort_order base; within a family the
  -- clients order by the localized display name, so no ordinal is stored.
  insert into public.achievements (id, slug, family, emotion_id, domain_id, sort_order)
  select md5('achievements:' || s.slug)::uuid,
         s.slug, s.family, s.emotion_id, s.domain_id, s.sort_order
  from (
    select 'emotion-first-' || e.slug as slug,
           'emotion_first'            as family,
           e.id                       as emotion_id,
           null::uuid                 as domain_id,
           200                        as sort_order
    from public.emotions e
    union all
    select 'domain-first-' || d.slug,
           'domain_first',
           null::uuid,
           d.id,
           300
    from public.domains d
  ) s
  on conflict (slug) do nothing;
end;
$$;

-- Generate the emotion_first / domain_first rows for everything seeded so far.
select public.sync_achievement_catalog();

-- ---------------------------------------------------------------------------
-- 4. The unlock ledger (D3). Structurally idempotent: the PK is the
-- exactly-once guarantee for both the unlock and its karma. Write-locked on
-- the wallet_balances precedent (20260629193636): owner-only SELECT and NO
-- insert/update/delete policies — the only writer is security definer code,
-- so a client structurally cannot mint, edit or revoke a badge. Badges are
-- permanent: deletions and count regressions never remove an unlock, and
-- there is deliberately no revocation path to forget about.
-- ---------------------------------------------------------------------------
create table public.achievement_unlocks (
  user_id        uuid not null references auth.users(id) on delete cascade,
  achievement_id uuid not null references public.achievements(id),
  unlocked_at    timestamptz not null default now(),
  primary key (user_id, achievement_id)
);

alter table public.achievement_unlocks enable row level security;

create policy achievement_unlocks_select_self on public.achievement_unlocks
  for select to authenticated using (user_id = auth.uid());
-- No INSERT/UPDATE/DELETE policies: written exclusively by check_achievements().

-- ---------------------------------------------------------------------------
-- 5. Evaluation (D4, D6, D9). One client-callable idempotent definer RPC:
-- a stats CTE computes each family's count once, a case over a.family decides
-- qualification, and a data-modifying CTE chain inserts the new unlocks and
-- their karma in the same statement. Exactly-once mechanics: `on conflict do
-- nothing` against the unlocks PK means a re-run inserts no unlock row, so
-- the karma CTE (which feeds off the RETURNING set) emits nothing either —
-- idempotency needs no bookkeeping beyond the PK.
--
-- Karma (D9): reason 'grant' (reserved since 20260629192621, emitted nowhere
-- else), type 'credit', delta = karma_reward read AT UNLOCK TIME (admin edits
-- never retro-pay), ref_id = the achievement id. The existing wallet/bounce
-- AFTER INSERT triggers fold the grant automatically. delta is smallint, so
-- rewards cap at 32767 — same ledger decision as every other reason.
--
-- The return value reports what was actually emitted, not the catalog's
-- current value, so the rewarding screen can never display a number the
-- ledger doesn't hold. Empty set = nothing new.
-- ---------------------------------------------------------------------------
create or replace function public.check_achievements()
returns table (slug text, karma_granted integer)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  return query
  with stats as (
    -- Counts may regress (deletions, a buyer's purge); permanence + the PK
    -- make that harmless. Drafts never count: pebble_drafts is a separate
    -- table, so exclusion is structural, not filtered.
    select
      (select count(*) from public.pebbles p
        where p.user_id = v_uid)                       as pebble_count,
      -- Ownership IS the custom flag: is_custom is generated as
      -- user_id is not null, so this counts carved glyphs only.
      (select count(*) from public.glyphs g
        where g.user_id = v_uid)                       as glyph_count,
      -- Sales come from entitlements, not the karma ledger: an entitlement
      -- held by another user is the sale fact and survives price changes.
      -- The <> guard is belt-and-braces (buy_glyph rejects self-purchase).
      (select count(*)
         from public.glyph_entitlements e
         join public.glyphs g on g.id = e.glyph_id
        where g.user_id = v_uid
          and e.user_id <> g.user_id)                  as glyph_sales,
      exists (select 1 from public.collections c
               where c.user_id = v_uid)                as has_collection,
      exists (select 1 from public.souls s
               where s.user_id = v_uid)                as has_soul
  ),
  qualified as (
    select a.id, a.slug, a.karma_reward
    from public.achievements a, stats st
    where a.is_active
      and case a.family
        when 'pebble_count'     then st.pebble_count >= a.threshold
        when 'emotion_first'    then exists (
          select 1 from public.pebbles p
           where p.user_id = v_uid and p.emotion_id = a.emotion_id)
        when 'domain_first'     then exists (
          select 1 from public.pebble_domains pd
            join public.pebbles p on p.id = pd.pebble_id
           where p.user_id = v_uid and pd.domain_id = a.domain_id)
        when 'first_collection' then st.has_collection
        when 'first_glyph'      then st.glyph_count >= 1
        when 'first_soul'       then st.has_soul
        when 'glyph_count'      then st.glyph_count >= a.threshold
        when 'glyph_sales'      then st.glyph_sales >= a.threshold
        else false
      end
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id)
    select v_uid, q.id from qualified q
    on conflict (user_id, achievement_id) do nothing
    returning achievement_id
  ),
  paid as (
    insert into public.karma_events (user_id, delta, reason, type, ref_id)
    select v_uid, q.karma_reward, 'grant', 'credit', q.id
    from unlocked u
    join qualified q on q.id = u.achievement_id
    where q.karma_reward > 0
    returning ref_id, delta
  )
  select q.slug, coalesce(p.delta::integer, 0)
  from unlocked u
  join qualified q on q.id = u.achievement_id
  left join paid p on p.ref_id = u.achievement_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. purge_account (D10): M46 standing rule, second customer. Body otherwise
-- verbatim from 20260729213348_pebble_drafts.sql — the only change is the
-- achievement_unlocks delete at the section-(4) append marker.
--
-- Section (4) is the right home: user_id cascades from auth.users (the
-- deleteUser backstop) and nothing else references the rows. The catalog
-- itself is global reference data — untouched by purge, exactly like
-- emotions. Badge glyphs need no kept-predicate extension: they are always
-- system-owned (user_id = null), which the purge never matches.
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

-- Grants are unchanged (service-role only, set in 20260729201326); create or
-- replace preserves them.

-- ---------------------------------------------------------------------------
-- 7. Grants. check_achievements reads only the caller's rows (auth.uid()), so
-- there is nothing to leak even as definer; the generator has no client
-- reason to exist and stays service-role only.
-- ---------------------------------------------------------------------------
revoke all on function public.sync_achievement_catalog()  from public, anon, authenticated;
grant execute on function public.sync_achievement_catalog()  to service_role;

revoke all on function public.check_achievements()  from public, anon;
grant execute on function public.check_achievements()  to authenticated;
