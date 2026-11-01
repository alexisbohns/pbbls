"use client"

import type { ReactNode } from "react"
import { AppNavigation } from "@/components/layout/AppNavigation"
import { DetailPane } from "@/components/layout/DetailPane"
import { RightArea } from "@/components/layout/RightArea"
import { mainClass } from "@/components/layout/main-class"
import { PathFocusProvider } from "@/components/path/PathFocusProvider"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { PANES_QUERY, routeShell } from "@/lib/config/app-routes"
import { useMediaQuery } from "@/lib/hooks/useMediaQuery"
import { cn } from "@/lib/utils"

/**
 * A view inside the app shell as `MainContent` lays a tab or a detail out
 * (#1384, two-area spec D1): the navigation, the left area (the page box
 * `mainClass` gives `at`) and the right area with the Path's line and, when
 * given, a detail in its pane. Breakpoints follow the window, not the frame,
 * so a phone layout is checked at a phone-sized window and two areas with
 * the frame on "Fill". Closing the sandbox's detail does nothing.
 */
export function ShellFrame({ at, children, detail }: { at: string; children: ReactNode; detail?: ReactNode }) {
  const wide = useMediaQuery(PANES_QUERY)
  const open = detail !== undefined
  const overlay = open && !wide
  return (
    <PathFocusProvider>
      <div className="m3 relative flex h-full bg-background">
        <AppNavigation at={at} />
        <div inert={overlay} className={cn(mainClass(routeShell(at), true), overlay && "overflow-y-hidden")}>
          {children}
        </div>
        <RightArea wide={wide} detailOpen={open} detail={open ? <DetailPane onClose={() => {}}>{detail}</DetailPane> : null} />
        <StoneSprite />
      </div>
    </PathFocusProvider>
  )
}
