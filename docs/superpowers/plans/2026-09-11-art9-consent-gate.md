# Art. 9 Consent Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the web app a real Art. 9 explicit-consent gate — captured at signup on every path including OAuth, recorded as a version-bound ledger the backend honours, withdrawable from Settings — and put the DPIA the regulation requires on record.

**Architecture:** A new `user_consents` table records consent *acts* (grant, withdrawal, version supersession) bound to a privacy-policy version, written only through two `security definer` RPCs. The email signup path carries consent in the existing `signUp` metadata and `handle_new_user` inserts the row; the OAuth path carries it as a callback query parameter and `app/auth/callback/route.ts` records it server-side. Settings gains a withdrawal surface, and the existing public-profile toggle is rerouted through the same RPCs.

**Tech Stack:** Postgres/Supabase migrations, Next.js 16 App Router, React 19, TypeScript strict, next-intl, Vitest, shadcn/ui.

**Spec:** `docs/superpowers/specs/2026-09-11-art9-consent-gate-design.md`
**Finding:** Kritik `F-2026-08-GDP-web-01` (GDP-02, web, high/P1)

---

## Read this before Task 1

**Conventions that will bite you if you skip them:**

- **Never run `npm run db:types`.** It targets `--local` (Docker) and truncates `packages/supabase/types/database.ts` when Docker is absent. Always `npm run db:types:remote --workspace=packages/supabase`.
- **Never run Supabase locally.** This project deploys to the remote/linked project. No Docker.
- **`purge_account` and `handle_new_user` use in-body append markers.** `create or replace` has no merge semantics and git reports no conflict, so before applying any migration that re-emits either, diff the new body against the previous emission and union manually.
- **Vitest only collects `lib/**/*.test.ts`** (see `apps/web/vitest.config.ts`). A test outside `lib/` runs nowhere.
- **TypeScript strict, no `any`, no type assertions unless unavoidable.**
- **Never refactor code you were not asked to change.** Mention it, don't do it.
- `apps/web/vitest.config.ts` has an **uncommitted local change** (`pool: "threads"`) that is not part of this work. Leave it uncommitted; never `git add` it.

**Deviation from the spec, decided during planning:** the spec's §4.1 table has a single `withdrawn_at`. This plan adds a second nullable timestamp, `superseded_at`, so that "the user withdrew" and "a newer policy version replaced this" stay distinguishable in the ledger. Conflating them would make the accountability record lie about what the user did. The active-row index keys on both.

**Issues:** #774 (Part 1), #775 (Part 2), #776 (Part 3), #777 (Part 4), #778 (Part 5), all on M55 · Compliance Batch A.

---

## File Structure

**Part 1 — DPIA (docs only)**
- Create: `docs/compliance/lawful-basis-map.md` — one row per processing operation → Art. 6 basis → Art. 9 condition → where the consent record lives. Living table.
- Create: `docs/compliance/dpia.md` — the dated assessment. Ships `status: draft`, unsigned.

**Part 2 — consent contract (`packages/supabase`)**
- Create: `supabase/migrations/20260911090000_user_consents.sql` — table, index, RLS, `record_consent`, `withdraw_consent`, `handle_new_user` re-emission.
- Create: `supabase/migrations/20260911090100_purge_account_consents.sql` — `purge_account` re-emission, isolated so the body diff is reviewable on its own.
- Modify: `scripts/verify-account-purge.ts` — seed + zero-row assertion.
- Modify: `types/database.ts` — regenerated, never hand-edited.

**Part 3 — capture at signup (`apps/web`)**
- Create: `lib/config/consent.ts` — `CONSENT_DOCUMENT_VERSION`, the consent kinds.
- Create: `lib/config/consent.test.ts` — pins the constant to the privacy doc frontmatter.
- Create: `lib/auth/registration-gate.ts` — pure `canSubmitRegistration`.
- Create: `lib/auth/registration-gate.test.ts`.
- Modify: `lib/types.ts` — `RegisterInput` gains `health_data_consent`.
- Modify: `lib/data/useSupabaseAuth.ts` — signup metadata, `buildCallbackUrl` consent param.
- Modify: `app/register/page.tsx` — third checkbox, OAuth gating, a11y hint.
- Modify: `app/auth/callback/route.ts` — server-side `record_consent`.
- Modify: `lib/i18n/messages/{en,fr}.json`.

**Part 4 — withdrawal (`apps/web`)**
- Create: `lib/data/consent.ts` — pure `activeConsent` selector + `ConsentRow` type.
- Create: `lib/data/consent.test.ts`.
- Create: `lib/data/useConsents.ts` — read hook + the two RPC callers.
- Create: `components/settings/ConsentSection.tsx`.
- Modify: `app/settings/page.tsx` — mount `ConsentSection`, route the public toggle through the RPCs.
- Modify: `components/settings/PublicProfileSection.tsx` — consent-naming copy.
- Modify: `lib/i18n/messages/{en,fr}.json`.

**Part 5 — policy text**
- Modify: `apps/web/docs/privacy/en.md`, `apps/web/docs/privacy/fr.md` — §3.2, §4.2, §4.3, frontmatter `1.1.0` → `1.2.0`.
- Modify: `apps/web/lib/config/consent.ts` — constant follows the bump.

---

# PART 1 — The DPIA

Branch: `docs/774-dpia`. Docs only. No code, no tests.

### Task 1: The lawful-basis map

**Files:**
- Create: `docs/compliance/lawful-basis-map.md`

- [ ] **Step 1: Create the directory and write the map**

The table must be grounded in the real schema. Read `packages/supabase/supabase/migrations/20260411000001_core_tables.sql` first for the actual column names. Write:

```markdown
# Lawful basis map

**Status:** living reference. Update it in the same PR as any feature that adds
a processing operation. Separate from `dpia.md` so this can move without
re-dating the assessment.

**Last reviewed:** 2026-09-11

| # | Processing operation | Data | Art. 6 basis | Art. 9 condition | Consent record |
|---|---|---|---|---|---|
| 1 | Account creation and authentication | email, password hash, `profiles.display_name` | 6(1)(b) contract | n/a | — |
| 2 | Recording a pebble | `pebbles.intensity`, `positiveness`, `emotion_id`, `description` | 6(1)(a) consent | **9(2)(a) explicit consent** | `user_consents.kind = 'health_data'` |
| 3 | Photo attachments on a pebble | `snaps`, storage objects | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 4 | Naming souls in a pebble | `souls`, `pebble_souls` | 6(1)(f) legitimate interest | n/a (third-party data — see DPIA §3) | — |
| 5 | Reflective cards | `pebble_cards` | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 6 | Mutual connections and connection-visible pebbles | `connections`, `pebbles.visibility` | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 7 | Public profile | `profiles.handle`, `public_profile` | 6(1)(a) consent | 9(2)(a) — enlarges #2 to the open web | `user_consents.kind = 'public_profile'` |
| 8 | Public share-by-link of a pebble | `pebbles.visibility = 'public'` | 6(1)(a) consent | 9(2)(a), via #2 | as #2 |
| 9 | Karma, bounces, achievements | `karma_events`, `bounces`, `achievement_unlocks` | 6(1)(b) contract | n/a | — |
| 10 | Glyph marketplace | `glyphs`, `glyph_submissions`, `glyph_entitlements` | 6(1)(b) contract | n/a | — |
| 11 | Operator analytics | aggregate views, `is_admin`-gated | 6(1)(f) legitimate interest | see gap below | — |
| 12 | Security and abuse prevention | auth logs | 6(1)(f) legitimate interest | n/a | — |

## Known gaps

- **#11 aggregates emotion data with no minimum-cohort threshold.** For a week
  with one or two active users the weekly emotion mix is effectively one
  identifiable person's record. Tracked as Kritik `F-2026-08-GDP-admin-06`.
- **#4 is third-party personal data** the data subject never supplied. See
  DPIA §3 "the souls problem".
- **Art. 20 portability is unmet**: no data export exists on any surface.
```

