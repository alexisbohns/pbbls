-- path_week_wrap: one week's wrap, aggregated on the server (#1207, part 3).
--
-- Everything the week header's tiles and its sheet show, computed once for
-- every client: which tiles exist (the thresholds live here, nowhere else),
-- persons with their days, the invitation's moments when nobody is named,
-- emotion families with worth and share, domains, the weight grid, hours, and
-- what was kept. Ids, not names: clients already hold moment titles (the feed),
-- souls, domains and emotion families (reference data).
--
-- p_week_start must be an ISO Monday; days are 0 = Monday … 6 = Sunday, in
-- p_tz, the reader's IANA zone. Only completed pebbles count. Computed on read.
--
-- Output (absent tiles' keys are omitted, never nulled):
--   {"week_start": "2026-10-05", "iso_week": 41, "iso_year": 2026, "live": false,
--    "count": 11, "people_count": 5,
--    "tiles": ["contents","persons","emotions","domains","weight","hours","kept"],
--    "persons":  [{"soul_id": "…", "count": 4, "days": [0,1,3,5]}],
--    "invite_moment_ids": ["…"],          -- only with the persons tile and nobody named
--    "families": [{"slug": "joy", "count": 3, "worth": 7, "share": 0.35, "days": [0,3,5]}],
--    "lead":     {"order": ["joy","peace",…], "zero": ["shame"]},
--    "domains":  [{"domain_id": "…", "count": 4, "days": [0,1,6]}],
--    "weight":   {"high": [0,2,3], "neutral": [2,1,0], "low": [1,2,0]},   -- by intensity 1..3
--    "hours":    [{"moment_id": "…", "happened": 10.25, "collected": 21.4}],
--    "kept":     {"photo_moment_ids": ["…"], "cards": [{"moment_id": "…", "count": 2}]}}
--
-- Thresholds: contents and persons from 3 pebbles; the stat tiles (emotions,
-- domains, weight, hours, kept) from 5 pebbles, on a week that is over, and
-- only when the stat has at least two distinct values (kept: one photo or one
-- card). Worth is intensity × charge: bright 1, heavy 1, even ½. Rankings break
-- ties by first appearance; families by worth, then pebbles, then the wheel
-- order joy · pride · anger · fear · shame · sadness · peace.

create or replace function public.path_week_wrap(p_week_start date, p_tz text)
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
  live boolean;
  from_ts timestamptz;
  to_ts timestamptz;
  n_pebbles int;
  n_people int;
  n_families int;
  n_domains int;
  n_weights int;
  n_hours int;
  n_kept int;
  j_persons jsonb;
  j_invite jsonb;
  j_families jsonb;
  j_lead jsonb;
  j_domains jsonb;
  j_weight jsonb;
  j_hours jsonb;
  j_kept jsonb;
  tiles text[] := '{}';
  result jsonb;
begin
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if p_week_start is null or extract(isodow from p_week_start) <> 1 then
    raise exception 'invalid_week_start' using errcode = '22023';
  end if;
  live := today between p_week_start and p_week_start + 6;
  from_ts := p_week_start::timestamp at time zone p_tz;
  to_ts := (p_week_start + 7)::timestamp at time zone p_tz;

  with wk as (
    select p.id as pebble_id, m.id as moment_id, m.happened_at,
           ((m.happened_at at time zone p_tz)::date - p_week_start) as dow,
           extract(epoch from (m.happened_at at time zone p_tz)::time) / 3600 as happened_h,
           extract(epoch from (m.created_at at time zone p_tz)::time) / 3600 as collected_h,
           p.intensity, p.positiveness, ec.slug as family,
           p.intensity * case when p.positiveness = 0 then 0.5 else 1 end as worth
    from public.moments m
    join public.pebbles p on p.moment_id = m.id and p.user_id = uid
    left join public.emotions e on e.id = p.emotion_id
    left join public.emotion_categories ec on ec.id = e.category_id
    where m.user_id = uid and m.happened_at >= from_ts and m.happened_at < to_ts
  ),
  souls as (
    select ps.soul_id, count(*) as n, min(w.happened_at) as first_at,
           jsonb_agg(distinct w.dow order by w.dow) as days
    from wk w join public.pebble_souls ps on ps.pebble_id = w.pebble_id
    group by ps.soul_id
  ),
  fams as (
    select family, count(*) as n, sum(worth) as worth, jsonb_agg(distinct dow order by dow) as days
    from wk where family is not null
    group by family
  ),
  doms as (
    select pd.domain_id, count(*) as n, min(w.happened_at) as first_at, jsonb_agg(distinct w.dow order by w.dow) as days
    from wk w join public.pebble_domains pd on pd.pebble_id = w.pebble_id
    group by pd.domain_id
  ),
  cards as (
    select w.moment_id, w.happened_at, count(*) as n
    from wk w join public.pebble_cards pc on pc.pebble_id = w.pebble_id
    group by w.moment_id, w.happened_at
  ),
  photos as (
    select w.moment_id, w.happened_at
    from wk w where exists (select 1 from public.snaps s where s.moment_id = w.moment_id)
  )
  select
    (select count(*) from wk),
    (select count(*) from souls),
    (select coalesce(jsonb_agg(jsonb_build_object('soul_id', soul_id, 'count', n, 'days', days)
                               order by n desc, first_at, soul_id), '[]'::jsonb) from souls),
    (select coalesce(jsonb_agg(moment_id order by happened_at, moment_id), '[]'::jsonb)
       from (select moment_id, happened_at from wk order by happened_at, moment_id limit 3) f),
    (select count(*) from fams),
    (select coalesce(jsonb_agg(jsonb_build_object('slug', family, 'count', n, 'worth', worth::float8,
                                                  'share', (worth / (select sum(worth) from fams))::float8, 'days', days)
                               order by worth desc, n desc, array_position(wheel, family)), '[]'::jsonb) from fams),
    jsonb_build_object(
      'order', (select coalesce(jsonb_agg(family order by worth desc, n desc, array_position(wheel, family)), '[]'::jsonb) from fams),
      'zero', (select coalesce(jsonb_agg(k order by array_position(wheel, k)), '[]'::jsonb)
                 from unnest(wheel) k where not exists (select 1 from fams where family = k))),
    (select count(*) from doms),
    (select coalesce(jsonb_agg(jsonb_build_object('domain_id', domain_id, 'count', n, 'days', days)
                               order by n desc, first_at, domain_id), '[]'::jsonb) from doms),
    (select count(distinct (positiveness, intensity)) from wk),
    jsonb_build_object(
      'high',    (select jsonb_agg((select count(*) from wk where positiveness = 1 and intensity = i) order by i) from generate_series(1, 3) i),
      'neutral', (select jsonb_agg((select count(*) from wk where positiveness = 0 and intensity = i) order by i) from generate_series(1, 3) i),
      'low',     (select jsonb_agg((select count(*) from wk where positiveness = -1 and intensity = i) order by i) from generate_series(1, 3) i)),
    (select count(distinct floor(happened_h)) from wk),
    (select coalesce(jsonb_agg(jsonb_build_object('moment_id', moment_id,
                                                  'happened', round(happened_h::numeric, 4)::float8,
                                                  'collected', round(collected_h::numeric, 4)::float8)
                               order by happened_at, moment_id), '[]'::jsonb) from wk),
    (select count(*) from photos) + (select count(*) from cards),
    jsonb_build_object(
      'photo_moment_ids', (select coalesce(jsonb_agg(moment_id order by happened_at, moment_id), '[]'::jsonb) from photos),
      'cards', (select coalesce(jsonb_agg(jsonb_build_object('moment_id', moment_id, 'count', n)
                                          order by happened_at, moment_id), '[]'::jsonb) from cards))
  into n_pebbles, n_people, j_persons, j_invite, n_families, j_families, j_lead,
       n_domains, j_domains, n_weights, j_weight, n_hours, j_hours, n_kept, j_kept;

  if n_pebbles >= 3 then
    tiles := array['contents', 'persons'];
    if n_pebbles >= 5 and not live then
      if n_families >= 2 then tiles := array_append(tiles, 'emotions'); end if;
      if n_domains >= 2 then tiles := array_append(tiles, 'domains'); end if;
      if n_weights >= 2 then tiles := array_append(tiles, 'weight'); end if;
      if n_hours >= 2 then tiles := array_append(tiles, 'hours'); end if;
      if n_kept >= 1 then tiles := array_append(tiles, 'kept'); end if;
    end if;
  end if;

  result := jsonb_build_object(
    'week_start', p_week_start,
    'iso_week', extract(week from p_week_start)::int,
    'iso_year', extract(isoyear from p_week_start)::int,
    'live', live,
    'count', n_pebbles,
    'people_count', n_people,
    'tiles', to_jsonb(tiles));
  if 'persons' = any(tiles) then
    result := result || jsonb_build_object('persons', j_persons);
    if n_people = 0 then result := result || jsonb_build_object('invite_moment_ids', j_invite); end if;
  end if;
  if 'emotions' = any(tiles) then result := result || jsonb_build_object('families', j_families, 'lead', j_lead); end if;
  if 'domains' = any(tiles) then result := result || jsonb_build_object('domains', j_domains); end if;
  if 'weight' = any(tiles) then result := result || jsonb_build_object('weight', j_weight); end if;
  if 'hours' = any(tiles) then result := result || jsonb_build_object('hours', j_hours); end if;
  if 'kept' = any(tiles) then result := result || jsonb_build_object('kept', j_kept); end if;
  return result;
end;
$$;

comment on function public.path_week_wrap(date, text) is
  'One week''s wrap (#1207): the tiles that exist (thresholds), persons, the invitation, families with worth and share, domains, weight, hours and kept. Bucketed in p_tz; computed on read.';

revoke all on function public.path_week_wrap(date, text) from public, anon;
grant execute on function public.path_week_wrap(date, text) to authenticated;
