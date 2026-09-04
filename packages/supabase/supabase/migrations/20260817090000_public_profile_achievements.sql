-- =============================================================================
-- Public profiles (#688, M50) — get_public_profile projects real achievements
-- =============================================================================
-- 20260730120000_public_profiles.sql shipped `'achievements', '[]'::jsonb` with
-- a comment promising M48 would extend the function in place. M48 shipped
-- (#664, #668, #682, #684, #685, #686) without doing so — the work fell between
-- the two milestones' issue sets — so every public profile showed zero badges,
-- including accounts with a full shelf. This is that extension.
--
-- Whole-body re-emission (create or replace has no merge semantics): everything
-- below except the achievements CTEs and the two new keys is verbatim from
-- 20260730120000. This is the second and, at time of writing, only other
-- emission of get_public_profile — nothing else appends to it.
--
-- The four questions the issue left open, and where the answers come from:
--
--   Ordering — `unlocked_at desc`, tie-broken by `sort_order asc`. The shelf
--   (achievements design D14, apps/web/components/profile/AchievementsShelf.tsx)
--   sorts on `unlocked_at desc` alone, over a catalog list already ordered by
--   sort_order; JS sort is stable, so its ties resolve by sort_order ascending.
--   Ties are the common case, not the edge: check_achievements() inserts a
--   whole retroactive batch in one statement, so a veteran's first call stamps
--   every badge with the same now(). The explicit tie-break reproduces the
--   owner shelf's effective order deterministically instead of leaning on a
--   client sort's stability guarantee.
--
--   Cap — 6, the SHELF_SIZE constant all three surfaces already hardcode
--   (AchievementsShelf.tsx:11, ProfileAchievementsCard.swift:5,
--   ProfileAchievementsCard.kt:43). Bounding an anon-facing payload is also a
--   surface decision in its own right: an unbounded array grows with the
--   catalog forever. `achievements_count` carries the untruncated total, so a
--   client can render "N badges earned" and a "+N more" affordance without the
--   rows behind them.
--
--   Locked badges — unlocked only (D14: "the shelf shows what you've EARNED;
--   the grid shows the whole ladder"). A visitor learns nothing about what the
--   owner has NOT earned, and nothing about catalog rows the admin has not
--   priced yet. Retired badges (`is_active = false`) are deliberately NOT
--   filtered: the owner-facing grid keeps rendering an unlock after retirement,
--   and a badge that was earned stays earned everywhere.
--
--   Glyph geometry — projected as raw `strokes` + `view_box`, the same embed
--   shape as the avatar glyph above it. glyphs RLS is authenticated-only
--   (20260630003348) and is not widened; the definer read is the projection.
--   Every seeded row carries a null glyph_id today, so this is null for all of
--   them until the admin assigns visuals (D12) — the key exists from day one so
--   that day needs no contract change. (Note the shape this function must NOT
--   use: admin_list_achievements returns glyph_id itself, which is useless to
--   an anon caller who cannot then read the glyph.)
--
-- Privacy contract unchanged. The per-badge payload is catalog reference data
-- (the `achievements` table is anon-readable already, RLS `using (true)`) plus
-- one per-user fact: that the badge is unlocked. `unlocked_at` is coarsened to
-- a UTC date, matching `member_since` — the assiduity grid already sets this
-- projection's activity granularity at one day, and a precise unlock timestamp
-- would be a finer presence signal than anything else here exposes (the same
-- reasoning that excludes `active_today`). Still excluded, unchanged: user_id,
-- is_admin, consent timestamps, quotas, karma, color_world, the raw counts
-- behind the levels, and active_today. `karma_reward` and `is_active` are
-- omitted too — no client renders them, and a projection earns its keys.
--
-- achievement_unlocks RLS is NOT widened (its only policy stays owner-only
-- SELECT); the definer recomputes the join for the target user, exactly as
-- achievements design D11 prescribes.
-- =============================================================================

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

-- Grants are unchanged (anon + authenticated, set in 20260730120000);
-- create or replace preserves them.
