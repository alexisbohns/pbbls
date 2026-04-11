# Supabase Auth + Apple Sign-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the localStorage-based auth system with Supabase Auth, adding Apple Sign-In alongside email/password, and auto-create profiles on signup via a database trigger.

**Architecture:** A new database trigger creates a `profiles` row whenever `auth.users` gets an insert. The frontend swaps its auth layer from `LocalProvider` to a `useSupabaseAuth` hook that wraps `@supabase/ssr`'s browser client. The `AuthContextValue` shape stays compatible so `AuthGate` and all `useAuth()` consumers keep working. An OAuth callback route handles Apple's redirect flow.

**Tech Stack:** Supabase Auth, `@supabase/ssr`, Next.js 16.2.0 App Router, TypeScript strict mode

**Note on testing:** This project has no test infrastructure yet (V1). Steps focus on implementation with build/lint verification. Code is structured to be testable when tests are added later.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `packages/supabase/supabase/migrations/20260411000004_auth_trigger.sql` | Create | `handle_new_user` trigger function |
| `apps/web/lib/supabase/client.ts` | Create | Browser-side Supabase client singleton |
| `apps/web/.env.local.example` | Create | Documents required environment variables |
| `apps/web/lib/types.ts` | Modify | Update auth types for Supabase (Account→email, Profile→user_id) |
| `apps/web/lib/data/auth-context.ts` | Modify | Add `signInWithApple` method to `AuthContextValue` |
| `apps/web/lib/data/useSupabaseAuth.ts` | Create | Hook that manages Supabase auth state and actions |
| `apps/web/components/layout/AuthProvider.tsx` | Rewrite | Delegate to `useSupabaseAuth` instead of `LocalProvider` |
| `apps/web/app/auth/callback/route.ts` | Create | OAuth redirect handler (exchanges code for session) |
| `apps/web/app/login/page.tsx` | Modify | Email field instead of username, add Apple button |
| `apps/web/app/register/page.tsx` | Modify | Email field instead of username, add Apple button |
| `apps/web/lib/data/password.ts` | Delete | No longer needed — Supabase handles password hashing |

---

### Task 1: Database migration — `handle_new_user` trigger

**Files:**
- Create: `packages/supabase/supabase/migrations/20260411000004_auth_trigger.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- ============================================================
-- Auth trigger: auto-create profile on signup
-- ============================================================

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

- [ ] **Step 2: Verify the migration file is correctly placed**

Run: `ls packages/supabase/supabase/migrations/`

Expected: five migration files in order:
```
20260411000000_reference_tables.sql
20260411000001_core_tables.sql
20260411000002_views.sql
20260411000003_rpc_functions.sql
20260411000004_auth_trigger.sql
```

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20260411000004_auth_trigger.sql
git commit -m "feat(db): add handle_new_user trigger for auto profile creation"
```

---

### Task 2: Install dependencies

**Files:**
- Modify: `apps/web/package.json`

- [ ] **Step 1: Install `@supabase/ssr` in the web app**

Run from repo root:
```bash
npm install --workspace=apps/web @supabase/ssr @supabase/supabase-js
```

Expected: both packages added to `apps/web/package.json` dependencies.

- [ ] **Step 2: Verify installation**

Run: `grep -E "supabase" apps/web/package.json`

Expected output includes:
```
"@supabase/ssr": "^<version>",
"@supabase/supabase-js": "^<version>",
```

- [ ] **Step 3: Commit**

```bash
git add apps/web/package.json package-lock.json
git commit -m "chore(auth): add @supabase/ssr and @supabase/supabase-js dependencies"
```

---

### Task 3: Environment setup

**Files:**
- Create: `apps/web/.env.local.example`

- [ ] **Step 1: Create `.env.local.example`**

```bash
# Supabase project credentials
# Get these from: Supabase Dashboard > Project Settings > API
NEXT_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=<your-anon-key>
```

- [ ] **Step 2: Verify `.env.local` is in `.gitignore`**

Run: `grep ".env.local" apps/web/.gitignore` or `grep ".env.local" .gitignore`

