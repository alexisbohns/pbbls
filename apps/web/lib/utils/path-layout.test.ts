import { describe, expect, it } from "vitest"
import type { Pebble } from "@/lib/types"
import { groupPebbles } from "./path-layout"

function pebble(id: string, intensity: 1 | 2 | 3): Pebble {
  return {
    id,
    name: id,
    happened_at: "2026-08-22T10:00:00Z",
    intensity,
    positiveness: 0,
    visibility: "private",
    emotion_id: "e",
    soul_ids: [],
    domain_ids: [],
    collection_ids: [],
    instants: [],
    snaps: [],
    cards: [],
    render_svg: null,
    render_version: null,
    created_at: "2026-08-22T10:00:00Z",
    updated_at: "2026-08-22T10:00:00Z",
  }
}

/** Compact shorthand so the expectations read as layouts, not as objects. */
function shape(pebbles: Pebble[], columns?: number): string[] {
  return groupPebbles(pebbles, columns).map((block) =>
    block.kind === "large" ? "L" : `G[${block.columns.map((c) => c.map((p) => p.id).join("")).join("|")}]`,
  )
}

const run = (n: number, intensity: 1 | 2 | 3 = 2) =>
  Array.from({ length: n }, (_, i) => pebble(String(i), intensity))

describe("groupPebbles", () => {
  it("returns no blocks for an empty week", () => {
    expect(groupPebbles([])).toEqual([])
  })

  it("deals a run round-robin across two columns", () => {
    expect(shape(run(5))).toEqual(["G[024|13]"])
  })

  it("mixes smalls and mediums into the same wall", () => {
    const week = [pebble("a", 1), pebble("b", 2), pebble("c", 1), pebble("d", 2)]
    expect(shape(week)).toEqual(["G[ac|bd]"])
  })

  it("honours a different column count", () => {
    expect(shape(run(7), 3)).toEqual(["G[036|14|25]"])
  })

  it("gives every large its own full-width block", () => {
    expect(shape([pebble("a", 3), pebble("b", 3)])).toEqual(["L", "L"])
  })

  it("splits the wall around an intervening large", () => {
    const week = [pebble("a", 2), pebble("b", 2), pebble("L", 3), pebble("c", 2)]
    expect(shape(week)).toEqual(["G[a|b]", "L", "G[c|]"])
  })

  it("emits no empty grid when a large leads or trails", () => {
    expect(shape([pebble("L", 3), pebble("a", 2)])).toEqual(["L", "G[a|]"])
    expect(shape([pebble("a", 2), pebble("L", 3)])).toEqual(["G[a|]", "L"])
  })

  it("reads left-to-right in chronological order", () => {
    // Round-robin is what guarantees this: reading row by row across the columns
    // must give back the original order.
    const week = run(6)
    const [block] = groupPebbles(week)
    if (block.kind !== "grid") throw new Error("expected a grid")
    const readingOrder: string[] = []
    for (let row = 0; row < block.columns[0].length; row++) {
      for (const column of block.columns) {
        const p = column[row]
        if (p) readingOrder.push(p.id)
      }
    }
    expect(readingOrder).toEqual(week.map((p) => p.id))
  })

  it("preserves order within every column", () => {
    const week = run(9)
    const [block] = groupPebbles(week)
    if (block.kind !== "grid") throw new Error("expected a grid")
    for (const column of block.columns) {
      const indices = column.map((p) => Number(p.id))
      expect([...indices].sort((a, b) => a - b)).toEqual(indices)
    }
  })

  it("does not mutate the input array", () => {
    const week = run(4)
    const copy = [...week]
    groupPebbles(week)
    expect(week).toEqual(copy)
  })
})
