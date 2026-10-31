"use client"

import { useMemo } from "react"
import { palettesFromReference, type AchievementPalettes } from "@/lib/achievements/palette"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { useEmotionsWithPalette } from "@/lib/data/useEmotionsWithPalette"

/** The achievements figures' colour lookups (#1308), from the reference reads the Path already caches. */
export function useAchievementPalettes(): AchievementPalettes {
  const { paletteByEmotionId } = useEmotionPalettes()
  const { rows } = useEmotionsWithPalette()
  return useMemo(() => palettesFromReference(rows, paletteByEmotionId), [rows, paletteByEmotionId])
}
