import type { SupabaseClient } from "@supabase/supabase-js"
import {
  EMPTY_STORE,
  type DataProvider,
  type Store,
  type CreatePebbleInput,
  type CreateSoulInput,
  type UpdateSoulInput,
  type CreateCollectionInput,
  type UpdateCollectionInput,
  type CreateMarkInput,
  type UpdateMarkInput,
  type WalletHistoryPage,
  type ConnectionInvite,
  type AcceptConnectionInviteResult,
  type AchievementUnlockResult,
} from "@/lib/data/data-provider"
import type {
  Moment,
  Pebble,
  PebbleSnap,
  Soul,
  Collection,
  Connection,
  Mark,
  MarketGlyph,
  GlyphSubmission,
  KarmaEvent,
  WalletSnapshot,
  RippleSummary,
  ProfileEngagement,
  Achievement,
  AchievementUnlock,
  SharedConnectionPebble,
} from "@/lib/types"
import { toConnectionPeer } from "@/lib/data/invite-api"
import { peerIdOf } from "@/lib/data/connection-peer"
import { DEFAULT_GLYPH_ID } from "@/lib/config/glyphs"
import {
  snapOriginalPath,
  snapThumbPath,
  sortedSnaps,
} from "@/lib/data/pebble-row"
import {
  compareMomentsNewestFirst,
  deleteMomentPaths,
  isCompleted,
  legacyPebbleFromMoment,
  momentFromViewRow,
} from "@/lib/data/moment-row"
import {
  compareSharedNewestFirst,
  sharedConnectionPebbleFromRow,
} from "@/lib/data/connection-pebble-row"
import { processPebbleImage } from "@/lib/utils/process-pebble-image"
import { ComposeError, readEdgeErrorBody } from "@/lib/data/compose-error"
import { MomentWriteError } from "@/lib/data/moment-write-error"
import type { CompletePebblePayload, SaveMomentPayload } from "@/lib/data/moment-payloads"

const PEBBLE_MEDIA_BUCKET = "pebbles-media"
const SNAP_URL_TTL_SECONDS = 3600

