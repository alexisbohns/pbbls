# SEC — Security

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Authentication and session handling, authorization (RLS, security-definer RPCs), secrets, injection, transport and storage protection, supply chain, API hardening.

---

## SEC-01 · Authentication and session lifecycle integrity

**Do all authentication flows and session lifecycles (issuance, refresh, expiry, revocation) go through the vetted auth SDK and behave consistently on every client surface?**

`authn` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Every sign-in, sign-up, password reset, OAuth callback, and magic-link flow uses the platform auth SDK rather than hand-rolled token parsing or storage. Sessions have bounded lifetimes, refresh is automatic and race-safe, and sign-out plus account deletion revoke sessions server-side, not just locally. Auth-state edge cases (expired refresh token, revoked session, concurrent refresh) degrade to a clean signed-out state instead of a half-authenticated UI. Protected server-side routes validate the session on the server, never only in client-side navigation guards.

*Why it matters:* For a product holding intimate personal data, account takeover of a single user is already a reportable breach. Multi-client products multiply the auth attack surface: each surface that improvises token handling is an independent way to lose a session.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Tokens are parsed, stored, or forwarded by hand-rolled code; sign-out only clears local UI state; no refresh or revocation logic is present anywhere on the surface. |
| **1 · Ad-hoc** | The auth SDK is used for the main sign-in path, but at least one flow (a deep-link callback, a background job, a server route) handles tokens manually, or sign-out behavior differs between screens or surfaces. |
| **2 · Defined** | All flows visibly go through the SDK with explicit session handling; remaining gaps (e.g. no server-side revocation on password change, unhandled refresh races) are known and written down in code comments, docs, or issues. |
| **3 · Managed** | Session lifecycle is uniform across the surface, edge states (expired refresh token, concurrent refresh, revoked session mid-use) are explicitly handled, and tests cover login, refresh, and sign-out paths. |
| **4 · Verified** | An automated harness or test suite exercises login, refresh, expiry, and revocation against a real backend in CI, and a failed assertion blocks merge; drift in auth handling is caught mechanically. |

### Audit checklist

