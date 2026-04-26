-- Migration: Add glyph_id to souls (issue #298)
--
-- Souls today carry only a name. This migration adds an FK to glyphs so each
-- soul has a visual identity (rendered in the iOS Souls grid + detail header).
--
-- Strategy:
--   1. Defensively guarantee the system default glyph exists.
--   2. Add column nullable (so existing rows don't violate the constraint).
--   3. Backfill all rows to the system default.
--   4. Tighten: NOT NULL + default = system default glyph.
--
-- ON DELETE RESTRICT: glyphs aren't user-deletable today, but if that changes
-- the constraint stops a deletion from silently stranding a soul.
-- The default glyph itself must never be deletable while any soul references it.

-- 1. Defensively ensure the system default glyph row exists.
--    The remote DB already has it (seeded by 20260415000001), but a fresh local
--    DB might not have run that seed yet. ON CONFLICT keeps it idempotent.
insert into public.glyphs (id, user_id, shape_id, strokes, view_box)
values (
  '4759c37c-68a6-46a6-b4fc-046bd0316752',
  null,
  null,
  '[]'::jsonb,
  '0 0 200 200'
)
on conflict (id) do nothing;

-- 2. Add column nullable, FK to glyphs.
alter table public.souls
  add column glyph_id uuid references public.glyphs(id) on delete restrict;

-- 3. Backfill existing rows to the system default.
update public.souls
set glyph_id = '4759c37c-68a6-46a6-b4fc-046bd0316752'
where glyph_id is null;

-- 4. Tighten: NOT NULL + default.
alter table public.souls
  alter column glyph_id set not null,
  alter column glyph_id set default '4759c37c-68a6-46a6-b4fc-046bd0316752';
