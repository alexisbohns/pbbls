-- Migration: record_consent serialises grants per (user, kind) and refuses
-- downgrades as a no-op (#1018)
--
-- Re-emits record_consent from its latest body (20260927090000 §4). No other
-- migration in this batch re-emits it. The signature, the return type (void)
-- and every error the function can raise are unchanged, so no client contract
-- moves and packages/supabase/types/database.ts does not change.
--
-- ============================================================
-- The two defects
-- ============================================================
-- 1. Concurrent different-version grants dropped one of the two acts silently.
--    20260911090060 §3 made the function contention-safe for the case it was
--    built for, a replay at the SAME version, by letting the partial unique
--    index arbitrate with `on conflict ... do nothing`. For two concurrent calls
--    at DIFFERENT versions, that same clause discarded the loser and reported
--    success. The art. 9 design spec (2026-09-11, §9) accepted this while
--    version bumps were rare. The consent gates (Android #967, web #1017,
--    iOS #821) make bumps routine: every privacy or terms bump sends every
--    active user through record_consent again, often from two surfaces at once.
--
-- 2. A downgrade superseded a newer grant. The function superseded an active
--    grant at ANY other version, so a caller citing an older version (a stale
--    web bundle, or the OAuth callback, which records the server's current
--    versions without a same-or-newer check) replaced a newer acceptance
--    recorded on another surface. Until now the clients guarded this on their
--    own (Android ConsentGateLogic.isAtLeast, web's missingConsents).
--
-- ============================================================
-- The chosen semantics
-- ============================================================
-- * Every grant for one (user, kind) takes a transaction-scoped advisory lock
--   before it reads anything. A second concurrent call for the same pair waits
--   for the first to commit. Under READ COMMITTED, which PostgREST uses, each
--   statement in this function then takes a fresh snapshot, so the second call
--   sees the first one's committed row and decides against it. The decision is
--   no longer made against a stale snapshot. Calls for different kinds or
--   different users never wait on each other.
--
--   This is a retry inside the function, not a `consent_conflict` error. The
--   issue offered both. The lock was chosen because it needs no client change
--   on three surfaces, and because every outcome of the contended case is
--   already a correct answer (below), so there is nothing a client could
--   usefully do with an error except call again.
--
-- * Against the active grant for that (user, kind), recording version V does
--   one of three things:
--     - active is V                 -> no-op, success (unchanged: idempotent)
--     - active is NEWER than V      -> no-op, success; the newer grant stays
--                                      active and nothing is written (NEW)
--     - no active grant, or OLDER   -> supersede it, insert V (unchanged)
--   So two concurrent calls at different versions always end with the HIGHER
--   version active, whichever order they commit in. Both callers are told they
--   succeeded, and both are right: the user's act at the higher version is on
--   record, and it covers the lower one. A downgrade is reported as success
--   rather than refused because the caller's goal ("this user has accepted at
--   least V") is met, and a new error slug would be a client contract change
--   on three surfaces for no gain in evidence. The older act is NOT written as
--   a superseded row: the ledger stores what is in force and the history of
--   what replaced it, and a row that was never in force belongs to neither.
--
-- * "Newer" is numeric, per component, over the x.y.z shape that
--   user_consents_document_version_shape enforces (20260913090000 §3):
--   1.10.0 is newer than 1.9.0. If either version does not have that shape the
--   comparison is skipped and the call takes the supersede-and-insert path, as
--   before. A malformed p_document_version therefore still fails on the shape
--   CHECK (23514) and rolls the supersede back, exactly as it did. The
--   comparison never raises a new error of its own (22P02 from a bad cast).
--
-- * The insert no longer carries `on conflict ... do nothing`. Under the lock it
--   cannot conflict: the only other writer of active rows is handle_new_user,
--   and it inserts in the same transaction that creates the auth user, before
--   any session for that user can exist. If something ever does write around
--   the lock, a loud 23505 is the right outcome, and a silent drop is the
--   defect this migration removes. Should a caller run this under REPEATABLE
--   READ, a contended call fails with 40001 or 23505 rather than silently. It
--   never takes the wrong path quietly.
--
-- * superseded_at and granted_at stay `now()`, the transaction start time. A
--   call that waited on the lock may therefore stamp its row a few
--   milliseconds earlier than the grant it supersedes. Which grant is in force
--   is decided by the version comparison, never by timestamps, so this is
--   cosmetic.
--
-- Not changed: withdraw_consent takes no lock. A withdrawal racing a grant
-- still resolves loudly. The row lock on the active row orders the two
-- UPDATEs, and whichever runs second re-evaluates its predicate (EvalPlanQual).
-- A withdrawal that loses therefore raises no_active_consent, and a grant that
-- loses inserts its row beside the withdrawn one. Neither outcome is silent.
--
-- Tests: the concurrent and downgrade cases are in
-- scripts/verify-account-purge.ts (the one harness that drives record_consent).
-- It needs the service role, so CI does not run it.

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
  v_active  text;
  v_shape   constant text := '^[0-9]+\.[0-9]+\.[0-9]+$';
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

  -- Serialise every grant for this (user, kind), see the header. Taken AFTER
  -- the validation above, so a rejected call never waits, and BEFORE the read
  -- below, so the read sees whatever the previous holder committed. Released
  -- at commit or rollback. A hash collision only makes two unrelated pairs
  -- wait on each other. It cannot change an outcome.
  perform pg_advisory_xact_lock(
    hashtextextended('record_consent:' || v_user_id::text || ':' || p_kind, 0)
  );

  select document_version into v_active
    from public.user_consents
   where user_id = v_user_id
     and kind = p_kind
     and withdrawn_at is null
     and superseded_at is null;

  -- Same version: changes nothing, and must not churn superseded_at.
  -- `=`, not `is not distinct from`: a NULL p_document_version with no active
  -- grant must still reach the insert and fail on NOT NULL, as it always has.
  if v_active = p_document_version then
    return;
  end if;

  -- Downgrade: the active grant is NEWER than the one being recorded. Keep it.
  -- Nested rather than one AND-ed condition, because SQL does not promise
  -- left-to-right evaluation, and the casts must never see a malformed string.
  if v_active ~ v_shape and p_document_version ~ v_shape then
    if string_to_array(v_active, '.')::numeric[]
       > string_to_array(p_document_version, '.')::numeric[] then
      return;
    end if;
  end if;

  -- No active grant, or an OLDER one: supersede it and record this version.
  update public.user_consents
     set superseded_at = now()
   where user_id = v_user_id
     and kind = p_kind
     and withdrawn_at is null
     and superseded_at is null;

  insert into public.user_consents (user_id, kind, document_version, source)
  values (v_user_id, p_kind, p_document_version, p_source);
end;
$$;
