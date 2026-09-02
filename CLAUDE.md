@AGENTS.md

# Project Guidelines

## What this repo is

**Pebbles** — you record life moments as "pebbles" (time, intensity, positiveness, an emotion, related souls, life domains, reflective cards). Four client surfaces sit on one Supabase database; the database contract is the only thing they share.

| Workspace | What it is | Read before working here |
|---|---|---|
| `apps/web` | `@pbbls/web` — Next.js 16 App Router PWA, the main app | `apps/web/CLAUDE.md` |
| `apps/ios` | `@pbbls/ios` — SwiftUI, iOS 17+, iPhone-only | `apps/ios/CLAUDE.md` |
| `apps/android` | `@pbbls/android` — Kotlin + Compose, minSdk 33; mirrors iOS 1:1 | `apps/android/CLAUDE.md` |
| `apps/admin` | `@pbbls/admin` — Next.js back-office (analytics, Lab logs, moderation), port 3001 | `apps/admin/CLAUDE.md` |
| `packages/supabase` | Migrations, generated `database.ts`, edge functions, DB verify harnesses | `packages/supabase/CLAUDE.md` |
| `packages/shared` | Stub — no code yet | `packages/shared/CLAUDE.md` |
| `packages/rive` | `.riv` animation assets, copied per surface (not an npm workspace) | — |

Turborepo + npm workspaces at the root. Web and admin deploy to Vercel (root directory set per app); Android ships to Play internal testing from CI (`docs/android-play-deploy.md`).

## Commands

| Command | Description |
|---|---|
| `npm run dev` / `build` / `lint` | Turborepo, all workspaces (the only root tasks — there is no root `test`) |
| `npm run lint --workspace=apps/web` | Workspace-scoped lint — the default for small/medium changes |
| `npm run test --workspace=apps/web` | Vitest (`apps/web/**/*.test.ts`) |
| `npm run build --workspace=@pbbls/ios` | `xcodegen generate` + `xcodebuild`; `test` runs Swift Testing, `lint` runs SwiftLint |
| `npm run build --workspace=@pbbls/android` | Gradle via `scripts/gradle-if-sdk.sh` (no-ops without an SDK); `lint` = ktlint, `test` = unit tests |
| `npm run db:* --workspace=packages/supabase` | Supabase CLI: `db:start`, `db:reset`, `db:types`, `db:push`, `db:migration:new`, … |

Tests: Vitest on web, Swift Testing on iOS (never XCTest), JUnit + screenshot previews on Android. Database contract harnesses live in `packages/supabase/scripts/` (`npm run db:verify --workspace=packages/supabase`) and run against the linked project — they are the proof for anything crossing a surface boundary. The four anon-only ones are a CI gate; `verify-account-purge.ts` needs the service role and stays a manual run.

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
- Update Arkaik (`docs/arkaik/bundle.json`) as part of the same change — see the `arkaik` skill.

## Where knowledge lives (load on demand)

Keep CLAUDE.md short. Read these when relevant — don't pre-load:

- **UI / styling / a11y** → `docs/agents/ui-and-styling.md` (atomic design, shadcn-first, base-nova quirks, theming, WCAG)
- **Data layer / Supabase / async** → `docs/agents/data-and-async.md` (DataProvider, auth deadlock, withTimeout, error logging)
- **Product architecture map** → `arkaik` skill (`.claude/skills/arkaik/`), bundle at `docs/arkaik/bundle.json`
- **Why something is the way it is** → `docs/decisions/log.md`. Read it before making an architectural call — it is append-only and supersede-don't-edit, so the *last* entry on a topic wins.
- **How a feature was designed / built** → `docs/superpowers/specs/<date>-<slug>-design.md` and `docs/superpowers/plans/<date>-<slug>.md`. Post-ship "Lessons learned" live in the plan.
- **Postgres / Supabase technique** → `.agents/skills/supabase-postgres-best-practices/` (RLS, indexing, locking, pooling).
- **PR Lab Notes** → `lab-note` skill (`.claude/skills/lab-note/`); see the Lab Note section below.

CI gates worth knowing about: `arkaik.yml` validates the bundle + journal on any `docs/arkaik/**` change, `android.yml` builds on `apps/android/**`, `supabase.yml` runs the contract harnesses on `packages/supabase/**` and nightly, `lab-note-reminder.yml` advises at PR-open and `lab-note.yml` posts the note at merge.

## Standing cross-surface rules

The database is the contract between four clients. These are hardened rules promoted from `docs/decisions/log.md` — breaking one is a regression, not a style choice.

