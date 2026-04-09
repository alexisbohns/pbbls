"use client"

import { useCallback } from "react"
import type { Pebble, Collection } from "@/lib/types"
import type { UpdatePebbleInput, UpdateCollectionInput } from "@/lib/data/data-provider"
import { EmotionPopover } from "@/components/record/EmotionPopover"
import { DomainPopover } from "@/components/record/DomainPopover"
import { CollectionPopover } from "@/components/record/CollectionPopover"

type PeekTagChipsProps = {
  pebble: Pebble
  collections: Collection[]
  allCollections: Collection[]
  onUpdatePebble: (input: UpdatePebbleInput) => Promise<Pebble>
  onUpdateCollection: (id: string, input: UpdateCollectionInput) => Promise<Collection>
}

export function PeekTagChips({
  pebble,
  collections,
  allCollections,
  onUpdatePebble,
  onUpdateCollection,
}: PeekTagChipsProps) {
  const linkedIds = collections.map((c) => c.id)

  const handleEmotionChange = useCallback(
    (id: string) => {
      void onUpdatePebble({ emotion_id: id })
    },
    [onUpdatePebble],
  )

  const handleDomainChange = useCallback(
    (ids: string[]) => {
      void onUpdatePebble({ domain_ids: ids })
    },
    [onUpdatePebble],
  )

  const handleCollectionChange = useCallback(
    (newIds: string[]) => {
      const added = newIds.filter((id) => !linkedIds.includes(id))
      const removed = linkedIds.filter((id) => !newIds.includes(id))

      for (const collId of added) {
        const coll = allCollections.find((c) => c.id === collId)
        if (coll) {
          void onUpdateCollection(collId, {
            pebble_ids: [...coll.pebble_ids, pebble.id],
          })
        }
      }
      for (const collId of removed) {
        const coll = allCollections.find((c) => c.id === collId)
        if (coll) {
          void onUpdateCollection(collId, {
            pebble_ids: coll.pebble_ids.filter((pid) => pid !== pebble.id),
          })
        }
      }
    },
    [linkedIds, allCollections, pebble.id, onUpdateCollection],
  )

  return (
    <div className="flex flex-wrap items-center justify-center gap-2">
      <EmotionPopover
        value={pebble.emotion_id}
        onChange={handleEmotionChange}
      />
      <DomainPopover
        value={pebble.domain_ids}
        onChange={handleDomainChange}
      />
      <CollectionPopover
        variant="chip"
        value={linkedIds}
        onChange={handleCollectionChange}
        collections={allCollections}
      />
    </div>
  )
}
