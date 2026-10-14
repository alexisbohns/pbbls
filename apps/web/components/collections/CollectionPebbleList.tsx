"use client"

import { X } from "lucide-react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import type { CompletedMoment, Soul } from "@/lib/types"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { momentDisplayName } from "@/lib/data/moment-row"
import { PebbleCard } from "@/components/path/PebbleCard"
import { Button } from "@/components/ui/button"

type CollectionPebbleListProps = {
  /** Already filtered and ordered, newest first (R9). */
  moments: CompletedMoment[]
  souls: Soul[]
  /** Called with the PEBBLE id: `collection_pebbles` is keyed by it (PD-W20). */
  onRemove?: (pebbleId: string) => void
}

export function CollectionPebbleList({
  moments,
  souls,
  onRemove,
}: CollectionPebbleListProps) {
  const router = useRouter()
  const { emotionMap, soulMap } = useLookupMaps(souls)
  const t = useTranslations("collections.detail")
  const tMoment = useTranslations("moment")

  return (
    <ul className="flex flex-col gap-2">
      {moments.map((moment) => (
        <li key={moment.id} className="flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <PebbleCard
              moment={moment}
              emotion={emotionMap.get(moment.pebble.emotion_id)}
              soulNames={moment.pebble.soul_ids
                .map((id) => soulMap.get(id)?.name)
                .filter((name): name is string => name != null)}
              onSelect={(momentId) => router.push(`/pebble/${momentId}`)}
            />
          </div>
          {onRemove && (
            <Button
              variant="ghost"
              size="icon-xs"
              aria-label={t("removePebbleAria", {
                name: momentDisplayName(moment.name) ?? tMoment("untitled"),
              })}
              onClick={() => onRemove(moment.pebble.id)}
            >
              <X />
            </Button>
          )}
        </li>
      ))}
    </ul>
  )
}
