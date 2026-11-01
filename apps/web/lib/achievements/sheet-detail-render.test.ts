import { cloneElement, createElement, isValidElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it, vi } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { DataContext, type DataContextValue } from "@/lib/data/provider-context"
import { EMPTY_STORE } from "@/lib/data/data-provider"
import { DetailPane } from "@/components/layout/DetailPane"
import { AchievementSheetDetail } from "@/components/achievements/sheets/AchievementSheetDetail"

// next, lucide-react and @base-ui resolve the root workspace's React (see
// home-render.test.ts): plain elements stand in. No provider, so the page
// read never lands: the detail shows its frame and its loading line.
type Props = { children?: React.ReactNode } & Record<string, unknown>
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: Props & { href: string }) => createElement("a", { href, ...rest }, children),
}))
vi.mock("lucide-react", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
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
vi.mock("@/lib/data/auth-context", () => ({
  useAuth: () => ({ user: null, isAuthenticated: false, isLoading: false }),
}))
vi.mock("next/navigation", () => ({
  usePathname: () => "/achievements/explorer",
  useRouter: () => ({ push: () => {}, replace: () => {}, back: () => {} }),
}))

const DATA: DataContextValue = { provider: null, store: EMPTY_STORE, setStore: () => {}, loading: false, error: null, refreshStore: () => {} }

function html(element: ReactElement): string {
  const intl: ComponentProps<typeof NextIntlClientProvider> = {
    locale: "en",
    messages: en,
    timeZone: "UTC",
    children: createElement(DataContext.Provider, { value: DATA }, element),
  }
  return renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
}

describe("an achievements sheet as a nested detail (#1384, two-area spec D5)", () => {
  it("is titled by its card, with a back arrow (not a close) at every size", () => {
    const markup = html(createElement(DetailPane, null, createElement(AchievementSheetDetail, { sheet: { card: "explorer" } })))
    expect(markup).toMatch(/<h2 id="detail-title"[^>]*>Explorer<\/h2>/)
    expect(markup).toMatch(/<button[^>]*aria-label="Back"[^>]*><svg data-icon="back"/)
    expect(markup).not.toContain('data-icon="close"')
  })

  it("titles a year's sheet by the year", () => {
    const markup = html(createElement(DetailPane, null, createElement(AchievementSheetDetail, { sheet: { card: "year", year: 2026 } })))
    expect(markup).toMatch(/<h2 id="detail-title"[^>]*>2026<\/h2>/)
  })

  it("says an unknown sheet is not there", () => {
    const markup = html(createElement(DetailPane, null, createElement(AchievementSheetDetail, { sheet: null })))
    expect(markup).toMatch(/<h2 id="detail-title"[^>]*>Achievements<\/h2>/)
  })
})
