"use client"

// The Path's line is drawn here on every tab from 1024 px: the Spine's small
// caps, office figures and real italics load with it.
import "@fontsource-variable/ysabeau-sc"
import "@fontsource-variable/ysabeau-office"
import "@fontsource-variable/ysabeau/wght-italic.css"
import type { ReactNode } from "react"
import { useTranslations } from "next-intl"
import { PathRecto } from "@/components/path/PathRecto"
import { DETAIL_TITLE_ID } from "@/lib/hooks/useDetailPane"
import { cn } from "@/lib/utils"

type RightAreaProps = {
  /** Two areas (from 1024 px): the line shows under any detail. */
  wide: boolean
  /** A detail is open in the slot. */
  detailOpen: boolean
  /** The `@detail` slot. */
  detail: ReactNode
}

/**
 * The right area (#1384, two-area spec D1, D3, D8). From 1024 px it is the
 * column beside the tab page: the Path's line, mounted once and kept while
 * the left area moves, with the detail slot over it. Below 1024 px it is only
 * there while a detail is open, as the overlay's box: the whole window under
 * 600 px, beside the rail from 600 px. Its landmark is named "Path" while the
 * line shows, and by the detail's title once one covers it.
 */
export function RightArea({ wide, detailOpen, detail }: RightAreaProps) {
  const t = useTranslations("nav")
  return (
    <section
      aria-label={detailOpen ? undefined : t("path")}
      aria-labelledby={detailOpen ? DETAIL_TITLE_ID : undefined}
      className={cn(
        "m3 bg-background text-foreground",
        "panes:relative panes:block panes:min-w-0 panes:flex-1 panes:border-s panes:border-m3-rule",
        detailOpen ? "max-panes:fixed max-panes:inset-0 max-panes:z-50 rail:max-panes:start-24" : "max-panes:hidden",
      )}
    >
      {wide && (
        <div inert={detailOpen} aria-hidden={detailOpen || undefined} className="isolate h-full">
          <PathRecto />
        </div>
      )}
      {detail}
    </section>
  )
}
