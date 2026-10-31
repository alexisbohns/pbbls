import { describe, expect, it } from "vitest"
import { renderGuideMarkdown } from "./guide-markdown"

describe("renderGuideMarkdown", () => {
  it("renders Markdown", () => {
    expect(renderGuideMarkdown("A **stone**.")).toBe("<p>A <strong>stone</strong>.</p>")
  })

  it("drops raw HTML and scripts", () => {
    const html = renderGuideMarkdown('Hi <script>alert(1)</script> <img src=x onerror="alert(1)">\n\n<div onclick="x()">block</div>')
    expect(html).not.toContain("<script")
    expect(html).not.toContain("onerror")
    expect(html).not.toContain("<div")
  })
})
