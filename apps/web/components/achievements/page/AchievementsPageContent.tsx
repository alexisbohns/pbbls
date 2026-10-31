"use client"

import type { AchievementPalettes } from "@/lib/achievements/palette"
import type { SheetKey } from "@/lib/achievements/sheets"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type { AchievementCompletion, AchievementsPage, CeremonyStack } from "@/lib/types"
import { AchievementsHeader } from "./AchievementsHeader"
import { DomainsCard, EmotionsCard, ExplorerCard, PebblesCard, YearCard } from "./AchievementsCards"
import { ReadyMark } from "./ReadyMark"

type AchievementsPageContentProps = {
  page: AchievementsPage
  palettes: AchievementPalettes
  /** The stack being pulled: every ready mark waits while any pull runs. */
  pulling?: CeremonyStack | null
  onPull?: (stack: CeremonyStack) => void
  /** Opens a card's sheet; without it the cards are still. */
  onOpen?: (key: SheetKey) => void
}

/**
 * The page's content (#1308, spec D1): the header, then a card per section the
 * payload carries, in the boards' order; a section the payload omits hides its
 * card (spec §4). A completion the server marks ready puts its mark on its
 * card. One column on a phone, two from `md`. iOS `AchievementsPageContent`.
 */
export function AchievementsPageContent({ page, palettes, pulling = null, onPull, onOpen }: AchievementsPageContentProps) {
  const opens = (key: SheetKey) => (onOpen ? () => onOpen(key) : undefined)
  const words = useAchievementsWords()
  const ready = (completion: AchievementCompletion | null) =>
    completion?.ready && onPull ? (
      <ReadyMark
        text={words.ready()}
        label={words.a11y.ready(completion.stack)}
        disabled={pulling !== null}
        onPull={() => onPull(completion.stack)}
      />
    ) : null
  return (
    <>
      <AchievementsHeader since={page.since} daysKept={page.daysKept} />
      <div className="grid gap-3.5 md:grid-cols-2 md:items-start">
        {page.explorer ? (
          <ExplorerCard
            explorer={page.explorer}
            emotions={page.emotions}
            domains={page.domains}
            palettes={palettes}
            ready={ready(page.explorer.completion)}
            onOpen={opens({ card: "explorer" })}
          />
        ) : null}
        {page.emotions ? (
          <EmotionsCard emotions={page.emotions} palettes={palettes} ready={ready(page.emotions.completion)} onOpen={opens({ card: "emotions" })} />
        ) : null}
        {page.domains ? <DomainsCard domains={page.domains} ready={ready(page.domains.completion)} onOpen={opens({ card: "domains" })} /> : null}
        {page.pebbles ? (
          <PebblesCard pebbles={page.pebbles} carved={page.glyphsCarved} sales={page.glyphSales} onOpen={opens({ card: "pebbles" })} />
        ) : null}
        {page.years.map((year) => (
          <YearCard key={year.year} year={year} palettes={palettes} onOpen={opens({ card: "year", year: year.year })} />
        ))}
      </div>
    </>
  )
}
