<!-- Kritik issue skeleton for AGT-07 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility, auth + the surface label + milestone. -->

Title: [Quality] AGT-07 automation safety: {short_finding} ({surface})

**Criterion:** AGT-07 Least privilege for agents and automation (AGT/automation-safety)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{over_scoped_credential_or_permission_with_path}

### Risk
{blast_radius_if_this_actor_is_compromised_or_errs}

### Remediation
- [ ] {move_secret_or_narrow_permission}
- [ ] {scope_workflow_or_allowlist}
- [ ] {write_dev_loop_ban_into_instructions}

### Acceptance criteria
- No privileged credential reachable from the routine dev loop
- Every workflow declares least-privilege token permissions

---
_Criterion: **AGT-07 · Least privilege for agents and automation** (`automation-safety`) — see [criteria reference](../criteria/index.md)._
_Question: Do coding agents, CI jobs, and bots operate with the narrowest credentials and permissions their task needs, with privileged credentials unreachable from the routine dev loop?_
_References: [OWASP Top 10 CI/CD Security Risks — CICD-SEC-6: Insufficient Credential Hygiene](https://owasp.org/www-project-top-10-ci-cd-security-risks/) · [GitHub Docs: Security hardening for GitHub Actions — Restricting permissions for tokens](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions) · [NIST SP 800-53 Rev. 5 Security and Privacy Controls — AC-6 Least Privilege](https://csrc.nist.gov/pubs/sp/800/53/r5/upd1/final)_