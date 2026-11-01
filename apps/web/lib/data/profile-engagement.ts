import type { ProfileEngagement } from "@/lib/types"

function field(row: unknown, key: string): unknown {
  return typeof row === "object" && row !== null ? Reflect.get(row, key) : undefined
}

/** A served count: a non-negative integer, else null (absent, null or the wrong type). */
function count(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null
}

/**
 * `get_profile_engagement`'s row (#1384 D17). The RPC `returns table(...)`,
 * so PostgREST yields an array of one row. Days and assiduity decode as they
 * always have (0 and [] when missing); the glyph counts #1386 added decode to
 * null when a server does not send them, so You shows its line without a
 * number rather than a wrong one. Never throws.
 */
export function decodeProfileEngagement(raw: unknown): ProfileEngagement {
  const row: unknown = Array.isArray(raw) ? raw[0] : raw
  const days = field(row, "days_practiced")
  const assiduity = field(row, "assiduity")
  return {
    daysPracticed: typeof days === "number" ? days : 0,
    assiduity: Array.isArray(assiduity) ? assiduity.map((day) => day === true) : [],
    glyphsCarved: count(field(row, "glyphs_carved")),
    glyphsOwned: count(field(row, "glyphs_owned")),
  }
}
