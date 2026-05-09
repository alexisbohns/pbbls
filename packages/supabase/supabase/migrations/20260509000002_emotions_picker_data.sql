-- Migration: Emotion picker data (#370)
-- Spec:  docs/superpowers/specs/2026-05-09-ios-emotion-picker-sheet-design.md
--
-- Three changes bundled — all derivable from existing rows, no manual data step:
--   A. Adds public.emotions.emoji (text not null) and seeds all 38 rows.
--   B. Realigns legacy public.emotions.color to category.primary_color (6-digit form),
--      so shipped iOS versions still reading emotions.color render acceptably.
--   C. Refreshes public.v_emotions_with_palette to expose emoji.
--
-- Note: emotions.color is left in place (soft-deprecated). Old iOS clients still
-- read it via from("emotions"); this migration aligns those colors to the new
-- palette so legacy installs don't show drift until users update.
--
-- Note on view definition (C): emoji is appended AT THE END of the SELECT list
-- because `CREATE OR REPLACE VIEW` in Postgres can only add columns at the end —
-- inserting in the middle would be detected as renaming an existing column and
-- rejected with SQLSTATE 42P16. Clients (iOS decoder) look columns up by name,
-- so position doesn't matter for callers.

-- ============================================================
-- A. emotions.emoji
-- ============================================================

alter table public.emotions add column emoji text;

update public.emotions set emoji = case slug
  when 'amazed'       then '🤩'
  when 'amused'       then '😂'
  when 'angry'        then '😡'
  when 'annoyed'      then '😒'
  when 'anxious'      then '😰'
  when 'ashamed'      then '🫣'
  when 'brave'        then '🫡'
  when 'calm'         then '🙂'
  when 'confident'    then '😌'
  when 'content'      then '😀'
  when 'disappointed' then '😕'
  when 'discouraged'  then '😫'
  when 'disgusted'    then '🤢'
  when 'drained'      then '😪'
  when 'embarrassed'  then '😬'
  when 'excited'      then '😇'
  when 'frustrated'   then '😤'
  when 'grateful'     then '🥰'
  when 'guilty'       then '😓'
  when 'happy'        then '🤗'
  when 'hopeful'      then '🥹'
  when 'hopeless'     then '😞'
  when 'indifferent'  then '😑'
  when 'irritated'    then '🙄'
  when 'jealous'      then '😠'
  when 'joyful'       then '🥳'
  when 'lonely'       then '🥺'
  when 'overwhelmed'  then '😣'
  when 'passionate'   then '😍'
  when 'peaceful'     then '☺️'
  when 'proud'        then '😎'
  when 'relieved'     then '😮‍💨'
  when 'sad'          then '😢'
  when 'satisfied'    then '😊'
  when 'scared'       then '😱'
  when 'stressed'     then '😖'
  when 'surprised'    then '😯'
  when 'worried'      then '😟'
end;

alter table public.emotions alter column emoji set not null;

-- ============================================================
-- B. legacy emotions.color realignment
-- ============================================================

update public.emotions e
set color = substr(c.primary_color, 1, 7)  -- '#RRGGBBAA' → '#RRGGBB'
from public.emotion_categories c
where e.category_id = c.id;

-- ============================================================
-- C. refresh view to include emoji (appended at end — see header note)
-- ============================================================

create or replace view public.v_emotions_with_palette as
select
  e.id, e.slug, e.name, e.color,
  c.id              as category_id,
  c.slug            as category_slug,
  c.name            as category_name,
  c.primary_color, c.secondary_color, c.light_color, c.surface_color,
  e.emoji
from public.emotions e
join public.emotion_categories c on c.id = e.category_id;
