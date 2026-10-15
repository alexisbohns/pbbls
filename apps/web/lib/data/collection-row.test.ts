import { describe, expect, it } from "vitest"
import { collectionFromRow, collectionInsertRow, collectionUpdateRow } from "./collection-row"

const GLYPH = "f738661b-6f49-448e-916c-443fed751475"

describe("collectionFromRow", () => {
  it("reads the glyph and the mode", () => {
    const c = collectionFromRow(
      {
        id: "0da36370-3a59-44c4-94e6-a49830a73339",
        user_id: "907e7d76-00d3-4e53-82a3-4849bf0a9614",
        name: "Morning runs",
        mode: "track",
        glyph_id: GLYPH,
        created_at: "2026-10-12T08:30:00+00:00",
        updated_at: "2026-10-12T08:30:00.123456+00:00",
      },
      ["p-1"],
    )
    expect(c).toEqual({
      id: "0da36370-3a59-44c4-94e6-a49830a73339",
      name: "Morning runs",
      mode: "track",
      glyph_id: GLYPH,
      pebble_ids: ["p-1"],
      created_at: "2026-10-12T08:30:00+00:00",
      updated_at: "2026-10-12T08:30:00.123456+00:00",
    })
  })

  it("reads an explicit null glyph and mode as none", () => {
    const c = collectionFromRow({ id: "c", name: "x", mode: null, glyph_id: null }, [])
    expect(c.glyph_id).toBeNull()
    expect(c.mode).toBeUndefined()
  })

  it("treats a missing glyph column as none", () => {
    expect(collectionFromRow({ id: "c", name: "x" }, []).glyph_id).toBeNull()
  })
})

describe("collectionInsertRow", () => {
  it("writes name, mode and glyph in one row", () => {
    expect(collectionInsertRow("u", { name: "Runs", mode: "track", glyph_id: GLYPH, pebble_ids: [] })).toEqual({
      user_id: "u",
      name: "Runs",
      mode: "track",
      glyph_id: GLYPH,
    })
  })

  it("sends an explicit null for no mode and no glyph", () => {
    expect(collectionInsertRow("u", { name: "Runs", glyph_id: null, pebble_ids: [] })).toEqual({
      user_id: "u",
      name: "Runs",
      mode: null,
      glyph_id: null,
    })
  })
})

describe("collectionUpdateRow", () => {
  it("puts the glyph in the same update as name and mode", () => {
    expect(collectionUpdateRow({ name: "Runs", mode: "pack", glyph_id: GLYPH })).toEqual({
      name: "Runs",
      mode: "pack",
      glyph_id: GLYPH,
    })
  })

  it("clears the glyph with null", () => {
    expect(collectionUpdateRow({ glyph_id: null })).toEqual({ glyph_id: null })
  })

  it("is null for a pebble-only change", () => {
    expect(collectionUpdateRow({ pebble_ids: ["p-1"] })).toBeNull()
  })
})
