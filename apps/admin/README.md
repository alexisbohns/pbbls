# @pbbls/admin

Internal back-office for managing Pebbles Lab content (changelog + announcements). Companion to `apps/web` and `apps/ios`; intentionally not a PWA.

## Local dev

1. Copy `.env.local.example` to `.env.local` and fill in the same Supabase URL + publishable key as `apps/web`.
2. From repo root: `npm run dev`. Admin runs at `http://localhost:3001`.
3. Bootstrap your admin user once via Supabase SQL editor:
   ```sql
   update public.profiles set is_admin = true where user_id = '<your-user-id>';
   ```

## Routes

- `/login` — email + password sign-in.
- `/logs` — list all changelog/announcement entries with filters.
- `/logs/new` — create.
- `/logs/[id]` — edit, publish/unpublish, delete.
- `/403` — shown when authed but not admin.
- `/auth/callback` — Supabase OAuth/email-confirm landing.
- `/auth/signout` — POST handler.

## Deployment (Vercel)

1. Create a new Vercel project pointed at the `pbbls` repo.
2. **Root directory:** `apps/admin`.
3. Build/install commands: leave Vercel defaults.
4. Env vars (copy from `apps/web` Vercel project):
   - `NEXT_PUBLIC_SUPABASE_URL`
   - `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`
5. Domain: add `admin.<consumer-domain>` in Settings → Domains; add the CNAME at the DNS provider.
6. Supabase: add `https://admin.<consumer-domain>` to Authentication → URL Configuration → Redirect URLs.

## Why a separate app?

`apps/web` is a PWA with a service worker. Admin is always-online and never installed. Mixing them fights the platform; subdomain isolation also gives a clean cookie boundary. See the design spec at `docs/superpowers/specs/2026-04-26-back-office-app-design.md`.
