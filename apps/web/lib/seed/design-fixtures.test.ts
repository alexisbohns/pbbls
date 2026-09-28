import { describe, expect, it } from "vitest"
import {
  DESIGN_ASSIDUITY,
  DESIGN_PROFILE,
  designCollections,
  designPebbles,
  designStore,
} from "./design-fixtures"

const NOW = new Date("2026-09-28T12:00:00Z")

describe("design fixtures", () => {
  const store = designStore(NOW)
  const soulIds = store.souls.map((s) => s.id)
  const markIds = store.marks.map((m) => m.id)
  const pebbleIds = store.pebbles.map((p) => p.id)

  it("dates every pebble within the week before now, newest first", () => {
    const times = designPebbles(NOW).map((p) => Date.parse(p.happened_at))
    for (const t of times) {
      expect(t).toBeLessThan(NOW.getTime())
      expect(NOW.getTime() - t).toBeLessThan(7 * 24 * 3_600_000)
    }
    expect([...times].sort((a, b) => b - a)).toEqual(times)
  })

  it("only references souls and marks that are in the store", () => {
    for (const pebble of store.pebbles) {
      for (const id of pebble.soul_ids) expect(soulIds).toContain(id)
      if (pebble.mark_id) expect(markIds).toContain(pebble.mark_id)
    }
    expect(markIds).toContain(DESIGN_PROFILE.glyph_id)
  })

  it("fills collections with pebbles from the store, one per mode", () => {
    const collections = designCollections(store.pebbles)
    expect(collections.map((c) => c.mode)).toEqual(["stack", "pack", "track"])
    for (const c of collections) for (const id of c.pebble_ids) expect(pebbleIds).toContain(id)
  })

  it("has 28 days of assiduity", () => {
    expect(DESIGN_ASSIDUITY).toHaveLength(28)
  })
})
