"use client"

import type { ReactNode } from "react"
import { DomainBand } from "@/components/achievements/figures/DomainBand"
import { EmotionRosace } from "@/components/achievements/figures/EmotionRosace"
import { ExplorerWheel } from "@/components/achievements/figures/ExplorerWheel"
import { PebbleSkyline } from "@/components/achievements/figures/PebbleSkyline"
import { YearDisc } from "@/components/achievements/figures/YearDisc"
import { countingLadder, domainsLadder, emotionsLadder, explorerLadder, yearLadder } from "@/lib/achievements/ladders"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type {
  AchievementYear,
  AchievementsDomains,
  AchievementsEmotions,
  AchievementsExplorer,
  CountingLadder,
} from "@/lib/types"
import { AchievementCard } from "./AchievementCard"

// The page's five cards (#1308, spec D1), each a shell over its figure. No
// legends and no "new since" lines: new items are marked on the figures only.
// A card's label, ladder and hint come from the payload. iOS `AchievementsCards`.

const LINE = "mt-2.5 text-center text-base leading-[1.35] font-[380] text-pretty italic"

type Shared = { onOpen?: () => void; ready?: ReactNode }

export function ExplorerCard({
  explorer,
  emotions,
  domains,
  palettes,
  onOpen,
  ready,
}: Shared & { explorer: AchievementsExplorer; emotions: AchievementsEmotions | null; domains: AchievementsDomains | null; palettes: AchievementPalettes }) {
  const words = useAchievementsWords()
  return (
    <AchievementCard
      title={words.cardTitle("explorer")}
      hint={words.explorerHint()}
      ladder={explorerLadder(explorer, emotions, domains)}
      label={words.a11y.explorer(explorer, emotions, domains)}
      onOpen={onOpen}
      ready={ready}
    >
      <div className="flex justify-center pt-3">
        <ExplorerWheel explorer={explorer} emotions={emotions} domains={domains} palettes={palettes} />
      </div>
    </AchievementCard>
  )
}

export function EmotionsCard({ emotions, palettes, onOpen, ready }: Shared & { emotions: AchievementsEmotions; palettes: AchievementPalettes }) {
  const words = useAchievementsWords()
  return (
    <AchievementCard
      title={words.cardTitle("emotions")}
      hint={emotions.since ? words.sinceHint(emotions.since) : null}
      ladder={emotionsLadder(emotions)}
      label={words.a11y.emotions(emotions)}
      onOpen={onOpen}
      ready={ready}
    >
      <div className="flex justify-center pt-3">
        <EmotionRosace emotions={emotions} palettes={palettes} />
      </div>
    </AchievementCard>
  )
}

export function DomainsCard({ domains, onOpen, ready }: Shared & { domains: AchievementsDomains }) {
  const words = useAchievementsWords()
  const meaning = words.coverage(domains.coverage)
  return (
    <AchievementCard
      title={words.cardTitle("domains")}
      hint={words.domainsHint()}
      longBar={domainsLadder(domains)}
      label={words.a11y.domains(domains)}
      onOpen={onOpen}
      ready={ready}
    >
      <div className="flex justify-center pt-4">
        <DomainBand domains={domains} />
      </div>
      {meaning ? <p className={LINE}>{meaning}</p> : null}
    </AchievementCard>
  )
}

function GlyphRow({ name, stack }: { name: string; stack: CountingLadder }) {
  const words = useAchievementsWords()
  return (
    <div className="flex min-h-11 flex-wrap items-center gap-x-2 border-b border-spine-rule py-1">
      <span className="min-w-[104px] font-spine-sc text-[11px] font-semibold tracking-[.14em] text-spine-ink-2">{name}</span>
      <span className="flex flex-1 items-center gap-2">
        <span className="flex flex-1 justify-center">
          <PebbleSkyline stack={stack} view="narrow" />
        </span>
        <span className="min-w-[34px] text-right font-spine-office text-xl font-light tabular-nums">{words.number(stack.count)}</span>
      </span>
    </div>
  )
}

export function PebblesCard({
  pebbles,
  carved,
  sales,
  onOpen,
}: Shared & { pebbles: CountingLadder; carved: CountingLadder | null; sales: CountingLadder | null }) {
  const words = useAchievementsWords()
  return (
    <AchievementCard
      title={words.cardTitle("pebbles")}
      hint={pebbles.since ? words.sinceHint(pebbles.since) : null}
      ladder={countingLadder(pebbles)}
      label={words.a11y.pebbles(pebbles, carved, sales)}
      onOpen={onOpen}
    >
      <div className="flex justify-center pt-5.5">
        <PebbleSkyline stack={pebbles} />
      </div>
      <p className={LINE}>{words.pebblesLine(pebbles)}</p>
      {carved || sales ? (
        <div className="mt-3.5 border-t border-spine-rule">
          {carved ? <GlyphRow name={words.cardTitle("glyphsCarved")} stack={carved} /> : null}
          {sales ? <GlyphRow name={words.cardTitle("glyphSales")} stack={sales} /> : null}
        </div>
      ) : null}
    </AchievementCard>
  )
}

export function YearCard({ year, palettes, onOpen }: Shared & { year: AchievementYear; palettes: AchievementPalettes }) {
  const words = useAchievementsWords()
  const step = year.nextStep ? words.nextStep(year.nextStep) : null
  return (
    <AchievementCard
      title={words.cardTitle("year")}
      heading={String(year.year)}
      hint={words.yearHint(year)}
      ladder={yearLadder(year)}
      label={words.a11y.year(year)}
      onOpen={onOpen}
    >
      <div className="flex justify-center pt-3">
        <YearDisc year={year} palettes={palettes} />
      </div>
      {step ? <p className={LINE}>{step}</p> : null}
    </AchievementCard>
  )
}
