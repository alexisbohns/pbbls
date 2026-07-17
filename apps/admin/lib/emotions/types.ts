// apps/admin/lib/emotions/types.ts

/** A row from the admin_list_emotion_categories RPC. All colours are 8-digit hex (#RRGGBBAA). */
export type AdminEmotionCategory = {
  id: string
  slug: string
  name: string
  primary_color: string
  secondary_color: string
  light_color: string
  surface_color: string
  shaded_color: string
  dark_color: string
}

/** A row from the admin_list_emotions RPC. */
export type AdminEmotion = {
  id: string
  slug: string
  name: string
  emoji: string
  category_id: string
  category_slug: string
  category_name: string
  category_primary_color: string
}

/**
 * The five hand-tuned palette variants exposed in the editor (#608).
 * surface_color is derived from primary server-side and is not edited here.
 */
export const PALETTE_VARIANTS = [
  { key: "primary_color", label: "Primary" },
  { key: "secondary_color", label: "Secondary" },
  { key: "light_color", label: "Light" },
  { key: "shaded_color", label: "Shaded" },
  { key: "dark_color", label: "Dark" },
] as const

export type PaletteVariantKey = (typeof PALETTE_VARIANTS)[number]["key"]

/** Map the SQL error contract to English admin copy. */
export function emotionErrorMessage(code: string): string {
  switch (code) {
    case "not_admin":
      return "You are not authorized to perform this action."
    case "not_found":
      return "That record no longer exists."
    case "bad_color":
      return "Each colour must be an 8-digit hex value (#RRGGBBAA)."
    case "bad_emoji":
      return "An emoji is required."
    default:
      return "Something went wrong. Check the server console for details."
  }
}
