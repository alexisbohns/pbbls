"use client"

import type { ReactNode } from "react"
import { Info, LifeBuoy, Scale } from "lucide-react"
import { useTranslations } from "next-intl"
import { ListGroupHeader } from "@/components/m3/ListGroupHeader"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { useSettingsLinks } from "@/lib/data/useSettingsLinks"
import { useLocale } from "@/lib/i18n"
import type { SettingsLinkSection } from "@/lib/settings/settings-links"

const SECTION_ICONS = { legal: Scale, about: Info, support: LifeBuoy } as const

/**
 * Settings' links (Legal / About / Support), edited from the admin (#1489).
 * Kept under its original name so its two call sites stay unchanged.
 */
export function LegalSection() {
  const t = useTranslations("settings.linkSections")
  const { locale } = useLocale()
  const groups = useSettingsLinks(locale)

  return (
    <>
      {groups.map((group) => (
        <LinkGroup key={group.section} section={group.section} title={t(group.section)}>
          {group.links.map((link) => (
            <SettingsRow
              key={link.key}
              href={link.url}
              external={link.external || link.url.startsWith("mailto:")}
              icon={SECTION_ICONS[group.section]}
            >
              {link.title}
            </SettingsRow>
          ))}
        </LinkGroup>
      ))}
    </>
  )
}

function LinkGroup({
  section,
  title,
  children,
}: {
  section: SettingsLinkSection
  title: string
  children: ReactNode
}) {
  const id = `settings-links-${section}`
  return (
    <section className="flex flex-col gap-2">
      <ListGroupHeader id={id}>{title}</ListGroupHeader>
      <SettingsGroup aria-labelledby={id}>{children}</SettingsGroup>
    </section>
  )
}
