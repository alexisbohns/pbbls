import { readFileSync } from "node:fs"
import { join } from "node:path"
import { parseFormKey } from "./forms"

export type Status = "exploring" | "candidate" | "shipped"
export const STATUSES: readonly Status[] = ["exploring", "candidate", "shipped"]

/** `src/<name>.dotted.svg`: the drawn dotted form (spec D3, D4), not an override. */
export const DOTTED_FORM = "dotted"

/** An override file's form (spec D4): any form key but `regular` and `dotted`. */
export const isOverrideForm = (v: string): boolean => v !== "regular" && v !== "dotted" && parseFormKey(v) !== null

export type Surface = "ios" | "android" | "web"
export const SURFACES: readonly Surface[] = ["ios", "android", "web"]

/** The tag keys a row may carry (spec D5). Tags help search; code never reads them. */
export const TAG_KEYS = ["subject", "wrapper", "number", "side", "direction", "size", "keywords", "category"] as const
export type TagKey = (typeof TAG_KEYS)[number]
export type Tags = Partial<Record<TagKey, string[]>>

const isTagKey = (v: string): v is TagKey => (TAG_KEYS as readonly string[]).includes(v)

export type IconRow = {
  name: string
  meaning: string
  /** Groups versions of one idea (`knot` for `knot-sm` and `knot-lg`); null when alone. */
  family: string | null
  tags: Tags
  wave: string
  status: string
  /** The surfaces whose code no longer names this row's `replaces` (the migration lint reads it). */
  migrated: Surface[]
  replaces: Record<Surface, string[]>
}

export type Manifest = { icons: IconRow[] }

/** One emitted icon: a row that is not `exploring`. */
export type EmittedRow = {
  name: string
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
  const { name, meaning, wave, status, migrated, replaces, family, tags } = value
  if (typeof name !== "string") throw new Error(`${where}.name is not a string`)
  if (typeof meaning !== "string") throw new Error(`${where} (${name}).meaning is not a string`)
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
  if (family !== undefined && family !== null && typeof family !== "string") throw new Error(`${where} (${name}).family is not a string`)
  const tagRecord: Tags = {}
  if (tags !== undefined) {
    if (!isRecord(tags)) throw new Error(`${where} (${name}).tags is not an object`)
    for (const [key, list] of Object.entries(tags)) {
      if (!isTagKey(key)) throw new Error(`${where} (${name}).tags.${key} is not a tag key (${TAG_KEYS.join(", ")})`)
      if (!isStringArray(list)) throw new Error(`${where} (${name}).tags.${key} is not a string array`)
      tagRecord[key] = list
    }
  }
  return { name, meaning, family: typeof family === "string" ? family : null, tags: tagRecord, wave, status, migrated: migratedSurfaces, replaces: lists }
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

/** The rows the generator emits, in manifest order: every row that is not `exploring`. */
export function emitted(manifest: Manifest): EmittedRow[] {
  return manifest.icons.filter((row) => row.status !== "exploring").map((row) => ({ name: row.name, meaning: row.meaning, row }))
}
