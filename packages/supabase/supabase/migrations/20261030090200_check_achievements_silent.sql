-- check_achievements(), re-emitted (#1305, spec D4, D7, D8, D9). Body copied
-- from 20261027090100_first_five_achievements.sql, comments and #1254 markers
-- included, with these changes, each marked "#1305":
--   1. qualified carries the family and the crossing pebble; unlocks store it;
--   2. qualified skips rows the user already holds, so the crossing pebble is
--      only looked up for what can still unlock (the result is unchanged: the
--      unlocks PK + on conflict do nothing still decide);
--   3. a first_stone arm;
--   4. the result returns only the rows to CELEBRATE. Silent rows are still
--      recorded (and still paid, where they pay) but not returned:
--      emotion_first, domain_first and first_stone (contributions, D7),
--      pebble-count-1 (first-pebble celebrates that pebble, once), and a
--      pebble_count or first_pebble level crossed by a heavy pebble
--      (positiveness -1, D8). Every shipped client celebrates every returned
--      row, so this keeps them correct without a client change. The OUT
--      columns are unchanged, (slug text, karma_granted integer), so create or
--      replace keeps the grants; they are repeated below for visibility;
--   5. after the unlocks, a set made whole records a completion, once. Its
--      pebble is the contribution recorded last (unlocked_at, then the moment's
--      happened_at among rows recorded together), over active rows only.
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
    select a.id, a.slug, a.family, a.karma_reward,                         -- #1305: family
           public.achievement_crossing_pebble(v_uid, a.family, a.threshold,
             a.emotion_id, a.domain_id, a.stone_kind) as pebble_id        -- #1305
    from public.achievements a, stats st
    where a.is_active
      -- #1305: a held row cannot unlock again; skip its crossing pebble.
      and not exists (select 1 from public.achievement_unlocks x
                       where x.user_id = v_uid and x.achievement_id = a.id)
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
        when 'first_stone'      then exists (                 -- #1305
          select 1 from public.pebbles p
           where p.user_id = v_uid
             and public.achievement_stone_match(a.stone_kind, p.intensity, p.positiveness))
        when 'glyph_count'      then st.glyph_count >= a.threshold
        when 'glyph_sales'      then st.glyph_sales >= a.threshold
        else false   -- first_five is claimed, never checked (claim_blue_pebble);
                     -- set_complete is pulled (pull_ceremony) (#1305)
      end
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id, pebble_id)  -- #1305: pebble_id
    select v_uid, q.id, q.pebble_id from qualified q
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
  left join paid p on p.ref_id = u.achievement_id
  -- #1305 (D7, D8): only the rows to celebrate. A data-modifying CTE runs to
  -- completion whatever the outer select reads, so the silent rows above are
  -- recorded all the same.
  where q.family not in ('emotion_first','domain_first','first_stone')
    and q.slug <> 'pebble-count-1'   -- first-pebble celebrates that pebble, once
    and not (q.family in ('pebble_count','first_pebble') and exists (
      select 1 from public.pebbles px where px.id = q.pebble_id and px.positiveness = -1));

  -- #1305 (D9): a set made whole records its completion once. This runs after
  -- the statement above, so it sees the unlocks it just inserted.
  insert into public.achievement_completions (user_id, stack, completed_at, pebble_id)
  select v_uid, f.stack, now(), s.pebble_id
  from (values ('emotions','emotion_first'), ('domains','domain_first'), ('six_firsts','first_stone')) f(stack, family)
  cross join lateral (
    select
      (select count(*) from public.achievements a where a.family = f.family and a.is_active) as total,
      (select count(*) from public.achievement_unlocks u
         join public.achievements a on a.id = u.achievement_id
        where u.user_id = v_uid and a.family = f.family and a.is_active) as held,
      (select u.pebble_id from public.achievement_unlocks u
         join public.achievements a on a.id = u.achievement_id
         left join public.pebbles p on p.id = u.pebble_id
         left join public.moments m on m.id = p.moment_id
        where u.user_id = v_uid and a.family = f.family and a.is_active
        order by u.unlocked_at desc, m.happened_at desc nulls last
        limit 1) as pebble_id
  ) s
  where s.total > 0 and s.held = s.total
  on conflict (user_id, stack) do nothing;
end;
$$;

revoke all on function public.check_achievements() from public, anon;
grant execute on function public.check_achievements() to authenticated;
