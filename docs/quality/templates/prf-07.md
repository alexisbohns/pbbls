<!-- Kritik issue skeleton for PRF-07 (Performance & Efficiency) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core + the surface label + milestone. -->

Title: [Quality] PRF-07 {surface}: caching/offline gap ({data_class} has no policy or purge)

## Quality finding: PRF-07 Layered caching strategy and offline reads

**Surface:** {surface}
**Observed level:** {observed_level}/4 | **Target level:** {target_level}/4

### Evidence
{evidence}

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
_Criterion: **PRF-07 · Layered caching strategy and offline reads** (`caching`) — see [criteria reference](../criteria/index.md)._
_Question: Does each class of data (immutable assets, media, private user data) have a named caching policy, and does a cold open on a dead network render cached content with a clear sync state instead of a spinner or crash?_
_References: [HTTP Caching (RFC 9111) — Freshness and validation model](https://www.rfc-editor.org/rfc/rfc9111) · [web.dev PWA course: Caching — Caching strategies (cache-first, stale-while-revalidate)](https://web.dev/learn/pwa/caching) · [Offline UX design guidelines — Communicating state when offline](https://web.dev/articles/offline-ux-design-guidelines)_