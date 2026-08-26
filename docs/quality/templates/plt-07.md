<!-- Kritik issue skeleton for PLT-07 (Platform & Store Compliance) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, api, web, admin + the surface label + milestone. -->

Title: [Quality] PLT-07 deploy hardening gap on {surface}: {gap_summary}

## Criterion
PLT-07 Hosting platform hardening (deploy-platform)

## Observed level
{observed_level}/4 on `{surface}` (target: {target_level}/4)

## Evidence
{evidence_bullets}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5 = {severity}. Missing headers and unprotected operator/preview deployments create direct exposure paths to sensitive data.

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criteria}

---
_Criterion: **PLT-07 · Hosting platform hardening: headers and deployment protection** (`deploy-platform`) — see [criteria reference](../criteria/index.md)._
_Question: Do the deployed web properties ship a deliberate security-header set via tracked config, and are operator-facing apps and preview deployments protected at the platform level beyond application code alone?_
_References: [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/) · [OWASP Application Security Verification Standard — V14 Configuration](https://owasp.org/www-project-application-security-verification-standard/) · [MDN: Content Security Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP)_