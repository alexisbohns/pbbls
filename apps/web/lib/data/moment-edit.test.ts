import { describe, expect, it } from "vitest"
import type { Moment, MomentPebble, PebbleSnap } from "@/lib/types"
import { completePebblePayload } from "./moment-payloads"
import {
  canSaveEnrich,
  detachedPaths,
  enrichDraftFrom,
  isEnrichDraftDirty,
  isMomentEditDirty,
  momentEditDraftFrom,
  momentEditHasContent,
  singleSelect,
  snapsAfterEdit,
} from "./moment-edit"

const USER = "907e7d76-00d3-4e53-82a3-4849bf0a9614"
const A: PebbleSnap = { id: "d49168b5-b97a-4888-a787-43ec65bb416a", storage_path: `${USER}/d49168b5-b97a-4888-a787-43ec65bb416a`, sort_order: 0 }
const B: PebbleSnap = { id: "0b7c2f8e-3c1d-4a8e-9a55-2f1e0c9d7b44", storage_path: `${USER}/0b7c2f8e-3c1d-4a8e-9a55-2f1e0c9d7b44`, sort_order: 0 }

const DOMAIN_1 = "5b0c3d3e-1111-4c6a-9a3a-0d6c2f1e2a10"
const DOMAIN_2 = "5b0c3d3e-2222-4c6a-9a3a-0d6c2f1e2a10"
const COLL_1 = "c0000000-0000-4000-8000-000000000001"
const COLL_2 = "c0000000-0000-4000-8000-000000000002"

function moment(overrides: Partial<Moment> = {}): Moment {
  return {
    id: "daa2a992-16d1-459a-90d0-e3d42fb863a9",
    name: null,
    description: null,
    happened_at: "2026-10-12T08:30:00Z",
    snaps: [],
    instants: [],
    cover: null,
    created_at: "2026-10-12T08:30:00Z",
    updated_at: "2026-10-12T08:30:00Z",
    pebble: null,
    ...overrides,
  }
}

const LEGACY_PEBBLE: MomentPebble = {
  id: "e1f2a3b4-0000-4000-8000-000000000000",
  intensity: 3,
  positiveness: -1,
  visibility: "private",
  emotion_id: "332cabe9-c76d-d737-bba6-f2d7024de808",
  soul_ids: [],
  domain_ids: [DOMAIN_1, DOMAIN_2],
  collection_ids: [COLL_1, COLL_2],
  cards: [],
  glyph: null,
  created_at: "2026-10-12T08:30:00Z",
  updated_at: "2026-10-12T08:30:00Z",
}

describe("detachedPaths", () => {
  it("returns a removed photo's directory", () => {
    expect(detachedPaths([A], [])).toEqual([A.storage_path])
  })

  it("returns nothing when the photo stays", () => {
    expect(detachedPaths([A], [A])).toEqual([])
  })

  it("returns the replaced photo's directory", () => {
    expect(detachedPaths([A], [B])).toEqual([A.storage_path])
  })
})

describe("snapsAfterEdit", () => {
  it("keeps the moment's whole set when the photo is untouched", () => {
    expect(snapsAfterEdit([A, B], A)).toEqual([A, B])
  })

  it("sends [] once the photo is removed", () => {
    expect(snapsAfterEdit([A], null)).toEqual([])
  })

  it("sends only the new upload when the photo is replaced", () => {
    expect(snapsAfterEdit([A], B)).toEqual([B])
  })
})

