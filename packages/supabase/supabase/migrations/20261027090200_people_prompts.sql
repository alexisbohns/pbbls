-- Migration: people_prompts (#1254, spec 2026-10-26 §4.4, D10)
-- The People tab's onboarding asks "who was there?" about the user's own
-- pebbles. The rule lives here so no client re-implements it:
--   * the pebble has no soul,
--   * at least one of its domains is OUTWARD (the constant below),
--   * its positiveness is high (1) or neutral (0): a heavy moment is never
--     asked about, whatever its domain,
--   * its moment is not hidden by moderation.
-- Latest first by happened_at. `skipped` names up to two more recent soul-less
-- pebbles the rule left out, so the client can say so in one line.
-- Totals let the client pick its state (no pebbles / prompts / small card).

create or replace function public.people_prompts(p_limit int default 3)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  -- The outward set (spec D10). A change here is a product decision, not a
  -- tuning: record it in docs/decisions/log.md.
  outward constant text[] := array['family','friends','partner','community','currentevents','dating','hobbies','fitness','travel'];
  result jsonb;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_limit is null or p_limit < 1 or p_limit > 10 then
    raise exception 'invalid_limit' using errcode = '22023';
  end if;

  with mine as (
    select p.id as pebble_id, m.id as moment_id, m.name, m.happened_at,
           p.intensity, p.positiveness, p.emotion_id,
           exists (select 1 from public.pebble_souls ps where ps.pebble_id = p.id) as has_soul,
           exists (select 1 from public.pebble_domains pd
                     join public.domains d on d.id = pd.domain_id
                    where pd.pebble_id = p.id and d.slug = any (outward)) as is_outward
    from public.pebbles p
    join public.moments m on m.id = p.moment_id
    where p.user_id = v_uid and m.hidden_at is null
  ),
  soulless as (select * from mine where not has_soul),
  eligible as (
    select * from soulless where is_outward and positiveness >= 0
    order by happened_at desc, pebble_id limit p_limit
  ),
  skipped as (
    select name from soulless s
    where not (s.is_outward and s.positiveness >= 0)
      and s.happened_at >= coalesce((select min(happened_at) from eligible), s.happened_at)
    order by happened_at desc limit 2
  )
  select jsonb_build_object(
    'pebbles', (select count(*) from mine),
    'souls', (select count(*) from public.souls s where s.user_id = v_uid),
    'prompts', (select coalesce(jsonb_agg(jsonb_build_object(
                  'pebble_id', pebble_id, 'moment_id', moment_id, 'name', name,
                  'happened_at', to_char(happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                  'intensity', intensity, 'positiveness', positiveness, 'emotion_id', emotion_id)
                  order by happened_at desc, pebble_id), '[]'::jsonb) from eligible),
    'skipped', (select coalesce(jsonb_agg(jsonb_build_object('name', name)), '[]'::jsonb) from skipped))
  into result;
  return result;
end;
$$;

revoke all on function public.people_prompts(int) from public, anon;
grant execute on function public.people_prompts(int) to authenticated;

comment on function public.people_prompts(int) is
  'The People tab''s onboarding prompts (#1254): the caller''s latest soul-less pebbles with an outward domain and a non-heavy positiveness, plus totals and the names the rule skipped. Whole-second UTC timestamps.';
