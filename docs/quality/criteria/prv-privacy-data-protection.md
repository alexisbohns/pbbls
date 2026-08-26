# PRV — Privacy & Data Protection

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Engineering privacy posture: minimization, PII flows, telemetry restraint, logs hygiene, media handling, local data, exposure surfaces, deletion propagation, ambient on-device exposure.

---

## PRV-01 · PII inventory and schema minimization

**Is every personal-data field that is collected, stored, or returned traceable to a documented purpose, with schemas and payloads carrying no more than that purpose needs?**

`minimization` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **2/3**

The product maintains an inventory of personal-data fields (including special-category adjacents such as emotional state, valence, or named third parties) mapping each field to where it is collected, stored, transmitted, and displayed. Database schemas, RPC return shapes, and client payloads carry only fields with a stated purpose. Speculative columns, over-wide reads (whole-row or select-star on personal-data tables), and echoing internal identifiers to clients are treated as defects, not style.

*Why it matters:* In a product storing intimate records and names of non-consenting third parties, every stored field is standing liability; data never collected cannot leak. GDPR Art. 5(1)(c) and Art. 25 make minimization a legal duty for EU-first products, not a preference.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No inventory exists anywhere in the repo; tables and payloads accumulate fields with no recorded purpose; clients read whole rows and render whatever arrives. |
| **1 · Ad-hoc** | Some RPCs project narrow shapes but others return whole rows of personal-data tables; a partial field list exists in one feature's design doc only. |
| **2 · Defined** | A written PII inventory covers the main tables and flows; new personal-data columns state a purpose in the migration or spec, but enforcement is reviewer diligence and some known over-wide reads remain open. |
| **3 · Managed** | The inventory covers all surfaces and is updated in the same change that adds a field; RPCs and client queries name explicit column lists; payload-versus-inventory checks are a routine review step. |
| **4 · Verified** | Automation ties schema to inventory: a CI check diffs generated database types against the inventory and fails on undocumented personal-data fields, and a lint or harness rejects whole-row returns and select-star on tables flagged as personal data. |

### Audit checklist

- [ ] Locate the PII inventory: search docs/ for 'inventory', 'PII', 'personal data', 'ROPA', 'data map'. If none exists, the criterion caps at level 1.
- [ ] Enumerate personal-data columns from the generated DB types (e.g. packages/supabase/types/database.ts): list every column holding names, free text, emotion or valence values, media paths, timestamps of intimate events, or identifiers of third parties; check each appears in the inventory with a purpose.
- [ ] Grep clients for over-wide reads: rg "select\('\*'\)|\.select\(\)" across the web/admin data layers and the equivalent query builders in iOS/Android; flag any hit on a table holding personal data.
- [ ] Read each security definer RPC in migrations (rg 'create or replace function' packages/supabase/supabase/migrations/) and confirm return shapes are explicit column lists or jsonb allowlists, never a row type of a personal-data table.
- [ ] Pick two recent migrations that add columns to user-owned tables and check whether the migration comment or its linked spec states the purpose of each new personal-data field.

### Monitoring signals

- rg "select\('\*'\)" over client data layers returns no hits on personal-data tables
- A CI job diffs generated database types against the PII inventory and fails on unlisted personal-data columns
- Every migration adding a column to a user-owned table carries a purpose comment, checkable by a conventional marker grep

### References

