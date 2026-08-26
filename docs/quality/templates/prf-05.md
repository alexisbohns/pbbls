<!-- Kritik issue skeleton for PRF-05 (Performance & Efficiency) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, api + the surface label + milestone. -->

Title: [Quality] PRF-05 {surface}: N+1 or unbounded read in {screen_or_module}

## Quality finding: PRF-05 Bounded, batched, lean client reads

**Surface:** {surface}
**Observed level:** {observed_level}/4 | **Target level:** {target_level}/4

### Evidence
{evidence}

(round-trip count observed: {n_requests} for {n_items} items)

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
_Criterion: **PRF-05 · Bounded, batched, lean client reads** (`query-efficiency`) — see [criteria reference](../criteria/index.md)._
_Question: Do client data layers fetch lists with a constant number of round trips, an explicit page bound, and only the columns they render, with no per-item follow-up queries?_
_References: [PostgREST resource embedding — Embedding related resources in one request](https://postgrest.org/en/stable/references/api/resource_embedding.html) · [PostgREST pagination and count — Limits, offsets, and Range headers](https://postgrest.org/en/stable/references/api/pagination_count.html) · [Use The Index, Luke: No Offset — Keyset pagination vs OFFSET](https://use-the-index-luke.com/no-offset)_