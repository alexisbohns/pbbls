import { describe, expect, it } from "vitest"
import { completeDefaults } from "./complete"
import {
  clearAllCompleteDrafts,
  clearCompleteDraft,
  COMPLETE_DRAFT_PREFIX,
  draftFromSnapshot,
  readCompleteDraft,
  snapshotFromDraft,
  writeCompleteDraft,
  type CompleteDraftSnapshot,
  type DraftStorage,
} from "./complete-draft-store"

const OWNER = "907e7d76-00d3-4e53-82a3-4849bf0a9614"
const OTHER_OWNER = "11111111-2222-4333-8444-555555555555"
const MOMENT = "492238c2-e6c2-4cea-943b-26ea8e16b961"
const EMOTION = "332cabe9-c76d-d737-bba6-f2d7024de808"
const DOMAIN = "5b0c3d3e-1111-4c6a-9a3a-0d6c2f1e2a10"
const SOUL = "a1b2c3d4-0000-4000-8000-000000000001"
const COLLECTION = "c0c0c0c0-0000-4000-8000-000000000002"

/** A `Storage` stand-in over a Map, in insertion order. */
class FakeStorage implements DraftStorage {
  readonly map = new Map<string, string>()
  get length() {
    return this.map.size
  }
  key(index: number) {
    return [...this.map.keys()][index] ?? null
  }
  getItem(key: string) {
    return this.map.get(key) ?? null
  }
  setItem(key: string, value: string) {
    this.map.set(key, value)
  }
  removeItem(key: string) {
    this.map.delete(key)
  }
}

const KEY = COMPLETE_DRAFT_PREFIX + MOMENT

function fullSnapshot(): CompleteDraftSnapshot {
  return {
    owner_id: OWNER,
    moment_id: MOMENT,
    intensity: 3,
    positiveness: -1,
    emotion_id: EMOTION,
    domain_ids: [DOMAIN],
    soul_ids: [SOUL],
    collection_ids: [COLLECTION],
    visibility: "private",
  }
}

describe("complete draft store", () => {
  it("round-trips every key", () => {
    const store = new FakeStorage()
    writeCompleteDraft(store, fullSnapshot())
    expect([...store.map.keys()]).toEqual([KEY])
    expect(readCompleteDraft(store, OWNER, MOMENT)).toEqual(fullSnapshot())
  })

  it("reads the body shape iOS writes, absent keys omitted", () => {
    const store = new FakeStorage()
    store.setItem(
      KEY,
      `{"owner_id":"${OWNER}","moment_id":"${MOMENT}","intensity":2,"positiveness":0,"emotion_id":"${EMOTION}","domain_ids":["${DOMAIN}"],"collection_ids":[],"visibility":"secret"}`,
    )
    expect(readCompleteDraft(store, OWNER, MOMENT)).toEqual({
      owner_id: OWNER,
      moment_id: MOMENT,
      intensity: 2,
      positiveness: 0,
      emotion_id: EMOTION,
      domain_ids: [DOMAIN],
      collection_ids: [],
      visibility: "secret",
    })
  })

  it("matches the moment key case-insensitively", () => {
    const store = new FakeStorage()
    writeCompleteDraft(store, fullSnapshot())
    expect(readCompleteDraft(store, OWNER.toUpperCase(), MOMENT.toUpperCase())).not.toBeNull()
  })

  it("removes and ignores another owner's draft", () => {
    const store = new FakeStorage()
    writeCompleteDraft(store, fullSnapshot())
    expect(readCompleteDraft(store, OTHER_OWNER, MOMENT)).toBeNull()
    expect(store.map.has(KEY)).toBe(false)
  })

  it("removes a body filed under another moment's key", () => {
    const store = new FakeStorage()
    store.setItem(KEY, JSON.stringify({ ...fullSnapshot(), moment_id: "ffffffff-0000-4000-8000-000000000000" }))
    expect(readCompleteDraft(store, OWNER, MOMENT)).toBeNull()
    expect(store.map.has(KEY)).toBe(false)
  })

  it("removes corrupt JSON", () => {
    const store = new FakeStorage()
    store.setItem(KEY, "{not json")
    expect(readCompleteDraft(store, OWNER, MOMENT)).toBeNull()
    expect(store.map.has(KEY)).toBe(false)
  })

  it("removes a body with nothing picked in it", () => {
    const store = new FakeStorage()
    store.setItem(KEY, JSON.stringify({ owner_id: OWNER, moment_id: MOMENT, visibility: "public" }))
    expect(readCompleteDraft(store, OWNER, MOMENT)).toBeNull()
    expect(store.map.has(KEY)).toBe(false)
  })

  it("drops malformed fields rather than trusting them", () => {
    const store = new FakeStorage()
    store.setItem(
      KEY,
      JSON.stringify({ owner_id: OWNER, moment_id: MOMENT, intensity: 7, emotion_id: EMOTION, visibility: "everyone" }),
    )
    expect(readCompleteDraft(store, OWNER, MOMENT)).toEqual({
      owner_id: OWNER,
      moment_id: MOMENT,
      emotion_id: EMOTION,
    })
  })

  it("returns null for a moment with no draft", () => {
    expect(readCompleteDraft(new FakeStorage(), OWNER, MOMENT)).toBeNull()
  })

  it("clears one moment's draft", () => {
    const store = new FakeStorage()
    writeCompleteDraft(store, fullSnapshot())
    clearCompleteDraft(store, MOMENT.toUpperCase())
    expect(store.map.size).toBe(0)
  })

  it("clearAllCompleteDrafts removes only prefixed keys", () => {
    const store = new FakeStorage()
    store.setItem("pbbls-pending-invite", "abc")
    writeCompleteDraft(store, fullSnapshot())
    writeCompleteDraft(store, { ...fullSnapshot(), moment_id: "ffffffff-0000-4000-8000-000000000000" })
    store.setItem("theme", "dark")
    clearAllCompleteDrafts(store)
    expect([...store.map.keys()]).toEqual(["pbbls-pending-invite", "theme"])
  })
})

