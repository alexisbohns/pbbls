"use client"

import { Trophy } from "lucide-react"
import { useTranslations } from "next-intl"
import { SectionCard } from "@/components/profile/SectionCard"
import { AchievementShelfBadge } from "@/components/profile/AchievementShelfBadge"
import type { AchievementDisplay, PublicAchievement } from "@/lib/types"

/**
 * The visitor's view of the owner's shelf (#688) — the public twin of
 * `AchievementsShelf`, reusing its badge so the two stay visually in step.
 *
 * Three deliberate differences, all inherited from the projection rather than
 * chosen here: there is no "view all" affordance (the grid is the owner's
 * screen, and the RPC does not project the ladder), the slice is already
 * capped and ordered server-side so nothing is sorted client-side, and the
 * count comes from `achievements_count` because the array is only its first
 * six rows. Renders nothing at all when the shelf is empty — a visitor has no
 * reason to see an invitation meant for the owner.
 */
export function PublicAchievementsShelf({
  achievements,
  total,
}: {
  achievements: PublicAchievement[]
  total: number
}) {
  const t = useTranslations("publicProfile")

  if (achievements.length === 0) return null

  return (
    <SectionCard>
      <div className="flex items-center gap-3">
        <Trophy className="size-[18px] shrink-0 text-primary" aria-hidden />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="text-base font-semibold text-foreground">{t("achievements")}</span>
          <span className="text-sm text-muted-foreground">
            {t("achievementsCount", { count: total })}
          </span>
        </div>
      </div>

      <ul className="flex flex-wrap gap-2 pl-[30px]">
        {achievements.map((badge) => (
          <AchievementShelfBadge key={badge.id} achievement={toDisplay(badge)} />
        ))}
      </ul>
    </SectionCard>
  )
}

/**
 * The projection is snake_case jsonb; the badge and its copy resolver speak the
 * camelCase domain shape. Nothing is derived here — the two are the same fields
 * under different names, which is what keeps the public shelf honest about
 * being the same badge the owner sees.
 */
function toDisplay(badge: PublicAchievement): AchievementDisplay {
  return {
    id: badge.id,
    slug: badge.slug,
    family: badge.family,
    threshold: badge.threshold,
    emotionId: badge.emotion_id,
    domainId: badge.domain_id,
    titleEn: badge.title_en,
    titleFr: badge.title_fr,
    descriptionEn: badge.description_en,
    descriptionFr: badge.description_fr,
  }
}
