"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import { achievementsWords, type AchievementsWords, type Translate } from "@/lib/achievements/copy"
import { useLocale } from "./useLocale"

/** The achievements page's words (#1308) in the reader's language: `achievementsWords` over `achievements.page`. */
export function useAchievementsWords(): AchievementsWords {
  // Untyped access: the words pick their keys at runtime (a tier, a need, a stack).
  const t = useTranslations("achievements.page") as unknown as Translate
  const root = useTranslations() as unknown as Translate
  const { locale } = useLocale()
  return useMemo(() => achievementsWords(t, locale, root), [t, locale, root])
}