- [ ] **Step 2: Verify every table name in the map exists**

Run: `for t in pebbles snaps souls pebble_souls pebble_cards connections profiles karma_events bounces achievement_unlocks glyphs glyph_submissions glyph_entitlements; do grep -qrn "create table public.$t" packages/supabase/supabase/migrations/ && echo "OK $t" || echo "MISSING $t"; done`

Expected: `OK` for every line. Any `MISSING` means the map cites a table that does not exist — fix the map, do not invent the table.

- [ ] **Step 3: Commit**

```bash
git add docs/compliance/lawful-basis-map.md
git commit -m "docs(legal): add the lawful-basis map"
```

### Task 2: The DPIA

**Files:**
- Create: `docs/compliance/dpia.md`

- [ ] **Step 1: Write the DPIA**

Follow the CNIL PIA structure. Use this skeleton and fill every section from the repo — do not leave a heading with no content under it, and do not invent facts:

```markdown
---
title: Data Protection Impact Assessment — Pebbles
status: draft
version: 0.1.0
reviewed_by:
validated_on:
---

# Data Protection Impact Assessment — Pebbles

> **Draft. Not yet validated.** Written by an engineering agent, not counsel.
> `reviewed_by` and `validated_on` are deliberately blank: an impact assessment
> is the controller's act. Every factual claim below cites a file in this
> repository; every uncertainty is marked as an open question.

## 1. Context

### 1.1 Controller
[Identity from `apps/web/docs/legal-notice/en.md` — read it, do not guess.]

**No DPO is appointed.** Stated rather than omitted: Art. 37 designation is not
mandatory here, and the reasoning belongs on the record.

### 1.2 The processing
Pebbles is a personal journal of emotional moments. A "pebble" records a time, an
intensity, a positiveness, an emotion, optionally related people ("souls"), life
domains, reflective cards, and free text.

### 1.3 Data inventory
[One row per table, from `packages/supabase/supabase/migrations/`. Columns:
table · what it holds · special category? · retention.]

### 1.4 Recipients and processors
- **Supabase** — database, auth, storage, edge functions.
- **Vercel** — application hosting and function execution for `apps/web` and
  `apps/admin`, functions pinned to `cdg1` (Paris) by PR #765 and named in the
  policy's processor inventory by PR #767.
[Anything else must be read out of the repo, not assumed.]

### 1.5 Purposes and retention
[From the privacy policy plus what the schema actually does.]

## 2. Necessity and proportionality

Cross-reference: `docs/compliance/lawful-basis-map.md`.

### 2.1 Minimisation
### 2.2 Accuracy and retention
### 2.3 Data-subject rights

| Right | Status | Where |
|---|---|---|
| Access (Art. 15) | partial — visible in-app, no bulk copy | — |
| Rectification (Art. 16) | met | pebble edit, settings |
| Erasure (Art. 17) | met | `purge_account` + delete-account edge function + `DeleteAccountSection` |
| **Portability (Art. 20)** | **unmet — no export exists on any surface** | gap, see §4 |
| Withdraw consent (Art. 7(3)) | met on web from this change | `withdraw_consent` RPC + settings |

## 3. Risks to rights and freedoms

For each: severity, likelihood, existing controls, residual risk.

### 3.1 Illegitimate access to data
### 3.2 Unwanted modification of data
### 3.3 Disappearance of data
### 3.4 Re-identification through public profiles
### 3.5 The souls problem
A user names other people inside their own pebbles. Those people are data
subjects who never supplied the data and cannot exercise rights they do not know
they have. Assess the household-exemption reasoning (Art. 2(2)(c)) and its limit:
the exemption weakens as pebbles become shareable (connections, public links).

### 3.6 Operator analytics without a minimum cohort
Tracked as Kritik `F-2026-08-GDP-admin-06`.

## 4. Measures

### 4.1 In place
- Row-level security on every user table.
- Cross-user reads go through `security definer` RPC projections building an
  explicit jsonb allowlist (`get_public_profile` is the template); `profiles`
  RLS is never widened.
- No telemetry or analytics SDKs reach sensitive fields.
- Account deletion works end to end and is covered by
  `packages/supabase/scripts/verify-account-purge.ts`.
- Function execution pinned to the EU (`cdg1`).

### 4.2 Planned
| Measure | Where |
|---|---|
| Art. 9 explicit consent at signup, recorded and withdrawable | this change |
| Re-consent surface for accounts created before it | M55 |
| Data export (Art. 20) | unscheduled — new issue |
| Minimum-cohort threshold on operator analytics | `F-2026-08-GDP-admin-06` |
| iOS and Android consent capture | `F-2026-08-GDP-ios-04`, `-android-02` |
| Sensitive-column inventory + CI sink guard | `F-2026-08-GDP-supabase-02` |

## 5. When to revisit

Re-open this assessment before merging anything that:
- adds or widens a column holding emotion, intensity, positiveness or free-text
  reflection;
- adds a recipient or processor;
- widens who can see a pebble;
- adds a new export, log or analytics sink over any of the above.

> This trigger is intended to become a repo rule in `CLAUDE.md`. Per
> `CLAUDE.md`, learnings are promoted at the audit grooming pass at milestone
> boundaries, never per-PR — so it lives here until then.

## 6. Open questions for the controller

[List every judgement call you could not resolve from the repo. Do not guess and
do not delete the section — an empty "open questions" on a first-draft DPIA is
itself a signal that the draft was not taken seriously.]

## 7. Validation

| | |
|---|---|
| Reviewed by | |
| Role | |
| Validated on | |
| Outcome | |
```

- [ ] **Step 2: Verify no section was left as a bare heading**

Run: `awk '/^#{2,3} /{h=$0; getline; while($0 ~ /^$/) getline; if ($0 ~ /^#{2,3} /) print "EMPTY: " h}' docs/compliance/dpia.md`

Expected: no output. Any `EMPTY:` line is an unfilled section — fill it.

- [ ] **Step 3: Verify the DPIA ships unsigned**

Run: `grep -E "^(reviewed_by|validated_on):" docs/compliance/dpia.md`

Expected: both keys present with empty values. If either is filled, clear it — signing is the maintainer's act, not yours.

- [ ] **Step 4: Commit**

```bash
git add docs/compliance/dpia.md
git commit -m "docs(legal): add the DPIA draft for the current feature set"
```

**Part 1 is done.** Open the PR with **no Lab Note** (docs-only), and add the `no-lab-note` label if the advisory reminder comments.

---

# PART 2 — The consent contract

Branch: `feat/775-consent-contract`, stacked on Part 1 or on `main`. **This part owns every database change in the stack.**

### Task 3: The `user_consents` table, RLS and RPCs

**Files:**
- Create: `packages/supabase/supabase/migrations/20260911090000_user_consents.sql`

- [ ] **Step 1: Read the previous `handle_new_user` emission before touching it**

Run: `cat packages/supabase/supabase/migrations/20260729120000_handle_new_user_consent.sql`

You are about to `create or replace` that function. Everything in its current body must survive into the new one. Confirm it is the latest emission:

Run: `grep -rl "create or replace function public.handle_new_user" packages/supabase/supabase/migrations/ | sort | tail -1`

Expected: `20260729120000_handle_new_user_consent.sql`. If a later file appears, read **that** body instead — it is the one you must preserve.

- [ ] **Step 2: Write the migration**

