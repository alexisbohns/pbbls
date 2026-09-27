# Android consent gate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every Android account, whether it signed up by email or Google, carries version-bound ledger rows for terms, privacy, Art. 9 health data and the 16+ attestation, and cannot use the app until it does.

**Architecture:** Three stacked PRs. Part 1 adds `terms` / `privacy` kinds to the `user_consents` ledger. Part 2 makes Android email signup send the full versioned metadata that `handle_new_user` turns into ledger rows. Part 3 adds a post-auth overlay in `RootScreen`, driven by a `ConsentGateViewModel`, that reads the user's active rows and records whatever is missing through `record_consent`.

**Tech Stack:** Postgres / Supabase migrations, Deno harness; Kotlin, Jetpack Compose (M3 Expressive), Hilt, supabase-kt, JUnit + Robolectric, Compose Preview Screenshot Testing.

**Spec:** `docs/superpowers/specs/2026-09-27-android-consent-gate-design.md` — read §1 (decisions D1–D9) before starting.

---

## Ground rules for every task

- Android commands run from `apps/android` with `export ANDROID_HOME=$HOME/Library/Android/sdk` set first. Without it `scripts/gradle-if-sdk.sh` silently no-ops.
- Read `apps/android/CLAUDE.md` §Architecture and §Lint & test before writing Android code. Its rules bind: `…UiState` is sealed, `hiltViewModel()` from `androidx.hilt.lifecycle.viewmodel.compose`, `NavigationBackHandler` not `BackHandler`, no `.message` of an exception reaches the UI, no colour / corner / font-size literals.
- Strings go in **both** `res/values/strings.xml` and `res/values-fr/strings.xml`. Insert them as text at the anchor shown, never by round-tripping the file through a serialiser. `LocalizationParityTest` fails if the key sets differ.
- Commits: conventional, lowercase, no period, ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Never** re-baseline screenshots locally and commit the PNGs. CI regenerates them from the `rebaseline-screenshots` PR label.
- **Never** write a Kritik finding id (`F-…`) in a commit message or in the Part 1 / Part 2 PR bodies.

## Branches (GitHub Stack)

| Part | Branch | Issue |
|---|---|---|
| 1 | `feat/966-consent-ledger-terms-privacy` | #966 |
| 2 | `feat/822-android-signup-consent-payload` | #822 |
| 3 | `feat/967-android-consent-gate` | #967 |

Created with `gh stack` (see the `gh-stack` skill), from an up-to-date `main`.

## File map

**Part 1**
- Create `packages/supabase/supabase/migrations/20260927090000_consent_terms_privacy.sql`: kind CHECK, not-withdrawable CHECK, `record_consent`, `handle_new_user`.
- Modify `packages/supabase/scripts/verify-account-purge.ts`: seed two more kinds, assert they can't be withdrawn, counts 3 → 5.
- Add `docs/superpowers/specs/2026-09-27-android-consent-gate-design.md` and this plan (first commit of the stack).

**Part 2**
- Create `apps/android/app/src/main/kotlin/app/pbbls/android/core/model/LegalVersions.kt`
- Create `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/LegalVersionsTest.kt`
- Modify `core/data/SupabaseService.kt` (`consentMetadata`, `signUp`)
- Modify `test/.../core/data/ConsentMetadataTest.kt`
- Modify `features/auth/AuthLogic.kt`, `test/.../features/auth/AuthLogicTest.kt`
- Modify `features/auth/AuthViewModel.kt`, `test/.../features/auth/AuthViewModelTest.kt`
- Modify `core/designsystem/PebblesCheckbox.kt` (add a label-only overload)
- Modify `features/auth/AuthScreen.kt`, `screenshotTest/.../FunnelScreenshots.kt`
- Modify `res/values/strings.xml`, `res/values-fr/strings.xml`

**Part 3**
- Create `core/model/Consent.kt`: `ConsentKind`, `ActiveConsent`
- Create `features/consent/ConsentGateLogic.kt` + `test/.../features/consent/ConsentGateLogicTest.kt`
- Create `core/data/ConsentService.kt`: `ConsentServicing`, `ConsentService`
- Create `core/data/ConsentPreferences.kt` + `test/.../core/data/ConsentPreferencesTest.kt`
- Modify `di/ServiceBindings.kt`; `test/.../testing/FakeServicesModule.kt`; create `test/.../testing/FakeConsentService.kt`; modify `test/.../testing/InMemoryPrefs.kt` (strings)
- Create `features/consent/ConsentGateViewModel.kt` + `test/.../features/consent/ConsentGateViewModelTest.kt`
- Create `features/consent/ConsentGateScreen.kt`, `screenshotTest/.../ConsentGateScreenshots.kt`
- Modify `RootScreen.kt`, `test/.../ui/RootGateTest.kt`
- Modify both `strings.xml`

(Android paths are relative to `apps/android/app/src/main/kotlin/app/pbbls/android/` unless they start with `test/`, `screenshotTest/` or `res/`, which are relative to `apps/android/app/src/`.)

---

# Part 1 — `terms` and `privacy` in the ledger (#966)

### Task 1: Branch and commit the design docs

**Files:** the spec and this plan (already written, uncommitted).

- [ ] **Step 1: Create the stack's first branch from up-to-date main**

```bash
cd /Users/alexis/code/pbbls
git checkout main && git pull --ff-only
gh stack init feat/966-consent-ledger-terms-privacy   # if the gh-stack skill says otherwise, follow the skill
git branch --show-current   # expect feat/966-consent-ledger-terms-privacy
```

- [ ] **Step 2: Commit the docs**

```bash
git add docs/superpowers/specs/2026-09-27-android-consent-gate-design.md docs/superpowers/plans/2026-09-27-android-consent-gate.md
git commit -m "docs(android): design and plan the android consent gate

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: The migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260927090000_consent_terms_privacy.sql`

The latest emissions of both re-emitted functions are in `20260913090000_age_assurance.sql` (§5 `record_consent`, §6 `handle_new_user`). No later migration re-emits either. Confirm that before writing:

- [ ] **Step 1: Confirm the latest emitters**

```bash
cd packages/supabase/supabase/migrations
grep -ln "function public.handle_new_user\|function public.record_consent\|user_consents_age_not_withdrawable" *.sql | sort | tail -2
```
Expected: `20260913090000_age_assurance.sql` is the last line. If a newer file appears, STOP: its body is the one to carry forward, and the diff below must be redone against it.

- [ ] **Step 2: Write the migration**

```sql
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
```

The profiles insert now reads `v_terms_at` / `v_privacy_at` from the `declare` block instead of re-casting inline. The values are identical: the same expression, evaluated once. Everything else is the verbatim body plus the two appended blocks.

- [ ] **Step 3: Diff the carried-forward bodies against the source**

```bash
cd /Users/alexis/code/pbbls/packages/supabase/supabase/migrations
diff <(sed -n '/^create or replace function public.record_consent/,/^\$\$;/p' 20260913090000_age_assurance.sql) \
     <(sed -n '/^create or replace function public.record_consent/,/^\$\$;/p' 20260927090000_consent_terms_privacy.sql)
diff <(sed -n '/^create or replace function public.handle_new_user/,/language plpgsql/p' 20260913090000_age_assurance.sql) \
     <(sed -n '/^create or replace function public.handle_new_user/,/language plpgsql/p' 20260927090000_consent_terms_privacy.sql)
```
Expected: the `record_consent` diff shows only the allowlist lines. The `handle_new_user` diff shows only the four new `declare` lines, the two `v_terms_at` / `v_privacy_at` values lines, the comment tweak "records from the client instead", and the two appended `if` blocks. Anything else is a transcription error: fix it.

- [ ] **Step 4: Validate on a throwaway local Postgres**

The full chain needs `auth.users` and Supabase roles, so this validates the new file against a minimal harness. The proof it matters is that the CHECK swap and both probes run clean.

```bash
SCRATCH=/private/tmp/claude-503/-Users-alexis-code-pbbls/83a2c961-a6b1-410f-b873-0a44746a616a/scratchpad
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
rm -rf $SCRATCH/pgdata && initdb -D $SCRATCH/pgdata -U postgres --auth=trust >/dev/null
pg_ctl -D $SCRATCH/pgdata -o "-p 55432 -k /tmp -c listen_addresses=127.0.0.1" -l $SCRATCH/pg.log start
psql -h 127.0.0.1 -p 55432 -U postgres -v ON_ERROR_STOP=1 <<'SQL'
create schema auth;
create table auth.users (id uuid primary key, raw_user_meta_data jsonb);
create function auth.uid() returns uuid language sql as $$ select null::uuid $$;
create table public.profiles (user_id uuid primary key, display_name text, terms_accepted_at timestamptz, privacy_accepted_at timestamptz);
SQL
M=/Users/alexis/code/pbbls/packages/supabase/supabase/migrations
# 20260911090000 grants to `authenticated`; create the roles it names.
psql -h 127.0.0.1 -p 55432 -U postgres -c "create role authenticated; create role anon;"
for f in 20260911090000_user_consents.sql 20260911090050_user_consents_revoke.sql 20260911090060_user_consents_hardening.sql 20260913090000_age_assurance.sql 20260927090000_consent_terms_privacy.sql; do
  echo "== $f"; psql -h 127.0.0.1 -p 55432 -U postgres -v ON_ERROR_STOP=1 -q -f $M/$f || break
done
```
Expected: every file applies with no error. If the age migration touches a table that does not exist in this harness, create a minimal stand-in for it and re-run: the goal is that the new file applies.

- [ ] **Step 5: Exercise the trigger in the harness**

