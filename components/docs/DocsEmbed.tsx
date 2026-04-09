"use client"

import { useSearchParams } from "next/navigation"
import { PageLayout } from "@/components/layout/PageLayout"
import { DocsSidebar } from "@/components/docs/DocsSidebar"
import { DocsContent } from "@/components/docs/DocsContent"
import type { DocsLocale, DocsPageContent, DocsPageMeta } from "@/lib/docs/types"

type DocsEmbedProps = {
  content: Record<DocsLocale, DocsPageContent>
  pages: DocsPageMeta[]
}

export function DocsEmbed({ content, pages }: DocsEmbedProps) {
  const searchParams = useSearchParams()
  const isEmbed = searchParams.get("embed") === "true"

  if (isEmbed) {
    return (
      <div className="mx-auto max-w-prose px-4 py-6">
        <DocsContent content={content} />
      </div>
    )
  }

  return (
    <PageLayout sidebar={<DocsSidebar pages={pages} />}>
      <section>
        <DocsContent content={content} />
      </section>
    </PageLayout>
  )
}
