-- Achievements launch step (#1305): price and activate the three set_complete
-- rows, now that every client ships the ceremony (#1306–#1308). They were
-- seeded inactive and unpriced in 20261030090000 and left so by
-- 20261030110000, because a pull against an inactive row records pulled_at
-- but pays nothing, for good. 50 karma each (maintainer, 31 October).
--
-- The check still never unlocks them (check_achievements' case says false for
-- set_complete); pull_ceremony stays their only way in.

update public.achievements
   set karma_reward = 50,
       is_active    = true
 where family = 'set_complete'
   and slug in ('complete-emotions', 'complete-domains', 'complete-six-firsts');
