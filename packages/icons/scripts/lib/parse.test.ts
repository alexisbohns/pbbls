import { describe, expect, it } from "vitest"
import { parseSvg } from "./parse"

const ROOT = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">'

describe("parseSvg", () => {
  it("parses a canonical file into its root and paths", () => {
    const text = `${ROOT}\n  <path d="M4.5 13 C6.1 14.3 7.7 15.9 9.5 18.1"/>\n  <path d="M6.22 16.4 Z" fill="currentColor" fill-rule="evenodd" stroke="none"/>\n</svg>\n`
    const parsed = parseSvg(text, "home.svg")
    expect(parsed.root).toEqual({
      xmlns: "http://www.w3.org/2000/svg",
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.75",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    })
    expect(parsed.paths).toHaveLength(2)
    expect(parsed.paths[0]).toMatchObject({ d: "M4.5 13 C6.1 14.3 7.7 15.9 9.5 18.1", filled: false, evenOdd: false, unstroked: false })
    expect(parsed.paths[1]).toMatchObject({ d: "M6.22 16.4 Z", filled: true, evenOdd: true, unstroked: true })
  })

  it.each([
    ["a circle", `${ROOT}<circle cx="1" cy="1" r="1"/></svg>`, "found <circle>"],
    ["a nested group", `${ROOT}<g><path d="M2 2 L3 3"/></g></svg>`, "found <g>"],
    ["a comment", `${ROOT}<!-- hi --><path d="M2 2 L3 3"/></svg>`, "comments are not allowed"],
    ["a second root", `${ROOT}<path d="M2 2 L3 3"/></svg>${ROOT}</svg>`, "content after </svg>"],
    ["a path without d", `${ROOT}<path fill="currentColor"/></svg>`, "path 1 has no d"],
    ["an open path", `${ROOT}<path d="M2 2 L3 3"></path></svg>`, "self-closing"],
  ])("throws on %s with the file name", (_what, text, message) => {
    expect(() => parseSvg(text, "bad.svg")).toThrow(`bad.svg: `)
    expect(() => parseSvg(text, "bad.svg")).toThrow(message)
  })
})
