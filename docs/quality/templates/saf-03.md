<!-- Kritik issue skeleton for SAF-03 (Safety & Wellbeing) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, db, admin, supabase + the surface label + milestone. -->

Title: [Quality] SAF-03 unmoderated cross-user content: {content_type}

**Criterion:** SAF-03 UGC moderation state machine and takedown (SAF/ugc-moderation)
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
- [ ] {content_type} serving paths filter on moderation status server-side
- [ ] Takedown removes the storage object and audit-logs the decision
- [ ] {additional_acceptance}

---
_Criterion: **SAF-03 · UGC moderation state machine and takedown** (`ugc-moderation`) — see [criteria reference](../criteria/index.md)._
_Question: Does every user-authored content type visible to other users or the public carry a server-enforced moderation state, operator review tooling with an audit trail, and takedown that propagates to all serving paths including storage?_
_References: [Apple App Review Guidelines — Guideline 1.2 (User-Generated Content)](https://developer.apple.com/app-store/review/guidelines/) · [Google Play Developer Program Policies, User Generated Content — User Generated Content policy](https://play.google/developer-content-policy/) · [Regulation (EU) 2022/2065 (Digital Services Act) — Art. 16 (Notice and action mechanisms)](https://eur-lex.europa.eu/eli/reg/2022/2065/oj)_