```bash
psql -h 127.0.0.1 -p 55432 -U postgres -v ON_ERROR_STOP=1 <<'SQL'
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();
-- Android-shaped signup: every key, whole-second timestamps.
insert into auth.users values ('11111111-1111-1111-1111-111111111111', '{
  "terms_accepted_at":"2026-09-27T10:00:00Z","terms_version":"1.1.0",
  "privacy_accepted_at":"2026-09-27T10:00:00Z","privacy_version":"1.3.0",
  "health_data_consent_at":"2026-09-27T10:00:00Z","health_data_consent_version":"1.3.0",
  "age_attested_at":"2026-09-27T10:00:00Z","age_attestation_version":"1.3.0",
  "signup_surface":"android"}');
-- Web-shaped signup today: timestamps, no terms/privacy versions, microseconds.
insert into auth.users values ('22222222-2222-2222-2222-222222222222', '{
  "terms_accepted_at":"2026-09-27T10:00:00.123456+00:00",
  "privacy_accepted_at":"2026-09-27T10:00:00.123456+00:00"}');
-- OAuth: no metadata.
insert into auth.users values ('33333333-3333-3333-3333-333333333333', '{}');
select user_id, kind, document_version, source from public.user_consents order by user_id, kind;
select user_id, terms_accepted_at is not null as t, privacy_accepted_at is not null as p from public.profiles order by user_id;
-- terms can't be withdrawn structurally:
update public.user_consents set withdrawn_at = now() where kind = 'terms';
SQL
```
Expected:
- User `1111…` has 4 rows: `age_assurance 1.3.0`, `health_data 1.3.0`, `privacy 1.3.0`, `terms 1.1.0`, all `android_register`.
- `2222…` and `3333…` have none.
- Profiles: `1111` t/p true, `2222` t/p true, `3333` false.
- The final UPDATE **fails** with `user_consents_not_withdrawable`. That failure is the pass condition. Because the heredoc runs with `ON_ERROR_STOP=1`, it is the last statement on purpose.

- [ ] **Step 6: Stop the throwaway cluster**

```bash
pg_ctl -D $SCRATCH/pgdata stop && rm -rf $SCRATCH/pgdata $SCRATCH/pg.log
```

- [ ] **Step 7: Commit**

```bash
cd /Users/alexis/code/pbbls
git add packages/supabase/supabase/migrations/20260927090000_consent_terms_privacy.sql
git commit -m "feat(db): record terms and privacy acceptance as version-bound ledger kinds

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: Extend the purge harness

**Files:**
- Modify: `packages/supabase/scripts/verify-account-purge.ts` (the consent seed block around the `ageConsentErr` seed, the `consentCount` check, and `expectedPurged`)

- [ ] **Step 1: Seed the two new kinds after the age seed**

Insert immediately after the `if (ageConsentErr) throw …` line:

```ts
  // Terms and privacy acceptance (#966). Same path as the app: the real RPC as
  // the signed-in seller. Versions differ on purpose: the two documents version
  // independently, and a harness that used one constant for both would not
  // notice a client or trigger that swapped them.
  const { error: termsConsentErr } = await seller.rpc("record_consent", {
    p_kind: "terms",
    p_document_version: "1.1.0",
    p_source: "android_register",
  });
  if (termsConsentErr) throw new Error(`record_consent terms: ${termsConsentErr.message}`);

  const { error: privacyConsentErr } = await seller.rpc("record_consent", {
    p_kind: "privacy",
    p_document_version: "1.3.0",
    p_source: "android_register",
  });
  if (privacyConsentErr) throw new Error(`record_consent privacy: ${privacyConsentErr.message}`);
```

- [ ] **Step 2: Assert neither can be withdrawn**

Insert immediately after the `check("age attestation still active after the refused withdrawal", …)` call:

```ts
  // Terms and privacy follow the age attestation: accepting a document ends
  // with the account, never with a toggle (#966, design §3.2). Same message
  // match, for the same reason as above.
  for (const kind of ["terms", "privacy"] as const) {
    const { error: withdrawErr } = await seller.rpc("withdraw_consent", { p_kind: kind });
    check(`withdraw_consent refuses ${kind} with invalid_kind`,
      withdrawErr?.message?.includes("invalid_kind") === true,
      `expected invalid_kind, got: ${withdrawErr?.message ?? "no error at all"}` +
        ` (code=${withdrawErr?.code ?? "none"})`);
  }
```

- [ ] **Step 3: Bump the two counts from 3 to 5**

```ts
  const consentCount = await countRows("user_consents", "user_id", sellerId);
  if (consentCount !== 5) {
    throw new Error(`expected 5 seeded consent rows, got ${consentCount}`);
  }
```
and in `expectedPurged`:
```ts
    ["user_consents", 5], // health_data + public_profile + age_assurance + terms + privacy
```
Update the comment above the age check that says "would still total 3" so it says 5.

- [ ] **Step 4: Typecheck**

```bash
cd packages/supabase && deno check scripts/verify-account-purge.ts
```
Expected: no errors. If `deno check` is not how `supabase.yml` typechecks, run whatever command that workflow's typecheck step runs (read `.github/workflows/supabase.yml`).

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/scripts/verify-account-purge.ts
git commit -m "test(db): seed and pin terms and privacy consent in the purge harness

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: Push to the linked project, verify, open the PR — **controller only, ask the user first**

This task writes to production. The controller (not a subagent) runs it, and only after the user explicitly approves the push.

- [ ] **Step 1: Ask the user to approve `db push`.** Show them the migration file name and the two things it changes on production (the kind CHECK, and the two function bodies).
- [ ] **Step 2: Push**

```bash
cd packages/supabase && npx supabase db push
```
Expected: applies `20260927090000_consent_terms_privacy.sql` only.

- [ ] **Step 3: Types.** `npm run db:types:remote --workspace=packages/supabase`, then `git diff --stat packages/supabase/types/database.ts`. Expected: **no diff**, because the change is to a CHECK and to function bodies with unchanged signatures. If there is a diff, check it is confined to this migration. If it isn't, revert the file and flag the drift separately.
- [ ] **Step 4: Harness.** `npm run db:verify:purge --workspace=packages/supabase` (service role; see the script header for env). Expected: every check passes.
- [ ] **Step 5: PR.** `gh stack push`, then open the PR: title `feat(db): record terms and privacy acceptance as version-bound ledger kinds`, body starting `Resolves #966`, labels `feat`, `db`, `supabase` + `no-lab-note`, milestone `M55 · Compliance Batch A`. No finding ids in the body.

---

# Part 2 — the Android signup payload (#822)

All paths below are relative to `apps/android/app/src/`.

### Task 5: Branch, and the document versions with a parity test

**Files:**
- Create: `main/kotlin/app/pbbls/android/core/model/LegalVersions.kt`
- Test: `test/kotlin/app/pbbls/android/core/model/LegalVersionsTest.kt`

- [ ] **Step 1: Stack the next branch**

```bash
gh stack add feat/822-android-signup-consent-payload   # per the gh-stack skill
```

- [ ] **Step 2: Write the failing test**

```kotlin
package app.pbbls.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The versions Android records must be the versions of the documents it links
 * to. The documents live in the web workspace (`apps/web/docs`), so a policy
 * bump that forgets Android fails this test rather than recording the wrong
 * version into an accountability ledger. Gradle runs unit tests with the
 * module directory (`apps/android/app`) as the working directory.
 */
class LegalVersionsTest {
    private fun frontmatterVersion(path: String): String {
        val file = File("../../web/docs/$path")
        check(file.exists()) { "expected the legal document at ${file.absolutePath}" }
        return file
            .readLines()
            .dropWhile { it.trim() != "---" }
            .drop(1)
            .takeWhile { it.trim() != "---" }
            .first { it.startsWith("version:") }
            .substringAfter("version:")
            .trim()
    }

    @Test
    fun `terms version matches the published Terms of Service`() {
        assertEquals(frontmatterVersion("terms/en.md"), LegalVersions.TERMS)
    }

    @Test
    fun `privacy version matches the published Privacy Policy`() {
        assertEquals(frontmatterVersion("privacy/en.md"), LegalVersions.PRIVACY)
    }

    @Test
    fun `the French documents carry the same versions`() {
        assertEquals(frontmatterVersion("terms/fr.md"), LegalVersions.TERMS)
        assertEquals(frontmatterVersion("privacy/fr.md"), LegalVersions.PRIVACY)
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

```bash
cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.core.model.LegalVersionsTest'
```
Expected: compilation failure, `Unresolved reference: LegalVersions`.

- [ ] **Step 4: Implement**

```kotlin
package app.pbbls.android.core.model

/**
 * The legal-document versions this build shows and records consent against.
 *
 * Bump these in the SAME change as the `version:` frontmatter of
 * `apps/web/docs/{terms,privacy}/*.md`; `LegalVersionsTest` fails otherwise.
 * `health_data` and `age_assurance` cite [PRIVACY], as web does
 * (`CONSENT_DOCUMENT_VERSION`): both statements live in the privacy policy.
 */