```sql
-- Migration: Art. 9 explicit consent as a version-bound ledger
--
-- The privacy policy qualifies moods, emotion labels and CBT reflections as
-- Art. 9 special-category health data processed only on explicit consent. No
-- consent step existed on any path (Kritik F-2026-08-GDP-web-01). This is the
-- storage half: the table, its invariant, and the only two ways to write it.

-- ============================================================
-- 1. The ledger
-- ============================================================
-- A row is a consent ACT, not a flag. Granting inserts; withdrawing stamps
-- withdrawn_at; a policy-version bump stamps superseded_at and inserts a new
-- row. withdrawn_at and superseded_at are kept distinct on purpose: "the user
-- withdrew" and "a newer document replaced this" are different facts, and an
-- accountability record that conflates them is a record that lies.

create table public.user_consents (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users(id),
  kind             text not null check (kind in ('health_data', 'public_profile')),
  document_version text not null,
  source           text not null check (source in ('web_register', 'web_oauth', 'web_settings')),
  granted_at       timestamptz not null default now(),
  withdrawn_at     timestamptz,
  superseded_at    timestamptz,
  created_at       timestamptz not null default now()
);

-- No `on delete cascade`, matching profiles: the purge stays explicit, so the
-- zero-row assertion in verify-account-purge.ts stays meaningful.

-- At most one ACTIVE consent per (user, kind) — a database invariant, not a
-- convention the application is trusted to keep.
create unique index user_consents_active_kind
  on public.user_consents (user_id, kind)
  where withdrawn_at is null and superseded_at is null;

create index user_consents_user_id on public.user_consents (user_id);

-- ============================================================
-- 2. RLS — owner select only
-- ============================================================
-- There are deliberately NO insert/update/delete policies. A consent record
-- must never land through a client write path: every write goes through the
-- two definer RPCs below, which stamp auth.uid() themselves. Same structural
-- choice as the whisper design — the absence of a policy is the guard.

alter table public.user_consents enable row level security;

create policy user_consents_select on public.user_consents for select
  using (auth.uid() = user_id);

-- ============================================================
-- 3. record_consent
-- ============================================================

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
    raise exception 'not_authenticated' using errcode = '28000';
  end if;

  -- Idempotent. A replayed OAuth callback and a second Save on an unchanged
  -- settings toggle must both be no-ops: the partial unique index would reject
  -- the duplicate insert anyway, and an error there would surface to the user
  -- as a failed signup.
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
  values (v_user_id, p_kind, p_document_version, p_source);
end;
$$;

-- ============================================================
-- 4. withdraw_consent
-- ============================================================

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
    raise exception 'not_authenticated' using errcode = '28000';
  end if;

  update public.user_consents
     set withdrawn_at = now()
   where user_id = v_user_id
     and kind = p_kind
     and withdrawn_at is null
     and superseded_at is null;
  get diagnostics v_n = row_count;

  if v_n = 0 then
    raise exception 'no_active_consent' using errcode = 'P0002';
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

grant execute on function public.record_consent(text, text, text) to authenticated;
grant execute on function public.withdraw_consent(text)            to authenticated;

-- ============================================================
-- 5. handle_new_user — record the email-path consent at signup
-- ============================================================
-- Body carried forward verbatim from 20260729120000 (profiles insert with the
-- two NULL-safe consent timestamps), with the Art. 9 insert appended.
--
-- Why metadata rather than a client RPC: at signUp() time there is no session
-- yet if email confirmations are enabled (M55 plans to enable them), so a
-- client-side record_consent call would simply be lost. The OAuth path cannot
-- use metadata at all — no signup metadata survives an OAuth round trip — and
-- records from the callback route instead.

create or replace function public.handle_new_user()
returns trigger as $$
declare
  v_consent_at      timestamptz := (new.raw_user_meta_data->>'health_data_consent_at')::timestamptz;
  v_consent_version text        := new.raw_user_meta_data->>'health_data_consent_version';
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
  -- metadata) skips this silently and records from the callback instead.
  if v_consent_at is not null and v_consent_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'health_data', v_consent_version, 'web_register', v_consent_at);
  end if;

  return new;
end;
$$ language plpgsql security definer set search_path = public;
```

- [ ] **Step 3: Diff the `handle_new_user` body against the previous emission**

Run:
```bash
diff <(sed -n '/create or replace function public.handle_new_user/,/^\$\$ language/p' packages/supabase/supabase/migrations/20260729120000_handle_new_user_consent.sql) \
     <(sed -n '/create or replace function public.handle_new_user/,/^\$\$ language/p' packages/supabase/supabase/migrations/20260911090000_user_consents.sql)
```

Expected: the only differences are **additions** — the `declare` block and the `if v_consent_at is not null` insert. If any line from the old body is missing or altered in the new one, you have silently dropped a prior append. Restore it before going further.

- [ ] **Step 4: Apply the migration**

Run: `npm run db:push --workspace=packages/supabase`
Expected: the migration applies cleanly against the linked project.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/20260911090000_user_consents.sql
git commit -m "feat(db): record Art. 9 consent as a version-bound ledger"
```

### Task 4: Purge the consent ledger on account deletion

**Files:**
- Create: `packages/supabase/supabase/migrations/20260911090100_purge_account_consents.sql`

- [ ] **Step 1: Read the previous `purge_account` emission in full**

Run: `grep -rl "create or replace function public.purge_account" packages/supabase/supabase/migrations/ | sort | tail -1`

Expected: `20260731090000_purge_account_union.sql`. Read the whole function body from that file. You must reproduce it **verbatim** with exactly one addition.

- [ ] **Step 2: Write the migration**

Copy the entire `create or replace function public.purge_account(...)` block from `20260731090000_purge_account_union.sql` into the new file, and insert this immediately after the `>>> APPEND new per-user tables from later milestones HERE. <<<` marker, before the `connections` delete:

```sql
  -- The Art. 9 consent ledger. Erasure wins over the Art. 7(1) duty to
  -- demonstrate consent: once there is no data subject, there is nothing left
  -- to demonstrate about.
  delete from public.user_consents where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('user_consents', v_n);
```

Head the file with:

```sql
-- Migration: purge_account also erases the Art. 9 consent ledger
--
-- Body copied verbatim from 20260731090000_purge_account_union.sql with one
-- addition at the section-(4) append marker. Re-emitting this function drops
-- any append it does not carry forward, and git reports no conflict — the
-- diff in step 3 is the check.
```

- [ ] **Step 3: Diff the body against the previous emission**

Run:
```bash
diff <(sed -n '/create or replace function public.purge_account/,/^\$\$;/p' packages/supabase/supabase/migrations/20260731090000_purge_account_union.sql) \
     <(sed -n '/create or replace function public.purge_account/,/^\$\$;/p' packages/supabase/supabase/migrations/20260911090100_purge_account_consents.sql)
```

Expected: the ONLY difference is the five added lines from step 2. Anything else — a missing delete, a reordered statement, a changed count key — means a prior append was dropped. Fix it before applying.

- [ ] **Step 4: Apply**

Run: `npm run db:push --workspace=packages/supabase`
Expected: applies cleanly.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/20260911090100_purge_account_consents.sql
git commit -m "feat(db): erase the consent ledger in purge_account"
```

### Task 5: Prove the purge with the harness

**Files:**
- Modify: `packages/supabase/scripts/verify-account-purge.ts`

A table added to `purge_account` gains its seed and its zero-row assertion in the same change. This is the one harness CI does not run.

- [ ] **Step 1: Seed a consent row for the seller**

In the seeding section, immediately after the public-profile block (the one ending with the `get_public_profile` pre-purge check, near line 258), add:

