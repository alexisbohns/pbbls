import manifest from "@pbbls/icons/icons.json"
import { GLYPHS, type Glyph } from "@/lib/design/glyph-forms"

/**
 * The hand-drawn set as the design page lists it: every row of
 * packages/icons/icons.json with its drawing from glyphs.json, which the page
 * draws through the package's `GlyphSvg` (the forms it reviews replace the twins).
 */

export type IconSurface = "web" | "ios" | "android"

export interface IconRow {
  readonly name: string
  readonly meaning: string
  readonly wave: string
  readonly status: string
  /** The row's drawing, null while the generator has none. */
  readonly glyph: Glyph | null
  readonly migrated: readonly string[]
  /** The Lucide names this drawing replaces on web. */
  readonly replacesWeb: readonly string[]
}

const BY_NAME: ReadonlyMap<string, Glyph> = new Map(GLYPHS.map((g) => [g.name, g]))

export const ICON_ROWS: readonly IconRow[] = manifest.icons.map((row) => ({
  name: row.name,
  meaning: row.meaning,
  wave: row.wave,
  status: row.status,
  glyph: BY_NAME.get(row.name) ?? null,
  migrated: row.migrated,
  replacesWeb: row.replaces.web,
}))

/** A row matches on its name, its meaning, or a Lucide name it replaces on web. */
export function glyphMatches(row: IconRow, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return (
    row.name.includes(q) ||
    row.meaning.toLowerCase().includes(q) ||
    row.replacesWeb.some((r) => r.toLowerCase().includes(q))
  )
}

/** `Trash2Icon`, `LucideTrash2` and `Trash2` are one drawing. */
export function lucideBaseName(name: string): string {
  return name.replace(/^Lucide(?=[A-Z])/, "").replace(/(?<=.)Icon$/, "")
}

/** The hand-drawn rows that replace a Lucide icon on web, from the manifest's `replaces.web`. */
export function replacementsFor(lucideName: string): readonly IconRow[] {
  const base = lucideBaseName(lucideName)
  return ICON_ROWS.filter((row) => row.replacesWeb.some((r) => lucideBaseName(r) === base))
}
