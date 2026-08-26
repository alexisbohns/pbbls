<!-- Kritik issue skeleton for ARC-04 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core + the surface label + milestone. -->

Title: [Quality] Convention drift on {surface}: {convention_area}

Criterion: {criterion_id} {criterion_name}
Surface: {surface}

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{sampled_violations_with_paths}

## Risk
{impact_x_likelihood_rationale}

## Remediation
- [ ] Document the rule in {doc_path}
- [ ] Mechanical rename PR for {files}
- [ ] Encode the rule in {linter}

## Acceptance criteria
- Written rule exists and matches practice
- Sampling shows conformance
- Linter catches new violations in CI

---
_Criterion: **ARC-04 · Naming and file convention consistency** (`conventions`) — see [criteria reference](../criteria/index.md)._
_Question: Are naming and file-layout conventions written down per surface and consistently applied, so an auditor can predict a file's location and name from its role?_
_References: [Swift.org API Design Guidelines — Naming](https://www.swift.org/documentation/api-design-guidelines/) · [Kotlin Coding Conventions — Naming rules](https://kotlinlang.org/docs/coding-conventions.html) · [ISO/IEC 25010:2023 Product quality model — Maintainability: Analysability](https://www.iso.org/standard/78176.html)_