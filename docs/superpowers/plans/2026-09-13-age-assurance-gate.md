# Age Assurance Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce a 16+ age attestation at signup, recorded server-side in the existing consent ledger, and correct the published minor-user text that promises a control the product does not have.

**Architecture:** No new table and no birthdate. The Art. 9 ledger `user_consents` gains a third `kind`, `age_assurance`, which inherits its owner-only RLS, its no-client-write-path guard, its one-active-act-per-kind index, its surface-neutral `source` values and its purge coverage. The web gate hangs off `canSubmitRegistration`, the single pure function that already gates the email submit and both OAuth buttons together.

**Tech Stack:** Postgres (Supabase migrations, `security definer` RPCs), Next.js 16 App Router, TypeScript strict, Vitest, Deno (DB verify harnesses), next-intl.

**Spec:** `docs/superpowers/specs/2026-09-13-age-assurance-gate-design.md`
**Issue:** #816

---

## Read before starting

- **Spec §12** lists what this deliberately does *not* fix. Do not describe the finding as closed.
- **Spec §4.3** — `withdraw_consent` is deliberately left untouched. Do not "fix" the asymmetry between the two allowlists.
- Never run `npm run db:types` (it targets a local Docker stack and truncates `database.ts` on failure). Always `db:types:remote`.
- `en.json` / `fr.json` are formatting-sensitive: insert at anchors as text, never round-trip the whole file through a serialiser.

## File structure

| File | Responsibility | Part |
|---|---|---|
| `packages/supabase/supabase/migrations/20260913090000_age_assurance.sql` | Create: widens `kind`, widens `record_consent`, appends to `handle_new_user` | 1 |
| `packages/supabase/types/database.ts` | Regenerate | 1 |
| `packages/supabase/scripts/verify-account-purge.ts` | Modify: seed the third row, assert 3, assert withdraw is refused | 1 |
| `apps/web/docs/terms/{en,fr}.md` | Modify: §3.1 eligibility, frontmatter version | 2 |
| `apps/web/docs/privacy/{en,fr}.md` | Modify: §10 collapse, frontmatter version | 2 |
| `apps/web/lib/config/consent.ts` | Modify: version constant, `CONSENT_KINDS`, withdrawable type | 2, 3 |
| `apps/web/lib/auth/registration-gate.ts` + `.test.ts` | Modify: the `age` flag | 3 |
| `apps/web/lib/types.ts` | Modify: `RegisterInput.age_attested` | 3 |
| `apps/web/lib/data/useSupabaseAuth.ts` | Modify: signup metadata | 3 |
| `apps/web/lib/data/useConsents.ts` | Modify: narrow `withdraw`'s type | 3 |
| `apps/web/app/register/page.tsx` | Modify: the fourth checkbox | 3 |
| `apps/web/app/auth/callback/route.ts` | Modify: second `record_consent` | 3 |
| `apps/web/lib/i18n/messages/{en,fr}.json` | Modify: two new strings, one corrected | 3 |

---

# Part 1 — Schema

Branch: `feat/816-age-assurance-schema` off updated `main`.

