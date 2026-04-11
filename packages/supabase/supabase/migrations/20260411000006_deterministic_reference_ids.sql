-- Migration: Deterministic Reference IDs
-- Replace auto-generated UUIDs in reference tables with deterministic values
-- derived from md5('<table>:<slug>').
-- This aligns database IDs with the client-side config which uses the same
-- deterministic UUIDs, enabling the SupabaseProvider to push/pull pebbles
-- without a slug↔UUID mapping layer.
--
-- Safe to run: no user data references these IDs yet (fresh deployment).

-- ============================================================
-- 1. Emotions
-- ============================================================

update public.emotions set id = md5('emotions:' || slug)::uuid;

-- ============================================================
-- 2. Domains
-- ============================================================

update public.domains set id = md5('domains:' || slug)::uuid;

-- ============================================================
-- 3. Card types
-- ============================================================

update public.card_types set id = md5('card_types:' || slug)::uuid;

-- ============================================================
-- 4. Pebble shapes
-- ============================================================

update public.pebble_shapes set id = md5('pebble_shapes:' || slug)::uuid;
