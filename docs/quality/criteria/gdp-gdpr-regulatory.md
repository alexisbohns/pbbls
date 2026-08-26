# GDP — GDPR & Regulatory

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Regulation mapping: lawful basis, Art. 9 special-category posture, data-subject rights, transparency, processors, transfers, retention, breach readiness, minors.

---

## GDP-01 · Consent records and lawful basis

**Is every account created through any signup path backed by a durable, version-bound record of the consent or other lawful basis it rests on, with age assurance where consent is the basis?**

`lawful-basis` · applies to: `web` `ios` `android` `supabase` · default impact **4/5** · weight **3/3**

Each processing purpose has a documented lawful basis (Art. 6), and where that basis is consent, acceptance is captured at signup on every path (email, Apple, Google, any federated flow) and persisted server-side with a timestamp and the version of the document accepted. Withdrawal is as easy as giving consent, and a policy change triggers re-acceptance. Where the service is offered directly to children, an age gate enforces the applicable Art. 8 threshold (13 to 16 depending on member state).

*Why it matters:* In a multi-client product each signup path is an independent chance to lose the consent proof; federated OAuth flows routinely skip the consent screen the email flow shows. Without a server-side, version-bound record, the controller cannot demonstrate consent (Art. 7(1)) and the accountability principle fails on day one.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No consent capture at signup and no lawful-basis documentation anywhere; auth flows create accounts with no record of terms or privacy acceptance, and no age gate exists. |
| **1 · Ad-hoc** | A consent checkbox exists in one signup path, but acceptance lives only in client state or is dropped server-side, or federated signup paths skip it entirely; no document-version binding, no age assurance. |
| **2 · Defined** | All signup paths capture acceptance and timestamps persist in the database, but records lack the accepted document version, re-consent on policy change is manual or absent, or age assurance is missing; a lawful-basis map exists but is partial. |
| **3 · Managed** | Every auth path persists timestamped, version-bound consent server-side; withdrawal and re-consent flows exist in-product; an age gate enforces the applicable threshold; a written lawful-basis map covers each processing purpose including analytics. |
| **4 · Verified** | Automated tests or a DB harness assert consent persistence for each signup path including federated ones, and a CI check or review gate fails when a new processing purpose or policy version lands without updating the consent machinery. |

### Audit checklist

- [ ] Grep all client signup flows for consent capture (e.g. grep -rn "terms_accepted\|privacy_accepted\|consent" across the web app routes and the iOS/Android auth modules) and list every auth path: email/password, Sign in with Apple, Google, magic link. Flag any path with no consent UI.
- [ ] Read the auth-provisioning trigger or RPC in the database migrations (grep migrations for handle_new_user or the equivalent signup hook) and confirm consent timestamps are persisted into a server-side table, are NULL-safe for federated signups, and are not silently discarded.
- [ ] Check version binding: grep for a policy-version constant or column next to the consent timestamps; confirm the stored record identifies which terms/privacy version was accepted and that a version bump forces re-acceptance in the client.
- [ ] Check withdrawal: trace the settings screens for a way to withdraw consent-based processing (not only full account deletion) and confirm the backend actually stops the processing it authorized.
- [ ] Check age assurance: grep the registration UI for a date-of-birth or age confirmation step, and compare against the Art. 8 age applicable in the primary member states served; cross-check store listing age ratings.

### Monitoring signals

