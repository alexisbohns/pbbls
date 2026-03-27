"use client"

import { useMemo } from "react"
import type { Pebble, Soul } from "@/lib/types"
import { EMOTIONS } from "@/lib/config"
import { PebbleCard } from "@/components/path/PebbleCard"

type CollectionPebbleListProps = {
  pebbles: Pebble[]
  souls: Soul[]
}

export function CollectionPebbleList({
  pebbles,
  souls,
}: CollectionPebbleListProps) {
  const emotionMap = useMemo(
    () => new Map(EMOTIONS.map((e) => [e.id, e])),
    [],
  )

  const soulMap = useMemo(
    () => new Map(souls.map((s) => [s.id, s])),
    [souls],
  )

  return (
    <ul className="flex flex-col gap-2">
      {pebbles.map((pebble) => (
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
  )
}
