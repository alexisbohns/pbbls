-- =============================================================================
-- get_pebble_enrichment gains the moment count and the completion rate (#1051)
--
-- Since the moments/pebbles split (spec 2026-10-11, D2) a moment can be saved
-- bare and completed into a pebble later, so the admin dashboard shows how many
-- moments were recorded beside how many pebbles, plus the share of those
-- moments that became a pebble.
--
-- Body from 20261012090300 §6 (its newest emission) with exactly these edits:
--   * two OUT columns appended: total_moments, pct_moments_completed;
--   * the moments of the window are counted up front into two variables;
--   * the HAVING keeps the row when the window holds moments but no pebbles
--     (a bare-moment week is a state worth seeing, not "no data").
-- The pebble columns and their computation are unchanged.
--
-- Completion is cohort-based: of the moments CREATED in the window, the share
-- that carry at least one pebble, whenever that pebble came. Dividing the
-- window's pebbles by the window's moments instead would drift past 100% at
-- the window's left edge (a pebble completing an older moment). While a moment
-- has one pebble at most (pebbles_moment_user_key, owner only until
-- participants ship), the two readings agree away from the edges.
--
-- The return type changes, so this drops and recreates the function rather
-- than `create or replace`, and re-applies the grant of 20260501000006 §4. The
-- is_admin gate inside is unchanged.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run. Not a purge_account
-- or remove_connection re-emission.
-- =============================================================================

drop function if exists public.get_pebble_enrichment(date, date);

create function public.get_pebble_enrichment(
  p_start date,
  p_end   date
)
returns table (
  total_pebbles          bigint,
  pct_with_picture       numeric,
  pct_in_collection      numeric,
  pct_with_custom_glyph  numeric,
  pct_with_thought       numeric,
  pct_with_soul          numeric,
  pct_with_intensity     numeric,
  total_moments          bigint,
  pct_moments_completed  numeric
)
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_moments   bigint;
  v_completed bigint;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;

  select count(*),
         count(*) filter (
           where exists (select 1 from public.pebbles p where p.moment_id = m.id)
         )
    into v_moments, v_completed
    from public.moments m
   where m.created_at::date between p_start and p_end;

  return query
  with base as (
    select
      p.id,
      m.description,
      p.intensity,
      exists (select 1 from public.snaps s
                where s.moment_id = p.moment_id)      as has_picture,
      exists (select 1 from public.collection_pebbles cp
                where cp.pebble_id = p.id)            as in_collection,
      exists (select 1 from public.pebble_souls ps
                where ps.pebble_id = p.id)            as has_soul,
      exists (select 1 from public.collection_pebbles cp
                join public.collections c on c.id = cp.collection_id
                join public.glyphs g on g.id = c.glyph_id
                where cp.pebble_id = p.id and g.is_custom) as has_custom_glyph
    from public.pebbles p
    join public.moments m on m.id = p.moment_id
    where p.created_at::date between p_start and p_end
  )
  select
    count(*)::bigint                                                           as total_pebbles,
    round(100.0 * count(*) filter (where has_picture)
                  / nullif(count(*), 0), 1)                                    as pct_with_picture,
    round(100.0 * count(*) filter (where in_collection)
                  / nullif(count(*), 0), 1)                                    as pct_in_collection,
    round(100.0 * count(*) filter (where has_custom_glyph)
                  / nullif(count(*), 0), 1)                                    as pct_with_custom_glyph,
    round(100.0 * count(*) filter (where description is not null
                                     and length(description) > 0)
                  / nullif(count(*), 0), 1)                                    as pct_with_thought,
    round(100.0 * count(*) filter (where has_soul)
                  / nullif(count(*), 0), 1)                                    as pct_with_soul,
    round(100.0 * count(*) filter (where intensity is not null)
                  / nullif(count(*), 0), 1)                                    as pct_with_intensity,
    v_moments                                                                  as total_moments,
    round(100.0 * v_completed / nullif(v_moments, 0), 1)                       as pct_moments_completed
  from base
  having count(*) > 0 or v_moments > 0;
end;
$$;

grant execute on function public.get_pebble_enrichment(date, date) to authenticated;