- [ ] Grep each client for the auth SDK entry points (signInWith*, signOut, onAuthStateChange or the platform equivalent auth-state listener) and list every call site; confirm no flow bypasses them.
- [ ] Grep for manual JWT handling: atob(, base64 decode of token segments, jwt.decode, JWTDecode, split('.') on a token variable. Anything outside the SDK internals is a finding.
- [ ] Trace the OAuth/magic-link callback on each mobile client (iOS: URL scheme / universal link handler; Android: intent-filter in AndroidManifest.xml) and confirm the token exchange is delegated to the SDK.
- [ ] Read every sign-out call site and confirm it calls the SDK sign-out (with server-side/global revocation where offered) and clears local caches of user data, and that account deletion also revokes sessions.
- [ ] On web/admin, open the middleware and server components for protected routes and confirm the session is validated server-side (e.g. supabase.auth.getUser() on the server), not only via client redirects.
- [ ] Check how expired or revoked sessions surface: search for the SDK's TOKEN_REFRESHED / SIGNED_OUT events (or equivalents) and confirm the app transitions to a signed-out state rather than showing stale data.

### Monitoring signals

- Grep for 'atob(' or manual JWT segment splitting in app code returns nothing outside the SDK.
- A single shared sign-out helper exists and every sign-out UI action references it (grep the helper name, count call sites vs. sign-out buttons).
- CI contains a job that runs an auth-flow test (login, refresh, sign-out) against a real or local backend.
- Server-side session validation appears in middleware for every protected route group (grep for the auth check in middleware/route handlers).

### References

- [OWASP ASVS v4.0.3 — V3 Session Management](https://owasp.org/www-project-application-security-verification-standard/)
- [NIST SP 800-63B Digital Identity Guidelines — Sec. 7 Session Management](https://pages.nist.gov/800-63-3/sp800-63b.html)
- [OWASP Top 10:2021 — A07 Identification and Authentication Failures](https://owasp.org/Top10/)
- [OWASP MASVS v2 — MASVS-AUTH](https://mas.owasp.org/MASVS/)

### Typical remediation

Route every auth flow through the SDK, centralize sign-out and session-expiry handling in one helper per surface, add server-side session validation to protected routes, then encode login/refresh/revocation into a CI-run harness.

*Issue skeleton:* [`templates/sec-01.md`](../templates/sec-01.md)

---

## SEC-02 · Row-Level Security default-deny on every table

**Is Row-Level Security enabled and default-deny on every application table and storage bucket, with policies scoped to the authenticated user?**

`authz` · applies to: `supabase` · default impact **5/5** · weight **3/3**

Every table in the application schema has RLS enabled, and every policy grants the minimum: reads and writes scoped to auth.uid() (or an explicit relationship check), never a blanket using (true) on user data. Storage buckets holding user content are private, with object policies mirroring the owning table's access rules. Reference/lookup tables that are intentionally world-readable are readable only, and the intent is visible in the migration. No table is reachable by the anon or authenticated role beyond what a policy explicitly permits.

*Why it matters:* In a shared-database architecture, RLS is the single enforcement point all clients inherit; one table with RLS disabled or a permissive policy exposes every user's rows to every authenticated user through the auto-generated API. For intimate journal data this is the catastrophic failure mode of the whole product.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | One or more application tables have no 'enable row level security' statement in any migration, or grants to anon/authenticated exist with no policies, leaving rows world-readable via the data API. |
| **1 · Ad-hoc** | RLS is enabled on the obviously sensitive tables only; some tables rely on obscurity, and at least one policy on user data uses using (true) or an over-broad role without a recorded reason. |
| **2 · Defined** | Every table has RLS enabled with owner-scoped policies and intentional exceptions are commented in migrations; storage bucket policies exist but coverage was established by review, not by a runnable check. |
| **3 · Managed** | RLS coverage is complete and policies are exercised by tests or a verification script that queries as distinct users and asserts cross-user reads and writes fail, including on storage objects. |
| **4 · Verified** | A CI-run harness asserts rowsecurity = true for all application tables and that a foreign user cannot read or write another user's rows or objects; a new table without RLS fails the pipeline. |

### Audit checklist

- [ ] Grep migrations for 'create table' and build the full table list; cross-check every table also appears in an 'enable row level security' statement (grep -i 'row level security').
- [ ] Grep migrations for 'using (true)' and 'with check (true)'; for each hit on a table holding user data, verify it is a read-only policy on a reference table and the intent is commented.
- [ ] Grep for 'grant' statements to anon and authenticated; confirm no table-level grant exists without a corresponding restrictive policy.
- [ ] Inspect storage bucket creation migrations: confirm user-content buckets are created with public = false and that storage.objects policies scope access by owner or relationship, mirroring the table rules.
- [ ] Start the local database (db:start / db:reset) and query pg_tables for rowsecurity = false in the application schema; any row is a finding.
- [ ] Trace one sensitive table end to end: as two different test users via the REST API, attempt to select and update the other user's row and confirm both fail.

### Monitoring signals

- A verification script or CI job asserts rowsecurity = true for all application-schema tables (fails on any new unprotected table).
- Grep 'using (true)' in migrations returns hits only on read-only reference-table policies.
- Grep 'public = true' (or storage bucket public flags) returns hits only for buckets documented as intentionally public.
- A cross-user access test (two seeded users, mutual read/write attempts) runs in CI or a linked-project harness.

### References

- [Supabase Docs: Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [PostgreSQL Documentation: CREATE POLICY](https://www.postgresql.org/docs/current/sql-createpolicy.html)
- [OWASP Top 10:2021 — A01 Broken Access Control](https://owasp.org/Top10/)
- [CWE-862 Missing Authorization](https://cwe.mitre.org/data/definitions/862.html)
- [GDPR — Art. 32 Security of processing](https://eur-lex.europa.eu/eli/reg/2016/679/oj)

### Typical remediation

Enumerate tables without RLS and enable it with owner-scoped policies in one migration batch, convert any permissive write policy to an explicit ownership check, make user-content buckets private with mirrored object policies, then add a coverage assertion script to CI so the next unprotected table fails the build.

*Issue skeleton:* [`templates/sec-02.md`](../templates/sec-02.md)

---

## SEC-03 · Security-definer RPC and privileged-role hygiene

**Does every security-definer function pin its search_path, verify caller ownership or role inside the function body, and expose cross-user data only through explicit column allowlists, with operator surfaces gated by server-verified roles?**

`authz` · applies to: `admin` `supabase` · default impact **5/5** · weight **3/3**

Every function declared security definer sets an explicit search_path and re-derives the caller's identity from auth.uid() (never from a caller-supplied parameter) before reading or writing rows on the caller's behalf. Cross-user reads return an explicitly constructed projection (a jsonb/record allowlist of safe columns), never whole rows or internal identifiers. Privileged operations (moderation, refunds, catalog edits) check an admin role or service_role inside the function, and execute grants are revoked from roles that should not call them. Operator back-office routes enforce the same role check server-side, not by hiding UI.

*Why it matters:* Security-definer functions bypass RLS by design, so each one is a hand-audited hole in the default-deny wall; a missing ownership check or an unpinned search_path turns a convenience RPC into a privilege escalation. Admin surfaces that trust client-side role checks are the same failure at the HTTP layer.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Security-definer functions exist with no search_path setting and no auth.uid()/role check in the body, or they accept a user id parameter and act on it unverified; admin routes render for any authenticated user. |
| **1 · Ad-hoc** | Some definer functions check ownership, but the pattern is inconsistent; at least one function returns raw rows across users or is executable by anon without intent; admin role checks exist only in client components. |
| **2 · Defined** | All definer functions pin search_path and check auth.uid() or an admin role; cross-user reads use explicit projections; the pattern is documented as a house rule but verified only by review. |
| **3 · Managed** | The definer pattern (search_path, ownership check, allowlist projection, grant hygiene) is uniform across all functions, admin routes verify the role server-side, and negative tests exist (calling as the wrong user or role fails). |
| **4 · Verified** | A CI-run check parses migrations or the live schema and fails on any security-definer function missing a pinned search_path or executable by unintended roles, and a harness exercises wrong-user and wrong-role calls against every privileged RPC. |

### Audit checklist

- [ ] Grep migrations for 'security definer' and list every function; for each, confirm a 'set search_path' clause appears in the same definition (the latest re-emission wins, so check the newest migration touching each function).
- [ ] For each definer function that writes, confirm the body derives the acting user from auth.uid() and compares it to the owning row; flag any function that trusts a caller-supplied user id or row id without an ownership join.
- [ ] For each definer function that reads across users, confirm the return is built from an explicit column allowlist (e.g. jsonb_build_object with named safe fields) and never includes internal user identifiers or select *.
- [ ] Grep for 'revoke execute' and 'grant execute' on these functions; confirm anon cannot execute anything not meant for logged-out use and that service-role-only functions are locked down accordingly.
- [ ] Grep function bodies for admin/role checks (is_admin, role claims, auth.jwt()) on every moderation or catalog-mutation RPC; a privileged RPC without an in-body role check is a finding.
- [ ] In the operator app, open the root layout/middleware for the admin route group and confirm the admin role is verified server-side before data is fetched or mutations are accepted; client-only gating is a finding.

### Monitoring signals

- A script counts security-definer functions vs. those with 'set search_path'; the difference must be zero.
- Grep for definer functions returning 'select *' or 'to jsonb(row)' across user boundaries returns nothing.
- Negative-path tests (wrong user, wrong role) exist for privileged RPCs and run in CI or a linked-project harness.
- Admin route middleware contains a server-side role check (grep the middleware for the role verification call).

### References

- [PostgreSQL Documentation: CREATE FUNCTION — Writing SECURITY DEFINER Functions Safely](https://www.postgresql.org/docs/current/sql-createfunction.html)
- [OWASP ASVS v4.0.3 — V4 Access Control](https://owasp.org/www-project-application-security-verification-standard/)
- [CWE-285 Improper Authorization](https://cwe.mitre.org/data/definitions/285.html)
- [OWASP Top 10:2021 — A01 Broken Access Control](https://owasp.org/Top10/)

### Typical remediation

Normalize every definer function to the safe template (pinned search_path, auth.uid() ownership or role check, allowlist projection), fix grants in the same migration, add server-side role checks to operator routes, then add a schema lint and wrong-user/wrong-role harness to CI.

*Issue skeleton:* [`templates/sec-03.md`](../templates/sec-03.md)

---

## SEC-04 · Secrets kept out of clients, source, and logs

**Are privileged credentials (service keys, signing keys, webhook secrets, CI tokens) absent from client bundles, source control, and logs, with only publishable keys shipped to clients?**

`secrets` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **5/5** · weight **3/3**

Clients embed only credentials designed to be public (the API URL and the publishable/anon key); the service-role key and any signing or webhook secret exist only in server-side environment configuration and CI secret stores. Environment files with real values are gitignored, an example file documents required variables without values, and git history is clean of leaked keys. Build systems and logs never echo secret values.

*Why it matters:* A leaked service-role key bypasses RLS entirely and is equivalent to full database compromise across all users. Client bundles, git history, and CI logs are the three places such keys actually leak from in this stack class.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | A service-role key, signing secret, or private token appears in client code, a committed .env file, or a mobile config file; or git history contains a live secret with no rotation recorded. |
| **1 · Ad-hoc** | Secrets are mostly in environment variables, but discipline is accidental: no .env.example, no gitignore rule for local env files, or a build-time public-variable prefix (e.g. NEXT_PUBLIC_) applied to something non-public. |
| **2 · Defined** | A deliberate scheme exists (documented env var naming, gitignored env files, CI secrets context) and clients verifiably carry only publishable keys; history has not been scanned and no automation guards against regression. |
| **3 · Managed** | Secret placement is systematic across all surfaces, git history has been scanned, rotation of any past leak is recorded, and code review explicitly checks new env vars for correct public/private placement. |
| **4 · Verified** | A secret scanner (gitleaks, trufflehog, or platform secret scanning) runs in CI and blocks on findings; a grep-style check asserts privileged key names never appear under client app directories. |

### Audit checklist

- [ ] Grep the whole repo for 'service_role', 'SERVICE_ROLE', and provider secret prefixes (sk_, whsec_, -----BEGIN); any hit under a client app directory or in a committed env file is a finding.
- [ ] Check .gitignore covers .env, .env.local, and platform equivalents (xcconfig with secrets, local.properties, keystore files); confirm an example env file exists listing variable names with placeholder values only.
- [ ] On web/admin, list every NEXT_PUBLIC_-prefixed variable (grep 'NEXT_PUBLIC_') and confirm each one is genuinely publishable (URL, anon/publishable key, feature flags); anything privileged with that prefix is a finding.
- [ ] On iOS and Android, inspect Info.plist, xcconfig, BuildConfig fields, and string resources for embedded credentials beyond the publishable key; check the Android signing keystore and its passwords are not committed.
- [ ] Open every CI workflow: confirm secrets are referenced via the secrets context, never inlined, and that no step echoes or uploads env contents as artifacts.
- [ ] Run a history scan (gitleaks detect or equivalent) over the full git history; record and rotate anything found, then note the rotation in the decisions log.

### Monitoring signals

- A secret-scanning job (gitleaks/trufflehog or GitHub secret scanning + push protection) is active and blocking.
- Grep for 'service_role' under client app directories returns nothing.
- Grep committed files for values matching key formats (JWT-shaped strings, PEM headers) returns nothing outside test fixtures.
- .env* patterns present in .gitignore and no .env file tracked by git (git ls-files '*.env*' is empty).

### References

- [CWE-798 Use of Hard-coded Credentials](https://cwe.mitre.org/data/definitions/798.html)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [GitHub Docs: Security hardening for GitHub Actions — Using secrets](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)
- [OWASP MASVS v2 — MASVS-STORAGE](https://mas.owasp.org/MASVS/)

### Typical remediation

Move any misplaced secret to server-side env or CI secret storage, rotate anything that ever touched git history or a client bundle, add an example env file and gitignore rules, then wire a blocking secret scanner into CI.

*Issue skeleton:* [`templates/sec-04.md`](../templates/sec-04.md)

---

## SEC-05 · Injection-safe input handling at trust boundaries

**Is externally influenced input validated and safely encoded at every trust boundary: dynamic SQL, HTML rendering, deep links, uploaded files, and RPC payloads?**

`input-validation` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **2/3**

Dynamic SQL inside database functions uses parameterization or format() with quote_ident/quote_literal, never string concatenation of caller input. Web surfaces render user content through the framework's escaping by default; any raw-HTML sink is justified and sanitized. Mobile deep-link and universal-link handlers validate host, path, and parameters before acting on them. File uploads are validated server-side for type and size, and jsonb/RPC payloads are type- and bounds-checked before touching tables.

*Why it matters:* The shared database means one injectable RPC or one XSS sink on any surface can pivot into every user's data. User-generated journal text and media are hostile input by definition, and deep links are the classic mobile confused-deputy entry point.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Caller input is concatenated into executed SQL, rendered as raw HTML, or acted on from a deep link without any validation anywhere on the surface. |
| **1 · Ad-hoc** | The framework's defaults provide most protection incidentally, but at least one raw sink (EXECUTE with concatenation, dangerouslySetInnerHTML, an unchecked link handler, unvalidated upload) exists without justification. |
| **2 · Defined** | Trust boundaries are identified and handled deliberately: raw sinks are enumerated with sanitization or a written justification; server-side payload validation exists but coverage is partial or untested. |
| **3 · Managed** | Every boundary (SQL, HTML, links, uploads, RPC payloads) has explicit validation or encoding, hostile-input cases appear in tests (oversized payloads, script tags in text fields, malformed link parameters), and review checks new sinks. |
| **4 · Verified** | Automation guards the boundaries: a lint rule or CI grep fails on new raw sinks, and hostile-input tests run in CI including payloads produced by the other client surfaces. |

### Audit checklist

- [ ] Grep migration function bodies for 'execute' combined with '||' concatenation; every dynamic SQL statement must use parameter placeholders or format() with %I/%L; concatenated identifiers or literals are findings.
- [ ] Grep web/admin for dangerouslySetInnerHTML, innerHTML =, and markdown renderers configured with raw HTML enabled; each hit needs a sanitizer (e.g. DOMPurify) or a written justification for trusted-only content.
- [ ] Open the deep-link handlers (iOS: onOpenURL / scene delegate URL handling; Android: intent-filter recipients in AndroidManifest.xml and their activities) and confirm host/path allowlisting and parameter validation before navigation or data mutation.
- [ ] Trace one file-upload path end to end: confirm the server side (edge function or storage policy) enforces content type and size limits rather than trusting the client picker.
- [ ] For each RPC accepting jsonb or text payloads, read the function body for type checks, length bounds, and enum validation before insert/update; absence on user-writable fields is a finding.
- [ ] Feed one hostile fixture through a client write path in tests: a script tag in free text, an oversized string, a malformed timestamp; assert it is stored inert and rendered escaped.

### Monitoring signals

- CI grep for 'execute' + '||' in new migrations returns nothing (or a lint allowlist covers audited exceptions).
- Grep for dangerouslySetInnerHTML returns only allowlisted, sanitized call sites.
- Hostile-input fixtures (script tags, oversize payloads, precision-variant timestamps) exist in the test suite and run in CI.
- Deep-link handlers reference a shared URL-validation helper (grep the helper name from both mobile surfaces).

### References

- [CWE-89 SQL Injection](https://cwe.mitre.org/data/definitions/89.html)
- [CWE-79 Cross-site Scripting](https://cwe.mitre.org/data/definitions/79.html)
- [OWASP Top 10:2021 — A03 Injection](https://owasp.org/Top10/)
- [OWASP ASVS v4.0.3 — V5 Validation, Sanitization and Encoding](https://owasp.org/www-project-application-security-verification-standard/)
- [OWASP MASVS v2 — MASVS-PLATFORM](https://mas.owasp.org/MASVS/)

### Typical remediation

Replace concatenated dynamic SQL with format()/parameters, sanitize or remove raw HTML sinks, add allowlist validation to link handlers and server-side checks to uploads and RPC payloads, then freeze the state with a lint/CI grep and hostile-input tests.

*Issue skeleton:* [`templates/sec-05.md`](../templates/sec-05.md)

---

## SEC-06 · Transport encryption and on-device data protection

**Is data protected in transit (TLS everywhere, no cleartext exceptions) and at rest on device and in storage (secure credential stores, private buckets, scoped access to user media)?**

`transport-storage` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **2/3**

All network traffic uses TLS with platform transport security intact: no ATS opt-outs on iOS, no cleartext traffic permitted on Android, HTTPS-only endpoints in every client config. Credentials and tokens live in the platform secure store (Keychain on iOS, Keystore-backed storage on Android, httpOnly/secure cookies or SDK-managed storage on web), never in plain preferences or localStorage by hand. User media sits in private server-side buckets accessed via scoped policies or short-lived signed URLs, and locally cached sensitive data is excluded from world-readable locations and backups where the platform allows.

*Why it matters:* Emotional-state records and photos of third parties are exactly the data class where interception or a lost device becomes a serious breach with regulatory exposure. Platform defaults are strong, so most failures here are explicit opt-outs that a config audit can catch.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | A cleartext opt-out exists (NSAllowsArbitraryLoads true, usesCleartextTraffic true, or hardcoded http:// endpoints), or tokens/sensitive data sit in plain UserDefaults/SharedPreferences/localStorage, or user media lives in a public bucket. |
| **1 · Ad-hoc** | Platform defaults are intact by accident: no opt-outs found, but credential storage was never deliberately chosen (SDK defaults unexamined) and bucket privacy or signed-URL lifetimes were never reviewed. |
| **2 · Defined** | Transport and storage choices are deliberate and documented: ATS/cleartext configs verified clean, credentials confirmed in the secure store, buckets confirmed private with a chosen access pattern; no automation checks for drift. |
| **3 · Managed** | The above is systematic across all surfaces, cached sensitive data and backup exposure were reviewed, signed-URL lifetimes are bounded, and a checklist item in review covers new endpoints, caches, and buckets. |
| **4 · Verified** | CI or a config-lint asserts no transport opt-outs and no public user-content buckets (schema check on bucket flags), and storage of credentials is covered by a test or static check on each mobile surface. |

### Audit checklist

- [ ] Open every Info.plist and grep for NSAppTransportSecurity; NSAllowsArbitraryLoads or per-domain exceptions must be absent or justified for localhost/dev only.
- [ ] Open AndroidManifest.xml and any network_security_config XML; confirm usesCleartextTraffic is not true and no cleartextTrafficPermitted='true' domain rules exist outside debug builds.
- [ ] Grep all surfaces for 'http://' literals; anything outside localhost, tests, and XML namespaces is a finding.
- [ ] Find where the auth session is persisted on each mobile client: confirm Keychain usage on iOS and Keystore-backed (e.g. EncryptedSharedPreferences) storage on Android; grep for UserDefaults/SharedPreferences writes of tokens or user content.
- [ ] In storage migrations, list every bucket and its public flag; for private user-media buckets, confirm access is via object policies or signed URLs and note the signed-URL expiry used by clients.
- [ ] On web/admin, check how the SDK persists the session (cookies vs. storage) and that any cookie carrying auth is httpOnly, secure, and sameSite; verify no custom code copies tokens into localStorage.

### Monitoring signals

- Grep for NSAllowsArbitraryLoads and usesCleartextTraffic returns nothing (or only debug-scoped configs).
- Grep for 'http://' outside tests/localhost returns nothing.
- A schema check asserts user-content buckets have public = false.
- Grep for token writes to UserDefaults/SharedPreferences/localStorage returns nothing.

### References

- [Apple Developer Documentation: Preventing Insecure Network Connections](https://developer.apple.com/documentation/security/preventing-insecure-network-connections)
- [Android Developers: Network security configuration](https://developer.android.com/privacy-and-security/security-config)
- [OWASP MASVS v2 — MASVS-NETWORK, MASVS-STORAGE](https://mas.owasp.org/MASVS/)
- [CWE-319 Cleartext Transmission of Sensitive Information](https://cwe.mitre.org/data/definitions/319.html)
- [CWE-312 Cleartext Storage of Sensitive Information](https://cwe.mitre.org/data/definitions/312.html)
- [GDPR — Art. 32 Security of processing](https://eur-lex.europa.eu/eli/reg/2016/679/oj)

### Typical remediation

Remove transport opt-outs, move any misplaced credential into the platform secure store, flip public user-content buckets to private with signed-URL or policy access, and add config greps to CI so an opt-out cannot land silently.

*Issue skeleton:* [`templates/sec-06.md`](../templates/sec-06.md)

---

## SEC-07 · Dependency and build pipeline integrity

**Are dependencies pinned via lockfiles and monitored for known vulnerabilities, and is the build pipeline tamper-resistant (pinned actions, no untrusted PR code executing with secrets in scope)?**

`supply-chain` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **1/3**

Every package ecosystem in the repo (npm, Swift Package Manager, Gradle) commits a lockfile or resolved-versions file so builds are reproducible. An automated process (Dependabot, Renovate, or scheduled audit) surfaces known-vulnerable dependencies, and updates flow through reviewed PRs. CI workflows pin third-party actions (ideally to a commit SHA) and do not execute untrusted PR code with secrets in scope. Credential and token scoping for CI jobs and other automation is AGT-07's concern.

*Why it matters:* A compromised dependency or CI action executes with the ability to read secrets and modify shipped code on every surface at once. This is the risk class where absence of process, not presence of a bug, is the finding.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No lockfile is committed for at least one ecosystem, or CI workflows run mutable third-party actions (branch or 'latest' refs); no vulnerability monitoring exists. |
| **1 · Ad-hoc** | Lockfiles exist because tooling created them, but no vulnerability alerts are configured and dependency updates happen ad hoc; action pinning is inconsistent. |
| **2 · Defined** | Lockfiles are committed everywhere, an update/alert mechanism is configured (Dependabot/Renovate or a scheduled audit), and known gaps such as unpinned actions or ignored alerts are listed. |
| **3 · Managed** | Vulnerability alerts are triaged on a cadence, actions are version-pinned with a review rule for bumps, no workflow exposes secrets to untrusted PR code, and mobile toolchains resolve from committed version files. |
| **4 · Verified** | CI fails on high-severity known vulnerabilities (audit/osv-scanner gate), third-party actions are SHA-pinned and updated by bot PRs, and a periodic check verifies no ecosystem drifted out of lockfile coverage. |

### Audit checklist

- [ ] Confirm lockfiles are committed: package-lock.json at the workspace root, Package.resolved for the iOS project, and Gradle version catalogs or lockfiles for Android (git ls-files each).
- [ ] Check for .github/dependabot.yml or renovate.json; if absent, check whether any CI workflow runs npm audit, osv-scanner, or an equivalent on a schedule.
- [ ] Open every workflow in .github/workflows and list third-party action references; flag any not pinned to at least a major version tag, and note which are SHA-pinned.
- [ ] Flag any pull_request_target usage or workflow that checks out and executes untrusted PR code with secrets available; token permission scoping itself is audited under AGT-07.
- [ ] Grep workspace package.json files for postinstall/preinstall scripts and dependencies fetched from git URLs or tarball URLs instead of the registry.
- [ ] Check release/deploy workflows (mobile release lanes included) for secret exposure to forked-PR triggers.

### Monitoring signals

- dependabot.yml or renovate config exists and open update PRs are recent (the bot is alive, not abandoned).
- A CI job fails the build on high-severity audit findings.
- Grep workflows for '@main' or '@master' action refs returns nothing.

### References

- [OWASP Top 10:2021 — A06 Vulnerable and Outdated Components](https://owasp.org/Top10/)
- [OWASP Top 10:2021 — A08 Software and Data Integrity Failures](https://owasp.org/Top10/)
- [GitHub Docs: Security hardening for GitHub Actions — Using third-party actions](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)
- [CWE-1104 Use of Unmaintained Third Party Components](https://cwe.mitre.org/data/definitions/1104.html)

### Typical remediation

Commit missing lockfiles, enable Dependabot or Renovate across ecosystems, pin actions to reviewed versions, remove any secret exposure to untrusted PR code, then add a severity-gated audit job so vulnerable dependencies block merge instead of accumulating. Over-broad automation tokens are filed under AGT-07.

*Issue skeleton:* [`templates/sec-07.md`](../templates/sec-07.md)

---

## SEC-08 · Server endpoint and webhook hardening

**Are server-side endpoints (edge functions, API routes, inbound webhooks) hardened with caller verification, strict CORS, request size caps, rate limiting on expensive paths, and signature verification on webhooks?**

`api-hardening` · applies to: `web` `admin` `supabase` · default impact **4/5** · weight **2/3**

Every server-side function or route verifies the caller before doing work: a validated JWT for user endpoints, a role check for privileged ones, and an HMAC signature (timing-safe compare, replay-window check) for inbound webhooks. CORS responses allowlist known origins rather than reflecting or wildcarding with credentials. Request bodies are size-capped before parsing, expensive endpoints (auth, media processing, account purge) carry rate limits or platform-level abuse protection, and error responses expose no stack traces, SQL, or internal identifiers.

*Why it matters:* Edge functions and API routes sit outside the database's RLS umbrella, so each one re-implements its own gate; a function that trusts an unverified header or parses unbounded bodies is an open door or a denial-of-service lever. Webhooks without signature verification let anyone forge platform events.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | At least one server-side endpoint performs reads or writes with no caller verification, or an inbound webhook is processed without signature verification, or CORS is wildcarded on a credentialed endpoint. |
| **1 · Ad-hoc** | Most endpoints verify the JWT because the platform default does it, but at least one opts out without a compensating check, and body caps, rate limits, and error hygiene were never considered. |
| **2 · Defined** | Every endpoint has a deliberate auth story (verified JWT, role check, or documented public intent), webhooks verify signatures, and CORS is allowlisted; size caps and rate limits exist only where an incident forced them. |
| **3 · Managed** | Verification, CORS allowlists, body caps, and rate limits on expensive paths are uniform across endpoints, error responses are sanitized, and negative tests exist (missing token, forged signature, oversized body all rejected). |
| **4 · Verified** | A harness or CI test suite exercises each endpoint's rejection paths (401 without token, 403 wrong role, 401/400 bad signature, 413 oversized body), and adding an endpoint without these tests fails review tooling. |

### Audit checklist

- [ ] List every edge function and API route; for each, identify the caller-verification step (platform JWT verification setting or an explicit Authorization header validation, e.g. a shared client that forwards and validates the caller's JWT) and flag any function reachable without one.
- [ ] Grep functions/routes for Access-Control-Allow-Origin; '*' combined with credentials or on any mutating endpoint is a finding; confirm allowed origins come from a maintained allowlist.
- [ ] Grep for req.json() / await request body reads and check for a content-length or byte-size guard before parsing; note which endpoints accept media or base64 payloads unbounded.
- [ ] For every inbound webhook, read the handler for signature verification: HMAC over the raw body, a timing-safe comparison, and a timestamp/replay check; verification after parsing or via string equality is a finding.
- [ ] Identify the expensive endpoints (auth flows, media composition, account purge, exports) and check for rate limiting (per-user or per-IP) or a documented reliance on platform abuse protection.
- [ ] Trigger one failure per endpoint in a test or manually (missing token, malformed body) and read the response: stack traces, SQL fragments, or internal ids in the body are findings.

### Monitoring signals

- Grep for wildcard Access-Control-Allow-Origin on mutating endpoints returns nothing.
- Every webhook handler references a shared verify-signature helper using a timing-safe compare (grep the helper name).
- Negative-path tests (401/403/413 assertions) exist per endpoint and run in CI.
- A body-size guard constant is referenced by every endpoint that parses a request body.

### References

- [OWASP ASVS v4.0.3 — V13 API and Web Service](https://owasp.org/www-project-application-security-verification-standard/)
- [OWASP API Security Top 10 (2023) — API4:2023 Unrestricted Resource Consumption](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [CWE-347 Improper Verification of Cryptographic Signature](https://cwe.mitre.org/data/definitions/347.html)
- [CWE-770 Allocation of Resources Without Limits or Throttling](https://cwe.mitre.org/data/definitions/770.html)

### Typical remediation

Add caller verification to any open endpoint, wrap webhook handling in raw-body HMAC verification with a replay window, introduce shared body-cap and CORS-allowlist helpers, add rate limits to expensive paths, then encode the rejection paths as CI-run negative tests.

*Issue skeleton:* [`templates/sec-08.md`](../templates/sec-08.md)
