import { describe, expect, it, vi } from "vitest"
import {
  compareMomentsNewestFirst,
  deleteMomentPaths,
  isCompleted,
  legacyPebbleFromMoment,
  momentDisplayName,
  momentFromViewRow,
  momentPebbleFromRow,
} from "./moment-row"
import {
  ANDROID_DETAIL_BARE_NAMED,
  ANDROID_DETAIL_COMPLETED,
  FRACTIONAL_WEB_ROW,
  IOS_DELETE_MOMENT,
  IOS_DETAIL_COMPLETED,
  IOS_DETAIL_PHOTO,
} from "./__fixtures__/moments-captured"

const row = (json: string): Record<string, unknown> => JSON.parse(json)
const firstRow = (json: string): Record<string, unknown> => JSON.parse(json)[0]

describe("captured fixtures", () => {
  it("are byte for byte what the sources hold", () => {
    // The Android source asserts these lengths on its own copies.
    expect(ANDROID_DETAIL_COMPLETED.length).toBe(3363)
    expect(ANDROID_DETAIL_BARE_NAMED.length).toBe(251)
    expect(IOS_DETAIL_COMPLETED.length).toBe(3065)
    expect(IOS_DETAIL_PHOTO.length).toBe(461)
  })
})

describe("momentFromViewRow", () => {
  it("decodes the iOS completed moment", () => {
    const m = momentFromViewRow(row(IOS_DETAIL_COMPLETED), new Map())
    expect(m.id).toBe("daa2a992-16d1-459a-90d0-e3d42fb863a9")
    expect(m.name).toBe("Coffee")
    expect(m.description).toBeNull()
    expect(new Date(m.happened_at).toISOString()).toBe("2026-10-12T08:30:00.000Z")
    expect(m.instants).toEqual([])
    expect(m.cover).toBeNull()
    expect(isCompleted(m)).toBe(true)
    expect(m.pebble?.id).toBe("bdbc0b70-f8db-46ef-873f-f93a1ccd72dc")
    expect(m.pebble?.intensity).toBe(2)
    expect(m.pebble?.positiveness).toBe(1)
    expect(m.pebble?.visibility).toBe("private")
    expect(m.pebble?.emotion_id).toBe("332cabe9-c76d-d737-bba6-f2d7024de808")
    expect(m.pebble?.soul_ids).toEqual(["91034f33-4240-4842-8236-4d797c46ac30"])
    expect(m.pebble?.domain_ids).toEqual(["574216e8-8a96-9059-b3de-cfa568b76016"])
    expect(m.pebble?.collection_ids).toEqual([
      "0da36370-3a59-44c4-94e6-a49830a73339",
      "bcb62445-b353-4cc5-878e-e96431949cce",
    ])
    expect(m.pebble?.cards).toEqual([])
    expect(m.pebble?.glyph?.id).toBe("f738661b-6f49-448e-916c-443fed751475")
    expect(m.pebble?.glyph?.strokes.length).toBe(5)
    expect(m.pebble?.glyph?.viewBox).toBe("0 0 200 200")
  })

  it("decodes the iOS photo-only moment and signs its cover", () => {
    const prefix = "907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a"
    const signed = new Map([
      [`${prefix}/original.jpg`, "https://signed/original"],
      [`${prefix}/thumb.jpg`, "https://signed/thumb"],
    ])
    const m = momentFromViewRow(row(IOS_DETAIL_PHOTO), signed)
    expect(m.name).toBeNull()
    expect(m.pebble).toBeNull()
    expect(isCompleted(m)).toBe(false)
    expect(m.snaps).toEqual([
      { id: "d49168b5-b97a-4888-a787-43ec65bb416a", sort_order: 0, storage_path: prefix },
    ])
    expect(m.instants).toEqual(["https://signed/original"])
    expect(m.cover).toEqual({
      snapId: "d49168b5-b97a-4888-a787-43ec65bb416a",
      url: "https://signed/original",
      thumbUrl: "https://signed/thumb",
    })
  })

  it("decodes the Android bare named moment, which has no updated_at", () => {
    const m = momentFromViewRow(firstRow(ANDROID_DETAIL_BARE_NAMED), new Map())
    expect(m.name).toBe("Morning coffee on the balcony")
    expect(m.description).toBe("First cold morning of the season.")
    expect(m.pebble).toBeNull()
    expect(m.updated_at).toBe("2026-10-13T00:20:24.890253+00:00")
  })

  it("decodes the Android completed moment with a domain-sourced glyph", () => {
    const m = momentFromViewRow(firstRow(ANDROID_DETAIL_COMPLETED), new Map())
    expect(m.name).toBe("Evening walk with Maya")
    expect(m.description).toBe("Long loop by the river.")
    expect(m.pebble?.id).toBe("e4ee586b-c229-4a7e-ab7d-bc820e51e1a5")
    expect(m.pebble?.positiveness).toBe(0)
    expect(m.pebble?.domain_ids).toEqual(["55a005c8-e2af-4f40-ad19-bd6914bee484"])
    const glyph = m.pebble?.glyph
    expect(glyph).not.toBeNull()
    // The resolver's `source` is the server's business, not the stone's.
    expect(glyph && "source" in glyph).toBe(false)
    expect(glyph?.strokes.length).toBe(12)
    expect(glyph?.strokes.every((s) => s.width === 6)).toBe(true)
    expect(glyph?.strokes[glyph.strokes.length - 1].d).toBe("M196.4,46.87 L211.07,38.6")
  })

  it("reads the web's pre-split precision and empty description", () => {
    const m = momentFromViewRow(row(FRACTIONAL_WEB_ROW), new Map())
    expect(m.description).toBeNull()
    expect(new Date(m.happened_at).getTime()).toBe(Date.UTC(2026, 9, 6, 17, 42, 9, 123))
    expect(m.happened_at).toBe("2026-10-06T17:42:09.123456+00:00")
  })
})

