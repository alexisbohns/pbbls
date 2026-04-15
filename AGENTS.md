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

# Supabase — prefer RPCs for multi-table writes

Before writing any Supabase query that touches more than one table, or that does more than a simple single-row `select`/`insert`/`update`/`delete`, **check whether an RPC already exists for it** in `packages/supabase/supabase/migrations/`. Grep the migrations for `create function public.` and read the relevant ones.

- **If an RPC exists:** call it via `.rpc("name", params: ...)`. Do not re-implement the logic as chained client calls, even if the RPC is missing a small piece of what you need — extend the RPC instead.
- **If no RPC exists but the operation is multi-table or multi-statement:** create a new RPC in a migration rather than stitching multiple client calls together. Client-stitched multi-table writes are not atomic (PostgREST has no client-side transactions), so a partial failure leaves the database in an inconsistent state. RPCs run in a single Postgres transaction and can enforce ownership checks past `security definer`.
- **If the operation is a single-table, single-statement read or write:** direct client calls are fine. No RPC needed.

This rule applies to both the web app and iOS. When extending an existing RPC, keep its sibling RPCs symmetric (e.g. if you add a payload key to `update_pebble`, add the same key to `create_pebble`) so future readers don't have to hunt down asymmetries.
