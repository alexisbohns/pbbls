# Supabase (database contract) — Kritik audit 2026-08

Commit `10181916ba9f56789e62c6351bb380682e5d90da` · Overall surface score **53 / 100 → grade D** (Pebbles profile weighting; open findings 1 Critical, 9 High, 31 Medium, 3 Low).

## Verdict

This surface holds the single Critical of the entire audit: `profiles.is_admin` is a client-writable column with no `WITH CHECK`, no guard trigger, and no column-privilege revoke, so any authenticated user can self-grant full operator rights with one PostgREST `PATCH` and defeat every `is_admin(auth.uid())` gate in the schema (F-2026-08-SEC-supabase-01, severity 20). That one hole caps the Security domain at D despite otherwise strong definer hygiene. The best structural strength is the opposite story: migration and schema-change quality is the top domain here (ARC 75 → B), with append-only migrations, RLS shipped alongside every table, all 63 security-definer emissions pinning `search_path`, RPC-first multi-table writes with sibling symmetry, and cross-user projections built from explicit jsonb allowlists that the verify harnesses assert field-by-field. The recurring theme across the weak domains is enforcement, not intent: excellent contract harnesses exist for purge, visibility, and public-profile projections, but nothing in CI runs them (TST 49 → D, and no workflow touches `packages/supabase` at all). Reliability is the lowest domain (33 → E): no backup, export, or restore posture exists for the database, the two storage buckets, or auth identities, and production failures land in unwatched logs. Platform compliance (34 → E) is blocked for store launch by the absence of any report primitive while cross-user UGC is already live. The overall surface score is **53**, grade **D**.

## Domain scores

| Domain | Score | Grade | Open (C/H/M/L) | Note |
|---|---|---|---|---|
| Security (SEC) | 62 | D* | 1 / 0 / 3 / 1 | Capped from C to D by the open Critical (`is_admin` self-escalation); definer hygiene and RLS default-deny are otherwise L3. |
| Privacy & Data Protection (PRV) | 63 | C | 0 / 0 / 1 / 0 | Strongest domain on the surface after ARC; only gap is the missing PII inventory / ROPA (caps PRV-01 at l1). |
| GDPR & Regulatory (GDP) | 42 | D | 0 / 1 / 5 / 0 | Federated-consent, DPIA, export, retention, and breach-readiness are all thin or absent. |
| Safety & Wellbeing (SAF) | 50 | D | 0 / 2 / 3 / 0 | Strong server-side block enforcement, but no age gate and emotion-conditioned rewards. |
| Code Quality & Architecture (ARC) | 75 | B | 0 / 0 / 2 / 0 | Best domain: append-only migrations, RPC-first writes, strict typing, RLS-with-tables. |
| Testing & Verification (TST) | 49 | D | 0 / 2 / 4 / 0 | High-quality harnesses that nothing in CI runs; coverage is a hand-maintained subset. |
| Platform & Store Compliance (PLT) | 34 | E | 0 / 2 / 2 / 1 | No report primitive, console-only production config, broken provider parity. |
| Accessibility & Inclusion (A11Y) | 40 | D | 0 / 0 / 2 / 0 | Slug-based localization strategy, but the catalog is not version-controlled and no copy guideline exists. |
| Performance & Efficiency (PRF) | 50 | D | 0 / 0 / 2 / 0 | Deliberate indexes and media caps, but bare `auth.uid()` in all 77 policies and unindexed hot FKs. |
| Reliability & Observability (REL) | 33 | E | 0 / 2 / 3 / 1 | Lowest domain: no backup/restore posture, no monitoring, orphaned media, migration re-emission hazard. |
| Agentic Development Readiness (AGT) | 70 | B | 0 / 0 / 4 / 0 | Dense, promoted decision rules, but placeholder lint and no CI backstop. |

`*` = grade capped below its raw band by an open finding: an open Critical caps the domain grade at D (framework 4.5). No domain is N/A for this surface; all 11 carry at least two applicable criteria.

## Findings

### 🔴 F-2026-08-SEC-supabase-01 Any authenticated user can self-grant admin: `profiles.is_admin` is client-writable, defeating every `is_admin(auth.uid())` gate

