-- path_strip: the Path's strip of weeks and months (#1207, part 2).
--
-- One call returns everything the strip draws, already decided on the server:
-- every week from the reader's first moment to the current week (or to the
-- last moment's week, if a moment is dated later), oldest first, with its
-- cairn weights; and a month item after the week that holds each completed
-- month's last day, for months with at least one pebble.
--
-- Days, weeks and months are bucketed in p_tz, the reader's IANA zone (no zone
-- is stored on the profile; clients send the device's). Weeks are ISO weeks,
-- starting on Monday. Computed on read: nothing is stored, so a write is
-- visible on the next call.
--
-- Output:
--   {"items": [
--     {"kind": "week", "week_start": "2026-08-10", "iso_week": 33, "iso_year": 2026,
--      "count": 3, "focusable": true, "live": false,
--      "days": [[0,0],[0,0],[2,3],[0,0],[1,0],[0,0],[0,0]]},   -- Monday first
--     {"kind": "month", "month": "2026-08-01", "count": 3, "focusable": true},
--     ...]}
-- A week's count is its moments (a moment without a pebble still marks its
-- day); days[i] is [moments that day, strongest pebble intensity that day, or
-- 0 when no moment of the day has a pebble]. A week is focusable when it holds
-- a moment or is the current (live) week; an empty past week is a spacer.
-- A month's count is its pebbles.

create or replace function public.path_strip(p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  today date;
  result jsonb;
begin
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;

  with mine as (
    select (m.happened_at at time zone p_tz)::date as d, p.intensity
    from public.moments m
    left join public.pebbles p on p.moment_id = m.id and p.user_id = (select auth.uid())
    where m.user_id = (select auth.uid())
  ),
  bounds as (
    select date_trunc('week', least(coalesce(min(d), today), today))::date as first_week,
           date_trunc('week', greatest(coalesce(max(d), today), today))::date as last_week,
           date_trunc('week', today)::date as live_week
    from mine
  ),
  weeks as (
    select gs::date as week_start
    from bounds, generate_series(bounds.first_week, bounds.last_week, interval '1 week') as gs
  ),
  per_day as (
    select date_trunc('week', d)::date as week_start, (d - date_trunc('week', d)::date) as dow,
           count(*) as n, coalesce(max(intensity), 0) as top
    from mine
    group by 1, 2
  ),
  week_items as (
    select w.week_start,
           jsonb_build_object(
             'kind', 'week',
             'week_start', w.week_start,
             'iso_week', extract(week from w.week_start)::int,
             'iso_year', extract(isoyear from w.week_start)::int,
             'count', coalesce(sum(pd.n), 0)::int,
             'focusable', coalesce(sum(pd.n), 0) > 0 or w.week_start = b.live_week,
             'live', w.week_start = b.live_week,
             'days', (select jsonb_agg(jsonb_build_array(coalesce(x.n, 0), coalesce(x.top, 0)) order by i)
                        from generate_series(0, 6) as i
                        left join per_day x on x.week_start = w.week_start and x.dow = i)
           ) as item
    from weeks w
    cross join bounds b
    left join per_day pd on pd.week_start = w.week_start
    group by w.week_start, b.live_week
  ),
  month_items as (
    select date_trunc('week', (date_trunc('month', d) + interval '1 month - 1 day'))::date as week_start,
           jsonb_build_object('kind', 'month', 'month', date_trunc('month', d)::date,
                              'count', count(*)::int, 'focusable', true) as item
    from mine
    where intensity is not null
      and date_trunc('month', d) < date_trunc('month', today)
    group by date_trunc('month', d)
  )
  select jsonb_build_object('items', coalesce(jsonb_agg(item order by week_start, rank), '[]'::jsonb))
  into result
  from (
    select week_start, 0 as rank, item from week_items
    union all
    select week_start, 1 as rank, item from month_items
  ) items;

  return result;
end;
$$;

comment on function public.path_strip(text) is
  'The Path strip (#1207): every week from the first moment to the current week with cairn day weights, and a month item after the week holding each completed month''s last day. Bucketed in p_tz; computed on read.';

revoke all on function public.path_strip(text) from public, anon;
grant execute on function public.path_strip(text) to authenticated;
