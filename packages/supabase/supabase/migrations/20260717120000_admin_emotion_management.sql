-- Admin emotion & palette management (#608)
-- Adds is_admin-gated SECURITY DEFINER RPCs that back a new back-office tab:
--   • edit each emotion category's colour palette (primary, secondary, light,
--     shaded, dark), and
--   • edit each emotion's emoji.
--
-- Model recap:
--   • public.emotion_categories holds an inlined palette. All colours are
--     8-digit hex (#RRGGBBAA). `surface_color` is derived by convention as
--     primary RGB + `1A` alpha (~10%) — see 20260506000000_emotion_categories.
--     The editor exposes the five hand-tuned variants named in #608; surface is
--     re-derived here on every save so it can never drift from primary.
--   • public.emotions.emoji (text, not null) is the picker glyph seeded in
--     20260509000002_emotions_picker_data.
--
-- Read paths reuse the existing public.v_emotions_with_palette view for app
-- consumption; these admin_* RPCs are the editor's own read/write path, gated to
-- admins like admin_list_domains / admin_update_domain (#518).

-- ============================================================
-- 1. admin_list_emotion_categories — rows for the Palettes tab
-- ============================================================
create or replace function public.admin_list_emotion_categories()
returns table (
  id uuid,
  slug text,
  name text,
  primary_color text,
  secondary_color text,
  light_color text,
  surface_color text,
  shaded_color text,
  dark_color text
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  return query
    select c.id, c.slug, c.name,
           c.primary_color, c.secondary_color, c.light_color,
           c.surface_color, c.shaded_color, c.dark_color
    from public.emotion_categories c
    order by c.name;
end;
$$;

-- ============================================================
-- 2. admin_update_emotion_palette — edit a category's palette
-- ============================================================
-- Updates the five hand-tuned variants from #608 and re-derives surface_color
-- from the new primary (RGB + `1A` alpha) so surface never drifts. Each colour
-- must be 8-digit hex (#RRGGBBAA); values are normalised to uppercase to match
-- the FF-padded seed convention.
create or replace function public.admin_update_emotion_palette(
  p_category_id uuid,
  p_primary text,
  p_secondary text,
  p_light text,
  p_shaded text,
  p_dark text
)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  if p_primary   !~ '^#[0-9A-Fa-f]{8}$'
     or p_secondary !~ '^#[0-9A-Fa-f]{8}$'
     or p_light     !~ '^#[0-9A-Fa-f]{8}$'
     or p_shaded    !~ '^#[0-9A-Fa-f]{8}$'
     or p_dark      !~ '^#[0-9A-Fa-f]{8}$' then
    raise exception 'bad_color';
  end if;

  update public.emotion_categories
     set primary_color   = upper(p_primary),
         secondary_color = upper(p_secondary),
         light_color     = upper(p_light),
         shaded_color    = upper(p_shaded),
         dark_color      = upper(p_dark),
         -- surface = primary RGB + 1A alpha (convention, ~10%)
         surface_color   = upper(substr(p_primary, 1, 7)) || '1A'
   where id = p_category_id;

  if not found then
    raise exception 'not_found';
  end if;
end;
$$;

-- ============================================================
-- 3. admin_list_emotions — rows for the Emojis tab
-- ============================================================
-- Emotions joined to their category (name + primary colour) so the tab can
-- group and tint rows without a second query.
create or replace function public.admin_list_emotions()
returns table (
  id uuid,
  slug text,
  name text,
  emoji text,
  category_id uuid,
  category_slug text,
  category_name text,
  category_primary_color text
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  return query
    select e.id, e.slug, e.name, e.emoji,
           c.id, c.slug, c.name, c.primary_color
    from public.emotions e
    join public.emotion_categories c on c.id = e.category_id
    order by c.name, e.name;
end;
$$;

-- ============================================================
-- 4. admin_update_emotion_emoji — edit one emotion's emoji
-- ============================================================
create or replace function public.admin_update_emotion_emoji(
  p_emotion_id uuid,
  p_emoji text
)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_emoji text := btrim(coalesce(p_emoji, ''));
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  -- Non-empty and short: a single emoji may be several codepoints (ZWJ
  -- sequences, variation selectors), so cap on length rather than one grapheme.
  if v_emoji = '' or char_length(v_emoji) > 16 then
    raise exception 'bad_emoji';
  end if;

  update public.emotions set emoji = v_emoji where id = p_emotion_id;
  if not found then
    raise exception 'not_found';
  end if;
end;
$$;

-- ============================================================
-- 5. Grants: authenticated only; the is_admin guard does the gating.
-- ============================================================
revoke all on function public.admin_list_emotion_categories()                       from public, anon;
revoke all on function public.admin_update_emotion_palette(uuid, text, text, text, text, text) from public, anon;
revoke all on function public.admin_list_emotions()                                 from public, anon;
revoke all on function public.admin_update_emotion_emoji(uuid, text)                from public, anon;
grant execute on function public.admin_list_emotion_categories()                       to authenticated;
grant execute on function public.admin_update_emotion_palette(uuid, text, text, text, text, text) to authenticated;
grant execute on function public.admin_list_emotions()                                 to authenticated;
grant execute on function public.admin_update_emotion_emoji(uuid, text)                to authenticated;
