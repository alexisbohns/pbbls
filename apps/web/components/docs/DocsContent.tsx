"use client"

import { useTranslations } from "next-intl"
import { DocsHeader } from "@/components/docs/DocsHeader"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import type { DocsCategory, DocsLocale, DocsPageContent } from "@/lib/docs/types"

type DocsContentProps = {
  content: Record<DocsLocale, DocsPageContent>
  category: DocsCategory
}

/** "2026-11-07" → "7 November 2026" / "7 novembre 2026". */
function longDate(iso: string, locale: DocsLocale): string {
  const date = new Date(`${iso}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat(locale === "fr" ? "fr-FR" : "en-GB", { dateStyle: "long", timeZone: "UTC" }).format(date)
}

export function DocsContent({ content, category }: DocsContentProps) {
  const { locale } = useDocsLocale()
  const t = useTranslations("docs")
  const page = content[locale]
  const { version, last_updated: updated } = page.frontmatter

  const kicker = [t(`categories.${category}`), version && t("version", { version })].filter(Boolean).join(" · ")

  return (
    <article lang={locale}>
      <DocsHeader
        kicker={kicker}
        title={page.title}
        lead={updated ? t("updated", { date: longDate(updated, locale) }) : undefined}
      />
      <div
        className="prose text-m3-on-surface [&_a]:text-m3-primary [&_a]:underline [&_a]:underline-offset-2 [&_h2]:mt-4! [&_h2]:scroll-mt-24 [&_h2]:font-spine! [&_h2]:text-[1.75rem]! [&_h2]:leading-tight [&_h2]:font-normal! [&_h2]:tracking-[-.012em] [&_h2]:text-m3-on-surface lg:[&_h2]:scroll-mt-8 [&_hr]:pb-knot-rule [&_hr]:my-4!"
        dangerouslySetInnerHTML={{ __html: page.html }}
      />
    </article>
  )
}
