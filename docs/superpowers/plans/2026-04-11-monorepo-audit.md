# Monorepo Audit Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all 60 findings from the monorepo quality audit, prioritized critical-first.

**Architecture:** Six ordered tasks, each a coherent PR-able unit. Tasks 1-2 are security-critical. Tasks 3-6 are medium/low cleanup grouped by domain.

**Tech Stack:** TypeScript, Next.js 16, Supabase, PostgreSQL, ESLint, Turborepo

---

### Task 1: Critical & High Security Fixes

**Files:**
- Modify: `.gitignore`
- Modify: `.mcp.json` (untrack)
- Create: `apps/web/lib/supabase/server.ts`
- Modify: `apps/web/app/auth/callback/route.ts`
- Create: `packages/supabase/supabase/migrations/20260411000005_security_hardening.sql`
- Modify: `packages/supabase/supabase/config.toml:175`
- Modify: `apps/web/lib/docs/load-docs.ts:13-15`

- [ ] **Step 1: Untrack `.mcp.json` and add to `.gitignore`**

Add `.mcp.json` to `.gitignore` after the `.vscode/*` line:

```gitignore
# mcp
.mcp.json
```

Then untrack:

```bash
git rm --cached .mcp.json
```

- [ ] **Step 2: Commit the `.mcp.json` fix**

```bash
git add .gitignore
git commit -m "fix(core): untrack .mcp.json containing supabase project ref"
```

- [ ] **Step 3: Create server-side Supabase client**

Create `apps/web/lib/supabase/server.ts`:

```typescript
import { createServerClient } from "@supabase/ssr"
import { cookies } from "next/headers"

export async function createServerSupabaseClient() {
  const cookieStore = await cookies()

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY

  if (!url || !key) {
    throw new Error(
      "Missing NEXT_PUBLIC_SUPABASE_URL or NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY. " +
        "Copy apps/web/.env.local.example to apps/web/.env.local and fill in your values.",
    )
  }

  return createServerClient(url, key, {
    cookies: {
      getAll() {
        return cookieStore.getAll()
      },
      setAll(cookiesToSet) {
        for (const { name, value, options } of cookiesToSet) {
          cookieStore.set(name, value, options)
        }
      },
    },
  })
}
```

- [ ] **Step 4: Fix auth callback to use server client**

Replace `apps/web/app/auth/callback/route.ts` with:

```typescript
import { NextResponse } from "next/server"
import { createServerSupabaseClient } from "@/lib/supabase/server"

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url)
  const code = searchParams.get("code")

  if (code) {
    const supabase = await createServerSupabaseClient()
    const { error } = await supabase.auth.exchangeCodeForSession(code)

    if (!error) {
      const {
        data: { user },
      } = await supabase.auth.getUser()

      if (user) {
        const { data: profile } = await supabase
          .from("profiles")
          .select("onboarding_completed")
          .eq("user_id", user.id)
          .single()

        const destination = profile?.onboarding_completed ? "/path" : "/onboarding"
        return NextResponse.redirect(`${origin}${destination}`)
      }
    }
  }

  return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`)
}
```

- [ ] **Step 5: Create security hardening migration**

Create `packages/supabase/supabase/migrations/20260411000005_security_hardening.sql`:

```sql
-- Migration: Security Hardening
-- Fixes: search_path on security definer functions, exposed internal functions, direct karma_events inserts, view scoping.

-- ============================================================
-- 1. Pin search_path on all security definer functions
-- ============================================================

create or replace function public.compute_karma_delta(
  p_description text,
  p_cards_count int,
  p_souls_count int,
  p_domains_count int,
  p_has_glyph boolean,
  p_snaps_count int
) returns int as $$
declare
  delta int := 1; -- base karma
begin
  if p_description is not null and length(trim(p_description)) > 0 then
    delta := delta + 1;
  end if;
  delta := delta + least(p_cards_count, 4);
  if p_souls_count > 0 then delta := delta + 1; end if;
  if p_domains_count > 0 then delta := delta + 1; end if;
  if p_has_glyph then delta := delta + 1; end if;
  if p_snaps_count > 0 then delta := delta + 1; end if;
  return least(delta, 10);
end;
$$ language plpgsql immutable set search_path = public;

