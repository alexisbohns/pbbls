<!-- Kritik issue skeleton for PRF-03 (Performance & Efficiency) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, ui, core + the surface label + milestone. -->

Title: [Quality] PRF-03 {surface}: media pipeline gap ({path} fetches {actual} for a {slot} slot)

## Quality finding: PRF-03 Image and media delivery pipeline

**Surface:** {surface}
**Observed level:** {observed_level}/4 | **Target level:** {target_level}/4

### Evidence
{evidence}

### Risk
{risk}

### Remediation checklist
- [ ] {remediation_step_1}
- [ ] {remediation_step_2}
- [ ] {remediation_step_3}

### Acceptance criteria
- {acceptance_criterion_1}
- {acceptance_criterion_2}

---
_Criterion: **PRF-03 · Image and media delivery pipeline** (`media-pipeline`) — see [criteria reference](../criteria/index.md)._
_Question: Is every media render path served an appropriately sized, modern-format, lazily loaded variant through cacheable URLs, instead of full-size originals behind per-render regenerated links?_
_References: [Serve responsive images — Resizing and srcset/sizes](https://web.dev/articles/serve-responsive-images) · [Browser-level image lazy loading — loading="lazy" semantics](https://web.dev/articles/browser-level-image-lazy-loading) · [Supabase Storage image transformations — Transforming images on the fly (width, quality)](https://supabase.com/docs/guides/storage/serving/image-transformations) · [Supabase Storage CDN fundamentals — Cache control and CDN caching of assets](https://supabase.com/docs/guides/storage/cdn/fundamentals)_