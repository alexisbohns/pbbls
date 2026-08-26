# Kritik in Arkaik — integration spec

**Status: RFC, written from the Pebbles side.** This document is deliberately portable: it is written so it can be copied to `arkaik/docs/rfcs/kritik.md` unchanged. Everything below was validated against the real Arkaik schema (`@arkaik/schema` at `packages/schema/src/`, bundle format v3) and the real Pebbles bundle (`docs/arkaik/bundle.json`, 460 nodes / 1001 edges).

Kritik turns the quality audit framework ([`framework.md`](./framework.md)) into an Arkaik capability: a versioned **criteria library**, per-project **assessments** rendered as the comparative matrix, **findings** with lifecycle, and a **monitoring loop** driven by journal events — the same append-only spine every other Arkaik feature derives from.

---

## 1. Why Arkaik is the right home

Three structural facts make this a graft, not a bolt-on:

1. **The acceptance species is Kritik's structural twin.** An acceptance is *a testable promise (one Given/When/Then) with per-platform status*, linked by `covers` edges to the views/flows it proves. A criterion assessment is *a testable quality promise with a per-surface score*. The acceptance matrix page, the platform-status rollup machinery, and the delivery board's `(node × platform)` projection are all precedents Kritik reuses — with `(criterion × surface)` cells instead.
2. **The journal is forward-compatible by design.** `JournalEventSchema.type` is an open string ("the v1 vocabulary … or an unknown forward-compatible value"), so `quality.*` events parse, round-trip, and archive correctly in today's Arkaik without any change. Monitoring can start shipping events *before* any UI exists to render them.
3. **Metadata round-trips unknown keys.** `ProjectMetadata` and `NodeMetadata` are `catchall(unknown)` — a `kritik` key survives import/export/Synk/Publik in current Arkaik untouched. Lane 1 below rides entirely on these two guarantees.

## 2. Vocabulary: surfaces ≠ platforms

Arkaik `PLATFORM_IDS = ["web", "ios", "android"]` — where a *view ships*. Kritik audits **surfaces** — independently assessable bodies of code. For Pebbles: `web`, `ios`, `android`, `admin`, `supabase`. The first three map 1:1 onto Arkaik platforms; `admin` and `supabase` do not (an operator back-office and a database contract are audit targets, not user platforms).

**Decision: surfaces are a Kritik-local vocabulary declared in the project's Kritik profile, with an optional `platform` mapping per surface.** `PLATFORM_IDS` is not extended — polluting the view-shipping vocabulary with `supabase` would corrupt delivery boards and platform rollups. Where a surface maps to a platform, Kritik UI can cross-link (e.g. a `SEC` finding on `ios` decorating iOS view variants); unmapped surfaces render only in Kritik pages.

```jsonc
"surfaces": [
  { "id": "web",      "title": "Web app",  "platform": "web" },
  { "id": "ios",      "title": "iOS",      "platform": "ios" },
  { "id": "android",  "title": "Android",  "platform": "android" },
  { "id": "admin",    "title": "Admin back-office" },
  { "id": "supabase", "title": "Database contract" }
]
```

## 3. Lane 1 — today, zero schema change

Everything in this lane works against current Arkaik. Pebbles adopts it in this PR.

### 3.1 The library sidecar

`docs/quality/library/framework.json` in the product repo is the canonical criteria library (scales, domains, criteria, profiles). It is *referenced from*, not embedded in, the bundle:

```jsonc
// project.metadata (catchall-preserved today)
"kritik": {
  "framework": "docs/quality/library/framework.json",
  "framework_version": "0.1.0",
  "last_audit": { "id": "2026-08", "commit": "<sha>", "path": "docs/quality/audits/2026-08/" }
}
```

### 3.2 Journal events (forward-compatible `quality.*` vocabulary)

Envelope identical to every other event (`id` ULID, `ts`, optional `actor`). Proposed v1 grammar:

