<!-- Kritik issue skeleton for PRV-02 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, core + the surface label + milestone. -->

Title: [Quality] PRV-02 {surface}: analytics collection without consent gate ({finding_summary})

**Criterion**: PRV-02 Analytics restraint and consent (PRV/telemetry)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{evidence_bullets_with_file_paths}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {gate_or_remove_offending_collection}
- [ ] {persist_consent_with_timestamp_and_version}
- [ ] {add_declined_path_test}

### Acceptance criteria
- No analytics call or event write occurs before consent or after decline on {surface}
- Consent record carries timestamp and policy version
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-02 · Analytics restraint and consent** (`telemetry`) — see [criteria reference](../criteria/index.md)._
_Question: Is behavioral analytics limited to named product questions, computed from first-party data where possible, and gated on a recorded user consent whenever it exceeds strict necessity?_
_References: [ePrivacy Directive 2002/58/EC — Art. 5(3) (storage of or access to information on terminal equipment)](https://eur-lex.europa.eu/eli/dir/2002/58/oj) · [GDPR — Art. 4(11) and Art. 7 (definition and conditions of consent)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · EDPB Guidelines 05/2020 on consent under Regulation 2016/679 — Guidelines 05/2020 · [Apple App Store Review Guidelines — Guideline 5.1.2 (Data Use and Sharing / App Tracking Transparency)](https://developer.apple.com/app-store/review/guidelines/)_