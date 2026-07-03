import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

type SettingsGroupProps = {
  children: ReactNode
  className?: string
  "aria-label"?: string
  "aria-labelledby"?: string
}

/**
 * Bordered, divided list container for Settings rows — the web analog of a
 * grouped iOS `List` section. Wraps `SettingsRow`s. Dedupes the
 * `divide-y rounded-xl border` markup that used to be hand-rolled across the
 * profile page, appearance, and legal sections.
 */
export function SettingsGroup({ children, className, ...aria }: SettingsGroupProps) {
  return (
    <ul
      {...aria}
      className={cn(
        "divide-y divide-border rounded-xl border border-border",
        className,
      )}
    >
      {children}
    </ul>
  )
}
