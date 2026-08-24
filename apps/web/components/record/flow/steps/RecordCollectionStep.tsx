"use client"

import { useCollections } from "@/lib/data/useCollections"
import { CollectionPickerContent } from "@/components/record/CollectionPickerContent"

type RecordCollectionStepProps = {
  selectedIds: string[]
  onToggle: (id: string) => void
}

/**
 * Step 7 — which collections, if any.
 *
 * Multi-select, which is where web parts from iOS's single-collection step: the
 * web composer has always let a pebble sit in several collections, and dropping
 * that so the step could advance on tap would cost a capability to buy
 * uniformity. So it advances through the Skip / Done button, like souls.
 *
 * No inline creation: `CreateCollectionSheet` lives in Profile, and a second
 * creation entry point here is out of scope for the flow.
 */
export function RecordCollectionStep({ selectedIds, onToggle }: RecordCollectionStepProps) {
  const { collections } = useCollections()
  return (
    <CollectionPickerContent
      collections={collections}
      selectedIds={selectedIds}
      onToggle={onToggle}
    />
  )
}
