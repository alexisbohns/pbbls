<!-- Kritik issue skeleton for PRV-07 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, db + the surface label + milestone. -->

Title: [Quality] PRV-07 {surface}: cross-user exposure beyond intended field set ({finding_summary})

**Criterion**: PRV-07 Cross-user exposure: field-set adequacy and minimality (PRV/exposure-surfaces)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{exposure_path_and_overexposed_fields_with_migration_refs}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {remove_unneeded_or_linkable_fields_from_projection}
- [ ] {replace_field_with_least_revealing_form}
- [ ] {add_or_extend_cross_user_verify_harness}

### Acceptance criteria
- The exposure path returns exactly the documented field set for each visibility grade
- Every remaining field has a recorded consuming feature
- Harness passes from stranger and connection sessions
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-07 · Cross-user exposure: field-set adequacy and minimality** (`exposure-surfaces`) — see [criteria reference](../criteria/index.md)._
_Question: Is the field set each cross-user projection exposes minimal and adequate: every allowlisted field needed by the consuming feature, internal identifiers and linkable attributes excluded, and each visibility grade's exposed set enumerated and justified?_
_References: [GDPR — Art. 25(2) (data protection by default: accessibility limitation)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [OWASP Top 10:2021 — A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/) · [CWE-200: Exposure of Sensitive Information to an Unauthorized Actor — CWE-200](https://cwe.mitre.org/data/definitions/200.html) · [Supabase Docs: Row Level Security — Postgres Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)_