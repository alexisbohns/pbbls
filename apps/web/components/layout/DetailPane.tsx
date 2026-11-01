"use client"

import { useEffect, useMemo, useRef, ViewTransition, type ReactNode } from "react"
import { usePathname } from "next/navigation"
import { DETAIL_TITLE_ID, DetailPaneContext, type DetailPaneState } from "@/lib/hooks/useDetailPane"
import { useDetailClose } from "@/lib/hooks/useDetailClose"
import { useMediaQuery } from "@/lib/hooks/useMediaQuery"
import { PANES_QUERY } from "@/lib/config/app-routes"

/**
 * The frame every detail opens in (#1384, two-area spec D5, D8, D9). It is the
 * layout of the `@detail` slot's `(pane)` group, so moving from one detail to
 * the next swaps what is inside a frame that stays mounted, its motion and its
 * focus belonging to the visit rather than to each page.
 *
 * From 1024 px it covers the right area, whose landmark it names through the
 * title; the Path's line stays mounted under it. Below, the right area is a
 * full-screen overlay (beside the rail from 600 px) and this is its dialog:
 * modal, labelled by the title, with the tab page under it inert.
 *
 * Focus moves to the title when a detail opens and back to whatever opened it
 * when it closes (else to the left area's heading). Escape closes it, unless
 * the key belongs to a dialog opened from inside the detail.
 */
export function DetailPane({ children, onClose }: { children: ReactNode; /** Stands in for the route's close where there is no route (the sandbox). */ onClose?: () => void }) {
  const wide = useMediaQuery(PANES_QUERY)
  const routeClose = useDetailClose()
  const close = onClose ?? routeClose
  const pathname = usePathname()
  const opener = useRef<HTMLElement | null>(null)

  // The control that opened the pane, read before focus moves into it.
  useEffect(() => {
    const active = document.activeElement
    opener.current = active instanceof HTMLElement && active !== document.body ? active : null
    return () => {
      const back = opener.current
      if (back?.isConnected) back.focus({ preventScroll: true })
      else document.querySelector<HTMLElement>("main h1")?.focus({ preventScroll: true })
    }
  }, [])

  // Each detail's title takes focus as it opens, the first and every next one.
  useEffect(() => {
    document.getElementById(DETAIL_TITLE_ID)?.focus({ preventScroll: true })
  }, [pathname])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape" || e.defaultPrevented) return
      const target = e.target instanceof Element ? e.target : null
      if (target?.closest("[role=dialog]:not([data-detail]), [role=alertdialog], [role=menu], [role=listbox]")) return
      e.preventDefault()
      close()
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [close])

  const state = useMemo((): DetailPaneState => ({ wide, close }), [wide, close])

  return (
    <DetailPaneContext value={state}>
      <ViewTransition enter={wide ? "detail-enter" : "overlay-enter"} exit={wide ? "detail-exit" : "overlay-exit"} update="detail-swap" default="none">
        <div
          data-detail=""
          role={wide ? undefined : "dialog"}
          aria-modal={wide ? undefined : true}
          aria-labelledby={wide ? undefined : DETAIL_TITLE_ID}
          className="m3 absolute inset-0 z-10 flex flex-col overflow-y-auto overscroll-contain bg-background font-sans text-foreground"
        >
          {children}
        </div>
      </ViewTransition>
    </DetailPaneContext>
  )
}
