<!-- Kritik issue skeleton for ARC-05 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core + the surface label + milestone. -->

Title: [Quality] Duplication/dead code on {surface}: {cluster_or_symbol}

Criterion: {criterion_id} {criterion_name}
Surface: {surface}

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{duplication_clusters_or_dead_symbols_with_paths}

## Risk
{impact_x_likelihood_rationale}

## Remediation
- [ ] Extract {logic} into {shared_module}
- [ ] Delete unreferenced {symbols_or_files}
- [ ] Add {analyzer} to CI or the audit cadence

## Acceptance criteria
- The rule exists once per surface
- Analyzer reports zero new unused exports
- Behavior unchanged (tests green)

---
_Criterion: **ARC-05 · Duplication control and dead code removal** (`duplication`) — see [criteria reference](../criteria/index.md)._
_Question: Is logic factored so each rule lives in one place per surface, is intentional cross-platform mirroring distinguished from accidental duplication, and is dead code actively removed?_
_References: [CWE-1041: Use of Redundant Code — CWE-1041](https://cwe.mitre.org/data/definitions/1041.html) · [CWE-561: Dead Code — CWE-561](https://cwe.mitre.org/data/definitions/561.html) · [ISO/IEC 25010:2023 Product quality model — Maintainability: Reusability](https://www.iso.org/standard/78176.html)_