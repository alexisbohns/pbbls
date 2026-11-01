import Link from "next/link"
import { useTranslations } from "next-intl"
import { PebbleGlowMd } from "@pbbls/icons"
import { Card } from "@/components/ui/card"
import { FAMILY_ICONS } from "@/components/achievements/AchievementBadge"
import type { AchievementFamily } from "@/lib/types"

export type YouBadge = { slug: string; family: AchievementFamily; title: string }

const FOCUS = "rounded-[18px] outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"

/**
 * You's Achievements (#1384 D18): the head with the served count, a link to
 * the page, then the served shelf as cards (four on a phone, six from `sm`),
 * each a link to the page too. Nothing earned keeps the head and its "0".
 */
export function YouAchievementsList({ badges, count }: { badges: readonly YouBadge[]; count: number | null }) {
  const t = useTranslations("you.achievements")
  return (
    <section>
      <Link href="/achievements" className={`mb-[18px] flex items-baseline justify-between gap-3 px-1 ${FOCUS}`}>
        <h2 className="pb-type-section text-m3-on-surface">{t("title")}</h2>
        {count !== null && (
          <span className="pb-type-figure text-m3-primary">
            {t.rich("earned", {
              count,
              word: (chunks) => <small className="pb-type-figure-small text-m3-on-surface-variant">{chunks}</small>,
            })}
          </span>
        )}
      </Link>
      {badges.length > 0 && (
        <ul className="grid grid-cols-4 gap-2.5 sm:grid-cols-6 max-sm:[&>li:nth-child(n+5)]:hidden">
          {badges.slice(0, 6).map((badge) => {
            const Icon = FAMILY_ICONS[badge.family] ?? PebbleGlowMd
            return (
              <li key={badge.slug}>
                <Link href="/achievements" className={`block h-full ${FOCUS}`}>
                  <Card className="pb-card h-full items-center gap-2 px-1 pt-4 pb-3">
                    <Icon className="size-[1.875rem] shrink-0 text-m3-primary" />
                    <span className="pb-type-caption line-clamp-2 text-center break-words text-m3-on-surface-variant">{badge.title}</span>
                  </Card>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
