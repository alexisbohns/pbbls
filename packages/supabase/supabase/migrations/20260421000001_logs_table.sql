-- Migration: Logs Table
-- Central product-transparency log: announcements, features across the pipeline
-- (backlog → planned → in_progress → shipped). Drives the iOS Lab tab.
-- Bilingual (EN required, FR optional fallback to EN). Public read is gated
-- on `published`; mutations are admin-only.

-- ============================================================
-- TABLE
-- ============================================================

create table public.logs (
  id uuid primary key default gen_random_uuid(),
  species text not null check (species in ('announcement','feature')),
  platform text not null check (platform in ('web','ios','android','all')),
  status text not null check (status in ('backlog','planned','in_progress','shipped')),

  title_en text not null,
  title_fr text,
  summary_en text not null,
  summary_fr text,
  body_md_en text,
  body_md_fr text,

  cover_image_path text,
  external_url text,

  published boolean not null default false,
  published_at timestamptz,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger logs_updated_at
  before update on public.logs
  for each row execute function public.set_updated_at();

-- ============================================================
-- ROW LEVEL SECURITY
-- ============================================================

alter table public.logs enable row level security;

-- Anyone can read published logs; admins see unpublished drafts too.
create policy "logs_select" on public.logs
  for select using (published = true or public.is_admin(auth.uid()));

-- Only admins can write.
create policy "logs_insert" on public.logs
  for insert with check (public.is_admin(auth.uid()));

create policy "logs_update" on public.logs
  for update using (public.is_admin(auth.uid()));

create policy "logs_delete" on public.logs
  for delete using (public.is_admin(auth.uid()));

-- ============================================================
-- INDEXES
-- ============================================================

create index logs_status_idx on public.logs (status);
create index logs_species_idx on public.logs (species);
create index logs_published_at_idx on public.logs (published_at desc);
