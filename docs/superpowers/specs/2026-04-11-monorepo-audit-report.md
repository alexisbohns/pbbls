# Monorepo Quality & Robustness Audit Report

**Date:** 2026-04-11
**Branch:** `quality/monorepo-audit`
**Baseline:** Build passes clean. Lint: 0 errors, 8 warnings.

---

## Domain 1: Root / Monorepo Infrastructure

### [CRITICAL / security] `.mcp.json` tracked in git with Supabase project ref
The file is committed and contains the Supabase project reference URL (`project_ref=enuuezhrnncuhqonyxbb`). This is an internal infrastructure identifier that should not be public. `.mcp.json` is a local tool config and should not be versioned.
**Fix:** Add `.mcp.json` to `.gitignore`, then `git rm --cached .mcp.json`.

### [HIGH / security] `.vscode/mcp.json` contains hardcoded Notion API token
The file contains a plaintext Notion token (`ntn_554681836326...`). It is gitignored (`.vscode/*`), but an accidental `.gitignore` edit could expose it.
**Fix:** Move the token to an env var. Rotate the token since it has been visible in local config.

### [HIGH / build-integrity] `@pbbls/ios` has no build or lint scripts
Empty `scripts: {}` means turbo skips it silently with a warning on every run. This adds noise and ambiguity.
**Fix:** Add no-op placeholder scripts matching the pattern in `packages/shared` and `packages/supabase`.

### [HIGH / doc-accuracy] `README.md` is significantly outdated
States "All data lives in localStorage -- there is no backend yet" and labels `packages/supabase` as "(placeholder)". Both are false: SupabaseProvider exists, migrations are live, auth is wired.
**Fix:** Update README data layer description, data flow diagram, tech stack table, and monorepo structure listing.

### [MEDIUM / dead-code] `plan.md` at repo root is stale
A leftover plan for issue #177 (Markdown Rendering). References `react-markdown` (not installed), a non-conforming branch name, and a component that was never created.
**Fix:** Delete `plan.md`.

### [MEDIUM / dead-code] `.next/` directory at monorepo root
Stale build output from before the monorepo migration. The real build is in `apps/web/.next/`.
**Fix:** `rm -rf .next`

### [MEDIUM / build-integrity] `turbo` not reliably available locally
Root scripts use `turbo dev/build/lint` directly, but the binary requires `npx turbo` on a fresh clone despite being in devDependencies.
**Fix:** Verify `./node_modules/.bin/turbo` works after `npm install`. If not, pin a specific version.

### [MEDIUM / build-integrity] `turbo.json` build outputs assume Next.js only
Outputs are `[".next/**", "!.next/cache/**"]`. When `packages/shared` or `packages/supabase` get real build steps producing `dist/`, their outputs won't be cached.
**Fix:** Use per-workspace task output overrides or add `"dist/**"` to the global outputs.

### [MEDIUM / redundancy] `CLAUDE.md` and `.github/copilot-instructions.md` are near-duplicates
Same content maintained in two places. Already drifting (typo in one, not the other).
**Fix:** Make one file the canonical source. Have the other reference it.

### [MEDIUM / pattern-consistency] CLAUDE.md typo: "abbriviations"
Line 39 has "abbriviations" while `copilot-instructions.md` has the correct "abbreviations".
**Fix:** Fix the typo.

### [LOW / dead-code] `next-env.d.ts` at monorepo root
Remnant from pre-monorepo. Belongs in `apps/web/` only.
**Fix:** Delete root `next-env.d.ts`.

### [LOW / dead-code] `tsconfig.tsbuildinfo` at monorepo root
270KB build cache with no root tsconfig. Serves no purpose.
**Fix:** Delete root `tsconfig.tsbuildinfo`.

### [LOW / dead-code] `public/` directory at monorepo root
Contains only `.DS_Store`. Real assets are in `apps/web/public/`.
**Fix:** Delete root `public/`.

### [LOW / redundancy] `.agents/` directory not gitignored
Contains downloaded skill definitions. Should likely be treated as local state.
**Fix:** Verify whether `.agents/` should be gitignored. If so, add to `.gitignore`.

### [LOW / redundancy] AGENTS.md creates circular instruction chain
CLAUDE.md includes AGENTS.md, which points to copilot-instructions.md, which has the same content as CLAUDE.md. Redundant loop.
**Fix:** Keep AGENTS.md focused on supplementary instructions (Next.js warning, DB migrations). Remove the redirect to copilot-instructions.md.

---

