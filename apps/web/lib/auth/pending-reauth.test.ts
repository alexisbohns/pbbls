import { describe, it, expect } from "vitest"
import { parsePendingReauth, serializePendingReauth, type PendingReauth } from "./pending-reauth"

const NOW = Date.parse("2026-10-06T12:00:00Z")

const save: PendingReauth = {
  purpose: "save",
  userId: "u-1",
  savedAt: NOW - 60_000,
  form: { name: "Ada", glyphId: { unchanged: false, value: null }, handle: "ada", isPublic: true },
}

describe("parsePendingReauth", () => {
  it("round-trips a save and a delete", () => {
    expect(parsePendingReauth(serializePendingReauth(save), NOW)).toEqual(save)
    const del: PendingReauth = { purpose: "delete", userId: "u-1", savedAt: NOW }
    expect(parsePendingReauth(serializePendingReauth(del), NOW)).toEqual(del)
  })

  it("keeps an unchanged glyph distinct from a cleared one", () => {
    const unchanged: PendingReauth = { ...save, form: { ...save.form, glyphId: { unchanged: true } } }
    expect(parsePendingReauth(serializePendingReauth(unchanged), NOW)).toEqual(unchanged)
  })

  it("drops a stash older than fifteen minutes or from the future", () => {
    expect(parsePendingReauth(serializePendingReauth({ ...save, savedAt: NOW - 16 * 60_000 }), NOW)).toBeNull()
    expect(parsePendingReauth(serializePendingReauth({ ...save, savedAt: NOW + 1 }), NOW)).toBeNull()
  })

  it("drops anything malformed", () => {
    for (const raw of [
      null,
      "",
      "not json",
      "null",
      "[]",
      JSON.stringify({ purpose: "delete", savedAt: NOW }),
      JSON.stringify({ purpose: "delete", userId: "", savedAt: NOW }),
      JSON.stringify({ purpose: "purge", userId: "u-1", savedAt: NOW }),
      JSON.stringify({ purpose: "save", userId: "u-1", savedAt: NOW }),
      JSON.stringify({ ...save, form: { ...save.form, isPublic: "yes" } }),
      JSON.stringify({ ...save, form: { ...save.form, glyphId: "g-1" } }),
      JSON.stringify({ ...save, form: { ...save.form, name: 3 } }),
    ]) {
      expect(parsePendingReauth(raw, NOW), String(raw)).toBeNull()
    }
  })

  it("never carries a password through", () => {
    const withPassword = JSON.stringify({ ...save, form: { ...save.form, password: "hunter2" } })
    expect(JSON.stringify(parsePendingReauth(withPassword, NOW))).not.toContain("hunter2")
  })
})
