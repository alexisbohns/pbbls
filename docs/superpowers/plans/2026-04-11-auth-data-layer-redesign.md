# Auth & Data Layer Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragile local-first sync layer with a Supabase-as-source-of-truth architecture that eliminates race conditions, data loss, and session instability.

**Architecture:** Delete LocalProvider and the provider switching logic. Rewrite SupabaseProvider so mutations hit Supabase first and only update local state on success. Simplify useSupabaseAuth to filter redundant auth events. DataProvider exposes loading/error/ready states instead of silently falling back.

**Tech Stack:** Next.js 16, React 19, Supabase SSR (`@supabase/ssr` 0.10), TypeScript strict

**Spec:** `docs/superpowers/specs/2026-04-11-auth-data-layer-redesign.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `apps/web/proxy.ts` | Modify | Add try/catch around `getUser()` |
| `apps/web/lib/data/useSupabaseAuth.ts` | Rewrite | Simplify auth event handling |
| `apps/web/lib/data/provider-context.ts` | Modify | Update context type for nullable provider, loading, error |
| `apps/web/lib/data/data-provider.ts` | Modify | Remove methods only LocalProvider used |
| `apps/web/lib/data/supabase-provider.ts` | Rewrite | Remove localStorage, make mutations Supabase-first |
| `apps/web/components/layout/DataProvider.tsx` | Rewrite | Remove provider switching, add loading/error |
| `apps/web/lib/data/usePebbles.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/useSouls.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/useCollections.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/useMarks.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/usePebble.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/useSoul.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/useCollection.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/useMark.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/usePebblesCount.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/useKarma.ts` | Modify | Read from store, guard null |
| `apps/web/lib/data/useBounce.ts` | Modify | Read from store, guard null |
| `apps/web/lib/data/useReset.ts` | Modify | Guard against null provider |
| `apps/web/lib/data/local-provider.ts` | Delete | No longer needed |
| `apps/web/components/auth/AuthGate.tsx` | Modify | Render nothing while loading |
| `apps/web/components/landing/LandingPage.tsx` | Modify | Show static seed data |
| `apps/web/app/layout.tsx` | Modify | Remove LocalProvider import if present |

---

### Task 1: Harden proxy.ts

**Files:**
- Modify: `apps/web/proxy.ts`

- [ ] **Step 1: Add try/catch around getUser()**

```typescript
import { NextResponse } from "next/server"
import type { NextRequest } from "next/server"
import { createServerClient } from "@supabase/ssr"

export async function proxy(request: NextRequest) {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL
  const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY

  if (!supabaseUrl || !supabaseKey) {
    return NextResponse.next()
  }

  let supabaseResponse = NextResponse.next({ request })

  const supabase = createServerClient(supabaseUrl, supabaseKey, {
    cookies: {
      getAll() {
        return request.cookies.getAll()
      },
      setAll(cookiesToSet) {
        for (const { name, value } of cookiesToSet) {
          request.cookies.set(name, value)
        }
        supabaseResponse = NextResponse.next({ request })
        for (const { name, value, options } of cookiesToSet) {
          supabaseResponse.cookies.set(name, value, options)
        }
      },
    },
  })

  try {
    await supabase.auth.getUser()
  } catch {
    // Token refresh failed — pass through with existing cookies.
    // The browser client will attempt its own refresh.
  }

  return supabaseResponse
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|sw.js|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)",
  ],
}
```

- [ ] **Step 2: Build check**

Run: `npm run build 2>&1 | tail -5`
Expected: `Tasks: 4 successful`

- [ ] **Step 3: Commit**

```bash
git add apps/web/proxy.ts
git commit -m "fix(auth): add error handling to proxy session refresh"
```

---

### Task 2: Simplify useSupabaseAuth

**Files:**
- Rewrite: `apps/web/lib/data/useSupabaseAuth.ts`

- [ ] **Step 1: Rewrite the hook**

Key changes from current code:
- Remove `currentUserIdRef` — replaced by event filtering
- `onAuthStateChange` only handles `SIGNED_IN` and `SIGNED_OUT` — ignore `TOKEN_REFRESHED` and `INITIAL_SESSION` (the `getSession()` call covers initial state)
- `fetchProfile` failure sets profile to null but doesn't block auth
- Remove `updateUserState` and `clearUserState` helpers — inline the logic

```typescript
"use client"

import { useState, useEffect, useCallback } from "react"
import { createClient } from "@/lib/supabase/client"

import type {
  Account,
  Profile,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
} from "@/lib/types"
import type { AuthContextValue } from "@/lib/data/auth-context"

// Singleton client — created once on first browser-side call.
let supabaseInstance: ReturnType<typeof createClient> | null = null

