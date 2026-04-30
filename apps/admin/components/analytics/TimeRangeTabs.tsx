"use client"

import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { cn } from "@/lib/utils"
import {
  TIME_RANGES,
  TIME_RANGE_LABELS,
  isTimeRange,
  type TimeRange,
} from "@/lib/analytics/types"

const DEFAULT: TimeRange = "30d"

export function TimeRangeTabs() {
  const params = useSearchParams()
  const raw = params?.get("range") ?? undefined
  const active: TimeRange = isTimeRange(raw) ? raw : DEFAULT

  return (
    <nav aria-label="Time range" className="flex items-center gap-1 rounded-md border p-1">
      {TIME_RANGES.map((range) => {
        const isActive = range === active
        return (
          <Link
            key={range}
            href={`?range=${range}`}
            scroll={false}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "rounded px-3 py-1 text-sm transition-colors",
              isActive
                ? "bg-foreground text-background"
                : "text-muted-foreground hover:bg-muted",
            )}
          >
            {TIME_RANGE_LABELS[range]}
          </Link>
        )
      })}
    </nav>
  )
}
