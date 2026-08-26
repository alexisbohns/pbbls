<!-- Kritik issue skeleton for PRF-08 (Performance & Efficiency) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, api + the surface label + milestone. -->

Title: [Quality] PRF-08 {surface}: chatty network path ({mechanism} in {screen_or_module})

## Quality finding: PRF-08 Network and battery frugality

**Surface:** {surface}
**Observed level:** {observed_level}/4 | **Target level:** {target_level}/4

### Evidence
{evidence}

(idle-minute request list: {idle_requests})

### Risk
{risk}

### Remediation checklist
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}
- [ ] {remediation_step_3}

### Acceptance criteria
- {acceptance_criterion_1}
- {acceptance_criterion_2}

---
_Criterion: **PRF-08 · Network and battery frugality** (`network-frugality`) — see [criteria reference](../criteria/index.md)._
_Question: Is recurring network work event-driven or justified and bounded (no unscoped polling, realtime subscriptions torn down when hidden, backoff on failure, deduplicated in-flight requests), so radios and batteries are not taxed by invisible traffic?_
_References: [Android: Optimize for Doze and App Standby — Restrictions on network and jobs in Doze](https://developer.android.com/training/monitoring-device-state/doze-standby) · [Energy Efficiency Guide for iOS Apps — Minimize and defer networking](https://developer.apple.com/library/archive/documentation/Performance/Conceptual/EnergyGuide-iOS/index.html) · [Supabase Realtime — Channels and subscription lifecycle](https://supabase.com/docs/guides/realtime)_