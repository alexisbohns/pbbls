import { describe, expect, it } from "vitest"
import { momentFromViewRow } from "@/lib/data/moment-row"
import {
  ANDROID_DETAIL_BARE_NAMED,
  IOS_DETAIL_COMPLETED,
  IOS_DETAIL_PHOTO,
} from "@/lib/data/__fixtures__/moments-captured"
import { focusKeyFor, nextFocusStep } from "./week-roll-entries"

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

describe("nextFocusStep", () => {
  const ID = "daa2a992-16d1-459a-90d0-e3d42fb863a9"
  const OTHER = "492238c2-e6c2-4cea-943b-26ea8e16b961" // the Sunday one, week 41
  const MISSING = "00000000-0000-4000-8000-000000000000"

  /** Renders PathScreen's state machine: every week it pages to, in order. */
  function run(renders: { moments: typeof MOMENTS; focus: string | null; loading: boolean }[]) {
    let consumed: string | null = null
    const paged: string[] = []
    for (const r of renders) {
      const step = nextFocusStep(r.moments, r.focus, consumed, r.loading)
      if (step === null) continue
      consumed = step.consumed
      if (step.key !== null) paged.push(step.key)
    }
    return { consumed, paged }
  }

  it("applies a present focus once", () => {
    const { consumed, paged } = run([
      { moments: [], focus: ID, loading: true },
      { moments: MOMENTS, focus: ID, loading: true },
      { moments: MOMENTS, focus: ID, loading: false },
    ])
    expect(paged).toEqual(["2026-W42"])
    expect(consumed).toBe(ID)
  })

  it("consumes a focus that is still missing once loading settles", () => {
    expect(nextFocusStep(MOMENTS, MISSING, null, true)).toBeNull()
    expect(nextFocusStep(MOMENTS, MISSING, null, false)).toEqual({ consumed: MISSING, key: null })
  })

  it("does not re-apply a consumed focus on a store refresh", () => {
    // The capture's reload failed: the moment only arrives on a later refresh.
    const { paged } = run([
      { moments: [], focus: ID, loading: true },
      { moments: [], focus: ID, loading: false },
      { moments: MOMENTS, focus: ID, loading: true },
      { moments: MOMENTS, focus: ID, loading: false },
    ])
    expect(paged).toEqual([])
    expect(nextFocusStep(MOMENTS, ID, ID, false)).toBeNull()
  })

  it("re-arms after the URL is cleaned and applies the next focus", () => {
    const { consumed, paged } = run([
      { moments: MOMENTS, focus: ID, loading: false },
      { moments: MOMENTS, focus: null, loading: false },
      { moments: MOMENTS, focus: OTHER, loading: false },
    ])
    expect(paged).toEqual(["2026-W42", "2026-W41"])
    expect(consumed).toBe(OTHER)
  })

  it("re-arms the same id after the URL is cleaned", () => {
    expect(nextFocusStep(MOMENTS, null, ID, false)).toEqual({ consumed: null, key: null })
    expect(nextFocusStep(MOMENTS, null, null, false)).toBeNull()
    expect(nextFocusStep(MOMENTS, ID, null, false)).toEqual({ consumed: ID, key: "2026-W42" })
  })
})
