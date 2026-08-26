<!-- Kritik issue skeleton for TST-01 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, test, core + the surface label + milestone. -->

Title: [Quality] TST-01: cover core path {path_name} with tests on {surface}

**Criterion:** TST-01 Core user paths have automated tests
**Surface:** {surface}
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets}

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

### Acceptance criteria
- {path_name} has a test that fails when its behavior regresses
- Test runs in the surface's standard test command and CI job

---
_Criterion: **TST-01 · Core user paths have automated tests** (`core-coverage`) — see [criteria reference](../criteria/index.md)._
_Question: Is every core user path on this surface exercised by at least one automated test that would fail if the path broke?_
_References: [Martin Fowler, TestPyramid — TestPyramid](https://martinfowler.com/bliki/TestPyramid.html) · [Ham Vocke, The Practical Test Pyramid — Unit tests / Integration tests sections](https://martinfowler.com/articles/practical-test-pyramid.html) · ISO/IEC 25010 Product quality model — Functional correctness_