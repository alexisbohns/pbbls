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

**Drafted:** 2026-09-11, against `main` at the head of the Art. 9 consent-gate
stack (issue #774).
**Why an assessment exists at all:** the processing is large-scale-adjacent
special-category data (Art. 35(3)(b)) — the product's central record is a mood,
an emotion label and a written reflection, which the controller's own published
policy qualifies as Art. 9 health data (`apps/web/docs/privacy/en.md` §3.2, which
states flatly that they "constitute special category personal data"; §4.1 hedges
the same point as "may be qualified" and is not relied on here).
The CNIL's own list of processing requiring a DPIA includes health and
well-being applications. The assessment is therefore treated as mandatory rather
than prudential.
**Companion document:** `docs/compliance/lawful-basis-map.md` — one row per
processing operation, its Art. 6 basis, its Art. 9 condition, and where the
consent record lives. It is a living table; this assessment is dated.

## 1. Context

Who processes what, on whose behalf, with which recipients, and for how long.

### 1.1 Controller

From `apps/web/docs/legal-notice/en.md` and `apps/web/docs/privacy/en.md` §1:

| | |
|---|---|
| Controller | Alexis Bohn, founder and developer of Pebbles |
| Capacity | Data controller within the meaning of Art. 4(7) |
| Location | France |
| Email | hello@bohns.design |
| Postal address | **Not published.** The legal notice and the policy both carry the placeholder `[adresse]` / `[address]` |
| Telephone | **Not published.** Same placeholder |
| Publication director | Alexis Bohn |

The two unfilled placeholders are a live Art. 13(1)(a) defect, not an editorial
oversight to be quietly carried forward. See §6, open question **Q1**.

Pebbles is an open-source project (https://github.com/alexisbohns/pbbls). The
source being public changes nothing about controllership: the deployed instance
and its database are the controller's.

**No DPO is appointed.** Stated rather than omitted: Art. 37 designation is not
mandatory here, and the reasoning belongs on the record. None of the three
Art. 37(1) triggers is met — the controller is not a public authority; the core
activity is not regular and systematic monitoring of data subjects on a large
scale (there is no profiling, scoring, tracking or advertising, and no telemetry
SDK exists on any surface, §4.1); and while the processing *is* of Art. 9 data,
it is not at present *large scale* — the product is in beta with a cohort the
controller describes as small and largely their own test accounts (design spec
`docs/superpowers/specs/2026-09-11-art9-consent-gate-design.md` §7, maintainer
decision of 2026-09-11). **That reasoning is scale-dependent and therefore
expires.** The designation question is one of the revisit triggers in §5.

### 1.2 The processing

Pebbles is a personal journal of emotional moments. A "pebble" records a time, an
intensity, a positiveness, an emotion, optionally related people ("souls"), life
domains, reflective cards, and free text.

Four client surfaces write to one Supabase (PostgreSQL) database, which is the
only contract between them: a Next.js web PWA (`apps/web`), a SwiftUI iOS app
(`apps/ios`), a Kotlin/Compose Android app (`apps/android`), and a Next.js
back-office used by the operator alone (`apps/admin`). There is no server the
controller runs beyond Supabase edge functions and Vercel serverless functions.

The lifecycle of a record is: a user composes a pebble on one surface; it is
written through RPCs or single-table client calls under row-level security; it
is readable by its owner, and — since the M51 visibility grades
(`20260817130000_pebble_visibility_grades.sql`) — optionally by mutual
connections (`private`) or by anyone holding the link (`public`). The default
grade is `secret`, owner-only, and every pebble that existed before the grades
shipped was backfilled to `secret` precisely so that activating the feature could
not silently widen anyone's exposure.

Secondary processing derived from that record: engagement counters (karma,
bounce, achievements), a glyph marketplace, and operator analytics over
aggregates.

### 1.3 Data inventory

One row per table in the `public` schema, read from
`packages/supabase/supabase/migrations/` and confirmed against the generated
`packages/supabase/types/database.ts`. "Special category?" is this assessment's
qualification under Art. 9, following the policy's own position that moods,
emotion labels and reflections are data concerning mental health.

Unless a row says otherwise, retention is **life of the account**, and erasure is
by `purge_account(p_user_id)` inside the `delete-account` edge function — a
single transaction, immediate, with no grace period (§2.2).

**User-owned tables**

| Table | What it holds | Special category? | Retention |
|---|---|---|---|
| `profiles` | display name, colour world, onboarding flag, `terms_accepted_at`, `privacy_accepted_at`, optional public `handle`, `public_profile` flag, profile glyph, `is_admin`, media quota | No, but `handle` + `public_profile` govern exposure of data that is | Life of account; row deleted by purge |
| `pebbles` | `name`, free-text `description`, `happened_at`, `intensity` (1–3), `positiveness` (−1…1), `emotion_id`, `visibility`, server-composed `render_svg` | **Yes** — the core Art. 9 record | Life of account; deleted by purge |
| `pebble_cards` | reflective-card answers (free text), CBT-shaped | **Yes** | Cascade from `pebbles` |
| `pebble_domains` | which life domains a pebble touches | **Yes by association** — ties an emotional state to a life area | Cascade from `pebbles` |
| `pebble_souls` | which souls a pebble names | **Yes by association**, and third-party data | Cascade from `pebbles` |
| `souls` | names of other people the user records, plus a glyph | Third-party personal data (§3.5); not Art. 9 in itself | Life of account; deleted by purge |
| `snaps` | storage path of a photo attached to a pebble | **Potentially** — the image content is user-chosen and unbounded | Cascade from `pebbles`; objects swept from storage by the edge function |
| `pebble_drafts` | `payload` jsonb: an unfinished pebble, same fields as above | **Yes** | Life of account; deleted by purge |
| `collections`, `collection_pebbles` | user-named groupings of pebbles | No, but the names are free text | Life of account; deleted by purge |
| `glyphs` | user-drawn strokes + view box | No | Life of account; **exception:** a glyph referenced by another user (sold, or used on their soul/pebble/profile) is **anonymized** (`user_id = null`), not deleted — decision log 2026-07-29 |
| `glyph_submissions` | marketplace submission, status, price, reviewer | No | `submitter_id` / `reviewed_by` detached to null on purge; the approved row survives as audit trail |
| `glyph_entitlements` | who bought which glyph, price paid | No | Deleted by purge (before `karma_events`, which it references) |
| `glyph_favourites` | user → glyph | No | Deleted by purge |
| `karma_events` | engagement ledger: delta, reason, type, ref | No, but reveals usage rhythm | Deleted by purge |
| `wallet_balances` | derived karma balance | No | Deleted by purge |
| `bounces` | derived streak score | No, but reveals daily app-use rhythm | Deleted by purge |
| `achievement_unlocks` | which achievements, when | No | Deleted by purge |
| `connections` | mutual connection pair (`user_a < user_b`) | Social graph | Deleted by purge, **both sides** |
| `connection_invites` | inviter, plaintext capability token, expiry, revocation | No; the token is a 7-day revocable capability, deliberately plaintext and owner-visible only (`20260730070347`) | Deleted by purge |
| `connection_blocks` | directed block pair | Social graph | Deleted by purge, both directions |
| `log_reactions` | user → Lab log reaction | No | Deleted by purge |

**Operator and reference tables (no data subject rows)**

| Table | What it holds | Special category? | Retention |
|---|---|---|---|
| `logs` | Lab changelog entries authored by the operator (EN/FR) | No | Indefinite; editorial content |
| `reserved_handles` | handle blocklist | No | Indefinite |
| `emotions`, `emotion_categories`, `domains`, `card_types`, `pebble_shapes`, `achievements` | catalogue/reference rows | No | Indefinite |

**Outside the `public` schema**

| Store | What it holds | Special category? | Retention |
|---|---|---|---|
| `auth.users` (Supabase Auth) | email, password hash, OAuth identity (Apple, Google), signup metadata | No | Deleted last in the edge function, by `auth.admin.deleteUser` |
| Supabase auth audit logs | sign-up / sign-in / sign-out timestamps | No | Policy §8.3 states 12 months. **Not evidenced by any file in this repo** — see **Q4** |
| Storage bucket `pebbles-media` | pebble photos; **private** bucket, 1.5 MB cap, `image/jpeg` only (`20260426000001`) | Follows `snaps` | Prefix `pebbles-media/{user_id}/` swept on deletion |
| Storage bucket `lab-assets` | Lab cover images; **public** bucket (`20260421000003`) | No | Operator content |
| Backups | Supabase-managed | Follows the source | Policy §8.5 states 90 days. **Not evidenced in this repo** — see **Q4** |

**Three processing operations are declared to users but do not exist in the
code, and they are declared across the whole published document set, not only in
the privacy policy.** Therapist access, HealthKit and Google Gemma have no
implementation: nothing in `apps/` or `packages/` outside the published markdown
carries them. What *is* published:

- **`apps/web/docs/privacy/en.md`** devotes §5 to therapist access with named
  permissions (`can_view_general`, `can_view_events`), §4.4 to HealthKit, and the
  whole of §12 to Gemma — §12.1 in the present tense ("Pebbles processes your
  events with Google Gemma") and §12.4 offering to disable a feature that does
  not exist.
- **`apps/web/docs/terms/en.md`** carries thirteen matches for the three terms,
  including §4.4 "Therapist Access", an entire §7 "Therapist-Specific Terms"
  (role activation, "Pebbles' approval and verification of professional
  qualifications", and an allocation of independent-controller status to the
  therapist), §9 on what therapists lose when an account closes, and §13.3 on
  HealthKit and biometric data. `fr.md` mirrors it.
- **`apps/web/docs/credits/{en,fr}.md`** describes Gemma in the present tense as
  "the language model powering Pebbles' analysis features, with data anonymized
  before transmission", and HealthKit as `HKStateOfMind` compatibility.

Only `can_view_general` / `can_view_events` genuinely appear nowhere outside the
privacy policy.

**The one disclaimer that exists is narrower than it looks.** Privacy §6.3 ("not
active today") and §7.2 ("This feature is not active today") mark the Gemma
integration as inactive. §12 carries no such marker and reads as live,
disableable processing; the Terms and Credits carry none either; and nothing
anywhere marks therapist access or HealthKit as future.

**The Terms are the more serious defect.** A stale privacy paragraph
over-declares processing. Contractual terms governing the activation, vetting and
data-protection status of a professional role that the product does not implement
create obligations around something that cannot be performed, and they invite a
user to believe a clinician can be given access. Declaring processing that does
not happen is a transparency defect in its own right, and it inflates the
apparent scope of this assessment. Recorded as **Q2**; the same inventory notes
that §2.2's "Cairns" are computed client-side in `WeekRoll` / `WeekRollCairn` and
are not stored anywhere, contrary to policy §8.4's "retained indefinitely".

### 1.4 Recipients and processors

There are no recipients other than processors and, where the user chooses it,
other users. No data is sold, shared with advertisers, or used to train models.

| Party | Role | Location | Basis for transfer |
|---|---|---|---|
| **Supabase Inc.** | Database, authentication, storage, edge functions | Servers in Paris, France (EU) per `legal-notice/en.md` and policy §6.1 | No transfer for primary storage; Art. 28 DPA per policy §6.1 |
| **Vercel Inc.** | Hosting and delivery of `apps/web` and `apps/admin` | Server-side functions pinned to `cdg1` (Paris) by PR #765, commit `f3acd11e`; the global edge network that routes and terminates TLS is operated from the US | EU-U.S. Data Privacy Framework certification, named in policy §6.2 and §7.2 by PR #767, commit `889e0de8` |
| **Apple / Google** | OAuth identity providers, when the user chooses that sign-in | Provider-operated | The user initiates the flow; only an identity assertion is exchanged |
| **Apple / Google** | App distribution (App Store, Play internal testing per `docs/android-play-deploy.md`) | Provider-operated | Distribution, not journal content |
| **Other users** | Recipients *by the user's own act* — a mutual connection sees `private` pebbles, anyone with the link sees `public` ones, and anyone on the open web sees an enabled public profile | n/a | The user's choice, per the visibility grade |

Google (Gemma) appears in the policy's processor inventory at §6.3, which is one
of only two places marking it "not active today". It is not a recipient today,
but three other published sections declare it in the present tense (§1.3,
**Q2**).

No analytics, tag-management, crash-reporting, attribution or advertising
processor exists on any surface. This was checked, not assumed: no such
dependency appears in `apps/web/package.json`, `apps/admin/package.json`,
`apps/android/app/build.gradle.kts` or the iOS project, and
`apps/ios/Pebbles/Resources/PrivacyInfo.xcprivacy` declares
`NSPrivacyTracking: false` with an empty `NSPrivacyTrackingDomains`.

### 1.5 Purposes and retention

| Purpose | Data | Retention |
|---|---|---|
| Deliver the journal: record, re-read, organise and revisit pebbles | Pebbles and every enrichment (cards, domains, souls, snaps, drafts, collections) | Life of the account. Erasure on request is immediate and total (§2.2) |
| Authenticate and identify | `auth.users`, `profiles.display_name` | Life of the account |
| Demonstrate consent (accountability, Art. 5(2) / 7(1)) | `profiles.terms_accepted_at`, `privacy_accepted_at` today; the `user_consents` ledger once Part 2 of this stack lands | Life of the account, plus whatever period is needed to evidence past processing. **Not yet decided — see Q5** |
| Engagement mechanics (karma, bounce, achievements, glyph marketplace) | `karma_events`, `wallet_balances`, `bounces`, `achievement_unlocks`, glyph tables | Life of the account |
| Social features the user opts into | `connections`, `connection_invites`, `connection_blocks`, `pebbles.visibility`, `profiles.handle` / `public_profile` | Life of the account; invites expire after 7 days by default and are revocable |
| Operator analytics: understand product use | Aggregate views over `pebbles`, gated behind `is_admin` security-definer RPCs | Derived on read from live rows; nothing separate is stored, so it disappears with the source data. **No minimum-cohort threshold — §3.6** |
| Security and abuse prevention | Supabase auth logs, `connection_blocks`, reserved handles | Policy states 12 months for auth logs (**Q4**) |

**Retention is, in practice, binary today: data lives while the account lives and
is gone the moment the account is deleted.** There is no partial ageing-out, no
archival tier and no scheduled pruning of old pebbles. For a journal whose value
is re-reading old entries that is defensible, but it should be a decision on the
record rather than an artefact of there being no deletion job. See **Q3**.

## 2. Necessity and proportionality

Cross-reference: `docs/compliance/lawful-basis-map.md`. That table is the
authority on which Art. 6 basis and which Art. 9 condition each operation runs
on; this section assesses whether the processing is *necessary* and
*proportionate* to the stated purposes, and does not restate the bases.

The headline finding of the map is repeated here because it is the reason this
assessment exists, and it is the anchor finding this document answers:
**Kritik `F-2026-08-GDP-web-01` (criterion GDP-02, surface web, high / P1) — the
Art. 9(2)(a) explicit consent the policy claims is not evidenced by any record
the system collects.** The same finding is open on four sibling surfaces
(`-android-02`, `-ios-04`, `-supabase-02`, `-admin-06`), all five citing the
absence of a DPIA; this document is the shared artifact that answers that half
of all five. `profiles.terms_accepted_at` and
`privacy_accepted_at` record blanket acceptance of two documents at signup, are
written by `handle_new_user()` (`20260729120000_handle_new_user_consent.sql`),
are read by nothing, are not bound to a document version, and are absent entirely
on the OAuth paths — that migration's own comment says so: "OAuth signups carry
no consent metadata." Accepting a policy that *mentions* health data is not
explicit consent *to process* it (Art. 9(2)(a), read with Art. 4(11) and Rec. 32).
Parts 2 to 5 of this stack close that for new web accounts; §4.2 lists what
remains.

### 2.1 Minimisation

What the product collects is close to the floor for what it does. A pebble is
three small integers, one foreign key to a fixed emotion catalogue, a timestamp
and optional free text. Nothing is inferred, scored, enriched from third-party
sources, or derived into a profile of the person. There is no device
fingerprinting, no location, no contacts import, no advertising identifier, and
no behavioural telemetry (§1.4).

Three observations cut the other way, and are recorded rather than argued away:

- **Free text is unbounded by construction.** `pebbles.description` and
  `pebble_cards.value` accept anything, and the product actively invites
  reflective disclosure. The controller cannot minimise what the user chooses to
  write; it can only limit who sees it and how long it is kept. **This
  assessment recommends, on its own account,** that the product encourage using
  a soul record rather than writing a person's name into free text — the
  mitigation is right in shape and becomes necessary the moment any AI feature
  ships. It is not offered here as an existing control: the only place that
  advice currently appears is privacy §12.3, inside the Gemma chapter, as a
  mitigation for transmission to a model §6.3 says is not enabled. Nothing in
  the product says it.
- **Photos are unbounded in content,** though bounded in size and type (1.5 MB,
  `image/jpeg`, private bucket).
- **Souls are third-party data collected without the third party's involvement.**
  Assessed in §3.5.

The collection that is *not* obviously necessary is the engagement layer: karma,
bounce streaks and achievement unlocks exist to encourage habit, not to deliver
the journal. They are low-sensitivity in isolation but they do reconstruct a
day-by-day rhythm of when a person was recording feelings. The map puts them on
6(1)(b) contract, which is arguable while they are advertised as part of the
product; it would not survive their being repurposed for anything else.

### 2.2 Accuracy and retention

**Accuracy.** Every field is self-reported and directly editable by its owner on
every surface, which is the strongest accuracy control available for subjective
data; `update_pebble` and the owner-scoped RLS update policies are the mechanism.
There is no inference to be wrong about.

**Retention.** As §1.5 records, retention is life-of-account with no ageing-out.
The erasure path, by contrast, is unusually strong and is the single most
substantiated claim in this document:

`delete-account` (`packages/supabase/supabase/functions/delete-account/index.ts`)
takes the caller's identity from the forwarded JWT — never from the body, so
nobody can delete anyone else — and runs three steps in a load-bearing order:
`purge_account(p_user_id)` as one SQL transaction, then a sweep of the storage
prefix `pebbles-media/{user_id}/`, then `auth.admin.deleteUser`. Each step is
idempotent, so a failure at any point converges on a retry.
`purge_account` (`20260729201326_account_deletion_purge.sql`, re-emitted as the
union of two parallel appends in `20260731090000_purge_account_union.sql`) is
`security definer`, granted to `service_role` only, and deletes every user-owned
table listed in §1.3 — including the two-sided `connections` and
`connection_blocks` deletes, which are deliberately not the usual
`user_id = p_user_id` shape.

`packages/supabase/scripts/verify-account-purge.ts` is the proof. It seeds a
throwaway account with every entity type, deletes it through the real edge
function, and asserts that every row is gone, that the storage prefix is empty,
that the auth user is gone, that a counterparty's bought glyph still renders, and
that a re-run converges to zero counts. It needs the service role, so CI does not
run it; the standing rule in `CLAUDE.md` requires it to be run manually against
the linked project after any change to `purge_account`.

Two honest caveats on erasure. First, **deletion is immediate, while the policy
promises a 30-day window** ("30 days after account deletion, before permanent
erasure", §8.1; "complete erasure within 30 days", §9.3). Acting faster than
promised is not a rights failure, but the text and the system disagree and the
text is the thing a regulator reads (**Q6**). Second, **externally-referenced
glyphs are anonymized rather than deleted** (`user_id = null`) so that another
user's purchase keeps rendering. That is a deliberate, logged decision and the
residual artefact is a drawing with no link to a person, but it is a documented
exception to "everything is erased" and belongs in the policy too.

### 2.3 Data-subject rights

| Right | Status | Where |
|---|---|---|
| Art. 13/14 information | **Partial** | `apps/web/docs/privacy/{en,fr}.md` is thorough, but the controller's postal address and telephone are unfilled placeholders (**Q1**), and it describes three operations that do not exist (**Q2**) |
| Art. 15 access | **Manual only** | Policy §9.8: by email to hello@bohns.design. No in-product access-request path, and no export to satisfy it with (see Art. 20) |
| Art. 16 rectification | **Met, self-service** | Every field is editable by its owner on every surface (§2.2) |
| Art. 17 erasure | **Met, self-service and verified** | `delete-account` edge function + `purge_account`, proven by `verify-account-purge.ts`. Reachable from web settings (`DeleteAccountSection`) |
| Art. 18 restriction | **Not implemented** | No mechanism exists to freeze processing short of deletion. The design spec records this as deliberate (D2): a read-only frozen account was rejected in favour of deletion |
| Art. 20 portability | **UNMET** | **No data export exists on any surface.** Greps across `apps/web`, `apps/ios` and `apps/android` for an export or download path return nothing. Policy §9.5 restates the statutory right in request-based terms and stops short of promising an in-product export, so the defect is not a broken promise: it is that no mechanism exists to answer such a request with. Tracked in the lawful-basis map's known gaps and in §4.2 below as unscheduled |
| Art. 21 objection | **Manual only** | By email. Applies to the legitimate-interest operations (souls, operator analytics, security) |
| Art. 7(3) withdrawal of consent | **Not implemented as such** | There is no consent to withdraw, because none is recorded (§2) — the absence of both a withdrawal surface and a document-version binding is Kritik `F-2026-08-GDP-web-06`. After Part 4 of this stack, withdrawal of the core consent routes to account deletion; the public-profile toggle is the one consent that is genuinely withdrawable today, in place |
| Art. 22 automated decision-making | **Not applicable** | No automated decision produces legal or similarly significant effects. No profiling, scoring or ranking of people exists |

**Art. 20 is the gap this assessment refuses to soften.** A journalling product
whose whole premise is that "your data remains yours" (policy preamble) and which
offers total erasure but no way to take a copy out first is in the least
defensible position of the three options: a user exercising erasure loses
everything irrecoverably, and a user exercising portability cannot. Erasure
without export also makes withdrawal-as-deletion (§4.2, design decision D2)
harsher than it needs to be. Export should be treated as a prerequisite for
that design, not as an unrelated nice-to-have.

## 3. Risks to rights and freedoms

Severity and likelihood use the CNIL's four-level scale (negligible, limited,
significant, maximum). Severity is judged on the impact on the data subject, not
on the controller.

### 3.1 Illegitimate access to data

**Feared event.** A third party reads a person's moods, reflections, photos or
social graph — through a broken authorisation rule, a leaked service-role key, a
compromised account, or a processor breach.

**Severity: significant.** These are Art. 9 data about mental state, often
naming other people, written in the belief that no one else reads them. Exposure
is not financially recoverable and can affect relationships, employment and
personal safety.

**Likelihood: limited.** Every one of the 30 tables in `public` has
`enable row level security` (verified table by table), and the owner predicate is
`user_id = auth.uid()`. Cross-user reads do not widen a policy: they go through
`security definer` projections that build an explicit jsonb allowlist —
`get_public_profile` (current body `20260817090000`, superseding `20260730120000`)
and `get_shared_pebble` (`20260817130000`), both of which deliberately omit
`user_id` and every enrichment table. `anon` is revoked from `public.pebbles` outright. Newer tables
use the stricter `for all … using … with check` policy shape precisely because
the older four-policy style omitted `with check` on UPDATE and would have let a
row be moved to another user's `user_id` (see the comment in
`20260729213348_pebble_drafts.sql`). The media bucket is private. Transport is
HTTPS and storage is encrypted at rest by Supabase (policy §11.1).

**Residual risk: limited, trending on operational rather than technical
weakness.** The remaining exposure is concentrated in three places: the
service-role key, which bypasses RLS entirely and is held by edge functions and
by the operator; the single-operator model, in which one compromised credential
is a total compromise (policy §11.2 states plainly that only the controller has
system access); and `is_admin`, a boolean on `profiles` that gates every
analytics RPC — mitigated since `20260902090000_profiles_privileged_guard.sql`
pinned privileged columns against direct client writes. No breach-notification
procedure (Art. 33/34) is documented anywhere in this repository: **Q7**.

### 3.2 Unwanted modification of data

**Feared event.** A person's journal is altered — by another user, by a defective
migration, or by a client bug — so that what they wrote is no longer what they
read back.

**Severity: limited.** A journal that misremembers is a real harm to the person
relying on it, particularly one used for emotional self-tracking, but it is not
of the same order as disclosure.

**Likelihood: limited.** Writes are owner-scoped by the same RLS policies as
reads, and cross-user reads are read-only projections with no write path.
Multi-table writes go through `security definer` RPCs in a single transaction
rather than client-stitched calls — a standing rule in `AGENTS.md` — so a partial
failure cannot leave a half-written pebble. Invariants that matter are enforced
as CHECK constraints so that no write path can bypass them (`intensity` 1–3,
`positiveness` −1…1, `visibility` in the three grades, handle format, "public
requires a handle").

**Residual risk: limited.** The real exposure is migration error rather than
attack. The repository has already recorded one instance of the class: two
migrations authored in parallel each re-emitted `purge_account` with their own
append, and `create or replace` silently dropped one set of deletes, with no git
conflict to warn anyone (`20260731090000_purge_account_union.sql`). That was
caught before it reached the live database and is now a standing rule in
`CLAUDE.md`, but it is the clearest evidence in this repo that whole-function
re-emission is a live failure mode. The contract harnesses in
`packages/supabase/scripts/` are the counterweight, four of them gated in CI
(`supabase.yml`).

### 3.3 Disappearance of data

**Feared event.** A person loses their journal — through a failed migration,
accidental deletion, processor failure, or an erasure that goes further than
intended.

**Severity: significant.** There is no second copy. Because **no export exists**
(§2.3), the user cannot hold one, so loss at the controller is loss outright. For
years of personal writing that is a serious harm, and the missing export
converts what would be a limited risk into a significant one.

**Likelihood: limited.** Supabase provides managed backups (policy §8.5 cites a
90-day window; not evidenced in this repo, **Q4**). Cascades are explicit and
FK-ordered. The deletion flow is scoped to the caller's own id, taken from the
JWT, and `verify-account-purge.ts` asserts the scoping directly by keeping a
counterparty account alive through the purge.

**Residual risk: significant, and the mitigation is export.** No restore has ever
been rehearsed as far as this repository records, and no one but the controller
could initiate one. Until a user can take their own copy, the product's
resilience is entirely the processor's.

### 3.4 Re-identification through public profiles

**Feared event.** A person's public profile, or a pebble shared by link, lets
someone connect a real identity to emotional data the person did not intend to
publish.

**Severity: significant.** Re-identified Art. 9 data is the same harm as §3.1,
arrived at by inference instead of intrusion — and it is harder to undo, because
the person believed they were choosing what to expose.

**Likelihood: limited, by design, but non-zero.** Publication requires two
separate opt-ins that both default off: claim a handle, then flip
`public_profile` (`20260730120000_public_profiles.sql`). `get_public_profile`
returns a fixed allowlist. Its **current body is
`20260817090000_public_profile_achievements.sql:67-205`**, a whole-body
re-emission that supersedes the day-one version in `20260730120000`; read against
the older file the allowlist looks smaller than it is. In full, it returns:
`display_name`, `handle`, the profile glyph as raw `strokes` + `view_box`,
`pebbles_count`, `ripple_level`, `bounce_level`, a 28-day `assiduity` grid,
`days_practiced`, `member_since` (UTC date), `achievements_count`, and
`achievements` — up to six *unlocked* badges, each carrying `id`, `slug`,
`family`, `threshold`, `emotion_id`, `domain_id`, EN/FR title and description
overrides, glyph geometry, and `unlocked_at` coarsened to a UTC date. Excluded,
per that migration's own contract note: `user_id`, email, all pebble content,
`is_admin`, consent timestamps, quotas, karma, `color_world`, the raw counts
behind the levels, and `active_today`. `get_shared_pebble` likewise projects a single pebble
by uuid (122 unguessable bits), returns null for anything not graded `public` so
that unknown and ungraded are indistinguishable, and excludes snaps, cards,
souls and domains entirely. Handles that invite impersonation are blocked by
`reserved_handles` and a trigger that binds direct writes, not just the RPC.

**Residual risk: limited, but for narrower reasons than the opt-in design alone.**
What remains is inherent to publishing at all, and it is more than a presence
signal. A public profile discloses a self-chosen display name alongside two
things:

- a **28-day daily activity grid** — a pattern of *when* someone was recording
  feelings, which is behavioural data about mental state even without a single
  pebble being readable; and
- a **shelf of up to six unlocked badges**, several of which are keyed to an
  `emotion_id` or a `domain_id` and each of which carries an unlock date. Those
  are earned by recording a given emotion, or recording in a given life domain,
  often enough to cross a threshold. So the shelf discloses not only *when* the
  person journals but *which emotional and life-domain themes recur* in their
  journalling, and roughly when each pattern became established. That is closer
  to the Art. 9 core than the grid is, and it is the disclosure §3.4 has to
  reason about, not the grid alone.

Combined with a handle reused elsewhere online, that is a real linkage vector.

The reason the assessment still lands on *limited* is that the badge disclosure
is deliberate and already narrowed at the point of projection, by the author of
`20260817090000`: only **unlocked** badges are returned, so a visitor learns
nothing about what the owner has not earned; the array is capped at six; and
`unlocked_at` is **coarsened to a UTC date specifically so it is no finer a
presence signal than the assiduity grid already is** — the same reasoning that
excludes `active_today`. Those are exactly the mitigations this section would
otherwise have to ask for.

Two mitigations worth the controller's consideration remain absent, and the first
matters more now than it would have before the badges shipped: no warning at the
moment of enabling a public profile explains what becomes public — the activity
grid *and* an emotion- and domain-keyed badge shelf — and nothing rate-limits or
robots-excludes public profile pages. Neither is a defect against a stated
promise, so both are recorded here rather than as findings.

### 3.5 The souls problem

**Feared event.** A user names another person inside their own pebbles. That
person is a data subject: their name is stored (`souls.name`), linked to the
user's emotional record (`pebble_souls`), and potentially readable by the user's
mutual connections or, through free text, by anyone holding a public share link.
They never supplied the data, do not know it exists, and cannot exercise rights
they do not know they have.

**Severity: significant.** The data is not merely "a name". A soul row acquires
meaning from the pebbles it is attached to: that this person was present when
someone felt a strong negative emotion, repeatedly, in a particular life domain.
That is an inference about a relationship, and about the named person's role in
someone else's mental state. The named person can neither correct it nor object,
and would have no way to discover it.

**Likelihood: maximum, in the sense that it is certain by design** — naming souls
is a core feature, not an edge case.

**The household-exemption reasoning, and its limit.** Art. 2(2)(c) excludes
processing "by a natural person in the course of a purely personal or household
activity" from the GDPR. A private diary naming one's family is the paradigm
case, and while a pebble is `secret` (the default, and the state every
pre-M51 pebble was backfilled to) the user's own processing sits squarely inside
it. That is the strongest argument available, and it is a real one.

It is also narrower than it looks, in three ways that this product actively
engages:

1. **The exemption covers the user, never the controller.** Rec. 18 is explicit
   that the GDPR "applies to controllers or processors which provide the means
   for processing personal data for such personal or household activities."
   Pebbles is exactly such a means. The controller's own obligations — lawful
   basis, security, minimisation — are undiminished, whatever the user's status.
   The lawful-basis map accordingly puts souls on 6(1)(f) legitimate interest
   (operation #4) rather than treating the row as out of scope.
2. **The exemption weakens as pebbles become shareable.** *Lindqvist* (C-101/01)
   and *Ryneš* (C-212/13) both turn on whether the activity reaches beyond the
   private sphere. `private` (connection-visible) pebbles push a named person's
   data to an audience the named person did not choose; `public` share links push
   it to anyone with a URL; a public profile attaches a real-world identity to
   the account doing it. M49 and M51 moved this product measurably toward the
   *Lindqvist* end of that spectrum, and future sharing features will move it
   further. **The household argument should be treated as weakening, not as
   settled.**
3. **Free text carries what the schema does not.** Even with souls excluded from
   every cross-user projection, a `description` naming someone travels wherever
   the pebble travels.

**Existing controls.** Souls are never exposed cross-user: `souls` and
`pebble_souls` keep owner-only RLS, `get_shared_pebble` excludes every enrichment
table by name, and `get_public_profile` returns no pebble content at all. The
default grade is `secret`. Souls are erased with the account. The policy
acknowledges souls as third-party data (§2.5). There is no directory, no search
across users' souls, and no attempt to resolve a soul to a real account.

**Not counted as a control:** the "use a soul record rather than writing the name
in plaintext" advice. It exists only in privacy §12.3, inside the Gemma chapter,
as a mitigation for transmission to a model §6.3 says is not enabled — a user
reading about a feature they cannot use. Nothing in the product carries it. §2.1
restates it as this assessment's own recommendation instead.

**Residual risk: significant, and structurally unresolvable by technical means.**
No control can give a named person rights they cannot know to exercise. What is
available is honesty about scope and restraint about sharing. Concretely, this
assessment recommends that (a) souls and any pebble free text remain excluded
from every cross-user projection as an invariant, not a default, (b) any future
feature that would surface a soul to a third party re-opens this assessment
(§5), and (c) the controller decide how a third party who *does* learn of a soul
record would exercise Art. 15 or 17 over it — no procedure exists (**Q8**).

### 3.6 Operator analytics without a minimum cohort

**Feared event.** The operator's analytics dashboards present aggregates that, in
a small cohort, are effectively one identifiable person's emotional record.

**Severity: limited.** The viewer is the controller, who could read the rows
directly with the service role; the harm is not disclosure to an outsider but the
absence of a boundary between "analysing a product" and "reading one person's
feelings", plus the risk that such a view is screenshotted, shared or built upon.

**Likelihood: significant** — it is the current state at current cohort size, not
a hypothetical.

**Existing controls.** Every analytics view is reachable only through a
`security definer` RPC that gates on `is_admin(auth.uid())`, and the underlying
views are never granted directly. The analytics RPCs include `get_kpi_daily`,
`get_active_users_series`, `get_retention_cohorts`, `get_pebble_volume_series`,
`get_pebble_enrichment`, `get_user_averages_series`, `get_emotion_share`,
`get_domain_share`, `get_bounce_distribution_today` and
`get_quality_signals_today` (`20260501000005_analytics_quality_signals.sql:151`).
That list is not exhaustive and should not be read as a closed set: the same
`if not public.is_admin(auth.uid())` guard appears 35 times across the
migrations, covering admin moderation and catalogue-management RPCs as well as
analytics. `is_admin` is itself protected against direct
client writes (`20260902090000`). No aggregate is stored: they are computed on
read, so nothing survives the deletion of the source rows.

**What is missing.** None of these RPCs applies a minimum-cohort threshold.
`v_analytics_emotion_share_weekly` (`20260501000003`) buckets every pebble by ISO
week and emotion and reports a percentage share; for a week with one active user
that share *is* that user's emotional record, rendered as a chart.
`v_analytics_user_averages_weekly` and the bounce distribution have the same
shape. Nothing suppresses a bucket below *k* contributors, and nothing warns the
viewer that a bucket is thin.

**Residual risk: limited but live.** Tracked as Kritik `F-2026-08-GDP-admin-06`
and recorded as a known gap in `docs/compliance/lawful-basis-map.md` (#11). The
fix is a suppression threshold applied inside the RPCs, where it cannot be
bypassed by a client, rather than in the dashboard.

## 4. Measures

What already answers the risks in §3, and what is still owed.

### 4.1 In place

Each of the following was verified against the repository while drafting, at the
file named. They are stated as facts about the code, not as aspirations.

| Measure | Evidence |
|---|---|
| **Row-level security on every table in `public`** | All 30 tables carry `alter table public.<t> enable row level security`, checked table by table across `packages/supabase/supabase/migrations/`. User tables scope on `user_id = auth.uid()`; dependent tables scope through their parent (`pebble_cards`, `pebble_souls`, `pebble_domains`, `collection_pebbles`) |
| **Writes that must not come from a client have no write policy at all** | `bounces`, `wallet_balances`, `achievement_unlocks`, `connections`, `connection_invites`, `connection_blocks` expose SELECT only; they are written exclusively by triggers or `security definer` RPCs |
| **Cross-user reads are `security definer` projections with an explicit jsonb allowlist, never widened RLS and never a view** | `get_public_profile` is the template — introduced in `20260730120000_public_profiles.sql`, **current body `20260817090000_public_profile_achievements.sql:67-205`** (cite the later file: the earlier one is superseded and its allowlist is shorter). `get_shared_pebble` (`20260817130000_pebble_visibility_grades.sql`) follows the same pattern. Neither returns `user_id`. `anon` is revoked from `public.pebbles`. What each projection exposes is assessed in §3.4 |
| **Private-by-default sharing** | `pebbles.visibility` defaults to `secret`; the M51 migration backfilled every pre-existing pebble to `secret` rather than letting the new grade widen them. Public profiles need two independent opt-ins, both default off |
| **Multi-table writes are atomic** | Standing rule in `AGENTS.md`: anything touching more than one table goes through an RPC in one transaction, not client-stitched calls |
| **No telemetry, analytics, crash-reporting or advertising SDK exists on any surface, so none can reach a sensitive field** | No such dependency in `apps/web/package.json`, `apps/admin/package.json`, the Android Gradle build or the iOS project. `apps/ios/Pebbles/Resources/PrivacyInfo.xcprivacy` declares `NSPrivacyTracking: false` with empty tracking domains and labels moods as `NSPrivacyCollectedDataTypeHealth`, linked and not used for tracking. Policy §13.3 says the same and is, unusually, exactly true |
| **Account deletion works end to end and is proven** | `packages/supabase/supabase/functions/delete-account/index.ts` → `purge_account` (`20260729201326`, union `20260731090000`) → storage sweep → `auth.admin.deleteUser`, each step idempotent. `packages/supabase/scripts/verify-account-purge.ts` seeds every entity type and asserts zero rows, empty storage prefix, dead auth user, surviving counterparty, and a converging re-run |
| **Erasure completeness is a standing repo rule, not a habit** | `CLAUDE.md`: a table added to `purge_account` gains its seed and its zero-row assertion in `verify-account-purge.ts` in the same change |
| **Execution and storage pinned to the EU** | Supabase database, auth and storage in Paris (`legal-notice/en.md`); Vercel server-side functions pinned to `cdg1` by PR #765 (`f3acd11e`). The remaining US touchpoint is Vercel's edge network, disclosed in policy §7.2 by PR #767 (`889e0de8`) |
| **Media is private and bounded** | `pebbles-media` is a non-public bucket, 1.5 MB per object, `image/jpeg` only (`20260426000001_pebbles_pictures.sql`) |
| **Privileged profile columns cannot be written by a client** | `20260902090000_profiles_privileged_guard.sql` |
| **Database contract is tested in CI** | `supabase.yml` runs four anon-only harnesses on every change under `packages/supabase/**` and nightly: `verify-pebble-drafts`, `verify-pebble-visibility`, `verify-profiles-privileged-guard`, `verify-public-profile` |
| **Transport and storage encryption** | HTTPS throughout; encryption at rest by Supabase (policy §11.1). Provider-level, not independently verified here |

### 4.2 Planned

| Measure | Where |
|---|---|
| Art. 9 explicit consent recorded in a version-bound `user_consents` ledger, owner-select-only RLS, written only through two `security definer` RPCs | Kritik **`F-2026-08-GDP-web-01`** — this stack, issue #774, Parts 2–4 (`docs/superpowers/plans/2026-09-11-art9-consent-gate.md`) |
| **Consent captured for every new web account, by two mechanisms rather than one** — (a) a third checkbox on `/register`, gating the email submit and that page's two OAuth buttons; (b) a consent gate rendered inside onboarding when no active `health_data` consent exists, which catches accounts created through the **login page's** OAuth buttons | Kritik **`F-2026-08-GDP-web-02`** (GDP-01, high / P1) — **resolved by this stack.** `apps/web/app/login/page.tsx:87,98` wires `signInWithGoogle` / `signInWithApple` gated on `submitting` alone, and an OAuth sign-in creates an account when none exists, so a `/register` checkbox structurally cannot reach every new account. The gate keys on `profiles.onboarding_completed`: a brand-new account is `false` and routes to `/onboarding`, an established one is `true` and never does — so it catches exactly the new consent-less accounts and cannot fire for the pre-existing cohort (design spec D1a, plan Task 14) |
| Consent bound to a document version, and withdrawable from settings without deleting the account first where the consent is severable | Kritik **`F-2026-08-GDP-web-06`** (GDP-01, P2) — **substantially resolved by this stack**: `document_version` on the ledger (Part 2) and the settings withdrawal surface (Part 4). The login-time re-consent trigger the finding also asks for — re-asking when the policy version moves — stays M55 work |
| Privacy-policy wording corrected so "during onboarding" matches where consent is actually taken, and withdrawal described as it actually behaves | This stack, Part 5 |
| **Re-consent surface for accounts that already exist**, which neither mechanism above can reach: every account created before this stack, including every OAuth account created to date, carries no Art. 9 consent record and is already past onboarding | M55. Accepted as out of scope for this stack by the maintainer on 2026-09-11 (design spec §7) on the basis that the beta cohort is small and largely the maintainer's own test accounts. **This is the one population the stack leaves uncovered**, and the reasoning is cohort-size-dependent (§5) |
| **Data export, to meet Art. 20 and to make erasure survivable** | Kritik **`F-2026-08-GDP-web-04`** (P2). Tracked, but **unscheduled**: no milestone and no surface, and this stack does not address it. The single largest open commitment in this assessment (§2.3, §3.3), and P2 arguably understates it given that erasure without export is irreversible for the user |
| Minimum-cohort suppression inside the analytics RPCs | Kritik `F-2026-08-GDP-admin-06` (§3.6) |
| iOS consent capture | Kritik `F-2026-08-GDP-ios-04` |
| Android consent capture | Kritik `F-2026-08-GDP-android-02` |
| Sensitive-column inventory plus a CI guard against sensitive fields reaching a log or analytics sink | Kritik `F-2026-08-GDP-supabase-02` |
| Documented Art. 33/34 breach-notification procedure | Not yet tracked — **Q7** |
| A decision on how a third party named as a soul would exercise their rights | Not yet tracked — **Q8** |

## 5. When to revisit

Re-open this assessment before merging anything that:

- adds or widens a column holding emotion, intensity, positiveness or free-text
  reflection;
- adds a recipient or processor;
- widens who can see a pebble;
- adds a new export, log or analytics sink over any of the above.

Two further triggers follow from the reasoning in this document specifically:

- **any feature that surfaces a soul, or free text naming a person, to anyone but
  the record's owner** — that is the point at which the §3.5 household reasoning
  stops holding;
- **material growth of the user base beyond the beta cohort** — both the §1.1 "no
  DPO" reasoning and the §3.6 cohort risk are scale-dependent and expire with it.

> This trigger is intended to become a repo rule in `CLAUDE.md`. Per
> `CLAUDE.md`, learnings are promoted at the audit grooming pass at milestone
> boundaries, never per-PR — so it lives here until then.

## 6. Open questions for the controller

Each of these is a judgement call that could not be resolved from the repository.
None is guessed at in the body above.

**Q1 — The controller's postal address and telephone are unpublished, in six
places across four documents.** Precisely, and per file:

| File | Where | Placeholder |
|---|---|---|
| `apps/web/docs/privacy/en.md` | §1 (`:33-34`) and §17 (`:418-419`) | `[address]` / `[telephone]` (English) |
| `apps/web/docs/privacy/fr.md` | `:25-26` and `:378-379` | `[adresse]` / `[téléphone]` |
| `apps/web/docs/legal-notice/en.md` | Publisher block (`:18-19`) | `[adresse]` / `[téléphone]` — **French placeholders left untranslated in an English document**. The legal notice has no numbered sections |
| `apps/web/docs/legal-notice/fr.md` | `:16-17` | `[adresse]` / `[téléphone]` |
| `apps/web/docs/terms/en.md` | Service Provider block (`:23-24`) | `[adresse]` / `[téléphone]`, again untranslated |
| `apps/web/docs/terms/fr.md` | `:19-20` | `[adresse]` / `[téléphone]` |

Separately, `legal-notice/{en,fr}.md` carries an unfilled `[LICENSE]` placeholder
on a sentence that publicly claims the project is open source — a licence naming
itself is the whole substance of that claim.

Art. 13(1)(a) requires the controller's identity and contact details, and the
French LCEN requires more of a publisher than the GDPR does. Only the controller
can decide what address to publish for a solo project, and whether a
domiciliation service is wanted. Until that is settled, this assessment cannot
state the controller's full identity (§1.1).

**Tracked as issue #779, outside this stack.** It is recorded here because an
impact assessment that could not name its controller would be incomplete without
saying why.

**Q2 — Three processing operations are declared to users across the published
document set and do not exist in the code.** Therapist access, HealthKit and
Google Gemma have no implementation anywhere in `apps/` or `packages/`, yet they
appear in the privacy policy (§5, §4.4, §12), the **Terms** (§4.4, the whole of
§7, §9, §13.3 — thirteen matches in `en.md`, mirrored in `fr.md`) and the
**Credits** page (Gemma described in the present tense as powering "Pebbles'
analysis features"; HealthKit as `HKStateOfMind` compatibility). Only the two
therapist permission names (`can_view_general`, `can_view_events`) are confined
to the privacy policy. The sole disclaimer sits in privacy §6.3 and §7.2 and
covers Gemma alone; privacy §12 itself reads as live, disableable processing.

Two decisions are needed, and neither is an engineering call:

1. **Is each operation planned or abandoned?** If planned, each needs marking as
   future processing wherever it appears, not only in §6.3. If abandoned, each
   needs removing from four documents.
2. **The Terms are the sharper end of this and should be handled first.** §7
   creates contractual machinery around a professional role — activation,
   Pebbles' approval, verification of professional qualifications, an allocation
   of independent-controller status — for a role the product does not implement.
   That is an obligation that cannot be performed and an invitation to believe a
   clinician can be granted access. It should not wait on the roadmap question.

The same question covers "Cairns" as an indefinitely-retained aggregate (policy
§8.4), which are computed client-side in `WeekRoll` / `WeekRollCairn` and stored
nowhere.

**Q3 — Is life-of-account the intended retention for journal content?** There is
no ageing-out, no archival tier and no pruning job, so the current behaviour is
as much an absence as a decision. For a re-reading product indefinite retention
is defensible, but Art. 5(1)(e) wants it stated and justified rather than
inherited. A stated policy — "kept until you delete your account or the pebble"
— would close it.

**Q4 — The two retention periods the policy states are not evidenced anywhere in
this repo.** Auth logs for 12 months (§8.3) and backups for 90 days (§8.5) are
both Supabase platform behaviours that depend on plan and dashboard
configuration. The controller should confirm the actual configured values and
whether they are contractually guaranteed, since the policy states them as
commitments.

**Q5 — How long should a consent record outlive the account?** Art. 7(1)
accountability argues for keeping proof of consent after processing ends; Art.
5(1)(e) and the product's own promise of total erasure argue for destroying it
with everything else. The design as planned deletes `user_consents` in
`purge_account` along with all other rows. That is the cleaner promise and
probably the right call, but it is a controller's decision about litigation risk,
not an engineering one.

**Q6 — Deletion is immediate; the policy promises a 30-day window.** The system
erases in one transaction with no grace period, while §8.1 and §9.3 describe up
to 30 days. Should the text be corrected to match the system (immediate, and
better), or should a grace period actually be built — which would give users a
recovery path from accidental deletion, at the cost of holding Art. 9 data after
a request to erase it? These are opposite answers and both are defensible.

**Q7 — No breach-notification procedure exists.** Nothing in this repository
documents how an Art. 33 notification to the CNIL within 72 hours, or an Art. 34
communication to affected users, would be assessed, drafted or sent. For a
single-operator product the procedure can be short, but it has to exist before
it is needed.

**Q8 — How would a person named as a soul exercise their rights?** §3.5 concludes
that no technical control can reach them. If such a person did learn of the
record and asked for access or erasure, the controller would have to search other
users' private journals to answer — which is itself a disclosure risk to those
users. A stated position is needed, even if the position is "we would decline
absent a legal order".

**Q9 — Is the §3.5 household-exemption reasoning accepted as drafted?** This
assessment argues the exemption covers the user but never the controller, and
weakens as sharing widens. That is a legal characterisation offered by an
engineering agent, and it is the single most consequential judgement in this
document: if a reviewer disagrees, the lawful basis for `souls` (map operation
#4, currently 6(1)(f)) and the whole of §3.5 need rework.

**Q10 — Was a prior consultation under Art. 36 considered?** Prior consultation is
required only where residual risk remains high after mitigation. This assessment
does not conclude that it does, but it does leave two "significant" residual risks
(§3.3 disappearance, §3.5 souls). The controller should record a decision either
way, since "we did not consider it" and "we considered it and concluded it was
not required" are different positions on the record.

## 7. Validation

| | |
|---|---|
| Reviewed by | |
| Role | |
| Validated on | |
| Outcome | |
