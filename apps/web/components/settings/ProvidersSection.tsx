"use client"

import { useTranslations } from "next-intl"
import { Link2 } from "lucide-react"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"

type ProvidersSectionProps = {
  /** All linked providers, e.g. ["apple", "google", "email"]. */
  providers: string[]
}

/**
 * Lists linked SSO identities (Apple, Google) — web port of the iOS
 * SettingsSheet Providers section. Renders nothing when the account has no SSO
 * provider (email-only accounts show the Password section instead).
 */
export function ProvidersSection({ providers }: ProvidersSectionProps) {
  const t = useTranslations("settings")
  const sso = providers.filter((p) => p !== "email")

  if (sso.length === 0) return null

  const labelFor = (provider: string) => {
    if (provider === "apple") return t("providerApple")
    if (provider === "google") return t("providerGoogle")
    return provider
  }

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-providers">{t("providers")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-providers">
        {sso.map((provider) => (
          <SettingsRow key={provider} icon={Link2}>
            {labelFor(provider)}
          </SettingsRow>
        ))}
      </SettingsGroup>
    </section>
  )
}
