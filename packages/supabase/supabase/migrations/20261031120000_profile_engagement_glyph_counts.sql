-- ============================================================
-- Migration: get_profile_engagement gains glyph counts (#1386)
-- ------------------------------------------------------------
-- The You tab's Glyphs section (iOS #1382, Android #1383, web
-- #1384) shows how many glyphs the reader carved and how many
-- they own. One definition of each, for every surface:
--
--   glyphs_carved int — the caller's own rows in public.glyphs
--                       (user_id = auth.uid()). System glyphs
--                       are ownerless, so never counted.
--   glyphs_owned  int — the caller's rows in
--                       public.glyph_entitlements (glyphs bought
--                       from the marketplace).
--
-- Re-emits 20260516104231's body unchanged (the only prior
-- definition; 20260730120000 only mentions it in a comment) and
-- appends the two counts. `returns table` changes shape, which
-- `create or replace` cannot do, so the function is dropped and
-- recreated. Shipped readers decode by name and ignore extra
-- keys (Android pinned by ProfileEngagementDecodingTest, iOS by
-- its synthesized Decodable, web by reading the row's fields).
--
-- Grants: the original only granted to authenticated, so anon
-- kept Postgres' default PUBLIC execute and got a row of zeros.
-- The recreated function follows the house pattern of every
-- other caller-scoped RPC: revoked from public and anon.
-- ============================================================

drop function if exists public.get_profile_engagement(text);

create function public.get_profile_engagement(p_tz text)
returns table (
  days_practiced int,
  assiduity      boolean[],
  glyphs_carved  int,
  glyphs_owned   int
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
    ),
    carved as (
      select count(*)::int as glyphs_carved
        from public.glyphs g
       where g.user_id = auth.uid()
    ),
    owned as (
      select count(*)::int as glyphs_owned
        from public.glyph_entitlements e
       where e.user_id = auth.uid()
    )
  select counts.days_practiced, grid.assiduity, carved.glyphs_carved, owned.glyphs_owned
    from counts, grid, carved, owned;
$$;

revoke all on function public.get_profile_engagement(text) from public, anon;
grant execute on function public.get_profile_engagement(text) to authenticated;
