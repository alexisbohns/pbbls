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
  // Background push — fire-and-forget to Supabase
  // ---------------------------------------------------------------------------

  private async safePush(label: string, fn: () => PromiseLike<unknown>): Promise<void> {
    try {
      await fn()
    } catch (err) {
      console.warn(`[SupabaseProvider] ${label} failed:`, err)
    }
  }

  private pushPebbleCreate(_pebble: Pebble, input: CreatePebbleInput): void {
    void this.safePush("pushPebbleCreate", () =>
      this.supabase.rpc("create_pebble", {
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
      }),
    )
  }

  private pushPebbleUpdate(id: string, input: UpdatePebbleInput): void {
    void this.safePush("pushPebbleUpdate", () =>
      this.supabase.rpc("update_pebble", {
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
      }),
    )
  }

  private pushPebbleDelete(id: string): void {
    void this.safePush("pushPebbleDelete", () =>
      this.supabase.rpc("delete_pebble", { p_pebble_id: id }),
    )
  }

  private pushSoulCreate(soul: Soul): void {
    void this.safePush("pushSoulCreate", () =>
      this.supabase.from("souls").insert({
        id: soul.id,
        user_id: this.userId,
        name: soul.name,
      }),
    )
  }

  private pushSoulUpdate(id: string, input: UpdateSoulInput): void {
    void this.safePush("pushSoulUpdate", () =>
      this.supabase.from("souls").update({
        ...(input.name !== undefined && { name: input.name }),
      }).eq("id", id),
    )
  }

  private pushSoulDelete(id: string): void {
    void this.safePush("pushSoulDelete", () =>
      this.supabase.from("souls").delete().eq("id", id),
    )
  }

  private pushCollectionCreate(collection: Collection): void {
    void this.safePush("pushCollectionCreate", async () => {
      await this.supabase.from("collections").insert({
        id: collection.id,
        user_id: this.userId,
        name: collection.name,
        mode: collection.mode ?? null,
      })
      if (collection.pebble_ids.length > 0) {
        await this.supabase.from("collection_pebbles").insert(
          collection.pebble_ids.map((pid) => ({
            collection_id: collection.id,
            pebble_id: pid,
          })),
        )
      }
    })
  }

  private pushCollectionUpdate(id: string, input: UpdateCollectionInput): void {
    void this.safePush("pushCollectionUpdate", async () => {
      const updates: Record<string, unknown> = {}
      if (input.name !== undefined) updates.name = input.name
      if (input.mode !== undefined) updates.mode = input.mode
      if (Object.keys(updates).length > 0) {
        await this.supabase.from("collections").update(updates).eq("id", id)
      }
      if (input.pebble_ids !== undefined) {
        await this.supabase.from("collection_pebbles").delete().eq("collection_id", id)
        if (input.pebble_ids.length > 0) {
          await this.supabase.from("collection_pebbles").insert(
            input.pebble_ids.map((pid) => ({
              collection_id: id,
              pebble_id: pid,
            })),
          )
        }
      }
    })
  }

  private pushCollectionDelete(id: string): void {
    void this.safePush("pushCollectionDelete", () =>
      this.supabase.from("collections").delete().eq("id", id),
    )
  }

  private pushMarkCreate(mark: Mark): void {
    void this.safePush("pushMarkCreate", () =>
      this.supabase.from("glyphs").insert({
        id: mark.id,
        user_id: this.userId,
        name: mark.name ?? null,
        shape_id: mark.shape_id,
        strokes: mark.strokes,
        view_box: mark.viewBox,
      }),
    )
  }

  private pushMarkUpdate(id: string, input: UpdateMarkInput): void {
    void this.safePush("pushMarkUpdate", () => {
      const updates: Record<string, unknown> = {}
      if (input.name !== undefined) updates.name = input.name
      if (input.shape_id !== undefined) updates.shape_id = input.shape_id
      if (input.strokes !== undefined) updates.strokes = input.strokes
      if (input.viewBox !== undefined) updates.view_box = input.viewBox
      return this.supabase.from("glyphs").update(updates).eq("id", id)
    })
  }

  private pushMarkDelete(id: string): void {
    void this.safePush("pushMarkDelete", () =>
      this.supabase.from("glyphs").delete().eq("id", id),
    )
  }
}
