"use client"

import { useTranslations } from "next-intl"
import { Compass, Gem, Heart, Layers, Lock, PenTool, Shapes, Store, Trophy, Users } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { cn } from "@/lib/utils"
import { useFormatDate } from "@/lib/i18n"
import type { Achievement, AchievementFamily } from "@/lib/types"
import type { AchievementCopy } from "@/lib/i18n"

// Seeded rows carry no glyph_id today, so every badge renders its family's
// fallback icon (D12). Glyph rendering for admin-assigned visuals ships with
// the rewarding-moment satellite — deliberately not built here.
const FAMILY_ICONS: Record<AchievementFamily, LucideIcon> = {
  pebble_count: Gem,
  emotion_first: Heart,
  domain_first: Compass,
  first_collection: Layers,
  first_glyph: PenTool,
  first_soul: Users,
  glyph_count: Shapes,
  glyph_sales: Store,
}

type AchievementBadgeProps = {
  achievement: Achievement
  copy: AchievementCopy
  /** Present iff the badge is unlocked. */
  unlockedAt?: string
}

export function AchievementBadge({ achievement, copy, unlockedAt }: AchievementBadgeProps) {
  const t = useTranslations("achievements")
  const formatDate = useFormatDate()
  // The DB types family as string and the provider narrows it with a cast, so
  // a server ahead of this build can ship families we don't know yet. Degrade
  // to a generic icon instead of crashing the grid on an undefined component.
  const Icon = FAMILY_ICONS[achievement.family] ?? Trophy
  const locked = unlockedAt === undefined

  // Locked state is never color-only: the greyed treatment is paired with a
  // lock icon and a visible "Locked" caption that screen readers also get.
  return (
    <li
      className={cn(
        "flex flex-col items-center gap-2 rounded-2xl border border-border p-4 text-center",
        locked && "opacity-60",
      )}
    >
      <div
        className={cn(
          "flex size-12 shrink-0 items-center justify-center rounded-full",
          locked ? "bg-muted text-muted-foreground" : "bg-primary/10 text-primary",
        )}
      >
        <Icon aria-hidden className="size-6" />
      </div>
      <span className="text-sm font-semibold text-foreground">{copy.title}</span>
      <span className="text-xs text-muted-foreground">{copy.description}</span>
      {locked ? (
        <span className="mt-auto flex items-center gap-1 text-xs font-medium text-muted-foreground">
          <Lock aria-hidden className="size-3" />
          {t("locked")}
        </span>
      ) : (
        <span className="mt-auto text-xs font-medium text-primary">
          {t("unlockedOn", { date: formatDate(unlockedAt, { dateStyle: "medium" }) })}
        </span>
      )}
    </li>
  )
}
