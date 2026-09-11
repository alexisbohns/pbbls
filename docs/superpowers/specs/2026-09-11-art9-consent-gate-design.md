# The Art. 9 explicit-consent gate, and the DPIA behind it — design

**Date:** 2026-09-11
**Finding:** Kritik `F-2026-08-GDP-web-01` (criterion GDP-02, surface `web`, severity **high / P1**, cost L, impact 4 × likelihood 4)
**Milestone:** M55 · Compliance batch A
**Status:** design only. No code, schema, policy or configuration is changed by this document.

> **This spec drafts a DPIA and published legal text. Every wording block is
> marked DRAFT and requires maintainer sign-off before it merges; the DPIA's
> validation section is deliberately left unfilled.** The author is an
> engineering agent, not counsel, and cannot sign an impact assessment. Nothing
> here is a legal opinion: every factual claim is grounded in a file in this
> repo or flagged as an open question.

## Context

The controller's own privacy policy qualifies moods, emotion labels and CBT-style
reflections as Article 9 special-category health data, and states they are
processed **only with explicit consent "which you provide during onboarding."**
No such consent step exists. That is the whole finding: the declared lawful
basis for the product's central processing is not evidenced by anything the
system collects, which is precisely the accountability failure Art. 5(2), 7(1)
and 9(2)(a) target.

