<!-- Kritik issue skeleton for SEC-05 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, api + the surface label + milestone. -->

Title: [Quality] SEC-05 input validation at level {observed_level} on {surface} (target {target_level})

## Quality finding: SEC-05 Injection-safe input handling

**Surface:** {surface}
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{sinks_with_paths_and_example_payloads}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}
- [ ] Add hostile-input fixtures to tests

### Acceptance criteria
- [ ] Listed sinks parameterized/sanitized/validated
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-05 · Injection-safe input handling at trust boundaries** (`input-validation`) — see [criteria reference](../criteria/index.md)._
_Question: Is externally influenced input validated and safely encoded at every trust boundary: dynamic SQL, HTML rendering, deep links, uploaded files, and RPC payloads?_
_References: [CWE-89 SQL Injection](https://cwe.mitre.org/data/definitions/89.html) · [CWE-79 Cross-site Scripting](https://cwe.mitre.org/data/definitions/79.html) · [OWASP Top 10:2021 — A03 Injection](https://owasp.org/Top10/) · [OWASP ASVS v4.0.3 — V5 Validation, Sanitization and Encoding](https://owasp.org/www-project-application-security-verification-standard/) · [OWASP MASVS v2 — MASVS-PLATFORM](https://mas.owasp.org/MASVS/)_