If not found, add `.env.local` to the appropriate `.gitignore`.

- [ ] **Step 3: Commit**

```bash
git add apps/web/.env.local.example
git commit -m "chore(auth): add .env.local.example with Supabase env vars"
```

---

### Task 4: Supabase browser client

**Files:**
- Create: `apps/web/lib/supabase/client.ts`

- [ ] **Step 1: Create the client file**

```typescript
import { createBrowserClient } from "@supabase/ssr"

export function createClient() {
  return createBrowserClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/lib/supabase/client.ts
git commit -m "feat(auth): add Supabase browser client"
```

---

### Task 5: Update auth types

**Files:**
- Modify: `apps/web/lib/types.ts`
- Modify: `apps/web/lib/data/auth-context.ts`

The current types use `Account` with `username` and `password_hash`, `Profile` with `account_id`, and input types with `username`. These need to reflect Supabase's model.

- [ ] **Step 1: Update types in `lib/types.ts`**

Replace the auth section (lines 72–110) with:

```typescript
// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export type Account = {
  id: string
  email: string
  created_at: string
}

export type Profile = {
  id: string
  user_id: string
  display_name: string
  onboarding_completed: boolean
  color_world: ColorWorld
  terms_accepted_at: string | null
  privacy_accepted_at: string | null
  created_at: string
  updated_at: string
}

export type RegisterInput = {
  email: string
  password: string
  terms_accepted: boolean
  privacy_accepted: boolean
}
export type LoginInput = { email: string; password: string }
export type UpdateProfileInput = Partial<
  Omit<Profile, "id" | "user_id" | "created_at" | "updated_at">
>
```

Key changes:
- `Account.username` → `Account.email`, removed `password_hash` (Supabase manages this)
- `Profile.account_id` → `Profile.user_id` (matches the DB schema)
- `Session` type removed (Supabase manages sessions internally)
- `RegisterInput.username` → `RegisterInput.email`
- `LoginInput.username` → `LoginInput.email`
- `UpdateProfileInput` omits `user_id` instead of `account_id`

- [ ] **Step 2: Add `signInWithApple` to `AuthContextValue` in `lib/data/auth-context.ts`**

Add to the `AuthContextValue` type, after the `register` method:

```typescript
export type AuthContextValue = {
  /** The logged-in account, or null when unauthenticated. */
  user: Account | null
  /** The logged-in user's profile, or null when unauthenticated. */
  profile: Profile | null
  /** Convenience flag — equivalent to `user !== null`. */
  isAuthenticated: boolean
  /** True while the initial session is being rehydrated. */
  isLoading: boolean
  login(input: LoginInput): Promise<void>
  register(input: RegisterInput): Promise<void>
  signInWithApple(): Promise<void>
  logout(): Promise<void>
  updateProfile(input: UpdateProfileInput): Promise<Profile>
}
```

Also update the imports to remove `Account` from the import (since `Account` is still used but `Session` is not — verify that `Session` is no longer imported anywhere):

```typescript
import type {
  Account,
  Profile,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
} from "@/lib/types"
```

(This import stays the same — `Session` was never imported here.)

- [ ] **Step 3: Fix any TypeScript errors from the type changes**

Run: `npx tsc --noEmit` from `apps/web/`

Expected: errors in `AuthProvider.tsx`, `local-provider.ts`, `login/page.tsx`, `register/page.tsx` — these will be fixed in subsequent tasks. The types themselves should be internally consistent.

- [ ] **Step 4: Commit**

```bash
git add apps/web/lib/types.ts apps/web/lib/data/auth-context.ts
git commit -m "feat(auth): update auth types for Supabase (email-based, add signInWithApple)"
```

---

### Task 6: Create the `useSupabaseAuth` hook

**Files:**
- Create: `apps/web/lib/data/useSupabaseAuth.ts`

This hook replaces the auth logic currently inlined in `AuthProvider.tsx`. It manages Supabase auth state, listens for auth changes, fetches the profile, and exposes auth actions.

- [ ] **Step 1: Create `useSupabaseAuth.ts`**

