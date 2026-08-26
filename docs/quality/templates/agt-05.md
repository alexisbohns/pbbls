<!-- Kritik issue skeleton for AGT-05 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility + the surface label + milestone. -->

Title: [Quality] AGT-05 determinism: {short_finding} ({surface})

**Criterion:** AGT-05 Scripts over tribal knowledge (AGT/determinism)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{prose_only_or_fragile_workflows}

### Risk
{what_an_agent_guesses_wrong_without_the_script}

### Remediation
- [ ] {script_the_workflow}
- [ ] {add_graceful_environment_guard}
- [ ] {align_ci_with_local_entry_points}

### Acceptance criteria
- Operation runs as one committed command in the agent environment
- CI invokes the same entry point

---
_Criterion: **AGT-05 · Scripts over tribal knowledge** (`determinism`) — see [criteria reference](../criteria/index.md)._
_Question: Is every routine operation (build, test, typegen, database workflows, release) a single committed script or task entry that runs deterministically in the agent environment, rather than a sequence a human must remember?_
_References: [Scripts to Rule Them All (GitHub Engineering) — Normalized script pattern](https://github.blog/2015-06-30-scripts-to-rule-them-all/) · [The Twelve-Factor App — Factor X: Dev/prod parity](https://12factor.net/dev-prod-parity) · [Site Reliability Engineering: Eliminating Toil (Google) — Chapter 5](https://sre.google/sre-book/eliminating-toil/)_