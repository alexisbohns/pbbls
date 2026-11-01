"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { ChevronRight } from "lucide-react"
import { PebbleGlowMd } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { SectionCard } from "@/components/profile/SectionCard"
import { AchievementShelfBadge } from "@/components/profile/AchievementShelfBadge"
import { shelfDisplays } from "@/lib/achievements/shelf"
import { useAchievementCatalog } from "@/lib/data/useAchievementCatalog"
import { useAchievementsShelf } from "@/lib/data/useAchievementsPage"

/**
 * The profile achievements shelf (D14, D18): the badges the server serves for
 * the shelf (`get_achievements`' `shelf`, newest first, the private families
 * removed), its total (`shelf_count`), and a way into the page. The kept copy
 * shows at once, and the read is skipped while it is under 15 minutes old.
 * Titles and icons resolve from the catalog by slug.
 *
 * The shelf shows what you have EARNED, so a profile with nothing yet falls
 * back to a plain invitation rather than an empty row.
 */
export function AchievementsShelf() {
  const t = useTranslations("profile.achievements")
  const { shelf, count, loading } = useAchievementsShelf()
  const catalog = useAchievementCatalog()
  const recent = shelf ? shelfDisplays(shelf, catalog) : []

  return (
    <Link href="/achievements" className="block transition-opacity hover:opacity-90">
      <SectionCard>
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <PebbleGlowMd className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-primary")} />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="text-base font-semibold text-foreground">{t("title")}</span>
              <span className="text-sm text-muted-foreground">
                {count === null && loading
                  ? t("loading")
                  : count !== null && count > 0
                    ? t("unlockedCount", { count })
                    : t("subtitle")}
              </span>
            </div>
            <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          </div>

          {recent.length > 0 ? (
            <ul className="flex flex-wrap gap-2 pl-[30px]">
              {recent.map((achievement) => (
                <AchievementShelfBadge key={achievement.id} achievement={achievement} />
              ))}
            </ul>
          ) : null}
        </div>
      </SectionCard>
    </Link>
  )
}
