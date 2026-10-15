import { describe, expect, it } from "vitest"
import type { Moment, MomentPebble } from "@/lib/types"
import { momentsInCollection, momentsWithSoul, soulPebbleCounts } from "./moment-lists"

function pebble(id: string, overrides: Partial<MomentPebble> = {}): MomentPebble {
  return {
    id,
    intensity: 2,
    positiveness: 0,
    visibility: "private",
    emotion_id: "emotion-1",
    soul_ids: [],
    domain_ids: [],
    collection_ids: [],
    cards: [],
    glyph: null,
    created_at: "2026-10-01T09:00:00Z",
    updated_at: "2026-10-01T09:00:00Z",
    ...overrides,
  }
}

function moment(id: string, happenedAt: string, p: MomentPebble | null): Moment {
  return {
    id,
    name: id,
    description: null,
    happened_at: happenedAt,
    snaps: [],
    instants: [],
    cover: null,
    created_at: happenedAt,
    updated_at: happenedAt,
    pebble: p,
  }
}

// Deliberately not in date order: the lists sort themselves.
const MOMENTS: Moment[] = [
  moment("m-old", "2026-10-01T08:00:00Z", pebble("p-old", { soul_ids: ["soul-a"] })),
  moment("m-bare", "2026-10-05T08:00:00Z", null),
  moment("m-new", "2026-10-09T08:00:00.123456Z", pebble("p-new", { soul_ids: ["soul-a", "soul-b"] })),
  moment("m-mid", "2026-10-04T08:00:00Z", pebble("p-mid", { soul_ids: ["soul-b"] })),
]

describe("momentsWithSoul", () => {
  it("keeps the completed moments naming the soul, newest happened_at first", () => {
    expect(momentsWithSoul(MOMENTS, "soul-a").map((m) => m.id)).toEqual(["m-new", "m-old"])
    expect(momentsWithSoul(MOMENTS, "soul-b").map((m) => m.id)).toEqual(["m-new", "m-mid"])
  })

  it("is empty for a soul nobody names", () => {
    expect(momentsWithSoul(MOMENTS, "soul-z")).toEqual([])
  })
})

describe("momentsInCollection", () => {
  it("matches on the PEBBLE id, newest first", () => {
    const rows = momentsInCollection(MOMENTS, { pebble_ids: ["p-old", "p-new", "m-mid"] })
    expect(rows.map((m) => m.id)).toEqual(["m-new", "m-old"])
  })

  it("skips ids no loaded moment carries", () => {
    expect(momentsInCollection(MOMENTS, { pebble_ids: ["p-gone"] })).toEqual([])
  })
})

describe("soulPebbleCounts", () => {
  it("counts completed moments per soul, bare moments for none", () => {
    expect(Object.fromEntries(soulPebbleCounts(MOMENTS))).toEqual({ "soul-a": 2, "soul-b": 2 })
  })
})
