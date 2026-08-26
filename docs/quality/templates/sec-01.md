<!-- Kritik issue skeleton for SEC-01 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, auth, core + the surface label + milestone. -->

Title: [Quality] SEC-01 auth/session lifecycle at level {observed_level} on {surface} (target {target_level})

## Quality finding: SEC-01 Authentication and session lifecycle integrity

**Surface:** {surface}
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{evidence_bullets_with_file_paths}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}
- [ ] {remediation_step_3}

### Acceptance criteria
- [ ] {acceptance_criterion_1}
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-01 · Authentication and session lifecycle integrity** (`authn`) — see [criteria reference](../criteria/index.md)._
_Question: Do all authentication flows and session lifecycles (issuance, refresh, expiry, revocation) go through the vetted auth SDK and behave consistently on every client surface?_
_References: [OWASP ASVS v4.0.3 — V3 Session Management](https://owasp.org/www-project-application-security-verification-standard/) · [NIST SP 800-63B Digital Identity Guidelines — Sec. 7 Session Management](https://pages.nist.gov/800-63-3/sp800-63b.html) · [OWASP Top 10:2021 — A07 Identification and Authentication Failures](https://owasp.org/Top10/) · [OWASP MASVS v2 — MASVS-AUTH](https://mas.owasp.org/MASVS/)_