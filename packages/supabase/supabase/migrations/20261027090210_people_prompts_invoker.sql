-- Migration: people_prompts runs as the caller (#1254, review of part 2)
-- 20261027090200 created people_prompts as security definer. It reads only the
-- caller's own rows, exactly path_month_wrap's situation, which runs as
-- security invoker so RLS stays the backstop: a future join (shared moments
-- through participants, say) cannot silently widen what is read. Body
-- otherwise verbatim from 20261027090200, with search_path = '' and every
-- relation schema-qualified (they already were).
--
-- Two facts about the payload, for the clients:
--   * `pebbles` counts the caller's pebbles whose moment is not hidden by
--     moderation, so an account whose only pebbles are hidden reads as
--     "no pebbles" here while the Path still shows them to their owner.
--   * `skipped` names rejects at least as recent as the oldest prompt shown;
--     with no prompt at all it names the two latest rejects.

create or replace function public.people_prompts(p_limit int default 3)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
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
