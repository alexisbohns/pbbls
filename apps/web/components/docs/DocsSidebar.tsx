"use client"

import { useState } from "react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { ArrowLeft, ChevronDown, ChevronLeft } from "lucide-react"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import { buttonVariants } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { LocaleToggle } from "@/components/docs/LocaleToggle"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import type { DocsPageMeta } from "@/lib/docs/types"

type DocsSidebarProps = {
  pages: DocsPageMeta[]
}

type DocsMenuProps = {
  pages: DocsPageMeta[]
  onNavigate?: () => void
}

function DocsMenu({ pages, onNavigate }: DocsMenuProps) {
  const params = useParams<{ slug?: string }>()
  const { locale } = useDocsLocale()
  const t = useTranslations("docs")

  const grouped = pages.reduce<Record<string, DocsPageMeta[]>>((acc, page) => {
    const group = acc[page.category] ?? []
    group.push(page)
    acc[page.category] = group
    return acc
  }, {})

  return (
    <>
      <LocaleToggle />

      <nav aria-label={t("navAria")}>
        {Object.entries(grouped).map(([category, categoryPages]) => (
          <section key={category}>
            <h2 className="mb-1 px-3 text-xs font-medium uppercase tracking-wider text-muted-foreground">
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
                        onClick={onNavigate}
                        className={cn(
                          "block rounded-full px-3 py-2 text-sm transition-colors",
                          isActive
                            ? "bg-secondary font-medium text-secondary-foreground"
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
    </>
  )
}

export function DocsSidebar({ pages }: DocsSidebarProps) {
  const params = useParams<{ slug?: string }>()
  const { locale } = useDocsLocale()
  const t = useTranslations("docs")
  const [open, setOpen] = useState(false)

  const current = pages.find((page) => page.slug === params.slug)

  return (
    <div className="flex flex-col gap-4">
      <Link href="/path" className={cn(buttonVariants({ variant: "outline" }), "hidden self-end md:inline-flex")}>
        <ChevronLeft />
        {t("back")}
      </Link>

      {/* Phones: an arrow back to the app, then language and pages folded
          behind the current page's title. */}
      <div className="flex items-start gap-2 md:hidden">
        <Link
          href="/path"
          aria-label={t("back")}
          className={cn(buttonVariants({ variant: "secondary" }), "size-12 shrink-0 rounded-full [&_svg:not([class*='size-'])]:size-6")}
        >
          <ArrowLeft />
        </Link>
        <Collapsible open={open} onOpenChange={setOpen} className="min-w-0 flex-1 rounded-xl bg-card">
          <CollapsibleTrigger className="flex min-h-12 w-full items-center justify-between gap-3 rounded-xl px-4 text-left text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring [&[data-panel-open]>svg]:rotate-180">
            <span className="truncate">{current ? current.titles[locale] : t("title")}</span>
            <ChevronDown aria-hidden className="size-4 shrink-0 text-muted-foreground transition-transform" />
          </CollapsibleTrigger>
          <CollapsibleContent className="flex flex-col gap-4 px-1 pb-3">
            <DocsMenu pages={pages} onNavigate={() => setOpen(false)} />
          </CollapsibleContent>
        </Collapsible>
      </div>

      <div className="hidden flex-col gap-4 md:flex">
        <DocsMenu pages={pages} />
      </div>
    </div>
  )
}
