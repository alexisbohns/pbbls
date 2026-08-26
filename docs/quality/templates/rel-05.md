<!-- Kritik issue skeleton for REL-05 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, {surface} + the surface label + milestone. -->

Title: [Quality] REL-05 {surface}: offline/backend-down behavior on {screen_or_flow} is {observed_behavior}

**Criterion:** REL-05 Predictable offline and dependency-down behavior
**Surface:** {surface}
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{offline_test_results_per_screen}

## Risk
{input_loss_or_trust_scenario}

## Remediation
- [ ] {add_connectivity_monitor_and_offline_notice}
- [ ] {persist_in_progress_input_locally}
- [ ] {define_offline_matrix_entry}
- [ ] {add_transport_failure_test}

## Acceptance criteria
- [ ] {screen_or_flow} renders its designed state with the backend unreachable
- [ ] In-progress input survives app kill and network loss
- [ ] Reconnect recovery is automatic or one tap

---
_Criterion: **REL-05 · Predictable offline and dependency-down behavior** (`offline-degradation`) — see [criteria reference](../criteria/index.md)._
_Question: When the network is absent or the backend is down, does each client render an intentional state, preserve in-progress user input, and recover cleanly on reconnect?_
_References: [web.dev: Offline UX design guidelines](https://web.dev/articles/offline-ux-design-guidelines) · [Android Core App Quality checklist — Functionality and network handling items](https://developer.android.com/docs/quality-guidelines/core-app-quality) · ISO/IEC 25010 Product quality model — Reliability: Fault tolerance_