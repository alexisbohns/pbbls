# REL — Reliability & Observability

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Failure surfacing, timeout/retry policy, integrity under partial failure, offline behavior, migration safety, crash reporting, degradation, concurrency.

---

## REL-01 · Failure states distinct from empty states

**Does every user-facing data load and mutation distinguish failure from emptiness, showing the user a recoverable error state and logging the cause?**

`error-surfacing` · applies to: `web` `ios` `android` `admin` · default impact **3/5** · weight **3/3**

Every screen or component that loads remote data has at least three terminal render states (loaded, genuinely empty, failed), and the failed state is visually and semantically distinct from the empty state, with a retry affordance. Every mutation reports failure to the user, not only to a console. Every catch or error path emits a labeled log entry; silently swallowed errors (empty catch blocks, ignored error returns) do not exist.

*Why it matters:* When a failed load renders as an empty state, users of a personal-data product conclude their data is gone, which destroys trust instantly and produces support noise that looks like data loss. Silent catches also make production debugging impossible on every surface.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Fetch errors are swallowed or crash; screens render the empty-state visual on failure; empty catch blocks appear in the codebase; mutations fail without telling the user. |
| **1 · Ad-hoc** | A few screens show an error message; most collapse failure into empty or spin forever; error handling style varies by author with no shared pattern. |
| **2 · Defined** | A shared loading/empty/error state pattern exists and is documented; most data-bearing screens use it; some still conflate failure with empty; logging on error paths is inconsistent or dev-only. |
| **3 · Managed** | All data-bearing screens implement the tri-state pattern with a retry affordance; every catch path logs with an operation label; error logs are never gated behind a development-only flag; the pattern is checked in code review. |
| **4 · Verified** | A lint rule or CI grep rejects empty catch blocks and unlabeled error paths; UI, snapshot, or unit tests cover the error state and the empty state as separate cases per screen; drift fails the build. |

### Audit checklist

- [ ] Grep all client surfaces for swallowed errors: `grep -rEn "catch\s*(\([^)]*\))?\s*\{\s*\}"` on .ts/.tsx, `catch \{\s*\}` on .swift, `catch \(.*\) \{\s*\}` on .kt; every hit is a finding.
- [ ] Enumerate data-fetching hooks/view models (grep for the data-provider or repository interface) and check each exposes an error/failed member distinct from an empty result, not just data-or-nil.
- [ ] For a Next.js App Router surface, list route segments with a page that fetches and verify an `error.tsx` boundary or an explicit in-component error branch exists for each; a missing boundary means failures bubble to a generic crash or a fake-empty render.
- [ ] Pick two screens per surface, trace the failure path from the network call to the render, and record whether the UI shown on error is the same component as the zero-items state.
- [ ] Grep for error logs gated behind environment checks (e.g. `NODE_ENV === "development"` wrapping console.error, `#if DEBUG` wrapping error logging) on paths that can fail in production.
- [ ] Trace one mutation (create/update) failure per surface and confirm the user sees a message or toast, not only a console line.

### Monitoring signals

- grep -rEn 'catch\s*(\([^)]*\))?\s*\{\s*\}' across app code returns zero hits
- ESLint `no-empty` with `allowEmptyCatch: false` (and SwiftLint `empty_catch`-style rule, detekt EmptyCatchBlock) enabled in each surface's lint config
- Every App Router route group that fetches has an error.tsx sibling (scriptable directory check in CI)
- grep for 'NODE_ENV' or '#if DEBUG' adjacent to console.error/os_log error calls returns zero hits on failure paths

### References

