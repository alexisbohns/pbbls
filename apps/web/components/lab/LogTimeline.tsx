"use client"

import { CircleCheckBig, CircleDashed, CircleDot, type LucideIcon } from "lucide-react"
import type { ReactNode } from "react"
import type { Log } from "@/lib/types"
import { logSummary, logTitle } from "@/lib/utils/log-localized"
import { dayMonthYearFormatter } from "@/lib/utils/formatters"
import { cn } from "@/lib/utils"

export type TimelineMode = "changelog" | "in_progress" | "backlog"

const MODE_ICON: Record<TimelineMode, LucideIcon> = {
  changelog: CircleCheckBig,
  in_progress: CircleDot,
  backlog: CircleDashed,
}

type LogTimelineProps = {
  mode: TimelineMode
  logs: Log[]
  locale: string
  renderTrailing?: (log: Log) => ReactNode
}

// Vertical timeline used by the Lab tab's changelog, in-progress, and
// backlog sections. Icon column on the left with a connecting line; the
// content column shows the localized title and (changelog only) the
// shipped date. Backlog rows can attach a reaction button via `renderTrailing`.
export function LogTimeline({ mode, logs, locale, renderTrailing }: LogTimelineProps) {
  const Icon = MODE_ICON[mode]
  const showDate = mode === "changelog"

  return (
    <ol className="flex flex-col">
      {logs.map((log, index) => {
        const isLast = index === logs.length - 1
        const date =
          showDate && log.published_at
            ? dayMonthYearFormatter.format(new Date(log.published_at))
            : null
        const trailing = renderTrailing?.(log)

        return (
          <li key={log.id} className="flex gap-3">
            <div className="flex flex-col items-center pt-0.5">
              <Icon
                className={cn(
                  "size-3.5 shrink-0",
                  mode === "changelog" ? "text-primary" : "text-muted-foreground",
                )}
                aria-hidden
              />
              {!isLast && <div className="my-1 w-px flex-1 bg-border" />}
            </div>

            <div className={cn("flex min-w-0 flex-1 items-start gap-3", !isLast && "pb-5")}>
              <div className="min-w-0 flex-1">
                {date && (
                  <p className="text-xs text-muted-foreground">{date}</p>
                )}
                <h3 className="text-sm font-medium leading-snug">
                  {logTitle(log, locale)}
                </h3>
                <p className="mt-1 text-xs text-muted-foreground">
                  {logSummary(log, locale)}
                </p>
              </div>
              {trailing && <div className="shrink-0">{trailing}</div>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
