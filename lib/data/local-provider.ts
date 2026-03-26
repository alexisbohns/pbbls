import type {
  DataProvider,
  Store,
  CreatePebbleInput,
  UpdatePebbleInput,
  CreateSoulInput,
  UpdateSoulInput,
  CreateCollectionInput,
  UpdateCollectionInput,
} from "@/lib/data/data-provider"
import type { Pebble, Soul, Collection } from "@/lib/types"
import { SEED_PEBBLES, SEED_SOULS, SEED_COLLECTIONS } from "@/lib/seed/seed-data"

const STORAGE_KEY = "pbbls:store"

const EMPTY_STORE: Store = { pebbles: [], souls: [], collections: [] }

export class LocalProvider implements DataProvider {
  private store: Store

  constructor() {
    this.store = this.load()
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  private load(): Store {
    // localStorage is not available during SSR — return an empty store so the
    // server render is consistent. The client will hydrate in useEffect.
    if (typeof window === "undefined") return EMPTY_STORE

    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) {
        const seed = this.fromSeed()
        this.writeToStorage(seed)
        return seed
      }
      return JSON.parse(raw) as Store
    } catch {
      // Corrupt or unreadable storage — fall back to seed.
      const seed = this.fromSeed()
      this.writeToStorage(seed)
      return seed
    }
  }

  private fromSeed(): Store {
    const now = new Date().toISOString()
    return {
      pebbles: SEED_PEBBLES.map((p) => ({ ...p, created_at: now, updated_at: now })),
      souls: SEED_SOULS.map((s) => ({ ...s, created_at: now, updated_at: now })),
      collections: SEED_COLLECTIONS.map((c) => ({ ...c, created_at: now, updated_at: now })),
    }
  }

  private writeToStorage(store: Store): void {
    if (typeof window === "undefined") return
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
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
  // DataProvider public API
  // ---------------------------------------------------------------------------

  getStore(): Store {
    return this.store
  }

  reset(): Store {
    const seed = this.fromSeed()
    this.mutate(seed)
    return seed
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
    this.mutate({ ...this.store, pebbles: [...this.store.pebbles, pebble] })
    return pebble
  }

  async updatePebble(id: string, input: UpdatePebbleInput): Promise<Pebble> {
    const idx = this.store.pebbles.findIndex((p) => p.id === id)
    if (idx === -1) throw new Error(`Pebble not found: ${id}`)
    const updated: Pebble = {
      ...this.store.pebbles[idx],
      ...input,
      updated_at: new Date().toISOString(),
    }
    const pebbles = [...this.store.pebbles]
    pebbles[idx] = updated
    this.mutate({ ...this.store, pebbles })
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
    this.mutate({ ...this.store, souls })
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
}
