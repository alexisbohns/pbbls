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
