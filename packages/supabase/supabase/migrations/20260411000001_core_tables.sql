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
