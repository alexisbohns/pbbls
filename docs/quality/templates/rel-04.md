<!-- Kritik issue skeleton for REL-04 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, {surface} + the surface label + milestone. -->

Title: [Quality] REL-04 {surface}: {mutation} lacks server-side idempotence guard

**Criterion:** REL-04 Idempotence and double-submit protection
**Surface:** {surface}
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{mutation_sites_and_missing_guards}

## Risk
{duplicate_or_race_scenario}

## Remediation
- [ ] {add_constraint_or_conflict_clause_or_state_check}
- [ ] {client_pending_guard_if_missing}
- [ ] {replay_twice_harness_assertion}

## Acceptance criteria
- [ ] Calling {mutation} twice produces exactly one observable effect
- [ ] The guard lives server-side, not only in the UI
- [ ] The concurrency policy for this record type is documented

---
_Criterion: **REL-04 · Idempotence and double-submit protection** (`concurrency`) — see [criteria reference](../criteria/index.md)._
_Question: Are duplicate submissions, retried requests, and concurrent writes prevented from creating duplicate records or corrupted state, by server-side guards rather than UI discipline alone?_
_References: [CWE-362: Concurrent Execution using Shared Resource with Improper Synchronization ('Race Condition') — CWE-362](https://cwe.mitre.org/data/definitions/362.html) · [Amazon Builders' Library: Making retries safe with idempotent APIs](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/) · [PostgreSQL Documentation — INSERT: ON CONFLICT Clause](https://www.postgresql.org/docs/current/sql-insert.html)_