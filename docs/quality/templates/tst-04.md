<!-- Kritik issue skeleton for TST-04 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, test, db, legal + the surface label + milestone. -->

Title: [Quality] TST-04: harness for {operation_name} missing {gap_summary}

**Criterion:** TST-04 Runnable harnesses for destructive cross-cutting operations
**Surface:** {surface}
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets} (e.g. {table_name} is purged by {function_name} but never seeded or asserted in {harness_path})

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative} (false erasure claim on personal data is a legal exposure)

### Remediation
- [ ] Seed {table_name} in {harness_path} and assert zero rows post-purge
- [ ] {remediation_step_2}

### Acceptance criteria
- Harness run against the linked environment passes with the new assertions
- Every table in {function_name} appears in the harness seed and assertion lists

---
_Criterion: **TST-04 · Runnable harnesses for destructive cross-cutting operations** (`harnesses`) — see [criteria reference](../criteria/index.md)._
_Question: Do executable verification harnesses exist for destructive and cross-cutting operations (account purge, visibility changes, moderation takedown), exercising the real production path and co-evolving with the schema?_
_References: [GDPR, Regulation (EU) 2016/679 — Art. 17 (right to erasure)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [GDPR, Regulation (EU) 2016/679 — Art. 5(2) (accountability)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [Ham Vocke, The Practical Test Pyramid — End-to-End Tests](https://martinfowler.com/articles/practical-test-pyramid.html)_