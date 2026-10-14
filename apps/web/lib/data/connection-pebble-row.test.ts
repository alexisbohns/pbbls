import { describe, expect, it } from "vitest"
import { compareSharedNewestFirst, sharedConnectionPebbleFromRow } from "./connection-pebble-row"
import { pebbleGlyphFromRow } from "./pebble-row"
import { IOS_DETAIL_COMPLETED } from "./__fixtures__/moments-captured"

// SHAPED, NOT CAPTURED. No test account was signed in for this change, so this
// body was built from the SQL and the captured mobile payloads instead of
// recorded: the query is
//   GET /rest/v1/pebbles?select=id,visibility,intensity,positiveness,
//       emotion:emotions(id,slug,name,color),moment:moments!inner(name,happened_at)
//       &user_id=eq.<peer>
// PostgREST serialises table rows compactly (no jsonb spacing), to-one embeds
// as objects, and timestamptz with a `+00:00` offset at the stored precision.
// Ids, emotions and dates come from the Android (#1048) and iOS captures; the
// third row is a pre-split web pebble (fractional seconds) on a photo-only
// moment. Replace with a verbatim capture when a test account is at hand.
const CONNECTION_PEBBLES_SHAPED = String.raw`[{"id":"e4ee586b-c229-4a7e-ab7d-bc820e51e1a5","visibility":"private","intensity":2,"positiveness":0,"emotion":{"id":"50a68890-f6bb-e67a-6655-e84ff0b71f49","slug":"amazed","name":"Awe","color":"#A15C08"},"moment":{"name":"Evening walk with Maya","happened_at":"2026-10-12T18:05:00+00:00"}}, 
 {"id":"bdbc0b70-f8db-46ef-873f-f93a1ccd72dc","visibility":"public","intensity":2,"positiveness":1,"emotion":{"id":"332cabe9-c76d-d737-bba6-f2d7024de808","slug":"disgusted","name":"Disgust","color":"#8E4242"},"moment":{"name":"Coffee","happened_at":"2026-10-12T08:30:00+00:00"}}, 
 {"id":"0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b","visibility":"private","intensity":3,"positiveness":-1,"emotion":{"id":"332cabe9-c76d-d737-bba6-f2d7024de808","slug":"disgusted","name":"Disgust","color":"#8E4242"},"moment":{"name":null,"happened_at":"2026-10-06T17:42:09.123456+00:00"}}]`

const rows = (): Record<string, unknown>[] => JSON.parse(CONNECTION_PEBBLES_SHAPED)

describe("sharedConnectionPebbleFromRow", () => {
  it("maps every field of a row", () => {
    expect(sharedConnectionPebbleFromRow(rows()[0], null)).toEqual({
      id: "e4ee586b-c229-4a7e-ab7d-bc820e51e1a5",
      name: "Evening walk with Maya",
      happened_at: "2026-10-12T18:05:00+00:00",
      visibility: "private",
      intensity: 2,
      positiveness: 0,
      emotion: { id: "50a68890-f6bb-e67a-6655-e84ff0b71f49", slug: "amazed", name: "Awe", color: "#A15C08" },
      glyph: null,
    })
  })

  it("carries the resolved glyph it is given", () => {
    const pebble = (JSON.parse(IOS_DETAIL_COMPLETED) as { pebble: { glyph: unknown } }).pebble
    const glyph = pebbleGlyphFromRow(pebble.glyph)
    expect(sharedConnectionPebbleFromRow(rows()[1], glyph)?.glyph).toBe(glyph)
  })

  it("names a photo-only moment with an empty string and keeps microseconds", () => {
    const shared = sharedConnectionPebbleFromRow(rows()[2], null)
    expect(shared?.name).toBe("")
    expect(shared?.happened_at).toBe("2026-10-06T17:42:09.123456+00:00")
  })

  it("drops a row whose moment embed is missing", () => {
    expect(sharedConnectionPebbleFromRow({ ...rows()[0], moment: null }, null)).toBeNull()
  })

  it("drops a row whose emotion embed is incomplete", () => {
    expect(sharedConnectionPebbleFromRow({ ...rows()[0], emotion: { id: "x" } }, null)).toBeNull()
  })
})

describe("compareSharedNewestFirst", () => {
  it("orders by the moment's happened_at across precisions", () => {
    const mapped = rows()
      .reverse()
      .flatMap((r) => sharedConnectionPebbleFromRow(r, null) ?? [])
    expect(mapped.sort(compareSharedNewestFirst).map((p) => p.id)).toEqual([
      "e4ee586b-c229-4a7e-ab7d-bc820e51e1a5",
      "bdbc0b70-f8db-46ef-873f-f93a1ccd72dc",
      "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b",
    ])
  })
})
