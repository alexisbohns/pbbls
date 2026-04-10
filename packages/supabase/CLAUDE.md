# @pbbls/supabase

Supabase client library — database access, auth helpers, and real-time subscriptions. Currently a **stub**.

## Status

Placeholder package. Build and lint scripts are no-ops. Entry point `src/index.ts` is an empty export.

## When development begins

- Will provide the Supabase client initialization and typed queries.
- Will replace `apps/web/lib/data/local-provider.ts` as the `DataProvider` backend.
- Must implement the `DataProvider` interface defined in `apps/web/lib/data/data-provider.ts`.
- Auth integration maps to the `AuthContextValue` interface in `apps/web/lib/data/auth-context.ts`.
- Content data maps to domain types in `apps/web/lib/types.ts` (will eventually move to `packages/shared/`).
