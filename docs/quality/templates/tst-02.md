<!-- Kritik issue skeleton for TST-02 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, test, db, core + the surface label + milestone. -->

Title: [Quality] TST-02: contract-test {shape_name} against real {producing_surface} payloads on {surface}

**Criterion:** TST-02 Shared shapes tested against real cross-surface payloads
**Surface:** {surface}
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets}

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative}

### Remediation
- [ ] Capture verbatim {shape_name} payloads from {producing_surface}
- [ ] Add decode tests covering precision variants and explicit nulls
- [ ] {remediation_step_3}

### Acceptance criteria
- Fixtures are attributed to their producing surface and date
- A same-surface round-trip is not the only evidence for {shape_name}

---
_Criterion: **TST-02 · Shared shapes tested against real cross-surface payloads** (`contract-tests`) — see [criteria reference](../criteria/index.md)._
_Question: Is every data shape that crosses a surface boundary tested against verbatim payloads produced by the other surfaces, including precision variants and explicit nulls?_
_References: [Martin Fowler, ContractTest — ContractTest](https://martinfowler.com/bliki/ContractTest.html) · [Pact documentation, consumer-driven contract testing — How Pact works](https://docs.pact.io) · [Ham Vocke, The Practical Test Pyramid — Contract Tests](https://martinfowler.com/articles/practical-test-pyramid.html)_