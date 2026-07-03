import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

type SectionLabelProps = {
  children: ReactNode
  className?: string
  id?: string
}

/**
 * Uppercase muted section heading — the web analog of the iOS `.cardHeading`
 * typography token. Shared by Profile card headings (STATS, COLLECTIONS) and
 * Settings section headers (Informations, Providers, …).
 */
export function SectionLabel({ children, className, id }: SectionLabelProps) {
  return (
    <span
      id={id}
      className={cn(
        "text-sm font-semibold uppercase tracking-wide text-muted-foreground",
        className,
      )}
    >
      {children}
    </span>
  )
}
