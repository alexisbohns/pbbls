import { describe, expect, it } from "vitest"
import { pebbleFromViewRow, pebbleGlyphFromRow, pebbleNameFromRow } from "./pebble-row"

// Rows as PostgREST returns `v_pebbles_full` (20260729000000): every column the
// view selects, jsonb embeds as objects, timestamptz as Postgres prints it. The
// first is what an iOS or Android moment can look like through the compat view
// after the moments split: no name, no glyph, whole-second `happened_at` (the
// precision those clients emit). The second is a web-composed pebble with a
// glyph and a fractional-second `happened_at`.
const MOMENT_ROW = {
  id: "8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01",
  user_id: "11111111-2222-3333-4444-555555555555",
  name: null,
  description: null,
  happened_at: "2026-10-05T08:15:00+00:00",
  intensity: 3,
  positiveness: 1,
  visibility: "private",
  emotion_id: "c2a3f1d4-0000-4000-8000-000000000007",
  glyph_id: null,
  created_at: "2026-10-05T08:16:02.481203+00:00",
  updated_at: "2026-10-05T08:16:02.481203+00:00",
  emotion: { id: "c2a3f1d4-0000-4000-8000-000000000007", slug: "pride", name: "Pride", color: "#F97316" },
  glyph: null,
  cards: [],
  souls: [
    { id: "aaaaaaaa-0000-4000-8000-000000000001", name: "Nadia" },
  ],
  domains: [],
  snaps: [
    { id: "s2", storage_path: "11111111-2222-3333-4444-555555555555/s2", sort_order: 1 },
    { id: "s1", storage_path: "11111111-2222-3333-4444-555555555555/s1", sort_order: 0 },
  ],
  collections: [],
}

const PEBBLE_ROW = {
  ...MOMENT_ROW,
  id: "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b",
  name: "Flag march with Nadia",
  description: "Rain, then sun.",
  happened_at: "2026-10-06T17:42:09.123456+00:00",
  glyph_id: "bbbbbbbb-0000-4000-8000-000000000002",
  glyph: {
    id: "bbbbbbbb-0000-4000-8000-000000000002",
    name: "flag",
    strokes: [
      { d: "M40 160 L40 40 L150 70 L40 100", width: 6 },
      { d: "M60 150 Q100 130 140 150", width: 6 },
    ],
    view_box: "0 0 200 200",
  },
  snaps: [],
}

const NO_RENDER = { render_svg: null, render_version: null }

describe("pebbleFromViewRow", () => {
  it("maps a nameless, glyphless moment from another surface", () => {
    const signed = new Map([
      ["11111111-2222-3333-4444-555555555555/s1/original.jpg", "https://signed/s1"],
      ["11111111-2222-3333-4444-555555555555/s2/original.jpg", "https://signed/s2"],
    ])
    const pebble = pebbleFromViewRow(MOMENT_ROW, NO_RENDER, signed)
    expect(pebble.name).toBe("")
    expect(pebble.glyph).toBeNull()
    expect(pebble.mark_id).toBeUndefined()
    expect(pebble.description).toBeUndefined()
    expect(pebble.soul_ids).toEqual(["aaaaaaaa-0000-4000-8000-000000000001"])
    // Snaps in sort_order, and the instants signed in that order.
    expect(pebble.snaps.map((s) => s.id)).toEqual(["s1", "s2"])
    expect(pebble.instants).toEqual(["https://signed/s1", "https://signed/s2"])
    expect(new Date(pebble.happened_at).toISOString()).toBe("2026-10-05T08:15:00.000Z")
  })

  it("maps a web pebble's glyph strokes and keeps a fractional timestamp readable", () => {
    const pebble = pebbleFromViewRow(PEBBLE_ROW, NO_RENDER, new Map())
    expect(pebble.name).toBe("Flag march with Nadia")
    expect(pebble.glyph).toEqual({
      id: "bbbbbbbb-0000-4000-8000-000000000002",
      name: "flag",
      strokes: PEBBLE_ROW.glyph.strokes,
      viewBox: "0 0 200 200",
    })
    expect(pebble.mark_id).toBe("bbbbbbbb-0000-4000-8000-000000000002")
    expect(new Date(pebble.happened_at).toISOString()).toBe("2026-10-06T17:42:09.123Z")
  })

  it("drops a snap whose URL failed to sign rather than leaving a hole", () => {
    const signed = new Map([["11111111-2222-3333-4444-555555555555/s2/original.jpg", "https://signed/s2"]])
    expect(pebbleFromViewRow(MOMENT_ROW, NO_RENDER, signed).instants).toEqual(["https://signed/s2"])
  })

  it("carries the render columns it is handed", () => {
    const pebble = pebbleFromViewRow(PEBBLE_ROW, { render_svg: "<svg/>", render_version: "v3" }, new Map())
    expect(pebble).toMatchObject({ render_svg: "<svg/>", render_version: "v3" })
  })
})

describe("pebbleGlyphFromRow", () => {
  it("treats a malformed embed as no glyph", () => {
    expect(pebbleGlyphFromRow(undefined)).toBeNull()
    expect(pebbleGlyphFromRow({ id: "g", view_box: "0 0 200 200" })).toBeNull()
    expect(pebbleGlyphFromRow({ id: "g", view_box: "0 0 200 200", strokes: [{ d: 1 }] })).toBeNull()
  })

  it("accepts a glyph with no name", () => {
    expect(pebbleGlyphFromRow({ id: "g", name: null, view_box: "0 0 200 200", strokes: [] })).toEqual({
      id: "g",
      name: undefined,
      strokes: [],
      viewBox: "0 0 200 200",
    })
  })
})

describe("pebbleNameFromRow", () => {
  it("maps anything but a string to an empty name", () => {
    expect(pebbleNameFromRow(null)).toBe("")
    expect(pebbleNameFromRow(undefined)).toBe("")
    expect(pebbleNameFromRow("Swim")).toBe("Swim")
  })
})
