<!-- Kritik issue skeleton for REL-08 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, {surface} + the surface label + milestone. -->

Title: [Quality] REL-08 {surface}: production failures do not reach a monitored destination

**Criterion:** REL-08 Production failures reach a human
**Surface:** {surface}
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{missing_sdk_or_unmonitored_paths}

## Risk
{silent_failure_scenario}

## Remediation
- [ ] {wire_surface_to_reporting_destination}
- [ ] {configure_pii_scrubbing}
- [ ] {add_error_rate_alert}
- [ ] {send_test_error_and_verify}

## Acceptance criteria
- [ ] A forced test error on {surface} appears at the monitored destination
- [ ] Reports contain no user content or personal data
- [ ] An alert fires on error-rate spike and someone is named to triage

---
_Criterion: **REL-08 · Production failures reach a human** (`crash-reporting`) — see [criteria reference](../criteria/index.md)._
_Question: Do production crashes and serious errors on every surface reliably reach a monitored destination with enough context to act, and with sensitive content scrubbed?_
_References: [OWASP Application Security Verification Standard 4.0.3 — V7.1 Log Content, V7.2 Log Processing](https://owasp.org/www-project-application-security-verification-standard/) · [NIST SP 800-92: Guide to Computer Security Log Management](https://csrc.nist.gov/pubs/sp/800/92/final) · [Apple App Review Guidelines — Guideline 2.1 App Completeness](https://developer.apple.com/app-store/review/guidelines/) · [Android vitals](https://developer.android.com/topic/performance/vitals)_