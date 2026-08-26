<!-- Kritik issue skeleton for AGT-04 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility, db + the surface label + milestone. -->

Title: [Quality] AGT-04 guardrails: {short_finding} ({surface})

**Criterion:** AGT-04 Dangerous operations flagged where agents read (AGT/guardrails)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{unencoded_hazard_and_where_it_lives_today}

### Risk
{concrete_corruption_or_loss_scenario}

### Remediation
- [ ] {write_trigger_action_proof_rule_at_scope}
- [ ] {add_automated_invariant_check_if_feasible}
- [ ] {link_rule_to_harness_or_ci}

### Acceptance criteria
- Rule present in the loaded instruction file at the correct scope
- {automation_or_harness_acceptance}

---
_Criterion: **AGT-04 · Dangerous operations flagged where agents read** (`guardrails`) — see [criteria reference](../criteria/index.md)._
_Question: Are known footguns and irreversible operations encoded as explicit standing rules, with trigger, required action, and proof, in the instruction files every agent session loads?_
_References: [Claude Code Best Practices (Anthropic Engineering) — Tune your CLAUDE.md files](https://www.anthropic.com/engineering/claude-code-best-practices) · [PostgreSQL documentation: CREATE FUNCTION — CREATE OR REPLACE FUNCTION replacement semantics](https://www.postgresql.org/docs/current/sql-createfunction.html)_