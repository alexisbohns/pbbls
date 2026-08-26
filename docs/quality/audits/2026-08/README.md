# Kritik audit 2026-08 — Pebbles

**Snapshot:** commit `10181916` (origin/main). **Framework:** Kritik v0.1.0 ([`../../framework.md`](../../framework.md)). **Scope:** five surfaces (web, iOS, Android, admin, Supabase) x eleven domains, 88 criteria, 338 (criterion x surface) assessments, **246 findings**.

**Method:** 27 auditor cells (one per surface x domain-cluster) plus a cross-surface contract auditor and an AGT repo-wide pass, each scoring every applicable criterion 0-4 with `file:line` evidence, then emitting findings. Every Critical and High finding (41 in total) went through an independent adversarial refutation attempt before retention: **41/41 confirmed**, 0 refuted (several were severity-downgraded, which is why they now sit in Medium). Machine baseline (lint/tests/typecheck) collected first, in [`baseline.md`](./baseline.md). Line numbers reference the snapshot commit.

---

## The comparative matrix

Rows = domains, columns = surfaces. Cell = `score (grade)`, scored 0-100 from the maturity levels weighted per criterion. `\*` = grade capped by an open finding (an open Critical caps the cell at D, an open High at B), so the letter can be worse than the number. `—` = no criteria apply.

| Domain | web | ios | android | admin | supabase |
| --- | --- | --- | --- | --- | --- |
| **SEC** Security | 44 (D) | 50 (D) | 48 (D) | 48 (D) | 62 (D\*) |
| **PRV** Privacy & Data Protection | 56 (C) | 44 (D) | 46 (D) | 41 (D) | 63 (C) |
| **GDP** GDPR & Regulatory | 43 (D) | 43 (D) | 38 (E) | 47 (D) | 42 (D) |
| **SAF** Safety & Wellbeing | 32 (E) | 32 (E) | 32 (E) | 38 (E) | 50 (D) |
| **ARC** Code Quality & Architecture | 60 (C) | 67 (C) | 77 (B) | 60 (C) | 75 (B) |
| **TST** Testing & Verification | 52 (D) | 50 (D) | 59 (C) | 9 (E) | 49 (D) |
| **PLT** Platform & Store Compliance | 35 (E) | 42 (D) | 33 (E) | 30 (E) | 34 (E) |
| **A11Y** Accessibility & Inclusion | 46 (D) | 48 (D) | 52 (D) | 34 (E) | 40 (D) |
| **PRF** Performance & Efficiency | 37 (E) | 54 (D) | 54 (D) | 35 (E) | 50 (D) |
| **REL** Reliability & Observability | 45 (D) | 47 (D) | 47 (D) | 37 (E) | 33 (E) |
| **AGT** Agentic Development Readiness | 78 (B) | 63 (C) | 78 (B) | 74 (B) | 70 (B) |
| **Overall (weighted)** | **48 (D)** | **48 (D)** | **50 (D)** | **42 (D)** | **53 (D)** |

Read a cell only against others in its row: SEC-web vs SEC-ios is meaningful, SEC-web vs PRF-ios is not. Overall uses the Pebbles profile weights (SEC and PRV x2, GDP and SAF x1.5, rest x1), so a surface is graded hardest on exactly the dimensions where its users are most exposed.

Findings by severity: **1 Critical · 40 High · 174 Medium · 31 Low**.

## Executive summary

**The craft is high; the launch-hardening layer is missing.** Two things are true at once and the matrix shows both. The code is clean, strictly typed, well-layered, and unusually friendly to coding agents: **ARC** (architecture) and **AGT** (agent-readiness) are the two strongest domains, B-grade across most surfaces, and that is not flattery. The data boundary is real and lint-enforced, mutation logic lives once in the schema package, the docs an agent reads first are accurate, and the whole repo is navigable through an Arkaik product graph. A team that can build this can fix everything below.

What is missing is not bad code. It is **absent systems** that an intimate-data consumer product legally and ethically needs before real users arrive, and because all four clients share one database contract, each gap appears identically on every surface rather than as five separate bugs:

- **Consent and lawful basis do not exist as records.** Federated (Apple/Google) signups persist a NULL consent and nothing binds a user to their acceptance of terms (GDP-01, four surfaces). The Art. 9 explicit-consent gate the privacy policy promises for emotional data is not implemented anywhere (GDP-02, five surfaces). For a product whose core data is emotional states plus named third parties, this is the load-bearing compliance gap.
- **No age gating** exists on any signup path although the terms declare a 13+ minimum and promise a parental flow (SAF-06, four surfaces + the DB).
- **Emotional safety is inverted.** Recording a maximum-negative emotional state yields a karma reward and surfaces zero crisis resources (SAF-01), and achievements are conditioned on emotional disclosure (SAF-02). SAF is the weakest domain in the whole audit (E on every client).
- **The account-takeover harm ceiling is unbounded.** A stolen session can purge the account, rotate the password, and widen visibility with no re-authentication (SAF-07, five surfaces).
- **No user-generated-content reporting apparatus** exists anywhere, on any surface, although cross-user and anonymous content is rendered (PLT-04, five surfaces). This is also an App Store and Play removal risk.
- **Production failures reach no one** (REL-08, five surfaces): no crash or error reporting on any client. And a failed data load renders as the first-run empty state, which reads as data loss (REL-01).
- **Nothing is enforced by CI except on Android.** There is no web, iOS, admin, or Supabase CI gate: the passing test suites and the lint rules that encode the architecture never block a merge (TST-06, five surfaces). The repo's own number-one rule, testing shared shapes against real cross-surface payloads, is itself untested (TST-02, six surfaces). This is why so many otherwise-good criteria are stuck at level 2-3: the quality is real but "verified by absence," safe today because the bad thing has not been added yet, not because anything stops it.

