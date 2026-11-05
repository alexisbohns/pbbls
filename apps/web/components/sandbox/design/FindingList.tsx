import type { ReactNode } from "react"
import type { FindingGroup } from "@/lib/design/audit"

type FindingListProps = {
  groups: readonly FindingGroup[]
  /** Drawn before each value (a swatch, an icon). */
  lead?: (group: FindingGroup) => ReactNode
  /** Shown when nothing was found. */
  empty: string
  /** Rows shown before "show all"; the rest stay one click away. */
  limit?: number
}

/** Distinct values found in the source, each opening onto the file:line list. */
export function FindingList({ groups, lead, empty, limit = 24 }: FindingListProps) {
  if (groups.length === 0) return <p className="text-sm text-muted-foreground">{empty}</p>
  const rows = (list: readonly FindingGroup[]) =>
    list.map((group) => (
      <li key={group.value}>
        <details className="group rounded-lg border border-border bg-card text-card-foreground">
          <summary className="flex cursor-pointer list-none items-center gap-3 px-3 py-2 text-sm">
            {lead?.(group)}
            <code className="min-w-0 flex-1 truncate">{group.value}</code>
            <span className="font-mono text-xs text-muted-foreground">×{group.count}</span>
          </summary>
          <ul className="flex flex-col gap-0.5 border-t border-border px-3 py-2 font-mono text-[11px] text-muted-foreground">
            {group.occurrences.map((o, i) => (
              // A value can appear twice on one line.
              <li key={`${o.file}:${o.line}:${i}`}>
                {o.file}:{o.line}
              </li>
            ))}
          </ul>
        </details>
      </li>
    ))
  return (
    <div className="flex flex-col gap-2">
      <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">{rows(groups.slice(0, limit))}</ul>
      {groups.length > limit && (
        <details>
          <summary className="cursor-pointer text-sm text-muted-foreground">Show {groups.length - limit} more</summary>
          <ul className="mt-2 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">{rows(groups.slice(limit))}</ul>
        </details>
      )}
    </div>
  )
}