function getSupabase() {
  if (typeof window === "undefined") return null
  if (!supabaseInstance) supabaseInstance = createClient()
  return supabaseInstance
}

export function useSupabaseAuth(): AuthContextValue {
  const [user, setUser] = useState<Account | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [isLoading, setIsLoading] = useState(() => getSupabase() !== null)

  useEffect(() => {
    const supabase = getSupabase()
    if (!supabase) return

    let cancelled = false

    // Rehydrate session on mount
    supabase.auth.getSession().then(async ({ data: { session } }) => {
      if (cancelled) return
      if (session?.user) {
        setUser({
          id: session.user.id,
          email: session.user.email ?? "",
          created_at: session.user.created_at,
        })
        const { data } = await supabase
          .from("profiles")
          .select("*")
          .eq("user_id", session.user.id)
          .maybeSingle()
        if (!cancelled) setProfile(data as Profile | null)
      }
      if (!cancelled) setIsLoading(false)
    })

    // Only react to actual sign-in/sign-out — not token refreshes
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, session) => {
      if (event !== "SIGNED_IN" && event !== "SIGNED_OUT") return
      if (cancelled) return

      if (event === "SIGNED_OUT" || !session?.user) {
        setUser(null)
        setProfile(null)
        return
      }

      setUser({
        id: session.user.id,
        email: session.user.email ?? "",
        created_at: session.user.created_at,
      })
      const { data } = await supabase
        .from("profiles")
        .select("*")
        .eq("user_id", session.user.id)
        .maybeSingle()
      if (!cancelled) setProfile(data as Profile | null)
    })

    return () => {
      cancelled = true
      subscription.unsubscribe()
    }
  }, [])

  const login = useCallback(async (input: LoginInput) => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signInWithPassword({
      email: input.email,
      password: input.password,
    })
    if (error) throw new Error(error.message)
  }, [])

  const register = useCallback(async (input: RegisterInput) => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
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
  }, [])

  const signInWithApple = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "apple",
      options: { redirectTo: `${window.location.origin}/auth/callback` },
    })
    if (error) throw new Error(error.message)
  }, [])

  const signInWithGoogle = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: `${window.location.origin}/auth/callback` },
    })
    if (error) throw new Error(error.message)
  }, [])

  const logout = useCallback(async () => {
    const supabase = getSupabase()
    if (!supabase) throw new Error("Supabase client not available")
    const { error } = await supabase.auth.signOut()
    if (error) throw new Error(error.message)
  }, [])

  const updateProfile = useCallback(
    async (input: UpdateProfileInput): Promise<Profile> => {
      const supabase = getSupabase()
      if (!supabase) throw new Error("Supabase client not available")
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
    [user],
  )

  return {
    user,
    profile,
    isAuthenticated: user !== null,
    isLoading,
    login,
    register,
    signInWithApple,
    signInWithGoogle,
    logout,
    updateProfile,
  }
}
```

- [ ] **Step 2: Lint and build check**

Run: `npm run lint 2>&1 | tail -5 && npm run build 2>&1 | tail -5`
Expected: Both pass

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/data/useSupabaseAuth.ts
git commit -m "fix(auth): simplify auth event handling to prevent cascading re-renders"
```

---

### Task 3: Update provider-context and data-provider types

**Files:**
- Modify: `apps/web/lib/data/provider-context.ts`
- Modify: `apps/web/lib/data/data-provider.ts`

- [ ] **Step 1: Update DataContextValue to support nullable provider and error state**

Replace the full content of `apps/web/lib/data/provider-context.ts`:

```typescript
"use client"

import { createContext, useContext } from "react"
import type { DataProvider, Store } from "@/lib/data/data-provider"

export type DataContextValue = {
  /** The active provider, or null when not authenticated or during initial load. */
  provider: DataProvider | null
  store: Store
  setStore: (store: Store) => void
  /** True while the initial data load from Supabase is in progress. */
  loading: boolean
  /** Set when data load fails. Null when loaded or loading. */
  error: Error | null
  /** Re-fetch all data from Supabase. Use after an error to retry. */
  refreshStore: () => void
}

export const DataContext = createContext<DataContextValue | null>(null)

export function useDataProvider(): DataContextValue {
  const ctx = useContext(DataContext)
  if (!ctx) throw new Error("useDataProvider must be used within <DataProvider>")
  return ctx
}
```

- [ ] **Step 2: Trim DataProvider interface**

Remove `reloadStore` from `apps/web/lib/data/data-provider.ts` (only used by LocalProvider). Replace the interface:

