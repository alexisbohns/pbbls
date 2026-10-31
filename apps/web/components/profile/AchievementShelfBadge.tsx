"use client"

import { Collection, Heart, PebbleDottedGlyph, PebbleGlowMd, People, Stack } from "@pbbls/icons"
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
import { useAchievementCopy } from "@/lib/i18n"
import type { AchievementDisplay, AchievementFamily } from "@/lib/types"

// Same family mapping as the grid's badge; unknown families degrade to the
// glow so a server ahead of this build can't break the shelf.
const FAMILY_ICONS: Record<AchievementFamily, PbblsIconComponent> = {
  pebble_count: Stack,
  emotion_first: Heart,
  domain_first: PebbleGlowMd,
  first_collection: Collection,
  first_glyph: PebbleDottedGlyph,
  first_soul: People,
  glyph_count: PebbleDottedGlyph,
  glyph_sales: PebbleGlowMd,
}

/**
 * One earned badge on the profile shelf: icon only, with the localized title
 * carried as the accessible name (and the native tooltip) since the shelf has
 * no room for labels.
 */
export function AchievementShelfBadge({ achievement }: { achievement: AchievementDisplay }) {
  const resolveCopy = useAchievementCopy()
  const Icon = FAMILY_ICONS[achievement.family] ?? PebbleGlowMd
  const { title } = resolveCopy(achievement)

  return (
    <li
      title={title}
      className="grid size-9 place-items-center rounded-full bg-primary/10 text-primary"
    >
      <Icon className={PBBLS_ICON_SIZE.inline} />
      <span className="sr-only">{title}</span>
    </li>
  )
}
