-- Migration: Emotions.category_id NOT NULL — Phase 2
-- Issue: https://github.com/Bohns/pbbls/issues/366
-- Spec:  docs/superpowers/specs/2026-05-06-emotion-categories-palettes-design.md
--
-- Tightens the constraint on public.emotions.category_id after manual
-- backfill in Supabase Studio. Phase 1 (20260506000000) added the column
-- as nullable to decouple DDL from data work; Phase 2 locks it down now
-- that every row has a category_id.
--
-- After this migration, public.v_emotions_with_palette returns one row
-- per emotion (the INNER JOIN can no longer drop rows).

alter table public.emotions
  alter column category_id set not null;
