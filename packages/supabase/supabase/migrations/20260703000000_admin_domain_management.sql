-- Admin domain management (#518)
-- Adds a read view exposing each domain's glyph, plus is_admin-gated RPCs to
-- edit a domain's name/label and to set/replace its glyph in place.
--
-- Model recap:
--   • domains.default_glyph_id -> glyphs(id); the glyph is SYSTEM-OWNED
--     (glyphs.user_id = NULL, shape_id = NULL — both nullable since the
--     remote-pebble-engine migration).
--   • "Description" is the existing domains.label column (no new column).
--   • Replace-in-place keeps ONE glyph per domain (no orphans) so every
--     consumer (compose-pebble edge fallback, web cache) reflects the change.

-- ============================================================
-- 1. Read view: domains + their glyph (web/iOS consumption)
-- ============================================================
-- security_invoker so the caller's glyphs RLS governs; domain glyphs are
-- system-owned (NULL user_id) and therefore readable. Mirrors v_glyph_market.
create view public.v_domains_with_glyph with (security_invoker = true) as
select
  d.id,
  d.slug,
  d.name,
  d.label,
  d.default_glyph_id,
  g.strokes,
  g.view_box
from public.domains d
left join public.glyphs g on g.id = d.default_glyph_id;

grant select on public.v_domains_with_glyph to anon, authenticated;

-- ============================================================
-- 2. admin_list_domains — rows for the admin editor
-- ============================================================
create or replace function public.admin_list_domains()
returns table (
  id uuid,
  slug text,
  name text,
  label text,
  default_glyph_id uuid,
  strokes jsonb,
  view_box text
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  return query
    select d.id, d.slug, d.name, d.label, d.default_glyph_id, g.strokes, g.view_box
    from public.domains d
    left join public.glyphs g on g.id = d.default_glyph_id
    order by d.name;
end;
$$;

-- ============================================================
-- 3. admin_update_domain — edit name + description (label)
-- ============================================================
create or replace function public.admin_update_domain(
  p_domain_id uuid,
  p_name text,
  p_label text
)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if btrim(coalesce(p_name, '')) = '' then
    raise exception 'bad_name';
  end if;
  update public.domains
     set name = btrim(p_name),
         label = btrim(coalesce(p_label, ''))
   where id = p_domain_id;
  if not found then
    raise exception 'not_found';
  end if;
end;
$$;

-- ============================================================
-- 4. admin_set_domain_glyph — set/replace the domain glyph in place
-- ============================================================
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
    insert into public.glyphs (user_id, shape_id, name, strokes, view_box)
    values (null, null, null, p_strokes, p_view_box)
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

-- ============================================================
-- 5. Grants: authenticated only; the is_admin guard does the gating.
-- ============================================================
revoke all on function public.admin_list_domains()                      from public, anon;
revoke all on function public.admin_update_domain(uuid, text, text)     from public, anon;
revoke all on function public.admin_set_domain_glyph(uuid, jsonb, text) from public, anon;
grant execute on function public.admin_list_domains()                      to authenticated;
grant execute on function public.admin_update_domain(uuid, text, text)     to authenticated;
grant execute on function public.admin_set_domain_glyph(uuid, jsonb, text) to authenticated;
