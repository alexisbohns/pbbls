<!-- Kritik issue skeleton for SEC-08 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, api, auth + the surface label + milestone. -->

Title: [Quality] SEC-08 endpoint hardening at level {observed_level} on {surface} (target {target_level})

## Quality finding: SEC-08 Server endpoint and webhook hardening

**Surface:** {surface}
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{endpoints_with_missing_controls_and_paths}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}
- [ ] Add negative-path tests (401/403/413, bad signature)

### Acceptance criteria
- [ ] Every listed endpoint verifies its caller and caps its body
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-08 · Server endpoint and webhook hardening** (`api-hardening`) — see [criteria reference](../criteria/index.md)._
_Question: Are server-side endpoints (edge functions, API routes, inbound webhooks) hardened with caller verification, strict CORS, request size caps, rate limiting on expensive paths, and signature verification on webhooks?_
_References: [OWASP ASVS v4.0.3 — V13 API and Web Service](https://owasp.org/www-project-application-security-verification-standard/) · [OWASP API Security Top 10 (2023) — API4:2023 Unrestricted Resource Consumption](https://owasp.org/API-Security/editions/2023/en/0x11-t10/) · [CWE-347 Improper Verification of Cryptographic Signature](https://cwe.mitre.org/data/definitions/347.html) · [CWE-770 Allocation of Resources Without Limits or Throttling](https://cwe.mitre.org/data/definitions/770.html)_