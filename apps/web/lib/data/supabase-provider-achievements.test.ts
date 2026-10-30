import { createClient } from "@supabase/supabase-js"
import { afterEach, describe, expect, it, vi } from "vitest"
import { SupabaseProvider } from "./supabase-provider"

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

describe("checkAchievements", () => {
  it("gives up after 10 s, so a hung check cannot hold the page's read", async () => {
    vi.useFakeTimers()
    const hung = providerAnswering(() => new Promise<Response>(() => {}))
    const check = hung.checkAchievements().catch((e: unknown) => e)
    await vi.advanceTimersByTimeAsync(10000)
    expect(String(await check)).toContain("[timeout] check_achievements")
  })
})
