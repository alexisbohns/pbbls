<!-- Kritik issue skeleton for PLT-02 (Platform & Store Compliance) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, ios, android + the surface label + milestone. -->

Title: [Quality] PLT-02 privacy declaration drift on {surface}: {gap_summary}

## Criterion
PLT-02 Truthful privacy declarations and tracking consent (store-privacy)

## Observed level
{observed_level}/4 on `{surface}` (target: {target_level}/4)

## Evidence
{evidence_bullets}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5 = {severity}. Undeclared collection or SDK data flows risk store rejection and regulatory action on sensitive data.

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criteria}

---
_Criterion: **PLT-02 · Truthful privacy declarations and tracking consent** (`store-privacy`) — see [criteria reference](../criteria/index.md)._
_Question: Do the App Store privacy nutrition labels, the iOS privacy manifest, and the Play Data safety form each match the data the code actually collects and the SDKs it actually ships, and is cross-app tracking either provably absent or gated behind ATT?_
_References: [Apple: App privacy details on the App Store](https://developer.apple.com/app-store/app-privacy-details/) · [Apple: Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files) · [Apple App Store Review Guidelines — 5.1.2 Data Use and Sharing](https://developer.apple.com/app-store/review/guidelines/) · [Apple: AppTrackingTransparency framework](https://developer.apple.com/documentation/apptrackingtransparency) · [Google Play: Provide information for the Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469) · [OWASP MASVS — MASVS-PRIVACY](https://mas.owasp.org/MASVS/)_