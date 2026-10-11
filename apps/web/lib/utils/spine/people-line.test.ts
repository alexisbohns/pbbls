import { describe, expect, it } from "vitest"
import { peopleLine } from "./people-line"

const ids = (n: number) => Array.from({ length: n }, (_, i) => `s${i}`)

describe("peopleLine", () => {
  it("draws no line for a solitary pebble", () => {
    expect(peopleLine([], [], "en")).toEqual({
      named: [],
      others: 0,
      othersWords: "",
      marks: [],
      overflow: 0,
    })
  })

  it.each([1, 2, 3])("names all %i souls", (n) => {
    const line = peopleLine(ids(n), ids(n), "en")
    expect(line.named).toEqual(ids(n))
    expect(line).toMatchObject({ others: 0, marks: [], overflow: 0 })
  })

  it("names two and folds the rest from four", () => {
    const line = peopleLine(ids(4), ids(4), "en")
    expect(line).toEqual({
      named: ["s0", "s1"],
      others: 2,
      othersWords: "two",
      marks: ["s2", "s3"],
      overflow: 0,
    })
  })

  it("draws at most five marks at seven", () => {
    const line = peopleLine(ids(7), ids(7), "en")
    expect(line.named).toHaveLength(2)
    expect(line.others).toBe(5)
    expect(line.othersWords).toBe("five")
    expect(line.marks).toEqual(["s2", "s3", "s4", "s5", "s6"])
    expect(line.overflow).toBe(0)
  })

  it("reads 'with A, B & twelve others [5 marks] +7' at fourteen", () => {
    const line = peopleLine(ids(14), ids(14), "en")
    expect(line.named).toEqual(["s0", "s1"])
    expect(line.others).toBe(12)
    expect(line.othersWords).toBe("twelve")
    expect(line.marks).toHaveLength(5)
    expect(line.overflow).toBe(7)
    expect(peopleLine(ids(14), ids(14), "fr").othersWords).toBe("douze")
  })

  it("follows the persons-of-the-week order, not the pebble's", () => {
    const line = peopleLine(["elio", "mara", "nadia", "ines"], ["nadia", "ines", "mara", "elio"], "en")
    expect(line.named).toEqual(["nadia", "ines"])
    expect(line.marks).toEqual(["mara", "elio"])
  })

  it("keeps unranked souls after the ranked ones, in their own order", () => {
    const line = peopleLine(["x", "mara", "y"], ["mara"], "en")
    expect(line.named).toEqual(["mara", "x", "y"])
  })

  it("counts a duplicated soul once", () => {
    expect(peopleLine(["a", "a", "b"], ["a", "b"], "en").named).toEqual(["a", "b"])
  })
})
