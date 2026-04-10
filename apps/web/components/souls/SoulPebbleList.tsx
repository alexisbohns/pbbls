"use client"

import type { Pebble, Soul } from "@/lib/types"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { PebbleCard } from "@/components/path/PebbleCard"

type SoulPebbleListProps = {
  pebbles: Pebble[]
  souls: Soul[]
}

export function SoulPebbleList({ pebbles, souls }: SoulPebbleListProps) {
  const { emotionMap, soulMap } = useLookupMaps(souls)

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