## Domain 2: `apps/web`

### [HIGH / security] Auth callback uses browser client in server context
`app/auth/callback/route.ts` uses `createBrowserClient` inside a Route Handler. This is designed for browser contexts and will fail to persist the session server-side. Supabase SSR docs require `createServerClient` with cookie adapters.
**Fix:** Create `lib/supabase/server.ts` with a `createServerClient` wrapper using `@supabase/ssr` and cookie accessors from `next/headers`. Use it in the auth callback.

### [HIGH / pattern-consistency] SupabaseProvider uses unsafe `Record<string, unknown>` casting
Extensive use of `Record<string, unknown>` with manual `as string`/`as number` casts for Supabase query results (lines 414-503). The generated `Database` type from `packages/supabase` exists but is not used.
**Fix:** Import `Database` from `@pbbls/supabase`, parameterize `createBrowserClient<Database>()`, and use typed query results.

### [MEDIUM / security] Docs pipeline allows dangerous HTML + `dangerouslySetInnerHTML`
`DocsContent.tsx` and `load-docs.ts` use `allowDangerousHtml: true` on remark-rehype and rehype-stringify, then render via `dangerouslySetInnerHTML`. XSS risk if doc content ever comes from untrusted sources.
**Fix:** Remove `allowDangerousHtml: true` or add `rehype-sanitize` to the pipeline.

### [MEDIUM / security] SVG rendering via `dangerouslySetInnerHTML` in PebbleVisual
`PebbleVisual.tsx` renders engine SVG including user glyph stroke `d` attributes without sanitization.
**Fix:** Validate stroke `d` values in `renderGlyphPaths` to only allow valid SVG path commands.

### [MEDIUM / dead-code] Unused `Stone` import and `pebblesCount` in PathProfileCard.tsx
Already flagged by lint. Unused code.
**Fix:** Remove the import and unused variable.

### [MEDIUM / dead-code] Unused `_seed` and `_tier` params in `lib/engine/render.ts`
Parameters declared but never used. Underscore prefix suppresses TS but not ESLint.
**Fix:** Remove or implement the parameters.

### [MEDIUM / dead-code] Orphaned hook: `lib/hooks/useStandalone.ts`
Defined but never imported anywhere.
**Fix:** Remove the file or wire it in.

### [MEDIUM / dead-code] Orphaned hook: `lib/hooks/useKeyboardOffset.ts`
Defined but never imported anywhere.
**Fix:** Remove the file or wire it in.

### [MEDIUM / build-integrity] ESLint config lacks architectural enforcement
No `no-restricted-imports` rule to prevent components from importing providers directly. No `no-explicit-any` rule despite CLAUDE.md requiring no `any`.
**Fix:** Add `no-restricted-imports` for provider files and `@typescript-eslint/no-explicit-any` as error.

### [MEDIUM / pattern-consistency] `ColorWorldProvider.tsx` reads/writes localStorage directly
Violates the "never read/write localStorage directly in components" rule from CLAUDE.md.
**Fix:** Extract localStorage access into a utility, consistent with DataProvider pattern.

### [MEDIUM / pattern-consistency] `ResetDataButton.tsx` calls provider directly
Imports `useDataProvider` and calls `provider.reset()` directly instead of going through a hook.
**Fix:** Add a `useReset` hook or extend an existing hook with reset capability.

### [MEDIUM / redundancy] Login and Register pages duplicate OAuth UI and handlers
Identical Apple/Google SVG icons, near-identical OAuth handler functions duplicated across both pages.
**Fix:** Extract `SocialAuthButtons` component and shared icon components.

### [MEDIUM / redundancy] `EMPTY_STORE` defined in 3 places
Same constant in `local-provider.ts`, `supabase-provider.ts`, and `DataProvider.tsx`.
**Fix:** Define once in `data-provider.ts` and import everywhere.

### [MEDIUM / doc-accuracy] `apps/web/CLAUDE.md` missing SupabaseProvider and auth hooks
Data layer section doesn't mention SupabaseProvider or `useSupabaseAuth`. Still frames LocalProvider as the primary provider.
**Fix:** Update to reflect current dual-provider architecture.

### [LOW / dead-code] Unused combobox UI primitive (`components/ui/combobox.tsx`)
7 exported components, zero imports. SoulPicker uses custom hooks instead.
**Fix:** Remove the file or integrate it.

### [LOW / dead-code] Deprecated `Session` type in `lib/types.ts`
Marked `@deprecated`, zero imports.
**Fix:** Remove the type.

