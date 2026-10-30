import { createElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { DomainsSheet, EmotionsSheet, ExplorerSheet, PebblesSheet, YearSheet } from "@/components/achievements/sheets/AchievementSheets"
import { ReadyMark } from "@/components/achievements/page/ReadyMark"
import { AchievementsPageContent } from "@/components/achievements/page/AchievementsPageContent"
import { designNames, designPage, designPalettes } from "./__fixtures__/design"

// The five sheets rendered to markup (react-dom/server, no DOM) from the
// canonical design fixture: what they show and what they read aloud.

const page = designPage()
const palettes = designPalettes()
const names = designNames()
const open = () => {}

function html(element: ReactElement): string {
  const intl: ComponentProps<typeof NextIntlClientProvider> = { locale: "en", messages: en, timeZone: "UTC", children: element }
  return renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
}
const count = (markup: string, needle: string) => markup.split(needle).length - 1

function sections() {
  const { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, years } = page
  if (!explorer || !emotions || !domains || !pebbles || !glyphsCarved || !glyphSales) throw new Error("fixture lost a section")
  return { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, year: years[0] }
}
const s = sections()

describe("the sheets, from the design fixture", () => {
  it("Explorer: its rows open their pebble, Domains and Emotions open their own sheet", () => {
    const out = html(createElement(ExplorerSheet, { explorer: s.explorer, emotions: s.emotions, domains: s.domains, palettes, open }))
    expect(out).toContain(">The first five<")
    expect(out).toContain('aria-label="First pebble, Mara moves in, Mar 2"')
    expect(out).toContain('aria-label="Domains, 14 of 18"')
    expect(out).toContain(">Open the pebble<")
    expect(out).toContain(">Open<")
    // A collection opens no pebble: its row reads, but is not a button.
    expect(out).toMatch(/<p class="sr-only">First collection, Elio, year one, Apr 10<\/p>/)
  })

  it("Explorer: carries the six firsts' ready mark on its band while offered", () => {
    const ready = createElement(ReadyMark, { text: "ready", label: "The six firsts: the ceremony is ready.", disabled: false, onPull: () => {} })
    const out = html(createElement(ExplorerSheet, { explorer: s.explorer, emotions: s.emotions, domains: s.domains, palettes, open, ready }))
    expect(out).toContain('aria-label="The six firsts: the ceremony is ready."')
    expect(out).not.toContain(">five rings<")
  })

  it("Emotions: its families in the served order, what is not yet read as one", () => {
    const out = html(createElement(EmotionsSheet, { emotions: s.emotions, names, palettes, open }))
    expect(out).toContain(">since March 2<")
    expect(out).toMatch(/Not yet: [^<]+/)
    expect(out).toContain("new since you last looked")
  })

  it("Domains: the coverage's meaning, the register numbered by arrival", () => {
    const out = html(createElement(DomainsSheet, { domains: s.domains, names, palettes, open }))
    expect(out).toContain(">the register<")
    expect(out).toContain("Your pebbles reach most parts of your life.")
    expect(out).toContain(">No. 14<")
    expect(out).toContain(">Not yet in these pages<")
  })

  it("Pebbles: the lead, the milestones, the next with what is left, the glyph stacks", () => {
    const out = html(createElement(PebblesSheet, { pebbles: s.pebbles, carved: s.glyphsCarved, sales: s.glyphSales, palettes, open }))
    expect(out).toContain(">137 pebbles kept.<")
    expect(out).toContain(">since March 2<")
    expect(out).toContain(">113 to go<")
    expect(out).toContain(">then every 500<")
    expect(out).toContain(">The buyer is never named.<")
  })

  it("the year: month rows read aloud, not buttons, the next step under the table, the first-year line", () => {
    const out = html(createElement(YearSheet, { year: s.year, years: page.years, palettes, open }))
    expect(out).toContain("November. Days with a moment: 2. With a pebble: 2. No full week yet.")
    expect(out).toMatch(/August\. Days with a moment: 19, reached\./)
    expect(out).toContain("Twelve more days with a moment")
    expect(out).toContain(">None yet. 2026 is your first year here.<")
    expect(count(out, "<button")).toBe(0)
  })
})

describe("the cards open their sheets", () => {
  it("as buttons named by their labels", () => {
    const out = html(createElement(AchievementsPageContent, { page, palettes, onOpen: () => {} }))
    expect(out).toMatch(/<button type="button" aria-label="Explorer: the first five, done;/)
    expect(out).toMatch(/<button type="button" aria-label="The year 2026\./)
  })
})
