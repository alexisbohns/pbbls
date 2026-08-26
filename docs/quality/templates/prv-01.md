<!-- Kritik issue skeleton for PRV-01 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, core + the surface label + milestone. -->

Title: [Quality] PRV-01 {surface}: undocumented or over-wide personal-data field(s) ({finding_summary})

**Criterion**: PRV-01 PII inventory and schema minimization (PRV/minimization)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{evidence_bullets_with_file_paths}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {add_or_update_inventory_entry}
- [ ] {narrow_offending_select_or_rpc}
- [ ] {add_ci_diff_between_types_and_inventory}

### Acceptance criteria
- Every personal-data field in {scope} appears in the inventory with a purpose
- No whole-row or select-star reads remain on personal-data tables in {scope}
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-01 · PII inventory and schema minimization** (`minimization`) — see [criteria reference](../criteria/index.md)._
_Question: Is every personal-data field that is collected, stored, or returned traceable to a documented purpose, with schemas and payloads carrying no more than that purpose needs?_
_References: [GDPR — Art. 5(1)(c) data minimisation; Art. 25 data protection by design and by default](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [GDPR — Art. 30 records of processing activities](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · EDPB Guidelines 4/2019 on Article 25 Data Protection by Design and by Default — Guidelines 4/2019, section on minimisation · [OWASP MASVS v2 — MASVS-PRIVACY-1 (minimize access to sensitive data)](https://mas.owasp.org/MASVS/)_