- **iOS and Android mirror each other 1:1.** Changing a schema/RPC contract or a cross-surface behavior on one surface means checking whether the other three need the same change.
- **Test a shared data shape against real payloads produced by the other surfaces**, verbatim — including precision variants and explicit nulls. A same-surface round-trip is structurally incapable of catching a same-surface formatter bug.
- **Timestamps crossing a surface boundary are parsed tolerantly and emitted at the narrowest precision every reader accepts** (whole seconds). Never leave a timestamp to whatever date strategy the ambient decoder happens to carry.
- **Cross-user reads go through `security definer` RPC projections** that build an explicit jsonb allowlist (`get_public_profile` is the template). Never widen `profiles` RLS, never return `user_id`, never add a view instead.
- **Two migrations that re-emit the same whole function body silently drop each other's appends.** `create or replace` has no merge semantics and git reports no conflict. Before applying a batch containing more than one re-emission of the same function (`purge_account`, `remove_connection` — both use in-body append markers), diff the bodies pairwise and union them manually in a new migration.
- **A table added to `purge_account` gains its seed and its zero-row assertion in `verify-account-purge.ts` in the same change.** Run that harness (`npm run db:verify:purge --workspace=packages/supabase`) against the linked project after any batch touching `purge_account` — it is the one harness CI does not run for you.
- **Any migration or admin RPC that inserts an emotion or a domain re-runs `sync_achievement_catalog()` in the same transaction**, or the achievement catalog drifts from the reference tables.

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
- Comment non-obvious code with intent and reasoning. Skip comments that restate the code.
- Always consider edge cases and error handling, even if it's just logging for now.
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
5. Run lint/build at the **scope of your change** (per task-size triage above), confirm green, then open the PR.
6. If this PR established or reversed a **significant** decision, append one entry to `docs/decisions/log.md` (usually a no-op). Significance bar: would a future agent or human waste real time rediscovering or wrongly reversing it? Supersede-don't-edit — status changes are new appended entries, never edits to prior ones.
7. **Lab Note (EN/FR)** — required for user-facing PRs; see the section below.

### Lab Note requirement — read before opening a PR

**When you open a PR that ships something a user would notice, you MUST include a Lab Note in the PR body.** This section is self-sufficient: you can author a valid note from it alone. The repo-local `lab-note` skill (`.claude/skills/lab-note/`) is the source of truth for full tone guidance and examples, and **takes precedence over the `lab-note@ariko` plugin skill** if both are present.

**The gate.** The PR has the `feat` label, **or** it touches a user-visible Arkaik view node (`docs/arkaik/bundle.json`) → write a note. Chore, refactor, infra, or docs-only → **no note**: delete the section from the PR body (if the advisory `lab-note-reminder` still comments, add the **`no-lab-note`** label to silence it).

**The contract.** One `## Lab Note (EN/FR)` section holding exactly one ` ```yaml ` fence. Both languages are mandatory (`en.title`, `en.summary`, `fr.title`, `fr.summary`) — French is a real adaptation using the informal "Tu", never a literal translation. **No em dashes** in either language; use parentheses or a new sentence. **Always double-quote every title and summary**, as the skeleton below does: a colon is the natural way to write a sentence ("Heads up: it moved", "ton compte : ceux que...") and it is exactly what an unquoted YAML value cannot hold, so the parser reads `key: value` and the whole note fails. Quoting removes the failure mode outright, apostrophes included; slug-ish values need no quotes. PR-time defaults: `status: in_progress`, `published: false`, omit `release-date` (the maintainer's release-time switches).

```yaml
species: feature          # announcement | feature — a user-facing fix is a `feature`
platform: ios             # all | webapp | ios | android | project | infra
status: in_progress       # backlog | planned | in_progress | shipped
published: false
en:
  title: "Short, benefit-first title"
  summary: "One or two sentences, user-facing."
fr:
  title: "Titre court, orienté bénéfice"
  summary: "Une ou deux phrases, adaptées, pas traduites littéralement."
suggested:                # optional; read only by the Ariko vault
  molecule: pbbls         # THIS repo's molecule slug
  type: feature           # feature | improvement | fix | announcement
  tags: [changelog]
  # atom: <slug>          # ONLY when you know the slug exists — never guess
```

**Tone.** Lead with the benefit, not the mechanism. Short, warm, a little playful, never corporate. No engineering jargon, ticket numbers, or internal names.

**One block, two destinations.** On merge, the Arkaik GitHub App webhook appends the note to this project's arkaik journal as a `deliverable.shipped` event (idempotent per PR; the Ariko federation reads it from the pollen feed) — a malformed note is reported in the delivery response, and the advisory `lab-note-reminder` surfaces the same problems at PR-open time, so fix it by editing the PR body. At release time a human pastes the same YAML into the Pebbles Lab admin ("New log" prefills from the clipboard). Never write to Supabase / `logs` from the dev loop.
