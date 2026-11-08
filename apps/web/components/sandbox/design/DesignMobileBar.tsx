"use client"

import { useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Dialog } from "@base-ui/react/dialog"
import { List, XSign } from "@pbbls/icons"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { directoryHref } from "./DesignContext"
import { Caret } from "./DesignSelect"
import { DesignTools, type DesignToolsState } from "./DesignTools"
import { currentPage, DIRECTORY_PAGES } from "./directory-pages"

const ROUND_BUTTON =
  "flex size-12 shrink-0 items-center justify-center rounded-full bg-m3-secondary-container text-m3-on-secondary-container outline-none transition-colors hover:bg-m3-secondary-container/80 focus-visible:ring-2 focus-visible:ring-m3-primary motion-reduce:transition-none"

/**
 * Below 600 px the directory keeps one sticky bar, like the public docs: the
 * page's name folding its tools open, and a menu button that opens the
 * directory's pages over the whole screen. It takes the place of the rail and
 * of the header.
 */
export function DesignMobileBar({ themeKey, ...state }: DesignToolsState & { themeKey: string }) {
  const page = currentPage(usePathname())
  const [toolsOpen, setToolsOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="m3 sticky top-0 z-40 bg-m3-surface/90 px-4 pt-[calc(0.5rem+var(--safe-area-top))] pb-2 text-m3-on-surface backdrop-blur-md rail:hidden">
      <div className="flex items-start gap-2">
        <Collapsible open={toolsOpen} onOpenChange={setToolsOpen} className="min-w-0 flex-1 rounded-3xl bg-m3-surface-container">
          <CollapsibleTrigger
            aria-label={`${page?.label ?? "Design"}: tools`}
            className="group flex min-h-12 w-full items-center justify-between gap-3 rounded-3xl px-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
          >
            <span className="flex min-w-0 flex-col">
              <span className="text-[0.6875rem] leading-4 font-medium tracking-wide text-m3-on-surface-variant">Pebbles design system</span>
              <span className="truncate text-sm leading-5 font-medium">{page?.label ?? "Design"}</span>
            </span>
            <span className="text-m3-on-surface-variant [&_svg]:transition-transform group-data-[panel-open]:[&_svg]:rotate-180">
              <Caret />
            </span>
          </CollapsibleTrigger>
          <CollapsibleContent className="max-h-[70dvh] overflow-y-auto px-2 pb-2">
            <DesignTools page={page} layout="grid" {...state} />
          </CollapsibleContent>
        </Collapsible>

        <Dialog.Root open={menuOpen} onOpenChange={setMenuOpen}>
          <Dialog.Trigger aria-label="Open the design system's pages" className={ROUND_BUTTON}>
            <List className={PBBLS_ICON_SIZE.toolbar} />
          </Dialog.Trigger>
          <Dialog.Portal>
            <Dialog.Popup className="m3 fixed inset-0 z-[60] overflow-y-auto bg-m3-surface px-4 pt-[calc(0.5rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))] text-m3-on-surface transition-opacity duration-200 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 motion-reduce:transition-none">
              <div className="flex items-center justify-between">
                <Dialog.Title className="font-heading text-lg">Pebbles design system</Dialog.Title>
                <Dialog.Close aria-label="Close the pages" className={ROUND_BUTTON}>
                  <XSign className={PBBLS_ICON_SIZE.toolbar} />
                </Dialog.Close>
              </div>
              <nav aria-label="Design system" className="mt-8">
                <ul className="flex flex-col gap-1">
                  {DIRECTORY_PAGES.map(({ path, label, summary, icon: Icon }) => (
                    <li key={path}>
                      <Link
                        href={directoryHref(path, themeKey)}
                        onClick={() => setMenuOpen(false)}
                        aria-current={page?.path === path ? "page" : undefined}
                        className={cn(
                          "flex items-start gap-4 rounded-3xl px-4 py-3 outline-none focus-visible:ring-2 focus-visible:ring-m3-primary",
                          page?.path === path
                            ? "bg-m3-secondary-container text-m3-on-secondary-container"
                            : "text-m3-on-surface hover:bg-m3-on-surface/5",
                        )}
                      >
                        <Icon className={cn(PBBLS_ICON_SIZE.toolbar, "mt-0.5 shrink-0")} />
                        <span className="flex min-w-0 flex-col gap-0.5">
                          <span className="text-base font-medium">{label}</span>
                          <span className="text-sm text-m3-on-surface-variant">{summary}</span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
            </Dialog.Popup>
          </Dialog.Portal>
        </Dialog.Root>
      </div>
    </div>
  )
}
