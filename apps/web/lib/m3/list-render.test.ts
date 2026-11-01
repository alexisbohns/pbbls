import { createElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { describe, expect, it, vi } from "vitest"
import { ListGroup } from "@/components/m3/ListGroup"
import { ListItem } from "@/components/m3/ListItem"

// next/link resolves the root workspace's React (see home-render.test.ts).
type Props = { children?: React.ReactNode } & Record<string, unknown>
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: Props & { href: string }) => createElement("a", { href, ...rest }, children),
}))

const html = (element: ReactElement) => renderToStaticMarkup(element)

describe("M3 list groups (#1384, two-area spec D6, D7)", () => {
  it("head the group with an h3 under the detail's h2, and segment its items", () => {
    const props: ComponentProps<typeof ListGroup> = {
      title: "Pebbles",
      children: [
        createElement(ListItem, { key: "a", href: "/pebble/a", headline: "Rain", supporting: "1 Nov 2026" }),
        createElement(ListItem, { key: "b", onClick: () => {}, headline: "Sun" }),
        createElement(ListItem, { key: "c", headline: "Static" }),
      ],
    }
    const markup = html(createElement(ListGroup, props))
    expect(markup).toMatch(/<section aria-labelledby="[^"]+"><h3 id="[^"]+" class="m3-type-label-large[^"]*">Pebbles<\/h3><ul class="m3-list-group">/)
    expect(markup).toMatch(/<li><a href="\/pebble\/a" class="[^"]*min-h-14[^"]*">.*Rain.*1 Nov 2026.*<\/a><\/li>/)
    expect(markup).toMatch(/<li><button type="button"[^>]*>.*Sun.*<\/button><\/li>/)
    expect(markup).toMatch(/<li><div class="[^"]*">.*Static/)
  })

  it("keep an item's own control beside its link, never inside it", () => {
    const markup = html(
      createElement(ListItem, { href: "/pebble/a", headline: "Rain", action: createElement("button", { type: "button", "aria-label": "Remove Rain" }) }),
    )
    expect(markup).toMatch(/<li><div[^>]*><a href="\/pebble\/a"[^>]*>.*<\/a><button type="button" aria-label="Remove Rain"><\/button><\/div><\/li>/)
    expect(markup.slice(markup.indexOf("<a "), markup.indexOf("</a>"))).not.toContain("<button")
  })
})
