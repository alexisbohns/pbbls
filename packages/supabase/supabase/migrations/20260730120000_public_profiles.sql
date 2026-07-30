-- =============================================================================
-- Public profiles (#654, M50) — handle + public_profile + reserved_handles +
-- set_handle + get_public_profile
-- =============================================================================
-- Opt-in public profile pages. Two explicit opt-ins: claim a handle, then flip
-- public_profile. Design: docs/superpowers/specs/
-- 2026-07-29-public-profiles-design.md (D1–D4, D8).
--
-- profiles RLS is deliberately NOT widened (it carries is_admin, consent
-- timestamps and quota columns). The public read is a definer-RPC *projection*
-- (get_public_profile), the first anon-granted row-data function in the schema
-- (only the boolean is_admin(uuid) predates it on the anon surface) — the
-- standing pattern for every future cross-user read (roadmap §5 item 8).
--
-- purge_account needs no extension for M50 (standing-rule check, design D8):
-- handle/public_profile live on profiles, whose row purge already deletes —
-- deleting an account frees its handle — and reserved_handles is admin data.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. profiles: handle + public_profile, invariants as constraints.
-- profiles_update RLS lets owners write columns directly, so validity cannot
-- live only in set_handle — the CHECKs make invalid states unrepresentable
-- for every write path. The unique index is the claim-race settler: two
-- concurrent claims resolve to one winner and one unique_violation.
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column handle text,
  add column public_profile boolean not null default false;

create unique index profiles_handle_key on public.profiles (handle);

-- Lowercase a-z0-9_, 3–30 chars, starts/ends alphanumeric. Stored normalized
-- (set_handle lowercases); the CHECK rejects direct writes that skip it.
alter table public.profiles
  add constraint profiles_handle_format
    check (handle is null or handle ~ '^[a-z0-9][a-z0-9_]{1,28}[a-z0-9]$');

-- "Public but unreachable" (no handle → no URL) is unrepresentable.
alter table public.profiles
  add constraint profiles_public_requires_handle
    check (public_profile = false or handle is not null);

-- ---------------------------------------------------------------------------
-- 2. reserved_handles: route names, infra/brand, impersonation-prone terms.
-- Admin-extensible WITHOUT a migration: is_admin-gated insert/delete policies,
-- so an admin can reserve a name from the back office and it binds immediately
-- (the guard trigger below reads this table live).
-- Some seeds (u, p, sw) are shorter than the format minimum and thus doubly
-- blocked — kept anyway to document intent and to survive a future loosening
-- of the format rule.
-- ---------------------------------------------------------------------------
create table public.reserved_handles (
  handle text primary key
);

alter table public.reserved_handles enable row level security;

create policy reserved_handles_select on public.reserved_handles
  for select using (true);

create policy reserved_handles_admin_insert on public.reserved_handles
  for insert to authenticated with check (public.is_admin(auth.uid()));

create policy reserved_handles_admin_delete on public.reserved_handles
  for delete to authenticated using (public.is_admin(auth.uid()));

insert into public.reserved_handles (handle) values
  -- top-level web routes, current and already-specced (M49 invite, M51 /p)
  ('path'), ('record'), ('pebble'), ('drafts'), ('collections'), ('souls'),
  ('glyphs'), ('carve'), ('wallet'), ('lab'), ('docs'), ('profile'),
  ('settings'), ('offline'), ('login'), ('register'), ('onboarding'),
  ('auth'), ('sw'), ('u'), ('p'), ('invite'),
  -- infra / brand
  ('admin'), ('api'), ('www'), ('app'), ('pebbles'), ('pbbls'), ('root'),
  ('system'), ('official'), ('store'),
  -- impersonation / abuse-prone
  ('support'), ('help'), ('about'), ('contact'), ('legal'), ('terms'),
  ('privacy'), ('security'), ('status'), ('team'), ('staff'), ('mod'),
  ('moderator'), ('null'), ('undefined');

-- ---------------------------------------------------------------------------
-- 3. Guard trigger: the reserved list must bind DIRECT writes too, not only
-- the set_handle path — a client .update({ handle: 'admin' }) passes the
-- format CHECK and profiles_update RLS. A CHECK cannot reference another
-- table, hence a trigger. security definer so the read works regardless of
-- the writing role's grants (precedent: handle_new_user).
-- ---------------------------------------------------------------------------
create function public.enforce_reserved_handle()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.handle is not null
     and exists (select 1 from public.reserved_handles rh where rh.handle = new.handle) then
    raise exception 'handle_reserved';
  end if;
  return new;
end;
$$;

create trigger profiles_handle_guard
  before insert or update of handle on public.profiles
  for each row execute function public.enforce_reserved_handle();

-- ---------------------------------------------------------------------------
-- 4. set_handle: the sanctioned claim/change/release path. security invoker —
-- profiles_update RLS scopes the write; the function adds normalization and
-- stable, client-facing error codes (invalid_handle / handle_reserved /
-- handle_taken), checked in friendly order before the structural guards fire.
--
-- set_handle(null) releases the handle and drops public_profile in the same
-- statement (profiles_public_requires_handle would reject the orphaned flag).
-- A released handle is immediately claimable by others: no handle history, no
-- redirects (decision log, 2026-07-30).
--
-- The public_profile toggle itself is a direct single-column client update
-- (single-table single-statement, root AGENTS.md) — no RPC.
-- ---------------------------------------------------------------------------
create function public.set_handle(p_handle text default null)
returns text
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_handle text := lower(trim(p_handle));
  v_result text;
begin
  if v_handle is null or v_handle = '' then
    update public.profiles
       set handle = null,
           public_profile = false,
           updated_at = now()
     where user_id = auth.uid();
    if not found then
      raise exception 'not_found';
    end if;
    return null;
  end if;

  if v_handle !~ '^[a-z0-9][a-z0-9_]{1,28}[a-z0-9]$' then
    raise exception 'invalid_handle';
  end if;

  if exists (select 1 from public.reserved_handles rh where rh.handle = v_handle) then
    raise exception 'handle_reserved';
  end if;

  begin
    update public.profiles
       set handle = v_handle,
           updated_at = now()
     where user_id = auth.uid()
    returning handle into v_result;
  exception
    when unique_violation then
      raise exception 'handle_taken';
  end;

  if v_result is null then
    raise exception 'not_found';
  end if;
  return v_result;
end;
$$;

revoke all on function public.set_handle(text) from public, anon;
grant execute on function public.set_handle(text) to authenticated;

-- ---------------------------------------------------------------------------
-- 5. get_public_profile: the anon-facing projection. Returns null unless the
-- handle exists AND its owner opted in — unknown and known-but-private are
-- indistinguishable to the caller (enumeration resistance).
--
-- Engagement is recomputed here for the target user instead of un-scoping
-- v_ripple / v_bounce / get_profile_engagement (roadmap §M50; the trailing
-- auth.uid() views stay untouched, per the 2026-07-29 #616 decision new code
-- must not copy them). Bucket math is verbatim from those definitions so the
-- owner and visitors see the same levels: ripple counts pebbles by created_at
-- over 28 days (20260516000001), bounce counts distinct happened_at days over
-- 28 days (20260411000005). The assiduity grid and days_practiced mirror
-- get_profile_engagement (20260516104231) but are fixed to UTC — a visitor
-- has no claim on the owner's timezone; the owner's tz-aware RPC is untouched.
--
-- Projected keys only — deliberately excluded: user_id (no stable cross-user
-- identifier leaks from the public surface), is_admin, consent timestamps,
-- quotas, karma, color_world, the raw counts behind the levels, and
-- active_today (a presence signal). The avatar glyph is projected as raw
-- geometry (strokes + view_box, the v_pebbles_full embed shape); glyphs RLS
-- is not widened either. 'achievements' is a stable empty array until M48
-- ships achievement_unlocks — M48 extends this function in place; clients
-- render the key from day one and need no contract change.
-- ---------------------------------------------------------------------------
create function public.get_public_profile(p_handle text)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  with target as (
    select p.user_id, p.display_name, p.handle, p.glyph_id, p.created_at
      from public.profiles p
     where p.handle = lower(trim(p_handle))
       and p.public_profile = true
  ),
  utc_today as (
    select (now() at time zone 'UTC')::date as d
  ),
  window_days as (
    select generate_series(
      (select d from utc_today) - interval '27 days',
      (select d from utc_today),
      interval '1 day'
    )::date as d
  ),
  active_days_utc as (
    select distinct (pb.created_at at time zone 'UTC')::date as d
      from public.pebbles pb
     where pb.user_id = (select user_id from target)
  ),
  grid as (
    select array_agg((ad.d is not null) order by w.d) as assiduity
      from window_days w
      left join active_days_utc ad using (d)
  ),
  ripple as (
    select count(*)::int as pebbles_28d
      from public.pebbles pb
     where pb.user_id = (select user_id from target)
       and pb.created_at >= now() - interval '28 days'
  ),
  bounce as (
    select count(distinct date(pb.happened_at))::int as active_days
      from public.pebbles pb
     where pb.user_id = (select user_id from target)
       and pb.happened_at >= now() - interval '28 days'
  )
  select jsonb_build_object(
    'display_name', t.display_name,
    'handle', t.handle,
    'glyph', (
      select jsonb_build_object('strokes', g.strokes, 'view_box', g.view_box)
        from public.glyphs g
       where g.id = t.glyph_id
    ),
    'pebbles_count', (
      select count(*)::int from public.pebbles pb where pb.user_id = t.user_id
    ),
    'ripple_level', (
      select case
        when r.pebbles_28d = 0                  then 0
        when r.pebbles_28d between  1 and  4    then 1
        when r.pebbles_28d between  5 and  8    then 2
        when r.pebbles_28d between  9 and 12    then 3
        when r.pebbles_28d between 13 and 16    then 4
        when r.pebbles_28d between 17 and 20    then 5
        else 6
      end from ripple r
    ),
    'bounce_level', (
      select case
        when b.active_days = 0                  then 0
        when b.active_days between  1 and  5    then 1
        when b.active_days between  6 and  9    then 2
        when b.active_days between 10 and 13    then 3
        when b.active_days between 14 and 17    then 4
        when b.active_days between 18 and 20    then 5
        when b.active_days between 21 and 24    then 6
        else 7
      end from bounce b
    ),
    'assiduity', (select to_jsonb(gr.assiduity) from grid gr),
    'days_practiced', (select count(*)::int from active_days_utc),
    'member_since', (t.created_at at time zone 'UTC')::date,
    'achievements', '[]'::jsonb
  )
  from target t;
$$;

revoke all on function public.get_public_profile(text) from public;
grant execute on function public.get_public_profile(text) to anon, authenticated;