- [GDPR — Art. 5(1)(c) data minimisation; Art. 25 data protection by design and by default](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [GDPR — Art. 30 records of processing activities](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- EDPB Guidelines 4/2019 on Article 25 Data Protection by Design and by Default — Guidelines 4/2019, section on minimisation
- [OWASP MASVS v2 — MASVS-PRIVACY-1 (minimize access to sensitive data)](https://mas.owasp.org/MASVS/)

### Typical remediation

Author the inventory from the generated DB types, one row per personal-data field with purpose and flows. Narrow over-wide selects and RPC returns to explicit lists. Add a CI diff between generated types and the inventory so a new field cannot land undocumented.

*Issue skeleton:* [`templates/prv-01.md`](../templates/prv-01.md)

---

## PRV-02 · Analytics restraint and consent

**Is behavioral analytics limited to named product questions, computed from first-party data where possible, and gated on a recorded user consent whenever it exceeds strict necessity?**

`telemetry` · applies to: `web` `ios` `android` `supabase` · default impact **4/5** · weight **2/3**

Analytics is deliberate: each metric or event answers a stated question, is computed server-side from data the product already holds where possible, and avoids per-user behavioral profiles. Any client-side tracking beyond what the requested service strictly requires stays off until the user consents; the consent decision is persisted with a timestamp and policy version, and declining leaves the product fully functional.

*Why it matters:* In an emotionally sensitive product, usage patterns are themselves sensitive (when a user records, how often, at what valence). ePrivacy Art. 5(3) and GDPR consent rules make non-essential client-side tracking without prior opt-in unlawful for EU users, and EU regulators actively fine it.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | A third-party analytics SDK or pixel initializes unconditionally at app start; events fire before any consent UI exists; no consent record is stored anywhere. |
| **1 · Ad-hoc** | Analytics exists with no written purpose per event; a consent banner exists on one surface but the SDK initializes before the answer, or sibling surfaces track without asking. |
| **2 · Defined** | Events have stated purposes and consent is requested before non-essential collection on every surface, but consent state lives client-side only and nothing stops a developer adding an unguarded event. |
| **3 · Managed** | Consent with timestamp and version is persisted server-side; analytics is server-computed aggregates or consent-gated events on all surfaces; tests cover the declined path (no analytics traffic). |
| **4 · Verified** | An automated check (CI test or runtime harness) proves no analytics call or event write occurs pre-consent or post-decline, and adding an event must pass a gate (typed wrapper plus lint rule) that enforces the consent check. |

### Audit checklist

- [ ] Inventory analytics code: rg -i 'posthog|amplitude|mixpanel|firebase|segment|plausible|matomo|gtag|appsflyer|analytics' across all client workspaces plus package.json, Package.resolved, and gradle version catalogs; list every hit.
- [ ] Find where consent is stored: rg -i 'consent' in migrations and client code; verify the record carries a timestamp and policy version, not a bare boolean, and that it is persisted server-side.
- [ ] Trace client startup (Next.js root layout and providers, the iOS App struct, the Android Application class) and confirm no analytics initialization or event emission precedes the consent check.
- [ ] Read the server-side analytics views and RPCs (rg 'analytics' in migrations): confirm they aggregate (counts, distincts, cohorts) rather than exposing per-user behavioral timelines beyond an operational need, and that access is gated to operators.
- [ ] Exercise or read the declined path: declining consent must disable all event emission while the product stays fully functional; look for a test asserting zero analytics requests when declined.

### Monitoring signals

- rg -i 'gtag|mixpanel|amplitude|segment' returns nothing, or every hit sits behind a single consent-checking wrapper module
- A test exists asserting no analytics network traffic or event insert when consent is declined
- Migrations define a consent record with timestamp and policy version, written at signup or first prompt

### References

- [ePrivacy Directive 2002/58/EC — Art. 5(3) (storage of or access to information on terminal equipment)](https://eur-lex.europa.eu/eli/dir/2002/58/oj)
- [GDPR — Art. 4(11) and Art. 7 (definition and conditions of consent)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- EDPB Guidelines 05/2020 on consent under Regulation 2016/679 — Guidelines 05/2020
- [Apple App Store Review Guidelines — Guideline 5.1.2 (Data Use and Sharing / App Tracking Transparency)](https://developer.apple.com/app-store/review/guidelines/)

### Typical remediation

Prefer server-side aggregates over client event streams. Funnel any client analytics through one consent-checking module and forbid direct SDK imports elsewhere via lint. Persist consent with timestamp and version, and add a declined-path test.

*Issue skeleton:* [`templates/prv-02.md`](../templates/prv-02.md)

---

## PRV-03 · Third-party egress inventory (SDKs, fonts, CDNs)

**Is every third-party network destination and SDK enumerated and justified, with personal data confirmed absent from each egress or covered by a documented processor relationship?**

`third-parties` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **2/3**

The product knows exactly which external hosts its clients and backend contact (SDKs, font and CDN loads, error reporters, outbound calls from serverless functions) and what data each receives. Static assets are self-hosted, or a remote load is a deliberate documented choice. No personal data reaches a third party without a purpose and a processor agreement, and dependency changes that open a new egress destination receive privacy review, including EU transfer implications for non-EU recipients.

*Why it matters:* Every third-party host receives at least the user's IP address and request context, which EU case law on remote font loading already treats as personal data. For a sensitive product, an unnoticed SDK is an unnoticed data recipient, and GDPR Chapter V applies the moment the recipient sits outside the EU.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No one can list the external hosts; clients load fonts and scripts from CDNs, SDKs were added ad-hoc, and serverless functions send user data to external APIs with no record of it. |
| **1 · Ad-hoc** | Some assets are self-hosted, but other remote loads remain by accident; the SDK list is whatever the lockfiles happen to contain. |
| **2 · Defined** | A written list of third-party destinations exists with a purpose per entry; known deviations are tracked; new SDKs get informal review. |
| **3 · Managed** | The list is verified complete across surfaces (checked against lockfiles, mobile dependency manifests, CSP, and serverless code) and is re-reviewed on dependency changes; each egress is confirmed personal-data-free or covered by a DPA note. |
| **4 · Verified** | Egress is enforced: a Content-Security-Policy or network allowlist pins web destinations, mobile privacy manifests and data-safety declarations match the inventory, and a CI check fails when a new external hostname or phoning-home SDK appears in source without an inventory update. |

### Audit checklist

- [ ] Build the destination list: rg -o 'https://[a-z0-9.-]+' across client source, next.config, HTML heads, Info.plist, AndroidManifest, and serverless functions; dedupe and classify each host first-party versus third-party.
- [ ] Check fonts and scripts: rg 'fonts.googleapis|fonts.gstatic|unpkg|jsdelivr|cdn\.' in web surfaces; each hit must be a documented deliberate choice or converted to a self-hosted asset.
- [ ] Diff dependency manifests (package.json workspaces, Package.resolved, gradle libs.versions.toml) for SDKs that phone home (analytics, crash reporting, attribution, ads); confirm each appears in the third-party inventory with a purpose.
- [ ] Read serverless/edge functions for outbound fetch calls; for each, identify exactly which user data fields the request body or headers carry and match against the inventory.
- [ ] For web surfaces, check whether a Content-Security-Policy exists (next.config headers, vercel.json, middleware) and whether its connect-src/font-src/script-src pin the observed destinations; on iOS, check the privacy manifest lists the same domains.

### Monitoring signals

- A committed third-party inventory exists and a CI grep fails when a new external hostname appears in source without an inventory entry
- CSP headers present on web surfaces with explicit connect-src and font-src allowlists
- rg 'fonts.googleapis|unpkg|jsdelivr' over client source returns nothing, or only hosts listed in the inventory

### References

- [GDPR — Art. 28 (processor) and Art. 44 (general principle for transfers)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Apple Developer Documentation: Privacy manifest files — Privacy manifests and tracking domains](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files)
- [Google Play Developer Content Policy — User Data policy (Data safety declarations)](https://play.google/developer-content-policy/)
- [OWASP MASVS v2 — MASVS-PRIVACY (third-party data sharing)](https://mas.owasp.org/MASVS/)

### Typical remediation

Self-host fonts and static assets. Write the destination inventory with purpose and data per host. Add a CSP with explicit allowlists on web surfaces and keep mobile privacy declarations in sync. Add a CI grep diffing external hostnames in source against the inventory.

*Issue skeleton:* [`templates/prv-03.md`](../templates/prv-03.md)

---

## PRV-04 · No personal data in logs and operator analytics

**Are logs, error reports, and operator-facing analytics free of personal content by construction, carrying opaque identifiers, codes, and counts only?**

`logs-hygiene` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **2/3**

Client logs, server and serverless logs, error reporters, and operator dashboards never carry personal content (free text, emotional values, names of third parties, media URLs bearing credentials); they carry opaque IDs, error codes, and counts. Error-path logging follows a written convention (log the code and entity ID, never the payload), error reporters are configured to scrub, and operator analytics is aggregate-first with row-level access reserved for an explicit, purposeful moderation path.

*Why it matters:* Logs outlive intent and flow to the widest audience: hosting consoles, log drains, terminal scrollback, screenshots. CWE-532 leakage is the classic route by which special-category data escapes an otherwise well-secured database.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Payloads are logged wholesale (console.log of responses, print of decoded models); error reporters capture request bodies; the operator surface lists raw user content as analytics. |
| **1 · Ad-hoc** | Some sensitive spots avoid logging but no rule exists; call sites mix payload logs and ID-only logs depending on the author. |
| **2 · Defined** | A written convention exists (IDs and codes only); most call sites follow it; the error reporter is configured to scrub known fields; exceptions are listed and known. |
| **3 · Managed** | Logging goes through shared helpers that accept only typed ID-level fields; log lines are a review checkpoint; operator analytics reads aggregate views, with row-level access confined to a moderation flow that states its purpose. |
| **4 · Verified** | Enforcement exists: a lint rule or CI grep blocks raw payload logging in data-layer paths, reporter scrubbing is covered by a test, and produced logs or drain configs are periodically scanned for personal-data patterns. |

### Audit checklist

- [ ] rg 'console\.(log|error|warn)' across web and admin data layers; read each hit and flag any that passes a response body, user content, or a URL carrying a token.
- [ ] rg 'print\(|NSLog|os_log|Log\.(d|e|i|w)|Timber' in the iOS and Android codebases; apply the same test to each hit.
- [ ] Read serverless/edge functions for console.log of request bodies or user records, and note what the hosting platform retains and for how long.
- [ ] If an error reporter is present (rg -i 'sentry|crashlytics|bugsnag'), open its init config: verify PII defaults are off, a beforeSend-style scrubber exists, and breadcrumbs do not capture fetch bodies.
- [ ] Open the operator analytics queries and views: confirm they aggregate; list any operator page that renders raw user free text, emotional values, or media outside an explicit moderation flow.

### Monitoring signals

- A lint rule restricting console/print logging is active on data-layer paths, with an explicit allowlist
- rg of logging calls in data providers returns only ID-and-code-level lines, never serialized payloads
- Error reporter config contains a tested scrubbing hook, and PII-capturing defaults are explicitly disabled

### References

- [CWE-532: Insertion of Sensitive Information into Log File — CWE-532](https://cwe.mitre.org/data/definitions/532.html)
- [OWASP ASVS 4.0.3 — V7.1 Log Content](https://owasp.org/www-project-application-security-verification-standard/)
- [GDPR — Art. 32 (security of processing)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)

### Typical remediation

Introduce a logging helper that accepts an error code plus entity ID only, sweep existing call sites onto it, configure and test reporter scrubbing, and move operator analytics to aggregate views with row-level reads gated behind purposeful moderation RPCs.

*Issue skeleton:* [`templates/prv-04.md`](../templates/prv-04.md)

---

## PRV-05 · Private media: EXIF, signed URLs, cache lifetime

**Are user photos private by default: metadata-stripped on upload, served only through short-lived signed URLs from non-public storage, and cached no longer than their access grant?**

`media` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **5/5** · weight **3/3**

User-uploaded media lands in private storage with per-user path scoping enforced by server-side policies. Clients strip or consciously handle embedded metadata (GPS above all) before upload, ideally by re-encoding. Reads go through expiring signed URLs or an equivalent authorized proxy, never permanent public URLs, and client caches key on the object rather than the signed URL, with a bounded lifetime so a revoked or deleted object stops being reachable within a defined window.

*Why it matters:* Photos are the highest-impact single asset in an intimate product: one public bucket or one long-lived leaked URL exposes faces, places, and context. Mobile operating systems embed location metadata by default, so stripping must be an explicit engineering act, not an assumption.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | The bucket is public or URLs are unsigned and permanent; EXIF passes through untouched; anyone holding a URL can read the object forever. |
| **1 · Ad-hoc** | The bucket is private and signed URLs are used somewhere, but TTLs are arbitrary or very long, EXIF was never considered, and at least one code path still serves public URLs. |
| **2 · Defined** | Private buckets with owner-scoped storage policies and signed URLs on every path; a recorded decision exists on EXIF (strip, or documented retention of capture date only); TTLs are chosen but caches can outlive the grant. |
| **3 · Managed** | The upload pipeline re-encodes or strips metadata on every surface with tests; signed-URL TTL and client cache lifetime are set together and documented; storage policies are exercised against cross-user reads. |
| **4 · Verified** | A harness proves the contract: an automated test uploads an EXIF-laden fixture and asserts the stored object carries no GPS; CI asserts bucket visibility and policy shape; cross-user URL access is exercised and denied. |

### Audit checklist

- [ ] Open storage bucket definitions in migrations and config (rg 'storage.buckets' and 'public' in packages/supabase): record each bucket's public flag and the RLS policies on storage.objects, checking user-media writes and reads are scoped to a {user_id}/ prefix.
- [ ] rg 'getPublicUrl' across all clients; any hit on a bucket holding user media is a finding, whatever the surrounding code intends.
- [ ] rg 'createSignedUrl|signedUrl' and record the TTL passed on each surface; then find each client's URL/image cache and check its keying (object path, not signed URL) and its eviction relative to the TTL.
- [ ] Trace the upload path on each client (picker, processing, upload): verify a re-encode or explicit metadata strip happens before upload and note whether GPS specifically is dropped; check for a GPS-bearing test fixture.
- [ ] Check deletion interplay: when a pebble or account is deleted, confirm the storage objects are removed and cached signed URLs age out within the documented window.

### Monitoring signals

- rg 'getPublicUrl' over user-media repositories returns nothing
- An automated test uploads a GPS-tagged fixture image and asserts the processed output carries no location metadata
- Signed-URL TTL and cache-lifetime constants are defined together and referenced by tests on each client

### References

- [Supabase Storage: Access Control — Storage security and access control](https://supabase.com/docs/guides/storage/security/access-control)
- [CWE-732: Incorrect Permission Assignment for Critical Resource — CWE-732](https://cwe.mitre.org/data/definitions/732.html)
- [GDPR — Art. 5(1)(c) data minimisation and Art. 32 security of processing](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [OWASP Top 10:2021 — A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)

### Typical remediation

Flip user-media buckets to private with owner-prefixed policies, route all reads through short-lived signed URLs, re-encode images on upload (canvas, ImageIO, or Bitmap re-compression drops EXIF), and align cache lifetime with the TTL while keying caches by object path.

*Issue skeleton:* [`templates/prv-05.md`](../templates/prv-05.md)

---

## PRV-06 · Local and offline data protection

**Is personal data at rest on the device or in the browser limited to what the feature needs, held in platform-appropriate protected storage, and fully cleared on sign-out?**

`local-data` · applies to: `web` `ios` `android` `admin` · default impact **3/5** · weight **2/3**

Client-side persistence (drafts, caches, tokens, offline stores) holds the minimum required, keeps secrets in the platform's protected storage (Keychain, Android Keystore or EncryptedSharedPreferences, the auth SDK's default rather than hand-rolled web storage), makes a deliberate backup-inclusion decision where the platform allows one, and is fully cleared on sign-out and on account deletion. Web storage (localStorage, IndexedDB, service-worker caches) is treated as readable by any code on the origin and never holds more than convenience state.

*Why it matters:* Devices are shared, lost, and backed up, and browser storage is exposed to any script on the origin. Intimate drafts and cached feeds sitting on disk are the most common mobile audit finding, which is why MASVS dedicates a whole category to storage.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Tokens or content sit in plain localStorage, UserDefaults, or SharedPreferences; sign-out clears nothing; caches grow unbounded. |
| **1 · Ad-hoc** | The auth SDK's defaults protect the session token, but app data (drafts, cached content) persists unencrypted with no inventory, and sign-out leaves data behind. |
| **2 · Defined** | A recorded decision exists on what may persist locally per surface; secrets are in Keychain/Keystore; sign-out clears the known stores; remaining gaps (e.g. an image cache) are listed. |
| **3 · Managed** | Local persistence is inventoried per surface, protected storage is verified, sign-out and deletion paths clear every store, and tests exercise the clearing. |
| **4 · Verified** | Automated checks: a test enumerates app storage after sign-out and asserts it is empty or convenience-only; lint or CI forbids plain-preference APIs for flagged data; backup-exclusion or extraction-rules config is asserted by a test. |

### Audit checklist

- [ ] Web/admin: rg 'localStorage|sessionStorage|indexedDB|caches\.' and list every stored key; flag entries containing content, tokens, or names of people; check how the auth client is configured to store its session.
- [ ] iOS: rg 'UserDefaults|FileManager|isExcludedFromBackup|kSecAttr' and verify secrets use Keychain while content files carry a deliberate file-protection and backup decision.
- [ ] Android: rg 'SharedPreferences|DataStore|EncryptedSharedPreferences' and open AndroidManifest plus dataExtractionRules for the allowBackup decision; verify flagged data uses protected storage.
- [ ] Find the sign-out implementation on each surface and enumerate every store it clears; diff that list against the storage inventory built in the prior steps.
- [ ] Check offline drafts and caches for scope: bounded in size and age, and cleared on account deletion as well as sign-out.

### Monitoring signals

- A per-client test asserts local stores are empty (or convenience-only) after sign-out
- rg 'localStorage.setItem' returns only convenience keys (theme, UI state), never content or token keys
- AndroidManifest carries an explicit allowBackup/dataExtractionRules decision committed to the repo

### References

- [OWASP MASVS v2 — MASVS-STORAGE-1 and MASVS-STORAGE-2](https://mas.owasp.org/MASVS/)
- [Android Developers: App security best practices — Store data safely](https://developer.android.com/topic/security/best-practices)
- [CWE-312: Cleartext Storage of Sensitive Information — CWE-312](https://cwe.mitre.org/data/definitions/312.html)

### Typical remediation

Inventory local stores per surface, move secrets to Keychain/Keystore or the auth SDK's defaults, add a single clear-all routine invoked by both sign-out and account deletion, set backup decisions explicitly, and add the post-sign-out emptiness test.

*Issue skeleton:* [`templates/prv-06.md`](../templates/prv-06.md)

---

## PRV-07 · Cross-user exposure: field-set adequacy and minimality

**Is the field set each cross-user projection exposes minimal and adequate: every allowlisted field needed by the consuming feature, internal identifiers and linkable attributes excluded, and each visibility grade's exposed set enumerated and justified?**

`exposure-surfaces` · applies to: `web` `ios` `android` `supabase` · default impact **5/5** · weight **3/3**

For every cross-user exposure path (public profiles, shared items, visibility grades, connections, feeds), the exact set of fields the projection returns is enumerated, and each field earns its place: it is needed by the consuming feature, it is the least-revealing form that serves the need, and internal identifiers (user_id, email) or attributes linkable across contexts are excluded. Clients do not receive-and-hide: the payload fetched for another user contains nothing the UI merely declines to render. The existence and hygiene of the allowlist mechanism itself (definer hardening, search_path pinning, in-body ownership checks) is SEC-03's concern; this criterion audits whether the allowlisted set is the right set.

*Why it matters:* An allowlist mechanism can be perfectly built and still leak: one over-generous field (an email, a stable internal identifier, an exact timestamp that fingerprints activity) turns intimate records into linkable data. Field-set minimality reviewable in one place is what GDPR Art. 25(2) default-limitation asks of engineering, and it is a different audit from whether the projection machinery is hardened.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Nobody can state what fields other users receive; projections return whole rows or generous supersets and clients hide the excess in the UI. |
| **1 · Ad-hoc** | Field sets were chosen ad hoc; internal identifiers or linkable attributes appear in at least one cross-user payload and get fixed reactively when someone notices. |
| **2 · Defined** | The exposed field set per path is documented and mostly minimal, but per-field justification is missing and receive-and-hide occurs on some clients. |
| **3 · Managed** | Every cross-user path has an enumerated, justified field set per visibility grade, internal identifiers and linkable attributes are excluded, and clients render everything they receive. |
| **4 · Verified** | A runnable harness signs in as unrelated and connected users and asserts exactly the allowed field set, and nothing more, per exposure path and grade; adding a field to a cross-user projection requires a recorded justification (review rule or CI diff on projection bodies). |

### Audit checklist

- [ ] Enumerate cross-user read paths and, for each, write down the exact field list returned; the mechanism inventory (definer functions, policies) can be reused from the SEC-03 audit rather than rebuilt.
- [ ] For each returned field, record the consuming feature and whether a less-revealing form would serve it (an initial instead of a full name, a week instead of an exact timestamp); a field with no consumer is a finding.
- [ ] Flag internal identifiers and linkable attributes: user_id, email, stable internal ids, exact timestamps, and any value repeated across contexts that permits correlation.
- [ ] On clients, check for receive-and-hide: the payload fetched for another user must not contain fields the UI merely declines to render.
- [ ] Run or read the cross-user verify harnesses (scripts that authenticate as a second user): confirm they assert both allowed fields present and disallowed fields absent, per visibility grade, including the stranger case.

### Monitoring signals

- A harness reads another user's shared data as a stranger and as a connection and asserts the exact field set, runnable against the linked environment
- No cross-user RPC body selects user_id or email columns of the target user (mechanical grep over RPC bodies)
- Each cross-user projection's field list is documented with a per-field consumer, and diffs to projection bodies are flagged in review

### References

- [GDPR — Art. 25(2) (data protection by default: accessibility limitation)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [OWASP Top 10:2021 — A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)
- [CWE-200: Exposure of Sensitive Information to an Unauthorized Actor — CWE-200](https://cwe.mitre.org/data/definitions/200.html)
- [Supabase Docs: Row Level Security — Postgres Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)

### Typical remediation

Enumerate the field set per exposure path, delete fields without a consuming feature, replace revealing forms with the least-revealing form that serves, and extend the verify harness to assert the exact set. Mechanism defects (missing allowlist construction, unhardened definer functions, widened policies) are filed under SEC-03.

*Issue skeleton:* [`templates/prv-07.md`](../templates/prv-07.md)

---

## PRV-08 · Deletion propagation and purge completeness

**Does account and item deletion propagate to every store holding the user's data (rows, storage objects, auth identity, caches, derived data), with a runnable proof of completeness that new tables cannot silently escape?**

`deletion` · applies to: `web` `ios` `android` `supabase` · default impact **5/5** · weight **3/3**

Deletion is a feature with a completeness contract: a single server-side purge covers every user-owned table and is extended in the same change that adds one, storage objects under the user's prefix and the auth identity are removed by an idempotent orchestration that converges on re-run after partial failure, and client-side stores clear too. A runnable harness seeds every user-owned table and asserts zero rows after purge. Deliberate residue (anonymized records, aggregates) is a documented decision, and backup retention windows are stated.

*Why it matters:* Erasure under GDPR Art. 17 is one of the few privacy duties an individual can directly enforce, and both app stores now require in-app account deletion. Purge completeness decays with every new table unless a harness and a standing rule pin it.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No deletion path exists, or deletion removes the auth row while content rows and storage objects remain. |
| **1 · Ad-hoc** | Deletion covers the main tables; storage objects, derived tables, or local caches are missed; the routine is not idempotent and new tables silently escape. |
| **2 · Defined** | A purge routine covers the known tables plus storage and auth; ordering and any anonymization decisions are written down; keeping it in sync with new tables relies on convention alone. |
| **3 · Managed** | The purge is transactional and idempotent with per-table observability; in-app account deletion exists on every client; client stores clear; the table list is reviewed on every schema change. |
| **4 · Verified** | A seed-and-purge harness asserts zero rows per user-owned table and absence of storage objects against a real environment, a standing rule ties adding a user-owned table to updating harness and purge in the same change, and backup retention is documented with an expiry. |

### Audit checklist

- [ ] Find the purge implementation (rg 'purge' in migrations and the delete-account serverless function); diff its table list against every table carrying a user-id foreign key in the schema, and list omissions.
- [ ] Verify orchestration order and idempotency: database purge, then storage prefix removal, then auth identity deletion; confirm a re-run after simulated mid-sequence failure converges (read the code, its comments, and any tests).
- [ ] Run the purge harness if present (seed, purge, assert zero rows): confirm it seeds every user-owned table including the most recently added ones and asserts storage-object absence, not just row counts.
- [ ] On each client, locate the in-app account-deletion entry point required by App Store Guideline 5.1.1(v) and Play's User Data policy, and trace that local stores and caches clear after deletion completes.
- [ ] Audit derived and side-channel data: analytics aggregates, ledgers, moderation queues, counterparty references; confirm each is deleted or anonymized by documented decision, and find a stated backup retention window.

### Monitoring signals

- A seed-and-assert purge harness exists and runs against the linked environment
- A documented standing rule ties every new user-owned table to a purge entry plus harness assertion in the same change
- A mechanical comparison (SQL over information_schema) shows tables with a user-id FK equals tables covered by the purge routine

### References

- [GDPR — Art. 17 (right to erasure)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Apple App Store Review Guidelines — Guideline 5.1.1(v) (account deletion)](https://developer.apple.com/app-store/review/guidelines/)
- [Google Play Developer Content Policy — User Data policy (account and data deletion requirement)](https://play.google/developer-content-policy/)
- [CWE-459: Incomplete Cleanup — CWE-459](https://cwe.mitre.org/data/definitions/459.html)

### Typical remediation

Centralize the purge in one transactional, idempotent server-side routine with per-table counts, orchestrate storage and auth deletion around it, write the seed-and-assert harness, and adopt the standing rule that new user-owned tables land with their purge entry and harness assertion in the same change.

*Issue skeleton:* [`templates/prv-08.md`](../templates/prv-08.md)

---

## PRV-09 · Ambient on-device exposure of sensitive content

**Is emotional content shielded from people near the device: notification previews carry none of it, the app-switcher snapshot hides sensitive screens, and an optional app lock protects entry?**

`ambient-exposure` · applies to: `ios` `android` `web` · default impact **4/5** · weight **2/3**

Content revealing emotional or mental state never reaches ambient view: push and local notification payloads carry neutral copy (no entry text, emotion names, intensity values, or names of recorded people), so the lock screen reveals nothing; when the app resigns to the background, sensitive screens are shielded in the app switcher (a redaction overlay or the platform's snapshot preparation hook on iOS, FLAG_SECURE or an equivalent obscuring treatment on Android, and a recorded decision for installable web surfaces where no snapshot hook exists); and an optional biometric or passcode app lock with an auto-lock timeout is available for users who share or hand over their device. If notification content can ever include personal data, the push providers (APNs/FCM) are listed as processors under GDP-06.

*Why it matters:* A journaling app holding mental-state data is read over shoulders, left on tables, and handed to children; the lock screen and the app switcher are exposure paths that RLS and transport encryption cannot touch. MASVS treats sensitive data leaking through the platform UI as its own control class for exactly this reason.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Notification payloads embed entry text or emotion details visible on the lock screen, the app switcher shows the last-viewed entry verbatim, and no app lock exists. |
| **1 · Ad-hoc** | Notification copy happens to be neutral but nobody decided it; snapshot shielding and app lock were never considered, so one changed template leaks. |
| **2 · Defined** | A recorded rule keeps emotional content out of notification payloads and current templates comply, but the app-switcher snapshot is unshielded on at least one platform, or no app lock exists. |
| **3 · Managed** | Neutral notification copy is a stated invariant with templates reviewed against it, sensitive screens are shielded in the app switcher on iOS and Android (with a recorded decision for web), an optional biometric/passcode app lock with auto-lock is available, and a recorded manual pass covers all three on each platform. |
| **4 · Verified** | The invariants are enforced by automation: a test or CI check asserts notification payload builders emit no sensitive fields, and UI tests (or a scripted device pass) verify the backgrounded snapshot is redacted and the app lock gates entry, so a regression in any of the three fails a check. |

### Audit checklist

- [ ] Inventory notification content: grep the notification-sending paths (edge functions or server code building push payloads, plus local notification schedulers on each client) and list every field interpolated into title/body; any emotional field (entry text, emotion, intensity, person name) is a finding.
- [ ] iOS: check scene lifecycle handling (scenePhase, willResignActive) for a shielding treatment when resigning active (overlay or content redaction before the system snapshot); grep for a privacy overlay component and where it is applied.
- [ ] Android: grep for FLAG_SECURE or an equivalent obscuring treatment (setRecentsScreenshotEnabled(false), redaction overlay) on activities rendering entry content; none anywhere is a finding.
- [ ] Web (installable/PWA): confirm a deliberate, recorded decision on ambient exposure, since the platform offers no snapshot hook; the decision may be that the app lock and session length carry the burden.
- [ ] Locate the app lock: an optional biometric/passcode gate (LocalAuthentication on iOS, BiometricPrompt on Android, a re-auth gate on web) with an auto-lock timeout; absence on the mobile clients is a finding.
- [ ] If any notification can carry personal data, verify APNs/FCM appear in the processor inventory audited under GDP-06.

### Monitoring signals

- Grep of push and local notification builders shows only neutral template strings, with no interpolation of entry, emotion, intensity, or person fields.
- FLAG_SECURE (or an equivalent shielding treatment) present on Android entry-content activities, and a resign-active shielding path present on iOS.
- An app-lock setting exists on the mobile clients, wired to biometric/passcode APIs, with a recorded manual pass of lock and snapshot behavior.

### References

- [OWASP MASVS v2 — MASVS-PLATFORM-3 (sensitive data in the user interface: screenshots, notifications, keyboard cache)](https://mas.owasp.org/MASVS/)
- [Android Developers: WindowManager.LayoutParams — FLAG_SECURE (exclude window content from screenshots and non-secure displays)](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Apple Developer Documentation: UIKit — applicationDidEnterBackground(_:) (prepare the UI before the system snapshots it for the app switcher)](https://developer.apple.com/documentation/uikit)
- [Apple Developer Documentation: LocalAuthentication — Authenticating a user with Face ID or Touch ID](https://developer.apple.com/documentation/localauthentication)

### Typical remediation

Rewrite notification templates to neutral copy and ban sensitive-field interpolation in the payload builders, add a resign-active redaction overlay on iOS and FLAG_SECURE (or equivalent) on Android for entry-rendering screens, record the web decision, ship an optional biometric/passcode app lock with auto-lock, and add APNs/FCM to the processor inventory if payloads can ever carry personal data.

*Issue skeleton:* [`templates/prv-09.md`](../templates/prv-09.md)
