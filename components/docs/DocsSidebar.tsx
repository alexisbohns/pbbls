"use client"

import Link from "next/link"
import { useParams } from "next/navigation"
import { ChevronLeft } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { LocaleToggle } from "@/components/docs/LocaleToggle"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import type { DocsPageMeta } from "@/lib/docs/types"

type DocsSidebarProps = {
  pages: DocsPageMeta[]
}

export function DocsSidebar({ pages }: DocsSidebarProps) {
  const params = useParams<{ slug?: string }>()
  const { locale } = useDocsLocale()

  const grouped = pages.reduce<Record<string, DocsPageMeta[]>>((acc, page) => {
    const group = acc[page.category] ?? []
    group.push(page)
    acc[page.category] = group
    return acc
  }, {})

  return (
    <div className="flex flex-col gap-4">
      <Button variant="outline" className="hidden md:flex self-end" render={<Link href="/path" />}>
        <ChevronLeft />
        Back to app
      </Button>

      <LocaleToggle />

      <nav aria-label="Documentation">
        {Object.entries(grouped).map(([category, categoryPages]) => (
          <section key={category}>
            <h2 className="mb-1 px-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {category}
            </h2>
            <ul className="flex flex-col gap-0.5">
              {categoryPages
                .sort((a, b) => a.order - b.order)
                .map((page) => {
                  const isActive = params.slug === page.slug
                  return (
                    <li key={page.slug}>
                      <Link
                        href={`/docs/${page.slug}`}
                        className={cn(
                          "block rounded-lg px-2 py-1.5 text-sm transition-colors",
                          isActive
                            ? "bg-muted font-medium text-primary"
                            : "text-muted-foreground hover:bg-muted hover:text-foreground",
                        )}
                        aria-current={isActive ? "page" : undefined}
                      >
                        {page.titles[locale]}
                      </Link>
                    </li>
                  )
                })}
            </ul>
          </section>
        ))}
      </nav>
    </div>
  )
}
