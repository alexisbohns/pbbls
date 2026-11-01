import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { createElement, type ComponentProps, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import AchievementsLayout from "@/app/@detail/(pane)/achievements/layout"
import { AchievementsPageContent } from "@/components/achievements/page/AchievementsPageContent"
import { YearDisc } from "@/components/achievements/figures/YearDisc"
import { stoneBodyId } from "@/lib/utils/spine/stone-ids"
import { designPage, designPalettes } from "./__fixtures__/design"
import type { AchievementsPage } from "@/lib/types"

// The page rendered to markup (react-dom/server, no DOM) from the canonical
// design fixture: what it shows and what it reads aloud.

const page = designPage()
const palettes = designPalettes()

function html(element: ReactElement): string {
  const intl: ComponentProps<typeof NextIntlClientProvider> = { locale: "en", messages: en, timeZone: "UTC", children: element }
  return renderToStaticMarkup(createElement(NextIntlClientProvider, intl))
}

const count = (markup: string, needle: string) => markup.split(needle).length - 1

describe("the achievements layout", () => {
  it("leaves the Spine's stone sprite to the app shell, which mounts it once beside every detail (#1384)", () => {
    const props: ComponentProps<typeof AchievementsLayout> = { children: createElement("main") }
    const markup = renderToStaticMarkup(createElement(AchievementsLayout, props))
    expect(markup).toBe("<main></main>")
    expect(count(markup, `id="${stoneBodyId("medium", "neutral")}"`)).toBe(0)
    const shell = readFileSync(fileURLToPath(new URL("../../components/layout/MainContent.tsx", import.meta.url)), "utf8")
    expect(count(shell, "<StoneSprite />")).toBe(1)
  })
})

describe("the page, from the design fixture", () => {
  const markup = html(createElement(AchievementsPageContent, { page, palettes, onPull: () => {} }))

  it("reads each card as its assembled label", () => {
    expect(markup).toContain("249 days, since Sunday, March 1, 2026")
    expect(markup).toContain(
      "Explorer: the first five, done; the six firsts, done; 25 pebbles, done; domains, 14 of 18; emotions, 23 of 38. 3 new.",
    )
    expect(markup).toContain("Emotions, 23 of 38. 2 new.")
    expect(markup).toContain("Domains, 14 of 18. Your pebbles reach most parts of your life. Your insights have plenty to read. 1 new.")
    expect(markup).toContain("Pebbles. 137 pebbles. Five stones stand; the sixth rises toward 250. Glyphs carved: 12. Glyph sales: 3.")
    expect(markup).toContain("The year 2026. On the rim: August.")
  })

  it("shows the words the server's values pick", () => {
    expect(markup).toContain("Twelve more days with a moment, one in each full week, bring November to the inner disc.")
    expect(markup).toContain("in the order they came")
    expect(markup).toContain(">2026<")
  })

  it("puts no ready mark where the server says nothing is ready", () => {
    expect(markup).not.toContain("the ceremony is ready")
  })

  it("puts a ready mark on a ready set, and stills every mark while a pull runs", () => {
    const ready: AchievementsPage = {
      ...page,
      explorer: page.explorer && {
        ...page.explorer,
        completion: { stack: "six_firsts", completedAt: null, offeredFrom: null, ready: true, pulledAt: null, pebble: null },
      },
    }
    const idle = html(createElement(AchievementsPageContent, { page: ready, palettes, onPull: () => {} }))
    expect(idle).toContain('aria-label="The six firsts: the ceremony is ready."')
    expect(idle).toContain(">ready<")
    // The ready mark takes the hint's place in the band.
    expect(idle).not.toContain("five rings")
    const pulling = html(createElement(AchievementsPageContent, { page: ready, palettes, pulling: "emotions", onPull: () => {} }))
    expect(pulling).toMatch(/<button[^>]*disabled=""[^>]*aria-label="The six firsts: the ceremony is ready."/)
  })

  it("hides the next step when the server's need is unknown, and every card whose section is absent", () => {
    const year = page.years[0]
    const sparse: AchievementsPage = {
      ...page,
      emotions: null,
      domains: null,
      glyphSales: null,
      years: [{ ...year, nextStep: year.nextStep && { ...year.nextStep, needs: null } }],
    }
    const out = html(createElement(AchievementsPageContent, { page: sparse, palettes }))
    expect(out).not.toContain("more days with")
    expect(out).not.toContain("Emotions, 23")
    expect(out).not.toContain("Glyph sales")
    expect(out).toContain("Explorer: the first five, done; the six firsts, done; 25 pebbles, done.<")
  })
})

describe("the year disc outside a card", () => {
  it("reads as one image when given its words, and stays hidden otherwise", () => {
    const labelled = html(createElement(YearDisc, { year: page.years[0], palettes, label: "The year 2026." }))
    expect(labelled).toMatch(/^<svg role="img" aria-label="The year 2026\."/)
    expect(html(createElement(YearDisc, { year: page.years[0], palettes }))).toMatch(/^<svg aria-hidden="true"/)
  })
})
