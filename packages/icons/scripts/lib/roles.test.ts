import { describe, expect, it } from "vitest"
import type { ParsedPath } from "./parse"
import { partCombinations, readRoles } from "./roles"

const p = (attrs: Record<string, string>): ParsedPath => ({ d: attrs.d ?? "M3 3 L4 4", filled: false, evenOdd: false, unstroked: false, attrs: { d: "M3 3 L4 4", ...attrs } })

describe("readRoles", () => {
  it("collects body, mark and numbered steps by path index", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body" }),
      p({}),
      p({ "data-role": "step", "data-step": "2" }),
      p({ "data-role": "step", "data-step": "1" }),
      p({ "data-role": "step", "data-step": "1" }),
      p({ "data-role": "mark" }),
    ])
    expect(issues).toEqual([])
    expect(roles).toEqual({ body: [0], parts: [], mark: [5], steps: [[3, 4], [2]], hidden: [] })
  })

  it("has no roles for a plain drawing", () => {
    expect(readRoles([p({}), p({})]).roles).toEqual({ body: [], parts: [], mark: [], steps: [], hidden: [] })
  })

  it("reports unknown roles, missing or stray step numbers and gaps", () => {
    const { issues } = readRoles([
      p({ "data-role": "halo" }),
      p({ "data-role": "step" }),
      p({ "data-step": "1" }),
      p({ "data-role": "step", "data-step": "3" }),
    ])
    expect(issues).toEqual([
      'path 1: data-role="halo" is not body, mark or step',
      "path 2: a step needs data-step",
      'path 3: data-step without data-role="step"',
      "steps must run 1…n without gaps; found 3",
    ])
  })

  it("collects the hidden mark and step paths", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body" }),
      p({ "data-role": "step", "data-step": "1", "data-rest": "hidden" }),
      p({ "data-role": "mark", "data-rest": "hidden" }),
    ])
    expect(issues).toEqual([])
    expect(roles.hidden).toEqual([1, 2])
    expect(roles.steps).toEqual([[1]])
    expect(roles.mark).toEqual([2])
  })

  it("reports data-rest on a body or a plain line, and any value but hidden", () => {
    const { issues } = readRoles([
      p({ "data-role": "body", "data-rest": "hidden" }),
      p({ "data-rest": "hidden" }),
      p({ "data-role": "mark", "data-rest": "shown" }),
    ])
    expect(issues).toEqual([
      "path 1: data-rest is only allowed on a mark or step",
      "path 2: data-rest is only allowed on a mark or step",
      'path 3: data-rest="shown" is not hidden',
    ])
  })

  it("reads a body split into named parts, in source order", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body", "data-part": "moon" }),
      p({ "data-role": "body", "data-part": "cloud" }),
      p({ "data-role": "body", "data-part": "moon" }),
      p({}),
    ])
    expect(issues).toEqual([])
    expect(roles.body).toEqual([0, 1, 2])
    expect(roles.parts).toEqual([
      { name: "moon", paths: [0, 2] },
      { name: "cloud", paths: [1] },
    ])
  })

  it("reports parts on some body paths only, one part, a bad name, too many, and a part off the body", () => {
    const body = (part?: string) => p(part === undefined ? { "data-role": "body" } : { "data-role": "body", "data-part": part })
    expect(readRoles([body("a"), body(), body("b")]).issues).toEqual(["when one body path names a part, every body path does"])
    expect(readRoles([body("moon")]).issues).toEqual(["a body with one part (moon) is a whole body: drop data-part"])
    expect(readRoles([body("Moon-1")]).issues).toEqual(['path 1: data-part="Moon-1" is not one lowercase word'])
    expect(readRoles(["a", "b", "c", "d", "e"].map((n) => body(n))).issues).toEqual(["5 parts; a body has at most 4"])
    expect(readRoles([p({ "data-role": "mark", "data-part": "moon" })]).issues).toEqual(['path 1: data-part without data-role="body"'])
  })
})

describe("partCombinations", () => {
  it("lists every combination short of all of them, smallest first", () => {
    expect(partCombinations(["left", "middle", "right"])).toEqual([["left"], ["middle"], ["right"], ["left", "middle"], ["left", "right"], ["middle", "right"]])
    expect(partCombinations(["moon", "cloud"])).toEqual([["moon"], ["cloud"]])
    expect(partCombinations([])).toEqual([])
  })
})
