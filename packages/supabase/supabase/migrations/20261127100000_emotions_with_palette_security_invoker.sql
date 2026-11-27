-- v_emotions_with_palette runs with invoker rights (#1713).
--
-- The Supabase advisor flags it under lint 0010 (Security Definer View). It
-- joins emotions and emotion_categories, reference tables whose select
-- policies (emotions_select, emotion_categories_select) are `using (true)` for
-- every role, so anon and authenticated get exactly the same rows as before.
-- Invoker rights keep it that way if a later column or join reaches beyond
-- reference data (decision log 2026-07-29).

alter view public.v_emotions_with_palette set (security_invoker = true);