- [OWASP Application Security Verification Standard 4.0.3 — V7.4 Error Handling](https://owasp.org/www-project-application-security-verification-standard/)
- [CWE-390: Detection of Error Condition Without Action — CWE-390](https://cwe.mitre.org/data/definitions/390.html)
- [Apple Human Interface Guidelines — Patterns: Loading](https://developer.apple.com/design/human-interface-guidelines/loading)
- ISO/IEC 25010 Product quality model — Reliability: Fault tolerance

### Typical remediation

Introduce one shared view-state abstraction per surface (a Result/UiState enum or a tri-state hook) plus a reusable error component with retry, then migrate screens to it. Enable the empty-catch lint rules and add labeled logging to every catch path as screens are touched.

*Issue skeleton:* [`templates/rel-01.md`](../templates/rel-01.md)

---

## REL-02 · Bounded timeouts and deliberate retries

**Does every network call that can block a user or a job carry an explicit, labeled timeout, and is every retry bounded, backed off, and applied only to idempotent operations?**

`timeouts-retries` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **2/3**

All network I/O that gates rendering, user interaction, or a background job completes, fails, or times out within an explicit bounded window carrying an operation label. Retries, where present, are deliberate policy (bounded attempt count, backoff, idempotence precondition) rather than accidental loops or user button-mashing. No flow can hang indefinitely on a library's unbounded default.

*Why it matters:* A hung request without a timeout is an infinite spinner, which users experience as a broken app, and unbounded retries against a struggling backend become a self-inflicted outage. Explicit labeled timeouts also turn silent hangs into diagnosable log lines.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Network calls rely on library defaults with no explicit timeouts; a hung request spins forever; the only retry mechanism is the user tapping again. |
| **1 · Ad-hoc** | A timeout wrapper or configured client timeout exists and is used in a handful of call sites; most blocking calls are unprotected; any retry loop lacks backoff or a bound. |
| **2 · Defined** | A documented timeout utility or client-level timeout configuration exists with a stated default and per-call labels; the convention says to wrap blocking calls but adoption is incomplete; retry policy is defined for some operations only. |
| **3 · Managed** | Blocking call sites have been audited and are covered (wrapper or transport-level timeout); the retry policy (attempts, backoff, which operations qualify as idempotent) is written down and consistently applied; timeout failures log their label. |
| **4 · Verified** | A lint rule or CI grep flags raw unwrapped client calls on blocking paths and bare outbound fetches without an abort signal in server-side functions; tests exercise the timeout path and assert the bounded-retry behavior. |

### Audit checklist

- [ ] Locate the timeout mechanism per surface: grep for a `withTimeout`-style wrapper in web code, `timeoutIntervalForRequest`/`timeoutIntervalForResource` on URLSessionConfiguration in iOS, OkHttp/Ktor `connectTimeout`/`readTimeout` configuration in Android.
- [ ] Grep client code for raw database-client calls (`.from(`, `.rpc(`, `.storage`) reachable from render-blocking or interaction-blocking paths and check each is wrapped or covered by a transport-level timeout.
- [ ] In server-side/edge functions, grep for `fetch(` and verify each outbound call passes an abort signal (e.g. `AbortSignal.timeout(...)`) or an equivalent deadline.
- [ ] Grep for retry constructs (`retry`, `attempt`, `maxRetries`, `while (true)`) and verify each loop has a bound, a backoff between attempts, and wraps only operations that are safe to repeat.
- [ ] Trace the app-startup/auth-resolution path and confirm it cannot block forever on a network call (watchdog, timeout, or synchronous state source).
- [ ] Check that timeout failures carry an operation label (grep the wrapper for a label parameter and sample its call sites).

### Monitoring signals

- Count of raw `.rpc(`/`.from(` call sites outside the data-layer wrapper on blocking paths is zero (grep-based CI check)
- grep -rn 'fetch(' in edge/server functions with no AbortSignal in scope returns zero hits
- grep -rEn 'while\s*\(true\)' near network calls returns zero hits
- A unit test exists that asserts the timeout wrapper rejects after its deadline with the operation label in the error

### References

- [Amazon Builders' Library: Timeouts, retries, and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
- [Google SRE Book — Chapter 22: Addressing Cascading Failures](https://sre.google/sre-book/addressing-cascading-failures/)
- [CWE-400: Uncontrolled Resource Consumption — CWE-400](https://cwe.mitre.org/data/definitions/400.html)

### Typical remediation

Standardize one labeled timeout wrapper (or transport-level timeout config) per surface with a sane default, sweep blocking call sites onto it, and write the retry policy down (bounded attempts, jittered backoff, idempotent operations only). Add the grep-based CI check so new call sites cannot regress.

*Issue skeleton:* [`templates/rel-02.md`](../templates/rel-02.md)

---

## REL-03 · Atomic multi-step writes

**Does every write that spans multiple tables or resources execute in a single server-side transaction, so that a partial failure can never leave the shared database inconsistent?**

`integrity` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Any mutation touching more than one table, or pairing a file/blob write with a database row, runs as one server-side transactional unit (stored function, RPC, or transactional server routine); clients never chain dependent writes where a mid-sequence failure strands partial state. File-plus-row pairs have a defined consistency story (fail-safe ordering or orphan cleanup). Client-stitched multi-table writes are treated as defects, not style choices.

*Why it matters:* In a multi-client shared-database product, PostgREST-style clients have no cross-call transactions, so a dropped connection between two chained writes silently corrupts the one contract all surfaces depend on. Server-side transactions also centralize ownership checks instead of duplicating them per client.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Clients chain awaited inserts/updates across tables; partial failure leaves dangling or half-written records; no transactional server functions exist beyond single statements. |
| **1 · Ad-hoc** | Some flows happen to use server-side functions; the choice is ad-hoc and new flows still stitch client calls; nobody can list which flows are atomic. |
| **2 · Defined** | A written rule mandates server-side transactions for multi-statement writes and most flows comply; the exceptions are known; file-plus-row consistency is unaddressed. |
| **3 · Managed** | Multi-step writes are inventoried and all routed through transactional server functions with ownership checks inside; storage/row pairs fail safe or have cleanup; code review enforces the rule for new flows. |
| **4 · Verified** | A runnable harness exercises each critical multi-step write against a real database, including an induced mid-step failure, and asserts zero partial state; a CI or grep check flags chained dependent client writes. |

### Audit checklist

- [ ] Grep each client surface for two or more awaited write calls (`insert`, `update`, `upsert`, `delete`) to different tables within one function body; each hit is a candidate stitched write.
- [ ] List server-side transactional functions (grep migrations for `create or replace function` and `create function`) and map every multi-step client flow to one; flows with no mapping are findings.
- [ ] Trace the create-with-media flow end to end: determine whether the blob upload or the row insert happens first, what happens when the second step fails, and whether any cleanup job or fail-safe ordering handles the orphan.
- [ ] Read the database verify/acceptance harness scripts (if any) and confirm they run against a real database and assert post-conditions after induced failure, not only the happy path.
- [ ] Inspect server-side/edge functions performing multi-statement writes for explicit transaction boundaries rather than sequential independent statements.
- [ ] Check deletion/purge flows: a user-data purge that spans tables must be one transaction, and every new table added to it must be covered by its harness in the same change.

### Monitoring signals

- Grep-based CI check: no client function body contains awaited writes to two different tables
- A verify harness per critical multi-step write exists under a scripts/ directory and is runnable on demand (documented command)
- Every table referenced by the purge/delete function also appears in the purge harness's assertions (scriptable diff)
- New RPC migrations touching an existing function body are diffed pairwise in CI to catch silent overwrite of a sibling migration's append

### References

- [PostgreSQL Documentation — Tutorial: Transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html)
- [Regulation (EU) 2016/679 (GDPR) — Art. 32(1)(b) integrity and resilience of processing systems](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- ISO/IEC 25010 Product quality model — Reliability: Recoverability

### Typical remediation

Move each stitched multi-table client write into a server-side transactional function with ownership checks, keeping sibling functions symmetric, then add or extend a database harness that induces a mid-step failure and asserts no partial state. Define the orphan policy for blob-plus-row writes.

*Issue skeleton:* [`templates/rel-03.md`](../templates/rel-03.md)

---

## REL-04 · Idempotence and double-submit protection

**Are duplicate submissions, retried requests, and concurrent writes prevented from creating duplicate records or corrupted state, by server-side guards rather than UI discipline alone?**

`concurrency` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **2/3**

Every user-triggered mutation is protected against double-fire at two layers: the client disables or debounces while pending, and the server enforces at-most-once effect via a unique constraint, idempotency key, conflict-aware upsert, or state-machine check. Operations that a retry policy or an offline queue may replay are idempotent by construction. The concurrency model for the same record edited from two sessions or devices is explicitly chosen (e.g. last-write-wins, versioning) rather than emergent.

*Why it matters:* Retries and offline queues are only safe when the write they replay is idempotent; without server-side guards, a double-tap or a timed-out-then-retried request duplicates records or double-credits ledgers. UI-only guards evaporate the moment a second client or a retry path exists.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Submit handlers can fire twice; no unique constraints beyond primary keys; retrying a create duplicates the row; ledger-style inserts can double-credit. |
| **1 · Ad-hoc** | Some buttons disable while pending; the server accepts duplicates; concurrency between devices has never been considered. |
| **2 · Defined** | Client-side pending guards are the norm and a few server-side unique constraints exist; which mutations are replay-safe is not written down; multi-device conflicts are a known but unhandled gap. |
| **3 · Managed** | Every non-idempotent mutation has a named server-side guard (constraint, idempotency key, transactional upsert, or state check that makes a second call a no-op); the concurrency model is documented; the highest-risk flows are tested. |
| **4 · Verified** | A harness replays each critical mutation twice against a real database and asserts exactly one observable effect; CI includes at least one concurrent-writer test for the most race-prone flow. |

### Audit checklist

- [ ] Grep submit handlers for pending-state guards (`isSubmitting`, `disabled`, `isLoading` gating the action; `.disabled(` in SwiftUI; `enabled =` in Compose) and list mutations without one.
- [ ] Grep migrations for `unique` constraints and indexes; map each user-triggered create flow to the constraint or guard that makes a duplicate call harmless; unmapped flows are findings.
- [ ] Read the bodies of publish/complete/claim-style server functions and check what happens on a second identical call (look for `on conflict`, status preconditions, `for update` row locks); a second call must be a no-op or a clean error, never a second effect.
- [ ] Inspect ledger-like inserts (credits, karma, counters) for a dedupe key or transactional balance check that prevents double-crediting under replay.
- [ ] Check any offline queue or retry wrapper: list the operations it can replay and verify each is idempotent server-side.
- [ ] Look for a written statement of the multi-device conflict policy (docs or decision log); absence means the model is emergent.

### Monitoring signals

- Every create-flow table has a documented uniqueness or idempotence guard (auditable mapping file or migration comments)
- A replay harness step exists: calling the critical mutation twice yields one row/effect (assertion in a runnable script)
- grep for ledger insert sites confirms each passes a dedupe/idempotency key
- Lint or review checklist item: new mutations must name their double-submit guard in the PR description

### References

- [CWE-362: Concurrent Execution using Shared Resource with Improper Synchronization ('Race Condition') — CWE-362](https://cwe.mitre.org/data/definitions/362.html)
- [Amazon Builders' Library: Making retries safe with idempotent APIs](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/)
- [PostgreSQL Documentation — INSERT: ON CONFLICT Clause](https://www.postgresql.org/docs/current/sql-insert.html)

### Typical remediation

Add the missing server-side guard per mutation (unique constraint, `on conflict` upsert, status precondition, or idempotency key column), keep client pending-guards as UX polish rather than the safety mechanism, and extend the database harness with a replay-twice assertion for each critical flow.

*Issue skeleton:* [`templates/rel-04.md`](../templates/rel-04.md)

---

## REL-05 · Predictable offline and dependency-down behavior

**When the network is absent or the backend is down, does each client render an intentional state, preserve in-progress user input, and recover cleanly on reconnect?**

`offline-degradation` · applies to: `web` `ios` `android` · default impact **3/5** · weight **2/3**

Offline and backend-unreachable conditions produce a designed outcome per screen (cached content, a read-only mode, or an explicit offline notice), never a crash, a blank screen, or a fake-empty state. Input the user is composing is preserved locally (draft, queue, or an explicit warning before loss) and survives app termination mid-compose. Which features degrade to read-only and which are blocked is a decision someone made, written down, and recovery on reconnect is automatic or one tap.

*Why it matters:* Mobile-first journaling happens in elevators, planes, and dead zones; losing a half-composed entry to a network blip destroys exactly the moment the product exists to capture. A dependency outage that renders every screen blank is indistinguishable from account deletion in the user's eyes.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Airplane mode yields infinite spinners, crashes, or blank screens; a half-typed entry is lost on failure or backgrounding; no screen renders cached content. |
| **1 · Ad-hoc** | Some cached data renders as an accident of the framework or HTTP cache; behavior varies by screen; there is no offline notice and no draft preservation. |
| **2 · Defined** | An offline or connection-error notice exists; the primary composition flow persists drafts locally; secondary flows are undefined; reconnect requires a manual reload. |
| **3 · Managed** | A documented offline matrix states what works, what is read-only, and what is blocked; primary user input auto-saves locally and survives process death; reconnect resumes gracefully; the matrix is manually verified each release. |
| **4 · Verified** | Automated tests run the key flows with the network disabled or failing (service-worker tests, URLProtocol stubs, OkHttp fault interceptors) and assert the designed state and draft survival; regressions fail CI. |

### Audit checklist

- [ ] On the web PWA, open the service worker registration and manifest: identify the caching strategy and whether an offline fallback route exists; a manifest with no offline handling is a PWA in name only.
- [ ] Grep for connectivity monitoring: `navigator.onLine`/`online`/`offline` event listeners on web, `NWPathMonitor` on iOS, `ConnectivityManager`/`NetworkCallback` on Android; absence means the app cannot even know it is offline.
- [ ] Trace the primary compose/create flow: where is in-progress input persisted (local store, draft table with local fallback, nowhere), what happens when the save call fails, and does the draft survive killing the app mid-compose.
- [ ] Point each client at an unreachable backend (or block the host) and load the main screens; record for each whether the result is cached content, an intentional notice, a spinner, a crash, or a fake-empty state.
- [ ] Check whether any queued-while-offline writes are replayed on reconnect, and cross-check with REL-04 that the replayed operations are idempotent.
- [ ] Search docs for an offline behavior matrix or decision entry; absence at level 3+ is disqualifying.

### Monitoring signals

- Service worker precaches an offline fallback and the app shell (scriptable check of the SW config/build output)
- A local draft persistence layer exists for the primary input flow (grep for the draft store) and has a unit test covering restore-after-restart
- At least one automated test per mobile surface stubs the transport to fail and asserts the designed offline state
- The offline matrix document exists and names every primary screen

### References

- [web.dev: Offline UX design guidelines](https://web.dev/articles/offline-ux-design-guidelines)
- [Android Core App Quality checklist — Functionality and network handling items](https://developer.android.com/docs/quality-guidelines/core-app-quality)
- ISO/IEC 25010 Product quality model — Reliability: Fault tolerance

### Typical remediation

Decide the degradation boundary per feature and write the matrix; add connectivity monitoring plus an offline notice component; persist the primary compose flow locally with restore-on-launch; then add transport-failure tests so the behavior stops regressing.

*Issue skeleton:* [`templates/rel-05.md`](../templates/rel-05.md)

---

## REL-06 · Contract-safe migrations with rollback story

**Can every schema migration deploy without breaking clients already in the field, and does each migration have an understood blast radius and a stated rollback path before it runs?**

`migration-safety` · applies to: `supabase` · default impact **4/5** · weight **3/3**

Migrations are append-only, ordered files; a migration that changes a contract consumed by deployed clients (especially mobile binaries that cannot be force-updated) follows expand-contract, shipping the breaking half only after all clients tolerate both shapes. Destructive DDL (drops, type narrowing, adding not-null to populated columns) is flagged and staged deliberately. Every migration lands with regenerated contract artifacts (generated types) in the same change, and its rollback path (revert migration or documented recovery) is stated.

*Why it matters:* The shared database is the only contract between the surfaces, so one careless drop bricks every mobile binary in the field simultaneously, and app-store review latency means the fix takes days. Ordered, append-only, rehearsed migrations are the difference between deployment and roulette.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Old migration files get edited or overwritten; drops and renames ship without checking deployed clients; nobody can say what a given migration would break; contract types drift from the schema. |
| **1 · Ad-hoc** | Migrations are append-only by habit; contract breakage is caught, if at all, by reviewer luck; regenerating types is remembered sometimes; rollback is never considered. |
| **2 · Defined** | Written rules exist (append-only, regenerate types in the same change, check the other surfaces on contract changes); expand-contract is known but unenforced; destructive changes are reviewed case by case; known sharp edges (e.g. two pending migrations re-emitting the same function body) are documented. |
| **3 · Managed** | Every contract-touching migration is checked against all deployed client versions before merge; destructive changes are staged across releases (expand, migrate, contract); each migration PR states its rollback path; a full local reset-and-apply run is part of the pre-merge workflow. |
| **4 · Verified** | CI applies the entire migration chain to a scratch database on every PR; a CI check flags destructive DDL keywords and duplicate re-emissions of the same function body for explicit human sign-off; generated contract types are diffed in CI so drift fails the build. |

### Audit checklist

- [ ] Run `git log --follow --diff-filter=M` over the migrations directory to detect edits to already-landed migration files; any modification of an old file is a finding.
- [ ] Grep migrations for destructive DDL: `drop table`, `drop column`, `alter column .* type`, `set not null`, `rename`; for each hit, verify the change was staged expand-contract relative to client releases at that date.
- [ ] Correlate migration commits with regeneration of the generated types/contract file (same commit or same PR); orphan migrations mean the contract artifact lies.
- [ ] Check CI workflows for a job that applies the full migration chain to a fresh database per PR; note its absence.
- [ ] For pending unmerged migrations, diff any two that re-emit the same function body (`create or replace function` with the same name); `create or replace` has no merge semantics, so the later one silently drops the earlier one's changes.
- [ ] Sample three recent migration PRs and check whether a rollback path is stated (revert migration, backup point, or documented recovery).

### Monitoring signals

- CI job exists that runs the migration chain against a scratch database on every PR touching migrations
- CI grep flags destructive DDL keywords in new migrations and requires a sign-off label
- Generated types file diff is clean after regeneration in CI (schema/contract drift detector)
- git history shows zero modifications to previously-landed migration files over the audit window

### References

- [Martin Fowler: Evolutionary Database Design](https://martinfowler.com/articles/evodb.html)
- [Martin Fowler: ParallelChange (expand-contract)](https://martinfowler.com/bliki/ParallelChange.html)
- [Supabase Docs: Database migrations](https://supabase.com/docs/guides/deployment/database-migrations)

### Typical remediation

Adopt expand-contract as the default for any contract change, add the scratch-database migration run and the destructive-DDL flag to CI, and make 'rollback path' a required line in the migration PR template. Add a pairwise-diff check for same-function re-emissions in a pending batch.

*Issue skeleton:* [`templates/rel-06.md`](../templates/rel-06.md)

---

## REL-07 · Backups exist and restore is rehearsed

**Are the database, file storage, and auth/identity data all covered by automated backups with known retention, and has a restore actually been performed and documented at least once?**

`backup-restore` · applies to: `supabase` · default impact **5/5** · weight **3/3**

Automated backups cover every store holding user data: the relational database, blob/file storage, and auth or identity records; retention, recovery point objective, and recovery time objective are stated numbers someone accepted. The team knows precisely what the current platform tier includes (daily snapshots versus point-in-time recovery) rather than assuming. A restore to a scratch environment has been executed, verified against real data, and dated in writing; an untested backup is a hope, not a control.

*Why it matters:* For a product holding users' intimate records, unrecoverable loss is the single worst outcome, worse than downtime, and GDPR Art. 32 explicitly requires the ability to restore availability and access to personal data after an incident. Blob storage and auth data are the classically forgotten stores that make a 'successful' database restore useless.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No backups beyond whatever the platform silently does; nobody can state the retention; storage buckets and auth data are not covered by anything. |
| **1 · Ad-hoc** | Platform default backups are assumed to exist but have never been verified; whether storage objects and auth users are included is unknown; there is no restore procedure. |
| **2 · Defined** | Backup coverage and retention are documented per store (tier, PITR yes or no, buckets, auth); a restore procedure is written but has never been executed. |
| **3 · Managed** | A restore has been rehearsed to a scratch project with data spot-verified, storage objects included or separately exported, RPO and RTO stated and accepted, and the rehearsal dated in the docs; rehearsal recurs at a stated cadence. |
| **4 · Verified** | Backup verification is automated and scheduled (a restore job, or an export plus integrity check on cron/CI); a failed backup or export alerts a human; rehearsal cadence is enforced by the schedule, not by memory. |

### Audit checklist

- [ ] Search the repo docs and decision log for backup, restore, PITR, and retention statements; record what is claimed versus what is silent.
- [ ] Determine the actual platform tier and its included backup features (daily snapshot vs point-in-time recovery, retention window); check config files and any infra notes rather than trusting memory.
- [ ] Enumerate blob/storage buckets (grep migrations and config for bucket creation) and verify each is covered by the platform backup or by a separate scheduled export; platform database backups typically do not include storage objects.
- [ ] Check `.github/workflows` and cron/scheduler config for scheduled `pg_dump`, storage sync, or auth export jobs, and whether their failure notifies anyone.
- [ ] Look for a dated restore-rehearsal record naming what was restored, where, and what was verified; absence caps the score at level 2.
- [ ] Confirm auth/identity data (users, credentials metadata) is included in whatever restore path exists.

### Monitoring signals

- A scheduled workflow performs an export or restore-verification and fails loudly (notification hook) on error
- Docs contain a dated restore rehearsal entry newer than the stated cadence
- Every storage bucket found in migrations appears in the backup coverage document (scriptable diff)
- RPO and RTO appear as explicit numbers in the reliability docs

### References

- [Regulation (EU) 2016/679 (GDPR) — Art. 32(1)(c) ability to restore availability and access to personal data](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [NIST SP 800-34 Rev. 1: Contingency Planning Guide for Federal Information Systems](https://csrc.nist.gov/pubs/sp/800/34/r1/upd1/final)
- [Supabase Docs: Database backups](https://supabase.com/docs/guides/platform/backups)

### Typical remediation

Document the real coverage per store first, then close the gaps (typically a scheduled logical dump plus a storage-object sync to independent storage), execute one full restore to a scratch project, date it, and put the rehearsal and an export-verification job on a schedule with failure alerting.

*Issue skeleton:* [`templates/rel-07.md`](../templates/rel-07.md)

---

## REL-08 · Production failures reach a human

**Do production crashes and serious errors on every surface reliably reach a monitored destination with enough context to act, and with sensitive content scrubbed?**

`crash-reporting` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **3/3**

Each client surface reports crashes and serious handled errors to a destination a human actually monitors (a crash/error SDK, actively-watched platform vitals, or shipped server-side error logs with alerting); logging to the device console alone is recognized as reaching no one. Server, database, and edge-function failures are logged where someone looks, with alerting on spikes. Because reports can embed user content, the pipeline scrubs personal and sensitive data before it leaves the device or server.

*Why it matters:* Without a feedback loop, production failures are discovered through app-store reviews or silent churn, and every other reliability control degrades unverified. For a product carrying intimate data, an unscrubbed crash report is itself a data leak, so the loop must be built with minimization in mind.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No crash or error reporting anywhere; client errors end at the device console; production failures are learned from user complaints or never. |
| **1 · Ad-hoc** | Platform-provided dashboards (store vitals, hosting logs) technically exist but nobody watches them on a cadence; no client-side reporting SDK or log shipping. |
| **2 · Defined** | At least one surface has real error reporting with evidence of use; the others rely on ad-hoc log checking; no alerting; scrubbing not configured. |
| **3 · Managed** | All surfaces report crashes and serious errors to a monitored destination; personal-data scrubbing is configured and reviewed; a triage routine exists (who looks, on what cadence); server and edge failures are included. |
| **4 · Verified** | Error-rate thresholds alert or page the maintainer automatically; release health (crash-free sessions) informs or gates rollouts; a deliberate test error verifies the pipeline end to end after configuration changes. |

### Audit checklist

- [ ] Grep dependency manifests for crash/error SDKs (sentry, crashlytics, bugsnag, datadog) across package.json files, the Xcode project spec or Package.swift, and build.gradle files; note which surfaces have none.
- [ ] Where no SDK exists, look for any substitute loop: shipped server logs, a database error/log table with an admin view that reads it, hosting-platform log drains, or store-vitals checking documented as a routine.
- [ ] Sample client catch paths and determine where the error terminates: a console call on the user's device counts as unreported.
- [ ] If an SDK exists, open its init config and verify sensitive-data scrubbing (beforeSend hooks, data scrubber settings, disabled default PII capture) given the product's data sensitivity.
- [ ] Check edge/server function error handling: are failures logged with context, and does any alert rule or notification hook fire on error spikes.
- [ ] Ask the evidence question at level 3+: find the artifact proving someone looks (triage notes, linked issues referencing report IDs, an alert channel).

### Monitoring signals

- Each surface's dependency manifest contains an error-reporting SDK or the docs name its monitored substitute (auditable roster)
- SDK config contains a scrubbing hook (grep for beforeSend or the platform equivalent) and disables default PII capture
- An alert rule on error rate exists in the monitoring config, checked into the repo or documented
- A pipeline verification note exists: a deliberate test error was seen at the destination after the last config change

### References

- [OWASP Application Security Verification Standard 4.0.3 — V7.1 Log Content, V7.2 Log Processing](https://owasp.org/www-project-application-security-verification-standard/)
- [NIST SP 800-92: Guide to Computer Security Log Management](https://csrc.nist.gov/pubs/sp/800/92/final)
- [Apple App Review Guidelines — Guideline 2.1 App Completeness](https://developer.apple.com/app-store/review/guidelines/)
- [Android vitals](https://developer.android.com/topic/performance/vitals)

### Typical remediation

Adopt one error-reporting destination and wire all client surfaces plus server/edge functions to it, with PII scrubbing configured before first deploy; add an error-rate alert and a written triage cadence; fire one deliberate test error per surface to prove the loop closes.

*Issue skeleton:* [`templates/rel-08.md`](../templates/rel-08.md)
