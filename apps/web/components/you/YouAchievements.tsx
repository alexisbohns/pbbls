"use client"

import { useMemo } from "react"
import { shelfDisplays } from "@/lib/achievements/shelf"
import { useAchievementCatalog } from "@/lib/data/useAchievementCatalog"
import { useAchievementsShelf } from "@/lib/data/useAchievementsPage"
import { useAchievementCopy } from "@/lib/i18n"
import { YouAchievementsList, type YouBadge } from "./YouAchievementsList"

/** You's Achievements (#1384 D18): the served shelf (kept copy first), titled from the catalog. */
export function YouAchievements() {
  const { shelf, count } = useAchievementsShelf()
  const catalog = useAchievementCatalog()
  const copy = useAchievementCopy()
  const badges = useMemo(
    (): YouBadge[] =>
      shelf ? shelfDisplays(shelf, catalog).map((a) => ({ slug: a.slug, family: a.family, title: copy(a).title })) : [],
    [shelf, catalog, copy],
  )
  return <YouAchievementsList badges={badges} count={count} />
}
