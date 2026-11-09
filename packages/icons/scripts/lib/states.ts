import { readFileSync } from "node:fs"
import { join } from "node:path"
import type { Form } from "./forms"

/** A state's forms, first available wins (spec D6). */
export type StateTable = Record<string, string[]>

export const STATES_FILE = "states.json"
const FORM_WORDS = new Set(["regular", "marked", "solid", "progress-n", "progress-k"])

export function parseStates(json: unknown): StateTable {
  if (typeof json !== "object" || json === null || !("states" in json)) throw new Error(`${STATES_FILE}: expected { "states": {...} }`)
  const states = (json as { states: unknown }).states
  if (typeof states !== "object" || states === null) throw new Error(`${STATES_FILE}: states is not an object`)
  const table: StateTable = {}
  for (const [state, list] of Object.entries(states)) {
    if (!Array.isArray(list) || !list.every((f): f is string => typeof f === "string")) throw new Error(`${STATES_FILE}: ${state} is not a string array`)
    for (const f of list) if (!FORM_WORDS.has(f)) throw new Error(`${STATES_FILE}: ${state}: unknown form ${f}`)
    if (list[list.length - 1] !== "regular") throw new Error(`${STATES_FILE}: ${state} must end with regular, the form every glyph has`)
    table[state] = list
  }
  return table
}

export function loadStates(packageRoot: string): StateTable {
  return parseStates(JSON.parse(readFileSync(join(packageRoot, STATES_FILE), "utf8")))
}

/** The form a state shows on a glyph whose forms are `forms` (from `formsOf`). */
export function resolveState(table: StateTable, forms: readonly string[], state: string, k = 0): Form {
  const list = table[state]
  if (!list) throw new Error(`unknown state ${state}`)
  const n = forms.filter((f) => /^progress-\d+$/.test(f)).length
  for (const word of list) {
    if (word === "regular") return { fill: { kind: "regular" } }
    if (word === "marked" && forms.includes("marked")) return { fill: { kind: "regular" }, marked: true }
    if (word === "solid" && forms.includes("solid")) return { fill: { kind: "solid" } }
    if (word === "progress-n" && n > 0) return { fill: { kind: "progress", k: n } }
    if (word === "progress-k" && n > 0) return { fill: { kind: "progress", k: Math.max(0, Math.min(Number.isInteger(k) ? k : 0, n)) } }
  }
  return { fill: { kind: "regular" } }
}
