-- Migration: Seed the emotion reference data the chain never carried (#796)
--
-- The other half of the repair begun in 20260505000000, which retired the 16
-- obsolete emotion slugs. This seeds what production actually runs on and the
-- migrations never recorded: 7 emotion categories with their six palette colours,
-- and the 38 emotions with emoji and category.
--
-- Both halves are no-ops against production. Every insert here conflicts on its
-- unique slug and does nothing; the delete in the other file matches nothing. On a
-- fresh database, together they make the chain replay to the end.
--
-- Dated last on purpose: every column written below arrives in a different
-- migration (emoji in 20260509000002, shaded_color / dark_color in 20260717000000),
-- and seeding earlier would break the ALTERs that add them. See the header of
-- 20260505000000 for the full ordering argument.
--
-- IDS ARE PINNED, not generated. None of the 38 production emotion IDs follow the
-- md5('emotions:' || slug) rule from 20260411000006 — they were created by hand
-- with gen_random_uuid() — and emotion_categories was never covered by that
-- migration at all. Pinning is what makes a fresh database byte-identical to
-- production, and what lets this file work as a restore path.
--
-- SOURCE OF TRUTH: packages/supabase/reference/{emotions,emotion-categories}.json.
-- This file is generated from them, and scripts/verify-reference-data.ts checks the
-- linked project against them on every packages/supabase PR and nightly. Studio
-- stays the editing surface; when a palette changes there, that harness is what
-- tells you this file has fallen behind.

-- ============================================================
-- 1. Emotion categories (7)
-- ============================================================

insert into public.emotion_categories
  (id, slug, name, primary_color, secondary_color, light_color, surface_color, shaded_color, dark_color)
values
  ('f094a725-61aa-4488-8bc0-1c814159b536', 'anger', 'Anger', '#8E4242FF', '#C17575FF', '#F4ECECFF', '#8E42421A', '#632E2EFF', '#1C0D0DFF'),
  ('1dada583-3ac8-4964-a976-76974eeac8fd', 'fear', 'Fear', '#7B5E99FF', '#AE91CCFF', '#F2EFF5FF', '#7B5E991A', '#56426BFF', '#19131FFF'),
  ('b9ff1de0-6846-4254-956e-7c7570f9b28b', 'joy', 'Joy', '#A15C08FF', '#CF8C39FF', '#FAF6EAFF', '#A15C081A', '#714006FF', '#201202FF'),
  ('eed68af7-491c-4736-a02d-ea2cd314e201', 'peace', 'Peaceful', '#487C5AFF', '#80BF96FF', '#EDF2EEFF', '#487C5A1A', '#32573FFF', '#0E1912FF'),
  ('57b334d9-f1e7-4865-ab20-1ca6ce794a51', 'pride', 'Pride', '#A9478AFF', '#EA91CEFF', '#F6EDF3FF', '#A9478A1A', '#763261FF', '#220E1CFF'),
  ('528f2356-d960-444b-a437-ec4340f78e45', 'sadness', 'Sadness', '#59658AFF', '#8C98BDFF', '#EEF0F3FF', '#59658A1A', '#3E4761FF', '#12141CFF'),
  ('f5316645-9221-4597-9772-677245060877', 'shame', 'Shame', '#868686FF', '#B9B9B9FF', '#F3F3F3FF', '#8686861A', '#5E5E5EFF', '#1B1B1BFF')
on conflict (slug) do nothing;

-- ============================================================
-- 2. Emotions (38)
-- ============================================================
--
-- category_id is looked up by slug rather than pinned twice, so the two blocks
-- cannot disagree about which category a row belongs to.

insert into public.emotions (id, slug, name, color, emoji, category_id)
values
  ('50a68890-f6bb-e67a-6655-e84ff0b71f49', 'amazed', 'Awe', '#A15C08', '🤩', (select id from public.emotion_categories where slug = 'joy')),
  ('f14ca000-32c7-4cf3-9aa5-ad5dc84c5285', 'amused', 'Amused', '#A15C08', '😂', (select id from public.emotion_categories where slug = 'joy')),
  ('8a851a45-d1c5-92fd-fd50-098c4e86eaef', 'angry', 'Anger', '#8E4242', '😡', (select id from public.emotion_categories where slug = 'anger')),
  ('25d98464-bc3d-417f-ae12-a3a996e5dee1', 'annoyed', 'Annoyed', '#8E4242', '😒', (select id from public.emotion_categories where slug = 'anger')),
  ('b4216f65-f0a6-2a26-ca1a-9dfa839fbbeb', 'anxious', 'Anxiety', '#7B5E99', '😰', (select id from public.emotion_categories where slug = 'fear')),
  ('0c98e05e-cf64-3b6f-929f-8bb6d0449b5f', 'ashamed', 'Shame', '#868686', '🫣', (select id from public.emotion_categories where slug = 'shame')),
  ('70e70f6a-237b-4e2d-8f4c-8043df889b36', 'brave', 'Brave', '#A9478A', '🫡', (select id from public.emotion_categories where slug = 'pride')),
  ('5ff5cd1c-6739-6143-537f-85a2dbbd8841', 'calm', 'Calm', '#487C5A', '🙂', (select id from public.emotion_categories where slug = 'peace')),
  ('3e14e406-90e8-41fd-a967-30fa545c47dc', 'confident', 'Confident', '#A9478A', '😌', (select id from public.emotion_categories where slug = 'pride')),
  ('b0230f83-c4e4-4cdc-bf9f-2d650509658c', 'content', 'Content', '#487C5A', '😀', (select id from public.emotion_categories where slug = 'peace')),
  ('f76b1274-04ab-4a9c-858d-4da26f8d124d', 'disappointed', 'Disappointed', '#59658A', '😕', (select id from public.emotion_categories where slug = 'sadness')),
  ('8a79a265-5dcd-4d42-b8de-8c1ff5d7e505', 'discouraged', 'Discouraged', '#59658A', '😫', (select id from public.emotion_categories where slug = 'sadness')),
  ('332cabe9-c76d-d737-bba6-f2d7024de808', 'disgusted', 'Disgust', '#8E4242', '🤢', (select id from public.emotion_categories where slug = 'anger')),
  ('ce828ce7-78ad-4a39-bfe6-0c843cfb2838', 'drained', 'Drained', '#59658A', '😪', (select id from public.emotion_categories where slug = 'sadness')),
  ('52301a19-4b27-4432-9d0b-281f6341bb95', 'embarrassed', 'Embarrassed', '#868686', '😬', (select id from public.emotion_categories where slug = 'shame')),
  ('98bdc543-6b26-e1c8-da1d-50ae8b9973ff', 'excited', 'Excitement', '#A15C08', '😇', (select id from public.emotion_categories where slug = 'joy')),
  ('fab5fa1f-ad54-4705-a205-f8a0b2b31806', 'frustrated', 'Frustrated', '#8E4242', '😤', (select id from public.emotion_categories where slug = 'anger')),
  ('b5c925f9-ed7d-5b87-2dc2-d3857e736235', 'grateful', 'Gratitude', '#487C5A', '🥰', (select id from public.emotion_categories where slug = 'peace')),
  ('648b1092-820c-b19a-8a27-07072b068724', 'guilty', 'Guilt', '#868686', '😓', (select id from public.emotion_categories where slug = 'shame')),
  ('f1b09d24-3c2e-414b-a751-fc91da51fcdb', 'happy', 'Happy', '#A15C08', '🤗', (select id from public.emotion_categories where slug = 'joy')),
  ('b4102799-3d5f-45f2-bfa9-28d72fb89bec', 'hopeful', 'Hopeful', '#487C5A', '🥹', (select id from public.emotion_categories where slug = 'peace')),
  ('5d5e346a-c5c5-4fcb-99e9-2951213c3ff3', 'hopeless', 'Hopeless', '#59658A', '😞', (select id from public.emotion_categories where slug = 'sadness')),
  ('59f68361-3443-4352-a119-b0daaf864bca', 'indifferent', 'Indifferent', '#868686', '😑', (select id from public.emotion_categories where slug = 'shame')),
  ('df630582-0846-4ebf-be75-166595513840', 'irritated', 'Irritated', '#8E4242', '🙄', (select id from public.emotion_categories where slug = 'anger')),
  ('c32faae1-46ac-411b-987f-e32e572259bb', 'jealous', 'Jealous', '#8E4242', '😠', (select id from public.emotion_categories where slug = 'anger')),
  ('3ffd6ca6-e033-6926-fa1f-ddf8f90bfd8c', 'joyful', 'Joy', '#A15C08', '🥳', (select id from public.emotion_categories where slug = 'joy')),
  ('f99d4e11-5a81-101b-07a3-6fc89a7aa1d9', 'lonely', 'Loneliness', '#59658A', '🥺', (select id from public.emotion_categories where slug = 'sadness')),
  ('afd28421-b994-475c-ad49-75ecae570ee5', 'overwhelmed', 'Overwhelmed', '#7B5E99', '😣', (select id from public.emotion_categories where slug = 'fear')),
  ('6b22ff42-67d4-ea3d-f35b-f71e49ae94cb', 'passionate', 'Passion', '#A15C08', '😍', (select id from public.emotion_categories where slug = 'joy')),
  ('21d79e4c-9482-40d3-bf73-53adfb452e5a', 'peaceful', 'Peaceful', '#487C5A', '☺️', (select id from public.emotion_categories where slug = 'peace')),
  ('ed9fbe9f-6a93-22bb-d5d5-35f42164c120', 'proud', 'Pride', '#A9478A', '😎', (select id from public.emotion_categories where slug = 'pride')),
  ('0312c804-7656-4f33-950c-a70a5b08b842', 'relieved', 'Relieved', '#487C5A', '😮‍💨', (select id from public.emotion_categories where slug = 'peace')),
  ('075ec40d-f27c-033d-a498-850947e4e887', 'sad', 'Sadness', '#59658A', '😢', (select id from public.emotion_categories where slug = 'sadness')),
  ('776d210e-29f3-4f14-acb3-9787518aa53d', 'satisfied', 'Satisfied', '#487C5A', '😊', (select id from public.emotion_categories where slug = 'peace')),
  ('8b2993f3-4043-7bdf-388d-c0e3d5c96f02', 'scared', 'Fear', '#7B5E99', '😱', (select id from public.emotion_categories where slug = 'fear')),
  ('93694d2b-41a7-4dfc-8582-7cce835ca9fe', 'stressed', 'Stressed', '#7B5E99', '😖', (select id from public.emotion_categories where slug = 'fear')),
  ('a9b0625b-8a77-06cf-ba9c-a348ab58b12c', 'surprised', 'Surprise', '#487C5A', '😯', (select id from public.emotion_categories where slug = 'peace')),
  ('2d45e136-cee7-4bf1-b5fa-a3a79f26feab', 'worried', 'Worried', '#7B5E99', '😟', (select id from public.emotion_categories where slug = 'fear'))
on conflict (slug) do nothing;

-- ============================================================
-- 3. Keep the achievement catalog in step
-- ============================================================
--
-- Standing rule (root CLAUDE.md): any migration that inserts an emotion or a domain
-- re-runs sync_achievement_catalog() in the same transaction, or the catalog drifts
-- from the reference tables. The function is `insert ... on conflict do nothing`
-- over emotions and domains, so this is a no-op on production and is what builds
-- the 38 emotion_first rows on a fresh database.

select public.sync_achievement_catalog();
