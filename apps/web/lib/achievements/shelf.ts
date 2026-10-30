import type { Achievement, AchievementDisplay, AchievementFamily, ShelfItem } from "@/lib/types"

// The owner's Profile shelf (#1308, D18): the served `shelf`, newest first,
// private families already removed by the server. A badge's title and icon
// resolve from the catalog by slug; without its catalog row (the catalog not
// read yet, or a row this build cannot see), a badge of a family this build
// knows still shows, from the served row alone.

const KNOWN: readonly AchievementFamily[] = [
  "pebble_count",
  "emotion_first",
  "domain_first",
  "first_collection",
  "first_glyph",
  "first_soul",
  "glyph_count",
  "glyph_sales",
]

export function shelfDisplays(shelf: readonly ShelfItem[], catalog: readonly Achievement[]): AchievementDisplay[] {
  const bySlug = new Map(catalog.map((a) => [a.slug, a]))
  return shelf.flatMap((item): AchievementDisplay[] => {
    const row = bySlug.get(item.slug)
    if (row) return [row]
    const family = KNOWN.find((f) => f === item.family)
    if (!family) return []
    return [
      {
        id: item.slug,
        slug: item.slug,
        family,
        threshold: item.threshold,
        emotionId: null,
        domainId: null,
        titleEn: null,
        titleFr: null,
        descriptionEn: null,
        descriptionFr: null,
      },
    ]
  })
}