| Event | Payload (beyond envelope) | Emitted when |
| --- | --- | --- |
| `quality.audit.completed` | `audit_id`, `framework_version`, `commit`, `scores: {surface: {domain: score0to100}}`, `counts: {critical, high, medium, low}` | An audit run lands |
| `quality.finding.opened` | `finding_id`, `criterion_id`, `surface`, `severity`, `priority`, `title`, `node_ids?`, `issue_url?` | A finding is retained (post-verification) |
| `quality.finding.resolved` | `finding_id`, `resolved_by` (PR/commit URL), `node_ids?` | The fix merges |
| `quality.signal.tripped` | `criterion_id`, `surface`, `signal`, `detail` | A monitoring signal fails between audits |

Design rules, matching the existing grammar: events are *facts, not state* (current state is a projection: latest `audit.completed` + open−resolved findings); `node_ids` ties a finding to the product graph exactly as `deliverable.shipped` already does; payloads stay small (full evidence lives in the audit files, the event carries identity + severity).

### 3.3 Graph linkage without new species

A finding that concerns a mappable feature attaches to its node(s) two existing ways: `node_ids` on the events (above) and, once filed as a GitHub issue, a `refs` entry (`type: "github-issue"`) on the node — both fully supported today. The rendered effect in current Arkaik: the issue appears on the node's references; the quality events sit in the raw history. Silent, lossless, upgrade-ready.

### 3.4 Monitoring loop (works with today's tooling)

1. **Per-audit:** an agent army runs the framework (as in `audits/2026-08/`), writes `scores.json`/`findings.json`, appends `quality.audit.completed` + one `quality.finding.opened` per retained finding to `docs/arkaik/journal.jsonl`, and files issues from the templates.
2. **Between audits:** each criterion's `signals[]` is mechanically checkable (greps that must return nothing, CI jobs that must exist). A scheduled job (CI cron or a Claude routine) runs the signal pack and appends `quality.signal.tripped` on regressions.
3. **On merge:** the existing Arkaik GitHub App webhook already appends `deliverable.shipped` per merged PR; a PR closing a quality issue appends `quality.finding.resolved` (lane 1: a tiny workflow step greps the PR body for `finding_id`; lane 2: the webhook grows native support).

## 4. Lane 2 — first-class Kritik (the Arkaik feature)

### 4.1 Data model: a `quality` bundle section, not a seventh species

Criteria and assessments must not become graph nodes: 75 criteria × surfaces would drown a 460-node product graph, and criteria are *library* content (project-independent), which the bundle already models precedent for — `maps` and `products` live in `project.metadata`, not as nodes. Following that precedent, plus one top-level section for project-owned quality state:

```ts
// packages/schema/src/quality.ts (new, additive — schema_version stays 3;
// older readers preserve the key via catchall)
interface QualityAssessment {
  criterion_id: string;          // "SEC-01" — resolved against the imported library
  surface: string;               // profile surface id
  level: 0 | 1 | 2 | 3 | 4;      // maturity; N/A = row absent
  evidence: string;              // file:line / config citations, markdown
  audit_id: string;              // "2026-08"
  commit?: string;
  ts: string;                    // ISO 8601
}
interface QualityFinding {
  id: string;                    // "F-2026-08-SEC-web-01"
  criterion_id: string; surface: string;
  title: string; detail: string; evidence: string;
  impact: 1|2|3|4|5; likelihood: 1|2|3|4|5;   // severity/priority are DERIVED
  cost: "S"|"M"|"L"|"XL";
  status: "open" | "resolved" | "refuted" | "accepted-risk";
  node_ids?: string[]; issue_url?: string;
  verification?: { verdict: "CONFIRMED"|"REFUTED"|"DOWNGRADED"; note: string };
}
interface QualitySection {
  framework_version: string;
  library?: KritikLibrary;       // embedded on export; repos may keep it as a sidecar
  profile: { surfaces: SurfaceDef[]; domain_weights: Record<string, number> };
  assessments: QualityAssessment[];   // latest per (criterion × surface); history in journal
  findings: QualityFinding[];
}
// ProjectBundle gains: quality?: QualitySection
```

