"use client"

import { useTranslations } from "next-intl"
import { Remove } from "@pbbls/icons"
import { Button } from "@/components/ui/button"
import { PebbleListItem } from "@/components/pebble/PebbleListItem"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { momentDisplayName } from "@/lib/data/moment-row"
import type { CompletedMoment } from "@/lib/types"

type CollectionPebbleListProps = {
  /** Already filtered and ordered, newest first (R9). */
  moments: CompletedMoment[]
  /** Called with the PEBBLE id: `collection_pebbles` is keyed by it (PD-W20). */
  onRemove?: (pebbleId: string) => void
}

/**
 * A collection's pebbles as one M3 list group (#1384, two-area spec D6), each
 * a link to the pebble with its remove control beside it.
 */
export function CollectionPebbleList({ moments, onRemove }: CollectionPebbleListProps) {
  const t = useTranslations("collections.detail")
  const tMoment = useTranslations("moment")

  return (
    <ul className="m3-list-group">
      {moments.map((moment) => (
        <PebbleListItem
          key={moment.id}
          moment={moment}
          action={
            onRemove && (
              <Button
                variant="ghost"
                size="icon"
                className="size-11 rounded-full text-m3-on-surface-variant"
                aria-label={t("removePebbleAria", { name: momentDisplayName(moment.name) ?? tMoment("untitled") })}
                onClick={() => onRemove(moment.pebble.id)}
              >
                <Remove className={PBBLS_ICON_SIZE.row} />
              </Button>
            )
          }
        />
      ))}
    </ul>
  )
}
