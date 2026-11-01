"use client"

import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { ReadyMark } from "@/components/achievements/page/ReadyMark"
import type { AchievementPalettes } from "@/lib/achievements/palette"
import type { RowOpens, SheetNames } from "@/lib/achievements/sheet-rows"
import { isShown, type SheetKey } from "@/lib/achievements/sheets"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import type { AchievementCompletion, AchievementsPage, CeremonyStack } from "@/lib/types"
import { DomainsSheet, EmotionsSheet, ExplorerSheet, PebblesSheet, YearSheet } from "./AchievementSheets"

type AchievementSheetViewProps = {
  /** Null: the URL names no sheet. */
  sheet: SheetKey | null
  page: AchievementsPage | null
  loading: boolean
  palettes: AchievementPalettes
  names: SheetNames
  pulling: CeremonyStack | null
  onPull: (ceremony: CeremonyStack) => void
  /** A row opens the next sheet or a pebble. */
  onOpen: (opens: RowOpens) => void
  onBack: () => void
}

/**
 * An achievements sheet's detail, drawn from props (#1384, two-area spec D5):
 * the top bar with its title and back arrow, then the sheet on the Spine's
 * faces over the M3 ground. A sheet whose section left the payload, or a URL
 * that names none, says it is not there.
 */
export function AchievementSheetView({ sheet, page, loading, palettes, names, pulling, onPull, onOpen, onBack }: AchievementSheetViewProps) {
  const t = useTranslations("achievements")
  const words = useAchievementsWords()
  const open = onOpen
  const ready = (completion: AchievementCompletion | null): ReactNode =>
    completion?.ready ? (
      <ReadyMark text={words.ready()} label={words.a11y.ready(completion.stack)} disabled={pulling !== null} onPull={() => onPull(completion.stack)} />
    ) : undefined
  const shown = sheet !== null && isShown(sheet, page)

  const title = sheet === null ? t("title") : sheet.card === "year" ? String(sheet.year) : words.cardTitle(sheet.card)

  let content = null
  if (page && sheet && shown) {
    if (sheet.card === "explorer" && page.explorer) {
      content = <ExplorerSheet explorer={page.explorer} emotions={page.emotions} domains={page.domains} palettes={palettes} open={open} ready={ready(page.explorer.completion)} />
    } else if (sheet.card === "emotions" && page.emotions) {
      content = <EmotionsSheet emotions={page.emotions} names={names} palettes={palettes} open={open} ready={ready(page.emotions.completion)} />
    } else if (sheet.card === "domains" && page.domains) {
      content = <DomainsSheet domains={page.domains} names={names} palettes={palettes} open={open} ready={ready(page.domains.completion)} />
    } else if (sheet.card === "pebbles" && page.pebbles) {
      content = <PebblesSheet pebbles={page.pebbles} carved={page.glyphsCarved} sales={page.glyphSales} palettes={palettes} open={open} />
    } else if (sheet.card === "year") {
      const year = page.years.find((y) => y.year === sheet.year)
      if (year) content = <YearSheet year={year} years={page.years} palettes={palettes} open={open} />
    }
  }

  return (
    <div className="min-h-full bg-spine-ground font-spine text-spine-ink">
      <DetailTopBar title={title} onBack={onBack} />
      <div className="mx-auto w-full max-w-2xl px-4 pt-2 pb-24">
        {content ??
          (loading && !page ? (
            <p aria-live="polite" className="text-sm text-spine-ink-2">
              {t("loading")}
            </p>
          ) : (
            <p className="text-sm text-spine-ink-2">{words.sheet.text("gone")}</p>
          ))}
      </div>
    </div>
  )
}
