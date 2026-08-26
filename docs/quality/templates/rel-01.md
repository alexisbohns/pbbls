<!-- Kritik issue skeleton for REL-01 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, ui, {surface} + the surface label + milestone. -->

Title: [Quality] REL-01 {surface}: failed loads indistinguishable from empty states on {screen_or_flow}

**Criterion:** REL-01 Failure states distinct from empty states
**Surface:** {surface}
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{evidence_bullets_with_file_paths}

## Risk
{risk_narrative}

## Remediation
- [ ] {introduce_or_reuse_tristate_state_pattern}
- [ ] {add_error_boundary_or_error_branch_per_screen}
- [ ] {enable_empty_catch_lint_rule}

## Acceptance criteria
- [ ] Simulated network failure on {screen_or_flow} renders a distinct error state with retry
- [ ] Empty-catch lint passes with zero suppressions
- [ ] Error path emits a labeled log in production builds

---
_Criterion: **REL-01 · Failure states distinct from empty states** (`error-surfacing`) — see [criteria reference](../criteria/index.md)._
_Question: Does every user-facing data load and mutation distinguish failure from emptiness, showing the user a recoverable error state and logging the cause?_
_References: [OWASP Application Security Verification Standard 4.0.3 — V7.4 Error Handling](https://owasp.org/www-project-application-security-verification-standard/) · [CWE-390: Detection of Error Condition Without Action — CWE-390](https://cwe.mitre.org/data/definitions/390.html) · [Apple Human Interface Guidelines — Patterns: Loading](https://developer.apple.com/design/human-interface-guidelines/loading) · ISO/IEC 25010 Product quality model — Reliability: Fault tolerance_