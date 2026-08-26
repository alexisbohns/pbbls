<!-- Kritik issue skeleton for AGT-02 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility + the surface label + milestone. -->

Title: [Quality] AGT-02 product map: {short_finding} ({surface})

**Criterion:** AGT-02 Product map freshness with drift gates (AGT/map-freshness)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{stale_or_missing_map_nodes_with_paths}

### Risk
{how_agents_get_misrouted}

### Remediation
- [ ] {backfill_missing_nodes_and_journal_events}
- [ ] {encode_update_trigger_in_agent_instructions}
- [ ] {add_or_extend_ci_validation}

### Acceptance criteria
- Validator passes in CI on map paths
- Sampled structural PRs carry same-PR map updates

---
_Criterion: **AGT-02 · Product map freshness with drift gates** (`map-freshness`) — see [criteria reference](../criteria/index.md)._
_Question: Does a machine-readable product map (screens, flows, data models, endpoints) exist with a defined update trigger for agents and a CI gate that rejects an invalid or contradicted map?_
_References: [C4 model for visualising software architecture — System Context and Container diagrams](https://c4model.com) · ISO/IEC/IEEE 42010 Systems and software engineering, Architecture description — Architecture description practices_