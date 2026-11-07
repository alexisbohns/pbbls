"use client"

import { useState } from "react"
import Link from "next/link"
import { Dialog } from "@base-ui/react/dialog"
import { ArrowLeft, ChevronDown, Menu, X } from "lucide-react"
import { useTranslations } from "next-intl"
import { buttonVariants } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { DocsNav } from "@/components/docs/DocsNav"
import { DocsWordmark } from "@/components/docs/DocsWordmark"
import { LocaleToggle } from "@/components/docs/LocaleToggle"
import { tocNumber } from "@/components/docs/DocsToc"
import { scrollToSection } from "@/lib/docs/scroll-to-section"
import { cn } from "@/lib/utils"
import type { DocsPageMeta, DocsTocItem } from "@/lib/docs/types"

type DocsMobileBarProps = {
  pages: readonly DocsPageMeta[]
  current?: string
  /** The page's title, shown while no section is being read. */
  title: string
  toc: readonly DocsTocItem[]
  active: string | null
}

const ROUND_BUTTON = cn(
  buttonVariants({ variant: "secondary" }),
  "size-12 shrink-0 rounded-full [&_svg:not([class*='size-'])]:size-6",
)

/**
 * Below 1024 px the docs keep one sticky bar: back to the app, the page's
 * contents folded behind the section being read, and a menu button that opens
 * the main navigation over the whole screen.
 */
export function DocsMobileBar({ pages, current, title, toc, active }: DocsMobileBarProps) {
  const t = useTranslations("docs")
  const [tocOpen, setTocOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const activeIndex = toc.findIndex((item) => item.id === active)
  const label = activeIndex >= 0 ? toc[activeIndex].label : title

  return (
    <div className="sticky top-0 z-30 -mx-4 bg-m3-surface/90 px-4 pt-[calc(0.5rem+var(--safe-area-top))] pb-2 backdrop-blur-md lg:hidden">
      <div className="flex items-start gap-2">
        <Link href="/path" aria-label={t("back")} className={ROUND_BUTTON}>
          <ArrowLeft />
        </Link>

        {toc.length > 0 ? (
          <Collapsible open={tocOpen} onOpenChange={setTocOpen} className="min-w-0 flex-1 rounded-3xl bg-m3-surface-container">
            <CollapsibleTrigger
              aria-label={t("tocAria")}
              className="flex min-h-12 w-full items-center justify-between gap-3 rounded-3xl px-4 text-left text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-ring [&[data-panel-open]>svg]:rotate-180"
            >
              <span className="flex min-w-0 gap-3">
                {activeIndex >= 0 && (
                  <span className="shrink-0 font-mono text-m3-outline tabular-nums">{tocNumber(activeIndex)}</span>
                )}
                <span className="truncate">{label}</span>
              </span>
              <ChevronDown aria-hidden className="size-4 shrink-0 text-m3-on-surface-variant transition-transform" />
            </CollapsibleTrigger>
            <CollapsibleContent>
              <ol className="max-h-[65dvh] overflow-y-auto px-2 pb-2">
                {toc.map((item, i) => (
                  <li key={item.id}>
                    <a
                      href={`#${item.id}`}
                      onClick={(event) => {
                        setTocOpen(false)
                        scrollToSection(event, item.id)
                      }}
                      aria-current={item.id === active ? "location" : undefined}
                      className={cn(
                        "flex gap-3 rounded-2xl px-2 py-2.5 text-sm leading-snug outline-none focus-visible:ring-2 focus-visible:ring-ring",
                        item.id === active
                          ? "bg-m3-secondary-container text-m3-on-secondary-container"
                          : "text-m3-on-surface-variant hover:bg-m3-on-surface/5",
                      )}
                    >
                      <span className="shrink-0 font-mono text-m3-outline tabular-nums">{tocNumber(i)}</span>
                      <span>{item.label}</span>
                    </a>
                  </li>
                ))}
              </ol>
            </CollapsibleContent>
          </Collapsible>
        ) : (
          <p className="flex min-h-12 min-w-0 flex-1 items-center truncate rounded-3xl bg-m3-surface-container px-4 text-sm font-medium">
            {title}
          </p>
        )}

        <Dialog.Root open={menuOpen} onOpenChange={setMenuOpen}>
          <Dialog.Trigger aria-label={t("openMenu")} className={ROUND_BUTTON}>
            <Menu />
          </Dialog.Trigger>
          <Dialog.Portal>
            <Dialog.Popup className="fixed inset-0 z-[60] overflow-y-auto bg-m3-surface px-4 pt-[calc(0.5rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))] text-m3-on-surface transition-opacity duration-200 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0">
              <div className="flex items-center justify-between">
                <DocsWordmark />
                <Dialog.Close aria-label={t("closeMenu")} className={ROUND_BUTTON}>
                  <X />
                </Dialog.Close>
              </div>
              <Dialog.Title className="sr-only">{t("title")}</Dialog.Title>
              <LocaleToggle className="mt-6 mb-8" />
              <DocsNav pages={pages} current={current} onNavigate={() => setMenuOpen(false)} />
            </Dialog.Popup>
          </Dialog.Portal>
        </Dialog.Root>
      </div>
    </div>
  )
}
