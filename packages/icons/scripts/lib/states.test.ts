import { describe, expect, it } from "vitest"
import { parseStates, resolveState } from "./states"

const table = parseStates({
  states: { rest: ["regular"], hover: ["marked", "regular"], selected: ["solid", "progress-n", "marked", "regular"], progress: ["progress-k", "regular"], done: ["progress-n", "solid", "regular"] },
})

describe("states", () => {
  it("takes the first form the glyph has", () => {
    expect(resolveState(table, ["regular", "marked", "solid"], "selected")).toEqual({ fill: { kind: "solid" } })
    expect(resolveState(table, ["regular", "progress-1", "progress-2"], "selected")).toEqual({ fill: { kind: "progress", k: 2 } })
    expect(resolveState(table, ["regular"], "hover")).toEqual({ fill: { kind: "regular" } })
    expect(resolveState(table, ["regular", "marked"], "hover")).toEqual({ fill: { kind: "regular" }, marked: true })
    expect(resolveState(table, ["regular", "progress-1", "progress-1-marked", "progress-2", "progress-2-marked"], "selected")).toEqual({ fill: { kind: "progress", k: 2 } })
  })

  it("clamps progress-k to the glyph's marks and falls back without marks", () => {
    expect(resolveState(table, ["regular", "progress-1", "progress-2", "progress-3"], "progress", 5)).toEqual({ fill: { kind: "progress", k: 3 } })
    expect(resolveState(table, ["regular", "progress-1"], "progress", 0)).toEqual({ fill: { kind: "progress", k: 0 } })
    expect(resolveState(table, ["regular"], "progress", 2)).toEqual({ fill: { kind: "regular" } })
  })

  it("treats a fractional or NaN k as 0", () => {
    const forms = ["regular", "progress-1", "progress-2", "progress-3"]
    expect(resolveState(table, forms, "progress", 1.5)).toEqual({ fill: { kind: "progress", k: 0 } })
    expect(resolveState(table, forms, "progress", Number.NaN)).toEqual({ fill: { kind: "progress", k: 0 } })
  })

  it("refuses a table whose state does not end in regular, or an unknown form", () => {
    expect(() => parseStates({ states: { rest: ["solid"] } })).toThrow(/rest must end with regular/)
    expect(() => parseStates({ states: { rest: ["glow", "regular"] } })).toThrow(/rest: unknown form glow/)
  })
})
