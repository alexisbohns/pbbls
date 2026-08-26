# TST — Testing & Verification

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Core-path coverage, cross-surface contract tests, regression protection, harnesses, test quality, CI gates, negative authorization proofs.

---

## TST-01 · Core user paths have automated tests

**Is every core user path on this surface exercised by at least one automated test that would fail if the path broke?**

`core-coverage` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

The surface maintains an identifiable set of core user paths (create/edit/delete of the primary entity, auth and session handling, sharing or visibility changes, purchase or entitlement flows) and each path's underlying logic is covered by automated tests asserting observable behavior. Coverage is judged per path, not per line: a path counts as covered only when a test exists that fails if that path's behavior regresses. New features add coverage for their path in the same change.

*Why it matters:* In a multi-client product on one shared database, an untested core path breaks silently for a whole platform's user base and often stays broken until a user reports it. Path-level coverage is the cheapest early-warning system for the flows users actually depend on.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No test files exist for the surface, or the suite that exists touches none of the core paths (only utility or formatting helpers are tested). |
| **1 · Ad-hoc** | A handful of tests exist for whatever modules past bugs happened to touch; no list of core paths exists anywhere and most primary-entity CRUD, auth, or visibility logic has no test. |
| **2 · Defined** | Core paths are named somewhere observable (docs, a plan, the test directory structure) and most have at least one behavior test, but at least one named core path (commonly auth/session or the destructive delete path) has none and the gap is not tracked. |
| **3 · Managed** | Every named core path has behavior-level tests, tests land in the same change as the feature they cover, and remaining gaps are tracked as issues. |
| **4 · Verified** | A CI job runs the surface's suite on every PR touching it, and an automated check makes an untested new core path visible (a coverage ratchet, a path-inventory file validated in CI, or a required check tied to the surface). |

### Audit checklist

- [ ] Enumerate test files per surface: Glob '**/*.test.ts' under the Next.js apps, list the iOS test target directory (e.g. *Tests/), list app/src/test under the Android module; count zero means l0 for that surface.
- [ ] Build the path inventory: read the route tree (Next.js app/ directory), the iOS/Android screen list, or a product map bundle if the repo keeps one (e.g. docs/**/bundle.json), and write down the create/edit/delete, auth, sharing/visibility, and purchase flows.
- [ ] For each inventoried path, trace to the module implementing its logic (hook, service, RPC) and check a test file imports that module and asserts its output or resulting state, not merely that it runs.
- [ ] On the database surface, map each security definer RPC and RLS-sensitive table to a test or harness that exercises it as a non-owner and as the owner.
- [ ] Cross-check recency: git log the core-path modules for the last few feature commits and verify tests changed in the same commits.

### Monitoring signals

- Per-surface test file count > 0 (Glob '**/*.test.ts', '*Tests/*.swift', 'src/test/**/*.kt' returns matches)
- CI workflow exists per surface that runs its test command (grep .github/workflows for the workspace test invocation)
- grep -ri 'delete\|purge\|signIn\|session\|visibility' across test files returns hits on each client surface

### References