- grep of the signup trigger/RPC migration shows both terms and privacy timestamps inserted for every path; a migration re-emitting the trigger without them is a red flag
- CI or harness test asserts non-null consent timestamps after email signup and after each OAuth provider signup
- grep client auth calls (signUp / signInWithIdToken equivalents) for invocations that omit consent metadata returns nothing unexplained

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 6, Art. 7, Art. 8](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- EDPB Guidelines 05/2020 on consent under Regulation 2016/679 — Sections 5 (conditions for valid consent) and 7 (children)

### Typical remediation

Persist consent timestamps and the accepted document version in the signup trigger for every auth path, backfill existing accounts from surviving signup metadata, add an age gate where consent is the basis, and write a one-page lawful-basis map per processing purpose.

*Issue skeleton:* [`templates/gdp-01.md`](../templates/gdp-01.md)

---

## GDP-02 · Special-category data gating and DPIA

**Is emotional, mental-state, or health-adjacent content treated as special-category data, with an explicit consent gate that names it, a DPIA on record, and exclusion from logs and secondary processing that lacks a basis?**

`special-category` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **5/5** · weight **3/3**

Fields that reveal emotional or mental state, mood, intensity, or other health-adjacent signals are inventoried and treated under Art. 9: the consent flow names the sensitive nature explicitly rather than burying it in terms, and a data protection impact assessment exists before features that enlarge this processing ship. Sensitive fields never appear in application logs, crash reports, or third-party telemetry, and any analytics over them is aggregated or thresholded against re-identification.

*Why it matters:* Systematic recording of emotional states is at minimum health-adjacent, and large-scale processing of special categories is an explicit Art. 35(3)(b) DPIA trigger. Products in this class routinely leak the sensitive fields into operator analytics and logging because the schema does not distinguish them from ordinary content.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Emotional or health-adjacent fields are treated as ordinary content: no Art. 9 analysis exists, no DPIA, and the fields flow into logs, crash reporting, and analytics unexamined. |
| **1 · Ad-hoc** | Sensitivity is acknowledged somewhere informal (a code comment, an issue) but the consent copy does not name it, and sensitive fields still reach telemetry, logs, or third-party calls. |
| **2 · Defined** | Explicit-consent wording names the sensitive data at capture or signup, and a DPIA draft or Art. 9 memo exists in the repo; known gaps remain open, such as raw sensitive fields surfacing in operator analytics dashboards. |
| **3 · Managed** | A completed, dated DPIA predates the shipped features that enlarge sensitive processing; sensitive fields are excluded from logs and third-party telemetry by reviewed convention; operator analytics use aggregation or minimum-cohort thresholds. |
| **4 · Verified** | Sensitive columns are machine-annotated (inventory file, column comments, or type wrappers), and lint/CI fails when an annotated field is added to a log statement, analytics sink, or export path without an accompanying DPIA/doc update. |

### Audit checklist

- [ ] Inventory sensitive fields: grep the migrations and the generated schema types (database.ts or equivalent) for emotion, mood, valence, intensity, positiveness, or product-specific equivalents; list every table and view that carries them, including analytics views.
- [ ] Search docs/ for a DPIA, an Art. 9 memo, or explicit-consent copy; confirm the signup or capture flow names the emotional/health-adjacent nature of the data in user-facing language, not only in the privacy policy.
- [ ] Trace secondary use: read the analytics migrations and the admin dashboard queries; flag anywhere raw per-user sensitive values (not aggregates) are exposed to operators, and check aggregate views for small-cohort re-identification.
- [ ] Grep client and edge-function code for logger, crash-reporting, and analytics calls that serialize pebble-like content objects; confirm sensitive fields are stripped before any sink.
- [ ] Check that admin/operator access to sensitive content is role-gated (grep for is_admin-style checks on every view or RPC exposing it) and that no service_role bypass reaches a client bundle.

### Monitoring signals

- A machine-readable sensitive-field inventory exists, and a grep of log/analytics sink code for those field names returns nothing
- CI or a PR checklist entry requires a DPIA/doc touch whenever a migration adds a column matching the sensitive inventory patterns
- Admin analytics views are aggregate-only: grep analytics view definitions for per-user sensitive columns returns nothing ungated

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 9, Art. 35(3)(b)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- Article 29 Working Party Guidelines on Data Protection Impact Assessment (DPIA), endorsed by the EDPB — WP248 rev.01

### Typical remediation

Write the DPIA against the current feature set, add explicit-consent copy naming the sensitive data, annotate sensitive columns in a committed inventory, and strip those fields from every log, telemetry, and operator-analytics path that lacks a documented basis.

*Issue skeleton:* [`templates/gdp-02.md`](../templates/gdp-02.md)

---

## GDP-03 · Bystander data containment

**Are records a user creates about other identifiable people minimized to what the feature needs, confined to their creator, stripped from every cross-user projection, and destroyed with the creator's account?**

`bystander-data` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Where users record identifiable third parties who never consented (named people, relationships, photos depicting them), the product captures the minimum attributes the feature needs, scopes those records to their creator by row-level security, and excludes third-party identifiers from every payload another user or the public can see. Bystander records are deleted by the creator's account purge, and no enrichment (face recognition, contact matching, profiling of the third party) occurs. The design stays on the safe side of the household-exemption boundary the controller itself does not enjoy.

*Why it matters:* Third parties recorded inside another person's private journal are data subjects with full GDPR rights but no relationship with the controller; the household exemption covers the user, not the platform providing the means (Recital 18). One leaky sharing projection turns private annotations about real people into a public disclosure the controller must answer for.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Third-party records accept unbounded free-text attributes, appear verbatim in shared or public payloads, and survive the creator's account deletion. |
| **1 · Ad-hoc** | Third-party records happen to be private because a generic per-user RLS policy covers their table, but sharing projections, exports, or media flows can leak names, and no minimization intent is visible in the schema. |
| **2 · Defined** | Captured attributes are deliberately minimal and creator-scoped by explicit policy; a known gap remains, such as shared payloads carrying third-party tags, operator surfaces showing names without need, or unproven purge coverage. |
| **3 · Managed** | Creator-only access is enforced by RLS; every cross-user or public projection strips third-party identifiers via an explicit field allowlist; the account purge removes bystander records; photos are owner-scoped in storage with no recognition or enrichment. |
| **4 · Verified** | A runnable harness seeds bystander data and asserts zero leakage through every sharing surface and zero rows after purge; projections that bypass the allowlist pattern are caught by review tooling or CI. |

### Audit checklist

- [ ] Identify third-party entities: grep migrations for person/contact/relationship-like tables (in this stack class, tables like souls); list every column and flag attributes beyond a label and relationship that the feature does not strictly need.
- [ ] Read the RLS policies on those tables in the migrations and confirm select/insert/update/delete are all scoped to the creating user; flag any policy widened for sharing.
- [ ] Read every security definer projection RPC that serves cross-user or public reads (public profile, shared/visible item feeds): confirm each builds an explicit jsonb/field allowlist and that no third-party identifier or foreign user_id appears in the output shape.
- [ ] Trace the sharing path end-to-end on each client: create a record tagged with a third party, share it at each visibility grade, and inspect the payload another account receives for third-party names or IDs.
- [ ] Confirm the account purge function covers the third-party table and its join tables, and that the purge verification harness seeds and zero-asserts them; check storage policies scope photos to the owner's prefix and grep for any face-detection or contacts-matching SDK.

### Monitoring signals

- The purge harness includes a seed and zero-row assertion for every third-party table
- grep of cross-user projection functions shows explicit field allowlists (jsonb_build_object or equivalent); any select-star projection reaching another user is a violation
- grep clients and dependencies for face-recognition or contact-import SDKs returns nothing

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 2(2)(c) and Recital 18 (household exemption boundary), Art. 5(1)(c) (data minimisation), Art. 14 (information where data are not obtained from the data subject)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)

