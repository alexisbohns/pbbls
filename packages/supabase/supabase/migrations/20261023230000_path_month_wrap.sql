-- path_month_wrap: one month's page, aggregated on the server (#1207, part 4).
--
-- Everything the month page shows below the strip, decided once for every
-- client: which sections exist (the thresholds live here), the cover's days,
-- the weeks summary, the five milestones, persons with who is new since the
-- previous month, the emotions gem (worths here, the facets from gem_allocate),
-- fine emotions, domains, the tone of the month with its verdict, and the hours
-- with the busiest window for collecting. Ids, not names.
--
-- p_month must be the first day of a month. Days are 0-based indices into the
-- month (day 0 is the 1st), in p_tz, the reader's IANA zone; first_weekday is
-- 0 = Monday. Only completed pebbles count. Computed on read.
--
-- Output (an absent section's keys are omitted, never nulled):
--   {"month": "2026-10-01", "days_in_month": 31, "first_weekday": 3, "count": 24, "people_count": 6,
--    "sections": ["weeks","milestones","persons","gem","domains","tone","hours"],
--    "cover":   {"days": [{"day": 0, "pebbles": [{"moment_id","emotion_id","intensity","polarity"}]}],
--                "souls_in_order": [...], "inks": ["joy",...], "papers": [domain ids, top 3]},
--    "weeks":   [{"week_start", "from", "to", "count", "soul_ids", "days": [[moments, top intensity] × 7]}],
--    "milestones": [moment ids],
--    "persons": [{"soul_id", "count", "days", "new_since_previous"}],
--    "previous_month": {"count", "soul_ids"},
--    "gem":     {"worths", "shares", "main", "rotation", "facets", "lead": {"order", "zero"}},
--    "fine_emotions": [{"family", "emotion_id", "count", "days"}],
--    "domains": [{"domain_id", "count", "days"}],
--    "tone":    {"days": [[up, even, down] × days_in_month], "verdict", "first", "last"},
--    "hours":   {"items": [{"moment_id", "happened", "collected"}], "busiest": {"start", "count"}}}
--
-- Thresholds: cover and weeks from 1 pebble; milestones from 3 pebbles with a
-- large or medium stone; persons from 3 pebbles (an empty list is the
-- client's invitation); gem from 3 pebbles in 2 families; domains from 3
-- pebbles in 2 domains; tone from 5 pebbles on 3 days; hours from 5 pebbles.
-- Milestones: large stones first, bright → even → heavy, then by time; then
-- medium stones in the same order; never small; at most five. Worth is
-- intensity × charge (bright 1, heavy 1, even ½). Tone verdict, on summed
-- intensities of bright (b) and heavy (h) pebbles: all_even, nothing_heavy,
-- nothing_bright, bright_more_than_two_to_one (b/h ≥ 2.5), bright_two_to_one
-- (≥ 1.8), heavy_outweighed (≤ 1/1.8), else balance.

create or replace function public.path_month_wrap(p_month date, p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  wheel constant text[] := array['joy', 'pride', 'anger', 'fear', 'shame', 'sadness', 'peace'];
  uid uuid := (select auth.uid());
  today date;
  last_day date;
  ndays int;
  from_ts timestamptz;
  to_ts timestamptz;
  prev_from timestamptz;
  n_pebbles int;
  n_people int;
  n_days int;
  n_families int;
  n_domains int;
  n_milestone_stones int;
  j_cover jsonb;
  j_weeks jsonb;
  j_milestones jsonb;
  j_persons jsonb;
  j_previous jsonb;
  j_gem_input jsonb;
  j_worths jsonb;
  j_lead jsonb;
  j_fine jsonb;
  j_domains jsonb;
  j_tone_days jsonb;
  bright float8;
  heavy float8;
  first_pol int;
  last_pol int;
  j_hours jsonb;
  collected float8[];
  verdict text;
  best_start int := 0;
  best_count int := -1;
  a int;
  c int;
  sections text[] := '{}';
  result jsonb;
begin
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if p_month is null or extract(day from p_month) <> 1 then
    raise exception 'invalid_month' using errcode = '22023';
  end if;
  last_day := (p_month + interval '1 month - 1 day')::date;
  ndays := extract(day from last_day)::int;
  from_ts := p_month::timestamp at time zone p_tz;
  to_ts := (p_month + interval '1 month')::timestamp at time zone p_tz;
  prev_from := (p_month - interval '1 month')::timestamp at time zone p_tz;

  with mo as (
    select p.id as pebble_id, m.id as moment_id, m.happened_at,
           (m.happened_at at time zone p_tz)::date as d,
           ((m.happened_at at time zone p_tz)::date - p_month) as dom,
           extract(epoch from (m.happened_at at time zone p_tz)::time) / 3600 as happened_h,
           extract(epoch from (m.created_at at time zone p_tz)::time) / 3600 as collected_h,
           p.intensity, p.positiveness, p.emotion_id, ec.slug as family,
           p.intensity * case when p.positiveness = 0 then 0.5 else 1 end as worth
    from public.moments m
    join public.pebbles p on p.moment_id = m.id and p.user_id = uid
    left join public.emotions e on e.id = p.emotion_id
    left join public.emotion_categories ec on ec.id = e.category_id
    where m.user_id = uid and m.happened_at >= from_ts and m.happened_at < to_ts
  ),
  prev_souls as (
    select ps.soul_id, min(m.happened_at) as first_at, count(distinct p.id) as n
    from public.moments m
    join public.pebbles p on p.moment_id = m.id and p.user_id = uid
    join public.pebble_souls ps on ps.pebble_id = p.id
    where m.user_id = uid and m.happened_at >= prev_from and m.happened_at < from_ts
    group by ps.soul_id
  ),
  souls as (
    select ps.soul_id, count(*) as n, min(x.happened_at) as first_at, jsonb_agg(distinct x.dom order by x.dom) as days
    from mo x join public.pebble_souls ps on ps.pebble_id = x.pebble_id
    group by ps.soul_id
  ),
  fams as (
    select family, count(*) as n, sum(worth) as worth, min(happened_at) as first_at
    from mo where family is not null
    group by family
  ),
  doms as (
    select pd.domain_id, count(*) as n, min(x.happened_at) as first_at, jsonb_agg(distinct x.dom order by x.dom) as days
    from mo x join public.pebble_domains pd on pd.pebble_id = x.pebble_id
    group by pd.domain_id
  ),
  weeks as (
    select gs::date as ws
    from generate_series(date_trunc('week', p_month), date_trunc('week', last_day), interval '1 week') gs
  )
  select
    (select count(*) from mo),
    (select count(*) from souls),
    (select count(distinct dom) from mo),
    (select count(*) from fams),
    (select count(*) from doms),
    (select count(*) from mo where intensity >= 2),
    jsonb_build_object(
      'days', (select coalesce(jsonb_agg(jsonb_build_object('day', dom, 'pebbles', pebbles) order by dom), '[]'::jsonb)
                 from (select dom, jsonb_agg(jsonb_build_object('moment_id', moment_id, 'emotion_id', emotion_id,
                                                                'intensity', intensity, 'polarity', positiveness)
                                             order by happened_at, moment_id) as pebbles
                       from mo group by dom) per_day),
      'souls_in_order', (select coalesce(jsonb_agg(soul_id order by first_at, soul_id), '[]'::jsonb) from souls),
      'inks', (select coalesce(jsonb_agg(family order by worth desc, n desc, array_position(wheel, family)), '[]'::jsonb) from fams),
      'papers', (select coalesce(jsonb_agg(domain_id order by n desc, first_at, domain_id), '[]'::jsonb)
                   from (select * from doms order by n desc, first_at, domain_id limit 3) top)),
    (select coalesce(jsonb_agg(jsonb_build_object(
              'week_start', w.ws,
              'from', greatest(w.ws, p_month) - p_month,
              'to', least(w.ws + 6, last_day) - p_month,
              'count', (select count(*) from mo where d between w.ws and w.ws + 6),
              'soul_ids', (select coalesce(jsonb_agg(soul_id order by first_at, soul_id), '[]'::jsonb)
                             from (select ps.soul_id, min(x.happened_at) as first_at
                                   from mo x join public.pebble_souls ps on ps.pebble_id = x.pebble_id
                                   where x.d between w.ws and w.ws + 6 group by ps.soul_id) ws_souls),
              'days', (select jsonb_agg(jsonb_build_array(
                                (select count(*) from mo where d = w.ws + i),
                                (select coalesce(max(intensity), 0) from mo where d = w.ws + i)) order by i)
                         from generate_series(0, 6) i))
            order by w.ws), '[]'::jsonb) from weeks w),
    (select coalesce(jsonb_agg(moment_id order by rk), '[]'::jsonb)
       from (select moment_id, row_number() over (order by intensity desc, positiveness desc, happened_at, moment_id) as rk
             from mo where intensity >= 2) ranked where rk <= 5),
    (select coalesce(jsonb_agg(jsonb_build_object('soul_id', s.soul_id, 'count', s.n, 'days', s.days,
                                                  'new_since_previous', not exists (select 1 from prev_souls ps where ps.soul_id = s.soul_id))
                               order by s.n desc, s.first_at, s.soul_id), '[]'::jsonb) from souls s),
    jsonb_build_object(
      'count', (select count(*) from public.moments m join public.pebbles p on p.moment_id = m.id and p.user_id = uid
                 where m.user_id = uid and m.happened_at >= prev_from and m.happened_at < from_ts),
      'soul_ids', (select coalesce(jsonb_agg(soul_id order by first_at, soul_id), '[]'::jsonb) from prev_souls)),
    (select coalesce(jsonb_object_agg(family, jsonb_build_object('worth', worth::float8, 'count', n)), '{}'::jsonb) from fams),
    (select coalesce(jsonb_object_agg(family, worth::float8), '{}'::jsonb) from fams),
    jsonb_build_object(
      'order', (select coalesce(jsonb_agg(family order by worth desc, n desc, array_position(wheel, family)), '[]'::jsonb) from fams),
      'zero', (select coalesce(jsonb_agg(k order by array_position(wheel, k)), '[]'::jsonb)
                 from unnest(wheel) k where not exists (select 1 from fams where family = k))),
    (select coalesce(jsonb_agg(jsonb_build_object('family', fe.family, 'emotion_id', fe.emotion_id, 'count', fe.n, 'days', fe.days)
                               order by f.worth desc, f.n desc, array_position(wheel, fe.family), fe.n desc, fe.first_at, fe.emotion_id), '[]'::jsonb)
       from (select family, emotion_id, count(*) as n, min(happened_at) as first_at, jsonb_agg(distinct dom order by dom) as days
             from mo where family is not null group by family, emotion_id) fe
       join fams f on f.family = fe.family),
    (select coalesce(jsonb_agg(jsonb_build_object('domain_id', domain_id, 'count', n, 'days', days)
                               order by n desc, first_at, domain_id), '[]'::jsonb) from doms),
    (select jsonb_agg(jsonb_build_array(
              (select coalesce(sum(intensity), 0) from mo where dom = i and positiveness = 1),
              (select coalesce(sum(intensity), 0) from mo where dom = i and positiveness = 0),
              (select coalesce(sum(intensity), 0) from mo where dom = i and positiveness = -1)) order by i)
       from generate_series(0, ndays - 1) i),
    (select coalesce(sum(intensity), 0) from mo where positiveness = 1),
    (select coalesce(sum(intensity), 0) from mo where positiveness = -1),
    (select positiveness from mo order by happened_at, moment_id limit 1),
    (select positiveness from mo order by happened_at desc, moment_id desc limit 1),
    (select coalesce(jsonb_agg(jsonb_build_object('moment_id', moment_id,
                                                  'happened', round(happened_h::numeric, 4)::float8,
                                                  'collected', round(collected_h::numeric, 4)::float8)
                               order by happened_at, moment_id), '[]'::jsonb) from mo),
    (select coalesce(array_agg(collected_h::float8), '{}') from mo)
  into n_pebbles, n_people, n_days, n_families, n_domains, n_milestone_stones,
       j_cover, j_weeks, j_milestones, j_persons, j_previous, j_gem_input, j_worths, j_lead,
       j_fine, j_domains, j_tone_days, bright, heavy, first_pol, last_pol, j_hours, collected;

  if n_pebbles >= 1 then sections := array_append(sections, 'weeks'); end if;
  if n_pebbles >= 3 and n_milestone_stones >= 1 then sections := array_append(sections, 'milestones'); end if;
  if n_pebbles >= 3 then sections := array_append(sections, 'persons'); end if;
  if n_pebbles >= 3 and n_families >= 2 then sections := array_append(sections, 'gem'); end if;
  if n_pebbles >= 3 and n_domains >= 2 then sections := array_append(sections, 'domains'); end if;
  if n_pebbles >= 5 and n_days >= 3 then sections := array_append(sections, 'tone'); end if;
  if n_pebbles >= 5 then sections := array_append(sections, 'hours'); end if;

  result := jsonb_build_object(
    'month', p_month,
    'days_in_month', ndays,
    'first_weekday', extract(isodow from p_month)::int - 1,
    'count', n_pebbles,
    'people_count', n_people,
    'sections', to_jsonb(sections));
  if n_pebbles >= 1 then result := result || jsonb_build_object('cover', j_cover); end if;
  if 'weeks' = any(sections) then result := result || jsonb_build_object('weeks', j_weeks); end if;
  if 'milestones' = any(sections) then result := result || jsonb_build_object('milestones', j_milestones); end if;
  if 'persons' = any(sections) then
    result := result || jsonb_build_object('persons', j_persons, 'previous_month', j_previous);
  end if;
  if 'gem' = any(sections) then
    result := result || jsonb_build_object(
      'gem', public.gem_allocate(j_gem_input) || jsonb_build_object('worths', j_worths, 'lead', j_lead),
      'fine_emotions', j_fine);
  end if;
  if 'domains' = any(sections) then result := result || jsonb_build_object('domains', j_domains); end if;
  if 'tone' = any(sections) then
    verdict := case
      when bright = 0 and heavy = 0 then 'all_even'
      when heavy = 0 then 'nothing_heavy'
      when bright = 0 then 'nothing_bright'
      when bright / heavy >= 2.5 then 'bright_more_than_two_to_one'
      when bright / heavy >= 1.8 then 'bright_two_to_one'
      when bright / heavy <= 1 / 1.8 then 'heavy_outweighed'
      else 'balance'
    end;
    result := result || jsonb_build_object('tone', jsonb_build_object(
      'days', j_tone_days, 'verdict', verdict, 'first', first_pol, 'last', last_pol));
  end if;
  if 'hours' = any(sections) then
    -- the four-hour window, starting on the hour, that holds the most collections (the earliest on a tie)
    for a in 0..23 loop
      select count(*) into c from unnest(collected) h where (h - a) - 24 * floor((h - a) / 24) < 4;
      if c > best_count then best_count := c; best_start := a; end if;
    end loop;
    result := result || jsonb_build_object('hours', jsonb_build_object(
      'items', j_hours, 'busiest', jsonb_build_object('start', best_start, 'count', best_count)));
  end if;
  return result;
end;
$$;

comment on function public.path_month_wrap(date, text) is
  'One month''s page (#1207): the sections that exist (thresholds), the cover, weeks, milestones, persons with who is new, the gem (via gem_allocate), fine emotions, domains, the tone with its verdict and the hours. Bucketed in p_tz; computed on read.';

revoke all on function public.path_month_wrap(date, text) from public, anon;
grant execute on function public.path_month_wrap(date, text) to authenticated;
