<!-- Kritik issue skeleton for SAF-07 (Safety & Wellbeing) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, auth, db, supabase + the surface label + milestone. -->

Title: [Quality] SAF-07 takeover ceiling gap on {action}: {short_finding}

**Criterion:** SAF-07 Account takeover harm ceiling (SAF/takeover-ceiling)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **target:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{evidence_paths_and_snippets}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step}

### Acceptance criteria
- [ ] {action} requires recent re-authentication or server-verified confirmation
- [ ] Credential change revokes sessions and notifies the user
- [ ] {additional_acceptance}

---
_Criterion: **SAF-07 · Account takeover harm ceiling** (`takeover-ceiling`) — see [criteria reference](../criteria/index.md)._
_Question: Is the damage a stolen session or credential can do bounded: recent re-authentication on destructive and exposure-widening actions, global session revocation on credential change, security notifications, export friction, and MFA-protected audited operator access?_
_References: [OWASP Application Security Verification Standard 4.0 — V3 (Session Management) and V2 (Authentication)](https://github.com/OWASP/ASVS) · [NIST SP 800-63B, Digital Identity Guidelines: Authentication and Lifecycle Management — Section 7 (Session Management)](https://pages.nist.gov/800-63-3/sp800-63b.html) · [CWE-613: Insufficient Session Expiration — CWE-613](https://cwe.mitre.org/data/definitions/613.html)_