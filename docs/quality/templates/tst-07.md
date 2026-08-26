<!-- Kritik issue skeleton for TST-07 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, test + the surface label + milestone. -->

Title: [Quality] TST-07: migrate {straggler_scope} to the designated test idiom on {surface}

**Criterion:** TST-07 One canonical test framework and idiom per surface
**Surface:** {surface}
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets} (files on the deprecated idiom, or tests not discovered by {test_command})

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative}

### Remediation
- [ ] Migrate {straggler_scope} to {designated_framework}
- [ ] {remediation_step_2}

### Acceptance criteria
- {test_command} discovers and runs every test file on the surface
- No deprecated-framework imports remain

---
_Criterion: **TST-07 · One canonical test framework and idiom per surface** (`platform-idioms`) — see [criteria reference](../criteria/index.md)._
_Question: Does each surface use a single designated, current test framework and the platform's supported idioms, with the whole suite runnable by the documented workspace command?_
_References: [Apple Developer Documentation, Swift Testing — Migrating a test from XCTest](https://developer.apple.com/documentation/testing) · [Android Developers, Test your Compose layout — Testing in Compose](https://developer.android.com/develop/ui/compose/testing) · [Android Developers, Compose Preview Screenshot Testing — Compose Preview Screenshot Testing](https://developer.android.com/studio/preview/compose-screenshot-testing) · [Vitest Guide — Getting Started](https://vitest.dev/guide/)_