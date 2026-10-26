-- Migration: guides
-- The "About Pebbles" readings shown at the bottom of the iOS Home tab (#1254,
-- spec 2026-10-26 §4.1). Modelled on `logs` (20260421000001): bilingual with
-- EN required and FR falling back to EN on the client, public read gated on
-- `published`, writes admin-only. `illustration` is an enum the clients draw
-- from their own components; there is no image.

create table public.guides (
  id            uuid primary key default gen_random_uuid(),
  slug          text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  sort_order    smallint not null default 0,
  illustration  text check (illustration in (
                  'pebbles','stones','emotions','valence','domains',
                  'collections','souls','glyphs','karma')),
  title_en      text not null,
  title_fr      text,
  summary_en    text not null,
  summary_fr    text,
  body_md_en    text not null,
  body_md_fr    text,
  published     boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.guides is
  'Short readings about Pebbles (the Home tab''s About section, #1254). Edited in the admin; clients read published rows ordered by sort_order and fall back to EN when FR is null.';

create trigger guides_updated_at
  before update on public.guides
  for each row execute function public.set_updated_at();

alter table public.guides enable row level security;

create policy "guides_select" on public.guides
  for select using (published = true or public.is_admin(auth.uid()));

create policy "guides_insert" on public.guides
  for insert with check (public.is_admin(auth.uid()));

create policy "guides_update" on public.guides
  for update using (public.is_admin(auth.uid()));

create policy "guides_delete" on public.guides
  for delete using (public.is_admin(auth.uid()));

create index guides_published_sort_idx on public.guides (published, sort_order);

-- Seed: the nine topics, published, with a short EN body the maintainer
-- rewrites in the admin. Deterministic ids so a fresh database and the
-- project agree.
insert into public.guides (id, slug, sort_order, illustration, title_en, title_fr, summary_en, summary_fr, body_md_en, published) values
  (md5('guides:pebbles')::uuid,     'pebbles',     10, 'pebbles',     'About Pebbles',      'À propos de Pebbles',      'The private memory of your life with the people in it.',         'La mémoire privée de ta vie avec les gens qui la peuplent.',  'Pebbles is a private journal of moments. A moment is a time and a name. A pebble is that moment completed with how it felt. Nothing here is shared unless you choose to.', true),
  (md5('guides:stones')::uuid,      'stones',      20, 'stones',      'About the stones',   'À propos des pierres',     'How a stone is built from intensity, positiveness and emotion.',  'Comment une pierre naît de l''intensité, de la valence et de l''émotion.', 'Every pebble is drawn as a stone. Its size is the intensity: small, medium or large. Its kind is the positiveness: a lava stone for a low moment, a river pebble for an even one, a gem for a bright one. Its colour is the emotion.', true),
  (md5('guides:emotions')::uuid,    'emotions',    30, 'emotions',    'About emotions',     'À propos des émotions',    'Seven families, and the finer emotions inside them.',             'Sept familles, et les émotions plus fines qu''elles abritent.', 'Each pebble carries one emotion. Emotions belong to seven families: joy, pride, anger, fear, shame, sadness and peace. The family gives the pebble its palette; the finer emotion gives it its name.', true),
  (md5('guides:valence')::uuid,     'valence',     40, 'valence',     'About valence',      'À propos de la valence',   'Bright, even or heavy: what a moment did to you.',                'Lumineux, égal ou lourd : ce qu''un moment t''a fait.', 'Positiveness says whether a moment lifted you, left you even, or weighed on you. It is not a judgement of the moment, only of how it landed. Heavy moments are as worth keeping as bright ones.', true),
  (md5('guides:domains')::uuid,     'domains',     50, 'domains',     'About domains',      'À propos des domaines',    'The areas of life a moment touches.',                             'Les domaines de la vie qu''un moment touche.', 'A domain is an area of your life: family, work, health, friends, travel, and more. A pebble can touch several. Over the months, domains show where your life is happening.', true),
  (md5('guides:collections')::uuid, 'collections', 60, 'collections', 'About collections',  'À propos des collections', 'Stacks, packs and tracks: pebbles gathered along a thread.',      'Piles, séries et parcours : des galets réunis par un fil.', 'A collection gathers pebbles along a thread. A stack is a pile, unordered. A pack is a set you complete. A track is a sequence in time. A pebble can be in several collections, and deleting a collection never deletes its pebbles.', true),
  (md5('guides:souls')::uuid,       'souls',       70, 'souls',       'About souls',        'À propos des âmes',        'The people and beings in your life.',                             'Les personnes et les êtres de ta vie.', 'A soul is someone in your life: a person, or a being like your dog. You name souls on your pebbles. Each soul''s page gathers every moment you shared. A soul can be linked to another Pebbles user if you both want to.', true),
  (md5('guides:glyphs')::uuid,      'glyphs',      80, 'glyphs',      'About glyphs',       'À propos des glyphes',     'Marks you carve, instead of photos of people.',                   'Des marques que tu graves, plutôt que des photos de gens.', 'A glyph is a small mark you carve by hand. Glyphs stand for your souls and your collections; there are no photos of people in Pebbles. You can carve as many as you like.', true),
  (md5('guides:karma')::uuid,       'karma',       90, 'karma',       'About karma',        'À propos du karma',        'A quiet count of what you keep.',                                 'Un compte discret de ce que tu gardes.', 'Karma grows as you record and complete moments and reach milestones. It is never shown to anyone else. Today it can be spent on glyphs; later it will count toward a printed book of your year.', true);
