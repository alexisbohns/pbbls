-- =============================================================================
-- Glyph system marker (#872)
-- =============================================================================
-- `user_id is null` meant two incompatible things at once:
--
--   (a) a first-party seed — the default glyph, the per-domain seeds, the
--       glyphs admin_set_domain_glyph / admin_set_achievement_glyph mint; and
--   (b) a glyph whose creator deleted their account, which purge_account
--       deliberately KEEPS and anonymizes because buyers hold entitlements on
--       it (20260729201326:93).
--
-- can_use_glyph and glyphs_select both read (a) and therefore accepted (b): the
-- moment a seller purged, artwork other people paid karma for became free for
-- everyone and indistinguishable from a first-party seed, and it grew the
-- system set with third-party work the admin queue never curated.
--
-- This splits the two meanings. `is_system` is the marker; ownerless is just
-- ownerless.
--
-- Design: docs/superpowers/specs/2026-09-19-glyph-system-marker-design.md
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The marker
-- ---------------------------------------------------------------------------
alter table public.glyphs
  add column is_system boolean not null default false;

comment on column public.glyphs.is_system is
  'First-party seed: the default glyph, the domain seeds, and the rows '
  'admin_set_domain_glyph / admin_set_achievement_glyph mint. NOT derivable '
  'from user_id — purge_account anonymizes SOLD glyphs to user_id = null and '
  'those must stay entitlement-gated (#872). Not settable by clients; see '
  'enforce_glyph_system_flag.';

-- ---------------------------------------------------------------------------
-- 2. Backfill
-- ---------------------------------------------------------------------------
-- The two `not exists` clauses are what keep this correct at APPLY time rather
-- than only at authoring time. Every sold glyph carries an approved
-- glyph_submissions row by construction (buy_glyph requires status='approved'
-- and listed; purge_account keeps the row and flips only `listed`), so a seller
-- who purges between authoring and deploy is excluded automatically.
--
-- Probed 2026-09-19 against the linked project: 20 ownerless glyphs — 18
-- `domain:<slug>` seeds, the default glyph, and one pre-marketplace row
-- ("Barking Dog", 2026-04-23) that three live rows already point at. None
-- carries a submission or an entitlement, so all 20 match. That last row is
-- also why this is not a provenance allowlist keyed on domains/achievements: it
-- would have been missed, and stripping it would break a soul and two pebbles.
update public.glyphs g
   set is_system = true
 where g.user_id is null
   and not exists (select 1 from public.glyph_submissions s where s.glyph_id = g.id)
   and not exists (select 1 from public.glyph_entitlements e where e.glyph_id = g.id);

create index glyphs_is_system_idx on public.glyphs (is_system) where is_system;

-- ---------------------------------------------------------------------------
-- 3. The write guard
-- ---------------------------------------------------------------------------
-- glyphs_insert forces user_id = auth.uid(), so no client can create an
-- ownerless row. Nothing stopped {user_id: me, is_system: true}: glyphs_update
-- has no WITH CHECK, so Postgres reuses USING, and a self-promotion satisfies
-- `user_id = auth.uid() and not submitted and not entitled`. Left open, any
-- user could donate their glyph into the curated set — the same class of defect
-- this migration closes.
--
-- Column-level REVOKE does not work here: Postgres cannot revoke one column out
-- of a table-level grant, so it would mean revoking table INSERT/UPDATE and
-- re-granting every other column by name, a list that rots as columns are added.
--
-- `auth.uid() is null` admits migrations and the service role (purge_account,
-- the delete-account function). is_admin admits the two admin RPCs below, which
-- run security definer but keep the caller's auth.uid().
create or replace function public.enforce_glyph_system_flag()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.is_system is distinct from coalesce(old.is_system, false)
     and auth.uid() is not null
     and not public.is_admin(auth.uid()) then
    raise exception 'is_system is not user-settable' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists glyphs_system_flag_guard on public.glyphs;
create trigger glyphs_system_flag_guard
  before insert or update on public.glyphs
  for each row execute function public.enforce_glyph_system_flag();

-- ---------------------------------------------------------------------------
-- 4. can_use_glyph — the attach guard (base: 20260712000000, one predicate)
-- ---------------------------------------------------------------------------
-- Shared by create_pebble, update_pebble and the souls_glyph_usable trigger;
-- re-emitting it alone fixes all three. None of those three is touched here.
create or replace function public.can_use_glyph(p_glyph_id uuid, p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_glyph_id is null
    or exists (
      select 1 from public.glyphs g
      where g.id = p_glyph_id
        and (g.user_id = p_user or g.is_system)
    )
    or exists (
      select 1 from public.glyph_entitlements e
      where e.glyph_id = p_glyph_id and e.user_id = p_user
    );
$$;

-- ---------------------------------------------------------------------------
-- 5. glyphs_select — the read policy (base: 20260630003348, one predicate)
-- ---------------------------------------------------------------------------
-- Safe for the anonymized sold glyph precisely because purge_account keeps
-- status = 'approved' and flips only `listed`: the approved-submission arm
-- still grants the read, so buyers' pebbles and souls keep rendering and the
-- glyph stays browsable. Only the ATTACH closes.
drop policy if exists "glyphs_select" on public.glyphs;
create policy "glyphs_select" on public.glyphs for select to authenticated
  using (
    user_id = auth.uid()
    or is_system
    or exists (select 1 from public.glyph_submissions s
               where s.glyph_id = glyphs.id and s.status = 'approved')
    or exists (select 1 from public.glyph_entitlements e
               where e.glyph_id = glyphs.id and e.user_id = auth.uid())
  );

-- ---------------------------------------------------------------------------
-- 6. New system glyphs are born system
-- ---------------------------------------------------------------------------
-- Bodies otherwise VERBATIM from 20260731090100 and 20260730150000, with
-- `is_system` added to the first-glyph insert only. The replace-in-place
-- branches touch strokes/view_box and are unchanged. Neither function carries
-- in-body append markers and neither is re-emitted twice in this migration, so
-- the pairwise-diff rule has nothing to union here.
--
-- publish_admin_glyph is deliberately NOT in this list: it writes
-- user_id = v_user, so an admin-published market glyph is owned, not system,
-- and is sold through buy_glyph like any other.
create or replace function public.admin_set_domain_glyph(
  p_domain_id uuid,
  p_strokes jsonb,
  p_view_box text
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_glyph_id uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  select default_glyph_id into v_glyph_id
  from public.domains where id = p_domain_id;
  if not found then
    raise exception 'not_found';
  end if;

  if v_glyph_id is null then
    -- First glyph for this domain: system-owned (NULL user_id), shapeless.
    insert into public.glyphs (user_id, name, strokes, view_box, is_system)
    values (null, null, p_strokes, p_view_box, true)
    returning id into v_glyph_id;

    update public.domains set default_glyph_id = v_glyph_id where id = p_domain_id;
  else
    -- Replace in place: same glyph_id, so FKs and caches keep pointing at it.
    update public.glyphs
       set strokes = p_strokes,
           view_box = p_view_box,
           updated_at = now()
     where id = v_glyph_id;
  end if;

  return v_glyph_id;
end;
$$;

create or replace function public.admin_set_achievement_glyph(
  p_achievement_id uuid,
  p_strokes jsonb,
  p_view_box text
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_glyph_id uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  select glyph_id into v_glyph_id
  from public.achievements where id = p_achievement_id;
  if not found then
    raise exception 'not_found';
  end if;

  if v_glyph_id is null then
    insert into public.glyphs (user_id, name, strokes, view_box, is_system)
    values (null, null, p_strokes, p_view_box, true)
    returning id into v_glyph_id;

    update public.achievements set glyph_id = v_glyph_id where id = p_achievement_id;
  else
    update public.glyphs
       set strokes = p_strokes,
           view_box = p_view_box,
           updated_at = now()
     where id = v_glyph_id;
  end if;

  return v_glyph_id;
end;
$$;
