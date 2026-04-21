-- Migration: Lab Assets Storage Bucket
-- Public-read bucket for announcement cover images and inline assets.
-- Writes are admin-only. iOS/web read via public URLs.

-- ============================================================
-- BUCKET
-- ============================================================

insert into storage.buckets (id, name, public)
values ('lab-assets', 'lab-assets', true)
on conflict (id) do nothing;

-- ============================================================
-- OBJECT POLICIES
-- ============================================================
-- storage.objects already has RLS enabled by Supabase. We add
-- bucket-scoped policies without touching other buckets.

create policy "lab_assets_public_read" on storage.objects
  for select using (bucket_id = 'lab-assets');

create policy "lab_assets_admin_insert" on storage.objects
  for insert with check (
    bucket_id = 'lab-assets' and public.is_admin(auth.uid())
  );

create policy "lab_assets_admin_update" on storage.objects
  for update using (
    bucket_id = 'lab-assets' and public.is_admin(auth.uid())
  );

create policy "lab_assets_admin_delete" on storage.objects
  for delete using (
    bucket_id = 'lab-assets' and public.is_admin(auth.uid())
  );
