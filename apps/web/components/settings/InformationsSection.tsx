"use client"

import { useTranslations } from "next-intl"
import { Input } from "@/components/ui/input"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"

type InformationsSectionProps = {
  name: string
  onNameChange: (value: string) => void
  email: string
}

/**
 * Editable display name + read-only email — web port of the iOS SettingsSheet
 * Informations section.
 */
export function InformationsSection({ name, onNameChange, email }: InformationsSectionProps) {
  const t = useTranslations("settings")

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-informations">{t("informations")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-informations">
        <SettingsRow>
          <label htmlFor="settings-name" className="sr-only">
            {t("name")}
          </label>
          <Input
            id="settings-name"
            value={name}
            onChange={(e) => onNameChange(e.target.value)}
            placeholder={t("namePlaceholder")}
            className="h-auto border-0 bg-transparent p-0 shadow-none focus-visible:ring-0"
          />
        </SettingsRow>
        <SettingsRow>
          <span className="text-muted-foreground">{email}</span>
        </SettingsRow>
      </SettingsGroup>
    </section>
  )
}
