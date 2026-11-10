import { readFileSync } from "node:fs"
import { join } from "node:path"

/** `categories.json`: the shelves the manifest's `tags.category` ids point at (spec D5). */
export type Category = { id: string; label: string; description: string }

export const CATEGORIES_FILE = "categories.json"

/** A row carries one to three categories, the first the main one. */
export const MAX_CATEGORIES = 3

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v)

/** Shape-checks a parsed `categories.json`; every failure names the entry. */
export function parseCategories(json: unknown): Category[] {
  if (!isRecord(json) || !Array.isArray(json.categories)) throw new Error(`${CATEGORIES_FILE}: expected { "categories": [...] }`)
  const seen = new Set<string>()
  return json.categories.map((value: unknown, index) => {
    const where = `${CATEGORIES_FILE}: categories[${index}]`
    if (!isRecord(value)) throw new Error(`${where} is not an object`)
    const { id, label, description } = value
    if (typeof id !== "string" || id === "") throw new Error(`${where}.id is not a non-empty string`)
    if (seen.has(id)) throw new Error(`${where}: "${id}" is listed twice`)
    seen.add(id)
    if (typeof label !== "string" || label === "") throw new Error(`${where} (${id}).label is not a non-empty string`)
    if (typeof description !== "string" || description === "") throw new Error(`${where} (${id}).description is not a non-empty string`)
    return { id, label, description }
  })
}

/** Reads `<root>/categories.json`, where `root` is the package directory. */
export function loadCategories(root: string): Category[] {
  const file = join(root, CATEGORIES_FILE)
  let text: string
  try {
    text = readFileSync(file, "utf8")
  } catch {
    throw new Error(`${CATEGORIES_FILE}: cannot read ${file}`)
  }
  let json: unknown
  try {
    json = JSON.parse(text)
  } catch (e) {
    throw new Error(`${CATEGORIES_FILE}: not valid JSON (${e instanceof Error ? e.message : String(e)})`)
  }
  return parseCategories(json)
}