describe("momentPebbleFromRow", () => {
  const completed = row(IOS_DETAIL_COMPLETED).pebble as Record<string, unknown>

  it("treats a null or malformed glyph as no glyph", () => {
    expect(momentPebbleFromRow({ ...completed, glyph: null })?.glyph).toBeNull()
    expect(momentPebbleFromRow({ ...completed, glyph: { id: "x" } })?.glyph).toBeNull()
  })

  it("draws an out-of-range valence as no pebble, and says so", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    expect(momentPebbleFromRow({ ...completed, intensity: 4 })).toBeNull()
    expect(momentPebbleFromRow({ ...completed, positiveness: 2 })).toBeNull()
    expect(warn).toHaveBeenCalledWith("[moments] unexpected valence", "bdbc0b70-f8db-46ef-873f-f93a1ccd72dc")
    warn.mockRestore()
  })

  it("is null for null, a non-object, or a pebble missing its id or emotion", () => {
    expect(momentPebbleFromRow(null)).toBeNull()
    expect(momentPebbleFromRow("pebble")).toBeNull()
    expect(momentPebbleFromRow({ ...completed, id: undefined })).toBeNull()
    expect(momentPebbleFromRow({ ...completed, emotion_id: null })).toBeNull()
  })
})

describe("legacyPebbleFromMoment", () => {
  it("keeps the pebble id as the id and carries the moment id", () => {
    const m = momentFromViewRow(row(IOS_DETAIL_COMPLETED), new Map())
    if (!isCompleted(m)) throw new Error("fixture is completed")
    const pebble = legacyPebbleFromMoment(m)
    expect(pebble.id).toBe("bdbc0b70-f8db-46ef-873f-f93a1ccd72dc")
    expect(pebble.moment_id).toBe("daa2a992-16d1-459a-90d0-e3d42fb863a9")
    expect(pebble.name).toBe("Coffee")
    expect(pebble.description).toBeUndefined()
    expect(pebble.mark_id).toBe("f738661b-6f49-448e-916c-443fed751475")
    expect(pebble.collection_ids).toHaveLength(2)
    // The pebble row is newer than the moment row here.
    expect(pebble.updated_at).toBe("2026-10-13T00:12:41.550659+00:00")
  })

  it("names an untitled moment with an empty string", () => {
    const m = momentFromViewRow(row(IOS_DETAIL_COMPLETED), new Map())
    if (!isCompleted(m)) throw new Error("fixture is completed")
    const pebble = legacyPebbleFromMoment({ ...m, name: null, updated_at: "2026-10-14T09:00:00Z" })
    expect(pebble.name).toBe("")
    expect(pebble.updated_at).toBe("2026-10-14T09:00:00Z")
  })
})

describe("momentDisplayName", () => {
  it("trims, and reads a blank name as untitled", () => {
    expect(momentDisplayName("   ")).toBeNull()
    expect(momentDisplayName(null)).toBeNull()
    expect(momentDisplayName(" Coffee ")).toBe("Coffee")
  })
})

describe("compareMomentsNewestFirst", () => {
  it("orders by happened_at across precisions, then by id", () => {
    const photo = momentFromViewRow(row(IOS_DETAIL_PHOTO), new Map()) // 2026-10-11T19:04:51
    const coffee = momentFromViewRow(row(IOS_DETAIL_COMPLETED), new Map()) // 2026-10-12T08:30:00
    const fractional = momentFromViewRow(row(FRACTIONAL_WEB_ROW), new Map()) // 2026-10-06
    const twin = { ...coffee, id: "ffffffff-0000-4000-8000-000000000000" }
    expect([fractional, photo, coffee, twin].sort(compareMomentsNewestFirst).map((m) => m.id)).toEqual([
      twin.id,
      coffee.id,
      photo.id,
      fractional.id,
    ])
  })
})

describe("deleteMomentPaths", () => {
  it("reads the captured delete_moment body", () => {
    expect(deleteMomentPaths(JSON.parse(IOS_DELETE_MOMENT))).toEqual([
      "907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a",
    ])
  })

  it("is empty for anything else", () => {
    expect(deleteMomentPaths(null)).toEqual([])
    expect(deleteMomentPaths({})).toEqual([])
    expect(deleteMomentPaths({ storage_paths: [] })).toEqual([])
  })
})
