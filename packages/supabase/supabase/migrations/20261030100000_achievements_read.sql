-- Achievements page, the read (#1305, spec D10–D15, §3.3). Computed on read,
-- security invoker, scoped by RLS and auth.uid(). An absent value omits its key,
-- and a snapshot drops its null fields (D17); the one null served is
-- shelf[].threshold, the same row get_public_profile's shelf serves. Every
-- order ends on a unique key, so two calls never disagree. "Whole" counts the
-- family's active catalog rows, as check_achievements' completions do.

create or replace function public.achievement_opt(p_key text, p_value jsonb)
returns jsonb language sql immutable set search_path = '' as $$
  select case when p_value is null then '{}'::jsonb else jsonb_build_object(p_key, p_value) end
$$;

-- The crossing pebble's display snapshot (D15). Null when it no longer exists.
create or replace function public.achievement_pebble(p_pebble_id uuid, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select jsonb_strip_nulls(jsonb_build_object(   -- an unnamed moment omits name
    'moment_id', m.id, 'name', m.name,
    'happened_at', to_char(m.happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'emotion_id', p.emotion_id, 'intensity', p.intensity, 'positiveness', p.positiveness))
  from public.pebbles p join public.moments m on m.id = p.moment_id
  where p.id = p_pebble_id
$$;

-- The day a row is dated by (one rule for every sheet): the day its pebble
-- happened, in the reader's zone; the day it was recorded when that pebble is gone.
create or replace function public.achievement_on(p_pebble_id uuid, p_unlocked timestamptz, p_tz text)
returns text language sql stable security invoker set search_path = '' as $$
  select to_char(coalesce(
           (select m.happened_at from public.pebbles p join public.moments m on m.id = p.moment_id where p.id = p_pebble_id),
           p_unlocked) at time zone p_tz, 'YYYY-MM-DD')
$$;

-- Glyph sales need other users' entitlements, which RLS hides from the seller:
-- a definer count scoped to the caller, as check_achievements counts them.
create or replace function public.achievement_sales_count()
returns int language sql stable security definer set search_path = '' as $$
  select count(*)::int
  from public.glyph_entitlements e join public.glyphs g on g.id = e.glyph_id
  where g.user_id = (select auth.uid()) and e.user_id <> g.user_id
$$;

-- A completion with its offer (D9): offered from 06:00 local the next day.
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
  cross join lateral (select ((((c.completed_at at time zone p_tz)::date + 1) + time '06:00') at time zone p_tz) as offered_at) o
  where c.user_id = (select auth.uid()) and c.stack = p_stack
$$;

-- What a first-five task made, and when (the sheet's row). '{}' when gone.
create or replace function public.achievement_first_subject(p_slug text, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select coalesce((
    select jsonb_strip_nulls(jsonb_build_object('on', to_char(x.created_at at time zone p_tz, 'YYYY-MM-DD'),
                              'subject', jsonb_build_object('kind', x.kind, 'id', x.id, 'name', x.name)))
    from (
      select 'moment' as kind, m.id, m.name, m.created_at from public.moments m
       where p_slug = 'first-moment' and m.user_id = (select auth.uid())
      union all
      select 'pebble', m.id, m.name, p.created_at from public.pebbles p join public.moments m on m.id = p.moment_id
       where p_slug = 'first-pebble' and p.user_id = (select auth.uid())
      union all
      select 'collection', c.id, c.name, c.created_at from public.collections c
       where p_slug = 'first-collection' and c.user_id = (select auth.uid())
      union all
      select 'soul', s.id, s.name, s.created_at from public.souls s
       where p_slug = 'first-soul' and s.user_id = (select auth.uid())
      union all
      select 'glyph', g.id, g.name, g.created_at from public.glyphs g
       where p_slug = 'first-glyph' and g.user_id = (select auth.uid())
    ) x
    order by x.created_at, x.id
    limit 1), '{}'::jsonb)
$$;

-- One counting stack: count, levels reached, the next, the ladder (D3).
create or replace function public.achievement_ladder(p_family text, p_count int, p_seen timestamptz, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object(
      'count', p_count,
      'levels', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'threshold', a.threshold,
                 'on', public.achievement_on(u.pebble_id, u.unlocked_at, p_tz),
                 'new', coalesce(u.unlocked_at > p_seen, false))
               || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
               order by a.threshold, a.slug)
        from public.achievement_unlocks u
        join public.achievements a on a.id = u.achievement_id and a.family = p_family
        where u.user_id = (select auth.uid())), '[]'::jsonb),
      'ladder', coalesce((
        select jsonb_agg(a.threshold order by a.threshold) from public.achievements a
        where a.family = p_family and a.is_active and (p_family <> 'pebble_count' or a.threshold <= 1000)), '[]'::jsonb))
    -- A count can fall below a level already held (a deleted pebble); the level
    -- stays (badges are permanent), so the next is above both, and progress
    -- waits at 0 until the count climbs back past the held level.
    || coalesce((
      select jsonb_build_object('next', jsonb_build_object(
               'threshold', nx.t, 'left', nx.t - p_count,
               'progress', greatest(0, round((p_count - pv.t)::numeric / (nx.t - pv.t), 3))))
      from (select coalesce(max(a.threshold), 0) as t
              from public.achievement_unlocks u
              join public.achievements a on a.id = u.achievement_id and a.family = p_family
             where u.user_id = (select auth.uid())) hd
      cross join lateral (select min(a.threshold) as t from public.achievements a
             where a.family = p_family and a.is_active and a.threshold > greatest(p_count, hd.t)) nx
      cross join lateral (select greatest(hd.t, coalesce(max(a.threshold), 0)) as t from public.achievements a
             where a.family = p_family and a.is_active and a.threshold <= p_count) pv
      where nx.t is not null), '{}'::jsonb)
    || case when p_family = 'pebble_count' then jsonb_build_object('then_every', 500) else '{}'::jsonb end
    -- the card's "since": the day of its first level
    || coalesce((
      select jsonb_build_object('since', public.achievement_on(u.pebble_id, u.unlocked_at, p_tz))
      from public.achievement_unlocks u
      join public.achievements a on a.id = u.achievement_id and a.family = p_family
      where u.user_id = (select auth.uid())
      order by a.threshold, a.slug limit 1), '{}'::jsonb)
$$;

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
  -- ended: from the Monday after its Sunday.
  fw as (
    select mo.mo, w.ws::date as ws
    from mon mo cross join lateral generate_series(mo.ms::timestamp, mo.me::timestamp, interval '1 day') w(ws)
    where extract(isodow from w.ws) = 1 and w.ws::date + 6 <= mo.me and w.ws::date + 6 < p_today
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

-- One year disc for the caller, today in p_tz (D12).
create or replace function public.achievements_year(p_year int, p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  uid uuid := (select auth.uid());
  today date;
  first_day date;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if today is null then
    raise exception 'invalid_tz' using errcode = '22023';
  end if;
  if p_year is null or p_year < 1 or p_year > 9999 then
    raise exception 'invalid_year' using errcode = '22023';
  end if;
  -- min on the instant, then the zone: the (user_id, happened_at) index answers it
  select (min(m.happened_at) at time zone p_tz)::date into first_day from public.moments m where m.user_id = uid;
  return public.achievement_year_disc(p_year, p_tz, today, first_day);
end;
$$;

create or replace function public.get_achievements(p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  wheel constant text[] := array['joy','pride','anger','fear','shame','sadness','peace'];
  uid uuid := (select auth.uid());
  today date;
  since date;
  first_day date;
  seen jsonb;        -- full precision, for the "new" comparisons
  seen_out jsonb;    -- whole seconds, for the payload (D17)
  n_pebbles int; n_glyphs int;
  n_five int; n_six int; has25 boolean;
  n_emo int; of_emo int; n_dom int; of_dom int; of_six int;
  rings text[] := '{}';
  j_explorer jsonb; j_emotions jsonb; j_domains jsonb; j_years jsonb;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if today is null then
    raise exception 'invalid_tz' using errcode = '22023';
  end if;

  select (pr.created_at at time zone p_tz)::date into since from public.profiles pr where pr.user_id = uid;
  select coalesce(jsonb_object_agg(v.card, to_jsonb(v.seen_at)), '{}'::jsonb),
         coalesce(jsonb_object_agg(v.card, to_char(v.seen_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')), '{}'::jsonb)
    into seen, seen_out
    from public.achievement_views v where v.user_id = uid;
  select count(*) into n_pebbles from public.pebbles p where p.user_id = uid;
  select count(*) into n_glyphs from public.glyphs g where g.user_id = uid;

  -- min on the instant, then the zone: the (user_id, happened_at) index answers it
  select (min(m.happened_at) at time zone p_tz)::date into first_day from public.moments m where m.user_id = uid;

  -- "Whole" is the family's active catalog rows, held over total, as in
  -- check_achievements' completions (M3): one denominator for count/of and rings.
  select count(*) filter (where a.slug in ('first-moment','first-pebble','first-collection','first-soul','first-glyph')),
         count(*) filter (where a.family = 'first_stone' and a.is_active),
         bool_or(a.slug = 'pebble-count-25'),
         count(*) filter (where a.family = 'emotion_first' and a.is_active),
         count(*) filter (where a.family = 'domain_first' and a.is_active)
    into n_five, n_six, has25, n_emo, n_dom
    from public.achievement_unlocks u join public.achievements a on a.id = u.achievement_id
   where u.user_id = uid;
  select count(*) filter (where a.family = 'emotion_first'),
         count(*) filter (where a.family = 'domain_first'),
         count(*) filter (where a.family = 'first_stone')
    into of_emo, of_dom, of_six
    from public.achievements a where a.is_active;
  if n_five = 5 then rings := array_append(rings, 'first_five'); end if;
  if n_six = of_six and of_six > 0 then rings := array_append(rings, 'six_firsts'); end if;
  if coalesce(has25, false) then rings := array_append(rings, 'twenty_five'); end if;
  if n_dom = of_dom and of_dom > 0 then rings := array_append(rings, 'domains'); end if;
  if n_emo = of_emo and of_emo > 0 then rings := array_append(rings, 'emotions'); end if;

  -- Explorer ------------------------------------------------------------------------
  select jsonb_build_object(
      'first_five', coalesce((
        select jsonb_agg(jsonb_build_object('slug', s.slug, 'done', u.user_id is not null,
                   'new', coalesce(u.unlocked_at > (seen->>'explorer')::timestamptz, false))
                 || case when u.user_id is null then '{}'::jsonb else public.achievement_first_subject(s.slug, p_tz) end
               order by s.ord)
        from unnest(array['first-moment','first-pebble','first-collection','first-soul','first-glyph']) with ordinality s(slug, ord)
        left join public.achievements a on a.slug = s.slug
        left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid), '[]'::jsonb),
      'six_firsts', coalesce((
        select jsonb_agg(jsonb_build_object('kind', a.stone_kind, 'done', u.user_id is not null,
                   'new', coalesce(u.unlocked_at > (seen->>'explorer')::timestamptz, false))
                 || case when u.user_id is null then '{}'::jsonb
                         else jsonb_build_object('on', public.achievement_on(u.pebble_id, u.unlocked_at, p_tz)) end
                 || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
               order by a.sort_order, a.slug)
        from public.achievements a
        left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid
        where a.family = 'first_stone' and a.is_active), '[]'::jsonb),
      'rings_done', to_jsonb(rings))     -- up to five: first_five, six_firsts, twenty_five, domains, emotions
    || public.achievement_opt('twenty_five', (
         select jsonb_build_object('count', least(n_pebbles, 25), 'done', u.user_id is not null,
                  'new', coalesce(u.unlocked_at > (seen->>'explorer')::timestamptz, false))
                || case when u.user_id is null then '{}'::jsonb
                        else jsonb_build_object('on', public.achievement_on(u.pebble_id, u.unlocked_at, p_tz)) end
                || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
         from public.achievements a
         left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid
         where a.slug = 'pebble-count-25'))
    || public.achievement_opt('first_pebble', (
         select public.achievement_pebble(u.pebble_id, p_tz)
         from public.achievement_unlocks u join public.achievements a on a.id = u.achievement_id
         where u.user_id = uid and a.slug = 'pebble-count-1'))
    || public.achievement_opt('completion', public.achievement_completion('six_firsts', p_tz))
  into j_explorer;

  -- Emotions: 38 seats, wheel order then slug; each family's met seats by the day met --------
  with seats as (
    select e.id, ec.slug as family,
           (row_number() over (order by array_position(wheel, ec.slug), e.slug, e.id) - 1)::int as seat
    from public.emotions e join public.emotion_categories ec on ec.id = e.category_id
  ),
  sm as (
    select s.seat, s.id, s.family, u.user_id is not null as met, u.unlocked_at, u.pebble_id,
           coalesce(m.happened_at, u.unlocked_at) as met_at
    from seats s
    left join public.achievements a on a.family = 'emotion_first' and a.emotion_id = s.id and a.is_active
    left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid
    left join public.pebbles p on p.id = u.pebble_id
    left join public.moments m on m.id = p.moment_id
  ),
  fams as (
    select x.family,
           coalesce(jsonb_agg(x.seat order by x.met_at, x.seat) filter (where x.met), '[]'::jsonb) as met,
           coalesce(jsonb_agg(x.seat order by x.seat) filter (where not x.met), '[]'::jsonb) as not_yet
    from sm x group by x.family
  )
  select jsonb_build_object('count', n_emo, 'of', of_emo,
           'seats', coalesce((select jsonb_agg(jsonb_build_object('seat', x.seat, 'emotion_id', x.id, 'family', x.family,
                      'met', x.met, 'new', coalesce(x.unlocked_at > (seen->>'emotions')::timestamptz, false))
                    || case when x.met then jsonb_build_object('on', public.achievement_on(x.pebble_id, x.unlocked_at, p_tz)) else '{}'::jsonb end
                    || public.achievement_opt('pebble', public.achievement_pebble(x.pebble_id, p_tz))
                    order by x.seat) from sm x), '[]'::jsonb),
           -- the sheet's order (D11 for emotions): families on the wheel, met seats by the day met
           'families', coalesce((select jsonb_agg(jsonb_build_object('family', f.family, 'met', f.met, 'not_yet', f.not_yet)
                                                   order by array_position(wheel, f.family), f.family collate "C") from fams f), '[]'::jsonb))
         || public.achievement_opt('since', (select to_jsonb(to_char(min(x.met_at) at time zone p_tz, 'YYYY-MM-DD')) from sm x where x.met))
         || public.achievement_opt('completion', public.achievement_completion('emotions', p_tz))
  into j_emotions;

  -- Domains: coverage tier, the order they came, not yet (D11) --------------------------
  select jsonb_build_object('count', n_dom, 'of', of_dom,
           'coverage', case when n_dom = 0 then 'none'
                            when n_dom < of_dom / 3.0 then 'few'
                            when n_dom < of_dom / 2.0 then 'third'
                            when n_dom < of_dom * 0.75 then 'half'
                            when n_dom < of_dom then 'most'
                            else 'all' end,
           'entered', coalesce((
             select jsonb_agg(jsonb_build_object('domain_id', a.domain_id,
                        'on', public.achievement_on(u.pebble_id, u.unlocked_at, p_tz),
                        'new', coalesce(u.unlocked_at > (seen->>'domains')::timestamptz, false))
                      || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
                    order by coalesce(m.happened_at, u.unlocked_at) desc, u.unlocked_at desc, a.slug)
             from public.achievement_unlocks u
             join public.achievements a on a.id = u.achievement_id and a.family = 'domain_first'
                                       and a.domain_id is not null and a.is_active
             left join public.pebbles p on p.id = u.pebble_id
             left join public.moments m on m.id = p.moment_id
             where u.user_id = uid), '[]'::jsonb),
           'not_yet', coalesce((
             select jsonb_agg(a.domain_id order by d.slug, d.id)
             from public.achievements a join public.domains d on d.id = a.domain_id
             where a.family = 'domain_first' and a.is_active
               and not exists (select 1 from public.achievement_unlocks u
                                where u.user_id = uid and u.achievement_id = a.id)), '[]'::jsonb))
         || public.achievement_opt('completion', public.achievement_completion('domains', p_tz))
  into j_domains;

  -- Years: the current one always, past ones with a moment, newest first (D12). A
  -- future-dated moment adds no disc, and a year before 1 is never asked for. ------
  select coalesce(jsonb_agg(public.achievement_year_disc(ys.y, p_tz, today, first_day) order by ys.y desc), '[]'::jsonb)
    into j_years
  from (
    select extract(year from today)::int as y
    union
    select distinct extract(year from (m.happened_at at time zone p_tz))::int from public.moments m where m.user_id = uid
  ) ys
  where ys.y between 1 and extract(year from today)::int;

  return public.achievement_opt('since', to_jsonb(to_char(since, 'YYYY-MM-DD')))
      || public.achievement_opt('days_kept', to_jsonb(today - since + 1))
      || jsonb_build_object(
           'explorer', j_explorer,
           'emotions', j_emotions,
           'domains', j_domains,
           'pebbles', public.achievement_ladder('pebble_count', n_pebbles, (seen->>'pebbles')::timestamptz, p_tz),
           'glyphs_carved', public.achievement_ladder('glyph_count', n_glyphs, (seen->>'pebbles')::timestamptz, p_tz),
           'glyph_sales', public.achievement_ladder('glyph_sales', public.achievement_sales_count(), (seen->>'pebbles')::timestamptz, p_tz),
           'years', j_years,
           'seen', seen_out,   -- when each card was last looked at (D10), for copy like "new since 2 October"
           -- D18: the owner's Profile shelf, without the private families
           'shelf', coalesce((
             select jsonb_agg(jsonb_build_object('slug', x.slug, 'family', x.family, 'threshold', x.threshold,
                                                 'unlocked_on', to_char(x.unlocked_at at time zone p_tz, 'YYYY-MM-DD'))
                              order by x.unlocked_at desc, x.sort_order, x.slug)
             from (select a.slug, a.family, a.threshold, a.sort_order, u.unlocked_at
                     from public.achievement_unlocks u join public.achievements a on a.id = u.achievement_id
                    where u.user_id = uid
                      and a.family not in ('emotion_first', 'domain_first', 'first_stone', 'set_complete')
                    order by u.unlocked_at desc, a.sort_order, a.slug
                    limit 6) x), '[]'::jsonb),
           'shelf_count', (select count(*) from public.achievement_unlocks u join public.achievements a on a.id = u.achievement_id
                            where u.user_id = uid
                              and a.family not in ('emotion_first', 'domain_first', 'first_stone', 'set_complete')));
end;
$$;

revoke all on function public.achievement_on(uuid, timestamptz, text) from public, anon;
grant execute on function public.achievement_on(uuid, timestamptz, text) to authenticated;
revoke all on function public.achievement_opt(text, jsonb) from public, anon;
revoke all on function public.achievement_pebble(uuid, text) from public, anon;
revoke all on function public.achievement_sales_count() from public, anon;
revoke all on function public.achievement_completion(text, text) from public, anon;
revoke all on function public.achievement_first_subject(text, text) from public, anon;
revoke all on function public.achievement_ladder(text, int, timestamptz, text) from public, anon;
revoke all on function public.achievement_year_disc(int, text, date, date) from public, anon;
revoke all on function public.achievements_year(int, text) from public, anon;
revoke all on function public.get_achievements(text) from public, anon;
grant execute on function public.achievement_opt(text, jsonb) to authenticated;
grant execute on function public.achievement_pebble(uuid, text) to authenticated;
grant execute on function public.achievement_sales_count() to authenticated;
grant execute on function public.achievement_completion(text, text) to authenticated;
grant execute on function public.achievement_first_subject(text, text) to authenticated;
grant execute on function public.achievement_ladder(text, int, timestamptz, text) to authenticated;
grant execute on function public.achievement_year_disc(int, text, date, date) to authenticated;
grant execute on function public.achievements_year(int, text) to authenticated;
grant execute on function public.get_achievements(text) to authenticated;
