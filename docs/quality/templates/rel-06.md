<!-- Kritik issue skeleton for REL-06 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, supabase + the surface label + milestone. -->

Title: [Quality] REL-06 supabase: migration {migration_file} risks {breakage_kind} for deployed clients

**Criterion:** REL-06 Contract-safe migrations with rollback story
**Surface:** supabase
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{migration_files_and_ddl_findings}

## Risk
{deployed_client_breakage_scenario}

## Remediation
- [ ] {stage_as_expand_contract}
- [ ] {add_scratch_db_ci_job}
- [ ] {state_rollback_path}
- [ ] {regenerate_contract_types}

## Acceptance criteria
- [ ] All deployed client versions tolerate both schema shapes during the transition
- [ ] CI applies the full migration chain green
- [ ] Rollback path documented in the PR

---
_Criterion: **REL-06 · Contract-safe migrations with rollback story** (`migration-safety`) — see [criteria reference](../criteria/index.md)._
_Question: Can every schema migration deploy without breaking clients already in the field, and does each migration have an understood blast radius and a stated rollback path before it runs?_
_References: [Martin Fowler: Evolutionary Database Design](https://martinfowler.com/articles/evodb.html) · [Martin Fowler: ParallelChange (expand-contract)](https://martinfowler.com/bliki/ParallelChange.html) · [Supabase Docs: Database migrations](https://supabase.com/docs/guides/deployment/database-migrations)_