describe("isMomentEditDirty", () => {
  it("ignores a whitespace-only name on an untitled moment", () => {
    const original = momentEditDraftFrom(moment())
    expect(isMomentEditDirty(original, { ...original, name: "   " })).toBe(false)
  })

  it("sees a rename, a cleared description, a new date and a removed photo", () => {
    const original = momentEditDraftFrom(moment({ name: "Coffee", description: "Hot", snaps: [A] }))
    expect(isMomentEditDirty(original, { ...original, name: "Tea" })).toBe(true)
    expect(isMomentEditDirty(original, { ...original, description: "" })).toBe(true)
    expect(isMomentEditDirty(original, { ...original, happenedAt: "2026-10-12T09:30:00Z" })).toBe(true)
    expect(isMomentEditDirty(original, { ...original, snap: null })).toBe(true)
  })

  it("reads the same instant in another spelling as no change", () => {
    const original = momentEditDraftFrom(moment())
    expect(isMomentEditDirty(original, { ...original, happenedAt: "2026-10-12T08:30:00.000Z" })).toBe(false)
  })
})

describe("momentEditHasContent", () => {
  it("needs a name or a photo (R6)", () => {
    const draft = momentEditDraftFrom(moment())
    expect(momentEditHasContent(draft)).toBe(false)
    expect(momentEditHasContent({ ...draft, name: "  " })).toBe(false)
    expect(momentEditHasContent({ ...draft, name: "Coffee" })).toBe(true)
    expect(momentEditHasContent({ ...draft, snap: A })).toBe(true)
  })
})

describe("singleSelect", () => {
  it("collapses a legacy multi-set to the picked id", () => {
    expect(singleSelect([DOMAIN_1, DOMAIN_2], DOMAIN_1, false)).toEqual([DOMAIN_1])
    expect(singleSelect([COLL_1, COLL_2], COLL_2, true)).toEqual([COLL_2])
  })

  it("clears the one selected collection, never the one domain", () => {
    expect(singleSelect([COLL_1], COLL_1, true)).toEqual([])
    expect(singleSelect([DOMAIN_1], DOMAIN_1, false)).toEqual([DOMAIN_1])
  })

  it("replaces a different selection", () => {
    expect(singleSelect([DOMAIN_1], DOMAIN_2, false)).toEqual([DOMAIN_2])
    expect(singleSelect([], COLL_1, true)).toEqual([COLL_1])
  })
})

describe("Edit pebble (PD-W11)", () => {
  it("seeds a confirmed valence and the whole legacy sets", () => {
    const draft = enrichDraftFrom(LEGACY_PEBBLE)
    expect(draft.valenceConfirmed).toBe(true)
    expect(draft.domainIds).toEqual([DOMAIN_1, DOMAIN_2])
    expect(draft.collectionIds).toEqual([COLL_1, COLL_2])
  })

  it("sends an untouched legacy multi-set back verbatim", () => {
    const original = enrichDraftFrom(LEGACY_PEBBLE)
    const draft = { ...original, visibility: "public" as const }
    expect(canSaveEnrich(original, draft)).toBe(true)
    const payload = completePebblePayload("DAA2A992-16D1-459A-90D0-E3D42FB863A9", draft)
    expect(payload?.domain_ids).toEqual([DOMAIN_1, DOMAIN_2])
    expect(payload?.collection_ids).toEqual([COLL_1, COLL_2])
  })

  it("collapses a touched field to one", () => {
    const original = enrichDraftFrom(LEGACY_PEBBLE)
    const draft = { ...original, domainIds: singleSelect(original.domainIds, DOMAIN_2, false) }
    expect(completePebblePayload("daa2a992-16d1-459a-90d0-e3d42fb863a9", draft)?.domain_ids).toEqual([DOMAIN_2])
  })

  it("is not dirty when untouched, nor when the souls only reorder", () => {
    const original = enrichDraftFrom({ ...LEGACY_PEBBLE, soul_ids: ["s1", "s2"] })
    expect(isEnrichDraftDirty(original, { ...original })).toBe(false)
    expect(isEnrichDraftDirty(original, { ...original, soulIds: ["s2", "s1"] })).toBe(false)
    expect(canSaveEnrich(original, original)).toBe(false)
  })

  it("will not save without a domain", () => {
    const original = enrichDraftFrom(LEGACY_PEBBLE)
    expect(canSaveEnrich(original, { ...original, domainIds: [] })).toBe(false)
  })
})