```typescript
"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { createClient } from "@/lib/supabase/client"
import type {
  Account,
  Profile,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
} from "@/lib/types"
import type { AuthContextValue } from "@/lib/data/auth-context"

/**
 * Manages Supabase auth state and exposes it in the shape expected by
 * AuthContext. Drop-in replacement for the old LocalProvider-based auth
 * logic in AuthProvider.
 */
export function useSupabaseAuth(): AuthContextValue {
  const supabase = useRef(createClient()).current

  const [user, setUser] = useState<Account | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Fetch the profile row for a given user ID. */
  const fetchProfile = useCallback(
    async (userId: string): Promise<Profile | null> => {
      const { data, error } = await supabase
        .from("profiles")
        .select("*")
        .eq("user_id", userId)
        .single()

      if (error || !data) return null
      return data as Profile
    },
    [supabase],
  )

  /** Map a Supabase user to our Account type. */
  const toAccount = useCallback(
    (supabaseUser: { id: string; email?: string; created_at: string }): Account => ({
      id: supabaseUser.id,
      email: supabaseUser.email ?? "",
      created_at: supabaseUser.created_at,
    }),
    [],
  )

  // -----------------------------------------------------------------------
  // Auth state listener
  // -----------------------------------------------------------------------

  useEffect(() => {
    // Check for existing session on mount
    supabase.auth.getSession().then(async ({ data: { session } }) => {
      if (session?.user) {
        setUser(toAccount(session.user))
        const prof = await fetchProfile(session.user.id)
        setProfile(prof)
      }
      setIsLoading(false)
    })

    // Listen for auth state changes (login, logout, token refresh)
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, session) => {
      if (session?.user) {
        setUser(toAccount(session.user))
        const prof = await fetchProfile(session.user.id)
        setProfile(prof)
      } else {
        setUser(null)
        setProfile(null)
      }
    })

    return () => subscription.unsubscribe()
  }, [supabase, toAccount, fetchProfile])

  // -----------------------------------------------------------------------
  // Auth actions
  // -----------------------------------------------------------------------

  const login = useCallback(
    async (input: LoginInput) => {
      const { error } = await supabase.auth.signInWithPassword({
        email: input.email,
        password: input.password,
      })
      if (error) throw new Error(error.message)
    },
    [supabase],
  )

  const register = useCallback(
    async (input: RegisterInput) => {
      const { error } = await supabase.auth.signUp({
        email: input.email,
        password: input.password,
        options: {
          data: {
            terms_accepted_at: input.terms_accepted ? new Date().toISOString() : null,
            privacy_accepted_at: input.privacy_accepted ? new Date().toISOString() : null,
          },
        },
      })
      if (error) throw new Error(error.message)
    },
    [supabase],
  )

  const signInWithApple = useCallback(async () => {
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "apple",
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    })
    if (error) throw new Error(error.message)
  }, [supabase])

  const logout = useCallback(async () => {
    const { error } = await supabase.auth.signOut()
    if (error) throw new Error(error.message)
  }, [supabase])

  const updateProfile = useCallback(
    async (input: UpdateProfileInput): Promise<Profile> => {
      if (!user) throw new Error("Not authenticated")

      const { data, error } = await supabase
        .from("profiles")
        .update(input)
        .eq("user_id", user.id)
        .select()
        .single()

      if (error) throw new Error(error.message)

      const updated = data as Profile
      setProfile(updated)
      return updated
    },
    [supabase, user],
  )

  return {
    user,
    profile,
    isAuthenticated: user !== null,
    isLoading,
    login,
    register,
    signInWithApple,
    logout,
    updateProfile,
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/lib/data/useSupabaseAuth.ts
git commit -m "feat(auth): add useSupabaseAuth hook"
```

---

### Task 7: Rewrite `AuthProvider`

**Files:**
- Modify: `apps/web/components/layout/AuthProvider.tsx`

The rewrite swaps all `LocalProvider` logic for the new `useSupabaseAuth` hook. The component becomes a thin wrapper.

- [ ] **Step 1: Rewrite `AuthProvider.tsx`**

