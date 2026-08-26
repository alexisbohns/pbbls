<!-- Kritik issue skeleton for AGT-08 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility + the surface label + milestone. -->

Title: [Quality] AGT-08 decision memory: {short_finding} ({surface})

**Criterion:** AGT-08 Decision log discipline (AGT/decision-memory)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{missing_entries_edited_history_or_unreferenced_log}

### Risk
{settled_choice_likely_to_be_re_litigated_or_reversed}

### Remediation
- [ ] {backfill_or_append_entries}
- [ ] {state_rules_in_log_and_instructions}
- [ ] {add_append_only_ci_check}

### Acceptance criteria
- Log states its bar and rules, and instructions direct agents to it
- History shows appends only; supersession links resolve both ways

---
_Criterion: **AGT-08 · Decision log discipline** (`decision-memory`) — see [criteria reference](../criteria/index.md)._
_Question: Is there an append-only decision log with a stated significance bar and a supersede-not-edit rule, referenced from agent instructions so settled choices are consulted instead of re-litigated?_
_References: [Documenting Architecture Decisions (Michael Nygard) — ADR structure and immutability](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) · [Architectural Decision Records — ADR overview](https://adr.github.io)_