-- Migration: Retire the 16 obsolete emotion slugs (#796)
--
-- Found by the `schema` CI job (#794/#795) on its first run: `supabase db reset`
-- against an empty database died three migrations later, at 20260506000001, with
--
--   ERROR: column "category_id" of relation "emotions" contains null values
--
-- 20260506000000 creates emotion_categories EMPTY and adds emotions.category_id
-- nullable, recording that the backfill happens "manually in Supabase Studio";
-- that Studio work was never written back as a migration. 20260506000001 then
-- applies `set not null` to rows nothing had ever backfilled.
--
-- The rows it trips over are the 16 emotions seeded by 20260411000000. Production
-- does not have them: at some point in May 2026 the roster was replaced in Studio
-- by 38 entirely different slugs (angry, annoyed, jealous, overwhelmed, ...), and
-- the two sets share NO slug. The 38 are reconstructed in 20260912120000; this
-- migration only removes what they replaced.
--
-- WHY THE DELETE IS HERE AND THE INSERT IS NOT. Every migration between this one
-- and 20260912120000 was written against an EMPTY table and still needs to be:
--
--   20260506000001  `set not null` on emotions.category_id — trivially true of
--                   an empty table, impossible with uncategorised rows present.
--   20260509000002  adds emoji, then `set not null` — same.
--   20260717000000  adds shaded_color / dark_color NOT NULL with no default,
--                   explicitly noting "on a fresh DB the emotion_categories table
--                   is empty, so the not-null columns land without a default".
--                   Seeding categories before this point would break that ALTER
--                   outright.
--
-- So the chain is repaired by emptying early and seeding once at the end, after
-- every column exists.
--
-- SAFETY. The delete is scoped to the 16 obsolete slugs by name. It is NOT
-- `delete from public.emotions`: on production that would remove 38 live rows and
-- cascade into user pebbles. Scoped this way it matches zero rows on production
-- (verified against all 16) and exactly the 16 seeded rows on a fresh database.
--
-- ORDERING. This file is dated before migrations already applied to production, so
-- applying it there needs `supabase db push --include-all`. That is safe precisely
-- because it is a no-op on production.

delete from public.emotions
where slug in (
  'joy',
  'sadness',
  'anger',
  'fear',
  'disgust',
  'surprise',
  'love',
  'pride',
  'shame',
  'guilt',
  'anxiety',
  'nostalgia',
  'gratitude',
  'serenity',
  'excitement',
  'awe'
);