**The one Critical** stands apart from those systemic gaps because it is exploitable now: `profiles.is_admin` is a client-writable column, so any authenticated user can self-grant admin and defeat every `is_admin()` gate in the schema (SEC-03, verified independently against the migrations). Fix it first; it is a one-line `WITH CHECK` / column-privilege change.

**Bottom line:** every surface lands at an overall D, clustered tightly (42-53). That is a fair verdict for a well-built product that has not yet done its pre-launch safety, privacy, compliance, and observability pass. The fixes are mostly small and mechanical (18 of the top findings are cost S); the reason they matter is that they are the exact things that turn "nice prototype" into "safe to put in front of people whose intimate data this is."

## Priority actions

Priority is severity x cost (framework §4.4). **P0 = this milestone.** Full list in [`findings.json`](./findings.json); per-surface detail in the reports linked below.

### P0 (7)

| Finding | Surface | Criterion | Cost | What |
| --- | --- | --- | --- | --- |
| 🔴 `F-2026-08-SEC-supabase-01` | supabase | SEC-03 | S | Client-writable `profiles.is_admin`: any user can self-grant admin |
| 🟠 `F-2026-08-TST-web-01` | web | TST-06 | S | No CI gate: the 125-test suite and boundary lint never block a merge |
| 🟠 `F-2026-08-REL-web-01` | web | REL-01 | S | Failed store load renders the first-run empty state (reads as data loss) |
| 🟠 `F-2026-08-GDP-ios-02` | ios | GDP-05 | S | No `PrivacyInfo.xcprivacy` privacy manifest ships |
| 🟠 `F-2026-08-PLT-ios-01` | ios | PLT-02 | S | Same missing manifest while required-reason APIs are used (review risk) |
| 🟠 `F-2026-08-GDP-admin-01` | admin | GDP-06 | S | Vercel (admin host) absent from processor inventory, no region pinned |
| 🟠 `F-2026-08-A11Y-android-01` | android | A11Y-05 | S | Google sign-in label illegible in dark theme (themed fg on fixed white) |

### The systemic High clusters (fix once, lands everywhere)

These are single decisions that clear a High on several surfaces at once, so they are the highest leverage after the Critical:

1. **Consent + lawful basis** (GDP-01, GDP-02): record consent at every signup path including federated; implement the Art. 9 gate. Clears Highs on web, iOS, Android, and the DB.
2. **Age assurance** (SAF-06): a signup age gate + a server-recorded age field. Clears Highs on all clients + the DB.
3. **Crisis + emotionally-safe design** (SAF-01, SAF-02): no reward on maximum-negative states, surface crisis resources, decouple karma from disclosure.
4. **UGC reporting + block enforcement** (PLT-04, SAF-03, SAF-04): a report primitive in the DB contract + a report affordance on each client. Also lifts the store-compliance risk.
5. **Production observability** (REL-08): a crash/error reporter on each client.
6. **CI gates** (TST-06): run each surface's existing lint/test on its PRs. This is what converts the many level-2/3 scores into durable level-4s.

## Per-surface reports

| Surface | Overall | Report |
| --- | --- | --- |
| Web (Next.js PWA) | 48 (D) | [`web.md`](./web.md) |
| iOS (SwiftUI) | 48 (D) | [`ios.md`](./ios.md) |
| Android (Kotlin/Compose) | 50 (D) | [`android.md`](./android.md) |
| Admin (back-office) | 42 (D) | [`admin.md`](./admin.md) |
| Supabase (database contract) | 53 (D) | [`supabase.md`](./supabase.md) |
| Cross-surface contract | — | [`cross-surface.md`](./cross-surface.md) |

## What is strong (so it does not get lost)

- **Architecture (ARC) and agent-readiness (AGT)** are B-grade: the data-layer boundary is real and lint-enforced on web, business logic sits in tested pure modules, the DB uses transactional security-definer RPCs for most multi-table writes, and the instruction docs an agent loads first are accurate and layered.
- **Privacy restraint on the clients (PRV-02)**: no behavioral-analytics SDK ships on web; fonts are self-hosted; user media sits in a private bucket behind short-lived signed URLs.
- **Android architecture (ARC 77) and the Supabase schema (ARC 75)** are the highest-scoring cells in the audit.

## Reproduce / extend

- Recompute the matrix from data: `node docs/quality/scripts/compute-matrix.mjs 2026-08`
- Regenerate criteria docs + issue skeletons: `node docs/quality/scripts/generate-projections.mjs`
- File an issue from a finding: use the per-criterion skeleton in [`../../templates/`](../../templates/) or the form at `.github/ISSUE_TEMPLATE/quality-finding.yml`.
- The framework and this audit are designed to graduate into Arkaik as a monitoring feature: see [`../../arkaik-integration.md`](../../arkaik-integration.md).

_This report is a snapshot and is immutable. The next audit is a new `audits/<YYYY-MM>/` folder, never an edit to this one._