object LegalVersions {
    const val TERMS = "1.1.0"
    const val PRIVACY = "1.3.0"
}
```

- [ ] **Step 5: Run it and watch it pass.** Same command. Expected: 3 tests PASS. If the French files carry a different version, STOP and report it: that is a documents bug, not something to paper over here.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/core/model/LegalVersions.kt app/src/test/kotlin/app/pbbls/android/core/model/LegalVersionsTest.kt
git commit -m "feat(android): pin the legal document versions consent is recorded against

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: The full signup metadata

**Files:**
- Modify: `main/kotlin/app/pbbls/android/core/data/SupabaseService.kt` (`signUp` KDoc + body, `companion object`)
- Modify: `test/kotlin/app/pbbls/android/core/data/ConsentMetadataTest.kt`

- [ ] **Step 1: Replace the test**

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.model.LegalVersions
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The sign-up metadata is a cross-surface contract: `handle_new_user`
 * (`packages/supabase/supabase/migrations/20260927090000_consent_terms_privacy.sql`)
 * reads these exact keys, and a key it does not read records nothing,
 * silently. [TRIGGER_KEYS] is copied from that function's `declare` block and
 * must move with it.
 */
class ConsentMetadataTest {
    private val now = "2026-07-11T12:00:00Z"
    private val payload = SupabaseService.consentMetadata(now)

    @Test
    fun `sends exactly the keys handle_new_user reads`() {
        assertEquals(TRIGGER_KEYS, payload.keys)
    }

    @Test
    fun `every act is stamped with the one sign-up instant`() {
        listOf("terms_accepted_at", "privacy_accepted_at", "health_data_consent_at", "age_attested_at")
            .forEach { assertEquals(it, now, payload[it]?.jsonPrimitive?.content) }
    }

    @Test
    fun `terms cite the terms version and every other act the privacy version`() {
        assertEquals(LegalVersions.TERMS, payload["terms_version"]?.jsonPrimitive?.content)
        assertEquals(LegalVersions.PRIVACY, payload["privacy_version"]?.jsonPrimitive?.content)
        assertEquals(LegalVersions.PRIVACY, payload["health_data_consent_version"]?.jsonPrimitive?.content)
        assertEquals(LegalVersions.PRIVACY, payload["age_attestation_version"]?.jsonPrimitive?.content)
    }

    /**
     * Without it the trigger's closed `case` falls back to `web_register`,
     * stamping an Android signup as web: silently, and uncorrectably.
     */
    @Test
    fun `declares the android surface`() {
        assertEquals("android", payload["signup_surface"]?.jsonPrimitive?.content)
    }

    /** Whole seconds, per the cross-surface timestamp rule (root CLAUDE.md). */
    @Test
    fun `the sign-up instant is whole seconds`() {
        val stamp = SupabaseService.signupInstant(Instant.parse("2026-09-27T10:00:00.987654321Z"))
        assertEquals("2026-09-27T10:00:00Z", stamp)
        assertTrue(!stamp.contains('.'))
    }

    private companion object {
        val TRIGGER_KEYS =
            setOf(
                "terms_accepted_at",
                "terms_version",
                "privacy_accepted_at",
                "privacy_version",
                "health_data_consent_at",
                "health_data_consent_version",
                "age_attested_at",
                "age_attestation_version",
                "signup_surface",
            )
    }
}
```

- [ ] **Step 2: Run and watch it fail.**
`./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.core.data.ConsentMetadataTest'`. Expected: compile failure on `signupInstant`.

- [ ] **Step 3: Implement.** In `SupabaseService.kt`, replace the `signUp` KDoc and the `this.data = …` line, and replace the companion's `consentMetadata`:

```kotlin
        /**
         * Sign up with email + password. The four consent acts ticked on the form
         * ride `raw_user_meta_data`, where `handle_new_user` turns each into a
         * version-bound `user_consents` row (and still fills the two legacy
         * `profiles` timestamps). Metadata rather than a client RPC: with email
         * confirmation on there is no session yet to call one with.
         */
        override suspend fun signUp(
            email: String,
            password: String,
        ) {
            try {
                client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    this.data = consentMetadata(signupInstant(Instant.now()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "signUp failed", e)
                throw e
            }
        }
```

```kotlin
            /**
             * The sign-up metadata `handle_new_user` reads. Every act is stamped
             * with the one sign-up instant; terms cite the Terms version and the
             * other three the privacy policy's (`LegalVersions`).
             * `signup_surface` is load-bearing: without it the trigger stamps the
             * rows `web_register`. Pure, so `ConsentMetadataTest` pins its shape.
             */
            fun consentMetadata(nowIso: String): JsonObject =
                buildJsonObject {
                    put("terms_accepted_at", nowIso)
                    put("terms_version", LegalVersions.TERMS)
                    put("privacy_accepted_at", nowIso)
                    put("privacy_version", LegalVersions.PRIVACY)
                    put("health_data_consent_at", nowIso)
                    put("health_data_consent_version", LegalVersions.PRIVACY)
                    put("age_attested_at", nowIso)
                    put("age_attestation_version", LegalVersions.PRIVACY)
                    put("signup_surface", "android")
                }

            /** Whole seconds: the narrowest precision every reader of a cross-surface timestamp accepts. */
            fun signupInstant(now: Instant): String = now.truncatedTo(ChronoUnit.SECONDS).toString()
```

Add the imports `app.pbbls.android.core.model.LegalVersions` and `java.time.temporal.ChronoUnit`.

- [ ] **Step 4: Run and watch it pass.** Same command. Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android/core/data/SupabaseService.kt app/src/test/kotlin/app/pbbls/android/core/data/ConsentMetadataTest.kt
git commit -m "feat(android): send versioned consent and the 16+ attestation at email signup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: `canSubmit` requires all four boxes

**Files:**
- Modify: `main/kotlin/app/pbbls/android/features/auth/AuthLogic.kt`
- Modify: `test/kotlin/app/pbbls/android/features/auth/AuthLogicTest.kt`

- [ ] **Step 1: Update the existing test calls.** `canSubmit` gains `healthAccepted` and `ageAccepted` between `privacyAccepted` and `isSubmitting`. In **every** existing call in `AuthLogicTest.kt`, insert `true, true` after the privacy argument. Login ignores the flags, and each existing signup case keeps its meaning because its subject is terms or privacy. Example:

```kotlin
AuthLogic.canSubmit(AuthMode.SIGNUP, "hello@bohns.design", "abcdef", false, true, true, true, false)
```

Also update the KDoc line "Ports all 16 iOS `AuthViewLogicTests` cases 1:1" by appending: "plus the two Android-first consent cases below (iOS gains them in #821)".

- [ ] **Step 2: Add the new failing cases**

```kotlin
    @Test
    fun signupMissingHealthConsentReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.SIGNUP, "hello@bohns.design", "abcdef", true, true, false, true, false),
        )
    }

    @Test
    fun signupMissingAgeAttestationReturnsFalse() {
        assertFalse(
            AuthLogic.canSubmit(AuthMode.SIGNUP, "hello@bohns.design", "abcdef", true, true, true, false, false),
        )
    }
```

- [ ] **Step 3: Run and watch it fail.** `./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.features.auth.AuthLogicTest'`. Expected: compile failure, too many arguments.

- [ ] **Step 4: Implement**

```kotlin
    /**
     * Whether the submit button is enabled. Email must be non-blank, contain
     * `@` and `.`, and contain no `+`; password ≥ 6; sign-up additionally
     * requires all four consent boxes (terms, privacy, the Art. 9 statement and
     * 16+). Always false while a request is in flight.
     */
    fun canSubmit(
        mode: AuthMode,
        email: String,
        password: String,
        termsAccepted: Boolean,
        privacyAccepted: Boolean,
        healthAccepted: Boolean,
        ageAccepted: Boolean,
        isSubmitting: Boolean,
    ): Boolean {
        val trimmed = email.trim()
        if (isSubmitting) return false
        if (trimmed.isEmpty()) return false
        if (!trimmed.contains("@")) return false
        if (!trimmed.contains(".")) return false
        if (trimmed.contains("+")) return false
        if (password.length < 6) return false
        return if (mode == AuthMode.SIGNUP) {
            termsAccepted && privacyAccepted && healthAccepted && ageAccepted
        } else {
            true
        }
    }
```

- [ ] **Step 5: Run and watch it pass.** Expected: the file's tests PASS. `AuthViewModel.kt` will not compile until Task 8; run only this test class, or do Task 8 before running the suite.

