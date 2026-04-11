import type { SupabaseClient } from "@supabase/supabase-js"
import type {
  DataProvider,
  Store,
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
} from "@/lib/types"
import { computeKarmaDelta } from "@/lib/data/karma"

const STORAGE_KEY = "pbbls:store"

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

export class SupabaseProvider implements DataProvider {
  private store: Store
  private readonly userId: string
  private readonly supabase: SupabaseClient

  constructor(userId: string, supabase: SupabaseClient) {
    this.userId = userId
    this.supabase = supabase
    this.store = this.loadFromLocalStorage()
  }

  // ---------------------------------------------------------------------------
  // localStorage helpers
  // ---------------------------------------------------------------------------

  private getStorageKey(): string {
    return `${STORAGE_KEY}:${this.userId}`
  }

  private loadFromLocalStorage(): Store {
    if (typeof window === "undefined") return EMPTY_STORE
    try {
      const raw = localStorage.getItem(this.getStorageKey())
      if (!raw) return EMPTY_STORE
      return JSON.parse(raw) as Store
    } catch {
      return EMPTY_STORE
    }
  }

  private saveToLocalStorage(): void {
    if (typeof window === "undefined") return
    try {
      localStorage.setItem(this.getStorageKey(), JSON.stringify(this.store))
    } catch {
      console.warn("[SupabaseProvider] Could not write to localStorage.")
    }
  }

  private mutate(store: Store): void {
    this.store = store
    this.saveToLocalStorage()
  }

  // ---------------------------------------------------------------------------
  // DataProvider — store access
  // ---------------------------------------------------------------------------

  getStore(): Store {
    return this.store
  }

  reloadStore(): Store {
    this.store = this.loadFromLocalStorage()
    return this.store
  }

  async reset(): Promise<Store> {
    this.mutate(EMPTY_STORE)
    return EMPTY_STORE
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
    // Bounce is computed server-side from pebble dates.
    // Locally we just return the cached value; sync updates it.
    return this.store.bounce
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
    this.pushPebbleCreate(pebble, input)
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

    this.pushPebbleUpdate(id, input)
    return updated
  }

  async deletePebble(id: string): Promise<void> {
    const pebbles = this.store.pebbles.filter((p) => p.id !== id)
    const collections = this.store.collections.map((c) => ({
      ...c,
      pebble_ids: c.pebble_ids.filter((pid) => pid !== id),
    }))
    this.mutate({ ...this.store, pebbles, collections })
    this.pushPebbleDelete(id)
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
    this.pushSoulCreate(soul)
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
    this.pushSoulUpdate(id, input)
    return updated
  }

  async deleteSoul(id: string): Promise<void> {
    const souls = this.store.souls.filter((s) => s.id !== id)
    const pebbles = this.store.pebbles.map((p) => ({
      ...p,
      soul_ids: p.soul_ids.filter((sid) => sid !== id),
    }))
    this.mutate({ ...this.store, souls, pebbles })
    this.pushSoulDelete(id)
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
    this.mutate({
      ...this.store,
      collections: [...this.store.collections, collection],
    })
    this.pushCollectionCreate(collection)
    return collection
  }

  async updateCollection(
    id: string,
    input: UpdateCollectionInput,
  ): Promise<Collection> {
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
    this.pushCollectionUpdate(id, input)
    return updated
  }

  async deleteCollection(id: string): Promise<void> {
    const collections = this.store.collections.filter((c) => c.id !== id)
    this.mutate({ ...this.store, collections })
    this.pushCollectionDelete(id)
  }

  // ---------------------------------------------------------------------------
  // Marks (mapped to DB "glyphs")
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
    this.pushMarkCreate(mark)
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
    this.pushMarkUpdate(id, input)
    return updated
  }

  async deleteMark(id: string): Promise<void> {
    const marks = this.store.marks.filter((m) => m.id !== id)
    this.mutate({ ...this.store, marks })
    this.pushMarkDelete(id)
  }

  // ---------------------------------------------------------------------------
  // Background push stubs — implemented in Task 3
  // ---------------------------------------------------------------------------

  private pushPebbleCreate(_pebble: Pebble, _input: CreatePebbleInput): void {}
  private pushPebbleUpdate(_id: string, _input: UpdatePebbleInput): void {}
  private pushPebbleDelete(_id: string): void {}
  private pushSoulCreate(_soul: Soul): void {}
  private pushSoulUpdate(_id: string, _input: UpdateSoulInput): void {}
  private pushSoulDelete(_id: string): void {}
  private pushCollectionCreate(_collection: Collection): void {}
  private pushCollectionUpdate(_id: string, _input: UpdateCollectionInput): void {}
  private pushCollectionDelete(_id: string): void {}
  private pushMarkCreate(_mark: Mark): void {}
  private pushMarkUpdate(_id: string, _input: UpdateMarkInput): void {}
  private pushMarkDelete(_id: string): void {}
}
