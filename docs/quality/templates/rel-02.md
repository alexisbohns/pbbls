<!-- Kritik issue skeleton for REL-02 (Reliability & Observability) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, api, {surface} + the surface label + milestone. -->

Title: [Quality] REL-02 {surface}: unbounded network calls on {flow} can hang or retry-storm

**Criterion:** REL-02 Bounded timeouts and deliberate retries
**Surface:** {surface}
**Observed level:** {observed_level}/4 (target {target_level}/4)
**Severity:** impact {impact} x likelihood {likelihood}

## Evidence
{unwrapped_call_sites_with_paths}

## Risk
{risk_narrative}

## Remediation
- [ ] {wrap_or_configure_timeouts_for_listed_sites}
- [ ] {document_retry_policy}
- [ ] {add_ci_grep_guard}

## Acceptance criteria
- [ ] No blocking call site bypasses the timeout mechanism (CI grep green)
- [ ] Timeout errors log an operation label
- [ ] Retries are bounded with backoff and only on idempotent operations

---
_Criterion: **REL-02 · Bounded timeouts and deliberate retries** (`timeouts-retries`) — see [criteria reference](../criteria/index.md)._
_Question: Does every network call that can block a user or a job carry an explicit, labeled timeout, and is every retry bounded, backed off, and applied only to idempotent operations?_
_References: [Amazon Builders' Library: Timeouts, retries, and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/) · [Google SRE Book — Chapter 22: Addressing Cascading Failures](https://sre.google/sre-book/addressing-cascading-failures/) · [CWE-400: Uncontrolled Resource Consumption — CWE-400](https://cwe.mitre.org/data/definitions/400.html)_