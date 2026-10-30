import { describe, expect, it } from "vitest"
import { CANONICAL_ROOT, formatSvg } from "./format"

const ILLUSTRATOR = `<?xml version="1.0" encoding="UTF-8"?>
<!-- Generator: Adobe Illustrator 28.0 -->
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" id="Layer_1" width="24px" height="24px" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.750" stroke-linecap="round" stroke-linejoin="round" xml:space="preserve">
  <title>trash</title>
  <path fill="none" d="M4.123 5.456 C6.1 14.3 7.7 15.9 9.5 18.1
     C11.9 13.5 14.6 9.8 17.9 6.8"/>
  <path fill="currentColor" stroke="none" d="M6.22 16.4 L7.104 16.4 Z"/>
</svg>
`

const CANONICAL = `${CANONICAL_ROOT}
  <path d="M4.12 5.46 C6.1 14.3 7.7 15.9 9.5 18.1 C11.9 13.5 14.6 9.8 17.9 6.8"/>
  <path d="M6.22 16.4 L7.1 16.4 Z" fill="currentColor" stroke="none"/>
</svg>
`

describe("formatSvg", () => {
  it("formats an editor export to the canonical form", () => {
    expect(formatSvg(ILLUSTRATOR)).toBe(CANONICAL)
  })

  it("is idempotent", () => {
    expect(formatSvg(CANONICAL)).toBe(CANONICAL)
  })

  it("passes two-decimal path data through unchanged", () => {
    const d = "M3.4 11.2 C5.25 9.9 7 12.05 9.3 11 Q10.5 10.1 12 12 A1.2 1.2 0 0 1 13.4 13.4 Z"
    const out = formatSvg(`${CANONICAL_ROOT}<path d="${d}"/></svg>`)
    expect(out).toBe(`${CANONICAL_ROOT}\n  <path d="${d}"/>\n</svg>\n`)
  })

  it("orders a carved stone's attributes d, fill, fill-rule, stroke", () => {
    const out = formatSvg(`${CANONICAL_ROOT}<path stroke="none" fill-rule="evenodd" fill="currentColor" d="M2 2 L3 3 Z"/></svg>`)
    expect(out).toContain('<path d="M2 2 L3 3 Z" fill="currentColor" fill-rule="evenodd" stroke="none"/>')
  })
})