> **Shipped as [#818](https://github.com/alexisbohns/pbbls/pull/818).** Code review
> added four things this plan did not anticipate, all folded into the same
> migration. Recorded here so the plan matches what exists:
>
> - **§2 `user_consents_age_not_withdrawable`** — a CHECK forbidding
>   `withdrawn_at` on an `age_assurance` row. The spec's §4.3 guard was a
>   comment in a file a maintainer would never open: widening
>   `withdraw_consent` means copying forward its newest emission, which lives
>   in `20260911090060` §4 and carries no warning. The CHECK is structural and
>   survives any future function rewrite.
> - **§3 `user_consents_document_version_shape`** — a semver CHECK.
>   `document_version` was written from client-controlled signup metadata with
>   no validation at all, while the OAuth path validated it. That asymmetry was
>   backwards: the trigger path is the one where the value is attacker-supplied.
> - **§4 a probe** proving the widened `kind` CHECK admits `age_assurance`.
>   The sibling migration proves every claim it makes; this one asserted a
>   drop-and-recreate and proved nothing. Note the ordering: the probe runs
>   after §3, so it uses `'0.0.0'`, not `'probe'`.
> - **`source` provenance** — `handle_new_user` hardcoded `'web_register'`.
>   It now maps a `signup_surface` metadata key through a closed `case`,
>   applied to the `health_data` insert too (maintainer-approved). Inert today,
>   since no client sends the key.
>
> The harness assertion in Task 3 was also **wrong as planned**: `withdrawAgeErr
> !== null` passed for a renamed function, a dropped grant, an expired session
> or `no_active_consent` alike, and never proved the row survived. It now
> asserts the specific `invalid_kind` message and counts the row as still
> active.

### Task 1: The migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260913090000_age_assurance.sql`

- [ ] **Step 1: Create the migration file**

Write exactly this. Note that §3's `handle_new_user` carries the whole body forward from `20260911090000` — the profiles insert, the Art. 9 insert, and the append marker — because `create or replace` has no merge semantics.

```sql
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

do $$
declare
  v_name text;
begin
  select con.conname into v_name
    from pg_constraint con
   where con.conrelid = 'public.user_consents'::regclass
     and con.contype = 'c'
     and pg_get_constraintdef(con.oid) like '%health_data%'
     and pg_get_constraintdef(con.oid) like '%public_profile%';

  if v_name is null then
    raise exception
      'expected the kind CHECK from 20260911090000, found none';
  end if;

  execute format('alter table public.user_consents drop constraint %I', v_name);
end;
$$;

alter table public.user_consents
  add constraint user_consents_kind_check
  check (kind in ('health_data', 'public_profile', 'age_assurance'));

-- ============================================================
-- 2. record_consent: admit age_assurance
-- ============================================================
-- The table CHECK alone is not enough: record_consent validates p_kind against
-- its own hand-rolled allowlist (20260911090060 §5) ahead of the structural
-- guards, so callers switch on a stable `invalid_kind` instead of parsing a
-- generated constraint name out of a 23514. Both lists move together.
--
-- >>> withdraw_consent's allowlist is deliberately NOT widened alongside this
-- >>> one. You cannot un-attest your age: left withdrawable, a user could null
-- >>> out their own age basis through a normal authenticated RPC call, and the
-- >>> ledger could not distinguish that account from one that never attested.
-- >>> withdraw_consent('age_assurance') therefore raises invalid_kind, which is
-- >>> the guard. Do NOT add age_assurance there "for symmetry" — design §4.3,
-- >>> asserted by verify-account-purge.ts.
--
-- Body carried forward verbatim from 20260911090060 §5 with the one-word
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
-- 3. handle_new_user: record the email-path attestation
-- ============================================================
-- Body carried forward verbatim from 20260911090000 §5 (profiles insert with
-- its two NULL-safe consent timestamps, plus the Art. 9 insert), with the age
-- insert appended at the marker.
--
-- Why metadata rather than a client RPC: at signUp() time there is no session
-- yet if email confirmations are enabled, so a client-side record_consent call
-- would simply be lost. The OAuth path cannot use metadata at all — no signup
-- metadata survives an OAuth round trip — and records from the callback route.
--
-- granted_at comes from client-controlled metadata and is already bounded by
-- user_consents_granted_at_range (20260911090060 §2). No new guard needed.

create or replace function public.handle_new_user()
returns trigger as $$
declare
  v_consent_at      timestamptz := (new.raw_user_meta_data->>'health_data_consent_at')::timestamptz;
  v_consent_version text        := new.raw_user_meta_data->>'health_data_consent_version';
  v_age_at          timestamptz := (new.raw_user_meta_data->>'age_attested_at')::timestamptz;
  v_age_version     text        := new.raw_user_meta_data->>'age_attestation_version';
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
    values (new.id, 'health_data', v_consent_version, 'web_register', v_consent_at);
  end if;

  if v_age_at is not null and v_age_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'age_assurance', v_age_version, 'web_register', v_age_at);
  end if;

  return new;
end;
$$ language plpgsql security definer set search_path = public;
```

- [ ] **Step 2: Verify the carried-forward bodies are verbatim**

The two `create or replace` bodies above must match their sources except for the stated changes. Diff them by eye:

```bash
sed -n '118,180p' packages/supabase/supabase/migrations/20260911090060_user_consents_hardening.sql
sed -n '155,200p' packages/supabase/supabase/migrations/20260911090000_user_consents.sql
```

Expected: `record_consent` differs only in the `p_kind` allowlist line and the added comment block. `handle_new_user` differs only in the two new `declare` lines and the appended `if` block. **If anything else differs, stop** — an append has been dropped.

- [ ] **Step 3: Apply to the linked project**

Run: `npm run db:push --workspace=packages/supabase`
Expected: applies with no error.

**Do not `db:reset`** — that spins up the local Docker stack, which this project deliberately avoids. The full-chain replay is `supabase.yml`'s job on the PR.

If the `do $$` block in §1 raises `expected the kind CHECK from 20260911090000, found none`, the constraint discovery failed. **Stop and investigate — do not hardcode a name.** That exception is the migration working as designed: it means the schema is not what this migration assumes.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20260913090000_age_assurance.sql
git commit -m "feat(db): admit age_assurance as a consent ledger kind

Refs #816"
```

---

### Task 2: Regenerate types

**Files:**
- Modify: `packages/supabase/types/database.ts`

- [ ] **Step 1: Regenerate against the linked project**

Run: `npm run db:types:remote --workspace=packages/supabase`

**Not `db:types`** — that one targets `--local` and silently truncates `database.ts` when the local stack is down.

- [ ] **Step 2: Verify the file is not truncated**

Run: `git diff --stat packages/supabase/types/database.ts`
Expected: a small diff, not a wholesale deletion. If the file shrank to near-zero lines, the generation failed — `git checkout` it and fix the link before retrying.

- [ ] **Step 3: Typecheck**

Run: `npm run build --workspace=packages/supabase`
Expected: passes (`tsc --noEmit`).

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/types/database.ts
git commit -m "chore(db): regenerate types for age_assurance

Refs #816"
```

---

### Task 3: Purge harness coverage

**Files:**
- Modify: `packages/supabase/scripts/verify-account-purge.ts:264-281` and `:330-336`

The standing rule: a table's purge coverage and its zero-row assertion move in the same change. `user_consents` is already purged, so what changes is the seeded count — plus a case pinning the §4.3 guard.

- [ ] **Step 1: Seed the third row and assert the withdraw refusal**

After the `public_profile` seed block (currently ending at the `publicConsentErr` throw), and **before** the `consentCount` check, insert:

```typescript
  const { error: ageConsentErr } = await seller.rpc("record_consent", {
    p_kind: "age_assurance",
    p_document_version: "1.1.0",
    p_source: "web_register",
  });
  if (ageConsentErr) throw new Error(`record_consent age_assurance: ${ageConsentErr.message}`);

  // The age attestation must not be withdrawable: you cannot un-attest your
  // age, and a withdrawn row would be indistinguishable from an account that
  // never attested. withdraw_consent's allowlist deliberately excludes
  // age_assurance (design §4.3), so this must error. If it ever succeeds,
  // someone widened that list "for symmetry" and opened the hole.
  const { error: withdrawAgeErr } = await seller.rpc("withdraw_consent", {
    p_kind: "age_assurance",
  });
  check("withdraw_consent refuses age_assurance", withdrawAgeErr !== null,
    "the RPC accepted a withdrawal it must refuse");
```

- [ ] **Step 2: Bump the seeded count**

Change:

```typescript
  const consentCount = await countRows("user_consents", "user_id", sellerId);
  if (consentCount !== 2) {
    throw new Error(`expected 2 seeded consent rows, got ${consentCount}`);
  }
```

to:

```typescript
  const consentCount = await countRows("user_consents", "user_id", sellerId);
  if (consentCount !== 3) {
    throw new Error(`expected 3 seeded consent rows, got ${consentCount}`);
  }
```

- [ ] **Step 3: Bump the purge assertion**

Change:

```typescript
    ["user_consents", 2], // health_data + public_profile
```

to:

```typescript
    ["user_consents", 3], // health_data + public_profile + age_assurance
```

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=packages/supabase`
Expected: passes (`deno check`).

- [ ] **Step 5: Run the harness against the linked project**

Run: `npm run db:verify:purge --workspace=packages/supabase`

Needs the service role in the environment. **This is the one harness CI does not run**, so this step is the only proof.

Expected: all checks pass, including `withdraw_consent refuses age_assurance` and `purge itself counted user_consents = 3`.

- [ ] **Step 6: Commit and open the PR**

```bash
git add packages/supabase/scripts/verify-account-purge.ts
git commit -m "test(db): cover the age attestation in the purge harness

Seeds an age_assurance row, bumps the purge assertion to 3, and pins
the withdraw refusal so nobody widens withdraw_consent's allowlist for
symmetry.

Refs #816"
```

PR title: `feat(db): admit age_assurance as a consent ledger kind`
Body: `Resolves #816` is **wrong here** — this is part 1 of 3. Use `Refs #816`.
Labels: `feat`, `db`, `supabase`. Milestone: `M55 · Compliance Batch A`.
Lab Note: **none** — no user-visible change in this part. Delete the section and add the `no-lab-note` label.

---

# Part 2 — The published legal text

Branch: `fix/816-age-assurance-legal-text` off updated `main` (**not** off part 1 — nothing here depends on the schema, and holding a legal-copy fix behind a migration review keeps the misrepresentation published longer than it needs to be).

> **This part changes published legal text and needs maintainer sign-off before merge.** Do not merge it on a code review alone.

### Task 4: Terms §3.1 — eligibility

**Files:**
- Modify: `apps/web/docs/terms/en.md:5` (frontmatter), `:87` (§3.1)
- Modify: `apps/web/docs/terms/fr.md:5` (frontmatter), `:83` (§3.1)

- [ ] **Step 1: Replace the EN eligibility paragraph**

`apps/web/docs/terms/en.md:87` currently reads:

> To use Pebbles, you must be at least **13 years old**. In France, users aged 13 to 15 must obtain parental consent or parental authority, in accordance with Article 8 of the General Data Protection Regulation (GDPR) and its implementation in French law. Parents or legal guardians are responsible for ensuring minors under their care comply with these Terms.

Replace the whole paragraph with:

```markdown
To use Pebbles, you must be at least **16 years old**. We do not knowingly create or maintain accounts for anyone under 16. Sixteen is the age of consent for information society services under Article 8 of the General Data Protection Regulation (GDPR), and we apply it uniformly rather than varying it by country. You are asked to confirm you meet this minimum when you create your account. If we learn that an account belongs to someone under 16, we close it and erase the data.
```

- [ ] **Step 2: Replace the FR eligibility paragraph**

`apps/web/docs/terms/fr.md:83` — a real adaptation, saying the same thing, keeping the document's existing "vous" register:

```markdown
Pour utiliser Pebbles, vous devez avoir au moins **16 ans**. Nous ne créons ni ne maintenons sciemment de compte pour une personne de moins de 16 ans. Seize ans est l'âge du consentement pour les services de la société de l'information au sens de l'article 8 du Règlement Général sur la Protection des Données (RGPD), et nous l'appliquons uniformément plutôt que de le faire varier selon les pays. Il vous est demandé de confirmer que vous atteignez ce minimum lors de la création de votre compte. Si nous apprenons qu'un compte appartient à une personne de moins de 16 ans, nous le fermons et effaçons les données.
```

- [ ] **Step 3: Bump both frontmatter versions**

In `en.md` and `fr.md`, change `version: 1.0.0` to `version: 1.1.0`, and set `last_updated: 2026-09-13`. Leave `effective_date` for the maintainer to decide at sign-off.

- [ ] **Step 4: Confirm EN and FR still agree**

Read the two paragraphs side by side. They must state the same minimum, the same uniform-application rule, the same signup confirmation and the same consequence. A divergence here is the defect this whole change exists to remove.

- [ ] **Step 5: Commit**

```bash
git add apps/web/docs/terms/en.md apps/web/docs/terms/fr.md
git commit -m "fix(legal): raise the terms eligibility minimum to 16

Refs #816"
```

---

### Task 5: Privacy §10 — collapse the minor-user section

**Files:**
- Modify: `apps/web/docs/privacy/en.md:5` (frontmatter), `:291-312` (§10)
- Modify: `apps/web/docs/privacy/fr.md:5` (frontmatter), `:265-286` (§10)

§10.2 is the misrepresentation: it promises parental-consent confirmation "during sign-up" that no surface asks. §10.3 grants rights over a minor population that a 16 minimum removes. §10.4 is moot. All three go.

- [ ] **Step 1: Replace the whole EN §10**

Replace everything from `## 10. Minor Users` (en.md:291) up to but **not including** the `---` that precedes `## 11. Data Security`, with:

```markdown
## 10. Minor Users

### 10.1 Minimum Age

Pebbles is for people aged 16 and over. We do not knowingly collect data from anyone under 16. Sixteen is the age of consent for information society services under Article 8 of the GDPR, and we apply it uniformly rather than varying it by country.

### 10.2 How We Apply It

When you create an account, you are asked to confirm that you are 16 or over, and we record that confirmation. We do not ask for your date of birth, and we do not store one.

### 10.3 If We Learn Otherwise

If we learn that an account belongs to someone under 16, we close it and erase the data. A parent or legal guardian who believes their child holds an account can tell us at hello@bohns.design and we will do the same.
```

- [ ] **Step 2: Replace the whole FR §10**

Replace everything from `## 10. Utilisateurs Mineurs` (fr.md:265) up to but **not including** the `---` that precedes `## 11`, with:

```markdown
## 10. Utilisateurs Mineurs

### 10.1 Âge Minimum

Pebbles s'adresse aux personnes âgées de 16 ans ou plus. Nous ne collectons pas sciemment de données concernant une personne de moins de 16 ans. Seize ans est l'âge du consentement pour les services de la société de l'information au sens de l'article 8 du RGPD, et nous l'appliquons uniformément plutôt que de le faire varier selon les pays.

### 10.2 Comment Nous l'Appliquons

Lors de la création de votre compte, il vous est demandé de confirmer que vous avez 16 ans ou plus, et nous conservons cette confirmation. Nous ne demandons pas votre date de naissance et nous n'en conservons aucune.

### 10.3 Si Nous Apprenons le Contraire

Si nous apprenons qu'un compte appartient à une personne de moins de 16 ans, nous le fermons et effaçons les données. Un parent ou représentant légal qui pense que son enfant détient un compte peut nous l'indiquer à hello@bohns.design et nous ferons de même.
```

- [ ] **Step 3: Check for dangling cross-references**

Run: `grep -n "10\.2\|10\.3\|10\.4\|13 ans\|13 years\|15 ans\|15 years" apps/web/docs/privacy/en.md apps/web/docs/privacy/fr.md apps/web/docs/terms/en.md apps/web/docs/terms/fr.md`

Expected: no hits that refer to the deleted clauses or to the old 13/15 thresholds. Any hit elsewhere in the documents must be updated in this commit — a policy that contradicts itself is worse than one that is merely out of date.

- [ ] **Step 4: Bump both frontmatter versions**

In `privacy/en.md` and `privacy/fr.md`, change `version: 1.2.0` to `version: 1.3.0` and `last_updated: 2026-09-13`.

- [ ] **Step 5: Bump `CONSENT_DOCUMENT_VERSION` in the same commit**

`apps/web/lib/config/consent.ts:9`:

```typescript
export const CONSENT_DOCUMENT_VERSION = "1.3.0"
```

This is not optional. `consent.test.ts` binds the constant to the privacy frontmatter in **both** languages, and it is the only thing tying a consent row to the text the person actually read.

- [ ] **Step 6: Run the binding test**

Run: `npm run test --workspace=apps/web -- consent`
Expected: both `CONSENT_DOCUMENT_VERSION` cases PASS. A failure here means a frontmatter and the constant disagree.

- [ ] **Step 7: Commit and open the PR**

```bash
git add apps/web/docs/privacy/en.md apps/web/docs/privacy/fr.md apps/web/lib/config/consent.ts
git commit -m "fix(legal): collapse the minor-user section to a 16+ minimum

Deletes the parental-consent clause promising a confirmation 'during
sign-up' that no surface asks, plus the parental-rights and 15+ clauses
that a 16 minimum makes moot. Bumps the policy to 1.3.0 and the bound
consent constant with it.

Refs #816"
```

PR title: `fix(legal): raise the published minimum age to 16`
Body: `Refs #816`. **Flag prominently that this changes published legal text and needs maintainer sign-off, not just code review.**
Labels: `fix`, `legal`, `web`. Milestone: `M55 · Compliance Batch A`.
Lab Note: **required** — this is user-facing. See Task 10.

---

# Part 3 — The web gate

Branch: `feat/816-age-assurance-web` stacked on part 1 (`gh stack`). It depends on part 1 for `age_assurance` to be an accepted `p_kind`.

### Task 6: The registration gate flag

**Files:**
- Modify: `apps/web/lib/auth/registration-gate.ts`
- Test: `apps/web/lib/auth/registration-gate.test.ts`

This one pure function gates the email submit and both OAuth buttons. Adding the flag here is the whole enforcement — there is no second place to remember.

- [ ] **Step 1: Write the failing tests**

Replace `apps/web/lib/auth/registration-gate.test.ts` entirely with:

```typescript
import { describe, it, expect } from "vitest"
import { canSubmitRegistration, type ConsentChecks } from "./registration-gate"

const all: ConsentChecks = { terms: true, privacy: true, healthData: true, age: true }

describe("canSubmitRegistration", () => {
  it("allows submission only when all four are accepted", () => {
    expect(canSubmitRegistration(all)).toBe(true)
  })

  it("blocks when any single box is unticked", () => {
    expect(canSubmitRegistration({ ...all, terms: false })).toBe(false)
    expect(canSubmitRegistration({ ...all, privacy: false })).toBe(false)
    expect(canSubmitRegistration({ ...all, healthData: false })).toBe(false)
    expect(canSubmitRegistration({ ...all, age: false })).toBe(false)
  })

  it("blocks when nothing is accepted", () => {
    expect(
      canSubmitRegistration({ terms: false, privacy: false, healthData: false, age: false }),
    ).toBe(false)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test --workspace=apps/web -- registration-gate`
Expected: FAIL — TypeScript rejects `age` as an unknown property on `ConsentChecks`.

- [ ] **Step 3: Add the flag**

`apps/web/lib/auth/registration-gate.ts` — replace the whole file:

```typescript
/** The four acceptances /register requires before an account can be created. */
export type ConsentChecks = {
  terms: boolean
  privacy: boolean
  /** Art. 9 explicit consent — a separate act, not a document acknowledgement. */
  healthData: boolean
  /**
   * The 16+ age attestation (Kritik F-2026-08-SAF-supabase-01). An attestation,
   * not a verification, and deliberately not a birthdate — see
   * docs/superpowers/specs/2026-09-13-age-assurance-gate-design.md §2.2.
   */
  age: boolean
}

/**
 * Whether registration may proceed. Shared by the email submit button and both
 * OAuth buttons: before this existed the OAuth buttons were gated on
 * `submitting` alone, so an OAuth account was created with no consent record at
 * all (Kritik F-2026-08-GDP-web-02).
 */
export function canSubmitRegistration(checks: ConsentChecks): boolean {
  return checks.terms && checks.privacy && checks.healthData && checks.age
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test --workspace=apps/web -- registration-gate`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/auth/registration-gate.ts apps/web/lib/auth/registration-gate.test.ts
git commit -m "feat(auth): gate registration on a 16+ attestation

Refs #816"
```

---

### Task 7: Consent kinds and the email signup path

**Files:**
- Modify: `apps/web/lib/config/consent.ts:12`
- Modify: `apps/web/lib/data/useConsents.ts:96`
- Modify: `apps/web/lib/types.ts:383-390`
- Modify: `apps/web/lib/data/useSupabaseAuth.ts:161-169`

- [ ] **Step 1: Widen `CONSENT_KINDS` and name the withdrawable subset**

`apps/web/lib/config/consent.ts` — replace lines 11-13 (the `CONSENT_KINDS` block) with:

```typescript
/** The consent kinds `user_consents.kind` accepts. */
export const CONSENT_KINDS = ["health_data", "public_profile", "age_assurance"] as const
export type ConsentKind = (typeof CONSENT_KINDS)[number]

/**
 * The kinds `withdraw_consent` accepts. `age_assurance` is absent on purpose:
 * you cannot un-attest your age, so the RPC's own allowlist refuses it with
 * `invalid_kind` and this type refuses it at compile time. Design §4.3.
 */
export type WithdrawableConsentKind = Exclude<ConsentKind, "age_assurance">
```

- [ ] **Step 2: Narrow `withdraw`'s parameter**

`apps/web/lib/data/useConsents.ts` — in the import block at line 9, add `type WithdrawableConsentKind` alongside `type ConsentKind`. Then change:

```typescript
  const withdraw = useCallback(
    async (kind: ConsentKind) => {
```

to:

```typescript
  const withdraw = useCallback(
    async (kind: WithdrawableConsentKind) => {
```

- [ ] **Step 3: Add the flag to `RegisterInput`**

`apps/web/lib/types.ts:383` — replace the type with:

```typescript
export type RegisterInput = {
  email: string
  password: string
  terms_accepted: boolean
  privacy_accepted: boolean
  /** Art. 9 explicit consent for emotional/health-adjacent data. */
  health_data_consent: boolean
  /** The 16+ age attestation. An attestation, never a birthdate. */
  age_attested: boolean
}
```

- [ ] **Step 4: Carry the attestation in the signup metadata**

`apps/web/lib/data/useSupabaseAuth.ts` — in the `register` callback's `options.data` object, after the `health_data_consent_version` line, add:

```typescript
          age_attested_at: input.age_attested ? new Date().toISOString() : null,
          age_attestation_version: input.age_attested ? CONSENT_DOCUMENT_VERSION : null,
          // Provenance for the trigger's `source` mapping (migration §6). Sent
          // explicitly so `web_register` is a stated fact rather than the
          // fallback branch — iOS and Android send their own value, and a
          // native client that forgets it is stamped as web with no error.
          signup_surface: "web",
```

The attestation cites the privacy version because privacy §10.1 states the minimum age (design §6.3). No second constant.

- [ ] **Step 5: Typecheck**

Run: `npm run lint --workspace=apps/web`
Expected: FAILS in `app/register/page.tsx` — `register()` is called without `age_attested`, and `canSubmitRegistration` without `age`. That is the correct failure; Task 8 fixes it.

- [ ] **Step 6: Commit**

```bash
git add apps/web/lib/config/consent.ts apps/web/lib/data/useConsents.ts apps/web/lib/types.ts apps/web/lib/data/useSupabaseAuth.ts
git commit -m "feat(auth): carry the age attestation in signup metadata

Refs #816"
```

---

### Task 8: The checkbox and the OAuth callback

**Files:**
- Modify: `apps/web/app/register/page.tsx`
- Modify: `apps/web/app/auth/callback/route.ts:60-69`
- Modify: `apps/web/lib/i18n/messages/en.json:469-470`
- Modify: `apps/web/lib/i18n/messages/fr.json:469-470`

- [ ] **Step 1: Add the i18n strings**

`en.json` — replace lines 469-470 (the `healthConsent` and `consentRequiredHint` entries) with:

```json
      "healthConsent": "Pebbles records how you feel: your moods, the emotions you pick, and what you write. That is sensitive data about your mental well-being, and we only process it with your explicit permission.",
      "ageAttestation": "I am 16 years old or over.",
      "consentRequiredHint": "Tick all four boxes above to continue."
```

`fr.json` — replace lines 469-470 with:

```json
      "healthConsent": "Pebbles enregistre ce que vous ressentez : vos humeurs, les émotions que vous choisissez et ce que vous écrivez. Ce sont des données sensibles sur votre bien-être mental, et nous ne les traitons qu'avec votre autorisation explicite.",
      "ageAttestation": "J'ai 16 ans ou plus.",
      "consentRequiredHint": "Cochez les quatre cases ci-dessus pour continuer."
```

Insert as text at these anchors. Do **not** parse and re-serialise either file.

Note the hint correction: both languages counted the boxes literally, so a fourth checkbox made the accessible explanation wrong.

- [ ] **Step 2: Add the state and the gate flag**

`apps/web/app/register/page.tsx` — after the `healthConsent` state declaration (line 38), add:

```typescript
  const [ageAttested, setAgeAttested] = useState(false)
```

Then in the `canSubmitRegistration` call (line 53), add the fourth flag:

```typescript
  const consentsAccepted = canSubmitRegistration({
    terms: termsAccepted,
    privacy: privacyAccepted,
    healthData: healthConsent,
    age: ageAttested,
  })
```

- [ ] **Step 3: Pass it to `register()`**

In the `await register({ ... })` call, after `health_data_consent: healthConsent,` add:

```typescript
        age_attested: ageAttested,
```

- [ ] **Step 4: Render the fourth checkbox**

After the health-consent checkbox block and **before** the `{error && (` block, insert:

```tsx
        {/* Deliberately not a document link, for the same reason as the health
            consent above: an age attestation is its own act, and "I accept the
            Terms" is exactly what does not qualify as one. No birthdate is
            asked for or stored — see the age assurance design §2.2. */}
        <div className="flex items-start gap-2 text-left">
          <Checkbox
            id="register-age"
            checked={ageAttested}
            onCheckedChange={(checked) => setAgeAttested(checked === true)}
            disabled={submitting}
            required
          />
          <label
            htmlFor="register-age"
            className="text-sm text-muted-foreground"
          >
            {t("ageAttestation")}
          </label>
        </div>
```

- [ ] **Step 5: Record the attestation on the OAuth path**

`apps/web/app/auth/callback/route.ts` — inside the `else` branch, after the existing `record_consent` call and its error log, add:

```typescript
      // The attestation rides the same validated param: both acts are gated by
      // the same checkbox set before the redirect, so one version covers both.
      const { error: ageError } = await supabase.rpc("record_consent", {
        p_kind: "age_assurance",
        p_document_version: consentVersion,
        p_source: "web_oauth",
      })
      if (ageError) {
        console.error("[auth/callback] record_consent age_assurance failed:", ageError.message)
      }
```

Same rules as its neighbour: idempotent, so a replayed callback is a no-op, and a failure is logged loudly but never blocks the sign-in — a silently missing record is the defect being fixed.

- [ ] **Step 6: Lint and typecheck**

Run: `npm run lint --workspace=apps/web`
Expected: PASS. The Task 7 failure is now resolved.

- [ ] **Step 7: Run the full web suite**

Run: `npm run test --workspace=apps/web`
Expected: all PASS, including the four `registration-gate` cases and the two `consent` binding cases.

- [ ] **Step 8: Build**

Run: `npm run build --workspace=apps/web`
Expected: PASS.

- [ ] **Step 9: Verify the gate by hand**

Run the app and open `/register`. Confirm:

1. With the first three boxes ticked and the age box clear, the submit button is disabled **and** both OAuth buttons are `aria-disabled`.
2. The hint under the divider reads "Tick all four boxes above to continue."
3. Ticking the fourth box enables all three paths.
4. Switch the locale to French and confirm the same, with "Cochez les quatre cases".

- [ ] **Step 10: Commit**

```bash
git add apps/web/app/register/page.tsx apps/web/app/auth/callback/route.ts apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(auth): ask for the 16+ attestation on both signup paths

Adds the fourth acceptance to /register, which gates the email submit
and both OAuth buttons through canSubmitRegistration, and records the
attestation from the OAuth callback on the same validated version param
the Art. 9 consent already uses.

Refs #816"
```

---

### Task 9: Verify the attestation lands

**Files:** none — this is a verification task.

- [ ] **Step 1: Create an account through the email path**

Sign up on `/register` with all four boxes ticked.

- [ ] **Step 2: Confirm the ledger row**

Query the linked project:

```sql
select kind, document_version, source, granted_at
  from public.user_consents
 where user_id = '<the new user id>'
 order by kind;
```

Expected: three rows — `age_assurance` and `health_data` both `web_register` at `1.3.0`, plus whatever the flow produced for `public_profile` (likely none at signup).

- [ ] **Step 3: Create an account through the OAuth path**

Sign up with Google. Re-run the query for that user.

Expected: `age_assurance` and `health_data`, both `web_oauth` at `1.3.0`.

- [ ] **Step 4: Open the PR**

PR title: `feat(auth): ask for a 16+ age attestation at signup`
Body: `Refs #816` — part 3 of 3. Note the stack position (depends on part 1).
Labels: `feat`, `auth`, `web`. Milestone: `M55 · Compliance Batch A`.
Lab Note: **required** — user-facing. See Task 10.

---

# Part 4 — Close-out

### Task 10: Lab Notes

Parts 2 and 3 both ship something a user notices, so each needs a `## Lab Note (EN/FR)` section. One block per PR.

- [ ] **Step 1: Part 2's note (the published text)**

```yaml
species: feature
platform: webapp
status: in_progress
published: false
en:
  title: "Clearer rules about who Pebbles is for"
  summary: "We rewrote the age section of the terms and the privacy policy. Pebbles is for people 16 and over, we say so plainly, and we no longer describe checks we were not actually doing."
fr:
  title: "Des règles plus claires sur qui peut utiliser Pebbles"
  summary: "On a réécrit la partie sur l'âge dans les conditions et la politique de confidentialité. Pebbles, c'est à partir de 16 ans, c'est écrit noir sur blanc, et on ne décrit plus des vérifications qu'on ne faisait pas."
nodes: [DM-profiles, DM-account]
suggested:
  molecule: pbbls
  type: announcement
  tags: [changelog]
```

- [ ] **Step 2: Part 3's note (the signup box)**

```yaml
species: feature
platform: webapp
status: in_progress
published: false
en:
  title: "One more box when you sign up"
  summary: "Creating an account now asks you to confirm you are 16 or over. We record the confirmation and nothing else (no birthday, no date, no extra questions)."
fr:
  title: "Une case de plus à l'inscription"
  summary: "La création de compte te demande maintenant de confirmer que tu as 16 ans ou plus. On garde la confirmation et rien d'autre (pas de date de naissance, pas de questions en plus)."
nodes: [DM-profiles, DM-account]
suggested:
  molecule: pbbls
  type: feature
  tags: [changelog]
```

**Before using these:** read the real node ids off the hosted map with `list_nodes` / `get_node`. `DM-profiles` and `DM-account` come from the finding's `node_ids` and must be confirmed to exist — an id matching nothing is dropped from the changelog entry.

Note the register split: the Lab Note skill's French uses "Tu", while the app's own i18n strings use "vous". Both are correct in their own place; do not unify them.

- [ ] **Step 3: Move the map nodes to `development`**

When part 1 is picked up, `update_node` the affected acceptance/view nodes to `development` over `arkaik-mcp`. If the MCP tools are unavailable, **say so and stop** — do not fall back to `docs/arkaik/bundle.json`.

---

### Task 11: Follow-on issues

- [ ] **Step 1: File the iOS issue**

Title: `[Feat] Ask for the 16+ age attestation on iOS signup`
Body: references `F-2026-08-SAF-supabase-01` and the design §9. Note that `record_consent` and the `ios_register` / `ios_oauth` source values already exist, so this is client work only.

**It must send `signup_surface: "ios"` alongside `age_attested_at`.** `handle_new_user` maps that key through a closed `case` whose fallback is `web_register`, so a native signup that omits it is stamped with false provenance — silently, with no error, in the one table whose purpose is to be true under audit, and uncorrectable after the fact. State this in the issue body, not just in review.

Labels: `feat`, `auth`, `ios`. Milestone: `M55 · Compliance Batch A`.

- [ ] **Step 2: File the Android issue**

Same body with `android_register` / `android_oauth` and `signup_surface: "android"`, including the same warning. Labels: `feat`, `auth`, `android`. Mirrors iOS 1:1.

- [ ] **Step 2b: File the malformed-metadata issue**

Title: `[Fix] Malformed signup metadata aborts account creation with an opaque 500`
Body: `age_attested_at: "banana"` (or a bad `health_data_consent_at`, `terms_accepted_at`, `privacy_accepted_at`) raises during `handle_new_user`'s `declare` block, aborting the transaction; GoTrue returns `500 Database error saving new user` with nothing distinguishing the cause. Pre-existing across four metadata keys; found during the age-assurance review and deliberately not fixed as a drive-by. Suggested fix: a guarded conversion per key, so unreadable metadata records nothing rather than failing the account.
Labels: `fix`, `db`, `supabase`. Milestone: `M55 · Compliance Batch A`.

- [ ] **Step 3: File the store-declarations issue**

Title: `[Chore] Commit the store age and target-audience declarations and diff them in CI`
Labels: `chore`, `infra`. Milestone: `M57 · Store readiness` — it belongs with store readiness, not this batch.

- [ ] **Step 4: Add the attestation to #788's scope**

Comment on #788: every account predating this change has no `age_assurance` row, and the re-consent surface should ask for it alongside the Art. 9 consent rather than growing a second blocking screen. It records through `record_consent` with `p_source: 'web_settings'`.

---

### Task 12: Resolve the finding — with its scope stated

- [ ] **Step 1: Resolve only after all three PRs have merged**

Use `kritik_resolve_finding` with `finding_id: "F-2026-08-SAF-supabase-01"` and `resolved_by` set to the part 3 PR URL.

- [ ] **Step 2: State the residual scope in the same note**

Resolving on new-account web enforcement alone would overclaim. The note must say: this is attestation, not verification; existing accounts stay unattested until #788; iOS and Android signups stay ungated until their own issues; store declarations are not yet reconciled. Design §12 is the source.

If that residual feels too large to call the finding resolved, `kritik_accept_finding` on the remainder is the honest alternative — ask the maintainer rather than deciding alone.

---

## Verification summary

| Claim | Proof | Task |
|---|---|---|
| `age_assurance` is an accepted kind | `db:reset` migration replay | 1 |
| Types regenerated, not truncated | `git diff --stat` + `tsc --noEmit` | 2 |
| `withdraw_consent('age_assurance')` is refused | `db:verify:purge` case | 3 |
| The attestation is purged with the account | `db:verify:purge` count = 3 | 3 |
| EN and FR legal text agree | Read side by side | 4, 5 |
| No dangling 13/15 references remain | `grep` sweep | 5 |
| The version constant matches both policies | `consent.test.ts` | 5 |
| Registration cannot proceed unattested | `registration-gate.test.ts` | 6 |
| The email path records the row | Live signup + SQL | 9 |
| The OAuth path records the row | Live Google signup + SQL | 9 |

---

# Lessons learned

Written after the three stacks shipped. Recorded here rather than promoted into
CLAUDE.md: per the editing rule, learnings harden at a milestone-boundary audit,
not per-PR.

## About the change itself

**Replacing an unkept promise with a different unkept promise is the same
defect.** The first draft of privacy §10.2 swapped "we confirm parental consent
during sign-up" for "you are asked to confirm you are 16". Both describe a
mechanism. The finding is *about* describing mechanisms the product does not
have, so the fix has to state the rule and the remedy and let the mechanism
sentence wait for the mechanism (#825). When a claim is true on some surfaces
and not others, a conditional ("Where we ask...") is honest where an affirmative
is not.

**The native surfaces serve the web legal documents.** `LegalDocumentSheet.swift:14-15`
and `LegalDocs.kt:14-15` open `https://www.pbbls.app/docs/...`. So a change to
`apps/web/docs/` is a four-surface release the moment it deploys, and the
standing cross-surface rule applies to published text exactly as it does to a
schema contract. This is not obvious from the file path.

**A guard by omission needs a structural backstop.** Leaving `age_assurance` out
of `withdraw_consent`'s allowlist is correct and sufficient — but the warning
lived in a different migration than the function a maintainer would open, since
widening means copying forward the *newest* emission. The `user_consents_age_not_withdrawable`
CHECK is what actually survives a future rewrite. Comments do not enforce
invariants; constraints do.

**Validate the untrusted path, not the convenient one.** The design praised the
OAuth callback for validating `document_version` and never noticed the trigger
path validated nothing — while the trigger path is the one where the value is
unambiguously attacker-supplied. When two paths write the same column, check
which one is actually exposed before deciding which deserves the guard.

## About running this kind of work

**Subagents share one working tree.** Switching branches while an agent is
running silently redirects its commits: two legal-text commits landed on the
docs branch because the controller checked out a different branch mid-flight.
The agent had no way to detect it. Either keep the controller off git entirely
while agents run, or give each agent its own worktree.

**Two agents amending concurrently can revert each other with no conflict.** One
agent's rebase cherry-picked a sibling's pre-amend SHA and silently dropped its
newer work. Git reports nothing — it is a clean replay of a stale commit. It was
caught only by blob-diffing against a pre-reset snapshot. Never instruct an
agent to amend a commit that is not `HEAD`, and never while a sibling is
committing.

**"Carry the body forward verbatim" must mean a pure addition.** The prescribed
`record_consent` body dropped a four-line comment from its source. The
implementer wrote it as specified and flagged it. A re-emission that deletes
anything is the exact drift the verbatim rule exists to prevent, so the check is
`diff` with zero `<`-side lines — not "looks right".

**An assertion that cannot fail is worse than no assertion.** `withdrawAgeErr !== null`
was satisfied by a renamed function, a dropped grant, an expired session and
`no_active_consent` alike — i.e. loudest precisely when it should be silent.
Assert the specific error, and assert the state that should have survived.

**Tell reviewers the stack shape.** A spec-compliance reviewer flagged three
"false claims" in comments that were simply forward references to later parts of
the same stack. The findings were noise because the prompt omitted context the
reviewer needed, which is the prompt's defect, not the reviewer's.
