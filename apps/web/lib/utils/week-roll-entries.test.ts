import { describe, expect, it } from "vitest"
import { momentFromViewRow } from "@/lib/data/moment-row"
import {
  ANDROID_DETAIL_BARE_NAMED,
  IOS_DETAIL_COMPLETED,
  IOS_DETAIL_PHOTO,
} from "@/lib/data/__fixtures__/moments-captured"
import { focusKeyFor } from "./week-roll-entries"

// TZ is Europe/Paris (vitest.config.ts). The moments are what iOS and Android
// wrote, verbatim.
const MOMENTS = [
  momentFromViewRow(JSON.parse(IOS_DETAIL_COMPLETED), new Map()), // Mon 12 Oct 10:30 Paris
  momentFromViewRow(JSON.parse(IOS_DETAIL_PHOTO), new Map()), // Sun 11 Oct 21:04 Paris, bare
  momentFromViewRow(JSON.parse(ANDROID_DETAIL_BARE_NAMED)[0], new Map()), // Mon 12 Oct 08:40 Paris, bare
]

describe("focusKeyFor", () => {
  it("opens the week the moment happened in", () => {
    expect(focusKeyFor(MOMENTS, "daa2a992-16d1-459a-90d0-e3d42fb863a9")).toBe("2026-W42")
  })

  it("focuses a bare moment's week too", () => {
    expect(focusKeyFor(MOMENTS, "ffd58f73-0339-4590-ad0e-84b894a266d8")).toBe("2026-W42")
    // Sunday evening in Paris is still the week before, though the UTC day is the same.
    expect(focusKeyFor(MOMENTS, "492238c2-e6c2-4cea-943b-26ea8e16b961")).toBe("2026-W41")
  })

  it("matches an id whatever its case", () => {
    expect(focusKeyFor(MOMENTS, "DAA2A992-16D1-459A-90D0-E3D42FB863A9")).toBe("2026-W42")
  })

  it("is null for a moment the store does not hold (yet)", () => {
    expect(focusKeyFor(MOMENTS, "00000000-0000-4000-8000-000000000000")).toBeNull()
    expect(focusKeyFor([], "daa2a992-16d1-459a-90d0-e3d42fb863a9")).toBeNull()
  })
})
