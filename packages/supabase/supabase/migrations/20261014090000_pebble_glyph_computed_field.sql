-- #1050: a pebble's derived glyph as a PostgREST computed field, so a reader
-- selecting from `pebbles` (web's connection page) gets `glyph` in the same
-- request instead of one resolve_pebble_glyph call per row.
--
-- Invoker, and it calls the public (gated) resolver, so it answers exactly what
-- resolve_pebble_glyph answers for the caller: the row is already readable under
-- pebbles_select when PostgREST evaluates it, and nothing new is exposed.
create function public.glyph(p public.pebbles)
returns jsonb
language sql
security invoker
stable
set search_path = public
as $$
  select public.resolve_pebble_glyph(p.id);
$$;
revoke all on function public.glyph(public.pebbles) from public, anon;
grant execute on function public.glyph(public.pebbles) to authenticated;
comment on function public.glyph(public.pebbles) is
  'Computed field: the pebble''s derived glyph (D5), same answer as resolve_pebble_glyph(p.id) for the caller. Select it as `glyph` on pebbles.';
