# Database Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the PostgreSQL schema for Pebbles' Supabase backend — four layered migrations covering reference tables, core tables, views, and RPCs.

**Architecture:** Layered migrations in `packages/supabase/supabase/migrations/`. Each migration builds on the previous. Reference tables seeded inline. Core tables use `user_id` on every row for RLS. Views consolidate reads with JSONB aggregation. RPCs handle atomic multi-table writes.

**Tech Stack:** PostgreSQL (via Supabase), SQL migrations, Supabase CLI, plpgsql functions.

**Spec:** `docs/superpowers/specs/2026-04-11-database-schema-design.md`

**Working directory:** All commands run from `packages/supabase/`.

---

### Task 1: Migration — Reference Tables

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_reference_tables.sql`

- [ ] **Step 1: Create the migration file**

Run from `packages/supabase/`:

```bash
npm run db:migration:new -- reference_tables
```

This creates an empty timestamped file in `supabase/migrations/`.

- [ ] **Step 2: Write the migration SQL**

Write the following SQL into the created migration file:

```sql
-- Migration: Reference Tables
-- Seeded lookup tables for emotions, domains, card types, and pebble shapes.
-- Global read-only data — no user_id, no RLS.

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

-- Everyone can read, nobody can write through the API.
-- Reference data is managed via migrations only.

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
```

- [ ] **Step 3: Start local Supabase and verify migration**

```bash
npm run db:start
npm run db:reset
```

Expected: migration runs without errors.

- [ ] **Step 4: Verify seed data**

Run from `packages/supabase/`:

```bash
npx supabase db execute --sql "select count(*) as n from emotions; select count(*) as n from domains; select count(*) as n from card_types; select count(*) as n from pebble_shapes;"
```

Expected output: 16, 5, 4, 6.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/
git commit -m "feat(db): add reference tables migration with seed data"
```

---

### Task 2: Migration — Core Tables, Join Tables, RLS, Indexes

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_core_tables.sql`

- [ ] **Step 1: Create the migration file**

```bash
cd packages/supabase && npm run db:migration:new -- core_tables
```

- [ ] **Step 2: Write the migration SQL**

Write the following SQL into the created migration file:

```sql
-- Migration: Core Tables, Join Tables, RLS Policies, Indexes
-- All user-owned tables with user_id for RLS.

-- ============================================================
-- TRIGGER FUNCTION (reusable)
-- ============================================================

create function public.set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

-- ============================================================
-- CORE TABLES
-- ============================================================

