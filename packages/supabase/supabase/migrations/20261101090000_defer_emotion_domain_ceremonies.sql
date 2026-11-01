-- Defer the emotions and domains completion ceremonies past V1 (#1458).
--
-- The V1 store launch keeps the ceremony for the six firsts only. Filling the
-- emotion wheel means meeting every hard emotion, and a set of domains is not
-- an achievement we want to reward yet, so neither completion is celebrated.
-- The contributions were already silent (D7); this defers the completions (D9):
--
-- * achievement_completion: body copied from 20261030110000_achievements_writes.sql;
--   the one change (#1458) is the stack filter, so get_achievements omits the
--   completion key on the emotions and domains cards. Every shipped client
--   shows the ready mark only from completion.ready, and already decodes an
--   absent completion ("absent until whole"), so no client changes.
-- * pull_ceremony: body copied from 20261030110000_achievements_writes.sql;
--   the one change (#1458) raises not_ready for the two deferred stacks, the
--   error a client already gets for a set that is not ready, so a page loaded
--   before this migration cannot pull or pay.
-- * complete-emotions and complete-domains go inactive again: no build can
--   earn them while their ceremony is deferred, and the catalog grids hide a
--   locked inactive row. Safe because no pull can reach them: an inactive row
--   that is pulled is lost for good (20261030110000), and the guard above
--   makes that impossible.
--
-- check_achievements is untouched: it keeps recording both completions. To
-- bring the ceremonies back, re-emit these two functions without the #1458
-- lines and re-activate the two rows; every completion recorded meanwhile is
-- then offered at once, since its offer time is long past.

-- 1. the read: no completion on the emotions and domains cards -----------------------
create or replace function public.achievement_completion(p_stack text, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object(
      'completed_at', to_char(c.completed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
      'offered_from', to_char(o.offered_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
      'ready', c.pulled_at is null and now() >= o.offered_at)
    || case when c.pulled_at is null then '{}'::jsonb
            else jsonb_build_object('pulled_at', to_char(c.pulled_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')) end
    || public.achievement_opt('pebble', public.achievement_pebble(c.pebble_id, p_tz))   -- the stone that made it whole
  from public.achievement_completions c
  cross join lateral (select public.achievement_offered_at(c.completed_at, p_tz) as offered_at) o   -- #1305 part 3
  where c.user_id = (select auth.uid()) and c.stack = p_stack
    and c.stack not in ('emotions', 'domains')   -- #1458: deferred past V1
$$;

-- 2. the pull: the deferred stacks are never ready -------------------------------------
create or replace function public.pull_ceremony(p_stack text, p_tz text)
returns table (stack text, karma_granted integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := (select auth.uid());
  catalog_slug text;
  today date;
  done_at timestamptz;
  pulled timestamptz;
  done_pebble uuid;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  catalog_slug := case p_stack when 'emotions'   then 'complete-emotions'
                       when 'domains'    then 'complete-domains'
                       when 'six_firsts' then 'complete-six-firsts' end;
  if catalog_slug is null then
    raise exception 'invalid_stack' using errcode = '22023';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if today is null then
    raise exception 'invalid_tz' using errcode = '22023';
  end if;

  -- #1458: deferred past V1. Before the lock, so a deferred pull writes nothing.
  if p_stack in ('emotions', 'domains') then
    raise exception 'not_ready' using errcode = 'P0001';
  end if;

  -- Qualified throughout: "stack" is also this function's OUT column.
  select c.completed_at, c.pulled_at, c.pebble_id into done_at, pulled, done_pebble
    from public.achievement_completions c
   where c.user_id = uid and c.stack = p_stack
     for update;
  if not found then
    raise exception 'not_ready' using errcode = 'P0001';
  end if;
  if pulled is not null then
    return;   -- already pulled: no row
  end if;
  if now() < public.achievement_offered_at(done_at, p_tz) then
    raise exception 'not_ready' using errcode = 'P0001';
  end if;

  update public.achievement_completions c set pulled_at = now()
   where c.user_id = uid and c.stack = p_stack and c.pulled_at is null;
  if not found then
    return;   -- belt and braces: the lock above already serialises the pull
  end if;

  return query
  with target as (
    select a.id, a.karma_reward from public.achievements a
     where a.slug = catalog_slug and a.family = 'set_complete' and a.is_active
  ),
  unlocked as (
    -- the unlock carries the pebble that made the set whole, as the completion does (D4)
    insert into public.achievement_unlocks (user_id, achievement_id, pebble_id)
    select uid, t.id, done_pebble from target t
    on conflict (user_id, achievement_id) do nothing
    returning achievement_id
  ),
  paid as (
    insert into public.karma_events (user_id, delta, reason, type, ref_id)
    select uid, t.karma_reward, 'grant', 'credit', t.id
      from unlocked u join target t on t.id = u.achievement_id
     where t.karma_reward > 0
    returning delta
  )
  select p_stack, coalesce((select p.delta::integer from paid p), 0);
end;
$$;

-- create or replace keeps the grants; repeated for visibility.
revoke all on function public.achievement_completion(text, text) from public, anon;
revoke all on function public.pull_ceremony(text, text) from public, anon;
grant execute on function public.achievement_completion(text, text) to authenticated;
grant execute on function public.pull_ceremony(text, text) to authenticated;

comment on function public.pull_ceremony(text, text) is
  'Pulls a completed set''s ceremony once it is offered (06:00 in p_tz the day after completion) and pays its set_complete karma once (#1305). A second call returns no row. Raises not_ready before the offer or without a completion, and always for emotions and domains, deferred past V1 (#1458).';

-- 3. the catalog: the two deferred badges go quiet --------------------------------------
-- Their price (50 karma) stays, ready for the day they come back.
update public.achievements
   set is_active = false
 where family = 'set_complete'
   and slug in ('complete-emotions', 'complete-domains');
