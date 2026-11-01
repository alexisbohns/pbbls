import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { decodeProfileEngagement } from "./profile-engagement"

// The real get_profile_engagement payload #1386's harness dumped, replayed
// verbatim (never copied): the glyph counts iOS and Android read too.
const webRoot = fileURLToPath(new URL("../..", import.meta.url))
const dump = path.join(webRoot, "../../packages/supabase/scripts/oracles/home/payloads/profile-engagement.json")
const served = (): unknown => JSON.parse(readFileSync(dump, "utf8"))

function withRow(edit: (row: Record<string, unknown>) => void): unknown {
  const rows = served()
  if (!Array.isArray(rows) || typeof rows[0] !== "object" || rows[0] === null) throw new Error("unexpected dump shape")
  edit(rows[0] as Record<string, unknown>)
  return rows
}

describe("decodeProfileEngagement", () => {
  it("reads the served dump: days, the 28-day window and both glyph counts", () => {
    const engagement = decodeProfileEngagement(served())
    expect(engagement.daysPracticed).toBe(1)
    expect(engagement.assiduity).toHaveLength(28)
    expect(engagement.assiduity[27]).toBe(true)
    expect(engagement.assiduity.filter(Boolean)).toHaveLength(1)
    expect(engagement.glyphsCarved).toBe(1)
    expect(engagement.glyphsOwned).toBe(0)
  })

  it("decodes a server from before #1386 (the keys absent) to null counts", () => {
    const engagement = decodeProfileEngagement(
      withRow((row) => {
        delete row.glyphs_carved
        delete row.glyphs_owned
      }),
    )
    expect(engagement.glyphsCarved).toBeNull()
    expect(engagement.glyphsOwned).toBeNull()
    expect(engagement.daysPracticed).toBe(1)
  })

  it("decodes explicit nulls, and a wrong type, to null counts", () => {
    const nulls = decodeProfileEngagement(
      withRow((row) => {
        row.glyphs_carved = null
        row.glyphs_owned = null
      }),
    )
    expect([nulls.glyphsCarved, nulls.glyphsOwned]).toEqual([null, null])
    const wrong = decodeProfileEngagement(
      withRow((row) => {
        row.glyphs_carved = "3"
        row.glyphs_owned = -1
      }),
    )
    expect([wrong.glyphsCarved, wrong.glyphsOwned]).toEqual([null, null])
  })

  it("never throws on an empty or odd payload", () => {
    for (const raw of [[], null, undefined, {}, "x"]) {
      expect(decodeProfileEngagement(raw)).toEqual({ daysPracticed: 0, assiduity: [], glyphsCarved: null, glyphsOwned: null })
    }
  })
})
