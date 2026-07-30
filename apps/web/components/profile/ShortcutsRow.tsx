"use client"

import { useTranslations } from "next-intl"
import { FolderOpen, Users, Fingerprint, UserRoundPlus } from "lucide-react"
import { ShortcutTile } from "@/components/profile/ShortcutTile"

/**
 * The Profile shortcuts (Collections / Souls / Glyphs / Connections) — web
 * port of the iOS `ProfileShortcutsRow`. Labels reuse the shared `nav.*`
 * translations.
 */
export function ShortcutsRow() {
  const t = useTranslations("nav")
  return (
    <div className="flex gap-2.5">
      <ShortcutTile href="/collections" icon={FolderOpen} label={t("collections")} />
      <ShortcutTile href="/souls" icon={Users} label={t("souls")} />
      <ShortcutTile href="/glyphs" icon={Fingerprint} label={t("glyphs")} />
      <ShortcutTile href="/connections" icon={UserRoundPlus} label={t("connections")} />
    </div>
  )
}
