"use client"

import type { ReactNode } from "react"
import { DomainBand } from "@/components/achievements/figures/DomainBand"
import { EmotionRosace } from "@/components/achievements/figures/EmotionRosace"
import { ExplorerWheel } from "@/components/achievements/figures/ExplorerWheel"
import { PebbleSkyline } from "@/components/achievements/figures/PebbleSkyline"
import { YearDisc } from "@/components/achievements/figures/YearDisc"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import {
  domainSections,
  earlierYears,
  emotionSections,
  explorerSections,
  ladderSection,
  milestonesNote,
  yearRows,
  type RowOpens,
  type SheetNames,
} from "@/lib/achievements/sheet-rows"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type { AchievementYear, AchievementsDomains, AchievementsEmotions, AchievementsExplorer, CountingLadder } from "@/lib/types"
import { SectionHead, SheetBody, SheetSection, YearColumnsHead, YearRowView } from "./SheetParts"

// The five sheets (#1308, the boards' `sheetExplorer` … `sheetYear`), iOS's
// `AchievementSheets`: each the figure large, then its register.

type Shared = { palettes: AchievementPalettes; open: (opens: RowOpens) => void; ready?: ReactNode }

/** Explorer: the wheel large, a section per ring inside out, then the rows that open Domains and Emotions on top. */
export function ExplorerSheet({ explorer, emotions, domains, palettes, open, ready }: Shared & { explorer: AchievementsExplorer; emotions: AchievementsEmotions | null; domains: AchievementsDomains | null }) {
  const words = useAchievementsWords()
  return (
    <SheetBody band={words.cardTitle("explorer")} hint={words.explorerHint()} ready={ready} figure={<ExplorerWheel explorer={explorer} emotions={emotions} domains={domains} palettes={palettes} size={340} />}>
      {explorerSections(explorer, emotions, domains, words).map((section) => (
        <SheetSection key={section.id} section={section} palettes={palettes} emotions={emotions} open={open} />
      ))}
    </SheetBody>
  )
}

/** Emotions: the rosace large, then the server's families in its order. */
export function EmotionsSheet({ emotions, names, palettes, open, ready }: Shared & { emotions: AchievementsEmotions; names: SheetNames }) {
  const words = useAchievementsWords()
  return (
    <SheetBody
      band={words.cardTitle("emotions")}
      hint={emotions.since ? words.sinceHint(emotions.since) : null}
      ready={ready}
      figure={<EmotionRosace emotions={emotions} palettes={palettes} size={330} />}
    >
      {emotionSections(emotions, names, words).map((section) => (
        <SheetSection key={section.id} section={section} palettes={palettes} emotions={emotions} open={open} />
      ))}
    </SheetBody>
  )
}

/** Domains: the band, what the coverage means, the register newest first (numbered by arrival), then not yet. */
export function DomainsSheet({ domains, names, palettes, open, ready }: Shared & { domains: AchievementsDomains; names: SheetNames }) {
  const words = useAchievementsWords()
  return (
    <SheetBody band={words.cardTitle("domains")} hint={words.sheet.text("register")} lead={words.coverage(domains.coverage)} ready={ready} figure={<DomainBand domains={domains} />}>
      {domainSections(domains, names, words).map((section) => (
        <SheetSection key={section.id} section={section} palettes={palettes} open={open} />
      ))}
    </SheetBody>
  )
}

/** Pebbles: the skyline, the pebbles kept, the milestones, then the glyph stacks in the same grammar, smaller. */
export function PebblesSheet({ pebbles, carved, sales, palettes, open }: Shared & { pebbles: CountingLadder; carved: CountingLadder | null; sales: CountingLadder | null }) {
  const words = useAchievementsWords()
  return (
    <SheetBody
      band={words.cardTitle("pebbles")}
      hint={pebbles.since ? words.sinceHint(pebbles.since) : null}
      lead={words.pebblesLead(pebbles)}
      figure={<PebbleSkyline stack={pebbles} width={350} />}
    >
      <SheetSection section={ladderSection(pebbles, "milestones", words.sheet.text("milestones"), milestonesNote(pebbles, words), words)} palettes={palettes} open={open} />
      {carved ? (
        <SheetSection section={ladderSection(carved, "glyphs-carved", words.cardTitle("glyphsCarved"), words.number(carved.count), words)} palettes={palettes} compact open={open} />
      ) : null}
      {sales ? (
        <SheetSection
          section={ladderSection(sales, "glyph-sales", words.cardTitle("glyphSales"), words.number(sales.count), words, words.sheet.text("buyerNeverNamed"))}
          palettes={palettes}
          compact
          open={open}
        />
      ) : null}
    </SheetBody>
  )
}

/** The year: the docked disc large, the months the server lists (not buttons), the next step, then the earlier years. */
export function YearSheet({ year, years, palettes, open }: Shared & { year: AchievementYear; years: readonly AchievementYear[] }) {
  const words = useAchievementsWords()
  const step = year.nextStep ? words.nextStep(year.nextStep) : null
  return (
    <SheetBody band={String(year.year)} hint={words.yearHint(year)} figure={<YearDisc year={year} palettes={palettes} size={330} />}>
      <section className="pt-7">
        <SectionHead title={words.sheet.text("months")} note={words.sheet.text("newestFirst")} />
        <YearColumnsHead />
        {yearRows(year, words).map((row) => (
          <YearRowView key={row.month.month} row={row} palettes={palettes} />
        ))}
        {step ? <p className="pt-3.5 text-[16.5px] leading-[1.35] font-[380] italic">{step}</p> : null}
      </section>
      <SheetSection section={earlierYears(year, years, words)} palettes={palettes} open={open} />
    </SheetBody>
  )
}
