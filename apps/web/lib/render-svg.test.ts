import { describe, expect, it } from "vitest"

import { safeRenderSvg } from "./render-svg"
import { COMPOSED_RENDER_SVG } from "./__fixtures__/composed-render-svg"

/**
 * Delivery guard for `pebbles.render_svg` (#829).
 *
 * Two halves, and both matter. The accept half is what stops the guard from
 * quietly blanking every pebble on the wall: it runs against a real composed
 * render, not a hand-written approximation. The reject half is the finding's
 * payload and its neighbours, delivered as a row already sitting in the
 * database — which is the case the compositor fix cannot reach.
 */

/** Splice markup into a real composed render, the way a pre-fix row carries it. */
function poison(payload: string): string {
  return COMPOSED_RENDER_SVG.replace(
    '<path id="glyph:stroke-0"',
    `<path id="glyph:stroke-0"/>${payload}<path`,
  )
}

describe("safeRenderSvg", () => {
  it("accepts a real composed render unchanged", () => {
    expect(safeRenderSvg(COMPOSED_RENDER_SVG)).toBe(COMPOSED_RENDER_SVG)
  })

  it("accepts the engine's empty-glyph square", () => {
    const svg =
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200"></svg>'
    expect(safeRenderSvg(svg)).toBe(svg)
  })

  it("returns null for absent or empty input", () => {
    expect(safeRenderSvg(null)).toBeNull()
    expect(safeRenderSvg(undefined)).toBeNull()
    expect(safeRenderSvg("")).toBeNull()
    expect(safeRenderSvg("   ")).toBeNull()
    expect(safeRenderSvg(42 as unknown as string)).toBeNull()
  })

  it("rejects the stored-XSS payloads from the finding", () => {
    const payloads = [
      "<foreignObject><img src=x onerror=alert(document.domain)></foreignObject>",
      "<script>alert(1)</script>",
      '<use href="data:image/svg+xml;base64,PHN2Zz4="/>',
      '<image href="x" onerror="alert(1)"/>',
      '<animate attributeName="href" values="javascript:alert(1)"/>',
      '<set attributeName="onload" to="alert(1)"/>',
      "<style>@import url(//evil.example)</style>",
      '<a href="javascript:alert(1)"><path d="M0 0"/></a>',
      "<iframe src=//evil.example></iframe>",
    ]
    for (const payload of payloads) {
      expect(safeRenderSvg(poison(payload)), payload).toBeNull()
    }
  })

  it("rejects event handlers and script URLs on otherwise-allowed elements", () => {
    const cases = [
      '<svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0" onload="alert(1)"/></svg>',
      '<svg xmlns="http://www.w3.org/2000/svg"><g onclick="alert(1)"></g></svg>',
      '<svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0" fill="url(javascript:alert(1))"/></svg>',
      '<svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0" clip-path="url(//evil.example)"/></svg>',
      '<svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0" style="behavior:url(#x)"/></svg>',
    ]
    for (const svg of cases) {
      expect(safeRenderSvg(svg), svg).toBeNull()
    }
  })

  it("rejects an unquoted attribute value", () => {
    expect(
      safeRenderSvg('<svg xmlns="http://www.w3.org/2000/svg"><path d=M0 /></svg>'),
    ).toBeNull()
  })

  it("rejects a foreign namespace on the root element", () => {
    expect(
      safeRenderSvg('<svg xmlns="http://www.w3.org/1999/xhtml"><g></g></svg>'),
    ).toBeNull()
  })

  it("rejects comments, declarations and CDATA", () => {
    for (const svg of [
      '<svg xmlns="http://www.w3.org/2000/svg"><!--<script>alert(1)</script>--></svg>',
      '<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"></svg>',
      '<svg xmlns="http://www.w3.org/2000/svg"><![CDATA[<script>alert(1)</script>]]></svg>',
    ]) {
      expect(safeRenderSvg(svg), svg).toBeNull()
    }
  })

  it("rejects stray text between tags", () => {
    expect(
      safeRenderSvg('<svg xmlns="http://www.w3.org/2000/svg">hello</svg>'),
    ).toBeNull()
  })

  it("rejects an unbalanced or non-svg-rooted document", () => {
    for (const svg of [
      '<svg xmlns="http://www.w3.org/2000/svg"><g>',
      '<g><path d="M0 0"/></g>',
      '<path d="M0 0"/>',
    ]) {
      expect(safeRenderSvg(svg), svg).toBeNull()
    }
  })
})
