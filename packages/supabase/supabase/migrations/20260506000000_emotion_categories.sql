-- Migration: Emotion categories and color palettes — Phase 1 (schema only)
-- Issue: https://github.com/Bohns/pbbls/issues/366
-- Spec:  docs/superpowers/specs/2026-05-06-emotion-categories-palettes-design.md
--
-- Notes:
--   - Adds public.emotion_categories with an inlined 4-color palette.
--     All palette colors are 8-digit hex (#RRGGBBAA). Opaque colors are
--     FF-padded; surface is seeded by convention as primary + 1A (10% alpha).
--   - Adds public.emotions.category_id as nullable. The NOT NULL constraint
--     follows in a separate Phase 2 migration once data has been populated
--     manually in Supabase Studio.
--   - Leaves public.emotions.color untouched (soft-deprecated). Shipped iOS
--     clients still read it; new clients will use the view below.
--   - public.v_emotions_with_palette is an INNER JOIN — emotions with a null
--     category_id are excluded from view results until Phase 2 lands. No
--     client consumes the view in this PR, so the partial-list-during-rollout
--     window is internal-state only.

-- ============================================================
-- TABLES
-- ============================================================

create table public.emotion_categories (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  primary_color text not null,
  secondary_color text not null,
  light_color text not null,
  surface_color text not null
);

-- ============================================================
-- ALTER EXISTING
-- ============================================================

alter table public.emotions
  add column category_id uuid references public.emotion_categories(id);

create index emotions_category_id_idx
  on public.emotions (category_id);

-- ============================================================
-- ACCESS CONTROL
-- ============================================================

alter table public.emotion_categories enable row level security;

create policy "emotion_categories_select" on public.emotion_categories
  for select using (true);

-- ============================================================
-- VIEWS
-- ============================================================

create view public.v_emotions_with_palette as
select
  e.id,
  e.slug,
  e.name,
  e.color,
  c.id              as category_id,
  c.slug            as category_slug,
  c.name            as category_name,
  c.primary_color,
  c.secondary_color,
  c.light_color,
  c.surface_color
from public.emotions e
join public.emotion_categories c on c.id = e.category_id;
