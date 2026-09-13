import { describe, expect, it } from "vitest"
import {
  UnsupportedPathError,
  parsePath,
  pathBounds,
  serializePath,
  transformPath,
  type Matrix,
} from "./path"

// This tokenizer is the front door to every glyph the store sells: whatever it
// produces is serialized straight into `glyphs.strokes`, which web, iOS and
// Android all render without re-deriving anything. The admin is the only writer,
// so a mistake here has no downstream reader that could catch it.

describe("parsePath", () => {
  it("reads absolute moveto and lineto", () => {
    expect(parsePath("M 10 20 L 30 40")).toEqual([
      { cmd: "M", points: [{ x: 10, y: 20 }] },
      { cmd: "L", points: [{ x: 30, y: 40 }] },
    ])
  })

  it("normalizes relative commands against the current point", () => {
    expect(parsePath("m 10 10 l 5 0 l 0 5")).toEqual([
      { cmd: "M", points: [{ x: 10, y: 10 }] },
      { cmd: "L", points: [{ x: 15, y: 10 }] },
      { cmd: "L", points: [{ x: 15, y: 15 }] },
    ])
  })

  // SVG's implicit-lineto rule: extra coordinate pairs after a moveto are
  // linetos, not more movetos. Getting this wrong turns one stroke into several.
  it("treats trailing moveto pairs as linetos", () => {
    expect(parsePath("M 0 0 10 0 10 10")).toEqual([
      { cmd: "M", points: [{ x: 0, y: 0 }] },
      { cmd: "L", points: [{ x: 10, y: 0 }] },
      { cmd: "L", points: [{ x: 10, y: 10 }] },
    ])
  })

  it("expands H and V into linetos, absolute and relative", () => {
    expect(parsePath("M 5 5 H 20 V 30 h -5 v -10")).toEqual([
      { cmd: "M", points: [{ x: 5, y: 5 }] },
      { cmd: "L", points: [{ x: 20, y: 5 }] },
      { cmd: "L", points: [{ x: 20, y: 30 }] },
      { cmd: "L", points: [{ x: 15, y: 30 }] },
      { cmd: "L", points: [{ x: 15, y: 20 }] },
    ])
  })

  it("keeps quadratic and cubic control points, relative ones offset from the current point", () => {
    expect(parsePath("M 0 0 Q 5 10 10 0")).toEqual([
      { cmd: "M", points: [{ x: 0, y: 0 }] },
      { cmd: "Q", points: [{ x: 5, y: 10 }, { x: 10, y: 0 }] },
    ])
    expect(parsePath("M 10 10 c 0 5 5 10 10 10")).toEqual([
      { cmd: "M", points: [{ x: 10, y: 10 }] },
      { cmd: "C", points: [{ x: 10, y: 15 }, { x: 15, y: 20 }, { x: 20, y: 20 }] },
    ])
  })

  // Z returns the pen to the start of the SUBPATH, not to the origin — a
  // relative command after a close is measured from there.
  it("returns the current point to the subpath start on Z", () => {
    expect(parsePath("M 10 10 L 20 10 Z l 5 5")).toEqual([
      { cmd: "M", points: [{ x: 10, y: 10 }] },
      { cmd: "L", points: [{ x: 20, y: 10 }] },
      { cmd: "Z", points: [] },
      { cmd: "L", points: [{ x: 15, y: 15 }] },
    ])
  })

  it("tracks the subpath start per subpath", () => {
    const cmds = parsePath("M 0 0 L 5 0 Z M 100 100 L 105 100 Z l 1 1")
    expect(cmds[cmds.length - 1]).toEqual({ cmd: "L", points: [{ x: 101, y: 101 }] })
  })

  it("parses commas, missing separators and negative numbers", () => {
    expect(parsePath("M0,0L-5.5,10.25")).toEqual([
      { cmd: "M", points: [{ x: 0, y: 0 }] },
      { cmd: "L", points: [{ x: -5.5, y: 10.25 }] },
    ])
  })

  // `e` is deliberately excluded from the command delimiter class: treated as a
  // command letter it would split `1e3` into a number and a bogus command.
  it("keeps exponent notation inside its number", () => {
    expect(parsePath("M 1e3 2e-2")).toEqual([{ cmd: "M", points: [{ x: 1000, y: 0.02 }] }])
  })

  it("ignores a trailing incomplete coordinate pair", () => {
    expect(parsePath("M 0 0 L 10 10 L 20")).toEqual([
      { cmd: "M", points: [{ x: 0, y: 0 }] },
      { cmd: "L", points: [{ x: 10, y: 10 }] },
    ])
  })

  it("returns nothing for an empty or whitespace `d`", () => {
    expect(parsePath("")).toEqual([])
    expect(parsePath("   ")).toEqual([])
  })

  // Arcs and the smooth shorthands are outside the supported subset. They must
  // throw with the letter named, because the uploader reports it to the human.
  it.each(["A", "S", "T"])("throws UnsupportedPathError naming %s", (letter) => {
    expect(() => parsePath(`M 0 0 ${letter} 1 2 3 4 5 6 7`)).toThrowError(UnsupportedPathError)
    try {
      parsePath(`M 0 0 ${letter} 1 2 3 4 5 6 7`)
    } catch (e) {
      expect((e as UnsupportedPathError).command).toBe(letter)
    }
  })

  it("reports a lowercase unsupported command by its uppercase letter", () => {
    try {
      parsePath("M 0 0 a 1 1 0 0 1 2 2")
      expect.unreachable()
    } catch (e) {
      expect((e as UnsupportedPathError).command).toBe("A")
    }
  })
})

