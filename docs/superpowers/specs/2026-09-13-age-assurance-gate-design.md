# The age assurance gate — design

**Date:** 2026-09-13
**Finding:** Kritik `F-2026-08-SAF-supabase-01` (criterion SAF-06, surface `supabase`, severity **high / P1**, cost XL, impact 4 × likelihood 4)
**Milestone:** M55 · Compliance batch A
**Status:** design only. No code, schema, policy or configuration is changed by this document.

> **This spec amends published legal text. Every wording block is marked DRAFT
> and requires maintainer sign-off before it merges.** The author is an
> engineering agent, not counsel. Nothing here is a legal opinion: every factual
> claim is grounded in a file in this repo or flagged as an open question.

## Context

Pebbles publishes a minimum age of 13 and promises, in the privacy policy, to
confirm parental consent "during sign-up" for 13–14 year olds. Neither is
enforced anywhere. No surface asks for an age, no column stores one, and no
record of an age basis exists for any account. Any child can create an account
with an email or a federated login and immediately record intimate emotional
content and use social features.

That is two defects in one, and they have different characters:

1. **No age basis is captured.** The Art. 8 consent lawful basis is
   undemonstrable — an Art. 5(2) accountability failure, and a store
   target-audience exposure at the store-launch milestone.
2. **The published policy describes a control that does not exist.** Privacy
   §10.2's "we ask you to confirm this consent during sign-up" is a
   documented misrepresentation, independent of (1).

