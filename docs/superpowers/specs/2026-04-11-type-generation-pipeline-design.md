# Type Generation Pipeline (TypeScript)

Resolves #196

## Overview

Set up a Supabase CLI-based type generation pipeline that produces TypeScript types from the local database schema. Types are committed to the repo so all consumers share a single source of truth. Swift types are deferred until the iOS app exists.

## Type Generation

### Script

Add a `db:types` script to `packages/supabase/package.json`:

```
supabase gen types typescript --local > types/database.ts
```

This requires the local Supabase instance to be running (`npm run db:start`).

### Output

Generated types are written to `packages/supabase/types/database.ts`. This file is committed to the repo — it's not gitignored. It only changes when someone runs the script after modifying migrations.

### Package export

Update `packages/supabase/src/index.ts` to re-export all generated types:

```typescript
export type * from "../types/database"
```

The web app can then import as `import type { Database } from "@pbbls/supabase"` (or any other generated type).

## When to regenerate

Run `npm run db:types --workspace=packages/supabase` after:
- Creating or modifying a migration file
- Running `db:reset` (to verify types match the applied schema)
- Pulling schema changes from remote (`db:pull`)

The local Supabase instance must be running. The workflow is:
1. `npm run db:start --workspace=packages/supabase` (if not already running)
2. `npm run db:reset --workspace=packages/supabase` (apply latest migrations)
3. `npm run db:types --workspace=packages/supabase` (regenerate types)
4. Commit the updated `types/database.ts`

## Documentation updates

### `packages/supabase/CLAUDE.md`

- Add `db:types` to the CLI Commands table
- Add `types/` to the Structure section
- Update the Status section to reflect that types are generated
- Add a "Type Generation" section explaining the workflow and when agents must regenerate

### Root `CLAUDE.md` or `AGENTS.md`

- Add a guideline that after creating or modifying migrations, the type generation script must be run and the updated types committed

## File impact

| File | Action |
|---|---|
| `packages/supabase/package.json` | Modify — add `db:types` script |
| `packages/supabase/types/database.ts` | Create — generated types (committed) |
| `packages/supabase/src/index.ts` | Modify — re-export Database type |
| `packages/supabase/CLAUDE.md` | Modify — document types workflow |
| `CLAUDE.md` or `AGENTS.md` | Modify — add migration/types guideline |

## Out of scope

- Swift type generation (deferred until iOS app exists)
- Turbo task integration (types are not part of the build pipeline)
- Auto-generation hooks (explicit manual run is preferred)
