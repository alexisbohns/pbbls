"use client"

import { useTranslations } from "next-intl"
import { useCollections } from "@/lib/data/useCollections"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { CollectionPickerContent } from "@/components/record/CollectionPickerContent"

type CompleteCollectionStepProps = {
  selectedIds: string[]
  /** A pick commits and advances; `null` is the "None" row. */
  onSelect: (id: string | null) => void
}

/**
 * Which collection, if any. Single-select (R13, PD-W11), unlike the record
 * flow's multi-select step: a tap commits and advances, and "None" or Skip
 * says none. No inline creation, as on iOS.
 */
export function CompleteCollectionStep({ selectedIds, onSelect }: CompleteCollectionStepProps) {
  const t = useTranslations("record.complete")
  const { collections } = useCollections()

  if (collections.length === 0) {
    return (
      <p className="px-2 py-12 text-center text-sm text-muted-foreground">{t("noCollections")}</p>
    )
  }

  return (
    <div className="flex flex-col gap-0.5">
      <SelectableItem
        selected={selectedIds.length === 0}
        onSelect={() => onSelect(null)}
        className="py-2 text-muted-foreground"
      >
        {t("noCollection")}
      </SelectableItem>
      <CollectionPickerContent
        collections={collections}
        selectedIds={selectedIds}
        onToggle={(id) => onSelect(id)}
      />
    </div>
  )
}