-- Revoke direct access to internal computation function
revoke execute on function public.compute_karma_delta from anon, authenticated;

-- Re-create handle_new_user with search_path pinned
create or replace function public.handle_new_user()
returns trigger as $$
begin
  insert into public.profiles (user_id, display_name)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', 'Pebbler')
  );
  return new;
end;
$$ language plpgsql security definer set search_path = public;

-- ============================================================
-- 2. Remove direct insert on karma_events
-- ============================================================

drop policy if exists "karma_events_insert" on public.karma_events;

-- ============================================================
-- 3. Scope views to current user
-- ============================================================

create or replace view public.v_karma_summary as
select
  u.id as user_id,
  coalesce((select sum(ke.delta) from public.karma_events ke where ke.user_id = u.id), 0) as total_karma,
  (select count(*) from public.pebbles pb where pb.user_id = u.id) as pebbles_count
from auth.users u
where u.id = auth.uid();

create or replace view public.v_bounce as
select
  u.id as user_id,
  coalesce(stats.active_days, 0) as active_days,
  case
    when coalesce(stats.active_days, 0) = 0  then 0
    when stats.active_days between 1  and 5  then 1
    when stats.active_days between 6  and 9  then 2
    when stats.active_days between 10 and 13 then 3
    when stats.active_days between 14 and 17 then 4
    when stats.active_days between 18 and 20 then 5
    when stats.active_days between 21 and 24 then 6
    else 7
  end::smallint as bounce_level
from auth.users u
left join lateral (
  select count(distinct date(pb.happened_at)) as active_days
  from public.pebbles pb
  where pb.user_id = u.id
    and pb.happened_at >= now() - interval '28 days'
) stats on true
where u.id = auth.uid();
```

Note: The `create_pebble`, `update_pebble`, and `delete_pebble` functions also need `set search_path = public` added. These are large functions — read the full content of `20260411000003_rpc_functions.sql` and re-create each with `set search_path = public` appended to the function definition (after `security definer`).

- [ ] **Step 6: Harden password policy**

In `packages/supabase/supabase/config.toml`, change line 175:

```toml
minimum_password_length = 8
```

And line 178:

```toml
password_requirements = "letters_digits"
```

- [ ] **Step 7: Remove `allowDangerousHtml` from docs pipeline**

In `apps/web/lib/docs/load-docs.ts`, replace lines 13-15:

```typescript
const processor = unified()
  .use(remarkParse)
  .use(remarkRehype)
  .use(rehypeStringify)
