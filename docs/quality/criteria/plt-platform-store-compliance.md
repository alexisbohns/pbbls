# PLT — Platform & Store Compliance

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

App Store and Play policy compliance, PWA standards, deployment platform posture, Supabase platform configuration.

---

## PLT-01 · In-app account deletion entry points, store-compliant

**Can a user find and initiate account deletion from inside every store-distributed client, and does a web-reachable deletion resource exist and match what is declared in the Play Console?**

`store-accounts` · applies to: `ios` `android` `web` · default impact **5/5** · weight **3/3**

Each app distributed through the App Store or Play offers an in-app, discoverable path to delete the account, not merely sign out or deactivate, and the path actually invokes the server-side deletion routine. Google Play additionally requires a web-reachable deletion resource declared in the Data safety section, so a signed-in web deletion page exists and its URL matches the declaration. Whether the server-side routine removes every store of data is PRV-08's concern; this criterion audits the store-facing presence, discoverability, and declaration consistency of the deletion entry points.

*Why it matters:* Apple 5.1.1(v) and Play's account deletion requirement are hard store gates: apps get rejected or removed without a discoverable in-app path and, on Play, the declared web resource. Presence of the entry points is a distinct failure mode from purge completeness, which PRV-08 proves separately.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No in-app deletion path exists on any store-distributed client; deletion requires emailing support. |
| **1 · Ad-hoc** | A delete control exists on one client only, or it merely signs out or soft-disables the account instead of invoking the server-side deletion routine. |
| **2 · Defined** | In-app deletion exists on both store apps and calls the server-side routine, but it is buried where review or users cannot find it, or no web deletion resource exists for the Play declaration. |
| **3 · Managed** | Deletion is discoverable in-app on iOS and Android and on the web, each path invokes the server-side routine (whose completeness PRV-08 audits), and the web URL matches the Play Console Data safety declaration. |
| **4 · Verified** | The declared Play deletion URL is tracked in repo config or docs and probed automatically, and each client's deletion path is exercised by a test or recorded pass so a regression in discoverability or wiring is caught. |

### Audit checklist

- [ ] Trace the deletion UI on each store client (iOS profile/settings feature, the Android mirror) and confirm the tap path invokes the server-side deletion routine and ends the session, not just a signOut() call; completeness of the routine itself is PRV-08's audit.
- [ ] Assess discoverability: the path starts from an obvious place (settings or account screen), is labeled as account deletion, and does not require contacting support.
- [ ] Verify a web page exists where a signed-in user can delete their account, and that its URL is the one declared in the Play Console Data safety deletion field (look for it in docs or store metadata in the repo).
- [ ] Confirm the confirmation flow re-authenticates before deleting and states what will be removed.

### Monitoring signals

- HTTP probe: the declared Play data-deletion URL returns 200 and renders a deletion flow.
- Each store client's settings module contains a delete-account entry point wired to the server-side deletion routine (grep the settings feature for the deletion call).
- The Play deletion URL is tracked in repo config or docs so declaration drift is visible in review.

### References

