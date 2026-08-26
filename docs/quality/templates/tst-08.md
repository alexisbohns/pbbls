<!-- Kritik issue skeleton for TST-08 (Testing & Verification) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, auth, supabase + the surface label + milestone. -->

Title: [Quality] TST-08 negative authorization tests: {gap_summary}

**Criterion:** TST-08 Negative authorization tests in CI
**Surface:** supabase
**Observed level:** {observed_level} ({observed_summary})
**Target level:** {target_level}

### Evidence
{evidence_bullets} (e.g. {table_name} has no cross-user denial test; {rpc_name} is never called as a non-owner)

### Risk
Impact {impact} x Likelihood {likelihood}: {risk_narrative} (RLS is the sole isolation boundary between users; an unproven policy is one refactor away from a breach)

### Remediation
- [ ] Add denial tests for {table_or_rpc_list}
- [ ] Add the schema-diff completeness check
- [ ] Wire the suite into CI on migration changes

### Acceptance criteria
- Cross-user and anon access denied on every application table, asserted by the suite
- Completeness check green against the current schema
- CI gate runs on migration paths

---
_Criterion: **TST-08 · Negative authorization tests in CI** (`authz-tests`) — see [criteria reference](../criteria/index.md)._
_Question: Does automated proof exist, kept current in CI, that RLS policies and security-definer RPCs deny cross-user reads and writes on every application table and RPC?_
_References: [Supabase Docs: Testing your database](https://supabase.com/docs/guides/database/testing) · [pgTAP: Unit testing for PostgreSQL](https://pgtap.org/) · [OWASP Top 10:2021 — A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/) · [CWE-862 Missing Authorization](https://cwe.mitre.org/data/definitions/862.html)_