-- =============================================================================
-- A block severs the signed-in public reads, and stops confirming itself (#834)
-- =============================================================================
-- Until now a block (connection_blocks, written by remove_connection(p_block
-- => true)) gated connections and invites only. A blocked user who stayed
-- signed in kept first-class read access to the blocker's public pebbles and
-- public profile, assiduity grid included. And preview_connection_invite was
-- block-unaware, so a blocked peer could preview the blocker's token (live
-- card), try to accept it (invite_expired) and read the block off the
-- difference: the exact signal the silent block exists to deny.
--
-- Four changes, one transaction:
--
--   1. public.is_blocked_with(p_other) — a definer helper, because the
--      blocked user's own RLS on connection_blocks is blocker-only (D5): an
--      inline subquery inside pebbles_select would see only the rows where
--      the viewer is the blocker, and miss the direction that matters.
--   2. pebbles_select — the two cross-user arms gain the both-directions
--      block predicate. The owner arm stays ungated.
--   3. get_public_profile — the `target` CTE gains the same predicate, so the
--      whole projection resolves null to the other party, the same answer as
--      an unknown, private or hidden handle.
--   4. preview_connection_invite — a viewer the inviter has blocked gets
--      {"status": "expired"}, the shape of a real expiry. The reverse
--      direction (the viewer blocked the inviter) stays live: the viewer
--      already knows about a block they placed, and going dark there would
--      only become an oracle in the other direction. The same re-emission
--      carries #833's hidden-profile gate on the inviter join, which #833
--      deliberately left to this issue so one function body is not
--      re-emitted by two migrations.
--
-- WHAT THIS DOES NOT CLOSE (accepted residuals, as the issue states):
--
--   - Anonymous access. auth.uid() is null for anon, every predicate below
--     is false for it, and the anon paths are unchanged on purpose. A blocked
--     user can sign out and compare. Perfect severance against someone who
--     can read anonymously is not possible.
--   - get_shared_pebble, the share-by-link path, is unchanged (it answers
--     anon and signed-in callers alike).
--   - is_blocked_with is callable through PostgREST by any signed-in user,
--     scoped to the caller: it can only answer "is there a block between ME
--     and p_other", never about a third pair. A caller who already holds the
--     other party's user_id learns what the anon/signed-in comparison above
--     already tells them. No cross-user projection returns a user_id.
--   - report_content's pebble gate mirrors pebbles_select by design
--     (20260916090000) and is NOT changed here. Whether it follows is an open
--     decision on #834.
--
-- Bases (each the newest definition at the time of writing; additions only,
-- check with a pairwise diff):
--   pebbles_select              20260916100000_moderation_state.sql §3
--   get_public_profile          20260916100100_get_public_profile_hidden.sql
--   preview_connection_invite   20260730070347_mutual_connections.sql §4
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The helper. Both directions, keyed on the caller. connection_blocks' pk
-- (blocker_id, blocked_id) serves both equality lookups.
-- ---------------------------------------------------------------------------
create function public.is_blocked_with(p_other uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
      from public.connection_blocks b
     where (b.blocker_id = auth.uid() and b.blocked_id = p_other)
        or (b.blocker_id = p_other and b.blocked_id = auth.uid())
  );
$$;

-- pebbles_select is `to authenticated`, and the definer functions below run
-- as the owner, so anon never needs it.
revoke all on function public.is_blocked_with(uuid) from public, anon;
grant execute on function public.is_blocked_with(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 2. pebbles_select. Base: 20260916100000 §3, with the block predicate added
-- inside the cross-user arm. It covers the private arm too: remove_connection
-- deletes the connection in the same transaction as the block, so that arm is
-- already dark, and the predicate keeps it dark if the two ever disagree.
-- ---------------------------------------------------------------------------
drop policy "pebbles_select" on public.pebbles;

create policy "pebbles_select" on public.pebbles
  for select to authenticated using (
    user_id = auth.uid()
    or (
      hidden_at is null
      and (
        visibility = 'public'
        or (
          visibility = 'private'
          and exists (
            select 1
              from public.connections c
             where c.user_a = least(auth.uid(), pebbles.user_id)
               and c.user_b = greatest(auth.uid(), pebbles.user_id)
          )
        )
      )
      -- a block in either direction severs every cross-user read (#834)
      and not public.is_blocked_with(pebbles.user_id)
    )
  );

-- ---------------------------------------------------------------------------
-- 3. get_public_profile. Body copied VERBATIM from
-- 20260916100100_get_public_profile_hidden.sql with exactly one addition, in
-- the `target` CTE. Every other CTE keys off `target`, so this darkens the
-- whole projection. For anon, auth.uid() is null and the predicate is false.
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
       and not public.is_blocked_with(p.user_id)
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
    select count(distinct date(pb.happened_at))::int as active_days
      from public.pebbles pb
     where pb.user_id = (select user_id from target)
       and pb.happened_at >= now() - interval '28 days'
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
-- 4. preview_connection_invite. Body copied VERBATIM from
-- 20260730070347_mutual_connections.sql §4 with two additions: the
-- blocked-viewer branch after the expiry check, and `pr.hidden_at is null` on
-- the inviter join.
--
-- The block branch is ONE direction on purpose: blocker = inviter, blocked =
-- the caller. It returns the expiry shape verbatim, so preview and accept now
-- agree for a blocked peer (both "expired"), and neither confirms the block.
--
-- A hidden inviter keeps status 'valid' with a null inviter: the card carries
-- no name and no glyph. Every client decodes a null inviter without failing
-- (iOS falls back to "Someone", Android to the unnamed-peer string, web to an
-- empty name and a "?" avatar). accept_connection_invite is unchanged.
-- ---------------------------------------------------------------------------
create or replace function public.preview_connection_invite(p_token text)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_invite  public.connection_invites;
  v_inviter jsonb;
begin
  select * into v_invite from public.connection_invites where token = p_token;
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;

  -- Revoked and expired share one outward shape with no inviter data: a
  -- withdrawn token goes fully dark.
  if v_invite.revoked_at is not null or v_invite.expires_at <= now() then
    return jsonb_build_object('status', 'expired');
  end if;

  -- A viewer the inviter has blocked sees the same dark shape as expiry
  -- (#834). auth.uid() is null for anon, so the anonymous preview is unchanged.
  if exists (
    select 1 from public.connection_blocks b
     where b.blocker_id = v_invite.inviter_id
       and b.blocked_id = auth.uid()
  ) then
    return jsonb_build_object('status', 'expired');
  end if;

  select jsonb_build_object(
    'display_name', pr.display_name,
    'glyph', case when g.id is not null then
      jsonb_build_object('strokes', g.strokes, 'view_box', g.view_box)
    else null end
  ) into v_inviter
  from public.profiles pr
  left join public.glyphs g on g.id = pr.glyph_id
  where pr.user_id = v_invite.inviter_id
    -- a suspended profile does not reach strangers through an invite link (#833)
    and pr.hidden_at is null;

  return jsonb_build_object('status', 'valid', 'inviter', v_inviter);
end;
$$;

-- Grants on get_public_profile and preview_connection_invite are unchanged
-- (anon + authenticated, set in 20260730120000 and 20260730070347);
-- create or replace preserves them.