```

- [ ] **Step 8: Regenerate Supabase types after migration**

```bash
npm run db:reset --workspace=packages/supabase
npm run db:types --workspace=packages/supabase
```

- [ ] **Step 9: Verify build and lint**

```bash
npx turbo build
npx turbo lint
```

- [ ] **Step 10: Commit all security fixes**

```bash
git add apps/web/lib/supabase/server.ts apps/web/app/auth/callback/route.ts
git add packages/supabase/supabase/migrations/20260411000005_security_hardening.sql
git add packages/supabase/supabase/config.toml
git add packages/supabase/types/database.ts
git add apps/web/lib/docs/load-docs.ts
git commit -m "fix(db): harden security definer functions, auth callback, password policy, and docs pipeline"
```

---

### Task 2: Root Infrastructure Cleanup

**Files:**
- Delete: `plan.md`
- Modify: `.gitignore`
- Modify: `apps/ios/package.json`
- Modify: `turbo.json`
- Modify: `CLAUDE.md:39`
- Modify: `AGENTS.md`

- [ ] **Step 1: Delete stale root files**

```bash
rm -rf .next public/
rm -f plan.md next-env.d.ts tsconfig.tsbuildinfo
```

- [ ] **Step 2: Add `.agents/` to `.gitignore`**

Add after the `.superpowers/` line in `.gitignore`:

```gitignore
# agent skills cache
.agents/
```

- [ ] **Step 3: Add no-op scripts to `apps/ios/package.json`**

Replace `apps/ios/package.json` with:

```json
{
  "name": "@pbbls/ios",
  "version": "0.0.0",
  "private": true,
  "scripts": {
    "build": "echo 'placeholder — no build step yet'",
    "lint": "echo 'placeholder — no lint step yet'"
  }
}
```

- [ ] **Step 4: Add `dist/**` to turbo.json build outputs**

Replace `turbo.json`:

```json
{
  "$schema": "https://turbo.build/schema.json",
  "tasks": {
    "build": {
      "dependsOn": ["^build"],
      "outputs": [".next/**", "!.next/cache/**", "dist/**"]
    },
    "dev": {
      "cache": false,
      "persistent": true
    },
    "lint": {
      "dependsOn": ["^build"]
    }
  }
}
```

- [ ] **Step 5: Fix CLAUDE.md typo**

In `CLAUDE.md` line 39, change `abbriviations` to `abbreviations`.

- [ ] **Step 6: Trim AGENTS.md to supplementary content only**

Replace `AGENTS.md` with:

```markdown
<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# Database Migrations

After creating or modifying migration files in `packages/supabase/supabase/migrations/`, you must regenerate the TypeScript types and commit the updated file:

```bash
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```
```

Note: The "Read `.github/copilot-instructions.md`" redirect is removed because CLAUDE.md already includes all guidelines inline via `@AGENTS.md`. This breaks the circular chain.

- [ ] **Step 7: Verify build**

```bash
npx turbo build
npx turbo lint
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore(core): clean up root infrastructure and fix stale configs"
```

---

### Task 3: Dead Code & Dependency Cleanup (`apps/web`)

**Files:**
- Modify: `apps/web/components/path/PathProfileCard.tsx:4,30`
- Modify: `apps/web/lib/engine/render.ts:28-32`
- Delete: `apps/web/lib/hooks/useStandalone.ts`
- Delete: `apps/web/lib/hooks/useKeyboardOffset.ts`
- Delete: `apps/web/components/ui/combobox.tsx`
- Modify: `apps/web/lib/types.ts` (remove `Session`)
- Modify: `apps/web/package.json` (deps cleanup)
- Modify: `apps/web/next.config.ts` (remove hardcoded IP)

- [ ] **Step 1: Fix PathProfileCard lint warnings**

In `apps/web/components/path/PathProfileCard.tsx` line 4, change:

```typescript
import { CircleUser, Stone, CirclePile, Sparkle } from "lucide-react"
```

to:

```typescript
import { CircleUser, CirclePile, Sparkle } from "lucide-react"
```

Then find and remove the `pebblesCount` variable on line 30 (read the file to find the exact line and its usage — it may be assigned from a hook but never referenced in JSX).

- [ ] **Step 2: Remove unused render.ts parameters**

In `apps/web/lib/engine/render.ts`, change the function signature from:

```typescript
export function renderPebble(
  params: PebbleParams,
  _seed: number,
  _tier: RenderTier,
): EngineOutput {
```

to:

```typescript
export function renderPebble(
  params: PebbleParams,
): EngineOutput {
```

Then update all call sites. Search for `renderPebble(` across the codebase and remove the extra arguments. The `RenderTier` import from `./types` can be removed from this file if no longer used.

- [ ] **Step 3: Delete orphaned hooks**

```bash
rm apps/web/lib/hooks/useStandalone.ts
rm apps/web/lib/hooks/useKeyboardOffset.ts
```

- [ ] **Step 4: Delete unused combobox primitive**

```bash
rm apps/web/components/ui/combobox.tsx
```

- [ ] **Step 5: Remove deprecated `Session` type**

In `apps/web/lib/types.ts`, find the `Session` type (around line 94-99) and delete it entirely. Verify no imports reference it first:

```bash
grep -r "Session" apps/web/lib/ apps/web/components/ apps/web/app/ --include="*.ts" --include="*.tsx" | grep -v "node_modules"
```

- [ ] **Step 6: Clean up package.json dependencies**

In `apps/web/package.json`:

1. Remove `@rive-app/react-canvas` from `dependencies`
2. Remove `esbuild-wasm` from `dependencies`
3. Move `shadcn` from `dependencies` to `devDependencies`

Then run:

```bash
npm install
```

- [ ] **Step 7: Remove hardcoded dev IP from next.config.ts**

In `apps/web/next.config.ts`, remove the `allowedDevOrigins` line:

```typescript
import { withSerwist } from "@serwist/turbopack";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {};

export default withSerwist(nextConfig);
```

- [ ] **Step 8: Suppress `<img>` lint warnings for data URLs**

In each file that uses `<img>` for base64 data URLs (`PebbleCard.tsx`, `QuickPebbleEditor.tsx`, `PebbleDetail.tsx`), add above the `<img>` tag:

```typescript
{/* eslint-disable-next-line @next/next/no-img-element -- base64 data URL, next/image optimization not applicable */}
```

- [ ] **Step 9: Verify build and lint**

```bash
npx turbo build
npx turbo lint
```

Expected: 0 warnings (all 8 previous warnings should be resolved).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "quality(ui): remove dead code, orphaned hooks, and unused dependencies"
```