```typescript
export interface DataProvider {
  /** Return the current in-memory store snapshot. */
  getStore(): Store

  /** Fetch all data from Supabase and replace the in-memory store. */
  loadFromSupabase(): Promise<Store>

  /** Overwrite store with empty data. */
  reset(): Promise<Store>

  // Pebbles counter (read from store, written by server via karma)
  getPebblesCount(): Promise<number>

  // Karma (read from store)
  getKarma(): Promise<number>

  // Bounce (read from store)
  getBounce(): Promise<number>

  // Pebbles
  listPebbles(): Promise<Pebble[]>
  getPebble(id: string): Promise<Pebble | undefined>
  createPebble(input: CreatePebbleInput): Promise<Pebble>
  updatePebble(id: string, input: UpdatePebbleInput): Promise<Pebble>
  deletePebble(id: string): Promise<void>

  // Souls
  listSouls(): Promise<Soul[]>
  getSoul(id: string): Promise<Soul | undefined>
  createSoul(input: CreateSoulInput): Promise<Soul>
  updateSoul(id: string, input: UpdateSoulInput): Promise<Soul>
  deleteSoul(id: string): Promise<void>

  // Collections
  listCollections(): Promise<Collection[]>
  getCollection(id: string): Promise<Collection | undefined>
  createCollection(input: CreateCollectionInput): Promise<Collection>
  updateCollection(id: string, input: UpdateCollectionInput): Promise<Collection>
  deleteCollection(id: string): Promise<void>

  // Marks
  listMarks(): Promise<Mark[]>
  getMark(id: string): Promise<Mark | undefined>
  createMark(input: CreateMarkInput): Promise<Mark>
  updateMark(id: string, input: UpdateMarkInput): Promise<Mark>
  deleteMark(id: string): Promise<void>
}
```

Note: removed `reloadStore`, `incrementPebblesCount`, `incrementKarma`, `refreshBounce` — these were LocalProvider-only helpers or are now derived from server state.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/data/provider-context.ts apps/web/lib/data/data-provider.ts
git commit -m "fix(core): update data context types for nullable provider and error state"
```

---

### Task 4: Rewrite SupabaseProvider

**Files:**
- Rewrite: `apps/web/lib/data/supabase-provider.ts`

- [ ] **Step 1: Rewrite the full file**

Key changes:
- No localStorage — store starts as `EMPTY_STORE`
- `loadFromSupabase()` replaces `syncFromSupabase()` — fetches from Supabase, throws on failure
- All mutations call Supabase FIRST, await the result, THEN update in-memory store
- Remove all `push*` methods, `safePush`, localStorage helpers
- On mutation failure, throw — caller handles

```typescript
import type { SupabaseClient } from "@supabase/supabase-js"
import {
  EMPTY_STORE,
  type DataProvider,
  type Store,
  type CreatePebbleInput,
  type UpdatePebbleInput,
  type CreateSoulInput,
  type UpdateSoulInput,
  type CreateCollectionInput,
  type UpdateCollectionInput,
  type CreateMarkInput,
  type UpdateMarkInput,
} from "@/lib/data/data-provider"
import type {
  Pebble,
  Soul,
  Collection,
  Mark,
} from "@/lib/types"

export class SupabaseProvider implements DataProvider {
  private store: Store
  private readonly userId: string
  private readonly supabase: SupabaseClient

  constructor(userId: string, supabase: SupabaseClient) {
    this.userId = userId
    this.supabase = supabase
    this.store = EMPTY_STORE
  }

  getStore(): Store {
    return this.store
  }

  private mutate(store: Store): void {
    this.store = store
  }

  // ---------------------------------------------------------------------------
  // Supabase helpers
  // ---------------------------------------------------------------------------

  /** Unwrap a Supabase response, throwing on error. */
  private unwrap<T>(result: { data: T; error: unknown }): T {
    if (result.error) {
      const err = result.error as { message?: string }
      throw new Error(err.message ?? "Supabase request failed")
    }
    return result.data
  }

  // ---------------------------------------------------------------------------
  // Load all data from Supabase
  // ---------------------------------------------------------------------------

