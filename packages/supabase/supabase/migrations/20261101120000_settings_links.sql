-- Migration: settings_links
-- The links every client shows in its Settings (#1489, spec 2026-11-01).
-- Modelled on guides (20261027090000): EN required, FR falls back to EN on the
-- client, public read gated on `published`, writes admin-only. Not user data
-- (no purge_account append); no emotion/domain (no sync_achievement_catalog).

create table public.settings_links (
  id          uuid primary key default gen_random_uuid(),
  key         text not null unique check (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  section     text not null check (section in ('legal','about','support')),
  sort_order  smallint not null default 0,
  title_en    text not null check (btrim(title_en) <> ''),
  title_fr    text,
  url         text not null check (url ~ '^(https://|mailto:)'),
  published   boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.settings_links is
  'Links shown in every client''s Settings (#1489). Edited in the admin; clients read published rows by section then sort_order, and fall back to EN when FR is null.';

create trigger settings_links_updated_at
  before update on public.settings_links
  for each row execute function public.set_updated_at();

alter table public.settings_links enable row level security;

create policy "settings_links_select" on public.settings_links
  for select using (published = true or public.is_admin(auth.uid()));
create policy "settings_links_insert" on public.settings_links
  for insert with check (public.is_admin(auth.uid()));
create policy "settings_links_update" on public.settings_links
  for update using (public.is_admin(auth.uid()));
create policy "settings_links_delete" on public.settings_links
  for delete using (public.is_admin(auth.uid()));

create index settings_links_published_idx on public.settings_links (published, section, sort_order);

insert into public.settings_links (id, key, section, sort_order, title_en, title_fr, url, published) values
  (md5('settings_links:terms')::uuid,        'terms',        'legal',   10, 'Terms of Service', 'Conditions générales d''utilisation', 'https://www.pbbls.app/docs/terms',        true),
  (md5('settings_links:privacy')::uuid,      'privacy',      'legal',   20, 'Privacy Policy',   'Politique de confidentialité',        'https://www.pbbls.app/docs/privacy',      true),
  (md5('settings_links:legal-notice')::uuid, 'legal-notice', 'legal',   30, 'Legal Notice',     'Mentions légales',                    'https://www.pbbls.app/docs/legal-notice', true),
  (md5('settings_links:credits')::uuid,      'credits',      'about',   10, 'Credits',          'Crédits',                             'https://www.pbbls.app/docs/credits',      true),
  (md5('settings_links:support')::uuid,      'support',      'support', 10, 'Contact support',  'Contacter le support',                'mailto:ciao@pbbls.app',                   true);