- [ ] **Step 6: Commit** (together with Task 8 if the module won't compile in between. That is allowed; say so in the message body.)

### Task 8: The form state and the two new boxes

**Files:**
- Modify: `main/kotlin/app/pbbls/android/features/auth/AuthViewModel.kt`
- Modify: `test/kotlin/app/pbbls/android/features/auth/AuthViewModelTest.kt`
- Modify: `main/kotlin/app/pbbls/android/core/designsystem/PebblesCheckbox.kt`
- Modify: `main/kotlin/app/pbbls/android/features/auth/AuthScreen.kt`
- Modify: `screenshotTest/kotlin/app/pbbls/android/FunnelScreenshots.kt`
- Modify: `main/res/values/strings.xml`, `main/res/values-fr/strings.xml`

- [ ] **Step 1: Update the ViewModel tests first**

In `AuthViewModelTest.kt`:
- `fillSignUp()` also calls `onHealthChange(true)` and `onAgeChange(true)`.
- `switching to Login clears the consents`: also tick health and age before switching, and assert `state.healthAccepted` and `state.ageAccepted` are false after.
- Rename `a sign-up cannot be submitted without both consents` to `a sign-up cannot be submitted without all four consents`, and have it tick terms, privacy and health (not age).
- The process-death test: also call `first.onHealthChange(true)` and `first.onAgeChange(true)`, and assert both are true on `restored`.

- [ ] **Step 2: Run and watch it fail.** `./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.features.auth.AuthViewModelTest'`. Expected: compile failure on `onHealthChange`.

- [ ] **Step 3: Implement the ViewModel**

In `AuthUiState` add, after `privacyAccepted`:
```kotlin
    val healthAccepted: Boolean = false,
    val ageAccepted: Boolean = false,
```
and pass them to `AuthLogic.canSubmit(… privacyAccepted = privacyAccepted, healthAccepted = healthAccepted, ageAccepted = ageAccepted, isSubmitting = isSubmitting)`.

In `AuthViewModel`:
- `start`: seed `healthAccepted = savedState[KEY_HEALTH] ?: false` and `ageAccepted = savedState[KEY_AGE] ?: false`.
- Add, after `onPrivacyChange`:
```kotlin
        fun onHealthChange(value: Boolean) {
            savedState[KEY_HEALTH] = value
            _uiState.update { it.copy(healthAccepted = value) }
        }

        fun onAgeChange(value: Boolean) {
            savedState[KEY_AGE] = value
            _uiState.update { it.copy(ageAccepted = value) }
        }
```
- `onModeChange`: when `clearsConsents`, also set `savedState[KEY_HEALTH] = false` and `savedState[KEY_AGE] = false`, and in the `copy` set `healthAccepted` and `ageAccepted` the same way as terms and privacy.
- Companion: `const val KEY_HEALTH = "auth-health"` and `const val KEY_AGE = "auth-age"`.

- [ ] **Step 4: Run and watch the ViewModel tests pass.** Same command.

- [ ] **Step 5: Add the strings.** In `main/res/values/strings.xml`, after `auth_consent_privacy_link`:
```xml
    <string name="auth_consent_health">I agree that Pebbles records how I feel: my moods, the emotions I pick and what I write. This is sensitive data about my mental well-being.</string>
    <string name="auth_consent_age">I am 16 years old or over.</string>
```
In `main/res/values-fr/strings.xml`, after `auth_consent_privacy_link`:
```xml
    <string name="auth_consent_health">J\'accepte que Pebbles enregistre ce que je ressens : mes humeurs, les émotions que je choisis et ce que j\'écris. Ce sont des données sensibles sur mon bien-être mental.</string>
    <string name="auth_consent_age">J\'ai 16 ans ou plus.</string>
```

- [ ] **Step 6: Add a label-only `PebblesCheckbox` overload.** Append to `PebblesCheckbox.kt`. It is additive: the existing linked overload is untouched.

```kotlin
/**
 * Consent row with a plain [label] and no document link — for statements that
 * are themselves the whole consent (the Art. 9 statement, the 16+ attestation).
 * Same row semantics as the linked overload: one `toggleable`, the box is not a
 * second target.
 */
@Composable
fun PebblesCheckbox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = isChecked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isChecked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
```

- [ ] **Step 7: Render the two boxes on Sign up.** In `AuthScreen.kt`:
- `AuthScreen` passes `onHealthChange = viewModel::onHealthChange` and `onAgeChange = viewModel::onAgeChange`.
- `AuthContent` gains the parameters `onHealthChange: (Boolean) -> Unit` and `onAgeChange: (Boolean) -> Unit`, after `onPrivacyChange`.
- Inside the `if (uiState.mode == AuthMode.SIGNUP)` column, after the privacy checkbox:
```kotlin
                PebblesCheckbox(
                    isChecked = uiState.healthAccepted,
                    onCheckedChange = onHealthChange,
                    label = stringResource(R.string.auth_consent_health),
                )
                PebblesCheckbox(
                    isChecked = uiState.ageAccepted,
                    onCheckedChange = onAgeChange,
                    label = stringResource(R.string.auth_consent_age),
                )
```
The Google button is unchanged (design D1): the gate in Part 3 covers it.

- [ ] **Step 8: Update the previews.** In `FunnelScreenshots.kt`, add `onHealthChange = {},` and `onAgeChange = {},` after `onPrivacyChange = {},` in every `AuthContent(` call.

- [ ] **Step 9: Full local gate**

```bash
cd apps/android && ./gradlew ktlintCheck lint testDebugUnitTest assembleDebug
```
Expected: all green. `LocalizationParityTest` passes, because both files gained the same two keys. `./gradlew validateDebugScreenshotTest` is expected to flag `AuthScreenSignup` (and its variants) as moved. That is the gate working. Do **not** re-baseline locally.

- [ ] **Step 10: Commit**

```bash
git add -A app/src
git commit -m "feat(android): ask for art. 9 consent and the 16+ attestation at email signup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 9: Device check and PR — **controller**

- [ ] **Step 1:** Install the debug build on the connected Pixel (`adb devices` shows it; `./gradlew installDebug`). Open Sign up and confirm four boxes, that submit enables only with all four ticked, and FR copy under a French system locale. Do **not** create an account on production without asking the user. Signup correctness is proven by Task 6's contract test and by Part 1's harness run.
- [ ] **Step 2:** `gh stack push`, then open the PR. Title `feat(android): ask for every consent at email signup, versioned`; body `Resolves #822`, key files, a Lab Note (platform `android`, species `feature`), labels `feat`, `auth`, `android`, milestone `M55 · Compliance Batch A`. No finding ids. Add the `rebaseline-screenshots` label, then approve the held runs after the bot commits (see `apps/android/CLAUDE.md` §Screenshot validation gate).

---

# Part 3 — the consent gate (#967)

### Task 10: The consent model and the gate's pure logic

**Files:**
- Create: `main/kotlin/app/pbbls/android/core/model/Consent.kt`
- Create: `main/kotlin/app/pbbls/android/features/consent/ConsentGateLogic.kt`
- Test: `test/kotlin/app/pbbls/android/features/consent/ConsentGateLogicTest.kt`

- [ ] **Step 1: Stack the branch.** `gh stack add feat/967-android-consent-gate`.

- [ ] **Step 2: Write the failing tests**

```kotlin
package app.pbbls.android.features.consent

import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.LegalVersions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentGateLogicTest {
    private fun current() = ConsentGateLogic.REQUIRED.map { ActiveConsent(it.wire, ConsentGateLogic.versionFor(it)) }

    @Test
    fun `an empty ledger is missing all four, in display order`() {
        assertEquals(
            listOf(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.HEALTH_DATA, ConsentKind.AGE_ASSURANCE),
            ConsentGateLogic.missing(emptyList()),
        )
    }

    @Test
    fun `a ledger at the current versions is missing nothing`() {
        assertEquals(emptyList<ConsentKind>(), ConsentGateLogic.missing(current()))
    }

    @Test
    fun `an older version counts as missing`() {
        val active = current().map { if (it.kind == "terms") it.copy(documentVersion = "1.0.9") else it }
        assertEquals(listOf(ConsentKind.TERMS), ConsentGateLogic.missing(active))
    }

    /** Design D8: a newer web-recorded version must never be downgraded by an older app. */
    @Test
    fun `a newer version satisfies`() {
        val active = current().map { if (it.kind == "privacy") it.copy(documentVersion = "1.10.0") else it }
        assertEquals(emptyList<ConsentKind>(), ConsentGateLogic.missing(active))
    }

    /** Rows web writes that the gate does not ask for (public_profile) are ignored, not an error. */
    @Test
    fun `unrelated kinds are ignored`() {
        val active = current() + ActiveConsent("public_profile", "1.1.0")
        assertEquals(emptyList<ConsentKind>(), ConsentGateLogic.missing(active))
    }

    @Test
    fun `health and age cite the privacy version, terms the terms version`() {
        assertEquals(LegalVersions.TERMS, ConsentGateLogic.versionFor(ConsentKind.TERMS))
        assertEquals(LegalVersions.PRIVACY, ConsentGateLogic.versionFor(ConsentKind.PRIVACY))
        assertEquals(LegalVersions.PRIVACY, ConsentGateLogic.versionFor(ConsentKind.HEALTH_DATA))
        assertEquals(LegalVersions.PRIVACY, ConsentGateLogic.versionFor(ConsentKind.AGE_ASSURANCE))
    }

    @Test
    fun `semver compares numerically, not lexically`() {
        assertTrue(ConsentGateLogic.isAtLeast("1.10.0", "1.9.0"))
        assertTrue(ConsentGateLogic.isAtLeast("1.3.0", "1.3.0"))
        assertFalse(ConsentGateLogic.isAtLeast("1.2.9", "1.3.0"))
        assertFalse(ConsentGateLogic.isAtLeast("not-a-version", "1.3.0"))
    }

    @Test
    fun `the fingerprint changes when any version changes`() {
        assertEquals("terms@1.1.0,privacy@1.3.0,health_data@1.3.0,age_assurance@1.3.0", ConsentGateLogic.fingerprint())
    }

    @Test
    fun `google accounts record as android_oauth, everyone else as android_settings`() {
        assertEquals("android_oauth", ConsentGateLogic.source(buildJsonObject { put("provider", "google") }))
        assertEquals("android_settings", ConsentGateLogic.source(buildJsonObject { put("provider", "email") }))
        assertEquals("android_settings", ConsentGateLogic.source(null))
    }
}
```

(Update the expected fingerprint string if `LegalVersions` changed since this plan was written. It must list `REQUIRED` in order.)

- [ ] **Step 3: Run and watch it fail.** `./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.features.consent.ConsentGateLogicTest'`. Expected: unresolved references.

- [ ] **Step 4: Implement the model** (`core/model/Consent.kt`)

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The consent acts the Android gate asks for. [wire] is `user_consents.kind`. */
enum class ConsentKind(
    val wire: String,
) {
    TERMS("terms"),
    PRIVACY("privacy"),
    HEALTH_DATA("health_data"),
    AGE_ASSURANCE("age_assurance"),
}

/**
 * One active (neither withdrawn nor superseded) `user_consents` row, projected
 * to the two columns the gate reads. [kind] stays a String: the ledger holds
 * kinds this app does not ask for (`public_profile`), and those must decode.
 * No timestamps, on purpose — web writes microsecond `granted_at`, and a
 * column the gate never reads is a cross-surface decoder it does not need.
 */
@Serializable
data class ActiveConsent(
    val kind: String,
    @SerialName("document_version") val documentVersion: String,
)
```

- [ ] **Step 5: Implement the logic** (`features/consent/ConsentGateLogic.kt`)

```kotlin
package app.pbbls.android.features.consent

import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.LegalVersions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the consent gate asks for, and whether a ledger already answers it.
 * Pure, so the rule that decides whether someone can use the app is tested
 * without a device or a network (design §5.2).
 */
object ConsentGateLogic {
    /** Every act a signed-in Android user must have on record, in the order the gate shows them. */
    val REQUIRED: List<ConsentKind> =
        listOf(ConsentKind.TERMS, ConsentKind.PRIVACY, ConsentKind.HEALTH_DATA, ConsentKind.AGE_ASSURANCE)

    /** The document version this build records [kind] against. */
    fun versionFor(kind: ConsentKind): String =
        when (kind) {
            ConsentKind.TERMS -> LegalVersions.TERMS
            ConsentKind.PRIVACY, ConsentKind.HEALTH_DATA, ConsentKind.AGE_ASSURANCE -> LegalVersions.PRIVACY
        }

    /**
     * The required kinds [active] does not satisfy, in display order. A row
     * satisfies when its version is the same as or NEWER than this build's
     * (design D8): an older app must never re-ask, and so never supersede, a
     * newer acceptance recorded from web.
     */
    fun missing(active: List<ActiveConsent>): List<ConsentKind> =
        REQUIRED.filter { kind ->
            active.none { it.kind == kind.wire && isAtLeast(it.documentVersion, versionFor(kind)) }
        }

    /** Numeric semver comparison. An unparsable version satisfies nothing. */
    fun isAtLeast(
        version: String,
        required: String,
    ): Boolean {
        val have = parse(version) ?: return false
        val need = parse(required) ?: return false
        for (i in 0 until 3) {
            if (have[i] != need[i]) return have[i] > need[i]
        }
        return true
    }

    /**
     * Identifies the set of versions this build requires. A passed check is
     * cached against it (design D7), so any version bump invalidates the cache.
     */
    fun fingerprint(): String = REQUIRED.joinToString(",") { "${it.wire}@${versionFor(it)}" }

    /**
     * `user_consents.source` for an act collected by the gate. Google accounts
     * reach the gate straight from the OAuth round trip; any other account is
     * re-consenting outside signup, recorded as `*_settings` like web's
     * re-consent path (age spec §8).
     */
    fun source(appMetadata: JsonObject?): String {
        val provider = appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull
        return if (provider == "google") "android_oauth" else "android_settings"
    }

    private fun parse(version: String): List<Int>? =
        version
            .split('.')
            .takeIf { it.size == 3 }
            ?.map { it.toIntOrNull() ?: return null }
}
```

- [ ] **Step 6: Run and watch it pass.** Same command. Also run `./gradlew :app:testDebugUnitTest --tests '*ArchitectureBoundaryTest*'`: `features/consent` imports only `core/`, so it must stay green.

- [ ] **Step 7: Commit** `feat(android): decide which consent acts a ledger still owes`.

### Task 11: `ConsentService`, its fake, and the DI seam

**Files:**
- Create: `main/kotlin/app/pbbls/android/core/data/ConsentService.kt`
- Modify: `main/kotlin/app/pbbls/android/di/ServiceBindings.kt`
- Create: `test/kotlin/app/pbbls/android/testing/FakeConsentService.kt`
- Modify: `test/kotlin/app/pbbls/android/testing/FakeServicesModule.kt`

The seam exists because the ViewModel test in Task 13 and the UI test in Task 15 need a fake (`apps/android/CLAUDE.md`: extract a `…Servicing` interface only when a test needs one).

- [ ] **Step 1: Implement the service**

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** The consent-ledger seam `ConsentGateViewModel` is tested against. */
interface ConsentServicing {
    /** The signed-in user's active rows (neither withdrawn nor superseded). */
    suspend fun active(): List<ActiveConsent>

    /** One consent act through `record_consent`, idempotent server-side. */
    suspend fun record(
        kind: ConsentKind,
        documentVersion: String,
        source: String,
    )
}

/**
 * The `user_consents` ledger. Reads are a single-table select that RLS scopes
 * to the owner; writes go through the `record_consent` definer RPC, because
 * the table has no client write policy at all (20260911090000 §2). Errors
 * propagate: the caller owns the gate's state.
 */
@Singleton
class ConsentService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : ConsentServicing {
        override suspend fun active(): List<ActiveConsent> =
            supabase.client
                .from("user_consents")
                .select(Columns.list("kind", "document_version")) {
                    filter {
                        exact("withdrawn_at", null)
                        exact("superseded_at", null)
                    }
                }.decodeList()

        override suspend fun record(
            kind: ConsentKind,
            documentVersion: String,
            source: String,
        ) {
            supabase.client.postgrest.rpc(
                "record_consent",
                buildJsonObject {
                    put("p_kind", kind.wire)
                    put("p_document_version", documentVersion)
                    put("p_source", source)
                },
            )
        }
    }
```

If `exact(column, null)` does not compile against the pinned supabase-kt, use `filter("withdrawn_at", FilterOperator.IS, "null")` (import `io.github.jan.supabase.postgrest.query.filter.FilterOperator`). Both produce `withdrawn_at=is.null`.

- [ ] **Step 2: Bind it.** In `ServiceBindings.kt` add the imports and:
```kotlin
    @Binds
    @Singleton
    fun bindConsentServicing(impl: ConsentService): ConsentServicing
```

- [ ] **Step 3: The fake**

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.core.data.ConsentServicing
import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.features.consent.ConsentGateLogic

/**
 * In-memory [ConsentServicing]. Defaults to a ledger that already satisfies the
 * gate, so every whole-app test that signs in lands where it did before the
 * gate existed; a gate test clears [rows] first.
 *
 * [record] behaves like `record_consent`: it replaces the active row of that
 * kind, so a follow-up [active] sees the new version.
 */
class FakeConsentService(
    val rows: MutableList<ActiveConsent> = satisfied(),
) : ConsentServicing {
    /** Every (kind, version, source) passed to [record], oldest first. */
    val recordCalls = mutableListOf<Triple<ConsentKind, String, String>>()

    var activeCalls = 0
        private set

    /** Thrown by every [active] call until cleared. */
    var activeFailure: Exception? = null

    /** Thrown by every [record] call until cleared. */
    var recordFailure: Exception? = null

    override suspend fun active(): List<ActiveConsent> {
        activeCalls += 1
        activeFailure?.let { throw it }
        return rows.toList()
    }

    override suspend fun record(
        kind: ConsentKind,
        documentVersion: String,
        source: String,
    ) {
        recordFailure?.let { throw it }
        recordCalls += Triple(kind, documentVersion, source)
        rows.removeAll { it.kind == kind.wire }
        rows += ActiveConsent(kind.wire, documentVersion)
    }

    companion object {
        fun satisfied(): MutableList<ActiveConsent> =
            ConsentGateLogic.REQUIRED
                .map { ActiveConsent(it.wire, ConsentGateLogic.versionFor(it)) }
                .toMutableList()
    }
}
```

- [ ] **Step 4: Register the fake** in `FakeServicesModule.kt`: add the imports, then `@Provides @Singleton fun fakeConsent() = FakeConsentService()` with the other fakes, and `@Provides fun consent(fake: FakeConsentService): ConsentServicing = fake` with the other seams. If `ServiceFakesTest` enumerates seams, add `ConsentServicing` to its list the same way the others appear.

- [ ] **Step 5: Compile and run the fast suite.** `./gradlew :app:testDebugUnitTest --tests '*ServiceFakesTest*' --tests '*ConsentGateLogicTest*'`. Expected: PASS.

- [ ] **Step 6: Commit** `feat(android): read and record the consent ledger`.

### Task 12: The device-side cache

**Files:**
- Create: `main/kotlin/app/pbbls/android/core/data/ConsentPreferences.kt`
- Modify: `test/kotlin/app/pbbls/android/testing/InMemoryPrefs.kt` (store strings)
- Test: `test/kotlin/app/pbbls/android/core/data/ConsentPreferencesTest.kt`

- [ ] **Step 1: Teach `InMemoryPrefs` strings.** Its `putString` currently discards the value and its `getString` returns the default. Change `putString` to `apply { pending[key!!] = value }` and `getString` to `values[key] as? String ?: defValue`. Update its KDoc: "booleans and strings".

- [ ] **Step 2: Write the failing test**

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.testing.InMemoryPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentPreferencesTest {
    private val prefs = ConsentPreferences(InMemoryPrefs())

    @Test
    fun `nothing is cached at first`() {
        assertFalse(prefs.isSatisfied("user-1", "fp-1"))
    }

    @Test
    fun `a pass is cached for that user and that fingerprint only`() {
        prefs.markSatisfied("user-1", "fp-1")

        assertTrue(prefs.isSatisfied("user-1", "fp-1"))
        assertFalse(prefs.isSatisfied("user-2", "fp-1"))
        assertFalse(prefs.isSatisfied("user-1", "fp-2"))
    }

    /** One entry, overwritten: the device keeps no list of every account that signed in on it. */
    @Test
    fun `a second user's pass replaces the first`() {
        prefs.markSatisfied("user-1", "fp-1")
        prefs.markSatisfied("user-2", "fp-1")

        assertFalse(prefs.isSatisfied("user-1", "fp-1"))
        assertTrue(prefs.isSatisfied("user-2", "fp-1"))
    }
}
```

- [ ] **Step 3: Run and watch it fail.** Expected: unresolved `ConsentPreferences`.

- [ ] **Step 4: Implement**

```kotlin
package app.pbbls.android.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers that the consent gate passed, so a consented user is not held
 * behind a network round trip on every launch, and is not locked out when they
 * launch offline (design D7).
 *
 * One entry, `"<userId>|<fingerprint>"`, overwritten by the next pass: the
 * device keeps no history of who signed in on it. The fingerprint is every
 * required kind at its version (`ConsentGateLogic.fingerprint`), so a version
 * bump misses the cache and the gate asks again.
 */