### Typical remediation

Trim third-party schemas to the minimum attribute set, re-derive every cross-user projection from an explicit allowlist, add bystander tables to the purge and its harness, and document the household-exemption reasoning in the DPIA.

*Issue skeleton:* [`templates/gdp-03.md`](../templates/gdp-03.md)

---

## GDP-04 · Data-subject rights workflows on every client

**Can a user exercise access, rectification, portability, and erasure self-serve, with entry points on every client, an export whose breadth matches the schema, and a documented operator path for out-of-band requests?**

`dsr-rights` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **5/5** · weight **3/3**

All four core rights have working in-product workflows: personal fields are editable (Art. 16), the user can obtain a machine-readable export of everything held about them (Art. 15/20), and account deletion is initiable self-serve from every client behind a re-authentication confirmation (Art. 17). Operators have a documented path for requests arriving out-of-band within the Art. 12(3) one-month window. Whether the erasure itself propagates to every store and converges to zero is PRV-08's concern, and the store-mandated presence of in-app deletion is PLT-01's; this criterion audits that each right has a reachable, working entry point on every client and that export breadth matches what the schema actually holds.

*Why it matters:* A right that exists in law but has no in-product workflow silently becomes an operator burden and a compliance failure: users cannot find it, operators improvise it, and export breadth rots as every milestone adds tables. Keeping the entry points and the export honest per client is a distinct failure mode from purge completeness, which PRV-08 proves separately.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No in-product deletion or export entry point exists on any client; most personal fields are not editable; a rights request would require manual database work with no written procedure. |
| **1 · Ad-hoc** | Piecemeal rights exist (delete one item, edit a profile field) but account-level erasure or export has no entry point on one or more clients, and operators improvise out-of-band requests. |
| **2 · Defined** | Deletion and rectification are self-serve on the main clients, but export is absent or partial, some clients lack an entry point, or the operator path for out-of-band requests is undocumented. |
| **3 · Managed** | All four rights are self-serve on every client: deletion requires re-auth confirmation and invokes the server-side purge (whose completeness PRV-08 owns), export is machine-readable and covers all user content including media references, and a documented operator path handles out-of-band requests within the Art. 12(3) window. |
| **4 · Verified** | Export completeness is diffed against the schema so drift fails a check, each client's rights entry points are exercised by tests or a recorded pass, and the operator runbook including identity verification is versioned in the repo. |

