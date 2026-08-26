<!-- Kritik issue skeleton for TST-03 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, test + the surface label + milestone. -->

Title: [Quality] TST-03: add regression test pinning {bug_summary} on {surface}

**Criterion:** TST-03 Fixed bugs leave pinning regression tests
**Surface:** {surface}
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets} (e.g. fix commits {commit_list} touched no test files)

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative}

### Remediation
- [ ] Write a test reproducing {bug_summary} that fails on {pre_fix_ref}
- [ ] {remediation_step_2}

### Acceptance criteria
- The test fails when the fix is reverted and passes on the current head
- The test runs in the surface's standard suite

---
_Criterion: **TST-03 · Fixed bugs leave pinning regression tests** (`regression`) — see [criteria reference](../criteria/index.md)._
_Question: Does every bug fix land together with an automated test that fails on the pre-fix code?_
_References: ISTQB Certified Tester Foundation Level Syllabus — Confirmation testing and regression testing · [Martin Fowler, SelfTestingCode — SelfTestingCode](https://martinfowler.com/bliki/SelfTestingCode.html)_