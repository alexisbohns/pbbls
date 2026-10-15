import { describe, expect, it } from "vitest"
import { createLatestRequest } from "@/lib/utils/latest-request"

describe("createLatestRequest", () => {
  it("treats a lone request as the latest", () => {
    const requests = createLatestRequest()
    const token = requests.begin()
    expect(requests.isLatest(token)).toBe(true)
  })

  it("makes an earlier request stale once a newer one begins", () => {
    const requests = createLatestRequest()
    const first = requests.begin()
    const second = requests.begin()
    expect(requests.isLatest(first)).toBe(false)
    expect(requests.isLatest(second)).toBe(true)
  })

  it("keeps an earlier request stale whatever order the results land in", () => {
    const requests = createLatestRequest()
    const first = requests.begin()
    const second = requests.begin()
    // The second result lands first, then the first: only the second applies.
    expect(requests.isLatest(second)).toBe(true)
    expect(requests.isLatest(first)).toBe(false)
  })

  it("makes every request stale on invalidate", () => {
    const requests = createLatestRequest()
    const token = requests.begin()
    requests.invalidate()
    expect(requests.isLatest(token)).toBe(false)
  })

  it("lets a request begun after invalidate be the latest", () => {
    const requests = createLatestRequest()
    requests.begin()
    requests.invalidate()
    const next = requests.begin()
    expect(requests.isLatest(next)).toBe(true)
  })

  it("keeps separate trackers independent", () => {
    const a = createLatestRequest()
    const b = createLatestRequest()
    const tokenA = a.begin()
    b.begin()
    b.begin()
    expect(a.isLatest(tokenA)).toBe(true)
  })
})
