"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import type { Pebble } from "@/lib/types"
import {
  groupPebblesByISOWeek,
  type WeekGroup,
} from "@/lib/utils/group-pebbles-by-iso-week"

/**
 * Group pebbles by ISO 8601 week with a localized "Week N" / "Semaine N"
 * label. Mirrors the iOS Path screen, which groups by ISO week and
 * renders each group under its own card.
 */
export function useGroupedPebblesByWeek(pebbles: Pebble[]): WeekGroup[] {
  const t = useTranslations("path")

  return useMemo(
    () =>
      groupPebblesByISOWeek(pebbles, (weekNumber) =>
        t("weekLabel", { week: weekNumber }),
      ),
    [pebbles, t],
  )
}