  async loadFromSupabase(): Promise<Store> {
    const [
      pebblesRes,
      soulsRes,
      collectionsRes,
      collectionPebblesRes,
      glyphsRes,
      karmaRes,
      bounceRes,
    ] = await Promise.all([
      this.supabase.from("v_pebbles_full").select("*").eq("user_id", this.userId),
      this.supabase.from("souls").select("*").eq("user_id", this.userId),
      this.supabase.from("collections").select("*").eq("user_id", this.userId),
      this.supabase.from("collection_pebbles").select("*, collections!inner(user_id)").eq("collections.user_id", this.userId),
      this.supabase.from("glyphs").select("*").eq("user_id", this.userId),
      this.supabase.from("v_karma_summary").select("*").eq("user_id", this.userId).maybeSingle(),
      this.supabase.from("v_bounce").select("*").eq("user_id", this.userId).maybeSingle(),
    ])

    // Check for errors on critical queries
    if (pebblesRes.error) throw new Error(`Failed to load pebbles: ${pebblesRes.error.message}`)
    if (soulsRes.error) throw new Error(`Failed to load souls: ${soulsRes.error.message}`)
    if (collectionsRes.error) throw new Error(`Failed to load collections: ${collectionsRes.error.message}`)
    if (glyphsRes.error) throw new Error(`Failed to load glyphs: ${glyphsRes.error.message}`)

    const pebbles: Pebble[] = (pebblesRes.data ?? []).map((row: Record<string, unknown>) => ({
      id: row.id as string,
      name: row.name as string,
      description: (row.description as string) ?? undefined,
      happened_at: row.happened_at as string,
      intensity: row.intensity as 1 | 2 | 3,
      positiveness: row.positiveness as -1 | 0 | 1,
      visibility: (row.visibility as string) as "private" | "public",
      emotion_id: row.emotion_id as string,
      soul_ids: ((row.souls as Array<{ id: string }>) ?? []).map((s) => s.id),
      domain_ids: ((row.domains as Array<{ id: string }>) ?? []).map((d) => d.id),
      mark_id: (row.glyph_id as string) ?? undefined,
      instants: [],
      cards: ((row.cards as Array<{ species_id: string; value: string }>) ?? []).map((c) => ({
        species_id: c.species_id,
        value: c.value,
      })),
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }))

    const souls: Soul[] = (soulsRes.data ?? []).map((row: Record<string, unknown>) => ({
      id: row.id as string,
      name: row.name as string,
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }))

    const cpMap = new Map<string, string[]>()
    for (const row of collectionPebblesRes.data ?? []) {
      const cid = (row as Record<string, string>).collection_id
      const pid = (row as Record<string, string>).pebble_id
      const arr = cpMap.get(cid) ?? []
      arr.push(pid)
      cpMap.set(cid, arr)
    }

    const collections: Collection[] = (collectionsRes.data ?? []).map((row: Record<string, unknown>) => ({
      id: row.id as string,
      name: row.name as string,
      mode: (row.mode as "stack" | "pack" | "track") ?? undefined,
      pebble_ids: cpMap.get(row.id as string) ?? [],
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }))

    const marks: Mark[] = (glyphsRes.data ?? []).map((row: Record<string, unknown>) => ({
      id: row.id as string,
      name: (row.name as string) ?? undefined,
      shape_id: row.shape_id as string,
      strokes: row.strokes as Mark["strokes"],
      viewBox: row.view_box as string,
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }))

    const karma = (karmaRes.data as Record<string, unknown>)?.total_karma as number ?? 0
    const pebblesCount = (karmaRes.data as Record<string, unknown>)?.pebbles_count as number ?? 0
    const bounce = (bounceRes.data as Record<string, unknown>)?.bounce_level as number ?? 0

    const newStore: Store = {
      pebbles,
      souls,
      collections,
      marks,
      pebbles_count: pebblesCount,
      karma,
      karma_log: [],
      bounce,
      bounce_window: [],
    }

    this.mutate(newStore)
    return newStore
  }

  async reset(): Promise<Store> {
    this.mutate(EMPTY_STORE)
    return EMPTY_STORE
  }

  // ---------------------------------------------------------------------------
  // Read helpers (from in-memory store)
  // ---------------------------------------------------------------------------

  async getPebblesCount(): Promise<number> { return this.store.pebbles_count }
  async getKarma(): Promise<number> { return this.store.karma }
  async getBounce(): Promise<number> { return this.store.bounce }
  async listPebbles(): Promise<Pebble[]> { return this.store.pebbles }
  async getPebble(id: string): Promise<Pebble | undefined> { return this.store.pebbles.find((p) => p.id === id) }
  async listSouls(): Promise<Soul[]> { return this.store.souls }
  async getSoul(id: string): Promise<Soul | undefined> { return this.store.souls.find((s) => s.id === id) }
  async listCollections(): Promise<Collection[]> { return this.store.collections }
  async getCollection(id: string): Promise<Collection | undefined> { return this.store.collections.find((c) => c.id === id) }
  async listMarks(): Promise<Mark[]> { return this.store.marks }
  async getMark(id: string): Promise<Mark | undefined> { return this.store.marks.find((m) => m.id === id) }

  // ---------------------------------------------------------------------------
  // Pebble mutations — Supabase first, then local state
  // ---------------------------------------------------------------------------

