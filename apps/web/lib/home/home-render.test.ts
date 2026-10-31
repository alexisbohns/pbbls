import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { createElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it, vi } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { DataContext, type DataContextValue } from "@/lib/data/provider-context"
import { EMPTY_STORE } from "@/lib/data/data-provider"
import { decodeHomeMonth, decodeHomeWraps } from "@/lib/data/home-payloads"
import { firstFiveState, FIRST_FIVE_ORDER, BLUE_PEBBLE } from "@/lib/home/first-five"
import { HomeMonthGrid } from "@/components/home/HomeMonthGrid"
import { HomeGreetingLines } from "@/components/home/HomeGreetingLines"
import { QuickActions } from "@/components/home/QuickActions"
import { FirstFiveBox } from "@/components/home/FirstFiveBox"
import { MonthlyWrapsList } from "@/components/home/MonthlyWrapsList"
import { AboutPebblesList } from "@/components/home/AboutPebblesList"

// next, lucide-react and @base-ui resolve the root workspace's React (19.3.0),
// not the one react-dom/server renders with here (apps/web's 19.3.1), so their
// hooks would read a null dispatcher. Plain elements stand in for the link, the
// chevron, the button and the dialog; the markup under test is Home's own.
type Props = { children?: React.ReactNode } & Record<string, unknown>
vi.mock("next/link", () => ({
  default: ({ href, children, ...rest }: Props & { href: string }) => createElement("a", { href, ...rest }, children),
}))
vi.mock("lucide-react", () => ({
  ChevronRight: ({ className, ...rest }: Props & { className?: string }) =>
    createElement("svg", { ...rest, className: `lucide lucide-chevron-right ${className ?? ""}` }),
}))
vi.mock("@/components/ui/button", () => ({
  Button: ({ children, ...rest }: Props) => {
    const { variant, size, ...dom } = rest
    void variant
    void size
    return createElement("button", dom, children)
  },
  buttonVariants: () => "",
}))
vi.mock("@/components/ui/dialog", () => {
  const pass = ({ children }: Props) => createElement("div", null, children)
  return {
    Dialog: ({ open, children }: Props & { open?: boolean }) => (open ? createElement("div", { role: "dialog" }, children) : null),
    DialogContent: pass,
    DialogHeader: pass,
    DialogFooter: pass,
    DialogTitle: ({ children }: Props) => createElement("h2", null, children),
    DialogDescription: ({ children }: Props) => createElement("p", null, children),
  }
})

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: () => {}, replace: () => {} }),
  usePathname: () => "/home",
}))

// Home rendered to markup (react-dom/server, no DOM) from the captured payloads:
// what it shows and what it reads aloud (#1384, spec §Accessibility).
const webRoot = fileURLToPath(new URL("../..", import.meta.url))
const oracle = (rel: string): unknown =>
  JSON.parse(readFileSync(path.join(webRoot, "../../packages/supabase/scripts/oracles", rel), "utf8"))

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

const count = (markup: string, needle: string | RegExp) =>
  typeof needle === "string" ? markup.split(needle).length - 1 : (markup.match(needle) ?? []).length

/** The markup with every aria-hidden subtree cut out (no nesting of the same tag inside one is assumed). */
function visibleToReaders(markup: string): string {
  let out = markup
  for (const tag of ["div", "svg", "span"]) {
    out = out.replace(new RegExp(`<${tag}[^>]*aria-hidden="true"[^>]*>[\\s\\S]*?</${tag}>`, "g"), "")
  }
  return out
}