The precedent for how a Kritik compliance finding lands here is
`F-2026-08-GDP-admin-01` (the Vercel processor inventory, spec 2026-09-03,
PRs #765/#767): verify at HEAD, split config/code from published legal text,
mark the legal text DRAFT. This one is larger — it adds a schema contract and
touches two user-facing surfaces — so it ships as a stack rather than a pair.

### This is one defect with five findings

`GDP-02` is open on five surfaces. All five cite the missing DPIA, which is a
single shared artifact:

| Finding | Surface | Priority |
|---|---|---|
| `F-2026-08-GDP-web-01` | web | P1 |
| `F-2026-08-GDP-android-02` | android | P1 |
| `F-2026-08-GDP-ios-04` | ios | P2 |
| `F-2026-08-GDP-supabase-02` | supabase | P2 |
| `F-2026-08-GDP-admin-06` | admin | P2 |

**Scope of this stack: the DPIA (which addresses the documentary half of all
five) and the consent gate on web only.** The other four findings stay open,
each with a follow-on issue. See §7.

## 1. Verification at HEAD (`main`, clean, 2026-09-11)

Every citation in the finding was re-read at the real root. **Verdict: the
finding is fully valid**, with one citation drifted and two aggravating facts
the audit did not record.

### 1.1 Citation by citation

| Claim | Status at HEAD |
|---|---|
| Policy declares Art. 9 + consent "during onboarding" | **Confirmed.** `apps/web/docs/privacy/en.md` §3.2 and §4.2; `fr.md:81` and `:108` say the same in French. |
| Onboarding has no consent step | **Confirmed.** `lib/config/onboarding-steps.ts` is exactly `path`, `pebble`, `ritual`. |
| Signup offers only generic checkboxes | **Confirmed.** `app/register/page.tsx` renders `register-terms` and `register-privacy`, both linking to documents, neither naming sensitive data. |
| OAuth accounts get no consent record | **Confirmed.** The Apple and Google buttons are `disabled={submitting}` only, and `useSupabaseAuth.signInWithApple` / `signInWithGoogle` pass no metadata. Migration `20260729120000_handle_new_user_consent.sql` says so in its own comment: "OAuth signups carry no consent metadata." |
| No DPIA anywhere | **Confirmed.** Greps for DPIA / AIPD / impact assessment hit only the Kritik framework files. |
| Line numbers in the evidence string | **Drifted.** The policy has been rewritten since the audit — it is now `version: 1.1.0`, effective 2026-09-04 (PR #767). The claims moved lines; none of them went away. |

### 1.2 Three facts the audit did not have

1. **The policy version moved.** `en.md` and `fr.md` are both at `1.1.0` /
   2026-09-04. Any consent recorded before this stack's Part 5 lands is consent
   against **1.1.0**, and must be recorded as such. See §5.3.
2. **`profiles.terms_accepted_at` / `privacy_accepted_at` are write-only.** They
   are populated by `handle_new_user` and read by nothing. Even the generic
   consent record has no operational effect today, and it is not bound to a
   document version.
3. **Account deletion already exists and works** (`purge_account` +
   the delete-account edge function + `DeleteAccountSection`), but **data export
   does not**. That makes deletion a usable withdrawal path and makes Art. 20
   portability an open gap — named honestly in the DPIA rather than omitted.

### 1.3 What is not wrong, and should not be "fixed"

- **The public-profile toggle already defaults off** and is a real opt-in. It is
  reframed as consent, not replaced.
- **`handle_new_user`'s NULL-safe `->>` casts are correct.** The bug is that
  nothing supplies the metadata on the OAuth path, not the casting.
- **No telemetry SDKs reach sensitive fields.** The defect is documentary and
  structural, not a leak. The DPIA should say so.

## 2. Design decisions

| # | Decision | Rejected alternative |
|---|---|---|
| D1 | Consent is captured by a **third checkbox on `/register`**, gating the email submit **and both OAuth buttons**. | A blocking post-auth gate before onboarding. It would have covered existing accounts and made the policy's "during onboarding" wording true, at the cost of a new interstitial on every path. Deferred to M55's re-consent surface. |
| D2 | Core consent is **required**; withdrawing it routes to account deletion, stated plainly. | A read-only "frozen" account. Retaining Art. 9 data after withdrawal is arguably worse than erasing it, and it needs a server-side write gate on every path. |
| D3 | The **public-profile toggle is reframed** as a separately withdrawable enlargement consent, in place. | Layered granular consents for sharing, connections and photos. More defensible, but adds gates on several write paths and needs a full policy rewrite. |
| D4 | Consent is a **ledger of acts** bound to a document version, in `user_consents`. | Two more nullable timestamps on `profiles`. No history, no version binding, and withdrawal and re-consent would overwrite each other. |
| D5 | The policy's **"during onboarding" wording is corrected** to match where consent is actually taken. | Leaving it. The first line of the finding's evidence would survive the fix. |
| D6 | The DPIA ships **unsigned and undated**, for the maintainer to validate. | Dating it myself. An engineering agent cannot sign a controller's impact assessment. |

## 3. Part split

One stack, five parts, ordered by dependency. Each lints and typechecks on its
own. Part 1 is independent; 2 → 3 → 4 → 5 is a strict chain.

| Part | Branch | Contents | Lab Note |
|---|---|---|---|
| 1 | `docs/NNN-dpia` | `docs/compliance/dpia.md` + `docs/compliance/lawful-basis-map.md` | no |
| 2 | `feat/NNN-consent-contract` | `user_consents` + RLS + RPCs + `handle_new_user` + `purge_account` + purge harness + `database.ts` | no |
| 3 | `feat/NNN-consent-capture-web` | `/register` checkbox, OAuth gating, callback flush, EN/FR | yes |
| 4 | `feat/NNN-consent-withdrawal-web` | Settings `ConsentSection`, public-profile toggle reframe, EN/FR | yes |
| 5 | `fix/NNN-privacy-art9-wording` | `apps/web/docs/privacy/{en,fr}.md` §3.2/§4.2/§4.3, version bump | yes |

Part 5 lands **after** 2–4 so the policy is true at the moment it merges.
`NNN` is the issue number from §11. **Part 2 owns every database change in this
stack**, so Parts 3 and 4 touch `apps/web` only.

## 4. The consent contract (Part 2)

### 4.1 Table

```sql
create table public.user_consents (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users(id),
  kind             text not null check (kind in ('health_data', 'public_profile')),
  document_version text not null,
  source           text not null check (source in ('web_register','web_oauth','web_settings')),
  granted_at       timestamptz not null default now(),
  withdrawn_at     timestamptz,
  created_at       timestamptz not null default now()
);

create unique index user_consents_active_kind
  on public.user_consents (user_id, kind) where withdrawn_at is null;

create index user_consents_user_id on public.user_consents (user_id);
```

Granting inserts a row. Withdrawing stamps `withdrawn_at` on the active row.
Re-consenting inserts a new row. The partial unique index makes "at most one
active consent per kind" a database invariant rather than a convention.

The FK carries **no `on delete cascade`**, matching `profiles` — the purge stays
explicit and the harness assertion stays meaningful.

### 4.2 RLS and the write path

**Owner `select` only. No insert, update or delete policies exist**, so a
consent record can never land through a client write path. This mirrors the
structural choice the roadmap makes for whispers (M53: "no insert/update
policies, so plaintext can never land via a client write path").

Two `security definer` RPCs, both keyed on `auth.uid()`:

- `record_consent(p_kind text, p_document_version text, p_source text)` —
  idempotent: no-ops when an active row of that kind at that version already
  exists, so a replayed OAuth callback cannot double-write.
- `withdraw_consent(p_kind text)` — stamps `withdrawn_at`, and for
  `public_profile` flips `profiles.public_profile` false **in the same
  transaction**. Multi-table, therefore an RPC (AGENTS.md).

Reading current state is a single-table owner `select`, so the settings page
reads `user_consents` directly. No RPC needed.

### 4.3 Document-version binding

`apps/web/lib/config/consent.ts` exports the version the UI records:

```ts
/** Privacy-policy version the consent copy on /register describes. */
export const CONSENT_DOCUMENT_VERSION = "1.1.0"
```

A Vitest case asserts it equals the `version:` frontmatter of
`apps/web/docs/privacy/en.md`. That test is the only thing standing between the
constant and silent drift from the document it claims to bind.

### 4.4 Obligations this part carries

Three standing rules apply, and all three are satisfied inside Part 2:

1. **`purge_account` gains** `delete from public.user_consents where user_id = p_user_id;`.
   That function uses in-body append markers, so the new body is **diffed
   pairwise against the previous emission** before the migration is applied —
   `create or replace` has no merge semantics and git reports no conflict.
   `handle_new_user` is re-emitted in this part too (to insert the consent row
   from signup metadata, see §5.1) and gets the same treatment.
2. **`verify-account-purge.ts` gains a seeded consent row and its zero-row
   assertion in the same change**, and `npm run db:verify:purge --workspace=packages/supabase`
   is run against the linked project. CI does not run that harness.
3. **Types are regenerated with `npm run db:types:remote`.** The plain
   `db:types` targets `--local` and truncates `database.ts` when Docker is absent.

This is a database contract change. iOS and Android do not call these RPCs yet,
so nothing breaks — but the table and both RPCs are deliberately **surface
neutral**, not web shaped, because the other surfaces' findings will need them.

## 5. The web surfaces

### 5.1 Part 3 — capture at signup

A third checkbox on `/register`, worded as an act rather than a document link,
because "I accept the Privacy Policy" is exactly what Art. 9 says is not enough.

**DRAFT copy, EN:**

> Pebbles records how you feel: your moods, the emotions you pick, and what you
> write. That is sensitive data about your mental well-being, and we only
> process it with your explicit permission.

**DRAFT copy, FR** (formal *vous*, matching the existing `auth.register` strings
and the policy itself):

> Pebbles enregistre ce que vous ressentez : vos humeurs, les émotions que vous
> choisissez et ce que vous écrivez. Ce sont des données sensibles sur votre
> bien-être mental, et nous ne les traitons qu'avec votre autorisation explicite.

Submit gates on all three checkboxes. **Both OAuth buttons gate on all three
too** — that single change is what closes the "OAuth accounts get no consent
record at all" half of the finding. A disabled button with no stated reason is a
WCAG failure, so a persistent hint line sits above them and both buttons carry
`aria-describedby` pointing at it.

**Two write paths, each the reliable one for its flow, converging on one table:**

- **Email.** `health_data_consent_at` and the document version ride the existing
  `signUp` metadata, and `handle_new_user` inserts the `user_consents` row.
  Reuses the proven mechanism, and keeps working if M55 enables email
  confirmations — there is no session at signup then, so a client-side RPC call
  would simply be lost.
- **OAuth.** `buildCallbackUrl()` appends `&consent=<version>`, and
  `app/auth/callback/route.ts` calls `record_consent(..., 'web_oauth')` after
  `exchangeCodeForSession` succeeds. Server-side, so it survives whatever the
  tab does next. The parameter is not an attack surface: it can only record
  consent for whoever completes the code exchange, which is that person.

**Verification required before this part is committed to:** the Supabase
redirect-URL allowlist must preserve the extra query parameter. If it strips it,
the fallback is a `localStorage` stash flushed client-side after return — less
reliable, and the PR says so rather than hiding it.

### 5.2 Part 4 — withdrawal in Settings

A new `components/settings/ConsentSection.tsx`, placed above `LegalSection`,
holding **one** row: the core consent, showing when it was given and against
which policy version, with a Withdraw action.

The dialog states plainly that Pebbles cannot function without this processing
and that withdrawing closes the account and erases everything in it, then routes
into the existing `deleteAccount()` flow. Stating the consequence is the honest
version; burying it would be the dark pattern.

The public-profile consent is **not** duplicated into that section — two
controls for one thing is worse than one. The existing toggle in
`PublicProfileSection` stays where it is, gets copy naming it as consent, and
its save handler calls `record_consent` / `withdraw_consent` instead of writing
`public_profile` directly. It keeps the page's staged-save model: nothing writes
until the page-level Save runs.

### 5.3 Part 5 — policy text (DRAFT, requires review)

`apps/web/docs/privacy/{en,fr}.md`:

- §3.2 and §4.2: "which you provide during onboarding" / "obtenu lors de
  l'onboarding" → "when you create your account" / "lorsque vous créez votre
  compte".
- §4.3: replace "withdraw at any time via your profile" with what actually
  happens — withdraw in Settings, and because Pebbles cannot operate without
  this processing, withdrawing closes the account and erases the data; prior
  processing remains lawful.
- Add the public-profile consent as a separately withdrawable consent.
- Frontmatter: `version: 1.1.0` → `1.2.0`, `last_updated` to the merge date.

**The version sequencing looks like a bug and is not.** Parts 3 and 4 ship while
the live policy still reads `1.1.0`, so the first accounts record
`document_version: '1.1.0'` — which is correct, because that is the text those
people actually saw. Part 5 then bumps to `1.2.0`, `CONSENT_DOCUMENT_VERSION`
moves with it, and later consents record the new version. The ledger's
`document_version` column exists precisely to hold that distinction, and M55's
re-consent surface is what re-asks the earlier cohort against the new text.

## 6. The DPIA (Part 1)

`docs/compliance/dpia.md`, following the CNIL PIA structure so it is
recognisable to the regulator that would ask for it.

1. **Context** — controller identity; **no DPO** (stated, not omitted); a
   systematic description of the processing; and a data inventory built from the
   actual schema (`pebbles.intensity` / `positiveness` / `emotion_id` /
   `description`, photos, souls, connections, drafts, marks, karma,
   achievements, glyphs, profiles). Recipients and processors, including Vercel
   as named by PR #767 and the `cdg1` pinning from PR #765.
2. **Necessity and proportionality** — minimisation, retention, and data-subject
   rights, cross-referenced to `docs/compliance/lawful-basis-map.md`. This
   section names a real gap: **there is no data export, so Art. 20 portability
   is unmet.** Better the DPIA says it than a regulator finds it.
3. **Risks** — the three CNIL feared events (illegitimate access, unwanted
   modification, disappearance), plus the Pebbles-specific ones:
   re-identification through public profiles; the **souls problem** (people named
   in another person's pebbles never consented to appearing there); and the admin
   dashboard's missing minimum-cohort threshold, cross-referenced to
   `F-2026-08-GDP-admin-06`.
4. **Measures** — shipped (RLS, `security definer` RPC projections, no telemetry
   SDKs, account deletion, EU region pinning) versus planned (this stack's
   consent gate, M55 re-consent, export, cohort thresholds).
5. **Validation** — **left blank for the maintainer.** Ships as `status: draft`
   with date and reviewer unfilled.

`docs/compliance/lawful-basis-map.md` is a separate living table: one row per
processing operation → Art. 6 basis → Art. 9 condition where applicable → where
the consent record lives. Separate from the DPIA so a new feature can update the
map without re-dating the assessment. That table is what makes the policy's
claims checkable instead of asserted.

**On the PR-checklist rule.** `F-2026-08-GDP-ios-04` asks for a rule that
migrations touching emotion columns must touch the DPIA. That is a CLAUDE.md
edit, and CLAUDE.md forbids per-PR edits for learnings — promotion happens at the
audit grooming pass. So the trigger is written into the DPIA's own "when to
revisit" section now, and promoted to a repo rule at the next milestone boundary.

## 7. What this stack does not fix

Each becomes a follow-on issue, and the resolution note on
`F-2026-08-GDP-web-01` says so plainly rather than claiming more:

| Gap | Why it is out of scope | Where it goes |
|---|---|---|
| Every existing account, and every current OAuth account, has no Art. 9 consent record | A `/register` checkbox cannot reach an account that already exists (D1) | M55 re-consent surface |
| iOS consent capture | Separate codebase, separate finding | `F-2026-08-GDP-ios-04` |
| Android consent capture | Separate codebase, separate finding | `F-2026-08-GDP-android-02` |
| Sensitive-column inventory / CI sink guard | Schema annotation work, separate finding | `F-2026-08-GDP-supabase-02` |
| Analytics minimum-cohort threshold | Admin surface, separate finding | `F-2026-08-GDP-admin-06` |
| Art. 20 data export | Not raised by this finding; named as a gap in the DPIA | new issue |

**So: this stack resolves `F-2026-08-GDP-web-01` for new web accounts, and
resolves the DPIA half of all five findings.** The web finding's own resolution
therefore depends on accepting the existing-cohort gap as M55 work.

## 8. Testing

| Layer | Test |
|---|---|
| Version binding | Vitest: `CONSENT_DOCUMENT_VERSION` equals the `version:` frontmatter in `apps/web/docs/privacy/en.md` |
| Signup gating | Vitest on a pure `canSubmitRegistration({ terms, privacy, health })`, extracted from the component so the gate is unit-tested rather than inferred |
| Consent state | Vitest on a pure `activeConsent(rows, kind)` selector |
| Purge contract | `verify-account-purge.ts` seed + zero-row assertion, run manually against the linked project |
| Scope | Workspace-scoped lint, test and build on `apps/web` and `packages/supabase` per part |

## 9. Risks

| Risk | Mitigation |
|---|---|
| Supabase strips the `consent` query param from the OAuth redirect | Verify in the dashboard config before writing Part 3; documented `localStorage` fallback |
| A `purge_account` / `handle_new_user` re-emission silently drops a prior append | Pairwise body diff before applying, per the standing rule |
| The DPIA is drafted by a non-lawyer | Ships unsigned and undated; maintainer validates (D6) |
| Published policy text is not revertible the way config is | Every wording block marked DRAFT; Part 5 merges last and separately |
| Withdrawal-as-deletion read as coercive by a regulator | Documented reasoning in the DPIA; the granular alternative (D3) is recorded as the escalation path |

## 10. Decision-log entry

One entry on merge: **Art. 9 consent is recorded as a version-bound ledger
(`user_consents`), not a profile flag, and withdrawal of the core consent is
account deletion.** Significance bar cleared — a future agent reaching for two
more timestamp columns on `profiles`, or for a freeze semantic, would otherwise
re-litigate both.

## 11. Proposed issues

| # | Title | Species | Scope | Milestone |
|---|---|---|---|---|
| 1 | `[Docs] Write the DPIA and lawful-basis map for the current feature set` | `docs` | `legal` | M55 |
| 2 | `[Feat] Record Art. 9 consent as a version-bound ledger` | `feat` | `db`, `supabase` | M55 |
| 3 | `[Feat] Capture explicit Art. 9 consent at web signup, OAuth included` | `feat` | `auth`, `web` | M55 |
| 4 | `[Feat] Consent withdrawal surface in web settings` | `feat` | `auth`, `web` | M55 |
| 5 | `[Fix] Correct the privacy policy's Art. 9 consent and withdrawal claims` | `fix` | `legal`, `web` | M55 |