- **Criterion:** SEC-03 Security-definer RPC and privileged-role hygiene
- **Priority:** P0 · **Cost:** S · **Impact × Likelihood:** 5 × 4 = 20 (Critical)
- **Where:** `packages/supabase/supabase/migrations/20260411000001_core_tables.sql:159-160` (`profiles_update: for update using (user_id = auth.uid())`, no with check); `20260421000000_profiles_is_admin.sql:10-11` (is_admin column) and `:20-28` (is_admin() reads only this column); gate call sites e.g. `20260430000000_analytics_thin_slice.sql:87`, `20260630084718_admin_glyph_moderation.sql:22`, `20260421000003_lab_assets_bucket.sql:22-24`.
- **Why it matters:** `is_admin(uuid)` reads only `profiles.is_admin`, and that column is the single source of truth for all privileged gating (admin moderation RPCs, all 8 analytics RPCs, lab-assets storage writes, unpublished-logs reads). The `profiles` UPDATE policy has no `with check`, no column scope, and no guard trigger, and Postgres implies `WITH CHECK = USING`, so a signed-up user sends `PATCH /rest/v1/profiles?user_id=eq.<own-uid>` with `{"is_admin": true}` and becomes a full operator. Same root policy gap makes `max_media_per_pebble` and the `terms_accepted_at`/`privacy_accepted_at` consent proofs self-writable. This is the same over-authority class the repo already fixed twice (#442 v_ripple, #616 v_pebbles_full), now via a writable capability column.
- **Fix:** Recreate `profiles_update` with `with check (user_id = auth.uid())` plus a BEFORE UPDATE trigger pinning `is_admin`, `max_media_per_pebble`, `terms_accepted_at`, `privacy_accepted_at` to their OLD values on client updates (the pattern `profiles_handle_guard` already uses), or revoke UPDATE on those columns from `authenticated` and mutate them only via a service-role/admin RPC. Add a negative-path harness assertion (self-PATCH `is_admin` must fail) and wire it into CI.
> Verification (CONFIRMED): Full chain independently reproduced. `is_admin` (boolean not null default false) is the only input to `is_admin(uuid)`, which gates every privileged path sampled; those RPCs keep the `authenticated` grant, so a self-granted admin can call them all. `profiles_update` is defined once with no `with check`, no column scope, and grep confirms no later drop/alter/re-emission and no column-level privilege change. The only BEFORE UPDATE triggers are `set_updated_at` and `before update OF handle`, neither of which fires on an is_admin-only update. Aggravating: the public_profiles author explicitly guarded `handle` against this exact write-bypass class but `is_admin` got no CHECK, no trigger, no revoke. Severity 20 stands.

### 🟠 F-2026-08-SAF-supabase-01 No age gate or server-recorded age assurance anywhere; GDPR Art.8 consent-age basis is undemonstrable

- **Criterion:** SAF-06 Age gating and minors protection posture
- **Priority:** P1 · **Cost:** XL · **Impact × Likelihood:** 4 × 4 = 16 (High)
- **Where:** grep age/birth/dob → none in `supabase/migrations`, `types/database.ts`, `apps/`; `20260729120000_handle_new_user_consent.sql:17-28`; `supabase/config.toml` `enable_signup=true`; public_profile default false `20260730120000_public_profiles.sql:29` (not age-derived).
- **Why it matters:** The schema, auth trigger, and all clients contain no age or birthdate field and no attestation. Any EU user, including a child below the Art.8 threshold (13-16; France 15), can create an account with an email or federated login and immediately record intimate emotional content and use social features. This is simultaneously a child-safety failure, an Art.8 consent defect, and a store-policy exposure at the store-launch milestone the roadmap is driving toward.
- **Fix:** Add a server-recorded age gate at signup (birthdate or over-threshold attestation persisted to profiles, refusing under-threshold accounts), map the enforced minimum to each member state's Art.8 age, and if minors are admitted ship a private-by-default minors settings matrix. Commit store age/target-audience metadata and diff it in CI.
> Verification (CONFIRMED): Every evidence item re-verified: no birth/age/dob column anywhere; `handle_new_user` records only terms/privacy timestamps and concedes OAuth signups carry no consent metadata; clients offer only terms+privacy checkboxes with OAuth bypassing even those. One nuance that does not neutralize the claim: `apps/web/docs/terms/en.md:87` and `privacy/en.md:278-284` declare a 13+ minimum with France parental-consent rules, so the product is declared-but-unenforced. Aggravating: privacy policy 10.2 promises parental-consent confirmation "during sign-up" that no flow asks, making the published policy a documented misrepresentation on top of the missing gate. Severity 16 honest.

### 🟠 F-2026-08-TST-supabase-01 No CI check of any kind runs on `packages/supabase` changes — migrations, RPCs, and RLS merge gateless

- **Criterion:** TST-06 No merge without the touched surfaces' gates
- **Priority:** P1 · **Cost:** M · **Impact × Likelihood:** 4 × 4 = 16 (High)
- **Where:** `.github/workflows/` contains only android.yml, android-release.yml, arkaik.yml, lab-note-reminder.yml; `android.yml:9-16` path-filters to `apps/android/**` only; `packages/supabase/package.json:9` (placeholder lint); `docs/quality/audits/2026-08/baseline.md:11`; incident evidence `20260731090000_purge_account_union.sql:1-18` and `20260731090100_fix_admin_set_domain_glyph.sql:4-14`.
- **Why it matters:** No workflow references `packages/supabase`, and the package's own lint is a placeholder echo that exits 0. A PR that edits an RLS policy, re-emits a security-definer function, or ships a migration triggers zero automated checks and merges green by absence, on the one surface all four clients share. Two contract defects already landed through this hole in one month (the `purge_account` re-emission collision, and `admin_set_domain_glyph` carrying a dropped `shape_id` reference for 28 days), both caught by manual reading.
- **Fix:** Add a `supabase.yml` path-filtered on `packages/supabase/**` that runs `supabase start` + `db reset` to prove the chain applies, regenerates types and fails on `git diff`, runs `tsc --noEmit` and `deno check` over `supabase/functions` and `scripts`, and optionally runs the anon-key harnesses against the local stack. Make it a required check.
> Verification (CONFIRMED): Every artifact re-checked. Workflows never gate this surface (the only "supabase" hits are Android build secrets). Lint is literally `echo 'placeholder'`; four edge functions exist with no `deno check`. Both incident migration headers confirm the finding verbatim. No husky/pre-commit hooks, no prepare script, and `apps/web` does not even declare `@pbbls/supabase`, so no indirect Vercel type-check could catch SQL/RLS/migration defects. Aggravating: 19 of 63 migrations landed in a two-month churn and 37 carry security-definer functions, so the ungated surface includes the auth boundary and the GDPR deletion path. Severity 16 honest.

### 🟠 F-2026-08-PLT-supabase-01 No report primitive exists in the database contract while cross-user UGC is live, leaving every client unable to ship the store-required report pillar

- **Criterion:** PLT-04 UGC safety apparatus: filter, report, block, respond
- **Priority:** P1 · **Cost:** L · **Impact × Likelihood:** 5 × 3 = 15 (High)
- **Where:** `packages/supabase/types/database.ts:42-1589` (full table list, no reports table); `20260817130000_pebble_visibility_grades.sql:64-79` and `:99-131` (cross-user and anon read paths); `20260630084718_admin_glyph_moderation.sql:1-52` (glyph-only review queue); `20260729201326_account_deletion_purge.sql:28` (comment naming 'reports' as future).
- **Why it matters:** Three cross-user content paths are live (connection-visible and public pebbles with free-text name/description, marketplace glyphs, public profiles) but there is no reports table, no report RPC, and no moderation queue for user-flagged content. Apple 1.2 and Play's UGC policy require an in-product reporting mechanism wired to an operator queue. Because the database is the contract for all four surfaces, the gap is structural: no client can ship a report button until the server primitive exists.
- **Fix:** Add a reports table (reporter, target kind + id, reason, status) with an insert RPC granted to authenticated, an is_admin-gated queue-read RPC following the `admin_list_glyph_submissions` pattern, extend `purge_account` plus `verify-account-purge.ts` in the same change per the standing rule, then wire report UI on each client screen that renders cross-user content.
> Verification (CONFIRMED): Table and RPC lists contain no reports table and no report/flag RPC; the only moderation apparatus is pre-publication glyph review. Cross-user UGC paths are live. The `purge_account` comment names reports as a known future table. Aggravating factor the auditor did not cite: the repo's own store-launch roadmap (`docs/superpowers/specs/2026-07-28-store-launch-roadmap.md`) lists "no in-app report/block for UGC (Apple 1.2)" as a confirmed store-compliance blocker and plans the exact missing primitive in M56, sequenced before store submission M57. Impact 5 corroborated by the repo's own launch-gate classification; likelihood 3 honest since UGC already reaches testers via Play internal testing. Severity 15 stands.

### 🟠 F-2026-08-GDP-supabase-01 Federated (Apple/Google) signups persist NULL consent and no record binds an accepted document version

- **Criterion:** GDP-01 Consent records and lawful basis
- **Priority:** P1 · **Cost:** M · **Impact × Likelihood:** 3 × 4 = 12 (High)
- **Where:** `20260729120000_handle_new_user_consent.sql:10-12,24-27`; `apps/web/lib/data/useSupabaseAuth.ts:159-172`; `apps/ios/Pebbles/Services/SupabaseService.swift:92-138`; profiles cols `20260411000001_core_tables.sql:26-27`; grep policy_version/terms_version → none.
- **Why it matters:** `handle_new_user()` copies consent timestamps from signup metadata and is deliberately NULL-safe because OAuth carries none, and the OAuth client paths send none, so every Apple/Google account is created with NULL consent and nothing forces a consent step. There is also no policy-version column, so even email consent cannot identify which document version was accepted and a policy bump forces no re-acceptance. The Art.7(1) accountability principle fails for every OAuth account and for version tracking on all accounts.
- **Fix:** Capture and persist consent on the OAuth callback, add an accepted-document-version column bound to each timestamp with a re-consent prompt on version bump, and add a harness asserting non-null consent after email and each OAuth provider signup.
> Verification (CONFIRMED): All three OAuth client paths (web, iOS, Android SupabaseService.kt, an aggravator not cited) send no consent metadata; profiles has only two timestamptz columns; repo-wide grep for policy/terms/privacy/consent_version returns nothing and no code writes consent after signup. No guard neutralizes it: OAuth buttons are gated only on `submitting`, and the login page offers OAuth with zero legal UI. Likelihood is deterministic per OAuth signup (arguably 5); 4 is not overstated. Severity 12 honest.

### 🟠 F-2026-08-PLT-supabase-02 Production auth and API configuration is console-only: no committed snapshot of the redirect allowlist, provider client IDs, OTP expiry, email confirmation, or SMTP

- **Criterion:** PLT-08 Managed database platform configuration
- **Priority:** P1 · **Cost:** M · **Impact × Likelihood:** 4 × 3 = 12 (High)
- **Where:** `packages/supabase/supabase/config.toml:154-156` (local-only site_url and redirect list), `:305-311` (apple disabled, no google section), `:209,:217` (confirmations and OTP on defaults); `docs/decisions/log.md:41-48` (remote-first deploy); `docs/superpowers/specs/2026-04-26-back-office-app-design.md:170` and `docs/superpowers/plans/2026-05-01-ios-apple-google-signin.md` Task 10 (dashboard steps as the only record).
- **Why it matters:** The tracked `config.toml` holds only local dev values while the decision log makes the remote project the primary environment. Every production auth setting lives solely in the dashboard, so nobody can state, review, diff, or restore the production redirect allowlist or provider configuration from the repo. A broad or wildcarded redirect URL added during debugging goes unreviewed indefinitely and enables OAuth token interception; project loss or an accidental change cannot be restored.
- **Fix:** Commit a reviewed snapshot (markdown or IaC) of the production dashboard's auth, storage, and API settings, treat it as the authority with dashboard changes landing as PRs, and add a scheduled drift check via the Supabase management API.
> Verification (CONFIRMED): `config.toml` has only 127.0.0.1 redirects, apple `enabled=false` with empty client_id and no google section, confirmations off, OTP at default, SMTP commented out; no workflow or script introduces config-as-code. Aggravating: drift already occurred (the 2026-04-11 apple-signin spec records the dashboard at a 10-char password policy while config.toml says 8); the tracked file is production-hostile (pushing it would disable Apple and set the allowlist to 127.0.0.1), so it cannot serve as a restoration base; shipped OAuth on all three clients hangs off console-only state. Severity 12 honest.

### 🟠 F-2026-08-REL-supabase-01 Same-function migration re-emissions can silently drop each other's changes and no CI applies the chain or diffs bodies; the failure already occurred once on `purge_account`

- **Criterion:** REL-06 Contract-safe migrations with rollback story
- **Priority:** P1 · **Cost:** M · **Impact × Likelihood:** 4 × 3 = 12 (High)
- **Where:** `20260731090000_purge_account_union.sql:1-18` (incident record); `20260730070347_mutual_connections.sql:376` and `20260730090000_achievements.sql:266` (the colliding re-emissions); `docs/decisions/log.md:433` (second collision, Arkaik nodes); `.github/workflows/` (no db job); `baseline.md:11`.
- **Why it matters:** `purge_account` and `remove_connection` both grow by re-emitting the whole function body with in-body append markers. `create or replace` has no merge semantics and git reports no conflict, so two migrations authored in parallel off the same base silently discard each other's appends. This already happened: `20260731090000` documents that two parallel migrations each re-emitted `purge_account` and the later timestamp won, dropping the three connections deletes from the GDPR erasure function. The only defense today is a prose CLAUDE.md rule written after the incident.
- **Fix:** Add a CI job on migration PRs that applies the full chain to a scratch Postgres, fails when two pending migrations re-emit the same function name without a union marker, and greps new files for destructive DDL requiring a sign-off label. Add "rollback path" as a required migration-PR checklist line.
> Verification (CONFIRMED): Incident record, both colliding re-emissions (`purge_account` re-emitted whole in 5 migrations), the `remove_connection` append marker, and the second same-class collision (#725 Arkaik nodes) all confirmed. No mechanical guard: no workflows touch migrations, no git/husky hooks, lint is a placeholder, `turbo.json` defines no test task. The CLAUDE.md rule landed 2026-08-02, after the 2026-07-31 union fix. Both marked functions are designed to keep growing by whole-body re-emission and parallel-branch authoring is routine, so exposure recurs by design. Severity 12 honest.

### 🟠 F-2026-08-REL-supabase-02 `delete_pebble` strands the pebble's photos in storage on every surface; no cleanup path exists before full account deletion

- **Criterion:** REL-03 Atomic multi-step writes
- **Priority:** P1 · **Cost:** M · **Impact × Likelihood:** 3 × 4 = 12 (High)
- **Where:** `20260411000003_rpc_functions.sql:351-379` (re-emitted `20260411000005_security_hardening.sql:361`); contrast `delete_pebble_media` returning storage_path at `20260426000002_pebble_media_edit.sql:234-249` with web cleanup at `apps/web/lib/data/supabase-provider.ts:541-551`; delete_pebble called with no cleanup at `supabase-provider.ts:773`, `apps/ios/Pebbles/Features/Path/PathView.swift:267`, `apps/android/.../PebbleWriteService.kt:110`; only sweep is `supabase/functions/delete-account/index.ts:69-92`.
- **Why it matters:** `delete_pebble` deletes the pebbles row and cascades snaps but never returns the snaps' `storage_path` and no server-side job removes the objects, so deleting a photo-bearing pebble leaves `{user_id}/{snap_id}/original.jpg` and `thumb.jpg` in `pebbles-media` indefinitely. The single-snap flow proves the team knows the pattern; the whole-pebble flow skips it. In an intimate-records product the user expects deleting the moment to delete its photo; instead the bytes persist, invisible to every surface, until the terminal delete-account sweep.
- **Fix:** Extend `delete_pebble` to return the deleted snaps' `storage_path` array (keeping create/update/delete sibling symmetry) and have each client sweep them like `delete_pebble_media` already does, or add a scheduled orphan sweep. Extend a harness to assert zero orphaned objects after a pebble deletion.
> Verification (CONFIRMED): `delete_pebble` cascades snaps without returning storage_path or touching storage.objects; no migration, trigger, cron, edge function, or admin tool sweeps `pebbles-media` (only delete-account's prefix purge). All client call sites are bare RPC calls; iOS has three such entry points. Aggravating: the gap is documented, deliberate, unpaid debt since April 2026 ("Orphan files accepted in V1... handled by the V2 sweep") with the follow-up issue drafted but no sweep ever landing. Mitigating and score-neutral: the bucket is private with owner-scoped RLS, so orphans are never cross-user visible and are size-capped, which is why impact 3 (not higher) is right and likelihood 4 is honest. Severity 12 honest.

### 🟠 F-2026-08-SAF-supabase-02 Account purge, public-profile flip, and visibility widening gate only on a valid session, not recent re-authentication

- **Criterion:** SAF-07 Account takeover harm ceiling
- **Priority:** P1 · **Cost:** L · **Impact × Likelihood:** 4 × 3 = 12 (High)
- **Where:** `functions/delete-account/index.ts:50-63`; `20260729201326_account_deletion_purge.sql:40-53`; `supabase/config.toml:211` (`secure_password_change=false`); `20260730120000_public_profiles.sql:118-120`.
- **Why it matters:** A stolen or hot session can irreversibly destroy or publicly expose the account with no elevation. `purge_account` is invoked on the forwarded JWT alone and has no recent-auth precondition; making a profile public, claiming a handle, and widening a pebble to public are plain owner-scoped client updates with no server-verified confirmation. `secure_password_change=false` lets a hot session rotate the password without the old one. An attacker with a live token silently purges the journal or flips every secret pebble to public, with no re-auth challenge and no security notification.
- **Fix:** Require recent re-authentication or a server-verified confirmation (a short-lived reauth nonce checked in the RPC/edge function) for purge, public-profile enablement, and bulk visibility widening; enable `secure_password_change` and security-notification templates so credential changes revoke sessions and notify the user.
> Verification (CONFIRMED): The delete-account function gates solely on `auth.getUser()` over the forwarded JWT, then uses the service-role client to purge, wipe storage, and delete the auth user, with no re-auth, confirmation, grace period, or notification; MFA is fully disabled and grep finds zero AAL/MFA/re-auth checks. Pebble widening confirmed via `20260817130000`. Aggravating: the web PWA stores the session including refresh token in localStorage (direct XSS-to-hot-session path); no soft-delete window. Severity 12 honest.

### 🟠 F-2026-08-TST-supabase-02 Cross-user denial is proven for only a handful of application tables and RPCs, with no completeness check and no CI run

- **Criterion:** TST-08 Negative authorization tests in CI
- **Priority:** P1 · **Cost:** L · **Impact × Likelihood:** 4 × 3 = 12 (High)
- **Where:** Covered: `packages/supabase/scripts/verify-pebble-drafts.ts:154-179`, `verify-pebble-visibility.ts` (read matrix + write denial), `verify-public-profile.ts` (anon allowlist). Uncovered set derived from grep `create table` over migrations vs grep of `scripts/` per name; no pgTAP directory, no workflow invocation.
- **Why it matters:** The denial tests that exist are excellent, but coverage is a hand-maintained subset: souls, collections, glyphs, snaps, karma_events, wallet_balances, bounces, the marketplace tables, the connections trio, achievement_unlocks, logs, log_reactions, and direct profiles selects have no automated denial assertion, and `update_pebble`, `delete_pebble`, `remove_connection`, `spend_karma`, and every `admin_*` function have no refusal test. Nothing diffs the covered list against the schema, and the suite runs only when a human remembers. On an intimate-data product where RLS is the entire privacy boundary, an accidental policy widening would be caught by no automation.
- **Fix:** Build a schema-driven denial suite (pgTAP, or extend the Deno pattern) that enumerates tables and definer functions from `information_schema` at runtime, asserts cross-user select/insert/update/delete and anon access per table and non-owner refusal per RPC, and fails on any table/function without a registered expectation. Run in CI on migration changes.
> Verification (CONFIRMED): Systemic claim holds on every load-bearing point: no CI runs any DB harness (scripts are not even wired as npm scripts), no completeness check, no pgTAP, and ~19 tables plus every mutating/admin RPC are unproven. Two evidence corrections that do not move severity: `verify-public-profile.ts:333-344` also asserts cross-user and anon denial on achievement_unlocks and glyphs, and `verify-pebble-visibility.ts:186-194` indirectly proves read denial on pebble_cards/souls/snaps, so "3 of ~30" undercounts slightly. Aggravating: this failure class has already shipped twice (#616 v_pebbles_full served 182 private pebbles across 20 users to the bare anon key; the earlier v_ripple instance). Severity 12 honest.

### 🟡 F-2026-08-REL-supabase-04 No backup, export, or restore posture exists for the database, the two storage buckets, or auth identities

- **Criterion:** REL-07 Backups exist and restore is rehearsed
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 5 × 2 = 10 (Medium)
- **Where:** greps over `docs/` and `packages/supabase` for backup/PITR/point-in-time/restore/retention return no infrastructure statements; `.github/workflows/` has no scheduled export job; buckets at `20260426000001_pebbles_pictures.sql:22-33` and `20260421000003_lab_assets_bucket.sql:9-11` appear in no coverage document.
- **Why it matters:** No statement of the tier's backups, no RPO/RTO, no scheduled dump or storage sync, no auth export, no restore procedure, no rehearsal. Supabase database backups do not cover storage objects, so users' photos in `pebbles-media` have no recovery path of any kind. For a product whose entire value is irreplaceable personal history, an unrecoverable-loss event is the worst-case outcome, and Art.32(1)(c) expects demonstrable restore ability. The strong purge discipline elsewhere makes this the starkest gap: deletion is harness-verified, restoration is entirely unaddressed.
- **Fix:** Document the actual tier and its included backups first; add a scheduled workflow doing a logical dump plus a storage-object sync to independent storage with failure notification; execute one full restore to a scratch project including auth users and bucket objects, date it, and put the rehearsal on a stated cadence.

### 🟡 F-2026-08-ARC-supabase-01 The create-or-replace re-emission collision remains manually policed even though it already fired once and the decision log names the next likely occurrence

- **Criterion:** ARC-08 Migration and schema change quality
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 3 × 3 = 9 (Medium, downgraded from 4 × 3)
- **Where:** `20260731090000_purge_account_union.sql:1-18` (incident record); `docs/decisions/log.md:376-380` ("the next likely collision"); markers at `20260731090000:144` and `20260730070347_mutual_connections.sql:320`; grep of `.github/workflows` shows no detection step.
- **Why it matters:** Two migrations authored in parallel each re-emitted `purge_account` with only their own append; the later timestamp won and silently dropped the other's three connections deletes with no VCS conflict. The repair (manual union, append markers, a documented pairwise-diff procedure) is the L3 practice but pure discipline; the decision log itself states M52/M53 will append to both `purge_account` and `remove_connection` and are the next likely collision. Under the parallel agent-authoring workflow the preconditions recur routinely.
- **Fix:** Add a small CI script on migration PRs that lists `create or replace function` names per changed migration plus history and fails when a batch introduces two emissions of one function, or re-emits a marker-carrying function without the harness file changing in the same PR.
> Verification (DOWNGRADED): Every citation re-verified and the process gap and likelihood 3 are honest. Impact 4 is overstated: the section-(4) append site holds only `auth.users ON DELETE CASCADE` tables by convention, and the sole production caller (the delete-account edge function) ends with `auth.admin.deleteUser`, so even the regressed body left zero residual rows after a successful deletion; the erasure gap exists only in the deleteUser-failure window. The finding also double-counts the harness blind spot (the same cascade both masks the harness and erases the data), and the harness pins the purge RPC's own counts for the connections trio, so an exact recurrence fails loudly when the harness runs. Adjusted severity 3 × 3 = 9.

### 🟡 F-2026-08-GDP-supabase-02 Systematic emotional-state recording has no DPIA, no Art.9 consent naming, and no sensitive-column annotation

- **Criterion:** GDP-02 Special-category data gating and DPIA
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `20260411000001_core_tables.sql:56-60`; grep dpia/'art. 9' `docs/` → quality framework only; `apps/web/app/register/page.tsx:186-221`; `v_analytics_user_averages_weekly` aggregate `20260501000002:82-100`.
- **Why it matters:** `pebbles.intensity/positiveness/emotion_id/description` are stored as ordinary NOT NULL columns with no sensitive-field inventory, column comments, or type wrapper, so the l4 automation (fail CI when an annotated field enters a log/analytics/export sink) is impossible to build. Large-scale processing of emotional/health-adjacent signals is an explicit Art.35(3)(b) DPIA trigger, yet no DPIA or Art.9 memo exists and signup copy names no sensitive data. Mitigating today: operator analytics are aggregate and is_admin-gated and sensitive fields do not reach logs or third-party telemetry.
- **Fix:** Write and date a DPIA against the current feature set, add explicit-consent copy naming the emotional/health-adjacent data at signup/capture, and commit a machine-readable sensitive-column inventory (or column comments) so a CI grep can flag any log/analytics/export addition of an annotated field.

### 🟡 F-2026-08-GDP-supabase-04 No machine-readable data export/portability endpoint (Art.15/20) on any surface

- **Criterion:** GDP-04 Data-subject rights workflows on every client
- **Priority:** P2 · **Cost:** L · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** grep export_/data_export/takeout over `supabase/migrations` and `supabase/functions` → none; purge exists (`20260729201326:40`) but no export counterpart.
- **Why it matters:** Erasure and rectification are self-serve, but there is no export path, so a user cannot obtain a machine-readable copy of everything held about them, and export breadth cannot be diffed against the schema's user-owned tables (so it would silently rot as milestones add tables). A user exercising access/portability forces the controller to hand-assemble an export by ad-hoc SQL, missing tables like pebble_drafts, connections, achievement_unlocks, or media references, and cannot demonstrate completeness. No operator runbook for out-of-band Art.12(3) requests exists.
- **Fix:** Build a schema-driven export RPC/edge function covering all user-owned tables plus media references so breadth cannot silently rot, expose an entry point on each client, and write the operator runbook for out-of-band rights requests.

### 🟡 F-2026-08-GDP-supabase-06 No code-derived processor inventory, DPA references, or committed region pinning

- **Criterion:** GDP-06 Processor inventory, DPAs, and transfers
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** grep processor/DPA `docs/` → none; `supabase/config.toml` (no region key; `network_restrictions` `allowed_cidrs 0.0.0.0/0`); no fetch to external hosts in `supabase/functions`.
- **Why it matters:** No processor inventory or DPA reference is committed and region is not pinned. The processor chain (Supabase backend/storage, Vercel hosting, Apple/Google OAuth, Deno edge runtime) is derivable from code but nowhere mapped to a DPA, data category, or contracted region, so a post-Schrems II transfer of intimate data cannot be shown to ride a documented Chapter V mechanism, and no automated check would catch a new SDK or egress. Positive: edge functions add no unlisted egress.
- **Fix:** Write a processor inventory from a code-derived dependency/endpoint sweep with a DPA reference and region per processor, pin EU regions where the platforms offer it, and add a dependency-review check that flags new SDKs/endpoints for classification.

### 🟡 F-2026-08-PLT-supabase-03 Apple-created accounts are stranded on Android: the provider set differs across surfaces and no password-recovery escape hatch exists on any client

- **Criterion:** PLT-03 Sign-in options meet platform equity rules
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `apps/android/.../components/GoogleSignInButton.kt:32` ("No Apple sign-in on Android, settled non-goal"); `apps/web/lib/data/useSupabaseAuth.ts:159-176` and `apps/ios/.../SupabaseService.swift:92-138` (Apple offered on web and iOS); grep `resetPassword|recover|forgot` across all clients → zero hits.
- **Why it matters:** Web and iOS offer Sign in with Apple; Android deliberately does not, and no client implements password reset or magic-link. A user who signs up with Apple has no password on the shared auth backend, so that identity is portable web to iOS but dead on Android. Because all four surfaces share one auth backend, this is a contract asymmetry: an iPhone user who signed up with Apple and moves to Android is offered only email+password (never set) and Google (a different identity), locking them out of their history or creating a duplicate account.
- **Fix:** Either add Apple sign-in on Android through Supabase's hosted OAuth (no native SDK required, mirroring the iOS Google approach), or ship a password-recovery/magic-link flow on Android and web so an OAuth-only account can establish a password; record the chosen portability story in `docs/decisions/log.md`.

### 🟡 F-2026-08-PRF-supabase-01 All 77 RLS policies evaluate `auth.uid()` bare, per row; none use the `(select auth.uid())` initplan form the repo's own skill prescribes

- **Criterion:** PRF-04 Indexes match access paths and RLS predicates
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `20260411000001_core_tables.sql:155-290` (all core policies bare); `20260817130000_pebble_visibility_grades.sql:64-77` (pebbles_select OR-chain with per-row `auth.uid()` in least/greatest plus connections EXISTS); `20260630003348_glyph_marketplace.sql:70-105` (two EXISTS probes per row); grep `(select auth.uid())` over migrations = 0 vs 157 bare; prescribed form at `.agents/skills/supabase-postgres-best-practices/references/security-rls-performance.md:25`.
- **Why it matters:** Every policy uses bare `auth.uid()`. On plans where the predicate cannot become an index condition (the OR-disjunct `pebbles_select` and `glyphs_select`, and junction-table EXISTS policies), the function is re-evaluated and the subqueries re-executed per candidate row. `pebbles_select` is the worst case, running `least/greatest(auth.uid(), ...)` plus a connections EXISTS probe per non-owner row on the hottest table. Supabase's advisor flags exactly this (`auth_rls_initplan`); the repo vendored the skill documenting the fix, yet zero policies apply it, and the degradation hits all four clients simultaneously once real volume arrives.
- **Fix:** One corrective migration re-emitting every policy with `(select auth.uid())` (and `(select public.is_admin(auth.uid()))` where applicable), verified by running the performance advisor against the linked project and committing the clean report; add the advisor run as a scripted harness.

### 🟡 F-2026-08-PRF-supabase-02 Unindexed foreign keys and hot filter columns concentrate on the delete, edit, and purge paths, including the karma ledger scanned on every pebble edit and delete

- **Criterion:** PRF-04 Indexes match access paths and RLS predicates
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** ref_id filters `20260411000003_rpc_functions.sql:336,368` and `20260729140000_media_quota_profile_lookup.sql:424`; index inventory `20260411000001_core_tables.sql:296-305`, `20260629192621_karma_events_type_axis.sql:28-31` (no ref_id index); `collection_pebbles` PK `20260411000001:117-121` cascade at `:119`; purge scans `20260731090000_purge_account_union.sql:57-71,88-96,108,152-160`.
- **Why it matters:** `karma_events.ref_id` is filtered on every `update_pebble` and `delete_pebble` but has no index, and it is the append-only ledger, so the seq scan grows monotonically. `collection_pebbles.pebble_id` trails its composite PK, so every deletion cascade scans the whole table. Other unindexed FK columns sit exactly where `purge_account` and the auth cascades read, so the GDPR erasure path degrades superlinearly with ledger and content size under the default `statement_timeout` with no override, making a large account's erasure aborting on timeout the plausible end state.
- **Fix:** Single migration adding indexes on `karma_events(ref_id)`, `collection_pebbles(pebble_id)`, `pebble_souls(soul_id)`, `snaps(user_id)`, `glyph_entitlements(karma_event_id)`, `connection_blocks(blocked_id)`, `glyph_submissions(submitter_id)` and `(reviewed_by)`, `pebbles(glyph_id)`, `souls(glyph_id)`. Then run the advisor and commit the clean `unindexed_foreign_keys` result as the baseline.

### 🟡 F-2026-08-PRV-supabase-01 No PII inventory / ROPA exists in the repo, so no personal-data field is traceable to a documented purpose

- **Criterion:** PRV-01 PII inventory and schema minimization
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** grep over `docs/` for inventory/PII/personal data/ROPA/data map finds only incidental mentions; personal-data columns enumerable from `packages/supabase/types/database.ts` and `20260411000001_core_tables.sql:20-99` carry no purpose annotation.
- **Why it matters:** There is no data inventory, ROPA, or personal-data map. The schema accumulates intimate columns (pebble name/description free text, emotion + valence, media pointers, intimate happened_at timestamps, souls names of third parties, connection graph) with no per-field purpose record, and new columns land without a purpose marker. Per the PRV-01 checklist this caps the criterion at level 1 outright. No one can mechanically answer what personal data is held, why, and where it flows, which is the precondition for the GDP domain's Art.9 special-category posture.
- **Fix:** Author a committed data inventory / ROPA mapping each personal-data column and cross-surface payload to a purpose and lawful basis, flag the special-category fields (emotion/valence), and add a review step (or a CI diff of generated database types against the inventory) so new personal-data columns must be listed with a purpose in the same change.

### 🟡 F-2026-08-REL-supabase-03 Edge-function and database failures terminate in unmonitored Supabase logs with no alerting, including failures of the account-erasure path

- **Criterion:** REL-08 Production failures reach a human
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** terminal `console.error` handlers `functions/delete-account/index.ts:54,65,87,97`; `compose-pebble/index.ts:45,60,71`; `compose-pebble-update/index.ts:51,67,78`; `backfill-pebble-render/index.ts:32,48,61`; soft-success contract `compose-pebble/index.ts:70-81`; grep sentry|crashlytics|bugsnag|posthog|datadog|rollbar over manifests → zero.
- **Why it matters:** All four edge functions log labeled context, but the destination is the Supabase function-log dashboard, which no document, routine, or alert rule connects to a human. A sustained run of `delete-account` 500s would go unnoticed until a user complains, and failed erasure carries legal exposure with deadlines. Compose failures are designed as soft-success with `backfill-renders.ts` as recovery, but nothing surfaces the failure rate that would prompt anyone to run it, so text-only pebbles can accumulate silently.
- **Fix:** Pick one destination (Supabase log drain to a watched channel, or a Sentry project wired into the four functions) with PII scrubbing configured before first deploy; add an error-rate alert for `delete-account` and `compose-pebble`; document who triages and on what cadence; fire one deliberate test error per function to prove the loop closes.

### 🟡 F-2026-08-SAF-supabase-03 Display names and public/shared pebble text are served cross-user and anonymously with no moderation state and no report or takedown path

- **Criterion:** SAF-03 UGC moderation state machine and takedown
- **Priority:** P2 · **Cost:** L · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `20260730070347_mutual_connections.sql:347-362`; `20260730120000_public_profiles.sql:242-281`; `20260817130000_pebble_visibility_grades.sql:106-128`; no reports/moderation_queue table (grep).
- **Why it matters:** Only glyphs have a moderation state machine. `display_name` is free text projected to connections and anonymously on public profiles, and public pebble name+description are served to anonymous visitors via `get_shared_pebble` and to connections via `v_pebbles_full`, all with no status/review column and no server-side gating on a moderation state. No report queue or takedown RPC exists for these types, so a user can set `display_name` to a slur or impersonation, or write abusive text in a public-link pebble, and an operator can only remove it by manual SQL, so a DSA notice-and-action or store UGC obligation cannot be met for the majority of cross-user content.
- **Fix:** Add a moderation status to cross-user-served content (display_name, publicly shared pebble text) with server-side status-gated serving, a report table feeding an operator queue, and admin-checked security-definer takedown RPCs that write an audit row; encode the reachability rule as a DB harness.

### 🟡 F-2026-08-SAF-supabase-04 Block oracle: preview returns 'valid' while accept returns 'expired', letting a blocked user confirm the block

- **Criterion:** SAF-04 Block integrity and anti-harassment enforcement
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `20260730070347_mutual_connections.sql:228-233` (accepted-residual comment), `168-198` (preview valid path), `218-240` (accept expired masking).
- **Why it matters:** The design documents an accepted residual: `preview_connection_invite` is block-unaware and returns 'valid', while `accept_connection_invite` masks a block as 'invite_expired'. A signed-in blocked peer who previews (valid) then accepts (expired) can distinguish this from a genuinely expired invite (which previews as 'expired' too), inferring they were specifically blocked. In an intimate-journal product the abuser may be a named person in the victim's entries, so a block oracle that confirms the block to the abuser is a real escalation risk for users leaving abusive relationships.
- **Fix:** Make `preview_connection_invite` block-aware for authenticated callers so a blocked caller receives the same dark shape as an expired invite, normalizing the preview/accept pair; add a block-matrix contract harness covering both directions and all read/contact paths.

### 🟡 F-2026-08-SEC-supabase-02 RLS/purge/cross-user verification harnesses exist but none run in CI, so no policy or purge regression is mechanically caught

- **Criterion:** SEC-02 Row-Level Security default-deny on every table
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `baseline.md` (supabase lint = placeholder exits 0; no supabase test task); `.github/workflows/` contains no job invoking `packages/supabase/scripts/verify-*.ts`; harnesses at `verify-account-purge.ts`, `verify-pebble-visibility.ts`, `verify-public-profile.ts` run against the remote project via env vars only.
- **Why it matters:** The crown-jewel invariants of this surface are all proven only by harnesses run by hand against the linked project. A future migration that adds a user-owned table and forgets the purge entry, widens an RLS policy or a cross-user projection, or re-introduces the `v_pebbles_full` definer-view class fails no pipeline, because `rowsecurity=true` is never asserted in CI, the FK-vs-purge table set is never diffed, and the field-set harnesses are not gated. This is what holds SEC-02, SEC-03, PRV-07, and PRV-08 at level 3 instead of 4, and it is the exact mechanism by which the `is_admin` self-escalation went unnoticed.
- **Fix:** Add a scheduled or PR CI job (against a disposable linked project or `supabase db start`) that runs the four verify harnesses plus a `pg_tables` assertion (`rowsecurity=true` for all public application tables) and an `information_schema` check that every table with a user-id FK appears in `purge_account`. Fail the build on any violation.

### 🟡 F-2026-08-TST-supabase-03 `verify-account-purge` cannot detect a dropped delete line for 4 of the 8 auth.users-CASCADE tables, and log_reactions is asserted but never seeded

- **Criterion:** TST-04 Runnable harnesses for destructive cross-cutting operations
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `packages/supabase/scripts/verify-account-purge.ts:301-317` (expectedPurged pins only 4 tables; rationale `:302-306`), `:336` (log_reactions asserted), §2 seed has no log_reactions insert; cascade FKs `20260730090000_achievements.sql:143`, `20260421000002_log_reactions.sql:12`, `20260629193636_wallet_balances.sql:5`, `20260501000004_bounces_and_analytics_distribution.sql:34`.
- **Why it matters:** All section-(4) purge tables cascade from auth.users, so by the time the zero-row assertions run (after deleteUser) the cascade empties them even if `purge_account` lost the delete line. The authors pin the RPC's own counts, but only for pebble_drafts and the connections trio; achievement_unlocks, wallet_balances, and bounces have counts printed but not asserted, and log_reactions is never seeded so its zero-row check is vacuous. A future re-emission that drops any of those four delete lines (the #687 failure class this harness exists to catch) passes green.
- **Fix:** Extend `expectedPurged` to pin achievement_unlocks (>= 1), wallet_balances (1), and bounces (1); seed one log_reactions row via the service-role client and pin its count too.

### 🟡 F-2026-08-TST-supabase-04 `delete_pebble`, `remove_connection`, and the moderation takedown RPCs have no verification harness

- **Criterion:** TST-04 Runnable harnesses for destructive cross-cutting operations
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 3 = 9 (Medium)
- **Where:** `ls packages/supabase/scripts/` shows five scripts, none covering these RPCs (grep for delete_pebble/remove_connection/reject_glyph/admin_delete_glyph in `scripts/` → nothing); remove_connection marker `20260730070347_mutual_connections.sql:320-324`; `docs/decisions/log.md:378` names remove_connection in the next-collision warning.
- **Why it matters:** Of the destructive operations, only account purge, drafts, and visibility have harnesses. `delete_pebble` (cascades pebble_cards/souls/domains/snaps/collection_pebbles and karma clawback), `remove_connection` (two-sided sever plus optional directed block, with not_found masking), and the moderation takedowns (`reject_glyph`, `admin_delete_glyph`, `set_glyph_listed=false`, which must change marketplace visibility without stranding buyers' entitlements) rest entirely on reading the SQL. `remove_connection` is most urgent: it carries the same append marker as `purge_account` and is named in the next-collision warning, but unlike `purge_account` it has no harness to turn a re-emission collision from silent into loud.
- **Fix:** Add a verify-connections-sever harness (connect two users, remove with and without `p_block`, assert connection gone for both, block directionality, not_found for a third user) before M52/M53 land; add takedown coverage (seed a listed glyph with a buyer, delist/reject, assert visibility and entitlement survival) and a `delete_pebble` cascade check.

### 🟡 F-2026-08-AGT-supabase-04 The sanctioned dev loop writes to the production Supabase project, with only the `logs` table under an explicit no-write rule

- **Criterion:** AGT-07 Least privilege for agents and automation
- **Priority:** P2 · **Cost:** L · **Impact × Likelihood:** 4 × 2 = 8 (Medium)
- **Where:** `docs/decisions/log.md:52-60` (2026-05-26 remote-first decision); `packages/supabase/scripts/verify-account-purge.ts:1-40` (service-role harness against the linked project); `CLAUDE.md:169` (the only ban); `CLAUDE.md:35` (harnesses run against the linked project).
- **Why it matters:** By recorded decision, migrations, edge functions, and DB changes are tested against the remote (production) project rather than local containers: `db:push` applies migrations directly, and the service-role harnesses seed and purge throwaway users on the same database that holds real users' intimate data. The only instruction-level ban on production writes covers the `logs` table; there is no staging project, no written boundary listing sanctioned production writes, and an agent handed the service-role key holds full production write capability for the whole session.
- **Fix:** Stand up a staging project or Supabase branch database for harness runs and migration rehearsal, and add a standing rule naming exactly which operations may touch the production project and under whose hands; keep the service-role key out of routine agent sessions.

### 🟡 F-2026-08-GDP-supabase-03 No incident-response runbook and no operator audit-log table to scope or notify a breach within 72 hours

- **Criterion:** GDP-08 Breach detection and response readiness
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 4 × 2 = 8 (Medium)
- **Where:** grep breach/incident `docs/` → none; audit trail limited to `20260630084718_admin_glyph_moderation.sql:72-73`; admin RPCs write no audit row (`20260730150000_admin_achievement_management.sql`, `20260717120000_admin_emotion_management.sql`, `20260703000000_admin_domain_management.sql`).
- **Why it matters:** There is no incident runbook and no general audit-log table: privileged mutations write no actor+timestamp trail beyond `reviewed_by/reviewed_at` on glyph_submissions, and the admin achievement/emotion/domain RPCs write no audit row. On a breach involving the emotional-content tables, the operator cannot enumerate affected users and records for a given table and window from any in-app trail, has no notification templates or processor chain, and cannot demonstrate the Art.33 72-hour flow. Positive: service_role appears in zero client sources and admin RPCs enforce is_admin server-side, so the gap is documentation and audit trail, not key leakage.
- **Fix:** Write the incident runbook (roles, Art.33 72-hour flow, Art.34 criteria/channel, notification templates, processor chain) and add an audit-log table that every admin/privileged RPC writes to with actor and timestamp; prewrite affected-user scoping queries and date-stamp one tabletop drill.

### 🟡 F-2026-08-GDP-supabase-05 No retention schedule and no scheduled pruning; append-only categories accumulate indefinitely

- **Criterion:** GDP-07 Enforced retention schedules
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 2 × 4 = 8 (Medium)
- **Where:** grep pg_cron/cron.schedule → none; `20260730070347_mutual_connections.sql:47-63`; `20260729213348_pebble_drafts.sql:29-35`; `20260411000001_core_tables.sql:92-99`.
- **Why it matters:** No retention document exists and no scheduled cleanup runs. `karma_events` is append-only, `connection_invites` retains expired/revoked rows forever (only `revoked_at` is set), and `pebble_drafts` have no TTL. The only deletion is on-demand. Over months these tables grow without bound and superseded drafts and dead invite tokens (plaintext capabilities) persist far beyond their purpose, breaching storage limitation Art.5(1)(e); nothing breaks, so the accumulation is silent. Backup retention is also undocumented and unreconciled with the erasure story.
- **Fix:** Write a per-category retention schedule and implement scheduled pruning (pg_cron or a scheduled edge function) for expired connection_invites, stale drafts, and any capped log/event categories; add a periodic oldest-row check so silent accumulation becomes a failing signal.

### 🟡 F-2026-08-PLT-supabase-04 No key inventory or rotation runbook: the service_role key is used from dev machines, CI, and edge functions with no documented map of where keys live

- **Criterion:** PLT-08 Managed database platform configuration
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 4 × 2 = 8 (Medium)
- **Where:** `packages/supabase/scripts/verify-account-purge.ts:32-40` and `scripts/backfill-renders.ts:16-23` (service_role from env on dev machines); `supabase/functions/_shared/supabase-client.ts:10,32-36`; `.github/workflows/android.yml:55-56` and `android-release.yml:82-83` (SUPABASE_ANON_KEY secrets); `apps/web/.env.local.example:3-4`; grep `rotat` across `docs/` and `packages/supabase` found no key runbook.
- **Why it matters:** The service_role key is required on maintainer machines to run harnesses against the linked production project, is auto-present in edge functions, and the anon key is baked into Actions secrets, Vercel env, the web bundle, and APKs. No document enumerates the issued keys or where each lives, and no rotation runbook exists. Naming drift compounds it (`NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` described as `<your-anon-key>` while CI calls it `SUPABASE_ANON_KEY`). A leaked service_role key from a laptop or shell history makes rotation slow and partial, prolonging a window in which the key bypasses all RLS on intimate data.
- **Fix:** Write a short key runbook listing each issued key, every location it is stored, the exact rotation steps per location, and verification steps; prefer migrating to the new publishable/secret key pairs which support zero-downtime rotation, and reconcile the env var naming.

### 🟡 F-2026-08-A11Y-supabase-01 Reference catalog is not version-controlled, so per-locale label completeness is unverifiable and slug drift is undetected

- **Criterion:** A11Y-07 Localization completeness and locale-safe formatting
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 2 × 3 = 6 (Medium)
- **Where:** `20260506000000_emotion_categories.sql:9-11` (category data "populated manually in Supabase Studio"); `20260717000000_emotion_categories_shaded_dark.sql:10-14`; `20260415000001_remote_pebble_engine.sql:55-61`; `20260509000002_emotions_picker_data.sql:26-65` vs `20260411000000_reference_tables.sql:63-79`; `apps/web/lib/i18n/useReferenceCatalog.ts:11-14,26-30`.
- **Why it matters:** The production emotion/domain vocabulary that clients localize by slug lives only in the linked project, not in migrations (emotion_categories rows and the 38-emotion set are hand-entered in Supabase Studio; migrations UPDATE-by-slug against rows they never INSERT, or rely on rows added out-of-band). Because the source of truth is not in the repo, no harness or CI step can assert every DB slug has a name/label key in every client's every locale, and the web fallback silently renders the English DB `name` when a slug is missing, a mixed-language screen for FR users invisible to any single-surface test.
- **Fix:** Move the authoritative emotion_categories + full emotion + domain seed into an idempotent migration (`insert ... on conflict (slug) do update`) so the catalog is version-controlled, then add a completeness harness/CI check that diffs the DB slug set against each client's per-locale key set and fails on any slug missing a key in any supported locale.

### 🟡 F-2026-08-A11Y-supabase-02 No inclusive-language guideline and no recorded review of the server-side emotion/domain vocabulary framing

- **Criterion:** A11Y-08 Inclusive language and emotional vocabulary
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 2 × 3 = 6 (Medium)
- **Where:** repo-wide grep for inclusiv|non-judgment|non-clinical|tone of voice|gendered|epicene|copy guideline matches only `docs/quality/**`; no vocabulary-framing entry in `docs/decisions/log.md`; `20260703000000_admin_domain_management.sql:61-83` and `20260717120000_admin_emotion_management.sql:128-151` are the only vocabulary-editing paths, gated only by is_admin.
- **Why it matters:** The emotion, domain, and category labels stored server-side are neutral and non-clinical today, but nothing documents an inclusive-language/tone standard for them or records that the vocabulary was reviewed for non-judgmental, non-pathologizing, non-gendered framing. Additions and edits pass through no framing-review gate, so a future addition could introduce clinical or judgmental language with no check. Per the A11Y-08 checklist the guideline's absence caps the criterion at level 1 regardless of how clean the current vocabulary is.
- **Fix:** Write a short per-locale inclusive-copy guideline covering the emotion/relationship/domain vocabulary, record a one-time review of the current catalog against it in the decision log or a spec, and add a recorded review step (or a deny-list check) before any migration/admin RPC that adds or renames a vocabulary entry.

### 🟡 F-2026-08-AGT-supabase-01 `packages/supabase` lint is a placeholder that exits 0 while checking nothing, yet root guidance names it as the workspace proof

- **Criterion:** AGT-03 Provable changes: fast agent verification loops
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 2 × 3 = 6 (Medium)
- **Where:** `packages/supabase/package.json` (`'lint': 'echo placeholder, no lint step yet'`); `docs/quality/audits/2026-08/baseline.md:11`; `CLAUDE.md:44` (workspace-scoped lint named for packages/supabase).
- **Why it matters:** The lint script is an echo that always succeeds, and the root CLAUDE.md task-size triage tells agents to run `npm run lint --workspace=packages/supabase` as the scoped check. An agent following instructions gets a green turbo summary and reasonably believes SQL and TypeScript were checked; nothing ran. This converts the documented verification loop into false assurance, which is worse than an absent loop because it terminates the agent's search for proof.
- **Fix:** Either wire a real check (eslint over `scripts/` and `src/`, optionally sqlfluff over migrations) or make the script exit nonzero with a clear "no lint exists, run build instead" message so it cannot pass as proof.

### 🟡 F-2026-08-AGT-supabase-02 The highest-impact DB standing rules have no mechanical backstop: purge coverage, catalog resync, and function re-emission are prose-only defenses

- **Criterion:** AGT-04 Dangerous operations flagged where agents read
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 2 = 6 (Medium)
- **Where:** `CLAUDE.md:81-83` (the three rules); grep `create or replace function public.purge_account` returns 5 files; grep of `.github/workflows` for purge_account → nothing; `docs/decisions/log.md:371` (#687 incident); `packages/supabase/scripts/verify-account-purge.ts:25-27`.
- **Why it matters:** Three root standing rules guard silent-corruption hazards (purge coverage lockstep with the harness, reference-table inserts re-running `sync_achievement_catalog`, manual union of same-function re-emissions). All three rely entirely on an agent having loaded and obeyed the rule; no CI diffs the purge function's table list against the harness's assertions, no check pairs catalog inserts with a resync call, and nothing detects two pending migrations re-emitting one function. A milestone that adds a user-owned table, misses the rule, leaves intimate rows behind, and the harness passes because it never learned about the table.
- **Fix:** Add a small CI script on migration PRs that (a) extracts the table list from the latest `purge_account` body and diffs it against the tables asserted in `verify-account-purge.ts`, (b) fails when a migration inserts into emotions/domains without `sync_achievement_catalog()` in the same file, and (c) warns when a PR batch contains two re-emissions of one function name.

### 🟡 F-2026-08-AGT-supabase-03 No CI detects drift between migrations and the committed generated `types/database.ts`

- **Criterion:** AGT-05 Scripts over tribal knowledge
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 2 = 6 (Medium)
- **Where:** `AGENTS.md:2-9` (regen mandate); `packages/supabase/package.json` `db:types`; no workflow with a `packages/supabase` paths filter; `packages/supabase/CLAUDE.md:48` (TS1434 incident narrative).
- **Why it matters:** AGENTS.md mandates regenerating and committing `database.ts` after any migration, and the regen is scripted, but nothing verifies the committed file matches the schema: no workflow triggers on `packages/supabase/**` and no job regenerates types and runs `git diff --exit-code` (the criterion's own l4 example). The workspace has already been bitten by a corrupted `database.ts` (the TS1434 stderr-leak incident that broke every TypeScript consumer and Vercel deploys), showing both that drift happens and that its blast radius spans web and admin.
- **Fix:** Add a CI job on `packages/supabase/**` that spins up the Supabase CLI, applies migrations, runs the committed `db:types` script, and fails on `git diff --exit-code types/database.ts`, so the same entry point runs locally and in CI.

### 🟡 F-2026-08-ARC-supabase-02 Edge functions and the verification harnesses are type-checked by nothing in the repo

- **Criterion:** ARC-03 Strict typing and exhaustiveness discipline
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 2 × 3 = 6 (Medium)
- **Where:** `packages/supabase/tsconfig.json` (`'include': ['src','types']`); `packages/supabase/src/index.ts` (single re-export line); package.json scripts contain no deno check/test entry; `baseline.md:10`; deno-lint-ignore usage at `supabase/functions/_shared/compose-and-write.ts:52,82`.
- **Why it matters:** `tsconfig.json` includes only `["src","types"]`, so the workspace build covers a one-line re-export plus the generated file, while the code that actually runs (four edge functions, the shared engine, five contract harnesses) is Deno code checked by no configured command. `deno run` does not type-check by default, so a type error in `verify-account-purge.ts` or `delete-account/index.ts` surfaces only at invocation time, which for the harnesses is the exact moment they are needed as proof after a purge-touching batch, and a broken harness that fails to run is easily mistaken for a transient environment problem.
- **Fix:** Add package scripts `check:functions` and `check:scripts` running `deno check` over `supabase/functions/**/index.ts` and `scripts/*.ts`, fold them into the workspace build or lint task, and run them in the supabase CI workflow once it exists.

### 🟡 F-2026-08-REL-supabase-05 `create_pebble` has no idempotency guard, so a retried or double-fired compose-pebble call duplicates the pebble and double-credits karma

- **Criterion:** REL-04 Idempotence and double-submit protection
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 2 × 3 = 6 (Medium)
- **Where:** latest `create_pebble` emission `20260817130000_pebble_visibility_grades.sql:144-333` (server-minted id `:235-251`, unconditional karma insert `:328-329`, client-supplied snap ids accepted `:306-313`); no unique constraints on pebbles `20260411000001_core_tables.sql:51-64`; edge function forwards any POST `supabase/functions/compose-pebble/index.ts:55-57`.
- **Why it matters:** `create_pebble` mints a fresh pebble id server-side and unconditionally emits a `pebble_created` karma event; pebbles has no unique constraint beyond the PK and the RPC accepts no idempotency key. A timeout-then-retry or double submit creates two pebbles and credits karma twice (wallet and bounce triggers fire per event). When the payload carries client-id snaps, the retry instead fails on the snaps PK and rolls back, which accidentally protects photo-bearing pebbles but proves the mechanism is simply unused for the pebble row. Every newer mutation names its guard, making the top-volume mutation the outlier; client-side pending guards are the only current protection, which the criterion discounts.
- **Fix:** Accept an optional client-generated pebble id in the payload (the pattern `snaps[].id` already uses), insert with `on conflict (id) do nothing` and return the existing id on replay; add a replay-twice assertion. Keep sibling symmetry by documenting the same key on `update_pebble`.

### 🟡 F-2026-08-SAF-supabase-05 `emotion_first` achievements condition the unlock and its karma reward on which emotion the user records

- **Criterion:** SAF-02 Emotionally safe engagement mechanics
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 2 × 3 = 6 (Medium)
- **Where:** `20260730090000_achievements.sql:219-221` (emotion_first reads emotion_id), `240-246` (karma paid on unlock); neutral karma helper by contrast `20260411000005_security_hardening.sql:13-42`.
- **Why it matters:** SAF-02 forbids rewards conditioned on emotion choice. `check_achievements()` qualifies the `emotion_first` family by matching the badge's `emotion_id` against the user's `pebbles.emotion_id` and pays each unlocked badge's karma_reward, so recording a pebble tagged with a specific emotion (including negative ones like sadness, anger, fear, grief) mints a badge and pays karma. This gamifies emotional disclosure by emotion type and fails the criterion's l4 signal. A user could record emotions they are not genuinely feeling to complete the collection and earn karma. Impact is bounded because there is no loss framing, no inactivity penalty, and the karma rewards default to 0 unless an admin prices them.
- **Fix:** Either drop the `emotion_first` family or re-key it off neutral activity, or document an explicit, reviewed exemption in the design record explaining why per-emotion first-time awareness is safe; encode a test that fails when an achievement/karma trigger references emotion/valence/intensity columns.

### 🟡 F-2026-08-SEC-supabase-03 Client-facing edge functions use wildcard CORS and parse unbounded request bodies with no size cap or rate limiting

- **Criterion:** SEC-08 Server endpoint and webhook hardening
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 2 = 6 (Medium)
- **Where:** `supabase/functions/compose-pebble/index.ts:26-30` (CORS '*'), `:42-47` (req.json() with no size guard); `compose-pebble-update/index.ts:33-37,47-53`; `delete-account/index.ts:29-33,50-56`; snap quota enforced only later in create_pebble `20260817130000:293-302`.
- **Why it matters:** `compose-pebble`, `compose-pebble-update`, and `delete-account` all set `Access-Control-Allow-Origin: '*'` and call `await req.json()` with no content-length or byte-size guard before parsing, and no rate limiting on the expensive compose/purge paths. A caller posts a multi-megabyte or deeply nested JSON body to `compose-pebble`; the function buffers and parses it fully before any DB-side quota (which lives in `create_pebble`, after the body is parsed), giving a cheap memory/compute amplification vector. Wildcard CORS is lower-risk here because these endpoints authenticate via a bearer, not cookies, but still permits any origin to drive the credentialed endpoint from a victim's browser session if a token is present.
- **Fix:** Add a shared body-size guard constant applied before `req.json()` in every client-facing function; replace wildcard CORS with an allowlist of known app origins; add per-user rate limiting (or document reliance on platform abuse protection) on compose and delete-account; add 401/413 rejection tests.

### 🟡 F-2026-08-SEC-supabase-04 No dependency vulnerability monitoring, a mutable `@main` action ref, and edge functions with no deno lockfile

- **Criterion:** SEC-07 Dependency and build pipeline integrity
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 2 = 6 (Medium)
- **Where:** `.github/workflows/lab-note-reminder.yml` (`uses: alexisbohns/ariko/...@main`); no `.github/dependabot.yml` or `renovate.json`; `packages/supabase/supabase/functions/*/index.ts` import `deno.land/std@0.224.0` and `esm.sh/@supabase/supabase-js@2`; no `deno.lock` found under `packages/supabase`.
- **Why it matters:** No dependency vulnerability monitoring exists (no dependabot, renovate, npm audit, or osv-scanner gate), so a newly disclosed CVE in a pinned dependency is never surfaced. A reusable workflow is pinned to a mutable branch ref (`@main`), so its code can change under the pipeline without review. The edge functions import remote modules with no committed `deno.lock`, so the `@2` major-range and transitive resolution are not integrity-pinned and can drift between deploys. An upstream compromise or breaking change in the `@main` workflow or the unpinned esm.sh graph reaches CI/edge deploys with nothing to catch it.
- **Fix:** Add dependabot or renovate for npm + GitHub Actions; SHA-pin third-party and reusable-workflow actions (replace `@main`); commit a `deno.lock` for the edge functions and pin `supabase-js` to an exact version; optionally add an npm audit / osv-scanner CI gate that fails on high-severity advisories.

### 🟡 F-2026-08-TST-supabase-05 Cross-surface read projections (`get_connections`, `path_pebbles`, `get_profile_engagement`) have no contract test at the database surface

- **Criterion:** TST-02 Shared shapes tested against real cross-surface payloads
- **Priority:** P2 · **Cost:** M · **Impact × Likelihood:** 3 × 2 = 6 (Medium)
- **Where:** grep of `packages/supabase/scripts/` for get_connections/path_pebbles/get_profile_engagement → nothing; projection shapes `20260730070347_mutual_connections.sql:338-366` (get_connections), `20260519141000_path_pebbles_positiveness.sql`, `20260516104231_profile_glyph_and_engagement.sql`; standing rule `docs/decisions/log.md:321` and root CLAUDE.md.
- **Why it matters:** The write-side shapes and two anon projections are contract-tested end to end, but the jsonb projections three hand-written clients decode on every screen open (`get_connections`, `path_pebbles` rows, `get_profile_engagement`) are asserted nowhere. A future re-emission that renames a key, changes `connected_at` precision, or nulls the peer object differently would type-check cleanly (jsonb returns are untyped in `database.ts`) and break iOS/Android/web decoding at runtime with no test failing anywhere. The repo's own standing rule names the harness as the proof mechanism, so this is a rule-compliance gap.
- **Fix:** Extend `verify-pebble-visibility` (or a new verify-projections harness) to call `get_connections` and `path_pebbles` as a seeded user and assert the exact key sets and timestamp forms, mirroring the `PROFILE_KEYS` allowlist pattern from `verify-public-profile.ts`.

### 🟡 F-2026-08-TST-supabase-06 The harness suite has no aggregate entry point — `packages/supabase` defines no test script

- **Criterion:** TST-07 One canonical test framework and idiom per surface
- **Priority:** P1 · **Cost:** S · **Impact × Likelihood:** 2 × 3 = 6 (Medium)
- **Where:** `packages/supabase/package.json` scripts block (build/lint/db:* only, no test); root CLAUDE.md commands table notes no supabase test task; each script documents only its own run line (e.g. `scripts/verify-pebble-drafts.ts:19-21`).
- **Why it matters:** Five verify/smoke scripts are the surface's entire test suite, yet `package.json` has no test script and no doc lists a single command that runs them all; each is invoked by hand with its own env-var incantation. After a migration batch there is no one command that answers whether all contract proofs still pass, so partial runs are the default (a contributor re-runs the harness their change obviously touches and skips the rest), and a new harness can be added without becoming part of any runnable whole.
- **Fix:** Add a `scripts/run-all.ts` (or npm `test` script) that executes the four anon-key harnesses plus `smoke-test-engine` sequentially against `SUPABASE_URL`/`ANON_KEY` and aggregates exit codes; document it in `packages/supabase/CLAUDE.md` as the post-batch command.

### 🟢 F-2026-08-PLT-supabase-05 `lab-assets` bucket is created public with no `file_size_limit` and no `allowed_mime_types`, unlike the deliberately constrained `pebbles-media` bucket

- **Criterion:** PLT-08 Managed database platform configuration
- **Priority:** P2 · **Cost:** S · **Impact × Likelihood:** 2 × 2 = 4 (Low)
- **Where:** `20260421000003_lab_assets_bucket.sql:9-11` (insert with no limits) and `:19-25` (public read, admin insert); contrast `20260426000001_pebbles_pictures.sql:22-33` (private, 1572864 bytes, `array['image/jpeg']`).
- **Why it matters:** The `lab-assets` insert sets only (id, name, public) and inherits no per-bucket size or MIME constraints, so any object an admin uploads (of any type and size up to the global cap) is publicly readable. The sibling `pebbles-media` migration shows the intended pattern (private, 1.5 MB cap, image/jpeg only), so the omission is inconsistency rather than a decision. Writes are gated on is_admin, which keeps likelihood low, but an admin session (or a compromised admin account, or a future widening of the insert policy) uploading an HTML/SVG document enables hosted-content abuse (serving scripts or phishing from the product's storage domain) or storage-cost surprise.
- **Fix:** Add a migration upserting `lab-assets` with an explicit `file_size_limit` and an image-only `allowed_mime_types` array, using the `on conflict do update` pattern from the `pebbles-media` migration.

### 🟢 F-2026-08-REL-supabase-06 No outbound call in any edge function carries an explicit deadline or abort signal

- **Criterion:** REL-02 Bounded timeouts and deliberate retries
- **Priority:** P2 · **Cost:** S · **Impact × Likelihood:** 2 × 2 = 4 (Low)
- **Where:** `packages/supabase/supabase/functions/_shared/supabase-client.ts:20-36` (no fetch/signal configuration); `delete-account/index.ts:50-99` and `:105-126` (chained calls, storage BFS, no deadlines); grep `AbortSignal|AbortController` over `supabase/functions` = 0 hits.
- **Why it matters:** The supabase-js clients are constructed without a fetch override or signal, and no call site passes `AbortSignal.timeout`. `delete-account` chains an unbounded-latency sequence (`auth.getUser`, purge RPC, a BFS storage walk of list/remove batches, `deleteUser`); one hung storage call holds the whole function until the platform wall-clock kill, surfacing to the deleting user as an opaque gateway timeout mid-erasure rather than a labeled, diagnosable failure. The documented resume matrix makes retry safe, so the consequence is degraded diagnosability and slower failure rather than corruption.
- **Fix:** Add a small labeled `withDeadline` helper in `_shared` (`AbortSignal.timeout` passed through the clients' global fetch, or wrapping each awaited call) with a stated per-operation default, and apply it to the storage walk and RPC calls; log the label on timeout.

### 🟢 F-2026-08-SEC-supabase-05 `snaps.storage_path` is stored as unvalidated free text with no check that its prefix matches the caller

- **Criterion:** SEC-05 Injection-safe input handling at trust boundaries
- **Priority:** P2 · **Cost:** S · **Impact × Likelihood:** 2 × 2 = 4 (Low)
- **Where:** `20260817130000_pebble_visibility_grades.sql:304-315` (create_pebble snaps insert, storage_path unvalidated); `20260729140000_media_quota_profile_lookup.sql:264-275` (update_pebble same); storage policy `20260426000001_pebbles_pictures.sql:43-61`.
- **Why it matters:** `create_pebble` and `update_pebble` insert `snaps.storage_path` directly from the client payload with no validation that the path begins with the caller's `auth.uid()` prefix, even though the storage RLS and the whole media layout assume `{user_id}/{snap_id}/...`. A caller can persist a snaps row whose storage_path points at an arbitrary prefix (e.g. another user's uid). This does not leak data today because the storage owner-prefix SELECT policy scopes signed-URL generation to the caller's own prefix, so a forged path yields a broken image rather than a foreign read; it is a defense-in-depth gap and a data-integrity hazard, and worth closing because the DB row is the record of truth for cleanup and a future code path trusting `storage_path` without re-checking ownership would turn this into a real cross-user reference.
- **Fix:** In `create_pebble`/`update_pebble`, validate that each snap `storage_path` starts with `v_user_id::text || '/'` (raise on mismatch), or derive the path server-side from `v_user_id` + snap id rather than trusting the client string; keep the two RPCs symmetric.

## Cross-surface findings that name the database contract

These findings are logged against the `cross-surface` surface, not `supabase`, so they are not counted in the domain table above, but their root cause is the shared database contract and each is relevant to this surface:

- 🟡 **F-2026-08-TST-cross-surface-03** (TST-05): `verify-account-purge.ts` oracles are vacuous for five purge tables (log_reactions never seeded; only four tables' purge counts pinned while zero-row checks run after the auth cascade). This is the same defect as F-2026-08-TST-supabase-03, viewed from the harness-quality criterion.
- 🟡 **F-2026-08-REL-cross-surface-01** (REL-06): the M51 privacy-grade reinterpretation backfilled `pebbles` but not `pebble_drafts`, so pre-M51 drafts publish as connections-visible without a fresh owner choice — a migration-contract completeness gap in the same `create or replace`/backfill discipline as F-2026-08-REL-supabase-01.
- 🟡 **F-2026-08-TST-cross-surface-01** (TST-02): Android emits timestamps via `OffsetDateTime.toString()`, producing seconds-less ISO strings that iOS silently drops on draft resume, violating the standing whole-second cross-surface rule that the `pebble_drafts.payload` contract depends on.
- 🟡 **F-2026-08-TST-cross-surface-02** (TST-02): the drafts contract's proof machinery (`verify-pebble-drafts.ts`) never grew an Android-shaped payload fixture, so the DB-surface harness covers only web and iOS shapes although Android is the third writer.

## Refuted during verification

No supabase finding was refuted. Eleven of the 44 were sent to verification; ten returned CONFIRMED and one (F-2026-08-ARC-supabase-01) was DOWNGRADED from impact 4 to impact 3 (severity 12 to 9, still Medium), on the grounds that the `purge_account` re-emission collision only produces residual rows in the narrow `deleteUser`-failure window because the append site holds only `auth.users ON DELETE CASCADE` tables. The finding stands; only its impact was reduced. All other findings were accepted as scored.

## What is already strong

- **Append-only migration discipline with RLS and search_path shipped in lockstep (ARC-08, level 3).** `git log --diff-filter=M` over `supabase/migrations` returns zero modified shipped files, all 12 table-creating migrations enable RLS in the same file, and a header scan shows all 63 security-definer emissions pin `set search_path` with zero misses. The one real re-emission collision was manually unioned in a dedicated migration with full diff reasoning (`20260731090000:1-18`) and codified as a standing rule.
- **RLS default-deny verified across every application table (SEC-02, level 3).** Every ~30 public tables enumerated from `create table` has `enable row level security`; policies are owner-scoped (`user_id=auth.uid()` or parent-join); `using(true)` appears only on reference/public-read tables, and cross-user denial is exercised by `verify-pebble-visibility.ts` (owner/connection/stranger/anon matrix).
- **Cross-user projections are minimal and per-field justified (PRV-07 / SAF-05, level 3).** `get_public_profile` builds an explicit jsonb allowlist with a documented "deliberately excluded" list (user_id, is_admin, consent, quotas, karma, color_world at `20260730120000:188-195`), and `verify-public-profile.ts` asserts the exact key set as an executable exclusion contract; soul and snap identifiers are never whole-row serialized into any cross-user path.
- **Purge and erasure completeness is harness-proven (PRV-08, level 3).** `purge_account` deletes every user-owned table (`20260731090000`), and `delete-account` orchestrates purge, storage-prefix sweep, and auth deletion, idempotent and resumable with identity from the JWT; `verify-account-purge.ts` seeds every entity type including storage files, deletes through the real edge function, and asserts zero rows, empty prefix, auth-user-gone, and re-run convergence.
- **RPC-first multi-table writes with enforced sibling symmetry (ARC-02, level 3).** The rule lives in root AGENTS.md where every agent loads it; the final `create_pebble` and `update_pebble` emissions accept the identical 16 payload keys, and all 50 distinct security-definer functions carry `auth.uid()`/`is_admin` checks except justified cases (trigger functions, anon projections, service-role-only `purge_account`, token-based invite preview).
- **Injection-safe writes at the trust boundary (SEC-05, level 3).** Zero dynamic-SQL sinks (`execute` + `||` over all migration bodies returns nothing; the only `format()` use is in an error `detail`, not executed), and RPC jsonb payloads are validated by type coercion (`::uuid`/`::smallint`/`::timestamptz`) plus CHECK domains (intensity/positiveness, visibility, handle format).
- **Structured, distinguishable error contracts (ARC-07, level 3).** Write RPCs raise distinct exceptions with stable messages and errcodes (71 `using errcode` occurrences); edge functions return structured `{error}` bodies with distinct statuses, `compose-and-write.ts` normalizes PGRST116 to a caller-branchable not-found, and no empty catch blocks exist.
- **Product-map freshness with a real drift gate (AGT-02, level 4, the surface's only level-4).** Schema/RPC changes land migrations plus a 153-line `bundle.json` diff and 18 journal lines in the same PR; `arkaik.yml` gates the map paths, the validator checks endpoint-to-data-model edge semantics, and it reports VALID at the audited snapshot.

## Scored criteria

| Criterion | Level (0-4) | Evidence summary |
|---|---|---|
| A11Y-07 Localization completeness and locale-safe formatting | 2 | Server labels English-only, localized by stable slug on clients; catalog not version-controlled, slug drift undetected. |
| A11Y-08 Inclusive language and emotional vocabulary | 1 | Vocabulary neutral in practice, but no inclusive-copy guideline exists (its absence caps at l1). |
| AGT-01 Layered agent instruction docs | 3 | CLAUDE.md db:* command table matches package.json verbatim; no CI resolves the documented commands. |
| AGT-02 Product map freshness with drift gates | 4 | Schema/RPC changes ship migrations + 153-line bundle.json diff + journal; arkaik.yml gates, validator VALID. |
| AGT-03 Provable changes: fast agent verification loops | 2 | build (tsc) green, but lint is a placeholder echo exiting 0; harnesses run in no CI. |
| AGT-04 Dangerous operations flagged where agents read | 3 | Three DB hazard rules state trigger+action in CLAUDE.md; no CI backstops purge coverage or resync. |
| AGT-05 Scripts over tribal knowledge | 3 | Complete 13-script db:* catalog with documented failure modes; no CI regenerates and diffs database.ts. |
| AGT-06 Machine-checkable contribution conventions | 3 | Repo-wide conventions; sampled db commits conform; advisory-only, unenforced. |
| AGT-07 Least privilege for agents and automation | 2 | Harnesses read service-role from env and refuse without it, but the sanctioned dev loop targets production. |
| AGT-08 Decision log discipline | 3 | DB decisions the densest, demonstrably promoted into standing rules; append-only unenforced by CI. |
| ARC-02 RPC-first server-side write conventions | 3 | RPC-first rule loaded by every agent; create/update_pebble share 16 payload keys; no mechanical check. |
| ARC-03 Strict typing and exhaustiveness discipline | 3 | strict:true, tsc clean, types regen with schema commits; edge fns and scripts type-checked by nothing. |
| ARC-04 Naming and file convention consistency | 3 | 64 migrations follow the timestamped scheme, snake_case, verb_noun functions; lint enforces nothing. |
| ARC-05 Duplication control and dead code removal | 3 | Dead objects dropped in dedicated migrations; every RPC has a call site except documented refund_karma. |
| ARC-07 Error handling as code structure | 3 | Write RPCs raise distinct errcodes (71); edge fns return structured {error}; no swallowed-error lint. |
| ARC-08 Migration and schema change quality | 3 | Append-only (zero modified files), RLS ships with tables, all 63 definers pin search_path; zero machine detection. |
| GDP-01 Consent records and lawful basis | 2 | Email signup persists consent timestamps (backfilled), but OAuth persists NULL and no doc-version column. |
| GDP-02 Special-category data gating and DPIA | 1 | Emotional fields are ordinary NOT NULL columns; no DPIA, no Art.9 naming, no sensitive-column annotation. |
| GDP-03 Bystander data containment | 3 | souls minimized to label+glyph, excluded from every cross-user projection, purge-covered and harness-asserted. |
| GDP-04 Data-subject rights workflows on every client | 2 | Erasure/rectification self-serve, but no machine-readable export (Art.15/20) and no operator runbook. |
| GDP-06 Processor inventory, DPAs, and transfers | 1 | No processor inventory/DPA, region unpinned (allowed_cidrs 0.0.0.0/0); no external egress in edge fns. |
| GDP-07 Enforced retention schedules | 1 | No retention schedule and no scheduled pruning; karma_events/invites/drafts accumulate indefinitely. |
| GDP-08 Breach detection and response readiness | 1 | No incident runbook, no general operator audit-log table; detection relies on platform-default logs. |
| PLT-03 Sign-in options meet platform equity rules | 1 | Provider sets differ (no Apple on Android) and no password recovery, so Apple accounts are non-portable. |
| PLT-04 UGC safety apparatus: filter, report, block, respond | 1 | Cross-user UGC live but no report primitive anywhere; only a server-backed block control exists. |
| PLT-08 Managed database platform configuration | 2 | Buckets configured in migrations and some deliberate auth settings, but production config is console-only. |
| PRF-03 Image and media delivery pipeline | 2 | pebbles-media enforces 1.5MB/jpeg with a two-variant convention; lab-assets public and unconstrained. |
| PRF-04 Indexes match access paths and RLS predicates | 2 | Deliberate owner/tenant indexes, but zero policies use (select auth.uid()) and hot FKs unindexed; no EXPLAIN. |
| PRV-01 PII inventory and schema minimization | 1 | No PII inventory/ROPA (caps at l1); schema minimization otherwise decent (jsonb allowlists, no cross-user select*). |
| PRV-02 Analytics restraint and consent | 3 | Analytics are first-party aggregates behind is_admin-gated definer RPCs; no third-party SDK; no declined-path test. |
| PRV-03 Third-party egress inventory | 2 | Edge functions make no third-party egress; no committed egress inventory or CI hostname check. |
| PRV-04 No personal data in logs and operator analytics | 2 | Edge-function logging is ID/error-level, never payloads; no shared helper or lint enforces it. |
| PRV-05 Private media: EXIF, signed URLs, cache lifetime | 3 | Private-by-default storage, owner-prefix policies, deletion propagates, EXIF strip documented; no automated GPS-fixture test. |
| PRV-07 Cross-user exposure: field-set adequacy and minimality | 3 | Cross-user projections minimal and per-field justified; verify harnesses assert exact key sets, not in CI. |
| PRV-08 Deletion propagation and purge completeness | 3 | purge_account covers every user-owned table, orchestrated idempotently; harness not CI-gated, no FK-vs-purge check. |
| REL-02 Bounded timeouts and deliberate retries | 1 | No timeout/abort signal anywhere; one bounded MAX_SWEEPS=5 loop; no statement/lock_timeout configured. |
| REL-03 Atomic multi-step writes | 2 | Every multi-table write is a single-transaction definer RPC, but pebble-delete orphans photos; no mid-step failure test. |
| REL-04 Idempotence and double-submit protection | 2 | Newer mutations name guards (on-conflict, locks, PK), but create_pebble has no idempotency guard. |
| REL-06 Contract-safe migrations with rollback story | 2 | Append-only holds, rules written, types regen in same commit; no rollback path, no scratch-DB CI; sharp edge fired once. |
| REL-07 Backups exist and restore is rehearsed | 0 | No backup/PITR/restore statement, no scheduled export, no rehearsal; indistinguishable from platform default. |
| REL-08 Production failures reach a human | 1 | Edge fns log to the Supabase dashboard, but no SDK, alert, log drain, or named watcher exists. |
| SAF-01 Crisis and self-harm response pathways | 2 | Server generation is deterministic SVG geometry (no negative-state text), but no crisis-resource data and no test gate. |
| SAF-02 Emotionally safe engagement mechanics | 2 | Karma helper neutral, but emotion_first achievements condition unlock and karma on emotion choice. |
| SAF-03 UGC moderation state machine and takedown | 2 | Glyphs have a full moderation state machine, but display_name and public pebble text are unmoderated. |
| SAF-04 Block integrity and anti-harassment enforcement | 2 | Blocks enforced both directions with race re-check, but preview-vs-accept leaks a block oracle; no block-matrix harness. |
| SAF-05 Bystander exposure on outbound paths | 3 | Every outbound projection excludes soul/snap identifiers via jsonb allowlist; harness-asserted, not CI-gated. |
| SAF-06 Age gating and minors protection posture | 1 | No age/birthdate column or attestation anywhere; Art.8 consent-age basis undemonstrable; scored 1 only on ToS text. |
| SAF-07 Account takeover harm ceiling | 2 | Sessions managed (rotation, reuse interval), but purge/profile-flip/visibility-widen gate only on a valid session. |
| SEC-01 Authentication and session lifecycle integrity | 3 | Identity from auth.uid() in all 58 definer RPCs, deliberate session policy, no manual JWT parsing; no CI auth harness. |
| SEC-02 Row-Level Security default-deny on every table | 3 | Every application table has RLS enabled, owner-scoped; using(true) only on public-read tables; not asserted in CI. |
| SEC-03 Security-definer RPC and privileged-role hygiene | 2 | Definer hygiene uniform and strong, but profiles.is_admin is client-writable (Critical), defeating every gate. |
| SEC-04 Secrets kept out of clients, source, and logs | 2 | No key material in repo, .gitignore covers secrets, edge fns read service-role from env; no secret-scanning CI. |
| SEC-05 Injection-safe input handling at trust boundaries | 3 | Zero dynamic-SQL sinks; jsonb validated by type coercion + CHECK domains; hostile-input fixtures absent; storage_path unvalidated. |
| SEC-06 Transport encryption and on-device data protection | 3 | pebbles-media private with caps, signed URLs with bounded TTL, no http:// literals; no CI bucket-visibility check. |
| SEC-07 Dependency and build pipeline integrity | 1 | Lockfiles exist but no dependabot/renovate/audit, a @main mutable action ref, and no committed deno.lock. |
| SEC-08 Server endpoint and webhook hardening | 2 | Each edge fn has a deliberate auth story and fails closed, but wildcard CORS and unbounded req.json() with no rate limiting. |
| TST-01 Core user paths have automated tests | 2 | Core DB paths (create/publish, drafts, visibility, purge, buy_glyph, connections) have harnesses; many RPCs uncovered; no CI. |
| TST-02 Shared shapes tested against real cross-surface payloads | 2 | Drafts harness tests iOS- and web-shaped payloads with explicit nulls/precision; other cross-boundary shapes untested; no CI. |
| TST-03 Fixed bugs leave pinning regression tests | 2 | Sampled fixes shipped pinning assertions with issue refs, but one fix shipped no test; no blanket convention or automation. |
| TST-04 Runnable harnesses for destructive cross-cutting operations | 2 | Purge harness nearly meets l3, but vacuous log_reactions, only 4/8 cascade tables pinned, no harness for other destructive RPCs. |
| TST-05 Tests assert behavior with real oracles | 3 | Assertions use literal/derived oracles, no expect(true)/.only/.skip; one deliberate Date.now; scripts sit in no runner. |
| TST-06 No merge without the touched surfaces' gates | 1 | No workflow references packages/supabase; lint is a placeholder; migrations/RPCs/RLS merge with zero checks. |
| TST-07 One canonical test framework and idiom per surface | 2 | One canonical Deno harness idiom across five scripts, but package.json defines no test script to run the suite. |
| TST-08 Negative authorization tests in CI | 2 | High-quality denial proofs where present, but ~18 of ~30 tables and most RPCs have no denial test; no CI, no pgTAP. |