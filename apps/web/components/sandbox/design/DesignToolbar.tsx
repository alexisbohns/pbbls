"use client"

import { usePathname } from "next/navigation"
import { tuningProgress } from "@/lib/design/specimens"
import { DesignTools, type DesignToolsState } from "./DesignTools"
import { currentPage } from "./directory-pages"

/**
 * The directory's second area: the page's header and its tools. From 1024 px
 * it is a column of its own between the rail and the page (the app's
 * two-area layout), held in place while the page scrolls. From 600 px it is a
 * sticky header over the page. Below 600 px the mobile bar takes its place.
 */
export function DesignToolbar(state: DesignToolsState) {
  const page = currentPage(usePathname())
  const { tuned, total } = tuningProgress()
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/90 backdrop-blur max-rail:hidden lg:h-dvh lg:w-md lg:shrink-0 lg:overflow-y-auto lg:border-r lg:border-b-0 lg:bg-background lg:backdrop-blur-none">
      <div className="flex flex-col gap-2 px-4 py-3 lg:gap-6 lg:px-6 lg:pt-8 lg:pb-10">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 lg:flex-col lg:items-start lg:gap-3">
          <h1 className="font-heading text-lg lg:flex lg:flex-col lg:gap-1">
            <span className="lg:text-sm lg:text-muted-foreground">Pebbles design system</span>
            {page && (
              <span className="text-muted-foreground lg:text-4xl lg:text-foreground">
                <span className="lg:hidden"> · </span>
                {page.label}
              </span>
            )}
          </h1>
          {page && <p className="hidden max-w-sm text-sm text-muted-foreground lg:block">{page.summary}</p>}
          <span className="text-xs text-muted-foreground">
            {tuned}/{total} tuned · <kbd>T</kbd> theme · <kbd>D</kbd> mode
          </span>
        </div>
        <DesignTools page={page} layout="row" {...state} />
      </div>
    </header>
  )
}
