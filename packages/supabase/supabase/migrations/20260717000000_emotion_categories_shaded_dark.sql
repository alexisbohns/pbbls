-- Migration: emotion category palette — shaded + dark colors (#599)
--
-- Adds two palette slots to public.emotion_categories and surfaces them on
-- public.v_emotions_with_palette:
--   - dark_color: the small/medium Petroglyph backfill in dark mode (the
--     "palette.dark" role in the #599 read-view colour table).
--   - shaded_color: a deeper tint (e.g. the pebble-page title), not yet
--     consumed by a client — surfaced now so the palette view is complete.
--
-- Schema-only, like the original four palette columns: values are 8-digit hex
-- (#RRGGBBAA) hand-entered per category in Supabase Studio, not seeded here.
-- `add column if not exists` because the columns were added live in Studio
-- ahead of this migration; on a fresh DB the emotion_categories table is empty,
-- so the not-null columns land without a default.
--
-- The view appends the two columns AT THE END of the select list — Postgres
-- CREATE OR REPLACE VIEW can only add trailing columns (mid-list insertion is
-- read as a rename and rejected with SQLSTATE 42P16). Clients look columns up
-- by name, so position doesn't matter.

alter table public.emotion_categories
  add column if not exists shaded_color text not null,
  add column if not exists dark_color text not null;

create or replace view public.v_emotions_with_palette as
select
  e.id, e.slug, e.name, e.color,
  c.id              as category_id,
  c.slug            as category_slug,
  c.name            as category_name,
  c.primary_color, c.secondary_color, c.light_color, c.surface_color,
  e.emoji,
  c.shaded_color, c.dark_color
from public.emotions e
join public.emotion_categories c on c.id = e.category_id;
