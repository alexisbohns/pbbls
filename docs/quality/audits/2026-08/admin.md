# Admin (back-office) — Kritik audit 2026-08

Commit `10181916ba9f56789e62c6351bb380682e5d90da` · framework v0.1.0 · overall surface score **42 / 100 (grade D)**.

## Verdict

The one High finding on this surface is the honest headline: Vercel hosts the operator back office and processes EU personal data (submitter and owner emails, aggregate special-category analytics, operator session cookies) on every render, yet it is absent from the privacy policy's Art. 28 processor inventory and has no region pinning anywhere in the repo (F-2026-08-GDP-admin-01, verified CONFIRMED). Structurally the deeper weakness is verification: Testing scores **9 / 100 (E)**, the lowest cell in the entire audit, because the surface has no test files, no test framework, and no CI job that runs admin lint or build, so a 150-line hand-rolled Lab Note YAML parser and the glyph SVG path math ship unproven and admin changes merge green-by-absence. The best structural strength is Agentic Development Readiness at **74 / 100 (B)**, the surface's top domain: accurate lean instruction docs, a fresh Arkaik map with a drift gate, and a genuinely uniform security-definer-plus-`is_admin` RPC discipline that keeps the dev loop free of elevated keys, which also lifts Code Quality to **60 / 100 (C)**. Against that, moderation enforcement is shallow (a deep pre-publication glyph queue exists, but there is no report intake, no operator block or takedown for public pebbles and profiles, and no audit trail), and the operator account itself has a low takeover ceiling (MFA cannot even be enrolled). The overall grade is **D (42)**, held down by an E in six of eleven domains. A cross-surface Critical compounds the operator risk: F-2026-08-SEC-supabase-01 shows `profiles.is_admin` is client-writable, so any authenticated user can self-grant operator power and defeat every `is_admin` gate this surface relies on (scored under the supabase surface; see that report).

## Domain scores

Framework domain order. No admin domain is capped (the single High finding lands in a domain already below the B cap, so no asterisk applies). All eleven domains carry applicable criteria on this surface, so there is no N/A row.

| Domain | Score | Grade | Open findings (C/H/M/L) | Note |
|---|---|---|---|---|
| Security | 48 | D | 0 / 0 / 5 / 3 | Uniform definer-RPC + `is_admin` gating and publishable-key-only clients, but no wrong-role tests, no secret/dependency scanning, no session-refresh middleware |
| Privacy & Data Protection | 41 | D | 0 / 0 / 1 / 2 | Clean egress and aggregate-only reads, but no PII inventory (caps PRV-01 at level 1) and no CSP |
| GDPR & Regulatory | 47 | D | 0 / 1 / 5 / 0 | Vercel missing from processor inventory (the one High); no DPIA, no rights-request runbook, no retention jobs, no audit trail |
| Safety & Wellbeing | 38 | E | 0 / 0 / 4 / 1 | Deep glyph moderation pipeline, but no report intake, no operator block/takedown, unmoderated display names, low takeover ceiling |
| Code Quality & Architecture | 60 | C | 0 / 0 / 3 / 1 | Strongest engineering domain: RPC-first writes, idiomatic App Router; blunted by unchecked `as unknown as` casts, view-layer data access, and triplicated guards |
| Testing & Verification | 9 | E | 0 / 0 / 4 / 0 | Lowest cell in the audit: zero tests, no framework, no admin CI job, no runnable harness for destructive ops |
| Platform & Store Compliance | 30 | E | 0 / 0 / 3 / 0 | No security headers, no deployment protection (previews hit production data), no report queue |
| Accessibility & Inclusion | 34 | E | 0 / 0 / 4 / 1 | Accessible primitives, but broken dark-mode wiring, measured AA contrast failures, zero reduced-motion handling |
| Performance & Efficiency | 35 | E | 0 / 0 / 2 / 1 | O(1) aggregate reads and zero idle network, but unbounded wildcard list reads and uncompressed cover images |
| Reliability & Observability | 37 | E | 0 / 0 / 4 / 3 | ErrorBlock pattern on analytics cards, but logs boards render failures as empty states, no error boundary, no monitored destination |
| Agentic Development Readiness | 74 | B | 0 / 0 / 1 / 0 | Best domain: accurate lean docs, fresh Arkaik map (level 4), RPC least-privilege; only the missing agent test loop dents it |

## Findings

49 open findings on this surface: 1 High, 36 Medium, 12 Low. None was refuted during verification. One finding was independently re-verified (GDP-admin-01, CONFIRMED); the remainder are single-pass assessments.

