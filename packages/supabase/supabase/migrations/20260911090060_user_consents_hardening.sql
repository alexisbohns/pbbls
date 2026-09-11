-- Migration: harden the Art. 9 consent ledger (#775)
--
-- Follow-up to 20260911090000, which is applied and therefore immutable.
-- Every decision below is justified in
-- docs/superpowers/specs/2026-09-11-art9-consent-gate-design.md §4;
-- the finding being closed is Kritik F-2026-08-GDP-web-01.
--
-- Five defects, all found in review of 20260911090000:
--   1. record_consent was idempotent only when uncontended (raw 23505 on a
--      concurrent replay — exactly the case its own comment promised to absorb)
--   2. `source` was web-shaped, contradicting design §4.4 "surface neutral"
--   3. granted_at accepted 'infinity' from client-controlled signup metadata
--   4. error codes diverged from house form (28000, P0002)
--   5. bad p_kind / p_source leaked raw constraint names as 23514

-- ============================================================
-- 1. source: surface neutral, per design §4.4
-- ============================================================
-- iOS and Android carry their own open findings (F-2026-08-GDP-ios-04,
-- F-2026-08-GDP-android-02) and will call these same RPCs. Widening now means
-- adding a surface is a client change, not a constraint drop-and-add on a
-- table that by then holds consent evidence. No rows exist yet, so this is
-- free exactly once — today.
--
-- The original CHECK was declared inline and so carries a generated name.
-- Discovering it by definition rather than assuming `user_consents_source_check`
-- keeps this loud: if it is not there, the assumption behind this migration is
-- wrong and we stop, rather than silently leaving the narrow CHECK in force
-- alongside the wide one.

do $$
declare
  v_name text;
begin
  select con.conname into v_name
    from pg_constraint con
   where con.conrelid = 'public.user_consents'::regclass
     and con.contype = 'c'
     and pg_get_constraintdef(con.oid) like '%web_register%';

  if v_name is null then
    raise exception
      'expected the surface-shaped source CHECK from 20260911090000, found none';
  end if;

  execute format('alter table public.user_consents drop constraint %I', v_name);
end;
$$;

alter table public.user_consents
  add constraint user_consents_source_check check (source in (
    'web_register',     'web_oauth',     'web_settings',
    'ios_register',     'ios_oauth',     'ios_settings',
    'android_register', 'android_oauth', 'android_settings'
  ));

-- ============================================================
-- 2. granted_at: bounded to a plausible calendar range
-- ============================================================
-- handle_new_user takes granted_at from raw_user_meta_data, which the client
-- controls. 'infinity', '-infinity', 'epoch' and 'allballs' all cast to
-- timestamptz without complaint; 'infinity' then serialises through PostgREST
-- as the literal string "infinity" and lands in every JS reader as
-- `Invalid Date`. A consent record whose timestamp cannot be read is not
-- evidence of anything.
--
-- Deliberately NOT `granted_at <= now()`: a CHECK must be immutable. A
-- now()-based CHECK is re-evaluated on every later UPDATE of the row, so a row
-- that was valid when written starts failing as the clock moves — and pg_dump
-- restores of old rows fail outright. Immutable literal bounds instead; they
-- catch the sentinel values, which is the actual attack surface.

alter table public.user_consents
  add constraint user_consents_granted_at_range check (
    granted_at >= timestamptz '2020-01-01Z'
    and granted_at < timestamptz '2100-01-01Z'
  );

-- Prove the CHECK bites rather than trusting that it was accepted. A CHECK is
-- evaluated during tuple insertion, before FK triggers fire (those are AFTER
-- triggers), so a deliberately bogus user_id still reaches the CHECK first:
-- 23514 proves the range constraint rejected 'infinity', 23503 would mean the
-- FK got there first and the probe proved nothing. Nothing commits either way
-- — the exception block rolls the subtransaction back.
do $$
begin
  begin
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values ('00000000-0000-0000-0000-000000000000', 'health_data', 'probe',
            'web_register', 'infinity');
    raise exception 'user_consents_granted_at_range did not reject infinity';
  exception
    when check_violation then
      null;  -- expected: the CHECK rejected it
    when foreign_key_violation then
      raise exception
        'granted_at CHECK probe inconclusive: the FK fired before the CHECK';
  end;
end;
$$;

-- ============================================================
-- 3. record_consent — contention-safe, stable error codes
-- ============================================================
-- The race, traced under READ COMMITTED for two concurrent calls on the same
-- (user_id, kind): both `exists` probes return false; A's supersede UPDATE
-- commits; B's UPDATE matches zero rows via EvalPlanQual; A's new row is
-- invisible to B's snapshot; B's INSERT hits the partial unique index and
-- raises a raw 23505. A replayed OAuth callback — a double-click, a client
-- retry, a Next.js double-invoke — therefore got precisely the opaque unique
-- violation the old comment promised it would not.
--
-- The fix is to let the index arbitrate instead of the snapshot: ON CONFLICT
-- takes a predicate lock and resolves the duplicate rather than erroring.
-- The index is partial, so its predicate has to be restated as the arbiter —
-- without the WHERE clause there is no matching unique index to infer.

create or replace function public.record_consent(
  p_kind text,
  p_document_version text,
  p_source text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  -- Validate before the structural guards can fire, so callers switch on a
  -- stable name instead of parsing a generated constraint name out of a 23514.
  -- Same shape as set_handle's invalid_handle / handle_taken / handle_reserved
  -- (20260730120000_public_profiles.sql §4).
  if p_kind is null or p_kind not in ('health_data', 'public_profile') then
    raise exception 'invalid_kind';
  end if;

  if p_source is null or p_source not in (
    'web_register',     'web_oauth',     'web_settings',
    'ios_register',     'ios_oauth',     'ios_settings',
    'android_register', 'android_oauth', 'android_settings'
  ) then
    raise exception 'invalid_source';
  end if;

  -- Fast path: re-granting the same version changes nothing, and must not
  -- churn superseded_at. Kept as an uncontended short-circuit only — the
  -- INSERT below, not this probe, is what makes the RPC safe under
  -- concurrency.
  if exists (
    select 1 from public.user_consents
     where user_id = v_user_id
       and kind = p_kind
       and document_version = p_document_version
       and withdrawn_at is null
       and superseded_at is null
  ) then
    return;
  end if;

  -- An active grant at a DIFFERENT version is superseded, not duplicated.
  update public.user_consents
     set superseded_at = now()
   where user_id = v_user_id
     and kind = p_kind
     and withdrawn_at is null
     and superseded_at is null;

  insert into public.user_consents (user_id, kind, document_version, source)
  values (v_user_id, p_kind, p_document_version, p_source)
  on conflict (user_id, kind) where withdrawn_at is null and superseded_at is null
  do nothing;
end;
$$;

-- Prove the arbiter resolves to user_consents_active_kind. EXPLAIN runs parse
-- analysis and planning — where arbiter inference happens, raising
-- "no unique or exclusion constraint matching the ON CONFLICT specification"
-- if the predicate does not match a partial unique index — and stops short of
-- execution, so no row is written and the bogus user_id never reaches the FK.
do $$
begin
  execute $probe$
    explain insert into public.user_consents
      (user_id, kind, document_version, source)
    values ('00000000-0000-0000-0000-000000000000', 'health_data', 'probe',
            'web_register')
    on conflict (user_id, kind) where withdrawn_at is null and superseded_at is null
    do nothing
  $probe$;
end;
$$;

-- ============================================================
-- 4. withdraw_consent — house-form error codes
-- ============================================================
-- no_active_consent was raised with P0002, the one SQLSTATE PostgREST maps to
-- 404 rather than 400 — the only 404-producing raise in the schema, which a
-- generic `if (error)` handler would quietly classify as "not found". A bare
-- raise yields P0001 → 400, and clients match on the message, exactly as
-- useSupabaseAuth already does for set_handle's codes.

create or replace function public.withdraw_consent(p_kind text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_n       int;
begin
  if v_user_id is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  if p_kind is null or p_kind not in ('health_data', 'public_profile') then
    raise exception 'invalid_kind';
  end if;

  update public.user_consents
     set withdrawn_at = now()
   where user_id = v_user_id
     and kind = p_kind
     and withdrawn_at is null
     and superseded_at is null;
  get diagnostics v_n = row_count;

  if v_n = 0 then
    raise exception 'no_active_consent';
  end if;

  -- Multi-table, therefore in this transaction and not stitched client-side:
  -- withdrawing public-profile consent must actually unpublish. A consent
  -- record the backend does not honour is precisely the defect this change
  -- exists to fix.
  if p_kind = 'public_profile' then
    update public.profiles set public_profile = false where user_id = v_user_id;
  end if;
end;
$$;
