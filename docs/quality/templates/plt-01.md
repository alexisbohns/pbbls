<!-- Kritik issue skeleton for PLT-01 (Platform & Store Compliance) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, core + the surface label + milestone. -->

Title: [Quality] PLT-01 account deletion entry point gap on {surface}: {gap_summary}

## Criterion
PLT-01 In-app account deletion entry points, store-compliant (store-accounts)

## Observed level
{observed_level}/4 on `{surface}` (target: {target_level}/4)

## Evidence
{evidence_bullets}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5 = {severity}. Store rejection/removal under Apple 5.1.1(v) or Play account-deletion policy; purge completeness exposure is tracked separately under PRV-08.

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criteria}

---
_Criterion: **PLT-01 · In-app account deletion entry points, store-compliant** (`store-accounts`) — see [criteria reference](../criteria/index.md)._
_Question: Can a user find and initiate account deletion from inside every store-distributed client, and does a web-reachable deletion resource exist and match what is declared in the Play Console?_
_References: [Apple App Store Review Guidelines — 5.1.1(v) Account Sign-In / Account Deletion](https://developer.apple.com/app-store/review/guidelines/) · [Apple: Offering account deletion in your app](https://developer.apple.com/support/offering-account-deletion-in-your-app/) · [Google Play: app account deletion requirement](https://support.google.com/googleplay/android-developer/answer/13327111) · [Regulation (EU) 2016/679 (GDPR) — Art. 17 Right to erasure](https://eur-lex.europa.eu/eli/reg/2016/679/oj)_