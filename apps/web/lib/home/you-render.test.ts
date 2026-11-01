import { cloneElement, createElement, isValidElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it, vi } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import fr from "@/lib/i18n/messages/fr.json"
import { DataContext, type DataContextValue } from "@/lib/data/provider-context"
import { EMPTY_STORE } from "@/lib/data/data-provider"
import { RippleBadge } from "@/components/profile/ripples/RippleBadge"
import { YouHeader } from "@/components/you/YouHeader"
import { YouCounters } from "@/components/you/YouCounters"
import { YouGlyphs } from "@/components/you/YouGlyphs"
import { YouAchievementsList } from "@/components/you/YouAchievementsList"
import { YouLabView } from "@/components/you/YouLabView"
import { YouScreen } from "@/components/you/YouScreen"
import { DESIGN_LAB_ANNOUNCEMENT, DESIGN_LAB_CHANGELOG, DESIGN_YOU_BADGES } from "@/lib/seed/design-you"

// next, lucide-react and @base-ui resolve the root workspace's React (19.3.0),
// not the one react-dom/server renders with here (apps/web's 19.3.1), so their
// hooks would read a null dispatcher. Plain elements stand in for the link, the
// chevron and the button (which renders its `render` element, as Base UI does);
// the markup under test is You's own.
type Props = { children?: React.ReactNode } & Record<string, unknown>
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: Props & { href: string }) => createElement("a", { href, ...rest }, children),
}))
vi.mock("lucide-react", () => ({
  ChevronRight: ({ className, ...rest }: Props & { className?: string }) =>
    createElement("svg", { ...rest, className: `lucide lucide-chevron-right ${className ?? ""}` }),
}))
vi.mock("@/components/ui/button", () => ({
  Button: ({ children, render, ...rest }: Props & { render?: unknown }) => {
    const { variant, size, nativeButton, ...dom } = rest
    void variant
    void size
    void nativeButton
    return isValidElement(render) ? cloneElement(render, dom, children) : createElement("button", dom, children)
  },
  buttonVariants: () => "",
}))
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: () => {}, replace: () => {} }),
  usePathname: () => "/profile",
}))
vi.mock("@/lib/data/auth-context", () => ({
  useAuth: () => ({
    user: { id: "u1", email: "a@example.com", created_at: "2026-03-14T10:00:00Z" },
    profile: { id: "p1", user_id: "u1", display_name: "Alexis", glyph_id: null },
    isAuthenticated: true,
    isLoading: false,
  }),
}))

const DATA: DataContextValue = { provider: null, store: EMPTY_STORE, setStore: () => {}, loading: false, error: null, refreshStore: () => {} }

function html(element: ReactElement, locale: "en" | "fr" = "en"): string {
  const intl: ComponentProps<typeof NextIntlClientProvider> = {
    locale,
    messages: locale === "fr" ? fr : en,
    timeZone: "UTC",
    children: createElement(DataContext.Provider, { value: DATA }, element),
  }
  return renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
}

const count = (markup: string, needle: RegExp) => (markup.match(needle) ?? []).length

describe("the ripple badge", () => {
  it("keeps its 44 px box and its digit by default", () => {
    const markup = html(createElement(RippleBadge, { level: 3, activeToday: true }))
    expect(markup).toContain("size-11")
    expect(markup).toMatch(/>3<\/span>/)
  })

  it("drops the digit at one line's height", () => {
    const markup = html(createElement(RippleBadge, { level: 3, activeToday: true, className: "size-6", showLevel: false }))
    expect(markup).toContain("size-6")
    expect(markup).not.toContain("size-11")
    expect(markup).not.toMatch(/>\d<\/span>/)
  })
})

describe("the header", () => {
  const markup = html(
    createElement(YouHeader, { name: "Alexis", memberSince: "2026-03-14T10:00:00Z", glyph: null, rippleLevel: 3, activeToday: true, locale: "en" }),
  )

  it("is headed by the name", () => {
    expect(markup).toMatch(/<h1[^>]*>Alexis<\/h1>/)
    expect(markup).toContain("Member since March 2026")
  })

  it("names its settings link, a standard icon button", () => {
    expect(markup).toMatch(/<a href="\/settings" aria-label="Settings" class="[^"]*size-11/)
  })

  it("states the ripples level in words, with no digit in the badge", () => {
    expect(markup).toContain("<span>Ripples Level 3</span>")
    const badge = markup.slice(markup.indexOf("<div class=\"pb-type-label"), markup.indexOf("<span>Ripples Level"))
    expect(badge).not.toMatch(/>\d</)
  })
})

