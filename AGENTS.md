<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# Instructions

Read `.github/copilot-instructions.md`

# Database Migrations

After creating or modifying migration files in `packages/supabase/supabase/migrations/`, you must regenerate the TypeScript types and commit the updated file:

```bash
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```