### Audit checklist

- [ ] Grep each client's settings surface for delete-account, export, and profile-edit entry points; confirm deletion requires re-authentication and invokes the server-side purge routine (the routine's completeness is audited by PRV-08, not here).
- [ ] Check rectification: list personal fields not editable in any client; each uneditable personal field is a finding.
- [ ] Check portability: verify an export produces machine-readable output covering content, third-party tags, media references, and profile; diff the export's covered tables against the schema's user-owned tables and flag gaps.
- [ ] Verify the deletion entry point ends in a state the user can verify (signed out, account gone) and that erased accounts disappear from connected users' views and cached projections.
- [ ] Locate the operator runbook for out-of-band rights requests: identity verification, response window (Art. 12(3)), and escalation; absence is a finding.

### Monitoring signals

- grep of each client settings module finds delete-account, export, and profile-edit entry points
- Scripted diff of user-owned tables in the schema versus tables covered by the export returns empty
- A versioned operator runbook for out-of-band rights requests exists in the repo

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 15, Art. 16, Art. 17, Art. 20, Art. 12(3)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Apple App Review Guidelines — Guideline 5.1.1(v) (account deletion)](https://developer.apple.com/app-store/review/guidelines/)
- [Google Play Developer Program Policies — User Data (account and data deletion requirements)](https://play.google/developer-content-policy/)

### Typical remediation

Add the missing entry points per client (deletion behind re-auth, export, field editing), build a schema-driven export endpoint so breadth cannot silently rot, and write the operator runbook for out-of-band requests including identity verification. Purge propagation gaps discovered along the way are filed under PRV-08.

*Issue skeleton:* [`templates/gdp-04.md`](../templates/gdp-04.md)

---

## GDP-05 · Transparency and store privacy declarations

**Do the privacy notice, in-product disclosures, and app-store privacy declarations exist on every client, in every supported language, and match the product's actual data flows?**

`transparency` · applies to: `web` `ios` `android` · default impact **4/5** · weight **2/3**

A privacy notice meeting Art. 13 content requirements (purposes, bases, processors, retention, rights, contact) is reachable from every client without login, in each supported language, and is dated. In-context notices appear at sensitive capture points, and the Apple privacy manifest/App Privacy details and the Google Play Data safety form declare exactly what the code collects and shares. Disclosures are reviewed whenever the data model or dependency set changes.

*Why it matters:* Store privacy declarations are audited mechanically by both stores, and a mismatch between declared and actual collection is a removal risk as well as an Art. 13 failure. Multi-client products drift because each surface ships disclosures on its own schedule.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No privacy policy reachable from the product; store listings carry empty, placeholder, or false privacy declarations. |
| **1 · Ad-hoc** | A policy page exists but is stale, single-language for a multi-language user base, or reachable only behind login; store declarations were filled once at submission and never reconciled with the code. |
| **2 · Defined** | The policy names actual purposes, processors, and rights, is dated, and is reachable from all clients; known mismatches remain between the privacy manifest or Data safety form and real data flows, or in-context notices at sensitive capture points are missing. |
| **3 · Managed** | Policy, in-context disclosures, and both stores' declarations are mutually consistent and demonstrably reviewed on data-flow changes; in-app deletion and permission purpose strings meet store rules; all supported languages are covered. |
| **4 · Verified** | Disclosure sources (privacy manifest, Data safety answers) are version-controlled, and a CI step or PR gate forces a disclosure review whenever the schema, permission set, or third-party dependency list changes. |

### Audit checklist

- [ ] Locate the privacy policy source: check the web app's legal/document routes (e.g. a docs/[slug] route) and each mobile client's settings links; verify it loads without authentication, in every supported locale, carries a last-updated date, and names actual processors, purposes, retention, and rights.
- [ ] iOS: open PrivacyInfo.xcprivacy and the App Store privacy details; diff declared collected data types and tracking against reality (schema fields synced, photo access, identifiers, third-party SDKs found in the project file or SPM manifest); check Info.plist purpose strings for every permission.
- [ ] Android: reconcile the Play Data safety form against the manifest permissions and actual network payloads; grep the manifest for permissions with no corresponding disclosure.
- [ ] Check in-context disclosure: at signup and at sensitive capture points (photo attach, emotional input), confirm a notice or policy link is present; confirm account deletion is discoverable in-app on both mobile clients.
- [ ] Compare policy claims against code: retention statements versus actual jobs, processor list versus dependency/endpoint inventory; flag every claim the code contradicts.

### Monitoring signals

- PrivacyInfo.xcprivacy exists in the iOS project and parses; its declared types match a committed data-collection inventory
- A version-controlled source of truth for Play Data safety answers exists in the repo
- The legal document route returns HTTP 200 without auth in every supported locale

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 12, Art. 13](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Apple: App privacy details on the App Store](https://developer.apple.com/app-store/app-privacy-details/)
- [Apple: Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files)
- [Google Play: Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)

### Typical remediation

Publish a dated, bilingual Art. 13-complete notice reachable from all clients, reconcile both stores' declarations against a code-derived data inventory, and add a PR checklist item tying schema or dependency changes to a disclosure review.

*Issue skeleton:* [`templates/gdp-05.md`](../templates/gdp-05.md)

---

## GDP-06 · Processor inventory, DPAs, and transfers

**Is every processor and international transfer identifiable from the code outward, covered by a DPA, and either pinned to an adequate region or safeguarded by a documented transfer mechanism?**

`processors` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **2/3**

A written processor inventory lists every service that touches personal data (backend platform, hosting/CDN, auth federation, email, crash/analytics SDKs), each with its DPA reference, role, data categories, and region. The inventory matches what the code actually does: dependencies, outbound endpoints, and hosting configuration. Data leaving the EEA rides a documented Chapter V mechanism (adequacy, SCCs, or an adequacy-decision framework), and region pinning is configured wherever the platform offers it.

*Why it matters:* In a BaaS-plus-serverless stack the controller's security posture is mostly its processor chain, and one unreviewed SDK or edge-function fetch silently adds an unlisted processor. Post-Schrems II, undocumented US transfers of intimate data are a live enforcement target.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No processor inventory exists; SDKs and endpoints send personal data to services no document names; hosting and database regions were never deliberately chosen. |
| **1 · Ad-hoc** | The team can name the major processors informally, but nothing is written, no DPA references exist, and client or function code contains third-party endpoints no one has reviewed. |
| **2 · Defined** | A written inventory lists the major processors with DPA links and regions; gaps remain, such as an email provider or edge-function egress unlisted, or a non-EEA processor with no documented transfer mechanism. |
| **3 · Managed** | The inventory matches code-derived reality (dependency manifests, outbound hosts, hosting configs); every processor has a DPA and, where needed, a transfer mechanism; EU-region pinning is configured for the database, storage, and function runtimes where offered. |
| **4 · Verified** | An automated check derives the outbound-endpoint and dependency list from the repo and diffs it against the inventory; adding an SDK or external fetch without an inventory update fails CI. |

### Audit checklist

- [ ] Derive the processor list from code: read hosting configs (vercel.json or equivalents, the Supabase project config), dependency manifests on every surface (package.json files, the iOS project/SPM manifest, Gradle dependency blocks), and grep edge functions and server code for outbound fetch/http calls to external hosts.
- [ ] Grep all client code for hard-coded https:// endpoints that could carry personal data (grep -rEo 'https://[a-z0-9.-]+' over app sources, deduplicated) and classify each hit as first-party, processor, or unknown.
- [ ] Locate the documented inventory in docs/ (or confirm its absence); for each processor verify a DPA reference exists (e.g. the backend platform's and host's standard DPAs) and note the contracted data region.
- [ ] Check region configuration: the database project's region, function/runtime regions in hosting config, storage/CDN geography; flag any non-EEA processing of EU user data lacking a documented SCC or adequacy-framework basis.
- [ ] Verify privileged credentials are not shared beyond listed processors: review environment variable usage and CI secrets for keys handed to unlisted services.

### Monitoring signals

- A scripted diff between repo-derived outbound hosts/dependencies and the documented inventory returns empty
- Hosting config pins regions explicitly (a regions key present rather than platform default)
- A dependency-review CI job flags new third-party SDKs for inventory classification

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 28, Art. 30, Chapter V (Art. 44-49)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- EDPB Recommendations 01/2020 on measures that supplement transfer tools to ensure compliance with the EU level of protection of personal data
- [Vercel Data Processing Agreement](https://vercel.com/legal/dpa)

### Typical remediation

Write the inventory from a code-derived endpoint and dependency sweep, attach DPA references and regions per processor, pin EU regions where the platforms offer them, and document the transfer mechanism for each remaining non-EEA hop.

*Issue skeleton:* [`templates/gdp-06.md`](../templates/gdp-06.md)

---

## GDP-07 · Enforced retention schedules

**Does every category of personal data have a stated lifetime that something actually enforces, on the server and in client-side stores?**

`retention` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **2/3**

Each data category (content, drafts, analytics events, operational logs, media, backups, client caches) has a documented retention period tied to its purpose, and enforcement exists as running code: scheduled cleanup jobs server-side, cache and local-store clearing on logout and account deletion client-side. Backup retention windows are known and consistent with what the privacy notice and erasure story promise. Orphaned data (media for deleted content, superseded drafts) is reaped rather than accumulating.

*Why it matters:* Append-heavy schemas (analytics events, audit rows, drafts) grow forever by default, and offline-capable clients keep local copies after logout unless someone clears them. Storage limitation (Art. 5(1)(e)) fails quietly: nothing breaks, the data just never leaves.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Nothing is deleted by design: logs, analytics events, drafts, and orphaned media accumulate indefinitely and no document states any lifetime for anything. |
| **1 · Ad-hoc** | Incidental cleanup exists (a one-off truncation, platform-default log expiry) but no schedule is written down, and client-side stores persist personal data after logout. |
| **2 · Defined** | A retention schedule document exists with per-category lifetimes; enforcement is partial or manual, and backup retention is acknowledged but not reconciled with the erasure story. |
| **3 · Managed** | Scheduled jobs enforce the stated lifetimes for server-side categories; logout and account deletion clear client caches, local databases, and keychain/credential stores; orphaned media is reaped; backup windows are documented and consistent with the notice. |
| **4 · Verified** | Retention jobs are monitored (a failed or skipped run alerts), and a query pack or harness asserts no category exceeds its stated lifetime; drift between the schedule document and the jobs fails a periodic check. |

### Audit checklist

- [ ] Enumerate time-accumulating tables in the migrations (analytics/event tables, logs, karma or audit event streams, drafts, bounces) and check each for a TTL mechanism: pg_cron jobs, scheduled edge functions, or scheduled CI workflows that prune; list categories with none.
- [ ] Search docs/ for a retention schedule and compare each stated lifetime against the actual job or its absence.
- [ ] Trace logout and account-deletion on every client: grep the signOut/delete handlers for clearing of PWA caches and IndexedDB/localStorage on web, and local databases, files, and keychain/keystore entries on the mobile clients.
- [ ] Check orphan reaping: verify media objects for deleted or edited content are removed (storage prefix cleanup on delete paths) and that superseded drafts expire.
- [ ] Check backups: find the platform's backup retention setting or documentation, confirm the privacy notice's retention claims account for it, and confirm the erasure story acknowledges the backup tail.

### Monitoring signals

- Every accumulating table maps to a greppable scheduled cleanup (pg_cron entry, scheduled function, or CI cron)
- grep of logout handlers on each client shows explicit local-store clearing calls
- A scripted comparison of documented lifetimes versus oldest-row ages per category returns no violations

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 5(1)(e) (storage limitation)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- CNIL practical guide on retention periods (Les durees de conservation)
- [OWASP Application Security Verification Standard — V8 (Data Protection)](https://owasp.org/www-project-application-security-verification-standard/)

### Typical remediation

Write the per-category schedule, implement pruning jobs for each accumulating table, clear client-side stores on logout and deletion, and wire a periodic oldest-row check so silent accumulation becomes a failing signal.

*Issue skeleton:* [`templates/gdp-07.md`](../templates/gdp-07.md)

---

## GDP-08 · Breach detection and response readiness

**Could the operator detect, scope, and notify a personal-data breach within regulatory timelines using logging, tooling, and a documented runbook that exist today?**

`breach` · applies to: `admin` `supabase` · default impact **4/5** · weight **2/3**

A written incident runbook covers detection, severity assessment, the Art. 33 72-hour notification flow to the lead supervisory authority, Art. 34 user communication, and the processor notification chain. Detection is real: privileged operations (admin RPCs, service-role usage, moderation actions) leave timestamped audit trails, privileged keys never reach client code, and prewritten queries can enumerate affected users and records for a given table and time window. Readiness is exercised, not just written.

*Why it matters:* The 72-hour clock starts at awareness, and a product holding intimate content cannot scope a breach without audit trails laid down in advance. For a small operator the runbook plus processor notification chain is most of the response capacity.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No incident runbook, no audit trail of operator or privileged access, secrets handled ad hoc; a breach would be neither detectable nor reportable within the deadline. |
| **1 · Ad-hoc** | Platform-default logs exist and response would be improvised; no documented roles, timelines, notification templates, or processor chain. |
| **2 · Defined** | A written runbook covers detection, assessment, 72-hour notification, and user communication; audit logging exists for some privileged actions but scoping queries would be written under fire. |
| **3 · Managed** | Runbook plus working detection: admin mutations and privileged RPCs are audit-logged with timestamps and actor identity, service-role credentials are confined to server contexts, and affected-user scoping queries are prewritten and tested against the schema. |
| **4 · Verified** | Alerts fire on anomalous privileged access or auth events; a breach exercise (tabletop or scripted drill) is documented with date and outcome; CI or lint prevents privileged keys from entering client bundles. |

### Audit checklist

- [ ] Search docs/ for an incident-response runbook; verify it names roles, the 72-hour Art. 33 flow to the controller's lead supervisory authority, Art. 34 user-communication criteria and channel, notification templates, and the processor breach-notification chain (backend platform, host, stores).
- [ ] Check detection surface: grep migrations for audit or action-log tables and confirm admin/moderation RPCs write to them with actor and timestamp; check what the backend platform's auth and API logs retain and for how long.
- [ ] Grep every client workspace for privileged credentials (grep -rn "service_role" and any admin API keys across app sources and bundles); confirm they appear only in server-side contexts, edge functions, and CI secrets.
- [ ] Verify scoping ability: confirm prewritten queries (or a documented procedure) can list affected users and records per table and time window from the audit trails; spot-check one against the schema.
- [ ] Check the Art. 34 channel: confirm a working mechanism to reach all affected users exists (transactional email capability or an in-product banner mechanism) and is named in the runbook; look for any documented drill or review date.

### Monitoring signals

- grep -rn 'service_role' across client app sources returns zero hits
- Admin mutation RPCs greppably insert into an audit/log table with actor and timestamp
- An incident runbook file exists in docs/ with a last-reviewed date under 12 months old

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 33, Art. 34](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- Article 29 Working Party Guidelines on Personal data breach notification under Regulation 2016/679, endorsed by the EDPB — WP250 rev.01
- NIST SP 800-61, Computer Security Incident Handling Guide

### Typical remediation

Write the runbook with templates and the processor chain, add an audit-log table that every admin and privileged RPC writes to, prewrite the scoping queries, and run one tabletop drill to date-stamp the readiness claim.

*Issue skeleton:* [`templates/gdp-08.md`](../templates/gdp-08.md)
