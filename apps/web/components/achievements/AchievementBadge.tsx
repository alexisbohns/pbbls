"use client"

import { useTranslations } from "next-intl"
import { Lock } from "lucide-react"
import { Collection, Heart, PebbleDottedGlyph, PebbleGlowMd, People, Stack } from "@pbbls/icons"
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { useFormatDate } from "@/lib/i18n"
import type { Achievement, AchievementFamily } from "@/lib/types"
import type { AchievementCopy } from "@/lib/i18n"

// Seeded rows carry no glyph_id today, so every badge renders its family's
// fallback icon (D12). Glyph rendering for admin-assigned visuals ships with
// the rewarding-moment satellite — deliberately not built here.
// `domain_first` and `glyph_sales` take the family default until the chrome
// wave draws life domain and market (iOS `familySymbol`).
export const FAMILY_ICONS: Record<AchievementFamily, PbblsIconComponent> = {
  pebble_count: Stack,
  emotion_first: Heart,
  domain_first: PebbleGlowMd,
  first_collection: Collection,
  first_glyph: PebbleDottedGlyph,
  first_soul: People,
  glyph_count: PebbleDottedGlyph,
  glyph_sales: PebbleGlowMd,
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
  const Icon = FAMILY_ICONS[achievement.family] ?? PebbleGlowMd
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
        <Icon className={PBBLS_ICON_SIZE.tile} />
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
