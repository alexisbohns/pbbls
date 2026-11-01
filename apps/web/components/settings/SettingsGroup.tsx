import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

type SettingsGroupProps = {
  children: ReactNode
  className?: string
  "aria-label"?: string
  "aria-labelledby"?: string
}

/**
 * The container for Settings rows: an M3 list group since #1384 (two-area
 * spec D7, as Android #1437), items on `surface-container` 2 px apart with
 * rounded ends, no borders and no dividers. Wraps `SettingsRow`s.
 */
export function SettingsGroup({ children, className, ...aria }: SettingsGroupProps) {
  return (
    <ul
      {...aria}
      className={cn("m3-list-group", className)}
    >
      {children}
    </ul>
  )
}
