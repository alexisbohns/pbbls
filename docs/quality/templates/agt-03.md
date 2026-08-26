<!-- Kritik issue skeleton for AGT-03 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility, db + the surface label + milestone. -->

Title: [Quality] AGT-03 verifiability: {short_finding} ({surface})

**Criterion:** AGT-03 Provable changes: fast agent verification loops (AGT/verifiability)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{missing_slow_or_environment_fragile_loops}

### Risk
{which_changes_land_unproven_because_loops_are_unrunnable}

### Remediation
- [ ] {add_scoped_one_command_checks}
- [ ] {make_loop_headless_or_degrade_gracefully}
- [ ] {document_proof_commands_in_agent_guidance}

### Acceptance criteria
- Scoped loop runs green headlessly within budget
- Every required proof is a single documented command runnable by an agent

---
_Criterion: **AGT-03 · Provable changes: fast agent verification loops** (`verifiability`) — see [criteria reference](../criteria/index.md)._
_Question: Can an agent prove any change it makes by running fast, deterministic, workspace-scoped lint, test, and build loops headlessly, with every required proof documented as a single command?_
_References: [Self Testing Code (Martin Fowler) — Self-testing code definition](https://martinfowler.com/bliki/SelfTestingCode.html) · [Consumer-Driven Contracts: A Service Evolution Pattern (Ian Robinson) — Consumer-driven contracts](https://martinfowler.com/articles/consumerDrivenContracts.html) · ISO/IEC 25010 Software product quality model — Maintainability: Testability_