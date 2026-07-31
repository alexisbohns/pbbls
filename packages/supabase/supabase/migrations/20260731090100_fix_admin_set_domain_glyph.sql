-- =============================================================================
-- admin_set_domain_glyph: drop the shape_id insert (#687)
-- =============================================================================
-- 20260701114205 dropped glyphs.shape_id and updated every function that
-- referenced it. 20260703000000_admin_domain_management.sql landed two days
-- later still carrying `insert into public.glyphs (user_id, shape_id, ...)`:
-- a plpgsql body is parsed but not name-resolved at creation time, so the
-- migration applied cleanly and the breakage only surfaces at call time.
--
-- Only the first-glyph branch is affected — the replace-in-place branch never
-- touches the column, and every live domain already has a default glyph, which
-- is why `column "shape_id" does not exist` has gone unnoticed. Found while
-- copying this function as the precedent for admin_set_achievement_glyph
-- (20260730150000), which correctly omits it.
--
-- Body otherwise verbatim from 20260703000000. Grants are unchanged
-- (authenticated only, gated by is_admin); create or replace preserves them.
-- =============================================================================

create or replace function public.admin_set_domain_glyph(
  p_domain_id uuid,
  p_strokes jsonb,
  p_view_box text
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_glyph_id uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  select default_glyph_id into v_glyph_id
  from public.domains where id = p_domain_id;
  if not found then
    raise exception 'not_found';
  end if;

  if v_glyph_id is null then
    -- First glyph for this domain: system-owned (NULL user_id), shapeless.
    insert into public.glyphs (user_id, name, strokes, view_box)
    values (null, null, p_strokes, p_view_box)
    returning id into v_glyph_id;

    update public.domains set default_glyph_id = v_glyph_id where id = p_domain_id;
  else
    -- Replace in place: same glyph_id, so FKs and caches keep pointing at it.
    update public.glyphs
       set strokes = p_strokes,
           view_box = p_view_box,
           updated_at = now()
     where id = v_glyph_id;
  end if;

  return v_glyph_id;
end;
$$;
