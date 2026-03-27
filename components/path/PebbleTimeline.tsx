"use client"

import { useMemo } from "react"
import type { Pebble, Soul } from "@/lib/types"
import { EMOTIONS } from "@/lib/config"
import { groupPebblesByDate } from "@/lib/utils/group-pebbles-by-date"
import { PebbleCard } from "@/components/path/PebbleCard"

type PebbleTimelineProps = {
  pebbles: Pebble[]
  souls: Soul[]
}

export function PebbleTimeline({ pebbles, souls }: PebbleTimelineProps) {
  const groups = useMemo(() => groupPebblesByDate(pebbles), [pebbles])

  const emotionMap = useMemo(
    () => new Map(EMOTIONS.map((e) => [e.id, e])),
    [],
  )

  const soulMap = useMemo(
    () => new Map(souls.map((s) => [s.id, s])),
    [souls],
  )

  return (
    <ol className="flex flex-col gap-8">
      {groups.map((group) => (
        <li key={group.dateKey}>
          <h2 className="mb-3 text-sm font-semibold text-muted-foreground">
            {group.label}
          </h2>
          <ul className="flex flex-col gap-2">
            {group.pebbles.map((pebble) => (
              <li key={pebble.id}>
                <PebbleCard
                  pebble={pebble}
                  emotion={emotionMap.get(pebble.emotion_id)}
                  soulNames={pebble.soul_ids
                    .map((id) => soulMap.get(id)?.name)
                    .filter((name): name is string => name != null)}
                />
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ol>
  )
}
