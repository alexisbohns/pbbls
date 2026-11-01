"use client"

import { Sun, Globe, Palette } from "lucide-react"
import { useTranslations } from "next-intl"
import { ListGroupHeader } from "@/components/m3/ListGroupHeader"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { ThemeToggle } from "@/components/layout/ThemeToggle"
import { LocaleSwitcher } from "@/components/settings/LocaleSwitcher"
import { ColorWorldSwitcher } from "@/components/settings/ColorWorldSwitcher"

/**
 * Web-only preferences: theme, language, and color world. iOS has no
 * equivalent (it follows the system), so this section only exists on web.
 */
export function AppearanceSection() {
  const t = useTranslations("settings")

  return (
    <section className="flex flex-col gap-2">
      <ListGroupHeader id="settings-appearance">{t("appearance")}</ListGroupHeader>
      <SettingsGroup aria-labelledby="settings-appearance">
        <SettingsRow icon={Sun} trailing={<ThemeToggle />}>
          {t("theme")}
        </SettingsRow>
        <SettingsRow icon={Globe} trailing={<LocaleSwitcher />}>
          {t("language")}
        </SettingsRow>
        <SettingsRow icon={Palette} trailing={<ColorWorldSwitcher />}>
          <span className="block">{t("colorWorld")}</span>
          <span className="m3-type-body-medium block text-m3-on-surface-variant">{t("colorWorldScope")}</span>
        </SettingsRow>
      </SettingsGroup>
    </section>
  )
}
