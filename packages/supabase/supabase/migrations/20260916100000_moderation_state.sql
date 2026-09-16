-- =============================================================================
-- Moderation state (#833, M56) — a reversible hidden_at replacing the
-- destructive takedown shipped in 20260916090000.
-- =============================================================================
-- resolve_content_report's `actioned` branch overwrote pebbles.visibility with
-- 'secret' and nulled a profile's handle. That gave the queue teeth, which was
-- the right first move, but it is the wrong shape: the takedown cannot be
-- undone (the previous visibility is gone), cannot be audited (a hidden pebble
-- is indistinguishable from one the user set to secret), and silently
-- overwrites a setting the user owns. Kritik F-2026-08-SAF-{admin-01,
-- supabase-03, web-04, ios-05} all say so independently.
--
-- Design: docs/superpowers/specs/2026-09-16-moderation-state-design.md
--
-- get_public_profile's gate lands in 20260916100100 (its own file — it is a
-- whole re-emission under the standing rule).
-- preview_connection_invite's gate belongs to #834, which already re-emits
-- that function this milestone; two migrations re-emitting one body is how
-- appends get silently dropped (20260731090000 exists to repair that accident).
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The state. Columns rather than a polymorphic takedown table: pebbles_select
-- runs on every timeline read for every row, so a `not exists` subquery buys a
-- join where a null check does. Partial indexes stay tiny — hidden rows are
-- rare by construction.
-- ---------------------------------------------------------------------------
alter table public.pebbles
  add column hidden_at timestamptz,
  add column hidden_by uuid references auth.users(id) on delete set null;

alter table public.profiles
  add column hidden_at timestamptz,
  add column hidden_by uuid references auth.users(id) on delete set null;

create index pebbles_hidden on public.pebbles (id) where hidden_at is not null;
create index profiles_hidden on public.profiles (user_id) where hidden_at is not null;

comment on column public.pebbles.hidden_at is
  'Set by moderation (admin_set_content_hidden). Null = not hidden. The owner still sees the row; every cross-user and anon path is dark.';
comment on column public.profiles.hidden_at is
  'Set by moderation. Suspends the PUBLIC profile only — established connections still see the person (design D4).';

-- ---------------------------------------------------------------------------
-- 2. THE GUARDS. Without these the feature is theatre.
--
-- pebbles_update and profiles_update are owner-scoped with no column
-- restriction (20260411000001:169), and Supabase grants UPDATE on the whole
-- table to authenticated — a table-level grant covers every column. So a user
-- whose content is hidden could `update pebbles set hidden_at = null` and
-- un-hide themselves.
--
-- SETTING is pinned as well as clearing: a user who can set hidden_at could
-- hide a pebble and later claim it was moderated.
--
-- Shape and exemption copied from enforce_profile_privileged_columns
-- (20260902090000): security INVOKER so current_user stays the role that
-- issued the statement, and postgres / service_role / any definer function
-- passes through — which is what lets admin_set_content_hidden below work.
-- ---------------------------------------------------------------------------
create function public.enforce_pebble_moderation_columns()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user not in ('authenticated', 'anon') then
    return new;
  end if;

  if new.hidden_at is distinct from old.hidden_at
     or new.hidden_by is distinct from old.hidden_by then
    raise exception 'pebbles_moderation_column'
      using hint = 'hidden_at and hidden_by are set by moderation, not by the owner.';
  end if;

  return new;
end;
$$;

-- `of <columns>` keeps the trigger off the hot path: an ordinary pebble edit
-- never fires it. Naming a pinned column in the SET list is what fires it.
create trigger pebbles_moderation_guard
  before update of hidden_at, hidden_by
  on public.pebbles
  for each row execute function public.enforce_pebble_moderation_columns();

