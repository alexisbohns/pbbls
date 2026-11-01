import type { RouteShell } from "@/lib/config/app-routes"
import { cn } from "@/lib/utils"

/**
 * <main>'s box for a route (#1384): its scroll, its padding, and on a tab
 * root the room it leaves the navigation (`app-nav-room`). One function, so
 * the shell and the sandbox's views lay a page out the same way.
 */
export function mainClass(shell: RouteShell, withNavigation: boolean): string {
  return cn(
    "min-w-0 flex-1 touch-pan-y overflow-x-hidden",
    shell.path ? "overflow-y-hidden" : "overflow-y-auto",
    withNavigation && "app-nav-room",
    shell.fullScreen
      ? "flex flex-col pb-(--app-nav-room)"
      : shell.immersive
        ? "px-4 pt-[calc(2rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))]"
        : "pt-[var(--safe-area-top)] pb-[calc(2rem+var(--safe-area-bottom)+var(--app-nav-room))]",
  )
}
