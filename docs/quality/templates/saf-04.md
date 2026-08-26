<!-- Kritik issue skeleton for SAF-04 (Safety & Wellbeing) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, db, supabase + the surface label + milestone. -->

Title: [Quality] SAF-04 block leak via {path}: {short_finding}

**Criterion:** SAF-04 Block integrity and anti-harassment enforcement (SAF/social-abuse)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **target:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{evidence_paths_and_snippets}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step}

### Acceptance criteria
- [ ] Block predicate enforced in {path} at read time, server-side
- [ ] Blocked party receives shapes indistinguishable from absence
- [ ] Block-matrix harness covers the fixed path and runs in CI
- [ ] {additional_acceptance}

---
_Criterion: **SAF-04 · Block integrity and anti-harassment enforcement** (`social-abuse`) — see [criteria reference](../criteria/index.md)._
_Question: Does a server-enforced, silent block primitive sever every read and contact path between two users, including pre-existing shares and invite flows, with enumeration- and spam-resistant invites, verified by a contract harness?_
_References: [Apple App Review Guidelines — Guideline 1.2 (User-Generated Content: ability to block abusive users)](https://developer.apple.com/app-store/review/guidelines/) · [Google Play Developer Program Policies, User Generated Content — User Generated Content policy (user blocking and reporting)](https://play.google/developer-content-policy/) · [eSafety Commissioner, Safety by Design — Safety by Design principles](https://www.esafety.gov.au/industry/safety-by-design)_