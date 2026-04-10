-- Migration: Reference Tables
-- Seeded lookup tables for emotions, domains, card types, and pebble shapes.
-- Global read-only data — no user_id, no RLS write access.

-- ============================================================
-- TABLES
-- ============================================================

create table public.emotions (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  color text not null
);

create table public.domains (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  label text not null
);

create table public.card_types (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  prompt text not null
);

create table public.pebble_shapes (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  path text not null,
  view_box text not null
);

-- ============================================================
-- ACCESS CONTROL
-- ============================================================

alter table public.emotions enable row level security;
alter table public.domains enable row level security;
alter table public.card_types enable row level security;
alter table public.pebble_shapes enable row level security;

create policy "emotions_select" on public.emotions
  for select using (true);

create policy "domains_select" on public.domains
  for select using (true);

create policy "card_types_select" on public.card_types
  for select using (true);

create policy "pebble_shapes_select" on public.pebble_shapes
  for select using (true);

-- ============================================================
-- SEED DATA
-- ============================================================

insert into public.emotions (slug, name, color) values
  ('joy',        'Joy',        '#FACC15'),
  ('sadness',    'Sadness',    '#60A5FA'),
  ('anger',      'Anger',      '#EF4444'),
  ('fear',       'Fear',       '#A855F7'),
  ('disgust',    'Disgust',    '#22C55E'),
  ('surprise',   'Surprise',   '#F97316'),
  ('love',       'Love',       '#EC4899'),
  ('pride',      'Pride',      '#EAB308'),
  ('shame',      'Shame',      '#6B7280'),
  ('guilt',      'Guilt',      '#78716C'),
  ('anxiety',    'Anxiety',    '#8B5CF6'),
  ('nostalgia',  'Nostalgia',  '#D4A373'),
  ('gratitude',  'Gratitude',  '#34D399'),
  ('serenity',   'Serenity',   '#67E8F9'),
  ('excitement', 'Excitement', '#FB923C'),
  ('awe',        'Awe',        '#818CF8');

insert into public.domains (slug, name, label) values
  ('zoe',        'Zoē',       'Health & body'),
  ('asphaleia',  'Asphaleia',  'Security & comfort'),
  ('philia',     'Philía',     'Relationships'),
  ('time',       'Timē',       'Recognition & community'),
  ('eudaimonia', 'Eudaimonia', 'Self-actualization');

insert into public.card_types (slug, name, prompt) values
  ('free',      'Free',      'Write anything…'),
  ('feelings',  'Feelings',  'What did I feel?'),
  ('thoughts',  'Thoughts',  'What did I think?'),
  ('behaviour', 'Behaviour', 'What did I do?');

insert into public.pebble_shapes (slug, name, path, view_box) values
  ('river-smooth', 'River Smooth', 'M100,10 C130,8 160,20 175,45 C192,72 195,105 185,135 C175,165 155,185 125,192 C95,200 65,195 42,178 C18,160 8,132 10,100 C12,68 25,42 50,25 C72,12 88,10 100,10 Z', '0 0 200 200'),
  ('creek-flat',   'Creek Flat',   'M110,10 C145,8 178,18 198,42 C215,62 218,90 210,118 C200,148 178,168 148,175 C118,182 85,178 58,165 C30,150 12,128 8,100 C4,72 14,45 35,28 C58,12 82,10 110,10 Z', '0 0 220 180'),
  ('moss-round',   'Moss Round',   'M95,12 C120,8 148,15 168,32 C188,50 198,78 196,108 C194,138 180,162 158,178 C135,194 108,200 82,192 C55,184 32,166 18,140 C5,115 4,85 15,60 C28,35 52,18 78,12 C85,10 90,11 95,12 Z', '0 0 200 200'),
  ('canyon-long',  'Canyon Long',  'M90,12 C115,8 140,18 158,38 C172,55 178,78 176,105 C174,132 170,158 160,182 C148,205 128,225 105,232 C80,238 55,228 38,210 C22,192 12,168 8,140 C4,112 6,82 18,58 C32,32 55,15 80,12 C84,11 87,11 90,12 Z', '0 0 180 240'),
  ('shore-wide',   'Shore Wide',   'M120,10 C155,8 188,15 212,32 C232,48 238,72 230,98 C222,124 200,145 172,158 C142,168 108,170 78,162 C48,154 22,138 10,115 C2,92 5,68 20,48 C38,28 65,15 95,10 C105,9 112,9 120,10 Z', '0 0 240 170'),
  ('dusk-pebble',  'Dusk Pebble',  'M98,12 C125,8 152,18 170,38 C190,60 198,90 192,120 C186,150 168,175 142,192 C118,205 88,210 62,200 C38,188 18,165 10,138 C2,110 8,80 22,55 C38,30 62,15 88,12 C92,11 95,11 98,12 Z', '0 0 200 210');
