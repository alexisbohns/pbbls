"use client"

import { useTranslations } from "next-intl"
import { Users, Fingerprint, UserRoundPlus } from "lucide-react"
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
      <ShortcutTile href="/souls" icon={Users} label={t("souls")} />
      <ShortcutTile href="/glyphs" icon={Fingerprint} label={t("glyphs")} />
      <ShortcutTile href="/connections" icon={UserRoundPlus} label={t("connections")} />
    </div>
  )
}
