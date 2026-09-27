-- Migration: terms and privacy acceptance as version-bound ledger kinds (#966)
--
-- Terms and privacy acceptance lived as two unversioned timestamptz columns on
-- profiles, so no surface could show which document a user accepted and a
-- policy change could not trigger re-acceptance. The ledger already binds
-- health_data, public_profile and age_assurance to a document version; terms
-- and privacy join it here. Design:
-- docs/superpowers/specs/2026-09-27-android-consent-gate-design.md §3
--
-- The profiles columns stay and keep being written (design D6): they are
-- legacy, not removed, and nothing new reads them.

-- ============================================================
-- 1. kind: admit terms and privacy
-- ============================================================
-- Discovered by definition, not assumed by name, exactly as 20260913090000 §1:
-- if the CHECK is not there the assumption behind this migration is wrong and
-- we stop, rather than leave the narrow CHECK in force beside the wide one.

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
       and pg_get_constraintdef(con.oid) like '%public_profile%'
       and pg_get_constraintdef(con.oid) like '%age_assurance%'
       and pg_get_constraintdef(con.oid) not like '%withdrawn_at%';
  exception
    when no_data_found then
      raise exception 'expected the kind CHECK from 20260913090000, found none';
    when too_many_rows then
      raise exception 'more than one CHECK matches the kind predicate; refusing to guess';
  end;

  execute format('alter table public.user_consents drop constraint %I', v_name);
end;
$$;

alter table public.user_consents
  add constraint user_consents_kind_check
  check (kind in ('health_data', 'public_profile', 'age_assurance', 'terms', 'privacy'));

-- ============================================================
-- 2. terms and privacy can never be withdrawn
-- ============================================================
-- Accepting the Terms is not revoked by a toggle; it ends with the account,
-- which purge_account erases. Same reasoning as the age attestation
-- (20260913090000 §2), so the one structural CHECK now covers all three.
-- withdraw_consent's allowlist is deliberately NOT widened (first guard); this
-- CHECK is the one that survives a future rewrite of that function.

alter table public.user_consents
  drop constraint user_consents_age_not_withdrawable;

alter table public.user_consents
  add constraint user_consents_not_withdrawable
  check (not (kind in ('age_assurance', 'terms', 'privacy') and withdrawn_at is not null));

-- ============================================================
-- 3. Prove the widened kind CHECK admits both new kinds
-- ============================================================
-- Reaching the FK means the kind passed: CHECKs run during tuple insertion,
-- before AFTER-trigger FK checks. Nothing commits either way.

do $$
declare
  v_kind text;
begin
  foreach v_kind in array array['terms', 'privacy'] loop
    begin
      insert into public.user_consents (user_id, kind, document_version, source)
      values ('00000000-0000-0000-0000-000000000000', v_kind, '0.0.0', 'web_register');
      raise exception 'probe inconclusive for %: the bogus user_id did not reach the FK', v_kind;
    exception
      when foreign_key_violation then
        null;  -- expected: kind accepted, the FK stopped the write
      when check_violation then
        raise exception 'user_consents_kind_check still rejects %', v_kind;
    end;
  end loop;
end;
$$;

-- ============================================================
-- 4. record_consent: admit terms and privacy
-- ============================================================
-- Body carried forward verbatim from 20260913090000 §5 with the two kinds added
-- to the p_kind allowlist. create or replace has no merge semantics.

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
  if p_kind is null or p_kind not in (
    'health_data', 'public_profile', 'age_assurance', 'terms', 'privacy'
  ) then
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
-- 5. handle_new_user: record terms and privacy from signup metadata
-- ============================================================
-- Body carried forward verbatim from 20260913090000 §6, with two inserts
-- appended at the marker. They reuse the timestamp keys every client already
-- sends (terms_accepted_at, privacy_accepted_at) and add one version key each,
-- so a client starts recording by adding the version and nothing else. A
-- client that sends the timestamp without the version (web and iOS today)
-- records no ledger row, exactly as before this migration.
--
-- Trust boundary, as in 20260913090000 §6: granted_at is bounded by
-- user_consents_granted_at_range, document_version by
-- user_consents_document_version_shape, source by the closed `case`.
--
-- ⚠️ #823 also re-emits this function. Whichever lands second must diff both
-- bodies and union them in a new migration.

create or replace function public.handle_new_user()
returns trigger as $$
declare
  v_consent_at      timestamptz := (new.raw_user_meta_data->>'health_data_consent_at')::timestamptz;
  v_consent_version text        := new.raw_user_meta_data->>'health_data_consent_version';
  v_age_at          timestamptz := (new.raw_user_meta_data->>'age_attested_at')::timestamptz;
  v_age_version     text        := new.raw_user_meta_data->>'age_attestation_version';
  v_terms_at        timestamptz := (new.raw_user_meta_data->>'terms_accepted_at')::timestamptz;
  v_terms_version   text        := new.raw_user_meta_data->>'terms_version';
  v_privacy_at      timestamptz := (new.raw_user_meta_data->>'privacy_accepted_at')::timestamptz;
  v_privacy_version text        := new.raw_user_meta_data->>'privacy_version';
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
    v_terms_at,
    v_privacy_at
  );

  -- >>> APPEND later signup-metadata side effects HERE. <<<
  -- `->>` yields NULL for an absent key, so the OAuth path (which carries no
  -- metadata) skips these silently and records from the client instead.
  if v_consent_at is not null and v_consent_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'health_data', v_consent_version, v_surface, v_consent_at);
  end if;

  if v_age_at is not null and v_age_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'age_assurance', v_age_version, v_surface, v_age_at);
  end if;

  if v_terms_at is not null and v_terms_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'terms', v_terms_version, v_surface, v_terms_at);
  end if;

  if v_privacy_at is not null and v_privacy_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'privacy', v_privacy_version, v_surface, v_privacy_at);
  end if;

  return new;
end;
$$ language plpgsql security definer set search_path = public;
