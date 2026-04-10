# @pbbls/supabase

Supabase infrastructure and client library package.

## Structure

- `supabase/` — Supabase CLI project directory
  - `config.toml` — local dev configuration (committed)
  - `migrations/` — versioned SQL migration files
  - `seed.sql` — seed data for local development
- `src/` — TypeScript client code (placeholder, not yet implemented)

## CLI Commands

All commands run from this package directory (`packages/supabase/`):

| Command | Description |
|---------|-------------|
| `npm run db:start` | Start local Supabase (Postgres, Auth, Storage, etc.) |
| `npm run db:stop` | Stop local Supabase containers |
| `npm run db:status` | Show status of local Supabase services |
| `npm run db:reset` | Reset local DB, re-run migrations and seed |
| `npm run db:push` | Push local migrations to linked remote project |
| `npm run db:pull` | Pull remote schema changes as a new migration |
| `npm run db:diff` | Diff local DB schema against migrations |
| `npm run db:migration:new -- <name>` | Create a new migration file |
| `npm run db:migration:list` | List migration status |
| `npm run db:link` | Link to a remote Supabase project (interactive) |

## Linking to Remote

After creating a Supabase project on the dashboard:
1. Get the project ref from the dashboard URL or settings
2. Run `npm run db:link -- --project-ref <ref>`
3. Enter the database password when prompted

## Status

- Infrastructure initialized, CLI configured
- No SDK client code yet
- No schema migrations yet
- Will provide the Supabase client initialization and typed queries
- Will replace `apps/web/lib/data/local-provider.ts` as the `DataProvider` backend
- Must implement the `DataProvider` interface defined in `apps/web/lib/data/data-provider.ts`