### [LOW / dead-code] Unused dependency: `@rive-app/react-canvas`
Listed in package.json but never imported.
**Fix:** Remove from dependencies.

### [LOW / dead-code] Unused dependency: `esbuild-wasm`
Listed in package.json but never imported.
**Fix:** Remove from dependencies.

### [LOW / dead-code] `shadcn` in production dependencies
CLI tool that should be a devDependency.
**Fix:** Move to devDependencies.

### [LOW / build-integrity] `next.config.ts` hardcodes developer IP
`allowedDevOrigins` is hardcoded to `['192.168.1.165']`. Breaks for other contributors.
**Fix:** Remove or move to env var.

### [LOW / build-integrity] 4 `<img>` lint warnings
`PebbleCard.tsx`, `QuickPebbleEditor.tsx`, `PebbleDetail.tsx` use `<img>` for base64 data URLs.
**Fix:** Suppress with eslint-disable comments (with rationale) or use `next/image` with `unoptimized`.

### [LOW / pattern-consistency] Login/Register pages are not thin route shells
200+ lines each with embedded form logic, violating the convention that route pages are thin shells.
**Fix:** Extract to `components/auth/LoginForm.tsx` and `RegisterForm.tsx`.

### [LOW / doc-accuracy] `apps/web/CLAUDE.md` missing `lib/supabase/` directory
New directory for Supabase client initialization not listed in structure.
**Fix:** Add to directory structure listing.

### [LOW / doc-accuracy] `apps/web/CLAUDE.md` stale comment about "future Supabase"
Provider-context.ts comment still says "so a future Supabase implementation can be swapped in."
**Fix:** Update to reflect current state.

---

## Domain 3: `packages/supabase`

### [HIGH / security] `security definer` functions missing `search_path`
All three RPC functions (`create_pebble`, `update_pebble`, `delete_pebble`) and the auth trigger (`handle_new_user`) are `security definer` without `set search_path`. This is a known PostgreSQL privilege escalation vector explicitly warned about in Supabase docs.
**Fix:** Add `set search_path = public` to each `security definer` function.

### [MEDIUM / security] `compute_karma_delta` exposed via PostgREST
Internal computation function in `public` schema is auto-exposed to any authenticated user.
**Fix:** Move to a non-exposed schema or revoke execute from `anon` and `authenticated` roles.

### [MEDIUM / security] Minimum password length is 6
`config.toml` sets `minimum_password_length = 6`. OWASP recommends 8+.
**Fix:** Increase to at least 8.

### [MEDIUM / build-integrity] Generated types file not type-checked
`tsconfig.json` includes `src` but not `types`. The `types/database.ts` file is never verified by `tsc`.
**Fix:** Add `"types"` to tsconfig `include`. Replace placeholder build with `tsc --noEmit`.

### [MEDIUM / pattern-consistency] Seed data embedded in migration instead of `seed.sql`
Reference table data (emotions, domains, card types, pebble shapes) is in the migration file while `seed.sql` is empty. Conflates schema definition with data seeding.
**Fix:** Move INSERT statements to `seed.sql` or document the decision.

### [MEDIUM / doc-accuracy] CLAUDE.md claims web app imports from `@pbbls/supabase`
States `import type { Database } from "@pbbls/supabase"` is used. It is not — no file in `apps/web` imports from this package, and it's not even listed as a dependency.
**Fix:** Update docs to say this is planned, or wire the import.

### [LOW / security] No password complexity requirements
`password_requirements = ""` allows very weak passwords.
**Fix:** Set to at least `"letters_digits"`.

### [LOW / security] `karma_events` allows direct inserts via RLS
The insert policy lets users fabricate karma events, bypassing the security-definer RPC functions.
**Fix:** Remove the `karma_events_insert` policy.

### [LOW / security] Views may expose cross-user data
`v_karma_summary` and `v_bounce` views join `auth.users` without `auth.uid()` filter. PostgREST exposes all rows.
**Fix:** Add `where u.id = auth.uid()` to each view or convert to RPC functions.

### [LOW / dead-code] `supabase/migrations/.gitkeep` unnecessary
Directory now has real migration files.
**Fix:** Delete `.gitkeep`.

### [LOW / dead-code] `supabase/seed.sql` is empty placeholder
All seed data is in the migration. Empty file is misleading.
**Fix:** Populate it or add a comment explaining the situation.

### [LOW / pattern-consistency] `glyphs` vs `Mark` naming mismatch
Database uses `glyphs`, app uses `Mark`/`marks`. Will cause confusion.
**Fix:** Align naming across layers and document the decision.