- [Apple App Store Review Guidelines — 5.1.1(v) Account Sign-In / Account Deletion](https://developer.apple.com/app-store/review/guidelines/)
- [Apple: Offering account deletion in your app](https://developer.apple.com/support/offering-account-deletion-in-your-app/)
- [Google Play: app account deletion requirement](https://support.google.com/googleplay/android-developer/answer/13327111)
- [Regulation (EU) 2016/679 (GDPR) — Art. 17 Right to erasure](https://eur-lex.europa.eu/eli/reg/2016/679/oj)

### Typical remediation

Add a discoverable in-app deletion flow to each store client behind a re-auth confirmation, stand up the web deletion route and declare its URL in the Play Console, track that URL in the repo, and wire every entry point to the single server-side deletion routine whose completeness PRV-08 audits.

*Issue skeleton:* [`templates/plt-01.md`](../templates/plt-01.md)

---

## PLT-02 · Truthful privacy declarations and tracking consent

**Do the App Store privacy nutrition labels, the iOS privacy manifest, and the Play Data safety form each match the data the code actually collects and the SDKs it actually ships, and is cross-app tracking either provably absent or gated behind ATT?**

`store-privacy` · applies to: `ios` `android` · default impact **4/5** · weight **3/3**

A repo-tracked inventory maps every collected data type, permission, and third-party SDK to the answers declared in the App Store privacy labels and the Play Data safety form. The iOS bundle ships a PrivacyInfo.xcprivacy declaring collected data types and required-reason API usage. Tracking status is explicit: either no cross-app tracking exists (and an SDK audit shows it), or the AppTrackingTransparency prompt gates every tracking pathway. Declarations are updated in the same change that adds a data type or SDK.

*Why it matters:* Mismatched labels are a rejection and trust hazard, and for emotional/relationship data an under-declaration is a deceptive practice with regulatory teeth. Console-only answers with no repo record drift silently as dependencies change.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No privacy manifest ships, and no record of the store privacy answers exists anywhere in the repo; nobody can say what was declared. |
| **1 · Ad-hoc** | Store forms were filled in the consoles once; the repo documents nothing; the dependency list has changed since submission with no review. |
| **2 · Defined** | A privacy manifest ships and the declared answers are recorded in the repo, but no process ties new SDKs, permissions, or data types to updating them; tracking status is assumed rather than audited. |
| **3 · Managed** | A repo-tracked inventory maps each data type, permission, and SDK to the label and Data safety answers; adding a dependency or permission triggers a documented review step; tracking status (none, or ATT-gated) is stated and matches a code audit. |
| **4 · Verified** | An automated check diffs the dependency manifests (SPM/Gradle) and Android permissions against the declared inventory and fails on drift; the privacy manifest is validated at build time (missing required-reason declarations fail CI). |

### Audit checklist

- [ ] iOS: `find apps/ios -name "PrivacyInfo.xcprivacy"`; if absent, flag immediately; if present, verify NSPrivacyCollectedDataTypes against actual collection and NSPrivacyAccessedAPITypes against actual API use (grep for UserDefaults, file timestamp, system boot time, disk space APIs).
- [ ] Grep both mobile codebases and their dependency manifests (project.yml / Package.resolved, gradle/libs.versions.toml) for analytics or ad SDK identifiers (Firebase, Amplitude, Mixpanel, AppsFlyer, Adjust, AdMob) and compare the hit list against the declared inventory.
- [ ] iOS: grep for `ATTrackingManager` and `NSUserTrackingUsageDescription`; a tracking SDK without ATT gating is a violation, and ATT strings without any tracking indicate an over-declaration to correct.
- [ ] Android: read AndroidManifest.xml permissions and the Gradle dependency set; confirm each permission and SDK maps to a Data safety answer, including third-party data sharing.
- [ ] Locate the repo-tracked record of the current label and Data safety answers (docs/ or store metadata directory); if the only copy lives in App Store Connect / Play Console, flag the drift risk.

### Monitoring signals

- CI job diffing dependency manifests against a committed data/SDK inventory file, failing on unlisted additions.
- Presence check in CI: PrivacyInfo.xcprivacy exists in the shipping app target.
- Grep for known tracking SDK package prefixes returns nothing outside a reviewed allowlist.

### References

- [Apple: App privacy details on the App Store](https://developer.apple.com/app-store/app-privacy-details/)
- [Apple: Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files)
- [Apple App Store Review Guidelines — 5.1.2 Data Use and Sharing](https://developer.apple.com/app-store/review/guidelines/)
- [Apple: AppTrackingTransparency framework](https://developer.apple.com/documentation/apptrackingtransparency)
- [Google Play: Provide information for the Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469)
- [OWASP MASVS — MASVS-PRIVACY](https://mas.owasp.org/MASVS/)

### Typical remediation

Create a committed privacy inventory (data types, permissions, SDKs, store answers), add the iOS privacy manifest with required-reason declarations, and wire a dependency-diff check; re-submit corrected labels and Data safety answers if they diverge from reality.

*Issue skeleton:* [`templates/plt-02.md`](../templates/plt-02.md)

---

## PLT-03 · Sign-in options meet platform equity rules

**If any client offers a third-party or social login, is a privacy-preserving option (per Apple 4.8) offered with equal prominence, implemented with correct nonce/PKCE handling, and is the identity-provider set consistent across all client surfaces?**

`store-accounts` · applies to: `ios` `android` `web` `supabase` · default impact **3/5** · weight **2/3**

Where third-party or social login exists, the store apps also offer a login service meeting Apple 4.8's privacy bar (data limited to name and email, email hiding allowed, no advertising-driven data collection), Sign in with Apple being the canonical qualifier. The OAuth flows use nonces or PKCE correctly against the shared auth backend. Every client surface accepts the same provider set so one account works everywhere, and the server-side provider and redirect-URL configuration is tracked in reviewable form.

*Why it matters:* Apple 4.8 non-compliance blocks releases outright when social login ships. On a shared-database product, provider asymmetry strands accounts on one surface, and untracked auth server config is where redirect-URL takeover bugs hide.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Only a third-party social login exists on the store apps with no privacy-preserving alternative. |
| **1 · Ad-hoc** | A qualifying option exists on one surface only, or provider sets differ across surfaces so an account created on one client cannot sign in on another. |
| **2 · Defined** | Store apps deliberately offer a qualifying option, but nonce/PKCE handling and cross-surface provider parity are unaudited, and the redirect allowlist lives only in a dashboard. |
| **3 · Managed** | Every surface offers the same provider set including a qualifying option with equal prominence; OAuth nonce/PKCE handling is correct and documented; the provider list and redirect allowlist are recorded next to the auth code or in tracked config. |
| **4 · Verified** | Nonce/PKCE helpers are unit-tested in CI, provider parity across surfaces is asserted by a check or a documented audit that runs each release, and auth server config drift against the tracked record is detected. |

### Audit checklist

- [ ] Inventory login methods per surface: grep each client for provider identifiers (`grep -rin "signInWith\|OAuth\|apple\|google" apps/ios/Pebbles apps/android apps/web/components/auth`) and build a surface-by-provider matrix.
- [ ] If any third-party login exists on iOS, confirm a 4.8-qualifying option is present with equal prominence in the same auth screen (read the auth feature views, not just the service layer).
- [ ] Read the Apple/Google sign-in implementation for nonce handling (grep for `nonce`, `ASAuthorizationAppleIDProvider`, SHA-256 hashing of the raw nonce) and trace what the shared auth backend receives.
- [ ] Check the auth backend config for the enabled provider list and redirect URL allowlist: local `config.toml` [auth] sections, plus any committed snapshot or doc of the production dashboard settings; flag if production auth config has no repo-tracked record.
- [ ] Confirm the web and Android clients accept the same providers (including Apple where feasible) so identities are portable across surfaces.

### Monitoring signals

- Unit tests covering OAuth nonce/PKCE helpers pass in CI.
- Grep-built provider matrix is identical across client surfaces.
- Redirect allowlist and provider set exist as tracked config or a versioned doc, not console-only state.

### References

- [Apple App Store Review Guidelines — 4.8 Login Services](https://developer.apple.com/app-store/review/guidelines/)
- [Sign in with Apple (Apple Developer)](https://developer.apple.com/sign-in-with-apple/)
- [Supabase Auth: Login with Apple](https://supabase.com/docs/guides/auth/social-login/auth-apple)

### Typical remediation

Add the qualifying login option to the store apps with equal prominence, align the provider set on the remaining surfaces, fix nonce/PKCE handling where absent, and commit a snapshot of the auth server provider and redirect configuration.

*Issue skeleton:* [`templates/plt-03.md`](../templates/plt-03.md)

---

## PLT-04 · UGC safety apparatus: filter, report, block, respond

**Where user content can reach other users, is each store-required UGC apparatus element present on every surface: a filtering or review method, an in-product reporting mechanism, a server-backed block control, and reachable developer contact information?**

`store-ugc` · applies to: `ios` `android` `web` `admin` `supabase` · default impact **5/5** · weight **3/3**

Any product where one user's content is visible to another ships the four pillars Apple 1.2 and Play's UGC policy require, each present and reachable: a method to filter or proactively review objectionable content, an in-product reporting mechanism (not a mailto link) wired to an operator queue, a user-facing block control backed by a server-side primitive, and reachable developer contact information in-app and in the store listings. This criterion audits presence of each element, which is what store review checks; enforcement depth is audited separately: the moderation state machine and takedown under SAF-03, and block-severing completeness under SAF-04. A presence defect scores only here; a depth defect scores only there.

*Why it matters:* UGC violations are a leading cause of store removal, and store review checks for the visible presence of the apparatus. A missing report button or absent contact info is an app-removal risk on its own, before any question of how deep the enforcement goes; splitting presence from depth keeps one defect from scoring in two domains.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Shared or public content exists with no report or block capability anywhere in the product. |
| **1 · Ad-hoc** | A report email is buried in terms or a website; there is no in-product mechanism, no block control, or no reachable contact info. |
| **2 · Defined** | In-product report and block controls exist on the store apps, but at least one pillar is missing on some surface (no filtering/review method, no contact info in-app, or a client without the controls). |
| **3 · Managed** | All four pillars are present and reachable on every surface where cross-user content appears, each report control feeds the operator queue, and contact info is in-app and in both store listings. |
| **4 · Verified** | Presence is checked mechanically: a UI-level test or recorded checklist per surface confirms each pillar is reachable from the screens where cross-user content renders, and store metadata is tracked in the repo so listing drift is visible. |

### Audit checklist

- [ ] Map every path where one user's content reaches another: grep migrations for visibility grades, public profile RPCs, connection tables, and shared-read RLS policies; list the screens that render cross-user content.
- [ ] Grep the client surfaces for report and block UI (`grep -rin "report\|block" apps/ios/Pebbles/Features apps/android/app/src apps/web/components`) and confirm each screen rendering cross-user content offers both; a mailto: link is not a mechanism.
- [ ] Confirm each report control fires a server call into a moderation queue and each block control fires the server-side block primitive; how deeply those primitives enforce is audited under SAF-03 (moderation and takedown) and SAF-04 (block severing).
- [ ] Confirm a filtering or proactive review method exists for objectionable content (automated filter, pre-publication review, or an equivalent documented method).
- [ ] Confirm user-reachable developer contact information exists in-app (settings or about screen) and in both store listings.

### Monitoring signals

- Every screen rendering cross-user content offers report and block affordances (UI inventory or grep-based diff).
- Grep confirms every report control inserts into a moderation table rather than composing an email.
- Contact information is present in-app and tracked with the store listing metadata in the repo.

### References

- [Apple App Store Review Guidelines — 1.2 User-Generated Content](https://developer.apple.com/app-store/review/guidelines/)
- Google Play Developer Program Policies — User Generated Content policy

### Typical remediation

Add the missing pillar(s): wire in-product report flows on every client into the moderation queue, surface a block control backed by the server-side primitive, stand up a filtering or review method, and publish contact info in-app and in the listings. Enforcement-depth gaps are filed under SAF-03 and SAF-04.

*Issue skeleton:* [`templates/plt-04.md`](../templates/plt-04.md)

---

## PLT-05 · Store technical currency (target API and toolchain floors)

**Are the Android target API level and the iOS build toolchain within the stores' current requirement windows, and does a process exist that catches the annual deadline before uploads are blocked?**

`store-currency` · applies to: `ios` `android` · default impact **3/5** · weight **1/3**

The Android targetSdk sits within Google Play's current target API requirement window, and the iOS app builds with an Xcode/SDK version the App Store still accepts for submissions. Versions are centralized (Gradle version catalog, project config, CI workflow pins) rather than scattered, and a recurring mechanism (release checklist, scheduled check, or dependency bot) surfaces the next deadline before it bites.

*Why it matters:* Both stores enforce rolling minimums: Play hides outdated apps from new users and blocks updates, Apple rejects builds from old SDKs. This fails silently until the day an urgent fix cannot ship.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | targetSdk or the build toolchain is pinned behind the current store requirement; uploads are already blocked or imminently will be. |
| **1 · Ad-hoc** | Levels comply today only because the project is young; versions are scattered across files and nobody owns tracking the deadlines. |
| **2 · Defined** | Versions are current and centralized (version catalog, project config, pinned CI toolchain) with the policy noted, but updates are reactive. |
| **3 · Managed** | A recurring process (release checklist item or scheduled job) compares targetSdk and the CI toolchain against the store requirement windows ahead of each release. |
| **4 · Verified** | CI fails or alerts when targetSdk or the toolchain falls inside the warning window, and dependency/toolchain bumps arrive as automated PRs. |

### Audit checklist

- [ ] Read the Android app Gradle config (e.g. `apps/android/app/build.gradle.kts`) for compileSdk and targetSdk; compare against the Play requirement in force for the current year.
- [ ] Read the iOS project config (`project.yml` or the pbxproj) for the deployment target, and the CI workflow/ci_scripts for the pinned Xcode version; compare against Apple's current submission minimum.
- [ ] Check whether versions are centralized (Gradle version catalog, single project.yml) and grep CI workflows for hardcoded SDK/toolchain versions with no bump history.
- [ ] Look for a release checklist, scheduled workflow, or dependency bot config (renovate.json, dependabot.yml) that would surface the next deadline.

### Monitoring signals

- A CI step compares targetSdk to a policy constant and warns/fails inside the deadline window.
- Dependabot/Renovate covers the Gradle version catalog and CI toolchain pins.
- Release workflow logs show a currently-accepted Xcode/SDK version.

### References

- [Google Play: Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Android Developers: Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)

### Typical remediation

Bump targetSdk and the CI toolchain to current, centralize version pins, and add a release-checklist item or scheduled check tied to the stores' published requirement calendars.

*Issue skeleton:* [`templates/plt-05.md`](../templates/plt-05.md)

---

## PLT-06 · PWA installability and service worker lifecycle discipline

**Does the web app meet installability requirements (complete manifest, secure context, registered service worker), and is the service worker's caching and update behavior a deliberate design that never mishandles authenticated data?**

`pwa-standards` · applies to: `web` · default impact **3/5** · weight **2/3**

The web manifest satisfies install criteria (name, icons at 192 and 512 including a maskable purpose, start_url, display mode) and matches the deployed scope. The service worker is generated by the build (never hand-edited), its runtime caching rules are explicit per route class, authenticated API and private storage responses are excluded from persistent caches or handled network-first by decision, an update strategy exists (prompt or auto-activate, chosen on purpose), and user-scoped caches are cleared on sign-out. Platform-specific install affordances (apple-touch-icon, splash screens) are handled where iOS home-screen install is targeted.

*Why it matters:* For a PWA-first product the manifest and service worker are its store listing and its runtime. A copied-in service worker that caches private API responses can leak one user's data to the next session on a shared device, and a broken update path strands users on stale code indefinitely.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No manifest or no service worker; the PWA claim is just a website. |
| **1 · Ad-hoc** | A manifest exists with gaps (missing maskable icon, wrong start_url, absent 512 icon); the service worker is template defaults with unknown caching of authenticated responses and no update handling. |
| **2 · Defined** | The manifest satisfies installability and the service worker is build-integrated, but caching rules are framework defaults with no recorded decision about authenticated data, offline behavior, or updates. |
| **3 · Managed** | Caching strategy is deliberate per route class with authenticated API/storage responses explicitly excluded or network-first, an update flow is chosen and implemented, sign-out clears user-scoped caches, an offline fallback exists, and iOS install assets are present where targeted. |
| **4 · Verified** | Installability is asserted in CI (Lighthouse or equivalent with thresholds), the generated worker file is protected from manual edits by a check, and the update path and auth-cache exclusion are covered by tests. |

### Audit checklist

- [ ] Open the manifest (e.g. `public/manifest.webmanifest` or an app-router manifest.ts): verify name, short_name, start_url, display, theme/background colors, and icons at 192 and 512 including one with `"purpose": "maskable"`; check scope/id against the deployed origin.
- [ ] Locate the service worker source (e.g. `app/sw.ts` with Serwist/Workbox wiring in next.config) and read every runtime caching rule; flag any rule whose URL pattern matches the backend REST/storage endpoints (grep the sw source for the API host or generic catch-alls) without an explicit network-first or no-store decision.
- [ ] Check registration and update handling (registration component): is there a prompt, skipWaiting/clients.claim decision, or documented auto-update choice; grep the sign-out path for cache clearing (`caches.delete`, provider reset).
- [ ] Confirm the generated worker (public/sw.js) is build output: gitignored or covered by a do-not-edit check; verify iOS install assets (apple-touch-icon links, splash screens) if home-screen install is targeted.
- [ ] Run Lighthouse (or the equivalent) against a production build and record installability and PWA audit results.

### Monitoring signals

- Lighthouse/installability check runs in CI with a threshold.
- Grep of the service worker source for backend URL patterns inside cache-first rules returns nothing.
- A CI diff check fails if the generated sw.js is edited by hand.

### References

- [W3C Web Application Manifest](https://www.w3.org/TR/appmanifest/)
- [web.dev: Installability criteria](https://web.dev/articles/install-criteria)
- [MDN: Service Worker API](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API)

### Typical remediation

Complete the manifest to install criteria, rewrite runtime caching as explicit per-route-class rules excluding authenticated responses, implement a chosen update flow plus sign-out cache clearing, and add a Lighthouse gate.

*Issue skeleton:* [`templates/plt-06.md`](../templates/plt-06.md)

---

## PLT-07 · Hosting platform hardening: headers and deployment protection

**Do the deployed web properties ship a deliberate security-header set via tracked config, and are operator-facing apps and preview deployments protected at the platform level beyond application code alone?**

`deploy-platform` · applies to: `web` `admin` · default impact **4/5** · weight **2/3**

Every deployed web property sets an intentional header baseline (HSTS, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, frame-ancestors or X-Frame-Options, and a CSP enforced or consciously excepted) via tracked config (framework headers() or platform config file), not console clicks. Operator-facing apps and preview deployments carry platform-level protection (password, SSO, or IP allowlist) in addition to application auth, and previews do not run against production data without a recorded reason. Environment-variable scoping and secret exposure are SEC-04's concern.

*Why it matters:* PaaS defaults are permissive: no CSP, publicly reachable previews, and a back-office one guessed URL away. On products handling sensitive data, an unprotected preview of the operator app is a full-severity exposure regardless of how careful the application code is.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No headers configured anywhere (empty platform config, no headers() in the framework config); the operator app and previews are publicly reachable with application auth as the only gate, if that. |
| **1 · Ad-hoc** | One or two headers were added ad hoc on one app; the sibling app has none; preview URLs are unprotected. |
| **2 · Defined** | A deliberate header baseline exists on both apps in tracked config, but CSP is absent or report-only without follow-up, and the operator app relies on application auth alone. |
| **3 · Managed** | CSP is enforced (or the exception documented with a reason), the baseline is consistent across apps, and previews and the operator app carry platform-level protection in depth. |
| **4 · Verified** | Header presence is asserted by an automated probe or integration test against the deployed hosts, and platform config lives in versioned files rather than console-only state. |

### Audit checklist

- [ ] Read each app's framework config (`next.config.ts` headers()) and platform config (`vercel.json`); list which of HSTS, CSP, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, frame-ancestors are set per app; an empty vercel.json plus no headers() scores 0 here.
- [ ] Determine operator-app protection layers: an auth gate in middleware or layout, plus evidence of platform deployment protection for production and previews (documented in the repo or runbook); flag if application auth is the only layer.
- [ ] If the hosts are reachable, curl each production URL and record the actual response headers against the intended baseline.
- [ ] Check preview deployment posture: are preview URLs indexed/protected, and do they run against production data or a safe environment.
- [ ] Env-var scoping and client-bundle secret exposure are audited under SEC-04; cross-file rather than double-score any leak noticed while reading platform config.

### Monitoring signals

- Scheduled or CI probe asserting the header baseline on deployed hosts.
- Non-empty, versioned header config exists for every deployed app.
- Platform deployment protection is enabled for the operator app and previews, recorded in repo docs or config.

### References

- [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/)
- [OWASP Application Security Verification Standard — V14 Configuration](https://owasp.org/www-project-application-security-verification-standard/)
- [MDN: Content Security Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP)

### Typical remediation

Define one header baseline in versioned config and apply it to every deployed app, add CSP (report-only first, then enforce), and enable platform deployment protection for the operator app and all previews. Secret-scoping defects are filed under SEC-04.

*Issue skeleton:* [`templates/plt-07.md`](../templates/plt-07.md)

---

## PLT-08 · Managed database platform configuration

**Are the platform's auth, storage, pooling, and API settings deliberate, recorded in tracked config rather than console-only state, protected against drift, and is key issuance and rotation tracked?**

`db-platform` · applies to: `supabase` · default impact **5/5** · weight **3/3**

The managed database platform's configuration is deliberate and reviewable: auth server settings (redirect allowlist, OTP and magic-link expiry, email confirmation) are set consciously and recorded in tracked config or a committed snapshot rather than console-only state; storage buckets are configured intentionally in migrations (visibility, size and MIME limits); API limits (max_rows, exposed schemas) are explicit; connection pooling mode matches how serverless and mobile clients connect; and key issuance and rotation are tracked so a rotation can be executed and verified. RLS coverage is SEC-02's concern, security-definer hardening SEC-03's, and privileged-key exposure SEC-04's; this criterion owns the platform configuration surface nothing else covers.

*Why it matters:* Console-only configuration cannot be reviewed, diffed, or restored, and permissive platform defaults (long OTP expiry, open redirect allowlists, mismatched pooling) undermine the guarantees the schema-level controls provide. An open redirect allowlist or a key that cannot be rotated is an account-takeover and recovery liability even when every table's RLS is perfect.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Auth, storage, and API settings sit on unreviewed platform defaults with no record anywhere in the repo; nobody can state the production redirect allowlist or OTP expiry without opening the console. |
| **1 · Ad-hoc** | A few settings were changed in the console when something broke, but nothing is recorded in tracked config, and pooling mode was never matched to the access patterns. |
| **2 · Defined** | Local config (config.toml) is deliberate for the auth and API sections and buckets are configured in migrations, but production dashboard settings have no committed snapshot and key rotation is untracked. |
| **3 · Managed** | Auth, storage, pooling, and API settings are deliberate and recorded in tracked config or a committed production snapshot, pooling mode matches the client access patterns, and key issuance and rotation are documented with a runbook. |
| **4 · Verified** | Drift between dashboard config and the tracked record is detected automatically (scheduled diff or IaC), and a key rotation has been exercised or is verified executable from the runbook. |

### Audit checklist

- [ ] Review auth config: the local config.toml [auth] section (redirect allowlist, OTP/link expiry, email confirmation) plus whether a committed snapshot of the production dashboard auth settings exists; console-only prod config is a finding.
- [ ] Read storage bucket migrations for deliberate configuration: visibility flags, size limits, and allowed MIME types set intentionally; access-policy correctness on the buckets is audited under SEC-02.
- [ ] Check API limits: max_rows and exposed schemas set deliberately in config, not left on defaults.
- [ ] Confirm pooling mode (transaction vs session) matches how serverless and mobile clients connect, and that the choice is recorded.
- [ ] Check key management: which API keys exist, where their issuance is documented, and whether a rotation runbook exists; exposure of privileged keys is audited under SEC-04, RLS coverage under SEC-02, and definer hardening under SEC-03.

### Monitoring signals

- A committed snapshot or IaC file for production auth/storage/API settings exists and a drift check compares it.
- config.toml [auth] and [api] sections show deliberate values with comments, not defaults.
- A key rotation runbook exists and names every issued key.

### References

- [Supabase: Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Supabase: Production checklist](https://supabase.com/docs/guides/platform/going-into-prod)
- [Supabase: Understanding API keys](https://supabase.com/docs/guides/api/api-keys)
- [OWASP Application Security Verification Standard — V4 Access Control](https://owasp.org/www-project-application-security-verification-standard/)

### Typical remediation

Commit a reviewed snapshot (or IaC definition) of production auth, storage, and API settings, set redirect allowlists and OTP expiry deliberately, match pooling mode to the access patterns, write the key rotation runbook, and add a scheduled drift check between the dashboard and the tracked record. Mechanism defects found along the way are filed under SEC-02, SEC-03, or SEC-04.

*Issue skeleton:* [`templates/plt-08.md`](../templates/plt-08.md)