  async createPebble(input: CreatePebbleInput): Promise<Pebble> {
    const result = await this.supabase.rpc("create_pebble", {
      payload: {
        name: input.name,
        description: input.description ?? null,
        happened_at: input.happened_at,
        intensity: input.intensity,
        positiveness: input.positiveness,
        visibility: input.visibility,
        emotion_id: input.emotion_id,
        soul_ids: input.soul_ids,
        domain_ids: input.domain_ids,
        cards: input.cards.map((c, i) => ({
          species_id: c.species_id,
          value: c.value,
          sort_order: i,
        })),
      },
    })
    const pebbleId = this.unwrap(result) as string

    // Reload full store from Supabase to get server-computed fields
    // (karma, pebbles_count, bounce, etc.)
    await this.loadFromSupabase()
    return this.store.pebbles.find((p) => p.id === pebbleId)!
  }

  async updatePebble(id: string, input: UpdatePebbleInput): Promise<Pebble> {
    const result = await this.supabase.rpc("update_pebble", {
      p_pebble_id: id,
      payload: {
        ...(input.name !== undefined && { name: input.name }),
        ...(input.description !== undefined && { description: input.description }),
        ...(input.happened_at !== undefined && { happened_at: input.happened_at }),
        ...(input.intensity !== undefined && { intensity: input.intensity }),
        ...(input.positiveness !== undefined && { positiveness: input.positiveness }),
        ...(input.visibility !== undefined && { visibility: input.visibility }),
        ...(input.emotion_id !== undefined && { emotion_id: input.emotion_id }),
        ...(input.soul_ids !== undefined && { soul_ids: input.soul_ids }),
        ...(input.domain_ids !== undefined && { domain_ids: input.domain_ids }),
        ...(input.cards !== undefined && {
          cards: input.cards.map((c, i) => ({
            species_id: c.species_id,
            value: c.value,
            sort_order: i,
          })),
        }),
      },
    })
    this.unwrap(result)
    await this.loadFromSupabase()
    const updated = this.store.pebbles.find((p) => p.id === id)
    if (!updated) throw new Error(`Pebble not found after update: ${id}`)
    return updated
  }

  async deletePebble(id: string): Promise<void> {
    const result = await this.supabase.rpc("delete_pebble", { p_pebble_id: id })
    this.unwrap(result)
    await this.loadFromSupabase()
  }

  // ---------------------------------------------------------------------------
  // Soul mutations
  // ---------------------------------------------------------------------------

  async createSoul(input: CreateSoulInput): Promise<Soul> {
    const result = await this.supabase
      .from("souls")
      .insert({ user_id: this.userId, name: input.name })
      .select()
      .single()
    const soul = this.unwrap(result) as Record<string, unknown>
    const created: Soul = {
      id: soul.id as string,
      name: soul.name as string,
      created_at: soul.created_at as string,
      updated_at: soul.updated_at as string,
    }
    this.mutate({ ...this.store, souls: [...this.store.souls, created] })
    return created
  }

  async updateSoul(id: string, input: UpdateSoulInput): Promise<Soul> {
    const result = await this.supabase
      .from("souls")
      .update({ ...(input.name !== undefined && { name: input.name }) })
      .eq("id", id)
      .select()
      .single()
    const soul = this.unwrap(result) as Record<string, unknown>
    const updated: Soul = {
      id: soul.id as string,
      name: soul.name as string,
      created_at: soul.created_at as string,
      updated_at: soul.updated_at as string,
    }
    const souls = this.store.souls.map((s) => (s.id === id ? updated : s))
    this.mutate({ ...this.store, souls })
    return updated
  }

  async deleteSoul(id: string): Promise<void> {
    this.unwrap(await this.supabase.from("souls").delete().eq("id", id))
    const souls = this.store.souls.filter((s) => s.id !== id)
    const pebbles = this.store.pebbles.map((p) => ({
      ...p,
      soul_ids: p.soul_ids.filter((sid) => sid !== id),
    }))
    this.mutate({ ...this.store, souls, pebbles })
  }

  // ---------------------------------------------------------------------------
  // Collection mutations
  // ---------------------------------------------------------------------------

  async createCollection(input: CreateCollectionInput): Promise<Collection> {
    const result = await this.supabase
      .from("collections")
      .insert({ user_id: this.userId, name: input.name, mode: input.mode ?? null })
      .select()
      .single()
    const col = this.unwrap(result) as Record<string, unknown>

    if (input.pebble_ids.length > 0) {
      this.unwrap(await this.supabase.from("collection_pebbles").insert(
        input.pebble_ids.map((pid) => ({ collection_id: col.id as string, pebble_id: pid })),
      ))
    }

    const created: Collection = {
      id: col.id as string,
      name: col.name as string,
      mode: (col.mode as "stack" | "pack" | "track") ?? undefined,
      pebble_ids: input.pebble_ids,
      created_at: col.created_at as string,
      updated_at: col.updated_at as string,
    }
    this.mutate({ ...this.store, collections: [...this.store.collections, created] })
    return created
  }

