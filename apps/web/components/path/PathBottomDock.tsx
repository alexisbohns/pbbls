"use client"

import { PathBottomBar } from "@/components/path/PathBottomBar"
import { cn } from "@/lib/utils"

/**
 * The dock floats over the foot of the Path (#1052): the week scrolls under
 * its translucent pill, and keeps 96 px at its end to clear it. Place it with
 * `className` (the Path lays it over the verso from 900 px).
 */
export function PathBottomDock({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "pointer-events-none absolute inset-x-0 bottom-0 z-20 flex justify-center px-4 pb-[calc(12px+var(--safe-area-bottom))]",
        className,
      )}
    >
      <PathBottomBar className="pointer-events-auto" />
    </div>
  )
}
