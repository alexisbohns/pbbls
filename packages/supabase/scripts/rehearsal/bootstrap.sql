-- Roles the dump's grants and policies name.
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then create role service_role nologin bypassrls; end if;
  if not exists (select 1 from pg_roles where rolname = 'supabase_auth_admin') then create role supabase_auth_admin nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'supabase_storage_admin') then create role supabase_storage_admin nologin; end if;
end $$;
create extension if not exists pgcrypto;
create extension if not exists "uuid-ossp";
-- auth.uid() reads a GUC so a test can impersonate a user: set request.jwt.claim.sub = '<uuid>'
create schema if not exists auth;
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create or replace function auth.role() returns text language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claim.role', true), ''), 'anon')
$$;
-- storage.foldername, used by list_orphan_snap_files
create schema if not exists storage;
create or replace function storage.foldername(name text) returns text[] language plpgsql immutable as $$
declare parts text[];
begin
  select string_to_array(name, '/') into parts;
  return parts[1 : array_length(parts, 1) - 1];
end $$;
-- Minimal shells of the three platform tables the public schema references.
-- run.sh copies only these columns from the live tables (see README).
create table if not exists auth.users (
  id uuid primary key,
  email text,
  raw_user_meta_data jsonb,
  created_at timestamptz
);
create table if not exists storage.buckets (
  id text primary key,
  name text
);
create table if not exists storage.objects (
  id uuid primary key,
  bucket_id text,
  name text,
  metadata jsonb,
  created_at timestamptz
);
