"use client"

import { X } from "lucide-react"
import type { Pebble, Soul } from "@/lib/types"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { PebbleCard } from "@/components/path/PebbleCard"
import { Button } from "@/components/ui/button"

type CollectionPebbleListProps = {
  pebbles: Pebble[]
  souls: Soul[]
  onRemove?: (pebbleId: string) => void
}

export function CollectionPebbleList({
  pebbles,
  souls,
  onRemove,
}: CollectionPebbleListProps) {
  const { emotionMap, soulMap } = useLookupMaps(souls)

  return (
    <ul className="flex flex-col gap-2">
      {pebbles.map((pebble) => (
        <li key={pebble.id} className="flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <PebbleCard
              pebble={pebble}
              emotion={emotionMap.get(pebble.emotion_id)}
              soulNames={pebble.soul_ids
                .map((id) => soulMap.get(id)?.name)
                .filter((name): name is string => name != null)}
            />
          </div>
          {onRemove && (
            <Button
              variant="ghost"
              size="icon-xs"
              aria-label={`Remove ${pebble.name} from collection`}
              onClick={() => onRemove(pebble.id)}
            >
              <X />
            </Button>
          )}
        </li>
      ))}
    </ul>
  )
}