The precedent for how a Kritik compliance finding lands here is the Art. 9
consent gate (spec `2026-09-11-art9-consent-gate-design.md`, PRs #775/#788):
verify at HEAD, reuse the ledger, split config/code from published legal text,
mark the legal text DRAFT, ship as a stack, leave the other client surfaces to
follow-on issues.

## 1. Verification at HEAD (`main`, clean, 2026-09-13)

Every citation in the finding was re-read at the real root. **Verdict: the
finding is fully valid.**

| Claim | Status at HEAD |
|---|---|
| No age/birth/dob field in migrations or `types/database.ts` | **Confirmed.** |
| No age field on any client surface | **Confirmed.** `app/register/page.tsx` renders exactly three acceptances: terms, privacy, health-data consent — none age-related. |
| `handle_new_user` records only terms/privacy timestamps | **Confirmed**, though drifted: it was re-emitted at `20260911090000:155-200` and now also inserts the Art. 9 ledger row. It still captures nothing age-related. |
| `enable_signup = true` | **Confirmed.** |
| `public_profile` default false is not age-derived | **Confirmed.** A privacy opt-in, unrelated. |
| Policy declares 13+ with France parental-consent rules | **Confirmed.** `apps/web/docs/terms/en.md:87` / `fr.md:83`; `privacy/en.md:291-312` / `fr.md:265-286`. |

**Aggravating fact the audit recorded and this spec upholds:** privacy §10.2
(`en.md:297-299`, `fr.md:271-273`) promises parental-consent confirmation during
sign-up. No surface asks it.

## 2. Decisions taken

Three product decisions were settled before design, and they collapse most of
the audit's assumed XL scope:

### 2.1 The enforced minimum is 16, and no minors are admitted

Rather than the currently published 13 (with a 13–14 parental-consent band), the
product enforces the GDPR Art. 8 ceiling. **Consequence: there is no minors
population, therefore no minors-default settings matrix**, no parental-consent
flow, no age-banded feature behaviour, and no ongoing obligation for every
future social feature to respect a minors state. This is the single largest
scope reduction against the finding's remediation text, which assumed minors
would be admitted.

It costs the 15-year-olds French law would let consent independently. That is
accepted deliberately: an intimate emotional product with social features and
public profiles is not where this codebase should be carrying a minors
population.

### 2.2 The record is an attestation, not a birthdate

No birthdate is collected or stored. A single "I am 16 or older" acceptance is
recorded as an act in the existing consent ledger.

**This is attestation, not verification, and the spec says so plainly.** A
checkbox is the weakest recognised form of age assurance; a regulator or a store
asking for genuine *verification* would not be satisfied by it, and this is the
layer that would be replaced in that event. What it does achieve, and what the
finding actually requires, is that both defects in §Context close: an age basis
is server-recorded against a document version at the moment of signup, and the
published documents stop describing a control the product does not have.

It is also explicitly within the finding's own remediation text: *"birthdate
**or over-threshold attestation** persisted... refusing under-threshold
accounts."*

Data-minimisation is the other half of the argument: a birthdate would be a new
piece of personal data on every account, needing RLS treatment, purge coverage
and a minimisation justification, for a value the product never otherwise reads.

### 2.3 Scope of this stack: schema + legal text + web

iOS and Android get follow-on issues, as the Art. 9 gate did. The schema and the
legal text are shared artifacts and close their halves on every surface at once.

## 3. Storage: reuse the ledger, do not add a table

`user_consents` (`20260911090000`, hardened `20260911090060`) already provides
everything this needs:

- owner-select-only RLS with **no client write path** — the absence of an
  insert policy is the guard, and every write goes through a definer RPC that
  stamps `auth.uid()` itself;
- a partial unique index enforcing at most one *active* act per `(user, kind)`;
- a surface-neutral `source` CHECK already listing all nine
  `{web,ios,android}_{register,oauth,settings}` values, widened at `090060` §1
  precisely so adding a surface stays a client change;
- purge coverage (`20260911090100`), with a zero-row assertion already in
  `verify-account-purge.ts`.

An age attestation is the same kind of artifact as the acts already in that
table: a thing the user asserted, at a time, against a document version. It
gets all of the above for free. A second accountability table would duplicate
the machinery and split the evidence across two places.

### 3.1 Semantics of the two lifecycle columns

`user_consents` distinguishes `withdrawn_at` ("the user withdrew") from
`superseded_at` ("a newer document replaced this"). For `age_assurance`:

- **`superseded_at` applies and is correct.** A terms-version bump supersedes
  the attestation and the user re-attests against the new document, which is
  exactly the existing `record_consent` behaviour and needs no special casing.
- **`withdrawn_at` must never apply.** You cannot un-attest your age. Left
  unguarded, `withdraw_consent('age_assurance')` would let a user null out
  their own age basis through a normal authenticated RPC call — the ledger
  would show an account with no active age record and no way to tell that from
  one that never had one. **§4.3 guards it.** The only exit from an attestation
  is account deletion, which purges the row.

## 4. The migration

One migration, three moves. `handle_new_user` is the only function re-emitted
in this batch, so the standing CLAUDE.md rule about two migrations silently
dropping each other's appends is satisfied without a pairwise merge.

### 4.1 Widen the `kind` CHECK

Add `age_assurance` to the `kind` allowlist. The constraint carries a generated
name, so it is **discovered by definition, not assumed** — the pattern
established at `090060` §1, which exists so that a wrong assumption stops loudly
rather than leaving the narrow CHECK in force alongside a new wide one.

### 4.2 Widen `record_consent`'s `p_kind` allowlist

`record_consent` validates `p_kind` against a hand-rolled list at
`090060:139`, ahead of the structural guards, so callers switch on a stable
`invalid_kind` rather than parsing a generated constraint name out of a 23514.
**Widening the table CHECK alone is not enough** — the RPC would still reject
`age_assurance`. Both change together.

### 4.3 Leave `withdraw_consent` alone — deliberately

**Corrected during planning.** `withdraw_consent` validates `p_kind` against its
*own* hand-rolled allowlist (`090060:222`), independent of `record_consent`'s.
That list stays `('health_data', 'public_profile')`, so
`withdraw_consent('age_assurance')` already raises `invalid_kind` today. The
guard §3.1 requires is achieved by **not** widening it — no re-emission, no new
error name, no diff.

The hazard is that this is a guard by omission, and the two allowlists now
differ by design where they previously matched. A future maintainer widening
`withdraw_consent`'s list "for symmetry" with `record_consent` would silently
open the hole. Two things prevent that: a loud comment at the widening site in
`record_consent`, and a harness case asserting the refusal (§11).

### 4.4 Append to `handle_new_user`

Re-emit carrying the **whole body forward verbatim** (the profiles insert with
its two NULL-safe consent timestamps, plus the Art. 9 insert appended at
`090000`), and append the age insert at the existing
`>>> APPEND later signup-metadata side effects HERE. <<<` marker.

Reads `age_attested_at` and `age_attestation_version` from
`raw_user_meta_data`. `->>` yields NULL for an absent key, so the OAuth path —
which carries no signup metadata at all — skips this silently and records from
the callback instead, exactly as `health_data` does.

`granted_at` is client-supplied metadata and is therefore already bounded by
the `user_consents_granted_at_range` CHECK added at `090060` §2. No new guard.

### 4.5 Types

`npm run db:types:remote --workspace=packages/supabase`, and commit
`packages/supabase/types/database.ts` in the same change. (The plain `db:types`
target points at a local Docker stack and truncates the file on failure.)

### 4.6 Added in code review

Four things the design did not anticipate, found reviewing the migration and
folded into it. They are recorded here because each closes a gap this spec
argued for but did not actually enforce.

**`user_consents_age_not_withdrawable`.** §4.3's guard-by-omission is correct
but invisible at the point of edit: a maintainer widening `withdraw_consent`
copies forward its newest emission (`20260911090060` §4), a file carrying no
warning, while the warning sits in the `record_consent` file they never open.
A CHECK forbidding `withdrawn_at` on an `age_assurance` row is structural and
survives any future rewrite of any function.

**`user_consents_document_version_shape`.** §7.4 praised the OAuth path for
validating `document_version` rather than trusting it, and the design never
noticed that the *trigger* path validated nothing at all. That asymmetry was
backwards — the trigger path is where the value is unambiguously
attacker-supplied — so a semver-shape CHECK now covers every path.

**A probe.** The sibling migration proves every claim it makes; this one
asserted a drop-and-recreate and proved nothing. Ordering matters: the probe
runs after the `document_version` CHECK, so it cites `'0.0.0'`, not `'probe'`
as its sibling does.

**`source` provenance.** `handle_new_user` hardcoded `source = 'web_register'`.
That is true today — iOS and Android send only `terms_accepted_at` and
`privacy_accepted_at`, so the consent inserts have never fired for a native
signup — but it stops being true the moment the follow-on client work lands,
and a mis-stamped accountability row cannot be told from a genuine one
afterwards. The source is now mapped from a `signup_surface` metadata key
through a closed `case`, applied to the `health_data` insert as well.

The `case` fallback is `web_register`, which means **a native client that sends
`age_attested_at` without `signup_surface` is silently stamped as web.** The
protection is only real once the client change lands, so §9's follow-on issues
name the key explicitly rather than leaving it to review.

## 5. The purge harness

`verify-account-purge.ts:335` asserts `["user_consents", 2]` — the health_data
and public_profile rows it seeds. This change seeds a third `age_assurance` row
and makes the assertion 3, **in the same change**, per the standing rule that a
table's purge coverage and its assertion move together.

This is the one harness CI does not run. It needs the service role and a manual
`npm run db:verify:purge --workspace=packages/supabase` against the linked
project.

## 6. The legal text (DRAFT — requires sign-off)

Four files: `apps/web/docs/terms/{en,fr}.md` and
`apps/web/docs/privacy/{en,fr}.md`.

### 6.1 Terms §3.1 — Eligibility

13 → 16. The France 13–15 parental-consent sentence is **deleted**, not
reworded: with a 16 minimum it describes a band that cannot exist.

### 6.2 Privacy §10 — Minor Users

The section collapses to a single statement of the 16 minimum and what happens
if an under-16 account is discovered. Specifically:

| Clause | Fate |
|---|---|
| §10.1 Minimum Age | Rewritten to 16. |
| §10.2 Parental Consent (13–14) | **Deleted.** This is the misrepresentation. |
| §10.3 Parental Rights | **Deleted.** It grants rights over "a minor user", a population that no longer exists. Guardian requests remain reachable through the ordinary rights route in §9.8. |
| §10.4 Users 15 and Above | **Deleted.** Moot at a 16 minimum. |

FR is a real adaptation, not a translation, and must stay consistent with EN —
the two currently agree and must continue to.

**Open question for the maintainer:** the documents are amended for new
accounts, but existing accounts were formed under a published 13+ minimum.
Whether that needs a transitional statement is a question for counsel, not for
this spec.

### 6.3 Version bumps, and which version the attestation cites

**Found during planning.** `CONSENT_DOCUMENT_VERSION` (`lib/config/consent.ts`)
is bound to the **privacy policy's** `version:` frontmatter by
`consent.test.ts`, which asserts against both `en.md` and `fr.md`. So:

| File | Now | After |
|---|---|---|
| `docs/privacy/{en,fr}.md` frontmatter | `1.2.0` | `1.3.0` |
| `docs/terms/{en,fr}.md` frontmatter | `1.0.0` | `1.1.0` |
| `CONSENT_DOCUMENT_VERSION` | `1.2.0` | `1.3.0` |

The constant **must** move in the same commit as the privacy frontmatter or
`consent.test.ts` fails — which is exactly what that test is for.

**The attestation cites `CONSENT_DOCUMENT_VERSION`** (the privacy version),
not a new terms-version constant. The minimum age is stated in privacy §10.1 as
well as terms §3.1, so the privacy version is a truthful citation for the act,
and it keeps one version constant and one OAuth query param rather than two.

Bumping the privacy version does **not** retroactively invalidate existing
`health_data` rows — they stay active at `1.2.0` until someone calls
`record_consent` at the new version. It does mean #788's re-consent surface has
a version mismatch to act on for every existing account, which is the intended
behaviour and reinforces §8's decision to fold the age attestation into it.

## 7. The web gate

### 7.1 `canSubmitRegistration`

`apps/web/lib/auth/registration-gate.ts` is the whole gate. It exists because
the OAuth buttons were once gated on `submitting` alone, creating accounts with
no consent record (Kritik `F-2026-08-GDP-web-02`). Adding `age: boolean` to
`ConsentChecks` and to the returned conjunction gates **the email submit and
both OAuth buttons at once** — there is no second place to remember.

Its existing unit test enumerates one case per check plus an all-false case;
the new flag gets the same treatment.

### 7.2 The checkbox

A fourth acceptance on `/register`, `id="register-age"`, matching the
health-consent checkbox's shape. Like that one it is **deliberately not a
document link**: an age attestation is its own act, and "I accept the Terms" is
exactly what does not qualify as one.

`register-oauth-hint` explains why the OAuth buttons are disabled (a disabled
control with no stated reason is a WCAG failure). **Corrected during planning:
it does need changing.** Its copy counts the boxes literally — "Tick all three
boxes above to continue" / "Cochez les trois cases ci-dessus pour continuer" —
so a fourth checkbox makes the accessible explanation wrong in both languages.

### 7.3 The email path

`register()` carries `age_attested_at` and `age_attestation_version` in the
signup metadata, alongside the health-consent pair, for §4.4 to pick up. Nothing
client-side calls `record_consent` at signup: with email confirmations enabled
there is no session yet, so the call would simply be lost.

### 7.4 The OAuth path

`app/auth/callback/route.ts` already receives a validated `consent` query
param — validated against `CONSENT_DOCUMENT_VERSION` rather than trusted
verbatim, because a garbage `document_version` in an accountability ledger is
worse than a missing row.

**Both acts ride that one param.** The attestation and the Art. 9 consent are
gated by the same checkbox set before the redirect, so recording both in that
same block is truthful and needs no second parameter. A second `record_consent`
call with `p_kind: "age_assurance"`, same version, `p_source: "web_oauth"`.
Idempotent, so a replayed callback is a no-op; a failure is logged loudly and
never blocks the sign-in, matching the existing call.

### 7.5 i18n

New strings in the web locale JSON. These files are formatting-sensitive:
insert at anchors as text, never round-trip the whole file through a serialiser.

## 8. Existing accounts

Every account that predates this change has no age attestation — the same gap
#788 ("Re-consent surface for accounts that predate the Art. 9 consent gate")
already exists to close for `health_data`.

**This rides #788 rather than growing a second re-consent surface.** A user who
is shown one blocking re-consent screen should be asked for everything missing
at once. Action: add the age attestation to #788's scope, noting that it must
be recorded through `record_consent` from that surface with
`p_source: 'web_settings'`.

Until #788 ships, the enforced minimum applies to new accounts only. That is a
known, bounded gap and should be stated as such when the finding is resolved —
resolving `F-2026-08-SAF-supabase-01` on new-account enforcement alone would
overclaim.

## 9. Scope boundary and follow-on issues

**In scope:** the migration (§4), the purge harness (§5), the legal text (§6),
the web gate (§7).

**Out of scope, each needing its own issue:**

| Work | Why separate |
|---|---|
| iOS attestation at signup | Independent auth flow; mirrors Android 1:1. The RPC and `ios_register`/`ios_oauth` source values already exist. Must also send `signup_surface: "ios"` — see §4.6. |
| Android attestation at signup | As above, plus `signup_surface: "android"`. |
| Malformed signup metadata aborts the account | `age_attested_at: "banana"` raises in `handle_new_user`'s `declare` block and returns an opaque 500. Pre-existing across four metadata keys, so not fixed as a drive-by. |
| Store age/target-audience declarations, diffed in CI | The finding's remediation asks for this. It belongs with M57 · Store readiness, not here. |
| Age attestation on the #788 re-consent surface | §8. An addition to an existing issue, not a new one. |

## 10. Stack shape

Four parts, ordered by dependency, one branch and one PR each (`gh stack`):

1. **Schema** — migration (§4) + regenerated `database.ts` + purge harness (§5).
   Stands alone; verifiable by the migration replay and the manual purge run.
2. **Legal text** — the four markdown files (§6). Independent of part 1; it is
   the half that closes the misrepresentation, and it is the part that blocks on
   maintainer sign-off.
3. **Web gate** — `registration-gate.ts` + its test, the checkbox, the two
   auth paths, i18n (§7). Depends on part 1 for `age_assurance` to be an
   accepted `p_kind`.
4. **Follow-on issues** — filed, not code (§9).

Part 2 is deliberately not stacked under part 1: nothing in the text depends on
the schema, and holding a legal-copy fix behind a migration review is how the
misrepresentation stays published longer than it needs to.

## 11. Verification

| Claim | How it is proven |
|---|---|
| `age_assurance` is an accepted kind | Migration replay on a local stack (`supabase.yml`). |
| `withdraw_consent('age_assurance')` is refused | Case in `verify-account-purge.ts`, asserting the RPC errors (§4.3). |
| The attestation is purged with the account | `npm run db:verify:purge` — manual, service role. |
| An ungated signup is impossible on web | `registration-gate.test.ts` case for the new flag. |
| The OAuth path records the attestation | Callback route change, exercised as the `health_data` call is. |
| EN and FR say the same thing | Read together at review. |

## 12. What this does not fix

Stated plainly so a future reader does not assume the criterion is discharged:

- **It is attestation, not verification** (§2.2). A determined under-16 user
  ticks the box.
- **Existing accounts stay unattested** until #788 (§8).
- **iOS and Android signups stay ungated** until their follow-on issues (§9).
- **Store declarations are not reconciled** (§9).

SAF-06 moves off its current maturity floor — from a declared-but-unenforced
minimum with a false published promise, to an enforced-at-signup minimum with a
server-recorded basis. It does not reach the top of that criterion, and the
finding should be resolved with that scope written into `resolved_by`.
