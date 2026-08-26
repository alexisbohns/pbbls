<!-- Kritik issue skeleton for PRV-04 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, core + the surface label + milestone. -->

Title: [Quality] PRV-04 {surface}: personal data reaches logs via {call_site}

**Criterion**: PRV-04 No personal data in logs and operator analytics (PRV/logs-hygiene)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{offending_log_lines_with_paths}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {replace_payload_logs_with_id_code_helper}
- [ ] {configure_and_test_reporter_scrubbing}
- [ ] {add_lint_rule_on_data_layer_paths}

### Acceptance criteria
- No log statement in {scope} serializes user content or credentials
- Operator analytics in {scope} is aggregate-only outside moderation flows
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-04 · No personal data in logs and operator analytics** (`logs-hygiene`) — see [criteria reference](../criteria/index.md)._
_Question: Are logs, error reports, and operator-facing analytics free of personal content by construction, carrying opaque identifiers, codes, and counts only?_
_References: [CWE-532: Insertion of Sensitive Information into Log File — CWE-532](https://cwe.mitre.org/data/definitions/532.html) · [OWASP ASVS 4.0.3 — V7.1 Log Content](https://owasp.org/www-project-application-security-verification-standard/) · [GDPR — Art. 32 (security of processing)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)_