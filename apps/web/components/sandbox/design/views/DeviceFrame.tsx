"use client"

import { useState, type ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export function DeviceFrame({ children }: { children: ReactNode }) {
  const [fill, setFill] = useState(false)
  return (
    <div className="flex w-full flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        <Button size="xs" variant={fill ? "outline" : "default"} aria-pressed={!fill} onClick={() => setFill(false)}>
          Phone
        </Button>
        <Button size="xs" variant={fill ? "default" : "outline"} aria-pressed={fill} onClick={() => setFill(true)}>
          Fill
        </Button>
        <span>Breakpoints follow the browser window, not the frame: narrow the window for the mobile layout.</span>
      </div>
      {/* translateZ(0) makes the frame the containing block for position:fixed
          descendants (bottom docks, sticky bars), so a view's fixed chrome stays
          inside its frame instead of covering the design page. */}
      <div
        className={cn(
          "h-[760px] overflow-y-auto rounded-3xl border border-border bg-background [transform:translateZ(0)]",
          fill ? "w-full" : "w-[390px] max-w-full",
        )}
      >
        {children}
      </div>
    </div>
  )
}
