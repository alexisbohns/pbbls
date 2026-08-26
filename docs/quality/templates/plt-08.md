<!-- Kritik issue skeleton for PLT-08 (Platform & Store Compliance) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, supabase + the surface label + milestone. -->

Title: [Quality] PLT-08 platform configuration: {gap_summary}

## Criterion
PLT-08 Managed database platform configuration (db-platform)

## Observed level
{observed_level}/4 on `supabase` (target: {target_level}/4)

## Evidence
{evidence_bullets}

## Risk
Impact {impact}/5 x Likelihood {likelihood}/5 = {severity}. Unreviewable console-only configuration and permissive auth defaults undermine the schema-level controls; related mechanism defects belong to SEC-02, SEC-03, or SEC-04.

## Remediation
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}

## Acceptance criteria
- {acceptance_criteria}

---
_Criterion: **PLT-08 · Managed database platform configuration** (`db-platform`) — see [criteria reference](../criteria/index.md)._
_Question: Are the platform's auth, storage, pooling, and API settings deliberate, recorded in tracked config rather than console-only state, protected against drift, and is key issuance and rotation tracked?_
_References: [Supabase: Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security) · [Supabase: Production checklist](https://supabase.com/docs/guides/platform/going-into-prod) · [Supabase: Understanding API keys](https://supabase.com/docs/guides/api/api-keys) · [OWASP Application Security Verification Standard — V4 Access Control](https://owasp.org/www-project-application-security-verification-standard/)_