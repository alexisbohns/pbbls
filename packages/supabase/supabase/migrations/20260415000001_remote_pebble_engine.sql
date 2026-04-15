-- Migration: Remote Pebble Engine slice 1
-- Issue: #261
--
-- Adds server-side pebble rendering infrastructure:
--   - glyphs: allow system-owned (NULL user_id) and shapeless (NULL shape_id) rows
--   - glyphs RLS: readable when system-owned (NULL user_id)
--   - domains: FK to a default system glyph used as iOS fallback
--   - pebbles: render_svg, render_manifest, render_version columns written by
--     the compose-pebble edge function
--   - Upserts the 18 canonical domains (matches the remote DB, idempotent)
--   - Seeds 18 system glyph rows keyed by domain slug
--   - Links each domain to its default_glyph_id
--
-- Idempotency: every INSERT uses ON CONFLICT DO NOTHING or WHERE NOT EXISTS so
-- re-running against a database that already has some/all of these rows is a
-- safe no-op. The remote DB already has the 18 domains (added out-of-band);
-- local dev currently has 5 outdated Greek-slug domains from
-- 20260411000000_reference_tables.sql which coexist untouched.

-- ============================================================
-- 1. Relax glyphs constraints
-- ============================================================

alter table public.glyphs alter column user_id drop not null;
alter table public.glyphs alter column shape_id drop not null;

-- ============================================================
-- 2. glyphs RLS — allow reading system glyphs (user_id is null)
-- ============================================================

drop policy if exists "glyphs_select" on public.glyphs;
create policy "glyphs_select" on public.glyphs
  for select using (user_id = auth.uid() or user_id is null);

-- Insert/update/delete policies stay user-scoped. Do not relax them here.

-- ============================================================
-- 3. domains: default_glyph_id FK
-- ============================================================

alter table public.domains
  add column default_glyph_id uuid references public.glyphs(id);

-- ============================================================
-- 4. pebbles: render output columns
-- ============================================================

alter table public.pebbles
  add column render_svg text,
  add column render_manifest jsonb,
  add column render_version text;

-- ============================================================
-- 5. Upsert the 18 canonical domains (idempotent)
-- ============================================================
-- The remote DB already has these rows (added out-of-band, with their own
-- UUIDs). Running this INSERT against remote is a no-op thanks to the
-- ON CONFLICT (slug) clause — existing rows keep their current IDs, names,
-- and labels. On a fresh local DB this creates all 18 rows so the seed step
-- below can JOIN on slug.

insert into public.domains (slug, name, label) values
  ('community',     'Community',    'Those who share my culture or values'),
  ('currentevents', 'Events',       'Things that happen in life'),
  ('dating',        'Dating',       'Meeting new people'),
  ('education',     'Education',    'Learning new things'),
  ('family',        'Family',       'Those who are part of me by blood or soul'),
  ('fitness',       'Sport',        'Fitness and physical activities'),
  ('friends',       'Friends',      'Relationships'),
  ('health',        'Health',       'Health & body'),
  ('hobbies',       'Passions',     'Self-actualization'),
  ('identity',      'Identity',     'Actualization of Me, myself and I'),
  ('money',         'Finance',      'Security & comfort'),
  ('partner',       'Partner',      'Partner or soulmate'),
  ('selfcare',      'Self Care',    'Taking care of myself'),
  ('spirituality',  'Spirituality', 'Faith and religion'),
  ('tasks',         'Tasks',        'Things I have to do'),
  ('travel',        'Travel',       'Vacations or adventures across the world'),
  ('weather',       'Weather',      'Seasons, sun or snow, thunder or rain'),
  ('work',          'Work',         'Recognition & community')
on conflict (slug) do nothing;

-- ============================================================
-- 6. Seed 18 system glyphs by slug (filled in by Task 1.2)
-- ============================================================
-- PLACEHOLDER — Task 1.2 appends a WITH … INSERT block here that seeds one
-- row per domain slug, reading stroke arrays from docs/seeds/domain-glyph-seed.json.

-- ============================================================
-- 7. Link each domain to its default glyph (filled in by Task 1.2)
-- ============================================================
-- PLACEHOLDER — Task 1.2 appends an UPDATE here that sets
-- domains.default_glyph_id by matching on slug.
