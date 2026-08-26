<!-- Kritik issue skeleton for TST-05 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, test + the surface label + milestone. -->

Title: [Quality] TST-05: fix low-signal tests in {module_or_dir} on {surface}

**Criterion:** TST-05 Tests assert behavior with real oracles
**Surface:** {surface}
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets} (file:line per smell)

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] Enable lint rules preventing recurrence

### Acceptance criteria
- Listed tests assert behavior against independent oracles
- Suite passes with time/locale/randomness pinned

---
_Criterion: **TST-05 · Tests assert behavior with real oracles** (`test-quality`) — see [criteria reference](../criteria/index.md)._
_Question: Do tests assert user-observable behavior against independent oracles, free of tautologies, implementation mirroring, and nondeterminism?_
_References: [Google Testing Blog, Testing on the Toilet: Test Behavior, Not Implementation — Test Behavior, Not Implementation](https://testing.googleblog.com/2013/08/testing-on-toilet-test-behavior-not.html) · [Gerard Meszaros, xUnit Test Patterns — Test smells (Assertion Roulette, Fragile Test)](http://xunitpatterns.com)_