---

### Task 4: Pattern Consistency & Redundancy Fixes (`apps/web`)

**Files:**
- Modify: `apps/web/lib/data/data-provider.ts` (add `EMPTY_STORE` export)
- Modify: `apps/web/lib/data/local-provider.ts` (import `EMPTY_STORE`)
- Modify: `apps/web/lib/data/supabase-provider.ts` (import `EMPTY_STORE`)
- Modify: `apps/web/components/layout/DataProvider.tsx` (import `EMPTY_STORE`)
- Modify: `apps/web/components/layout/ResetDataButton.tsx` (use hook instead of provider)
- Modify: `apps/web/eslint.config.mjs` (add architectural rules)

- [ ] **Step 1: Centralize `EMPTY_STORE`**

Add to `apps/web/lib/data/data-provider.ts` after the `Store` type definition (after line 24):

```typescript
export const EMPTY_STORE: Store = {
  pebbles: [],
  souls: [],
  collections: [],
  marks: [],
  pebbles_count: 0,
  karma: 0,
  karma_log: [],
  bounce: 0,
  bounce_window: [],
}
```

- [ ] **Step 2: Update consumers to import `EMPTY_STORE`**

In `apps/web/lib/data/local-provider.ts`, remove the local `EMPTY_STORE` definition (lines 20-30) and add to the import:

```typescript
import type {
  DataProvider,
  Store,
  CreatePebbleInput,
  // ... existing imports
} from "@/lib/data/data-provider"
import { EMPTY_STORE } from "@/lib/data/data-provider"
```

Do the same in `apps/web/lib/data/supabase-provider.ts` — remove lines 25-35 and import from `data-provider`.

In `apps/web/components/layout/DataProvider.tsx`, remove lines 11-21 and add:

```typescript
import { EMPTY_STORE } from "@/lib/data/data-provider"
import type { DataProvider as DataProviderInterface, Store } from "@/lib/data/data-provider"
```

- [ ] **Step 3: Fix `ResetDataButton` to use a hook**

First, check if there is an existing hook that exposes reset. If not, add a `useReset` export in a data hook or extend `provider-context.ts`. The simplest fix: since `useDataProvider` is already the hook API for this, and reset is an admin action, the pragmatic fix is to keep the current pattern but rename the import to use the hook name. However, to comply strictly with CLAUDE.md, wrap it:

Create the hook inline in `apps/web/lib/data/useReset.ts`:

```typescript
import { useDataProvider } from "@/lib/data/provider-context"
import type { Store } from "@/lib/data/data-provider"

export function useReset(): { reset: () => Promise<Store> } {
  const { provider, setStore } = useDataProvider()

  const reset = async (): Promise<Store> => {
    const snapshot = await provider.reset()
    setStore(snapshot)
    return snapshot
  }

  return { reset }
}
```

Then update `apps/web/components/layout/ResetDataButton.tsx`:

```typescript
"use client"

import { RotateCcw } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { useReset } from "@/lib/data/useReset"

export function ResetDataButton() {
  const { reset } = useReset()

  return (
    <ConfirmDialog
      trigger={
        <Button variant="ghost" size="icon" aria-label="Reset to seed data">
          <RotateCcw className="size-4" />
        </Button>
      }
      title="Reset all data?"
      description="This will replace your pebbles, souls, and collections with the original seed data. This cannot be undone."
      confirmLabel="Reset data"
      onConfirm={reset}
    />
  )
}
```

- [ ] **Step 4: Add architectural ESLint rules**

In `apps/web/eslint.config.mjs`, add rules to enforce patterns:

