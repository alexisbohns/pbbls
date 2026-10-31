import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

/**
 * A page that wears the brand Material 3 roles while the rest of the app keeps
 * its colour world (#1384, spec D3). The `.m3` class picks the generated scheme
 * (light, or Night below `.dark` on <html>) and the widened bridge in
 * globals.css points the shadcn tokens at its roles, so everything inside it,
 * shadcn primitives included, renders M3. Rendered by the route layout on the
 * server, so there is no flash. `MainContent` also toggles `m3` on <body> for
 * the same routes, so the overscroll, the safe areas and portalled dialogs and
 * toasts stand on the same ground.
 */
export function M3Island({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("m3 min-h-full bg-background text-foreground font-sans", className)}>{children}</div>
}
