"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { ChevronRight, Trophy } from "lucide-react"
import { SectionCard } from "@/components/profile/SectionCard"
import { AchievementShelfBadge } from "@/components/profile/AchievementShelfBadge"
import { useAchievements } from "@/lib/data/useAchievements"

/** How many recent badges the shelf shows before "view all" takes over. */
const SHELF_SIZE = 6

/**
 * The profile achievements shelf (D14): the most recently unlocked badges, the
 * total count, and a way into the full grid. It reads the same two queries the
 * grid does — no new endpoint, no new RLS — and M50's public-profile projection
 * reuses exactly this shape.
 *
 * The shelf shows what you have EARNED; the grid shows the whole ladder. So
 * locked badges never appear here, and a profile with nothing unlocked yet
 * falls back to a plain invitation rather than an empty row.
 */
export function AchievementsShelf() {
  const t = useTranslations("profile.achievements")
  const { achievements, unlockedAt, loading } = useAchievements()

  const unlocked = achievements
    .filter((a) => unlockedAt.has(a.id))
    .map((a) => ({ achievement: a, at: unlockedAt.get(a.id) as string }))
    .sort((a, b) => b.at.localeCompare(a.at))

  const recent = unlocked.slice(0, SHELF_SIZE)

  return (
    <Link href="/achievements" className="block transition-opacity hover:opacity-90">
      <SectionCard>
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <Trophy className="size-[18px] shrink-0 text-primary" aria-hidden />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="text-base font-semibold text-foreground">{t("title")}</span>
              <span className="text-sm text-muted-foreground">
                {loading
                  ? t("loading")
                  : unlocked.length > 0
                    ? t("unlockedCount", { count: unlocked.length })
                    : t("subtitle")}
              </span>
            </div>
            <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          </div>

          {recent.length > 0 ? (
            <ul className="flex flex-wrap gap-2 pl-[30px]">
              {recent.map(({ achievement }) => (
                <AchievementShelfBadge key={achievement.id} achievement={achievement} />
              ))}
            </ul>
          ) : null}
        </div>
      </SectionCard>
    </Link>
  )
}
