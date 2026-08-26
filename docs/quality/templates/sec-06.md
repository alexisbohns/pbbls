<!-- Kritik issue skeleton for SEC-06 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core + the surface label + milestone. -->

Title: [Quality] SEC-06 transport/storage protection at level {observed_level} on {surface} (target {target_level})

## Quality finding: SEC-06 Transport encryption and on-device data protection

**Surface:** {surface}
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{configs_and_storage_locations_with_paths}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

### Acceptance criteria
- [ ] Config greps clean (no opt-outs, no cleartext, no public user buckets)
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-06 · Transport encryption and on-device data protection** (`transport-storage`) — see [criteria reference](../criteria/index.md)._
_Question: Is data protected in transit (TLS everywhere, no cleartext exceptions) and at rest on device and in storage (secure credential stores, private buckets, scoped access to user media)?_
_References: [Apple Developer Documentation: Preventing Insecure Network Connections](https://developer.apple.com/documentation/security/preventing-insecure-network-connections) · [Android Developers: Network security configuration](https://developer.android.com/privacy-and-security/security-config) · [OWASP MASVS v2 — MASVS-NETWORK, MASVS-STORAGE](https://mas.owasp.org/MASVS/) · [CWE-319 Cleartext Transmission of Sensitive Information](https://cwe.mitre.org/data/definitions/319.html) · [CWE-312 Cleartext Storage of Sensitive Information](https://cwe.mitre.org/data/definitions/312.html) · [GDPR — Art. 32 Security of processing](https://eur-lex.europa.eu/eli/reg/2016/679/oj)_