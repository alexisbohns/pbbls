-- =============================================================================
-- Moments and pebbles split — moderation, reports, purge, analytics, orphans
-- (spec §4 "Moderation and reports", "Purge")
--
-- NOT safe to push alone: 20261012090000–20261012090400 ship together.
--
-- Every function below is re-emitted from its NEWEST emission (named in each
-- header) with only the edits the header lists; the check is the pairwise diff
-- against that base. `create or replace` preserves each function's grants.
-- The two analytics views were dropped by 20261012090000 (they bound the
-- dropped pebble columns) and come back here with the base's revoke policy.
-- resolve_content_report dispatches on content_reports.target_kind, whose
-- 'pebble' rows 20261012090000 rewrote to 'moment', so it is not re-emitted.
-- check_achievements keeps counting pebbles rows and is not re-emitted.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. admin_set_content_hidden. Base 20260916100000 §5. 'moment' is the kind;
-- 'pebble' stays accepted as an alias (same id space: every pre-split pebble
-- id is its moment id) until the admin app (Part 5) sends 'moment'. The
-- response always names the canonical kind.
-- ---------------------------------------------------------------------------
create or replace function public.admin_set_content_hidden(
  p_target_kind text,
  p_target_id   uuid,
  p_hidden      boolean,
  p_note        text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin uuid := auth.uid();
  v_when  timestamptz := case when p_hidden then now() else null end;
  v_who   uuid := case when p_hidden then v_admin else null end;
  v_rows  integer;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  if p_target_kind in ('moment', 'pebble') then
    update public.moments set hidden_at = v_when, hidden_by = v_who
     where id = p_target_id;
  elsif p_target_kind = 'profile' then
    update public.profiles set hidden_at = v_when, hidden_by = v_who
     where user_id = p_target_id;
  else
    -- Glyphs keep glyph_submissions.listed: it is already a reversible flag in
    -- the existing moderation vocabulary (design D3).
    raise exception 'invalid_kind';
  end if;

  get diagnostics v_rows = row_count;
  if v_rows = 0 then raise exception 'not_found'; end if;

  return jsonb_build_object(
    'target_kind', case when p_target_kind = 'pebble' then 'moment' else p_target_kind end,
    'target_id', p_target_id,
    'hidden', p_hidden,
    'note', nullif(btrim(p_note), '')
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. report_content. Base 20260916090000 §3. A 'moment' target; 'pebble' plus
-- a pebble id is normalised to that pebble's moment for callers not yet
-- migrated. The gate is "the reporter may read a pebble on that moment",
-- mirroring pebbles_select minus the owner arm. Only the kind check and the
-- first gate branch change; the insert and lookup read the normalised kind
-- and id.
-- ---------------------------------------------------------------------------
create or replace function public.report_content(
  p_target_kind text,
  p_target_id   uuid,
  p_reason      text,
  p_detail      text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user     uuid := auth.uid();
  v_kind     text := p_target_kind;
  v_target   uuid := p_target_id;
  v_owner    uuid;
  v_snapshot jsonb;
  v_row      public.content_reports;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  if v_kind not in ('moment', 'pebble', 'profile', 'glyph') then
    raise exception 'invalid_kind';
  end if;

  if p_reason not in ('sexual', 'violence', 'hate', 'harassment',
                      'self_harm', 'illegal', 'spam', 'impersonation', 'other') then
    raise exception 'invalid_reason';
  end if;

  -- The alias: a pebble id becomes its moment id. An unknown pebble leaves
  -- v_target null, which falls through to the single not_found below.
  if v_kind = 'pebble' then
    v_kind := 'moment';
    select p.moment_id into v_target from public.pebbles p where p.id = p_target_id;
  end if;

  -- The visibility gate, mirroring each kind's live read rule. A miss leaves
  -- v_owner null and falls through to the single not_found below.
  if v_kind = 'moment' then
    -- Mirrors pebbles_select minus the owner arm: owners are caught by
    -- cannot_report_own, not by the gate. The connections pair is canonical
    -- (user_a < user_b), so least/greatest lands on the unique (user_a,
    -- user_b) index.
    select m.user_id,
           jsonb_build_object('name', m.name, 'description', m.description)
      into v_owner, v_snapshot
      from public.moments m
     where m.id = v_target
       and exists (
         select 1
           from public.pebbles p
          where p.moment_id = m.id
            and (
                 p.visibility = 'public'
              or (p.visibility = 'private' and exists (
                    select 1
                      from public.connections c
                     where c.user_a = least(v_user, p.user_id)
                       and c.user_b = greatest(v_user, p.user_id)))
            )
       );

  elsif v_kind = 'profile' then
    -- target_id is the profile's user_id: the public profile IS the user.
    select pr.user_id,
           jsonb_build_object('handle', pr.handle, 'display_name', pr.display_name)
      into v_owner, v_snapshot
      from public.profiles pr
     where pr.user_id = v_target
       and pr.public_profile = true;

  else
    select g.user_id,
           jsonb_build_object('name', g.name)
      into v_owner, v_snapshot
      from public.glyphs g
     where g.id = v_target
       and exists (
             select 1
               from public.glyph_submissions s
              where s.glyph_id = g.id
                and s.status = 'approved'
                and s.listed = true);
  end if;

  -- One slug for three cases: no such row, a row the reporter cannot see, and
  -- an OWNERLESS glyph. The third is why this tests v_owner rather than FOUND:
  -- purge_account anonymises a sold glyph to user_id = null, and its delisting
  -- pass only covers submissions the departing user submitted — so a glyph
  -- re-attributed by admin_attribute_glyph can survive as listed with a null
  -- owner. Selecting it yields v_owner = null, which lands here instead of
  -- violating target_user_id's not-null on insert.
  --
  -- Distinguishing the three would make this an existence oracle — feed it
  -- uuids, learn which secret pebbles exist. Same choice get_shared_pebble
  -- makes by returning null for both (20260817130000 §4).
  if v_owner is null then
    raise exception 'not_found';
  end if;

  if v_owner = v_user then
    raise exception 'cannot_report_own';
  end if;

  -- Idempotent on content_reports_open_unique. A second file is a success from
  -- the user's point of view — they reported it — so it returns the standing
  -- report rather than an error slug every client would have to special-case.
  insert into public.content_reports
    (reporter_id, target_kind, target_id, target_user_id, reason, detail, target_snapshot)
  values
    (v_user, v_kind, v_target, v_owner, p_reason,
     nullif(btrim(p_detail), ''), v_snapshot)
  on conflict (reporter_id, target_kind, target_id) where status = 'open'
  do nothing
  returning * into v_row;

  if v_row.id is null then
    select * into v_row
      from public.content_reports
     where reporter_id = v_user
       and target_kind = v_kind
       and target_id   = v_target
       and status      = 'open';
  end if;

  -- Projected keys only. target_user_id is deliberately excluded (standing
  -- rule: no cross-user identifier ever leaves an RPC), and so is the
  -- snapshot. created_at is whole-second UTC (standing timestamp rule).
  return jsonb_build_object(
    'id', v_row.id,
    'status', v_row.status,
    'created_at', to_char(v_row.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. admin_list_content_reports. Base 20260916090000 §4; only the two
-- target_kind branches change ('pebble' -> 'moment'). The live projection
-- carries the moderation flag and the widest visibility of any pebble on the
-- moment, since the moment itself has no visibility.
-- ---------------------------------------------------------------------------
create or replace function public.admin_list_content_reports(p_status text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_result jsonb;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  -- created_at is emitted as fixed-width ISO-8601 UTC, so ordering on the text
  -- is chronological.
  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at), '[]'::jsonb)
  into v_result
  from (
    select
      r.id              as id,
      r.reporter_id     as reporter_id,
      r.target_kind     as target_kind,
      r.target_id       as target_id,
      r.target_user_id  as target_user_id,
      r.reason          as reason,
      r.detail          as detail,
      r.target_snapshot as target_snapshot,
      r.status          as status,
      r.resolution_note as resolution_note,
      r.resolved_at     as resolved_at,
      r.resolved_by     as resolved_by,
      to_char(r.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as created_at,
      -- The orphan flag: the owner deleted the reported content themselves,
      -- which is self-remediation. The snapshot still says what was reported.
      (case r.target_kind
         when 'moment'  then not exists (select 1 from public.moments  m  where m.id      = r.target_id)
         when 'profile' then not exists (select 1 from public.profiles pr where pr.user_id = r.target_id)
         else                not exists (select 1 from public.glyphs   g  where g.id      = r.target_id)
       end) as target_missing,
      -- The signal that separates one bad moment from a bad account.
      (select count(*)
         from public.content_reports o
        where o.target_kind = r.target_kind
          and o.target_id   = r.target_id
          and o.status      = 'open') as open_reports_against_target,
      -- Live content beside the snapshot: the two disagreeing IS the evidence
      -- that the owner edited after being reported.
      (case r.target_kind
         when 'moment' then (select jsonb_build_object(
                               'name', m.name, 'description', m.description,
                               'hidden', m.hidden_at is not null,
                               'visibility', (select max(p.visibility) from public.pebbles p
                                               where p.moment_id = m.id))
                               from public.moments m where m.id = r.target_id)
         when 'profile' then (select jsonb_build_object(
                               'handle', pr.handle, 'display_name', pr.display_name,
                               'public_profile', pr.public_profile)
                               from public.profiles pr where pr.user_id = r.target_id)
         else (select jsonb_build_object(
                 'name', g.name,
                 'listed', exists (select 1 from public.glyph_submissions s
                                    where s.glyph_id = g.id and s.listed))
                 from public.glyphs g where g.id = r.target_id)
       end) as target_live
    from public.content_reports r
    where p_status is null or r.status = p_status
  ) t;

  return v_result;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. purge_account. Body copied from 20260916090100 with exactly three edits,
-- checked by pairwise diff:
--   (2) the kept-glyph predicate reads collections.glyph_id instead of the
--       dropped pebbles.glyph_id;
--   (3) moments are deleted first (cascading the user's own pebbles and
--       snaps), then any pebble of the user on another user's moment; the
--       counts gain 'moments';
--   (4) the pebble_drafts delete is gone with the table (20261012090000).
-- Re-emitting this function is how appends get lost: `create or replace` has
-- no merge semantics and git reports no conflict, so anything not carried
-- forward is silently dropped (that is the accident 20260731090000 exists to
-- repair). Grants are unchanged (service-role only, set in 20260729201326);
-- create or replace preserves them. The harness (verify-account-purge.ts)
-- gains the moments seed and zero-row assertion in the same part.
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
  -- user's soul (RESTRICT FK) ∪ another user's collection (NO ACTION FK;
  -- a pebble's glyph is derived from its collections since 20261012090000)
  -- ∪ another user's profile glyph (SET NULL, kept for their UX) ∪ a domain
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
      or exists (select 1 from public.collections c
                 where c.glyph_id = g.id and c.user_id <> p_user_id)
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
  -- (3) Content, FK-ordered. Moments first: deleting a moment cascades its
  -- pebbles (pebbles.moment_id CASCADE) and snaps (snaps.moment_id CASCADE),
  -- and each pebble cascades pebble_cards / pebble_domains / pebble_souls /
  -- collection_pebbles. The pebbles delete then takes this user's pebbles on
  -- OTHER users' moments (participants, later); the snaps delete is
  -- belt-and-braces. Souls before glyphs (souls.glyph_id RESTRICT);
  -- collections before glyphs (collections.glyph_id NO ACTION).
  -- -------------------------------------------------------------------------
  delete from public.moments where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('moments', v_n);

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
-- 5. The analytics views, dropped in 20261012090000. Bodies from
-- 20260919120000 (= 20260501000006) with three substitutions: snaps hang off
-- the moment, the description lives on the moment, and "custom glyph" now
-- means the pebble inherits a user-carved glyph through one of its
-- collections (the derived glyph, spec §4 D5). Column lists are unchanged.
-- ---------------------------------------------------------------------------
create view public.v_analytics_pebble_volume_daily as
with days as (
  select generate_series(
    coalesce((select min(created_at)::date from public.pebbles), current_date),
    current_date,
    interval '1 day'
  )::date as bucket_date
),
day_pebbles as (
  select
    p.created_at::date                                  as bucket_date,
    count(*)::int                                       as pebbles,
    count(*) filter (
      where exists (
        select 1 from public.snaps s where s.moment_id = p.moment_id
      )
    )::int                                              as pebbles_with_picture,
    count(*) filter (
      where exists (
        select 1 from public.collection_pebbles cp where cp.pebble_id = p.id
      )
    )::int                                              as pebbles_in_collection,
    count(*) filter (
      where exists (
        select 1 from public.collection_pebbles cp
        join public.collections c on c.id = cp.collection_id
        join public.glyphs g on g.id = c.glyph_id
        where cp.pebble_id = p.id and g.is_custom
      )
    )::int                                              as pebbles_with_custom_glyph,
    count(distinct p.user_id)::int                      as active_users
  from public.pebbles p
  group by p.created_at::date
)
select
  d.bucket_date,
  coalesce(dp.pebbles, 0)                   as pebbles,
  coalesce(dp.pebbles_with_picture, 0)      as pebbles_with_picture,
  coalesce(dp.pebbles_in_collection, 0)     as pebbles_in_collection,
  coalesce(dp.pebbles_with_custom_glyph, 0) as pebbles_with_custom_glyph,
  coalesce(dp.active_users, 0)              as active_users
from days d
left join day_pebbles dp using (bucket_date);

create view public.v_analytics_pebble_enrichment_daily as
with base as (
  select
    p.created_at::date                                            as bucket_date,
    p.id,
    m.description,
    p.intensity,
    exists (select 1 from public.snaps s
              where s.moment_id = p.moment_id)                    as has_picture,
    exists (select 1 from public.collection_pebbles cp
              where cp.pebble_id = p.id)                          as in_collection,
    exists (select 1 from public.pebble_souls ps
              where ps.pebble_id = p.id)                          as has_soul,
    exists (select 1 from public.collection_pebbles cp
              join public.collections c on c.id = cp.collection_id
              join public.glyphs g on g.id = c.glyph_id
              where cp.pebble_id = p.id and g.is_custom)          as has_custom_glyph
  from public.pebbles p
  join public.moments m on m.id = p.moment_id
)
select
  bucket_date,
  count(*)::int                                                                  as total_pebbles,
  round(100.0 * count(*) filter (where has_picture)
                / nullif(count(*), 0), 1)                                        as pct_with_picture,
  round(100.0 * count(*) filter (where in_collection)
                / nullif(count(*), 0), 1)                                        as pct_in_collection,
  round(100.0 * count(*) filter (where has_custom_glyph)
                / nullif(count(*), 0), 1)                                        as pct_with_custom_glyph,
  round(100.0 * count(*) filter (where description is not null
                                   and length(description) > 0)
                / nullif(count(*), 0), 1)                                        as pct_with_thought,
  round(100.0 * count(*) filter (where has_soul)
                / nullif(count(*), 0), 1)                                        as pct_with_soul,
  round(100.0 * count(*) filter (where intensity is not null)
                / nullif(count(*), 0), 1)                                        as pct_with_intensity
from base
group by bucket_date;

-- A recreated view is a new object and carries no grants from the old one.
-- Same policy as 20260501000001 / 20260501000006 / 20260919120000: the
-- SECURITY DEFINER RPCs below are the access path, so direct reads are revoked
-- from every API role and nothing is granted.
revoke all on public.v_analytics_pebble_volume_daily     from public, anon, authenticated;
revoke all on public.v_analytics_pebble_enrichment_daily from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 6. get_pebble_volume_series / get_pebble_enrichment. Bodies from
-- 20260501000006 §3 (their newest emission; 20260919120000 deliberately left
-- them alone) with the same three substitutions as the views. Return types
-- are unchanged, so create or replace applies and keeps the authenticated
-- grant; is_admin gates inside.
-- ---------------------------------------------------------------------------
create or replace function public.get_pebble_volume_series(
  p_start  date,
  p_end    date,
  p_bucket text default 'day'
)
returns table (
  bucket_date               date,
  pebbles                   bigint,
  pebbles_with_picture      bigint,
  pebbles_in_collection     bigint,
  pebbles_with_custom_glyph bigint,
  active_users              bigint
)
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  if p_bucket not in ('day', 'week', 'month', 'year') then
    raise exception 'invalid p_bucket: %, expected day|week|month|year', p_bucket
      using errcode = '22023';
  end if;

  return query
  with buckets as (
    select date_trunc(p_bucket, gs)::date as bucket_date
    from generate_series(
      date_trunc(p_bucket, p_start::timestamptz),
      date_trunc(p_bucket, p_end::timestamptz),
      ('1 ' || p_bucket)::interval
    ) as gs
  ),
  data as (
    select
      date_trunc(p_bucket, p.created_at)::date as bucket_date,
      count(*)                                                                       as pebbles,
      count(*) filter (
        where exists (select 1 from public.snaps s where s.moment_id = p.moment_id)
      )                                                                              as pebbles_with_picture,
      count(*) filter (
        where exists (
          select 1 from public.collection_pebbles cp where cp.pebble_id = p.id
        )
      )                                                                              as pebbles_in_collection,
      count(*) filter (
        where exists (
          select 1 from public.collection_pebbles cp
          join public.collections c on c.id = cp.collection_id
          join public.glyphs g on g.id = c.glyph_id
          where cp.pebble_id = p.id and g.is_custom
        )
      )                                                                              as pebbles_with_custom_glyph,
      count(distinct p.user_id)                                                      as active_users
    from public.pebbles p
    where p.created_at::date between p_start and p_end
    group by date_trunc(p_bucket, p.created_at)
  )
  select
    b.bucket_date,
    coalesce(d.pebbles, 0)::bigint                    as pebbles,
    coalesce(d.pebbles_with_picture, 0)::bigint       as pebbles_with_picture,
    coalesce(d.pebbles_in_collection, 0)::bigint      as pebbles_in_collection,
    coalesce(d.pebbles_with_custom_glyph, 0)::bigint  as pebbles_with_custom_glyph,
    coalesce(d.active_users, 0)::bigint               as active_users
  from buckets b
  left join data d using (bucket_date)
  order by b.bucket_date asc;
end;
$$;

create or replace function public.get_pebble_enrichment(
  p_start date,
  p_end   date
)
returns table (
  total_pebbles          bigint,
  pct_with_picture       numeric,
  pct_in_collection      numeric,
  pct_with_custom_glyph  numeric,
  pct_with_thought       numeric,
  pct_with_soul          numeric,
  pct_with_intensity     numeric
)
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  return query
  with base as (
    select
      p.id,
      m.description,
      p.intensity,
      exists (select 1 from public.snaps s
                where s.moment_id = p.moment_id)      as has_picture,
      exists (select 1 from public.collection_pebbles cp
                where cp.pebble_id = p.id)            as in_collection,
      exists (select 1 from public.pebble_souls ps
                where ps.pebble_id = p.id)            as has_soul,
      exists (select 1 from public.collection_pebbles cp
                join public.collections c on c.id = cp.collection_id
                join public.glyphs g on g.id = c.glyph_id
                where cp.pebble_id = p.id and g.is_custom) as has_custom_glyph
    from public.pebbles p
    join public.moments m on m.id = p.moment_id
    where p.created_at::date between p_start and p_end
  )
  select
    count(*)::bigint                                                           as total_pebbles,
    round(100.0 * count(*) filter (where has_picture)
                  / nullif(count(*), 0), 1)                                    as pct_with_picture,
    round(100.0 * count(*) filter (where in_collection)
                  / nullif(count(*), 0), 1)                                    as pct_in_collection,
    round(100.0 * count(*) filter (where has_custom_glyph)
                  / nullif(count(*), 0), 1)                                    as pct_with_custom_glyph,
    round(100.0 * count(*) filter (where description is not null
                                     and length(description) > 0)
                  / nullif(count(*), 0), 1)                                    as pct_with_thought,
    round(100.0 * count(*) filter (where has_soul)
                  / nullif(count(*), 0), 1)                                    as pct_with_soul,
    round(100.0 * count(*) filter (where intensity is not null)
                  / nullif(count(*), 0), 1)                                    as pct_with_intensity
  from base
  having count(*) > 0;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. get_public_profile. Body copied VERBATIM from 20261007140000 §3 with
-- exactly one edit: the `bounce` CTE reads happened_at off the moment, which
-- is where the date now lives. The assiduity grid and the ripple count key on
-- pebbles.created_at and are untouched. Grants (anon + authenticated, set in
-- 20260730120000) are preserved by create or replace.
-- ---------------------------------------------------------------------------
create or replace function public.get_public_profile(p_handle text)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  with target as (
    select p.user_id, p.display_name, p.handle, p.glyph_id, p.created_at
      from public.profiles p
     where p.handle = lower(trim(p_handle))
       and p.public_profile = true
       and p.hidden_at is null
       -- a block in either direction: null to the other party (#834)
       and not private.is_blocked_with(p.user_id)
  ),
  utc_today as (
    select (now() at time zone 'UTC')::date as d
  ),
  window_days as (
    select generate_series(
      (select d from utc_today) - interval '27 days',
      (select d from utc_today),
      interval '1 day'
    )::date as d
  ),
  active_days_utc as (
    select distinct (pb.created_at at time zone 'UTC')::date as d
      from public.pebbles pb
     where pb.user_id = (select user_id from target)
  ),
  grid as (
    select array_agg((ad.d is not null) order by w.d) as assiduity
      from window_days w
      left join active_days_utc ad using (d)
  ),
  ripple as (
    select count(*)::int as pebbles_28d
      from public.pebbles pb
     where pb.user_id = (select user_id from target)
       and pb.created_at >= now() - interval '28 days'
  ),
  bounce as (
    select count(distinct date(m.happened_at))::int as active_days
      from public.pebbles pb
      join public.moments m on m.id = pb.moment_id
     where pb.user_id = (select user_id from target)
       and m.happened_at >= now() - interval '28 days'
  ),
  -- Every badge the target user holds. No is_active filter (see header).
  unlocked as (
    select a.id, a.slug, a.family, a.threshold, a.emotion_id, a.domain_id,
           a.title_en, a.title_fr, a.description_en, a.description_fr,
           a.glyph_id, a.sort_order, u.unlocked_at
      from public.achievement_unlocks u
      join public.achievements a on a.id = u.achievement_id
     where u.user_id = (select user_id from target)
  ),
  -- The shelf slice, ordered once here so no client has to re-sort. The inner
  -- ordered LIMIT picks the six; the aggregate repeats the ordering because
  -- jsonb_agg does not inherit a subquery's row order.
  shelf as (
    select coalesce(
      jsonb_agg(
        jsonb_build_object(
          'id',             s.id,
          'slug',           s.slug,
          'family',         s.family,
          'threshold',      s.threshold,
          -- The reference ids, not names: emotions/domains are anon-readable
          -- public reference tables whose seeded ids the clients already
          -- hardcode in static config, and the display name is localized
          -- client-side by slug (web useAchievementCopy, iOS/Android siblings).
          'emotion_id',     s.emotion_id,
          'domain_id',      s.domain_id,
          -- Admin copy overrides; null on every seeded row, where clients
          -- compose the title from family-keyed i18n instead (D7).
          'title_en',       s.title_en,
          'title_fr',       s.title_fr,
          'description_en', s.description_en,
          'description_fr', s.description_fr,
          'glyph', (
            select jsonb_build_object('strokes', g.strokes, 'view_box', g.view_box)
              from public.glyphs g
             where g.id = s.glyph_id
          ),
          'unlocked_at',    (s.unlocked_at at time zone 'UTC')::date
        )
        order by s.unlocked_at desc, s.sort_order
      ),
      '[]'::jsonb
    ) as items
    from (
      select * from unlocked
       order by unlocked_at desc, sort_order
       limit 6
    ) s
  )
  select jsonb_build_object(
    'display_name', t.display_name,
    'handle', t.handle,
    'glyph', (
      select jsonb_build_object('strokes', g.strokes, 'view_box', g.view_box)
        from public.glyphs g
       where g.id = t.glyph_id
    ),
    'pebbles_count', (
      select count(*)::int from public.pebbles pb where pb.user_id = t.user_id
    ),
    'ripple_level', (
      select case
        when r.pebbles_28d = 0                  then 0
        when r.pebbles_28d between  1 and  4    then 1
        when r.pebbles_28d between  5 and  8    then 2
        when r.pebbles_28d between  9 and 12    then 3
        when r.pebbles_28d between 13 and 16    then 4
        when r.pebbles_28d between 17 and 20    then 5
        else 6
      end from ripple r
    ),
    'bounce_level', (
      select case
        when b.active_days = 0                  then 0
        when b.active_days between  1 and  5    then 1
        when b.active_days between  6 and  9    then 2
        when b.active_days between 10 and 13    then 3
        when b.active_days between 14 and 17    then 4
        when b.active_days between 18 and 20    then 5
        when b.active_days between 21 and 24    then 6
        else 7
      end from bounce b
    ),
    'assiduity', (select to_jsonb(gr.assiduity) from grid gr),
    'days_practiced', (select count(*)::int from active_days_utc),
    'member_since', (t.created_at at time zone 'UTC')::date,
    -- Contract note: 'achievements' keeps its day-one type (a jsonb array,
    -- empty when nothing is unlocked). Clients written against the placeholder
    -- still parse; only the element type is new.
    'achievements', (select sh.items from shelf sh),
    'achievements_count', (select count(*)::int from unlocked)
  )
  from target t;
$$;

-- ---------------------------------------------------------------------------
-- 8. list_orphan_snap_files. Base 20261007131500; the draft_snaps CTE and its
-- `not exists` arm are gone with pebble_drafts. Named drafts became moments
-- with real snaps rows in 20261012090000, so their photos are protected by the
-- public.snaps arms; the unnamed drafts' uploads are orphans by design and
-- this is what finds them. The age guard still covers in-flight composes.
-- Grants: service_role only, as before (the base's statements, repeated so
-- the lockdown is visible beside the body).
-- ---------------------------------------------------------------------------
create or replace function public.list_orphan_snap_files(
  p_min_age_seconds integer default 86400,
  p_owner uuid default null
)
returns table(name text, size bigint, created_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  -- `materialized` so the UUID filter is applied before anything casts the
  -- segment: an inlined CTE would let the planner evaluate `::uuid` on a row
  -- the regex was meant to exclude.
  with candidates as materialized (
    select
      o.name,
      coalesce((o.metadata->>'size')::bigint, 0) as size,
      o.created_at,
      lower((storage.foldername(o.name))[2]) as snap_id
    from storage.objects o
    where o.bucket_id = 'pebbles-media'
      and array_length(storage.foldername(o.name), 1) >= 2
      and (storage.foldername(o.name))[2]
          ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      and o.created_at < now() - make_interval(secs => greatest(coalesce(p_min_age_seconds, 86400), 0))
      and (p_owner is null or (storage.foldername(o.name))[1] = p_owner::text)
  )
  select c.name, c.size, c.created_at
  from candidates c
  where not exists (
      select 1 from public.snaps sn where sn.id = c.snap_id::uuid
    )
    and not exists (
      select 1 from public.snaps sn
      where sn.storage_path = c.name
         or starts_with(c.name, sn.storage_path || '/')
    )
  order by c.created_at, c.name;
$$;

revoke all on function public.list_orphan_snap_files(integer, uuid) from public, anon, authenticated;
grant execute on function public.list_orphan_snap_files(integer, uuid) to service_role;
