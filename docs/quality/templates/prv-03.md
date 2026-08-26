<!-- Kritik issue skeleton for PRV-03 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, api + the surface label + milestone. -->

Title: [Quality] PRV-03 {surface}: unreviewed third-party egress to {host_or_sdk}

**Criterion**: PRV-03 Third-party egress inventory (PRV/third-parties)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{evidence_bullets_with_hosts_and_files}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative_including_transfer_implications}

### Remediation
- [ ] {self_host_or_remove_or_document_egress}
- [ ] {update_inventory_and_platform_declarations}
- [ ] {pin_csp_or_add_ci_hostname_check}

### Acceptance criteria
- Every external host contacted by {surface} appears in the inventory with a purpose
- No personal data leaves to a third party without a documented processor relationship
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-03 · Third-party egress inventory (SDKs, fonts, CDNs)** (`third-parties`) — see [criteria reference](../criteria/index.md)._
_Question: Is every third-party network destination and SDK enumerated and justified, with personal data confirmed absent from each egress or covered by a documented processor relationship?_
_References: [GDPR — Art. 28 (processor) and Art. 44 (general principle for transfers)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [Apple Developer Documentation: Privacy manifest files — Privacy manifests and tracking domains](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files) · [Google Play Developer Content Policy — User Data policy (Data safety declarations)](https://play.google/developer-content-policy/) · [OWASP MASVS v2 — MASVS-PRIVACY (third-party data sharing)](https://mas.owasp.org/MASVS/)_