-- Achievements page, the writes (#1305, spec D9, D10, §3.4).
--
-- * achievement_offered_at: the one expression for a completion's offer,
--   06:00 in the reader's zone the day after it was completed (D9). The read
--   (achievement_completion, re-emitted below) and pull_ceremony both call it,
--   so "ready" on the page and "not_ready" from the pull cannot drift.
-- * mark_achievements_seen: "new since you last looked", per card (D10).
-- * pull_ceremony: a completion is pulled once, and pays its stack's
--   set_complete catalog row once (§3.4).
-- * the three set_complete rows, seeded inactive and unpriced in part 1, stay
--   so here. pull_ceremony is their only way to unlock (check_achievements'
--   case says false for the family). The shipped grids show every active row,
--   so activating them before the clients ship the ceremony would show badges
--   no build can earn. Launch step, in the admin, before any client can pull:
--   price them, then activate them. A pull while a row is inactive records
--   pulled_at but unlocks and pays nothing, for good.

-- 1. the offer (D9) -----------------------------------------------------------------
-- Null for an unknown zone is impossible (at time zone raises), and null for a
-- null zone: callers refuse a null zone first.
create or replace function public.achievement_offered_at(p_completed timestamptz, p_tz text)
returns timestamptz language sql stable set search_path = '' as $$
  select ((((p_completed at time zone p_tz)::date + 1) + time '06:00') at time zone p_tz)
$$;

-- achievement_completion: body copied from 20261030100000_achievements_read.sql;
-- the one change (#1305, part 3) is the lateral, which calls
-- achievement_offered_at instead of spelling the expression out.
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
$$;

-- 2. seen, per card (D10) --------------------------------------------------------------
-- Stored at full precision: the "new" comparisons in get_achievements read it
-- against unlocked_at, and the payload emits it at whole seconds (D17).
create or replace function public.mark_achievements_seen(p_card text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := (select auth.uid());
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_card is null or p_card not in ('explorer', 'emotions', 'domains', 'pebbles', 'year') then
    raise exception 'invalid_card' using errcode = '22023';
  end if;
  insert into public.achievement_views (user_id, card, seen_at)
  values (uid, p_card, now())
  on conflict (user_id, card) do update set seen_at = excluded.seen_at;
end;
$$;

-- 3. the ceremony (D9, §3.4) -----------------------------------------------------------
-- Pulls a completion once it is offered. Exactly-once: the completion row is
-- locked, and the pull is the update that finds pulled_at null; a concurrent
-- second call waits on the lock, then sees pulled_at set and returns no row,
-- which clients must not read as success. The karma rides the same mechanics
-- as check_achievements and claim_blue_pebble (the unlocks PK + on conflict do
-- nothing feeding the karma insert), so it is paid at most once even past the
-- lock. An inactive set_complete row (not yet launched, or retired) still
-- lets the pull happen: it returns its one row with karma_granted 0, and
-- unlocks and pays nothing.
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

revoke all on function public.achievement_offered_at(timestamptz, text) from public, anon;
revoke all on function public.mark_achievements_seen(text) from public, anon;
revoke all on function public.pull_ceremony(text, text) from public, anon;
grant execute on function public.achievement_offered_at(timestamptz, text) to authenticated;
grant execute on function public.mark_achievements_seen(text) to authenticated;
grant execute on function public.pull_ceremony(text, text) to authenticated;
-- achievement_completion keeps its grants (create or replace); repeated for visibility.
revoke all on function public.achievement_completion(text, text) from public, anon;
grant execute on function public.achievement_completion(text, text) to authenticated;

comment on function public.pull_ceremony(text, text) is
  'Pulls a completed set''s ceremony once it is offered (06:00 in p_tz the day after completion) and pays its set_complete karma once (#1305). A second call returns no row. Raises not_ready before the offer or without a completion.';
