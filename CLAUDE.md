@AGENTS.md

# Project Guidelines

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

## Topical references (load on demand)

Keep CLAUDE.md short. Read these when relevant — don't pre-load:

- **UI / styling / a11y** → `docs/agents/ui-and-styling.md` (atomic design, shadcn-first, base-nova quirks, theming, WCAG)
- **Data layer / Supabase / async** → `docs/agents/data-and-async.md` (DataProvider, auth deadlock, withTimeout, error logging)
- **Product architecture map** → `arkaik` skill (see `.claude/skills/arkaik/`)

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
- Scope (optional): `core`, `ui`, `db`, `api`, `auth`, `facility`.
- Examples: `feat(ui): add emotion picker grid component`, `fix(db): correct seed data validation`.

### Branches

- Format: `type/issueNumber-description` (e.g. `feat/12-path-timeline-view`).
- Create the branch with the correct name **before any commit**.

### Issues & labels

- Issue titles: `[Type] Description`.
- Apply one species label (`feat`, `fix`, `bug`, `chore`, `docs`, `test`, `quality`) plus one or more scope labels (`core`, `ui`, `db`, `api`, `auth`, `facility`).

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

**The contract.** One `## Lab Note (EN/FR)` section holding exactly one ` ```yaml ` fence. Both languages are mandatory (`en.title`, `en.summary`, `fr.title`, `fr.summary`) — French is a real adaptation using the informal "Tu", never a literal translation. **No em dashes** in either language; use parentheses or a new sentence. PR-time defaults: `status: in_progress`, `published: false`, omit `release-date` (the maintainer's release-time switches).

```yaml
species: feature          # announcement | feature — a user-facing fix is a `feature`
platform: ios             # all | webapp | ios | android | project | infra
status: in_progress       # backlog | planned | in_progress | shipped
published: false
en:
  title: Short, benefit-first title
  summary: One or two sentences, user-facing.
fr:
  title: Titre court, orienté bénéfice
  summary: Une ou deux phrases, adaptées, pas traduites littéralement.
suggested:                # optional; read only by the Ariko vault
  molecule: pbbls         # THIS repo's molecule slug
  type: feature           # feature | improvement | fix | announcement
  tags: [changelog]
  # atom: <slug>          # ONLY when you know the slug exists — never guess
```

**Tone.** Lead with the benefit, not the mechanism. Short, warm, a little playful, never corporate. No engineering jargon, ticket numbers, or internal names.

**One block, two destinations.** On merge, `.github/workflows/lab-note.yml` posts the note to the Ariko changelog vault automatically (idempotent on `owner/repo#N`) — a malformed note fails that job loudly, so fix it by editing the PR body and re-running. At release time a human pastes the same YAML into the Pebbles Lab admin ("New log" prefills from the clipboard). Never write to Supabase / `logs` from the dev loop.
