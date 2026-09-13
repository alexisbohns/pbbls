-- Migration: the 16+ age attestation, as a third kind in the consent ledger
--
-- Kritik F-2026-08-SAF-supabase-01 (SAF-06). No age gate or server-recorded
-- age assurance existed anywhere: no age/birth/dob field in any migration, in
-- types/database.ts, or on any client surface, so the GDPR Art. 8 consent
-- basis was undemonstrable. Design:
-- docs/superpowers/specs/2026-09-13-age-assurance-gate-design.md
--
-- The enforced minimum is 16 and no minors are admitted, so there is no
-- minors-default settings matrix and no parental-consent flow — see design
-- §2.1. What is stored is an attestation, never a birthdate (§2.2).

-- ============================================================
-- 1. kind: admit age_assurance
-- ============================================================
-- The CHECK was declared inline at 20260911090000 and so carries a generated
-- name. Discovering it by definition rather than assuming
-- `user_consents_kind_check` keeps this loud: if it is not there, the
-- assumption behind this migration is wrong and we stop, rather than silently
-- leaving the narrow CHECK in force alongside the wide one. Same pattern as
-- 20260911090060 §1.
--
-- `into strict` rather than a plain `into`: the block's whole purpose is to
-- refuse to guess, and a plain `into` would silently take the first row if the
-- predicate ever matched two constraints — dropping one and leaving the other
-- in force, which is the exact failure this discovery dance exists to avoid.

do $$
declare
  v_name text;
begin
  begin
    select con.conname into strict v_name
      from pg_constraint con
     where con.conrelid = 'public.user_consents'::regclass
       and con.contype = 'c'
       and pg_get_constraintdef(con.oid) like '%health_data%'
       and pg_get_constraintdef(con.oid) like '%public_profile%';
  exception
    when no_data_found then
      raise exception 'expected the kind CHECK from 20260911090000, found none';
    when too_many_rows then
      raise exception 'more than one CHECK matches the kind predicate; refusing to guess';
  end;

  execute format('alter table public.user_consents drop constraint %I', v_name);
end;
$$;

alter table public.user_consents
  add constraint user_consents_kind_check
  check (kind in ('health_data', 'public_profile', 'age_assurance'));

-- ============================================================
-- 2. age_assurance can never be withdrawn
-- ============================================================
-- You cannot un-attest your age, and a withdrawn row would be
-- indistinguishable from an account that never attested. withdraw_consent's
-- own allowlist already refuses the kind, but that is a guard by omission
-- living in a different migration than the function a maintainer would edit:
-- widening that allowlist "for symmetry" would ship a hole, and
-- verify-account-purge.ts (the only test of it) is service-role and not a CI
-- gate. This CHECK is the structural backstop — it survives every future
-- rewrite of every function.

alter table public.user_consents
  add constraint user_consents_age_not_withdrawable
  check (not (kind = 'age_assurance' and withdrawn_at is not null));

-- ============================================================
-- 3. document_version: a version, not an arbitrary string
-- ============================================================
-- handle_new_user takes document_version from raw_user_meta_data, which the
-- client controls. An accountability record citing "i-was-12" or a 100KB blob
-- is worse than a missing row, because a missing row is at least visibly
-- absent. Structural rather than in-function, so it covers the OAuth path,
-- the settings path and every future surface identically.

alter table public.user_consents
  add constraint user_consents_document_version_shape
  check (document_version ~ '^[0-9]+\.[0-9]+\.[0-9]+$');

-- ============================================================
-- 4. Prove the widened kind CHECK actually admits age_assurance
-- ============================================================
-- A drop-and-recreate that silently did the wrong thing would otherwise
-- surface at the first real signup, in production. Reaching the FK means the
-- kind passed validation: CHECK constraints are evaluated during tuple
-- insertion, before AFTER-trigger FK checks fire. Nothing commits either way —
-- the exception block rolls the subtransaction back. document_version is
-- '0.0.0' rather than 'probe' because §3 now requires a semver shape.
do $$
begin
  begin
    insert into public.user_consents (user_id, kind, document_version, source)
    values ('00000000-0000-0000-0000-000000000000', 'age_assurance', '0.0.0',
            'web_register');
    raise exception 'probe inconclusive: the bogus user_id did not reach the FK';
  exception
    when foreign_key_violation then
      null;  -- expected: kind was accepted, the FK stopped the write
    when check_violation then
      raise exception 'user_consents_kind_check still rejects age_assurance';
  end;
end;
$$;