describe("the month grid", () => {
  const month = decodeHomeMonth(oracle("path-wrap/payloads/month-full.json"))
  if (!month) throw new Error("month-full did not decode")
  const markup = html(createElement(HomeMonthGrid, { month, locale: "en", paletteFor: () => null }))

  it("is one link per week, named by its range and its pebbles", () => {
    expect(count(markup, /<a [^>]*href="\/path\?week=/g)).toBe(5)
    expect(markup).toContain('href="/path?week=2026-10-05"')
    expect(markup).toContain('aria-label="5 – 11 October, 11 pebbles"')
    expect(markup).toContain('aria-label="1 – 4 October, 1 pebble"')
  })

  it("names the navigation by the month, and hides the legend", () => {
    expect(markup).toMatch(/<nav aria-labelledby="[^"]+"><span id="[^"]+" class="sr-only">October 2026<\/span>/)
    expect(markup).toMatch(/<div aria-hidden="true" class="flex w-4 flex-col/)
  })

  it("keeps every seal, socket and cairn inside an aria-hidden subtree", () => {
    expect(markup).toContain("<use")
    expect(visibleToReaders(markup)).not.toContain("<use")
    expect(visibleToReaders(markup)).not.toContain("<svg")
  })

  it("lays out an empty month with dotted cairns", () => {
    const empty = html(
      createElement(HomeMonthGrid, {
        month: { month: "2026-10-01", daysInMonth: 31, firstWeekday: 3, count: 0, days: [], weeks: null },
        locale: "en",
        paletteFor: () => null,
      }),
    )
    expect(empty).toContain('aria-label="1 – 4 October, no pebble yet"')
    expect(count(empty, 'stroke-dasharray="2 2"')).toBe(5)
  })
})

describe("the greeting", () => {
  it("is the page's heading", () => {
    const markup = html(
      createElement(HomeGreetingLines, { now: new Date("2026-10-31T19:00:00Z"), name: "Alexis", locale: "en", timeZone: "Europe/Paris" }),
    )
    expect(markup).toContain(">Saturday 31 October<")
    expect(markup).toMatch(/<h1[^>]*>Good evening, Alexis<\/h1>/)
    expect(markup).toContain("What stays with you from today?")
  })
})

describe("the quick actions", () => {
  const markup = html(createElement(QuickActions, { onQuickMoment: () => {} }))

  it("are three controls named by their visible labels", () => {
    expect(markup).toMatch(/<label[^>]*>.*<input type="file" accept="image\/\*" capture="environment" class="sr-only"/)
    expect(markup).toContain("<span>Camera</span>")
    expect(markup).toMatch(/<button type="button"[^>]*>.*<span>Quick moment<\/span><\/button>/)
    expect(markup).toMatch(/<a [^>]*href="\/record"[^>]*>.*<span>New pebble<\/span><\/a>/)
  })

  it("hide their drawings", () => {
    expect(count(markup, /<svg[^>]*aria-hidden="true"/g)).toBe(3)
  })
})

describe("the first five", () => {
  const catalog = new Set([...FIRST_FIVE_ORDER, BLUE_PEBBLE])
  const props = { hrefFor: () => "/collections", onQuickMoment: () => {}, onClaim: () => {} }

  it("reads 2 of 5, done rows as plain text and open rows as links with a chevron", () => {
    const state = firstFiveState(catalog, new Set(["first-moment", "first-pebble"]))
    const markup = html(createElement(FirstFiveBox, { ...props, state }))
    expect(markup).toContain('2 <small class="pb-type-figure-small text-m3-on-surface-variant">of</small> 5')
    expect(markup).toContain('Record your first moment<span class="sr-only">, Done</span>')
    expect(count(markup, /<li><div class="flex w-full/g)).toBe(2)
    expect(count(markup, /<li><a /g)).toBe(3)
    expect(count(markup, "lucide-chevron-right")).toBe(3)
    expect(markup).not.toContain("Claim the blue pebble")
  })

  it("offers the claim once all five are done", () => {
    const markup = html(createElement(FirstFiveBox, { ...props, state: firstFiveState(catalog, new Set(FIRST_FIVE_ORDER)) }))
    expect(markup).toMatch(/<button[^>]*>Claim the blue pebble<\/button>/)
    expect(count(markup, "lucide-chevron-right")).toBe(0)
  })

  it("opens the quick moment for an open first-moment task", () => {
    const markup = html(createElement(FirstFiveBox, { ...props, state: firstFiveState(catalog, new Set()) }))
    expect(markup).toMatch(/<li><button type="button"[^>]*>.*Record your first moment/)
  })
})

describe("the monthly wraps", () => {
  const base = { currentMonth: "2026-10-01", locale: "en", onReveal: async () => {} }

  it("asks to unfold a folded month", () => {
    const wraps = decodeHomeWraps(oracle("home/payloads/home-wraps.json"))
    const markup = html(createElement(MonthlyWrapsList, { ...base, wraps }))
    expect(markup).toMatch(/<button type="button" aria-label="September 2026, 4 pebbles, folded"/)
    expect(markup).toContain(">folded<")
  })

  it("links a revealed month to the Path at its first week", () => {
    const wraps = [{ month: "2026-09-01", count: 23, revealedAt: new Date("2026-10-01T08:00:00Z"), days: [0, 3] }]
    const markup = html(createElement(MonthlyWrapsList, { ...base, wraps }))
    expect(markup).toContain('href="/path?week=2026-08-31" aria-label="September 2026, 23 pebbles"')
    expect(markup).toContain(">1<")
  })

  it("shows one dashed card before the first finished month", () => {
    const markup = html(createElement(MonthlyWrapsList, { ...base, wraps: [] }))
    expect(markup).toContain("border-dashed")
    expect(markup).toContain("Your first wrap folds on 31 October.")
  })
})

describe("About Pebbles", () => {
  it("links each guide to its page, and is only a head without guides", () => {
    const guides = [{ slug: "stones", illustration: "stones" as const, title: "Stones", summary: "Nine shapes.", bodyMd: "" }]
    expect(html(createElement(AboutPebblesList, { guides }))).toContain('href="/guides/stones"')
    expect(html(createElement(AboutPebblesList, { guides: [] }))).not.toContain("<ul")
  })
})
