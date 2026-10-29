-- Achievements page, purge and admin (#1305, spec 2026-10-29 D7, D9, D10).
--
-- Three whole function bodies re-emitted. create or replace has no merge
-- semantics: diff each against its source before applying a batch that also
-- re-emits one of them.

-- purge_account: body copied verbatim from 20261027090300_wrap_reveals.sql,
-- plus the achievement_views and achievement_completions deletes (#1305) right
-- after the APPEND marker. Both tables cascade from auth.users; explicit for
-- the same reason as their neighbours.
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
  -- belt-and-braces. So counts.moments is the user's moments, while
  -- counts.pebbles / counts.snaps count only rows NOT cascaded by that delete
  -- and read 0 until participants ship; the harness pins moments = <seeded>
  -- and pebbles = snaps = 0, and proves deletion with the zero-row table
  -- checks. Souls before glyphs (souls.glyph_id RESTRICT); collections before
  -- glyphs (collections.glyph_id NO ACTION).
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

  delete from public.achievement_views where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('achievement_views', v_n);

  delete from public.achievement_completions where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('achievement_completions', v_n);

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

  -- Unfolded monthly wraps (#1254). Cascades from auth.users; explicit for the
  -- same reason as its neighbours.
  delete from public.wrap_reveals where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('wrap_reveals', v_n);

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

-- admin_create_achievement: body copied verbatim from
-- 20261027090100_first_five_achievements.sql, plus two guards after bad_karma
-- (#1305): the seeded families are refused, and so is karma on a contribution.
create or replace function public.admin_create_achievement(
  p_slug text,
  p_family text,
  p_title_en text,
  p_title_fr text,
  p_karma_reward integer,
  p_sort_order integer,
  p_threshold integer default null,
  p_emotion_id uuid default null,
  p_domain_id uuid default null,
  p_description_en text default null,
  p_description_fr text default null
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_slug text := btrim(coalesce(p_slug, ''));
  v_id   uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  -- Slug shape matches the seeded convention (kebab, lowercase) so the
  -- catalog stays greppable and deterministic ids stay the seed's business.
  if v_slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$' then
    raise exception 'bad_slug';
  end if;
  if btrim(coalesce(p_title_en, '')) = '' or btrim(coalesce(p_title_fr, '')) = '' then
    raise exception 'missing_copy';
  end if;
  if p_karma_reward is null or p_karma_reward < 0 or p_karma_reward > 32767 then
    raise exception 'bad_karma';
  end if;
  -- #1305: first_stone and set_complete are seeded only (their rules key on
  -- stone_kind and on the stack slug, which the admin cannot set).
  if p_family in ('first_stone', 'set_complete') then
    raise exception 'seeded_family';
  end if;
  -- #1305 (D7): contributions never pay; say so before the CHECK does.
  if p_family in ('emotion_first', 'domain_first') and p_karma_reward > 0 then
    raise exception 'silent_family_karma';
  end if;

  -- Threshold/reference coherence per family. The counting families are
  -- meaningless without a positive threshold; the one-shot families are
  -- meaningless with one.
  if p_family in ('pebble_count', 'glyph_count', 'glyph_sales') then
    if p_threshold is null or p_threshold <= 0 then
      raise exception 'bad_threshold';
    end if;
  elsif p_family in ('first_collection', 'first_glyph', 'first_soul', 'first_moment', 'first_pebble', 'first_five') then
    if p_threshold is not null then
      raise exception 'bad_threshold';
    end if;
  elsif p_family = 'emotion_first' then
    if p_emotion_id is null then raise exception 'missing_reference'; end if;
  elsif p_family = 'domain_first' then
    if p_domain_id is null then raise exception 'missing_reference'; end if;
  end if;
  -- Anything else fails on the column CHECK below, which is the authority.

  insert into public.achievements (
    slug, family, threshold, emotion_id, domain_id, sort_order,
    karma_reward, title_en, title_fr, description_en, description_fr
  ) values (
    v_slug, p_family, p_threshold,
    case when p_family = 'emotion_first' then p_emotion_id end,
    case when p_family = 'domain_first'  then p_domain_id  end,
    coalesce(p_sort_order, 900),
    p_karma_reward,
    btrim(p_title_en), btrim(p_title_fr),
    nullif(btrim(coalesce(p_description_en, '')), ''),
    nullif(btrim(coalesce(p_description_fr, '')), '')
  )
  returning id into v_id;

  return v_id;
exception
  when unique_violation then
    raise exception 'duplicate_slug';
end;
$$;

-- admin_update_achievement: body copied verbatim from
-- 20260730150000_admin_achievement_management.sql, plus one guard after
-- bad_karma (#1305, D7): a contribution's karma stays 0.
create or replace function public.admin_update_achievement(
  p_achievement_id uuid,
  p_title_en text,
  p_title_fr text,
  p_karma_reward integer,
  p_description_en text default null,
  p_description_fr text default null,
  p_sort_order integer default null,
  p_is_active boolean default null
)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if btrim(coalesce(p_title_en, '')) = '' or btrim(coalesce(p_title_fr, '')) = '' then
    raise exception 'missing_copy';
  end if;
  if p_karma_reward is null or p_karma_reward < 0 or p_karma_reward > 32767 then
    raise exception 'bad_karma';
  end if;
  -- #1305 (D7): contributions never pay karma.
  if p_karma_reward > 0 and exists (
       select 1 from public.achievements a
        where a.id = p_achievement_id
          and a.family in ('emotion_first', 'domain_first', 'first_stone')) then
    raise exception 'silent_family_karma';
  end if;

  update public.achievements
     set title_en       = btrim(p_title_en),
         title_fr       = btrim(p_title_fr),
         description_en = nullif(btrim(coalesce(p_description_en, '')), ''),
         description_fr = nullif(btrim(coalesce(p_description_fr, '')), ''),
         karma_reward   = p_karma_reward,
         sort_order     = coalesce(p_sort_order, sort_order),
         is_active      = coalesce(p_is_active, is_active)
   where id = p_achievement_id;

  if not found then
    raise exception 'not_found';
  end if;
end;
$$;
