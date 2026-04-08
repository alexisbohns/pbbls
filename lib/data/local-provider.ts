import type {
  DataProvider,
  Store,
  AuthStore,
  CreatePebbleInput,
  UpdatePebbleInput,
  CreateSoulInput,
  UpdateSoulInput,
  CreateCollectionInput,
  UpdateCollectionInput,
  CreateMarkInput,
  UpdateMarkInput,
} from "@/lib/data/data-provider"
import type {
  Pebble,
  Soul,
  Collection,
  KarmaEvent,
  Mark,
  Account,
  Profile,
  Session,
  RegisterInput,
  LoginInput,
  UpdateProfileInput,
} from "@/lib/types"
import { hashPassword, verifyPassword } from "@/lib/data/password"
import { SEED_PEBBLES, SEED_SOULS, SEED_COLLECTIONS } from "@/lib/seed/seed-data"
import { refreshBounceWindow, decayBounceWindow, todayLocal } from "@/lib/data/bounce-levels"
import { computeKarmaDelta } from "@/lib/data/karma"

const STORAGE_KEY = "pbbls:store"
const AUTH_STORAGE_KEY = "pbbls:auth"
const SESSION_STORAGE_KEY = "pbbls:session"
const ONBOARDING_LEGACY_KEY = "pebbles_onboarding_completed"

const EMPTY_AUTH_STORE: AuthStore = {
  accounts: [],
  profiles: [],
}

const EMPTY_STORE: Store = {
  pebbles: [],
  souls: [],
  collections: [],
  marks: [],
  pebbles_count: 0,
  karma: 0,
  karma_log: [],
  bounce: 0,
  bounce_window: [],
}

export class LocalProvider implements DataProvider {
  private store: Store
  private authStore: AuthStore
  private session: Session | null