```ts
  // Art. 9 consent ledger rows. Written through the real RPC as the signed-in
  // seller — user_consents has no client insert policy, so a direct insert
  // would prove nothing about the path the app actually uses.
  const { error: consentErr } = await seller.rpc("record_consent", {
    p_kind: "health_data",
    p_document_version: "1.1.0",
    p_source: "web_register",
  });
  if (consentErr) throw new Error(`record_consent health_data: ${consentErr.message}`);

  const { error: publicConsentErr } = await seller.rpc("record_consent", {
    p_kind: "public_profile",
    p_document_version: "1.1.0",
    p_source: "web_settings",
  });
  if (publicConsentErr) throw new Error(`record_consent public_profile: ${publicConsentErr.message}`);

  const consentCount = await countRows("user_consents", "user_id", sellerId);
  if (consentCount !== 2) {
    throw new Error(`expected 2 seeded consent rows, got ${consentCount}`);
  }
```

- [ ] **Step 2: Pin the purge's own accounting**

`user_consents` references `auth.users`, so by the time the zero-row checks run the row would be gone even if the purge delete line were dropped. Only the RPC's own counts catch that. In the `expectedPurged` array, add:

```ts
    ["user_consents", 2], // health_data + public_profile
```

- [ ] **Step 3: Add the zero-row assertion**

In the `sellerScoped` array, add:

```ts
    ["user_consents", "user_id"],
```

- [ ] **Step 4: Run the harness against the linked project**

Run: `npm run db:verify:purge --workspace=packages/supabase`
Expected: exits 0, all checks pass, and the printed purge counts include `"user_consents":2`.

If it exits non-zero, do not proceed — a failing purge harness means account deletion is leaving Art. 9 data behind, which is worse than the finding you are fixing.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/scripts/verify-account-purge.ts
git commit -m "test(db): assert the consent ledger is purged with the account"
```

### Task 6: Regenerate the types

**Files:**
- Modify: `packages/supabase/types/database.ts`

- [ ] **Step 1: Regenerate**

Run: `npm run db:types:remote --workspace=packages/supabase`

**Not `db:types`** — that one targets `--local` and truncates the file when Docker is absent.

- [ ] **Step 2: Verify the new shapes landed**

Run: `grep -c "user_consents\|record_consent\|withdraw_consent" packages/supabase/types/database.ts`
Expected: a number greater than 0. If it is 0, or the file shrank dramatically, the generation failed — `git checkout packages/supabase/types/database.ts` and retry.

- [ ] **Step 3: Typecheck both consumers**

Run: `npm run build --workspace=apps/web`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/types/database.ts
git commit -m "chore(db): regenerate types for the consent ledger"
```

**Part 2 is done.** Open the PR with **no Lab Note** (nothing user-visible ships yet).

---

# PART 3 — Capture at signup

Branch: `feat/776-consent-capture-web`, stacked on Part 2. **`apps/web` only.**

### Task 7: The document-version constant

**Files:**
- Create: `apps/web/lib/config/consent.ts`
- Create: `apps/web/lib/config/consent.test.ts`

- [ ] **Step 1: Write the failing test**

`apps/web/lib/config/consent.test.ts`:

```ts
import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { describe, it, expect } from "vitest"
import { CONSENT_DOCUMENT_VERSION } from "./consent"

/**
 * The consent record is only meaningful if it names the version of the document
 * the person actually saw. Nothing else in the system ties the constant to the
 * document, so this test is the whole binding: bump the policy without bumping
 * the constant and every consent recorded afterwards cites the wrong text.
 */
describe("CONSENT_DOCUMENT_VERSION", () => {
  it("matches the privacy policy frontmatter", () => {
    const path = fileURLToPath(new URL("../../docs/privacy/en.md", import.meta.url))
    const frontmatter = readFileSync(path, "utf8").split("---")[1]
    const version = frontmatter.match(/^version:\s*(.+)$/m)?.[1].trim()

    expect(version).toBeDefined()
    expect(CONSENT_DOCUMENT_VERSION).toBe(version)
  })

  it("matches the French policy too, so the two cannot drift apart", () => {
    const path = fileURLToPath(new URL("../../docs/privacy/fr.md", import.meta.url))
    const frontmatter = readFileSync(path, "utf8").split("---")[1]
    const version = frontmatter.match(/^version:\s*(.+)$/m)?.[1].trim()

    expect(CONSENT_DOCUMENT_VERSION).toBe(version)
  })
})
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `npm run test --workspace=apps/web -- consent`
Expected: FAIL — `Failed to resolve import "./consent"`.

- [ ] **Step 3: Write the minimal implementation**

`apps/web/lib/config/consent.ts`:

```ts
/**
 * The privacy-policy version the consent copy on /register describes.
 *
 * Bump this in the SAME commit as the policy's `version:` frontmatter —
 * `consent.test.ts` fails otherwise. A consent row records the version of the
 * document the person actually read, so an older row citing an older version
 * is correct, not stale (see the design spec §5.3).
 */
export const CONSENT_DOCUMENT_VERSION = "1.1.0"

/** The consent kinds `user_consents.kind` accepts. */
export const CONSENT_KINDS = ["health_data", "public_profile"] as const
export type ConsentKind = (typeof CONSENT_KINDS)[number]

/** Where a consent act was collected. Mirrors `user_consents.source`. */
export type ConsentSource = "web_register" | "web_oauth" | "web_settings"
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test --workspace=apps/web -- consent`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/config/consent.ts apps/web/lib/config/consent.test.ts
git commit -m "feat(auth): bind the consent record to the privacy-policy version"
```

### Task 8: The registration gate

**Files:**
- Create: `apps/web/lib/auth/registration-gate.ts`
- Create: `apps/web/lib/auth/registration-gate.test.ts`

The gate goes in a pure module rather than the component so it is unit-tested directly — business logic stays out of components, and the OAuth buttons and the submit button then provably share one rule.

- [ ] **Step 1: Write the failing test**

`apps/web/lib/auth/registration-gate.test.ts`:

```ts
import { describe, it, expect } from "vitest"
import { canSubmitRegistration, type ConsentChecks } from "./registration-gate"

const all: ConsentChecks = { terms: true, privacy: true, healthData: true }

describe("canSubmitRegistration", () => {
  it("allows submission only when all three are accepted", () => {
    expect(canSubmitRegistration(all)).toBe(true)
  })

  it("blocks when any single box is unticked", () => {
    expect(canSubmitRegistration({ ...all, terms: false })).toBe(false)
    expect(canSubmitRegistration({ ...all, privacy: false })).toBe(false)
    expect(canSubmitRegistration({ ...all, healthData: false })).toBe(false)
  })

  it("blocks when nothing is accepted", () => {
    expect(canSubmitRegistration({ terms: false, privacy: false, healthData: false })).toBe(false)
  })
})
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `npm run test --workspace=apps/web -- registration-gate`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the minimal implementation**

`apps/web/lib/auth/registration-gate.ts`:

```ts
/** The three acceptances /register requires before an account can be created. */
export type ConsentChecks = {
  terms: boolean
  privacy: boolean
  /** Art. 9 explicit consent — a separate act, not a document acknowledgement. */
  healthData: boolean
}

/**
 * Whether registration may proceed. Shared by the email submit button and both
 * OAuth buttons: before this existed the OAuth buttons were gated on
 * `submitting` alone, so an OAuth account was created with no consent record at
 * all (Kritik F-2026-08-GDP-web-01).
 */
