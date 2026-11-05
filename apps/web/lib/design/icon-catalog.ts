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

/** `Trash2Icon`, `LucideTrash2` and `Trash2` are one drawing. */
export function lucideBaseName(name: string): string {
  return name.replace(/^Lucide(?=[A-Z])/, "").replace(/(?<=.)Icon$/, "")
}

/** The hand-drawn rows that replace a Lucide icon on web, from the manifest's `replaces.web`. */
export function replacementsFor(lucideName: string): readonly IconRow[] {
  const base = lucideBaseName(lucideName)
  return ICON_ROWS.filter((row) => row.replacesWeb.some((r) => lucideBaseName(r) === base))
}
