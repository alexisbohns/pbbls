-- =============================================================================
-- Admin achievement management (#668, M48) — the library manager's RPCs (D12)
-- =============================================================================
-- The back-office manages the CATALOG, not the rule engine: new tiers of
-- existing families, copy, visual, karma, retirement. A new *kind* of rule
-- stays a migration plus a new `case` arm in check_achievements(), because the
-- family CHECK is exactly the boundary of what that function can evaluate.
--
-- Design: docs/superpowers/specs/2026-07-29-achievements-design.md (D12).
-- Style: the domain/emotion admin RPCs (20260703000000, 20260717120000) —
-- is_admin-gated `security definer`, `authenticated`-only grants, the guard
-- doing the gating rather than the grant.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. admin_list_achievements — rows for the editor
-- ---------------------------------------------------------------------------
-- Returns the FULL catalog including inactive rows (the client grid filters;
-- the admin must see what it retired), the joined glyph geometry, and a
-- per-badge unlock count so curation is informed by what users actually reach.
create or replace function public.admin_list_achievements()
returns table (
  id uuid,
  slug text,
  family text,
  threshold integer,
  emotion_id uuid,
  emotion_slug text,
  domain_id uuid,
  domain_slug text,
  sort_order integer,
  glyph_id uuid,
  strokes jsonb,
  view_box text,
  karma_reward integer,
  is_active boolean,
  title_en text,
  title_fr text,
  description_en text,
  description_fr text,
  unlock_count bigint
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  return query
    select a.id, a.slug, a.family, a.threshold,
           a.emotion_id, e.slug, a.domain_id, d.slug,
           a.sort_order, a.glyph_id, g.strokes, g.view_box,
           a.karma_reward, a.is_active,
           a.title_en, a.title_fr, a.description_en, a.description_fr,
           (select count(*) from public.achievement_unlocks u
             where u.achievement_id = a.id)
    from public.achievements a
    left join public.emotions e on e.id = a.emotion_id
    left join public.domains  d on d.id = a.domain_id
    left join public.glyphs   g on g.id = a.glyph_id
    order by a.sort_order, a.slug;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. admin_create_achievement — a new tier of an EXISTING family
-- ---------------------------------------------------------------------------
-- Family-bound by construction: the column's CHECK rejects anything
-- check_achievements() cannot evaluate, so the admin can add a
-- `pebble-count-2000` or a new sales rung but not invent a rule.
--
-- Bilingual copy is mandatory here (D7): a runtime-created row is one no
-- client has shipped strings for, so it must carry its own EN and FR.
-- Threshold rules mirror the seed: the counting families need one, the
-- one-shot families must not have one, and emotion_first/domain_first must
-- reference their row (which is what the generated seed does).
-- Signature order is deliberate: what every family needs comes first, and the
-- per-family/optional arguments carry `default null` so a caller omits what does
-- not apply (and the generated TypeScript types mark them optional rather than
-- demanding an explicit null). PostgREST always calls by name, so order is a
-- typing concern only.
create or replace function public.admin_create_achievement(
  p_slug text,
  p_family text,
  p_title_en text,
  p_title_fr text,
  p_karma_reward integer,
  p_sort_order integer,
  p_threshold integer default null,
  p_emotion_id uuid default null,
  p_domain_id uuid default null,
  p_description_en text default null,
  p_description_fr text default null
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_slug text := btrim(coalesce(p_slug, ''));
  v_id   uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  -- Slug shape matches the seeded convention (kebab, lowercase) so the
  -- catalog stays greppable and deterministic ids stay the seed's business.
  if v_slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$' then
    raise exception 'bad_slug';
  end if;
  if btrim(coalesce(p_title_en, '')) = '' or btrim(coalesce(p_title_fr, '')) = '' then
    raise exception 'missing_copy';
  end if;
  if p_karma_reward is null or p_karma_reward < 0 or p_karma_reward > 32767 then
    raise exception 'bad_karma';
  end if;

  -- Threshold/reference coherence per family. The counting families are
  -- meaningless without a positive threshold; the one-shot families are
  -- meaningless with one.
  if p_family in ('pebble_count', 'glyph_count', 'glyph_sales') then
    if p_threshold is null or p_threshold <= 0 then
      raise exception 'bad_threshold';
    end if;
  elsif p_family in ('first_collection', 'first_glyph', 'first_soul') then
    if p_threshold is not null then
      raise exception 'bad_threshold';
    end if;
  elsif p_family = 'emotion_first' then
    if p_emotion_id is null then raise exception 'missing_reference'; end if;
  elsif p_family = 'domain_first' then
    if p_domain_id is null then raise exception 'missing_reference'; end if;
  end if;
  -- Anything else fails on the column CHECK below, which is the authority.

  insert into public.achievements (
    slug, family, threshold, emotion_id, domain_id, sort_order,
    karma_reward, title_en, title_fr, description_en, description_fr
  ) values (
    v_slug, p_family, p_threshold,
    case when p_family = 'emotion_first' then p_emotion_id end,
    case when p_family = 'domain_first'  then p_domain_id  end,
    coalesce(p_sort_order, 900),
    p_karma_reward,
    btrim(p_title_en), btrim(p_title_fr),
    nullif(btrim(coalesce(p_description_en, '')), ''),
    nullif(btrim(coalesce(p_description_fr, '')), '')
  )
  returning id into v_id;

  return v_id;
exception
  when unique_violation then
    raise exception 'duplicate_slug';
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. admin_update_achievement — copy, karma, order, retirement
-- ---------------------------------------------------------------------------
-- Deliberately CANNOT change family/threshold/emotion_id/domain_id: those
-- define what the badge means, and editing them under users who already
-- unlocked it would rewrite history. Retire and re-create instead.
--
-- A karma edit applies to FUTURE unlocks only (D9) — check_achievements()
-- reads karma_reward at unlock time and never re-emits, so nothing here
-- retro-pays or claws back.
create or replace function public.admin_update_achievement(
  p_achievement_id uuid,
  p_title_en text,
  p_title_fr text,
  p_karma_reward integer,
  p_description_en text default null,
  p_description_fr text default null,
  p_sort_order integer default null,
  p_is_active boolean default null
)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if btrim(coalesce(p_title_en, '')) = '' or btrim(coalesce(p_title_fr, '')) = '' then
    raise exception 'missing_copy';
  end if;
  if p_karma_reward is null or p_karma_reward < 0 or p_karma_reward > 32767 then
    raise exception 'bad_karma';
  end if;

  update public.achievements
     set title_en       = btrim(p_title_en),
         title_fr       = btrim(p_title_fr),
         description_en = nullif(btrim(coalesce(p_description_en, '')), ''),
         description_fr = nullif(btrim(coalesce(p_description_fr, '')), ''),
         karma_reward   = p_karma_reward,
         sort_order     = coalesce(p_sort_order, sort_order),
         is_active      = coalesce(p_is_active, is_active)
   where id = p_achievement_id;

  if not found then
    raise exception 'not_found';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. admin_set_achievement_glyph — set/replace the badge visual in place
-- ---------------------------------------------------------------------------
-- The admin_set_domain_glyph pattern (20260703000000): the badge visual is a
-- SYSTEM-OWNED glyph (user_id null), replaced in place on the same id so every
-- consumer and cache reflects the change, and so purge_account never matches
-- it (it only deletes by owner).
--
-- One deliberate divergence from that precedent: it inserts `shape_id`, a
-- column dropped in 20260701114205, so its first-glyph path raises
-- `column "shape_id" does not exist` at runtime. Reported separately rather
-- than patched here; this function omits the column.
create or replace function public.admin_set_achievement_glyph(
  p_achievement_id uuid,
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

  select glyph_id into v_glyph_id
  from public.achievements where id = p_achievement_id;
  if not found then
    raise exception 'not_found';
  end if;

  if v_glyph_id is null then
    insert into public.glyphs (user_id, name, strokes, view_box)
    values (null, null, p_strokes, p_view_box)
    returning id into v_glyph_id;

    update public.achievements set glyph_id = v_glyph_id where id = p_achievement_id;
  else
    update public.glyphs
       set strokes = p_strokes,
           view_box = p_view_box,
           updated_at = now()
     where id = v_glyph_id;
  end if;

  return v_glyph_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. admin_delete_achievement — correction path only
-- ---------------------------------------------------------------------------
-- Retirement is `is_active = false` (D12): unlocks FK the row and badges are
-- permanent (D3), so a badge someone earned can never be deleted. Deleting is
-- reserved for fixing a row created by mistake, and only while nobody holds it.
create or replace function public.admin_delete_achievement(p_achievement_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_unlocks bigint;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  select count(*) into v_unlocks
  from public.achievement_unlocks where achievement_id = p_achievement_id;
  if v_unlocks > 0 then
    raise exception 'has_unlocks';
  end if;

  delete from public.achievements where id = p_achievement_id;
  if not found then
    raise exception 'not_found';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Grants: authenticated only; the is_admin guard does the gating.
-- ---------------------------------------------------------------------------
revoke all on function public.admin_list_achievements()                          from public, anon;
revoke all on function public.admin_create_achievement(text, text, text, text, integer, integer, integer, uuid, uuid, text, text) from public, anon;
revoke all on function public.admin_update_achievement(uuid, text, text, integer, text, text, integer, boolean) from public, anon;
revoke all on function public.admin_set_achievement_glyph(uuid, jsonb, text)      from public, anon;
revoke all on function public.admin_delete_achievement(uuid)                     from public, anon;
grant execute on function public.admin_list_achievements()                          to authenticated;
grant execute on function public.admin_create_achievement(text, text, text, text, integer, integer, integer, uuid, uuid, text, text) to authenticated;
grant execute on function public.admin_update_achievement(uuid, text, text, integer, text, text, integer, boolean) to authenticated;
grant execute on function public.admin_set_achievement_glyph(uuid, jsonb, text)      to authenticated;
grant execute on function public.admin_delete_achievement(uuid)                     to authenticated;
