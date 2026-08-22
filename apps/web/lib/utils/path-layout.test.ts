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

/** Compact shorthand so the expectations below read as layouts, not as objects. */
function shape(pebbles: Pebble[]): string[] {
  return groupPebbles(pebbles).map((block) => {
    if (block.kind === "small") return `S${block.pebbles.length}`
    if (block.kind === "large") return "L"
    return `M[${block.rows.map((r) => r.length).join(",")}]`
  })
}

const mediums = (n: number) =>
  Array.from({ length: n }, (_, i) => pebble(`m${i}`, 2))

describe("groupPebbles", () => {
  it("returns no blocks for an empty week", () => {
    expect(groupPebbles([])).toEqual([])
  })

  it("puts a lone small in its own block", () => {
    expect(shape([pebble("a", 1)])).toEqual(["S1"])
  })

  it("merges consecutive smalls into one block", () => {
    expect(shape([pebble("a", 1), pebble("b", 1), pebble("c", 1)])).toEqual(["S3"])
  })

  it.each([
    [1, "M[1]"],
    [2, "M[2]"],
    [3, "M[2,1]"],
    [4, "M[2,2]"],
    [5, "M[2,2,1]"],
  ])("chunks a run of %i mediums into %s", (n, expected) => {
    expect(shape(mediums(n))).toEqual([expected])
  })

  it("gives every large its own block", () => {
    expect(shape([pebble("a", 3), pebble("b", 3)])).toEqual(["L", "L"])
  })

  it("does not merge medium runs across an intervening large", () => {
    expect(shape([pebble("a", 2), pebble("b", 3), pebble("c", 2)])).toEqual([
      "M[1]",
      "L",
      "M[1]",
    ])
  })

  it("does not merge medium runs across an intervening small", () => {
    expect(shape([...mediums(2), pebble("s", 1), pebble("m9", 2)])).toEqual([
      "M[2]",
      "S1",
      "M[1]",
    ])
  })

  it("walks the full progressive ladder in order", () => {
    const week = [
      pebble("s1", 1),
      pebble("s2", 1),
      pebble("m1", 2),
      pebble("m2", 2),
      pebble("s3", 1),
      pebble("l1", 3),
      pebble("m3", 2),
      pebble("m4", 2),
      pebble("m5", 2),
    ]
    expect(shape(week)).toEqual(["S2", "M[2]", "S1", "L", "M[2,1]"])
  })

  it("preserves chronological order within and across blocks", () => {
    const week = [
      pebble("a", 1),
      pebble("b", 2),
      pebble("c", 2),
      pebble("d", 2),
      pebble("e", 3),
    ]
    const flattened = groupPebbles(week).flatMap((block) => {
      if (block.kind === "small") return block.pebbles
      if (block.kind === "large") return [block.pebble]
      return block.rows.flat()
    })
    expect(flattened.map((p) => p.id)).toEqual(["a", "b", "c", "d", "e"])
  })

  it("does not mutate the input array", () => {
    const week = [pebble("a", 2), pebble("b", 2)]
    const copy = [...week]
    groupPebbles(week)
    expect(week).toEqual(copy)
  })
})
