<!-- Kritik issue skeleton for PRF-02 (Performance & Efficiency) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, ui, web, admin + the surface label + milestone. -->

Title: [Quality] PRF-02 {surface}: bundle discipline gap ({offender} adds {kb} kB to first load)

## Quality finding: PRF-02 Client JavaScript bundle discipline

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
_Criterion: **PRF-02 · Client JavaScript bundle discipline** (`bundle`) — see [criteria reference](../criteria/index.md)._
_Question: Is shipped client JavaScript kept intentionally small, with server/client component boundaries pushed to the leaves, heavy dependencies audited, and bundle size tracked so growth is a visible decision?_
_References: [Reduce JavaScript payloads with code splitting — Code splitting strategies](https://web.dev/articles/reduce-javascript-payloads-with-code-splitting) · [React Server Components — Server Components without a framework / usage](https://react.dev/reference/rsc/server-components) · [Performance budgets 101 — Quantity-based metrics](https://web.dev/articles/performance-budgets-101)_