Cross-surface note: the operator privilege model this surface depends on is undermined by a Critical scored under supabase, F-2026-08-SEC-supabase-01 (`profiles.is_admin` is client-writable with no `with check` clause, so any authenticated user can self-grant admin and then read every user's analytics, approve their own glyph submissions, and mutate catalogs). It does not count against admin's own cells but it is the single most important context for every `is_admin`-gated finding below.

### 🟠 F-2026-08-GDP-admin-01 Vercel hosts the admin back office but is absent from the processor inventory and has no region pinning

- **Criterion**: GDP-06 Processor inventory, DPAs, and transfers
- **Priority**: P0 · **Cost**: S · **Impact x Likelihood**: 3 x 4 = 12 (High)
- **Where**: Root `/home/user/pbbls/CLAUDE.md` ('Web and admin deploy to Vercel'); `apps/web/docs/privacy/en.md:159-176` (inventory lists Supabase + Google only); find for vercel.json returns only `apps/web/vercel.json` whose sole content is the $schema key; `apps/admin/next.config.ts` is empty; admin_list_glyph_submissions emails at `packages/supabase/supabase/migrations/20260701102810_glyph_marketplace_curation.sql:189-191`
- **Why it matters**: Every admin page render and server action passes personal data (submitter and owner emails, aggregate special-category analytics, operator session cookies) through Vercel's serverless runtime, which defaults to the US region (iad1) with no `vercel.json` region config anywhere. That is EU personal data processed by an unlisted processor with no documented Chapter V transfer mechanism, under ordinary conditions on every request.
- **Fix**: Add a `vercel.json` with an EU regions key (e.g. cdg1/fra1) to `apps/admin` and `apps/web`, add Vercel to the processor inventory with its DPA reference and the transfer mechanism for any residual US processing, and remove the unused Google Gemma entry or mark it prospective.
> Verification (CONFIRMED): Independently re-verified every evidence point: (1) admin deploys to Vercel per `apps/admin/README.md`, the 2026-04-26 back-office spec, and `packages/supabase/CLAUDE.md` ("web/admin Vercel deploys"), not just root CLAUDE.md; (2) the only vercel.json in the repo (apps/web) contains solely a $schema key, apps/admin has none, `apps/admin/next.config.ts` is an empty config, and no preferredRegion/regions/runtime export exists anywhere in apps/admin, so no repo-demonstrable region pinning; (3) the Art. 28 inventory in both `apps/web/docs/privacy/en.md` (sec. 6, lines 159-176) and fr.md (sec. 6) lists only Supabase and Google, and "vercel" appears nowhere in apps/web/docs; (4) admin_list_glyph_submissions (migration 20260701102810) returns su.email/ou.email from auth.users, and the moderation page is an async Server Component calling it through createServerSupabaseClient (cookies from next/headers), so end-user emails, operator session cookies, and analytics RPC responses are processed in the Vercel function runtime on every render. Aggravating factors found: the policy affirmatively claims "No transfer outside the EU is involved in primary storage" (7.1) and "No transfer to external CDNs" (7.3) while all traffic transits Vercel's US-operated global edge, making the notice misleading rather than merely incomplete; the repo's own gdp-06 template cites the Vercel DPA, showing the framework anticipated Vercel as a processor; the policy still cites the invalidated Privacy Shield. Vercel's automatic DPA/DPF certification may supply a contractual Chapter V mechanism, but GDP-06 requires a documented one and a complete inventory, which the finding correctly states is absent. Severity 3x4 is honest.

### 🟡 F-2026-08-SEC-admin-01 No session-refresh middleware: RSC token refreshes discard rotated refresh tokens, causing reuse-detection logouts

- **Criterion**: SEC-01 Authentication and session lifecycle integrity
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 4 = 8 (Medium)
- **Where**: `find apps/admin -name 'middleware.*'` returns nothing (also no proxy file in the app listing); `apps/admin/lib/supabase/server.ts:23-31` (setAll catch with comment 'Server Component cookies are read-only'); `apps/admin/lib/supabase/admin-guard.ts:22` (getUser in RSC layout path via `app/(authed)/layout.tsx:7`)
- **Why it matters**: With no middleware, a returning admin's token refresh happens inside a Server Component where cookie writes throw and are swallowed, so the rotated refresh token is lost and the already-consumed one is re-presented. With Supabase's default refresh-token rotation and reuse detection, that revokes the whole session family and force-logs the admin out; until then every request pays an extra auth round trip. Operational friction on an internal tool, not data exposure.
- **Fix**: Add the Supabase session-refresh middleware (Next 16: check `node_modules/next/dist/docs` for the current middleware/proxy convention) that calls getUser/getClaims and persists refreshed cookies on every matched request, and verify a stale-session page load lands one Set-Cookie.

### 🟡 F-2026-08-SEC-admin-02 No wrong-role negative tests exist for the ~20 is_admin-gated admin RPCs

- **Criterion**: SEC-03 Security-definer RPC and privileged-role hygiene
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 4 x 2 = 8 (Medium)
- **Where**: `grep -rn 'not_admin|42501' packages/supabase/scripts/` matches only a comment (`verify-account-purge.ts:159`); verify-public-profile.ts covers only get_public_profile; grants at `20260630084718_admin_glyph_moderation.sql:178-182` and `20260701102810_glyph_marketplace_curation.sql:214-217` give execute to all authenticated; re-emission hazard documented in root CLAUDE.md 'Standing cross-surface rules' and migration `20260731090000_purge_account_union.sql`
- **Why it matters**: Every privileged RPC is granted to all authenticated users and relies solely on a copy-pasted in-body `is_admin(auth.uid())` guard, and nothing exercises the rejection path. The repo has already lived the create-or-replace re-emission failure (bodies silently dropping each other's changes), so a future migration re-emitting `admin_list_glyph_submissions` or `admin_find_user` without the guard would ship with zero signal, letting any app user dump submitter emails or tamper with catalog, prices, and moderation state.
- **Fix**: Add a `verify-admin-rpcs.ts` harness (same style as verify-public-profile.ts) that signs up a throwaway non-admin user, calls every admin_* / analytics / moderation RPC, and asserts each fails with 42501; run it against the linked project after any migration batch touching those functions and wire it into the audit signals.

### 🟡 F-2026-08-SEC-admin-03 No dependency vulnerability monitoring for the admin workspace (or any workspace)

- **Criterion**: SEC-07 Dependency and build pipeline integrity
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 3 x 3 = 9 (Medium)
- **Where**: `ls .github/dependabot.yml` -> absent; no renovate.json at root or `.github/`; grep of all four workflows (`android-release.yml`, `android.yml`, `arkaik.yml`, `lab-note-reminder.yml`) for audit/osv/scanner returns nothing; `apps/admin/package.json` dependency list
- **Why it matters**: No Dependabot, Renovate, npm audit, or osv-scanner runs on any schedule, so a known-vulnerable transitive dependency in an authenticated operator tool (next 16, react 19, @supabase/ssr, @uiw/react-md-editor, recharts, sonner) sits unnoticed until manually discovered. Lockfile pinning prevents drift but also freezes vulnerable versions in place indefinitely.
- **Fix**: Commit a `dependabot.yml` covering npm (root workspaces), gradle, and swift, or a scheduled workflow running `npm audit --audit-level=high` / osv-scanner; triage cadence at milestone boundaries.

### 🟡 F-2026-08-SEC-admin-04 lab-note-reminder workflow runs a third-party reusable workflow pinned to mutable @main with a pull-requests:write token

- **Criterion**: SEC-07 Dependency and build pipeline integrity
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `.github/workflows/lab-note-reminder.yml:18` (uses: `alexisbohns/ariko/.github/workflows/lab-note-reminder.yml@main`) with permissions at lines 12-14; all other workflow `uses:` references are @v4/@v1 tags (grep 'uses:' `.github/workflows`)
- **Why it matters**: A branch ref is mutable, so whoever can push to that repo's main can change what executes in this repo's PR context on the next event, receiving the GITHUB_TOKEN with contents:read and pull-requests:write. The ariko repo appears to belong to the same maintainer, which lowers but does not remove the risk (a single compromised account or force-push propagates instantly with no review gate in pbbls). Blast radius is limited to token scope (PR comments, labels, read access) because it uses pull_request rather than pull_request_target.
- **Fix**: Pin the reusable workflow to a tag or commit SHA in the ariko repo and bump deliberately; optionally mirror the workflow into this repo.

### 🟡 F-2026-08-SEC-admin-05 No secret-scanning gate in CI and no recorded git-history scan

- **Criterion**: SEC-04 Secrets kept out of clients, source, and logs
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `grep -rn 'gitleaks|trufflehog|secret' .github/workflows/*.yml` shows only legitimate secrets-context references in the Android workflows; no scan/rotation entry in `docs/decisions/log.md` (grep 'rotat|leak|scan'); positive posture evidence at `apps/admin/.gitignore:3-5` and `.env.local.example`
- **Why it matters**: Secret placement currently holds (only publishable keys under apps/admin, env files gitignored), but nothing detects regression. The admin workspace is exactly where a service-role key is most likely to be introduced by accident, and a `NEXT_PUBLIC_`-prefixed privileged variable would ship straight into the client bundle. Today the only guard is reviewer attention.
- **Fix**: Add a gitleaks workflow (push + PR) with a one-time full-history run recorded in the decisions log; assert in the same job that 'service_role' never appears under `apps/*` directories.

### 🟡 F-2026-08-PRV-admin-01 No PII inventory exists, so the operator surface's personal-data exposures (submitter emails) have no documented purpose mapping

- **Criterion**: PRV-01 PII inventory and schema minimization
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `grep -rli 'PII|ROPA|data map|data inventory' docs/` matches only the Kritik criteria files and unrelated plans; email projections at `packages/supabase/supabase/migrations/20260701102810_glyph_marketplace_curation.sql:189-191` and 132-143; rendered at `apps/admin/app/(authed)/pebblestore/glyphs/_components/SubmissionCard.tsx:101`
- **Why it matters**: The admin surface reads real PII (`admin_list_glyph_submissions` returns submitter_email and owner_email; `admin_find_user` resolves arbitrary emails to user ids), but the purposes are only reconstructable from decisions-log prose, not a field-to-purpose map, so nothing distinguishes a justified email exposure from scope creep in review and a future widened projection would not be flagged against anything. The missing inventory also caps the score for every surface.
- **Fix**: Author `docs/privacy/pii-inventory.md` mapping every personal-data column and RPC projection (including admin-facing emails) to purpose and exposure surface; require the file to be updated in the same change that adds a personal-data field.

### 🟡 F-2026-08-GDP-admin-02 Privileged admin actions leave no durable audit trail, making breach scoping and operator attribution impossible

- **Criterion**: GDP-08 Breach detection and response readiness
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 3 = 9 (Medium)
- **Where**: `grep -n 'audit'` over `packages/supabase/supabase/migrations/*.sql` matches only comments; `20260701102810_glyph_marketplace_curation.sql:102-161` (four admin RPCs with no logging); `:119-129` (admin_delete_glyph, delete cascades submission per FK design at `:117-118`); `apps/admin/app/(authed)/logs/actions.ts:151-162` (deleteLog); reviewed_by/reviewed_at exist only on glyph_submissions (`20260630084718:69-74`)
- **Why it matters**: No audit or action-log table exists. `admin_delete_glyph` hard-deletes the glyph and, via cascade, the submission row holding reviewed_by/reviewed_at, destroying the only incidental trail; set_glyph_listed, set_glyph_price, admin_attribute_glyph, and admin_find_user (a privileged email lookup over auth.users) record no actor and no timestamp. A compromised or misused operator account cannot be enumerated for what was read or changed, which defeats Art. 33's 72-hour scoping requirement and leaves moderation decisions undefendable.
- **Fix**: Add an `admin_actions` audit table (actor, action, target ids, timestamp, payload digest) and insert into it from every `is_admin`-gated mutation RPC in the same transaction; for hard deletes, write the audit row before the cascade removes the evidence.

### 🟡 F-2026-08-GDP-admin-03 Out-of-band data-subject rights requests have no operator runbook and no admin tooling, against a published 30-day promise

- **Criterion**: GDP-04 Data-subject rights workflows on every client
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 3 = 9 (Medium)
- **Where**: `apps/web/docs/privacy/en.md:258-264` (30-day commitment), `:288-293` (parental rights); `grep -rilE 'runbook|out.of.band|rights request|subject access'` over docs/, apps/admin, packages/supabase returns nothing relevant; admin route inventory has no user-management surface; `packages/supabase/supabase/functions/delete-account/index.ts:21-23` (manual path exists only as a comment); no export code anywhere in apps/web
- **Why it matters**: The policy invites email rights requests and commits to a 30-day response plus parental access/erasure, but nothing on the operator side backs this: no runbook, no admin tooling to trigger erasure or produce an access/portability export, and portability is unimplemented product-wide, so an Art. 15/20 request would be hand-written SQL under a regulatory clock. The gap is acute for parental and post-lockout requests, which cannot use the self-serve deletion flow.
- **Fix**: Write a versioned operator runbook (identity verification, Art. 12(3) clock, per-right procedure, escalation) in docs/; add a minimal admin flow that, for a verified user id, runs purge_account plus auth deletion, and a scripted export that walks the user-owned tables in database.ts.

### 🟡 F-2026-08-GDP-admin-04 No incident-response runbook: an Art. 33 notification within 72 hours is not achievable from documented material

- **Criterion**: GDP-08 Breach detection and response readiness
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 4 x 2 = 8 (Medium)
- **Where**: `grep -rilE 'incident.response|breach notification|72.hour|supervisory authority'` over docs/, apps/, packages/ returns only the audit framework's own criteria files and the privacy policy's complaint section (`apps/web/docs/privacy/en.md:266-272`); docs/ directory listing contains no security or incident folder
- **Why it matters**: The repo contains no incident-response document of any kind: no roles, no severity assessment, no 72-hour CNIL notification flow, no Art. 34 user-communication template, and no processor notification chain for Supabase or Vercel. Combined with the missing audit trail, a breach discovered today would be handled entirely from improvisation by a single operator, with the deadline consumed inventing process rather than scoping impact.
- **Fix**: Author `docs/security/incident-response.md` covering detection sources, severity assessment, the CNIL Art. 33 flow with a notification template, Art. 34 user-communication criteria and channel, and the Supabase/Vercel processor chain; add a last-reviewed date and revisit it at milestone audits.

### 🟡 F-2026-08-GDP-admin-05 No scheduled retention enforcement exists; policy lifetimes for auth logs and backups are claims with no backing mechanism

- **Criterion**: GDP-07 Enforced retention schedules
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 2 x 4 = 8 (Medium)
- **Where**: `grep -rniE 'pg_cron|cron\.schedule|schedule'` over `packages/supabase/supabase/migrations/*.sql`: zero hits; `.github/workflows` contains no scheduled cleanup; `apps/web/docs/privacy/en.md:197-223` (stated lifetimes incl. Cairns at `:214-217`, a feature absent from the schema); `20260630084718_admin_glyph_moderation.sql:11-12` (review_note retained indefinitely)
- **Why it matters**: The policy states 12-month auth-log retention, a 90-day backup tail, and a 30-day post-deletion buffer, but there are zero pg_cron jobs or scheduled functions and no CI cron prunes anything, so every stated lifetime is either an unverified platform default or fiction. Deletion is the only reaper; rejected glyph submissions keep submitter identity and the review_note forever. The policy even names data that does not exist (Cairns aggregates), evidence the schedule and system were never reconciled.
- **Fix**: Reconcile section 8 of the policy with reality (verify actual auth-log and backup retention and correct the numbers), add pg_cron jobs or scheduled functions for any category with a stated lifetime (e.g. detach submitter identity from old rejected submissions), and document the category-to-job mapping in docs/.

### 🟡 F-2026-08-GDP-admin-06 No DPIA exists despite expanding Art. 9 processing, and operator analytics lack minimum-cohort thresholds

- **Criterion**: GDP-02 Special-category data gating and DPIA
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `grep -riE 'DPIA|data protection impact'` over docs/, apps/, packages/ returns only docs/quality framework files; `apps/web/docs/privacy/en.md:113-128` (Art. 9 self-qualification, dated 2026-04-09) versus migrations `20260730120000`/`20260817130000`/`20260730070347` (July-August 2026 features); `20260501000003_analytics_meaning_share.sql:41-53` (group by week+emotion, no HAVING threshold); `apps/admin/CLAUDE.md` tooltip example ('the 12 active users')
- **Why it matters**: The product self-qualifies moods and reflections as Art. 9 health data and processing has widened materially since the policy date (public profiles, sharing, connections, a marketplace joining auth emails), yet no DPIA or Art. 9 memo exists beyond the policy text; Art. 35(3)(b) makes one hard to avoid. Separately, the operator emotion-share dashboard aggregates with no minimum cohort, so a week with one or two active users renders effectively one identifiable person's emotional record to the operator.
- **Fix**: Write and date a DPIA in docs/ covering the current processing inventory and gate future Art.-9-enlarging features on updating it; add a minimum-cohort HAVING clause (suppress emotion/domain shares for weeks with fewer than N distinct active users) to the analytics views.

### 🟡 F-2026-08-SAF-admin-01 Anonymously served UGC (public pebbles, public profiles) has no moderation state and no admin takedown tooling

- **Criterion**: SAF-03 UGC moderation state machine and takedown
- **Priority**: P2 · **Cost**: L · **Impact x Likelihood**: 3 x 3 = 9 (Medium)
- **Where**: `packages/supabase/supabase/migrations/20260817130000_pebble_visibility_grades.sql:99-131` (get_shared_pebble, anon grant at `:131`); `20260730120000_public_profiles.sql:197-282` (get_public_profile projects glyph strokes at `:245-249`, anon grant at `:285`); admin route inventory (`apps/admin/app/(authed)/`: logs, analytics, pebblestore/glyphs, achievements, domains, emotions only); `grep -rniE 'report(ed|ing)?[_ ](content|user|abuse)|report_'` across migrations and clients returns zero hits
- **Why it matters**: `get_shared_pebble` serves user-authored name, description, and render_svg to anonymous callers, and `get_public_profile` serves display_name, handle, and self-drawn avatar geometry to anon, but neither content type carries a moderation status column, there is no report intake, and the admin has no route or RPC for pebbles or profiles. A user can carve any image as a glyph, set it as their public avatar, and have it served to the open web with no review state and no operator kill switch; removing an abusive public pebble, profile, or avatar today is manual SQL.
- **Fix**: Add a moderation status (or at minimum an admin-settable hidden flag) checked inside get_shared_pebble, get_public_profile, and pebbles_select; add is_admin RPCs to hide a pebble, unpublish a profile, and clear an avatar; surface them in an admin queue; wire a report intake table so user reports become operator-actionable rows.

### 🟡 F-2026-08-SAF-admin-02 Operator accounts cannot enroll MFA and destructive admin actions need only a live session cookie

- **Criterion**: SAF-07 Account takeover harm ceiling
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 4 x 2 = 8 (Medium)
- **Where**: `packages/supabase/supabase/config.toml:285-288` (totp enroll_enabled=false, verify_enabled=false), `:211` (secure_password_change=false); `20260701102810_glyph_marketplace_curation.sql:119-129` (admin_delete_glyph preconditions: is_admin + existence only), `:166-205` (unpaginated listing with emails); `apps/admin/app/(authed)/pebblestore/glyphs/_components/SubmissionCard.tsx:141-177` (client-side dialogs); `grep -rni 'mfa|totp'` over apps/admin: zero hits
- **Why it matters**: config.toml disables TOTP MFA enrollment and verification outright and sets secure_password_change=false, so an admin account (whose compromise exposes every user's submitter email, all aggregate emotional analytics, and full moderation power) is protected by exactly one password. No admin action requires recent re-authentication, and `admin_list_glyph_submissions` returns the full submission history with emails in one unpaginated call, so a hijacked session exfiltrates the entire dataset in one request with no friction and no audit record.
- **Fix**: Enable TOTP MFA and enforce enrollment for is_admin accounts (aal2 check inside is_admin or the admin guard), turn on secure_password_change, add a server-verified recent-auth requirement to admin_delete_glyph and log deletion, and paginate admin_list_glyph_submissions.

### 🟡 F-2026-08-SAF-admin-03 Operators have no anti-harassment enforcement tooling: no admin block, connection severance, or account suspension exists

- **Criterion**: SAF-04 Block integrity and anti-harassment enforcement
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `packages/supabase/supabase/migrations/20260730070347_mutual_connections.sql:301-332` (remove_connection is the only connection_blocks write path, membership check at `:313`); admin RPC inventory (grep rpc( over apps/admin) contains no block/connection/suspension capability; no admin_ban/suspend function in any migration (`grep -rniE 'admin_(block|ban|suspend)'` over migrations: zero)
- **Why it matters**: The block primitive is user-initiated only (connection_blocks rows are written exclusively by remove_connection, which requires the caller to be a member). An operator handling an abuse escalation (a harassment report arriving by email, the only channel that exists) cannot place a block between the parties, sever their connection, revoke an invite token, or suspend the account; every one is manual SQL against production, exactly when speed matters.
- **Fix**: Add an is_admin-gated RPC that inserts into connection_blocks (and deletes the pair's connection row) using the same table the user path writes, so operator blocks inherit the existing enforcement; pair it with a minimal admin escalation page and audit-log the action.

### 🟡 F-2026-08-SAF-admin-04 display_name is unconstrained and unmoderated while served to anonymous visitors and invite recipients

- **Criterion**: SAF-03 UGC moderation state machine and takedown
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `packages/supabase/supabase/migrations/20260411000001_core_tables.sql:23` (display_name text not null, no CHECK); contrast `20260730120000_public_profiles.sql:35-37` and `:89-106` (handle format CHECK + reserved trigger); exposure paths `20260730120000:242-243` (get_public_profile) and `20260730070347:186-194` (preview_connection_invite, anon-granted); admin RPC inventory has no profile moderation call
- **Why it matters**: `profiles.display_name` is bare `text not null` with no length cap, charset rule, or reserved-name guard, unlike handles which get a format CHECK plus a trigger-enforced reserved list. It is projected to anonymous callers and to invite recipients before any relationship exists, so a name like 'Pebbles Support' or a multi-kilobyte abuse string reaches strangers unfiltered, and there is no admin tooling to reset or moderate it.
- **Fix**: Add a length and control-character CHECK on display_name, reuse the reserved-word trigger for impersonation-prone values, and add an is_admin RPC to reset a display name (audit-logged).

### 🟡 F-2026-08-ARC-admin-01 Logs boards render database failures as empty states, indistinguishable from 'no entries'

- **Criterion**: ARC-07 Error handling as code structure
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: `app/(authed)/logs/features/page.tsx:32-38` (error logged, page renders grouped empty sections); `app/(authed)/logs/announcements/page.tsx:19-23`; `app/(authed)/logs/features/_components/FeaturesShippedSection.tsx:24-30`; `app/(authed)/logs/announcements/_components/AnnouncementsPublishedSection.tsx:15-19`; contrast `domains/page.tsx:25-28` and `achievements/page.tsx:78` (error UI rendered) and `components/analytics/ActiveUsersChartCard.tsx:11-20` (ErrorBlock pattern)
- **Why it matters**: Four files in the logs family log a failed select and then render from `data ?? []`, so a transient DB or auth failure shows 'No features in progress.' instead of an error, and the admin may conclude entries were lost or recreate duplicates. The surface already owns two better patterns (analytics ErrorBlock, reference-data destructive message), so error surfacing depends on which route you are on, which is the definition of an improvised rather than structural failure path.
- **Fix**: In the four logs files, branch on the select error and render the existing ErrorBlock (or the reference-pages' inline destructive message) instead of falling through to the empty-state copy.

### 🟡 F-2026-08-ARC-admin-02 Unchecked casts on Json-returning RPCs and hand-mirrored row types bypass the generated database contract

- **Criterion**: ARC-03 Strict typing and exhaustiveness discipline
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `lib/pebblestore/fetchers.ts:13`; `app/(authed)/achievements/actions.ts:47`; `app/(authed)/achievements/page.tsx:31`, `app/(authed)/domains/page.tsx:13`, `app/(authed)/emotions/palettes/page.tsx:12` (as-casts on RPC data); `lib/analytics/types.ts:1-9` and 51-60 vs `packages/supabase/types/database.ts:2288-2296` (nullability divergence); `packages/supabase/types/database.ts:2105-2108` (admin_list_glyph_submissions Returns: Json); counter-example `lib/logs/types.ts:3-5`
- **Why it matters**: Where an RPC returns jsonb, admin asserts the shape blind (`as unknown as AdminSubmission[]`); where the generated types carry structured Returns, admin re-declares rows by hand, and its nullability already diverges from the contract. The hand copies are the safe direction of divergence today, but nothing detects the unsafe direction, and the casts mean a schema change to the submissions payload compiles clean and fails at runtime in the moderation queue.
- **Fix**: Import structured RPC returns from the generated Database type where they exist (analytics, achievements, domains, emotions lists) and delete the hand mirrors; for the two genuinely-Json RPCs (admin_list_glyph_submissions, admin_find_user) either change the SQL to `returns table` so types generate, or add a narrow runtime validator at the cast site.

### 🟡 F-2026-08-ARC-admin-03 isLogPlatform and its platform value list are defined three times on the surface, beside a module that already owns the sibling guards

- **Criterion**: ARC-05 Duplication control and dead code removal
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: `app/(authed)/logs/actions.ts:9-19`; `lib/logs/parse-lab-note.ts:7-17`; `app/(authed)/logs/_components/LogSection.tsx:121`; `lib/logs/options.ts:8-15` (PLATFORM_OPTIONS) and 27-33 (the pattern the guard should follow)
- **Why it matters**: `lib/logs/options.ts` is the designated home for log enum options and derives isLogSpecies and isLogStatus, but the platform guard was never added there. Three independent copies exist (one with the array in a different order), so adding a platform value (the enum already grew to include 'project' and 'infra') means finding and updating all three, and a missed copy silently drops the Lab Note prefill's platform field or refuses a legitimate submission with no error pointing at the stale copy.
- **Fix**: Export a single `isLogPlatform` from `lib/logs/options.ts` derived from PLATFORM_OPTIONS (same as isLogSpecies/isLogStatus) and delete the three local copies.

### 🟡 F-2026-08-TST-admin-01 Admin changes merge green-by-absence: no CI workflow runs admin lint or build on any PR

- **Criterion**: TST-06 No merge without the touched surfaces' gates
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 5 = 10 (Medium)
- **Where**: `.github/workflows/` contains only `android-release.yml`, `android.yml`, `arkaik.yml`, `lab-note-reminder.yml` (verified on the remote at commit 10181916 via GitHub contents API); `android.yml` on.pull_request.paths lists only `apps/android/**` and the workflow file; `apps/admin/package.json:5-9` (no test script); `docs/quality/audits/2026-08/baseline.md` rows 'admin lint: clean' and 'admin tests: no test script exists'
- **Why it matters**: No workflow runs `npm run lint --workspace=apps/admin` or an admin build, and the workspace has no test script, so a PR that breaks admin's ESLint, its TypeScript build, or its compatibility with a schema change merges with zero checks executed; first detection is a post-merge Vercel build or an admin noticing the back-office broken. Because moderation and Lab publishing run through this app, an undetected breakage stalls glyph review and release-note publishing until someone debugs main. The gates pass when run by hand, so the entire gap is wiring.
- **Fix**: Add a `web-admin.yml` workflow running `npm ci` plus `npm run lint --workspace=apps/admin` and `npm run build --workspace=apps/admin` with on.pull_request.paths covering `apps/admin/**`, `packages/supabase/**`, and the workflow file itself; make the job a required check on main.

### 🟡 F-2026-08-TST-admin-02 Zero automated tests on the admin surface; 150-line hand-rolled YAML parser and 200-line SVG path math run unverified

- **Criterion**: TST-01 Core user paths have automated tests
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 3 = 9 (Medium)
- **Where**: `find apps/admin -name '*.test.*'` returns nothing; `apps/admin/package.json:5-9` has no test script; `lib/logs/parse-lab-note.ts:61-149` (parser logic); `lib/pebblestore/path.ts:29+` (parsePath), `lib/pebblestore/svg-to-strokes.ts`, `lib/pebblestore/transform-path.ts`; `docs/superpowers/specs/2026-06-30-issue-497-admin-glyph-moderation-design.md:279` ('No admin test runner (V1)')
- **Why it matters**: Every core path (log CRUD and publishing state machine, glyph moderation, achievements management) is verified only by lint, build, and manual checklists. The riskiest untested modules are pure logic with real edge-case density: the custom YAML parser (149 lines of indent tracking and enum dropping) and the SVG path math whose output is baked into stored glyph geometry rendered by web, iOS, and Android. A parser regression silently drops prefill fields (e.g. the FR title); a path-math regression publishes corrupted market geometry. Both are trivially unit-testable today and the sibling web surface already runs 125 Vitest tests.
- **Fix**: Add Vitest to `apps/admin` mirroring apps/web's setup, start with the pure modules (parse-lab-note, path/svg-to-strokes/transform-path, analytics date helpers, logs options guards), and wire the suite into the same CI job as the lint gate.

### 🟡 F-2026-08-TST-admin-03 Destructive moderation operations (admin_delete_glyph cascade, takedown, log delete) have no runnable harness

- **Criterion**: TST-04 Runnable harnesses for destructive cross-cutting operations
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 4 x 2 = 8 (Medium)
- **Where**: `glyphs/actions.ts:112-122` (deleteGlyph, comment 'cascades to its submission + entitlements. Destructive.'); `packages/supabase/supabase/migrations/20260701102810_glyph_marketplace_curation.sql:119-129` (function body); `20260630003348_glyph_marketplace.sql:13,33,44` (on delete cascade FKs); `20260411000001_core_tables.sql:61` and `20260426000000_add_glyph_to_souls.sql:31` (restricting FKs); `grep 'admin_delete_glyph|approve_glyph|reject_glyph'` over `packages/supabase/scripts/` matches only `verify-account-purge.ts:158` which bypasses the RPCs; moderation design doc section 9 (manual-only)
- **Why it matters**: `admin_delete_glyph` issues a bare delete whose FK graph cascades to glyph_submissions and glyph_entitlements, so a buyer who purchased the glyph but has not placed it loses the item and the karma spent, silently and irreversibly; whether the delete is instead blocked depends on usage state and surfaces as an unmapped generic error. Nothing runnable exercises any of this, and the repo's own bar for destructive operations (a seeded, asserting, re-runnable harness like verify-account-purge.ts) is established one directory over and simply not applied.
- **Fix**: Write `verify-glyph-moderation.ts` on the verify-account-purge.ts pattern: seed an admin, submitter, and buyer; drive approve/reject/delist/attribute/delete through the real RPCs; assert entitlement survival on delist, entitlement destruction plus karma ledger state on delete, and the blocked-delete path when a pebble or soul references the glyph; map the FK-violation error code in messageFor.

### 🟡 F-2026-08-TST-admin-04 Cross-surface shapes admin handles (glyph strokes jsonb, logs rows, Lab Note YAML) have zero contract tests, against the repo's own standing rule

- **Criterion**: TST-02 Shared shapes tested against real cross-surface payloads
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 2 = 6 (Medium)
- **Where**: `docs/decisions/log.md:320-323` (the standing rule and its motivating incident); `lib/pebblestore/fetchers.ts:13` ('as unknown as AdminSubmission[]' on a Returns: Json RPC, `packages/supabase/types/database.ts:2105-2108`); `apps/web/lib/data/logs-api.ts:10-35` (cross-surface logs reader, 'Mirrors apps/ios ... LogsService.swift'); `lib/logs/parse-lab-note.ts` (no fixture from the skill's schema); no fixture or serialization test exists anywhere under apps/admin (find for *.test.* empty; only UI playground fixtures under `components/analytics/__fixtures__/`)
- **Why it matters**: The standing cross-surface rule (promoted into root CLAUDE.md) mandates testing any shape shared by more than one client against verbatim payloads from the other surfaces. Admin sits on three such shapes and tests none: the strokes jsonb from web's carving flow (decoded through a blind double cast), logs rows read by web/iOS/Android Lab screens, and the Lab Note YAML whose schema lives in the lab-note skill. The precedent that motivated the rule (iOS drafts silently losing timestamps while same-surface tests passed) is exactly the failure mode these three are open to.
- **Fix**: Once the Vitest setup lands, add decoder tests fed verbatim captures: a real admin_list_glyph_submissions payload from the linked project, real strokes emitted by web carving, and the lab-note skill's own skeleton YAML as the parse-lab-note fixture; assert the explicit-null and absent-key variants the standing rule names.

### 🟡 F-2026-08-PLT-admin-01 Admin deploys with zero security headers in any tracked config

- **Criterion**: PLT-07 Hosting platform hardening: headers and deployment protection
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 4 x 2 = 8 (Medium)
- **Where**: `apps/admin/next.config.ts:3` (empty NextConfig); repo-wide find for vercel.json returns only `apps/web/vercel.json` containing just a $schema key; `find apps -name middleware.ts` returns nothing; `rg 'headers|Content-Security|X-Frame|Strict-Transport'` over both next.config.ts files returns nothing
- **Why it matters**: The deployed operator app ships with Vercel's permissive defaults: no CSP, no frame-ancestors/X-Frame-Options, no X-Content-Type-Options, no Referrer-Policy, no Permissions-Policy. Missing frame-ancestors allows the admin to be iframed from any origin, enabling clickjacking of destructive one-click actions (delete glyph, unlist, publish log); missing CSP means any future XSS (the app renders user-supplied glyph names, review notes, and markdown) runs unmitigated on a surface that exposes user emails. The sibling web app is equally bare, so no baseline exists to inherit.
- **Fix**: Add a headers() block to `apps/admin/next.config.ts` with HSTS, X-Content-Type-Options nosniff, Referrer-Policy, Permissions-Policy, frame-ancestors 'none', and a CSP (report-only first, then enforced); mirror the same baseline in apps/web so the two apps stay consistent.

### 🟡 F-2026-08-PLT-admin-02 Operator app and its preview deployments rely on application auth alone, with previews pointed at production Supabase

- **Criterion**: PLT-07 Hosting platform hardening: headers and deployment protection
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 4 x 2 = 8 (Medium)
- **Where**: `apps/admin/README.md:24-31` (Vercel defaults, env vars copied from the web production project, no protection step); `rg 'deployment protection|password protect|vercel authentication|preview.*protect'` over docs/ and apps/admin/ matches only docs/quality framework files; `lib/supabase/admin-guard.ts:16-46` is the sole gate
- **Why it matters**: Nothing records any platform-level protection (Vercel Deployment Protection, SSO, or IP allowlist) for the admin production deployment or its previews; the only gate is requireAdmin plus RLS. The README instructs copying the production Supabase URL and key from the web project, so every preview deployment of every open PR is a publicly reachable copy of the back-office running unreviewed branch code against the production database. An attacker who finds a preview URL gets the full login surface plus whatever a buggy branch leaks.
- **Fix**: Enable Vercel Deployment Protection (at minimum Standard Protection covering previews) on the admin project, record the setting and rationale in `apps/admin/README.md`, and either point previews at a non-production Supabase project or record the accepted reason for sharing production.

### 🟡 F-2026-08-PLT-admin-03 No report intake queue exists in the admin while cross-user content is live without any in-product reporting mechanism

- **Criterion**: PLT-04 UGC safety apparatus: filter, report, block, respond
- **Priority**: P2 · **Cost**: L · **Impact x Likelihood**: 3 x 3 = 9 (Medium)
- **Where**: `rg -in 'create table.*report|content_report|user_report|report_' packages/supabase/supabase/migrations/` returns zero hits; `rg -il 'report'` over apps/web/components, apps/ios/Pebbles, apps/android/app/src yields only incidental matches (e.g. `apps/web/components/settings/PublicProfileSection.tsx:72` is a comment); `docs/decisions/log.md:356` ('revisit if abuse appears (M56 adds reporting)'); admin queue covers only glyph submissions (`apps/admin/app/(authed)/pebblestore/glyphs/page.tsx:7-19`)
- **Why it matters**: Cross-user content already ships (mutual connections expose profile data; the glyph marketplace publishes user-created glyphs to everyone), but there is no reporting pillar anywhere in the stack: no report table, no report UI on any client, and no operator queue. Connected users encountering an abusive display name or avatar have no in-product path to report it, and operators have no intake to respond from, so the UGC apparatus required by Apple 1.2 and Play's UGC policy is incomplete on every surface.
- **Fix**: Stand up a content_reports table with an is_admin-gated listing RPC, add an admin queue page mirroring the glyph moderation pattern, and wire client report affordances into it; the client-side controls belong to the web/ios/android cells but the schema and operator queue are the admin-plus-supabase slice.

### 🟡 F-2026-08-A11Y-admin-01 Dark-mode wiring is broken: media-query dark: utilities activate under OS dark while the class-based token block can never apply

- **Criterion**: A11Y-05 Dark/light parity and high-contrast modes
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 4 = 8 (Medium)
- **Where**: `app/globals.css:39-71` (.dark block, class-based) with no @custom-variant dark line (present in `apps/web/app/globals.css:5`); `rg 'next-themes|ThemeProvider|setTheme'` over apps/admin hits only `components/ui/sonner.tsx:3`; `app/layout.tsx:11-19` mounts no provider; mismatch consumers at `components/analytics/KpiCard.tsx:77-78`, `DomainShare.tsx:157-158`, `UserAverages.tsx:128-129`, `QualitySignalsTable.tsx:111-112`, `components/ui/input.tsx:12`
- **Why it matters**: The admin defines a full .dark token block but nothing sets the .dark class, and globals.css lacks the @custom-variant dark override, so every dark: utility compiles to the prefers-color-scheme media query. An operator whose OS is in dark mode gets a hybrid render: page and tokens stay light while dark-tuned utilities activate on top, producing measurably broken pairs (delta badges near 1.8:1, translucent input fills on white, dark toasts on the light app). OS-level dark preference is common among internal operators, so this fires under ordinary conditions.
- **Fix**: Pick one: commit to light-only by removing the dead .dark block and all dark: utilities (and pinning sonner theme='light'), or wire the theme properly by adding @custom-variant dark plus the next-themes provider as apps/web does.

### 🟡 F-2026-08-A11Y-admin-02 Measured AA contrast failures: retention heatmap bucket 4 at 2.41:1 and chart series 1 and 2 at 1.48:1 and 2.68:1

- **Criterion**: A11Y-02 Contrast, reflow, and text resize
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 4 = 8 (Medium)
- **Where**: `components/analytics/RetentionHeatmap.tsx:14` (bg-stone-400 text-stone-50 bucket) and `:9-18`; `app/globals.css:32-33` (chart-1/chart-2 values); consumers at `components/analytics/ActiveUsersChart.tsx:38-39`, `PebbleVolumeChart.tsx:35-36`, `BounceDistributionChart.tsx:17`, `DomainShare.tsx:37`; ratios computed by script over the token values (stone-400 #a8a29e vs stone-50 #fafaf9 = 2.41:1; oklch(0.87 0 0) vs white = 1.48:1; oklch(0.70 0 0) vs white = 2.68:1; muted-foreground on muted = 4.39:1)
- **Why it matters**: Mechanically computed WCAG ratios show three concrete failures on the analytics surface. The retention heatmap's fourth bucket renders text-stone-50 on bg-stone-400 at 2.41:1, far below 4.5:1, making a common retention range unreadable to low-vision operators. The grayscale chart ramp puts chart-1 (the primary series color for DAU lines, bounce bars, domain bars, and pebble volume) at 1.48:1 and chart-2 at 2.68:1, both below the 3:1 non-text minimum. Nothing in the repo measures contrast and no CI catches drift.
- **Fix**: Darken the bucket-4 pair (e.g. keep stone-900 text through bucket 4) and rebase the chart ramp to start at chart-3 lightness or add hue; add a small token-contrast script to the workspace and run it in lint.

### 🟡 F-2026-08-A11Y-admin-03 No reduced-motion handling anywhere in the admin: every animation ignores the user preference

- **Criterion**: A11Y-06 Reduced motion honored across all animation
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 4 = 8 (Medium)
- **Where**: `rg 'prefers-reduced-motion|useReducedMotion|motion-reduce|motion-safe' apps/admin/app apps/admin/components apps/admin/lib apps/admin/hooks` returns zero hits; `rg -l 'prefers-reduced-motion' node_modules/tw-animate-css/dist/` returns nothing; animation entry points at `components/ui/dialog.tsx:34,56`, `dropdown-menu.tsx:44,138`, `select.tsx:86`, `tooltip.tsx:53`, `skeleton.tsx:7`, `sonner.tsx:28`; `rg 'isAnimationActive' apps/admin/components` returns nothing
- **Why it matters**: There is not a single reduce-motion API in the workspace and the tw-animate-css library ships no built-in gate, so all motion plays unconditionally for users who asked the OS to reduce it: zoom-and-slide enter/exit on every dialog, dropdown, select, tooltip, and sheet; the looping skeleton pulse; the toast spinner; and recharts' default mount animations that redraw every line and bar on each analytics visit. The vocabulary is small-scale, but the preference is ignored 100 percent of the time for the users it exists to protect.
- **Fix**: Add a global `@media (prefers-reduced-motion: reduce)` block in globals.css that zeroes animation and transition durations, and pass `isAnimationActive={false}` (or a shared useReducedMotion hook value) to the recharts components.

### 🟡 F-2026-08-A11Y-admin-04 Account-menu trigger in the top bar suppresses the focus outline with no focus-visible replacement

- **Criterion**: A11Y-01 Keyboard operability and accessible semantics
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: `apps/admin/components/layout/TopBar.tsx:30` (className with outline-none and no focus-visible: token, contrast with `components/ui/button.tsx:7` which pairs outline-none with focus-visible:border-ring focus-visible:ring-3)
- **Why it matters**: TopBar's DropdownMenuTrigger (the control that opens the sign-out menu) sets outline-none with no focus-visible ring or border substitute, unlike every other control in the ui kit. A keyboard user tabbing through the header gets no visible indication when this control has focus, violating WCAG 2.4.7 on the one control that reaches sign-out; it also sets cursor-default, masking its interactivity. Because Base UI's Menu.Trigger renders an unstyled element, the shadcn default styles do not apply here.
- **Fix**: Append focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 (the kit's standard pair) to the trigger's className, or render the trigger through the Button component.

### 🟡 F-2026-08-PRF-admin-01 Unbounded wildcard reads: logs boards fetch full markdown bodies, moderation RPC returns every submission with full strokes

- **Criterion**: PRF-05 Bounded, batched, lean client reads
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: select('*') with no .limit: `apps/admin/app/(authed)/logs/features/page.tsx:21-26`, `features/_components/FeaturesShippedSection.tsx:13-18`, `announcements/page.tsx:12-17`, `announcements/_components/AnnouncementsPublishedSection.tsx:8-13`; rendered columns: `LogSection.tsx:174-183`. Unbounded jsonb_agg with strokes: `packages/supabase/supabase/migrations/20260630084718_admin_glyph_moderation.sql:26-47`; caller: `apps/admin/lib/pebblestore/fetchers.ts:4-14`, page `apps/admin/app/(authed)/pebblestore/glyphs/page.tsx:21`. Whole-catalog single-row fetches: `achievements/[id]/page.tsx:15-20`, `domains/[id]/page.tsx:15-21`. Grep for limit(|range( in apps/admin: zero hits.
- **Why it matters**: No list read carries a limit. The logs boards use select('*'), so every row's body_md_en and body_md_fr markdown travels to the server component even though only title, platform, and one date render, and the payload grows per authored log and doubles per language. Worse, admin_list_glyph_submissions aggregates all submissions for a status into one jsonb including each glyph's full strokes array; the 'approved' tab is the entire community market catalog and grows monotonically, with no pagination in the RPC or UI.
- **Fix**: Project named columns for the boards (id, species, platform, status, title_en, updated_at, released_at, published, published_at) and add a sane .limit with a 'show all' escape; add p_limit/p_offset (or keyset on created_at) to admin_list_glyph_submissions and paginate the approved tab; fetch single rows by id for the detail editors.

### 🟡 F-2026-08-PRF-admin-02 Cover images are stored and served as uncompressed originals with no cache-control

- **Criterion**: PRF-03 Image and media delivery pipeline
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: `apps/admin/app/(authed)/logs/_components/CoverImageInput.tsx:11` (MAX_BYTES 5 MB), `:27-34` (type/size gates only, no canvas/createImageBitmap resize), `:43-45` (upload with contentType but no cacheControl), `:78-83` (raw <img> with the lint rule disabled inline, fetching the original). User-facing consumer: `packages/supabase/supabase/migrations/20260421000001_logs_table.sql:1-5,24`. Grep for next/image and transform params (width=|quality=) in apps/admin: zero hits.
- **Why it matters**: The cover-image upload performs no downscaling or recompression (only a 5 MB ceiling and MIME allowlist) and passes no cacheControl, so the object gets whatever default the SDK applies. Because the logs table drives the iOS Lab tab, every end user's Lab feed downloads whatever original the admin picked, up to 5 MB per cover, multiplied across the user base for each published log; the fix point is this upload pipeline, not the readers.
- **Fix**: Downscale client-side before upload (createImageBitmap + canvas to a bounded long edge, re-encode to webp/jpeg ~80), pass an explicit cacheControl (e.g. '31536000' since paths are content-addressed by uuid), and render the admin preview through next/image or a storage transform URL sized to the slot.

### 🟡 F-2026-08-REL-admin-01 Logs boards render database failures as the empty state

- **Criterion**: REL-01 Failure states distinct from empty states
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: `apps/admin/app/(authed)/logs/features/page.tsx:32-38` (error -> console.error -> `const active: LogRow[] = data ?? []`); `apps/admin/app/(authed)/logs/features/_components/FeaturesShippedSection.tsx:24-30`; `apps/admin/app/(authed)/logs/announcements/page.tsx:19-23`; `apps/admin/app/(authed)/logs/announcements/_components/AnnouncementsPublishedSection.tsx:15-19`. Empty rendering: `LogSection.tsx:146-149`. Contrast with the compliant analytics pattern at `components/analytics/KpiStrip.tsx:11-27`.
- **Why it matters**: All four logs board fetch sites log the error and fall through to `data ?? []`, so a failed select renders the dashed empty visual instead of an error. An admin hitting a transient Supabase outage, an expired session, or an RLS regression sees a confidently empty board, can conclude drafts or shipped entries were lost or deleted, and may re-create entries (duplicates once the read path recovers). This contradicts the surface's own ErrorBlock pattern that the analytics cards follow.
- **Fix**: In each of the four sites, return the shared ErrorBlock (`components/analytics/ErrorBlock.tsx`) when `error` is non-null, as the analytics cards do, keeping the empty branch for a genuinely zero-row result.

### 🟡 F-2026-08-REL-admin-02 No error.tsx boundary anywhere; a failed log delete or moderation-queue load crashes the whole app

- **Criterion**: REL-01 Failure states distinct from empty states
- **Priority**: P1 · **Cost**: S · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: `find apps/admin/app -name error.tsx -o -name global-error.tsx -o -name not-found.tsx` returns nothing. Throwing action: `apps/admin/app/(authed)/logs/actions.ts:155-158`; unguarded call: `apps/admin/app/(authed)/logs/_components/DeleteLogButton.tsx:43-47`. Throwing fetcher: `apps/admin/lib/pebblestore/fetchers.ts:9-12`; unguarded page: `apps/admin/app/(authed)/pebblestore/glyphs/page.tsx:21`.
- **Why it matters**: The app contains no error.tsx, global-error.tsx, or not-found.tsx. Two production paths throw where nothing catches: deleteLog throws on a failed delete and DeleteLogButton awaits it inside startTransition with no try/catch, so any delete failure escapes to React with no boundary and produces Next's generic crash screen, discarding the admin's open form; and listSubmissions rethrows RPC errors that GlyphModerationPage awaits with no try/catch, so any failure of admin_list_glyph_submissions crashes the whole moderation route.
- **Fix**: Add an `app/(authed)/error.tsx` (and a root global-error.tsx) rendering the ErrorBlock pattern with a reset() retry; change deleteLog to return an ActionResult like every sibling action instead of throwing, and surface it in DeleteLogButton; wrap the moderation page fetch in try/catch rendering ErrorBlock.

### 🟡 F-2026-08-REL-admin-05 Cover-image storage objects and the logs row have no consistency story

- **Criterion**: REL-03 Atomic multi-step writes
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 2 x 3 = 6 (Medium)
- **Where**: Upload-before-row: `apps/admin/app/(authed)/logs/_components/CoverImageInput.tsx:39-53`; immediate best-effort remove of a possibly-referenced object: `CoverImageInput.tsx:59-69`; row-only delete: `apps/admin/app/(authed)/logs/actions.ts:151-162`; user-facing consumer: `packages/supabase/supabase/migrations/20260421000001_logs_table.sql:1-5,24` (cover_image_path, 'Drives the iOS Lab tab').
- **Why it matters**: The cover image is a file-plus-row pair with three unmanaged failure paths: (1) the object uploads before any logs row references it, so abandoning the form strands it forever; (2) deleteLog deletes only the row, leaving the object in the public bucket; (3) the Remove button deletes the object immediately, before save, so if the admin navigates away the saved row still points at a deleted object and the iOS Lab tab renders a broken cover. There is no cleanup job, fail-safe ordering, or orphan sweep.
- **Fix**: Defer the storage delete to save time (mark for removal in form state, delete in updateLog after the row update succeeds); make deleteLog remove the referenced object after the row delete; add a periodic orphan sweep or fold lab-assets into an existing cleanup harness.

### 🟡 F-2026-08-REL-admin-07 Admin production failures reach no monitored destination

- **Criterion**: REL-08 Production failures reach a human
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 3 x 3 = 9 (Medium)
- **Where**: `apps/admin/package.json:11-38` (no sentry/bugsnag/datadog/etc.; repo-wide grep confirms only 'scrollbar' false positives); grep count: 51 console.error sites in apps/admin; no vercel.json for admin (ls: No such file or directory); grep for 'log drain', 'triage', 'error monitor', 'observability' in docs/ and apps/admin: no relevant hits; no alerting config in `.github/workflows` (android-release.yml, android.yml, arkaik.yml, lab-note-reminder.yml, lab-note.yml only).
- **Why it matters**: Every failure path ends at console.error (51 call sites across 24 files) in Vercel function logs or the admin's own browser console. There is no error-reporting SDK, log drain, alert rule, or documented routine for checking logs. Because the admin is the moderation and publishing surface, a persistent failure (the moderation RPC failing after a migration, or createLog failing) manifests only as the admin noticing odd UI, and there is no signal at all for failures the admin does not personally trigger; the crash-page failures from the missing error.tsx are invisible after the fact.
- **Fix**: Add a lightweight error-reporting integration (e.g. Sentry for Next.js with beforeSend scrubbing) or at minimum a Vercel log drain plus a documented weekly check routine; wire server-action failures through one shared reporting helper instead of bare console.error.

### 🟡 F-2026-08-AGT-admin-01 apps/admin has no test entry point at all, so an agent cannot prove any admin change beyond lint and build

- **Criterion**: AGT-03 Provable changes: fast agent verification loops
- **Priority**: P2 · **Cost**: M · **Impact x Likelihood**: 2 x 4 = 8 (Medium)
- **Where**: `apps/admin/package.json` scripts block (dev/build/start/lint only); `docs/quality/audits/2026-08/baseline.md:14`; `apps/admin/CLAUDE.md:23-28` (fixture and error-mapping conventions with no test harness behind them)
- **Why it matters**: apps/admin defines no test script and has no test files, so an agent cannot prove real logic (analytics fetchers, fixture variants, is_admin-gated flows) by a command. Its own CLAUDE.md mandates playground fixtures precisely because states are hard to review, yet none is assertable. An agent refactoring a fetcher's error mapping (the PostgrestError-is-not-an-Error rule) has no loop that would catch the regression; the break surfaces as a blank ErrorBlock in production analytics.
- **Fix**: Add vitest to apps/admin mirroring the web setup, seed it with tests for the fetcher error mapping and fixture-driven component states, and wire `npm run test`.

### 🟢 F-2026-08-SEC-admin-06 is_admin(uuid) is an admin-status oracle callable by anon with any user id

- **Criterion**: SEC-03 Security-definer RPC and privileged-role hygiene
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `packages/supabase/supabase/migrations/20260421000000_profiles_is_admin.sql:21-29` (definer function with caller-supplied p_user_id, grant execute to anon, authenticated); all in-repo call sites pass auth.uid() (e.g. `20260421000001_logs_table.sql:46-56`, `20260421000003_lab_assets_bucket.sql:24`)
- **Why it matters**: `public.is_admin(p_user_id uuid)` is security definer, granted to anon and authenticated, and returns the is_admin flag for whatever uuid the caller supplies rather than deriving the subject from auth.uid(), so any client can probe whether a given user id is an operator (helping an attacker pick the account to phish). Exploitation needs known uuids (which the schema is otherwise careful not to leak), so likelihood is low, but submitter_id does travel to admin clients. The parameterized shape is needed by RLS policies, but those always pass auth.uid(), so constraining the argument costs nothing.
- **Fix**: Re-emit is_admin to return false when p_user_id is distinct from auth.uid() (all existing call sites already pass auth.uid()), or revoke from anon and add a self-only wrapper for client use.

### 🟢 F-2026-08-SEC-admin-07 lab-assets bucket enforces no server-side content-type or size limits; upload validation is client-only

- **Criterion**: SEC-05 Injection-safe input handling at trust boundaries
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `packages/supabase/supabase/migrations/20260421000003_lab_assets_bucket.sql:9-11` (no file_size_limit/allowed_mime_types columns set); `apps/admin/app/(authed)/logs/_components/CoverImageInput.tsx:27-33` (client-only ACCEPT and MAX_BYTES checks), 43-45 (contentType taken from the client file)
- **Why it matters**: The bucket insert sets only id, name, and public; storage-level allowed_mime_types and file_size_limit are absent, so the server accepts whatever content type and size an admin-authenticated client sends (SVG or multi-hundred-MB blobs) into a public bucket. The 5 MB and PNG/JPEG/WebP checks live only in CoverImageInput and can be bypassed by any direct storage API call holding an admin session. The trust boundary is soft because writers must already be admins, so this is a hardening gap; the practical risks are a scripted mistake filling the bucket and script-bearing SVG served from the storage origin.
- **Fix**: Alter storage.buckets for lab-assets to set allowed_mime_types = image/png,image/jpeg,image/webp and file_size_limit = 5MB so the server enforces the same contract the client promises.

### 🟢 F-2026-08-SEC-admin-08 createLog/updateLog throw an unhandled RangeError on a malformed released_at instead of returning a form error

- **Criterion**: SEC-05 Injection-safe input handling at trust boundaries
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `apps/admin/app/(authed)/logs/actions.ts:36-40` (new Date(released_at_raw).toISOString() with no isNaN/validity guard, upstream of the field validations at lines 42-46)
- **Why it matters**: readLogFields converts the raw released_at with `new Date(raw).toISOString()` before any validity check. A string Date cannot parse yields an Invalid Date, and toISOString() then throws RangeError, escaping the action as a 500 with a Next error digest instead of the structured `{ error }` result every other invalid field produces. The normal datetime-local input never emits such a value, so it surfaces only via hand-crafted POSTs (which any authenticated user can send) or a browser quirk; there is no data or security consequence because the throw precedes any DB call and RLS still gates writes.
- **Fix**: Parse defensively: `const d = new Date(raw); if (Number.isNaN(d.getTime())) return { error: 'Release date is invalid.' };` then call toISOString().

### 🟢 F-2026-08-PRV-admin-02 Cover images upload verbatim to the public lab-assets bucket with no re-encode or EXIF strip

- **Criterion**: PRV-05 Private media: EXIF, signed URLs, cache lifetime
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `apps/admin/app/(authed)/logs/_components/CoverImageInput.tsx:24-53` (raw file upload, client-only validation); bucket is public with no processing pipeline (`packages/supabase/supabase/migrations/20260421000003_lab_assets_bucket.sql:9-11`); no EXIF handling anywhere under apps/admin (grep 'exif|EXIF' empty)
- **Why it matters**: CoverImageInput uploads the picked File directly to the public lab-assets bucket after only client-side type and size checks, with no re-encode or metadata strip on any side, and the object is then served forever from a permanent public URL embedded in the changelog consumed by all clients. If the maintainer ever uses a phone photo as a cover, its EXIF block (GPS, capture time, device) publishes with it. Exposure is limited to the operator's own metadata (not app users'), and typical covers are designed graphics, so impact and likelihood are low, but the gap is structural and the fix is cheap.
- **Fix**: Re-encode the image in the browser before upload (canvas/createImageBitmap to WebP/JPEG drops all metadata) or add a server-side transform; also set allowed_mime_types and file_size_limit on the bucket while touching it.

### 🟢 F-2026-08-PRV-admin-03 No Content-Security-Policy or security headers on the admin surface

- **Criterion**: PRV-03 Third-party egress inventory (SDKs, fonts, CDNs)
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `apps/admin/next.config.ts:3` (empty config, no headers()); no vercel.json or middleware anywhere under apps/admin; observed egress is Supabase-only (grep of https:// literals and package.json dependency review)
- **Why it matters**: next.config.ts is an empty object with no CSP, connect-src/script-src allowlist, frame-ancestors, or referrer policy. The app's egress is currently perfect (only the Supabase origin), which is exactly when a CSP is cheapest to adopt and most valuable, because it converts today's clean posture into an enforced invariant: any future dependency or copy-pasted snippet that phones home would be blocked and visible. For an operator tool whose session cookies are JS-readable by SDK design, script-src restriction is also meaningful XSS blast-radius containment. Absence today is a hardening gap, not an active exposure.
- **Fix**: Add a headers() block in next.config.ts with a CSP pinning default-src 'self', connect-src 'self' plus the Supabase project origin, img-src 'self' plus the storage origin, frame-ancestors 'none', and X-Content-Type-Options nosniff.

### 🟢 F-2026-08-SAF-admin-05 No documented removal channel or operator procedure exists for a named third party (a soul) to request erasure

- **Criterion**: SAF-05 Bystander exposure on outbound paths
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `apps/web/docs/privacy/en.md:74-77` (souls acknowledged, no recourse), `:226-272` (rights section addresses only 'your' data); `grep -rilE 'removal|third.party.*request'` over docs/ finds no channel; admin surface has no soul lookup capability (RPC inventory)
- **Why it matters**: The privacy policy acknowledges that users store third-party names as souls but documents rights only for the account holder; a person who learns they are named has no stated channel to request removal, and operators have no procedure or tooling to locate and remove a soul by name across accounts. Because public sharing now exists, the household exemption does not shield published paths, so the controller carries the obligation directly. The gap is recourse and procedure, not exposure: no third-party name currently leaves the owner's scope on any traced outbound path.
- **Fix**: Add a paragraph to the privacy policy naming the contact channel for recorded third parties, and a short operator procedure in docs/ (verify the requester, locate matching souls via a service-role query, remove or pseudonymize with owner notification where required).

### 🟢 F-2026-08-ARC-admin-04 CoverImageInput performs storage IO and owns upload business rules inside a client view component, with no lint guard on the data boundary

- **Criterion**: ARC-01 Responsibility and layer separation
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `app/(authed)/logs/_components/CoverImageInput.tsx:7` (imports createClient from lib/supabase/browser), `:19` (client construction in a view), `:43-45` (storage upload), `:65` (storage remove), `:9-11` (BUCKET/ACCEPT/MAX_BYTES rules inline); `eslint.config.mjs:5-21` (no no-restricted-imports rule); `grep 'createClient('` over components/ and app/ returns this file as the only view-layer hit
- **Why it matters**: CoverImageInput is the one view component on the surface that constructs a Supabase client and talks to the backend directly (uploads to and removes from the lab-assets bucket), and the upload business rules (MIME types, 5 MB cap, bucket name, object path scheme) live inline in the render module rather than a lib module the rest of the surface could reuse or a test could import. With no no-restricted-imports rule fencing lib/supabase/browser off from components, nothing prevents the next contributor from copying this pattern, eroding the otherwise clean fetchers-and-actions boundary.
- **Fix**: Extract the upload/remove/publicUrl logic and its constants into `lib/logs/cover-image.ts` (or a server action), leave the component as state plus rendering, and add an ESLint no-restricted-imports rule blocking lib/supabase/browser imports outside lib/.

### 🟢 F-2026-08-A11Y-admin-05 ActiveUsersChart metric switcher claims role=tablist without the APG tabs keyboard model, and LogSection hides platform from assistive tech

- **Criterion**: A11Y-01 Keyboard operability and accessible semantics
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `apps/admin/components/analytics/ActiveUsersChart.tsx:77-89` (role=tablist, role=tab, aria-selected on Button elements, no keyboard handlers or panels); `apps/admin/app/(authed)/logs/_components/LogSection.tsx:163-171` (platform icon container marked aria-hidden with no textual alternative in the row)
- **Why it matters**: The DAU/WAU/MAU switcher marks a row of plain buttons with role=tablist/tab plus aria-selected but implements none of the APG tabs contract (no roving tabindex, arrow-key navigation, aria-controls, or tabpanel), so a screen reader announces tab semantics that then behave like buttons, which is more confusing than plain buttons. Separately, LogSection conveys each row's platform only through an aria-hidden icon tile, so assistive-technology users get no platform information in the features and announcements lists. Both are semantics defects on real operator flows, though each control remains operable.
- **Fix**: Drop the tab roles in favor of a group with aria-pressed toggle buttons (or implement the full APG tabs pattern), and add an sr-only platform label or aria-label on the LogSection row link.

### 🟢 F-2026-08-PRF-admin-03 No bundle analyzer or size gate exists for the admin surface

- **Criterion**: PRF-02 Client JavaScript bundle discipline
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `apps/admin/package.json:11-38` (no analyzer/size tooling; recharts ^3.8.0 and @uiw/react-md-editor ^4.1.0 present); `apps/admin/next.config.ts:1-5` (empty config); `.github/workflows/` contains no admin build or size job (android-release.yml, android.yml, arkaik.yml, lab-note-reminder.yml, lab-note.yml).
- **Why it matters**: Despite disciplined server/client boundaries, nothing measures or bounds shipped client JavaScript: no @next/bundle-analyzer or size-limit dependency, next.config.ts is empty, and no CI builds admin at all, so a dependency regression (recharts major bump, a wholesale lodash import, the md-editor losing its dynamic import) would land invisibly. The surface already carries two heavyweight client dependencies whose cost is managed only by convention.
- **Fix**: Wire @next/bundle-analyzer behind an ANALYZE env flag in next.config.ts and record the current first-load-JS route table as the baseline; optionally add a size-limit budget file and a CI job asserting it on `apps/admin/**` changes.

### 🟢 F-2026-08-REL-admin-03 Detail pages convert transient DB errors into 404 not-found responses

- **Criterion**: REL-01 Failure states distinct from empty states
- **Priority**: P2 · **Cost**: S · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: `apps/admin/app/(authed)/logs/[id]/page.tsx:23-26` (`if (error || !data) { ... notFound() }`); `apps/admin/app/(authed)/achievements/[id]/page.tsx:17-21` (error -> `data ?? []` -> find fails -> notFound()); `apps/admin/app/(authed)/domains/[id]/page.tsx:17-21` (same shape).
- **Why it matters**: Three detail routes treat a failed read identically to a missing row: on error the data is coalesced to null/empty and the code falls into notFound(), so a transient RPC or select failure renders a 404 for a record that exists, telling the admin an achievement, domain, or log has vanished. For the logs editor (the entry point to editing published, user-visible Lab content) a spurious 404 during an incident window reads as data loss. The failure is logged server-side but the rendered state is indistinguishable from genuine absence.
- **Fix**: Branch on `error` before the notFound() check: render ErrorBlock (or rethrow into a new error.tsx boundary) for read failures, and reserve notFound() for a successful read that returned no row.

### 🟢 F-2026-08-REL-admin-04 No network call in the admin surface carries an explicit timeout

- **Criterion**: REL-02 Bounded timeouts and deliberate retries
- **Priority**: P3 · **Cost**: M · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: Case-insensitive grep for withTimeout|AbortSignal|AbortController|timeout across apps/admin/app, components, lib, hooks: zero hits. Representative unprotected call sites: `apps/admin/lib/analytics/fetchers.ts:30` (rpc), `apps/admin/lib/supabase/admin-guard.ts:22,32` (auth + rpc on every authed navigation), `apps/admin/app/(authed)/logs/_components/CoverImageInput.tsx:43-45` (browser storage upload).
- **Why it matters**: The surface has no timeout utility, AbortSignal usage, or client-level timeout, so every Supabase RPC, table read, auth call, and storage upload relies on library defaults. A hung upstream leaves an RSC card suspended on its skeleton until the hosting platform's function deadline converts it into an opaque 5xx, and leaves the browser-side upload pending indefinitely. The web workspace already has a documented withTimeout convention that was never ported, so this is a known-pattern gap; no retry loops exist, so there is no unbounded-retry hazard, but no flow can distinguish 'slow' from 'dead'.
- **Fix**: Port the web workspace's `withTimeout(label)` wrapper into apps/admin/lib and wrap the fetchers, admin-guard calls, and the storage upload; alternatively pass a global fetch with AbortSignal.timeout into createServerClient/createBrowserClient so every SDK call inherits a deadline.

### 🟢 F-2026-08-REL-admin-06 Log creation and admin glyph publish have no server-side duplicate guard

- **Criterion**: REL-04 Idempotence and double-submit protection
- **Priority**: P3 · **Cost**: M · **Impact x Likelihood**: 2 x 2 = 4 (Low)
- **Where**: No unique constraints: `packages/supabase/supabase/migrations/20260421000001_logs_table.sql:11-32` (grep for 'unique' in logs migrations: zero hits). Unguarded insert: `apps/admin/app/(authed)/logs/actions.ts:78-82`; unconditional glyph insert: `packages/supabase/supabase/migrations/20260630084718_admin_glyph_moderation.sql:159-166`. Read-then-write: `apps/admin/app/(authed)/logs/actions.ts:106-139`. Contrast server guards: same migration lines 67, 95, 124.
- **Why it matters**: createLog inserts into logs with no unique constraint beyond the PK, and publish_admin_glyph inserts a new glyph unconditionally, so any double-fire past the client pending guard (two tabs, a replayed POST, a future retry wrapper) creates duplicate rows; a duplicate published log is user-visible in the iOS Lab feed. Additionally, updateLog's read-then-write makes concurrent edits by two admin sessions last-write-wins with stamp decisions based on stale reads, and no concurrency model is documented. Client-side guards are consistently present, and the moderation RPCs' invalid_state preconditions are a good server-side model these two flows lack.
- **Fix**: Add a scoped uniqueness or idempotency guard for log creation (e.g. unique (species, title_en) where published, or an idempotency key column checked on insert); give publish_admin_glyph a dedupe key or unique (user_id, name) partial index; document the last-write-wins model for log edits in `docs/decisions/log.md` or add updated_at optimistic checking to updateLog.

## Refuted during verification

None. No admin-surface finding was disproved during verification. The single verified admin finding (F-2026-08-GDP-admin-01) was CONFIRMED; the other 48 are single-pass assessments carried at their recorded severity.

## What is already strong

- **Agentic readiness is the surface's best domain (B, 74).** The Arkaik product map covers the back office with 58 admin nodes present and current, backed by the repo-wide `arkaik.yml` drift gate (AGT-02, level 4). The 30-line `apps/admin/CLAUDE.md` is accurate and lean, governed by the root edit policy (AGT-01, level 3), and admin-local hazards carry both trigger and action where agents read them, e.g. the "fetchers throw new Error because PostgrestError is not an Error subclass" rule (`apps/admin/CLAUDE.md:26-28`, AGT-04 level 3).
- **Privileged-RPC hygiene is uniform and deliberate.** Every admin RPC is security definer with a pinned search_path, an in-body `is_admin(auth.uid())` check, authenticated-only grants, and explicit revokes (e.g. `20260630084718_admin_glyph_moderation.sql:16-24,173-182`; all six analytics migrations revoke direct view select), and the operator app re-verifies the role server-side in `requireAdmin` (`app/(authed)/layout.tsx:7`, admin-guard.ts:32-43) (SEC-03, AGT-07, level 3 for least-privilege).
- **RPC-first write discipline holds across every multi-table flow.** Moderation, achievements, domains, and emotions all mutate exclusively through named RPCs (`app/(authed)/pebblestore/glyphs/actions.ts`, `achievements/actions.ts`, `domains/actions.ts`, `emotions/actions.ts`); only the single-table logs CRUD uses direct `.from()`, which the written rule permits (ARC-02, level 3).
- **The App Router idiom is followed correctly throughout.** Route pages are server components that fetch server-side, writes go through server actions consumed with useActionState, and no client component sits at a route root; the one app-level useEffect reads sessionStorage, a legitimate client concern (ARC-06, level 3).
- **Operator analytics is aggregate-first by construction.** All cards read `is_admin`-gated definer RPCs returning counts and rates only, with direct view select revoked from anon/authenticated (`20260430000000:147-151`), and every logging call site is tag-plus-message only, never a payload (PRV-04, level 2; GDP-03 bystander containment, level 3, with souls appearing only as `avg_souls`/`pct_with_soul`).
- **The glyph moderation pipeline is genuinely deep.** A pending/approved/rejected state machine plus a listed flag, cross-user reads status-gated at the RLS layer (`glyphs_select` requires `status='approved'` for non-owners), an operator FIFO queue with approve/reject/reprice/delist/attribute/delete, mandatory reject reasons, and stamped reviewer identity (SAF-03 and PLT-04, level 2, both held back only by the missing report pillar for other content types).
- **The surface has zero idle network work by architecture.** No setInterval/setTimeout, no realtime channels, and no client-side data-fetching layer, so an open admin tab's idle network profile is zero, and no retry loops create an unbounded-retry hazard (PRF-08, level 3).
- **Secrets and naming conventions are clean.** Clients carry only publishable values (`NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`), no service_role appears under apps/admin, env files are gitignored (SEC-04, level 2), and components/hooks/utilities follow the documented PascalCase/camelCase/kebab-case conventions with near-total conformance outside vendored shadcn files (ARC-04, level 3).

## Scored criteria

All 59 admin assessments, in criterion-id order. Level is the 0-4 maturity score (0 Absent, 1 Ad-hoc, 2 Defined, 3 Managed, 4 Verified).

| Criterion | Level | Evidence summary |
|---|---|---|
| A11Y-01 Keyboard operability and accessible semantics | 2 | Accessible Base UI primitives, focus-visible pairing, zero div/span onClick, but TopBar trigger drops focus outline, ActiveUsersChart misuses tab roles, and no a11y CI |
| A11Y-02 Contrast, reflow, and text resize | 2 | Token pairs mostly pass and reflow is sound, but heatmap bucket 4 (2.41:1) and chart-1/chart-2 (1.48:1, 2.68:1) measurably fail AA and nothing measures |
| A11Y-05 Dark/light parity and high-contrast modes | 1 | Full .dark token block exists but no provider or class ever applies it and the @custom-variant is missing, so OS dark yields a broken hybrid render |
| A11Y-06 Reduced motion honored across all animation | 0 | Zero reduce-motion API anywhere; dialogs, dropdowns, skeletons, spinners, and recharts all animate unconditionally |
| AGT-01 Layered agent instruction docs, accurate and lean | 3 | 30-line rules-only admin CLAUDE.md, verified accurate against the tree, governed by the root edit policy; no automation checks it (not 4) |
| AGT-02 Product map freshness with drift gates | 4 | 58 current admin nodes, repo-wide arkaik.yml gate, encoded triggers, demonstrated same-PR discipline on other surfaces |
| AGT-03 Provable changes: fast agent verification loops | 2 | Lint and build run headless, but no test script and no CI covers admin paths, so no test loop an agent could run |
| AGT-04 Dangerous operations flagged where agents read | 3 | Admin-local rules with trigger and action (fetcher-throws, definer-RPC gate) plus inherited root hazards; no automation backs them (not 4) |
| AGT-05 Scripts over tribal knowledge | 3 | dev/build/lint scripted, playground fixtures documented next to the code; no CI executes admin entry points (not 4) |
| AGT-06 Machine-checkable contribution conventions | 3 | Repo-wide conventions, sampled admin commits conform; advisory-only, no required checks (not 4) |
| AGT-07 Least privilege for agents and automation | 3 | Empty env example, gitignored env, privileged ops gated by is_admin definer RPCs so the dev loop holds no elevated key; no scanning (not 4) |
| AGT-08 Decision log discipline | 3 | Admin-relevant decisions present and promoted, promotion cadence documented; no machine check (not 4) |
| ARC-01 Responsibility and layer separation | 2 | One client-module family and a documented analytics boundary, but view files do direct data access, a client view does storage IO, and no lint guard catches new violations |
| ARC-02 RPC-first server-side write conventions | 3 | Every multi-table admin flow is one named RPC with in-body ownership checks; only single-table logs CRUD uses direct .from(); no CI diff of sibling signatures (not 4) |
| ARC-03 Strict typing and exhaustiveness discipline | 2 | strict true, no-explicit-any at error, zero as any, but five as-unknown-as double casts and hand-mirrored analytics types diverge from the generated contract |
| ARC-04 Naming and file convention consistency | 3 | Written conventions with near-total conformance; deviations confined to vendored shadcn files; ESLint does not encode naming and no CI runs admin lint (not 4) |
| ARC-05 Duplication control and dead code removal | 2 | Shared utilities are the norm and no dead code found, but isLogPlatform is defined three times beside the module that owns its siblings, with no duplication tooling |
| ARC-06 Platform idiom adherence | 3 | Idiomatic App Router throughout (SC fetch, server actions, useActionState), no useEffect data fetching, idiom era pinned in docs; no lint audits use client (not 4) |
| ARC-07 Error handling as code structure | 2 | DB error codes mapped and all analytics cards render ErrorBlock, but the logs family swallows failures into empty states and no failure path is tested |
| GDP-02 Special-category data gating and DPIA | 2 | Operator access minimized to one table plus aggregate is_admin RPCs and consent timestamps persist, but no DPIA exists and analytics lack minimum-cohort thresholds |
| GDP-03 Bystander data containment | 3 | No soul identifier reaches the operator surface, cross-user projections use jsonb allowlists, purge covers both-sided tables with a harness; harness is manual, no CI (not 4) |
| GDP-04 Data-subject rights workflows on every client | 1 | Policy promises a 30-day rights channel and parental rights, but no operator runbook, no admin rights tooling, and portability unimplemented product-wide |
| GDP-06 Processor inventory, DPAs, and transfers | 2 | Written inventory covers Supabase and the admin app adds no data SDKs, but Vercel (the actual host) is absent, no region pinning exists, and Google Gemma is listed though no such code exists |
| GDP-07 Enforced retention schedules | 2 | Per-category lifetimes documented, but no pg_cron or CI cron enforces anything, backup/auth-log claims are unreconciled, and rejected submissions retain identity indefinitely |
| GDP-08 Breach detection and response readiness | 1 | No incident-response runbook and no audit/action-log table; admin mutations leave partial traces at best; kept above 0 by no service_role in admin code and server-side is_admin checks |
| PLT-04 UGC safety apparatus: filter, report, block, respond | 2 | Pre-publication glyph review pillar is deliberate and systematic with server-side blocks existing, but no report intake exists anywhere and it is deferred to M56 |
| PLT-07 Hosting platform hardening: headers and deployment protection | 0 | Empty next.config.ts, no vercel.json, no middleware, no headers, and no recorded deployment protection; previews run against production data; application auth is the only gate |
| PRF-02 Client JavaScript bundle discipline | 1 | Deliberate client-boundary placement and a dynamically imported editor, but no analyzer or size tooling and no CI measures admin, so scored down per torn-pick-lower |
| PRF-03 Image and media delivery pipeline | 1 | Cover upload passes no cacheControl and does no downscale/recompress, preview uses a raw img, no next/image or transforms; lifted above 0 by type/size validation and reserved dimensions |
| PRF-05 Bounded, batched, lean client reads | 1 | Excellent O(1) aggregate round-trips and no awaited query in a loop, but no list read carries a limit, logs boards use select(*), and the moderation RPC returns all submissions with full strokes |
| PRF-08 Network and battery frugality | 3 | Zero setInterval/setTimeout, no channels, no client fetching, no retry loops, so idle network is zero by architecture; nothing detects a future leak (not 4) |
| PRV-01 PII inventory and schema minimization | 1 | No PII inventory anywhere (caps at 1); reads are otherwise narrow, but the moderation queue surfaces auth emails with purpose recorded only in prose |
| PRV-03 Third-party egress inventory (SDKs, fonts, CDNs) | 1 | Clean de-facto egress (no third-party host, no font CDN, no analytics SDK), but no written destination list and no CSP/connect-src enforcement |
| PRV-04 No personal data in logs and operator analytics | 2 | Aggregate-first analytics by documented house rule and every log call site is tag-plus-message only, but no shared logging helper, no lint rule, and the no-payload rule is convention-by-example |
| PRV-05 Private media: EXIF, signed URLs, cache lifetime | 2 | No user-photo access and the one media path is the deliberate public lab-assets bucket, but covers upload verbatim with no re-encode/EXIF strip and no automated bucket-flag check |
| PRV-06 Local and offline data protection | 2 | Minimal local persistence (SDK cookies plus one removed-after-read sessionStorage prefill key, a recorded decision), but no storage inventory and sign-out does not clear the prefill key |
| REL-01 Failure states distinct from empty states | 2 | Documented ErrorBlock pattern on all 10 analytics cards, but logs boards collapse failure into empty state, detail pages collapse errors into notFound(), no error.tsx, no tests |
| REL-02 Bounded timeouts and deliberate retries | 0 | No withTimeout/AbortSignal/timeout anywhere; every call relies on library defaults; the web withTimeout convention was never ported |
| REL-03 Atomic multi-step writes | 2 | Multi-table mutations go through RPCs and publish is atomic in one function, but the cover-image file-plus-row pair has no consistency story and updateLog is a non-atomic read-then-write |
| REL-04 Idempotence and double-submit protection | 2 | Universal client pending guards and real server-side moderation state-machine guards, but createLog and publish_admin_glyph have no server dedupe and log edits are emergent last-write-wins |
| REL-08 Production failures reach a human | 1 | No crash/error SDK, no log drain, no alert rule, no documented triage; 51 console.error sites terminate in unwatched Vercel or browser logs |
| SAF-03 UGC moderation state machine and takedown | 2 | Deep glyph pipeline with RLS-gated cross-user reads and handle hardening, but public pebbles, profiles, and avatar glyphs carry no moderation state and no report intake exists |
| SAF-04 Block integrity and anti-harassment enforcement | 1 | No operator-side enforcement (no admin block, connection severance, invite revoke, or suspension); kept above 0 because the shared two-directional block primitive exists and admin cannot weaken it |
| SAF-05 Bystander exposure on outbound paths | 2 | Operator-analytics outbound path aggregates counts and never enumerates soul names, with a hardened allowlist rule, but no removal channel for named third parties and no test asserts the invariant |
| SAF-07 Account takeover harm ceiling | 1 | MFA cannot be enrolled (TOTP disabled) and no destructive action requires re-auth; bulk email egress is unpaginated; positives are server-side is_admin, no service_role, and token rotation |
| SEC-01 Authentication and session lifecycle integrity | 2 | All auth via the SDK with getUser server-side validation and no manual JWT handling, but no session-refresh middleware (gap acknowledged in a comment) and no auth-flow tests |
| SEC-03 Security-definer RPC and privileged-role hygiene | 2 | Impressively uniform definer + in-body is_admin + revoke pattern verified server-side, but no negative (wrong-role) tests exist and is_admin(uuid) accepts a caller-supplied id granted to anon |
| SEC-04 Secrets kept out of clients, source, and logs | 2 | Publishable values only, no service_role under apps/admin, env files gitignored (deliberate scheme), but no secret scanner in CI and no recorded history scan or rotation note |
| SEC-05 Injection-safe input handling at trust boundaries | 2 | Server actions validate before writes, RPC bodies re-validate, no dynamic SQL, glyph strokes rendered inert, but one unjustified dangerouslySetInnerHTML, client-only upload gating, and zero hostile-input tests |
| SEC-06 Transport encryption and on-device data protection | 2 | No cleartext endpoints, SDK-managed cookie split reasoned about in comments, deliberate public bucket, but no security headers/HSTS/CSP and the non-httpOnly auth cookie was never documented |
| SEC-07 Dependency and build pipeline integrity | 1 | Committed lockfile and mostly tag-pinned actions, but no Dependabot/Renovate, no audit/osv job, and a third-party reusable workflow pinned to a mutable @main |
| SEC-08 Server endpoint and webhook hardening | 2 | Every server action and auth route has a deliberate DB-gated auth story with sanitized errors and no wildcard CORS, but no body-size caps, no login rate limiting documented, and no negative-path tests |
| TST-01 Core user paths have automated tests | 0 | Zero test files, no test script, no vitest config; core paths and testability-built pure modules have no test importing them |
| TST-02 Shared shapes tested against real cross-surface payloads | 0 | Admin produces/consumes logs rows, glyph strokes jsonb, and Lab Note YAML, and none has a decoder test fed verbatim other-surface payloads, against the repo's own standing rule |
| TST-03 Fixed bugs leave pinning regression tests | 0 | No pinning test can exist with no test files; fix commits never touch a test file and a reintroduced bug would only be caught by the admin using the tool |
| TST-04 Runnable harnesses for destructive cross-cutting operations | 1 | Destructive ops (admin_delete_glyph cascade, takedown, deleteLog) exist but no harness exercises them; only an 8-step manual checklist in the design doc |
| TST-05 Tests assert behavior with real oracles | 0 | No tests to sample, so no assertion of any kind exists; the only fixture-like artifacts are unexecuted UI playground data |
| TST-06 No merge without the touched surfaces' gates | 1 | An admin-only change triggers zero CI checks (no workflow runs admin lint or build) while Android runs ktlint plus unit tests, so admin merges green-by-absence |
| TST-07 One canonical test framework and idiom per surface | 0 | No test framework configured, no testing devDependency, no runnable test command, though the sibling web surface designates Vitest |