create table public.profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid unique not null references auth.users(id),
  display_name text not null,
  onboarding_completed boolean not null default false,
  color_world text not null default 'blush-quartz',
  terms_accepted_at timestamptz,
  privacy_accepted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.souls (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.glyphs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  name text,
  shape_id uuid not null references public.pebble_shapes(id),
  strokes jsonb not null default '[]',
  view_box text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.pebbles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  name text not null,
  description text,
  happened_at timestamptz not null,
  intensity smallint not null check (intensity between 1 and 3),
  positiveness smallint not null check (positiveness between -1 and 1),
  visibility text not null default 'private',
  emotion_id uuid not null references public.emotions(id),
  glyph_id uuid references public.glyphs(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.collections (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  name text not null,
  mode text check (mode in ('stack', 'pack', 'track')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.pebble_cards (
  id uuid primary key default gen_random_uuid(),
  pebble_id uuid not null references public.pebbles(id) on delete cascade,
  species_id uuid not null references public.card_types(id),
  value text not null,
  sort_order smallint not null default 0
);

create table public.snaps (
  id uuid primary key default gen_random_uuid(),
  pebble_id uuid not null references public.pebbles(id) on delete cascade,
  user_id uuid not null references auth.users(id),
  storage_path text not null,
  sort_order smallint not null default 0,
  created_at timestamptz not null default now()
);

create table public.karma_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  delta smallint not null,
  reason text not null,
  ref_id uuid,
  created_at timestamptz not null default now()
);

-- ============================================================
-- JOIN TABLES
-- ============================================================

create table public.pebble_souls (
  pebble_id uuid not null references public.pebbles(id) on delete cascade,
  soul_id uuid not null references public.souls(id) on delete cascade,
  primary key (pebble_id, soul_id)
);

create table public.pebble_domains (
  pebble_id uuid not null references public.pebbles(id) on delete cascade,
  domain_id uuid not null references public.domains(id),
  primary key (pebble_id, domain_id)
);

create table public.collection_pebbles (
  collection_id uuid not null references public.collections(id) on delete cascade,
  pebble_id uuid not null references public.pebbles(id) on delete cascade,
  primary key (collection_id, pebble_id)
);

-- ============================================================
-- UPDATED_AT TRIGGERS
-- ============================================================

create trigger profiles_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

create trigger souls_updated_at
  before update on public.souls
  for each row execute function public.set_updated_at();

create trigger glyphs_updated_at
  before update on public.glyphs
  for each row execute function public.set_updated_at();

create trigger pebbles_updated_at
  before update on public.pebbles
  for each row execute function public.set_updated_at();

create trigger collections_updated_at
  before update on public.collections
  for each row execute function public.set_updated_at();

-- ============================================================
-- ROW LEVEL SECURITY
-- ============================================================

-- Core tables: direct user_id check

alter table public.profiles enable row level security;

create policy "profiles_select" on public.profiles
  for select using (user_id = auth.uid());
create policy "profiles_insert" on public.profiles
  for insert with check (user_id = auth.uid());
create policy "profiles_update" on public.profiles
  for update using (user_id = auth.uid());
create policy "profiles_delete" on public.profiles
  for delete using (user_id = auth.uid());

alter table public.pebbles enable row level security;

create policy "pebbles_select" on public.pebbles
  for select using (user_id = auth.uid());
create policy "pebbles_insert" on public.pebbles
  for insert with check (user_id = auth.uid());
create policy "pebbles_update" on public.pebbles
  for update using (user_id = auth.uid());
create policy "pebbles_delete" on public.pebbles
  for delete using (user_id = auth.uid());

alter table public.souls enable row level security;

create policy "souls_select" on public.souls
  for select using (user_id = auth.uid());
create policy "souls_insert" on public.souls
  for insert with check (user_id = auth.uid());
create policy "souls_update" on public.souls
  for update using (user_id = auth.uid());
create policy "souls_delete" on public.souls
  for delete using (user_id = auth.uid());

alter table public.glyphs enable row level security;

create policy "glyphs_select" on public.glyphs
  for select using (user_id = auth.uid());
create policy "glyphs_insert" on public.glyphs
  for insert with check (user_id = auth.uid());
create policy "glyphs_update" on public.glyphs
  for update using (user_id = auth.uid());
create policy "glyphs_delete" on public.glyphs
  for delete using (user_id = auth.uid());

alter table public.collections enable row level security;

create policy "collections_select" on public.collections
  for select using (user_id = auth.uid());
create policy "collections_insert" on public.collections
  for insert with check (user_id = auth.uid());
create policy "collections_update" on public.collections
  for update using (user_id = auth.uid());
create policy "collections_delete" on public.collections
  for delete using (user_id = auth.uid());

alter table public.karma_events enable row level security;

create policy "karma_events_select" on public.karma_events
  for select using (user_id = auth.uid());
create policy "karma_events_insert" on public.karma_events
  for insert with check (user_id = auth.uid());

alter table public.snaps enable row level security;

create policy "snaps_select" on public.snaps
  for select using (user_id = auth.uid());
create policy "snaps_insert" on public.snaps
  for insert with check (user_id = auth.uid());
create policy "snaps_update" on public.snaps
  for update using (user_id = auth.uid());
create policy "snaps_delete" on public.snaps
  for delete using (user_id = auth.uid());

-- Dependent tables: ownership via parent join

alter table public.pebble_cards enable row level security;

create policy "pebble_cards_select" on public.pebble_cards
  for select using (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );
create policy "pebble_cards_insert" on public.pebble_cards
  for insert with check (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );
create policy "pebble_cards_update" on public.pebble_cards
  for update using (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );
create policy "pebble_cards_delete" on public.pebble_cards
  for delete using (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );

alter table public.pebble_souls enable row level security;

create policy "pebble_souls_select" on public.pebble_souls
  for select using (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );
create policy "pebble_souls_insert" on public.pebble_souls
  for insert with check (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );
create policy "pebble_souls_delete" on public.pebble_souls
  for delete using (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );

alter table public.pebble_domains enable row level security;

create policy "pebble_domains_select" on public.pebble_domains
  for select using (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );
create policy "pebble_domains_insert" on public.pebble_domains
  for insert with check (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );
create policy "pebble_domains_delete" on public.pebble_domains
  for delete using (
    exists (select 1 from public.pebbles where id = pebble_id and user_id = auth.uid())
  );

alter table public.collection_pebbles enable row level security;

create policy "collection_pebbles_select" on public.collection_pebbles
  for select using (
    exists (select 1 from public.collections where id = collection_id and user_id = auth.uid())
  );
create policy "collection_pebbles_insert" on public.collection_pebbles
  for insert with check (
    exists (select 1 from public.collections where id = collection_id and user_id = auth.uid())
  );
create policy "collection_pebbles_delete" on public.collection_pebbles
  for delete using (
    exists (select 1 from public.collections where id = collection_id and user_id = auth.uid())
  );

-- ============================================================
-- INDEXES
-- ============================================================

-- profiles.user_id already has a UNIQUE constraint (implicit index)
create index pebbles_user_id_idx on public.pebbles (user_id);
create index pebbles_happened_at_idx on public.pebbles (happened_at);
create index pebbles_emotion_id_idx on public.pebbles (emotion_id);
create index souls_user_id_idx on public.souls (user_id);
create index glyphs_user_id_idx on public.glyphs (user_id);
create index collections_user_id_idx on public.collections (user_id);
create index pebble_cards_pebble_id_idx on public.pebble_cards (pebble_id);
create index snaps_pebble_id_idx on public.snaps (pebble_id);
create index karma_events_user_id_idx on public.karma_events (user_id);
```

- [ ] **Step 3: Reset DB and verify migration**

```bash
npm run db:reset
```

Expected: both migrations run without errors.

- [ ] **Step 4: Verify table creation**

```bash
npx supabase db execute --sql "
  select table_name
  from information_schema.tables
  where table_schema = 'public'
  order by table_name;
"
```

Expected: `card_types`, `collection_pebbles`, `collections`, `domains`, `emotions`, `glyphs`, `karma_events`, `pebble_cards`, `pebble_domains`, `pebble_shapes`, `pebble_souls`, `pebbles`, `profiles`, `snaps`, `souls`.

- [ ] **Step 5: Verify RLS is enabled on all tables**

```bash
npx supabase db execute --sql "
  select tablename, rowsecurity
  from pg_tables
  where schemaname = 'public'
  order by tablename;
"
```

Expected: all 15 tables show `rowsecurity = true`.

- [ ] **Step 6: Verify updated_at trigger works**

```bash
npx supabase db execute --sql "
  select tgname, tgrelid::regclass
  from pg_trigger
  where tgname like '%updated_at'
  order by tgname;
"
```

Expected: 5 triggers on profiles, pebbles, souls, glyphs, collections.

- [ ] **Step 7: Commit**

```bash
git add packages/supabase/supabase/migrations/
git commit -m "feat(db): add core tables, join tables, RLS policies, and indexes"
```

---

### Task 3: Migration — Views

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_views.sql`

- [ ] **Step 1: Create the migration file**

```bash
cd packages/supabase && npm run db:migration:new -- views
```

- [ ] **Step 2: Write the migration SQL**

Write the following SQL into the created migration file:

```sql
-- Migration: JSONB Views
-- Consolidated read views for pebbles, karma, and bounce.

-- ============================================================
-- v_pebbles_full
-- ============================================================
-- Returns one row per pebble with all related data as JSONB.
-- RLS applies transparently via the underlying tables.

create view public.v_pebbles_full as
select
  p.id,
  p.user_id,
  p.name,
  p.description,
  p.happened_at,
  p.intensity,
  p.positiveness,
  p.visibility,
  p.emotion_id,
  p.glyph_id,
  p.created_at,
  p.updated_at,

  -- emotion (1:1, always present)
  jsonb_build_object(
    'id',    e.id,
    'slug',  e.slug,
    'name',  e.name,
    'color', e.color
  ) as emotion,

  -- glyph (1:1, optional)
  case when g.id is not null then
    jsonb_build_object(
      'id',       g.id,
      'name',     g.name,
      'shape_id', g.shape_id,
      'strokes',  g.strokes,
      'view_box', g.view_box
    )
  else null end as glyph,

  -- cards (1:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',         pc.id,
        'species_id', pc.species_id,
        'value',      pc.value,
        'sort_order', pc.sort_order
      ) order by pc.sort_order
    )
    from public.pebble_cards pc
    where pc.pebble_id = p.id),
    '[]'::jsonb
  ) as cards,

  -- souls (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',   s.id,
        'name', s.name
      ) order by s.name
    )
    from public.pebble_souls ps
    join public.souls s on s.id = ps.soul_id
    where ps.pebble_id = p.id),
    '[]'::jsonb
  ) as souls,

  -- domains (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',    d.id,
        'slug',  d.slug,
        'name',  d.name,
        'label', d.label
      ) order by d.slug
    )
    from public.pebble_domains pd
    join public.domains d on d.id = pd.domain_id
    where pd.pebble_id = p.id),
    '[]'::jsonb
  ) as domains,

  -- snaps (1:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',           sn.id,
        'storage_path', sn.storage_path,
        'sort_order',   sn.sort_order
      ) order by sn.sort_order
    )
    from public.snaps sn
    where sn.pebble_id = p.id),
    '[]'::jsonb
  ) as snaps,

  -- collections (N:N)
  coalesce(
    (select jsonb_agg(
      jsonb_build_object(
        'id',   c.id,
        'name', c.name,
        'mode', c.mode
      ) order by c.name
    )
    from public.collection_pebbles cp
    join public.collections c on c.id = cp.collection_id
    where cp.pebble_id = p.id),
    '[]'::jsonb
  ) as collections

from public.pebbles p
join public.emotions e on e.id = p.emotion_id
left join public.glyphs g on g.id = p.glyph_id;

-- ============================================================
-- v_karma_summary
-- ============================================================

create view public.v_karma_summary as
select
  u.id as user_id,
  coalesce((select sum(ke.delta) from public.karma_events ke where ke.user_id = u.id), 0) as total_karma,
  (select count(*) from public.pebbles pb where pb.user_id = u.id) as pebbles_count
from auth.users u;

-- ============================================================
-- v_bounce
-- ============================================================

create view public.v_bounce as
select
  u.id as user_id,
  coalesce(stats.active_days, 0) as active_days,
  case
    when coalesce(stats.active_days, 0) = 0  then 0
    when stats.active_days between 1  and 5  then 1
    when stats.active_days between 6  and 9  then 2
    when stats.active_days between 10 and 13 then 3
    when stats.active_days between 14 and 17 then 4
    when stats.active_days between 18 and 20 then 5
    when stats.active_days between 21 and 24 then 6
    else 7
  end::smallint as bounce_level
from auth.users u
left join lateral (
  select count(distinct date(pb.happened_at)) as active_days
  from public.pebbles pb
  where pb.user_id = u.id
    and pb.happened_at >= now() - interval '28 days'
) stats on true;
```

- [ ] **Step 3: Reset DB and verify migration**

```bash
npm run db:reset
```

Expected: all three migrations run without errors.

- [ ] **Step 4: Verify views exist**

```bash
npx supabase db execute --sql "
  select table_name
  from information_schema.views
  where table_schema = 'public'
  order by table_name;
"
```

Expected: `v_bounce`, `v_karma_summary`, `v_pebbles_full`.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/
git commit -m "feat(db): add consolidated JSONB views for pebbles, karma, and bounce"
```

---

### Task 4: Migration — RPC Functions

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_rpc_functions.sql`

- [ ] **Step 1: Create the migration file**

```bash
cd packages/supabase && npm run db:migration:new -- rpc_functions
```

- [ ] **Step 2: Write the migration SQL**

Write the following SQL into the created migration file:

```sql
-- Migration: RPC Functions
-- Atomic multi-table write operations for pebbles.

-- ============================================================
-- HELPER: compute_karma_delta
-- ============================================================
-- Pure function that calculates karma points for a pebble state.
-- Base: +1
-- +1 if description is non-empty
-- +N for each card (up to 4)
-- +1 if at least one soul
-- +1 if at least one domain
-- +1 if glyph attached
-- +1 if at least one snap

create function public.compute_karma_delta(
  p_description text,
  p_cards_count int,
  p_souls_count int,
  p_domains_count int,
  p_has_glyph boolean,
  p_snaps_count int
) returns int as $$
declare
  delta int := 1; -- base
begin
  if p_description is not null and p_description <> '' then
    delta := delta + 1;
  end if;
  delta := delta + least(p_cards_count, 4);
  if p_souls_count > 0 then
    delta := delta + 1;
  end if;
  if p_domains_count > 0 then
    delta := delta + 1;
  end if;
  if p_has_glyph then
    delta := delta + 1;
  end if;
  if p_snaps_count > 0 then
    delta := delta + 1;
  end if;
  return delta;
end;
$$ language plpgsql immutable;

-- ============================================================
-- create_pebble(payload jsonb) → uuid
-- ============================================================
-- Atomically creates a pebble and all related entities.
-- Supports inline creation of new glyphs and souls.

create function public.create_pebble(payload jsonb)
returns uuid as $$
declare
  v_user_id uuid := auth.uid();
  v_pebble_id uuid;
  v_glyph_id uuid;
  v_soul_ids uuid[];
  v_new_soul record;
  v_new_soul_id uuid;
  v_card record;
  v_snap record;
  v_karma int;
  v_cards_count int;
  v_souls_count int;
  v_domains_count int;
  v_snaps_count int;
begin
  -- Inline glyph creation
  if payload ? 'new_glyph' then
    insert into public.glyphs (user_id, name, shape_id, strokes, view_box)
    values (
      v_user_id,
      (payload->'new_glyph'->>'name'),
      (payload->'new_glyph'->>'shape_id')::uuid,
      coalesce(payload->'new_glyph'->'strokes', '[]'::jsonb),
      (payload->'new_glyph'->>'view_box')
    )
    returning id into v_glyph_id;
  else
    v_glyph_id := (payload->>'glyph_id')::uuid;
  end if;

  -- Collect existing soul IDs
  select array_agg(val::uuid)
  into v_soul_ids
  from jsonb_array_elements_text(coalesce(payload->'soul_ids', '[]'::jsonb)) val;

  -- Inline soul creation
  if payload ? 'new_souls' then
    for v_new_soul in select * from jsonb_array_elements(payload->'new_souls')
    loop
      insert into public.souls (user_id, name)
      values (v_user_id, v_new_soul.value->>'name')
      returning id into v_new_soul_id;

      v_soul_ids := array_append(v_soul_ids, v_new_soul_id);
    end loop;
  end if;

  -- Create the pebble
  insert into public.pebbles (
    user_id, name, description, happened_at,
    intensity, positiveness, visibility,
    emotion_id, glyph_id
  )
  values (
    v_user_id,
    payload->>'name',
    payload->>'description',
    (payload->>'happened_at')::timestamptz,
    (payload->>'intensity')::smallint,
    (payload->>'positiveness')::smallint,
    coalesce(payload->>'visibility', 'private'),
    (payload->>'emotion_id')::uuid,
    v_glyph_id
  )
  returning id into v_pebble_id;

  -- Insert cards
  v_cards_count := 0;
  if payload ? 'cards' then
    for v_card in select * from jsonb_array_elements(payload->'cards')
    loop
      insert into public.pebble_cards (pebble_id, species_id, value, sort_order)
      values (
        v_pebble_id,
        (v_card.value->>'species_id')::uuid,
        v_card.value->>'value',
        coalesce((v_card.value->>'sort_order')::smallint, 0)
      );
      v_cards_count := v_cards_count + 1;
    end loop;
  end if;

  -- Insert pebble_souls
  v_souls_count := 0;
  if v_soul_ids is not null then
    insert into public.pebble_souls (pebble_id, soul_id)
    select v_pebble_id, unnest(v_soul_ids);
    v_souls_count := array_length(v_soul_ids, 1);
  end if;

  -- Insert pebble_domains
  v_domains_count := 0;
  if payload ? 'domain_ids' then
    insert into public.pebble_domains (pebble_id, domain_id)
    select v_pebble_id, (val::text)::uuid
    from jsonb_array_elements_text(payload->'domain_ids') val;
    v_domains_count := jsonb_array_length(payload->'domain_ids');
  end if;

  -- Insert snaps
  v_snaps_count := 0;
  if payload ? 'snaps' then
    for v_snap in select * from jsonb_array_elements(payload->'snaps')
    loop
      insert into public.snaps (pebble_id, user_id, storage_path, sort_order)
      values (
        v_pebble_id,
        v_user_id,
        v_snap.value->>'storage_path',
        coalesce((v_snap.value->>'sort_order')::smallint, 0)
      );
      v_snaps_count := v_snaps_count + 1;
    end loop;
  end if;

  -- Compute and insert karma
  v_karma := public.compute_karma_delta(
    payload->>'description',
    v_cards_count,
    v_souls_count,
    v_domains_count,
    v_glyph_id is not null,
    v_snaps_count
  );

  insert into public.karma_events (user_id, delta, reason, ref_id)
  values (v_user_id, v_karma, 'pebble_created', v_pebble_id);

  return v_pebble_id;
end;
$$ language plpgsql security definer;

-- ============================================================
-- update_pebble(pebble_id uuid, payload jsonb) → void
-- ============================================================
-- Atomically updates a pebble and its related data.
-- Only provided fields are updated. Array fields are replaced wholesale.

create function public.update_pebble(p_pebble_id uuid, payload jsonb)
returns void as $$
declare
  v_user_id uuid := auth.uid();
  v_glyph_id uuid;
  v_soul_ids uuid[];
  v_new_soul record;
  v_new_soul_id uuid;
  v_card record;
  v_snap record;
  v_old_karma int;
  v_new_karma int;
  v_description text;
  v_cards_count int;
  v_souls_count int;
  v_domains_count int;
  v_has_glyph boolean;
  v_snaps_count int;
begin
  -- Verify ownership
  if not exists (
    select 1 from public.pebbles
    where id = p_pebble_id and user_id = v_user_id
  ) then
    raise exception 'Pebble not found or access denied';
  end if;

  -- Inline glyph creation
  if payload ? 'new_glyph' then
    insert into public.glyphs (user_id, name, shape_id, strokes, view_box)
    values (
      v_user_id,
      (payload->'new_glyph'->>'name'),
      (payload->'new_glyph'->>'shape_id')::uuid,
      coalesce(payload->'new_glyph'->'strokes', '[]'::jsonb),
      (payload->'new_glyph'->>'view_box')
    )
    returning id into v_glyph_id;

    -- Force glyph_id into the payload for the UPDATE below
    payload := payload || jsonb_build_object('glyph_id', v_glyph_id::text);
  end if;

  -- Inline soul creation
  if payload ? 'new_souls' then
    -- Start with existing soul_ids if provided
    select array_agg(val::uuid)
    into v_soul_ids
    from jsonb_array_elements_text(coalesce(payload->'soul_ids', '[]'::jsonb)) val;

    for v_new_soul in select * from jsonb_array_elements(payload->'new_souls')
    loop
      insert into public.souls (user_id, name)
      values (v_user_id, v_new_soul.value->>'name')
      returning id into v_new_soul_id;

      v_soul_ids := array_append(v_soul_ids, v_new_soul_id);
    end loop;

    -- Override soul_ids in payload with merged list
    payload := payload || jsonb_build_object(
      'soul_ids', to_jsonb(v_soul_ids)
    );
  end if;

  -- Update scalar fields (only those present in payload)
  update public.pebbles set
    name          = coalesce(payload->>'name', name),
    description   = case when payload ? 'description' then payload->>'description' else description end,
    happened_at   = coalesce((payload->>'happened_at')::timestamptz, happened_at),
    intensity     = coalesce((payload->>'intensity')::smallint, intensity),
    positiveness  = coalesce((payload->>'positiveness')::smallint, positiveness),
    visibility    = coalesce(payload->>'visibility', visibility),
    emotion_id    = coalesce((payload->>'emotion_id')::uuid, emotion_id),
    glyph_id      = case when payload ? 'glyph_id' then (payload->>'glyph_id')::uuid else glyph_id end
  where id = p_pebble_id;

  -- Replace cards
  if payload ? 'cards' then
    delete from public.pebble_cards where pebble_id = p_pebble_id;

    for v_card in select * from jsonb_array_elements(payload->'cards')
    loop
      insert into public.pebble_cards (pebble_id, species_id, value, sort_order)
      values (
        p_pebble_id,
        (v_card.value->>'species_id')::uuid,
        v_card.value->>'value',
        coalesce((v_card.value->>'sort_order')::smallint, 0)
      );
    end loop;
  end if;

  -- Replace souls
  if payload ? 'soul_ids' then
    delete from public.pebble_souls where pebble_id = p_pebble_id;

    insert into public.pebble_souls (pebble_id, soul_id)
    select p_pebble_id, (val::text)::uuid
    from jsonb_array_elements_text(payload->'soul_ids') val;
  end if;

  -- Replace domains
  if payload ? 'domain_ids' then
    delete from public.pebble_domains where pebble_id = p_pebble_id;

    insert into public.pebble_domains (pebble_id, domain_id)
    select p_pebble_id, (val::text)::uuid
    from jsonb_array_elements_text(payload->'domain_ids') val;
  end if;

  -- Replace snaps
  if payload ? 'snaps' then
    delete from public.snaps where pebble_id = p_pebble_id;

    for v_snap in select * from jsonb_array_elements(payload->'snaps')
    loop
      insert into public.snaps (pebble_id, user_id, storage_path, sort_order)
      values (
        p_pebble_id,
        v_user_id,
        v_snap.value->>'storage_path',
        coalesce((v_snap.value->>'sort_order')::smallint, 0)
      );
    end loop;
  end if;

  -- Recompute karma
  select p.description into v_description from public.pebbles p where p.id = p_pebble_id;
  select count(*) into v_cards_count from public.pebble_cards where pebble_id = p_pebble_id;
  select count(*) into v_souls_count from public.pebble_souls where pebble_id = p_pebble_id;
  select count(*) into v_domains_count from public.pebble_domains where pebble_id = p_pebble_id;
  select glyph_id is not null into v_has_glyph from public.pebbles where id = p_pebble_id;
  select count(*) into v_snaps_count from public.snaps where pebble_id = p_pebble_id;

  v_new_karma := public.compute_karma_delta(
    v_description, v_cards_count, v_souls_count,
    v_domains_count, v_has_glyph, v_snaps_count
  );

  -- Get previous karma for this pebble
  select coalesce(sum(ke.delta), 0) into v_old_karma
  from public.karma_events ke
  where ke.ref_id = p_pebble_id and ke.user_id = v_user_id;

  -- Insert adjustment if karma changed
  if v_new_karma <> v_old_karma then
    insert into public.karma_events (user_id, delta, reason, ref_id)
    values (v_user_id, v_new_karma - v_old_karma, 'pebble_enriched', p_pebble_id);
  end if;
end;
$$ language plpgsql security definer;

-- ============================================================
-- delete_pebble(pebble_id uuid) → void
-- ============================================================
-- Deletes a pebble and reverses its karma.

create function public.delete_pebble(p_pebble_id uuid)
returns void as $$
declare
  v_user_id uuid := auth.uid();
  v_total_karma int;
begin
  -- Verify ownership
  if not exists (
    select 1 from public.pebbles
    where id = p_pebble_id and user_id = v_user_id
  ) then
    raise exception 'Pebble not found or access denied';
  end if;

  -- Calculate total karma earned by this pebble
  select coalesce(sum(ke.delta), 0) into v_total_karma
  from public.karma_events ke
  where ke.ref_id = p_pebble_id and ke.user_id = v_user_id;

  -- Insert negative karma event to reverse
  if v_total_karma > 0 then
    insert into public.karma_events (user_id, delta, reason, ref_id)
    values (v_user_id, -v_total_karma, 'pebble_deleted', p_pebble_id);
  end if;

  -- Delete pebble (cascades to cards, snaps, join tables)
  delete from public.pebbles where id = p_pebble_id;
end;
$$ language plpgsql security definer;
```

- [ ] **Step 3: Reset DB and verify migration**

```bash
npm run db:reset
```

Expected: all four migrations run without errors.

- [ ] **Step 4: Verify functions exist**

```bash
npx supabase db execute --sql "
  select routine_name, routine_type
  from information_schema.routines
  where routine_schema = 'public'
    and routine_type = 'FUNCTION'
    and routine_name in ('create_pebble', 'update_pebble', 'delete_pebble', 'compute_karma_delta', 'set_updated_at')
  order by routine_name;
"
```

Expected: 5 functions listed.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/
git commit -m "feat(db): add RPC functions for atomic pebble operations"
```

---

### Task 5: End-to-End Verification

**Files:** None (verification only).

This task runs a full integration test against the local Supabase instance to verify the complete schema works end-to-end.

- [ ] **Step 1: Reset to a clean state**

```bash
cd packages/supabase && npm run db:reset
```

- [ ] **Step 2: Verify reference data counts**

```bash
npx supabase db execute --sql "
  select 'emotions' as tbl, count(*) as n from emotions
  union all select 'domains', count(*) from domains
  union all select 'card_types', count(*) from card_types
  union all select 'pebble_shapes', count(*) from pebble_shapes;
"
```

Expected: 16, 5, 4, 6.

- [ ] **Step 3: Test create_pebble RPC with inline entities**

This test bypasses RLS by running as the postgres role directly. It sets up a test user, then exercises the full RPC flow.

```bash
npx supabase db execute --sql "
  -- Create a test user in auth.users
  insert into auth.users (id, email, instance_id, aud, role, encrypted_password, email_confirmed_at, created_at, updated_at, confirmation_token, recovery_token, email_change_token_new, email_change_token_current)
  values (
    'a1b2c3d4-0000-0000-0000-000000000001',
    'test@pebbles.app',
    '00000000-0000-0000-0000-000000000000',
    'authenticated',
    'authenticated',
    crypt('password', gen_salt('bf')),
    now(), now(), now(), '', '', '', ''
  );

  -- Set the auth context so auth.uid() returns our test user
  set local role authenticated;
  set local request.jwt.claims to '{\"sub\": \"a1b2c3d4-0000-0000-0000-000000000001\", \"role\": \"authenticated\"}';

  -- Create profile
  insert into public.profiles (user_id, display_name)
  values ('a1b2c3d4-0000-0000-0000-000000000001', 'Test User');

  -- Get a valid emotion_id and domain_id and card_type_id
  -- Then call create_pebble
  do \$\$
  declare
    v_emotion_id uuid;
    v_domain_id uuid;
    v_card_type_id uuid;
    v_pebble_id uuid;
  begin
    select id into v_emotion_id from emotions where slug = 'joy';
    select id into v_domain_id from domains where slug = 'philia';
    select id into v_card_type_id from card_types where slug = 'feelings';

    v_pebble_id := public.create_pebble(jsonb_build_object(
      'name', 'Morning walk with Mia',
      'description', 'Beautiful sunrise together',
      'happened_at', '2026-04-11T07:30:00Z',
      'intensity', 2,
      'positiveness', 1,
      'emotion_id', v_emotion_id,
      'new_souls', jsonb_build_array(jsonb_build_object('name', 'Mia')),
      'domain_ids', jsonb_build_array(v_domain_id),
      'cards', jsonb_build_array(jsonb_build_object(
        'species_id', v_card_type_id,
        'value', 'Felt deeply grateful',
        'sort_order', 0
      ))
    ));

    raise notice 'Created pebble: %', v_pebble_id;
  end;
  \$\$;
"
```

Expected: pebble created without errors, notice shows the UUID.

- [ ] **Step 4: Verify data was distributed correctly**

```bash
npx supabase db execute --sql "
  -- Reset role for direct queries
  reset role;

  select 'pebbles' as tbl, count(*) as n from pebbles
  union all select 'souls', count(*) from souls
  union all select 'pebble_cards', count(*) from pebble_cards
  union all select 'pebble_souls', count(*) from pebble_souls
  union all select 'pebble_domains', count(*) from pebble_domains
  union all select 'karma_events', count(*) from karma_events;
"
```

Expected: pebbles=1, souls=1, pebble_cards=1, pebble_souls=1, pebble_domains=1, karma_events=1.

- [ ] **Step 5: Verify v_pebbles_full view returns consolidated data**

```bash
npx supabase db execute --sql "
  select name, emotion->>'name' as emotion, souls, domains, cards
  from v_pebbles_full
  limit 1;
"
```

Expected: one row with name='Morning walk with Mia', emotion='Joy', souls array with Mia, domains array with Philía, cards array with one entry.

- [ ] **Step 6: Verify karma was computed correctly**

```bash
npx supabase db execute --sql "
  select total_karma, pebbles_count
  from v_karma_summary
  where user_id = 'a1b2c3d4-0000-0000-0000-000000000001';
"
```

Expected: total_karma=5 (base 1 + description 1 + 1 card + 1 soul + 1 domain), pebbles_count=1.

- [ ] **Step 7: Test delete_pebble reverses karma**

```bash
npx supabase db execute --sql "
  set local role authenticated;
  set local request.jwt.claims to '{\"sub\": \"a1b2c3d4-0000-0000-0000-000000000001\", \"role\": \"authenticated\"}';

  select public.delete_pebble(id) from pebbles limit 1;

  reset role;

  select total_karma, pebbles_count
  from v_karma_summary
  where user_id = 'a1b2c3d4-0000-0000-0000-000000000001';
"
```

Expected: total_karma=0, pebbles_count=0.

- [ ] **Step 8: Verify cascade cleanup**

```bash
npx supabase db execute --sql "
  select 'pebble_cards' as tbl, count(*) as n from pebble_cards
  union all select 'pebble_souls', count(*) from pebble_souls
  union all select 'pebble_domains', count(*) from pebble_domains;
"
```

Expected: all counts = 0.

- [ ] **Step 9: Commit any verification fixes**

If any issues were found and fixed during verification, commit the fixes:

```bash
git add packages/supabase/supabase/migrations/
git commit -m "fix(db): address issues found during e2e verification"
```

If no fixes were needed, skip this step.

- [ ] **Step 10: Run final db:reset to confirm clean state**

```bash
npm run db:reset
```

Expected: all migrations run cleanly from scratch.