export function canSubmitRegistration(checks: ConsentChecks): boolean {
  return checks.terms && checks.privacy && checks.healthData
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test --workspace=apps/web -- registration-gate`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/auth/registration-gate.ts apps/web/lib/auth/registration-gate.test.ts
git commit -m "feat(auth): extract the registration consent gate"
```

### Task 9: Carry the consent through both auth paths

**Files:**
- Modify: `apps/web/lib/types.ts:383-388`
- Modify: `apps/web/lib/data/useSupabaseAuth.ts:30-32` and `:143-177`

- [ ] **Step 1: Extend `RegisterInput`**

In `apps/web/lib/types.ts`, replace the `RegisterInput` type with:

```ts
export type RegisterInput = {
  email: string
  password: string
  terms_accepted: boolean
  privacy_accepted: boolean
  /** Art. 9 explicit consent for emotional/health-adjacent data. */
  health_data_consent: boolean
}
```

- [ ] **Step 2: Send the consent in the signup metadata**

In `apps/web/lib/data/useSupabaseAuth.ts`, add the import:

```ts
import { CONSENT_DOCUMENT_VERSION } from "@/lib/config/consent"
```

and replace the `options.data` object inside `register` with:

```ts
        data: {
          terms_accepted_at: input.terms_accepted ? new Date().toISOString() : null,
          privacy_accepted_at: input.privacy_accepted ? new Date().toISOString() : null,
          // handle_new_user turns these two into the user_consents row. Sent as
          // metadata rather than a post-signup RPC because there is no session
          // yet when email confirmations are on.
          health_data_consent_at: input.health_data_consent ? new Date().toISOString() : null,
          health_data_consent_version: input.health_data_consent ? CONSENT_DOCUMENT_VERSION : null,
        },
```

- [ ] **Step 3: Carry the consent across the OAuth round trip**

Replace `buildCallbackUrl` with:

```ts
/**
 * OAuth callback URL, optionally carrying a validated post-auth destination
 * (M49, design D12) and the Art. 9 consent act. A `next` that is not strictly
 * relative is dropped — the callback route re-validates on its side regardless.
 *
 * The consent rides the URL because no signup metadata survives an OAuth round
 * trip, and the callback route is server-side so the record survives whatever
 * the tab does next. It is not an attack surface: the parameter can only record
 * consent for whoever completes the code exchange, which is that person.
 */
function buildCallbackUrl(next?: string, consentVersion?: string): string {
  const base = `${window.location.origin}/auth/callback`
  const params = new URLSearchParams()
  if (isSafeRelativePath(next)) params.set("next", next)
  if (consentVersion) params.set("consent", consentVersion)
  const query = params.toString()
  return query ? `${base}?${query}` : base
}
```

- [ ] **Step 4: Thread the consent version through both OAuth callers**

In `apps/web/lib/data/auth-context.ts`, change both signatures:

```ts
  signInWithApple(next?: string, consentVersion?: string): Promise<void>
  signInWithGoogle(next?: string, consentVersion?: string): Promise<void>
```

In `useSupabaseAuth.ts`, change both implementations — `signInWithApple` shown, `signInWithGoogle` identical with `provider: "google"`:

```ts
  const signInWithApple = useCallback(async (next?: string, consentVersion?: string) => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "apple",
      options: { redirectTo: buildCallbackUrl(next, consentVersion) },
    })
    if (error) throw new Error(error.message)
  }, [])
```

- [ ] **Step 5: Typecheck**

Run: `npm run build --workspace=apps/web`
Expected: **FAIL**, with an error in `app/register/page.tsx` — `register()` is now missing `health_data_consent`. That is the next task. Do not silence it.

- [ ] **Step 6: Commit**

```bash
git add apps/web/lib/types.ts apps/web/lib/data/useSupabaseAuth.ts apps/web/lib/data/auth-context.ts
git commit -m "feat(auth): carry Art. 9 consent through both signup paths"
```

### Task 10: The register page

**Files:**
- Modify: `apps/web/app/register/page.tsx`

- [ ] **Step 1: Add the state and the gate**

Add the imports:

```ts
import { canSubmitRegistration } from "@/lib/auth/registration-gate"
import { CONSENT_DOCUMENT_VERSION } from "@/lib/config/consent"
```

Add the state next to `privacyAccepted`:

```ts
  const [healthConsent, setHealthConsent] = useState(false)
```

Add, after the `next` state declaration:

```ts
  const consentsAccepted = canSubmitRegistration({
    terms: termsAccepted,
    privacy: privacyAccepted,
    healthData: healthConsent,
  })
```

- [ ] **Step 2: Gate the submit handler**

Replace the `if (!termsAccepted || !privacyAccepted)` block with:

```ts
    if (!consentsAccepted) {
      setError(tErrors("mustAcceptLegal"))
      return
    }
```

and add the new field to the `register()` call:

```ts
      await register({
        email: submittedEmail,
        password,
        terms_accepted: termsAccepted,
        privacy_accepted: privacyAccepted,
        health_data_consent: healthConsent,
      })
```

- [ ] **Step 3: Pass the consent version to both OAuth handlers**

```ts
  const handleGoogleSignIn = async () => {
    setError(null)
    try {
      await signInWithGoogle(next ?? undefined, CONSENT_DOCUMENT_VERSION)
    } catch (err) {
      const message = err instanceof Error ? err.message : tErrors("generic")
      setError(message)
    }
  }
```

Do the same in `handleAppleSignIn` with `signInWithApple`.

- [ ] **Step 4: Add the third checkbox**

Immediately after the `register-privacy` checkbox block, add:

```tsx
        <div className="flex items-start gap-2 text-left">
          <Checkbox
            id="register-health-consent"
            checked={healthConsent}
            onCheckedChange={(checked) => setHealthConsent(checked === true)}
            disabled={submitting}
            required
          />
          <label
            htmlFor="register-health-consent"
            className="text-sm text-muted-foreground"
          >
            {t("healthConsent")}
          </label>
        </div>
```

Note this label is deliberately **not** a document link: Art. 9 explicit consent is an act, and "I accept the Privacy Policy" is exactly what does not qualify.

- [ ] **Step 5: Gate the submit button on all three**

```tsx
        <Button type="submit" size="lg" disabled={submitting || !consentsAccepted}>
          {submitting ? t("submitting") : t("submit")}
        </Button>
```

- [ ] **Step 6: Gate both OAuth buttons and explain why they are disabled**

In the OAuth block, immediately after the `or` divider, add the hint, then gate both buttons:

```tsx
        <p id="register-oauth-hint" className="text-center text-xs text-muted-foreground">
          {t("consentRequiredHint")}
        </p>
```

and on **each** of the Apple and Google buttons:

```tsx
          disabled={submitting || !consentsAccepted}
          aria-describedby="register-oauth-hint"
```

A disabled control with no stated reason is a WCAG failure; the hint is what makes the gate perceivable.

- [ ] **Step 7: Typecheck**

Run: `npm run build --workspace=apps/web`
Expected: succeeds (the i18n keys resolve at runtime, not build time — Task 12 adds them).

- [ ] **Step 8: Commit**

```bash
git add apps/web/app/register/page.tsx
git commit -m "feat(auth): capture explicit Art. 9 consent at signup, OAuth included"
```

### Task 11: Record the OAuth consent server-side

**Files:**
- Modify: `apps/web/app/auth/callback/route.ts`

- [ ] **Step 1: Read the consent parameter and record it**

After the `if (!user)` guard and **before** the `profiles` select, add:

```ts
  // Art. 9 consent from the OAuth path. The register page put the policy
  // version on the callback URL because no signup metadata survives an OAuth
  // round trip; recording it here rather than client-side means it survives
  // whatever the tab does after the redirect.
  //
  // record_consent is idempotent, so a replayed callback is a no-op, and a
  // failure must never block the sign-in — it is logged loudly instead, because
  // a silently missing consent record is the exact defect this change fixes.
  const consentVersion = searchParams.get("consent")
  if (consentVersion) {
    const { error: consentError } = await supabase.rpc("record_consent", {
      p_kind: "health_data",
      p_document_version: consentVersion,
      p_source: "web_oauth",
    })
    if (consentError) {
      console.error("[auth/callback] record_consent failed:", consentError.message)
    }
  }
```

- [ ] **Step 2: Verify the Supabase redirect allowlist preserves the parameter**

This is the one external unknown in the plan. In the Supabase dashboard, under **Authentication → URL Configuration**, confirm the redirect allowlist entry for `/auth/callback` permits extra query parameters (a wildcard entry such as `https://<site>/auth/callback*`, or the provider preserving `redirect_to` query strings).

Then test it for real: on a preview or production deploy, tick all three boxes, sign up with Google, and check the row landed:

```sql
select kind, document_version, source, granted_at
from public.user_consents
order by created_at desc limit 5;
```

Expected: a row with `source = 'web_oauth'` and `document_version = '1.1.0'`.

**If the parameter is stripped**, stop and report it rather than working around it silently. The fallback is a `localStorage` stash written before the redirect and flushed by a client-side `record_consent` after return — less reliable, and it must be called out in the PR body, not hidden.

- [ ] **Step 3: Typecheck**

Run: `npm run build --workspace=apps/web`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/web/app/auth/callback/route.ts
git commit -m "feat(auth): record Art. 9 consent on the OAuth callback"
```

### Task 12: The copy, EN and FR

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`
- Modify: `apps/web/lib/i18n/messages/fr.json`

**Formatting warning:** these files are formatting-sensitive catalogs. Insert the new keys as text at the right anchor. Do not read-modify-write the whole file with a JSON dumper — it reorders keys and rewrites escaping across the entire file.

- [ ] **Step 1: Add the English keys**

In `en.json`, inside `auth.register`, after `"privacyLink": "Privacy Policy"`:

```json
    "healthConsent": "Pebbles records how you feel: your moods, the emotions you pick, and what you write. That is sensitive data about your mental well-being, and we only process it with your explicit permission.",
    "consentRequiredHint": "Tick all three boxes above to continue."
```

- [ ] **Step 2: Add the French keys**

In `fr.json`, inside `auth.register`, after `"privacyLink": "Politique de Confidentialité"`. Formal *vous*, matching the surrounding `auth.register` strings and the policy itself — the informal *tu* of Lab Notes does not apply here. **No em dashes.**

```json
    "healthConsent": "Pebbles enregistre ce que vous ressentez : vos humeurs, les émotions que vous choisissez et ce que vous écrivez. Ce sont des données sensibles sur votre bien-être mental, et nous ne les traitons qu'avec votre autorisation explicite.",
    "consentRequiredHint": "Cochez les trois cases ci-dessus pour continuer."
```

- [ ] **Step 3: Verify both files still parse and the keys match**

Run:
```bash
cd apps/web && for f in en fr; do node -e "
const d=require('./lib/i18n/messages/$f.json');
const r=d.auth.register;
if(!r.healthConsent||!r.consentRequiredHint) throw new Error('$f: missing key');
console.log('$f ok');
"; done
```
Expected: `en ok` then `fr ok`.

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=apps/web`
Expected: passes.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(auth): add the Art. 9 consent copy in EN and FR"
```

**Part 3 is done.** Run `npm run test --workspace=apps/web` and `npm run lint --workspace=apps/web`, confirm green, and open the PR **with a Lab Note** — this is user-visible.

---

# PART 4 — Withdrawal in Settings

Branch: `feat/777-consent-withdrawal-web`, stacked on Part 3. **`apps/web` only.**

### Task 13: The active-consent selector

**Files:**
- Create: `apps/web/lib/data/consent.ts`
- Create: `apps/web/lib/data/consent.test.ts`

- [ ] **Step 1: Write the failing test**

`apps/web/lib/data/consent.test.ts`:

```ts
import { describe, it, expect } from "vitest"
import { activeConsent, type ConsentRow } from "./consent"

const row = (over: Partial<ConsentRow>): ConsentRow => ({
  id: "r1",
  kind: "health_data",
  document_version: "1.1.0",
  source: "web_register",
  granted_at: "2026-09-11T10:00:00Z",
  withdrawn_at: null,
  superseded_at: null,
  ...over,
})

describe("activeConsent", () => {
  it("returns the live row for the kind", () => {
    expect(activeConsent([row({})], "health_data")?.id).toBe("r1")
  })

  it("returns null when the only row was withdrawn", () => {
    expect(activeConsent([row({ withdrawn_at: "2026-09-12T10:00:00Z" })], "health_data")).toBeNull()
  })

  it("returns null when the only row was superseded", () => {
    expect(activeConsent([row({ superseded_at: "2026-09-12T10:00:00Z" })], "health_data")).toBeNull()
  })

  it("ignores rows of another kind", () => {
    expect(activeConsent([row({ kind: "public_profile" })], "health_data")).toBeNull()
  })

  it("picks the live row out of a full history", () => {
    const history = [
      row({ id: "old", superseded_at: "2026-09-12T10:00:00Z", document_version: "1.0.0" }),
      row({ id: "gone", withdrawn_at: "2026-09-13T10:00:00Z" }),
      row({ id: "live", document_version: "1.2.0" }),
    ]
    expect(activeConsent(history, "health_data")?.id).toBe("live")
  })

  it("returns null for an empty ledger", () => {
    expect(activeConsent([], "health_data")).toBeNull()
  })
})
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `npm run test --workspace=apps/web -- data/consent`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the minimal implementation**

`apps/web/lib/data/consent.ts`:

```ts
import type { ConsentKind, ConsentSource } from "@/lib/config/consent"

/** One consent act, as stored in `public.user_consents`. */
export type ConsentRow = {
  id: string
  kind: ConsentKind
  document_version: string
  source: ConsentSource
  granted_at: string
  /** Set when the user withdrew it. */
  withdrawn_at: string | null
  /** Set when a newer policy version replaced it. Distinct from withdrawal. */
  superseded_at: string | null
}

/**
 * The one live consent of a kind, or null.
 *
 * The database enforces at most one (partial unique index on user + kind where
 * neither timestamp is set), so returning the first match is not a guess.
 */
export function activeConsent(rows: ConsentRow[], kind: ConsentKind): ConsentRow | null {
  return (
    rows.find(
      (r) => r.kind === kind && r.withdrawn_at === null && r.superseded_at === null,
    ) ?? null
  )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test --workspace=apps/web -- data/consent`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/data/consent.ts apps/web/lib/data/consent.test.ts
git commit -m "feat(settings): add the active-consent selector"
```

### Task 14: The consent hook

**Files:**
- Create: `apps/web/lib/data/useConsents.ts`

Follow the shape of the existing hooks in `lib/data/` — read one (for example `useConnections.ts`) first and match its loading/error conventions rather than inventing a new one.

- [ ] **Step 1: Write the hook**

```ts
"use client"

import { useState, useEffect, useCallback } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"
import { activeConsent, type ConsentRow } from "@/lib/data/consent"
import { CONSENT_DOCUMENT_VERSION, type ConsentKind, type ConsentSource } from "@/lib/config/consent"

/**
 * The signed-in user's consent ledger.
 *
 * Reads are a plain owner select (single table, so no RPC needed). Writes go
 * through the two definer RPCs — `user_consents` has no client write policy at
 * all, by design.
 */
export function useConsents() {
  const [rows, setRows] = useState<ConsentRow[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const refresh = useCallback(async () => {
    const supabase = createClient()
    const { data, error } = await withTimeout(
      supabase
        .from("user_consents")
        .select("id, kind, document_version, source, granted_at, withdrawn_at, superseded_at")
        .order("granted_at", { ascending: false }),
      10000,
      "load consents",
    )
    if (error) {
      console.error("[consents] load failed:", error.message)
      setRows([])
    } else {
      setRows((data ?? []) as ConsentRow[])
    }
    setIsLoading(false)
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const record = useCallback(
    async (kind: ConsentKind, source: ConsentSource) => {
      const supabase = createClient()
      const { error } = await withTimeout(
        supabase.rpc("record_consent", {
          p_kind: kind,
          p_document_version: CONSENT_DOCUMENT_VERSION,
          p_source: source,
        }),
        10000,
        "record consent",
      )
      if (error) throw new Error(error.message)
      await refresh()
    },
    [refresh],
  )

  const withdraw = useCallback(
    async (kind: ConsentKind) => {
      const supabase = createClient()
      const { error } = await withTimeout(
        supabase.rpc("withdraw_consent", { p_kind: kind }),
        10000,
        "withdraw consent",
      )
      if (error) throw new Error(error.message)
      await refresh()
    },
    [refresh],
  )

  return {
    rows,
    isLoading,
    healthData: activeConsent(rows, "health_data"),
    publicProfile: activeConsent(rows, "public_profile"),
    record,
    withdraw,
    refresh,
  }
}
```

- [ ] **Step 2: Typecheck**

Run: `npm run build --workspace=apps/web`
Expected: succeeds. If `supabase.rpc("record_consent", ...)` errors as an unknown function name, Part 2's type regeneration did not land — go back and run `db:types:remote`.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/data/useConsents.ts
git commit -m "feat(settings): add the consent ledger hook"
```

### Task 15: The settings consent section

**Files:**
- Create: `apps/web/components/settings/ConsentSection.tsx`

Read `apps/web/components/settings/DeleteAccountSection.tsx` first — this component follows it closely (same `AlertDialog` primitives, for the same reason: the dialog must stay open in a busy state while the edge function runs).

- [ ] **Step 1: Write the component**

```tsx
"use client"

import { useState } from "react"
import { HeartHandshake } from "lucide-react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { Button } from "@/components/ui/button"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import type { ConsentRow } from "@/lib/data/consent"

type ConsentSectionProps = {
  /** The live health-data consent, or null if it was never recorded. */
  consent: ConsentRow | null
  /** Called after the account is deleted and the local session is cleared. */
  onWithdrawn: () => void
}

/**
 * Art. 9 consent, and the Art. 7(3) right to take it back.
 *
 * Withdrawal routes into the existing account-deletion flow rather than a
 * toggle, because this consent is the lawful basis for the product's core
 * activity: there is no version of Pebbles that keeps your journal and stops
 * processing it. The dialog says exactly that. Burying the consequence behind a
 * friendly toggle would be the dark pattern; stating it is the honest version.
 *
 * The public-profile consent is deliberately NOT mirrored here — it is one
 * control, and it lives in PublicProfileSection where the handle it depends on
 * is edited.
 */
export function ConsentSection({ consent, onWithdrawn }: ConsentSectionProps) {
  const { deleteAccount } = useAuth()
  const t = useTranslations("settings.consent")
  const tCommon = useTranslations("common")
  const [open, setOpen] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)

  const handleConfirm = async () => {
    setWithdrawing(true)
    try {
      await deleteAccount()
      onWithdrawn()
    } catch (err) {
      console.error("[settings] consent withdrawal failed:", err)
      toast.error(t("withdrawError"))
      setWithdrawing(false)
      setOpen(false)
    }
  }

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-consent">{t("title")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-consent">
        <SettingsRow
          icon={HeartHandshake}
          trailing={
            consent ? (
              <Button variant="ghost" size="sm" onClick={() => setOpen(true)}>
                {t("withdraw")}
              </Button>
            ) : null
          }
        >
          <span className="flex flex-col text-left">
            <span>{t("healthData")}</span>
            <span className="text-xs text-muted-foreground">
              {consent
                ? t("grantedOn", {
                    date: new Date(consent.granted_at).toLocaleDateString(),
                    version: consent.document_version,
                  })
                : t("notRecorded")}
            </span>
          </span>
        </SettingsRow>
      </SettingsGroup>

      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("confirmBody")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={withdrawing}>{tCommon("cancel")}</AlertDialogCancel>
            <Button variant="destructive" onClick={handleConfirm} disabled={withdrawing}>
              {withdrawing ? t("withdrawing") : t("confirmAction")}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}
```

- [ ] **Step 2: Check the props the shared primitives actually accept**

Run: `grep -n "type SettingsRowProps" -A 20 apps/web/components/settings/SettingsRow.tsx`

`SettingsRow` must accept `icon`, `trailing` and children with no `href`/`onClick`. If it requires one of those, adapt the call — do not change `SettingsRow`, it is shared by every other section.

- [ ] **Step 3: Typecheck**

Run: `npm run build --workspace=apps/web`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/settings/ConsentSection.tsx
git commit -m "feat(settings): add the Art. 9 consent withdrawal surface"
```

### Task 16: Wire it up and reroute the public toggle

**Files:**
- Modify: `apps/web/app/settings/page.tsx`
- Modify: `apps/web/components/settings/PublicProfileSection.tsx`

- [ ] **Step 1: Mount `ConsentSection`**

In `apps/web/app/settings/page.tsx`, add the imports:

```ts
import { ConsentSection } from "@/components/settings/ConsentSection"
import { useConsents } from "@/lib/data/useConsents"
```

Add inside the component, next to the other hooks:

```ts
  const { healthData: healthConsent, record: recordConsent, withdraw: withdrawConsent } = useConsents()
```

and render it between `LegalSection` and `AppearanceSection`:

```tsx
          <LegalSection />
          <ConsentSection consent={healthConsent} onWithdrawn={() => router.push("/")} />
          <AppearanceSection />
```

- [ ] **Step 2: Route the public-profile toggle through the consent RPCs**

In the save handler, replace the line that stages the toggle:

```ts
      if (publicChanged && effectiveHandle) updates.public_profile = isPublic
```

with:

```ts
      // The public-profile flag IS the consent to publish, so it is written by
      // the consent RPCs rather than as a profile field: withdraw_consent is
      // what flips the flag back off, in the same transaction as the ledger
      // row. A consent record the backend does not honour is the defect this
      // whole change exists to fix.
      if (publicChanged && effectiveHandle) {
        if (isPublic) {
          await recordConsent("public_profile", "web_settings")
          updates.public_profile = true
        } else {
          await withdrawConsent("public_profile")
        }
      }
```

Note `withdrawConsent` already sets `profiles.public_profile = false` server-side, so the `updates` field is deliberately not set on that branch.

- [ ] **Step 3: Name the toggle as consent**

In `apps/web/components/settings/PublicProfileSection.tsx`, the toggle currently
uses `t("publicToggle")` twice (line 128 `aria-label`, line 133 the visible
label). Change **both** to `t("publicProfileConsent")`, and add the supporting
line immediately after the label:

```tsx
            <span className="text-xs text-muted-foreground">
              {t("publicProfileConsentHint")}
            </span>
```

Leave `publicToggleNeedsHandle` (line 137) alone — it is a different message,
about the handle prerequisite, not about consent.

- [ ] **Step 4: Add the copy, EN and FR**

Insert-as-text at the anchor, never a whole-file JSON rewrite.

In `en.json`, inside `settings`, add a `consent` object and the two public-profile keys:

```json
    "consent": {
      "title": "Consent",
      "healthData": "Recording how you feel",
      "grantedOn": "Given on {date} (policy version {version})",
      "notRecorded": "No record on file",
      "withdraw": "Withdraw",
      "withdrawing": "Withdrawing…",
      "withdrawError": "Could not withdraw your consent. Please try again.",
      "confirmTitle": "Withdraw your consent?",
      "confirmBody": "Pebbles cannot work without permission to record how you feel, so withdrawing closes your account and permanently erases your pebbles, photos and connections. Everything processed until now stays lawful. This cannot be undone.",
      "confirmAction": "Withdraw and delete"
    },
    "publicProfileConsent": "Let anyone see my public profile",
    "publicProfileConsentHint": "This is a separate permission. Turn it off any time and your profile goes private again.",
```

In `fr.json`, same anchor, formal *vous*, **no em dashes**:

```json
    "consent": {
      "title": "Consentement",
      "healthData": "Enregistrer ce que vous ressentez",
      "grantedOn": "Donné le {date} (version {version} de la politique)",
      "notRecorded": "Aucun enregistrement",
      "withdraw": "Retirer",
      "withdrawing": "Retrait en cours…",
      "withdrawError": "Impossible de retirer votre consentement. Veuillez réessayer.",
      "confirmTitle": "Retirer votre consentement ?",
      "confirmBody": "Pebbles ne peut pas fonctionner sans l'autorisation d'enregistrer ce que vous ressentez. Le retrait ferme donc votre compte et efface définitivement vos pebbles, vos photos et vos connexions. Tout ce qui a été traité jusqu'ici reste licite. Cette action est irréversible.",
      "confirmAction": "Retirer et supprimer"
    },
    "publicProfileConsent": "Autoriser tout le monde à voir mon profil public",
    "publicProfileConsentHint": "C'est une autorisation distincte. Désactivez-la quand vous voulez et votre profil redevient privé.",
```

- [ ] **Step 5: Verify both catalogs parse**

Run:
```bash
cd apps/web && for f in en fr; do node -e "
const d=require('./lib/i18n/messages/$f.json');
if(!d.settings.consent.confirmBody) throw new Error('$f: missing consent copy');
if(!d.settings.publicProfileConsent) throw new Error('$f: missing toggle copy');
console.log('$f ok');
"; done
```
Expected: `en ok` then `fr ok`.

- [ ] **Step 6: Full workspace verification**

Run: `npm run lint --workspace=apps/web && npm run test --workspace=apps/web && npm run build --workspace=apps/web`
Expected: all three pass.

- [ ] **Step 7: Commit**

```bash
git add apps/web/app/settings/page.tsx apps/web/components/settings/PublicProfileSection.tsx apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(settings): honour consent withdrawal in the backend"
```

**Part 4 is done.** Open the PR **with a Lab Note**.

---

# PART 5 — The policy text

Branch: `fix/778-privacy-art9-wording`, stacked on Part 4.

> **Every wording block in this part is DRAFT and requires maintainer sign-off
> before merge.** Published legal text on a user-facing route is not revertible
> the way config is.

### Task 17: Correct the consent and withdrawal claims

**Files:**
- Modify: `apps/web/docs/privacy/en.md`
- Modify: `apps/web/docs/privacy/fr.md`

These are formatting-sensitive published documents. Edit the specific sections as text; do not reformat the file.

- [ ] **Step 1: Correct §3.2 and §4.2 in `en.md`**

In §3.2, replace `which you provide during onboarding` with `which you give when you create your account`.

In §4.2, replace `On the basis of your explicit consent, obtained during onboarding;` with `On the basis of your explicit consent, given when you create your account;`.

- [ ] **Step 2: Rewrite §4.3 in `en.md`**

Replace the whole of §4.3 with:

```markdown
### 4.3 Right to Withdraw Consent

You may withdraw your consent at any time from Settings.

Pebbles cannot operate without permission to record how you feel: that
processing is the service. Withdrawing your consent therefore closes your
account and permanently erases your pebbles, photos and connections.
Withdrawal does not render prior processing unlawful.

Permission to publish your public profile is separate. You can turn it off at
any time from Settings without closing your account; your profile becomes
private again immediately.
```

- [ ] **Step 3: Apply the same three edits to `fr.md`**

§3.2 (line ~81): replace `que vous accordez lors de l'onboarding` with `que vous accordez lors de la création de votre compte`.

§4.2 (line ~108): replace `obtenu lors de l'onboarding` with `obtenu lors de la création de votre compte`.

§4.3 (line ~114): replace the section with:

```markdown
### 4.3 Droit de retrait du consentement

Vous pouvez retirer votre consentement à tout moment depuis les Réglages.

Pebbles ne peut pas fonctionner sans l'autorisation d'enregistrer ce que vous
ressentez : ce traitement constitue le service lui-même. Le retrait de votre
consentement ferme donc votre compte et efface définitivement vos pebbles, vos
photos et vos connexions. Le retrait ne rend pas le traitement antérieur
illicite.

L'autorisation de publier votre profil public est distincte. Vous pouvez la
désactiver à tout moment depuis les Réglages sans fermer votre compte ; votre
profil redevient immédiatement privé.
```

- [ ] **Step 4: Bump both frontmatters**

In **both** files, change `version: 1.1.0` to `version: 1.2.0` and `last_updated:` to the merge date. Leave `effective_date` for the maintainer to decide.

- [ ] **Step 5: Bump the constant in the same commit**

In `apps/web/lib/config/consent.ts`:

```ts
export const CONSENT_DOCUMENT_VERSION = "1.2.0"
```

Consents already recorded against `1.1.0` are **correct and must not be migrated** — those people consented to that text. The ledger's `document_version` column exists to hold exactly this distinction, and M55's re-consent surface is what re-asks the earlier cohort.

- [ ] **Step 6: Verify the binding test catches the pair**

Run: `npm run test --workspace=apps/web -- consent`
Expected: PASS. If it fails, one of the three (en.md, fr.md, the constant) was missed — that failure is the test doing its job.

- [ ] **Step 7: Verify the "during onboarding" claim is gone**

Run: `grep -rn "during onboarding\|lors de l'onboarding" apps/web/docs/privacy/`
Expected: no output.

- [ ] **Step 8: Full verification**

Run: `npm run lint --workspace=apps/web && npm run test --workspace=apps/web && npm run build --workspace=apps/web`
Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add apps/web/docs/privacy/en.md apps/web/docs/privacy/fr.md apps/web/lib/config/consent.ts
git commit -m "fix(legal): correct the Art. 9 consent and withdrawal claims"
```

**Part 5 is done.** Open the PR **with a Lab Note**, flagged for maintainer review of the legal text before merge.

---

## After the stack merges

- [ ] **Append one entry to `docs/decisions/log.md`** — Art. 9 consent is a version-bound ledger (`user_consents`), not a profile flag; withdrawal of the core consent is account deletion; `withdrawn_at` and `superseded_at` stay distinct. Supersede-don't-edit.
- [ ] **Move the touched Arkaik nodes to `releasing`** via `arkaik-mcp` (`update_node`). If the MCP tools are unavailable, say so and stop — never fall back to editing `docs/arkaik/bundle.json`.
- [ ] **Resolve the finding** with `kritik_resolve_finding(finding_id: "F-2026-08-GDP-web-01", resolved_by: <PR URL>)`, and state in the PR that it resolves the gap **for new web accounts** — existing and pre-existing OAuth accounts are M55 re-consent work, accepted by the maintainer on 2026-09-11 on the grounds that Pebbles is in beta.
- [ ] **Open the follow-on issues** from spec §7: the M55 re-consent surface, Art. 20 data export, and the iOS/Android/supabase/admin instances of GDP-02.
