import { describe, expect, it } from "vitest"
import { DUMPED_GET_ACHIEVEMENTS } from "@/lib/data/__fixtures__/achievements-captured"
import { decodeAchievementsPage } from "@/lib/data/achievements-payload"
import { shelfDisplays } from "./shelf"
import type { Achievement } from "@/lib/types"

const shelf = decodeAchievementsPage(JSON.parse(DUMPED_GET_ACHIEVEMENTS))?.page.shelf ?? []

const row = (slug: string, family: Achievement["family"], over: Partial<Achievement> = {}): Achievement => ({
  id: `id-${slug}`,
  slug,
  family,
  threshold: null,
  emotionId: null,
  domainId: null,
  sortOrder: 0,
  glyphId: null,
  karmaReward: 0,
  isActive: true,
  titleEn: null,
  titleFr: null,
  descriptionEn: null,
  descriptionFr: null,
  ...over,
})

describe("the Profile shelf", () => {
  it("resolves each served badge from the catalog by slug, in the served order", () => {
    const catalog = [row("first-pebble", "pebble_count", { titleEn: "First pebble" }), row("pebble-count-1", "pebble_count", { threshold: 1 })]
    expect(shelfDisplays(shelf, catalog).map((d) => d.id)).toEqual(["id-pebble-count-1", "id-first-pebble"])
  })

  it("draws a badge of a known family from the served row alone, and leaves out one it cannot name", () => {
    const out = shelfDisplays(shelf, [])
    expect(out).toEqual([
      expect.objectContaining({ slug: "pebble-count-1", family: "pebble_count", threshold: 1 }),
    ])
  })
})
