"use client"

import type { Pebble, Soul } from "@/lib/types"
import { useMarks } from "@/lib/data/useMarks"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { useGroupedPebblesByWeek } from "@/lib/hooks/useGroupedPebblesByWeek"
import { PathPebbleRow } from "@/components/path/PathPebbleRow"
import { WeekSectionHeader } from "@/components/path/WeekSectionHeader"

type PebbleTimelineProps = {
  pebbles: Pebble[]
  souls: Soul[]
  onSelectPebble?: (id: string) => void
}

export function PebbleTimeline({ pebbles, souls, onSelectPebble }: PebbleTimelineProps) {
  const groups = useGroupedPebblesByWeek(pebbles)
  const { marks } = useMarks()
  const { markMap } = useLookupMaps(souls, marks)

  return (
    <ol className="flex flex-col gap-6">
      {groups.map((group) => (
        <li key={group.weekKey}>
          <WeekSectionHeader label={group.label} />
          <ul className="flex flex-col gap-1 rounded-2xl border bg-card p-2">
            {group.pebbles.map((pebble) => (
              <li key={pebble.id} id={`pebble-${pebble.id}`}>
                <PathPebbleRow
                  pebble={pebble}
                  mark={pebble.mark_id ? markMap.get(pebble.mark_id) : undefined}
                  onSelect={onSelectPebble}
                />
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ol>
  )
}
