<!-- Kritik issue skeleton for ARC-08 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db + the surface label + milestone. -->

Title: [Quality] Migration hazard: {hazard_kind} in {migration_file}

Criterion: {criterion_id} {criterion_name}
Surface: supabase

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{migration_files_and_diff_findings}

## Risk
{impact_x_likelihood_rationale} A clobbered function body silently drops shipped behavior for all clients.

## Remediation
- [ ] New migration unioning {function} bodies (never edit shipped files)
- [ ] Add missing {rls_or_search_path_or_sync} in a follow-up migration
- [ ] Regenerate client types and update {harness}
- [ ] Add CI check for {drift_kind}

## Acceptance criteria
- Pairwise body diff shows all appends present
- Types match migrations in CI
- Harness green against a real database

---
_Criterion: **ARC-08 · Migration and schema change quality** (`schema-quality`) — see [criteria reference](../criteria/index.md)._
_Question: Are schema changes append-only, safely re-runnable where intended, free of create-or-replace clobber hazards, paired with regenerated client types, and complete (RLS, derived-data sync, harness updates) within the same change?_
_References: [PostgreSQL Documentation: CREATE FUNCTION — CREATE OR REPLACE FUNCTION semantics](https://www.postgresql.org/docs/current/sql-createfunction.html) · [Supabase Docs: Database Migrations — Local development and migrations workflow](https://supabase.com/docs/guides/deployment/database-migrations) · [Supabase Docs: Row Level Security — Enabling RLS on tables](https://supabase.com/docs/guides/database/postgres/row-level-security)_