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
    // The render columns (render_svg / render_version) are
    // not exposed by `v_pebbles_full`, so we fetch them in parallel from the
    // base `pebbles` table and merge them in by id. This mirrors the iOS read
    // pattern (it queries `pebbles` directly with explicit columns) and avoids
    // having to extend the view.
    const [
      pebblesRes,
      pebblesRenderRes,
      soulsRes,
      collectionsRes,
      collectionPebblesRes,
      glyphsRes,
      karmaRes,
      bounceRes,
    ] = await Promise.all([
      this.supabase.from("v_pebbles_full").select("*").eq("user_id", this.userId),
      this.supabase
        .from("pebbles")
        .select("id, render_svg, render_version")
        .eq("user_id", this.userId),
      this.supabase.from("souls").select("*").eq("user_id", this.userId),
      this.supabase.from("collections").select("*").eq("user_id", this.userId),
      this.supabase.from("collection_pebbles").select("*, collections!inner(user_id)").eq("collections.user_id", this.userId),
      this.supabase.from("glyphs").select("*").eq("user_id", this.userId),
      this.supabase.from("v_karma_summary").select("*").eq("user_id", this.userId).maybeSingle(),
      this.supabase.from("v_bounce").select("*").eq("user_id", this.userId).maybeSingle(),
    ])

    if (pebblesRes.error) throw new Error(`Failed to load pebbles: ${pebblesRes.error.message}`)
    if (pebblesRenderRes.error) throw new Error(`Failed to load pebble renders: ${pebblesRenderRes.error.message}`)
    if (soulsRes.error) throw new Error(`Failed to load souls: ${soulsRes.error.message}`)
    if (collectionsRes.error) throw new Error(`Failed to load collections: ${collectionsRes.error.message}`)
    if (glyphsRes.error) throw new Error(`Failed to load glyphs: ${glyphsRes.error.message}`)

    const renderById = new Map<
      string,
      { render_svg: string | null; render_version: string | null }
    >()
    for (const row of pebblesRenderRes.data ?? []) {
      const r = row as Record<string, unknown>
      renderById.set(r.id as string, {
        render_svg: (r.render_svg as string | null) ?? null,
        render_version: (r.render_version as string | null) ?? null,
      })
    }

    const pebbles: Pebble[] = (pebblesRes.data ?? []).map((row: Record<string, unknown>) => {
      const id = row.id as string
      const render = renderById.get(id) ?? {
        render_svg: null,
        render_version: null,
      }
      return {
        id,
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
        render_svg: render.render_svg,
        render_version: render.render_version,
        created_at: row.created_at as string,
        updated_at: row.updated_at as string,
      }
    })

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
  // Read helpers
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

  async updatePebble(id: string, input: UpdatePebbleInput): Promise<Pebble> {
    const payload = {
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
    }
    await this.invokeCompose("compose-pebble-update", { pebble_id: id, payload })
    await this.loadFromSupabase()
    const updated = this.store.pebbles.find((p) => p.id === id)
    if (!updated) throw new Error(`Pebble not found after update: ${id}`)
    return updated
  }

  /**
   * Invoke the compose-pebble or compose-pebble-update edge function and
   * return the resulting pebble id. Mirrors iOS soft-success handling
   * (`apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift:139` and
   * `EditPebbleSheet.swift:180`): if the function returns 5xx but the body
   * still carries a `pebble_id`, the row was inserted/updated successfully
   * and only the render write-back failed — we keep going and let the next
   * `loadFromSupabase` reflect the missing render.
   */
  private async invokeCompose(
    name: "compose-pebble" | "compose-pebble-update",
    body: Record<string, unknown>,
  ): Promise<string> {
    const { data, error } = await this.supabase.functions.invoke<{
      pebble_id?: string
      error?: string
    }>(name, { body })

    const pebbleId = data?.pebble_id

    if (error) {
      if (pebbleId) {
        console.warn(
          `[${name}] edge function returned an error but pebble_id is set — soft-success`,
          { error, pebbleId },
        )
        return pebbleId
      }
      console.error(`[${name}] edge function failed`, error)
      throw new Error(error.message ?? `${name} failed`)
    }

    if (!pebbleId) {
      console.error(`[${name}] edge function returned no pebble_id`, data)
      throw new Error(`${name} returned no pebble_id`)
    }
    return pebbleId
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
