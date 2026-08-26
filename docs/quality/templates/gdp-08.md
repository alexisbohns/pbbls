<!-- Kritik issue skeleton for GDP-08 (GDPR & Regulatory) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, db, {surface} + the surface label + milestone. -->

Title: [Quality] GDP-08 breach readiness: {defect_summary} ({surface})

## Criterion
GDP-08 Breach detection and response readiness (GDP / breach)

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
_Criterion: **GDP-08 · Breach detection and response readiness** (`breach`) — see [criteria reference](../criteria/index.md)._
_Question: Could the operator detect, scope, and notify a personal-data breach within regulatory timelines using logging, tooling, and a documented runbook that exist today?_
_References: [Regulation (EU) 2016/679 (GDPR) — Art. 33, Art. 34](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · Article 29 Working Party Guidelines on Personal data breach notification under Regulation 2016/679, endorsed by the EDPB — WP250 rev.01 · NIST SP 800-61, Computer Security Incident Handling Guide_