```javascript
import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    "public/sw.js",
    "public/sw.js.map",
  ]),
  {
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "no-restricted-imports": ["error", {
        patterns: [
          {
            group: ["@/lib/data/local-provider"],
            message: "Import data hooks from @/lib/data/ instead. Only DataProvider.tsx may import providers directly.",
          },
          {
            group: ["@/lib/data/supabase-provider"],
            message: "Import data hooks from @/lib/data/ instead. Only DataProvider.tsx may import providers directly.",
          },
        ],
      }],
    },
  },
  {
    files: ["components/layout/DataProvider.tsx"],
    rules: {
      "no-restricted-imports": "off",
    },
  },
]);

export default eslintConfig;
```

- [ ] **Step 5: Verify build and lint**

```bash
npx turbo build
npx turbo lint
```

Fix any new lint errors from the `no-explicit-any` rule (there may be some in existing code that need `unknown` instead of `any`).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "quality(core): centralize EMPTY_STORE, add useReset hook, enforce lint rules"
```

---

### Task 5: Supabase Package & Build Integrity

**Files:**
- Modify: `packages/supabase/tsconfig.json`
- Modify: `packages/supabase/package.json`
- Modify: `packages/supabase/supabase/seed.sql`
- Delete: `packages/supabase/supabase/migrations/.gitkeep`

- [ ] **Step 1: Include types directory in tsconfig**

Replace `packages/supabase/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2017",
    "module": "esnext",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "noEmit": true,
    "isolatedModules": true
  },
  "include": ["src", "types"]
}
```

- [ ] **Step 2: Replace placeholder build with type check**

In `packages/supabase/package.json`, change the build script:

```json
"build": "tsc --noEmit"
```

Note: This requires `typescript` as a devDependency. Check if it's inherited from root or needs to be added:

```bash
ls node_modules/.bin/tsc
```

If not available, add:

```bash
npm install -D typescript --workspace=packages/supabase
```

- [ ] **Step 3: Add comment to empty seed.sql**

Replace `packages/supabase/supabase/seed.sql`:

```sql
-- Seed data for local development.
--
-- Reference data (emotions, domains, card_types, pebble_shapes) is inserted
-- by the migration 20260411000000_reference_tables.sql because it is immutable
-- production data, not dev-only seed data.
--
-- Add development-only INSERT statements below (e.g., test users, sample pebbles).
```

- [ ] **Step 4: Delete unnecessary .gitkeep**

```bash
rm packages/supabase/supabase/migrations/.gitkeep
```

- [ ] **Step 5: Verify build**

```bash
npx turbo build
```

The `@pbbls/supabase` build should now run `tsc --noEmit` and catch type errors.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "quality(db): add type checking to supabase package and clean up stubs"
```

---

### Task 6: Documentation Updates

**Files:**
- Modify: `README.md`
- Modify: `apps/web/CLAUDE.md`
- Modify: `packages/supabase/CLAUDE.md`
- Modify: `.claude/settings.local.json`

- [ ] **Step 1: Update README.md**

Update the README to reflect the current monorepo state. Key changes:

1. Monorepo structure: remove `(placeholder)` from `supabase/`, keep for `shared/`
2. Web app description: replace "All data lives in localStorage — there is no backend yet" with current architecture
3. Data flow diagram: update to show dual-provider architecture
4. Tech stack table: update Storage row

Replace the `## Web app` section (lines 38-43) with:

```markdown
## Web app (`apps/web/`)

The web app is a local-first PWA built with Next.js (App Router), React, Tailwind CSS, and shadcn/ui. Data is stored in localStorage with background sync to Supabase for authenticated users. The DataProvider interface abstracts the storage layer — `LocalProvider` for anonymous/offline use, `SupabaseProvider` for authenticated users.

See [`apps/web/`](apps/web/) for the full web app documentation.
```

Replace the data flow diagram (lines 84-93) with:

```markdown
### Data flow

```
in-memory Store (React context)
↕
LocalProvider (localStorage) ──or── SupabaseProvider (localStorage + Supabase sync)
↕                                   ↕
React hooks (usePebbles, etc.)      Background push/pull to Supabase
↕
Page components → UI
```
```

Replace the tech stack table Storage row (line 101):

