-- Migration: the first five and the blue pebble (#1254, spec 2026-10-26 §4.2)
--
-- The Home tab's beginner checklist is six achievements. Two new one-shot
-- families, first_moment and first_pebble, join first_collection, first_glyph
-- and first_soul: check_achievements() unlocks them once and they survive any
-- later deletion, so the five tasks rest on one truth. The sixth, blue-pebble
-- (family first_five), is NEVER unlocked by the check: the user claims it
-- through claim_blue_pebble() once the five are unlocked. The claim is the
-- ceremony; after it the checklist leaves Home.
--
-- Three pieces are re-emitted here and were diffed pairwise against their
-- previous definition before this file was committed:
--   * achievements.family check: + first_moment, first_pebble, first_five
--   * check_achievements(): body from 20260730090000 + two case arms
--   * admin_create_achievement(): body from 20260730150000 + the one-shot list

-- 1. the family check ---------------------------------------------------------
alter table public.achievements drop constraint achievements_family_check;
alter table public.achievements add constraint achievements_family_check check (family in (
  'pebble_count','emotion_first','domain_first','first_collection',
  'first_glyph','first_soul','glyph_count','glyph_sales',
  'first_moment','first_pebble','first_five'));

-- 2. the seed ----------------------------------------------------------------------
-- Deterministic ids like the rest of the catalog; sort_order sits in the
-- one-shot block (400s) after first-soul. karma_reward is set by the
-- maintainer in the admin afterwards (0 until then, so nothing is paid by
-- accident).
insert into public.achievements (id, slug, family, threshold, sort_order) values
  (md5('achievements:first-moment')::uuid, 'first-moment', 'first_moment', null, 430),
  (md5('achievements:first-pebble')::uuid, 'first-pebble', 'first_pebble', null, 440),
  (md5('achievements:blue-pebble')::uuid,  'blue-pebble',  'first_five',   null, 450);

-- 3. check_achievements() ------------------------------------------------------------
-- Body verbatim from 20260730090000 with two additions in the stats CTE and
-- the case: has_moment / first_moment and pebble_count >= 1 / first_pebble.
-- first_five is deliberately absent: it falls to `else false`.
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
               where s.user_id = v_uid)                as has_soul,
      -- #1254: a moment is a thing before it is a pebble.
      exists (select 1 from public.moments m
               where m.user_id = v_uid)                as has_moment
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
        when 'first_moment'     then st.has_moment            -- #1254
        when 'first_pebble'     then st.pebble_count >= 1     -- #1254
        when 'glyph_count'      then st.glyph_count >= a.threshold
        when 'glyph_sales'      then st.glyph_sales >= a.threshold
        else false   -- first_five is claimed, never checked (claim_blue_pebble)
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

-- 4. claim_blue_pebble() --------------------------------------------------------------
-- The same exactly-once mechanics as the check (the unlocks PK + on conflict
-- do nothing feeding the karma insert), gated on the five one-shot unlocks.
-- A second call inserts nothing and returns no row. Raises 'not_ready' when a
-- prerequisite is missing, so a client cannot claim ahead of the data.
-- The gate keys on five SLUGS, and check_achievements grants only is_active
-- rows: retiring any of first-moment, first-pebble, first-collection,
-- first-soul or first-glyph in the admin makes the blue pebble unreachable for
-- every later user. Retire the blue pebble itself instead (the claim then
-- returns no row, which clients must not read as a success).
create or replace function public.claim_blue_pebble()
returns table (slug text, karma_granted integer)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_missing int;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  select count(*) into v_missing
  from unnest(array['first-moment','first-pebble','first-collection','first-soul','first-glyph']) req(s)
  where not exists (
    select 1 from public.achievement_unlocks u
    join public.achievements a on a.id = u.achievement_id
    where u.user_id = v_uid and a.slug = req.s);
  if v_missing > 0 then
    raise exception 'not_ready' using errcode = 'P0001';
  end if;

  return query
  with target as (
    select a.id, a.slug, a.karma_reward
    from public.achievements a
    where a.slug = 'blue-pebble' and a.is_active
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id)
    select v_uid, t.id from target t
    on conflict (user_id, achievement_id) do nothing
    returning achievement_id
  ),
  paid as (
    insert into public.karma_events (user_id, delta, reason, type, ref_id)
    select v_uid, t.karma_reward, 'grant', 'credit', t.id
    from unlocked u join target t on t.id = u.achievement_id
    where t.karma_reward > 0
    returning ref_id, delta
  )
  select t.slug, coalesce(p.delta::integer, 0)
  from unlocked u
  join target t on t.id = u.achievement_id
  left join paid p on p.ref_id = u.achievement_id;
end;
$$;

revoke all on function public.claim_blue_pebble() from public, anon;
grant execute on function public.claim_blue_pebble() to authenticated;

comment on function public.claim_blue_pebble() is
  'Claims the blue-pebble achievement once the five firsts are unlocked (#1254). Idempotent: a second call returns no row. Raises not_ready before the five.';

-- 5. admin_create_achievement(): the one-shot list ------------------------------------
-- Body verbatim from 20260730150000; the only change is the one-shot family
-- list in the threshold-coherence branch, which gains the three new families.
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
