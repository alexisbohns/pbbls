import type { EmotionPalette } from "@/lib/data/useEmotionPalettes"
import { EMOTIONS } from "@/lib/config/emotions"

/**
 * The real `emotion_categories` palettes, as exported from Supabase Studio.
 *
 * They are not in any migration — `20260717000000_emotion_categories_shaded_dark.sql`
 * is schema-only, and the values are hand-entered per category. Copied here so the
 * sandbox renders true colours with no network; not a source of truth, and expected
 * to drift if someone re-tunes a category in Studio.
 */
const CATEGORY_PALETTES: Record<string, EmotionPalette> = {
  fear:    { primary_color: "#7B5E99FF", secondary_color: "#AE91CCFF", light_color: "#F2EFF5FF", surface_color: "#7B5E991A" },
  sadness: { primary_color: "#59658AFF", secondary_color: "#8C98BDFF", light_color: "#EEF0F3FF", surface_color: "#59658A1A" },
  pride:   { primary_color: "#A9478AFF", secondary_color: "#EA91CEFF", light_color: "#F6EDF3FF", surface_color: "#A9478A1A" },
  joy:     { primary_color: "#A15C08FF", secondary_color: "#CF8C39FF", light_color: "#FAF6EAFF", surface_color: "#A15C081A" },
  peace:   { primary_color: "#487C5AFF", secondary_color: "#80BF96FF", light_color: "#EDF2EEFF", surface_color: "#487C5A1A" },
  anger:   { primary_color: "#8E4242FF", secondary_color: "#C17575FF", light_color: "#F4ECECFF", surface_color: "#8E42421A" },
  shame:   { primary_color: "#868686FF", secondary_color: "#B9B9B9FF", light_color: "#F3F3F3FF", surface_color: "#8686861A" },
}

/**
 * The `dark_color` column, which `EmotionPalette` does not carry — the app's
 * dark-mode swap happens in CSS, so no consumer has ever needed the value in JS.
 * The sandbox does: its stones pick their fill in JS from the toolbar's theme
 * state, so both ends of the swap have to be reachable from here.
 */
const CATEGORY_DARK: Record<string, string> = {
  fear: "#19131FFF",
  sadness: "#12141CFF",
  pride: "#220E1CFF",
  joy: "#201202FF",
  peace: "#0E1912FF",
  anger: "#1C0D0DFF",
  shame: "#1B1B1BFF",
}

/**
 * `EMOTIONS[].color` predates emotion categories and carries exactly one value per
 * category — seven colours, seven categories, a clean 1:1. Joining on it is what
 * lets the fixture reach the real palette without a `category_id` the static config
 * never had. If a future emotion arrives with an eighth colour it simply falls out
 * of the map, and its pebbles render untinted in the sandbox — visible, not silent.
 */
const CATEGORY_BY_LEGACY_COLOR: Record<string, string> = {
  "#8B5CF6": "fear",
  "#60A5FA": "sadness",
  "#78716C": "shame",
  "#EEEEEE": "peace",
  "#EF4444": "anger",
  "#F97316": "pride",
  "#FACC15": "joy",
}

/** Every emotion id → its category's palette. Built once at module scope. */
export const SANDBOX_PALETTES: Map<string, EmotionPalette> = new Map(
  EMOTIONS.flatMap((emotion) => {
    const category = CATEGORY_BY_LEGACY_COLOR[emotion.color]
    const palette = category ? CATEGORY_PALETTES[category] : undefined
    return palette ? [[emotion.id, palette] as const] : []
  }),
)

/** Every emotion id → its category's `dark_color`. */
export const SANDBOX_DARK_COLORS: Map<string, string> = new Map(
  EMOTIONS.flatMap((emotion) => {
    const category = CATEGORY_BY_LEGACY_COLOR[emotion.color]
    const dark = category ? CATEGORY_DARK[category] : undefined
    return dark ? [[emotion.id, dark] as const] : []
  }),
)

/** Pick an emotion id by slug, for the fixture's readability. */
export function emotionId(slug: string): string {
  const match = EMOTIONS.find((e) => e.slug === slug)
  if (!match) throw new Error(`[sandbox] unknown emotion slug: ${slug}`)
  return match.id
}
