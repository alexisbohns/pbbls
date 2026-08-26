<!-- Kritik issue skeleton for ARC-02 (Code Quality & Architecture) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, db, api + the surface label + milestone. -->

Title: [Quality] RPC-first convention breach: {flow_name} re-implemented client-side on {surface}

Criterion: {criterion_id} {criterion_name}
Surface: {surface}

**Observed level:** {observed_level} ({level_label})
**Target level:** {target_level}

## Evidence
{call_sites_reimplementing_rpc_logic}

## Risk
Divergent client-side implementations of {flow_name} drift apart and bypass in-function ownership checks; the runtime atomicity exposure is tracked under REL-03. {impact_x_likelihood_rationale}

## Remediation
- [ ] Extend RPC {rpc_name} to cover the missing piece
- [ ] Replace the client-side re-implementation with .rpc on {surfaces}
- [ ] Restore payload symmetry between {rpc_name} and its sibling

## Acceptance criteria
- The flow is one RPC call from every surface
- Sibling RPCs are payload-symmetric
- Contributor guidance states the check-then-extend rule

---
_Criterion: **ARC-02 · RPC-first server-side write conventions** (`rpc-convention`) — see [criteria reference](../criteria/index.md)._
_Question: Do all surfaces follow the RPC-first convention for multi-table writes: existing RPCs are reused and extended rather than re-implemented as chained client calls, sibling RPCs stay payload-symmetric, and transaction logic lives server-side by design?_
_References: [PostgreSQL Documentation: Transactions — 3.4. Transactions (atomicity of function bodies)](https://www.postgresql.org/docs/current/tutorial-transactions.html) · [Supabase Docs: Database Functions — Calling functions via RPC](https://supabase.com/docs/guides/database/functions) · [ISO/IEC 25010:2023 Product quality model — Reliability: Recoverability](https://www.iso.org/standard/78176.html)_