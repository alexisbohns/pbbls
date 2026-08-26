<!-- Kritik issue skeleton for REL-03 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, api, {surface} + the surface label + milestone. -->

Title: [Quality] REL-03 {surface}: non-atomic multi-step write in {flow} can strand partial state

**Criterion:** REL-03 Atomic multi-step writes
**Surface:** {surface}
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{stitched_write_sites_and_tables}

## Risk
{partial_state_scenario}

## Remediation
- [ ] {create_or_extend_transactional_rpc}
- [ ] {migrate_all_client_surfaces_to_it}
- [ ] {add_failure_injection_assertion_to_harness}

## Acceptance criteria
- [ ] The flow is a single server-side transaction with ownership checks
- [ ] Harness asserts zero partial state after induced mid-step failure
- [ ] No client surface still stitches this write

---
_Criterion: **REL-03 · Atomic multi-step writes** (`integrity`) — see [criteria reference](../criteria/index.md)._
_Question: Does every write that spans multiple tables or resources execute in a single server-side transaction, so that a partial failure can never leave the shared database inconsistent?_
_References: [PostgreSQL Documentation — Tutorial: Transactions](https://www.postgresql.org/docs/current/tutorial-transactions.html) · [Regulation (EU) 2016/679 (GDPR) — Art. 32(1)(b) integrity and resilience of processing systems](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · ISO/IEC 25010 Product quality model — Reliability: Recoverability_