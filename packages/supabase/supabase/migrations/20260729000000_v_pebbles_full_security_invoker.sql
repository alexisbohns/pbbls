-- Migration: v_pebbles_full security_invoker (fix for #616)
--
-- v_pebbles_full was created (20260411000002_views.sql) and recreated
-- (20260701114205_drop_glyph_shape.sql) without `security_invoker`. Postgres
-- views default to DEFINER rights, so the view ran as its owner and bypassed
-- RLS on every underlying table. Combined with Supabase's default privileges
-- (public-schema objects are granted to anon + authenticated), the view was
-- readable by ANYONE holding the publishable anon key, with no session at all:
--
--   GET /rest/v1/v_pebbles_full?select=*   ->  200, 182 rows, 20 distinct users
--   GET /rest/v1/pebbles?select=*          ->  200, [] (table RLS held)
--
-- i.e. names, descriptions, emotions, intensities, cards, souls, domains and
-- snap storage paths for every user's private pebbles were public. The only
-- thing scoping reads was the client-side .eq("user_id", ...) in
-- apps/web/lib/data/supabase-provider.ts. Same vulnerability class already
-- patched on v_ripple in 20260516000001_v_ripple_security_filter.sql.
--
-- Fix: recreate with security_invoker = true so the caller's RLS applies
-- (pattern: v_glyph_market, 20260630003348_glyph_marketplace.sql), plus an
-- explicit revoke/grant so anon cannot read the view even if the reloption is
-- ever lost in a future CREATE OR REPLACE.
--
-- No functional change for legitimate reads:
--   - pebbles/snaps/souls/collections  -> user_id = auth.uid()
--   - pebble_cards/_souls/_domains/collection_pebbles -> owner via parent join
--   - emotions/domains                 -> `using (true)` reference data, so the
--                                         inner join on emotions cannot blank rows
--   - glyphs                           -> glyphs_select allows own ∪ system ∪
--                                         approved ∪ entitled, a superset of
--                                         can_use_glyph (20260712000000), which
--                                         gates what may be attached to a pebble
--   - service_role bypasses RLS, so admin/analytics reads are unaffected.

drop view if exists public.v_pebbles_full;

create view public.v_pebbles_full
with (security_invoker = true) as
select
  p.id,
  p.user_id,
  p.name,
  p.description,
  p.happened_at,
  p.intensity,
  p.positiveness,
  p.visibility,
  p.emotion_id,
  p.glyph_id,
  p.created_at,
  p.updated_at,

  -- emotion (1:1, always present)
  jsonb_build_object(
    'id',    e.id,
    'slug',  e.slug,
    'name',  e.name,
    'color', e.color
  ) as emotion,

  -- glyph (1:1, optional)
  case when g.id is not null then
    jsonb_build_object(
      'id',       g.id,
      'name',     g.name,
      'strokes',  g.strokes,
      'view_box', g.view_box
    )
  else null end as glyph,

  -- cards (1:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',         pc.id,
        'species_id', pc.species_id,
        'value',      pc.value,
        'sort_order', pc.sort_order
      ) order by pc.sort_order
    )
    from public.pebble_cards pc
    where pc.pebble_id = p.id),
    '[]'::jsonb
  ) as cards,

  -- souls (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',   s.id,
        'name', s.name
      ) order by s.name
    )
    from public.pebble_souls ps
    join public.souls s on s.id = ps.soul_id
    where ps.pebble_id = p.id),
    '[]'::jsonb
  ) as souls,

  -- domains (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',    d.id,
        'slug',  d.slug,
        'name',  d.name,
        'label', d.label
      ) order by d.slug
    )
    from public.pebble_domains pd
    join public.domains d on d.id = pd.domain_id
    where pd.pebble_id = p.id),
    '[]'::jsonb
  ) as domains,

  -- snaps (1:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',           sn.id,
        'storage_path', sn.storage_path,
        'sort_order',   sn.sort_order
      ) order by sn.sort_order
    )
    from public.snaps sn
    where sn.pebble_id = p.id),
    '[]'::jsonb
  ) as snaps,

  -- collections (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',   c.id,
        'name', c.name,
        'mode', c.mode
      ) order by c.name
    )
    from public.collection_pebbles cp
    join public.collections c on c.id = cp.collection_id
    where cp.pebble_id = p.id),
    '[]'::jsonb
  ) as collections

from public.pebbles p
join public.emotions e on e.id = p.emotion_id
left join public.glyphs g on g.id = p.glyph_id;

-- Defense in depth: the anon role has no legitimate read on private pebbles.
revoke all on public.v_pebbles_full from public, anon;
grant select on public.v_pebbles_full to authenticated;
