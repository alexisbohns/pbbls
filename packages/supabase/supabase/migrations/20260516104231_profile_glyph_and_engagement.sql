-- ============================================================
-- Migration: profile glyph FK + engagement RPC
-- ------------------------------------------------------------
-- 1. Adds profiles.glyph_id (nullable FK to glyphs).
-- 2. update_profile(p_display_name, p_glyph_id):
--    atomic field-level edit. Null args mean "do not change".
--    Relies on profiles_update RLS (user_id = auth.uid()).
-- 3. get_profile_engagement(p_tz):
--    returns days_practiced (all-time distinct days with a
--    pebble) and assiduity (28-element bool[] for the last 28
--    days), both bucketed in the caller's IANA timezone.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Schema: profiles.glyph_id
-- ------------------------------------------------------------

alter table public.profiles
  add column glyph_id uuid references public.glyphs(id) on delete set null;

create index profiles_glyph_id_idx on public.profiles(glyph_id);

-- ------------------------------------------------------------
-- 2. update_profile
-- ------------------------------------------------------------
-- Returns the updated profile row (or no row if RLS blocked /
-- profile missing — caller treats absence as a not-found error).
-- Both args default to null; null means "leave column unchanged".
-- We intentionally cannot clear glyph_id via this RPC: there is
-- no UX for it. A future "remove glyph" feature should add a
-- dedicated p_clear_glyph boolean rather than re-interpreting null.

create or replace function public.update_profile(
  p_display_name text default null,
  p_glyph_id     uuid default null
)
returns public.profiles
language sql
security invoker
set search_path = public
as $$
  update public.profiles
     set display_name = coalesce(p_display_name, display_name),
         glyph_id     = case
                          when p_glyph_id is not null then p_glyph_id
                          else glyph_id
                        end,
         updated_at   = now()
   where user_id = auth.uid()
   returning *;
$$;

grant execute on function public.update_profile(text, uuid) to authenticated;

-- ------------------------------------------------------------
-- 3. get_profile_engagement
-- ------------------------------------------------------------
-- p_tz: IANA timezone string (e.g. 'Europe/Paris'). The client
-- passes TimeZone.current.identifier on iOS.
--
-- Returns:
--   days_practiced int       — all-time distinct days the user
--                              has created at least one pebble,
--                              bucketed in p_tz.
--   assiduity     boolean[]  — length 28. Index 1 = 27 days ago,
--                              index 28 = today (in p_tz). true
--                              when ≥1 pebble was created on
--                              that local-day.
--
-- security invoker: RLS on public.pebbles already restricts to
-- the caller's rows (user_id = auth.uid()).

create or replace function public.get_profile_engagement(p_tz text)
returns table (
  days_practiced int,
  assiduity      boolean[]
)
language sql
security invoker
stable
set search_path = public
as $$
  with
    today_local as (
      select (now() at time zone p_tz)::date as d
    ),
    window_days as (
      select generate_series(
        (select d from today_local) - interval '27 days',
        (select d from today_local),
        interval '1 day'
      )::date as d
    ),
    active_days as (
      select distinct (p.created_at at time zone p_tz)::date as d
        from public.pebbles p
       where p.user_id = auth.uid()
    ),
    grid as (
      select array_agg((ad.d is not null) order by w.d) as assiduity
        from window_days w
        left join active_days ad using (d)
    ),
    counts as (
      select count(*)::int as days_practiced from active_days
    )
  select counts.days_practiced, grid.assiduity
    from counts, grid;
$$;

grant execute on function public.get_profile_engagement(text) to authenticated;
