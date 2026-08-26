<!-- Kritik issue skeleton for ARC-01 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core, ui + the surface label + milestone. -->

Title: [Quality] Layer bleed in {surface}: {component_or_module} bypasses the data boundary

Criterion: {criterion_id} {criterion_name}
Surface: {surface}

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{evidence_bullets_with_file_paths_and_line_refs}

## Risk
{impact_x_likelihood_rationale}

## Remediation
- [ ] Move data calls in {files} behind the data-layer boundary
- [ ] Relocate business rules to {hook_viewmodel_or_util}
- [ ] Add or extend the lint/architecture rule blocking direct provider imports

## Acceptance criteria
- No view file imports the storage client or concrete provider
- The guarding rule fails CI on reintroduction
- Extracted logic has a unit test

---
_Criterion: **ARC-01 · Responsibility and layer separation** (`layering`) — see [criteria reference](../criteria/index.md)._
_Question: Is business logic kept out of view components, with all data access funneled through a single isolated data-layer boundary, and does each module own one responsibility?_
_References: [ISO/IEC 25010:2023 Product quality model — Maintainability: Modularity, Modifiability](https://www.iso.org/standard/78176.html) · [Android Developers: Guide to app architecture — UI layer / Data layer separation](https://developer.android.com/topic/architecture) · [Apple Developer Documentation: SwiftUI Model data — Managing model data in your app](https://developer.apple.com/documentation/swiftui/model-data)_