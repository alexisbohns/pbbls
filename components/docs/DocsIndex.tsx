"use client"

import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { FileText } from "lucide-react"
import { PageLayout } from "@/components/layout/PageLayout"
import { DocsSidebar } from "@/components/docs/DocsSidebar"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import type { DocsPageMeta } from "@/lib/docs/types"

type DocsIndexProps = {
  pages: DocsPageMeta[]
}

export function DocsIndex({ pages }: DocsIndexProps) {
  const { locale } = useDocsLocale()
  const searchParams = useSearchParams()
  const isEmbed = searchParams.get("embed") === "true"

  const grouped = pages.reduce<Record<string, DocsPageMeta[]>>((acc, page) => {
    const group = acc[page.category] ?? []
    group.push(page)
    acc[page.category] = group
    return acc
  }, {})

  const content = (
    <section>
      <h1 className="mb-6 text-xl font-semibold">
        {locale === "fr" ? "Documentation" : "Documentation"}
      </h1>
      {Object.entries(grouped).map(([category, categoryPages]) => (
        <div key={category} className="mb-6">
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
            {category}
          </h2>
          <ul className="flex flex-col gap-2">
            {categoryPages
              .sort((a, b) => a.order - b.order)
              .map((page) => (
                <li key={page.slug}>
                  <Link
                    href={`/docs/${page.slug}`}
                    className="flex items-center gap-3 rounded-lg border border-border px-4 py-3 text-sm transition-colors hover:bg-muted"
                  >
                    <FileText className="size-4 shrink-0 text-muted-foreground" />
                    {page.titles[locale]}
                  </Link>
                </li>
              ))}
          </ul>
        </div>
      ))}
    </section>
  )

  if (isEmbed) {
    return <div className="mx-auto max-w-prose px-4 py-6">{content}</div>
  }

  return (
    <PageLayout sidebar={<DocsSidebar pages={pages} />}>
      {content}
    </PageLayout>
  )
}