-- profiles already has the guard; extend it. BOTH halves are required: adding
-- the columns to the function body without adding them to the trigger's
-- `before update of` list is a silent no-op, because the trigger would never
-- fire on a statement that touches only hidden_at.
create or replace function public.enforce_profile_privileged_columns()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user not in ('authenticated', 'anon') then
    return new;
  end if;

  if new.is_admin is distinct from old.is_admin
     or new.max_media_per_pebble is distinct from old.max_media_per_pebble
     or new.terms_accepted_at is distinct from old.terms_accepted_at
     or new.privacy_accepted_at is distinct from old.privacy_accepted_at
     or new.hidden_at is distinct from old.hidden_at
     or new.hidden_by is distinct from old.hidden_by then
    raise exception 'profiles_privileged_column'
      using hint = 'is_admin, max_media_per_pebble, the consent timestamps and the moderation columns are not client-writable.';
  end if;

  return new;
end;
$$;

drop trigger profiles_privileged_guard on public.profiles;

create trigger profiles_privileged_guard
  before update of is_admin, max_media_per_pebble, terms_accepted_at,
                   privacy_accepted_at, hidden_at, hidden_by
  on public.profiles
  for each row execute function public.enforce_profile_privileged_columns();

-- ---------------------------------------------------------------------------
-- 3. pebbles_select: the gate on the cross-user arms only.
--
-- The OWNER ARM IS DELIBERATELY UNGATED (design D2). Hiding a pebble from its
-- author would present as data loss — they would believe the app ate their
-- entry. Suspending someone's public reach and deleting their journal must not
-- look the same from inside the app.
--
-- Base: 20260817130000 §3, with `hidden_at is null` wrapped around the two
-- non-owner arms.
-- ---------------------------------------------------------------------------
drop policy "pebbles_select" on public.pebbles;

create policy "pebbles_select" on public.pebbles
  for select to authenticated using (
    user_id = auth.uid()
    or (
      hidden_at is null
      and (
        visibility = 'public'
        or (
          visibility = 'private'
          and exists (
            select 1
              from public.connections c
             where c.user_a = least(auth.uid(), pebbles.user_id)
               and c.user_b = greatest(auth.uid(), pebbles.user_id)
          )
        )
      )
    )
  );

