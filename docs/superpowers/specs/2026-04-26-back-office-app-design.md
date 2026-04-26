# M26 · Back-office app — design

**Status:** approved design, ready for implementation plan
**Milestone:** [M26 · Back-Office](https://github.com/alexisbohns/pbbls/milestone/26)
**Date:** 2026-04-26

## Goal

Introduce an internal back-office for managing Lab content (changelog and announcements stored in `public.logs`). The back-office is the foundation for future admin surfaces (themes, skins, community glyphs) but **M26's deliverable is logs CRUD only**.

## Non-goals

- No themes / skins / glyph review UI (later milestones).
- No "manage admins" surface — first admin is set via SQL.
- No public-facing webapp Lab page (orthogonal; iOS already consumes `logs`).
- No analytics, audit log, or moderation history.

## Architectural decision: separate app

The back-office is a **new Next.js app at `apps/admin/`**, not routes inside `apps/web/`. Reasons:

- `apps/web/` is a PWA (service worker, manifest, install prompts, offline-first via `LocalProvider`). Admin work is the opposite of every PWA assumption — always online, never installed, single trusted user. Mixing them fights the platform.
- Admin-only deps (markdown editor, image tooling) would otherwise ship to every consumer.
- Subdomain isolation (`admin.<domain>`) gives a clean cookie/session boundary — consumer sessions cannot grant admin access.
- M26 is the cheapest moment to split. Splitting later, after a year of admin code lives in `apps/web`, is a much bigger migration.
- `packages/supabase` and `packages/shared` already exist, so cross-app sharing is free.

A hybrid (`(admin)` route group with its own root layout, no SW) was considered and rejected: it keeps the bundle and conceptual mixing without saving the deployment.

## Monorepo layout

```
pbbls/
  apps/
    web/         (existing — consumer PWA)
    ios/         (existing)
    admin/       ← NEW
  packages/
    supabase/    (shared client + types + migrations + RLS)
    shared/      (shared domain types if needed)
```

## Stack (`apps/admin/`)

- Next.js 16 App Router, React 19, TypeScript strict
- Tailwind CSS 4 + shadcn/ui (fresh `components.json`; primitives copied per shadcn convention — not imported from `apps/web`)
- `@supabase/ssr` and `@supabase/supabase-js` (already used by `apps/web`)
- One markdown editor for `body_md_en`/`body_md_fr` — concrete pick deferred to the implementation plan; a lightweight, SSR-friendly editor (e.g. `@uiw/react-md-editor` or equivalent) is the target.
- No service worker, no manifest, no `next-themes`/PWA scaffolding.
- No `DataProvider` / `LocalProvider` abstraction — admin reads/writes Supabase directly.

Turborepo picks up `apps/admin` automatically once it's added to the workspace; existing `dev`/`build`/`lint` tasks need no change.

## Existing infrastructure reused

All the back-office's data layer already exists:

- `public.logs` table — bilingual (EN required, FR optional), draft/publish, cover image path, external URL, status pipeline (`backlog`/`planned`/`in_progress`/`shipped`), species (`announcement`/`feature`), platform (`web`/`ios`/`android`/`all`).
- `public.profiles.is_admin` boolean column.
- `public.is_admin(uuid)` SQL helper (`security definer`, callable from RLS).
- RLS policies on `logs`: public reads gated on `published = true`; all writes and unpublished reads gated on `is_admin(auth.uid())`.
- `lab-assets` storage bucket (public read) for cover images.

**No new migrations are required for M26.** The back-office is a UI layer over schema that already exists.

## Auth model

**Login flow.**
- Admin uses the same Supabase project (one `auth.users`, one `profiles`).
- Login form lives at `admin.<domain>/login` and only redirects to admin pages.
- Auth method: **email + password**, matching `apps/web/app/login/page.tsx` today. The OAuth options (`signInWithGoogle`, `signInWithApple`) used in the consumer app are intentionally not ported to admin — fewer moving parts on a single-user surface.
- Cookies are scoped to the `admin.` subdomain. A consumer-app session does not grant admin access, and vice versa.

**Authorization gate (defense in depth).**

1. **Server-side route gate.** A `requireAdmin()` helper runs in the root admin layout (server component). It:
   - Reads the session via `@supabase/ssr`.
   - Calls `public.is_admin(auth.uid())`.
   - Redirects to `/login` if no session, or to `/403` if the session exists but `is_admin = false`.
   - Every admin route inherits the gate via the layout — no per-route opt-in to forget.
2. **RLS at the database.** Already enforced. Even if the server gate were bypassed, `logs` writes and unpublished reads require `is_admin(auth.uid())`. The gate is for UX; RLS is the security boundary.

**Bootstrapping the first admin.** No UI. Set `update profiles set is_admin = true where user_id = '...'` once via SQL.

## UI surface for M26

### Routes

```
/login           public — Supabase auth form
/                redirects to /logs
/logs            list of all logs (drafts + published)
/logs/new        create form
/logs/[id]       edit form (same component as /new)
/403             shown when authed but not admin
```

### `/logs` (list view)

- Table with columns: `title_en`, `species`, `platform`, `status`, `published`, `updated_at`.
- Filters: species, status, published (yes/no/all). Server-side filtering via query params.
- Row click navigates to `/logs/[id]`.
- Top-right "New log" button → `/logs/new`.
- No pagination in M26 (volume is low). Sort: `updated_at desc`.

### `/logs/new` and `/logs/[id]` (editor)

A single form component used by both routes. Two-column layout (or tabbed) for EN/FR fields:

| Field | Type | Required |
|---|---|---|
| `species` | radio (announcement / feature) | yes |
| `platform` | radio (web / ios / android / all) | yes |
| `status` | radio (backlog / planned / in_progress / shipped) | yes |
| `title_en` | text | yes |
| `title_fr` | text | no |
| `summary_en` | textarea (plain text) | yes |
| `summary_fr` | textarea (plain text) | no |
| `body_md_en` | markdown editor | no |
| `body_md_fr` | markdown editor | no |
| `cover_image` | file upload → `lab-assets` bucket → stores returned path in `cover_image_path` | no |
| `external_url` | url | no |
| `published` | toggle | — |

Footer actions:

- **Save draft** — submit with `published = false`. If the row was already published and the toggle is now off, also clear `published_at`.
- **Publish** — submit with `published = true`. Server action sets `published_at = now()` if it was previously null.
- **Delete** (only on `/logs/[id]`) — confirm dialog, then delete and redirect to `/logs`.

### Image upload

- Browser uploads file directly to the `lab-assets` bucket via the client Supabase SDK. The file does not round-trip through the Next.js server.
- The returned object path is sent to a server action that updates `cover_image_path`.
- The bucket is public-read, so iOS continues to render covers without signed URLs.

### Layout shell

- Sidebar nav with one entry today: **Logs**. Built so adding more sections (Themes, Skins, Glyphs) later is trivial.
- Top bar with the signed-in admin's email and a Sign out action.

## Data access pattern

- **Reads** in server components using the SSR Supabase client. RLS lets admins see drafts.
- **Writes** in server actions. Each action calls `revalidatePath('/logs')` and the affected row path.
- **Image upload** client-side direct to storage; the path is then committed via server action.
- **No new RPCs.** Every operation is single-table, single-statement on `logs` — well below the project's "RPC required" bar (multi-table writes). Per `AGENTS.md`, direct client calls are fine here.
- **Type safety** via `import type { Database } from "@pbbls/supabase"`. Inserts and updates are checked at compile time.

## Error handling

- Every server action wraps Supabase calls; errors surface as form-level error messages **and** a `console.error` with a labelled message (matching the project's "no silent failures" rule).
- No `withTimeout` wrapper required: admin is always online and there is no PWA loading-state risk to debug.
- No watchdog timers (those exist in `apps/web` to protect PWA cold starts; not relevant here).

## Deployment (Vercel)

Subdomain target: `admin.<consumer-domain>` (final domain TBD; preview URL is fine until the consumer app picks one).

Setup steps (the user will do these; this spec documents them so the implementation plan can reference them):

1. Create a new Vercel project pointed at the same `pbbls` repo.
2. Set **Root directory** to `apps/admin`. Vercel auto-detects Next.js.
3. Leave Vercel's default build/install commands — Turborepo handles them.
4. Environment variables (copy from the existing `apps/web` Vercel project):
   - `NEXT_PUBLIC_SUPABASE_URL`
   - `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` (matches the name `apps/web` uses)
   - No service-role key — RLS is the authorization layer.
5. Domain: in **Settings → Domains**, add `admin.<consumer-domain>`. Add the CNAME at the DNS provider. Vercel auto-provisions SSL.
6. Supabase: in dashboard → **Authentication → URL Configuration**, add `https://admin.<consumer-domain>` to the allowed redirect URLs (used by email-confirmation and password-reset flows; relevant if those features are surfaced later).

Until the custom subdomain is wired, every PR ships a Vercel preview at `pbbls-admin-<hash>.vercel.app` automatically.

## Risks & open questions

- **Markdown editor pick:** deferred to the implementation plan. Constraint: SSR-safe and small bundle.
- **Consumer domain not yet decided:** zero blocker — admin ships to a Vercel preview URL until the domain is chosen, then a CNAME is added.

## Out of scope (named so we don't drift)

- Themes, skins, community glyph review.
- Submission queues from non-admin users.
- Audit logging, change history, version history of log entries.
- Bulk operations, CSV import/export.
- Pagination, full-text search.
- A "manage admins" UI.
- A consumer-facing web Lab page.

## Acceptance criteria for M26

1. `apps/admin` exists in the monorepo, builds via `npm run build`, lints clean via `npm run lint`.
2. Admin can sign in at `/login` with email + password.
3. A non-admin authed user is redirected to `/403`.
4. Admin can list, create, edit, delete, and publish/unpublish entries in `public.logs`, including bilingual fields and a cover image upload to `lab-assets`.
5. iOS Lab continues to read `logs` without change (no schema or contract change is introduced by this milestone).
6. The admin app deploys to a Vercel preview URL on every PR; the custom subdomain is documented but not blocking.
