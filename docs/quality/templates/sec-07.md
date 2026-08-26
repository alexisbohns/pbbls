<!-- Kritik issue skeleton for SEC-07 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, facility + the surface label + milestone. -->

Title: [Quality] SEC-07 supply-chain hygiene at level {observed_level} on {surface} (target {target_level})

## Quality finding: SEC-07 Dependency and build pipeline integrity

**Surface:** {surface}
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{missing_lockfiles_unpinned_actions_with_paths}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

### Acceptance criteria
- [ ] All ecosystems lockfile-covered; alerts configured; actions pinned
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-07 · Dependency and build pipeline integrity** (`supply-chain`) — see [criteria reference](../criteria/index.md)._
_Question: Are dependencies pinned via lockfiles and monitored for known vulnerabilities, and is the build pipeline tamper-resistant (pinned actions, no untrusted PR code executing with secrets in scope)?_
_References: [OWASP Top 10:2021 — A06 Vulnerable and Outdated Components](https://owasp.org/Top10/) · [OWASP Top 10:2021 — A08 Software and Data Integrity Failures](https://owasp.org/Top10/) · [GitHub Docs: Security hardening for GitHub Actions — Using third-party actions](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions) · [CWE-1104 Use of Unmaintained Third Party Components](https://cwe.mitre.org/data/definitions/1104.html)_