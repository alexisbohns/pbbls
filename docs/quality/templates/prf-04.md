<!-- Kritik issue skeleton for PRF-04 (Performance & Efficiency) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, supabase + the surface label + milestone. -->

Title: [Quality] PRF-04 supabase: missing index for {table}.{column} used by {policy_or_query}

## Quality finding: PRF-04 Indexes match access paths and RLS predicates

**Surface:** supabase
**Observed level:** {observed_level}/4 | **Target level:** {target_level}/4

### Evidence
{evidence}

(EXPLAIN output or advisor report excerpt)

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
_Criterion: **PRF-04 · Indexes match access paths and RLS predicates** (`query-efficiency`) — see [criteria reference](../criteria/index.md)._
_Question: Does every hot query path, including the implicit filters injected by RLS policies, have a matching index, and is this verified with query plans rather than assumed?_
_References: [Supabase Row Level Security — RLS performance recommendations (add indexes, wrap functions in select)](https://supabase.com/docs/guides/database/postgres/row-level-security) · [PostgreSQL Indexes — Chapter 11, index types and multicolumn indexes](https://www.postgresql.org/docs/current/indexes.html) · [Supabase database advisors — Performance advisors incl. unindexed foreign keys](https://supabase.com/docs/guides/database/database-advisors)_