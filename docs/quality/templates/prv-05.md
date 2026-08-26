<!-- Kritik issue skeleton for PRV-05 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, db + the surface label + milestone. -->

Title: [Quality] PRV-05 {surface}: media privacy gap ({finding_summary})

**Criterion**: PRV-05 Private media: EXIF, signed URLs, cache lifetime (PRV/media)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{evidence_bucket_policy_url_or_exif_findings}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {make_bucket_private_or_fix_policy}
- [ ] {strip_metadata_on_upload_path}
- [ ] {align_signed_url_ttl_and_cache_lifetime}
- [ ] {add_exif_fixture_and_cross_user_access_test}

### Acceptance criteria
- No public URL path exists for user media on {surface}
- Uploaded media carries no GPS metadata, proven by fixture test
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-05 · Private media: EXIF, signed URLs, cache lifetime** (`media`) — see [criteria reference](../criteria/index.md)._
_Question: Are user photos private by default: metadata-stripped on upload, served only through short-lived signed URLs from non-public storage, and cached no longer than their access grant?_
_References: [Supabase Storage: Access Control — Storage security and access control](https://supabase.com/docs/guides/storage/security/access-control) · [CWE-732: Incorrect Permission Assignment for Critical Resource — CWE-732](https://cwe.mitre.org/data/definitions/732.html) · [GDPR — Art. 5(1)(c) data minimisation and Art. 32 security of processing](https://eur-lex.europa.eu/eli/reg/2016/679/oj) · [OWASP Top 10:2021 — A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)_