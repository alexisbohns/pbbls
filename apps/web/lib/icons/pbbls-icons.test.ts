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
})
