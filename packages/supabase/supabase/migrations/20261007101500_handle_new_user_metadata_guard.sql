-- Migration: malformed signup metadata degrades, it no longer aborts signup (#823)
--
-- handle_new_user casts four client-controlled metadata values to timestamptz
-- and copies four client-controlled version strings into
-- user_consents.document_version. Until now any one of them could abort the
-- account:
--
--   * an unreadable timestamp ("banana") raised 22007 in the `declare` block,
--     before the body ran at all;
--   * a readable but out-of-range one ("2019-01-01Z", "infinity") failed
--     user_consents_granted_at_range (20260911090060) at insert;
--   * a version string off the x.y.z shape failed
--     user_consents_document_version_shape (20260913090000 §3) at insert.
--
-- The trigger is `after insert on auth.users` (20260411000004), so each of
-- these rolled the whole signup back and GoTrue answered with its generic
-- `500 Database error saving new user`: no account, and nothing in any log to
-- tell it from any other signup failure.
--
-- ============================================================
-- The decision: degrade to "not on record", never to "granted"
-- ============================================================
-- Failing loudly was considered and rejected. "Loud" is not available here:
-- GoTrue maps every trigger exception to the same opaque 500, so the user
-- would see a dead signup they cannot fix (the metadata is assembled by the
-- client, not typed by them) and no client could tell them why. Aborting
-- protects nothing either, because the ledger already has a meaning for an
-- absent row: no act on record. Every reader treats it as not granted.
--
-- So an unreadable act is dropped, and dropped completely:
--
--   * It records NO user_consents row. It is never coerced into a plausible
--     value (no now(), no default version), because a ledger row is evidence
--     and a guessed one is worse than a visibly missing one.
--   * Its timestamp is nulled for the legacy profiles columns too, so
--     profiles.terms_accepted_at / privacy_accepted_at never claim an
--     acceptance the ledger refused to record (a value outside the range
--     CHECK is not evidence of anything — 20260911090060).
--   * It raises a WARNING naming the key and the user id (never the value,
--     which is client-controlled and unbounded), so a dropped act is now
--     distinguishable in the Postgres log. An ABSENT key stays silent: that is
--     the OAuth path and the "did not consent" path, not an error.
--
-- What re-asks for a dropped act today:
--   * Android: the post-auth consent gate (#967) blocks the app until every
--     required kind is on record, so a dropped act is asked again at launch.
--   * Web: the onboarding ConsentGate asks again for a missing health_data
--     consent before onboarding completes. Nothing re-asks a missing
--     age_assurance row until the re-consent surface (#788) lands. Web sends
--     no terms/privacy version, so it records no terms/privacy rows either way.
--   * iOS: sends only terms/privacy timestamps (no versions), so it records no
--     ledger rows either way; nothing re-asks until #821 lands.
-- The clients format these values themselves, so in practice this path is
-- reached by a client bug or a crafted request, not by a user mistake.
--
-- The checks below MIRROR user_consents_granted_at_range and
-- user_consents_document_version_shape. Those CHECKs stay the authority (they
-- cover record_consent and every future writer); this is a pre-test so a
-- failure skips one act instead of raising. A migration that changes either
-- CHECK must change the mirror here in the same migration: a CHECK narrower
-- than the mirror brings the 500 back, a wider one drops valid acts.
--
-- `signup_surface` needed nothing: it already goes through a closed `case`.
-- `full_name` → profiles.display_name is untouched here; #835 guards it.
--
-- ⚠️ Shared function body. This re-emits handle_new_user from the
-- 20260927090000 §5 body (the latest), which warned that #823 re-emits it.
-- Only the `declare` block and the guard section before the profiles insert
-- changed; every other line is carried forward verbatim, including the
-- APPEND marker. create or replace has no merge semantics: #821 and #835 also
-- re-emit this function and must start from THIS body, diffing against any
-- other re-emission that lands in between and unioning them.

create or replace function public.handle_new_user()
returns trigger as $$
declare
  -- The four timestamps are no longer initialised here (#823): a `declare`
  -- initialiser runs before the body, where no exception block can reach it.
  -- They are parsed under a guard at the top of the body instead.
  v_consent_at      timestamptz;
  v_consent_version text        := new.raw_user_meta_data->>'health_data_consent_version';
  v_age_at          timestamptz;
  v_age_version     text        := new.raw_user_meta_data->>'age_attestation_version';
  v_terms_at        timestamptz;
  v_terms_version   text        := new.raw_user_meta_data->>'terms_version';
  v_privacy_at      timestamptz;
  v_privacy_version text        := new.raw_user_meta_data->>'privacy_version';
  -- Mirrors of the two user_consents CHECKs (see the header). Literals, like
  -- the CHECKs themselves.
  v_at_min          constant timestamptz := timestamptz '2020-01-01Z';
  v_at_max          constant timestamptz := timestamptz '2100-01-01Z';
  v_version_shape   constant text        := '^[0-9]+\.[0-9]+\.[0-9]+$';
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
  -- ---- #823 guards: an unreadable act is dropped, never fatal -------------
  -- data_exception (SQLSTATE class 22) covers every way a text → timestamptz
  -- cast fails (22007 bad format, 22008 field overflow, 22023 unknown time
  -- zone, …) and nothing else, so a genuine bug elsewhere still raises.
  begin
    v_consent_at := (new.raw_user_meta_data->>'health_data_consent_at')::timestamptz;
  exception when data_exception then
    raise warning 'handle_new_user: dropped unreadable health_data_consent_at for user %', new.id;
  end;

  begin
    v_age_at := (new.raw_user_meta_data->>'age_attested_at')::timestamptz;
  exception when data_exception then
    raise warning 'handle_new_user: dropped unreadable age_attested_at for user %', new.id;
  end;

  begin
    v_terms_at := (new.raw_user_meta_data->>'terms_accepted_at')::timestamptz;
  exception when data_exception then
    raise warning 'handle_new_user: dropped unreadable terms_accepted_at for user %', new.id;
  end;

  begin
    v_privacy_at := (new.raw_user_meta_data->>'privacy_accepted_at')::timestamptz;
  exception when data_exception then
    raise warning 'handle_new_user: dropped unreadable privacy_accepted_at for user %', new.id;
  end;

  -- Readable but outside user_consents_granted_at_range ('infinity' included).
  if v_consent_at < v_at_min or v_consent_at >= v_at_max then
    raise warning 'handle_new_user: dropped out-of-range health_data_consent_at for user %', new.id;
    v_consent_at := null;
  end if;

  if v_age_at < v_at_min or v_age_at >= v_at_max then
    raise warning 'handle_new_user: dropped out-of-range age_attested_at for user %', new.id;
    v_age_at := null;
  end if;

  if v_terms_at < v_at_min or v_terms_at >= v_at_max then
    raise warning 'handle_new_user: dropped out-of-range terms_accepted_at for user %', new.id;
    v_terms_at := null;
  end if;

  if v_privacy_at < v_at_min or v_privacy_at >= v_at_max then
    raise warning 'handle_new_user: dropped out-of-range privacy_accepted_at for user %', new.id;
    v_privacy_at := null;
  end if;

  -- Off the user_consents_document_version_shape shape. Nulling the version
  -- skips that act's ledger insert below; the timestamp still reaches the
  -- legacy profiles column, exactly as it does for a client that sends no
  -- version at all.
  if v_consent_version !~ v_version_shape then
    raise warning 'handle_new_user: dropped malformed health_data_consent_version for user %', new.id;
    v_consent_version := null;
  end if;

  if v_age_version !~ v_version_shape then
    raise warning 'handle_new_user: dropped malformed age_attestation_version for user %', new.id;
    v_age_version := null;
  end if;

  if v_terms_version !~ v_version_shape then
    raise warning 'handle_new_user: dropped malformed terms_version for user %', new.id;
    v_terms_version := null;
  end if;

  if v_privacy_version !~ v_version_shape then
    raise warning 'handle_new_user: dropped malformed privacy_version for user %', new.id;
    v_privacy_version := null;
  end if;
  -- ---- end #823 guards -----------------------------------------------------

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