Derived values (severity buckets, P0–P3, domain scores, grades, caps) are **projections in `@arkaik/schema`** (`deriveQualityMatrix(bundle)`), exactly as delivery/backlog are journal projections today — stored data stays minimal and un-fake-able.

### 4.2 The library as a distributable pack

The criteria library ships like the seed example ships: a versioned JSON pack (`@arkaik/kritik-library`, or `arkaik kritik init` fetching a starter pack). The library built here from Pebbles — 11 domains, product-agnostic wording, stack-class calibrated — is the first pack. Projects pin a pack version; criteria are append-and-supersede (`superseded_by`), so matrices stay comparable across audits.

### 4.3 UI

- **Quality page** (sibling of Delivery/Acceptances): the comparative matrix — rows domains, columns profile surfaces, cells `score (grade)` colored by band, cap asterisks, trend arrows vs the previous `quality.audit.completed`. Click a cell → criterion list with per-criterion levels; click a criterion → detail panel (question, anchors, references, checklist, evidence) — the same panel-stack pattern as node details.
- **Findings board**: findings by priority lane (P0–P3), severity chips, linked nodes, issue refs; `accepted-risk` requires a note (rendered as a decision-log-style entry).
- **Node decoration**: a node with open findings shows a severity badge (as `blocked_by` badges today); the overview dashboard gains a quality gauge per surface next to the existing coverage gauges.

### 4.4 CLI / MCP (the agent loop)

```
arkaik kritik score   <criterion> <surface> <level> --evidence <text|file>
arkaik kritik finding open|resolve|accept …
arkaik kritik matrix  [--json]          # derived matrix, CI-friendly
arkaik kritik signals [--surface s]     # run the library's signal pack → exit code
arkaik kritik issue   <criterion> --surface s   # emit prefilled issue markdown
```

MCP tools mirror these (`kritik_score`, `kritik_open_finding`, …) through the same validated mutation path as existing tools — which is what makes **scheduled agent audits** a first-class monitoring feature: a routine wakes, runs `kritik signals`, audits deltas since the last commit audited, scores through MCP, and the journal accumulates the quality history that the UI renders as trends.

### 4.5 Validation rules (additive)

`validateBundle` gains warnings (never import-blocking, per Arkaik's leniency doctrine): unknown `criterion_id` against the pinned library; assessment surface absent from the profile; finding with `status: open` older than N audits; `quality.*` event whose `finding_id` never appeared in a `finding.opened`.

## 5. Adoption path

| Step | Where | What |
| --- | --- | --- |
| 1 (this PR) | pbbls | Library + first audit + templates under `docs/quality/`; `quality.*` events appended to `docs/arkaik/journal.jsonl`; `kritik` key in bundle `project.metadata` |
| 2 | arkaik | Copy this file to `docs/rfcs/kritik.md`; land `quality.ts` + projections in `@arkaik/schema` (additive) |
| 3 | arkaik | Quality page + findings board reading the new section; seed pack from the Pebbles library |
| 4 | arkaik | CLI/MCP verbs; webhook grows `quality.finding.resolved`; signal-pack runner |
| 5 | pbbls | Switch from sidecar-only to the first-class section; delete nothing (the journal already carries history) |

## 6. Open questions (for the Arkaik side)

1. **Library governance** — one canonical pack evolving by PR, or per-org forks? (Recommendation: canonical pack + profile-level overrides, mirroring how `products` stay project-local.)
2. **Score authority** — should MCP-written scores require an `actor` distinguishing human/agent/CI, so trends can be filtered by assessor kind? (Recommendation: yes; the envelope's `actor` field already exists.)
3. **Publik exposure** — are quality matrices part of a public snapshot? (Recommendation: excluded by default; a security matrix is a roadmap for attackers. Needs an explicit opt-in flag.)
4. **Cross-project rollup** — Ariko-level aggregation (a portfolio quality pulse over the pollen feed) is attractive but out of scope for v1; `quality.audit.completed` is deliberately shaped to be feed-summarizable later.
