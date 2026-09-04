// apps/admin/lib/achievements/types.ts
import type { GlyphStroke } from "@/lib/pebblestore/types"

/** A row from the admin_list_achievements RPC. */
export type AdminAchievement = {
  id: string
  slug: string
  family: string
  threshold: number | null
  emotion_id: string | null
  emotion_slug: string | null
  domain_id: string | null
  domain_slug: string | null
  sort_order: number
  glyph_id: string | null
  strokes: GlyphStroke[] | null
  view_box: string | null
  karma_reward: number
  is_active: boolean
  title_en: string | null
  title_fr: string | null
  description_en: string | null
  description_fr: string | null
  unlock_count: number
}

/**
 * The eight families `check_achievements()` can evaluate. The list is a mirror
 * of the column's CHECK constraint — a ninth family needs a migration and a new
 * `case` arm in that function, so the admin can only add tiers within these.
 */
export const ACHIEVEMENT_FAMILIES = [
  "pebble_count",
  "emotion_first",
  "domain_first",
  "first_collection",
  "first_glyph",
  "first_soul",
  "glyph_count",
  "glyph_sales",
] as const

export type AchievementFamily = (typeof ACHIEVEMENT_FAMILIES)[number]

export const FAMILY_LABELS: Record<AchievementFamily, string> = {
  pebble_count: "Pebble count",
  emotion_first: "First of an emotion",
  domain_first: "First of a domain",
  first_collection: "First collection",
  first_glyph: "First glyph",
  first_soul: "First soul",
  glyph_count: "Glyphs carved",
  glyph_sales: "Glyph sales",
}

/** Families counting up to a threshold; the rest are one-shot. */
export const COUNTING_FAMILIES: readonly AchievementFamily[] = [
  "pebble_count",
  "glyph_count",
  "glyph_sales",
]

/** Families whose badge is bound to one reference row. */
export const REFERENCE_FAMILIES: readonly AchievementFamily[] = [
  "emotion_first",
  "domain_first",
]

export function familyLabel(family: string): string {
  return FAMILY_LABELS[family as AchievementFamily] ?? family
}

/** Map the SQL error contract to English admin copy. */
export function achievementErrorMessage(code: string): string {
  switch (code) {
    case "not_admin":
      return "You are not authorized to perform this action."
    case "not_found":
      return "That achievement no longer exists."
    case "bad_slug":
      return "Slugs are lowercase words joined by hyphens, e.g. pebble-count-2000."
    case "duplicate_slug":
      return "An achievement with that slug already exists."
    case "missing_copy":
      return "Both the English and French titles are required."
    case "bad_karma":
      return "Karma must be between 0 and 32767."
    case "bad_threshold":
      return "Counting families need a positive threshold; one-shot families must not have one."
    case "missing_reference":
      return "Pick the emotion or domain this badge belongs to."
    case "has_unlocks":
      return "Users have already unlocked this badge. Retire it instead of deleting it."
    case "empty_glyph":
      return "This glyph has no usable strokes to save."
    default:
      return "Something went wrong. Check the server console for details."
  }
}
