# SupabaseProvider — Local-First Data Layer

Resolves #198

## Overview

Replace `LocalProvider` with a `SupabaseProvider` that implements the `DataProvider` interface using a local-first, optimistic architecture. localStorage is the primary store for instant reads/writes. Supabase syncs in the background as a backup. On every app mount, Supabase is fetched as the source of truth.

## Architecture

```
UI (hooks) → in-memory Store → localStorage (instant persist) → Supabase (background sync)
```

**Reads**: always from in-memory Store (instant).

**Writes**: mutate in-memory Store → persist to localStorage → fire-and-forget push to Supabase. The UI never waits for Supabase.

**On mount** (every page load):
1. Load localStorage into memory (instant — app is usable immediately)
2. In background: fetch full state from Supabase
3. Find local items with IDs not in Supabase (created offline) → push them to Supabase
4. Replace local Store + localStorage with Supabase state (now includes the pushed items)
5. Re-render with fresh data

## SupabaseProvider class

### Constructor

Takes `userId: string` and the Supabase client instance. Loads localStorage into memory immediately using a user-scoped storage key (`pbbls:store:{userId}`).

### Sync method: `syncFromSupabase()`

Called on mount. Performs the fetch-push-replace cycle:
1. Fetch all user data from Supabase (pebbles via `v_pebbles_full`, souls, collections, glyphs, karma via `v_karma_summary`, bounce via `v_bounce`)
2. Compare local IDs against Supabase IDs per entity type
3. Push any local-only items (created offline) to Supabase via the appropriate RPC or insert
4. Replace local store with full Supabase state
5. Trigger a React re-render via `setStore`

Errors during sync are logged but never thrown — the app continues working from local data.

### Background push: `pushToSupabase()`

Fire-and-forget helper called after every local write. Catches errors silently (logs them). The local write already succeeded, so the UI is consistent regardless.

### Storage key

Scoped to user ID: `pbbls:store:{userId}`. When the user logs out, the key is not cleared — it serves as cache for next login.

## Method mapping to Supabase

### Pebbles

- `createPebble` → RPC `create_pebble(payload)` — handles join tables (souls, domains, cards) atomically
- `updatePebble` → RPC `update_pebble(p_pebble_id, payload)` — same atomic handling
- `deletePebble` → RPC `delete_pebble(p_pebble_id)` — cascades joins
- `listPebbles` → query `v_pebbles_full` view (denormalized with nested relations)
- `getPebble` → same view filtered by ID

### Souls

Straight CRUD on the `souls` table. No joins.

### Collections

CRUD on `collections` table + manage `collection_pebbles` join table for `pebble_ids`.

### Marks/Glyphs

CRUD on `glyphs` table. The DB calls them "glyphs", the app calls them "marks" — the provider maps field names between the two.

### Karma

- `getKarma` → query `v_karma_summary` view
- `incrementKarma` → insert into `karma_events` table

### Bounce

- `getBounce` → query `v_bounce` view
- `refreshBounce` → the view computes this from pebble dates, so it's read-only from the client

### Counters

- `getPebblesCount` → from `v_karma_summary` view (derived from actual count)
- `incrementPebblesCount` → no-op server-side (derived value)

## Provider wiring

### Layout reorder

The provider tree must change so `DataProvider` can access auth state:

```
Before:  DataProvider > AuthProvider > ...
After:   AuthProvider > DataProvider > ...
```

This is safe because `AuthProvider` no longer depends on `DataProvider` (decoupled in PR #226).

### DataProvider.tsx

- When authenticated: create `SupabaseProvider` with `userId` and Supabase client, call `syncFromSupabase()` in a `useEffect`
- When not authenticated: no provider needed — `AuthGate` redirects to login

### Demo mode removal

`LocalProvider` is no longer used. Unauthenticated users are redirected to login. Seed data files remain in the codebase for potential future demo mode rebuild.

## DataProvider interface cleanup

Remove auth methods from the `DataProvider` interface — they're dead code since PR #226 moved auth to `useSupabaseAuth`. Methods to remove:
- `register`, `login`, `logout`, `getSession`, `getAccount`, `getProfile`, `updateProfile`
- `AuthStore` type

This also removes the corresponding stubs from `LocalProvider` (which stays in the codebase but is unused).

## File impact

| File | Action |
|---|---|
| `apps/web/lib/data/supabase-provider.ts` | **New** — SupabaseProvider class |
| `apps/web/components/layout/DataProvider.tsx` | **Modify** — swap to SupabaseProvider, depend on auth |
| `apps/web/app/layout.tsx` | **Modify** — reorder: AuthProvider > DataProvider |
| `apps/web/lib/data/data-provider.ts` | **Modify** — remove auth methods from interface |
| `apps/web/lib/data/local-provider.ts` | **Keep** — unused but retained for potential future use |

## Out of scope

- Paginated sync for large datasets (optimization for later)
- Social/friends pebble fetching (different data path, not DataProvider)
- Offline detection / retry queue (background push is fire-and-forget for V1)
- Soft deletes for cross-device delete sync (V1 accepts Supabase-wins on mount)
- Demo mode rebuild