describe("serializePath", () => {
  it("writes every command back in absolute form", () => {
    const d = "M 0 0 L 10 0 Q 15 5 10 10 C 5 15 0 15 0 10 Z"
    expect(serializePath(parsePath(d))).toBe(d)
  })

  it("rounds to two decimals and drops trailing zeros", () => {
    expect(serializePath(parsePath("M 1.005 2.5 L 3.10 4.129"))).toBe("M 1 2.5 L 3.1 4.13")
  })

  // Re-serializing an already-serialized path must be a no-op: the moderation
  // flow parses and re-writes the same geometry more than once (import, then
  // bake), and drift would compound.
  it("is idempotent through a second parse", () => {
    const once = serializePath(parsePath("m 1.333 2.666 q 1 2 3 4 z"))
    expect(serializePath(parsePath(once))).toBe(once)
  })
})

describe("pathBounds", () => {
  it("includes control points, which is what makes it a safe framing superset", () => {
    expect(pathBounds(parsePath("M 0 0 Q 50 -20 10 10"))).toEqual({
      minX: 0,
      minY: -20,
      maxX: 50,
      maxY: 10,
    })
  })

  it("returns null when there is no geometry at all", () => {
    expect(pathBounds([])).toBeNull()
    expect(pathBounds(parsePath("M 0 0 Z"))).not.toBeNull()
  })
})

describe("transformPath", () => {
  const scaleThenTranslate: Matrix = [2, 0, 0, 3, 10, 20]

  it("applies the affine matrix to every point, control points included", () => {
    expect(transformPath(parsePath("M 1 1 Q 2 2 3 3"), scaleThenTranslate)).toEqual([
      { cmd: "M", points: [{ x: 12, y: 23 }] },
      { cmd: "Q", points: [{ x: 14, y: 26 }, { x: 16, y: 29 }] },
    ])
  })

  it("leaves Z alone — it carries no points to move", () => {
    expect(transformPath(parsePath("M 0 0 Z"), scaleThenTranslate)[1]).toEqual({
      cmd: "Z",
      points: [],
    })
  })

  it("does not mutate the input commands", () => {
    const cmds = parsePath("M 1 1")
    transformPath(cmds, scaleThenTranslate)
    expect(cmds).toEqual([{ cmd: "M", points: [{ x: 1, y: 1 }] }])
  })

  it("honours the skew terms b and c", () => {
    // x' = a·x + c·y + e ; y' = b·x + d·y + f
    expect(transformPath(parsePath("M 1 2"), [1, 0.5, 0.25, 1, 0, 0])).toEqual([
      { cmd: "M", points: [{ x: 1.5, y: 2.5 }] },
    ])
  })
})
