-- Backfill for the rows and users that existed before 20261030090000 (#1305).
-- Runs as the owner, so RLS does not apply. Pays no karma: the six firsts are
-- contributions (D7), and nothing here inserts into karma_events.
--
-- 1. achievement_unlocks.pebble_id, by the rule check_achievements now applies
--    (D4). Rows whose pebble no longer exists stay null.
-- 2. The six firsts every existing user has already earned. Without this, a
--    veteran's next check_achievements would record them all at now(), and
--    they would jump to the front of every shelf ordered by unlocked_at. Each
--    row carries its crossing pebble and that pebble's created_at.
-- 3. The six_firsts completion for every user who now holds all six,
--    completed when the last of them was earned, on that unlock's pebble (the
--    order check_achievements uses: unlocked_at, then the moment's
--    happened_at). Not pulled.

-- 1. the crossing pebble -----------------------------------------------------------
update public.achievement_unlocks u
   set pebble_id = public.achievement_crossing_pebble(
         u.user_id, a.family, a.threshold, a.emotion_id, a.domain_id, a.stone_kind)
  from public.achievements a
 where a.id = u.achievement_id
   and u.pebble_id is null
   and a.family in ('pebble_count','first_pebble','emotion_first','domain_first','first_stone');

-- 2. the six firsts already earned -----------------------------------------------
insert into public.achievement_unlocks (user_id, achievement_id, pebble_id, unlocked_at)
select x.user_id, x.achievement_id, p.id, p.created_at
from (
  select u.user_id, a.id as achievement_id,
         public.achievement_crossing_pebble(u.user_id, 'first_stone', null, null, null, a.stone_kind) as pebble_id
  from (select distinct pb.user_id from public.pebbles pb) u
  cross join public.achievements a
  where a.family = 'first_stone'
) x
join public.pebbles p on p.id = x.pebble_id   -- no matching pebble, no row
on conflict (user_id, achievement_id) do nothing;

-- 3. the six firsts made whole -----------------------------------------------------
insert into public.achievement_completions (user_id, stack, completed_at, pebble_id)
select distinct on (u.user_id) u.user_id, 'six_firsts', u.unlocked_at, u.pebble_id
from public.achievement_unlocks u
join public.achievements a on a.id = u.achievement_id
left join public.pebbles p on p.id = u.pebble_id
left join public.moments m on m.id = p.moment_id
where a.family = 'first_stone' and a.is_active
  and u.user_id in (
    select u2.user_id
      from public.achievement_unlocks u2
      join public.achievements a2 on a2.id = u2.achievement_id
     where a2.family = 'first_stone' and a2.is_active
     group by u2.user_id
    having count(*) = (select count(*) from public.achievements a3
                        where a3.family = 'first_stone' and a3.is_active))
order by u.user_id, u.unlocked_at desc, m.happened_at desc nulls last
on conflict (user_id, stack) do nothing;
