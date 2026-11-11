import { renderToStaticMarkup } from "react-dom/server"
import { describe, expect, it } from "vitest"
import { GlyphSvg } from "./GlyphSvg"
import { MARKED, SOLID } from "./forms"

const glyph = {
  name: "dot",
  paths: [
    { d: "M4 12 C4 6 20 6 20 12 C20 18 4 18 4 12", role: "body" as const },
    { d: "M12 11 L12 13", role: "mark" as const },
  ],
  parts: [],
  steps: 0,
  carved: { solid: { d: "M4 12 L20 12 L12 4 Z", keep: [1], lit: [] } },
  dotted: null,
  overrides: {},
}

describe("GlyphSvg", () => {
  it("draws regular as strokes, hidden from assistive tech, 24 by default", () => {
    const html = renderToStaticMarkup(<GlyphSvg glyph={glyph} />)
    expect(html).toContain('width="24"')
    expect(html).toContain('aria-hidden="true"')
    expect(html).toContain('focusable="false"')
    expect(html).toContain('stroke="currentColor"')
    expect(html).not.toContain('fill="currentColor"')
  })

  it("draws a solid's carved fill even-odd and unstroked, then its kept lines", () => {
    const html = renderToStaticMarkup(<GlyphSvg glyph={glyph} form={SOLID} />)
    expect(html).toContain('<path d="M4 12 L20 12 L12 4 Z" fill="currentColor" fill-rule="evenodd" stroke="none"')
    expect(html).toContain('<path d="M12 11 L12 13"')
    expect(html).toContain('data-form="solid"')
  })

  it("labels when asked, and marks filled paths for the fade only when animated", () => {
    const html = renderToStaticMarkup(<GlyphSvg glyph={glyph} form={MARKED} aria-label="Dot" animate />)
    expect(html).toContain('role="img"')
    expect(html).not.toContain("aria-hidden")
    expect(html).toContain('class="pbbls-glyph-fill"')
    expect(renderToStaticMarkup(<GlyphSvg glyph={glyph} form={MARKED} />)).not.toContain("pbbls-glyph-fill")
  })
})