  async updateCollection(id: string, input: UpdateCollectionInput): Promise<Collection> {
    const updates: Record<string, unknown> = {}
    if (input.name !== undefined) updates.name = input.name
    if (input.mode !== undefined) updates.mode = input.mode
    if (Object.keys(updates).length > 0) {
      this.unwrap(await this.supabase.from("collections").update(updates).eq("id", id).select().single())
    }
    if (input.pebble_ids !== undefined) {
      this.unwrap(await this.supabase.from("collection_pebbles").delete().eq("collection_id", id))
      if (input.pebble_ids.length > 0) {
        this.unwrap(await this.supabase.from("collection_pebbles").insert(
          input.pebble_ids.map((pid) => ({ collection_id: id, pebble_id: pid })),
        ))
      }
    }

    const prev = this.store.collections.find((c) => c.id === id)
    if (!prev) throw new Error(`Collection not found: ${id}`)
    const updated: Collection = {
      ...prev,
      ...input,
      updated_at: new Date().toISOString(),
    }
    const collections = this.store.collections.map((c) => (c.id === id ? updated : c))
    this.mutate({ ...this.store, collections })
    return updated
  }

  async deleteCollection(id: string): Promise<void> {
    this.unwrap(await this.supabase.from("collections").delete().eq("id", id))
    const collections = this.store.collections.filter((c) => c.id !== id)
    this.mutate({ ...this.store, collections })
  }

  // ---------------------------------------------------------------------------
  // Mark mutations (DB table: glyphs)
  // ---------------------------------------------------------------------------

  async createMark(input: CreateMarkInput): Promise<Mark> {
    const result = await this.supabase
      .from("glyphs")
      .insert({
        user_id: this.userId,
        name: input.name ?? null,
        shape_id: input.shape_id,
        strokes: input.strokes,
        view_box: input.viewBox,
      })
      .select()
      .single()
    const row = this.unwrap(result) as Record<string, unknown>
    const created: Mark = {
      id: row.id as string,
      name: (row.name as string) ?? undefined,
      shape_id: row.shape_id as string,
      strokes: row.strokes as Mark["strokes"],
      viewBox: row.view_box as string,
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }
    this.mutate({ ...this.store, marks: [...this.store.marks, created] })
    return created
  }

  async updateMark(id: string, input: UpdateMarkInput): Promise<Mark> {
    const updates: Record<string, unknown> = {}
    if (input.name !== undefined) updates.name = input.name
    if (input.shape_id !== undefined) updates.shape_id = input.shape_id
    if (input.strokes !== undefined) updates.strokes = input.strokes
    if (input.viewBox !== undefined) updates.view_box = input.viewBox
    const result = await this.supabase.from("glyphs").update(updates).eq("id", id).select().single()
    const row = this.unwrap(result) as Record<string, unknown>
    const updated: Mark = {
      id: row.id as string,
      name: (row.name as string) ?? undefined,
      shape_id: row.shape_id as string,
      strokes: row.strokes as Mark["strokes"],
      viewBox: row.view_box as string,
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }
    const marks = this.store.marks.map((m) => (m.id === id ? updated : m))
    this.mutate({ ...this.store, marks })
    return updated
  }

