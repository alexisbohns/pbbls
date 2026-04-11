# Auth & Data Layer Redesign

**Date:** 2026-04-11
**Status:** Approved
**Resolves:** #232

## Problem

The auth lifecycle and data sync layer have interacting race conditions that cause:
- Session loss on page refresh (random, intermittent)
- Pebbles not reaching Supabase (fire-and-forget push failures swallowed)
- Pebble duplication (sync overwrites trigger re-creation)
- Blank screens during auth state transitions
- No error feedback to the user

Root causes:
- Two-provider switching (LocalProvider ↔ SupabaseProvider) with complex lifecycle
- localStorage cache diverging from Supabase state
- Fire-and-forget mutations with no error propagation
- `onAuthStateChange` creating new object references on every event, triggering downstream effect cascades
- Deferred state updates via `Promise.resolve().then()` racing with each other

## Design Decisions

1. **Supabase is the source of truth.** No localStorage cache for data. Mutations go to Supabase first; local state updates only on success.
2. **Graceful degradation when offline.** Show last-loaded data as read-only. Mutations are blocked with a user-visible message. No creating data that can't be saved.
3. **No anonymous data layer.** Landing page shows static seed data as presentational content. No provider, no hooks. `LocalProvider` is deleted.
4. **No provider switching.** `DataProvider` creates a single `SupabaseProvider` when authenticated. No fallback provider.

## Architecture

### Auth Lifecycle

#### `proxy.ts`

Unchanged in purpose. Refreshes Supabase auth tokens on every request.

Change: wrap `getUser()` in try/catch. On failure, pass through without modifying cookies — the user retains their existing session and the browser client can attempt its own refresh.

#### `auth/callback/route.ts`

Unchanged. Exchanges OAuth code for session, checks onboarding status, redirects.

#### `useSupabaseAuth`

Simplify to eliminate redundant state updates:

- `getSession()` on mount → set user + profile → `setIsLoading(false)`
- `onAuthStateChange` only handles `SIGNED_IN` and `SIGNED_OUT` events. Ignore `TOKEN_REFRESHED` and `INITIAL_SESSION` (redundant with the `getSession()` call).
- Remove `currentUserIdRef` tracking — the event filtering replaces it.
- `fetchProfile` failures set `profile` to `null` but don't block auth. User is "authenticated" even if profile fetch fails temporarily.

#### `AuthProvider`

Unchanged. Thin wrapper bridging hook to context.

### Data Layer

#### Delete `LocalProvider`

Remove `lib/data/local-provider.ts` entirely. Remove `fallbackProvider` from `DataProvider`. Remove all imports.

#### `SupabaseProvider` — rewrite

**Constructor:** Takes `userId` and `supabase` client. Store starts as `EMPTY_STORE`. No `loadFromLocalStorage()`.

**`loadFromSupabase()`** (renamed from `syncFromSupabase`):
- Fetches all user data from Supabase in parallel (pebbles, souls, collections, glyphs, karma, bounce)
- Sets in-memory store via `mutate()`
- Returns the store
- Throws on failure (no silent catch)

**Mutations (`createPebble`, `updatePebble`, `deletePebble`, etc.):**
- Call Supabase first (RPC or direct query), await the result
- On success: update in-memory store
- On failure: throw — caller handles the error
- No fire-and-forget. No `pushPebble*`. No `safePush`.

**Remove:**
- All `pushPebble*` methods
- `safePush` helper
- `loadFromLocalStorage`, `saveToLocalStorage`, `getStorageKey`
- `STORAGE_KEY` constant

#### `DataProvider` — simplified

Three states exposed via context:

| State | `loading` | `error` | `provider` | `store` |
|-------|-----------|---------|------------|---------|
| Loading | `true` | `null` | `null` | `EMPTY_STORE` |
| Ready | `false` | `null` | `SupabaseProvider` | data |
| Error | `false` | `Error` | `null` | `EMPTY_STORE` |
| No auth | `false` | `null` | `null` | `EMPTY_STORE` |

Lifecycle:
1. When `user` appears (and `user.id` differs from `activeUserIdRef`): create `SupabaseProvider`, call `loadFromSupabase()`, set store on success or error on failure.
2. When `user` disappears: clear provider, store, and error.
3. Track `user.id` via ref to avoid recreating on object reference changes.
4. Expose `refreshStore()` so components can retry after error.
5. No `Promise.resolve().then()` — use an async function called from the effect.

#### `usePebbles` and other data hooks

- Mutations are `async` and propagate errors to the caller.
- After successful mutation, call `setStore(provider.getStore())` to update React state.
- Callers (components) handle errors with toasts or inline messages.

### Landing Page & Seed Data

`LandingPage` imports seed data from `lib/seed/` and renders it as static presentational content. No data provider, no hooks, no localStorage.

Seed data files stay in `lib/seed/` — they are only consumed by the landing page.

### Auth Gates

`AuthGate` and `OnboardingGate` remain client-side. One fix: when `isLoading` is true on a protected route, render nothing (or a skeleton) instead of briefly showing content then redirecting.

### Error Handling & Loading States

| Scenario | Behavior |
|----------|----------|
| Initial data load | Skeleton/loading UI |
| Load failure | "Failed to load. Retry?" with `refreshStore()` |
| Mutation failure | Error bubbles to component → toast or inline error |
| Supabase unreachable | Error state → read-only message, retry button |
| Auth lost on refresh | Proxy refreshes token; if that fails, redirect to login |

## Files Changed

### Deleted
- `apps/web/lib/data/local-provider.ts`

### Rewritten
- `apps/web/lib/data/supabase-provider.ts` — remove localStorage, make mutations synchronous with Supabase
- `apps/web/components/layout/DataProvider.tsx` — remove provider switching, add loading/error states
- `apps/web/lib/data/useSupabaseAuth.ts` — simplify event handling

### Modified
- `apps/web/proxy.ts` — add try/catch around `getUser()`
- `apps/web/lib/data/provider-context.ts` — update `DataContextValue` type for loading/error/nullable provider
- `apps/web/lib/data/usePebbles.ts` — error propagation (same for other data hooks)
- `apps/web/components/auth/AuthGate.tsx` — render nothing while loading
- `apps/web/components/landing/LandingPage.tsx` — render seed data statically
- `apps/web/app/layout.tsx` — remove LocalProvider-related imports if any

### Not changed
- `apps/web/lib/supabase/client.ts`
- `apps/web/lib/supabase/server.ts`
- `apps/web/app/auth/callback/route.ts`
- `apps/web/components/layout/AuthProvider.tsx`
- Database migrations (deterministic UUIDs already deployed)

## Out of Scope

- Full local-first/offline support (deferred)
- Conflict resolution and merge logic (not needed — Supabase is source of truth)
- Server-side auth gates via proxy (client gates sufficient once auth is reliable)
- Real-time subscriptions (future enhancement)
- Retry/queue for failed mutations (show error, let user retry manually)
