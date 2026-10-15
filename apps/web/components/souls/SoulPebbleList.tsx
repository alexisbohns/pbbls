"use client"

import { useRouter } from "next/navigation"
import type { CompletedMoment, Soul } from "@/lib/types"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { PebbleCard } from "@/components/path/PebbleCard"

type SoulPebbleListProps = {
  /** Already filtered and ordered, newest first (R9). */
  moments: CompletedMoment[]
  souls: Soul[]
}

export function SoulPebbleList({ moments, souls }: SoulPebbleListProps) {
  const router = useRouter()
  const { emotionMap, soulMap } = useLookupMaps(souls)

  return (
    <ul className="flex flex-col gap-2">
      {moments.map((moment) => (
        <li key={moment.id}>
          <PebbleCard
            moment={moment}
            emotion={emotionMap.get(moment.pebble.emotion_id)}
            soulNames={moment.pebble.soul_ids
              .map((id) => soulMap.get(id)?.name)
              .filter((name): name is string => name != null)}
            onSelect={(momentId) => router.push(`/pebble/${momentId}`)}
          />
        </li>
      ))}
    </ul>
  )
}
