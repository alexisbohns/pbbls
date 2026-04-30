# Data Layer & Async Rules

Read this when touching data access, Supabase, auth, or async code paths.

## Data layer

- All data access goes through the `DataProvider` interface via hooks.
- Never read from or write to localStorage directly in components.
- Static configs (emotions, domains, card types) are imported directly — they do not go through the provider.
- Define domain entity types in `apps/web/lib/types.ts`. Props types stay inline / co-located with the component, not in `types.ts`.

## Supabase & Auth

- **Never `await` inside `onAuthStateChange` callbacks.** The Supabase client holds an internal lock during initialization. Awaiting any Supabase call (database, auth, storage) inside this callback creates a deadlock — the lock waits for the callback, the callback waits for the Supabase call, the call waits for the lock. Use fire-and-forget (`Promise.resolve(...).then(...)`) instead.
- Wrap any Supabase call that blocks rendering or user interaction with `withTimeout()` from `lib/utils/with-timeout.ts`. Default timeout: 10 s.
- Auth state is driven by `onAuthStateChange` (synchronous callback), not by `getUser()` (network call that can hang).

## Error visibility & logging

- Any async operation that can fail or hang **must** have a `console.warn` or `console.error` in its catch/error path. Silent failures (empty catch blocks, swallowed errors) are bugs — they make debugging impossible.
- Use `withTimeout()` on any async operation that blocks rendering. A timeout error with a label (`"profile fetch"`, `"session check"`) is infinitely more useful than a silent hang.
- In development, add watchdog timers for critical loading states (see the auth watchdog in `useSupabaseAuth` as a pattern). If a loading state persists beyond 3–5 s, log a warning with diagnostic hints.
- Never guard `console.warn`/`console.error` behind `NODE_ENV === "development"` for operations that can fail in production. Dev-only guards on error logs hide production issues.
