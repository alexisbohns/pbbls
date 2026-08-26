<!-- Kritik issue skeleton for GDP-07 (GDPR & Regulatory) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, db, {surface} + the surface label + milestone. -->

Title: [Quality] GDP-07 retention: {defect_summary} ({surface})

## Criterion
GDP-07 Enforced retention schedules (GDP / retention)

## Observed level
{observed_level}/4 on `{surface}` (target: {target_level}/4)

## Evidence
{evidence_paths_and_grep_output}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5. {risk_narrative}

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criterion_1}
- {acceptance_criterion_2}

---
_Criterion: **GDP-07 · Enforced retention schedules** (`retention`) — see [criteria reference](../criteria/index.md)._
_Question: Does every category of personal data have a stated lifetime that something actually enforces, on the server and in client-side stores?_
_References: [Regulation (EU) 2016/679 (GDPR) — Art. 5(1)(e) (storage limitation)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · CNIL practical guide on retention periods (Les durees de conservation) · [OWASP Application Security Verification Standard — V8 (Data Protection)](https://owasp.org/www-project-application-security-verification-standard/)_