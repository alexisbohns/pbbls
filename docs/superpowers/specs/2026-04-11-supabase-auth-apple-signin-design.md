# Supabase Auth Configuration (Email + Apple Sign-In)

Resolves #195

## Overview

Set up Supabase Auth for both email/password and Apple Sign-In, replace the current localStorage-based auth with Supabase Auth on the frontend, and add a database trigger to auto-create profiles on signup.

Email/password auth is already configured in the Supabase dashboard (10-char password with uppercase, lowercase, digits, special characters, 6-digit OTP). This spec covers adding Apple Sign-In and migrating the frontend auth layer.

## Apple Developer Portal Setup

### Step 1 — Create an App ID (iOS app)

1. Go to developer.apple.com/account > Certificates, Identifiers & Profiles > Identifiers
2. Click + to register a new identifier
3. Select App IDs, click Continue
4. Select App (not App Clip), click Continue
5. Fill in:
   - Description: "Pebbles"
   - Bundle ID: select Explicit, enter `app.pbbls.ios`
6. Scroll down to Capabilities, check Sign In with Apple
7. Click Continue, then Register

### Step 2 — Create a Service ID (web app)

1. Still in Identifiers, click + again
2. Select Services IDs, click Continue
3. Fill in:
   - Description: "Pebbles Web" (shown to users on the consent screen)
   - Identifier: `app.pbbls.web` (this becomes the `client_id` in Supabase)
4. Click Continue, then Register
5. Click on the newly created Service ID to edit it
6. Check Sign In with Apple, click Configure
7. In the configuration dialog:
   - Primary App ID: select the App ID created in Step 1
   - Domains: add your Supabase project domain (`<project-ref>.supabase.co`)
   - Return URLs: add `https://<project-ref>.supabase.co/auth/v1/callback`
8. Click Save, then Continue, then Save again

### Step 3 — Create a Private Key

1. Go to Keys in the sidebar, click +
2. Key Name: "Pebbles Auth Key"
3. Check Sign In with Apple, click Configure
4. Select the Primary App ID from Step 1, click Save
5. Click Continue, then Register
6. Download the key file (`.p8`) — you can only download this once
7. Note the Key ID shown on the confirmation page
8. Note your Team ID — visible in the top-right of the developer portal or under Membership

You now have four values needed for Supabase:
- Client ID (Service ID identifier): `app.pbbls.web`
- Key ID: from Step 3
- Team ID: from Apple Developer membership
- Private Key: contents of the `.p8` file

## Supabase Dashboard Configuration

1. Go to your Supabase project dashboard > Authentication > Providers
2. Find Apple in the list, toggle it on
3. Fill in:
   - Team ID: your 10-character alphanumeric Team ID
   - Client ID: `app.pbbls.web` (the Service ID identifier)
   - Secret: a generated client secret (see below)
   - Client IDs (for native sign-in): add iOS bundle identifier `app.pbbls.ios`
4. Save

### Generating the Secret

Apple doesn't give a static secret. You generate a short-lived JWT (valid up to 6 months) signed with the `.p8` key. Supabase provides a browser-based tool on their Apple auth docs page to generate it. You'll need:
- The `.p8` key file contents
- Key ID
- Team ID
- Service ID (`app.pbbls.web`)

Important: this tool doesn't work in Safari — use Firefox or Chrome. The secret must be regenerated every 6 months. Keep the `.p8` file stored securely and set a calendar reminder.

## Database: `handle_new_user` Trigger

New migration to auto-create a `profiles` row when a user signs up via `auth.users`.

```sql
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
$$ language plpgsql security definer;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
```

- `display_name`: uses Apple-provided `full_name` if available, otherwise defaults to "Pebbler"
- `security definer`: required because the trigger runs on `auth.users` but inserts into `public.profiles` which has RLS enabled
- All other profile fields use their column defaults (`onboarding_completed = false`, `color_world = 'blush-quartz'`, etc.)

## Supabase Client Setup

### New file: `apps/web/lib/supabase/client.ts`

A browser-side Supabase client using `createBrowserClient` from `@supabase/ssr`.

### Environment variables

Add to `.env.local`:
- `NEXT_PUBLIC_SUPABASE_URL` — project URL from Supabase dashboard
- `NEXT_PUBLIC_SUPABASE_ANON_KEY` — anon/public key from Supabase dashboard

## Frontend Auth Layer Replacement

### New hook: `useSupabaseAuth`

Replaces the auth logic currently in `AuthProvider.tsx`:
- Initializes a Supabase auth listener (`onAuthStateChange`) to track session state
- Exposes `user`, `profile`, `isAuthenticated`, `isLoading` — same shape as current `AuthContextValue`
- Fetches the `profiles` row whenever a session is active
- Provides `login(email, password)`, `register(email, password)`, `signInWithApple()`, and `logout()` methods

### Updated `AuthProvider.tsx`

Swaps internal logic from `LocalProvider` calls to the new `useSupabaseAuth` hook. The context shape (`AuthContextValue`) stays the same, so all consumers (`AuthGate`, any component using `useAuth()`) keep working without changes.

### Updated login page (`app/login/page.tsx`)

- Change username field to email field
- Add "Sign in with Apple" button calling `signInWithApple()`
- Keep password field as-is

### Updated register page (`app/register/page.tsx`)

- Change username field to email field
- Keep password + confirmation + terms/privacy checkboxes
- Add "Sign up with Apple" button (same Apple flow — the trigger handles profile creation)

### New auth callback route (`app/auth/callback/route.ts`)

A Route Handler (not a page) that handles the OAuth redirect from Apple Sign-In. Receives the authorization code as a query parameter, exchanges it for a Supabase session using `exchangeCodeForSession`, then redirects the user to `/path` (or `/onboarding` if `onboarding_completed` is false).

## File Impact Summary

| File | Action |
|---|---|
| `lib/supabase/client.ts` | New — browser Supabase client |
| `app/auth/callback/route.ts` | New — OAuth redirect Route Handler |
| `lib/data/auth-context.ts` | Keep as-is — context shape unchanged |
| `components/layout/AuthProvider.tsx` | Rewrite — swap to Supabase auth |
| `app/login/page.tsx` | Update — email field, Apple button |
| `app/register/page.tsx` | Update — email field, Apple button |
| `components/auth/AuthGate.tsx` | Keep as-is — reads from same `useAuth()` |
| `lib/data/local-provider.ts` | Keep — still used for content data |
| `lib/data/data-provider.ts` | Keep — interface unchanged |
| `lib/data/password.ts` | Remove — Supabase handles password hashing |
| New migration: `handle_new_user` | New — auto-creates profile on signup |

## Dependencies

- `@supabase/ssr` — Supabase client for Next.js (SSR-compatible browser client)
- `@supabase/supabase-js` — core Supabase JS client (likely already a dependency via the supabase package)

## Out of Scope

- Content data migration from localStorage to Supabase (pebbles, souls, collections, etc.)
- Optimistic/local-first caching pattern for content data
- Server-side auth middleware
- Magic link / passwordless login
