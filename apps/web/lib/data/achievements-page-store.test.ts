import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { AchievementsPageStore, type AchievementsPageProvider } from "./achievements-page-store"
import { achievementsCacheEpoch, clearAchievementsCaches, writeAchievementsCache } from "./achievements-cache"
import { CeremonyNotReadyError, type AchievementUnlockResult, type CeremonyPull } from "./data-provider"
import { DUMPED_GET_ACHIEVEMENTS, DUMPED_GET_ACHIEVEMENTS_PULLED } from "./__fixtures__/achievements-captured"

class MemoryStorage {
  private items = new Map<string, string>()
  get length() {
    return this.items.size
  }
  key(i: number) {
    return [...this.items.keys()][i] ?? null
  }
  getItem(k: string) {
    return this.items.get(k) ?? null
  }
  setItem(k: string, v: string) {
    this.items.set(k, v)
  }
  removeItem(k: string) {
    this.items.delete(k)
  }
}

type Deferred<T> = { promise: Promise<T>; resolve: (value: T) => void; reject: (err: unknown) => void }
function deferred<T>(): Deferred<T> {
  let resolve: (value: T) => void = () => {}
  let reject: (err: unknown) => void = () => {}
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const ZONE = "Europe/Paris"
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

/** The dumped page, its six firsts' completion set as served by `edit`. */
function page(edit?: (completion: Record<string, unknown>) => void): unknown {
  const body: unknown = JSON.parse(DUMPED_GET_ACHIEVEMENTS)
  if (edit) {
    const explorer: unknown = Reflect.get(Object(body), "explorer")
    edit(Object(Reflect.get(Object(explorer), "completion")))
  }
  return body
}
const READY = () => page((c) => (c.ready = true))
const NOT_READY = () => page()
const PULLED = (): unknown => JSON.parse(DUMPED_GET_ACHIEVEMENTS_PULLED)

function provider(reads: Array<() => Promise<unknown>>, overrides: Partial<AchievementsPageProvider> = {}) {
  const queue = [...reads]
  const calls: string[] = []
  const fake = {
    checkAchievements: vi.fn(async (): Promise<AchievementUnlockResult[]> => {
      calls.push("check")
      return []
    }),
    getAchievementsPayload: vi.fn((tz: string, signal?: AbortSignal): Promise<unknown> => {
      calls.push(`read ${tz}`)
      void signal
      const next = queue.shift()
      return next ? next() : Promise.reject(new Error("no read scripted"))
    }),
    markAchievementsSeen: vi.fn(async (): Promise<void> => {}),
    pullCeremony: vi.fn(async (): Promise<CeremonyPull | null> => ({ karmaGranted: 20 })),
    ...overrides,
  }
  return { fake, calls }
}

function store(p: AchievementsPageProvider, storage = new MemoryStorage()) {
  return { store: new AchievementsPageStore({ provider: p, owner: "alice", zone: () => ZONE, storage }), storage }
}

let error: ReturnType<typeof vi.spyOn>
let warn: ReturnType<typeof vi.spyOn>
beforeEach(() => {
  error = vi.spyOn(console, "error").mockImplementation(() => {})
  warn = vi.spyOn(console, "warn").mockImplementation(() => {})
  vi.spyOn(console, "info").mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe("loading the page", () => {
  it("checks before it reads, sends the zone, and keeps the body", async () => {
    const { fake, calls } = provider([async () => NOT_READY()])
    const { store: s, storage } = store(fake)
    await s.open()
    expect(calls).toEqual(["check", `read ${ZONE}`])
    expect(s.getSnapshot()).toMatchObject({ loading: false, failed: false })
    expect(s.getSnapshot().page?.daysKept).toBe(1)
    expect(storage.length).toBe(1)
  })

  it("shows the kept copy at once, then replaces it with the read", async () => {
    const read = deferred<unknown>()
    const { fake } = provider([() => read.promise])
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", ZONE, PULLED(), achievementsCacheEpoch())
    const { store: s } = store(fake, storage)
    const opened = s.open()
    expect(s.getSnapshot().page?.explorer?.completion?.pulledAt).not.toBeNull()
    expect(s.getSnapshot().loading).toBe(true)
    read.resolve(NOT_READY())
    await opened
    expect(s.getSnapshot().page?.explorer?.completion?.pulledAt).toBeNull()
  })

  it("still reads when the check fails, and logs the check", async () => {
    const { fake } = provider([async () => NOT_READY()], {
      checkAchievements: vi.fn(async (): Promise<AchievementUnlockResult[]> => {
        throw new Error("check_achievements failed: timeout")
      }),
    })
    const { store: s } = store(fake)
    await s.open()
    expect(s.getSnapshot().page).not.toBeNull()
    expect(warn).toHaveBeenCalledWith("[achievements] check_achievements failed", expect.any(Error))
  })

  it("keeps the kept copy when the read fails, and is failed only with nothing to show", async () => {
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", ZONE, NOT_READY(), achievementsCacheEpoch())
    const kept = store(provider([async () => Promise.reject(new Error("get_achievements failed: invalid_tz"))]).fake, storage).store
    await kept.open()
    expect(kept.getSnapshot()).toMatchObject({ failed: false, loading: false })
    expect(kept.getSnapshot().page).not.toBeNull()

    const empty = store(provider([async () => null]).fake).store
    await empty.open()
    expect(empty.getSnapshot()).toMatchObject({ failed: true, page: null })
    expect(error).toHaveBeenCalledTimes(2)
  })

  it("never lets an older read overwrite a newer one", async () => {
    const first = deferred<unknown>()
    const second = deferred<unknown>()
    const { fake } = provider([() => first.promise, () => second.promise])
    const { store: s } = store(fake)
    const opened = s.open()
    await flush()
    // A pull before any read has landed reads first: the second read.
    const pulled = s.pull("six_firsts")
    second.resolve(PULLED())
    expect(await pulled).toEqual({ kind: "alreadyPulled" })
    first.resolve(NOT_READY())
    await opened
    expect(s.getSnapshot().page?.explorer?.completion?.pulledAt).not.toBeNull()
  })

  it("reads the zone as each read starts, and keeps the body under it", async () => {
    const { fake, calls } = provider([async () => NOT_READY(), async () => NOT_READY()])
    const storage = new MemoryStorage()
    let zone = ZONE
    const s = new AchievementsPageStore({ provider: fake, owner: "alice", zone: () => zone, storage })
    await s.open()
    zone = "America/New_York"
    await s.load()
    expect(calls.filter((c) => c.startsWith("read"))).toEqual([`read ${ZONE}`, "read America/New_York"])
    expect(JSON.parse(storage.getItem("pbbls-achievements:alice") ?? "{}").zone).toBe("America/New_York")
  })

  it("drops a read cancelled by close: no page, no error, nothing kept", async () => {
    const read = deferred<unknown>()
    const { fake } = provider([() => read.promise])
    const { store: s, storage } = store(fake)
    const opened = s.open()
    await flush()
    s.close()
    read.resolve(NOT_READY())
    await opened
    expect(s.getSnapshot()).toMatchObject({ page: null, failed: false, loading: false })
    expect(storage.length).toBe(0)
    expect(error).not.toHaveBeenCalled()
  })

  it("reads afresh when reopened (React's strict effects close and reopen at once)", async () => {
    const { fake } = provider([async () => NOT_READY()])
    const { store: s } = store(fake)
    const closed = s.open()
    s.close()
    await s.open()
    await closed
    // The closed session gave up before its read; the reopened one read.
    expect(fake.getAchievementsPayload).toHaveBeenCalledTimes(1)
    expect(s.getSnapshot()).toMatchObject({ loading: false, failed: false })
    expect(s.getSnapshot().page).not.toBeNull()
  })

  it("never writes back a body read across a sign-out's clear", async () => {
    const read = deferred<unknown>()
    const { fake } = provider([() => read.promise])
    const { store: s, storage } = store(fake)
    const opened = s.open()
    await flush()
    clearAchievementsCaches(storage)
    read.resolve(NOT_READY())
    await opened
    expect(storage.length).toBe(0)
  })
})

describe("marking a card seen", () => {
  it("never throws, and logs a failure", async () => {
    const { fake } = provider([], {
      markAchievementsSeen: vi.fn(async (): Promise<void> => {
        throw new Error("mark_achievements_seen failed: invalid_card")
      }),
    })
    await store(fake).store.markSeen("explorer")
    expect(warn).toHaveBeenCalledWith("[achievements] mark_achievements_seen failed", "explorer", expect.any(Error))
  })
})

describe("pulling a ceremony", () => {
  async function opened(reads: Array<() => Promise<unknown>>, overrides: Partial<AchievementsPageProvider> = {}) {
    const { fake } = provider(reads, overrides)
    const { store: s } = store(fake)
    await s.open()
    return { s, fake }
  }

  it("decides on this session's read, never on the kept copy", async () => {
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", ZONE, READY(), achievementsCacheEpoch())
    const read = deferred<unknown>()
    const { fake } = provider([() => read.promise, async () => PULLED()])
    const s = store(fake, storage).store
    void s.open()
    await flush()
    // The kept copy says ready; the read this pull makes first says pulled.
    expect(s.getSnapshot().page?.explorer?.completion?.ready).toBe(true)
    expect(await s.pull("six_firsts")).toEqual({ kind: "alreadyPulled" })
    expect(fake.pullCeremony).not.toHaveBeenCalled()
    read.resolve(READY())
  })

  it("is failed, without a call, when no read lands in this session", async () => {
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", ZONE, READY(), achievementsCacheEpoch())
    const offline = async () => Promise.reject(new Error("get_achievements failed: offline"))
    const { fake } = provider([offline, offline])
    const s = store(fake, storage).store
    await s.open()
    expect(s.getSnapshot().page?.explorer?.completion?.ready).toBe(true)
    expect(await s.pull("six_firsts")).toEqual({ kind: "failed" })
    expect(fake.pullCeremony).not.toHaveBeenCalled()
  })

  it("is not ready, without a call, when the set is not offered or not complete", async () => {
    const { s, fake } = await opened([async () => NOT_READY()])
    expect(await s.pull("six_firsts")).toEqual({ kind: "notReady" })
    expect(await s.pull("emotions")).toEqual({ kind: "notReady" })
    expect(fake.pullCeremony).not.toHaveBeenCalled()
  })

  it("is already pulled, without a call, when the read shows pulled_at", async () => {
    const { s, fake } = await opened([async () => PULLED()])
    expect(await s.pull("six_firsts")).toEqual({ kind: "alreadyPulled" })
    expect(fake.pullCeremony).not.toHaveBeenCalled()
  })

  it("pulls a ready set, sends the zone, and reads again", async () => {
    const { s, fake } = await opened([async () => READY(), async () => PULLED()], {
      pullCeremony: vi.fn(async (): Promise<CeremonyPull | null> => ({ karmaGranted: 0 })),
    })
    expect(await s.pull("six_firsts")).toEqual({ kind: "pulled", karmaGranted: 0 })
    expect(fake.pullCeremony).toHaveBeenCalledWith("six_firsts", ZONE)
    expect(fake.getAchievementsPayload).toHaveBeenCalledTimes(2)
    expect(s.getSnapshot().page?.explorer?.completion?.pulledAt).not.toBeNull()
    expect(s.getSnapshot().pulling).toBeNull()
  })

  it("reads no row as pulled without karma only when the read after it shows pulled_at", async () => {
    const landed = await opened([async () => READY(), async () => PULLED()], {
      pullCeremony: vi.fn(async (): Promise<CeremonyPull | null> => null),
    })
    expect(await landed.s.pull("six_firsts")).toEqual({ kind: "pulledWithoutKarma" })

    const lost = await opened([async () => READY(), async () => READY()], {
      pullCeremony: vi.fn(async (): Promise<CeremonyPull | null> => null),
    })
    expect(await lost.s.pull("six_firsts")).toEqual({ kind: "alreadyPulled" })
    expect(error).toHaveBeenCalledWith(
      "[achievements] pull_ceremony(six_firsts) returned no row, and the read does not show it pulled",
    )
  })

  it("settles a lost answer by the read after it, and is failed when that read fails too", async () => {
    const timeout = vi.fn(async (): Promise<CeremonyPull | null> => {
      throw new Error("[timeout] pull_ceremony did not respond after 10000ms")
    })
    const landed = await opened([async () => READY(), async () => PULLED()], { pullCeremony: timeout })
    expect(await landed.s.pull("six_firsts")).toEqual({ kind: "pulledWithoutKarma" })

    const unknown = await opened([async () => READY(), async () => Promise.reject(new Error("offline"))], { pullCeremony: timeout })
    expect(await unknown.s.pull("six_firsts")).toEqual({ kind: "failed" })
  })

  it("reads not_ready as not ready", async () => {
    const { s } = await opened([async () => READY(), async () => READY()], {
      pullCeremony: vi.fn(async (): Promise<CeremonyPull | null> => {
        throw new CeremonyNotReadyError("six_firsts")
      }),
    })
    expect(await s.pull("six_firsts")).toEqual({ kind: "notReady" })
  })

  it("is busy while another pull runs", async () => {
    const answer = deferred<CeremonyPull | null>()
    const { s } = await opened([async () => READY(), async () => PULLED()], { pullCeremony: vi.fn(() => answer.promise) })
    const first = s.pull("six_firsts")
    expect(s.getSnapshot().pulling).toBe("six_firsts")
    expect(await s.pull("six_firsts")).toEqual({ kind: "busy" })
    answer.resolve({ karmaGranted: 20 })
    expect(await first).toEqual({ kind: "pulled", karmaGranted: 20 })
  })

  it("is failed with the page closed", async () => {
    const { s } = await opened([async () => READY()])
    s.close()
    expect(await s.pull("six_firsts")).toEqual({ kind: "failed" })
  })
})
