<!-- Kritik issue skeleton for PRF-06 (Performance & Efficiency) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, ui, ios, android + the surface label + milestone. -->

Title: [Quality] PRF-06 {surface}: startup/frame discipline gap in {screen_or_path}

## Quality finding: PRF-06 Mobile cold start, frames, and animation

**Surface:** {surface}
**Observed level:** {observed_level}/4 | **Target level:** {target_level}/4

### Evidence
{evidence}

(trace or benchmark excerpt if available)

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
_Criterion: **PRF-06 · Mobile cold start, frames, and animation** (`mobile-startup`) — see [criteria reference](../criteria/index.md)._
_Question: Are cold start and frame rendering kept within platform vitals, with blocking work off the launch path and main thread, contained recomposition/invalidation scope, and animations running on the render path?_
_References: [Android: App startup time — Cold start expectations and diagnosis](https://developer.android.com/topic/performance/vitals/launch-time) · [Jetpack Compose performance — Stability, keys, deferred reads](https://developer.android.com/develop/ui/compose/performance) · [Baseline Profiles overview — Startup improvement via profile-guided compilation](https://developer.android.com/topic/performance/baselineprofiles/overview) · [Apple: Reducing your app's launch time — Minimize work at launch](https://developer.apple.com/documentation/xcode/reducing-your-app-s-launch-time)_