@Singleton
class ConsentPreferences internal constructor(
    private val prefs: SharedPreferences,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    fun isSatisfied(
        userId: String,
        fingerprint: String,
    ): Boolean = prefs.getString(KEY_SATISFIED, null) == entry(userId, fingerprint)

    fun markSatisfied(
        userId: String,
        fingerprint: String,
    ) {
        prefs.edit { putString(KEY_SATISFIED, entry(userId, fingerprint)) }
    }

    private fun entry(
        userId: String,
        fingerprint: String,
    ) = "$userId|$fingerprint"

    private companion object {
        /** Shared with OnboardingPreferences and AppearancePreferences: one prefs file for the app. */
        const val PREFS_NAME = "pebbles_prefs"
        const val KEY_SATISFIED = "consentGateSatisfied"
    }
}
```

- [ ] **Step 5: Run and watch it pass.** Also re-run any test that uses `InMemoryPrefs` (`grep -rl InMemoryPrefs app/src/test`) to confirm the string change broke nothing.

- [ ] **Step 6: Commit** `feat(android): remember a passed consent check on the device`.

### Task 13: `ConsentGateViewModel`

**Files:**
- Create: `main/kotlin/app/pbbls/android/features/consent/ConsentGateViewModel.kt`
- Test: `test/kotlin/app/pbbls/android/features/consent/ConsentGateViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package app.pbbls.android.features.consent

