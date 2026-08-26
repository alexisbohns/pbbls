<!-- Kritik issue skeleton for ARC-03 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, core + the surface label + milestone. -->

Title: [Quality] Typing escape hatches on {surface}: {pattern} in {module}

Criterion: {criterion_id} {criterion_name}
Surface: {surface}

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{grep_hits_with_paths_and_lines}

## Risk
{impact_x_likelihood_rationale}

## Remediation
- [ ] Replace {escape_pattern} occurrences with typed alternatives
- [ ] Import shared shapes from generated DB types
- [ ] Add/raise lint rule {rule} to error severity
- [ ] Add CI drift check for generated types

## Acceptance criteria
- Grep for the pattern returns zero hits in production code
- Lint fails CI on reintroduction
- Generated types verified against migrations in CI

---
_Criterion: **ARC-03 · Strict typing and exhaustiveness discipline** (`typing`) — see [criteria reference](../criteria/index.md)._
_Question: Is the type system used at full strength: no any/Any escapes, no unchecked casts or force-unwraps, exhaustive handling of closed enums, and shared data shapes modeled from generated database types?_
_References: [typescript-eslint: no-explicit-any — Rule: @typescript-eslint/no-explicit-any](https://typescript-eslint.io/rules/no-explicit-any/) · [TypeScript TSConfig Reference — strict](https://www.typescriptlang.org/tsconfig#strict) · [CWE-704: Incorrect Type Conversion or Cast — CWE-704](https://cwe.mitre.org/data/definitions/704.html)_