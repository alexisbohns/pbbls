"use client"

import { useTranslations } from "next-intl"
import { useAchievements } from "@/lib/data/useAchievements"
import { useAchievementCopy } from "@/lib/i18n"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"
import { AchievementBadge } from "@/components/achievements/AchievementBadge"
import type { Achievement, AchievementFamily } from "@/lib/types"
import type { AchievementCopy } from "@/lib/i18n"

type BadgeEntry = { achievement: Achievement; copy: AchievementCopy }
type FamilyGroup = { family: AchievementFamily; entries: BadgeEntry[] }

export function AchievementsView() {
  const t = useTranslations("achievements")
  const { achievements, unlockedAt, loading, error } = useAchievements()
  const resolveCopy = useAchievementCopy()

  // Family sections follow the catalog's per-family sort_order blocks; inside
  // a family, generated rows (emotion_first / domain_first) share one
  // sort_order and are ordered by localized title instead (D2). Inactive
  // badges are retired: hidden while locked, still shown once earned (D12).
  const groups: FamilyGroup[] = []
  const byFamily = new Map<AchievementFamily, FamilyGroup>()
  const visible = achievements
    .filter((a) => a.isActive || unlockedAt.has(a.id))
    .map((a): BadgeEntry => ({ achievement: a, copy: resolveCopy(a) }))
    .sort(
      (a, b) =>
        a.achievement.sortOrder - b.achievement.sortOrder ||
        a.copy.title.localeCompare(b.copy.title),
    )
  for (const entry of visible) {
    const family = entry.achievement.family
    let group = byFamily.get(family)
    if (!group) {
      group = { family, entries: [] }
      byFamily.set(family, group)
      groups.push(group)
    }
    group.entries.push(entry)
  }

  return (
    <PageLayout>
      <div className="flex flex-col gap-6">
        <PageHeader title={t("title")} backHref="/profile" />
        {loading && (
          <p aria-live="polite" className="text-sm text-muted-foreground">
            {t("loading")}
          </p>
        )}
        {!loading && error && (
          <p role="alert" className="text-sm text-destructive">
            {t("error")}
          </p>
        )}
        {!loading &&
          !error &&
          groups.map((group) => (
            <section key={group.family}>
              <h2 className="mb-2 text-sm font-semibold text-muted-foreground">
                {/* Unknown families (server ahead of this build) fall back to
                    the raw slug instead of rendering a bare message-key path. */}
                {t.has(`group.${group.family}`) ? t(`group.${group.family}`) : group.family}
              </h2>
              <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {group.entries.map(({ achievement, copy }) => (
                  <AchievementBadge
                    key={achievement.id}
                    achievement={achievement}
                    copy={copy}
                    unlockedAt={unlockedAt.get(achievement.id)}
                  />
                ))}
              </ul>
            </section>
          ))}
      </div>
    </PageLayout>
  )
}
