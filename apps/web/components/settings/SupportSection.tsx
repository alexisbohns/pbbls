"use client"

import { LifeBuoy } from "lucide-react"
import { useTranslations } from "next-intl"
import { ListGroupHeader } from "@/components/m3/ListGroupHeader"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"

/** Settings' "Get support" row: the Crisis resources page (#1459). */
export function SupportSection() {
  const t = useTranslations("settings.support")
  return (
    <section className="flex flex-col gap-2">
      <ListGroupHeader id="settings-support">{t("title")}</ListGroupHeader>
      <SettingsGroup aria-labelledby="settings-support">
        <SettingsRow icon={LifeBuoy} href="/settings/support">
          {t("title")}
        </SettingsRow>
      </SettingsGroup>
    </section>
  )
}
