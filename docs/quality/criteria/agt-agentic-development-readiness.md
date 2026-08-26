# AGT — Agentic Development Readiness

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Instruction-doc accuracy and token economy, product-map freshness, agent-runnable verification loops, encoded guardrails, determinism, machine-checkable conventions, automation credential scoping, decision memory.

---

## AGT-01 · Layered agent instruction docs, accurate and lean

**Are agent instruction files (root and per-workspace) accurate against the current tree, layered so detail loads on demand, and governed by an explicit edit and promotion policy?**

`instruction-docs` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **3/3**

The repo carries agent instruction files (CLAUDE.md, AGENTS.md, or equivalents) at the root and in each workspace. Every command, path, port, and factual claim they state is verifiably true against the current code. Always-loaded files hold only durable, action-guiding rules; detailed knowledge is split into on-demand documents the instruction files point to. A written policy states when learnings get promoted into these files and at what scope, so they stay lean instead of silting up.

*Why it matters:* Agents obey instruction files ahead of their own judgment, so one stale command or dead path misroutes every future session identically. A bloated always-loaded file also taxes every session's context window, which is the scarcest resource agentic work has.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No agent instruction files exist, or a single generated boilerplate file untouched since initialization. |
| **1 · Ad-hoc** | An instruction file exists but spot-checks find stale commands, dead paths, or rules contradicting current code; no per-workspace layering. |
| **2 · Defined** | Root and workspace instruction files exist and are broadly accurate; some deep detail still lives inline instead of in pointed-to on-demand docs, and no written policy says when or how the files get edited. |
| **3 · Managed** | Layered files verify accurate on spot-check; always-loaded content is limited to durable action-guiding rules with pointers to on-demand docs; the files themselves state a promotion bar, a cadence, and scope placement rules for edits. |
| **4 · Verified** | Accuracy is mechanically checked: a CI job or scheduled audit resolves every path, command, and workspace name referenced in the instruction files, and drift produces a failing check or an auto-filed issue rather than waiting to be noticed. |

### Audit checklist

