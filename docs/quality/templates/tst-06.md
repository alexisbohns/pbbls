<!-- Kritik issue skeleton for TST-06 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, test, core + the surface label + milestone. -->

Title: [Quality] TST-06: close CI gate hole for {surface_or_path}

**Criterion:** TST-06 No merge without the touched surfaces' gates
**Surface:** {surface}
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets} (e.g. changing {example_file} triggers zero test jobs)

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] Mark {job_name} as a required status check

### Acceptance criteria
- A PR touching {example_file} shows the expected checks running
- Branch protection lists the checks as required

---
_Criterion: **TST-06 · No merge without the touched surfaces' gates** (`ci-gates`) — see [criteria reference](../criteria/index.md)._
_Question: Can any change reach the release branch without the tests and lint of every surface it touches having run and passed?_
_References: [GitHub Docs, About protected branches — Require status checks before merging](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches) · [Martin Fowler, Continuous Integration — Self-Testing Build](https://martinfowler.com/articles/continuousIntegration.html)_