```markdown
| Storage     | localStorage + Supabase (local-first) |
```

In the monorepo structure (line 16), change:

```markdown
    supabase/    ← Supabase client, types & migrations
```

- [ ] **Step 2: Update `apps/web/CLAUDE.md`**

Add `SupabaseProvider` and `useSupabaseAuth` to the data layer section. Add `lib/supabase/` to the directory structure. Update the description of provider-context to reflect current state.

In the directory structure, after the `lib/data/` line, add:

```
  supabase/       → Supabase client initialization (browser + server)
```

In the data layer section, update the first bullet to:

```markdown
- **Data hooks** live in `lib/data/` — they wrap the `DataProvider` interface: `usePebbles`, `useSouls`, `useCollections`, `useMarks`, `useKarma`, `useBounce`, `useLookupMaps`, `useSupabaseAuth`, `useReset`.
```

Add after the business logic bullet:

```markdown
- **Providers**: `LocalProvider` handles localStorage for anonymous/offline use. `SupabaseProvider` adds background sync to Supabase for authenticated users. `DataProvider.tsx` switches between them based on auth state.
```

- [ ] **Step 3: Update `packages/supabase/CLAUDE.md`**

Update the status section to reflect current state. Replace the Status section with:

```markdown
## Status

- Infrastructure initialized, CLI configured
- Database schema deployed (reference tables, core tables, views, RPC functions, auth trigger)
- Generated TypeScript types from database schema
- `SupabaseProvider` implemented in `apps/web/lib/data/supabase-provider.ts`
- Type generation pipeline: modify migration → `db:reset` → `db:types` → commit
```

Update the type generation paragraph to say:

```markdown
Generated types live in `types/database.ts` and are committed to the repo. The web app will import them via `import type { Database } from "@pbbls/supabase"` once the dependency is wired.
```

- [ ] **Step 4: Remove orphaned permission from Claude settings**

In `.claude/settings.local.json`, remove the `mcp__Claude_Preview__preview_start` line:

```json
{
  "permissions": {
    "allow": [
      "Bash(npm run:*)",
      "Bash(gh issue:*)",
      "Bash(git commit:*)",
      "Bash(supabase --version)",
      "Bash(git checkout:*)",
      "WebSearch",
      "Bash(git add:*)",
      "Bash(npx turbo:*)"
    ]
  },
  "enabledMcpjsonServers": [
    "supabase"
  ]
}
```

- [ ] **Step 5: Verify build**

```bash
npx turbo build
npx turbo lint
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "docs(core): update README, CLAUDE.md files, and clean up agent config"
```

---

## Findings NOT addressed in this plan

These findings are intentionally deferred — they are real but require broader design decisions or are backlog items:

| Finding | Reason for deferral |
|---------|-------------------|
| `ColorWorldProvider.tsx` reads localStorage directly | Needs design decision on whether to extend DataProvider or create a separate theme storage abstraction |
| Login/Register pages not thin route shells | Refactoring 200+ lines per page is a separate PR — create a backlog issue |
| Login/Register OAuth UI duplication | Same as above — extract `SocialAuthButtons` as part of the auth page refactor |
| `packages/shared` not consumed | Backlog: migrate shared types when iOS development starts |
| Workspace glob auto-includes directories | Low risk, explicit listing is a preference not urgency |
| `glyphs` vs `Mark` naming mismatch | Requires a naming decision and coordinated migration + view update |
| Dual type definitions (generated vs manual) | Blocked on wiring `@pbbls/supabase` as a dependency of `apps/web` |
| SVG stroke `d` sanitization in PebbleVisual | Needs design of a validation function — separate security-focused PR |
| Notion token in `.vscode/mcp.json` | Not code-fixable — user must rotate the token manually and move it to an env var |
| Arkaik bundle updates | Use the `arkaik` skill in a separate session — surgical updates to the bundle |
| Skills split across `.agents/` and `.claude/skills/` | Needs investigation of which agent framework owns `.agents/` |
| Copilot-instructions.md Arkaik section | Low priority, Copilot-specific |
| `apps/ios` README/CLAUDE.md consolidation | Trivial, can be done anytime |
| Superpowers plans reference deleted files | Historical artifacts, not actionable |