  constructor() {
    this.authStore = this.loadAuth()
    this.session = this.loadSession()
    this.store = this.load()
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  /**
   * Return the localStorage key for content data. When a session is active the
   * key is scoped to the profile so each account has its own pebbles, souls,
   * collections, etc. Falls back to the unscoped key when no session exists.
   */
  private getStorageKey(): string {
    return this.session?.profile_id
      ? `${STORAGE_KEY}:${this.session.profile_id}`
      : STORAGE_KEY
  }

  private load(): Store {
    // localStorage is not available during SSR — return an empty store so the
    // server render is consistent. The client will initialize the store from
    // localStorage (or seed data) when this runs in the browser.
    if (typeof window === "undefined") return EMPTY_STORE

    try {
      const raw = localStorage.getItem(this.getStorageKey())
      if (!raw) {
        // Return seed in-memory only; the DataProvider mounts a useEffect to
        // persist via persistIfNeeded() after the first render. This keeps
        // load() side-effect-free and safe for StrictMode double-invocation.
        return this.fromSeed()
      }
      const parsed = JSON.parse(raw) as Store
      // Migration: backfill pebbles_count for existing users.
      if (parsed.pebbles_count === undefined) {
        parsed.pebbles_count = parsed.pebbles.length
      }
      // Migration: backfill karma for existing users.
      if (parsed.karma === undefined) {
        parsed.karma = parsed.pebbles.length
        parsed.karma_log = parsed.pebbles.map((p: Pebble) => ({
          delta: 1,
          reason: "pebble_created",
          ref_id: p.id,
          created_at: p.created_at,
        }))
      }
      // Migration: backfill marks for existing users.
      if (parsed.marks === undefined) {
        parsed.marks = []
      }
      // Migration: backfill instants for existing pebbles.
      for (const p of parsed.pebbles) {
        if (!p.instants) p.instants = []
      }
      // Migration: clamp positiveness from 5-level to 3-level and add visibility.
      for (const p of parsed.pebbles) {
        const raw = p.positiveness as number
        if (raw <= -2) p.positiveness = -1
        if (raw >= 2) p.positiveness = 1
        if (!p.visibility) p.visibility = "private"
      }
      // Migration: backfill bounce fields for existing users.
      if (parsed.bounce === undefined) {
        parsed.bounce = 0
        parsed.bounce_window = []
      }
      // Lazy decay: prune expired dates and recompute level on app open.
      const decayed = decayBounceWindow(parsed.bounce_window)
      parsed.bounce = decayed.bounce
      parsed.bounce_window = decayed.bounce_window
      return parsed
    } catch {
      // Corrupt or unreadable storage — fall back to seed (no write; same as above).
      return this.fromSeed()
    }
  }

  /**
   * Write the current in-memory store to localStorage only if the key is
   * absent. Safe to call multiple times (e.g. StrictMode double-effect) because
   * the second call will find the key already present and return immediately.
   * Call this post-mount (useEffect) — never during construction or render.
   */
  persistIfNeeded(): void {
    if (typeof window === "undefined") return
    if (localStorage.getItem(this.getStorageKey()) !== null) return
    this.writeToStorage(this.store)
  }

  private fromSeed(): Store {
    const now = new Date().toISOString()

    // Seed bounce with ~5 recent dates so demo users see the mechanic.
    const today = todayLocal()
    const seedDates: string[] = []
    for (const offset of [0, 2, 4, 7, 9]) {
      const d = new Date()
      d.setDate(d.getDate() - offset)
      seedDates.push(d.toLocaleDateString("en-CA"))
    }
    // Deduplicate in case offsets collapse (unlikely but safe).
    const bounceWindow = [...new Set(seedDates)].filter((d) => d <= today)

    const pebbles = SEED_PEBBLES.map((p) => ({ ...p, created_at: now, updated_at: now }))

    return {
      pebbles,
      souls: SEED_SOULS.map((s) => ({ ...s, created_at: now, updated_at: now })),
      collections: SEED_COLLECTIONS.map((c) => ({ ...c, created_at: now, updated_at: now })),
      marks: [],
      pebbles_count: SEED_PEBBLES.length,
      karma: SEED_PEBBLES.length,
      karma_log: pebbles.map((p) => ({
        delta: 1,
        reason: "pebble_created",
        ref_id: p.id,
        created_at: p.created_at,
      })),
      bounce: refreshBounceWindow(bounceWindow).bounce,
      bounce_window: bounceWindow,
    }
  }

  private writeToStorage(store: Store): void {
    if (typeof window === "undefined") return
    try {
      localStorage.setItem(this.getStorageKey(), JSON.stringify(store))
    } catch {
      console.warn("[LocalProvider] Could not write to localStorage.")
    }
  }

  /** Replace in-memory store and persist to localStorage. */
  private mutate(store: Store): void {
    this.store = store
    this.writeToStorage(store)
  }

  // ---------------------------------------------------------------------------
  // Auth storage helpers
  // ---------------------------------------------------------------------------

  private loadAuth(): AuthStore {
    if (typeof window === "undefined") return EMPTY_AUTH_STORE
    try {
      const raw = localStorage.getItem(AUTH_STORAGE_KEY)
      if (!raw) return EMPTY_AUTH_STORE
      return JSON.parse(raw) as AuthStore
    } catch {
      return EMPTY_AUTH_STORE
    }
  }

  private loadSession(): Session | null {
    if (typeof window === "undefined") return null
    try {
      const raw = localStorage.getItem(SESSION_STORAGE_KEY)
      if (!raw) return null
      return JSON.parse(raw) as Session
    } catch {
      return null
    }
  }

  private writeAuthToStorage(authStore: AuthStore): void {
    if (typeof window === "undefined") return
    try {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authStore))
    } catch {
      console.warn("[LocalProvider] Could not write auth to localStorage.")
    }
  }

  private writeSessionToStorage(session: Session | null): void {
    if (typeof window === "undefined") return
    try {
      if (session) {
        localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
      } else {
        localStorage.removeItem(SESSION_STORAGE_KEY)
      }
    } catch {
      console.warn("[LocalProvider] Could not write session to localStorage.")
    }
  }

  private mutateAuth(authStore: AuthStore): void {
    this.authStore = authStore
    this.writeAuthToStorage(authStore)
  }

  /**
   * Copy unscoped `pbbls:store` data to a profile-scoped key on first
   * login/register so existing pebbles are not lost. No-op if the scoped key
   * already exists or there is no unscoped data to migrate.
   */
  private migrateUnscopedData(profileId: string): void {
    if (typeof window === "undefined") return
    const scopedKey = `${STORAGE_KEY}:${profileId}`
    if (localStorage.getItem(scopedKey) !== null) return
    const unscopedData = localStorage.getItem(STORAGE_KEY)
    if (!unscopedData) return
    localStorage.setItem(scopedKey, unscopedData)
    localStorage.removeItem(STORAGE_KEY)
  }

  // ---------------------------------------------------------------------------
  // DataProvider public API
  // ---------------------------------------------------------------------------

  getStore(): Store {
    return this.store
  }

  /**
   * Re-read the content store from localStorage using the current session's
   * storage key. Call this after login/register/logout to switch to the
   * correct per-profile data.
   */
  reloadStore(): Store {
    this.store = this.load()
    return this.store
  }

  async reset(): Promise<Store> {
    const seed = this.fromSeed()
    this.mutate(seed)
    return seed
  }

  // ---------------------------------------------------------------------------
  // Pebbles counter
  // ---------------------------------------------------------------------------

  async getPebblesCount(): Promise<number> {
    return this.store.pebbles_count
  }

  async incrementPebblesCount(): Promise<number> {
    const next = this.store.pebbles_count + 1
    this.mutate({ ...this.store, pebbles_count: next })
    return next
  }

  // ---------------------------------------------------------------------------
  // Karma
  // ---------------------------------------------------------------------------

  async getKarma(): Promise<number> {
    return this.store.karma
  }

  async incrementKarma(
    delta: number,
    reason: string,
    refId?: string,
  ): Promise<number> {
    const event: KarmaEvent = {
      delta,
      reason,
      ...(refId !== undefined && { ref_id: refId }),
      created_at: new Date().toISOString(),
    }
    const next = this.store.karma + delta
    this.mutate({
      ...this.store,
      karma: next,
      karma_log: [...this.store.karma_log, event],
    })
    return next
  }

  // ---------------------------------------------------------------------------
  // Bounce
  // ---------------------------------------------------------------------------

  async getBounce(): Promise<number> {
    return this.store.bounce
  }

  async refreshBounce(): Promise<number> {
    const { bounce, bounce_window } = refreshBounceWindow(this.store.bounce_window)
    this.mutate({ ...this.store, bounce, bounce_window })
    return bounce
  }

  // ---------------------------------------------------------------------------
  // Pebbles
  // ---------------------------------------------------------------------------

  async listPebbles(): Promise<Pebble[]> {
    return this.store.pebbles
  }

  async getPebble(id: string): Promise<Pebble | undefined> {
    return this.store.pebbles.find((p) => p.id === id)
  }

  async createPebble(input: CreatePebbleInput): Promise<Pebble> {
    const now = new Date().toISOString()
    const pebble: Pebble = {
      ...input,
      id: crypto.randomUUID(),
      created_at: now,
      updated_at: now,
    }
    const karmaDelta = computeKarmaDelta(input)
    const karmaEvent: KarmaEvent = {
      delta: karmaDelta,
      reason: "pebble_created",
      ref_id: pebble.id,
      created_at: now,
    }
    this.mutate({
      ...this.store,
      pebbles: [...this.store.pebbles, pebble],
      pebbles_count: this.store.pebbles_count + 1,
      karma: this.store.karma + karmaDelta,
      karma_log: [...this.store.karma_log, karmaEvent],
    })
    await this.refreshBounce()
    return pebble
  }

  async updatePebble(id: string, input: UpdatePebbleInput): Promise<Pebble> {
    const idx = this.store.pebbles.findIndex((p) => p.id === id)
    if (idx === -1) throw new Error(`Pebble not found: ${id}`)
    const prev = this.store.pebbles[idx]
    const updated: Pebble = {
      ...prev,
      ...input,
      updated_at: new Date().toISOString(),
    }
    const pebbles = [...this.store.pebbles]
    pebbles[idx] = updated

    // Recompute karma: award the difference between old and new enrichment.
    const karmaBefore = computeKarmaDelta(prev)
    const karmaAfter = computeKarmaDelta(updated)
    const diff = karmaAfter - karmaBefore

    if (diff !== 0) {
      const karmaEvent: KarmaEvent = {
        delta: diff,
        reason: "pebble_enriched",
        ref_id: id,
        created_at: updated.updated_at,
      }
      this.mutate({
        ...this.store,
        pebbles,
        karma: this.store.karma + diff,
        karma_log: [...this.store.karma_log, karmaEvent],
      })
    } else {
      this.mutate({ ...this.store, pebbles })
    }

    return updated
  }

  async deletePebble(id: string): Promise<void> {
    const pebbles = this.store.pebbles.filter((p) => p.id !== id)
    // Cascade: remove the deleted pebble from all collections.
    const collections = this.store.collections.map((c) => ({
      ...c,
      pebble_ids: c.pebble_ids.filter((pid) => pid !== id),
    }))
    this.mutate({ ...this.store, pebbles, collections })
  }

  // ---------------------------------------------------------------------------
  // Souls
  // ---------------------------------------------------------------------------

  async listSouls(): Promise<Soul[]> {
    return this.store.souls
  }

  async getSoul(id: string): Promise<Soul | undefined> {
    return this.store.souls.find((s) => s.id === id)
  }

  async createSoul(input: CreateSoulInput): Promise<Soul> {
    const now = new Date().toISOString()
    const soul: Soul = {
      ...input,
      id: crypto.randomUUID(),
      created_at: now,
      updated_at: now,
    }
    this.mutate({ ...this.store, souls: [...this.store.souls, soul] })
    return soul
  }

  async updateSoul(id: string, input: UpdateSoulInput): Promise<Soul> {
    const idx = this.store.souls.findIndex((s) => s.id === id)
    if (idx === -1) throw new Error(`Soul not found: ${id}`)
    const updated: Soul = {
      ...this.store.souls[idx],
      ...input,
      updated_at: new Date().toISOString(),
    }
    const souls = [...this.store.souls]
    souls[idx] = updated
    this.mutate({ ...this.store, souls })
    return updated
  }

  async deleteSoul(id: string): Promise<void> {
    const souls = this.store.souls.filter((s) => s.id !== id)
    // Cascade: remove the deleted soul from all pebbles
    const pebbles = this.store.pebbles.map((p) => ({
      ...p,
      soul_ids: p.soul_ids.filter((sid) => sid !== id),
    }))
    this.mutate({ ...this.store, souls, pebbles })
  }

  // ---------------------------------------------------------------------------
  // Collections
  // ---------------------------------------------------------------------------

  async listCollections(): Promise<Collection[]> {
    return this.store.collections
  }

  async getCollection(id: string): Promise<Collection | undefined> {
    return this.store.collections.find((c) => c.id === id)
  }

  async createCollection(input: CreateCollectionInput): Promise<Collection> {
    const now = new Date().toISOString()
    const collection: Collection = {
      ...input,
      id: crypto.randomUUID(),
      created_at: now,
      updated_at: now,
    }
    this.mutate({ ...this.store, collections: [...this.store.collections, collection] })
    return collection
  }

  async updateCollection(id: string, input: UpdateCollectionInput): Promise<Collection> {
    const idx = this.store.collections.findIndex((c) => c.id === id)
    if (idx === -1) throw new Error(`Collection not found: ${id}`)
    const updated: Collection = {
      ...this.store.collections[idx],
      ...input,
      updated_at: new Date().toISOString(),
    }
    const collections = [...this.store.collections]
    collections[idx] = updated
    this.mutate({ ...this.store, collections })
    return updated
  }

  async deleteCollection(id: string): Promise<void> {
    const collections = this.store.collections.filter((c) => c.id !== id)
    this.mutate({ ...this.store, collections })
  }

  // ---------------------------------------------------------------------------
  // Marks
  // ---------------------------------------------------------------------------

  async listMarks(): Promise<Mark[]> {
    return this.store.marks
  }

  async getMark(id: string): Promise<Mark | undefined> {
    return this.store.marks.find((m) => m.id === id)
  }

  async createMark(input: CreateMarkInput): Promise<Mark> {
    const now = new Date().toISOString()
    const mark: Mark = {
      ...input,
      id: crypto.randomUUID(),
      created_at: now,
      updated_at: now,
    }
    this.mutate({ ...this.store, marks: [...this.store.marks, mark] })
    return mark
  }

  async updateMark(id: string, input: UpdateMarkInput): Promise<Mark> {
    const idx = this.store.marks.findIndex((m) => m.id === id)
    if (idx === -1) throw new Error(`Mark not found: ${id}`)
    const updated: Mark = {
      ...this.store.marks[idx],
      ...input,
      updated_at: new Date().toISOString(),
    }
    const marks = [...this.store.marks]
    marks[idx] = updated
    this.mutate({ ...this.store, marks })
    return updated
  }

  async deleteMark(id: string): Promise<void> {
    const marks = this.store.marks.filter((m) => m.id !== id)
    this.mutate({ ...this.store, marks })
  }

  // ---------------------------------------------------------------------------
  // Auth
  // ---------------------------------------------------------------------------

  async register(input: RegisterInput): Promise<Session> {
    const existing = this.authStore.accounts.find(
      (a) => a.username === input.username,
    )
    if (existing) throw new Error("Username already taken")

    const now = new Date().toISOString()
    const passwordHash = await hashPassword(input.password)

    const account: Account = {
      id: crypto.randomUUID(),
      username: input.username,
      password_hash: passwordHash,
      created_at: now,
    }

    // Migrate the legacy onboarding flag into the new profile if it exists.
    const legacyOnboarding =
      typeof window !== "undefined" &&
      localStorage.getItem(ONBOARDING_LEGACY_KEY) === "true"

    const profile: Profile = {
      id: crypto.randomUUID(),
      account_id: account.id,
      display_name: input.username,
      onboarding_completed: legacyOnboarding,
      color_world: "blush-quartz",
      created_at: now,
      updated_at: now,
    }

    this.mutateAuth({
      accounts: [...this.authStore.accounts, account],
      profiles: [...this.authStore.profiles, profile],
    })

    const session: Session = {
      account_id: account.id,
      profile_id: profile.id,
      created_at: now,
    }
    this.session = session
    this.writeSessionToStorage(session)
    this.migrateUnscopedData(profile.id)

    return session
  }

  async login(input: LoginInput): Promise<Session> {
    const account = this.authStore.accounts.find(
      (a) => a.username === input.username,
    )
    if (!account) throw new Error("Invalid username or password")

    const valid = await verifyPassword(input.password, account.password_hash)
    if (!valid) throw new Error("Invalid username or password")

    const profile = this.authStore.profiles.find(
      (p) => p.account_id === account.id,
    )
    if (!profile) throw new Error("Profile not found")

    const session: Session = {
      account_id: account.id,
      profile_id: profile.id,
      created_at: new Date().toISOString(),
    }
    this.session = session
    this.writeSessionToStorage(session)
    this.migrateUnscopedData(profile.id)

    return session
  }

  async logout(): Promise<void> {
    this.session = null
    this.writeSessionToStorage(null)
  }

  getSession(): Session | null {
    return this.session
  }

  async getAccount(): Promise<Account | undefined> {
    if (!this.session) return undefined
    return this.authStore.accounts.find(
      (a) => a.id === this.session!.account_id,
    )
  }

  async getProfile(): Promise<Profile | undefined> {
    if (!this.session) return undefined
    return this.authStore.profiles.find(
      (p) => p.id === this.session!.profile_id,
    )
  }

  async updateProfile(input: UpdateProfileInput): Promise<Profile> {
    if (!this.session) throw new Error("No active session")

    const idx = this.authStore.profiles.findIndex(
      (p) => p.id === this.session!.profile_id,
    )
    if (idx === -1) throw new Error("Profile not found")

    const updated: Profile = {
      ...this.authStore.profiles[idx],
      ...input,
      updated_at: new Date().toISOString(),
    }
    const profiles = [...this.authStore.profiles]
    profiles[idx] = updated
    this.mutateAuth({ ...this.authStore, profiles })

    return updated
  }
}