import app.pbbls.android.R
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.testing.FakeConsentService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.InMemoryPrefs
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.testSession
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ConsentGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cache = ConsentPreferences(InMemoryPrefs())

    private fun viewModel(
        consents: FakeConsentService = FakeConsentService(),
        session: UserSession? = testSession(),
    ) = ConsentGateViewModel(consents, cache, FakeSupabaseService(session = session, isInitializing = false))

    private fun googleSession() =
        testSession().copy(
            user = UserInfo(id = "user-1", aud = "authenticated", appMetadata = buildJsonObject { put("provider", "google") }),
        )

    @Test
    fun `no user is Idle and never checks`() =
        runTest {
            val consents = FakeConsentService()
            val vm = viewModel(consents)
            vm.start(null)
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Idle, vm.uiState.value)
            assertEquals(0, consents.activeCalls)
        }

    @Test
    fun `a satisfied ledger passes and is cached`() =
        runTest {
            val vm = viewModel()
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
            assertTrue(cache.isSatisfied("user-1", ConsentGateLogic.fingerprint()))
        }

    @Test
    fun `a cached pass skips the network`() =
        runTest {
            cache.markSatisfied("user-1", ConsentGateLogic.fingerprint())
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
            assertEquals(0, consents.activeCalls)
        }

    @Test
    fun `an empty ledger asks for all four`() =
        runTest {
            val vm = viewModel(FakeConsentService(rows = mutableListOf()))
            vm.start("user-1")
            advanceUntilIdle()
            val state = vm.uiState.value as ConsentGateUiState.Required
            assertEquals(ConsentGateLogic.REQUIRED, state.missing)
            assertEquals(false, state.canContinue)
        }

    @Test
    fun `continue records each missing act and passes`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            ConsentGateLogic.REQUIRED.forEach { vm.onToggle(it, true) }

            vm.onContinue()
            advanceUntilIdle()

            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
            assertEquals(ConsentGateLogic.REQUIRED, consents.recordCalls.map { it.first })
            assertTrue(consents.recordCalls.all { it.third == "android_settings" })
            assertEquals("1.1.0", consents.recordCalls.first { it.first == ConsentKind.TERMS }.second)
        }

    @Test
    fun `a google account records as android_oauth`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents, session = googleSession())
            vm.start("user-1")
            advanceUntilIdle()
            ConsentGateLogic.REQUIRED.forEach { vm.onToggle(it, true) }
            vm.onContinue()
            advanceUntilIdle()
            assertTrue(consents.recordCalls.all { it.third == "android_oauth" })
        }

    @Test
    fun `continue does nothing until every shown box is ticked`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            vm.onToggle(ConsentKind.TERMS, true)
            vm.onContinue()
            advanceUntilIdle()
            assertTrue(consents.recordCalls.isEmpty())
        }

    @Test
    fun `only the outdated act is asked for`() =
        runTest {
            val rows = FakeConsentService.satisfied()
            rows.replaceAll { if (it.kind == "terms") it.copy(documentVersion = "1.0.0") else it }
            val vm = viewModel(FakeConsentService(rows = rows))
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(listOf(ConsentKind.TERMS), (vm.uiState.value as ConsentGateUiState.Required).missing)
        }

    @Test
    fun `a failed check fails closed, and retry recovers`() =
        runTest {
            val consents = FakeConsentService()
            consents.activeFailure = IOException("offline")
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Failed(R.string.error_offline), vm.uiState.value)

            consents.activeFailure = null
            vm.retry()
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-1"), vm.uiState.value)
        }

    @Test
    fun `a failed record keeps the form and shows an error`() =
        runTest {
            val consents = FakeConsentService(rows = mutableListOf())
            consents.recordFailure = IOException("offline")
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            ConsentGateLogic.REQUIRED.forEach { vm.onToggle(it, true) }
            vm.onContinue()
            advanceUntilIdle()
            val state = vm.uiState.value as ConsentGateUiState.Required
            assertEquals(R.string.error_offline, state.errorRes)
            assertEquals(false, state.isSubmitting)
            assertEquals(ConsentGateLogic.REQUIRED.toSet(), state.ticked)
        }

    @Test
    fun `a new user re-checks`() =
        runTest {
            val consents = FakeConsentService()
            val vm = viewModel(consents)
            vm.start("user-1")
            advanceUntilIdle()
            vm.start("user-2")
            advanceUntilIdle()
            assertEquals(ConsentGateUiState.Satisfied("user-2"), vm.uiState.value)
            assertEquals(2, consents.activeCalls)
        }
}
```

Before running, check whether `UserSession` is a data class with `copy` in the pinned supabase-kt. If it isn't, build `googleSession()` the way `testSession()` builds its session, passing the `UserInfo` above.

- [ ] **Step 2: Run and watch it fail.** Expected: unresolved `ConsentGateViewModel`.

- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.features.consent

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.data.ConsentServicing
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.ConsentKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "consent"

sealed interface ConsentGateUiState {
    /** No signed-in user: nothing to gate. */
    data object Idle : ConsentGateUiState

    data object Checking : ConsentGateUiState

    /** [userId] passed. Carried so a stale pass can never let a different user through. */
    data class Satisfied(
        val userId: String,
    ) : ConsentGateUiState

    data class Required(
        val missing: List<ConsentKind>,
        val ticked: Set<ConsentKind> = emptySet(),
        val isSubmitting: Boolean = false,
        @StringRes val errorRes: Int? = null,
    ) : ConsentGateUiState {
        val canContinue: Boolean by lazy { !isSubmitting && ticked.containsAll(missing) }
    }

    /** The ledger could not be read. Fails closed (design D4). */
    data class Failed(
        @StringRes val messageRes: Int,
    ) : ConsentGateUiState
}

/**
 * Holds a signed-in user behind the consent gate until the ledger carries every
 * act `ConsentGateLogic.REQUIRED` names at a current version (design §5.3).
 *
 * Owned by `RootScreen`, not by a back-stack entry: the gate is an overlay
 * above `NavDisplay` (design D9), so nothing that navigates can route around
 * it. `RootScreen` calls [start] whenever the session's user id changes.
 */
@HiltViewModel
class ConsentGateViewModel
    @Inject
    constructor(
        private val consents: ConsentServicing,
        private val cache: ConsentPreferences,
        private val supabase: SupabaseServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ConsentGateUiState>(ConsentGateUiState.Idle)
        val uiState: StateFlow<ConsentGateUiState> = _uiState.asStateFlow()

        private var userId: String? = null

        fun start(userId: String?) {
            if (userId == this.userId && _uiState.value != ConsentGateUiState.Idle) return
            this.userId = userId
            when {
                userId == null -> _uiState.value = ConsentGateUiState.Idle
                cache.isSatisfied(userId, ConsentGateLogic.fingerprint()) ->
                    _uiState.value = ConsentGateUiState.Satisfied(userId)
                else -> check(userId)
            }
        }

        fun retry() {
            userId?.let { check(it) }
        }

        fun onToggle(
            kind: ConsentKind,
            checked: Boolean,
        ) {
            _uiState.update { state ->
                if (state !is ConsentGateUiState.Required || state.isSubmitting) return@update state
                state.copy(ticked = if (checked) state.ticked + kind else state.ticked - kind, errorRes = null)
            }
        }

        fun onContinue() {
            val state = _uiState.value as? ConsentGateUiState.Required ?: return
            val uid = userId ?: return
            if (!state.canContinue) return
            _uiState.value = state.copy(isSubmitting = true, errorRes = null)
            val source = ConsentGateLogic.source(supabase.session?.user?.appMetadata)

            viewModelScope.launch {
                // One idempotent record_consent per act, as web's OAuth callback
                // does. The acts are independent, so there is no cross-row
                // invariant for a batch RPC to protect; if one fails, the
                // re-read below shows only what is still missing.
                runCatchingCancellable {
                    state.missing.forEach { consents.record(it, ConsentGateLogic.versionFor(it), source) }
                    consents.active()
                }.onSuccess { active ->
                    if (uid != userId) return@onSuccess
                    resolve(uid, ConsentGateLogic.missing(active))
                }.onFailure { error ->
                    Log.e(TAG, "record consent failed", error)
                    if (uid != userId) return@onFailure
                    _uiState.update {
                        (it as? ConsentGateUiState.Required)
                            ?.copy(isSubmitting = false, errorRes = consentErrorMessage(error.toDataError()))
                            ?: it
                    }
                }
            }
        }

        private fun check(uid: String) {
            _uiState.value = ConsentGateUiState.Checking
            viewModelScope.launch {
                runCatchingCancellable { consents.active() }
                    .onSuccess { active ->
                        if (uid != userId) return@onSuccess
                        resolve(uid, ConsentGateLogic.missing(active))
                    }.onFailure { error ->
                        Log.e(TAG, "consent check failed", error)
                        if (uid != userId) return@onFailure
                        _uiState.value = ConsentGateUiState.Failed(consentErrorMessage(error.toDataError()))
                    }
            }
        }

        private fun resolve(
            uid: String,
            missing: List<ConsentKind>,
        ) {
            if (missing.isEmpty()) {
                cache.markSatisfied(uid, ConsentGateLogic.fingerprint())
                _uiState.value = ConsentGateUiState.Satisfied(uid)
            } else {
                _uiState.value = ConsentGateUiState.Required(missing)
            }
        }
    }

@StringRes
internal fun consentErrorMessage(error: DataError): Int =
    when (error) {
        DataError.Network -> R.string.error_offline
        DataError.Unauthorized,
        DataError.NotFound,
        DataError.Quota,
        is DataError.Conflict,
        is DataError.Unknown,
        -> R.string.consent_gate_error
    }
```

