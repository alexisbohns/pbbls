"use client"

import { useMemo } from "react"
import { unified } from "unified"
import remarkParse from "remark-parse"
import remarkRehype from "remark-rehype"
import rehypeStringify from "rehype-stringify"
import { useTranslations } from "next-intl"
import { useLocale } from "@/lib/i18n"
import { useAnnouncement } from "@/lib/data/useLab"
import { logBody, logSummary, logTitle } from "@/lib/utils/log-localized"
import { labAssetUrl } from "@/lib/utils/lab-asset-url"

const markdownProcessor = unified()
  .use(remarkParse)
  .use(remarkRehype)
  .use(rehypeStringify)

function renderMarkdown(md: string): string {
  return String(markdownProcessor.processSync(md))
}

type AnnouncementDetailProps = {
  id: string
}

// Full-screen detail for a single announcement: cover image, title,
// summary, then the localized Markdown body rendered with the same
// `unified` pipeline used by the docs feature.
// Mirrors apps/ios/Pebbles/Features/Lab/Views/AnnouncementDetailView.swift.
export function AnnouncementDetail({ id }: AnnouncementDetailProps) {
  const { log, loading, error } = useAnnouncement(id)
  const t = useTranslations("lab")
  const { locale } = useLocale()

  const html = useMemo(() => {
    if (!log) return null
    const body = logBody(log, locale)
    if (!body) return null
    return renderMarkdown(body)
  }, [log, locale])

  if (loading) {
    return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  }

  if (error || !log) {
    return (
      <p className="text-sm text-muted-foreground">{t("announcementError")}</p>
    )
  }

  const coverUrl = labAssetUrl(log.cover_image_path)

  return (
    <article className="flex flex-col gap-4">
      {coverUrl && (
        // eslint-disable-next-line @next/next/no-img-element -- public Supabase storage URL, next/image domain config not wired
        <img
          src={coverUrl}
          alt=""
          className="aspect-[16/9] w-full rounded-xl bg-muted object-cover"
        />
      )}

      <header className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold leading-tight">
          {logTitle(log, locale)}
        </h1>
        <p className="text-base text-muted-foreground">
          {logSummary(log, locale)}
        </p>
      </header>

      {html && (
        <div
          className="prose prose-sm dark:prose-invert max-w-none"
          dangerouslySetInnerHTML={{ __html: html }}
        />
      )}
    </article>
  )
}
