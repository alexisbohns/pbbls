"use client"

import { CheckCircle, PebbleRound, PebbleRoundLit1 } from "@pbbls/icons"
import { PBBLS_ICON_SIZE, type PbblsIconComponent } from "@/lib/icons/pbbls-icon"
import type { ReactNode } from "react"
import type { Log } from "@/lib/types"
import { logSummary, logTitle } from "@/lib/utils/log-localized"
import { useFormatDate } from "@/lib/i18n"
import { cn } from "@/lib/utils"

export type TimelineMode = "changelog" | "in_progress" | "backlog"

// In progress is the lit stone, the backlog a plain one (iOS `LogTimeline`).
const MODE_ICON: Record<TimelineMode, PbblsIconComponent> = {
  changelog: CheckCircle,
  in_progress: PebbleRoundLit1,
  backlog: PebbleRound,
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
  const formatDate = useFormatDate()

  return (
    <ol className="flex flex-col">
      {logs.map((log, index) => {
        const isLast = index === logs.length - 1
        const dateSource = showDate ? (log.released_at ?? log.published_at) : null
        const date = dateSource
          ? formatDate(dateSource, {
              day: "numeric",
              month: "long",
              year: "numeric",
            })
          : null
        const trailing = renderTrailing?.(log)

        return (
          <li key={log.id} className="flex gap-3">
            <div className="flex flex-col items-center pt-0.5">
              <Icon
                className={cn(
                  PBBLS_ICON_SIZE.inline,
                  "shrink-0",
                  mode === "changelog" ? "text-primary" : "text-muted-foreground",
                )}
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
