import type { ParsedPath } from "./parse"

/**
 * A glyph's roles (spec D2), as indices into its paths: the body (unioned) and
 * its parts in source order, the mark, and the steps in fill order (`steps[0]`
 * is step 1, possibly several paths). Untagged paths are plain lines. `hidden`
 * lists the mark and step paths that carry `data-rest="hidden"`: not drawn at
 * rest, drawn filled when their role fills.
 */
export type GlyphRoles = {
  body: number[]
  /** The body's parts in the order they first appear; empty when the body is whole. */
  parts: { name: string; paths: number[] }[]
  mark: number[]
  steps: number[][]
  hidden: number[]
}

export const ROLES = ["body", "mark", "step"] as const
/** A body splits into 2…4 parts at most: 4 parts already give 14 combinations. */
export const MAX_PARTS = 4
const PART_NAME = /^[a-z]+$/

export function readRoles(paths: readonly ParsedPath[]): { roles: GlyphRoles; issues: string[] } {
  const issues: string[] = []
  const body: number[] = []
  const mark: number[] = []
  const hidden: number[] = []
  const parts: { name: string; paths: number[] }[] = []
  let unnamed = 0
  const bySteps = new Map<number, number[]>()
  paths.forEach((path, i) => {
    const where = `path ${i + 1}`
    const role = path.attrs["data-role"]
    const step = path.attrs["data-step"]
    const part = path.attrs["data-part"]
    const rest = path.attrs["data-rest"]
    if (rest !== undefined) {
      if (role !== "mark" && role !== "step") issues.push(`${where}: data-rest is only allowed on a mark or step`)
      else if (rest !== "hidden") issues.push(`${where}: data-rest="${rest}" is not hidden`)
      else hidden.push(i)
    }
    if (step !== undefined && role !== "step") issues.push(`${where}: data-step without data-role="step"`)
    if (part !== undefined && role !== "body") issues.push(`${where}: data-part without data-role="body"`)
    if (role === undefined) return
    if (role === "body") {
      body.push(i)
      if (part === undefined) unnamed += 1
      else if (!PART_NAME.test(part)) issues.push(`${where}: data-part="${part}" is not one lowercase word`)
      else {
        const known = parts.find((p) => p.name === part)
        if (known) known.paths.push(i)
        else parts.push({ name: part, paths: [i] })
      }
    } else if (role === "mark") mark.push(i)
    else if (role === "step") {
      const n = step === undefined ? NaN : Number(step)
      if (!Number.isInteger(n) || n < 1) {
        issues.push(`${where}: a step needs data-step`)
        return
      }
      bySteps.set(n, [...(bySteps.get(n) ?? []), i])
    } else issues.push(`${where}: data-role="${role}" is not body, mark or step`)
  })
  if (parts.length > 0 && unnamed > 0) issues.push("when one body path names a part, every body path does")
  if (parts.length === 1) issues.push(`a body with one part (${parts[0]?.name}) is a whole body: drop data-part`)
  if (parts.length > MAX_PARTS) issues.push(`${parts.length} parts; a body has at most ${MAX_PARTS}`)
  const numbers = [...bySteps.keys()].sort((a, b) => a - b)
  if (numbers.some((n, i) => n !== i + 1)) issues.push(`steps must run 1…n without gaps; found ${numbers.join(", ")}`)
  const steps = numbers.map((n) => bySteps.get(n) ?? [])
  return { roles: { body, parts: parts.length >= 2 ? parts : [], mark, steps, hidden }, issues }
}

/**
 * Every combination of parts short of all of them (all of them is the solid),
 * smallest first, each in the parts' order: [left], [middle], [left, middle], …
 */
export function partCombinations(names: readonly string[]): string[][] {
  const out: string[][] = []
  const n = names.length
  for (let bits = 1; bits < (1 << n) - 1; bits += 1) out.push(names.filter((_, i) => bits & (1 << i)))
  return out.sort((a, b) => a.length - b.length)
}
