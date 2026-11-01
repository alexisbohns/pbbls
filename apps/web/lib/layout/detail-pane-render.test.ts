import { cloneElement, createElement, isValidElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it, vi } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { DetailPane } from "@/components/layout/DetailPane"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { RightArea } from "@/components/layout/RightArea"

// next, lucide-react and @base-ui resolve the root workspace's React (see
// home-render.test.ts): plain elements stand in for the link, the icons and
// the button (which renders its `render` element, as Base UI does). The server
// render reads every media query as false, so the pane renders as the
// overlay (below 1024 px).
type Props = { children?: React.ReactNode } & Record<string, unknown>
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: Props & { href: string }) => createElement("a", { href, ...rest }, children),
}))
vi.mock("lucide-react", () => ({
  ChevronLeft: () => createElement("svg", { "data-icon": "back" }),
  X: () => createElement("svg", { "data-icon": "close" }),
}))
vi.mock("@/components/ui/button", () => ({
  Button: ({ children, render, ...rest }: Props & { render?: unknown }) => {
    const { variant, size, nativeButton, ...dom } = rest
    void variant
    void size
    void nativeButton
    return isValidElement(render) ? cloneElement(render, dom, children) : createElement("button", dom, children)
  },
}))
vi.mock("next/navigation", () => ({
  usePathname: () => "/wallet",
  useRouter: () => ({ push: () => {}, replace: () => {}, back: () => {} }),
}))

function html(element: ReactElement): string {
  const intl: ComponentProps<typeof NextIntlClientProvider> = { locale: "en", messages: en, timeZone: "UTC", children: element }
  return renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
}

describe("a detail's pane (#1384, two-area spec D5, D8)", () => {
  const markup = html(createElement(DetailPane, null, createElement(DetailTopBar, { title: "Wallet" })))

  it("is a modal dialog named by its title in the overlay", () => {
    expect(markup).toMatch(/<div data-detail="" role="dialog" aria-modal="true" aria-labelledby="detail-title"/)
    expect(markup).toContain("m3 ")
  })

  it("titles the detail with an h2 that takes focus, under a back control", () => {
    expect(markup).toMatch(/<h2 id="detail-title" tabindex="-1"[^>]*>Wallet<\/h2>/)
    expect(markup).toMatch(/<button[^>]*aria-label="Back"[^>]*><svg data-icon="back"/)
  })

  it("goes back to its parent detail through a link when nested", () => {
    const nested = html(createElement(DetailPane, null, createElement(DetailTopBar, { title: "Changelog", backHref: "/lab" })))
    expect(nested).toMatch(/<a href="\/lab" aria-label="Back"/)
  })
})

describe("the right area (#1384, two-area spec D1, D8)", () => {
  it("is named Path while idle, and hidden below 1024 px", () => {
    const idle = html(createElement(RightArea, { wide: false, detailOpen: false, detail: null }))
    expect(idle).toMatch(/^<section aria-label="Path" class="[^"]*max-panes:hidden/)
  })

  it("is named by the detail's title once one opens, and becomes the overlay's box", () => {
    const open = html(createElement(RightArea, { wide: false, detailOpen: true, detail: createElement("div", null, "x") }))
    expect(open).toMatch(/^<section aria-labelledby="detail-title" class="[^"]*max-panes:fixed max-panes:inset-0/)
  })
})
