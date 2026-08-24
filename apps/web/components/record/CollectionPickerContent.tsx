"use client"

import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import { SelectableItem } from "@/components/ui/SelectableItem"

type CollectionPickerContentProps = {
  collections: Collection[]
  selectedIds: string[]
  onToggle: (id: string) => void
}

/**
 * The collection list. Multi-select — a pebble can sit in several collections
 * on web, and the flow keeps that.
 *
 * Presentation only, shared by `CollectionSheet` and the flow's collection step.
 * No inline creation on either: `CreateCollectionSheet` lives in Profile, and a
 * second creation entry point here is out of scope.
 */
export function CollectionPickerContent({
  collections,
  selectedIds,
  onToggle,
}: CollectionPickerContentProps) {
  const t = useTranslations("record.collection")

  if (collections.length === 0) {
    return (
      <p className="px-2 py-4 text-center text-sm text-muted-foreground">{t("empty")}</p>
    )
  }

  return (
    <div className="flex flex-col gap-0.5">
      {collections.map((coll) => (
        <SelectableItem
          key={coll.id}
          selected={selectedIds.includes(coll.id)}
          onSelect={() => onToggle(coll.id)}
          className="py-2"
        >
          {/* Collection names are user-authored, so never localized. */}
          {coll.name}
        </SelectableItem>
      ))}
    </div>
  )
}
