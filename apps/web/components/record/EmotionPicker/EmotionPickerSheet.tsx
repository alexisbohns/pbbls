"use client"

import { useMemo } from "react"
import { useLocale, useTranslations } from "next-intl"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { useEmotionsWithPalette, type EmotionWithPalette } from "@/lib/data/useEmotionsWithPalette"
import {
  emotionCategoryOrder,
  type Intensity,
  type Valence,
} from "@/lib/config/emotion-category-ordering"
import { EmotionPickerSection } from "./EmotionPickerSection"
import { EmotionPickerEmpty } from "./EmotionPickerEmpty"

type EmotionPickerSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Current emotion_id from the form. */
  value: string | undefined
  /** Drives section order (mirrors iOS valence-based ordering). */
  intensity?: Intensity
  valence?: Valence
  /**
   * Called when the user picks (or clears) an emotion. Receives `undefined`
   * when the user taps the currently-selected chip to deselect.
   */
  onChange: (id: string | undefined) => void
}

type CategoryGroup = {
  slug: string
  name: string
  primaryColor: string
  rows: EmotionWithPalette[]
}

/**
 * Two-level emotion picker presented as a Sheet.
 *
 * Categories derive from the cached `v_emotions_with_palette` rows by deduping
 * on `category_slug`; section order is `emotionCategoryOrder(intensity, valence)`
 * driven by the form's current cell. Tapping a chip commits the selection and
 * dismisses the sheet — tapping the currently-selected chip clears the value
 * (preserves iOS deselect behavior). The close (X) button in the header
 * dismisses without committing any change.
 */
export function EmotionPickerSheet({
  open,
  onOpenChange,
  value,
  intensity,
  valence,
  onChange,
}: EmotionPickerSheetProps) {
  const t = useTranslations("record.emotionPicker")
  // Untyped accessor for the runtime `emotion.<slug>.name` catalog — slugs
  // are DB values, not part of the typed message tree.
  const tAll = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  const locale = useLocale()
  const { rows, loading } = useEmotionsWithPalette()

  const groups = useMemo<CategoryGroup[]>(() => {
    if (rows.length === 0) return []
    const byCategorySlug = new Map<string, CategoryGroup>()
    for (const row of rows) {
      let group = byCategorySlug.get(row.category_slug)
      if (!group) {
        group = {
          slug: row.category_slug,
          name: row.category_name,
          primaryColor: row.primary_color,
          rows: [],
        }
        byCategorySlug.set(row.category_slug, group)
      }
      group.rows.push(row)
    }

    // Sort emotions within each category by their localized name so the order
    // matches the chip text the user actually sees (and follows fr-locale
    // collation when active).
    const collator = new Intl.Collator(locale, { sensitivity: "base" })
    const localizedName = (row: EmotionWithPalette): string => {
      const key = `emotion.${row.slug}.name`
      return tAll.has(key) ? tAll(key) : row.name
    }
    for (const group of byCategorySlug.values()) {
      group.rows.sort((a, b) => collator.compare(localizedName(a), localizedName(b)))
    }

    const order = emotionCategoryOrder(intensity, valence)
    const ordered: CategoryGroup[] = []
    for (const slug of order) {
      const group = byCategorySlug.get(slug)
      if (group && group.rows.length > 0) ordered.push(group)
    }
    return ordered
  }, [rows, intensity, valence, locale, tAll])

  const handleChipSelect = (id: string) => {
    onChange(id === value ? undefined : id)
    onOpenChange(false)
  }

  return (
    <PickerSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      closeLabel={t("close")}
    >
      {loading || groups.length === 0 ? (
        <EmotionPickerEmpty />
      ) : (
        <div className="flex flex-col gap-6 pb-4">
          {groups.map((group) => (
            <EmotionPickerSection
              key={group.slug}
              categorySlug={group.slug}
              categoryName={group.name}
              primaryColor={group.primaryColor}
              rows={group.rows}
              selectedId={value}
              onSelect={handleChipSelect}
            />
          ))}
        </div>
      )}
    </PickerSheet>
  )
}
