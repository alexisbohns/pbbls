import { createElement, type ReactElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { NextIntlClientProvider } from "next-intl"
import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import { DomainBand } from "@/components/achievements/figures/DomainBand"
import { EmotionRosace } from "@/components/achievements/figures/EmotionRosace"
import { ExplorerWheel } from "@/components/achievements/figures/ExplorerWheel"
import { PebbleSkyline } from "@/components/achievements/figures/PebbleSkyline"
import { YearDisc } from "@/components/achievements/figures/YearDisc"
import { designPage, designPalettes } from "./__fixtures__/design"
import type { AchievementsEmotions } from "@/lib/types"

// The figures rendered to markup from the canonical design fixture (no DOM:
// react-dom/server): each draws, the same way twice, what its model says.

const page = designPage()
const palettes = designPalettes()

function html(element: ReactElement): string {
  return renderToStaticMarkup(createElement(NextIntlClientProvider, { locale: "en", messages: en, timeZone: "UTC", children: element }))
}

const count = (markup: string, needle: string) => markup.split(needle).length - 1

function sections() {
  const { explorer, emotions, domains, pebbles, glyphsCarved, years } = page
  if (!explorer || !emotions || !domains || !pebbles || !glyphsCarved) throw new Error("fixture lost a section")
  return { explorer, emotions, domains, pebbles, glyphsCarved, year: years[0] }
}
const s = sections()

describe("the figures, drawn from the design fixture", () => {
  it("draw deterministically", () => {
    const wheel = () => html(createElement(ExplorerWheel, { explorer: s.explorer, emotions: s.emotions, domains: s.domains, palettes }))
    expect(wheel()).toBe(wheel())
    const disc = () => html(createElement(YearDisc, { year: s.year, palettes }))
    expect(disc()).toBe(disc())
  })

  it("the wheel ghosts what is not yet, and marks only what the server flags new", () => {
    const markup = html(createElement(ExplorerWheel, { explorer: s.explorer, emotions: s.emotions, domains: s.domains, palettes }))
    // 15 emotions and 4 domains not yet; the 25, the six and the five all done.
    expect(count(markup, 'stroke-dasharray="1.2 1.6"')).toBe(19)
    // Two emotions and one domain are new: a ring and a dot each.
    expect(count(markup, 'r="1.9"')).toBe(3)
  })

  it("the rosace lights its 23 met kites, names the seven families and counts as served", () => {
    const markup = html(createElement(EmotionRosace, { emotions: s.emotions, palettes }))
    expect(count(markup, "fill-(--tone-paper)")).toBeGreaterThanOrEqual(23)
    expect(markup).toContain(">23<")
    expect(markup).toContain(">of 38<")
    expect(markup).toContain(">Joy<")
    expect(count(markup, 'r="2.6"')).toBe(2)
  })

  it("the rosace lights and names a family it cannot resolve in ink", () => {
    const emotions: AchievementsEmotions = {
      ...s.emotions,
      seats: s.emotions.seats.map((seat, k) => (k < 6 ? { ...seat, family: "awe" } : seat)),
    }
    const markup = html(createElement(EmotionRosace, { emotions, palettes }))
    expect(markup).toContain(">awe<")
    expect(markup).toMatch(/class="[^"]*fill-spine-ink[^"]*"[^>]*>awe</)
    expect(count(markup, 'class="fill-spine-ink"')).toBeGreaterThanOrEqual(5)
  })

  it("the band inks the 14 that came and dots the 4 not yet", () => {
    const markup = html(createElement(DomainBand, { domains: s.domains }))
    expect(count(markup, 'stroke-width="1.3"')).toBe(14)
    expect(count(markup, 'stroke-dasharray="1.2 2.2"')).toBe(4)
    expect(count(markup, 'r="2.4"')).toBe(1)
  })

  it("the skyline stands five stages and raises the sixth", () => {
    const markup = html(createElement(PebbleSkyline, { stack: s.pebbles }))
    expect(count(markup, 'fill="var(--spine-ink)"')).toBe(6)
    expect(count(markup, "<clipPath")).toBe(1)
    expect(markup).toContain('stroke="var(--spine-ink-2)"')
  })

  it("the disc carries its forty grains, rings August alone and dots December", () => {
    const markup = html(createElement(YearDisc, { year: s.year, palettes }))
    expect(count(markup, 'fill="var(--spine-ink-3)"')).toBe(40)
    expect(count(markup, 'r="9.75"')).toBe(1)
    expect(markup).toMatch(/font-weight="600"[^>]*>VIII</)
    expect(count(markup, 'stroke-dasharray="1.6 1.7"')).toBe(1)
  })
})
