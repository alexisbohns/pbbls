<!-- Kritik issue skeleton for PRV-08 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, db + the surface label + milestone. -->

Title: [Quality] PRV-08 {surface}: deletion does not propagate to {store_or_table}

**Criterion**: PRV-08 Deletion propagation and purge completeness (PRV/deletion)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{missed_tables_objects_or_caches_with_refs}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {add_missed_store_to_purge_routine}
- [ ] {seed_and_assert_it_in_the_harness}
- [ ] {verify_idempotent_convergence_and_client_cache_clear}

### Acceptance criteria
- Purge covers every user-owned store including {store_or_table}, proven by the harness
- Re-run after partial failure converges to zero counts
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-08 · Deletion propagation and purge completeness** (`deletion`) — see [criteria reference](../criteria/index.md)._
_Question: Does account and item deletion propagate to every store holding the user's data (rows, storage objects, auth identity, caches, derived data), with a runnable proof of completeness that new tables cannot silently escape?_
_References: [GDPR — Art. 17 (right to erasure)](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [Apple App Store Review Guidelines — Guideline 5.1.1(v) (account deletion)](https://developer.apple.com/app-store/review/guidelines/) · [Google Play Developer Content Policy — User Data policy (account and data deletion requirement)](https://play.google/developer-content-policy/) · [CWE-459: Incomplete Cleanup — CWE-459](https://cwe.mitre.org/data/definitions/459.html)_