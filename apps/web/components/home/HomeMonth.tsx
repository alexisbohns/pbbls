"use client"

import { useAchievementPalettes } from "@/lib/data/useAchievementPalettes"
import { useHomeMonth } from "@/lib/data/useHomeMonth"
import { bareMonth } from "@/lib/home/month-labels"
import { currentMonthStart, readerTimeZone } from "@/lib/home/timezone"
import { useLocale } from "@/lib/i18n"
import { HomeMonthGrid } from "./HomeMonthGrid"

/**
 * Home's month (#1384, spec D10, D23): the served month once read, and until
 * then (or on a failed read) the bare calendar of the current month, so the
 * grid never jumps. Seals are inked from the achievements' palette lookups.
 */
export function HomeMonth() {
  const { locale } = useLocale()
  const { data, error, retry } = useHomeMonth()
  const palettes = useAchievementPalettes()
  const month = data ?? bareMonth(currentMonthStart(readerTimeZone()))
  return <HomeMonthGrid month={month} locale={locale} paletteFor={palettes.emotion} error={error !== null} onRetry={retry} />
}
