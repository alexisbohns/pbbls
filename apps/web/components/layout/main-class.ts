import type { RouteShell } from "@/lib/config/app-routes"
import { cn } from "@/lib/utils"

/**
 * <main>'s box for a route (#1384): its scroll, its padding, on a tab or a
 * detail the room it leaves the navigation (`app-nav-room`), and from
 * 1024 px its width as the left area. One function, so
 * the shell and the sandbox's views lay a page out the same way.
 */
export function mainClass(shell: RouteShell, withNavigation: boolean): string {
  return cn(
    "min-w-0 flex-1 touch-pan-y overflow-x-hidden",
    // The Path's whole book scrolls inside itself; its verso alone, in the
    // left area from 1024 px, scrolls like any page.
    shell.path ? "overflow-y-hidden panes:overflow-y-auto" : "overflow-y-auto",
    withNavigation && "app-nav-room",
    // From 1024 px a page with the navigation is the left area: the reading
    // column's width, the right area taking the rest (two-area spec D1).
    withNavigation && "panes:w-md panes:flex-none",
    shell.fullScreen
      ? "flex flex-col pb-(--app-nav-room)"
      : shell.immersive
        ? "px-4 pt-[calc(2rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))]"
        : "pt-[var(--safe-area-top)] pb-[calc(2rem+var(--safe-area-bottom)+var(--app-nav-room))]",
  )
}
