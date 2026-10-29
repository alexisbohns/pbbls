-- check_achievements(), re-emitted (#1305, spec D4, D7, D8, D9). Body from
-- 20261027090100 with four changes, each marked "#1305":
--   1. qualified carries the family and the crossing pebble; unlocks store it;
--   2. a first_stone arm;
--   3. the return gains celebrate (false for contributions and for a level
--      crossed by a heavy pebble), so OUT columns change: drop, then create;
--   4. after the unlocks, a set made whole records a completion.
drop function public.check_achievements();

create function public.check_achievements()
returns table (slug text, karma_granted integer, celebrate boolean)
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
    select
      (select count(*) from public.pebbles p where p.user_id = v_uid)                       as pebble_count,
      (select count(*) from public.glyphs g where g.user_id = v_uid)                        as glyph_count,
      (select count(*)
         from public.glyph_entitlements e
         join public.glyphs g on g.id = e.glyph_id
        where g.user_id = v_uid and e.user_id <> g.user_id)                                  as glyph_sales,
      exists (select 1 from public.collections c where c.user_id = v_uid)                   as has_collection,
      exists (select 1 from public.souls s where s.user_id = v_uid)                         as has_soul,
      exists (select 1 from public.moments m where m.user_id = v_uid)                       as has_moment
  ),
  qualified as (
    select a.id, a.slug, a.family, a.karma_reward,                                          -- #1305: family
           public.achievement_crossing_pebble(v_uid, a.family, a.threshold,
             a.emotion_id, a.domain_id, a.stone_kind) as pebble_id                         -- #1305
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
        when 'first_moment'     then st.has_moment
        when 'first_pebble'     then st.pebble_count >= 1
        when 'first_stone'      then exists (                                               -- #1305
          select 1 from public.pebbles p
           where p.user_id = v_uid
             and public.achievement_stone_match(a.stone_kind, p.intensity, p.positiveness))
        when 'glyph_count'      then st.glyph_count >= a.threshold
        when 'glyph_sales'      then st.glyph_sales >= a.threshold
        else false   -- first_five is claimed; set_complete is pulled (pull_ceremony)
      end
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id, pebble_id)              -- #1305
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
  select q.slug,
         coalesce(p.delta::integer, 0),
         case                                                                               -- #1305 (D7, D8)
           when q.family in ('emotion_first','domain_first','first_stone') then false
           when q.slug = 'pebble-count-1' then false   -- first-pebble celebrates that pebble, once
           when q.family in ('pebble_count','first_pebble') and exists (
             select 1 from public.pebbles px where px.id = q.pebble_id and px.positiveness = -1) then false
           else true
         end
  from unlocked u
  join qualified q on q.id = u.achievement_id
  left join paid p on p.ref_id = u.achievement_id;

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
        where u.user_id = v_uid and a.family = f.family
        order by m.happened_at desc nulls last, u.unlocked_at desc
        limit 1) as pebble_id
  ) s
  where s.total > 0 and s.held = s.total
  on conflict (user_id, stack) do nothing;
end;
$$;

revoke all on function public.check_achievements() from public, anon;
grant execute on function public.check_achievements() to authenticated;
