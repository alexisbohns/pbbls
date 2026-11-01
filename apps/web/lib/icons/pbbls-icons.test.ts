import { CheckCircle } from "@pbbls/icons"
import { createElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { describe, expect, it } from "vitest"

// Proves the workspace resolves from the app (spec §7): the generated
// components under packages/icons/react render here as plain functions.
describe("@pbbls/icons", () => {
  it("renders a generated icon", () => {
    const markup = renderToStaticMarkup(createElement(CheckCircle, {}))
    expect(markup).toContain('viewBox="0 0 24 24"')
    expect(markup).toContain('stroke="currentColor"')
    expect(markup).toContain('aria-hidden="true"')
  })

  it("names itself an image only when labelled (spec D8)", () => {
    const markup = renderToStaticMarkup(createElement(CheckCircle, { "aria-label": "Done" }))
    expect(markup).toContain('role="img"')
    expect(markup).toContain('aria-label="Done"')
    expect(markup).not.toContain("aria-hidden")
    expect(markup).toContain('focusable="false"')
  })

  it("puts a className on the svg, so a size class opts out of the shadcn slot default (spec D7)", () => {
    const markup = renderToStaticMarkup(createElement(CheckCircle, { className: "size-4 text-primary" }))
    expect(markup).toContain('class="size-4 text-primary"')
  })
})
