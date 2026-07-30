"use client"

import { Compass, Gem, Heart, Layers, PenTool, Shapes, Store, Trophy, Users } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { useAchievementCopy } from "@/lib/i18n"
import type { Achievement, AchievementFamily } from "@/lib/types"

// Same family mapping as the grid's badge; unknown families degrade to the
// trophy so a server ahead of this build can't break the shelf.
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

/**
 * One earned badge on the profile shelf: icon only, with the localized title
 * carried as the accessible name (and the native tooltip) since the shelf has
 * no room for labels.
 */
export function AchievementShelfBadge({ achievement }: { achievement: Achievement }) {
  const resolveCopy = useAchievementCopy()
  const Icon = FAMILY_ICONS[achievement.family] ?? Trophy
  const { title } = resolveCopy(achievement)

  return (
    <li
      title={title}
      className="grid size-9 place-items-center rounded-full bg-primary/10 text-primary"
    >
      <Icon className="size-4" aria-hidden />
      <span className="sr-only">{title}</span>
    </li>
  )
}
