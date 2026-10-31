"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"
import { PageHeader } from "@/components/layout/PageHeader"
import { useGuides } from "@/lib/data/useGuides"
import { renderGuideMarkdown } from "@/lib/home/guide-markdown"
import { useLocale } from "@/lib/i18n"

/**
 * One guide (#1384, spec D14): its title, its italic summary and its Markdown
 * body, from the same cached read as About Pebbles. An unknown slug, once the
 * read has landed, is a not-found card back to Home.
 */
export function GuidePage({ slug }: { slug: string }) {
  const t = useTranslations("home.guide")
  const { locale } = useLocale()
  const { data, loading } = useGuides(locale)
  const guide = data?.find((g) => g.slug === slug) ?? null
  const html = useMemo(() => (guide ? renderGuideMarkdown(guide.bodyMd) : null), [guide])

  if (!guide) {
    if (loading) return null
    return <NotFoundCard title={t("missingTitle")} description={t("missingBody")} href="/home" linkText={t("missingLink")} />
  }

  return (
    <article className="mx-auto flex max-w-md flex-col px-4 pt-[1.375rem] pb-12">
      <PageHeader title={guide.title} backHref="/home" />
      <p className="pb-type-lead -mt-2 mb-6 text-m3-on-surface-variant">{guide.summary}</p>
      {html && <div className="prose prose-sm max-w-none text-m3-on-surface dark:prose-invert" dangerouslySetInnerHTML={{ __html: html }} />}
    </article>
  )
}
