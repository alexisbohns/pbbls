import type { ParsedPath } from "./parse"

/**
 * A glyph's roles (spec D2), as indices into its paths: the body (unioned),
 * the marks in fill order (`marks[0]` is mark 1, possibly several paths) and
 * the focus. Untagged paths are plain lines. `hidden` lists the mark and focus
 * paths that carry `data-rest="hidden"`: not drawn at rest, drawn filled when
 * their role fills.
 */
export type GlyphRoles = { body: number[]; marks: number[][]; focus: number[]; hidden: number[] }

export const ROLES = ["body", "mark", "focus"] as const

export function readRoles(paths: readonly ParsedPath[]): { roles: GlyphRoles; issues: string[] } {
  const issues: string[] = []
  const body: number[] = []
  const focus: number[] = []
  const hidden: number[] = []
  const byMark = new Map<number, number[]>()
  paths.forEach((path, i) => {
    const where = `path ${i + 1}`
    const role = path.attrs["data-role"]
    const mark = path.attrs["data-mark"]
    const rest = path.attrs["data-rest"]
    if (rest !== undefined) {
      if (role !== "mark" && role !== "focus") issues.push(`${where}: data-rest is only allowed on a mark or focus`)
      else if (rest !== "hidden") issues.push(`${where}: data-rest="${rest}" is not hidden`)
      else hidden.push(i)
    }
    if (role === undefined) {
      if (mark !== undefined) issues.push(`${where}: data-mark without data-role="mark"`)
      return
    }
    if (role === "body") body.push(i)
    else if (role === "focus") focus.push(i)
    else if (role === "mark") {
      const n = mark === undefined ? NaN : Number(mark)
      if (!Number.isInteger(n) || n < 1) {
        issues.push(`${where}: a mark needs data-mark`)
        return
      }
      byMark.set(n, [...(byMark.get(n) ?? []), i])
    } else issues.push(`${where}: data-role="${role}" is not body, mark or focus`)
    if (role !== "mark" && mark !== undefined) issues.push(`${where}: data-mark without data-role="mark"`)
  })
  const numbers = [...byMark.keys()].sort((a, b) => a - b)
  if (numbers.some((n, i) => n !== i + 1)) issues.push(`marks must run 1…n without gaps; found ${numbers.join(", ")}`)
  const marks = numbers.map((n) => byMark.get(n) ?? [])
  return { roles: { body, marks, focus, hidden }, issues }
}
