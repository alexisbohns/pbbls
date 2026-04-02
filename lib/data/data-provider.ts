import type { Pebble, Soul, Collection, KarmaEvent, Mark } from "@/lib/types"

// ---------------------------------------------------------------------------
// Store snapshot — the in-memory representation of all persisted data.
// Passed through React context so all hooks re-render from the same snapshot.
// ---------------------------------------------------------------------------

export type Store = {
  pebbles: Pebble[]
  souls: Soul[]
  collections: Collection[]
  marks: Mark[]
  pebbles_count: number
  karma: number
  karma_log: KarmaEvent[]
  bounce: number
  bounce_window: string[]
}

// ---------------------------------------------------------------------------
// Input types — omit server-generated fields so callers never touch them.
// ---------------------------------------------------------------------------

export type CreatePebbleInput = Omit<Pebble, "id" | "created_at" | "updated_at">
export type UpdatePebbleInput = Partial<Omit<Pebble, "id" | "created_at" | "updated_at">>

export type CreateSoulInput = Omit<Soul, "id" | "created_at" | "updated_at">
export type UpdateSoulInput = Partial<Omit<Soul, "id" | "created_at" | "updated_at">>

export type CreateCollectionInput = Omit<Collection, "id" | "created_at" | "updated_at">
export type UpdateCollectionInput = Partial<Omit<Collection, "id" | "created_at" | "updated_at">>

export type CreateMarkInput = Omit<Mark, "id" | "created_at" | "updated_at">
export type UpdateMarkInput = Partial<Omit<Mark, "id" | "created_at" | "updated_at">>

// ---------------------------------------------------------------------------
// DataProvider interface — designed for a Supabase swap: keep this interface,
// replace the implementation. All mutation methods return Promises so the
// contract stays async even though LocalProvider resolves synchronously.
// ---------------------------------------------------------------------------

export interface DataProvider {
  /** Return the current in-memory store snapshot. */
  getStore(): Store

  /** Overwrite store with seed data and return the new snapshot. */
  reset(): Promise<Store>

  // Pebbles counter
  getPebblesCount(): Promise<number>
  incrementPebblesCount(): Promise<number>

  // Karma
  getKarma(): Promise<number>
  incrementKarma(delta: number, reason: string, refId?: string): Promise<number>

  // Bounce
  getBounce(): Promise<number>
  refreshBounce(): Promise<number>

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
