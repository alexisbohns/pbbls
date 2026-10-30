"use client"

import { useEffect, useRef } from "react"
import { Sheet, SheetContent } from "@/components/ui/sheet"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import type { RowOpens, SheetNames } from "@/lib/achievements/sheet-rows"
import { sheetId, type SheetKey } from "@/lib/achievements/sheets"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type { AchievementCard, AchievementCompletion, AchievementsPage, CeremonyStack } from "@/lib/types"
import { ReadyMark } from "@/components/achievements/page/ReadyMark"
import { DomainsSheet, EmotionsSheet, ExplorerSheet, PebblesSheet, YearSheet } from "./AchievementSheets"
import { InSheetContext } from "./SheetParts"

type StackProps = {
  /** The sheets still shown, bottom first (`shownStack`). */
  stack: readonly SheetKey[]
  page: AchievementsPage
  palettes: AchievementPalettes
  names: SheetNames
  pulling: CeremonyStack | null
  onPull: (stack: CeremonyStack) => void
  /** A row pushed a sheet on top. */
  onPush: (key: SheetKey) => void
  /** The sheet at `depth` was dismissed: it and everything above it close. */
  onClose: (depth: number) => void
  /** A row opens its pebble: the sheets close first. */
  onOpenMoment: (momentId: string) => void
  /** A sheet opened: its card is marked seen, once per open. */
  onSeen: (card: AchievementCard) => void
}

/**
 * The achievements sheets (#1308, spec §4 "Interaction"), iOS's
 * `AchievementSheetHost`: each sheet nests inside the one below it, so
 * dismissing pops only the top; a row opens its pebble (the sheets close
 * first) or pushes a sheet on top; a sheet whose section leaves the payload
 * has already left `stack`.
 */
export function AchievementSheetStack(props: StackProps) {
  return props.stack.length > 0 ? <Layer {...props} depth={0} /> : null
}

function Layer(props: StackProps & { depth: number }) {
  const { stack, depth, page, palettes, names, pulling, onPull, onPush, onClose, onOpenMoment, onSeen } = props
  const key = stack[depth]
  const words = useAchievementsWords()
  const marked = useRef<string | null>(null)
  const id = sheetId(key)

  useEffect(() => {
    // Once per open: a sheet re-rendered by a later read is the same open.
    if (marked.current === id) return
    marked.current = id
    onSeen(key.card)
  }, [id, key.card, onSeen])

  const open = (opens: RowOpens) => (opens.kind === "moment" ? onOpenMoment(opens.momentId) : onPush(opens.sheet))
  const ready = (completion: AchievementCompletion | null) =>
    completion?.ready ? (
      <ReadyMark text={words.ready()} label={words.a11y.ready(completion.stack)} disabled={pulling !== null} onPull={() => onPull(completion.stack)} />
    ) : undefined

  let content = null
  if (key.card === "explorer" && page.explorer) {
    content = <ExplorerSheet explorer={page.explorer} emotions={page.emotions} domains={page.domains} palettes={palettes} open={open} ready={ready(page.explorer.completion)} />
  } else if (key.card === "emotions" && page.emotions) {
    content = <EmotionsSheet emotions={page.emotions} names={names} palettes={palettes} open={open} ready={ready(page.emotions.completion)} />
  } else if (key.card === "domains" && page.domains) {
    content = <DomainsSheet domains={page.domains} names={names} palettes={palettes} open={open} ready={ready(page.domains.completion)} />
  } else if (key.card === "pebbles" && page.pebbles) {
    content = <PebblesSheet pebbles={page.pebbles} carved={page.glyphsCarved} sales={page.glyphSales} palettes={palettes} open={open} />
  } else if (key.card === "year") {
    const year = page.years.find((y) => y.year === key.year)
    if (year) content = <YearSheet year={year} years={page.years} palettes={palettes} open={open} />
  }

  return (
    <Sheet open onOpenChange={(isOpen) => (isOpen ? undefined : onClose(depth))}>
      <SheetContent className="bg-spine-stock text-spine-ink md:w-[440px]">
        <InSheetContext value>{content}</InSheetContext>
        {depth + 1 < stack.length ? <Layer {...props} depth={depth + 1} /> : null}
      </SheetContent>
    </Sheet>
  )
}
