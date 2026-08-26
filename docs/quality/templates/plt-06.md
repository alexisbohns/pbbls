<!-- Kritik issue skeleton for PLT-06 (Platform & Store Compliance) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, web + the surface label + milestone. -->

Title: [Quality] PLT-06 PWA platform gap on web: {gap_summary}

## Criterion
PLT-06 PWA installability and service worker lifecycle discipline (pwa-standards)

## Observed level
{observed_level}/4 on `web` (target: {target_level}/4)

## Evidence
{evidence_bullets}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5 = {severity}. Broken installability degrades the primary distribution channel; careless caching can expose or stale-serve private data.

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criteria}

---
_Criterion: **PLT-06 · PWA installability and service worker lifecycle discipline** (`pwa-standards`) — see [criteria reference](../criteria/index.md)._
_Question: Does the web app meet installability requirements (complete manifest, secure context, registered service worker), and is the service worker's caching and update behavior a deliberate design that never mishandles authenticated data?_
_References: [W3C Web Application Manifest](https://www.w3.org/TR/appmanifest/) · [web.dev: Installability criteria](https://web.dev/articles/install-criteria) · [MDN: Service Worker API](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API)_