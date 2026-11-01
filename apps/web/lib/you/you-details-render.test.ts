import { createElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it, vi } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { GlyphTabs } from "@/components/glyphs/GlyphTabs"
import { WalletHistoryItem } from "@/components/wallet/WalletHistoryItem"

// next and lucide resolve the root workspace's React (see home-render.test.ts).
type Props = { children?: React.ReactNode } & Record<string, unknown>
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: Props & { href: string }) => createElement("a", { href, ...rest }, children),
}))
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: () => {} }),
  useSearchParams: () => new URLSearchParams("tab=market"),
}))
vi.mock("lucide-react", () => ({
  ArrowDownLeft: () => createElement("svg", { "data-icon": "in" }),
  ArrowUpRight: () => createElement("svg", { "data-icon": "out" }),
}))

function html(element: ReactElement): string {
  const intl: ComponentProps<typeof NextIntlClientProvider> = { locale: "en", messages: en, timeZone: "UTC", children: element }
  return renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
}

describe("You's details on M3 (#1384, two-area spec D6)", () => {
  it("draws the glyph tabs as M3 secondary tabs, the active one marked", () => {
    const markup = html(createElement(GlyphTabs, { active: "market" }))
    expect(markup).toMatch(/<nav class="[^"]*border-b[^"]*" role="tablist">/)
    expect(markup).toMatch(/<button role="tab" aria-selected="true" class="[^"]*border-m3-primary[^"]*">Market<\/button>/)
    expect((markup.match(/aria-selected="false"/g) ?? []).length).toBe(2)
  })

  it("lists a karma movement as an M3 list item, its amount in Ysabeau's figures", () => {
    const markup = html(
      createElement(WalletHistoryItem, { event: { id: "1", type: "credit", delta: 10, reason: "pebble_created", created_at: "2026-10-31T09:00:00Z" } }),
    )
    expect(markup).toMatch(/^<li><div class="[^"]*min-h-14[^"]*bg-m3-surface-container/)
    expect(markup).toContain("Pebble created")
    expect(markup).toMatch(/<span class="pb-type-figure-small[^"]*">\+10<\/span>/)
    expect(markup).not.toContain("tabular-nums")
  })
})
