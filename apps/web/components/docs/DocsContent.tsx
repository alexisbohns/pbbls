"use client"

import { useTranslations } from "next-intl"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import type { DocsLocale, DocsPageContent } from "@/lib/docs/types"

type DocsContentProps = {
  content: Record<DocsLocale, DocsPageContent>
}

export function DocsContent({ content }: DocsContentProps) {
  const { locale } = useDocsLocale()
  const t = useTranslations("docs")
  const page = content[locale]

  return (
    <article lang={locale}>
      {page.frontmatter.last_updated && (
        <p className="mb-4 text-xs text-muted-foreground">
          {t("lastUpdated")}:{" "}
          <time dateTime={page.frontmatter.last_updated}>{page.frontmatter.last_updated}</time>
          {page.frontmatter.version && ` — v${page.frontmatter.version}`}
        </p>
      )}
      <div
        className="prose prose-sm dark:prose-invert max-w-none"
        dangerouslySetInnerHTML={{ __html: page.html }}
      />
    </article>
  )
}
