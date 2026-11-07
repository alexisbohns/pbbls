"use client"

import { useSearchParams } from "next/navigation"
import { DocsShell } from "@/components/docs/DocsShell"
import { DocsContent } from "@/components/docs/DocsContent"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import type { DocsLocale, DocsPageContent, DocsPageMeta } from "@/lib/docs/types"

type DocsEmbedProps = {
  content: Record<DocsLocale, DocsPageContent>
  page: DocsPageMeta
  pages: DocsPageMeta[]
}

export function DocsEmbed({ content, page, pages }: DocsEmbedProps) {
  const searchParams = useSearchParams()
  const { locale } = useDocsLocale()
  const isEmbed = searchParams.get("embed") === "true"

  if (isEmbed) {
    return (
      <div className="mx-auto max-w-prose px-4 py-6">
        <DocsContent content={content} category={page.category} />
      </div>
    )
  }

  return (
    <DocsShell pages={pages} current={page.slug} title={content[locale].title} toc={content[locale].toc}>
      <DocsContent content={content} category={page.category} />
    </DocsShell>
  )
}