Replace the entire file contents with:

```typescript
"use client"

import { AuthContext } from "@/lib/data/auth-context"
import { useSupabaseAuth } from "@/lib/data/useSupabaseAuth"

/**
 * Client-only wrapper that manages auth state via Supabase and exposes it
 * to the React tree via AuthContext.
 *
 * Uses the useSupabaseAuth hook which handles session rehydration,
 * auth state changes, and profile fetching.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const auth = useSupabaseAuth()

  return (
    <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
  )
}
```

- [ ] **Step 2: Verify `AuthProvider` no longer imports from `provider-context` or `LocalProvider`**

Run: `grep -E "useDataProvider|LocalProvider|provider-context|local-provider" apps/web/components/layout/AuthProvider.tsx`

Expected: no output (no matches).

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/layout/AuthProvider.tsx
git commit -m "feat(auth): rewrite AuthProvider to use Supabase auth"
```

---

### Task 8: Auth callback route

**Files:**
- Create: `apps/web/app/auth/callback/route.ts`

This Route Handler receives the OAuth redirect from Apple Sign-In, exchanges the authorization code for a Supabase session, then redirects the user.

- [ ] **Step 1: Create the route handler**

```typescript
import { NextResponse } from "next/server"
import { createClient } from "@/lib/supabase/client"

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url)
  const code = searchParams.get("code")

  if (code) {
    const supabase = createClient()
    const { error } = await supabase.auth.exchangeCodeForSession(code)

    if (!error) {
      // Check if user needs onboarding
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

  // If something went wrong, redirect to login with error
  return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`)
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/app/auth/callback/route.ts
git commit -m "feat(auth): add OAuth callback route handler"
```

---

### Task 9: Update login page

**Files:**
- Modify: `apps/web/app/login/page.tsx`

Changes: username field → email field, add "Sign in with Apple" button.

- [ ] **Step 1: Update the login page**

Replace the entire file contents with:

```typescript
"use client"

import { useState, useEffect, type FormEvent } from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export default function LoginPage() {
  const { login, signInWithApple, profile, isAuthenticated, isLoading } =
    useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()

  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace(profile?.onboarding_completed ? "/path" : "/onboarding")
    }
  }, [isLoading, isAuthenticated, profile, router])

  // Show error from OAuth callback failure
  useEffect(() => {
    if (searchParams.get("error") === "auth_callback_failed") {
      setError("Sign-in failed. Please try again.")
    }
  }, [searchParams])

  if (isLoading || isAuthenticated) return null

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!email.trim() || !password) {
      setError("Please fill in all fields.")
      return
    }

    setSubmitting(true)
    try {
      await login({ email: email.trim(), password })
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
      setSubmitting(false)
    }
  }

  const handleAppleSignIn = async () => {
    setError(null)
    try {
      await signInWithApple()
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
    }
  }

  return (
    <section className="flex min-h-full flex-col items-center justify-center px-6 text-center">
      <h1 className="text-4xl font-bold tracking-tight">Log in</h1>
      <p className="mt-3 max-w-sm text-muted-foreground">
        Welcome back. Pick up where you left off.
      </p>

      <form
        onSubmit={handleSubmit}
        className="mt-8 flex w-full max-w-xs flex-col gap-4"
        noValidate
      >
        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="login-email" className="text-sm font-medium">
            Email
          </label>
          <Input
            id="login-email"
            type="email"
            autoComplete="email"
            autoCapitalize="none"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="login-password" className="text-sm font-medium">
            Password
          </label>
          <Input
            id="login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <Button type="submit" size="lg" disabled={submitting}>
          {submitting ? "Logging in…" : "Log in"}
        </Button>
      </form>

      <div className="mt-4 flex w-full max-w-xs flex-col gap-3">
        <div className="relative flex items-center justify-center">
          <span className="absolute inset-x-0 top-1/2 h-px bg-border" />
          <span className="relative bg-background px-2 text-xs text-muted-foreground">
            or
          </span>
        </div>

        <Button
          variant="outline"
          size="lg"
          onClick={handleAppleSignIn}
          disabled={submitting}
          aria-label="Sign in with Apple"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
            className="mr-2"
            aria-hidden="true"
          >
            <path d="M17.05 20.28c-.98.95-2.05.88-3.08.4-1.09-.5-2.08-.48-3.24 0-1.44.62-2.2.44-3.06-.4C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z" />
          </svg>
          Sign in with Apple
        </Button>
      </div>

      <Link
        href="/register"
        className="mt-6 text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
      >
        Create account
      </Link>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/app/login/page.tsx
git commit -m "feat(auth): update login page with email field and Apple Sign-In"
```

---

### Task 10: Update register page

**Files:**
- Modify: `apps/web/app/register/page.tsx`

Changes: username field → email field, add "Sign up with Apple" button.

- [ ] **Step 1: Update the register page**

Replace the entire file contents with:

```typescript
"use client"

import { useState, useEffect, type FormEvent } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"

export default function RegisterPage() {
  const { register, signInWithApple, isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [privacyAccepted, setPrivacyAccepted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace("/onboarding")
    }
  }, [isLoading, isAuthenticated, router])

  if (isLoading || isAuthenticated) return null

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!email.trim() || !password || !confirmPassword) {
      setError("Please fill in all fields.")
      return
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match.")
      return
    }

    if (!termsAccepted || !privacyAccepted) {
      setError("You must accept the Terms of Service and Privacy Policy.")
      return
    }

    setSubmitting(true)
    try {
      await register({
        email: email.trim(),
        password,
        terms_accepted: termsAccepted,
        privacy_accepted: privacyAccepted,
      })
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
      setSubmitting(false)
    }
  }

  const handleAppleSignIn = async () => {
    setError(null)
    try {
      await signInWithApple()
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Something went wrong."
      setError(message)
    }
  }

  return (
    <section className="flex min-h-full flex-col items-center justify-center px-6 text-center">
      <h1 className="text-4xl font-bold tracking-tight">Create account</h1>
      <p className="mt-3 max-w-sm text-muted-foreground">
        Start collecting meaningful moments.
      </p>

      <form
        onSubmit={handleSubmit}
        className="mt-8 flex w-full max-w-xs flex-col gap-4"
        noValidate
      >
        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="register-email" className="text-sm font-medium">
            Email
          </label>
          <Input
            id="register-email"
            type="email"
            autoComplete="email"
            autoCapitalize="none"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="register-password" className="text-sm font-medium">
            Password
          </label>
          <Input
            id="register-password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        <div className="flex flex-col gap-1.5 text-left">
          <label htmlFor="register-confirm" className="text-sm font-medium">
            Confirm password
          </label>
          <Input
            id="register-confirm"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            aria-invalid={error ? true : undefined}
            disabled={submitting}
          />
        </div>

        <div className="flex items-start gap-2 text-left">
          <Checkbox
            id="register-terms"
            checked={termsAccepted}
            onCheckedChange={(checked) => setTermsAccepted(checked)}
            disabled={submitting}
            required
          />
          <label
            htmlFor="register-terms"
            className="text-sm text-muted-foreground"
          >
            I accept the{" "}
            <Link
              href="/docs/terms"
              target="_blank"
              rel="noopener noreferrer"
              className="underline underline-offset-4 hover:text-foreground"
            >
              Terms of Service
            </Link>
          </label>
        </div>

        <div className="flex items-start gap-2 text-left">
          <Checkbox
            id="register-privacy"
            checked={privacyAccepted}
            onCheckedChange={(checked) => setPrivacyAccepted(checked)}
            disabled={submitting}
            required
          />
          <label
            htmlFor="register-privacy"
            className="text-sm text-muted-foreground"
          >
            I accept the{" "}
            <Link
              href="/docs/privacy"
              target="_blank"
              rel="noopener noreferrer"
              className="underline underline-offset-4 hover:text-foreground"
            >
              Privacy Policy
            </Link>
          </label>
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <Button
          type="submit"
          size="lg"
          disabled={submitting || !termsAccepted || !privacyAccepted}
        >
          {submitting ? "Creating account…" : "Create account"}
        </Button>
      </form>

      <div className="mt-4 flex w-full max-w-xs flex-col gap-3">
        <div className="relative flex items-center justify-center">
          <span className="absolute inset-x-0 top-1/2 h-px bg-border" />
          <span className="relative bg-background px-2 text-xs text-muted-foreground">
            or
          </span>
        </div>

        <Button
          variant="outline"
          size="lg"
          onClick={handleAppleSignIn}
          disabled={submitting}
          aria-label="Sign up with Apple"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
            className="mr-2"
            aria-hidden="true"
          >
            <path d="M17.05 20.28c-.98.95-2.05.88-3.08.4-1.09-.5-2.08-.48-3.24 0-1.44.62-2.2.44-3.06-.4C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z" />
          </svg>
          Sign up with Apple
        </Button>
      </div>

      <Link
        href="/login"
        className="mt-6 text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
      >
        Log in
      </Link>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/app/register/page.tsx
git commit -m "feat(auth): update register page with email field and Apple Sign-In"
```

---

### Task 11: Remove `password.ts` and fix `LocalProvider` auth references

**Files:**
- Delete: `apps/web/lib/data/password.ts`
- Modify: `apps/web/lib/data/local-provider.ts` (if it imports password.ts or references old auth types)

- [ ] **Step 1: Delete `password.ts`**

```bash
rm apps/web/lib/data/password.ts
```

- [ ] **Step 2: Check if `local-provider.ts` imports from `password.ts`**

Run: `grep "password" apps/web/lib/data/local-provider.ts`

If it does, the auth methods in `LocalProvider` (`login`, `register`, `logout`, `getSession`, `getAccount`, `getProfile`) are now dead code for auth purposes. However, `LocalProvider` is still used for content data, so it must remain. Comment out or remove the auth method implementations, replacing them with stubs that throw:

```typescript
// Auth methods — now handled by Supabase Auth (useSupabaseAuth hook)
async login(): Promise<void> {
  throw new Error("Auth is handled by Supabase — use useSupabaseAuth")
}
async register(): Promise<void> {
  throw new Error("Auth is handled by Supabase — use useSupabaseAuth")
}
async logout(): Promise<void> {
  throw new Error("Auth is handled by Supabase — use useSupabaseAuth")
}
getSession() { return null }
async getAccount() { return undefined }
async getProfile() { return undefined }
async updateProfile(): Promise<never> {
  throw new Error("Auth is handled by Supabase — use useSupabaseAuth")
}
```

Remove the `import { hashPassword, verifyPassword } from "./password"` line.

- [ ] **Step 3: Verify no other files import from `password.ts`**

Run: `grep -r "password" apps/web/lib/data/ --include="*.ts" --include="*.tsx" -l`

Expected: only `local-provider.ts` (now updated) and `useSupabaseAuth.ts` (which doesn't import password.ts).

- [ ] **Step 4: Commit**

```bash
git add -A apps/web/lib/data/password.ts apps/web/lib/data/local-provider.ts
git commit -m "chore(auth): remove password.ts and stub LocalProvider auth methods"
```

---

### Task 12: Build and lint verification

- [ ] **Step 1: Run the TypeScript compiler**

Run from repo root:
```bash
npm run build
```

Expected: build succeeds with no type errors.

- [ ] **Step 2: Run the linter**

Run from repo root:
```bash
npm run lint
```

Expected: no lint errors.

- [ ] **Step 3: Fix any errors found**

If there are type errors or lint issues, fix them. Common issues:
- Import paths that reference deleted files
- Type mismatches from the `Account`/`Profile` field changes (especially `account_id` → `user_id` and `username` → `email`)
- Components that destructure `username` from `user` — update to `email`
- The `Checkbox` `onCheckedChange` type might need `checked === true` instead of just `checked`

After fixing, re-run build and lint to verify.

- [ ] **Step 4: Final commit if fixes were needed**

```bash
git add -A
git commit -m "fix(auth): resolve build and lint errors from auth migration"
```
