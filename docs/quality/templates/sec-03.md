<!-- Kritik issue skeleton for SEC-03 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, auth, api + the surface label + milestone. -->

Title: [Quality] SEC-03 definer RPC hygiene at level {observed_level} on {surface} (target {target_level})

## Quality finding: SEC-03 Security-definer RPC and privileged-role hygiene

**Surface:** {surface}
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{functions_or_routes_with_paths_and_missing_checks}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}
- [ ] Add wrong-user / wrong-role negative tests

### Acceptance criteria
- [ ] Every listed function pins search_path and checks caller identity or role
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-03 · Security-definer RPC and privileged-role hygiene** (`authz`) — see [criteria reference](../criteria/index.md)._
_Question: Does every security-definer function pin its search_path, verify caller ownership or role inside the function body, and expose cross-user data only through explicit column allowlists, with operator surfaces gated by server-verified roles?_
_References: [PostgreSQL Documentation: CREATE FUNCTION — Writing SECURITY DEFINER Functions Safely](https://www.postgresql.org/docs/current/sql-createfunction.html) · [OWASP ASVS v4.0.3 — V4 Access Control](https://owasp.org/www-project-application-security-verification-standard/) · [CWE-285 Improper Authorization](https://cwe.mitre.org/data/definitions/285.html) · [OWASP Top 10:2021 — A01 Broken Access Control](https://owasp.org/Top10/)_