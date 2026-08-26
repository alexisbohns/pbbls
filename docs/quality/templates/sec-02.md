<!-- Kritik issue skeleton for SEC-02 (Security) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, auth, supabase + the surface label + milestone. -->

Title: [Quality] SEC-02 RLS default-deny at level {observed_level} on supabase (target {target_level})

## Quality finding: SEC-02 Row-Level Security default-deny

**Surface:** supabase
**Observed level:** {observed_level} ({observed_level_name})
**Target level:** {target_level} ({target_level_name})
**Severity seed:** impact {impact} x likelihood {likelihood}

### Evidence
{tables_or_policies_with_migration_paths}

### Risk
{risk_narrative}

### Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}
- [ ] Add/extend the RLS coverage assertion harness

### Acceptance criteria
- [ ] Cross-user read/write attempts fail for {affected_tables}
- [ ] Target anchor holds: {target_anchor_text}

---
_Criterion: **SEC-02 · Row-Level Security default-deny on every table** (`authz`) — see [criteria reference](../criteria/index.md)._
_Question: Is Row-Level Security enabled and default-deny on every application table and storage bucket, with policies scoped to the authenticated user?_
_References: [Supabase Docs: Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security) · [PostgreSQL Documentation: CREATE POLICY](https://www.postgresql.org/docs/current/sql-createpolicy.html) · [OWASP Top 10:2021 — A01 Broken Access Control](https://owasp.org/Top10/) · [CWE-862 Missing Authorization](https://cwe.mitre.org/data/definitions/862.html) · [GDPR — Art. 32 Security of processing](https://eur-lex.europa.eu/eli/reg/2016/679/oj)_