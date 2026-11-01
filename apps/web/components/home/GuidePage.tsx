"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { GuideArticle } from "./GuideArticle"
import { useGuides } from "@/lib/data/useGuides"
import { renderGuideMarkdown } from "@/lib/home/guide-markdown"
import { useLocale } from "@/lib/i18n"

/**
 * One guide (#1384, spec D14): its title, its italic summary and its Markdown
 * body, from the same cached read as About Pebbles, as a detail beside Home
 * (two-area spec D2). An unknown slug, once the read has landed, is a
 * not-found card back to Home.
 */
export function GuidePage({ slug }: { slug: string }) {
  const t = useTranslations("home.guide")
  const { locale } = useLocale()
  const { data, loading } = useGuides(locale)
  const guide = data?.find((g) => g.slug === slug) ?? null
  const html = useMemo(() => (guide ? renderGuideMarkdown(guide.bodyMd) : null), [guide])

  if (!guide) {
    return (
      <>
        <DetailTopBar title={loading ? "" : t("missingTitle")} />
        {!loading && (
          <div className="mx-auto w-full max-w-2xl px-4 pb-12">
            <NotFoundCard title={t("missingTitle")} description={t("missingBody")} href="/home" linkText={t("missingLink")} />
          </div>
        )}
      </>
    )
  }

  return <GuideArticle guide={guide} html={html} />
}
