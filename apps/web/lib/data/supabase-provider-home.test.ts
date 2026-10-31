import { createClient } from "@supabase/supabase-js"
import { describe, expect, it } from "vitest"
import { SupabaseProvider } from "./supabase-provider"

// Home's provider calls against a real supabase-js client whose fetch records
// the request and answers what PostgREST would: the seam is the HTTP body.
type Seen = { url: string; body: unknown }

function providerAnswering(body: string): { provider: SupabaseProvider; seen: Seen[] } {
  const seen: Seen[] = []
  const client = createClient("http://localhost:54321", "anon-key", {
    auth: { persistSession: false, autoRefreshToken: false },
    global: {
      fetch: async (input: RequestInfo | URL, init?: RequestInit) => {
        seen.push({ url: String(input), body: typeof init?.body === "string" ? JSON.parse(init.body) : null })
        return new Response(body, { status: 200, headers: { "Content-Type": "application/json" } })
      },
    },
  })
  return { provider: new SupabaseProvider("alice", client), seen }
}

describe("Home's provider calls", () => {
  it("reads the month through path_month_wrap(p_month, p_tz)", async () => {
    const { provider, seen } = providerAnswering(`{"month":"2026-10-01","days_in_month":31,"first_weekday":3,"count":0}`)
    const month = await provider.getHomeMonth("2026-10-01", "Europe/Paris")
    expect(seen[0].url).toContain("/rest/v1/rpc/path_month_wrap")
    expect(seen[0].body).toEqual({ p_month: "2026-10-01", p_tz: "Europe/Paris" })
    expect(month?.weeks).toBeNull()
  })

  it("reads the wraps through home_wraps(p_tz) and unfolds through reveal_wrap(p_month, p_tz)", async () => {
    const { provider, seen } = providerAnswering(`{"months":[]}`)
    expect(await provider.getHomeWraps("Europe/Paris")).toEqual([])
    await provider.revealWrap("2026-09-01", "Europe/Paris")
    expect(seen.map((s) => s.url.split("/rest/v1/")[1])).toEqual(["rpc/home_wraps", "rpc/reveal_wrap"])
    expect(seen[0].body).toEqual({ p_tz: "Europe/Paris" })
    expect(seen[1].body).toEqual({ p_month: "2026-09-01", p_tz: "Europe/Paris" })
  })

  it("selects the published guides by sort order", async () => {
    const { provider, seen } = providerAnswering(`[]`)
    await provider.getGuides("fr")
    const url = decodeURIComponent(seen[0].url)
    expect(url).toContain("/rest/v1/guides?")
    expect(url).toContain("published=eq.true")
    expect(url).toContain("order=sort_order")
    expect(url).toContain("select=slug,illustration,sort_order,title_en,title_fr,summary_en,summary_fr,body_md_en,body_md_fr")
  })

  it("claims through claim_blue_pebble(), reading [] as not granted", async () => {
    const none = providerAnswering(`[]`)
    expect(await none.provider.claimBluePebble()).toBeNull()
    expect(none.seen[0].url).toContain("/rest/v1/rpc/claim_blue_pebble")
    const granted = providerAnswering(`[{"slug":"blue-pebble","karma_granted":50}]`)
    expect(await granted.provider.claimBluePebble()).toEqual({ slug: "blue-pebble", karmaGranted: 50 })
  })
})