/** The deprecated flat list (#1050 Part 6 removes it): completed moments only. */
function derivePebbles(moments: readonly Moment[]): Pebble[] {
  return moments.filter(isCompleted).map(legacyPebbleFromMoment)
}

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
      momentsRes,
      soulsRes,
      collectionsRes,
      collectionPebblesRes,
      glyphsRes,
      entitledRes,
      karmaRes,
      bounceRes,
    ] = await Promise.all([
      // The user_id filter is load-bearing: moments_select also opens the
      // connections' moments, which are not this user's path.
      this.supabase
        .from("v_moments_full")
        .select("id, user_id, name, description, happened_at, created_at, updated_at, snaps, pebble")
        .eq("user_id", this.userId),
      this.supabase.from("souls").select("*").eq("user_id", this.userId),
      this.supabase.from("collections").select("*").eq("user_id", this.userId),
      this.supabase.from("collection_pebbles").select("*, collections!inner(user_id)").eq("collections.user_id", this.userId),
      this.supabase.from("glyphs").select("*").eq("user_id", this.userId),
      this.supabase.from("v_glyph_market").select("*").eq("owned", true),
      this.supabase.from("v_karma_summary").select("*").eq("user_id", this.userId).maybeSingle(),
      this.supabase.from("v_bounce").select("*").eq("user_id", this.userId).maybeSingle(),
    ])

    if (momentsRes.error) throw new Error(`Failed to load moments: ${momentsRes.error.message}`)
    if (soulsRes.error) throw new Error(`Failed to load souls: ${soulsRes.error.message}`)
    if (collectionsRes.error) throw new Error(`Failed to load collections: ${collectionsRes.error.message}`)
    if (glyphsRes.error) throw new Error(`Failed to load glyphs: ${glyphsRes.error.message}`)
    if (entitledRes.error) throw new Error(`Failed to load entitled glyphs: ${entitledRes.error.message}`)

    // Snaps are stored in the private `pebbles-media` bucket as
    // `{storage_path}/original.jpg` (+ `…/thumb.jpg`); the view only carries
    // the prefix. Collect every original.jpg path across all moments, mint
    // signed URLs in a single round-trip (mirrors iOS's 1 h TTL in
    // PebbleSnapRepository), and hand the resulting URLs back to each moment
    // in `sort_order`.
    const momentRows: Record<string, unknown>[] = momentsRes.data ?? []
    const snapSets: PebbleSnap[][] = []
    for (const r of momentRows) {
      const snaps = sortedSnaps(r.snaps)
      if (snaps.length > 0) snapSets.push(snaps)
    }

    // Every original, plus the first snap's thumb: the Spine's card probes the
    // thumb for the photo's ratio. One batch either way.
    const signPaths: string[] = []
    for (const snaps of snapSets) {
      for (const s of snaps) signPaths.push(snapOriginalPath(s))
      const first = snaps[0]
      if (first) signPaths.push(snapThumbPath(first))
    }

    const signedUrlByPath = new Map<string, string>()
    if (signPaths.length > 0) {
      const { data, error } = await this.supabase.storage
        .from(PEBBLE_MEDIA_BUCKET)
        .createSignedUrls(signPaths, SNAP_URL_TTL_SECONDS)
      if (error) {
        console.warn("[moments] failed to sign snap URLs", error)
      } else {
        for (const entry of data ?? []) {
          if (entry.signedUrl && entry.path) {
            signedUrlByPath.set(entry.path, entry.signedUrl)
          }
        }
      }
    }

    const moments: Moment[] = momentRows
      .map((row) => momentFromViewRow(row, signedUrlByPath))
      .sort(compareMomentsNewestFirst)
    const pebbles = derivePebbles(moments)

    const souls: Soul[] = (soulsRes.data ?? []).map((row: Record<string, unknown>) => ({
      id: row.id as string,
      name: row.name as string,
      glyph_id: row.glyph_id as string,
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
      strokes: row.strokes as Mark["strokes"],
      viewBox: row.view_box as string,
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }))

    const entitledMarks: Mark[] = (entitledRes.data ?? []).map((row) =>
      this.rowToMark(row as Record<string, unknown>),
    )

    const karma = (karmaRes.data as Record<string, unknown>)?.total_karma as number ?? 0
    const pebblesCount = (karmaRes.data as Record<string, unknown>)?.pebbles_count as number ?? 0
    const bounce = (bounceRes.data as Record<string, unknown>)?.bounce_level as number ?? 0

    const newStore: Store = {
      moments,
      pebbles,
      souls,
      collections,
      marks,
      entitledMarks,
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
  // Read helpers
  // ---------------------------------------------------------------------------

  async getPebblesCount(): Promise<number> { return this.store.pebbles_count }
  async getKarma(): Promise<number> { return this.store.karma }
  async getBounce(): Promise<number> { return this.store.bounce }

  // ---------------------------------------------------------------------------
  // Profile dashboard reads — ripple summary (v_ripple) and engagement stats
  // (get_profile_engagement RPC). Both are scoped to the caller by RLS.
  // ---------------------------------------------------------------------------

  async getRipple(): Promise<RippleSummary> {
    const { data } = await this.supabase
      .from("v_ripple")
      .select("ripple_level, active_today, pebbles_28d")
      .eq("user_id", this.userId)
      .maybeSingle()
    return {
      level: (data?.ripple_level as number) ?? 0,
      activeToday: (data?.active_today as boolean) ?? false,
      pebbles28d: (data?.pebbles_28d as number) ?? 0,
    }
  }

  async getProfileEngagement(tz: string): Promise<ProfileEngagement> {
    // The RPC `returns table(...)`, so PostgREST yields an array of rows.
    const { data, error } = await this.supabase.rpc("get_profile_engagement", {
      p_tz: tz,
    })
    if (error) throw new Error(`getProfileEngagement failed: ${error.message}`)
    const row = Array.isArray(data) ? data[0] : data
    return {
      daysPracticed: (row?.days_practiced as number) ?? 0,
      assiduity: (row?.assiduity as boolean[]) ?? [],
    }
  }

  // ---------------------------------------------------------------------------
  // Achievements (M48) — public catalog, owner-scoped unlock ledger, and the
  // idempotent check_achievements() evaluation RPC.
  // ---------------------------------------------------------------------------

  async getAchievements(): Promise<Achievement[]> {
    const { data, error } = await this.supabase
      .from("achievements")
      .select("*")
      .order("sort_order", { ascending: true })
    if (error) throw new Error(`getAchievements failed: ${error.message}`)
    return (data ?? []).map((row) => {
      const r = row as Record<string, unknown>
      return {
        id: r.id as string,
        slug: r.slug as string,
        // The `family` CHECK constraint on the catalog makes this cast sound.
        family: r.family as Achievement["family"],
        threshold: (r.threshold as number | null) ?? null,
        emotionId: (r.emotion_id as string | null) ?? null,
        domainId: (r.domain_id as string | null) ?? null,
        sortOrder: r.sort_order as number,
        glyphId: (r.glyph_id as string | null) ?? null,
        karmaReward: (r.karma_reward as number) ?? 0,
        isActive: Boolean(r.is_active),
        titleEn: (r.title_en as string | null) ?? null,
        titleFr: (r.title_fr as string | null) ?? null,
        descriptionEn: (r.description_en as string | null) ?? null,
        descriptionFr: (r.description_fr as string | null) ?? null,
      }
    })
  }

  async getAchievementUnlocks(): Promise<AchievementUnlock[]> {
    const { data, error } = await this.supabase
      .from("achievement_unlocks")
      .select("achievement_id, unlocked_at")
      .eq("user_id", this.userId)
    if (error) throw new Error(`getAchievementUnlocks failed: ${error.message}`)
    return (data ?? []).map((row) => {
      const r = row as Record<string, unknown>
      return {
        achievementId: r.achievement_id as string,
        unlockedAt: r.unlocked_at as string,
      }
    })
  }

  async checkAchievements(): Promise<AchievementUnlockResult[]> {
    // The RPC `returns table(...)`, so PostgREST yields an array of rows —
    // empty when nothing newly unlocked.
    const { data, error } = await this.supabase.rpc("check_achievements")
    if (error) throw new Error(`check_achievements failed: ${error.message}`)
    return ((data ?? []) as Record<string, unknown>[]).map((row) => ({
      slug: row.slug as string,
      karmaGranted: (row.karma_granted as number) ?? 0,
    }))
  }

  // ---------------------------------------------------------------------------
  // Wallet — read summary + on-demand paginated history, and spend via RPC.
  // ---------------------------------------------------------------------------

  async getWallet(): Promise<WalletSnapshot> {
    const { data } = await this.supabase
      .from("v_wallet_summary")
      .select("*")
      .eq("user_id", this.userId)
      .maybeSingle()
    return {
      balance: (data?.balance as number) ?? 0,
      totalEarned: (data?.total_earned as number) ?? 0,
      totalSpent: (data?.total_spent as number) ?? 0,
    }
  }

  async getWalletHistory(cursor?: string, limit = 20): Promise<WalletHistoryPage> {
    let query = this.supabase
      .from("karma_events")
      .select("id, type, delta, reason, ref_id, created_at")
      .eq("user_id", this.userId)
      .order("created_at", { ascending: false })
      .order("id", { ascending: false })
      .limit(limit + 1)
    if (cursor) {
      // Composite keyset cursor "<created_at>|<id>": fetch rows strictly before
      // (created_at, id) under the (desc, desc) ordering, so same-timestamp rows
      // straddling a page boundary are not dropped.
      const sep = cursor.lastIndexOf("|")
      const ts = cursor.slice(0, sep)
      const id = cursor.slice(sep + 1)
      query = query.or(`created_at.lt.${ts},and(created_at.eq.${ts},id.lt.${id})`)
    }
    const { data, error } = await query
    if (error) throw new Error(`getWalletHistory failed: ${error.message}`)
    // `as KarmaEvent[]` is load-bearing: PostgREST types `type`/`reason` as plain
    // strings; the karma_events CHECK constraints are what make this cast sound.
    const rows = (data ?? []) as KarmaEvent[]
    const hasMore = rows.length > limit
    const events = hasMore ? rows.slice(0, limit) : rows
    const last = events[events.length - 1]
    const nextCursor = hasMore && last ? `${last.created_at}|${last.id}` : null
    return { events, nextCursor }
  }

  async spendKarma(amount: number, reason: "purchase", refId?: string): Promise<string> {
    const { data, error } = await this.supabase.rpc("spend_karma", {
      p_amount: amount,
      p_reason: reason,
      p_ref_id: refId ?? null,
    })
    if (error) throw new Error(`spend_karma failed: ${error.message}`)
    return data as string
  }
  async listPebbles(): Promise<Pebble[]> { return this.store.pebbles }
  async getPebble(id: string): Promise<Pebble | undefined> { return this.store.pebbles.find((p) => p.id === id) }
  async listSouls(): Promise<Soul[]> { return this.store.souls }
  async getSoul(id: string): Promise<Soul | undefined> { return this.store.souls.find((s) => s.id === id) }
  async listCollections(): Promise<Collection[]> { return this.store.collections }
  async getCollection(id: string): Promise<Collection | undefined> { return this.store.collections.find((c) => c.id === id) }
  async listMarks(): Promise<Mark[]> { return this.store.marks }
  async getMark(id: string): Promise<Mark | undefined> { return this.store.marks.find((m) => m.id === id) }

  // ---------------------------------------------------------------------------
  // Pebble mutations — Supabase first, then reload full store
  // ---------------------------------------------------------------------------

  async createPebble(input: CreatePebbleInput): Promise<Pebble> {
    const payload = {
      name: input.name,
      description: input.description ?? null,
      happened_at: input.happened_at,
      intensity: input.intensity,
      positiveness: input.positiveness,
      visibility: input.visibility,
      emotion_id: input.emotion_id,
      soul_ids: input.soul_ids,
      domain_ids: input.domain_ids,
      collection_ids: input.collection_ids,
      snaps: input.snaps.map((s, i) => ({
        id: s.id,
        storage_path: s.storage_path,
        sort_order: i,
      })),
      cards: input.cards.map((c, i) => ({
        species_id: c.species_id,
        value: c.value,
        sort_order: i,
      })),
    }
    const pebbleId = await this.invokeCompose("compose-pebble", { payload })
    await this.loadFromSupabase()
    const created = this.store.pebbles.find((p) => p.id === pebbleId)
    if (!created) throw new Error(`Pebble not found after create: ${pebbleId}`)
    return created
  }

  // ---------------------------------------------------------------------------
  // Snap media — mirror of iOS PebbleSnapRepository.uploadProcessed.
  // ---------------------------------------------------------------------------

  async uploadSnap(file: File): Promise<PebbleSnap> {
    const processed = await processPebbleImage(file)
    const snapId = crypto.randomUUID()
    const storagePath = `${this.userId}/${snapId}`
    const bucket = this.supabase.storage.from(PEBBLE_MEDIA_BUCKET)
    const options = { contentType: "image/jpeg", upsert: false }
    const [originalRes, thumbRes] = await Promise.all([
      bucket.upload(`${storagePath}/original.jpg`, processed.original, options),
      bucket.upload(`${storagePath}/thumb.jpg`, processed.thumb, options),
    ])
    if (originalRes.error) throw new Error(`Snap original upload failed: ${originalRes.error.message}`)
    if (thumbRes.error) throw new Error(`Snap thumb upload failed: ${thumbRes.error.message}`)
    return { id: snapId, storage_path: storagePath, sort_order: 0 }
  }

  // ---------------------------------------------------------------------------
  // Connections (M49) — definer-RPC-only, no direct table access: every write
  // is multi-table validated logic (accept touches invites, blocks and
  // connections), and cross-user reads are display projections, never a
  // profiles row. Deliberately outside the eager store (their own surface,
  // refreshed on screen open — no realtime). Error slugs (invite_not_found,
  // invite_expired, cannot_accept_own_invite, not_found) survive as substrings
  // of the thrown message for callers to `.includes`-match. Zero karma (D9).
  // ---------------------------------------------------------------------------

  async listConnections(): Promise<Connection[]> {
    // The RPC returns a jsonb array ordered connected_at desc (newest first).
    const { data, error } = await this.supabase.rpc("get_connections")
    if (error) throw new Error(`get_connections failed: ${error.message}`)
    const rows = Array.isArray(data) ? (data as Record<string, unknown>[]) : []
    return rows.map((row) => ({
      id: row.connection_id as string,
      connectedAt: row.connected_at as string,
      peer: toConnectionPeer(row.peer),
    }))
  }

  async listConnectionSharedPebbles(
    connectionId: string,
  ): Promise<SharedConnectionPebble[] | null> {
    const { data: row, error: rowError } = await this.supabase
      .from("connections")
      .select("id, user_a, user_b")
      .eq("id", connectionId)
      .maybeSingle()
    if (rowError) throw new Error(`connection lookup failed: ${rowError.message}`)
    if (!row) return null

    const peerId = peerIdOf(row as { user_a: string; user_b: string }, this.userId)
    if (!peerId) return null

    // The widened pebbles_select (M51) trims this to 'private' + 'public'
    // rows; emotions is reference data (RLS `using (true)`), so that embed
    // cannot blank rows. name and happened_at live on `moments` (#1046);
    // moments_select opens a connection's moment exactly when one of its
    // pebbles is readable, and `!inner` drops a row whose moment is not.
    // v_moments_full cannot serve this: it nests only the CALLER's pebble.
    // `glyph` is the pebbles.glyph computed field (#1050): the derived glyph,
    // gated like resolve_pebble_glyph, in this one request.
    const { data, error } = await this.supabase
      .from("pebbles")
      .select(
        "id, visibility, intensity, positiveness, emotion:emotions(id, slug, name, color), moment:moments!inner(name, happened_at), glyph",
      )
      .eq("user_id", peerId)
    if (error) throw new Error(`shared pebbles fetch failed: ${error.message}`)

    const rows: Record<string, unknown>[] = data ?? []
    return rows
      .flatMap((row) => {
        const shared = sharedConnectionPebbleFromRow(row)
        if (!shared) {
          console.warn("[supabase-provider] dropped shared pebble with a broken embed", row.id)
          return []
        }
        return [shared]
      })
      .sort(compareSharedNewestFirst)
  }

  async createConnectionInvite(rotate = false): Promise<ConnectionInvite> {
    // Returns the live invite if one exists; `p_rotate` revokes it and mints
    // fresh (the entire revocation surface — there is no separate revoke RPC).
    const { data, error } = await this.supabase.rpc("create_connection_invite", {
      p_rotate: rotate,
    })
    if (error) throw new Error(`create_connection_invite failed: ${error.message}`)
    const r = data as { token: string; expires_at: string; created_at: string }
    return { token: r.token, expiresAt: r.expires_at, createdAt: r.created_at }
  }

  async acceptConnectionInvite(token: string): Promise<AcceptConnectionInviteResult> {
    const { data, error } = await this.supabase.rpc("accept_connection_invite", {
      p_token: token,
    })
    if (error) throw new Error(`accept_connection_invite failed: ${error.message}`)
    const r = data as Record<string, unknown>
    return {
      connectionId: r.connection_id as string,
      alreadyConnected: Boolean(r.already_connected),
      connectedAt: r.connected_at as string,
      peer: toConnectionPeer(r.peer),
    }
  }

  async removeConnection(id: string, block = false): Promise<void> {
    const { error } = await this.supabase.rpc("remove_connection", {
      p_connection_id: id,
      p_block: block,
    })
    if (error) throw new Error(`remove_connection failed: ${error.message}`)
  }

  /**
   * Invoke the compose-pebble edge function (the old composer's create, until
   * Capture replaces it) and return the resulting pebble id. A failure is a failure: since the moments
   * split (#1046) nothing runs after the write that could fail on its own, so
   * there is no soft success. A refusal throws a `ComposeError` carrying the
   * server's slug (`pebble_needs_domain`, …) for the composer to explain.
   */
  private async invokeCompose(
    name: "compose-pebble",
    body: Record<string, unknown>,
  ): Promise<string> {
    const { data, error } = await this.supabase.functions.invoke<{ pebble_id?: string }>(name, {
      body,
    })

    if (error) {
      const slug = await readEdgeErrorBody((error as { context?: unknown }).context)
      console.error(`[${name}] edge function failed`, slug ?? error)
      throw new ComposeError(name, slug ?? error.message ?? "unknown")
    }

    const pebbleId = data?.pebble_id
    if (!pebbleId) {
      console.error(`[${name}] edge function returned no pebble_id`, data)
      throw new Error(`${name} returned no pebble_id`)
    }
    return pebbleId
  }

  // ---------------------------------------------------------------------------
  // Moment writes (#1050) — save_moment / complete_pebble / delete_moment /
  // delete_pebble, no edge function. Reload is not the write: see reloadAfter.
  // ---------------------------------------------------------------------------

  async saveMoment(payload: SaveMomentPayload): Promise<string> {
    const { data, error } = await this.supabase.rpc("save_moment", { payload })
    if (error || typeof data !== "string") {
      console.error("[moments] save_moment failed", error ?? data)
      throw new MomentWriteError("save_moment", error)
    }
    await this.reloadAfter("save_moment")
    return data
  }

  async completePebble(payload: CompletePebblePayload): Promise<string> {
    const { data, error } = await this.supabase.rpc("complete_pebble", { payload })
    if (error || typeof data !== "string") {
      console.error("[moments] complete_pebble failed", error ?? data)
      throw new MomentWriteError("complete_pebble", error)
    }
    await this.reloadAfter("complete_pebble")
    return data
  }

  async pebbleKarma(pebbleId: string): Promise<number | null> {
    const { data, error } = await this.supabase
      .from("karma_events")
      .select("delta")
      .eq("user_id", this.userId)
      .eq("ref_id", pebbleId)
    if (error) {
      console.warn("[moments] pebble karma read failed", error)
      return null
    }
    return (data ?? []).reduce((sum, r) => sum + (typeof r.delta === "number" ? r.delta : 0), 0)
  }

  async deleteMoment(momentId: string): Promise<void> {
    const { data, error } = await this.supabase.rpc("delete_moment", { p_moment_id: momentId })
    if (error) {
      console.error("[moments] delete_moment failed", error)
      throw new Error(`delete_moment failed: ${error.message}`)
    }
    const paths = deleteMomentPaths(data)
    if (paths.length > 0) {
      const { error: rmErr } = await this.supabase.storage
        .from(PEBBLE_MEDIA_BUCKET)
        .remove(paths.flatMap((p) => [`${p}/original.jpg`, `${p}/thumb.jpg`]))
      if (rmErr) console.warn("[moments] snap file cleanup failed (orphan sweep is the backstop)", rmErr)
    }
    await this.reloadAfter("delete_moment")
  }

  async removeSnapFiles(storagePaths: string[]): Promise<void> {
    if (storagePaths.length === 0) return
    const { error } = await this.supabase.storage
      .from(PEBBLE_MEDIA_BUCKET)
      .remove(storagePaths.flatMap((p) => [`${p}/original.jpg`, `${p}/thumb.jpg`]))
    if (error) console.warn("[moments] snap file cleanup failed (orphan sweep is the backstop)", error)
  }

  async deletePebble(pebbleId: string): Promise<void> {
    const result = await this.supabase.rpc("delete_pebble", { p_pebble_id: pebbleId })
    this.unwrap(result)
    await this.reloadAfter("delete_pebble")
  }

  /**
   * Reload after a write that already succeeded. A failure is logged and the
   * store stays stale; it never turns the write into an error, because the
   * caller would retry it (and a `save_moment` create is not idempotent).
   */
  private async reloadAfter(fn: string): Promise<void> {
    try {
      await this.loadFromSupabase()
    } catch (err) {
      console.warn(`[moments] reload after ${fn} failed`, err)
    }
  }

  // ---------------------------------------------------------------------------
  // Soul mutations
  // ---------------------------------------------------------------------------

  async createSoul(input: CreateSoulInput): Promise<Soul> {
    const result = await this.supabase
      .from("souls")
      .insert({
        user_id: this.userId,
        name: input.name,
        glyph_id: input.glyph_id ?? DEFAULT_GLYPH_ID,
      })
      .select()
      .single()
    const soul = this.unwrap(result) as Record<string, unknown>
    const created: Soul = {
      id: soul.id as string,
      name: soul.name as string,
      glyph_id: soul.glyph_id as string,
      created_at: soul.created_at as string,
      updated_at: soul.updated_at as string,
    }
    this.mutate({ ...this.store, souls: [...this.store.souls, created] })
    return created
  }

  async updateSoul(id: string, input: UpdateSoulInput): Promise<Soul> {
    const result = await this.supabase
      .from("souls")
      .update({
        ...(input.name !== undefined && { name: input.name }),
        ...(input.glyph_id !== undefined && { glyph_id: input.glyph_id }),
      })
      .eq("id", id)
      .select()
      .single()
    const soul = this.unwrap(result) as Record<string, unknown>
    const updated: Soul = {
      id: soul.id as string,
      name: soul.name as string,
      glyph_id: soul.glyph_id as string,
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
    const moments = this.store.moments.map((m) =>
      m.pebble && m.pebble.soul_ids.includes(id)
        ? { ...m, pebble: { ...m.pebble, soul_ids: m.pebble.soul_ids.filter((sid) => sid !== id) } }
        : m,
    )
    this.mutate({ ...this.store, souls, moments, pebbles: derivePebbles(moments) })
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

  private rowToMark(row: Record<string, unknown>): Mark {
    return {
      id: row.id as string,
      name: (row.name as string) ?? undefined,
      strokes: row.strokes as Mark["strokes"],
      viewBox: row.view_box as string,
      created_at: row.created_at as string,
      updated_at: row.updated_at as string,
    }
  }

  async createMark(input: CreateMarkInput): Promise<Mark> {
    const result = await this.supabase
      .from("glyphs")
      .insert({
        user_id: this.userId,
        name: input.name ?? null,
        strokes: input.strokes,
        view_box: input.viewBox,
      })
      .select()
      .single()
    const row = this.unwrap(result) as Record<string, unknown>
    const created: Mark = {
      id: row.id as string,
      name: (row.name as string) ?? undefined,
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
    if (input.strokes !== undefined) updates.strokes = input.strokes
    if (input.viewBox !== undefined) updates.view_box = input.viewBox
    const result = await this.supabase.from("glyphs").update(updates).eq("id", id).select().single()
    const row = this.unwrap(result) as Record<string, unknown>
    const updated: Mark = {
      id: row.id as string,
      name: (row.name as string) ?? undefined,
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

  // ---------------------------------------------------------------------------
  // Glyph marketplace (#496)
  // ---------------------------------------------------------------------------

  private rowToMarketGlyph(row: Record<string, unknown>): MarketGlyph {
    return {
      ...this.rowToMark(row),
      price: row.price as number,
      owned: Boolean(row.owned),
      favourited: Boolean(row.favourited),
    }
  }

  async listMarketGlyphs(): Promise<MarketGlyph[]> {
    const { data, error } = await this.supabase.from("v_glyph_market").select("*")
    if (error) throw new Error(`Failed to load market glyphs: ${error.message}`)
    // Hide the caller's own creations — they live under Mine, not the Market.
    return (data ?? [])
      .filter((row) => (row as Record<string, unknown>).user_id !== this.userId)
      .map((row) => this.rowToMarketGlyph(row as Record<string, unknown>))
  }

  async listFavouriteGlyphs(): Promise<MarketGlyph[]> {
    // Bought (owned) ∪ favourited, both drawn from approved listings. (A glyph
    // delisted after purchase would drop out — acceptable for V1; D doesn't exist yet.)
    const { data, error } = await this.supabase
      .from("v_glyph_market")
      .select("*")
      .or("owned.eq.true,favourited.eq.true")
    if (error) throw new Error(`Failed to load favourites: ${error.message}`)
    return (data ?? []).map((row) => this.rowToMarketGlyph(row as Record<string, unknown>))
  }

  async getMySubmissions(): Promise<GlyphSubmission[]> {
    const { data, error } = await this.supabase
      .from("glyph_submissions")
      .select("id, glyph_id, status, price, created_at, review_note")
      .eq("submitter_id", this.userId)
      .order("created_at", { ascending: false }) // newest first → page's .find picks the active row
    if (error) throw new Error(`Failed to load submissions: ${error.message}`)
    return (data ?? []).map((row) => {
      const r = row as Record<string, unknown>
      return {
        id: r.id as string,
        glyph_id: r.glyph_id as string,
        status: r.status as GlyphSubmission["status"],
        price: r.price as number,
        created_at: r.created_at as string,
        review_note: (r.review_note as string | null) ?? null,
      }
    })
  }

  async submitGlyph(glyphId: string): Promise<GlyphSubmission> {
    const { data, error } = await this.supabase.rpc("submit_glyph", { p_glyph_id: glyphId })
    if (error) throw new Error(error.message)
    const r = data as Record<string, unknown>
    return {
      id: r.id as string,
      glyph_id: r.glyph_id as string,
      status: r.status as GlyphSubmission["status"],
      price: r.price as number,
      created_at: r.created_at as string,
    }
  }

  async buyGlyph(glyphId: string): Promise<{ entitlementId: string; karma: number }> {
    const { data, error } = await this.supabase.rpc("buy_glyph", { p_glyph_id: glyphId })
    if (error) throw new Error(error.message)
    const r = data as { entitlement_id: string; balance: number }
    // Reload so karma and entitledMarks refresh together (the bought glyph
    // becomes usable in the picker).
    await this.loadFromSupabase()
    return { entitlementId: r.entitlement_id, karma: this.store.karma }
  }

  async setFavourite(glyphId: string, favourite: boolean): Promise<void> {
    if (favourite) {
      const { error } = await this.supabase
        .from("glyph_favourites")
        .upsert({ user_id: this.userId, glyph_id: glyphId }, { onConflict: "user_id,glyph_id" })
      if (error) throw new Error(error.message)
    } else {
      const { error } = await this.supabase
        .from("glyph_favourites")
        .delete()
        .eq("user_id", this.userId)
        .eq("glyph_id", glyphId)
      if (error) throw new Error(error.message)
    }
  }
}