`R.string.consent_gate_error` lands in Task 14. Add both string entries now if you need the module to compile. Task 14 lists every key.

Verify `toDataError()` classifies a plain `IOException` as `DataError.Network`. The test expects `error_offline`. If it doesn't, read `DataError.kt`'s `toDataError` and use the exception type the existing Network tests use (e.g. a `HttpRequestException` helper under `testing/RestExceptions.kt`).

- [ ] **Step 4: Run and watch it pass.**
- [ ] **Step 5: Commit** `feat(android): hold a signed-in user until their consent is on record`.

### Task 14: The gate screen, its strings and previews

**Files:**
- Create: `main/kotlin/app/pbbls/android/features/consent/ConsentGateScreen.kt`
- Create: `screenshotTest/kotlin/app/pbbls/android/ConsentGateScreenshots.kt`
- Modify: both `strings.xml`

- [ ] **Step 1: Strings.** EN, after `auth_consent_age`:
```xml
    <string name="consent_gate_title">Before you continue</string>
    <string name="consent_gate_body">Pebbles keeps a journal of how you feel, so we need your agreement on each point below. Tick them all to carry on.</string>
    <string name="consent_gate_continue">Continue</string>
    <string name="consent_gate_sign_out">Log out</string>
    <string name="consent_gate_retry">Try again</string>
    <string name="consent_gate_error">We couldn\'t save that. Please try again.</string>
```
FR, after `auth_consent_age`:
```xml
    <string name="consent_gate_title">Avant de continuer</string>
    <string name="consent_gate_body">Pebbles tient le journal de ce que tu ressens, alors on a besoin de ton accord sur chaque point ci-dessous. Coche-les tous pour continuer.</string>
    <string name="consent_gate_continue">Continuer</string>
    <string name="consent_gate_sign_out">Me déconnecter</string>
    <string name="consent_gate_retry">Réessayer</string>
    <string name="consent_gate_error">Impossible d\'enregistrer ta réponse. Réessaie.</string>
```

- [ ] **Step 2: The screen**

```kotlin
package app.pbbls.android.features.consent

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesCheckbox
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.ConsentKind

/**
 * The consent gate (design §5.3), drawn by `RootScreen` above everything else.
 *
 * A `Surface`, so it swallows every touch aimed at the app beneath it. Back
 * sends the app to the background rather than doing nothing: a screen that
 * traps Back is worse than one that leaves, and leaving grants nothing. The
 * handler outranks `NavDisplay`'s because it registers later, the same
 * arrangement `AchievementMomentOverlay` relies on.
 */
@Composable
fun ConsentGateScreen(
    uiState: ConsentGateUiState,
    onToggle: (ConsentKind, Boolean) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as? Activity
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState, isBackEnabled = true) {
        activity?.moveTaskToBack(true)
    }
    ConsentGateContent(
        uiState = uiState,
        onToggle = onToggle,
        onContinue = onContinue,
        onRetry = onRetry,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

/** Stateless content layer, what the screenshots render. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConsentGateContent(
    uiState: ConsentGateUiState,
    onToggle: (ConsentKind, Boolean) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (uiState) {
            // Idle and Satisfied are never shown by RootScreen; a blank surface is
            // the safe render if they ever are.
            ConsentGateUiState.Idle,
            is ConsentGateUiState.Satisfied,
            -> Unit

            ConsentGateUiState.Checking ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { LoadingIndicator() }

            is ConsentGateUiState.Failed ->
                GateColumn {
                    Text(stringResource(uiState.messageRes), style = MaterialTheme.typography.bodyLarge)
                    PebblesPrimaryButton(text = stringResource(R.string.consent_gate_retry), onClick = onRetry)
                    SignOutButton(onSignOut)
                }

            is ConsentGateUiState.Required ->
                GateColumn {
                    Text(stringResource(R.string.consent_gate_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.consent_gate_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        uiState.missing.forEach { kind ->
                            ConsentRow(kind, kind in uiState.ticked) { onToggle(kind, it) }
                        }
                    }
                    uiState.errorRes?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    PebblesPrimaryButton(
                        text = stringResource(R.string.consent_gate_continue),
                        onClick = onContinue,
                        enabled = uiState.canContinue,
                        isLoading = uiState.isSubmitting,
                        modifier = Modifier.testTag(CONSENT_GATE_CONTINUE),
                    )
                    SignOutButton(onSignOut)
                }
        }
    }
}

@Composable
private fun GateColumn(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) { content() }
}

@Composable
private fun ConsentRow(
    kind: ConsentKind,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val tag = Modifier.testTag(consentRowTag(kind))
    when (kind) {
        ConsentKind.TERMS ->
            PebblesCheckbox(
                isChecked = checked,
                onCheckedChange = onChange,
                prefix = stringResource(R.string.auth_consent_prefix),
                linkText = stringResource(R.string.auth_consent_terms_link),
                onLinkTap = { openLegalDoc(context, LegalDoc.TERMS) },
                modifier = tag,
            )
        ConsentKind.PRIVACY ->
            PebblesCheckbox(
                isChecked = checked,
                onCheckedChange = onChange,
                prefix = stringResource(R.string.auth_consent_prefix),
                linkText = stringResource(R.string.auth_consent_privacy_link),
                onLinkTap = { openLegalDoc(context, LegalDoc.PRIVACY) },
                modifier = tag,
            )
        ConsentKind.HEALTH_DATA ->
            PebblesCheckbox(checked, onChange, stringResource(R.string.auth_consent_health), tag)
        ConsentKind.AGE_ASSURANCE ->
            PebblesCheckbox(checked, onChange, stringResource(R.string.auth_consent_age), tag)
    }
}

@Composable
private fun SignOutButton(onSignOut: () -> Unit) {
    TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.consent_gate_sign_out))
    }
}

internal const val CONSENT_GATE_CONTINUE = "consent_gate_continue"

internal fun consentRowTag(kind: ConsentKind) = "consent_gate_${kind.wire}"
```

Check each import against a sibling that already uses it (`AchievementMomentOverlay.kt` for `NavigationBackHandler` / `rememberNavigationEventState` / `NavigationEventInfo`, `EditPebbleScreen.kt` for `LoadingIndicator`, `AuthScreen.kt` for `openLegalDoc` / `readableWidth`), and match their packages exactly. If `openLegalDoc`'s signature differs from `(Context, LegalDoc)`, follow `AuthScreen.kt`'s call. If `ThemeLiteralsTest` objects to `24.dp`/`32.dp`, use the `Spacing` tokens that `AuthScreen.kt` uses for the same job.

- [ ] **Step 3: Previews** (`screenshotTest/.../ConsentGateScreenshots.kt`). Copy the imports and the `@PreviewTest` / `@Preview` / `@PreviewLargeFont` / `@PreviewFrench` pattern from `FunnelScreenshots.kt`:

```kotlin
@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun ConsentGateAllFour() {
    PebblesTheme {
        ConsentGateContent(
            uiState = ConsentGateUiState.Required(missing = ConsentGateLogic.REQUIRED, ticked = setOf(ConsentKind.TERMS)),
            onToggle = { _, _ -> },
            onContinue = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ConsentGateOneOutdated() {
    PebblesTheme {
        ConsentGateContent(
            uiState = ConsentGateUiState.Required(missing = listOf(ConsentKind.TERMS)),
            onToggle = { _, _ -> },
            onContinue = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ConsentGateFailed() {
    PebblesTheme {
        ConsentGateContent(
            uiState = ConsentGateUiState.Failed(R.string.error_offline),
            onToggle = { _, _ -> },
            onContinue = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}
```

