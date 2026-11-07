"use client"

import { useId } from "react"
import Link from "next/link"
import { useTranslations } from "next-intl"
import type { ComponentType } from "react"
import { IconChip } from "@/components/home/IconChip"
import { PRIMARY_CONTAINER_FILL } from "@/components/home/home-fills"
import { Stone } from "@/components/stones/Stone"
import { DOCS_ICONS } from "@/lib/config/docs-icons"
import { groupDocsPages } from "@/lib/docs/group-pages"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import { cn } from "@/lib/utils"
import type { DocsPageMeta } from "@/lib/docs/types"

type DocsNavProps = {
  pages: readonly DocsPageMeta[]
  /** The page being read, marked in its section. */
  current?: string
  onNavigate?: () => void
}

/**
 * The docs' main navigation: one section per category, each in the style of
 * Home's About Pebbles (a head, then a stone chip, the title over its line,
 * a chevron per page).
 */
export function DocsNav({ pages, current, onNavigate }: DocsNavProps) {
  const { locale } = useDocsLocale()
  const t = useTranslations("docs")
  // The nav renders more than once (the column, the phone menu, the index).
  const uid = useId()

  return (
    <nav aria-label={t("navAria")} className="flex flex-col gap-8">
      {groupDocsPages(pages).map(([category, list]) => {
        const titleId = `${uid}-${category}`
        return (
          <section key={category} aria-labelledby={titleId}>
            <h2 id={titleId} className="pb-type-section mb-2 px-1 text-m3-on-surface">
              {t(`categories.${category}`)}
            </h2>
            <ul className="flex flex-col gap-0.5">
              {list.map((page) => {
                const active = page.slug === current
                return (
                  <li key={page.slug}>
                    <Link
                      href={`/docs/${page.slug}`}
                      onClick={onNavigate}
                      aria-current={active ? "page" : undefined}
                      className={cn(
                        "flex items-center gap-3.5 rounded-2xl py-2 pr-3 pl-1.5 outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring",
                        !active && "hover:bg-m3-on-surface/5",
                      )}
                    >
                      <NavChip seed={`docs-${page.slug}`} icon={DOCS_ICONS[page.icon]} active={active} />
                      <span className="min-w-0 flex-1">
                        <span className={cn("pb-type-row-title block", active ? "text-m3-primary" : "text-m3-on-surface")}>
                          {page.titles[locale]}
                        </span>
                        <span className="mt-0.5 block text-sm leading-snug text-pretty text-m3-on-surface-variant">
                          {page.summaries[locale]}
                        </span>
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          </section>
        )
      })}
    </nav>
  )
}

/**
 * A row's chip. The page being read gets a pebble diamond instead: a stone
 * turned onto its point, in primary container, the glyph upright in
 * on-primary-container; the title in primary.
 */
function NavChip({ seed, icon: Icon, active }: { seed: string; icon: ComponentType<{ className?: string }>; active: boolean }) {
  if (!active) return <IconChip seed={seed} icon={Icon} />
  return (
    <span aria-hidden="true" className="grid h-[46px] w-12 shrink-0 place-items-center">
      <Stone seed={seed} corner={9} fill={PRIMARY_CONTAINER_FILL} className="grid size-[35px] rotate-45 place-items-center">
        <Icon className="size-[24px] -rotate-45 text-m3-on-primary-container" />
      </Stone>
    </span>
  )
}
