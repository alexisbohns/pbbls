"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import { useFormatDate } from "@/lib/i18n"
import type { Pebble } from "@/lib/types"
import { groupPebblesByDate, type DateGroup } from "@/lib/utils/group-pebbles-by-date"

/**
 * Group a list of pebbles by local-date with locale-aware day labels and
 * Today/Yesterday detection. The label format mirrors the original UX
 * ("Today — Monday, April 5, 2026") with each piece localized.
 */
export function useGroupedPebbles(pebbles: Pebble[]): DateGroup[] {
  const t = useTranslations("path")
  const formatDate = useFormatDate()

  return useMemo(
    () =>
      groupPebblesByDate(pebbles, {
        formatLongDate: (d) =>
          formatDate(d, {
            weekday: "long",
            year: "numeric",
            month: "long",
            day: "numeric",
          }),
        formatTodayLabel: (date) => t("todayLabel", { date }),
        formatYesterdayLabel: (date) => t("yesterdayLabel", { date }),
      }),
    [pebbles, formatDate, t],
  )
}
