"use client"

import { useEffect, useRef, type Dispatch } from "react"
import { Sheet, SheetContent } from "@/components/ui/sheet"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import type { RowOpens, SheetNames } from "@/lib/achievements/sheet-rows"
import { sheetId, type SheetEntry, type SheetsAction, type SheetsState } from "@/lib/achievements/sheets"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type { AchievementCard, AchievementCompletion, AchievementsPage, CeremonyStack } from "@/lib/types"
import { ReadyMark } from "@/components/achievements/page/ReadyMark"
import { DomainsSheet, EmotionsSheet, ExplorerSheet, PebblesSheet, YearSheet } from "./AchievementSheets"
import { InSheetContext } from "./SheetParts"

type StackProps = {
  /** The stack's state (`sheetsReducer`): what is open, and what is still drawn while it closes. */
  sheets: SheetsState
  dispatch: Dispatch<SheetsAction>
  page: AchievementsPage
  palettes: AchievementPalettes
  names: SheetNames
  pulling: CeremonyStack | null
  onPull: (ceremony: CeremonyStack) => void
  /** A sheet opened: its card is marked seen, once per open. */
  onSeen: (card: AchievementCard) => void
  /** The sheets have finished closing on the way to a pebble: open it. */
  onMoment: (momentId: string) => void
}

/**
 * The achievements sheets (#1308, spec §4 "Interaction"), iOS's
 * `AchievementSheetHost`: each sheet nests inside the one below it, so
 * dismissing (Escape, the backdrop, the close control) pops only the top.
 * Sheets close through base UI, so their close plays before they leave; a
 * row's pebble opens once the bottom sheet has finished closing, as iOS opens
 * it on dismissal.
 */
export function AchievementSheetStack(props: StackProps) {
  const first = props.sheets.rendered[0]
  return first ? <Layer key={layerKey(first)} {...props} depth={0} /> : null
}

function layerKey(entry: SheetEntry): string {
  return `${sheetId(entry.key)}-${entry.open}`
}

function Layer(props: StackProps & { depth: number }) {
  const { sheets, dispatch, depth, page, palettes, names, pulling, onPull, onSeen, onMoment } = props
  const entry = sheets.rendered[depth]
  const isOpen = sheets.opened[depth]?.open === entry.open
  const words = useAchievementsWords()
  const popup = useRef<HTMLDivElement>(null)
  const key = entry.key

  useEffect(() => {
    // Once per open: a sheet re-rendered by a later read is the same open.
    if (!isOpen || sheets.seen.includes(entry.open)) return
    dispatch({ type: "seen", open: entry.open })
    onSeen(key.card)
  }, [isOpen, sheets.seen, entry.open, key.card, dispatch, onSeen])

  const open = (opens: RowOpens) =>
    dispatch(opens.kind === "moment" ? { type: "openMoment", momentId: opens.momentId } : { type: "open", key: opens.sheet })
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

  const above = sheets.rendered[depth + 1]
  return (
    <Sheet
      open={isOpen}
      onOpenChange={(next) => (next ? undefined : dispatch({ type: "close", depth }))}
      onOpenChangeComplete={(next) => {
        if (next) return
        // The bottom sheet has finished closing on the way to a pebble: open it now.
        if (depth === 0 && sheets.pendingMoment) {
          onMoment(sheets.pendingMoment)
          dispatch({ type: "movedOn" })
        }
        dispatch({ type: "closed", open: entry.open })
      }}
    >
      <SheetContent
        ref={popup}
        // The sheet itself takes focus, never its first control: a held Enter cannot pull a ceremony.
        initialFocus={popup}
        className="bg-spine-stock text-spine-ink transition-[scale,filter] motion-reduce:animate-none md:w-[440px] data-nested-dialog-open:scale-[0.985] data-nested-dialog-open:brightness-[0.97]"
      >
        <InSheetContext value>{content}</InSheetContext>
        {above ? <Layer key={layerKey(above)} {...props} depth={depth + 1} /> : null}
      </SheetContent>
    </Sheet>
  )
}
