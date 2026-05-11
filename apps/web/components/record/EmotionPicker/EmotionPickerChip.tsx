"use client"

import { useEmotionLocalized } from "@/lib/i18n"
import type { EmotionWithPalette } from "@/lib/data/useEmotionsWithPalette"

type EmotionPickerChipProps = {
  row: EmotionWithPalette
  selected: boolean
  onSelect: () => void
}

/**
 * Single emoji-prefixed chip rendered inside an `EmotionPickerSection`.
 * Selected → category `primary_color` background, `light_color` foreground.
 * Unselected → category `surface_color` background, default foreground.
 * Tapping commits the selection through `onSelect`; the parent sheet
 * dismisses on commit (and treats a tap on the currently-selected chip as a
 * deselect).
 */
export function EmotionPickerChip({ row, selected, onSelect }: EmotionPickerChipProps) {
  // `useEmotionLocalized` keys on slug + falls back to the DB `name`/`label`
  // columns. View rows don't carry a label — pass an empty fallback since we
  // only render the name here.
  const { name } = useEmotionLocalized({ slug: row.slug, name: row.name, label: "" })
  const style = selected
    ? { backgroundColor: row.primary_color, color: row.light_color }
    : { backgroundColor: row.surface_color }

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className="flex w-full items-center gap-2 rounded-xl px-3.5 py-3 text-left text-sm font-medium outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
      style={style}
    >
      <span aria-hidden>{row.emoji}</span>
      <span className="line-clamp-1">{name}</span>
    </button>
  )
}