describe("the counters", () => {
  const values = { days: 19, pebbles: 128, karma: 1240 }

  it("read each label before its figure", () => {
    const markup = html(createElement(YouCounters, { values, locale: "en" }))
    expect(count(markup, /<dl /g)).toBe(3)
    expect(count(markup, /<dt[^>]*>.*?<\/dt><dd[^>]*>/g)).toBe(3)
    expect(markup).toMatch(/<span class="min-w-0">Karma<\/span><\/dt><dd[^>]*>1,240<\/dd>/)
  })

  it("format the figures in the locale", () => {
    const markup = html(createElement(YouCounters, { values, locale: "fr" }), "fr")
    expect(markup).toContain(">1 240<")
  })

  it("show a dash while a value loads, and a retry when the days fail", () => {
    const markup = html(createElement(YouCounters, { values: { ...values, days: null }, locale: "en", failed: true, onRetry: () => {} }))
    expect(markup).toContain(">—<")
    expect(markup).toMatch(/Couldn&#x27;t load your numbers\. <button type="button"[^>]*>Try again<\/button>/)
  })
})

describe("the glyphs", () => {
  it("say the served counts", () => {
    const markup = html(createElement(YouGlyphs, { carved: 12, owned: 3 }))
    expect(markup).toContain("The 12 marks you carved.")
    expect(markup).toContain("The 3 you collected from others.")
    expect(markup).toContain('href="/glyphs?tab=mine"')
    expect(markup).toContain('href="/glyphs?tab=favourites"')
    expect(markup).toContain('href="/glyphs?tab=market"')
  })

  it("keep the line without a number when no count is served", () => {
    const markup = html(createElement(YouGlyphs, { carved: null, owned: null }))
    expect(markup).toContain("The marks you carved.")
    expect(markup).toContain("The ones you collected from others.")
  })

  it("invite to carve at zero", () => {
    expect(html(createElement(YouGlyphs, { carved: 0, owned: 0 }))).toContain("Carve your first mark.")
  })
})

describe("the achievements", () => {
  it("head with the served count, a link to the page", () => {
    const markup = html(createElement(YouAchievementsList, { badges: DESIGN_YOU_BADGES, count: 12 }))
    expect(markup).toMatch(/<a href="\/achievements"[^>]*><h2[^>]*>Achievements<\/h2><span class="pb-type-figure[^"]*">12 <small[^>]*>earned<\/small><\/span><\/a>/)
  })

  it("show four cards on a phone, six from sm", () => {
    const markup = html(createElement(YouAchievementsList, { badges: DESIGN_YOU_BADGES, count: 12 }))
    expect(count(markup, /<li>/g)).toBe(6)
    expect(markup).toContain("grid-cols-4")
    expect(markup).toContain("sm:grid-cols-6")
    expect(markup).toContain("max-sm:[&amp;&gt;li:nth-child(n+5)]:hidden")
  })

  it("keep the head and its zero with nothing earned", () => {
    const markup = html(createElement(YouAchievementsList, { badges: [], count: 0 }))
    expect(markup).toContain("0 <small")
    expect(markup).not.toContain("<ul")
  })
})

describe("the lab", () => {
  const markup = html(
    createElement(YouLabView, { announcement: DESIGN_LAB_ANNOUNCEMENT, changelog: DESIGN_LAB_CHANGELOG, locale: "en" }),
  )

  it("opens the community in a new tab, and says so", () => {
    expect(markup).toMatch(/<a href="https:\/\/chat\.whatsapp\.com\/[^"]+" target="_blank" rel="noreferrer noopener"/)
    expect(markup).toContain('<span class="sr-only"> (opens in a new tab)</span>')
  })

  it("dates three shipped features", () => {
    const timeline = markup.slice(markup.indexOf("<ol>"), markup.indexOf("</ol>"))
    expect(count(timeline, /<li /g)).toBe(3)
    expect(count(timeline, /<time dateTime="[^"]+"/g)).toBe(3)
    expect(timeline).toContain('<time dateTime="2026-10-29T09:00:00Z"')
    expect(markup).toContain('href="/lab/changelog"')
  })

  it("links the announcement under its kicker", () => {
    expect(markup).toContain('href="/lab/announcements/design-announcement"')
    expect(markup).toContain("Lab notes · 31 October")
  })

  it("keeps the community when both reads fail", () => {
    const failed = html(createElement(YouLabView, { announcement: null, changelog: [], locale: "en", failed: true, onRetry: () => {} }))
    expect(failed).toContain("Couldn&#x27;t load the Lab&#x27;s news.")
    expect(failed).toContain("Join the community")
  })
})

describe("the page", () => {
  const markup = html(createElement(YouScreen))

  it("has no log out (it lives in Settings, D20)", () => {
    expect(markup).not.toContain(en.profile.logout)
    expect(markup).not.toContain(en.profile.loggingOut)
  })

  it("is headed by the name, with the null glyph counts read as lines without numbers", () => {
    expect(markup).toMatch(/<h1[^>]*>Alexis<\/h1>/)
    expect(markup).toContain("The marks you carved.")
  })
})
