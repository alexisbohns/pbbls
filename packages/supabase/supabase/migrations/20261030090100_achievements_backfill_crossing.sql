-- Backfill achievement_unlocks.pebble_id for the rows that existed before
-- 20261030090000, by the rule check_achievements now applies (D4). Runs as the
-- owner, so RLS does not apply. Rows whose pebble no longer exists stay null.
update public.achievement_unlocks u
   set pebble_id = public.achievement_crossing_pebble(
         u.user_id, a.family, a.threshold, a.emotion_id, a.domain_id, a.stone_kind)
  from public.achievements a
 where a.id = u.achievement_id
   and u.pebble_id is null
   and a.family in ('pebble_count','first_pebble','emotion_first','domain_first','first_stone');
