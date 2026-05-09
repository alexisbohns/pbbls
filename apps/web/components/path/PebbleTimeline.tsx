"use client"

import type { Pebble, Soul } from "@/lib/types"
import { useMarks } from "@/lib/data/useMarks"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { useGroupedPebbles } from "@/lib/hooks/useGroupedPebbles"
import { PebbleCard } from "@/components/path/PebbleCard"

type PebbleTimelineProps = {
  pebbles: Pebble[]
  souls: Soul[]
  onSelectPebble?: (id: string) => void
}

export function PebbleTimeline({ pebbles, souls, onSelectPebble }: PebbleTimelineProps) {
  const groups = useGroupedPebbles(pebbles)
  const { marks } = useMarks()
  const { emotionMap, soulMap, markMap } = useLookupMaps(souls, marks)

  return (
    <ol className="flex flex-col gap-8">
      {groups.map((group) => (
        <li key={group.dateKey}>
          <h2 className="mb-4 mt-5 font-heading text-sm font-semibold text-muted-foreground text-center">
            {group.label}
          </h2>
          <ul className="flex flex-col gap-3">
            {group.pebbles.map((pebble) => (
              <li key={pebble.id} id={`pebble-${pebble.id}`}>
                <PebbleCard
                  pebble={pebble}
                  emotion={emotionMap.get(pebble.emotion_id)}
                  mark={pebble.mark_id ? markMap.get(pebble.mark_id) : undefined}
                  soulNames={pebble.soul_ids
                    .map((id) => soulMap.get(id)?.name)
                    .filter((name): name is string => name != null)}
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
