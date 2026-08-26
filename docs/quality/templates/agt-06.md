<!-- Kritik issue skeleton for AGT-06 (Agentic Development Readiness) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, facility + the surface label + milestone. -->

Title: [Quality] AGT-06 conventions: {short_finding} ({surface})

**Criterion:** AGT-06 Machine-checkable contribution conventions (AGT/conventions)
**Surface(s):** {surfaces}
**Observed level:** {observed_level}/4, **Target level:** {target_level}/4
**Severity:** impact {impact} x likelihood {likelihood}

### Evidence
{grammar_gaps_or_unchecked_blocks}

### Risk
{downstream_automation_or_history_damage}

### Remediation
- [ ] {tighten_grammar_in_docs}
- [ ] {add_pr_time_validator}
- [ ] {document_escape_hatch}

### Acceptance criteria
- Violations surface at PR-open time with a specific message
- Machine-read blocks are schema-validated before merge

---
_Criterion: **AGT-06 · Machine-checkable contribution conventions** (`conventions`) — see [criteria reference](../criteria/index.md)._
_Question: Are commit, branch, PR, label, and structured PR-body conventions specified as exact grammars, and are the highest-value ones checked by automation at PR time?_
_References: [Conventional Commits — v1.0.0 specification](https://www.conventionalcommits.org/en/v1.0.0/) · [GitHub Docs: About issue and pull request templates — Pull request templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/about-issue-and-pull-request-templates)_