"use client"

import { useTranslations } from "next-intl"
import { UserRoundPlus } from "lucide-react"
import { PebbleDottedGlyph, People } from "@pbbls/icons"
import { ShortcutTile } from "@/components/profile/ShortcutTile"

/**
 * The Profile shortcuts (Souls / Glyphs / Connections) — web port of the iOS
 * `ProfileShortcutsRow`. Labels reuse the shared `nav.*` translations.
 * Collections have their own section card, so they get no shortcut tile.
 */
export function ShortcutsRow() {
  const t = useTranslations("nav")
  return (
    <div className="flex gap-2.5">
      <ShortcutTile href="/souls" icon={People} label={t("souls")} />
      <ShortcutTile href="/glyphs" icon={PebbleDottedGlyph} label={t("glyphs")} />
      <ShortcutTile href="/connections" icon={UserRoundPlus} label={t("connections")} />
    </div>
  )
}
