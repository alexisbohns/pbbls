<!-- Kritik issue skeleton for PLT-05 (Platform & Store Compliance) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, ios, android + the surface label + milestone. -->

Title: [Quality] PLT-05 store toolchain currency on {surface}: {gap_summary}

## Criterion
PLT-05 Store technical currency (store-currency)

## Observed level
{observed_level}/4 on `{surface}` (target: {target_level}/4)

## Evidence
{evidence_bullets}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5 = {severity}. Blocked uploads at the store deadline; the app becomes unfixable in an emergency.

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criteria}

---
_Criterion: **PLT-05 · Store technical currency (target API and toolchain floors)** (`store-currency`) — see [criteria reference](../criteria/index.md)._
_Question: Are the Android target API level and the iOS build toolchain within the stores' current requirement windows, and does a process exist that catches the annual deadline before uploads are blocked?_
_References: [Google Play: Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878) · [Android Developers: Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)_