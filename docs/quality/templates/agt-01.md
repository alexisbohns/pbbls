<!-- Kritik issue skeleton for AGT-01 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility + the surface label + milestone. -->

Title: [Quality] AGT-01 instruction docs: {short_finding} ({surface})

**Criterion:** AGT-01 Layered agent instruction docs, accurate and lean (AGT/instruction-docs)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{evidence_bullets_with_file_paths}

### Risk
{what_agents_will_do_wrong_because_of_this}

### Remediation
- [ ] {fix_or_delete_stale_statements}
- [ ] {split_detail_into_on_demand_docs}
- [ ] {add_promotion_policy_or_ci_reference_check}

### Acceptance criteria
- Every command and path referenced in instruction files resolves against the tree
- {additional_acceptance_criterion}

---
_Criterion: **AGT-01 · Layered agent instruction docs, accurate and lean** (`instruction-docs`) — see [criteria reference](../criteria/index.md)._
_Question: Are agent instruction files (root and per-workspace) accurate against the current tree, layered so detail loads on demand, and governed by an explicit edit and promotion policy?_
_References: [Claude Code Best Practices (Anthropic Engineering) — Create CLAUDE.md files; Tune your CLAUDE.md files](https://www.anthropic.com/engineering/claude-code-best-practices) · [AGENTS.md open format for agent instructions — Format specification](https://agents.md)_