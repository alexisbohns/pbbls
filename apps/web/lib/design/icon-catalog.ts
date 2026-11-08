import * as PbblsIcons from "@pbbls/icons"
import manifest from "@pbbls/icons/icons.json"
import type { PbblsIconComponent } from "@/lib/icons/pbbls-icon"

/**
 * The hand-drawn set as the design page lists it: every row of
 * packages/icons/icons.json with its generated React component and twins.
 */

export type IconSurface = "web" | "ios" | "android"

export interface IconTwin {
  readonly variant: string
  readonly Icon: PbblsIconComponent | null
}

export interface IconRow {
  readonly name: string
  readonly meaning: string
  readonly wave: string
  readonly status: string
  readonly Icon: PbblsIconComponent | null
  readonly twins: readonly IconTwin[]
  readonly migrated: readonly string[]
  /** The Lucide names this drawing replaces on web. */
  readonly replacesWeb: readonly string[]
}

const COMPONENTS: ReadonlyMap<string, PbblsIconComponent> = new Map(Object.entries(PbblsIcons))

/** `check-circle-dot` → `CheckCircleDot`, `path` + `lit-1` → `PathLit1`: the generator's naming. */
export function componentName(name: string, variant?: string): string {
  return [name, variant ?? ""]
    .join("-")
    .split("-")
    .filter(Boolean)
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join("")
}

export const ICON_ROWS: readonly IconRow[] = manifest.icons.map((row) => ({
  name: row.name,
  meaning: row.meaning,
  wave: row.wave,
  status: row.status,
  Icon: COMPONENTS.get(componentName(row.name)) ?? null,
  twins: row.variants.map((variant) => ({ variant, Icon: COMPONENTS.get(componentName(row.name, variant)) ?? null })),
  migrated: row.migrated,
  replacesWeb: row.replaces.web,
}))

/**
 * One drawing on its own: a base (`home`) or a twin (`home.active`, `path.lit-3`),
 * named like its source file. The page lists glyphs, not rows: a twin is the
 * base run through a state, but it is drawn, shipped and used on its own.
 */
export interface IconGlyph {
  readonly name: string
  readonly variant: string | null
  readonly Icon: PbblsIconComponent | null
  readonly row: IconRow
}

export const ICON_GLYPHS: readonly IconGlyph[] = ICON_ROWS.flatMap((row) => [
  { name: row.name, variant: null, Icon: row.Icon, row },
  ...row.twins.map((twin) => ({ name: `${row.name}.${twin.variant}`, variant: twin.variant, Icon: twin.Icon, row })),
])

/** A glyph matches on its name, its row's meaning, or a Lucide name its row replaces on web. */
export function glyphMatches(glyph: IconGlyph, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return (
    glyph.name.includes(q) ||
    glyph.row.meaning.toLowerCase().includes(q) ||
    glyph.row.replacesWeb.some((r) => r.toLowerCase().includes(q))
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
