import type { ReactNode } from "react"
import { AppNavigation } from "@/components/layout/AppNavigation"
import { mainClass } from "@/components/layout/main-class"
import { routeShell } from "@/lib/config/app-routes"

/**
 * A view inside the app shell as `MainContent` lays a tab root out (#1384):
 * the navigation (bar or rail by the window's width, since breakpoints follow
 * the window, not the frame) beside the page box `mainClass` gives `at`.
 */
export function ShellFrame({ at, children }: { at: string; children: ReactNode }) {
  return (
    <div className="m3 flex h-full bg-background">
      <AppNavigation at={at} />
      <div className={mainClass(routeShell(at), true)}>{children}</div>
    </div>
  )
}