### [LOW / redundancy] Dual type definitions for domain entities
Types defined in generated `database.ts` and manually in `apps/web/lib/types.ts`. Can drift silently.
**Fix:** When wired, derive app types from generated types or add compatibility checks.

### [LOW / doc-accuracy] CLAUDE.md status section stale
Still says SupabaseProvider "must implement the DataProvider interface" when it already exists.
**Fix:** Update status section.

---

## Domain 4: `packages/shared` + `apps/ios`

### [MEDIUM / build-integrity] Workspace glob auto-includes accidental directories
Root workspaces are `["apps/*", "packages/*"]`. Any accidental directory becomes a workspace member.
**Fix:** Consider explicit workspace entries.

### [MEDIUM / pattern-consistency] Domain types and configs live only in `apps/web`
9 config files and `lib/types.ts` are web-app-only. Future consumers (iOS) would have to duplicate.
**Fix:** Create a backlog issue to migrate shared types/config to `packages/shared` before iOS dev starts.

### [LOW / dead-code] `packages/shared` not consumed by any workspace
Not listed as a dependency in any other package.json. Turbo runs its no-op scripts for nothing.
**Fix:** Either wire it up or document it as intentionally unused.

### [LOW / pattern-consistency] Inconsistent stub patterns
`packages/shared` and `packages/supabase` have no-op echo scripts. `apps/ios` has none at all.
**Fix:** Add no-op scripts to `apps/ios` to match the pattern.

### [LOW / redundancy] `apps/ios` has both README.md and CLAUDE.md
Both cover the same ground for a stub with no code.
**Fix:** Consolidate into one file.

---

## Domain 5: Docs & Agent Instructions

### [HIGH / doc-accuracy] Arkaik bundle missing core views and has stale auth model
1. Missing view nodes for `/path` (primary timeline), `/carve`, `/offline`.
2. `DM-account` still describes username/password auth; login/register views reference a custom auth system being replaced by Supabase Auth.
3. All API endpoints are `status: "idea"` but Supabase RPCs are already implemented.
**Fix:** Add missing view nodes. Update auth model and endpoints to reflect Supabase Auth. Update statuses.

### [MEDIUM / doc-accuracy] `.claude/settings.local.json` has orphaned permission
References `mcp__Claude_Preview__preview_start` but no Claude Preview MCP server is configured.
**Fix:** Remove the orphaned permission entry.

### [MEDIUM / pattern-consistency] Skills split across `.agents/` and `.claude/skills/`
Two separate skill directories with different conventions. Confusing.
**Fix:** Document the purpose of each directory or consolidate.

### [LOW / doc-accuracy] Copilot-instructions.md has Arkaik section unusable by Copilot
The Arkaik skill is Claude-specific. Copilot agents can't use it.
**Fix:** Remove or simplify the Arkaik section in copilot-instructions.md.

### [LOW / doc-accuracy] Superpowers plans reference deleted files
Auth plan mentions `apps/web/lib/data/password.ts` (already deleted).
**Fix:** Mark completed tasks in plans to distinguish done vs remaining.

---

## Severity Summary

| Domain | Critical | High | Medium | Low | Total |
|--------|----------|------|--------|-----|-------|
| Root infra | 1 | 3 | 5 | 5 | 14 |
| `apps/web` | 0 | 2 | 10 | 10 | 22 |
| `packages/supabase` | 0 | 1 | 5 | 8 | 14 |
| `packages/shared` + `apps/ios` | 0 | 0 | 2 | 3 | 5 |
| Docs & instructions | 0 | 1 | 2 | 2 | 5 |
| **Total** | **1** | **7** | **24** | **28** | **60** |

## Top Priority Items

1. **`.mcp.json` tracked in git** — untrack immediately (critical/security)
2. **`security definer` without `search_path`** — fix before any non-local deployment (high/security)
3. **Auth callback uses browser client server-side** — broken auth flow (high/security)
4. **Notion token in `.vscode/mcp.json`** — rotate token (high/security)
5. **SupabaseProvider uses unsafe type casting** — wire generated types (high/pattern-consistency)
6. **README significantly outdated** — update to reflect current architecture (high/doc-accuracy)
7. **Arkaik bundle missing core views** — add `/path`, `/carve`, `/offline` (high/doc-accuracy)
8. **`@pbbls/ios` missing placeholder scripts** — add no-ops (high/build-integrity)
