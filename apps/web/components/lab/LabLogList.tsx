"use client"

import { useLogList, type LogListMode } from "@/lib/data/useLab"
import { LogRow } from "@/components/lab/LogRow"
import { ReactionButton } from "@/components/lab/ReactionButton"

const LOCALE = "en"

type LabLogListProps = {
  mode: LogListMode
}

// Full "See all" list for the Lab tab's changelog or backlog section.
// Re-uses the same data fetcher as the parent feed but without the top-N
// limit. Backlog mode attaches a reaction toggle on each row.
// Mirrors apps/ios/Pebbles/Features/Lab/Views/LogListView.swift.
export function LabLogList({ mode }: LabLogListProps) {
  const { logs, reactedIds, loading, error, toggleReaction } = useLogList(mode)

  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>
  }

  if (error) {
    return (
      <p className="text-sm text-muted-foreground">
        Couldn’t load the list. Please try again.
      </p>
    )
  }

  if (logs.length === 0) {
    return <p className="text-sm text-muted-foreground">Nothing here yet.</p>
  }

  return (
    <ul className="flex flex-col gap-2">
      {logs.map((log) => (
        <li key={log.id}>
          <LogRow
            log={log}
            locale={LOCALE}
            trailing={
              mode === "backlog" ? (
                <ReactionButton
                  count={log.reaction_count}
                  isReacted={reactedIds.has(log.id)}
                  onToggle={() => void toggleReaction(log.id)}
                />
              ) : undefined
            }
          />
        </li>
      ))}
    </ul>
  )
}
