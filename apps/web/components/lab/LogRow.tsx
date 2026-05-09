import type { ReactNode } from "react"
import type { Log } from "@/lib/types"
import { logSummary, logTitle } from "@/lib/utils/log-localized"

type LogRowProps = {
  log: Log
  locale: string
  trailing?: ReactNode
}

// Compact row rendering a log's localized title and summary. Used for
// changelog, in-progress and backlog sections. The `trailing` slot lets
// the parent attach contextual controls (e.g. a reaction button on backlog).
// Mirrors apps/ios/Pebbles/Features/Lab/Components/LogRow.swift.
export function LogRow({ log, locale, trailing }: LogRowProps) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-border px-4 py-3">
      <div className="min-w-0 flex-1">
        <h3 className="text-sm font-medium">{logTitle(log, locale)}</h3>
        <p className="mt-1 line-clamp-3 text-xs text-muted-foreground">
          {logSummary(log, locale)}
        </p>
      </div>
      {trailing && <div className="shrink-0">{trailing}</div>}
    </div>
  )
}