-- ============================================================
-- 5. record_consent: admit age_assurance
-- ============================================================
-- The table CHECK alone is not enough: record_consent validates p_kind against
-- its own hand-rolled allowlist (20260911090060 §3) ahead of the structural
-- guards, so callers switch on a stable `invalid_kind` instead of parsing a
-- generated constraint name out of a 23514. Both lists move together.
--
-- >>> withdraw_consent's allowlist is deliberately NOT widened alongside this
-- >>> one. You cannot un-attest your age: left withdrawable, a user could null
-- >>> out their own age basis through a normal authenticated RPC call, and the
-- >>> ledger could not distinguish that account from one that never attested.
-- >>> withdraw_consent('age_assurance') therefore raises invalid_kind, which is
-- >>> the first guard; §2 above is the structural one that holds even if this
-- >>> allowlist is later widened by mistake. Do NOT add age_assurance there
-- >>> "for symmetry" — design §4.3, asserted by verify-account-purge.ts.
--
-- Body carried forward verbatim from 20260911090060 §3 with the one-word
-- allowlist change; create or replace has no merge semantics.

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
  if p_kind is null or p_kind not in ('health_data', 'public_profile', 'age_assurance') then
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

-- ============================================================
-- 6. handle_new_user: record the email-path attestation
-- ============================================================
-- Body carried forward verbatim from 20260911090000 §5 (profiles insert with
-- its two NULL-safe consent timestamps, plus the Art. 9 insert), with the age
-- insert appended at the marker and the hardcoded source replaced by the
-- mapped `v_surface`.
--
-- Why metadata rather than a client RPC: at signUp() time there is no session
-- yet if email confirmations are enabled, so a client-side record_consent call
-- would simply be lost. The OAuth path cannot use metadata at all — no signup
-- metadata survives an OAuth round trip — and records from the callback route.
--
-- Trust boundary. Everything read out of raw_user_meta_data below is
-- client-controlled, so each value is accounted for individually:
--   * granted_at       — bounded by user_consents_granted_at_range
--                        (20260911090060 §2).
--   * document_version — bounded by user_consents_document_version_shape (§3).
--   * source           — no longer taken from metadata verbatim; the raw
--                        `signup_surface` string is mapped through the closed
--                        `case` below, so a crafted value cannot reach the
--                        column at all.
-- Deliberately still unguarded: a malformed age_attested_at (say "banana")
-- raises during the `declare` block and aborts the signup with an opaque 500.
-- That is pre-existing behaviour shared with terms_accepted_at,
-- privacy_accepted_at and health_data_consent_at, tracked separately, and not
-- fixed here — widening this migration to cover it would change four existing
-- signup paths under cover of an age-gate change.

create or replace function public.handle_new_user()
returns trigger as $$
declare
  v_consent_at      timestamptz := (new.raw_user_meta_data->>'health_data_consent_at')::timestamptz;
  v_consent_version text        := new.raw_user_meta_data->>'health_data_consent_version';
  v_age_at          timestamptz := (new.raw_user_meta_data->>'age_attested_at')::timestamptz;
  v_age_version     text        := new.raw_user_meta_data->>'age_attestation_version';
  -- Provenance, not a guess. iOS and Android sign up through this same
  -- trigger, so a hardcoded 'web_register' would stamp a native attestation
  -- as web — and a mis-stamped accountability row cannot be told from a
  -- genuine one after the fact. A `case` rather than string concatenation:
  -- it cannot be driven outside the source CHECK by a crafted client, so
  -- unknown metadata degrades to 'web_register' instead of aborting signup.
  v_surface text := case new.raw_user_meta_data->>'signup_surface'
                      when 'ios'     then 'ios_register'
                      when 'android' then 'android_register'
                      else 'web_register'
                    end;
begin
  insert into public.profiles (
    user_id,
    display_name,
    terms_accepted_at,
    privacy_accepted_at
  )
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', 'Pebbler'),
    (new.raw_user_meta_data->>'terms_accepted_at')::timestamptz,
    (new.raw_user_meta_data->>'privacy_accepted_at')::timestamptz
  );

  -- >>> APPEND later signup-metadata side effects HERE. <<<
  -- `->>` yields NULL for an absent key, so the OAuth path (which carries no
  -- metadata) skips these silently and records from the callback instead.
  if v_consent_at is not null and v_consent_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'health_data', v_consent_version, v_surface, v_consent_at);
  end if;

  if v_age_at is not null and v_age_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'age_assurance', v_age_version, v_surface, v_age_at);
  end if;

  return new;
end;
$$ language plpgsql security definer set search_path = public;
