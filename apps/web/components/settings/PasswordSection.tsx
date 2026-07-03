"use client"

import { useTranslations } from "next-intl"
import { Input } from "@/components/ui/input"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"

type PasswordSectionProps = {
  value: string
  onChange: (value: string) => void
}

/**
 * New-password field for email accounts — web port of the iOS SettingsSheet
 * Password section. Blank means "keep current password".
 */
export function PasswordSection({ value, onChange }: PasswordSectionProps) {
  const t = useTranslations("settings")

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-password">{t("password")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-password">
        <SettingsRow>
          <label htmlFor="settings-password-input" className="sr-only">
            {t("newPassword")}
          </label>
          <Input
            id="settings-password-input"
            type="password"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={t("newPassword")}
            autoComplete="new-password"
            className="h-auto border-0 bg-transparent p-0 shadow-none focus-visible:ring-0"
          />
        </SettingsRow>
      </SettingsGroup>
      <p className="px-1 text-xs text-muted-foreground">{t("passwordHint")}</p>
    </section>
  )
}
