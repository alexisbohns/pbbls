<!-- Kritik issue skeleton for REL-07 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, supabase + the surface label + milestone. -->

Title: [Quality] REL-07 supabase: {store} lacks verified backup/restore coverage

**Criterion:** REL-07 Backups exist and restore is rehearsed
**Surface:** supabase
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{coverage_gaps_per_store}

## Risk
{irrecoverable_loss_scenario}

## Remediation
- [ ] {document_tier_and_retention}
- [ ] {add_export_job_for_uncovered_store}
- [ ] {perform_and_date_restore_rehearsal}
- [ ] {alert_on_backup_failure}

## Acceptance criteria
- [ ] Database, storage objects, and auth data each have a named backup mechanism with stated retention
- [ ] A dated restore rehearsal document exists
- [ ] Backup/export failure notifies a human

---
_Criterion: **REL-07 · Backups exist and restore is rehearsed** (`backup-restore`) — see [criteria reference](../criteria/index.md)._
_Question: Are the database, file storage, and auth/identity data all covered by automated backups with known retention, and has a restore actually been performed and documented at least once?_
_References: [Regulation (EU) 2016/679 (GDPR) — Art. 32(1)(c) ability to restore availability and access to personal data](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [NIST SP 800-34 Rev. 1: Contingency Planning Guide for Federal Information Systems](https://csrc.nist.gov/pubs/sp/800/34/r1/upd1/final) · [Supabase Docs: Database backups](https://supabase.com/docs/guides/platform/backups)_