-- ---------------------------------------------------------------------------
-- 4. get_shared_pebble: the anon path. Base: 20260817130000 §4, one predicate
-- added. A hidden pebble is null to a link holder — same shape as secret,
-- private and unknown, so the link cannot distinguish "taken down" from
-- "never existed".
-- ---------------------------------------------------------------------------
create or replace function public.get_shared_pebble(p_pebble_id uuid)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  select jsonb_build_object(
    'id', p.id,
    'name', p.name,
    'description', p.description,
    'happened_at', to_char(p.happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'intensity', p.intensity,
    'positiveness', p.positiveness,
    'render_svg', p.render_svg,
    'emotion', jsonb_build_object(
      'id', e.id,
      'slug', e.slug,
      'name', e.name,
      'color', e.color,
      'primary_color', c.primary_color,
      'secondary_color', c.secondary_color
    )
  )
  from public.pebbles p
  join public.emotions e on e.id = p.emotion_id
  left join public.emotion_categories c on c.id = e.category_id
  where p.id = p_pebble_id
    and p.visibility = 'public'
    and p.hidden_at is null;
$$;

-- ---------------------------------------------------------------------------
-- 5. admin_set_content_hidden — the toggle, and the APPEAL PATH.
--
-- Independent of any report, so an operator can act on a complaint that
-- arrived by other means, and can UN-hide wrongly-removed content, which
-- 20260916090000's destructive takedown made impossible.
-- ---------------------------------------------------------------------------
create function public.admin_set_content_hidden(
  p_target_kind text,
  p_target_id   uuid,
  p_hidden      boolean,
  p_note        text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin uuid := auth.uid();
  v_when  timestamptz := case when p_hidden then now() else null end;
  v_who   uuid := case when p_hidden then v_admin else null end;
  v_rows  integer;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  if p_target_kind = 'pebble' then
    update public.pebbles set hidden_at = v_when, hidden_by = v_who
     where id = p_target_id;
  elsif p_target_kind = 'profile' then
    update public.profiles set hidden_at = v_when, hidden_by = v_who
     where user_id = p_target_id;
  else
    -- Glyphs keep glyph_submissions.listed: it is already a reversible flag in
    -- the existing moderation vocabulary (design D3).
    raise exception 'invalid_kind';
  end if;

  get diagnostics v_rows = row_count;
  if v_rows = 0 then raise exception 'not_found'; end if;

  return jsonb_build_object(
    'target_kind', p_target_kind,
    'target_id', p_target_id,
    'hidden', p_hidden,
    'note', nullif(btrim(p_note), '')
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. admin_release_handle — the DESTRUCTIVE half, now explicit.
--
-- resolve_content_report no longer nulls a handle (section 7). Releasing one
-- returns the name to the pool where anyone can claim it, so it is a separate
-- deliberate act — the right tool for impersonation ('Pebbles Support'), which
-- is the case that actually needs the name freed. Every other profile report
-- is served by hiding.
--
-- public_profile is forced false in the same statement:
-- profiles_public_requires_handle (20260730120000) is checked at statement end,
-- so a null handle with public_profile still true would violate it.
-- ---------------------------------------------------------------------------
create function public.admin_release_handle(p_user_id uuid, p_note text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin  uuid := auth.uid();
  v_handle text;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  select handle into v_handle from public.profiles where user_id = p_user_id;
  if not found then raise exception 'not_found'; end if;

  update public.profiles
     set handle = null, public_profile = false
   where user_id = p_user_id;

  return jsonb_build_object(
    'user_id', p_user_id,
    'released_handle', v_handle,
    'note', nullif(btrim(p_note), '')
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. resolve_content_report — re-emitted so `actioned` sets the flag instead
-- of mutating the user's own settings.
--
-- Base: 20260916090000 §5, with only the takedown branch changed. The verdict
-- and the takedown stay in one transaction: "the status says actioned" and
-- "the content is down" must remain one fact.
-- ---------------------------------------------------------------------------
create or replace function public.resolve_content_report(
  p_report_id uuid,
  p_outcome   text,
  p_note      text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin uuid := auth.uid();
  v_row   public.content_reports;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  if p_outcome not in ('actioned', 'dismissed') then
    raise exception 'invalid_outcome';
  end if;

  -- for update: two admins resolving the same row race otherwise, and the
  -- second takedown would run against already-removed content.
  select * into v_row from public.content_reports where id = p_report_id for update;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'open' then raise exception 'invalid_state'; end if;

  if p_outcome = 'actioned' then
    if v_row.target_kind = 'glyph' then
      -- Every submission for the glyph: a re-attributed glyph can carry more
      -- than one, and leaving any listed leaves it in the Market.
      update public.glyph_submissions set listed = false where glyph_id = v_row.target_id;
    else
      -- Reversible, auditable, and it leaves the user's own visibility /
      -- handle settings alone. The handle of an impersonator is released
      -- separately and deliberately via admin_release_handle.
      perform public.admin_set_content_hidden(
        v_row.target_kind, v_row.target_id, true, p_note
      );
    end if;
  end if;

  update public.content_reports
     set status          = p_outcome,
         resolution_note = nullif(btrim(p_note), ''),
         resolved_at     = now(),
         resolved_by     = v_admin
   where id = p_report_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants: authenticated only; the is_admin guard does the real gating.
-- ---------------------------------------------------------------------------
revoke all on function public.admin_set_content_hidden(text, uuid, boolean, text) from public, anon;
revoke all on function public.admin_release_handle(uuid, text)                     from public, anon;
grant execute on function public.admin_set_content_hidden(text, uuid, boolean, text) to authenticated;
grant execute on function public.admin_release_handle(uuid, text)                     to authenticated;
