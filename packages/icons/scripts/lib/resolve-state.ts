import type { Form } from "./forms"

/** A state's forms, first available wins (spec D6). */
export type StateTable = Record<string, string[]>

/** A state table that is only read: `StateTable`, or the generated `as const` one. */
export type ReadonlyStateTable = Readonly<Record<string, readonly string[]>>

/** The form a state shows on a glyph whose forms are `forms` (from `formsOf`). */
export function resolveState(table: ReadonlyStateTable, forms: readonly string[], state: string, k = 0): Form {
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
