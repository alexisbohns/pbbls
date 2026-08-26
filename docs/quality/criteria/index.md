# Criteria catalog — Kritik v0.1.0

> Generated from [`library/framework.json`](../library/framework.json) — do not edit by hand.

88 active criteria across 11 domains. Columns are the five Pebbles surfaces; ● = applicable.

| Id | Criterion | Sub | web | ios | android | admin | supabase | I | W |
| --- | --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| [SEC-01](./sec-security.md#sec-01--authentication-and-session-lifecycle-integrity) | Authentication and session lifecycle integrity | `authn` | ● | ● | ● | ● | ● | 4 | 3 |
| [SEC-02](./sec-security.md#sec-02--row-level-security-default-deny-on-every-table) | Row-Level Security default-deny on every table | `authz` | · | · | · | · | ● | 5 | 3 |
| [SEC-03](./sec-security.md#sec-03--security-definer-rpc-and-privileged-role-hygiene) | Security-definer RPC and privileged-role hygiene | `authz` | · | · | · | ● | ● | 5 | 3 |
| [SEC-04](./sec-security.md#sec-04--secrets-kept-out-of-clients-source-and-logs) | Secrets kept out of clients, source, and logs | `secrets` | ● | ● | ● | ● | ● | 5 | 3 |
| [SEC-05](./sec-security.md#sec-05--injection-safe-input-handling-at-trust-boundaries) | Injection-safe input handling at trust boundaries | `input-validation` | ● | ● | ● | ● | ● | 4 | 2 |
| [SEC-06](./sec-security.md#sec-06--transport-encryption-and-on-device-data-protection) | Transport encryption and on-device data protection | `transport-storage` | ● | ● | ● | ● | ● | 4 | 2 |
| [SEC-07](./sec-security.md#sec-07--dependency-and-build-pipeline-integrity) | Dependency and build pipeline integrity | `supply-chain` | ● | ● | ● | ● | ● | 3 | 1 |
| [SEC-08](./sec-security.md#sec-08--server-endpoint-and-webhook-hardening) | Server endpoint and webhook hardening | `api-hardening` | ● | · | · | ● | ● | 4 | 2 |
| [PRV-01](./prv-privacy-data-protection.md#prv-01--pii-inventory-and-schema-minimization) | PII inventory and schema minimization | `minimization` | ● | ● | ● | ● | ● | 3 | 2 |
| [PRV-02](./prv-privacy-data-protection.md#prv-02--analytics-restraint-and-consent) | Analytics restraint and consent | `telemetry` | ● | ● | ● | · | ● | 4 | 2 |
| [PRV-03](./prv-privacy-data-protection.md#prv-03--third-party-egress-inventory-sdks-fonts-cdns-) | Third-party egress inventory (SDKs, fonts, CDNs) | `third-parties` | ● | ● | ● | ● | ● | 4 | 2 |
| [PRV-04](./prv-privacy-data-protection.md#prv-04--no-personal-data-in-logs-and-operator-analytics) | No personal data in logs and operator analytics | `logs-hygiene` | ● | ● | ● | ● | ● | 4 | 2 |
| [PRV-05](./prv-privacy-data-protection.md#prv-05--private-media-exif-signed-urls-cache-lifetime) | Private media: EXIF, signed URLs, cache lifetime | `media` | ● | ● | ● | ● | ● | 5 | 3 |
| [PRV-06](./prv-privacy-data-protection.md#prv-06--local-and-offline-data-protection) | Local and offline data protection | `local-data` | ● | ● | ● | ● | · | 3 | 2 |
| [PRV-07](./prv-privacy-data-protection.md#prv-07--cross-user-exposure-field-set-adequacy-and-minimality) | Cross-user exposure: field-set adequacy and minimality | `exposure-surfaces` | ● | ● | ● | · | ● | 5 | 3 |
| [PRV-08](./prv-privacy-data-protection.md#prv-08--deletion-propagation-and-purge-completeness) | Deletion propagation and purge completeness | `deletion` | ● | ● | ● | · | ● | 5 | 3 |
| [PRV-09](./prv-privacy-data-protection.md#prv-09--ambient-on-device-exposure-of-sensitive-content) | Ambient on-device exposure of sensitive content | `ambient-exposure` | ● | ● | ● | · | · | 4 | 2 |
| [GDP-01](./gdp-gdpr-regulatory.md#gdp-01--consent-records-and-lawful-basis) | Consent records and lawful basis | `lawful-basis` | ● | ● | ● | · | ● | 4 | 3 |
| [GDP-02](./gdp-gdpr-regulatory.md#gdp-02--special-category-data-gating-and-dpia) | Special-category data gating and DPIA | `special-category` | ● | ● | ● | ● | ● | 5 | 3 |
| [GDP-03](./gdp-gdpr-regulatory.md#gdp-03--bystander-data-containment) | Bystander data containment | `bystander-data` | ● | ● | ● | ● | ● | 4 | 3 |
| [GDP-04](./gdp-gdpr-regulatory.md#gdp-04--data-subject-rights-workflows-on-every-client) | Data-subject rights workflows on every client | `dsr-rights` | ● | ● | ● | ● | ● | 5 | 3 |
| [GDP-05](./gdp-gdpr-regulatory.md#gdp-05--transparency-and-store-privacy-declarations) | Transparency and store privacy declarations | `transparency` | ● | ● | ● | · | · | 4 | 2 |
| [GDP-06](./gdp-gdpr-regulatory.md#gdp-06--processor-inventory-dpas-and-transfers) | Processor inventory, DPAs, and transfers | `processors` | ● | ● | ● | ● | ● | 4 | 2 |
| [GDP-07](./gdp-gdpr-regulatory.md#gdp-07--enforced-retention-schedules) | Enforced retention schedules | `retention` | ● | ● | ● | ● | ● | 3 | 2 |
| [GDP-08](./gdp-gdpr-regulatory.md#gdp-08--breach-detection-and-response-readiness) | Breach detection and response readiness | `breach` | · | · | · | ● | ● | 4 | 2 |
| [SAF-01](./saf-safety-wellbeing.md#saf-01--crisis-and-self-harm-response-pathways) | Crisis and self-harm response pathways | `crisis-pathways` | ● | ● | ● | · | ● | 5 | 3 |
| [SAF-02](./saf-safety-wellbeing.md#saf-02--emotionally-safe-engagement-mechanics) | Emotionally safe engagement mechanics | `emotional-design` | ● | ● | ● | · | ● | 3 | 2 |
| [SAF-03](./saf-safety-wellbeing.md#saf-03--ugc-moderation-state-machine-and-takedown) | UGC moderation state machine and takedown | `ugc-moderation` | ● | ● | ● | ● | ● | 4 | 2 |
| [SAF-04](./saf-safety-wellbeing.md#saf-04--block-integrity-and-anti-harassment-enforcement) | Block integrity and anti-harassment enforcement | `social-abuse` | ● | ● | ● | ● | ● | 4 | 3 |
| [SAF-05](./saf-safety-wellbeing.md#saf-05--bystander-exposure-on-outbound-paths) | Bystander exposure on outbound paths | `bystander-privacy` | ● | ● | ● | ● | ● | 4 | 3 |
| [SAF-06](./saf-safety-wellbeing.md#saf-06--age-gating-and-minors-protection-posture) | Age gating and minors protection posture | `minors-safety` | ● | ● | ● | · | ● | 5 | 3 |
| [SAF-07](./saf-safety-wellbeing.md#saf-07--account-takeover-harm-ceiling) | Account takeover harm ceiling | `takeover-ceiling` | ● | ● | ● | ● | ● | 4 | 2 |
| [ARC-01](./arc-code-quality-architecture.md#arc-01--responsibility-and-layer-separation) | Responsibility and layer separation | `layering` | ● | ● | ● | ● | · | 3 | 3 |
| [ARC-02](./arc-code-quality-architecture.md#arc-02--rpc-first-server-side-write-conventions) | RPC-first server-side write conventions | `rpc-convention` | ● | ● | ● | ● | ● | 4 | 3 |
| [ARC-03](./arc-code-quality-architecture.md#arc-03--strict-typing-and-exhaustiveness-discipline) | Strict typing and exhaustiveness discipline | `typing` | ● | ● | ● | ● | ● | 3 | 2 |
| [ARC-04](./arc-code-quality-architecture.md#arc-04--naming-and-file-convention-consistency) | Naming and file convention consistency | `conventions` | ● | ● | ● | ● | ● | 2 | 1 |
| [ARC-05](./arc-code-quality-architecture.md#arc-05--duplication-control-and-dead-code-removal) | Duplication control and dead code removal | `duplication` | ● | ● | ● | ● | ● | 2 | 2 |
| [ARC-06](./arc-code-quality-architecture.md#arc-06--platform-idiom-adherence) | Platform idiom adherence | `idioms` | ● | ● | ● | ● | · | 3 | 2 |
| [ARC-07](./arc-code-quality-architecture.md#arc-07--error-handling-as-code-structure) | Error handling as code structure | `error-handling` | ● | ● | ● | ● | ● | 3 | 2 |
| [ARC-08](./arc-code-quality-architecture.md#arc-08--migration-and-schema-change-quality) | Migration and schema change quality | `schema-quality` | · | · | · | · | ● | 4 | 3 |
| [TST-01](./tst-testing-verification.md#tst-01--core-user-paths-have-automated-tests) | Core user paths have automated tests | `core-coverage` | ● | ● | ● | ● | ● | 4 | 3 |
| [TST-02](./tst-testing-verification.md#tst-02--shared-shapes-tested-against-real-cross-surface-payloads) | Shared shapes tested against real cross-surface payloads | `contract-tests` | ● | ● | ● | ● | ● | 4 | 3 |
| [TST-03](./tst-testing-verification.md#tst-03--fixed-bugs-leave-pinning-regression-tests) | Fixed bugs leave pinning regression tests | `regression` | ● | ● | ● | ● | ● | 3 | 2 |
| [TST-04](./tst-testing-verification.md#tst-04--runnable-harnesses-for-destructive-cross-cutting-operations) | Runnable harnesses for destructive cross-cutting operations | `harnesses` | · | · | · | ● | ● | 5 | 3 |
| [TST-05](./tst-testing-verification.md#tst-05--tests-assert-behavior-with-real-oracles) | Tests assert behavior with real oracles | `test-quality` | ● | ● | ● | ● | ● | 2 | 2 |
| [TST-06](./tst-testing-verification.md#tst-06--no-merge-without-the-touched-surfaces-gates) | No merge without the touched surfaces' gates | `ci-gates` | ● | ● | ● | ● | ● | 4 | 3 |
| [TST-07](./tst-testing-verification.md#tst-07--one-canonical-test-framework-and-idiom-per-surface) | One canonical test framework and idiom per surface | `platform-idioms` | ● | ● | ● | ● | ● | 2 | 1 |
| [TST-08](./tst-testing-verification.md#tst-08--negative-authorization-tests-in-ci) | Negative authorization tests in CI | `authz-tests` | · | · | · | · | ● | 5 | 3 |
| [PLT-01](./plt-platform-store-compliance.md#plt-01--in-app-account-deletion-entry-points-store-compliant) | In-app account deletion entry points, store-compliant | `store-accounts` | ● | ● | ● | · | · | 5 | 3 |
| [PLT-02](./plt-platform-store-compliance.md#plt-02--truthful-privacy-declarations-and-tracking-consent) | Truthful privacy declarations and tracking consent | `store-privacy` | · | ● | ● | · | · | 4 | 3 |
| [PLT-03](./plt-platform-store-compliance.md#plt-03--sign-in-options-meet-platform-equity-rules) | Sign-in options meet platform equity rules | `store-accounts` | ● | ● | ● | · | ● | 3 | 2 |
| [PLT-04](./plt-platform-store-compliance.md#plt-04--ugc-safety-apparatus-filter-report-block-respond) | UGC safety apparatus: filter, report, block, respond | `store-ugc` | ● | ● | ● | ● | ● | 5 | 3 |
| [PLT-05](./plt-platform-store-compliance.md#plt-05--store-technical-currency-target-api-and-toolchain-floors-) | Store technical currency (target API and toolchain floors) | `store-currency` | · | ● | ● | · | · | 3 | 1 |
| [PLT-06](./plt-platform-store-compliance.md#plt-06--pwa-installability-and-service-worker-lifecycle-discipline) | PWA installability and service worker lifecycle discipline | `pwa-standards` | ● | · | · | · | · | 3 | 2 |
| [PLT-07](./plt-platform-store-compliance.md#plt-07--hosting-platform-hardening-headers-and-deployment-protection) | Hosting platform hardening: headers and deployment protection | `deploy-platform` | ● | · | · | ● | · | 4 | 2 |
| [PLT-08](./plt-platform-store-compliance.md#plt-08--managed-database-platform-configuration) | Managed database platform configuration | `db-platform` | · | · | · | · | ● | 5 | 3 |
| [A11Y-01](./a11y-accessibility-inclusion.md#a11y-01--keyboard-operability-and-accessible-semantics) | Keyboard operability and accessible semantics | `wcag-keyboard-semantics` | ● | · | · | ● | · | 4 | 3 |
| [A11Y-02](./a11y-accessibility-inclusion.md#a11y-02--contrast-reflow-and-text-resize) | Contrast, reflow, and text resize | `wcag-visual` | ● | · | · | ● | · | 3 | 2 |
| [A11Y-03](./a11y-accessibility-inclusion.md#a11y-03--voiceover-and-talkback-support) | VoiceOver and TalkBack support | `mobile-screen-readers` | · | ● | ● | · | · | 4 | 3 |
| [A11Y-04](./a11y-accessibility-inclusion.md#a11y-04--dynamic-type-font-scaling-touch-targets) | Dynamic Type, font scaling, touch targets | `mobile-scaling-targets` | · | ● | ● | · | · | 3 | 2 |
| [A11Y-05](./a11y-accessibility-inclusion.md#a11y-05--dark-light-parity-and-high-contrast-modes) | Dark/light parity and high-contrast modes | `theming` | ● | ● | ● | ● | · | 2 | 1 |
| [A11Y-06](./a11y-accessibility-inclusion.md#a11y-06--reduced-motion-honored-across-all-animation) | Reduced motion honored across all animation | `motion` | ● | ● | ● | ● | · | 3 | 2 |
| [A11Y-07](./a11y-accessibility-inclusion.md#a11y-07--localization-completeness-and-locale-safe-formatting) | Localization completeness and locale-safe formatting | `i18n` | ● | ● | ● | · | ● | 3 | 3 |
| [A11Y-08](./a11y-accessibility-inclusion.md#a11y-08--inclusive-language-and-emotional-vocabulary) | Inclusive language and emotional vocabulary | `inclusive-content` | ● | ● | ● | · | ● | 3 | 2 |
| [PRF-01](./prf-performance-efficiency.md#prf-01--core-web-vitals-budgets-and-measurement) | Core Web Vitals budgets and measurement | `web-vitals` | ● | · | · | · | · | 3 | 3 |
| [PRF-02](./prf-performance-efficiency.md#prf-02--client-javascript-bundle-discipline) | Client JavaScript bundle discipline | `bundle` | ● | · | · | ● | · | 2 | 2 |
| [PRF-03](./prf-performance-efficiency.md#prf-03--image-and-media-delivery-pipeline) | Image and media delivery pipeline | `media-pipeline` | ● | ● | ● | ● | ● | 3 | 3 |
| [PRF-04](./prf-performance-efficiency.md#prf-04--indexes-match-access-paths-and-rls-predicates) | Indexes match access paths and RLS predicates | `query-efficiency` | · | · | · | · | ● | 3 | 3 |
| [PRF-05](./prf-performance-efficiency.md#prf-05--bounded-batched-lean-client-reads) | Bounded, batched, lean client reads | `query-efficiency` | ● | ● | ● | ● | · | 3 | 3 |
| [PRF-06](./prf-performance-efficiency.md#prf-06--mobile-cold-start-frames-and-animation) | Mobile cold start, frames, and animation | `mobile-startup` | · | ● | ● | · | · | 3 | 2 |
| [PRF-07](./prf-performance-efficiency.md#prf-07--layered-caching-strategy-and-offline-reads) | Layered caching strategy and offline reads | `caching` | ● | ● | ● | · | · | 2 | 2 |
| [PRF-08](./prf-performance-efficiency.md#prf-08--network-and-battery-frugality) | Network and battery frugality | `network-frugality` | ● | ● | ● | ● | · | 2 | 2 |
| [REL-01](./rel-reliability-observability.md#rel-01--failure-states-distinct-from-empty-states) | Failure states distinct from empty states | `error-surfacing` | ● | ● | ● | ● | · | 3 | 3 |
| [REL-02](./rel-reliability-observability.md#rel-02--bounded-timeouts-and-deliberate-retries) | Bounded timeouts and deliberate retries | `timeouts-retries` | ● | ● | ● | ● | ● | 3 | 2 |
| [REL-03](./rel-reliability-observability.md#rel-03--atomic-multi-step-writes) | Atomic multi-step writes | `integrity` | ● | ● | ● | ● | ● | 4 | 3 |
| [REL-04](./rel-reliability-observability.md#rel-04--idempotence-and-double-submit-protection) | Idempotence and double-submit protection | `concurrency` | ● | ● | ● | ● | ● | 3 | 2 |
| [REL-05](./rel-reliability-observability.md#rel-05--predictable-offline-and-dependency-down-behavior) | Predictable offline and dependency-down behavior | `offline-degradation` | ● | ● | ● | · | · | 3 | 2 |
| [REL-06](./rel-reliability-observability.md#rel-06--contract-safe-migrations-with-rollback-story) | Contract-safe migrations with rollback story | `migration-safety` | · | · | · | · | ● | 4 | 3 |
| [REL-07](./rel-reliability-observability.md#rel-07--backups-exist-and-restore-is-rehearsed) | Backups exist and restore is rehearsed | `backup-restore` | · | · | · | · | ● | 5 | 3 |
| [REL-08](./rel-reliability-observability.md#rel-08--production-failures-reach-a-human) | Production failures reach a human | `crash-reporting` | ● | ● | ● | ● | ● | 3 | 3 |
| [AGT-01](./agt-agentic-development-readiness.md#agt-01--layered-agent-instruction-docs-accurate-and-lean) | Layered agent instruction docs, accurate and lean | `instruction-docs` | ● | ● | ● | ● | ● | 3 | 3 |
| [AGT-02](./agt-agentic-development-readiness.md#agt-02--product-map-freshness-with-drift-gates) | Product map freshness with drift gates | `map-freshness` | ● | ● | ● | ● | ● | 2 | 2 |
| [AGT-03](./agt-agentic-development-readiness.md#agt-03--provable-changes-fast-agent-verification-loops) | Provable changes: fast agent verification loops | `verifiability` | ● | ● | ● | ● | ● | 3 | 3 |
| [AGT-04](./agt-agentic-development-readiness.md#agt-04--dangerous-operations-flagged-where-agents-read) | Dangerous operations flagged where agents read | `guardrails` | ● | ● | ● | ● | ● | 4 | 3 |
| [AGT-05](./agt-agentic-development-readiness.md#agt-05--scripts-over-tribal-knowledge) | Scripts over tribal knowledge | `determinism` | ● | ● | ● | ● | ● | 2 | 2 |
| [AGT-06](./agt-agentic-development-readiness.md#agt-06--machine-checkable-contribution-conventions) | Machine-checkable contribution conventions | `conventions` | ● | ● | ● | ● | ● | 2 | 1 |
| [AGT-07](./agt-agentic-development-readiness.md#agt-07--least-privilege-for-agents-and-automation) | Least privilege for agents and automation | `automation-safety` | ● | ● | ● | ● | ● | 5 | 3 |
| [AGT-08](./agt-agentic-development-readiness.md#agt-08--decision-log-discipline) | Decision log discipline | `decision-memory` | ● | ● | ● | ● | ● | 2 | 2 |