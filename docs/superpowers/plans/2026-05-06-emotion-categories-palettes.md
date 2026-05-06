# Emotion categories and color palettes — Phase 1 implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the schema for emotion categories and color palettes (issue #366, Phase 1 only — schema additions + view, no data, no client wiring).

**Architecture:** One additive Supabase migration file: a new `public.emotion_categories` reference table with an inlined 8-digit-hex palette, a nullable `category_id` column on `public.emotions`, a supporting index, and an `INNER JOIN` view `public.v_emotions_with_palette`. The `NOT NULL` constraint on `category_id` lands in a separate Phase 2 PR once the user has populated data manually in Supabase Studio. `emotions.color` is preserved untouched for backwards compatibility with shipped iOS clients.

**Tech Stack:** Supabase Postgres, Supabase CLI, TypeScript (`packages/supabase/types/database.ts` generation).

**Spec:** `docs/superpowers/specs/2026-05-06-emotion-categories-palettes-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `packages/supabase/supabase/migrations/20260506000000_emotion_categories.sql` | Create | Phase 1 DDL: new table, RLS, alter `emotions`, index, view |
| `packages/supabase/types/database.ts` | Modify (regenerate) | Reflect schema additions; auto-generated, committed |

No application code changes in this PR. Client wiring (iOS service, web hook, render code) is a separate follow-up issue.

---

## Task 1: Create the feature branch

**Files:** none (branch only)

- [ ] **Step 1: Verify clean working tree**

Run: `git status`
Expected: `nothing to commit, working tree clean` and current branch `main`.

- [ ] **Step 2: Pull latest main**

Run: `git pull --ff-only origin main`

- [ ] **Step 3: Create the branch**

Run: `git checkout -b feat/366-emotion-categories-palettes`

Branch name follows the project convention `type/issueNumber-description` (per `CLAUDE.md`).

---

## Task 2: Write the Phase 1 migration file

**Files:**
- Create: `packages/supabase/supabase/migrations/20260506000000_emotion_categories.sql`

- [ ] **Step 1: Create the migration file with the full DDL**

Write the file with this exact content:

```sql
-- Migration: Emotion categories and color palettes — Phase 1 (schema only)
-- Issue: https://github.com/Bohns/pbbls/issues/366
-- Spec:  docs/superpowers/specs/2026-05-06-emotion-categories-palettes-design.md
--
-- Notes:
--   - Adds public.emotion_categories with an inlined 4-color palette.
--     All palette colors are 8-digit hex (#RRGGBBAA). Opaque colors are
--     FF-padded; surface is seeded by convention as primary + 1A (10% alpha).
--   - Adds public.emotions.category_id as nullable. The NOT NULL constraint
--     follows in a separate Phase 2 migration once data has been populated
--     manually in Supabase Studio.
--   - Leaves public.emotions.color untouched (soft-deprecated). Shipped iOS
--     clients still read it; new clients will use the view below.
--   - public.v_emotions_with_palette is an INNER JOIN — emotions with a null
--     category_id are excluded from view results until Phase 2 lands. No
--     client consumes the view in this PR, so the partial-list-during-rollout
--     window is internal-state only.

-- ============================================================
-- TABLES
-- ============================================================

create table public.emotion_categories (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  name text not null,
  primary_color text not null,
  secondary_color text not null,
  light_color text not null,
  surface_color text not null
);

-- ============================================================
-- ALTER EXISTING
-- ============================================================

alter table public.emotions
  add column category_id uuid references public.emotion_categories(id);

create index emotions_category_id_idx
  on public.emotions (category_id);

-- ============================================================
-- ACCESS CONTROL
-- ============================================================

alter table public.emotion_categories enable row level security;

create policy "emotion_categories_select" on public.emotion_categories
  for select using (true);

-- ============================================================
-- VIEWS
-- ============================================================

create view public.v_emotions_with_palette as
select
  e.id,
  e.slug,
  e.name,
  e.color,
  c.id              as category_id,
  c.slug            as category_slug,
  c.name            as category_name,
  c.primary_color,
  c.secondary_color,
  c.light_color,
  c.surface_color
from public.emotions e
join public.emotion_categories c on c.id = e.category_id;
```

- [ ] **Step 2: Sanity-check the file**

Run: `wc -l packages/supabase/supabase/migrations/20260506000000_emotion_categories.sql`
Expected: ~50 lines.

Run: `head -5 packages/supabase/supabase/migrations/20260506000000_emotion_categories.sql`
Expected: starts with `-- Migration: Emotion categories...`.

---

## Task 3: Apply the migration to the remote Supabase project

The user's workflow avoids local Docker (per project memory). Two ways to apply:

- **Option A — paste into Supabase Studio SQL editor** (matches user's stated preference earlier in brainstorming): open Studio → SQL Editor → paste the contents of the new migration file → run.
- **Option B — `supabase db push` from the linked CLI** (no Docker required if `db:link` was previously run with the project ref): `npm run db:push --workspace=packages/supabase`.

Either path produces the same result. Pick one.

- [ ] **Step 1: Apply the migration**

Run **one** of:

```bash
# Option A: copy the file's contents into Supabase Studio → SQL Editor → Run
cat packages/supabase/supabase/migrations/20260506000000_emotion_categories.sql
```

```bash
# Option B: push via CLI
npm run db:push --workspace=packages/supabase
```

- [ ] **Step 2: Verify the schema in Supabase Studio**

Open Studio → Table Editor. Confirm:
- `emotion_categories` table exists with columns `id`, `slug`, `name`, `primary_color`, `secondary_color`, `light_color`, `surface_color`.
- `emotions` table now has a `category_id` column (nullable, foreign key to `emotion_categories.id`).
- Database → Indexes shows `emotions_category_id_idx`.
- Database → Policies shows `emotion_categories_select`.

In SQL Editor, run:

```sql
select * from public.v_emotions_with_palette limit 1;
```

Expected: 0 rows (no emotion has `category_id` populated yet — INNER JOIN behavior). Query succeeds; no error.

---

## Task 4: Regenerate TypeScript types

The package's `db:types` script is wired to `--local`, which requires Docker. Since the user avoids local Supabase, regenerate from the remote project instead.

- [ ] **Step 1: Generate types from the remote project**

Use **one** of:

- **Option A — Supabase MCP tool** (preferred per `packages/supabase/CLAUDE.md` fallback note): call `generate_typescript_types` against the linked remote project, pipe the result into `packages/supabase/types/database.ts`.

- **Option B — local Docker, one-shot**:

```bash
npm run db:start --workspace=packages/supabase
npm run db:reset --workspace=packages/supabase
npm run db:types --workspace=packages/supabase
npm run db:stop --workspace=packages/supabase
```

- [ ] **Step 2: Verify the generated types include the new schema**

Run:
```bash
grep -c "emotion_categories" packages/supabase/types/database.ts
```
Expected: ≥ 3 (table definition + Row/Insert/Update types).

Run:
```bash
grep -c "v_emotions_with_palette" packages/supabase/types/database.ts
```
Expected: ≥ 1 (view definition).

Run:
```bash
grep -A2 "category_id" packages/supabase/types/database.ts | head -20
```
Expected: `category_id` typed as `string | null` (nullable in Phase 1; will tighten to `string` in Phase 2).

---

## Task 5: Workspace-scoped build check

This is a small, schema-only change touching `packages/supabase`. Per the project's task-size triage, lint/build only the affected workspace.

- [ ] **Step 1: Type-check the supabase package**

Run: `npm run build --workspace=packages/supabase`
Expected: passes (the package's `build` script runs `tsc --noEmit`).

- [ ] **Step 2: Confirm no consumer is broken**

The new `category_id` column appears as `string | null` on the generated `emotions` Row type. No existing code references it, so this is additive-only. To be safe, also build the consumers that import the regenerated types:

Run: `npm run build --workspace=apps/web`
Expected: passes.

(`apps/ios` is built by Xcode, not npm — skipped here.)

---

## Task 6: Commit

- [ ] **Step 1: Stage the migration and the regenerated types**

Run:
```bash
git add packages/supabase/supabase/migrations/20260506000000_emotion_categories.sql
git add packages/supabase/types/database.ts
```

- [ ] **Step 2: Verify nothing else is staged**

Run: `git status`
Expected: only the two files above appear under "Changes to be committed".

- [ ] **Step 3: Commit with a conventional-commit message**

```bash
git commit -m "$(cat <<'EOF'
feat(db): emotion_categories table and palette view (phase 1)

Adds public.emotion_categories with an inlined 4-color palette
(primary/secondary/light/surface, all 8-digit hex), a nullable
emotions.category_id FK, an index, and an INNER JOIN view
public.v_emotions_with_palette. Schema only — palette rows and
category_id values are populated manually in Supabase Studio.
NOT NULL on category_id follows in a Phase 2 migration.

Refs #366

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Push and open the PR

- [ ] **Step 1: Push the branch**

Run: `git push -u origin feat/366-emotion-categories-palettes`

- [ ] **Step 2: Inspect issue #366 for labels and milestone to inherit**

Run: `gh issue view 366 --json labels,milestone`

Per project convention (`CLAUDE.md` PR checklist), propose inheriting the issue's labels and milestone. Issue #366 is currently labelled `question`. Since the PR ships a feature, swap `question` for `feat`. Scope label(s) must include `db` (schema change). Confirm milestone with the user before opening the PR if the issue has none.

- [ ] **Step 3: Open the PR**

```bash
gh pr create \
  --title "feat(db): emotion_categories table and palette view (phase 1)" \
  --label feat --label db \
  --body "$(cat <<'EOF'
Resolves part of #366 (Phase 1 — schema only).

## Summary

- New `public.emotion_categories` reference table with inlined 4-color palette (primary/secondary/light/surface, all 8-digit hex).
- New nullable `public.emotions.category_id` FK + index.
- New view `public.v_emotions_with_palette` (INNER JOIN — un-categorized emotions are hidden until Phase 2 lands).
- `public.emotions.color` is left untouched for backwards compatibility with shipped iOS clients.

## Out of scope (future PRs)

- Phase 2: `ALTER COLUMN category_id SET NOT NULL` once data is populated.
- Client wiring: iOS service that fetches `v_emotions_with_palette` on mount; web equivalent; updating render code.
- iOS `Color(hex:)` extended to handle 8-digit hex (length-dispatch on 6 vs 8).
- Theming.

## Test plan

- [ ] Migration applies cleanly to the remote Supabase project.
- [ ] `select * from public.v_emotions_with_palette limit 1` returns 0 rows pre-backfill (INNER JOIN behavior).
- [ ] After manual data population in Studio, the same query returns one row per categorized emotion with all four palette columns populated as 8-digit hex.
- [ ] `npm run build --workspace=packages/supabase` passes.
- [ ] `npm run build --workspace=apps/web` passes.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

If the issue has a milestone, append `--milestone "<name>"` (confirm with user first per project memory).

- [ ] **Step 4: Confirm the PR opened**

The command output prints the PR URL. Verify in browser that the PR has the correct title, labels, body, and that CI (if any) is queued.

---

## Out of plan (Phase 2 — separate future PR)

Once the user has populated all 7 palette rows and set `category_id` on every emotion, a Phase 2 PR will:

1. Create a new migration file (e.g. `<later-date>_emotions_category_not_null.sql`) containing only:
   ```sql
   alter table public.emotions alter column category_id set not null;
   ```
2. Apply it (Studio or `db:push`).
3. Regenerate types (`category_id` will now be `string`, not `string | null`).
4. Commit and PR.

This is intentionally not part of this plan — it's gated on user data work.
