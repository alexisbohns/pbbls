@AGENTS.md

# Project Guidelines

## What this repo is

**Pebbles** — you record life moments as "pebbles" (time, intensity, positiveness, an emotion, related souls, life domains, reflective cards). Four client surfaces sit on one Supabase database; the database contract is the only thing they share.

| Workspace | What it is | Read before working here |
|---|---|---|
| `apps/web` | `@pbbls/web` — Next.js 16 App Router PWA, the main app | `apps/web/CLAUDE.md` |
| `apps/ios` | `@pbbls/ios` — SwiftUI, iOS 17+, iPhone-only | `apps/ios/CLAUDE.md` |
| `apps/android` | `@pbbls/android` — Kotlin + Compose, minSdk 33; mirrors iOS 1:1 | `apps/android/CLAUDE.md` (also routes the two Android skill sets) |
| `apps/admin` | `@pbbls/admin` — Next.js back-office (analytics, Lab logs, moderation), port 3001 | `apps/admin/CLAUDE.md` |
| `packages/supabase` | Migrations, generated `database.ts`, edge functions, DB verify harnesses | `packages/supabase/CLAUDE.md` |
| `packages/icons` | `@pbbls/icons` — the hand-drawn icon set and its generator (outputs committed per surface) | `packages/icons/CLAUDE.md` |
| `packages/shared` | Stub — no code yet | `packages/shared/CLAUDE.md` |
| `packages/rive` | `.riv` animation assets, copied per surface (not an npm workspace) | — |

Turborepo + npm workspaces at the root. Web and admin deploy to Vercel (root directory set per app); Android ships to Play internal testing from CI (`docs/android-play-deploy.md`); iOS ships to TestFlight internal testing from Xcode Cloud (`docs/ios-testflight-deploy.md`).

## Commands

| Command | Description |
|---|---|
| `npm run build --workspace=@pbbls/android` | Gradle via `scripts/gradle-if-sdk.sh` (no-ops without an SDK); `lint` = ktlint, `test` = unit tests |
| `npm run db:* --workspace=packages/supabase` | Supabase CLI: `db:start`, `db:reset`, `db:types`, `db:push`, `db:migration:new`, … |

There is no root `test` task. Tests: Vitest on web, Swift Testing on iOS (never XCTest), JUnit + screenshot previews on Android. Database contract harnesses live in `packages/supabase/scripts/` (`npm run db:verify --workspace=packages/supabase`) and run against the linked project — they are the proof for anything crossing a surface boundary. The four anon-only ones are a CI gate; `verify-account-purge.ts` needs the service role and stays a manual run.

## Before you start

- Check the issue description for the specific task and its dependencies.
- Never refactor existing code without explicit approval. If you see something to improve, mention it in a comment — don't change it.

## Task-size triage (read first)

Match ceremony to task size. Heavy workflows on small tasks are the main reason agent work feels slow.

**Small (≤ ~150 LOC, single file or tightly scoped):**
- Skip brainstorming, planning, TDD ceremony. Just make the change.
- Skip subagents (Plan, Explore, Reviewer) unless you genuinely don't know where something lives.
- Lint only the affected workspace: `npm run lint --workspace=apps/web` (or `apps/admin`, `packages/supabase`, etc.). Skip full `npm run build` unless touching types/config.
- Skip the Arkaik map update unless you added/removed/renamed a screen, route, data model, or endpoint.

**Medium (multi-file, single feature, ≤ ~500 LOC):**
- Sketch the approach in 2–3 sentences before coding. No formal plan doc.
- Workspace-scoped lint + build. Full build only if you changed shared types or `packages/*`.
- Update Arkaik only if architecture changed.

**Large (cross-app, schema migration, new feature surface):**
- Use the brainstorming/planning/TDD/review skills. The ceremony pays for itself here.
- Full `npm run build` and `npm run lint` from the repo root.
- Update the Arkaik map as part of the same change — see the `arkaik` skill and **The map is hosted** below.

## Where knowledge lives (load on demand)

Keep CLAUDE.md short. Read these when relevant — don't pre-load:

- **UI / styling / a11y** → `docs/agents/ui-and-styling.md` (atomic design, shadcn-first, base-nova quirks, theming, WCAG)
- **Data layer / Supabase / async** → `docs/agents/data-and-async.md` (DataProvider, auth deadlock, withTimeout, error logging)
- **Product architecture map** → `arkaik` skill (`.claude/skills/arkaik/`). The map is **hosted**, not a file in this repo — see below
- **Why something is the way it is** → `docs/decisions/log.md`. Read it before making an architectural call — it is append-only and supersede-don't-edit, so the *last* entry on a topic wins.
- **How a feature was designed / built** → `docs/superpowers/specs/<date>-<slug>-design.md` and `docs/superpowers/plans/<date>-<slug>.md`. Post-ship "Lessons learned" live in the plan.
- **Postgres / Supabase technique** → `.agents/skills/supabase-postgres-best-practices/` (RLS, indexing, locking, pooling).
- **PR Lab Notes** → `lab-note` skill (`.claude/skills/lab-note/`); see the Lab Note section below.

