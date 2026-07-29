import type {
  Pebble,
  PebbleSnap,
  Soul,
  Collection,
  KarmaEvent,
  Mark,
  MarketGlyph,
  GlyphSubmission,
  WalletSnapshot,
  RippleSummary,
  ProfileEngagement,
} from "@/lib/types"

// ---------------------------------------------------------------------------
// Store snapshot — the in-memory representation of all persisted data.
// Passed through React context so all hooks re-render from the same snapshot.
// ---------------------------------------------------------------------------

export type Store = {
  pebbles: Pebble[]
  souls: Soul[]
  collections: Collection[]
  marks: Mark[]
  entitledMarks: Mark[]
  pebbles_count: number
  karma: number
  karma_log: KarmaEvent[]
  bounce: number
  bounce_window: string[]
}

export const EMPTY_STORE: Store = {
  pebbles: [],
  souls: [],
  collections: [],
  marks: [],
  entitledMarks: [],
  pebbles_count: 0,
  karma: 0,
  karma_log: [],
  bounce: 0,
  bounce_window: [],
}

// ---------------------------------------------------------------------------
// Input types — omit server-generated fields so callers never touch them.
// ---------------------------------------------------------------------------

// Render columns are written by the compose-pebble / compose-pebble-update edge
// functions, never by the client — keep them out of mutation inputs.
// `instants` is the read-path projection (signed URLs); writes use `snaps`.
type ServerOwnedPebbleFields =
  | "id"
  | "created_at"
  | "updated_at"
  | "render_svg"
  | "render_version"
  | "instants"

export type CreatePebbleInput = Omit<Pebble, ServerOwnedPebbleFields>
export type UpdatePebbleInput = Partial<Omit<Pebble, ServerOwnedPebbleFields>>

// glyph_id is optional on create — providers default it to the system glyph
// (matches the DB column default in `20260426000000_add_glyph_to_souls.sql`).
export type CreateSoulInput = Omit<Soul, "id" | "glyph_id" | "created_at" | "updated_at"> & {
  glyph_id?: string
}
export type UpdateSoulInput = Partial<Omit<Soul, "id" | "created_at" | "updated_at">>

export type CreateCollectionInput = Omit<Collection, "id" | "created_at" | "updated_at">
export type UpdateCollectionInput = Partial<Omit<Collection, "id" | "created_at" | "updated_at">>

export type CreateMarkInput = Omit<Mark, "id" | "created_at" | "updated_at">
// `name: null` clears the column; `name: undefined` leaves it unchanged.
export type UpdateMarkInput = Partial<
  Omit<Mark, "id" | "name" | "created_at" | "updated_at">
> & { name?: string | null }

export type WalletHistoryPage = {
  events: KarmaEvent[]
  nextCursor: string | null
}

// ---------------------------------------------------------------------------
// Drafts (M47) — a draft is a PARTIAL compose-pebble wire payload, stored as
// jsonb in `pebble_drafts.payload`. Deliberately keyed like the wire and not
// like `CreatePebbleInput` (note `glyph_id`, not `mark_id`, and `snaps` shaped
// as the payload wants them): publishing is then a pass-through, and the same
// shape is what iOS/Android persist. Unset keys are OMITTED, never nulled —
// `description` and `glyph_id` are the only keys where `null` is meaningful.
// See docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md (D2).
// ---------------------------------------------------------------------------

export type DraftSnapPayload = {
  id: string
  storage_path: string
  sort_order: number
}

export type PebbleDraftPayload = {
  name?: string
  description?: string | null
  happened_at?: string
  intensity?: Pebble["intensity"]
  positiveness?: Pebble["positiveness"]
  visibility?: Pebble["visibility"]
  emotion_id?: string
  domain_ids?: string[]
  soul_ids?: string[]
  collection_ids?: string[]
  glyph_id?: string | null
  snaps?: DraftSnapPayload[]
}

export type PebbleDraftRecord = {
  id: string
  payload: PebbleDraftPayload
  created_at: string
  updated_at: string
}

