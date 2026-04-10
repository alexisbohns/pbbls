"use client"

import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import type { DocsLocale, DocsPageContent } from "@/lib/docs/types"

type DocsContentProps = {
  content: Record<DocsLocale, DocsPageContent>
}

export function DocsContent({ content }: DocsContentProps) {
  const { locale } = useDocsLocale()
  const page = content[locale]

  return (
    <article lang={locale}>
      {page.frontmatter.last_updated && (
        <p className="mb-4 text-xs text-muted-foreground">
          {locale === "fr" ? "Dernière mise à jour" : "Last updated"}:{" "}
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