  async deleteMark(id: string): Promise<void> {
    this.unwrap(await this.supabase.from("glyphs").delete().eq("id", id))
    const marks = this.store.marks.filter((m) => m.id !== id)
    this.mutate({ ...this.store, marks })
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/lib/data/supabase-provider.ts
git commit -m "fix(db): rewrite SupabaseProvider as Supabase-first with no localStorage"
```

---

### Task 5: Rewrite DataProvider

**Files:**
- Rewrite: `apps/web/components/layout/DataProvider.tsx`

- [ ] **Step 1: Rewrite the full file**

```typescript
"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { DataContext } from "@/lib/data/provider-context"
import { SupabaseProvider } from "@/lib/data/supabase-provider"
import { useAuth } from "@/lib/data/auth-context"
import { createClient } from "@/lib/supabase/client"
import { EMPTY_STORE, type Store } from "@/lib/data/data-provider"

export function DataProvider({ children }: { children: React.ReactNode }) {
  const { user, isLoading: authLoading } = useAuth()

  const [provider, setProvider] = useState<SupabaseProvider | null>(null)
  const [store, setStore] = useState<Store>(EMPTY_STORE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const activeUserIdRef = useRef<string | null>(null)

  const loadData = useCallback(async (userId: string) => {
    activeUserIdRef.current = userId
    setLoading(true)
    setError(null)

    try {
      const supabase = createClient()
      const sp = new SupabaseProvider(userId, supabase)
      const freshStore = await sp.loadFromSupabase()

      if (activeUserIdRef.current !== userId) return // user changed during load
      setProvider(sp)
      setStore(freshStore)
    } catch (err) {
      if (activeUserIdRef.current !== userId) return
      setError(err instanceof Error ? err : new Error("Failed to load data"))
      setProvider(null)
      setStore(EMPTY_STORE)
    } finally {
      if (activeUserIdRef.current === userId) setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (authLoading) return

    if (!user) {
      activeUserIdRef.current = null
      setProvider(null)
      setStore(EMPTY_STORE)
      setLoading(false)
      setError(null)
      return
    }

    if (activeUserIdRef.current === user.id) return

    void loadData(user.id)
  }, [user, authLoading, loadData])

  const refreshStore = useCallback(() => {
    if (user) void loadData(user.id)
  }, [user, loadData])

  return (
    <DataContext.Provider value={{ provider, store, setStore, loading, error, refreshStore }}>
      {children}
    </DataContext.Provider>
  )
}
```

- [ ] **Step 2: Lint and build check**

Run: `npm run lint 2>&1 | tail -10 && npm run build 2>&1 | tail -5`

Note: this will likely fail because data hooks still reference `provider.someMethod()` without null checks. That's expected — Task 6 fixes them.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/layout/DataProvider.tsx
git commit -m "fix(core): rewrite DataProvider with loading/error states, no provider switching"
```

---

### Task 6: Update all data hooks for nullable provider

**Files:**
- Modify: all hooks in `apps/web/lib/data/use*.ts`

Every data hook calls `useDataProvider()` and accesses `provider.someMethod()`. Since `provider` is now nullable, each hook must guard against null.

- [ ] **Step 1: Update usePebbles.ts**

```typescript
"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { CreatePebbleInput, UpdatePebbleInput } from "@/lib/data/data-provider"
import type { Pebble } from "@/lib/types"

export function usePebbles() {
  const { provider, store, setStore, loading } = useDataProvider()

  const addPebble = async (input: CreatePebbleInput): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const pebble = await provider.createPebble(input)
    setStore(provider.getStore())
    return pebble
  }

  const updatePebble = async (id: string, input: UpdatePebbleInput): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const pebble = await provider.updatePebble(id, input)
    setStore(provider.getStore())
    return pebble
  }

  const removePebble = async (id: string): Promise<void> => {
    if (!provider) throw new Error("Not authenticated")
    await provider.deletePebble(id)
    setStore(provider.getStore())
  }

  return {
    pebbles: store.pebbles,
    loading,
    addPebble,
    updatePebble,
    removePebble,
  }
}
```

- [ ] **Step 2: Update usePebble.ts**

Read the current file and add `if (!provider) throw new Error("Not authenticated")` guard before each `provider.` call. Follow the same pattern as usePebbles.

- [ ] **Step 3: Update useSouls.ts**

Same pattern — add `if (!provider) throw ...` guard before each provider call.

- [ ] **Step 4: Update useSoul.ts**

Same pattern.

- [ ] **Step 5: Update useCollections.ts**

Same pattern.

- [ ] **Step 6: Update useCollection.ts**

Same pattern.

- [ ] **Step 7: Update useMarks.ts**

Same pattern.

- [ ] **Step 8: Update useMark.ts**

Same pattern.

- [ ] **Step 9: Update usePebblesCount.ts**

This hook only reads from `store.pebbles_count` — no provider call needed. Just ensure it compiles:

```typescript
"use client"

import { useDataProvider } from "@/lib/data/provider-context"

export function usePebblesCount() {
  const { store, loading } = useDataProvider()
  return { pebblesCount: store.pebbles_count, loading }
}
```

- [ ] **Step 10: Update useKarma.ts**

Reads from store only — no provider call. Leave as-is if it compiles.

- [ ] **Step 11: Update useBounce.ts**

Reads from store only — no provider call. Leave as-is if it compiles.

- [ ] **Step 12: Update useReset.ts**

```typescript
import { useDataProvider } from "@/lib/data/provider-context"
import type { Store } from "@/lib/data/data-provider"

export function useReset(): { reset: () => Promise<Store> } {
  const { provider, setStore } = useDataProvider()

  const reset = async (): Promise<Store> => {
    if (!provider) throw new Error("Not authenticated")
    const snapshot = await provider.reset()
    setStore(snapshot)
    return snapshot
  }

  return { reset }
}
```

- [ ] **Step 13: Lint and build check**

Run: `npm run lint 2>&1 | tail -10 && npm run build 2>&1 | tail -5`
Expected: Both pass (or identify remaining type errors to fix)

- [ ] **Step 14: Commit**

```bash
git add apps/web/lib/data/use*.ts
git commit -m "fix(core): guard all data hooks against null provider"
```

---

### Task 7: Delete LocalProvider and clean up

**Files:**
- Delete: `apps/web/lib/data/local-provider.ts`
- Modify: `apps/web/app/layout.tsx` (if it imports LocalProvider)

- [ ] **Step 1: Delete the file**

```bash
rm apps/web/lib/data/local-provider.ts
```

- [ ] **Step 2: Verify no remaining imports**

Run: `grep -r "local-provider" apps/web/ --include="*.ts" --include="*.tsx"`
Expected: No results. If any found, remove the imports.

- [ ] **Step 3: Build check**

Run: `npm run build 2>&1 | tail -10`
Expected: Passes with no import errors

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore(core): delete LocalProvider — Supabase is now sole data source"
```

---

### Task 8: Fix AuthGate loading behavior

**Files:**
- Modify: `apps/web/components/auth/AuthGate.tsx`

- [ ] **Step 1: Return null while auth is loading on protected routes**

```typescript
"use client"

import { useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"

const PROTECTED_PREFIXES = [
  "/path",
  "/record",
  "/pebble",
  "/collections",
  "/souls",
  "/glyphs",
  "/carve",
  "/profile",
]

function isProtected(pathname: string): boolean {
  return PROTECTED_PREFIXES.some((p) => pathname === p || pathname.startsWith(p + "/"))
}

export function AuthGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const { isAuthenticated, isLoading } = useAuth()

  useEffect(() => {
    if (isLoading) return
    if (!isAuthenticated && isProtected(pathname)) {
      router.replace("/")
    }
  }, [pathname, router, isAuthenticated, isLoading])

  // While auth is loading on a protected route, render nothing
  if (isLoading && isProtected(pathname)) return null

  // Not authenticated on a protected route — render nothing (redirect is in progress)
  if (!isLoading && !isAuthenticated && isProtected(pathname)) return null

  return <>{children}</>
}
```

Note: this changes AuthGate from a side-effect-only component to one that wraps children. Update `MainContent.tsx` if needed — AuthGate must wrap the page content, not just render alongside it.

- [ ] **Step 2: Check MainContent.tsx**

Read `apps/web/components/layout/MainContent.tsx`. If AuthGate is rendered as a sibling (`<AuthGate /><div>{children}</div>`), refactor it to wrap children: `<AuthGate><div>{children}</div></AuthGate>`. If it already wraps, no change needed.

- [ ] **Step 3: Lint and build check**

Run: `npm run lint 2>&1 | tail -10 && npm run build 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/auth/AuthGate.tsx apps/web/components/layout/MainContent.tsx
git commit -m "fix(auth): render nothing on protected routes while auth is loading"
```

---

### Task 9: Update LandingPage to show static seed data

**Files:**
- Modify: `apps/web/components/landing/LandingPage.tsx`

- [ ] **Step 1: Read the current LandingPage**

Read `apps/web/components/landing/LandingPage.tsx` to understand the current structure.

- [ ] **Step 2: Add static seed pebbles as presentational content**

Import seed data from `@/lib/seed/seed-data` and render a small sample as a visual preview below the existing hero section. This is purely presentational — no hooks, no provider, no data layer. Keep the existing feature cards and CTA buttons.

The seed data is already typed and ready to render. Show 2-3 sample pebbles in a card-like format to give visitors a taste of the app.

- [ ] **Step 3: Lint and build check**

Run: `npm run lint 2>&1 | tail -10 && npm run build 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/landing/LandingPage.tsx
git commit -m "feat(ui): show static seed pebbles on landing page"
```

---

### Task 10: Final verification

- [ ] **Step 1: Full lint**

Run: `npm run lint`
Expected: 0 errors

- [ ] **Step 2: Full build**

Run: `npm run build`
Expected: exits 0, all routes compile

- [ ] **Step 3: Verify no LocalProvider references remain**

Run: `grep -r "LocalProvider\|local-provider\|fallbackProvider" apps/web/ --include="*.ts" --include="*.tsx"`
Expected: no results

- [ ] **Step 4: Verify no localStorage references in data layer**

Run: `grep -r "localStorage" apps/web/lib/data/ --include="*.ts"`
Expected: no results (localStorage is only used by non-data files like color-world prefs)

- [ ] **Step 5: Push and verify preview**

```bash
git push
```

Test on preview:
1. Sign in with Google → onboarding or path (no blank screen)
2. Create a pebble → network request visible, pebble appears
3. Refresh → pebble persists, auth persists
4. Hard refresh → same result
5. Check Supabase → pebble exists
6. Visit `/profile` → shows profile data
