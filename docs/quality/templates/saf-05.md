<!-- Kritik issue skeleton for SAF-05 (Safety & Wellbeing) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, core, db + the surface label + milestone. -->

Title: [Quality] SAF-05 third-party identifier leak via {path}: {short_finding}

**Criterion:** SAF-05 Bystander exposure on outbound paths (SAF/bystander-privacy)
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
- [ ] Third-party identifiers absent from {path}, proven by a projection test
- [ ] A removal-request channel is documented and reachable
- [ ] {additional_acceptance}

---
_Criterion: **SAF-05 · Bystander exposure on outbound paths** (`bystander-privacy`) — see [criteria reference](../criteria/index.md)._
_Question: Are identifiers of recorded third parties stripped or generalized on every outbound path (shares, public pages, exports, media metadata, operator analytics), with a documented channel for a named third party to request removal?_
_References: [Regulation (EU) 2016/679 (GDPR) — Art. 5(1)(c) (data minimisation) and Art. 14 (information where data are not obtained from the data subject)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · CJEU, Bodil Lindqvist — Case C-101/01 (household exemption does not cover publication to an indefinite audience) · [Regulation (EU) 2016/679 (GDPR) — Art. 17 (right to erasure)](https://eur-lex.europa.eu/eli/reg/2016/679/oj)_