- [ ] **Step 4: Compile and lint.** `./gradlew ktlintCheck lint testDebugUnitTest`. Expected: green (the new previews have no reference yet; CI's re-baseline adds them).
- [ ] **Step 5: Commit** `feat(android): add the consent gate screen`.

### Task 15: Wire the gate into `RootScreen`, with whole-app tests

**Files:**
- Modify: `main/kotlin/app/pbbls/android/RootScreen.kt`
- Modify: `test/kotlin/app/pbbls/android/ui/RootGateTest.kt`

- [ ] **Step 1: Write the failing whole-app tests.** Add to `RootGateTest` (with `@Inject lateinit var consents: FakeConsentService` on the class and the needed imports: `performClick`, `onNodeWithTag`, `IOException`, `consentRowTag`, `CONSENT_GATE_CONTINUE`, `ConsentGateLogic`):

```kotlin
    @Test
    fun `a consented account never sees the gate`() {
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
    }

    @Test
    fun `an account with no consent is held at the gate until it accepts`() {
        consents.rows.clear()
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        ConsentGateLogic.REQUIRED.forEach { compose.onNodeWithTag(consentRowTag(it)).performScrollTo().performClick() }
        compose.onNodeWithTag(CONSENT_GATE_CONTINUE).performScrollTo().performClick()
        compose.waitForIdle()

        onText(R.string.consent_gate_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
        assertEquals(4, consents.recordCalls.size)
    }

    @Test
    fun `the gate comes before onboarding`() {
        consents.rows.clear()
        launch()
        signIn()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        ConsentGateLogic.REQUIRED.forEach { compose.onNodeWithTag(consentRowTag(it)).performScrollTo().performClick() }
        compose.onNodeWithTag(CONSENT_GATE_CONTINUE).performScrollTo().performClick()
        compose.waitForIdle()

        onText(R.string.onboarding_skip).assertIsDisplayed()
    }

    @Test
    fun `an invite waits behind the gate`() {
        consents.rows.clear()
        skipOnboarding()
        launch(inviteIntent("tok-1"))
        signOut()
        signIn()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        onText(R.string.connections_accept_title).assertDoesNotExist()

        ConsentGateLogic.REQUIRED.forEach { compose.onNodeWithTag(consentRowTag(it)).performScrollTo().performClick() }
        compose.onNodeWithTag(CONSENT_GATE_CONTINUE).performScrollTo().performClick()
        compose.waitForIdle()

        onText(R.string.connections_accept_title).assertIsDisplayed()
    }

    @Test
    fun `a failed check blocks, and retry lets a consented account through`() {
        consents.activeFailure = IOException("offline")
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_retry).assertIsDisplayed()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsNotDisplayed()

        consents.activeFailure = null
        onText(R.string.consent_gate_retry).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
    }

    @Test
    fun `logging out from the gate returns to Welcome`() {
        consents.rows.clear()
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_sign_out).performClick()

        assertEquals(1, supabase.signOutCount)
        onText(R.string.welcome_log_in).assertIsDisplayed()
    }
```

`CONSENT_GATE_CONTINUE` and `consentRowTag` are `internal`, and the test module is the same Gradle module, so they are visible. If `assertIsNotDisplayed()` fails because the node is fully absent under the overlay's cleared semantics, use `assertDoesNotExist()` for that one line. The point is that the user cannot reach it.

- [ ] **Step 2: Run and watch them fail.** `./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.ui.RootGateTest'`. Expected: the new gate tests fail (no gate). The consented-account test may already pass.

- [ ] **Step 3: Wire it in `RootScreen.kt`.**

After `val root by viewModel.uiState.collectAsStateWithLifecycle()`:
```kotlin
    // The consent gate (#967, design §5.4). Its own ViewModel, activity-scoped
    // like RootViewModel; RootScreen owns it because the gate is an overlay
    // above NavDisplay, not a back-stack entry (design D9).
    val consentViewModel: ConsentGateViewModel = hiltViewModel()
    val consent by consentViewModel.uiState.collectAsStateWithLifecycle()
```

After `val userId = root.userId`:
```kotlin
    LaunchedEffect(userId) { consentViewModel.start(userId) }

    // Gated unless THIS user passed. Keyed on the id, so the frame between a
    // user switch and start() re-checking can never show the app to the new
    // user on the old user's pass. Fails closed while Idle/Checking.
    val isConsentGated =
        root.destination == RootDestination.SignedIn &&
            consent != ConsentGateUiState.Satisfied(userId ?: "")
```

In the pending-invite effect, add `isConsentGated` to its keys and `if (isConsentGated) return@LaunchedEffect` after the `shouldPresentOnboarding` guard. Extend its comment: "…and past the consent gate: an invite is a stranger's content, and nobody reaches content before consent is on record."

Replace the `Box { PebblesNavDisplay(…); if (!overlaySlot.isHostedBySheet) overlaySlot.content() }` body with:
```kotlin
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            // While gated, what is beneath is hidden from accessibility services
            // as well as covered: a TalkBack user must not be able to reach
            // the app around the gate.
            Box(modifier = if (isConsentGated) Modifier.clearAndSetSemantics {} else Modifier) {
                PebblesNavDisplay(
                    navigator = navigator,
                    state = navState,
                    onSignOut = viewModel::onSignOut,
                    welcomeContentRevealed = welcomeContentRevealed,
                    onOnboardingFinished = {
                        OnboardingPreferences.setHasSeenOnboarding(context, true)
                        hasSeenOnboarding = true
                        navigator.goBack()
                    },
                )
            }
            // Drawn last for z-order (D9), unless a sheet is hosting them.
            // The hand-over lands one frame late: a sheet registers in an
            // effect, so on the frame it opens both copies compose, and a
            // celebration already on screen can replay its haptic once.
            if (!overlaySlot.isHostedBySheet) overlaySlot.content()
            // Above everything, celebrations included (design D9).
            if (isConsentGated) {
                ConsentGateScreen(
                    uiState = consent,
                    onToggle = consentViewModel::onToggle,
                    onContinue = consentViewModel::onContinue,
                    onRetry = consentViewModel::retry,
                    onSignOut = viewModel::onSignOut,
                )
            }
        }
```

Imports: `androidx.compose.ui.semantics.clearAndSetSemantics`, `app.pbbls.android.features.consent.ConsentGateScreen`, `…ConsentGateUiState`, `…ConsentGateViewModel`. Add one paragraph to the `RootScreen` KDoc: "**The consent gate is an overlay, not an entry (#967, design D9).** It draws above `NavDisplay` whenever the signed-in user has not passed `ConsentGateViewModel`, so an App Link or a restored stack cannot route around it; the parked invite also waits for it."

- [ ] **Step 4: Run the whole-app suite.** `./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.ui.*'`. Expected: every `ui` test passes, the pre-existing ones included. They run against the satisfied-by-default fake.

- [ ] **Step 5: Full local gate.** `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`. Expected: green. Then `./gradlew validateDebugScreenshotTest`: only the new `ConsentGate*` previews and Part 2's `AuthScreenSignup*` should differ from `main`'s local run.

- [ ] **Step 6: Commit** `feat(android): gate the app on a complete consent record`.

### Task 16: On-device check — **controller, on the connected Pixel**

Part 1 must already be pushed (Task 4). This uses the user's own account, per the memory note: ask before signing in, and never sign up a throwaway on production without asking.

- [ ] **Step 1:** `adb devices`, then `cd apps/android && ./gradlew installDebug`. Launch the app.
- [ ] **Step 2 (existing account):** ask the user to sign in, or to confirm the session already on the device. Expect the gate with all four rows (no Android account has ledger rows for terms/privacy yet). Tick all four and tap Continue. Expect Path (or onboarding on a first run).
- [ ] **Step 3 (ledger):** with the service role, list that user's active `user_consents` rows. Expect `terms 1.1.0`, `privacy 1.3.0`, `health_data 1.3.0`, `age_assurance 1.3.0` with source `android_oauth` (Google) or `android_settings`. Any pre-existing web `health_data` / `age_assurance` rows at 1.3.0 mean the gate asked only for the other two. Confirm it did.
- [ ] **Step 4 (cache):** force-stop, relaunch with airplane mode on. Expect no gate (cached pass). Airplane mode off.
- [ ] **Step 5 (Back and TalkBack):** on the gate, Back sends the app to the background, and TalkBack cannot focus anything beneath the gate. For this, clear app data to see the gate again, and ask the user before doing that.
- [ ] **Step 6:** if the user agrees to a Google sign-in with a fresh test Google account, confirm a brand-new Google account meets the gate before anything else.

### Task 17: Map, PR, follow-ups — **controller**

- [ ] **Arkaik** (hosted, `arkaik-mcp`; stop if unavailable):
  - `DM-user-consents` gains the `android` platform.
  - `AC-you-knowingly-agree-to-pebbles-recording-how-you-feel` and `AC-signup-consent-is-kept-on-record` gain `android` at `development`.
  - Create view `V-consent-gate` (android) and compose it into `F-legal-consent`. Do the first two when Part 3 work starts.
- [ ] **PR:** `gh stack push`, then the PR `feat(android): gate the app until every consent is on record`. Body: `Resolves #967`, key files, notes on D4/D7/D8/D9. This is the one PR that names `F-2026-08-GDP-android-01`, `F-2026-08-GDP-android-02` and `F-2026-08-SAF-android-02`, because it closes them. Add a Lab Note (android, feature) with `nodes:` read from the map. Labels `feat`, `auth`, `android`, milestone `M55 · Compliance Batch A`, plus the `rebaseline-screenshots` label.
- [ ] **Follow-up issues:** web sends `terms_version` / `privacy_version` and records both kinds on OAuth (`feat`, `auth`, `web`, M55). Comment on #821 that iOS must send the same full payload, and needs the gate. Android health-consent withdrawal in Settings (`feat`, `auth`, `android`). Comment on #788 that Android's pre-existing accounts are covered by #967.
- [ ] **After merge:** `kritik_resolve_finding` for the three findings, citing the Part 3 PR URL (and Part 1's for the version half of GDP-android-01). Move the acceptances to `releasing` only if the Arkaik App has not.
- [ ] **Decision log:** append one entry to `docs/decisions/log.md` in the Part 3 PR: "Terms and privacy are ledger kinds; Android gates on a complete, current-version ledger (overlay, fail-closed, cached per user+fingerprint, newer-version-satisfies)."