// ---------------------------------------------------------------------------
// DataProvider interface — implemented by SupabaseProvider.
// All mutation methods return Promises to match async Supabase calls.
// ---------------------------------------------------------------------------

export interface DataProvider {
  getStore(): Store
  loadFromSupabase(): Promise<Store>
  reset(): Promise<Store>

  getWallet(): Promise<WalletSnapshot>
  getWalletHistory(cursor?: string, limit?: number): Promise<WalletHistoryPage>
  spendKarma(amount: number, reason: "purchase", refId?: string): Promise<string>

  getPebblesCount(): Promise<number>
  getKarma(): Promise<number>
  getBounce(): Promise<number>

  // Profile dashboard reads (on-demand, not part of the eager store):
  // ripple level/activity from v_ripple, and days-practiced + 28-day
  // assiduity from the get_profile_engagement RPC.
  getRipple(): Promise<RippleSummary>
  getProfileEngagement(tz: string): Promise<ProfileEngagement>

  listPebbles(): Promise<Pebble[]>
  getPebble(id: string): Promise<Pebble | undefined>
  createPebble(input: CreatePebbleInput): Promise<Pebble>
  updatePebble(id: string, input: UpdatePebbleInput): Promise<Pebble>
  deletePebble(id: string): Promise<void>

  // Snap media: upload processes the file client-side (mirrors iOS
  // ImagePipeline) and writes original + thumb JPEGs to `pebbles-media`.
  // The returned `PebbleSnap` is what callers pass to `createPebble`/
  // `updatePebble` via the `snaps` field. `deletePebbleMedia` is the
  // eager-cleanup variant for existing snaps (file delete + DB row).
  uploadSnap(file: File): Promise<PebbleSnap>
  deletePebbleMedia(snapId: string): Promise<void>

  // Drafts: owner-scoped single-table CRUD, deliberately outside the eager
  // `Store` (their own surface, fetched on demand). `savePebbleDraft` upserts —
  // omit `id` to create, pass it to replace that draft's payload wholesale.
  // None of these touch karma: `create_pebble` is the only emitter and a draft
  // never reaches it.
  listPebbleDrafts(): Promise<PebbleDraftRecord[]>
  getPebbleDraft(id: string): Promise<PebbleDraftRecord | undefined>
  savePebbleDraft(payload: PebbleDraftPayload, id?: string): Promise<PebbleDraftRecord>
  deletePebbleDraft(id: string): Promise<void>
  /** Signed read URL for a draft snap's thumb (drafts have no `instants`). */
  getDraftSnapUrl(storagePath: string): Promise<string | undefined>

  listSouls(): Promise<Soul[]>
  getSoul(id: string): Promise<Soul | undefined>
  createSoul(input: CreateSoulInput): Promise<Soul>
  updateSoul(id: string, input: UpdateSoulInput): Promise<Soul>
  deleteSoul(id: string): Promise<void>

  listCollections(): Promise<Collection[]>
  getCollection(id: string): Promise<Collection | undefined>
  createCollection(input: CreateCollectionInput): Promise<Collection>
  updateCollection(id: string, input: UpdateCollectionInput): Promise<Collection>
  deleteCollection(id: string): Promise<void>

  listMarks(): Promise<Mark[]>
  getMark(id: string): Promise<Mark | undefined>
  createMark(input: CreateMarkInput): Promise<Mark>
  updateMark(id: string, input: UpdateMarkInput): Promise<Mark>
  deleteMark(id: string): Promise<void>

  listMarketGlyphs(): Promise<MarketGlyph[]>     // approved, others' glyphs, + caller flags
  listFavouriteGlyphs(): Promise<MarketGlyph[]>  // entitled ∪ favourited
  getMySubmissions(): Promise<GlyphSubmission[]> // caller's submissions (status badges)
  submitGlyph(glyphId: string): Promise<GlyphSubmission>
  buyGlyph(glyphId: string): Promise<{ entitlementId: string; karma: number }>
  setFavourite(glyphId: string, favourite: boolean): Promise<void>
}
