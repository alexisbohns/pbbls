"use client"

import { useTranslations } from "next-intl"
import type { RippleSummary } from "@/lib/types"
import { RippleBadge } from "@/components/profile/ripples/RippleBadge"
import { AssiduityGrid } from "@/components/profile/AssiduityGrid"

// Min pebbles (trailing 28 days) to reach level index+1, mirroring the v_ripple
// bucket thresholds: L1=1, L2=5, L3=9, L4=13, L5=17, L6=21.
const NEXT_LEVEL_MIN = [1, 5, 9, 13, 17, 21]

type RipplesRowProps = {
  ripple: RippleSummary
  assiduity: boolean[]
}

/**
 * Ripple badge + level/progress copy + 28-day assiduity grid. Web port of the
 * iOS `RipplesRow`.
 */
export function RipplesRow({ ripple, assiduity }: RipplesRowProps) {
  const t = useTranslations("profile.stats")
  const level = Math.min(Math.max(ripple.level, 0), 6)
  const isMax = level >= 6
  const remaining = isMax ? 0 : Math.max(NEXT_LEVEL_MIN[level] - ripple.pebbles28d, 0)

  return (
    <div className="flex items-center gap-4">
      <RippleBadge level={ripple.level} activeToday={ripple.activeToday} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-base font-semibold text-foreground">
          {t("ripplesLevel", { level })}
        </span>
        <span className="text-sm text-muted-foreground">
          {isMax
            ? t("ripplesMax")
            : t("ripplesProgress", { count: remaining, next: level + 1 })}
        </span>
      </div>
      <AssiduityGrid data={assiduity} />
    </div>
  )
}