- [ ] Enumerate instruction files: ls CLAUDE.md AGENTS.md apps/*/CLAUDE.md packages/*/CLAUDE.md (or whatever the agent runtime auto-loads); note any source-bearing workspace missing one.
- [ ] Execute every command the root instruction file names (workspace-scoped lint/test/build, db:types and similar) and confirm each exists in the corresponding package.json, turbo.json, or Gradle/Xcode task list.
- [ ] Grep instruction files for repo paths (docs/, scripts/, .claude/skills/, .agents/) and stat each one; every referenced path must exist.
- [ ] Verify layering: the root file should point to on-demand docs (e.g. docs/agents/*.md, skill directories) instead of inlining surface detail; compare root file size against the detail docs it points to.
- [ ] Look for an explicit editing policy inside the files themselves: promotion bar (durable + action-guiding or equivalent), cadence, and which scope (root vs workspace file) a rule lands at.
- [ ] Cross-check stated cross-surface facts (mirroring rules, port numbers, minSdk, framework versions) against the actual configs: package.json, project.yml, build.gradle.kts, Info.plist.

### Monitoring signals

- CI or scheduled script resolves every path and command referenced in CLAUDE.md/AGENTS.md and fails on a dead reference
- Root instruction file stays under an agreed line budget (e.g. wc -l CLAUDE.md < 300) via a lint check
- find apps packages -maxdepth 2 -name CLAUDE.md returns one file per source-bearing workspace

### References

- [Claude Code Best Practices (Anthropic Engineering) — Create CLAUDE.md files; Tune your CLAUDE.md files](https://www.anthropic.com/engineering/claude-code-best-practices)
- [AGENTS.md open format for agent instructions — Format specification](https://agents.md)

### Typical remediation

Audit each instruction file against the tree, fix or delete stale statements, split detail into on-demand docs with pointers from the root file, write the promotion policy into the file itself, and add a periodic or CI-run reference-resolution check.

*Issue skeleton:* [`templates/agt-01.md`](../templates/agt-01.md)

---

## AGT-02 · Product map freshness with drift gates

**Does a machine-readable product map (screens, flows, data models, endpoints) exist with a defined update trigger for agents and a CI gate that rejects an invalid or contradicted map?**

`map-freshness` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **2/3**

A machine-readable map of the product (views, routes, data models, API/RPC endpoints, and the edges between them) is versioned in the repo alongside an append-only change journal. Agent-facing instructions state exactly which code changes require a map update, in the same PR. A CI gate validates the map's schema and its consistency with the journal on every change to it, so structural drift fails a check instead of accumulating silently.

*Why it matters:* Agents navigate by the map before they grep, so a stale map misroutes every session. Without a gate the map decays precisely because updating it is the step agents and humans skip under pressure.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No machine-readable product map exists; architecture knowledge lives only in code and in heads. |
| **1 · Ad-hoc** | A map or diagram exists but is hand-drawn or visibly stale; nothing tells an agent when to update it and nothing validates it. |
| **2 · Defined** | A structured map file is versioned and instructions name the update triggers, but freshness relies on discipline alone; no validator script and no CI gate. |
| **3 · Managed** | Map plus append-only journal are maintained through a documented workflow (e.g. a repo skill with auto-apply trigger language); spot-checks show recent structural changes reflected; a validator script exists and is run manually. |
| **4 · Verified** | CI validates the map and journal on every change to their paths, the update trigger is encoded in always-loaded agent instructions or an auto-triggering skill, and sampled recent PRs that changed routes, models, or endpoints show matching map updates in the same PR. |

### Audit checklist

- [ ] Locate the map: look for a product-graph bundle such as docs/arkaik/bundle.json with a sidecar journal.jsonl, or grep the repo for ProjectBundle-style JSON describing screens/routes/models/endpoints.
- [ ] Open .github/workflows/ and confirm a job triggers on pull_request for the map's paths and runs a validator; read the validator script to see what it actually checks (schema, journal consistency, or both).
- [ ] Diff the map against reality: pick three recently added or renamed routes/screens (git log --diff-filter=AR on Next.js app/ routes, SwiftUI views, Compose screens) and confirm each appears in the map with current naming.
- [ ] Confirm the update trigger is written where agents will load it: root instruction file task-size rules and/or a skill whose description says it applies even when nobody asked.
- [ ] Check the journal is append-only JSONL and its newest events correspond to the newest snapshot state (no snapshot change without a journal event).

### Monitoring signals

- CI workflow with paths filter on the map directory invoking the bundle validator exists (grep the workflows for the validator script path)
- Validator exits 0 against the committed bundle when run locally
- Sampled PRs touching route/view/model files include a sibling change under the map directory (near-100 percent for structural changes)

### References

- [C4 model for visualising software architecture — System Context and Container diagrams](https://c4model.com)
- ISO/IEC/IEEE 42010 Systems and software engineering, Architecture description — Architecture description practices

### Typical remediation

Adopt a structured map format with an append-only journal, encode the update trigger in always-loaded instructions or an auto-applying skill, and add a CI job that validates schema plus journal consistency on every map-touching change.

*Issue skeleton:* [`templates/agt-02.md`](../templates/agt-02.md)

---

## AGT-03 · Provable changes: fast agent verification loops

**Can an agent prove any change it makes by running fast, deterministic, workspace-scoped lint, test, and build loops headlessly, with every required proof documented as a single command?**

`verifiability` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **3/3**

Every surface exposes one-command, workspace-scoped lint, test, and build entry points that run headlessly and deterministically in an agent environment, complete within a small time budget, and degrade gracefully when a host dependency (SDK, simulator, Docker) is absent. Written guidance maps task size to the required verification scope, and every proof an agent is expected to supply, including the cross-surface contract harnesses whose existence and coverage TST-02 and TST-04 audit, is invocable as a single documented command from the agent environment.

*Why it matters:* An agent that cannot cheaply run a proof either skips verification or burns the session on full builds. Whether the proofs exist is TST-02 and TST-04's question; whether an agent can actually execute them, quickly and deterministically, is what decides whether they get run at all.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No runnable tests or lint on the surface; the only check is whether it compiles, and possibly not even that in a headless agent environment. |
| **1 · Ad-hoc** | Checks exist but only through a global, slow, or environment-fragile invocation that an agent cannot reliably run headlessly. |
| **2 · Defined** | Workspace-scoped lint and test commands are defined and documented, and guidance says which scope to run for which task size, but some checks depend on absent host tooling without graceful degradation, or harness invocations are undocumented. |
| **3 · Managed** | Scoped loops run green headlessly within minutes, every required proof (contract harnesses included) is a single documented command that works in the agent environment, and instructions state when each must be run. |
| **4 · Verified** | CI runs the scoped checks on matching paths, loop runtime is kept within a stated budget, and the documented proof commands are exercised regularly enough that environment drift breaking an agent loop is caught rather than discovered mid-task. |

### Audit checklist

- [ ] Enumerate verification entry points per workspace from package.json scripts and turbo.json (or Gradle/xcodebuild tasks); run the scoped lint and test for one workspace and time them.
- [ ] Confirm the loops run headlessly: no hard dependency on a simulator, emulator, attached device, or Docker daemon, or a wrapper that degrades gracefully when the dependency is absent (gradle-if-sdk style scripts).
- [ ] Attempt each documented harness invocation from the agent environment (credentials via runtime lookup, not interactive prompts); an existing harness an agent cannot run headlessly is a finding here, even though its existence and coverage belong to TST-02/TST-04.
- [ ] Check task-size-to-verification-scope guidance exists in agent instructions (which commands suffice for a small change, what a cross-surface change requires).
- [ ] Inspect CI workflows for path-scoped triggers so a surface's changes run that surface's checks (e.g. an android job triggered on apps/android/**).

### Monitoring signals

- Workspace-scoped lint plus test completes under an agreed budget (e.g. 3 minutes) in the agent environment
- Every proof named in agent instructions is a single command that runs headlessly and exits nonzero on failure
- Every client surface has a CI workflow or job with a paths filter matching its directory

### References

- [Self Testing Code (Martin Fowler) — Self-testing code definition](https://martinfowler.com/bliki/SelfTestingCode.html)
- [Consumer-Driven Contracts: A Service Evolution Pattern (Ian Robinson) — Consumer-driven contracts](https://martinfowler.com/articles/consumerDrivenContracts.html)
- ISO/IEC 25010 Software product quality model — Maintainability: Testability

### Typical remediation

Add one-command workspace-scoped lint/test entries and make them environment tolerant (skip-with-notice wrappers for absent SDKs), document each required proof as a single command in the guidance agents load, and bind scoped checks to CI path triggers. Missing or shallow harnesses are filed under TST-02 or TST-04.

*Issue skeleton:* [`templates/agt-03.md`](../templates/agt-03.md)

---

## AGT-04 · Dangerous operations flagged where agents read

**Are known footguns and irreversible operations encoded as explicit standing rules, with trigger, required action, and proof, in the instruction files every agent session loads?**

`guardrails` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

Operations that can silently corrupt shared state (function re-emissions that clobber each other's appends, destructive routines whose coverage must be extended in lockstep with a harness, catalogs that drift unless resynced in the same transaction) are written as short imperative standing rules in always-loaded instruction files. Each rule names its trigger condition, the required action, and where possible the harness or check that proves compliance. Hazards are promoted into rules when discovered, not after they bite twice.

*Why it matters:* Agents carry no institutional scar tissue between sessions; a hazard that lives only in a postmortem, a commit message, or a maintainer's memory will be re-triggered by the next agent that touches the same code. In a shared-database product these hazards are data-loss class, not style class.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No written hazard rules; known footguns live in commit messages, chat history, or nowhere. |
| **1 · Ad-hoc** | Warnings exist but only as code comments or deep docs an agent will not have loaded before acting. |
| **2 · Defined** | A standing-rules section exists in always-loaded instructions covering some hazards, but rules lack trigger conditions or the required countermeasure, and some known incidents never became rules. |
| **3 · Managed** | Each standing rule states trigger, action, and proof; rules sit at the scope where the hazard occurs (root file for cross-surface, workspace file for local); recent incident-derived hazards are all present as rules. |
| **4 · Verified** | The highest-impact rules are backed by automation: a lint, CI check, or harness detects violation of the guarded invariant (e.g. destructive-routine coverage diffed against its harness), so the written rule is a backstop rather than the only defense. |

### Audit checklist

- [ ] Read the always-loaded instruction files for a standing-rules section; for each rule verify it names a trigger condition, a required action, and a way to verify compliance.
- [ ] Grep migrations for repeated whole-body re-emissions of the same function (grep -l 'create or replace function public.<name>' across supabase/migrations/) and check a rule warns that re-emissions silently drop each other's appends with no git conflict.
- [ ] Identify destructive or cascading routines (account purge, connection removal, catalog sync) and confirm each has a written lockstep rule tying schema changes to seed plus assertion updates in a named harness.
- [ ] Trace one past incident or lessons-learned entry (plan post-mortems, decision log) and verify the hazard it describes was promoted into a rule placed where agents load it.
- [ ] Check scope placement: cross-surface hazards in the root file, surface-local hazards in the workspace file, not the reverse.

### Monitoring signals

- Standing-rules section present in the root instruction file (grep -i 'standing' CLAUDE.md returns a section heading)
- Script or CI check diffs guarded-routine coverage against its harness (table list in the purge function vs harness zero-row assertions)
- No migration inserts into a guarded reference table without invoking its companion resync in the same file (grep pairs return matched counts)

### References

- [Claude Code Best Practices (Anthropic Engineering) — Tune your CLAUDE.md files](https://www.anthropic.com/engineering/claude-code-best-practices)
- [PostgreSQL documentation: CREATE FUNCTION — CREATE OR REPLACE FUNCTION replacement semantics](https://www.postgresql.org/docs/current/sql-createfunction.html)

### Typical remediation

Inventory hazards from incidents, lessons-learned sections, and destructive routines; write each as a trigger-action-proof rule at the correct scope in always-loaded instructions; automate detection for the highest-impact invariants.

*Issue skeleton:* [`templates/agt-04.md`](../templates/agt-04.md)

---

## AGT-05 · Scripts over tribal knowledge

**Is every routine operation (build, test, typegen, database workflows, release) a single committed script or task entry that runs deterministically in the agent environment, rather than a sequence a human must remember?**

`determinism` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **2/3**

Routine operations are invocable as one documented command backed by a committed script or task-runner entry, including generated-artifact refresh (schema types), database workflows, and release paths. Environment-sensitive scripts declare their preconditions and degrade gracefully when a dependency (SDK, emulator, Docker) is absent, instead of failing cryptically. CI invokes the same committed entry points rather than reimplementing them inline, so local and CI behavior cannot diverge.

*Why it matters:* Agents can only execute what is written; every undocumented manual step is a fork where the agent guesses, and guessed operations against a shared database or a store release are how automation causes real damage.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Core workflows require undocumented manual steps; committed scripts do not run as checked in. |
| **1 · Ad-hoc** | Some task entries exist but key workflows (type generation, migrations, deploy) are performed by hand from memory or chat logs. |
| **2 · Defined** | Most workflows have committed entry points and docs name them, but some scripts fail hard on missing environment pieces and a few steps remain prose-only. |
| **3 · Managed** | All routine workflows are single commands; environment-sensitive ones are wrapped to no-op or explain themselves when prerequisites are missing; generated artifacts have a scripted refresh documented next to the rule that mandates committing the output. |
| **4 · Verified** | CI executes the same committed entry points (no CI-only inline shell duplicating local workflows), and a check detects drift between generated artifacts and their source, for example schema types regenerated in CI and diffed against the committed file. |

### Audit checklist

- [ ] Inventory entry points: read root and workspace package.json scripts, turbo.json, and scripts/ directories; list every operation mentioned in docs that has no corresponding command.
- [ ] Run each entry point in the agent environment; note which fail on a missing SDK, Docker daemon, or simulator, and whether a graceful wrapper exists (a gradle-if-sdk style guard that no-ops with a message).
- [ ] Verify generated artifacts have a scripted refresh: schema type generation (db:types) exists as a task, and instructions mandate regenerating and committing the output after migrations.
- [ ] Compare CI workflow steps to local scripts: CI should invoke the same npm/gradle/xcodebuild tasks, not reimplement multi-line inline shell versions of them.
- [ ] Search docs for imperative prose sequences ('then run', 'manually', 'by hand') describing multi-step operations that lack a script, and list them as candidates.

### Monitoring signals

- CI regenerates schema types and git diff --exit-code on the generated file passes
- Workflow yml steps invoke committed tasks or scripts; grep for multi-line inline run: blocks duplicating local commands returns nothing
- Every doc-described workflow resolves to a runnable command (scripted-coverage list has zero prose-only entries)

### References

- [Scripts to Rule Them All (GitHub Engineering) — Normalized script pattern](https://github.blog/2015-06-30-scripts-to-rule-them-all/)
- [The Twelve-Factor App — Factor X: Dev/prod parity](https://12factor.net/dev-prod-parity)
- [Site Reliability Engineering: Eliminating Toil (Google) — Chapter 5](https://sre.google/sre-book/eliminating-toil/)

### Typical remediation

Promote each prose workflow into a committed script or task entry, wrap environment-dependent ones to degrade gracefully with a message, and point CI at the same entry points so local and CI behavior stay identical.

*Issue skeleton:* [`templates/agt-05.md`](../templates/agt-05.md)

---

## AGT-06 · Machine-checkable contribution conventions

**Are commit, branch, PR, label, and structured PR-body conventions specified as exact grammars, and are the highest-value ones checked by automation at PR time?**

`conventions` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **1/3**

Contribution conventions (commit message format, branch naming, PR title and body structure, label taxonomy, and any structured PR-body blocks that downstream automation parses) are written as exact grammars with examples in agent-loaded docs. The machine-checkable subset is enforced or advised by CI or bots at PR-open time, and structured blocks parsed on merge (changelog or release-note YAML) are validated before merge so a malformed block is caught while the author can still fix it. A documented escape hatch exists for legitimate exceptions.

*Why it matters:* Agents follow written grammars far more reliably than vibes, and machine-parsed PR content that fails silently at merge time poisons downstream automation with no feedback loop for the author.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No written conventions; commit and PR style is whatever each author produced. |
| **1 · Ad-hoc** | Conventions are described loosely in a doc; formats are not precise enough to lint and nothing checks them. |
| **2 · Defined** | Exact formats with examples are written in agent-loaded docs (types, scopes, branch grammar, label taxonomy) but compliance is checked only by human review. |
| **3 · Managed** | An advisory bot or CI check surfaces violations at PR-open time (malformed structured blocks get a comment with the specific problem), and a documented escape-hatch label exists for legitimate exceptions. |
| **4 · Verified** | CI enforces the machine-checkable subset as required checks (commit lint, branch-name check, PR-title lint, structured-block schema validation), and a parse failure of any machine-read block blocks or loudly flags the merge. |

### Audit checklist

- [ ] Read the conventions section of the agent instructions: verify commit types, scopes, branch grammar, label taxonomy, and PR checklist are enumerated exactly, with examples.
- [ ] Open .github/workflows/ and identify PR-time validators: title lint, commit lint, and any advisory workflow that checks structured PR-body blocks (changelog or release-note YAML) at open/edit time; read what each validates.
- [ ] Sample the last 10 commits (git log --oneline -10) and 5 recent PRs against the written grammar; count and record violations.
- [ ] If PR bodies carry machine-read blocks parsed on merge (YAML fences consumed by a webhook), locate the parser and confirm a pre-merge advisory or validation exists for malformed blocks, including the known YAML failure modes (unquoted colons).
- [ ] Verify the escape hatch: a documented label or marker that legitimately silences the advisory, documented where agents read.

### Monitoring signals

- A commit-lint or PR-title-lint workflow exists in .github/workflows
- An advisory workflow triggers on pull_request opened/edited for structured PR-body blocks
- Sampled commit conformance to the written grammar is above an agreed threshold (e.g. 95 percent)

### References

- [Conventional Commits — v1.0.0 specification](https://www.conventionalcommits.org/en/v1.0.0/)
- [GitHub Docs: About issue and pull request templates — Pull request templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/about-issue-and-pull-request-templates)

### Typical remediation

Tighten conventions into exact grammars with examples in agent-loaded docs, then wire PR-time automation: title and commit lint, branch-name check, schema validation for machine-parsed PR-body blocks, plus a documented exception label.

*Issue skeleton:* [`templates/agt-06.md`](../templates/agt-06.md)

---

## AGT-07 · Least privilege for agents and automation

**Do coding agents, CI jobs, and bots operate with the narrowest credentials and permissions their task needs, with privileged credentials unreachable from the routine dev loop?**

`automation-safety` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **5/5** · weight **3/3**

Every automated actor (agent sessions, CI workflows, deploy jobs, webhook apps) holds only the permissions its task requires. CI tokens are read-only by default and elevated per job; deploy and service-role credentials live in platform secret stores, never in the repo or in env files agents load by default; agent permission allowlists cover routine safe commands rather than blanket approval. Instructions explicitly forbid privileged writes from the dev loop (direct production-data writes) and route them through reviewed, operator-run paths such as harnesses that read credentials at runtime.

*Why it matters:* An agent or pipeline holding standing privileged credentials converts a single prompt injection, bug, or hallucinated command into a production data breach; for a product holding intimate personal data about users and named third parties, that is the worst available failure.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Privileged credentials (service keys, deploy tokens) are committed in the repo or exported in shared env files agents load; CI uses default broad token permissions with write access everywhere. |
| **1 · Ad-hoc** | Secrets are out of git, but the dev loop and agent sessions run with a privileged key by default; workflows declare no permission scoping. |
| **2 · Defined** | Workflows declare permissions blocks and secrets live in the platform store, but some jobs are over-scoped and no instruction forbids privileged writes from the dev loop. |
| **3 · Managed** | Per-job least-privilege permissions; privileged keys appear only in the narrow jobs or operator-run harnesses that need them, read from env at runtime; agent allowlists are scoped to routine commands; an explicit written rule bans production writes from the dev loop. |
| **4 · Verified** | Enforced and monitored: secret scanning is enabled on the repository, CI config is linted for over-broad token permissions or dangerous triggers, and a periodic check (scorecard-style tooling) fails on regressions. |

### Audit checklist

- [ ] Grep the tree for privileged material: grep -ri 'service_role' apps/ packages/ (excluding operator-run scripts that read it from env at runtime); confirm .env files are gitignored and example env files carry no live values.
- [ ] Read every .github/workflows/*.yml for a top-level or per-job permissions block; flag workflows relying on the default token scope and any pull_request_target usage with checkout of PR code.
- [ ] Identify scripts requiring privileged keys (verify harnesses, backfills, smoke tests): confirm they read credentials from env at runtime, are excluded from client bundles, and are documented as operator-run against a named project.
- [ ] Inspect agent permission configuration (.claude/settings.json and settings.local.json allowlists or equivalent) for blanket wildcards versus scoped allows on routine read/lint/test commands.
- [ ] Confirm an explicit instruction-file rule forbids writing to production data stores from the dev loop and names the sanctioned path instead.
- [ ] Verify store and hosting deploy credentials exist only as CI secrets referenced by release workflows, never in the repo.

### Monitoring signals

- grep for service-role keys or private keys inside client app directories returns nothing
- grep -L 'permissions:' .github/workflows/*.yml returns no files (every workflow declares token scope)
- Repository secret scanning enabled (gh api repos/{owner}/{repo} shows security_and_analysis active)

### References

- [OWASP Top 10 CI/CD Security Risks — CICD-SEC-6: Insufficient Credential Hygiene](https://owasp.org/www-project-top-10-ci-cd-security-risks/)
- [GitHub Docs: Security hardening for GitHub Actions — Restricting permissions for tokens](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)
- [NIST SP 800-53 Rev. 5 Security and Privacy Controls — AC-6 Least Privilege](https://csrc.nist.gov/pubs/sp/800/53/r5/upd1/final)

### Typical remediation

Move all privileged credentials to platform secret stores, add explicit least-privilege permissions blocks per workflow job, scope agent allowlists to routine commands, write the production-write ban into agent instructions, and enable secret scanning plus CI-config linting.

*Issue skeleton:* [`templates/agt-07.md`](../templates/agt-07.md)

---

## AGT-08 · Decision log discipline

**Is there an append-only decision log with a stated significance bar and a supersede-not-edit rule, referenced from agent instructions so settled choices are consulted instead of re-litigated?**

`decision-memory` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **2/3**

Significant product and engineering decisions live in a versioned, append-only log with a fixed entry template (status, scope, context, decision, why, consequences, supersession links, refs). The log states its own significance bar and its supersede-not-edit rule, agent instructions direct agents to read it before architectural calls (with the convention that the last entry on a topic wins), and the PR workflow includes an explicit step asking whether the change created or reversed a significant decision. Hardened decisions get promoted into standing rules on a documented cadence rather than staying buried in the log.

*Why it matters:* Without durable decision memory, each agent session re-derives or silently reverses settled choices; the log is what makes 'we already decided this' checkable by an agent that was not in the room.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No decision record; rationale exists only in scattered PR descriptions or nowhere. |
| **1 · Ad-hoc** | Some decisions are written down ad hoc (wiki page, README notes) with no template, ordering, or supersession semantics. |
| **2 · Defined** | A dedicated log with a template exists, but the significance bar or the supersede rule is unstated, agents are not directed to consult it, and recent significant changes are missing from it. |
| **3 · Managed** | Append-only log with template, significance bar, and supersede rule; agent instructions require consulting it before architectural calls and the PR checklist includes the append step; spot-checks show recent significant decisions present with refs and intact supersession links. |
| **4 · Verified** | Discipline is machine-checked: CI or review tooling flags any diff that modifies lines of prior entries instead of appending, and promotion of hardened decisions into standing instruction rules happens on a documented cadence tied to milestones or audits. |

### Audit checklist

- [ ] Locate the log (docs/decisions/log.md or an ADR directory); verify it states its own rules: append-only, supersede-not-edit, a significance bar, and a fixed entry template.
- [ ] Run git log --follow -p on the log file and check whether history shows edits to earlier entries versus pure appends at the bottom.
- [ ] Grep agent instruction files for a directive to read the log before architectural calls (including last-entry-wins semantics) and check the PR checklist for the append step with its significance bar.
- [ ] Sample three recent significant changes (schema migrations, auth behavior, cross-surface contract changes) and check each has a corresponding entry or a defensible absence under the stated bar.
- [ ] Check supersession integrity: entries marked superseded reference the superseding entry by date and title, and the newest entry on each topic is the operative one.

### Monitoring signals

- Diffs touching the log only append after the previous end of file (CI check on hunk positions)
- Instruction files reference the log path and the consult-before-architectural-calls rule (grep 'decisions/log' CLAUDE.md)
- A validator confirms every entry carries the required template headings

### References

- [Documenting Architecture Decisions (Michael Nygard) — ADR structure and immutability](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [Architectural Decision Records — ADR overview](https://adr.github.io)

### Typical remediation

Adopt an append-only decision log with a fixed template, explicit significance bar, and supersede-not-edit rule; wire consult-and-append steps into agent instructions and the PR checklist; add a CI check that rejects edits to prior entries; promote hardened decisions into standing rules at milestone boundaries.

*Issue skeleton:* [`templates/agt-08.md`](../templates/agt-08.md)
