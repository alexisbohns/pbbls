"use client"

import { Scale, ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"

const LEGAL_LINKS = [
  { key: "legalNotice" as const, href: "/docs/legal-notice" },
  { key: "terms" as const, href: "/docs/terms" },
  { key: "privacy" as const, href: "/docs/privacy" },
]

/**
 * Legal links (Legal Notice / Terms / Privacy) — web port of the iOS
 * SettingsSheet Legal section, rebuilt on the shared SettingsGroup/Row.
 */
export function LegalSection() {
  const t = useTranslations("legal")
  const tSettings = useTranslations("settings")

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-legal">{tSettings("legal")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-legal">
        {LEGAL_LINKS.map((link) => (
          <SettingsRow
            key={link.href}
            href={link.href}
            icon={Scale}
            trailing={<ChevronRight className="size-4 text-muted-foreground" aria-hidden />}
          >
            {t(link.key)}
          </SettingsRow>
        ))}
      </SettingsGroup>
    </section>
  )
}
