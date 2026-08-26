<!-- Kritik issue skeleton for SEC-04 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, auth + the surface label + milestone. -->

Title: [Quality] SEC-04 secrets handling at level {observed_level} on {surface} (target {target_level})

## Quality finding: SEC-04 Secrets kept out of clients, source, and logs

**Surface:** {surface}
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{leak_locations_with_paths_do_not_paste_secret_values}

### Risk
{risk_narrative}

### Remediation
- [ ] Rotate {affected_credentials}
- [ ] {remediation_step_2}
- [ ] Add blocking secret scan to CI

### Acceptance criteria
- [ ] History scan clean or all findings rotated and recorded
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-04 · Secrets kept out of clients, source, and logs** (`secrets`) — see [criteria reference](../criteria/index.md)._
_Question: Are privileged credentials (service keys, signing keys, webhook secrets, CI tokens) absent from client bundles, source control, and logs, with only publishable keys shipped to clients?_
_References: [CWE-798 Use of Hard-coded Credentials](https://cwe.mitre.org/data/definitions/798.html) · [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html) · [GitHub Docs: Security hardening for GitHub Actions — Using secrets](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions) · [OWASP MASVS v2 — MASVS-STORAGE](https://mas.owasp.org/MASVS/)_