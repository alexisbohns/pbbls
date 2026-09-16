-- =============================================================================
-- get_public_profile: a hidden profile resolves null (#833)
-- =============================================================================
-- profiles.hidden_at (20260916100000) suspends a profile's PUBLIC reach. Every
-- CTE in this function keys off `target`, so gating that one CTE darkens the
-- whole projection — handle, display name, avatar glyph, assiduity grid,
-- badges. Null is the same answer the function already gives for an unknown or
-- unpublished handle, so "suspended" is indistinguishable from "never existed".
--
-- Established connections are deliberately NOT affected: get_connections is
-- untouched. Suspending public reach and blanking someone's name inside the
-- connection lists of people who already know them are different acts, and an
-- abusive display_name has its own remedy (#835). Design D4.
--
-- The body below is copied VERBATIM from
-- 20260817090000_public_profile_achievements.sql with exactly one addition, in
-- the `target` CTE. Re-emitting a whole function is how work gets silently
-- dropped: `create or replace` has no merge semantics and git reports no
-- conflict. The check is the pairwise diff — additions only.
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
       and p.hidden_at is null
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