describe("snapshotFromDraft", () => {
  it("is null for an empty draft, whatever the visibility", () => {
    expect(snapshotFromDraft(OWNER, MOMENT, completeDefaults())).toBeNull()
    expect(snapshotFromDraft(OWNER, MOMENT, { ...completeDefaults(), visibility: "public" })).toBeNull()
  })

  it("keeps the valence only once confirmed", () => {
    const seeded = snapshotFromDraft(OWNER, MOMENT, { ...completeDefaults(), emotionId: EMOTION })
    expect(seeded).toEqual({ owner_id: OWNER, moment_id: MOMENT, emotion_id: EMOTION, visibility: "secret" })

    const confirmed = snapshotFromDraft(OWNER, MOMENT, {
      ...completeDefaults(),
      intensity: 1,
      positiveness: 1,
      valenceConfirmed: true,
    })
    expect(confirmed).toEqual({
      owner_id: OWNER,
      moment_id: MOMENT,
      intensity: 1,
      positiveness: 1,
      visibility: "secret",
    })
  })

  it("omits empty lists and lowercases every id", () => {
    const snap = snapshotFromDraft(OWNER.toUpperCase(), MOMENT.toUpperCase(), {
      ...completeDefaults(),
      valenceConfirmed: true,
      emotionId: EMOTION.toUpperCase(),
      domainIds: [DOMAIN.toUpperCase()],
    })
    expect(snap).toEqual({
      owner_id: OWNER,
      moment_id: MOMENT,
      intensity: 2,
      positiveness: 0,
      emotion_id: EMOTION,
      domain_ids: [DOMAIN],
      visibility: "secret",
    })
    expect(snap && "soul_ids" in snap).toBe(false)
    expect(snap && "collection_ids" in snap).toBe(false)
  })
})

describe("draftFromSnapshot", () => {
  const known = { soulIds: new Set([SOUL]), collectionIds: new Set([COLLECTION]) }

  it("confirms the valence when intensity is present", () => {
    const draft = draftFromSnapshot(fullSnapshot(), known)
    expect(draft).toEqual({
      intensity: 3,
      positiveness: -1,
      valenceConfirmed: true,
      emotionId: EMOTION,
      domainIds: [DOMAIN],
      soulIds: [SOUL],
      collectionIds: [COLLECTION],
      visibility: "private",
    })
  })

  it("leaves the valence unconfirmed at the seed when absent", () => {
    const draft = draftFromSnapshot({ owner_id: OWNER, moment_id: MOMENT, emotion_id: EMOTION }, known)
    expect(draft).toMatchObject({ intensity: 2, positiveness: 0, valenceConfirmed: false, visibility: "secret" })
  })

  it("drops a soul or a collection that no longer exists", () => {
    const draft = draftFromSnapshot(
      {
        ...fullSnapshot(),
        soul_ids: [SOUL, "deadbeef-0000-4000-8000-000000000000"],
        collection_ids: ["deadbeef-0000-4000-8000-000000000001"],
      },
      known,
    )
    expect(draft.soulIds).toEqual([SOUL])
    expect(draft.collectionIds).toEqual([])
  })

  it("keeps at most one domain and one collection", () => {
    const draft = draftFromSnapshot(
      { ...fullSnapshot(), domain_ids: [DOMAIN, "d2"], collection_ids: [COLLECTION, COLLECTION] },
      known,
    )
    expect(draft.domainIds).toEqual([DOMAIN])
    expect(draft.collectionIds).toEqual([COLLECTION])
  })
})
