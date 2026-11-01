"use client"

import { useCallback, useEffect, useRef } from "react"
import { usePathname, useRouter } from "next/navigation"
import { toast } from "sonner"
import type { RowOpens } from "@/lib/achievements/sheet-rows"
import { isShown, sheetHref, sheetId, type SheetKey } from "@/lib/achievements/sheets"
import { useAchievementPalettes } from "@/lib/data/useAchievementPalettes"
import { useAchievementsPage } from "@/lib/data/useAchievementsPage"
import { usePreviousPathname } from "@/lib/hooks/usePreviousPathname"
import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"
import { useSheetNames } from "@/lib/i18n/useSheetNames"
import type { CeremonyStack } from "@/lib/types"
import { AchievementSheetView } from "./AchievementSheetView"

/**
 * An achievements sheet as a detail (#1308 §4, #1384 two-area spec D5): a
 * nested detail under Achievements, in the right area from 1024 px and the
 * overlay below, with a back arrow to where it was opened from (Achievements,
 * or the sheet whose row opened it). Its card is marked seen once per visit;
 * a ready mark pulls its ceremony; a row opens the next sheet's URL, or its
 * pebble, which opens over the same tab and closes back here.
 */
export function AchievementSheetDetail({ sheet }: { sheet: SheetKey | null }) {
  const words = useAchievementsWords()
  const names = useSheetNames()
  const palettes = useAchievementPalettes()
  const { page, loading, pulling, pull, markSeen } = useAchievementsPage()
  const router = useRouter()
  const pathname = usePathname()
  const previous = usePreviousPathname()

  const back = useCallback(() => {
    // Opened from Achievements or another sheet in this page load: pop it. A hard load goes up to Achievements.
    if (previous?.startsWith("/achievements")) router.back()
    else router.replace("/achievements")
  }, [previous, router])

  const shown = sheet !== null && isShown(sheet, page)

  // Once per visit of this sheet (a later read re-rendering it is the same visit).
  const marked = useRef<string | null>(null)
  useEffect(() => {
    if (!shown || !sheet) return
    const id = `${pathname}:${sheetId(sheet)}`
    if (marked.current === id) return
    marked.current = id
    markSeen(sheet.card)
  }, [shown, sheet, pathname, markSeen])

  const onPull = useCallback(
    (stack: CeremonyStack) => {
      void pull(stack, { title: words.ceremonyTitle(stack), description: words.ceremonyBody(stack) }).then((outcome) => {
        if (outcome.kind === "failed") toast.error(words.ceremonyError())
      })
    },
    [pull, words],
  )
  const open = useCallback(
    (opens: RowOpens) => router.push(opens.kind === "moment" ? `/pebble/${opens.momentId}` : sheetHref(opens.sheet)),
    [router],
  )

  return (
    <AchievementSheetView
      sheet={sheet}
      page={page}
      loading={loading}
      palettes={palettes}
      names={names}
      pulling={pulling}
      onPull={onPull}
      onOpen={open}
      onBack={back}
    />
  )
}
