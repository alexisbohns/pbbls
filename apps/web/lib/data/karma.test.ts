// Pins the TS mirror to the SQL `compute_karma_delta` formula (packages/supabase/
// supabase/migrations/20260411000005_security_hardening.sql) so the two can't
// silently drift again — issue #621.

import { describe, expect, it } from "vitest"

import { computeKarmaDelta } from "./karma"
import type { Pebble } from "@/lib/types"

function makePebble(overrides: Partial<Pebble> = {}): Pebble {
  return {
    id: "pebble-1",
    name: "Test pebble",
    description: undefined,
    happened_at: "2026-07-29T00:00:00Z",
    intensity: 2,
    positiveness: 0,
    visibility: "private",
    emotion_id: "emotion-1",
    soul_ids: [],
    domain_ids: [],
    collection_ids: [],
    mark_id: undefined,
    instants: [],
    snaps: [],
    cards: [],
    render_svg: null,
    render_version: null,
    created_at: "2026-07-29T00:00:00Z",
    updated_at: "2026-07-29T00:00:00Z",
    ...overrides,
  }
}

function cards(count: number): Pebble["cards"] {
  return Array.from({ length: count }, (_, i) => ({ species_id: `card-${i}`, value: `value-${i}` }))
}

describe("computeKarmaDelta", () => {
  it("returns the base karma for a bare pebble", () => {
    expect(computeKarmaDelta(makePebble())).toBe(1)
  })

  it("adds 1 for a non-empty description", () => {
    expect(computeKarmaDelta(makePebble({ description: "a memory" }))).toBe(2)
  })

  it("ignores a whitespace-only description", () => {
    expect(computeKarmaDelta(makePebble({ description: "   " }))).toBe(1)
  })

  it("adds 1 per non-empty card up to 4", () => {
    expect(computeKarmaDelta(makePebble({ cards: cards(1) }))).toBe(2)
    expect(computeKarmaDelta(makePebble({ cards: cards(4) }))).toBe(5)
  })

  it("caps the card bonus at 4 even with 5+ cards", () => {
    expect(computeKarmaDelta(makePebble({ cards: cards(5) }))).toBe(5)
    expect(computeKarmaDelta(makePebble({ cards: cards(8) }))).toBe(5)
  })

  it("ignores whitespace-only card values when counting", () => {
    expect(computeKarmaDelta(makePebble({ cards: [{ species_id: "c", value: "   " }] }))).toBe(1)
  })

  it("adds 1 for at least one soul, domain, glyph, or snap", () => {
    expect(computeKarmaDelta(makePebble({ soul_ids: ["s1"] }))).toBe(2)
    expect(computeKarmaDelta(makePebble({ domain_ids: ["d1"] }))).toBe(2)
    expect(computeKarmaDelta(makePebble({ mark_id: "glyph-1" }))).toBe(2)
    expect(computeKarmaDelta(makePebble({ snaps: [{ id: "snap-1", storage_path: "u/snap-1", sort_order: 0 }] }))).toBe(
      2,
    )
  })

  it("caps the total delta at 10 for maximal enrichment (6 cards + everything)", () => {
    const pebble = makePebble({
      description: "a memory",
      cards: cards(6),
      soul_ids: ["s1"],
      domain_ids: ["d1"],
      mark_id: "glyph-1",
      snaps: [{ id: "snap-1", storage_path: "u/snap-1", sort_order: 0 }],
    })
    // Uncapped: 1 base + 1 description + 6 cards + 1 soul + 1 domain + 1 glyph + 1 snap = 12
    expect(computeKarmaDelta(pebble)).toBe(10)
  })
})
