"use client"

import { useEmotionCategoryName } from "@/lib/i18n"
import type { EmotionWithPalette } from "@/lib/data/useEmotionsWithPalette"
import { EmotionPickerChip } from "./EmotionPickerChip"

type EmotionPickerSectionProps = {
  categorySlug: string
  categoryName: string
  primaryColor: string
  rows: EmotionWithPalette[]
  selectedId: string | undefined
  onSelect: (id: string) => void
}

/**
 * One category section in the emotion picker: uppercase localized header in
 * the category's primary color, followed by a 2-column grid of chips.
 */
export function EmotionPickerSection({
  categorySlug,
  categoryName,
  primaryColor,
  rows,
  selectedId,
  onSelect,
}: EmotionPickerSectionProps) {
  const localizedCategoryName = useEmotionCategoryName({ slug: categorySlug, name: categoryName })

  return (
    <section className="flex flex-col gap-3">
      <h3
        className="text-[11px] font-semibold uppercase tracking-[0.12em]"
        style={{ color: primaryColor }}
      >
        {localizedCategoryName}
      </h3>
      <div className="grid grid-cols-2 gap-2.5">
        {rows.map((row) => (
          <EmotionPickerChip
            key={row.id}
            row={row}
            selected={row.id === selectedId}
            onSelect={() => onSelect(row.id)}
          />
        ))}
      </div>
    </section>
  )
}
