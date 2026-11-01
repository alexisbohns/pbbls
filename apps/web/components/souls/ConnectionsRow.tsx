"use client"

import { ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"
import { People } from "@pbbls/icons"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"

/**
 * People's way to Connections (#1384 D2): You's shortcuts row was the web's
 * only link there, and iOS reaches Connections from its souls list too.
 */
export function ConnectionsRow() {
  const t = useTranslations("souls.connectionsRow")
  return (
    <SettingsGroup className="mb-6">
      <SettingsRow icon={People} href="/connections" trailing={<ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />}>
        <span className="block">{t("title")}</span>
        <span className="block text-xs font-normal text-muted-foreground">{t("line")}</span>
      </SettingsRow>
    </SettingsGroup>
  )
}
