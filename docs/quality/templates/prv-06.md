<!-- Kritik issue skeleton for PRV-06 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, core + the surface label + milestone. -->

Title: [Quality] PRV-06 {surface}: unprotected or unclearable local data ({finding_summary})

**Criterion**: PRV-06 Local and offline data protection (PRV/local-data)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{stores_and_keys_found_with_paths}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {move_flagged_data_to_protected_storage}
- [ ] {wire_store_into_signout_and_deletion_clear}
- [ ] {set_backup_decision_and_add_emptiness_test}

### Acceptance criteria
- No content or secret persists outside protected storage on {surface}
- Sign-out and account deletion clear every inventoried store, proven by test
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-06 · Local and offline data protection** (`local-data`) — see [criteria reference](../criteria/index.md)._
_Question: Is personal data at rest on the device or in the browser limited to what the feature needs, held in platform-appropriate protected storage, and fully cleared on sign-out?_
_References: [OWASP MASVS v2 — MASVS-STORAGE-1 and MASVS-STORAGE-2](https://mas.owasp.org/MASVS/) · [Android Developers: App security best practices — Store data safely](https://developer.android.com/topic/security/best-practices) · [CWE-312: Cleartext Storage of Sensitive Information — CWE-312](https://cwe.mitre.org/data/definitions/312.html)_