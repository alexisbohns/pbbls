import { readFileSync } from "node:fs"
import { join } from "node:path"
import { type StateTable } from "./resolve-state"

export { resolveState, type StateTable } from "./resolve-state"

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
