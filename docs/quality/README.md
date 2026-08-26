# docs/quality — the Kritik quality layer

Start here. This directory holds the **Kritik quality audit framework** and its audit runs for Pebbles — five surfaces (web, iOS, Android, admin, Supabase) scored on eleven domains with one comparable scale.

| You want… | Read |
| --- | --- |
| The framework itself: entities, scales (maturity 0–4, risk 5×5, cost S–XL, priority P0–P3), roll-up rules, audit process | [`framework.md`](./framework.md) |
| The latest audit: executive summary + the comparative matrix + top findings | [`audits/2026-08/README.md`](./audits/2026-08/README.md) |
| One surface's detailed report | `audits/2026-08/<surface>.md` (plus `cross-surface.md` for contract findings) |
| The criteria, human-readable, one file per domain | [`criteria/index.md`](./criteria/index.md) |
| The criteria, machine-readable (the modelable artifact) | [`library/framework.json`](./library/framework.json) |
| How this becomes an Arkaik feature (data model, `quality.*` journal events, monitoring loop) | [`arkaik-integration.md`](./arkaik-integration.md) |
| Issue skeletons per criterion (plus the generic form in `.github/ISSUE_TEMPLATE/quality-finding.yml`) | `templates/` |

## The 30-second version

- A **criterion** (e.g. `SEC-01`) is one auditable question with observable **maturity anchors 0–4** (0 absent → 4 enforced-by-automation), references to real standards, an executable checklist, monitoring **signals**, and an issue template.
- An **audit** scores every applicable (criterion × surface) cell with `file:line` evidence pinned to a commit, emits **findings** (severity = impact × likelihood; cost S–XL; priority P0–P3), and adversarially verifies every Critical/High finding before retaining it.
- Scores roll up to **domain grades per surface** (A–E, with caps: an open Critical caps the domain at D) and to the **comparative matrix** — the one-page answer to "how good is each surface, where, and what do we fix first?"
- Everything is a projection of `scores.json` + `findings.json` + `library/framework.json`; the markdown is generated from the data, never the other way around.

## Maintaining this directory

- **New audit** → new `audits/<YYYY-MM>/` folder; never edit an old audit (snapshots are immutable, like `docs/decisions/log.md` entries).
- **Changing a criterion** → supersede, never redefine: deprecate the id with `superseded_by`, add a new id, bump the framework version (`framework.md` §8).
- **Between audits** → criteria `signals` are the monitoring layer; regressions surface as `quality.signal.tripped` journal events (`arkaik-integration.md` §3.4).
