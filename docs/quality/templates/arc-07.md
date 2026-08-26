<!-- Kritik issue skeleton for ARC-07 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, api + the surface label + milestone. -->

Title: [Quality] Swallowed or untyped failure path on {surface}: {operation}

Criterion: {criterion_id} {criterion_name}
Surface: {surface}

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{swallowed_or_untyped_paths_with_file_and_line}

## Risk
{impact_x_likelihood_rationale}

## Remediation
- [ ] Handle or propagate errors at {catch_sites}
- [ ] Convert {rpc_or_endpoint} to raise/return structured errors
- [ ] Add lint/CI grep banning empty catches

## Acceptance criteria
- Grep for empty catches returns zero
- Callers branch on typed failure kinds at {boundary}
- CI rejects reintroduction

---
_Criterion: **ARC-07 · Error handling as code structure** (`error-handling`) — see [criteria reference](../criteria/index.md)._
_Question: Is failure a first-class code path in the code's structure: no swallowed errors, and typed errors surfaced at layer boundaries so callers can branch on failure kind?_
_References: [CWE-390: Detection of Error Condition Without Action — CWE-390](https://cwe.mitre.org/data/definitions/390.html) · [CWE-544: Missing Standardized Error Handling Mechanism — CWE-544](https://cwe.mitre.org/data/definitions/544.html) · [ISO/IEC 25010:2023 Product quality model — Reliability: Fault tolerance](https://www.iso.org/standard/78176.html)_