- [Martin Fowler, TestPyramid — TestPyramid](https://martinfowler.com/bliki/TestPyramid.html)
- [Ham Vocke, The Practical Test Pyramid — Unit tests / Integration tests sections](https://martinfowler.com/articles/practical-test-pyramid.html)
- ISO/IEC 25010 Product quality model — Functional correctness

### Typical remediation

Write the core-path inventory first (one page), then add behavior tests for the highest-impact uncovered paths (destructive delete, auth/session, visibility) before broadening. Wire the suite into a per-surface CI job so coverage cannot silently rot.

*Issue skeleton:* [`templates/tst-01.md`](../templates/tst-01.md)

---

## TST-02 · Shared shapes tested against real cross-surface payloads

**Is every data shape that crosses a surface boundary tested against verbatim payloads produced by the other surfaces, including precision variants and explicit nulls?**

`contract-tests` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Every serialized shape shared through the common database or API (timestamps, numeric precision, optional fields, enums) has decoder and encoder tests whose fixtures are captured verbatim from the other producing surfaces, not synthesized by the same surface's own encoder. Fixtures cover precision variants (fractional vs whole-second timestamps, timezone offset forms, int vs float) and the distinction between an explicit null and an absent key. A same-surface round-trip is not accepted as contract evidence, because it cannot catch a formatter bug shared by both directions.

*Why it matters:* When several independently built clients share one database, the encoded bytes are the real contract; each platform's default date and JSON strategies differ, so same-surface round-trips pass while cross-surface decoding fails in production. Real captured payloads are the only oracle that detects this class of bug before users do.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Decoders and encoders of shared shapes have no tests, or every test feeds the decoder output from the same surface's own encoder (pure round-trips). |
| **1 · Ad-hoc** | A few decoding tests use literal JSON, but the fixtures were hand-written from memory of the schema; no fixture is attributed to a producing surface and no precision or null variants exist. |
| **2 · Defined** | Some shapes have fixtures explicitly labelled as captured from another surface, with timestamp precision or explicit-null variants for those shapes; other cross-boundary shapes still rely on round-trips or nothing. |
| **3 · Managed** | Every shape crossing the boundary has verbatim fixtures from each producing surface, covering precision variants and explicit-null vs absent-key cases; adding a shared field extends the fixtures in the same change, and the rule is written down where agents and reviewers see it. |
| **4 · Verified** | Fixture freshness is mechanized: a capture script or harness regenerates fixtures from real surface output, and CI fails when the shared schema or generated types change without the corresponding fixture and test update. |

### Audit checklist

- [ ] Locate decoder/encoder tests per surface: grep client test dirs for literal JSON fixtures (multiline string literals containing '{', loadFixture helpers, checked-in .json fixture files) and for Codable/serializer test names (e.g. *DecodingTests.swift, *EncodingTests.swift, *serialization*.test.ts).
- [ ] For each fixture, check its provenance: a comment or file header naming the producing surface and capture date. Unattributed fixtures count as hand-written.
- [ ] Verify variant coverage: search fixtures for both fractional-second and whole-second timestamps, both 'Z' and offset timezone forms, integer and float renderings of the same numeric field, and cases distinguishing "field": null from the key being absent.
- [ ] Confirm no decoder test builds its input by calling the same surface's encoder (read the test setup; flag round-trips presented as contract tests).
- [ ] Check emit-side tests: each client's encoder output is asserted against the narrowest form every reader accepts (e.g. whole-second timestamps), not against whatever the ambient date strategy produces.
- [ ] Check the generated DB types file is current: regenerate (e.g. the repo's db:types script) and confirm git diff is clean; a dirty diff means the contract drifted without tests noticing.

### Monitoring signals

- grep test fixtures for a provenance marker convention (e.g. 'captured from', 'verbatim', producing-surface name in fixture headers) returns hits for every shared shape
- grep -r '\.000Z\|\+00:00' across contract test fixtures returns both precision variants
- CI step that regenerates DB types and fails on git diff exists in a workflow

### References

- [Martin Fowler, ContractTest — ContractTest](https://martinfowler.com/bliki/ContractTest.html)
- [Pact documentation, consumer-driven contract testing — How Pact works](https://docs.pact.io)
- [Ham Vocke, The Practical Test Pyramid — Contract Tests](https://martinfowler.com/articles/practical-test-pyramid.html)

### Typical remediation

Capture one real payload per shape from each producing surface (log it from a dev build or query the database directly), check it in as an attributed fixture, and write decode tests over it plus precision and null variants. Then add the standing rule to contributor docs: a new shared field ships with fixture updates in the same change.

*Issue skeleton:* [`templates/tst-02.md`](../templates/tst-02.md)

---

## TST-03 · Fixed bugs leave pinning regression tests

**Does every bug fix land together with an automated test that fails on the pre-fix code?**

`regression` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **2/3**

Each bug fix includes, in the same change, a test that reproduces the reported failure and passes only with the fix applied. The test asserts the user-observable symptom (wrong output, crash, wrong state), references the bug it pins (issue number or a descriptive name), and runs in the surface's standard suite so the bug cannot silently return.

*Why it matters:* Bugs cluster: code that broke once sits in the part of the design most likely to break again under the next refactor. A pinning test converts each incident into permanent protection at near-zero marginal cost, and its absence is the single most common reason the same bug ships twice.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Fix commits never touch test files; reintroduced bugs are only caught by users. |
| **1 · Ad-hoc** | An occasional fix includes a test, with no visible convention; most recent fix commits modify only production code. |
| **2 · Defined** | A written convention requires regression tests for fixes, and most recent fix commits include one, but exceptions pass review without justification and some pinning tests assert the mechanism rather than the symptom. |
| **3 · Managed** | Sampling recent fix commits shows each one adds or extends a test that fails pre-fix; tests reference the bug they pin; exceptions are rare and justified in the PR. |
| **4 · Verified** | Automation flags the gap: a CI or bot check on fix-labelled PRs fails or warns when no test file changed, and the check's history shows it firing. |

### Audit checklist

- [ ] Sample the last 15 to 20 fix commits: git log --oneline --grep '^fix' (or the repo's fix label on merged PRs) and for each, git show --stat to see whether a test file changed in the same commit or PR.
- [ ] For a few sampled fixes, read the added test and confirm it asserts the reported symptom (the wrong value, the crash, the missing row), not an internal detail added by the fix.
- [ ] Spot-verify one pinning test genuinely pins: check out the fix commit's parent, run that single test, and confirm it fails.
- [ ] grep test files for issue references or bug-descriptive names (e.g. '#\d+', 'regression', a dated slug) to gauge traceability.
- [ ] Check PR templates, contributor docs, or CI for a stated or enforced regression-test requirement on fix PRs.

### Monitoring signals

- Ratio over the last 20 fix commits of commits touching at least one test file (target: > 0.8), computable from git log --grep '^fix' --stat
- grep -ri 'regression\|#[0-9]+' across test directories returns hits
- A CI or bot check exists that inspects fix-labelled PRs for test changes

### References

- ISTQB Certified Tester Foundation Level Syllabus — Confirmation testing and regression testing
- [Martin Fowler, SelfTestingCode — SelfTestingCode](https://martinfowler.com/bliki/SelfTestingCode.html)

### Typical remediation

Adopt the rule that a fix PR contains a test failing on the parent commit, add it to the PR checklist, and backfill pinning tests for the highest-impact recent bugs first (data loss, auth, cross-surface decode failures).

*Issue skeleton:* [`templates/tst-03.md`](../templates/tst-03.md)

---

## TST-04 · Runnable harnesses for destructive cross-cutting operations

**Do executable verification harnesses exist for destructive and cross-cutting operations (account purge, visibility changes, moderation takedown), exercising the real production path and co-evolving with the schema?**

`harnesses` · applies to: `supabase` `admin` · default impact **5/5** · weight **3/3**

Every operation that spans many tables or irreversibly destroys data (account deletion and erasure, ownership transfer or nulling, moderation takedown, data export) has a runnable harness that seeds a complete fixture graph covering every entity type the operation touches, invokes the real production code path (the deployed edge function or RPC, never a test-side re-implementation), and asserts the end state exhaustively: zero-row checks per table, storage prefixes empty, the auth record gone, and surviving counterpart data (e.g. a buyer's purchased item) intact. A standing co-evolution rule ties schema growth to harness growth: any new table reached by the operation gains its seed and its assertion in the same change, and the harness runs against a real environment after any change to the operation.

*Why it matters:* Multi-table destructive operations are exactly where unit tests lie: each statement can be correct while the composition leaks rows. For a product holding intimate personal data, an account purge that leaves rows behind is a false erasure claim with direct legal exposure under the right to erasure, and only an exhaustive end-state harness can prove convergence to zero.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No harness exists; deletion or takedown correctness rests on reading the SQL and hoping. |
| **1 · Ad-hoc** | A manual SQL snippet or checklist in a doc describes how someone once verified the operation; nothing is runnable or repeatable. |
| **2 · Defined** | A runnable harness exists for at least one destructive operation but asserts only a subset of touched tables, seeds a partial fixture graph, skips storage or auth assertions, or calls raw deletes instead of the production path. |
| **3 · Managed** | Each destructive operation has a harness that seeds every touched entity type, runs the real edge function or RPC, asserts zero rows per table plus storage and auth state, verifies re-run convergence, and the co-evolution rule (new table means new seed plus new assertion) is written where contributors see it. |
| **4 · Verified** | The harness runs automatically (CI on migrations touching the operation, or a scheduled run against a real environment) and a mechanical drift check compares the tables named in the operation's function body against the tables the harness seeds and asserts, failing on mismatch. |

### Audit checklist

- [ ] Inventory harnesses: ls the database package's scripts directory for verify-* or smoke-* executables and read each header for what it seeds, invokes, and asserts.
- [ ] Read the purge/deletion function in the migrations (grep migrations for the purge or delete function name), list every table it touches, and diff that list against the tables the harness seeds and zero-row-asserts; any table in the function but not the harness is a finding.
- [ ] Confirm the harness invokes the deployed production path (the edge function or RPC by name) rather than issuing its own deletes, and that it asserts storage-object listings and the auth user record, not only database rows.
- [ ] Check for a re-run convergence assertion (running the operation twice ends at the same zero state) and for preservation assertions on counterpart data that must survive (transferred or nulled ownership).
- [ ] Check when the harness must run: grep contributor docs and CI workflows for the harness name; verify the co-evolution rule (new table means seed plus assertion in the same change) is stated in a doc agents load.
- [ ] For moderation/takedown operations in the back-office, trace the operation to its RPC and check whether any harness or test seeds content, runs the takedown, and asserts visibility end-state across user grades.

### Monitoring signals

- A verify-* script exists per destructive operation (ls packages/*/scripts/verify-*.ts non-empty)
- Diff between tables named in the purge function body and tables asserted in the harness is empty (scriptable: grep table names from both files and comm -3)
- grep CI workflows and contributor docs for the harness invocation returns at least one hit

### References

- [GDPR, Regulation (EU) 2016/679 — Art. 17 (right to erasure)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [GDPR, Regulation (EU) 2016/679 — Art. 5(2) (accountability)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Ham Vocke, The Practical Test Pyramid — End-to-End Tests](https://martinfowler.com/articles/practical-test-pyramid.html)

### Typical remediation

Write one harness per destructive operation following the pattern: seed a throwaway user graph covering every entity type, invoke the real production path, assert zero rows per table plus storage and auth, verify re-run convergence, clean up even on failure. Then codify the co-evolution rule in contributor docs and, if feasible, add a CI drift check between the function body's table list and the harness's assertion list.

*Issue skeleton:* [`templates/tst-04.md`](../templates/tst-04.md)

---

## TST-05 · Tests assert behavior with real oracles

**Do tests assert user-observable behavior against independent oracles, free of tautologies, implementation mirroring, and nondeterminism?**

`test-quality` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **2/3**

Tests state expected outputs and end states as literals or independently derived values, never by calling the code under test to generate its own expectation. Each test contains at least one meaningful assertion, asserts behavior (output, resulting state, emitted call) rather than internal structure, and is deterministic: time, timezone, locale, and randomness are injected or pinned. Focused, skipped, or permanently disabled tests do not linger in the tree unexplained.

*Why it matters:* A suite full of tautologies and mirrored implementations produces green builds while providing zero regression protection, which is worse than no suite because it manufactures false confidence. Nondeterministic tests train the team to ignore failures, destroying the suite's signal exactly when it matters.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Tests exist but a sample shows assertion-free tests, expect(true) style tautologies, or expectations computed by the code under test; failures would not indicate a real defect. |
| **1 · Ad-hoc** | Most tests assert something real, but a nontrivial share mirror the implementation (fixture generated by the same formatter being tested) or depend on wall-clock time, ambient locale, or unseeded randomness. |
| **2 · Defined** | Tests generally assert behavior with literal expectations; known flaky or mirrored tests exist and are informally known but not marked, tracked, or quarantined. |
| **3 · Managed** | A sample across the suite finds only behavior-level assertions with independent oracles; time, locale, and randomness are injected or pinned; skipped tests carry a reason and a tracking reference. |
| **4 · Verified** | Tooling enforces it: lint rules fail on assertion-free or focused tests (e.g. an expect-expect and no-focused-tests rule on the JS surfaces, equivalent conventions linted on mobile), golden/snapshot inputs are pinned, and flaky tests are automatically detected or quarantined with tracking. |

### Audit checklist

- [ ] grep test directories for tautologies and dead switches: 'expect(true)', 'assertTrue(true)', '.only(', '.skip(', '@Disabled', 'XCTSkip', 'withKnownIssue', and check each hit has a justification or tracking reference.
- [ ] Sample 10 tests per surface and check each expectation's provenance: is it a literal or independently derived value, or is it produced by calling the function under test (self-oracle)?
- [ ] grep tests for ambient nondeterminism: 'Date()', 'Date.now', 'TimeZone.current', 'Locale.current', 'System.currentTimeMillis', 'Math.random', 'Int.random' and verify each is injected, frozen, or seeded in the test setup.
- [ ] For golden and snapshot tests, confirm inputs and seeds are pinned in the fixture and the golden file is human-reviewed on change (look for regenerate scripts and PR diffs of golden files).
- [ ] Check the lint config on JS surfaces for test-quality rules (e.g. vitest or jest plugin rules like expect-expect and no-focused-tests) and whether the test suite fails or merely warns on them.

### Monitoring signals

- grep -rn 'expect(true)\|assertTrue(true)\|\.only(' across test dirs returns nothing
- grep -rn '\.skip(\|@Disabled\|XCTSkip' hits all carry an adjacent comment or issue reference
- Lint config enables an expect-expect equivalent rule at error severity

### References

- [Google Testing Blog, Testing on the Toilet: Test Behavior, Not Implementation — Test Behavior, Not Implementation](https://testing.googleblog.com/2013/08/testing-on-toilet-test-behavior-not.html)
- [Gerard Meszaros, xUnit Test Patterns — Test smells (Assertion Roulette, Fragile Test)](http://xunitpatterns.com)

### Typical remediation

Delete or fix tautological tests, replace self-oracle expectations with literals captured once and reviewed, inject clock/locale/randomness at the seams, and turn on the test-lint rules so the classes of smell you just cleaned cannot re-enter.

*Issue skeleton:* [`templates/tst-05.md`](../templates/tst-05.md)

---

## TST-06 · No merge without the touched surfaces' gates

**Can any change reach the release branch without the tests and lint of every surface it touches having run and passed?**

`ci-gates` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Every surface has a CI job running its lint and test suite, triggered on pull requests by path filters that include the surface's own files, the shared packages it depends on, and the workflow file itself. The jobs that matter are required status checks on the release branch, and deploy pipelines (hosting platform builds, store uploads) ship only from commits whose gates ran. The observable failure mode this criterion excludes is green-by-absence: a change that triggers zero checks and merges clean.

*Why it matters:* In a monorepo with independently deployed surfaces, path-filtered CI is efficient but every filter hole is a category of change that ships untested, and a shared-contract change (schema, generated types) that triggers only one surface's suite defeats the point of having four clients on one contract. Gates that exist but are not required protect nothing.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No CI workflow runs any tests; merges are gated only by human review, if that. |
| **1 · Ad-hoc** | CI exists for one surface; changes to the other surfaces merge with no checks executed (green by absence). |
| **2 · Defined** | Most surfaces have CI jobs, but the jobs are not required checks, or path filters have identifiable holes (a shared package or the schema directory triggers no dependent surface's suite, or the workflow file is not in its own filter). |
| **3 · Managed** | Every surface's lint and tests run on PRs touching it, the relevant checks are required on the release branch, path filters include shared dependencies and the workflow files, and deploys build from the gated branch only. |
| **4 · Verified** | The gate configuration itself is verified: branch protection is managed as code or audited on a schedule, a change to the shared contract (migrations or generated types) provably triggers every dependent surface's suite, and no file in the repo can change without at least one check running. |

### Audit checklist

- [ ] ls .github/workflows and map each surface to the workflow that runs its lint and tests; any surface with no mapping is green-by-absence (note that mobile builds via hosted CI configs like Xcode Cloud ci_scripts count if they run tests on PRs).
- [ ] Read each workflow's on.pull_request.paths and check three inclusions: the surface's own directory, every shared package it imports (schema package, shared types), and the workflow file itself.
- [ ] Query branch protection on the release branch (gh api repos/{owner}/{repo}/branches/{default}/protection) and compare required_status_checks against the job names found; a job that exists but is not required is a finding at l2.
- [ ] Trace the deploy paths: hosting config (vercel.json, project root settings) and release workflows; confirm production deploys come from the protected branch, not from arbitrary refs.
- [ ] Adversarial probe: name three concrete files (a migration, a shared type, a client screen) and determine from the filters which jobs each would trigger; any file whose change triggers zero test jobs is a hole to record.

### Monitoring signals

- Every surface's package name or directory appears in at least one workflow's test-running job (grep .github/workflows for each workspace path)
- gh api branch protection shows required_status_checks non-empty and matching current job names
- The schema/migrations path appears in the paths filter of every client surface's test workflow (grep workflows for the migrations directory)

### References

- [GitHub Docs, About protected branches — Require status checks before merging](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [Martin Fowler, Continuous Integration — Self-Testing Build](https://martinfowler.com/articles/continuousIntegration.html)

### Typical remediation

Add a lint-plus-test workflow per missing surface, widen path filters to cover shared packages and the workflow files themselves, then mark the jobs required in branch protection. Close the shared-contract hole first: schema and generated-type changes must trigger every client suite.

*Issue skeleton:* [`templates/tst-06.md`](../templates/tst-06.md)

---

## TST-07 · One canonical test framework and idiom per surface

**Does each surface use a single designated, current test framework and the platform's supported idioms, with the whole suite runnable by the documented workspace command?**

`platform-idioms` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **1/3**

Each surface designates one test framework (e.g. Vitest for the Next.js apps, Swift Testing rather than legacy XCTest for the SwiftUI app, JUnit plus the platform's preview/screenshot mechanism for Compose) and applies it consistently; the designation is written down, and the full suite is discovered and run by the surface's single documented test command. UI-level visual checks use the platform-supported mechanism (Compose preview screenshot testing, the chosen SwiftUI snapshot approach) rather than ad-hoc scripts, and no test exists that only runs when invoked by hand.

*Why it matters:* Mixed or deprecated frameworks fragment the suite: some tests stop being run by the standard command, contributors copy whichever idiom they see first, and migration debt compounds. A single current idiom per surface keeps every test discoverable by CI and every future contributor writing tests that actually execute.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No test framework is configured, or existing tests cannot be run by any documented command. |
| **1 · Ad-hoc** | Frameworks are mixed with no stated designation (e.g. XCTest and Swift Testing cases side by side, or scripts run ad hoc outside the runner), and some tests only run when invoked by hand. |
| **2 · Defined** | A designated framework per surface is written down and dominant, but stragglers on the old idiom remain and it is not verified that the documented command discovers every test file. |
| **3 · Managed** | One framework per surface, all test files discovered and run by the single documented workspace command, UI/screenshot idiom in place where visuals matter, and the designation stated in contributor docs. |
| **4 · Verified** | The idiom is enforced mechanically: a lint or CI check fails on the deprecated framework's imports or on test files outside the runner's discovery globs, and screenshot diffs gate PRs where the surface uses them. |

### Audit checklist

- [ ] grep the iOS test target for 'import XCTest' vs 'import Testing'; any XCTest hits on a surface designating Swift Testing are stragglers to list.
- [ ] Read each workspace's package.json test script (and the Gradle test task, xcodebuild test invocation) and confirm it is the single entry point contributor docs name; flag any test file reachable only by a different command.
- [ ] Compare discovery to reality: count test files on disk per surface against the number of files the runner reports executing (runner output or its include globs in vitest.config, Gradle sourceSets, the Xcode test plan).
- [ ] Check the Android module's build.gradle.kts for the screenshot/preview testing setup where the surface claims visual coverage, and check golden images are committed and diffed in PRs.
- [ ] Check lint configs for enforcement hooks: a rule or CI grep failing on the deprecated framework import, focused tests, or test files outside discovery globs.

### Monitoring signals

- grep -rn 'import XCTest' in the iOS test target returns nothing (when Swift Testing is the designation)
- Test-file count on disk equals the count executed by the documented command (scriptable comparison of Glob results vs runner summary)
- Each workspace package.json defines exactly one test script and CI invokes that same script

### References

- [Apple Developer Documentation, Swift Testing — Migrating a test from XCTest](https://developer.apple.com/documentation/testing)
- [Android Developers, Test your Compose layout — Testing in Compose](https://developer.android.com/develop/ui/compose/testing)
- [Android Developers, Compose Preview Screenshot Testing — Compose Preview Screenshot Testing](https://developer.android.com/studio/preview/compose-screenshot-testing)
- [Vitest Guide — Getting Started](https://vitest.dev/guide/)

### Typical remediation

Write the per-surface framework designation into contributor docs, migrate stragglers to the designated framework (mechanical for most assertion styles), fix runner discovery globs so file count equals executed count, and add a grep-based CI check against the deprecated import.

*Issue skeleton:* [`templates/tst-07.md`](../templates/tst-07.md)

---

## TST-08 · Negative authorization tests in CI

**Does automated proof exist, kept current in CI, that RLS policies and security-definer RPCs deny cross-user reads and writes on every application table and RPC?**

`authz-tests` · applies to: `supabase` · default impact **5/5** · weight **3/3**

Denial is proven, not inspected: automated tests authenticate as at least two distinct seeded users plus the anon role and assert that cross-user selects, inserts, updates, and deletes are denied on every application table, and that every security-definer RPC refuses to act on rows the caller does not own. Coverage is mechanically complete: the suite enumerates tables and definer functions from the schema, or a completeness check diffs the covered list against the schema, so a new table or RPC cannot ship without its denial tests. The suite runs as a CI gate on migration changes against a real database with the migrations applied. The existence and shape of the policies and ownership checks themselves is SEC-02 and SEC-03's concern; this criterion audits that their denial behavior is executed and asserted continuously.

*Why it matters:* Four clients share one database and RLS is the sole isolation boundary between users; a policy that reads correctly in a migration can still permit cross-user access after one refactor of a join or a definer function. Only executed denial assertions catch that regression class, and only schema-driven enumeration keeps the proof complete as the schema grows.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No test anywhere authenticates as a second user and asserts a denial; access-control correctness rests on reading policy SQL. |
| **1 · Ad-hoc** | A one-off script or manual checklist once verified denial on a few tables; nothing is runnable or repeatable today. |
| **2 · Defined** | Runnable denial tests exist for the sensitive tables or a few RPCs, but coverage is a hand-maintained list that drifts from the schema, and the tests run only when someone remembers. |
| **3 · Managed** | Every application table and security-definer RPC has denial tests (cross-user read, write, and anon access), the covered list is diffed against the schema so gaps are visible, and the suite runs on a documented cadence against a real database. |
| **4 · Verified** | The denial suite runs as a CI gate on migration changes, and coverage is derived from the schema (or a completeness check fails on any table or RPC without denial tests), so a new table or RPC cannot merge without proof that cross-user access is denied. |

### Audit checklist

- [ ] Inventory denial coverage: list application tables (grep migrations for 'create table') and definer functions (grep for 'security definer'); locate the denial suite (pgTAP files or verify-* scripts that authenticate as multiple users) and diff its covered set against both lists; any uncovered table or RPC is a finding.
- [ ] Read the denial assertions per table: cross-user select, insert, update, and delete must each be asserted denied (error or empty result), plus the anon role wherever the table is not intentionally public; asserting reads only is a partial pass.
- [ ] For each security-definer RPC, confirm a test calls it as a non-owner and asserts refusal (exception or absence-shaped result), including RPCs whose ownership check is buried in a where clause.
- [ ] Check the anti-drift mechanism: either the suite enumerates tables and definer functions from the schema at runtime, or a completeness check exits nonzero when the schema list and the covered list diverge.
- [ ] Confirm the suite runs in CI on migration changes (grep .github/workflows for the suite invocation with a paths filter on the migrations directory) and that its target is a real Postgres with the migrations applied, not mocks.
- [ ] Prove the suite can fail: temporarily widen one policy locally (or run the suite's documented mutation mode) and verify the corresponding denial assertion trips.

### Monitoring signals

- The denial suite seeds two users plus anon and asserts cross-user reads and writes fail per table (grep the suite for the second-user session setup).
- A completeness check diffs schema tables and definer functions against the covered list and exits nonzero on drift.
- A CI workflow runs the denial suite with a paths filter matching the migrations directory.

### References

- [Supabase Docs: Testing your database](https://supabase.com/docs/guides/database/testing)
- [pgTAP: Unit testing for PostgreSQL](https://pgtap.org/)
- [OWASP Top 10:2021 — A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)
- [CWE-862 Missing Authorization](https://cwe.mitre.org/data/definitions/862.html)

### Typical remediation

Stand up a denial suite against the local database: seed two users, iterate every application table asserting cross-user and anon access is denied, call each definer RPC as a non-owner and assert refusal, add a schema-diff completeness check, and wire the suite into CI with a paths filter on migrations so new tables and RPCs cannot ship untested.

*Issue skeleton:* [`templates/tst-08.md`](../templates/tst-08.md)
