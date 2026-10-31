import { createClient } from "@supabase/supabase-js"
import { afterEach, describe, expect, it, vi } from "vitest"
import { SupabaseProvider } from "./supabase-provider"
import { CeremonyNotReadyError } from "./data-provider"

// The provider's achievements calls against a real supabase-js client whose
// fetch answers what PostgREST would: the seam is the HTTP body, not the client.
function providerAnswering(answer: (url: string) => Promise<Response>): SupabaseProvider {
  const client = createClient("http://localhost:54321", "anon-key", {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { fetch: (input: RequestInfo | URL) => answer(String(input)) },
  })
  return new SupabaseProvider("alice", client)
}

const json = (status: number, body: string) =>
  new Response(body, { status, headers: { "Content-Type": "application/json" } })

afterEach(() => {
  vi.useRealTimers()
})

describe("pullCeremony", () => {
  it("throws CeremonyNotReadyError on the RPC's not_ready, and a plain error on anything else", async () => {
    const notReady = providerAnswering(async () =>
      json(400, `{"code":"P0001","details":null,"hint":null,"message":"not_ready"}`),
    )
    await expect(notReady.pullCeremony("six_firsts", "Europe/Paris")).rejects.toBeInstanceOf(CeremonyNotReadyError)

    const invalid = providerAnswering(async () =>
      json(400, `{"code":"22023","details":null,"hint":null,"message":"not_ready_or_not"}`),
    )
    const err = await invalid.pullCeremony("six_firsts", "Europe/Paris").catch((e: unknown) => e)
    expect(err).toBeInstanceOf(Error)
    expect(err).not.toBeInstanceOf(CeremonyNotReadyError)
  })

  it("reads one row as the pull and no row as null", async () => {
    const pulled = providerAnswering(async () => json(200, `[{"stack": "six_firsts", "karma_granted": 20}]`))
    expect(await pulled.pullCeremony("six_firsts", "Europe/Paris")).toEqual({ karmaGranted: 20 })
    const again = providerAnswering(async () => json(200, `[]`))
    expect(await again.pullCeremony("six_firsts", "Europe/Paris")).toBeNull()
  })
})

describe("checkAchievements", () => {
  it("gives up after 10 s, so a hung check cannot hold the page's read", async () => {
    vi.useFakeTimers()
    const hung = providerAnswering(() => new Promise<Response>(() => {}))
    const check = hung.checkAchievements().catch((e: unknown) => e)
    await vi.advanceTimersByTimeAsync(10000)
    expect(String(await check)).toContain("[timeout] check_achievements")
  })
})
