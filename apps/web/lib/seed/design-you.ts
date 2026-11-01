import type { YouBadge } from "@/components/you/YouAchievementsList"
import type { Log } from "@/lib/types"

// You's fixtures for /sandbox/design (#1384): what the page reads from the
// network (the served counts, the shelf, the Lab), network-free and never a
// source of truth.

/** The served counters and glyph counts (get_profile_engagement, the store). */
export const DESIGN_YOU_COUNTS = { days: 19, pebbles: 128, karma: 1240, glyphsCarved: 12, glyphsOwned: 3 } as const

export const DESIGN_YOU_BADGES: readonly YouBadge[] = [
  { slug: "first-pebble", family: "pebble_count", title: "First pebble" },
  { slug: "emotion-first-joy", family: "emotion_first", title: "First joy" },
  { slug: "first-collection", family: "first_collection", title: "First collection" },
  { slug: "first-soul", family: "first_soul", title: "First soul" },
  { slug: "first-glyph", family: "first_glyph", title: "First glyph" },
  { slug: "pebbles-10", family: "pebble_count", title: "Ten pebbles" },
]

function log(id: string, species: Log["species"], title: string, summary: string, date: string): Log {
  return {
    id,
    species,
    platform: "all",
    status: "shipped",
    title_en: title,
    title_fr: null,
    summary_en: summary,
    summary_fr: null,
    body_md_en: null,
    body_md_fr: null,
    cover_image_path: null,
    external_url: null,
    published: true,
    published_at: date,
    released_at: species === "feature" ? date : null,
    created_at: date,
    reaction_count: 0,
  }
}

export const DESIGN_LAB_ANNOUNCEMENT: Log = log(
  "design-announcement",
  "announcement",
  "Home has a home",
  "Your month at a glance, a greeting, and three ways to keep a moment.",
  "2026-10-31T09:00:00Z",
)

export const DESIGN_LAB_CHANGELOG: readonly Log[] = [
  log("design-feature-1", "feature", "Monthly wraps", "Each finished month folds into a card.", "2026-10-29T09:00:00Z"),
  log("design-feature-2", "feature", "Achievements", "Badges along your path, with a wheel of firsts.", "2026-10-24T09:00:00Z"),
  log("design-feature-3", "feature", "Lit stones", "Your pebbles catch the light.", "2026-10-18T09:00:00Z"),
]
