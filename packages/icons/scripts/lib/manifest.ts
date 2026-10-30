import { readFileSync } from "node:fs"
import { join } from "node:path"

export type Status = "exploring" | "candidate" | "shipped"
export const STATUSES: readonly Status[] = ["exploring", "candidate", "shipped"]

export type Variant = "active" | "lit-1" | "lit-2" | "lit-3"
export const VARIANTS: readonly Variant[] = ["active", "lit-1", "lit-2", "lit-3"]

export type Surface = "ios" | "android" | "web"
export const SURFACES: readonly Surface[] = ["ios", "android", "web"]

export type IconRow = {
  name: string
  meaning: string
  variants: string[]
  wave: string
  status: string
  /** The surfaces whose code no longer names this row's `replaces` (the migration lint reads it). */
  migrated: Surface[]
  replaces: Record<Surface, string[]>
}

export type Manifest = { icons: IconRow[] }

/** One emitted icon: a base row, or one of its twins. */
export type EmittedRow = {
  /** `home` or `home.active`. */
  name: string
  /** The base row's name (`home` for both). */
  base: string
  variant: Variant | null
  meaning: string
  row: IconRow
}

export const MANIFEST_FILE = "icons.json"

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v)

const isStringArray = (v: unknown): v is string[] => Array.isArray(v) && v.every((x) => typeof x === "string")

const isSurface = (v: string): v is Surface => (SURFACES as readonly string[]).includes(v)

function rowFrom(value: unknown, index: number): IconRow {
  const where = `${MANIFEST_FILE}: icons[${index}]`
  if (!isRecord(value)) throw new Error(`${where} is not an object`)
  const { name, meaning, variants, wave, status, migrated, replaces } = value
  if (typeof name !== "string") throw new Error(`${where}.name is not a string`)
  if (typeof meaning !== "string") throw new Error(`${where} (${name}).meaning is not a string`)
  if (!isStringArray(variants)) throw new Error(`${where} (${name}).variants is not a string array`)
  if (typeof wave !== "string") throw new Error(`${where} (${name}).wave is not a string`)
  if (typeof status !== "string") throw new Error(`${where} (${name}).status is not a string`)
  if (!isStringArray(migrated)) throw new Error(`${where} (${name}).migrated is not a string array`)
  const migratedSurfaces: Surface[] = []
  for (const surface of migrated) {
    if (!isSurface(surface)) throw new Error(`${where} (${name}).migrated: ${surface} is not a surface`)
    if (migratedSurfaces.includes(surface)) throw new Error(`${where} (${name}).migrated: ${surface} twice`)
    migratedSurfaces.push(surface)
  }
  if (status === "shipped") {
    const missing = SURFACES.filter((s) => !migratedSurfaces.includes(s))
    if (missing.length > 0) throw new Error(`${where} (${name}): shipped, but migrated lacks ${missing.join(", ")}`)
  }
  if (!isRecord(replaces)) throw new Error(`${where} (${name}).replaces is not an object`)
  const lists = {} as Record<Surface, string[]>
  for (const surface of SURFACES) {
    const list = replaces[surface]
    if (!isStringArray(list)) throw new Error(`${where} (${name}).replaces.${surface} is not a string array`)
    lists[surface] = list
  }
  return { name, meaning, variants, wave, status, migrated: migratedSurfaces, replaces: lists }
}

/** Shape-checks a parsed `icons.json`; every failure names the row. */
export function parseManifest(json: unknown): Manifest {
  if (!isRecord(json) || !Array.isArray(json.icons)) throw new Error(`${MANIFEST_FILE}: expected { "icons": [...] }`)
  return { icons: json.icons.map(rowFrom) }
}

/** Reads `<root>/icons.json`, where `root` is the package directory. */
export function loadManifest(root: string): Manifest {
  const file = join(root, MANIFEST_FILE)
  return parseManifest(JSON.parse(readFileSync(file, "utf8")))
}

const isVariant = (v: string): v is Variant => (VARIANTS as readonly string[]).includes(v)

/**
 * The rows the generator emits, in manifest order: every row that is not
 * `exploring`, each followed by its twins in `variants` order. Unknown variants
 * are skipped here; the lint reports them.
 */
export function emitted(manifest: Manifest): EmittedRow[] {
  const out: EmittedRow[] = []
  for (const row of manifest.icons) {
    if (row.status === "exploring") continue
    out.push({ name: row.name, base: row.name, variant: null, meaning: row.meaning, row })
    for (const variant of row.variants) {
      if (!isVariant(variant)) continue
      out.push({ name: `${row.name}.${variant}`, base: row.name, variant, meaning: row.meaning, row })
    }
  }
  return out
}
