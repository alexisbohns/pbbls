import { describe, expect, it } from "vitest"
import {
  captureSavePayload,
  completePebblePayload,
  editMomentSavePayload,
  type CompleteDraft,
} from "./moment-payloads"

// The exact serialized JSON is the contract: key order, lowercase ids, whole
// seconds, and absent keys omitted rather than null.

const SNAP = {
  id: "D49168B5-B97A-4888-A787-43EC65BB416A",
  storage_path: "907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a",
  sort_order: 0,
}

const DRAFT: CompleteDraft = {
  intensity: 2,
  positiveness: 1,
  valenceConfirmed: true,
  emotionId: "332CABE9-C76D-D737-BBA6-F2D7024DE808",
  domainIds: ["5B0C3D3E-1111-4C6A-9A3A-0D6C2F1E2A10"],
  soulIds: [],
  collectionIds: [],
  visibility: "private",
}

describe("captureSavePayload", () => {
  it("sends a trimmed name and a whole-second date, nothing else", () => {
    const payload = captureSavePayload({ name: "  Coffee ", happenedAt: "2026-10-12T08:30:00.789Z", snap: undefined })
    expect(JSON.stringify(payload)).toBe(`{"name":"Coffee","happened_at":"2026-10-12T08:30:00Z"}`)
  })

  it("omits a blank name and sends the photo with a lowercase id", () => {
    const payload = captureSavePayload({ name: "   ", happenedAt: "2026-10-11T19:04:51.000Z", snap: SNAP })
    expect(JSON.stringify(payload)).toBe(
      `{"happened_at":"2026-10-11T19:04:51Z","snaps":[{"id":"d49168b5-b97a-4888-a787-43ec65bb416a","storage_path":"907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a","sort_order":0}]}`,
    )
  })

  it("truncates to the second, never rounds", () => {
    const payload = captureSavePayload({ name: "Late", happenedAt: "2026-10-06T17:42:09.999Z", snap: undefined })
    expect(payload.happened_at).toBe("2026-10-06T17:42:09Z")
  })
})

describe("editMomentSavePayload", () => {
  it("sends every field, clears with empty strings and removes the photo with []", () => {
    const payload = editMomentSavePayload("DAA2A992-16D1-459A-90D0-E3D42FB863A9", {
      name: "",
      description: " ",
      happenedAt: "2026-10-12T08:30:00+00:00",
      snaps: [],
    })
    expect(JSON.stringify(payload)).toBe(
      `{"id":"daa2a992-16d1-459a-90d0-e3d42fb863a9","name":"","description":"","happened_at":"2026-10-12T08:30:00Z","snaps":[]}`,
    )
  })

  it("numbers the snaps in their order", () => {
    const payload = editMomentSavePayload("daa2a992-16d1-459a-90d0-e3d42fb863a9", {
      name: "Coffee",
      description: "On the balcony.",
      happenedAt: "2026-10-12T08:30:00.5Z",
      snaps: [{ ...SNAP, sort_order: 3 }],
    })
    expect(payload.snaps).toEqual([{ id: "d49168b5-b97a-4888-a787-43ec65bb416a", storage_path: SNAP.storage_path, sort_order: 0 }])
  })
})

describe("completePebblePayload", () => {
  it("sends every key, ids lowercased", () => {
    expect(JSON.stringify(completePebblePayload("DAA2A992-16D1-459A-90D0-E3D42FB863A9", DRAFT))).toBe(
      `{"moment_id":"daa2a992-16d1-459a-90d0-e3d42fb863a9","emotion_id":"332cabe9-c76d-d737-bba6-f2d7024de808","intensity":2,"positiveness":1,"visibility":"private","domain_ids":["5b0c3d3e-1111-4c6a-9a3a-0d6c2f1e2a10"],"soul_ids":[],"collection_ids":[]}`,
    )
  })

  it("is null until the draft is complete", () => {
    expect(completePebblePayload("m", { ...DRAFT, emotionId: null })).toBeNull()
    expect(completePebblePayload("m", { ...DRAFT, domainIds: [] })).toBeNull()
    expect(completePebblePayload("m", { ...DRAFT, valenceConfirmed: false })).toBeNull()
  })
})

describe("every builder", () => {
  it("never sends a null", () => {
    const outputs = [
      captureSavePayload({ name: "", happenedAt: "2026-10-12T08:30:00Z", snap: undefined }),
      captureSavePayload({ name: "Coffee", happenedAt: "2026-10-12T08:30:00Z", snap: SNAP }),
      editMomentSavePayload("m", { name: "", description: "", happenedAt: "2026-10-12T08:30:00Z", snaps: [] }),
      completePebblePayload("m", DRAFT),
    ]
    for (const output of outputs) expect(JSON.stringify(output)).not.toContain("null")
  })
})
