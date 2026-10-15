// Pins the TS mirror to the SQL `compute_karma_delta` formula (packages/supabase/
// supabase/migrations/20260411000005_security_hardening.sql) so the two can't
// silently drift again — issue #621.

import { describe, expect, it } from "vitest"

import { computeKarmaDelta } from "./karma"
import type { CompletedMoment, Moment, MomentPebble } from "@/lib/types"

function makeMoment(
  moment: Partial<Omit<Moment, "pebble">> = {},
  pebble: Partial<MomentPebble> = {},
): CompletedMoment {
  return {
    id: "moment-1",
    name: "Test pebble",
    description: null,
    happened_at: "2026-07-29T00:00:00Z",
    snaps: [],
    instants: [],
    cover: null,
    created_at: "2026-07-29T00:00:00Z",
    updated_at: "2026-07-29T00:00:00Z",
    ...moment,
    pebble: {
      id: "pebble-1",
      intensity: 2,
      positiveness: 0,
      visibility: "private",
      emotion_id: "emotion-1",
      soul_ids: [],
      domain_ids: [],
      collection_ids: [],
      cards: [],
      glyph: null,
      created_at: "2026-07-29T00:00:00Z",
      updated_at: "2026-07-29T00:00:00Z",
      ...pebble,
    },
  }
}

function cards(count: number): MomentPebble["cards"] {
  return Array.from({ length: count }, (_, i) => ({ species_id: `card-${i}`, value: `value-${i}` }))
}

describe("computeKarmaDelta", () => {
  it("returns the base karma for a bare pebble", () => {
    expect(computeKarmaDelta(makeMoment())).toBe(1)
  })

  it("adds 1 for a non-empty description", () => {
    expect(computeKarmaDelta(makeMoment({ description: "a memory" }))).toBe(2)
  })

  it("ignores a whitespace-only description", () => {
    expect(computeKarmaDelta(makeMoment({ description: "   " }))).toBe(1)
  })

  it("adds 1 per non-empty card up to 4", () => {
    expect(computeKarmaDelta(makeMoment({}, { cards: cards(1) }))).toBe(2)
    expect(computeKarmaDelta(makeMoment({}, { cards: cards(4) }))).toBe(5)
  })

  it("caps the card bonus at 4 even with 5+ cards", () => {
    expect(computeKarmaDelta(makeMoment({}, { cards: cards(5) }))).toBe(5)
    expect(computeKarmaDelta(makeMoment({}, { cards: cards(8) }))).toBe(5)
  })

  it("ignores whitespace-only card values when counting", () => {
    expect(computeKarmaDelta(makeMoment({}, { cards: [{ species_id: "c", value: "   " }] }))).toBe(1)
  })

  it("adds 1 for at least one soul, domain, or snap", () => {
    expect(computeKarmaDelta(makeMoment({}, { soul_ids: ["s1"] }))).toBe(2)
    expect(computeKarmaDelta(makeMoment({}, { domain_ids: ["d1"] }))).toBe(2)
    expect(computeKarmaDelta(makeMoment({ snaps: [{ id: "snap-1", storage_path: "u/snap-1", sort_order: 0 }] }))).toBe(
      2,
    )
  })

  it("sums every bonus for maximal enrichment (6 cards + everything)", () => {
    const pebble = makeMoment(
      { description: "a memory", snaps: [{ id: "snap-1", storage_path: "u/snap-1", sort_order: 0 }] },
      { cards: cards(6), soul_ids: ["s1"], domain_ids: ["d1"] },
    )
    // 1 base + 1 description + 4 cards (capped) + 1 soul + 1 domain + 1 snap = 9.
    // Without the glyph bonus the 10 cap is no longer reachable.
    expect(computeKarmaDelta(pebble)).toBe(9)
  })

  it("gives no glyph bonus: the glyph is derived, not chosen (#1046)", () => {
    expect(computeKarmaDelta(makeMoment({}, { glyph: { id: "glyph-1", strokes: [], viewBox: "0 0 200 200" } }))).toBe(1)
  })
})
