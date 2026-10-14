import type {
  Moment,
  PebbleSnap,
  Soul,
  Collection,
  Connection,
  ConnectionPeer,
  KarmaEvent,
  Mark,
  MarketGlyph,
  GlyphSubmission,
  WalletSnapshot,
  RippleSummary,
  ProfileEngagement,
  Achievement,
  AchievementUnlock,
  SharedConnectionPebble,
} from "@/lib/types"
import type { CompletePebblePayload, SaveMomentPayload } from "@/lib/data/moment-payloads"

// ---------------------------------------------------------------------------
// Store snapshot — the in-memory representation of all persisted data.
// Passed through React context so all hooks re-render from the same snapshot.
// ---------------------------------------------------------------------------

export type Store = {
  /** The source of truth (#1050): every own moment from `v_moments_full`, newest first. */
  moments: Moment[]
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
  moments: [],
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

// One newly granted badge from `check_achievements()`. `karmaGranted` is what
// the ledger actually received at unlock time (0 for reward-less badges),
// never the catalog's current value.
export type AchievementUnlockResult = {
  slug: string
  karmaGranted: number
}

// ---------------------------------------------------------------------------
// Connections (M49) — operation results for the definer-RPC wire contract.
// See docs/superpowers/specs/2026-07-29-mutual-connections-design.md.
// ---------------------------------------------------------------------------

// The caller's live multi-use invite. The client composes the share URL
// (`${origin}/invite/<token>`) — the server returns only the token (D11).
export type ConnectionInvite = {
  token: string
  expiresAt: string
  createdAt: string
}

// Accept is idempotent: a repeat accept SUCCEEDS with `alreadyConnected: true`
// (re-scans and retries are the normal case for a multi-use QR, D5).
export type AcceptConnectionInviteResult = {
  connectionId: string
  alreadyConnected: boolean
  connectedAt: string
  peer: ConnectionPeer
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

  // Achievements (on-demand, not part of the eager store): the full public
  // catalog including inactive rows (clients decide what to render), the
  // caller's unlock ledger, and the idempotent evaluation RPC — it inserts
  // whatever the caller now qualifies for and returns only what it NEWLY
  // granted (empty array = nothing new).
  getAchievements(): Promise<Achievement[]>
  getAchievementUnlocks(): Promise<AchievementUnlock[]>
  checkAchievements(): Promise<AchievementUnlockResult[]>

  // Moments (#1050): the four definer RPCs, called directly, one per user
  // action (R5). Each returns once its RPC succeeded, then reloads the store
  // best-effort: a failed reload is logged and leaves the store stale, and the
  // write still counts (retrying a `save_moment` create would duplicate the
  // moment). A refused save or completion throws a `MomentWriteError`.
  /** Creates (no id) or edits a moment; returns the moment id. */
  saveMoment(payload: SaveMomentPayload): Promise<string>
  /** Creates or updates the caller's pebble on a moment; returns the pebble id. */
  completePebble(payload: CompletePebblePayload): Promise<string>
  /**
   * The karma one pebble has earned the caller so far: the sum of its
   * `karma_events` (`ref_id` = the pebble id). Null when the read fails.
   */
  pebbleKarma(pebbleId: string): Promise<number | null>
  /** Deletes the moment, its pebbles and snaps, then removes the snap files best-effort. */
  deleteMoment(momentId: string): Promise<void>
  /** Deletes the pebble only, keyed by the PEBBLE id; the moment stays, bare. */
  deletePebble(pebbleId: string): Promise<void>
  /**
   * Removes the files of snaps no row holds any more (a photo an edit detached,
   * an upload a cancel discarded), best-effort: a failure is logged, never thrown.
   */
  removeSnapFiles(storagePaths: string[]): Promise<void>

  // Snap media: upload processes the file client-side (mirrors iOS
  // ImagePipeline) and writes original + thumb JPEGs to `pebbles-media`. The
  // returned `PebbleSnap` is what a `save_moment` payload carries in `snaps`.
  // A photo is detached by saving the moment without it (R6), never by a
  // separate call.
  uploadSnap(file: File): Promise<PebbleSnap>

  // Connections (M49): definer-RPC-only — every write is multi-table validated
  // logic (accept touches invites, blocks and connections), so there are no
  // direct table writes on any surface. Reads return display projections
  // (name + glyph geometry), never a profiles row. Error slugs
  // (invite_not_found, invite_expired, cannot_accept_own_invite, not_found)
  // arrive as substrings of the thrown message — match with `.includes` on the
  // lowercased message. None of these touch karma (D9).
  listConnections(): Promise<Connection[]>
  /**
   * The pebbles a connection shares with the viewer, newest first. Null when
   * the connection id does not exist or is not the viewer's (indistinguishable
   * by design). Direct reads under the widened M51 RLS — no RPC (spec D2).
   */
  listConnectionSharedPebbles(connectionId: string): Promise<SharedConnectionPebble[] | null>
  /** Returns the live invite; `rotate` revokes it and mints a fresh one. */
  createConnectionInvite(rotate?: boolean): Promise<ConnectionInvite>
  acceptConnectionInvite(token: string): Promise<AcceptConnectionInviteResult>
  /** Severs for both sides; `block` additionally blocks the removed peer. */
  removeConnection(id: string, block?: boolean): Promise<void>

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
