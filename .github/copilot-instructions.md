# Project Guidelines

The agent rules for this repository live in one place. Read, in this order:

1. `CLAUDE.md` and `AGENTS.md` at the repository root: what the repo is, task-size triage, the standing cross-surface rules, the git and PR workflow.
2. The `CLAUDE.md` of the workspace you touch (`apps/web`, `apps/ios`, `apps/android`, `apps/admin`, `packages/supabase`, `packages/icons`).
3. `docs/agents/ui-and-styling.md` or `docs/agents/data-and-async.md` when the task is UI or data work on web or admin.

Two rules to hold before you have read those files:

- The Arkaik map is hosted. Never create or edit `docs/arkaik/bundle.json` or `journal.jsonl`.
- Every surface has tests. Run the test suite of the workspace you changed before opening a PR.
