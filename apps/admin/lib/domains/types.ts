// apps/admin/lib/domains/types.ts
import type { GlyphStroke } from "@/lib/pebblestore/types"

/** A row from the admin_list_domains RPC. */
export type AdminDomain = {
  id: string
  slug: string
  name: string
  label: string
  default_glyph_id: string | null
  strokes: GlyphStroke[] | null
  view_box: string | null
}

/** Map the SQL error contract to English admin copy. */
export function domainErrorMessage(code: string): string {
  switch (code) {
    case "not_admin":
      return "You are not authorized to perform this action."
    case "not_found":
      return "That domain no longer exists."
    case "bad_name":
      return "A name is required."
    case "empty_glyph":
      return "This glyph has no usable strokes to save."
    default:
      return "Something went wrong. Check the server console for details."
  }
}
