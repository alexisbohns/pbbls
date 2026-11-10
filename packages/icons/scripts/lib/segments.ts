import { tokenizePath } from "./lint"

/** One drawing command of normalised path data, with its numbers in order. */
export type Segment = { op: "M" | "L" | "C" | "Q" | "Z"; pts: number[] }

const ARITY: Record<string, number> = { M: 2, L: 2, C: 6, Q: 4 }

/**
 * Normalised path data (absolute `M L C Q Z`, spec D7) as the segments every
 * surface must read (`fixtures/path-contract.json`). Extra coordinate groups
 * repeat their command, an `M`'s as `L`.
 */
export function segmentsOf(d: string): Segment[] {
  const out: Segment[] = []
  for (const { command, numbers } of tokenizePath(d)) {
    if (command === "Z") {
      out.push({ op: "Z", pts: [] })
      continue
    }
    const arity = ARITY[command]
    if (arity === undefined) throw new Error(`segmentsOf: ${command} is not normalised (absolute M L C Q Z only)`)
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const op: Segment["op"] = command === "M" ? (i === 0 ? "M" : "L") : command === "L" ? "L" : command === "C" ? "C" : "Q"
      out.push({ op, pts: numbers.slice(i, i + arity) })
    }
  }
  return out
}
