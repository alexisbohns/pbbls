# Monorepo Quality & Robustness Audit

## Goal

Comprehensive quality and robustness review of the pbbls monorepo, covering correctness, safety, hygiene, and maintainability. Produces two deliverables: a categorized findings report and a prioritized implementation plan.

## Approach

Domain-by-domain sweep. Each domain is audited as an independent unit using a consistent checklist, then a synthesis pass catches cross-domain issues.

### Audit domains

| # | Domain | Scope |
|---|--------|-------|
| 1 | Root / Monorepo infra | `package.json`, `turbo.json`, workspace configs, git/CI setup, root-level files |
| 2 | `apps/web` | Routes, components, data layer, hooks, configs, utils, PWA setup |
| 3 | `packages/supabase` | Migrations, types, client code, CLI scripts |
| 4 | `packages/shared` | Stub state, exports, actual usage |
| 5 | `apps/ios` | Stub state, noise/risk assessment |
| 6 | Docs & agent instructions | CLAUDE.md (root + workspace), AGENTS.md, copilot-instructions.md, Arkaik bundle, specs/plans (light pass) |

### Per-domain checklist

Each domain is checked against these 6 categories:

1. **Security** — exposed secrets, unsafe patterns, auth gaps, XSS/injection vectors, env handling
2. **Dead code** — unused exports, orphaned files, unreachable routes, stale imports
3. **Build integrity** — build/lint pass cleanly, unused deps, mismatched versions, workspace wiring
4. **Pattern consistency** — adherence to CLAUDE.md conventions (naming, structure, separation of concerns)
5. **Redundancy** — duplicated logic, components, or configs across workspaces
6. **Doc accuracy** — do instructions match reality, stale references to old structure

## Findings format

Each finding is tagged with:

- **Severity**: `critical` (security/data risk), `high` (build/correctness risk), `medium` (maintainability), `low` (hygiene/nitpick)
- **Category**: one of the 6 checklist items
- **Location**: file path(s) affected
- **Description**: what's wrong and why it matters
- **Suggested fix**: concrete action to resolve it

The report ends with a severity summary table (counts per severity per domain).

## Execution strategy

1. Run `npm run build` and `npm run lint` to establish baseline build health.
2. Dispatch 6 parallel audit agents, one per domain, each running the full checklist.
3. Synthesis pass: review all findings, deduplicate, check for cross-domain issues, assign final severities.
4. Write the findings report to `docs/superpowers/specs/2026-04-11-monorepo-audit-report.md`.
5. Write the implementation plan using the writing-plans skill.

## Out of scope

- Performance profiling or bundle size analysis
- Runtime functional testing (build/lint only, not behavioral)
- Deep Arkaik node-by-node cross-referencing (light pass for obvious staleness only)

## Deliverables

1. **Findings report**: `docs/superpowers/specs/2026-04-11-monorepo-audit-report.md`
2. **Implementation plan**: written via writing-plans skill, saved to `docs/superpowers/plans/`