CI gates worth knowing about: `arkaik.yml` validates the legacy bundle + journal on any `docs/arkaik/**` change (it should now rarely run — see below), `android.yml` builds on `apps/android/**`, `web.yml` runs ESLint + the Vitest suite on **every** PR (no path filter, so it can be a required check), `supabase.yml` typechecks, replays the migration chain on a local stack and runs the contract harnesses on `packages/supabase/**` (harnesses also nightly), `lab-note-reminder.yml` advises at PR-open and `lab-note.yml` posts the note at merge.

## The map is hosted — never edit `docs/arkaik/bundle.json`

`docs/arkaik/arkaik.json` is present, so the Arkaik map lives in a **hosted account** and is changed only through the `arkaik-mcp` tools (`create_node`, `update_node`, `create_edge`, …). `docs/arkaik/bundle.json` and `journal.jsonl` are a frozen pre-hosted snapshot kept for `arkaik.yml`: **never create or edit them** (an `arkaik restore` from them has already wiped this project's federation feed and PR-promotion policy). **If the `arkaik-mcp` tools are not available, say so and stop.** Never fall back to the file.

Status is part of the change: move an acceptance or view to `development` (`update_node`) when you pick the work up. The Arkaik GitHub App marks `development` at PR-open and `releasing` at merge; a production web deploy marks the web acceptances its PRs named `live` (`mark-live.yml`), and nothing does that for iOS or Android yet, so never set `live` yourself — leave it at `releasing`. Full rules and rationale: the `arkaik` skill, "Hosted map rules for pbbls".

## Standing cross-surface rules

The database is the contract between four clients. These are hardened rules promoted from `docs/decisions/log.md` — breaking one is a regression, not a style choice.

- **iOS and Android mirror each other 1:1.** Changing a schema/RPC contract or a cross-surface behavior on one surface means checking whether the other three need the same change.
- **Test a shared data shape against real payloads produced by the other surfaces**, verbatim — including precision variants and explicit nulls. A same-surface round-trip is structurally incapable of catching a same-surface formatter bug.
- **Timestamps crossing a surface boundary are parsed tolerantly and emitted at the narrowest precision every reader accepts** (whole seconds). Never leave a timestamp to whatever date strategy the ambient decoder happens to carry.
- **Cross-user reads go through `security definer` RPC projections** that build an explicit jsonb allowlist (`get_public_profile` is the template). Never widen `profiles` RLS, never return `user_id`, never add a view instead.
- **Two migrations that re-emit the same whole function body silently drop each other's appends.** `create or replace` has no merge semantics and git reports no conflict. Before applying a batch containing more than one re-emission of the same function (`purge_account`, `remove_connection` — both use in-body append markers), diff the bodies pairwise and union them manually in a new migration.
- **A table added to `purge_account` gains its seed and its zero-row assertion in `verify-account-purge.ts` in the same change.** Run that harness (`npm run db:verify:purge --workspace=packages/supabase`) against the linked project after any batch touching `purge_account` — it is the one harness CI does not run for you.
- **Any migration or admin RPC that inserts an emotion or a domain re-runs `sync_achievement_catalog()` in the same transaction**, or the achievement catalog drifts from the reference tables.

## Build journal — one pebble per task

The maintainer's own Pebbles account is their public blog: one pebble per moment spent building Pebbles. **At the end of every session task that changed something** (a merged or opened PR, a shipped fix, an exploration, a release), drop one pebble with `node scripts/journal/drop-pebble.mjs` (usage in its header). Skip it for pure Q&A or aborted work, and drop one per task, not per commit.

- `--size small` by default; `medium` for an intermediate feature or a release, `large` only for a milestone.
- `--valence highlight` for something new, `neutral` for chores, refactors, quality and Kritik improvements, `lowlight` for bugs, incidents and critical findings.
- `--emotion`: one live slug chosen honestly for how the day felt. `--collection`: the surface (`Web`, `iOS`, `Android`) and/or `Releases`, `Milestones`, `Back-end & data`, `Brand & design`, `Admin & Lab`, `Quality & tooling`. `--image`: a screenshot reference that shows the change, when one exists.
- `--title` ≤ 50 chars; `--journal` first person, 1–3 short sentences in plain words a reader gets. No PR numbers, file names, finding ids, secrets or user data: it is published.
- If it fails on auth, tell the maintainer to re-bootstrap (`--init`, see the script header). Never work around it with the service role.

## Editing CLAUDE.md / AGENTS.md

These files load into every agent context, so they are the most token-precious docs in the repo — they must hold only durable, action-guiding rules, not a junk drawer of observations.

Treat learnings as living wisdom captured in plans' "Lessons learned" sections. Promote a learning into a CLAUDE.md/AGENTS.md rule **only when it hardens** — i.e. clears both bars:

- **Durable** — the constraint will outlive the next refactor, not a quirk of one feature.
- **Action-guiding** — it tells a future agent what to do or avoid, not a passive observation.

Cadence: promote during the periodic monorepo-audit grooming pass at **milestone boundaries** (folded into the audit's "Doc accuracy" domain — see `docs/superpowers/specs/2026-04-11-monorepo-audit-design.md`). **Never edit CLAUDE.md per-PR for learnings.** Land each promoted rule at the right scope: root `CLAUDE.md` / `AGENTS.md` for cross-cutting rules; workspace `CLAUDE.md` (`apps/web`, `apps/ios`, `apps/android`, `apps/admin`, `packages/supabase`) for surface-specific ones.

## Code conventions

- TypeScript strict. No `any`. No type assertions unless absolutely necessary.
- Components: PascalCase files, one exported component per file. Co-locate sub-components only if exclusively used by the parent.
- Hooks: camelCase prefixed with `use` (`usePebbles.ts`).
- Config / utility files: kebab-case (`card-types.ts`).
- Keep business logic out of components — put it in hooks or pure utility functions.
- Follow established patterns. New patterns require discussion first.

## Git & PR workflow

### Commits

- One logical change per commit.
- Conventional commits, lowercase, no period: `type(scope): description`.
- Types: `feat`, `fix`, `chore`, `docs`, `test`, `quality`.
- Scope (optional): a domain (`core`, `ui`, `db`, `api`, `auth`, `facility`, `legal`) or a surface (`web`, `ios`, `android`, `admin`).
- Examples: `feat(ui): add emotion picker grid component`, `fix(db): correct seed data validation`.

### Branches

- Format: `type/issueNumber-description` (e.g. `feat/12-path-timeline-view`).
- Create the branch with the correct name **before any commit**.

### Shipping larger work — parts, tasks, and stacked PRs

Anything bigger than a single focused change is planned as **parts** (one part = one branch = one PR), chained with `gh stack`. The splitting rules (a part is a reviewable unit; order by dependency, never convenience; separate a move from a rewrite; fix a lower layer in the layer that owns it; each part lints and typechecks alone; one stack tells one story) live in the `gh-stack` skill. Read it before planning a stack.

### Issues & labels

- Issue titles: `[Type] Description`.
- Apply one species label (`feat`, `fix`, `bug`, `chore`, `docs`, `test`, `quality`) plus one or more scope labels — domain (`core`, `ui`, `db`, `api`, `auth`, `facility`, `legal`) and/or surface (`web`, `ios`, `android`, `supabase`).
- A feature that lands on every surface is one backend issue plus one issue per client, each carrying its own surface label — that split is what keeps the milestone readable.

### PR checklist

1. Branch name matches `type/issueNumber-description` before pushing.
2. PR title in conventional commits format.
3. PR body starts with `Resolves #N` (or `Closes #N`); list key files and implementation notes.
4. Labels and milestone:
   - If the PR resolves an issue, propose inheriting its labels and milestone (except `bug` → PR gets `fix`). Confirm with the user.
   - If no issue, ask for species + scope label(s) and milestone.
   - Never open a PR without labels and milestone (unless the user confirms there's no milestone).
5. Run lint, **the workspace test suite**, and build at the **scope of your change** (per task-size triage above), confirm green, then open the PR.
6. **Never write a Kritik finding id (`F-…`) in a PR body unless that PR closes it.** The Arkaik App resolves *every* id it finds in the body — it does not distinguish "closes" from "mentions", so listing follow-up work by finding id marks that work done. Reference follow-ups by GitHub issue number instead. Five launch-gating PLT-04 findings were silently closed this way by #832 ([arkaik#440](https://github.com/alexisbohns/arkaik/issues/440)); the matrix then reports a store-compliance pillar as satisfied when no client has shipped it, and a hosted session cannot re-open a finding to repair it.
7. If this PR established or reversed a **significant** decision, append one entry to `docs/decisions/log.md` (usually a no-op). Significance bar: would a future agent or human waste real time rediscovering or wrongly reversing it? Supersede-don't-edit — status changes are new appended entries, never edits to prior ones.
8. **Lab Note (EN/FR)** — required for user-facing PRs; see the section below.

### Lab Note requirement — read before opening a PR

**The gate.** The PR has the `feat` label, **or** it touches a user-visible Arkaik view node → the PR body MUST carry one `## Lab Note (EN/FR)` section. Invoke the repo-local `lab-note` skill (`.claude/skills/lab-note/`) to author it; it takes precedence over the `lab-note@ariko` plugin skill and holds the schema, the quoting rules and the tone guide. Chore, refactor, infra, or docs-only → **no note**: delete the section from the PR body and add the `no-lab-note` label if the advisory reminder still comments. Never write to Supabase / `logs` from the dev loop.

