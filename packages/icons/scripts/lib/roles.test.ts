import { describe, expect, it } from "vitest"
import type { ParsedPath } from "./parse"
import { readRoles } from "./roles"

const p = (attrs: Record<string, string>): ParsedPath => ({ d: attrs.d ?? "M3 3 L4 4", filled: false, evenOdd: false, unstroked: false, attrs: { d: "M3 3 L4 4", ...attrs } })

describe("readRoles", () => {
  it("collects body, numbered marks and focus by path index", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body" }),
      p({}),
      p({ "data-role": "mark", "data-mark": "2" }),
      p({ "data-role": "mark", "data-mark": "1" }),
      p({ "data-role": "mark", "data-mark": "1" }),
      p({ "data-role": "focus" }),
    ])
    expect(issues).toEqual([])
    expect(roles).toEqual({ body: [0], marks: [[3, 4], [2]], focus: [5], hidden: [] })
  })

  it("has no roles for a plain drawing", () => {
    expect(readRoles([p({}), p({})]).roles).toEqual({ body: [], marks: [], focus: [], hidden: [] })
  })

  it("reports unknown roles, missing or stray mark numbers and gaps", () => {
    const { issues } = readRoles([
      p({ "data-role": "halo" }),
      p({ "data-role": "mark" }),
      p({ "data-mark": "1" }),
      p({ "data-role": "mark", "data-mark": "3" }),
    ])
    expect(issues).toEqual([
      'path 1: data-role="halo" is not body, mark or focus',
      "path 2: a mark needs data-mark",
      "path 3: data-mark without data-role=\"mark\"",
      "marks must run 1…n without gaps; found 3",
    ])
  })

  it("collects the hidden mark and focus paths", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body" }),
      p({ "data-role": "mark", "data-mark": "1", "data-rest": "hidden" }),
      p({ "data-role": "focus", "data-rest": "hidden" }),
    ])
    expect(issues).toEqual([])
    expect(roles.hidden).toEqual([1, 2])
    expect(roles.marks).toEqual([[1]])
    expect(roles.focus).toEqual([2])
  })

  it("reports data-rest on a body or a plain line, and any value but hidden", () => {
    const { issues } = readRoles([
      p({ "data-role": "body", "data-rest": "hidden" }),
      p({ "data-rest": "hidden" }),
      p({ "data-role": "focus", "data-rest": "shown" }),
    ])
    expect(issues).toEqual([
      "path 1: data-rest is only allowed on a mark or focus",
      "path 2: data-rest is only allowed on a mark or focus",
      'path 3: data-rest="shown" is not hidden',
    ])
  })
})
