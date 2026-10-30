-- The year disc's first month (#1305, spec D12): in the month of the user's
-- first moment, only the full weeks starting on or after that day count, so a
-- new user's month is not held back by weeks that ended before they began.
-- reachable follows (it compares weeks_filled with full_weeks). Every other
-- month is unchanged.
--
-- Re-emits achievement_year_disc whole, copied from 20261030100000_achievements_read.sql
-- (its latest definition); only the fw filter differs. create or replace keeps
-- the existing grants (revoked from public and anon, executable by authenticated).

-- One year disc (D12) as of p_today, for a user whose first moment fell on
-- p_first. Mirrors scripts/oracles/achievements/year.js exactly. The callers
-- check the zone and hoist today and the first day, so get_achievements works
-- them out once for every disc.
create or replace function public.achievement_year_disc(p_year int, p_tz text, p_today date, p_first date)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  wheel constant text[] := array['joy','pride','anger','fear','shame','sadness','peace'];
  uid uuid := (select auth.uid());
  j_months jsonb;
  j_listed jsonb;
  j_next jsonb;
  cur jsonb;
  v_needs text;
  v_left int;
  v_room int;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_year is null or p_year < 1 or p_year > 9999 then
    raise exception 'invalid_year' using errcode = '22023';
  end if;

  with items as (
    select (m.happened_at at time zone p_tz)::date as d, p.id is not null as is_pebble, ec.slug as family, p.intensity
    from public.moments m
    left join public.pebbles p on p.moment_id = m.id and p.user_id = uid
    left join public.emotions e on e.id = p.emotion_id
    left join public.emotion_categories ec on ec.id = e.category_id
    where m.user_id = uid
      and m.happened_at >= make_date(p_year, 1, 1)::timestamp at time zone p_tz
      and m.happened_at <  make_date(p_year + 1, 1, 1)::timestamp at time zone p_tz
  ),
  mon as (
    select g.mo, make_date(p_year, g.mo, 1) as ms, (make_date(p_year, g.mo, 1) + interval '1 month - 1 day')::date as me
    from generate_series(1, 12) g(mo)
  ),
  dys as (
    select mo.mo,
           count(distinct i.d) filter (where i.d <= p_today) as mdays,
           count(distinct i.d) filter (where i.d <= p_today and i.is_pebble) as pdays
    from mon mo left join items i on i.d between mo.ms and mo.me
    group by mo.mo
  ),
  -- A full week runs Monday to Sunday inside the month, and counts once it has
  -- ended: from the Monday after its Sunday. In the first moment's month, only
  -- the weeks starting on or after that day count.
  fw as (
    select mo.mo, w.ws::date as ws
    from mon mo cross join lateral generate_series(mo.ms::timestamp, mo.me::timestamp, interval '1 day') w(ws)
    where extract(isodow from w.ws) = 1 and w.ws::date + 6 <= mo.me and w.ws::date + 6 < p_today
      and (p_first is null or p_first not between mo.ms and mo.me or w.ws::date >= p_first)
  ),
  -- Each item's week (its Monday), bucketed once; a full week lies inside its
  -- month, so inside the year, and has ended, so it holds no future item.
  wi as (
    select i.d - (extract(isodow from i.d)::int - 1) as ws, bool_or(i.is_pebble) as has_pebble
    from items i group by 1
  ),
  wk as (
    select fw.mo, count(*) as full_weeks, count(wi.ws) as wm, count(*) filter (where wi.has_pebble) as wp
    from fw left join wi on wi.ws = fw.ws
    group by fw.mo
  ),
  fam as (
    select distinct on (mo.mo) mo.mo, i.family
    from mon mo join items i on i.is_pebble and i.family is not null and i.d between mo.ms and mo.me and i.d <= p_today
    group by mo.mo, i.family
    -- off the wheel sorts last (a null position), then by slug, as in the oracle
    order by mo.mo, count(*) desc, sum(i.intensity) desc, array_position(wheel, i.family), i.family collate "C"
  ),
  lv as (
    select mo.mo, mo.ms, coalesce(d.mdays, 0) as mdays, coalesce(d.pdays, 0) as pdays,
           coalesce(w.full_weeks, 0) as fw, coalesce(w.wm, 0) as wm, coalesce(w.wp, 0) as wp, f.family
    from mon mo left join dys d on d.mo = mo.mo left join wk w on w.mo = mo.mo left join fam f on f.mo = mo.mo
  ),
  lv2 as (
    select *, case when pdays >= 14 and wp = fw then 2 when mdays >= 14 and wm = fw then 1 else 0 end as level from lv
  )
  select jsonb_agg(
           case when ms > p_today then jsonb_build_object('month', mo, 'state', 'upcoming', 'moment_days', 0, 'pebble_days', 0,
                                                          'full_weeks', 0, 'weeks_filled', 0, 'level', 0, 'progress', 0,
                                                          'done', jsonb_build_object('moments', false, 'pebbles', false, 'weeks', false))
           else jsonb_build_object(
             'month', mo, 'state', case when pdays = 0 then 'quiet' else 'kept' end,
             'moment_days', mdays, 'pebble_days', pdays, 'full_weeks', fw,
             'weeks_filled', case when level = 0 then wm else wp end, 'level', level,
             'progress', case level
                when 0 then round(least(mdays / 14.0, case when fw > 0 then wm::numeric / fw else 1 end), 3)
                when 1 then round(least(pdays / 14.0, case when fw > 0 then wp::numeric / fw else 1 end), 3)
                else 0 end,
             -- the done style is the server's call too (D12): no client compares a count with 14
             'done', jsonb_build_object('moments', mdays >= 14, 'pebbles', pdays >= 14,
                                        'weeks', fw > 0 and (case when level = 0 then wm else wp end) = fw))
             || public.achievement_opt('family', to_jsonb(family))
           end order by mo)
  into j_months from lv2;

  select coalesce(jsonb_agg(g.mo order by g.mo desc), '[]'::jsonb) into j_listed
  from generate_series(1, 12) g(mo)
  where p_first is not null
    and make_date(p_year, g.mo, 1) <= p_today
    and make_date(p_year, g.mo, 1) >= make_date(extract(year from p_first)::int, extract(month from p_first)::int, 1);

  if p_year = extract(year from p_today)::int then
    select x.value into cur from jsonb_array_elements(j_months) x(value)
     where (x.value->>'month')::int = extract(month from p_today)::int;
    if (cur->>'level')::int < 2 then
      v_needs := case when (cur->>'level')::int = 0 then 'moments' else 'pebbles' end;
      v_left := greatest(0, 14 - (case when v_needs = 'moments' then cur->>'moment_days' else cur->>'pebble_days' end)::int);
      -- The days the month can still add: the rest of it, today included unless
      -- today already counts. Fewer than needed, and the level is out of reach.
      v_room := (make_date(p_year, extract(month from p_today)::int, 1) + interval '1 month - 1 day')::date - p_today + 1
                - case when exists (
                    select 1 from public.moments m
                    left join public.pebbles p on p.moment_id = m.id and p.user_id = uid
                    where m.user_id = uid
                      and m.happened_at >= p_today::timestamp at time zone p_tz
                      and m.happened_at <  (p_today + 1)::timestamp at time zone p_tz
                      and (v_needs = 'moments' or p.id is not null)) then 1 else 0 end;
      j_next := jsonb_build_object(
        'month', (cur->>'month')::int, 'needs', v_needs, 'days_left', v_left,
        'reachable', (cur->>'weeks_filled')::int = (cur->>'full_weeks')::int and v_left <= v_room);
    end if;
  end if;

  return jsonb_build_object('year', p_year, 'current', p_year = extract(year from p_today)::int,
                            'days_target', 14, 'months', j_months, 'listed', j_listed)
         || public.achievement_opt('next_step', j_next);
end;
$$;
